/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.ui.components

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceIn
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.zuan.kernelmanager.next.ui.navigation.DampedDragAnimation
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
import com.zuan.kernelmanager.next.feature.settings.ToggleStyle
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import java.io.File

/**
 * Switch global ZKM Next. Semua toggle di app memakai komponen ini sehingga
 * style-nya bisa diganti dari Wallpaper Style Screen > Style > Toggle:
 * - MATERIAL_EXPRESSIVE: Switch Material 3 bawaan.
 * - LIQUID_GLASS: track + thumb Android Liquid Glass (Kyant backdrop).
 * - CLASSIC: toggle flat klasik (iOS-like) tanpa efek kaca.
 * - CUSTOM_IMAGE: thumb memakai gambar custom (gambar OFF saat mati,
 *   gambar ON saat hidup) yang dipilih user.
 */
@Composable
fun ZkmSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    thumbContent: (@Composable () -> Unit)? = null,
    colors: SwitchColors = SwitchDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val context = LocalContext.current
    val prefs = remember { SettingsPreference.getInstance(context) }
    val style by prefs.toggleStyle.collectAsStateWithLifecycle()

    when (style) {
        ToggleStyle.MATERIAL_EXPRESSIVE -> androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            thumbContent = thumbContent,
            colors = colors,
            interactionSource = interactionSource,
        )
        ToggleStyle.LIQUID_GLASS -> ZkmLiquidGlassSwitch(checked, onCheckedChange, modifier, enabled)
        ToggleStyle.CLASSIC -> ZkmClassicSwitch(checked, onCheckedChange, modifier, enabled)
        ToggleStyle.CUSTOM_IMAGE -> ZkmCustomImageSwitch(checked, onCheckedChange, modifier, enabled)
    }
}

private val ZkmSwitchTrackWidth = 56.dp
private val ZkmSwitchTrackHeight = 32.dp

@Composable
private fun ZkmSwitchTrack(
    checked: Boolean,
    modifier: Modifier = Modifier,
    trackColor: Color,
    glass: Boolean,
) {
    val backdrop = LocalZkmLiquidBackdrop.current
    val spec = if (glass) rememberZkmLiquidGlassSpec() else null
    val shape = CircleShape
    if (glass && backdrop != null && isLiquidGlassSupported() && spec != null) {
        Box(
            modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    blur(4f.dp.toPx())
                    lens(6f.dp.toPx(), 6f.dp.toPx(), chromaticAberration = spec.chromaticAberration)
                },
                onDrawSurface = { drawRect(trackColor) },
            )
        )
    } else {
        Box(modifier.background(trackColor, shape))
    }
}

@Composable
private fun ZkmSwitchFrame(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier,
    enabled: Boolean,
    glass: Boolean,
    thumb: @Composable (progress: Float, size: Dp) -> Unit,
) {
    val isDark = zkmIsDarkTheme()
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow),
        label = "ZkmSwitchProgress"
    )
    val primary = MaterialTheme.colorScheme.primary
    val offTrack = if (isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.12f)
    val trackColor = lerp(offTrack, primary.copy(alpha = if (glass) 0.55f else 0.9f), progress)

    val thumbSize = 24.dp
    val travel = ZkmSwitchTrackWidth - ZkmSwitchTrackHeight // thumb center travel with padding
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .size(ZkmSwitchTrackWidth, ZkmSwitchTrackHeight)
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(enabled = enabled && onCheckedChange != null) {
                onCheckedChange?.invoke(!checked)
            }
    ) {
        ZkmSwitchTrack(
            checked = checked,
            modifier = Modifier.fillMaxSize().clip(CircleShape),
            trackColor = trackColor,
            glass = glass,
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp)
                .offset(x = with(density) { (travel * progress).toPx().toDp() })
        ) {
            thumb(progress, thumbSize)
        }
    }
}

/**
 * Toggle Android Liquid Glass — port dari LiquidToggle milik Kyant0:
 * thumb kapsul 40x24 yang bisa DI-DRAG, membesar + berubah jadi kaca bening
 * (lens + chromatic aberration) saat ditekan, memantul (damped spring), dan
 * melihat warna track lewat backdrop gabungan (wallpaper + track).
 * Fallback ke toggle klasik bila backdrop tidak tersedia (Android < 13,
 * atau berada di window terpisah seperti dialog / bottom sheet).
 */
