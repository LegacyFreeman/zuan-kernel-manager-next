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
import kotlin.math.sin

/**
 * ZKM NEXT — Ultimate 2D collection.
 * Canvas-only ornaments; no bitmap assets and no per-frame allocations outside drawing.
 */

internal fun DrawScope.drawAetherWings(c: Offset, r: Float, w: Float, t: Float) {
    val pearl = Color(0xFFE1F5FE)
    val cyan = Color(0xFF40C4FF)
    val violet = Color(0xFFB388FF)
    val pulse = decoPulse(t)
    drawCircle(cyan.copy(alpha = 0.12f + 0.08f * pulse), r + w * 0.55f, c, style = Stroke(w * 2.2f))
    drawCircle(Brush.sweepGradient(listOf(violet, pearl, cyan, violet), c), r, c, style = Stroke(w * 0.55f))
    for (side in listOf(-1f, 1f)) {
        val base = Offset(c.x + side * r * 0.68f, c.y - r * 0.02f)
        for (i in 0 until 4) {
            val spread = i / 3f
            val tip = Offset(c.x + side * (r + w * (1.3f + spread * 0.6f)), c.y - r * (0.64f - spread * 0.08f) + w * (i - 1.5f))
            val feather = Path().apply {
                moveTo(base.x, base.y - w * 0.42f)
                cubicTo(base.x + side * w * 0.8f, base.y - w * (1.4f + i * 0.12f), tip.x - side * w * 0.25f, tip.y - w * 0.45f, tip.x, tip.y)
                cubicTo(tip.x - side * w * 0.75f, tip.y + w * 0.2f, base.x + side * w * 0.4f, base.y + w * 0.45f, base.x, base.y - w * 0.42f)
                close()
            }
            drawPath(feather, Brush.linearGradient(listOf(pearl, cyan.copy(alpha = 0.82f), violet), base, tip), alpha = 0.76f)
            drawPath(feather, pearl.copy(alpha = 0.65f), style = Stroke(w * 0.08f, join = StrokeJoin.Round))
        }
    }
    decoSparkle(Offset(c.x, c.y - r - w * 0.8f), w * 0.75f, pearl, 0.9f)
}

internal fun DrawScope.drawThunderCrest(c: Offset, r: Float, w: Float, t: Float) {
    val electric = Color(0xFF7C4DFF)
    val blue = Color(0xFF00E5FF)
    val pulse = decoPulse(t)
    drawCircle(electric.copy(alpha = 0.18f + pulse * 0.14f), r + w * 0.55f, c, style = Stroke(w * 2.1f))
    for (i in 0 until 12) {
        val a = decoRad(i * 30f + 15f)
        val p0 = decoPt(c, r - w * 0.25f, a)
        val p1 = decoPt(c, r + w * (if (i % 3 == 0) 1.45f else 0.55f), a)
        drawLine(if (i % 3 == 0) blue else electric.copy(alpha = 0.65f), p0, p1, w * (if (i % 3 == 0) 0.32f else 0.14f), StrokeCap.Round)
    }
    drawCircle(Brush.sweepGradient(listOf(blue, electric, Color.White, blue), c), r, c, style = Stroke(w * 0.62f))
    for (i in 0 until 5) {
        val a = decoRad(i * 72f + 10f + t * 7f)
        val p = decoPt(c, r + w * 0.9f, a)
        val bolt = Path().apply {
            moveTo(p.x - w * 0.26f, p.y - w * 0.75f)
            lineTo(p.x + w * 0.1f, p.y - w * 0.08f)
            lineTo(p.x - w * 0.1f, p.y - w * 0.02f)
            lineTo(p.x + w * 0.28f, p.y + w * 0.75f)
            lineTo(p.x - w * 0.08f, p.y + w * 0.15f)
            lineTo(p.x + w * 0.08f, p.y + w * 0.08f)
            close()
        }
        drawPath(bolt, if (i % 2 == 0) Color.White else blue)
    }
}

