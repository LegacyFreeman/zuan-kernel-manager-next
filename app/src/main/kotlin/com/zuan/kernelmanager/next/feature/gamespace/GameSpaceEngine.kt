/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.feature.gamespace

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Environment
import android.provider.Settings
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime
import com.zuan.kernelmanager.next.core.mode.WorkingMode
import com.zuan.kernelmanager.next.core.mode.WorkingModeManager
import com.zuan.kernelmanager.next.library.shell.ShizukuShell
import com.zuan.kernelmanager.next.library.shell.PrivilegedAccess
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Penerap/pemulih tweak sistem untuk sesi game.
 *
 * Semua perintah lewat [KernelRootRuntime] sehingga otomatis mengikuti Working Mode aplikasi
 * (Root / Shizuku). Di mode Basic perintah gagal dengan tenang - tiap aksi mengembalikan Boolean
 * supaya UI bisa menampilkan status yang jujur, bukan pura-pura berhasil.
 *
 * Nilai asli disimpan di [saved] saat [enter] dan dikembalikan persis di [exit].
 */
object GameSpaceEngine {

    private data class Saved(
        val pkg: String,
        val dndFilter: Int?,
        val headsUp: String?,
        val brightnessMode: String?,
        val minRefresh: String?,
        val peakRefresh: String?,
        val headsTouched: Boolean,
        val brightTouched: Boolean,
        val refreshTouched: Boolean,
        val usedGameMode: Boolean,
        val startedAt: Long
    )

    @Volatile private var saved: Saved? = null
    @Volatile var recording = false
        private set

    val activePackage: String? get() = saved?.pkg
    val sessionStartedAt: Long get() = saved?.startedAt ?: 0L

    private fun sh(c: Context, vararg cmd: String): com.zuan.kernelmanager.next.library.shell.SuResult {
        KernelRootRuntime.initialize(c.applicationContext)
        return KernelRootRuntime.executeBlocking(*cmd)
    }

    private fun getSetting(c: Context, ns: String, key: String): String? =
        sh(c, "settings get $ns $key").outputString.trim().takeIf { it.isNotEmpty() }

    private fun restoreSetting(c: Context, ns: String, key: String, value: String?) {
        if (value == null || value == "null") sh(c, "settings delete $ns $key")
        else sh(c, "settings put $ns $key $value")
    }

    fun canRunPrivileged(): Boolean = PrivilegedAccess.shellUsable()

    /**
     * Alasan jujur kenapa aksi privileged gagal. Pesan lama selalu "Needs Root or Shizuku mode"
     * walau pengguna sudah di mode itu - padahal penyebab sebenarnya bisa Shizuku belum jalan,
     * root ditolak, atau perintahnya sendiri yang gagal.
     */
    fun whyUnavailable(c: Context): String = when (WorkingModeManager.current) {
        WorkingMode.BASIC -> c.getString(R.string.gs_need_privileged)
        WorkingMode.ADB ->
            if (!PrivilegedAccess.shellUsable()) c.getString(R.string.gs_shizuku_not_ready) else cmdFailed(c)
        WorkingMode.ROOT -> cmdFailed(c)
    }

    private fun cmdFailed(c: Context): String {
        val err = KernelRootRuntime.lastErrorText()?.lineSequence()?.firstOrNull { it.isNotBlank() }?.take(80)
        return when {
            err == null -> c.getString(R.string.gs_cmd_failed, "?")
            err.contains("denied", true) -> c.getString(R.string.gs_root_denied)
            else -> c.getString(R.string.gs_cmd_failed, err)
        }
    }

    // ---- sesi -------------------------------------------------------------------------------

