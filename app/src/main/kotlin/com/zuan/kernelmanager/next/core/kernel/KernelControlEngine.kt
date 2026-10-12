/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.core.kernel

import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking

/**
 * Typed, capability-aware kernel controls. Feature modules should use this
 * layer rather than constructing privileged shell commands themselves.
 */
object KernelControlEngine {
    private fun onMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()

    data class OperationResult(
        val success: Boolean,
        val path: String = "",
        val requested: String = "",
        val observed: String = "",
        val error: String? = null
    )

    data class ZramResult(
        val success: Boolean,
        val previousSizeBytes: Long = 0L,
        val previousAlgorithm: String = "",
        val requestedSizeBytes: Long = 0L,
        val requestedAlgorithm: String = "",
        val rollbackAttempted: Boolean = false,
        val rollbackVerified: Boolean = false,
        val error: String? = null
    )

    data class TransactionResult(
        val success: Boolean,
        val operations: List<OperationResult> = emptyList(),
        val rollbackAttempted: Boolean = false,
        val rollbackVerified: Boolean = false,
        val error: String? = null
    )

    suspend fun setCpuGovernor(policy: String, governor: String): OperationResult = withContext(Dispatchers.IO) {
        val policyName = safeName(policy, "policy[0-9]+") ?: return@withContext fail(policy, governor, "Invalid CPU policy")
        val gov = governor.trim()
        if (!gov.matches(Regex("[A-Za-z0-9_.-]+"))) return@withContext fail(policy, governor, "Invalid governor")
        val base = "/sys/devices/system/cpu/cpufreq/$policyName"
        val path = "$base/scaling_governor"
        if (!KernelRootRuntime.nodeExists(path)) return@withContext fail(path, gov, "CPU policy is not supported")
        val available = KernelRootRuntime.readNode("$base/scaling_available_governors")
        if (available.isNotBlank() && gov !in available.split(Regex("\\s+"))) return@withContext fail(path, gov, "Governor is not exposed by kernel")
        writeVerify(path, gov)
    }

    suspend fun setCpuMinMax(policy: String, minKHz: Long, maxKHz: Long): List<OperationResult> = withContext(Dispatchers.IO) {
        if (minKHz <= 0L || maxKHz <= 0L || minKHz > maxKHz) return@withContext listOf(fail(policy, "$minKHz:$maxKHz", "Invalid frequency range"))
        val policyName = safeName(policy, "policy[0-9]+") ?: return@withContext listOf(fail(policy, "$minKHz:$maxKHz", "Invalid CPU policy"))
        val base = "/sys/devices/system/cpu/cpufreq/$policyName"
        val minPath = "$base/scaling_min_freq"
        val maxPath = "$base/scaling_max_freq"
        val currentMin = KernelRootRuntime.readNode(minPath).toLongOrNull() ?: minKHz
        val currentMax = KernelRootRuntime.readNode(maxPath).toLongOrNull() ?: maxKHz
        if (!KernelRootRuntime.nodeExists(minPath) || !KernelRootRuntime.nodeExists(maxPath)) {
            return@withContext listOf(fail(base, "$minKHz:$maxKHz", "CPU frequency nodes missing"))
        }

        val order = if (minKHz > currentMax) listOf(maxPath, minPath)
        else if (maxKHz < currentMin) listOf(minPath, maxPath)
        else listOf(minPath, maxPath)
        val targets = order.map { path -> path to if (path == minPath) minKHz.toString() else maxKHz.toString() }
        return@withContext transactionalWrite(targets).operations
    }

    suspend fun setCpuCoreOnline(coreId: Int, online: Boolean): OperationResult = withContext(Dispatchers.IO) {
        if (coreId <= 0 || coreId > 4096) return@withContext fail("cpu$coreId", online.toString(), "Invalid CPU id")
        val path = "/sys/devices/system/cpu/cpu$coreId/online"
        writeVerify(path, if (online) "1" else "0")
    }

