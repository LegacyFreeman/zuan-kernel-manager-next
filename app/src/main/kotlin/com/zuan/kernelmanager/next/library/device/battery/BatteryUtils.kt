/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.library.device.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import android.util.Log
import com.zuan.kernelmanager.next.library.shell.PrivilegedAccess
import com.zuan.kernelmanager.next.library.shell.suBlocking
import com.zuan.kernelmanager.next.core.kernel.KernelControlEngine
import com.zuan.kernelmanager.next.core.kernel.KernelNodePolicy
import com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime
import kotlin.math.roundToInt

object BatteryUtils {

    const val FAST_CHARGING = "/sys/kernel/fast_charge/force_fast_charge"
    const val BYPASS_CHARGING = "/sys/class/power_supply/battery/input_suspend"
    const val BATTERY_DESIGN_CAPACITY = "/sys/class/power_supply/battery/charge_full_design"
    const val BATTERY_MAXIMUM_CAPACITY = "/sys/class/power_supply/battery/charge_full"
    const val BATTERY_TECHNOLOGY = "/sys/class/power_supply/battery/technology"
    
    const val BATTERY_CURRENT_NOW = "/sys/class/power_supply/battery/current_now"
    const val BATTERY_CURRENT_AVG = "/sys/class/power_supply/battery/current_avg" 
    const val BMS_CURRENT_NOW = "/sys/class/power_supply/bms/current_now" 
    
    const val BATTERY_STATUS = "/sys/class/power_supply/battery/status"
    const val BATTERY_CYCLE_COUNT = "/sys/class/power_supply/battery/cycle_count"

    const val THERMAL_SCONFIG = "/sys/class/thermal/thermal_message/sconfig"

    const val TAG = "BatteryUtils"

    // --- FUNGSI UTAMA UNTUK FIX BUG UNIT CAPACITY (mAh vs uAh) ---
    private fun normalizeCapacity(valueStr: String?): Double {
        val value = valueStr?.trim()?.toDoubleOrNull() ?: 0.0
        // Jika nilai > 100.000, kemungkinan besar kernel pakai satuan uAh (Micro Ampere Hour)
        // Kita konversi ke mAh (Milli Ampere Hour) dengan bagi 1000
        return if (value > 100_000) {
            value / 1000.0
        } else {
            value
        }
    }

