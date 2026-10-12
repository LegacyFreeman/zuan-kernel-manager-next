/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.library.device.monitor

import android.os.SystemClock
import com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime
import com.zuan.kernelmanager.next.library.shell.PrivilegedAccess

/**
 * Single GPU frequency / load source for the FPS overlay, the system-monitor overlays and the
 * recorder (they used to have three different, partly wrong, discovery routines).
 *
 * Works in ROOT, ADB (Shizuku, shell uid) and BASIC mode - it just reads whatever the active
 * privilege level is allowed to read:
 *  - a node is only selected after it was *actually read* with a believable value (exists is not
 *    readable under SELinux),
 *  - discovery is re-done when the privilege situation changes (Shizuku binder arriving late used
 *    to leave the GPU at 0 for the whole session) and retried every [RETRY_MS] while nothing is
 *    found, instead of probing on every tick,
 *  - per tick only the selected node(s) are read in one batch (no shell at all when the app uid can
 *    read the node itself),
 *  - when no load node is readable but the clock and the maximum clock are, a clock-ratio estimate
 *    is returned with [GpuSample.loadEstimated] = true so the UI can mark it with "~".
 *    Nothing is invented when even the clock is unavailable: freq = 0 / load = -1 ("N/A").
 */
object GpuReader {
    private const val RETRY_MS = 20_000L
    private const val CACHE_MS = 300L
    private const val MAX_FAILS = 3

    data class GpuSample(
        val freqMHz: Int = 0,
        /** 0..100, -1 = unknown. */
        val loadPct: Int = -1,
        val loadEstimated: Boolean = false,
        val gpuName: String = "GPU",
        val source: String = ""
    ) {
        val hasFreq: Boolean get() = freqMHz > 0
        val hasLoad: Boolean get() = loadPct >= 0
    }

    private var freqNode: FreqNode? = null
    private var loadPath: String? = null
    private var maxFreqMhz = 0
    private var discovered = false
    private var lastDiscoveryAt = 0L
    private var fails = 0
    private var seenEpoch = -1

    @Volatile private var prevGpuBusy = 0L
    @Volatile private var prevGpuTotal = 0L
    @Volatile private var cachedGpuName = "GPU"

    private var cached = GpuSample()
    private var cachedAt = 0L

    @Volatile private var label = "GPU: not detected"
    fun describe(): String = label

    /** Nama chipset/vendor GPU terdeteksi (Adreno, Mali MTK, Mali, Exynos GPU, GPU). */
    fun getGpuName(): String = cachedGpuName

    /** Clock GPU maksimum (MHz) bila terdeteksi, 0 jika tidak. */
    fun maxFreqMHz(): Int = maxFreqMhz

    /** Blocking, IO thread only. Cheap enough to call every overlay tick. */
    @Synchronized
    fun sample(): GpuSample {
        val epoch = PrivilegedAccess.epoch
        if (epoch != seenEpoch) {
            seenEpoch = epoch
            resetLocked()
        }
        val now = SystemClock.elapsedRealtime()
        if (now - cachedAt < CACHE_MS) return cached

        if (!discovered && (lastDiscoveryAt == 0L || now - lastDiscoveryAt >= RETRY_MS)) {
            lastDiscoveryAt = now
            discoverLocked()
        }
        val out = if (discovered) readLocked() else GpuSample()
        cached = out
        cachedAt = SystemClock.elapsedRealtime()
        return out
    }

    fun reset() = synchronized(this) { resetLocked() }

    private fun resetLocked() {
        freqNode = null
        loadPath = null
        maxFreqMhz = 0
        discovered = false
        lastDiscoveryAt = 0L
        fails = 0
        prevGpuBusy = 0L
        prevGpuTotal = 0L
        cachedGpuName = "GPU"
        cached = GpuSample()
        cachedAt = 0L
        label = "GPU: not detected"
    }

    // ------------------------------------------------------------------------------------------

