/*
 * Copyright (c) 2026 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.zuan.kernelmanager.next.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import android.graphics.BitmapFactory
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Gaya bentuk (shape) untuk semua kotak ikon di menu aplikasi.
 *
 * Daftar ini sama dengan MaterialShapes (ShapesSample) dari Material 3 Expressive.
 * DEFAULT = bentuk asli aplikasi (rounded square sesuai radius masing-masing layar).
 */
enum class ZkmIconShapeStyle(val label: String) {
    DEFAULT("Default"),
    CIRCLE("Circle"),
    SQUARE("Square"),
    SLANTED("Slanted"),
    ARCH("Arch"),
    FAN("Fan"),
    ARROW("Arrow"),
    SEMI_CIRCLE("SemiCircle"),
    OVAL("Oval"),
    PILL("Pill"),
    TRIANGLE("Triangle"),
    DIAMOND("Diamond"),
    CLAM_SHELL("ClamShell"),
    PENTAGON("Pentagon"),
    GEM("Gem"),
    SUNNY("Sunny"),
    VERY_SUNNY("VerySunny"),
    COOKIE_4("Cookie4Sided"),
    COOKIE_6("Cookie6Sided"),
    COOKIE_7("Cookie7Sided"),
    COOKIE_9("Cookie9Sided"),
    COOKIE_12("Cookie12Sided"),
    GHOSTISH("Ghostish"),
    CLOVER_4("Clover4Leaf"),
    CLOVER_8("Clover8Leaf"),
    BURST("Burst"),
    SOFT_BURST("SoftBurst"),
    BOOM("Boom"),
    SOFT_BOOM("SoftBoom"),
    FLOWER("Flower"),
    PUFFY("Puffy"),
    PUFFY_DIAMOND("PuffyDiamond"),
    PIXEL_CIRCLE("PixelCircle"),
    PIXEL_TRIANGLE("PixelTriangle"),
    BUN("Bun"),
    HEART("Heart"),
    RANDOM("Mix / Random");

