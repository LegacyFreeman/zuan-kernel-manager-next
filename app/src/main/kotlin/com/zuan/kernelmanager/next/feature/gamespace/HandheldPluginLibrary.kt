/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 *
 * Handheld Game Space - Plugin Library: grid tile plugin + panel detail.
 * Nyata: Auxiliary Line (crosshair), Fast Stopwatch (overlay), Touch Indicator (show_touches).
 * Sisanya (Hold, Double Tap, Chat) kerangka UI - lihat label "Preview".
 * Original ZKM.
 */
@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.zuan.kernelmanager.next.feature.gamespace

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zuan.kernelmanager.next.R

/** Satu plugin di Plugin Library. [ready] = sudah tersambung ke logika nyata. */
internal data class HubPlugin(
    val id: String,
    val icon: ImageVector,
    val nameRes: Int,
    val descRes: Int,
    val genres: List<Int>,
    val tint: Color,
    val ready: Boolean = false
)

internal const val PLUGIN_AUX = "aux_line"

internal val HubPlugins: List<HubPlugin> = listOf(
    HubPlugin(PLUGIN_AUX, Icons.Rounded.CenterFocusStrong, R.string.gsx_pn_aux, R.string.gsx_pd_aux,
        listOf(R.string.gsx_genre_moba, R.string.gsx_genre_act, R.string.gsx_genre_fps), Color(0xFF3D8BFF), ready = true),
    HubPlugin("stopwatch", Icons.Rounded.Timer, R.string.gsx_pn_stopwatch, R.string.gsx_pd_stopwatch,
        listOf(R.string.gsx_genre_all), Color(0xFFFF8A3D), ready = true),
    HubPlugin("keypos", Icons.Rounded.Gamepad, R.string.gsx_pn_keypos, R.string.gsx_pd_keypos,
        listOf(R.string.gsx_genre_all), Color(0xFF2FBF71), ready = true),
    HubPlugin("hold", Icons.Rounded.PanTool, R.string.gsx_pn_hold, R.string.gsx_pd_hold,
        listOf(R.string.gsx_genre_all), Color(0xFFE5484D)),
    HubPlugin("double", Icons.Rounded.TouchApp, R.string.gsx_pn_double, R.string.gsx_pd_double,
        listOf(R.string.gsx_genre_all), Color(0xFFA855F7)),
    HubPlugin("chat", Icons.Rounded.RecordVoiceOver, R.string.gsx_pn_chat, R.string.gsx_pd_chat,
        listOf(R.string.gsx_genre_moba), Color(0xFF14B8A6)),
)

@Composable
internal fun HandheldPluginLibrary(
    accent: Color,
    global: GameSettings,
    onGlobalChange: (GameSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    var sel by remember { mutableIntStateOf(0) }
    val saved = remember {
        mutableStateMapOf<String, Boolean>().apply {
            HubPlugins.forEach { put(it.id, GameHubPrefs.bool(ctx, GameHubPrefs.plugin(it.id), false)) }
        }
    }
    fun isOn(p: HubPlugin) = if (p.id == PLUGIN_AUX) global.crosshair else saved[p.id] == true
    fun setOn(p: HubPlugin, v: Boolean) {
        if (p.id == PLUGIN_AUX) {
            onGlobalChange(global.copy(crosshair = v))
        } else {
            saved[p.id] = v
            GameHubPrefs.setBool(ctx, GameHubPrefs.plugin(p.id), v)
        }
    }

    val p = HubPlugins[sel]
    val readyLabel = stringResource(R.string.gsx_tag_ready)
    val previewLabel = stringResource(R.string.gsx_tag_preview)

    MechaDialog(
        accent = accent,
        title = stringResource(R.string.gsx_pl_title),
        subtitle = stringResource(R.string.gsx_pl_sub),
        icon = Icons.Rounded.Extension,
        onDismiss = onDismiss,
        widthFraction = 0.94f, heightFraction = 0.92f
    ) { _ ->
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // ---- detail plugin terpilih ----
            Column(
                Modifier.weight(0.42f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PluginPreview(p, Modifier.fillMaxWidth().height(116.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(p.nameRes), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black,
                        maxLines = 2, modifier = Modifier.weight(1f)
                    )
                    HubTag(if (p.ready) readyLabel else previewLabel, if (p.ready) MechaOk else Color(0xFFF2B84B))
                }
                Text(
                    stringResource(R.string.gsx_pl_supported), color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    p.genres.forEach { g ->
                        Text(
                            stringResource(g), color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
                Text(stringResource(p.descRes), color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, lineHeight = 16.sp)
                MechaToggleTile(
                    p.icon, stringResource(R.string.gsx_pl_enable),
                    stringResource(if (p.ready) R.string.gsx_pl_enable_ready else R.string.gsx_pl_enable_preview),
                    isOn(p), p.tint, Modifier.fillMaxWidth()
                ) { setOn(p, it) }
                HubBottomSpace()
            }

            // ---- grid plugin ----
            LazyVerticalGrid(
                columns = GridCells.Adaptive(138.dp),
                modifier = Modifier.weight(0.58f).fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(HubPlugins) { i, pl ->
                    PluginTile(pl, selected = i == sel, on = isOn(pl), accent = accent, index = i) { sel = i }
                }
            }
        }
    }
}

@Composable
private fun PluginPreview(p: HubPlugin, modifier: Modifier) {
    Box(
        modifier
            .clip(MechaChamfer)
            .background(Brush.linearGradient(listOf(p.tint.copy(alpha = 0.35f), Color(0xFF0A0C14))))
            .border(1.dp, p.tint.copy(alpha = 0.6f), MechaChamfer),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // garis bidik samar sebagai latar semua pratinjau
            val c = center
            val len = size.minDimension * 0.34f
            val lw = 1.dp.toPx()
            drawLine(p.tint.copy(alpha = 0.25f), Offset(c.x - len, c.y), Offset(c.x + len, c.y), lw)
            drawLine(p.tint.copy(alpha = 0.25f), Offset(c.x, c.y - len), Offset(c.x, c.y + len), lw)
        }
        Icon(p.icon, null, tint = p.tint, modifier = Modifier.size(44.dp))
    }
}

@Composable
private fun PluginTile(p: HubPlugin, selected: Boolean, on: Boolean, accent: Color, index: Int, onClick: () -> Unit) {
    val shape = MechaChamfer
    Column(
        Modifier
            .mechaEnter(index, dx = 0.dp, dy = 22.dp)
            .mechaClick(0.94f, onClick = onClick)
            .fillMaxWidth()
            .height(96.dp)
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.05f))
            .border(if (selected) 1.5.dp else 1.dp, if (selected) accent else Color.White.copy(alpha = 0.10f), shape)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(50)).background(p.tint.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) { Icon(p.icon, null, tint = p.tint, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(p.nameRes), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 13.sp
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.width(18.dp).height(3.dp).clip(RoundedCornerShape(50))
                .background(if (on) MechaOk else Color.White.copy(alpha = 0.15f))
        )
    }
}
