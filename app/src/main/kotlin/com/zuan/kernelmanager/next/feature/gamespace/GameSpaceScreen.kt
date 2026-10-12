/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.zuan.kernelmanager.next.feature.gamespace

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.StayCurrentLandscape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.ui.navigation.NavigationRoute
import com.zuan.kernelmanager.next.feature.home.menu.AppIcon
import com.zuan.kernelmanager.next.feature.home.menu.UsageCard
import com.zuan.kernelmanager.next.feature.home.menu.UsageScreenScaffold
import com.zuan.kernelmanager.next.feature.home.menu.formatUsageDuration
import com.zuan.kernelmanager.next.feature.home.menu.usageSubColor
import com.zuan.kernelmanager.next.library.device.usage.UsageStatsHelper
import com.zuan.kernelmanager.next.library.shell.PrivilegedAccess
import com.zuan.kernelmanager.next.ui.components.ZkmSlider
import com.zuan.kernelmanager.next.ui.components.ZkmSwitch
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal data class Perms(
    val overlay: Boolean,
    val usage: Boolean,
    val dnd: Boolean,
    val shell: Boolean,
    val modeLabel: String
)

internal data class AppRow(val pkg: String, val label: String, val isGame: Boolean)

/**
 * Pintu masuk Game Space.
 *  - Explore  : layar setelan biasa, rotasi bebas (9:16 maupun 16:9).
 *  - Handheld : app dikunci 16:9 landscape dan menampilkan konsol Game Space layar penuh;
 *               setelan tetap ada lewat tombol gerigi di konsol.
 */
