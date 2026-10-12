/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.library.device.monitor

/**
 * Turns `dumpsys SurfaceFlinger --list` into names that `--latency <name>` accepts.
 *
 * Android <= 14 prints one bare layer name per line. Android 15/16 (layer lifecycle manager)
 * prints `RequestedLayerState{... SurfaceView[pkg/act](BLAST)#829 parentId=828 z=-2}`; handing that
 * whole line to `--latency` returns nothing.
 *
 * v2: the wrapper is no longer stripped with one anchored regex (it silently produced nothing when a
 * vendor build printed the wrapper slightly differently). The canonical name is *searched for* in
 * the line instead - the same approach as the reference no-root source - so old, new and vendor
 * formats all work, and when no layer carries the foreground package at all the caller can still
 * fall back to [universalCandidates] / [synthetic] instead of reporting 0 fps.
 */
internal object LayerNames {

    private val surfaceViewName = Regex(
        """SurfaceView\s-\s[A-Za-z0-9._$]+/[A-Za-z0-9._$]+(?:#\d+)?""" +
            """|SurfaceView\[[^\]]+\](?:\(BLAST\))?(?:#\d+)?"""
    )
    private val activityName = Regex("""[A-Za-z][A-Za-z0-9._$]*/[A-Za-z0-9._$]+(?:#\d+)?""")

    /** Raw-line filter: layers that never carry the app's own frames. */
    private val deadTokens = listOf(
        "Background", "ActivityRecord", "InputSink", "InputMethod", "AppWindowToken", "WindowToken",
        "Bounds for", "StatusBar", "NavigationBar", "ScreenDecor", "NotificationShade",
        "EdgeBackGestureHandler", "ShellDropTarget", "Wallpaper", "DisplayOverlay", "Gesture Monitor",
        "leash", "Leash", "Mirror", "Splash Screen", "SnapshotStartingWindow", "Dim Layer", "Letterbox",
        "VolumePanel", "Toast", "com.android.systemui", "com.android.launcher"
    )

    // "IME" must stand alone as a token; a plain substring test would drop layers such as "...time...".
    private val imeToken = Regex("""(?:^|[^a-zA-Z])(?:IME|Ime|ime)(?![a-zA-Z])""")

    private fun isDeadLine(line: String): Boolean =
        deadTokens.any { line.contains(it) } || imeToken.containsMatchIn(line)

    /** Canonical layer name found inside [line] (null = nothing usable). Dead layers are NOT filtered here. */
    fun canonical(line: String): String? {
        val t = line.trim()
        if (t.isEmpty()) return null
        surfaceViewName.find(t)?.value?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        activityName.find(t)?.value?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        // Bare, unusual name (older vendor builds): usable as is, but never the raw wrapper.
        return t.takeIf { !it.startsWith("RequestedLayerState{") && !it.contains('{') }
    }

    /** Every usable, non-system layer name of the dump, in dump order. */
    fun names(listLines: List<String>): List<String> =
        listLines.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !isDeadLine(it) }
            .mapNotNull(::canonical)
            .filter { !it.lowercase().startsWith("ime") }
            .distinct()
            .toList()

    private fun rank(n: String): Int = when {
        n.startsWith("SurfaceView[") && n.contains("BLAST") -> 0
        n.startsWith("SurfaceView") -> 1
        n.contains("BLAST") -> 2
        else -> 3
    }

    /** Best layers of [pkg] first (games render into SurfaceView, apps into the activity layer). */
    fun latencyCandidates(listLines: List<String>, pkg: String, max: Int = 4): List<String> {
        if (pkg.isBlank()) return emptyList()
        return names(listLines)
            .filter { it.contains(pkg, ignoreCase = true) }
            .sortedBy(::rank)
            .take(max)
    }

    /**
     * Last resort when no layer carries [pkg] (UsageStats named another package, or the vendor
     * prefixes layer names): every game-like layer (SurfaceView / BLAST) except our own. The caller
     * measures them all and keeps the one that is actually producing frames.
     */
    fun universalCandidates(listLines: List<String>, ownPkg: String = "", max: Int = 6): List<String> =
        names(listLines)
            .filter { it.startsWith("SurfaceView") || it.contains("(BLAST)") }
            .filter { ownPkg.isEmpty() || !it.contains(ownPkg) }
            .sortedBy(::rank)
            .take(max)

    /** Educated guesses used when `--list` itself returned nothing (older Android, vendor builds). */
    fun synthetic(pkg: String, activity: String): List<String> {
        if (pkg.isBlank() || activity.isBlank()) return emptyList()
        val cls = if (activity.startsWith(".")) pkg + activity else activity
        return listOf(
            "SurfaceView[$pkg/$cls](BLAST)#0",
            "SurfaceView - $pkg/$cls#0",
            "$pkg/$cls#0"
        )
    }
}
