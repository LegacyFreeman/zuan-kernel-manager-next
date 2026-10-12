package com.zuan.kernelmanager.next.library.device.monitor

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.os.BatteryManager
import android.view.WindowManager
import com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime
import com.zuan.kernelmanager.next.core.kernel.KernelRuntimeContext
import com.zuan.kernelmanager.next.library.device.battery.BatteryApi
import com.zuan.kernelmanager.next.library.device.usage.UsageStatsHelper
import com.zuan.kernelmanager.next.library.shell.PrivilegedAccess
import com.topjohnwu.superuser.Shell
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.abs

object MonitorReader {
    private const val TAG = "MonitorReader"
    private var lastTotal = 0L
    private var lastIdle = 0L
    private var shellReady = false

    private val cpuFreqCandidates =
        (0..9).map { "/sys/devices/system/cpu/cpufreq/policy$it/scaling_cur_freq" } +
            listOf("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
    /** Semua cluster yang ada; frekuensi yang ditampilkan = yang tertinggi (cluster prime/big). */
    private var cpuFreqPaths: List<String> = emptyList()
    private var nodeDiscoveryDone = false

    data class RamInfo(val usedMb: Int, val totalMb: Int, val percent: Int)

    data class MonitorSnapshot(
        val fps: Float = 0f,
        val cpuLoad: Int = 0,
        val cpuFreqMHz: Float = 0f,
        /** 0..100, -1 = unknown (shown as N/A). */
        val gpuLoad: Int = 0,
        val gpuFreqMHz: Float = 0f,
        /** true = load is derived from the clock ratio, not read from a load node. */
        val gpuLoadEstimated: Boolean = false,
        val gpuName: String = "GPU",
        val temp: Float = 0f,
        val watt: Float = 0f,
        val ram: RamInfo = RamInfo(0, 0, 0),
        val batteryLevel: Int = 0,
        val batteryVoltageMv: Float = 0f,
        val batteryCurrentMa: Float = 0f,
        val refreshRateHz: Float = 0f,
        val renderer: String = "FPS",
        val foregroundPackage: String = ""
    )

    fun initializeShell() {
        if (shellReady) return
        // Only ROOT mode may open a libsu root shell (it would trigger the su prompt).
        if (!com.zuan.kernelmanager.next.core.mode.WorkingModeManager.current.hasRoot) return
        Thread {
            shellReady = runCatching { Shell.getShell().isRoot }.getOrDefault(false)
        }.start()
    }

    @Volatile private var seenEpoch = -1

    private fun syncEpoch() {
        val e = PrivilegedAccess.epoch
        if (e != seenEpoch) {
            seenEpoch = e
            nodeDiscoveryDone = false
            lastTotal = 0L
            lastIdle = 0L
            cachedFgAt = 0L
            rendererPkg = ""
        }
    }

    private fun discoverNodes() {
        syncEpoch()
        if (nodeDiscoveryDone) return
        val exists = KernelRootRuntime.nodeExistsBatchBlocking(cpuFreqCandidates)
        cpuFreqPaths = cpuFreqCandidates.filter { exists[it] == true }
        // GPU nodes are handled (and re-discovered) by GpuReader.
        // Only trust a "nothing exists" verdict when a shell could actually have answered.
        nodeDiscoveryDone = cpuFreqPaths.isNotEmpty() || !PrivilegedAccess.shellWanted() || PrivilegedAccess.shellUsable()
    }

    @Volatile private var cachedFg: String = ""
    @Volatile private var cachedFgAt: Long = 0L

    private val ignoredForeground = setOf("com.android.systemui", "com.miui.home", "android")

    /**
     * Foreground app, cheapest source first:
     *  1. UsageStats (no shell; ADB mode grants it automatically through Shizuku)
     *  2. one single dumpsys call over Shizuku / root
     * Result is cached for 700 ms so the overlay, the recorder and the renderer probe share one lookup.
     */
    fun getForegroundPackage(): String {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - cachedFgAt < 700L && cachedFg.isNotEmpty()) return cachedFg
        val found = resolveForeground()
        if (found.isNotEmpty()) { cachedFg = found; cachedFgAt = now }
        return found
    }

    /** Foreground app as seen by the window manager (authoritative), used by the FPS engine. */
    class ForegroundApp(val pkg: String, val activity: String, val viaShell: Boolean)

    @Volatile private var cachedApp: ForegroundApp = ForegroundApp("", "", false)
    @Volatile private var cachedAppAt: Long = 0L

