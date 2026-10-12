/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.library.shell

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.zuan.kernelmanager.next.core.kernel.KernelRuntimeContext
import frb.axeron.api.Axeron
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ADB-level (shell uid 2000) executor used by the "ADB mode" via Axeron / AxManager (AXM).
 *
 * Commands run through the Axeron server (frb.axeron.server / AxManager).
 * No root and no `su` prompt is ever involved unless Axeron itself is running as root.
 */
object AxeronShell {
    private const val TAG = "ZkmAxeronShell"
    private const val DEFAULT_TIMEOUT_MS = 8000L
    private const val MAX_PARALLEL = 3

    enum class State { NOT_RUNNING, NEEDS_PERMISSION, DENIED, READY }

    private val _state = MutableStateFlow(State.NOT_RUNNING)
    val state: StateFlow<State> = _state.asStateFlow()

    private val registered = AtomicBoolean(false)
    @Volatile private var permissionDenied = false
    private val gate = Semaphore(MAX_PARALLEL, true)

    /** Call once from Application.onCreate. Safe to call many times. */
    fun registerListeners(context: Context) {
        if (!registered.compareAndSet(false, true)) return
        val app = context.applicationContext
        try {
            Axeron.addBinderReceivedListenerSticky(Axeron.OnBinderReceivedListener {
                permissionDenied = false
                refreshState()
                PrivilegedAccess.bumpEpoch()
                if (isGranted()) AxeronProvisioner.onReady(app)
            })
            Axeron.addBinderDeadListener(Axeron.OnBinderDeadListener {
                refreshState()
                PrivilegedAccess.bumpEpoch()
            })
        } catch (t: Throwable) {
            registered.set(false)
            Log.w(TAG, "registerListeners failed", t)
        }
        refreshState()
    }

    @Volatile private var uidCache = -1

    /** uid server Axeron dari cache (diperbarui di [refreshState]); -1 = belum diketahui. */
    fun cachedServerUid(): Int = uidCache

    fun refreshState() {
        uidCache = serverUid()
        _state.value = when {
            !isRunning() -> State.NOT_RUNNING
            isGranted() -> State.READY
            permissionDenied -> State.DENIED
            else -> State.NEEDS_PERMISSION
        }
    }

    /** The Axeron server is alive (Axeron app / axeron_server started). */
    fun isRunning(): Boolean = try {
        Axeron.pingBinder()
    } catch (_: Throwable) {
        false
    }

    /** Server alive AND this app was granted permission in Axeron. */
    fun isGranted(): Boolean = try {
        isRunning() && !Axeron.getAxeronInfo().isNeedExtraStep()
    } catch (_: Throwable) {
        false
    }

    /** 2000 = started through adb (no root), 0 = Axeron itself runs as root, -1 = unknown. */
    fun serverUid(): Int = try {
        if (!isRunning()) -1
        else if (uidCache >= 0) uidCache
        else {
            val res = exec(arrayOf("id -u"), 1500L)
            if (res.isSuccess) {
                val parsed = res.out.firstOrNull()?.trim()?.toIntOrNull() ?: 2000
                uidCache = parsed
                parsed
            } else 2000
        }
    } catch (_: Throwable) {
        -1
    }

    /** Ask the Axeron server for permission by launching AxManager. Safe to call anytime. */
    fun requestPermission(context: Context? = null) {
        try {
            val ctx = context ?: KernelRuntimeContext.context ?: return
            val intent = ctx.packageManager.getLaunchIntentForPackage("frb.axeron.manager")
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "requestPermission failed", t)
        }
    }

    /**
     * Blocks (worker thread only) until Axeron is running + granted, or the timeout elapses.
     */
    fun awaitReady(timeoutMs: Long): Boolean {
        if (isGranted()) return true
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            try { Thread.sleep(60L) } catch (_: InterruptedException) { return isGranted() }
            if (isGranted()) {
                refreshState()
                return true
            }
        }
        return isGranted()
    }

    /** Blocking. Call from a worker thread only. */
    fun exec(commands: Array<out String>, timeoutMs: Long = DEFAULT_TIMEOUT_MS): SuResult {
        if (commands.isEmpty()) return SuResult(true, emptyList(), emptyList(), 0)
        if (!isGranted() && !awaitReady(1500L)) {
            refreshState()
            return SuResult(
                false, emptyList(),
                listOf("ADB mode needs Axeron (AXM) running with permission granted"), -1
            )
        }

        if (!gate.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
            return SuResult(false, emptyList(), listOf("ADB shell busy (Axeron)"), -1)
        }
        try {
            val script = commands.joinToString("\n")
            var result = runOnce(script, timeoutMs)
            // Binder died / was re-created between the permission check and the call: retry once.
            if (result.code == -3 && awaitReady(1200L)) result = runOnce(script, timeoutMs)
            return if (result.code == -3) result.copy(code = -1) else result
        } finally {
            gate.release()
        }
    }

    /** code == -3 marks a retryable transport failure. */
    private fun runOnce(script: String, timeoutMs: Long): SuResult {
        var process: Process? = null
        return try {
            process = Axeron.newProcess(arrayOf("sh", "-c", script))
            if (process == null) {
                return SuResult(false, emptyList(), listOf("Failed to create Axeron process"), -1)
            }
            runCatching { process.outputStream.close() }

            val errLines = ArrayList<String>()
            val outLines = ArrayList<String>()
            val errReader = Thread {
                runCatching {
                    BufferedReader(InputStreamReader(process.errorStream)).useLines { l ->
                        l.forEach { synchronized(errLines) { errLines.add(it) } }
                    }
                }
            }.apply { isDaemon = true; start() }
            val outReader = Thread {
                runCatching {
                    BufferedReader(InputStreamReader(process.inputStream)).useLines { l ->
                        l.forEach { synchronized(outLines) { outLines.add(it) } }
                    }
                }
            }.apply { isDaemon = true; start() }

            val exitHolder = java.util.concurrent.atomic.AtomicInteger(Int.MIN_VALUE)
            val waiter = Thread {
                try { exitHolder.set(process.waitFor()) } catch (_: Throwable) { }
            }.apply { isDaemon = true; name = "ZKM-AxeronWait"; start() }
            waiter.join(timeoutMs)
            if (exitHolder.get() == Int.MIN_VALUE) {
                runCatching { process.destroy() }
                waiter.join(300)
                return SuResult(false, synchronized(outLines) { outLines.toList() }, listOf("ADB shell timeout (Axeron)"), -1)
            }
            outReader.join(2000)
            errReader.join(1000)
            val code = exitHolder.get()
            SuResult(
                code == 0,
                synchronized(outLines) { outLines.toList() },
                synchronized(errLines) { errLines.toList() },
                code
            )
        } catch (t: Throwable) {
            val cause = t.cause ?: t
            Log.w(TAG, "Axeron exec failed", cause)
            runCatching { process?.destroy() }
            val transport = cause is android.os.RemoteException || cause is IllegalStateException ||
                cause.javaClass.simpleName.contains("Dead", true)
            SuResult(
                false, emptyList(),
                listOf(cause.message ?: cause.javaClass.simpleName),
                if (transport) -3 else -1
            )
        }
    }
}
