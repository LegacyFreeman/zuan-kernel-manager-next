/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.home.menu

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.zuan.kernelmanager.next.core.kernel.KernelBackendEngine
import com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime
import com.zuan.kernelmanager.next.core.kernel.KernelControlEngine
import java.io.File
import kotlin.math.abs

data class BatteryInfo(
    val level: Int,
    val scale: Int,
    val percentage: Int,
    val status: String,
    val health: String,
    val temperature: Float,
    val voltage: Int,
    val currentNow: Int,
    val currentAverage: Int,
    val technology: String,
    val isCharging: Boolean,
    val chargingType: String,
    val cycleCount: Int
)

data class ChargingStats(
    val maxCurrent: Int,
    val maxVoltage: Int,
    val chargingPower: Float,
    val estimatedTimeRemaining: Long
)

object BatteryControllerUtils {
    @Volatile private var activeContext: Context? = null


    // --- CONSTANTS ---
    private const val BATTERY_PATH = "/sys/class/power_supply/battery"
    private const val USB_PATH = "/sys/class/power_supply/usb"
    const val THERMAL_SCONFIG = "/sys/class/thermal/thermal_message/sconfig"
    const val BYPASS_CHARGING_PATH = "/sys/class/power_supply/battery/input_suspend"

    // Charging mutations are centralized in KernelControlEngine below.

    fun getBatteryInfo(context: Context): BatteryInfo {
        activeContext = context.applicationContext
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, intentFilter)
        
        return if (batteryStatus != null) {
            val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val percentage = (level * 100 / scale)
            
            val status = when (batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                BatteryManager.BATTERY_STATUS_FULL -> "Full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
                else -> "Unknown"
            }
            
            val health = when (batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failed"
                BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                else -> "Unknown"
            }
            
            val temp = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
            val voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
            val technology = batteryStatus.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown"
            
            val isCharging = status == "Charging" || status == "Full"
            
            val chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            val chargingType = when (chargePlug) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC Adapter"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                else -> "None"
            }
            
