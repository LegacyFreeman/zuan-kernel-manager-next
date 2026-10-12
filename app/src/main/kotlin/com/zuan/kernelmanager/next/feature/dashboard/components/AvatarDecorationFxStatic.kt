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

/*
 * ===========================================================================
 *  Dekorasi avatar STATIS (tidak bergerak) EPIC PREMIUM, MYTHIC & DIVINE
 *  Digambar SEKALI tanpa clock animasi (ringan dan hemat daya).
 * ===========================================================================
 */

// ---------------------------------------------------------------------------
// STATIC-1: OBSIDIAN EDGE — ring hitam glossy dengan tepi perak & highlight
// ---------------------------------------------------------------------------
internal fun DrawScope.drawObsidianEdge(c: Offset, r: Float, w: Float) {
    val black = Color(0xFF101014)
    val gloss = listOf(Color(0xFF4A4A55), black, Color(0xFF1C1C22), Color(0xFF55555F), black)
    drawCircle(Color.Black.copy(alpha = 0.4f), r + w * 0.5f, c, style = Stroke(w * 1.4f))
    drawCircle(Brush.sweepGradient(gloss, c), r, c, style = Stroke(w * 1.05f))
    drawCircle(Color(0xFF9E9EAE).copy(alpha = 0.7f), r + w * 0.62f, c, style = Stroke(w * 0.12f))
    drawCircle(Color(0xFF9E9EAE).copy(alpha = 0.35f), r - w * 0.62f, c, style = Stroke(w * 0.12f))
    ringArc(Color.White, 200f, 80f, c, r, Stroke(w * 0.4f, cap = StrokeCap.Round), 0.35f)
}

// ---------------------------------------------------------------------------
// STATIC-2: JADE DYNASTY — ring giok hijau dengan inlay busur emas
// ---------------------------------------------------------------------------
internal fun DrawScope.drawJadeDynasty(c: Offset, r: Float, w: Float) {
    val jade = Color(0xFF00A86B)
    val jadeLight = Color(0xFF7FFFD4)
    val gold = Color(0xFFFFD54F)
    drawCircle(jade.copy(alpha = 0.2f), r + w * 0.4f, c, style = Stroke(w * 1.8f))
    drawCircle(
        Brush.sweepGradient(listOf(Color(0xFF00695C), jade, jadeLight, jade, Color(0xFF00695C)), c),
        r, c, style = Stroke(w * 1.0f)
    )
    for (i in 0 until 4) ringArc(gold, i * 90f + 15f, 60f, c, r, Stroke(w * 0.28f))
    drawCircle(gold.copy(alpha = 0.8f), r + w * 0.68f, c, style = Stroke(w * 0.14f))
    drawCircle(gold.copy(alpha = 0.8f), r - w * 0.68f, c, style = Stroke(w * 0.14f))
}

// ---------------------------------------------------------------------------
// STATIC-3: STEAMPUNK GEAR — cincin gigi perunggu 12 gigi + baut
// ---------------------------------------------------------------------------
internal fun DrawScope.drawSteampunkGear(c: Offset, r: Float, w: Float) {
    val copper = Color(0xFFB87333)
    val dark = Color(0xFF4E342E)
    drawCircle(dark, r, c, style = Stroke(w * 1.2f))
    drawCircle(
        Brush.sweepGradient(listOf(dark, copper, Color(0xFFFFE0B2), copper, dark), c),
        r, c, style = Stroke(w * 0.85f)
    )
    for (i in 0 until 12) {
        val a = DECO_TAU * i / 12f
        val half = DECO_TAU / 12f * 0.3f
        val p1 = decoPt(c, r + w * 0.5f, a - half)
        val p2 = decoPt(c, r + w * 1.3f, a - half * 0.55f)
        val p3 = decoPt(c, r + w * 1.3f, a + half * 0.55f)
        val p4 = decoPt(c, r + w * 0.5f, a + half)
        val tooth = Path().apply {
            moveTo(p1.x, p1.y)
            lineTo(p2.x, p2.y)
            lineTo(p3.x, p3.y)
            lineTo(p4.x, p4.y)
            close()
        }
        drawPath(tooth, copper)
        drawPath(tooth, dark, style = Stroke(w * 0.08f, join = StrokeJoin.Round))
    }
    for (i in 0 until 6) {
        val p = decoPt(c, r - w * 0.1f, DECO_TAU * i / 6f + 0.26f)
        drawCircle(dark, w * 0.2f, p)
        drawCircle(Color(0xFFFFE0B2).copy(alpha = 0.8f), w * 0.08f, p)
    }
}

