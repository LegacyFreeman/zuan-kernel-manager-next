/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.core.kernel

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Evidence-driven kernel topology and capability discovery. */
object KernelCapabilityEngine {
    enum class Family { CPU, GPU, MEMORY, SCHEDULER, THERMAL, BATTERY, DISPLAY, STORAGE, NETWORK, VENDOR }
    enum class Vendor { GENERIC, QUALCOMM, MEDIATEK, SAMSUNG_EXYNOS, UNKNOWN }

    data class Capability(
        val id: String,
        val family: String,
        val supported: Boolean,
        val matchedPath: String? = null,
        val evidencePaths: List<String> = emptyList(),
        val writable: Boolean = false,
        val confidence: Int = if (supported) 100 else 0,
        val source: String = "sysfs/procfs"
    )

    data class Topology(
        val cpuPolicies: List<String> = emptyList(),
        val gpuDevfreq: List<String> = emptyList(),
        val thermalZones: List<String> = emptyList(),
        val blockDevices: List<String> = emptyList(),
        val generatedAt: Long = 0L
    )

    data class Snapshot(
        val generatedAt: Long,
        val generation: Long,
        val capabilities: List<Capability>,
        val vendor: Vendor = Vendor.UNKNOWN,
        val backendHints: List<String> = emptyList(),
        val scanLatencyMs: Long = 0L,
        val topology: Topology = Topology()
    ) {
        fun has(id: String): Boolean = capabilities.any { it.id == id && it.supported }
        fun supported(): List<Capability> = capabilities.filter { it.supported }
        fun family(family: String): List<Capability> = capabilities.filter { it.supported && it.family == family }
        fun capability(id: String): Capability? = capabilities.firstOrNull { it.id == id && it.supported }
    }

    private data class Candidate(
        val id: String,
        val family: Family,
        val paths: List<String>,
        val source: String = "sysfs/procfs"
    )

    private val mutex = Mutex()
    private var generation = 0L

