/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.library.device.monitor

/**
 * Pure parsers + node catalogue for GPU frequency / load. No Android types so it can be unit tested.
 *
 * Ported and tightened from the FPS/GPU reader supplied for the no-root build:
 *  - a MediaTek OPP *table* is only trusted when it marks the active OPP (otherwise the first row is
 *    the maximum OPP and the overlay would show "max clock" forever),
 *  - "current freq" keys are preferred in gpufreq dumps (`g_cur_opp_freq`) over "max"/"min" ones,
 *  - Hz / kHz / MHz are told apart by magnitude and sanity-checked against 1..5000 MHz.
 */
internal enum class FreqStyle {
    /** One number (Hz, kHz or MHz). */
    PLAIN,
    /** Several numbers on one line, the frequency being the biggest (ged `current_freqency`). */
    BIGGEST,
    /** MediaTek `stack_signed_opp_table`: needs the `[nn*]` active marker. */
    MTK_TABLE,
    /** Text dump with `...cur...freq = N` / `freq: N`. */
    DUMP
}

internal data class FreqNode(val path: String, val style: FreqStyle)

internal object GpuParse {

    // ---- catalogue ---------------------------------------------------------------------------

    val freqNodes: List<FreqNode> = listOf(
        // Qualcomm Adreno
        FreqNode("/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq", FreqStyle.PLAIN),
        FreqNode("/sys/class/kgsl/kgsl-3d0/gpuclk", FreqStyle.PLAIN),
        FreqNode("/sys/class/kgsl/kgsl-3d0/clock_mhz", FreqStyle.PLAIN),
        // ARM Mali / generic devfreq (Pixel, Exynos, Dimensity with mali devfreq)
        FreqNode("/sys/class/devfreq/gpufreq/cur_freq", FreqStyle.PLAIN),
        FreqNode("/sys/class/devfreq/mtk-gpufreq/cur_freq", FreqStyle.PLAIN),
        FreqNode("/sys/class/devfreq/mtk-mali/cur_freq", FreqStyle.PLAIN),
        FreqNode("/sys/class/devfreq/13000000.mali/cur_freq", FreqStyle.PLAIN),
        FreqNode("/sys/class/devfreq/1c500000.mali/cur_freq", FreqStyle.PLAIN),
        FreqNode("/sys/class/devfreq/14ac0000.mali/cur_freq", FreqStyle.PLAIN),
        FreqNode("/sys/class/devfreq/28000000.mali/cur_freq", FreqStyle.PLAIN),
        FreqNode("/sys/devices/platform/13000000.mali/devfreq/13000000.mali/cur_freq", FreqStyle.PLAIN),
        FreqNode("/sys/devices/platform/soc/13000000.mali/devfreq/13000000.mali/cur_freq", FreqStyle.PLAIN),
        FreqNode("/sys/class/misc/mali0/device/devfreq/mali0/cur_freq", FreqStyle.PLAIN),
        // Samsung / Exynos
        FreqNode("/sys/kernel/gpu/gpu_clock", FreqStyle.PLAIN),
        // MediaTek GED (note: "freqency" is the real, misspelled kernel name)
        FreqNode("/sys/kernel/ged/hal/current_freqency", FreqStyle.BIGGEST),
        FreqNode("/proc/gpufreqv2/stack_signed_opp_table", FreqStyle.MTK_TABLE),
        FreqNode("/proc/gpufreqv2/gpufreq_status", FreqStyle.DUMP),
        FreqNode("/proc/gpufreq/gpufreq_opp_freq", FreqStyle.DUMP),
        FreqNode("/proc/gpufreq/gpufreq_var_dump", FreqStyle.DUMP),
        FreqNode("/proc/gpufreqv2/fix_custom_freq_volt", FreqStyle.DUMP),
        FreqNode("/proc/gpufreqv2/fix_target_opp_index", FreqStyle.DUMP),
        FreqNode("/proc/gpufreq/gpufreq_fixed_freq_volt", FreqStyle.DUMP)
    )

    val loadNodes: List<String> = listOf(
        "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
        "/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load",
        "/sys/class/kgsl/kgsl-3d0/gpuload",
        "/sys/class/kgsl/kgsl-3d0/gpubusy",
        "/sys/class/devfreq/13000000.mali/load",
        "/sys/module/ged/parameters/gpu_loading",
        "/sys/kernel/ged/hal/gpu_utilization",
        "/sys/kernel/debug/ged/hal/gpu_utilization", // debugfs: readable with root only, harmless otherwise
        "/sys/kernel/gpu/gpu_busy",
        "/sys/devices/platform/13000000.mali/utilization",
        "/sys/devices/platform/soc/13000000.mali/utilization",
        "/sys/devices/platform/1c500000.mali/utilization",
        "/sys/devices/platform/28000000.mali/utilization",
        "/sys/class/misc/mali0/device/utilization",
        "/proc/mali/utilization"
    )

