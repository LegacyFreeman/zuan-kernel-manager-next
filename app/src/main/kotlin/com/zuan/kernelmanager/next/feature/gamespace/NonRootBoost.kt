/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 *
 * Game Space - tweak NO-ROOT (Shizuku / ADB, shell uid 2000).
 *
 * Hanya hal yang benar-benar bisa dilakukan shell uid 2000 DAN punya efek nyata:
 *  - Game Mode resmi Android (`cmd game mode performance|standard <pkg>`, GameManagerService)
 *  - Clean RAM (`am kill-all`: hanya proses cache di latar belakang)
 *  - Background limiter (appops RUN_ANY_IN_BACKGROUND = ignore untuk app pihak ketiga yang sedang
 *    berjalan; nilai asli disimpan di disk dan DIKEMBALIKAN walau proses ZKM mati di tengah game)
 *  - Fixed performance mode (`cmd power set-fixed-performance-mode-enabled`) - eksperimental, bergantung
 *    Power HAL vendor, default MATI
 *  - Matikan Battery Saver (yang menahan CPU) - satu arah, tidak dinyalakan lagi otomatis
 *  - Dynamic: turun ke Standard saat sistem melapor thermal status >= MODERATE, naik lagi saat <= LIGHT
 *
 * TIDAK ada di sini (butuh root, sysfs/proc tidak bisa ditulis shell uid 2000): governor & batas frekuensi
 * CPU/GPU, renice/oom_score_adj, sched_boost, read-ahead, swappiness. Itu tetap di RootBoost / CpuModeEngine.
 * Bila Shizuku sendiri berjalan sebagai root (uid 0), semua jalur Root otomatis aktif ([PrivilegedAccess.rootEquivalent]).
 */
package com.zuan.kernelmanager.next.feature.gamespace

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime
import com.zuan.kernelmanager.next.core.mode.WorkingMode
import com.zuan.kernelmanager.next.core.mode.WorkingModeManager
import com.zuan.kernelmanager.next.library.shell.AxeronShell
import com.zuan.kernelmanager.next.library.shell.PrivilegedAccess
import com.zuan.kernelmanager.next.library.shell.ShizukuShell
import com.zuan.kernelmanager.next.library.shell.SuResult

object NonRootBoost {

    const val GAMEMODE = "nr_gamemode"
    const val FIXED = "nr_fixedperf"
    const val BGLIMIT = "nr_bglimit"
    const val BATSAVER = "nr_batsaver"

    private const val FILE = "zkm_nonroot_state"
    private const val K_GM = "gm_pkg"
    private const val K_FIXED = "fixed"
    private const val K_BG = "bg"
    private const val K_KEEP = "keep"
    private const val BG_CAP = 60
    private val PKG = Regex("^[A-Za-z][A-Za-z0-9_.]*$")
    private val APPOPS_MODES = setOf("allow", "deny", "ignore", "default", "foreground")

    enum class Level { ROOT, SHIZUKU_ROOT, SHIZUKU_ADB, SHIZUKU_OFF, BASIC }

    /** Level akses saat ini, dari status yang di-cache (aman dipanggil dari UI). */
    fun level(): Level = when (WorkingModeManager.current) {
        WorkingMode.ROOT -> Level.ROOT
        WorkingMode.ADB -> {
            val shizukuReady = ShizukuShell.state.value == ShizukuShell.State.READY
            val axeronReady = AxeronShell.state.value == AxeronShell.State.READY
            if (!shizukuReady && !axeronReady) Level.SHIZUKU_OFF
            else if ((shizukuReady && ShizukuShell.cachedServerUid() == 0) || (axeronReady && AxeronShell.cachedServerUid() == 0)) Level.SHIZUKU_ROOT
            else Level.SHIZUKU_ADB
        }
        WorkingMode.BASIC -> Level.BASIC
    }

