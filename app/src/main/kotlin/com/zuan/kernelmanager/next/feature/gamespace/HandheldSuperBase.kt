/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 *
 * Handheld Game Space - tab Super Base: dek perintah berisi Core Reactor (mode CPU),
 * Plugin Library, Game Library, Game Center, Atmosphere dan Control Panel. Original ZKM.
 */
package com.zuan.kernelmanager.next.feature.gamespace

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.library.device.usage.UsageStatsHelper
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun SuperBase(
    pkg: String?,
    accent: Color,
    cpuMode: CpuMode,
    onCpu: () -> Unit,
    onPlugins: () -> Unit,
    onLibrary: () -> Unit,
    onCenter: () -> Unit,
    onAtmosphere: () -> Unit,
    onControl: () -> Unit,
    onCustom: () -> Unit,
    onRoot: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(start = 24.dp)) {
        Text(
            stringResource(R.string.gsx_sb_title), color = Color.White, fontSize = 22.sp,
            fontWeight = FontWeight.Black, letterSpacing = 4.sp
        )
        Text(stringResource(R.string.gsx_sb_sub), color = accent, fontSize = 11.sp, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))

        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CoreCard(cpuMode, Modifier.weight(1f).fillMaxHeight(), onCpu)

            Column(Modifier.weight(1.35f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HubTile(
                        Icons.Rounded.Extension, stringResource(R.string.gsx_sb_plugins), stringResource(R.string.gsx_sb_plugins_sub),
                        Color(0xFF5B8CFF), Modifier.weight(1f).fillMaxHeight(), index = 1, onClick = onPlugins
                    )
                    HubTile(
                        Icons.Rounded.Folder, stringResource(R.string.gsx_sb_library), stringResource(R.string.gsx_sb_library_sub),
                        Color(0xFFF2B84B), Modifier.weight(1f).fillMaxHeight(), index = 2, onClick = onLibrary
                    )
                }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HubTile(
                        Icons.Rounded.EmojiEvents, stringResource(R.string.gsx_sb_center), stringResource(R.string.gsx_sb_center_sub),
                        accent, Modifier.weight(1f).fillMaxHeight(), index = 3, onClick = onCenter
                    )
                    HubTile(
                        Icons.Rounded.Lightbulb, stringResource(R.string.gsx_sb_atmos), stringResource(R.string.gsx_sb_atmos_sub),
                        Color(0xFFC56BFF), Modifier.weight(1f).fillMaxHeight(), index = 4, onClick = onAtmosphere
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val root = RootBoost.rootAvailable()
                    SbStrip(Icons.Rounded.Tune, stringResource(R.string.gsx_sb_control_short), accent, Modifier.weight(1f), 5, onControl)
                    SbStrip(if (root) Icons.Rounded.Speed else Icons.Rounded.Lock, stringResource(R.string.gsx_sb_custom) + if (root) "" else " (ROOT)", if (root) cpuMode.color else Color.Gray, Modifier.weight(1f), 6, onCustom)
                    SbStrip(Icons.Rounded.Bolt, stringResource(if (root) R.string.gsx_sb_root else R.string.gsx_nr_title), if (root) Color(0xFFFF8A4C) else Color(0xFF5DD6FF), Modifier.weight(1f), 7, onRoot)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Kartu besar: ring mode CPU dengan tiga titik orbit. Ketuk untuk ganti mode. */
@Composable
private fun CoreCard(cpuMode: CpuMode, modifier: Modifier, onCpu: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "core")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "coreRot")
    val col by animateColorAsState(cpuMode.color, tween(400), label = "coreCol")
    val frac = when (cpuMode) {
        CpuMode.BALANCE -> 0.42f
        CpuMode.DYNAMIC -> 0.72f
        CpuMode.PERFORMANCE -> 1f
    }
    BoxWithConstraints(
        modifier
            .mechaEnter(0, dx = (-30).dp)
            .mechaClick(0.96f, onClick = onCpu)
            .clip(MechaChamfer)
            .background(Brush.verticalGradient(listOf(col.copy(alpha = 0.20f), Color.White.copy(alpha = 0.03f))))
            .border(1.dp, col.copy(alpha = 0.7f), MechaChamfer),
        contentAlignment = Alignment.Center
    ) {
        val d = minOf(maxWidth, maxHeight) * 0.80f
        Box(Modifier.size(d), contentAlignment = Alignment.Center) {
            HubRing(frac, col, Modifier.fillMaxSize(), thickness = 7.dp)
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f * 0.70f
                drawCircle(col.copy(alpha = 0.22f), r, center, style = Stroke(1.dp.toPx()))
                for (k in 0 until 3) {
                    val a = Math.toRadians((rot + k * 120f).toDouble())
                    drawCircle(col, 3.dp.toPx(), Offset(center.x + (cos(a) * r).toFloat(), center.y + (sin(a) * r).toFloat()))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.gsx_sb_core).uppercase(), color = Color.White.copy(alpha = 0.6f),
                    fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp
                )
                Text(
                    stringResource(cpuMode.labelRes).uppercase(), color = col, fontSize = 17.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.gsx_sb_core_hint), color = Color.White.copy(alpha = 0.45f),
                    fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Tombol lebar di bawah grid (Control Panel / Custom Mode / Root Tools). */
@Composable
private fun SbStrip(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, tint: Color, modifier: Modifier, index: Int, onClick: () -> Unit) {
    Row(
        modifier
            .mechaEnter(index, dx = 0.dp, dy = 24.dp)
            .mechaClick(0.96f, onClick = onClick)
            .height(52.dp)
            .clip(MechaSlant)
            .background(Brush.horizontalGradient(listOf(tint.copy(alpha = 0.30f), tint.copy(alpha = 0.08f))))
            .border(1.dp, tint, MechaSlant)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            title.uppercase(), color = Color.White, fontSize = 11.sp,
            fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}
