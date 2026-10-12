/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 *
 * Handheld Game Space - Control Panel per game (profil global jika tanpa game).
 * Nyata (dibaca GameHubEngine / GameSpaceEngine): Game Mode (CpuMode), GPU frequency floor (Root),
 * Network (dual-channel + Wi-Fi lock), Functions (kualitas rekam + pintasan panel).
 * Halaman Touch dan Display dihapus: tidak ada API Android untuk keduanya.
 * Original ZKM.
 */
@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.zuan.kernelmanager.next.feature.gamespace

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.library.device.usage.UsageStatsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private object CpSection {
    const val MODE = 0
    const val GPU = 1
    const val DISPLAY = 2
    const val AUDIO = 3
    const val NET = 4
    const val FUNC = 5
}

private data class CpTelemetry(val ramPct: Int, val battery: Int, val tempC: Float, val romPct: Int)

private fun readCpTelemetry(c: Context): CpTelemetry {
    val i = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val bat = if (level >= 0 && scale > 0) level * 100 / scale else 0
    val temp = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
    val mi = ActivityManager.MemoryInfo()
    c.getSystemService(ActivityManager::class.java)?.getMemoryInfo(mi)
    val ram = if (mi.totalMem > 0) (((mi.totalMem - mi.availMem) * 100) / mi.totalMem).toInt() else 0
    val rom = runCatching {
        val st = StatFs(Environment.getDataDirectory().path)
        val total = st.blockCountLong * st.blockSizeLong
        val free = st.availableBlocksLong * st.blockSizeLong
        if (total > 0) (((total - free) * 100) / total).toInt() else 0
    }.getOrDefault(0)
    return CpTelemetry(ram, bat, temp, rom)
}

@Composable
internal fun HandheldControlPanel(
    accent: Color,
    pkg: String?,
    cpuMode: CpuMode,
    onCpuMode: (CpuMode) -> Unit,
    onCustomize: () -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    var section by remember { mutableIntStateOf(CpSection.MODE) }
    // dipakai untuk memaksa halaman dibangun ulang setelah "Restore defaults"
    var resetTick by remember { mutableIntStateOf(0) }
    val t by produceState(initialValue = readCpTelemetry(ctx)) {
        while (true) { value = withContext(Dispatchers.IO) { readCpTelemetry(ctx) }; delay(2000) }
    }
    val name = if (pkg != null) UsageStatsHelper.appLabel(ctx, pkg) else stringResource(R.string.gsx_cp_global)

    val nav = listOf(
        Icons.Rounded.Speed to stringResource(R.string.gsx_cp_mode),
        Icons.Rounded.Memory to stringResource(R.string.gsx_cp_gpu),
        Icons.Rounded.Visibility to stringResource(R.string.gsx_cp_show),
        Icons.Rounded.VolumeUp to stringResource(R.string.gsx_cp_audio),
        Icons.Rounded.Wifi to stringResource(R.string.gsx_cp_net),
        Icons.Rounded.Widgets to stringResource(R.string.gsx_cp_func)
    )

    MechaDialog(
        accent = accent,
        title = stringResource(R.string.gsx_cp_title),
        subtitle = name,
        icon = Icons.Rounded.Tune,
        onDismiss = onDismiss,
        widthFraction = 0.96f, heightFraction = 0.94f
    ) { _ ->
        // ---- strip telemetri ----
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            HubMiniGauge(stringResource(R.string.gsx_g_cpu), stringResource(cpuMode.labelRes), when (cpuMode) {
                CpuMode.BALANCE -> 0.42f
                CpuMode.DYNAMIC -> 0.72f
                CpuMode.PERFORMANCE -> 1f
            }, cpuMode.color)
            HubMiniGauge(stringResource(R.string.gsx_g_ram), "${t.ramPct}%", t.ramPct / 100f, Color(0xFF34D17A))
            HubMiniGauge(stringResource(R.string.gsx_g_temp), "${t.tempC.roundToInt()}°C", (t.tempC / 60f).coerceIn(0f, 1f), if (t.tempC >= 42f) Color(0xFFFF8A4C) else Color(0xFF5DD6FF))
            HubMiniGauge(stringResource(R.string.gsx_g_bat), "${t.battery}%", t.battery / 100f, if (t.battery <= 15) Color(0xFFFF5A5A) else Color(0xFF34D17A))
            HubMiniGauge(stringResource(R.string.gsx_g_rom), "${t.romPct}%", t.romPct / 100f, Color(0xFFC56BFF))
        }
        Spacer(Modifier.height(8.dp))

        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(0.26f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HubSideNav(nav, section, accent, Modifier.weight(1f)) { section = it }
                Box(
                    Modifier
                        .mechaClick(0.94f) {
                            GameHubPrefs.clearGame(ctx, pkg)
                            resetTick++
                        }
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(MechaChamfer)
                        .background(accent.copy(alpha = 0.10f))
                        .border(1.dp, accent.copy(alpha = 0.7f), MechaChamfer)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.gsx_cp_reset).uppercase(), color = Color.White, fontSize = 10.sp,
                        fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 2, lineHeight = 12.sp
                    )
                }
            }
            Box(Modifier.weight(0.74f).fillMaxHeight()) {
                MechaSlideSwitch(section) { s ->
                    // resetTick sebagai key supaya semua rememberHubXxx membaca ulang nilai bawaan
                    androidx.compose.runtime.key(resetTick) {
                        when (s) {
                            CpSection.MODE -> ModePage(accent, cpuMode, onCpuMode, onCustomize)
                            CpSection.GPU -> GpuPage(accent, pkg)
                            CpSection.DISPLAY -> DisplayPage(accent, pkg)
                            CpSection.AUDIO -> AudioPage(accent, pkg)
                            CpSection.NET -> NetPage(accent, pkg)
                            else -> FuncPage(accent, pkg)
                        }
                    }
                }
            }
        }
    }
}

