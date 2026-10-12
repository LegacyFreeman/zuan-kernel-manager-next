/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 *
 * "Mecha" - design system khusus mode Handheld Game Space.
 * Semua komponen di sini berdiri sendiri (tidak memakai Material / komponen mode Explore):
 * bentuk bersudut potong, tombol yang menekan dengan pegas, dialog yang "terbuka" seperti
 * jendela HUD (garis melebar -> panel membuka -> isi masuk), switch/slider/chip bergaya mesin.
 */
@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.zuan.kernelmanager.next.feature.gamespace

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ==================================== bentuk & warna ======================================

/** Jajar genjang: sisi kiri-kanan miring. */
internal val MechaSlant = GenericShape { size, _ ->
    val s = size.height * 0.28f
    moveTo(s, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width - s, size.height)
    lineTo(0f, size.height)
    close()
}

/**
 * Kotak dengan dua sudut dipotong (kiri-atas & kanan-bawah).
 * Potongan dibatasi [maxCut] (dp) supaya kartu yang tinggi/lebar tidak memotong isi
 * (dulu 28% sisi terpendek -> di kartu Custom Mode teks & chip kiri-atas ikut terpotong).
 */
internal class MechaChamferShape(private val maxCut: Dp = 14.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = minOf(size.minDimension * 0.28f, with(density) { maxCut.toPx() })
        val p = Path().apply {
            moveTo(c, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height - c)
            lineTo(size.width - c, size.height)
            lineTo(0f, size.height)
            lineTo(0f, c)
            close()
        }
        return Outline.Generic(p)
    }
}

internal val MechaChamfer: Shape = MechaChamferShape(14.dp)

/** Pelat panel: dua sudut besar dipotong (kiri-atas, kanan-bawah) + dua sudut kecil. */
internal class MechaPlateShape(private val big: Dp, private val small: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { big.toPx() }.coerceAtMost(size.minDimension / 2f)
        val s = with(density) { small.toPx() }.coerceAtMost(size.minDimension / 4f)
        val p = Path().apply {
            moveTo(c, 0f)
            lineTo(size.width - s, 0f)
            lineTo(size.width, s)
            lineTo(size.width, size.height - c)
            lineTo(size.width - c, size.height)
            lineTo(s, size.height)
            lineTo(0f, size.height - s)
            lineTo(0f, c)
            close()
        }
        return Outline.Generic(p)
    }
}

internal val MechaDanger = Color(0xFFFF4D5E)
internal val MechaOk = Color(0xFF39E58C)

// ==================================== gerak dasar =========================================

/**
 * Klik dengan "tekan" berpegas: tombol menyusut cepat lalu memantul kembali.
 * Taruh PALING DEPAN di rantai modifier supaya seluruh tombol ikut menyusut.
 */
@Composable
internal fun Modifier.mechaClick(
    pressScale: Float = 0.94f,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed) pressScale else 1f,
        spring(dampingRatio = 0.38f, stiffness = 520f), label = "press"
    )
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

/** Masuk berurutan (stagger): geser + muncul dengan pegas. Index > 8 tidak ditunda. */
@Composable
internal fun Modifier.mechaEnter(index: Int = 0, dx: Dp = 36.dp, dy: Dp = 0.dp): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (index in 1..8) delay(index * 45L)
        p.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 260f))
    }
    return this.graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        translationX = (1f - v) * dx.toPx()
        translationY = (1f - v) * dy.toPx()
    }
}

// ==================================== dialog host =========================================

/**
 * Dialog HUD di dalam konsol (bukan Dialog sistem, bukan bottom sheet).
 * Buka: garis cahaya melebar -> panel membuka ke bawah -> isi masuk. Tutup: kebalikannya.
 * [content] dan [footer] menerima `close` untuk menutup dengan animasi.
 */
