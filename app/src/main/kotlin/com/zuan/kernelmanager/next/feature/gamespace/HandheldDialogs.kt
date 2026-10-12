/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 *
 * Dialog Handheld Game Space: tambah game, profil game, dan panel setelan.
 * Semuanya dirender DI DALAM konsol memakai komponen Mecha - tidak pernah keluar ke layar Explore.
 */
@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.zuan.kernelmanager.next.feature.gamespace

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.NotificationsPaused
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings as SettingsIcon
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Dialog yang sedang terbuka di atas konsol. */
internal sealed interface HhDialog {
    data object None : HhDialog
    data object Picker : HhDialog
    data class Profile(val pkg: String) : HhDialog
    data object Settings : HhDialog
    data object Plugins : HhDialog
    data class Center(val section: Int = 0) : HhDialog
    data class Control(val pkg: String?) : HhDialog
    data object Custom : HhDialog
    data object RootTools : HhDialog
}

// ==================================== tambah game =========================================

@Composable
internal fun HandheldAppPicker(
    existing: List<String>, accent: Color,
    onDismiss: () -> Unit, onPick: (String) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var gamesOnly by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<String?>(null) }
    val apps by produceState<List<AppRow>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { loadLaunchableApps(ctx) }
    }
    val filtered = remember(apps, query, gamesOnly, existing) {
        apps.orEmpty().filter {
            it.pkg !in existing && (!gamesOnly || it.isGame) &&
                (query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true))
        }
    }

    MechaDialog(
        accent = accent,
        title = stringResource(R.string.gs_add_game),
        subtitle = stringResource(R.string.gs_hh_picker_sub),
        icon = Icons.Rounded.Add,
        onDismiss = onDismiss,
        widthFraction = 0.8f, heightFraction = 0.92f,
        footer = { close ->
            Text(
                stringResource(R.string.gs_hh_apps_count, filtered.size), color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f)
            )
            MechaButton(stringResource(R.string.gs_hh_close), null, accent, filled = false, onClick = close)
        }
    ) { close ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            MechaSearchField(query, { query = it }, stringResource(R.string.gs_search), accent, Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            MechaChip(stringResource(R.string.gs_hh_games_only), gamesOnly, accent) { gamesOnly = !gamesOnly }
        }
        Spacer(Modifier.height(10.dp))

        when {
            apps == null -> ScanningState(accent)
            filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.gs_hh_no_apps), color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp, letterSpacing = 2.sp)
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(filtered, key = { _, r -> r.pkg }) { i, row ->
                    val isPicked = picked == row.pkg
                    val bg by animateColorAsState(
                        if (isPicked) accent else Color.White.copy(alpha = 0.05f), tween(160), label = "pickBg"
                    )
                    val s by animateFloatAsState(if (isPicked) 1.04f else 1f, spring(dampingRatio = 0.4f, stiffness = 500f), label = "pickS")
                    Row(
                        Modifier
                            .mechaEnter(i, dx = 0.dp, dy = 18.dp)
                            .mechaClick(0.95f) {
                                if (picked == null) {
                                    picked = row.pkg
                                    scope.launch { delay(300); onPick(row.pkg); close() }
                                }
                            }
                            .scale(s)
                            .height(58.dp)
                            .clip(MechaChamfer)
                            .background(bg)
                            .border(1.dp, if (isPicked) Color.White else Color.White.copy(alpha = 0.10f), MechaChamfer)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(row.pkg, 38.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                row.label, color = if (isPicked) Color.Black else Color.White, fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                row.pkg, color = if (isPicked) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.4f),
                                fontSize = 9.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (row.isGame) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.gs_tag_game).uppercase(), color = if (isPicked) Color.Black else accent,
                                fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanningState(accent: Color) {
    val t = rememberInfiniteTransition(label = "scanning")
    val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "scanA")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(54.dp)) {
                drawCircle(accent.copy(alpha = 0.2f), radius = size.minDimension / 2f, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                rotate(a) {
                    drawArc(accent, 0f, 90f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.gs_hh_scanning), color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
        }
    }
}

// ==================================== profil game =========================================

