/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.library.device.monitor

import kotlin.math.abs
import kotlin.math.max

/**
 * "Universal Devices (BETA)" FPS engine - pure Kotlin, no Android types, unit-testable.
 *
 * Source: `dumpsys SurfaceFlinger --timestats -dump`. It is the only compositor statistic that
 * every Android 10+ build exposes to the shell uid (Shizuku / wireless debugging) without root,
 * without SELinux tricks and without the per-version layer-name formats that `--latency` needs.
 *
 * What was wrong before (and what this engine does differently):
 *  1. Timing came from the app side (process spawn + binder + dump), so one Shizuku tick could be
 *     off by 50-300 ms -> +-10-30 % FPS error on a 1 s window. Now the shell script prints
 *     `/proc/uptime` right before and right after the dump and the engine uses that device-side
 *     midpoint as the sample time.
 *  2. Only the previous sample was used. Now a short history is kept and the baseline is the
 *     newest sample that is >= ~1 s old, so fast refresh intervals still measure a proper window
 *     and slow ones (>1 s) still work.
 *  3. A counter that went backwards (SF restart / `-clear` by another tool / layer evicted) used to
 *     poison the next window. Now it rebases the history.
 *  4. Several overlays calling in parallel used to eat each other's baseline. The engine is
 *     synchronized and FpsReader shares one result between callers.
 */
internal object TimeStatsScript {
    const val MARK_BEGIN = "@@ZKM_T0"
    const val MARK_END = "@@ZKM_T1"

    /**
     * One shell process per tick. `grep` trims the dump (histograms etc.) down to the two keys we
     * need, which matters a lot over Shizuku where every byte crosses a binder pipe.
     */
    fun dumpScript(enable: Boolean): String = buildString {
        if (enable) append("dumpsys SurfaceFlinger --timestats -enable >/dev/null 2>&1\n")
        append("read u _ < /proc/uptime; echo \"").append(MARK_BEGIN).append(" \$u\"\n")
        append("dumpsys SurfaceFlinger --timestats -dump 2>/dev/null | ")
            .append("grep -E '^[[:space:]]*(layerName|totalFrames)[[:space:]]*='\n")
        append("read u _ < /proc/uptime; echo \"").append(MARK_END).append(" \$u\"\n")
    }
}

internal class ParsedTimeStats(
    /** layer name -> totalFrames. Key "" is the global counter that precedes the first layer. */
    val frames: Map<String, Long>,
    val beginSec: Double?,
    val endSec: Double?
) {
    /** Device-side time (seconds, boottime) at which SurfaceFlinger most likely sampled. */
    val midpointSec: Double?
        get() {
            val a = beginSec ?: return null
            val b = endSec ?: return null
            return if (b >= a) (a + b) / 2.0 else null
        }
}

internal fun parseTimeStatsDump(lines: List<String>): ParsedTimeStats {
    var begin: Double? = null
    var end: Double? = null
    val m = LinkedHashMap<String, Long>()
    var cur = ""
    for (raw in lines) {
        val l = raw.trim()
        when {
            l.startsWith(TimeStatsScript.MARK_BEGIN) ->
                begin = l.removePrefix(TimeStatsScript.MARK_BEGIN).trim().toDoubleOrNull()
            l.startsWith(TimeStatsScript.MARK_END) ->
                end = l.removePrefix(TimeStatsScript.MARK_END).trim().toDoubleOrNull()
            l.startsWith("layerName") -> cur = l.substringAfter('=', "").trim()
            l.startsWith("totalFrames") -> {
                val v = l.substringAfter('=', "").trim().toLongOrNull() ?: continue
                if (!m.containsKey(cur)) m[cur] = v
            }
        }
    }
    return ParsedTimeStats(m, begin, end)
}

internal class TimeStatsEngine(private val windowSec: Double = 1.0) {

    class Result(
        val fps: Float,
        /** Layer the number came from (null when nothing could be resolved yet). */
        val layer: String?,
        /** true = a layer that belongs to the foreground package was found. */
        val matchedApp: Boolean,
        /** false = the dump carried no data at all (timestats off / SF restarted). */
        val valid: Boolean
    )

    private class Snap(val t: Double, val frames: Map<String, Long>)

    private val history = ArrayDeque<Snap>()
    private var ema = 0f
    private var zeroTicks = 0
    private var lastPkg = ""
    private var lastLayer: String? = null
    private var lastMatched = false

