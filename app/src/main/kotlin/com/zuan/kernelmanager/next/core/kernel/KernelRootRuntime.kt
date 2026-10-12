/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.core.kernel

import android.content.Context
import android.os.Looper
import android.util.Log
import com.topjohnwu.superuser.Shell
import com.zuan.kernelmanager.next.core.mode.ModeNotice
import com.zuan.kernelmanager.next.core.mode.WorkingMode
import com.zuan.kernelmanager.next.core.mode.WorkingModeManager
import com.zuan.kernelmanager.next.library.shell.AxeronShell
import com.zuan.kernelmanager.next.library.shell.PrivilegedAccess
import com.zuan.kernelmanager.next.library.shell.RootIpcManager
import com.zuan.kernelmanager.next.library.shell.ShizukuShell
import com.zuan.kernelmanager.next.library.shell.SuResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Single privileged transport for ZKM Next.
 *
 * New code should use the suspend APIs. Kernel node operations prefer the
 * persistent ZKM Binder daemon and retry once after a Binder disconnect before
 * falling back to the process-wide libsu shell.
 */
object KernelRootRuntime {
    private const val TAG = "ZKM-KernelRuntime"
    private const val MAX_BATCH = 96
    private const val COMMAND_TIMEOUT_MS = 6000L

    private fun isMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()

    private fun blockingThreadError(operation: String): SuResult =
        SuResult(false, emptyList(), listOf("$operation must not be called from the main thread; use the suspend API"), -2)


    private val initialized = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private data class ShellTask(
        val commands: Array<out String>,
        val result: CompletableDeferred<SuResult>
    )

    private val shellQueue = Channel<ShellTask>(Channel.UNLIMITED)

    private val shellCalls = AtomicLong(0L)
    private val daemonCalls = AtomicLong(0L)
    private val fallbackCalls = AtomicLong(0L)
    private val nodeReads = AtomicLong(0L)
    private val nodeWrites = AtomicLong(0L)
    private val batchReads = AtomicLong(0L)
    private val batchWrites = AtomicLong(0L)
    private val failures = AtomicLong(0L)
    private val timeouts = AtomicLong(0L)
    private val reconnectAttempts = AtomicLong(0L)
    private val lastBatchTransport = AtomicReference("none")
    private val lastOperationMs = AtomicLong(0L)
    private val lastError = AtomicReference<String?>(null)

    /** Last shell failure (diagnostics shown on the FPS status card when Shizuku looks "not ready"). */
    fun lastErrorText(): String? = lastError.get()

    init {
        scope.launch {
            for (task in shellQueue) {
                runCatching { processShellTask(task.commands) }
                    .onSuccess { task.result.complete(it) }
                    .onFailure {
                        failures.incrementAndGet()
                        task.result.complete(
                            SuResult(false, emptyList(), listOf(it.message ?: it.javaClass.simpleName), -1)
                        )
                    }
            }
        }
    }

    fun initialize(context: Context) {
        val app = context.applicationContext
        KernelRuntimeContext.context = app
        WorkingModeManager.load(app)
        ShizukuShell.registerListeners(app)
        AxeronShell.registerListeners(app)
        // The ZKM root daemon (and therefore the su prompt) only exists in ROOT mode.
        if (!WorkingModeManager.current.hasRoot) {
            initialized.set(false)
            return
        }
        if (initialized.compareAndSet(false, true)) {
            RootIpcManager.bind(app)
        } else if (!RootIpcManager.isConnected()) {
            RootIpcManager.bind(app)
        }
    }

    /**
     * Called when the user switches working mode at runtime. Leaving ROOT drops the daemon so no
     * privileged transport survives; entering ROOT binds it again.
     */
    fun onWorkingModeChanged(context: Context, mode: WorkingMode) {
        val app = context.applicationContext
        KernelRuntimeContext.context = app
        PrivilegedAccess.bumpEpoch()
        if (mode == WorkingMode.ADB) {
            ShizukuShell.registerListeners(app)
            ShizukuShell.refreshState()
            AxeronShell.registerListeners(app)
            AxeronShell.refreshState()
        }
        if (mode.hasRoot) {
            initialize(app)
        } else {
            initialized.set(false)
            RootIpcManager.unbind()
        }
    }

