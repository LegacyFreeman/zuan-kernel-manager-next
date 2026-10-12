/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 *
 * Game Space - "Energy Cube" hub: overlay layar penuh (landscape), mengikuti gaya RedMagic Game Space:
 *  - dua panel hitam di kiri & kanan, batasnya berupa pita segmen berbentuk chevron "<  >"
 *    (abu-abu di atas -> merah menyala di bawah), bagian tengah = game tetap terlihat
 *  - gauge CPU (kiri) & GPU (kanan) di dalam panel: angka, cincin elips berlapis, label, pill mode
 *  - meter baji kecerahan (kiri) & volume (kanan), bisa digeser
 *  - kolom kiri : toggle list/grid + kartu fitur, kolom kanan: grid tombol cepat 2 kolom
 *  - atas       : home / edit / cube, judul + status di tengah, pintasan game + tutup di kanan
 *  - bawah      : bar info (jam, baterai, jaringan) dan toast
 *
 * Seluruh koordinat desain memakai ruang referensi 1600x723 (hasil ukur dari screenshot RedMagic).
 * Panel kiri menempel ke tepi kiri, panel kanan ke tepi kanan (cermin), jadi tetap rapi di rasio layar berbeda.
 */
package com.zuan.kernelmanager.next.feature.gamespace

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Brightness7
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sin
import kotlin.math.roundToInt

internal data class HubTile(val icon: ImageVector, val label: String, val active: Boolean, val onClick: () -> Unit)
internal data class HubCard(val icon: ImageVector, val label: String, val active: Boolean, val onClick: () -> Unit)
internal data class HubShortcut(val label: String, val icon: ImageBitmap?, val onClick: () -> Unit)

internal data class HubModel(
    val accent: Color,
    val title: String,
    val cpuGhz: Float,
    val gpuMhz: Float,
    val gpuRaw: Float = gpuMhz,
    val gpuUnit: String = "MHz",
    /** 0..1 - posisi frekuensi CPU terhadap maksimum (menggerakkan pita kiri). */
    val cpuLevel: Float,
    /** 0..1 - posisi frekuensi/load GPU terhadap maksimum (menggerakkan pita kanan). */
    val gpuLevel: Float,
    /** Naik tiap mode CPU diganti -> memicu lonjakan animasi. */
    val modeKey: Int,
    val cpuMode: String,
    val gpuMode: String,
    val currentCpuMode: CpuMode = CpuMode.BALANCE,
    val onCpuMode: () -> Unit,
    val onGpuMode: (() -> Unit)? = null,
    val onSelectCpuMode: ((CpuMode) -> Unit)? = null,
    val onSelectGpuMetric: ((Int) -> Unit)? = null,
    val statusLines: List<String>,
    val netText: String,
    val clock: String,
    val battery: Int,
    val sessionHours: Float,
    val tempC: Float = 0f,
    /** 0..1 */
    val brightness: Float,
    /** 0..1 */
    val volume: Float,
    val listMode: Boolean,
    val onListMode: (Boolean) -> Unit,
    val cards: List<HubCard>,
    val tiles: List<HubTile>,
    val shortcuts: List<HubShortcut>,
    val toast: String,
    val onHome: () -> Unit,
    val onEdit: () -> Unit,
    val onCube: () -> Unit,
    val onClose: () -> Unit,
    /** (nilai 0..1, selesai) */
    val onBrightness: (Float, Boolean) -> Unit,
    val onVolume: (Float, Boolean) -> Unit,
    val onInteract: () -> Unit
)

// ---------------------------------------------------------------------------------------------
// konstanta desain (ruang referensi 1600 x 723)
// ---------------------------------------------------------------------------------------------

private const val DH = 723f
private const val GAUGE_X = 363f       // pusat gauge dari tepi panel (pusat pill "Rise" & label GHz/CPU di screenshot RedMagic 10S Pro = 362,6)
private const val METER_X = 351f       // pusat meter baji kecerahan/volume (RedMagic: sisi batang di x = 304,8, lebar 92)
private const val METER_ICON_X = 314f  // pusat ikon matahari / speaker di bawah meter (RedMagic = 313,7)
private const val GAUGE_Y = 247f       // pusat cincin
private const val STRIP_X0 = 439.57f   // pusat pita pada y = 0 (jarak dari tepi layar). Fit kaki atas RedMagic: x = 439,57 + 0,5362*y (rms 0,23)
private const val HOUSE_OUT = 10.5f   // jarak garis luar (sisi game) dari tepi blok. Garis ini TERPISAH dari panel UI, dengan celah gelap di antaranya
private const val PICK_TOP = 432f     // tepi atas daftar dropdown
private const val STRIP_YB = 229.1f     // ketinggian titik temu kedua kaki (ujung chevron runcing = perpotongan garis). Sudut runcing RedMagic: (562,4 ; 229,1)
private const val STRIP_M1 = 0.5362f    // kemiringan kaki atas  (dx/dy)
private const val STRIP_M2 = -0.4973f   // kemiringan kaki bawah (dx/dy). Fit kaki bawah RedMagic: x = 676,36 - 0,4973*y (rms 0,29)
private const val STRIP_SMOOTH = 34f    // setengah panjang busur tekukan (satuan desain). RedMagic: ujung chevron MEMBULAT mulus, bukan runcing.
                                        // Makin besar = makin bulat. Rentang aman 20..40; di atas ~60 kaki chevron ikut melengkung.

/** Status animasi per-frame. Dibaca hanya di fase gambar, jadi tidak memicu recomposition tiap frame. */
private class HubAnim {
    var phaseL by mutableFloatStateOf(0f)
    var phaseR by mutableFloatStateOf(0f)
    var kickL by mutableFloatStateOf(0f)
    var kickR by mutableFloatStateOf(0f)
    var clock by mutableFloatStateOf(0f)
}

/** Panel meluncur dari tepi; dist dalam satuan dp. Dibaca di layer sehingga tidak recompose. */
private fun Modifier.slideIn(p: State<Float>, dir: Int, dist: Float): Modifier = this.graphicsLayer {
    val v = p.value
    translationX = dir * (1f - v) * dist * density
    alpha = (v * 1.8f).coerceIn(0f, 1f)
}

/**
 * Level yang ditampilkan: saat masuk pita menyapu penuh dari bawah ke atas lalu turun ke level asli
 * (saat keluar kebalikannya). [f] = progres isian 0..1.
 */
private fun introLevel(f: Float, level: Float): Float =
    if (f < 0.55f) f / 0.55f else 1f + (level - 1f) * ((f - 0.55f) / 0.45f)

private val ChipGrey = Color(0xFF3C3C3F)
private val ChipGreyLight = Color(0xFF58585D)
private val PanelBlack = Color(0xFA040405)

/** Pusat pita (sisi kiri) pada ketinggian y, dengan tekukan yang dibulatkan. */
private fun stripX(y: Float): Float {
    val w = STRIP_SMOOTH
    val y1 = STRIP_YB - w
    val y2 = STRIP_YB + w
    val x1 = STRIP_X0 + STRIP_M1 * y1
    return when {
        y <= y1 -> STRIP_X0 + STRIP_M1 * y
        y >= y2 -> x1 + w * (STRIP_M1 + STRIP_M2) + STRIP_M2 * (y - y2)
        else -> { val t = y - y1; x1 + STRIP_M1 * t + (STRIP_M2 - STRIP_M1) * t * t / (4f * w) }
    }
}

