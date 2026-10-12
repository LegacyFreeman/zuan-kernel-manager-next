/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.dashboard.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.sin

/** ZKM NEXT Ultimate 3D collection. Every design uses the shared depth-aware 3D renderer. */

internal fun DrawScope.drawPrismaticReactor(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val big = r * 1.07f
    val cyan = Color(0xFF80D8FF)
    val violet = Color(0xFFB388FF)
    val pink = Color(0xFFFF80AB)
    val a = Rot3(0.8f + 0.1f * sin(t * 0.5f), t * 0.42f, t * 0.32f)
    val b = Rot3(1.3f, -t * 0.36f, 1.05f - t * 0.2f)
    val d = Rot3(0.2f + t * 0.25f, 1.0f, -t * 0.55f)
    ring3D(c, big, 56, a, cyan, w * 0.34f, layer, minA = 0.18f)
    ring3D(c, big * 0.86f, 56, b, violet, w * 0.30f, layer, minA = 0.2f)
    ring3D(c, big * 0.67f, 48, d, pink, w * 0.24f, layer, minA = 0.16f)
    for (i in 0 until 8) {
        val rot = when (i % 3) { 0 -> a; 1 -> b; else -> d }
        val color = when (i % 3) { 0 -> cyan; 1 -> violet; else -> pink }
        node3D(c, big * (0.75f + 0.04f * (i % 3)), rot, t * (0.72f + i * 0.03f) + i * 0.785f, w * 0.28f, color, layer)
    }
}

internal fun DrawScope.drawCelestialArmillary(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val big = r * 1.08f
    val gold = Color(0xFFFFD180)
    val blue = Color(0xFF80DEEA)
    val white = Color(0xFFFFF8E1)
    val rotations = listOf(
        Rot3(0.38f, 0.2f, t * 0.18f),
        Rot3(1.05f, 0.55f, -t * 0.24f + 0.8f),
        Rot3(1.48f, -0.36f, t * 0.12f + 1.4f),
        Rot3(0.75f, 1.1f, -t * 0.16f)
    )
    val colors = listOf(gold, blue, white, Color(0xFFB39DDB))
    rotations.forEachIndexed { index, rot ->
        ring3D(c, big * (1.02f - index * 0.075f), 64, rot, colors[index], w * (0.27f - index * 0.025f), layer, minA = 0.2f)
        for (gem in 0 until (if (index == 0) 4 else 2)) {
            node3D(c, big * (1.02f - index * 0.075f), rot, t * (0.3f + index * 0.08f) + gem * 3.14159f, w * 0.22f, colors[index], layer)
        }
    }
    node3D(c, big * 0.22f, Rot3(0.6f, t * 0.2f, 0f), t * 0.55f, w * 0.32f, gold, layer)
}

internal fun DrawScope.drawNebulaDragon(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val big = r * 1.04f
    val violet = Color(0xFF7C4DFF)
    val teal = Color(0xFF1DE9B6)
    val ember = Color(0xFFFF7043)
    val r1 = Rot3(1.05f, 0.25f, t * 0.38f)
    val r2 = Rot3(0.82f, -0.55f, -t * 0.32f + 1.2f)
    ring3D(c, big, 72, r1, violet, w * 0.34f, layer, minA = 0.13f)
    ring3D(c, big * 0.95f, 72, r2, teal, w * 0.25f, layer, minA = 0.15f)
    for (i in 0 until 12) {
        val angle = t * 0.72f + i * 6.2831855f / 12f
        node3D(c, big * (0.9f + 0.06f * sin(angle * 2f)), if (i % 2 == 0) r1 else r2, angle, w * (0.25f + 0.04f * (i % 3)), if (i % 3 == 0) ember else if (i % 2 == 0) violet else teal, layer)
    }
    // Head-like jewel at the upper orbit and a tapering tail of scale nodes.
    node3D(c, big * 0.98f, r1, -1.38f + t * 0.72f, w * 0.48f, Color(0xFFFFD180), layer)
    for (i in 0 until 4) node3D(c, big * (0.92f - i * 0.1f), r2, 1.55f + i * 0.22f + t * 0.72f, w * (0.22f - i * 0.025f), teal, layer)
}

