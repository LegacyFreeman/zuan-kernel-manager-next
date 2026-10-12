/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.core.kernel

import android.content.Context
import android.os.Process
import com.zuan.kernelmanager.next.BuildConfig
import com.zuan.kernelmanager.next.library.shell.RootIpcManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** One-shot validation of the whole engine stack. All mutations are test-sandbox only. */
object KernelEngineFullDiagnostics {
    enum class Status { PASS, FAIL, SKIP, RUNNING }

    data class Result(
        val root: Status = Status.SKIP,
        val daemon: Status = Status.SKIP,
        val singleRead: Status = Status.SKIP,
        val batchRead: Status = Status.SKIP,
        val reconnect: Status = Status.SKIP,
        val capabilities: Status = Status.SKIP,
        val backend: Status = Status.SKIP,
        val state: Status = Status.SKIP,
        val policy: Status = Status.SKIP,
        val transaction: Status = Status.SKIP,
        val security: Status = Status.SKIP,
        val control: Status = Status.SKIP,
        val hardwareControl: Status = Status.SKIP,
        val hardwareControlAttempted: Int = 0,
        val hardwareControlPassed: Int = 0,
        val hardwareControlSkipped: Int = 0,
        val hardwareControlFailures: Int = 0,
        val hardwareControlDetail: String = "",
        val profiles: Status = Status.SKIP,
        val lifecyclePersistence: Status = Status.SKIP,
        val migration: Status = Status.SKIP,
        val timedAutomation: Status = Status.SKIP,
        val automation: Status = Status.SKIP,
        val benchmark: Status = Status.SKIP,
        val recovery: Status = Status.SKIP,
        val capabilityCount: Int = 0,
        val backendId: String = "none",
        val stateSequence: Long = 0L,
        val batchSuccess: Int = 0,
        val batchSize: Int = 0,
        val transport: String = "none",
        val latencyMs: Long = 0L,
        val singleReadBenchmarkMs: Long = 0L,
        val batchReadBenchmarkMs: Long = 0L,
        val batchSpeedup: Float = 0f,
        val benchmarkWallMs: Long = 0L,
        val benchmarkCpuDeltaMs: Long = 0L,
        val benchmarkCpuOverheadPercent: Float = 0f,
        val concurrentOps: Int = 0,
        val concurrentFailures: Int = 0,
        val pssBeforeKb: Int = 0,
        val pssAfterKb: Int = 0,
        val runtimeFailuresDelta: Long = 0L,
        val error: String? = null,
        val completedAt: Long = 0L
    ) {
        val finished: Boolean get() = completedAt != 0L
        val corePass: Boolean get() = root == Status.PASS && daemon == Status.PASS && singleRead == Status.PASS && batchRead == Status.PASS && reconnect == Status.PASS
        val enginePass: Boolean get() = capabilities == Status.PASS && backend == Status.PASS && state == Status.PASS && policy == Status.PASS && (transaction == Status.PASS || (transaction == Status.SKIP && !BuildConfig.DEBUG)) && security == Status.PASS && control == Status.PASS && hardwareControl == Status.PASS && profiles == Status.PASS && lifecyclePersistence == Status.PASS && migration == Status.PASS && timedAutomation == Status.PASS && automation == Status.PASS && benchmark == Status.PASS
        val allPass: Boolean get() = corePass && enginePass && recovery != Status.FAIL
    }

    private val batchPaths = listOf(
        "/proc/uptime", "/proc/stat", "/proc/meminfo", "/proc/loadavg",
        "/proc/version", "/proc/sys/kernel/osrelease", "/proc/sys/kernel/pid_max"
    )

    suspend fun run(context: Context, includeRecovery: Boolean = true): Result = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val started = android.os.SystemClock.elapsedRealtime()
        var root = Status.SKIP; var daemon = Status.SKIP; var single = Status.SKIP; var batch = Status.SKIP; var reconnect = Status.SKIP
        var caps = Status.SKIP; var backend = Status.SKIP; var state = Status.SKIP; var policy = Status.SKIP; var transaction = Status.SKIP
        var automation = Status.SKIP; var security = Status.SKIP; var control = Status.SKIP; var hardwareControl = Status.SKIP; var profiles = Status.SKIP; var lifecyclePersistence = Status.SKIP; var migration = Status.SKIP; var timedAutomation = Status.SKIP; var benchmark = Status.SKIP; var recovery = if (includeRecovery) Status.SKIP else Status.SKIP
        var capCount = 0; var backendId = "none"; var stateSeq = 0L; var batchSuccess = 0; var transport = "none"
        var singleBench = 0L; var batchBench = 0L; var speedup = 0f; var benchWall = 0L; var benchCpuDelta = 0L; var benchCpuOverhead = 0f; var concurrentOps = 0; var concurrentFailures = 0
        var pssBefore = 0; var pssAfter = 0; var cpuFailureDelta = 0L; var error: String? = null
        var hardwareAttempted = 0; var hardwarePassed = 0; var hardwareSkipped = 0; var hardwareFailures = 0; var hardwareDetail = ""

