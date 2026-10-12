/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.core.kernel

import android.content.Context
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Capability-selected vendor abstraction. Probing is read-only. Mutations are
 * strictly typed and delegated to KernelControlEngine after capability checks.
 */
object KernelBackendEngine {
    data class CapabilitySupport(
        val id: String,
        val supported: Boolean,
        val evidence: List<String>,
        val readOnly: Boolean = true
    )

    data class Probe(
        val backendId: String,
        val supported: Boolean,
        val evidence: List<String> = emptyList(),
        val controls: List<CapabilitySupport> = emptyList(),
        val latencyMs: Long = 0L,
        val error: String? = null
    )

    sealed interface ControlRequest {
        data class CpuGovernor(val policy: String, val governor: String) : ControlRequest
        data class CpuRange(val policy: String, val minKHz: Long, val maxKHz: Long) : ControlRequest
        data class CpuOnline(val coreId: Int, val online: Boolean) : ControlRequest
        data class Uclamp(val min: Int? = null, val max: Int? = null) : ControlRequest
        data class GpuGovernor(val devfreqName: String, val governor: String) : ControlRequest
        data class GpuRange(val devfreqName: String, val minHz: Long? = null, val maxHz: Long? = null) : ControlRequest
        data class QualcommGpuClockRange(val minHz: Long? = null, val maxHz: Long? = null) : ControlRequest
        data class QualcommGpuPowerLevelRange(val minLevel: Int? = null, val maxLevel: Int? = null) : ControlRequest
        data class IoScheduler(val blockDevice: String, val scheduler: String) : ControlRequest
        data class NodeWrite(val path: String, val value: String, val verify: Boolean = true) : ControlRequest
        data class ProcessAffinity(val pid: Int, val cpuList: String) : ControlRequest
        data class CgroupPlacement(val pid: Int, val cgroupPath: String) : ControlRequest
        data class ChargingLimit(val path: String, val value: Int) : ControlRequest
        data class ChargingToggle(val path: String, val enabled: Boolean) : ControlRequest
        data class FastCharge(val path: String, val enabled: Boolean) : ControlRequest
        data class BypassCharging(val path: String, val enabled: Boolean) : ControlRequest
    }

    data class DispatchResult(
        val backendId: String,
        val success: Boolean,
        val operation: String,
        val error: String? = null
    )