@Composable
internal fun MechaDialog(
    accent: Color,
    title: String,
    subtitle: String?,
    icon: ImageVector,
    onDismiss: () -> Unit,
    widthFraction: Float = 0.78f,
    heightFraction: Float = 0.9f,
    footer: (@Composable RowScope.(close: () -> Unit) -> Unit)? = null,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit
) {
    val scope = rememberCoroutineScope()
    val fade = remember { Animatable(0f) }
    val ux = remember { Animatable(0f) }
    val uy = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch { fade.animateTo(1f, tween(220)) }
        ux.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
        uy.animateTo(1f, spring(dampingRatio = 0.68f, stiffness = 320f))
    }

    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                uy.animateTo(0f, tween(170, easing = FastOutSlowInEasing))
                ux.animateTo(0f, tween(130))
                fade.animateTo(0f, tween(90))
                onDismiss()
            }
        }
    }
    BackHandler(onBack = close)

    val inf = rememberInfiniteTransition(label = "mechaDlg")
    val scan by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "scan")
    val glow by inf.animateFloat(
        0.45f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow"
    )

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.66f * fade.value))
            .pointerInput(Unit) { detectTapGestures { close() } }
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        val shape = remember { MechaPlateShape(22.dp, 8.dp) }
        val contentA = ((uy.value - 0.55f) / 0.45f).coerceIn(0f, 1f)

        Column(
            Modifier
                .width(maxWidth * widthFraction)
                .height(maxHeight * heightFraction)
                .graphicsLayer {
                    scaleX = 0.04f + 0.96f * ux.value
                    scaleY = 0.03f + 0.97f * uy.value
                    alpha = fade.value
                }
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xF4121624), Color(0xF4080A12))))
                .border(
                    1.5.dp,
                    Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.2f), accent)),
                    shape
                )
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    // balok cahaya pemindai
                    val beam = 70.dp.toPx()
                    val y = h * scan
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, accent.copy(alpha = 0.10f), Color.Transparent),
                            startY = y - beam, endY = y + beam
                        ),
                        topLeft = Offset(0f, y - beam), size = Size(w, beam * 2)
                    )
                    // garis miring tipis
                    val lw = 1.dp.toPx()
                    for (i in 0..9) {
                        val x = i * (w / 7f)
                        drawLine(accent.copy(alpha = 0.035f), Offset(x, 0f), Offset(x - h * 0.6f, h), strokeWidth = lw)
                    }
                    // siku sudut menyala
                    val len = 26.dp.toPx()
                    val sw = 2.5.dp.toPx()
                    val col = accent.copy(alpha = glow)
                    drawLine(col, Offset(26.dp.toPx(), 0f), Offset(26.dp.toPx() + len, 0f), sw, StrokeCap.Round)
                    drawLine(col, Offset(w - 26.dp.toPx(), h), Offset(w - 26.dp.toPx() - len, h), sw, StrokeCap.Round)
                    drawLine(col, Offset(w, 18.dp.toPx()), Offset(w, 18.dp.toPx() + len), sw, StrokeCap.Round)
                    drawLine(col, Offset(0f, h - 18.dp.toPx()), Offset(0f, h - 18.dp.toPx() - len), sw, StrokeCap.Round)
                }
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            // ---- header ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 26.dp, end = 14.dp, top = 14.dp, bottom = 10.dp)
                    .graphicsLayer { alpha = contentA },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(46.dp, 34.dp).clip(MechaSlant).background(accent), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title.uppercase(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(shadow = Shadow(accent.copy(alpha = 0.7f), blurRadius = 22f))
                    )
                    if (subtitle != null) {
                        Text(subtitle, color = accent, fontSize = 10.sp, letterSpacing = 2.sp, maxLines = 1)
                    }
                }
                IconKey(Icons.Rounded.Close, "Close", active = false, accent = accent, size = 38, onClick = close)
            }
            MechaDivider(accent, Modifier.graphicsLayer { alpha = contentA })

            // ---- isi ----
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 26.dp, vertical = 12.dp)
                    .graphicsLayer {
                        alpha = contentA
                        translationY = (1f - contentA) * 14.dp.toPx()
                    }
            ) { content(close) }

            if (footer != null) {
                MechaDivider(accent, Modifier.graphicsLayer { alpha = contentA })
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 26.dp, end = 30.dp, top = 8.dp, bottom = 12.dp)
                        .graphicsLayer { alpha = contentA },
                    verticalAlignment = Alignment.CenterVertically
                ) { footer(close) }
            }
        }
    }
}

/** Garis pemisah dengan kilatan cahaya yang berlari. */
@Composable
internal fun MechaDivider(accent: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "div")
    val x by t.animateFloat(-0.2f, 1.2f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "divX")
    Canvas(modifier.fillMaxWidth().height(2.dp)) {
        drawRect(accent.copy(alpha = 0.22f))
        val cx = size.width * x
        drawRect(
            Brush.horizontalGradient(
                listOf(Color.Transparent, accent, Color.Transparent),
                startX = cx - 90.dp.toPx(), endX = cx + 90.dp.toPx()
            )
        )
    }
}