    suspend fun execute(vararg commands: String): SuResult = withContext(Dispatchers.IO) {
        if (commands.isEmpty()) return@withContext SuResult(true, emptyList(), emptyList(), 0)
        val activeMode = currentMode()
        if (activeMode != WorkingMode.ROOT) return@withContext executeNonRoot(activeMode, commands)
        val deferred = CompletableDeferred<SuResult>()
        shellQueue.send(ShellTask(commands.copyOf(), deferred))
        val started = System.nanoTime()
        val result = withTimeoutOrNull(COMMAND_TIMEOUT_MS) { deferred.await() }
        lastOperationMs.set((System.nanoTime() - started) / 1_000_000L)
        if (result != null) {
            if (result.isSuccess) lastError.set(null)
            return@withContext result
        }
        timeouts.incrementAndGet()
        failures.incrementAndGet()
        lastError.set("Root shell timeout")
        SuResult(false, emptyList(), listOf("Root shell timeout"), -1)
    }

    private fun currentMode(): WorkingMode {
        KernelRuntimeContext.context?.let { WorkingModeManager.load(it) }
        return WorkingModeManager.current
    }

    /** Execute privileged shell command via Shizuku or Axeron (AXM). */
    private fun execAdb(commands: Array<out String>): SuResult = when {
        ShizukuShell.isGranted() -> ShizukuShell.exec(commands)
        AxeronShell.isGranted() -> AxeronShell.exec(commands)
        ShizukuShell.isRunning() -> ShizukuShell.exec(commands)
        AxeronShell.isRunning() -> AxeronShell.exec(commands)
        else -> SuResult(
            false, emptyList(),
            listOf("ADB mode needs Shizuku or Axeron (AXM) running with permission granted"), -1
        )
    }

    /**
     * BASIC / ADB path. No shared single-thread queue: a slow `dumpsys` must not starve cheap
     * `cat` reads (that head-of-line blocking made the 6 s timeout fire constantly over Shizuku).
     */
    private fun executeNonRoot(mode: WorkingMode, commands: Array<out String>): SuResult {
        shellCalls.incrementAndGet()
        val started = System.nanoTime()
        val result = when (mode) {
            WorkingMode.BASIC -> {
                lastError.set("Not available in Basic mode")
                SuResult(false, emptyList(), listOf("Not available in Basic mode"), -1)
            }
            else -> execAdb(commands)
        }
        lastOperationMs.set((System.nanoTime() - started) / 1_000_000L)
        if (result.isSuccess) lastError.set(null)
        else if (mode != WorkingMode.BASIC) {
            failures.incrementAndGet()
            if (result.errorString.contains("timeout", true)) timeouts.incrementAndGet()
            lastError.set(result.errorString.ifBlank { "ADB command failed" })
        }
        return result
    }

    /** World-readable sysfs/procfs nodes can be read by the app itself - no shell needed. */
    private fun directRead(path: String): String? = try {
        val f = java.io.File(path)
        if (f.isFile && f.canRead()) f.readText().trim().takeIf { it.isNotEmpty() } else null
    } catch (_: Throwable) {
        null
    }

    /** Legacy blocking facade for existing feature modules. */
    fun executeBlocking(vararg commands: String): SuResult {
        if (isMainThread()) return blockingThreadError("KernelRootRuntime.executeBlocking()")
        return runBlocking(Dispatchers.IO) { execute(*commands) }
    }

    private fun processShellTask(commands: Array<out String>): SuResult {
        shellCalls.incrementAndGet()
        KernelRuntimeContext.context?.let { WorkingModeManager.load(it) }
        when (WorkingModeManager.current) {
            WorkingMode.BASIC -> {
                failures.incrementAndGet()
                lastError.set("Not available in Basic mode")
                return SuResult(false, emptyList(), listOf("Not available in Basic mode"), -1)
            }
            WorkingMode.ADB -> {
                val adbResult = execAdb(commands)
                if (!adbResult.isSuccess) {
                    failures.incrementAndGet()
                    lastError.set(adbResult.errorString.ifBlank { "ADB command failed" })
                } else lastError.set(null)
                return adbResult
            }
            WorkingMode.ROOT -> Unit
        }
        return try {
            val shell = Shell.getShell()
            if (!shell.isRoot) {
                failures.incrementAndGet()
                lastError.set("Root access denied")
                return SuResult(false, emptyList(), listOf("Root access denied"), -1)
            }
            val result = Shell.cmd(*commands).exec()
            if (!result.isSuccess) {
                failures.incrementAndGet()
                lastError.set(result.err.joinToString(" ").ifBlank { "Root command failed" })
            }
            SuResult(result.isSuccess, result.out, result.err, result.code)
        } catch (t: Throwable) {
            failures.incrementAndGet()
            lastError.set(t.message ?: t.javaClass.simpleName)
            Log.e(TAG, "Root shell execution failed", t)
            SuResult(false, emptyList(), listOf(t.message ?: t.javaClass.simpleName), -1)
        }
    }