/** Setengah lebar blok pita (horizontal): lebar di atas / sekitar tekukan, menyempit ke kaki bawah (ukuran RedMagic). */
private fun stripHalf(y: Float): Float = 20.5f - 5.5f * (y / DH)

/** Celah gelap antara tepi panel UI dan blok: agak lebar di atas, merapat ke kaki bawah (ukuran RedMagic). */
private fun houseIn(y: Float): Float = 10.5f - 8f * ((y - 320f) / 220f).coerceIn(0f, 1f)

/** Tepi panel hitam (UI), jarak dari tepi layar. Sudutnya ikut membulat mengikuti [stripX]. */
private fun edgeIn(y: Float): Float = stripX(y) - stripHalf(y) - houseIn(y)

/** Garis luar rumah pita (sisi game), terpisah dari panel UI. */
private fun edgeOut(y: Float): Float = stripX(y) + stripHalf(y) + HOUSE_OUT

/** Kotak yang dipusatkan di (x, yc desain) dengan ukuran desain w x h. */
private fun Modifier.boxAt(x: Dp, yc: Float, w: Float, h: Float, u: Float): Modifier =
    this.offset(x - (w * u / 2f).dp, ((yc - h / 2f) * u).dp).size((w * u).dp, (h * u).dp)

@Composable
internal fun AnimatedVisibilityScope.EnergyCubeHub(m: HubModel) {
    // Warna mengikuti mode CPU dan berpindah halus (hijau / kuning / merah).
    val accent by animateColorAsState(m.accent, tween(450), label = "hubAccent")
    val deep = lerp(accent, Color.Black, 0.6f)
    val hot = lerp(accent, Color.White, 0.3f)
    val latest by rememberUpdatedState(m)

    // Progres masuk/keluar dari transisi AnimatedVisibility: panel kiri dari kiri, panel kanan dari kanan.
    val slideP = transition.animateFloat(
        transitionSpec = {
            if (targetState == EnterExitState.Visible) tween(620, easing = CubicBezierEasing(0.18f, 0.9f, 0.22f, 1f))
            else tween(460, easing = FastOutLinearInEasing)
        }, label = "hubSlide"
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    // Progres isian pita (bawah -> atas), sedikit tertinggal dari slide.
    val fillP = transition.animateFloat(
        transitionSpec = {
            if (targetState == EnterExitState.Visible) tween(1000, delayMillis = 180, easing = FastOutSlowInEasing)
            else tween(420, easing = FastOutLinearInEasing)
        }, label = "hubFill"
    ) { if (it == EnterExitState.Visible) 1f else 0f }

    // Level frekuensi dihaluskan dengan pegas, sehingga naik-turunnya terlihat mengalir.
    val cpuLvl = animateFloatAsState(m.cpuLevel.coerceIn(0f, 1f), spring(dampingRatio = 0.72f, stiffness = 90f), label = "cpuLvl")
    val gpuLvl = animateFloatAsState(m.gpuLevel.coerceIn(0f, 1f), spring(dampingRatio = 0.72f, stiffness = 90f), label = "gpuLvl")

    // Lonjakan saat mode diganti: kiri dulu, kanan menyusul (bergantian), lalu tenang kembali.
    val kickL = remember { Animatable(0f) }
    val kickR = remember { Animatable(0f) }
    LaunchedEffect(m.modeKey) {
        launch { kickL.snapTo(1f); kickL.animateTo(0f, tween(1500, easing = FastOutSlowInEasing)) }
        launch { delay(170); kickR.snapTo(1f); kickR.animateTo(0f, tween(1500, easing = FastOutSlowInEasing)) }
    }

    // Loop per-frame: fase putar tiap gauge (makin cepat saat frekuensi tinggi / saat lonjakan).
    val anim = remember { HubAnim() }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                val dt = if (last == 0L) 0.016f else ((t - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                last = t
                val kl = kickL.value
                val kr = kickR.value
                anim.phaseL = (anim.phaseL + dt * (70f + 170f * cpuLvl.value) * (1f + 5f * kl)) % 3600f
                anim.phaseR = (anim.phaseR + dt * (70f + 170f * gpuLvl.value) * (1f + 5f * kr)) % 3600f
                anim.kickL = kl
                anim.kickR = kr
                anim.clock += dt
            }
        }
    }

    // Dropdown popup RedMagic untuk profil CPU & metrik GPU
    var cpuDropdownOpen by remember { mutableStateOf(false) }
    var gpuDropdownOpen by remember { mutableStateOf(false) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        latest.onInteract()
                    }
                }
            }
            // Hub hanya menutup bila tap jatuh di area GAME (tengah, di luar kedua panel + pita).
            // Tap meleset di dalam panel / di sela tombol diabaikan, jadi tidak lagi "sensitif".
            .pointerInput(Unit) {
                val openedAt = System.currentTimeMillis()
                detectTapGestures { o ->
                    if (cpuDropdownOpen || gpuDropdownOpen) {
                        cpuDropdownOpen = false
                        gpuDropdownOpen = false
                        return@detectTapGestures
                    }
                    if (System.currentTimeMillis() - openedAt < 400L) return@detectTapGestures
                    val k = size.height / DH
                    if (k <= 0f) return@detectTapGestures
                    val yd = o.y / k
                    val xd = (if (o.x <= size.width / 2f) o.x else size.width - o.x) / k
                    val panelEdge = stripX(yd.coerceIn(0f, DH)) + stripHalf(yd.coerceIn(0f, DH)) + 24f
                    if (xd > panelEdge) latest.onClose()
                }
            }
    ) {
        val u = maxHeight.value / DH            // skala seni (gauge, pita, meter)
        val ku = maxOf(u, 0.55f)                // skala tombol / kartu: dijaga agar tetap bisa disentuh
        val full = maxWidth
        fun s(px: Float): Dp = (px * u).dp
        fun sk(px: Float): Dp = (px * ku).dp

        // ------------------------------------------------------------------ latar: panel + pita + gauge
        val slideDist = 520f * u
        Canvas(Modifier.fillMaxSize()) {
            val f = fillP.value
            drawEnergyArt(accent, anim, slideP.value, introLevel(f, cpuLvl.value), introLevel(f, gpuLvl.value))
        }

        // ------------------------------------------------------------------ gauge CPU & GPU
        GaugeBlock(
            x = s(GAUGE_X), u = u, raw = m.cpuGhz, fmt = { "%.2f".format(it) }, unit = "GHz", label = "CPU",
            mode = m.cpuMode, accent = accent, deep = deep, hot = hot,
            onMode = {
                cpuDropdownOpen = !cpuDropdownOpen
                gpuDropdownOpen = false
            },
            isOpen = cpuDropdownOpen,
            fill = fillP, slide = Modifier.slideIn(slideP, -1, slideDist)
        )
        GaugeBlock(
            x = full - s(GAUGE_X), u = u, raw = m.gpuRaw, fmt = { it.roundToInt().toString() }, unit = m.gpuUnit, label = "GPU",
            mode = m.gpuMode, accent = accent, deep = deep, hot = hot,
            onMode = {
                gpuDropdownOpen = !gpuDropdownOpen
                cpuDropdownOpen = false
            },
            isOpen = gpuDropdownOpen,
            fill = fillP, slide = Modifier.slideIn(slideP, 1, slideDist)
        )

        // ------------------------------------------------------------------ meter kecerahan / volume
        Box(Modifier.fillMaxSize().slideIn(slideP, -1, slideDist)) {
            WedgeMeter(Modifier.boxAt(s(METER_X), 536f, 92f, 160f, u), m.brightness, alignEnd = false, onChange = m.onBrightness)
            Icon(Icons.Rounded.Brightness7, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.boxAt(s(METER_ICON_X), 637f, 28f, 28f, u))
        }
        Box(Modifier.fillMaxSize().slideIn(slideP, 1, slideDist)) {
            WedgeMeter(Modifier.boxAt(full - s(METER_X), 536f, 92f, 160f, u), m.volume, alignEnd = true, onChange = m.onVolume)
            Icon(Icons.Rounded.VolumeUp, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.boxAt(full - s(METER_ICON_X), 637f, 28f, 28f, u))
        }

        // ------------------------------------------------------------------ judul + baris status (tengah atas)
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = s(26f))
                .graphicsLayer { alpha = ((slideP.value - 0.35f) / 0.65f).coerceIn(0f, 1f) },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                buildAnnotatedString {
                    val parts = m.title.trim().split(' ', limit = 2)
                    withStyle(SpanStyle(color = lerp(accent, Color.White, 0.45f))) { append(parts.first()) }
                    if (parts.size > 1) withStyle(SpanStyle(color = Color.White.copy(alpha = 0.88f))) { append(" " + parts[1]) }
                },
                fontSize = maxOf(34f * u, 14f).sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
                letterSpacing = 2.sp, maxLines = 1, overflow = TextOverflow.Clip
            )
            Spacer(Modifier.height(s(8f)))
            m.statusLines.take(3).forEach { line ->
                HubText("•  $line", 17f, u, Color.White.copy(alpha = 0.78f), minSp = 9f)
            }
        }

        // ------------------------------------------------------------------ bar info bawah
        Box(
            Modifier.boxAt(full / 2, 618f, 470f, 32f, u)
                .graphicsLayer { alpha = ((slideP.value - 0.35f) / 0.65f).coerceIn(0f, 1f) },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                // RedMagic: tab trapesium yang MELEBAR ke bawah, keempat sudutnya membulat (bukan runcing).
                val w = size.width; val h = size.height; val cut = h * 0.6f
                val p = roundedPolygon(
                    listOf(Offset(cut, 0f), Offset(w - cut, 0f), Offset(w, h), Offset(0f, h)),
                    radius = h * 0.26f
                )
                drawPath(p, Color.Black.copy(alpha = 0.62f))
                drawPath(p, Color.White.copy(alpha = 0.28f), style = Stroke(width = 1.3f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(sk(12f))) {
                val c = Color.White.copy(alpha = 0.92f)
                HubText("ZKM", 19f, u, c, FontWeight.Medium, spacing = 4f, minSp = 8f)
                HubText(m.clock, 19f, u, c, minSp = 8f)
                Icon(Icons.Rounded.BatteryFull, null, tint = c, modifier = Modifier.size(sk(18f)))
                HubText("${m.battery}%", 19f, u, c, minSp = 8f)
                if (m.tempC > 0f) {
                    val tempColor = if (m.tempC >= 42f) Color(0xFFFF4D5E) else if (m.tempC >= 38f) Color(0xFFFFB347) else Color(0xFF34D17A)
                    Box(Modifier.size(sk(7f)).clip(CircleShape).background(tempColor))
                    HubText("${m.tempC.roundToInt()}°C", 19f, u, c, minSp = 8f)
                }
                Icon(Icons.Rounded.Public, null, tint = c, modifier = Modifier.size(sk(18f)))
                HubText(m.netText, 19f, u, c, minSp = 8f)
            }
        }

        // ------------------------------------------------------------------ toast (pill gelap di bawah)
        if (m.toast.isNotEmpty()) {
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = s(640f))
                    .clip(RoundedCornerShape(50)).background(Color(0xE6101012))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                    .padding(horizontal = s(30f), vertical = s(10f))
            ) { HubText(m.toast, 20f, u, Color.White.copy(alpha = 0.9f), minSp = 10f) }
        }

        // ------------------------------------------------------------------ UI interaktif (atas + kolom)
        Column(Modifier.fillMaxSize()) {
            // Bar atas: grup kiri meluncur dari kiri, grup kanan dari kanan
            Box(Modifier.fillMaxWidth().padding(start = sk(38f), end = sk(44f), top = sk(22f))) {
                // Pintasan game TIDAK boleh keluar dari panel (sudut dalam UI): hitung ruang sampai tepi dalam panel
                // di tinggi atas ikon, rapatkan jarak bila perlu, dan buang pintasan paling kiri bila tetap tidak muat.
                val barSpace = ((edgeIn(58f) - 14f) * u - 44f * ku) / ku   // y = 58: pusat tombol bulat (22 + 72/2)
                var shownShortcuts = m.shortcuts
                var shortcutGap = 36f
                while (shownShortcuts.isNotEmpty()) {
                    val fitGap = (barSpace - 72f) / shownShortcuts.size - 68f
                    if (fitGap >= 18f) { shortcutGap = minOf(36f, fitGap); break }
                    shownShortcuts = shownShortcuts.drop(1)
                }
                val shortcutsToShow = shownShortcuts
                val shortcutGapToUse = shortcutGap
                Row(
                    Modifier.align(Alignment.CenterStart).slideIn(slideP, -1, slideDist),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoundBtn(Icons.Rounded.Home, ku) { m.onHome() }
                    Spacer(Modifier.width(sk(28f)))
                    RoundBtn(Icons.Rounded.Edit, ku) { m.onEdit() }
                    Spacer(Modifier.width(sk(28f)))
                    RoundBtn(Icons.Rounded.ViewInAr, ku) { m.onCube() }
                }
                Row(
                    Modifier.align(Alignment.CenterEnd).slideIn(slideP, 1, slideDist),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    shortcutsToShow.forEach { sc ->
                        Box(
                            Modifier.size(sk(68f)).clip(CircleShape).background(ChipGrey).clickable { sc.onClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (sc.icon != null) Image(sc.icon, sc.label, Modifier.size(sk(68f)))
                            else HubText(sc.label.take(1).uppercase(), 28f, ku, Color.White, FontWeight.Bold)
                        }
                        Spacer(Modifier.width(sk(shortcutGapToUse)))
                    }
                    RoundBtn(Icons.Rounded.ExpandMore, ku) { m.onClose() }
                }
            }

            Row(Modifier.weight(1f).fillMaxWidth()) {
                // ---------------------------------------------------------- kolom kiri
                Column(
                    Modifier.slideIn(slideP, -1, slideDist)
                        .padding(start = sk(38f), top = sk(39f)).width(sk(182f)).fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        Modifier.width(sk(182f)).height(sk(40f)).clip(RoundedCornerShape(50)).background(ChipGrey)
                    ) {
                        listOf(true to Icons.Rounded.FormatListBulleted, false to Icons.Rounded.GridView).forEach { (mode, ic) ->
                            val on = m.listMode == mode
                            Box(
                                Modifier.weight(1f).fillMaxHeight()
                                    .background(if (on) lerp(accent, Color.Black, 0.45f) else Color.Transparent)
                                    .clickable { m.onListMode(mode) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    ic, null,
                                    tint = if (on) lerp(accent, Color.White, 0.2f) else Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(sk(24f))
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(sk(48f)))

                    if (m.listMode) {
                        m.cards.forEach { c ->
                            CardView(c, sk(182f), ku, accent, dots = true)
                            Spacer(Modifier.height(sk(13f)))
                        }
                    } else {
                        m.cards.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(sk(10f))) {
                                row.forEach { c -> CardView(c, sk(86f), ku, accent, dots = false) }
                            }
                            Spacer(Modifier.height(sk(13f)))
                        }
                    }
                    Spacer(Modifier.height(sk(60f)))
                }

                Spacer(Modifier.weight(1f))

                // ---------------------------------------------------------- kolom kanan (grid tombol)
                Column(
                    Modifier.slideIn(slideP, 1, slideDist)
                        .padding(end = sk(44f), top = sk(33f)).width(sk(182f)).fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    m.tiles.chunked(2).forEach { row ->
                        Row(Modifier.width(sk(182f)), horizontalArrangement = Arrangement.SpaceBetween) {
                            row.forEach { t -> TileView(t, ku, accent) }
                            if (row.size == 1) Spacer(Modifier.width(sk(72f)))
                        }
                        Spacer(Modifier.height(sk(19f)))
                    }
                    Spacer(Modifier.height(sk(60f)))
                }
            }
        }

        // ------------------------------------------------------------------ dropdown gaya RedMagic (paling atas: menimpa kartu & meter)
        if (cpuDropdownOpen) {
            val items = CpuMode.entries.map { md ->
                PickItem(stringResource(md.labelRes), m.currentCpuMode == md) {
                    m.onSelectCpuMode?.invoke(md) ?: m.onCpuMode()
                    cpuDropdownOpen = false
                }
            }
            PickerDropdown(left = true, full = full, u = u, k = ku, items = items, modifier = Modifier.slideIn(slideP, -1, slideDist))
        }
        if (gpuDropdownOpen) {
            val items = listOf(
                PickItem("GPU Clock", m.gpuUnit == "MHz") { m.onSelectGpuMetric?.invoke(0) ?: m.onGpuMode?.invoke(); gpuDropdownOpen = false },
                PickItem("GPU Load", m.gpuUnit == "%") { m.onSelectGpuMetric?.invoke(1) ?: m.onGpuMode?.invoke(); gpuDropdownOpen = false },
                PickItem("Screen Notification", false) { m.onSelectGpuMetric?.invoke(2); gpuDropdownOpen = false }
            )
            PickerDropdown(left = false, full = full, u = u, k = ku, items = items, modifier = Modifier.slideIn(slideP, 1, slideDist))
        }
    }
}

