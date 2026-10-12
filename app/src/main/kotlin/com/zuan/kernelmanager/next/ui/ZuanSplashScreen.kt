/*
 * Copyright (c) 2025-2026 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.ui

import android.app.ActivityManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zuan.kernelmanager.next.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * ════════════════════════════════════════════════════════════════════════════
 *  ZUAN 3D SPLASH — "SINGULARITY" EDITION  (Sonnet 5.5 × Gemini 3.8, satu otak)
 *
 *  Fondasi = Physics Engine (Sonnet 5.5): rigid body quaternion, gimbal pegas-peredam, orbit Kepler,
 *  shockwave Sedov–Taylor, lensa gravitasi, kartu 3D berpencahayaan, huruf jatuh berfisika.
 *
 *  Warisan Gemini 3.8
 *   • Beaming Doppler pada bintang warp: makin dekat makin terang + bergeser ke putih/biru
 *     (di sini dibuat halus & aman di tema terang — tidak lagi putih-di-atas-putih)
 *   • Phase-lock huruf judul: begitu energi pantulan habis, huruf dikunci → tak ada getar sisa
 *     (di sini baru mengunci setelah rotasi benar-benar tenang, jadi tidak ada "pop" rotasi)
 *   • Clamp aman pada interferensi film tipis
 *
 *  Warisan Sonnet 5.5 (versi sebelumnya)
 *   • Tesseract 4D penuh (rotasi XW + ZW), dipol magnet, simpul trefoil, sumur gravitasi, god rays
 *   • Proyeksi globe & tesseract sekali per frame, kualitas adaptif, hormati "Remove animations"
 *
 *  Poles baru (versi ini)
 *   • Haptic tick sungguhan per huruf yang mendarat (dibatasi laju, huruf spasi dilewati)
 *   • Tier hemat otomatis juga saat Battery Saver aktif (selain core sedikit / low-RAM / frame lambat)
 *   • Boot log hidup: "[ .. ]" berubah jadi "[ OK ]" satu per satu
 *   • Double-tap di mana saja = lewati splash (matikan lewat ALLOW_DOUBLE_TAP_SKIP)
 *   • Label versi dibaca dari versionName aplikasi (fallback ke teks lama)
 * ════════════════════════════════════════════════════════════════════════════
 */

// ═════════════════════════ KONSTANTA ═════════════════════════

private const val FOV = 4f
private const val SC = 6                       // stride scratch node: sx, sy, z, disp, lit, nz
private const val NOMINAL_SPIN = 0.3927f       // 2π / 16 detik
private const val AXIS_K = 38f                 // kekakuan torsi pemulih sumbu globe
private const val AXIS_C = 5f                  // redaman gerak nutasi
private const val SPIN_C = 1.1f                // relaksasi spin ke kecepatan nominal
private const val PARTICLE_DRAG = 2.2f
private const val PARTICLE_G = 2.6f
private const val FLOOR_Y = 1.7f
private const val LETTER_DROP = 46f            // dp
private const val LETTER_G = 1100f             // dp/s²
private const val LETTER_RK = 60f
private const val LETTER_RC = 7f
private const val SETTLE_ROT = 0.35f           // derajat: huruf dianggap tenang bila di bawah ini
private const val SETTLE_ROTV = 3f             // derajat/detik
private const val HAPTIC_GAP_NS = 110_000_000L // jeda minimum antar haptic huruf (110 ms)
private const val ALLOW_DOUBLE_TAP_SKIP = true  // double-tap di layar = lewati splash
private const val LIGHT_X = -0.42f
private const val LIGHT_Y = -0.52f
private const val LIGHT_Z = 0.74f
private const val TESS_SCALE = 0.40f          // ukuran tesseract relatif radius globe
private const val TESS_FOV4 = 3f              // jarak kamera 4D → 3D
private const val KNOT_SCALE = 0.21f          // ukuran simpul trefoil relatif radius globe

private val D2R = (PI / 180.0).toFloat()
private val TWO_PI = (PI * 2.0).toFloat()
private val RING_TILTS = floatArrayOf(64f, -46f, 14f)
private val RING_A = floatArrayOf(1.36f, 1.46f, 1.56f)       // sumbu semi-mayor (satuan radius globe)
private val RING_E = floatArrayOf(0.06f, 0.12f, 0.18f)       // eksentrisitas
private val RING_N = floatArrayOf(1.9f, -1.45f, 1.1f)        // gerak rata-rata (rad/s)
private val RING_PREC = floatArrayOf(0.35f, -0.28f, 0.20f)   // presesi apsidal (rad/s)
private val RING_K = floatArrayOf(30f, 18f, 11f)             // kekakuan gimbal

// ═════════════════════════ DATA 3D ═════════════════════════

/** Globe jaringan: titik Fibonacci di permukaan bola + sisi penghubung antar titik berdekatan. */
private class GlobeData(val pts: FloatArray, val edges: IntArray) {
    val scratch = FloatArray(pts.size / 3 * SC) // buffer proyeksi → tanpa alokasi per frame
    var epoch = -1       // epoch SplashWorld terakhir yang sudah diproyeksikan (cache lintas canvas)
    var width = -1f      // lebar canvas saat proyeksi terakhir
}

private fun buildGlobe(n: Int, linkDist: Float): GlobeData {
    val pts = FloatArray(n * 3)
    val golden = (PI * (3.0 - sqrt(5.0))).toFloat()
    for (i in 0 until n) {
        val y = 1f - (i / (n - 1f)) * 2f
        val r = sqrt((1f - y * y).coerceAtLeast(0f))
        val t = golden * i
        pts[i * 3] = cos(t) * r
        pts[i * 3 + 1] = y
        pts[i * 3 + 2] = sin(t) * r
    }
    val edges = ArrayList<Int>()
    val d2 = linkDist * linkDist
    for (i in 0 until n) {
        for (j in i + 1 until n) {
            val dx = pts[i * 3] - pts[j * 3]
            val dy = pts[i * 3 + 1] - pts[j * 3 + 1]
            val dz = pts[i * 3 + 2] - pts[j * 3 + 2]
            if (dx * dx + dy * dy + dz * dz < d2) {
                edges.add(i)
                edges.add(j)
            }
        }
    }
    return GlobeData(pts, edges.toIntArray())
}

/** Bintang warp: packed (x, y, z0). */
private fun buildStars(n: Int): FloatArray {
    val rnd = Random(42)
    val out = FloatArray(n * 3)
    for (i in 0 until n) {
        out[i * 3] = rnd.nextFloat() * 2f - 1f
        out[i * 3 + 1] = rnd.nextFloat() * 2f - 1f
        out[i * 3 + 2] = rnd.nextFloat()
    }
    return out
}

/** Arah ledakan: packed (dx, dy, dz, speed) — arah acak seragam di bola. */
private fun buildBurst(n: Int): FloatArray {
    val rnd = Random(7)
    val out = FloatArray(n * 4)
    for (i in 0 until n) {
        val z = rnd.nextFloat() * 2f - 1f
        val t = rnd.nextFloat() * TWO_PI
        val r = sqrt((1f - z * z).coerceAtLeast(0f))
        out[i * 4] = cos(t) * r
        out[i * 4 + 1] = sin(t) * r
        out[i * 4 + 2] = z
        out[i * 4 + 3] = 0.5f + 0.7f * rnd.nextFloat()
    }
    return out
}

/** Hiperkubus 4D: 16 titik sudut (±1,±1,±1,±1) dan 32 rusuk (pasangan yang beda tepat 1 bit). */
private class SplashTesseract {
    val pts = FloatArray(16 * 4)
    val edges = IntArray(32 * 2)
    val proj = FloatArray(16 * 3) // hasil rotasi 4D → 3D (kerangka layar); dipakai bersama pass belakang & depan
    var epoch = -1

    init {
        for (i in 0 until 16) {
            pts[i * 4] = if ((i and 1) == 0) -1f else 1f
            pts[i * 4 + 1] = if ((i and 2) == 0) -1f else 1f
            pts[i * 4 + 2] = if ((i and 4) == 0) -1f else 1f
            pts[i * 4 + 3] = if ((i and 8) == 0) -1f else 1f
        }
        var idx = 0
        for (i in 0 until 16) {
            for (j in i + 1 until 16) {
                if ((i xor j).countOneBits() == 1) {
                    edges[idx++] = i
                    edges[idx++] = j
                }
            }
        }
    }
}

/** Jumlah elemen yang digambar per tier performa. */
private class SplashQuality(
    val ringSteps: Int,
    val scanSteps: Int,
    val shockSteps: Int,
    val floorSegs: Int,
    val dipLines: Int,
    val dipSteps: Int,
    val knotSteps: Int,
    val rays: Int
)

private val QUALITY_HIGH = SplashQuality(
    ringSteps = 84, scanSteps = 72, shockSteps = 110, floorSegs = 22,
    dipLines = 8, dipSteps = 28, knotSteps = 120, rays = 24
)
private val QUALITY_LOW = SplashQuality(
    ringSteps = 56, scanSteps = 48, shockSteps = 72, floorSegs = 12,
    dipLines = 5, dipSteps = 20, knotSteps = 80, rays = 14
)

// ═════════════════════════ MATEMATIKA: MATRIKS & QUATERNION ═════════════════════════

/** Quaternion (w, x, y, z) → matriks rotasi 3×3 (row-major). */
private fun quatToMat(q: FloatArray, m: FloatArray) {
    val w = q[0]; val x = q[1]; val y = q[2]; val z = q[3]
    val xx = x * x; val yy = y * y; val zz = z * z
    val xy = x * y; val xz = x * z; val yz = y * z
    val wx = w * x; val wy = w * y; val wz = w * z
    m[0] = 1f - 2f * (yy + zz); m[1] = 2f * (xy - wz); m[2] = 2f * (xz + wy)
    m[3] = 2f * (xy + wz); m[4] = 1f - 2f * (xx + zz); m[5] = 2f * (yz - wx)
    m[6] = 2f * (xz - wy); m[7] = 2f * (yz + wx); m[8] = 1f - 2f * (xx + yy)
}

/** out = a · b  (out tidak boleh sama dengan a atau b). */
private fun matMul(a: FloatArray, b: FloatArray, out: FloatArray) {
    for (r in 0 until 3) {
        for (c in 0 until 3) {
            out[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c]
        }
    }
}

private fun matApply(m: FloatArray, x: Float, y: Float, z: Float, out: FloatArray) {
    out[0] = m[0] * x + m[1] * y + m[2] * z
    out[1] = m[3] * x + m[4] * y + m[5] * z
    out[2] = m[6] * x + m[7] * y + m[8] * z
}