// ---------------------------------------------------------------------------
// STATIC-4: PEARL LACE — kalung 16 mutiara mengelilingi avatar
// ---------------------------------------------------------------------------
internal fun DrawScope.drawPearlLace(c: Offset, r: Float, w: Float) {
    drawCircle(Color(0xFFF8E8F0).copy(alpha = 0.25f), r + w * 0.4f, c, style = Stroke(w * 1.6f))
    drawCircle(Color(0xFFF3E5F5).copy(alpha = 0.6f), r, c, style = Stroke(w * 0.3f))
    val n = 16
    for (i in 0 until n) {
        val p = decoPt(c, r, DECO_TAU * i / n)
        val pr = w * 0.42f
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White, Color(0xFFFCE4EC), Color(0xFFE1BEE7)),
                center = Offset(p.x - pr * 0.35f, p.y - pr * 0.35f), radius = pr * 1.6f
            ),
            pr, p
        )
    }
}

// ---------------------------------------------------------------------------
// STATIC-5: CARBON CORE — ring serat karbon gelap + 6 aksen heksagon
// ---------------------------------------------------------------------------
internal fun DrawScope.drawCarbonCore(c: Offset, r: Float, w: Float) {
    val carbon = Color(0xFF21262B)
    val accent = Color(0xFFFF3D00)
    drawCircle(Color.Black.copy(alpha = 0.35f), r + w * 0.4f, c, style = Stroke(w * 1.6f))
    drawCircle(
        Brush.sweepGradient(listOf(carbon, Color(0xFF3A4149), carbon, Color(0xFF2A3036), carbon), c),
        r, c, style = Stroke(w * 1.05f)
    )
    for (i in 0 until 6) {
        val p = decoPt(c, r, DECO_TAU * i / 6f)
        val hex = Path()
        for (k in 0 until 6) {
            val q = decoPt(p, w * 0.42f, DECO_TAU * k / 6f + 0.52f)
            if (k == 0) hex.moveTo(q.x, q.y) else hex.lineTo(q.x, q.y)
        }
        hex.close()
        drawPath(hex, if (i % 2 == 0) accent else Color(0xFF37474F))
        drawPath(hex, Color.White.copy(alpha = 0.25f), style = Stroke(w * 0.06f, join = StrokeJoin.Round))
    }
    drawCircle(accent.copy(alpha = 0.85f), r - w * 0.62f, c, style = Stroke(w * 0.12f))
}

// ---------------------------------------------------------------------------
// STATIC-6: MARBLE LUXE — ring marmer putih dengan urat abu-abu
// ---------------------------------------------------------------------------
internal fun DrawScope.drawMarbleLuxe(c: Offset, r: Float, w: Float) {
    drawCircle(Color(0xFF90A4AE).copy(alpha = 0.25f), r + w * 0.5f, c, style = Stroke(w * 1.6f))
    drawCircle(
        Brush.linearGradient(
            listOf(Color(0xFFFAFAFA), Color(0xFFCFD8DC), Color(0xFFFFFFFF), Color(0xFFECEFF1)),
            Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r)
        ),
        r, c, style = Stroke(w * 1.0f)
    )
    for (k in 0 until 3) {
        val vein = decoWavyPath(c, r + (k - 1) * w * 0.4f, w * 0.3f, 5 + k, k * 2.1f)
        drawPath(vein, Color(0xFF78909C).copy(alpha = 0.55f - 0.12f * k), style = Stroke(w * (0.14f - 0.02f * k), join = StrokeJoin.Round))
    }
    drawCircle(Color(0xFFB0BEC5).copy(alpha = 0.7f), r + w * 0.66f, c, style = Stroke(w * 0.12f))
}