    private val candidates = listOf(
        Candidate("cpu_freq", Family.CPU, listOf(
            "/sys/devices/system/cpu/cpufreq/policy0/scaling_cur_freq",
            "/sys/devices/system/cpu/cpufreq/policy0/scaling_governor"
        )),
        Candidate("cpu_available_governors", Family.CPU, listOf(
            "/sys/devices/system/cpu/cpufreq/policy0/scaling_available_governors"
        )),
        Candidate("cpu_available_frequencies", Family.CPU, listOf(
            "/sys/devices/system/cpu/cpufreq/policy0/scaling_available_frequencies"
        )),
        Candidate("cpu_boost", Family.CPU, listOf(
            "/sys/devices/system/cpu/cpufreq/boost",
            "/sys/module/cpu_boost/parameters/input_boost_enabled",
            "/sys/module/cpu_input_boost/parameters/input_boost_enabled"
        )),
        Candidate("cpu_hotplug", Family.CPU, listOf(
            "/sys/devices/system/cpu/cpu0/online"
        )),
        Candidate("uclamp", Family.SCHEDULER, listOf(
            "/proc/sys/kernel/sched_util_clamp_min",
            "/proc/sys/kernel/sched_util_clamp_max"
        )),
        Candidate("cgroup_v2", Family.SCHEDULER, listOf(
            "/sys/fs/cgroup/cgroup.controllers"
        )),
        Candidate("cgroup_legacy", Family.SCHEDULER, listOf(
            "/dev/cpuctl"
        )),
        Candidate("scheduler_tunables", Family.SCHEDULER, listOf(
            "/proc/sys/kernel/sched_latency_ns",
            "/proc/sys/kernel/sched_min_granularity_ns",
            "/proc/sys/kernel/sched_wakeup_granularity_ns"
        )),
        Candidate("io_scheduler", Family.STORAGE, listOf(
            "/sys/block/sda/queue/scheduler",
            "/sys/block/mmcblk0/queue/scheduler",
            "/sys/block/sdb/queue/scheduler",
            "/sys/block/dm-0/queue/scheduler"
        )),
        Candidate("vm_tuning", Family.MEMORY, listOf(
            "/proc/sys/vm/swappiness",
            "/proc/sys/vm/vfs_cache_pressure"
        )),
        Candidate("zram", Family.MEMORY, listOf(
            "/sys/block/zram0/disksize"
        )),
        Candidate("swap", Family.MEMORY, listOf(
            "/proc/swaps"
        )),
        Candidate("mglru", Family.MEMORY, listOf(
            "/sys/kernel/mm/lru_gen/enabled"
        )),
        Candidate("simple_lmk", Family.MEMORY, listOf(
            "/sys/module/simple_lmk/parameters/slmk_minfree",
            "/sys/module/simple_lmk/parameters/slmk_timeout"
        )),
        Candidate("psi", Family.MEMORY, listOf(
            "/proc/pressure/memory"
        )),
        Candidate("qcom_kgsl", Family.VENDOR, listOf(
            "/sys/class/kgsl/kgsl-3d0",
            "/sys/class/kgsl"
        )),
        Candidate("qcom_gpu_power_levels", Family.GPU, listOf(
            "/sys/class/kgsl/kgsl-3d0/available_pwrlevels",
            "/sys/class/kgsl/kgsl-3d0/min_pwrlevel",
            "/sys/class/kgsl/kgsl-3d0/max_pwrlevel"
        )),
        Candidate("qcom_gpu_clock_range", Family.GPU, listOf(
            "/sys/class/kgsl/kgsl-3d0/min_gpuclk",
            "/sys/class/kgsl/kgsl-3d0/max_gpuclk"
        )),
        Candidate("qcom_bus_dcvs", Family.VENDOR, listOf(
            "/sys/devices/system/cpu/bus_dcvs"
        )),
        Candidate("qcom_msm_thermal", Family.VENDOR, listOf(
            "/sys/module/msm_thermal/parameters/core_control_mask"
        )),
        Candidate("mtk_gpufreq_v2", Family.VENDOR, listOf("/proc/gpufreqv2")),
        Candidate("mtk_gpufreq_legacy", Family.VENDOR, listOf("/proc/gpufreq")),
        Candidate("mtk_ppm", Family.VENDOR, listOf(
            "/proc/ppm/enabled",
            "/proc/ppm/policy_status"
        )),
        Candidate("mtk_dvfsrc", Family.VENDOR, listOf(
            "/sys/class/devfreq/mtk-dvfsrc-devfreq",
            "/sys/class/devfreq/10012000.dvfsrc",
            "/sys/class/devfreq/13000000.dvfsrc"
        )),
        Candidate("exynos_thermal", Family.VENDOR, listOf(
            "/sys/module/exynos_thermal"
        )),
        Candidate("thermal", Family.THERMAL, listOf("/sys/class/thermal")),
        Candidate("thermal_control", Family.THERMAL, listOf(
            "/sys/class/thermal/thermal_message/sconfig",
            "/sys/module/msm_thermal/parameters/core_control_mask"
        )),
        Candidate("battery_power_supply", Family.BATTERY, listOf(
            "/sys/class/power_supply/battery/capacity"
        )),
        Candidate("battery_charge_control", Family.BATTERY, listOf(
            "/sys/class/power_supply/battery/charge_control_limit",
            "/sys/class/power_supply/battery/input_suspend"
        )),
        Candidate("display_kcal", Family.DISPLAY, listOf(
            "/sys/devices/platform/kcal_ctrl.0/kcal",
            "/sys/module/kcal/parameters/kcal_enable"
        )),
        Candidate("display_mtk_videox", Family.DISPLAY, listOf(
            "/sys/devices/platform/mtk_disp_mgr.0/rgb"
        )),
        Candidate("fstrim", Family.STORAGE, listOf("/system/bin/fstrim")),
        Candidate("tcp_keepalive", Family.NETWORK, listOf("/proc/sys/net/ipv4/tcp_keepalive_time")),
        Candidate("tcp_timestamps", Family.NETWORK, listOf("/proc/sys/net/ipv4/tcp_timestamps")),
        Candidate("bpf_jit_harden", Family.NETWORK, listOf("/proc/sys/net/core/bpf_jit_harden")),
        Candidate("selinux_enforcement", Family.VENDOR, listOf("/sys/fs/selinux/enforce"))
    )

    @Volatile
    private var lastSnapshot = Snapshot(0L, 0L, emptyList())

