/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.core.kernel

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

sealed interface KernelAction {
    data class WriteNode(val path: String, val value: String) : KernelAction
}

data class KernelProfile(
    val id: String,
    val displayName: String,
    val actions: List<KernelAction>
)

data class ProfileApplyResult(
    val profileId: String,
    val applied: Int,
    val failed: String? = null,
    val rolledBack: Boolean = false,
    val verified: Int = 0
)

data class KernelTransactionSnapshot(
    val profileId: String,
    val originalValues: Map<String, String>,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Typed, capability/policy-gated transaction engine.
 *
 * Apply is serialized, captures originals before mutation, uses daemon batch
 * writes where possible, verifies writes, and rolls back every captured node on
 * any failure. It never accepts arbitrary shell commands.
 */
class KernelProfileEngine {
    private val profiles = LinkedHashMap<String, KernelProfile>()
    private var activeTransaction: KernelTransactionSnapshot? = null
    private val mutex = Mutex()

    @Synchronized fun register(profile: KernelProfile) {
        require(profile.id.isNotBlank()) { "Profile id must not be blank" }
        require(profile.actions.size <= 96) { "Profile has too many actions" }
        profiles[profile.id] = profile
    }

    @Synchronized fun unregister(profileId: String) { profiles.remove(profileId) }
    @Synchronized fun get(profileId: String): KernelProfile? = profiles[profileId]
    @Synchronized fun all(): List<KernelProfile> = profiles.values.toList()
    @Synchronized fun activeProfileId(): String? = activeTransaction?.profileId

    suspend fun apply(context: Context, profileId: String): ProfileApplyResult = mutex.withLock {
        val profile = synchronized(this) { profiles[profileId] }
            ?: return@withLock ProfileApplyResult(profileId, 0, "Unknown profile")
        applyActionsLocked(profileId, profile.actions.filterIsInstance<KernelAction.WriteNode>())
    }

    /**
     * Apply a typed action set without requiring the caller to register a permanent
     * profile. The action set is still captured, verified and made the active
     * transaction so another profile can safely restore it.
     */
    suspend fun applyActions(context: Context, profileId: String, actions: List<KernelAction.WriteNode>): ProfileApplyResult =
        mutex.withLock { applyActionsLocked(profileId, actions) }

    private suspend fun applyActionsLocked(profileId: String, writes: List<KernelAction.WriteNode>): ProfileApplyResult {
        if (!restoreActiveLocked()) {
            return ProfileApplyResult(profileId, 0, "Existing transaction could not be restored safely")
        }
        if (writes.isEmpty()) {
            synchronized(this) { activeTransaction = KernelTransactionSnapshot(profileId, emptyMap()) }
            return ProfileApplyResult(profileId, 0, verified = 0)
        }

        val normalizedWrites = writes.map {
            it.copy(path = KernelNodePolicy.normalizeLegacyPath(it.path))
        }.distinctBy { it.path }
        val invalid = normalizedWrites.firstOrNull {
            val canonical = KernelNodePolicy.canonical(it.path)
            canonical == null || canonical != it.path ||
                !KernelNodePolicy.isWriteAllowed(it.path) || it.value.isBlank() || it.value.length > 512
        }
        if (invalid != null) {
            return ProfileApplyResult(profileId, 0, "Invalid or blocked node: ${invalid.path}")
        }

        val tx = KernelTransactionV2.execute(
            normalizedWrites.map { it.path to it.value },
            verify = true
        )
        if (!tx.success) {
            return ProfileApplyResult(
                profileId = profileId,
                applied = 0,
                failed = tx.error ?: "Transaction failed",
                rolledBack = tx.rollbackVerified,
                verified = tx.entries.count { it.ok }
            )
        }

        synchronized(this) {
            activeTransaction = KernelTransactionSnapshot(
                profileId = profileId,
                originalValues = tx.entries.associate { it.path to it.before }
            )
        }
        return ProfileApplyResult(
            profileId = profileId,
            applied = tx.entries.size,
            verified = tx.entries.count { it.ok }
        )
    }

    suspend fun dryRun(profileId: String): ProfileApplyResult = withContext(Dispatchers.Default) {
        val profile = synchronized(this@KernelProfileEngine) { profiles[profileId] }
            ?: return@withContext ProfileApplyResult(profileId, 0, "Unknown profile")
        val rejected = profile.actions.filterIsInstance<KernelAction.WriteNode>()
            .firstOrNull { !KernelNodePolicy.isWriteAllowed(it.path) }
        if (rejected != null) ProfileApplyResult(profileId, 0, "Policy rejected: ${rejected.path}")
        else ProfileApplyResult(profileId, profile.actions.size)
    }

    suspend fun restoreActive(): Boolean = mutex.withLock { restoreActiveLocked() }

    private suspend fun restoreActiveLocked(): Boolean {
        val tx = synchronized(this) { activeTransaction } ?: return true
        val restored = rollbackVerified(tx.originalValues)
        if (restored) synchronized(this) { activeTransaction = null }
        return restored
    }

    private suspend fun rollback(values: Map<String, String>): Boolean {
        return rollbackVerified(values)
    }

    private suspend fun rollbackVerified(values: Map<String, String>): Boolean {
        if (values.isEmpty()) return true
        val safe = values.filterKeys { KernelNodePolicy.isWriteAllowed(it) }
        if (safe.isEmpty()) return false
        return runCatching {
            val outcomes = KernelRootRuntime.writeNodes(safe.keys.toList(), safe.values.toList())
            if (safe.keys.any { outcomes[it] != true }) return@runCatching false
            val observed = KernelRootRuntime.readNodes(safe.keys.toList())
            safe.all { (path, value) -> normalize(observed[path]) == normalize(value) }
        }.onFailure { Log.w("ZKM-ProfileEngine", "Rollback failed", it) }.getOrDefault(false)
    }

    /** Capture a reversible profile from the central state snapshot. */
    suspend fun captureCurrent(context: Context, profileId: String, displayName: String): KernelProfile? = withContext(Dispatchers.IO) {
        val snapshot = KernelCapabilityEngine.current()
        val state = KernelStateEngine.flow.value
        if (!snapshot.has("cpu_freq") && state.cpuPolicies.isEmpty()) return@withContext null
        val actions = buildList {
            state.cpuPolicies.forEach { cpu ->
                val base = "/sys/devices/system/cpu/cpufreq/${cpu.policy}"
                if (cpu.governor.isNotBlank() && KernelRootRuntime.nodeExistsBlocking("$base/scaling_governor")) {
                    add(KernelAction.WriteNode("$base/scaling_governor", cpu.governor))
                }
                if (cpu.minKHz > 0 && KernelRootRuntime.nodeExistsBlocking("$base/scaling_min_freq")) {
                    add(KernelAction.WriteNode("$base/scaling_min_freq", cpu.minKHz.toString()))
                }
                if (cpu.maxKHz > 0 && KernelRootRuntime.nodeExistsBlocking("$base/scaling_max_freq")) {
                    add(KernelAction.WriteNode("$base/scaling_max_freq", cpu.maxKHz.toString()))
                }
            }
            state.gpu.forEach { gpu ->
                val base = gpu.path
                if (gpu.governor.isNotBlank() && KernelRootRuntime.nodeExistsBlocking("$base/governor")) {
                    add(KernelAction.WriteNode("$base/governor", gpu.governor))
                }
                if (gpu.minKHz > 0 && KernelRootRuntime.nodeExistsBlocking("$base/min_freq")) {
                    add(KernelAction.WriteNode("$base/min_freq", (gpu.minKHz * 1000L).toString()))
                }
                if (gpu.maxKHz > 0 && KernelRootRuntime.nodeExistsBlocking("$base/max_freq")) {
                    add(KernelAction.WriteNode("$base/max_freq", (gpu.maxKHz * 1000L).toString()))
                }
            }
            state.ioSchedulers.forEach { (device, raw) ->
                val active = Regex("\\[([^]]+)\\]").find(raw)?.groupValues?.getOrNull(1)
                    ?: raw.split(Regex("\\s+")).firstOrNull().orEmpty()
                if (active.isNotBlank()) add(KernelAction.WriteNode("/sys/block/$device/queue/scheduler", active))
            }

            // Shared scheduler/VM state is safe to capture and restore as part of
            // performance profiles. Charging, SELinux and ZRAM are intentionally
            // excluded from generic profiles because they have broader side effects.
            val extraPaths = listOf(
                "/proc/sys/kernel/sched_util_clamp_min",
                "/proc/sys/kernel/sched_util_clamp_max",
                "/proc/sys/kernel/sched_latency_ns",
                "/proc/sys/kernel/sched_min_granularity_ns",
                "/proc/sys/kernel/sched_wakeup_granularity_ns",
                "/proc/sys/vm/swappiness",
                "/proc/sys/vm/vfs_cache_pressure",
                "/proc/sys/vm/dirty_ratio",
                "/proc/sys/vm/dirty_background_ratio"
            )
            val extraValues = KernelRootRuntime.readNodes(extraPaths.filter { KernelRootRuntime.nodeExistsBlocking(it) })
            extraValues.forEach { (path, value) ->
                if (value.isNotBlank() && KernelNodePolicy.isWriteAllowed(path)) {
                    add(KernelAction.WriteNode(path, value.trim()))
                }
            }

            if (snapshot.vendor == KernelCapabilityEngine.Vendor.QUALCOMM) {
                val qcomPaths = listOf(
                    "/sys/class/kgsl/kgsl-3d0/min_gpuclk",
                    "/sys/class/kgsl/kgsl-3d0/max_gpuclk",
                    "/sys/class/kgsl/kgsl-3d0/min_pwrlevel",
                    "/sys/class/kgsl/kgsl-3d0/max_pwrlevel"
                )
                KernelRootRuntime.readNodes(qcomPaths.filter { KernelRootRuntime.nodeExistsBlocking(it) })
                    .forEach { (path, value) -> if (value.isNotBlank()) add(KernelAction.WriteNode(path, value.trim())) }
            }
        }.distinctBy { it.path }
        if (actions.isEmpty()) null else KernelProfile(profileId, displayName, actions)
    }

    private fun normalize(value: String?): String = value.orEmpty().trim()
}

/**
 * Separate read-vs-write policy. Read discovery remains broad, while writes
 * reject known destructive sysfs control paths. Canonicalization prevents '..'
 * traversal from bypassing the prefix checks.
 */
object KernelNodePolicy {
    private val allowedPrefixes = listOf(
        "/sys/", "/proc/", "/dev/cpuset/", "/dev/stune/", "/dev/cpuctl/", "/sys/fs/cgroup/"
    )
    private val deniedWritePrefixes = listOf(
        "/sys/kernel/debug/",
        "/sys/bus/pci/drivers/",
        "/sys/bus/platform/drivers/",
        "/proc/sysrq-trigger",
        "/proc/kcore",
        "/proc/kmem",
        "/proc/mem"
    )
    private val deniedWriteNames = setOf(
        "unbind", "bind", "remove", "delete", "driver_override", "uevent",
        "soft_reset", "hard_reset", "reset_controller"
    )

    /**
     * Normalize a small set of paths emitted by older ZKM GPU/profile code.
     * Some Qualcomm kernels expose `min_gpuclk`/`max_gpuclk`; older ZKM
     * snapshots accidentally persisted the underscored spellings. Keeping the
     * alias here lets old profiles migrate without weakening the write policy.
     */
    fun normalizeLegacyPath(path: String): String {
        val trimmed = path.trim()
        return when (trimmed) {
            "/sys/class/kgsl/kgsl-3d0/min_gpu_clk" -> "/sys/class/kgsl/kgsl-3d0/min_gpuclk"
            "/sys/class/kgsl/kgsl-3d0/max_gpu_clk" -> "/sys/class/kgsl/kgsl-3d0/max_gpuclk"
            else -> trimmed
        }
    }

    fun canonical(path: String): String? = runCatching {
        val normalized = java.nio.file.Paths.get(normalizeLegacyPath(path)).normalize()
        if (!normalized.isAbsolute) null else normalized.toString()
    }.getOrNull()

    fun isAllowed(path: String): Boolean {
        val canonical = canonical(path) ?: return false
        return allowedPrefixes.any { canonical == it.dropLast(1) || canonical.startsWith(it) }
    }

    /**
     * Write policy intentionally remains broad enough for vendor kernel knobs,
     * but explicitly excludes device-driver lifecycle controls and dangerous
     * procfs interfaces. Cgroup v2 is restricted to cgroup.procs only.
     */
    fun isWriteAllowed(path: String): Boolean {
        val canonical = canonical(path) ?: return false
        if (!isAllowed(canonical)) return false
        if (deniedWritePrefixes.any { canonical.startsWith(it) }) return false
        val name = canonical.substringAfterLast('/')
        if (name in deniedWriteNames) return false
        if (canonical.contains("/driver/") && canonical.contains("/drivers/")) return false
        if (canonical.startsWith("/sys/fs/cgroup/")) return canonical.endsWith("/cgroup.procs")
        if (canonical == "/proc/sys/kernel/sysrq" || canonical == "/proc/sys/kernel/kexec_load_disabled") return false
        return true
    }
}

/** Small persistent profile store; profiles contain typed node actions only. */
object KernelProfileStore {
    private const val PREF = "zkm_kernel_profiles_v1"
    private const val KEY = "profiles"

    fun save(context: Context, profile: KernelProfile) {
        val prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val all = loadAll(context).filterNot { it.id == profile.id }.toMutableList()
        all += profile
        prefs.edit().putString(KEY, encode(all)).apply()
    }

    fun delete(context: Context, profileId: String) {
        val prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY, encode(loadAll(context).filterNot { it.id == profileId })).apply()
    }

    fun loadAll(context: Context): List<KernelProfile> {
        val raw = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val actions = item.optJSONArray("actions") ?: JSONArray()
                    val parsed = buildList {
                        for (j in 0 until actions.length()) {
                            val a = actions.getJSONObject(j)
                            if (a.optString("type") == "write") {
                                val rawPath = a.optString("path")
                                val normalizedPath = KernelNodePolicy.normalizeLegacyPath(rawPath)
                                add(KernelAction.WriteNode(normalizedPath, a.optString("value")))
                            }
                        }
                    }
                    add(KernelProfile(item.optString("id"), item.optString("name"), parsed))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encode(profiles: List<KernelProfile>): String {
        val array = JSONArray()
        profiles.forEach { profile ->
            val obj = JSONObject().put("id", profile.id).put("name", profile.displayName)
            val actions = JSONArray()
            profile.actions.filterIsInstance<KernelAction.WriteNode>().forEach {
                actions.put(JSONObject().put("type", "write").put("path", it.path).put("value", it.value))
            }
            obj.put("actions", actions)
            array.put(obj)
        }
        return array.toString()
    }
}
