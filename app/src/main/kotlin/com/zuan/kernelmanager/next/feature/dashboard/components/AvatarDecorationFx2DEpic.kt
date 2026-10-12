/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.dashboard.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/*
 * ===========================================================================
 *  Dekorasi avatar 2D EPIC PREMIUM, MYTHIC & DIVINE
 *  Semua digambar prosedural (tanpa aset) dan digerakkan oleh waktu [t] (detik).
 *  Radius efektif ring ≈ r, tebal dasar ≈ w.
 * ===========================================================================
 */

// ---------------------------------------------------------------------------
// 2D-EPIC-1: VOID VORTEX — pusaran kegelapan: 3 lengan spiral ungu + partikel tersedot
// ---------------------------------------------------------------------------
internal fun DrawScope.drawVoidVortex(c: Offset, r: Float, w: Float, t: Float) {
    val violet = Color(0xFF7C4DFF)
    val magenta = Color(0xFFE040FB)
    val pulse = decoPulse(t)
    drawCircle(violet.copy(alpha = 0.14f + 0.12f * pulse), r + w * 0.7f, c, style = Stroke(w * 2.6f))
    for (k in 0 until 3) {
        val col = if (k == 1) magenta else violet
        rotate(t * 90f + k * 120f, c) {
            drawCircle(
                Brush.sweepGradient(
                    0f to Color.Transparent, 0.55f to Color.Transparent, 1f to col, center = c
                ),
                r + w * 0.15f * k, c,
                alpha = 0.95f - 0.15f * k,
                style = Stroke(w * (0.9f - 0.15f * k), cap = StrokeCap.Round)
            )
        }
        val head = decoPt(c, r + w * 0.15f * k, decoRad(t * 90f + k * 120f))
        drawCircle(col, w * 0.45f, head)
        drawCircle(Color.White.copy(alpha = 0.85f), w * 0.16f, head)
    }
    for (i in 0 until 10) {
        val f = decoFract(t * 0.5f + i * 0.1f)
        val a = decoRad(i * 36f + t * 60f)
        val p = decoPt(c, r + w * (1.8f - 1.4f * f), a)
        drawCircle(violet.copy(alpha = (1f - f) * 0.8f), w * 0.2f * (1f - 0.5f * f), p)
    }
}