    suspend fun setUclamp(min: Int? = null, max: Int? = null): List<OperationResult> = withContext(Dispatchers.IO) {
        val out = ArrayList<OperationResult>(2)
        if (min != null) out += boundedWrite("/proc/sys/kernel/sched_util_clamp_min", min, 0..1024)
        if (max != null) out += boundedWrite("/proc/sys/kernel/sched_util_clamp_max", max, 0..1024)
        out
    }

    suspend fun setGpuGovernor(devfreqName: String, governor: String): OperationResult = withContext(Dispatchers.IO) {
        val name = safeDeviceName(devfreqName) ?: return@withContext fail(devfreqName, governor, "Invalid devfreq name")
        val gov = governor.trim()
        if (!gov.matches(Regex("[A-Za-z0-9_.-]+"))) return@withContext fail(name, governor, "Invalid governor")
        val base = "/sys/class/devfreq/$name"
        val path = "$base/governor"
        if (!KernelRootRuntime.nodeExists(path)) return@withContext fail(path, gov, "GPU governor node missing")
        val available = KernelRootRuntime.readNode("$base/available_governors")
        if (available.isNotBlank() && gov !in available.split(Regex("\\s+"))) return@withContext fail(path, gov, "Governor is not exposed by kernel")
        writeVerify(path, gov)
    }

    suspend fun setGpuMinMax(devfreqName: String, minHz: Long? = null, maxHz: Long? = null): List<OperationResult> = withContext(Dispatchers.IO) {
        val name = safeDeviceName(devfreqName) ?: return@withContext listOf(fail(devfreqName, "", "Invalid devfreq name"))
        val base = "/sys/class/devfreq/$name"
        val minPath = "$base/min_freq"
        val maxPath = "$base/max_freq"
        val currentMin = KernelRootRuntime.readNode(minPath).toLongOrNull() ?: 0L
        val currentMax = KernelRootRuntime.readNode(maxPath).toLongOrNull() ?: Long.MAX_VALUE
        val targets = LinkedHashMap<String, String>()
        minHz?.let { if (it > 0L) targets[minPath] = it.toString() else return@withContext listOf(fail(minPath, it.toString(), "Invalid minimum")) }
        maxHz?.let { if (it > 0L) targets[maxPath] = it.toString() else return@withContext listOf(fail(maxPath, it.toString(), "Invalid maximum")) }
        if (targets.isEmpty()) return@withContext emptyList()
        val effectiveMin = minHz ?: currentMin
        val effectiveMax = maxHz ?: currentMax
        if (effectiveMin > effectiveMax) return@withContext listOf(fail(base, "$effectiveMin:$effectiveMax", "Invalid frequency range"))
        val order = when {
            minHz != null && minHz > currentMax -> listOf(maxPath, minPath)
            maxHz != null && maxHz < currentMin -> listOf(minPath, maxPath)
            else -> targets.keys.toList()
        }.filter { targets.containsKey(it) }
        return@withContext transactionalWrite(order.map { it to targets.getValue(it) }).operations
    }

    suspend fun setQualcommGpuClockRange(minHz: Long? = null, maxHz: Long? = null): TransactionResult = withContext(Dispatchers.IO) {
        val base = "/sys/class/kgsl/kgsl-3d0"
        // kgsl exposes max_gpuclk (Hz) but usually has no min_gpuclk; the devfreq nodes
        // (Hz) are the portable pair, so prefer whichever actually exists.
        val minPath = firstExistingNode("$base/min_gpuclk", "$base/devfreq/min_freq") ?: "$base/min_gpuclk"
        val maxPath = firstExistingNode("$base/max_gpuclk", "$base/devfreq/max_freq") ?: "$base/max_gpuclk"
        val targets = buildList {
            minHz?.let { if (it > 0L) add(minPath to it.toString()) }
            maxHz?.let { if (it > 0L) add(maxPath to it.toString()) }
        }
        if (targets.isEmpty()) return@withContext TransactionResult(false, error = "No valid Qualcomm GPU clock bound requested")
        val effectiveMin = minHz ?: KernelRootRuntime.readNode(minPath).toLongOrNull() ?: 0L
        val effectiveMax = maxHz ?: KernelRootRuntime.readNode(maxPath).toLongOrNull() ?: Long.MAX_VALUE
        if (effectiveMin > effectiveMax) return@withContext TransactionResult(false, error = "Invalid Qualcomm GPU clock range")
        val ordered = when {
            minHz != null && minHz > effectiveMax -> listOf(maxPath, minPath)
            maxHz != null && maxHz < effectiveMin -> listOf(minPath, maxPath)
            else -> targets.map { it.first }
        }.filter { path -> targets.any { it.first == path } }
        transactionalWrite(ordered.map { path -> path to targets.first { it.first == path }.second })
    }

