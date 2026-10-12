/*
 * Copyright (c) 2025-2026 ZKM (Zuan Kernel Manager)
 * INTRO "SPATIAL" EDITION — FULL 3D hero engine, aurora depth background, glass UI, kinetic motion.
 *
 *  • 3 hero 3D sungguhan (proyeksi perspektif + depth-sort, tanpa library): Layer Stack, Hazard Octahedron, Vault Cube
 *  • Digerakkan oleh swipe pager (objek ikut berputar), accelerometer (parallax), dan ketukan (impuls fisika pegas)
 *  • Latar aurora + debu bintang berlapis kedalaman + lantai grid perspektif; adaptif tema terang/gelap
 *  • Halaman berputar ala kubus, teks kinetik berlapis, kartu penjelasan masuk dengan rotasi 3D
 *  • Hormati setelan "Remove animations" (pose statis)
 *
 *  Final step: "Select working mode" (Basic / ADB / ROOT). The chosen mode is stored through
 *  WorkingModeManager and limits which features the rest of the app unlocks.
 *
 *  All user-facing text lives in values/strings.xml (English) so the language pack
 *  export / import keeps working for every string.
 */
package com.zuan.kernelmanager.next.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Adb
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.EnergySavingsLeaf
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.core.mode.WorkingMode
import com.zuan.kernelmanager.next.core.mode.WorkingModeManager
import com.zuan.kernelmanager.next.feature.settings.SettingsPreference
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

// ═════════════════════════ PALET ═════════════════════════
val ZkmDark = Color(0xFF0F172A)
val ZkmMuted = Color(0xFF64748B)
val ZkmSurfaceBg = Color(0xFFF8FAFC)

val ThemeBlue = Color(0xFF0284C7)
val ThemeRed = Color(0xFFDC2626)
val ThemeGreen = Color(0xFF059669)

sealed class IntroPage(
    val stepLabel: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val themeColor: Color,
    val isRiskPage: Boolean = false,
    val accent2: Color = themeColor,
    val insights: List<Int> = emptyList(),
    val insightIcons: List<ImageVector> = emptyList()
) {
    object Intro : IntroPage(
        "01 / ARCHITECTURE", R.string.intro_p1_title, R.string.intro_p1_desc, ThemeBlue,
        accent2 = Color(0xFF22D3EE),
        insights = listOf(R.string.intro_x_p1_i1, R.string.intro_x_p1_i2, R.string.intro_x_p1_i3),
        insightIcons = listOf(Icons.Rounded.Memory, Icons.Rounded.Tune, Icons.Rounded.Save)
    )

    object Risk : IntroPage(
        "02 / PRIVILEGED ACCESS", R.string.intro_p2_title, R.string.intro_p2_desc, ThemeRed, true,
        accent2 = Color(0xFFF97316),
        insights = listOf(R.string.intro_x_p2_i1, R.string.intro_x_p2_i2, R.string.intro_x_p2_i3),
        insightIcons = listOf(Icons.Rounded.AdminPanelSettings, Icons.Rounded.LocalFireDepartment, Icons.Rounded.Backup)
    )

    object Privacy : IntroPage(
        "03 / DATA ISOLATION", R.string.intro_p3_title, R.string.intro_p3_desc, ThemeGreen,
        accent2 = Color(0xFF2DD4BF),
        insights = listOf(R.string.intro_x_p3_i1, R.string.intro_x_p3_i2, R.string.intro_x_p3_i3),
        insightIcons = listOf(Icons.Rounded.Lock, Icons.Rounded.TouchApp, Icons.Rounded.Code)
    )
}

internal class IntroColors(val dark: Boolean) {
    val bgTop = if (dark) Color(0xFF070A10) else ZkmSurfaceBg
    val bgBottom = if (dark) Color(0xFF020409) else Color(0xFFE3ECF5)
    val text = if (dark) Color(0xFFF1F5F9) else ZkmDark
    val muted = if (dark) Color(0xFF94A3B8) else ZkmMuted
    val glassA = if (dark) Color.White.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.88f)
    val glassB = if (dark) Color.White.copy(alpha = 0.03f) else Color.White.copy(alpha = 0.55f)
    val strokeA = if (dark) Color.White.copy(alpha = 0.24f) else Color.White
    val strokeB = if (dark) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.4f)
}

internal fun Modifier.glass(colors: IntroColors, shape: Shape): Modifier =
    this
        .clip(shape)
        .background(Brush.linearGradient(listOf(colors.glassA, colors.glassB)))
        .border(1.dp, Brush.linearGradient(listOf(colors.strokeA, colors.strokeB)), shape)

// ═════════════════════════ UTIL & MESIN 3D ═════════════════════════
private const val FOV = 4f
private val PI_F = PI.toFloat()
private val TAU = (2.0 * PI).toFloat()