// =============================================================================================
// potongan UI
// =============================================================================================

@Composable
private fun HubText(
    t: String, px: Float, k: Float,
    color: Color = Color.White,
    weight: FontWeight = FontWeight.Normal,
    italic: Boolean = false,
    spacing: Float = 0f,
    minSp: Float = 8f,
    align: TextAlign? = null,
    modifier: Modifier = Modifier
) {
    Text(
        t, color = color, fontSize = maxOf(px * k, minSp).sp, fontWeight = weight,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        letterSpacing = spacing.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        textAlign = align, modifier = modifier
    )
}

@Composable
private fun RoundBtn(icon: ImageVector, k: Float, onClick: () -> Unit) {
    Box(
        Modifier.size((72f * k).dp).clip(CircleShape).background(ChipGrey).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.size((36f * k).dp)) }
}

/** Satu gauge: angka bergradasi (hitung naik saat masuk), satuan, label, pill mode oktagon faceted. */
@Composable
private fun GaugeBlock(
    x: Dp, u: Float, raw: Float, fmt: (Float) -> String, unit: String, label: String, mode: String,
    accent: Color, deep: Color, hot: Color, onMode: () -> Unit,
    isOpen: Boolean, fill: State<Float>, slide: Modifier
) {
    val smooth by animateFloatAsState(raw.coerceAtLeast(0f), tween(450, easing = FastOutSlowInEasing), label = "gaugeNum")
    // Angka menghitung naik dari 0 mengikuti isian pita saat overlay baru muncul.
    val shown = smooth * (fill.value / 0.55f).coerceIn(0f, 1f)
    Box(slide.fillMaxSize()) {
        Box(Modifier.boxAt(x, 156f, 240f, 72f, u), contentAlignment = Alignment.Center) {
            Text(
                if (raw >= 0f) fmt(shown) else "--",
                style = TextStyle(
                    brush = Brush.verticalGradient(listOf(Color.White, lerp(accent, Color.White, 0.7f))),
                    fontSize = maxOf(58f * u, 20f).sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
                    letterSpacing = 1.sp
                ),
                maxLines = 1, overflow = TextOverflow.Clip
            )
        }
        Box(Modifier.boxAt(x, 188f, 110f, 24f, u), contentAlignment = Alignment.Center) {
            HubText(unit, 16f, u, Color.White.copy(alpha = 0.85f), minSp = 8f)
        }
        Box(Modifier.boxAt(x, 318f, 100f, 30f, u), contentAlignment = Alignment.Center) {
            HubText(label, 22f, u, lerp(accent, Color.White, 0.1f), FontWeight.Black, italic = true, spacing = 2f, minSp = 9f)
        }
        ModePill(
            Modifier.boxAt(x, 384f, 152f, 52f, u).clickable { onMode() },
            mode, accent, deep, isOpen, u
        )
    }
}