    suspend fun setQualcommGpuPowerLevelRange(minLevel: Int? = null, maxLevel: Int? = null): TransactionResult = withContext(Dispatchers.IO) {
        val base = "/sys/class/kgsl/kgsl-3d0"
        val minPath = "$base/min_pwrlevel"
        val maxPath = "$base/max_pwrlevel"
        val availableRaw = KernelRootRuntime.readNode("$base/available_pwrlevels")
        val available = Regex("\\d+").findAll(availableRaw).mapNotNull { it.value.toIntOrNull() }.toSet()
        if (minLevel == null && maxLevel == null) return@withContext TransactionResult(false, error = "No Qualcomm GPU power-level bound requested")
        if (available.isNotEmpty()) {
            if (minLevel != null && minLevel !in available) return@withContext TransactionResult(false, error = "Minimum power level is not exposed")
            if (maxLevel != null && maxLevel !in available) return@withContext TransactionResult(false, error = "Maximum power level is not exposed")
        }
        if (minLevel != null && maxLevel != null && minLevel > maxLevel) return@withContext TransactionResult(false, error = "Invalid Qualcomm GPU power-level range")
        val currentMin = KernelRootRuntime.readNode(minPath).toIntOrNull() ?: 0
        val currentMax = KernelRootRuntime.readNode(maxPath).toIntOrNull() ?: Int.MAX_VALUE
        val targets = buildList {
            minLevel?.let { add(minPath to it.toString()) }
            maxLevel?.let { add(maxPath to it.toString()) }
        }
        val ordered = when {
            minLevel != null && minLevel > currentMax -> listOf(maxPath, minPath)
            maxLevel != null && maxLevel < currentMin -> listOf(minPath, maxPath)
            else -> targets.map { it.first }
        }.filter { path -> targets.any { it.first == path } }
        transactionalWrite(ordered.map { path -> path to targets.first { it.first == path }.second })
    }

    suspend fun setIoScheduler(blockDevice: String, scheduler: String): OperationResult = withContext(Dispatchers.IO) {
        val block = safeDeviceName(blockDevice) ?: return@withContext fail(blockDevice, scheduler, "Invalid block device")
        val path = "/sys/block/$block/queue/scheduler"
        val exposed = KernelRootRuntime.readNode(path)
        if (exposed.isBlank()) return@withContext fail(path, scheduler, "Scheduler node missing")
        val normalized = exposed.replace("[", " ").replace("]", " ").split(Regex("\\s+")).filter { it.isNotBlank() }
        if (scheduler !in normalized) return@withContext fail(path, scheduler, "Scheduler is not exposed")
        writeVerify(path, scheduler)
    }

    suspend fun setKernelNode(path: String, value: String, verify: Boolean = true): OperationResult = withContext(Dispatchers.IO) {
        val normalizedPath = KernelNodePolicy.normalizeLegacyPath(path)
        val canonical = KernelNodePolicy.canonical(normalizedPath)
            ?: return@withContext fail(path, value, "Invalid kernel path")
        if (canonical != normalizedPath) return@withContext fail(path, value, "Non-normalized kernel path")
        if (!KernelNodePolicy.isWriteAllowed(canonical)) return@withContext fail(canonical, value, "Write blocked by kernel policy")
        if (!KernelRootRuntime.nodeExists(canonical)) return@withContext fail(canonical, value, "Kernel node does not exist")
        val normalized = value.replace('\u0000', ' ').trim()
        if (normalized.isBlank() || normalized.length > 512) return@withContext fail(canonical, value, "Invalid kernel value")
        if (verify) writeVerify(canonical, normalized) else {
            val ok = KernelRootRuntime.writeNode(canonical, normalized)
            OperationResult(ok, canonical, normalized, if (ok) KernelRootRuntime.readNode(canonical) else "", if (ok) null else "Kernel write failed")
        }
    }

