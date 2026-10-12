/*
 * Original code from: Rem01Gaming (origami_kernel_manager)
 * Modified and integrated by: Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.library.device.tuning

import com.zuan.kernelmanager.next.core.kernel.KernelBackendEngine
import com.zuan.kernelmanager.next.core.kernel.KernelControlEngine
import com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime

object MemoryUtils {
    // --- ZRAM PATHS ---
    const val ZRAM_SIZE = "/sys/block/zram0/disksize"
    const val ZRAM_RESET = "/sys/block/zram0/reset"
    const val ZRAM_DEV = "/dev/block/zram0"
    const val ZRAM_COMP_ALGORITHM = "/sys/block/zram0/comp_algorithm"

    // --- VM PATHS ---
    const val SWAPPINESS = "/proc/sys/vm/swappiness"
    const val VFS_CACHE_PRESSURE = "/proc/sys/vm/vfs_cache_pressure"
    const val MIN_FREE_KBYTES = "/proc/sys/vm/min_free_kbytes"
    const val EXTRA_FREE_KBYTES = "/proc/sys/vm/extra_free_kbytes"
    const val DIRTY_RATIO = "/proc/sys/vm/dirty_ratio"
    const val DIRTY_BACKGROUND_RATIO = "/proc/sys/vm/dirty_background_ratio"
    const val LAPTOP_MODE = "/proc/sys/vm/laptop_mode"

    // --- ZRAM FUNCTIONS ---
    fun getZramSizeBytes(): Long {
        return KernelRootRuntime.readNodeBlocking(ZRAM_SIZE).trim().toLongOrNull() ?: 0L
    }

    // Mengembalikan Pair: (Algoritma Aktif, List Semua Algoritma)
    fun getZramAlgoInfo(): Pair<String, List<String>> {
        val raw = KernelRootRuntime.readNodeBlocking(ZRAM_COMP_ALGORITHM).trim()
        if (raw.isEmpty()) return Pair("Unknown", emptyList())

        // Contoh raw: "lzo [lz4] zstd"
        val allAlgos = raw.replace("[", "").replace("]", "").split("\\s+".toRegex())
        val match = "\\[([^\\]]+)\\]".toRegex().find(raw)
        val active = match?.groupValues?.get(1) ?: allAlgos.firstOrNull() ?: "Unknown"

        return Pair(active, allAlgos)
    }

    fun swapoff() {
        KernelRootRuntime.executeBlocking("swapoff $ZRAM_DEV 2>/dev/null || true")
    }

    fun swapon() {
        KernelRootRuntime.executeBlocking("swapon $ZRAM_DEV 2>/dev/null")
    }

    fun mkswap() {
        KernelRootRuntime.executeBlocking("mkswap $ZRAM_DEV 2>/dev/null")
    }

    fun resetZram() {
        KernelRootRuntime.executeBlocking("printf '%s' '1' > '$ZRAM_RESET'")
    }

    fun setZramSize(bytes: Long) {
        if (bytes > 0) KernelControlEngine.setKernelNodeBlocking(ZRAM_SIZE, bytes.toString(), verify = false)
    }

    fun setZramCompAlgorithm(algo: String) {
        if (algo.matches(Regex("[A-Za-z0-9._-]+"))) KernelControlEngine.setKernelNodeBlocking(ZRAM_COMP_ALGORITHM, algo, verify = false)
    }

    // --- I/O SCHEDULER FUNCTIONS ---

    // [FIX] Menggunakan Shell "ls" karena File.listFiles() sering gagal di Android modern
    fun getBlockDevices(): List<String> {
        val output = KernelRootRuntime.listDirectoriesBlocking("/sys/block")
        return output.filter { name ->
            // HANYA tampilkan storage fisik (sd* atau mmcblk*)
            // dan sembunyikan dm-*, loop*, ram*, zram*
            (name.startsWith("sd") || name.startsWith("mmcblk")) &&
                    !name.startsWith("loop") && !name.startsWith("zram")
        }
    }

    // Mengembalikan Pair: (Scheduler Aktif, List Scheduler Tersedia)
    fun getIOSchedulerInfo(deviceName: String): Pair<String, List<String>> {
        val path = "/sys/block/$deviceName/queue/scheduler"
        val raw = KernelRootRuntime.readNodeBlocking(path).trim()
        if (raw.isEmpty()) return Pair("none", emptyList())

        // Contoh: "none [mq-deadline] kyber"
        val allScheds = raw.replace("[", "").replace("]", "").split("\\s+".toRegex())
        val match = "\\[([^\\]]+)\\]".toRegex().find(raw)
        val active = match?.groupValues?.get(1) ?: "none"

        return Pair(active, allScheds)
    }

    fun setIOScheduler(deviceName: String, scheduler: String): Boolean {
        val context = com.zuan.kernelmanager.next.core.kernel.KernelRuntimeContext.context ?: return false
        return KernelBackendEngine.applyBlocking(context, KernelBackendEngine.ControlRequest.IoScheduler(deviceName, scheduler)).success
    }

    // Generic function untuk membaca tunables di /sys/block/X/queue/Y
    fun getIOTunable(deviceName: String, tunable: String): String {
        return KernelRootRuntime.readNodeBlocking("/sys/block/$deviceName/queue/$tunable").trim()
    }

    fun setIOTunable(deviceName: String, tunable: String, value: String): Boolean {
        val safeDevice = deviceName.takeIf { it.matches(Regex("[A-Za-z0-9_.:-]+")) } ?: return false
        val safeTunable = tunable.takeIf { it.matches(Regex("[A-Za-z0-9_.:-]+")) } ?: return false
        val safeValue = value.replace('\u0000', ' ').trim().takeIf { it.isNotEmpty() && it.length <= 512 } ?: return false
        val context = com.zuan.kernelmanager.next.core.kernel.KernelRuntimeContext.context ?: return false
        return KernelBackendEngine.applyBlocking(context, KernelBackendEngine.ControlRequest.NodeWrite("/sys/block/$safeDevice/queue/$safeTunable", safeValue)).success
    }
}
