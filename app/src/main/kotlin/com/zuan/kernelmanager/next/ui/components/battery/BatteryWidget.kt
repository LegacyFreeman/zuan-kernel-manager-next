/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.zuan.kernelmanager.next.ui.components.battery

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.feature.dashboard.OverallBatteryInfo
import com.zuan.kernelmanager.next.ui.components.BlurredAlertDialog
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
//  ORBIT POWER v2
//
//  Konsep: baterai = sebuah orbit.
//   • Cincin orbit miring di sekeliling astronot = indikator level (busur terang makin panjang
//     sesuai persen), dengan "kepala" bercahaya di ujungnya. Satelit kecil mengitari cincin.
//   • Planet di bawah dengan atmosfer; ketinggian horizon mengikuti level.
//   • Charging = energi MENGALIR MASUK ke astronot: partikel spiral dari tepi cincin ke inti,
//     gelombang denyut keluar dari astronot, kilau yang berlari di sepanjang busur, jetpack
//     menyala, dan kolom cahaya naik dari planet. (Tidak ada lagi garis-garis jatuh seperti hujan.)
//   • Idle = melayang tenang + meteor langka.
// ─────────────────────────────────────────────────────────────────────────────

private val AccentCyan = Color(0xFF38BDF8)
private val AccentMint = Color(0xFF34D399)
private val AccentAmber = Color(0xFFFBBF24)
private val AccentRed = Color(0xFFF87171)

private fun levelAccent(level: Float, chargeFade: Float): Color {
    val base = when {
        level < 0.10f -> AccentRed
        level < 0.20f -> lerp(AccentRed, AccentAmber, (level - 0.10f) / 0.10f)
        level < 0.30f -> lerp(AccentAmber, AccentCyan, (level - 0.20f) / 0.10f)
        else -> AccentCyan
    }
    return lerp(base, AccentMint, chargeFade * 0.65f)
}

/** Geometri cincin orbit (satuan px). */
private class RingGeo(val cx: Float, val cy: Float, val rx: Float, val ry: Float, val tilt: Float)

