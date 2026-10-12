/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.zuan.kernelmanager.next.ui.navigation.DampedDragAnimation
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.zuan.kernelmanager.next.feature.settings.SettingsPreference
import com.zuan.kernelmanager.next.feature.settings.SliderStyle
import java.io.File
import kotlin.math.roundToInt

/**
 * Slider global ZKM Next. Semua slider di app memakai komponen ini sehingga
 * style-nya bisa diganti dari Wallpaper Style Screen > Style > Slider:
 * - MATERIAL_EXPRESSIVE: Slider Material 3 bawaan.
 * - LIQUID_GLASS: track + thumb Android Liquid Glass (Kyant backdrop).
 * - CLASSIC: slider flat klasik (track tipis + thumb bulat solid).
 * - CUSTOM_IMAGE: thumb memakai gambar custom yang dipilih user.
 *
 * Signature sengaja kompatibel dengan `androidx.compose.material3.Slider`
 * (subset parameter yang dipakai di app) supaya bisa jadi pengganti drop-in.
 */
@Composable
fun ZkmSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val context = LocalContext.current
    val prefs = remember { SettingsPreference.getInstance(context) }
    val style by prefs.sliderStyle.collectAsState()

    when (style) {
        SliderStyle.MATERIAL_EXPRESSIVE -> androidx.compose.material3.Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            colors = colors,
            interactionSource = interactionSource,
        )
        SliderStyle.LIQUID_GLASS -> ZkmLiquidGlassSlider(value, onValueChange, modifier, enabled, valueRange, steps, onValueChangeFinished)
        SliderStyle.CLASSIC -> ZkmCustomSlider(value, onValueChange, modifier, enabled, valueRange, steps, onValueChangeFinished, glass = false, classic = true)
        SliderStyle.CUSTOM_IMAGE -> ZkmCustomSlider(value, onValueChange, modifier, enabled, valueRange, steps, onValueChangeFinished, glass = false, classic = false)
    }
}

/**
 * Slider Android Liquid Glass — port dari LiquidSlider milik Kyant0: track
 * tipis 6dp, thumb kapsul 40x24 yang bisa di-drag, membesar jadi kaca bening
 * (lens + chromatic aberration) saat ditekan, dengan efek damped spring.
 * Tap di track memindahkan nilai dengan animasi. Fallback ke slider klasik
 * bila backdrop tidak tersedia (Android < 13 atau window dialog/bottom sheet).
 */
@Composable
private fun ZkmLiquidGlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChangeFinished: (() -> Unit)?,
) {
    val backdrop = LocalZkmLiquidBackdrop.current
    if (backdrop == null || !isLiquidGlassSupported() || !isInHostWindow()) {
        ZkmCustomSlider(value, onValueChange, modifier, enabled, valueRange, steps, onValueChangeFinished, glass = false, classic = true)
        return
    }

    val isDark = zkmIsDarkTheme()
    val accentColor = MaterialTheme.colorScheme.primary
    val trackColor =
        if (isDark) Color(0xFF787880).copy(alpha = 0.36f)
        else Color(0xFF787878).copy(alpha = 0.2f)

    val trackBackdrop = rememberLayerBackdrop()
    val rangeSize = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val currentValue by rememberUpdatedState(value.coerceIn(valueRange.start, valueRange.endInclusive))
    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentFinished by rememberUpdatedState(onValueChangeFinished)

    fun snap(v: Float): Float {
        val c = v.coerceIn(valueRange.start, valueRange.endInclusive)
        if (steps <= 0) return c
        val stepSize = rangeSize / (steps + 1)
        return (valueRange.start + ((c - valueRange.start) / stepSize).roundToInt() * stepSize)
            .coerceIn(valueRange.start, valueRange.endInclusive)
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .alpha(if (enabled) 1f else 0.38f),
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var didDrag by remember { mutableStateOf(false) }

        val dampedDragAnimation = remember(animationScope, valueRange) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = currentValue,
                valueRange = valueRange.start..valueRange.endInclusive,
                visibilityThreshold = rangeSize * 0.001f,
                initialScale = 1f,
                pressedScale = 1.5f,
                onDragStarted = {},
                onDragStopped = {
                    didDrag = false
                    currentFinished?.invoke()
                },
                onDrag = { _, dragAmount ->
                    if (!didDrag) {
                        didDrag = dragAmount.x != 0f
                    }
                    val delta = rangeSize * (dragAmount.x / trackWidth)
                    val next = if (isLtr) targetValue + delta else targetValue - delta
                    currentOnChange(snap(next))
                }
            )
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentValue }.collectLatest { v ->
                if (dampedDragAnimation.targetValue != v) {
                    dampedDragAnimation.updateValue(v)
                }
            }
        }

        Box(Modifier.layerBackdrop(trackBackdrop)) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(trackColor)
                    .then(
                        if (enabled) Modifier.pointerInput(animationScope, valueRange, steps) {
                            detectTapGestures { position ->
                                val delta = rangeSize * (position.x / trackWidth)
                                val target = snap(
                                    if (isLtr) valueRange.start + delta
                                    else valueRange.endInclusive - delta
                                )
                                dampedDragAnimation.animateToValue(target)
                                currentOnChange(target)
                                currentFinished?.invoke()
                            }
                        } else Modifier
                    )
                    .height(6.dp)
                    .fillMaxWidth()
            )
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(accentColor)
                    .height(6.dp)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val width = (constraints.maxWidth * dampedDragAnimation.progress).fastRoundToInt()
                        layout(width, placeable.height) {
                            placeable.place(0, 0)
                        }
                    }
            )
        }

        Box(
            Modifier
                .graphicsLayer {
                    translationX =
                        (-size.width / 2f + trackWidth * dampedDragAnimation.progress)
                            .fastCoerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f) * if (isLtr) 1f else -1f
                }
                .then(if (enabled) dampedDragAnimation.modifier else Modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, trackBackdrop),
                    shape = { CircleShape },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur(8f.dp.toPx() * (1f - progress))
                        lens(
                            10f.dp.toPx() * progress,
                            14f.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = { Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress) },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(radius = 4f.dp * progress, alpha = progress)
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    }
                )
                .size(40.dp, 24.dp)
        )
    }
}

