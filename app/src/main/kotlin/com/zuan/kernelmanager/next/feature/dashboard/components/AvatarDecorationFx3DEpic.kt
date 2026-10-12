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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/*
 * ===========================================================================
 *  Dekorasi avatar 3D EPIC PREMIUM, MYTHIC & DIVINE
 *  Memakai mesin 3D: Rot3 + ring3D/node3D/seg3D
 *  z >= 0 digambar di FRONT, z < 0 digambar di BACK (oklusi nyata).
 * ===========================================================================
 */

// ---------------------------------------------------------------------------
// 3D-EPIC-1: PLASMA ORB — bola plasma terang mengorbit dengan jejak memudar
// ---------------------------------------------------------------------------
internal fun DrawScope.drawPlasmaOrb(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val big = r * 1.05f
    val rot = Rot3(1.15f, 0f, 0.4f * sin(t * 0.5f))
    ring3D(c, big, 64, rot, Color(0xFF40C4FF), w * 0.3f, layer, minA = 0.15f)
    for (k in 0 until 6) {
        val ang = t * 2.2f - k * 0.22f
        val col = if (k == 0) Color(0xFFE1F5FE) else Color(0xFF40C4FF).copy(alpha = 1f - 0.15f * k)
        node3D(c, big, rot, ang, w * (0.8f - 0.11f * k), col, layer)
    }
    for (i in 0 until 8) {
        node3D(c, big * 0.92f, rot, -t * 1.4f + i * DECO_TAU / 8f, w * 0.22f, Color(0xFF80D8FF), layer)
    }
}

// ---------------------------------------------------------------------------
// 3D-EPIC-2: HYPER PYRAMID — oktahedron kawat magenta berputar + simpul bercahaya
// ---------------------------------------------------------------------------
internal fun DrawScope.drawHyperPyramid(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val a = r * 0.95f
    val big = a
    val rot = Rot3(t * 0.6f, t * 0.85f, t * 0.25f)
    val lx = floatArrayOf(a, -a, 0f, 0f, 0f, 0f)
    val ly = floatArrayOf(0f, 0f, a, -a, 0f, 0f)
    val lz = floatArrayOf(0f, 0f, 0f, 0f, a, -a)
    val vx = FloatArray(6) { rot.px(lx[it], ly[it], lz[it]) }
    val vy = FloatArray(6) { rot.py(lx[it], ly[it], lz[it]) }
    val vz = FloatArray(6) { rot.pz(lx[it], ly[it], lz[it]) }
    val edges = listOf(
        0 to 2, 0 to 3, 0 to 4, 0 to 5,
        1 to 2, 1 to 3, 1 to 4, 1 to 5,
        2 to 4, 4 to 3, 3 to 5, 5 to 2
    )
    val neon = Color(0xFFE040FB)
    for ((i, j) in edges) {
        seg3D(c, big, vx[i], vy[i], vz[i], vx[j], vy[j], vz[j], neon, w * 0.4f, layer, 0.2f, true)
    }
    for (i in 0 until 6) {
        if ((vz[i] >= 0f) != (layer == DecoLayer.FRONT)) continue
        val d = depth01(vz[i], big)
        val s = pf(vz[i], big)
        shadedSphere(Offset(c.x + vx[i] * s, c.y + vy[i] * s), w * 0.5f * (0.7f + 0.6f * d), Color(0xFFEA80FC), 0.6f + 0.4f * d)
    }
}

// ---------------------------------------------------------------------------
// 3D-EPIC-3: PORTAL GATE — gerbang ganda emas/jingga + percikan di bibir portal
// ---------------------------------------------------------------------------
internal fun DrawScope.drawPortalGate(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val big = r * 1.08f
    val gold = Color(0xFFFFD740)
    val orange = Color(0xFFFF6D00)
    val rotA = Rot3(1.25f + 0.1f * sin(t * 0.8f), 0f, t * 0.5f)
    val rotB = Rot3(1.25f + 0.1f * sin(t * 0.8f + 1f), 0f, -t * 0.35f + 1.2f)
    ring3D(c, big, 72, rotA, gold, w * 0.4f, layer, minA = 0.25f)
    ring3D(c, big * 0.92f, 72, rotB, orange, w * 0.3f, layer, minA = 0.25f)
    for (i in 0 until 10) {
        val col = if (i % 2 == 0) gold else Color.White
        node3D(c, big, rotA, t * 3f + i * DECO_TAU / 10f, w * (0.2f + 0.14f * decoHash(i * 5 + 2)), col, layer)
    }
}

