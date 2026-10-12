/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.dashboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import android.os.StatFs
import android.os.Environment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.zuan.kernelmanager.next.core.kernel.KernelRuntimeContext
import com.zuan.kernelmanager.next.core.mode.WorkingModeManager
import com.zuan.kernelmanager.next.library.device.battery.BatteryApi
import com.zuan.kernelmanager.next.library.shell.PrivilegedAccess
import com.zuan.kernelmanager.next.library.shell.suBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.roundToInt

data class ProcessInfo(
    val name: String,
    val packageName: String,
    val pid: String,
    val cpuUsage: Float,
    val icon: ImageBitmap? = null
)

data class CoreDetailInfo(
    val coreIndex: Int,
    val governor: String,
    val tunables: Map<String, String>
)

data class OverallBatteryInfo(
    val level: String = "50%",
    val status: String = "Unknown",
    val tech: String = "Li-ion",
    val voltage: String = "0 mV",
    val temp: String = "0 °C",
    val currentNow: Int = 0,
    val wattage: Float = 0f,
    val health: String = "Good",
    val deepSleep: String = "0%",
    val uptime: String = "0s",
    val cycleCount: String = "0",
    val designCapacity: String = "N/A",
    val maximumCapacity: String = "N/A"
)

data class MemoryData(
    val totalMem: Long,
    val usedMem: Long,
    val freeMem: Long,
    val totalSwap: Long,
    val usedSwap: Long,
    val freeSwap: Long,
    val zramAlgorithm: String = "Unknown"
)

data class VMParameters(
    val swappiness: String = "Unknown",
    val extraFreeKbytes: String = "Unknown",
    val watermarkScaleFactor: String = "Unknown",
    val vfsCachePressure: String = "Unknown",
    val dirtyRatio: String = "Unknown",
    val dirtyBgRatio: String = "Unknown"
)

data class DisplayInfo(
    val resolution: String = "Unknown",
    val densityDpi: String = "Unknown",
    val density: String = "Unknown",
    val technology: String = "Unknown",
    val diagonalSize: String = "Unknown",
    val physicalSize: String = "Unknown",
    val refreshRate: String = "Unknown",
    val orientation: String = "Unknown",
    val hdrSupport: String = "Unknown"
)

data class CpuLoadResult(
    val totalLoad: Float,
    val perCoreLoads: List<Int>
)

data class StorageIntelligence(
    val usedBytes: Long = 0L, val totalBytes: Long = 0L, val availableBytes: Long = 0L, val usagePercent: Float = 0f, val inodePercent: Float = 0f
)

data class UfsHealthInfo(
    val storageType: String = "Unknown",
    val healthSupported: Boolean = false,
    val healthPercent: Int? = null,
    val status: String = "Unknown",
    val lifetimeA: String = "Unknown",
    val lifetimeB: String = "Unknown",
    /** Raw `eol_info` / pre-EOL descriptor (0x01 normal, 0x02 warning, 0x03 urgent). */
    val eolInfo: String = "Unknown",
    val temperatureC: Float? = null
)

data class ThermalIntelligence(
    val maxTempC: Float = 0f,
    val avgTempC: Float = 0f,
    val zoneCount: Int = 0,
    val hottestZone: String = "Unknown",
    val throttling: Boolean = false
)

data class SystemCorrelation(
    val score: Int = 0,
    val state: String = "IDLE",
    val summary: String = "Waiting for telemetry",
    val cpuLoad: Float = 0f,
    val gpuLoad: Int = 0,
    val thermalC: Float = 0f,
    val powerW: Float = 0f
)
data class PowerIntelligence(
    val currentW: Float = 0f,
    val voltageV: Float = 0f,
    val currentMa: Int = 0,
    val status: String = "Unknown",
    val trend: String = "STABLE"
)
data class SmartProfile(
    val name: String = "Balanced",
    val reason: String = "Waiting for telemetry",
    val confidence: Int = 0,
    val recommendedAction: String = "Monitor"
)
data class SystemPulse(
    val storageUsedBytes: Long = 0L,
    val storageTotalBytes: Long = 0L,
    val batteryPowerWatts: Float = 0f,
    val batteryHealth: String = "Unknown",
    val rootAvailable: Boolean = false,
    val uptime: String = "0s",
    val awakeTime: String = "0s",
    val deepSleepTime: String = "0s"
)

data class PressureInfo(
    val supported: Boolean = false,
    val cpuSome: Float = 0f,
    val memorySome: Float = 0f,
    val memoryFull: Float = 0f,
    val ioSome: Float = 0f,
    val ioFull: Float = 0f,
    val load1: Float = 0f,
    val load5: Float = 0f,
    val load15: Float = 0f
)
data class SecurityInfo(
    val rootAvailable: Boolean = false,
    val selinux: String = "Unknown",
    val securityPatch: String = "Unknown",
    val verifiedBoot: String = "Unknown"
)

object OverallUtils {

