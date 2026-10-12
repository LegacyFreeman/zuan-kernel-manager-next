/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3, or (at your option) any later version.
 */
package com.zuan.kernelmanager.next.library.device.process

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Process/task-manager backend.
 *
 * The UI is deliberately kept separate from this class.  The runtime follows
 * the same practical model used by Scene: keep one privileged shell transport,
 * prefer a robust `top`/`ps` command discovered at runtime, cache app metadata,
 * and expose a small set of direct process operations.
 */
data class ProcessData(
    val pid: String,
    val res: String,
    val cpu: String,
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val swap: String = "—",
    val user: String = "—",
    val command: String = "",
    val cmdline: String = "",
    val state: String = "",
    val cpuSet: String = "—",
    val cGroup: String = "—",
    val oomAdj: String = "—",
    val oomScore: String = "—",
    val oomScoreAdj: String = "—",
    val isAndroidApp: Boolean = false,
    val isUserApp: Boolean = false,
    /** true hanya bila nama proses cocok dengan paket yang benar-benar terpasang (punya label + ikon). */
    val isInstalledApp: Boolean = false
)

data class ProcessActionResult(
    val success: Boolean,
    val message: String
)

enum class SortType {
    CPU, RES, PID
}

enum class ProcessFilter {
    ALL, ANDROID_USER, ANDROID_SYSTEM, ANDROID, OTHER
}

object ProcessUtils {
    private const val MAX_PROCESS_ROWS = 4096

    private val appCache = ConcurrentHashMap<String, Pair<String, android.graphics.drawable.Drawable.ConstantState?>>()
    private val listCommand = AtomicReference<String?>(null)

    suspend fun getTopProcesses(
        context: Context,
        limit: Int,
        sortType: SortType,
        filter: ProcessFilter = ProcessFilter.ALL,
        keyword: String = "",
        includeSelf: Boolean = false
    ): List<ProcessData> {
        val snapshot = snapshot(context, includeSelf)
        return snapshot
            .asSequence()
            .filter { matchesFilter(it, filter) }
            .filter { matchesKeyword(it, keyword) }
            .sortedWith(sortComparator(sortType))
            .take(limit.coerceIn(1, MAX_PROCESS_ROWS))
            .toList()
    }

    suspend fun snapshot(context: Context, includeSelf: Boolean = false): List<ProcessData> {
        val app = context.applicationContext
        KernelRootRuntime.initialize(app)

        val command = ensureListCommand(app) ?: return emptyList()
        val result = KernelRootRuntime.execute("$command 2>&1")
        if (!result.isSuccess && result.out.isEmpty()) return emptyList()

        val processes = ArrayList<ProcessData>()
        val rows = result.out
        var headerConsumed = false
        for (rawRow in rows) {
            val row = rawRow.trim()
            if (row.isBlank()) continue
            if (!headerConsumed && looksLikeHeader(row)) {
                headerConsumed = true
                continue
            }
            val process = parseRow(app, row) ?: continue
            if (shouldExclude(app, process, includeSelf)) continue
            processes += process
            if (processes.size >= MAX_PROCESS_ROWS) break
        }
        return processes
    }

    suspend fun getProcessDetail(context: Context, pid: String): ProcessData? {
        val safePid = pid.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val current = snapshot(context).firstOrNull { it.pid == safePid.toString() }
        val base = current ?: ProcessData(
            pid = safePid.toString(),
            res = "—",
            cpu = "—",
            packageName = "",
            appName = safePid.toString()
        )

        val paths = listOf(
            "/proc/$safePid/status",
            "/proc/$safePid/cmdline",
            "/proc/$safePid/cpuset",
            "/proc/$safePid/cgroup",
            "/proc/$safePid/oom_adj",
            "/proc/$safePid/oom_score",
            "/proc/$safePid/oom_score_adj"
        )
        val values = KernelRootRuntime.readNodes(paths)
        if (values.isEmpty() && current == null) return null

        val status = values[paths[0]].orEmpty()
        val parsedStatus = parseStatus(status)
        val cmdline = values[paths[1]].orEmpty()
            .replace('\u0000', ' ')
            .trim()
        val name = base.packageName.ifBlank { parsedStatus["Name"].orEmpty() }
        val isAndroid = base.isAndroidApp || looksLikeAndroidProcess(name, base.command)
        val resolved = resolveApp(context, name, isAndroid)

        return base.copy(
            packageName = if (base.packageName.isNotBlank()) base.packageName else name,
            appName = resolved?.first ?: base.appName.ifBlank { name },
            icon = resolved?.second,
            swap = parsedStatus["VmSwap"]?.let(::formatStatusMemory) ?: base.swap,
            user = base.user.ifBlank { parsedStatus["Uid"].orEmpty() },
            command = base.command.ifBlank { parsedStatus["Name"].orEmpty() },
            cmdline = cmdline.ifBlank { base.cmdline },
            state = parsedStatus["State"]?.substringBefore(' ')?.trim().orEmpty().ifBlank { base.state },
            cpuSet = values[paths[2]]?.trim().orEmpty().ifBlank { base.cpuSet },
            cGroup = values[paths[3]]?.trim().orEmpty().ifBlank { base.cGroup },
            oomAdj = values[paths[4]]?.trim().orEmpty().ifBlank { base.oomAdj },
            oomScore = values[paths[5]]?.trim().orEmpty().ifBlank { base.oomScore },
            oomScoreAdj = values[paths[6]]?.trim().orEmpty().ifBlank { base.oomScoreAdj },
            isAndroidApp = isAndroid,
            isUserApp = base.isUserApp || isAndroidUserProcess(base)
        )
    }

