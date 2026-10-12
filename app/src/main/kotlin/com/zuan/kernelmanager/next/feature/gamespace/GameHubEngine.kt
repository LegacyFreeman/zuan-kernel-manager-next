/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 *
 * Game Space Hub - menerapkan setelan Control Panel / Plugin / Game Center yang NYATA saat sesi game:
 *  - Touch Indicator (plugin)  : show_touches
 *  - Dual-channel network      : mobile_data_always_on (data seluler tetap siaga saat Wi-Fi)
 *  - Wi-Fi low-latency lock    : WifiLock selama sesi
 *  - GPU frequency floor       : devfreq min_freq (hanya Root)
 *  - Thermal guard             : turunkan mode CPU ke Balance saat baterai panas
 *  - Play-time reminder        : notifikasi tiap jam bermain
 * Semua perubahan dicatat dan dikembalikan di [restore]. Original ZKM.
 */
package com.zuan.kernelmanager.next.feature.gamespace

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime
import com.zuan.kernelmanager.next.core.mode.WorkingMode
import com.zuan.kernelmanager.next.core.mode.WorkingModeManager
import com.zuan.kernelmanager.next.library.device.usage.UsageStatsHelper
import com.zuan.kernelmanager.next.library.shell.PrivilegedAccess

object GameHubEngine {

    const val PLUGIN_TOUCHES = "keypos"
    const val PLUGIN_STOPWATCH = "stopwatch"

    private data class Saved(
        val touches: String?, val touchesTouched: Boolean,
        val mobileOn: String?, val mobileTouched: Boolean,
        val gpuLines: List<String>
    )

    @Volatile private var saved: Saved? = null
    private var wifiLock: WifiManager.WifiLock? = null

    // keadaan thermal guard & pengingat
    private var guardActive = false
    private var guardPrev: CpuMode? = null
    private var lastRemindAt = 0L
    private var remindedHours = 0

    private const val CH = "zkm_gamespace_alert"
    private const val NOTIF_GUARD = 137 // 131 dipakai notifikasi foreground GameBarService
    private const val NOTIF_REMIND = 135

    private fun sh(c: Context, vararg cmd: String): com.zuan.kernelmanager.next.library.shell.SuResult {
        KernelRootRuntime.initialize(c.applicationContext)
        return KernelRootRuntime.executeBlocking(*cmd)
    }

    private fun get(c: Context, ns: String, key: String): String? =
        sh(c, "settings get $ns $key").outputString.trim().takeIf { it.isNotEmpty() }

    private fun put(c: Context, ns: String, key: String, v: String?) {
        if (v == null || v == "null") sh(c, "settings delete $ns $key") else sh(c, "settings put $ns $key $v")
    }

    // ---- sesi -------------------------------------------------------------------------------

    @Synchronized
    fun apply(c: Context, pkg: String) {
        val app = c.applicationContext
        val priv = PrivilegedAccess.shellUsable()
        restore(app)

        var touchPrev: String? = null
        val touches = priv && GameHubPrefs.pluginOn(app, PLUGIN_TOUCHES)
        if (touches) { touchPrev = get(app, "system", "show_touches"); put(app, "system", "show_touches", "1") }

        var mobPrev: String? = null
        val dual = priv && GameHubPrefs.effBool(app, pkg, "net_dual", false)
        if (dual) { mobPrev = get(app, "global", "mobile_data_always_on"); put(app, "global", "mobile_data_always_on", "1") }

        if (GameHubPrefs.effBool(app, pkg, "net_prio", false)) acquireWifiLock(app)

        val customGpu = CustomModeStore.get(app, CpuMode.fromOrdinal(GameSpacePrefs.cpuMode(app))).enabled
        val gpu = if (PrivilegedAccess.rootEquivalent() && !customGpu) // Custom Mode mengatur GPU sendiri
            gpuBoost(app, GameHubPrefs.effInt(app, pkg, "gpu_boost", 0)) else emptyList()

        saved = Saved(touchPrev, touches, mobPrev, dual, gpu)
        guardActive = false
        guardPrev = null
        lastRemindAt = SystemClock.elapsedRealtime()
        remindedHours = 0
    }

    @Synchronized
    fun restore(c: Context) {
        val app = c.applicationContext
        releaseWifiLock()
        runCatching { DynamicManager.reset() }
        val st = saved ?: return
        saved = null
        if (PrivilegedAccess.shellUsable()) {
            if (st.touchesTouched) put(app, "system", "show_touches", st.touches ?: "0")
            if (st.mobileTouched) put(app, "global", "mobile_data_always_on", st.mobileOn)
        }
        if (st.gpuLines.isNotEmpty() && PrivilegedAccess.rootEquivalent()) {
            sh(app, st.gpuLines.joinToString("; ") { l ->
                val p = l.removePrefix("ZKMGSAVE ").trim().split(' ')
                "echo ${p[1]} > ${p[0]}/min_freq"
            })
        }
        if (guardActive) {
            guardPrev?.let { GameSpacePrefs.setCpuMode(app, it.ordinal) }
            guardActive = false
            guardPrev = null
        }
        app.getSystemService(NotificationManager::class.java).apply { cancel(NOTIF_GUARD); cancel(NOTIF_REMIND) }
    }

