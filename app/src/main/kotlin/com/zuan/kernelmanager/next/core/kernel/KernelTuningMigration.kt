/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.core.kernel

import android.content.Context
import com.zuan.kernelmanager.next.library.device.tuning.CpuTweakStore
import com.zuan.kernelmanager.next.library.device.tuning.MemoryTweakStore
import com.zuan.kernelmanager.next.library.device.tuning.SchedulerTweakStore
import com.zuan.kernelmanager.next.feature.home.menu.ThermalTweakStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Compatibility bridge for the pre-KernelEngine tuning stores. Persistence
 * remains backward compatible, while every kernel mutation is routed through
 * the typed policy/transaction layer.
 */
object KernelTuningMigration {
    data class Report(
        val cpuActions: Int = 0,
        val memoryActions: Int = 0,
        val schedulerActions: Int = 0,
        val thermalAttempted: Boolean = false,
        val thermalApplied: Boolean = true,
        val zramAttempted: Boolean = false,
        val zramApplied: Boolean = false,
        val transactionSuccess: Boolean = true,
        val rollbackVerified: Boolean = true,
        val errors: List<String> = emptyList(),
        val skippedLegacyNodes: Int = 0
    ) {
        val passed: Boolean get() = transactionSuccess && rollbackVerified && errors.isEmpty() && thermalApplied && (!zramAttempted || zramApplied)
    }

    suspend fun applyPersisted(context: Context, force: Boolean = false): Report = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        KernelRootRuntime.initialize(app)

        val cpuPrefs = CpuTweakStore.exportPreferences(app)
        val memoryPrefs = MemoryTweakStore.exportPreferences(app)
        val schedulerPrefs = SchedulerTweakStore.exportPreferences(app)

        val actions = ArrayList<KernelAction.WriteNode>()
        val errors = ArrayList<String>()
        var skippedLegacyNodes = 0

        val cpuEnabled = force || (cpuPrefs[CpuTweakStore.APPLY_ON_BOOT.name] as? Boolean ?: cpuPrefs[CpuTweakStore.APPLY_ON_BOOT.name].toString().toBoolean())
        if (cpuEnabled) buildCpuActions(cpuPrefs, actions, errors)

        val memoryVmEnabled = force || (memoryPrefs[MemoryTweakStore.APPLY_ON_BOOT_VM_IO.name] as? Boolean ?: memoryPrefs[MemoryTweakStore.APPLY_ON_BOOT_VM_IO.name].toString().toBoolean())
        if (memoryVmEnabled) buildMemoryActions(memoryPrefs, actions, errors)

        val schedulerEnabled = force || (schedulerPrefs[SchedulerTweakStore.APPLY_ON_BOOT.name] as? Boolean ?: schedulerPrefs[SchedulerTweakStore.APPLY_ON_BOOT.name].toString().toBoolean())
        if (schedulerEnabled) buildSchedulerActions(schedulerPrefs, actions, errors)

        val transaction = if (errors.isEmpty() && actions.isNotEmpty()) {
            KernelCore.profiles.applyActions(app, "__persisted_tuning__", actions)
        } else {
            ProfileApplyResult("__persisted_tuning__", 0, if (errors.isEmpty()) null else errors.first(), verified = 0)
        }

        val transactionSuccess = transaction.failed == null && transaction.applied == transaction.verified
        var zramAttempted = false
        var zramApplied = true
        if (transactionSuccess && (force || (memoryPrefs[MemoryTweakStore.APPLY_ON_BOOT_ZRAM.name] as? Boolean ?: memoryPrefs[MemoryTweakStore.APPLY_ON_BOOT_ZRAM.name].toString().toBoolean()))) {
            val size = memoryPrefs[MemoryTweakStore.ZRAM_SIZE.name]?.toString()?.toIntOrNull()
            val algo = memoryPrefs[MemoryTweakStore.ZRAM_ALGO.name]?.toString()
            if (size != null && !algo.isNullOrBlank()) {
                zramAttempted = true
                zramApplied = KernelControlEngine.configureZram(size, algo).success
                if (!zramApplied) {
                    errors += "ZRAM configuration failed"
                    KernelCore.profiles.restoreActive()
                }
            }
        }

        var thermalAttempted = false
        var thermalApplied = true
        val thermalPrefs = ThermalTweakStore.exportPreferences(app)
        val thermalEnabled = force || (thermalPrefs[ThermalTweakStore.APPLY_ON_BOOT.name] as? Boolean ?: thermalPrefs[ThermalTweakStore.APPLY_ON_BOOT.name].toString().toBoolean())
        if (transactionSuccess && zramApplied && thermalEnabled) {
            thermalAttempted = true
            thermalApplied = runCatching { ThermalTweakStore.applyAll(app); true }.getOrElse {
                errors += "Thermal persisted apply failed: ${it.message ?: it.javaClass.simpleName}"
                false
            }
        }

