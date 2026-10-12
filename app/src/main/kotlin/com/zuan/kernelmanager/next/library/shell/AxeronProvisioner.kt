/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.library.shell

import android.content.Context
import android.util.Log
import com.zuan.kernelmanager.next.core.mode.WorkingMode
import com.zuan.kernelmanager.next.core.mode.WorkingModeManager
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Once per Axeron grant (ADB mode only) the shell uid hands this app the app-ops it would
 * otherwise make the user toggle by hand. Everything here is something ZKM already asks the
 * user for elsewhere; nothing is granted that the app does not use.
 *
 *  - GET_USAGE_STATS      -> foreground app without parsing dumpsys (FPS / recorder / Screen Time)
 *  - SYSTEM_ALERT_WINDOW  -> FPS / monitor overlays
 *  - POST_NOTIFICATIONS   -> live-stats notifications (API 33+)
 *  - deviceidle whitelist -> monitors keep sampling with the screen off
 */
object AxeronProvisioner {
    private const val TAG = "ZkmAxeronProv"
    private const val PREFS = "settings_prefs"
    private const val KEY_DONE = "axeron_provisioned_v1"
    private val running = AtomicBoolean(false)

    fun onReady(context: Context) {
        val app = context.applicationContext
        if (WorkingModeManager.current != WorkingMode.ADB) return
        if (!running.compareAndSet(false, true)) return
        Thread {
            try {
                val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                if (prefs.getBoolean(KEY_DONE, false)) return@Thread
                val pkg = app.packageName
                val res = AxeronShell.exec(
                    arrayOf(
                        "appops set $pkg GET_USAGE_STATS allow",
                        "appops set $pkg SYSTEM_ALERT_WINDOW allow",
                        "pm grant $pkg android.permission.POST_NOTIFICATIONS 2>/dev/null",
                        "dumpsys deviceidle whitelist +$pkg 2>/dev/null",
                        "true"
                    )
                )
                if (res.isSuccess) prefs.edit().putBoolean(KEY_DONE, true).apply()
                else Log.w(TAG, "provision failed: ${res.errorString}")
                PrivilegedAccess.bumpEpoch()
            } catch (t: Throwable) {
                Log.w(TAG, "provision error", t)
            } finally {
                running.set(false)
            }
        }.apply { isDaemon = true; name = "ZKM-AxeronProv" }.start()
    }
}
