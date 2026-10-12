package com.zuan.kernelmanager.next.library.device.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpuParseTest {

    @Test
    fun magnitudesBecomeMhz() {
        assertEquals(710, GpuParse.toMhz(710_000_000L)) // Hz   (kgsl gpuclk)
        assertEquals(1200, GpuParse.toMhz(1_200_000_000L)) // Hz > 1 GHz
        assertEquals(800, GpuParse.toMhz(800_000L))     // kHz  (MediaTek)
        assertEquals(485, GpuParse.toMhz(485L))         // MHz
        assertEquals(0, GpuParse.toMhz(0L))             // idle
        assertNull(GpuParse.toMhz(-3L))
        assertNull(GpuParse.toMhz(99_999_999_999_999L))
    }

    @Test
    fun plainNodes() {
        assertEquals(645, GpuParse.parseFreqMhz("645000000\n", FreqStyle.PLAIN))
        assertEquals(0, GpuParse.parseFreqMhz("0", FreqStyle.PLAIN))
        assertNull(GpuParse.parseFreqMhz("", FreqStyle.PLAIN))
        assertNull(GpuParse.parseFreqMhz(null, FreqStyle.PLAIN))
        assertNull(GpuParse.parseFreqMhz("Permission denied", FreqStyle.PLAIN))
    }

    @Test
    fun mediatekGedTakesTheBiggestNumber() {
        assertEquals(880, GpuParse.parseFreqMhz("3 880000", FreqStyle.BIGGEST))
    }

    @Test
    fun mediatekOppTableNeedsTheActiveMarker() {
        val noMarker = "[00] freq: 900000, volt: 80000\n[01] freq: 700000, volt: 70000"
        assertNull(GpuParse.parseFreqMhz(noMarker, FreqStyle.MTK_TABLE)) // would be "max OPP" = a lie
        val marker = "[00] freq: 900000, volt: 80000\n[01*] freq: 700000, volt: 70000"
        assertEquals(700, GpuParse.parseFreqMhz(marker, FreqStyle.MTK_TABLE))
    }

    @Test
    fun dumpsPreferTheCurrentKeyOverMaxMin() {
        val dump = "g_max_opp_freq = 900000\ng_cur_opp_freq = 612000\ng_min_opp_freq = 300000"
        assertEquals(612, GpuParse.parseFreqMhz(dump, FreqStyle.DUMP))
        assertEquals(700, GpuParse.parseFreqMhz("freq: 700000", FreqStyle.DUMP))
    }

    @Test
    fun availableFrequenciesGiveTheMax() {
        assertEquals(840, GpuParse.parseMaxOfList("257000000 400000000 840000000"))
        assertNull(GpuParse.parseMaxOfList(""))
    }

    @Test
    fun loadVariants() {
        assertEquals(37, GpuParse.parseLoadPct("37 %", "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage"))
        assertEquals(50, GpuParse.parseLoadPct("  250 500", "/sys/class/kgsl/kgsl-3d0/gpubusy"))
        assertNull(GpuParse.parseLoadPct("0 0", "/sys/class/kgsl/kgsl-3d0/gpubusy"))
        assertEquals(50, GpuParse.parseLoadPct("128", "/sys/devices/platform/x.mali/utilization")) // 8-bit
        assertEquals(60, GpuParse.parseLoadPct("600", "/x/load"))                                  // per-mille
        assertEquals(100, GpuParse.parseLoadPct("99999", "/x/load"))
        assertNull(GpuParse.parseLoadPct("", "/x/load"))
        assertNull(GpuParse.parseLoadPct("denied", "/x/load"))
    }

    @Test
    fun catalogueHasNoDuplicatePathsAndFitsOneBatch() {
        val all = GpuParse.freqNodes.map { it.path } + GpuParse.loadNodes + GpuParse.maxFreqNodes.map { it.path }
        assertEquals(all.size, all.toSet().size)
        assertTrue(all.size <= 96) // KernelRootRuntime.MAX_BATCH
        assertTrue(GpuParse.looksLikeGpuDir("13000000.mali"))
        assertTrue(GpuParse.looksLikeGpuDir("kgsl-3d0"))
        assertTrue(!GpuParse.looksLikeGpuDir("cpubw"))
    }
}
