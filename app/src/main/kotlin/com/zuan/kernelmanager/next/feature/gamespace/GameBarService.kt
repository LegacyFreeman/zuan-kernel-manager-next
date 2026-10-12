/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.feature.gamespace

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.TrafficStats
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.ScreenLockRotation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.SportsScore
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.feature.overlay.BaseOverlayService
import com.zuan.kernelmanager.next.library.device.monitor.FpsReader
import com.zuan.kernelmanager.next.library.device.monitor.GpuReader
import com.zuan.kernelmanager.next.library.device.monitor.MonitorReader
import com.zuan.kernelmanager.next.library.device.usage.UsageStatsHelper
import com.zuan.kernelmanager.next.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Panel samping Game Space.
 *
 * Tertutup: hanya pill tipis menempel di tepi layar (geser vertikal untuk memindah, tap / geser ke dalam untuk buka).
 * Terbuka : panel kaca gelap berisi statistik live (FPS + sparkline, CPU, GPU, suhu, RAM, baterai),
 *           grid tombol cepat, slider kecerahan, dan baris status aksi. Menutup sendiri saat idle.
 */
class GameBarService : BaseOverlayService() {

    companion object {
        @Volatile var isRunning = false
            private set

        fun start(c: Context) {
            runCatching { ContextCompat.startForegroundService(c, Intent(c, GameBarService::class.java)) }
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, GameBarService::class.java))
        }
    }

    override val notificationId = 131
    override fun onRunningChanged(running: Boolean) { isRunning = running }

    private var edgeState by mutableStateOf(PanelEdge.RIGHT)

    /** Naik tiap ada sentuhan di luar panel kompak; panel yang sedang terbuka menutup dengan animasi halus. */
    private var outsideTouches by mutableIntStateOf(0)

    override fun onOutsideTouch() { outsideTouches++ }

    private fun gravityFor(e: PanelEdge) = Gravity.TOP or if (e == PanelEdge.RIGHT) Gravity.END else Gravity.START

    override fun buildLayoutParams() = overlayParams().apply {
        flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        edgeState = GameSpacePrefs.edge(this@GameBarService)
        gravity = gravityFor(edgeState)
        x = 0
        y = GameSpacePrefs.edgeY(this@GameBarService)
    }

    private fun dragVertical(dy: Float) {
        params.y = (params.y + dy.roundToInt()).coerceAtLeast(0)
        applyLayout()
    }

    /** Hub Energy Cube memakai jendela layar penuh; saat tertutup kembali jadi pill kecil di tepi. */
    private fun setFullscreen(full: Boolean) {
        if (full) {
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            params.gravity = Gravity.TOP or Gravity.START
            params.x = 0
            params.y = 0
        } else {
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.gravity = gravityFor(edgeState)
            params.x = 0
            params.y = GameSpacePrefs.edgeY(this)
        }
        applyLayout()
    }

    private fun persistY() = GameSpacePrefs.setEdgeY(this, params.y)

    private fun flipEdge() {
        val next = if (edgeState == PanelEdge.RIGHT) PanelEdge.LEFT else PanelEdge.RIGHT
        edgeState = next
        GameSpacePrefs.setEdge(this, next)
        params.gravity = gravityFor(next)
        applyLayout()
    }

    // ------------------------------------------------------------------------------------------

    @Composable
    override fun OverlayContent() {
        val ctx = this
        val scope = rememberCoroutineScope()
        val accent = remember { Color(GameSpacePrefs.accent(ctx)) }
        val autoHide = remember { GameSpacePrefs.autoHideSeconds(ctx) }
        val pkg = GameDetectorService.currentGame
        val gameSettings = remember(pkg) { GameSpacePrefs.settingsFor(ctx, pkg.orEmpty()) }
        val gameLabel = remember(pkg) { pkg?.let { UsageStatsHelper.appLabel(ctx, it) } ?: ctx.getString(R.string.gs_preview) }
        val edge = edgeState

        var expanded by remember { mutableStateOf(false) }
        var interaction by remember { mutableLongStateOf(System.currentTimeMillis()) }
        fun touch() { interaction = System.currentTimeMillis() }

        // ---- status tombol -----------------------------------------------------------------
        var dnd by remember { mutableStateOf(false) }
        var heads by remember { mutableStateOf(false) }
        var bright by remember { mutableStateOf(false) }
        var perf by remember { mutableStateOf(gameSettings.performanceMode) }
        var cross by remember { mutableStateOf(CrosshairService.isRunning) }
        var rec by remember { mutableStateOf(GameSpaceEngine.recording) }
        var brightness by remember { mutableFloatStateOf(128f) }
        var status by remember { mutableStateOf("") }
        var wifi by remember { mutableStateOf(false) }
        var rotLock by remember { mutableStateOf(false) }
        var hz by remember { mutableIntStateOf(0) }
        var volume by remember { mutableFloatStateOf(0f) }
        var netKbps by remember { mutableFloatStateOf(0f) }
        var listMode by remember { mutableStateOf(true) }
        var cpuModeIdx by remember { mutableIntStateOf(GameSpacePrefs.cpuMode(ctx)) }
        var modeKey by remember { mutableIntStateOf(0) }
        var cpuPeak by remember { mutableFloatStateOf(0f) }
        var gpuPeak by remember { mutableFloatStateOf(0f) }
        var gpuShowLoad by remember { mutableStateOf(false) }
        val cpuMode = CpuMode.fromOrdinal(cpuModeIdx)

        val cubeStyle = remember { GameSpacePrefs.panelStyle(ctx) == 0 }
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val cube = cubeStyle && landscape
        // Pintasan hub yang boleh tampil (Control Panel > Functions > Panel shortcuts).
        val scCross = remember(pkg) { GameHubPrefs.effBool(ctx, pkg, "fn_sc_cross", true) }
        val scRam = remember(pkg) { GameHubPrefs.effBool(ctx, pkg, "fn_sc_ram", true) }
        val scCpu = remember(pkg) { GameHubPrefs.effBool(ctx, pkg, "fn_sc_cpu", true) }
        val scRec = remember(pkg) { GameHubPrefs.effBool(ctx, pkg, "fn_sc_rec", true) }
        // Jendela layar penuh dipertahankan sampai animasi keluar hub selesai (tidak berkedip / terpotong).
        var fullWin by remember { mutableStateOf(false) }
        val hubVisible = remember { MutableTransitionState(false) }
        LaunchedEffect(expanded, cube) {
            if (expanded && cube) {
                setFullscreen(true)
                fullWin = true
                hubVisible.targetState = true
            } else {
                hubVisible.targetState = false
                if (fullWin) delay(900)
                fullWin = false
                setFullscreen(false)
            }
        }
        val clockFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

        var snap by remember { mutableStateOf(MonitorReader.MonitorSnapshot()) }
        var fps by remember { mutableFloatStateOf(0f) }
        val history = remember { mutableStateListOf<Float>() }
        var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }

        // Baca status awal & sampling hanya saat panel terbuka (hemat baterai saat bermain).
        LaunchedEffect(expanded) {
            if (!expanded) return@LaunchedEffect
            cpuModeIdx = GameSpacePrefs.cpuMode(ctx) // bisa berubah dari sub-overlay Custom Mode
            withContext(Dispatchers.IO) {
                dnd = GameSpaceEngine.isDndOn(ctx)
                heads = GameSpaceEngine.isHeadsUpBlocked(ctx)
                bright = GameSpaceEngine.isBrightnessManual(ctx)
                brightness = GameSpaceEngine.getBrightness(ctx).toFloat()
                wifi = GameSpaceEngine.isWifiOn(ctx)
                rotLock = GameSpaceEngine.isRotationLocked(ctx)
                hz = GameSpaceEngine.refreshSetting(ctx)
                volume = GameSpaceEngine.getVolume(ctx)
                FpsReader.init(ctx)
                var lastBytes = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()
                var lastAt = SystemClock.elapsedRealtime()
                while (isActive) {
                    if (cpuPeak <= 0f) cpuPeak = CpuModeEngine.maxMHz()
                    if (gpuPeak <= 0f) gpuPeak = GpuReader.maxFreqMHz().toFloat()
                    runCatching { MonitorReader.getEnhancedSnapshot(ctx) }.getOrNull()?.let {
                        snap = it
                        // Jika maksimum tidak terbaca dari sysfs, pakai puncak yang pernah teramati.
                        if (it.cpuFreqMHz > cpuPeak) cpuPeak = it.cpuFreqMHz
                        if (it.gpuFreqMHz > gpuPeak) gpuPeak = it.gpuFreqMHz
                    }
                    fps = runCatching { FpsReader.getRealFps() }.getOrDefault(0f)
                    history.add(fps)
                    while (history.size > 40) history.removeAt(0)
                    nowTick = System.currentTimeMillis()
                    val rx = TrafficStats.getTotalRxBytes()
                    val tx = TrafficStats.getTotalTxBytes()
                    val at = SystemClock.elapsedRealtime()
                    if (rx >= 0 && tx >= 0) {
                        val bytes = rx + tx
                        val dt = (at - lastAt).coerceAtLeast(1L) / 1000f
                        netKbps = ((bytes - lastBytes).coerceAtLeast(0L) / 1024f) / dt
                        lastBytes = bytes
                    }
                    lastAt = at
                    delay(700)
                }
            }
        }

        // Sentuhan di luar panel kompak -> tutup (animasi keluar bawaan AnimatedVisibility).
        val outside = outsideTouches
        LaunchedEffect(outside) { if (outside > 0 && expanded && !cube) expanded = false }

        // Tutup sendiri setelah idle - hanya bila pengguna memilih durasi (default: Never).
        LaunchedEffect(expanded, interaction) {
            if (!expanded || autoHide <= 0) return@LaunchedEffect
            delay(autoHide * 1000L)
            if (System.currentTimeMillis() - interaction >= autoHide * 1000L) expanded = false
        }

        fun say(msg: String) {
            status = msg
            val mine = msg
            scope.launch { delay(3500); if (status == mine) status = "" }
        }

        fun act(block: () -> String?) {
            touch()
            scope.launch(Dispatchers.IO) {
                val r = block()
                if (r != null) withContext(Dispatchers.Main) { say(r) }
            }
        }

        fun unavailable(): String = GameSpaceEngine.whyUnavailable(ctx)

        fun openZkm() {
            runCatching { ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        fun launchPkg(p: String) {
            runCatching {
                ctx.packageManager.getLaunchIntentForPackage(p)?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
        /**
         * Pola sub-overlay: hub menutup sendiri, menu tambahan tampil di jendela barunya, dan tombol X di sana
         * menutup jendela itu saja (tidak kembali ke hub).
         */
        fun openSub(page: SubPage) {
            touch()
            expanded = false
            scope.launch { delay(260); GameSubOverlayService.show(ctx, page) }
        }
        /** Tutup hub dulu supaya tidak ikut terekam / ter-screenshot, baru jalankan aksinya. */
        fun hideThen(block: () -> String?) {
            touch()
            expanded = false
            scope.launch(Dispatchers.IO) {
                delay(500)
                val r = block()
                if (r != null) withContext(Dispatchers.Main) { say(r) }
            }
        }
        // Ganti mode CPU: UI langsung berubah (warna + lonjakan animasi), tweak diterapkan di latar.
        val cycleCpuMode = {
            touch()
            val next = cpuMode.next()
            cpuModeIdx = next.ordinal
            modeKey++
            GameSpacePrefs.setCpuMode(ctx, next.ordinal)
            scope.launch(Dispatchers.IO) {
                val r = CpuModeEngine.apply(ctx, next)
                r.message?.let { msg -> withContext(Dispatchers.Main) { say(msg) } }
            }
            Unit
        }
        val togglePerf = {
            act {
                val t = !perf
                when {
                    pkg == null -> ctx.getString(R.string.gs_no_game)
                    !GameSpaceEngine.canRunPrivileged() -> GameSpaceEngine.whyUnavailable(ctx)
                    GameSpaceEngine.setPerformance(ctx, pkg, t) -> { perf = t; null }
                    else -> GameSpaceEngine.whyUnavailable(ctx)
                }
            }
        }
        val toggleCross = {
            touch()
            if (CrosshairService.isRunning) CrosshairService.stop(ctx) else CrosshairService.start(ctx)
            cross = !cross
        }
        // Pilih mode langsung (Compact): tidak berputar, jadi satu ketukan = satu mode.
        val setCpuModeTo: (CpuMode) -> Unit = { m ->
            if (m != cpuMode) {
                touch()
                cpuModeIdx = m.ordinal
                modeKey++
                GameSpacePrefs.setCpuMode(ctx, m.ordinal)
                scope.launch(Dispatchers.IO) {
                    val r = CpuModeEngine.apply(ctx, m)
                    r.message?.let { msg -> withContext(Dispatchers.Main) { say(msg) } }
                }
            }
        }
        // Pintasan game lain di baris atas panel Compact.
        val compactApps by produceState(emptyList<HubShortcut>(), pkg, cube, expanded) {
            if (cube || !expanded) return@produceState
            value = withContext(Dispatchers.IO) {
                GameSpacePrefs.games(ctx).filter { it != pkg }.take(5).mapNotNull { gp ->
                    runCatching {
                        HubShortcut(
                            UsageStatsHelper.appLabel(ctx, gp),
                            ctx.packageManager.getApplicationIcon(gp).toBitmap(96, 96).asImageBitmap()
                        ) { launchPkg(gp) }
                    }.getOrNull()
                }
            }
        }
        val shortcuts by produceState(emptyList<HubShortcut>(), pkg, cube) {
            if (!cube) return@produceState
            value = withContext(Dispatchers.IO) {
                GameSpacePrefs.games(ctx).filter { it != pkg }.take(3).mapNotNull { gp ->
                    runCatching {
                        HubShortcut(
                            UsageStatsHelper.appLabel(ctx, gp),
                            ctx.packageManager.getApplicationIcon(gp).toBitmap(96, 96).asImageBitmap()
                        ) { launchPkg(gp) }
                    }.getOrNull()
                }
            }
        }

        if (fullWin) AnimatedVisibility(
            visibleState = hubVisible,
            // Gerak masuk/keluar (slide kiri-kanan, pita terisi) dikerjakan EnergyCubeHub lewat transisi ini;
            // fade keluar ditunda supaya animasi panel sempat terlihat.
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(200, delayMillis = 560))
        ) {
            val started = GameSpaceEngine.sessionStartedAt
            val hzLabel = if (hz > 0) "${hz}Hz" else ctx.getString(R.string.gs_hub_hz_default)
            val thermal = when {
                snap.temp <= 0f -> "--"
                snap.temp < 38f -> ctx.getString(R.string.gs_hub_cool)
                snap.temp < 43f -> ctx.getString(R.string.gs_hub_warm)
                else -> ctx.getString(R.string.gs_hub_hot)
            }
            val hasGpuFreq = snap.gpuFreqMHz > 0f
            val hasGpuLoad = snap.gpuLoad >= 0
            // Jika frekuensi GPU tidak terbaca / 0 (misal mode non-root Shizuku pada Adreno),
            // otomatis tampilkan GPU load (persen) seperti di versi Compact.
            val displayLoad = gpuShowLoad || (!hasGpuFreq && hasGpuLoad)

            val activeGpuRaw = when {
                displayLoad && hasGpuLoad -> snap.gpuLoad.toFloat()
                hasGpuFreq -> snap.gpuFreqMHz
                hasGpuLoad -> snap.gpuLoad.toFloat()
                else -> -1f
            }
            val activeGpuUnit = if (displayLoad && hasGpuLoad) "%" else if (hasGpuFreq) "MHz" else "%"

            val activeGpuLevel = when {
                !displayLoad && gpuPeak > 0f && hasGpuFreq -> (snap.gpuFreqMHz / gpuPeak).coerceIn(0f, 1f)
                hasGpuLoad -> (snap.gpuLoad / 100f).coerceIn(0f, 1f)
                gpuPeak > 0f && hasGpuFreq -> (snap.gpuFreqMHz / gpuPeak).coerceIn(0f, 1f)
                else -> 0f
            }

            val activeGpuMode = when {
                displayLoad && hasGpuFreq -> "${snap.gpuFreqMHz.roundToInt()} MHz"
                !displayLoad && hasGpuLoad -> "${snap.gpuLoad}% • $thermal"
                else -> thermal
            }

            EnergyCubeHub(
                HubModel(
                    accent = cpuMode.color,
                    title = ctx.getString(R.string.gs_hub_title),
                    cpuGhz = snap.cpuFreqMHz / 1000f,
                    gpuMhz = snap.gpuFreqMHz,
                    gpuRaw = activeGpuRaw,
                    gpuUnit = activeGpuUnit,
                    cpuLevel = if (cpuPeak > 0f) snap.cpuFreqMHz / cpuPeak else 0f,
                    gpuLevel = activeGpuLevel,
                    modeKey = modeKey,
                    cpuMode = ctx.getString(cpuMode.labelRes),
                    gpuMode = activeGpuMode,
                    currentCpuMode = cpuMode,
                    onCpuMode = { cycleCpuMode() },
                    onGpuMode = if (hasGpuFreq && hasGpuLoad) { { gpuShowLoad = !gpuShowLoad } } else null,
                    onSelectCpuMode = { setCpuModeTo(it) },
                    onSelectGpuMetric = { metric ->
                        when (metric) {
                            0 -> { gpuShowLoad = false; touch() }
                            1 -> { gpuShowLoad = true; touch() }
                            2 -> {
                                val t = !dnd
                                if (GameSpaceEngine.setDnd(ctx, t)) dnd = t
                                touch()
                            }
                        }
                    },
                    statusLines = listOf(
                        ctx.getString(if (cpuMode != CpuMode.BALANCE) R.string.gs_hub_line_perf_on else R.string.gs_hub_line_perf_off),
                        ctx.getString(if (dnd || heads) R.string.gs_hub_line_quiet else R.string.gs_hub_line_notif),
                        ctx.getString(when { rec -> R.string.gs_hub_line_rec; cross -> R.string.gs_hub_line_cross; else -> R.string.gs_hub_line_ready })
                    ),
                    netText = when {
                        netKbps >= 1000f -> "%.2f MB/S".format(netKbps / 1024f)
                        netKbps >= 10f -> "%.1f KB/S".format(netKbps)
                        else -> "%.2f KB/S".format(netKbps)
                    },
                    clock = clockFmt.format(Date(nowTick)),
                    battery = snap.batteryLevel,
                    sessionHours = if (started > 0) (nowTick - started).coerceAtLeast(0L) / 3_600_000f else 0f,
                    tempC = snap.temp,
                    brightness = (brightness / 255f).coerceIn(0f, 1f),
                    volume = volume,
                    listMode = listMode,
                    onListMode = { listMode = it },
                    cards = listOfNotNull(
                        HubCard(Icons.Rounded.SportsScore, ctx.getString(R.string.gs_hub_card_perf), cpuMode == CpuMode.PERFORMANCE) { cycleCpuMode() }.takeIf { scCpu },
                        HubCard(Icons.Rounded.Timer, ctx.getString(R.string.gs_hub_card_counter) + if (started > 0) "  " + formatSession(nowTick - started) else "", false) { touch() },
                        HubCard(Icons.Rounded.GpsFixed, ctx.getString(R.string.gs_hub_card_cross), cross) { toggleCross() }.takeIf { scCross },
                        HubCard(
                            if (RootBoost.rootAvailable()) Icons.Rounded.Tune else Icons.Rounded.Lock,
                            ctx.getString(R.string.gsx_hub_card_custom) + if (RootBoost.rootAvailable()) "" else " (Root)",
                            false
                        ) { openSub(SubPage.CUSTOM_MODE) },
                        HubCard(Icons.Rounded.Bolt, ctx.getString(if (RootBoost.rootAvailable()) R.string.gsx_hub_card_root else R.string.gsx_hub_card_shizuku), false) { openSub(SubPage.ROOT_TOOLS) },
                        HubCard(Icons.Rounded.SwapHoriz, ctx.getString(R.string.gs_hub_card_dock), false) { flipEdge(); touch() }
                    ),
                    tiles = listOfNotNull(
                        HubTile(Icons.Rounded.Wifi, ctx.getString(R.string.gs_hub_wifi), wifi) {
                            act { val t = !wifi; if (GameSpaceEngine.setWifi(ctx, t)) { wifi = t; null } else unavailable() }
                        },
                        HubTile(Icons.Rounded.DoNotDisturbOn, ctx.getString(R.string.gs_hub_dnd), dnd) {
                            act { val t = !dnd; if (GameSpaceEngine.setDnd(ctx, t)) { dnd = t; null } else ctx.getString(R.string.gs_need_dnd) }
                        },
                        HubTile(Icons.Rounded.Speed, hzLabel, hz > 0) {
                            act {
                                val steps = GameSpaceEngine.HZ_STEPS
                                val next = steps[(steps.indexOf(hz).coerceAtLeast(0) + 1) % steps.size]
                                if (GameSpaceEngine.setRefreshRate(ctx, next)) { hz = next; null } else unavailable()
                            }
                        },
                        HubTile(Icons.Rounded.Info, ctx.getString(R.string.gs_hub_info), false) { openZkm() },
                        HubTile(Icons.Rounded.ScreenLockRotation, ctx.getString(R.string.gs_hub_rotation), rotLock) {
                            act { val t = !rotLock; if (GameSpaceEngine.setRotationLock(ctx, t)) { rotLock = t; null } else unavailable() }
                        },
                        HubTile(if (rec) Icons.Rounded.Stop else Icons.Rounded.Videocam, ctx.getString(R.string.gs_hub_screen_rec), rec) {
                            if (GameSpaceEngine.recording) {
                                act { GameSpaceEngine.stopRecording(ctx); rec = false; ctx.getString(R.string.gs_rec_stopped) }
                            } else {
                                hideThen {
                                    val p = GameSpaceEngine.startRecording(ctx)
                                    rec = p != null
                                    if (p != null) ctx.getString(R.string.gs_rec_started) else unavailable()
                                }
                            }
                        }.takeIf { scRec },
                        HubTile(Icons.Rounded.RocketLaunch, ctx.getString(R.string.gs_hub_optimize), false) {
                            act { if (GameSpaceEngine.clearRam(ctx)) ctx.getString(R.string.gs_ram_cleared) else unavailable() }
                        }.takeIf { scRam },
                        HubTile(Icons.Rounded.NotificationsOff, ctx.getString(R.string.gs_hub_heads), heads) {
                            act { val t = !heads; if (GameSpaceEngine.setHeadsUp(ctx, t)) { heads = t; null } else unavailable() }
                        },
                        HubTile(Icons.Rounded.PhotoCamera, ctx.getString(R.string.gs_hub_shot), false) {
                            hideThen { GameSpaceEngine.screenshot(ctx)?.let { ctx.getString(R.string.gs_saved, it.substringAfterLast('/')) } ?: unavailable() }
                        },
                        HubTile(Icons.Rounded.BrightnessMedium, ctx.getString(R.string.gs_hub_bright), bright) {
                            act { val t = !bright; if (GameSpaceEngine.setBrightnessManual(ctx, t)) { bright = t; null } else unavailable() }
                        }
                    ),
                    shortcuts = shortcuts,
                    toast = status,
                    onHome = {
                        runCatching {
                            ctx.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                        expanded = false
                    },
                    onEdit = { openZkm() },
                    onCube = { openZkm() },
                    onClose = { expanded = false },
                    onBrightness = { v, final ->
                        brightness = (v * 255f).coerceIn(1f, 255f)
                        touch()
                        if (final) act { if (GameSpaceEngine.setBrightness(ctx, brightness.roundToInt())) null else unavailable() }
                    },
                    onVolume = { v, _ -> volume = v; GameSpaceEngine.setVolume(ctx, v) },
                    onInteract = { touch() }
                )
            )
        } else Row(verticalAlignment = Alignment.Top) {
            if (edge == PanelEdge.RIGHT) {
                Handle(accent, edge, expanded,
                    onToggle = { expanded = !expanded; touch() },
                    onDrag = { dragVertical(it) }, onDragEnd = { persistY() }, onSwipeIn = { expanded = true; touch() })
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(160)) + expandHorizontally(tween(220), expandFrom = if (edge == PanelEdge.RIGHT) Alignment.End else Alignment.Start),
                exit = fadeOut(tween(120)) + shrinkHorizontally(tween(180), shrinkTowards = if (edge == PanelEdge.RIGHT) Alignment.End else Alignment.Start)
            ) {
                Panel(accent, onTouch = { touch() }) {
                    // ---- header ----
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(gameLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                            val started = GameSpaceEngine.sessionStartedAt
                            Text(
                                if (started > 0) formatSession(nowTick - started) else ctx.getString(R.string.gs_preview_hint),
                                color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            "${snap.batteryLevel}%  ${if (snap.temp > 0f) snap.temp.roundToInt().toString() + "°" else "--"}",
                            color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontFamily = FontFamily.Monospace
                        )
                        Spacer(Modifier.width(8.dp))
                        IconChip(Icons.Rounded.SwapHoriz) { flipEdge(); touch() }
                        Spacer(Modifier.width(6.dp))
                        IconChip(Icons.Rounded.ExpandMore) { expanded = false }
                    }

                    // ---- pintasan game lain ----
                    if (compactApps.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            compactApps.forEach { sc ->
                                Box(
                                    Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.07f))
                                        .clickable { touch(); sc.onClick() },
                                    contentAlignment = Alignment.Center
                                ) { sc.icon?.let { Image(it, sc.label, Modifier.size(30.dp)) } }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    // ---- ring CPU | FPS | ring GPU ----
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        HubRing((snap.cpuLoad / 100f), cpuMode.color, Modifier.size(84.dp), thickness = 5.dp) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${snap.cpuLoad}%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, fontFamily = FontFamily.Monospace)
                                Text("CPU", color = Color.White.copy(alpha = 0.55f), fontSize = 9.sp, letterSpacing = 1.sp)
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val fpsColor = when {
                                fps >= 55f -> Color(0xFF39E58C)
                                fps >= 40f -> Color(0xFFFFD84D)
                                fps > 0f -> Color(0xFFFF4D5E)
                                else -> accent
                            }
                            Text(
                                if (fps > 0f) fps.roundToInt().toString() else "--",
                                color = fpsColor, fontWeight = FontWeight.Black, fontSize = 38.sp, fontFamily = FontFamily.Monospace
                            )
                            Text("FPS", color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                        HubRing(if (snap.gpuLoad < 0) 0f else snap.gpuLoad / 100f, Color(0xFF5DD6FF), Modifier.size(84.dp), thickness = 5.dp) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (snap.gpuLoad < 0) "N/A" else "${snap.gpuLoad}%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, fontFamily = FontFamily.Monospace)
                                Text("GPU", color = Color.White.copy(alpha = 0.55f), fontSize = 9.sp, letterSpacing = 1.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Sparkline(history, accent, Modifier.fillMaxWidth().height(38.dp))
                    if (history.isNotEmpty()) {
                        val mn = history.filter { it > 0f }.minOrNull() ?: 0f
                        val av = history.filter { it > 0f }.average().let { if (it.isNaN()) 0.0 else it }
                        Text(
                            "min ${mn.roundToInt()}  ·  avg ${av.roundToInt()}  ·  max ${(history.maxOrNull() ?: 0f).roundToInt()}",
                            color = accent.copy(alpha = 0.85f), fontSize = 10.sp, fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    // ---- mode CPU (langsung pilih) ----
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.07f)).padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        CpuMode.entries.forEach { m ->
                            val on = m == cpuMode
                            Box(
                                Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(13.dp))
                                    .background(if (on) m.color.copy(alpha = 0.30f) else Color.Transparent)
                                    .then(if (on) Modifier.border(1.dp, m.color.copy(alpha = 0.7f), RoundedCornerShape(13.dp)) else Modifier)
                                    .clickable { setCpuModeTo(m) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    ctx.getString(m.labelRes), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                                    color = if (on) m.color else Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    // ---- Dynamic live (hanya saat mode Dynamic benar-benar bekerja) ----
                    val dyn by DynamicManager.state.collectAsState()
                    if (dyn.active) {
                        Text(
                            ctx.getString(R.string.gsx_dyn_strip, dyn.level.name, dyn.loadPct, dyn.capPct) +
                                (if (dyn.lastAction.isNotEmpty()) "\n" + dyn.lastAction else ""),
                            color = accent, fontSize = 10.sp, fontFamily = FontFamily.Monospace, lineHeight = 13.sp,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                    }
                    // ---- stat ----
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Stat("RAM", "${snap.ram.percent}%", Modifier.weight(1f))
                        Stat("HZ", if (snap.refreshRateHz > 0f) snap.refreshRateHz.roundToInt().toString() else "--", Modifier.weight(1f))
                        Stat("NET", if (netKbps >= 1000f) "%.1fM".format(netKbps / 1024f) else "${netKbps.roundToInt()}K", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))

                    // ---- tombol cepat (4 kolom x 3 baris) ----
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile(Icons.Rounded.PhotoCamera, ctx.getString(R.string.gs_tile_shot), false, accent, Modifier.weight(1f)) {
                            hideThen { GameSpaceEngine.screenshot(ctx)?.let { ctx.getString(R.string.gs_saved, it.substringAfterLast('/')) } ?: unavailable() }
                        }
                        Tile(if (rec) Icons.Rounded.Stop else Icons.Rounded.FiberManualRecord,
                            ctx.getString(if (rec) R.string.gs_tile_stop else R.string.gs_tile_rec), rec, accent, Modifier.weight(1f)) {
                            if (GameSpaceEngine.recording) {
                                act { GameSpaceEngine.stopRecording(ctx); rec = false; ctx.getString(R.string.gs_rec_stopped) }
                            } else {
                                hideThen {
                                    val p = GameSpaceEngine.startRecording(ctx)
                                    rec = p != null
                                    if (p != null) ctx.getString(R.string.gs_rec_started) else unavailable()
                                }
                            }
                        }
                        Tile(Icons.Rounded.DoNotDisturbOn, ctx.getString(R.string.gs_tile_dnd), dnd, accent, Modifier.weight(1f)) {
                            act { val t = !dnd; if (GameSpaceEngine.setDnd(ctx, t)) { dnd = t; null } else ctx.getString(R.string.gs_need_dnd) }
                        }
                        Tile(Icons.Rounded.NotificationsOff, ctx.getString(R.string.gs_tile_heads), heads, accent, Modifier.weight(1f)) {
                            act { val t = !heads; if (GameSpaceEngine.setHeadsUp(ctx, t)) { heads = t; null } else unavailable() }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile(Icons.Rounded.Wifi, ctx.getString(R.string.gs_hub_wifi), wifi, accent, Modifier.weight(1f)) {
                            act { val t = !wifi; if (GameSpaceEngine.setWifi(ctx, t)) { wifi = t; null } else unavailable() }
                        }
                        Tile(Icons.Rounded.ScreenLockRotation, ctx.getString(R.string.gs_hub_rotation), rotLock, accent, Modifier.weight(1f)) {
                            act { val t = !rotLock; if (GameSpaceEngine.setRotationLock(ctx, t)) { rotLock = t; null } else unavailable() }
                        }
                        Tile(Icons.Rounded.Speed, if (hz > 0) "${hz}Hz" else ctx.getString(R.string.gs_hub_hz_default), hz > 0, accent, Modifier.weight(1f)) {
                            act {
                                val steps = GameSpaceEngine.HZ_STEPS
                                val next = steps[(steps.indexOf(hz).coerceAtLeast(0) + 1) % steps.size]
                                if (GameSpaceEngine.setRefreshRate(ctx, next)) { hz = next; null } else unavailable()
                            }
                        }
                        Tile(Icons.Rounded.CleaningServices, ctx.getString(R.string.gs_tile_ram), false, accent, Modifier.weight(1f)) {
                            act { if (GameSpaceEngine.clearRam(ctx)) ctx.getString(R.string.gs_ram_cleared) else unavailable() }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile(Icons.Rounded.BrightnessMedium, ctx.getString(R.string.gs_tile_bright), bright, accent, Modifier.weight(1f)) {
                            act { val t = !bright; if (GameSpaceEngine.setBrightnessManual(ctx, t)) { bright = t; null } else unavailable() }
                        }
                        Tile(Icons.Rounded.RocketLaunch, ctx.getString(R.string.gs_tile_perf), perf, accent, Modifier.weight(1f)) { togglePerf() }
                        Tile(Icons.Rounded.GpsFixed, ctx.getString(R.string.gs_tile_cross), cross, accent, Modifier.weight(1f)) { toggleCross() }
                        Tile(
                            if (RootBoost.rootAvailable()) Icons.Rounded.Tune else Icons.Rounded.Lock,
                            ctx.getString(R.string.gsx_hub_card_custom) + if (RootBoost.rootAvailable()) "" else " (Root)",
                            false, accent, Modifier.weight(1f)
                        ) { openSub(SubPage.CUSTOM_MODE) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile(Icons.Rounded.Bolt, ctx.getString(if (RootBoost.rootAvailable()) R.string.gsx_hub_card_root else R.string.gsx_hub_card_shizuku), false, accent, Modifier.weight(1f)) { openSub(SubPage.ROOT_TOOLS) }
                        Tile(Icons.Rounded.Info, ctx.getString(R.string.gs_hub_info), false, accent, Modifier.weight(1f)) { openZkm() }
                        Tile(Icons.Rounded.CleaningServices, ctx.getString(R.string.gsx_tile_trim), false, accent, Modifier.weight(1f)) {
                            act { if (DynamicManager.manualTrim(ctx)) ctx.getString(R.string.gsx_trim_done) else unavailable() }
                        }
                        Tile(Icons.Rounded.Speed, ctx.getString(R.string.gsx_tile_dyn), GameSpacePrefs.cpuMode(ctx) == CpuMode.DYNAMIC.ordinal, accent, Modifier.weight(1f)) {
                            act {
                                GameSpacePrefs.setCpuMode(ctx, CpuMode.DYNAMIC.ordinal)
                                val r = CpuModeEngine.apply(ctx, CpuMode.DYNAMIC)
                                r.message ?: ctx.getString(R.string.gsx_dyn_on)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    // ---- slider volume + kecerahan ----
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.BrightnessMedium, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                        Slider(
                            value = brightness,
                            onValueChange = { brightness = it; touch() },
                            onValueChangeFinished = { act { GameSpaceEngine.setBrightness(ctx, brightness.roundToInt()); null } },
                            valueRange = 1f..255f,
                            colors = SliderDefaults.colors(
                                thumbColor = accent, activeTrackColor = accent,
                                inactiveTrackColor = Color.White.copy(alpha = 0.16f)
                            ),
                            modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.VolumeUp, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                        Slider(
                            value = volume,
                            onValueChange = { volume = it; GameSpaceEngine.setVolume(ctx, it); touch() },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = accent, activeTrackColor = accent,
                                inactiveTrackColor = Color.White.copy(alpha = 0.16f)
                            ),
                            modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                        )
                    }
                    if (status.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(status, color = accent, fontSize = 11.sp, maxLines = 2)
                    }
                }
            }

            if (edge == PanelEdge.LEFT) {
                Handle(accent, edge, expanded,
                    onToggle = { expanded = !expanded; touch() },
                    onDrag = { dragVertical(it) }, onDragEnd = { persistY() }, onSwipeIn = { expanded = true; touch() })
            }
        }
    }
}

// ============================ potongan UI panel ==============================================

private val PanelFill = Color(0xF40A0C14)

@Composable
private fun Panel(accent: Color, onTouch: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    val maxH = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.94f).dp
    Column(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .width(324.dp)
            .heightIn(max = maxH)
            .clip(shape)
            .background(PanelFill)
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.14f), Color.Transparent), endY = 380f))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f))), shape)
            .pointerInput(Unit) {
                // Pantau sentuhan apa pun di dalam panel supaya timer auto-close (bila aktif) tidak jalan saat dipakai.
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        onTouch()
                    }
                }
            }
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun Handle(
    accent: Color,
    edge: PanelEdge,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onSwipeIn: () -> Unit
) {
    // Area sentuh 28dp lebar supaya mudah ditangkap jari, pill visualnya hanya 7dp.
    Box(
        modifier = Modifier
            .width(28.dp)
            .height(96.dp)
            .pointerInput(edge) {
                var dx = 0f
                detectDragGestures(
                    onDragStart = { dx = 0f },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd
                ) { change, drag ->
                    change.consume()
                    dx += drag.x
                    onDrag(drag.y)
                    val inward = if (edge == PanelEdge.RIGHT) -dx else dx
                    if (!expanded && inward > 36f) { onSwipeIn(); dx = 0f }
                }
            }
            .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onToggle() },
        contentAlignment = if (edge == PanelEdge.RIGHT) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            Modifier
                .width(if (expanded) 5.dp else 7.dp)
                .height(if (expanded) 44.dp else 72.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.92f), accent, accent.copy(alpha = 0.45f))))
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
        )
    }
}