    private fun discoverLocked() {
        // 1) static catalogue, one batch
        val statics = ArrayList<String>().apply {
            GpuParse.freqNodes.forEach { add(it.path) }
            addAll(GpuParse.loadNodes)
            GpuParse.maxFreqNodes.forEach { add(it.path) }
        }
        var values = KernelRootRuntime.readNodesBlocking(statics)

        var pickedFreq = pickFreq(GpuParse.freqNodes, values)
        var pickedLoad = pickLoad(GpuParse.loadNodes, values)
        var maxMhz = pickMax(GpuParse.maxFreqNodes, values)

        // 2) dynamic devfreq / platform scan for GPUs that use a non-standard name
        if (pickedFreq == null || pickedLoad == null) {
            val dynFreq = ArrayList<FreqNode>()
            val dynLoad = ArrayList<String>()
            val dynMax = ArrayList<FreqNode>()
            val dirs = LinkedHashSet<String>()
            KernelRootRuntime.listDirectoriesBlocking("/sys/class/devfreq")
                .filter(GpuParse::looksLikeGpuDir)
                .forEach { dirs.add("/sys/class/devfreq/$it") }
            KernelRootRuntime.listDirectoriesBlocking("/sys/devices/platform")
                .filter { it.lowercase().let { n -> n.contains("mali") || n.contains("gpu") } }
                .take(4)
                .forEach { plat ->
                    dirs.add("/sys/devices/platform/$plat")
                    KernelRootRuntime.listDirectoriesBlocking("/sys/devices/platform/$plat/devfreq")
                        .take(2).forEach { dirs.add("/sys/devices/platform/$plat/devfreq/$it") }
                }
            dirs.take(8).forEach { dir ->
                dynFreq += FreqNode("$dir/cur_freq", FreqStyle.PLAIN)
                dynMax += FreqNode("$dir/max_freq", FreqStyle.PLAIN)
                dynLoad += GpuParse.loadNodesIn(dir)
            }
            val extra = ArrayList<String>().apply {
                dynFreq.forEach { add(it.path) }
                dynMax.forEach { add(it.path) }
                addAll(dynLoad)
            }.filter { it !in values }
            if (extra.isNotEmpty()) values = values + KernelRootRuntime.readNodesBlocking(extra)
            if (pickedFreq == null) pickedFreq = pickFreq(dynFreq, values)
            if (pickedLoad == null) pickedLoad = pickLoad(dynLoad, values)
            if (maxMhz == 0) maxMhz = pickMax(dynMax, values)
        }

        // 3) max clock from available frequencies list when max_freq node is clamped or unreadable
        if (maxMhz <= 200) {
            val availCandidates = listOfNotNull(
                "/sys/class/kgsl/kgsl-3d0/gpu_available_frequencies",
                "/sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies",
                pickedFreq?.path?.takeIf { it.endsWith("/cur_freq") }?.let { it.removeSuffix("cur_freq") + "available_frequencies" }
            )
            val availMap = KernelRootRuntime.readNodesBlocking(availCandidates)
            val foundMax = availCandidates.mapNotNull { GpuParse.parseMaxOfList(availMap[it]) }.maxOrNull()
            if (foundMax != null && foundMax > maxMhz) {
                maxMhz = foundMax
            }
        }

        freqNode = pickedFreq
        loadPath = pickedLoad
        maxFreqMhz = maxMhz
        fails = 0
        discovered = pickedFreq != null || pickedLoad != null
        cachedGpuName = when {
            pickedFreq?.path?.contains("kgsl", true) == true || pickedLoad?.contains("kgsl", true) == true -> "Adreno"
            pickedFreq?.path?.contains("mtk", true) == true || pickedFreq?.path?.contains("gpufreq", true) == true || pickedLoad?.contains("ged", true) == true -> "Mali MTK"
            pickedFreq?.path?.contains("mali", true) == true || pickedLoad?.contains("mali", true) == true -> "Mali"
            pickedFreq?.path?.contains("gpu_clock", true) == true || pickedLoad?.contains("gpu_busy", true) == true -> "Exynos GPU"
            else -> "GPU"
        }
        label = when {
            !discovered -> "GPU: no readable node (${PrivilegedAccess.label()})"
            else -> buildString {
                append(cachedGpuName).append(": ")
                append(pickedFreq?.path?.substringAfter("/sys/")?.substringBefore("/cur")?.take(40) ?: "-")
                append(" | load: ")
                append(pickedLoad?.substringAfterLast('/') ?: if (maxMhz > 0) "estimated" else "n/a")
                append(" | ").append(PrivilegedAccess.label())
            }
        }
    }