    companion object {
        fun fromName(name: String?): ZkmIconShapeStyle =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** Style aktif; di-provide oleh [ZuanKernelManagerTheme] dari preferensi. */
val LocalZkmIconShapeStyle = staticCompositionLocalOf { ZkmIconShapeStyle.DEFAULT }

/** Ubah style menjadi Shape Compose. DEFAULT mengembalikan [fallback]. */
@Composable
fun ZkmIconShapeStyle.resolve(fallback: Shape): Shape = when (this) {
    ZkmIconShapeStyle.DEFAULT -> fallback
    ZkmIconShapeStyle.CIRCLE -> MaterialShapes.Circle.toShape()
    ZkmIconShapeStyle.SQUARE -> MaterialShapes.Square.toShape()
    ZkmIconShapeStyle.SLANTED -> MaterialShapes.Slanted.toShape()
    ZkmIconShapeStyle.ARCH -> MaterialShapes.Arch.toShape()
    ZkmIconShapeStyle.FAN -> MaterialShapes.Fan.toShape()
    ZkmIconShapeStyle.ARROW -> MaterialShapes.Arrow.toShape()
    ZkmIconShapeStyle.SEMI_CIRCLE -> MaterialShapes.SemiCircle.toShape()
    ZkmIconShapeStyle.OVAL -> MaterialShapes.Oval.toShape()
    ZkmIconShapeStyle.PILL -> MaterialShapes.Pill.toShape()
    ZkmIconShapeStyle.TRIANGLE -> MaterialShapes.Triangle.toShape()
    ZkmIconShapeStyle.DIAMOND -> MaterialShapes.Diamond.toShape()
    ZkmIconShapeStyle.CLAM_SHELL -> MaterialShapes.ClamShell.toShape()
    ZkmIconShapeStyle.PENTAGON -> MaterialShapes.Pentagon.toShape()
    ZkmIconShapeStyle.GEM -> MaterialShapes.Gem.toShape()
    ZkmIconShapeStyle.SUNNY -> MaterialShapes.Sunny.toShape()
    ZkmIconShapeStyle.VERY_SUNNY -> MaterialShapes.VerySunny.toShape()
    ZkmIconShapeStyle.COOKIE_4 -> MaterialShapes.Cookie4Sided.toShape()
    ZkmIconShapeStyle.COOKIE_6 -> MaterialShapes.Cookie6Sided.toShape()
    ZkmIconShapeStyle.COOKIE_7 -> MaterialShapes.Cookie7Sided.toShape()
    ZkmIconShapeStyle.COOKIE_9 -> MaterialShapes.Cookie9Sided.toShape()
    ZkmIconShapeStyle.COOKIE_12 -> MaterialShapes.Cookie12Sided.toShape()
    ZkmIconShapeStyle.GHOSTISH -> MaterialShapes.Ghostish.toShape()
    ZkmIconShapeStyle.CLOVER_4 -> MaterialShapes.Clover4Leaf.toShape()
    ZkmIconShapeStyle.CLOVER_8 -> MaterialShapes.Clover8Leaf.toShape()
    ZkmIconShapeStyle.BURST -> MaterialShapes.Burst.toShape()
    ZkmIconShapeStyle.SOFT_BURST -> MaterialShapes.SoftBurst.toShape()
    ZkmIconShapeStyle.BOOM -> MaterialShapes.Boom.toShape()
    ZkmIconShapeStyle.SOFT_BOOM -> MaterialShapes.SoftBoom.toShape()
    ZkmIconShapeStyle.FLOWER -> MaterialShapes.Flower.toShape()
    ZkmIconShapeStyle.PUFFY -> MaterialShapes.Puffy.toShape()
    ZkmIconShapeStyle.PUFFY_DIAMOND -> MaterialShapes.PuffyDiamond.toShape()
    ZkmIconShapeStyle.PIXEL_CIRCLE -> MaterialShapes.PixelCircle.toShape()
    ZkmIconShapeStyle.PIXEL_TRIANGLE -> MaterialShapes.PixelTriangle.toShape()
    ZkmIconShapeStyle.BUN -> MaterialShapes.Bun.toShape()
    ZkmIconShapeStyle.HEART -> MaterialShapes.Heart.toShape()
    ZkmIconShapeStyle.RANDOM -> fallback
}

private fun ZkmIconShapeStyle.polygon(seed: Int = 0): RoundedPolygon? {
    val candidates = listOf(
        MaterialShapes.Circle, MaterialShapes.Square, MaterialShapes.Slanted, MaterialShapes.Arch,
        MaterialShapes.Fan, MaterialShapes.Arrow, MaterialShapes.SemiCircle, MaterialShapes.Oval,
        MaterialShapes.Pill, MaterialShapes.Triangle, MaterialShapes.Diamond, MaterialShapes.ClamShell,
        MaterialShapes.Pentagon, MaterialShapes.Gem, MaterialShapes.Sunny, MaterialShapes.VerySunny,
        MaterialShapes.Cookie4Sided, MaterialShapes.Cookie6Sided, MaterialShapes.Cookie7Sided,
        MaterialShapes.Cookie9Sided, MaterialShapes.Cookie12Sided, MaterialShapes.Ghostish,
        MaterialShapes.Clover4Leaf, MaterialShapes.Clover8Leaf, MaterialShapes.Burst,
        MaterialShapes.SoftBurst, MaterialShapes.Boom, MaterialShapes.SoftBoom, MaterialShapes.Flower,
        MaterialShapes.Puffy, MaterialShapes.PuffyDiamond, MaterialShapes.PixelCircle,
        MaterialShapes.PixelTriangle, MaterialShapes.Bun, MaterialShapes.Heart
    )
    return when (this) {
        ZkmIconShapeStyle.DEFAULT -> RoundedPolygon(4, rounding = CornerRounding(0.18f))
        ZkmIconShapeStyle.RANDOM -> candidates[(seed and Int.MAX_VALUE) % candidates.size]
        else -> when (this) {
            ZkmIconShapeStyle.CIRCLE -> MaterialShapes.Circle; ZkmIconShapeStyle.SQUARE -> MaterialShapes.Square; ZkmIconShapeStyle.SLANTED -> MaterialShapes.Slanted
            ZkmIconShapeStyle.ARCH -> MaterialShapes.Arch; ZkmIconShapeStyle.FAN -> MaterialShapes.Fan; ZkmIconShapeStyle.ARROW -> MaterialShapes.Arrow
            ZkmIconShapeStyle.SEMI_CIRCLE -> MaterialShapes.SemiCircle; ZkmIconShapeStyle.OVAL -> MaterialShapes.Oval; ZkmIconShapeStyle.PILL -> MaterialShapes.Pill
            ZkmIconShapeStyle.TRIANGLE -> MaterialShapes.Triangle; ZkmIconShapeStyle.DIAMOND -> MaterialShapes.Diamond; ZkmIconShapeStyle.CLAM_SHELL -> MaterialShapes.ClamShell
            ZkmIconShapeStyle.PENTAGON -> MaterialShapes.Pentagon; ZkmIconShapeStyle.GEM -> MaterialShapes.Gem; ZkmIconShapeStyle.SUNNY -> MaterialShapes.Sunny
            ZkmIconShapeStyle.VERY_SUNNY -> MaterialShapes.VerySunny; ZkmIconShapeStyle.COOKIE_4 -> MaterialShapes.Cookie4Sided
            ZkmIconShapeStyle.COOKIE_6 -> MaterialShapes.Cookie6Sided; ZkmIconShapeStyle.COOKIE_7 -> MaterialShapes.Cookie7Sided
            ZkmIconShapeStyle.COOKIE_9 -> MaterialShapes.Cookie9Sided; ZkmIconShapeStyle.COOKIE_12 -> MaterialShapes.Cookie12Sided
            ZkmIconShapeStyle.GHOSTISH -> MaterialShapes.Ghostish; ZkmIconShapeStyle.CLOVER_4 -> MaterialShapes.Clover4Leaf
            ZkmIconShapeStyle.CLOVER_8 -> MaterialShapes.Clover8Leaf; ZkmIconShapeStyle.BURST -> MaterialShapes.Burst
            ZkmIconShapeStyle.SOFT_BURST -> MaterialShapes.SoftBurst; ZkmIconShapeStyle.BOOM -> MaterialShapes.Boom
            ZkmIconShapeStyle.SOFT_BOOM -> MaterialShapes.SoftBoom; ZkmIconShapeStyle.FLOWER -> MaterialShapes.Flower; ZkmIconShapeStyle.PUFFY -> MaterialShapes.Puffy
            ZkmIconShapeStyle.PUFFY_DIAMOND -> MaterialShapes.PuffyDiamond; ZkmIconShapeStyle.PIXEL_CIRCLE -> MaterialShapes.PixelCircle
            ZkmIconShapeStyle.PIXEL_TRIANGLE -> MaterialShapes.PixelTriangle; ZkmIconShapeStyle.BUN -> MaterialShapes.Bun; ZkmIconShapeStyle.HEART -> MaterialShapes.Heart
        }
    }
}

private class ZkmMorphShape(
    private val morph: Morph,
    private val progress: Float
) : Shape {
    private val matrix = Matrix()
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        matrix.reset()
        matrix.scale(size.width / 2f, size.height / 2f)
        matrix.translate(1f, 1f)
        val path = morph.toPath(progress).asComposePath()
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

/** Shape with a smooth morph whenever the selected style changes. */
@Composable
fun zkmAnimatedIconShape(default: Shape, randomSeed: Int = 0): Shape {
    val style = LocalZkmIconShapeStyle.current
    val target = remember(style, randomSeed) { style.polygon(randomSeed) }
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(target) {
        if (to !== target) {
            from = to
            to = target
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec = tween(360, easing = FastOutSlowInEasing))
        }
    }

    return if (from != null && to != null && from !== to) {
        ZkmMorphShape(Morph(from!!, to!!), progress.value)
    } else {
        style.resolve(default)
    }
}

/**
 * Pakai ini di SEMUA kotak ikon menu:
 *   .clip(zkmIconShape(RoundedCornerShape(14.dp)))
 * [default] dipakai kalau user memilih "Default".
 */
@Composable
fun zkmIconShape(default: Shape, randomSeed: Int = 0): Shape = LocalZkmIconShapeStyle.current.let {
    if (it == ZkmIconShapeStyle.RANDOM) it.polygon(randomSeed)?.toShape() ?: default else it.resolve(default)
}

/** Animated variant used by menu icon containers. */
@Composable
fun zkmMenuIconShape(default: Shape, randomSeed: Int = 0): Shape = zkmAnimatedIconShape(default, randomSeed)

@Composable
fun zkmMenuIconShapeFor(default: Shape, style: ZkmIconShapeStyle, randomSeed: Int = 0): Shape {
    val target = remember(style, randomSeed) { style.polygon(randomSeed) }
    return if (style == ZkmIconShapeStyle.DEFAULT) default else target?.toShape() ?: default
}

/** Gambar custom (null = pakai icon bawaan). Di-provide oleh [ZuanKernelManagerTheme]. */
val LocalZkmIconImage = staticCompositionLocalOf<ImageBitmap?> { null }

/**
 * Isi kotak ikon menu. Kalau user memasang gambar custom, gambar itu menimpa icon
 * dan memenuhi kotak (tetap terpotong mengikuti shape). Kalau tidak, tampil icon biasa.
 */
@Composable
fun ZkmIconGlyph(
    icon: ImageVector,
    tint: Color,
    iconSize: Dp,
    modifier: Modifier = Modifier
) {
    val image = LocalZkmIconImage.current
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.fillMaxSize().padding(3.dp)
        )
    } else {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = modifier.size(iconSize))
    }
}