internal fun DrawScope.drawFrostbiteSigil(c: Offset, r: Float, w: Float, t: Float) {
    val ice = Color(0xFF80DEEA)
    val whiteBlue = Color(0xFFE0F7FA)
    val deep = Color(0xFF00838F)
    drawCircle(ice.copy(alpha = 0.18f), r + w * 0.65f, c, style = Stroke(w * 2f))
    drawCircle(Brush.sweepGradient(listOf(deep, ice, whiteBlue, ice, deep), c), r, c, style = Stroke(w * 0.72f))
    for (i in 0 until 8) {
        val a = decoRad(i * 45f + 22.5f)
        val p0 = decoPt(c, r - w * 0.2f, a)
        val p1 = decoPt(c, r + w * 1.5f, a)
        val p2 = decoPt(c, r + w * 0.65f, a + decoRad(9f))
        val p3 = decoPt(c, r + w * 0.65f, a - decoRad(9f))
        val shard = Path().apply { moveTo(p0.x, p0.y); lineTo(p1.x, p1.y); lineTo(p2.x, p2.y); close() }
        drawPath(shard, Brush.linearGradient(listOf(whiteBlue, ice, deep), p0, p1), alpha = 0.86f)
        drawLine(whiteBlue.copy(alpha = 0.85f), p1, p3, w * 0.12f, StrokeCap.Round)
        val mid = decoPt(c, r + w * (0.55f + 0.08f * sin(t * 2f + i)), a)
        drawCircle(whiteBlue, w * 0.16f, mid)
    }
}

internal fun DrawScope.drawSolarLance(c: Offset, r: Float, w: Float, t: Float) {
    val gold = Color(0xFFFFD54F)
    val amber = Color(0xFFFF8F00)
    val whiteGold = Color(0xFFFFF8E1)
    val spin = t * 8f
    drawCircle(gold.copy(alpha = 0.18f), r + w * 0.7f, c, style = Stroke(w * 2f))
    drawCircle(Brush.sweepGradient(listOf(amber, gold, whiteGold, gold, amber), c), r, c, style = Stroke(w * 0.82f))
    for (i in 0 until 16) {
        val a = decoRad(i * 22.5f + spin)
        val inner = decoPt(c, r + w * 0.3f, a)
        val tip = decoPt(c, r + w * (if (i % 2 == 0) 1.7f else 0.9f), a)
        val sideL = decoPt(c, r + w * 0.38f, a - decoRad(4.7f))
        val sideR = decoPt(c, r + w * 0.38f, a + decoRad(4.7f))
        val ray = Path().apply { moveTo(sideL.x, sideL.y); lineTo(tip.x, tip.y); lineTo(sideR.x, sideR.y); close() }
        drawPath(ray, if (i % 2 == 0) gold else amber, alpha = 0.92f)
        drawCircle(whiteGold.copy(alpha = 0.8f), w * 0.11f, inner)
    }
    decoSparkle(Offset(c.x, c.y - r - w * 1.35f), w * 0.62f, whiteGold, 0.95f)
}

internal fun DrawScope.drawRoseVortex(c: Offset, r: Float, w: Float, t: Float) {
    val rose = Color(0xFFFF4081)
    val pink = Color(0xFFFF80AB)
    val wine = Color(0xFF880E4F)
    drawCircle(rose.copy(alpha = 0.16f), r + w * 0.55f, c, style = Stroke(w * 2.1f))
    drawCircle(Brush.sweepGradient(listOf(wine, rose, pink, Color.White, wine), c), r, c, style = Stroke(w * 0.55f))
    for (i in 0 until 8) {
        val a = decoRad(i * 45f + t * 6f)
        val p = decoPt(c, r + w * 0.62f, a)
        rotate(i * 45f + t * 8f, p) {
            val petal = Path().apply {
                moveTo(p.x, p.y)
                cubicTo(p.x - w * 0.68f, p.y - w * 0.8f, p.x - w * 0.12f, p.y - w * 1.55f, p.x, p.y - w * 1.75f)
                cubicTo(p.x + w * 0.72f, p.y - w * 0.86f, p.x + w * 0.7f, p.y - w * 0.28f, p.x, p.y)
                close()
            }
            drawPath(petal, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.7f), pink, rose), p, Offset(p.x, p.y - w * 1.7f)), alpha = 0.9f)
            drawPath(petal, wine.copy(alpha = 0.5f), style = Stroke(w * 0.08f))
        }
        drawCircle(Color.White.copy(alpha = 0.7f), w * 0.12f, p)
    }
}