    suspend fun probe(context: Context, snapshot: KernelCapabilityEngine.Snapshot = KernelCapabilityEngine.current()): Probe = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        val backend = KernelBackendRegistry.select(snapshot)
        try {
            val controls = mutableListOf<CapabilitySupport>()
            val evidence = mutableListOf<String>()
            val ok = when (backend.id) {
                "qualcomm" -> {
                    val kgsl = snapshot.capability("qcom_kgsl")?.matchedPath
                    val bus = snapshot.capability("qcom_bus_dcvs")?.matchedPath
                    val thermal = snapshot.capability("qcom_msm_thermal")?.matchedPath
                    val gpuPower = snapshot.capability("qcom_gpu_power_levels")?.matchedPath
                    val gpuClock = snapshot.capability("qcom_gpu_clock_range")?.matchedPath
                    listOf(kgsl, bus, thermal, gpuPower, gpuClock).filterNotNull().forEach(evidence::add)
                    controls += CapabilitySupport("gpu_kgsl", kgsl != null, listOfNotNull(kgsl))
                    controls += CapabilitySupport("gpu_power_levels", gpuPower != null, listOfNotNull(gpuPower))
                    controls += CapabilitySupport("gpu_clock_range", gpuClock != null, listOfNotNull(gpuClock))
                    controls += CapabilitySupport("bus_dcvs", bus != null, listOfNotNull(bus))
                    controls += CapabilitySupport("thermal_qcom", thermal != null, listOfNotNull(thermal))
                    kgsl != null
                }
                "mediatek", "mediatek_legacy" -> {
                    val gpu = snapshot.capabilities.firstOrNull { it.id == "mtk_gpufreq_v2" || it.id == "mtk_gpufreq_legacy" }?.matchedPath
                    val ppm = snapshot.capability("mtk_ppm")?.matchedPath
                    val dvfs = snapshot.capability("mtk_dvfsrc")?.matchedPath
                    listOf(gpu, ppm, dvfs).filterNotNull().forEach(evidence::add)
                    controls += CapabilitySupport("gpu_gpufreq", gpu != null, listOfNotNull(gpu))
                    controls += CapabilitySupport("ppm", ppm != null, listOfNotNull(ppm))
                    controls += CapabilitySupport("dvfsrc", dvfs != null, listOfNotNull(dvfs))
                    gpu != null
                }
                "samsung-exynos" -> {
                    val thermal = snapshot.capability("exynos_thermal")?.matchedPath
                    controls += CapabilitySupport("thermal_exynos", thermal != null, listOfNotNull(thermal))
                    thermal != null
                }
                else -> {
                    val devfreq = snapshot.capability("gpu_devfreq")?.matchedPath
                    controls += CapabilitySupport("gpu_devfreq", devfreq != null, listOfNotNull(devfreq))
                    val uptime = KernelRootRuntime.readNode("/proc/uptime")
                    if (uptime.isNotBlank()) evidence += "/proc/uptime"
                    uptime.isNotBlank() || snapshot.supported().isNotEmpty()
                }
            }
            Probe(backend.id, ok, evidence.distinct(), controls, (System.nanoTime() - started) / 1_000_000L)
        } catch (t: Throwable) {
            Probe(backend.id, false, latencyMs = (System.nanoTime() - started) / 1_000_000L, error = t.message ?: t.javaClass.simpleName)
        }
    }

    /**
     * Dispatch a typed mutation. No arbitrary shell is accepted here.
     * Generic CPU/scheduler controls are capability checked; vendor-specific
     * probes must exist before a request is dispatched.
     */
    suspend fun apply(context: Context, request: ControlRequest): DispatchResult = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        KernelRootRuntime.initialize(app)
        val snapshot = KernelCapabilityEngine.current().takeIf { it.generation > 0L }
            ?: KernelCapabilityEngine.scan(app)
        val backend = KernelBackendRegistry.select(snapshot)
        fun result(operation: String, success: Boolean, error: String? = null) = DispatchResult(backend.id, success, operation, error)

        try {
            when (request) {
                is ControlRequest.CpuGovernor -> {
                    if (!snapshot.has("cpu_freq")) return@withContext result("cpu_governor", false, "CPU frequency capability unavailable")
                    val r = KernelControlEngine.setCpuGovernor(request.policy, request.governor)
                    result("cpu_governor", r.success, r.error)
                }
                is ControlRequest.CpuRange -> {
                    if (!snapshot.has("cpu_freq")) return@withContext result("cpu_range", false, "CPU frequency capability unavailable")
                    val rs = KernelControlEngine.setCpuMinMax(request.policy, request.minKHz, request.maxKHz)
                    result("cpu_range", rs.isNotEmpty() && rs.all { it.success }, rs.firstOrNull { !it.success }?.error)
                }
                is ControlRequest.CpuOnline -> {
                    if (!snapshot.has("cpu_hotplug")) return@withContext result("cpu_online", false, "CPU hotplug capability unavailable")
                    val r = KernelControlEngine.setCpuCoreOnline(request.coreId, request.online)
                    result("cpu_online", r.success, r.error)
                }
                is ControlRequest.Uclamp -> {
                    if (!snapshot.has("uclamp")) return@withContext result("uclamp", false, "uclamp capability unavailable")
                    val rs = KernelControlEngine.setUclamp(request.min, request.max)
                    result("uclamp", rs.isNotEmpty() && rs.all { it.success }, rs.firstOrNull { !it.success }?.error)
                }
                is ControlRequest.GpuGovernor -> {
                    if (!snapshot.has("gpu_governor") && !snapshot.has("qcom_kgsl")) return@withContext result("gpu_governor", false, "GPU governor capability unavailable")
                    val r = KernelControlEngine.setGpuGovernor(request.devfreqName, request.governor)
                    result("gpu_governor", r.success, r.error)
                }
                is ControlRequest.GpuRange -> {
                    if (!snapshot.has("gpu_min_max") && !snapshot.has("qcom_kgsl")) return@withContext result("gpu_range", false, "GPU frequency capability unavailable")
                    val rs = KernelControlEngine.setGpuMinMax(request.devfreqName, request.minHz, request.maxHz)
                    result("gpu_range", rs.isNotEmpty() && rs.all { it.success }, rs.firstOrNull { !it.success }?.error)
                }
                is ControlRequest.QualcommGpuClockRange -> {
                    if (backend.id != "qualcomm" || !snapshot.has("qcom_gpu_clock_range")) return@withContext result("qcom_gpu_clock", false, "Qualcomm GPU clock-range capability unavailable")
                    val tx = KernelControlEngine.setQualcommGpuClockRange(request.minHz, request.maxHz)
                    result("qcom_gpu_clock", tx.success, tx.error)
                }
                is ControlRequest.QualcommGpuPowerLevelRange -> {
                    if (backend.id != "qualcomm" || !snapshot.has("qcom_gpu_power_levels")) return@withContext result("qcom_gpu_power_level", false, "Qualcomm GPU power-level capability unavailable")
                    val tx = KernelControlEngine.setQualcommGpuPowerLevelRange(request.minLevel, request.maxLevel)
                    result("qcom_gpu_power_level", tx.success, tx.error)
                }
                is ControlRequest.IoScheduler -> {
                    if (!snapshot.has("io_scheduler") && !snapshot.has("block_queue")) return@withContext result("io_scheduler", false, "I/O scheduler capability unavailable")
                    val r = KernelControlEngine.setIoScheduler(request.blockDevice, request.scheduler)
                    result("io_scheduler", r.success, r.error)
                }
                is ControlRequest.NodeWrite -> {
                    val r = KernelControlEngine.setKernelNode(request.path, request.value, request.verify)
                    result("node_write", r.success, r.error)
                }
                is ControlRequest.ProcessAffinity -> {
                    val r = KernelControlEngine.setProcessAffinity(request.pid, request.cpuList)
                    result("process_affinity", r.success, r.error)
                }
                is ControlRequest.CgroupPlacement -> {
                    if (!snapshot.has("cgroup_v2")) return@withContext result("cgroup", false, "cgroup v2 capability unavailable")
                    val r = KernelControlEngine.moveProcessToCgroup(request.pid, request.cgroupPath)
                    result("cgroup", r.success, r.error)
                }
                is ControlRequest.ChargingLimit -> {
                    if (!snapshot.has("battery_charge_control")) return@withContext result("charging_limit", false, "Charging control capability unavailable")
                    val r = KernelControlEngine.setKernelNode(request.path, request.value.coerceIn(20, 100).toString(), true)
                    result("charging_limit", r.success, r.error)
                }
                is ControlRequest.ChargingToggle -> {
                    if (!snapshot.has("battery_charge_control")) return@withContext result("charging_toggle", false, "Charging control capability unavailable")
                    val r = KernelControlEngine.setKernelNode(request.path, if (request.enabled) "0" else "1", true)
                    result("charging_toggle", r.success, r.error)
                }
                is ControlRequest.FastCharge -> {
                    if (!snapshot.has("battery_charge_control")) return@withContext result("fast_charge", false, "Charging control capability unavailable")
                    val r = KernelControlEngine.setKernelNode(request.path, if (request.enabled) "1" else "0", true)
                    result("fast_charge", r.success, r.error)
                }
                is ControlRequest.BypassCharging -> {
                    if (!snapshot.has("battery_charge_control")) return@withContext result("bypass_charging", false, "Charging control capability unavailable")
                    val r = KernelControlEngine.setKernelNode(request.path, if (request.enabled) "1" else "0", true)
                    result("bypass_charging", r.success, r.error)
                }
            }
        } catch (t: Throwable) {
            result(request::class.simpleName ?: "control", false, t.message ?: t.javaClass.simpleName)
        }
    }

    fun applyBlocking(context: Context, request: ControlRequest): DispatchResult {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return DispatchResult("blocked", false, request::class.simpleName ?: "control", "Blocking backend API called from main thread; use the suspend API")
        }
        return kotlinx.coroutines.runBlocking(Dispatchers.IO) { apply(context, request) }
    }
}
