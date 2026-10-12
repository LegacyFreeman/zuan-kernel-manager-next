/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 *
 * Game Space - mode DYNAMIC yang benar-benar dinamis (dipanggil tiap tick dari [GameHubEngine.tick]).
 *
 *  CPU      (Root / Shizuku-root) : beban CPU dibaca dari /proc/stat, lalu batas min/max tiap cluster
 *           dinaikkan-diturunkan bertahap (ECO / MID / BOOST) dengan histeresis, plus plafon suhu.
 *           Batas Custom Mode (min/max global atau per cluster) dipakai sebagai lantai & plafon.
 *  RAM      (semua mode shell)    : RAM bebas < ambang -> `am kill-all` (proses cache saja), + compact_memory
 *           bila root. Ada jeda minimal supaya tidak menendang game sendiri.
 *  Storage  (semua mode shell)    : ruang /data < ambang -> `pm trim-caches` (hanya cache app, bukan data pengguna).
 *  Shizuku ADB murni              : sysfs tidak bisa ditulis, jadi bagian CPU tidak dijalankan (tidak ada placebo);
 *           bagian thermal-aware tetap lewat [NonRootBoost.tick], RAM & Storage tetap bekerja.
 *
 * Semua aksi dicatat di [state] supaya UI bisa menampilkan apa yang BENAR-BENAR terjadi.
 * Tidak ada yang dinyalakan bila mode CPU aktif bukan DYNAMIC.
 */
