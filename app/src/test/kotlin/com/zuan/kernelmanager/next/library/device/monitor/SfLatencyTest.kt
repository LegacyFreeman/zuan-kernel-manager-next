package com.zuan.kernelmanager.next.library.device.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SfLatencyTest {

    private val pkg = "com.game.fun"

    // ---------------------------------------------------------------- layer names

    @Test
    fun wrapperWithoutHashIsStillUnwrapped() {
        // Real Android 15/16 builds differ in what precedes the layer name; the name is searched, not anchored.
        val list = listOf(
            "RequestedLayerState{SurfaceView[com.game.fun/com.game.fun.Main](BLAST)#900 parentId=5 z=-2}",
            "RequestedLayerState{com.game.fun/com.game.fun.Main#0 parentId=1 z=0}",
            "RequestedLayerState{Task=12#3 parentId=1 z=0}"
        )
        val c = LayerNames.latencyCandidates(list, pkg)
        assertEquals(listOf(
            "SurfaceView[com.game.fun/com.game.fun.Main](BLAST)#900",
            "com.game.fun/com.game.fun.Main#0"
        ), c)
    }

    @Test
    fun oldDashStyleSurfaceViewName() {
        val c = LayerNames.latencyCandidates(
            listOf("SurfaceView - com.game.fun/com.game.fun.Main#0", "com.game.fun/com.game.fun.Main#0"), pkg
        )
        assertEquals("SurfaceView - com.game.fun/com.game.fun.Main#0", c.first())
    }

    @Test
    fun noLayerCarriesThePackageGivesNothingButUniversalFindsGameLayers() {
        val list = listOf(
            "SurfaceView[com.vendor.render/com.vendor.render.Game](BLAST)#44",
            "com.android.systemui/com.android.systemui.StatusBar#3",
            "SurfaceView[com.zuan.kernelmanager.next/x.Overlay](BLAST)#9",
            "Background for SurfaceView[com.vendor.render/com.vendor.render.Game](BLAST)#45"
        )
        assertTrue(LayerNames.latencyCandidates(list, pkg).isEmpty())
        val u = LayerNames.universalCandidates(list, "com.zuan.kernelmanager.next")
        assertEquals(listOf("SurfaceView[com.vendor.render/com.vendor.render.Game](BLAST)#44"), u)
    }

    @Test
    fun imeTokenIsDroppedButNamesContainingImeAreKept() {
        val list = listOf(
            "com.game.fun/com.game.fun.TimerActivity#0",
            "com.game.fun/com.game.fun.Main#0 IME#12"
        )
        val c = LayerNames.latencyCandidates(list, pkg)
        assertEquals(listOf("com.game.fun/com.game.fun.TimerActivity#0"), c)
    }

    @Test
    fun syntheticNamesExpandRelativeActivities() {
        val s = LayerNames.synthetic(pkg, ".Main")
        assertTrue(s.contains("SurfaceView[com.game.fun/com.game.fun.Main](BLAST)#0"))
        assertTrue(s.contains("com.game.fun/com.game.fun.Main#0"))
        assertTrue(LayerNames.synthetic(pkg, "").isEmpty())
    }

    // ---------------------------------------------------------------- --latency parsing

    private fun dump(vararg actual: Long, period: Long = 16_666_666L): List<String> =
        listOf(period.toString()) + actual.map { "${it - 1000}\t$it\t${it + 500}" }

    @Test
    fun timestampsUseActualPresentColumnAndSkipPlaceholders() {
        val lines = listOf("16666666") +
            listOf("0\t0\t0", "9223372036854775807\t9223372036854775807\t9223372036854775807") +
            listOf("100\t2000000000\t200", "100\t2016000000\t200")
        assertEquals(listOf(2_000_000_000L, 2_016_000_000L), SfLatency.timestamps(lines))
    }

    @Test
    fun emptyActualColumnFallsBackToDesiredColumn() {
        val lines = listOf("16666666", "5000000000\t0\t0", "5016000000\t0\t0")
        assertEquals(listOf(5_000_000_000L, 5_016_000_000L), SfLatency.timestamps(lines))
    }

    @Test
    fun garbageAndHeaderOnlyGiveNothing() {
        assertTrue(SfLatency.timestamps(listOf("16666666")).isEmpty())
        assertTrue(SfLatency.timestamps(listOf("Permission Denial: can't dump")).isEmpty())
    }

    // ---------------------------------------------------------------- tracker

    private fun frames(startNs: Long, count: Int, stepNs: Long) = (0 until count).map { startNs + it * stepNs }

    @Test
    fun steadySixtyFpsFromFirstSnapshot() {
        val t = LatencyTracker()
        val ts = frames(10_000_000_000L, 100, 16_666_667L)
        assertEquals(60f, t.feed(ts, 0L), 0.2f)
    }

    @Test
    fun secondTickCountsOnlyNewFrames() {
        val t = LatencyTracker()
        val a = frames(10_000_000_000L, 60, 16_666_667L)
        t.feed(a, 0L)
        val b = a + frames(a.last() + 16_666_667L, 60, 16_666_667L)
        assertEquals(60f, t.feed(b.takeLast(127), 1000L), 0.5f)
    }

    @Test
    fun staticScreenHoldsBrieflyThenDropsToZero() {
        val t = LatencyTracker(holdMs = 1200L)
        val a = frames(10_000_000_000L, 60, 16_666_667L)
        assertTrue(t.feed(a, 0L) > 50f)
        assertTrue("held", t.feed(a, 500L) > 50f)          // no new frames, still inside hold
        assertEquals(0f, t.feed(a, 2000L), 0f)               // idle for real
    }

    @Test
    fun resumesAfterIdle() {
        val t = LatencyTracker()
        val a = frames(10_000_000_000L, 30, 33_333_333L)
        t.feed(a, 0L)
        t.feed(a, 3000L)
        val b = a + frames(a.last() + 33_333_333L, 30, 33_333_333L)
        assertEquals(30f, t.feed(b, 4000L), 1f)
    }

    @Test
    fun notEnoughTimestampsIsZeroNotACrash() {
        val t = LatencyTracker()
        assertEquals(0f, t.feed(emptyList(), 0L), 0f)
        assertEquals(0f, t.feed(listOf(1L), 0L), 0f)
        assertFalse(t.feed(frames(1_000_000_000L, 5, 16_666_667L), 0L).isNaN())
    }
}