/** Rx(ax) · Ry(ay), sudut dalam radian — kamera: pitch lalu yaw. */
private fun viewMat(ax: Float, ay: Float, m: FloatArray) {
    val sa = sin(ax); val ca = cos(ax); val sb = sin(ay); val cb = cos(ay)
    m[0] = cb; m[1] = 0f; m[2] = sb
    m[3] = sa * sb; m[4] = ca; m[5] = -sa * cb
    m[6] = -ca * sb; m[7] = sa; m[8] = ca * cb
}

private fun rotZMat(a: Float, m: FloatArray) {
    val s = sin(a); val c = cos(a)
    m[0] = c; m[1] = -s; m[2] = 0f
    m[3] = s; m[4] = c; m[5] = 0f
    m[6] = 0f; m[7] = 0f; m[8] = 1f
}

private fun rotYMat(a: Float, m: FloatArray) {
    val s = sin(a); val c = cos(a)
    m[0] = c; m[1] = 0f; m[2] = s
    m[3] = 0f; m[4] = 1f; m[5] = 0f
    m[6] = -s; m[7] = 0f; m[8] = c
}

/** Integrasi orientasi: q̇ = ½ (0, ω) ⊗ q, ω dalam kerangka dunia. */
private fun integrateQuat(q: FloatArray, w: FloatArray, dt: Float) {
    val ox = w[0]; val oy = w[1]; val oz = w[2]
    val qw = q[0]; val qx = q[1]; val qy = q[2]; val qz = q[3]
    val hd = 0.5f * dt
    q[0] = qw + hd * (-ox * qx - oy * qy - oz * qz)
    q[1] = qx + hd * (ox * qw + oy * qz - oz * qy)
    q[2] = qy + hd * (oy * qw + oz * qx - ox * qz)
    q[3] = qz + hd * (oz * qw + ox * qy - oy * qx)
    val n = sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3])
    if (n < 1e-6f) {
        q[0] = 1f; q[1] = 0f; q[2] = 0f; q[3] = 0f
    } else {
        val inv = 1f / n
        q[0] *= inv; q[1] *= inv; q[2] *= inv; q[3] *= inv
    }
}

/** Persamaan Kepler  M = E − e·sin E  (Newton–Raphson, 3 iterasi cukup untuk e < 0.3). */
private fun solveKepler(meanAnomaly: Float, e: Float): Float {
    var mm = meanAnomaly % TWO_PI
    if (mm < 0f) mm += TWO_PI
    var ee = mm + e * sin(mm)
    for (i in 0 until 3) {
        ee -= (ee - e * sin(ee) - mm) / (1f - e * cos(ee))
    }
    return ee
}

/** Interferensi film tipis: warna dari beda lintasan optik 2·n·d·cosθ terhadap panjang gelombang RGB. */
private fun thinFilm(cosT: Float, thicknessNm: Float, alpha: Float): Color {
    val p = 2f * 1.33f * thicknessNm * cosT
    val r = (0.5f + 0.5f * cos(TWO_PI * p / 650f)).coerceIn(0f, 1f)
    val g = (0.5f + 0.5f * cos(TWO_PI * p / 540f)).coerceIn(0f, 1f)
    val b = (0.5f + 0.5f * cos(TWO_PI * p / 460f)).coerceIn(0f, 1f)
    return Color(r, g, b, alpha)
}

private fun mix(a: Color, b: Color, t: Float): Color =
    Color(
        a.red + (b.red - a.red) * t,
        a.green + (b.green - a.green) * t,
        a.blue + (b.blue - a.blue) * t,
        1f
    )

// ═════════════════════════ MESIN FISIKA ═════════════════════════

/** Osilator harmonik teredam (semi-implicit Euler): x'' = −k·x − c·x'. */
private class Osc(private val k: Float, private val c: Float) {
    var x = 0f
    var v = 0f
    fun step(dt: Float) {
        v += (-k * x - c * v) * dt
        x += v * dt
    }

    fun kick(impulse: Float) {
        v += impulse
    }
}

private class SplashWorld(letterCount: Int, burstDirs: FloatArray) {
    /** State pemicu redraw: dibaca di fase draw / graphicsLayer → tanpa recomposition. */
    val tick = mutableIntStateOf(0)
    fun subscribe(): Int = tick.intValue

    var time = 0f
    var warpPhase = 0f
    var impactT = -1f // detik sejak impact; < 0 = belum terjadi
    var epoch = 0     // naik setiap kali matriks dihitung ulang → cache proyeksi lintas canvas
    var phase4 = 0f   // sudut rotasi hiperkubus 4D (rad)
    var hyper = 0f    // kecepatan rotasi 4D tambahan (rad/s), ditendang saat impact lalu meredam

    // — Globe sebagai rigid body (kerangka dunia) —
    val bodyQ = floatArrayOf(1f, 0f, 0f, 0f)
    val bodyW = floatArrayOf(0f, NOMINAL_SPIN, 0f)

    // — Cincin = gimbal yang mengejar orientasi body lewat pegas-peredam —
    val ringQ = Array(3) { floatArrayOf(1f, 0f, 0f, 0f) }
    val ringW = Array(3) { floatArrayOf(0f, NOMINAL_SPIN, 0f) }

    // — Osilator —
    val tiltX = Osc(120f, 8.8f)   // tilt dari jari (derajat), kembali dengan pegas
    val tiltY = Osc(120f, 8.8f)
    var dragging = false
    val logoDy = Osc(260f, 9f)    // thud logo (dp)
    val wobX = Osc(180f, 7f)      // wobble kartu (derajat)
    val wobY = Osc(170f, 6.5f)
    val wobZ = Osc(140f, 6f)
    val camX = Osc(520f, 16f)     // guncangan kamera (dp)
    val camY = Osc(480f, 15f)
    val camRot = Osc(300f, 11f)   // derajat
    val camZoom = Osc(360f, 12f)  // fraksi skala

    // — Huruf judul: jatuh, memantul, berputar —
    val letterY = FloatArray(letterCount) { -LETTER_DROP }
    val letterVy = FloatArray(letterCount)
    val letterRot = FloatArray(letterCount) { -90f }
    val letterRotV = FloatArray(letterCount)
    val letterAt = FloatArray(letterCount) { Float.MAX_VALUE }
    val letterLocked = BooleanArray(letterCount)   // phase-lock: sudah benar-benar tenang
    private val letterTouched = BooleanArray(letterCount)
    var landCount = 0      // naik setiap kali sebuah huruf pertama kali menyentuh dasar
    var lastLandIdx = -1   // indeks huruf yang terakhir mendarat (untuk haptic)

    // — Partikel balistik: (x, y, z, vx, vy, vz) —
    private val dirs = burstDirs
    val pCount = burstDirs.size / 4
    val pp = FloatArray(pCount * 6)
    val pAge = FloatArray(pCount) { 1f }
    val pLife = FloatArray(pCount) { 1f }

    // — Matriks kerja —
    val mView = FloatArray(9)
    val mM = FloatArray(9)
    val mRing = Array(3) { FloatArray(9) }
    val tmpV = FloatArray(3)
    val tmpV2 = FloatArray(3)
    private val mBody = FloatArray(9)
    private val mA = FloatArray(9)
    private val mB = FloatArray(9)
    private val mC = FloatArray(9)
    private var preparedTick = -1
    private var pvx = Float.NaN
    private var pvy = Float.NaN

    fun hover(): Float = 0.62f * sin(2.62f * time) + 0.38f * sin(1.71f * time + 1.1f)

    /** Hitung semua matriks transformasi sekali per frame (di-cache lintas canvas). */
    fun prepare(viewXDeg: Float, viewYDeg: Float) {
        val t = tick.intValue
        if (t == preparedTick && viewXDeg == pvx && viewYDeg == pvy) return
        preparedTick = t
        pvx = viewXDeg
        pvy = viewYDeg
        viewMat((viewXDeg + 16f) * D2R, viewYDeg * D2R, mView)
        quatToMat(bodyQ, mBody)
        matMul(mView, mBody, mM)
        for (r in 0 until 3) {
            quatToMat(ringQ[r], mA)
            rotZMat(RING_TILTS[r] * D2R, mB)
            matMul(mA, mB, mC)
            rotYMat(RING_PREC[r] * time, mB)
            matMul(mC, mB, mA)
            matMul(mView, mA, mRing[r])
        }
        epoch++
    }

    fun advance(dt: Float, warp: Float) {
        if (dt <= 0f) return
        val n = ceil(dt / 0.0083f).toInt().coerceIn(1, 6)
        val h = dt / n
        repeat(n) { stepOnce(h) }
        warpPhase += dt * warp
        tick.intValue = tick.intValue + 1
    }

    private fun stepOnce(h: Float) {
        time += h
        if (impactT >= 0f) impactT += h
        stepBody(h)
        stepRings(h)
        if (!dragging) {
            tiltX.step(h)
            tiltY.step(h)
        }
        logoDy.step(h)
        wobX.step(h)
        wobY.step(h)
        wobZ.step(h)
        camX.step(h)
        camY.step(h)
        camRot.step(h)
        camZoom.step(h)
        stepParticles(h)
        stepLetters(h)
        phase4 += (0.38f + hyper) * h
        hyper *= (1f - 1.6f * h).coerceAtLeast(0f)
    }

    /**
     * Globe: ω̇ = −c·(ω − ω_nominal) pada sumbu spin, dan torsi pemulih τ = k·(u × ŷ)
     * pada sumbu atas body u → globe berayun (nutasi) lalu tegak kembali seperti gasing.
     */
    private fun stepBody(h: Float) {
        val q = bodyQ
        val w = bodyW
        val ux = 2f * (q[1] * q[2] - q[0] * q[3])
        val uz = 2f * (q[2] * q[3] + q[0] * q[1])
        w[0] += (-AXIS_K * uz - AXIS_C * w[0]) * h
        w[1] += (-SPIN_C * (w[1] - NOMINAL_SPIN)) * h
        w[2] += (AXIS_K * ux - AXIS_C * w[2]) * h
        integrateQuat(q, w, h)
    }