/**
 * Pill mode gaya RedMagic: heksagon runcing di kiri & kanan ("<  >"), isi gelap dengan pendar merah elips di tengah,
 * garis atas putus di tengah, garis bawah putus lebih lebar tempat chevron "v" duduk (berbalik "^" saat terbuka).
 * Sisi runcing lebih terang, garis datar redup.
 */
@Composable
private fun ModePill(
    modifier: Modifier,
    text: String,
    accent: Color,
    deep: Color,
    isOpen: Boolean,
    k: Float
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val ins = h * 0.30f
            val gapTop = w * 0.17f
            val gapBot = w * 0.27f
            val sw = (h / 58f).coerceAtLeast(0.6f)
            val hex = Path().apply {
                moveTo(ins, 0f); lineTo(w - ins, 0f); lineTo(w, h / 2f)
                lineTo(w - ins, h); lineTo(ins, h); lineTo(0f, h / 2f); close()
            }
            // pendar luar sangat tipis di sekitar ujung
            drawPath(hex, accent.copy(alpha = if (isOpen) 0.22f else 0.12f), style = Stroke(width = 7f * sw))
            // isi: hitam kemerahan, pendar elips di tengah
            clipPath(hex) {
                drawRect(Color(0xF2060205))
                drawOval(
                    Brush.radialGradient(
                        0f to lerp(accent, Color.Black, 0.40f).copy(alpha = if (isOpen) 0.95f else 0.80f),
                        0.55f to lerp(accent, Color.Black, 0.72f).copy(alpha = 0.55f),
                        1f to Color.Transparent,
                        center = Offset(w / 2f, h / 2f), radius = w * 0.30f
                    ),
                    topLeft = Offset(w * 0.12f, -h * 0.15f), size = Size(w * 0.76f, h * 1.30f)
                )
            }
            val rose = lerp(accent, Color.Black, 0.22f)
            val tip = lerp(accent, Color.White, 0.10f)
            // garis datar atas / bawah (redup, memudar ke arah celah)
            fun flat(y: Float, gap: Float) {
                val cx = w / 2f
                val bl = Brush.horizontalGradient(listOf(tip.copy(alpha = 0.95f), rose.copy(alpha = 0.50f)), startX = ins, endX = cx - gap / 2f)
                val br = Brush.horizontalGradient(listOf(rose.copy(alpha = 0.50f), tip.copy(alpha = 0.95f)), startX = cx + gap / 2f, endX = w - ins)
                drawLine(bl, Offset(ins, y), Offset(cx - gap / 2f, y), strokeWidth = 1.5f * sw)
                drawLine(br, Offset(cx + gap / 2f, y), Offset(w - ins, y), strokeWidth = 1.5f * sw)
            }
            flat(0.8f * sw, gapTop)
            flat(h - 0.8f * sw, gapBot)
            // sisi runcing: lebih terang & tebal
            val l = Path().apply { moveTo(ins, 0f); lineTo(0f, h / 2f); lineTo(ins, h) }
            val r = Path().apply { moveTo(w - ins, 0f); lineTo(w, h / 2f); lineTo(w - ins, h) }
            val tipA = if (isOpen) 1f else 0.92f
            drawPath(l, tip.copy(alpha = tipA), style = Stroke(width = 2.6f * sw, cap = StrokeCap.Round))
            drawPath(r, tip.copy(alpha = tipA), style = Stroke(width = 2.6f * sw, cap = StrokeCap.Round))
            // chevron di celah garis bawah
            val cw = w * 0.095f
            val cy0 = h * 0.93f
            val dy = h * 0.14f * (if (isOpen) -1f else 1f)
            val chev = Path().apply {
                moveTo(w / 2f - cw, cy0 - dy / 2f); lineTo(w / 2f, cy0 + dy / 2f); lineTo(w / 2f + cw, cy0 - dy / 2f)
            }
            drawPath(chev, Color.White.copy(alpha = 0.95f), style = Stroke(width = 2.4f * sw, cap = StrokeCap.Round))
        }
        HubText(
            text, 17f, k,
            color = Color.White,
            weight = FontWeight.Bold,
            minSp = 9f,
            modifier = Modifier.padding(horizontal = (18f * k).dp).offset(y = (-2f * k).dp)
        )
    }
}