    /** Dipanggil sekali saat game masuk ke depan. Idempotent untuk paket yang sama. */
    @Synchronized
    fun enter(c: Context, pkg: String, s: GameSettings) {
        if (saved?.pkg == pkg) return
        if (saved != null) exit(c)
        val app = c.applicationContext
        val nm = app.getSystemService(NotificationManager::class.java)

        var dndPrev: Int? = null
        if (s.dnd) {
            if (nm.isNotificationPolicyAccessGranted) {
                dndPrev = nm.currentInterruptionFilter
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            }
        }
        val headsPrev = if (s.blockHeadsUp && canRunPrivileged()) getSetting(app, "global", "heads_up_notifications_enabled") else null
        if (s.blockHeadsUp && canRunPrivileged()) sh(app, "settings put global heads_up_notifications_enabled 0")

        val brightPrev = if (s.lockBrightnessMode && canRunPrivileged()) getSetting(app, "system", "screen_brightness_mode") else null
        if (s.lockBrightnessMode && canRunPrivileged()) sh(app, "settings put system screen_brightness_mode 0")

        var minPrev: String? = null
        var peakPrev: String? = null
        if (s.refreshRateHz > 0 && canRunPrivileged()) {
            minPrev = getSetting(app, "system", "min_refresh_rate")
            peakPrev = getSetting(app, "system", "peak_refresh_rate")
            sh(app, "settings put system min_refresh_rate ${s.refreshRateHz}", "settings put system peak_refresh_rate ${s.refreshRateHz}")
        }

        var gm = false
        if (s.performanceMode && canRunPrivileged() && PKG_REGEX.matches(pkg)) {
            gm = sh(app, "cmd game mode performance $pkg").isSuccess
        }
        if (s.clearRamOnLaunch) clearRam(app)

        val priv = canRunPrivileged()
        saved = Saved(
            pkg, dndPrev, headsPrev, brightPrev, minPrev, peakPrev,
            headsTouched = s.blockHeadsUp && priv,
            brightTouched = s.lockBrightnessMode && priv,
            refreshTouched = s.refreshRateHz > 0 && priv,
            usedGameMode = gm,
            startedAt = System.currentTimeMillis()
        )
        runCatching { GameHubEngine.apply(app, pkg) }
    }

    /** Kembalikan semua yang diubah [enter]. Aman dipanggil berulang. */
    @Synchronized
    fun exit(c: Context) {
        val st = saved ?: return
        saved = null
        val app = c.applicationContext
        GameSpacePrefs.addPlaytime(app, st.pkg, System.currentTimeMillis() - st.startedAt)
        val nm = app.getSystemService(NotificationManager::class.java)
        st.dndFilter?.let { if (nm.isNotificationPolicyAccessGranted) nm.setInterruptionFilter(it) }
        if (canRunPrivileged()) {
            if (st.headsTouched) restoreSetting(app, "global", "heads_up_notifications_enabled", st.headsUp)
            if (st.brightTouched) restoreSetting(app, "system", "screen_brightness_mode", st.brightnessMode)
            if (st.refreshTouched) {
                restoreSetting(app, "system", "min_refresh_rate", st.minRefresh)
                restoreSetting(app, "system", "peak_refresh_rate", st.peakRefresh)
            }
            if (st.usedGameMode && PKG_REGEX.matches(st.pkg)) sh(app, "cmd game mode standard ${st.pkg}")
        }
        runCatching { GameHubEngine.restore(app) }
        runCatching { RootBoost.restore(app) }
        runCatching { NonRootBoost.restore(app) }
        CpuModeEngine.restore(app)
        if (recording) stopRecording(app)
    }

    // ---- aksi cepat dari panel --------------------------------------------------------------

