package com.zuan.kernelmanager.next.data.fps

import android.content.Context
import android.util.Log
import com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime
import com.zuan.kernelmanager.next.data.database.FpsDataPoint
import com.zuan.kernelmanager.next.data.database.FpsDatabase
import com.zuan.kernelmanager.next.data.database.FpsSession
import com.zuan.kernelmanager.next.library.device.monitor.FpsReader
import com.zuan.kernelmanager.next.library.device.monitor.MonitorReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Collections
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/** High-detail in-memory FPS recorder. Persistence happens once per session, not once per sample. */
object FpsRecorder {
    private const val TAG = "FpsRecorder"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recordedPoints = Collections.synchronizedList(mutableListOf<FpsDataPoint>())

    @Volatile var isRecording = false; private set
    @Volatile var targetPackage: String = ""; private set
    @Volatile var isPaused = false; private set
    @Volatile var sampleIntervalMs: Long = 1000L; private set
    private var startTime = 0L

    fun startRecording(initialPkg: String, intervalMs: Long = 1000L) {
        if (isRecording) return
        targetPackage = initialPkg
        isRecording = true
        isPaused = false
        sampleIntervalMs = intervalMs.coerceIn(250L, 5000L)
        startTime = System.currentTimeMillis()
        recordedPoints.clear()
        Log.d(TAG, "REC START: $targetPackage / ${sampleIntervalMs}ms")
    }

    fun tick(context: Context, currentPkg: String, snapshot: MonitorReader.MonitorSnapshot, fps: Float) {
        if (!isRecording) return
        if ((targetPackage.isEmpty() || targetPackage == "Unknown App") && currentPkg.isNotEmpty()) {
            targetPackage = currentPkg
        }
        val validTarget = targetPackage.isNotEmpty() && targetPackage != "Unknown App"
        val differentApp = currentPkg.isNotEmpty() && currentPkg != targetPackage
        if (validTarget && differentApp) {
            isPaused = true
            return
        }
        isPaused = false

        val cleanFps = fps.coerceIn(0f, 240f)
        val frameTime = if (cleanFps > 0.1f) 1000f / cleanFps else 0f
        val refresh = snapshot.refreshRateHz.takeIf { it > 0f } ?: 60f
        val targetFrame = 1000f / refresh.coerceAtLeast(1f)
        val jank = frameTime > targetFrame * 1.5f && cleanFps > 0f

        recordedPoints.add(
            FpsDataPoint(
                sessionId = 0,
                timestamp = System.currentTimeMillis() - startTime,
                fps = cleanFps,
                frameTimeMs = frameTime,
                cpuLoad = snapshot.cpuLoad,
                cpuFreqMHz = snapshot.cpuFreqMHz,
                gpuLoad = snapshot.gpuLoad.coerceAtLeast(0), // -1 = unknown must not pollute avg/max stats
                gpuFreqMHz = snapshot.gpuFreqMHz,
                temp = snapshot.temp,
                watt = snapshot.watt,
                ramUsageMb = snapshot.ram.usedMb,
                ramTotalMb = snapshot.ram.totalMb,
                ramPercent = snapshot.ram.percent,
                batteryLevel = snapshot.batteryLevel,
                batteryVoltageMv = snapshot.batteryVoltageMv,
                batteryCurrentMa = snapshot.batteryCurrentMa,
                refreshRateHz = snapshot.refreshRateHz,
                jank = jank,
                renderer = snapshot.renderer,
                foregroundPackage = currentPkg
            )
        )
    }

    /** Compatibility overload for legacy callers. */
    fun tick(context: Context, currentPkg: String, fps: Float, cpu: Int, watt: Float, temp: Float, ramMb: Int) {
        tick(
            context,
            currentPkg,
            MonitorReader.MonitorSnapshot(
                cpuLoad = cpu,
                watt = watt,
                temp = temp,
                ram = MonitorReader.RamInfo(ramMb, 0, 0)
            ),
            fps
        )
    }

