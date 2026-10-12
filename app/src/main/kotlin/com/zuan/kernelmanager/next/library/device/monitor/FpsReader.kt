/*
 * Copyright (c) 2025 ZKM
 *
 * FPS reader architecture inspired by the original VTools measured_fps discovery
 * strategy, rewritten for ZKM's persistent root runtime and extended so that it also
 * works from a plain shell uid (Shizuku / wireless debugging, no root).
 */
package com.zuan.kernelmanager.next.library.device.monitor

import android.content.Context
import android.os.SystemClock
import com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime
import com.zuan.kernelmanager.next.core.kernel.KernelRuntimeContext
import com.zuan.kernelmanager.next.library.shell.PrivilegedAccess
import java.util.Locale
import java.util.regex.Pattern

/** FPS source selection. AUTO is the recommended/default path. */
enum class FpsMode {
    UNIVERSAL_ANDROID,
    UNIVERSAL_GPU,
    UNIVERSAL_BETA,
    AUTO
}

object FpsReader {
    private const val PREF_NAME = "fps_settings"
    private const val KEY_MODE = "reader_mode"

    // New installations use adaptive discovery. Existing explicit selections are preserved.
    var currentMode: FpsMode = FpsMode.AUTO
        private set

    private var fpsFilePath: String? = null
    @Volatile private var lastSourceLabel: String = "Not sampled"
    private var fpsValueColumn = false

    private var lastTime = -1L
    private var lastFrames = -1L

    // The discovery command runs only when the cached source is absent/invalid.
    private var discoveryAttempted = false
    private var discoveryAt = 0L
    private val measuredFpsCandidates = linkedSetOf<String>()

    // Kernel node health: an unreadable node (SELinux denies the shell domain on some ROMs) must
    // not trigger a full rediscovery on every tick.
    private var kernelFailures = 0
    private var kernelDisabledUntil = 0L

    // SurfaceFlinger --latency state
    private var sfLayer: String? = null
    private var sfLayerPkg = ""
    private var sfLayerAt = 0L
    private var sfResolveAt = 0L
    private var sfLayerUniversal = false
    private var sfEmptyTicks = 0
    private var latZeroTicks = 0
    private val latencyTracker = LatencyTracker()

    // gfxinfo (last-resort source for ordinary HWUI apps)
    private var gfxPkg = ""
    private var gfxPrevTotal = -1L
    private var gfxPrevAt = 0L

    // Live diagnostics (shown on the FPS screen) - one short line per stage.
    @Volatile private var diagFg = "-"
    @Volatile private var diagLat = "-"
    @Volatile private var diagTs = "-"
    @Volatile private var diagGfx = "-"

    @Volatile private var seenEpoch = -1

    // init()/setMode() run on the UI thread; the actual reset happens inside getRealFps() so the
    // UI thread never waits for a running shell call.
    @Volatile private var resetRequested = false

