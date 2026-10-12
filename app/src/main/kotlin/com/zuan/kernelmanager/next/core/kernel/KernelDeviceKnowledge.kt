package com.zuan.kernelmanager.next.core.kernel

import android.os.Build
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Local device/kernel fingerprint. No cloud dependency and no guessed hardware facts. */
object KernelDeviceKnowledge {
    data class Snapshot(
        val fingerprint: String,
        val manufacturer: String,
        val model: String,
        val soc: String,
        val kernelRelease: String,
        val androidRelease: String,
        val vendor: KernelCapabilityEngine.Vendor,
        val backend: String,
        val capabilityGeneration: Long,
        val capabilityCount: Int,
        val topologyCpuPolicies: Int,
        val topologyGpuDevices: Int,
        val topologyThermalZones: Int,
        val topologyBlockDevices: Int,
        val generatedAt: Long
    )

    private const val PREF = "zkm_device_knowledge_v1"
    private const val KEY = "snapshot"

    suspend fun collect(context: Context): Snapshot = withContext(Dispatchers.IO) {
        val cap = KernelCapabilityEngine.scan(context)
        val soc = listOfNotNull(
            Build.HARDWARE.takeIf { it.isNotBlank() },
            Build.SOC_MANUFACTURER.takeIf { it.isNotBlank() },
            Build.SOC_MODEL.takeIf { it.isNotBlank() }
        ).distinct().joinToString("/")
        Snapshot(
            Build.FINGERPRINT,
            Build.MANUFACTURER.orEmpty(), Build.MODEL.orEmpty(), soc,
            KernelRootRuntime.readNode("/proc/sys/kernel/osrelease").trim(),
            Build.VERSION.RELEASE.orEmpty(), cap.vendor,
            when (cap.vendor) {
                KernelCapabilityEngine.Vendor.QUALCOMM -> "qualcomm"
                KernelCapabilityEngine.Vendor.MEDIATEK -> "mediatek"
                KernelCapabilityEngine.Vendor.SAMSUNG_EXYNOS -> "exynos"
                KernelCapabilityEngine.Vendor.GENERIC -> "generic"
                KernelCapabilityEngine.Vendor.UNKNOWN -> "unknown"
            }, cap.generation, cap.supported().size,
            cap.topology.cpuPolicies.size, cap.topology.gpuDevfreq.size,
            cap.topology.thermalZones.size, cap.topology.blockDevices.size, System.currentTimeMillis()
        ).also { save(context, it) }
    }

    fun save(context: Context, snapshot: Snapshot) {
        val o = JSONObject()
            .put("fingerprint", snapshot.fingerprint)
            .put("manufacturer", snapshot.manufacturer)
            .put("model", snapshot.model)
            .put("soc", snapshot.soc)
            .put("kernelRelease", snapshot.kernelRelease)
            .put("androidRelease", snapshot.androidRelease)
            .put("vendor", snapshot.vendor.name)
            .put("backend", snapshot.backend)
            .put("capabilityGeneration", snapshot.capabilityGeneration)
            .put("capabilityCount", snapshot.capabilityCount)
            .put("topologyCpuPolicies", snapshot.topologyCpuPolicies)
            .put("topologyGpuDevices", snapshot.topologyGpuDevices)
            .put("topologyThermalZones", snapshot.topologyThermalZones)
            .put("topologyBlockDevices", snapshot.topologyBlockDevices)
            .put("generatedAt", snapshot.generatedAt)
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, o.toString()).apply()
    }

    fun load(context: Context): Snapshot? = runCatching {
        val raw = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return null
        val o = JSONObject(raw)
        Snapshot(
            fingerprint = o.optString("fingerprint"),
            manufacturer = o.optString("manufacturer"),
            model = o.optString("model"),
            soc = o.optString("soc"),
            kernelRelease = o.optString("kernelRelease"),
            androidRelease = o.optString("androidRelease"),
            vendor = runCatching { KernelCapabilityEngine.Vendor.valueOf(o.optString("vendor")) }
                .getOrDefault(KernelCapabilityEngine.Vendor.UNKNOWN),
            backend = o.optString("backend"),
            capabilityGeneration = o.optLong("capabilityGeneration"),
            capabilityCount = o.optInt("capabilityCount"),
            topologyCpuPolicies = o.optInt("topologyCpuPolicies"),
            topologyGpuDevices = o.optInt("topologyGpuDevices"),
            topologyThermalZones = o.optInt("topologyThermalZones"),
            topologyBlockDevices = o.optInt("topologyBlockDevices"),
            generatedAt = o.optLong("generatedAt")
        )
    }.getOrNull()

    fun sameDevice(snapshot: Snapshot): Boolean =
        snapshot.fingerprint.isNotBlank() && snapshot.fingerprint == Build.FINGERPRINT
}
