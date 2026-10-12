/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 *
 * Handheld Game Space - konsol layar penuh 16:9 (original ZKM design).
 * Reaktor animasi di tengah, daftar game bersudut di kiri, tombol launch di kanan,
 * tab Game Lobby / Arsenal di bawah. Semua warna mengikuti aksen Game Space.
 */
@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.zuan.kernelmanager.next.feature.gamespace

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.StayCurrentLandscape
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale as scaleDraw
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.feature.home.menu.AppIcon
import com.zuan.kernelmanager.next.feature.home.menu.formatUsageDuration
import com.zuan.kernelmanager.next.library.device.usage.UsageStatsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ==================================== bentuk & warna ======================================

private val Ink = Color(0xFF05060B)

/** Bentuk bersama dari design system Mecha (HandheldMecha.kt). */
private val Slant = MechaSlant
private val Chamfer = MechaChamfer

// ==================================== telemetri ===========================================

internal data class Telemetry(val battery: Int, val tempC: Float, val ramPct: Int, val charging: Boolean)

internal fun readTelemetry(c: Context): Telemetry {
    val i = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val pct = if (level >= 0 && scale > 0) level * 100 / scale else 0
    val temp = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
    val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    val mi = ActivityManager.MemoryInfo()
    c.getSystemService(ActivityManager::class.java)?.getMemoryInfo(mi)
    val ram = if (mi.totalMem > 0) (((mi.totalMem - mi.availMem) * 100) / mi.totalMem).toInt() else 0
    return Telemetry(pct, temp, ram, charging)
}

// ==================================== layar utama =========================================

