/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 *
 * Game Space - mode EXPLORE: semua fitur Handheld, dibangun dari komponen ZKM murni
 * (UsageCard, ToggleRow, FilterChip, Slider Material3). Warna/kaca/blur/background mengikuti
 * Wallpaper Style screen, jadi tidak ada gaya Mecha di sini. Logika & penyimpanan memakai sumber
 * yang SAMA dengan Handheld (GameHubPrefs, CustomModeStore, RootBoost, CpuModeEngine).
 */
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.zuan.kernelmanager.next.feature.gamespace

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.feature.home.menu.UsageCard
import com.zuan.kernelmanager.next.feature.home.menu.formatUsageDuration
import com.zuan.kernelmanager.next.feature.home.menu.usageSubColor
import com.zuan.kernelmanager.next.ui.adaptive.zkmSheetWindowInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// ============================== kartu yang bisa dibuka-tutup ==================================

@Composable
internal fun XCard(title: String, subtitle: String? = null, startOpen: Boolean = false, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(startOpen) }
    UsageCard {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = usageSubColor())
            }
            Icon(Icons.Rounded.ExpandMore, null, tint = usageSubColor(), modifier = Modifier.rotate(if (open) 180f else 0f))
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
        }
    }
}

@Composable
private fun XLabel(text: String) =
    Text(text, style = MaterialTheme.typography.labelLarge, color = usageSubColor(), modifier = Modifier.padding(top = 6.dp))

@Composable
private fun XNote(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = usageSubColor())

@Composable
private fun XChoice(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { i, o -> FilterChip(i == selected, { onSelect(i) }, { Text(o) }) }
        }
    }
}

@Composable
private fun XPct(title: String, value: Int, min: Int, refMhz: Int?, onChange: (Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            val mhz = if (refMhz != null && refMhz > 0) "  ≈ ${refMhz * value / 100} MHz" else ""
            Text("$value%$mhz", style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value.toFloat(), { onChange(((it / 5f).roundToInt() * 5).coerceIn(min, 100)) }, valueRange = 0f..100f)
    }
}

// ============================== Game Mode + Control Panel =====================================

@Composable
internal fun ExploreGameModeCards(cpuMode: CpuMode, onCpuMode: (CpuMode) -> Unit, onCustom: () -> Unit) {
    val ctx = LocalContext.current
    val root = RootBoost.rootAvailable()
    XCard(stringResource(R.string.gsx_cp_mode), stringResource(cpuMode.labelRes), startOpen = true) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CpuMode.entries.forEach { m -> FilterChip(m == cpuMode, { onCpuMode(m) }, { Text(stringResource(m.labelRes)) }) }
        }
        XNote(
            stringResource(
                when (cpuMode) {
                    CpuMode.BALANCE -> R.string.gsx_md_balance
                    CpuMode.DYNAMIC -> R.string.gsx_md_dynamic
                    CpuMode.PERFORMANCE -> R.string.gsx_md_performance
                }
            )
        )
        if (!com.zuan.kernelmanager.next.library.shell.PrivilegedAccess.rootEquivalent()) XNote(stringResource(R.string.gsx_nr_mode_note))
        OutlinedButton(
            onClick = onCustom,
            modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = if (root) 1f else 0.55f }
        ) {
            if (!root) {
                Icon(Icons.Rounded.Lock, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                stringResource(R.string.gsx_cp_customize) +
                    if (!root) " (${stringResource(R.string.gsx_nr_rootonly_title)})" else ""
            )
        }
    }

    XCard(stringResource(R.string.gsx_cp_gpu)) {
        val boost = rememberHubInt(GameHubPrefs.game(null, "gpu_boost"), 0)
        if (!root) {
            XNote(stringResource(R.string.gsx_nr_rootonly_body))
        }
        Box(modifier = Modifier.graphicsLayer { alpha = if (root) 1f else 0.45f }) {
            XChoice(
                stringResource(R.string.gsx_gpu_boost),
                listOf(stringResource(R.string.gsx_gpu_off), stringResource(R.string.gsx_gpu_b_mid), stringResource(R.string.gsx_gpu_b_max)),
                boost.value
            ) { if (root) boost.value = it }
        }
        XNote(stringResource(R.string.gsx_gpu_note))
    }

    XCard(stringResource(R.string.gsx_cp_net)) {
        val dual = rememberHubBool(GameHubPrefs.game(null, "net_dual"), false)
        val prio = rememberHubBool(GameHubPrefs.game(null, "net_prio"), false)
        ToggleRow(stringResource(R.string.gsx_net_dual), stringResource(R.string.gsx_net_dual_desc), dual.value) { dual.value = it }
        ToggleRow(stringResource(R.string.gsx_net_prio), stringResource(R.string.gsx_net_prio_desc), prio.value) { prio.value = it }
        XNote(stringResource(R.string.gsx_net_note))
    }

    XCard(stringResource(R.string.gsx_cp_func)) {
        val quality = rememberHubInt(GameHubPrefs.game(null, "fn_quality"), 1)
        val scCross = rememberHubBool(GameHubPrefs.game(null, "fn_sc_cross"), true)
        val scRam = rememberHubBool(GameHubPrefs.game(null, "fn_sc_ram"), true)
        val scCpu = rememberHubBool(GameHubPrefs.game(null, "fn_sc_cpu"), true)
        val scRec = rememberHubBool(GameHubPrefs.game(null, "fn_sc_rec"), true)
        XChoice(stringResource(R.string.gsx_fn_quality), listOf("SD", "HD"), quality.value) { quality.value = it }
        XLabel(stringResource(R.string.gsx_fn_shortcuts))
        ToggleRow(stringResource(R.string.gsx_fn_sc_cross), null, scCross.value) { scCross.value = it }
        ToggleRow(stringResource(R.string.gsx_fn_sc_ram), null, scRam.value) { scRam.value = it }
        ToggleRow(stringResource(R.string.gsx_fn_sc_cpu), null, scCpu.value) { scCpu.value = it }
        ToggleRow(stringResource(R.string.gsx_fn_sc_rec), null, scRec.value) { scRec.value = it }
        XNote(stringResource(R.string.gsx_fn_note))
    }
    // ctx dipakai supaya import tidak dianggap sia-sia bila fitur ditambah nanti
    @Suppress("UNUSED_VARIABLE") val unused = ctx
}

