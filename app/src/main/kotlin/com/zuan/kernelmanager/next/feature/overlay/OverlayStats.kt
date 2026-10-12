/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.overlay

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime
import com.zuan.kernelmanager.next.library.device.battery.BatteryApi
import com.zuan.kernelmanager.next.library.device.monitor.FpsReader
import com.zuan.kernelmanager.next.library.device.monitor.GpuReader
import com.zuan.kernelmanager.next.library.device.monitor.MonitorReader
import kotlin.math.abs

/**
 * =====================================================================================
 * DATA LAYER UNTUK OVERLAY MONITOR (System / Mini / Thread / Temperature)
 *
 * Semua sampler di sini BLOCKING dan harus dipanggil dari thread IO (lihat LaunchedEffect
 * di tiap overlay). Setiap overlay memegang instance sampler-nya sendiri, jadi counter delta
 * (/proc/stat, tick thread) tidak saling mengganggu antar overlay.
 * =====================================================================================
 */

data class CoreStat(val id: Int, val loadPct: Int, val freqMHz: Int)

data class ClusterStat(val cpus: List<Int>, val freqMHz: Int, val cores: List<CoreStat>) {
    /** Contoh: "#0~3", "#7~7" (sama dengan format monitor referensi). */
    val label: String get() = "#${cpus.first()}~${cpus.last()}"
}

data class SystemStats(
    val cpuLoad: Int = 0,
    /** Frekuensi cluster tertinggi (MHz) — angka di bawah ring CPU. */
    val cpuFreqMHz: Int = 0,
    /** -1 = tidak diketahui. */
    val gpuLoad: Int = -1,
    val gpuFreqMHz: Int = 0,
    /** true = [gpuLoad] is estimated from the GPU clock ratio (no load node readable). */
    val gpuLoadEstimated: Boolean = false,
    val gpuName: String = "GPU",
    val batteryPct: Int = 0,
    val charging: Boolean = false,
    val batteryTempC: Float = 0f,
    val ramPct: Int = 0,
    val swapPct: Int = 0,
    /** 0 = tidak diketahui. */
    val ddrMHz: Int = 0,
    val cpuTempC: Float? = null,
    val clusters: List<ClusterStat> = emptyList(),
    val fps: Float = 0f,
    val watt: Float = 0f
) {
    val allCores: List<CoreStat> get() = clusters.flatMap { it.cores }.sortedBy { it.id }
}

/** Satu pembacaan sensor suhu; null = sensor tidak ada di perangkat ini. */
data class TempReadings(
    val battery: Float? = null,
    val cpu: Float? = null,
    val gpu: Float? = null,
    val ddr: Float? = null,
    val camera: Float? = null
)

private const val CPU_ROOT = "/sys/devices/system/cpu"
private const val BATTERY_ROOT = "/sys/class/power_supply/battery"

private fun batteryIntent(context: Context): Intent? =
    context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

/** devfreq / sysfs frekuensi bisa Hz, kHz, atau MHz — samakan ke MHz. */
internal fun normalizeToMHz(raw: String?): Int {
    val v = raw?.trim()?.split(Regex("\\s+"))?.firstNotNullOfOrNull { it.toLongOrNull() } ?: return 0
    return when {
        v >= 100_000_000L -> (v / 1_000_000L).toInt() // Hz
        v >= 100_000L -> (v / 1_000L).toInt()         // kHz
        else -> v.toInt()                              // MHz
    }
}

// ---------------------------------------------------------------------------------------
// SUHU
// ---------------------------------------------------------------------------------------

class ThermalReader(private val context: Context) {
    private enum class Kind { CPU, GPU, DDR, CAM, BAT }

    private var zonePaths: Map<String, Kind>? = null

    private fun classify(type: String): Kind? {
        val t = type.lowercase()
        return when {
            t.contains("gpu") -> Kind.GPU
            t.contains("ddr") || t.contains("dram") -> Kind.DDR
            t.contains("cam") -> Kind.CAM
            t.contains("cpu") && !t.contains("isolate") -> Kind.CPU
            t == "battery" || t.startsWith("batt") -> Kind.BAT
            else -> null
        }
    }

    private fun discover() {
        val root = "/sys/class/thermal"
        val zones = KernelRootRuntime.listDirectoriesBlocking(root).filter { it.startsWith("thermal_zone") }
        val found = LinkedHashMap<String, Kind>()
        // KernelRootRuntime membatasi 96 node per batch.
        zones.chunked(90).forEach { chunk ->
            val types = KernelRootRuntime.readNodesBlocking(chunk.map { "$root/$it/type" })
            chunk.forEach { zone ->
                val kind = types["$root/$zone/type"]?.let(::classify) ?: return@forEach
                if (found.size < 90) found["$root/$zone/temp"] = kind
            }
        }
        zonePaths = found
    }

