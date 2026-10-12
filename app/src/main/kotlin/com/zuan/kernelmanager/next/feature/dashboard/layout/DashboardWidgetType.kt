/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.dashboard.layout

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Monitor
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * =====================================================================================
 * WIDGET REGISTRY
 * Satu-satunya sumber kebenaran untuk semua widget dashboard.
 *
 * Cara menambah widget baru:
 * 1. Tambah 1 entry enum di sini.
 * 2. Tambah 1 branch `when` di renderer widget pada OverallScreen.kt.
 * Selesai. Urutan, drag & drop, persistence, dan Widget Gallery otomatis mengenalinya.
 * =====================================================================================
 *
 * @param title        Judul widget yang tampil di Widget Gallery.
 * @param description  Deskripsi singkat di Widget Gallery.
 * @param icon         Ikon representatif di Widget Gallery.
 * @param defaultVisible Apakah widget tampil secara default (fresh install).
 * @param removable    false = widget inti yang tidak bisa disembunyikan (mis. Device Hero).
 * @param isExtra      true = widget tambahan. Tidak tampil default; ditambahkan lewat menu
 *                     "Add Widget" (tekan-tahan lalu lepas di dashboard). Widget tambahan yang
 *                     sudah dipasang baru muncul di Widget Gallery.
 */
enum class DashboardWidgetType(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val defaultVisible: Boolean = true,
    val removable: Boolean = true,
    val isExtra: Boolean = false
) {
    DEVICE_HERO(
        title = "Device Info",
        description = "Model, SoC, and kernel version",
        icon = Icons.Rounded.Smartphone,
        removable = false // Widget inti, tidak bisa dihapus
    ),
    BATTERY_DASHBOARD(
        title = "Battery Dashboard",
        description = "Battery weather, ongoing info & detailed statistics",
        icon = Icons.Rounded.BatteryChargingFull
    ),
    CPU_MONITOR(
        title = "CPU Monitor",
        description = "Per-core load, frequency & top processes",
        icon = Icons.Rounded.Memory
    ),
    GPU(
        title = "Graphics (GPU)",
        description = "GPU load and frequency",
        icon = Icons.Rounded.DeveloperBoard
    ),
    MEMORY(
        title = "Memory",
        description = "RAM, swap & ZRAM usage",
        icon = Icons.Rounded.Storage
    ),
    VM_PARAMS(
        title = "VM Parameters",
        description = "Tunable virtual memory kernel",
        icon = Icons.Rounded.Tune
    ),
    DISPLAY_INFO(
        title = "Display",
        description = "Resolution, refresh rate & density",
        icon = Icons.Rounded.Monitor
    ),
    STORAGE_INTELLIGENCE(
        title = "Storage Intelligence", description = "Capacity, free space and filesystem pressure", icon = Icons.Rounded.Storage
    ),
    UFS_HEALTH(
        title = "UFS Health",
        description = "UFS lifetime health and storage temperature when exposed by the kernel",
        icon = Icons.Rounded.Storage
    ),

    // ---- WIDGET TAMBAHAN (Add Widget) ----
    THERMAL_MONITOR(
        title = "Thermal Monitor",
        description = "Hottest zone, average temperature & throttling state",
        icon = Icons.Rounded.Thermostat,
        defaultVisible = false,
        isExtra = true
    ),
    POWER_DRAW(
        title = "Power Draw",
        description = "Live battery wattage, current, voltage & temperature",
        icon = Icons.Rounded.Bolt,
        defaultVisible = false,
        isExtra = true
    ),
    SYSTEM_UPTIME(
        title = "Uptime & Deep Sleep",
        description = "Uptime, awake time and deep sleep ratio",
        icon = Icons.Rounded.Schedule,
        defaultVisible = false,
        isExtra = true
    ),
    SYSTEM_PRESSURE(
        title = "System Pressure",
        description = "Kernel PSI for CPU, memory & I/O plus load average",
        icon = Icons.Rounded.Speed,
        defaultVisible = false,
        isExtra = true
    ),
    ROOT_SECURITY(
        title = "Root & Security",
        description = "Root access, SELinux mode, security patch & verified boot",
        icon = Icons.Rounded.Security,
        defaultVisible = false,
        isExtra = true
    );

    companion object {
        /** Urutan default dashboard (sesuai layout lama). */
        val DEFAULT_ORDER: List<DashboardWidgetType> = entries.toList()

        /** Widget yang disembunyikan secara default. */
        val DEFAULT_HIDDEN: Set<DashboardWidgetType> =
            entries.filter { !it.defaultVisible }.toSet()
    }
}


fun DashboardWidgetType.localizedTitleRes(): Int = when (this) {
    DashboardWidgetType.DEVICE_HERO -> com.zuan.kernelmanager.next.R.string.overall_device_info
    DashboardWidgetType.BATTERY_DASHBOARD -> com.zuan.kernelmanager.next.R.string.overall_battery
    DashboardWidgetType.CPU_MONITOR -> com.zuan.kernelmanager.next.R.string.overall_processor
    DashboardWidgetType.GPU -> com.zuan.kernelmanager.next.R.string.overall_graphics
    DashboardWidgetType.MEMORY -> com.zuan.kernelmanager.next.R.string.overall_memory
    DashboardWidgetType.VM_PARAMS -> com.zuan.kernelmanager.next.R.string.overall_vm_params
    DashboardWidgetType.DISPLAY_INFO -> com.zuan.kernelmanager.next.R.string.overall_display
    DashboardWidgetType.STORAGE_INTELLIGENCE -> com.zuan.kernelmanager.next.R.string.lp_overall_storage_intelligence
    DashboardWidgetType.UFS_HEALTH -> com.zuan.kernelmanager.next.R.string.lp_overall_ufs_health
    DashboardWidgetType.THERMAL_MONITOR -> com.zuan.kernelmanager.next.R.string.lp_widget_thermal_monitor
    DashboardWidgetType.POWER_DRAW -> com.zuan.kernelmanager.next.R.string.lp_widget_power_draw
    DashboardWidgetType.SYSTEM_UPTIME -> com.zuan.kernelmanager.next.R.string.lp_widget_system_uptime
    DashboardWidgetType.SYSTEM_PRESSURE -> com.zuan.kernelmanager.next.R.string.lp_widget_system_pressure
    DashboardWidgetType.ROOT_SECURITY -> com.zuan.kernelmanager.next.R.string.lp_widget_root_security
}

fun DashboardWidgetType.localizedDescriptionRes(): Int = when (this) {
    DashboardWidgetType.STORAGE_INTELLIGENCE -> com.zuan.kernelmanager.next.R.string.lp_overall_desc_storage_intelligence
    DashboardWidgetType.UFS_HEALTH -> com.zuan.kernelmanager.next.R.string.lp_overall_desc_ufs_health
    DashboardWidgetType.THERMAL_MONITOR -> com.zuan.kernelmanager.next.R.string.lp_widget_desc_thermal_monitor
    DashboardWidgetType.POWER_DRAW -> com.zuan.kernelmanager.next.R.string.lp_widget_desc_power_draw
    DashboardWidgetType.SYSTEM_UPTIME -> com.zuan.kernelmanager.next.R.string.lp_widget_desc_system_uptime
    DashboardWidgetType.SYSTEM_PRESSURE -> com.zuan.kernelmanager.next.R.string.lp_widget_desc_system_pressure
    DashboardWidgetType.ROOT_SECURITY -> com.zuan.kernelmanager.next.R.string.lp_widget_desc_root_security
    else -> 0
}