    fun stopRecording(context: Context) {
        if (!isRecording) return
        isRecording = false
        val endTime = System.currentTimeMillis()
        val points = synchronized(recordedPoints) { ArrayList(recordedPoints) }
        recordedPoints.clear()
        if (points.isEmpty()) return

        val packageName = targetPackage.ifBlank { "Unknown App" }
        val started = startTime
        val interval = sampleIntervalMs
        val mode = FpsReader.currentMode.name
        scope.launch {
            runCatching {
                val validFps = points.map { it.fps }.filter { it > 0f }
                val avg = validFps.averageOrZero()
                val sorted = validFps.sorted()
                val onePercentLow = percentileLower(sorted, 0.01)
                val zeroOnePercentLow = percentileLower(sorted, 0.001)
                val p95Frame = percentileNearest(points.map { it.frameTimeMs }.filter { it > 0f }.sorted(), 0.95)
                val variance = if (validFps.isEmpty()) 0f else sqrt(validFps.sumOf { (it - avg).pow(2).toDouble() } / validFps.size).toFloat()
                val smoothness = if (validFps.isEmpty()) 0f else validFps.count { it >= avg * 0.8f } * 100f / validFps.size
                val fps60 = points.count { it.fps >= 57f }
                val stutter = points.count { it.jank }
                val avgTemp = points.averageOf { it.temp }
                val avgWatt = points.averageOf { it.watt }
                val avgRam = points.averageOf { it.ramUsageMb }
                val avgRamPct = points.averageOf { it.ramPercent }
                val avgCpu = points.averageOf { it.cpuLoad }
                val avgGpu = points.averageOf { it.gpuLoad }
                val avgCpuFreq = points.averageOf { it.cpuFreqMHz }
                val avgGpuFreq = points.averageOf { it.gpuFreqMHz }
                val avgVoltage = points.averageOf { it.batteryVoltageMv }
                val avgCurrent = points.averageOf { it.batteryCurrentMa }
                val avgBattery = points.averageOf { it.batteryLevel }
                val renderer = points.asSequence().map { it.renderer }.filter { it.isNotBlank() && it != "FPS" }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "FPS"
                val refresh = points.map { it.refreshRateHz }.filter { it > 0f }.averageOrZero()
                val session = FpsSession(
                    packageName = packageName,
                    startTime = started,
                    endTime = endTime,
                    durationSeconds = ((endTime - started) / 1000L).coerceAtLeast(1L),
                    sampleIntervalMs = interval,
                    sampleCount = points.size,
                    validFpsSamples = validFps.size,
                    avgFps = avg,
                    minFps = validFps.minOrNull() ?: 0f,
                    maxFps = validFps.maxOrNull() ?: 0f,
                    onePercentLow = onePercentLow,
                    zeroOnePercentLow = zeroOnePercentLow,
                    frameTimeP95Ms = p95Frame,
                    jankPercent = if (points.isEmpty()) 0f else stutter * 100f / points.size,
                    stutterCount = stutter,
                    variance = variance,
                    smoothness = smoothness,
                    avgCpu = avgCpu,
                    maxCpu = points.maxOfOrNull { it.cpuLoad } ?: 0,
                    avgGpuLoad = avgGpu,
                    maxGpuLoad = points.maxOfOrNull { it.gpuLoad } ?: 0,
                    avgTemp = avgTemp,
                    maxTemp = points.maxOfOrNull { it.temp } ?: 0f,
                    avgWatt = avgWatt,
                    maxWatt = points.maxOfOrNull { it.watt } ?: 0f,
                    avgRam = avgRam.toInt(),
                    maxRam = points.maxOfOrNull { it.ramUsageMb } ?: 0,
                    avgRamPercent = avgRamPct,
                    maxRamPercent = points.maxOfOrNull { it.ramPercent } ?: 0,
                    avgCpuFreqMHz = avgCpuFreq,
                    maxCpuFreqMHz = points.maxOfOrNull { it.cpuFreqMHz } ?: 0f,
                    avgGpuFreqMHz = avgGpuFreq,
                    maxGpuFreqMHz = points.maxOfOrNull { it.gpuFreqMHz } ?: 0f,
                    startBattery = points.firstOrNull()?.batteryLevel ?: 0,
                    endBattery = points.lastOrNull()?.batteryLevel ?: 0,
                    avgBattery = avgBattery,
                    minBattery = points.minOfOrNull { it.batteryLevel } ?: 0,
                    avgVoltageMv = avgVoltage,
                    avgCurrentMa = avgCurrent,
                    refreshRateHz = refresh,
                    renderer = renderer,
                    fpsSource = FpsReader.getSourceLabel().ifBlank { mode }
                )
                val dao = FpsDatabase.getDatabase(context.applicationContext).fpsDao()
                val id = dao.insertSession(session)
                dao.insertDataPoints(points.map { it.copy(sessionId = id) })
                Log.d(TAG, "REC SAVED: $id samples=${points.size} 60fps=${fps60}")
            }.onFailure { Log.e(TAG, "Failed to save FPS session", it) }
        }
    }

    private fun percentileLower(values: List<Float>, fraction: Double): Float {
        if (values.isEmpty()) return 0f
        val count = (values.size * fraction).toInt().coerceAtLeast(1).coerceAtMost(values.size)
        return values.take(count).average().toFloat()
    }

    private fun percentileNearest(values: List<Float>, fraction: Double): Float {
        if (values.isEmpty()) return 0f
        val index = ((values.size - 1) * fraction).toInt().coerceIn(0, values.lastIndex)
        return values[index]
    }

    private fun List<Float>.averageOrZero(): Float = if (isEmpty()) 0f else average().toFloat()
    private fun List<FpsDataPoint>.averageOf(selector: (FpsDataPoint) -> Number): Float =
        if (isEmpty()) 0f else map { selector(it).toDouble() }.average().toFloat()
}