    private fun normalize(raw: String?): Float? {
        val v = raw?.trim()?.toFloatOrNull() ?: return null
        val c = when {
            abs(v) >= 1000f -> v / 1000f
            abs(v) >= 200f -> v / 10f
            else -> v
        }
        return c.takeIf { it in -20f..150f }
    }

    fun read(): TempReadings {
        if (zonePaths == null) discover()
        val zones = zonePaths.orEmpty()
        val nodes = if (zones.isEmpty()) emptyMap() else KernelRootRuntime.readNodesBlocking(zones.keys.toList())

        fun peak(kind: Kind): Float? = zones.entries
            .filter { it.value == kind }
            .mapNotNull { normalize(nodes[it.key]) }
            .maxOrNull()

        // Suhu baterai paling akurat dari BatteryManager (resolusi 0.1°C).
        val battery = (batteryIntent(context)?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }?.div(10f))
            ?: peak(Kind.BAT)

        return TempReadings(
            battery = battery,
            cpu = peak(Kind.CPU),
            gpu = peak(Kind.GPU),
            ddr = peak(Kind.DDR),
            camera = peak(Kind.CAM)
        )
    }
}

// ---------------------------------------------------------------------------------------
// SYSTEM (CPU per-core, GPU, RAM, DDR, baterai, FPS, daya)
// ---------------------------------------------------------------------------------------

class SystemStatsSampler(context: Context, private val withFps: Boolean = true) {
    private val app = context.applicationContext
    private val thermal = ThermalReader(app)

    private var ready = false
    private var clusters: List<List<Int>> = emptyList()
    private var ddrFreqPath: String? = null

    /** key "cpu" / "cpuN" -> [total, idle] dari sampel sebelumnya. */
    private val prevStat = HashMap<String, LongArray>()

    private fun discover() {
        KernelRootRuntime.initialize(app)

        // --- cluster dari cpufreq policy ---
        val policyRoot = "$CPU_ROOT/cpufreq"
        val policies = KernelRootRuntime.listDirectoriesBlocking(policyRoot).filter { it.startsWith("policy") }
        val related = KernelRootRuntime.readNodesBlocking(policies.map { "$policyRoot/$it/related_cpus" })
        clusters = policies.mapNotNull { policy ->
            related["$policyRoot/$policy/related_cpus"]
                ?.trim()?.split(Regex("\\s+"))?.mapNotNull { it.toIntOrNull() }?.sorted()
                ?.takeIf { it.isNotEmpty() }
        }.sortedBy { it.first() }
        if (clusters.isEmpty()) {
            clusters = listOf((0 until Runtime.getRuntime().availableProcessors()).toList())
        }

        // GPU (Adreno / Mali / MediaTek / Exynos) is handled by GpuReader: it works with the shell uid
        // (Shizuku), validates that a node is really readable and re-discovers after privilege changes.
        val devfreqRoot = "/sys/class/devfreq"
        val devfreq = KernelRootRuntime.listDirectoriesBlocking(devfreqRoot)
        // Qualcomm kernel baru (bus_dcvs, mis. SM8450) menaruh frekuensi DDR di sini (kHz), bukan di devfreq.
        val busDcvsDdr = "/sys/devices/system/cpu/bus_dcvs/DDR/cur_freq"
        if (KernelRootRuntime.nodeExistsBatchBlocking(listOf(busDcvsDdr))[busDcvsDdr] == true) {
            ddrFreqPath = busDcvsDdr
        }
        if (ddrFreqPath == null) ddrFreqPath = (devfreq.firstOrNull { it.lowercase().contains("ddr") }
            ?: devfreq.firstOrNull { n ->
                val l = n.lowercase()
                l.contains("dram") || l.contains("dmc") || l.contains("dvfsrc")
            })?.let { "$devfreqRoot/$it/cur_freq" }

        ready = true
    }

    private fun parseLoad(statText: String): Map<String, Int> {
        val out = HashMap<String, Int>()
        statText.lineSequence().filter { it.startsWith("cpu") }.forEach { line ->
            val p = line.trim().split(Regex("\\s+"))
            val key = p[0]
            val v = p.drop(1).take(8).map { it.toLongOrNull() ?: 0L }
            if (v.size < 5) return@forEach
            val idle = v[3] + v.getOrElse(4) { 0L }
            val total = v.sum()
            val prev = prevStat[key]
            prevStat[key] = longArrayOf(total, idle)
            if (prev != null) {
                val dt = total - prev[0]
                val di = idle - prev[1]
                if (dt > 0) out[key] = (((dt - di) * 100L) / dt).toInt().coerceIn(0, 100)
            }
        }
        return out
    }