@Composable
private fun IconChip(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(17.dp)) }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.07f)).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, letterSpacing = 0.8.sp)
    }
}

@Composable
private fun Tile(icon: ImageVector, label: String, active: Boolean, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg = if (active) Brush.verticalGradient(listOf(accent.copy(alpha = 0.34f), accent.copy(alpha = 0.12f)))
             else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.04f)))
    val fg = if (active) Color.White else Color.White.copy(alpha = 0.82f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.dp, if (active) accent.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = if (active) accent else fg, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = fg, fontSize = 10.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
    }
}

@Composable
private fun Sparkline(values: List<Float>, accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val maxV = (values.maxOrNull() ?: 60f).coerceAtLeast(30f)
        val step = size.width / (values.size - 1)
        val path = Path()
        val fillPath = Path()
        values.forEachIndexed { i, v ->
            val p = Offset(i * step, size.height - (v / maxV).coerceIn(0f, 1f) * size.height)
            if (i == 0) {
                path.moveTo(p.x, p.y)
                fillPath.moveTo(p.x, size.height)
                fillPath.lineTo(p.x, p.y)
            } else {
                path.lineTo(p.x, p.y)
                fillPath.lineTo(p.x, p.y)
            }
        }
        fillPath.lineTo(size.width, size.height)
        fillPath.close()
        drawPath(fillPath, Brush.verticalGradient(listOf(accent.copy(alpha = 0.30f), Color.Transparent)))
        drawPath(path, accent, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
    }
}

private fun formatSession(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}
