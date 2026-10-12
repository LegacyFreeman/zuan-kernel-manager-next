/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 *
 * The five ZKM home-screen widgets.
 */
package com.zuan.kernelmanager.next.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.os.Build
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.feature.gamespace.CpuMode
import com.zuan.kernelmanager.next.feature.gamespace.GameSpaceEngine
import com.zuan.kernelmanager.next.feature.gamespace.GameSpacePrefs
import com.zuan.kernelmanager.next.library.device.monitor.MonitorReader
import com.zuan.kernelmanager.next.library.shell.PrivilegedAccess
import com.zuan.kernelmanager.next.ui.navigation.NavigationRoute
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun now(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

/* ------------------------------------------------------------------------------------------ */
/* 1. Performance mode                                                                         */
/* ------------------------------------------------------------------------------------------ */
class PerformanceWidget : ZkmWidgetProvider() {
    override fun build(ctx: Context, mgr: AppWidgetManager, id: Int): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.zw_widget_performance)
        val active = GameSpacePrefs.cpuMode(ctx)
        val tiles = listOf(
            Triple(R.id.zw_mode_balance, R.id.zw_mode_balance_icon, R.id.zw_mode_balance_label),
            Triple(R.id.zw_mode_dynamic, R.id.zw_mode_dynamic_icon, R.id.zw_mode_dynamic_label),
            Triple(R.id.zw_mode_perf, R.id.zw_mode_perf_icon, R.id.zw_mode_perf_label)
        )
        CpuMode.entries.forEachIndexed { i, mode ->
            val (tile, icon, label) = tiles[i]
            rv.setTextViewText(label, ctx.getString(mode.labelRes))
            rv.setOnClickPendingIntent(tile, ZkmWidgetKit.action(ctx, ZkmWidgetKit.ACTION_CPU_MODE, i))
            ZkmWidgetKit.style(ctx, rv, tile, icon, label, i == active)
        }
        rv.setTextViewText(
            R.id.zw_perf_status,
            ctx.getString(R.string.zw_active_fmt, ctx.getString(CpuMode.fromOrdinal(active).labelRes))
        )
        rv.setOnClickPendingIntent(R.id.zw_header, ZkmWidgetKit.openRoute(ctx, NavigationRoute.GameSpace.route))
        return rv
    }
}

/* ------------------------------------------------------------------------------------------ */
/* 2. Quick actions                                                                            */
/* ------------------------------------------------------------------------------------------ */
class QuickActionsWidget : ZkmWidgetProvider() {
    override fun build(ctx: Context, mgr: AppWidgetManager, id: Int): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.zw_widget_toggles)

        // Refresh rate
        val hz = GameSpaceEngine.refreshSetting(ctx)
        rv.setTextViewText(R.id.zw_t_hz_label, ctx.getString(R.string.zw_refresh_rate))
        rv.setTextViewText(
            R.id.zw_t_hz_value,
            if (hz <= 0) ctx.getString(R.string.zw_hz_auto) else ctx.getString(R.string.zw_hz_fmt, hz)
        )
        rv.setOnClickPendingIntent(R.id.zw_t_hz, ZkmWidgetKit.action(ctx, ZkmWidgetKit.ACTION_CYCLE_HZ))
        ZkmWidgetKit.style(ctx, rv, R.id.zw_t_hz, R.id.zw_t_hz_icon, R.id.zw_t_hz_label, hz > 0)

        // Clear RAM
        val ram = MonitorReader.getRamInfo(ctx)
        rv.setTextViewText(R.id.zw_t_ram_label, ctx.getString(R.string.zw_clear_ram))
        rv.setTextViewText(
            R.id.zw_t_ram_value,
            ctx.getString(R.string.zw_free_fmt, String.format(Locale.US, "%.1f", (ram.totalMb - ram.usedMb) / 1024f))
        )
        rv.setOnClickPendingIntent(R.id.zw_t_ram, ZkmWidgetKit.action(ctx, ZkmWidgetKit.ACTION_CLEAR_RAM))
        ZkmWidgetKit.style(ctx, rv, R.id.zw_t_ram, R.id.zw_t_ram_icon, R.id.zw_t_ram_label, false)

        // Do Not Disturb
        val dnd = runCatching { GameSpaceEngine.isDndOn(ctx) }.getOrDefault(false)
        rv.setTextViewText(R.id.zw_t_dnd_label, ctx.getString(R.string.zw_dnd))
        rv.setTextViewText(R.id.zw_t_dnd_value, ctx.getString(if (dnd) R.string.zw_on else R.string.zw_off))
        rv.setOnClickPendingIntent(R.id.zw_t_dnd, ZkmWidgetKit.action(ctx, ZkmWidgetKit.ACTION_TOGGLE_DND))
        ZkmWidgetKit.style(ctx, rv, R.id.zw_t_dnd, R.id.zw_t_dnd_icon, R.id.zw_t_dnd_label, dnd)

        // Game Space
        rv.setTextViewText(R.id.zw_t_game_label, ctx.getString(R.string.zw_game_space))
        rv.setOnClickPendingIntent(R.id.zw_t_game, ZkmWidgetKit.openRoute(ctx, NavigationRoute.GameSpace.route))
        ZkmWidgetKit.style(ctx, rv, R.id.zw_t_game, R.id.zw_t_game_icon, R.id.zw_t_game_label, false)

        rv.setTextViewText(R.id.zw_tog_status, PrivilegedAccess.label())
        rv.setOnClickPendingIntent(R.id.zw_header, ZkmWidgetKit.action(ctx, ZkmWidgetKit.ACTION_REFRESH))
        return rv
    }
}

