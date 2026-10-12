/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 *
 * Game Space - Custom Mode & Root Tools.
 *
 * Isi halaman ditulis SEKALI ([CustomModeContent], [RootToolsContent]) lalu dipakai di dua tempat:
 *  1. Konsol Handheld  : dibungkus MechaDialog (lihat HandheldGameSpace.kt)
 *  2. Overlay in-game  : dibungkus [SubOverlayFrame] di [GameSubOverlayService]
 *
 * Pola sub-overlay (menu apa pun di hub in-game yang butuh layar sendiri):
 *   ketuk menu -> hub Game Space menutup sendiri -> jendela sub-overlay baru muncul ->
 *   tombol X (kanan atas) menutup sub-overlay itu SAJA dan kembali langsung ke game (tidak balik ke hub).
 */
package com.zuan.kernelmanager.next.feature.gamespace

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.feature.overlay.BaseOverlayService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// =============================================================================================
// Custom Mode
// =============================================================================================

@Composable
internal fun CustomModeContent(accent: Color, initialMode: CpuMode = CpuMode.fromOrdinal(GameSpacePrefs.cpuMode(LocalContext.current))) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(initialMode) }
    var prof by remember(editing) { mutableStateOf(CustomModeStore.get(ctx, editing)) }
    var activeIdx by remember { mutableStateOf(GameSpacePrefs.cpuMode(ctx)) }
    var status by remember { mutableStateOf("") }
    val clusters = remember { CustomModeEngine.clusters() }
    val governors = remember { CustomModeEngine.governors() }
    val gpuMax = remember { CustomModeEngine.gpuMaxMHz() }
    val root = RootBoost.rootAvailable()

    fun upd(p: ModeProfile) {
        prof = p
        CustomModeStore.set(ctx, editing, p)
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        // ---- kiri: pilih mode yang diedit ----
        Column(Modifier.weight(0.27f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CpuMode.entries.forEach { m ->
                CmModeTab(
                    label = stringResource(m.labelRes), color = m.color,
                    selected = m == editing, active = m.ordinal == activeIdx,
                    custom = CustomModeStore.get(ctx, m).enabled
                ) { editing = m }
            }
            Spacer(Modifier.weight(1f))
            HubNote(stringResource(R.string.gsx_cm_legend))
        }

        // ---- kanan: isi mode ----
        HubPage(Modifier.weight(0.73f)) {
            if (editing == CpuMode.DYNAMIC) DynamicEngineCard(editing.color)

            MechaToggleTile(
                Icons.Rounded.Tune, stringResource(R.string.gsx_cm_use), stringResource(R.string.gsx_cm_use_desc),
                prof.enabled, editing.color, Modifier.fillMaxWidth()
            ) { upd(prof.copy(enabled = it)) }

            if (!prof.enabled) {
                HubNote(stringResource(R.string.gsx_cm_builtin))
            } else {
                if (!root) HubNote(stringResource(R.string.gsx_nr_rootonly_body))

                // CPU & GPU: hanya bisa ditulis dengan Root
                Box(Modifier.graphicsLayer { alpha = if (root) 1f else 0.45f }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MechaSectionLabel(stringResource(R.string.gsx_cm_cpu) + if (root) "" else "  ·  " + stringResource(R.string.gsx_dyn_locked), editing.color)
                        HubCard(Modifier.fillMaxWidth()) {
                            val perCluster = prof.clusterLimits.isNotEmpty()
                            if (clusters.size > 1) {
                                HubChoiceRow(
                                    stringResource(R.string.gsx_cm_cluster_scope),
                                    listOf(stringResource(R.string.gsx_cm_cluster_all), stringResource(R.string.gsx_cm_cluster_each)),
                                    if (perCluster) 1 else 0, editing.color
                                ) { i ->
                                    if (root) upd(
                                        if (i == 0) prof.copy(clusterLimits = emptyMap())
                                        else prof.copy(clusterLimits = clusters.associate { it.name to (prof.cpuMinPct to prof.cpuMaxPct) })
                                    )
                                }
                            }
                            if (!perCluster) {
                                PctSlider(stringResource(R.string.gsx_cm_max), prof.cpuMaxPct, 10, editing.color, clusters.maxOfOrNull { it.maxMHz }) {
                                    if (root) upd(prof.copy(cpuMaxPct = it.coerceAtLeast(prof.cpuMinPct).coerceAtLeast(10)))
                                }
                                PctSlider(stringResource(R.string.gsx_cm_min), prof.cpuMinPct, 0, editing.color, clusters.maxOfOrNull { it.maxMHz }) {
                                    if (root) upd(prof.copy(cpuMinPct = it.coerceAtMost(prof.cpuMaxPct)))
                                }
                                if (clusters.isNotEmpty()) {
                                    Text(
                                        clusters.joinToString("   ·   ") { cl ->
                                            "${cl.name.removePrefix("policy")}: ${cl.maxMHz * prof.cpuMinPct / 100}–${cl.maxMHz * prof.cpuMaxPct / 100} MHz"
                                        },
                                        color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, lineHeight = 13.sp
                                    )
                                }
                            } else {
                                clusters.forEachIndexed { idx, cl ->
                                    val (lo, hi) = prof.clusterLimits[cl.name] ?: (prof.cpuMinPct to prof.cpuMaxPct)
                                    fun put(newLo: Int, newHi: Int) =
                                        if (root) upd(prof.copy(clusterLimits = prof.clusterLimits + (cl.name to (newLo to newHi)))) else Unit
                                    MechaSectionLabel("${clusterTitle(idx, clusters.size)} · ${cl.name.removePrefix("policy")}", editing.color)
                                    PctSlider(stringResource(R.string.gsx_cm_max), hi, 10, editing.color, cl.maxMHz) {
                                        val h = it.coerceAtLeast(10); put(lo.coerceAtMost(h), h)
                                    }
                                    PctSlider(stringResource(R.string.gsx_cm_min), lo, 0, editing.color, cl.maxMHz) {
                                        put(it.coerceAtMost(hi), hi)
                                    }
                                }
                            }
                            if (governors.isNotEmpty()) {
                                HubChoiceRow(
                                    stringResource(R.string.gsx_cm_governor),
                                    listOf(stringResource(R.string.gsx_cm_auto)) + governors,
                                    if (prof.governor.isBlank()) 0 else (governors.indexOf(prof.governor) + 1).coerceAtLeast(0),
                                    editing.color
                                ) { i -> if (root) upd(prof.copy(governor = if (i == 0) "" else governors[i - 1])) }
                            }
                        }

                        // GPU
                        MechaSectionLabel(stringResource(R.string.gsx_cm_gpu) + if (root) "" else "  ·  " + stringResource(R.string.gsx_dyn_locked), editing.color)
                        HubCard(Modifier.fillMaxWidth()) {
                            PctSlider(stringResource(R.string.gsx_cm_max), prof.gpuMaxPct, 10, editing.color, gpuMax.takeIf { it > 0 }) {
                                if (root) upd(prof.copy(gpuMaxPct = it.coerceAtLeast(prof.gpuMinPct).coerceAtLeast(10)))
                            }
                            PctSlider(stringResource(R.string.gsx_cm_min), prof.gpuMinPct, 0, editing.color, gpuMax.takeIf { it > 0 }) {
                                if (root) upd(prof.copy(gpuMinPct = it.coerceAtMost(prof.gpuMaxPct)))
                            }
                        }
                    }
                }

                // Thermal
                MechaSectionLabel(stringResource(R.string.gsx_cm_thermal), editing.color)
                MechaToggleTile(
                    Icons.Rounded.AcUnit, stringResource(R.string.gsx_cm_t_guard), stringResource(R.string.gsx_cm_t_guard_desc),
                    prof.thermalOn, editing.color, Modifier.fillMaxWidth()
                ) { upd(prof.copy(thermalOn = it)) }
                if (prof.thermalOn) {
                    HubCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.gsx_cm_t_limit), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("${prof.thermalLimitC}°C", color = editing.color, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                        MechaSlider(prof.thermalLimitC.toFloat(), { upd(prof.copy(thermalLimitC = it.roundToInt())) }, 38f..55f, editing.color)
                    }
                    HubNote(stringResource(R.string.gsx_cm_t_note))
                }
            }

            // Aksi
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MechaButton(
                    if (root) stringResource(R.string.gsx_cm_apply) else stringResource(R.string.gsx_nr_rootonly_title),
                    if (root) Icons.Rounded.Bolt else Icons.Rounded.Lock,
                    if (root) editing.color else Color.Gray,
                    Modifier.weight(1f).graphicsLayer { alpha = if (root) 1f else 0.45f }
                ) {
                    if (!root) {
                        status = ctx.getString(R.string.gsx_cm_need_root)
                        return@MechaButton
                    }
                    val m = editing
                    GameSpacePrefs.setCpuMode(ctx, m.ordinal)
                    activeIdx = m.ordinal
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { CpuModeEngine.apply(ctx, m) }
                        status = r.message ?: ctx.getString(R.string.gsx_cm_applied, ctx.getString(m.labelRes))
                    }
                }
                MechaButton(stringResource(R.string.gsx_cm_reset), null, editing.color, Modifier.weight(1f), filled = false) {
                    CustomModeStore.reset(ctx, editing)
                    prof = CustomModeStore.get(ctx, editing)
                    status = ctx.getString(R.string.gsx_cm_was_reset)
                }
            }
            if (status.isNotEmpty()) Text(status, color = editing.color, fontSize = 12.sp)
            HubBottomSpace()
        }
    }
}