@Composable
private fun ZkmCustomSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChangeFinished: (() -> Unit)?,
    glass: Boolean,
    classic: Boolean,
) {
    val range = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - valueRange.start) / range).coerceIn(0f, 1f)

    val thumbSize = when {
        glass -> 26.dp
        classic -> 20.dp
        else -> 28.dp
    }
    val trackHeight = if (classic) 4.dp else 10.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .alpha(if (enabled) 1f else 0.38f)
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val thumbPx = with(density) { thumbSize.toPx() }

        fun valueAt(xPx: Float): Float {
            val usable = (widthPx - thumbPx).coerceAtLeast(1f)
            val f = ((xPx - thumbPx / 2f) / usable).coerceIn(0f, 1f)
            var v = valueRange.start + f * range
            if (steps > 0) {
                val stepSize = range / (steps + 1)
                v = valueRange.start + ((v - valueRange.start) / stepSize).roundToInt() * stepSize
            }
            return v.coerceIn(valueRange.start, valueRange.endInclusive)
        }

        val gestureModifier = if (enabled) {
            Modifier
                .pointerInput(valueRange, steps, enabled) {
                    detectTapGestures { offset -> onValueChange(valueAt(offset.x)) }
                }
                .pointerInput(valueRange, steps, enabled) {
                    var dragX = fraction * (widthPx - thumbPx) + thumbPx / 2f
                    detectDragGestures(
                        onDragStart = { start -> dragX = start.x; onValueChange(valueAt(start.x)) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragX += dragAmount.x
                            onValueChange(valueAt(dragX))
                        },
                        onDragEnd = { onValueChangeFinished?.invoke() },
                        onDragCancel = { onValueChangeFinished?.invoke() },
                    )
                }
        } else Modifier

        Box(Modifier.fillMaxSize().then(gestureModifier), contentAlignment = Alignment.CenterStart) {
            // Track
            ZkmSliderTrack(
                fraction = fraction,
                trackHeight = trackHeight,
                glass = glass,
                modifier = Modifier.fillMaxWidth()
            )
            // Thumb
            val thumbOffsetX = with(density) {
                ((widthPx - thumbPx) * fraction).toDp()
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = thumbOffsetX)
            ) {
                ZkmSliderThumb(size = thumbSize, glass = glass, classic = classic)
            }
        }
    }
}

@Composable
private fun ZkmSliderTrack(
    fraction: Float,
    trackHeight: Dp,
    glass: Boolean,
    modifier: Modifier = Modifier,
) {
    val backdrop = LocalZkmLiquidBackdrop.current
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(trackHeight / 2)

    BoxWithConstraints(modifier.height(trackHeight)) {
        val fullWidth = maxWidth
        if (glass && backdrop != null && isLiquidGlassSupported()) {
            // Track belakang: kaca liquid
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            blur(4f.dp.toPx())
                            lens(4f.dp.toPx(), 4f.dp.toPx())
                        },
                        onDrawSurface = { drawRect(Color.White.copy(alpha = 0.18f)) },
                    )
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape
                    )
            )
        }
        // Bagian aktif
        Box(
            Modifier
                .width(fullWidth * fraction)
                .height(trackHeight)
                .background(primary.copy(alpha = if (glass) 0.75f else 1f), shape)
        )
    }
}

@Composable
private fun ZkmSliderThumb(
    size: Dp,
    glass: Boolean,
    classic: Boolean,
) {
    when {
        glass -> {
            val backdrop = LocalZkmLiquidBackdrop.current
            val spec = rememberZkmLiquidGlassSpec()
            val tint = Color.White.copy(alpha = 0.65f)
            if (backdrop != null && isLiquidGlassSupported()) {
                Box(
                    Modifier
                        .size(size)
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { CircleShape },
                            effects = {
                                blur(2f.dp.toPx())
                                lens(4f.dp.toPx(), 4f.dp.toPx(), chromaticAberration = spec.chromaticAberration)
                            },
                            onDrawSurface = { drawRect(tint) },
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                )
            } else {
                Box(
                    Modifier
                        .size(size)
                        .background(tint, CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                )
            }
        }
        classic -> {
            Box(
                Modifier
                    .size(size)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
        else -> {
            // CUSTOM_IMAGE
            val context = LocalContext.current
            val prefs = remember { SettingsPreference.getInstance(context) }
            val thumbPath by prefs.customSliderThumbImagePath.collectAsState()
            val file = thumbPath?.let { File(it) }?.takeIf { it.exists() }
            if (file != null) {
                Image(
                    painter = rememberAsyncImagePainter(model = file),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                )
            } else {
                Box(
                    Modifier
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }
    }
}
