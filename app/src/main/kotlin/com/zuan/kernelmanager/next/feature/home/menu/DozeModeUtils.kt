/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.home.menu

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.zuan.kernelmanager.next.library.shell.suBlocking

data class DozeApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val isWhitelisted: Boolean,
    val importance: String
)

object DozeModeUtils {

    private fun isSafePackageName(packageName: String): Boolean =
        packageName.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+"))

    private const val GMS_PACKAGE = "com.google.android.gms"

    fun getGmsDozeMode(): String {
        return try {
            val bgResult = suBlocking("cmd appops get $GMS_PACKAGE RUN_IN_BACKGROUND").outputString
            val wakeResult = suBlocking("cmd appops get $GMS_PACKAGE WAKE_LOCK").outputString
            
            when {
                wakeResult.contains("ignore") -> "Aggressive"
                bgResult.contains("ignore") -> "Standard"
                else -> "Default"
            }
        } catch (e: Exception) {
            "Default"
        }
    }

    fun applyGmsDozeMode(mode: String): Boolean {
        suBlocking("dumpsys deviceidle whitelist -$GMS_PACKAGE")
        
        val result = when (mode) {
            "Aggressive" -> {
                suBlocking(
                    "cmd appops set $GMS_PACKAGE RUN_IN_BACKGROUND ignore",
                    "cmd appops set $GMS_PACKAGE WAKE_LOCK ignore",
                    "cmd appops set $GMS_PACKAGE ALARM_WAKEUP ignore",
                    "am set-standby-bucket $GMS_PACKAGE restricted"
                )
            }
            "Standard" -> {
                suBlocking(
                    "cmd appops set $GMS_PACKAGE RUN_IN_BACKGROUND ignore",
                    "cmd appops set $GMS_PACKAGE WAKE_LOCK allow",
                    "cmd appops set $GMS_PACKAGE ALARM_WAKEUP allow",
                    "am set-standby-bucket $GMS_PACKAGE restricted"
                )
            }
            else -> {
                suBlocking(
                    "cmd appops set $GMS_PACKAGE RUN_IN_BACKGROUND allow",
                    "cmd appops set $GMS_PACKAGE WAKE_LOCK allow",
                    "cmd appops set $GMS_PACKAGE ALARM_WAKEUP allow",
                    "am set-standby-bucket $GMS_PACKAGE active"
                )
            }
        }
        return result.isSuccess
    }

    fun getDozeWhitelist(context: Context): List<DozeApp> {
        val apps = mutableListOf<DozeApp>()
        try {
            val result = suBlocking("dumpsys deviceidle whitelist")
            if (!result.isSuccess) return emptyList()
            
            val whitelistedPackages = result.out
                .filter { it.contains("system-ex:") || it.contains("user:") }
                .map { it.substringAfterLast(" ").trim() }
                .toSet()
            
            val pm = context.packageManager
            val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
            
            packages.forEach { pkg ->
                try {
                    val appInfo = pkg.applicationInfo ?: return@forEach
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val isWhitelisted = whitelistedPackages.contains(pkg.packageName)
                    
                    apps.add(DozeApp(
                        packageName = pkg.packageName,
                        label = label,
                        isSystem = isSystem,
                        isWhitelisted = isWhitelisted,
                        importance = if (isSystem) "System" else "User"
                    ))
                } catch (e: Exception) { android.util.Log.w("ZKM", "Optional operation failed; continuing", e) }
            }
        } catch (e: Exception) {
            android.util.Log.e("ZKM", "Unhandled operation failure", e)
        }
        return apps.sortedBy { it.label }
    }

    fun addToWhitelist(packageName: String): Boolean {
        if (!isSafePackageName(packageName)) return false
        return suBlocking("dumpsys deviceidle whitelist +$packageName").isSuccess
    }

    fun removeFromWhitelist(packageName: String): Boolean {
        if (!isSafePackageName(packageName)) return false
        return suBlocking("dumpsys deviceidle whitelist -$packageName").isSuccess
    }

    fun getDozeState(): String {
        val result = suBlocking("dumpsys deviceidle state")
        return if (result.isSuccess) {
            val output = result.outputString
            when {
                output.contains("ACTIVE") -> "Active"
                output.contains("IDLE_PENDING") -> "Idle Pending"
                output.contains("SENSING") -> "Sensing"
                output.contains("LOCATING") -> "Locating"
                output.contains("IDLE") -> "Idle"
                output.contains("IDLE_MAINTENANCE") -> "Maintenance"
                else -> "Unknown"
            }
        } else "Unknown"
    }

    fun forceIdle(): Boolean = suBlocking("dumpsys deviceidle force-idle").isSuccess
    fun unforceIdle(): Boolean = suBlocking("dumpsys deviceidle unforce").isSuccess
    fun stepIdleState(): Boolean = suBlocking("dumpsys deviceidle step").isSuccess