// ---------------------------------------------------------------------------
// 3D-EPIC-4: GRAVITY WELL — cincin bertingkat makin curam + partikel tersedot
// ---------------------------------------------------------------------------
internal fun DrawScope.drawGravityWell(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val cols = listOf(Color(0xFF7C4DFF), Color(0xFF536DFE), Color(0xFF00B0FF))
    for (k in 0 until 3) {
        val rad = r * (1.08f - 0.14f * k)
        val rot = Rot3(1.1f + 0.18f * k + 0.08f * sin(t * 0.6f + k), 0f, t * (0.7f + 0.4f * k))
        ring3D(c, rad, 64, rot, cols[k], w * (0.45f - 0.1f * k), layer, minA = 0.2f)
    }
    val rotP = Rot3(1.2f, 0f, 0f)
    for (i in 0 until 6) {
        val f = decoFract(t * 0.35f + i * 0.167f)
        val rad = r * (1.08f - 0.45f * f)
        node3D(c, rad, rotP, i * 1.3f + t * (1f + 3f * f), w * (0.3f - 0.1f * f), Color(0xFFB3E5FC), layer)
    }
}

// ---------------------------------------------------------------------------
// 3D-EPIC-5: WARP TUNNEL — cincin konsentris berfasa mengecil seperti terowongan warp
// ---------------------------------------------------------------------------
internal fun DrawScope.drawWarpTunnel(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    for (k in 0 until 4) {
        val f = decoFract(t * 0.4f + k * 0.25f)
        val rad = r * (1.12f - 0.35f * f)
        val rot = Rot3(0.9f + 0.3f * f, t * 0.4f + k, t * (0.3f + 0.2f * k))
        val col = if (k % 2 == 0) Color(0xFF00FFD5) else Color(0xFF00B0FF)
        ring3D(c, rad, 48, rot, col, w * (0.5f - 0.08f * k), layer, minA = 0.12f + 0.1f * (1f - f))
    }
}

// ---------------------------------------------------------------------------
// 3D-EPIC-6: ORBITAL BLADES — tiga bilah baja menyinggung orbit 3D
// ---------------------------------------------------------------------------
internal fun DrawScope.drawOrbitalBlades(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val big = r * 1.06f
    val rot = Rot3(1.05f + 0.12f * sin(t * 0.5f), 0f, t * 1.2f)
    ring3D(c, big, 60, rot, Color(0xFFB0BEC5), w * 0.18f, layer, minA = 0.12f, glow = false)
    for (j in 0 until 3) {
        val ang = j * DECO_TAU / 3f
        val x0 = big * cos(ang)
        val y0 = big * sin(ang)
        val x = rot.px(x0, y0, 0f)
        val y = rot.py(x0, y0, 0f)
        val z = rot.pz(x0, y0, 0f)
        if ((z >= 0f) != (layer == DecoLayer.FRONT)) continue
        val d = depth01(z, big)
        val s = pf(z, big)
        val p = Offset(c.x + x * s, c.y + y * s)
        val bladeAng = atan2(y, x)
        val len = w * (1.6f + 0.5f * d)
        val wid = w * (0.5f + 0.2f * d)
        val dx = cos(bladeAng + 1.5708f)
        val dy = sin(bladeAng + 1.5708f)
        val nx = cos(bladeAng)
        val ny = sin(bladeAng)
        val path = Path().apply {
            moveTo(p.x - dx * len, p.y - dy * len)
            lineTo(p.x + nx * wid, p.y + ny * wid)
            lineTo(p.x + dx * len, p.y + dy * len)
            lineTo(p.x - nx * wid, p.y - ny * wid)
            close()
        }
        drawPath(
            path,
            Brush.linearGradient(
                listOf(Color(0xFFECEFF1), Color(0xFF90A4AE), Color(0xFFECEFF1)),
                Offset(p.x - dx * len, p.y - dy * len), Offset(p.x + dx * len, p.y + dy * len)
            ),
            alpha = 0.5f + 0.5f * d
        )
        drawPath(path, Color(0xFF37474F).copy(alpha = 0.4f + 0.4f * d), style = Stroke(w * 0.1f, join = StrokeJoin.Round))
    }
}