/* ------------------------------------------------------------------------------------------ */
/* 3. Menu shortcuts                                                                           */
/* ------------------------------------------------------------------------------------------ */
class MenuWidget : ZkmWidgetProvider() {
    private class Item(val route: String, val label: Int)

    private val items = listOf(
        Item(NavigationRoute.GameSpace.route, R.string.zw_menu_gamespace),
        Item(NavigationRoute.SoC.route, R.string.zw_menu_soc),
        Item(NavigationRoute.BatteryController.route, R.string.zw_menu_battery),
        Item(NavigationRoute.ThermalDevices.route, R.string.zw_menu_thermal),
        Item(NavigationRoute.Display.route, R.string.zw_menu_display),
        Item(NavigationRoute.ProcessManager.route, R.string.zw_menu_process),
        Item(NavigationRoute.Terminal.route, R.string.zw_menu_terminal),
        Item(NavigationRoute.Storage.route, R.string.zw_menu_storage)
    )

    override fun build(ctx: Context, mgr: AppWidgetManager, id: Int): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.zw_widget_menu)
        val tiles = intArrayOf(R.id.zw_m0, R.id.zw_m1, R.id.zw_m2, R.id.zw_m3, R.id.zw_m4, R.id.zw_m5, R.id.zw_m6, R.id.zw_m7)
        val icons = intArrayOf(R.id.zw_m0_icon, R.id.zw_m1_icon, R.id.zw_m2_icon, R.id.zw_m3_icon, R.id.zw_m4_icon, R.id.zw_m5_icon, R.id.zw_m6_icon, R.id.zw_m7_icon)
        val labels = intArrayOf(R.id.zw_m0_label, R.id.zw_m1_label, R.id.zw_m2_label, R.id.zw_m3_label, R.id.zw_m4_label, R.id.zw_m5_label, R.id.zw_m6_label, R.id.zw_m7_label)
        items.forEachIndexed { i, item ->
            rv.setTextViewText(labels[i], ctx.getString(item.label))
            rv.setOnClickPendingIntent(tiles[i], ZkmWidgetKit.openRoute(ctx, item.route))
            ZkmWidgetKit.style(ctx, rv, tiles[i], icons[i], labels[i], false)
        }
        return rv
    }
}

/* ------------------------------------------------------------------------------------------ */
/* 4. Game launcher                                                                            */
/* ------------------------------------------------------------------------------------------ */
class GameLauncherWidget : ZkmWidgetProvider() {
    override fun build(ctx: Context, mgr: AppWidgetManager, id: Int): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.zw_widget_games)
        val pm = ctx.packageManager
        val slots = listOf(
            Triple(R.id.zw_g0, R.id.zw_g0_icon, R.id.zw_g0_label),
            Triple(R.id.zw_g1, R.id.zw_g1_icon, R.id.zw_g1_label),
            Triple(R.id.zw_g2, R.id.zw_g2_icon, R.id.zw_g2_label),
            Triple(R.id.zw_g3, R.id.zw_g3_icon, R.id.zw_g3_label)
        )
        val games = GameSpacePrefs.games(ctx)
            .filter { pm.getLaunchIntentForPackage(it) != null }
            .sortedByDescending { GameSpacePrefs.lastPlayed(ctx, it) }
            .take(slots.size)

        rv.setTextViewText(R.id.zw_games_status, ctx.getString(R.string.zw_games_count_fmt, GameSpacePrefs.games(ctx).size))
        rv.setOnClickPendingIntent(R.id.zw_header, ZkmWidgetKit.openRoute(ctx, NavigationRoute.GameSpace.route))

        if (games.isEmpty()) {
            rv.setViewVisibility(R.id.zw_games_empty, android.view.View.VISIBLE)
            rv.setTextViewText(R.id.zw_games_empty, ctx.getString(R.string.zw_no_games))
            rv.setOnClickPendingIntent(R.id.zw_games_empty, ZkmWidgetKit.openRoute(ctx, NavigationRoute.GameSpace.route))
            return rv
        }

        games.forEachIndexed { i, pkg ->
            val (slot, icon, label) = slots[i]
            val bmp: Bitmap? = runCatching { pm.getApplicationIcon(pkg).toBitmap(120, 120) }.getOrNull()
            rv.setViewVisibility(slot, android.view.View.VISIBLE)
            if (bmp != null) rv.setImageViewBitmap(icon, bmp)
            rv.setTextViewText(label, runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg))
            ZkmWidgetKit.launchPackage(ctx, pkg)?.let { rv.setOnClickPendingIntent(slot, it) }
        }
        return rv
    }
}