@Composable
fun HandheldGameSpace(
    onSwitchToExplore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // Aksen berubah mulus ke seluruh konsol saat diganti dari panel setelan.
    var accentLong by remember { mutableLongStateOf(GameSpacePrefs.accent(ctx)) }
    val accent by animateColorAsState(Color(accentLong), tween(500), label = "accent")
    var enabled by remember { mutableStateOf(GameSpacePrefs.isEnabled(ctx)) }
    var dialog by remember { mutableStateOf<HhDialog>(HhDialog.None) }

    var gamesTick by remember { mutableIntStateOf(0) }
    val games by produceState(initialValue = GameSpacePrefs.games(ctx), gamesTick) {
        while (true) { value = GameSpacePrefs.games(ctx); delay(1500) }
    }
    var global by remember { mutableStateOf(GameSpacePrefs.globalSettings(ctx)) }
    var cpuMode by remember { mutableStateOf(CpuMode.fromOrdinal(GameSpacePrefs.cpuMode(ctx))) }
    val look = remember { HubLookState(ctx) }
    LaunchedEffect(Unit) { look.loadBg() }
    var tab by remember { mutableIntStateOf(0) }
    var selected by remember { mutableIntStateOf(0) }
    val sel = if (games.isEmpty()) -1 else selected.coerceIn(0, games.lastIndex)
    val selPkg: String? = games.getOrNull(sel)

    val telemetry by produceState(initialValue = readTelemetry(ctx)) {
        while (true) { value = withContext(Dispatchers.IO) { readTelemetry(ctx) }; delay(2000) }
    }
    val clock by produceState(initialValue = "") {
        val f = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) { value = f.format(Date()); delay(5000) }
    }

    // Detektor game hidup/mati mengikuti saklar utama (juga memulihkan service setelah proses dimatikan).
    LaunchedEffect(enabled) {
        if (enabled) { if (!GameDetectorService.isRunning) GameDetectorService.start(ctx) }
        else GameDetectorService.stop(ctx)
    }

    fun update(s: GameSettings) { global = s; GameSpacePrefs.setGlobalSettings(ctx, s) }
    fun cycleCpu() {
        val n = cpuMode.next()
        cpuMode = n
        GameSpacePrefs.setCpuMode(ctx, n.ordinal)
        scope.launch(Dispatchers.IO) { CpuModeEngine.apply(ctx, n) }
    }
    fun setCpu(m: CpuMode) {
        cpuMode = m
        GameSpacePrefs.setCpuMode(ctx, m.ordinal)
        scope.launch(Dispatchers.IO) { CpuModeEngine.apply(ctx, m) }
    }
    fun launchGame(pkg: String) {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) {
            runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        } else {
            Toast.makeText(ctx, R.string.gs_hh_cannot_launch, Toast.LENGTH_SHORT).show()
        }
    }

    // ---- efek: ledakan reaktor tiap ganti game, sapuan saat START ----
    val burst = remember { Animatable(1f) }
    LaunchedEffect(selPkg) { burst.snapTo(0f); burst.animateTo(1f, tween(950, easing = FastOutSlowInEasing)) }

    val launchFx = remember { Animatable(0f) }
    val launching by remember { derivedStateOf { launchFx.value > 0f } }
    fun startGame(pkg: String) {
        if (launchFx.isRunning || launching) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            val fx = GameHubPrefs.bool(ctx, "gen_start_anim", true)
            if (fx) launchFx.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            launchGame(pkg)
            if (fx) delay(350)
            launchFx.snapTo(0f)
        }
    }

    // ---- animasi bersama ----
    val inf = rememberInfiniteTransition(label = "hh")
    val rotA by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(26000, easing = LinearEasing)), label = "rotA")
    val rotB by inf.animateFloat(360f, 0f, infiniteRepeatable(tween(41000, easing = LinearEasing)), label = "rotB")
    val pulse by inf.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse"
    )
    val drift by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing)), label = "drift")
    val shimmer by inf.animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "shimmer")

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val openAnim = remember { GameHubPrefs.bool(ctx, "gen_open_anim", true) }
    val enterSpec: androidx.compose.animation.core.AnimationSpec<Float> =
        if (openAnim) spring(dampingRatio = 0.7f, stiffness = 90f) else androidx.compose.animation.core.snap()
    val enter by animateFloatAsState(if (entered) 1f else 0f, enterSpec, label = "enter")

    // Konsol "mundur" ke belakang saat dialog terbuka: mengecil sedikit, meredup, ter-blur.
    val dlgOpen = dialog != HhDialog.None
    val depth by animateFloatAsState(if (dlgOpen) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 300f), label = "depth")

    Box(modifier.fillMaxSize().background(Ink)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val sc = 1f - 0.03f * depth
                    scaleX = sc; scaleY = sc
                    alpha = 1f - 0.3f * depth
                }
                .then(if (depth > 0.01f) Modifier.blur((10f * depth).dp) else Modifier)
        ) {
            HandheldBackdrop(accent = accent, drift = { drift }, pulse = { pulse }, look = look.toLook())

            Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 10.dp)) {
                Box(Modifier.mechaEnter(0, dx = 0.dp, dy = (-26).dp)) {
                    TopBar(
                        accent = accent, clock = clock, t = telemetry,
                        onExplore = onSwitchToExplore, onSettings = { dialog = HhDialog.Settings }
                    )
                }
                Spacer(Modifier.height(6.dp))

                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val listW = (maxWidth * 0.30f).coerceIn(210.dp, 340.dp)
                    Row(Modifier.fillMaxSize()) {
                        GameList(
                            games = games, selected = sel, accent = accent,
                            onSelect = {
                                if (it != selected) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selected = it
                            },
                            modifier = Modifier.width(listW).fillMaxHeight()
                        )
                        Box(Modifier.weight(1f).fillMaxHeight().padding(start = 8.dp)) {
                            MechaSlideSwitch(tab) { t ->
                                if (t == 1) {
                                    SuperBase(
                                        pkg = selPkg, accent = accent, cpuMode = cpuMode,
                                        onCpu = ::cycleCpu,
                                        onPlugins = { dialog = HhDialog.Plugins },
                                        onLibrary = { dialog = HhDialog.Picker },
                                        onCenter = { dialog = HhDialog.Center(CenterSection.PLAYTIME) },
                                        onAtmosphere = { dialog = HhDialog.Center(CenterSection.ATMOSPHERE) },
                                        onControl = { dialog = HhDialog.Control(selPkg) },
                                        onCustom = { dialog = HhDialog.Custom },
                                        onRoot = { dialog = HhDialog.RootTools }
                                    )
                                } else if (t == 0) {
                                    Lobby(
                                        pkg = selPkg, accent = accent, telemetry = telemetry, enter = enter,
                                        rotA = { rotA }, rotB = { rotB }, pulse = { pulse }, shimmer = { shimmer },
                                        burst = { burst.value },
                                        onStart = { selPkg?.let { startGame(it) } },
                                        onEdit = { selPkg?.let { dialog = HhDialog.Profile(it) } },
                                        onAdd = { dialog = HhDialog.Picker }
                                    )
                                } else {
                                    Arsenal(
                                        s = global, accent = accent, cpuMode = cpuMode,
                                        onChange = ::update, onCpu = ::cycleCpu
                                    )
                                }
                            }
                        }
                    }
                }

                Box(Modifier.mechaEnter(2, dx = 0.dp, dy = 30.dp)) {
                    BottomBar(
                        tab = tab, onTab = { tab = it }, accent = accent, s = global, cpuMode = cpuMode,
                        onAdd = { dialog = HhDialog.Picker }, onChange = ::update, onCpu = ::cycleCpu
                    )
                }
            }
        }

        // ---- dialog Mecha (di dalam konsol, tidak pernah keluar ke Explore) ----
        when (val d = dialog) {
            HhDialog.None -> {}
            HhDialog.Picker -> HandheldAppPicker(
                existing = games, accent = accent,
                onDismiss = { dialog = HhDialog.None },
                onPick = { pkg ->
                    GameSpacePrefs.addGame(ctx, pkg)
                    gamesTick++
                    selected = GameSpacePrefs.games(ctx).indexOf(pkg).coerceAtLeast(0)
                    tab = 0
                }
            )
            is HhDialog.Profile -> HandheldGameProfile(
                pkg = d.pkg, global = global, accent = accent,
                onDismiss = { dialog = HhDialog.None },
                onRemove = { GameSpacePrefs.removeGame(ctx, d.pkg); gamesTick++ }
            )
            HhDialog.Plugins -> HandheldPluginLibrary(
                accent = accent, global = global, onGlobalChange = ::update,
                onDismiss = { dialog = HhDialog.None }
            )
            is HhDialog.Center -> HandheldCenter(
                accent = accent, global = global, onGlobalChange = ::update, look = look,
                games = games, initialSection = d.section,
                onDismiss = { dialog = HhDialog.None }
            )
            is HhDialog.Control -> HandheldControlPanel(
                accent = accent, pkg = d.pkg, cpuMode = cpuMode, onCpuMode = ::setCpu,
                onCustomize = { dialog = HhDialog.Custom },
                onDismiss = { dialog = HhDialog.None }
            )
            HhDialog.Custom -> MechaDialog(
                accent = accent, title = stringResource(R.string.gsx_cm_title), subtitle = stringResource(R.string.gsx_cm_sub),
                icon = Icons.Rounded.Tune, onDismiss = {
                    // mode aktif bisa berubah dari halaman Custom Mode -> segarkan state konsol
                    cpuMode = CpuMode.fromOrdinal(GameSpacePrefs.cpuMode(ctx)); dialog = HhDialog.None
                },
                widthFraction = 0.94f, heightFraction = 0.92f
            ) { _ -> CustomModeContent(accent) }
            HhDialog.RootTools -> {
                val root = RootBoost.rootAvailable()
                MechaDialog(
                    accent = accent,
                    title = stringResource(if (root) R.string.gsx_rt_title else R.string.gsx_nr_title),
                    subtitle = stringResource(if (root) R.string.gsx_rt_sub else R.string.gsx_nr_sub),
                    icon = Icons.Rounded.Bolt, onDismiss = { dialog = HhDialog.None },
                    widthFraction = 0.86f, heightFraction = 0.92f
                ) { _ -> RootToolsContent(accent) }
            }
            HhDialog.Settings -> HandheldSettingsPanel(
                accent = accent, accentLong = accentLong,
                enabled = enabled,
                onEnabled = { enabled = it; GameSpacePrefs.setEnabled(ctx, it) },
                global = global, onGlobalChange = ::update,
                onAccentChange = { accentLong = it; GameSpacePrefs.setAccent(ctx, it) },
                onSwitchToExplore = onSwitchToExplore,
                onDismiss = { dialog = HhDialog.None }
            )
        }

        if (launching) LaunchSlash(accent) { launchFx.value }
    }
}

