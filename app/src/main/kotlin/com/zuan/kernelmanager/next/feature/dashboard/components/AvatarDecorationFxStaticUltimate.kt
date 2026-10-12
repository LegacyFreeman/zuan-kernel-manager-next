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
import kotlin.math.cos
import kotlin.math.sin

/** ZKM NEXT Ultimate static collection. These functions are draw-only, with no animation clock. */

internal fun DrawScope.drawAmethystDiadem(c: Offset, r: Float, w: Float) {
    val purple = Color(0xFF7E57C2)
    val light = Color(0xFFE1BEE7)
    val gold = Color(0xFFFFD180)
    drawCircle(Color(0xFF311B92).copy(alpha = 0.28f), r + w * 0.52f, c, style = Stroke(w * 1.6f))
    drawCircle(Brush.sweepGradient(listOf(purple, light, gold, purple), c), r, c, style = Stroke(w * 0.88f))
    for (i in 0 until 10) {
        val a = decoRad(i * 36f)
        val p = decoPt(c, r, a)
        val gemstone = Path().apply {
            moveTo(p.x, p.y - w * 0.42f); lineTo(p.x + w * 0.3f, p.y); lineTo(p.x, p.y + w * 0.42f); lineTo(p.x - w * 0.3f, p.y); close()
        }
        drawPath(gemstone, if (i % 2 == 0) light else purple)
        drawPath(gemstone, Color.White.copy(alpha = 0.75f), style = Stroke(w * 0.055f))
    }
    val top = c.y - r * 0.72f
    val crown = Path().apply {
        moveTo(c.x - r * 0.5f, top + w)
        lineTo(c.x - r * 0.54f, top - w * 0.48f)
        lineTo(c.x - r * 0.2f, top + w * 0.48f)
        lineTo(c.x, top - w * 0.82f)
        lineTo(c.x + r * 0.2f, top + w * 0.48f)
        lineTo(c.x + r * 0.54f, top - w * 0.48f)
        lineTo(c.x + r * 0.5f, top + w)
        close()
    }
    drawPath(crown, Brush.linearGradient(listOf(Color(0xFFFFF8E1), gold, Color(0xFF8D6E63)), Offset(c.x, top - w), Offset(c.x, top + w)))
    drawPath(crown, purple.copy(alpha = 0.7f), style = Stroke(w * 0.08f, join = StrokeJoin.Round))
}

internal fun DrawScope.drawTitaniumWreath(c: Offset, r: Float, w: Float) {
    val steel = Color(0xFF90A4AE)
    val white = Color(0xFFECEFF1)
    val blue = Color(0xFF546E7A)
    drawCircle(Brush.sweepGradient(listOf(blue, white, steel, white, blue), c), r, c, style = Stroke(w * 0.92f))
    drawCircle(Color.White.copy(alpha = 0.62f), r - w * 0.55f, c, style = Stroke(w * 0.12f))
    for (side in listOf(-1f, 1f)) {
        for (i in 0 until 7) {
            val y = c.y + (i - 3f) * w * 0.54f
            val x = c.x + side * (r - w * 0.18f - kotlin.math.abs(i - 3) * w * 0.15f)
            val leaf = Path().apply {
                moveTo(x, y)
                quadraticTo(x + side * w * 0.9f, y - w * 0.48f, x + side * w * 1.18f, y - w * 0.92f)
                quadraticTo(x + side * w * 0.38f, y - w * 0.62f, x, y)
                close()
            }
            drawPath(leaf, Brush.linearGradient(listOf(white, steel, blue), Offset(x, y), Offset(x + side * w, y - w)))
            drawPath(leaf, blue.copy(alpha = 0.65f), style = Stroke(w * 0.07f))
        }
    }
    for (i in 0 until 6) {
        val p = decoPt(c, r + w * 0.15f, decoRad(i * 60f + 30f))
        drawCircle(Color(0xFFFFD180), w * 0.14f, p)
    }
}

