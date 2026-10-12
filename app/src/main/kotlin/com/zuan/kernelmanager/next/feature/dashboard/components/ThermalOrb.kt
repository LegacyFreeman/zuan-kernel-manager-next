/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.dashboard.components

import com.zuan.kernelmanager.next.ui.components.zkmInfiniteFloat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.feature.dashboard.OverallBatteryInfo
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
//  THERMAL ORB — kartu Temperature generasi baru
//
//  Bukan lagi "lingkaran muter ganti warna". Sekarang:
//   • Gauge busur 270° dengan gradien termal (cyan → hijau → kuning → oranye → merah)
//     yang panjangnya = suhu sebenarnya (rentang 20–55 °C), dengan kepala bercahaya di ujungnya.
//   • "Heat core" di tengah: inti plasma + 3 gumpalan cahaya yang mengorbit, denyutnya
//     makin cepat saat makin panas.
//   • Partikel kontekstual: bara api naik saat panas (≥ 41 °C), kilau es saat dingin (< 31 °C).
//   • Tren suhu (▲/▼) dari beberapa sampel terakhir langsung di chip status.
//   • Disentuh → buka halaman Thermal.
// ─────────────────────────────────────────────────────────────────────────────

private const val GAUGE_MIN = 20f
private const val GAUGE_MAX = 55f
private const val GAUGE_SWEEP = 270f
private const val GAUGE_START = 135f

private val ThermalColorStops: List<Pair<Float, Color>> = listOf(
    24f to Color(0xFF22D3EE), // cyan  – dingin
    33f to Color(0xFF34D399), // hijau – normal
    39f to Color(0xFFFACC15), // kuning – hangat
    43f to Color(0xFFFB923C), // oranye – panas
    48f to Color(0xFFEF4444)  // merah – bahaya
)

private fun thermalColor(temp: Float): Color {
    val first = ThermalColorStops.first()
    if (temp <= first.first) return first.second
    for (i in 1 until ThermalColorStops.size) {
        val (t1, c1) = ThermalColorStops[i]
        if (temp <= t1) {
            val (t0, c0) = ThermalColorStops[i - 1]
            return lerp(c0, c1, ((temp - t0) / (t1 - t0)).coerceIn(0f, 1f))
        }
    }
    return ThermalColorStops.last().second
}

private fun gaugeNorm(temp: Float): Float = ((temp - GAUGE_MIN) / (GAUGE_MAX - GAUGE_MIN)).coerceIn(0f, 1f)

/** Posisi stop gradien sweep: busur dimulai dari 0 dan hanya mencakup 270° (= 0.75 lingkaran penuh). */
private val SweepStops: Array<Pair<Float, Color>> = run {
    val list = ArrayList<Pair<Float, Color>>()
    list.add(0f to ThermalColorStops.first().second)
    ThermalColorStops.forEach { (t, c) -> list.add(gaugeNorm(t) * 0.75f to c) }
    list.add(0.75f to ThermalColorStops.last().second)
    list.add(1f to ThermalColorStops.last().second)
    list.toTypedArray()
}

@Composable
fun TemperatureReactorCard(
    info: OverallBatteryInfo,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit = {}
) {
    val tempValue = remember(info.temp) {
        info.temp.replace(Regex("[^0-9.]"), "").toFloatOrNull() ?: 30f
    }

    // Gauge bergerak lembut (pegas), bukan loncat tiap sampel baru.
    val tempAnim by animateFloatAsState(
        targetValue = tempValue,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
        label = "tempAnim"
    )
    val accent = thermalColor(tempAnim)

    val statusText = when {
        tempValue < 30f -> stringResource(R.string.temp_freezing)
        tempValue < 36f -> stringResource(R.string.temp_cool)
        tempValue < 41f -> stringResource(R.string.temp_warm)
        tempValue < 45f -> stringResource(R.string.temp_hot)
        else -> stringResource(R.string.temp_overheat)
    }

    // Tren: bandingkan sampel terbaru dengan sampel tertua di jendela 8 sampel.
    val history = remember { mutableStateListOf<Float>() }
    LaunchedEffect(tempValue) {
        history.add(tempValue)
        while (history.size > 8) history.removeAt(0)
    }
    val trend = if (history.size >= 3) history.last() - history.first() else 0f
    val trendArrow = when {
        trend > 0.25f -> " ▲"
        trend < -0.25f -> " ▼"
        else -> ""
    }

    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(0.dp),
        border = null
    ) {
        Row(
            modifier = Modifier
                .padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.temp_temperature),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.6f),
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "%.1f".format(java.util.Locale.US, tempValue),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
                        maxLines = 1
                    )
                    Text(
                        text = "°C",
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor.copy(alpha = 0.55f),
                        modifier = Modifier.padding(start = 2.dp, bottom = 5.dp),
                        maxLines = 1
                    )
                }
                Spacer(Modifier.height(4.dp))
                Surface(color = accent.copy(alpha = 0.16f), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = statusText + trendArrow,
                        color = accent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            ThermalOrb(
                temp = tempAnim,
                color = accent,
                trackColor = textColor,
                modifier = Modifier.size(62.dp)
            )
        }
    }
}