    /** String banner jujur untuk tiap level. */
    fun levelTextRes(l: Level): Int = when (l) {
        Level.ROOT -> R.string.gsx_nr_lvl_root
        Level.SHIZUKU_ROOT -> R.string.gsx_nr_lvl_shizuku_root
        Level.SHIZUKU_ADB -> R.string.gsx_nr_lvl_adb
        Level.SHIZUKU_OFF -> R.string.gs_shizuku_not_ready
        Level.BASIC -> R.string.gsx_nr_lvl_basic
    }

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    internal fun sh(c: Context, timeoutMs: Long, vararg cmd: String): SuResult {
        KernelRootRuntime.initialize(c.applicationContext)
        return if (WorkingModeManager.current == WorkingMode.ADB) {
            when {
                ShizukuShell.isGranted() -> ShizukuShell.exec(cmd, timeoutMs)
                AxeronShell.isGranted() -> AxeronShell.exec(cmd, timeoutMs)
                ShizukuShell.isRunning() -> ShizukuShell.exec(cmd, timeoutMs)
                AxeronShell.isRunning() -> AxeronShell.exec(cmd, timeoutMs)
                else -> ShizukuShell.exec(cmd, timeoutMs)
            }
        } else KernelRootRuntime.executeBlocking(*cmd)
    }

    private fun availMb(c: Context): Long {
        val mi = ActivityManager.MemoryInfo()
        c.getSystemService(ActivityManager::class.java)?.getMemoryInfo(mi)
        return mi.availMem / (1024 * 1024)
    }

    // ---- daftar "selalu boleh jalan" untuk background limiter ---------------------------------

    fun keepList(c: Context): List<String> =
        sp(c).getStringSet(K_KEEP, emptySet())!!.filter { PKG.matches(it) }.sorted()

    fun addKeep(c: Context, pkg: String) {
        if (!PKG.matches(pkg)) return
        sp(c).edit().putStringSet(K_KEEP, HashSet(keepList(c)) + pkg).apply()
    }

    fun removeKeep(c: Context, pkg: String) {
        sp(c).edit().putStringSet(K_KEEP, HashSet(keepList(c)) - pkg).apply()
    }

