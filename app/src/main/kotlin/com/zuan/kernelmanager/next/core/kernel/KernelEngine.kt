/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.core.kernel

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


/** Process-wide orchestrator for the completed kernel engine stack. */
object KernelEngine {
    data class Status(
        val initialized: Boolean,
        val daemonConnected: Boolean,
        val capabilityGeneration: Long,
        val capabilityCount: Int,
        val vendor: KernelCapabilityEngine.Vendor,
        val backendId: String,
        val stateSequence: Long,
        val stateHealthy: Boolean,
        val automationRules: Int,
        val activeProfile: String?
    )

    @Volatile private var initialized = false
    private var stateBridgeJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initMutex = Mutex()

    suspend fun initialize(context: Context) = initMutex.withLock { withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (initialized && KernelRootRuntime.snapshotStats().daemonConnected && KernelStateEngine.isRunning()) return@withContext
        KernelRuntimeContext.context = app
        KernelRootRuntime.initialize(app)
        KernelCapabilityEngine.scan(app)
        KernelProfileStore.loadAll(app).forEach { KernelCore.profiles.register(it) }
        KernelCore.automation.attachPersistence(app)
        KernelCore.automation.restorePersisted(app)
        KernelStateEngine.refresh(app)
        KernelStateEngine.start(app)
        stateBridgeJob?.cancel()
        stateBridgeJob = scope.launch {
            KernelStateEngine.flow.collectLatest { state ->
                if (!isActive || state.timestamp == 0L) return@collectLatest
                runCatching { KernelCore.automation.onBattery(app, state.batteryPercent) }
                if (state.maxThermalC > 0f) {
                    runCatching { KernelCore.automation.onThermal(app, state.maxThermalC) }
                }
            }
        }
        if (KernelCore.automation.ruleCount() > 0 || KernelCore.automation.appProfilesSnapshot().isNotEmpty()) {
            KernelCore.automation.startPolling(app)
        }
        initialized = true
    } }

    fun shutdown() {
        stateBridgeJob?.cancel()
        stateBridgeJob = null
        scope.coroutineContext.cancelChildren()
        initialized = false
    }

    suspend fun rescan(context: Context): KernelCapabilityEngine.Snapshot =
        KernelCapabilityEngine.scan(context.applicationContext)

    suspend fun status(context: Context): Status = withContext(Dispatchers.IO) {
        val snapshot = KernelCapabilityEngine.current()
        val state = KernelStateEngine.flow.value
        Status(
            initialized = initialized,
            daemonConnected = KernelRootRuntime.snapshotStats().daemonConnected,
            capabilityGeneration = snapshot.generation,
            capabilityCount = snapshot.supported().size,
            vendor = snapshot.vendor,
            backendId = KernelBackendRegistry.select(snapshot).id,
            stateSequence = state.sequence,
            stateHealthy = state.healthy && !state.stale,
            automationRules = KernelCore.automation.ruleCount(),
            activeProfile = KernelCore.profiles.activeProfileId()
        )
    }
}