@Composable
internal fun MechaSectionLabel(text: String, accent: Color, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp, 8.dp).clip(MechaSlant).background(accent))
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), color = accent, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
    }
}

// ==================================== kontrol =============================================

@Composable
internal fun MechaSwitch(checked: Boolean, accent: Color) {
    val x by animateDpAsState(if (checked) 25.dp else 3.dp, spring(dampingRatio = 0.45f, stiffness = 520f), label = "swX")
    val track by animateColorAsState(if (checked) accent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.10f), tween(180), label = "swT")
    val line by animateColorAsState(if (checked) accent else Color.White.copy(alpha = 0.28f), tween(180), label = "swL")
    val thumb by animateColorAsState(if (checked) accent else Color.White.copy(alpha = 0.6f), tween(180), label = "swK")
    Box(Modifier.size(50.dp, 22.dp).clip(MechaSlant).background(track).border(1.dp, line, MechaSlant)) {
        Box(Modifier.offset(x = x, y = 3.dp).size(22.dp, 16.dp).clip(MechaSlant).background(thumb))
    }
}

@Composable
internal fun MechaToggleTile(
    icon: ImageVector?, title: String, desc: String?, checked: Boolean, accent: Color,
    modifier: Modifier = Modifier, onChange: (Boolean) -> Unit
) {
    val bg by animateColorAsState(if (checked) accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f), tween(220), label = "tgBg")
    val bd by animateColorAsState(if (checked) accent else Color.White.copy(alpha = 0.12f), tween(220), label = "tgBd")
    Row(
        modifier
            .mechaClick(0.97f) { onChange(!checked) }
            .clip(MechaChamfer)
            .background(bg)
            .border(1.dp, bd, MechaChamfer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (checked) accent else Color.White.copy(alpha = 0.55f), modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
            if (desc != null) {
                Text(desc, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, lineHeight = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        MechaSwitch(checked, accent)
    }
}

@Composable
internal fun MechaChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) accent else Color.White.copy(alpha = 0.06f), tween(200), label = "chipBg")
    Box(
        Modifier
            .mechaClick(0.9f, onClick = onClick)
            .clip(MechaSlant)
            .background(bg)
            .border(1.dp, if (selected) accent else Color.White.copy(alpha = 0.14f), MechaSlant)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, color = if (selected) Color.Black else Color.White.copy(alpha = 0.75f),
            fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp
        )
    }
}

@Composable
internal fun MechaButton(
    label: String, icon: ImageVector?, accent: Color, modifier: Modifier = Modifier,
    filled: Boolean = true, danger: Boolean = false, onClick: () -> Unit
) {
    val base = if (danger) MechaDanger else accent
    val fill = if (filled) Brush.horizontalGradient(listOf(lerp(base, Color.Black, 0.05f), lerp(base, Color.Black, 0.5f)))
    else Brush.horizontalGradient(listOf(base.copy(alpha = 0.10f), base.copy(alpha = 0.10f)))
    Row(
        modifier
            .height(44.dp)
            .mechaClick(0.94f, onClick = onClick)
            .clip(MechaSlant)
            .background(fill)
            .border(1.dp, if (filled) Color.White.copy(alpha = 0.35f) else base.copy(alpha = 0.7f), MechaSlant)
            .padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label.uppercase(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, maxLines = 1)
    }
}