private fun frac(x: Float): Float = x - floor(x)
private fun mixF(a: Float, b: Float, t: Float): Float = a + (b - a) * t
internal fun smooth(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Kamera orbit: yaw (sumbu Y) lalu pitch (sumbu X) + proyeksi perspektif. z lebih besar = lebih dekat. */
private class Cam {
    private var cYaw = 1f
    private var sYaw = 0f
    private var cPit = 1f
    private var sPit = 0f
    val o = FloatArray(3)

    fun set(yaw: Float, pitch: Float) {
        cYaw = cos(yaw); sYaw = sin(yaw); cPit = cos(pitch); sPit = sin(pitch)
    }

    fun apply(x: Float, y: Float, z: Float) {
        val x1 = x * cYaw + z * sYaw
        val z1 = -x * sYaw + z * cYaw
        o[0] = x1
        o[1] = y * cPit - z1 * sPit
        o[2] = y * sPit + z1 * cPit
    }

    /** out = (layarX, layarY, kedalaman, skala perspektif) */
    fun project(x: Float, y: Float, z: Float, cx: Float, cy: Float, base: Float, out: FloatArray) {
        apply(x, y, z)
        val s = FOV / (FOV - o[2])
        out[0] = cx + o[0] * base * s
        out[1] = cy + o[1] * base * s
        out[2] = o[2]
        out[3] = s
    }
}

/** Buffer kerja bersama → nol alokasi per frame. */
private class Gfx {
    val a = FloatArray(4)
    val b = FloatArray(4)
    val c = FloatArray(4)
    val d = FloatArray(4)
    val vp = FloatArray(8 * 4)
    val ord = IntArray(8)
    val zs = FloatArray(8)
    val path = Path()

    /** Urutkan indeks 0 until n berdasarkan zs naik (jauh → dekat, painter's algorithm). */
    fun sortByZ(n: Int) {
        for (i in 0 until n) ord[i] = i
        for (i in 1 until n) {
            val v = ord[i]
            val zv = zs[v]
            var j = i - 1
            while (j >= 0 && zs[ord[j]] > zv) {
                ord[j + 1] = ord[j]
                j--
            }
            ord[j + 1] = v
        }
    }
}

private fun DrawScope.fillQuad(g: Gfx, fill: Color, stroke: Color, sw: Float, blend: BlendMode) {
    val p = g.path
    p.reset()
    p.moveTo(g.a[0], g.a[1])
    p.lineTo(g.b[0], g.b[1])
    p.lineTo(g.c[0], g.c[1])
    p.lineTo(g.d[0], g.d[1])
    p.close()
    drawPath(p, fill)
    drawPath(p, stroke, style = Stroke(width = sw), blendMode = blend)
}

private fun DrawScope.seg(p: FloatArray, q: FloatArray, color: Color, w: Float, blend: BlendMode = BlendMode.SrcOver) {
    drawLine(color, Offset(p[0], p[1]), Offset(q[0], q[1]), strokeWidth = w, cap = StrokeCap.Round, blendMode = blend)
}

// ═════════════════════════ HERO 1 — LAYER STACK (Architecture) ═════════════════════════
/** 4 lapisan sistem melayang (app → framework → kernel → hardware) dengan paket data naik-turun dan inti berdenyut. */
private fun DrawScope.drawStack(g: Gfx, cam: Cam, t: Float, k: Float, c1: Color, c2: Color, dark: Boolean) {
    val cx = size.width / 2f
    val cy = size.height / 2f + sin(t * 1.6f) * size.minDimension * 0.012f
    val base = size.minDimension * 0.255f * (1f + 0.07f * k)
    val u = 1.dp.toPx()
    val glow = if (dark) BlendMode.Plus else BlendMode.SrcOver
    val hot = if (dark) Color.White else c1
    val gap = 0.60f + 0.10f * sin(t * 0.9f) + 0.22f * k

    for (i in 0 until 4) {
        cam.apply(0f, (i - 1.5f) * gap, 0f)
        g.zs[i] = cam.o[2]
    }
    g.sortByZ(4)

    // rel vertikal + paket data
    val y0 = -1.5f * gap
    val y1 = 1.5f * gap
    for (j in 0 until 4) {
        val sx = if ((j and 1) == 0) -0.78f else 0.78f
        val sz = if ((j and 2) == 0) -0.78f else 0.78f
        cam.project(sx, y0, sz, cx, cy, base, g.a)
        cam.project(sx, y1, sz, cx, cy, base, g.b)
        seg(g.a, g.b, c1.copy(alpha = 0.22f), 1f * u)
        for (m in 0 until 2) {
            var p = frac(t * 0.5f + j * 0.23f + m * 0.5f)
            if ((j and 1) == 1) p = 1f - p
            cam.project(sx, mixF(y0, y1, p), sz, cx, cy, base, g.c)
            val al = sin(p * PI_F)
            val ctr = Offset(g.c[0], g.c[1])
            drawCircle(color = c2.copy(alpha = 0.28f * al), radius = 7f * u * g.c[3], center = ctr, blendMode = glow)
            drawCircle(color = hot.copy(alpha = 0.95f * al), radius = 2.2f * u * g.c[3], center = ctr)
        }
    }

    // lapisan (jauh → dekat)
    for (oi in 0 until 4) {
        val i = g.ord[oi]
        val y = (i - 1.5f) * gap
        val h = 1.08f - 0.08f * i
        val col = lerp(c1, c2, i / 3f)
        cam.project(-h, y, -h, cx, cy, base, g.a)
        cam.project(h, y, -h, cx, cy, base, g.b)
        cam.project(h, y, h, cx, cy, base, g.c)
        cam.project(-h, y, h, cx, cy, base, g.d)
        fillQuad(g, col.copy(alpha = if (dark) 0.16f else 0.13f), col.copy(alpha = 0.85f), 1.4f * u, glow)
        drawCircle(col.copy(alpha = 0.95f), 2.2f * u * g.a[3], Offset(g.a[0], g.a[1]))
        drawCircle(col.copy(alpha = 0.95f), 2.2f * u * g.b[3], Offset(g.b[0], g.b[1]))
        drawCircle(col.copy(alpha = 0.95f), 2.2f * u * g.c[3], Offset(g.c[0], g.c[1]))
        drawCircle(col.copy(alpha = 0.95f), 2.2f * u * g.d[3], Offset(g.d[0], g.d[1]))
        for (q in -1..1) {
            val f = q * 0.5f * h
            cam.project(f, y, -h, cx, cy, base, g.a)
            cam.project(f, y, h, cx, cy, base, g.b)
            seg(g.a, g.b, col.copy(alpha = 0.16f), 0.8f * u)
            cam.project(-h, y, f, cx, cy, base, g.a)
            cam.project(h, y, f, cx, cy, base, g.b)
            seg(g.a, g.b, col.copy(alpha = 0.16f), 0.8f * u)
        }
    }

    // inti berdenyut di poros tengah
    cam.project(0f, 0f, 0f, cx, cy, base, g.a)
    val pulse = 1f + 0.12f * sin(t * 3f) + 0.35f * k
    val r = 30f * u * pulse * g.a[3]
    val ctr = Offset(g.a[0], g.a[1])
    drawCircle(
        brush = Brush.radialGradient(listOf(c2.copy(alpha = 0.55f), c1.copy(alpha = 0.18f), Color.Transparent), center = ctr, radius = r * 2.2f),
        radius = r * 2.2f, center = ctr, blendMode = glow
    )
    drawCircle(hot.copy(alpha = 0.95f), 5f * u * pulse * g.a[3], ctr)

    // cincin orbit + satelit
    val ob = 1.55f
    for (s in 0 until 36) {
        val a = s * TAU / 36f
        cam.project(ob * cos(a), 0.30f * sin(a), ob * sin(a), cx, cy, base, g.b)
        drawCircle(c1.copy(alpha = 0.18f), 1.1f * u * g.b[3], Offset(g.b[0], g.b[1]))
    }
    for (j in 5 downTo 0) {
        val a = t * 1.1f - j * 0.09f
        cam.project(ob * cos(a), 0.30f * sin(a), ob * sin(a), cx, cy, base, g.b)
        val f = 1f - j / 6f
        val p = Offset(g.b[0], g.b[1])
        if (j == 0) {
            drawCircle(c2.copy(alpha = 0.30f), 9f * u * g.b[3], p, blendMode = glow)
            drawCircle(hot, 2.6f * u * g.b[3], p)
        } else {
            drawCircle(c2.copy(alpha = 0.35f * f * f), (0.8f + 2.4f * f) * u * g.b[3], p, blendMode = glow)
        }
    }
}

// ═════════════════════════ HERO 2 — HAZARD OCTAHEDRON (Privileged access) ═════════════════════════
private val OCT_V = floatArrayOf(1f, 0f, 0f, -1f, 0f, 0f, 0f, 1f, 0f, 0f, -1f, 0f, 0f, 0f, 1f, 0f, 0f, -1f)
private val OCT_F = intArrayOf(0, 2, 4, 2, 1, 4, 1, 3, 4, 3, 0, 4, 2, 0, 5, 1, 2, 5, 3, 1, 5, 0, 3, 5)

/** Oktahedron peringatan: berdenyut seperti detak, glitch sesekali, gelombang kejut di lantai, percikan naik. */
private fun DrawScope.drawHazard(g: Gfx, cam: Cam, t: Float, k: Float, c1: Color, c2: Color, dark: Boolean) {
    val cx = size.width / 2f
    val cy = size.height / 2f + sin(t * 1.6f) * size.minDimension * 0.012f
    val base = size.minDimension * 0.27f * (1f + 0.06f * k)
    val u = 1.dp.toPx()
    val glow = if (dark) BlendMode.Plus else BlendMode.SrcOver
    val hot = if (dark) Color.White else c1
    val hb = 1f + 0.08f * max(0f, sin(t * 5.2f)).pow(3) + 0.12f * k
    val gl = if (frac(t * 0.45f) < 0.05f) sin(t * 90f) * 0.06f else 0f

    // gelombang kejut di lantai
    for (w in 0 until 3) {
        val p = frac(t * 0.45f + w / 3f)
        val rr = 0.3f + 1.9f * p
        val fa = 1f - p
        g.path.reset()
        for (s in 0..48) {
            val th = s * TAU / 48f
            cam.project(rr * cos(th), 1.25f, rr * sin(th), cx, cy, base, g.a)
            if (s == 0) g.path.moveTo(g.a[0], g.a[1]) else g.path.lineTo(g.a[0], g.a[1])
        }
        drawPath(g.path, c1.copy(alpha = fa * fa * 0.8f), style = Stroke(width = (1f + 1.8f * fa) * u), blendMode = glow)
    }

    // proyeksi 6 titik sudut
    for (i in 0 until 6) {
        cam.project((OCT_V[i * 3] + gl) * 0.95f * hb, OCT_V[i * 3 + 1] * 0.95f * hb, OCT_V[i * 3 + 2] * 0.95f * hb, cx, cy, base, g.a)
        g.vp[i * 4] = g.a[0]; g.vp[i * 4 + 1] = g.a[1]; g.vp[i * 4 + 2] = g.a[2]; g.vp[i * 4 + 3] = g.a[3]
    }
    for (f in 0 until 8) {
        g.zs[f] = (g.vp[OCT_F[f * 3] * 4 + 2] + g.vp[OCT_F[f * 3 + 1] * 4 + 2] + g.vp[OCT_F[f * 3 + 2] * 4 + 2]) / 3f
    }
    g.sortByZ(8)
    for (oi in 0 until 8) {
        val f = g.ord[oi]
        val i0 = OCT_F[f * 3]; val i1 = OCT_F[f * 3 + 1]; val i2 = OCT_F[f * 3 + 2]
        g.path.reset()
        g.path.moveTo(g.vp[i0 * 4], g.vp[i0 * 4 + 1])
        g.path.lineTo(g.vp[i1 * 4], g.vp[i1 * 4 + 1])
        g.path.lineTo(g.vp[i2 * 4], g.vp[i2 * 4 + 1])
        g.path.close()
        val zn = ((g.zs[f] + 1f) * 0.5f).coerceIn(0f, 1f)
        drawPath(g.path, lerp(c1, c2, zn).copy(alpha = 0.08f + 0.20f * zn))
    }
    for (i in 0 until 6) {
        for (j in i + 1 until 6) {
            if (i / 2 == j / 2) continue
            val zm = ((g.vp[i * 4 + 2] + g.vp[j * 4 + 2]) * 0.5f + 1f) * 0.5f
            drawLine(
                color = c1.copy(alpha = (0.35f + 0.55f * zm).coerceIn(0f, 1f)),
                start = Offset(g.vp[i * 4], g.vp[i * 4 + 1]), end = Offset(g.vp[j * 4], g.vp[j * 4 + 1]),
                strokeWidth = 1.5f * u, cap = StrokeCap.Round, blendMode = glow
            )
        }
    }
    for (i in 0 until 6) {
        drawCircle(hot.copy(alpha = 0.9f), 2.4f * u * g.vp[i * 4 + 3], Offset(g.vp[i * 4], g.vp[i * 4 + 1]))
    }

    // percikan naik
    for (i in 0 until 14) {
        val hs = frac(sin(i * 12.9898f) * 43758.547f)
        val ang = i * 2.39996f
        val rr = 0.25f + 1.35f * hs
        val p = frac(t * 0.4f + hs * 3f)
        cam.project(rr * cos(ang), 1.25f - p * 2.6f, rr * sin(ang), cx, cy, base, g.a)
        drawCircle(c2.copy(alpha = (1f - p) * 0.9f), (1.2f + 1.4f * (1f - p)) * u * g.a[3], Offset(g.a[0], g.a[1]), blendMode = glow)
    }

    // inti peringatan "!"
    cam.project(0f, 0f, 0f, cx, cy, base, g.a)
    val ctr = Offset(g.a[0], g.a[1])
    val r = 22f * u * hb
    drawCircle(
        brush = Brush.radialGradient(listOf(c1.copy(alpha = 0.55f), Color.Transparent), center = ctr, radius = r * 3f),
        radius = r * 3f, center = ctr, blendMode = glow
    )
    drawCircle(c1, r, ctr)
    drawLine(Color.White, Offset(ctr.x, ctr.y - r * 0.5f), Offset(ctr.x, ctr.y + r * 0.1f), strokeWidth = 4.5f * u, cap = StrokeCap.Round)
    drawCircle(Color.White, 2.6f * u, Offset(ctr.x, ctr.y + r * 0.46f))
}

// ═════════════════════════ HERO 3 — VAULT CUBE (Data isolation) ═════════════════════════
private fun bitSign(i: Int, bit: Int): Float = if ((i and bit) == 0) -1f else 1f
private val CUBE_F = intArrayOf(0, 1, 3, 2, 4, 6, 7, 5, 0, 4, 5, 1, 2, 3, 7, 6, 0, 2, 6, 4, 1, 5, 7, 3)

/** Separuh depan / belakang globe kawat (lintang + bujur) yang mengurung brankas. */
private fun DrawScope.drawGlobe(
    g: Gfx, cam: Cam, c1: Color, front: Boolean,
    cx: Float, cy: Float, base: Float, rg: Float, spin: Float
) {
    val u = 1.dp.toPx()
    val n = 32
    for (li in 0 until 4) {
        val phi = (li - 1.5f) * 0.52f
        val rr = rg * cos(phi)
        val yy = rg * sin(phi)
        var px = 0f; var py = 0f; var pz = 0f
        for (s in 0..n) {
            val th = s * TAU / n
            cam.project(rr * cos(th), yy, rr * sin(th), cx, cy, base, g.a)
            if (s > 0) {
                val zm = (pz + g.a[2]) * 0.5f
                if ((zm >= 0f) == front) {
                    val d = ((zm / rg + 1f) * 0.5f).coerceIn(0f, 1f)
                    drawLine(c1.copy(alpha = if (front) 0.12f + 0.30f * d else 0.05f + 0.12f * d), Offset(px, py), Offset(g.a[0], g.a[1]), 0.9f * u)
                }
            }
            px = g.a[0]; py = g.a[1]; pz = g.a[2]
        }
    }
    for (m in 0 until 6) {
        val al = m * PI_F / 6f + spin
        val ca = cos(al)
        val sa = sin(al)
        var px = 0f; var py = 0f; var pz = 0f
        for (s in 0..n) {
            val th = s * TAU / n
            cam.project(rg * cos(th) * ca, rg * sin(th), rg * cos(th) * sa, cx, cy, base, g.a)
            if (s > 0) {
                val zm = (pz + g.a[2]) * 0.5f
                if ((zm >= 0f) == front) {
                    val d = ((zm / rg + 1f) * 0.5f).coerceIn(0f, 1f)
                    drawLine(c1.copy(alpha = if (front) 0.12f + 0.30f * d else 0.05f + 0.12f * d), Offset(px, py), Offset(g.a[0], g.a[1]), 0.9f * u)
                }
            }
            px = g.a[0]; py = g.a[1]; pz = g.a[2]
        }
    }
}

/** Brankas kubus transparan + pemindai vertikal + gembok, dikurung globe kawat dengan satelit pengawal. */
private fun DrawScope.drawVault(g: Gfx, cam: Cam, t: Float, k: Float, c1: Color, c2: Color, dark: Boolean) {
    val cx = size.width / 2f
    val cy = size.height / 2f + sin(t * 1.6f) * size.minDimension * 0.012f
    val base = size.minDimension * 0.25f * (1f + 0.06f * k)
    val u = 1.dp.toPx()
    val glow = if (dark) BlendMode.Plus else BlendMode.SrcOver
    val hot = if (dark) Color.White else c1
    val rg = 1.42f

    drawGlobe(g, cam, c1, false, cx, cy, base, rg, -t * 0.45f)

    // kubus
    val hc = 0.62f * (1f + 0.03f * sin(t * 2f) + 0.10f * k)
    for (i in 0 until 8) {
        cam.project(bitSign(i, 1) * hc, bitSign(i, 2) * hc, bitSign(i, 4) * hc, cx, cy, base, g.a)
        g.vp[i * 4] = g.a[0]; g.vp[i * 4 + 1] = g.a[1]; g.vp[i * 4 + 2] = g.a[2]; g.vp[i * 4 + 3] = g.a[3]
    }
    for (f in 0 until 6) {
        g.zs[f] = (g.vp[CUBE_F[f * 4] * 4 + 2] + g.vp[CUBE_F[f * 4 + 1] * 4 + 2] +
            g.vp[CUBE_F[f * 4 + 2] * 4 + 2] + g.vp[CUBE_F[f * 4 + 3] * 4 + 2]) / 4f
    }
    g.sortByZ(6)
    for (oi in 0 until 6) {
        val f = g.ord[oi]
        g.path.reset()
        for (q in 0 until 4) {
            val vi = CUBE_F[f * 4 + q]
            if (q == 0) g.path.moveTo(g.vp[vi * 4], g.vp[vi * 4 + 1]) else g.path.lineTo(g.vp[vi * 4], g.vp[vi * 4 + 1])
        }
        g.path.close()
        val zn = ((g.zs[f] + 1f) * 0.5f).coerceIn(0f, 1f)
        drawPath(g.path, lerp(c1, c2, zn).copy(alpha = 0.10f + 0.16f * zn))
    }
    for (i in 0 until 8) {
        for (j in i + 1 until 8) {
            if ((i xor j).countOneBits() != 1) continue
            val zm = ((g.vp[i * 4 + 2] + g.vp[j * 4 + 2]) * 0.5f + 1f) * 0.5f
            drawLine(
                color = c1.copy(alpha = (0.40f + 0.50f * zm).coerceIn(0f, 1f)),
                start = Offset(g.vp[i * 4], g.vp[i * 4 + 1]), end = Offset(g.vp[j * 4], g.vp[j * 4 + 1]),
                strokeWidth = 1.5f * u, cap = StrokeCap.Round, blendMode = glow
            )
        }
    }

    // pemindai vertikal
    val ys = 0.72f * sin(t * 1.5f)
    val hs = 0.92f
    cam.project(-hs, ys, -hs, cx, cy, base, g.a)
    cam.project(hs, ys, -hs, cx, cy, base, g.b)
    cam.project(hs, ys, hs, cx, cy, base, g.c)
    cam.project(-hs, ys, hs, cx, cy, base, g.d)
    fillQuad(g, c2.copy(alpha = 0.14f), c2.copy(alpha = 0.85f), 1.6f * u, glow)

    // satelit pengawal
    for (s in 0 until 3) {
        val tilt = 0.6f * s + 0.4f
        for (j in 4 downTo 0) {
            val a = t * (1.3f - 0.25f * s) + s * 2.1f - j * 0.10f
            cam.project(1.62f * cos(a), 1.62f * sin(a) * sin(tilt), 1.62f * sin(a) * cos(tilt), cx, cy, base, g.b)
            val p = Offset(g.b[0], g.b[1])
            if (j == 0) {
                drawCircle(c2.copy(alpha = 0.32f), 8f * u * g.b[3], p, blendMode = glow)
                drawCircle(hot, 2.4f * u * g.b[3], p)
            } else {
                val f = 1f - j / 5f
                drawCircle(c2.copy(alpha = 0.35f * f * f), (0.8f + 2f * f) * u * g.b[3], p, blendMode = glow)
            }
        }
    }

    drawGlobe(g, cam, c1, true, cx, cy, base, rg, -t * 0.45f)

    // gembok di pusat
    cam.project(0f, 0f, 0f, cx, cy, base, g.a)
    val ctr = Offset(g.a[0], g.a[1])
    val r = 21f * u
    drawCircle(
        brush = Brush.radialGradient(listOf(c1.copy(alpha = 0.5f), Color.Transparent), center = ctr, radius = r * 2.8f),
        radius = r * 2.8f, center = ctr, blendMode = glow
    )
    drawCircle(c1, r, ctr)
    drawArc(
        color = Color.White, startAngle = 180f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(ctr.x - 4.5f * u, ctr.y - 10f * u), size = Size(9f * u, 9f * u),
        style = Stroke(width = 2.4f * u, cap = StrokeCap.Round)
    )
    drawRoundRect(Color.White, Offset(ctr.x - 7f * u, ctr.y - 5.5f * u), Size(14f * u, 11f * u), CornerRadius(3f * u))
    drawCircle(c1, 1.6f * u, Offset(ctr.x, ctr.y)) 
}

// ═════════════════════════ KONTEKS & SENSOR ═════════════════════════
internal class Tilt {
    val x = mutableFloatStateOf(0f)
    val y = mutableFloatStateOf(0f)
}

@Stable
internal class HeroCtx(
    val time: FloatState,
    val tilt: Tilt,
    val kick: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    val onKick: () -> Unit
)

@Composable
private fun rememberClock(animScale: Float): FloatState {
    val t = remember { mutableFloatStateOf(1.2f) }
    LaunchedEffect(animScale) {
        if (animScale == 0f) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            t.floatValue += ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
            last = now
        }
    }
    return t
}