    suspend fun killProcess(context: Context, process: ProcessData): ProcessActionResult {
        val pid = process.pid.toIntOrNull()?.takeIf { it > 0 }
            ?: return ProcessActionResult(false, "Invalid PID")

        val packageName = normalizePackageName(process.packageName)
        val command = if (process.isAndroidApp && packageName.isNotBlank() && packageName.contains('.')) {
            val pkg = shellQuote(packageName)
            "killall -9 $pkg 2>/dev/null; am force-stop $pkg 2>/dev/null; am kill $pkg 2>/dev/null; true"
        } else {
            "kill -9 $pid"
        }

        val result = KernelRootRuntime.execute(command)
        return if (result.isSuccess) {
            ProcessActionResult(true, "Process $pid terminated")
        } else {
            ProcessActionResult(false, result.err.joinToString(" ").ifBlank { "Failed to terminate process $pid" })
        }
    }

    suspend fun forceStopApp(context: Context, process: ProcessData): ProcessActionResult {
        val packageName = normalizePackageName(process.packageName).trim()
        if (!process.isAndroidApp || !packageName.contains('.')) {
            return ProcessActionResult(false, "This process is not a managed Android application")
        }
        val result = KernelRootRuntime.execute("am force-stop ${shellQuote(packageName)}")
        return if (result.isSuccess) {
            ProcessActionResult(true, "Force-stopped $packageName")
        } else {
            ProcessActionResult(false, result.err.joinToString(" ").ifBlank { "Unable to force-stop $packageName" })
        }
    }

    private suspend fun ensureListCommand(context: Context): String? {
        listCommand.get()?.let { return it }
        val candidates = listOf(
            "top -o %CPU,RES,SWAP,NAME,PID,USER,COMMAND,CMDLINE -q -b -n 1 -m 65535",
            "top -o %CPU,RES,SWAP,NAME,PID,USER,COMMAND,CMDLINE -b -n 1 -m 65535",
            "ps -e -o %CPU,RES,SWAP,NAME,PID,USER,COMMAND,CMDLINE"
        )
        for (candidate in candidates) {
            val result = KernelRootRuntime.execute("$candidate 2>&1")
            if (looksUsableProcessOutput(result.out)) {
                listCommand.compareAndSet(null, candidate)
                return listCommand.get()
            }
        }
        return null
    }

    private fun parseRow(context: Context, row: String): ProcessData? {
        val columns = row.split(Regex("\\s+"))
        if (columns.size < 7) return null

        // All discovered Scene-style commands use the same leading columns:
        // %CPU, RES, SWAP, NAME, PID, USER, COMMAND, ...
        val cpu = columns.getOrNull(0) ?: return null
        val res = columns.getOrNull(1) ?: return null
        val swap = columns.getOrNull(2) ?: "0"
        val name = columns.getOrNull(3) ?: return null
        val pid = columns.getOrNull(4)?.toIntOrNull()?.takeIf { it > 0 }?.toString() ?: return null
        val user = columns.getOrNull(5)?.takeIf { it.matches(Regex("[A-Za-z0-9_.$-]+")) } ?: ""
        val command = columns.getOrNull(6).orEmpty()
        val cmdline = if (command.isNotBlank()) {
            row.substringAfter(command, "").trim()
        } else ""

        if (cpu.toFloatOrNull() == null) return null
        val isAndroid = looksLikeAndroidProcess(name, command)
        val resolved = resolveApp(context, name, isAndroid)
        val appName = resolved?.first ?: when {
            name.contains('/') -> name.substringAfterLast('/')
            else -> name
        }
        val isUser = isAndroid && isAndroidUser(user)

        return ProcessData(
            pid = pid,
            res = formatRes(res),
            cpu = "${cpu.removeSuffix("%")}%",
            packageName = name,
            appName = appName,
            icon = resolved?.second,
            swap = formatRes(swap),
            user = user.ifBlank { "—" },
            command = command,
            cmdline = cmdline,
            isAndroidApp = isAndroid,
            isUserApp = isUser,
            isInstalledApp = resolved != null
        )
    }

