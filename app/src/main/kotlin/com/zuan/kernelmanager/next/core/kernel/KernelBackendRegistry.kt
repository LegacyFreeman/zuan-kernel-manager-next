/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.core.kernel

/** Capability-selected backend registry. */
object KernelBackendRegistry {
    data class Backend(
        val id: String,
        val family: String,
        val requiredCapabilities: Set<String>,
        val priority: Int = 0
    )

    private val builtIns = listOf(
        Backend("qualcomm", "qualcomm", setOf("qcom_kgsl"), 100),
        Backend("mediatek", "mediatek", setOf("mtk_gpufreq_v2"), 100),
        Backend("mediatek_legacy", "mediatek", setOf("mtk_gpufreq_legacy"), 90),
        Backend("samsung-exynos", "samsung-exynos", setOf("exynos_thermal"), 80),
        Backend("generic-devfreq", "generic", setOf("gpu_devfreq"), 40),
        Backend("generic", "generic", emptySet(), 0)
    )

    @Volatile private var registered: List<Backend> = builtIns

    @Synchronized fun register(backend: Backend) {
        registered = (registered.filterNot { it.id == backend.id } + backend).sortedByDescending { it.priority }
    }

    @Synchronized fun unregister(id: String) {
        if (builtIns.any { it.id == id }) return
        registered = registered.filterNot { it.id == id }
    }

    fun all(): List<Backend> = registered

    fun available(snapshot: KernelCapabilityEngine.Snapshot): List<Backend> =
        registered.filter { backend -> backend.requiredCapabilities.all(snapshot::has) }

    fun select(snapshot: KernelCapabilityEngine.Snapshot): Backend =
        available(snapshot).firstOrNull { it.id != "generic" } ?: builtIns.last()
}