/** Sapuan potongan miring saat START GAME ditekan, lalu kilat putih sebelum game dibuka. */
@Composable
private fun LaunchSlash(accent: Color, progress: () -> Float) {
    Canvas(Modifier.fillMaxSize()) {
        val t = progress()
        val w = size.width
        val h = size.height
        val bars = 8
        val skew = h * 0.35f
        for (i in 0 until bars) {
            val y0 = h * i / bars
            val y1 = h * (i + 1) / bars
            val local = (t * 1.5f - i * 0.07f).coerceIn(0f, 1f)
            val x = (w + skew) * local
            val path = Path().apply {
                moveTo(-skew, y0); lineTo(x, y0); lineTo(x - skew * 0.4f, y1); lineTo(-skew, y1); close()
            }
            drawPath(path, accent.copy(alpha = 0.92f))
        }
        drawRect(Color.White.copy(alpha = ((t - 0.75f) / 0.25f).coerceIn(0f, 1f) * 0.9f))
    }
}

// ==================================== latar HUD ===========================================

@Composable
private fun HandheldBackdrop(accent: Color, drift: () -> Float, pulse: () -> Float, look: HubLook) {
    val ctx = LocalContext.current
    val particles = GameHubPrefs.bool(ctx, "at_particles", true)
    val vignette = GameHubPrefs.bool(ctx, "at_vignette", true)

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF090B14), Color(0xFF040509))))
        drawRect(
            Brush.radialGradient(
                listOf(accent.copy(alpha = 0.16f + 0.06f * pulse()), Color.Transparent),
                center = Offset(w * 0.5f, h * 0.52f), radius = w * 0.55f
            )
        )

        // garis diagonal tipis (rasa "kecepatan")
        val lineW = 1.dp.toPx()
        for (i in 0..14) {
            val x = i * (w / 11f)
            drawLine(
                accent.copy(alpha = 0.045f), Offset(x, 0f), Offset(x - h * 0.7f, h), strokeWidth = lineW
            )
        }

        // bara cahaya miring di dua sisi
        drawLine(
            Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.5f), Color.Transparent)),
            Offset(w * 0.30f, 0f), Offset(w * 0.30f - h * 0.18f, h), strokeWidth = 2.dp.toPx()
        )
        drawLine(
            Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.35f), Color.Transparent)),
            Offset(w * 0.98f, 0f), Offset(w * 0.98f - h * 0.18f, h), strokeWidth = 2.dp.toPx()
        )

        // bara api yang melayang naik (jika partikel aktif)
        val d = drift()
        if (particles) {
            for (i in 0 until 40) {
                val x0 = (((i * 7919) % 997) / 997f) * w
                val phase = ((i * 104729) % 1013) / 1013f
                val speed = 1 + (i % 3)
                val yFrac = 1f - ((d * speed + phase) % 1f)
                val sway = sin((d * 2 * PI * (1 + i % 2)).toFloat() + i) * 14.dp.toPx()
                val a = sin(yFrac * PI.toFloat()) * 0.55f
                drawCircle(
                    accent.copy(alpha = a.coerceIn(0f, 1f)),
                    radius = (1.1f + (i % 3) * 0.8f).dp.toPx(),
                    center = Offset(x0 + sway, yFrac * h)
                )
            }
        }

        // gaya latar pilihan (Grid / Neon / Hex / Circuit / Vortex / Matrix / Carbon) + LED tepi
        if (look.style == GameHubPrefs.STYLE_IMAGE && look.bg != null) {
            // gambar dari Galeri: isi layar (crop tengah) + peredam supaya konsol tetap terbaca
            val img = look.bg
            val sc = maxOf(w / img.width, h / img.height)
            val srcW = (w / sc).toInt().coerceAtMost(img.width)
            val srcH = (h / sc).toInt().coerceAtMost(img.height)
            drawImage(
                img,
                srcOffset = androidx.compose.ui.unit.IntOffset((img.width - srcW) / 2, (img.height - srcH) / 2),
                srcSize = androidx.compose.ui.unit.IntSize(srcW, srcH),
                dstSize = androidx.compose.ui.unit.IntSize(w.toInt(), h.toInt())
            )
            drawRect(Color.Black.copy(alpha = look.bgDim))
            drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent, Color.Black.copy(alpha = 0.35f))))
        } else {
            drawHubStyle(look.style, accent, d)
        }
        drawHubLed(look, accent, d, pulse())

        // vinyet: tepi layar menggelap supaya fokus ke reaktor (kesan layar premium)
        if (vignette) {
            drawRect(
                Brush.radialGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f)),
                    center = Offset(w * 0.5f, h * 0.5f), radius = maxOf(w, h) * 0.72f
                )
            )
        }

        // braket sudut HUD
        val len = 38.dp.toPx()
        val inset = 12.dp.toPx()
        val sw = 2.dp.toPx()
        val col = accent.copy(alpha = 0.7f)
        fun bracket(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(col, Offset(x, y), Offset(x + dx * len, y), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(col, Offset(x, y), Offset(x, y + dy * len), strokeWidth = sw, cap = StrokeCap.Round)
        }
        bracket(inset, inset, 1f, 1f)
        bracket(w - inset, inset, -1f, 1f)
        bracket(inset, h - inset, 1f, -1f)
        bracket(w - inset, h - inset, -1f, -1f)
    }
}

