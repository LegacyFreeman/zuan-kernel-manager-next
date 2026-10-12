/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.core.kernel

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Read-only production hardening audit for the kernel engine stack. */
object KernelAuditEngine {
    data class Result(
        val rootConnected: Boolean,
        val policyIsolation: Boolean,
        val destructiveWritesBlocked: Boolean,
        val cgroupWriteScope: Boolean,
        val profilesSafe: Boolean,
        val automationSafe: Boolean,
        val selinuxState: String,
        val warnings: List<String> = emptyList()
    ) {
        val passed: Boolean get() = rootConnected && policyIsolation && destructiveWritesBlocked && cgroupWriteScope && profilesSafe && automationSafe
    }

    suspend fun run(context: Context): Result = withContext(Dispatchers.IO) {
        val warnings = mutableListOf<String>()
        val runtime = KernelRootRuntime.snapshotStats()
        val profilesSafe = KernelCore.profiles.all().all { profile ->
            profile.actions.filterIsInstance<KernelAction.WriteNode>().all { KernelNodePolicy.isWriteAllowed(it.path) }
        }
        val automationSafe = KernelCore.automation.rules().all { rule ->
            rule.profileId.isNotBlank() && rule.profileId.let { KernelCore.profiles.get(it) != null }
        }
        val selinux = KernelRootRuntime.readNode("/sys/fs/selinux/enforce").ifBlank { "unknown" }
        if (selinux == "0") warnings += "SELinux reports permissive mode."
        if (runtime.fallbackCalls > 0L) warnings += "RootRuntime has used shell fallback ${runtime.fallbackCalls} times."
        if (runtime.failures > 0L) warnings += "RootRuntime reports ${runtime.failures} failures."
        Result(
            rootConnected = runtime.daemonConnected,
            policyIsolation = KernelNodePolicy.isAllowed("/sys/fs/cgroup/cgroup.controllers") &&
                !KernelNodePolicy.isAllowed("/data/local/tmp/zkm-audit"),
            destructiveWritesBlocked = !KernelNodePolicy.isWriteAllowed("/sys/bus/platform/drivers/example/unbind") &&
                !KernelNodePolicy.isWriteAllowed("/sys/kernel/debug/example") &&
                !KernelNodePolicy.isWriteAllowed("/proc/sys/kernel/sysrq"),
            cgroupWriteScope = KernelNodePolicy.isWriteAllowed("/sys/fs/cgroup/test/cgroup.procs") &&
                !KernelNodePolicy.isWriteAllowed("/sys/fs/cgroup/test/cpu.weight"),
            profilesSafe = profilesSafe,
            automationSafe = automationSafe,
            selinuxState = selinux,
            warnings = warnings
        )
    }
}