    fun getKernelVersion(): String {
        return try {
            val result = suBlocking("uname -r")
            if (result.isSuccess) {
                result.out.joinToString("").trim()
            } else {
                "Unknown"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private val CHARGING_DISABLE_WRITES = listOf(
        "/sys/class/power_supply/battery/batt_slate_mode" to "1",
        "/sys/class/power_supply/battery/battery_input_suspend" to "1",
        "/sys/class/power_supply/battery/bd_trickle_cnt" to "1",
        "/sys/class/power_supply/battery/device/Charging_Enable" to "0",
        "/sys/class/power_supply/battery/charging_enabled" to "0",
        "/sys/class/power_supply/battery/op_disable_charge" to "1",
        "/sys/class/power_supply/battery/store_mode" to "1",
        "/sys/class/power_supply/battery/test_mode" to "1",
        "/sys/class/power_supply/battery/battery_ext/smart_charging_interruption" to "1",
        "/sys/class/power_supply/battery/siop_level" to "0",
        "/sys/class/power_supply/battery/battery_charging_enabled" to "0",
        "/sys/class/power_supply/battery/mmi_charging_enable" to "0",
        "/sys/class/power_supply/battery/stop_charging_enable" to "1",
        "/sys/class/hw_power/charger/charge_data/enable_charger" to "0",
        "/sys/class/qcom-battery/input_suspend" to "1",
        "/sys/devices/platform/charger/tran_aichg_disable_charger" to "1",
        "/sys/devices/platform/charger/bypass_charger" to "1",
        "/sys/devices/platform/huawei_charger/enable_charger" to "0",
        "/sys/devices/platform/lge-unified-nodes/charging_completed" to "1",
        "/sys/devices/platform/lge-unified-nodes/charging_enable" to "0",
        "/sys/devices/platform/mt-battery/disable_charger" to "1",
        "/sys/devices/platform/soc/soc:google,charger/charge_disable" to "1",
        "/sys/kernel/debug/google_charger/chg_suspend" to "1",
        "/sys/kernel/debug/google_charger/input_suspend" to "1",
        "/sys/kernel/nubia_charge/charger_bypass" to "on",
        "/proc/mtk_battery_cmd/current_cmd" to "0 1"
    )

    private val CHARGING_ENABLE_WRITES = listOf(
        "/sys/class/power_supply/battery/batt_slate_mode" to "0",
        "/sys/class/power_supply/battery/battery_input_suspend" to "0",
        "/sys/class/power_supply/battery/bd_trickle_cnt" to "0",
        "/sys/class/power_supply/battery/device/Charging_Enable" to "1",
        "/sys/class/power_supply/battery/charging_enabled" to "1",
        "/sys/class/power_supply/battery/op_disable_charge" to "0",
        "/sys/class/power_supply/battery/store_mode" to "0",
        "/sys/class/power_supply/battery/test_mode" to "2",
        "/sys/class/power_supply/battery/battery_ext/smart_charging_interruption" to "0",
        "/sys/class/power_supply/battery/siop_level" to "100",
        "/sys/class/power_supply/battery/battery_charging_enabled" to "1",
        "/sys/class/power_supply/battery/mmi_charging_enable" to "1",
        "/sys/class/power_supply/battery/stop_charging_enable" to "0",
        "/sys/class/hw_power/charger/charge_data/enable_charger" to "1",
        "/sys/class/qcom-battery/input_suspend" to "0",
        "/sys/devices/platform/charger/tran_aichg_disable_charger" to "0",
        "/sys/devices/platform/charger/bypass_charger" to "0",
        "/sys/devices/platform/huawei_charger/enable_charger" to "1",
        "/sys/devices/platform/lge-unified-nodes/charging_completed" to "0",
        "/sys/devices/platform/lge-unified-nodes/charging_enable" to "1",
        "/sys/devices/platform/mt-battery/disable_charger" to "0",
        "/sys/devices/platform/soc/soc:google,charger/charge_disable" to "0",
        "/sys/kernel/debug/google_charger/chg_suspend" to "0",
        "/sys/kernel/debug/google_charger/input_suspend" to "0",
        "/sys/kernel/nubia_charge/charger_bypass" to "off",
        "/proc/mtk_battery_cmd/current_cmd" to "0 0"
    )

    private fun applyChargingWrites(writes: List<Pair<String, String>>): Boolean {
        val eligible = writes.filter { (path, _) ->
            KernelRootRuntime.nodeExistsBlocking(path) && KernelNodePolicy.isWriteAllowed(path)
        }
        if (eligible.isEmpty()) return false
        var anySuccess = false
        eligible.forEach { (path, value) ->
            if (KernelControlEngine.setKernelNodeBlocking(path, value, verify = true).success) {
                anySuccess = true
            }
        }
        return anySuccess
    }

    fun setChargingEnabled(enable: Boolean) {
        Thread {
            applyChargingWrites(if (enable) CHARGING_ENABLE_WRITES else CHARGING_DISABLE_WRITES)
        }.start()
    }

    /** First line of a sysfs node: app uid first (many are world-readable), shell only if the mode has one. */
    private fun readSysFirst(path: String): String? {
        runCatching {
            java.io.File(path).takeIf { it.isFile && it.canRead() }?.readText()?.trim()
        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        if (!PrivilegedAccess.shellWanted()) return null
        return suBlocking("cat $path 2>/dev/null").out.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun Context.getBatteryIntent(): Intent? = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    fun getBatteryTechnology(context: Context): String = runCatching {
        readSysFirst(BATTERY_TECHNOLOGY)
            ?: context.getBatteryIntent()?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.ifBlank { null }
            ?: "N/A"
    }.getOrElse {
        Log.e(TAG, "Error reading battery technology", it)
        context.getBatteryIntent()?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "N/A"
    }

    fun getBatteryHealth(context: Context): String {
        return when (context.getBatteryIntent()?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified Failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "N/A"
        }
    }

    fun getBatteryLevel(context: Context): String {
        val level = context.getBatteryIntent()?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        return if (level != -1) "$level%" else "N/A"
    }

    fun getBatteryDesignCapacity(): String = runCatching {
        val raw = readSysFirst(BATTERY_DESIGN_CAPACITY)
        val mah = if (raw != null) normalizeCapacity(raw) else
            (com.zuan.kernelmanager.next.core.kernel.KernelRuntimeContext.context
                ?.let { BatteryApi.read(it).designCapacityMah }?.toDouble() ?: 0.0)
        if (mah > 0) "${mah.toInt()} mAh" else "N/A"
    }.getOrElse { "N/A" }

    // Dihapus atau disesuaikan jika tidak dipakai di UI baru, tapi ini backup untuk calculatedHealth
    fun getCalculatedHealth(): String {
        val fullPaths = listOf("/sys/class/power_supply/battery/charge_full", "/sys/class/power_supply/battery/charge_counter")
        val designPaths = listOf("/sys/class/power_supply/battery/charge_full_design", "/sys/class/power_supply/battery/batt_full_capacity")

        var fullCap = 0.0
        var designCap = 0.0

        for (path in fullPaths) {
            val res = readSysFirst(path)
            if (res != null) {
                fullCap = normalizeCapacity(res)
                if (fullCap > 0) break
            }
        }

        for (path in designPaths) {
            val res = readSysFirst(path)
            if (res != null) {
                designCap = normalizeCapacity(res)
                if (designCap > 0) break
            }
        }

        if (fullCap <= 0 || designCap <= 0) return "N/A"

        val percentage = (fullCap * 100 / designCap)
        val formattedPercent = String.format("%.1f", percentage)

        return "$formattedPercent% (${fullCap.toInt()}mAh from ${designCap.toInt()}mAh)"
    }

    fun getBatteryMaximumCapacity(context: Context): String = runCatching {
        val api = BatteryApi.read(context)
        var currentCap = normalizeCapacity(readSysFirst(BATTERY_MAXIMUM_CAPACITY))
        // No readable charge_full (BASIC mode): estimate from the fuel-gauge counter / level.
        if (currentCap <= 0 && api.estimatedFullMah != null) currentCap = api.estimatedFullMah!!.toDouble()
        var designCap = normalizeCapacity(readSysFirst(BATTERY_DESIGN_CAPACITY))
        if (designCap <= 0 && api.designCapacityMah != null) designCap = api.designCapacityMah!!.toDouble()

        if (currentCap > 0) {
            if (designCap > 0) {
                val percentage = (currentCap / designCap * 100).toInt()
                "${currentCap.toInt()} mAh ($percentage%)"
            } else {
                "${currentCap.toInt()} mAh"
            }
        } else {
            "N/A"
        }
    }.getOrElse { "N/A" }


    fun getBatteryCurrentNow(): Int {
        val paths = listOf(BATTERY_CURRENT_NOW, BATTERY_CURRENT_AVG, BMS_CURRENT_NOW)
        for (path in paths) {
            val result = runCatching {
                readSysFirst(path)?.trim()?.toIntOrNull()
            }.getOrNull()
            if (result != null && result != 0) {
                 return result / 1000
            }
        }
        return com.zuan.kernelmanager.next.core.kernel.KernelRuntimeContext.context
            ?.let { kotlin.math.abs(BatteryApi.read(it).currentMa) } ?: 0
    }

    fun getChargingStatus(context: Context): String {
        val kernelStatus = runCatching {
            readSysFirst(BATTERY_STATUS)?.trim()
        }.getOrNull()

        if (!kernelStatus.isNullOrEmpty()) return kernelStatus

        val status = context.getBatteryIntent()?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
            else -> "Unknown"
        }
    }
    
    fun getCycleCount(): String = runCatching {
        readSysFirst(BATTERY_CYCLE_COUNT)?.trim() ?: "N/A"
    }.getOrElse { "N/A" }

    fun getUptime(): String {
        val uptimeMillis = SystemClock.elapsedRealtime()
        val seconds = (uptimeMillis / 1000) % 60
        val minutes = (uptimeMillis / (1000 * 60)) % 60
        val hours = (uptimeMillis / (1000 * 60 * 60)) % 24
        val days = (uptimeMillis / (1000 * 60 * 60 * 24))
        return buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0 || days > 0) append("${hours}h ")
            if (minutes > 0 || hours > 0 || days > 0) append("${minutes}m ")
            append("${seconds}s")
        }.trim()
    }

    fun getDeepSleep(): String {
        val deepSleepMillis = SystemClock.elapsedRealtime() - SystemClock.uptimeMillis()
        val seconds = (deepSleepMillis / 1000) % 60
        val minutes = (deepSleepMillis / (1000 * 60)) % 60
        val hours = (deepSleepMillis / (1000 * 60 * 60)) % 24
        val days = deepSleepMillis / (1000 * 60 * 60 * 24)
        val percentage = if (SystemClock.elapsedRealtime() > 0) {
            (deepSleepMillis * 100 / SystemClock.elapsedRealtime()).toInt()
        } else { 0 }
        return buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0 || days > 0) append("${hours}h ")
            if (minutes > 0 || hours > 0 || days > 0) append("${minutes}m ")
            append("${seconds}s")
            append(" ($percentage%)")
        }.trim()
    }
    