            val cycleCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                batteryStatus.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, 0)
            } else {
                readSysFile("$BATTERY_PATH/cycle_count")?.toIntOrNull() ?: 0
            }
            
            // Logic mA yang lebih akurat dan aman (dijadikan absolut agar angkanya cantik di UI)
            val currentNowRaw = getBatteryCurrentNow()
            val currentNow = abs(currentNowRaw) 
            
            // FIX BUG -2147483mA: Filter nilai error Int.MIN_VALUE dari sensor Android
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            var currentAverageRaw = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
            
            val currentAverage = if (currentAverageRaw == Int.MIN_VALUE || currentAverageRaw == Int.MIN_VALUE / 1000) {
                // Jika Android API gagal membaca Average, fallback baca dari kernel
                val kernelAvg = readSysFile("$BATTERY_PATH/current_avg")?.toIntOrNull()
                if (kernelAvg != null) abs(kernelAvg / 1000) else currentNow // Jika gagal total, samakan dengan currentNow
            } else {
                abs(currentAverageRaw / 1000)
            }
            
            BatteryInfo(
                level = level,
                scale = scale,
                percentage = percentage,
                status = status,
                health = health,
                temperature = temp,
                voltage = voltage, // Ini dalam mV
                currentNow = currentNow,
                currentAverage = currentAverage,
                technology = technology,
                isCharging = isCharging,
                chargingType = chargingType,
                cycleCount = cycleCount
            )
        } else {
            BatteryInfo(0, 0, 0, "Unknown", "Unknown", 0f, 0, 0, 0, "Unknown", false, "None", 0)
        }
    }

    // [PENTING] HARUS PUBLIC agar bisa dipakai BatteryMonitorService
    fun getBatteryCurrentNow(): Int {
        val paths = listOf(
            "$BATTERY_PATH/current_now",
            "/sys/class/power_supply/bms/current_now"
        )
        for (path in paths) {
            val result = readSysFile(path)?.trim()?.toIntOrNull()
            if (result != null && result != 0) {
                 return result / 1000 // Convert uA to mA
            }
        }
        return 0
    }

    fun getChargingStats(): ChargingStats {
        val maxCurrent = readSysFile("$USB_PATH/current_max")?.toIntOrNull() ?: 0
        val maxVoltage = readSysFile("$USB_PATH/voltage_max")?.toIntOrNull() ?: 0
        
        val currentNow = abs(getBatteryCurrentNow()) // dalam mA
        val voltageNowRaw = readSysFile("$BATTERY_PATH/voltage_now")?.toIntOrNull() ?: 0
        
        // FIX BUG DAYA (WATT): Normalisasi uV vs mV ke Volt
        val voltageVolts = if (voltageNowRaw > 100_000) {
            voltageNowRaw / 1_000_000f // uV to Volts
        } else {
            voltageNowRaw / 1000f // mV to Volts
        }
        val currentAmps = currentNow / 1000f // mA to Amps
        
        val power = if (currentNow != 0 && voltageVolts > 0) {
            currentAmps * voltageVolts
        } else 0f
        
        return ChargingStats(
            maxCurrent = maxCurrent / 1000,
            maxVoltage = maxVoltage / 1000,
            chargingPower = power,
            estimatedTimeRemaining = 0L
        )
    }

    // --- FITUR UTAMA ---

    fun setChargingSpeed(mA: Int): Boolean {
        val value = mA.coerceIn(100, 100000) * 1000
        val path = "$USB_PATH/current_max"
        val context = activeContext ?: com.zuan.kernelmanager.next.core.kernel.KernelRuntimeContext.context ?: return false
        return KernelBackendEngine.applyBlocking(context, KernelBackendEngine.ControlRequest.NodeWrite(path, value.toString())).success
    }

    fun setChargingEnabled(enabled: Boolean): Boolean {
        val writes = if (enabled) listOf(
            "/sys/class/power_supply/battery/batt_slate_mode" to "0",
            "/sys/class/power_supply/battery/battery_input_suspend" to "0",
            "/sys/class/power_supply/battery/device/Charging_Enable" to "1",
            "/sys/class/power_supply/battery/charging_enabled" to "1",
            "/sys/class/qcom-battery/input_suspend" to "0",
            "/sys/devices/platform/charger/bypass_charger" to "0"
        ) else listOf(
            "/sys/class/power_supply/battery/batt_slate_mode" to "1",
            "/sys/class/power_supply/battery/battery_input_suspend" to "1",
            "/sys/class/power_supply/battery/device/Charging_Enable" to "0",
            "/sys/class/power_supply/battery/charging_enabled" to "0",
            "/sys/class/qcom-battery/input_suspend" to "1",
            "/sys/devices/platform/charger/bypass_charger" to "1"
        )
        val available = writes.filter { KernelRootRuntime.nodeExistsBlocking(it.first) }
        if (available.isEmpty()) return false
        val transaction = KernelControlEngine.setKernelNodesTransactionalBlocking(available)
        return transaction.success
    }

    fun setChargingLimit(percentage: Int): Boolean {
        return KernelControlEngine.setKernelNodeBlocking(
            "$BATTERY_PATH/charge_control_limit", percentage.coerceIn(20, 100).toString(), true
        ).success
    }

    fun setFastCharge(enabled: Boolean): Boolean {
        val value = if (enabled) "1" else "0"
        val paths = listOf(
            "/sys/kernel/fast_charge/force_fast_charge",
            "/sys/class/power_supply/battery/fastchg",
            "/sys/class/power_supply/usb/fastchg"
        )
        for (path in paths) {
            if (KernelRootRuntime.nodeExistsBlocking(path)) {
                if (KernelControlEngine.setKernelNodeBlocking(path, value, true).success) return true
            }
        }
        return false
    }
    
    fun setBypassCharging(enabled: Boolean): Boolean {
        val value = if (enabled) "1" else "0"
        return KernelControlEngine.setKernelNodeBlocking(BYPASS_CHARGING_PATH, value, true).success
    }
    
    fun isBypassSupported(): Boolean {
        return KernelRootRuntime.nodeExistsBlocking(BYPASS_CHARGING_PATH)
    }

    fun isFastChargeSupported(): Boolean {
        val paths = listOf(
            "/sys/kernel/fast_charge/force_fast_charge",
            "/sys/class/power_supply/battery/fastchg",
            "/sys/class/power_supply/usb/fastchg"
        )
        return paths.any { KernelRootRuntime.nodeExistsBlocking(it) }
    }

    fun getFastChargeStatus(): Boolean {
        val paths = listOf(
            "/sys/kernel/fast_charge/force_fast_charge",
            "/sys/class/power_supply/battery/fastchg",
            "/sys/class/power_supply/usb/fastchg"
        )
        for (path in paths) {
            val value = readSysFile(path)
            if (value == "1") return true
        }
        return false
    }
    
    fun getBypassStatus(): Boolean {
        return readSysFile(BYPASS_CHARGING_PATH) == "1"
    }

    fun setBatterySaver(enabled: Boolean): Boolean {
        val value = if (enabled) "1" else "0"
        return KernelRootRuntime.executeBlocking("settings put global low_power $value").isSuccess
    }

    fun isBatterySaverEnabled(): Boolean {
        val result = KernelRootRuntime.executeBlocking("settings get global low_power")
        return result.isSuccess && result.outputString.trim() == "1"
    }

    // --- THERMAL UTILS ---
    fun hasThermalSconfig(): Boolean = File(THERMAL_SCONFIG).exists()

    fun getThermalSconfig(): String = readSysFile(THERMAL_SCONFIG) ?: "0"

    fun setThermalSconfig(value: String): Boolean {
        return KernelControlEngine.setKernelNodeBlocking(THERMAL_SCONFIG, value.trim(), true).success
    }

    // --- SMART CHARGE CHECK ---
    fun isSmartChargeSupported(): Boolean {
        val testPaths = listOf(
            "/sys/class/power_supply/battery/charging_enabled",
            "/sys/class/power_supply/battery/input_suspend",
            "/sys/class/qcom-battery/input_suspend",
            "/sys/class/power_supply/battery/batt_slate_mode"
        )
        return testPaths.any { path -> KernelRootRuntime.nodeExistsBlocking(path) }
    }

    // --- RAW UEVENT + CHARGE LIMIT INFO (dipakai tab Charge Control & Raw Data) ---
    // Semua fungsi ini memanggil shell root sebagai fallback: JANGAN panggil dari main thread.
    fun readBatteryUevent(): String = readSysFile("$BATTERY_PATH/uevent").orEmpty()

    fun readUsbUevent(): String = readSysFile("$USB_PATH/uevent").orEmpty()

    fun parseUevent(raw: String): Map<String, String> = raw.lineSequence()
        .mapNotNull { line ->
            val idx = line.indexOf('=')
            if (idx <= 0) null else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
        }.toMap()

    /** Batas arus charge yang benar-benar dipakai kernel (mA), dibaca dari CONSTANT_CHARGE_CURRENT (uA). */
    fun getActualUpperCurrentMa(uevent: Map<String, String>): Int {
        val fromUevent = uevent["POWER_SUPPLY_CONSTANT_CHARGE_CURRENT"] ?: uevent["CONSTANT_CHARGE_CURRENT"]
        val raw = fromUevent?.toLongOrNull()
            ?: readSysFile("$BATTERY_PATH/constant_charge_current")?.toLongOrNull()
            ?: readSysFile("$BATTERY_PATH/constant_charge_current_max")?.toLongOrNull()
            ?: return 0
        return (raw / 1000L).toInt().coerceAtLeast(0)
    }

    fun getUsbCurrentMaxMa(): Int = (readSysFile("$USB_PATH/current_max")?.toLongOrNull() ?: 0L).div(1000).toInt()

    /**
     * charge_control_limit di sebagian kernel (mis. Xiaomi: LIMIT=0, LIMIT_MAX=16) adalah INDEKS level
     * thermal, bukan persen. Menulis 20..100 ke node itu salah. Chip persen hanya valid jika MAX >= 100.
     */
    fun getChargeControlLimitMax(): Int =
        readSysFile("$BATTERY_PATH/charge_control_limit_max")?.toIntOrNull() ?: 0

    fun isPercentChargeLimitSupported(): Boolean {
        if (!KernelRootRuntime.nodeExistsBlocking("$BATTERY_PATH/charge_control_limit")) return false
        val max = getChargeControlLimitMax()
        return max == 0 || max >= 100
    }

    // --- HELPERS ---
    private fun normalizeCapacity(valueStr: String?): Double {
        val value = valueStr?.trim()?.toDoubleOrNull() ?: 0.0
        return if (value > 100_000) value / 1000.0 else value
    }

    fun getBatteryCapacity(): Int {
        val capacity = readSysFile("$BATTERY_PATH/charge_full")
        val designCapacity = readSysFile("$BATTERY_PATH/charge_full_design")
        
        return if (capacity != null && designCapacity != null) {
            val cap = normalizeCapacity(capacity)
            val design = normalizeCapacity(designCapacity)
            if (design > 0) ((cap / design) * 100).toInt() else 0
        } else 100
    }

    private fun readSysFile(path: String): String? {
        return try {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                file.readText().trim()
            } else {
                val result = KernelRootRuntime.executeBlocking("cat '${path.replace("'", "'\"'\"'")}' 2>/dev/null")
                if (result.isSuccess && result.outputString.isNotEmpty()) result.outputString else null
            }
        } catch (e: Exception) {
            android.util.Log.w("ZKM", "Battery sysfs read failed; returning unavailable", e)
            null
        }
    }
}