internal data class PickItem(val label: String, val selected: Boolean, val onClick: () -> Unit)

/**
 * Dropdown RedMagic: daftar teks (item terpilih putih tebal, lainnya redup & memudar) di atas panel kaca abu-abu.
 * Sisi dalam panel mengikuti kemiringan kaki bawah pita (jajar genjang miring), digambar di koordinat desain
 * sehingga otomatis dicerminkan untuk panel kanan.
 */
@Composable
private fun PickerDropdown(left: Boolean, full: Dp, u: Float, k: Float, items: List<PickItem>, modifier: Modifier = Modifier) {
    val n = items.size.coerceAtLeast(1)
    val sel = items.indexOfFirst { it.selected }.let { if (it < 0) n / 2 else it }
    val rowH = 44f
    val pad = 6f
    val top = PICK_TOP
    val bottom = top + n * rowH + 2f * pad
    val width = 148f
    fun xr(y: Float) = edgeIn(y) - 9f     // sisi dalam (dekat pita)
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val kk = size.height / DH
            val p = Path().apply {
                moveTo(px(xr(top) - width, left, kk), top * kk)
                lineTo(px(xr(top), left, kk), top * kk)
                lineTo(px(xr(bottom), left, kk), bottom * kk)
                lineTo(px(xr(bottom) - width, left, kk), bottom * kk)
                close()
            }
            drawPath(p, Brush.verticalGradient(listOf(Color(0xEC45454B), Color(0xEC2B2B30)), startY = top * kk, endY = bottom * kk))
            drawPath(p, Color.White.copy(alpha = 0.10f), style = Stroke(width = 1.2f))
        }
        items.forEachIndexed { i, item ->
            val yc = top + pad + (i + 0.5f) * rowH
            val xc = xr(yc) - width / 2f
            val dist = abs(i - sel)
            val a = if (dist == 0) 1f else (0.55f - 0.12f * (dist - 1)).coerceAtLeast(0.25f)
            Box(
                Modifier
                    .boxAt(if (left) (xc * u).dp else full - (xc * u).dp, yc, width - 14f, rowH, u)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { item.onClick() },
                contentAlignment = Alignment.Center
            ) {
                HubText(
                    item.label, if (dist == 0) 19f else 15f, k,
                    color = Color.White.copy(alpha = a),
                    weight = if (dist == 0) FontWeight.Bold else FontWeight.Normal,
                    minSp = 9f
                )
            }
        }
    }
}

/** Meter baji: 15 garis, panjang menyusut ke bawah; bagian bawah menyala. Geser vertikal untuk mengubah nilai. */
@Composable
private fun WedgeMeter(modifier: Modifier, level: Float, alignEnd: Boolean, onChange: (Float, Boolean) -> Unit) {
    val cb by rememberUpdatedState(onChange)
    val last = remember { floatArrayOf(level) }
    Canvas(
        modifier
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { o -> last[0] = 1f - (o.y / size.height).coerceIn(0f, 1f); cb(last[0], false) },
                    onDragEnd = { cb(last[0], true) },
                    onDragCancel = { cb(last[0], true) }
                ) { change, _ ->
                    change.consume()
                    last[0] = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                    cb(last[0], false)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    last[0] = 1f - (o.y / size.height).coerceIn(0f, 1f)
                    cb(last[0], true)
                }
            }
    ) {
        val n = 15
        val lit = ceil(level.coerceIn(0f, 1f) * n).toInt()
        val pitch = size.height / n
        for (i in 0 until n) {
            val wBar = size.width * (1f - 0.76f * i / (n - 1f))
            val on = (n - 1 - i) < lit
            val x = if (alignEnd) size.width - wBar else 0f
            drawRect(
                if (on) Color(0xFFE9E9EC) else Color.White.copy(alpha = 0.34f),
                topLeft = Offset(x, i * pitch + pitch * 0.2f),
                size = Size(wBar, pitch * 0.56f)
            )
        }
    }
}

