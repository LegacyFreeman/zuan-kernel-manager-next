/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Description
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.zuan.kernelmanager.next.R
import dev.chrisbanes.haze.HazeState

// --- IMPORTS SCREEN ---
import com.zuan.kernelmanager.next.feature.dashboard.OverallScreen
import com.zuan.kernelmanager.next.feature.home.HomeScreen
import com.zuan.kernelmanager.next.feature.soc.SoCScreen
import com.zuan.kernelmanager.next.feature.settings.SettingsScreen
import com.zuan.kernelmanager.next.feature.about.AboutScreen
import com.zuan.kernelmanager.next.feature.about.changelogs.ChangelogsScreen
import com.zuan.kernelmanager.next.feature.about.opensource.OpenSourceScreen
import com.zuan.kernelmanager.next.feature.settings.WallpaperStyleScreen
import com.zuan.kernelmanager.next.feature.general.GeneralScreen
import com.zuan.kernelmanager.next.feature.terminal.TerminalScreen
import com.zuan.kernelmanager.next.feature.setedit.SetEditScreen
import com.zuan.kernelmanager.next.feature.fps.FpsManagerScreen
import com.zuan.kernelmanager.next.feature.process.ProcessManagerScreen
import com.zuan.kernelmanager.next.feature.kernel.flasher.KernelFlasherScreen
import com.zuan.kernelmanager.next.feature.activitylauncher.ActivityLauncherScreen
import com.zuan.kernelmanager.next.feature.ksuweb.KsuWebuiScreen
import com.zuan.kernelmanager.next.feature.gpu.adreno.AdrenoScreen
import com.zuan.kernelmanager.next.feature.gpu.mtk.MtkScreen
import com.zuan.kernelmanager.next.feature.appmanager.AppManagerScreen
import com.zuan.kernelmanager.next.feature.miuithermal.MiuiThermalScreen

// Import Menu System
import com.zuan.kernelmanager.next.feature.home.menu.DebloatFreezeScreen
import com.zuan.kernelmanager.next.feature.home.menu.ThermalDevicesScreen
import com.zuan.kernelmanager.next.feature.home.menu.DisplayScreen
import com.zuan.kernelmanager.next.feature.home.menu.BatteryControllerScreen
import com.zuan.kernelmanager.next.feature.home.menu.DozeModeScreen
import com.zuan.kernelmanager.next.feature.home.menu.Dex2oatScreen
import com.zuan.kernelmanager.next.feature.home.menu.ScreenTimeScreen
import com.zuan.kernelmanager.next.feature.home.menu.BatteryUsageScreen
import com.zuan.kernelmanager.next.feature.home.menu.ChargeStatsScreen
import com.zuan.kernelmanager.next.feature.home.menu.NetworkTrafficScreen
import com.zuan.kernelmanager.next.feature.home.menu.WakelockAnalyzerScreen
import com.zuan.kernelmanager.next.feature.storage.StorageScreen
import com.zuan.kernelmanager.next.feature.gamespace.GameSpaceScreen