// ---------------------------------------------------------------------------
// STATIC-7: TRIBAL SPIRIT — paku tribal selang-seling hitam & oranye
// ---------------------------------------------------------------------------
internal fun DrawScope.drawTribalSpirit(c: Offset, r: Float, w: Float) {
    val ink = Color(0xFF1A1A1A)
    val accent = Color(0xFFE65100)
    drawCircle(ink, r, c, style = Stroke(w * 0.8f))
    drawCircle(accent.copy(alpha = 0.9f), r, c, style = Stroke(w * 0.25f))
    val n = 18
    for (i in 0 until n) {
        val a = DECO_TAU * i / n
        val long = i % 2 == 0
        val tip = decoPt(c, r + w * (if (long) 1.5f else 0.9f), a)
        val b1 = decoPt(c, r + w * 0.35f, a - 0.07f)
        val b2 = decoPt(c, r + w * 0.35f, a + 0.07f)
        val tri = Path().apply {
            moveTo(b1.x, b1.y)
            lineTo(tip.x, tip.y)
            lineTo(b2.x, b2.y)
            close()
        }
        drawPath(tri, if (long) ink else accent)
    }
}

// ---------------------------------------------------------------------------
// STATIC-8: SILVER WINGS — sepasang sayap perak 3 bulu di kiri & kanan
// ---------------------------------------------------------------------------
internal fun DrawScope.drawSilverWings(c: Offset, r: Float, w: Float) {
    val silver = Color(0xFFB0BEC5)
    val silverLight = Color(0xFFECEFF1)
    drawCircle(
        Brush.linearGradient(
            listOf(silverLight, silver, Color(0xFF78909C), silverLight),
            Offset(c.x - r, c.y), Offset(c.x + r, c.y)
        ),
        r, c, style = Stroke(w * 0.8f)
    )
    for (side in listOf(0f, 180f)) {
        val dir = if (side == 180f) -1f else 1f
        for (k in 0 until 3) {
            val baseA = decoRad(side + (k - 1) * 22f)
            val base = decoPt(c, r, baseA)
            val tipOut = decoPt(c, r + w * (2.2f - 0.5f * k), baseA)
            val tip = Offset(tipOut.x + dir * w * 0.8f, tipOut.y - w * (0.8f - 0.2f * k))
            val feather = Path().apply {
                moveTo(base.x, base.y - w * 0.3f)
                quadraticBezierTo(base.x + dir * w * 1.2f, base.y - w * 1.1f, tip.x, tip.y)
                quadraticBezierTo(base.x + dir * w * 0.8f, base.y - w * 0.1f, base.x, base.y + w * 0.3f)
                close()
            }
            drawPath(feather, Brush.linearGradient(listOf(silverLight, silver), base, tip))
            drawPath(feather, Color(0xFF546E7A).copy(alpha = 0.7f), style = Stroke(w * 0.07f, join = StrokeJoin.Round))
        }
    }
    decoSparkle(Offset(c.x, c.y - r - w * 0.3f), w * 0.55f, silverLight, 0.95f)
}

// ---------------------------------------------------------------------------
// STATIC-MYTHIC-1: ROYAL CREST — ring emas, 4 permata & mahkota kecil di atas
// ---------------------------------------------------------------------------
internal fun DrawScope.drawRoyalCrest(c: Offset, r: Float, w: Float) {
    val gold = Color(0xFFD4AF37)
    val goldDeep = Color(0xFF8C6D1F)
    val red = Color(0xFFDC143C)
    drawCircle(gold.copy(alpha = 0.2f), r + w * 0.5f, c, style = Stroke(w * 1.8f))
    drawCircle(
        Brush.sweepGradient(listOf(goldDeep, gold, Color(0xFFFFF3C4), gold, goldDeep), c),
        r, c, style = Stroke(w * 1.0f)
    )
    drawCircle(gold.copy(alpha = 0.9f), r + w * 0.75f, c, style = Stroke(w * 0.15f))
    for (i in 0 until 4) {
        val p = decoPt(c, r, decoRad(45f + i * 90f))
        drawCircle(if (i % 2 == 0) red else Color(0xFF2962FF), w * 0.5f, p)
        drawCircle(Color.White.copy(alpha = 0.85f), w * 0.18f, Offset(p.x - w * 0.14f, p.y - w * 0.14f))
        drawCircle(goldDeep, w * 0.5f, p, style = Stroke(w * 0.12f))
    }
    val baseY = c.y - r - w * 0.1f
    val cw = w * 2.6f
    val crown = Path().apply {
        moveTo(c.x - cw, baseY)
        lineTo(c.x - cw, baseY - w * 1.1f)
        lineTo(c.x - cw * 0.5f, baseY - w * 0.5f)
        lineTo(c.x, baseY - w * 1.6f)
        lineTo(c.x + cw * 0.5f, baseY - w * 0.5f)
        lineTo(c.x + cw, baseY - w * 1.1f)
        lineTo(c.x + cw, baseY)
        close()
    }
    drawPath(crown, Brush.verticalGradient(listOf(Color(0xFFFFF3C4), gold, goldDeep), baseY - w * 1.6f, baseY))
    drawPath(crown, goldDeep, style = Stroke(w * 0.1f, join = StrokeJoin.Round))
    drawCircle(red, w * 0.3f, Offset(c.x, baseY - w * 0.7f))
}

