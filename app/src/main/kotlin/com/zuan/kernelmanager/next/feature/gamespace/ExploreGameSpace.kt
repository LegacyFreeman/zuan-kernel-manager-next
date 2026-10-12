/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 *
 * Game Space - mode EXPLORE (portrait 9:16, gaya aplikasi ZKM).
 *
 * Punya SEMUA fitur konsol Handheld: lobby + START, daftar game, profil game, mode CPU (Balance/Dynamic/Performance),
 * Control Panel (Game Mode / GPU / Network / Functions), Center (Playtime / Cooling / Network / General / Atmosphere / Help),
 * Plugin Library, Custom Mode (per cluster CPU), Root Tools, dan pengaturan.
 * Bedanya: tata letak vertikal + komponen Material3 ZKM; halaman isi dipakai ULANG dari Handheld (satu sumber kebenaran),
 * jadi fitur baru di Handheld otomatis ikut muncul di sini.
 */
package com.zuan.kernelmanager.next.feature.gamespace

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.StayCurrentLandscape
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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
import com.zuan.kernelmanager.next.core.util.ZkmSamplerBus
import kotlin.math.roundToInt

private enum class XSection(val titleRes: Int) {
    MODE(R.string.gsx_cp_mode), GPU(R.string.gsx_cp_gpu), NET(R.string.gsx_cp_net), FUNC(R.string.gsx_cp_func),
    PLAYTIME(R.string.gsx_ct_playtime), COOLING(R.string.gsx_ct_cooling), NETWORK(R.string.gsx_ct_network),
    GENERAL(R.string.gsx_ct_general), ATMOS(R.string.gsx_ct_atmos), HELP(R.string.gsx_ct_help)
}

private sealed interface XDialog {
    data object None : XDialog
    data object Picker : XDialog
    data class Profile(val pkg: String) : XDialog
    data object Plugins : XDialog
    data object Custom : XDialog
    data object Root : XDialog
}