@Composable
fun GameSpaceScreen(navController: NavController, hazeState: HazeState) {
    val ctx = LocalContext.current
    var screenMode by remember { mutableIntStateOf(GameSpacePrefs.screenMode(ctx)) }

    // Handheld = konsol Mecha selalu tampil. Tambah game, profil game, dan setelan semuanya
    // dibuka sebagai dialog DI DALAM konsol (lihat HandheldDialogs.kt) - tidak pernah keluar ke Explore.
    val handheld = screenMode == GameSpacePrefs.SCREEN_HANDHELD
    val consoleVisible = handheld

    // Handheld mengunci landscape selama layar ini terbuka; Explore / keluar layar = rotasi bebas lagi.
    // PENTING: aktivitas ini dibuat ulang saat rotasi (tanpa configChanges). Dulu onDispose SELALU mengembalikan
    // orientasi ke UNSPECIFIED - termasuk saat aktivitas lama dihancurkan karena rotasi - sehingga layar
    // memantul 9:16 <-> 16:9 terus. Sekarang: set hanya bila beda, dan jangan reset saat sekadar rotasi/recreate.
    DisposableEffect(handheld) {
        val act = ctx.findActivity()
        val want = if (handheld) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (act != null && act.requestedOrientation != want) act.requestedOrientation = want
        onDispose {
            if (act != null && !act.isChangingConfigurations && !act.isDestroyed &&
                act.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ) act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Konsol layar penuh: sembunyikan status bar & navigation bar (geser dari tepi untuk memunculkan).
    val view = LocalView.current
    DisposableEffect(consoleVisible) {
        val window = ctx.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (consoleVisible && controller != null) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    if (consoleVisible) {
        BackHandler { navController.popBackStack() }
        HandheldGameSpace(
            onSwitchToExplore = {
                screenMode = GameSpacePrefs.SCREEN_EXPLORE
                GameSpacePrefs.setScreenMode(ctx, GameSpacePrefs.SCREEN_EXPLORE)
            }
        )
    } else {
        // Explore = SATU layar saja (gaya ZKM mengikuti Wallpaper Style), berisi semua fitur Handheld.
        GameSpaceSettings(
            navController = navController,
            hazeState = hazeState,
            screenMode = screenMode,
            onScreenModeChange = {
                screenMode = it
                GameSpacePrefs.setScreenMode(ctx, it)
            }
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
internal fun GameSpaceSettings(
    navController: NavController,
    hazeState: HazeState,
    screenMode: Int,
    onScreenModeChange: (Int) -> Unit
) {
    val ctx = LocalContext.current

    var enabled by remember { mutableStateOf(GameSpacePrefs.isEnabled(ctx)) }
    var games by remember { mutableStateOf(GameSpacePrefs.games(ctx)) }
    var global by remember { mutableStateOf(GameSpacePrefs.globalSettings(ctx)) }
    var edge by remember { mutableStateOf(GameSpacePrefs.edge(ctx)) }
    var accent by remember { mutableStateOf(GameSpacePrefs.accent(ctx)) }
    var autoHide by remember { mutableIntStateOf(GameSpacePrefs.autoHideSeconds(ctx)) }
    var style by remember { mutableIntStateOf(GameSpacePrefs.panelStyle(ctx)) }
    var showPicker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }
    var cpuMode by remember { mutableStateOf(CpuMode.fromOrdinal(GameSpacePrefs.cpuMode(ctx))) }
    var showCustom by remember { mutableStateOf(false) }
    val look = remember { HubLookState(ctx) }
    val xScope = androidx.compose.runtime.rememberCoroutineScope()

    // Detektor hidup/mati mengikuti saklar utama (juga memulihkan service setelah proses app dimatikan).
    LaunchedEffect(enabled) {
        if (enabled) { if (!GameDetectorService.isRunning) GameDetectorService.start(ctx) }
        else GameDetectorService.stop(ctx)
    }

    val live by produceState(initialValue = Triple(false, null as String?, false)) {
        while (true) {
            value = Triple(GameDetectorService.isRunning, GameDetectorService.currentGame, GameBarService.isRunning)
            delay(1000)
        }
    }
    val perms by produceState(initialValue = readPerms(ctx)) {
        while (true) { value = withContext(Dispatchers.IO) { readPerms(ctx) }; delay(1500) }
    }

    UsageScreenScaffold(
        title = stringResource(R.string.ext_gamespace),
        navController = navController,
        hazeState = hazeState,
        helpRoute = NavigationRoute.GameSpace.route
    ) {
        // ------------------------------- status ------------------------------------------------
        UsageCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Color(accent).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.SportsEsports, null, tint = Color(accent), modifier = Modifier.size(26.dp)) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.gs_master_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val statusText = when {
                        !enabled -> stringResource(R.string.gs_status_off)
                        live.second != null -> stringResource(R.string.gs_status_playing, UsageStatsHelper.appLabel(ctx, live.second!!))
                        live.first -> stringResource(R.string.gs_status_watching)
                        else -> stringResource(R.string.gs_status_starting)
                    }
                    Text(statusText, style = MaterialTheme.typography.bodySmall, color = usageSubColor())
                }
                ZkmSwitch(checked = enabled, onCheckedChange = {
                    enabled = it
                    GameSpacePrefs.setEnabled(ctx, it)
                })
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (live.third) GameBarService.stop(ctx)
                        else if (Settings.canDrawOverlays(ctx)) GameBarService.start(ctx)
                        else openOverlaySettings(ctx)
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(if (live.third) R.string.gs_preview_close else R.string.gs_preview_open)) }
            }
        }

        // ------------------------------- mode layar --------------------------------------------
        UsageCard {
            Text(stringResource(R.string.gs_screen_mode_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.gs_screen_mode_desc), style = MaterialTheme.typography.bodySmall, color = usageSubColor())
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ScreenModeOption(
                    icon = Icons.Rounded.Explore,
                    title = stringResource(R.string.gs_mode_explore),
                    desc = stringResource(R.string.gs_mode_explore_desc),
                    selected = screenMode == GameSpacePrefs.SCREEN_EXPLORE,
                    accent = accent,
                    modifier = Modifier.weight(1f)
                ) { onScreenModeChange(GameSpacePrefs.SCREEN_EXPLORE) }
                ScreenModeOption(
                    icon = Icons.Rounded.StayCurrentLandscape,
                    title = stringResource(R.string.gs_mode_handheld),
                    desc = stringResource(R.string.gs_mode_handheld_desc),
                    selected = screenMode == GameSpacePrefs.SCREEN_HANDHELD,
                    accent = accent,
                    modifier = Modifier.weight(1f)
                ) { onScreenModeChange(GameSpacePrefs.SCREEN_HANDHELD) }
            }
        }

        // ------------------------------- izin --------------------------------------------------
        UsageCard {
            Text(stringResource(R.string.gs_perm_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            PermRow(stringResource(R.string.gs_perm_overlay), stringResource(R.string.gs_perm_overlay_desc), perms.overlay) { openOverlaySettings(ctx) }
            PermRow(stringResource(R.string.gs_perm_usage), stringResource(R.string.gs_perm_usage_desc), perms.usage) { UsageStatsHelper.openAccessSettings(ctx) }
            PermRow(stringResource(R.string.gs_perm_dnd), stringResource(R.string.gs_perm_dnd_desc), perms.dnd) {
                runCatching { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PermRow(
                stringResource(R.string.gs_perm_shell, perms.modeLabel),
                stringResource(R.string.gs_perm_shell_desc),
                perms.shell,
                onClick = null
            )
        }

        // ------------------------------- game saya ---------------------------------------------
        UsageCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.gs_games_title), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = { showPicker = true }) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.gs_add_game))
                }
            }
            if (games.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.gs_games_empty), style = MaterialTheme.typography.bodyMedium, color = usageSubColor())
            }
            games.forEach { pkg ->
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { editing = pkg }
                ) {
                    AppIcon(pkg, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(UsageStatsHelper.appLabel(ctx, pkg), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val pt = GameSpacePrefs.playtimeMs(ctx, pkg)
                        val sub = buildString {
                            append(if (GameSpacePrefs.hasOverride(ctx, pkg)) ctx.getString(R.string.gs_custom_profile) else ctx.getString(R.string.gs_default_profile))
                            if (pt > 0) append(" • ").append(formatUsageDuration(pt))
                        }
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = usageSubColor(), maxLines = 1)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = usageSubColor())
                }
            }
        }

        // ------------------------------- fitur Handheld (versi ZKM) ----------------------------
        ExploreGameModeCards(
            cpuMode = cpuMode,
            onCpuMode = { m ->
                cpuMode = m
                GameSpacePrefs.setCpuMode(ctx, m.ordinal)
                xScope.launch(Dispatchers.IO) { CpuModeEngine.apply(ctx, m) }
            },
            onCustom = { showCustom = true }
        )
        ExplorePluginsCard(global) { global = it; GameSpacePrefs.setGlobalSettings(ctx, it) }
        ExploreCenterCards(games, global, { global = it; GameSpacePrefs.setGlobalSettings(ctx, it) }, look)
        if (RootBoost.rootAvailable()) ExploreRootToolsCard() else ExploreShizukuToolsCard()

        // ------------------------------- default global ----------------------------------------
        UsageCard {
            Text(stringResource(R.string.gs_defaults_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.gs_defaults_desc), style = MaterialTheme.typography.bodySmall, color = usageSubColor())
            Spacer(Modifier.height(8.dp))
            SettingsEditor(global, accent) { global = it; GameSpacePrefs.setGlobalSettings(ctx, it) }
        }

        // ------------------------------- tampilan ----------------------------------------------
        UsageCard {
            Text(stringResource(R.string.gs_look_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.gs_look_style), style = MaterialTheme.typography.labelLarge, color = usageSubColor())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                FilterChip(style == 0, { style = 0; GameSpacePrefs.setPanelStyle(ctx, 0) }, { Text(stringResource(R.string.gs_style_cube)) })
                FilterChip(style == 1, { style = 1; GameSpacePrefs.setPanelStyle(ctx, 1) }, { Text(stringResource(R.string.gs_style_compact)) })
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.gs_look_edge), style = MaterialTheme.typography.labelLarge, color = usageSubColor())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                FilterChip(edge == PanelEdge.LEFT, { edge = PanelEdge.LEFT; GameSpacePrefs.setEdge(ctx, edge) }, { Text(stringResource(R.string.gs_edge_left)) })
                FilterChip(edge == PanelEdge.RIGHT, { edge = PanelEdge.RIGHT; GameSpacePrefs.setEdge(ctx, edge) }, { Text(stringResource(R.string.gs_edge_right)) })
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.gs_look_accent), style = MaterialTheme.typography.labelLarge, color = usageSubColor())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                GameAccentPresets.forEach { c ->
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                            .then(if (c == accent) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                            .clickable { accent = c; GameSpacePrefs.setAccent(ctx, c) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.gs_look_autohide), style = MaterialTheme.typography.labelLarge, color = usageSubColor())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                listOf(0, 3, 6, 10).forEach { s ->
                    FilterChip(
                        selected = autoHide == s,
                        onClick = { autoHide = s; GameSpacePrefs.setAutoHideSeconds(ctx, s) },
                        label = { Text(if (s == 0) stringResource(R.string.gs_never) else "${s}s") }
                    )
                }
            }
        }
    }

    if (showCustom) {
        ExploreCustomModeSheet(onDismiss = {
            cpuMode = CpuMode.fromOrdinal(GameSpacePrefs.cpuMode(ctx)); showCustom = false
        })
    }

    if (showPicker) {
        GamePickerSheet(
            existing = games,
            onDismiss = { showPicker = false },
            onPick = {
                GameSpacePrefs.addGame(ctx, it); games = GameSpacePrefs.games(ctx); showPicker = false
            }
        )
    }

    editing?.let { pkg ->
        GameProfileSheet(
            pkg = pkg,
            global = global,
            accent = accent,
            onDismiss = { editing = null },
            onRemove = {
                GameSpacePrefs.removeGame(ctx, pkg); games = GameSpacePrefs.games(ctx); editing = null
            }
        )
    }
}