// ==================================== top bar =============================================

@Composable
private fun TopBar(
    accent: Color, clock: String, t: Telemetry,
    onExplore: () -> Unit, onSettings: () -> Unit
) {
    Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("ZKM", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.width(2.dp).height(18.dp).background(accent))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.gs_hh_brand), color = accent, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 4.sp
        )
        Spacer(Modifier.width(18.dp))
        Text(clock, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(12.dp))
        StatChip(Icons.Rounded.BatteryFull, "${t.battery}%", if (t.battery <= 15 && !t.charging) Color(0xFFFF5A5A) else Color.White)
        Spacer(Modifier.width(6.dp))
        StatChip(Icons.Rounded.Thermostat, "${t.tempC.roundToInt()}°C", if (t.tempC >= 42f) Color(0xFFFF8A4C) else Color.White)
        Spacer(Modifier.width(6.dp))
        StatChip(Icons.Rounded.Memory, "${t.ramPct}%", Color.White)

        Spacer(Modifier.weight(1f))

        // Segmen mode layar: Explore | Handheld (aktif)
        Row(
            Modifier.clip(Slant).background(Color.White.copy(alpha = 0.06f)).border(1.dp, Color.White.copy(alpha = 0.12f), Slant)
        ) {
            ModeSeg(Icons.Rounded.Explore, stringResource(R.string.gs_mode_explore), false, accent, onExplore)
            ModeSeg(Icons.Rounded.StayCurrentLandscape, stringResource(R.string.gs_mode_handheld), true, accent) {}
        }
        Spacer(Modifier.width(10.dp))
        IconKey(Icons.Rounded.Settings, stringResource(R.string.gs_hh_open_settings), active = false, accent = accent, size = 38, onClick = onSettings)
    }
}

@Composable
private fun StatChip(icon: ImageVector, text: String, tint: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.07f)).padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint.copy(alpha = 0.9f), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        AnimatedContent(
            targetState = text,
            transitionSpec = { (slideInVertically { it } + fadeIn(tween(160))) togetherWith (slideOutVertically { -it } + fadeOut(tween(120))) },
            label = "chip"
        ) { t ->
            Text(t, color = tint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun ModeSeg(icon: ImageVector, label: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .mechaClick(0.92f, enabled = !active, onClick = onClick)
            .clip(Slant)
            .background(if (active) accent else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (active) Color.Black else Color.White.copy(alpha = 0.7f), modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            label.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
            color = if (active) Color.Black else Color.White.copy(alpha = 0.7f)
        )
    }
}

/** Tombol ikon bersudut potong. */
@Composable
internal fun IconKey(
    icon: ImageVector, desc: String, active: Boolean, accent: Color,
    size: Int = 44, tint: Color = accent, onClick: () -> Unit
) {
    val bg by animateColorAsState(if (active) tint else Color.White.copy(alpha = 0.06f), tween(220), label = "key")
    Box(
        Modifier
            .mechaClick(0.86f, onClick = onClick)
            .size(size.dp)
            .clip(Chamfer)
            .background(bg)
            .border(1.dp, if (active) tint else Color.White.copy(alpha = 0.14f), Chamfer),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, tint = if (active) Color.Black else Color.White.copy(alpha = 0.8f), modifier = Modifier.size((size * 0.5f).dp))
    }
}

// ==================================== daftar game =========================================