/** Nama ramah untuk cluster ke-[idx] dari [total] (dari yang paling hemat ke yang paling kencang). */
internal fun clusterTitle(idx: Int, total: Int): String = when {
    total <= 1 -> "CPU"
    idx == 0 -> "LITTLE"
    idx == total - 1 -> if (total >= 3) "PRIME" else "BIG"
    total == 4 && idx == 1 -> "MID"
    total == 4 && idx == 2 -> "BIG"
    else -> "BIG"
}

@Composable
private fun CmModeTab(label: String, color: Color, selected: Boolean, active: Boolean, custom: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .mechaClick(0.95f, onClick = onClick)
            .fillMaxWidth()
            .height(52.dp)
            .clip(MechaSlant)
            .background(if (selected) Brush.horizontalGradient(listOf(color.copy(alpha = 0.38f), color.copy(alpha = 0.10f))) else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.05f))))
            .border(if (selected) 1.5.dp else 1.dp, if (selected) color else Color.White.copy(alpha = 0.12f), MechaSlant)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = if (selected) color else Color.White.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
            if (custom) Text("CUSTOM", color = Color.White.copy(alpha = 0.5f), fontSize = 8.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        }
        if (active) Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(color))
    }
}

/** Slider persen (kelipatan 5) dengan label frekuensi perkiraan bila [refMhz] diketahui. */
@Composable
private fun PctSlider(title: String, value: Int, min: Int, accent: Color, refMhz: Int?, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            val mhz = if (refMhz != null && refMhz > 0) "  ≈ ${refMhz * value / 100} MHz" else ""
            Text("$value%$mhz", color = accent, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
        MechaSlider(value.toFloat(), { onChange(((it / 5f).roundToInt() * 5).coerceIn(min, 100)) }, 0f..100f, accent)
    }
}