/** Per-menu image variant. When [imagePath] is set, it overrides the global custom icon. */
@Composable
fun ZkmIconGlyph(
    icon: ImageVector,
    tint: Color,
    iconSize: Dp,
    imagePath: String?,
    modifier: Modifier = Modifier
) {
    val globalImage = LocalZkmIconImage.current
    val menuImage = produceState<ImageBitmap?>(initialValue = null, key1 = imagePath) {
        value = imagePath?.let { path ->
            withContextCompatDecode(path)
        }
    }.value
    val image = menuImage ?: globalImage
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.fillMaxSize().padding(3.dp)
        )
    } else {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = modifier.size(iconSize))
    }
}

private suspend fun withContextCompatDecode(path: String): ImageBitmap? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
    }

/**
 * Pengganti `Icon(..., modifier = Modifier.padding(N.dp))` di dalam Surface/Box ber-shape.
 * Kalau ada gambar custom, gambar memenuhi container (container sudah di-clip shape).
 */
@Composable
fun ZkmPaddedIcon(
    imageVector: ImageVector,
    tint: Color,
    padding: Dp,
    contentDescription: String? = null
) {
    val image = LocalZkmIconImage.current
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(3.dp)
        )
    } else {
        Icon(imageVector = imageVector, contentDescription = contentDescription, tint = tint, modifier = Modifier.padding(padding))
    }
}