// ---------------------------------------------------------------------------
// 3D-EPIC-7: METEOR BELT — sabuk batuan miring: 14 meteor beragam ukuran + debu
// ---------------------------------------------------------------------------
internal fun DrawScope.drawMeteorBelt(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val big = r * 1.1f
    val rot = Rot3(1.3f, 0f, -0.3f)
    ring3D(c, big, 72, rot, Color(0xFF8D6E63), w * 0.14f, layer, minA = 0.1f, glow = false)
    ring3D(c, big * 0.93f, 72, rot, Color(0xFF6D4C41), w * 0.1f, layer, minA = 0.08f, glow = false)
    val cols = listOf(Color(0xFFA1887F), Color(0xFF8D6E63), Color(0xFFD7CCC8))
    for (i in 0 until 14) {
        val ang = t * (0.9f + 0.05f * i) + i * DECO_TAU / 14f
        val size = w * (0.25f + 0.5f * decoHash(i * 11 + 4))
        node3D(c, big * (0.93f + 0.14f * decoHash(i * 3 + 8)), rot, ang, size, cols[i % 3], layer)
    }
}

// ---------------------------------------------------------------------------
// 3D-EPIC-8: NEBULA SPHERE — bola dunia kawat: lintang + meridian + bintang
// ---------------------------------------------------------------------------
internal fun DrawScope.drawNebulaSphere(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val big = r * 1.04f
    val rot = Rot3(0.5f, t * 0.6f, 0f)
    for (k in -1..1) {
        val ringR = big * cos(k * 0.6f)
        val oz = big * sin(k * 0.6f)
        var ox = 0f
        var oy = 0f
        var ozv = 0f
        for (i in 0..48) {
            val a = DECO_TAU * i / 48
            val x0 = ringR * cos(a)
            val y0 = ringR * sin(a)
            val x = rot.px(x0, y0, oz)
            val y = rot.py(x0, y0, oz)
            val z = rot.pz(x0, y0, oz)
            if (i > 0) seg3D(c, big, ox, oy, ozv, x, y, z, Color(0xFFCE93D8), w * 0.22f, layer, 0.15f, false)
            ox = x
            oy = y
            ozv = z
        }
    }
    for (k in 0 until 2) {
        val rotM = Rot3(1.5708f, t * 0.6f + k * 1.5708f, 0f)
        ring3D(c, big, 48, rotM, Color(0xFF9C27B0), w * 0.22f, layer, minA = 0.15f, glow = false)
    }
    for (i in 0 until 6) {
        node3D(c, big, rot, t * (0.8f + 0.2f * i) + i, w * 0.2f, Color(0xFFF3E5F5), layer)
    }
}

// ---------------------------------------------------------------------------
// 3D-MYTHIC-1: DRONE SWARM — 5 drone mini mengorbit: bodi gelap, LED & rotor
// ---------------------------------------------------------------------------
internal fun DrawScope.drawDroneSwarm(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val big = r * 1.05f
    val orbit = Rot3(1.15f, 0.2f * sin(t * 0.4f), 0f)
    ring3D(c, big, 60, orbit, Color(0xFF455A64), w * 0.15f, layer, minA = 0.1f, glow = false)
    for (j in 0 until 5) {
        val ang = t * (1.6f + 0.15f * j) + j * DECO_TAU / 5f
        val x0 = big * cos(ang)
        val y0 = big * sin(ang)
        val x = orbit.px(x0, y0, 0f)
        val y = orbit.py(x0, y0, 0f)
        val z = orbit.pz(x0, y0, 0f)
        if ((z >= 0f) != (layer == DecoLayer.FRONT)) continue
        val d = depth01(z, big)
        val s = pf(z, big)
        val p = Offset(c.x + x * s, c.y + y * s)
        val size = w * (0.55f + 0.35f * d)
        shadedSphere(p, size, Color(0xFF546E7A), 0.55f + 0.45f * d)
        val blink = if (decoFract(t * 1.5f + j * 0.37f) < 0.5f) Color(0xFFFF1744) else Color(0xFF00E5FF)
        drawCircle(blink, size * 0.28f, Offset(p.x, p.y - size * 0.2f))
        drawCircle(Color(0xFFB0BEC5).copy(alpha = 0.35f + 0.4f * d), size * 0.85f, p, style = Stroke(w * 0.12f))
    }
}