@Composable
private fun ZkmLiquidGlassSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier,
    enabled: Boolean,
) {
    val backdrop = LocalZkmLiquidBackdrop.current
    if (backdrop == null || !isLiquidGlassSupported() || !isInHostWindow()) {
        ZkmClassicSwitch(checked, onCheckedChange, modifier, enabled)
        return
    }

    val isDark = zkmIsDarkTheme()
    val accentColor = MaterialTheme.colorScheme.primary
    val trackColor =
        if (isDark) Color(0xFF787880).copy(alpha = 0.36f)
        else Color(0xFF787878).copy(alpha = 0.2f)

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { 20.dp.toPx() }
    val animationScope = rememberCoroutineScope()
    val currentChecked by rememberUpdatedState(checked)
    val currentOnChange by rememberUpdatedState(onCheckedChange)
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }

    val dampedDragAnimation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f,
            onDragStarted = {},
            onDragStopped = {
                if (didDrag) {
                    fraction = if (targetValue >= 0.5f) 1f else 0f
                    currentOnChange?.invoke(fraction == 1f)
                    didDrag = false
                } else {
                    fraction = if (currentChecked) 0f else 1f
                    currentOnChange?.invoke(fraction == 1f)
                }
            },
            onDrag = { _, dragAmount ->
                if (!didDrag) {
                    didDrag = dragAmount.x != 0f
                }
                val delta = dragAmount.x / dragWidth
                fraction =
                    if (isLtr) (fraction + delta).fastCoerceIn(0f, 1f)
                    else (fraction - delta).fastCoerceIn(0f, 1f)
            }
        )
    }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { fraction }.collectLatest { f -> dampedDragAnimation.updateValue(f) }
    }
    // State dari luar berubah (mis. diset program / ditolak parent): sinkronkan thumb.
    LaunchedEffect(checked) {
        val target = if (checked) 1f else 0f
        if (target != fraction) {
            fraction = target
            dampedDragAnimation.animateToValue(target)
        }
    }

    val trackBackdrop = rememberLayerBackdrop()
    val interactive = enabled && onCheckedChange != null

    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.38f)
            .size(ZkmLiquidSwitchTrackWidth, ZkmLiquidSwitchTrackHeight),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .clip(CircleShape)
                .drawBehind {
                    drawRect(lerp(trackColor, accentColor, dampedDragAnimation.value))
                }
                .size(ZkmLiquidSwitchTrackWidth, ZkmLiquidSwitchTrackHeight)
        )

        Box(
            Modifier
                .graphicsLayer {
                    val f = dampedDragAnimation.value
                    val padding = 2.dp.toPx()
                    translationX =
                        if (isLtr) androidx.compose.ui.util.lerp(padding, padding + dragWidth, f)
                        else androidx.compose.ui.util.lerp(-padding, -(padding + dragWidth), f)
                }
                .semantics { role = Role.Switch }
                .then(if (interactive) dampedDragAnimation.modifier else Modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, trackBackdrop),
                    shape = { CircleShape },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur(8f.dp.toPx() * (1f - progress))
                        lens(
                            5f.dp.toPx() * progress,
                            10f.dp.toPx() * progress,
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
                        val velocity = dampedDragAnimation.velocity / 50f
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

private val ZkmLiquidSwitchTrackWidth = 64.dp
private val ZkmLiquidSwitchTrackHeight = 28.dp

@Composable
private fun ZkmClassicSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier,
    enabled: Boolean,
) {
    ZkmSwitchFrame(checked, onCheckedChange, modifier, enabled, glass = false) { _, size ->
        Box(
            Modifier
                .size(size)
                .background(Color.White, CircleShape)
        )
    }
}

@Composable
private fun ZkmCustomImageSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier,
    enabled: Boolean,
) {
    val context = LocalContext.current
    val prefs = remember { SettingsPreference.getInstance(context) }
    val onPath by prefs.customToggleOnImagePath.collectAsStateWithLifecycle()
    val offPath by prefs.customToggleOffImagePath.collectAsStateWithLifecycle()

    ZkmSwitchFrame(checked, onCheckedChange, modifier, enabled, glass = false) { progress, size ->
        val imagePath = if (progress >= 0.5f) onPath else offPath
        val file = imagePath?.let { File(it) }?.takeIf { it.exists() }
        if (file != null) {
            Image(
                painter = rememberAsyncImagePainter(model = file),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size + 4.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
            )
        } else {
            // Belum ada gambar custom: thumb default mengikuti warna primary saat ON.
            val color = lerp(Color.White, MaterialTheme.colorScheme.primary, progress)
            Box(Modifier.size(size).background(color, CircleShape))
        }
    }
}