// ---------------------------------------------------------------------------
// STATIC-MYTHIC-2: ONYX CROWN — paku mahkota onyx-emas di busur atas
// ---------------------------------------------------------------------------
internal fun DrawScope.drawOnyxCrown(c: Offset, r: Float, w: Float) {
    val onyx = Color(0xFF14141A)
    val gold = Color(0xFFFFC107)
    drawCircle(Color.Black.copy(alpha = 0.35f), r + w * 0.4f, c, style = Stroke(w * 1.5f))
    drawCircle(
        Brush.sweepGradient(listOf(onyx, Color(0xFF2E2E38), onyx, Color(0xFF23232B), onyx), c),
        r, c, style = Stroke(w * 0.95f)
    )
    drawCircle(gold.copy(alpha = 0.9f), r - w * 0.6f, c, style = Stroke(w * 0.14f))
    for (j in 0 until 5) {
        val a = decoRad(-90f + (j - 2) * 26f)
        val base = decoPt(c, r, a)
        val tall = j == 2
        val tip = decoPt(c, r + w * (if (tall) 2.4f else 1.7f), a)
        val half = 0.075f
        val b1 = decoPt(c, r, a - half)
        val b2 = decoPt(c, r, a + half)
        val spike = Path().apply {
            moveTo(b1.x, b1.y)
            lineTo(tip.x, tip.y)
            lineTo(b2.x, b2.y)
            close()
        }
        drawPath(spike, Brush.linearGradient(listOf(Color(0xFF3A3A46), onyx), base, tip))
        drawPath(spike, gold.copy(alpha = 0.85f), style = Stroke(w * 0.09f, join = StrokeJoin.Round))
        drawCircle(gold, w * (if (tall) 0.28f else 0.2f), tip)
    }
}

// ---------------------------------------------------------------------------
// STATIC-NEW-1: DIVINE IMPERIAL CROWN (Divine Regal)
// ---------------------------------------------------------------------------
internal fun DrawScope.drawDivineImperialCrown(c: Offset, r: Float, w: Float) {
    val gold = Color(0xFFFFD54F)
    val goldDark = Color(0xFFC79100)
    val ruby = Color(0xFFD50000)
    val emerald = Color(0xFF00C853)

    drawCircle(gold.copy(alpha = 0.2f), r + w * 0.5f, c, style = Stroke(w * 1.8f))
    drawCircle(
        brush = Brush.sweepGradient(listOf(goldDark, gold, Color(0xFFFFF9C4), gold, goldDark), c),
        radius = r,
        center = c,
        style = Stroke(w * 1.1f)
    )

    val crownBaseY = c.y - r - w * 0.1f
    val cw = w * 3.2f
    val crownPath = Path().apply {
        moveTo(c.x - cw, crownBaseY)
        lineTo(c.x - cw * 0.85f, crownBaseY - w * 1.6f)
        lineTo(c.x - cw * 0.4f, crownBaseY - w * 0.7f)
        lineTo(c.x, crownBaseY - w * 2.3f)
        lineTo(c.x + cw * 0.4f, crownBaseY - w * 0.7f)
        lineTo(c.x + cw * 0.85f, crownBaseY - w * 1.6f)
        lineTo(c.x + cw, crownBaseY)
        close()
    }
    drawPath(crownPath, Brush.verticalGradient(listOf(Color(0xFFFFF9C4), gold, goldDark), crownBaseY - w * 2.3f, crownBaseY))
    drawPath(crownPath, goldDark, style = Stroke(w * 0.12f, join = StrokeJoin.Round))

    drawCircle(ruby, w * 0.45f, Offset(c.x, crownBaseY - w * 1.4f))
    drawCircle(emerald, w * 0.28f, Offset(c.x - cw * 0.65f, crownBaseY - w * 0.9f))
    drawCircle(emerald, w * 0.28f, Offset(c.x + cw * 0.65f, crownBaseY - w * 0.9f))
}