@Composable
private fun rememberTilt(enabled: Boolean): Tilt {
    val context = LocalContext.current
    val tilt = remember { Tilt() }
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose {}
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var bx = Float.NaN
        var by = Float.NaN
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val ax = event.values[0]
                val ay = event.values[1]
                if (bx.isNaN()) {
                    bx = ax
                    by = ay
                }
                tilt.x.floatValue += (((ax - bx) * -0.16f).coerceIn(-1f, 1f) - tilt.x.floatValue) * 0.10f
                tilt.y.floatValue += (((ay - by) * 0.16f).coerceIn(-1f, 1f) - tilt.y.floatValue) * 0.10f
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sm != null && sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm?.unregisterListener(listener) }
    }
    return tilt
}

@Composable
internal fun Hero3D(
    kind: Int,
    ctx: HeroCtx,
    offset: () -> Float,
    c1: Color,
    c2: Color,
    dark: Boolean,
    modifier: Modifier
) {
    val cam = remember { Cam() }
    val g = remember { Gfx() }
    Canvas(modifier) {
        val t = ctx.time.floatValue
        val k = ctx.kick.value
        val off = offset()
        val yaw = 0.5f * t * (if (kind == 1) 1.6f else 1f) + off * 1.6f + ctx.tilt.x.floatValue * 0.5f + 2.2f * k + kind * 0.6f
        val pitch = 0.46f + ctx.tilt.y.floatValue * 0.30f + 0.04f * sin(t * 0.7f)
        cam.set(yaw, pitch)

        // bayangan kontak di bawah objek (grounding)
        drawOval(
            brush = Brush.radialGradient(
                listOf(c1.copy(alpha = if (dark) 0.30f else 0.22f), Color.Transparent),
                center = Offset(size.width / 2f, size.height * 0.91f), radius = size.width * 0.34f
            ),
            topLeft = Offset(size.width * 0.14f, size.height * 0.86f),
            size = Size(size.width * 0.72f, size.height * 0.10f)
        )
        when (kind) {
            0 -> drawStack(g, cam, t, k, c1, c2, dark)
            1 -> drawHazard(g, cam, t, k, c1, c2, dark)
            else -> drawVault(g, cam, t, k, c1, c2, dark)
        }
    }
}