// =============================================================================================
// Root Tools
// =============================================================================================

@Composable
internal fun RootToolsContent(accent: Color) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val root = RootBoost.rootAvailable()
    if (!root) { ShizukuToolsContent(accent); return }
    val ram = rememberHubBool(RootBoost.RAM, true)
    val prio = rememberHubBool(RootBoost.PRIO, true)
    val sched = rememberHubBool(RootBoost.SCHED, false)
    val io = rememberHubBool(RootBoost.IO, false)
    val swap = rememberHubBool(RootBoost.SWAP, false)
    val splash = rememberHubBool(RootBoost.SPLASH, true)
    var status by remember { mutableStateOf("") }

    HubPage {
        MechaToggleTile(
            Icons.Rounded.Bolt, stringResource(R.string.gsx_rt_splash), stringResource(R.string.gsx_rt_splash_desc),
            splash.value, accent, Modifier.fillMaxWidth()
        ) { splash.value = it }

        MechaSectionLabel(stringResource(R.string.gsx_rt_section), accent)
        if (!root) HubNote(stringResource(R.string.gsx_rt_need_root))
        RtTile(Icons.Rounded.CleaningServices, R.string.gsx_rt_ram, R.string.gsx_rt_ram_d, ram, accent, root)
        RtTile(Icons.Rounded.Speed, R.string.gsx_rt_prio, R.string.gsx_rt_prio_d, prio, accent, root)
        RtTile(Icons.Rounded.Memory, R.string.gsx_rt_sched, R.string.gsx_rt_sched_d, sched, accent, root)
        RtTile(Icons.Rounded.Storage, R.string.gsx_rt_io, R.string.gsx_rt_io_d, io, accent, root)
        RtTile(Icons.Rounded.Memory, R.string.gsx_rt_swap, R.string.gsx_rt_swap_d, swap, accent, root)

        MechaButton(
            stringResource(R.string.gsx_rt_clean_now), Icons.Rounded.CleaningServices, accent,
            Modifier.fillMaxWidth().graphicsLayer { alpha = if (root) 1f else 0.45f }
        ) {
            if (!root) return@MechaButton
            scope.launch {
                val freed = withContext(Dispatchers.IO) {
                    val am = ctx.getSystemService(ActivityManager::class.java)
                    fun avail(): Long { val mi = ActivityManager.MemoryInfo(); am?.getMemoryInfo(mi); return mi.availMem / (1024 * 1024) }
                    val before = avail()
                    if (GameSpaceEngine.clearRam(ctx)) (avail() - before).coerceAtLeast(0L) else -1L
                }
                status = if (freed >= 0) ctx.getString(R.string.gsx_rt_cleaned, freed.toInt()) else ctx.getString(R.string.gsx_rt_failed)
            }
        }
        if (status.isNotEmpty()) Text(status, color = accent, fontSize = 12.sp)
        HubNote(stringResource(R.string.gsx_rt_note))
        HubBottomSpace()
    }
}