    suspend fun setKernelNodesTransactional(targets: List<Pair<String, String>>): TransactionResult =
        withContext(Dispatchers.IO) { transactionalWrite(targets) }

    fun setKernelNodesTransactionalBlocking(targets: List<Pair<String, String>>): TransactionResult {
        if (onMainThread()) return TransactionResult(false, error = "Blocking kernel API called from main thread; use the suspend API")
        return runBlocking(Dispatchers.IO) { setKernelNodesTransactional(targets) }
    }

    suspend fun readKernelNode(path: String): String = KernelRootRuntime.readNode(path)

    suspend fun processAffinity(pid: Int): String = withContext(Dispatchers.IO) {
        if (pid <= 0) return@withContext ""
        KernelRootRuntime.execute("taskset -pc $pid 2>/dev/null").outputString.trim()
    }

    suspend fun setProcessAffinity(pid: Int, cpuList: String): OperationResult = withContext(Dispatchers.IO) {
        if (pid <= 0 || !cpuList.matches(Regex("[0-9,\\-]+"))) return@withContext fail("pid:$pid", cpuList, "Invalid PID/CPU list")
        val result = KernelRootRuntime.execute("taskset -pc ${shellQuote(cpuList)} $pid 2>/dev/null")
        OperationResult(result.isSuccess, "pid:$pid", cpuList, result.outputString.trim(), result.err.joinToString(" ").ifBlank { null })
    }

    suspend fun moveProcessToCgroup(pid: Int, cgroupPath: String): OperationResult = withContext(Dispatchers.IO) {
        if (pid <= 0) return@withContext fail(cgroupPath, pid.toString(), "Invalid PID")
        val path = KernelNodePolicy.canonical(cgroupPath) ?: return@withContext fail(cgroupPath, pid.toString(), "Invalid cgroup path")
        if (!path.startsWith("/sys/fs/cgroup/")) return@withContext fail(path, pid.toString(), "Only cgroup v2 paths are allowed")
        val procs = if (path.endsWith("/cgroup.procs")) path else "$path/cgroup.procs"
        if (!KernelNodePolicy.isWriteAllowed(procs)) return@withContext fail(procs, pid.toString(), "Cgroup write blocked")
        val result = KernelRootRuntime.writeNode(procs, pid.toString())
        val observed = if (result) KernelRootRuntime.readNode(procs) else ""
        val verified = result && observed.lineSequence().map { it.trim() }.any { it == pid.toString() }
        OperationResult(verified, procs, pid.toString(), observed, when {
            !result -> "cgroup write failed"
            !verified -> "cgroup write verification failed"
            else -> null
        })
    }

