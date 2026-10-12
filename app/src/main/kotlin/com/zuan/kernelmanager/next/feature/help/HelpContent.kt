/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.help

import androidx.annotation.StringRes
import com.zuan.kernelmanager.next.R

/** One in-app help topic. Content lives in strings.xml (help_* keys) so the
 *  runtime language-pack system can translate it like any other string. */
data class HelpTopic(
    val route: String,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    @StringRes val bulletRes: List<Int>,
    @StringRes val warningRes: Int? = null,
)

data class HelpFaq(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
)

object HelpContent {

    val topics: List<HelpTopic> = listOf(
        HelpTopic(
            route = "overall_screen",
            titleRes = R.string.help_overall_title,
            descRes = R.string.help_overall_desc,
            bulletRes = listOf(R.string.help_overall_b1, R.string.help_overall_b2, R.string.help_overall_b3, R.string.help_overall_b4, R.string.help_overall_b5),
            warningRes = null,
        ),
        HelpTopic(
            route = "dashboard_grid",
            titleRes = R.string.help_dashboard_title,
            descRes = R.string.help_dashboard_desc,
            bulletRes = listOf(R.string.help_dashboard_b1, R.string.help_dashboard_b2, R.string.help_dashboard_b3),
            warningRes = null,
        ),
        HelpTopic(
            route = "soc_screen",
            titleRes = R.string.help_soc_title,
            descRes = R.string.help_soc_desc,
            bulletRes = listOf(R.string.help_soc_b1, R.string.help_soc_b2, R.string.help_soc_b3, R.string.help_soc_b4, R.string.help_soc_b5),
            warningRes = R.string.help_soc_warn,
        ),
        HelpTopic(
            route = "settings_screen",
            titleRes = R.string.help_settings_title,
            descRes = R.string.help_settings_desc,
            bulletRes = listOf(R.string.help_settings_b1, R.string.help_settings_b2, R.string.help_settings_b3, R.string.help_settings_b4),
            warningRes = null,
        ),
        HelpTopic(
            route = "about_screen",
            titleRes = R.string.help_about_title,
            descRes = R.string.help_about_desc,
            bulletRes = listOf(R.string.help_about_b1, R.string.help_about_b2, R.string.help_about_b3),
            warningRes = null,
        ),
        HelpTopic(
            route = "wallpaper_style_screen",
            titleRes = R.string.help_wallpaper_title,
            descRes = R.string.help_wallpaper_desc,
            bulletRes = listOf(R.string.help_wallpaper_b1, R.string.help_wallpaper_b2, R.string.help_wallpaper_b3, R.string.help_wallpaper_b4),
            warningRes = R.string.help_wallpaper_warn,
        ),
        HelpTopic(
            route = "general_screen",
            titleRes = R.string.help_general_title,
            descRes = R.string.help_general_desc,
            bulletRes = listOf(R.string.help_general_b1, R.string.help_general_b2),
            warningRes = null,
        ),
        HelpTopic(
            route = "terminal_screen",
            titleRes = R.string.help_terminal_title,
            descRes = R.string.help_terminal_desc,
            bulletRes = listOf(R.string.help_terminal_b1, R.string.help_terminal_b2, R.string.help_terminal_b3, R.string.help_terminal_b4),
            warningRes = R.string.help_terminal_warn,
        ),
        HelpTopic(
            route = "SetEdit",
            titleRes = R.string.help_setedit_title,
            descRes = R.string.help_setedit_desc,
            bulletRes = listOf(R.string.help_setedit_b1, R.string.help_setedit_b2, R.string.help_setedit_b3),
            warningRes = R.string.help_setedit_warn,
        ),
        HelpTopic(
            route = "fps_manager",
            titleRes = R.string.help_fps_title,
            descRes = R.string.help_fps_desc,
            bulletRes = listOf(R.string.help_fps_b1, R.string.help_fps_b2, R.string.help_fps_b3, R.string.help_fps_b4, R.string.help_fps_b5),
            warningRes = R.string.help_fps_warn,
        ),
        HelpTopic(
            route = "game_space_screen",
            titleRes = R.string.help_gamespace_title,
            descRes = R.string.help_gamespace_desc,
            bulletRes = listOf(R.string.help_gamespace_b1, R.string.help_gamespace_b2, R.string.help_gamespace_b3, R.string.help_gamespace_b4, R.string.help_gamespace_b5, R.string.help_gamespace_b6, R.string.help_gamespace_b7),
            warningRes = R.string.help_gamespace_warn,
        ),
        HelpTopic(
            route = "process_manager",
            titleRes = R.string.help_process_title,
            descRes = R.string.help_process_desc,
            bulletRes = listOf(R.string.help_process_b1, R.string.help_process_b2, R.string.help_process_b3),
            warningRes = R.string.help_process_warn,
        ),
        HelpTopic(
            route = "kernel_flasher",
            titleRes = R.string.help_flasher_title,
            descRes = R.string.help_flasher_desc,
            bulletRes = listOf(R.string.help_flasher_b1, R.string.help_flasher_b2, R.string.help_flasher_b3),
            warningRes = R.string.help_flasher_warn,
        ),
        HelpTopic(
            route = "activity_launcher",
            titleRes = R.string.help_launcher_title,
            descRes = R.string.help_launcher_desc,
            bulletRes = listOf(R.string.help_launcher_b1, R.string.help_launcher_b2, R.string.help_launcher_b3),
            warningRes = null,
        ),
        HelpTopic(
            route = "ksu_web",
            titleRes = R.string.help_ksuweb_title,
            descRes = R.string.help_ksuweb_desc,
            bulletRes = listOf(R.string.help_ksuweb_b1, R.string.help_ksuweb_b2, R.string.help_ksuweb_b3),
            warningRes = null,
        ),
        HelpTopic(
            route = "adreno_screen",
            titleRes = R.string.help_adreno_title,
            descRes = R.string.help_adreno_desc,
            bulletRes = listOf(R.string.help_adreno_b1, R.string.help_adreno_b2, R.string.help_adreno_b3, R.string.help_adreno_b4, R.string.help_adreno_b5),
            warningRes = R.string.help_adreno_warn,
        ),
        HelpTopic(
            route = "mtk_screen",
            titleRes = R.string.help_mtk_title,
            descRes = R.string.help_mtk_desc,
            bulletRes = listOf(R.string.help_mtk_b1, R.string.help_mtk_b2, R.string.help_mtk_b3, R.string.help_mtk_b4, R.string.help_mtk_b5),
            warningRes = R.string.help_mtk_warn,
        ),
        HelpTopic(
            route = "debloat_freeze_screen",
            titleRes = R.string.help_debloat_title,
            descRes = R.string.help_debloat_desc,
            bulletRes = listOf(R.string.help_debloat_b1, R.string.help_debloat_b2, R.string.help_debloat_b3),
            warningRes = R.string.help_debloat_warn,
        ),
        HelpTopic(
            route = "thermal_devices_screen",
            titleRes = R.string.help_thermaldev_title,
            descRes = R.string.help_thermaldev_desc,
            bulletRes = listOf(R.string.help_thermaldev_b1, R.string.help_thermaldev_b2, R.string.help_thermaldev_b3),
            warningRes = R.string.help_thermaldev_warn,
        ),
        HelpTopic(
            route = "display_screen",
            titleRes = R.string.help_display_title,
            descRes = R.string.help_display_desc,
            bulletRes = listOf(R.string.help_display_b1, R.string.help_display_b2, R.string.help_display_b3, R.string.help_display_b4),
            warningRes = R.string.help_display_warn,
        ),
        HelpTopic(
            route = "battery_controller_screen",
            titleRes = R.string.help_battery_title,
            descRes = R.string.help_battery_desc,
            bulletRes = listOf(R.string.help_battery_b1, R.string.help_battery_b2, R.string.help_battery_b3),
            warningRes = R.string.help_battery_warn,
        ),
        HelpTopic(
            route = "doze_mode_screen",
            titleRes = R.string.help_doze_title,
            descRes = R.string.help_doze_desc,
            bulletRes = listOf(R.string.help_doze_b1, R.string.help_doze_b2, R.string.help_doze_b3),
            warningRes = null,
        ),
        HelpTopic(
            route = "dex2oat_screen",
            titleRes = R.string.help_dex2oat_title,
            descRes = R.string.help_dex2oat_desc,
            bulletRes = listOf(R.string.help_dex2oat_b1, R.string.help_dex2oat_b2, R.string.help_dex2oat_b3),
            warningRes = R.string.help_dex2oat_warn,
        ),
        HelpTopic(
            route = "app_manager",
            titleRes = R.string.help_appmgr_title,
            descRes = R.string.help_appmgr_desc,
            bulletRes = listOf(R.string.help_appmgr_b1, R.string.help_appmgr_b2, R.string.help_appmgr_b3),
            warningRes = null,
        ),
        HelpTopic(
            route = "miui_thermal",
            titleRes = R.string.help_miuithm_title,
            descRes = R.string.help_miuithm_desc,
            bulletRes = listOf(R.string.help_miuithm_b1, R.string.help_miuithm_b2, R.string.help_miuithm_b3),
            warningRes = R.string.help_miuithm_warn,
        ),
        HelpTopic(
            route = "screen_time_screen",
            titleRes = R.string.help_screentime_title,
            descRes = R.string.help_screentime_desc,
            bulletRes = listOf(R.string.help_screentime_b1, R.string.help_screentime_b2, R.string.help_screentime_b3),
            warningRes = null,
        ),
        HelpTopic(
            route = "battery_usage_screen",
            titleRes = R.string.help_batusage_title,
            descRes = R.string.help_batusage_desc,
            bulletRes = listOf(R.string.help_batusage_b1, R.string.help_batusage_b2),
            warningRes = null,
        ),
        HelpTopic(
            route = "charge_stats_screen",
            titleRes = R.string.help_chargestat_title,
            descRes = R.string.help_chargestat_desc,
            bulletRes = listOf(R.string.help_chargestat_b1, R.string.help_chargestat_b2, R.string.help_chargestat_b3),
            warningRes = null,
        ),
        HelpTopic(
            route = "network_traffic_screen",
            titleRes = R.string.help_nettraffic_title,
            descRes = R.string.help_nettraffic_desc,
            bulletRes = listOf(R.string.help_nettraffic_b1, R.string.help_nettraffic_b2),
            warningRes = null,
        ),
        HelpTopic(
            route = "wakelock_analyzer_screen",
            titleRes = R.string.help_wakelock_title,
            descRes = R.string.help_wakelock_desc,
            bulletRes = listOf(R.string.help_wakelock_b1, R.string.help_wakelock_b2),
            warningRes = null,
        ),
        HelpTopic(
            route = "storage_screen",
            titleRes = R.string.help_storage_title,
            descRes = R.string.help_storage_desc,
            bulletRes = listOf(R.string.help_storage_b1, R.string.help_storage_b2, R.string.help_storage_b3),
            warningRes = null,
        ),
        HelpTopic(
            route = "changelogs_screen",
            titleRes = R.string.help_changelogs_title,
            descRes = R.string.help_changelogs_desc,
            bulletRes = listOf(R.string.help_changelogs_b1, R.string.help_changelogs_b2),
            warningRes = null,
        ),
        HelpTopic(
            route = "opensource_screen",
            titleRes = R.string.help_oss_title,
            descRes = R.string.help_oss_desc,
            bulletRes = listOf(R.string.help_oss_b1, R.string.help_oss_b2),
            warningRes = null,
        ),
        HelpTopic(
            route = "help_center",
            titleRes = R.string.help_help_title,
            descRes = R.string.help_help_desc,
            bulletRes = listOf(R.string.help_help_b1, R.string.help_help_b2, R.string.help_help_b3),
            warningRes = null,
        ),
    )

    val faqs: List<HelpFaq> = listOf(
        HelpFaq(R.string.help_faq_root_title, R.string.help_faq_root_body),
        HelpFaq(R.string.help_faq_safety_title, R.string.help_faq_safety_body),
        HelpFaq(R.string.help_faq_lang_title, R.string.help_faq_lang_body),
        HelpFaq(R.string.help_faq_perf_title, R.string.help_faq_perf_body),
        HelpFaq(R.string.help_faq_gs_shizuku_title, R.string.help_faq_gs_shizuku_body),
        HelpFaq(R.string.help_faq_gs_modes_title, R.string.help_faq_gs_modes_body),
    )

    /** Topic for a navigation route, or null when the route has no help yet. */
    fun forRoute(route: String?): HelpTopic? = topics.firstOrNull { it.route == route }
}
