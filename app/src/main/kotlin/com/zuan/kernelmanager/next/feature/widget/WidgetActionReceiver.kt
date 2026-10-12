/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 *
 * Runs the background actions fired by widget buttons and app shortcuts
 * (CPU mode, clear RAM, refresh-rate cycle, Do Not Disturb).
 */
package com.zuan.kernelmanager.next.feature.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.core.kernel.KernelCore
import com.zuan.kernelmanager.next.core.mode.WorkingModeManager
import com.zuan.kernelmanager.next.feature.gamespace.CpuMode
import com.zuan.kernelmanager.next.feature.gamespace.CpuModeEngine
import com.zuan.kernelmanager.next.feature.gamespace.GameSpaceEngine
import com.zuan.kernelmanager.next.feature.gamespace.GameSpacePrefs
import kotlin.concurrent.thread

class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val action = intent.action ?: return
        val pending = goAsync()

        // The process may have been started just for this broadcast: bring the mode + engine up first.
        runCatching {
            WorkingModeManager.load(app)
            if (action != ZkmWidgetKit.ACTION_REFRESH) KernelCore.initialize(app)
        }

        thread(name = "zkm-widget-action") {
            var message: String? = null
            try {
                message = when (action) {
                    ZkmWidgetKit.ACTION_CPU_MODE -> cpuMode(app, intent.getIntExtra(ZkmWidgetKit.EXTRA_MODE, 0))
                    ZkmWidgetKit.ACTION_CLEAR_RAM -> clearRam(app)
                    ZkmWidgetKit.ACTION_CYCLE_HZ -> cycleHz(app)
                    ZkmWidgetKit.ACTION_TOGGLE_DND -> toggleDnd(app)
                    else -> null
                }
                ZkmWidgetProvider.refreshAll(app)
            } catch (t: Throwable) {
                Log.e("ZkmWidget", "action $action failed", t)
                message = app.getString(R.string.zw_toast_failed)
            } finally {
                message?.let { toast(app, it) }
                pending.finish()
            }
        }
    }

    private fun cpuMode(c: Context, ordinal: Int): String {
        val mode = CpuMode.fromOrdinal(ordinal)
        val r = CpuModeEngine.apply(c, mode)
        return if (r.ok) {
            GameSpacePrefs.setCpuMode(c, mode.ordinal)
            c.getString(R.string.zw_toast_mode_ok, c.getString(mode.labelRes))
        } else {
            r.message ?: c.getString(R.string.zw_toast_failed)
        }
    }

    private fun clearRam(c: Context): String =
        c.getString(if (GameSpaceEngine.clearRam(c)) R.string.zw_toast_ram_ok else R.string.zw_toast_failed)

    private fun cycleHz(c: Context): String {
        val steps = GameSpaceEngine.HZ_STEPS
        val current = GameSpaceEngine.refreshSetting(c)
        val next = steps[(steps.indexOf(current).let { if (it < 0) 0 else it } + 1) % steps.size]
        return if (GameSpaceEngine.setRefreshRate(c, next)) {
            val label = if (next <= 0) c.getString(R.string.zw_hz_auto) else c.getString(R.string.zw_hz_fmt, next)
            c.getString(R.string.zw_toast_hz_fmt, label)
        } else {
            c.getString(R.string.zw_toast_failed)
        }
    }

    private fun toggleDnd(c: Context): String? {
        val target = !GameSpaceEngine.isDndOn(c)
        return if (GameSpaceEngine.setDnd(c, target)) null else c.getString(R.string.zw_toast_dnd_denied)
    }

    private fun toast(c: Context, text: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(c, text, Toast.LENGTH_SHORT).show() }
    }
}