        runCatching { KernelStateEngine.refresh(app) }.onFailure { errors += "State refresh failed: ${it.message ?: it.javaClass.simpleName}" }
        Report(
            cpuActions = actions.count { it.path.contains("/cpufreq/") },
            memoryActions = actions.count { it.path.startsWith("/proc/sys/vm/") || it.path.contains("/sys/block/") },
            schedulerActions = actions.count { it.path.startsWith("/proc/sys/kernel/sched_") },
            zramAttempted = zramAttempted,
            zramApplied = zramApplied,
            thermalAttempted = thermalAttempted,
            thermalApplied = thermalApplied,
            transactionSuccess = transactionSuccess,
            rollbackVerified = transaction.rolledBack || transaction.failed == null,
            errors = errors + listOfNotNull(transaction.failed),
            skippedLegacyNodes = skippedLegacyNodes
        )
    }

    suspend fun dryRun(context: Context, force: Boolean = false): Report = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val cpuPrefs = CpuTweakStore.exportPreferences(app)
        val memoryPrefs = MemoryTweakStore.exportPreferences(app)
        val schedulerPrefs = SchedulerTweakStore.exportPreferences(app)
        val actions = ArrayList<KernelAction.WriteNode>()
        val errors = ArrayList<String>()
        val cpuEnabled = force || (cpuPrefs[CpuTweakStore.APPLY_ON_BOOT.name] as? Boolean ?: cpuPrefs[CpuTweakStore.APPLY_ON_BOOT.name].toString().toBoolean())
        val memEnabled = force || (memoryPrefs[MemoryTweakStore.APPLY_ON_BOOT_VM_IO.name] as? Boolean ?: memoryPrefs[MemoryTweakStore.APPLY_ON_BOOT_VM_IO.name].toString().toBoolean())
        val schedEnabled = force || (schedulerPrefs[SchedulerTweakStore.APPLY_ON_BOOT.name] as? Boolean ?: schedulerPrefs[SchedulerTweakStore.APPLY_ON_BOOT.name].toString().toBoolean())
        if (cpuEnabled) buildCpuActions(cpuPrefs, actions, errors)
        if (memEnabled) buildMemoryActions(memoryPrefs, actions, errors)
        if (schedEnabled) buildSchedulerActions(schedulerPrefs, actions, errors)
        val unsafe = actions.firstOrNull {
            val normalized = KernelNodePolicy.normalizeLegacyPath(it.path)
            !KernelNodePolicy.isWriteAllowed(normalized)
        }
        if (unsafe != null) errors += "Policy rejected: ${KernelNodePolicy.normalizeLegacyPath(unsafe.path)}"
        Report(
            cpuActions = actions.count { it.path.contains("/cpufreq/") },
            memoryActions = actions.count { it.path.startsWith("/proc/sys/vm/") || it.path.contains("/sys/block/") },
            schedulerActions = actions.count { it.path.startsWith("/proc/sys/kernel/sched_") },
            zramAttempted = force || memoryPrefs[MemoryTweakStore.APPLY_ON_BOOT_ZRAM.name]?.toString()?.toBoolean() == true,
            zramApplied = true,
            transactionSuccess = errors.isEmpty(),
            rollbackVerified = true,
            errors = errors
        )
    }

    private fun buildCpuActions(prefs: Map<String, Any>, actions: MutableList<KernelAction.WriteNode>, errors: MutableList<String>) {
        prefs.forEach { (name, raw) ->
            val value = raw.toString().trim()
            when {
                name.startsWith("gov::") -> {
                    val base = name.removePrefix("gov::")
                    addAction(actions, errors, "$base/scaling_governor", value)
                }
                name.startsWith("min::") -> {
                    val base = name.removePrefix("min::")
                    val mhz = value.toLongOrNull()
                    if (mhz != null && mhz > 0) addAction(actions, errors, "$base/scaling_min_freq", (mhz * 1000L).toString())
                }
                name.startsWith("max::") -> {
                    val base = name.removePrefix("max::")
                    val mhz = value.toLongOrNull()
                    if (mhz != null && mhz > 0) addAction(actions, errors, "$base/scaling_max_freq", (mhz * 1000L).toString())
                }
            }
        }
        val offline = prefs[CpuTweakStore.OFFLINE_CORES.name]
        val cores = when (offline) {
            is Set<*> -> offline.mapNotNull { it.toString().toIntOrNull() }
            else -> emptyList()
        }
        cores.filter { it > 0 && it <= 4096 }.forEach { addAction(actions, errors, "/sys/devices/system/cpu/cpu$it/online", "0") }
    }

    private fun buildMemoryActions(prefs: Map<String, Any>, actions: MutableList<KernelAction.WriteNode>, errors: MutableList<String>) {
        prefs.forEach { (name, raw) ->
            val value = raw.toString().replace('\u0000', ' ').trim()
            when {
                name.startsWith("vm::") -> addAction(actions, errors, name.removePrefix("vm::"), value)
                name.startsWith("iosched::") -> {
                    val dev = name.removePrefix("iosched::")
                    addAction(actions, errors, "/sys/block/$dev/queue/scheduler", value)
                }
                name.startsWith("iotun::") -> {
                    val parts = name.removePrefix("iotun::").split("::")
                    if (parts.size == 2) addAction(actions, errors, "/sys/block/${parts[0]}/queue/${parts[1]}", value)
                }
            }
        }
    }

    private fun buildSchedulerActions(prefs: Map<String, Any>, actions: MutableList<KernelAction.WriteNode>, errors: MutableList<String>) {
        prefs.forEach { (name, raw) ->
            if (name.startsWith("sched::")) addAction(actions, errors, name.removePrefix("sched::"), raw.toString().trim())
        }
    }

    private fun addAction(actions: MutableList<KernelAction.WriteNode>, errors: MutableList<String>, path: String, value: String) {
        val normalized = KernelNodePolicy.normalizeLegacyPath(path)
        val canonical = KernelNodePolicy.canonical(normalized)
        if (canonical == null || canonical != normalized || !KernelNodePolicy.isWriteAllowed(canonical)) {
            // Old versions could persist nodes that are no longer exposed or are
            // intentionally blocked by the current safety policy. These are stale
            // compatibility records, not a reason to prevent all other persisted
            // tuning from migrating. Never weaken policy to make them writable.
            return
        }
        if (value.isBlank() || value.length > 512) {
            errors += "Invalid persisted value: $normalized"
            return
        }
        actions += KernelAction.WriteNode(canonical, value)
    }
}