sealed class NavigationRoute(
    val route: String,
    val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    // Route Khusus Container Swipe
    object MainSwipeContainer : NavigationRoute("main_swipe_container", R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home)

    object Overall : NavigationRoute("overall_screen", R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home)

    object Dashboard : NavigationRoute("dashboard_grid", R.string.nav_home, Icons.Filled.GridView, Icons.Outlined.GridView)

    object SoC : NavigationRoute("soc_screen", R.string.nav_soc, Icons.Filled.Tune, Icons.Outlined.Tune)

    object Settings : NavigationRoute("settings_screen", R.string.nav_pref, Icons.Filled.Settings, Icons.Outlined.Settings)

    object About : NavigationRoute("about_screen", R.string.nav_pref, Icons.Filled.SupportAgent, Icons.Outlined.SupportAgent)

    object General : NavigationRoute("general_screen", R.string.sect_general, Icons.Filled.GridView, Icons.Outlined.GridView)

    object WallpaperStyle : NavigationRoute(
        "wallpaper_style_screen",
        R.string.sect_appearance,
        Icons.Filled.Palette,
        Icons.Outlined.Palette,
    )

    object Terminal : NavigationRoute("terminal_screen", R.string.nav_fastshell, Icons.Filled.Code, Icons.Outlined.Code)

    object SetEdit : NavigationRoute("SetEdit", R.string.nav_setedit, Icons.Filled.Description, Icons.Outlined.Description)

    object FpsManager : NavigationRoute("fps_manager", R.string.nav_fps, Icons.Filled.Speed, Icons.Outlined.Speed)

    object ProcessManager : NavigationRoute("process_manager", R.string.nav_process, Icons.Filled.Assessment, Icons.Outlined.Assessment)

    object KernelFlasher : NavigationRoute("kernel_flasher", R.string.nav_flasher, Icons.Filled.SystemUpdate, Icons.Outlined.SystemUpdate)

    object ActivityLauncher : NavigationRoute("activity_launcher", R.string.nav_launcher, Icons.Filled.Apps, Icons.Outlined.Apps)

    object KsuWebUI : NavigationRoute("ksu_web", R.string.nav_ksuweb, Icons.Filled.Public, Icons.Outlined.Public)

    object Adreno : NavigationRoute("adreno_screen", R.string.nav_gpu, Icons.Filled.GraphicEq, Icons.Outlined.GraphicEq)

    object Mtk : NavigationRoute("mtk_screen", R.string.nav_gpu, Icons.Filled.GraphicEq, Icons.Outlined.GraphicEq)

    object GenericGpu : NavigationRoute("generic_gpu_screen", R.string.nav_gpu, Icons.Filled.GraphicEq, Icons.Outlined.GraphicEq)

    // Route untuk System Tools
    object DebloatFreeze : NavigationRoute(
        "debloat_freeze_screen",
        R.string.sect_general,
        Icons.Filled.AppBlocking,
        Icons.Outlined.AppBlocking,
    )

    object ThermalDevices : NavigationRoute(
        "thermal_devices_screen",
        R.string.sect_general,
        Icons.Filled.Thermostat,
        Icons.Outlined.Thermostat,
    )

    object Display : NavigationRoute("display_screen", R.string.sect_general, Icons.Filled.DisplaySettings, Icons.Outlined.DisplaySettings)

    object BatteryController : NavigationRoute(
        "battery_controller_screen",
        R.string.sect_general,
        Icons.Filled.BatteryChargingFull,
        Icons.Outlined.BatteryChargingFull,
    )

    object DozeMode : NavigationRoute("doze_mode_screen", R.string.sect_general, Icons.Filled.Bedtime, Icons.Outlined.Bedtime)

    object Dex2oat : NavigationRoute("dex2oat_screen", R.string.sect_general, Icons.Filled.Build, Icons.Outlined.Build)

    object AppManager : NavigationRoute("app_manager", R.string.ext_app_manager, Icons.Filled.Apps, Icons.Outlined.Apps)

    object MiuiThermal : NavigationRoute("miui_thermal", R.string.ext_miui_thermal, Icons.Filled.Thermostat, Icons.Outlined.Thermostat)

    object ScreenTime : NavigationRoute("screen_time_screen", R.string.ext_screen_time, Icons.Filled.Timer, Icons.Outlined.Timer)

    object BatteryUsage : NavigationRoute(
        "battery_usage_screen",
        R.string.ext_battery_usage,
        Icons.Filled.BatteryStd,
        Icons.Outlined.BatteryStd,
    )

    object ChargeStats : NavigationRoute("charge_stats_screen", R.string.ext_charge_stats, Icons.Filled.Bolt, Icons.Outlined.Bolt)

    object NetworkTraffic : NavigationRoute("network_traffic_screen", R.string.ext_network_traffic, Icons.Filled.NetworkCheck, Icons.Outlined.NetworkCheck)

    object WakelockAnalyzer : NavigationRoute("wakelock_analyzer_screen", R.string.ext_wakelock, Icons.Filled.Analytics, Icons.Outlined.Analytics)

    object Storage : NavigationRoute("storage_screen", R.string.ext_storage, Icons.Filled.Storage, Icons.Outlined.Storage)

    object GameSpace : NavigationRoute("game_space_screen", R.string.ext_gamespace, Icons.Filled.SportsEsports, Icons.Outlined.SportsEsports)

    // Route untuk Changelogs & Open Source
    object Changelogs : NavigationRoute("changelogs_screen", R.string.sect_general, Icons.Filled.History, Icons.Outlined.History)

    object OpenSource : NavigationRoute("opensource_screen", R.string.sect_general, Icons.Filled.Code, Icons.Outlined.Code)

    // Pusat Bantuan & Dokumentasi (in-app help untuk semua menu)
    object HelpCenter : NavigationRoute("help_center", R.string.help_center_title, Icons.Filled.HelpOutline, Icons.Outlined.HelpOutline)
}

