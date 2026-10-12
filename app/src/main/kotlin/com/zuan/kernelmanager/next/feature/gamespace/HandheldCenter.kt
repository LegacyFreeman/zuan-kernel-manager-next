/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 *
 * Handheld Game Space - Game Center: Playtime (data nyata dari GameSpacePrefs), Cooling, Network,
 * General, Atmosphere (backdrop + LED yang langsung mengubah konsol) dan Help.
 * Nyata: Cooling (thermal guard), Network (dual-channel + Wi-Fi lock), General (kunci kecerahan,
 * pengingat main, animasi). "Hide icons" dan "Screen saver" masih kerangka UI. Original ZKM.
 */
@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.zuan.kernelmanager.next.feature.gamespace

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
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

internal val RankHours = intArrayOf(0, 10, 50, 150, 400)
internal val RankNames = intArrayOf(R.string.gsx_rank_0, R.string.gsx_rank_1, R.string.gsx_rank_2, R.string.gsx_rank_3, R.string.gsx_rank_4)

internal object CenterSection {
    const val PLAYTIME = 0
    const val COOLING = 1
    const val NETWORK = 2
    const val GENERAL = 3
    const val ATMOSPHERE = 4
    const val HELP = 5
}

@Composable
internal fun HandheldCenter(
    accent: Color,
    global: GameSettings,
    onGlobalChange: (GameSettings) -> Unit,
    look: HubLookState,
    games: List<String>,
    initialSection: Int,
    onDismiss: () -> Unit
) {
    var section by remember { mutableIntStateOf(initialSection.coerceIn(0, 5)) }
    val nav = listOf(
        Icons.Rounded.HourglassBottom to stringResource(R.string.gsx_ct_playtime),
        Icons.Rounded.AcUnit to stringResource(R.string.gsx_ct_cooling),
        Icons.Rounded.Wifi to stringResource(R.string.gsx_ct_network),
        Icons.Rounded.Settings to stringResource(R.string.gsx_ct_general),
        Icons.Rounded.Lightbulb to stringResource(R.string.gsx_ct_atmos),
        Icons.Rounded.Info to stringResource(R.string.gsx_ct_help)
    )

    MechaDialog(
        accent = accent,
        title = stringResource(R.string.gsx_ct_title),
        subtitle = stringResource(R.string.gsx_ct_sub),
        icon = Icons.Rounded.EmojiEvents,
        onDismiss = onDismiss,
        widthFraction = 0.94f, heightFraction = 0.92f
    ) { _ ->
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            HubSideNav(nav, section, accent, Modifier.weight(0.26f).fillMaxHeight()) { section = it }
            Box(Modifier.weight(0.74f).fillMaxHeight()) {
                MechaSlideSwitch(section) { s ->
                    when (s) {
                        CenterSection.PLAYTIME -> PlaytimePage(games, accent)
                        CenterSection.COOLING -> CoolingPage(accent)
                        CenterSection.NETWORK -> NetworkPage(accent)
                        CenterSection.GENERAL -> GeneralPage(accent, global, onGlobalChange)
                        CenterSection.ATMOSPHERE -> AtmospherePage(accent, look)
                        else -> HelpPage(accent)
                    }
                }
            }
        }
    }
}

// ==================================== Playtime ============================================