    fun sample(): SystemStats {
        if (!ready) discover()

        val cpus = clusters.flatten()
        val paths = ArrayList<String>().apply {
            add("/proc/stat")
            add("/proc/meminfo")
            cpus.forEach {
                add("$CPU_ROOT/cpu$it/cpufreq/scaling_cur_freq")
                add("$CPU_ROOT/cpu$it/cpufreq/cpuinfo_cur_freq")
            }
            ddrFreqPath?.let(::add)
            add("$BATTERY_ROOT/voltage_now")
            add("$BATTERY_ROOT/current_now")
        }
        val nodes = KernelRootRuntime.readNodesBlocking(paths)

        val load = parseLoad(nodes["/proc/stat"].orEmpty())
        val clusterStats = clusters.map { ids ->
            val coreStats = ids.map { id ->
                val scaling = normalizeToMHz(nodes["$CPU_ROOT/cpu$id/cpufreq/scaling_cur_freq"])
                val actual = normalizeToMHz(nodes["$CPU_ROOT/cpu$id/cpufreq/cpuinfo_cur_freq"])
                CoreStat(id, load["cpu$id"] ?: 0, if (actual > 0) actual else scaling)
            }
            ClusterStat(ids, normalizeToMHz(nodes["$CPU_ROOT/cpu${ids.first()}/cpufreq/scaling_cur_freq"]), coreStats)
        }

        // --- memori ---
        val mem = HashMap<String, Long>()
        nodes["/proc/meminfo"].orEmpty().lineSequence().forEach { line ->
            val key = line.substringBefore(':', "")
            if (key in setOf("MemTotal", "MemAvailable", "SwapTotal", "SwapFree")) {
                mem[key] = line.substringAfter(':').trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull() ?: 0L
            }
        }
        val memTotal = mem["MemTotal"] ?: 0L
        val ramPct = if (memTotal > 0) (((memTotal - (mem["MemAvailable"] ?: 0L)) * 100L) / memTotal).toInt() else 0
        val swapTotal = mem["SwapTotal"] ?: 0L
        val swapPct = if (swapTotal > 0) (((swapTotal - (mem["SwapFree"] ?: 0L)) * 100L) / swapTotal).toInt() else 0

        // --- baterai & daya ---
        val intent = batteryIntent(app)
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = (intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val scale = (intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
        val volts = nodes["$BATTERY_ROOT/voltage_now"]?.trim()?.toLongOrNull()?.let { abs(it) } ?: 0L
        val amps = nodes["$BATTERY_ROOT/current_now"]?.trim()?.toLongOrNull()?.let { abs(it) } ?: 0L
        var v = if (volts > 100_000L) volts / 1_000_000f else volts / 1_000f // µV atau mV -> V
        var a = if (amps > 20_000L) amps / 1_000_000f else amps / 1_000f     // µA atau mA -> A
        // Node sysfs tidak terbaca (SELinux / mode Shizuku): pakai API baterai publik.
        if (v <= 0f || a <= 0f) {
            runCatching { BatteryApi.read(app) }.getOrNull()?.let { api ->
                if (v <= 0f) v = api.voltageV
                if (a <= 0f) a = abs(api.currentMa) / 1000f
            }
        }

        val temps = thermal.read()
        val gpu = runCatching { GpuReader.sample() }.getOrDefault(GpuReader.GpuSample())
        val fps = if (withFps) runCatching { FpsReader.getRealFps() }.getOrDefault(0f) else 0f

        return SystemStats(
            cpuLoad = load["cpu"] ?: 0,
            cpuFreqMHz = clusterStats.maxOfOrNull { it.freqMHz } ?: 0,
            gpuLoad = gpu.loadPct,
            gpuFreqMHz = gpu.freqMHz,
            gpuLoadEstimated = gpu.loadEstimated,
            gpuName = gpu.gpuName,
            batteryPct = (level * 100 / scale).coerceIn(0, 100),
            charging = plugged || status == BatteryManager.BATTERY_STATUS_CHARGING,
            batteryTempC = temps.battery ?: 0f,
            ramPct = ramPct.coerceIn(0, 100),
            swapPct = swapPct.coerceIn(0, 100),
            ddrMHz = normalizeToMHz(ddrFreqPath?.let { nodes[it] }),
            cpuTempC = temps.cpu,
            clusters = clusterStats,
            fps = fps,
            // Tanda daya: + saat mengisi, - saat baterai dipakai (sama seperti monitor referensi).
            watt = if (plugged || status == BatteryManager.BATTERY_STATUS_CHARGING) v * a else -(v * a)
        )
    }
}

// ---------------------------------------------------------------------------------------
// THREAD (aplikasi foreground)
// ---------------------------------------------------------------------------------------

data class ThreadRow(val tid: Int, val comm: String, val cpuPct: Float, val cpus: String)

data class ThreadSnapshot(
    val packageName: String = "",
    val rows: List<ThreadRow> = emptyList(),
    /** Pesan status bila tidak ada data (mis. tidak ada app foreground). */
    val message: String? = null
)

class ThreadSampler(context: Context) {
    private val app = context.applicationContext
    private val pkgRegex = Regex("^[A-Za-z0-9_.]+$")

    private var tick = 0
    private var cachedPkg = ""
    private var cachedPid = ""
    private val lastTicks = HashMap<Int, Long>()
    private val cpusAllowed = HashMap<Int, String>()
    private var lastNanos = 0L

    private fun resolveTarget() {
        // Mencari app foreground butuh dumpsys (berat) -> cukup tiap beberapa tick.
        if (tick % 3 == 0 || cachedPkg.isEmpty()) {
            val pkg = MonitorReader.getForegroundPackage()
            if (pkg != cachedPkg) {
                cachedPkg = pkg
                cachedPid = ""
                lastTicks.clear()
                cpusAllowed.clear()
            }
        }
        if (cachedPkg.isNotEmpty() && pkgRegex.matches(cachedPkg)) {
            val pid = KernelRootRuntime.executeBlocking("pidof -s $cachedPkg 2>/dev/null")
                .outputString.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
            if (pid != cachedPid) {
                cachedPid = pid
                lastTicks.clear()
                cpusAllowed.clear()
            }
        }
    }

    fun sample(limit: Int = 10): ThreadSnapshot {
        KernelRootRuntime.initialize(app)
        resolveTarget()
        tick++

        if (cachedPkg.isEmpty()) return ThreadSnapshot(message = "No foreground app")
        if (cachedPid.isEmpty() || cachedPid.toIntOrNull() == null) {
            return ThreadSnapshot(packageName = cachedPkg, message = "Process not running")
        }
        val pid = cachedPid

        val statLines = KernelRootRuntime.executeBlocking("cat /proc/$pid/task/*/stat 2>/dev/null").out
        val nowNanos = System.nanoTime()
        val dtSec = if (lastNanos == 0L) 0.0 else (nowNanos - lastNanos) / 1e9
        lastNanos = nowNanos

        data class Parsed(val tid: Int, val comm: String, val ticks: Long)
        val parsed = statLines.mapNotNull { line ->
            val open = line.indexOf('(')
            val close = line.lastIndexOf(')')
            if (open <= 0 || close <= open) return@mapNotNull null
            val tid = line.substring(0, open).trim().toIntOrNull() ?: return@mapNotNull null
            val rest = line.substring(close + 1).trim().split(Regex("\\s+"))
            // rest[0]=state(field 3) ... utime=field 14 -> rest[11], stime=field 15 -> rest[12]
            val utime = rest.getOrNull(11)?.toLongOrNull() ?: return@mapNotNull null
            val stime = rest.getOrNull(12)?.toLongOrNull() ?: return@mapNotNull null
            Parsed(tid, line.substring(open + 1, close), utime + stime)
        }
        if (parsed.isEmpty()) return ThreadSnapshot(packageName = cachedPkg, message = "No thread data")

        // Affinity (Cpus_allowed_list) jarang berubah: ambil hanya bila ada thread baru.
        if (parsed.any { it.tid !in cpusAllowed }) {
            KernelRootRuntime.executeBlocking("grep -H Cpus_allowed_list /proc/$pid/task/*/status 2>/dev/null").out
                .forEach { line ->
                    val m = Regex("/task/(\\d+)/status:Cpus_allowed_list:\\s*(.+)$").find(line) ?: return@forEach
                    cpusAllowed[m.groupValues[1].toInt()] = m.groupValues[2].trim()
                }
        }

        val rows = parsed.map { p ->
            val prev = lastTicks[p.tid]
            val pct = if (prev != null && dtSec > 0.0) ((p.ticks - prev) / dtSec).toFloat().coerceIn(0f, 100f) else 0f
            ThreadRow(p.tid, p.comm, pct, cpusAllowed[p.tid] ?: "-")
        }
        lastTicks.clear()
        parsed.forEach { lastTicks[it.tid] = it.ticks }

        return ThreadSnapshot(
            packageName = cachedPkg,
            rows = rows.sortedWith(compareByDescending<ThreadRow> { it.cpuPct }.thenBy { it.tid }).take(limit)
        )
    }
}