    /** First readable node; a node that reads > 0 beats one that reads 0 (idle or dead). */
    private fun pickFreq(nodes: List<FreqNode>, values: Map<String, String>): FreqNode? {
        var zero: FreqNode? = null
        for (n in nodes) {
            val mhz = GpuParse.parseFreqMhz(values[n.path], n.style) ?: continue
            if (mhz > 0) return n
            if (zero == null) zero = n
        }
        return zero
    }

    private fun pickLoad(paths: List<String>, values: Map<String, String>): String? =
        paths.firstOrNull { GpuParse.parseLoadPct(values[it], it) != null }

    private fun pickMax(nodes: List<FreqNode>, values: Map<String, String>): Int {
        for (n in nodes) {
            val mhz = GpuParse.parseFreqMhz(values[n.path], n.style) ?: continue
            if (mhz > 0) return mhz
        }
        return 0
    }

    private fun readLocked(): GpuSample {
        val fNode = freqNode
        val lPath = loadPath
        val wanted = ArrayList<String>(2)
        fNode?.let { wanted += it.path }
        lPath?.let { wanted += it }
        val values = KernelRootRuntime.readNodesBlocking(wanted)

        val mhz = fNode?.let { GpuParse.parseFreqMhz(values[it.path], it.style) }
        var load = lPath?.let { path ->
            val raw = values[path]
            if (path.contains("gpubusy") && !path.contains("percentage")) {
                val t = GpuParse.tokens(raw.orEmpty())
                if (t.size >= 2) {
                    val busy = t[0]
                    val total = t[1]
                    val dBusy = busy - prevGpuBusy
                    val dTotal = total - prevGpuTotal
                    val deltaLoad = if (prevGpuTotal > 0L && total > prevGpuTotal && dTotal > 0L && dBusy >= 0L) {
                        ((dBusy * 100L) / dTotal).toInt().coerceIn(0, 100)
                    } else if (total > 0L) {
                        ((busy * 100L) / total).toInt().coerceIn(0, 100)
                    } else null
                    prevGpuBusy = busy
                    prevGpuTotal = total
                    deltaLoad
                } else {
                    GpuParse.parseLoadPct(raw, path)
                }
            } else {
                GpuParse.parseLoadPct(raw, path)
            }
        }

        // Both selected nodes stopped answering (Shizuku died, SELinux flip): rediscover soon.
        if ((fNode != null && mhz == null) && (lPath == null || load == null)) {
            if (++fails >= MAX_FAILS) {
                discovered = false
                lastDiscoveryAt = SystemClock.elapsedRealtime() - RETRY_MS + 2_000L
            }
        } else {
            fails = 0
        }

        var estimated = false
        val effectiveMax = if (maxFreqMhz > 0) maxFreqMhz else 900
        if (load == null && (mhz ?: 0) > 0) {
            load = ((mhz!! * 100) / effectiveMax).coerceIn(1, 100)
            estimated = true
        } else if (load == null && mhz == 0) {
            load = 0
            estimated = true
        }
        return GpuSample(
            freqMHz = mhz ?: 0,
            loadPct = load ?: -1,
            loadEstimated = estimated,
            gpuName = cachedGpuName,
            source = label
        )
    }
}
