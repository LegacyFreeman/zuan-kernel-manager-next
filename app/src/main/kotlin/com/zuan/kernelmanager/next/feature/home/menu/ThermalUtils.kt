/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.home.menu

import com.zuan.kernelmanager.next.core.kernel.KernelControlEngine
import com.zuan.kernelmanager.next.core.kernel.KernelNodePolicy
import com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime
import java.io.File
import kotlin.math.abs


data class ThermalZone(
    val id: Int,
    val name: String,
    val type: String,
    val temperature: Int,
    val path: String,
    val isEnabled: Boolean
)

data class ThermalCoolingDevice(
    val id: Int,
    val name: String,
    val type: String,
    val curState: Int,
    val maxState: Int,
    val path: String
)

data class TripPoint(
    val id: Int,
    val temperature: Int,
    val type: String
)

object ThermalUtils {

    private const val THERMAL_PATH = "/sys/class/thermal"
    private const val COOLING_PATH = "/sys/class/thermal/cooling_device"

    private fun normalizeTemperature(rawTemp: Int): Int {
        var temp = if (abs(rawTemp) > 1000) rawTemp / 1000 else rawTemp
        if (temp < -40 || temp > 200) temp = 0
        return temp
    }

    fun getAllThermalZones(): List<ThermalZone> {
        val zones = mutableListOf<ThermalZone>()
        return try {
            val thermalDir = File(THERMAL_PATH)
            if (!thermalDir.exists()) return emptyList()
            val zoneDirs = thermalDir.listFiles { file ->
                file.isDirectory && file.name.startsWith("thermal_zone")
            }?.sortedBy { it.name.removePrefix("thermal_zone").toIntOrNull() ?: 0 } ?: emptyList()
            zoneDirs.forEach { zoneDir ->
                val id = zoneDir.name.removePrefix("thermal_zone").toIntOrNull() ?: 0
                val name = readThermalFile(zoneDir, "type") ?: "Unknown"
                val rawTemp = readThermalFile(zoneDir, "temp")?.toIntOrNull() ?: 0
                zones += ThermalZone(
                    id = id,
                    name = getDisplayName(name),
                    type = categorizeThermalType(name),
                    temperature = normalizeTemperature(rawTemp),
                    path = zoneDir.absolutePath,
                    isEnabled = readThermalFile(zoneDir, "mode")?.trim() != "disabled"
                )
            }
            zones
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun getCoolingDevices(): List<ThermalCoolingDevice> {
        val devices = mutableListOf<ThermalCoolingDevice>()
        return try {
            val coolingDir = File(COOLING_PATH)
            if (!coolingDir.exists()) return emptyList()
            val deviceDirs = coolingDir.listFiles { file ->
                file.isDirectory && file.name.startsWith("cooling_device")
            }?.sortedBy { it.name.removePrefix("cooling_device").toIntOrNull() ?: 0 } ?: emptyList()
            deviceDirs.forEach { deviceDir ->
                val id = deviceDir.name.removePrefix("cooling_device").toIntOrNull() ?: 0
                val name = readThermalFile(deviceDir, "type") ?: "Unknown"
                devices += ThermalCoolingDevice(
                    id = id,
                    name = name,
                    type = categorizeCoolingType(name),
                    curState = readThermalFile(deviceDir, "cur_state")?.toIntOrNull() ?: 0,
                    maxState = readThermalFile(deviceDir, "max_state")?.toIntOrNull() ?: 0,
                    path = deviceDir.absolutePath
                )
            }
            devices
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun setThermalZoneState(zoneId: Int, enabled: Boolean): Boolean {
        if (zoneId < 0 || zoneId > 4096) return false
        return writeNode("/sys/class/thermal/thermal_zone$zoneId/mode", if (enabled) "enabled" else "disabled")
    }

    fun setCoolingDeviceState(deviceId: Int, state: Int): Boolean {
        if (deviceId < 0 || deviceId > 4096 || state !in 0..1_000_000) return false
        return writeNode("/sys/class/thermal/cooling_device$deviceId/cur_state", state.toString())
    }

    fun getThermalPolicy(): String = KernelRootRuntime.readNodeBlocking("/sys/class/thermal/thermal_policy").ifBlank { "default" }

    fun setThermalPolicy(policy: String): Boolean {
        val normalized = policy.trim()
        if (normalized.isBlank() || normalized.length > 128 || !normalized.matches(Regex("[A-Za-z0-9_.:-]+"))) return false
        return writeNode("/sys/class/thermal/thermal_policy", normalized)
    }

    fun getTripPoints(zonePath: String): List<TripPoint> {
        val canonical = KernelNodePolicy.canonical(zonePath) ?: return emptyList()
        if (!canonical.startsWith("$THERMAL_PATH/thermal_zone")) return emptyList()
        val zoneDir = File(canonical)
        val trips = mutableListOf<TripPoint>()
        for (i in 0..9) {
            val rawTemp = readThermalFile(zoneDir, "trip_point_${i}_temp")?.toIntOrNull() ?: break
            val type = readThermalFile(zoneDir, "trip_point_${i}_type") ?: "Unknown"
            trips += TripPoint(i, normalizeTemperature(rawTemp), type)
        }
        return trips
    }

    private fun readThermalFile(dir: File, filename: String): String? {
        val file = File(dir, filename)
        return try {
            if (file.exists() && file.canRead()) file.readText().trim()
            else KernelRootRuntime.readNodeBlocking(file.absolutePath).trim().takeIf { it.isNotEmpty() }
        } catch (_: Throwable) {
            null
        }
    }

    private fun categorizeThermalType(name: String): String = when {
        name.contains("cpu", true) || name.contains("cluster", true) -> "CPU"
        name.contains("gpu", true) || name.contains("graphics", true) -> "GPU"
        name.contains("battery", true) || name.contains("batt", true) -> "Battery"
        name.contains("charger", true) || name.contains("usb", true) -> "Charger"
        name.contains("skin", true) || name.contains("surface", true) -> "Skin"
        name.contains("modem", true) || name.contains("radio", true) -> "Modem"
        name.contains("wifi", true) -> "WiFi"
        name.contains("cam", true) || name.contains("camera", true) -> "Camera"
        name.contains("flash", true) || name.contains("led", true) -> "Flash"
        name.contains("pa", true) || name.contains("amplifier", true) -> "PA"
        else -> "System"
    }

    private fun categorizeCoolingType(name: String): String = when {
        name.contains("cpu", true) -> "CPU"
        name.contains("gpu", true) -> "GPU"
        name.contains("fan", true) || name.contains("cooler", true) -> "Fan"
        name.contains("backlight", true) || name.contains("brightness", true) -> "Display"
        name.contains("thermal", true) -> "Thermal"
        else -> "Other"
    }

    private fun getDisplayName(name: String): String = name.replace("_", " ")
        .replace("tsens", "Temperature Sensor")
        .replace("tz", "Zone")
        .split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

    fun isPathExist(path: String): Boolean = KernelRootRuntime.nodeExistsBlocking(path)

    private fun readStateInt(path: String, default: Int = 0): Int =
        KernelRootRuntime.readNodeBlocking(path).trim().toIntOrNull() ?: default

    private fun readStateString(path: String, default: String = ""): String =
        KernelRootRuntime.readNodeBlocking(path).trim().ifBlank { default }

    fun isPpmSupported(): Boolean = isPathExist("/proc/ppm/enabled")
    fun isGedDcsSupported(): Boolean = isPathExist("/sys/kernel/ged/hal/dcs_mode")
    fun isGpuIgnoreSupported(): Boolean = isPathExist("/proc/gpufreq/gpufreq_opp_stress_test")
    fun isDvfsSupported(): Boolean = isPathExist("/proc/mali/dvfs_enable")
    fun isGpuAlwaysOnSupported(): Boolean = isPathExist("/proc/mali/always_on")
    fun isCciSupported(): Boolean = isPathExist("/proc/cpufreq/cpufreq_cci_mode")
    fun isCpuPowerModeSupported(): Boolean = isPathExist("/proc/cpufreq/cpufreq_power_mode")
    fun isAdrenoSupported(): Boolean = isPathExist("/sys/class/kgsl/kgsl-3d0")
    fun isAdrenoLpmSupported(): Boolean = isPathExist("/sys/devices/system/cpu/qcom_lpm/parameters/prediction_disabled")
    fun isUfshcSupported(): Boolean = isPathExist("/sys/devices/platform/soc/1d84000.ufshc/clkgate_enable")
    fun isSconfigSupported(): Boolean = isPathExist("/sys/class/thermal/thermal_message/sconfig")

    fun getPpmEnabled(): Boolean = readStateInt("/proc/ppm/enabled") == 1
    fun getGedDcsDisabled(): Boolean = readStateInt("/sys/kernel/ged/hal/dcs_mode", 1) == 0
    fun getDvfsDisabled(): Boolean = readStateInt("/proc/mali/dvfs_enable", 1) == 0
    fun getGpuAlwaysOn(): Boolean = readStateInt("/proc/mali/always_on") == 1
    fun getCciModeEnabled(): Boolean = readStateInt("/proc/cpufreq/cpufreq_cci_mode") == 1
    fun getCpuPowerMode(): Float = readStateInt("/proc/cpufreq/cpufreq_power_mode").toFloat()
    fun getAdrenoThrottleDisabled(): Boolean = readStateInt("/sys/class/kgsl/kgsl-3d0/throttling", 1) == 0
    fun getAdrenoBclDisabled(): Boolean = readStateInt("/sys/class/kgsl/kgsl-3d0/bcl", 1) == 0
    fun getAdrenoPreemptDisabled(): Boolean = readStateInt("/sys/class/kgsl/kgsl-3d0/preemption", 1) == 0
    fun getAdrenoLpmPredDisabled(): Boolean = readStateString("/sys/devices/system/cpu/qcom_lpm/parameters/prediction_disabled") == "Y"
    fun getAdrenoLpmSleepDisabled(): Boolean = readStateString("/sys/devices/system/cpu/qcom_lpm/parameters/sleep_disabled") == "Y"
    fun getUfshcThrottlingDisabled(): Boolean = readStateInt("/sys/devices/platform/soc/1d84000.ufshc/clkgate_enable", 1) == 0
    fun getThermalSconfigValue(): Int = readStateInt("/sys/class/thermal/thermal_message/sconfig", -1)

    fun setPpmEnabled(enabled: Boolean) { writeNode("/proc/ppm/enabled", if (enabled) "1" else "0") }
    fun setPpmPolicy(index: Int, enabled: Boolean) {
        if (index !in 0..255) return
        writeNode("/proc/ppm/policy_status", "$index ${if (enabled) 1 else 0}")
    }
    fun setGedDcs(disabled: Boolean) { writeNode("/sys/kernel/ged/hal/dcs_mode", if (disabled) "0" else "1") }

    fun setGpuIgnore(enabled: Boolean) {
        val base = listOf(
            "/proc/gpufreq/gpufreq_opp_stress_test" to "1",
            "/proc/gpufreq/gpufreq_input_boost" to if (enabled) "0" else "1",
            "/proc/gpufreq/gpufreq_limited_thermal_ignore" to if (enabled) "1" else "0",
            "/proc/gpufreq/gpufreq_limited_oc_ignore" to if (enabled) "1" else "0",
            "/proc/gpufreq/gpufreq_limited_low_batt_volume_ignore" to if (enabled) "1" else "0",
            "/proc/gpufreq/gpufreq_limited_low_batt_volt_ignore" to if (enabled) "1" else "0",
            "/proc/gpufreqv2/gpufreqv2_opp_stress_test" to "1",
            "/proc/gpufreqv2/gpufreqv2_input_boost" to if (enabled) "0" else "1",
            "/proc/gpufreqv2/gpufreqv2_limited_thermal_ignore" to if (enabled) "1" else "0",
            "/proc/gpufreqv2/gpufreqv2_limited_oc_ignore" to if (enabled) "1" else "0",
            "/proc/gpufreqv2/gpufreqv2_limited_low_batt_volume_ignore" to if (enabled) "1" else "0",
            "/proc/gpufreqv2/gpufreqv2_limited_low_batt_volt_ignore" to if (enabled) "1" else "0"
        )
        base.forEach { (path, value) -> if (isPathExist(path)) writeNode(path, value) }
    }

    fun setDvfs(disabled: Boolean) { writeNode("/proc/mali/dvfs_enable", if (disabled) "0" else "1") }
    fun setGpuAlwaysOn(enabled: Boolean) { writeNode("/proc/mali/always_on", if (enabled) "1" else "0") }
    fun setCpufreqCci(enabled: Boolean) { writeNode("/proc/cpufreq/cpufreq_cci_mode", if (enabled) "1" else "0") }
    fun setCpufreqPowerMode(mode: Int) { if (mode in 0..7) writeNode("/proc/cpufreq/cpufreq_power_mode", mode.toString()) }

    fun setPowerLevelMax(enabled: Boolean) {
        val base = "/sys/class/kgsl/kgsl-3d0"
        val levels = readStateInt("$base/num_pwrlevels", -1)
        if (levels <= 0) return
        val lowest = (levels - 1).toString()
        writeNode("$base/default_pwrlevel", lowest)
        writeNode("$base/min_pwrlevel", lowest)
        writeNode("$base/max_pwrlevel", if (enabled) "0" else "3")
        writeNode("$base/thermal_pwrlevel", "0")
    }

    fun setThrottling(disabled: Boolean) { writeNode("/sys/class/kgsl/kgsl-3d0/throttling", if (disabled) "0" else "1") }
    fun setBcl(disabled: Boolean) { writeNode("/sys/class/kgsl/kgsl-3d0/bcl", if (disabled) "0" else "1") }
    fun setPreemption(disabled: Boolean) { writeNode("/sys/class/kgsl/kgsl-3d0/preemption", if (disabled) "0" else "1") }
    fun setLpmPrediction(disabled: Boolean) { writeNode("/sys/devices/system/cpu/qcom_lpm/parameters/prediction_disabled", if (disabled) "Y" else "N") }
    fun setLpmSleep(disabled: Boolean) { writeNode("/sys/devices/system/cpu/qcom_lpm/parameters/sleep_disabled", if (disabled) "Y" else "N") }
    fun setUfshcThrottling(disabled: Boolean) { writeNode("/sys/devices/platform/soc/1d84000.ufshc/clkgate_enable", if (disabled) "0" else "1") }

    fun setThermalZoneLevel(level: Int) {
        if (level !in 0..2) return
        val zones = getAllThermalZones()
        zones.forEach { zone ->
            val shouldDisable = when (level) {
                1 -> zone.type == "CPU" || zone.type == "GPU"
                2 -> true
                else -> false
            }
            writeNode("${zone.path}/mode", if (shouldDisable) "disabled" else "enabled")
        }
    }

    fun setThermalSconfig(value: Int) { if (value in -1..1_000_000) writeNode("/sys/class/thermal/thermal_message/sconfig", value.toString()) }

    fun checkThermalProp(): String {
        val result = KernelRootRuntime.executeBlocking("getprop | grep thermal")
        return if (result.isSuccess && result.outputString.isNotBlank()) result.outputString else "No thermal prop found or error."
    }

    fun setThermalProp(disabled: Boolean) {
        val cmd = if (disabled) {
            "getprop | grep 'thermal' | cut -d '[' -f2 | cut -d ']' -f1 | grep -v 'hal' | while read -r prop; do status=\$(getprop \"\$prop\"); case \"\$status\" in running|restarting) service=\${prop:9}; [ -n \"\$service\" ] && setprop ctl.stop \"\$service\";; esac; done"
        } else {
            "getprop | grep 'thermal' | cut -d '[' -f2 | cut -d ']' -f1 | grep -v 'hal' | while read -r prop; do service=\${prop:9}; [ -n \"\$service\" ] && setprop ctl.start \"\$service\"; done"
        }
        KernelRootRuntime.executeBlocking(cmd)
    }

    fun checkThermalProcess(): String {
        val result = KernelRootRuntime.executeBlocking("ps -e | grep thermal")
        return if (result.isSuccess && result.outputString.isNotBlank()) result.outputString else "No thermal process found."
    }

    fun setThermalProcess(disabled: Boolean) {
        val cmd = if (disabled) "kill -STOP" else "kill -CONT"
        KernelRootRuntime.executeBlocking("ps -e | grep thermal | awk '{print \$2}' | while read -r pid; do [ -n \"\$pid\" ] && $cmd \"\$pid\"; done")
    }

    data class AdvanceStates(
        val ppmEnabled: Boolean = false,
        val gedDcsDisabled: Boolean = false,
        val dvfsDisabled: Boolean = false,
        val gpuAlwaysOn: Boolean = false,
        val cciModeEnabled: Boolean = false,
        val cpuPowerMode: Float = 0f,
        val adrenoThrottleDisabled: Boolean = false,
        val adrenoBclDisabled: Boolean = false,
        val adrenoPreemptDisabled: Boolean = false,
        val lpmPredDisabled: Boolean = false,
        val lpmSleepDisabled: Boolean = false,
        val ufshcThrottlingDisabled: Boolean = false,
        val sconfigValue: Int = -1
    )

    suspend fun readAdvanceStatesBatch(): AdvanceStates {
        val paths = listOf(
            "/proc/ppm/enabled", "/sys/kernel/ged/hal/dcs_mode", "/proc/mali/dvfs_enable",
            "/proc/mali/always_on", "/proc/cpufreq/cpufreq_cci_mode", "/proc/cpufreq/cpufreq_power_mode",
            "/sys/class/kgsl/kgsl-3d0/throttling", "/sys/class/kgsl/kgsl-3d0/bcl",
            "/sys/class/kgsl/kgsl-3d0/preemption", "/sys/devices/system/cpu/qcom_lpm/parameters/prediction_disabled",
            "/sys/devices/system/cpu/qcom_lpm/parameters/sleep_disabled",
            "/sys/devices/platform/soc/1d84000.ufshc/clkgate_enable",
            "/sys/class/thermal/thermal_message/sconfig"
        )
        val batch = runCatching { KernelRootRuntime.readNodes(paths) }.getOrDefault(emptyMap())
        fun intOf(path: String, default: Int) = batch[path]?.trim()?.toIntOrNull() ?: default
        return AdvanceStates(
            ppmEnabled = intOf(paths[0], 0) == 1,
            gedDcsDisabled = intOf(paths[1], 1) == 0,
            dvfsDisabled = intOf(paths[2], 1) == 0,
            gpuAlwaysOn = intOf(paths[3], 0) == 1,
            cciModeEnabled = intOf(paths[4], 0) == 1,
            cpuPowerMode = intOf(paths[5], 0).toFloat(),
            adrenoThrottleDisabled = intOf(paths[6], 1) == 0,
            adrenoBclDisabled = intOf(paths[7], 1) == 0,
            adrenoPreemptDisabled = intOf(paths[8], 1) == 0,
            lpmPredDisabled = batch[paths[9]] == "Y",
            lpmSleepDisabled = batch[paths[10]] == "Y",
            ufshcThrottlingDisabled = intOf(paths[11], 1) == 0,
            sconfigValue = intOf(paths[12], -1)
        )
    }

    private fun writeNode(path: String, value: String): Boolean {
        if (!KernelNodePolicy.isWriteAllowed(path)) return false
        return KernelControlEngine.setKernelNodeBlocking(path, value, verify = true).success
    }
}