@Composable
fun ExploreGameSpace(
    onOpenSettings: () -> Unit,
    onSwitchToHandheld: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var accentLong by remember { mutableLongStateOf(GameSpacePrefs.accent(ctx)) }
    val accent by animateColorAsState(Color(accentLong), tween(400), label = "xAccent")
    var dialog by remember { mutableStateOf<XDialog>(XDialog.None) }
    var gamesTick by remember { mutableIntStateOf(0) }
    val games by produceState(initialValue = GameSpacePrefs.games(ctx), gamesTick) {
        ZkmSamplerBus.slowTick.collect { value = GameSpacePrefs.games(ctx) }
    }
    var global by remember { mutableStateOf(GameSpacePrefs.globalSettings(ctx)) }
    var cpuMode by remember { mutableStateOf(CpuMode.fromOrdinal(GameSpacePrefs.cpuMode(ctx))) }
    val look = remember { HubLookState(ctx) }
    var selected by remember { mutableIntStateOf(0) }
    val sel = if (games.isEmpty()) -1 else selected.coerceIn(0, games.lastIndex)
    val selPkg: String? = games.getOrNull(sel)
    var section by remember { mutableStateOf(XSection.MODE) }
    var enabled by remember { mutableStateOf(GameSpacePrefs.isEnabled(ctx)) }

    val t by produceState(initialValue = readTelemetry(ctx)) {
        ZkmSamplerBus.fastTick.collect {
            value = withContext(Dispatchers.IO) { readTelemetry(ctx) }
        }
    }
    LaunchedEffect(enabled) {
        if (enabled) { if (!GameDetectorService.isRunning) GameDetectorService.start(ctx) }
        else GameDetectorService.stop(ctx)
    }

    fun updateGlobal(s: GameSettings) { global = s; GameSpacePrefs.setGlobalSettings(ctx, s) }
    fun setCpu(m: CpuMode) {
        cpuMode = m
        GameSpacePrefs.setCpuMode(ctx, m.ordinal)
        scope.launch(Dispatchers.IO) { CpuModeEngine.apply(ctx, m) }
    }
    fun launchGame(pkg: String) {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        else Toast.makeText(ctx, R.string.gs_hh_cannot_launch, Toast.LENGTH_SHORT).show()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ---------------- header ----------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ext_gamespace), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${t.battery}%  ·  ${t.tempC.roundToInt()}°C  ·  RAM ${t.ramPct}%",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
            IconButton(onClick = onSwitchToHandheld) { Icon(Icons.Rounded.StayCurrentLandscape, stringResource(R.string.gs_mode_handheld)) }
            IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, null) }
        }

        // ---------------- game ----------------
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(games.size) { i ->
                val pkg = games[i]
                val on = i == sel
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (on) accent.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(if (on) 2.dp else 0.dp, if (on) accent else Color.Transparent, RoundedCornerShape(20.dp))
                        .clickable { selected = i },
                    contentAlignment = Alignment.Center
                ) { AppIcon(pkg, 44.dp) }
            }
            item {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                        .clickable { dialog = XDialog.Picker },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Add, stringResource(R.string.gs_add_game)) }
            }
        }

        // ---------------- hero: game terpilih ----------------
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.34f), MaterialTheme.colorScheme.surfaceContainerHigh)))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (selPkg == null) {
                Text(stringResource(R.string.gs_games_empty), style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(onClick = { dialog = XDialog.Picker }) { Text(stringResource(R.string.gs_add_game)) }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(selPkg, 52.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            UsageStatsHelper.appLabel(ctx, selPkg), style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        val pt = GameSpacePrefs.playtimeMs(ctx, selPkg)
                        Text(
                            if (pt > 0) formatUsageDuration(pt) else stringResource(R.string.gs_default_profile),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { dialog = XDialog.Profile(selPkg) }) { Icon(Icons.Rounded.Tune, null) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = { launchGame(selPkg) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.gs_hh_start), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ---------------- mode CPU ----------------
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            CpuMode.entries.forEach { m ->
                val on = m == cpuMode
                Box(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (on) m.color.copy(alpha = 0.26f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(if (on) 1.5.dp else 0.dp, if (on) m.color else Color.Transparent, RoundedCornerShape(16.dp))
                        .clickable { setCpu(m) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(m.labelRes), fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        color = if (on) m.color else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // ---------------- pintasan: Plugin / Custom Mode / Root Tools ----------------
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { dialog = XDialog.Plugins }, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, accent.copy(alpha = 0.6f))) {
                Icon(Icons.Rounded.Extension, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.gsx_pl_title), maxLines = 1, fontSize = 12.sp)
            }
            OutlinedButton(onClick = { dialog = XDialog.Custom }, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, accent.copy(alpha = 0.6f))) {
                Icon(Icons.Rounded.Tune, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.gsx_cm_title), maxLines = 1, fontSize = 12.sp)
            }
            OutlinedButton(onClick = { dialog = XDialog.Root }, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, accent.copy(alpha = 0.6f))) {
                Icon(Icons.Rounded.Bolt, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.gsx_rt_title), maxLines = 1, fontSize = 12.sp)
            }
        }

        // ---------------- bagian: Control Panel + Center ----------------
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            XSection.entries.forEach { s ->
                FilterChip(selected = s == section, onClick = { section = s }, label = { Text(stringResource(s.titleRes)) })
            }
        }
        // Halaman dipakai ulang dari Handheld (tema gelap Mecha) -> dibungkus kartu gelap supaya terbaca di tema terang juga.
        val pageHeight = (LocalConfiguration.current.screenHeightDp * 0.62f).dp
        Box(
            Modifier
                .fillMaxWidth()
                .height(pageHeight)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF0C0E16))
                .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
                .padding(14.dp)
        ) {
            when (section) {
                XSection.MODE -> ModePage(accent, cpuMode, ::setCpu, onCustomize = { dialog = XDialog.Custom })
                XSection.GPU -> GpuPage(accent, selPkg)
                XSection.NET -> NetPage(accent, selPkg)
                XSection.FUNC -> FuncPage(accent, selPkg)
                XSection.PLAYTIME -> PlaytimePage(games, accent)
                XSection.COOLING -> CoolingPage(accent)
                XSection.NETWORK -> NetworkPage(accent)
                XSection.GENERAL -> GeneralPage(accent, global, ::updateGlobal)
                XSection.ATMOS -> AtmospherePage(accent, look)
                XSection.HELP -> HelpPage(accent)
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    when (val d = dialog) {
        XDialog.None -> {}
        XDialog.Picker -> GamePickerSheet(
            existing = games, onDismiss = { dialog = XDialog.None },
            onPick = { GameSpacePrefs.addGame(ctx, it); gamesTick++; selected = GameSpacePrefs.games(ctx).indexOf(it).coerceAtLeast(0); dialog = XDialog.None }
        )
        is XDialog.Profile -> GameProfileSheet(
            pkg = d.pkg, global = global, accent = accentLong,
            onDismiss = { dialog = XDialog.None },
            onRemove = { GameSpacePrefs.removeGame(ctx, d.pkg); gamesTick++; dialog = XDialog.None }
        )
        XDialog.Plugins -> HandheldPluginLibrary(
            accent = accent, global = global, onGlobalChange = ::updateGlobal, onDismiss = { dialog = XDialog.None }
        )
        XDialog.Custom -> MechaDialog(
            accent = accent, title = stringResource(R.string.gsx_cm_title), subtitle = stringResource(R.string.gsx_cm_sub),
            icon = Icons.Rounded.Tune,
            onDismiss = { cpuMode = CpuMode.fromOrdinal(GameSpacePrefs.cpuMode(ctx)); dialog = XDialog.None },
            widthFraction = 0.96f, heightFraction = 0.9f
        ) { _ -> CustomModeContent(accent) }
        XDialog.Root -> MechaDialog(
            accent = accent, title = stringResource(R.string.gsx_rt_title), subtitle = stringResource(R.string.gsx_rt_sub),
            icon = Icons.Rounded.Bolt, onDismiss = { dialog = XDialog.None },
            widthFraction = 0.96f, heightFraction = 0.9f
        ) { _ -> RootToolsContent(accent) }
    }
}