@Composable
private fun CardView(c: HubCard, width: Dp, k: Float, accent: Color, dots: Boolean) {
    val shape = RoundedCornerShape((12f * k).dp)
    val bg = if (c.active) {
        Brush.verticalGradient(listOf(lerp(accent, Color.Black, 0.42f), lerp(accent, Color.Black, 0.58f)))
    } else Brush.verticalGradient(listOf(ChipGrey, ChipGrey))
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.width(width).height((74f * k).dp).clip(shape).background(bg).clickable { c.onClick() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.size((54f * k).dp).clip(CircleShape)
                    .background(if (c.active) Color.Black.copy(alpha = 0.42f) else Color(0xFF2A2A2D)),
                contentAlignment = Alignment.Center
            ) { Icon(c.icon, null, tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.size((30f * k).dp)) }
            if (dots) {
                Icon(
                    Icons.Rounded.MoreVert, null, tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = (8f * k).dp, end = (4f * k).dp).size((22f * k).dp)
                )
            }
        }
        Spacer(Modifier.height((4f * k).dp))
        HubText(
            c.label, 20f, k, Color.White.copy(alpha = if (c.active) 0.95f else 0.62f),
            minSp = 10f, align = TextAlign.Center, modifier = Modifier.width(width)
        )
    }
}

@Composable
private fun TileView(t: HubTile, k: Float, accent: Color) {
    val shape = RoundedCornerShape((12f * k).dp)
    val bg = if (t.active) {
        Brush.verticalGradient(listOf(lerp(accent, Color.Black, 0.42f), lerp(accent, Color.Black, 0.58f)))
    } else Brush.verticalGradient(listOf(ChipGrey, ChipGrey))
    Column(Modifier.width((72f * k).dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size((72f * k).dp).clip(shape).background(bg).clickable { t.onClick() },
            contentAlignment = Alignment.Center
        ) { Icon(t.icon, null, tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.size((38f * k).dp)) }
        Spacer(Modifier.height((4f * k).dp))
        HubText(
            t.label, 20f, k, Color.White.copy(alpha = if (t.active) 0.95f else 0.62f),
            minSp = 10f, align = TextAlign.Center, modifier = Modifier.width((72f * k).dp)
        )
    }
}

// =============================================================================================
// gambar: panel hitam + pita segmen chevron + cincin gauge
// =============================================================================================

/**
 * Poligon tertutup dengan SEMUA sudut dibulatkan (kurva kuadrat). [radius] dibatasi setengah panjang sisi
 * terpendek di tiap sudut, jadi aman untuk bentuk kecil. Dipakai agar sudut tidak runcing seperti RedMagic.
 */
private fun roundedPolygon(pts: List<Offset>, radius: Float): Path {
    val path = Path()
    val n = pts.size
    for (i in 0 until n) {
        val prev = pts[(i + n - 1) % n]
        val cur = pts[i]
        val next = pts[(i + 1) % n]
        val v1 = prev - cur
        val v2 = next - cur
        val l1 = v1.getDistance()
        val l2 = v2.getDistance()
        if (l1 <= 0f || l2 <= 0f) continue
        val r = minOf(radius, l1 / 2f, l2 / 2f)
        val a = cur + v1 * (r / l1)
        val b = cur + v2 * (r / l2)
        if (i == 0) path.moveTo(a.x, a.y) else path.lineTo(a.x, a.y)
        path.quadraticBezierTo(cur.x, cur.y, b.x, b.y)
    }
    path.close()
    return path
}

private fun DrawScope.drawEnergyArt(accent: Color, anim: HubAnim, p: Float, levelL: Float, levelR: Float) {
    val k = size.height / DH

    // Redupkan game sedikit supaya judul / info tengah tetap terbaca; game tetap terlihat jelas.
    drawRect(Color.Black.copy(alpha = 0.30f * p.coerceIn(0f, 1f)))

    // "Bernapas": level bergoyang halus saat diam, supaya pita tetap hidup (real-time).
    val wobL = 0.018f * sin(anim.clock * 1.9f)
    val wobR = 0.018f * sin(anim.clock * 2.3f + 1.7f)
    val lvL = (levelL + wobL * levelL.coerceIn(0.2f, 1f)).coerceIn(0f, 1f)
    val lvR = (levelR + wobR * levelR.coerceIn(0.2f, 1f)).coerceIn(0f, 1f)

    // Tiap sisi meluncur dari tepinya sendiri.
    val shift = 560f * k * (1f - p)
    translate(left = -shift) {
        drawSide(left = true, accent = accent, k = k, level = lvL, kick = anim.kickL, clock = anim.clock)
        drawGauge(GAUGE_X * k, k, accent, anim.phaseL, anim.kickL, anim.clock, lvL)
    }
    translate(left = shift) {
        drawSide(left = false, accent = accent, k = k, level = lvR, kick = anim.kickR, clock = anim.clock + 0.9f)
        drawGauge(size.width - GAUGE_X * k, k, accent, -anim.phaseR, anim.kickR, anim.clock + 0.9f, lvR)
    }
}

/** Peta koordinat desain (sisi kiri) -> piksel, dicerminkan untuk sisi kanan. */
private fun DrawScope.px(xd: Float, left: Boolean, k: Float): Float = if (left) xd * k else size.width - xd * k

/** Pita pada rentang y [ya, yb] (desain), melebar `extra` di tiap sisi. Tepi atas/bawah datar. */
private fun DrawScope.bandPath(left: Boolean, ya: Float, yb: Float, extra: Float, k: Float, extraOut: Float = extra): Path {
    val n = maxOf(2, ((yb - ya) / 3f).toInt() + 1)
    val p = Path()
    for (i in 0..n) {
        val y = ya + (yb - ya) * i / n
        val x = px(stripX(y) - stripHalf(y) - extra, left, k)
        if (i == 0) p.moveTo(x, y * k) else p.lineTo(x, y * k)
    }
    for (i in n downTo 0) {
        val y = ya + (yb - ya) * i / n
        p.lineTo(px(stripX(y) + stripHalf(y) + extraOut, left, k), y * k)
    }
    p.close()
    return p
}

/** Warna segmen menurut ketinggian: abu-abu -> mawar gelap -> merah menyala -> merah tua di dasar. */
private fun stripColor(y: Float, accent: Color): Color {
    val grey = Color(0xFF2E2E31)
    val rose = lerp(accent, Color.Black, 0.62f)
    val hot = lerp(accent, Color.White, 0.04f)
    val base = lerp(accent, Color.Black, 0.30f)
    return when {
        y < 255f -> grey
        y < 335f -> lerp(grey, rose, (y - 255f) / 80f)
        y < 420f -> lerp(rose, hot, (y - 335f) / 85f)
        y < 520f -> hot
        else -> lerp(hot, base, ((y - 520f) / 203f).coerceIn(0f, 1f))
    }
}

/** Warna segmen yang menyala: terang di bawah, makin gelap ke atas. */
private fun litColor(y: Float, accent: Color): Color {
    val rose = lerp(accent, Color.Black, 0.55f)
    val hot = lerp(accent, Color.White, 0.04f)
    val base = lerp(accent, Color.Black, 0.30f)
    return when {
        y < 420f -> lerp(rose, hot, (y / 420f).coerceIn(0f, 1f))
        y < 520f -> hot
        else -> lerp(hot, base, ((y - 520f) / 203f).coerceIn(0f, 1f))
    }
}

