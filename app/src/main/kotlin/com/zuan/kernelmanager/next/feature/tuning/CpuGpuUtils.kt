/*
 * Original code from: Rem01Gaming (origami_kernel_manager) and helloklf (vtools)
 * Modified and integrated by: Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.tuning

import com.zuan.kernelmanager.next.library.device.gpu.GenericGpuUtils
import com.zuan.kernelmanager.next.library.device.gpu.MtkUtils
import com.zuan.kernelmanager.next.core.util.Utils
import com.zuan.kernelmanager.next.core.kernel.KernelControlEngine
import com.zuan.kernelmanager.next.core.kernel.KernelBackendEngine
import com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime
import com.zuan.kernelmanager.next.core.kernel.KernelRuntimeContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class GovTunable(
    val name: String,
    val value: String,
    val path: String
)

// Data Class untuk status per-Core
data class CoreStatus(
    val id: Int,
    val isOnline: Boolean
)

// Data Class untuk Cpuset
data class CpusetData(
    val name: String,
    val value: String, // e.g. "0-3"
    val path: String,
    val key: String // e.g. "top-app"
)

object CpuGpuUtils {

    // --- Constants ---
    const val GPU_TEMP = "/sys/class/kgsl/kgsl-3d0/temp"
    const val CURRENT_FREQ_GPU = "/sys/class/kgsl/kgsl-3d0/gpuclk"
    const val CPU_BASE_PATH = "/sys/devices/system/cpu/cpufreq"
    const val CPU_TEMP = "/sys/class/thermal/thermal_zone0/temp"
    
    // Path Baru
    const val CPU_ONLINE_PATH = "/sys/devices/system/cpu/cpu%d/online"
    const val CPUSET_BASE = "/dev/cpuset" 

    // Boost Paths
    const val CPU_INPUT_BOOST_MS = "/sys/devices/system/cpu/cpu_boost/input_boost_ms"
    const val CPU_SCHED_BOOST_ON_INPUT = "/sys/devices/system/cpu/cpu_boost/sched_boost_on_input"

    private var sPrevTotal: Long = -1
    private var sPrevIdle: Long = -1
    private val numCores by lazy { Runtime.getRuntime().availableProcessors() }

    enum class GpuType {
        ADRENO, MEDIATEK_V2, MEDIATEK_LEGACY, GENERIC_DEVFREQ, UNKNOWN
    }

    // --- SoC Info ---
    suspend fun getCpuInfo(): String = withContext(Dispatchers.IO) {
        try {
            val manufacturer = Utils.getSystemProperty("ro.soc.manufacturer").trim()
            val model = Utils.getSystemProperty("ro.soc.model").trim()
            if (manufacturer.isNotEmpty() && model.isNotEmpty()) "$manufacturer $model"
            else Utils.getSystemProperty("ro.board.platform").takeIf { it.isNotEmpty() } ?: "Unknown SoC"
        } catch (e: Exception) { "Unknown Processor" }
    }

    // --- CPU Logic ---
    suspend fun getCpuPolicies(): List<String> = withContext(Dispatchers.IO) {
        try {
            KernelRootRuntime.listDirectories(CPU_BASE_PATH)
                .filter { it.startsWith("policy") }
                .sortedBy { it.removePrefix("policy").toIntOrNull() ?: 999 }
                .map { "$CPU_BASE_PATH/$it" }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun readFreq(policyPath: String, file: String): String = withContext(Dispatchers.IO) {
        try {
            val raw = KernelRootRuntime.readNode("$policyPath/$file")
            raw.toLongOrNull()?.let { (it / 1000L).toString() }.orEmpty()
        } catch (e: Exception) { "" }
    }

    suspend fun writeFreq(policyPath: String, file: String, frequency: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val freqInt = frequency.toIntOrNull() ?: return@withContext false
            val freqInKHz = freqInt * 1000L
            if (freqInKHz <= 0L) return@withContext false
            val policyName = policyPath.substringAfterLast('/').takeIf { it.matches(Regex("policy[0-9]+")) }
            val context = KernelRuntimeContext.context
            if (policyName == null || context == null) return@withContext false
            when (file) {
                "scaling_min_freq" -> KernelBackendEngine.apply(
                    context,
                    KernelBackendEngine.ControlRequest.CpuRange(
                        policyName,
                        freqInKHz,
                        KernelRootRuntime.readNode("$policyPath/scaling_max_freq").toLongOrNull() ?: freqInKHz
                    )
                ).success
                "scaling_max_freq" -> KernelBackendEngine.apply(
                    context,
                    KernelBackendEngine.ControlRequest.CpuRange(
                        policyName,
                        KernelRootRuntime.readNode("$policyPath/scaling_min_freq").toLongOrNull() ?: freqInKHz,
                        freqInKHz
                    )
                ).success
                else -> false
            }
        } catch (e: Exception) {
            android.util.Log.e("ZKM", "Unhandled operation failure", e)
            false
        }
    }

    suspend fun writeGov(policyPath: String, governor: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val policyName = policyPath.substringAfterLast('/').takeIf { it.matches(Regex("policy[0-9]+")) }
            val context = KernelRuntimeContext.context
            if (policyName == null || context == null) return@withContext false
            KernelBackendEngine.apply(context, KernelBackendEngine.ControlRequest.CpuGovernor(policyName, governor)).success
        } catch (e: Exception) {
            android.util.Log.e("ZKM", "Unhandled operation failure", e)
            false
        }
    }

    suspend fun readAvailableFreq(policyPath: String): List<String> = withContext(Dispatchers.IO) {
        val freqSet = mutableSetOf<Int>()
        try {
            val availRaw = KernelRootRuntime.readNode("$policyPath/scaling_available_frequencies")
            if (availRaw.isNotBlank()) {
                availRaw.trim().split("\\s+".toRegex())
                    .mapNotNull { it.toIntOrNull() }
                    .forEach { freqSet.add(it) }
            }
            KernelRootRuntime.readNode("$policyPath/cpuinfo_max_freq").trim().toIntOrNull()?.let { freqSet.add(it) }
            KernelRootRuntime.readNode("$policyPath/cpuinfo_min_freq").trim().toIntOrNull()?.let { freqSet.add(it) }
            freqSet.sorted().map { (it / 1000).toString() }
        } catch (e: Exception) { 
            emptyList() 
        }
    }

    suspend fun readAvailableGov(policyPath: String): List<String> = withContext(Dispatchers.IO) {
        try {
            KernelRootRuntime.readNode("$policyPath/scaling_available_governors").trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        } catch (e: Exception) { emptyList() }
    }
    
    suspend fun readGovernor(policyPath: String): String = withContext(Dispatchers.IO) {
         try {
            KernelRootRuntime.readNode("$policyPath/scaling_governor").ifBlank { "N/A" }
        } catch (e: Exception) { "N/A" }
    }

    // --- CPU CORE CONTROL [NEW] ---

    suspend fun getAffectedCpus(policyPath: String): List<Int> = withContext(Dispatchers.IO) {
        try {
            val related = KernelRootRuntime.readNode("$policyPath/related_cpus")
            val affected = if (related.isBlank()) KernelRootRuntime.readNode("$policyPath/affected_cpus") else related
            if (affected.isNotBlank()) {
                affected.trim().split("\\s+".toRegex()).mapNotNull { it.toIntOrNull() }
            } else {
                val id = policyPath.substringAfterLast("policy").toIntOrNull() ?: 0
                listOf(id)
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getCoreStatus(coreId: Int): Boolean = withContext(Dispatchers.IO) {
        if (coreId == 0) return@withContext true // CPU0 usually always on
        try {
            val raw = KernelRootRuntime.readNode(String.format(CPU_ONLINE_PATH, coreId))
            raw.isBlank() || raw.trim() == "1"
        } catch (e: Exception) { true }
    }

    suspend fun setCoreOnline(coreId: Int, online: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (coreId == 0) return@withContext false
        val context = KernelRuntimeContext.context ?: return@withContext false
        KernelBackendEngine.apply(context, KernelBackendEngine.ControlRequest.CpuOnline(coreId, online)).success
    }

    suspend fun getTotalOnlineCores(): Int = withContext(Dispatchers.IO) {
        var count = 0
        for (i in 0 until numCores) {
            if (getCoreStatus(i)) count++
        }
        count
    }

    fun getMaxCoreCount(): Int = numCores

    // --- CPUSET LOGIC [NEW] ---
    
    suspend fun getCpusetInfo(): List<CpusetData> = withContext(Dispatchers.IO) {
        val groups = listOf("top-app", "foreground", "background", "system-background")
        val result = mutableListOf<CpusetData>()
        
        groups.forEach { group ->
            val path = "$CPUSET_BASE/$group/cpus"
            val file = File(path)
            if (KernelRootRuntime.nodeExistsBlocking(path)) {
                try {
                    val value = KernelRootRuntime.readNodeBlocking(path).trim()
                    val uiName = when(group) {
                        "top-app" -> "Top App"
                        "foreground" -> "Foreground App"
                        "background" -> "User's bg app"
                        "system-background" -> "System bg app"
                        else -> group.split("-").joinToString(" ") { it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() } }
                    }
                    result.add(CpusetData(uiName, value, path, group))
                } catch (e: Exception) { /* Ignore */ }
            }
        }
        result
    }

    suspend fun applyCpuset(path: String, selectedCores: List<Int>): Boolean = withContext(Dispatchers.IO) {
        if (selectedCores.isEmpty()) return@withContext false
        val sorted = selectedCores.sorted()
        // Format: "0,1,2,3" (Comma separated works on most Android kernels for cpuset)
        val value = sorted.joinToString(",") 
        val context = KernelRuntimeContext.context ?: return@withContext false
        KernelBackendEngine.apply(context, KernelBackendEngine.ControlRequest.NodeWrite(path, value)).success
    }
    
    // Helper: Parse "0-3,6-7" to List [0,1,2,3,6,7]
    fun parseCpusetCores(cpusetString: String): List<Int> {
        val cores = mutableListOf<Int>()
        try {
            val parts = cpusetString.split(",")
            parts.forEach { part ->
                if (part.contains("-")) {
                    val range = part.split("-")
                    val start = range[0].toInt()
                    val end = range[1].toInt()
                    for (i in start..end) cores.add(i)
                } else {
                    part.trim().toIntOrNull()?.let { cores.add(it) }
                }
            }
        } catch (e: Exception) { return emptyList() }
        return cores.distinct().sorted()
    }

    // --- GPU Logic ---
    suspend fun getGpuType(): GpuType = withContext(Dispatchers.IO) {
        when {
            KernelRootRuntime.nodeExistsBlocking("/sys/class/kgsl/kgsl-3d0") -> GpuType.ADRENO
            MtkUtils.isMtkV2() -> GpuType.MEDIATEK_V2
            MtkUtils.isMtkLegacy() -> GpuType.MEDIATEK_LEGACY
            GenericGpuUtils.getGpuPath() != null -> GpuType.GENERIC_DEVFREQ
            else -> GpuType.UNKNOWN
        }
    }

    suspend fun readFreqGPU(filePath: String): String = withContext(Dispatchers.IO) {
        try {
            val raw = KernelRootRuntime.readNode(filePath)
            raw.toLongOrNull()?.let { value -> if (value > 1000000L) (value / 1000000L).toString() else if (value > 1000L) (value / 1000L).toString() else value.toString() } ?: ""
        } catch (e: Exception) { "" }
    }

    suspend fun getGpuUsage(): String = withContext(Dispatchers.IO) {
        val kgslUsage = KernelRootRuntime.readNode("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage")
        if (kgslUsage.isNotEmpty()) return@withContext kgslUsage.replace("%", "").trim()
        
        val genericPath = GenericGpuUtils.getGpuPath()
        if (genericPath != null) {
            val load = KernelRootRuntime.readNode("$genericPath/load")
            if (load.isNotBlank()) return@withContext load.split("@")[0].trim()
        }
        "0"
    }

    // --- Governor Tunables ---
    suspend fun getGovernorTunables(policyPath: String, governor: String): List<GovTunable> = withContext(Dispatchers.IO) {
        try {
            val govDir = File("$policyPath/$governor")
            if (!govDir.exists() || !govDir.isDirectory) return@withContext emptyList<GovTunable>()

            govDir.listFiles()
                ?.filter { it.isFile && it.canRead() }
                ?.sortedBy { it.name }
                ?.map { file ->
                    val value = KernelRootRuntime.readNodeBlocking(file.absolutePath).trim().ifBlank { "N/A" }
                    GovTunable(file.name, value, file.absolutePath)
                } ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e("ZKM", "Unhandled operation failure", e)
            emptyList()
        }
    }

    suspend fun writeTunable(path: String, value: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val context = KernelRuntimeContext.context ?: return@withContext false
            KernelBackendEngine.apply(
                context,
                KernelBackendEngine.ControlRequest.NodeWrite(path, value, verify = true)
            ).success
        } catch (e: Exception) {
            android.util.Log.e("ZKM", "Unhandled operation failure", e)
            false
        }
    }
}