    fun setDnd(c: Context, on: Boolean): Boolean {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
            return true
        }
        return canRunPrivileged() && sh(c, "cmd notification set_dnd ${if (on) "priority" else "off"}").isSuccess
    }

    fun isDndOn(c: Context): Boolean =
        c.getSystemService(NotificationManager::class.java).currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL

    fun setHeadsUp(c: Context, blocked: Boolean): Boolean =
        canRunPrivileged() && sh(c, "settings put global heads_up_notifications_enabled ${if (blocked) 0 else 1}").isSuccess

    fun isHeadsUpBlocked(c: Context): Boolean = getSetting(c, "global", "heads_up_notifications_enabled") == "0"

    fun setBrightnessManual(c: Context, manual: Boolean): Boolean =
        canRunPrivileged() && sh(c, "settings put system screen_brightness_mode ${if (manual) 0 else 1}").isSuccess

    fun isBrightnessManual(c: Context): Boolean = getSetting(c, "system", "screen_brightness_mode") == "0"

    fun setBrightness(c: Context, level0to255: Int): Boolean =
        canRunPrivileged() && sh(c, "settings put system screen_brightness ${level0to255.coerceIn(1, 255)}").isSuccess

    fun getBrightness(c: Context): Int = getSetting(c, "system", "screen_brightness")?.toIntOrNull() ?: 128

    fun setPerformance(c: Context, pkg: String, on: Boolean): Boolean =
        canRunPrivileged() && PKG_REGEX.matches(pkg) &&
            sh(c, "cmd game mode ${if (on) "performance" else "standard"} $pkg").isSuccess

    /** Matikan proses cache di latar belakang (`am kill-all` tidak menyentuh app yang sedang di depan). */
    fun clearRam(c: Context): Boolean = canRunPrivileged() && sh(c, "am kill-all").isSuccess

    fun screenshot(c: Context): String? {
        if (!canRunPrivileged()) return null
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ZKMNext GameSpace")
        val name = "GS_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".png"
        val path = File(dir, name).absolutePath
        sh(c, "mkdir -p '${dir.absolutePath}'")
        val ok = sh(c, "screencap -p '$path'").isSuccess
        if (ok) sh(c, "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file://$path >/dev/null 2>&1")
        return if (ok) path else null
    }

    fun startRecording(c: Context): String? {
        if (!canRunPrivileged() || recording) return null
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "ZKMNext GameSpace")
        val name = "GS_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
        val path = File(dir, name).absolutePath
        sh(c, "mkdir -p '${dir.absolutePath}'")
        // screenrecord dibatasi 180 dtk oleh sistem; nohup + & agar shell tidak menunggu.
        // Kualitas dari Control Panel > Functions: SD = 6 Mbps, HD = 16 Mbps.
        val hd = GameHubPrefs.effInt(c, activePackage, "fn_quality", 1) == 1
        val bitRate = if (hd) 16_000_000 else 6_000_000
        val r = sh(c, "nohup screenrecord --bit-rate $bitRate --time-limit 180 '$path' >/dev/null 2>&1 &")
        recording = r.isSuccess
        return if (recording) path else null
    }

    fun stopRecording(c: Context) {
        if (!recording) return
        recording = false
        sh(c, "pkill -2 screenrecord")
    }


    // ---- aksi tambahan untuk hub Energy Cube ----------------------------------------------

    fun isWifiOn(c: Context): Boolean =
        (c.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.isWifiEnabled == true

    fun setWifi(c: Context, on: Boolean): Boolean =
        canRunPrivileged() && sh(c, "svc wifi ${if (on) "enable" else "disable"}").isSuccess

    fun isRotationLocked(c: Context): Boolean =
        Settings.System.getInt(c.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0

    fun setRotationLock(c: Context, locked: Boolean): Boolean =
        canRunPrivileged() && sh(c, "settings put system accelerometer_rotation ${if (locked) 0 else 1}").isSuccess

    /** Urutan siklus refresh rate; 0 = bawaan sistem. */
    val HZ_STEPS = listOf(0, 60, 90, 120, 144)

    /** Refresh rate terkunci saat ini (0 = bawaan). */
    fun refreshSetting(c: Context): Int =
        Settings.System.getFloat(c.contentResolver, "min_refresh_rate", 0f).toInt().coerceAtLeast(0)

    fun setRefreshRate(c: Context, hz: Int): Boolean {
        if (!canRunPrivileged()) return false
        return if (hz <= 0) {
            sh(c, "settings delete system min_refresh_rate", "settings delete system peak_refresh_rate").isSuccess
        } else {
            sh(c, "settings put system min_refresh_rate $hz", "settings put system peak_refresh_rate $hz").isSuccess
        }
    }

    /** Volume media 0..1 (tanpa izin khusus). */
    fun getVolume(c: Context): Float {
        val am = c.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return 0f
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return am.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
    }

    fun setVolume(c: Context, fraction: Float) {
        val am = c.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, (fraction.coerceIn(0f, 1f) * max).toInt(), 0) }
    }

    private val PKG_REGEX = Regex("^[A-Za-z0-9_.]+$")
}