// ==================================== Game Mode ===========================================

@Composable
internal fun ModePage(accent: Color, cpuMode: CpuMode, onCpuMode: (CpuMode) -> Unit, onCustomize: () -> Unit) {
    val values = when (cpuMode) {
        CpuMode.BALANCE -> listOf(0.55f, 0.78f, 0.42f, 0.60f, 0.65f)
        CpuMode.DYNAMIC -> listOf(0.76f, 0.58f, 0.62f, 0.78f, 0.76f)
        CpuMode.PERFORMANCE -> listOf(1.00f, 0.32f, 0.88f, 0.96f, 0.86f)
    }
    val labels = listOf(
        stringResource(R.string.gsx_rd_perf), stringResource(R.string.gsx_rd_batt), stringResource(R.string.gsx_rd_temp),
        stringResource(R.string.gsx_rd_video), stringResource(R.string.gsx_rd_ctrl)
    )
    val desc = when (cpuMode) {
        CpuMode.BALANCE -> R.string.gsx_md_balance
        CpuMode.DYNAMIC -> R.string.gsx_md_dynamic
        CpuMode.PERFORMANCE -> R.string.gsx_md_performance
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CpuMode.entries.forEach { m ->
                Box(Modifier.weight(1f)) {
                    ModeButton(stringResource(m.labelRes), m == cpuMode, m.color) { onCpuMode(m) }
                }
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            HubRadar(values, labels, cpuMode.color, Modifier.weight(1f).fillMaxHeight())
            Text(
                stringResource(desc), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, lineHeight = 17.sp,
                modifier = Modifier.weight(0.9f)
            )
        }
        // Isi tiap mode (CPU/GPU/thermal) diatur di Custom Mode - satu tempat, tidak bentrok dengan panel ini.
        val root = RootBoost.rootAvailable()
        MechaButton(
            stringResource(R.string.gsx_cp_customize) + if (root) "" else " (" + stringResource(R.string.gsx_nr_rootonly_title) + ")",
            if (root) Icons.Rounded.Tune else Icons.Rounded.Lock,
            if (root) cpuMode.color else Color.Gray,
            Modifier.fillMaxWidth().graphicsLayer { alpha = if (root) 1f else 0.55f },
            filled = false
        ) { onCustomize() }
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .mechaClick(0.94f, onClick = onClick)
            .fillMaxWidth()
            .height(42.dp)
            .clip(MechaSlant)
            .background(if (selected) Brush.horizontalGradient(listOf(color.copy(alpha = 0.40f), color.copy(alpha = 0.12f))) else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.05f))))
            .border(if (selected) 1.5.dp else 1.dp, if (selected) color else Color.White.copy(alpha = 0.12f), MechaSlant),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) color else Color.White.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

