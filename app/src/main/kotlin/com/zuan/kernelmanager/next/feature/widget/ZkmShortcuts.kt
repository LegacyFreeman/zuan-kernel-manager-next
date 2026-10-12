/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 *
 * Long-press app-icon shortcuts. Published at runtime (dynamic) instead of from XML, so they
 * do not depend on the launcher having re-parsed the manifest after an update.
 */
package com.zuan.kernelmanager.next.feature.widget

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.ui.MainActivity
import com.zuan.kernelmanager.next.ui.navigation.NavigationRoute
import kotlin.concurrent.thread

object ZkmShortcuts {

    fun publish(context: Context) {
        val app = context.applicationContext
        thread(name = "zkm-shortcuts") {
            runCatching {
                val list = listOf(
                    route(app, "zkm_gamespace", R.string.zw_sc_gamespace_short, R.string.zw_sc_gamespace_long,
                        R.drawable.zw_sc_gamespace, NavigationRoute.GameSpace.route, 0),
                    action(app, "zkm_performance", R.string.zw_sc_boost_short, R.string.zw_sc_boost_long,
                        R.drawable.zw_sc_boost, ZkmWidgetKit.ACTION_CPU_MODE, 2, 1),
                    action(app, "zkm_clear_ram", R.string.zw_sc_ram_short, R.string.zw_sc_ram_long,
                        R.drawable.zw_sc_ram, ZkmWidgetKit.ACTION_CLEAR_RAM, -1, 2),
                    route(app, "zkm_thermal", R.string.zw_sc_thermal_short, R.string.zw_sc_thermal_long,
                        R.drawable.zw_sc_thermal, NavigationRoute.ThermalDevices.route, 3)
                )
                ShortcutManagerCompat.setDynamicShortcuts(app, list)
            }
        }
    }

    private fun route(c: Context, id: String, short: Int, long: Int, icon: Int, route: String, rank: Int) =
        build(
            c, id, short, long, icon, rank,
            Intent(c, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(ZkmDeepLink.EXTRA_ROUTE, route)
        )

    private fun action(c: Context, id: String, short: Int, long: Int, icon: Int, action: String, mode: Int, rank: Int) =
        build(
            c, id, short, long, icon, rank,
            Intent(c, ShortcutActionActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra("zkm_action", action)
                .apply { if (mode >= 0) putExtra(ZkmWidgetKit.EXTRA_MODE, mode) }
        )

    private fun build(c: Context, id: String, short: Int, long: Int, icon: Int, rank: Int, intent: Intent) =
        ShortcutInfoCompat.Builder(c, id)
            .setShortLabel(c.getString(short))
            .setLongLabel(c.getString(long))
            .setIcon(IconCompat.createWithResource(c, icon))
            .setIntent(intent)
            .setRank(rank)
            .build()
}