// ================================ potongan UI ================================================

internal fun readPerms(c: Context): Perms {
    val nm = c.getSystemService(NotificationManager::class.java)
    return Perms(
        overlay = Settings.canDrawOverlays(c),
        usage = UsageStatsHelper.hasAccess(c),
        dnd = nm.isNotificationPolicyAccessGranted,
        shell = PrivilegedAccess.shellUsable(),
        modeLabel = PrivilegedAccess.label()
    )
}

internal fun openOverlaySettings(c: Context) {
    runCatching {
        c.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${c.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
private fun ScreenModeOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String,
    selected: Boolean,
    accent: Long,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val c = Color(accent)
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) c.copy(alpha = 0.16f) else Color.Transparent)
            .border(if (selected) 2.dp else 1.dp, if (selected) c else usageSubColor().copy(alpha = 0.3f), shape)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Icon(icon, null, tint = if (selected) c else usageSubColor(), modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, fontWeight = FontWeight.Bold)
        Text(desc, style = MaterialTheme.typography.bodySmall, color = usageSubColor())
    }
}

@Composable
private fun PermRow(title: String, desc: String, ok: Boolean, onClick: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Icon(
            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, null,
            tint = if (ok) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = usageSubColor())
        }
        if (!ok && onClick != null) TextButton(onClick = onClick) { Text(stringResource(R.string.gs_grant)) }
    }
}