/**
 * Icon "telanjang" (tanpa kotak, misal di kartu SoC). Dengan gambar custom, gambar
 * tampil sedikit lebih besar dan dipotong mengikuti shape pilihan.
 */
@Composable
fun ZkmShapedLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    strokeWidth: Dp = 3.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(zkmIconShape(RoundedCornerShape(10.dp)))
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = color, strokeWidth = strokeWidth, modifier = Modifier.size(size * 0.62f))
    }
}

@Composable
fun ZkmShapedFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(zkmIconShape(RoundedCornerShape(18.dp)))
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
fun ZkmBareIcon(
    imageVector: ImageVector,
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val image = LocalZkmIconImage.current
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.size(size + 12.dp).clip(zkmIconShape(RoundedCornerShape(10.dp)))
        )
    } else {
        Icon(imageVector = imageVector, contentDescription = null, tint = tint, modifier = modifier.size(size))
    }
}

/** Versi Painter (untuk icon dari drawable via painterResource). */
@Composable
fun ZkmIconGlyph(
    painter: Painter,
    tint: Color,
    iconSize: Dp,
    modifier: Modifier = Modifier
) {
    val image = LocalZkmIconImage.current
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.fillMaxSize().padding(3.dp)
        )
    } else {
        Icon(painter = painter, contentDescription = null, tint = tint, modifier = modifier.size(iconSize))
    }
}
