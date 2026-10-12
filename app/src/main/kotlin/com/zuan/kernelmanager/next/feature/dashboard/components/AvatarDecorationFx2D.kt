/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.dashboard.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/*
 * ===========================================================================
 *  Dekorasi avatar 2D tambahan — dirancang oleh Claude Sonnet 5.5
 *  Semua digambar prosedural (tanpa aset) dan digerakkan oleh waktu [t] (detik).
 *  Radius efektif ring ≈ r, tebal dasar ≈ w; elemen luar dijaga <= ±0.5 ukuran.
 * ===========================================================================
 */

internal const val DECO_TAU = 6.2831855f

internal fun decoRad(deg: Float): Float = deg * (PI.toFloat() / 180f)

internal fun decoPt(c: Offset, rad: Float, angle: Float): Offset =
    Offset(c.x + rad * cos(angle), c.y + rad * sin(angle))

internal fun decoFract(x: Float): Float = x - floor(x)

/** Acak deterministik 0..1 dari bilangan bulat (supaya efek "random" stabil per-frame). */
internal fun decoHash(n: Int): Float {
    val v = sin(n * 12.9898f) * 43758.547f
    return v - floor(v)
}

/** Bintang 4 titik (kilau). */
internal fun DrawScope.decoSparkle(p: Offset, s: Float, color: Color, alpha: Float = 1f) {
    val path = Path().apply {
        moveTo(p.x, p.y - s)
        quadraticBezierTo(p.x, p.y, p.x + s, p.y)
        quadraticBezierTo(p.x, p.y, p.x, p.y + s)
        quadraticBezierTo(p.x, p.y, p.x - s, p.y)
        quadraticBezierTo(p.x, p.y, p.x, p.y - s)
        close()
    }
    drawPath(path, color, alpha = alpha)
}

internal fun DrawScope.ringArc(
    color: Color, start: Float, sweep: Float, c: Offset, r: Float, st: Stroke, alpha: Float = 1f
) {
    drawArc(
        color = color,
        startAngle = start,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(c.x - r, c.y - r),
        size = Size(r * 2f, r * 2f),
        alpha = alpha,
        style = st
    )
}