@Composable
internal fun HandheldGameProfile(
    pkg: String, global: GameSettings, accent: Color,
    onDismiss: () -> Unit, onRemove: () -> Unit
) {
    val ctx = LocalContext.current
    var custom by remember { mutableStateOf(GameSpacePrefs.hasOverride(ctx, pkg)) }
    var s by remember { mutableStateOf(GameSpacePrefs.settingsFor(ctx, pkg)) }
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) { if (armed) { delay(2600); armed = false } }

    MechaDialog(
        accent = accent,
        title = UsageStatsHelper.appLabel(ctx, pkg),
        subtitle = stringResource(R.string.gs_hh_profile_sub),
        icon = Icons.Rounded.Tune,
        onDismiss = onDismiss,
        widthFraction = 0.82f, heightFraction = 0.92f,
        footer = { close ->
            Spacer(Modifier.weight(1f))
            MechaButton(stringResource(R.string.gs_hh_done), null, accent, onClick = close)
        }
    ) { close ->
        Row(Modifier.weight(1f).fillMaxWidth()) {
            // ---- kiri: identitas ----
            Column(
                Modifier.weight(0.34f).fillMaxHeight().padding(end = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                MiniReactor(pkg, accent)
                Spacer(Modifier.height(10.dp))
                val pt = GameSpacePrefs.playtimeMs(ctx, pkg)
                Text(
                    if (pt > 0) stringResource(R.string.gs_hh_played, formatUsageDuration(pt)) else stringResource(R.string.gs_hh_never_played),
                    color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontFamily = FontFamily.Monospace
                )
                Text(pkg, color = Color.White.copy(alpha = 0.35f), fontSize = 9.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(14.dp))
                MechaButton(
                    label = if (armed) stringResource(R.string.gs_hh_confirm_remove) else stringResource(R.string.gs_remove),
                    icon = Icons.Rounded.Delete, accent = accent, filled = armed, danger = true
                ) {
                    if (armed) { onRemove(); close() } else armed = true
                }
            }

            // ---- kanan: setelan ----
            Column(
                Modifier.weight(0.66f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MechaToggleTile(
                    Icons.Rounded.SportsEsports, stringResource(R.string.gs_use_custom), stringResource(R.string.gs_use_custom_desc),
                    custom, accent, Modifier.fillMaxWidth()
                ) { on ->
                    custom = on
                    if (on) GameSpacePrefs.setOverride(ctx, pkg, s)
                    else { GameSpacePrefs.setOverride(ctx, pkg, null); s = global }
                }
                AnimatedVisibility(
                    visible = custom,
                    enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) + fadeIn(tween(240)),
                    exit = shrinkVertically(tween(200)) + fadeOut(tween(140))
                ) {
                    MechaSettingsEditor(s, accent) { s = it; GameSpacePrefs.setOverride(ctx, pkg, it) }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

/** Reaktor mini: cincin tick berputar mengelilingi ikon game. */
@Composable
private fun MiniReactor(pkg: String, accent: Color) {
    val t = rememberInfiniteTransition(label = "mini")
    val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "miniA")
    val b by t.animateFloat(360f, 0f, infiniteRepeatable(tween(22000, easing = LinearEasing)), label = "miniB")
    Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2f
            for (i in 0 until 48) {
                val ang = Math.toRadians(a + i * 360.0 / 48)
                val major = i % 6 == 0
                val r1 = r * 0.96f
                val r2 = r1 - r * (if (major) 0.10f else 0.05f)
                drawLine(
                    accent.copy(alpha = if (major) 0.95f else 0.3f),
                    c + Offset(cos(ang).toFloat() * r1, sin(ang).toFloat() * r1),
                    c + Offset(cos(ang).toFloat() * r2, sin(ang).toFloat() * r2),
                    strokeWidth = (if (major) 2.5f else 1f).dp.toPx(), cap = StrokeCap.Round
                )
            }
            val segR = r * 0.74f
            for (k in 0 until 3) {
                drawArc(
                    accent.copy(alpha = 0.6f), b + k * 120f, 70f, false,
                    Offset(c.x - segR, c.y - segR), androidx.compose.ui.geometry.Size(segR * 2, segR * 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(r * 0.05f)
                )
            }
            drawCircle(accent.copy(alpha = 0.18f), r * 0.6f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
        AppIcon(pkg, 58.dp)
    }
}

// ==================================== editor setelan ======================================

/** Editor setelan Mecha: dipakai untuk profil global (Arsenal/Defaults) dan override per game. */
@Composable
internal fun MechaSettingsEditor(s: GameSettings, accent: Color, onChange: (GameSettings) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MechaSectionLabel(stringResource(R.string.gs_hh_section_loadout), accent)
        FlowRow(
            maxItemsInEachRow = 2,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val m = Modifier.weight(1f)
            MechaToggleTile(Icons.Rounded.Widgets, stringResource(R.string.gs_opt_panel), stringResource(R.string.gs_opt_panel_desc), s.showPanel, accent, m) { onChange(s.copy(showPanel = it)) }
            MechaToggleTile(Icons.Rounded.NotificationsOff, stringResource(R.string.gs_opt_dnd), stringResource(R.string.gs_opt_dnd_desc), s.dnd, accent, m) { onChange(s.copy(dnd = it)) }
            MechaToggleTile(Icons.Rounded.NotificationsPaused, stringResource(R.string.gs_opt_heads), stringResource(R.string.gs_opt_heads_desc), s.blockHeadsUp, accent, m) { onChange(s.copy(blockHeadsUp = it)) }
            MechaToggleTile(Icons.Rounded.Brightness6, stringResource(R.string.gs_opt_bright), stringResource(R.string.gs_opt_bright_desc), s.lockBrightnessMode, accent, m) { onChange(s.copy(lockBrightnessMode = it)) }
            MechaToggleTile(Icons.Rounded.Bolt, stringResource(R.string.gs_opt_perf), stringResource(R.string.gs_opt_perf_desc), s.performanceMode, accent, m) { onChange(s.copy(performanceMode = it)) }
            MechaToggleTile(Icons.Rounded.CleaningServices, stringResource(R.string.gs_opt_ram), stringResource(R.string.gs_opt_ram_desc), s.clearRamOnLaunch, accent, m) { onChange(s.copy(clearRamOnLaunch = it)) }
        }

        MechaSectionLabel(stringResource(R.string.gs_hh_section_refresh), accent)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 60, 90, 120, 144).forEach { hz ->
                MechaChip(if (hz == 0) stringResource(R.string.gs_hz_auto) else "$hz Hz", s.refreshRateHz == hz, accent) {
                    onChange(s.copy(refreshRateHz = hz))
                }
            }
        }

        MechaSectionLabel(stringResource(R.string.gs_hh_section_cross), accent)
        MechaToggleTile(
            Icons.Rounded.CenterFocusStrong, stringResource(R.string.gs_opt_cross), stringResource(R.string.gs_opt_cross_desc),
            s.crosshair, accent, Modifier.fillMaxWidth()
        ) { onChange(s.copy(crosshair = it)) }
        AnimatedVisibility(
            visible = s.crosshair,
            enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) + fadeIn(tween(240)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(140))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(CrosshairService.STYLE_COUNT) { i ->
                        val on = i == s.crosshairStyle
                        val sc by animateFloatAsState(if (on) 1.08f else 1f, spring(dampingRatio = 0.4f, stiffness = 480f), label = "xhS")
                        Box(
                            Modifier
                                .scale(sc)
                                .size(52.dp)
                                .mechaClick(0.9f) { onChange(s.copy(crosshairStyle = i)) }
                                .clip(MechaChamfer)
                                .background(if (on) accent.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.35f))
                                .border(if (on) 1.5.dp else 1.dp, if (on) accent else Color.White.copy(alpha = 0.12f), MechaChamfer),
                            contentAlignment = Alignment.Center
                        ) { CrosshairView(i, 26, accent) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${stringResource(R.string.gs_cross_size)}: ${s.crosshairSizeDp}dp", color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(130.dp)
                    )
                    MechaSlider(s.crosshairSizeDp.toFloat(), { onChange(s.copy(crosshairSizeDp = it.roundToInt())) }, 16f..72f, accent)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${stringResource(R.string.gs_cross_opacity)}: ${s.crosshairOpacity}%", color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(130.dp)
                    )
                    MechaSlider(s.crosshairOpacity.toFloat(), { onChange(s.copy(crosshairOpacity = it.roundToInt())) }, 20f..100f, accent)
                }
            }
        }
    }
}