    /** Gimbal: error rotasi e = q_body ⊗ conj(q_ring) → torsi = 2k·e_vec − c·(ω_ring − ω_body). */
    private fun stepRings(h: Float) {
        val a0 = bodyQ[0]; val a1 = bodyQ[1]; val a2 = bodyQ[2]; val a3 = bodyQ[3]
        for (r in 0 until 3) {
            val qr = ringQ[r]
            val wr = ringW[r]
            val r0 = qr[0]; val r1 = qr[1]; val r2 = qr[2]; val r3 = qr[3]
            val ew = a0 * r0 + a1 * r1 + a2 * r2 + a3 * r3
            var ex = -a0 * r1 + a1 * r0 - a2 * r3 + a3 * r2
            var ey = -a0 * r2 + a1 * r3 + a2 * r0 - a3 * r1
            var ez = -a0 * r3 - a1 * r2 + a2 * r1 + a3 * r0
            if (ew < 0f) {
                ex = -ex; ey = -ey; ez = -ez
            }
            val k = RING_K[r]
            val c = 0.7f * sqrt(k)
            wr[0] += (2f * k * ex - c * (wr[0] - bodyW[0])) * h
            wr[1] += (2f * k * ey - c * (wr[1] - bodyW[1])) * h
            wr[2] += (2f * k * ez - c * (wr[2] - bodyW[2])) * h
            integrateQuat(qr, wr, h)
        }
    }

    /** Balistik: v ← v·(1 − k·dt), vy += g·dt, pantul di bidang lantai dengan restitusi 0.42. */
    private fun stepParticles(h: Float) {
        val damp = (1f - PARTICLE_DRAG * h).coerceAtLeast(0f)
        for (i in 0 until pCount) {
            if (pAge[i] >= pLife[i]) continue
            pAge[i] += h
            val o = i * 6
            pp[o + 3] *= damp
            pp[o + 4] = pp[o + 4] * damp + PARTICLE_G * h
            pp[o + 5] *= damp
            pp[o] += pp[o + 3] * h
            pp[o + 1] += pp[o + 4] * h
            pp[o + 2] += pp[o + 5] * h
            if (pp[o + 1] > FLOOR_Y && pp[o + 4] > 0f) {
                pp[o + 1] = FLOOR_Y - (pp[o + 1] - FLOOR_Y)
                pp[o + 4] = -pp[o + 4] * 0.42f
                pp[o + 3] *= 0.8f
                pp[o + 5] *= 0.8f
            }
        }
    }

    /**
     * Huruf: jatuh (g), memantul (restitusi 0.36), berputar lewat pegas-peredam.
     * Phase-lock: setelah diam di dasar DAN rotasi tenang (di bawah ambang), huruf dikunci pada pose akhir
     * sehingga tidak ada integrasi sisa; sebelum itu rotasi tetap dibiarkan mengendap alami (tanpa "pop").
     */
    private fun stepLetters(h: Float) {
        for (i in letterY.indices) {
            if (time < letterAt[i] || letterLocked[i]) continue
            var vy = letterVy[i] + LETTER_G * h
            var y = letterY[i] + vy * h
            if (y >= 0f) {
                y = 0f
                if (!letterTouched[i]) {
                    letterTouched[i] = true
                    landCount++
                    lastLandIdx = i
                }
                if (vy > 70f) {
                    letterRotV[i] -= vy * 0.16f
                    vy = -vy * 0.36f
                } else {
                    vy = 0f
                }
            }
            letterY[i] = y
            letterVy[i] = vy
            letterRotV[i] += (-LETTER_RK * letterRot[i] - LETTER_RC * letterRotV[i]) * h
            letterRot[i] += letterRotV[i] * h
            if (y == 0f && vy == 0f && abs(letterRot[i]) < SETTLE_ROT && abs(letterRotV[i]) < SETTLE_ROTV) {
                letterRot[i] = 0f
                letterRotV[i] = 0f
                letterY[i] = 0f
                letterLocked[i] = true
            }
        }
    }

    fun dropTitle() {
        for (i in letterAt.indices) letterAt[i] = time + i * 0.045f
    }

    fun drag(dx: Float, dy: Float) {
        tiltY.x = (tiltY.x + dx * 0.3f).coerceIn(-70f, 70f)
        tiltX.x = (tiltX.x - dy * 0.3f).coerceIn(-50f, 50f)
        tiltX.v = 0f
        tiltY.v = 0f
        // impuls momentum sudut → globe terus berputar (fling) setelah jari dilepas
        bodyW[1] = (bodyW[1] + dx * 0.014f).coerceIn(-12f, 12f)
        bodyW[0] = (bodyW[0] - dy * 0.014f).coerceIn(-12f, 12f)
    }

    /** IMPACT: semua osilator ditendang, globe digoyang, partikel disemburkan. */
    fun impact() {
        impactT = 0f
        hyper += 2.6f
        bodyW[0] += 1.5f
        bodyW[1] += 0.9f
        bodyW[2] -= 1.2f
        logoDy.kick(150f)
        wobX.kick(230f)
        wobY.kick(-170f)
        wobZ.kick(90f)
        camX.kick(190f)
        camY.kick(-150f)
        camRot.kick(7f)
        camZoom.kick(0.55f)
        for (i in 0 until pCount) {
            val dx = dirs[i * 4]
            val dy = dirs[i * 4 + 1]
            val dz = dirs[i * 4 + 2]
            val sp = dirs[i * 4 + 3]
            val o = i * 6
            pp[o] = dx * 0.22f
            pp[o + 1] = dy * 0.22f
            pp[o + 2] = dz * 0.22f
            val speed = sp * 4.3f
            pp[o + 3] = dx * speed
            pp[o + 4] = dy * speed - 0.9f
            pp[o + 5] = dz * speed
            pAge[i] = 0f
            pLife[i] = 0.75f + 1.1f * ((sp - 0.5f) / 0.7f).coerceIn(0f, 1f)
        }
    }
}

// ═════════════════════════ DRAWING ═════════════════════════

/**
 * Terowongan warp + lensa gravitasi titik-massa:
 * posisi gambar  r' = ½(r + √(r² + 4·rE²)),  magnifikasi μ = (u²+2)/(u·√(u²+4)),  u = r/rE.
 * Saat warp melambat, bintang tertekuk membentuk cincin Einstein di sekeliling globe.
 */
private fun DrawScope.drawWarp(
    stars: FloatArray,
    phase: Float,
    warp: Float,
    lensStrength: Float,
    lensRadius: Float,
    color: Color,
    core: Color,
    glow: BlendMode,
    chroma: Boolean
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val k = size.maxDimension * 0.07f
    val u = 1.dp.toPx()
    val boost = 0.30f + 0.70f * (warp / 1.8f).coerceIn(0f, 1f)
    val trail = 0.010f + warp * 0.045f
    val rE = lensRadius * lensStrength
    val rE4 = 4f * rE * rE
    val spread = if (chroma && warp > 0.8f) 0.010f * (warp - 0.5f) else 0f

    for (i in 0 until stars.size / 3) {
        var z = (stars[i * 3 + 2] - phase) % 1f
        if (z < 0f) z += 1f
        if (z < 0.03f) continue
        val x = stars[i * 3]
        val y = stars[i * 3 + 1]
        var hx = x * k / z
        var hy = y * k / z
        val zt = z + trail
        var tx = x * k / zt
        var ty = y * k / zt
        var mag = 1f
        if (rE > 0.5f) {
            val h2 = (hx * hx + hy * hy).coerceAtLeast(1f)
            val fh = 0.5f * (1f + sqrt(1f + rE4 / h2))
            val uu = sqrt(h2) / rE
            mag = ((uu * uu + 2f) / (uu * sqrt(uu * uu + 4f))).coerceIn(1f, 2.4f)
            hx *= fh
            hy *= fh
            val t2 = (tx * tx + ty * ty).coerceAtLeast(1f)
            val ft = 0.5f * (1f + sqrt(1f + rE4 / t2))
            tx *= ft
            ty *= ft
        }
        val px = cx + hx
        val py = cy + hy
        if (px < -60f || px > size.width + 60f || py < -60f || py > size.height + 60f) continue
        val near = 1f - z
        // Beaming Doppler: bintang yang melesat mendekat makin terang dan bergeser ke warna inti (putih / aksen)
        val blue = (near * warp * 0.9f).coerceIn(0f, 1f)
        val tint = mix(color, core, blue)
        val alpha = ((0.12f + 0.88f * near * near) * boost * mag * (1f + 0.35f * blue)).coerceIn(0f, 1f)
        val sw = (0.6f + 2.2f * near) * u

        if (spread > 0f) {
            val sr = 1f + spread
            val sb = 1f - spread
            drawLine(
                color = Color(1f, 0.28f, 0.38f, alpha * 0.45f),
                start = Offset(cx + tx * sr, cy + ty * sr),
                end = Offset(cx + hx * sr, cy + hy * sr),
                strokeWidth = sw * 0.8f,
                cap = StrokeCap.Round,
                blendMode = glow
            )
            drawLine(
                color = Color(0.25f, 0.62f, 1f, alpha * 0.45f),
                start = Offset(cx + tx * sb, cy + ty * sb),
                end = Offset(cx + hx * sb, cy + hy * sb),
                strokeWidth = sw * 0.8f,
                cap = StrokeCap.Round,
                blendMode = glow
            )
        }
        drawLine(
            color = tint.copy(alpha = alpha),
            start = Offset(cx + tx, cy + ty),
            end = Offset(px, py),
            strokeWidth = sw,
            cap = StrokeCap.Round,
            blendMode = glow
        )
    }
}

/** Gelombang tanah dispersif dari titik impact: k = 2.6, ω = 13 → kecepatan fase 5 unit/s, teredam e^(−0.32·d). */
private fun groundWave(xj: Float, s: Float, t: Float): Float {
    val dz = (s - 2.0f) * 0.9f
    val d = sqrt(xj * xj + dz * dz)
    val gate = ((5.2f * t - d) / 1.2f + 0.2f).coerceIn(0f, 1f)
    if (gate <= 0f) return 0f
    return sin(2.6f * d - 13f * t) * exp(-0.32f * d) * gate
}

/**
 * Lantai grid perspektif sejati (q = 1/z): baris meluncur ke kamera, garis vertikal menyatu di horizon.
 * Sumur gravitasi di bawah globe melengkungkan grid ([well]); setelah impact sumur mendalam sesaat
 * dan grid ber-riak oleh gelombang tanah.
 */