@Composable
fun BatteryWeatherCard(
    info: OverallBatteryInfo,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("zkm_orbit_power", Context.MODE_PRIVATE) }
    val engine = remember { BatteryWeatherEngine() }

    val timePhase = remember { BatteryTimeSystem.getCurrentPhase() }
    val theme = remember(timePhase) { BatteryTimeSystem.getTheme(timePhase) }

    val statusLower = info.status.lowercase()
    val isCharging = statusLower.contains("charging") &&
        !statusLower.contains("discharging") &&
        !statusLower.contains("not")

    val levelTarget = remember(info.level) {
        ((info.level.replace("%", "").trim().toIntOrNull() ?: 50) / 100f).coerceIn(0f, 1f)
    }
    // Level & intensitas dianimasikan halus (tidak loncat saat angka berubah)
    val level by animateFloatAsState(levelTarget, tween(1200, easing = FastOutSlowInEasing), label = "orbitLevel")
    val levelShown by animateIntAsState((levelTarget * 100f).roundToInt(), tween(800), label = "orbitPercent")
    val intensityTarget = if (isCharging) (abs(info.currentNow) / 2500f).coerceIn(0.35f, 1f) else 0f
    val intensity by animateFloatAsState(intensityTarget, tween(900), label = "orbitPower")

    // Pilihan animasi sekarang diingat (sebelumnya reset tiap aplikasi dibuka)
    var isAnimEnabled by remember { mutableStateOf(prefs.getBoolean("anim_enabled", false)) }
    var showAnimDialog by rememberSaveable { mutableStateOf(false) }

    val chargingNow by rememberUpdatedState(isCharging)
    val intensityNow by rememberUpdatedState(intensity)

    // Loop frame berbasis dt nyata. Dibatasi ±60 fps supaya layar 120 Hz tidak boros GPU.
    LaunchedEffect(isAnimEnabled) {
        if (!isAnimEnabled) return@LaunchedEffect
        var last = 0L
        var acc = 0f
        while (isActive) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last != 0L) acc += ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                if (acc >= 0.0155f) {
                    engine.update(acc, chargingNow, intensityNow)
                    acc = 0f
                }
            }
        }
    }

    val accentUi = levelAccent(level, if (isCharging) 1f else 0f)

    Card(
        modifier = modifier.clickable { showAnimDialog = true },
        elevation = CardDefaults.cardElevation(0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                @Suppress("UNUSED_VARIABLE")
                val frame = engine.tick // langganan redraw per frame (tanpa recomposition)

                val w = size.width
                val h = size.height
                if (w <= 1f || h <= 1f) return@Canvas
                val u = density

                // Saat animasi dijeda, tampilkan frame statis yang tetap mencerminkan status charging
                val cf = if (isAnimEnabled) engine.chargeFade else if (isCharging) 1f else 0f
                val time = engine.time
                val accent = levelAccent(level, cf)

                val geo = RingGeo(cx = w * 0.5f, cy = h * 0.40f, rx = w * 0.40f, ry = w * 0.40f * 0.34f, tilt = -14f)

                // A. Ruang angkasa
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(theme.spaceTop, theme.spaceBottom),
                        startY = 0f,
                        endY = h
                    )
                )
                drawNebula(theme, time, w, h)
                drawStars(engine, theme.starAlpha, time, w, h, u)
                if (cf < 0.2f) drawMeteor(engine, w, h, u)

                // B. Planet + atmosfer (+ kolom energi naik saat charging)
                drawPlanet(theme.planetColor, theme.planetGlow, level, accent, cf, time, w, h, u)

                // C. Sisi belakang cincin, partikel energi, gelombang denyut
                drawOrbitRing(geo, level, accent, cf, time, front = false, engine = engine, u = u)
                drawChargeEffects(engine, geo, accent, cf, time, u)

                // D. Astronot (dengan aura & jetpack)
                val ax = w * (0.5f + engine.astroX)
                val ay = h * (0.40f + engine.astroY)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.14f + 0.20f * cf), Color.Transparent),
                        center = Offset(ax, ay),
                        radius = 46f * u
                    ),
                    radius = 46f * u,
                    center = Offset(ax, ay)
                )
                BatteryArtAssets.run {
                    drawAstronaut(
                        x = ax,
                        y = ay,
                        rotation = engine.astroRot,
                        scale = 0.80f * u,
                        thrust = cf,
                        accent = accent
                    )
                }

                // E. Sisi depan cincin (melewati di depan astronot → kesan 3D)
                drawOrbitRing(geo, level, accent, cf, time, front = true, engine = engine, u = u)
            }

            // Teks selalu putih (di luar angkasa)
            val textColor = Color.White
            val subTextColor = Color.White.copy(alpha = 0.72f)

            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 20.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(28.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.lp_orbit_power),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = subTextColor,
                        maxLines = 1
                    )
                    // Indikator animasi: ikon kecil (menggantikan teks "Anim Paused" yang bertumpuk)
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isAnimEnabled) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Column {
                    Text(
                        "$levelShown%",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isCharging) {
                            Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = null,
                                tint = accentUi,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                        }
                        Text(
                            info.status,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = subTextColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }

    // Dialog kontrol animasi
    if (showAnimDialog) {
        BlurredAlertDialog(
            onDismissRequest = { showAnimDialog = false },
            title = { Text(stringResource(R.string.lp_interstellar_animation), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.lp_the_astronaut_and_warp_speed_animations_require_gp)) },
            confirmButton = {
                Button(onClick = {
                    isAnimEnabled = true
                    prefs.edit().putBoolean("anim_enabled", true).apply()
                    showAnimDialog = false
                }) { Text(stringResource(R.string.lp_play_animation)) }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    isAnimEnabled = false
                    prefs.edit().putBoolean("anim_enabled", false).apply()
                    showAnimDialog = false
                }) { Text(stringResource(R.string.lp_freeze)) }
            }
        )
    }
}

// ───────────────────────────── DRAW HELPERS ─────────────────────────────

private fun DrawScope.drawNebula(theme: SpaceTheme, time: Float, w: Float, h: Float) {
    val p1 = Offset(w * (0.25f + 0.05f * sin(time * 0.13f)), h * (0.28f + 0.03f * cos(time * 0.11f)))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(theme.planetGlow.copy(alpha = 0.22f), Color.Transparent),
            center = p1,
            radius = w * 0.75f
        ),
        radius = w * 0.75f,
        center = p1
    )
    val p2 = Offset(w * (0.80f + 0.04f * cos(time * 0.09f)), h * (0.55f + 0.03f * sin(time * 0.17f)))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(lerp(theme.spaceBottom, Color.White, 0.25f).copy(alpha = 0.20f), Color.Transparent),
            center = p2,
            radius = w * 0.6f
        ),
        radius = w * 0.6f,
        center = p2
    )
}

