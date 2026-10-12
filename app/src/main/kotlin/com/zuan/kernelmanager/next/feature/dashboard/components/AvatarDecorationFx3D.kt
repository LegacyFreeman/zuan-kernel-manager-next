/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.dashboard.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * ===========================================================================
 *  Dekorasi avatar 3D — dirancang oleh Claude Sonnet 5.5
 *
 *  Ini 3D sungguhan (bukan sekadar bayangan): tiap titik dirotasi dengan matriks
 *  Euler lalu diberi perspektif ringan. Konvensi: z > 0 = dekat penonton.
 *  Objek dengan z >= 0 digambar di lapisan FRONT (di atas foto/video avatar),
 *  z < 0 digambar di lapisan BACK (di bawah avatar) -> oklusi 3D yang benar.
 *  Kedalaman juga mengatur alpha, tebal garis, dan ukuran bola.
 * ===========================================================================
 */

/** Matriks rotasi: urutan terapan Z, lalu X, lalu Y. */
internal class Rot3(ax: Float, ay: Float, az: Float) {
    val m00: Float
    val m01: Float
    val m02: Float
    val m10: Float
    val m11: Float
    val m12: Float
    val m20: Float
    val m21: Float
    val m22: Float

    init {
        val cx = cos(ax)
        val sx = sin(ax)
        val cy = cos(ay)
        val sy = sin(ay)
        val cz = cos(az)
        val sz = sin(az)
        m00 = cy * cz + sy * sx * sz
        m01 = -cy * sz + sy * sx * cz
        m02 = sy * cx
        m10 = cx * sz
        m11 = cx * cz
        m12 = -sx
        m20 = -sy * cz + cy * sx * sz
        m21 = sy * sz + cy * sx * cz
        m22 = cy * cx
    }

    fun px(x: Float, y: Float, z: Float): Float = m00 * x + m01 * y + m02 * z
    fun py(x: Float, y: Float, z: Float): Float = m10 * x + m11 * y + m12 * z
    fun pz(x: Float, y: Float, z: Float): Float = m20 * x + m21 * y + m22 * z
}

/** Kedalaman ter-normalisasi 0 (jauh) .. 1 (dekat). */
internal fun depth01(z: Float, big: Float): Float = ((z / big).coerceIn(-1f, 1f) + 1f) * 0.5f

/** Faktor perspektif: objek dekat sedikit membesar. */
internal fun pf(z: Float, big: Float): Float = 1f + 0.16f * (z / big).coerceIn(-1f, 1f)

/** Satu ruas garis 3D, hanya digambar bila sisi depan/belakangnya cocok dengan [layer]. */
internal fun DrawScope.seg3D(
    c: Offset, big: Float,
    x0: Float, y0: Float, z0: Float,
    x1: Float, y1: Float, z1: Float,
    color: Color, width: Float, layer: DecoLayer, minA: Float, glow: Boolean
) {
    val zm = (z0 + z1) * 0.5f
    val front = zm >= 0f
    if ((layer == DecoLayer.FRONT) != front) return
    val d = depth01(zm, big)
    val s0 = pf(z0, big)
    val s1 = pf(z1, big)
    val p0 = Offset(c.x + x0 * s0, c.y + y0 * s0)
    val p1 = Offset(c.x + x1 * s1, c.y + y1 * s1)
    val al = minA + (1f - minA) * d
    if (glow && front) drawLine(color, p0, p1, width * 2.4f, StrokeCap.Round, alpha = al * 0.18f)
    drawLine(color, p0, p1, width * (0.65f + 0.55f * d), StrokeCap.Round, alpha = al)
}

/** Cincin (sides besar) atau poligon (sides kecil) di bidang 3D. */
internal fun DrawScope.ring3D(
    c: Offset, big: Float, sides: Int, rot: Rot3,
    color: Color, width: Float, layer: DecoLayer,
    minA: Float = 0.22f, glow: Boolean = true
) {
    var ox = 0f
    var oy = 0f
    var oz = 0f
    for (i in 0..sides) {
        val a = DECO_TAU * i / sides
        val x0 = big * cos(a)
        val y0 = big * sin(a)
        val x = rot.px(x0, y0, 0f)
        val y = rot.py(x0, y0, 0f)
        val z = rot.pz(x0, y0, 0f)
        if (i > 0) seg3D(c, big, ox, oy, oz, x, y, z, color, width, layer, minA, glow)
        ox = x
        oy = y
        oz = z
    }
}