    // Dashboard parsers are implemented in Kotlin.
    // This feature must not depend on a separately-built native library being
    // copied into the APK; otherwise a stale native library can crash the
    // app with UnsatisfiedLinkError after package refactors.
    fun parseMemoryInfo(rawData: String): String {
        var mt = "0"
        var mf = "0"
        var ma = "0"
        var st = "0"
        var sf = "0"
        for (line in rawData.lineSequence()) {
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 2) continue
            when (parts[0]) {
                "MemTotal:" -> mt = parts[1]
                "MemFree:" -> mf = parts[1]
                "MemAvailable:" -> ma = parts[1]
                "SwapTotal:" -> st = parts[1]
                "SwapFree:" -> sf = parts[1]
            }
        }
        return "$mt;$mf;$ma;$st;$sf"
    }

    fun calculateCpuLoad(prevStat: String, currStat: String): String {
        fun parse(stat: String): List<LongArray> = stat.lineSequence()
            .filter { it.startsWith("cpu") }
            .mapNotNull { line ->
                val values = line.trim().split(Regex("\\s+")).drop(1)
                    .mapNotNull { it.toLongOrNull() }
                if (values.size >= 4) values.toLongArray() else null
            }.toList()

        val prev = parse(prevStat)
        val curr = parse(currStat)
        if (prev.isEmpty() || curr.isEmpty()) return "0"

        return buildString {
            for (i in 0 until minOf(prev.size, curr.size)) {
                val p = prev[i]
                val c = curr[i]
                val pTotal = p.sum()
                val cTotal = c.sum()
                val totalDiff = (cTotal - pTotal).coerceAtLeast(0L)
                val workDiff = if (totalDiff > 0L) {
                    ((cTotal - c[3]) - (pTotal - p[3])).coerceAtLeast(0L)
                } else 0L
                val load = if (totalDiff > 0L) {
                    (workDiff.toDouble() / totalDiff.toDouble() * 100.0).toInt()
                } else 0
                if (i > 0) append(';')
                append(load)
            }
        }
    }

    fun parseTopProcesses(rawTop: String): String {
        data class Proc(val pid: String, val cpu: Float, val cmd: String)
        val processes = ArrayList<Proc>(300)

        for (line in rawTop.lineSequence()) {
            val trim = line.trim()
            if (trim.isEmpty() || trim.startsWith("PID") || trim.startsWith("Mem") ||
                trim.startsWith("Tasks") || trim.contains("User")
            ) continue

            val parts = trim.split(Regex("\\s+"))
            if (parts.size < 3 || parts[0].toUIntOrNull() == null) continue

            val pid = parts[0]
            var cpu = parts.getOrNull(1)?.removeSuffix("%")?.toFloatOrNull()
            var cmdStart = 2

            if (cpu == null && parts.size > 8) {
                cpu = parts.getOrNull(8)?.removeSuffix("%")?.toFloatOrNull()
                    ?: parts.getOrNull(9)?.removeSuffix("%")?.toFloatOrNull()
                for (i in parts.indices) {
                    if (parts[i].contains(':') && parts[i].contains('.')) {
                        cmdStart = i + 1
                        break
                    }
                }
                if (cmdStart <= 2 || cmdStart >= parts.size) cmdStart = parts.lastIndex
            }

            val cmd = parts.drop(cmdStart).joinToString(" ")
            if (cmd.startsWith("top ") || cmd == "top") continue
            processes.add(Proc(pid, cpu ?: 0f, cmd))
        }

        processes.sortByDescending { it.cpu }
        val seen = HashSet<String>()
        return buildString {
            var count = 0
            for (p in processes) {
                if (!seen.add(p.pid)) continue
                if (count > 0) append(';')
                append(p.pid).append('|').append(p.cpu).append('|').append(p.cmd)
                count++
                if (count >= 50) break
            }
        }
    }

    // Scoped background work for process metadata lookup
    private val utilsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ---- Layered reader: direct (app uid) -> shell (Shizuku / root) --------------------------
    // Old behaviour cached a path as DEAD *forever* after one failed attempt. Over Shizuku the
    // binder arrives a moment after process start, so the very first reads failed and the whole
    // dashboard stayed at 0 for the session. DEAD is now a short-lived verdict and is wiped
    // whenever the privilege epoch changes (Shizuku connected / permission granted / mode switch).
    private enum class Access { DIRECT, SHELL }
    private val accessCache = ConcurrentHashMap<String, Access>()
    private val deadUntil = ConcurrentHashMap<String, Long>()
    @Volatile private var seenEpoch = -1
    private const val DEAD_TTL_MS = 20_000L
    @Volatile private var rootCacheValue: Boolean = false
    @Volatile private var rootCacheAt: Long = 0L
    @Volatile private var ufsHealthCache: UfsHealthInfo? = null
    @Volatile private var ufsHealthCacheAt: Long = 0L

    private fun syncEpoch() {
        val e = PrivilegedAccess.epoch
        if (e != seenEpoch) {
            seenEpoch = e
            accessCache.clear()
            deadUntil.clear()
            propCache.clear()
            rootCacheAt = 0L
            ufsHealthCache = null
            cpuFreqPaths = null
            gpuLoadProbed = false
            gpuLoadPath = null
        }
    }

    private fun isDead(path: String): Boolean {
        val until = deadUntil[path] ?: return false
        if (SystemClock.elapsedRealtime() < until) return true
        deadUntil.remove(path)
        return false
    }

    private fun markDead(path: String) {
        deadUntil[path] = SystemClock.elapsedRealtime() + DEAD_TTL_MS
        accessCache.remove(path)
    }

    private fun readDirect(path: String): String? = try {
        val f = File(path)
        if (f.isFile && f.canRead()) f.readText().trim().takeIf { it.isNotEmpty() } else null
    } catch (_: Exception) {
        null
    }

    private fun readViaShell(path: String): String? {
        if (!PrivilegedAccess.shellWanted()) return null
        val res = suBlocking("cat ${shellQuote(path)} 2>/dev/null")
        return res.out.joinToString("\n").trim().takeIf { it.isNotEmpty() }
    }

    fun readFile(path: String): String? {
        syncEpoch()
        if (accessCache[path] == Access.DIRECT) readDirect(path)?.let { return it }
        if (isDead(path)) return null
        readDirect(path)?.let {
            accessCache[path] = Access.DIRECT
            return it
        }
        val viaShell = readViaShell(path)
        if (viaShell != null) accessCache[path] = Access.SHELL else markDead(path)
        return viaShell
    }

    fun readLines(path: String): List<String> {
        syncEpoch()
        if (accessCache[path] == Access.DIRECT) {
            try { File(path).readLines().takeIf { it.isNotEmpty() }?.let { return it } } catch (_: Exception) {}
        }
        if (isDead(path)) return emptyList()
        try {
            val f = File(path)
            if (f.isFile && f.canRead()) {
                val lines = f.readLines()
                if (lines.isNotEmpty()) {
                    accessCache[path] = Access.DIRECT
                    return lines
                }
            }
        } catch (e: Exception) { android.util.Log.w("ZKM", "Optional operation failed; continuing", e) }
        if (!PrivilegedAccess.shellWanted()) { markDead(path); return emptyList() }
        val viaShell = suBlocking("cat ${shellQuote(path)} 2>/dev/null").out
        if (viaShell.isNotEmpty()) accessCache[path] = Access.SHELL else markDead(path)
        return viaShell
    }

    fun batchRead(paths: List<String>): Map<String, String> {
        syncEpoch()
        val result = HashMap<String, String>(paths.size)
        val needShell = ArrayList<String>()

        for (p in paths) {
            val direct = readDirect(p)
            if (direct != null) {
                accessCache[p] = Access.DIRECT
                result[p] = direct
            } else if (!isDead(p)) {
                needShell.add(p)
            }
        }

        if (needShell.isNotEmpty() && PrivilegedAccess.shellWanted()) {
            val marker = "##ZKM##"
            val script = needShell.joinToString("; ") { path ->
                "printf '%s\\n' ${shellQuote("$marker$path")}; cat ${shellQuote(path)} 2>/dev/null"
            }
            val out = suBlocking(script).out
            var current: String? = null
            val sb = StringBuilder()
            val seen = HashSet<String>()
            fun flush() {
                current?.let { p ->
                    seen += p
                    val v = sb.toString().trim()
                    if (v.isNotEmpty()) {
                        result[p] = v
                        accessCache[p] = Access.SHELL
                    } else markDead(p)
                }
                sb.setLength(0)
            }
            for (line in out) {
                if (line.startsWith(marker)) {
                    flush()
                    current = line.removePrefix(marker)
                } else {
                    if (sb.isNotEmpty()) sb.append('\n')
                    sb.append(line)
                }
            }
            flush()
            // Shell produced nothing at all (not ready yet): do NOT blacklist anything.
            if (out.isEmpty()) needShell.forEach { deadUntil.remove(it) }
        } else {
            needShell.forEach { markDead(it) }
        }
        return result
    }

    private val propCache = ConcurrentHashMap<String, String>()
    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    /** `getprop` is executable by normal apps and ro.* props are world-readable, so no shell needed. */
    private fun getPropLocal(name: String): String = runCatching {
        val p = ProcessBuilder("getprop", name).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().use { it.readText() }.trim()
        p.waitFor()
        out
    }.getOrDefault("")

    private fun getProp(name: String): String {
        propCache[name]?.let { return it }
        var v = getPropLocal(name)
        if (v.isEmpty() && PrivilegedAccess.shellWanted()) {
            v = suBlocking("getprop ${shellQuote(name)}").out.firstOrNull()?.trim().orEmpty()
        }
        // Do not cache an empty value: it may only mean the shell was not ready yet.
        if (v.isNotEmpty()) propCache[name] = v
        return v
    }

    suspend fun getDeviceModel(): String = withContext(Dispatchers.IO) { getProp("ro.product.model").ifEmpty { Build.MODEL } }
    suspend fun getDeviceCodename(): String = withContext(Dispatchers.IO) { getProp("ro.product.device").ifEmpty { Build.DEVICE } }
    suspend fun getSoCName(): String = withContext(Dispatchers.IO) {
        val platform = getProp("ro.board.platform")
        if (platform.isNotEmpty()) return@withContext platform.uppercase()
        val hardware = readLines("/proc/cpuinfo").firstOrNull { it.startsWith("Hardware") }
        hardware?.substringAfter(":")?.trim() ?: "Unknown SoC"
    }

    suspend fun getKernelVersion(): String = withContext(Dispatchers.IO) {
        readFile("/proc/version")?.split(" ")?.getOrNull(2) ?: System.getProperty("os.version") ?: "Unknown Kernel"
    }

    suspend fun getVMParameters(): VMParameters = withContext(Dispatchers.IO) {
        fun sysctl(param: String): String = readFile("/proc/sys/${param.replace('.', '/')}") ?: "N/A"
        VMParameters(
            swappiness = sysctl("vm.swappiness"),
            extraFreeKbytes = sysctl("vm.extra_free_kbytes"),
            watermarkScaleFactor = sysctl("vm.watermark_scale_factor"),
            vfsCachePressure = sysctl("vm.vfs_cache_pressure"),
            dirtyRatio = sysctl("vm.dirty_ratio"),
            dirtyBgRatio = sysctl("vm.dirty_background_ratio")
        )
    }

    suspend fun getDisplayInfo(context: Context): DisplayInfo = withContext(Dispatchers.IO) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION") val display = windowManager.defaultDisplay
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION") display.getRealMetrics(metrics)

        val resolution = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            "${bounds.width()} x ${bounds.height()}"
        } else "${metrics.widthPixels} x ${metrics.heightPixels}"

        val orientation = when (display.rotation) {
            Surface.ROTATION_0 -> "Portrait"
            Surface.ROTATION_90 -> "Landscape"
            Surface.ROTATION_180 -> "Portrait (Reversed)"
            Surface.ROTATION_270 -> "Landscape (Reversed)"
            else -> "Unknown"
        }

        val hdrSupport = run {
            val hdrTypes = display.hdrCapabilities?.supportedHdrTypes
            when {
                hdrTypes == null || hdrTypes.isEmpty() -> "Not Supported"
                hdrTypes.contains(Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS) -> "HDR10+"
                hdrTypes.contains(Display.HdrCapabilities.HDR_TYPE_HDR10) -> "HDR10"
                hdrTypes.contains(Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION) -> "Dolby Vision"
                hdrTypes.contains(Display.HdrCapabilities.HDR_TYPE_HLG) -> "HLG"
                else -> "Supported"
            }
        }

        val technology = getProp("ro.vendor.display.type").ifEmpty { getProp("ro.display.type") }.ifEmpty { "Unknown" }
        val widthInches = metrics.widthPixels / metrics.xdpi
        val heightInches = metrics.heightPixels / metrics.ydpi
        val diagonalInches = sqrt(widthInches * widthInches + heightInches * heightInches)

        DisplayInfo(
            resolution = resolution,
            densityDpi = "${metrics.densityDpi} DPI",
            density = "${metrics.density}x",
            technology = technology.uppercase(),
            diagonalSize = "%.1f\"".format(diagonalInches),
            physicalSize = "${widthInches.toInt()}\" x ${heightInches.toInt()}\"",
            refreshRate = "${display.refreshRate.toInt()} Hz",
            orientation = orientation,
            hdrSupport = hdrSupport
        )
    }

    suspend fun getCpuGovernor(coreIndex: Int): String = withContext(Dispatchers.IO) {
        readFile("/sys/devices/system/cpu/cpu$coreIndex/cpufreq/scaling_governor") ?: "Unknown"
    }

    suspend fun getGovernorTunables(coreIndex: Int, governor: String): Map<String, String> = withContext(Dispatchers.IO) {
        if (governor == "Unknown") return@withContext emptyMap()
        val possiblePaths = listOf(
            "/sys/devices/system/cpu/cpufreq/policy$coreIndex/$governor",
            "/sys/devices/system/cpu/cpu$coreIndex/cpufreq/$governor",
            "/sys/devices/system/cpu/cpufreq/$governor"
        )
        val validPath = possiblePaths.firstOrNull { File(it).isDirectory }
            ?: possiblePaths.firstOrNull { suBlocking("[ -d ${shellQuote(it)} ] && echo exist").out.firstOrNull() == "exist" }
            ?: return@withContext mapOf("Error" to "Parameters folder not found for $governor")
        
        val files = File(validPath).list()?.toList() ?: suBlocking("ls ${shellQuote(validPath)}").out
        val values = batchRead(files.filter { it.isNotBlank() }.map { "$validPath/$it" })
        buildMap {
            for ((path, value) in values) {
                if (value.length < 50) put(path.substringAfterLast('/'), value)
            }
        }
    }

    @Volatile private var gpuLoadPath: String? = null
    @Volatile private var gpuLoadProbed = false
    private val gpuLoadCandidates = listOf(
        "/sys/class/kgsl/kgsl-3d0/gpubusy",
        "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
        "/sys/class/kgsl/kgsl-3d0/gpu_load",
        "/sys/class/misc/mali0/device/utilization",
        "/sys/kernel/gpu/gpu_busy"
    )

    suspend fun getGpuDetail(): String = withContext(Dispatchers.IO) {
        try {
            val dumpsys = suBlocking("dumpsys SurfaceFlinger | grep 'GLES:'").out.firstOrNull()
            if (!dumpsys.isNullOrEmpty()) return@withContext dumpsys.replace("GLES:", "").trim()
        } catch (e: Exception) { android.util.Log.w("ZKM", "Optional operation failed; continuing", e) }
        readFile("/sys/class/kgsl/kgsl-3d0/gpu_model")?.replace("gpu", "")?.trim() ?: "GPU Info Not Available"
    }

    private fun parseGpuLoad(path: String, raw: String): Int? = when {
        path.endsWith("gpubusy") -> {
            val parts = raw.trim().split("\\s+".toRegex())
            val busy = parts.getOrNull(0)?.toFloatOrNull()
            val total = parts.getOrNull(1)?.toFloatOrNull()
            if (busy != null && total != null && total > 0) ((busy / total) * 100).toInt() else null
        }
        else -> raw.replace(Regex("[^0-9]"), "").toIntOrNull()
    }

    suspend fun getGpuLoadAndFreq(): Pair<String, Int> = withContext(Dispatchers.IO) {
        val freq = readFile("/sys/class/kgsl/kgsl-3d0/gpuclk")?.toLongOrNull()
        val freqStr = if (freq != null) "${freq / 1_000_000} MHz" else "0 MHz"
        gpuLoadPath?.let { path ->
            val raw = readFile(path)
            if (raw != null) parseGpuLoad(path, raw)?.let { return@withContext freqStr to it }
        }
        if (!gpuLoadProbed) {
            gpuLoadProbed = true
            for (path in gpuLoadCandidates) {
                val raw = readFile(path) ?: continue
                val load = parseGpuLoad(path, raw) ?: continue
                gpuLoadPath = path
                return@withContext freqStr to load
            }
        }
        freqStr to 0
    }

    private data class AppMeta(val label: String, val icon: ImageBitmap?)
    private val appMetaCache = ConcurrentHashMap<String, AppMeta>()
    private val NO_META = AppMeta("", null)

    suspend fun getTopProcesses(context: Context): List<ProcessInfo> = withContext(Dispatchers.IO) {
        if (!PrivilegedAccess.shellWanted()) return@withContext emptyList()
        val rawOut = suBlocking("top -b -n 2 -d 0.2 -o PID,%CPU,CMDLINE 2>/dev/null || top -b -n 2 -d 0.2 2>/dev/null").out
        val rawString = rawOut.joinToString("\n")

        val parsedProcesses = parseTopProcesses(rawString)
        if (parsedProcesses.isEmpty()) return@withContext emptyList()

        val list = ArrayList<ProcessInfo>()
        val pm = context.packageManager
        val processItems = parsedProcesses.split(";")

        for (item in processItems) {
            if (item.isBlank()) continue
            val parts = item.split("|")
            if (parts.size < 3) continue

            val pid = parts[0]
            val cpuUsage = parts[1].toFloatOrNull() ?: 0f
            val processName = parts[2]

            val isKernel = processName.startsWith("[") && processName.endsWith("]")
            var displayName = processName
            var iconBitmap: ImageBitmap? = null

            if (!isKernel) {
                val pkg = processName.substringBefore(':')
                val meta = appMetaCache[pkg]

                if (meta != null) {
                    if (meta !== NO_META) {
                        displayName = meta.label
                        iconBitmap = meta.icon
                    }
                } else {
                    appMetaCache[pkg] = NO_META
                    
                    // Keep metadata lookup bounded to the utility scope
                    utilsScope.launch {
                        try {
                            val appInfo = pm.getApplicationInfo(pkg, 0)
                            val label = pm.getApplicationLabel(appInfo).toString()
                            val icon = drawableToBitmap(pm.getApplicationIcon(appInfo)).asImageBitmap()
                            appMetaCache[pkg] = AppMeta(label, icon)
                        } catch (e: Exception) { android.util.Log.w("ZKM", "Optional operation failed; continuing", e) }
                    }
                }
            }
            list.add(ProcessInfo(displayName, processName, pid, cpuUsage, iconBitmap))
        }
        list.take(50)
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        val size = 96 
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }

    @Volatile
    private var prevCpuStatStr: String = ""

    suspend fun getCpuLoads(): CpuLoadResult = withContext(Dispatchers.IO) {
        val currStatStr = readLines("/proc/stat").joinToString("\n")
        // /proc/stat is hidden from normal apps since Android 8. Shizuku / root read it fine;
        // BASIC mode estimates the load from how far each core's clock is above its minimum.
        if (currStatStr.isEmpty()) return@withContext estimateCpuLoadFromFreq()

        val cpuLoadOutput = calculateCpuLoad(prevCpuStatStr, currStatStr)
        prevCpuStatStr = currStatStr 

        val loads = cpuLoadOutput.split(";")
        val total = loads.getOrNull(0)?.toFloatOrNull()?.div(100f) ?: 0f

        val perCore = ArrayList<Int>()
        for (i in 1 until loads.size) {
            perCore.add(loads[i].toIntOrNull() ?: 0)
        }
        CpuLoadResult(total, perCore)
    }

    @Volatile private var cpuFreqPaths: List<String>? = null

    /** Core count even when /sys/devices/system/cpu cannot be listed by the app. */
    private fun cpuCoreCount(): Int {
        val listed = File("/sys/devices/system/cpu").listFiles { f -> f.name.matches(Regex("cpu[0-9]+")) }?.size ?: 0
        return maxOf(listed, Runtime.getRuntime().availableProcessors(), 1)
    }

    private fun freqPaths(): List<String> = cpuFreqPaths ?: run {
        val n = cpuCoreCount()
        (0 until n).map { "/sys/devices/system/cpu/cpu$it/cpufreq/scaling_cur_freq" }
            .also { cpuFreqPaths = it }
    }

    suspend fun getCpuFrequencies(): List<String> = withContext(Dispatchers.IO) {
        val paths = freqPaths()
        // One batched read instead of N separate shell spawns (matters a lot over Shizuku).
        val values = batchRead(paths)
        paths.map { path ->
            val freq = values[path]?.trim()?.toLongOrNull()
            if (freq != null) "${freq / 1000} MHz" else "Offline"
        }
    }

    /**
     * Approximate load when /proc/stat is unavailable (BASIC mode).
     * load = (cur - min) / (max - min) per core. It tracks governor ramp-up, not true busy time,
     * so it is only used as a fallback and never when real counters can be read.
     */
    private suspend fun estimateCpuLoadFromFreq(): CpuLoadResult = withContext(Dispatchers.IO) {
        val n = cpuCoreCount()
        val cur = (0 until n).map { "/sys/devices/system/cpu/cpu$it/cpufreq/scaling_cur_freq" }
        val min = (0 until n).map { "/sys/devices/system/cpu/cpu$it/cpufreq/cpuinfo_min_freq" }
        val max = (0 until n).map { "/sys/devices/system/cpu/cpu$it/cpufreq/cpuinfo_max_freq" }
        val v = batchRead(cur + min + max)
        val perCore = (0 until n).map { i ->
            val c = v[cur[i]]?.trim()?.toLongOrNull() ?: return@map 0
            val lo = v[min[i]]?.trim()?.toLongOrNull() ?: 0L
            val hi = v[max[i]]?.trim()?.toLongOrNull() ?: 0L
            if (hi <= lo) 0 else (((c - lo).toFloat() / (hi - lo)) * 100f).toInt().coerceIn(0, 100)
        }
        val any = (0 until n).any { v[cur[it]] != null }
        if (!any) return@withContext CpuLoadResult(0f, perCore)
        CpuLoadResult(perCore.average().toFloat() / 100f, perCore)
    }

    private const val BAT = "/sys/class/power_supply/battery"
    private val batteryFiles = listOf(
        "$BAT/status", "$BAT/voltage_now", "$BAT/current_now", "$BAT/temp",
        "$BAT/capacity", "$BAT/cycle_count", "$BAT/charge_full_design",
        "$BAT/charge_full", "$BAT/health", "$BAT/technology"
    )





    fun calculateSystemCorrelation(cpuLoad: Float, gpuLoad: Int, thermalC: Float, powerW: Float): SystemCorrelation {
        val thermal = ((thermalC - 40f) / 45f).coerceIn(0f, 1f)
        val cpu = cpuLoad.coerceIn(0f, 1f)
        val gpu = (gpuLoad / 100f).coerceIn(0f, 1f)
        val power = (powerW / 20f).coerceIn(0f, 1f)
        val score = ((cpu * .30f + gpu * .25f + thermal * .30f + power * .15f) * 100f).toInt()
        val state = when { score >= 80 -> "HIGH LOAD"; score >= 55 -> "ACTIVE"; score >= 25 -> "BALANCED"; else -> "IDLE" }
        val summary = when (state) {
            "HIGH LOAD" -> "CPU/GPU and thermal activity are elevated"
            "ACTIVE" -> "System is actively processing workloads"
            "BALANCED" -> "Telemetry is within a balanced range"
            else -> "System activity is currently light"
        }
        return SystemCorrelation(score, state, summary, cpuLoad, gpuLoad, thermalC, powerW)
    }

    /**
     * UFS health discovery.
     *
     * The JEDEC health descriptor is exposed by the Linux UFS core as
     * `<ufshc>/health_descriptor/{eol_info,life_time_estimation_a,life_time_estimation_b}`.
     * The host controller node differs per SoC (1d84000.ufshc on SM8450 / SM8350 / SM8550,
     * 1d84000.ufshc on most Snapdragons, 11270000.ufshci on MediaTek, ...), so instead of
     * hardcoding a single path we ask root to locate every `health_descriptor` directory
     * under /sys/devices and read it in one shell invocation.
     *
     * Notes:
     *  - Reads are done through a fresh root command instead of [readFile]; [readFile]
     *    permanently caches a path as DEAD if root was not ready on the first attempt,
     *    which made UFS health stay "Unknown" for the whole session.
     *  - Negative results are cached for 60 s, positive for 5 min, so the (bounded) sysfs
     *    scan never runs every second.
     */
    suspend fun getUfsHealth(): UfsHealthInfo = withContext(Dispatchers.IO) {
        val now = SystemClock.elapsedRealtime()
        ufsHealthCache?.let { cached ->
            val ttl = if (cached.healthSupported) 300_000L else 60_000L
            if (now - ufsHealthCacheAt < ttl) return@withContext cached
        }

        var lifetimeA = ""
        var lifetimeB = ""
        var eolInfo = ""

        fun consume(name: String, rawValue: String) {
            val value = rawValue.trim()
            if (value.isEmpty() || value.contains("No such file", true) || value.contains("denied", true)) return
            val key = name.substringAfterLast('/').lowercase(Locale.US).replace("_", "").replace("-", "")
            when {
                key == "lifetimeestimationa" || key == "lifetimea" -> if (lifetimeA.isEmpty()) lifetimeA = value
                key == "lifetimeestimationb" || key == "lifetimeb" -> if (lifetimeB.isEmpty()) lifetimeB = value
                key == "lifetimeest" -> if (lifetimeA.isEmpty()) lifetimeA = value else if (lifetimeB.isEmpty()) lifetimeB = value
                key == "eolinfo" || key == "preeolinfo" -> if (eolInfo.isEmpty()) eolInfo = value
            }
        }

        // 1) Fast path - well known descriptor locations (direct read first, then root).
        val wellKnown = listOf(
            "/sys/devices/platform/soc/1d84000.ufshc/health_descriptor",
            "/sys/devices/platform/soc/1d84000.ufshc/health",
            "/sys/devices/platform/soc/1da4000.ufshc/health_descriptor",
            "/sys/devices/platform/soc/4804000.ufshc/health_descriptor",
            "/sys/devices/platform/soc/1d87000.ufshc/health_descriptor",
            "/sys/devices/platform/11270000.ufshci/health_descriptor"
        )
        for (dir in wellKnown) {
            for (leaf in listOf("life_time_estimation_a", "life_time_estimation_b", "eol_info", "lifetimeA", "lifetimeB")) {
                readDirect("$dir/$leaf")?.let { consume(leaf, it) }
            }
        }

        // 2) Root discovery: locate every health_descriptor dir under /sys/devices and dump it.
        if (lifetimeA.isEmpty() || lifetimeB.isEmpty()) {
            // Single-line script so it behaves identically on every su / libsu implementation.
            val script = "for d in ${'$'}(find /sys/devices -maxdepth 9 -type d \\( -name health_descriptor -o -name health \\) 2>/dev/null | head -n 12); do " +
                "for f in \"${'$'}d\"/*; do [ -f \"${'$'}f\" ] && printf '%s=%s\\n' \"${'$'}f\" \"${'$'}(head -n 1 \"${'$'}f\" 2>/dev/null)\"; done; done; " +
                "find /sys/devices -maxdepth 9 -type f \\( -iname 'life_time_estimation_*' -o -iname 'lifetime*' -o -iname 'eol_info' \\) 2>/dev/null | head -n 24 | " +
                "while read -r p; do printf '%s=%s\\n' \"${'$'}p\" \"${'$'}(head -n 1 \"${'$'}p\" 2>/dev/null)\"; done"
            val out = runCatching { suBlocking(script).out }.getOrDefault(emptyList())
            for (line in out) {
                val eq = line.indexOf('=')
                if (eq <= 0) continue
                consume(line.substring(0, eq), line.substring(eq + 1))
            }
        }

        // 3) Last resort: storaged / health HAL summary (only exposes lifetime on some ROMs).
        if (lifetimeA.isEmpty() && lifetimeB.isEmpty()) {
            val dump = runCatching {
                suBlocking("dumpsys storaged 2>/dev/null | grep -i -E 'lifetime|eol' | head -n 8").out
            }.getOrDefault(emptyList())
            for (line in dump) {
                val lower = line.lowercase(Locale.US)
                val value = Regex("(0x[0-9a-fA-F]+|\\d+)\\s*$").find(line.trim())?.value ?: continue
                when {
                    lower.contains("lifetimea") || lower.contains("lifetime_a") || lower.contains("lifetime a") -> if (lifetimeA.isEmpty()) lifetimeA = value
                    lower.contains("lifetimeb") || lower.contains("lifetime_b") || lower.contains("lifetime b") -> if (lifetimeB.isEmpty()) lifetimeB = value
                    lower.contains("eol") -> if (eolInfo.isEmpty()) eolInfo = value
                }
            }
        }

        // ---- storage type detection (unchanged heuristics) ------------------------------
        val bootDevice = getProp("ro.boot.bootdevice").lowercase(Locale.US)
        val blockPathEvidence = runCatching {
            File("/sys/class/block").listFiles()?.any { block ->
                val path = runCatching { block.canonicalFile.path.lowercase(Locale.US) }.getOrDefault("")
                path.contains("ufshc") || path.contains("ufshci") || path.contains("ufshcd")
            } == true
        }.getOrDefault(false)
        val ufsEvidence = bootDevice.contains("ufs") ||
            lifetimeA.isNotEmpty() || lifetimeB.isNotEmpty() ||
            blockPathEvidence ||
            runCatching {
                File("/sys/class/scsi_host").listFiles()?.any {
                    it.name.contains("ufs", ignoreCase = true)
                } == true
            }.getOrDefault(false) ||
            runCatching {
                File("/sys/devices/platform").listFiles()?.any {
                    it.name.contains("ufshc", ignoreCase = true) || it.name.contains("ufshci", ignoreCase = true)
                } == true
            }.getOrDefault(false)

        val emmcEvidence = runCatching {
            File("/sys/block").listFiles()?.any { it.name.startsWith("mmcblk") } == true
        }.getOrDefault(false)

        val storageType = when {
            ufsEvidence -> "UFS"
            emmcEvidence -> "eMMC"
            else -> "Unknown"
        }

        // Linux prints the descriptor as "0x0B"; some vendor nodes print plain decimal.
        val parseCode = { raw: String ->
            val t = raw.trim().substringBefore(' ')
            when {
                t.startsWith("0x", true) -> t.drop(2).toIntOrNull(16)
                t.any { it in 'a'..'f' || it in 'A'..'F' } -> t.toIntOrNull(16)
                else -> t.toIntOrNull()
            }
        }

        // JEDEC: 0x00 = not defined, 0x01..0x0A = 0-10% ... 90-100% of life used, 0x0B = exceeded.
        // Report the midpoint of the remaining-life bucket (0x01 -> 95%, 0x02 -> 85% ...).
        val mapUfsHealth: (Int?) -> Int? = { code ->
            when {
                code == null || code == 0x00 -> null
                code in 0x01..0x0A -> 105 - code * 10
                code >= 0x0B -> 0
                else -> null
            }
        }

        val pctA = mapUfsHealth(parseCode(lifetimeA))
        val pctB = mapUfsHealth(parseCode(lifetimeB))
        // Type A (SLC) and type B (MLC/TLC) wear independently - the worse one decides.
        val health = if (storageType == "UFS") listOfNotNull(pctA, pctB).minOrNull() else null

        val eolCode = parseCode(eolInfo)
        val status = when {
            storageType != "UFS" -> "Unknown"
            eolCode == 0x03 -> "CRITICAL"
            health == null -> "Unknown"
            eolCode == 0x02 || health < 60 -> if (health < 30) "CRITICAL" else "CAUTION"
            health >= 90 -> "HEALTHY"
            else -> "CAUTION"
        }

        // ---- storage temperature ---------------------------------------------------------
        var temperature: Float? = null
        val zones = File("/sys/class/thermal").listFiles { f -> f.isDirectory && f.name.startsWith("thermal_zone") } ?: emptyArray()
        for (zone in zones) {
            val type = readFile("${zone.path}/type")?.lowercase(Locale.US).orEmpty()
            if (!type.contains("ufs") && !type.contains("emmc") && !type.contains("flash") && !type.contains("storage")) continue
            val raw = readFile("${zone.path}/temp")?.toFloatOrNull() ?: continue
            val c = if (raw > 1_000f) raw / 1000f else raw
            if (c in -50f..150f) { temperature = c; break }
        }

        val result = UfsHealthInfo(
            storageType = storageType,
            healthSupported = storageType == "UFS" && health != null,
            healthPercent = health,
            status = status,
            lifetimeA = lifetimeA.ifEmpty { "Unknown" },
            lifetimeB = lifetimeB.ifEmpty { "Unknown" },
            eolInfo = eolInfo.ifEmpty { "Unknown" },
            temperatureC = temperature
        )
        ufsHealthCache = result
        ufsHealthCacheAt = now
        result
    }

    suspend fun getStorageIntelligence(): StorageIntelligence = withContext(Dispatchers.IO) {
        val stat = runCatching { StatFs(Environment.getDataDirectory().absolutePath) }.getOrNull()
        val total = stat?.totalBytes ?: 0L; val avail = stat?.availableBytes ?: 0L; val used = (total-avail).coerceAtLeast(0L)
        val blockTotal = stat?.blockCountLong ?: 0L; val blockFree = stat?.availableBlocksLong ?: 0L
        val blockPct = if (blockTotal > 0) ((blockTotal-blockFree).toFloat()/blockTotal).coerceIn(0f,1f) else 0f
        StorageIntelligence(used,total,avail,if(total>0) used.toFloat()/total else 0f,blockPct)
    }

    suspend fun getThermalIntelligence(): ThermalIntelligence = withContext(Dispatchers.IO) {
        val root = File("/sys/class/thermal")
        val zones = root.listFiles { f -> f.isDirectory && f.name.startsWith("thermal_zone") } ?: emptyArray()
        var max = -Float.MAX_VALUE
        var sum = 0f
        var count = 0
        var hottest = "Unknown"

        fun normalizeTemperature(raw: Float): Float? {
            // Linux Android thermal sysfs normally exposes millidegrees C.
            // A few vendor nodes expose degrees directly, so accept both.
            val c = when {
                raw > 1000f -> raw / 1000f
                else -> raw
            }
            return c.takeIf { it in -50f..150f }
        }

        for (zone in zones) {
            val raw = readFile("${zone.path}/temp")?.trim()?.toFloatOrNull() ?: continue
            val c = normalizeTemperature(raw) ?: continue
            count++
            sum += c
            if (c > max) {
                max = c
                hottest = readFile("${zone.path}/type")?.ifBlank { zone.name } ?: zone.name
            }
        }

        if (count == 0) {
            // No readable thermal zone (BASIC mode on most ROMs): use the battery sensor so the
            // widget shows real data instead of 0.0 degrees.
            val t = KernelRuntimeContext.context?.let { BatteryApi.read(it).tempC } ?: 0f
            if (t > 0f) { max = t; sum = t; count = 1; hottest = "battery" }
        }
        val maxTemp = if (count > 0) max else 0f
        ThermalIntelligence(
            maxTempC = maxTemp,
            avgTempC = if (count > 0) sum / count else 0f,
            zoneCount = count,
            hottestZone = hottest,
            throttling = maxTemp >= 80f
        )
    }

    suspend fun getSystemPulse(context: Context): SystemPulse = withContext(Dispatchers.IO) {
        val stat = runCatching { StatFs(Environment.getDataDirectory().absolutePath) }.getOrNull()
        val total = stat?.totalBytes ?: 0L
        val available = stat?.availableBytes ?: 0L
        val battery = getDetailedBatteryInfo()
        val now = SystemClock.elapsedRealtime()
        val rootAvailable = if (now - rootCacheAt >= 10_000L) {
            val result = WorkingModeManager.current.hasRoot && runCatching { suBlocking("id").isSuccess }.getOrDefault(false)
            rootCacheValue = result
            rootCacheAt = now
            result
        } else rootCacheValue
        SystemPulse(
            storageUsedBytes = (total - available).coerceAtLeast(0L),
            storageTotalBytes = total,
            batteryPowerWatts = battery.wattage,
            batteryHealth = battery.health,
            rootAvailable = rootAvailable
        )
    }

    private fun parsePsiAvg10(lines: List<String>, kind: String): Float? =
        lines.firstOrNull { it.startsWith("$kind ") }
            ?.split(' ')
            ?.firstOrNull { it.startsWith("avg10=") }
            ?.removePrefix("avg10=")
            ?.toFloatOrNull()

    /** Linux PSI (pressure stall information) + load average. Dibaca via root bila perlu. */
    suspend fun getPressureInfo(): PressureInfo = withContext(Dispatchers.IO) {
        val cpu = readLines("/proc/pressure/cpu")
        val mem = readLines("/proc/pressure/memory")
        val io = readLines("/proc/pressure/io")
        val load = readFile("/proc/loadavg")?.split(' ').orEmpty()
        PressureInfo(
            supported = cpu.isNotEmpty() || mem.isNotEmpty() || io.isNotEmpty(),
            cpuSome = parsePsiAvg10(cpu, "some") ?: 0f,
            memorySome = parsePsiAvg10(mem, "some") ?: 0f,
            memoryFull = parsePsiAvg10(mem, "full") ?: 0f,
            ioSome = parsePsiAvg10(io, "some") ?: 0f,
            ioFull = parsePsiAvg10(io, "full") ?: 0f,
            load1 = load.getOrNull(0)?.toFloatOrNull() ?: 0f,
            load5 = load.getOrNull(1)?.toFloatOrNull() ?: 0f,
            load15 = load.getOrNull(2)?.toFloatOrNull() ?: 0f
        )
    }

    /** Status root, mode SELinux, security patch, dan verified boot state. */
    suspend fun getSecurityInfo(): SecurityInfo = withContext(Dispatchers.IO) {
        val now = SystemClock.elapsedRealtime()
        val rootAvailable = if (now - rootCacheAt >= 10_000L) {
            val result = WorkingModeManager.current.hasRoot && runCatching { suBlocking("id").isSuccess }.getOrDefault(false)
            rootCacheValue = result
            rootCacheAt = now
            result
        } else rootCacheValue

        val selinux = when (readFile("/sys/fs/selinux/enforce")?.trim()) {
            "1" -> "Enforcing"
            "0" -> "Permissive"
            else -> "Unknown"
        }
        val patch = Build.VERSION.SECURITY_PATCH.takeIf { it.isNotBlank() } ?: "Unknown"
        val boot = getProp("ro.boot.verifiedbootstate").ifBlank { "Unknown" }
            .replaceFirstChar { it.uppercase() }
        SecurityInfo(
            rootAvailable = rootAvailable,
            selinux = selinux,
            securityPatch = patch,
            verifiedBoot = boot
        )
    }

    suspend fun getDetailedBatteryInfo(): OverallBatteryInfo = withContext(Dispatchers.IO) {
        val values = batchRead(batteryFiles)
        fun read(file: String) = values["$BAT/$file"] ?: ""
        // Public-API layer: always available, fills whatever sysfs could not give us.
        val api = KernelRuntimeContext.context?.let { runCatching { BatteryApi.read(it) }.getOrNull() }

        val statusRaw = read("status").ifEmpty { api?.statusText ?: "Unknown" }

        val voltRaw = read("voltage_now").toFloatOrNull() ?: 0f
        var voltFloat = if (voltRaw > 1_000_000) voltRaw / 1_000_000 else if (voltRaw > 1000) voltRaw / 1000 else voltRaw
        if (voltFloat <= 0f) voltFloat = api?.voltageV ?: 0f

        var currentNow = read("current_now").toIntOrNull() ?: 0
        if (abs(currentNow) > 100_000) currentNow /= 1000
        if (currentNow == 0) currentNow = api?.currentMa ?: 0

        val tempRaw = read("temp").toFloatOrNull() ?: 0f
        var tempFloat = when {
            tempRaw > 10_000 -> tempRaw / 1000f
            tempRaw > 100 -> tempRaw / 10f
            else -> tempRaw
        }
        if (tempFloat <= 0f) tempFloat = api?.tempC ?: 0f

        val wattage = abs(voltFloat * (currentNow.toFloat() / 1000f))

        var designCap = read("charge_full_design").toDoubleOrNull() ?: 0.0
        var maxCap = read("charge_full").toDoubleOrNull() ?: 0.0
        var dCap = if (designCap > 100_000) designCap / 1000 else designCap
        var mCap = if (maxCap > 100_000) maxCap / 1000 else maxCap
        if (dCap <= 0.0) dCap = (api?.designCapacityMah ?: 0f).toDouble()
        if (mCap <= 0.0) mCap = (api?.estimatedFullMah ?: 0f).toDouble()

        val levelText = read("capacity").ifEmpty { api?.levelPercent?.toString() ?: "0" }
        val healthText = read("health").ifEmpty { api?.healthText?.takeIf { it != "Unknown" } ?: "Good" }
        val cycles = read("cycle_count").ifEmpty { api?.cycleCount?.toString() ?: "0" }

        val realMillis = SystemClock.elapsedRealtime()
        val deepSleepMillis = realMillis - SystemClock.uptimeMillis()
        val dsPercent = if (realMillis > 0) (deepSleepMillis.toFloat() / realMillis) * 100f else 0f

        OverallBatteryInfo(
            level = "$levelText%",
            status = statusRaw,
            tech = read("technology").ifEmpty { api?.technology?.ifBlank { null } ?: "Li-ion" },
            voltage = "%.3f V".format(Locale.US, voltFloat),
            temp = "%.1f °C".format(Locale.US, tempFloat),
            currentNow = currentNow,
            wattage = wattage,
            health = healthText,
            cycleCount = cycles,
            uptime = formatDuration(realMillis),
            designCapacity = if (dCap > 0) "${dCap.toInt()} mAh" else "N/A",
            maximumCapacity = if (mCap > 0) "${mCap.toInt()} mAh" else "N/A",
            deepSleep = "${formatDuration(deepSleepMillis)} (${"%.0f".format(dsPercent)}%)"
        )
    }

    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return String.format(Locale.US, "%02dh %02dm", h, m)
    }
}