// ---------------------------------------------------------------------------
// 3D-MYTHIC-2: CRYSTAL CROWN — mahkota kristal 3D di busur atas avatar
// ---------------------------------------------------------------------------
internal fun DrawScope.drawCrystalCrown(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val big = r * 1.02f
    val rot = Rot3(1.35f + 0.06f * sin(t * 0.7f), 0f, 0f)
    ring3D(c, big, 64, rot, Color(0xFFFFD54F), w * 0.35f, layer, minA = 0.2f)
    val hues = listOf(
        Color(0xFF80D8FF), Color(0xFFB388FF), Color(0xFFE1F5FE), Color(0xFF84FFFF), Color(0xFFEA80FC)
    )
    for (j in 0 until 5) {
        val ang = -1.5708f + (j - 2) * 0.42f + 0.05f * sin(t * 1.2f + j)
        val x0 = big * cos(ang)
        val y0 = big * sin(ang)
        val x = rot.px(x0, y0, 0f)
        val y = rot.py(x0, y0, 0f)
        val z = rot.pz(x0, y0, 0f)
        if ((z >= 0f) != (layer == DecoLayer.FRONT)) continue
        val d = depth01(z, big)
        val s = pf(z, big)
        val p = Offset(c.x + x * s, c.y + y * s)
        val h = w * (2.6f - 0.45f * abs(j - 2)) * (0.8f + 0.4f * d)
        val wid = w * (0.55f - 0.08f * abs(j - 2))
        val na = atan2(y, x)
        val nx = cos(na)
        val ny = sin(na)
        val tx = -ny
        val ty = nx
        val tip = Offset(p.x + nx * h, p.y + ny * h)
        val path = Path().apply {
            moveTo(p.x + tx * wid, p.y + ty * wid)
            lineTo(tip.x + tx * wid * 0.35f, tip.y + ty * wid * 0.35f)
            lineTo(tip.x, tip.y)
            lineTo(tip.x - tx * wid * 0.35f, tip.y - ty * wid * 0.35f)
            lineTo(p.x - tx * wid, p.y - ty * wid)
            close()
        }
        val col = hues[j]
        drawPath(
            path,
            Brush.linearGradient(
                listOf(Color.White, col, Color(col.red * 0.5f, col.green * 0.5f, col.blue * 0.5f, 1f)),
                p, tip
            ),
            alpha = 0.5f + 0.5f * d
        )
        drawPath(path, Color.White.copy(alpha = 0.5f), style = Stroke(w * 0.08f, join = StrokeJoin.Round))
    }
}

// ---------------------------------------------------------------------------
// 3D-NEW-1: CELESTIAL ASTROLABE (Divine Relic)
// ---------------------------------------------------------------------------
internal fun DrawScope.drawCelestialAstrolabe(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val big = r * 1.10f
    val gold = Color(0xFFFFD54F)
    val platinum = Color(0xFFECEFF1)
    val starGlow = Color(0xFFFFF9C4)

    val rA = Rot3(1.2f, t * 0.6f, 0f)
    val rB = Rot3(-0.8f, t * 0.4f + 1.2f, 1.4f)
    val rC = Rot3(0.5f * sin(t * 0.4f), -t * 0.7f, 2.1f)

    ring3D(c, big, 72, rA, gold, w * 0.45f, layer, minA = 0.25f)
    ring3D(c, big * 0.92f, 64, rB, platinum, w * 0.35f, layer, minA = 0.2f)
    ring3D(c, big * 0.84f, 64, rC, gold, w * 0.3f, layer, minA = 0.2f)

    node3D(c, big, rA, t * 1.8f, w * 0.7f, starGlow, layer)
    node3D(c, big * 0.92f, rB, -t * 1.5f, w * 0.6f, gold, layer)
    node3D(c, big * 0.84f, rC, t * 2.2f + 1f, w * 0.55f, platinum, layer)
}