internal fun DrawScope.drawObsidianDragon(c: Offset, r: Float, w: Float) {
    val black = Color(0xFF101014)
    val slate = Color(0xFF455A64)
    val red = Color(0xFFFF1744)
    drawCircle(black, r + w * 0.1f, c, style = Stroke(w * 1.02f))
    drawCircle(Brush.sweepGradient(listOf(slate, black, Color(0xFF78909C), black, slate), c), r, c, style = Stroke(w * 0.75f))
    for (i in 0 until 18) {
        val a = decoRad(i * 20f)
        val p = decoPt(c, r + w * 0.25f, a)
        val p1 = decoPt(c, r + w * (if (i % 2 == 0) 1.05f else 0.62f), a + decoRad(3.2f))
        val p2 = decoPt(c, r + w * 0.42f, a + decoRad(7.5f))
        val scale = Path().apply { moveTo(p.x, p.y); lineTo(p1.x, p1.y); lineTo(p2.x, p2.y); close() }
        drawPath(scale, if (i % 3 == 0) red else slate)
        drawPath(scale, Color.Black.copy(alpha = 0.75f), style = Stroke(w * 0.06f))
    }
    for (side in listOf(-1f, 1f)) {
        val horn = Path().apply {
            moveTo(c.x + side * r * 0.55f, c.y - r * 0.52f)
            lineTo(c.x + side * r * 0.92f, c.y - r * 0.98f)
            lineTo(c.x + side * r * 0.76f, c.y - r * 0.35f)
            close()
        }
        drawPath(horn, Brush.linearGradient(listOf(Color(0xFF90A4AE), black), Offset(c.x, c.y - r), Offset(c.x + side * r, c.y)))
        drawPath(horn, red.copy(alpha = 0.8f), style = Stroke(w * 0.08f))
    }
}

internal fun DrawScope.drawFrostedSapphire(c: Offset, r: Float, w: Float) {
    val blue = Color(0xFF1565C0)
    val ice = Color(0xFFB3E5FC)
    val white = Color(0xFFE1F5FE)
    drawCircle(blue.copy(alpha = 0.18f), r + w * 0.46f, c, style = Stroke(w * 1.65f))
    drawCircle(Brush.sweepGradient(listOf(blue, ice, white, ice, blue), c), r, c, style = Stroke(w * 0.92f))
    for (i in 0 until 8) {
        val a = decoRad(i * 45f + 22.5f)
        val p = decoPt(c, r, a)
        val tip = decoPt(c, r + w * 0.78f, a)
        val side1 = decoPt(c, r - w * 0.55f, a - decoRad(6f))
        val side2 = decoPt(c, r - w * 0.55f, a + decoRad(6f))
        val facet = Path().apply { moveTo(side1.x, side1.y); lineTo(tip.x, tip.y); lineTo(side2.x, side2.y); close() }
        drawPath(facet, Brush.linearGradient(listOf(white, ice, blue), side1, tip), alpha = 0.92f)
        drawCircle(Color.White.copy(alpha = 0.9f), w * 0.1f, p)
    }
    for (i in 0 until 4) decoSparkle(decoPt(c, r + w * 0.95f, decoRad(i * 90f + 45f)), w * 0.22f, white, 0.9f)
}

internal fun DrawScope.drawCelestialRelic(c: Offset, r: Float, w: Float) {
    val gold = Color(0xFFFFD180)
    val ivory = Color(0xFFFFF8E1)
    val teal = Color(0xFF80CBC4)
    drawCircle(Brush.sweepGradient(listOf(gold, ivory, teal, gold), c), r, c, style = Stroke(w * 0.75f))
    drawCircle(gold.copy(alpha = 0.55f), r - w * 0.56f, c, style = Stroke(w * 0.1f))
    for (i in 0 until 12) {
        val a = decoRad(i * 30f)
        val inner = decoPt(c, r - w * 0.12f, a)
        val outer = decoPt(c, r + w * 0.45f, a)
        drawLine(if (i % 3 == 0) ivory else gold.copy(alpha = 0.65f), inner, outer, w * 0.12f, StrokeCap.Round)
        if (i % 2 == 0) drawCircle(teal, w * 0.12f, outer)
    }
    // Symmetric moon crescents at top and bottom.
    for (ySign in listOf(-1f, 1f)) {
        val p = Offset(c.x, c.y + ySign * (r + w * 0.22f))
        drawCircle(gold, w * 0.42f, p)
        drawCircle(Color(0xFF263238), w * 0.34f, Offset(p.x + w * 0.2f, p.y - ySign * w * 0.1f))
        decoSparkle(Offset(p.x - w * 0.85f, p.y), w * 0.17f, ivory, 0.9f)
    }
}

