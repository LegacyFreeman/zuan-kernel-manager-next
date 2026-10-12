/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 *
 * Game Space Hub - komponen UI bersama (ring gauge, radar, tile, side-nav, gaya latar, LED).
 * Semua mengikuti design system Mecha (HandheldMecha.kt). Original ZKM.
 */
@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.zuan.kernelmanager.next.feature.gamespace

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

// ==================================== ring gauge ==========================================

/** Gauge busur 270 derajat dengan tick; isi tengah lewat [content]. */
@Composable
internal fun HubRing(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    thickness: Dp = 6.dp,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing), label = "hubRing")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = thickness.toPx()
            val inset = sw / 2f + 1.dp.toPx()
            val d = size.minDimension - inset * 2f
            val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            val sz = Size(d, d)
            drawArc(Color.White.copy(alpha = 0.10f), 135f, 270f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
            if (f > 0.001f) {
                drawArc(color.copy(alpha = 0.28f), 135f, 270f * f, false, tl, sz, style = Stroke(sw * 2.2f, cap = StrokeCap.Round))
                drawArc(color, 135f, 270f * f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
            }
            val c = center
            val rOut = d / 2f - sw - 3.dp.toPx()
            val rIn = rOut - 5.dp.toPx()
            for (i in 0..27) {
                val a = Math.toRadians(135.0 + 270.0 / 27.0 * i)
                val on = i / 27f <= f
                drawLine(
                    if (on) color.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.12f),
                    Offset(c.x + (cos(a) * rOut).toFloat(), c.y + (sin(a) * rOut).toFloat()),
                    Offset(c.x + (cos(a) * rIn).toFloat(), c.y + (sin(a) * rIn).toFloat()),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
        content()
    }
}

/** Ring kecil dengan angka dan label (strip telemetri Control Panel). */
@Composable
internal fun HubMiniGauge(label: String, valueText: String, fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        HubRing(fraction, color, Modifier.size(44.dp), thickness = 4.dp)
        Spacer(Modifier.width(6.dp))
        Column {
            Text(label, color = Color.White.copy(alpha = 0.55f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            Text(valueText, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
        }
    }
}

// ==================================== radar ===============================================

/** Radar segi-n. [values] 0..1 searah jarum jam mulai dari atas; berubah mulus saat nilai berganti. */
@Composable
internal fun HubRadar(values: List<Float>, labels: List<String>, accent: Color, modifier: Modifier = Modifier) {
    val anim = values.mapIndexed { i, v ->
        animateFloatAsState(v.coerceIn(0f, 1f), tween(550, easing = FastOutSlowInEasing), label = "radar$i").value
    }
    val n = anim.size
    BoxWithConstraints(modifier) {
        val side = minOf(maxWidth, maxHeight)
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.minDimension * 0.30f
            fun pt(i: Int, k: Float): Offset {
                val a = Math.toRadians(-90.0 + 360.0 / n * i)
                return Offset(cx + (cos(a) * r * k).toFloat(), cy + (sin(a) * r * k).toFloat())
            }
            for (ring in 1..4) {
                val k = ring / 4f
                val p = Path()
                for (i in 0 until n) {
                    val o = pt(i, k)
                    if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y)
                }
                p.close()
                drawPath(p, Color.White.copy(alpha = if (ring == 4) 0.18f else 0.07f), style = Stroke(1.dp.toPx()))
            }
            for (i in 0 until n) drawLine(Color.White.copy(alpha = 0.08f), Offset(cx, cy), pt(i, 1f), 1.dp.toPx())
            val poly = Path()
            for (i in 0 until n) {
                val o = pt(i, anim[i].coerceAtLeast(0.04f))
                if (i == 0) poly.moveTo(o.x, o.y) else poly.lineTo(o.x, o.y)
            }
            poly.close()
            drawPath(poly, accent.copy(alpha = 0.26f))
            drawPath(poly, accent, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
            for (i in 0 until n) drawCircle(accent, 3.dp.toPx(), pt(i, anim[i].coerceAtLeast(0.04f)))
        }
        val labelR = side * 0.30f * 1.42f
        for (i in 0 until n) {
            val a = Math.toRadians(-90.0 + 360.0 / n * i)
            Text(
                labels.getOrElse(i) { "" },
                color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = (cos(a) * labelR.value).dp, y = (sin(a) * labelR.value).dp)
            )
        }
    }
}

// ==================================== kartu & tile ========================================

/** Tile besar bersudut potong dengan ikon, judul dan sub-judul; dipakai di Super Base. */
@Composable
internal fun HubTile(
    icon: ImageVector,
    title: String,
    sub: String?,
    tint: Color,
    modifier: Modifier = Modifier,
    index: Int = 0,
    onClick: () -> Unit
) {
    Box(
        modifier
            .mechaEnter(index, dx = 0.dp, dy = 24.dp)
            .mechaClick(0.95f, onClick = onClick)
            .clip(MechaChamfer)
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.24f), Color.White.copy(alpha = 0.03f))))
            .border(1.dp, Brush.linearGradient(listOf(tint.copy(alpha = 0.8f), Color.White.copy(alpha = 0.08f))), MechaChamfer)
    ) {
        Icon(
            icon, null, tint = tint.copy(alpha = 0.14f),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp).size(70.dp)
        )
        Icon(icon, null, tint = tint, modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 12.dp).size(28.dp))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 10.dp, bottom = 10.dp)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) {
                Text(sub, color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, lineHeight = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Kaca tipis bersudut potong untuk mengelompokkan isi halaman. */
@Composable
internal fun HubCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(MechaChamfer)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), MechaChamfer)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

