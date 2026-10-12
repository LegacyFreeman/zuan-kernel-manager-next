/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.library.shell

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import com.topjohnwu.superuser.ipc.RootService
import com.zuan.kernelmanager.next.IZkmRootService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.delay

/**
 * Process-wide resilient Binder connection to ZKM's root daemon.
 *
 * Responsibilities are deliberately limited to connection lifecycle. Kernel
 * reads/writes stay in KernelRootRuntime so every feature shares one transport.
 */
object RootIpcManager {
    private const val TAG = "ZkmRootIPC"
    private const val SERVICE_CLASS = "com.zuan.kernelmanager.next.service.ZkmRootService"
    private const val REBIND_DELAY_MS = 600L

    @Volatile
    var ipc: IZkmRootService? = null
        private set

    @Volatile private var isBinding = false
    @Volatile private var applicationContext: Context? = null
    @Volatile private var intentionallyUnbound = false
    @Volatile private var boundBinder: IBinder? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val callbackExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ZKM-RootIPC").apply { isDaemon = true }
    }

    private val reconnects = AtomicLong(0L)
    private val disconnects = AtomicLong(0L)
    private val lastError = AtomicReference<String?>(null)

    private val deathRecipient = IBinder.DeathRecipient {
        clearConnection("binder-death")
        scheduleRebind()
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            try { service.linkToDeath(deathRecipient, 0) } catch (e: Throwable) { Log.w(TAG, "linkToDeath failed", e) }
            synchronized(this@RootIpcManager) {
                boundBinder = service
                ipc = IZkmRootService.Stub.asInterface(service)
                isBinding = false
                intentionallyUnbound = false
            }
            lastError.set(null)
            Log.i(TAG, "Kernel root daemon connected")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            disconnects.incrementAndGet()
            clearConnection("service-disconnected")
            scheduleRebind()
        }

        override fun onBindingDied(name: ComponentName) {
            disconnects.incrementAndGet()
            clearConnection("binding-died")
            scheduleRebind()
        }

        override fun onNullBinding(name: ComponentName) {
            disconnects.incrementAndGet()
            clearConnection("null-binding")
            scheduleRebind()
        }
    }

    fun bind(context: Context) {
        val app = context.applicationContext
        synchronized(this) {
            applicationContext = app
            intentionallyUnbound = false
            if (ipc != null || isBinding) return
            isBinding = true
        }
        mainHandler.post {
            try {
                val intent = Intent().setComponent(ComponentName(app, SERVICE_CLASS)).apply {
                    addCategory(RootService.CATEGORY_DAEMON_MODE)
                }
                RootService.bind(intent, callbackExecutor, connection)
            } catch (t: Throwable) {
                synchronized(this@RootIpcManager) { isBinding = false }
                lastError.set(t.message ?: t.javaClass.simpleName)
                reconnects.incrementAndGet()
                Log.e(TAG, "Daemon bind failed", t)
                scheduleRebind()
            }
        }
    }

    suspend fun ensureConnected(context: Context, timeoutMs: Long = 4000L): Boolean {
        if (!isConnected()) bind(context)
        return waitForConnectionState(true, timeoutMs)
    }

    fun isConnected(): Boolean = ipc != null && boundBinder?.isBinderAlive != false

    fun reconnectCount(): Long = reconnects.get()
    fun disconnectCount(): Long = disconnects.get()
    fun lastError(): String? = lastError.get()

    /** Debug-only destructive self-test. */
    fun crashDaemonForDiagnostics(): Boolean {
        val service = ipc ?: return false
        return try {
            service.crashForDiagnostics()
        } catch (_: RemoteException) {
            true
        } catch (_: Throwable) {
            true
        }
    }

    suspend fun waitForConnectionState(connected: Boolean, timeoutMs: Long): Boolean {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs.coerceAtLeast(100L)
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            val actual = isConnected()
            if (actual == connected) return true
            delay(50L)
        }
        return isConnected() == connected
    }

    private fun clearConnection(reason: String) {
        val old = synchronized(this) {
            val binder = boundBinder
            boundBinder = null
            ipc = null
            isBinding = false
            binder
        }
        runCatching { old?.unlinkToDeath(deathRecipient, 0) }
        Log.w(TAG, "Kernel root daemon disconnected: $reason")
    }

    private fun scheduleRebind() {
        val context = applicationContext ?: return
        if (intentionallyUnbound) return
        mainHandler.postDelayed({
            if (!intentionallyUnbound && !isConnected()) {
                reconnects.incrementAndGet()
                bind(context)
            }
        }, REBIND_DELAY_MS)
    }

    suspend fun rebindForDiagnostics(context: Context, timeoutMs: Long = 3500L): Boolean {
        intentionallyUnbound = false
        unbind()
        delay(250L)
        bind(context.applicationContext)
        return waitForConnectionState(true, timeoutMs)
    }

    fun unbind() {
        mainHandler.removeCallbacksAndMessages(null)
        synchronized(this) {
            intentionallyUnbound = true
            ipc = null
            isBinding = false
            applicationContext = null
            boundBinder = null
        }
        mainHandler.post {
            try { RootService.unbind(connection) } catch (e: Throwable) { Log.d(TAG, "Daemon unbind failed", e) }
        }
    }
}