@Composable
private fun RtTile(icon: ImageVector, title: Int, desc: Int, pref: HubPref<Boolean>, accent: Color, enabled: Boolean) {
    Box(Modifier.graphicsLayer { alpha = if (enabled) 1f else 0.45f }) {
        MechaToggleTile(icon, stringResource(title), stringResource(desc), pref.value && enabled, accent, Modifier.fillMaxWidth()) {
            if (enabled) pref.value = it
        }
    }
}

// =============================================================================================
// Sub-overlay in-game
// =============================================================================================

enum class SubPage { CUSTOM_MODE, ROOT_TOOLS }

/** Isi halaman "Shizuku Tools" (tanpa root) untuk konsol Handheld dan overlay in-game. */
@Composable
internal fun ShizukuToolsContent(accent: Color) {
    val level = NonRootBoost.level()
    val ready = level == NonRootBoost.Level.SHIZUKU_ADB || level == NonRootBoost.Level.SHIZUKU_ROOT
    val ram = rememberHubBool(RootBoost.RAM, true)
    val gm = rememberHubBool(NonRootBoost.GAMEMODE, true)
    val fixed = rememberHubBool(NonRootBoost.FIXED, false)
    val bg = rememberHubBool(NonRootBoost.BGLIMIT, false)
    val bat = rememberHubBool(NonRootBoost.BATSAVER, false)
    val splash = rememberHubBool(RootBoost.SPLASH, true)
    HubPage {
        HubNote(stringResource(NonRootBoost.levelTextRes(level)))
        MechaToggleTile(
            Icons.Rounded.Bolt, stringResource(R.string.gsx_rt_splash), stringResource(R.string.gsx_rt_splash_desc),
            splash.value, accent, Modifier.fillMaxWidth()
        ) { splash.value = it }
        MechaSectionLabel(stringResource(R.string.gsx_rt_section), accent)
        RtTile(Icons.Rounded.Speed, R.string.gsx_nr_gm, R.string.gsx_nr_gm_d, gm, accent, ready)
        RtTile(Icons.Rounded.CleaningServices, R.string.gsx_nr_ram, R.string.gsx_nr_ram_d, ram, accent, ready)
        RtTile(Icons.Rounded.Memory, R.string.gsx_nr_bg, R.string.gsx_nr_bg_d, bg, accent, ready)
        RtTile(Icons.Rounded.Bolt, R.string.gsx_nr_bat, R.string.gsx_nr_bat_d, bat, accent, ready)
        RtTile(Icons.Rounded.AcUnit, R.string.gsx_nr_fixed, R.string.gsx_nr_fixed_d, fixed, accent, ready)
        HubNote(stringResource(R.string.gsx_nr_keep_hint))
        HubNote(stringResource(R.string.gsx_nr_modes_body))
        HubNote(stringResource(R.string.gsx_nr_rootonly_body))
        HubBottomSpace()
    }
}

