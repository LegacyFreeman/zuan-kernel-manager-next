/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.library.device.gpu

import com.zuan.kernelmanager.next.core.util.Utils
import com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime
import com.zuan.kernelmanager.next.core.kernel.KernelControlEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GenericGpuUtils {

    // Cache path agar tidak perlu searching ulang setiap kali request
    private var cachedGpuPath: String? = null

    // Path statis yang umum digunakan (fallback)
    private val POTENTIAL_PATHS = listOf(
        "/sys/class/devfreq/mtk-dvfsrc-devfreq",
        "/sys/class/devfreq/dfrgx", // Samsung/Mali Legacy
        "/sys/devices/platform/dfrgx/devfreq/dfrgx",
        "/sys/class/kgsl/kgsl-3d0/devfreq", // Snapdragon
        "/sys/kernel/gpu", // Generic Kernel interface
        "/sys/kernel/tegra_gpu" // Nvidia Tegra
    )

    /**
     * Mencari path devfreq GPU yang valid.
     * Updated: Support Exynos Xclipse (*.sgpu) dan pencarian lebih luas.
     */
    suspend fun getGpuPath(): String? = withContext(Dispatchers.IO) {
        cachedGpuPath?.let { return@withContext it }

        // Prefer capability-driven enumeration from the central root runtime.
        val candidates = KernelRootRuntime.listDirectories("/sys/class/devfreq")
            .map { "/sys/class/devfreq/$it" }
            .filter { path ->
                val name = path.substringAfterLast('/').lowercase()
                name.contains("gpu") || name.contains("kgsl") || name.contains("mali") ||
                    name.contains("3d") || name.contains("adreno") ||
                    KernelRootRuntime.nodeExistsBlocking("$path/available_frequencies") ||
                    KernelRootRuntime.nodeExistsBlocking("$path/trans_stat")
            }

        for (path in candidates) {
            if (isValidDevfreq(path)) {
                cachedGpuPath = path
                return@withContext path
            }
        }

        for (path in POTENTIAL_PATHS) {
            if (isValidDevfreq(path)) {
                cachedGpuPath = path
                return@withContext path
            }
        }

        val staticDirs = listOf(
            "/sys/class/devfreq",
            "/sys/devices/platform"
        )
        for (dir in staticDirs) {
            for (name in KernelRootRuntime.listDirectories(dir)) {
                val path = "$dir/$name"
                if (isValidDevfreq(path)) {
                    val n = name.lowercase()
                    if (n.contains("gpu") || n.contains("mali") || n.contains("sgpu") || n.contains("3d")) {
                        cachedGpuPath = path
                        return@withContext path
                    }
                }
            }
        }
        null
    }

    private fun isValidDevfreq(path: String): Boolean {
        if (!KernelRootRuntime.nodeExistsBlocking(path)) return false
        return KernelRootRuntime.nodeExistsBlocking("$path/available_frequencies") ||
            KernelRootRuntime.nodeExistsBlocking("$path/available_governors") ||
            KernelRootRuntime.nodeExistsBlocking("$path/trans_stat") ||
            KernelRootRuntime.nodeExistsBlocking("$path/governor")
    }

    // === COMMANDS ===

    suspend fun getMinFreq(path: String): String = Utils.readFile("$path/min_freq")
    suspend fun getMaxFreq(path: String): String = Utils.readFile("$path/max_freq")
    suspend fun getCurFreq(path: String): String = Utils.readFile("$path/cur_freq")
    suspend fun getGov(path: String): String = Utils.readFile("$path/governor")

    suspend fun getAvailableFreqs(path: String): List<String> = withContext(Dispatchers.IO) {
        val raw = KernelRootRuntime.readNode(path.trimEnd('/') + "/available_frequencies")
        if (raw.isBlank()) return@withContext emptyList()
        try {
            raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.map { 
                // Normalisasi: Devfreq biasanya Hz atau KHz. Kita ubah ke MHz agar seragam di UI.
                val freqVal = it.toLongOrNull() ?: 0L
                when {
                    freqVal > 1_000_000 -> (freqVal / 1_000_000).toString() // Hz -> MHz
                    freqVal > 1_000 -> (freqVal / 1_000).toString() // KHz -> MHz
                    else -> it // Anggap sudah MHz atau format lain
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getAvailableGovs(path: String): List<String> = withContext(Dispatchers.IO) {
        val raw = KernelRootRuntime.readNode(path.trimEnd('/') + "/available_governors")
        if (raw.isBlank()) emptyList() else raw.trim().split(Regex("\\s+"))
    }

    suspend fun setFreq(path: String, type: String, freqMHz: String) {
        val rawContent = KernelRootRuntime.readNode(path.trimEnd('/') + "/available_frequencies")
        val rawFreqs = rawContent.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val requested = freqMHz.toLongOrNull() ?: return
        val targetRaw = rawFreqs.find { raw ->
            val value = raw.toLongOrNull() ?: return@find false
            val mhz = when {
                value > 1_000_000L -> value / 1_000_000L
                value > 1_000L -> value / 1_000L
                else -> value
            }
            mhz == requested
        } ?: requested.toString()

        val standardNode = if (type.equals("min", true)) "min_freq" else "max_freq"
        val lockNode = if (type.equals("min", true)) "dvfs_min_lock" else "dvfs_max_lock"
        val lockPath = path.trimEnd('/') + "/$lockNode"
        if (KernelRootRuntime.nodeExists(lockPath)) {
            KernelControlEngine.setKernelNodeBlocking(lockPath, targetRaw, verify = true).success
        } else {
            // Reuse typed devfreq control for standard min/max nodes.
            val name = path.trimEnd('/').substringAfterLast('/')
            if (type.equals("min", true)) KernelControlEngine.setGpuMinMax(name, minHz = targetRaw.toLongOrNull())
            else KernelControlEngine.setGpuMinMax(name, maxHz = targetRaw.toLongOrNull())
                .also { _ -> if (!KernelRootRuntime.nodeExists(path.trimEnd('/') + "/$standardNode")) KernelControlEngine.setKernelNodeBlocking(path.trimEnd('/') + "/$standardNode", targetRaw, verify = true).success }
        }
    }

    suspend fun setGov(path: String, gov: String) {
        KernelControlEngine.setKernelNode(path.trimEnd('/') + "/governor", gov, verify = true)
    }
}
