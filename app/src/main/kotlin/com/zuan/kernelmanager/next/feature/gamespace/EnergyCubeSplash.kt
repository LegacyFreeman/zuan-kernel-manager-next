/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 *
 * Game Space - splash "Energy Cube" saat game dimulai (5 detik).
 *
 *  0.00 s  layar digelapkan, kilatan garis cahaya horizontal membelah layar (ignition)
 *  0.2 s   kubus 3D dirakit dari garis tepi, berputar cepat lalu melambat; partikel tersedot ke pusat
 *  0.6 s   inti menyala, dua cincin gyroscope mengorbit, petir energi menyambar cincin lantai, grid lantai bergerak
 *  2.1 s   grup kubus bergeser ke kiri; judul "ENERGY CUBE" muncul dengan efek acak-huruf + glitch kromatik,
 *          baris status menyala satu per satu
 *  sepanjang 5 s  strip chevron kiri-kanan (gaya hub) terisi sesuai progres + cincin reaktor kanan-bawah
 *  ~4.5 s  ledakan energi penutup, lalu hilang
 *
 * Teks status dibuat dari [RootBoost.Report] (hasil kerja sungguhan), bukan hiasan.
 * Seluruh efek visual digambar di Canvas (tanpa aset gambar). Splash hanya sebuah jendela overlay:
 * selesai sendiri (atau saat sesi game berakhir) lalu hilang.
 */
package com.zuan.kernelmanager.next.feature.gamespace

import android.content.Context
import android.content.Intent
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.feature.overlay.BaseOverlayService
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Jembatan antara pekerjaan optimasi (thread latar) dan splash (UI). */
object SplashBus {
    var report by mutableStateOf<RootBoost.Report?>(null)
    private val lastShown = HashMap<String, Long>()

    /** Cegah splash berulang bila pemain hanya keluar-masuk game sebentar. */
    @Synchronized
    fun shouldShow(pkg: String, cooldownMs: Long = 90_000L): Boolean {
        val now = System.currentTimeMillis()
        val last = lastShown[pkg] ?: 0L
        if (now - last < cooldownMs) return false
        lastShown[pkg] = now
        return true
    }
}

class EnergyCubeSplashService : BaseOverlayService() {

    companion object {
        const val DURATION_MS = 5000
        @Volatile var isRunning = false
            private set

        fun start(c: Context) {
            SplashBus.report = null
            runCatching { ContextCompat.startForegroundService(c, Intent(c, EnergyCubeSplashService::class.java)) }
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, EnergyCubeSplashService::class.java))
        }
    }

    override val notificationId = 138
    override fun onRunningChanged(running: Boolean) { isRunning = running }

    override fun buildLayoutParams() = overlayParams(
        WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT
    )

    @Composable
    override fun OverlayContent() {
        val mode = CpuMode.fromOrdinal(GameSpacePrefs.cpuMode(this))
        EnergyCubeSplash(mode = mode, report = SplashBus.report, onDone = { stopSelf() })
    }
}

// =============================================================================================
// UI
// =============================================================================================

private val Pink = Color(0xFFFF3D8B)
private val Violet = Color(0xFF8A4DFF)
private val Blue = Color(0xFF3D7BFF)
private val Hot = Color(0xFFFF2A45)     // merah inti (aksen hub)
private val Cyan = Color(0xFF45E3FF)    // cahaya tepi dingin sebagai kontras
private val Ink = Color(0xFF04050A)

private const val TAU = 6.2831855f
private const val PI_F = 3.1415927f
private const val CAM = 4.6f            // jarak kamera proyeksi perspektif (satuan kubus)
private const val SCRAMBLE = "01<>/\\#%&=+*?"

private fun ease(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }
private fun easeOut(x: Float): Float { val u = 1f - x.coerceIn(0f, 1f); return 1f - u * u * u }
private fun seg(t: Float, a: Float, b: Float): Float = ((t - a) / (b - a)).coerceIn(0f, 1f)

/** Segitiga 0..1..0 pada rentang [a, b]. */
private fun bump(t: Float, a: Float, b: Float): Float {
    val m = (a + b) / 2f
    return (1f - abs((t - m) / ((b - a) / 2f))).coerceIn(0f, 1f)
}

/** Acak deterministik 0..1 dari bilangan bulat (tanpa state, aman dipanggil tiap frame). */
private fun hash(n: Int): Float {
    var x = n * 374761393 + 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0xFFFFFF) / 16777215f
}