    suspend fun configureZram(sizeMb: Int, algorithm: String): ZramResult = withContext(Dispatchers.IO) {
        val safeSize = sizeMb.coerceAtLeast(8)
        val sizeBytes = safeSize.toLong() * 1024L * 1024L
        if (sizeMb != safeSize || sizeMb > 65536) {
            return@withContext ZramResult(false, requestedSizeBytes = sizeBytes, requestedAlgorithm = algorithm, error = "ZRAM size must be 8..65536 MB")
        }
        val algo = algorithm.trim()
        if (!algo.matches(Regex("[A-Za-z0-9._-]+"))) {
            return@withContext ZramResult(false, requestedSizeBytes = sizeBytes, requestedAlgorithm = algo, error = "Invalid ZRAM algorithm")
        }
        if (!KernelRootRuntime.nodeExists("/sys/block/zram0/disksize") || !KernelRootRuntime.nodeExists("/sys/block/zram0/comp_algorithm")) {
            return@withContext ZramResult(false, requestedSizeBytes = sizeBytes, requestedAlgorithm = algo, error = "ZRAM interface unavailable")
        }
        val available = KernelRootRuntime.readNode("/sys/block/zram0/comp_algorithm")
        if (available.isNotBlank() && algo !in available.replace("[", " ").replace("]", " ").split(Regex("\\s+"))) {
            return@withContext ZramResult(false, requestedSizeBytes = sizeBytes, requestedAlgorithm = algo, error = "Compression algorithm is not exposed")
        }
        val previousSize = KernelRootRuntime.readNode("/sys/block/zram0/disksize").toLongOrNull() ?: 0L
        val previousAlgorithm = Regex("\\[([^]]+)]").find(available)?.groupValues?.getOrNull(1).orEmpty()
        val commands = listOf(
            "swapoff /dev/block/zram0 2>/dev/null || true",
            "printf '%s' '1' > '/sys/block/zram0/reset'",
            "printf '%s' '$algo' > '/sys/block/zram0/comp_algorithm'",
            "printf '%s' '$sizeBytes' > '/sys/block/zram0/disksize'",
            "mkswap /dev/block/zram0 2>/dev/null",
            "swapon /dev/block/zram0 2>/dev/null"
        )
        val result = KernelRootRuntime.execute(*commands.toTypedArray())
        if (result.isSuccess) {
            val observedSize = KernelRootRuntime.readNode("/sys/block/zram0/disksize").toLongOrNull() ?: 0L
            val observedAlgo = Regex("\\[([^]]+)]").find(KernelRootRuntime.readNode("/sys/block/zram0/comp_algorithm"))?.groupValues?.getOrNull(1).orEmpty()
            val verified = observedSize == sizeBytes && observedAlgo == algo
            if (verified) return@withContext ZramResult(true, previousSize, previousAlgorithm, sizeBytes, algo)
        }
        val rollbackAttempted = previousSize > 0L && previousAlgorithm.matches(Regex("[A-Za-z0-9._-]+"))
        val rollbackVerified = if (rollbackAttempted) restoreZram(previousSize, previousAlgorithm) else false
        ZramResult(false, previousSize, previousAlgorithm, sizeBytes, algo, rollbackAttempted, rollbackVerified, result.errorString.ifBlank { "ZRAM configuration failed" })
    }

    fun setKernelNodeBlocking(path: String, value: String, verify: Boolean = true): OperationResult {
        if (onMainThread()) return OperationResult(false, path, value, error = "Blocking kernel API called from main thread; use the suspend API")
        return runBlocking(Dispatchers.IO) { setKernelNode(path, value, verify) }
    }

    fun setCpuGovernorBlocking(policy: String, governor: String): OperationResult {
        if (onMainThread()) return OperationResult(false, policy, governor, error = "Blocking kernel API called from main thread; use the suspend API")
        return runBlocking(Dispatchers.IO) { setCpuGovernor(policy, governor) }
    }

    fun setCpuMinMaxBlocking(policy: String, minKHz: Long, maxKHz: Long): List<OperationResult> {
        if (onMainThread()) return listOf(OperationResult(false, policy, "$minKHz:$maxKHz", error = "Blocking kernel API called from main thread; use the suspend API"))
        return runBlocking(Dispatchers.IO) { setCpuMinMax(policy, minKHz, maxKHz) }
    }

    fun setCpuCoreOnlineBlocking(coreId: Int, online: Boolean): OperationResult {
        if (onMainThread()) return OperationResult(false, "cpu$coreId", online.toString(), error = "Blocking kernel API called from main thread; use the suspend API")
        return runBlocking(Dispatchers.IO) { setCpuCoreOnline(coreId, online) }
    }

    fun setQualcommGpuClockRangeBlocking(minHz: Long? = null, maxHz: Long? = null): TransactionResult {
        if (onMainThread()) return TransactionResult(false, error = "Blocking kernel API called from main thread; use the suspend API")
        return runBlocking(Dispatchers.IO) { setQualcommGpuClockRange(minHz, maxHz) }
    }