// ---------------------------------------------------------------------------
// 2D-EPIC-2: PHOENIX FLAME — pita api phoenix berlapis + bara yang terbang naik
// ---------------------------------------------------------------------------
internal fun DrawScope.drawPhoenixFlame(c: Offset, r: Float, w: Float, t: Float) {
    val gold = Color(0xFFFFD54F)
    val orange = Color(0xFFFF7043)
    val red = Color(0xFFD32F2F)
    val pulse = decoPulse(t)
    drawCircle(orange.copy(alpha = 0.16f + 0.16f * pulse), r + w, c, style = Stroke(w * 2.4f))
    val p2 = decoWavyPath(c, r + w * 0.45f, w * 0.55f, 5, -t * 3f + 2f)
    drawPath(
        p2, Brush.sweepGradient(listOf(red, orange, gold, red), c),
        alpha = 0.8f, style = Stroke(w * 0.75f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    val p1 = decoWavyPath(c, r, w * (0.7f + 0.25f * pulse), 7, t * 4f)
    drawPath(
        p1, Brush.sweepGradient(listOf(gold, Color.White, orange, gold), c),
        style = Stroke(w * 0.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    for (i in 0 until 7) {
        val f = decoFract(t * 0.45f + decoHash(i * 31 + 7))
        val a = decoRad(i * 51.4f + t * 24f)
        val p = decoPt(c, r + w * (0.3f + 1.6f * f), a)
        drawCircle((if (i % 2 == 0) gold else orange).copy(alpha = 1f - f), w * (0.3f - 0.12f * f), p)
    }
}

// ---------------------------------------------------------------------------
// 2D-EPIC-3: TOXIC SLIME — ring lendir hijau: tetesan memanjang + gelembung asam
// ---------------------------------------------------------------------------
internal fun DrawScope.drawToxicSlime(c: Offset, r: Float, w: Float, t: Float) {
    val slime = Color(0xFF76FF03)
    val slimeDeep = Color(0xFF33691E)
    val acid = Color(0xFFC6FF00)
    val pulse = decoPulse(t)
    drawCircle(slime.copy(alpha = 0.15f + 0.12f * pulse), r + w * 0.5f, c, style = Stroke(w * 2.2f))
    drawCircle(Brush.sweepGradient(listOf(slimeDeep, slime, acid, slime, slimeDeep), c), r, c, style = Stroke(w * 0.95f))
    for (i in 0 until 5) {
        val a = decoRad(35f + i * 28f)
        val stretch = 0.6f + 0.5f * (0.5f + 0.5f * sin(t * 2.2f + i * 1.9f))
        val base = decoPt(c, r, a)
        val tip = decoPt(c, r + w * 2.2f * stretch, a)
        drawLine(slime, base, tip, w * 0.5f, StrokeCap.Round)
        drawCircle(acid, w * 0.34f * stretch, tip)
    }
    for (i in 0 until 6) {
        val a = decoRad(i * 60f - t * 30f)
        val f = decoFract(t * 0.6f + i * 0.23f)
        val p = decoPt(c, r + w * (0.15f + 0.5f * f), a)
        val s = w * (0.18f + 0.3f * f)
        drawCircle(slimeDeep.copy(alpha = 0.8f), s, p)
        drawCircle(Color.White.copy(alpha = 0.5f), s, p, style = Stroke(w * 0.08f))
    }
}

// ---------------------------------------------------------------------------
// 2D-EPIC-4: DIGITAL MATRIX — hujan kode hijau berjatuhan keluar dari ring
// ---------------------------------------------------------------------------
internal fun DrawScope.drawDigitalMatrix(c: Offset, r: Float, w: Float, t: Float) {
    val green = Color(0xFF00FF88)
    val dim = Color(0xFF004D40)
    drawCircle(green.copy(alpha = 0.16f), r + w * 0.5f, c, style = Stroke(w * 1.8f))
    drawCircle(dim.copy(alpha = 0.5f), r, c, style = Stroke(w * 0.5f))
    val n = 26
    for (i in 0 until n) {
        val a = DECO_TAU * i / n
        val speed = 0.35f + 0.65f * decoHash(i * 13 + 1)
        val f = decoFract(t * speed + decoHash(i * 7 + 3))
        val rr = r + w * (0.2f + 2.0f * f)
        val head = decoPt(c, rr, a)
        val tail = decoPt(c, rr - w * 0.8f, a)
        val alpha = if (f < 0.15f) f / 0.15f else 1f - (f - 0.15f) / 0.85f
        drawLine(green.copy(alpha = 0.4f * alpha), tail, head, w * 0.28f, StrokeCap.Round)
        drawCircle(Color(0xFFB9FFDD).copy(alpha = alpha), w * 0.16f, head)
    }
    if (decoFract(t / 3f) < 0.06f) drawCircle(green.copy(alpha = 0.6f), r, c, style = Stroke(w * 0.9f))
}

// ---------------------------------------------------------------------------
// 2D-EPIC-5: TECH CIRCUIT — jalur PCB bercabang keluar ring + arus berjalan
// ---------------------------------------------------------------------------
internal fun DrawScope.drawTechCircuit(c: Offset, r: Float, w: Float, t: Float) {
    val trace = Color(0xFF00E676)
    val copper = Color(0xFFFFB300)
    drawCircle(trace.copy(alpha = 0.15f), r + w * 0.4f, c, style = Stroke(w * 1.6f))
    drawCircle(trace, r, c, style = Stroke(w * 0.4f))
    val step = floor(t * 2f).toInt()
    for (i in 0 until 8) {
        val a = decoRad(i * 45f + 22.5f)
        val p0 = decoPt(c, r, a)
        val p1 = decoPt(c, r + w * 0.8f, a)
        val a2 = a + decoRad(if (i % 2 == 0) 14f else -14f)
        val p2 = decoPt(c, r + w * 1.5f, a2)
        val on = (step + i) % 8 < 3
        val col = if (on) trace else trace.copy(alpha = 0.3f)
        drawLine(col, p0, p1, w * 0.22f)
        drawLine(col, p1, p2, w * 0.22f)
        drawCircle(if (on) copper else copper.copy(alpha = 0.35f), w * 0.28f, p2)
    }
    val a = decoRad(t * 120f)
    val p = decoPt(c, r, a)
    drawCircle(copper, w * 0.5f, p)
    drawCircle(Color.White, w * 0.18f, p)
}

// ---------------------------------------------------------------------------
// 2D-EPIC-6: BUTTERFLY DREAM — kupu-kupu mengepak mengelilingi ring pastel
// ---------------------------------------------------------------------------
internal fun DrawScope.drawButterflyDream(c: Offset, r: Float, w: Float, t: Float) {
    val lavender = Color(0xFFB39DFF)
    val pink = Color(0xFFFF9AD5)
    val pulse = decoPulse(t)
    drawCircle(lavender.copy(alpha = 0.14f + 0.12f * pulse), r + w * 0.6f, c, style = Stroke(w * 2f))
    drawCircle(Brush.sweepGradient(listOf(lavender, pink, Color.White, lavender), c), r, c, style = Stroke(w * 0.7f))
    for (i in 0 until 4) {
        val flyA = t * 40f + i * 90f + 20f * sin(t * 1.3f + i)
        val p = decoPt(c, r + w * (1.0f + 0.3f * sin(t * 2f + i * 2f)), decoRad(flyA))
        val flap = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * 9f + i * 1.8f))
        val s = w * 0.8f
        val col = if (i % 2 == 0) pink else lavender
        rotate(flyA + 90f, p) {
            for (side in listOf(-1f, 1f)) {
                val wing = Path().apply {
                    moveTo(p.x, p.y)
                    cubicTo(
                        p.x + side * s * 1.6f * flap, p.y - s * 1.2f,
                        p.x + side * s * 1.8f * flap, p.y + s * 0.4f,
                        p.x, p.y + s * 0.3f
                    )
                    close()
                }
                drawPath(wing, col, alpha = 0.9f)
            }
            drawLine(
                Color(0xFF4A148C),
                Offset(p.x, p.y - s * 0.4f), Offset(p.x, p.y + s * 0.5f),
                w * 0.12f, StrokeCap.Round
            )
        }
    }
    for (i in 0 until 5) {
        val tw = 0.3f + 0.7f * (0.5f + 0.5f * sin(t * 2.6f + i * 1.3f))
        decoSparkle(decoPt(c, r + w * 1.3f, decoRad(i * 72f - t * 15f)), w * 0.35f, Color.White, tw)
    }
}

// ---------------------------------------------------------------------------
// 2D-EPIC-7: LAVA FLOW — ring magma tebal dengan retakan kerak + tetesan lava
// ---------------------------------------------------------------------------
internal fun DrawScope.drawLavaFlow(c: Offset, r: Float, w: Float, t: Float) {
    val magma = Color(0xFFFF5722)
    val hot = Color(0xFFFFC107)
    val crust = Color(0xFF3E2723)
    val pulse = decoPulse(t)
    drawCircle(magma.copy(alpha = 0.20f + 0.15f * pulse), r + w * 0.7f, c, style = Stroke(w * 2.6f))
    drawCircle(Brush.sweepGradient(listOf(crust, magma, hot, magma, crust), c), r, c, style = Stroke(w * 1.15f))
    val n = 48
    val seed = floor(t * 2f).toInt()
    val crack = Path()
    for (i in 0..n) {
        val a = DECO_TAU * i / n
        val j = (decoHash(i * 17 + seed * 7) - 0.5f) * w * 0.9f
        val p = decoPt(c, r + j, a)
        if (i == 0) crack.moveTo(p.x, p.y) else crack.lineTo(p.x, p.y)
    }
    crack.close()
    drawPath(crack, crust.copy(alpha = 0.8f), style = Stroke(w * 0.3f, join = StrokeJoin.Round))
    for (i in 0 until 6) {
        val f = decoFract(t * 0.3f + i * 0.17f)
        val a = decoRad(i * 60f + 30f)
        val p = decoPt(c, r + w * (0.4f + 1.5f * f), a)
        drawCircle((if (i % 2 == 0) hot else magma).copy(alpha = 1f - f), w * (0.35f - 0.15f * f), p)
    }
}

// ---------------------------------------------------------------------------
// 2D-EPIC-8: HOLO FOIL — foil hologram berputar cepat + kilau menyapu
// ---------------------------------------------------------------------------
internal fun DrawScope.drawHoloFoil(c: Offset, r: Float, w: Float, t: Float) {
    val holo = listOf(
        Color(0xFFFF9AD5), Color(0xFFFFF39A), Color(0xFF9CFFD4),
        Color(0xFF7DD3FF), Color(0xFFB39DFF), Color(0xFFFF9AD5)
    )
    val pulse = decoPulse(t)
    drawCircle(Color.White.copy(alpha = 0.10f + 0.08f * pulse), r + w * 0.6f, c, style = Stroke(w * 2.2f))
    rotate(t * 120f, c) {
        drawCircle(Brush.sweepGradient(holo, c), r, c, style = Stroke(w * 0.95f))
    }
    rotate(-t * 60f, c) {
        drawCircle(Brush.sweepGradient(holo.map { it.copy(alpha = 0.35f) }, c), r + w * 0.7f, c, style = Stroke(w * 0.3f))
        drawCircle(Brush.sweepGradient(holo.map { it.copy(alpha = 0.35f) }, c), r - w * 0.7f, c, style = Stroke(w * 0.3f))
    }
    decoSparkle(decoPt(c, r, decoRad(t * 150f)), w * (0.6f + 0.4f * pulse), Color.White, 0.95f)
    decoSparkle(decoPt(c, r, decoRad(t * 150f + 180f)), w * 0.45f, Color.White, 0.7f)
}

// ---------------------------------------------------------------------------
// 2D-MYTHIC-1: BLOOD MOON — ring darah + gerhana bulan merah & kabut crimson
// ---------------------------------------------------------------------------
internal fun DrawScope.drawBloodMoon(c: Offset, r: Float, w: Float, t: Float) {
    val blood = Color(0xFFB71C1C)
    val crimson = Color(0xFFFF1744)
    val ember = Color(0xFFFF8A80)
    val pulse = decoPulse(t)
    drawCircle(blood.copy(alpha = 0.20f + 0.15f * pulse), r + w * 0.8f, c, style = Stroke(w * 2.8f))
    drawCircle(
        Brush.sweepGradient(listOf(Color(0xFF2A0000), blood, crimson, blood, Color(0xFF2A0000)), c),
        r, c, style = Stroke(w * 0.9f)
    )
    val moonC = decoPt(c, r + w * 0.6f, decoRad(-90f + 8f * sin(t * 0.6f)))
    drawCircle(crimson.copy(alpha = 0.35f), w * 2.0f, moonC)
    drawCircle(crimson, w * 1.15f, moonC)
    val shadow = w * (0.3f + 0.5f * (0.5f + 0.5f * sin(t * 0.4f)))
    drawCircle(Color(0xFF1A0000), w * 0.85f, Offset(moonC.x + shadow, moonC.y - w * 0.2f))
    for (k in 0 until 2) {
        rotate(t * (30f + 18f * k) + k * 170f, c) {
            drawCircle(
                Brush.sweepGradient(
                    0f to Color.Transparent, 0.6f to Color.Transparent, 1f to crimson.copy(alpha = 0.7f),
                    center = c
                ),
                r + w * (0.4f + 0.5f * k), c, style = Stroke(w * 0.5f, cap = StrokeCap.Round)
            )
        }
    }
    for (i in 0 until 6) {
        val tw = 0.3f + 0.7f * (0.5f + 0.5f * sin(t * 3f + i * 2.4f))
        drawCircle(ember.copy(alpha = tw), w * 0.22f, decoPt(c, r + w * 1.1f, decoRad(i * 60f + t * 10f)))
    }
}

// ---------------------------------------------------------------------------
// 2D-MYTHIC-2: SERPENT COIL — ular melingkar: tubuh bergelombang + kepala & lidah
// ---------------------------------------------------------------------------
internal fun DrawScope.drawSerpentCoil(c: Offset, r: Float, w: Float, t: Float) {
    val jade = Color(0xFF00E676)
    val emerald = Color(0xFF1B5E20)
    val goldEye = Color(0xFFFFD600)
    val body2 = decoWavyPath(c, r + w * 0.4f, w * 0.45f, 8, t * 2.6f + 3.1416f)
    drawPath(body2, emerald, alpha = 0.85f, style = Stroke(w * 0.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    val body1 = decoWavyPath(c, r, w * 0.55f, 8, t * 2.6f)
    drawPath(body1, jade, style = Stroke(w * 0.85f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    for (i in 0 until 24) {
        val a = DECO_TAU * i / 24f
        val rr = r + w * 0.55f * sin(8f * a + t * 2.6f)
        drawCircle(emerald, w * 0.14f, decoPt(c, rr, a))
    }
    val ha = decoRad(t * 45f)
    val head = decoPt(c, r + w * 0.2f, ha)
    rotate(t * 45f + 90f, head) {
        val hp = Path().apply {
            moveTo(head.x, head.y - w * 1.0f)
            quadraticBezierTo(head.x + w * 0.85f, head.y - w * 0.2f, head.x, head.y + w * 0.9f)
            quadraticBezierTo(head.x - w * 0.85f, head.y - w * 0.2f, head.x, head.y - w * 1.0f)
            close()
        }
        drawPath(hp, jade)
        drawPath(hp, emerald, style = Stroke(w * 0.12f, join = StrokeJoin.Round))
        drawCircle(goldEye, w * 0.16f, Offset(head.x - w * 0.3f, head.y - w * 0.25f))
        drawCircle(goldEye, w * 0.16f, Offset(head.x + w * 0.3f, head.y - w * 0.25f))
    }
    if (decoFract(t / 2.6f) < 0.2f) {
        val root = decoPt(c, r + w * 0.4f, ha)
        val f1 = decoPt(c, r + w * 1.1f, ha + decoRad(8f))
        val f2 = decoPt(c, r + w * 1.1f, ha - decoRad(8f))
        drawLine(Color(0xFFFF5252), root, f1, w * 0.12f, StrokeCap.Round)
        drawLine(Color(0xFFFF5252), root, f2, w * 0.12f, StrokeCap.Round)
    }
}

// ---------------------------------------------------------------------------
// 2D-NEW-1: SUPERNOVA BURST (Divine Cosmic)
// ---------------------------------------------------------------------------
internal fun DrawScope.drawSupernovaBurst(c: Offset, r: Float, w: Float, t: Float) {
    val gold = Color(0xFFFFD54F)
    val cyan = Color(0xFF80D8FF)
    val white = Color.White
    val pulse = decoPulse(t)

    for (i in 0 until 12) {
        val a = decoRad(i * 30f + t * 20f)
        val beamLen = w * (1.8f + 0.9f * sin(t * 3f + i * 1.5f))
        val p1 = decoPt(c, r + w * 0.4f, a)
        val p2 = decoPt(c, r + beamLen, a)
        drawLine(
            brush = Brush.linearGradient(listOf(gold.copy(alpha = 0.8f), Color.Transparent), p1, p2),
            start = p1,
            end = p2,
            strokeWidth = w * (0.35f + 0.15f * pulse),
            cap = StrokeCap.Round
        )
    }

    drawCircle(cyan.copy(alpha = 0.18f + 0.15f * pulse), r + w * 1.2f, c, style = Stroke(w * 1.8f))
    drawCircle(
        brush = Brush.sweepGradient(listOf(gold, white, cyan, gold), c),
        radius = r,
        center = c,
        style = Stroke(w * 1.1f)
    )

    for (i in 0 until 8) {
        val f = decoFract(t * 0.45f + i * 0.125f)
        val a = decoRad(i * 45f - t * 35f)
        val p = decoPt(c, r + w * (0.2f + 1.8f * f), a)
        drawCircle(white.copy(alpha = 1f - f), w * (0.35f * (1f - f)), p)
        decoSparkle(p, w * 0.25f, gold, 1f - f)
    }
}

// ---------------------------------------------------------------------------
// 2D-NEW-2: CYBERPUNK GLITCH (Legendary Cyber)
// ---------------------------------------------------------------------------
internal fun DrawScope.drawCyberpunkGlitch(c: Offset, r: Float, w: Float, t: Float) {
    val neonYellow = Color(0xFFFFEA00)
    val neonCyan = Color(0xFF00E5FF)
    val neonPink = Color(0xFFFF1744)

    drawCircle(neonCyan.copy(alpha = 0.22f), r, c, style = Stroke(w * 0.6f))

    rotate(t * 55f, c) {
        for (i in 0 until 8) {
            val startAng = i * 45f + 10f
            ringArc(neonYellow, startAng, 25f, c, r + w * 0.35f, Stroke(w * 0.4f, cap = StrokeCap.Round))
        }
    }

    rotate(-t * 20f, c) {
        for (i in 0 until 36) {
            val a = DECO_TAU * i / 36f
            val p1 = decoPt(c, r + w * 0.8f, a)
            val p2 = decoPt(c, r + w * 1.25f, a)
            drawLine(
                color = if (i % 3 == 0) neonPink else neonCyan.copy(alpha = 0.4f),
                start = p1,
                end = p2,
                strokeWidth = w * 0.15f
            )
        }
    }

    if (decoFract(t * 2.8f) < 0.14f) {
        val shift = w * 0.45f
        drawCircle(neonPink.copy(alpha = 0.6f), r, Offset(c.x - shift, c.y), style = Stroke(w * 0.5f))
        drawCircle(neonCyan.copy(alpha = 0.6f), r, Offset(c.x + shift, c.y), style = Stroke(w * 0.5f))
        decoSparkle(decoPt(c, r + w, decoRad(t * 120f)), w * 0.7f, neonYellow)
    }
}

// ---------------------------------------------------------------------------
// 2D-NEW-3: DRAGON SOUL (Mythic Oriental)
// ---------------------------------------------------------------------------
internal fun DrawScope.drawDragonSoul(c: Offset, r: Float, w: Float, t: Float) {
    val gold = Color(0xFFFFD54F)
    val fire = Color(0xFFFF3D00)
    val aura = Color(0xFFFFAB00)

    drawCircle(fire.copy(alpha = 0.18f), r + w * 0.5f, c, style = Stroke(w * 2f))

    val body = decoWavyPath(c, r, w * 0.65f, 6, t * 3.2f)
    drawPath(
        path = body,
        brush = Brush.sweepGradient(listOf(gold, fire, aura, gold), c),
        style = Stroke(w * 1.15f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    val headA = decoRad(t * 40f)
    val headPt = decoPt(c, r + w * 0.25f, headA)
    rotate(t * 40f + 90f, headPt) {
        val headPath = Path().apply {
            moveTo(headPt.x, headPt.y - w * 1.2f)
            lineTo(headPt.x + w * 0.8f, headPt.y + w * 0.4f)
            lineTo(headPt.x, headPt.y + w * 0.9f)
            lineTo(headPt.x - w * 0.8f, headPt.y + w * 0.4f)
            close()
        }
        drawPath(headPath, gold)
        drawCircle(Color.White, w * 0.18f, Offset(headPt.x - w * 0.3f, headPt.y - w * 0.2f))
        drawCircle(Color.White, w * 0.18f, Offset(headPt.x + w * 0.3f, headPt.y - w * 0.2f))
    }

    val pearlPt = decoPt(c, r + w * 0.9f, headA + decoRad(25f))
    drawCircle(Color.White, w * 0.35f, pearlPt)
    drawCircle(gold.copy(alpha = 0.4f), w * 0.7f, pearlPt)
}

// ---------------------------------------------------------------------------
// 2D-NEW-4: ABYSSAL KRAKEN (Mythic Eldritch)
// ---------------------------------------------------------------------------
internal fun DrawScope.drawAbyssalKraken(c: Offset, r: Float, w: Float, t: Float) {
    val darkViolet = Color(0xFF311B92)
    val bioCyan = Color(0xFF00E5FF)
    val deepPurple = Color(0xFF7C4DFF)

    drawCircle(darkViolet.copy(alpha = 0.35f), r + w * 0.4f, c, style = Stroke(w * 1.6f))
    drawCircle(
        brush = Brush.sweepGradient(listOf(deepPurple, bioCyan, darkViolet, deepPurple), c),
        radius = r,
        center = c,
        style = Stroke(w * 0.85f)
    )

    for (i in 0 until 6) {
        val baseAng = i * 60f + t * 15f
        val wriggle = sin(t * 2.5f + i * 1.3f) * w * 0.6f
        val pBase = decoPt(c, r - w * 0.2f, decoRad(baseAng))
        val pMid = decoPt(c, r + w * 0.8f + wriggle, decoRad(baseAng + 18f))
        val pTip = decoPt(c, r + w * 1.7f, decoRad(baseAng + 32f + wriggle * 0.2f))

        val tentacle = Path().apply {
            moveTo(pBase.x, pBase.y)
            quadraticBezierTo(pMid.x, pMid.y, pTip.x, pTip.y)
        }
        drawPath(tentacle, deepPurple, style = Stroke(w * 0.55f, cap = StrokeCap.Round))
        drawPath(tentacle, bioCyan.copy(alpha = 0.8f), style = Stroke(w * 0.18f, cap = StrokeCap.Round))
        drawCircle(bioCyan, w * 0.22f, pTip)
    }
}

// ---------------------------------------------------------------------------
// 2D-NEW-5: ETHEREAL WINGS (Divine Celestial)
// ---------------------------------------------------------------------------
internal fun DrawScope.drawEtherealWings(c: Offset, r: Float, w: Float, t: Float) {
    val lightGold = Color(0xFFFFF9C4)
    val holyCyan = Color(0xFFB2EBF2)
    val white = Color.White
    val pulse = decoPulse(t)

    drawCircle(white.copy(alpha = 0.12f + 0.12f * pulse), r + w * 0.6f, c, style = Stroke(w * 2f))
    drawCircle(Brush.sweepGradient(listOf(lightGold, white, holyCyan, lightGold), c), r, c, style = Stroke(w * 0.75f))

    val flap = sin(t * 2.2f) * 8f
    for (side in listOf(-1f, 1f)) {
        val anchor = Offset(c.x + side * r * 0.85f, c.y)
        rotate(side * (20f + flap), anchor) {
            for (f in 0 until 4) {
                val len = w * (2.8f - f * 0.45f)
                val tipY = anchor.y - len * 0.9f + f * w * 0.5f
                val tipX = anchor.x + side * len * 1.1f
                val wingPath = Path().apply {
                    moveTo(anchor.x, anchor.y + f * w * 0.3f)
                    quadraticBezierTo(anchor.x + side * w * 0.6f, tipY - w * 0.4f, tipX, tipY)
                    quadraticBezierTo(anchor.x + side * w * 0.8f, anchor.y + f * w * 0.5f, anchor.x, anchor.y + (f + 1) * w * 0.4f)
                    close()
                }
                drawPath(wingPath, Brush.linearGradient(listOf(white, holyCyan.copy(alpha = 0.75f)), anchor, Offset(tipX, tipY)))
                drawPath(wingPath, lightGold.copy(alpha = 0.85f), style = Stroke(w * 0.08f, join = StrokeJoin.Round))
            }
        }
    }

    for (i in 0 until 4) {
        val a = decoRad(45f + i * 90f + t * 15f)
        decoSparkle(decoPt(c, r + w * 1.1f, a), w * 0.4f, white)
    }
}

// ---------------------------------------------------------------------------
// 2D-NEW-6: QUANTUM SINGULARITY (Mythic Sci-Fi)
// ---------------------------------------------------------------------------
internal fun DrawScope.drawQuantumSingularity(c: Offset, r: Float, w: Float, t: Float) {
    val voidBlack = Color(0xFF0D0221)
    val cosmicCyan = Color(0xFF00F0FF)
    val purpleShift = Color(0xFFD500F9)

    drawCircle(voidBlack.copy(alpha = 0.45f), r + w * 0.3f, c, style = Stroke(w * 1.8f))

    for (k in 0 until 3) {
        val col = if (k == 0) cosmicCyan else purpleShift
        rotate(-t * (80f + k * 25f) + k * 120f, c) {
            drawCircle(
                brush = Brush.sweepGradient(
                    0f to Color.Transparent, 0.45f to Color.Transparent, 1f to col,
                    center = c
                ),
                radius = r + (k - 1) * w * 0.35f,
                center = c,
                style = Stroke(w * (0.8f - k * 0.15f), cap = StrokeCap.Round)
            )
        }
    }

    for (i in 0 until 12) {
        val fract = decoFract(t * 0.6f + i * 0.083f)
        val ang = decoRad(i * 30f + t * 90f)
        val pos = decoPt(c, r + w * (1.7f - 1.5f * fract), ang)
        drawCircle(cosmicCyan.copy(alpha = (1f - fract) * 0.9f), w * 0.2f * (1f - 0.4f * fract), pos)
    }
}

// ---------------------------------------------------------------------------
// 2D-NEW-7: SAKURA NIGHTFALL (Legendary Aesthetic)
// ---------------------------------------------------------------------------
internal fun DrawScope.drawSakuraNightfall(c: Offset, r: Float, w: Float, t: Float) {
    val darkPlum = Color(0xFF2C0B30)
    val neonSakura = Color(0xFFFF4081)
    val softPink = Color(0xFFF8BBD0)
    val moonGlow = Color(0xFFFFF9C4)

    drawCircle(darkPlum.copy(alpha = 0.3f), r + w * 0.5f, c, style = Stroke(w * 1.8f))
    drawCircle(
        brush = Brush.sweepGradient(listOf(darkPlum, neonSakura, softPink, neonSakura, darkPlum), c),
        radius = r,
        center = c,
        style = Stroke(w * 0.85f)
    )

    val moonCenter = decoPt(c, r + w * 0.85f, decoRad(-50f))
    drawCircle(moonGlow.copy(alpha = 0.95f), w * 0.8f, moonCenter)
    drawCircle(Color(0xFF14071E), w * 0.68f, Offset(moonCenter.x - w * 0.3f, moonCenter.y - w * 0.1f))

    for (i in 0 until 8) {
        val a = decoRad(i * 45f + t * 35f)
        val petalPt = decoPt(c, r + w * (0.1f + 0.45f * sin(t * 1.8f + i)), a)
        rotate(t * 60f + i * 45f, petalPt) {
            val petal = Path().apply {
                moveTo(petalPt.x, petalPt.y - w * 0.6f)
                quadraticBezierTo(petalPt.x + w * 0.45f, petalPt.y - w * 0.2f, petalPt.x, petalPt.y + w * 0.6f)
                quadraticBezierTo(petalPt.x - w * 0.45f, petalPt.y - w * 0.2f, petalPt.x, petalPt.y - w * 0.6f)
            }
            drawPath(petal, if (i % 2 == 0) neonSakura else softPink, alpha = 0.9f)
        }
    }
}
