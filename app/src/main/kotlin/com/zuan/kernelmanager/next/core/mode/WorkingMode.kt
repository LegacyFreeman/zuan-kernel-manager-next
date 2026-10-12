/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.core.mode

import android.content.Context
import com.zuan.kernelmanager.next.ui.navigation.NavigationRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Privilege level the user picked at the end of the intro.
 *
 *  - BASIC : no privileged shell at all. View-only features that work with normal app permissions.
 *  - ADB   : shell-level (uid 2000) through Shizuku / wireless debugging. Most view features and
 *            a part of the modify features (pm, settings, cmd, dumpsys, wm ...).
 *  - ROOT  : everything is unlocked.
 */
enum class WorkingMode(val id: String, val rank: Int) {
    BASIC("basic", 0),
    ADB("adb", 1),
    ROOT("root", 2);

    /** True when a privileged shell (Shizuku or su) is expected to be available. */
    val hasShell: Boolean get() = rank >= ADB.rank

    /** True when su / the ZKM root daemon may be used. */
    val hasRoot: Boolean get() = rank >= ROOT.rank

    companion object {
        fun fromId(id: String?): WorkingMode? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Process-wide holder of the active [WorkingMode].
 *
 * It reads / writes the same "settings_prefs" file as SettingsPreference so it can be used from
 * services, receivers and the kernel runtime where no SettingsPreference instance exists yet.
 */
object WorkingModeManager {
    private const val PREFS = "settings_prefs"
    private const val KEY_MODE = "working_mode"
    private const val KEY_INTRO_DONE = "has_completed_intro"

    private val _saved = MutableStateFlow<WorkingMode?>(null)
    private val _mode = MutableStateFlow(WorkingMode.BASIC)

    @Volatile private var loaded = false

    /** Mode the user explicitly chose, or null when the intro has not been completed yet. */
    val saved: StateFlow<WorkingMode?> = _saved.asStateFlow()

    /** Effective mode. Until the user chooses one the app behaves as BASIC (nothing privileged runs). */
    val mode: StateFlow<WorkingMode> = _mode.asStateFlow()

    val current: WorkingMode get() = _mode.value

    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var chosen = WorkingMode.fromId(prefs.getString(KEY_MODE, null))
        if (chosen == null && prefs.getBoolean(KEY_INTRO_DONE, false)) {
            // Upgrade path: this install finished the old intro, when the app was root-only.
            chosen = WorkingMode.ROOT
            prefs.edit().putString(KEY_MODE, chosen.id).apply()
        }
        publish(chosen)
        loaded = true
    }

    fun set(context: Context, mode: WorkingMode) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, mode.id).apply()
        loaded = true
        publish(mode)
    }

    private fun publish(chosen: WorkingMode?) {
        _saved.value = chosen
        _mode.value = chosen ?: WorkingMode.BASIC
    }

    fun allows(route: String?, mode: WorkingMode = current): Boolean =
        mode.rank >= FeatureAccess.requiredMode(route).rank
}

/** Which minimum [WorkingMode] each screen needs. */
object FeatureAccess {
    private val basic: Set<String> = setOf(
        NavigationRoute.MainSwipeContainer.route,
        NavigationRoute.Overall.route,
        NavigationRoute.Dashboard.route,
        NavigationRoute.SoC.route,
        NavigationRoute.Settings.route,
        NavigationRoute.About.route,
        NavigationRoute.General.route,
        NavigationRoute.WallpaperStyle.route,
        NavigationRoute.Changelogs.route,
        NavigationRoute.OpenSource.route,
        NavigationRoute.HelpCenter.route,
        NavigationRoute.Storage.route,
        NavigationRoute.GameSpace.route,
        NavigationRoute.ScreenTime.route,
        NavigationRoute.BatteryUsage.route,
        NavigationRoute.ChargeStats.route,
        NavigationRoute.NetworkTraffic.route,
        NavigationRoute.ActivityLauncher.route
    )

    private val adb: Set<String> = setOf(
        NavigationRoute.Terminal.route,
        NavigationRoute.SetEdit.route,
        NavigationRoute.FpsManager.route,
        NavigationRoute.ProcessManager.route,
        NavigationRoute.AppManager.route,
        NavigationRoute.DebloatFreeze.route,
        NavigationRoute.Dex2oat.route,
        NavigationRoute.DozeMode.route,
        NavigationRoute.Display.route,
        NavigationRoute.WakelockAnalyzer.route
    )

    /** Anything not listed above (kernel flasher, KSU WebUI, GPU tuning, thermal, battery controller ...) needs root. */
    fun requiredMode(route: String?): WorkingMode = when {
        route == null -> WorkingMode.BASIC
        route in basic -> WorkingMode.BASIC
        route in adb -> WorkingMode.ADB
        else -> WorkingMode.ROOT
    }
}