// ==================================== GPU =================================================

@Composable
internal fun GpuPage(accent: Color, pkg: String?) {
    val boost = rememberHubInt(GameHubPrefs.game(pkg, "gpu_boost"), 0)
    val root = RootBoost.rootAvailable()
    HubPage {
        if (!root) {
            HubNote(stringResource(R.string.gsx_nr_rootonly_body))
        }
        Box(Modifier.graphicsLayer { alpha = if (root) 1f else 0.45f }) {
            HubCard(Modifier.fillMaxWidth()) {
                HubChoiceRow(
                    stringResource(R.string.gsx_gpu_boost),
                    listOf(stringResource(R.string.gsx_gpu_off), stringResource(R.string.gsx_gpu_b_mid), stringResource(R.string.gsx_gpu_b_max)),
                    if (root) boost.value else 0, accent
                ) { if (root) boost.value = it }
            }
        }
        HubNote(stringResource(R.string.gsx_gpu_note))
        HubBottomSpace()
    }
}

// ==================================== Display =============================================

@Composable
internal fun DisplayPage(accent: Color, pkg: String?) {
    val ctx = LocalContext.current
    val hz = rememberHubInt(GameHubPrefs.game(pkg, "display_hz"), 0)
    val touchBoost = rememberHubBool(GameHubPrefs.game(pkg, "fn_touch_boost"), true)
    HubPage {
        MechaSectionLabel(stringResource(R.string.gsx_cp_hz_title), accent)
        HubCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.gsx_cp_hz_desc), color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 60, 90, 120, 144).forEach { valHz ->
                    val label = if (valHz == 0) stringResource(R.string.gs_hz_auto) else "$valHz Hz"
                    MechaChip(label, hz.value == valHz, accent) {
                        hz.value = valHz
                        if (pkg != null) {
                            val cur = GameSpacePrefs.settingsFor(ctx, pkg)
                            GameSpacePrefs.setOverride(ctx, pkg, cur.copy(refreshRateHz = valHz))
                        }
                    }
                }
            }
        }
        MechaToggleTile(
            Icons.Rounded.TouchApp,
            stringResource(R.string.gsx_cp_touch_boost),
            stringResource(R.string.gsx_cp_touch_boost_desc),
            touchBoost.value,
            accent,
            Modifier.fillMaxWidth()
        ) { touchBoost.value = it }
        HubBottomSpace()
    }
}

// ==================================== Audio ===============================================

@Composable
internal fun AudioPage(accent: Color, pkg: String?) {
    val profile = rememberHubInt(GameHubPrefs.game(pkg, "audio_profile"), 0)
    val norm = rememberHubBool(GameHubPrefs.game(pkg, "audio_norm"), false)
    val profiles = listOf(
        stringResource(R.string.gsx_cp_audio_standard),
        stringResource(R.string.gsx_cp_audio_footstep),
        stringResource(R.string.gsx_cp_audio_bass),
        stringResource(R.string.gsx_cp_audio_voice)
    )
    HubPage {
        MechaSectionLabel(stringResource(R.string.gsx_cp_audio_mode), accent)
        HubCard(Modifier.fillMaxWidth()) {
            HubChoiceRow(
                stringResource(R.string.gsx_cp_audio_mode),
                profiles,
                profile.value,
                accent
            ) { profile.value = it }
        }
        MechaToggleTile(
            Icons.Rounded.VolumeUp,
            "Loudness Equalizer",
            "Smooth extreme volume spikes for balanced clarity in gunfights",
            norm.value,
            accent,
            Modifier.fillMaxWidth()
        ) { norm.value = it }
        HubBottomSpace()
    }
}

