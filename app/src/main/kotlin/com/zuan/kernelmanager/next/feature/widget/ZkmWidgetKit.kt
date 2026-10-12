/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 *
 * Shared helpers for the ZKM home-screen widgets and app shortcuts:
 * deep links into the app, broadcast actions, styling and live data.
 */
package com.zuan.kernelmanager.next.feature.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.library.device.battery.BatteryApi
import com.zuan.kernelmanager.next.library.device.monitor.MonitorReader
import com.zuan.kernelmanager.next.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import kotlin.math.abs

/** A route requested from outside the app (widget / shortcut). Consumed by the NavHost. */
object ZkmDeepLink {
    const val EXTRA_ROUTE = "zkm_route"

    val pending = MutableStateFlow<String?>(null)

    fun handle(intent: Intent?) {
        val route = intent?.getStringExtra(EXTRA_ROUTE) ?: return
        intent.removeExtra(EXTRA_ROUTE)
        pending.value = route
    }
}

internal object ZkmWidgetKit {
    const val ACTION_CPU_MODE = "com.zuan.kernelmanager.next.widget.CPU_MODE"
    const val ACTION_CLEAR_RAM = "com.zuan.kernelmanager.next.widget.CLEAR_RAM"
    const val ACTION_CYCLE_HZ = "com.zuan.kernelmanager.next.widget.CYCLE_HZ"
    const val ACTION_TOGGLE_DND = "com.zuan.kernelmanager.next.widget.TOGGLE_DND"
    const val ACTION_REFRESH = "com.zuan.kernelmanager.next.widget.REFRESH"
    const val EXTRA_MODE = "mode"

    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    /** Open ZKM and navigate to [route]. */
    fun openRoute(ctx: Context, route: String): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            data = Uri.parse("zkm://route/$route")
            putExtra(ZkmDeepLink.EXTRA_ROUTE, route)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(ctx, route.hashCode(), i, FLAGS)
    }

    /** Run a background action in [WidgetActionReceiver]. */
    fun action(ctx: Context, action: String, mode: Int = -1): PendingIntent {
        val i = Intent(ctx, WidgetActionReceiver::class.java).setAction(action)
        if (mode >= 0) i.putExtra(EXTRA_MODE, mode)
        return PendingIntent.getBroadcast(ctx, (action + mode).hashCode(), i, FLAGS)
    }

    /** Start another app (used by the Game Launcher). */
    fun launchPackage(ctx: Context, pkg: String): PendingIntent? {
        val launch = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(ctx, pkg.hashCode(), launch, FLAGS)
    }

    /**
     * Paint a chip/tile as selected. The normal state is left to the layout defaults so the
     * day/night colours keep following the system theme without needing a new update.
     */
    fun style(ctx: Context, rv: RemoteViews, tile: Int, icon: Int, label: Int, active: Boolean) {
        if (!active) return
        val fg = ctx.getColor(R.color.zw_on_accent)
        rv.setInt(tile, "setBackgroundResource", R.drawable.zw_chip_active)
        rv.setInt(icon, "setColorFilter", fg)
        rv.setTextColor(label, fg)
    }

    // ---- live data (no root needed) ----------------------------------------------------------

    data class Stats(
        val level: Int,
        val charging: Boolean,
        val watts: Float,
        val tempC: Float,
        val ramPercent: Int,
        val ramUsedGb: Float,
        val ramTotalGb: Float,
        val cpuMHz: Int
    )

    fun readStats(ctx: Context): Stats {
        val b = BatteryApi.read(ctx)
        val ram = MonitorReader.getRamInfo(ctx)
        val cpu = runCatching {
            File("/sys/devices/system/cpu/cpufreq").listFiles { f -> f.name.startsWith("policy") }
                ?.mapNotNull { File(it, "scaling_cur_freq").readText().trim().toLongOrNull() }
                ?.maxOrNull() ?: 0L
        }.getOrDefault(0L)
        val mhz = when {
            cpu >= 10_000_000L -> (cpu / 1_000_000L).toInt()
            cpu >= 10_000L -> (cpu / 1000L).toInt()
            else -> cpu.toInt()
        }
        return Stats(
            level = b.levelPercent,
            charging = b.charging,
            watts = abs(b.voltageV * b.currentMa / 1000f),
            tempC = b.tempC,
            ramPercent = ram.percent,
            ramUsedGb = ram.usedMb / 1024f,
            ramTotalGb = ram.totalMb / 1024f,
            cpuMHz = mhz
        )
    }
}
