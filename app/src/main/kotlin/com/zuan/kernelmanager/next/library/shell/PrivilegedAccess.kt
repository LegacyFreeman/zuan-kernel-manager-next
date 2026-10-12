/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.library.shell

import com.zuan.kernelmanager.next.core.mode.WorkingMode
import com.zuan.kernelmanager.next.core.mode.WorkingModeManager
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * One place that answers "can I run a privileged command right now?" for the active
 * [WorkingMode], plus an *epoch* counter.
 *
 * The epoch is bumped whenever the privilege situation changes (Shizuku binder arrives / dies,
 * permission granted, working mode switched). Anything that cached "this node is unreadable"
 * compares against the epoch and throws that negative cache away - this is what stops the
 * dashboard from staying at 0 for the whole session when Shizuku connects a bit late.
 */
object PrivilegedAccess {
    private val _epoch = AtomicInteger(0)
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    val epoch: Int get() = _epoch.get()

    fun bumpEpoch() {
        _epoch.incrementAndGet()
        listeners.forEach { runCatching { it() } }
    }

    fun addEpochListener(listener: () -> Unit) { listeners += listener }
    fun removeEpochListener(listener: () -> Unit) { listeners -= listener }

    /** The active mode is allowed to try shell commands at all (Basic never does). */
    fun shellWanted(): Boolean = WorkingModeManager.current.hasShell

    /** A shell command can be expected to succeed right now. */
    fun shellUsable(): Boolean = when (WorkingModeManager.current) {
        WorkingMode.BASIC -> false
        WorkingMode.ADB -> ShizukuShell.isGranted() || AxeronShell.isGranted()
        WorkingMode.ROOT -> true
    }

    /**
     * sysfs / proc bisa ditulis: Root penuh, ATAU Shizuku/Axeron yang server-nya berjalan sebagai root (uid 0).
     * Shizuku / Axeron murni ADB (uid 2000) -> false: governor/frekuensi/renice tidak bisa dipakai.
     * Memakai status yang di-cache, jadi aman dipanggil dari Compose.
     */
    fun rootEquivalent(): Boolean = when (WorkingModeManager.current) {
        WorkingMode.ROOT -> true
        WorkingMode.ADB -> (ShizukuShell.state.value == ShizukuShell.State.READY && ShizukuShell.cachedServerUid() == 0) ||
                           (AxeronShell.state.value == AxeronShell.State.READY && AxeronShell.cachedServerUid() == 0)
        WorkingMode.BASIC -> false
    }

    /** Short label for UI, e.g. "Shizuku (ADB)" / "Axeron (ADB)" / "Root" / "Basic". */
    fun label(): String = when (WorkingModeManager.current) {
        WorkingMode.BASIC -> "Basic"
        WorkingMode.ROOT -> "Root"
        WorkingMode.ADB -> when {
            ShizukuShell.isGranted() -> when (ShizukuShell.cachedServerUid()) {
                0 -> "Shizuku (root)"
                else -> "Shizuku (ADB)"
            }
            AxeronShell.isGranted() -> when (AxeronShell.cachedServerUid()) {
                0 -> "Axeron (root)"
                else -> "Axeron (ADB)"
            }
            ShizukuShell.isRunning() -> "Shizuku"
            AxeronShell.isRunning() -> "Axeron (AXM)"
            else -> "ADB"
        }
    }
}