internal fun DrawScope.drawArcaneTarot(c: Offset, r: Float, w: Float, t: Float) {
    val violet = Color(0xFF9575CD)
    val lilac = Color(0xFFE1BEE7)
    val gold = Color(0xFFFFD180)
    drawCircle(violet.copy(alpha = 0.13f), r + w * 0.72f, c, style = Stroke(w * 1.8f))
    drawCircle(Brush.sweepGradient(listOf(violet, gold, lilac, violet), c), r, c, style = Stroke(w * 0.6f))
    for (i in 0 until 24) {
        val a = decoRad(i * 15f)
        val long = i % 3 == 0
        drawLine(gold.copy(alpha = if (long) 0.95f else 0.45f), decoPt(c, r - w * 0.18f, a), decoPt(c, r + w * (if (long) 0.52f else 0.2f), a), w * (if (long) 0.15f else 0.08f), StrokeCap.Round)
    }
    for (i in 0 until 6) {
        val a = decoRad(i * 60f + 30f + t * 3f)
        val p = decoPt(c, r + w * 0.85f, a)
        val diamond = Path().apply {
            moveTo(p.x, p.y - w * 0.58f); lineTo(p.x + w * 0.27f, p.y); lineTo(p.x, p.y + w * 0.58f); lineTo(p.x - w * 0.27f, p.y); close()
        }
        drawPath(diamond, if (i % 2 == 0) gold else lilac)
        drawCircle(Color.White.copy(alpha = 0.65f), w * 0.1f, p)
    }
    drawCircle(violet.copy(alpha = 0.35f), r * 0.74f, c, style = Stroke(w * 0.18f))
}

internal fun DrawScope.drawCrimsonBloom(c: Offset, r: Float, w: Float, t: Float) {
    val crimson = Color(0xFFD50032)
    val scarlet = Color(0xFFFF1744)
    val dark = Color(0xFF2A0010)
    drawCircle(dark.copy(alpha = 0.6f), r + w * 0.5f, c, style = Stroke(w * 1.8f))
    drawCircle(Brush.sweepGradient(listOf(dark, crimson, scarlet, dark), c), r, c, style = Stroke(w * 0.72f))
    for (i in 0 until 9) {
        val a = decoRad(i * 40f + t * 10f)
        val p = decoPt(c, r + w * 0.7f, a)
        val petal = Path().apply {
            moveTo(p.x, p.y)
            cubicTo(p.x - w * 0.42f, p.y - w * 0.85f, p.x + w * 0.3f, p.y - w * 1.4f, p.x + w * 0.05f, p.y - w * 1.55f)
            cubicTo(p.x + w * 0.8f, p.y - w * 0.9f, p.x + w * 0.56f, p.y - w * 0.3f, p.x, p.y)
            close()
        }
        drawPath(petal, if (i % 2 == 0) scarlet else crimson, alpha = 0.9f)
        drawPath(petal, Color(0xFFFF8A80).copy(alpha = 0.7f), style = Stroke(w * 0.08f))
    }
    for (i in 0 until 5) decoSparkle(decoPt(c, r + w * (1.0f + 0.2f * sin(t + i)), decoRad(i * 72f + 20f)), w * 0.18f, Color(0xFFFFCDD2), 0.85f)
}

internal fun DrawScope.drawDigitalPhantom(c: Offset, r: Float, w: Float, t: Float) {
    val mint = Color(0xFF64FFDA)
    val blue = Color(0xFF00B0FF)
    val purple = Color(0xFF651FFF)
    drawCircle(mint.copy(alpha = 0.12f), r + w * 0.75f, c, style = Stroke(w * 1.8f))
    drawCircle(Brush.sweepGradient(listOf(purple, blue, mint, purple), c), r, c, style = Stroke(w * 0.65f))
    for (i in 0 until 18) {
        val a = decoRad(i * 20f + (if (i % 2 == 0) t * 4f else -t * 3f))
        val p = decoPt(c, r + w * (0.45f + decoHash(i * 17 + 2) * 0.9f), a)
        val dx = w * (0.3f + decoHash(i * 7 + 1) * 0.5f)
        val dy = w * (0.08f + decoHash(i * 3 + 8) * 0.15f)
        val glitch = Path().apply { moveTo(p.x - dx, p.y - dy); lineTo(p.x + dx, p.y - dy); lineTo(p.x + dx, p.y + dy); lineTo(p.x - dx, p.y + dy); close() }
        val flash = decoFract(t * 0.8f + i * 0.17f) > 0.45f
        drawPath(glitch, if (flash) mint else blue.copy(alpha = 0.55f))
    }
    ringArc(Color.White.copy(alpha = 0.6f), -22f + t * 6f, 42f, c, r + w * 1.35f, Stroke(w * 0.2f, cap = StrokeCap.Round))
    ringArc(purple, 145f - t * 4f, 56f, c, r + w * 1.15f, Stroke(w * 0.28f, cap = StrokeCap.Round))
}