private fun DrawScope.drawStars(engine: BatteryWeatherEngine, starAlpha: Float, time: Float, w: Float, h: Float, u: Float) {
    for (st in engine.stars) {
        // Parallax: bintang dekat bergeser sedikit lebih cepat
        val x = (((st.x - time * 0.006f * st.depth) % 1f) + 1f) % 1f
        val tw = 0.5f + 0.5f * sin(time * st.twinkleSpeed + st.phase)
        val alpha = ((0.25f + 0.75f * tw) * starAlpha * (0.5f + 0.5f * st.depth)).coerceIn(0f, 1f)
        drawCircle(
            color = Color.White.copy(alpha = alpha),
            radius = st.size * u,
            center = Offset(x * w, st.y * h)
        )
    }
}

private fun DrawScope.drawMeteor(engine: BatteryWeatherEngine, w: Float, h: Float, u: Float) {
    val m = engine.meteor ?: return
    val dirX = -0.82f
    val dirY = 0.57f
    val dist = m.progress * w * 0.55f
    val head = Offset(m.x * w + dirX * dist, m.y * h + dirY * dist)
    val tail = Offset(head.x - dirX * m.len * u, head.y - dirY * m.len * u)
    val a = sin(m.progress * PI.toFloat()).coerceIn(0f, 1f)
    drawLine(
        brush = Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.9f * a)),
            start = tail,
            end = head
        ),
        start = tail,
        end = head,
        strokeWidth = 1.6f * u,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawPlanet(
    planetColor: Color,
    planetGlow: Color,
    level: Float,
    accent: Color,
    cf: Float,
    time: Float,
    w: Float,
    h: Float,
    u: Float
) {
    // Horizon naik saat baterai makin penuh
    val horizonY = h * (0.90f - 0.10f * level)
    val r = w * 1.6f
    val center = Offset(w / 2f, horizonY + r)
    val pulse = 0.5f + 0.5f * sin(time * 2.4f)
    val rim = lerp(planetGlow, accent, 0.35f * cf)

    // Kolom energi naik dari planet ke astronot saat charging
    if (cf > 0.02f) {
        val top = horizonY - h * 0.34f
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, accent.copy(alpha = (0.10f + 0.12f * pulse) * cf)),
                startY = top,
                endY = horizonY
            ),
            topLeft = Offset(0f, top),
            size = Size(w, horizonY - top)
        )
    }

    // Atmosfer (cincin cahaya tipis di sekeliling planet)
    val glowR = r * 1.10f
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.Transparent,
            0.88f to Color.Transparent,
            0.909f to rim.copy(alpha = (0.55f + 0.25f * cf * pulse).coerceAtMost(1f)),
            1f to Color.Transparent,
            center = center,
            radius = glowR
        ),
        radius = glowR,
        center = center
    )

    // Badan planet dengan shading (terang di bagian atas)
    drawCircle(
        brush = Brush.radialGradient(
            0f to lerp(planetColor, Color.White, 0.18f),
            0.55f to planetColor,
            1f to lerp(planetColor, Color.Black, 0.45f),
            center = Offset(center.x, horizonY + r * 0.2f),
            radius = r * 1.05f
        ),
        radius = r,
        center = center
    )

    // Garis horizon tipis yang menyala
    drawCircle(
        color = rim.copy(alpha = 0.65f),
        radius = r,
        center = center,
        style = Stroke(width = 1.4f * u)
    )
}

/**
 * Cincin orbit miring. Dipanggil dua kali: sisi belakang (di atas sumbu cincin, di belakang
 * astronot) dan sisi depan (melewati di depan astronot).
 */