@Composable
private fun ThermalOrb(
    temp: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier
) {

    // Satu siklus penuh 12 detik; semua gerak orbit memakai kelipatan BULAT darinya
    // supaya loop-nya mulus (tidak ada loncatan di titik ulang).
    val t by zkmInfiniteFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing)),
        label = "orbitT"
    )

    // Denyut: makin panas makin cepat.
    val pulseMs = when {
        temp > 45f -> 700
        temp > 41f -> 1100
        temp > 36f -> 1700
        else -> 2600
    }
    val pulse by zkmInfiniteFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(pulseMs, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Canvas(modifier = modifier) {
        val s = size.minDimension
        val c = Offset(size.width / 2f, size.height / 2f)
        val stroke = s * 0.085f
        val rArc = s / 2f - stroke
        val arcTopLeft = Offset(c.x - rArc, c.y - rArc)
        val arcSize = Size(rArc * 2f, rArc * 2f)
        val norm = gaugeNorm(temp)
        val twoPi = (2.0 * PI).toFloat()

        // 0. Halo ambient di belakang orb
        val haloR = s * 0.62f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.20f + 0.10f * pulse), Color.Transparent),
                center = c,
                radius = haloR
            ),
            radius = haloR,
            center = c
        )

        // 1. Track gauge
        drawArc(
            color = trackColor.copy(alpha = 0.10f),
            startAngle = GAUGE_START,
            sweepAngle = GAUGE_SWEEP,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        // 2. Tick halus tiap 27° (11 titik) di sisi dalam track
        for (i in 0..10) {
            val a = Math.toRadians((GAUGE_START + i * 27f).toDouble())
            val rr = rArc - stroke * 1.35f
            drawCircle(
                color = trackColor.copy(alpha = 0.14f),
                radius = stroke * 0.14f,
                center = Offset(c.x + rr * cos(a).toFloat(), c.y + rr * sin(a).toFloat())
            )
        }

        // 3. Busur progres bergradien termal
        val sweep = GAUGE_SWEEP * norm
        if (sweep > 1f) {
            rotate(degrees = GAUGE_START, pivot = c) {
                drawArc(
                    brush = Brush.sweepGradient(*SweepStops, center = c),
                    startAngle = 0f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }

        // 4. Kepala bercahaya di ujung busur
        val headAngle = Math.toRadians((GAUGE_START + sweep).toDouble())
        val head = Offset(c.x + rArc * cos(headAngle).toFloat(), c.y + rArc * sin(headAngle).toFloat())
        val glowR = stroke * (2.4f + 0.5f * pulse)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.60f), Color.Transparent),
                center = head,
                radius = glowR
            ),
            radius = glowR,
            center = head
        )
        drawCircle(color = Color.White, radius = stroke * 0.42f, center = head)

        // 5. Heat core: inti plasma
        val coreR = rArc * 0.62f
        val coreBreath = coreR * (1f + 0.08f * pulse)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.35f), Color.Transparent),
                center = c,
                radius = coreBreath
            ),
            radius = coreBreath,
            center = c
        )

        // 6. Tiga gumpalan cahaya mengorbit (kelipatan bulat 1, -2, 3 → loop mulus)
        val bright = lerp(color, Color.White, 0.55f)
        for (i in 0 until 3) {
            val dir = if (i == 1) -1f else 1f
            val a = t * twoPi * (i + 1) * dir
            val orbit = coreR * (0.30f + 0.10f * i)
            val p = Offset(c.x + cos(a) * orbit, c.y + sin(a) * orbit * 0.9f)
            val br = coreR * (0.40f - 0.05f * i)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(bright.copy(alpha = 0.75f), color.copy(alpha = 0f)),
                    center = p,
                    radius = br
                ),
                radius = br,
                center = p
            )
        }

        // 7. Partikel kontekstual
        if (temp >= 41f) {
            // Bara api naik dari inti
            val n = if (temp >= 45f) 7 else 5
            for (i in 0 until n) {
                val ph = (t * (3 + i % 3) + i * 0.37f) % 1f
                val x = c.x + sin(i * 2.1f) * coreR * 0.9f + sin(ph * twoPi * 2f) * coreR * 0.12f
                val y = c.y + coreR * 0.9f - ph * rArc * 1.7f
                val fadeIn = (ph / 0.15f).coerceAtMost(1f)
                val alpha = (1f - ph) * fadeIn
                drawCircle(
                    color = Color(0xFFFFE08A).copy(alpha = alpha * 0.9f),
                    radius = stroke * 0.30f * (1f - ph * 0.5f),
                    center = Offset(x, y)
                )
            }
        } else if (temp < 31f) {
            // Kilau es berkedip (bintang 4 sudut)
            for (i in 0 until 5) {
                val ph = (t * (2 + i % 2) + i * 0.21f) % 1f
                val tw = abs(sin(ph * PI.toFloat()))
                val ang = i * 1.256f + 0.4f
                val rr = coreR * (0.35f + 0.12f * i)
                val p = Offset(c.x + cos(ang) * rr, c.y + sin(ang) * rr)
                val len = stroke * 0.9f * tw
                val col = Color.White.copy(alpha = 0.85f * tw)
                drawLine(col, Offset(p.x - len, p.y), Offset(p.x + len, p.y), strokeWidth = stroke * 0.16f, cap = StrokeCap.Round)
                drawLine(col, Offset(p.x, p.y - len), Offset(p.x, p.y + len), strokeWidth = stroke * 0.16f, cap = StrokeCap.Round)
            }
        }

        // 8. Kilap spekular kecil supaya inti terasa seperti kaca/cairan
        drawCircle(
            color = Color.White.copy(alpha = 0.45f),
            radius = coreR * 0.12f,
            center = Offset(c.x - coreR * 0.32f, c.y - coreR * 0.36f)
        )
    }
}
