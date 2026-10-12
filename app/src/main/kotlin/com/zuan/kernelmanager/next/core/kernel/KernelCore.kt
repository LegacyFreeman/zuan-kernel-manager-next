/*
 * Copyright (c) 2025 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.core.kernel

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelChildren

/** Single entry point for the new ZKM kernel-management runtime. */
object KernelCore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var initialized = false
    @Volatile private var starting = false

    val root: KernelRootRuntime = KernelRootRuntime
    val capabilities: KernelCapabilityEngine = KernelCapabilityEngine
    val backends: KernelBackendRegistry = KernelBackendRegistry
    val backendEngine: KernelBackendEngine = KernelBackendEngine
    val state: KernelStateEngine = KernelStateEngine
    val profiles = KernelProfileEngine()
    val automation = KernelAutomationEngine(profiles)

    @Synchronized
    fun initialize(context: Context) {
        val appContext = context.applicationContext
        KernelRuntimeContext.context = appContext
        KernelRootRuntime.initialize(appContext)
        if (initialized || starting) return
        starting = true

        // One orchestrator owns startup so RootRuntime, capabilities, profiles,
        // automation persistence, state collection and backend probing share the
        // same lifecycle. All heavy work remains off the caller thread.
        scope.launch {
            var success = false
            for (attempt in 0 until 4) {
                val result = runCatching {
                    automation.attachPersistence(appContext)
                    KernelEngine.initialize(appContext)
                }
                if (result.isSuccess) {
                    success = true
                    break
                }
                if (attempt < 3) kotlinx.coroutines.delay(1500L)
            }
            synchronized(this@KernelCore) {
                initialized = success
                starting = false
            }
        }
    }

    @Synchronized
    fun shutdown() {
        starting = false
        initialized = false
        scope.coroutineContext.cancelChildren()
        KernelEngine.shutdown()
        KernelStateEngine.stop()
        automation.stopPolling()
    }

    fun rescanCapabilities(context: Context) {
        scope.launch {
            runCatching { KernelCapabilityEngine.scan(context.applicationContext) }
        }
    }
}