    private suspend fun ensureDaemon(context: Context, timeoutMs: Long = 1200L): Boolean {
        initialize(context.applicationContext)
        if (!WorkingModeManager.current.hasRoot) return false
        return if (RootIpcManager.isConnected()) true else {
            reconnectAttempts.incrementAndGet()
            RootIpcManager.ensureConnected(context.applicationContext, timeoutMs)
        }
    }

    suspend fun readNode(path: String): String = withContext(Dispatchers.IO) {
        if (!KernelNodePolicy.isAllowed(path)) return@withContext ""
        val started = System.nanoTime()
        nodeReads.incrementAndGet()
        var remote: String? = null
        if (ensureDaemon((KernelRuntimeContext.context ?: return@withContext ""))) {
            remote = runCatching { RootIpcManager.ipc?.readNode(path) }.getOrNull()
            if (remote != null) daemonCalls.incrementAndGet()
        }
        if (remote != null) {
            lastError.set(null)
            lastBatchTransport.set("daemon")
            lastOperationMs.set((System.nanoTime() - started) / 1_000_000L)
            return@withContext remote.trim()
        }
        if (!WorkingModeManager.current.hasRoot) {
            directRead(path)?.let {
                lastBatchTransport.set("direct")
                lastOperationMs.set((System.nanoTime() - started) / 1_000_000L)
                return@withContext it
            }
        }
        fallbackCalls.incrementAndGet()
        lastBatchTransport.set("shell_fallback")
        val result = execute("cat ${shellQuote(path)} 2>/dev/null")
        lastOperationMs.set((System.nanoTime() - started) / 1_000_000L)
        if (!result.isSuccess) failures.incrementAndGet() else lastError.set(null)
        result.outputString
    }

    /** Blocking helper retained for legacy synchronous feature code. */
    fun readNodeBlocking(path: String): String {
        if (isMainThread()) return ""
        if (!KernelNodePolicy.isAllowed(path)) return ""
        val remote = runCatching { RootIpcManager.ipc?.readNode(path) }.getOrNull()
        if (remote != null) {
            nodeReads.incrementAndGet()
            daemonCalls.incrementAndGet()
            lastError.set(null)
            return remote.trim()
        }
        nodeReads.incrementAndGet()
        if (!WorkingModeManager.current.hasRoot) directRead(path)?.let { return it }
        fallbackCalls.incrementAndGet()
        val result = executeBlocking("cat ${shellQuote(path)} 2>/dev/null")
        if (result.isSuccess) lastError.set(null)
        return result.outputString
    }