    private fun parseStatus(status: String): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        status.lineSequence().forEach { line ->
            val index = line.indexOf(':')
            if (index <= 0) return@forEach
            map[line.substring(0, index).trim()] = line.substring(index + 1).trim()
        }
        return map
    }

    private fun resolveApp(
        context: Context,
        processName: String,
        isAndroid: Boolean
    ): Pair<String, Drawable?>? {
        if (!isAndroid) return null
        val packageName = normalizePackageName(processName)
        if (!packageName.contains('.')) return null
        val cached = appCache[packageName]
        if (cached != null) {
            val icon = cached.second?.newDrawable(context.resources)
            return cached.first to icon
        }
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            val label = pm.getApplicationLabel(info).toString()
            val icon = pm.getApplicationIcon(info)
            appCache[packageName] = label to icon.constantState
            label to icon
        } catch (_: Exception) {
            null
        }
    }

    private fun matchesFilter(process: ProcessData, filter: ProcessFilter): Boolean = when (filter) {
        ProcessFilter.ALL -> true
        ProcessFilter.ANDROID_USER -> process.isAndroidApp && process.isUserApp
        ProcessFilter.ANDROID_SYSTEM -> process.isAndroidApp && !process.isUserApp
        ProcessFilter.ANDROID -> process.isAndroidApp
        ProcessFilter.OTHER -> !process.isAndroidApp
    }

    private fun matchesKeyword(process: ProcessData, keyword: String): Boolean {
        val q = keyword.trim().lowercase()
        if (q.isEmpty()) return true
        return listOf(process.appName, process.packageName, process.user, process.command, process.cmdline, process.pid)
            .any { it.lowercase().contains(q) }
    }

    private fun sortComparator(sortType: SortType): Comparator<ProcessData> = Comparator { a, b ->
        when (sortType) {
            SortType.CPU -> parseCpu(b.cpu).compareTo(parseCpu(a.cpu))
            SortType.RES -> parseMemoryKb(b.res).compareTo(parseMemoryKb(a.res))
            SortType.PID -> (a.pid.toLongOrNull() ?: Long.MAX_VALUE).compareTo(b.pid.toLongOrNull() ?: Long.MAX_VALUE)
        }
    }

    private fun looksUsableProcessOutput(rows: List<String>): Boolean {
        if (rows.size < 4) return false
        val hits = rows.count { row ->
            val cols = row.trim().split(Regex("\\s+"))
            cols.size >= 6 && cols.any { it.toIntOrNull()?.let { pid -> pid > 0 } == true }
        }
        return hits >= 2
    }

    private fun looksLikeHeader(row: String): Boolean {
        val upper = row.uppercase()
        return upper.startsWith("PID ") || upper.startsWith("USER ") || upper.startsWith("TASK") ||
            upper.startsWith("MEM") || upper.startsWith("SWAP") || upper.contains("%CPU")
    }

    private fun looksLikeAndroidProcess(name: String, command: String): Boolean {
        val normalized = normalizePackageName(name)
        return normalized.contains('.') &&
            (command.contains("app_process") || normalized.firstOrNull()?.isLowerCase() == true)
    }

    private fun isAndroidUser(user: String): Boolean = user.matches(Regex("u[0-9]+_.*"))

    private fun isAndroidUserProcess(process: ProcessData): Boolean = process.isAndroidApp && process.isUserApp

    private fun shouldExclude(context: Context, process: ProcessData, includeSelf: Boolean): Boolean {
        val ownPackage = context.packageName
        val name = normalizePackageName(process.packageName)
        if (name in setOf("top", "ps", "sh", "toybox", "toybox-outside", "toybox-outside64")) return true
        return !includeSelf && name == ownPackage
    }

    private fun normalizePackageName(name: String): String =
        if (name.contains(':')) name.substringBefore(':') else name

    private fun formatRes(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) return "—"
        if (value.endsWith("B", ignoreCase = true) || value.endsWith("K", true) || value.endsWith("M", true) || value.endsWith("G", true)) {
            val number = value.dropLast(1).toFloatOrNull()
            if (number != null) {
                return when {
                    value.endsWith("G", true) -> String.format("%.1f GB", number)
                    value.endsWith("M", true) -> String.format("%.1f MB", number)
                    value.endsWith("K", true) -> String.format("%.0f KB", number)
                    else -> value
                }
            }
        }
        return value.toLongOrNull()?.let { kb ->
            if (kb >= 1024) String.format("%.1f MB", kb / 1024f) else "$kb KB"
        } ?: value
    }

    private fun formatStatusMemory(value: String): String {
        val kb = value.substringBefore(' ').toLongOrNull() ?: return value
        return if (kb >= 1024) String.format("%.1f MB", kb / 1024f) else "$kb KB"
    }

    private fun parseCpu(value: String): Float = value.removeSuffix("%").toFloatOrNull() ?: 0f

    private fun parseMemoryKb(value: String): Long {
        val upper = value.uppercase()
        val num = upper.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
        return when {
            upper.contains("GB") || upper.contains("G") -> (num * 1024 * 1024).toLong()
            upper.contains("MB") || upper.contains("M") -> (num * 1024).toLong()
            else -> num.toLong()
        }
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"
}
