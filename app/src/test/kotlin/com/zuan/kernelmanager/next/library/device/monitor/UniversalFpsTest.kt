package com.zuan.kernelmanager.next.library.device.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalFpsTest {

    private val pkg = "com.game.fun"
    private val sv = "SurfaceView[com.game.fun/com.game.fun.Main](BLAST)#812"
    private val act = "com.game.fun/com.game.fun.Main#0"
    private val sysui = "com.android.systemui/com.android.systemui.StatusBar#3"

    private fun frames(global: Long, game: Long, ui: Long = 5L) = mapOf(
        "" to global, sv to game, act to 10L, sysui to ui
    )

    @Test
    fun parsesRealisticTimestatsOutputWithDeviceTimestamps() {
        val lines = """
            @@ZKM_T0 1000.50
            totalFrames = 4200
            layerName = $sv
            totalFrames = 1800
            layerName = $sysui
            totalFrames = 77
            @@ZKM_T1 1000.70
        """.trimIndent().lines()
        val p = parseTimeStatsDump(lines)
        assertEquals(4200L, p.frames[""])
        assertEquals(1800L, p.frames[sv])
        assertEquals(77L, p.frames[sysui])
        assertEquals(1000.60, p.midpointSec!!, 1e-9)
    }

    @Test
    fun missingMarkersGiveNoMidpoint() {
        val p = parseTimeStatsDump(listOf("totalFrames = 5"))
        assertNull(p.midpointSec)
        assertEquals(5L, p.frames[""])
    }

    @Test
    fun scriptEnablesOnlyWhenAsked() {
        assertTrue(TimeStatsScript.dumpScript(true).contains("--timestats -enable"))
        assertFalse(TimeStatsScript.dumpScript(false).contains("--timestats -enable"))
        assertTrue(TimeStatsScript.dumpScript(false).contains("/proc/uptime"))
    }

    @Test
    fun steadySixtyFpsOverOneSecondWindows() {
        val e = TimeStatsEngine()
        assertEquals(0f, e.feed(100.0, frames(0, 0), pkg).fps, 0f)
        val r = e.feed(101.0, frames(60, 60), pkg)
        assertTrue(r.valid)
        assertTrue(r.matchedApp)
        assertEquals(sv, r.layer)
        assertEquals(60f, r.fps, 0.01f)
    }

    @Test
    fun deviceSideTimingBeatsJitter() {
        // The shell took 1.2 s between two ticks (not 1.0 s): 72 frames / 1.2 s = 60 fps, not 72.
        val e = TimeStatsEngine()
        e.feed(10.0, frames(0, 0), pkg)
        assertEquals(60f, e.feed(11.2, frames(72, 72), pkg).fps, 0.01f)
    }

    @Test
    fun fastPollingStillMeasuresAOneSecondWindow() {
        val e = TimeStatsEngine()
        var t = 0.0
        var f = 0L
        var last = 0f
        repeat(12) {
            last = e.feed(t, frames(f, f), pkg).fps
            t += 0.25
            f += 15 // 60 fps
        }
        assertEquals(60f, last, 0.5f)
    }

    @Test
    fun slowPollingEveryFiveSecondsStillWorks() {
        val e = TimeStatsEngine()
        e.feed(0.0, frames(0, 0), pkg)
        assertEquals(30f, e.feed(5.0, frames(150, 150), pkg).fps, 0.01f)
        assertEquals(30f, e.feed(10.0, frames(300, 300), pkg).fps, 0.01f)
    }

    @Test
    fun idleAppDropsToZeroAndStaysOnItsOwnLayer() {
        val e = TimeStatsEngine()
        e.feed(0.0, frames(0, 0), pkg)
        e.feed(1.0, frames(60, 60), pkg)
        val a = e.feed(2.0, frames(60, 60), pkg)
        assertTrue(a.matchedApp)
        assertEquals(30f, a.fps, 0.01f) // first idle tick: halved, not an instant cliff
        assertEquals(0f, e.feed(3.0, frames(60, 60), pkg).fps, 0f)
    }

    @Test
    fun systemUiNoiseDoesNotLeakIntoTheAppNumber() {
        val e = TimeStatsEngine()
        e.feed(0.0, frames(0, 0, 0), pkg)
        // status bar redraws 200 times, the game 45 times
        val r = e.feed(1.0, frames(300, 45, 200), pkg)
        assertEquals(45f, r.fps, 0.01f)
    }

    @Test
    fun picksTheBusiestAppLayerSurfaceViewOverActivityLayer() {
        val e = TimeStatsEngine()
        e.feed(0.0, mapOf("" to 0L, sv to 0L, act to 0L), pkg)
        val r = e.feed(1.0, mapOf("" to 200L, sv to 90L, act to 4L), pkg)
        assertEquals(sv, r.layer)
        assertEquals(90f, r.fps, 0.01f)
    }

    @Test
    fun counterResetRebasesInsteadOfPoisoning() {
        val e = TimeStatsEngine()
        e.feed(0.0, frames(1000, 1000), pkg)
        e.feed(1.0, frames(1060, 1060), pkg)
        // SurfaceFlinger restarted / stats cleared: counters start again from ~0
        val afterReset = e.feed(2.0, frames(30, 30), pkg)
        assertEquals(0f, afterReset.fps, 0f)
        val next = e.feed(3.0, frames(90, 90), pkg)
        assertEquals(60f, next.fps, 0.01f)
    }

    @Test
    fun unknownForegroundFallsBackToBusiestNonSystemLayerButNeverOwnApp() {
        val own = "com.zuan.kernelmanager.next"
        val ownLayer = "$own/$own.overlay.FpsOverlay#9"
        val e = TimeStatsEngine()
        e.feed(0.0, mapOf("" to 0L, sv to 0L, sysui to 0L, ownLayer to 0L), "", own)
        val r = e.feed(1.0, mapOf("" to 400L, sv to 58L, sysui to 120L, ownLayer to 100L), "", own)
        assertEquals(sv, r.layer)
        assertEquals(58f, r.fps, 0.01f)
        assertFalse(r.matchedApp)
    }

    @Test
    fun packageSwitchResetsSmoothing() {
        val e = TimeStatsEngine()
        e.feed(0.0, frames(0, 0), pkg)
        e.feed(1.0, frames(60, 60), pkg)
        val other = "com.other.app"
        val l2 = "$other/$other.Main#1"
        val m1 = mapOf("" to 100L, l2 to 100L, sv to 60L)
        val m0 = mapOf("" to 0L, l2 to 0L, sv to 60L)
        e.reset()
        e.feed(5.0, m0, other)
        val r = e.feed(6.0, m1, other)
        assertEquals(l2, r.layer)
        assertEquals(100f, r.fps, 0.01f)
    }

    @Test
    fun emptyDumpIsReportedAsInvalid() {
        val e = TimeStatsEngine()
        val r = e.feed(1.0, emptyMap(), pkg)
        assertFalse(r.valid)
        assertEquals(0f, r.fps, 0f)
    }

    @Test
    fun duplicatedClockIsIgnored() {
        val e = TimeStatsEngine()
        e.feed(0.0, frames(0, 0), pkg)
        val a = e.feed(1.0, frames(60, 60), pkg)
        val b = e.feed(1.0, frames(60, 60), pkg)
        assertEquals(a.fps, b.fps, 0f)
    }

    // ---------------------------------------------------------------- layer names for --latency

    @Test
    fun layerListOldAndNewFormats() {
        val list = listOf(
            "Background for SurfaceView[com.game.fun/com.game.fun.Main](BLAST)#813",
            "SurfaceView[com.game.fun/com.game.fun.Main](BLAST)#812",
            "com.game.fun/com.game.fun.Main#0",
            "RequestedLayerState{1a2b SurfaceView[com.game.fun/com.game.fun.Main](BLAST)#900 parentId=5 z=-2}",
            "RequestedLayerState{3c4d com.game.fun/com.game.fun.Main#0 parentId=1 z=0}",
            "RequestedLayerState{3c4d ActivityRecord{abc u0 com.game.fun/.Main} parentId=1 z=0}",
            "com.android.systemui/com.android.systemui.StatusBar#3"
        )
        val c = LayerNames.latencyCandidates(list, pkg)
        assertEquals("SurfaceView[com.game.fun/com.game.fun.Main](BLAST)#812", c.first())
        assertTrue(c.contains("SurfaceView[com.game.fun/com.game.fun.Main](BLAST)#900"))
        assertTrue(c.contains("com.game.fun/com.game.fun.Main#0"))
        assertTrue(c.none { it.contains("RequestedLayerState") || it.contains("Background") || it.contains("ActivityRecord") })
        assertEquals(emptyList<String>(), LayerNames.latencyCandidates(list, ""))
        assertNotNull(c)
    }
}
