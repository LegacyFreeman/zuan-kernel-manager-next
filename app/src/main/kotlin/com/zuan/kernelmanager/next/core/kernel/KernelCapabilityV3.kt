package com.zuan.kernelmanager.next.core.kernel

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Capability V3: evidence is separated from support, mutability and confidence. */
object KernelCapabilityV3 {
    data class NodeEvidence(
        val path: String,
        val exists: Boolean,
        val readable: Boolean,
        val writable: Boolean,
        val value: String = "",
        val safeForProbe: Boolean = KernelNodePolicy.isWriteAllowed(path)
    )

    data class Capability(
        val id: String,
        val family: KernelCapabilityEngine.Family,
        val backend: String,
        val supported: Boolean,
        val readable: Boolean,
        val writable: Boolean,
        val confidence: Int,
        val evidence: List<NodeEvidence>
    )

    data class Snapshot(
        val generatedAt: Long,
        val sourceGeneration: Long,
        val vendor: KernelCapabilityEngine.Vendor,
        val kernelRelease: String,
        val capabilities: List<Capability>
    ) {
        fun get(id: String) = capabilities.firstOrNull { it.id == id }
        fun writable(id: String) = get(id)?.writable == true
    }

    suspend fun scan(context: Context): Snapshot = withContext(Dispatchers.IO) {
        val base = KernelCapabilityEngine.scan(context)
        val candidates = base.capabilities.filter { it.supported }
        val paths = candidates.flatMap { it.evidencePaths }.distinct()
        val exists = KernelRootRuntime.nodeExistsBatch(paths)
        val values = KernelRootRuntime.readNodes(paths.filter { exists[it] == true })
        val writable = KernelRootRuntime.nodeWritableBatch(paths.filter { exists[it] == true })
        val caps = candidates.map { c ->
            val evidence = c.evidencePaths.map { p ->
                val e = exists[p] == true
                NodeEvidence(p, e, e && values.containsKey(p), e && writable[p] == true, values[p].orEmpty())
            }
            val readable = evidence.any { it.readable }
            val canWrite = evidence.any { it.writable && it.safeForProbe }
            val confidence = when {
                canWrite && readable -> 100
                readable -> 85
                evidence.any { it.exists } -> 60
                else -> 0
            }
            Capability(c.id, runCatching { KernelCapabilityEngine.Family.valueOf(c.family) }.getOrDefault(KernelCapabilityEngine.Family.VENDOR),
                backendFor(base.vendor), c.supported, readable, canWrite, confidence, evidence)
        }
        Snapshot(System.currentTimeMillis(), base.generation, base.vendor, KernelRootRuntime.readNode("/proc/sys/kernel/osrelease").trim(), caps)
    }

    private fun backendFor(vendor: KernelCapabilityEngine.Vendor): String = when (vendor) {
        KernelCapabilityEngine.Vendor.QUALCOMM -> "qualcomm"
        KernelCapabilityEngine.Vendor.MEDIATEK -> "mediatek"
        KernelCapabilityEngine.Vendor.SAMSUNG_EXYNOS -> "exynos"
        KernelCapabilityEngine.Vendor.GENERIC -> "generic"
        KernelCapabilityEngine.Vendor.UNKNOWN -> "unknown"
    }
}