/** Bola ber-shading (highlight kiri-atas + sisi gelap) dengan glow lembut. */
internal fun DrawScope.shadedSphere(p: Offset, rad: Float, color: Color, alpha: Float) {
    drawCircle(color.copy(alpha = 0.22f * alpha), rad * 2.1f, p)
    val hl = Offset(p.x - rad * 0.38f, p.y - rad * 0.42f)
    val dark = Color(color.red * 0.35f, color.green * 0.35f, color.blue * 0.35f, 1f)
    drawCircle(
        Brush.radialGradient(listOf(Color.White, color, dark), center = hl, radius = rad * 1.7f),
        rad, p, alpha = alpha
    )
}

/** Bola yang bergerak mengikuti cincin [rot] pada sudut [angle]. */
internal fun DrawScope.node3D(
    c: Offset, big: Float, rot: Rot3, angle: Float, rad: Float, color: Color, layer: DecoLayer
) {
    val x0 = big * cos(angle)
    val y0 = big * sin(angle)
    val x = rot.px(x0, y0, 0f)
    val y = rot.py(x0, y0, 0f)
    val z = rot.pz(x0, y0, 0f)
    if ((z >= 0f) != (layer == DecoLayer.FRONT)) return
    val d = depth01(z, big)
    val s = pf(z, big)
    shadedSphere(Offset(c.x + x * s, c.y + y * s), rad * (0.7f + 0.6f * d), color, 0.55f + 0.45f * d)
}

// ---------------------------------------------------------------------------
// 3D-1: GYRO CORE — tiga cincin giroskop berputar di semua sumbu + bola penanda
// ---------------------------------------------------------------------------
internal fun DrawScope.drawGyroCore(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val big = r * 1.04f
    val cyan = Color(0xFF00E5FF)
    val magenta = Color(0xFFFF2D95)
    val amber = Color(0xFFFFD740)
    val r1 = Rot3(t * 0.9f, t * 0.55f, 0f)
    val r2 = Rot3(t * 0.6f + 2.1f, -t * 0.8f, 1.0f)
    val r3 = Rot3(-t * 0.7f + 4f, t * 0.45f + 1f, 2.0f)
    ring3D(c, big, 64, r1, cyan, w * 0.6f, layer)
    ring3D(c, big, 64, r2, magenta, w * 0.6f, layer)
    ring3D(c, big, 64, r3, amber, w * 0.6f, layer)
    node3D(c, big, r1, t * 2.4f, w * 0.7f, cyan, layer)
    node3D(c, big, r2, -t * 2.0f + 1f, w * 0.7f, magenta, layer)
    node3D(c, big, r3, t * 1.7f + 2f, w * 0.7f, amber, layer)
}

// ---------------------------------------------------------------------------
// 3D-2: SATURN HALO — cincin Saturnus miring (melintas depan & belakang avatar) + bulan
// ---------------------------------------------------------------------------
internal fun DrawScope.drawSaturnHalo(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val big = r * 1.18f
    val rot = Rot3(1.2f + 0.14f * sin(t * 0.7f), 0f, -0.38f + 0.1f * cos(t * 0.5f))
    ring3D(c, big, 72, rot, Color(0xFFFFF0C2), w * 0.34f, layer, minA = 0.3f)
    ring3D(c, big * 0.94f, 72, rot, Color(0xFFFFD27F), w * 0.5f, layer, minA = 0.3f)
    ring3D(c, big * 0.87f, 72, rot, Color(0xFFFFA94D), w * 0.36f, layer, minA = 0.3f)

    val moonRot = Rot3(0.55f, 0f, 1.25f)
    val moonR = r * 1.02f
    ring3D(c, moonR, 56, moonRot, Color(0xFF9AD1FF), w * 0.18f, layer, minA = 0.1f, glow = false)
    node3D(c, moonR, moonRot, t * 1.6f, w * 0.85f, Color(0xFF8CCBFF), layer)
}