private fun DrawScope.drawFloor(
    phase: Float,
    waveT: Float,
    color: Color,
    path: Path,
    segs: Int,
    well: Float
) {
    val w = size.width
    val h = size.height
    val horizon = h * 0.66f
    val span = h - horizon
    val cx = w / 2f
    val u = 1.dp.toPx()
    val waving = waveT >= 0f && waveT < 3.4f
    val energy = if (waving) exp(-1.5f * waveT) else 0f
    val amp = 24.dp.toPx() * energy
    val fade = Brush.verticalGradient(
        colors = listOf(Color.Transparent, color.copy(alpha = (0.32f + 0.34f * energy).coerceAtMost(0.8f))),
        startY = horizon,
        endY = h
    )
    val stroke = Stroke(width = 1f * u)
    val colW = w * 0.16f
    val rows = 9

    // sumur gravitasi: profil Lorentzian (Yukawa-like) berpusat di bawah globe
    val wellAmt = well * (1f + 0.9f * energy)
    val wellR2 = (w * 0.42f) * (w * 0.42f)
    val wellDepth = h * 0.045f * wellAmt
    fun sag(px: Float, py: Float): Float {
        val dx = px - cx
        val dy = py - horizon
        return wellDepth * wellR2 / (dx * dx + dy * dy + wellR2 * 0.6f)
    }

    // kabut atmosfer di horizon
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Transparent,
            0.6f to color.copy(alpha = 0.10f + 0.16f * energy),
            1f to Color.Transparent,
            startY = horizon - span * 0.35f,
            endY = horizon + span * 0.25f
        ),
        topLeft = Offset(0f, horizon - span * 0.35f),
        size = Size(w, span * 0.6f)
    )

    val segsF = segs.toFloat()
    for (i in 0 until rows) {
        val s = i + 1f - phase
        val q = 1f / (1f + s * 0.5f)
        val yb = horizon + span * q
        path.reset()
        for (m in 0..segs) {
            val xj = -6.5f + 13f * (m / segsF)
            val lift = if (waving) amp * q * groundWave(xj, s, waveT) else 0f
            val px = cx + xj * colW * q
            val py = yb - lift + sag(px, yb)
            if (m == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path = path, brush = fade, style = stroke)
    }

    val samples = if (waving || well > 0.02f) 9 else 1
    for (j in -6..6) {
        path.reset()
        for (m in 0..samples) {
            val q = 0.02f + 0.98f * (m / samples.toFloat())
            val lift = if (waving) amp * q * groundWave(j.toFloat(), 2f * (1f / q - 1f), waveT) else 0f
            val px = cx + j * colW * q
            val yb = horizon + span * q
            val py = yb - lift + sag(px, yb)
            if (m == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path = path, brush = fade, style = stroke)
    }

    drawLine(
        brush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, color.copy(alpha = 0.55f + 0.3f * energy), Color.Transparent),
            startX = 0f,
            endX = w
        ),
        start = Offset(0f, horizon),
        end = Offset(w, horizon),
        strokeWidth = 1.4f * u
    )
}

/**
 * God rays volumetrik: sinar radial bergoyang pelan. Satu brush gradien dipakai bersama semua sinar
 * (alpha per sinar lewat parameter drawPath) dan satu Path didaur ulang → tanpa alokasi per sinar.
 */
private fun DrawScope.drawGodRays(
    time: Float,
    accent: Color,
    intensity: Float,
    glow: BlendMode,
    path: Path,
    rays: Int
) {
    if (intensity <= 0.01f) return
    val cx = size.width / 2f
    val cy = size.height / 2f
    val maxR = size.maxDimension * 0.80f
    val innerR = maxR * 0.18f
    val brush = Brush.radialGradient(
        0f to accent.copy(alpha = 0.20f),
        0.45f to accent.copy(alpha = 0.07f),
        0.85f to Color.Transparent,
        center = Offset(cx, cy),
        radius = maxR
    )
    val alpha = intensity.coerceIn(0f, 1f)
    for (i in 0 until rays) {
        val a = (i.toFloat() / rays) * TWO_PI + sin(time * 0.20f + i * 1.7f) * 0.055f
        val len = maxR * (0.52f + 0.34f * sin(time * 0.12f + i * 2.3f))
        val hw = 0.006f + 0.011f * abs(sin(time * 0.27f + i))
        val a1 = a - hw
        val a2 = a + hw
        path.reset()
        path.moveTo(cx + cos(a1) * innerR, cy + sin(a1) * innerR)
        path.lineTo(cx + cos(a2) * innerR, cy + sin(a2) * innerR)
        path.lineTo(cx + cos(a2) * len, cy + sin(a2) * len)
        path.lineTo(cx + cos(a1) * len, cy + sin(a1) * len)
        path.close()
        drawPath(path = path, brush = brush, alpha = alpha, blendMode = glow)
    }
}

/**
 * Globe jaringan (rigid body) + gelombang permukaan impact + scanner + 3 gimbal elips + satelit Kepler.
 * Cincin membawa beaming Doppler (sisi yang mendekat kamera lebih terang) dan menyala saat impact.
 * [front] = false → separuh BELAKANG (di belakang logo); true → separuh DEPAN.
 */
private fun DrawScope.drawOrbitalField(
    front: Boolean,
    world: SplashWorld,
    globe: GlobeData,
    base: Float,
    viewX: Float,
    viewY: Float,
    dotColor: Color,
    accent: Color,
    glow: BlendMode,
    ringSteps: Int,
    scanSteps: Int
) {
    world.prepare(viewX, viewY)
    val cx = size.width / 2f
    val cy = size.height / 2f
    val u = 1.dp.toPx()
    val out = world.tmpV
    val m = world.mM
    val pts = globe.pts
    val sc = globe.scratch
    val n = pts.size / 3
    val time = world.time
    val beam = 0.92f * sin(time * 1.6f) // posisi scanner sepanjang sumbu Y body

    val tImp = world.impactT
    val waving = tImp >= 0f && tImp < 2.8f
    val amp0 = if (waving) 0.075f * exp(-1.7f * tImp) else 0f

    // 0) Atmosfer: limb glow (hamburan di tepi bola) — hanya di lapisan belakang
    if (!front) {
        val glowR = base * 1.12f
        val pulse = if (waving) 1f + 1.6f * exp(-2.4f * tImp) else 1f
        drawCircle(
            brush = Brush.radialGradient(
                0f to accent.copy(alpha = 0.04f),
                0.70f to Color.Transparent,
                0.86f to accent.copy(alpha = (0.10f * pulse).coerceAtMost(0.5f)),
                0.92f to accent.copy(alpha = (0.30f * pulse).coerceAtMost(0.85f)),
                1f to Color.Transparent,
                center = Offset(cx, cy),
                radius = glowR
            ),
            radius = glowR,
            center = Offset(cx, cy)
        )
    }

    // 1) Proyeksikan semua node: rotasi → gelombang radial → perspektif → pencahayaan.
    //    Dihitung sekali per frame; pass belakang & depan berbagi hasilnya.
    if (globe.epoch != world.epoch || globe.width != size.width) {
        for (i in 0 until n) {
            matApply(m, pts[i * 3], pts[i * 3 + 1], pts[i * 3 + 2], out)
            val nx = out[0]
            val ny = out[1]
            val nz = out[2]
            var rho = 1f
            if (waving) {
                // gelombang permukaan teredam; sudut θ dari kutub yang menghadap kamera (titik impact)
                val th = acos(nz.coerceIn(-1f, 1f))
                val gate = ((2.6f * tImp - th) / 0.9f + 0.15f).coerceIn(0f, 1f)
                val spreadAmp = 1f / sqrt(0.25f + sin(th)) // fokus geometris di kutub & antipoda
                rho = 1f + amp0 * gate * spreadAmp * sin(7f * th - 18.2f * tImp)
            }
            val zr = nz * rho
            val s = FOV / (FOV - zr)
            val o = i * SC
            sc[o] = cx + nx * rho * base * s
            sc[o + 1] = cy + ny * rho * base * s
            sc[o + 2] = zr
            sc[o + 3] = rho - 1f
            sc[o + 4] = (nx * LIGHT_X + ny * LIGHT_Y + nz * LIGHT_Z).coerceAtLeast(0f)
            sc[o + 5] = nz
        }
        globe.epoch = world.epoch
        globe.width = size.width
    }

    // 2) Jaring penghubung — sisi yang meregang akibat gelombang menyala
    val pulseSeed = (time * 2.6f).toInt()
    val e = globe.edges
    for (k in 0 until e.size / 2) {
        val a = e[k * 2]
        val b = e[k * 2 + 1]
        val zm = (sc[a * SC + 2] + sc[b * SC + 2]) * 0.5f
        if ((zm >= 0f) != front) continue
        val depth = ((zm + 1f) * 0.5f).coerceIn(0f, 1f)
        val hot = (k + pulseSeed * 13) % 29 == 0
        val strain = (abs(sc[a * SC + 3]) + abs(sc[b * SC + 3])) * 0.5f
        val flare = (strain * 9f).coerceIn(0f, 1f)
        val p0 = Offset(sc[a * SC], sc[a * SC + 1])
        val p1 = Offset(sc[b * SC], sc[b * SC + 1])
        if (hot) {
            drawLine(
                color = accent.copy(alpha = 0.35f + 0.55f * depth),
                start = p0, end = p1, strokeWidth = 1.4f * u, blendMode = glow
            )
        } else if (flare > 0.02f) {
            drawLine(
                color = accent.copy(alpha = ((0.06f + 0.55f * flare) * (0.4f + 0.6f * depth)).coerceIn(0f, 1f)),
                start = p0, end = p1, strokeWidth = (0.6f + 0.9f * flare) * u, blendMode = glow
            )
        } else {
            drawLine(
                color = dotColor.copy(alpha = 0.03f + 0.15f * depth),
                start = p0, end = p1, strokeWidth = 0.6f * u
            )
        }
    }

    // 3) Node: Lambert + rim Fresnel; yang terkena scanner / gelombang menyala
    for (i in 0 until n) {
        val o = i * SC
        val z = sc[o + 2]
        if ((z >= 0f) != front) continue
        val s = FOV / (FOV - z)
        val depth = ((z + 1f) * 0.5f).coerceIn(0f, 1f)
        val lit = sc[o + 4]
        val rim = 1f - abs(sc[o + 5])
        val rim3 = rim * rim * rim
        val c = Offset(sc[o], sc[o + 1])
        val alpha = ((0.12f + 0.70f * depth) * (0.62f + 0.38f * lit) + 0.34f * rim3).coerceIn(0f, 1f)
        drawCircle(
            color = dotColor.copy(alpha = alpha),
            radius = (0.8f + 1.7f * depth) * u * s,
            center = c
        )
        val hit = 1f - (abs(pts[i * 3 + 1] - beam) / 0.12f).coerceIn(0f, 1f)
        if (hit > 0f) {
            drawCircle(accent.copy(alpha = 0.22f * hit), radius = (5f + 9f * hit) * u * s, center = c, blendMode = glow)
            drawCircle(accent.copy(alpha = 0.85f * hit), radius = (1.5f + 3.5f * hit) * u * s, center = c, blendMode = glow)
        }
        val d = sc[o + 3]
        if (d > 0.025f || d < -0.025f) {
            val f = (abs(d) * 8f).coerceIn(0f, 1f)
            drawCircle(Color.White.copy(alpha = 0.55f * f), radius = (1.2f + 2f * f) * u * s, center = c, blendMode = glow)
        }
    }

    // 4) Cincin scanner (kerangka body)
    val br = sqrt((1f - beam * beam).coerceAtLeast(0f))
    for (k in 0 until scanSteps) {
        val t = k * (TWO_PI / scanSteps)
        matApply(m, cos(t) * br, beam, sin(t) * br, out)
        val z = out[2]
        if ((z >= 0f) != front) continue
        val s = FOV / (FOV - z)
        drawCircle(
            color = accent.copy(alpha = 0.22f + 0.50f * ((z + 1f) * 0.5f).coerceIn(0f, 1f)),
            radius = 1.3f * u * s,
            center = Offset(cx + out[0] * base * s, cy + out[1] * base * s),
            blendMode = glow
        )
    }

    // 5) Gimbal elips (beaming Doppler) + satelit orbit Kepler dengan ekor komet
    val ringFlare = if (tImp >= 0f) 1f + 1.4f * exp(-2.2f * tImp) else 1f
    for (r in 0 until 3) {
        val mr = world.mRing[r]
        val a = RING_A[r]
        val ecc = RING_E[r]
        val b = a * sqrt(1f - ecc * ecc)
        val dir = if (RING_N[r] >= 0f) 1f else -1f

        for (k in 0 until ringSteps) {
            val ee = k * (TWO_PI / ringSteps)
            val sE = sin(ee)
            val cE = cos(ee)
            matApply(mr, a * (cE - ecc), 0f, b * sE, out)
            val z = out[2]
            if ((z >= 0f) != front) continue
            val s = FOV / (FOV - z)
            val depth = ((z / a + 1f) * 0.5f).coerceIn(0f, 1f)
            // vektor kecepatan orbit (tangen) → komponen sepanjang sumbu pandang → pergeseran Doppler
            val tx = -a * sE
            val tz = b * cE
            val tl = sqrt(tx * tx + tz * tz).coerceAtLeast(1e-4f)
            val vz = dir * (mr[6] * tx + mr[8] * tz) / tl
            val dop = (1f + 0.85f * vz).coerceIn(0.15f, 1.85f)
            val bright = dop * ringFlare
            drawCircle(
                color = (if (bright > 2.1f) Color.White else accent)
                    .copy(alpha = ((0.05f + 0.30f * depth) * bright).coerceIn(0f, 1f)),
                radius = (0.5f + 0.8f * depth) * u * s * (0.8f + 0.3f * dop),
                center = Offset(cx + out[0] * base * s, cy + out[1] * base * s)
            )
        }

        val mean = RING_N[r] * time + r * 2.1f
        for (j in 6 downTo 0) {
            val ee = solveKepler(mean - dir * j * 0.10f, ecc)
            matApply(mr, a * (cos(ee) - ecc), 0f, b * sin(ee), out)
            val z = out[2]
            if ((z >= 0f) != front) continue
            val s = FOV / (FOV - z)
            val c = Offset(cx + out[0] * base * s, cy + out[1] * base * s)
            if (j == 0) {
                drawCircle(accent.copy(alpha = 0.18f), radius = 11f * u * s, center = c, blendMode = glow)
                drawCircle(accent.copy(alpha = 0.40f), radius = 6f * u * s, center = c, blendMode = glow)
                drawCircle(Color.White.copy(alpha = 0.95f), radius = 2.6f * u * s, center = c)
            } else {
                val f = 1f - j / 7f
                drawCircle(accent.copy(alpha = 0.40f * f * f), radius = (0.8f + 3.2f * f) * u * s, center = c, blendMode = glow)
            }
        }
    }
}