internal fun DrawScope.drawCrimsonSigil(c: Offset, r: Float, w: Float) {
    val red = Color(0xFFB71C1C)
    val scarlet = Color(0xFFFF5252)
    val black = Color(0xFF212121)
    drawCircle(black, r, c, style = Stroke(w * 0.95f))
    drawCircle(Brush.sweepGradient(listOf(red, scarlet, Color(0xFFFFCDD2), red), c), r - w * 0.08f, c, style = Stroke(w * 0.44f))
    for (i in 0 until 10) {
        val a = decoRad(i * 36f)
        val p0 = decoPt(c, r - w * 0.18f, a)
        val p1 = decoPt(c, r + w * 0.7f, a)
        val left = decoPt(c, r + w * 0.38f, a - decoRad(5.5f))
        val right = decoPt(c, r + w * 0.38f, a + decoRad(5.5f))
        val spike = Path().apply { moveTo(p0.x, p0.y); lineTo(left.x, left.y); lineTo(p1.x, p1.y); lineTo(right.x, right.y); close() }
        drawPath(spike, if (i % 2 == 0) red else black)
        drawPath(spike, scarlet.copy(alpha = 0.55f), style = Stroke(w * 0.06f))
    }
    for (i in 0 until 5) {
        val p = decoPt(c, r - w * 0.5f, decoRad(i * 72f - 18f))
        val diamond = Path().apply { moveTo(p.x, p.y - w * 0.22f); lineTo(p.x + w * 0.14f, p.y); lineTo(p.x, p.y + w * 0.22f); lineTo(p.x - w * 0.14f, p.y); close() }
        drawPath(diamond, Color(0xFFFFCDD2))
    }
}

internal fun DrawScope.drawEclipseMedallion(c: Offset, r: Float, w: Float) {
    val silver = Color(0xFFCFD8DC)
    val gold = Color(0xFFFFD54F)
    val navy = Color(0xFF1A237E)
    drawCircle(Brush.sweepGradient(listOf(silver, Color.White, gold, silver), c), r, c, style = Stroke(w * 0.85f))
    drawCircle(navy.copy(alpha = 0.85f), r - w * 0.42f, c, style = Stroke(w * 0.12f))
    drawCircle(gold.copy(alpha = 0.65f), r - w * 0.76f, c, style = Stroke(w * 0.09f))
    for (i in 0 until 16) {
        val a = decoRad(i * 22.5f)
        val long = i % 4 == 0
        drawLine(gold, decoPt(c, r - w * 0.2f, a), decoPt(c, r + w * (if (long) 0.22f else 0.04f), a), w * (if (long) 0.12f else 0.06f), StrokeCap.Round)
    }
    val sun = Offset(c.x, c.y - r - w * 0.12f)
    drawCircle(gold, w * 0.5f, sun)
    drawCircle(Color.White.copy(alpha = 0.9f), w * 0.18f, Offset(sun.x - w * 0.1f, sun.y - w * 0.13f))
    for (side in listOf(-1f, 1f)) {
        val p = Offset(c.x + side * (r + w * 0.12f), c.y)
        drawCircle(silver, w * 0.22f, p)
        drawCircle(navy, w * 0.13f, p)
    }
}

