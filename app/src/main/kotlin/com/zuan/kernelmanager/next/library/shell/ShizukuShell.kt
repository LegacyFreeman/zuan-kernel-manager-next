/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.library.shell

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ADB-level (shell uid 2000) executor used by the "ADB mode".
 *
 * Commands run through the Shizuku server (started via wireless debugging / `adb shell`).
 * No root and no `su` prompt is ever involved.
 *
 * Changes vs. the old version:
 *  - Binder / permission listeners keep [state] live and bump [PrivilegedAccess.epoch] so every
 *    cache that remembered "this path is unreadable" is dropped the moment Shizuku comes online.
 *  - [awaitReady] lets services (FPS overlay, notification monitors, boot receiver) wait for the
 *    binder instead of failing in the first second after process start.
 *  - Commands are NOT serialised any more (small semaphore instead) and one transparent retry is
 *    done when the binder died mid-call.
 */
object ShizukuShell {
    private const val TAG = "ZkmShizukuShell"
    const val PERMISSION_REQUEST_CODE = 4107
    private const val DEFAULT_TIMEOUT_MS = 8000L
    private const val MAX_PARALLEL = 3

    enum class State { NOT_RUNNING, NEEDS_PERMISSION, DENIED, READY }

    private val _state = MutableStateFlow(State.NOT_RUNNING)
    val state: StateFlow<State> = _state.asStateFlow()

    private val registered = AtomicBoolean(false)
    @Volatile private var permissionDenied = false
    private val gate = Semaphore(MAX_PARALLEL, true)

    /** Shizuku keeps newProcess private in API 13; the reflective handle is resolved once. */
    private val newProcessMethod: Method? by lazy {
        runCatching {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
        }.onFailure { Log.w(TAG, "Shizuku.newProcess not reachable", it) }.getOrNull()
    }

    /** Call once from Application.onCreate. Safe to call many times. */
    fun registerListeners(context: Context) {
        if (!registered.compareAndSet(false, true)) return
        val app = context.applicationContext
        runCatching {
            Shizuku.addBinderReceivedListenerSticky {
                permissionDenied = false
                refreshState()
                PrivilegedAccess.bumpEpoch()
                if (isGranted()) ShizukuProvisioner.onReady(app)
            }
            Shizuku.addBinderDeadListener {
                refreshState()
                PrivilegedAccess.bumpEpoch()
            }
            Shizuku.addRequestPermissionResultListener { _, grantResult ->
                permissionDenied = grantResult != PackageManager.PERMISSION_GRANTED
                refreshState()
                PrivilegedAccess.bumpEpoch()
                if (!permissionDenied) ShizukuProvisioner.onReady(app)
            }
        }.onFailure {
            registered.set(false)
            Log.w(TAG, "registerListeners failed", it)
        }
        refreshState()
    }

    @Volatile private var uidCache = -1

    /** uid server Shizuku dari cache (diperbarui di [refreshState]); -1 = belum diketahui. */
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

    /** The Shizuku server is alive (Shizuku app started). */
    fun isRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    /** Server alive AND this app was granted permission. */
    fun isGranted(): Boolean = try {
        isRunning() && !Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    /** 2000 = started through adb (no root), 0 = Shizuku itself runs as root, -1 = unknown. */
    fun serverUid(): Int = try {
        if (isRunning()) Shizuku.getUid() else -1
    } catch (_: Throwable) {
        -1
    }

    /** Ask the Shizuku server for permission. Safe to call when Shizuku is not running. */
    fun requestPermission() {
        try {
            if (isRunning() && !Shizuku.isPreV11() && !isGranted()) {
                permissionDenied = false
                Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "requestPermission failed", t)
        }
    }

    /**
     * Blocks (worker thread only) until Shizuku is running + granted, or the timeout elapses.
     * Needed because the binder is delivered asynchronously after process start.
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
                listOf("ADB mode needs Shizuku running with permission granted"), -1
            )
        }
        val method = newProcessMethod
            ?: return SuResult(false, emptyList(), listOf("Shizuku process API unavailable"), -1)

        if (!gate.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
            return SuResult(false, emptyList(), listOf("ADB shell busy"), -1)
        }
        try {
            val script = commands.joinToString("\n")
            var result = runOnce(method, script, timeoutMs)
            // Binder died / was re-created between the permission check and the call: retry once.
            if (result.code == -3 && awaitReady(1200L)) result = runOnce(method, script, timeoutMs)
            return if (result.code == -3) result.copy(code = -1) else result
        } finally {
            gate.release()
        }
    }

    /** code == -3 marks a retryable transport failure. */
    private fun runOnce(method: Method, script: String, timeoutMs: Long): SuResult {
        var process: Process? = null
        return try {
            process = method.invoke(null, arrayOf("sh", "-c", script), null, null) as Process
            runCatching { process.outputStream.close() } // some tools block on stdin otherwise

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

            // NOT process.waitFor(timeout, unit): its default Java implementation polls exitValue()
            // and expects IllegalThreadStateException while running, but over Shizuku's binder that
            // exception arrives as a plain IllegalArgumentException("process hasn't exited"),
            // so every command that was still running at the first poll failed.
            // The blocking waitFor() is a clean binder call; a watchdog join gives the timeout.
            val exitHolder = java.util.concurrent.atomic.AtomicInteger(Int.MIN_VALUE)
            val waiter = Thread {
                try { exitHolder.set(process.waitFor()) } catch (_: Throwable) { }
            }.apply { isDaemon = true; name = "ZKM-ShizukuWait"; start() }
            waiter.join(timeoutMs)
            if (exitHolder.get() == Int.MIN_VALUE) {
                runCatching { process.destroy() }
                waiter.join(300)
                return SuResult(false, synchronized(outLines) { outLines.toList() }, listOf("ADB shell timeout"), -1)
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
            Log.w(TAG, "Shizuku exec failed", cause)
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