    fun setQualcommGpuPowerLevelRangeBlocking(minLevel: Int? = null, maxLevel: Int? = null): TransactionResult {
        if (onMainThread()) return TransactionResult(false, error = "Blocking kernel API called from main thread; use the suspend API")
        return runBlocking(Dispatchers.IO) { setQualcommGpuPowerLevelRange(minLevel, maxLevel) }
    }

    fun setIoSchedulerBlocking(blockDevice: String, scheduler: String): OperationResult {
        if (onMainThread()) return OperationResult(false, blockDevice, scheduler, error = "Blocking kernel API called from main thread; use the suspend API")
        return runBlocking(Dispatchers.IO) { setIoScheduler(blockDevice, scheduler) }
    }

    suspend fun setSelinuxEnforcing(enforcing: Boolean): OperationResult = withContext(Dispatchers.IO) {
        val result = KernelRootRuntime.execute("setenforce ${if (enforcing) 1 else 0}")
        val observed = KernelRootRuntime.execute("getenforce 2>/dev/null").outputString.trim()
        OperationResult(result.isSuccess && observed.equals(if (enforcing) "Enforcing" else "Permissive", true), "selinux", enforcing.toString(), observed, if (result.isSuccess) null else result.errorString)
    }

    fun setSelinuxEnforcingBlocking(enforcing: Boolean): OperationResult {
        if (onMainThread()) return OperationResult(false, "selinux", enforcing.toString(), error = "Blocking kernel API called from main thread; use the suspend API")
        return runBlocking(Dispatchers.IO) { setSelinuxEnforcing(enforcing) }
    }

    private suspend fun restoreZram(sizeBytes: Long, algorithm: String): Boolean {
        val result = KernelRootRuntime.execute(
            "swapoff /dev/block/zram0 2>/dev/null || true",
            "printf '%s' '1' > '/sys/block/zram0/reset'",
            "printf '%s' '$algorithm' > '/sys/block/zram0/comp_algorithm'",
            "printf '%s' '$sizeBytes' > '/sys/block/zram0/disksize'",
            "mkswap /dev/block/zram0 2>/dev/null",
            "swapon /dev/block/zram0 2>/dev/null"
        )
        if (!result.isSuccess) return false
        val observedSize = KernelRootRuntime.readNode("/sys/block/zram0/disksize").toLongOrNull() ?: 0L
        return observedSize == sizeBytes
    }

    private suspend fun rollbackVerified(values: Map<String, String>): Boolean {
        if (values.isEmpty()) return true
        val safe = values.filterKeys { KernelNodePolicy.isWriteAllowed(it) }
        if (safe.size != values.size) return false
        return runCatching {
            val outcomes = KernelRootRuntime.writeNodes(safe.keys.toList(), safe.values.toList())
            if (safe.keys.any { outcomes[it] != true }) return@runCatching false
            val observed = KernelRootRuntime.readNodes(safe.keys.toList())
            safe.all { (path, value) -> normalize(observed[path] ?: "") == normalize(value) }
        }.getOrDefault(false)
    }

    private suspend fun transactionalWrite(targets: List<Pair<String, String>>): TransactionResult {
        val ordered = targets
            .map { it.first.trim() to it.second.replace('\u0000', ' ').trim() }
            .distinctBy { it.first }
        if (ordered.isEmpty()) return TransactionResult(false, error = "No kernel targets")

        val invalid = ordered.firstOrNull { (path, value) ->
            val normalizedPath = KernelNodePolicy.normalizeLegacyPath(path)
            val canonical = KernelNodePolicy.canonical(normalizedPath)
            canonical == null || canonical != normalizedPath || !KernelNodePolicy.isWriteAllowed(canonical) || value.isBlank() || value.length > 512
        }
        if (invalid != null) return TransactionResult(false, error = "Invalid or blocked node: ${invalid.first}")

        val original = KernelRootRuntime.readNodes(ordered.map { it.first })
        if (original.size != ordered.size) return TransactionResult(false, error = "Could not capture original kernel state")

        val operations = ArrayList<OperationResult>(ordered.size)
        for ((path, value) in ordered) {
            val op = writeVerify(path, value)
            operations += op
            if (!op.success) {
                val rollback = rollbackVerified(original)
                val detail = if (rollback) "Transaction failed; rollback verified" else "Transaction failed; rollback verification FAILED"
                val failed = op.copy(error = listOfNotNull(op.error, detail).joinToString("; "))
                operations[operations.lastIndex] = failed
                return TransactionResult(false, operations, rollbackAttempted = true, rollbackVerified = rollback, error = failed.error)
            }
        }
        return TransactionResult(true, operations)
    }