package com.zuan.kernelmanager.next.feature.gamespace

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import com.zuan.kernelmanager.next.library.shell.PrivilegedAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DynamicManager {

    const val PREF_RAM = "dyn_ram"
    const val PREF_STORAGE = "dyn_storage"
    const val PREF_CPU = "dyn_cpu"

    enum class Level { ECO, MID, BOOST }

    data class State(
        val active: Boolean = false,
        val level: Level = Level.MID,
        val loadPct: Int = 0,
        val tempC: Float = 0f,
        val capPct: Int = 100,
        val ramFreeMb: Long = 0,
        val ramTotalMb: Long = 0,
        val storageFreePct: Int = 100,
        val lastAction: String = "",
        val cpuWrites: Int = 0,
        val ramCleans: Int = 0,
        val storageTrims: Int = 0,
        val canWriteCpu: Boolean = false
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    // ---- ambang (sengaja konservatif) ----
    private const val UP_LOAD = 68
    private const val DOWN_LOAD = 32
    private const val DOWN_SAMPLES = 3
    private const val SAMPLE_MS = 3_000L
    private const val RAM_LOW_PCT = 12
    private const val RAM_COOLDOWN_MS = 120_000L
    private const val STORAGE_LOW_PCT = 8
    private const val STORAGE_COOLDOWN_MS = 30 * 60_000L

    private var lastSampleAt = 0L
    private var lastRamAt = 0L
    private var lastStorageAt = 0L
    private var prevTotal = 0L
    private var prevIdle = 0L
    private var lowStreak = 0
    private var level = Level.MID
    private var capPct = 100
    private var capHot = 0
    private var appliedKey = ""
    private var cpuWrites = 0
    private var ramCleans = 0
    private var storageTrims = 0
    private var lastAction = ""

    private fun on(c: Context, key: String) = GameHubPrefs.bool(c, key, true)

    /** Dipanggil dari thread latar (loop GameDetectorService). */
    @Synchronized
    fun tick(c: Context, pkg: String) {
        val app = c.applicationContext
        val mode = CpuMode.fromOrdinal(GameSpacePrefs.cpuMode(app))
        if (mode != CpuMode.DYNAMIC) {
            if (appliedKey.isNotEmpty() || _state.value.active) resetInternal(keepCounters = false)
            return
        }
        if (!PrivilegedAccess.shellUsable()) {
            _state.value = _state.value.copy(active = true, canWriteCpu = false, lastAction = "")
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastSampleAt < SAMPLE_MS) return
        lastSampleAt = now

        val rootEq = PrivilegedAccess.rootEquivalent()
        val temp = batteryTemp(app)
        val (ramFree, ramTotal) = memInfo(app)
        val stPct = storageFreePct()

        // ---------- CPU ----------
        var load = _state.value.loadPct
        if (rootEq && GameHubPrefs.bool(app, PREF_CPU, true)) {
            val l = readLoad(app)
            if (l >= 0) load = l
            decideLevel(load)
            decideCap(app, temp)
            val key = "$level|$capPct"
            if (key != appliedKey) {
                if (applyCpu(app, mode)) {
                    appliedKey = key
                    cpuWrites++
                    lastAction = "CPU ${level.name} · cap $capPct%"
                }
            }
        }

        // ---------- RAM ----------
        if (on(app, PREF_RAM) && ramTotal > 0) {
            val pct = (ramFree * 100 / ramTotal).toInt()
            if (pct < RAM_LOW_PCT && now - lastRamAt >= RAM_COOLDOWN_MS) {
                lastRamAt = now
                if (cleanRam(app, rootEq)) {
                    ramCleans++
                    lastAction = "RAM clean ($pct% free)"
                }
            }
        }

        // ---------- Storage ----------
        if (on(app, PREF_STORAGE) && stPct < STORAGE_LOW_PCT && now - lastStorageAt >= STORAGE_COOLDOWN_MS) {
            lastStorageAt = now
            if (trimCaches(app)) {
                storageTrims++
                lastAction = "Storage cache trim ($stPct% free)"
            }
        }

        _state.value = State(
            active = true, level = level, loadPct = load, tempC = temp, capPct = capPct,
            ramFreeMb = ramFree, ramTotalMb = ramTotal, storageFreePct = stPct, lastAction = lastAction,
            cpuWrites = cpuWrites, ramCleans = ramCleans, storageTrims = storageTrims, canWriteCpu = rootEq
        )
    }

    /** Dipanggil saat sesi game berakhir / mode berganti. Pemulihan sysfs dilakukan [CpuModeEngine.restore]. */
    @Synchronized
    fun reset() = resetInternal(keepCounters = false)

    private fun resetInternal(keepCounters: Boolean) {
        appliedKey = ""
        level = Level.MID
        capPct = 100
        capHot = 0
        lowStreak = 0
        prevTotal = 0; prevIdle = 0
        lastSampleAt = 0
        if (!keepCounters) { cpuWrites = 0; ramCleans = 0; storageTrims = 0; lastAction = ""; lastRamAt = 0; lastStorageAt = 0 }
        _state.value = State()
    }

    // ------------------------------------------------------------------------------------------
    // keputusan
    // ------------------------------------------------------------------------------------------

    private fun decideLevel(load: Int) {
        when {
            load >= UP_LOAD -> { level = Level.BOOST; lowStreak = 0 }
            load <= DOWN_LOAD -> {
                // turun hanya setelah beberapa sampel berturut-turut rendah (histeresis, anti gelagapan)
                if (++lowStreak >= DOWN_SAMPLES) { level = Level.ECO }
            }
            else -> { lowStreak = 0; if (level == Level.ECO) level = Level.MID else if (level == Level.BOOST && load < UP_LOAD - 15) level = Level.MID }
        }
    }

    /** Plafon frekuensi mengikuti suhu baterai + thermal status sistem, turun/naik dengan histeresis 2 derajat. */
    private fun decideCap(c: Context, temp: Float) {
        val thermal = c.getSystemService(PowerManager::class.java)?.currentThermalStatus ?: 0
        val want = when {
            thermal >= PowerManager.THERMAL_STATUS_SEVERE || temp >= 47f -> 3
            thermal >= PowerManager.THERMAL_STATUS_MODERATE || temp >= 44.5f -> 2
            temp >= 42f -> 1
            else -> 0
        }
        if (want > capHot) capHot = want
        else if (want < capHot) {
            val release = when (capHot) { 3 -> 45f; 2 -> 42.5f; else -> 40f }
            if (temp <= release && thermal < PowerManager.THERMAL_STATUS_MODERATE) capHot--
        }
        capPct = when (capHot) { 3 -> 60; 2 -> 75; 1 -> 90; else -> 100 }
    }

    // ------------------------------------------------------------------------------------------
    // CPU (root)
    // ------------------------------------------------------------------------------------------

    private fun floorPct(level: Level) = when (level) { Level.ECO -> 0; Level.MID -> 30; Level.BOOST -> 55 }

    private fun applyCpu(c: Context, mode: CpuMode): Boolean {
        val prof = CustomModeStore.get(c, mode)
        val clusters = CustomModeEngine.clusters()
        if (clusters.isEmpty()) return false
        val sb = StringBuilder()
        clusters.forEach { cl ->
            val (cMin, cMax) = if (prof.enabled) (prof.clusterLimits[cl.name] ?: (prof.cpuMinPct to prof.cpuMaxPct)) else (0 to 100)
            // lantai: tingkat dinamis, tapi tidak boleh di bawah min Custom Mode; plafon: min(cap suhu, max Custom Mode)
            val ceil = minOf(capPct, cMax).coerceAtLeast(10)
            val floor = maxOf(floorPct(level), cMin).coerceAtMost(ceil)
            sb.append(
                """
                p=$CPU_BASE/${cl.name}
                if [ -d "§p" ]; then
                  mx=§(cat §p/cpuinfo_max_freq 2>/dev/null); mn=§(cat §p/cpuinfo_min_freq 2>/dev/null)
                  av=§(cat §p/scaling_available_frequencies 2>/dev/null)
                  tmax=§((mx*$ceil/100)); tmin=§((mx*$floor/100))
                  fmax=§mn; fmin=§mn
                  for x in §av; do
                    [ "§x" -le "§tmax" ] && fmax=§x
                  done
                  for x in §av; do
                    if [ "§x" -ge "§tmin" ]; then fmin=§x; break; fi
                  done
                  [ -z "§av" ] && { fmax=§tmax; fmin=§tmin; }
                  [ "§fmin" -gt "§fmax" ] && fmin=§fmax
                  chmod 644 §p/scaling_min_freq §p/scaling_max_freq 2>/dev/null
                  echo §fmin > §p/scaling_min_freq 2>/dev/null
                  echo §fmax > §p/scaling_max_freq 2>/dev/null
                  echo §fmin > §p/scaling_min_freq 2>/dev/null
                fi
                """.trimIndent()
            ).append('\n')
        }
        val r = runCatching { RootBoost.shell(c, sb.toString().replace('§', '$')) }.getOrNull() ?: return false
        return r.isSuccess
    }

    /** Persen beban CPU total sejak sampel sebelumnya; -1 bila belum ada pembanding / gagal baca. */
    private fun readLoad(c: Context): Int {
        val out = runCatching { RootBoost.shell(c, "head -n 1 /proc/stat") }.getOrNull()?.out?.firstOrNull { it.startsWith("cpu") }
            ?: return -1
        val p = out.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (p.size < 5) return -1
        val idle = p[3] + p.getOrElse(4) { 0L }
        val total = p.sum()
        val dT = total - prevTotal
        val dI = idle - prevIdle
        val first = prevTotal == 0L
        prevTotal = total; prevIdle = idle
        if (first || dT <= 0) return -1
        return (((dT - dI) * 100) / dT).toInt().coerceIn(0, 100)
    }

    // ------------------------------------------------------------------------------------------
    // RAM & Storage (root + Shizuku)
    // ------------------------------------------------------------------------------------------

    private fun cleanRam(c: Context, rootEq: Boolean): Boolean {
        val cmd = buildString {
            append("am kill-all >/dev/null 2>&1; ")
            if (rootEq) append("echo 1 > /proc/sys/vm/compact_memory 2>/dev/null; ")
            append("echo ZKMOK")
        }
        return runCatching { RootBoost.shell(c, cmd) }.getOrNull()?.out?.any { it.contains("ZKMOK") } == true
    }

    /** Aksi manual dari overlay: kosongkan cache app sekarang (tanpa menunggu ambang). */
    fun manualTrim(c: Context): Boolean = PrivilegedAccess.shellUsable() && trimCaches(c.applicationContext)

    private fun trimCaches(c: Context): Boolean {
        // Minta sistem mengosongkan cache app sampai ~2 GB bebas lagi. Tidak menyentuh data pengguna.
        val r = runCatching { RootBoost.shell(c, "pm trim-caches 2G >/dev/null 2>&1; echo ZKMOK") }.getOrNull()
        return r?.out?.any { it.contains("ZKMOK") } == true
    }

    // ------------------------------------------------------------------------------------------
    // pembacaan tanpa shell
    // ------------------------------------------------------------------------------------------

    private fun memInfo(c: Context): Pair<Long, Long> {
        val mi = ActivityManager.MemoryInfo()
        c.getSystemService(ActivityManager::class.java)?.getMemoryInfo(mi)
        return (mi.availMem / (1024 * 1024)) to (mi.totalMem / (1024 * 1024))
    }

    private fun storageFreePct(): Int = runCatching {
        val s = StatFs(Environment.getDataDirectory().path)
        if (s.blockCountLong <= 0) 100 else (s.availableBlocksLong * 100 / s.blockCountLong).toInt()
    }.getOrDefault(100)

    private fun batteryTemp(c: Context): Float {
        val i: Intent? = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
    }

    private const val CPU_BASE = "/sys/devices/system/cpu/cpufreq"
}