/** Label kecil berbentuk pil, mis. READY / PREVIEW. */
@Composable
internal fun HubTag(text: String, color: Color, modifier: Modifier = Modifier) {
    val pill = RoundedCornerShape(50)
    Text(
        text.uppercase(), color = color, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp,
        modifier = modifier
            .clip(pill)
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.5f), pill)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

// ==================================== navigasi samping ====================================

/** Rel menu kiri (seperti sidebar Game Space Center / Control Panel). */
@Composable
internal fun HubSideNav(
    items: List<Pair<ImageVector, String>>,
    selected: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEachIndexed { i, item ->
            val on = i == selected
            val bg by animateColorAsState(if (on) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.04f), tween(200), label = "navBg")
            val fg by animateColorAsState(if (on) Color.White else Color.White.copy(alpha = 0.6f), tween(200), label = "navFg")
            Row(
                Modifier
                    .mechaClick(0.96f) { onSelect(i) }
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(MechaSlant)
                    .background(bg)
                    .border(1.dp, if (on) accent else Color.White.copy(alpha = 0.08f), MechaSlant)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(item.first, null, tint = if (on) accent else fg, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(item.second, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ==================================== baris setelan =======================================

@Composable
internal fun HubChoiceRow(
    title: String,
    options: List<String>,
    selected: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, o -> MechaChip(o, i == selected, accent) { onSelect(i) } }
        }
    }
}

/** Slider bertingkat (-2..2 secara bawaan) dengan label kiri/kanan. */
@Composable
internal fun HubStepSlider(
    title: String,
    left: String,
    right: String,
    value: Int,
    accent: Color,
    min: Int = -2,
    max: Int = 2,
    onChange: (Int) -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(if (value > 0) "+$value" else "$value", color = accent, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
        MechaSlider(value.toFloat(), { onChange(it.roundToInt().coerceIn(min, max)) }, min.toFloat()..max.toFloat(), accent)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(left, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
            Text(right, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
        }
    }
}

// ==================================== gaya latar & LED ====================================

/**
 * Hiasan latar konsol. [style]:
 * 0 Reactor (bawaan mecha), 1 Grid (synthwave horizon), 2 Neon (tachyon laser), 3 Hex (matrix armor),
 * 4 Circuit (ROG cyber PCB), 5 Vortex (RedMagic tachyon vortex), 6 Matrix (digital data rain), 7 Carbon (stealth weave), 8 Aurora, 9 Starfield, 10 Custom image (digambar terpisah di backdrop).
 * [t] 0..1 berulang; semua gerak dibuat kelipatan bulat supaya loop mulus.
 */
internal fun DrawScope.drawHubStyle(style: Int, accent: Color, t: Float) {
    val w = size.width
    val h = size.height
    when (style) {
        8 -> { // Aurora: pita warna lembut melayang pelan
            val tau = 2f * PI.toFloat()
            for (b in 0 until 4) {
                val base = h * (0.22f + 0.17f * b)
                val col = lerp(accent, listOf(Color(0xFF4DE6FF), Color(0xFFC56BFF), Color(0xFF5DF08A), Color(0xFFFF6FB5))[b], 0.55f)
                val path = androidx.compose.ui.graphics.Path()
                val steps = 36
                for (i in 0..steps) {
                    val x = w * i / steps
                    val y = base + sin(tau * (t * (1 + b % 2) + i / 14f + b * 0.23f)) * h * 0.07f +
                        sin(tau * (t * 2f + i / 7f)) * h * 0.02f
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.lineTo(w, base + h * 0.30f); path.lineTo(0f, base + h * 0.30f); path.close()
                drawPath(path, Brush.verticalGradient(listOf(col.copy(alpha = 0.20f), Color.Transparent), startY = base - h * 0.08f, endY = base + h * 0.30f))
            }
        }
        9 -> { // Starfield: tiga lapis bintang bergerak dengan kecepatan beda (paralaks)
            for (i in 0 until 90) {
                val layer = 1 + i % 3
                val x0 = (((i * 7919) % 997) / 997f)
                val y0 = (((i * 104729) % 1013) / 1013f)
                val x = ((x0 - t * layer * 0.5f) % 1f + 1f) % 1f * w
                val tw = 0.45f + 0.55f * sin(2f * PI.toFloat() * (t * (2 + layer) + i * 0.13f))
                drawCircle(
                    Color.White.copy(alpha = (0.12f + 0.16f * layer) * tw.coerceIn(0.2f, 1f)),
                    radius = (0.6f + 0.5f * layer).dp.toPx(), center = Offset(x, y0 * h)
                )
            }
            drawCircle(accent.copy(alpha = 0.08f), radius = h * 0.55f, center = Offset(w * 0.78f, h * 0.2f))
        }
        1 -> {
            val vy = h * 0.52f
            val line = 1.dp.toPx()
            for (j in -9..9) {
                drawLine(accent.copy(alpha = 0.10f), Offset(w / 2f, vy), Offset(w / 2f + j * (w / 9f), h), strokeWidth = line)
            }
            for (i in 0 until 9) {
                val k = ((i / 9f) + t * 6f) % 1f
                val y = vy + (h - vy) * k * k
                drawLine(accent.copy(alpha = 0.04f + 0.22f * k), Offset(0f, y), Offset(w, y), strokeWidth = line)
            }
        }
        2 -> {
            val c2 = lerp(accent, Color.White, 0.35f)
            val pulse = 0.55f + 0.45f * sin(t * 2f * PI.toFloat() * 4f)
            fun beam(x0: Float, x1: Float, c: Color, a: Float) {
                drawLine(
                    Brush.verticalGradient(listOf(Color.Transparent, c.copy(alpha = a), c.copy(alpha = a * 0.4f))),
                    Offset(x0, 0f), Offset(x1, h), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round
                )
            }
            beam(w * 0.08f, w * 0.38f, accent, 0.55f * pulse)
            beam(w * 0.20f, w * 0.46f, c2, 0.35f * pulse)
            beam(w * 0.92f, w * 0.62f, accent, 0.55f * pulse)
            beam(w * 0.80f, w * 0.54f, c2, 0.35f * pulse)
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.14f * pulse)), startY = h * 0.65f, endY = h),
                topLeft = Offset(0f, h * 0.65f), size = Size(w, h * 0.35f)
            )
        }
        3 -> {
            val r = 24.dp.toPx()
            val dx = r * 1.7320508f
            val dy = r * 1.5f
            val cols = (w / dx).toInt() + 2
            val rows = (h / dy).toInt() + 2
            val hex = Path()
            for (row in 0..rows) {
                for (col in 0..cols) {
                    val cx = col * dx + if (row % 2 == 1) dx / 2f else 0f
                    val cy = row * dy
                    val dist = hypot(cx - w / 2f, cy - h / 2f) / (w * 0.6f)
                    val a = 0.025f + 0.075f * (0.5f + 0.5f * sin((dist * 2f - t) * 2f * PI.toFloat()))
                    hex.reset()
                    for (k in 0 until 6) {
                        val ang = Math.toRadians(30.0 + 60.0 * k)
                        val px = cx + (cos(ang) * r * 0.92f).toFloat()
                        val py = cy + (sin(ang) * r * 0.92f).toFloat()
                        if (k == 0) hex.moveTo(px, py) else hex.lineTo(px, py)
                    }
                    hex.close()
                    drawPath(hex, accent.copy(alpha = a), style = Stroke(1.dp.toPx()))
                }
            }
        }
        4 -> {
            // ROG Cyber Circuit: jalur PCB cyber 45 derajat dengan data packet meluncur
            val line = 1.2.dp.toPx()
            val pcbCol = accent.copy(alpha = 0.14f)
            val glowCol = lerp(accent, Color.White, 0.6f)
            val traces = 12
            for (i in 0 until traces) {
                val yStart = (i + 1) * (h / (traces + 1f))
                val xBend1 = w * (0.15f + (i % 4) * 0.18f)
                val xBend2 = xBend1 + (if (i % 2 == 0) 60f else -60f)
                val yBend2 = yStart + (if (i % 2 == 0) 40f else -40f)
                val path = Path().apply {
                    moveTo(0f, yStart)
                    lineTo(xBend1, yStart)
                    lineTo(xBend2, yBend2)
                    lineTo(w, yBend2)
                }
                drawPath(path, pcbCol, style = Stroke(line, cap = StrokeCap.Round))
                drawCircle(accent.copy(alpha = 0.25f), 3.5.dp.toPx(), Offset(xBend1, yStart))
                drawCircle(accent.copy(alpha = 0.40f), 2.dp.toPx(), Offset(xBend1, yStart))

                // Data packet mengalir
                val packetT = ((t * 2.5f + i * 0.13f) % 1f)
                val px = when {
                    packetT < 0.35f -> (packetT / 0.35f) * xBend1
                    packetT < 0.60f -> xBend1 + ((packetT - 0.35f) / 0.25f) * (xBend2 - xBend1)
                    else -> xBend2 + ((packetT - 0.60f) / 0.40f) * (w - xBend2)
                }
                val py = when {
                    packetT < 0.35f -> yStart
                    packetT < 0.60f -> yStart + ((packetT - 0.35f) / 0.25f) * (yBend2 - yStart)
                    else -> yBend2
                }
                drawCircle(glowCol, 2.5.dp.toPx(), Offset(px, py))
                drawCircle(accent.copy(alpha = 0.35f), 6.dp.toPx(), Offset(px, py))
            }
        }
        5 -> {
            // RedMagic Tachyon Vortex: spiral gravitasi kosmik & cincin terdistorsi
            val cx = w * 0.5f
            val cy = h * 0.52f
            val maxR = minOf(w, h) * 0.48f
            for (ring in 1..6) {
                val rFrac = ring / 6f
                val r = maxR * rFrac
                val ringRot = (t * (7 - ring) * 45f) % 360f
                val ringA = 0.06f + 0.12f * (1f - rFrac)
                val p = Path()
                val spokes = 16
                for (s in 0 until spokes) {
                    val ang = Math.toRadians((ringRot + s * (360.0 / spokes)).toDouble())
                    val distortion = sin(ang * 3 + t * 4 * PI).toFloat() * 12f
                    val rx = cx + (cos(ang) * (r + distortion)).toFloat()
                    val ry = cy + (sin(ang) * (r * 0.45f + distortion * 0.45f)).toFloat()
                    if (s == 0) p.moveTo(rx, ry) else p.lineTo(rx, ry)
                }
                p.close()
                drawPath(p, accent.copy(alpha = ringA), style = Stroke(1.2.dp.toPx()))
            }
            // Debu pusaran melayang
            for (k in 0 until 24) {
                val angK = Math.toRadians(((k * 15f + t * 180f * (1 + k % 3)) % 360f).toDouble())
                val distK = maxR * (((k * 17) % 100) / 100f)
                val kx = cx + (cos(angK) * distK).toFloat()
                val ky = cy + (sin(angK) * distK * 0.42f).toFloat()
                val alphaK = 0.15f + 0.35f * (1f - distK / maxR)
                drawCircle(lerp(accent, Color.White, 0.4f).copy(alpha = alphaK), (1.2f + (k % 3) * 0.8f).dp.toPx(), Offset(kx, ky))
            }
        }
        6 -> {
            // Cyber Matrix Data Stream: hujan data biner berkecepatan dinamis
            val cols = 28
            val colW = w / cols
            for (c in 0 until cols) {
                val speed = 1f + (c % 4) * 0.7f
                val streamT = ((t * speed * 2f + (c * 7919 % 997) / 997f) % 1f)
                val x = c * colW + colW * 0.5f
                val yHead = streamT * (h + 120f) - 60f
                val streamLen = 80.dp.toPx() + (c % 3) * 30.dp.toPx()
                drawLine(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, accent.copy(alpha = 0.22f), lerp(accent, Color.White, 0.75f)),
                        startY = yHead - streamLen, endY = yHead
                    ),
                    Offset(x, (yHead - streamLen).coerceAtLeast(0f)),
                    Offset(x, yHead.coerceAtMost(h)),
                    strokeWidth = 1.8.dp.toPx(),
                    cap = StrokeCap.Round
                )
                if (yHead in 0f..h) {
                    drawCircle(Color.White.copy(alpha = 0.85f), 1.6.dp.toPx(), Offset(x, yHead))
                }
            }
        }
        7 -> {
            // Carbon Fiber Stealth: anyaman serat karbon mikro + aksen braket HUD
            val step = 14.dp.toPx()
            val carbonA = 0.045f
            val carbonB = 0.09f
            for (x in 0..(w / step).toInt()) {
                val px = x * step
                for (y in 0..(h / step).toInt()) {
                    val py = y * step
                    val even = (x + y) % 2 == 0
                    val col = if (even) accent.copy(alpha = carbonA) else Color.White.copy(alpha = carbonB)
                    drawLine(col, Offset(px, py), Offset(px + step, py + step), strokeWidth = 1.dp.toPx())
                }
            }
            // Garis retikel mecha di tengah
            val cx = w * 0.5f
            val cy = h * 0.52f
            drawLine(accent.copy(alpha = 0.12f), Offset(cx - 140.dp.toPx(), cy), Offset(cx + 140.dp.toPx(), cy), strokeWidth = 1.dp.toPx())
            drawLine(accent.copy(alpha = 0.12f), Offset(cx, cy - 80.dp.toPx()), Offset(cx, cy + 80.dp.toPx()), strokeWidth = 1.dp.toPx())
        }
        else -> Unit
    }
}