// ---------------------------------------------------------------------------
// 3D-3: QUANTUM ATOM — tiga orbit elektron yang berpresesi + elektron bercahaya
// ---------------------------------------------------------------------------
internal fun DrawScope.drawQuantumAtom(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val big = r * 1.06f
    val electrons = listOf(Color(0xFF4FC3F7), Color(0xFFFF80AB), Color(0xFFB9F6CA))
    for (k in 0 until 3) {
        val rot = Rot3(1.12f, t * 0.55f, k * 1.0472f + t * 0.25f)
        ring3D(c, big, 60, rot, Color(0xFFBFE9FF), w * 0.3f, layer, minA = 0.18f, glow = false)
        node3D(c, big, rot, t * (2.3f + 0.5f * k) + k * 2f, w * 0.75f, electrons[k], layer)
    }
}

// ---------------------------------------------------------------------------
// 3D-4: HYPER CUBE — kubus kawat neon yang berputar mengurung avatar
// ---------------------------------------------------------------------------
internal fun DrawScope.drawHyperCube(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val a = r * 0.70f
    val big = a * 1.732f
    val rot = Rot3(t * 0.7f, t * 0.9f, t * 0.3f)
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
    val neon = Color(0xFF00F0FF)
    val edgeBits = intArrayOf(1, 2, 4)
    for (i in 0 until 8) {
        for (bit in edgeBits) {
            val j = i xor bit
            if (j > i) {
                seg3D(c, big, vx[i], vy[i], vz[i], vx[j], vy[j], vz[j], neon, w * 0.4f, layer, 0.2f, true)
            }
        }
    }
    for (i in 0 until 8) {
        if ((vz[i] >= 0f) != (layer == DecoLayer.FRONT)) continue
        val d = depth01(vz[i], big)
        val s = pf(vz[i], big)
        val p = Offset(c.x + vx[i] * s, c.y + vy[i] * s)
        drawCircle(neon.copy(alpha = 0.25f * (0.4f + 0.6f * d)), w * 1.1f, p)
        drawCircle(Color.White.copy(alpha = 0.5f + 0.5f * d), w * 0.3f * (0.7f + 0.6f * d), p)
    }
}

// ---------------------------------------------------------------------------
// 3D-5: HELIX RING — heliks ganda DNA yang melingkari avatar, saling silang depan-belakang
// ---------------------------------------------------------------------------
internal fun DrawScope.drawHelixRing(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val n = 96
    val turns = 9
    val amp = w * 1.05f
    val big = r + amp
    val rot = Rot3(0.5f, 0.5f * sin(t * 0.4f), 0f)
    val colA = Color(0xFF00FFC6)
    val colB = Color(0xFFFF4DA6)
    val px = FloatArray(2)
    val py = FloatArray(2)
    val pz = FloatArray(2)
    val cx = FloatArray(2)
    val cy = FloatArray(2)
    val cz = FloatArray(2)
    for (i in 0..n) {
        val th = DECO_TAU * i / n
        val ph = turns * th + t * 2.4f
        for (k in 0 until 2) {
            val phase = ph + k * 3.1415927f
            val rho = r + amp * cos(phase)
            val zz = amp * sin(phase)
            val x0 = rho * cos(th)
            val y0 = rho * sin(th)
            cx[k] = rot.px(x0, y0, zz)
            cy[k] = rot.py(x0, y0, zz)
            cz[k] = rot.pz(x0, y0, zz)
        }
        if (i > 0) {
            seg3D(c, big, px[0], py[0], pz[0], cx[0], cy[0], cz[0], colA, w * 0.55f, layer, 0.25f, true)
            seg3D(c, big, px[1], py[1], pz[1], cx[1], cy[1], cz[1], colB, w * 0.55f, layer, 0.25f, true)
        }
        if (i % 4 == 0) {
            seg3D(c, big, cx[0], cy[0], cz[0], cx[1], cy[1], cz[1], Color.White, w * 0.18f, layer, 0.12f, false)
        }
        for (k in 0 until 2) {
            px[k] = cx[k]
            py[k] = cy[k]
            pz[k] = cz[k]
        }
    }
}