private fun DrawScope.drawSide(left: Boolean, accent: Color, k: Float, level: Float, kick: Float, clock: Float) {
    val lw = k.coerceAtLeast(0.8f)

    /** Garis tepi (polyline) dari fungsi jarak-dari-tepi-layar f(y). */
    fun edgePath(f: (Float) -> Float): Path = Path().apply {
        var y = -2f
        var first = true
        while (y <= DH + 2f) {
            val x = px(f(y), left, k)
            if (first) { moveTo(x, y * k); first = false } else lineTo(x, y * k)
            y += 3f
        }
    }

    /** Pita tertutup di antara dua tepi (dalam -> luar). */
    fun ribbon(inner: (Float) -> Float, outer: (Float) -> Float): Path = Path().apply {
        var y = -2f
        var first = true
        while (y <= DH + 2f) {
            val x = px(inner(y), left, k)
            if (first) { moveTo(x, y * k); first = false } else lineTo(x, y * k)
            y += 3f
        }
        y = DH + 2f
        while (y >= -2f) {
            lineTo(px(outer(y), left, k), y * k)
            y -= 3f
        }
        close()
    }

    // ---- panel hitam (UI) sampai tepi panel; sudut tekukan membulat
    val panel = Path().apply {
        val edge = if (left) 0f else size.width
        moveTo(edge, 0f)
        var y = 0f
        while (y <= DH) { lineTo(px(edgeIn(y), left, k), y * k); y += 3f }
        lineTo(px(edgeIn(DH), left, k), size.height)
        lineTo(edge, size.height)
        close()
    }
    drawPath(panel, PanelBlack)
    // Pendar volumetrik lembut di belakang gauge: panel tetap HITAM, hanya sekitar cincin yang berpendar (seperti RedMagic)
    clipPath(panel) {
        val c = Offset(px(GAUGE_X, left, k), 240f * k)
        val gaCore = (0.34f + 0.30f * kick).coerceAtMost(0.80f)
        val gaMid = (0.14f + 0.14f * kick).coerceAtMost(0.40f)
        drawCircle(
            Brush.radialGradient(
                0.0f to accent.copy(alpha = gaCore),
                0.30f to accent.copy(alpha = gaMid),
                0.65f to accent.copy(alpha = 0.04f),
                1.0f to Color.Transparent,
                center = c,
                radius = 235f * k
            ),
            radius = 235f * k,
            center = c
        )
    }
    // garis pemisah tipis di bawah bar atas (dan di bawah toggle pada sisi kiri)
    val edgeX = px(0f, left, k)
    val lineEnd = px(edgeIn(108f) - 6f, left, k)
    drawLine(Color.White.copy(alpha = 0.10f), Offset(edgeX, 108f * k), Offset(lineEnd, 108f * k), strokeWidth = 1.3f * lw)
    if (left) drawLine(Color.White.copy(alpha = 0.10f), Offset(16f * k, 197f * k), Offset(255f * k, 197f * k), strokeWidth = 1.3f * lw)

    // ---- rumah pita: kanal GELAP hampir pekat di antara tepi panel dan garis luar, supaya isi game di belakang
    //      pita tidak tembus / berantakan (RedMagic menggelapkan area ini). Panel UI dan garis luar terpisah.
    drawPath(ribbon({ y -> edgeIn(y) }, { y -> edgeOut(y) }), Color(0xFF04050A).copy(alpha = 0.90f))
    // garis tepi panel UI (biru-ungu tipis, membulat) + pendar halus
    drawPath(edgePath { y -> edgeIn(y) }, Color(0xFF7E86D8).copy(alpha = 0.20f), style = Stroke(width = 4.5f * lw))
    drawPath(edgePath { y -> edgeIn(y) }, Color(0xFF6C74C8).copy(alpha = 0.92f), style = Stroke(width = 1.5f * lw))
    // garis luar melengkung (sisi game), terpisah dari panel oleh blok + celah gelap
    drawPath(edgePath { y -> edgeOut(y) }, Color(0xFF4F6DBA).copy(alpha = 0.16f), style = Stroke(width = 4f * lw))
    drawPath(edgePath { y -> edgeOut(y) }, Color(0xFF5876C4).copy(alpha = 0.78f), style = Stroke(width = 1.3f * lw))

    // ---- segmen: datar (tanpa bevel), ujung horizontal, celah tipis; menyala bergradasi merah muda -> merah menyala
    val pitch = 42.84f   // RedMagic: celah antar segmen tiap 42,84 unit desain (diukur 15 interval, selisih < 1)
    val gap = 5.8f       // lebar celah RedMagic (terukur 6,4 dengan metode yang sama yang membaca celah 4,5 sebagai 5,1)
    val off = -0.74f     // pusat celah pertama
    var i = 0
    while (true) {
        val y0 = off + i * pitch + gap / 2f
        val y1 = off + (i + 1) * pitch - gap / 2f
        if (y0 > DH) break
        val ya = y0.coerceAtLeast(0f)
        val yb = y1.coerceAtMost(DH)
        if (yb - ya > 3f) {
            val mid = (ya + yb) / 2f
            val h = 1f - mid / DH
            val a = ((level - h) / (pitch / DH) + 0.5f).coerceIn(0f, 1f)
            val sweep = (clock * 0.42f) % 1.7f
            val shim = (1f - abs(h - sweep) / 0.12f).coerceIn(0f, 1f) * a
            val litTop = lerp(litColor(ya, accent), Color.White, 0.05f + 0.25f * shim)
            val litBot = lerp(litColor(yb, accent), Color.Black, 0.10f)
            val offTop = Color(0xFF302F33)
            val offBot = Color(0xFF262629)
            val path = bandPath(left, ya, yb, 0f, k)
            if (a > 0f) drawPath(path, accent.copy(alpha = (0.20f + 0.22f * kick) * a), style = Stroke(width = (7f + 6f * kick) * k))
            drawPath(
                path,
                Brush.verticalGradient(listOf(lerp(offTop, litTop, a), lerp(offBot, litBot, a)), startY = ya * k, endY = yb * k)
            )
        }
        i++
    }
}

/** Titik bantu: pendar lembut dengan menumpuk goresan elips yang makin lebar & makin transparan (meniru blur). */
private fun DrawScope.bloomOval(tl: Offset, sz: Size, color: Color, w: Float, a: Float, passes: Int = 5) {
    for (i in passes downTo 1) {
        drawOval(color.copy(alpha = (a / (i + 0.6f)).coerceIn(0f, 1f)), tl, sz, style = Stroke(w + i * w * 0.9f))
    }
}

/**
 * Gauge ala RedMagic: piringan elips tebal berpendar (dinding + bibir terang di depan), lubang gelap berlapis cincin tipis,
 * pusaran melayang yang meruncing (tebal di kepala, menipis ke ekor) berputar, lidah api kecil, busur bawah + garis label.
 */