@Composable
private fun GameList(
    games: List<String>, selected: Int, accent: Color,
    onSelect: (Int) -> Unit, modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val listState = rememberLazyListState()
    LaunchedEffect(selected) { if (selected >= 0) listState.animateScrollToItem((selected - 1).coerceAtLeast(0)) }

    if (games.isEmpty()) {
        Column(modifier.padding(end = 16.dp), verticalArrangement = Arrangement.Center) {
            Icon(Icons.Rounded.SportsEsports, null, tint = accent, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.gs_hh_no_games), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(stringResource(R.string.gs_hh_no_games_desc), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }
        return
    }

    LazyColumn(
        state = listState, modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 22.dp, top = 4.dp, bottom = 8.dp)
    ) {
        itemsIndexed(games, key = { _, pkg -> pkg }) { index, pkg ->
            val isSel = index == selected
            val slide by animateDpAsState(if (isSel) 18.dp else 0.dp, spring(dampingRatio = 0.5f, stiffness = 380f), label = "slide")
            val bar by animateFloatAsState(if (isSel) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 300f), label = "bar")
            val iconS by animateFloatAsState(if (isSel) 1.14f else 1f, spring(dampingRatio = 0.4f, stiffness = 420f), label = "iconS")
            val bg by animateColorAsState(
                if (isSel) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.045f), tween(220), label = "rowBg"
            )
            val pt = GameSpacePrefs.playtimeMs(ctx, pkg)
            Box(
                Modifier
                    .mechaEnter(index, dx = (-46).dp)
                    .mechaClick(0.96f) { onSelect(index) }
                    .fillMaxWidth()
                    .height(62.dp)
                    .offset(x = slide)
                    .clip(Slant)
                    .background(bg)
                    .border(if (isSel) 1.5.dp else 1.dp, if (isSel) accent else Color.White.copy(alpha = 0.08f), Slant)
            ) {
                Box(Modifier.align(Alignment.CenterStart).padding(start = 14.dp).width(4.dp).fillMaxHeight(bar * 0.7f).background(accent))
                Row(
                    Modifier.fillMaxSize().padding(start = 22.dp, end = 22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.graphicsLayer { scaleX = iconS; scaleY = iconS }) { AppIcon(pkg, 40.dp) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            UsageStatsHelper.appLabel(ctx, pkg), color = Color.White,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (pt > 0) formatUsageDuration(pt) else stringResource(R.string.gs_hh_never_played),
                            color = if (isSel) accent else Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// ==================================== tab: Game Lobby =====================================

@Composable
private fun Lobby(
    pkg: String?, accent: Color, telemetry: Telemetry, enter: Float,
    rotA: () -> Float, rotB: () -> Float, pulse: () -> Float, shimmer: () -> Float, burst: () -> Float,
    onStart: () -> Unit, onEdit: () -> Unit, onAdd: () -> Unit
) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        // ---- reaktor ----
        BoxWithConstraints(Modifier.weight(1.15f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            val d = minOf(maxWidth, maxHeight)
            Box(
                Modifier.size(d).scale(0.6f + 0.4f * enter).alpha(enter),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val c = center
                    val r = size.minDimension / 2f
                    val a = rotA()
                    val kick = (1f - burst()).let { it * it } * 160f
                    val b = rotB() + kick
                    val p = pulse()

                    // Ambient reactor glow
                    drawCircle(
                        Brush.radialGradient(listOf(accent.copy(alpha = 0.32f + 0.14f * p), Color.Transparent), center = c, radius = r),
                        radius = r, center = c
                    )

                    // ---- 1. ALAS 3D ISOMETRIK & PROYEKSI HOLOGRAFIK ----
                    val baseC = Offset(c.x, c.y + r * 0.88f)
                    // Berkas cahaya volumetrik holografik dari lantai naik ke reaktor
                    val beamW = r * 0.82f
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, accent.copy(alpha = 0.08f + 0.06f * p), accent.copy(alpha = 0.22f + 0.10f * p)),
                            startY = c.y - r * 0.1f, endY = baseC.y
                        ),
                        topLeft = Offset(c.x - beamW / 2f, c.y - r * 0.1f),
                        size = Size(beamW, baseC.y - (c.y - r * 0.1f))
                    )

                    // Cincin alas 3D isometrik bertingkat di lantai
                    scaleDraw(1f, 0.28f, pivot = baseC) {
                        // Lingkaran lantai terluar dengan kisi heksagonal
                        drawCircle(
                            Brush.radialGradient(listOf(accent.copy(alpha = 0.45f + 0.15f * p), Color.Transparent), center = baseC, radius = r * 1.05f),
                            radius = r * 1.05f, center = baseC
                        )
                        drawCircle(accent.copy(alpha = 0.65f), r * 0.85f, baseC, style = Stroke(2.5.dp.toPx()))
                        drawCircle(Color.White.copy(alpha = 0.20f), r * 0.98f, baseC, style = Stroke(1.2.dp.toPx()))
                        // Kisi lantai isometrik
                        for (idx in -4..4) {
                            val lineX = baseC.x + idx * (r * 0.18f)
                            drawLine(accent.copy(alpha = 0.18f), Offset(lineX, baseC.y - r * 0.6f), Offset(lineX, baseC.y + r * 0.6f), 1.dp.toPx())
                        }
                    }

                    // ---- 2. CINCIN GIMBAL 3D GYROSCOPIC (ROTASI MULTI-SUMBU) ----
                    // Gimbal Ring A: Kemiringan Pitch (horizontal tilt)
                    scaleDraw(1f, 0.42f, pivot = c) {
                        rotate(a, pivot = c) {
                            val gr = r * 0.92f
                            drawCircle(accent.copy(alpha = 0.25f), gr, c, style = Stroke(1.5.dp.toPx()))
                            for (k in 0 until 4) {
                                drawArc(
                                    accent.copy(alpha = 0.75f), k * 90f + 10f, 60f, false,
                                    Offset(c.x - gr, c.y - gr), Size(gr * 2, gr * 2),
                                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                        }
                    }

                    // Gimbal Ring B: Kemiringan Yaw (vertical tilt) berputar berlawanan
                    scaleDraw(0.44f, 1f, pivot = c) {
                        rotate(b * 0.85f, pivot = c) {
                            val gr2 = r * 0.88f
                            drawCircle(Color.White.copy(alpha = 0.18f), gr2, c, style = Stroke(1.2.dp.toPx()))
                            for (k in 0 until 3) {
                                drawArc(
                                    lerp(accent, Color.White, 0.4f).copy(alpha = 0.8f), k * 120f + 15f, 75f, false,
                                    Offset(c.x - gr2, c.y - gr2), Size(gr2 * 2, gr2 * 2),
                                    style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                        }
                    }

                    // ---- 3. CASING ARMOR CHASSIS (MECHA BEZEL DENGAN CUTOUT) ----
                    val ticks = 120
                    for (i in 0 until ticks) {
                        val ang = Math.toRadians(a + i * 360.0 / ticks)
                        val major = i % 10 == 0
                        val semi = i % 5 == 0
                        val r1 = r * 0.95f
                        val r2 = r1 - r * (if (major) 0.08f else if (semi) 0.05f else 0.03f)
                        val ca = cos(ang).toFloat()
                        val sa = sin(ang).toFloat()
                        val alphaTick = if (major) 0.95f else if (semi) 0.55f else 0.22f
                        drawLine(
                            accent.copy(alpha = alphaTick),
                            c + Offset(ca * r1, sa * r1), c + Offset(ca * r2, sa * r2),
                            strokeWidth = (if (major) 2.5f else 1f).dp.toPx(), cap = StrokeCap.Round
                        )
                    }

                    // Segmen pelat armor mecha berputar
                    val segR = r * 0.78f
                    for (k in 0 until 3) {
                        drawArc(
                            accent.copy(alpha = 0.70f + 0.25f * p), b + k * 120f, 72f + 20f * p, false,
                            Offset(c.x - segR, c.y - segR), Size(segR * 2, segR * 2),
                            style = Stroke(width = r * 0.048f, cap = StrokeCap.Round)
                        )
                        // Aksen garis dalam segmen
                        drawArc(
                            Color.White.copy(alpha = 0.85f), b + k * 120f + 20f, 32f, false,
                            Offset(c.x - segR, c.y - segR), Size(segR * 2, segR * 2),
                            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    drawCircle(accent.copy(alpha = 0.26f), r * 0.71f, c, style = Stroke(1.2.dp.toPx()))

                    // Busur telemetri bergradien: Baterai & RAM
                    val soft = lerp(accent, Color.White, 0.65f)
                    fun gauge(radius: Float, frac: Float, col: Color) {
                        val tl = Offset(c.x - radius, c.y - radius)
                        val sz = Size(radius * 2, radius * 2)
                        val st = Stroke(width = r * 0.032f, cap = StrokeCap.Round)
                        drawArc(Color.White.copy(alpha = 0.07f), 135f, 270f, false, tl, sz, style = st)
                        drawArc(col, 135f, 270f * frac.coerceIn(0.02f, 1f), false, tl, sz, style = st)
                    }
                    gauge(r * 0.63f, telemetry.battery / 100f, accent)
                    gauge(r * 0.55f, telemetry.ramPct / 100f, soft)

                    // Radar sweep holografik
                    rotate(a * 2.2f, pivot = c) {
                        drawCircle(
                            Brush.sweepGradient(
                                0f to Color.Transparent, 0.70f to Color.Transparent,
                                1f to accent.copy(alpha = 0.38f), center = c
                            ),
                            radius = r * 0.48f, center = c
                        )
                    }

                    // Partikel plasma 3D pada orbit miring (sin(θ) < 0 = di belakang inti)
                    fun plasmaSparks(front: Boolean) {
                        val orbit = r * 0.52f
                        for (k in 0 until 4) {
                            val th = Math.toRadians((a * 3.2f + k * 90f).toDouble())
                            val depth = sin(th).toFloat()
                            if ((depth >= 0f) != front) continue
                            val bx = c.x + cos(th).toFloat() * orbit
                            val by = c.y + depth * orbit * 0.36f
                            val k3 = 0.55f + 0.45f * (depth + 1f) / 2f
                            drawCircle(accent.copy(alpha = 0.35f * k3), 10.dp.toPx() * k3, Offset(bx, by))
                            drawCircle(lerp(accent, Color.White, 0.65f).copy(alpha = k3), 4.dp.toPx() * k3, Offset(bx, by))
                        }
                    }
                    plasmaSparks(false)

                    // ---- 4. INTI REAKTOR 3D GELAP (OBSIDIAN / DARK GLASS CORE) ----
                    val cr = r * 0.42f
                    // Bayangan jatuh pekat (Ambient Occlusion)
                    drawCircle(Color.Black.copy(alpha = 0.75f), cr * 1.08f, c + Offset(0f, cr * 0.12f))
                    // Bevel luar baja mecha gelap
                    drawCircle(
                        Brush.linearGradient(
                            listOf(Color(0xFF2B3145), Color(0xFF0E1119), Color(0xFF04060A)),
                            start = c + Offset(-cr, -cr), end = c + Offset(cr, cr)
                        ),
                        radius = cr * 1.03f, center = c
                    )
                    // Badan kaca obsidian gelap bervolume 3D
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Color(0xFF161A28), Color(0xFF0A0C14), Color(0xFF020306)),
                            center = c + Offset(-cr * 0.28f, -cr * 0.32f), radius = cr * 1.35f
                        ),
                        radius = cr, center = c
                    )
                    // Bevel pencahayaan tajam (Metallic Rim)
                    drawCircle(
                        Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.85f), accent.copy(alpha = 0.65f + 0.35f * p), accent.copy(alpha = 0.18f)),
                            start = c + Offset(-cr * 0.8f, -cr * 0.8f), end = c + Offset(cr * 0.8f, cr * 0.8f)
                        ),
                        cr, c, style = Stroke(3.dp.toPx())
                    )
                    // Cincin neon internal bernapas
                    drawCircle(accent.copy(alpha = 0.24f + 0.18f * p), cr * 0.88f, c, style = Stroke(1.5.dp.toPx()))

                    // Retikel taktis HUD di dalam inti
                    val retLen = cr * 0.20f
                    val retCol = accent.copy(alpha = 0.45f)
                    drawLine(retCol, c + Offset(-cr * 0.7f, 0f), c + Offset(-cr * 0.7f + retLen, 0f), 1.2.dp.toPx())
                    drawLine(retCol, c + Offset(cr * 0.7f, 0f), c + Offset(cr * 0.7f - retLen, 0f), 1.2.dp.toPx())
                    drawLine(retCol, c + Offset(0f, -cr * 0.7f), c + Offset(0f, -cr * 0.7f + retLen), 1.2.dp.toPx())
                    drawLine(retCol, c + Offset(0f, cr * 0.7f), c + Offset(0f, cr * 0.7f - retLen), 1.2.dp.toPx())

                    // Kilap kaca atas (Specular Glass Glint)
                    drawArc(
                        Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.40f), Color.Transparent),
                            start = c + Offset(-cr * 0.75f, -cr * 0.75f), end = c + Offset(0f, 0f)
                        ),
                        190f, 110f, false,
                        Offset(c.x - cr * 0.84f, c.y - cr * 0.84f), Size(cr * 1.68f, cr * 1.68f),
                        style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round)
                    )

                    plasmaSparks(true)

                    // ---- 5. GELOMBANG KEJUT KINETIK 3D SAAT GANTI GAME ----
                    val bu = burst()
                    if (bu < 1f) {
                        // Gelombang kejut radial udara
                        drawCircle(
                            accent.copy(alpha = (1f - bu) * 0.90f), r * (0.42f + 0.65f * bu), c,
                            style = Stroke(((1f - bu) * 5.5f + 0.8f).dp.toPx())
                        )
                        // Gelombang kejut isometrik di lantai
                        scaleDraw(1f, 0.28f, pivot = baseC) {
                            drawCircle(
                                lerp(accent, Color.White, 0.5f).copy(alpha = (1f - bu) * 0.75f),
                                r * (0.5f + 0.85f * bu), baseC,
                                style = Stroke(((1f - bu) * 4f + 0.5f).dp.toPx())
                            )
                        }
                    }
                }

                val pop = remember(pkg) { Animatable(0.55f) }
                LaunchedEffect(pkg) { pop.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 360f)) }
                Crossfade(
                    targetState = pkg, animationSpec = tween(260), label = "core",
                    modifier = Modifier.graphicsLayer {
                        scaleX = pop.value; scaleY = pop.value
                        translationY = (pulse() - 0.5f) * 8.dp.toPx() // Efek melayang holografik di atas alas
                    }
                ) { target ->
                    if (target != null) AppIcon(target, d * 0.27f)
                    else Icon(Icons.Rounded.SportsEsports, null, tint = accent, modifier = Modifier.size(d * 0.2f))
                }
            }
        }

        // ---- info + start ----
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(bottom = 6.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End
        ) {
            if (pkg == null) {
                Text(stringResource(R.string.gs_hh_no_games), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End)
                Spacer(Modifier.height(14.dp))
                StartButton(stringResource(R.string.gs_add_game), Icons.Rounded.Add, accent, shimmer, onAdd)
            } else {
                val cur: String = pkg
                AnimatedContent(
                    targetState = cur,
                    transitionSpec = {
                        (slideInHorizontally(spring(dampingRatio = 0.7f, stiffness = 380f)) { it / 3 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(160)) { -it / 4 } + fadeOut(tween(120)))
                    },
                    contentAlignment = Alignment.CenterEnd,
                    label = "gameName"
                ) { p ->
                    val name = UsageStatsHelper.appLabel(ctx, p)
                    val pt = GameSpacePrefs.playtimeMs(ctx, p)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            stringResource(R.string.gs_hh_selected), color = accent, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 4.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            name, color = Color.White, textAlign = TextAlign.End,
                            fontSize = if (name.length > 14) 26.sp else 36.sp,
                            lineHeight = if (name.length > 14) 28.sp else 38.sp,
                            fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = androidx.compose.ui.text.TextStyle(shadow = Shadow(accent.copy(alpha = 0.8f), blurRadius = 28f))
                        )
                        Text(
                            if (pt > 0) stringResource(R.string.gs_hh_played, formatUsageDuration(pt)) else stringResource(R.string.gs_hh_never_played),
                            color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconKey(Icons.Rounded.Tune, stringResource(R.string.gs_hh_edit_game), active = false, accent = accent, size = 52, onClick = onEdit)
                    Spacer(Modifier.width(10.dp))
                    StartButton(stringResource(R.string.gs_hh_start), Icons.Rounded.PlayArrow, accent, shimmer, onStart)
                }
            }
        }
    }
}