    fun getDozeSettings(): DozeSettings {
        val enabledResult = suBlocking("settings get global device_idle_enabled")
        val enabled = enabledResult.isSuccess && enabledResult.outputString.trim() != "0"
        
        val aggressiveResult = suBlocking("settings get global device_idle_aggressive")
        val aggressive = aggressiveResult.isSuccess && aggressiveResult.outputString.trim() == "1"
        
        return DozeSettings(
            isEnabled = enabled,
            isAggressive = aggressive,
            lightDozeEnabled = true,
            deepDozeEnabled = true
        )
    }

    fun setDozeEnabled(enabled: Boolean): Boolean {
        val value = if (enabled) "1" else "0"
        return suBlocking("settings put global device_idle_enabled $value").isSuccess
    }

    fun setAggressiveDoze(enabled: Boolean): Boolean {
        val value = if (enabled) "1" else "0"
        return suBlocking("settings put global device_idle_aggressive $value").isSuccess
    }

    fun getBatteryOptimizationStatus(context: Context): List<BatteryOptimizedApp> {
        val apps = mutableListOf<BatteryOptimizedApp>()
        try {
            val result = suBlocking("dumpsys deviceidle")
            val output = result.outputString
            
            val pm = context.packageManager
            val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
            
            packages.forEach { pkg ->
                try {
                    val appInfo = pkg.applicationInfo ?: return@forEach
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    
                    val isOptimized = !output.contains("Whitelist (except idle): ${pkg.packageName}") &&
                                     !output.contains("Whitelist (all): ${pkg.packageName}")
                    
                    apps.add(BatteryOptimizedApp(
                        packageName = pkg.packageName,
                        label = label,
                        isSystem = isSystem,
                        isOptimized = isOptimized
                    ))
                } catch (e: Exception) { android.util.Log.w("ZKM", "Optional operation failed; continuing", e) }
            }
        } catch (e: Exception) {
            android.util.Log.e("ZKM", "Unhandled operation failure", e)
        }
        return apps.sortedBy { it.label }
    }

    fun setBatteryOptimization(packageName: String, optimize: Boolean): Boolean {
        val cmd = if (optimize) "cmd appops set $packageName RUN_IN_BACKGROUND ignore"
        else "cmd appops set $packageName RUN_IN_BACKGROUND allow"
        return suBlocking(cmd).isSuccess
    }

    fun getDozeStats(): DozeStats {
        val result = suBlocking("dumpsys deviceidle")
        val output = result.outputString
        
        val idleCount = Regex("Idle count: (\\d+)").find(output)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val lightIdleCount = Regex("Light idle count: (\\d+)").find(output)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        
        return DozeStats(idleCount, lightIdleCount)
    }

    fun resetDozeStats(): Boolean = suBlocking("dumpsys deviceidle reset").isSuccess

    fun getIdleTimeouts(): IdleTimeouts {
        val result = suBlocking("settings get global device_idle_constants")
        val output = result.outputString
        
        return IdleTimeouts(
            lightIdleAfterInactive = parseTimeout(output, "light_idle_after_inactive_to"),
            lightIdlePreIdle = parseTimeout(output, "light_pre_idle_to"),
            lightIdleTimeout = parseTimeout(output, "light_idle_to"),
            lightIdleFactor = parseFloat(output, "light_idle_factor"),
            minLightMaintenance = parseTimeout(output, "min_light_maintenance_time"),
            minDeepMaintenance = parseTimeout(output, "min_deep_maintenance_time")
        )
    }

    private fun parseTimeout(output: String, key: String): Long {
        val regex = "$key=(\\d+)".toRegex()
        return regex.find(output)?.groupValues?.get(1)?.toLongOrNull() ?: 0
    }

    private fun parseFloat(output: String, key: String): Float {
        val regex = "$key=(\\d+\\.?\\d*)".toRegex()
        return regex.find(output)?.groupValues?.get(1)?.toFloatOrNull() ?: 1.0f
    }
}

data class DozeSettings(
    val isEnabled: Boolean,
    val isAggressive: Boolean,
    val lightDozeEnabled: Boolean,
    val deepDozeEnabled: Boolean
)

data class BatteryOptimizedApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val isOptimized: Boolean
)

data class DozeStats(
    val idleCount: Int,
    val lightIdleCount: Int
)

data class IdleTimeouts(
    val lightIdleAfterInactive: Long,
    val lightIdlePreIdle: Long,
    val lightIdleTimeout: Long,
    val lightIdleFactor: Float,
    val minLightMaintenance: Long,
    val minDeepMaintenance: Long
)