/** Lingkaran bergelombang (radius dimodulasi sinus). */
internal fun decoWavyPath(c: Offset, r: Float, amp: Float, freq: Int, phase: Float, n: Int = 84): Path {
    val path = Path()
    for (i in 0..n) {
        val a = DECO_TAU * i / n
        val rr = r + amp * sin(freq * a + phase)
        val x = c.x + rr * cos(a)
        val y = c.y + rr * sin(a)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

internal fun decoBump(x: Float, center: Float, width: Float): Float {
    val d = (x - center) / width
    return exp(-d * d)
}

internal fun DrawScope.decoHeart(p: Offset, s: Float, color: Color, alpha: Float) {
    val path = Path().apply {
        moveTo(p.x, p.y + s * 0.9f)
        cubicTo(p.x - s * 1.6f, p.y - s * 0.1f, p.x - s * 0.9f, p.y - s * 1.2f, p.x, p.y - s * 0.4f)
        cubicTo(p.x + s * 0.9f, p.y - s * 1.2f, p.x + s * 1.6f, p.y - s * 0.1f, p.x, p.y + s * 0.9f)
        close()
    }
    drawPath(path, color, alpha = alpha)
}

// ---------------------------------------------------------------------------
// 2D-1: AURORA VEIL — tirai aurora: 3 pita bergelombang berwarna hijau/cyan/ungu/pink
// ---------------------------------------------------------------------------
internal fun DrawScope.drawAurora(c: Offset, r: Float, w: Float, t: Float) {
    val cols = listOf(
        Color(0xFF00FFA3), Color(0xFF00D4FF), Color(0xFF8E7CFF), Color(0xFFFF6BD6), Color(0xFF00FFA3)
    )
    val pulse = decoPulse(t)
    drawCircle(Color(0xFF00FFC8).copy(alpha = 0.10f + 0.10f * pulse), r + w * 0.6f, c, style = Stroke(w * 2.6f))
    for (k in 0 until 3) {
        val path = decoWavyPath(
            c, r + (k - 1) * w * 0.5f, w * (0.60f - 0.12f * k), 3 + k, t * (1.2f + 0.45f * k) + k * 2f
        )
        rotate(t * (38f - 14f * k) + k * 70f, c) {
            drawPath(
                path, Brush.sweepGradient(cols, c),
                alpha = 0.9f - 0.2f * k,
                style = Stroke(w * (0.8f - 0.16f * k), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 2D-2: CYBER HUD — ring HUD: tick marks, scanner berputar, busur segmen, glitch RGB
// ---------------------------------------------------------------------------
internal fun DrawScope.drawCyberHud(c: Offset, r: Float, w: Float, t: Float) {
    val cyan = Color(0xFF00F0FF)
    val yellow = Color(0xFFFFE600)
    val magenta = Color(0xFFFF2BD6)
    val flick = if (decoFract(t * 3.1f) > 0.93f) 0.45f else 1f

    drawCircle(cyan.copy(alpha = 0.22f), r, c, style = Stroke(w * 0.5f))

    // tick marks luar (berputar pelan)
    rotate(t * 12f, c) {
        for (i in 0 until 60) {
            val a = DECO_TAU * i / 60f
            val major = i % 5 == 0
            val r1 = r + w * (if (major) 1.15f else 1.3f)
            val r2 = r + w * 1.6f
            drawLine(
                cyan.copy(alpha = if (major) 0.95f else 0.5f),
                decoPt(c, r1, a), decoPt(c, r2, a),
                strokeWidth = w * (if (major) 0.2f else 0.12f)
            )
        }
    }
    // scanner: ekor menyapu + kepala terang
    rotate(t * 140f, c) {
        drawCircle(
            Brush.sweepGradient(
                0f to Color.Transparent, 0.72f to Color.Transparent, 1f to cyan.copy(alpha = 0.95f),
                center = c
            ),
            r, c, style = Stroke(w * 1.0f)
        )
    }
    // busur segmen berlawanan arah
    val st = Stroke(w * 0.35f)
    rotate(-t * 45f, c) {
        ringArc(yellow, 30f, 60f, c, r + w * 1.05f, st, flick)
        ringArc(magenta, 200f, 22f, c, r + w * 1.05f, st, flick)
        ringArc(cyan, 250f, 70f, c, r + w * 1.05f, st, 0.7f)
    }
    // glitch: salinan merah/cyan bergeser sesaat
    if (decoFract(t / 2.4f) < 0.07f) {
        val d = w * 0.5f
        drawCircle(Color.Red.copy(alpha = 0.55f), r, Offset(c.x - d, c.y), style = Stroke(w * 0.5f))
        drawCircle(cyan.copy(alpha = 0.55f), r, Offset(c.x + d, c.y), style = Stroke(w * 0.5f))
    }
}

// ---------------------------------------------------------------------------
// 2D-3: ELECTRIC STORM — ring listrik bergetar + petir menyambar acak
// ---------------------------------------------------------------------------
internal fun DrawScope.drawElectricStorm(c: Offset, r: Float, w: Float, t: Float) {
    val core = Color(0xFFE8FBFF)
    val blue = Color(0xFF3D8BFF)
    val violet = Color(0xFF7C4DFF)
    val seed = floor(t * 12f).toInt()
    val pulse = decoPulse(t)

    drawCircle(blue.copy(alpha = 0.16f + 0.14f * pulse), r + w * 0.6f, c, style = Stroke(w * 2.4f))

    val ringPath = Path()
    val n = 60
    for (i in 0..n) {
        val a = DECO_TAU * i / n
        val jitter = (decoHash(seed * 131 + i) - 0.5f) * w * 0.7f
        val p = decoPt(c, r + jitter, a)
        if (i == 0) ringPath.moveTo(p.x, p.y) else ringPath.lineTo(p.x, p.y)
    }
    ringPath.close()
    drawPath(ringPath, blue, alpha = 0.9f, style = Stroke(w * 0.95f, join = StrokeJoin.Round))
    drawPath(ringPath, core, alpha = 0.95f, style = Stroke(w * 0.35f, join = StrokeJoin.Round))

    // petir
    for (k in 0 until 4) {
        val base = seed + k * 7
        if (decoHash(base * 17 + 3) < 0.35f) continue
        val a0 = DECO_TAU * decoHash(base * 31 + k)
        val start = decoPt(c, r + w * 0.3f, a0)
        val bolt = Path().apply { moveTo(start.x, start.y) }
        var ang = a0
        for (s in 1..4) {
            ang += (decoHash(base * 53 + s * 11 + k) - 0.5f) * 0.35f
            val rr = r + w * (0.3f + 0.33f * s) + (decoHash(base * 7 + s + k * 19) - 0.5f) * w * 0.5f
            val q = decoPt(c, rr, ang)
            bolt.lineTo(q.x, q.y)
        }
        drawPath(bolt, violet, alpha = 0.55f, style = Stroke(w * 0.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(bolt, core, style = Stroke(w * 0.22f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// ---------------------------------------------------------------------------
// 2D-4: OCEAN WAVE — ombak air berlapis + gelembung naik
// ---------------------------------------------------------------------------
internal fun DrawScope.drawOceanWave(c: Offset, r: Float, w: Float, t: Float) {
    val light = Color(0xFF9DF3FF)
    val mid = Color(0xFF29B6F6)
    val deep = Color(0xFF0D47A1)
    val grad = Brush.linearGradient(
        listOf(light, mid, deep), Offset(c.x, c.y - r), Offset(c.x, c.y + r * 1.2f)
    )
    drawCircle(mid.copy(alpha = 0.14f), r + w * 0.5f, c, style = Stroke(w * 2.2f))

    val back = decoWavyPath(c, r + w * 0.15f, w * 0.55f, 6, -t * 2.4f)
    drawPath(back, deep, alpha = 0.55f, style = Stroke(w * 0.9f))
    val front = decoWavyPath(c, r, w * 0.5f, 5, t * 3.0f)
    drawPath(front, grad, style = Stroke(w * 0.95f, join = StrokeJoin.Round))

    // gelembung
    for (i in 0 until 6) {
        val a = DECO_TAU * (i / 6f) + t * (0.35f + 0.05f * i)
        val rise = decoFract(t * 0.4f + i * 0.37f)
        val p = decoPt(c, r + w * (0.5f + 1.1f * rise), a)
        val s = w * (0.25f + 0.18f * (i % 3))
        drawCircle(light.copy(alpha = 0.25f * (1f - rise)), s, p)
        drawCircle(Color.White.copy(alpha = 0.7f * (1f - rise)), s, p, style = Stroke(w * 0.1f))
    }
}

// ---------------------------------------------------------------------------
// 2D-5: LOVE PULSE — ring pink berdetak (lub-dub) + hati mengorbit
// ---------------------------------------------------------------------------
internal fun DrawScope.drawLovePulse(c: Offset, r: Float, w: Float, t: Float) {
    val ph = decoFract(t / 1.15f)
    val beat = max(decoBump(ph, 0.12f, 0.07f), 0.65f * decoBump(ph, 0.32f, 0.08f))
    val rose = Color(0xFFFF5C8A)
    val red = Color(0xFFFF1744)
    val blush = Color(0xFFFFC1D6)

    drawCircle(rose.copy(alpha = 0.14f + 0.30f * beat), r + w, c, style = Stroke(w * (1.8f + 1.0f * beat)))
    rotate(t * 25f, c) {
        drawCircle(
            Brush.sweepGradient(listOf(rose, blush, red, rose), c),
            r, c, style = Stroke(w * (0.85f + 0.3f * beat))
        )
    }
    for (i in 0 until 6) {
        val a = DECO_TAU * i / 6f - t * 0.6f
        val hp = decoPt(c, r + w * 1.05f, a)
        decoHeart(hp, w * (0.55f + 0.3f * beat), if (i % 2 == 0) red else rose, 0.95f)
    }
}

// ---------------------------------------------------------------------------
// 2D-6: COMET TRAIL — tiga komet beda kecepatan + bintang berkedip
// ---------------------------------------------------------------------------
internal fun DrawScope.drawCometTrail(c: Offset, r: Float, w: Float, t: Float) {
    val gold = Color(0xFFFFE08A)
    val cyan = Color(0xFF7DF9FF)
    val pink = Color(0xFFFF8AD8)
    drawCircle(Color.White.copy(alpha = 0.07f), r, c, style = Stroke(w * 0.8f))

    for (i in 0 until 9) {
        val a = DECO_TAU * decoHash(i * 7 + 1)
        val rr = r + w * (0.9f + 0.8f * decoHash(i * 13 + 5))
        val tw = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * (1.5f + decoHash(i)) * 2f + i))
        decoSparkle(decoPt(c, rr, a), w * (0.25f + 0.25f * decoHash(i * 3 + 2)), Color.White, tw)
    }

    val comets = listOf(gold, cyan, pink)
    for (k in 0 until 3) {
        val col = comets[k]
        val ang = t * (70f + 35f * k) + k * 120f
        val rad = r + w * 0.5f * k
        rotate(ang, c) {
            drawCircle(
                Brush.sweepGradient(
                    0f to Color.Transparent, 0.55f to Color.Transparent, 1f to col,
                    center = c
                ),
                rad, c, style = Stroke(w * (0.7f - 0.1f * k), cap = StrokeCap.Round)
            )
        }
        val head = decoPt(c, rad, decoRad(ang))
        drawCircle(col.copy(alpha = 0.35f), w * 1.0f, head)
        drawCircle(Color.White, w * 0.32f, head)
    }
}

// ---------------------------------------------------------------------------
// 2D-7: RUNE CIRCLE — lingkaran sihir: cincin putus-putus, bintang segi enam, node bercahaya
// ---------------------------------------------------------------------------
internal fun DrawScope.drawRuneCircle(c: Offset, r: Float, w: Float, t: Float) {
    val gold = Color(0xFFFFC857)
    val violet = Color(0xFF9B5DE5)
    val glow = Color(0xFFB388FF)
    val pulse = decoPulse(t)

    drawCircle(glow.copy(alpha = 0.12f + 0.16f * pulse), r + w, c, style = Stroke(w * 2.2f))
    drawCircle(gold, r, c, style = Stroke(w * 0.4f))

    rotate(t * 18f, c) {
        drawCircle(
            violet, r + w * 0.85f, c,
            style = Stroke(w * 0.55f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 1.1f, w * 0.7f), 0f))
        )
    }
    rotate(-t * 26f, c) {
        drawCircle(
            gold.copy(alpha = 0.9f), r + w * 1.4f, c,
            style = Stroke(w * 0.22f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.3f, w * 0.5f), 0f))
        )
    }

    // hexagram = 2 segitiga
    val sr = r + w * 1.45f
    rotate(t * 10f, c) {
        for (k in 0 until 2) {
            val tri = Path()
            for (i in 0 until 3) {
                val p = decoPt(c, sr, decoRad(k * 60f + 120f * i - 90f))
                if (i == 0) tri.moveTo(p.x, p.y) else tri.lineTo(p.x, p.y)
            }
            tri.close()
            drawPath(tri, gold, alpha = 0.85f, style = Stroke(w * 0.22f, join = StrokeJoin.Round))
        }
    }
    for (i in 0 until 6) {
        val p = decoPt(c, sr, decoRad(i * 60f - 90f + t * 10f))
        drawCircle(glow.copy(alpha = 0.35f + 0.3f * pulse), w * 0.5f, p)
        drawCircle(gold, w * 0.2f, p)
    }
}

// ---------------------------------------------------------------------------
// 2D-8: PIXEL RETRO — ring piksel 8-bit palet PICO-8 yang berkedip seperti marquee arcade
// ---------------------------------------------------------------------------
internal fun DrawScope.drawPixelRetro(c: Offset, r: Float, w: Float, t: Float) {
    val pal = listOf(
        Color(0xFFFF004D), Color(0xFFFFA300), Color(0xFFFFEC27),
        Color(0xFF00E436), Color(0xFF29ADFF), Color(0xFF83769C)
    )
    val g = w * 0.85f
    val n = 60
    val step = floor(t * 7f).toInt()
    for (i in 0 until n) {
        if (((i / 3) + step) % 4 == 0) continue
        val a = DECO_TAU * i / n
        val p = decoPt(c, r + w * 0.05f, a)
        val sx = floor((p.x - c.x) / g) * g + c.x
        val sy = floor((p.y - c.y) / g) * g + c.y
        drawRect(pal[((i / 4) + step) % pal.size], Offset(sx, sy), Size(g * 0.92f, g * 0.92f))
    }
}

// ---------------------------------------------------------------------------
// 2D-9: NEKO EARS — telinga kucing imut di atas ring pastel (sesekali berkedut)
// ---------------------------------------------------------------------------
private fun decoEarAngle(deg: Float, mirror: Boolean): Float = decoRad(if (mirror) -180f - deg else deg)

internal fun DrawScope.drawNekoEars(c: Offset, r: Float, w: Float, t: Float) {
    val pink = Color(0xFFFF9EBB)
    val inner = Color(0xFFFFD6E2)
    val deep = Color(0xFFFF6F9C)
    val pulse = decoPulse(t)

    drawCircle(pink.copy(alpha = 0.14f + 0.12f * pulse), r + w * 0.4f, c, style = Stroke(w * 1.8f))
    drawCircle(Brush.sweepGradient(listOf(pink, Color.White, deep, pink), c), r, c, style = Stroke(w * 0.85f))

    val twitch = if (decoFract(t / 3.2f) < 0.14f) sin(t * 55f) * 6f else 0f
    for (side in 0 until 2) {
        val mirror = side == 1
        val bA = decoPt(c, r, decoEarAngle(-162f, mirror))
        val bB = decoPt(c, r, decoEarAngle(-112f, mirror))
        val tip = decoPt(c, r + w * 3.3f, decoEarAngle(-138f, mirror))
        val k1 = decoPt(c, r + w * 2.2f, decoEarAngle(-160f, mirror))
        val k2 = decoPt(c, r + w * 2.2f, decoEarAngle(-118f, mirror))
        val outer = Path().apply {
            moveTo(bA.x, bA.y)
            quadraticBezierTo(k1.x, k1.y, tip.x, tip.y)
            quadraticBezierTo(k2.x, k2.y, bB.x, bB.y)
            close()
        }
        val iA = decoPt(c, r + w * 0.35f, decoEarAngle(-153f, mirror))
        val iB = decoPt(c, r + w * 0.35f, decoEarAngle(-121f, mirror))
        val iT = decoPt(c, r + w * 2.35f, decoEarAngle(-137f, mirror))
        val innerPath = Path().apply {
            moveTo(iA.x, iA.y)
            lineTo(iT.x, iT.y)
            lineTo(iB.x, iB.y)
            close()
        }
        val pivot = decoPt(c, r, decoEarAngle(-137f, mirror))
        rotate(if (mirror) -twitch else twitch, pivot) {
            drawPath(outer, pink)
            drawPath(innerPath, inner, alpha = 0.95f)
            drawPath(outer, deep, alpha = 0.9f, style = Stroke(w * 0.18f, join = StrokeJoin.Round))
        }
    }

    for (i in 0 until 4) {
        val a = decoRad(20f + i * 55f + 40f * decoHash(i + 11))
        val tw = 0.3f + 0.7f * (0.5f + 0.5f * sin(t * 2.4f + i * 1.9f))
        decoSparkle(decoPt(c, r + w * 1.2f, a), w * 0.4f, Color.White, tw)
    }
}

// ---------------------------------------------------------------------------
// 2D-10: ANGEL HALO — halo emas melayang di atas kepala + glow suci & kilau
// ---------------------------------------------------------------------------
internal fun DrawScope.drawAngelHalo(c: Offset, r: Float, w: Float, t: Float) {
    val gold = Color(0xFFFFD86B)
    val pale = Color(0xFFFFF6D6)
    val pulse = decoPulse(t)

    drawCircle(pale.copy(alpha = 0.16f + 0.14f * pulse), r + w * 0.6f, c, style = Stroke(w * 2.0f))
    drawCircle(
        Brush.sweepGradient(listOf(pale, Color.White, gold, pale), c),
        r, c, alpha = 0.9f, style = Stroke(w * 0.7f)
    )

    val bob = sin(t * 2f) * w * 0.35f
    val hcx = c.x
    val hcy = c.y - r * 1.07f + bob
    val hw = r * 0.62f
    val hh = r * 0.17f
    val topLeft = Offset(hcx - hw, hcy - hh)
    val sz = Size(hw * 2f, hh * 2f)
    drawOval(gold.copy(alpha = 0.22f + 0.20f * pulse), topLeft, sz, style = Stroke(w * 1.6f))
    drawOval(
        Brush.horizontalGradient(listOf(gold, pale, gold), hcx - hw, hcx + hw),
        topLeft, sz, style = Stroke(w * 0.7f)
    )

    for (i in 0 until 5) {
        val a = decoRad(-150f + i * 30f + 8f * decoHash(i + 3))
        val tw = 0.3f + 0.7f * (0.5f + 0.5f * sin(t * 2.2f + i * 1.7f))
        decoSparkle(decoPt(c, r + w * (0.9f + 0.5f * decoHash(i + 21)), a), w * 0.42f, pale, tw)
    }
}