/* ------------------------------------------------------------------------------------------ */
/* 5. Live stats                                                                               */
/* ------------------------------------------------------------------------------------------ */
class LiveStatsWidget : ZkmWidgetProvider() {

    private fun tint(rv: RemoteViews, bar: Int, color: Int) {
        // Per-bar colour needs RemoteViews.setColorStateList (Android 12+); older versions keep the accent.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            rv.setColorStateList(bar, "setProgressTintList", ColorStateList.valueOf(color))
        }
    }

    override fun build(ctx: Context, mgr: AppWidgetManager, id: Int): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.zw_widget_stats)
        val s = ZkmWidgetKit.readStats(ctx)
        val good = ctx.getColor(R.color.zw_good)
        val warn = ctx.getColor(R.color.zw_warn)
        val hot = ctx.getColor(R.color.zw_hot)

        // Battery
        rv.setTextViewText(
            R.id.zw_s0_label,
            ctx.getString(if (s.charging) R.string.zw_stat_charging else R.string.zw_stat_battery)
        )
        rv.setTextViewText(R.id.zw_s0_value, "${s.level}%")
        rv.setProgressBar(R.id.zw_s0_bar, 100, s.level, false)
        tint(rv, R.id.zw_s0_bar, if (s.level <= 15 && !s.charging) hot else if (s.charging) good else ctx.getColor(R.color.zw_accent))

        // Power
        rv.setTextViewText(
            R.id.zw_s1_label,
            ctx.getString(if (s.charging) R.string.zw_stat_power_in else R.string.zw_stat_power_out)
        )
        rv.setTextViewText(R.id.zw_s1_value, String.format(Locale.US, "%.1f W", s.watts))
        rv.setProgressBar(R.id.zw_s1_bar, 100, (s.watts / 25f * 100f).toInt().coerceIn(0, 100), false)
        tint(rv, R.id.zw_s1_bar, if (s.watts > 15f) warn else good)

        // RAM
        rv.setTextViewText(
            R.id.zw_s2_label,
            ctx.getString(
                R.string.zw_stat_ram,
                String.format(Locale.US, "%.1f", s.ramUsedGb),
                String.format(Locale.US, "%.1f", s.ramTotalGb)
            )
        )
        rv.setTextViewText(R.id.zw_s2_value, "${s.ramPercent}%")
        rv.setProgressBar(R.id.zw_s2_bar, 100, s.ramPercent.coerceIn(0, 100), false)
        tint(rv, R.id.zw_s2_bar, if (s.ramPercent > 85) hot else if (s.ramPercent > 70) warn else good)

        // Temperature
        rv.setTextViewText(R.id.zw_s3_label, ctx.getString(R.string.zw_stat_temp))
        rv.setTextViewText(R.id.zw_s3_value, String.format(Locale.US, "%.1f°C", s.tempC))
        rv.setProgressBar(R.id.zw_s3_bar, 100, ((s.tempC - 20f) / 30f * 100f).toInt().coerceIn(0, 100), false)
        val tempColor = if (s.tempC >= 43f) hot else if (s.tempC >= 38f) warn else good
        tint(rv, R.id.zw_s3_bar, tempColor)
        rv.setTextColor(R.id.zw_s3_value, tempColor)

        val status = buildString {
            if (s.cpuMHz > 0) append(ctx.getString(R.string.zw_cpu_fmt, s.cpuMHz)).append(" · ")
            append(ctx.getString(R.string.zw_updated_fmt, now()))
        }
        rv.setTextViewText(R.id.zw_stats_status, status)

        // Tap anywhere = refresh, header = open the app.
        rv.setOnClickPendingIntent(R.id.zw_s0, ZkmWidgetKit.action(ctx, ZkmWidgetKit.ACTION_REFRESH))
        rv.setOnClickPendingIntent(R.id.zw_s1, ZkmWidgetKit.action(ctx, ZkmWidgetKit.ACTION_REFRESH))
        rv.setOnClickPendingIntent(R.id.zw_s2, ZkmWidgetKit.action(ctx, ZkmWidgetKit.ACTION_REFRESH))
        rv.setOnClickPendingIntent(R.id.zw_s3, ZkmWidgetKit.action(ctx, ZkmWidgetKit.ACTION_REFRESH))
        rv.setOnClickPendingIntent(R.id.zw_header, ZkmWidgetKit.openRoute(ctx, NavigationRoute.Overall.route))
        return rv
    }
}