// ---------------------------------------------------------------------------
// STATIC-NEW-2: VOID REAPER SCYTHE (Mythic Dark)
// ---------------------------------------------------------------------------
internal fun DrawScope.drawVoidReaperScythe(c: Offset, r: Float, w: Float) {
    val obsidian = Color(0xFF1A1A24)
    val purpleRune = Color(0xFFD500F9)
    val steel = Color(0xFF78909C)

    drawCircle(obsidian, r, c, style = Stroke(w * 1.2f))
    drawCircle(purpleRune.copy(alpha = 0.8f), r, c, style = Stroke(w * 0.25f))

    for (side in listOf(-1f, 1f)) {
        val startPt = Offset(c.x + side * r * 0.9f, c.y + r * 0.2f)
        val tipPt = Offset(c.x + side * (r + w * 2.2f), c.y - r * 0.9f)
        val scythe = Path().apply {
            moveTo(startPt.x, startPt.y)
            quadraticBezierTo(c.x + side * (r + w * 2.6f), c.y - r * 0.1f, tipPt.x, tipPt.y)
            quadraticBezierTo(c.x + side * (r + w * 1.2f), c.y - r * 0.3f, startPt.x, startPt.y - w * 0.8f)
            close()
        }
        drawPath(scythe, Brush.linearGradient(listOf(steel, obsidian), startPt, tipPt))
        drawPath(scythe, purpleRune, style = Stroke(w * 0.08f, join = StrokeJoin.Round))
    }
}

// ---------------------------------------------------------------------------
// STATIC-NEW-3: SOLARIS EMBLEM (Legendary Ancient)
// ---------------------------------------------------------------------------
internal fun DrawScope.drawSolarisEmblem(c: Offset, r: Float, w: Float) {
    val ancientGold = Color(0xFFFFB300)
    val darkBrass = Color(0xFF5D4037)
    val topaz = Color(0xFFFF6F00)

    drawCircle(ancientGold, r, c, style = Stroke(w * 0.9f))
    drawCircle(darkBrass, r - w * 0.5f, c, style = Stroke(w * 0.15f))

    for (i in 0 until 12) {
        val a = DECO_TAU * i / 12f
        val isMajor = i % 2 == 0
        val rayLen = if (isMajor) w * 1.8f else w * 1.1f
        val tip = decoPt(c, r + rayLen, a)
        val b1 = decoPt(c, r + w * 0.4f, a - 0.12f)
        val b2 = decoPt(c, r + w * 0.4f, a + 0.12f)

        val ray = Path().apply {
            moveTo(b1.x, b1.y)
            lineTo(tip.x, tip.y)
            lineTo(b2.x, b2.y)
            close()
        }
        drawPath(ray, if (isMajor) ancientGold else topaz)
        drawPath(ray, darkBrass, style = Stroke(w * 0.08f, join = StrokeJoin.Round))
    }
}

// ---------------------------------------------------------------------------
// STATIC-NEW-4: CYBER EXOSKELETON (Legendary Mecha)
// ---------------------------------------------------------------------------
internal fun DrawScope.drawCyberExoskeleton(c: Offset, r: Float, w: Float) {
    val mechaWhite = Color(0xFFECEFF1)
    val carbon = Color(0xFF263238)
    val neonOrange = Color(0xFFFF6D00)

    drawCircle(carbon, r, c, style = Stroke(w * 1.3f))
    drawCircle(mechaWhite, r + w * 0.5f, c, style = Stroke(w * 0.3f))

    for (i in 0 until 4) {
        val a = decoRad(45f + i * 90f)
        val p = decoPt(c, r, a)
        val hex = Path()
        for (k in 0 until 6) {
            val q = decoPt(p, w * 0.65f, DECO_TAU * k / 6f)
            if (k == 0) hex.moveTo(q.x, q.y) else hex.lineTo(q.x, q.y)
        }
        hex.close()
        drawPath(hex, mechaWhite)
        drawPath(hex, carbon, style = Stroke(w * 0.1f, join = StrokeJoin.Round))
        drawCircle(neonOrange, w * 0.25f, p)
    }

    for (i in 0 until 4) {
        ringArc(neonOrange, i * 90f + 15f, 60f, c, r - w * 0.45f, Stroke(w * 0.2f))
    }
}
