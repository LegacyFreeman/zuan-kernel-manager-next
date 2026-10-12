/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.library.device.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.io.File
import kotlin.math.abs

/**
 * Pembacaan baterai ringan TANPA root (BatteryManager + sysfs yang bisa dibaca app).
 * Dipakai notifikasi realtime, history, dan layar Home (Charge Stats / Battery Usage).
 *
 * Tanda daya: + saat mengisi, - saat baterai dipakai (sama seperti Mini/System Monitor).
 */
data class BatteryReading(
    val level: Int,
    val tempC: Float,
    val voltageV: Float,
    /** mA bertanda: + mengisi, - dipakai. */
    val currentMa: Float,
    /** Watt bertanda: + mengisi, - dipakai. */
    val watt: Float,
    val charging: Boolean,
    val status: Int
)

object BatterySampler {

    private const val CURRENT_NODE = "/sys/class/power_supply/battery/current_now"
    private const val CACHE_DURATION_MS = 500L

    @Volatile private var cachedReading: BatteryReading? = null
    @Volatile private var cachedAtMs = 0L

    fun read(context: Context): BatteryReading {
        // Multi-consumer cache 500ms (Adopsi pola CpuLoadUtils dari VTools)
        // Mencegah multiple widget membaca IPC BatteryManager dan sysfs storage berulang kali.
        val now = android.os.SystemClock.elapsedRealtime()
        val cached = cachedReading
        if (cached != null && now - cachedAtMs < CACHE_DURATION_MS) {
            return cached
        }

        val app = context.applicationContext
        val intent: Intent? = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val rawLevel = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val scale = (intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
        val level = (rawLevel * 100 / scale).coerceIn(0, 100)
        val tempC = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val voltageV = (intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000f
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING

        val magnitudeMa = readCurrentMagnitudeMa(app)
        val signedMa = if (charging) magnitudeMa else -magnitudeMa
        val watt = voltageV * signedMa / 1000f

        val result = BatteryReading(level, tempC, voltageV, signedMa, watt, charging, status)
        cachedReading = result
        cachedAtMs = now
        return result
    }

    /** Arus (mA, tanpa tanda). Kernel bisa melapor µA atau mA -> samakan. */
    private fun readCurrentMagnitudeMa(context: Context): Float {
        var raw = 0L
        runCatching {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            raw = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        }
        if (raw == 0L || raw == Long.MIN_VALUE) {
            raw = runCatching { File(CURRENT_NODE).readText().trim().toLong() }.getOrDefault(0L)
        }
        val mag = abs(raw).toFloat()
        return if (mag >= 20_000f) mag / 1000f else mag
    }

    fun statusWord(reading: BatteryReading): String = when (reading.status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
        BatteryManager.BATTERY_STATUS_FULL -> "Full"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
        else -> "Discharging"
    }
}