@Composable
internal fun ToggleRow(title: String, desc: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            if (desc != null) Text(desc, style = MaterialTheme.typography.bodySmall, color = usageSubColor())
        }
        Spacer(Modifier.width(8.dp))
        ZkmSwitch(checked = checked, onCheckedChange = onChange)
    }
}

/** Editor setelan: dipakai untuk default global dan override per game. */
@Composable
private fun SettingsEditor(s: GameSettings, accent: Long, onChange: (GameSettings) -> Unit) {
    ToggleRow(stringResource(R.string.gs_opt_panel), stringResource(R.string.gs_opt_panel_desc), s.showPanel) { onChange(s.copy(showPanel = it)) }
    ToggleRow(stringResource(R.string.gs_opt_dnd), stringResource(R.string.gs_opt_dnd_desc), s.dnd) { onChange(s.copy(dnd = it)) }
    ToggleRow(stringResource(R.string.gs_opt_heads), stringResource(R.string.gs_opt_heads_desc), s.blockHeadsUp) { onChange(s.copy(blockHeadsUp = it)) }
    ToggleRow(stringResource(R.string.gs_opt_bright), stringResource(R.string.gs_opt_bright_desc), s.lockBrightnessMode) { onChange(s.copy(lockBrightnessMode = it)) }
    ToggleRow(stringResource(R.string.gs_opt_perf), stringResource(R.string.gs_opt_perf_desc), s.performanceMode) { onChange(s.copy(performanceMode = it)) }
    ToggleRow(stringResource(R.string.gs_opt_ram), stringResource(R.string.gs_opt_ram_desc), s.clearRamOnLaunch) { onChange(s.copy(clearRamOnLaunch = it)) }

    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.gs_opt_hz), fontWeight = FontWeight.Medium)
    Text(stringResource(R.string.gs_opt_hz_desc), style = MaterialTheme.typography.bodySmall, color = usageSubColor())
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
        listOf(0, 60, 90, 120, 144).forEach { hz ->
            FilterChip(
                selected = s.refreshRateHz == hz,
                onClick = { onChange(s.copy(refreshRateHz = hz)) },
                label = { Text(if (hz == 0) stringResource(R.string.gs_hz_auto) else "$hz Hz") }
            )
        }
    }

    Spacer(Modifier.height(8.dp))
    ToggleRow(stringResource(R.string.gs_opt_cross), stringResource(R.string.gs_opt_cross_desc), s.crosshair) { onChange(s.copy(crosshair = it)) }
    if (s.crosshair) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            repeat(CrosshairService.STYLE_COUNT) { i ->
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .then(if (i == s.crosshairStyle) Modifier.border(2.dp, Color(accent), RoundedCornerShape(14.dp)) else Modifier)
                        .clickable { onChange(s.copy(crosshairStyle = i)) },
                    contentAlignment = Alignment.Center
                ) { CrosshairView(i, 26, Color(accent)) }
            }
        }
        Text("${stringResource(R.string.gs_cross_size)}: ${s.crosshairSizeDp}dp", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        ZkmSlider(
            value = s.crosshairSizeDp.toFloat(),
            onValueChange = { onChange(s.copy(crosshairSizeDp = it.roundToInt())) },
            valueRange = 16f..72f
        )
        Text("${stringResource(R.string.gs_cross_opacity)}: ${s.crosshairOpacity}%", style = MaterialTheme.typography.labelLarge)
        ZkmSlider(
            value = s.crosshairOpacity.toFloat(),
            onValueChange = { onChange(s.copy(crosshairOpacity = it.roundToInt())) },
            valueRange = 20f..100f
        )
    }
}