    private suspend fun boundedWrite(path: String, value: Int, range: IntRange): OperationResult {
        if (value !in range) return fail(path, value.toString(), "Value outside ${range.first}..${range.last}")
        return writeVerify(path, value.toString())
    }

    private suspend fun writeVerify(path: String, value: String): OperationResult {
        if (!KernelNodePolicy.isWriteAllowed(path)) return fail(path, value, "Write blocked by kernel policy")
        if (!KernelRootRuntime.nodeExists(path)) return fail(path, value, "Kernel node does not exist")
        val normalizedValue = value.replace('\u0000', ' ').trim()
        if (normalizedValue.isEmpty()) return fail(path, value, "Empty kernel value")
        val ok = KernelRootRuntime.writeNode(path, normalizedValue)
        if (!ok) return fail(path, normalizedValue, "Kernel write failed")
        val observed = KernelRootRuntime.readNode(path)
        val matches = verifyMatches(observed, normalizedValue)
        return OperationResult(matches, path, normalizedValue, observed, if (matches) null else "Verification mismatch")
    }

    /**
     * Read-back comparison that tolerates how real vendor nodes behave.
     *
     * A strict `observed == requested` check reported false failures (and, inside
     * transactions, triggered needless rollbacks) for nodes that:
     *  - are write-only / command style (MTK `/proc/ppm/policy_status`, `fix_target_opp_index`,
     *    GED `hal/<node>`): read-back is empty or a different format;
     *  - return a status dump or a selector list ("[mq-deadline] none", multi-line text);
     *  - format numbers differently ("0800" vs "800").
     * A single-token numeric or single-line value that really differs is still a failure,
     * so kernel-side clamping or a silently ignored write is still detected.
     */
    private fun boolToken(v: String): String? = when (v.lowercase()) {
        "1", "y", "yes", "true", "on" -> "1"
        "0", "n", "no", "false", "off" -> "0"
        else -> null
    }

    private fun verifyMatches(observed: String, requested: String): Boolean {
        val obs = normalize(observed)
        val req = normalize(requested)
        if (obs == req) return true
        if (obs.isEmpty()) return true
        val obsNum = obs.toLongOrNull()
        val reqNum = req.toLongOrNull()
        if (obsNum != null && reqNum != null) return obsNum == reqNum
        // Module param bool dicetak kernel sebagai Y/N walau ditulis 1/0.
        val obsBool = boolToken(obs)
        val reqBool = boolToken(req)
        if (obsBool != null && reqBool != null) return obsBool == reqBool
        val tokens = obs.split(Regex("[\\s\\[\\],=:]+")).filter { it.isNotEmpty() }
        if (req in tokens) return true
        if (obs.contains('\n')) return true
        return false
    }

    private fun safeName(name: String, pattern: String): String? {
        val n = name.trim().removePrefix("/")
        return n.takeIf { Regex(pattern).matches(it) }
    }

    private fun safeDeviceName(name: String): String? {
        val n = name.trim().removePrefix("/")
        return n.takeIf { it.matches(Regex("[A-Za-z0-9_.:-]+")) && !it.contains("..") }
    }

    private suspend fun firstExistingNode(vararg paths: String): String? =
        paths.firstOrNull { KernelRootRuntime.nodeExists(it) }

    private fun fail(path: String, requested: String, error: String) = OperationResult(false, path, requested, "", error)
    private fun normalize(value: String): String = value.trim()
    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"
}