// ---------------------------------------------------------------------------
// 3D-NEW-2: BLACK HOLE ACCRETION (Mythic Cosmic)
// ---------------------------------------------------------------------------
internal fun DrawScope.drawBlackHoleAccretion(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val big = r * 1.15f
    val rotDisk = Rot3(1.35f, 0f, t * 0.35f)
    val blueShift = Color(0xFF00E5FF)
    val redShift = Color(0xFFFF3D00)

    ring3D(c, big, 72, rotDisk, blueShift, w * 0.6f, layer, minA = 0.25f)
    ring3D(c, big * 0.88f, 72, rotDisk, redShift, w * 0.45f, layer, minA = 0.2f)

    val photonRot = Rot3(0.4f * sin(t * 0.5f), 1.25f, 0f)
    ring3D(c, big * 0.76f, 60, photonRot, Color.White, w * 0.25f, layer, minA = 0.3f)

    for (i in 0 until 8) {
        val col = if (i % 2 == 0) blueShift else redShift
        node3D(c, big, rotDisk, t * (2.0f + 0.1f * i) + i * 0.785f, w * 0.4f, col, layer)
    }
}

// ---------------------------------------------------------------------------
// 3D-NEW-3: NEON TESSERACT (Legendary 4D)
// ---------------------------------------------------------------------------
internal fun DrawScope.drawNeonTesseract(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val a = r * 0.68f
    val big = a * 1.6f
    val rot = Rot3(t * 0.75f, t * 0.65f, t * 0.4f)
    val cyan = Color(0xFF00F0FF)
    val violet = Color(0xFFD500F9)

    val vx = FloatArray(8)
    val vy = FloatArray(8)
    val vz = FloatArray(8)
    for (i in 0 until 8) {
        val x0 = if ((i and 1) == 0) -a else a
        val y0 = if ((i and 2) == 0) -a else a
        val z0 = if ((i and 4) == 0) -a else a
        vx[i] = rot.px(x0, y0, z0)
        vy[i] = rot.py(x0, y0, z0)
        vz[i] = rot.pz(x0, y0, z0)
    }

    val edges = intArrayOf(1, 2, 4)
    for (i in 0 until 8) {
        for (bit in edges) {
            val j = i xor bit
            if (j > i) {
                seg3D(c, big, vx[i], vy[i], vz[i], vx[j], vy[j], vz[j], cyan, w * 0.38f, layer, 0.2f, true)
            }
        }
    }
    for (i in 0 until 8) {
        if ((vz[i] >= 0f) != (layer == DecoLayer.FRONT)) continue
        val d = depth01(vz[i], big)
        val s = pf(vz[i], big)
        drawCircle(violet, w * (0.35f + 0.35f * d), Offset(c.x + vx[i] * s, c.y + vy[i] * s))
    }
}

// ---------------------------------------------------------------------------
// 3D-NEW-4: INFINITY OUROBOROS (Mythic Mystical)
// ---------------------------------------------------------------------------
internal fun DrawScope.drawInfinityOuroboros(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val n = 72
    val big = r * 1.1f
    val rot = Rot3(0.95f, 0.35f * sin(t * 0.5f), t * 0.45f)
    val gold = Color(0xFFFFD54F)
    val jade = Color(0xFF00E676)

    var ox = 0f; var oy = 0f; var oz = 0f

    for (i in 0..n) {
        val th = DECO_TAU * i / n
        val scale = big / (1f + sin(th) * sin(th))
        val x0 = scale * cos(th)
        val y0 = scale * sin(th) * cos(th)
        val z0 = w * 1.4f * sin(2f * th + t * 2f)

        val x = rot.px(x0, y0, z0)
        val y = rot.py(x0, y0, z0)
        val z = rot.pz(x0, y0, z0)

        if (i > 0) {
            seg3D(c, big, ox, oy, oz, x, y, z, gold, w * 0.55f, layer, 0.25f, true)
            if (i % 6 == 0) {
                seg3D(c, big, ox, oy, oz, x, y, z, jade, w * 0.2f, layer, 0.2f, false)
            }
        }
        ox = x; oy = y; oz = z
    }
}

// ---------------------------------------------------------------------------
// 3D-NEW-5: SOLAR CORONA (Legendary Solar)
// ---------------------------------------------------------------------------
internal fun DrawScope.drawSolarCorona(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val big = r * 1.08f
    val sunGold = Color(0xFFFFD600)
    val flareRed = Color(0xFFFF3D00)

    val rBase = Rot3(1.1f, 0f, t * 0.5f)
    ring3D(c, big, 64, rBase, sunGold, w * 0.4f, layer, minA = 0.25f)

    for (k in 0 until 4) {
        val loopRot = Rot3(1.2f + 0.3f * k, t * 0.7f + k * 1.57f, k * 0.8f)
        ring3D(c, big * (0.95f + 0.15f * k), 48, loopRot, flareRed, w * (0.32f - 0.05f * k), layer, minA = 0.2f)
        node3D(c, big * (0.95f + 0.15f * k), loopRot, t * 2.5f + k, w * 0.5f, sunGold, layer)
    }
}

