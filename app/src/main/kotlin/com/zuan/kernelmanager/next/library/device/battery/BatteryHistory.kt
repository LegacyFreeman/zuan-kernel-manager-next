/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.library.device.battery

import android.content.Context
import android.os.PowerManager
import com.zuan.kernelmanager.next.library.device.usage.UsageStatsHelper
import java.io.File

/** Satu titik riwayat baterai. [watt] bertanda (+ mengisi, - dipakai). */
data class BatterySample(
    val t: Long,
    val level: Int,
    val watt: Float,
    val tempC: Float,
    val charging: Boolean,
    val screenOn: Boolean,
    val pkg: String
)

/**
 * Riwayat baterai ringan (CSV append-only di filesDir, disimpan 72 jam).
 * Diisi oleh service realtime (Live Stats / Battery Monitor) dan layar Charge Stats /
 * Battery Usage saat terbuka. Format baris: t,level,wattx100,suhux10,flags,pkg
 * (flags: bit0 = mengisi, bit1 = layar menyala).
 *
 * record() melakukan I/O -> panggil dari thread background.
 */
object BatteryHistory {
    private const val FILE_NAME = "battery_history.csv"
    private const val RETENTION_MS = 72L * 3_600_000L
    private const val MIN_INTERVAL_MS = 15_000L
    private const val FLUSH_MS = 60_000L

    private val lock = Any()
    private val samples = ArrayList<BatterySample>()
    private val pending = StringBuilder()
    private var loaded = false
    private var needsRewrite = false
    private var lastRecord = 0L
    private var lastFlush = 0L

    fun record(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        synchronized(lock) {
            ensureLoaded(app)
            if (!force && now - lastRecord < MIN_INTERVAL_MS) return
            lastRecord = now
        }

        val reading = BatterySampler.read(app)
        val screenOn = (app.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        val pkg = if (screenOn) UsageStatsHelper.foregroundPackage(app) else ""
        val sample = BatterySample(now, reading.level, reading.watt, reading.tempC, reading.charging, screenOn, pkg)

        synchronized(lock) {
            samples.add(sample)
            pending.append(encode(sample)).append('\n')
            trimLocked(now)
            if (now - lastFlush >= FLUSH_MS) flushLocked(app, now)
        }
    }

    /** Salinan riwayat (terlama -> terbaru). */
    fun snapshot(context: Context): List<BatterySample> = synchronized(lock) {
        ensureLoaded(context.applicationContext)
        ArrayList(samples)
    }

    fun flush(context: Context) = synchronized(lock) {
        flushLocked(context.applicationContext, System.currentTimeMillis())
    }

    fun clear(context: Context) = synchronized(lock) {
        samples.clear()
        pending.setLength(0)
        needsRewrite = false
        runCatching { File(context.applicationContext.filesDir, FILE_NAME).delete() }
        Unit
    }

    // ---- internal ------------------------------------------------------------------------

    private fun trimLocked(now: Long) {
        val cutoff = now - RETENTION_MS
        var drop = 0
        while (drop < samples.size && samples[drop].t < cutoff) drop++
        if (drop > 0) {
            samples.subList(0, drop).clear()
            needsRewrite = true
        }
    }

    private fun flushLocked(context: Context, now: Long) {
        lastFlush = now
        val file = File(context.filesDir, FILE_NAME)
        runCatching {
            if (needsRewrite) {
                file.writeText(buildString { samples.forEach { append(encode(it)).append('\n') } })
                needsRewrite = false
            } else if (pending.isNotEmpty()) {
                file.appendText(pending.toString())
            }
        }
        pending.setLength(0)
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return
        runCatching {
            file.forEachLine { line -> decode(line)?.let { samples.add(it) } }
        }
        val before = samples.size
        trimLocked(System.currentTimeMillis())
        if (samples.size != before) needsRewrite = true
    }

    private fun encode(s: BatterySample): String {
        val flags = (if (s.charging) 1 else 0) or (if (s.screenOn) 2 else 0)
        return "${s.t},${s.level},${(s.watt * 100).toInt()},${(s.tempC * 10).toInt()},$flags,${s.pkg}"
    }

    private fun decode(line: String): BatterySample? = runCatching {
        val p = line.split(',', limit = 6)
        if (p.size < 5) return null
        val flags = p[4].toInt()
        BatterySample(
            t = p[0].toLong(),
            level = p[1].toInt(),
            watt = p[2].toInt() / 100f,
            tempC = p[3].toInt() / 10f,
            charging = flags and 1 != 0,
            screenOn = flags and 2 != 0,
            pkg = p.getOrElse(5) { "" }.trim()
        )
    }.getOrNull()
}