    private val resumedActivity = Regex(
        "(?:topResumedActivity|mResumedActivity|ResumedActivity).*?ActivityRecord\\{[0-9a-fA-F]+\\s+(?:\\S+\\s+)?([a-zA-Z0-9._]+)/([a-zA-Z0-9._$]*)"
    )
    private val focusedWindow = Regex(
        "Window\\{[0-9a-fA-F]+\\s+(?:\\S+\\s+)?([a-zA-Z0-9._]+)/([a-zA-Z0-9._$]+)"
    )

    /**
     * FPS needs the package *and* activity of what is really on screen. UsageStats can lag behind
     * or name a different task, which made the layer lookup miss and the FPS stay at 0, so the
     * shell answer (`dumpsys activity`) goes first and UsageStats is only the fallback.
     * Cached for 1 s: overlay, recorder and FPS engine share one lookup.
     */
    fun getForegroundApp(): ForegroundApp {
        val now = android.os.SystemClock.elapsedRealtime()
        val cur = cachedApp
        if (now - cachedAppAt < 1000L && cur.pkg.isNotEmpty()) return cur
        var found: ForegroundApp? = null
        if (PrivilegedAccess.shellWanted()) {
            runCatching {
                val res = KernelRootRuntime.executeBlocking(
                    "dumpsys activity activities 2>/dev/null | grep -iE 'topResumedActivity|mResumedActivity|ResumedActivity' | head -n 4"
                )
                val text = res.outputString
                val m = resumedActivity.find(text)
                if (m != null && m.groupValues[1].contains('.') && m.groupValues[1] !in ignoredForeground) {
                    found = ForegroundApp(m.groupValues[1], expandActivity(m.groupValues[1], m.groupValues[2]), true)
                }
            }
            if (found == null) {
                runCatching {
                    val res = KernelRootRuntime.executeBlocking(
                        "dumpsys window 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' | head -n 4"
                    )
                    val text = res.outputString
                    val m = focusedWindow.find(text)
                        ?: Regex("ActivityRecord\\{[0-9a-fA-F]+\\s+(?:\\S+\\s+)?([a-zA-Z0-9._]+)/([a-zA-Z0-9._$]*)").find(text)
                    if (m != null && m.groupValues[1].contains('.') && m.groupValues[1] !in ignoredForeground) {
                        found = ForegroundApp(m.groupValues[1], expandActivity(m.groupValues[1], m.groupValues[2]), true)
                    }
                }
            }
        }
        val result = found ?: ForegroundApp(getForegroundPackage(), "", false)
        if (result.pkg.isNotEmpty()) {
            cachedApp = result
            cachedAppAt = now
        }
        return result
    }

    private fun expandActivity(pkg: String, act: String): String = when {
        act.isEmpty() -> ""
        act.startsWith(".") -> pkg + act
        else -> act
    }

    private fun resolveForeground(): String {
        val ctx = KernelRuntimeContext.context
        if (ctx != null) {
            val viaUsage = runCatching { UsageStatsHelper.foregroundPackage(ctx) }.getOrDefault("")
            if (viaUsage.isNotEmpty() && viaUsage !in ignoredForeground && viaUsage != ctx.packageName) return viaUsage
        }
        if (!PrivilegedAccess.shellWanted()) return ""
        return try {
            val res = KernelRootRuntime.executeBlocking(
                "dumpsys activity activities 2>/dev/null | grep -E 'topResumedActivity|mResumedActivity' | head -n 3"
            )
            val fromActivities = if (res.isSuccess) parsePackageFromDump(res.outputString) else ""
            if (fromActivities.isNotEmpty()) return fromActivities
            val win = KernelRootRuntime.executeBlocking("dumpsys window 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' | head -n 3")
            if (win.isSuccess) parsePackageFromDump(win.outputString) else ""
        } catch (_: Exception) { "" }
    }

    private fun parsePackageFromDump(rawOutput: String): String {
        val matcher = Pattern.compile("([a-zA-Z0-9_]+\\.[a-zA-Z0-9_\\.]+)/").matcher(rawOutput)
        while (matcher.find()) {
            val found = matcher.group(1).orEmpty()
            if (found !in ignoredForeground) return found
        }
        return ""
    }

    fun getAppName(context: Context, packageName: String): String {
        if (packageName == "Unknown App" || packageName.isEmpty()) return "Unknown App"
        return runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
    }

    fun getAppIcon(context: Context, packageName: String): Drawable? {
        if (packageName == "Unknown App" || packageName.isEmpty()) return null
        return runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
    }