@Composable
internal fun GamePickerSheet(existing: List<String>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    val apps by produceState(initialValue = emptyList<AppRow>()) {
        value = withContext(Dispatchers.IO) { loadLaunchableApps(ctx) }
    }
    val filtered = remember(apps, query, existing) {
        apps.filter { it.pkg !in existing && (query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true)) }
    }
    ModalBottomSheet(
        contentWindowInsets = { com.zuan.kernelmanager.next.ui.adaptive.zkmSheetWindowInsets() },
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.gs_add_game), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text(stringResource(R.string.gs_search)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 480.dp)) {
                items(filtered, key = { it.pkg }) { row ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(row.pkg) }.padding(vertical = 8.dp)
                    ) {
                        AppIcon(row.pkg, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(row.label, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(row.pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (row.isGame) Text(stringResource(R.string.gs_tag_game), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun GameProfileSheet(pkg: String, global: GameSettings, accent: Long, onDismiss: () -> Unit, onRemove: () -> Unit) {
    val ctx = LocalContext.current
    var custom by remember { mutableStateOf(GameSpacePrefs.hasOverride(ctx, pkg)) }
    var s by remember { mutableStateOf(GameSpacePrefs.settingsFor(ctx, pkg)) }
    ModalBottomSheet(
        contentWindowInsets = { com.zuan.kernelmanager.next.ui.adaptive.zkmSheetWindowInsets() },
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).then(Modifier)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(pkg, 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(UsageStatsHelper.appLabel(ctx, pkg), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                IconButton(onClick = onRemove) { Icon(Icons.Rounded.Delete, stringResource(R.string.gs_remove)) }
            }
            Spacer(Modifier.height(8.dp))
            ToggleRow(stringResource(R.string.gs_use_custom), stringResource(R.string.gs_use_custom_desc), custom) { on ->
                custom = on
                if (on) { GameSpacePrefs.setOverride(ctx, pkg, s) } else { GameSpacePrefs.setOverride(ctx, pkg, null); s = global }
            }
            if (custom) {
                Column(Modifier.fillMaxWidth()) {
                    SettingsEditor(s, accent) { s = it; GameSpacePrefs.setOverride(ctx, pkg, it) }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

internal fun loadLaunchableApps(c: Context): List<AppRow> {
    val pm = c.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return runCatching {
        pm.queryIntentActivities(intent, 0).mapNotNull { ri ->
            val ai = ri.activityInfo?.applicationInfo ?: return@mapNotNull null
            if (ai.packageName == c.packageName) return@mapNotNull null
            val isGame = ai.category == ApplicationInfo.CATEGORY_GAME || (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            AppRow(ai.packageName, ri.loadLabel(pm).toString(), isGame)
        }.distinctBy { it.pkg }.sortedWith(compareByDescending<AppRow> { it.isGame }.thenBy { it.label.lowercase() })
    }.getOrDefault(emptyList())
}