    suspend fun scan(context: Context): Snapshot = mutex.withLock {
        withContext(Dispatchers.IO) {
            val started = System.nanoTime()
            val app = context.applicationContext
            KernelRootRuntime.initialize(app)

            val cpuPolicies = KernelRootRuntime.listDirectories("/sys/devices/system/cpu/cpufreq")
                .filter { it.startsWith("policy") }
                .sortedWith(compareBy<String> { it.removePrefix("policy").toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it })
            val gpuDevfreq = KernelRootRuntime.listDirectories("/sys/class/devfreq")
                .filter { name ->
                    val n = name.lowercase()
                    n.contains("gpu") || n.contains("kgsl") || n.contains("mali") || n.contains("3d") || n.contains("adreno")
                }
                .take(24)
            val thermalZones = KernelRootRuntime.listDirectories("/sys/class/thermal")
                .filter { it.startsWith("thermal_zone") }
                .take(64)
            val blockDevices = KernelRootRuntime.listDirectories("/sys/block").take(64)

            val dynamic = buildList {
                if (cpuPolicies.isNotEmpty()) {
                    add(Candidate("cpu_policy_count", Family.CPU, cpuPolicies.map { "/sys/devices/system/cpu/cpufreq/$it" }, "dynamic-cpufreq"))
                }
                if (gpuDevfreq.isNotEmpty()) {
                    add(Candidate("gpu_devfreq", Family.GPU, gpuDevfreq.map { "/sys/class/devfreq/$it" }, "dynamic-devfreq"))
                    add(Candidate("gpu_cur_freq", Family.GPU, gpuDevfreq.map { "/sys/class/devfreq/$it/cur_freq" }, "dynamic-devfreq"))
                    add(Candidate("gpu_governor", Family.GPU, gpuDevfreq.map { "/sys/class/devfreq/$it/governor" }, "dynamic-devfreq"))
                    add(Candidate("gpu_min_max", Family.GPU, gpuDevfreq.flatMap { listOf(
                        "/sys/class/devfreq/$it/min_freq",
                        "/sys/class/devfreq/$it/max_freq"
                    ) }, "dynamic-devfreq"))
                }
                if (thermalZones.isNotEmpty()) {
                    add(Candidate("thermal_zones", Family.THERMAL, thermalZones.map { "/sys/class/thermal/$it/temp" }, "dynamic-thermal"))
                }
                if (blockDevices.isNotEmpty()) {
                    add(Candidate("block_queue", Family.STORAGE, blockDevices.map { "/sys/block/$it/queue/scheduler" }, "dynamic-block"))
                }
            }

            val expanded = (candidates + dynamic)
            val allPaths = expanded.flatMap { it.paths }.distinct().filter { KernelNodePolicy.isAllowed(it) }
            val exists = KernelRootRuntime.nodeExistsBatch(allPaths)
            val writable = KernelRootRuntime.nodeWritableBatch(allPaths.filter { exists[it] == true })

            val result = expanded.map { candidate ->
                val evidence = candidate.paths.filter { exists[it] == true }
                val matched = evidence.firstOrNull()
                Capability(
                    id = candidate.id,
                    family = candidate.family.name.lowercase(),
                    supported = matched != null,
                    matchedPath = matched,
                    evidencePaths = evidence.take(16),
                    writable = matched?.let { writable[it] == true } ?: false,
                    confidence = evidence.size.coerceAtMost(3) * 25 + if (matched != null) 25 else 0,
                    source = candidate.source
                )
            }

            val vendor = inferVendor(result)
            generation += 1L
            val topology = Topology(cpuPolicies, gpuDevfreq, thermalZones, blockDevices, System.currentTimeMillis())
            Snapshot(
                generatedAt = System.currentTimeMillis(),
                generation = generation,
                capabilities = result,
                vendor = vendor,
                backendHints = backendHints(vendor, result),
                scanLatencyMs = (System.nanoTime() - started) / 1_000_000L,
                topology = topology
            ).also { lastSnapshot = it }
        }
    }

    fun current(): Snapshot = lastSnapshot

    private fun inferVendor(caps: List<Capability>): Vendor {
        val q = caps.any { it.supported && it.id.startsWith("qcom_") }
        val m = caps.any { it.supported && it.id.startsWith("mtk_") }
        val e = caps.any { it.supported && it.id.startsWith("exynos") }
        return when {
            q && !m && !e -> Vendor.QUALCOMM
            m && !q && !e -> Vendor.MEDIATEK
            e && !q && !m -> Vendor.SAMSUNG_EXYNOS
            q && m || q && e || m && e -> Vendor.UNKNOWN
            else -> Vendor.GENERIC
        }
    }

    private fun backendHints(vendor: Vendor, caps: List<Capability>): List<String> = buildList {
        add("generic")
        when (vendor) {
            Vendor.QUALCOMM -> add("qualcomm")
            Vendor.MEDIATEK -> add("mediatek")
            Vendor.SAMSUNG_EXYNOS -> add("samsung-exynos")
            Vendor.GENERIC, Vendor.UNKNOWN -> Unit
        }
        if (caps.any { it.supported && it.id == "gpu_devfreq" }) add("devfreq")
        if (caps.any { it.supported && it.id == "uclamp" }) add("scheduler-uclamp")
        if (caps.any { it.supported && it.id == "cgroup_v2" }) add("cgroup-v2")
    }
}
