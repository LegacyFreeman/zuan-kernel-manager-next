/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.core.kernel

import android.content.Context
import android.content.IntentFilter
import android.os.BatteryManager
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * One shared, immutable hardware snapshot for Dashboard/SoC/Battery/Thermal and
 * automation. Topology comes from Capability Engine, so the collector does not
 * rescan directories on every 2-second sample.
 */
object KernelStateEngine {
    data class CpuPolicyState(
        val policy: String,
        val currentKHz: Long = 0L,
        val minKHz: Long = 0L,
        val maxKHz: Long = 0L,
        val governor: String = "unknown"
    )

    data class GpuState(
        val path: String,
        val currentKHz: Long = 0L,
        val governor: String = "",
        val minKHz: Long = 0L,
        val maxKHz: Long = 0L
    )

    data class State(
        val timestamp: Long = 0L,
        val sequence: Long = 0L,
        val collectionLatencyMs: Long = 0L,
        val cpuLoadPercent: Int = 0,
        val cpuPolicies: List<CpuPolicyState> = emptyList(),
        val batteryPercent: Int = 0,
        val batteryStatus: String = "unknown",
        val batteryVoltageUv: Long = 0L,
        val batteryCurrentUa: Long = 0L,
        val batteryPowerW: Float = 0f,
        val batteryTempC: Float = 0f,
        val ramUsedMb: Int = 0,
        val ramTotalMb: Int = 0,
        val ramAvailableMb: Int = 0,
        val zramDiskMb: Int = 0,
        val zramUsedMb: Int = 0,
        val gpuClock: String = "",
        val gpu: List<GpuState> = emptyList(),
        val thermal: Map<String, Float> = emptyMap(),
        val maxThermalC: Float = 0f,
        val thermalHotZones: Int = 0,
        val pressureMemory: String = "",
        val pressureCpu: String = "",
        val ioSchedulers: Map<String, String> = emptyMap(),
        val daemonConnected: Boolean = false,
        val transport: String = "none",
        val runtimeFailures: Long = 0L,
        val healthy: Boolean = false,
        val error: String? = null
    ) {
        val ageMs: Long
            get() = if (timestamp == 0L) Long.MAX_VALUE else (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
        val stale: Boolean get() = timestamp == 0L || ageMs > 5_000L
        val batteryCharging: Boolean get() = batteryStatus.equals("charging", true) || batteryStatus.equals("full", true)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val state = MutableStateFlow(State())
    val flow: StateFlow<State> = state.asStateFlow()
    private val mutex = Mutex()
    private var monitorJob: Job? = null
    private var lastCpuTotal = 0L
    private var lastCpuIdle = 0L
    private var sequence = 0L
    private val history = mutableListOf<State>()

    suspend fun refresh(context: Context): State = mutex.withLock {
        withContext(Dispatchers.IO) {
            val started = System.nanoTime()
            val app = context.applicationContext
            KernelRootRuntime.initialize(app)
            var snapshot = KernelCapabilityEngine.current()
            if (snapshot.generatedAt == 0L || System.currentTimeMillis() - snapshot.generatedAt > 30_000L) {
                snapshot = runCatching { KernelCapabilityEngine.scan(app) }.getOrElse { KernelCapabilityEngine.current() }
            }
            val topology = snapshot.topology

            val paths = LinkedHashSet<String>()
            paths += "/proc/stat"
            paths += "/proc/meminfo"
            paths += "/proc/pressure/memory"
            paths += "/proc/pressure/cpu"
            paths += "/sys/class/power_supply/battery/capacity"
            paths += "/sys/class/power_supply/battery/status"
            paths += "/sys/class/power_supply/battery/voltage_now"
            paths += "/sys/class/power_supply/battery/current_now"
            paths += "/sys/class/power_supply/battery/temp"
            paths += "/sys/block/zram0/disksize"
            paths += "/sys/block/zram0/mem_used_total"
            paths += "/sys/class/kgsl/kgsl-3d0/gpuclk"
            topology.cpuPolicies.forEach { policy ->
                val base = "/sys/devices/system/cpu/cpufreq/$policy"
                paths += "$base/scaling_cur_freq"
                paths += "$base/scaling_min_freq"
                paths += "$base/scaling_max_freq"
                paths += "$base/scaling_governor"
            }
            topology.gpuDevfreq.forEach { gpu ->
                val base = "/sys/class/devfreq/$gpu"
                paths += "$base/cur_freq"
                paths += "$base/min_freq"
                paths += "$base/max_freq"
                paths += "$base/governor"
            }
            topology.thermalZones.forEach {
                paths += "/sys/class/thermal/$it/type"
                paths += "/sys/class/thermal/$it/temp"
            }
            topology.blockDevices.forEach {
                paths += "/sys/block/$it/queue/scheduler"
            }

            val values = KernelRootRuntime.readNodes(paths.toList())
            val cpu = parseCpu(values["/proc/stat"].orEmpty())
            val cpuLoad = computeCpuLoad(cpu.first, cpu.second)
            val mem = parseMeminfo(values["/proc/meminfo"].orEmpty())
            val battery = parseBattery(app, values)
            val thermal = parseThermal(topology.thermalZones, values)
            val gpu = topology.gpuDevfreq.map { dir ->
                val base = "/sys/class/devfreq/$dir"
                GpuState(
                    path = base,
                    currentKHz = toKHz(values["$base/cur_freq"]),
                    governor = values["$base/governor"].orEmpty(),
                    minKHz = toKHz(values["$base/min_freq"]),
                    maxKHz = toKHz(values["$base/max_freq"])
                )
            }
            val io = topology.blockDevices.mapNotNull { device ->
                values["/sys/block/$device/queue/scheduler"]?.trim()?.takeIf { it.isNotEmpty() }?.let { device to it }
            }.toMap()
            val runtime = KernelRootRuntime.snapshotStats()
            val maxThermal = thermal.values.maxOrNull() ?: battery.tempC
            val now = System.currentTimeMillis()
            sequence += 1L
            State(
                timestamp = now,
                sequence = sequence,
                collectionLatencyMs = (System.nanoTime() - started) / 1_000_000L,
                cpuLoadPercent = cpuLoad,
                cpuPolicies = topology.cpuPolicies.map { policy ->
                    val base = "/sys/devices/system/cpu/cpufreq/$policy"
                    CpuPolicyState(
                        policy = policy,
                        currentKHz = values["$base/scaling_cur_freq"]?.toLongOrNull() ?: 0L,
                        minKHz = values["$base/scaling_min_freq"]?.toLongOrNull() ?: 0L,
                        maxKHz = values["$base/scaling_max_freq"]?.toLongOrNull() ?: 0L,
                        governor = values["$base/scaling_governor"].orEmpty().ifBlank { "unknown" }
                    )
                },
                batteryPercent = battery.percent,
                batteryStatus = battery.status,
                batteryVoltageUv = battery.voltageUv,
                batteryCurrentUa = battery.currentUa,
                batteryPowerW = battery.powerW,
                batteryTempC = battery.tempC,
                ramUsedMb = mem.usedMb,
                ramTotalMb = mem.totalMb,
                ramAvailableMb = mem.availableMb,
                zramDiskMb = (values["/sys/block/zram0/disksize"]?.toLongOrNull()?.div(1024L * 1024L) ?: 0L).toInt(),
                zramUsedMb = (values["/sys/block/zram0/mem_used_total"]?.toLongOrNull()?.div(1024L * 1024L) ?: 0L).toInt(),
                gpuClock = values["/sys/class/kgsl/kgsl-3d0/gpuclk"].orEmpty(),
                gpu = gpu,
                thermal = thermal,
                maxThermalC = maxThermal,
                thermalHotZones = thermal.values.count { it >= 50f },
                pressureMemory = values["/proc/pressure/memory"].orEmpty(),
                pressureCpu = values["/proc/pressure/cpu"].orEmpty(),
                ioSchedulers = io,
                daemonConnected = runtime.daemonConnected,
                transport = runtime.lastBatchTransport,
                runtimeFailures = runtime.failures,
                healthy = runtime.daemonConnected && runtime.lastError == null && (cpu.first > 0L || mem.totalMb > 0),
                error = if (runtime.lastError != null) runtime.lastError else null
            ).also {
                state.value = it
                synchronized(history) {
                    history.add(it)
                    while (history.size > 60) history.removeAt(0)
                }
            }
        }
    }

    fun start(context: Context, intervalMs: Long = 2000L) {
        if (monitorJob?.isActive == true) return
        val app = context.applicationContext
        monitorJob = scope.launch {
            KernelRootRuntime.initialize(app)
            while (isActive) {
                runCatching { refresh(app) }.onFailure { throwable ->
                    val old = state.value
                    state.value = old.copy(error = throwable.message ?: throwable.javaClass.simpleName, healthy = false)
                }
                delay(intervalMs.coerceAtLeast(1000L))
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
    }

    fun isRunning(): Boolean = monitorJob?.isActive == true

    fun history(): List<State> = synchronized(history) { history.toList() }

    private data class CpuSample(val total: Long, val idle: Long)
    private fun parseCpu(raw: String): Pair<Long, Long> {
        val line = raw.lineSequence().firstOrNull { it.startsWith("cpu ") }.orEmpty()
        val v = line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        return (v.sum()) to ((v.getOrNull(3) ?: 0L) + (v.getOrNull(4) ?: 0L))
    }

    private fun computeCpuLoad(total: Long, idle: Long): Int {
        if (lastCpuTotal == 0L || total <= lastCpuTotal) {
            lastCpuTotal = total
            lastCpuIdle = idle
            return 0
        }
        val totalDelta = total - lastCpuTotal
        val idleDelta = idle - lastCpuIdle
        lastCpuTotal = total
        lastCpuIdle = idle
        if (totalDelta <= 0L) return 0
        return (((totalDelta - idleDelta).toDouble() / totalDelta) * 100.0).toInt().coerceIn(0, 100)
    }

    private data class Memory(val usedMb: Int, val totalMb: Int, val availableMb: Int)
    private fun parseMeminfo(raw: String): Memory {
        fun kb(name: String): Long = raw.lineSequence().firstOrNull { it.startsWith(name) }
            ?.substringAfter(':')?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.toLongOrNull() ?: 0L
        val total = kb("MemTotal")
        val available = kb("MemAvailable")
        return Memory(((total - available).coerceAtLeast(0L) / 1024L).toInt(), (total / 1024L).toInt(), (available / 1024L).toInt())
    }

    private data class BatteryData(val percent: Int, val status: String, val voltageUv: Long, val currentUa: Long, val powerW: Float, val tempC: Float)
    private fun parseBattery(context: Context, values: Map<String, String>): BatteryData {
        val percent = values["/sys/class/power_supply/battery/capacity"]?.toIntOrNull()
            ?: runCatching { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0 }.getOrDefault(0)
        val status = values["/sys/class/power_supply/battery/status"].orEmpty().ifBlank { "unknown" }
        val voltage = values["/sys/class/power_supply/battery/voltage_now"]?.toLongOrNull() ?: 0L
        val current = values["/sys/class/power_supply/battery/current_now"]?.toLongOrNull() ?: 0L
        val power = if (voltage > 0L && current != 0L) (abs(voltage.toDouble() * current.toDouble()) / 1_000_000_000_000.0).toFloat() else 0f
        val rawTemp = values["/sys/class/power_supply/battery/temp"]?.toFloatOrNull() ?: 0f
        val temp = when {
            rawTemp >= 10000f -> rawTemp / 1000f
            rawTemp >= 100f -> rawTemp / 10f
            else -> rawTemp
        }
        return BatteryData(percent.coerceIn(0, 100), status, voltage, current, power, temp)
    }

    private fun parseThermal(zones: List<String>, values: Map<String, String>): Map<String, Float> = zones.mapNotNull { zone ->
        val raw = values["/sys/class/thermal/$zone/temp"]?.toFloatOrNull() ?: return@mapNotNull null
        val temp = when {
            raw >= 10000f -> raw / 1000f
            raw >= 1000f -> raw / 1000f
            raw >= 100f -> raw / 10f
            else -> raw
        }
        val type = values["/sys/class/thermal/$zone/type"].orEmpty().ifBlank { zone }
        type to temp
    }.toMap()

    private fun toKHz(raw: String?): Long {
        val value = raw?.trim()?.toLongOrNull() ?: return 0L
        return if (value > 10_000_000L) value / 1000L else value / 1000L
    }
}