    @Volatile private var rendererPkg: String = ""
    @Volatile private var rendererValue: String = "FPS"

    /**
     * VULKAN / OPENGL detection reads /proc/<pid>/maps, which only root may do for other apps.
     * Over Shizuku (shell uid) that read is denied, so the label simply stays "FPS" there.
     */
    fun getCurrentRenderer(): String {
        if (!PrivilegedAccess.shellWanted()) return "FPS"
        return try {
            val pkgName = getForegroundPackage()
            if (pkgName.isBlank()) return "FPS"
            if (pkgName == rendererPkg) return rendererValue
            val pid = KernelRootRuntime.executeBlocking("pidof $pkgName").outputString.trim().split(" ").firstOrNull()
                ?: return "FPS"
            val maps = KernelRootRuntime.executeBlocking(
                "grep -o -E 'libvulkan\\.so|libGLES[a-zA-Z0-9_]*\\.so' /proc/$pid/maps 2>/dev/null | sort -u"
            ).outputString
            val value = when {
                maps.contains("libvulkan.so") -> "VULKAN"
                maps.contains("libGLES") -> "OPENGL"
                else -> "FPS"
            }
            // Only remember a real answer; an unreadable maps file must be retried next time.
            if (value != "FPS") { rendererPkg = pkgName; rendererValue = value }
            value
        } catch (_: Exception) { "FPS" }
    }

    /** BASIC-mode fallback when /proc/stat is hidden: average (cur-min)/(max-min) over all cores. */
    private fun estimateCpuLoadFromFreq(): Int {
        val n = Runtime.getRuntime().availableProcessors().coerceIn(1, 16)
        val base = "/sys/devices/system/cpu/cpu"
        val paths = (0 until n).flatMap {
            listOf("$base$it/cpufreq/scaling_cur_freq", "$base$it/cpufreq/cpuinfo_min_freq", "$base$it/cpufreq/cpuinfo_max_freq")
        }
        val v = KernelRootRuntime.readNodesBlocking(paths)
        val loads = (0 until n).mapNotNull { i ->
            val c = v["$base$i/cpufreq/scaling_cur_freq"]?.trim()?.toLongOrNull() ?: return@mapNotNull null
            val lo = v["$base$i/cpufreq/cpuinfo_min_freq"]?.trim()?.toLongOrNull() ?: 0L
            val hi = v["$base$i/cpufreq/cpuinfo_max_freq"]?.trim()?.toLongOrNull() ?: return@mapNotNull null
            if (hi <= lo) null else (((c - lo).toFloat() / (hi - lo)) * 100f).toInt().coerceIn(0, 100)
        }
        return if (loads.isEmpty()) 0 else loads.average().toInt()
    }

    private fun readCpuLoadFromStat(): Int {
        val statRaw = KernelRootRuntime.readNodeBlocking("/proc/stat")
        if (statRaw.isBlank()) return estimateCpuLoadFromFreq()
        val line = statRaw.lineSequence().firstOrNull { it.startsWith("cpu ") } ?: return estimateCpuLoadFromFreq()
        val p = line.trim().split(Regex("\\s+"))
        if (p.size < 5) return 0
        val user = p.getOrNull(1)?.toLongOrNull() ?: 0L
        val nice = p.getOrNull(2)?.toLongOrNull() ?: 0L
        val system = p.getOrNull(3)?.toLongOrNull() ?: 0L
        val idle = p.getOrNull(4)?.toLongOrNull() ?: 0L
        val iowait = p.getOrNull(5)?.toLongOrNull() ?: 0L
        val irq = p.getOrNull(6)?.toLongOrNull() ?: 0L
        val softirq = p.getOrNull(7)?.toLongOrNull() ?: 0L
        val steal = p.getOrNull(8)?.toLongOrNull() ?: 0L
        val total = user + nice + system + idle + iowait + irq + softirq + steal
        val idleAll = idle + iowait
        if (lastTotal == 0L) { lastTotal = total; lastIdle = idleAll; return 0 }
        val dt = total - lastTotal
        val di = idleAll - lastIdle
        lastTotal = total
        lastIdle = idleAll
        if (dt <= 0L) return 0
        return (((dt - di) * 100L) / dt).toInt().coerceIn(0, 100)
    }