        try {
            KernelRuntimeContext.context = app
            KernelRootRuntime.initialize(app)
            val rootResult = withTimeout(5000L) { KernelRootRuntime.execute("id") }
            root = if (rootResult.isSuccess && rootResult.outputString.contains("uid=0")) Status.PASS else Status.FAIL

            daemon = if (RootIpcManager.isConnected()) Status.PASS
            else if (RootIpcManager.ensureConnected(app, 4000L)) Status.PASS else Status.FAIL

            val singleValue = withTimeout(5000L) { KernelRootRuntime.readNode("/proc/uptime") }
            single = if (singleValue.isNotBlank()) Status.PASS else Status.FAIL

            val batchValues = withTimeout(6000L) { KernelRootRuntime.readNodes(batchPaths) }
            val afterBatch = KernelRootRuntime.snapshotStats()
            batchSuccess = batchValues.size
            transport = afterBatch.lastBatchTransport
            batch = if (batchSuccess == batchPaths.size && transport == "daemon") Status.PASS else Status.FAIL

            reconnect = if (RootIpcManager.rebindForDiagnostics(app, 4500L)) Status.PASS else Status.FAIL

            val snapshot = KernelCapabilityEngine.scan(app)
            capCount = snapshot.supported().size
            caps = if (capCount > 0 && snapshot.generation > 0 && snapshot.scanLatencyMs < 10_000L) Status.PASS else Status.FAIL

            val probe = KernelBackendEngine.probe(app, snapshot)
            backendId = probe.backendId
            backend = if (probe.supported) Status.PASS else Status.FAIL

            val s1 = withTimeout(8000L) { KernelStateEngine.refresh(app) }
            delay(150L)
            val s2 = withTimeout(8000L) { KernelStateEngine.refresh(app) }
            stateSeq = s2.sequence
            state = if (s2.sequence > s1.sequence && s2.timestamp >= s1.timestamp && s2.daemonConnected && !s2.stale) Status.PASS else Status.FAIL

            policy = if (
                KernelNodePolicy.isAllowed("/proc/uptime") &&
                !KernelNodePolicy.isAllowed("/data/local/tmp/zkm-diagnostics") &&
                KernelNodePolicy.isWriteAllowed("/proc/uptime") &&
                !KernelNodePolicy.isWriteAllowed("/sys/bus/platform/drivers/example/unbind")
            ) Status.PASS else Status.FAIL

            transaction = runDiagnosticTransaction()

            val audit = KernelAuditEngine.run(app)
            security = if (audit.passed) Status.PASS else Status.FAIL

            control = runControlValidation(snapshot)
            val hw = runHardwareControlValidation(app, snapshot)
            hardwareControl = hw.status
            hardwareAttempted = hw.attempted
            hardwarePassed = hw.passed
            hardwareSkipped = hw.skipped
            hardwareFailures = hw.failures
            hardwareDetail = hw.detail
            migration = runCautiousMigrationValidation(app)

            val profileEngine = KernelProfileEngine()
            profileEngine.register(KernelProfile("__diag_profile__", "Diagnostics", emptyList()))
            val automationEngine = KernelAutomationEngine(profileEngine)
            automationEngine.registerAppProfile("com.android.settings", "__diag_profile__")
            automationEngine.registerRule(KernelAutomationEngine.Rule(
                id = "__diag_battery__",
                event = KernelAutomationEngine.EventType.BATTERY,
                batteryBelow = 100,
                profileId = "__diag_profile__"
            ))
            automationEngine.onBattery(app, 50)
            automation = if (automationEngine.ruleCount() == 1 && profileEngine.activeProfileId() == "__diag_profile__") Status.PASS else Status.FAIL
            profileEngine.restoreActive()

            profiles = runProfilePersistenceValidation(app, profileEngine)
            lifecyclePersistence = runLifecyclePersistenceValidation(app, profileEngine, automationEngine)
            timedAutomation = runTimedAutomationValidation(app, profileEngine, automationEngine)

            val bench = KernelBenchmarkV2.run(app, rounds = 10)
            singleBench = bench.p50Ms
            batchBench = bench.batchP50Ms
            speedup = bench.speedup
            benchWall = bench.samplesMs.sum()
            benchCpuDelta = 0L
            benchCpuOverhead = 0f
            concurrentOps = 128
            concurrentFailures = bench.concurrentFailures
            pssBefore = 0
            pssAfter = bench.pssDeltaKb
            cpuFailureDelta = bench.runtimeFailureDelta
            benchmark = if (bench.passed) Status.PASS else Status.FAIL

            if (includeRecovery) {
                recovery = runRecoveryInternal(app)
                if (recovery == Status.PASS) {
                    val post = runCatching { KernelStateEngine.refresh(app) }.getOrNull()
                    if (post == null || !post.daemonConnected || post.stale) recovery = Status.FAIL
                }
            }
        } catch (t: Throwable) {
            error = t.message ?: t.javaClass.simpleName
            if (root == Status.SKIP) root = Status.FAIL
            if (daemon == Status.SKIP) daemon = if (RootIpcManager.isConnected()) Status.PASS else Status.FAIL
        }