/** Bingkai layar penuh untuk menu tambahan di dalam game. Tombol X menutup HANYA jendela ini. */
@Composable
internal fun SubOverlayFrame(
    accent: Color, title: String, subtitle: String?, icon: ImageVector,
    onClose: () -> Unit, content: @Composable () -> Unit
) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 260f)) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f * p.value))
            // sentuhan di area gelap ditelan supaya tidak tembus ke game, tapi TIDAK menutup (hanya X yang menutup)
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center
    ) {
        val shape = remember { MechaPlateShape(22.dp, 8.dp) }
        Column(
            Modifier
                .width(maxWidth * 0.90f)
                .height(maxHeight * 0.90f)
                .graphicsLayer { alpha = p.value; scaleX = 0.94f + 0.06f * p.value; scaleY = 0.94f + 0.06f * p.value }
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xF4121624), Color(0xF4080A12))))
                .border(1.dp, Brush.linearGradient(listOf(accent.copy(alpha = 0.85f), Color.White.copy(alpha = 0.08f), accent.copy(alpha = 0.5f))), shape)
                .padding(start = 26.dp, end = 18.dp, top = 12.dp, bottom = 14.dp)
        ) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title.uppercase(), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp, maxLines = 1)
                    if (subtitle != null) Text(subtitle, color = accent, fontSize = 10.sp, letterSpacing = 1.sp, maxLines = 1)
                }
                IconKey(Icons.Rounded.Close, stringResource(R.string.gsx_sub_close), active = false, accent = accent, size = 40, onClick = onClose)
            }
            MechaDivider(accent)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
    }
}

class GameSubOverlayService : BaseOverlayService() {