internal fun DrawScope.drawLunarEclipse(c: Offset, r: Float, w: Float, t: Float) {
    val moon = Color(0xFFCFD8DC)
    val blue = Color(0xFF7986CB)
    val gold = Color(0xFFFFD180)
    val phase = 0.5f + 0.5f * sin(t * 0.35f)
    drawCircle(blue.copy(alpha = 0.13f + 0.1f * phase), r + w * 0.65f, c, style = Stroke(w * 2f))
    drawCircle(Brush.sweepGradient(listOf(blue, moon, gold, blue), c), r, c, style = Stroke(w * 0.58f))
    ringArc(gold.copy(alpha = 0.85f), 205f, 130f, c, r + w * 0.9f, Stroke(w * 0.2f, cap = StrokeCap.Round))
    ringArc(moon.copy(alpha = 0.7f), 20f + t * 3f, 90f, c, r + w * 0.55f, Stroke(w * 0.16f, cap = StrokeCap.Round))
    val p = Offset(c.x + r * 0.8f, c.y - r * 0.78f)
    drawCircle(gold.copy(alpha = 0.95f), w * 0.52f, p)
    drawCircle(Color(0xFF283593), w * (0.4f + phase * 0.06f), Offset(p.x + w * 0.22f, p.y - w * 0.08f))
    for (i in 0 until 6) decoSparkle(decoPt(c, r + w * 1.2f, decoRad(i * 60f + 5f)), w * 0.16f, Color.White, 0.75f)
}

internal fun DrawScope.drawStardustCrown(c: Offset, r: Float, w: Float, t: Float) {
    val gold = Color(0xFFFFD54F)
    val light = Color(0xFFFFF8E1)
    val purple = Color(0xFFB388FF)
    drawCircle(gold.copy(alpha = 0.15f), r + w * 0.55f, c, style = Stroke(w * 1.8f))
    drawCircle(Brush.sweepGradient(listOf(gold, light, purple, gold), c), r, c, style = Stroke(w * 0.58f))
    val baseY = c.y - r * 0.78f
    val crown = Path().apply {
        moveTo(c.x - r * 0.66f, baseY + w * 1.25f)
        lineTo(c.x - r * 0.72f, baseY - w * 0.12f)
        lineTo(c.x - r * 0.32f, baseY + w * 0.58f)
        lineTo(c.x, baseY - w * 0.78f)
        lineTo(c.x + r * 0.32f, baseY + w * 0.58f)
        lineTo(c.x + r * 0.72f, baseY - w * 0.12f)
        lineTo(c.x + r * 0.66f, baseY + w * 1.25f)
        close()
    }
    drawPath(crown, Brush.linearGradient(listOf(light, gold, Color(0xFFB8860B)), Offset(c.x, baseY - w), Offset(c.x, baseY + w * 1.5f)))
    drawPath(crown, Color(0xFF7A5200), style = Stroke(w * 0.1f, join = StrokeJoin.Round))
    for (i in 0 until 7) {
        val a = decoRad(i * 48f + t * 5f)
        val p = decoPt(c, r + w * (0.7f + 0.1f * sin(t + i)), a)
        decoSparkle(p, w * (0.18f + 0.04f * (i % 2)), if (i % 2 == 0) light else purple, 0.88f)
    }
    drawCircle(Color(0xFFB71C1C), w * 0.2f, Offset(c.x, baseY + w * 0.72f))
}