    // ---- Wi-Fi lock -------------------------------------------------------------------------

    private fun acquireWifiLock(c: Context) {
        runCatching {
            val wm = c.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            else @Suppress("DEPRECATION") WifiManager.WIFI_MODE_FULL_HIGH_PERF
            wifiLock = wm.createWifiLock(mode, "zkm:gamespace").apply { setReferenceCounted(false); acquire() }
        }
    }

    private fun releaseWifiLock() {
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
        wifiLock = null
    }

    // ---- GPU (Root) -------------------------------------------------------------------------

    /** level 0 = bawaan, 1 = naikkan batas bawah ke ~60% maks, 2 = kunci di maks. Mengembalikan baris simpanan. */
    private fun gpuBoost(c: Context, level: Int): List<String> {
        if (level <= 0) return emptyList()
        val frac = if (level >= 2) 100 else 60
        val script = """
            ok=0
            for d in /sys/class/kgsl/kgsl-3d0/devfreq /sys/class/devfreq/*; do
              [ -f "§d/min_freq" ] || continue
              case "§d" in *kgsl*|*gpu*|*GPU*|*mali*) ;; *) continue;; esac
              mx=§(cat §d/max_freq 2>/dev/null); mn=§(cat §d/min_freq 2>/dev/null)
              [ -n "§mx" ] || continue
              echo "ZKMGSAVE §d §mn"
              t=§((mx*$frac/100)); f=§mx
              for x in §(cat §d/available_frequencies 2>/dev/null); do
                if [ "§x" -ge "§t" ] && [ "§x" -le "§f" ]; then f=§x; break; fi
              done
              echo §f > §d/min_freq 2>/dev/null
              [ "§(cat §d/min_freq 2>/dev/null)" = "§f" ] && ok=§((ok+1))
            done
            echo "ZKMGPU §ok"
        """.trimIndent().replace('§', '$')
        val r = KernelRootRuntime.executeBlocking(script)
        return r.out.filter { it.startsWith("ZKMGSAVE ") }
    }

    // ---- loop sesi (dipanggil detektor tiap ~1,5 dtk) ----------------------------------------

    private fun batteryTemp(c: Context): Float {
        val i = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
    }

    private fun notify(c: Context, id: Int, text: String) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, c.getString(R.string.gs_alert_channel), NotificationManager.IMPORTANCE_DEFAULT))
        nm.notify(
            id,
            NotificationCompat.Builder(c, CH)
                .setContentTitle(c.getString(R.string.ext_gamespace))
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setAutoCancel(true)
                .build()
        )
    }

    @Synchronized
    fun tick(c: Context, pkg: String) {
        val app = c.applicationContext
        if (saved == null) return

        // Dynamic tanpa root: turun/naik Game Mode mengikuti thermal status sistem.
        runCatching { NonRootBoost.tick(app, pkg) }
        // Dynamic sungguhan: CPU adaptif (root), RAM & storage (root + Shizuku).
        runCatching { DynamicManager.tick(app, pkg) }

        // Thermal guard: bagian dari Custom Mode (per mode) dan MATI secara bawaan.
        // Saat guard sedang menahan, batasnya diambil dari mode ASLI (guardPrev), bukan dari Balance.
        val cur = CpuMode.fromOrdinal(GameSpacePrefs.cpuMode(app))
        val watched = if (guardActive) guardPrev ?: cur else cur
        val prof = CustomModeStore.get(app, watched)
        if (guardActive || (prof.thermalOn && cur != CpuMode.BALANCE)) {
            val limit = prof.thermalLimitC.toFloat()
            val temp = batteryTemp(app)
            if (!guardActive && temp >= limit && cur != CpuMode.BALANCE) {
                guardActive = true
                guardPrev = cur
                GameSpacePrefs.setCpuMode(app, CpuMode.BALANCE.ordinal)
                CpuModeEngine.apply(app, CpuMode.BALANCE)
                notify(app, NOTIF_GUARD, app.getString(R.string.gs_guard_hot, temp.toInt()))
            } else if (guardActive && temp <= limit - 3f) {
                val back = guardPrev
                guardActive = false
                guardPrev = null
                // hanya kembalikan bila pengguna tidak mengganti mode sendiri selama itu
                if (back != null && cur == CpuMode.BALANCE) {
                    GameSpacePrefs.setCpuMode(app, back.ordinal)
                    CpuModeEngine.apply(app, back)
                    notify(app, NOTIF_GUARD, app.getString(R.string.gs_guard_cool, app.getString(back.labelRes)))
                }
            }
        }

        // Play-time reminder: sekali tiap jam
        if (GameHubPrefs.bool(app, "gen_wellbeing", false)) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastRemindAt >= 3_600_000L) {
                lastRemindAt = now
                remindedHours++
                notify(app, NOTIF_REMIND, app.getString(R.string.gs_wellbeing_msg, UsageStatsHelper.appLabel(app, pkg), remindedHours))
            }
        }
    }
}