// ═════════════════════════ LATAR AURORA + KEDALAMAN ═════════════════════════
@Composable
private fun AuroraBackground(
    c1: Color,
    c2: Color,
    colors: IntroColors,
    time: FloatState,
    pagePos: () -> Float
) {
    val dust = remember {
        val rnd = kotlin.random.Random(11)
        FloatArray(64 * 4) { rnd.nextFloat() }
    }
    Canvas(Modifier.fillMaxSize()) {
        val t = time.floatValue
        val pp = pagePos()
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val dark = colors.dark

        drawRect(Brush.verticalGradient(listOf(colors.bgTop, colors.bgBottom)))

        // 3 blob aurora yang melayang & bergeser mengikuti swipe (lapisan kedalaman berbeda)
        for (i in 0 until 3) {
            val a = t * (0.12f + 0.05f * i) + i * 2.1f
            val bx = w * (0.5f + 0.34f * cos(a)) - pp * w * 0.10f * (i + 1)
            val by = h * (0.26f + 0.18f * sin(a * 1.3f) + 0.20f * i)
            val rad = w * (0.62f + 0.10f * i)
            val col = if (i == 1) c2 else c1
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(col.copy(alpha = if (dark) 0.30f else 0.20f), Color.Transparent),
                    center = Offset(bx, by), radius = rad
                ),
                radius = rad, center = Offset(bx, by)
            )
        }

        // lantai grid perspektif
        val horizon = h * 0.76f
        val span = h - horizon
        val cxg = w / 2f - pp * w * 0.30f
        val colW = w * 0.20f
        val ph = frac(t * 0.22f)
        for (i in 0 until 7) {
            val s = i + 1f - ph
            val q = 1f / (1f + s * 0.5f)
            val y = horizon + span * q
            drawLine(c1.copy(alpha = (0.05f + 0.16f * q) * (if (dark) 1f else 0.7f)), Offset(0f, y), Offset(w, y), strokeWidth = 1f * u)
        }
        for (j in -6..6) {
            drawLine(
                c1.copy(alpha = if (dark) 0.10f else 0.07f),
                Offset(cxg + j * colW * 0.03f, horizon), Offset(cxg + j * colW, h), strokeWidth = 1f * u
            )
        }

        // debu bintang berlapis kedalaman (parallax terhadap swipe)
        for (i in 0 until 64) {
            val d = 0.25f + 0.75f * dust[i * 4 + 2]
            val x = frac(dust[i * 4] - pp * 0.25f * d + t * 0.006f * d)
            val y = frac(dust[i * 4 + 1] - t * 0.015f * d)
            val tw = 0.6f + 0.4f * sin(t * 2f + dust[i * 4 + 3] * 6f)
            drawCircle(
                color = (if (dark) Color.White else c1).copy(alpha = (0.10f + 0.40f * d) * tw),
                radius = (0.6f + 1.4f * d) * u,
                center = Offset(x * w, y * h * 0.78f)
            )
        }
    }
}