@Composable
internal fun PlaytimePage(games: List<String>, accent: Color) {
    val ctx = LocalContext.current
    val times = games.map { it to GameSpacePrefs.playtimeMs(ctx, it) }.sortedByDescending { it.second }
    val totalMs = times.sumOf { it.second }
    val hours = (totalMs / 3_600_000L).toInt()
    var rank = 0
    for (i in RankHours.indices) if (hours >= RankHours[i]) rank = i
    val nextAt = RankHours.getOrNull(rank + 1)
    val frac = if (nextAt == null) 1f else {
        val base = RankHours[rank]
        ((totalMs / 3_600_000f) - base) / (nextAt - base).toFloat()
    }
    val top = times.firstOrNull()?.takeIf { it.second > 0 }

    HubPage {
        Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GaugeBlock(stringResource(R.string.gsx_ct_total), Modifier.weight(1f).fillMaxHeight()) {
                HubRing(if (totalMs > 0) 0.35f + 0.65f * (hours.coerceAtMost(100) / 100f) else 0f, accent, Modifier.fillMaxSize(), thickness = 6.dp) {
                    Text(
                        formatUsageDuration(totalMs), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center, maxLines = 2
                    )
                }
            }
            GaugeBlock(stringResource(R.string.gsx_ct_rank), Modifier.weight(1f).fillMaxHeight()) {
                HubRing(frac, accent, Modifier.fillMaxSize(), thickness = 6.dp) {
                    Text(
                        stringResource(RankNames[rank]).uppercase(), color = accent, fontSize = 15.sp,
                        fontWeight = FontWeight.Black, letterSpacing = 1.sp, textAlign = TextAlign.Center, maxLines = 1
                    )
                }
            }
            GaugeBlock(stringResource(R.string.gsx_ct_detail), Modifier.weight(1f).fillMaxHeight()) {
                HubRing(if (top != null && totalMs > 0) top.second.toFloat() / totalMs else 0f, accent, Modifier.fillMaxSize(), thickness = 6.dp) {
                    if (top != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AppIcon(top.first, 30.dp)
                            Text(
                                formatUsageDuration(top.second), color = Color.White, fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace, maxLines = 1
                            )
                        }
                    } else {
                        Text(stringResource(R.string.gsx_ct_nogames), color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        HubNote(
            if (nextAt == null) stringResource(R.string.gsx_ct_max_rank)
            else stringResource(R.string.gsx_ct_to_next, (nextAt - totalMs / 3_600_000f).toInt() + 1)
        )

        if (times.isNotEmpty()) {
            MechaSectionLabel(stringResource(R.string.gsx_ct_breakdown), accent)
            times.take(5).forEach { (pkg, ms) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(pkg, 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(UsageStatsHelper.appLabel(ctx, pkg), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(MechaSlant).background(Color.White.copy(alpha = 0.08f))) {
                            val f = if (totalMs > 0) (ms.toFloat() / totalMs).coerceIn(0f, 1f) else 0f
                            Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(accent))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(formatUsageDuration(ms), color = accent, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                }
            }
        }
        HubNote(stringResource(R.string.gsx_ct_tip))
        HubBottomSpace()
    }
}

@Composable
private fun GaugeBlock(title: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .clip(MechaChamfer)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), MechaChamfer)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title.uppercase(), color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, maxLines = 1)
        Box(Modifier.weight(1f).fillMaxWidth().padding(2.dp), contentAlignment = Alignment.Center) { content() }
    }
}

// ==================================== Cooling =============================================

@Composable
internal fun CoolingPage(accent: Color) {
    // Thermal guard dipindah ke Custom Mode (per mode, bawaan MATI) supaya tidak ada dua pengatur yang saling bentrok.
    HubPage {
        HubCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.gsx_cool_moved_title), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
            Text(stringResource(R.string.gsx_cool_moved_desc), color = accent, fontSize = 12.sp, lineHeight = 17.sp)
        }
        HubNote(stringResource(R.string.gsx_cool_note))
        HubBottomSpace()
    }
}

// ==================================== Network =============================================

@Composable
internal fun NetworkPage(accent: Color) {
    val dual = rememberHubBool("net_dual", false)
    val prio = rememberHubBool("net_prio", false)
    HubPage {
        MechaToggleTile(Icons.Rounded.Wifi, stringResource(R.string.gsx_net_dual), stringResource(R.string.gsx_net_dual_desc), dual.value, accent, Modifier.fillMaxWidth()) { dual.value = it }
        MechaToggleTile(Icons.Rounded.Wifi, stringResource(R.string.gsx_net_prio), stringResource(R.string.gsx_net_prio_desc), prio.value, accent, Modifier.fillMaxWidth()) { prio.value = it }
        HubNote(stringResource(R.string.gsx_net_note))
        HubBottomSpace()
    }
}

// ==================================== General =============================================

@Composable
internal fun GeneralPage(accent: Color, global: GameSettings, onGlobalChange: (GameSettings) -> Unit) {
    val hide = rememberHubBool("gen_hide_icons", false)
    val saver = rememberHubBool("gen_screen_saver", false)
    val open = rememberHubBool("gen_open_anim", true)
    val wellbeing = rememberHubBool("gen_wellbeing", false)
    val start = rememberHubBool("gen_start_anim", true)
    HubPage {
        // nyata: kunci kecerahan, pengingat main, animasi pembuka & mulai
        MechaToggleTile(
            null, stringResource(R.string.gsx_gen_bright), stringResource(R.string.gsx_gen_bright_desc),
            global.lockBrightnessMode, accent, Modifier.fillMaxWidth()
        ) { onGlobalChange(global.copy(lockBrightnessMode = it)) }
        MechaToggleTile(null, stringResource(R.string.gsx_gen_hide), stringResource(R.string.gsx_gen_hide_desc), hide.value, accent, Modifier.fillMaxWidth()) { hide.value = it }
        MechaToggleTile(null, stringResource(R.string.gsx_gen_saver), stringResource(R.string.gsx_gen_saver_desc), saver.value, accent, Modifier.fillMaxWidth()) { saver.value = it }
        MechaToggleTile(null, stringResource(R.string.gsx_gen_open), null, open.value, accent, Modifier.fillMaxWidth()) { open.value = it }
        MechaToggleTile(null, stringResource(R.string.gsx_gen_wellbeing), null, wellbeing.value, accent, Modifier.fillMaxWidth()) { wellbeing.value = it }
        MechaToggleTile(null, stringResource(R.string.gsx_gen_start), null, start.value, accent, Modifier.fillMaxWidth()) { start.value = it }
        HubNote(stringResource(R.string.gsx_gen_note))
        HubBottomSpace()
    }
}