    /** App yang TIDAK PERNAH dibatasi: game, ZKM, launcher, keyboard, SMS, dialer. */
    private fun protectedPackages(c: Context, game: String): Set<String> {
        val out = HashSet<String>()
        out += game
        out += c.packageName
        runCatching {
            c.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                ?.activityInfo?.packageName?.let { out += it }
        }
        runCatching {
            Settings.Secure.getString(c.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')?.let { out += it }
        }
        runCatching { Telephony.Sms.getDefaultSmsPackage(c)?.let { out += it } }
        runCatching {
            c.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { out += it }
        }
        return out.filter { PKG.matches(it) }.toSet()
    }

    // ---- Game Mode & fixed performance ----------------------------------------------------------

    private fun setGameMode(c: Context, pkg: String, performance: Boolean): Boolean {
        if (!PKG.matches(pkg)) return false
        val r = sh(c, 8000L, "cmd game mode ${if (performance) "performance" else "standard"} $pkg")
        if (r.isSuccess) {
            if (performance) sp(c).edit().putString(K_GM, pkg).apply() else sp(c).edit().remove(K_GM).apply()
        }
        return r.isSuccess
    }

    private fun setFixed(c: Context, on: Boolean): Boolean {
        val r = sh(c, 8000L, "cmd power set-fixed-performance-mode-enabled ${if (on) "true" else "false"}")
        if (r.isSuccess) sp(c).edit().putBoolean(K_FIXED, on).apply()
        return r.isSuccess
    }

    /** Dinamis: true saat kita sedang menahan game di Standard karena panas. */
    @Volatile private var dynamicHeld = false

    private fun applyMode(app: Context, pkg: String, mode: CpuMode, steps: MutableList<RootBoost.Step>?): Boolean {
        dynamicHeld = false
        var ok = true
        val wantPerf = mode != CpuMode.BALANCE && GameHubPrefs.bool(app, GAMEMODE, true)
        if (wantPerf) {
            if (setGameMode(app, pkg, true)) steps?.add(RootBoost.Step.GAMEMODE) else ok = false
        } else {
            sp(app).getString(K_GM, null)?.let { setGameMode(app, it, false) }
        }
        val wantFixed = mode == CpuMode.PERFORMANCE && GameHubPrefs.bool(app, FIXED, false)
        if (wantFixed) {
            if (setFixed(app, true)) steps?.add(RootBoost.Step.FIXED) // gagal = diabaikan, bukan error
        } else if (sp(app).getBoolean(K_FIXED, false)) {
            setFixed(app, false)
        }
        return ok
    }

    /** Dipanggil [CpuModeEngine] saat pengguna mengganti mode (tombol/overlay) tanpa root. */
    fun applyMode(c: Context, mode: CpuMode): CpuModeEngine.Result {
        val app = c.applicationContext
        if (!PrivilegedAccess.shellUsable()) return CpuModeEngine.Result(true, app.getString(R.string.gsx_nr_mode_saved))
        val pkg = GameSpaceEngine.activePackage
            ?: return CpuModeEngine.Result(true, app.getString(R.string.gsx_nr_mode_waiting))
        return if (applyMode(app, pkg, mode, null)) CpuModeEngine.Result(true, null)
        else CpuModeEngine.Result(false, app.getString(R.string.gsx_nr_mode_failed))
    }

    // ---- sesi ------------------------------------------------------------------------------------

    /** Jalankan semua fitur no-root yang dinyalakan. Panggil dari thread latar. */
    @Synchronized
    fun apply(c: Context, pkg: String, mode: CpuMode): RootBoost.Report {
        val app = c.applicationContext
        restoreBg(app) // sisa sesi sebelumnya (mis. proses ZKM sempat mati) dibersihkan dulu
        if (!PrivilegedAccess.shellUsable() || !PKG.matches(pkg)) return RootBoost.Report(emptyList(), 0, false)

        val steps = mutableListOf<RootBoost.Step>()
        var freed = 0

        if (RootBoost.isOn(app, RootBoost.RAM)) {
            val before = availMb(app)
            if (sh(app, 8000L, "am kill-all").isSuccess) {
                freed = (availMb(app) - before).coerceAtLeast(0L).toInt()
                steps += RootBoost.Step.RAM
            }
        }

        applyMode(app, pkg, mode, steps)

        if (GameHubPrefs.bool(app, BGLIMIT, false) && limitBackground(app, pkg) > 0) steps += RootBoost.Step.BGLIMIT
        if (GameHubPrefs.bool(app, BATSAVER, false) && batterySaverOff(app)) steps += RootBoost.Step.BATSAVER

        return RootBoost.Report(steps, freed, false)
    }

    /** Kembalikan semua yang diubah. Aman dipanggil berulang. */
    @Synchronized
    fun restore(c: Context) {
        val app = c.applicationContext
        dynamicHeld = false
        if (!PrivilegedAccess.shellUsable()) return // state tetap tersimpan, dipulihkan saat Shizuku siap
        sp(app).getString(K_GM, null)?.let { setGameMode(app, it, false) }
        if (sp(app).getBoolean(K_FIXED, false)) setFixed(app, false)
        restoreBg(app)
    }

    /** Dipanggil saat detektor start: pulihkan sisa sesi bila tidak ada game aktif (mis. ZKM sempat dimatikan sistem). */
    fun restoreStale(c: Context) {
        if (GameSpaceEngine.activePackage != null) return
        val st = sp(c)
        if (st.getString(K_GM, null) == null && !st.getBoolean(K_FIXED, false) && st.getStringSet(K_BG, emptySet()).isNullOrEmpty()) return
        restore(c)
    }

    /** Mode Dynamic tanpa root: Standard saat thermal >= MODERATE, Performance lagi saat <= LIGHT. */
    @Synchronized
    fun tick(c: Context, pkg: String) {
        if (PrivilegedAccess.rootEquivalent() || !PrivilegedAccess.shellUsable()) return
        val app = c.applicationContext
        val mode = CpuMode.fromOrdinal(GameSpacePrefs.cpuMode(app))
        if (mode != CpuMode.DYNAMIC || !GameHubPrefs.bool(app, GAMEMODE, true)) { dynamicHeld = false; return }
        val st = app.getSystemService(PowerManager::class.java)?.currentThermalStatus ?: return
        if (!dynamicHeld && st >= PowerManager.THERMAL_STATUS_MODERATE) {
            if (setGameMode(app, pkg, false)) dynamicHeld = true
        } else if (dynamicHeld && st <= PowerManager.THERMAL_STATUS_LIGHT) {
            if (setGameMode(app, pkg, true)) dynamicHeld = false
        }
    }

    // ---- background limiter ----------------------------------------------------------------------

    /** Batasi app pihak ketiga yang SEDANG berjalan. Mengembalikan jumlah app yang dibatasi. */
    private fun limitBackground(app: Context, game: String): Int {
        val skip = (protectedPackages(app, game) + keepList(app)).joinToString("|")
        val skipLine = if (skip.isNotEmpty()) "case \"§n\" in $skip) continue;; esac" else ""
        val script = """
            third=§(cmd package list packages -3 2>/dev/null | sed 's/^package://')
            ps -A -o NAME 2>/dev/null | sed 's/:.*//' | sort -u | head -n $BG_CAP | while read n; do
              [ -n "§n" ] || continue
              echo "§third" | grep -qx "§n" || continue
              $skipLine
              m=§(cmd appops get "§n" RUN_ANY_IN_BACKGROUND 2>/dev/null | head -n 1 | sed 's/.*: *//; s/[;, ].*//')
              [ "§m" = "ignore" ] && continue
              cmd appops set "§n" RUN_ANY_IN_BACKGROUND ignore >/dev/null 2>&1 && echo "ZKMBG §n §m"
            done
            echo ZKMDONE
        """.trimIndent().replace('§', '$')
        val r = sh(app, 40_000L, script)
        val saved = HashSet(sp(app).getStringSet(K_BG, emptySet())!!)
        var n = 0
        r.out.filter { it.startsWith("ZKMBG ") }.forEach { l ->
            val p = l.removePrefix("ZKMBG ").trim().split(' ').filter { it.isNotBlank() }
            val name = p.getOrNull(0) ?: return@forEach
            if (!PKG.matches(name)) return@forEach
            val old = p.getOrNull(1)?.takeIf { it in APPOPS_MODES } ?: "default"
            saved += "$name=$old"
            n++
        }
        if (n > 0) sp(app).edit().putStringSet(K_BG, saved).apply()
        return n
    }

    private fun restoreBg(app: Context) {
        val saved = sp(app).getStringSet(K_BG, emptySet()).orEmpty()
        if (saved.isEmpty() || !PrivilegedAccess.shellUsable()) return
        val cmds = saved.mapNotNull { e ->
            val name = e.substringBefore('=')
            val mode = e.substringAfter('=', "default")
            if (PKG.matches(name) && mode in APPOPS_MODES) "cmd appops set $name RUN_ANY_IN_BACKGROUND $mode >/dev/null 2>&1" else null
        }
        if (cmds.isNotEmpty()) sh(app, 40_000L, cmds.joinToString("; ") + "; true")
        sp(app).edit().remove(K_BG).apply()
    }

    // ---- Battery Saver ---------------------------------------------------------------------------

    fun isBatterySaverOn(c: Context): Boolean =
        c.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true

    /** Satu arah: Battery Saver tidak dinyalakan lagi otomatis. */
    fun batterySaverOff(c: Context): Boolean {
        if (!isBatterySaverOn(c)) return false
        return sh(c, 8000L, "settings put global low_power 0").isSuccess
    }
}