    val maxFreqNodes: List<FreqNode> = listOf(
        FreqNode("/sys/class/kgsl/kgsl-3d0/devfreq/max_freq", FreqStyle.PLAIN),
        FreqNode("/sys/class/kgsl/kgsl-3d0/max_clock_mhz", FreqStyle.PLAIN),
        FreqNode("/sys/class/kgsl/kgsl-3d0/max_gpuclk", FreqStyle.PLAIN),
        FreqNode("/sys/class/devfreq/gpufreq/max_freq", FreqStyle.PLAIN),
        FreqNode("/sys/class/devfreq/mtk-gpufreq/max_freq", FreqStyle.PLAIN),
        FreqNode("/sys/class/devfreq/13000000.mali/max_freq", FreqStyle.PLAIN),
        FreqNode("/sys/devices/platform/soc/13000000.mali/devfreq/13000000.mali/max_freq", FreqStyle.PLAIN),
        FreqNode("/sys/kernel/gpu/gpu_max_clock", FreqStyle.PLAIN)
    )

    private val gpuNameTokens = listOf("gpu", "mali", "kgsl", "3d", "g3d", "sgpu", "adreno", "gpufreq")

    fun looksLikeGpuDir(name: String): Boolean {
        val l = name.lowercase()
        return gpuNameTokens.any { l.contains(it) }
    }

    /** Sibling nodes worth probing next to a discovered devfreq directory. */
    fun loadNodesIn(dir: String): List<String> = listOf(
        "$dir/load", "$dir/utilization", "$dir/gpu_load", "$dir/gpu_busy_percentage",
        "$dir/mali_ondemand/utilisation"
    )

    // ---- numbers -----------------------------------------------------------------------------

    private val intToken = Regex("""-?\d+""")

    internal fun tokens(raw: String): List<Long> =
        intToken.findAll(raw).mapNotNull { it.value.toLongOrNull() }.toList()

    /** Hz / kHz / MHz -> MHz by magnitude. null = not a believable GPU clock. */
    fun toMhz(n: Long): Int? {
        if (n < 0L) return null
        if (n == 0L) return 0
        val mhz = when {
            n < 10_000L -> n                       // already MHz
            else -> {
                val k = n / 1000L                  // kHz -> MHz
                if (k > 5000L) n / 1_000_000L else k // otherwise it was Hz
            }
        }
        return if (mhz in 1L..5000L) mhz.toInt() else null
    }

    /** Frequency in MHz, 0 = idle/off, null = unreadable or not parsable. */
    fun parseFreqMhz(raw: String?, style: FreqStyle): Int? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        return when (style) {
            FreqStyle.PLAIN -> tokens(text.lineSequence().first()).firstOrNull()?.let(::toMhz)
            FreqStyle.BIGGEST -> tokens(text).maxOrNull()?.let(::toMhz)
            FreqStyle.MTK_TABLE -> {
                val active = Regex("""\[\s*\d+\s*\*\s*]\s*freq\s*[:=]\s*(\d+)""").find(text)
                val m = active?.groupValues?.get(1)?.toLongOrNull()?.let(::toMhz)
                if (m != null && m > 0) m else {
                    Regex("""freq\s*[:=]\s*(\d+)""").findAll(text)
                        .mapNotNull { it.groupValues.getOrNull(1)?.toLongOrNull()?.let(::toMhz) }
                        .firstOrNull { it > 0 }
                }
            }
            FreqStyle.DUMP -> {
                val cur = Regex("""(?i)cur\w*freq\w*\s*[=:]\s*(\d+)""").find(text)
                    ?: Regex("""(?i)\bfreq(?:uency)?\s*[=:]\s*(\d+)""").find(text)
                val m = cur?.groupValues?.get(1)?.toLongOrNull()?.let(::toMhz)
                if (m != null && m > 0) m else {
                    Regex("""\d{5,}""").find(text)?.value?.toLongOrNull()?.let(::toMhz)
                }
            }
        }
    }

    /** `available_frequencies` / a table -> the highest frequency in MHz. */
    fun parseMaxOfList(raw: String?): Int? {
        val best = raw?.let(::tokens)?.maxOrNull() ?: return null
        return toMhz(best)?.takeIf { it > 0 }
    }

    /** GPU busy percentage 0..100, null = unreadable. */
    fun parseLoadPct(raw: String?, path: String): Int? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val t = tokens(text)
        if (t.isEmpty()) return null
        // kgsl `gpubusy`: "<busy> <total>" of the last window.
        if (path.endsWith("/gpubusy") && t.size >= 2) {
            val total = t[1]
            if (total <= 0L) return null
            return ((t[0] * 100L) / total).toInt().coerceIn(0, 100)
        }
        val v = t[0]
        return when {
            v < 0L -> null
            v <= 100L -> v.toInt()
            v <= 255L -> ((v * 100L) / 255L).toInt() // 8-bit utilisation (some Mali kbase builds)
            v <= 1000L -> (v / 10L).toInt()          // per-mille
            else -> 100
        }
    }
}