// ==================================== Network =============================================

@Composable
internal fun NetPage(accent: Color, pkg: String?) {
    val dual = rememberHubBool(GameHubPrefs.game(pkg, "net_dual"), false)
    val prio = rememberHubBool(GameHubPrefs.game(pkg, "net_prio"), false)
    HubPage {
        MechaToggleTile(Icons.Rounded.Wifi, stringResource(R.string.gsx_net_dual), stringResource(R.string.gsx_net_dual_desc), dual.value, accent, Modifier.fillMaxWidth()) { dual.value = it }
        MechaToggleTile(Icons.Rounded.Wifi, stringResource(R.string.gsx_net_prio), stringResource(R.string.gsx_net_prio_desc), prio.value, accent, Modifier.fillMaxWidth()) { prio.value = it }
        HubNote(stringResource(R.string.gsx_net_note))
        HubBottomSpace()
    }
}

// ==================================== Functions ===========================================

@Composable
internal fun FuncPage(accent: Color, pkg: String?) {
    val quality = rememberHubInt(GameHubPrefs.game(pkg, "fn_quality"), 1)
    val scCross = rememberHubBool(GameHubPrefs.game(pkg, "fn_sc_cross"), true)
    val scRam = rememberHubBool(GameHubPrefs.game(pkg, "fn_sc_ram"), true)
    val scCpu = rememberHubBool(GameHubPrefs.game(pkg, "fn_sc_cpu"), true)
    val scRec = rememberHubBool(GameHubPrefs.game(pkg, "fn_sc_rec"), true)
    val scShot = rememberHubBool(GameHubPrefs.game(pkg, "fn_sc_shot"), true)
    val scDnd = rememberHubBool(GameHubPrefs.game(pkg, "fn_sc_dnd"), true)
    HubPage {
        MechaSectionLabel(stringResource(R.string.gsx_fn_rec), accent)
        HubCard(Modifier.fillMaxWidth()) {
            HubChoiceRow(stringResource(R.string.gsx_fn_quality), listOf("SD", "HD"), quality.value, accent) { quality.value = it }
        }
        MechaSectionLabel(stringResource(R.string.gsx_fn_shortcuts), accent)
        MechaToggleTile(Icons.Rounded.CenterFocusStrong, stringResource(R.string.gsx_fn_sc_cross), null, scCross.value, accent, Modifier.fillMaxWidth()) { scCross.value = it }
        MechaToggleTile(Icons.Rounded.CleaningServices, stringResource(R.string.gsx_fn_sc_ram), null, scRam.value, accent, Modifier.fillMaxWidth()) { scRam.value = it }
        MechaToggleTile(Icons.Rounded.Speed, stringResource(R.string.gsx_fn_sc_cpu), null, scCpu.value, accent, Modifier.fillMaxWidth()) { scCpu.value = it }
        MechaToggleTile(Icons.Rounded.VideoLibrary, stringResource(R.string.gsx_fn_sc_rec), null, scRec.value, accent, Modifier.fillMaxWidth()) { scRec.value = it }
        MechaToggleTile(Icons.Rounded.PhotoCamera, stringResource(R.string.gs_hub_shot), null, scShot.value, accent, Modifier.fillMaxWidth()) { scShot.value = it }
        MechaToggleTile(Icons.Rounded.NotificationsOff, stringResource(R.string.gs_hub_dnd), null, scDnd.value, accent, Modifier.fillMaxWidth()) { scDnd.value = it }
        HubNote(stringResource(R.string.gsx_fn_note))
        HubBottomSpace()
    }
}

@Composable
private fun SecondsSlider(title: String, value: Int, max: Int, accent: Color, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${value}s", color = accent, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
        MechaSlider(value.toFloat(), { onChange((it / 5f).roundToInt() * 5) }, 0f..max.toFloat(), accent)
    }
}