// ==================================== Atmosphere ==========================================

@Composable
internal fun AtmospherePage(accent: Color, look: HubLookState) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MechaTabs(listOf(stringResource(R.string.gsx_at_backdrop), stringResource(R.string.gsx_at_led)), tab, accent, { tab = it })
        Box(Modifier.weight(1f).fillMaxWidth()) {
            MechaSlideSwitch(tab) { t ->
                if (t == 0) BackdropPicker(accent, look) else LedPage(accent, look)
            }
        }
    }
}

@Composable
private fun BackdropPicker(accent: Color, look: HubLookState) {
    val names = listOf(
        stringResource(R.string.gsx_bd_reactor), stringResource(R.string.gsx_bd_grid),
        stringResource(R.string.gsx_bd_neon), stringResource(R.string.gsx_bd_hex),
        stringResource(R.string.gsx_bd_circuit), stringResource(R.string.gsx_bd_vortex),
        stringResource(R.string.gsx_bd_matrix), stringResource(R.string.gsx_bd_carbon),
        stringResource(R.string.gsx_bd_aurora), stringResource(R.string.gsx_bd_stars)
    )
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var pickMsg by remember { mutableStateOf("") }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) scope.launch {
            val ok = look.importBg(uri)
            pickMsg = ctx.getString(if (ok) R.string.gsx_bd_img_ok else R.string.gsx_bd_img_fail)
        }
    }
    val particles = rememberHubBool("at_particles", true)
    val vignette = rememberHubBool("at_vignette", true)

    HubPage {
        MechaToggleTile(
            null, stringResource(R.string.gsx_at_particles), stringResource(R.string.gsx_at_particles_desc),
            particles.value, accent, Modifier.fillMaxWidth()
        ) { particles.value = it }
        MechaToggleTile(
            null, stringResource(R.string.gsx_at_vignette), stringResource(R.string.gsx_at_vignette_desc),
            vignette.value, accent, Modifier.fillMaxWidth()
        ) { vignette.value = it }

        MechaSectionLabel(stringResource(R.string.gsx_at_backdrop), accent)
        for (rowStart in names.indices step 2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (i in rowStart until minOf(rowStart + 2, names.size)) {
                    BackdropCard(names[i], i, look.style == i, accent, Modifier.weight(1f)) { look.updateStyle(i) }
                }
            }
        }

        // ---- gambar sendiri dari Galeri ----
        MechaSectionLabel(stringResource(R.string.gsx_bd_image), accent)
        HubCard(Modifier.fillMaxWidth()) {
            val img = look.bgImage
            if (img != null) {
                Box(
                    Modifier.fillMaxWidth().height(110.dp).clip(MechaChamfer)
                        .border(if (look.style == GameHubPrefs.STYLE_IMAGE) 2.dp else 1.dp, if (look.style == GameHubPrefs.STYLE_IMAGE) accent else Color.White.copy(alpha = 0.14f), MechaChamfer)
                        .mechaClick(0.97f) { look.updateStyle(GameHubPrefs.STYLE_IMAGE) }
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val sc = maxOf(size.width / img.width, size.height / img.height)
                        val sw = (size.width / sc).toInt().coerceAtMost(img.width)
                        val sh = (size.height / sc).toInt().coerceAtMost(img.height)
                        drawImage(
                            img, srcOffset = androidx.compose.ui.unit.IntOffset((img.width - sw) / 2, (img.height - sh) / 2),
                            srcSize = androidx.compose.ui.unit.IntSize(sw, sh),
                            dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
                        )
                        drawRect(Color.Black.copy(alpha = look.bgDim / 100f))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.gsx_bd_dim), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${look.bgDim}%", color = accent, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
                MechaSlider(look.bgDim.toFloat(), { look.updateBgDim(it.roundToInt()) }, 0f..90f, accent)
            } else {
                Text(stringResource(R.string.gsx_bd_image_desc), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, lineHeight = 14.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MechaButton(stringResource(R.string.gsx_bd_pick), Icons.Rounded.Lightbulb, accent, Modifier.weight(1f)) { picker.launch("image/*") }
                if (img != null) MechaButton(stringResource(R.string.gsx_bd_remove), null, accent, Modifier.weight(1f), filled = false, danger = true) { look.clearBg(); pickMsg = "" }
            }
            if (pickMsg.isNotEmpty()) Text(pickMsg, color = accent, fontSize = 11.sp)
        }
        HubBottomSpace()
    }
}