    private val ignoredLayerTokens = listOf(
        "StatusBar", "NavigationBar", "ScreenDecor", "Wallpaper", "InputMethod", "Dim Layer",
        "Background for", "Bounds for", "Splash Screen", "Letterbox", "NotificationShade",
        "com.android.systemui", "com.android.launcher", "Gesture Monitor", "VolumePanel", "Toast"
    )

    @Synchronized
    fun reset() {
        history.clear()
        ema = 0f
        zeroTicks = 0
        lastLayer = null
        lastMatched = false
    }

    @Synchronized
    fun feed(atSec: Double, frames: Map<String, Long>, pkg: String, ownPkg: String = ""): Result {
        if (frames.isEmpty()) {
            reset()
            return Result(0f, null, matchedApp = false, valid = false)
        }
        if (pkg != lastPkg) {
            lastPkg = pkg
            ema = 0f
            zeroTicks = 0
            lastLayer = null
        }

        // Counters going backwards = SF restart / stats cleared / layer evicted. If that hits most
        // layers the old baseline is meaningless.
        history.lastOrNull()?.let { prev ->
            var common = 0
            var backwards = 0
            for ((k, v) in frames) {
                val p = prev.frames[k] ?: continue
                common++
                if (v < p) backwards++
            }
            if (common > 0 && backwards * 2 >= common) history.clear()
        }
        // Samples must be strictly increasing in time (two callers / clock hiccup).
        if (history.isNotEmpty() && atSec <= history.last().t) {
            return Result(ema, lastLayer, lastMatched, valid = true)
        }

        history.addLast(Snap(atSec, frames))
        // Keep ~4 s, but never fewer than two samples (previous + current) so a slow polling
        // interval still has a baseline.
        while (history.size > 2 && atSec - history.first().t > 4.0) history.removeFirst()
        if (history.size >= 2 && atSec - history.first().t > 10.0) {
            while (history.size > 1) history.removeFirst()
        }
        if (history.size < 2) return Result(0f, null, matchedApp = false, valid = true)

        // Baseline: newest older sample that already spans the window; otherwise the oldest one.
        val cur = history.last()
        var base: Snap? = null
        for (i in history.size - 2 downTo 0) {
            val s = history[i]
            if (atSec - s.t >= windowSec * 0.9) { base = s; break }
        }
        if (base == null) base = history.first()
        val dt = atSec - base.t
        if (dt < 0.25) return Result(ema, lastLayer, lastMatched, valid = true)

        fun delta(k: String): Long {
            val a = cur.frames[k] ?: return 0L
            val b = base.frames[k] ?: return 0L // layer appeared inside the window: no baseline yet
            return if (a >= b) a - b else 0L
        }

        val appLayers = if (pkg.isNotBlank()) {
            cur.frames.keys.filter { it.isNotEmpty() && it.contains(pkg) }
        } else emptyList()
        val matched = appLayers.isNotEmpty()

        var chosen: String? = null
        var frameDelta = 0L
        if (matched) {
            for (k in appLayers) {
                val d = delta(k)
                if (chosen == null || d > frameDelta) { chosen = k; frameDelta = d }
            }
        } else {
            // Foreground package unknown or its layer name did not carry the package:
            // take the busiest non-system layer; the global counter only when we know nothing.
            for (k in cur.frames.keys) {
                if (k.isEmpty()) continue
                if (ownPkg.isNotEmpty() && k.contains(ownPkg)) continue
                if (ignoredLayerTokens.any { k.contains(it) }) continue
                val d = delta(k)
                if (d > frameDelta) { chosen = k; frameDelta = d }
            }
            if (frameDelta == 0L && pkg.isBlank()) {
                val g = delta("")
                if (g > 0L) { chosen = "(global)"; frameDelta = g }
            }
        }

        val raw = (frameDelta / dt).toFloat()
        if (!raw.isFinite() || raw > 480f) return Result(ema, lastLayer, lastMatched, valid = true)

        if (raw <= 0f) {
            zeroTicks++
            ema = if (zeroTicks >= 2) 0f else ema * 0.5f
        } else {
            zeroTicks = 0
            // The window already averages over ~1 s; only damp tiny jitter, snap on real changes.
            ema = if (ema > 0f && abs(raw - ema) < ema * 0.15f) ema * 0.5f + raw * 0.5f else raw
        }
        lastLayer = chosen
        lastMatched = matched
        return Result(max(ema, 0f), chosen, matched, valid = true)
    }
}