/**
 * Medan dipol magnet: garis medan r = r₀·sin²θ pada bidang meridian, tersebar merata mengelilingi
 * sumbu Y body dan berputar pelan. Ikut goyang bersama globe (memakai matriks body).
 * Bagian di dalam r < 0.5 dilewati agar pusat (di balik logo) tidak penuh garis.
 */
private fun DrawScope.drawDipoleField(
    front: Boolean,
    world: SplashWorld,
    base: Float,
    viewX: Float,
    viewY: Float,
    color: Color,
    lines: Int,
    steps: Int,
    strength: Float
) {
    if (strength <= 0.01f) return
    world.prepare(viewX, viewY)
    val cx = size.width / 2f
    val cy = size.height / 2f
    val u = 1.dp.toPx()
    val out = world.tmpV
    val m = world.mM
    val dim = if (front) 0.55f else 1f
    val piF = PI.toFloat()
    for (l in 0 until lines) {
        val r0 = 1.30f + 0.11f * (l % 5)
        val phase = world.time * 0.35f + l * (TWO_PI / lines)
        val cp = cos(phase)
        val sp = sin(phase)
        var prevX = 0f
        var prevY = 0f
        var hasPrev = false
        for (k in 0..steps) {
            val th = piF * k / steps
            val st = sin(th)
            val r = r0 * st * st
            if (r < 0.5f) {
                hasPrev = false
                continue
            }
            val rho = r * st
            matApply(m, rho * cp, r * cos(th), rho * sp, out)
            if ((out[2] >= 0f) != front) {
                hasPrev = false
                continue
            }
            val s = FOV / (FOV - out[2])
            val px = cx + out[0] * base * s
            val py = cy + out[1] * base * s
            if (hasPrev) {
                val depth = ((out[2] + 1f) * 0.5f).coerceIn(0f, 1f)
                drawLine(
                    color = color.copy(alpha = ((0.05f + 0.30f * depth) * strength * dim).coerceIn(0f, 1f)),
                    start = Offset(prevX, prevY),
                    end = Offset(px, py),
                    strokeWidth = (0.5f + 0.7f * depth) * u
                )
            }
            prevX = px
            prevY = py
            hasPrev = true
        }
    }
}

/**
 * Tesseract (hiperkubus 4D): 16 titik, 32 rusuk. Dirotasi di bidang XW dan ZW (4D), diproyeksikan
 * 4D → 3D secara perspektif, lalu mengikuti orientasi body 3D. Saat impact rotasi 4D ditendang
 * sehingga kubus dalam "terbuka" sesaat.
 */
private fun DrawScope.drawTesseractCage(
    front: Boolean,
    world: SplashWorld,
    tess: SplashTesseract,
    base: Float,
    viewX: Float,
    viewY: Float,
    accent: Color,
    glow: BlendMode,
    strength: Float
) {
    if (strength <= 0.01f) return
    world.prepare(viewX, viewY)
    val cx = size.width / 2f
    val cy = size.height / 2f
    val u = 1.dp.toPx()
    val out = world.tmpV
    val pr = tess.proj
    val pts = tess.pts

    if (tess.epoch != world.epoch) {
        val a = world.phase4
        val cXW = cos(a)
        val sXW = sin(a)
        val cZW = cos(a * 0.6f)
        val sZW = sin(a * 0.6f)
        for (i in 0 until 16) {
            var x = pts[i * 4]
            val y = pts[i * 4 + 1]
            var z = pts[i * 4 + 2]
            var w = pts[i * 4 + 3]
            var t = x * cXW - w * sXW
            w = x * sXW + w * cXW
            x = t
            t = z * cZW - w * sZW
            w = z * sZW + w * cZW
            z = t
            val s4 = TESS_FOV4 / (TESS_FOV4 - w)
            matApply(world.mM, x * s4 * TESS_SCALE, y * s4 * TESS_SCALE, z * s4 * TESS_SCALE, out)
            pr[i * 3] = out[0]
            pr[i * 3 + 1] = out[1]
            pr[i * 3 + 2] = out[2]
        }
        tess.epoch = world.epoch
    }

    val tImp = world.impactT
    val flare = if (tImp >= 0f) 1f + 1.6f * exp(-2.5f * tImp) else 1f
    val dim = if (front) 0.7f else 1f
    val e = tess.edges
    for (k in 0 until 32) {
        val a = e[k * 2]
        val b = e[k * 2 + 1]
        val za = pr[a * 3 + 2]
        val zb = pr[b * 3 + 2]
        val zm = (za + zb) * 0.5f
        if ((zm >= 0f) != front) continue
        val sa = FOV / (FOV - za)
        val sb = FOV / (FOV - zb)
        val depth = ((zm + 1f) * 0.5f).coerceIn(0f, 1f)
        drawLine(
            color = accent.copy(alpha = ((0.16f + 0.50f * depth) * strength * flare * dim).coerceIn(0f, 1f)),
            start = Offset(cx + pr[a * 3] * base * sa, cy + pr[a * 3 + 1] * base * sa),
            end = Offset(cx + pr[b * 3] * base * sb, cy + pr[b * 3 + 1] * base * sb),
            strokeWidth = (0.8f + 0.5f * depth) * u,
            blendMode = glow
        )
    }
    for (i in 0 until 16) {
        val z = pr[i * 3 + 2]
        if ((z >= 0f) != front) continue
        val s = FOV / (FOV - z)
        val depth = ((z + 1f) * 0.5f).coerceIn(0f, 1f)
        drawCircle(
            color = Color.White.copy(alpha = ((0.30f + 0.50f * depth) * strength * dim).coerceIn(0f, 1f)),
            radius = (1.2f + 1.2f * depth) * u * s,
            center = Offset(cx + pr[i * 3] * base * s, cy + pr[i * 3 + 1] * base * s),
            blendMode = glow
        )
    }
}