/** Slider segmen: batang miring yang menyala, thumb berpegas. Bisa diketuk dan digeser. */
@Composable
internal fun MechaSlider(
    value: Float, onValueChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>,
    accent: Color, modifier: Modifier = Modifier
) {
    val cb by rememberUpdatedState(onValueChange)
    val frac = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    val af by animateFloatAsState(frac, spring(dampingRatio = 0.75f, stiffness = 650f), label = "slF")
    fun set(x: Float, w: Int) {
        val f = (x / w.coerceAtLeast(1)).coerceIn(0f, 1f)
        cb(range.start + f * (range.endInclusive - range.start))
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(30.dp)
            .pointerInput(Unit) { detectTapGestures { set(it.x, size.width) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(onDragStart = { set(it.x, size.width) }) { c, _ -> set(c.position.x, size.width) }
            }
    ) {
        val n = 30
        val step = size.width / n
        val top = 6.dp.toPx()
        val bot = size.height - 6.dp.toPx()
        val slant = 4.dp.toPx()
        for (i in 0 until n) {
            val on = (i + 0.5f) / n <= af
            drawLine(
                if (on) accent else Color.White.copy(alpha = 0.13f),
                Offset(i * step + slant + step * 0.4f, top), Offset(i * step + step * 0.4f, bot),
                strokeWidth = (step * 0.5f), cap = StrokeCap.Butt
            )
        }
        val tx = size.width * af
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), Color.Transparent), Offset(tx, size.height / 2), 18.dp.toPx()), 18.dp.toPx(), Offset(tx, size.height / 2))
        drawLine(Color.White, Offset(tx + 3.dp.toPx(), 1.dp.toPx()), Offset(tx - 3.dp.toPx(), size.height - 1.dp.toPx()), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
internal fun MechaSearchField(
    value: String, onValueChange: (String) -> Unit, placeholder: String, accent: Color, modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val bd by animateColorAsState(if (focused) accent else Color.White.copy(alpha = 0.16f), tween(200), label = "sfBd")
    Row(
        modifier
            .height(42.dp)
            .clip(MechaChamfer)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, bd, MechaChamfer)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, null, tint = if (focused) accent else Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = value, onValueChange = onValueChange, singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = SolidColor(accent),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
            )
            if (value.isEmpty()) Text(placeholder, color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp)
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Rounded.Close, null, tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp).clickable { onValueChange("") }
            )
        }
    }
}

/** Tab dengan indikator yang meluncur berpegas. */
@Composable
internal fun MechaTabs(labels: List<String>, selected: Int, accent: Color, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().height(40.dp)) {
        val tabW = maxWidth / labels.size
        val x by animateDpAsState(tabW * selected, spring(dampingRatio = 0.6f, stiffness = 420f), label = "tabX")
        Box(Modifier.fillMaxSize().clip(MechaSlant).background(Color.White.copy(alpha = 0.05f)).border(1.dp, Color.White.copy(alpha = 0.10f), MechaSlant))
        Box(Modifier.offset(x = x).width(tabW).fillMaxSize().clip(MechaSlant).background(accent))
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { i, label ->
                val c by animateColorAsState(if (i == selected) Color.Black else Color.White.copy(alpha = 0.65f), tween(200), label = "tabC")
                Box(Modifier.width(tabW).fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null
                ) { onSelect(i) }, contentAlignment = Alignment.Center) {
                    Text(label.uppercase(), color = c, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                }
            }
        }
    }
}

/** Isi tab yang bertukar dengan geser + pudar searah perpindahan tab. */
@Composable
internal fun <T : Comparable<T>> MechaSlideSwitch(target: T, content: @Composable (T) -> Unit) {
    AnimatedContent(
        targetState = target,
        transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            (slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 6 * dir } + fadeIn(tween(220))) togetherWith
                (slideOutHorizontally(tween(180)) { -it / 8 * dir } + fadeOut(tween(120)))
        },
        label = "mechaSwitch"
    ) { content(it) }
}

/** Baris status izin: lampu LED berkedip jika belum aktif. */
@Composable
internal fun MechaStatusRow(
    title: String, desc: String, ok: Boolean, accent: Color,
    actionLabel: String, modifier: Modifier = Modifier, onClick: (() -> Unit)?
) {
    val inf = rememberInfiniteTransition(label = "led")
    val blink by inf.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "blink")
    val led = if (ok) MechaOk else MechaDanger
    Row(
        modifier
            .fillMaxWidth()
            .mechaClick(0.98f, enabled = !ok && onClick != null) { onClick?.invoke() }
            .clip(MechaChamfer)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, if (ok) Color.White.copy(alpha = 0.1f) else MechaDanger.copy(alpha = 0.45f), MechaChamfer)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(10.dp).drawBehind {
                drawCircle(led.copy(alpha = if (ok) 0.3f else 0.35f * blink), radius = size.minDimension * 1.3f)
                drawCircle(led.copy(alpha = if (ok) 1f else blink))
            }
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(desc, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, lineHeight = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (!ok && onClick != null) {
            Spacer(Modifier.width(8.dp))
            Text(actionLabel.uppercase(), color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        }
    }
}