@Composable
private fun BackdropCard(name: String, style: Int, selected: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "bdPrev")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "bdT")
    Box(
        modifier
            .mechaClick(0.96f, onClick = onClick)
            .height(110.dp)
            .clip(MechaChamfer)
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else Color.White.copy(alpha = 0.14f), MechaChamfer)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF090B14), Color(0xFF040509))))
            drawRect(Brush.radialGradient(listOf(accent.copy(alpha = 0.20f), Color.Transparent), center = Offset(size.width / 2f, size.height * 0.55f), radius = size.width * 0.6f))
            clipRect { drawHubStyle(style, accent, t) }
        }
        Text(
            name.uppercase(), color = if (selected) accent else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black,
            letterSpacing = 2.sp, modifier = Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 8.dp)
        )
    }
}

@Composable
private fun LedPage(accent: Color, look: HubLookState) {
    HubPage {
        MechaToggleTile(
            Icons.Rounded.Lightbulb, stringResource(R.string.gsx_led_on), stringResource(R.string.gsx_led_on_desc),
            look.ledOn, accent, Modifier.fillMaxWidth()
        ) { look.updateLedOn(it) }
        HubCard(Modifier.fillMaxWidth()) {
            HubChoiceRow(
                stringResource(R.string.gsx_led_fx),
                listOf(
                    stringResource(R.string.gsx_led_steady), stringResource(R.string.gsx_led_breathe),
                    stringResource(R.string.gsx_led_flash), stringResource(R.string.gsx_led_flow),
                    stringResource(R.string.gsx_led_rainbow), stringResource(R.string.gsx_led_aurora),
                    stringResource(R.string.gsx_led_pulse)
                ),
                look.ledFx, accent
            ) { look.updateLedFx(it) }
            Text(stringResource(R.string.gsx_led_color), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LedSwatch(accent, look.ledColorIdx == -1, stringResource(R.string.gsx_led_auto)) { look.updateLedColor(-1) }
                HubLedColors.forEachIndexed { i, c -> LedSwatch(c, look.ledColorIdx == i, null) { look.updateLedColor(i) } }
            }
        }
        HubBottomSpace()
    }
}

@Composable
private fun LedSwatch(color: Color, selected: Boolean, label: String?, onClick: () -> Unit) {
    Box(
        Modifier
            .mechaClick(0.88f, onClick = onClick)
            .size(if (label != null) 58.dp else 44.dp, 36.dp)
            .clip(MechaSlant)
            .background(color)
            .border(if (selected) 2.5.dp else 1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.2f), MechaSlant),
        contentAlignment = Alignment.Center
    ) {
        if (label != null) Text(label, color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

// ==================================== Help ================================================

@Composable
internal fun HelpPage(accent: Color) {
    HubPage {
        HelpCard(stringResource(R.string.gsx_help_1_t), stringResource(R.string.gsx_help_1_d), accent)
        HelpCard(stringResource(R.string.gsx_help_2_t), stringResource(R.string.gsx_help_2_d), accent)
        HelpCard(stringResource(R.string.gsx_help_3_t), stringResource(R.string.gsx_help_3_d), accent)
        HelpCard(stringResource(R.string.gsx_help_4_t), stringResource(R.string.gsx_help_4_d), accent)
        HelpCard(stringResource(R.string.gsx_help_5_t), stringResource(R.string.gsx_help_5_d), accent)
        HelpCard(stringResource(R.string.gsx_help_6_t), stringResource(R.string.gsx_help_6_d), accent)
        HelpCard(stringResource(R.string.gsx_help_7_t), stringResource(R.string.gsx_help_7_d), accent)
        HubBottomSpace()
    }
}

@Composable
private fun HelpCard(title: String, desc: String, accent: Color) {
    HubCard(Modifier.fillMaxWidth()) {
        Text(title, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Black)
        Text(desc, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, lineHeight = 16.sp)
    }
}