/** Simpul trefoil (3 persilangan) berputar di dalam globe, dengan satu bead yang berjalan di sepanjang simpul. */
private fun DrawScope.drawTrefoilKnot(
    front: Boolean,
    world: SplashWorld,
    base: Float,
    viewX: Float,
    viewY: Float,
    accent: Color,
    glow: BlendMode,
    steps: Int,
    strength: Float
) {
    if (strength <= 0.01f) return
    world.prepare(viewX, viewY)
    val cx = size.width / 2f
    val cy = size.height / 2f
    val u = 1.dp.toPx()
    val out = world.tmpV
    val m = world.mM
    val ang = world.time * 0.7f
    val sa = sin(ang)
    val ca = cos(ang)
    val dim = if (front) 0.6f else 1f
    var prevX = 0f
    var prevY = 0f
    var hasPrev = false
    for (k in 0..steps) {
        val t = k * (TWO_PI / steps)
        val kx = (sin(t) + 2f * sin(2f * t)) * KNOT_SCALE
        val ky = (cos(t) - 2f * cos(2f * t)) * KNOT_SCALE
        val kz = -sin(3f * t) * KNOT_SCALE
        matApply(m, kx * ca + kz * sa, ky, -kx * sa + kz * ca, out)
        if ((out[2] >= 0f) != front) {
            hasPrev = false
            continue
        }
        val s = FOV / (FOV - out[2])
        val px = cx + out[0] * base * s
        val py = cy + out[1] * base * s
        if (hasPrev) {
            val depth = ((out[2] + 1f) * 0.5f).coerceIn(0f, 1f)
            drawLine(
                color = accent.copy(alpha = ((0.20f + 0.55f * depth) * strength * dim).coerceIn(0f, 1f)),
                start = Offset(prevX, prevY),
                end = Offset(px, py),
                strokeWidth = (0.8f + 1.2f * depth) * u * s,
                cap = StrokeCap.Round,
                blendMode = glow
            )
        }
        prevX = px
        prevY = py
        hasPrev = true
    }

    val tt = world.time * 1.6f
    val bx = (sin(tt) + 2f * sin(2f * tt)) * KNOT_SCALE
    val by = (cos(tt) - 2f * cos(2f * tt)) * KNOT_SCALE
    val bz = -sin(3f * tt) * KNOT_SCALE
    matApply(m, bx * ca + bz * sa, by, -bx * sa + bz * ca, out)
    if ((out[2] >= 0f) == front) {
        val s = FOV / (FOV - out[2])
        val c = Offset(cx + out[0] * base * s, cy + out[1] * base * s)
        drawCircle(accent.copy(alpha = 0.30f * strength), radius = 8f * u * s, center = c, blendMode = glow)
        drawCircle(Color.White.copy(alpha = 0.95f * strength), radius = 2.4f * u * s, center = c)
    }
}

/**
 * Shockwave 3D: ledakan Sedov–Taylor, radius R ∝ t^(2/5) (cepat di awal, melambat), dua lapis,
 * dengan turbulensi radial dan riak vertikal.
 */
private fun DrawScope.drawShockwave(
    shock: Float,
    world: SplashWorld,
    viewX: Float,
    viewY: Float,
    base: Float,
    accent: Color,
    glow: BlendMode,
    steps: Int
) {
    if (shock <= 0f || shock >= 1f) return
    world.prepare(viewX, viewY)
    val m = world.mView
    val cx = size.width / 2f
    val cy = size.height / 2f
    val u = 1.dp.toPx()
    val out = world.tmpV
    val a = 1f - shock
    val sr = 0.45f + 2.45f * shock.pow(0.4f)
    for (layer in 0 until 2) {
        val rl = sr * (1f - 0.04f * layer)
        val la = if (layer == 0) 1f else 0.45f
        for (k in 0 until steps) {
            val t = k * (TWO_PI / steps)
            val rr = rl * (1f + 0.045f * sin(7f * t + 11f * shock))
            val yv = 0.07f * a * sin(5f * t - 14f * shock + layer)
            matApply(m, cos(t) * rr, yv, sin(t) * rr, out)
            val s = FOV / (FOV - out[2])
            drawCircle(
                color = accent.copy(alpha = (0.85f * a * a * la).coerceIn(0f, 1f)),
                radius = (1.2f + 1.8f * a) * u * s * (1f - 0.25f * layer),
                center = Offset(cx + out[0] * base * s, cy + out[1] * base * s),
                blendMode = glow
            )
        }
    }
}

/** Partikel balistik 3D dengan motion-streak searah kecepatan; memantul di lantai. */
private fun DrawScope.drawBurst(
    world: SplashWorld,
    viewX: Float,
    viewY: Float,
    base: Float,
    accent: Color,
    glow: BlendMode
) {
    if (world.impactT < 0f || world.impactT > 2.2f) return
    world.prepare(viewX, viewY)
    val m = world.mView
    val cx = size.width / 2f
    val cy = size.height / 2f
    val u = 1.dp.toPx()
    val out = world.tmpV
    val out2 = world.tmpV2
    val p = world.pp
    for (i in 0 until world.pCount) {
        val life = world.pLife[i]
        val age = world.pAge[i]
        if (age >= life) continue
        val f = age / life
        val a = 1f - f
        val o = i * 6
        matApply(m, p[o], p[o + 1], p[o + 2], out)
        if (out[2] > 2.9f) continue
        matApply(m, p[o] - p[o + 3] * 0.05f, p[o + 1] - p[o + 4] * 0.05f, p[o + 2] - p[o + 5] * 0.05f, out2)
        val s1 = FOV / (FOV - out[2])
        val s0 = FOV / (FOV - out2[2].coerceAtMost(2.9f))
        val p1 = Offset(cx + out[0] * base * s1, cy + out[1] * base * s1)
        val p0 = Offset(cx + out2[0] * base * s0, cy + out2[1] * base * s0)
        drawLine(
            color = mix(Color.White, accent, f).copy(alpha = (a * 0.9f).coerceIn(0f, 1f)),
            start = p0,
            end = p1,
            strokeWidth = (1f + 1.6f * a) * u * s1,
            cap = StrokeCap.Round,
            blendMode = glow
        )
        drawCircle(Color.White.copy(alpha = a), radius = (0.9f + 1.1f * a) * u * s1, center = p1)
        if (i % 3 == 0) {
            drawCircle(accent.copy(alpha = 0.22f * a), radius = (4f + 5f * a) * u * s1, center = p1, blendMode = glow)
        }
    }
}