    suspend fun readNodes(paths: List<String>): Map<String, String> = withContext(Dispatchers.IO) {
        val unique = paths.filter { KernelNodePolicy.isAllowed(it) }.distinct().take(MAX_BATCH)
        if (unique.isEmpty()) return@withContext emptyMap()
        val started = System.nanoTime()
        nodeReads.addAndGet(unique.size.toLong())

        val context = KernelRuntimeContext.context
        if (context != null) ensureDaemon(context, 1600L)
        val ipcResult = runCatching {
            RootIpcManager.ipc?.readNodes(unique.toTypedArray())
        }.getOrNull()
        if (ipcResult != null && ipcResult.size == unique.size) {
            daemonCalls.incrementAndGet()
            lastError.set(null)
            batchReads.incrementAndGet()
            lastBatchTransport.set("daemon")
            lastOperationMs.set((System.nanoTime() - started) / 1_000_000L)
            return@withContext unique.mapIndexedNotNull { index, path ->
                ipcResult[index].trim().takeIf { it.isNotEmpty() }?.let { path to it }
            }.toMap()
        }

        val directHits = LinkedHashMap<String, String>()
        var pending = unique
        if (!WorkingModeManager.current.hasRoot) {
            unique.forEach { p -> directRead(p)?.let { directHits[p] = it } }
            pending = unique.filter { it !in directHits }
            if (pending.isEmpty()) {
                batchReads.incrementAndGet()
                lastBatchTransport.set("direct")
                lastOperationMs.set((System.nanoTime() - started) / 1_000_000L)
                return@withContext directHits
            }
        }
        fallbackCalls.incrementAndGet()
        batchReads.incrementAndGet()
        lastBatchTransport.set("shell_fallback")
        val marker = "__ZKM_NODE_${System.nanoTime()}__"
        val script = buildString {
            pending.forEachIndexed { index, path ->
                append("echo '").append(marker).append(index).append("'\n")
                append("cat ").append(shellQuote(path)).append(" 2>/dev/null; echo\n")
            }
        }
        val lines = execute(script).out
        val result = LinkedHashMap<String, String>(directHits)
        var currentIndex = -1
        val buffer = StringBuilder()
        fun flush() {
            if (currentIndex in pending.indices) {
                buffer.toString().trim().takeIf { it.isNotEmpty() }?.let { result[pending[currentIndex]] = it }
            }
            buffer.setLength(0)
        }
        for (line in lines) {
            if (line.startsWith(marker)) {
                flush()
                currentIndex = line.removePrefix(marker).toIntOrNull() ?: -1
            } else if (currentIndex >= 0) {
                if (buffer.isNotEmpty()) buffer.append('\n')
                buffer.append(line)
            }
        }
        flush()
        lastOperationMs.set((System.nanoTime() - started) / 1_000_000L)
        result
    }


    fun readNodesBlocking(paths: List<String>): Map<String, String> {
        if (isMainThread()) return emptyMap()
        return runBlocking(Dispatchers.IO) { readNodes(paths) }
    }

    fun writeNodesBlocking(paths: List<String>, values: List<String>): Map<String, Boolean> {
        if (isMainThread()) return emptyMap()
        return runBlocking(Dispatchers.IO) { writeNodes(paths, values) }
    }

    fun nodeExistsBatchBlocking(paths: List<String>): Map<String, Boolean> {
        if (isMainThread()) return emptyMap()
        return runBlocking(Dispatchers.IO) { nodeExistsBatch(paths) }
    }

    fun listDirectoriesBlocking(path: String): List<String> {
        if (isMainThread()) return emptyList()
        return runBlocking(Dispatchers.IO) { listDirectories(path) }
    }

    suspend fun writeNodes(paths: List<String>, values: List<String>): Map<String, Boolean> = withContext(Dispatchers.IO) {
        if (paths.isEmpty() || paths.size != values.size) return@withContext emptyMap()
        if (!WorkingModeManager.current.hasRoot) {
            ModeNotice.denied(KernelRuntimeContext.context, WorkingMode.ROOT)
            return@withContext paths.associateWith { false }
        }
        val pairs = paths.zip(values)
            .filter { KernelNodePolicy.isWriteAllowed(it.first) }
            .distinctBy { it.first }
            .take(MAX_BATCH)
        if (pairs.isEmpty()) return@withContext emptyMap()
        val app = KernelRuntimeContext.context
        if (app != null) ensureDaemon(app, 1600L)
        val remote = runCatching {
            RootIpcManager.ipc?.writeNodes(pairs.map { it.first }.toTypedArray(), pairs.map { it.second }.toTypedArray())
        }.getOrNull()
        if (remote != null && remote.size == pairs.size) {
            batchWrites.incrementAndGet()
            daemonCalls.incrementAndGet()
            nodeWrites.addAndGet(pairs.size.toLong())
            return@withContext pairs.mapIndexed { i, p -> p.first to remote[i] }.toMap()
        }
        fallbackCalls.incrementAndGet()
        batchWrites.incrementAndGet()
        val result = LinkedHashMap<String, Boolean>()
        pairs.forEach { (path, value) -> result[path] = writeNode(path, value) }
        result
    }