@Composable
internal fun EnergyCubeSplash(mode: CpuMode, report: RootBoost.Report?, onDone: () -> Unit) {
    val clock = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        clock.animateTo(1f, tween(EnergyCubeSplashService.DURATION_MS, easing = LinearEasing))
        onDone()
    }
    val t = clock.value
    val sec = t * EnergyCubeSplashService.DURATION_MS / 1000f

    val bgA = ease(seg(t, 0f, 0.10f)) * (1f - ease(seg(t, 0.95f, 1f)))
    val fadeOut = 1f - ease(seg(t, 0.95f, 1f))
    val appear = ease(seg(t, 0.04f, 0.22f))
    val glow = ease(seg(t, 0.12f, 0.34f))
    val shift = ease(seg(t, 0.42f, 0.58f))
    val progress = ease(seg(t, 0.06f, 0.88f))
    val burst = ease(bump(t, 0.885f, 0.965f))
    val tick = (sec * 24f).toInt()

    val ctx = androidx.compose.ui.platform.LocalContext.current
    val lines = remember(report, mode) { splashLines(ctx, mode, report) }
    val pool = remember(report, mode) { splashMessages(ctx, report, mode) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Ink.copy(alpha = 0.97f * bgA))) {
        val w = maxWidth
        val h = maxHeight
        val rDp = min(w.value, h.value).dp * 0.15f
        val cx = w * (0.5f - 0.17f * shift)

        // ---- seluruh adegan: latar, strip, kubus 3D, cincin, petir, partikel ----
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = fadeOut }) {
            drawScene(Offset(cx.toPx(), size.height * 0.5f), rDp.toPx(), t, sec, appear, glow, progress, burst)
        }

        // ---- teks kanan kubus ----
        val titleP = ease(seg(t, 0.46f, 0.60f))
        val reveal = ease(seg(t, 0.47f, 0.70f))
        val textX = w * 0.5f + rDp * 0.55f + 28.dp
        val barW = (w - textX - 40.dp).coerceIn(120.dp, 320.dp)
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = textX - 18.dp)
                .graphicsLayer { alpha = titleP * fadeOut; translationX = (1f - titleP) * 70f }
                .drawBehind {
                    // pelat jajar genjang miring + garis aksen kiri (gaya hub)
                    val sk = 14.dp.toPx()
                    val plate = Path().apply {
                        moveTo(sk, 0f); lineTo(size.width, 0f); lineTo(size.width - sk, size.height); lineTo(0f, size.height); close()
                    }
                    drawPath(plate, Brush.horizontalGradient(listOf(Hot.copy(alpha = 0.22f), Hot.copy(alpha = 0.05f), Color.Transparent)))
                    drawLine(Hot.copy(alpha = 0.35f), Offset(sk, 0f), Offset(0f, size.height), strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = 0.9f), Offset(sk, 0f), Offset(0f, size.height), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(Hot.copy(alpha = 0.5f), Offset(sk, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
                }
                .padding(start = 22.dp, top = 12.dp, bottom = 14.dp, end = 28.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GlitchTitle(stringResource(R.string.gsx_sp_title), reveal, tick)

            // garis di bawah judul: menyapu dari kiri, kepala bercahaya
            Canvas(Modifier.width(barW).height(10.dp)) {
                val p = ease(seg(t, 0.52f, 0.68f))
                val y = size.height / 2f
                drawLine(Color.White.copy(alpha = 0.14f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                val x = size.width * p
                if (p > 0f) {
                    val br = Brush.horizontalGradient(listOf(Hot, Pink, Violet, Blue), startX = 0f, endX = size.width)
                    drawLine(br, Offset(0f, y), Offset(x, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    drawCircle(Pink.copy(alpha = 0.5f * (1f - p * 0.6f)), 6.dp.toPx(), Offset(x, y), blendMode = BlendMode.Plus)
                    drawCircle(Color.White, 2.dp.toPx(), Offset(x, y))
                }
                // tanda kecil di sepanjang garis
                var tx = 0f
                while (tx <= size.width) {
                    drawLine(Color.White.copy(alpha = 0.16f), Offset(tx, y - 3.dp.toPx()), Offset(tx, y + 3.dp.toPx()), strokeWidth = 1.dp.toPx())
                    tx += 24.dp.toPx()
                }
            }

            lines.forEachIndexed { i, line ->
                val st = 0.56f + i * 0.07f
                val p = ease(seg(t, st, st + 0.08f))
                val flash = 1f - ease(seg(t, st + 0.02f, st + 0.11f))
                Row(
                    Modifier.graphicsLayer { alpha = p; translationX = (1f - p) * 36f },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Canvas(Modifier.size(10.dp)) {
                        val m = size.width / 2f
                        val d = Path().apply { moveTo(m, 0f); lineTo(size.width, m); lineTo(m, size.height); lineTo(0f, m); close() }
                        drawCircle(Hot.copy(alpha = 0.45f + 0.4f * flash), m * 1.7f, center, blendMode = BlendMode.Plus)
                        drawPath(d, lerp(Hot, Color.White, flash))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        line, color = Color.White.copy(alpha = 0.86f), fontSize = 14.sp, fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // ---- kanan bawah: pesan acak + cincin reaktor progres ----
        val pct = (progress * 100f).roundToInt().coerceIn(0, 100)
        val dockP = ease(seg(t, 0.07f, 0.20f))
        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 64.dp, bottom = 22.dp)
                .graphicsLayer { alpha = dockP * fadeOut; translationY = (1f - dockP) * 30f }
                .drawBehind {
                    val sk = 10.dp.toPx()
                    val plate = Path().apply {
                        moveTo(sk, 0f); lineTo(size.width, 0f); lineTo(size.width - sk, size.height); lineTo(0f, size.height); close()
                    }
                    drawPath(plate, Brush.horizontalGradient(listOf(Color.Transparent, Ink.copy(alpha = 0.62f)), startX = 0f, endX = size.width * 0.6f))
                    drawLine(Hot.copy(alpha = 0.6f), Offset(sk, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
                }
                .padding(start = 26.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val idx = if (pool.isEmpty()) 0 else (progress * (pool.size - 0.001f)).toInt().coerceIn(0, pool.lastIndex)
            AnimatedContent(
                targetState = if (pool.isEmpty()) "" else pool[idx],
                transitionSpec = {
                    (slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(220))) togetherWith
                        (slideOutVertically(tween(160)) { -it / 2 } + fadeOut(tween(140)))
                },
                label = "splashMsg"
            ) { msg ->
                Text(msg, color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp, textAlign = TextAlign.End, maxLines = 2)
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) { drawReactorRing(progress, sec, burst) }
                Text("$pct%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

/** Judul: huruf teracak lalu "mengunci" satu per satu, ghost kromatik tipis, glitch sesaat berkala. */
@Composable
private fun GlitchTitle(text: String, reveal: Float, tick: Int) {
    val n = text.length
    val settled = (reveal * (n + 1)).toInt()
    val shown = buildString {
        for (i in 0 until n) {
            val ch = text[i]
            if (ch == ' ' || i < settled) append(ch)
            else append(SCRAMBLE[(hash(tick * 31 + i * 7) * SCRAMBLE.length).toInt().coerceAtMost(SCRAMBLE.length - 1)])
        }
    }
    val glitch = reveal >= 1f && hash((tick / 4) * 13 + 3) > 0.90f
    val off = if (glitch) 7f else 1.6f
    Box(Modifier.graphicsLayer { translationX = if (glitch) (hash(tick) - 0.5f) * 10.dp.toPx() else 0f }) {
        Text(
            shown, maxLines = 1,
            style = TextStyle(color = Cyan.copy(alpha = if (glitch) 0.8f else 0.42f), fontSize = 34.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, letterSpacing = 2.sp),
            modifier = Modifier.graphicsLayer { translationX = -off.dp.toPx() }
        )
        Text(
            shown, maxLines = 1,
            style = TextStyle(color = Hot.copy(alpha = if (glitch) 0.85f else 0.50f), fontSize = 34.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, letterSpacing = 2.sp),
            modifier = Modifier.graphicsLayer { translationX = off.dp.toPx() }
        )
        Text(
            shown, maxLines = 1,
            style = TextStyle(
                brush = Brush.horizontalGradient(listOf(Color.White, Pink, Violet, Blue)),
                fontSize = 34.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, letterSpacing = 2.sp
            )
        )
    }
}

private fun splashLines(c: Context, mode: CpuMode, r: RootBoost.Report?): List<String> {
    val out = mutableListOf<String>()
    out += c.getString(R.string.gsx_sp_mode_on, c.getString(mode.labelRes))
    if (r != null) {
        if (RootBoost.Step.RAM in r.steps) out += c.getString(R.string.gsx_sp_l_clean)
        if (RootBoost.Step.PRIORITY in r.steps) out += c.getString(R.string.gsx_sp_l_prio)
        // "Performance enhancement on" hanya bila benar-benar ada yang diterapkan (root, atau Game Mode diterima sistem).
        if (mode != CpuMode.BALANCE && (r.root || RootBoost.Step.GAMEMODE in r.steps)) out += c.getString(R.string.gsx_sp_l_perf)
        if (RootBoost.Step.GAMEMODE in r.steps) out += c.getString(R.string.gsx_sp_l_gm)
        if (RootBoost.Step.BGLIMIT in r.steps) out += c.getString(R.string.gsx_sp_l_bg)
        if (RootBoost.Step.SCHED in r.steps) out += c.getString(R.string.gsx_sp_l_sched)
    } else {
        out += c.getString(R.string.gsx_sp_l_prep)
    }
    return out.take(4)
}

/** Pesan kanan-bawah: urutannya diacak, tetapi isinya hanya hal yang benar-benar terjadi; penutup selalu "ready". */
private fun splashMessages(c: Context, r: RootBoost.Report?, mode: CpuMode): List<String> {
    val msgs = mutableListOf<String>()
    if (r != null) {
        if (RootBoost.Step.RAM in r.steps) {
            msgs += if (r.freedMb > 0) c.getString(R.string.gsx_sp_m_mem_mb, r.freedMb) else c.getString(R.string.gsx_sp_m_mem)
            msgs += c.getString(R.string.gsx_sp_m_bg)
        }
        if (RootBoost.Step.PRIORITY in r.steps) msgs += c.getString(R.string.gsx_sp_m_prio)
        if (RootBoost.Step.SCHED in r.steps) msgs += c.getString(R.string.gsx_sp_m_sched)
        if (RootBoost.Step.IO in r.steps) msgs += c.getString(R.string.gsx_sp_m_io)
        if (RootBoost.Step.SWAP in r.steps) msgs += c.getString(R.string.gsx_sp_m_swap)
        if (RootBoost.Step.GAMEMODE in r.steps) msgs += c.getString(R.string.gsx_sp_m_gm)
        if (RootBoost.Step.FIXED in r.steps) msgs += c.getString(R.string.gsx_sp_m_fixed)
        if (RootBoost.Step.BGLIMIT in r.steps) msgs += c.getString(R.string.gsx_sp_m_bglimit)
        if (RootBoost.Step.BATSAVER in r.steps) msgs += c.getString(R.string.gsx_sp_m_bat)
    }
    msgs += c.getString(R.string.gsx_sp_m_mode, c.getString(mode.labelRes))
    return msgs.shuffled() + c.getString(R.string.gsx_sp_m_ready)
}

// =============================================================================================
// gambar
// =============================================================================================

/** Rotasi 3D (Y -> X -> Z) + proyeksi perspektif. Sin/cos dihitung sekali per frame. */
private class Rot(ay: Float, ax: Float, az: Float) {
    private val cy = cos(ay); private val sy = sin(ay)
    private val cx = cos(ax); private val sx = sin(ax)
    private val cz = cos(az); private val sz = sin(az)

    /** Memutar (x,y,z) lalu memproyeksikan ke layar di sekitar [c] berskala [s]. out = [sx, sy, zRotasi]. */
    fun project(x: Float, y: Float, z: Float, c: Offset, s: Float, out: FloatArray) {
        val x1 = x * cy + z * sy
        val z1 = -x * sy + z * cy
        val y2 = y * cx - z1 * sx
        val z2 = y * sx + z1 * cx
        val x3 = x1 * cz - y2 * sz
        val y3 = x1 * sz + y2 * cz
        val k = CAM / (CAM - z2)
        out[0] = c.x + x3 * k * s
        out[1] = c.y + y3 * k * s
        out[2] = z2
    }

    /** Hanya memutar vektor arah (untuk normal bidang). */
    fun dir(x: Float, y: Float, z: Float, out: FloatArray) {
        val x1 = x * cy + z * sy
        val z1 = -x * sy + z * cy
        val y2 = y * cx - z1 * sx
        val z2 = y * sx + z1 * cx
        out[0] = x1 * cz - y2 * sz
        out[1] = x1 * sz + y2 * cz
        out[2] = z2
    }
}

private val CUBE_FACES = arrayOf(
    intArrayOf(4, 5, 7, 6), intArrayOf(0, 2, 3, 1), intArrayOf(1, 3, 7, 5),
    intArrayOf(0, 4, 6, 2), intArrayOf(2, 6, 7, 3), intArrayOf(0, 1, 5, 4)
)
private val CUBE_NORMALS = arrayOf(
    floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 0f, -1f), floatArrayOf(1f, 0f, 0f),
    floatArrayOf(-1f, 0f, 0f), floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, -1f, 0f)
)
private val CUBE_EDGES = arrayOf(
    intArrayOf(0, 1), intArrayOf(1, 3), intArrayOf(3, 2), intArrayOf(2, 0),
    intArrayOf(4, 5), intArrayOf(5, 7), intArrayOf(7, 6), intArrayOf(6, 4),
    intArrayOf(0, 4), intArrayOf(1, 5), intArrayOf(3, 7), intArrayOf(2, 6)
)

private fun DrawScope.drawScene(
    c: Offset, r: Float, t: Float, sec: Float,
    appear: Float, glow: Float, progress: Float, burst: Float
) {
    val ringC = Offset(c.x, c.y + r * 0.82f)
    val cc = Offset(c.x, c.y - r * 0.12f + sin(sec * 2.2f) * r * 0.04f)

    // putaran: sangat cepat saat muncul lalu melambat, ditendang lagi saat ledakan penutup
    val spinIn = 1f - easeOut(seg(t, 0.02f, 0.36f))
    val ay = sec * 0.85f + 7f * spinIn + 5f * ease(seg(t, 0.88f, 0.97f))
    val ax = 0.55f + 0.12f * sin(sec * 1.3f)
    val az = 0.10f * sin(sec * 0.9f)
    val s = r * 0.50f * (0.55f + 0.45f * easeOut(appear)) * (1f + 0.035f * sin(sec * 6f) + 0.35f * burst)

    drawBackdrop(c, r, sec, glow, burst)
    drawSideStrips(appear, progress, burst)
    drawHudRails(t, sec)
    drawFloorRings(ringC, r, t, sec, appear, glow, burst)

    // halo besar di belakang kubus
    drawCircle(
        Brush.radialGradient(listOf(Pink.copy(alpha = 0.50f * glow + 0.35f * burst), Hot.copy(alpha = 0.16f * glow), Color.Transparent), center = cc, radius = r * 2.1f),
        radius = r * 2.1f, center = cc, blendMode = BlendMode.Plus
    )

    // gyroscope: separuh belakang dulu, separuh depan setelah kubus
    val gA = glow * (0.9f + 0.3f * burst)
    val gk = easeOut(appear)
    val g1 = Rot(sec * 1.1f, 1.2f, 0.3f)
    val g2 = Rot(-sec * 0.8f + 1.5f, 0.45f, -0.5f + sec * 0.2f)
    drawOrbit3(cc, s, 2.35f * gk, g1, Cyan, 1.5f * density, gA, sec * 2.6f, 0)
    drawOrbit3(cc, s, 2.85f * gk, g2, Pink, 1.5f * density, gA, -sec * 2.0f + 2f, 0)

    // kubus kaca + inti
    val build = seg(t, 0.04f, 0.26f)
    drawCube3(cc, s, Rot(ay, ax, az), build, 0.34f * glow, Pink, Color(0xFF4A1030), Pink, 1.7f * density, 1f)
    drawCube3(cc, s * 0.42f, Rot(-ay * 1.6f + 1f, -ax * 1.2f, -az), seg(t, 0.14f, 0.34f), 0.92f * glow, Color.White, Hot, Color.White, 1.3f * density, glow)
    drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f * glow), Pink.copy(alpha = 0.45f * glow), Color.Transparent), center = cc, radius = r * 0.62f * (1f + 0.12f * sin(sec * 7f) + 0.8f * burst)),
        radius = r * 0.62f * (1f + 0.12f * sin(sec * 7f) + 0.8f * burst), center = cc, blendMode = BlendMode.Plus
    )

    drawArcs(cc, ringC, r, sec, glow, burst)
    drawOrbit3(cc, s, 2.35f * gk, g1, Cyan, 1.5f * density, gA, sec * 2.6f, 1)
    drawOrbit3(cc, s, 2.85f * gk, g2, Pink, 1.5f * density, gA, -sec * 2.0f + 2f, 1)

    drawEmbers(ringC, r, sec, appear, glow, burst)
    drawStreak(cc, glow, burst)

    // kilatan penutup + kilatan pembuka
    if (burst > 0.01f) drawRect(Color.White.copy(alpha = 0.20f * burst), blendMode = BlendMode.Plus)
    val flare = ease(seg(t, 0f, 0.07f)) * (1f - ease(seg(t, 0.07f, 0.16f)))
    if (flare > 0.01f) {
        val br = Brush.horizontalGradient(0f to Color.Transparent, 0.5f to Color.White, 1f to Color.Transparent, startX = 0f, endX = size.width)
        drawLine(br, Offset(0f, c.y), Offset(size.width, c.y), strokeWidth = (1.5f + 14f * flare) * density, alpha = flare, blendMode = BlendMode.Plus)
    }
}

/** Vignette merah, sinar cahaya berputar, dan lantai grid perspektif yang bergerak maju. */
private fun DrawScope.drawBackdrop(c: Offset, r: Float, sec: Float, glow: Float, burst: Float) {
    val dp = density
    val w = size.width
    val h = size.height

    drawRect(
        Brush.radialGradient(
            listOf(Hot.copy(alpha = (0.20f * glow + 0.30f * burst).coerceAtMost(0.5f)), Color.Transparent),
            center = c, radius = w * 0.62f
        )
    )

    // sinar
    val rayBrush = Brush.radialGradient(listOf(Pink.copy(alpha = 0.6f), Color.Transparent), center = c, radius = w * 0.55f)
    val rays = 18
    for (i in 0 until rays) {
        val h1 = hash(i * 13 + 5)
        val h2 = hash(i * 7 + 91)
        val ang = i * TAU / rays + sec * (0.10f + 0.10f * h1)
        val half = 0.012f + 0.03f * h2
        val len = w * 0.6f
        val p = Path().apply {
            moveTo(c.x, c.y)
            lineTo(c.x + cos(ang - half) * len, c.y + sin(ang - half) * len)
            lineTo(c.x + cos(ang + half) * len, c.y + sin(ang + half) * len)
            close()
        }
        drawPath(p, rayBrush, alpha = (0.10f + 0.12f * h1) * glow * (1f + 1.5f * burst), blendMode = BlendMode.Plus)
    }

    // lantai grid
    val horizon = c.y + r * 0.35f
    val gA = 0.32f * glow
    if (gA > 0.01f) {
        val fade = Brush.verticalGradient(listOf(Color.Transparent, Hot.copy(alpha = 0.9f)), startY = horizon, endY = h)
        val n = 16
        for (i in -n..n) {
            val a = gA * (1f - abs(i) / (n + 1f))
            drawLine(fade, Offset(c.x + i * r * 0.07f, horizon), Offset(c.x + i * w * 0.07f, h), strokeWidth = 1f * dp, alpha = a)
        }
        val rows = 10
        val scroll = (sec * 0.45f) % 1f
        for (k in 0 until rows) {
            val z = (k + scroll) / rows
            val y = horizon + (h - horizon) * z * z
            drawLine(Hot.copy(alpha = gA * z), Offset(0f, y), Offset(w, y), strokeWidth = 1f * dp)
        }
    }
}

/** Dua strip chevron bersegmen kiri-kanan (gaya hub) yang terisi dari bawah sesuai progres. */
private fun DrawScope.drawSideStrips(appear: Float, progress: Float, burst: Float) {
    val dp = density
    val w = size.width
    val h = size.height
    val slide = (1f - ease(appear)) * 90f * dp
    val n = 14
    val pitch = h / n
    for (side in 0..1) {
        val left = side == 0
        fun xAt(y: Float): Float {
            val u = y / h
            val shape = if (u < 0.30f) 0.25f + 0.75f * (u / 0.30f) else 1f - 0.5f * ((u - 0.30f) / 0.70f)
            val inset = (14f + 40f * shape) * dp
            return if (left) inset - slide else w - inset + slide
        }
        fun halfW(y: Float): Float {
            val u = y / h
            return (if (u < 0.30f) 6.5f + 1.5f * (u / 0.30f) else 8f - 3.5f * ((u - 0.30f) / 0.70f)) * dp
        }
        for (i in 0 until n) {
            val ya = i * pitch + 2.5f * dp
            val yb = (i + 1) * pitch - 2.5f * dp
            val lit = (progress * n - (n - 1 - i)).coerceIn(0f, 1f)
            val path = Path().apply {
                moveTo(xAt(ya) - halfW(ya), ya); lineTo(xAt(ya) + halfW(ya), ya)
                lineTo(xAt(yb) + halfW(yb), yb); lineTo(xAt(yb) - halfW(yb), yb); close()
            }
            val onCol = lerp(lerp(Color(0xFF7A1630), Color(0xFFFF3350), yb / h), Color.White, burst * 0.7f)
            if (lit > 0.3f) drawPath(path, Hot.copy(alpha = 0.28f * lit), style = Stroke(5f * dp), blendMode = BlendMode.Plus)
            drawPath(path, lerp(Color(0xFF2A2A2E), onCol, lit), alpha = 0.55f + 0.45f * lit)
        }
    }
}

/** Dua rel horizontal tipis di tepi atas & bawah: tumbuh dari tengah, ada komet cahaya yang berlari. */
private fun DrawScope.drawHudRails(t: Float, sec: Float) {
    val dp = density
    val a = ease(seg(t, 0.02f, 0.16f))
    if (a <= 0f) return
    val x0 = 72f * dp
    val x1 = size.width - 72f * dp
    val mid = size.width / 2f
    val half = (x1 - x0) / 2f * a
    for (side in 0..1) {
        val y = if (side == 0) 12f * dp else size.height - 12f * dp
        drawLine(Color.White.copy(alpha = 0.16f), Offset(mid - half, y), Offset(mid + half, y), strokeWidth = 1f * dp)
        var x = mid - half
        var k = 0
        while (x <= mid + half) {
            val tall = k % 5 == 0
            drawLine(Color.White.copy(alpha = if (tall) 0.26f else 0.12f), Offset(x, y - (if (tall) 3f else 1.5f) * dp), Offset(x, y + (if (tall) 3f else 1.5f) * dp), strokeWidth = 1f * dp)
            x += 14f * dp
            k++
        }
        val cx = x0 + (((sec * 0.35f) + side * 0.5f) % 1f) * (x1 - x0)
        val br = Brush.horizontalGradient(listOf(Color.Transparent, Hot.copy(alpha = 0.95f), Color.Transparent), startX = cx - 70f * dp, endX = cx + 70f * dp)
        drawLine(br, Offset(cx - 70f * dp, y), Offset(cx + 70f * dp, y), strokeWidth = 2f * dp, alpha = a)
    }
}

/** Cincin elips di lantai: pucat -> menyala, cincin komet, cincin putus-putus, tick, gelombang kejut. */
private fun DrawScope.drawFloorRings(ringC: Offset, r: Float, t: Float, sec: Float, appear: Float, glow: Float, burst: Float) {
    val squash = 0.30f
    val a = appear
    val spin = sec * 40f

    scale(1f, 0.42f, pivot = ringC) {
        drawCircle(
            Brush.radialGradient(
                listOf(lerp(Color(0xFF6A6F86), Hot, glow).copy(alpha = (0.42f + 0.40f * burst) * a), Violet.copy(alpha = 0.10f * a), Color.Transparent),
                center = ringC, radius = r * 2.8f
            ),
            radius = r * 2.8f, center = ringC, blendMode = BlendMode.Plus
        )
    }

    val pale = Color(0xFFB9BDD0)
    val main = Brush.sweepGradient(listOf(lerp(pale, Hot, glow), lerp(pale, Pink, glow), lerp(pale, Violet, glow), lerp(pale, Hot, glow)), center = ringC)
    val comet = Brush.sweepGradient(
        0f to Color.Transparent, 0.62f to Pink.copy(alpha = 0f), 0.90f to Pink, 0.99f to Color.White, 1f to Color.Transparent,
        center = ringC
    )
    val cold = Brush.sweepGradient(listOf(Cyan, Blue, Cyan), center = ringC)
    val hotRing = Brush.sweepGradient(listOf(Hot, Pink, Hot), center = ringC)

    fun ring(sr: Float, width: Float, alpha: Float, rotDeg: Float, brush: Brush, effect: PathEffect? = null, bloom: Boolean = false) {
        val rr = r * sr * (0.6f + 0.4f * a)
        scale(1f, squash, pivot = ringC) {
            rotate(rotDeg, pivot = ringC) {
                if (bloom) {
                    drawCircle(brush, rr, ringC, alpha = alpha * 0.16f, style = Stroke(width * 4.5f), blendMode = BlendMode.Plus)
                    drawCircle(brush, rr, ringC, alpha = alpha * 0.34f, style = Stroke(width * 2.2f), blendMode = BlendMode.Plus)
                }
                drawCircle(brush, rr, ringC, alpha = alpha, style = Stroke(width, pathEffect = effect))
            }
        }
    }
    ring(2.28f, r * 0.018f, 0.55f * a, -spin * 0.35f, cold, PathEffect.dashPathEffect(floatArrayOf(r * 0.05f, r * 0.09f)))
    ring(1.95f, r * 0.030f, 0.45f * a, -spin * 0.6f, main)
    ring(1.95f, r * 0.045f, a, -spin * 1.9f, comet, bloom = true)
    ring(1.55f, r * 0.075f, a, spin, main, bloom = true)
    ring(1.55f, r * 0.060f, a, spin * 2.4f, comet, bloom = true)
    ring(1.12f, r * 0.050f, 0.85f * glow, -spin * 1.3f, hotRing, PathEffect.dashPathEffect(floatArrayOf(r * 0.22f, r * 0.12f)))

    // tick di cincin luar
    scale(1f, squash, pivot = ringC) {
        rotate(spin * 0.3f, pivot = ringC) {
            val n = 72
            for (i in 0 until n) {
                val ang = i * TAU / n
                val long = i % 6 == 0
                val r1 = r * 2.40f
                val r2 = r * (if (long) 2.58f else 2.50f)
                drawLine(
                    Color.White.copy(alpha = (if (long) 0.5f else 0.25f) * a),
                    Offset(ringC.x + cos(ang) * r1, ringC.y + sin(ang) * r1),
                    Offset(ringC.x + cos(ang) * r2, ringC.y + sin(ang) * r2),
                    strokeWidth = 1.5f
                )
            }
        }
    }

    // gelombang kejut di lantai: saat menyala dan saat ledakan penutup
    fun shock(p: Float, maxR: Float, color: Color) {
        if (p <= 0f || p >= 1f) return
        val rr = r * maxR * easeOut(p)
        scale(1f, squash, pivot = ringC) {
            drawCircle(color.copy(alpha = 0.9f * (1f - p)), rr, ringC, style = Stroke(r * 0.09f * (1f - p) + 1f), blendMode = BlendMode.Plus)
        }
    }
    shock(seg(t, 0.10f, 0.34f), 3.4f, Pink)
    shock(seg(t, 0.885f, 0.98f), 4.6f, Color.White)
}

/** Cincin orbit 3D mengelilingi kubus. part: 0 = separuh belakang, 1 = separuh depan; manik + ekor komet. */
private fun DrawScope.drawOrbit3(c: Offset, s: Float, rho: Float, rot: Rot, color: Color, w: Float, alpha: Float, head: Float, part: Int) {
    if (alpha <= 0.01f || rho <= 0f) return
    val o = FloatArray(3)
    val n = 90
    var lx = 0f; var ly = 0f; var lz = 0f
    for (j in 0..n) {
        val a = j * TAU / n
        rot.project(cos(a) * rho, sin(a) * rho, 0f, c, s, o)
        if (j > 0) {
            val front = (o[2] + lz) > 0f
            if (front == (part == 1)) {
                drawLine(
                    color.copy(alpha = alpha * (if (front) 0.95f else 0.30f)),
                    Offset(lx, ly), Offset(o[0], o[1]),
                    strokeWidth = w * (if (front) 1f else 0.7f), cap = StrokeCap.Round
                )
            }
        }
        lx = o[0]; ly = o[1]; lz = o[2]
    }
    val bead = lerp(color, Color.White, 0.6f)
    for (j in 0..12) {
        val a = head - j * 0.075f
        rot.project(cos(a) * rho, sin(a) * rho, 0f, c, s, o)
        if ((o[2] > 0f) != (part == 1)) continue
        val k = 1f - j / 13f
        drawCircle(bead.copy(alpha = alpha * k), w * (1.2f + 2.2f * k), Offset(o[0], o[1]), blendMode = BlendMode.Plus)
    }
}

/** Kubus kaca 3D: bidang translusen terurut kedalaman + tepi menyala yang "digambar" bertahap. */
private fun DrawScope.drawCube3(
    c: Offset, s: Float, rot: Rot, build: Float, fillA: Float,
    lit: Color, shade: Color, edge: Color, edgeW: Float, edgeA: Float
) {
    if (build <= 0f) return
    val px = FloatArray(8); val py = FloatArray(8); val pz = FloatArray(8)
    val o = FloatArray(3)
    for (i in 0 until 8) {
        rot.project(
            if ((i and 1) != 0) 1f else -1f,
            if ((i and 2) != 0) 1f else -1f,
            if ((i and 4) != 0) 1f else -1f,
            c, s, o
        )
        px[i] = o[0]; py[i] = o[1]; pz[i] = o[2]
    }

    if (fillA > 0.01f) {
        val nrm = FloatArray(3)
        val order = (0 until 6).sortedBy { f ->
            val q = CUBE_FACES[f]
            pz[q[0]] + pz[q[1]] + pz[q[2]] + pz[q[3]]
        }
        for (f in order) {
            val q = CUBE_FACES[f]
            val nv = CUBE_NORMALS[f]
            rot.dir(nv[0], nv[1], nv[2], nrm)
            val lightDot = (-nrm[0] * 0.35f - nrm[1] * 0.55f + nrm[2] * 0.75f).coerceIn(0f, 1f)
            val path = Path().apply {
                moveTo(px[q[0]], py[q[0]]); lineTo(px[q[1]], py[q[1]]); lineTo(px[q[2]], py[q[2]]); lineTo(px[q[3]], py[q[3]]); close()
            }
            val a = fillA * (if (nrm[2] > 0f) 0.55f + 0.45f * lightDot else 0.22f)
            drawPath(path, lerp(shade, lit, lightDot), alpha = a)
        }
    }

    val coreEdge = lerp(edge, Color.White, 0.75f)
    for (e in 0 until 12) {
        val ed = CUBE_EDGES[e]
        val p = ease(seg(build, e / 12f * 0.55f, e / 12f * 0.55f + 0.45f))
        if (p <= 0f) continue
        val a = Offset(px[ed[0]], py[ed[0]])
        val b = Offset(a.x + (px[ed[1]] - a.x) * p, a.y + (py[ed[1]] - a.y) * p)
        val front = if ((pz[ed[0]] + pz[ed[1]]) > 0f) 1f else 0.45f
        val k = edgeA * front
        drawLine(edge.copy(alpha = 0.16f * k), a, b, strokeWidth = edgeW * 6f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
        drawLine(edge.copy(alpha = 0.40f * k), a, b, strokeWidth = edgeW * 2.4f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
        drawLine(coreEdge.copy(alpha = 0.95f * k), a, b, strokeWidth = edgeW, cap = StrokeCap.Round)
    }
    for (i in 0 until 8) {
        if (build < 0.5f) break
        val k = edgeA * (if (pz[i] > 0f) 1f else 0.45f)
        drawCircle(Color.White.copy(alpha = 0.9f * k), edgeW * 1.5f, Offset(px[i], py[i]), blendMode = BlendMode.Plus)
    }
}

/** Petir energi: dari kubus ke cincin lantai, berubah ~15x/detik, berkedip, menggila saat ledakan. */
private fun DrawScope.drawArcs(cc: Offset, ringC: Offset, r: Float, sec: Float, glow: Float, burst: Float) {
    if (glow < 0.4f) return
    val frame = (sec * 15f).toInt()
    val power = (glow * (0.6f + 0.6f * burst)).coerceAtMost(1f)
    for (k in 0 until 4) {
        val sd = frame * 17 + k * 101
        if (burst < 0.2f && hash(sd) < 0.5f) continue
        val ang = hash(sd + 1) * TAU
        val a = Offset(cc.x, cc.y + r * 0.2f)
        val b = Offset(ringC.x + cos(ang) * r * 1.55f, ringC.y + sin(ang) * r * 1.55f * 0.30f)
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val nx = -dy / len
        val ny = dx / len
        val path = Path().apply {
            moveTo(a.x, a.y)
            val segs = 8
            for (j in 1 until segs) {
                val u = j / segs.toFloat()
                val jit = (hash(sd + 10 + j) - 0.5f) * r * 0.55f * sin(u * PI_F)
                lineTo(a.x + dx * u + nx * jit, a.y + dy * u + ny * jit)
            }
            lineTo(b.x, b.y)
        }
        drawPath(path, Pink.copy(alpha = 0.30f * power), style = Stroke(r * 0.06f, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
        drawPath(path, Color.White.copy(alpha = 0.95f * power), style = Stroke(r * 0.018f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(Color.White.copy(alpha = 0.8f * power), r * 0.05f, b, blendMode = BlendMode.Plus)
    }
}

/** Percikan: tersedot ke tengah saat muncul, lalu naik berputar dari cincin lantai; menyebar saat ledakan. */
private fun DrawScope.drawEmbers(ringC: Offset, r: Float, sec: Float, appear: Float, glow: Float, burst: Float) {
    val gather = 1f + 2.4f * (1f - appear)
    val n = 64
    for (i in 0 until n) {
        val h1 = hash(i * 3 + 1)
        val h2 = hash(i * 3 + 2)
        val h3 = hash(i * 3 + 3)
        val cyc = (sec * (0.18f + 0.22f * h1) + h2) % 1f
        val ang = h3 * TAU + sec * (0.5f + h1) * 0.8f * (if (i % 2 == 0) 1f else -1f)
        val orbit = r * (1.2f + 1.1f * h2) * gather * (1f - 0.25f * cyc) * (1f + 1.8f * burst)
        val x = ringC.x + cos(ang) * orbit
        val y = ringC.y + sin(ang) * orbit * 0.30f - cyc * r * (1.6f + 1.2f * h1) * (1f + burst)
        val a = sin(cyc * PI_F) * glow.coerceAtLeast(0.25f * appear)
        val col = when {
            h1 < 0.33f -> Pink
            h1 < 0.66f -> Color.White
            h1 < 0.85f -> Hot
            else -> Cyan
        }
        drawCircle(col.copy(alpha = 0.9f * a), (0.8f + 1.8f * h3) * density * (1f + burst), Offset(x, y), blendMode = BlendMode.Plus)
    }
}

/** Garis lensa anamorfik horizontal yang melintasi inti kubus. */
private fun DrawScope.drawStreak(cc: Offset, glow: Float, burst: Float) {
    val a = (0.55f * glow + 0.4f * burst).coerceAtMost(1f)
    if (a <= 0.01f) return
    val len = size.width * (0.28f + 0.5f * burst)
    val br = Brush.horizontalGradient(0f to Color.Transparent, 0.5f to Pink.copy(alpha = 0.9f), 1f to Color.Transparent, startX = cc.x - len, endX = cc.x + len)
    drawLine(br, Offset(cc.x - len, cc.y), Offset(cc.x + len, cc.y), strokeWidth = (2f + 5f * burst) * density, alpha = a, blendMode = BlendMode.Plus)
    drawLine(br, Offset(cc.x - len, cc.y), Offset(cc.x + len, cc.y), strokeWidth = 12f * density, alpha = a * 0.18f, blendMode = BlendMode.Plus)
}

/** Cincin reaktor progres: 48 tick, tick menyala = progres, kepala putih, cincin dalam putus-putus berputar. */
private fun DrawScope.drawReactorRing(progress: Float, sec: Float, burst: Float) {
    val ro = size.minDimension / 2f - 3.dp.toPx()
    val ticks = 48
    val lit = progress * ticks
    for (i in 0 until ticks) {
        val ang = -PI_F / 2f + i * TAU / ticks
        val on = (lit - i).coerceIn(0f, 1f)
        val head = lit >= i && lit < i + 1f && progress < 1f
        val len = (if (head) 10.dp else 7.dp).toPx()
        val p0 = Offset(center.x + cos(ang) * (ro - len), center.y + sin(ang) * (ro - len))
        val p1 = Offset(center.x + cos(ang) * ro, center.y + sin(ang) * ro)
        val onCol = lerp(lerp(Hot, Pink, i / ticks.toFloat()), Color.White, burst)
        val col = when {
            head -> Color.White
            on > 0f -> onCol
            else -> Color.White.copy(alpha = 0.14f)
        }
        if (on > 0f) drawLine(Hot.copy(alpha = 0.30f), p0, p1, strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round, blendMode = BlendMode.Plus)
        drawLine(col, p0, p1, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
    rotate(sec * 40f) {
        drawCircle(
            Cyan.copy(alpha = 0.35f), ro - 14.dp.toPx(), center,
            style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx())))
        )
    }
}