// ==================================== panel setelan =======================================

@Composable
internal fun HandheldSettingsPanel(
    accent: Color,
    accentLong: Long,
    enabled: Boolean,
    onEnabled: (Boolean) -> Unit,
    global: GameSettings,
    onGlobalChange: (GameSettings) -> Unit,
    onAccentChange: (Long) -> Unit,
    onSwitchToExplore: () -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var edge by remember { mutableStateOf(GameSpacePrefs.edge(ctx)) }
    var autoHide by remember { mutableIntStateOf(GameSpacePrefs.autoHideSeconds(ctx)) }
    var style by remember { mutableIntStateOf(GameSpacePrefs.panelStyle(ctx)) }

    val live by produceState(initialValue = Triple(false, null as String?, false)) {
        while (true) {
            value = Triple(GameDetectorService.isRunning, GameDetectorService.currentGame, GameBarService.isRunning)
            delay(1000)
        }
    }
    val perms by produceState(initialValue = readPerms(ctx)) {
        while (true) { value = withContext(Dispatchers.IO) { readPerms(ctx) }; delay(1500) }
    }

    MechaDialog(
        accent = accent,
        title = stringResource(R.string.gs_master_title),
        subtitle = stringResource(R.string.gs_hh_settings_sub),
        icon = Icons.Rounded.SettingsIcon,
        onDismiss = onDismiss,
        widthFraction = 0.78f, heightFraction = 0.92f
    ) { close ->
        MechaTabs(
            listOf(
                stringResource(R.string.gs_hh_tab_system),
                stringResource(R.string.gs_hh_tab_defaults),
                stringResource(R.string.gs_hh_tab_look)
            ), tab, accent, { tab = it }
        )
        Spacer(Modifier.height(12.dp))
        MechaSlideSwitch(tab) { page ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (page) {
                    0 -> {
                        val statusText = when {
                            !enabled -> stringResource(R.string.gs_status_off)
                            live.second != null -> stringResource(R.string.gs_status_playing, UsageStatsHelper.appLabel(ctx, live.second!!))
                            live.first -> stringResource(R.string.gs_status_watching)
                            else -> stringResource(R.string.gs_status_starting)
                        }
                        MechaToggleTile(
                            Icons.Rounded.SportsEsports, stringResource(R.string.gs_master_title), statusText,
                            enabled, accent, Modifier.fillMaxWidth(), onEnabled
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MechaButton(
                                stringResource(if (live.third) R.string.gs_preview_close else R.string.gs_preview_open),
                                Icons.Rounded.PlayArrow, accent, Modifier.weight(1f)
                            ) {
                                if (live.third) GameBarService.stop(ctx)
                                else if (Settings.canDrawOverlays(ctx)) GameBarService.start(ctx)
                                else openOverlaySettings(ctx)
                            }
                            MechaButton(stringResource(R.string.gs_hh_switch_explore), Icons.Rounded.Explore, accent, Modifier.weight(1f), filled = false) {
                                close(); onSwitchToExplore()
                            }
                        }
                        MechaSectionLabel(stringResource(R.string.gs_perm_title), accent)
                        val grant = stringResource(R.string.gs_hh_grant)
                        MechaStatusRow(stringResource(R.string.gs_perm_overlay), stringResource(R.string.gs_perm_overlay_desc), perms.overlay, accent, grant) { openOverlaySettings(ctx) }
                        MechaStatusRow(stringResource(R.string.gs_perm_usage), stringResource(R.string.gs_perm_usage_desc), perms.usage, accent, grant) { UsageStatsHelper.openAccessSettings(ctx) }
                        MechaStatusRow(stringResource(R.string.gs_perm_dnd), stringResource(R.string.gs_perm_dnd_desc), perms.dnd, accent, grant) {
                            runCatching { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                        MechaStatusRow(stringResource(R.string.gs_perm_shell, perms.modeLabel), stringResource(R.string.gs_perm_shell_desc), perms.shell, accent, grant, onClick = null)
                        Spacer(Modifier.height(4.dp))
                    }
                    1 -> {
                        Text(
                            stringResource(R.string.gs_defaults_desc), color = Color.White.copy(alpha = 0.55f),
                            fontSize = 11.sp, lineHeight = 14.sp
                        )
                        MechaSettingsEditor(global, accent, onGlobalChange)
                        Spacer(Modifier.height(4.dp))
                    }
                    else -> {
                        MechaSectionLabel(stringResource(R.string.gs_look_style), accent)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MechaChip(stringResource(R.string.gs_style_cube), style == 0, accent) { style = 0; GameSpacePrefs.setPanelStyle(ctx, 0) }
                            MechaChip(stringResource(R.string.gs_style_compact), style == 1, accent) { style = 1; GameSpacePrefs.setPanelStyle(ctx, 1) }
                        }
                        MechaSectionLabel(stringResource(R.string.gs_look_edge), accent)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MechaChip(stringResource(R.string.gs_edge_left), edge == PanelEdge.LEFT, accent) { edge = PanelEdge.LEFT; GameSpacePrefs.setEdge(ctx, edge) }
                            MechaChip(stringResource(R.string.gs_edge_right), edge == PanelEdge.RIGHT, accent) { edge = PanelEdge.RIGHT; GameSpacePrefs.setEdge(ctx, edge) }
                        }
                        MechaSectionLabel(stringResource(R.string.gs_look_accent), accent)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            GameAccentPresets.forEach { c ->
                                val on = c == accentLong
                                val sc by animateFloatAsState(if (on) 1.18f else 1f, spring(dampingRatio = 0.35f, stiffness = 420f), label = "swatch")
                                Box(
                                    Modifier
                                        .scale(sc)
                                        .size(44.dp, 36.dp)
                                        .mechaClick(0.88f) { onAccentChange(c) }
                                        .clip(MechaSlant)
                                        .background(Color(c))
                                        .border(if (on) 2.5.dp else 1.dp, if (on) Color.White else Color.White.copy(alpha = 0.2f), MechaSlant)
                                )
                            }
                        }
                        MechaSectionLabel(stringResource(R.string.gs_look_autohide), accent)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0, 3, 6, 10).forEach { sec ->
                                MechaChip(if (sec == 0) stringResource(R.string.gs_never) else "${sec}s", autoHide == sec, accent) {
                                    autoHide = sec; GameSpacePrefs.setAutoHideSeconds(ctx, sec)
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}