    suspend fun nodeExists(path: String): Boolean = withContext(Dispatchers.IO) {
        if (!KernelNodePolicy.isAllowed(path)) return@withContext false
        val remote = runCatching { RootIpcManager.ipc?.nodeExists(path) }.getOrNull()
        if (remote != null) return@withContext remote
        if (java.io.File(path).exists()) return@withContext true
        execute("test -e ${shellQuote(path)}").isSuccess
    }

    fun nodeExistsBlocking(path: String): Boolean {
        if (isMainThread()) return false
        if (!KernelNodePolicy.isAllowed(path)) return false
        val remote = runCatching { RootIpcManager.ipc?.nodeExists(path) }.getOrNull()
        if (remote != null) return remote
        if (java.io.File(path).exists()) return true
        return executeBlocking("test -e ${shellQuote(path)}").isSuccess
    }

    suspend fun nodeExistsBatch(paths: List<String>): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val unique = paths.filter { KernelNodePolicy.isAllowed(it) }.distinct().take(MAX_BATCH)
        if (unique.isEmpty()) return@withContext emptyMap()
        val app = KernelRuntimeContext.context
        if (app != null) ensureDaemon(app, 1600L)
        val remote = runCatching { RootIpcManager.ipc?.nodeExistsBatch(unique.toTypedArray()) }.getOrNull()
        if (remote != null && remote.size == unique.size) {
            daemonCalls.incrementAndGet()
            return@withContext unique.mapIndexed { i, p -> p to remote[i] }.toMap()
        }
        val result = HashMap<String, Boolean>(unique.size)
        val unresolved = ArrayList<String>()
        unique.forEach { path ->
            if (java.io.File(path).exists()) result[path] = true else unresolved += path
        }
        if (unresolved.isNotEmpty()) {
            val marker = "__ZKM_EXISTS_${System.nanoTime()}__"
            val script = buildString {
                unresolved.forEachIndexed { i, path ->
                    append("if test -e ").append(shellQuote(path))
                        .append("; then printf '%s%d\\n' '").append(marker).append("' ").append(i)
                        .append("; else printf '%s-\\n' '").append(marker).append("' ").append(i)
                        .append("; fi\n")
                }
            }
            execute(script).out.forEach { line ->
                if (!line.startsWith(marker)) return@forEach
                val token = line.removePrefix(marker)
                val idx = token.removeSuffix("-").toIntOrNull() ?: return@forEach
                if (idx in unresolved.indices) result[unresolved[idx]] = !token.endsWith('-')
            }
        }
        unique.associateWith { result[it] == true }
    }

    suspend fun nodeWritableBatch(paths: List<String>): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val unique = paths.filter { KernelNodePolicy.isAllowed(it) }.distinct().take(MAX_BATCH)
        if (unique.isEmpty()) return@withContext emptyMap()
        val app = KernelRuntimeContext.context
        if (app != null) ensureDaemon(app, 1600L)
        val remote = runCatching { RootIpcManager.ipc?.nodeWritableBatch(unique.toTypedArray()) }.getOrNull()
        if (remote != null && remote.size == unique.size) {
            return@withContext unique.mapIndexed { i, p -> p to remote[i] }.toMap()
        }
        unique.associateWith { java.io.File(it).canWrite() }
    }

    suspend fun listDirectories(path: String): List<String> = withContext(Dispatchers.IO) {
        if (!KernelNodePolicy.isAllowed(path)) return@withContext emptyList()
        val remote = runCatching { RootIpcManager.ipc?.listDirectories(path) }.getOrNull()
        if (remote != null) return@withContext remote
        // Daemon not connected yet: the app UID usually cannot see sysfs/procfs dirs, so an
        // empty File listing must not be trusted. Ask the root shell instead.
        val viaApp = java.io.File(path).listFiles()?.filter { it.isDirectory }?.map { it.name }.orEmpty()
        if (viaApp.isNotEmpty()) return@withContext viaApp
        execute("for d in ${shellQuote(path)}/*/; do [ -d \"\$d\" ] && basename \"\$d\"; done")
            .out.map { it.trim() }.filter { it.isNotEmpty() }
    }

    suspend fun writeNode(path: String, value: String): Boolean = withContext(Dispatchers.IO) {
        if (!KernelNodePolicy.isWriteAllowed(path)) return@withContext false
        if (!WorkingModeManager.current.hasRoot) {
            ModeNotice.denied(KernelRuntimeContext.context, WorkingMode.ROOT)
            return@withContext false
        }
        val normalized = value.replace('\u0000', ' ').trim()
        if (normalized.isEmpty()) return@withContext false
        KernelRuntimeContext.context?.let { ensureDaemon(it, 1600L) }
        val remote = runCatching { RootIpcManager.ipc?.writeNode(path, normalized) }.getOrNull()
        nodeWrites.incrementAndGet()
        if (remote != null) {
            daemonCalls.incrementAndGet()
            if (!remote) failures.incrementAndGet() else lastError.set(null)
            return@withContext remote
        }
        fallbackCalls.incrementAndGet()
        val result = execute("printf '%s' ${shellQuote(normalized)} > ${shellQuote(path)}")
        if (!result.isSuccess) failures.incrementAndGet()
        result.isSuccess
    }

    fun writeNodeBlocking(path: String, value: String): Boolean {
        if (isMainThread()) return false
        if (!KernelNodePolicy.isWriteAllowed(path)) return false
        if (!WorkingModeManager.current.hasRoot) {
            ModeNotice.denied(KernelRuntimeContext.context, WorkingMode.ROOT)
            return false
        }
        val normalized = value.replace('\u0000', ' ').trim()
        if (normalized.isEmpty()) return false
        val remote = runCatching { RootIpcManager.ipc?.writeNode(path, normalized) }.getOrNull()
        nodeWrites.incrementAndGet()
        if (remote != null) {
            daemonCalls.incrementAndGet()
            if (remote) lastError.set(null)
            return remote
        }
        fallbackCalls.incrementAndGet()
        return executeBlocking("printf '%s' ${shellQuote(normalized)} > ${shellQuote(path)}").isSuccess
    }

    suspend fun diagnosticWriteFile(path: String, value: String): Boolean = withContext(Dispatchers.IO) {
        if (!path.startsWith("/data/local/tmp/zkm-next-diag-")) return@withContext false
        val app = KernelRuntimeContext.context ?: return@withContext false
        if (!ensureDaemon(app, 2500L)) return@withContext false
        runCatching { RootIpcManager.ipc?.diagnosticWriteFile(path, value) == true }.getOrDefault(false)
    }

    suspend fun diagnosticReadFile(path: String): String = withContext(Dispatchers.IO) {
        if (!path.startsWith("/data/local/tmp/zkm-next-diag-")) return@withContext ""
        runCatching { RootIpcManager.ipc?.diagnosticReadFile(path).orEmpty() }.getOrDefault("")
    }

    suspend fun diagnosticDeleteFile(path: String): Boolean = withContext(Dispatchers.IO) {
        if (!path.startsWith("/data/local/tmp/zkm-next-diag-")) return@withContext false
        runCatching { RootIpcManager.ipc?.diagnosticDeleteFile(path) == true }.getOrDefault(false)
    }

    fun snapshotStats(): KernelRuntimeStats = KernelRuntimeStats(
        shellCalls = shellCalls.get(),
        daemonCalls = daemonCalls.get(),
        fallbackCalls = fallbackCalls.get(),
        nodeReads = nodeReads.get(),
        nodeWrites = nodeWrites.get(),
        batchReads = batchReads.get(),
        batchWrites = batchWrites.get(),
        failures = failures.get(),
        timeouts = timeouts.get(),
        reconnectAttempts = reconnectAttempts.get(),
        daemonConnected = RootIpcManager.isConnected(),
        lastBatchTransport = lastBatchTransport.get(),
        lastOperationMs = lastOperationMs.get(),
        lastError = lastError.get()
    )

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"
}

/** Process-wide context handoff used by the central runtime. */
object KernelRuntimeContext {
    @Volatile var context: Context? = null
}

data class KernelRuntimeStats(
    val shellCalls: Long,
    val daemonCalls: Long,
    val fallbackCalls: Long,
    val nodeReads: Long,
    val nodeWrites: Long,
    val batchReads: Long,
    val batchWrites: Long,
    val failures: Long,
    val timeouts: Long,
    val reconnectAttempts: Long,
    val daemonConnected: Boolean,
    val lastBatchTransport: String,
    val lastOperationMs: Long,
    val lastError: String?
)