private fun DrawScope.drawOrbitRing(
    geo: RingGeo,
    level: Float,
    accent: Color,
    cf: Float,
    time: Float,
    front: Boolean,
    engine: BatteryWeatherEngine,
    u: Float
) {
    withTransform({
        translate(geo.cx, geo.cy)
        rotate(geo.tilt, Offset.Zero)
    }) {
        val clipTop = if (front) 0f else -geo.ry * 3f
        val clipBottom = if (front) geo.ry * 3f else 0f
        val topLeft = Offset(-geo.rx, -geo.ry)
        val ovalSize = Size(geo.rx * 2f, geo.ry * 2f)
        val sweep = 360f * level

        clipRect(left = -geo.rx * 1.5f, top = clipTop, right = geo.rx * 1.5f, bottom = clipBottom) {
            // Track tipis
            drawArc(
                color = Color.White.copy(alpha = 0.16f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = ovalSize,
                style = Stroke(width = 1.6f * u)
            )
            if (sweep > 0.5f) {
                // Glow lalu busur progres
                drawArc(
                    color = accent.copy(alpha = 0.20f),
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = ovalSize,
                    style = Stroke(width = 7f * u, cap = StrokeCap.Round)
                )
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = ovalSize,
                    style = Stroke(width = 2.8f * u, cap = StrokeCap.Round)
                )
                // Kilau berlari di sepanjang busur saat charging
                if (cf > 0.02f) {
                    val span = (sweep - 20f).coerceAtLeast(0f)
                    val frac = (time * 0.45f) % 1f
                    drawArc(
                        color = Color.White.copy(alpha = 0.85f * cf),
                        startAngle = -90f + span * frac,
                        sweepAngle = minOf(20f, sweep),
                        useCenter = false,
                        topLeft = topLeft,
                        size = ovalSize,
                        style = Stroke(width = 3.4f * u, cap = StrokeCap.Round)
                    )
                }
            }
        }

        // Kepala bercahaya di ujung busur (digambar di sisi yang sesuai posisinya)
        val th = Math.toRadians((-90f + sweep).toDouble())
        val hx = (geo.rx * cos(th)).toFloat()
        val hy = (geo.ry * sin(th)).toFloat()
        if ((hy >= 0f) == front) {
            val gr = 9f * u
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.70f), Color.Transparent),
                    center = Offset(hx, hy),
                    radius = gr
                ),
                radius = gr,
                center = Offset(hx, hy)
            )
            drawCircle(Color.White, radius = 2.6f * u, center = Offset(hx, hy))
        }

        // Satelit kecil mengitari cincin
        val phi = engine.orbitPhase
        val sx = geo.rx * cos(phi)
        val sy = geo.ry * sin(phi)
        if ((sy >= 0f) == front) {
            BatteryArtAssets.run { drawSatellite(sx, sy, 0.20f * u) }
        }
    }
}

/** Efek charging di belakang astronot: gelombang denyut + partikel energi spiral masuk. */
private fun DrawScope.drawChargeEffects(
    engine: BatteryWeatherEngine,
    geo: RingGeo,
    accent: Color,
    cf: Float,
    time: Float,
    u: Float
) {
    if (cf <= 0.01f) return
    withTransform({
        translate(geo.cx, geo.cy)
        rotate(geo.tilt, Offset.Zero)
    }) {
        // Dua gelombang denyut bergantian, mengembang dari astronot ke luar
        for (k in 0..1) {
            val ph = (time * 0.55f + k * 0.5f) % 1f
            val sc = 0.15f + 0.95f * ph
            val a = (1f - ph) * (1f - ph) * 0.35f * cf
            drawOval(
                color = accent.copy(alpha = a),
                topLeft = Offset(-geo.rx * sc, -geo.ry * sc),
                size = Size(geo.rx * 2f * sc, geo.ry * 2f * sc),
                style = Stroke(width = 1.6f * u)
            )
        }

        // Partikel energi: spiral dari tepi cincin menuju astronot
        for (m in engine.motes) {
            val fadeIn = ((1f - m.radius) / 0.12f).coerceIn(0f, 1f)
            val fadeOut = (m.radius / 0.15f).coerceIn(0f, 1f)
            val alpha = cf * fadeIn * fadeOut * 0.95f
            if (alpha <= 0.01f) continue

            val px = geo.rx * 1.12f * m.radius * cos(m.angle)
            val py = geo.ry * 1.12f * m.radius * sin(m.angle)
            // Ekor pendek di belakang arah gerak
            val tr = m.radius + 0.08f
            val ta = m.angle - 0.18f * (if (m.spin >= 0f) 1f else -1f)
            val tx = geo.rx * 1.12f * tr * cos(ta)
            val ty = geo.ry * 1.12f * tr * sin(ta)
            drawLine(
                color = accent.copy(alpha = alpha * 0.5f),
                start = Offset(tx, ty),
                end = Offset(px, py),
                strokeWidth = m.size * 0.8f * u,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = lerp(accent, Color.White, 0.5f).copy(alpha = alpha),
                radius = m.size * u,
                center = Offset(px, py)
            )
        }
    }
}