// ============================== Center ========================================================

@Composable
internal fun ExploreCenterCards(games: List<String>, global: GameSettings, onGlobal: (GameSettings) -> Unit, look: HubLookState) {
    val ctx = LocalContext.current

    XCard(stringResource(R.string.gsx_ct_playtime)) {
        val times = games.map { it to GameSpacePrefs.playtimeMs(ctx, it) }.sortedByDescending { it.second }
        val totalMs = times.sumOf { it.second }
        val hours = (totalMs / 3_600_000L).toInt()
        var rank = 0
        for (i in RankHours.indices) if (hours >= RankHours[i]) rank = i
        val nextAt = RankHours.getOrNull(rank + 1)
        Row {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.gsx_ct_total), style = MaterialTheme.typography.labelLarge, color = usageSubColor())
                Text(formatUsageDuration(totalMs), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.gsx_ct_rank), style = MaterialTheme.typography.labelLarge, color = usageSubColor())
                Text(stringResource(RankNames[rank]).uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
        XNote(
            if (nextAt == null) stringResource(R.string.gsx_ct_max_rank)
            else stringResource(R.string.gsx_ct_to_next, (nextAt - totalMs / 3_600_000f).toInt() + 1)
        )
        if (times.isNotEmpty()) {
            XLabel(stringResource(R.string.gsx_ct_breakdown))
            times.take(5).forEach { (pkg, ms) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.zuan.kernelmanager.next.feature.home.menu.AppIcon(pkg, 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(com.zuan.kernelmanager.next.library.device.usage.UsageStatsHelper.appLabel(ctx, pkg), maxLines = 1, fontWeight = FontWeight.SemiBold)
                        LinearProgressIndicator(
                            progress = { if (totalMs > 0) (ms.toFloat() / totalMs).coerceIn(0f, 1f) else 0f },
                            modifier = Modifier.fillMaxWidth().height(4.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(formatUsageDuration(ms), style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
                }
            }
        }
        XNote(stringResource(R.string.gsx_ct_tip))
    }

    XCard(stringResource(R.string.gsx_ct_general)) {
        val hide = rememberHubBool("gen_hide_icons", false)
        val saver = rememberHubBool("gen_screen_saver", false)
        val open = rememberHubBool("gen_open_anim", true)
        val wellbeing = rememberHubBool("gen_wellbeing", false)
        val start = rememberHubBool("gen_start_anim", true)
        ToggleRow(stringResource(R.string.gsx_gen_bright), stringResource(R.string.gsx_gen_bright_desc), global.lockBrightnessMode) { onGlobal(global.copy(lockBrightnessMode = it)) }
        ToggleRow(stringResource(R.string.gsx_gen_hide), stringResource(R.string.gsx_gen_hide_desc), hide.value) { hide.value = it }
        ToggleRow(stringResource(R.string.gsx_gen_saver), stringResource(R.string.gsx_gen_saver_desc), saver.value) { saver.value = it }
        ToggleRow(stringResource(R.string.gsx_gen_open), null, open.value) { open.value = it }
        ToggleRow(stringResource(R.string.gsx_gen_wellbeing), null, wellbeing.value) { wellbeing.value = it }
        ToggleRow(stringResource(R.string.gsx_gen_start), null, start.value) { start.value = it }
        XNote(stringResource(R.string.gsx_gen_note))
    }

    XCard(stringResource(R.string.gsx_ct_cooling)) {
        Text(stringResource(R.string.gsx_cool_moved_title), fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.gsx_cool_moved_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        XNote(stringResource(R.string.gsx_cool_note))
    }

    XCard(stringResource(R.string.gsx_ct_atmos), "Handheld") {
        XChoice(
            stringResource(R.string.gsx_at_backdrop),
            listOf(stringResource(R.string.gsx_bd_reactor), stringResource(R.string.gsx_bd_grid), stringResource(R.string.gsx_bd_neon), stringResource(R.string.gsx_bd_hex)),
            look.style
        ) { look.updateStyle(it) }
        XLabel(stringResource(R.string.gsx_at_led))
        ToggleRow(stringResource(R.string.gsx_led_on), stringResource(R.string.gsx_led_on_desc), look.ledOn) { look.updateLedOn(it) }
        XChoice(
            stringResource(R.string.gsx_led_fx),
            listOf(stringResource(R.string.gsx_led_steady), stringResource(R.string.gsx_led_breathe), stringResource(R.string.gsx_led_flash), stringResource(R.string.gsx_led_flow)),
            look.ledFx
        ) { look.updateLedFx(it) }
        XChoice(
            stringResource(R.string.gsx_led_color),
            listOf(stringResource(R.string.gsx_led_auto)) + HubLedColors.indices.map { "${it + 1}" },
            look.ledColorIdx + 1
        ) { look.updateLedColor(it - 1) }
    }

    XCard(stringResource(R.string.gsx_ct_help)) {
        listOf(
            R.string.gsx_help_1_t to R.string.gsx_help_1_d,
            R.string.gsx_help_2_t to R.string.gsx_help_2_d,
            R.string.gsx_help_3_t to R.string.gsx_help_3_d,
            R.string.gsx_help_4_t to R.string.gsx_help_4_d,
            R.string.gsx_help_5_t to R.string.gsx_help_5_d,
            R.string.gsx_help_6_t to R.string.gsx_help_6_d,
            R.string.gsx_help_7_t to R.string.gsx_help_7_d
        ).forEach { (t, d) ->
            Text(stringResource(t), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            XNote(stringResource(d))
        }
    }
}

// ============================== Plugin Library ================================================

@Composable
internal fun ExplorePluginsCard(global: GameSettings, onGlobal: (GameSettings) -> Unit) {
    val ctx = LocalContext.current
    val saved = remember {
        mutableStateMapOf<String, Boolean>().apply {
            HubPlugins.forEach { put(it.id, GameHubPrefs.bool(ctx, GameHubPrefs.plugin(it.id), false)) }
        }
    }
    XCard(stringResource(R.string.gsx_pl_title), stringResource(R.string.gsx_pl_sub)) {
        HubPlugins.forEach { p ->
            val on = if (p.id == PLUGIN_AUX) global.crosshair else saved[p.id] == true
            ToggleRow(
                stringResource(p.nameRes) + if (p.ready) "" else "  · " + stringResource(R.string.gsx_tag_preview),
                stringResource(p.descRes), on
            ) { v ->
                if (p.id == PLUGIN_AUX) onGlobal(global.copy(crosshair = v))
                else { saved[p.id] = v; GameHubPrefs.setBool(ctx, GameHubPrefs.plugin(p.id), v) }
            }
        }
    }
}

// ============================== Root Tools ====================================================

@Composable
internal fun ExploreRootToolsCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val root = RootBoost.rootAvailable()
    val ram = rememberHubBool(RootBoost.RAM, true)
    val prio = rememberHubBool(RootBoost.PRIO, true)
    val sched = rememberHubBool(RootBoost.SCHED, false)
    val io = rememberHubBool(RootBoost.IO, false)
    val swap = rememberHubBool(RootBoost.SWAP, false)
    val splash = rememberHubBool(RootBoost.SPLASH, true)
    var status by remember { mutableStateOf("") }

    XCard(stringResource(R.string.gsx_rt_title), stringResource(R.string.gsx_rt_sub)) {
        ToggleRow(stringResource(R.string.gsx_rt_splash), stringResource(R.string.gsx_rt_splash_desc), splash.value) { splash.value = it }
        XLabel(stringResource(R.string.gsx_rt_section))
        if (!root) XNote(stringResource(R.string.gsx_rt_need_root))
        ToggleRow(stringResource(R.string.gsx_rt_ram), stringResource(R.string.gsx_rt_ram_d), ram.value && root) { if (root) ram.value = it }
        ToggleRow(stringResource(R.string.gsx_rt_prio), stringResource(R.string.gsx_rt_prio_d), prio.value && root) { if (root) prio.value = it }
        ToggleRow(stringResource(R.string.gsx_rt_sched), stringResource(R.string.gsx_rt_sched_d), sched.value && root) { if (root) sched.value = it }
        ToggleRow(stringResource(R.string.gsx_rt_io), stringResource(R.string.gsx_rt_io_d), io.value && root) { if (root) io.value = it }
        ToggleRow(stringResource(R.string.gsx_rt_swap), stringResource(R.string.gsx_rt_swap_d), swap.value && root) { if (root) swap.value = it }
        Button(
            enabled = root, modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    val freed = withContext(Dispatchers.IO) {
                        val am = ctx.getSystemService(android.app.ActivityManager::class.java)
                        fun avail(): Long { val mi = android.app.ActivityManager.MemoryInfo(); am?.getMemoryInfo(mi); return mi.availMem / (1024 * 1024) }
                        val before = avail()
                        if (GameSpaceEngine.clearRam(ctx)) (avail() - before).coerceAtLeast(0L) else -1L
                    }
                    status = if (freed >= 0) ctx.getString(R.string.gsx_rt_cleaned, freed.toInt()) else ctx.getString(R.string.gsx_rt_failed)
                }
            }
        ) { Text(stringResource(R.string.gsx_rt_clean_now)) }
        if (status.isNotEmpty()) Text(status, color = MaterialTheme.colorScheme.primary)
        XNote(stringResource(R.string.gsx_rt_note))
    }
}

// ============================== Shizuku Tools (no-root) =======================================

@Composable
internal fun ExploreShizukuToolsCard() {
    val ctx = LocalContext.current
    val level = NonRootBoost.level()
    val ready = level == NonRootBoost.Level.SHIZUKU_ADB || level == NonRootBoost.Level.SHIZUKU_ROOT
    val ram = rememberHubBool(RootBoost.RAM, true)
    val gm = rememberHubBool(NonRootBoost.GAMEMODE, true)
    val fixed = rememberHubBool(NonRootBoost.FIXED, false)
    val bg = rememberHubBool(NonRootBoost.BGLIMIT, false)
    val bat = rememberHubBool(NonRootBoost.BATSAVER, false)
    val splash = rememberHubBool(RootBoost.SPLASH, true)
    var keep by remember { mutableStateOf(NonRootBoost.keepList(ctx)) }
    var picking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    XCard(stringResource(R.string.gsx_nr_title), stringResource(R.string.gsx_nr_sub), startOpen = true) {
        XNote(stringResource(NonRootBoost.levelTextRes(level)))
        ToggleRow(stringResource(R.string.gsx_rt_splash), stringResource(R.string.gsx_rt_splash_desc), splash.value) { splash.value = it }
        XLabel(stringResource(R.string.gsx_rt_section))
        ToggleRow(stringResource(R.string.gsx_nr_gm), stringResource(R.string.gsx_nr_gm_d), gm.value && ready) { if (ready) gm.value = it }
        ToggleRow(stringResource(R.string.gsx_nr_ram), stringResource(R.string.gsx_nr_ram_d), ram.value && ready) { if (ready) ram.value = it }
        ToggleRow(stringResource(R.string.gsx_nr_bg), stringResource(R.string.gsx_nr_bg_d), bg.value && ready) { if (ready) bg.value = it }
        AnimatedVisibility(bg.value && ready) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                XLabel(stringResource(R.string.gsx_nr_keep))
                XNote(stringResource(R.string.gsx_nr_keep_d))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    keep.forEach { p ->
                        FilterChip(
                            true,
                            { NonRootBoost.removeKeep(ctx, p); keep = NonRootBoost.keepList(ctx) },
                            { Text(com.zuan.kernelmanager.next.library.device.usage.UsageStatsHelper.appLabel(ctx, p) + "  \u2715", maxLines = 1) }
                        )
                    }
                    OutlinedButton(onClick = { picking = true }) { Text(stringResource(R.string.gsx_nr_keep_add)) }
                }
            }
        }
        ToggleRow(stringResource(R.string.gsx_nr_bat), stringResource(R.string.gsx_nr_bat_d), bat.value && ready) { if (ready) bat.value = it }
        ToggleRow(stringResource(R.string.gsx_nr_fixed), stringResource(R.string.gsx_nr_fixed_d), fixed.value && ready) { if (ready) fixed.value = it }
        Button(
            enabled = ready, modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    val freed = withContext(Dispatchers.IO) {
                        val am = ctx.getSystemService(android.app.ActivityManager::class.java)
                        fun avail(): Long { val mi = android.app.ActivityManager.MemoryInfo(); am?.getMemoryInfo(mi); return mi.availMem / (1024 * 1024) }
                        val before = avail()
                        if (GameSpaceEngine.clearRam(ctx)) (avail() - before).coerceAtLeast(0L) else -1L
                    }
                    status = if (freed >= 0) ctx.getString(R.string.gsx_rt_cleaned, freed.toInt()) else ctx.getString(R.string.gsx_rt_failed)
                }
            }
        ) { Text(stringResource(R.string.gsx_rt_clean_now)) }
        if (status.isNotEmpty()) Text(status, color = MaterialTheme.colorScheme.primary)
        XLabel(stringResource(R.string.gsx_nr_modes_title))
        XNote(stringResource(R.string.gsx_nr_modes_body))
        XLabel(stringResource(R.string.gsx_nr_rootonly_title))
        XNote(stringResource(R.string.gsx_nr_rootonly_body))
        XNote(stringResource(R.string.gsx_nr_note))
    }

    if (picking) {
        GamePickerSheet(
            existing = keep,
            onDismiss = { picking = false },
            onPick = { NonRootBoost.addKeep(ctx, it); keep = NonRootBoost.keepList(ctx); picking = false }
        )
    }
}