// ---------------------------------------------------------------------------
// 3D-6: PRISM SHARDS — 7 kristal bipiramida (flat-shaded) mengorbit sambil berputar
// ---------------------------------------------------------------------------
internal fun DrawScope.drawPrismShards(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val big = r * 1.06f
    val orbit = Rot3(0.95f, 0f, 0.5f)
    val n = 7
    val hues = listOf(
        Color(0xFFFF5C8A), Color(0xFFFFB347), Color(0xFFFFF176), Color(0xFF69F0AE),
        Color(0xFF40C4FF), Color(0xFF7C4DFF), Color(0xFFE040FB)
    )
    val lightX = -0.406f
    val lightY = -0.507f
    val lightZ = 0.760f

    for (j in 0 until n) {
        val ang = t * 0.75f + j * DECO_TAU / n
        val x0 = big * cos(ang)
        val y0 = big * sin(ang)
        val sx = orbit.px(x0, y0, 0f)
        val sy = orbit.py(x0, y0, 0f)
        val sz = orbit.pz(x0, y0, 0f)
        if ((sz >= 0f) != (layer == DecoLayer.FRONT)) continue

        val s = pf(sz, big)
        val center = Offset(c.x + sx * s, c.y + sy * s)
        val d = depth01(sz, big)
        val half = w * (1.05f + 0.35f * d)
        val col = hues[j % hues.size]

        drawCircle(col.copy(alpha = 0.2f), half * 2.0f, center)

        val own = Rot3(0.35f + 0.2f * sin(t + j), t * 2.2f + j * 1.3f, 0.2f)
        // verteks lokal: 0 = puncak, 1 = dasar, 2..5 = ekuator
        val lx = floatArrayOf(0f, 0f, half, 0f, -half, 0f)
        val ly = floatArrayOf(-half * 1.7f, half * 1.7f, 0f, 0f, 0f, 0f)
        val lz = floatArrayOf(0f, 0f, 0f, half, 0f, -half)
        val vx = FloatArray(6)
        val vy = FloatArray(6)
        val vz = FloatArray(6)
        for (v in 0 until 6) {
            vx[v] = own.px(lx[v], ly[v], lz[v])
            vy[v] = own.py(lx[v], ly[v], lz[v])
            vz[v] = own.pz(lx[v], ly[v], lz[v])
        }
        val fa = IntArray(8)
        val fb = IntArray(8)
        val fc = IntArray(8)
        for (i in 0 until 4) {
            val e0 = 2 + i
            val e1 = 2 + (i + 1) % 4
            fa[i * 2] = 0
            fb[i * 2] = e0
            fc[i * 2] = e1
            fa[i * 2 + 1] = 1
            fb[i * 2 + 1] = e1
            fc[i * 2 + 1] = e0
        }
        // painter's algorithm: gambar sisi terjauh dulu
        val order = (0 until 8).sortedBy { vz[fa[it]] + vz[fb[it]] + vz[fc[it]] }
        for (f in order) {
            val a = fa[f]
            val b = fb[f]
            val e = fc[f]
            val ux = vx[b] - vx[a]
            val uy = vy[b] - vy[a]
            val uz = vz[b] - vz[a]
            val wx = vx[e] - vx[a]
            val wy = vy[e] - vy[a]
            val wz = vz[e] - vz[a]
            var nx = uy * wz - uz * wy
            var ny = uz * wx - ux * wz
            var nz = ux * wy - uy * wx
            val gx = vx[a] + vx[b] + vx[e]
            val gy = vy[a] + vy[b] + vy[e]
            val gz = vz[a] + vz[b] + vz[e]
            if (nx * gx + ny * gy + nz * gz < 0f) {
                nx = -nx
                ny = -ny
                nz = -nz
            }
            val len = sqrt(nx * nx + ny * ny + nz * nz).let { if (it < 1e-6f) 1f else it }
            val br = 0.30f + 0.70f * max(0f, (nx * lightX + ny * lightY + nz * lightZ) / len)
            val spec = if (br > 0.88f) (br - 0.88f) * 2f else 0f
            val face = Color(
                (col.red * br + spec).coerceIn(0f, 1f),
                (col.green * br + spec).coerceIn(0f, 1f),
                (col.blue * br + spec).coerceIn(0f, 1f),
                0.95f
            )
            val path = Path().apply {
                moveTo(center.x + vx[a], center.y + vy[a])
                lineTo(center.x + vx[b], center.y + vy[b])
                lineTo(center.x + vx[e], center.y + vy[e])
                close()
            }
            drawPath(path, face)
            drawPath(path, Color.White.copy(alpha = 0.4f), style = Stroke(w * 0.1f, join = StrokeJoin.Round))
        }
    }
}

