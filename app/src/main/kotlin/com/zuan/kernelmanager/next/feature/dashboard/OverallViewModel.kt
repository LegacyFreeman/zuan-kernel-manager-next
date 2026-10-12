package com.zuan.kernelmanager.next.feature.dashboard

import android.content.Context
import android.os.SystemClock
import android.os.Process
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zuan.kernelmanager.next.feature.dashboard.layout.DashboardWidgetType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs

data class GithubRelease(
    val tag_name: String,
    val body: String,
    val assets: List<GithubAsset>
)
data class GithubAsset(val browser_download_url: String)
data class DashboardState(
    val updateRelease: GithubRelease? = null,
    val deviceModel: String = "Loading...",
    val deviceCodename: String = "...",
    val socName: String = "Loading...",
    val kernelVersion: String = "Loading...",
    val memTotal: String = "0 GB",
    val memUsed: String = "0 GB",
    val memFree: String = "0 GB",
    val ramProgress: Float = 0f,
    val ramTextSimple: String = "0/0",
    val swapTotal: String = "0 GB",
    val swapUsed: String = "0 GB",
    val swapFree: String = "0 GB",
    val swapProgress: Float = 0f,
    val swapText: String = "0/0",
    val zramAlgorithm: String = "Unknown",
    val vmParameters: VMParameters = VMParameters(),
    val displayInfo: DisplayInfo = DisplayInfo(),
    val gpuFreq: String = "0 MHz",
    val gpuLoad: Int = 0,
    val gpuDetail: String = "Loading...",
    val batCurrent: String = "0mA",
    val batVolt: String = "0mV",
    val batTemp: String = "0°C",
    val batteryInfo: OverallBatteryInfo = OverallBatteryInfo(),
    val batteryHistory: List<Int> = List(20) { 0 },
    val processList: List<ProcessInfo> = emptyList(),
    val uptime: String = "0s",
    val awakeTime: String = "0s",
    val deepSleepTime: String = "0s",
    val cpuFreqs: List<String> = emptyList(),
    val cpuHistory: List<Float> = emptyList(),
    val currentCpuLoad: String = "0%",
    val perCoreLoads: List<Int> = emptyList(),
    val selectedCoreInfo: CoreDetailInfo? = null,
    val isLoadingCoreDetail: Boolean = false,
    val storageIntelligence: StorageIntelligence = StorageIntelligence(),
    val ufsHealth: UfsHealthInfo = UfsHealthInfo(),
    // --- Widget tambahan (Add Widget) ---
    val thermal: ThermalIntelligence = ThermalIntelligence(),
    val thermalHistory: List<Float> = emptyList(),
    val pressure: PressureInfo = PressureInfo(),
    val security: SecurityInfo = SecurityInfo()
)

private const val POLL_INTERVAL_MS = 1000L
private const val HISTORY_SIZE = 20
private const val CPU_HISTORY_SIZE = 48
private const val THERMAL_HISTORY_SIZE = 30

class OverallViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardState())
    val uiState: StateFlow<DashboardState> = _uiState.asStateFlow()
    private val visibleWidgets = MutableStateFlow(DashboardWidgetType.entries.toSet())
    private var appContext: WeakReference<Context> = WeakReference(null)
    private var started = false
    private var tickCount = 0L

    fun setVisibleWidgets(widgets: Set<DashboardWidgetType>) {
        visibleWidgets.value = widgets
    }

    fun start(currentVersion: String, context: Context) {
        appContext = WeakReference(context.applicationContext)
        if (started) return
        started = true
        
        viewModelScope.launch(Dispatchers.IO) {
            coroutineScope {
                val model = async { OverallUtils.getDeviceModel() }
                val codename = async { OverallUtils.getDeviceCodename() }
                val soc = async { OverallUtils.getSoCName() }
                val kernel = async { OverallUtils.getKernelVersion() }
                val gpuInfo = async { OverallUtils.getGpuDetail() }
                val vmParams = async { OverallUtils.getVMParameters() }
                val displayInfo = async { OverallUtils.getDisplayInfo(context.applicationContext) }

                _uiState.update {
                    it.copy(
                        deviceModel = model.await(),
                        deviceCodename = codename.await(),
                        socName = soc.await(),
                        kernelVersion = kernel.await(),
                        gpuDetail = gpuInfo.await(),
                        vmParameters = vmParams.await(),
                        displayInfo = displayInfo.await()
                    )
                }
            }
            checkUpdate(currentVersion)
        }
    }

    val monitoring: StateFlow<Unit> = flow {
        // Little-Core Pinning (ZKM Perf - Adopsi Scene / ACSIDE):
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) }

        while (currentCoroutineContext().isActive) {
            // Screen-Off Deep Sleep (Adopsi VTools ScreenOffCleanup):
            if (!com.zuan.kernelmanager.next.core.util.ZkmSamplerBus.isScreenOn.value) {
                com.zuan.kernelmanager.next.core.util.ZkmSamplerBus.isScreenOn.first { it }
            }

            sampleOnce()
            emit(Unit)

            // Interval adaptif yang sinkron (2s Pro / 5s Lite) alih-alih 1s terus menerus
            val delayMs = if (com.zuan.kernelmanager.next.ui.components.ZkmLite.active) 5000L else 2000L
            delay(delayMs)
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 3000),
            initialValue = Unit
        )

    private suspend fun sampleOnce() = coroutineScope {
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) }
        val context = appContext.get() ?: return@coroutineScope
        val visible = visibleWidgets.value
        tickCount++

        val needCpu = DashboardWidgetType.CPU_MONITOR in visible
        val needGpu = DashboardWidgetType.GPU in visible
        val needMem = DashboardWidgetType.MEMORY in visible
        val needBat = DashboardWidgetType.BATTERY_DASHBOARD in visible ||
            DashboardWidgetType.POWER_DRAW in visible
        val needStorage = DashboardWidgetType.STORAGE_INTELLIGENCE in visible
        val needUfsHealth = DashboardWidgetType.UFS_HEALTH in visible
        val needThermal = DashboardWidgetType.THERMAL_MONITOR in visible
        val needPressure = DashboardWidgetType.SYSTEM_PRESSURE in visible
        val needSecurity = DashboardWidgetType.ROOT_SECURITY in visible

        val needCpuTelemetry = needCpu
        val needGpuTelemetry = needGpu
        val needBatteryTelemetry = needBat
        val needStorageTelemetry = needStorage
        val needUfsHealthTelemetry = needUfsHealth

        // 1. MEMORY parsing
        var memTotalKb = 0L; var memAvailKb = 0L; var memUsedKb = 0L; var swapTotalKb = 0L; var swapUsedKb = 0L; var swapFreeKb = 0L
        if (needMem) {
            val rawMeminfo = OverallUtils.readLines("/proc/meminfo").joinToString("\n")
            val parsedMem = OverallUtils.parseMemoryInfo(rawMeminfo).split(";")
            memTotalKb = parsedMem.getOrNull(0)?.toLongOrNull() ?: 0L
            val memFreeKb = parsedMem.getOrNull(1)?.toLongOrNull() ?: 0L
            memAvailKb = parsedMem.getOrNull(2)?.toLongOrNull() ?: 0L
            swapTotalKb = parsedMem.getOrNull(3)?.toLongOrNull() ?: 0L
            swapFreeKb = parsedMem.getOrNull(4)?.toLongOrNull() ?: 0L
            val memAvailFinal = if (memAvailKb > 0) memAvailKb else memFreeKb
            memUsedKb = if (memTotalKb > 0) memTotalKb - memAvailFinal else 0L
            swapUsedKb = if (swapTotalKb > 0) swapTotalKb - swapFreeKb else 0L
        }

        // 2. KOTLIN ASYNC FETCHERS
        val batDeferred = if (needBatteryTelemetry) async { OverallUtils.getDetailedBatteryInfo() } else null
        val gpuDeferred = if (needGpuTelemetry) async { OverallUtils.getGpuLoadAndFreq() } else null
        val cpuLoadDeferred = if (needCpuTelemetry) async { OverallUtils.getCpuLoads() } else null
        val cpusDeferred = if (needCpu) async { OverallUtils.getCpuFrequencies() } else null
        val procDeferred = if (needCpu && tickCount % 4 == 1L) async { OverallUtils.getTopProcesses(context) } else null
        val storageDeferred = if (needStorageTelemetry) async { OverallUtils.getStorageIntelligence() } else null
        val ufsHealthDeferred = if (needUfsHealthTelemetry) async { OverallUtils.getUfsHealth() } else null
        val thermalDeferred = if (needThermal && (tickCount % 2 == 1L || _uiState.value.thermal.zoneCount == 0)) async { OverallUtils.getThermalIntelligence() } else null
        val pressureDeferred = if (needPressure) async { OverallUtils.getPressureInfo() } else null
        val securityDeferred = if (needSecurity && (tickCount % 5 == 1L || _uiState.value.security.selinux == "Unknown")) async { OverallUtils.getSecurityInfo() } else null

        val batNew = batDeferred?.await()
        val gpu = gpuDeferred?.await()
        val cpuLoads = cpuLoadDeferred?.await()
        val cpus = cpusDeferred?.await()
        val processes = procDeferred?.await()
        val storage = storageDeferred?.await()
        val ufsHealth = ufsHealthDeferred?.await()
        val thermal = thermalDeferred?.await()
        val pressure = pressureDeferred?.await()
        val security = securityDeferred?.await()

        val upMillis = SystemClock.elapsedRealtime()
        val deepSleepMillis = upMillis - SystemClock.uptimeMillis()

        _uiState.update { s ->
            // First commit the raw telemetry into one coherent snapshot.
            // Derived intelligence below therefore always sees the same tick.
            var next = s.copy(
                uptime = formatDuration(upMillis),
                awakeTime = formatDuration(upMillis - deepSleepMillis),
                deepSleepTime = formatDuration(deepSleepMillis)
            )

            if (needMem && memTotalKb > 0) {
                next = next.copy(
                    memTotal = formatGb(memTotalKb),
                    memUsed = formatGb(memUsedKb),
                    memFree = formatGb(memAvailKb),
                    ramProgress = (memUsedKb.toFloat() / memTotalKb.toFloat()).coerceIn(0f, 1f),
                    ramTextSimple = "${memUsedKb / 1024 / 1024}GB / ${memTotalKb / 1024 / 1024}GB",
                    swapTotal = formatGb(swapTotalKb),
                    swapUsed = formatGb(swapUsedKb),
                    swapFree = formatGb(swapFreeKb),
                    swapProgress = if (swapTotalKb > 0) (swapUsedKb.toFloat() / swapTotalKb.toFloat()).coerceIn(0f, 1f) else 0f,
                    swapText = "${swapUsedKb / 1024 / 1024}GB / ${swapTotalKb / 1024 / 1024}GB"
                )
            }

            if (batNew != null) {
                next = next.copy(
                    batteryInfo = batNew,
                    batCurrent = "${batNew.currentNow}mA",
                    batVolt = "${batNew.level} (${batNew.voltage})",
                    batTemp = batNew.temp,
                    batteryHistory = s.batteryHistory.append(abs(batNew.currentNow), HISTORY_SIZE)
                )
            }
            if (cpuLoads != null) {
                next = next.copy(
                    cpuHistory = s.cpuHistory.append(cpuLoads.totalLoad, CPU_HISTORY_SIZE),
                    currentCpuLoad = "${(cpuLoads.totalLoad * 100).toInt()}%",
                    perCoreLoads = cpuLoads.perCoreLoads
                )
            }
            if (gpu != null) next = next.copy(gpuFreq = gpu.first, gpuLoad = gpu.second)
            if (cpus != null) next = next.copy(cpuFreqs = cpus)
            if (processes != null && processes != s.processList) next = next.copy(processList = processes)
            if (storage != null) next = next.copy(storageIntelligence = storage)
            if (ufsHealth != null) next = next.copy(ufsHealth = ufsHealth)
            if (thermal != null) {
                next = next.copy(
                    thermal = thermal,
                    thermalHistory = s.thermalHistory.append(thermal.maxTempC, THERMAL_HISTORY_SIZE)
                )
            }
            if (pressure != null) next = next.copy(pressure = pressure)
            if (security != null) next = next.copy(security = security)

            next
        }
    }

    fun loadCoreDetail(coreIndex: Int) {
        _uiState.update { it.copy(isLoadingCoreDetail = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val gov = OverallUtils.getCpuGovernor(coreIndex)
            val tunables = OverallUtils.getGovernorTunables(coreIndex, gov)
            _uiState.update { it.copy(selectedCoreInfo = CoreDetailInfo(coreIndex, gov, tunables), isLoadingCoreDetail = false) }
        }
    }
    fun dismissCoreDetail() { _uiState.update { it.copy(selectedCoreInfo = null) } }

    private fun formatGb(kb: Long): String = String.format(Locale.US, "%.1f GB", kb / 1024f / 1024f)
    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return String.format(Locale.US, "%02dh %02dm", h, m)
    }
    private fun <T> List<T>.append(value: T, maxSize: Int): List<T> = if (size >= maxSize) drop(size - maxSize + 1) + value else this + value

    private fun checkUpdate(@Suppress("UNUSED_PARAMETER") currentVer: String) {
        val ctx = appContext.get() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            // Same source as the update dialog: github.com/zuanvfx02/zuan-kernel-manager-next/releases,
            // honouring the "Check for manager/beta updates" toggles in Settings.
            val info = com.zuan.kernelmanager.next.feature.update.UpdateChecker.checkForUpdate(ctx) ?: return@launch
            val isApk = info.downloadUrl.substringBefore('?').endsWith(".apk", ignoreCase = true)
            if (!isApk) return@launch
            _uiState.update {
                it.copy(
                    updateRelease = GithubRelease(
                        tag_name = "v${info.version}",
                        body = info.changelog,
                        assets = listOf(GithubAsset(info.downloadUrl))
                    )
                )
            }
        }
    }
}