    // Overlays (FPS overlay + system monitor + recorder) may all ask for FPS: share one fresh result.
    private var lastResult = 0f
    private var lastResultAt = 0L
    private var lastResultMode: FpsMode? = null
    private const val SHARE_MS = 250L
    private const val UNKNOWN_FG = "(unknown)"

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        currentMode = if (prefs.contains(KEY_MODE)) {
            val idx = prefs.getInt(KEY_MODE, FpsMode.AUTO.ordinal)
            FpsMode.values().getOrElse(idx) { FpsMode.AUTO }
        } else {
            FpsMode.AUTO
        }
        resetRequested = true
    }

    fun setMode(context: Context, mode: FpsMode) {
        currentMode = mode
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_MODE, mode.ordinal).apply()
        resetRequested = true
    }

    private fun resetRuntimeState() {
        fpsFilePath = null
        fpsValueColumn = false
        discoveryAttempted = false
        discoveryAt = 0L
        measuredFpsCandidates.clear()
        lastTime = -1L
        lastFrames = -1L
        kernelFailures = 0
        kernelDisabledUntil = 0L
        sfLayer = null
        sfLayerPkg = ""
        sfLayerAt = 0L
        sfResolveAt = 0L
        sfLayerUniversal = false
        sfEmptyTicks = 0
        latZeroTicks = 0
        latencyTracker.reset()
        gfxPkg = ""
        gfxPrevTotal = -1L
        gfxPrevAt = 0L
        diagFg = "-"; diagLat = "-"; diagTs = "-"; diagGfx = "-"
        tsEngine.reset()
        tsEnabledEpoch = -1
        tsForceEnable = false
        tsIdleTicks = 0
        lastResultAt = 0L
        lastSourceLabel = "Not sampled"
    }

    /** Shizuku connected late / mode switched: forget every negative verdict. */
    private fun syncEpoch() {
        val e = PrivilegedAccess.epoch
        if (e != seenEpoch) {
            seenEpoch = e
            resetRuntimeState()
        }
    }

    fun getSourceLabel(): String = lastSourceLabel

    @Synchronized
    fun getRealFps(): Float {
        if (resetRequested) {
            resetRequested = false
            resetRuntimeState()
        }
        syncEpoch()
        val now = SystemClock.elapsedRealtime()
        val age = now - lastResultAt
        if (lastResultMode == currentMode && age >= 0L && age < SHARE_MS) return lastResult
        val v = computeRealFps()
        lastResult = v
        lastResultMode = currentMode
        lastResultAt = SystemClock.elapsedRealtime()
        return v
    }

    private fun computeRealFps(): Float {
        if (!PrivilegedAccess.shellWanted()) {
            // BASIC mode: nothing can read the compositor. Report 0 instead of spamming failed shells.
            lastSourceLabel = "Unavailable in Basic mode"
            return 0f
        }
        return when (currentMode) {
            FpsMode.AUTO -> getAdaptiveFps()
            FpsMode.UNIVERSAL_ANDROID -> {
                var v = getSurfaceFlingerFps()
                if (isUsableFps(v)) lastSourceLabel = "SurfaceFlinger 1013"
                else {
                    v = getLatencyFps()
                    if (isUsableFps(v)) lastSourceLabel = "SurfaceFlinger latency"
                }
                v
            }
            FpsMode.UNIVERSAL_GPU -> getKernelFps().also { if (isUsableFps(it)) lastSourceLabel = "Kernel node: ${fpsFilePath ?: "unknown"}" }
            FpsMode.UNIVERSAL_BETA -> getUniversalDevicesFps()
        }
    }

    /**
     * Automatic chain (cached discovery + runtime validation):
     *
     * 1) kernel measured_fps node          (root, or shell if SELinux allows it)
     * 2) SurfaceFlinger --latency          (works for shell uid - the no-root workhorse)
     * 3) SurfaceFlinger 1013 frame counter (shell uid or root, removed on some Android builds)
     * 4) timestats                         (last-resort diagnostic)
     */
    private fun getAdaptiveFps(): Float {
        val now = SystemClock.elapsedRealtime()
        if (now >= kernelDisabledUntil) {
            val kernel = getKernelFps(allowDiscovery = true)
            if (isUsableFps(kernel)) {
                kernelFailures = 0
                lastSourceLabel = "Kernel node: ${fpsFilePath ?: "discovered"}"
                return kernel
            }
        }

        val latency = getLatencyFps()
        if (isUsableFps(latency)) {
            lastSourceLabel = "SurfaceFlinger latency"
            return latency
        }

        val surface = getSurfaceFlingerFps()
        if (isUsableFps(surface)) {
            lastSourceLabel = "SurfaceFlinger 1013"
            return surface
        }

        // A static screen legitimately produces 0 fps from the first two sources; only dig into
        // timestats when nothing has been learnt at all (layer unresolved).
        if (sfLayer != null) return 0f
        val dump = getDumpsysFps()
        if (isUsableFps(dump)) lastSourceLabel = "SurfaceFlinger timestats"
        return dump
    }

    // ---------------------------------------------------------------- kernel node

    private fun getKernelFps(allowDiscovery: Boolean = true): Float {
        if (fpsFilePath.isNullOrEmpty() && allowDiscovery) discoverFpsPath()
        val path = fpsFilePath ?: return 0f
        if (path.isEmpty()) return 0f

        val output = KernelRootRuntime.readNodeBlocking(path)
        if (output.isBlank()) {
            // Empty output = node unreadable for this uid. Back off instead of re-running the
            // recursive find on every tick (that used to freeze the overlay over Shizuku).
            kernelFailures++
            if (kernelFailures >= 3) {
                fpsFilePath = null
                kernelDisabledUntil = SystemClock.elapsedRealtime() + 60_000L
                kernelFailures = 0
            }
            return 0f
        }
        kernelFailures = 0
        return parseKernelFps(output)
    }

    private fun discoverFpsPath() {
        val now = SystemClock.elapsedRealtime()
        if (discoveryAttempted && now - discoveryAt < 60_000L) return
        discoveryAttempted = true
        discoveryAt = now

        val known = listOf(
            "/sys/class/drm/sde-crtc-0/measured_fps" to true,
            "/sys/class/graphics/fb0/measured_fps" to false,
            "/sys/class/video/fps_info" to false
        )

        // One batched read: also proves the node is *readable* (exists != readable under SELinux).
        val probed = KernelRootRuntime.readNodesBlocking(known.map { it.first })
        for ((path, secondColumn) in known) {
            if (!probed[path].isNullOrBlank()) {
                fpsFilePath = path
                fpsValueColumn = secondColumn
                return
            }
        }

        // Bounded search: only display-related sysfs roots, shallow, hard time limit. The old
        // `find /sys` walked the whole tree and regularly hit the shell timeout.
        val command = "timeout 4 find /sys/class/drm /sys/class/graphics /sys/class/video " +
            "/sys/devices/virtual/graphics /sys/devices/platform -maxdepth 6 -type f " +
            "\\( -name measured_fps -o -name fps \\) 2>/dev/null"
        val result = KernelRootRuntime.executeBlocking(command)
        if (result.isSuccess || result.out.isNotEmpty()) {
            val candidates = result.outputString.lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("/sys/") }
                .filter { path ->
                    val lower = path.lowercase(Locale.US)
                    lower.contains("crtc") || lower.contains("drm") ||
                        lower.contains("graphics") || lower.contains("video") ||
                        lower.contains("display")
                }
                .distinct()
                .toList()

            measuredFpsCandidates.clear()
            measuredFpsCandidates.addAll(candidates.filter { it.endsWith("measured_fps") })
            measuredFpsCandidates.addAll(candidates.filter { !it.endsWith("measured_fps") })

            // Pick the first candidate that is actually readable by this uid.
            val readable = KernelRootRuntime.readNodesBlocking(measuredFpsCandidates.toList().take(12))
            val selected = measuredFpsCandidates.firstOrNull { !readable[it].isNullOrBlank() }
            if (selected != null) {
                fpsFilePath = selected
                fpsValueColumn = selected.endsWith("sde-crtc-0/measured_fps")
            }
        }
    }

    private fun parseKernelFps(output: String): Float {
        val clean = output.trim()
        if (clean.isEmpty()) return 0f

        // Some Qualcomm/display nodes expose two columns; VTools reads column #2.
        if (fpsValueColumn) {
            val tokens = clean.split(Regex("\\s+"))
            if (tokens.size >= 2) {
                tokens[1].toFloatOrNull()?.let { return it }
            }
        }

        clean.toFloatOrNull()?.let { return it }
        val tokens = clean.split(Regex("\\s+"))
        tokens.asSequence().mapNotNull { it.toFloatOrNull() }.firstOrNull()?.let { return it }
        return 0f
    }

    // ---------------------------------------------------------------- SurfaceFlinger --latency

    private fun q(s: String) = "'" + s.replace("'", "'\"'\"'") + "'"

    private fun fmt(v: Float) = String.format(Locale.US, "%.1f", v)
    private fun short(layer: String) = layer.take(46)

    /** [usable] = the layer answered with frame timestamps (so a 0 means "no new frames", not "no data"). */
    private class LatencyResult(val fps: Float, val usable: Boolean)

    /** Real per-app FPS from the compositor's frame timestamps. Works with shell uid, no root. */
    private fun getLatencyFps(): Float = latencyTick().fps

    private fun latencyTick(): LatencyResult {
        return try {
            val detected = MonitorReader.getForegroundApp()
            diagFg = when {
                detected.pkg.isEmpty() -> "unknown (scanning all layers)"
                else -> detected.pkg + (if (detected.activity.isNotEmpty()) "/" + detected.activity.substringAfterLast('.') else "") +
                    (if (detected.viaShell) "" else " (usagestats)")
            }
            // Foreground unknown (launcher / lookup failed): do not give up with a hard 0 - scan every
            // app layer and follow whichever one is really producing frames.
            val app = if (detected.pkg.isBlank()) MonitorReader.ForegroundApp(UNKNOWN_FG, "", false) else detected
            val pkg = app.pkg
            val now = SystemClock.elapsedRealtime()

            val mustResolve = sfLayerPkg != pkg ||
                (sfLayer == null && now - sfResolveAt >= 1_500L) ||
                (sfEmptyTicks >= 3 && now - sfLayerAt > 4_000L)
            if (mustResolve) resolveLayer(app)

            val layer = sfLayer
            if (layer == null) {
                diagLat = "no SurfaceFlinger layer found for $pkg"
                return LatencyResult(0f, false)
            }

            val res = KernelRootRuntime.executeBlocking("dumpsys SurfaceFlinger --latency ${q(layer)} 2>/dev/null")
            val ts = SfLatency.timestamps(res.out)
            if (ts.size < 2) {
                sfEmptyTicks++
                diagLat = "layer ${short(layer)}: no timestamps (${res.out.size} lines" +
                    (if (!res.isSuccess) ", ${res.errorString.take(60)}" else "") + ")"
                return LatencyResult(0f, false)
            }
            val fps = latencyTracker.feed(ts, now)
            // Timestamps exist but nothing new is being drawn: the chosen layer may be a stale container
            // (e.g. the activity layer while the game draws in a sibling SurfaceView), or it was picked
            // while the app was still loading. Re-pick the layer instead of reading 0 forever.
            if (fps < 0.1f) {
                sfEmptyTicks++
            } else {
                sfEmptyTicks = 0
            }
            diagLat = "layer ${short(layer)}${if (sfLayerUniversal) " [universal]" else ""} n=${ts.size} -> ${fmt(fps)}"
            LatencyResult(fps, true)
        } catch (t: Throwable) {
            diagLat = "error: ${t.javaClass.simpleName}"
            LatencyResult(0f, false)
        }
    }

    /**
     * Picks the layer that really carries the app's frames. Games render into a SurfaceView
     * layer, ordinary apps into the activity layer; we ask SurfaceFlinger for all candidates in
     * one shell call and keep the one with the freshest, densest timestamps.
     *
     * Candidate sources, in order: layers carrying the package name -> any game-like layer (when
     * the package is not part of a layer name / UsageStats named another task) -> guessed names built
     * from the foreground activity (when `--list` printed nothing).
     */
    private fun resolveLayer(app: MonitorReader.ForegroundApp) {
        val pkg = app.pkg
        val now = SystemClock.elapsedRealtime()
        sfLayerPkg = pkg
        sfLayerAt = now
        sfResolveAt = now
        latencyTracker.reset()
        latZeroTicks = 0
        sfEmptyTicks = 0
        sfLayer = null
        sfLayerUniversal = false

        val list = KernelRootRuntime.executeBlocking("dumpsys SurfaceFlinger --list 2>/dev/null")
        val own = KernelRuntimeContext.context?.packageName.orEmpty()

        var candidates: List<String> = if (list.out.isEmpty()) emptyList() else LayerNames.latencyCandidates(list.out, pkg, 4)
        var guessed = false
        var universal = false
        if (candidates.isEmpty() && list.out.isNotEmpty()) {
            candidates = LayerNames.universalCandidates(list.out, own, 6)
            universal = candidates.isNotEmpty()
        }
        if (candidates.isEmpty()) {
            candidates = LayerNames.synthetic(pkg, app.activity)
            guessed = candidates.isNotEmpty()
        }
        if (candidates.isEmpty()) return
        var best = measureBest(candidates)
        var usedUniversal = universal
        // The app's own layers exist but none is drawing (stale container, app still loading, or the
        // frames live in a layer whose name does not carry the package): look at every app layer.
        if (best == null && !guessed && !universal && list.out.isNotEmpty()) {
            val extra = LayerNames.universalCandidates(list.out, own, 6).filter { it !in candidates }
            if (extra.isNotEmpty()) {
                best = measureBest(extra)
                if (best != null) usedUniversal = true
            }
        }
        // Guessed / universal names are only trusted once they produced timestamps; real package
        // layers are kept even when idle so the next tick can still read them.
        sfLayer = best ?: if (guessed || universal) null else candidates.first()
        sfLayerUniversal = usedUniversal && sfLayer != null && best != null
    }

    /** Runs `--latency` for every candidate in one shell call; freshest + densest layer wins. */
    private fun measureBest(candidates: List<String>): String? {
        if (candidates.isEmpty()) return null
        val script = candidates.joinToString("\n") {
            "echo '@@ZKMLAYER'; dumpsys SurfaceFlinger --latency ${q(it)} 2>/dev/null"
        }
        val res = KernelRootRuntime.executeBlocking(script)
        var best: String? = null
        var bestScore = -1L
        var idx = -1
        val buf = ArrayList<String>()
        fun flush() {
            if (idx in candidates.indices) {
                val ts = SfLatency.timestamps(buf)
                if (ts.size >= 2) {
                    val score = ts.last() / 1_000_000L * 1000L + ts.size
                    if (score > bestScore) { bestScore = score; best = candidates[idx] }
                }
            }
            buf.clear()
        }
        for (line in res.out) {
            if (line.trim() == "@@ZKMLAYER") { flush(); idx++ } else buf.add(line)
        }
        flush()
        return best
    }

    // ---------------------------------------------------------------- legacy sources

    private fun getSurfaceFlingerFps(): Float {
        return try {
            val result = KernelRootRuntime.executeBlocking("service call SurfaceFlinger 1013")
            if (!result.isSuccess) return 0f
            val output = result.outputString
            if (!output.contains("Parcel", ignoreCase = true)) return 0f

            // VTools' original parser reads the first 32-bit frame counter from Parcel.
            val matcher = Pattern.compile("\\(([0-9a-fA-F]{8})").matcher(output)
            if (!matcher.find()) return 0f

            val currentFrames = matcher.group(1)?.toLongOrNull(16) ?: return 0f
            val now = System.currentTimeMillis()
            if (lastTime <= 0L || lastFrames < 0L) {
                lastFrames = currentFrames
                lastTime = now
                return 0f
            }

            val elapsed = now - lastTime
            val delta = currentFrames - lastFrames
            lastFrames = currentFrames
            lastTime = now

            if (elapsed <= 0L || delta < 0L) return 0f
            (delta * 1000f / elapsed).takeIf { it in 0f..240f } ?: 0f
        } catch (_: Throwable) {
            0f
        }
    }

    // ---------------------------------------------------------------- timestats (v3)

    private val tsEngine = TimeStatsEngine()
    private var tsEnabledEpoch = -1
    private var tsForceEnable = false
    private var tsIdleTicks = 0

    /**
     * One shell process per call: enable (only when needed) + dump + device-side timestamps.
     * All maths lives in [TimeStatsEngine] (pure, unit-tested).
     */
    private fun timeStatsSample(): TimeStatsEngine.Result {
        val invalid = TimeStatsEngine.Result(0f, null, matchedApp = false, valid = false)
        return try {
            val needEnable = tsForceEnable || tsEnabledEpoch != PrivilegedAccess.epoch
            val t0 = SystemClock.elapsedRealtimeNanos()
            val res = KernelRootRuntime.executeBlocking(TimeStatsScript.dumpScript(needEnable))
            val t1 = SystemClock.elapsedRealtimeNanos()
            val parsed = parseTimeStatsDump(res.out)
            if (parsed.frames.isEmpty()) {
                // timestats off / SurfaceFlinger restarted / shell not ready: enable again next time.
                tsForceEnable = true
                tsEngine.reset()
                diagTs = "no data (" + (if (res.isSuccess) "dump empty, re-enabling" else res.errorString.take(60)) + ")"
                return invalid
            }
            tsForceEnable = false
            tsEnabledEpoch = PrivilegedAccess.epoch
            // Prefer the device-side midpoint; fall back to the app-side one if markers are missing.
            val atSec = parsed.midpointSec ?: (((t0 + t1) / 2) / 1_000_000_000.0)
            val pkg = runCatching { MonitorReader.getForegroundPackage() }.getOrDefault("")
            val own = KernelRuntimeContext.context?.packageName.orEmpty()
            tsEngine.feed(atSec, parsed.frames, pkg, own).also { r ->
                diagTs = "${parsed.frames.size} layers, " +
                    (if (r.matchedApp) "app layer ${short(r.layer.orEmpty())}" else "no layer carries $pkg") +
                    " -> ${fmt(r.fps)}"
            }
        } catch (t: Throwable) {
            diagTs = "error: ${t.javaClass.simpleName}"
            invalid
        }
    }

    private fun getDumpsysFps(): Float = timeStatsSample().fps

    /**
     * "Universal Devices (BETA)" - every no-root source, in the order that proved reliable over a
     * plain shell uid (Shizuku / wireless debugging):
     *
     *  1. `dumpsys SurfaceFlinger --latency <layer>`  (layer resolved from the real foreground app,
     *     tolerant to Android 10-16 layer-name formats, with guessed / universal fallbacks)
     *  2. SurfaceFlinger timestats                    (cross-check; also takes over when latency is dead)
     *  3. `dumpsys gfxinfo <pkg>` frame counter       (ordinary HWUI apps)
     *
     * A source that answered with data but no new frames is a real "0 fps" (static screen) and ends
     * the chain; only "no data" falls through to the next source, so a static screen costs one shell
     * call per tick, not three.
     */
    private fun getUniversalDevicesFps(): Float {
        val lat = latencyTick()
        if (lat.usable) {
            if (lat.fps >= 0.1f) {
                latZeroTicks = 0
                lastSourceLabel = "SurfaceFlinger latency"
                return lat.fps
            }
            latZeroTicks++
            // Zero for a while: either the screen is really static, or the chosen layer is stale.
            // Ask timestats (it needs no layer name) before believing it.
            if (latZeroTicks < 3) {
                lastSourceLabel = "SurfaceFlinger latency"
                return 0f
            }
            val ts = timeStatsSample()
            if (ts.valid && ts.fps >= 0.1f) {
                lastSourceLabel = "SurfaceFlinger timestats"
                sfEmptyTicks = 3; sfLayerAt = 0L // re-pick the layer on the next tick
                return ts.fps
            }
            lastSourceLabel = "SurfaceFlinger latency"
            return 0f
        }

        val ts = timeStatsSample()
        if (ts.valid && (ts.fps >= 0.1f || ts.matchedApp)) {
            lastSourceLabel = "SurfaceFlinger timestats"
            return ts.fps
        }

        val pkg = MonitorReader.getForegroundApp().pkg
        if (pkg.isNotEmpty()) {
            val gfx = getGfxInfoFps(pkg)
            if (gfx != null) {
                lastSourceLabel = "gfxinfo frame counter"
                return gfx
            }
        }
        lastSourceLabel = "No FPS source answered"
        return 0f
    }

    /** `Total frames rendered` delta of the foreground app. null = the app has no HWUI stats. */
    private fun getGfxInfoFps(pkg: String): Float? {
        return try {
            if (gfxPkg != pkg) {
                gfxPkg = pkg
                gfxPrevTotal = -1L
                gfxPrevAt = 0L
            }
            val res = KernelRootRuntime.executeBlocking(
                "dumpsys gfxinfo ${q(pkg)} framestats 2>/dev/null | grep -E 'Total frames rendered'"
            )
            val m = Regex("""Total frames rendered:\s*(\d+)""").find(res.outputString)
            if (m == null) {
                diagGfx = "no HWUI stats for $pkg"
                return null
            }
            val total = m.groupValues[1].toLongOrNull() ?: return null
            val now = SystemClock.elapsedRealtime()
            val prevTotal = gfxPrevTotal
            val prevAt = gfxPrevAt
            gfxPrevTotal = total
            gfxPrevAt = now
            if (prevTotal < 0L || total < prevTotal || now <= prevAt) {
                diagGfx = "baseline taken ($total frames)"
                return 0f
            }
            val fps = ((total - prevTotal) * 1000f / (now - prevAt)).takeIf { it in 0f..480f } ?: 0f
            diagGfx = "$total frames -> ${fmt(fps)}"
            fps
        } catch (t: Throwable) {
            diagGfx = "error: ${t.javaClass.simpleName}"
            null
        }
    }

    /** Multi-line, human readable state of the FPS engine (FPS screen / bug reports). */
    fun getDiagnostics(): String = buildString {
        append("mode: ").append(currentMode.name).append(" | access: ").append(PrivilegedAccess.label()).append('\n')
        append("source: ").append(lastSourceLabel).append('\n')
        append("foreground: ").append(diagFg).append('\n')
        append("latency: ").append(diagLat).append('\n')
        append("timestats: ").append(diagTs).append('\n')
        append("gfxinfo: ").append(diagGfx)
        KernelRootRuntime.lastErrorText()?.takeIf { it.isNotBlank() }?.let { append("\nshell: ").append(it.take(80)) }
    }

    private fun isUsableFps(value: Float): Boolean = value in 0.1f..480f
}