// ---------------------------------------------------------------------------
// 3D-7: GLASS GLOBE — avatar di dalam bola kaca: rim iridescent, kilau & pantulan
// ---------------------------------------------------------------------------
internal fun DrawScope.drawGlassGlobe(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    if (layer == DecoLayer.BACK) {
        val rim = r + w * 0.15f
        val hues = listOf(
            Color(0xFFFF9AD5), Color(0xFFB39DFF), Color(0xFF7DD3FF),
            Color(0xFF9CFFD4), Color(0xFFFFF39A), Color(0xFFFF9AD5)
        )
        drawCircle(Color(0xFF9BD9FF).copy(alpha = 0.16f), rim + w * 0.7f, c, style = Stroke(w * 1.8f))
        rotate(t * 28f, c) {
            drawCircle(Brush.sweepGradient(hues, c), rim, c, alpha = 0.85f, style = Stroke(w * 1.1f))
        }
        return
    }

    // FRONT: kaca di atas foto/video
    val gr = r * 0.93f
    val clip = Path().apply { addOval(Rect(c.x - gr, c.y - gr, c.x + gr, c.y + gr)) }
    clipPath(clip) {
        drawCircle(
            Brush.radialGradient(
                0.62f to Color.Transparent,
                0.92f to Color.White.copy(alpha = 0.10f),
                1f to Color.White.copy(alpha = 0.34f),
                center = c, radius = gr
            ),
            gr, c
        )
        val u = decoFract(t / 4f)
        if (u < 0.55f) {
            val f = u / 0.55f
            val x = c.x + (f * 2f - 1f) * gr * 1.5f
            val bw = gr * 0.28f
            rotate(24f, c) {
                drawRect(
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.5f to Color.White.copy(alpha = 0.30f),
                        1f to Color.Transparent,
                        startX = x - bw, endX = x + bw
                    ),
                    Offset(x - bw, c.y - gr * 1.6f), Size(bw * 2f, gr * 3.2f)
                )
            }
        }
    }
    val arcTl = Offset(c.x - gr * 0.88f, c.y - gr * 0.88f)
    val arcSz = Size(gr * 1.76f, gr * 1.76f)
    drawArc(
        Color.White, 200f, 70f, false, arcTl, arcSz,
        alpha = 0.75f, style = Stroke(w * 0.55f, cap = StrokeCap.Round)
    )
    drawCircle(Color.White.copy(alpha = 0.9f), w * 0.3f, Offset(c.x - gr * 0.55f, c.y - gr * 0.72f))
    drawArc(
        Color(0xFF9BD9FF), 20f, 50f, false, arcTl, arcSz,
        alpha = 0.45f, style = Stroke(w * 0.35f, cap = StrokeCap.Round)
    )
}