// ============================== Custom Mode (sheet ZKM) =======================================

@Composable
internal fun ExploreCustomModeSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(CpuMode.fromOrdinal(GameSpacePrefs.cpuMode(ctx))) }
    var prof by remember(editing) { mutableStateOf(CustomModeStore.get(ctx, editing)) }
    var status by remember { mutableStateOf("") }
    val clusters = remember { CustomModeEngine.clusters() }
    val governors = remember { CustomModeEngine.governors() }
    val gpuMax = remember { CustomModeEngine.gpuMaxMHz() }
    val root = RootBoost.rootAvailable()

    fun upd(p: ModeProfile) { prof = p; CustomModeStore.set(ctx, editing, p) }

    ModalBottomSheet(onDismissRequest = onDismiss, contentWindowInsets = { zkmSheetWindowInsets() }) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.gsx_cm_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            XNote(stringResource(R.string.gsx_cm_sub))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CpuMode.entries.forEach { m ->
                    FilterChip(m == editing, { editing = m }, {
                        Text(stringResource(m.labelRes) + if (CustomModeStore.get(ctx, m).enabled) " ●" else "")
                    })
                }
            }
            ToggleRow(stringResource(R.string.gsx_cm_use), stringResource(R.string.gsx_cm_use_desc), prof.enabled) { upd(prof.copy(enabled = it)) }

            if (!prof.enabled) {
                XNote(stringResource(R.string.gsx_cm_builtin))
            } else {
                if (!root) XNote(stringResource(R.string.gsx_nr_rootonly_body))

                Box(Modifier.graphicsLayer { alpha = if (root) 1f else 0.45f }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        XLabel(stringResource(R.string.gsx_cm_cpu))
                        val perCluster = prof.clusterLimits.isNotEmpty()
                        if (clusters.size > 1) {
                            XChoice(
                                stringResource(R.string.gsx_cm_cluster_scope),
                                listOf(stringResource(R.string.gsx_cm_cluster_all), stringResource(R.string.gsx_cm_cluster_each)),
                                if (perCluster) 1 else 0
                            ) { i ->
                                if (root) upd(
                                    if (i == 0) prof.copy(clusterLimits = emptyMap())
                                    else prof.copy(clusterLimits = clusters.associate { it.name to (prof.cpuMinPct to prof.cpuMaxPct) })
                                )
                            }
                        }
                        if (!perCluster) {
                            val ref = clusters.maxOfOrNull { it.maxMHz }
                            XPct(stringResource(R.string.gsx_cm_max), prof.cpuMaxPct, 10, ref) {
                                if (root) upd(prof.copy(cpuMaxPct = it.coerceAtLeast(prof.cpuMinPct).coerceAtLeast(10)))
                            }
                            XPct(stringResource(R.string.gsx_cm_min), prof.cpuMinPct, 0, ref) {
                                if (root) upd(prof.copy(cpuMinPct = it.coerceAtMost(prof.cpuMaxPct)))
                            }
                        } else {
                            clusters.forEachIndexed { idx, cl ->
                                val (lo, hi) = prof.clusterLimits[cl.name] ?: (prof.cpuMinPct to prof.cpuMaxPct)
                                fun put(a: Int, b: Int) =
                                    if (root) upd(prof.copy(clusterLimits = prof.clusterLimits + (cl.name to (a to b)))) else Unit
                                XLabel("${clusterTitle(idx, clusters.size)} · ${cl.name.removePrefix("policy")}")
                                XPct(stringResource(R.string.gsx_cm_max), hi, 10, cl.maxMHz) {
                                    val h = it.coerceAtLeast(10); put(lo.coerceAtMost(h), h)
                                }
                                XPct(stringResource(R.string.gsx_cm_min), lo, 0, cl.maxMHz) {
                                    put(it.coerceAtMost(hi), hi)
                                }
                            }
                        }
                        if (governors.isNotEmpty()) {
                            XChoice(
                                stringResource(R.string.gsx_cm_governor),
                                listOf(stringResource(R.string.gsx_cm_auto)) + governors,
                                if (prof.governor.isBlank()) 0 else (governors.indexOf(prof.governor) + 1).coerceAtLeast(0)
                            ) { i -> if (root) upd(prof.copy(governor = if (i == 0) "" else governors[i - 1])) }
                        }

                        XLabel(stringResource(R.string.gsx_cm_gpu))
                        XPct(stringResource(R.string.gsx_cm_max), prof.gpuMaxPct, 10, gpuMax.takeIf { it > 0 }) {
                            if (root) upd(prof.copy(gpuMaxPct = it.coerceAtLeast(prof.gpuMinPct).coerceAtLeast(10)))
                        }
                        XPct(stringResource(R.string.gsx_cm_min), prof.gpuMinPct, 0, gpuMax.takeIf { it > 0 }) {
                            if (root) upd(prof.copy(gpuMinPct = it.coerceAtMost(prof.gpuMaxPct)))
                        }
                    }
                }

                XLabel(stringResource(R.string.gsx_cm_thermal))
                ToggleRow(stringResource(R.string.gsx_cm_t_guard), stringResource(R.string.gsx_cm_t_guard_desc), prof.thermalOn) { upd(prof.copy(thermalOn = it)) }
                if (prof.thermalOn) {
                    Row {
                        Text(stringResource(R.string.gsx_cm_t_limit), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${prof.thermalLimitC}°C", fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(prof.thermalLimitC.toFloat(), { upd(prof.copy(thermalLimitC = it.roundToInt())) }, valueRange = 38f..55f)
                    XNote(stringResource(R.string.gsx_cm_t_note))
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    enabled = root,
                    modifier = Modifier.weight(1f).graphicsLayer { alpha = if (root) 1f else 0.45f },
                    onClick = {
                        val m = editing
                        GameSpacePrefs.setCpuMode(ctx, m.ordinal)
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { CpuModeEngine.apply(ctx, m) }
                            status = r.message ?: ctx.getString(R.string.gsx_cm_applied, ctx.getString(m.labelRes))
                        }
                    }
                ) {
                    if (!root) {
                        Icon(Icons.Rounded.Lock, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(if (root) stringResource(R.string.gsx_cm_apply) else stringResource(R.string.gsx_nr_rootonly_title))
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        CustomModeStore.reset(ctx, editing)
                        prof = CustomModeStore.get(ctx, editing)
                        status = ctx.getString(R.string.gsx_cm_was_reset)
                    }
                ) { Text(stringResource(R.string.gsx_cm_reset)) }
            }
            if (status.isNotEmpty()) Text(status, color = MaterialTheme.colorScheme.primary)
        }
    }
}