// ═════════════════════════ LAYAR UTAMA ═════════════════════════
@Composable
fun IntroScreen(
    onIntroCompleted: () -> Unit,
    onDeclined: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { SettingsPreference.getInstance(context) }
    val liteMode by prefs.liteMode.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val dark = com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme()
    val colors = remember(dark) { IntroColors(dark) }

    val pages = remember { listOf(IntroPage.Intro, IntroPage.Risk, IntroPage.Privacy) }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val currentPage = pages[pagerState.currentPage]

    // 0 = explanation pages, 1 = "Select working mode" (required),
    // 2..5 = optional setup: Support, Profile, Appearance, Backup & Language (all skippable)
    var stage by rememberSaveable { mutableStateOf(0) }
    var selectedModeId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedMode = WorkingMode.fromId(selectedModeId)
    val totalSteps = pages.size + 1 + (SETUP_LAST_STAGE - SETUP_FIRST_STAGE + 1)
    // dot / counter position of the current stage: pages 0..2, mode 3, setup 4..7
    val stepIndex = if (stage == 0) pagerState.currentPage else pages.size + stage - 1

    BackHandler(enabled = stage > 0) { stage -= 1 }

    val finishIntro: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        // Mode first, then the intro flag: the flag is what swaps the screen.
        selectedMode?.let { WorkingModeManager.set(context, it) }
        prefs.setAcceptedTerms(true)
        prefs.setHasCompletedIntro(true)
        onIntroCompleted()
    }

    LaunchedEffect(pagerState.currentPage, stage) {
        if (stage == 0) {
            haptic.performHapticFeedback(
                if (currentPage.isRiskPage) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
            )
        }
    }

    // di tema gelap warna dibuat lebih "neon"
    fun neon(c: Color): Color = if (dark) lerp(c, Color.White, 0.22f) else c
    val appPrimary = MaterialTheme.colorScheme.primary
    val appTertiary = MaterialTheme.colorScheme.tertiary
    val targetA = when (stage) {
        0 -> currentPage.themeColor
        1 -> selectedMode?.let { modeTheme(it).first } ?: ThemeBlue
        2 -> Color(0xFFF59E0B)      // support: coffee amber
        3 -> ThemeBlue              // profile
        4 -> appPrimary             // appearance: follows the theme the user is picking
        else -> ThemeGreen          // backup & language
    }
    val targetB = when (stage) {
        0 -> currentPage.accent2
        1 -> selectedMode?.let { modeTheme(it).second } ?: Color(0xFF22D3EE)
        2 -> Color(0xFFFB7185)
        3 -> Color(0xFF22D3EE)
        4 -> appTertiary
        else -> Color(0xFF2DD4BF)
    }
    val c1 by animateColorAsState(neon(targetA), tween(600, easing = FastOutSlowInEasing), label = "c1")
    val c2 by animateColorAsState(neon(targetB), tween(600, easing = FastOutSlowInEasing), label = "c2")

    val animScale = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }
    val time = rememberClock(animScale)
    val tilt = rememberTilt(animScale != 0f)
    val kick = remember { Animatable(0f) }
    val ctx = remember {
        HeroCtx(time, tilt, kick) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch {
                kick.snapTo(1f)
                kick.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 55f))
            }
        }
    }
    val pagePos: () -> Float = { if (stage >= 1) (pages.size + stage - 1).toFloat() else pagerState.currentPage + pagerState.currentPageOffsetFraction }

    Scaffold(
        containerColor = colors.bgBottom,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues)) {
            if (liteMode) {
                // Versi Lite: tanpa kanvas aurora animasi, cukup gradien polos.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(colors.bgTop, colors.bgBottom)))
                )
            } else {
                AuroraBackground(c1, c2, colors, time, pagePos)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IntroHeader(stepIndex, totalSteps, colors, c1)

                Spacer(Modifier.height(8.dp))

                AnimatedContent(
                    targetState = stage,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally(tween(450)) { it / 3 } + fadeIn(tween(450))) togetherWith
                                (slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(300)))
                        } else {
                            (slideInHorizontally(tween(450)) { -it / 3 } + fadeIn(tween(450))) togetherWith
                                (slideOutHorizontally(tween(300)) { it / 3 } + fadeOut(tween(300)))
                        }
                    },
                    label = "IntroStage"
                ) { st ->
                    if (st == 0) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.weight(1f),
                                userScrollEnabled = true
                            ) { index ->
                                IntroPageContent(pages[index], index, pagerState, ctx, colors, c1, c2)
                            }

                            IntroBottomDock(
                                pages = pages,
                                totalSteps = totalSteps,
                                pagerState = pagerState,
                                c1 = c1,
                                c2 = c2,
                                colors = colors,
                                time = time,
                                onNext = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    scope.launch {
                                        pagerState.animateScrollToPage(
                                            page = pagerState.currentPage + 1,
                                            animationSpec = spring(stiffness = Spring.StiffnessLow)
                                        )
                                    }
                                },
                                onAgree = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    prefs.setAcceptedTerms(true)
                                    stage = 1
                                },
                                onDecline = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDeclined()
                                }
                            )
                        }
                    } else if (st == 1) {
                        ModeSelectStage(
                            ctx = ctx,
                            colors = colors,
                            c1 = c1,
                            c2 = c2,
                            time = time,
                            totalSteps = totalSteps,
                            currentStep = pages.size,
                            selected = selectedMode,
                            onSelect = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedModeId = it.id
                            },
                            onBack = { stage = 0 },
                            onStart = {
                                val mode = selectedMode ?: return@ModeSelectStage
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                // Save the mode now; the intro only completes at the end of the (skippable) setup.
                                WorkingModeManager.set(context, mode)
                                prefs.setAcceptedTerms(true)
                                stage = SETUP_FIRST_STAGE
                            }
                        )
                    } else {
                        IntroSetupStage(
                            stage = st,
                            ctx = ctx,
                            colors = colors,
                            c1 = c1,
                            c2 = c2,
                            time = time,
                            totalSteps = totalSteps,
                            currentStep = pages.size + st - 1,
                            selectedMode = selectedMode,
                            onContinue = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (st >= SETUP_LAST_STAGE) finishIntro() else stage = st + 1
                            },
                            onBack = { stage = st - 1 },
                            onSkipAll = finishIntro
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroHeader(current: Int, count: Int, colors: IntroColors, c1: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .glass(colors, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(7.dp).background(c1, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.intro_app_label),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = colors.text,
                letterSpacing = 1.5.sp
            )
        }
        Text(
            text = "${(current + 1).toString().padStart(2, '0')} / ${count.toString().padStart(2, '0')}",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = colors.muted,
            letterSpacing = 1.sp
        )
    }
}