internal fun DrawScope.drawTitanRing(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val big = r * 1.08f
    val steel = Color(0xFFB0BEC5)
    val chrome = Color(0xFFECEFF1)
    val orange = Color(0xFFFF6D00)
    val outer = Rot3(1.22f + 0.06f * sin(t * 0.35f), 0.12f, t * 0.16f)
    val inner = Rot3(1.14f, -0.16f, -t * 0.12f + 0.8f)
    ring3D(c, big, 72, outer, steel, w * 0.43f, layer, minA = 0.25f, glow = false)
    ring3D(c, big * 0.87f, 72, inner, chrome, w * 0.28f, layer, minA = 0.28f, glow = false)
    for (i in 0 until 12) {
        val ang = i * 6.2831855f / 12f + t * 0.16f
        node3D(c, big, outer, ang, w * 0.29f, if (i % 3 == 0) orange else chrome, layer)
    }
    for (i in 0 until 6) node3D(c, big * 0.88f, inner, -t * 0.3f + i * 6.2831855f / 6f, w * 0.19f, steel, layer)
}

internal fun DrawScope.drawAetherCube(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val a = r * 0.78f
    val rot = Rot3(t * 0.4f + 0.55f, t * 0.55f + 0.35f, t * 0.3f)
    val xs = FloatArray(8)
    val ys = FloatArray(8)
    val zs = FloatArray(8)
    for (i in 0 until 8) {
        val x = if ((i and 1) == 0) -a else a
        val y = if ((i and 2) == 0) -a else a
        val z = if ((i and 4) == 0) -a else a
        xs[i] = rot.px(x, y, z); ys[i] = rot.py(x, y, z); zs[i] = rot.pz(x, y, z)
    }
    val cyan = Color(0xFF18FFFF)
    val purple = Color(0xFFB388FF)
    for (i in 0 until 8) for (bit in intArrayOf(1, 2, 4)) {
        val j = i xor bit
        if (j > i) seg3D(c, a * 1.75f, xs[i], ys[i], zs[i], xs[j], ys[j], zs[j], if ((i + j) % 2 == 0) cyan else purple, w * 0.28f, layer, 0.28f, true)
    }
    for (i in 0 until 8) {
        if ((zs[i] >= 0f) != (layer == DecoLayer.FRONT)) continue
        val d = depth01(zs[i], a * 1.7f)
        val s = pf(zs[i], a * 1.7f)
        shadedSphere(Offset(c.x + xs[i] * s, c.y + ys[i] * s), w * 0.24f * (0.7f + d), if (i % 2 == 0) cyan else purple, 0.75f + d * 0.25f)
    }
    ring3D(c, r * 0.98f, 64, Rot3(1.25f, 0f, -t * 0.22f), Color(0xFF80DEEA), w * 0.17f, layer, minA = 0.1f)
}

internal fun DrawScope.drawOrbitalCrown(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val big = r * 1.05f
    val gold = Color(0xFFFFD54F)
    val ruby = Color(0xFFFF1744)
    val pearl = Color(0xFFFFF8E1)
    val rot = Rot3(1.18f, 0.05f, t * 0.14f)
    val rot2 = Rot3(0.68f, 0.38f, -t * 0.19f + 0.55f)
    ring3D(c, big, 64, rot, gold, w * 0.34f, layer, minA = 0.24f, glow = false)
    ring3D(c, big * 0.86f, 64, rot2, pearl, w * 0.21f, layer, minA = 0.22f, glow = false)
    for (i in 0 until 5) {
        val angle = -2.2f + i * 1.1f + t * 0.12f
        node3D(c, big, rot, angle, w * (if (i == 2) 0.55f else 0.32f), if (i == 2) ruby else gold, layer)
    }
    for (i in 0 until 8) node3D(c, big * 0.86f, rot2, t * 0.2f + i * 6.2831855f / 8f, w * 0.16f, pearl, layer)
}

internal fun DrawScope.drawCrystalHeart(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val a = r * 0.79f
    val rot = Rot3(0.9f + t * 0.2f, 0.55f, -t * 0.31f)
    val x = floatArrayOf(0f, a, 0f, -a, 0f, 0f)
    val y = floatArrayOf(-a, 0f, a, 0f, 0f, 0f)
    val z = floatArrayOf(0f, 0f, 0f, 0f, a, -a)
    val vx = FloatArray(6); val vy = FloatArray(6); val vz = FloatArray(6)
    for (i in x.indices) { vx[i] = rot.px(x[i], y[i], z[i]); vy[i] = rot.py(x[i], y[i], z[i]); vz[i] = rot.pz(x[i], y[i], z[i]) }
    val edges = arrayOf(0 to 1, 1 to 2, 2 to 3, 3 to 0, 0 to 4, 1 to 4, 2 to 4, 3 to 4, 0 to 5, 1 to 5, 2 to 5, 3 to 5)
    val aqua = Color(0xFF80CBC4)
    val pink = Color(0xFFF8BBD0)
    for ((i, j) in edges) seg3D(c, a * 1.5f, vx[i], vy[i], vz[i], vx[j], vy[j], vz[j], if ((i + j) % 2 == 0) aqua else pink, w * 0.27f, layer, 0.24f, true)
    for (i in x.indices) if ((vz[i] >= 0f) == (layer == DecoLayer.FRONT)) {
        val d = depth01(vz[i], a)
        shadedSphere(Offset(c.x + vx[i] * pf(vz[i], a), c.y + vy[i] * pf(vz[i], a)), w * (0.24f + d * 0.13f), if (i % 2 == 0) aqua else pink, 0.65f + 0.35f * d)
    }
    ring3D(c, r * 1.01f, 56, Rot3(1.15f, 0f, t * 0.15f), Color(0xFFD1C4E9), w * 0.17f, layer, minA = 0.12f)
}