internal fun DrawScope.drawRoseGoldFiligree(c: Offset, r: Float, w: Float) {
    val rose = Color(0xFFC08081)
    val blush = Color(0xFFFFDDE1)
    val gold = Color(0xFFFFD180)
    drawCircle(Brush.sweepGradient(listOf(rose, blush, gold, rose), c), r, c, style = Stroke(w * 0.78f))
    drawCircle(gold.copy(alpha = 0.55f), r - w * 0.58f, c, style = Stroke(w * 0.1f))
    for (side in listOf(-1f, 1f)) {
        for (i in 0 until 4) {
            val p = Offset(c.x + side * r * 0.7f, c.y + (i - 1.5f) * w * 0.62f)
            val curl = Path().apply {
                moveTo(p.x, p.y)
                cubicTo(p.x + side * w * 0.55f, p.y - w * 0.88f, p.x + side * w * 1.15f, p.y - w * 0.4f, p.x + side * w * 0.55f, p.y + w * 0.05f)
                cubicTo(p.x + side * w * 0.1f, p.y + w * 0.34f, p.x + side * w * 0.22f, p.y - w * 0.26f, p.x + side * w * 0.5f, p.y - w * 0.18f)
            }
            drawPath(curl, rose, style = Stroke(w * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(blush, w * 0.12f, Offset(p.x + side * w * 0.55f, p.y + w * 0.05f))
        }
    }
    for (i in 0 until 8) {
        val p = decoPt(c, r + w * 0.1f, decoRad(i * 45f + 22.5f))
        drawCircle(gold, w * 0.16f, p)
    }
}

internal fun DrawScope.drawMysticRunestone(c: Offset, r: Float, w: Float) {
    val jade = Color(0xFF80CBC4)
    val mint = Color(0xFFB9F6CA)
    val dark = Color(0xFF004D40)
    drawCircle(dark.copy(alpha = 0.85f), r, c, style = Stroke(w * 0.95f))
    drawCircle(Brush.sweepGradient(listOf(dark, jade, mint, jade, dark), c), r - w * 0.08f, c, style = Stroke(w * 0.52f))
    for (i in 0 until 5) {
        val a = decoRad(i * 72f - 90f)
        val p = decoPt(c, r + w * 0.36f, a)
        val rune = Path().apply {
            moveTo(p.x, p.y - w * 0.42f)
            lineTo(p.x + w * 0.2f, p.y - w * 0.12f)
            lineTo(p.x - w * 0.14f, p.y + w * 0.05f)
            lineTo(p.x + w * 0.18f, p.y + w * 0.38f)
        }
        drawPath(rune, mint, style = Stroke(w * 0.17f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    for (i in 0 until 5) {
        val p = decoPt(c, r - w * 0.62f, decoRad(i * 72f - 90f))
        drawCircle(jade, w * 0.13f, p)
    }
    val sigil = Path().apply {
        moveTo(c.x, c.y - r * 0.55f)
        lineTo(c.x + r * 0.47f, c.y + r * 0.22f)
        lineTo(c.x - r * 0.47f, c.y + r * 0.22f)
        close()
    }
    drawPath(sigil, jade.copy(alpha = 0.6f), style = Stroke(w * 0.14f, join = StrokeJoin.Round))
}

internal fun DrawScope.drawImperialPhoenix(c: Offset, r: Float, w: Float) {
    val gold = Color(0xFFFFD54F)
    val amber = Color(0xFFFF8F00)
    val red = Color(0xFFC62828)
    drawCircle(Brush.sweepGradient(listOf(amber, gold, Color(0xFFFFF8E1), gold, amber), c), r, c, style = Stroke(w * 0.85f))
    drawCircle(red.copy(alpha = 0.5f), r - w * 0.58f, c, style = Stroke(w * 0.12f))
    for (side in listOf(-1f, 1f)) {
        for (i in 0 until 4) {
            val origin = Offset(c.x + side * r * 0.36f, c.y - w * 0.14f)
            val tip = Offset(c.x + side * (r + w * (0.8f + i * 0.18f)), c.y - r * (0.5f - i * 0.11f))
            val feather = Path().apply {
                moveTo(origin.x, origin.y - w * 0.3f)
                cubicTo(origin.x + side * w * 0.68f, origin.y - w * 1.05f, tip.x - side * w * 0.25f, tip.y - w * 0.45f, tip.x, tip.y)
                cubicTo(tip.x - side * w * 0.65f, tip.y + w * 0.26f, origin.x + side * w * 0.4f, origin.y + w * 0.32f, origin.x, origin.y - w * 0.3f)
                close()
            }
            drawPath(feather, Brush.linearGradient(listOf(Color(0xFFFFF8E1), gold, if (i % 2 == 0) red else amber), origin, tip), alpha = 0.94f)
            drawPath(feather, Color(0xFF8D3B00).copy(alpha = 0.75f), style = Stroke(w * 0.07f))
        }
    }
    val crownY = c.y - r * 0.76f
    val crown = Path().apply {
        moveTo(c.x - r * 0.34f, crownY + w * 0.78f)
        lineTo(c.x - r * 0.38f, crownY - w * 0.28f)
        lineTo(c.x, crownY + w * 0.2f)
        lineTo(c.x + r * 0.38f, crownY - w * 0.28f)
        lineTo(c.x + r * 0.34f, crownY + w * 0.78f)
        close()
    }
    drawPath(crown, gold)
    drawCircle(red, w * 0.17f, Offset(c.x, crownY + w * 0.34f))
}