// ---------------------------------------------------------------------------
// 3D-NEW-6: VALKYRIE AEGIS (Divine Warrior)
// ---------------------------------------------------------------------------
internal fun DrawScope.drawValkyrieAegis(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val big = r * 1.12f
    val holyGold = Color(0xFFFFD54F)
    val prismBlue = Color(0xFF80D8FF)

    for (k in 0 until 3) {
        val ang = t * 1.2f + k * (DECO_TAU / 3f)
        val orbitRot = Rot3(1.15f, 0.25f * sin(t * 0.6f + k), k * 1.05f)
        ring3D(c, big, 60, orbitRot, holyGold.copy(alpha = 0.3f), w * 0.15f, layer, minA = 0.1f, glow = false)

        val x0 = big * cos(ang)
        val y0 = big * sin(ang)
        val x = orbitRot.px(x0, y0, 0f)
        val y = orbitRot.py(x0, y0, 0f)
        val z = orbitRot.pz(x0, y0, 0f)

        if ((z >= 0f) == (layer == DecoLayer.FRONT)) {
            val d = depth01(z, big)
            val s = pf(z, big)
            val p = Offset(c.x + x * s, c.y + y * s)
            val sw = w * (0.85f + 0.35f * d)
            val path = Path().apply {
                moveTo(p.x, p.y - sw * 1.3f)
                lineTo(p.x + sw, p.y - sw * 0.3f)
                lineTo(p.x + sw, p.y + sw * 1.3f)
                lineTo(p.x - sw, p.y - sw * 0.3f)
                close()
            }
            drawPath(path, Brush.verticalGradient(listOf(holyGold, prismBlue), p.y - sw * 1.3f, p.y + sw * 1.3f))
            drawPath(path, Color.White.copy(alpha = 0.8f), style = Stroke(w * 0.1f, join = StrokeJoin.Round))
        }
    }
}

// ---------------------------------------------------------------------------
// 3D-NEW-7: DNA CYBER SYNTH (Legendary Bio-Tech)
// ---------------------------------------------------------------------------
internal fun DrawScope.drawDnaCyberSynth(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val n = 64
    val turns = 7
    val amp = w * 1.2f
    val big = r + amp
    val rot = Rot3(0.85f, 0.35f * sin(t * 0.4f), t * 0.6f)
    val neonCyan = Color(0xFF00E5FF)
    val neonGreen = Color(0xFF00E676)

    var px0 = 0f; var py0 = 0f; var pz0 = 0f
    var px1 = 0f; var py1 = 0f; var pz1 = 0f

    for (i in 0..n) {
        val th = DECO_TAU * i / n
        val ph = turns * th + t * 2.8f

        val rho0 = r + amp * cos(ph)
        val zz0 = amp * sin(ph)
        val x0 = rot.px(rho0 * cos(th), rho0 * sin(th), zz0)
        val y0 = rot.py(rho0 * cos(th), rho0 * sin(th), zz0)
        val z0 = rot.pz(rho0 * cos(th), rho0 * sin(th), zz0)

        val rho1 = r + amp * cos(ph + 3.1416f)
        val zz1 = amp * sin(ph + 3.1416f)
        val x1 = rot.px(rho1 * cos(th), rho1 * sin(th), zz1)
        val y1 = rot.py(rho1 * cos(th), rho1 * sin(th), zz1)
        val z1 = rot.pz(rho1 * cos(th), rho1 * sin(th), zz1)

        if (i > 0) {
            seg3D(c, big, px0, py0, pz0, x0, y0, z0, neonCyan, w * 0.45f, layer, 0.25f, true)
            seg3D(c, big, px1, py1, pz1, x1, y1, z1, neonGreen, w * 0.45f, layer, 0.25f, true)
        }
        if (i % 4 == 0) {
            seg3D(c, big, x0, y0, z0, x1, y1, z1, Color.White, w * 0.18f, layer, 0.15f, false)
        }
        px0 = x0; py0 = y0; pz0 = z0
        px1 = x1; py1 = y1; pz1 = z1
    }
}