internal fun DrawScope.drawEclipseEngine(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val big = r * 1.08f
    val darkBlue = Color(0xFF283593)
    val cyan = Color(0xFF80DEEA)
    val gold = Color(0xFFFFD180)
    val outer = Rot3(1.18f, 0.08f, t * 0.12f)
    val middle = Rot3(0.98f, 0.5f, -t * 0.18f + 0.9f)
    val inner = Rot3(1.38f, -0.42f, t * 0.22f + 0.45f)
    ring3D(c, big, 72, outer, gold, w * 0.28f, layer, minA = 0.18f, glow = false)
    ring3D(c, big * 0.88f, 72, middle, cyan, w * 0.28f, layer, minA = 0.2f)
    ring3D(c, big * 0.65f, 64, inner, darkBlue, w * 0.38f, layer, minA = 0.26f)
    for (i in 0 until 10) node3D(c, big, outer, t * 0.22f + i * 6.2831855f / 10f, w * 0.18f, if (i % 2 == 0) gold else Color.White, layer)
    for (i in 0 until 6) node3D(c, big * 0.65f, inner, -t * 0.45f + i * 6.2831855f / 6f, w * 0.2f, cyan, layer)
}

internal fun DrawScope.drawPhoenixNebula(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val big = r * 1.06f
    val fire = Color(0xFFFF7043)
    val gold = Color(0xFFFFD54F)
    val magenta = Color(0xFFFF80AB)
    val outer = Rot3(1.05f, 0.1f, t * 0.26f)
    val wingL = Rot3(0.65f, -0.7f, t * 0.18f + 0.5f)
    val wingR = Rot3(0.65f, 0.7f, -t * 0.18f - 0.5f)
    ring3D(c, big, 72, outer, fire, w * 0.31f, layer, minA = 0.17f)
    ring3D(c, big * 0.83f, 60, wingL, gold, w * 0.25f, layer, minA = 0.16f)
    ring3D(c, big * 0.83f, 60, wingR, magenta, w * 0.25f, layer, minA = 0.16f)
    for (i in 0 until 12) {
        val ang = t * (0.7f + 0.025f * i) + i * 6.2831855f / 12f
        node3D(c, big * (0.84f + 0.05f * sin(ang * 3f)), if (i % 2 == 0) wingL else wingR, ang, w * (0.2f + 0.04f * (i % 3)), if (i % 3 == 0) gold else fire, layer)
    }
}

internal fun DrawScope.drawQuantumTrident(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val big = r * 1.02f
    val aqua = Color(0xFF18FFFF)
    val gold = Color(0xFFFFD54F)
    val violet = Color(0xFFB388FF)
    val orbitA = Rot3(1.1f, 0.12f, t * 0.22f)
    val orbitB = Rot3(1.2f, 0.72f, -t * 0.18f + 0.7f)
    ring3D(c, big, 64, orbitA, aqua, w * 0.28f, layer, minA = 0.22f)
    ring3D(c, big * 0.88f, 64, orbitB, violet, w * 0.28f, layer, minA = 0.22f)
    // Three prongs are separate depth-aware 3D rods around the orbit, not a flat overlay.
    for (i in 0 until 3) {
        val angle = t * 0.16f + i * 2.0943952f
        val x0 = big * 0.42f * cos(angle); val y0 = big * 0.42f * sin(angle)
        val x1 = big * 0.9f * cos(angle + 0.18f); val y1 = big * 0.9f * sin(angle + 0.18f)
        val z0 = big * 0.34f * sin(angle * 1.5f); val z1 = -z0 * 0.72f
        val rot = Rot3(0.5f + i * 0.2f, 0.6f, t * 0.1f)
        seg3D(c, big, rot.px(x0, y0, z0), rot.py(x0, y0, z0), rot.pz(x0, y0, z0), rot.px(x1, y1, z1), rot.py(x1, y1, z1), rot.pz(x1, y1, z1), if (i == 1) gold else aqua, w * 0.52f, layer, 0.25f, true)
        node3D(c, big * 0.9f, orbitA, angle, w * 0.32f, if (i == 1) gold else aqua, layer)
    }
}