// ═════════════════════════ HALAMAN ═════════════════════════
@Composable
private fun IntroPageContent(
    page: IntroPage,
    index: Int,
    pagerState: PagerState,
    ctx: HeroCtx,
    colors: IntroColors,
    c1: Color,
    c2: Color
) {
    val active = pagerState.currentPage == index
    val enter = animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "enter"
    )
    val offset: () -> Float = { (pagerState.currentPage - index) + pagerState.currentPageOffsetFraction }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // halaman berputar ala kubus 3D
                val off = offset()
                val a = abs(off)
                cameraDistance = 16f * density
                transformOrigin = TransformOrigin(if (off > 0f) 1f else 0f, 0.5f)
                rotationY = -off * 32f
                alpha = 1f - (a * 1.3f).coerceIn(0f, 1f)
                val s = 1f - 0.10f * a.coerceIn(0f, 1f)
                scaleX = s
                scaleY = s
            }
    ) {
        val heroSize = (maxHeight * 0.40f).coerceIn(190.dp, 310.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Hero3D(
                kind = index,
                ctx = ctx,
                offset = offset,
                c1 = c1,
                c2 = c2,
                dark = colors.dark,
                modifier = Modifier
                    .size(heroSize)
                    .pointerInput(Unit) { detectTapGestures(onTap = { ctx.onKick() }) }
            )

            // label langkah
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .graphicsLayer {
                        alpha = smooth(enter.value * 1.6f)
                        translationX = offset() * 70f
                    }
            ) {
                Box(Modifier.size(6.dp).background(c1, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = page.stepLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = c1,
                    letterSpacing = 1.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(page.titleRes),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = colors.text,
                textAlign = TextAlign.Center,
                lineHeight = 36.sp,
                modifier = Modifier.graphicsLayer {
                    val p = smooth(enter.value * 1.4f)
                    alpha = p
                    translationY = (1f - p) * 26.dp.toPx()
                    translationX = offset() * 110f
                }
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(page.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .graphicsLayer {
                        val p = smooth((enter.value - 0.08f) * 1.4f)
                        alpha = p
                        translationY = (1f - p) * 26.dp.toPx()
                        translationX = offset() * 190f
                    }
            )

            Spacer(Modifier.height(14.dp))

            page.insights.forEachIndexed { i, res ->
                InsightCard(
                    icon = page.insightIcons.getOrNull(i) ?: Icons.Rounded.Code,
                    text = stringResource(res),
                    c1 = c1,
                    colors = colors,
                    enter = enter,
                    order = i,
                    offset = offset
                )
                Spacer(Modifier.height(8.dp))
            }

            if (index == 0) {
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.graphicsLayer { alpha = smooth((enter.value - 0.5f) * 2f) * 0.9f }
                ) {
                    Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = colors.muted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.intro_x_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.muted,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun InsightCard(
    icon: ImageVector,
    text: String,
    c1: Color,
    colors: IntroColors,
    enter: androidx.compose.runtime.State<Float>,
    order: Int,
    offset: () -> Float
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                // masuk dengan rotasi 3D berurutan (stagger)
                val p = smooth((enter.value - 0.15f - order * 0.12f) * 1.8f)
                cameraDistance = 14f * density
                transformOrigin = TransformOrigin(0.5f, 0f)
                alpha = p
                translationY = (1f - p) * 30.dp.toPx()
                rotationX = (1f - p) * -40f
                translationX = offset() * (230f + order * 40f)
            }
            .glass(colors, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(c1.copy(alpha = 0.16f))
        ) {
            Icon(icon, contentDescription = null, tint = c1, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = colors.text,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

// ═════════════════════════ DOCK BAWAH ═════════════════════════
@Composable
internal fun PagerPills(position: () -> Float, count: Int, c1: Color, muted: Color) {
    Canvas(Modifier.width(160.dp).height(10.dp)) {
        val pos = position()
        val minW = 8.dp.toPx()
        val maxW = 34.dp.toPx()
        val gap = 8.dp.toPx()
        var total = gap * (count - 1)
        for (i in 0 until count) total += mixF(minW, maxW, (1f - abs(pos - i)).coerceIn(0f, 1f))
        var x = (size.width - total) / 2f
        for (i in 0 until count) {
            val near = (1f - abs(pos - i)).coerceIn(0f, 1f)
            val w = mixF(minW, maxW, near)
            drawRoundRect(
                color = lerp(muted.copy(alpha = 0.28f), c1, near),
                topLeft = Offset(x, (size.height - minW) / 2f),
                size = Size(w, minW),
                cornerRadius = CornerRadius(minW / 2f)
            )
            x += w + gap
        }
    }
}

@Composable
internal fun GlowButton(
    text: String,
    showArrow: Boolean,
    c1: Color,
    c2: Color,
    time: FloatState,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "press"
    )
    val shape = RoundedCornerShape(22.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .graphicsLayer {
                alpha = if (enabled) 1f else 0.45f
                cameraDistance = 14f * density
                val s = 1f - 0.04f * press
                scaleX = s
                scaleY = s
                rotationX = 10f * press
            }
            .shadow(18.dp, shape, ambientColor = c1.copy(alpha = 0.3f), spotColor = c1.copy(alpha = 0.7f))
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(c1, c2)))
            .drawWithContent {
                drawContent()
                val w = size.width
                val x = -w * 0.4f + w * 1.8f * frac(time.floatValue * 0.35f)
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.28f), Color.Transparent),
                        start = Offset(x, 0f), end = Offset(x + w * 0.35f, size.height)
                    )
                )
                drawRoundRect(
                    Color.White.copy(alpha = 0.28f),
                    cornerRadius = CornerRadius(22.dp.toPx()),
                    style = Stroke(1.dp.toPx())
                )
            }
            .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            if (showArrow) {
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Rounded.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun IntroBottomDock(
    pages: List<IntroPage>,
    totalSteps: Int,
    pagerState: PagerState,
    c1: Color,
    c2: Color,
    colors: IntroColors,
    time: FloatState,
    onNext: () -> Unit,
    onAgree: () -> Unit,
    onDecline: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        PagerPills({ pagerState.currentPage + pagerState.currentPageOffsetFraction }, totalSteps, c1, colors.muted)
        Spacer(Modifier.height(18.dp))

        if (pagerState.currentPage < pages.size - 1) {
            GlowButton(stringResource(R.string.intro_btn_next), true, c1, c2, time, onClick = onNext)
            Spacer(Modifier.height(10.dp))
        } else {
            GlowButton(stringResource(R.string.intro_btn_agree), false, c1, c2, time, onClick = onAgree)

            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = onDecline,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ThemeRed),
                border = BorderStroke(1.5.dp, ThemeRed.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(stringResource(R.string.intro_btn_decline), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.intro_footer_legal),
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

// ═════════════════════════ LANGKAH AKHIR — SELECT WORKING MODE ═════════════════════════
/** Primary + secondary accent used for each working mode (intro cards, picker dialog, locked screen). */
internal fun modeTheme(mode: WorkingMode): Pair<Color, Color> = when (mode) {
    WorkingMode.BASIC -> ThemeGreen to Color(0xFF2DD4BF)
    WorkingMode.ADB -> ThemeBlue to Color(0xFF22D3EE)
    WorkingMode.ROOT -> ThemeRed to Color(0xFFF97316)
}

internal fun modeIcon(mode: WorkingMode): ImageVector = when (mode) {
    WorkingMode.BASIC -> Icons.Rounded.Visibility
    WorkingMode.ADB -> Icons.Rounded.Adb
    WorkingMode.ROOT -> Icons.Rounded.AdminPanelSettings
}

@StringRes
internal fun modeDescRes(mode: WorkingMode): Int = when (mode) {
    WorkingMode.BASIC -> R.string.mode_basic_desc
    WorkingMode.ADB -> R.string.mode_adb_desc
    WorkingMode.ROOT -> R.string.mode_root_desc
}

private val BadgeNoRoot = Color(0xFF10B981)
private val BadgeExperience = Color(0xFFF97316)

/** "Noroot" for Basic + ADB, "Experience" for ROOT. */
@Composable
internal fun ModeBadge(mode: WorkingMode, dark: Boolean) {
    val base = if (mode.hasRoot) BadgeExperience else BadgeNoRoot
    val tint = if (dark) lerp(base, Color.White, 0.18f) else base
    Text(
        text = stringResource(if (mode.hasRoot) R.string.mode_badge_experience else R.string.mode_badge_noroot),
        color = tint,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.3.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(base.copy(alpha = if (dark) 0.20f else 0.16f))
            .border(1.dp, base.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    )
}

@Composable
private fun ModeSelectStage(
    ctx: HeroCtx,
    colors: IntroColors,
    c1: Color,
    c2: Color,
    time: FloatState,
    totalSteps: Int,
    currentStep: Int,
    selected: WorkingMode?,
    onSelect: (WorkingMode) -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val enter = animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "modeEnter"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val heroSize = (maxHeight * 0.26f).coerceIn(130.dp, 200.dp)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Hero3D(
                    kind = 0,
                    ctx = ctx,
                    offset = { 0f },
                    c1 = c1,
                    c2 = c2,
                    dark = colors.dark,
                    modifier = Modifier
                        .size(heroSize)
                        .pointerInput(Unit) { detectTapGestures(onTap = { ctx.onKick() }) }
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.graphicsLayer { alpha = smooth(enter.value * 1.6f) }
                ) {
                    Box(Modifier.size(6.dp).background(c1, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.intro_mode_step),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = c1,
                        letterSpacing = 1.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.intro_mode_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = colors.text,
                    textAlign = TextAlign.Center,
                    lineHeight = 36.sp,
                    modifier = Modifier.graphicsLayer {
                        val p = smooth(enter.value * 1.4f)
                        alpha = p
                        translationY = (1f - p) * 26.dp.toPx()
                    }
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.intro_mode_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .graphicsLayer {
                            val p = smooth((enter.value - 0.08f) * 1.4f)
                            alpha = p
                            translationY = (1f - p) * 26.dp.toPx()
                        }
                )

                Spacer(Modifier.height(18.dp))

                WorkingMode.entries.forEachIndexed { i, mode ->
                    ModeCard(
                        mode = mode,
                        selected = selected == mode,
                        colors = colors,
                        enter = enter,
                        order = i,
                        onClick = { onSelect(mode) }
                    )
                    Spacer(Modifier.height(12.dp))
                }
                LiteModeCard(colors = colors, c1 = c1)
                Spacer(Modifier.height(4.dp))
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().animateContentSize()
        ) {
            PagerPills({ currentStep.toFloat() }, totalSteps, c1, colors.muted)
            Spacer(Modifier.height(12.dp))

            if (selected == null) {
                Text(
                    text = stringResource(R.string.intro_mode_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.muted,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(8.dp))
            }

            GlowButton(
                text = stringResource(R.string.intro_btn_continue),
                showArrow = true,
                c1 = c1,
                c2 = c2,
                time = time,
                enabled = selected != null,
                onClick = onStart
            )

            TextButton(onClick = onBack) {
                Text(
                    text = stringResource(R.string.intro_mode_back),
                    color = colors.muted,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/**
 * Toggle "Versi Lite" di intro. Aktif = aplikasi lebih ringan (blur / glass / efek berat
 * dimatikan, tampilan simpel). Mati = Pro, tampilan normal penuh. Bisa diganti lagi di Pengaturan.
 */
@Composable
private fun LiteModeCard(colors: IntroColors, c1: Color) {
    val context = LocalContext.current
    val prefs = remember { SettingsPreference.getInstance(context) }
    val lite by prefs.liteMode.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(24.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .glass(colors, shape)
            .clickable(role = Role.Switch) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                prefs.setLiteMode(!lite)
            }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.EnergySavingsLeaf,
            contentDescription = null,
            tint = c1,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.lite_mode_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.text
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.lite_mode_desc),
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(if (lite) R.string.lite_mode_state_on else R.string.lite_mode_state_off),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = c1,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.width(12.dp))
        androidx.compose.material3.Switch(
            checked = lite,
            onCheckedChange = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                prefs.setLiteMode(it)
            }
        )
    }
}

@Composable
private fun ModeCard(
    mode: WorkingMode,
    selected: Boolean,
    colors: IntroColors,
    enter: androidx.compose.runtime.State<Float>,
    order: Int,
    onClick: () -> Unit
) {
    val theme = modeTheme(mode)
    val accent = if (colors.dark) lerp(theme.first, Color.White, 0.2f) else theme.first
    val shape = RoundedCornerShape(24.dp)

    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "modePress"
    )
    val sel by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "modeSel"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val p = smooth((enter.value - 0.22f - order * 0.14f) * 1.8f)
                cameraDistance = 14f * density
                transformOrigin = TransformOrigin(0.5f, 0f)
                alpha = p
                translationY = (1f - p) * 36.dp.toPx()
                rotationX = (1f - p) * -40f
                val sc = 1f - 0.03f * press + 0.012f * sel
                scaleX = sc
                scaleY = sc
            }
            .clip(shape)
            .background(Brush.linearGradient(listOf(colors.glassA, colors.glassB)))
            .background(accent.copy(alpha = 0.10f * sel))
            .border(
                width = (1f + 0.8f * sel).dp,
                brush = Brush.linearGradient(
                    listOf(
                        lerp(colors.strokeA, accent, sel),
                        lerp(colors.strokeB, accent.copy(alpha = 0.35f), sel)
                    )
                ),
                shape = shape
            )
            .clickable(interactionSource = src, indication = null, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.30f), theme.second.copy(alpha = 0.14f))))
        ) {
            Icon(modeIcon(mode), contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        when (mode) {
                            WorkingMode.BASIC -> R.string.mode_basic_name
                            WorkingMode.ADB -> R.string.mode_adb_name
                            WorkingMode.ROOT -> R.string.mode_root_name
                        }
                    ),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.text,
                    maxLines = 1
                )
                Spacer(Modifier.width(8.dp))
                ModeBadge(mode, colors.dark)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(modeDescRes(mode)),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = colors.muted
            )
        }

        Spacer(Modifier.width(10.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = sel))
                .border(1.5.dp, lerp(colors.muted.copy(alpha = 0.35f), accent, sel), CircleShape)
        ) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colors.muted,
                modifier = Modifier.size(18.dp).graphicsLayer { alpha = 1f - sel }
            )
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp).graphicsLayer { alpha = sel }
            )
        }
    }
}