// ---------------------------------------------------------------------------
// 3D-8: CHROME TORUS — cincin krom tebal bergaya tabung: pantulan bergerak + highlight
// ---------------------------------------------------------------------------
internal fun DrawScope.drawChromeTorus(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    if (layer != DecoLayer.BACK) return
    val metal = listOf(
        Color(0xFF1F2329), Color(0xFFF7FAFF), Color(0xFF8793A6), Color(0xFF2B3038),
        Color(0xFFE9F0FA), Color(0xFF6C778B), Color(0xFF1F2329)
    )
    drawCircle(Color.Black.copy(alpha = 0.35f), r + w * 0.55f, c, style = Stroke(w * 0.9f))
    rotate(t * 40f, c) {
        drawCircle(Brush.sweepGradient(metal, c), r, c, style = Stroke(w * 1.5f))
    }
    drawCircle(Color.Black.copy(alpha = 0.25f), r - w * 0.55f, c, style = Stroke(w * 0.2f))

    val tl = Offset(c.x - (r - w * 0.2f), c.y - (r - w * 0.2f))
    val sz = Size((r - w * 0.2f) * 2f, (r - w * 0.2f) * 2f)
    drawArc(
        Color.White, 190f, 100f, false, tl, sz,
        alpha = 0.8f, style = Stroke(w * 0.3f, cap = StrokeCap.Round)
    )
    val tl2 = Offset(c.x - (r + w * 0.35f), c.y - (r + w * 0.35f))
    val sz2 = Size((r + w * 0.35f) * 2f, (r + w * 0.35f) * 2f)
    drawArc(
        Color(0xFF7DD3FF), 20f, 60f, false, tl2, sz2,
        alpha = 0.35f, style = Stroke(w * 0.25f, cap = StrokeCap.Round)
    )
    val head = decoPt(c, r - w * 0.15f, decoRad(t * 60f))
    drawCircle(Color.White.copy(alpha = 0.35f), w * 0.55f, head)
    drawCircle(Color.White.copy(alpha = 0.95f), w * 0.22f, head)
}

// ---------------------------------------------------------------------------
// 3D-9: MINI SOLAR — empat planet ber-shading di orbit miring yang berbeda
// ---------------------------------------------------------------------------
internal fun DrawScope.drawMiniSolar(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val radii = floatArrayOf(0.96f, 1.00f, 1.05f, 0.90f)
    val tilt = floatArrayOf(1.25f, 0.95f, 1.42f, 0.6f)
    val spin = floatArrayOf(0.3f, 1.4f, -0.9f, 2.6f)
    val sizes = floatArrayOf(0.55f, 0.85f, 1.1f, 0.7f)
    val speeds = floatArrayOf(2.6f, 1.7f, 1.0f, 2.0f)
    val cols = listOf(Color(0xFFFF8A65), Color(0xFF4FC3F7), Color(0xFFFFCC80), Color(0xFFB388FF))
    for (k in 0 until 4) {
        val rot = Rot3(tilt[k], 0.35f * sin(t * 0.3f + k), spin[k])
        val rad = r * radii[k]
        ring3D(c, rad, 56, rot, Color.White, w * 0.14f, layer, minA = 0.08f, glow = false)
        node3D(c, rad, rot, t * speeds[k] + k * 1.7f, w * sizes[k], cols[k], layer)
    }
}

// ---------------------------------------------------------------------------
// 3D-10: HEX HOLOGRAM — dua heksagon holografik berputar berlawanan + cincin & node
// ---------------------------------------------------------------------------
internal fun DrawScope.drawHexHolo(c: Offset, r: Float, w: Float, t: Float, layer: DecoLayer) {
    val teal = Color(0xFF00FFD5)
    val violet = Color(0xFFB388FF)
    val white = Color(0xFFE6FFFB)
    val big = r * 1.05f
    val rotA = Rot3(0.25f * sin(t * 0.7f), t * 1.0f, 0.2f)
    val rotB = Rot3(0.25f * sin(t * 0.7f + 1f), -t * 0.8f, 0.52f)
    val rotC = Rot3(t * 0.8f, 0.3f, 0.52f)
    ring3D(c, big, 6, rotA, teal, w * 0.5f, layer)
    ring3D(c, big * 0.86f, 6, rotB, violet, w * 0.4f, layer)
    ring3D(c, big * 0.98f, 72, rotC, white, w * 0.25f, layer, minA = 0.15f, glow = false)
    for (i in 0 until 6) {
        node3D(c, big, rotA, i * 1.0472f, w * 0.5f, white, layer)
    }
}