    companion object {
        @Volatile var isRunning = false
            private set
        private var page by mutableStateOf(SubPage.CUSTOM_MODE)

        /** Buka (atau ganti isi) sub-overlay. Pemanggil bertanggung jawab menutup hub lebih dulu. */
        fun show(c: Context, p: SubPage) {
            page = p
            runCatching { ContextCompat.startForegroundService(c, Intent(c, GameSubOverlayService::class.java)) }
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, GameSubOverlayService::class.java))
        }
    }

    override val notificationId = 139
    override fun onRunningChanged(running: Boolean) { isRunning = running }

    override fun buildLayoutParams() = overlayParams(
        WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT
    )

    @Composable
    override fun OverlayContent() {
        val accent = Color(GameSpacePrefs.accent(this))
        val close = { stopSelf() }
        when (page) {
            SubPage.CUSTOM_MODE -> SubOverlayFrame(
                accent, stringResource(R.string.gsx_cm_title), stringResource(R.string.gsx_cm_sub), Icons.Rounded.Tune, close
            ) { CustomModeContent(accent) }
            SubPage.ROOT_TOOLS -> SubOverlayFrame(
                accent,
                stringResource(if (RootBoost.rootAvailable()) R.string.gsx_rt_title else R.string.gsx_nr_title),
                stringResource(if (RootBoost.rootAvailable()) R.string.gsx_rt_sub else R.string.gsx_nr_sub),
                Icons.Rounded.Bolt, close
            ) { RootToolsContent(accent) }
        }
    }
}


// =============================================================================================
// Dynamic engine: saklar + status langsung (apa yang benar-benar dilakukan manajer Dynamic)
// =============================================================================================

@Composable
internal fun DynamicEngineCard(accent: Color) {
    val ctx = LocalContext.current
    val st by DynamicManager.state.collectAsState()
    val rootEq = RootBoost.rootAvailable()
    val cpu = rememberHubBool(DynamicManager.PREF_CPU, true)
    val ram = rememberHubBool(DynamicManager.PREF_RAM, true)
    val sto = rememberHubBool(DynamicManager.PREF_STORAGE, true)

    MechaSectionLabel(stringResource(R.string.gsx_dyn_title), accent)
    HubCard(Modifier.fillMaxWidth()) {
        // status langsung
        if (st.active) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(accent))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.gsx_dyn_live) + " · " + st.level.name,
                    color = accent, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black
                )
            }
            Text(
                stringResource(R.string.gsx_dyn_stat1, st.loadPct, st.tempC, st.capPct),
                color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontFamily = FontFamily.Monospace
            )
            Text(
                stringResource(R.string.gsx_dyn_stat2, st.ramFreeMb, st.ramTotalMb, st.storageFreePct),
                color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontFamily = FontFamily.Monospace
            )
            Text(
                stringResource(R.string.gsx_dyn_stat3, st.cpuWrites, st.ramCleans, st.storageTrims),
                color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontFamily = FontFamily.Monospace
            )
            if (st.lastAction.isNotEmpty()) {
                Text(stringResource(R.string.gsx_dyn_last, st.lastAction), color = accent, fontSize = 11.sp)
            }
        } else {
            Text(stringResource(R.string.gsx_dyn_idle), color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, lineHeight = 14.sp)
        }

        MechaToggleTile(
            Icons.Rounded.Speed, stringResource(R.string.gsx_dyn_cpu),
            stringResource(if (rootEq) R.string.gsx_dyn_cpu_d else R.string.gsx_dyn_cpu_lock),
            cpu.value && rootEq, accent, Modifier.fillMaxWidth().graphicsLayer { alpha = if (rootEq) 1f else 0.45f }
        ) { if (rootEq) { cpu.value = it } }
        MechaToggleTile(
            Icons.Rounded.Memory, stringResource(R.string.gsx_dyn_ram), stringResource(R.string.gsx_dyn_ram_d),
            ram.value, accent, Modifier.fillMaxWidth()
        ) { ram.value = it }
        MechaToggleTile(
            Icons.Rounded.Storage, stringResource(R.string.gsx_dyn_sto), stringResource(R.string.gsx_dyn_sto_d),
            sto.value, accent, Modifier.fillMaxWidth()
        ) { sto.value = it }
    }
}
