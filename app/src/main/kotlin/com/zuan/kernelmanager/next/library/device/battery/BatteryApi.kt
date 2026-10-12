/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.library.device.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import kotlin.math.abs

/**
 * Everything the public Android API can tell us about the battery - no root, no Shizuku.
 *
 * Used as the base layer in BASIC mode and as the gap-filler in ADB / ROOT mode when a sysfs
 * node is missing or unreadable on a given kernel.
 */
data class ApiBattery(
    val levelPercent: Int,
    val statusText: String,
    val charging: Boolean,
    val plugged: Boolean,
    val voltageV: Float,
    val tempC: Float,
    /** Signed mA: + charging, - discharging. 0 when the device does not report current. */
    val currentMa: Int,
    val healthText: String,
    val technology: String,
    /** null when the platform does not report it (needs Android 14+ and a HAL that fills it in). */
    val cycleCount: Int?,
    /** Remaining charge in mAh from BATTERY_PROPERTY_CHARGE_COUNTER, or null. */
    val chargeCounterMah: Float?,
    /** Design capacity from the internal PowerProfile, or null. */
    val designCapacityMah: Float?
) {
    /** Estimated full-charge capacity = remaining charge / level. Only meaningful at 15%+ . */
    val estimatedFullMah: Float?
        get() = chargeCounterMah?.takeIf { levelPercent >= 15 && it > 0f }
            ?.let { it * 100f / levelPercent }
}

object BatteryApi {

    @Volatile private var designCache: Float? = null
    @Volatile private var designResolved = false

    fun read(context: Context): ApiBattery {
        val app = context.applicationContext
        val intent: Intent? = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val bm = app.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val rawLevel = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = (intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
        val level = if (rawLevel >= 0) (rawLevel * 100 / scale).coerceIn(0, 100)
        else bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: 0
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val plugged = (intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0

        val voltRaw = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        // Some ROMs report volts*1000 (mV), a few report µV.
        val voltageV = when {
            voltRaw > 100_000 -> voltRaw / 1_000_000f
            voltRaw > 100 -> voltRaw / 1000f
            else -> voltRaw.toFloat()
        }
        val tempC = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f

        val currentRaw = runCatching { bm?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }.getOrNull()
        val currentMag = if (currentRaw == null || currentRaw == Long.MIN_VALUE || currentRaw == 0L) 0
        else {
            // Spec says µA but many OEMs return mA.
            val m = abs(currentRaw).toFloat()
            (if (m >= 20_000f) m / 1000f else m).toInt()
        }
        val signedCurrent = when {
            currentMag == 0 -> 0
            charging || (currentRaw != null && currentRaw > 0 && plugged) -> currentMag
            else -> -currentMag
        }

        val counterUah = runCatching { bm?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) }.getOrNull()
        val counterMah = counterUah?.takeIf { it > 0L && it != Long.MIN_VALUE }?.let { it / 1000f }

        val cycles = if (Build.VERSION.SDK_INT >= 34) {
            intent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)?.takeIf { it >= 0 }
        } else null

        return ApiBattery(
            levelPercent = level,
            statusText = statusText(status),
            charging = charging,
            plugged = plugged,
            voltageV = voltageV,
            tempC = tempC,
            currentMa = signedCurrent,
            healthText = healthText(intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1),
            technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY).orEmpty(),
            cycleCount = cycles,
            chargeCounterMah = counterMah,
            designCapacityMah = designCapacity(app)
        )
    }

    /** `com.android.internal.os.PowerProfile#getBatteryCapacity()` - reflection, cached, best effort. */
    private fun designCapacity(context: Context): Float? {
        if (designResolved) return designCache
        val value = runCatching {
            val cls = Class.forName("com.android.internal.os.PowerProfile")
            val inst = cls.getConstructor(Context::class.java).newInstance(context)
            (cls.getMethod("getBatteryCapacity").invoke(inst) as Number).toFloat()
        }.getOrNull()?.takeIf { it in 500f..30_000f }
        designCache = value
        designResolved = true
        return value
    }

    fun statusText(status: Int): String = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
        BatteryManager.BATTERY_STATUS_FULL -> "Full"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
        else -> "Unknown"
    }

    fun healthText(health: Int): String = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified Failure"
        BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
        else -> "Unknown"
    }
}