    /**
     * Nilai sysfs bisa dalam kHz (umum: 1804800), Hz (1804800000) atau sudah MHz (1804).
     * Versi lama membagi kHz dengan 1_000_000 sehingga 1.8 GHz terbaca "1.8 MHz" -> UI menampilkan 0,00 GHz.
     */
    private fun parseFrequencyMHz(raw: String): Float {
        val value = raw.trim().split(Regex("\\s+"))
            .asSequence().mapNotNull { it.toFloatOrNull() }.firstOrNull() ?: return 0f
        return when {
            value >= 10_000_000f -> value / 1_000_000f   // Hz
            value >= 10_000f -> value / 1000f            // kHz
            else -> value                                // sudah MHz
        }
    }

    fun getEnhancedSnapshot(context: Context, includeRenderer: Boolean = false): MonitorSnapshot {
        discoverNodes()
        val paths = buildList {
            addAll(cpuFreqPaths)
            add("/sys/class/power_supply/battery/voltage_now")
            add("/sys/class/power_supply/battery/current_now")
            add("/sys/class/power_supply/battery/capacity")
        }
        val nodes = KernelRootRuntime.readNodesBlocking(paths)
        val api = runCatching { BatteryApi.read(context) }.getOrNull()
        val temp = api?.tempC ?: 0f
        var voltageMv = (nodes["/sys/class/power_supply/battery/voltage_now"]?.trim()?.toLongOrNull() ?: 0L) / 1000f
        // Some kernels already report mV (or V*1000): normalise before trusting it.
        if (voltageMv > 100_000f) voltageMv /= 1000f
        if (voltageMv <= 0f) voltageMv = (api?.voltageV ?: 0f) * 1000f
        var currentMa = abs(nodes["/sys/class/power_supply/battery/current_now"]?.trim()?.toLongOrNull() ?: 0L) / 1000f
        if (currentMa <= 0f) currentMa = abs(api?.currentMa ?: 0).toFloat()
        val batteryLevel = nodes["/sys/class/power_supply/battery/capacity"]?.trim()?.toIntOrNull()
            ?: api?.levelPercent ?: 0
        val ram = getRamInfo(context)
        val refresh = runCatching {
            context.getSystemService(WindowManager::class.java)?.defaultDisplay?.refreshRate ?: 0f
        }.getOrDefault(0f)
        val renderer = if (includeRenderer) getCurrentRenderer() else ""
        val gpu = runCatching { GpuReader.sample() }.getOrDefault(GpuReader.GpuSample())

        return MonitorSnapshot(
            cpuLoad = readCpuLoadFromStat(),
            cpuFreqMHz = cpuFreqPaths.maxOfOrNull { parseFrequencyMHz(nodes[it].orEmpty()) } ?: 0f,
            gpuLoad = gpu.loadPct,
            gpuFreqMHz = gpu.freqMHz.toFloat(),
            gpuLoadEstimated = gpu.loadEstimated,
            gpuName = gpu.gpuName,
            temp = temp,
            watt = if (voltageMv > 0f && currentMa > 0f) voltageMv * currentMa / 1_000_000f else 0f,
            ram = ram,
            batteryLevel = batteryLevel.coerceIn(0, 100),
            batteryVoltageMv = voltageMv,
            batteryCurrentMa = currentMa,
            refreshRateHz = refresh,
            renderer = renderer
        )
    }

    fun getCpuLoad(): Int = readCpuLoadFromStat()

    fun getPowerWatt(): Float {
        val nodes = KernelRootRuntime.readNodesBlocking(listOf(
            "/sys/class/power_supply/battery/voltage_now",
            "/sys/class/power_supply/battery/current_now"
        ))
        val v = nodes["/sys/class/power_supply/battery/voltage_now"]?.trim()?.toLongOrNull() ?: 0L
        val c = nodes["/sys/class/power_supply/battery/current_now"]?.trim()?.toLongOrNull() ?: 0L
        if (v > 0L && c != 0L) return (abs(v.toDouble() * c.toDouble()) / 1_000_000_000_000.0).toFloat()
        val api = KernelRuntimeContext.context?.let { runCatching { BatteryApi.read(it) }.getOrNull() } ?: return 0f
        return abs(api.voltageV * api.currentMa / 1000f)
    }

    fun getBatteryTemp(context: Context): Float {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
    }

    fun getRamInfo(context: Context): RamInfo {
        return runCatching {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(info)
            val total = (info.totalMem / 1_048_576L).toInt()
            val used = ((info.totalMem - info.availMem) / 1_048_576L).toInt()
            RamInfo(used, total, if (total > 0) ((used.toDouble() / total) * 100).toInt() else 0)
        }.getOrDefault(RamInfo(0, 0, 0))
    }

    fun getDebugInfo(context: Context): String = String.format(Locale.US, "nodes=%s", nodeDiscoveryDone)
}
