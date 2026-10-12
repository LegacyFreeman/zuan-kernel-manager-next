/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.library.device.monitor

/**
 * Pure helpers for `dumpsys SurfaceFlinger --latency <layer>` (no Android types, unit-testable).
 *
 * Output format: line 0 = refresh period (ns); then up to 127 lines of
 * `desiredPresent  actualPresent  frameReady` timestamps (ns). Unused slots are 0 or Long.MAX_VALUE.
 */
internal object SfLatency {
    private const val LONG_MAX = Long.MAX_VALUE

    /**
     * Frame timestamps, ascending. Column 1 (actual present) is the reference; when a vendor build
     * leaves that column empty, column 0 (desired present) is used instead - never a per-row mix,
     * because the columns can be in different clock domains.
     */
    fun timestamps(lines: List<String>): List<Long> {
        val actual = ArrayList<Long>(128)
        val desired = ArrayList<Long>(128)
        for (raw in lines) {
            val parts = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (parts.size != 3) continue // also skips the 1-column refresh-period header
            val d = parts[0].toLongOrNull()
            val a = parts[1].toLongOrNull()
            if (a != null && a > 0L && a != LONG_MAX) actual.add(a)
            if (d != null && d > 0L && d != LONG_MAX) desired.add(d)
        }
        val chosen = if (actual.size >= 2) actual else desired
        chosen.sort()
        return chosen
    }
}

/**
 * Turns successive `--latency` snapshots of one layer into an FPS number.
 *
 * - A tick without any new frame holds the previous value for [holdMs] (so a 24 fps video or a
 *   slow poll does not flicker to 0) and then drops to 0 ("static screen", a real answer).
 * - First sample / more frames than the 127-slot buffer since the last tick: the newest one second
 *   of the buffer is used.
 */
internal class LatencyTracker(private val holdMs: Long = 1200L) {
    private var prevLast = 0L
    private var lastFps = 0f
    private var lastChangeAtMs = 0L

    fun reset() {
        prevLast = 0L
        lastFps = 0f
        lastChangeAtMs = 0L
    }

    fun feed(ts: List<Long>, nowMs: Long): Float {
        if (ts.size < 2) return 0f
        val last = ts.last()
        val prev = prevLast
        prevLast = last

        if (prev != 0L && last <= prev) {
            // no new frame since the previous tick
            if (lastFps > 0f && nowMs - lastChangeAtMs < holdMs) return lastFps
            lastFps = 0f
            return 0f
        }

        val fps: Double = if (prev != 0L && ts.first() <= prev) {
            val newFrames = ts.count { it > prev }
            val span = (last - prev) / 1e9
            if (span > 0.0) newFrames / span else 0.0
        } else {
            val windowStart = last - 1_000_000_000L
            val w = ts.filter { it >= windowStart }
            if (w.size < 2) 0.0 else (w.size - 1) / ((w.last() - w.first()) / 1e9)
        }
        val v = fps.toFloat().takeIf { it in 0f..480f } ?: 0f
        lastFps = v
        lastChangeAtMs = nowMs
        return v
    }
}
