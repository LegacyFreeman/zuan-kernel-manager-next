/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.feature.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import com.zuan.kernelmanager.next.core.mode.WorkingModeManager
import kotlin.concurrent.thread

/**
 * Base class: every widget builds its [RemoteViews] off the main thread (some readers touch
 * sysfs), so the update is wrapped in goAsync().
 */
abstract class ZkmWidgetProvider : AppWidgetProvider() {

    abstract fun build(ctx: Context, mgr: AppWidgetManager, id: Int): RemoteViews

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val app = context.applicationContext
        val pending = goAsync()
        thread(name = "zkm-widget-update") {
            try {
                WorkingModeManager.load(app)
                ids.forEach { runCatching { mgr.updateAppWidget(it, build(app, mgr, it)) }
                    .onFailure { e -> Log.e(TAG, "widget update failed", e) } }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        onUpdate(context, mgr, intArrayOf(id))
    }

    companion object {
        private const val TAG = "ZkmWidget"

        private fun all(): List<ZkmWidgetProvider> = listOf(
            PerformanceWidget(), QuickActionsWidget(), MenuWidget(), GameLauncherWidget(), LiveStatsWidget()
        )

        /** Rebuild every placed ZKM widget. Call from a worker thread. */
        fun refreshAll(ctx: Context) {
            val app = ctx.applicationContext
            val mgr = AppWidgetManager.getInstance(app)
            all().forEach { p ->
                val ids = mgr.getAppWidgetIds(ComponentName(app, p.javaClass))
                ids.forEach { id ->
                    runCatching { mgr.updateAppWidget(id, p.build(app, mgr, id)) }
                        .onFailure { e -> Log.e(TAG, "refresh failed", e) }
                }
            }
        }
    }
}