/** LED tepi atas dan bawah konsol. [fx]: 0 tetap, 1 bernapas, 2 berkedip, 3 mengalir, 4 pelangi, 5 aurora, 6 pulsa jantung. */
internal fun DrawScope.drawHubLed(look: HubLook, accent: Color, t: Float, pulse: Float) {
    if (!look.ledOn) return
    val c = if (look.ledColor == Color.Unspecified) accent else look.ledColor
    val w = size.width
    val h = size.height
    val bar = 3.dp.toPx()
    val glow = 32.dp.toPx()

    when (look.ledFx) {
        4 -> {
            // Rainbow / Spectrum Cycle
            val rainbowList = listOf(
                Color(0xFFFF0055), Color(0xFFFF7700), Color(0xFFFFEE00),
                Color(0xFF00FF66), Color(0xFF00EEFF), Color(0xFF0055FF),
                Color(0xFFAA00FF), Color(0xFFFF0055)
            )
            val offsetT = (t * 2f) % 1f
            val grad = Brush.horizontalGradient(
                colors = rainbowList,
                startX = -w * offsetT,
                endX = w * (2f - offsetT)
            )
            drawRect(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Transparent), startY = 0f, endY = glow),
                size = Size(w, glow)
            )
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.25f)), startY = h - glow, endY = h),
                topLeft = Offset(0f, h - glow), size = Size(w, glow)
            )
            drawRect(grad, size = Size(w, bar))
            drawRect(grad, topLeft = Offset(0f, h - bar), size = Size(w, bar))
        }
        5 -> {
            // Aurora Borealis Wave
            val auroraGrad = Brush.horizontalGradient(
                listOf(
                    c.copy(alpha = 0.2f),
                    Color(0xFF00FFD1).copy(alpha = 0.85f),
                    Color(0xFFBD00FF).copy(alpha = 0.85f),
                    c.copy(alpha = 0.2f)
                ),
                startX = w * (((t * 1.5f) % 1f) * 1.8f - 0.4f),
                endX = w * (((t * 1.5f) % 1f) * 1.8f + 0.6f)
            )
            drawRect(
                Brush.verticalGradient(listOf(Color(0xFF00FFD1).copy(alpha = 0.20f), Color.Transparent), startY = 0f, endY = glow),
                size = Size(w, glow)
            )
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Color(0xFFBD00FF).copy(alpha = 0.20f)), startY = h - glow, endY = h),
                topLeft = Offset(0f, h - glow), size = Size(w, glow)
            )
            drawRect(auroraGrad, size = Size(w, bar))
            drawRect(auroraGrad, topLeft = Offset(0f, h - bar), size = Size(w, bar))
        }
        6 -> {
            // Heartbeat / Performance Pulse: lub-dub spike
            val cycle = (t * 8f) % 1f
            val spike = when {
                cycle < 0.15f -> sin(cycle / 0.15f * PI.toFloat())
                cycle in 0.20f..0.35f -> sin((cycle - 0.20f) / 0.15f * PI.toFloat()) * 0.75f
                else -> 0.12f
            }
            val a = (0.15f + 0.85f * spike).coerceIn(0f, 1f)
            drawRect(
                Brush.verticalGradient(listOf(c.copy(alpha = 0.35f * a), Color.Transparent), startY = 0f, endY = glow),
                size = Size(w, glow)
            )
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, c.copy(alpha = 0.35f * a)), startY = h - glow, endY = h),
                topLeft = Offset(0f, h - glow), size = Size(w, glow)
            )
            drawRect(c.copy(alpha = a), size = Size(w, bar))
            drawRect(c.copy(alpha = a), topLeft = Offset(0f, h - bar), size = Size(w, bar))
        }
        else -> {
            val a = when (look.ledFx) {
                0 -> 1f
                1 -> 0.25f + 0.75f * pulse
                2 -> if ((t * 40f) % 1f < 0.5f) 1f else 0.08f
                else -> 0.4f
            }
            drawRect(
                Brush.verticalGradient(listOf(c.copy(alpha = 0.30f * a), Color.Transparent), startY = 0f, endY = glow),
                size = Size(w, glow)
            )
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, c.copy(alpha = 0.30f * a)), startY = h - glow, endY = h),
                topLeft = Offset(0f, h - glow), size = Size(w, glow)
            )
            if (look.ledFx == 3) {
                val cx = w * (((t * 8f) % 1f) * 1.6f - 0.3f)
                val br = Brush.horizontalGradient(
                    listOf(c.copy(alpha = 0.12f), c, c.copy(alpha = 0.12f)),
                    startX = cx - w * 0.2f, endX = cx + w * 0.2f
                )
                drawRect(br, size = Size(w, bar))
                drawRect(br, topLeft = Offset(0f, h - bar), size = Size(w, bar))
            } else {
                drawRect(c.copy(alpha = a), size = Size(w, bar))
                drawRect(c.copy(alpha = a), topLeft = Offset(0f, h - bar), size = Size(w, bar))
            }
        }
    }
}

/** Isi kolom halaman (Center/Control Panel) yang bisa digulir. */
@Composable
internal fun HubPage(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

/** Garis kosong kecil di bawah halaman supaya elemen terakhir tidak mepet tepi. */
@Composable
internal fun HubBottomSpace() {
    Spacer(Modifier.fillMaxWidth().height(6.dp))
}

@Composable
internal fun HubNote(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, lineHeight = 14.sp, modifier = modifier)
}