@Composable
private fun StartButton(label: String, icon: ImageVector, accent: Color, shimmer: () -> Float, onClick: () -> Unit) {
    Row(
        Modifier
            .mechaClick(0.92f, onClick = onClick)
            .height(52.dp)
            .widthIn(min = 190.dp)
            .clip(Slant)
            .background(Brush.horizontalGradient(listOf(lerp(accent, Color.Black, 0.1f), lerp(accent, Color.Black, 0.55f))))
            .border(1.5.dp, Color.White.copy(alpha = 0.4f), Slant)
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), endY = size.height * 0.5f),
                    size = Size(size.width, size.height * 0.5f)
                )
                val x = size.width * shimmer()
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.38f), Color.Transparent),
                        start = Offset(x, 0f), end = Offset(x + size.width * 0.25f, size.height)
                    )
                )
            }
            .padding(horizontal = 30.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
    }
}

// ==================================== tab: Arsenal ========================================

@Composable
private fun Arsenal(
    s: GameSettings, accent: Color, cpuMode: CpuMode,
    onChange: (GameSettings) -> Unit, onCpu: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val gap = 10.dp
        val tileW = (maxWidth - 24.dp - gap * 2) / 3f
        Column(Modifier.fillMaxSize().padding(start = 24.dp), verticalArrangement = Arrangement.Center) {
            Text(
                stringResource(R.string.gs_hh_arsenal_title), color = Color.White, fontSize = 24.sp,
                fontWeight = FontWeight.Black, letterSpacing = 4.sp
            )
            Text(stringResource(R.string.gs_hh_arsenal_sub), color = accent, fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(12.dp))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
                ArsenalTile(0, Icons.Rounded.NotificationsOff, stringResource(R.string.gs_opt_dnd), s.dnd, accent, tileW) { onChange(s.copy(dnd = it)) }
                ArsenalTile(1, Icons.Rounded.Bolt, stringResource(R.string.gs_opt_perf), s.performanceMode, accent, tileW) { onChange(s.copy(performanceMode = it)) }
                ArsenalTile(2, Icons.Rounded.CenterFocusStrong, stringResource(R.string.gs_opt_cross), s.crosshair, accent, tileW) { onChange(s.copy(crosshair = it)) }
                ArsenalTile(3, Icons.Rounded.CleaningServices, stringResource(R.string.gs_opt_ram), s.clearRamOnLaunch, accent, tileW) { onChange(s.copy(clearRamOnLaunch = it)) }
                ArsenalTile(4, Icons.Rounded.NotificationsOff, stringResource(R.string.gs_opt_heads), s.blockHeadsUp, accent, tileW) { onChange(s.copy(blockHeadsUp = it)) }
                ArsenalTile(5, Icons.Rounded.Lock, stringResource(R.string.gs_opt_bright), s.lockBrightnessMode, accent, tileW) { onChange(s.copy(lockBrightnessMode = it)) }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.gs_opt_hz), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp))
                listOf(0, 60, 90, 120, 144).forEach { hz ->
                    Box(Modifier.padding(end = 6.dp)) {
                        MechaChip(if (hz == 0) stringResource(R.string.gs_hz_auto) else "$hz", s.refreshRateHz == hz, accent) {
                            onChange(s.copy(refreshRateHz = hz))
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier
                    .mechaClick(0.94f, onClick = onCpu)
                    .clip(Slant)
                    .background(cpuMode.color.copy(alpha = 0.18f))
                    .border(1.dp, cpuMode.color, Slant)
                    .padding(horizontal = 26.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Speed, null, tint = cpuMode.color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "${stringResource(R.string.gs_hh_cpu)}: ${stringResource(cpuMode.labelRes)}",
                    color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ArsenalTile(
    index: Int, icon: ImageVector, title: String, on: Boolean, accent: Color,
    width: androidx.compose.ui.unit.Dp, onToggle: (Boolean) -> Unit
) {
    val bg by animateColorAsState(if (on) accent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f), tween(200), label = "tileBg")
    Row(
        Modifier
            .mechaEnter(index, dx = 0.dp, dy = 26.dp)
            .mechaClick(0.94f) { onToggle(!on) }
            .width(width)
            .height(62.dp)
            .clip(Chamfer)
            .background(bg)
            .border(1.dp, if (on) accent else Color.White.copy(alpha = 0.1f), Chamfer)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (on) accent else Color.White.copy(alpha = 0.55f), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp, modifier = Modifier.weight(1f)
        )
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(if (on) accent else Color.White.copy(alpha = 0.2f)))
    }
}

// ==================================== bottom bar ==========================================

@Composable
private fun BottomBar(
    tab: Int, onTab: (Int) -> Unit, accent: Color, s: GameSettings, cpuMode: CpuMode,
    onAdd: () -> Unit, onChange: (GameSettings) -> Unit, onCpu: () -> Unit
) {
    Box(Modifier.fillMaxWidth().height(62.dp)) {
        Box(Modifier.align(Alignment.CenterStart)) {
            IconKey(Icons.Rounded.Add, stringResource(R.string.gs_add_game), active = false, accent = accent, onClick = onAdd)
        }

        Box(
            Modifier
                .align(Alignment.Center)
                .clip(Slant)
                .background(Color.Black.copy(alpha = 0.45f))
                .border(1.dp, Color.White.copy(alpha = 0.14f), Slant)
                .padding(4.dp)
        ) {
            val hx by animateDpAsState(126.dp * tab, spring(dampingRatio = 0.55f, stiffness = 380f), label = "tabHi")
            Box(Modifier.offset(x = hx).width(124.dp).height(42.dp).clip(Slant).background(accent))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TabItem(Icons.Rounded.SportsEsports, stringResource(R.string.gs_hh_lobby), tab == 0, accent) { onTab(0) }
                TabItem(Icons.Rounded.Dashboard, stringResource(R.string.gsx_tab_superbase), tab == 1, accent) { onTab(1) }
                TabItem(Icons.Rounded.Tune, stringResource(R.string.gs_hh_arsenal), tab == 2, accent) { onTab(2) }
            }
        }

        Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconKey(Icons.Rounded.NotificationsOff, stringResource(R.string.gs_opt_dnd), s.dnd, accent) { onChange(s.copy(dnd = !s.dnd)) }
            IconKey(Icons.Rounded.Bolt, stringResource(R.string.gs_opt_perf), s.performanceMode, accent) { onChange(s.copy(performanceMode = !s.performanceMode)) }
            IconKey(Icons.Rounded.CenterFocusStrong, stringResource(R.string.gs_opt_cross), s.crosshair, accent) { onChange(s.copy(crosshair = !s.crosshair)) }
            IconKey(Icons.Rounded.Speed, stringResource(R.string.gs_hh_cpu), true, accent, tint = cpuMode.color, onClick = onCpu)
        }
    }
}

@Composable
private fun TabItem(icon: ImageVector, label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val fg by animateColorAsState(if (selected) Color.Black else Color.White.copy(alpha = 0.6f), tween(240), label = "tabFg")
    Row(
        Modifier
            .mechaClick(0.94f, onClick = onClick)
            .width(124.dp)
            .height(42.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp, color = fg)
    }
}