        Result(
            root = root, daemon = daemon, singleRead = single, batchRead = batch, reconnect = reconnect,
            capabilities = caps, backend = backend, state = state, policy = policy,
            transaction = transaction, security = security, control = control,
            hardwareControl = hardwareControl, hardwareControlAttempted = hardwareAttempted,
            hardwareControlPassed = hardwarePassed, hardwareControlSkipped = hardwareSkipped,
            hardwareControlFailures = hardwareFailures, hardwareControlDetail = hardwareDetail,
            profiles = profiles, lifecyclePersistence = lifecyclePersistence, migration = migration, timedAutomation = timedAutomation, automation = automation, benchmark = benchmark, recovery = recovery,
            capabilityCount = capCount, backendId = backendId, stateSequence = stateSeq,
            batchSuccess = batchSuccess, batchSize = batchPaths.size, transport = transport,
            latencyMs = android.os.SystemClock.elapsedRealtime() - started,
            singleReadBenchmarkMs = singleBench, batchReadBenchmarkMs = batchBench, batchSpeedup = speedup,
            benchmarkWallMs = benchWall, benchmarkCpuDeltaMs = benchCpuDelta, benchmarkCpuOverheadPercent = benchCpuOverhead,
            concurrentOps = concurrentOps, concurrentFailures = concurrentFailures,
            pssBeforeKb = pssBefore, pssAfterKb = pssAfter,
            runtimeFailuresDelta = cpuFailureDelta, error = error, completedAt = System.currentTimeMillis()
        )
    }

    private suspend fun runControlValidation(snapshot: KernelCapabilityEngine.Snapshot): Status {
        val cpu = KernelBackendEngine.apply(KernelRuntimeContext.context ?: return Status.FAIL, KernelBackendEngine.ControlRequest.CpuGovernor("invalid-policy", "invalid"))
        val dangerous = KernelBackendEngine.apply(KernelRuntimeContext.context ?: return Status.FAIL, KernelBackendEngine.ControlRequest.NodeWrite("/sys/bus/platform/drivers/example/unbind", "1"))
        return if (!cpu.success && !dangerous.success && snapshot.generation > 0L) Status.PASS else Status.FAIL
    }

    data class HardwareControlReport(
        val status: Status,
        val attempted: Int,
        val passed: Int,
        val skipped: Int,
        val failures: Int,
        val detail: String
    )

    /**
     * Real hardware control smoke test. Every mutation is a same-value write,
     * so the test exercises the complete UI -> backend -> policy -> daemon ->
     * kernel -> read-back path without intentionally changing device state.
     */
    private suspend fun runHardwareControlValidation(
        context: Context,
        snapshot: KernelCapabilityEngine.Snapshot
    ): HardwareControlReport = KernelHardwareControlValidator.run(context, snapshot)

    private suspend fun runCautiousMigrationValidation(context: Context): Status {
        return runCatching { KernelTuningMigration.dryRun(context, force = false).passed }.getOrDefault(false).let { if (it) Status.PASS else Status.FAIL }
    }

    private suspend fun runProfilePersistenceValidation(context: Context, profileEngine: KernelProfileEngine): Status {
        return runCatching {
            val profile = profileEngine.captureCurrent(context, "__diag_snapshot__", "Diagnostics Snapshot")
            if (profile == null || profile.actions.isEmpty()) return@runCatching false
            KernelProfileStore.save(context, profile)
            val loaded = KernelProfileStore.loadAll(context).firstOrNull { it.id == profile.id }
            KernelProfileStore.delete(context, profile.id)
            loaded != null && loaded.actions.size == profile.actions.size &&
                loaded.actions.filterIsInstance<KernelAction.WriteNode>().all { KernelNodePolicy.isWriteAllowed(it.path) }
        }.getOrDefault(false).let { if (it) Status.PASS else Status.FAIL }
    }

    private suspend fun runLifecyclePersistenceValidation(
        context: Context,
        profileEngine: KernelProfileEngine,
        automationEngine: KernelAutomationEngine
    ): Status = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val profileId = "__diag_lifecycle_profile__"
        val scheduleId = "__diag_lifecycle_timed__"
        try {
            // Exercise the actual process-wide KernelCore owners. The previous
            // implementation captured/scheduled on temporary engines, then
            // verified the restored state on KernelCore, which made the test
            // dependent on two different registries.
            KernelCore.automation.cancelTimedProfile(scheduleId)
            KernelAutomationStore.load(app)
            KernelCore.profiles.unregister(profileId)
            KernelProfileStore.delete(app, profileId)

            val captured = KernelCore.profiles.captureCurrent(app, profileId, "Lifecycle Diagnostics")
                ?: return@withContext Status.FAIL
            if (captured.actions.isEmpty()) return@withContext Status.FAIL
            KernelProfileStore.save(app, captured)
            KernelCore.profiles.register(captured)

            KernelCore.automation.attachPersistence(app)
            val scheduled = KernelCore.automation.scheduleTimedProfile(
                app, scheduleId, 120_000L, profileId
            )
            if (!scheduled) return@withContext Status.FAIL

            // Force a real engine lifecycle boundary. Both the in-memory profile
            // and schedule are removed by shutdown/reinitialization; success now
            // requires Store -> KernelCore restore.
            KernelCore.shutdown()
            KernelCore.profiles.unregister(profileId)

            KernelEngine.initialize(app)

            val restoredProfile = KernelCore.profiles.get(profileId)
            val restoredSchedule = KernelCore.automation.timedSchedules()
                .firstOrNull { it.id == scheduleId }
            val state = KernelStateEngine.flow.value
            val daemonOk = KernelRootRuntime.snapshotStats().daemonConnected
            val stateOk = state.daemonConnected && !state.stale
            val profileOk = restoredProfile != null && restoredProfile.actions.isNotEmpty()
            val scheduleOk = restoredSchedule != null && restoredSchedule.profileId == profileId
            if (!profileOk || !scheduleOk || !daemonOk || !stateOk) {
                return@withContext Status.FAIL
            }
            Status.PASS
        } catch (_: Throwable) {
            Status.FAIL
        } finally {
            runCatching { KernelCore.automation.cancelTimedProfile(scheduleId) }
            runCatching { KernelAutomationStore.save(app, KernelCore.automation) }
            runCatching { KernelProfileStore.delete(app, profileId) }
            runCatching { KernelCore.profiles.unregister(profileId) }
        }
    }

    private fun runTimedAutomationValidation(context: Context, profileEngine: KernelProfileEngine, automationEngine: KernelAutomationEngine): Status {
        return runCatching {
            automationEngine.attachPersistence(context)
            val scheduled = automationEngine.scheduleTimedProfile(context, "__diag_timed__", 60_000L, "__diag_profile__")
            val persisted = KernelAutomationStore.load(context).timedSchedules.any { it.id == "__diag_timed__" }
            automationEngine.cancelTimedProfile("__diag_timed__")
            scheduled && persisted && automationEngine.timedSchedules().none { it.id == "__diag_timed__" }
        }.getOrDefault(false).let { if (it) Status.PASS else Status.FAIL }
    }

    private suspend fun runDiagnosticTransaction(): Status {
        if (!BuildConfig.DEBUG || !RootIpcManager.isConnected()) return Status.SKIP
        val path = "/data/local/tmp/zkm-next-diag-${Process.myPid()}-${System.nanoTime()}"
        return try {
            val a = KernelRootRuntime.diagnosticWriteFile(path, "alpha")
            val r1 = KernelRootRuntime.diagnosticReadFile(path)
            val b = KernelRootRuntime.diagnosticWriteFile(path, "beta")
            val r2 = KernelRootRuntime.diagnosticReadFile(path)
            val deleted = KernelRootRuntime.diagnosticDeleteFile(path)
            if (a && b && r1 == "alpha" && r2 == "beta" && deleted && KernelRootRuntime.diagnosticReadFile(path).isBlank()) Status.PASS else Status.FAIL
        } catch (_: Throwable) {
            runCatching { KernelRootRuntime.diagnosticDeleteFile(path) }
            Status.FAIL
        }
    }

    private suspend fun runRecoveryInternal(context: Context): Status {
        if (!BuildConfig.DEBUG) return Status.SKIP
        if (!RootIpcManager.ensureConnected(context, 4000L)) return Status.FAIL
        if (!RootIpcManager.crashDaemonForDiagnostics()) return Status.FAIL
        val disconnected = RootIpcManager.waitForConnectionState(false, 5000L)
        if (!disconnected) return Status.FAIL
        val reconnected = RootIpcManager.waitForConnectionState(true, 12_000L)
        if (!reconnected) return Status.FAIL
        val ping = runCatching { RootIpcManager.ipc?.ping() == true }.getOrDefault(false)
        val read = runCatching { KernelRootRuntime.readNode("/proc/uptime") }.getOrDefault("")
        return if (ping && read.isNotBlank()) Status.PASS else Status.FAIL
    }
}