private fun DrawScope.drawGauge(cx: Float, k: Float, accent: Color, phase: Float, kick: Float, clock: Float, level: Float) {
    val cy = 262f * k
    val rx = 125f * k
    val ry = 38f * k
    val hot = lerp(accent, Color.White, 0.30f)
    val deep = lerp(accent, Color.Black, 0.62f)
    val breath = 0.5f + 0.5f * sin(clock * 2.2f)

    // pendar besar di belakang gauge (bernapas)
    drawCircle(
        Brush.radialGradient(
            0f to accent.copy(alpha = (0.26f + 0.10f * breath + 0.30f * kick).coerceAtMost(0.7f)),
            0.55f to accent.copy(alpha = 0.07f), 1f to Color.Transparent,
            center = Offset(cx, cy - 12f * k), radius = 190f * k
        ),
        radius = 190f * k, center = Offset(cx, cy - 12f * k)
    )

    // busur bawah samar + garis pengapit label
    val ex = Offset(cx - 120f * k, cy + 4f * k - 52f * k)
    val es = Size(240f * k, 104f * k)
    drawArc(accent.copy(alpha = 0.22f), 100f, 55f, false, ex, es, style = Stroke(2.2f * k, cap = StrokeCap.Round))
    drawArc(accent.copy(alpha = 0.22f), 25f, 55f, false, ex, es, style = Stroke(2.2f * k, cap = StrokeCap.Round))
    drawArc(accent.copy(alpha = 0.30f), 107f, 41f, false, ex, es, style = Stroke(7f * k, cap = StrokeCap.Round))
    drawArc(accent.copy(alpha = 0.30f), 32f, 41f, false, ex, es, style = Stroke(7f * k, cap = StrokeCap.Round))
    drawArc(accent.copy(alpha = 0.95f), 107f, 41f, false, ex, es, style = Stroke(3.6f * k, cap = StrokeCap.Butt))
    drawArc(accent.copy(alpha = 0.95f), 32f, 41f, false, ex, es, style = Stroke(3.6f * k, cap = StrokeCap.Butt))

    // dinding piringan (tebal) + pendar tepi
    drawOval(deep.copy(alpha = 0.95f), Offset(cx - rx, cy - ry + 11f * k), Size(rx * 2f, ry * 2f))
    bloomOval(Offset(cx - rx, cy - ry), Size(rx * 2f, ry * 2f), accent, 5f * k, 0.55f + 0.25f * kick + 0.10f * breath)
    // permukaan piringan = bibir tebal: gelap di belakang, terang di depan
    drawOval(
        Brush.verticalGradient(listOf(lerp(accent, Color.Black, 0.55f), accent, hot), startY = cy - ry, endY = cy + ry),
        Offset(cx - rx, cy - ry), Size(rx * 2f, ry * 2f)
    )
    // kilau bibir depan
    drawArc(
        Brush.horizontalGradient(
            listOf(accent.copy(alpha = 0.0f), Color.White.copy(alpha = 0.55f), accent.copy(alpha = 0.0f)),
            startX = cx - rx * 0.8f, endX = cx + rx * 0.8f
        ),
        startAngle = 25f, sweepAngle = 130f, useCenter = false,
        topLeft = Offset(cx - rx * 0.90f, cy - ry * 0.55f + 4f * k), size = Size(rx * 1.8f, ry * 1.1f),
        style = Stroke(width = 3.2f * k, cap = StrokeCap.Round)
    )
    // lubang gelap (bergeser ke atas -> bibir depan lebih tebal, perspektif)
    val pcy = cy - 5f * k
    val prx = 100f * k
    val pry = 25.5f * k
    drawOval(Color(0xFF080204), Offset(cx - prx, pcy - pry), Size(prx * 2f, pry * 2f))
    drawOval(Color.Black.copy(alpha = 0.35f), Offset(cx - prx, pcy - pry), Size(prx * 2f, pry * 2f), style = Stroke(3f * k))
    // lingkar konsentris tipis, berputar berlawanan arah
    val rings = floatArrayOf(0.80f, 0.56f, 0.33f)
    for ((i, f) in rings.withIndex()) {
        val tl = Offset(cx - prx * f, pcy - pry * f)
        val sz = Size(prx * 2f * f, pry * 2f * f)
        drawOval(accent.copy(alpha = 0.14f), tl, sz, style = Stroke(1.0f * k))
        val dir = if (i % 2 == 0) -1f else 1f
        drawArc(accent.copy(alpha = 0.40f), phase * 0.8f * dir + i * 70f, 70f, false, tl, sz, style = Stroke(1.4f * k, cap = StrokeCap.Round))
    }

    // pusaran melayang: spiral meruncing (kepala tebal & terang -> ekor tipis & redup)
    val scy = cy - 27f * k
    val srx0 = 98f * k
    val sry0 = 31f * k
    val head = 200f + phase
    val span = 255f
    val n = 30
    for (pass in 0..1) {
        for (j in 0 until n) {
            val t0 = j / n.toFloat()
            val t1 = (j + 1) / n.toFloat()
            val tm = (t0 + t1) / 2f
            val shrink = 0.80f + 0.20f * tm
            val rrx = srx0 * shrink
            val rry = sry0 * shrink
            val wd = (1.6f + 17f * tm * tm) * k * (1f + 0.25f * kick)
            val start = head - span + span * t0
            val sweep = span * (t1 - t0) + 1.2f
            val tl = Offset(cx - rrx, scy - rry)
            val sz = Size(rrx * 2f, rry * 2f)
            if (pass == 0) {
                drawArc(accent.copy(alpha = 0.22f * (0.2f + tm)), start, sweep, false, tl, sz, style = Stroke(wd * 2.4f, cap = StrokeCap.Round))
            } else {
                drawArc(lerp(accent, hot, tm).copy(alpha = 0.35f + 0.65f * tm), start, sweep, false, tl, sz, style = Stroke(wd, cap = StrokeCap.Round))
            }
        }
    }
    // inti terang di kepala pusaran
    drawArc(Color.White.copy(alpha = 0.35f), head - 60f, 60f, false, Offset(cx - srx0, scy - sry0), Size(srx0 * 2f, sry0 * 2f), style = Stroke(3.2f * k, cap = StrokeCap.Round))

    // lidah api kecil di pusat lubang: bergetar, makin tinggi saat level naik
    val fl = 0.75f + 0.35f * level + 0.12f * sin(clock * 17f) + 0.4f * kick
    val jx = 1.6f * k * sin(clock * 23f)
    val fy = pcy + 4f * k
    val flame = Path().apply {
        moveTo(cx - 1.5f * k, fy + 4f * k)
        lineTo(cx + 3.5f * k + jx, fy - 7f * k * fl)
        lineTo(cx - 2.5f * k - jx, fy - 14f * k * fl)
        lineTo(cx + 2f * k + jx * 0.5f, fy - 26f * k * fl)
    }
    drawPath(flame, accent.copy(alpha = 0.35f), style = Stroke(7f * k, cap = StrokeCap.Round))
    drawPath(flame, lerp(accent, Color.White, 0.70f), style = Stroke(2.4f * k, cap = StrokeCap.Round))

    // riak yang memuai keluar saat mode diganti
    if (kick > 0.02f) {
        val g = 1f + (1f - kick) * 1.1f
        drawOval(
            accent.copy(alpha = kick * 0.6f),
            Offset(cx - rx * g, cy - ry * g), Size(rx * 2f * g, ry * 2f * g), style = Stroke(3f * k)
        )
    }
}