    fun getBatteryLevelRaw(context: Context): Int {
        return context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 0
    }

    fun getTempSimple(context: Context): String {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        return "%.1f°C".format(temp / 10.0)
    }

    private fun registerBatteryListener(context: Context, onReceive: (Intent) -> Unit): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                intent?.let(onReceive)
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return receiver
    }

    fun registerBatteryLevelListener(context: Context, callback: (String) -> Unit): BroadcastReceiver =
        registerBatteryListener(context) { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            callback(if (level != -1) "$level%" else "N/A")
        }

    fun registerBatteryTemperatureListener(context: Context, callback: (String) -> Unit): BroadcastReceiver =
        registerBatteryListener(context) { intent ->
            val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            callback(if (temp != -1) "%.1f °C".format(temp / 10.0) else "N/A")
        }

    fun registerBatteryVoltageListener(context: Context, callback: (String) -> Unit): BroadcastReceiver =
        registerBatteryListener(context) { intent ->
            val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            callback(if (voltage != -1) "%.3f V".format(voltage / 1000.0) else "N/A")
        }

    fun registerBatteryCapacityListener(context: Context, callback: (String) -> Unit): BroadcastReceiver =
        registerBatteryListener(context) {
            callback(getBatteryMaximumCapacity(context))
        }

    fun isSmartChargeSupported(): Boolean {
        val testPaths = listOf(
            "/sys/class/power_supply/battery/charging_enabled",
            "/sys/class/power_supply/battery/battery_charging_enabled",
            "/sys/class/power_supply/battery/input_suspend",
            "/sys/class/power_supply/battery/battery_input_suspend",
            "/sys/class/qcom-battery/input_suspend",
            "/sys/kernel/debug/google_charger/input_suspend",
            "/sys/class/power_supply/battery/batt_slate_mode",
            "/sys/class/hw_power/charger/charge_data/enable_charger"
        )
        
        return testPaths.any { path ->
            runCatching {
                suBlocking("test -f $path && test -w $path").isSuccess
            }.getOrDefault(false)
        }
    }
}