@Composable
fun ZuanKernelManagerNavHost(
    navController: NavHostController,
    hazeState: HazeState,
) {
    NavHost(navController = navController, startDestination = NavigationRoute.Overall.route) {
        composable(NavigationRoute.Overall.route) { OverallScreen(navController = navController, hazeState = hazeState) }
        composable(NavigationRoute.Dashboard.route) { HomeScreen(navController = navController, hazeState = hazeState) }
        composable(NavigationRoute.SoC.route) { SoCScreen(navController = navController, hazeState = hazeState) }
        composable(NavigationRoute.Settings.route) { SettingsScreen(navController = navController, hazeState = hazeState) }
        composable(NavigationRoute.About.route) { AboutScreen(navController = navController, hazeState = hazeState) }
        composable(NavigationRoute.WallpaperStyle.route) { WallpaperStyleScreen(navController = navController) }
        composable(NavigationRoute.General.route) { GeneralScreen(navController = navController) }
        composable(NavigationRoute.Terminal.route) { TerminalScreen() }
        composable(NavigationRoute.SetEdit.route) { SetEditScreen(navController = navController) }
        composable(NavigationRoute.FpsManager.route) { FpsManagerScreen(navController = navController) }

        // ProcessManager dengan hazeState parameter
        composable(NavigationRoute.ProcessManager.route) {
            ProcessManagerScreen(
                navController = navController,
                hazeState = hazeState,
            )
        }

        composable(NavigationRoute.KernelFlasher.route) { KernelFlasherScreen(rootNavController = navController) }

        composable(NavigationRoute.ActivityLauncher.route) { ActivityLauncherScreen(rootNavController = navController) }
        composable(NavigationRoute.KsuWebUI.route) { KsuWebuiScreen(navController = navController) }
        composable(NavigationRoute.Adreno.route) { AdrenoScreen(navController = navController) }
        composable(NavigationRoute.Mtk.route) { MtkScreen(navController = navController) }

        // System Tools
        composable(NavigationRoute.DebloatFreeze.route) { DebloatFreezeScreen(navController = navController) }
        composable(NavigationRoute.ThermalDevices.route) { ThermalDevicesScreen(navController = navController) }
        composable(NavigationRoute.Display.route) { DisplayScreen(navController = navController) }
        composable(NavigationRoute.BatteryController.route) { BatteryControllerScreen(navController = navController) }
        composable(NavigationRoute.DozeMode.route) { DozeModeScreen(navController = navController) }
        composable(NavigationRoute.Dex2oat.route) { Dex2oatScreen(navController = navController) }
        composable(NavigationRoute.AppManager.route) { AppManagerScreen() }
        composable(NavigationRoute.MiuiThermal.route) { MiuiThermalScreen() }

        // [FIXED] ScreenTime, BatteryUsage, & ChargeStats sekarang meneruskan hazeState
        composable(NavigationRoute.ScreenTime.route) {
            ScreenTimeScreen(navController = navController, hazeState = hazeState)
        }
        composable(NavigationRoute.BatteryUsage.route) {
            BatteryUsageScreen(navController = navController, hazeState = hazeState)
        }
        composable(NavigationRoute.ChargeStats.route) {
            ChargeStatsScreen(navController = navController, hazeState = hazeState)
        }
        composable(NavigationRoute.NetworkTraffic.route) {
            NetworkTrafficScreen(navController = navController, hazeState = hazeState)
        }
        composable(NavigationRoute.WakelockAnalyzer.route) {
            WakelockAnalyzerScreen(navController = navController, hazeState = hazeState)
        }
        composable(NavigationRoute.Storage.route) {
            StorageScreen(navController = navController, hazeState = hazeState)
        }
        composable(NavigationRoute.GameSpace.route) {
            GameSpaceScreen(navController = navController, hazeState = hazeState)
        }

        // Changelogs & Open Source
        composable(NavigationRoute.Changelogs.route) {
            ChangelogsScreen(navController = navController, hazeState = hazeState)
        }
        composable(NavigationRoute.OpenSource.route) {
            OpenSourceScreen(navController = navController, hazeState = hazeState)
        }
    }
}