// ═════════════════════════ SPLASH SCREEN ═════════════════════════

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Zuan3DCustomSplashScreen(onSplashFinished: () -> Unit) {
    // Ikuti mode terang/gelap APP (Wallpaper Style Screen -> themeMode), bukan tema sistem HP.
    val isDark = zkmIsDarkTheme()
    val splashHoldTime = 1500L
    val title = "Zuan Manager"
    val bootLines = listOf(
        "Initializing kernel interface",
        "Probing CPU / GPU / thermal nodes",
        "Applying saved profiles"
    )

    // --- Color tokens (Obsidian Modern) ---
    val bgTop = if (isDark) Color(0xFF0B0F14) else Color(0xFFFFFFFF)
    val bgBottom = if (isDark) Color(0xFF05070A) else Color(0xFFE6EEF3)
    val textPrimary = if (isDark) Color(0xFFECEFF1) else Color(0xFF1E262C)
    val textSecondary = if (isDark) Color(0xFF90A4AE) else Color(0xFF607D8B)
    val accent = if (isDark) Color(0xFF4DD0E1) else Color(0xFF00838F)
    val dotColor = if (isDark) Color(0xFF80CBC4) else Color(0xFF00695C)
    val starColor = if (isDark) Color(0xFFB2EBF2) else Color(0xFF00796B)
    val shimmer = if (isDark) Color(0xFFB2EBF2) else Color(0xFF00ACC1)
    val streakCore = if (isDark) Color.White else accent
    val accentAura = if (isDark) Color(0xFF26A69A).copy(alpha = 0.26f) else Color(0xFF00897B).copy(alpha = 0.14f)
    val badgeBg = if (isDark) Color(0xFF141920) else Color(0xFFE3E9ED)
    val cardBg = if (isDark) Color(0xFF131821) else Color(0xFFFFFFFF)
    val vignetteColor = if (isDark) Color(0xB3000000) else Color(0x3314303A)
    // Emisif (bloom) hanya masuk akal di tema gelap; di tema terang Plus akan memutihkan
    val glowBlend = if (isDark) BlendMode.Plus else BlendMode.SrcOver

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val finish by rememberUpdatedState(onSplashFinished) // selalu memanggil callback versi terbaru
    val finished = remember { booleanArrayOf(false) }
    val finishOnce: () -> Unit = {
        if (!finished[0]) {
            finished[0] = true
            finish()
        }
    }

    // Tier performa: HP kelas bawah (core sedikit / low-RAM) atau Battery Saver aktif langsung mulai
    // di tier hemat; HP lain turun otomatis bila frame terukur lambat di awal splash (lihat loop fisika).
    val baseLowEnd = remember {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        Runtime.getRuntime().availableProcessors() <= 4 ||
            am?.isLowRamDevice == true ||
            pm?.isPowerSaveMode == true
    }
    var degraded by remember { mutableStateOf(false) }
    val lowEnd = baseLowEnd || degraded
    val quality = if (lowEnd) QUALITY_LOW else QUALITY_HIGH
    val globe = remember(lowEnd) { if (lowEnd) buildGlobe(110, 0.43f) else buildGlobe(170, 0.35f) }
    val stars = remember(lowEnd) { buildStars(if (lowEnd) 90 else 150) }
    val burstParts = remember { buildBurst(if (baseLowEnd) 40 else 70) }
    val tess = remember { SplashTesseract() }
    val floorPath = remember { Path() }
    val rayPath = remember { Path() }
    val world = remember { SplashWorld(title.length, burstParts) }

    // Label versi dari versionName aplikasi; bila gagal dibaca, pakai teks bawaan lama
    val versionLabel = remember {
        runCatching {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName
        }.getOrNull()?.takeIf { it.isNotBlank() }?.let { "v$it" } ?: "v3.0.0-PROD"
    }

    // Double-tap = lewati splash (lompat hyperspace keluar yang dipercepat)
    var skipping by remember { mutableStateOf(false) }

    // Hormati setelan "Remove animations" / animator scale = 0
    val animScale = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    // --- Animatables (koreografi); gerak fisik ada di SplashWorld ---
    val warp = remember { Animatable(2.2f) }
    val logoScale = remember { Animatable(0.2f) }
    val logoRotX = remember { Animatable(40f) }
    val logoRotY = remember { Animatable(-200f) }
    val fieldAlpha = remember { Animatable(0f) }
    val fieldScale = remember { Animatable(0.4f) }
    val auraAlpha = remember { Animatable(0f) }
    val floorAlpha = remember { Animatable(0f) }
    val shock = remember { Animatable(0f) }
    val shock2 = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    val godRay = remember { Animatable(0f) }      // intensitas god rays
    val extraAlpha = remember { Animatable(0f) }  // dipol + tesseract + simpul trefoil
    val well = remember { Animatable(0f) }        // kedalaman sumur gravitasi di lantai

    val bootAlpha = remember { List(bootLines.size) { Animatable(0f) } }
    val bootDone = remember { List(bootLines.size) { mutableStateOf(false) } } // "[ .. ]" → "[ OK ]"
    val badgeAlpha = remember { Animatable(0f) }
    val subtitleAlpha = remember { Animatable(0f) }
    val loaderAlpha = remember { Animatable(0f) }
    val exitProgress = remember { Animatable(0f) }

    // Tilt dari accelerometer; tilt dari jari hidup di SplashWorld (pegas)
    val sensorX = remember { mutableFloatStateOf(0f) }
    val sensorY = remember { mutableFloatStateOf(0f) }
    val effX: () -> Float = { world.subscribe(); world.tiltX.x + sensorX.floatValue }
    val effY: () -> Float = { world.subscribe(); world.tiltY.x + sensorY.floatValue }

    // Orientasi kartu logo (derajat): entrance + tilt + wobble fisik + pose diam + sway idle
    val cardRotX: () -> Float = {
        logoRotX.value + effX() * 0.45f + world.wobX.x + 7f + 5f * sin(1.3f * world.time)
    }
    val cardRotY: () -> Float = {
        logoRotY.value + effY() * 0.45f + world.wobY.x + 9f + 8f * sin(0.9f * world.time + 1f)
    }

    /**
     * Terapkan transformasi kartu pada sebuah layer. [depthDp] = jarak irisan di belakang muka kartu;
     * irisan digeser sepanjang proyeksi layar dari normal permukaan → ekstrusi 3D yang konsisten.
     * Mengembalikan faktor hadap (n·z).
     */
    fun GraphicsLayerScope.applyCard(depthDp: Float): Float {
        val rx = cardRotX()
        val ry = cardRotY()
        val s = logoScale.value
        scaleX = s
        scaleY = s
        rotationX = rx
        rotationY = ry
        rotationZ = world.wobZ.x
        cameraDistance = 14f * density
        val ax = rx * D2R
        val ay = ry * D2R
        val reach = depthDp * density * s * 1.25f
        translationX = -sin(ay) * cos(ax) * reach
        translationY = (world.hover() * 6f + world.logoDy.x) * density + sin(ax) * reach
        return cos(ay) * cos(ax)
    }

    // --- Animasi ambient (dibaca di fase draw → tanpa recomposition) ---
    val infinite = rememberInfiniteTransition(label = "ambient")
    val shine = infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "shine"
    )
    val gridPhase = infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "grid"
    )

    /** Satu pass medan 3D untuk separuh BELAKANG atau DEPAN logo: dipol → globe → tesseract → simpul. */
    fun DrawScope.drawField(front: Boolean) {
        val base = 300.dp.toPx() * 0.30f
        val vx = effX()
        val vy = effY()
        val ex = extraAlpha.value
        drawDipoleField(front, world, base, vx, vy, dotColor, quality.dipLines, quality.dipSteps, ex)
        drawOrbitalField(
            front, world, globe, base, vx, vy, dotColor, accent, glowBlend,
            quality.ringSteps, quality.scanSteps
        )
        drawTesseractCage(front, world, tess, base, vx, vy, accent, glowBlend, ex)
        drawTrefoilKnot(front, world, base, vx, vy, accent, glowBlend, quality.knotSteps, ex)
    }

    val popSpec = spring<Float>(dampingRatio = 0.5f, stiffness = 260f)
    val settleSpec = spring<Float>(dampingRatio = 0.62f, stiffness = 120f)

    // Mesin fisika: satu loop per frame, substep adaptif. Sekaligus memantau frame-time:
    // bila ≥ 0.35 dtk frame lambat (< ~38 fps) terkumpul di awal splash, turun ke tier hemat (sekali saja).
    LaunchedEffect(world) {
        var last = withFrameNanos { it }
        var elapsed = 0f
        var slow = 0f
        var seenLand = 0
        var lastHaptic = 0L
        while (true) {
            val now = withFrameNanos { it }
            val raw = (now - last) / 1_000_000_000f
            last = now
            elapsed += raw
            if (!baseLowEnd && !degraded && elapsed > 0.35f && elapsed < 3.5f) {
                slow = if (raw > 0.026f) slow + raw.coerceAtMost(0.05f) else (slow - raw * 0.5f).coerceAtLeast(0f)
                if (slow > 0.35f) degraded = true
            }
            world.advance(raw.coerceIn(0f, 0.05f), warp.value)

            // Tick haptic ringan setiap huruf judul mendarat (dibatasi laju; huruf spasi dilewati)
            if (world.landCount != seenLand) {
                seenLand = world.landCount
                if (now - lastHaptic > HAPTIC_GAP_NS && title.getOrNull(world.lastLandIdx) != ' ') {
                    lastHaptic = now
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        }
    }

    // Sensor accelerometer → scene ikut miring saat HP digerakkan
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var baseX = Float.NaN
        var baseZ = Float.NaN
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val ax = event.values[0]
                val az = event.values[2]
                if (baseX.isNaN()) {
                    baseX = ax
                    baseZ = az
                }
                val ny = ((ax - baseX) * -4f).coerceIn(-35f, 35f)   // kalau arah terbalik, ubah ke 4f
                val nx = ((az - baseZ) * 4f).coerceIn(-35f, 35f)
                sensorY.floatValue += (ny - sensorY.floatValue) * 0.12f
                sensorX.floatValue += (nx - sensorX.floatValue) * 0.12f
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sm != null && sensor != null) {
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { sm?.unregisterListener(listener) }
    }

    // --- Timeline utama ---
    LaunchedEffect(skipping) {
        if (animScale == 0f) {          // user mematikan animasi → langsung lanjut
            delay(300)
            finishOnce()
            return@LaunchedEffect
        }
        if (skipping) {                 // double-tap → keluar cepat dari keadaan apa pun
            launch { warp.animateTo(3.2f, tween(260, easing = FastOutLinearInEasing)) }
            exitProgress.animateTo(1f, tween(260, easing = FastOutLinearInEasing))
            finishOnce()
            return@LaunchedEffect
        }

        // 1. Hyperspace warp melambat (lensa gravitasi menguat), medan 3D & lantai grid muncul
        launch { warp.animateTo(0.06f, tween(2100, easing = FastOutSlowInEasing)) }
        launch { delay(250); auraAlpha.animateTo(1f, tween(700)) }
        launch { delay(250); fieldAlpha.animateTo(1f, tween(800)) }
        launch { delay(250); fieldScale.animateTo(1f, spring(0.6f, 140f)) }
        launch { delay(900); floorAlpha.animateTo(1f, tween(900)) }
        launch { delay(300); well.animateTo(1f, tween(1400)) }
        launch { delay(600); godRay.animateTo(1f, tween(1200)) }
        launch { delay(700); extraAlpha.animateTo(1f, tween(900)) }

        // 2. Logo berputar 3D masuk dari kedalaman
        delay(400)
        launch { logoScale.animateTo(1f, popSpec) }
        launch { logoRotX.animateTo(0f, settleSpec) }
        launch { logoRotY.animateTo(0f, settleSpec) }

        // 3. IMPACT: fisika ditendang (globe, kartu, kamera, partikel) + shockwave + flash + haptic
        delay(520)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        world.impact()
        launch { shock.animateTo(1f, tween(1300, easing = LinearEasing)) }
        launch { delay(180); shock2.animateTo(1f, tween(1300, easing = LinearEasing)) }
        launch { flash.snapTo(0.6f); flash.animateTo(0f, tween(500)) }

        // 4. Judul jatuh + memantul + boot log
        delay(100)
        world.dropTitle()
        launch { badgeAlpha.animateTo(1f, tween(400)) }
        bootAlpha.forEachIndexed { i, a ->
            launch {
                delay(200L + i * 260L)
                a.animateTo(1f, tween(250))
                delay(170)
                bootDone[i].value = true
            }
        }

        // 5. Subtitle + loader
        delay(380)
        launch { subtitleAlpha.animateTo(1f, tween(450)) }
        delay(160)
        launch { loaderAlpha.animateTo(1f, tween(400)) }

        // 6. Tahan, lalu lompat hyperspace keluar
        delay(splashHoldTime)
        launch { warp.animateTo(3.2f, tween(380, easing = FastOutLinearInEasing)) }
        exitProgress.animateTo(1f, tween(380, easing = FastOutLinearInEasing))
        finishOnce()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                if (ALLOW_DOUBLE_TAP_SKIP) {
                    detectTapGestures(onDoubleTap = { skipping = true })
                }
            }
            .graphicsLayer {
                world.subscribe()
                val p = exitProgress.value
                val z = 1f + 0.18f * p + world.camZoom.x
                scaleX = z
                scaleY = z
                alpha = 1f - p
                translationX = world.camX.x.dp.toPx()
                translationY = world.camY.x.dp.toPx()
                rotationZ = world.camRot.x
            }
            .background(Brush.verticalGradient(listOf(bgTop, bgBottom))),
        contentAlignment = Alignment.Center
    ) {
        // ───────── Terowongan warp + lensa gravitasi (latar) ─────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            world.subscribe()
            val base = 300.dp.toPx() * 0.30f
            val lens = ((2.2f - warp.value) / 2.15f).coerceIn(0f, 1f)
            drawWarp(
                stars = stars,
                phase = world.warpPhase,
                warp = warp.value,
                lensStrength = lens * lens,
                lensRadius = base * 1.15f,
                color = starColor,
                core = streakCore,
                glow = glowBlend,
                chroma = isDark && !lowEnd
            )
        }

        // ───────── Lantai grid perspektif + gelombang tanah ─────────
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = floorAlpha.value }
        ) {
            world.subscribe()
            drawFloor(gridPhase.value, world.impactT, accent, floorPath, quality.floorSegs, well.value)
        }

        // ───────── God rays volumetrik (menyembur saat impact) ─────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            world.subscribe()
            val tImp = world.impactT
            val spike = if (tImp >= 0f) 1.05f * exp(-3.2f * tImp) else 0f
            drawGodRays(world.time, accent, godRay.value * (0.55f + spike), glowBlend, rayPath, quality.rays)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            // ───────── STAGE 3D ─────────
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(300.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { world.dragging = true },
                            onDragEnd = { world.dragging = false },
                            onDragCancel = { world.dragging = false }
                        ) { change, drag ->
                            change.consume()
                            world.drag(drag.x, drag.y)
                        }
                    }
            ) {
                // Aura radial — berdenyut saat impact
                Box(
                    modifier = Modifier
                        .size(300.dp)
                        .graphicsLayer {
                            world.subscribe()
                            alpha = auraAlpha.value
                            val pulse = if (world.impactT >= 0f) 0.30f * exp(-2.4f * world.impactT) else 0f
                            val s = fieldScale.value * (1f + 0.04f * world.hover() + pulse)
                            scaleX = s
                            scaleY = s
                        }
                        .background(
                            brush = Brush.radialGradient(listOf(accentAura, Color.Transparent)),
                            shape = CircleShape
                        )
                )

                // Medan 3D — separuh BELAKANG logo
                Canvas(
                    modifier = Modifier
                        .requiredSize(520.dp)
                        .graphicsLayer {
                            alpha = fieldAlpha.value
                            scaleX = fieldScale.value
                            scaleY = fieldScale.value
                        }
                ) {
                    drawField(front = false)
                }

                // ── Kartu logo 3D: irisan ketebalan sejati + muka berpencahayaan ──
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(112.dp)
                ) {
                    // Pelat cahaya terjauh
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .graphicsLayer {
                                val f = applyCard(17f)
                                scaleX *= 1.08f
                                scaleY *= 1.08f
                                alpha = 0.25f + 0.75f * f.coerceIn(0f, 1f)
                            }
                            .clip(RoundedCornerShape(26.dp))
                            .background(accent.copy(alpha = 0.10f))
                    )
                    // Irisan ketebalan kartu (6 lapis)
                    for (i in 6 downTo 1) {
                        val depth = i * 2.4f
                        Box(
                            modifier = Modifier
                                .size(92.dp)
                                .graphicsLayer {
                                    val f = applyCard(depth)
                                    alpha = 0.25f + 0.75f * f.coerceIn(0f, 1f)
                                }
                                .clip(RoundedCornerShape(26.dp))
                                .background(accent.copy(alpha = if (isDark) 0.20f else 0.15f))
                                .border(0.5.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(26.dp))
                        )
                    }
                    // Muka kartu
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(92.dp)
                            .graphicsLayer { applyCard(0f) }
                            .clip(RoundedCornerShape(26.dp))
                            .background(cardBg)
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    listOf(
                                        accent.copy(alpha = if (isDark) 0.55f else 0.35f),
                                        Color.Transparent,
                                        accent.copy(alpha = 0.18f)
                                    )
                                ),
                                shape = RoundedCornerShape(26.dp)
                            )
                            .drawWithContent {
                                drawContent()
                                world.subscribe()
                                val w = size.width
                                val hh = size.height
                                val u = 1.dp.toPx()
                                val rxr = cardRotX() * D2R
                                val ryr = cardRotY() * D2R
                                // normal permukaan kartu (konvensi tanda sama dengan ekstrusi)
                                val nx = sin(ryr) * cos(rxr)
                                val ny = -sin(rxr)
                                val nzc = cos(ryr) * cos(rxr)
                                val diffuse = (nx * LIGHT_X + ny * LIGHT_Y + nzc * LIGHT_Z).coerceAtLeast(0f)
                                val hx = LIGHT_X
                                val hy = LIGHT_Y
                                val hz = LIGHT_Z + 1f
                                val hl = sqrt(hx * hx + hy * hy + hz * hz)
                                val nh = ((nx * hx + ny * hy + nzc * hz) / hl).coerceAtLeast(0f)
                                val spec = nh.pow(10f)

                                // 1) Lambert: sisi yang menjauhi cahaya menggelap
                                val shade = (if (isDark) 0.30f else 0.16f) * (1f - diffuse)
                                drawRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = shade.coerceIn(0f, 0.3f))),
                                        start = Offset(w * (0.5f + LIGHT_X), hh * (0.5f + LIGHT_Y)),
                                        end = Offset(w * (0.5f - LIGHT_X), hh * (0.5f - LIGHT_Y))
                                    )
                                )

                                // 2) kilau yang menyapu
                                val x = -w + (w * 3f) * shine.value
                                drawRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.White.copy(alpha = if (isDark) 0.16f else 0.35f),
                                            Color.Transparent
                                        ),
                                        start = Offset(x, 0f),
                                        end = Offset(x + w * 0.8f, hh)
                                    )
                                )

                                // 3) Blinn-Phong: highlight bergeser mengikuti normal
                                val specAlpha = (if (isDark) 0.12f else 0.26f) + (if (isDark) 0.30f else 0.40f) * spec
                                drawRect(
                                    brush = Brush.radialGradient(
                                        colors = listOf(Color.White.copy(alpha = specAlpha.coerceIn(0f, 0.8f)), Color.Transparent),
                                        center = Offset(w * (0.5f + 1.05f * nx), hh * (0.5f + 1.05f * ny)),
                                        radius = w * 0.8f
                                    )
                                )

                                // 4) Tepi iridescent: interferensi film tipis, bergeser dengan sudut pandang
                                val cosT = nzc.coerceAtLeast(0.2f)
                                val tt = world.time
                                val rim = ArrayList<Color>(13)
                                for (k in 0..12) {
                                    val phi = (k % 12) * (TWO_PI / 12f)
                                    val d = 480f + 150f * sin(2f * phi + 0.9f * tt) +
                                        70f * cos(3f * phi - 0.4f * tt) + 160f * (1f - cosT)
                                    rim.add(thinFilm(cosT, d, if (isDark) 0.62f else 0.5f))
                                }
                                drawRoundRect(
                                    brush = Brush.sweepGradient(rim, Offset(w / 2f, hh / 2f)),
                                    cornerRadius = CornerRadius(26.dp.toPx()),
                                    style = Stroke(width = 1.6f * u)
                                )
                            }
                    ) {
                        // Bayangan glyph di atas muka kartu (efek timbul)
                        Image(
                            painter = painterResource(id = R.drawable.ic_app),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(Color.Black.copy(alpha = if (isDark) 0.45f else 0.22f)),
                            modifier = Modifier
                                .size(54.dp)
                                .graphicsLayer {
                                    translationX = 2.2.dp.toPx()
                                    translationY = 3.dp.toPx()
                                }
                        )
                        // Glyph melayang: paralaks mengikuti normal permukaan
                        Image(
                            painter = painterResource(id = R.drawable.ic_app),
                            contentDescription = stringResource(R.string.lp_a11y_app_logo),
                            modifier = Modifier
                                .size(54.dp)
                                .graphicsLayer {
                                    val ax = cardRotX() * D2R
                                    val ay = cardRotY() * D2R
                                    translationX = sin(ay) * cos(ax) * 9.dp.toPx()
                                    translationY = -sin(ax) * 9.dp.toPx()
                                }
                        )
                    }
                }

                // Medan 3D — separuh DEPAN logo
                Canvas(
                    modifier = Modifier
                        .requiredSize(520.dp)
                        .graphicsLayer {
                            alpha = fieldAlpha.value
                            scaleX = fieldScale.value
                            scaleY = fieldScale.value
                        }
                ) {
                    drawField(front = true)
                }

                // Double shockwave (Sedov–Taylor) + partikel balistik (kanvas besar supaya tidak terpotong)
                Canvas(modifier = Modifier.requiredSize(720.dp)) {
                    val base = 300.dp.toPx() * 0.30f
                    val vx = effX()
                    val vy = effY()
                    drawShockwave(shock.value, world, vx, vy, base, accent, glowBlend, quality.shockSteps)
                    drawShockwave(shock2.value, world, vx, vy, base, accent, glowBlend, quality.shockSteps)
                    drawBurst(world, vx, vy, base, accent, glowBlend)
                }

                // Lens streak anamorphic saat impact
                Box(
                    modifier = Modifier
                        .requiredSize(width = 720.dp, height = 2.dp)
                        .graphicsLayer {
                            val f = (flash.value / 0.6f).coerceIn(0f, 1f)
                            alpha = f
                            scaleX = 0.25f + 0.75f * (1f - f)
                        }
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, accent, streakCore, accent, Color.Transparent)
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Badge
            Box(
                modifier = Modifier
                    .graphicsLayer { alpha = badgeAlpha.value }
                    .clip(RoundedCornerShape(50))
                    .background(badgeBg)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "KERNEL ENGINE NEXT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        letterSpacing = 1.6.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = textSecondary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Judul: huruf jatuh (gravitasi) + memantul + berputar pegas, kilau shimmer menyapu teks
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val w = size.width
                        val x = -w * 0.6f + w * 2.2f * shine.value
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(Color.Transparent, shimmer, Color.Transparent),
                                start = Offset(x, 0f),
                                end = Offset(x + w * 0.5f, size.height)
                            ),
                            blendMode = BlendMode.SrcAtop
                        )
                    }
            ) {
                title.forEachIndexed { i, ch ->
                    Text(
                        text = ch.toString(),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 30.sp,
                            letterSpacing = (-0.8).sp
                        ),
                        color = textPrimary,
                        modifier = Modifier.graphicsLayer {
                            world.subscribe()
                            val y = world.letterY[i]
                            translationY = y.dp.toPx()
                            rotationX = world.letterRot[i]
                            alpha = (1f + y / LETTER_DROP).coerceIn(0f, 1f)
                            cameraDistance = 12f * density
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "High-Performance Root Orchestrator",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.2.sp
                ),
                color = textSecondary,
                modifier = Modifier.graphicsLayer {
                    alpha = subtitleAlpha.value
                    translationY = (1f - subtitleAlpha.value) * 12.dp.toPx()
                }
            )
        }

        // ───────── Boot log + loader + versi ─────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                bootLines.forEachIndexed { i, line ->
                    Text(
                        text = buildAnnotatedString {
                            val done = bootDone[i].value
                            withStyle(
                                SpanStyle(
                                    color = if (done) accent else textSecondary.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append(if (done) "[ OK ] " else "[ .. ] ")
                            }
                            withStyle(SpanStyle(color = textSecondary)) {
                                append(line)
                            }
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            letterSpacing = 0.4.sp
                        ),
                        modifier = Modifier.graphicsLayer {
                            alpha = bootAlpha[i].value
                            translationX = (1f - bootAlpha[i].value) * -10.dp.toPx()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier.graphicsLayer { alpha = loaderAlpha.value },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearWavyProgressIndicator(
                    modifier = Modifier.width(136.dp),
                    color = accent,
                    trackColor = textSecondary.copy(alpha = 0.2f),
                    amplitude = 0.55f,
                    wavelength = 18.dp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = versionLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 1.2.sp
                    ),
                    color = textSecondary.copy(alpha = 0.6f)
                )
            }
        }

        // ───────── Vignette sinematik ─────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    0f to Color.Transparent,
                    0.5f to Color.Transparent,
                    1f to vignetteColor,
                    center = center,
                    radius = size.maxDimension * 0.62f
                )
            )
        }

        // ───────── Flash saat impact ─────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = flash.value }
                .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), Color.Transparent)))
        )

        // ───────── Hint skip (murni additive: timeline & animasi splash tidak diubah) ─────────
        if (ALLOW_DOUBLE_TAP_SKIP && animScale > 0f) {
            val skipHintAlpha = remember { Animatable(0f) }
            LaunchedEffect(skipping) {
                if (skipping) return@LaunchedEffect
                delay(1600)
                skipHintAlpha.animateTo(1f, tween(500))
            }
            Text(
                text = stringResource(R.string.help_splash_skip),
                color = textSecondary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                    .graphicsLayer { alpha = skipHintAlpha.value * (1f - exitProgress.value) }
            )
        }
    }
}
