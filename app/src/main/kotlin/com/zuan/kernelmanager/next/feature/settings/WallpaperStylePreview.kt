/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalHazeMaterialsApi::class
)

package com.zuan.kernelmanager.next.feature.settings

import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.zuan.kernelmanager.next.R
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.zuan.kernelmanager.next.feature.dashboard.components.AvatarPreviewContent
import com.zuan.kernelmanager.next.feature.dashboard.components.AvatarWithDecoration
import com.zuan.kernelmanager.next.feature.dashboard.components.rememberProfileMediaSnapshot
import com.zuan.kernelmanager.next.ui.components.AppBarBannerType
import com.zuan.kernelmanager.next.ui.components.LocalZkmLiquidBackdrop
import com.zuan.kernelmanager.next.ui.components.WeatherEffectOverlay
import com.zuan.kernelmanager.next.ui.components.ZkmAppBarBanner
import com.zuan.kernelmanager.next.ui.components.ZkmHazeStyleCache
import com.zuan.kernelmanager.next.ui.components.ZkmLiquidCardTintAlpha
import com.zuan.kernelmanager.next.ui.components.ZkmLiquidGlassSpec
import com.zuan.kernelmanager.next.ui.components.ZkmSlider
import com.zuan.kernelmanager.next.ui.components.ZkmSwitch
import com.zuan.kernelmanager.next.ui.components.isLiquidGlassSupported
import com.zuan.kernelmanager.next.ui.components.zkmCustomBackgroundCardColor
import com.zuan.kernelmanager.next.ui.components.zkmLiquidGlassCard
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundContentColor
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundSubContentColor
import com.zuan.kernelmanager.next.ui.theme.ZkmIconShapeStyle
import com.zuan.kernelmanager.next.ui.theme.resolve
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi

// =============================================================================
// LIVE PREVIEW LAYAR OVERALL
//
// Satu "ponsel mini" yang merender ulang layar Overall memakai SEMUA pengaturan
// yang sedang diedit secara realtime:
//   wallpaper (+blur/saturasi/overlay/cuaca) · appbar progressive blur / banner ·
//   kartu Frosted Glass (Haze) · kartu Android Liquid Glass (Kyant) · font ·
//   bentuk ikon · avatar decoration · toggle · slider · dialog · tema gelap/terang.
//
// Semua ukuran di bawah ditulis dalam "dp desain" (ponsel 380 x 800 dp) lalu
// dikali PREVIEW_SCALE, jadi proporsi kartu, radius blur dan radius liquid glass
// ikut mengecil secara konsisten.
// =============================================================================

private const val PREVIEW_SCALE = 0.52f
private const val PHONE_W = 380f
private const val PHONE_H = 800f

private fun pv(v: Float): Dp = (v * PREVIEW_SCALE).dp
private fun psp(v: Float): TextUnit = (v * PREVIEW_SCALE).sp

private fun TextStyle.pv(designSp: Float, weight: FontWeight? = null): TextStyle =
    copy(
        fontSize = psp(designSp),
        lineHeight = TextUnit.Unspecified,
        fontWeight = weight ?: fontWeight
    )

/** Fokus preview: halaman detail yang sedang dibuka menyorot bagian terkait. */
enum class PreviewFocus { NONE, DIALOG }

/** Semua yang dibutuhkan satu kartu pratinjau agar bergaya sama dengan Overall. */
private class PreviewGlass(
    val glassOn: Boolean,
    val liquid: Boolean,
    val liquidSpec: ZkmLiquidGlassSpec,
    val haze: HazeState,
    val hazeStyle: HazeStyle,
    val backdrop: Backdrop,
    val liquidTint: Color,
    val scrim: Color,
    val solid: Color,
    val border: Color,
)

private fun Modifier.pvCard(g: PreviewGlass, corner: Dp, forceCorner: Boolean = false): Modifier {
    val liquidCorner = if (forceCorner) corner else g.liquidSpec.cornerRadius
    return when {
        g.glassOn && g.liquid -> this.zkmLiquidGlassCard(
            backdrop = g.backdrop,
            spec = g.liquidSpec.copy(cornerRadius = liquidCorner),
            tint = g.liquidTint,
            borderColor = g.border,
        )
        g.glassOn -> {
            val shape = RoundedCornerShape(corner)
            this.clip(shape)
                .hazeEffect(state = g.haze, style = g.hazeStyle)
                .background(g.scrim, shape)
                .border(1.dp, g.border, shape)
        }
        else -> {
            val shape = RoundedCornerShape(corner)
            this.clip(shape).background(g.solid, shape)
        }
    }
}

/** Menskalakan komponen asli (ZkmSwitch / ZkmSlider) tanpa mengubah logika gayanya. */
@Composable
private fun ScaledBox(
    naturalWidth: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, _ ->
        val w = naturalWidth.roundToPx()
        val placeable = measurables.first().measure(
            Constraints(minWidth = w, maxWidth = w, minHeight = 0, maxHeight = Constraints.Infinity)
        )
        val outW = (placeable.width * PREVIEW_SCALE).toInt()
        val outH = (placeable.height * PREVIEW_SCALE).toInt()
        layout(outW, outH) {
            placeable.placeWithLayer(0, 0) {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = PREVIEW_SCALE
                scaleY = PREVIEW_SCALE
            }
        }
    }
}

// -----------------------------------------------------------------------------
// STAGE: panggung preview di bagian atas layar (bisa diperkecil)
// -----------------------------------------------------------------------------

@Composable
fun PreviewStage(
    viewModel: WallpaperStyleViewModel,
    bgSaturation: Float,
    blurStrength: Float,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    focus: PreviewFocus,
    modifier: Modifier = Modifier
) {
    val stageHeight by animateDpAsState(
        targetValue = if (expanded) pv(PHONE_H) + 64.dp else 232.dp,
        animationSpec = tween(380),
        label = "PreviewStageHeight"
    )
    val scheme = MaterialTheme.colorScheme

    // Gradien menutup juga area status bar; isi stage digeser di bawahnya.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        scheme.primaryContainer.copy(alpha = 0.45f),
                        scheme.background
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .height(stageHeight)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 52.dp)
            ) {
                OverallLivePreview(
                    viewModel = viewModel,
                    bgSaturation = bgSaturation,
                    blurStrength = blurStrength,
                    focus = focus
                )
            }

            // Pil "LIVE" + tombol perbesar/perkecil
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LivePill()
                Spacer(Modifier.weight(1f))
                com.zuan.kernelmanager.next.feature.help.ZkmHelpButton(
                    com.zuan.kernelmanager.next.ui.navigation.NavigationRoute.WallpaperStyle.route,
                    tint = scheme.onSurface
                )
                Surface(
                    onClick = onToggleExpanded,
                    shape = CircleShape,
                    color = scheme.surfaceContainerHigh.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Rounded.UnfoldLess else Icons.Rounded.UnfoldMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = scheme.onSurface
                        )
                        Text(
                            text = stringResource(
                                if (expanded) R.string.wall_studio_compact else R.string.wall_studio_full_preview
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * Panel preview untuk landscape / PC mode: ponsel mini di kolom kiri, otomatis diskalakan
 * supaya SELURUH ponsel muat di tinggi layar (stage portrait memakai tinggi tetap dan akan
 * terpotong di layar 16:9). Tidak ada tombol compact/full karena selalu muat.
 */
@Composable
fun PreviewSidePane(
    viewModel: WallpaperStyleViewModel,
    bgSaturation: Float,
    blurStrength: Float,
    focus: PreviewFocus,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(
                        scheme.primaryContainer.copy(alpha = 0.45f),
                        scheme.background
                    )
                )
            )
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 60.dp, bottom = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            FitToBox {
                OverallLivePreview(
                    viewModel = viewModel,
                    bgSaturation = bgSaturation,
                    blurStrength = blurStrength,
                    focus = focus
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LivePill()
            Spacer(Modifier.weight(1f))
            com.zuan.kernelmanager.next.feature.help.ZkmHelpButton(
                com.zuan.kernelmanager.next.ui.navigation.NavigationRoute.WallpaperStyle.route,
                tint = scheme.onSurface
            )
        }
    }
}

/** Mengukur konten pada ukuran aslinya lalu menskalakannya agar muat di ruang yang tersedia. */
@Composable
private fun FitToBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val placeable = measurables.first().measure(Constraints())
        val scale = minOf(
            constraints.maxWidth.toFloat() / placeable.width,
            constraints.maxHeight.toFloat() / placeable.height,
            1.5f
        ).coerceAtLeast(0.1f)
        val outW = (placeable.width * scale).toInt()
        val outH = (placeable.height * scale).toInt()
        layout(outW, outH) {
            placeable.placeWithLayer(0, 0) {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = scale
                scaleY = scale
            }
        }
    }
}

@Composable
private fun LivePill() {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "LivePulse")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "LivePulseAlpha"
    )
    Surface(
        shape = CircleShape,
        color = scheme.surfaceContainerHigh.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(Color(0xFF34C759).copy(alpha = pulse), CircleShape)
            )
            Text(
                text = stringResource(R.string.wall_studio_live),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurface
            )
        }
    }
}

// -----------------------------------------------------------------------------
// PONSEL MINI
// -----------------------------------------------------------------------------

@Composable
fun OverallLivePreview(
    viewModel: WallpaperStyleViewModel,
    bgSaturation: Float,
    blurStrength: Float,
    focus: PreviewFocus,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f

    // --- State dari pengaturan (semua realtime) ---
    val bgType by viewModel.bgType.collectAsState()
    val bgUri by viewModel.backgroundImageUri.collectAsState()
    val weatherEffect by viewModel.weatherEffect.collectAsState()
    val weatherIntensity by viewModel.weatherIntensity.collectAsState()

    val isCardBlur by viewModel.isCardBlur.collectAsState()
    val cardDarkness by viewModel.cardDarkness.collectAsState()
    val cardGlassStyle by viewModel.cardGlassStyle.collectAsState()
    val liquidCorner by viewModel.liquidCornerRadius.collectAsState()
    val liquidBlur by viewModel.liquidBlurRadius.collectAsState()
    val liquidRefHeight by viewModel.liquidRefractionHeight.collectAsState()
    val liquidRefAmount by viewModel.liquidRefractionAmount.collectAsState()
    val liquidChroma by viewModel.liquidChromaticAberration.collectAsState()

    val bannerEnabled by viewModel.appBarBannerEnabled.collectAsState()
    val bannerUriString by viewModel.appBarBannerUri.collectAsState()
    val bannerTypeKey by viewModel.appBarBannerType.collectAsState()
    val bannerDim by viewModel.appBarBannerDim.collectAsState()

    val avatarDeco by viewModel.avatarDecoration.collectAsState()
    val iconShapeStyle by viewModel.iconShapeStyle.collectAsState()
    val dialogStyle by viewModel.dialogStyle.collectAsState()

    val isCustomBg = bgType == BgType.GALLERY && bgUri != null
    val hasBanner = bannerEnabled && bannerUriString != null

    val onSurface = if (isCustomBg) zkmOnCustomBackgroundContentColor(isDark) else scheme.onSurface
    val onSurfaceVariant = if (isCustomBg) zkmOnCustomBackgroundSubContentColor(isDark) else scheme.onSurfaceVariant

    // --- Haze + backdrop milik preview (terpisah dari backdrop app) ---
    val hazeState = remember { HazeState() }
    val backdrop = rememberLayerBackdrop()

    val liquidActive = isCardBlur && cardGlassStyle == CardGlassStyle.LIQUID_GLASS && isLiquidGlassSupported()
    val hazeCardStyle = ZkmHazeStyleCache.of(
        blurRadius = pv(20f),
        noiseFactor = 0.1f,
    )
    val glass = PreviewGlass(
        glassOn = isCardBlur,
        liquid = liquidActive,
        liquidSpec = ZkmLiquidGlassSpec(
            cornerRadius = pv(liquidCorner),
            blurRadius = pv(liquidBlur),
            refractionHeight = pv(liquidRefHeight),
            refractionAmount = pv(liquidRefAmount),
            chromaticAberration = liquidChroma,
        ),
        haze = hazeState,
        hazeStyle = hazeCardStyle,
        backdrop = backdrop,
        liquidTint = scheme.surfaceContainer.copy(alpha = ZkmLiquidCardTintAlpha),
        scrim = if (isDark) Color.Black.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.30f),
        solid = if (isCustomBg) zkmCustomBackgroundCardColor(isDark, cardDarkness) else scheme.surfaceContainer,
        border = Color.White.copy(alpha = if (isDark) 0.18f else 0.45f),
    )

    val phoneShape = RoundedCornerShape(pv(46f))
    val profileMedia = rememberProfileMediaSnapshot()

    CompositionLocalProvider(LocalZkmLiquidBackdrop provides backdrop) {
        Box(
            modifier = modifier
                .size(pv(PHONE_W), pv(PHONE_H))
                .shadow(18.dp, phoneShape, ambientColor = Color.Black, spotColor = Color.Black)
                .border(
                    width = 3.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            scheme.outline.copy(alpha = 0.9f),
                            scheme.outlineVariant.copy(alpha = 0.6f)
                        )
                    ),
                    shape = phoneShape
                )
                .clip(phoneShape)
                .background(scheme.background)
        ) {
            // LAYER 0 — wallpaper (direkam sebagai backdrop liquid + sumber haze)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
                    .hazeSource(state = hazeState, zIndex = 0f)
            ) {
                BackgroundRendererWithEffects(
                    viewModel = viewModel,
                    bgSaturation = bgSaturation,
                    blurStrength = blurStrength * PREVIEW_SCALE
                )
                val visibleWeather =
                    if (isCustomBg || weatherEffect == WeatherEffect.NONE || weatherEffect == WeatherEffect.SNOW) {
                        weatherEffect
                    } else {
                        WeatherEffect.NONE
                    }
                WeatherEffectOverlay(
                    effect = visibleWeather,
                    intensity = weatherIntensity,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // LAYER 1 — konten dashboard (juga sumber haze supaya app bar mem-blur-nya)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState, zIndex = 1f)
                    .padding(horizontal = pv(16f)),
                verticalArrangement = Arrangement.spacedBy(pv(12f))
            ) {
                Spacer(Modifier.height(pv(104f)))

                // Kartu Processor
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pvCard(glass, pv(26f))
                        .padding(pv(16f)),
                    verticalArrangement = Arrangement.spacedBy(pv(12f))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PreviewIconTile(Icons.Rounded.Memory, iconShapeStyle, size = 44f)
                        Spacer(Modifier.width(pv(12f)))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Processor",
                                style = MaterialTheme.typography.titleMedium.pv(17f, FontWeight.Bold),
                                color = onSurface,
                                maxLines = 1
                            )
                            Text(
                                "Snapdragon 8 Elite · 8 cores",
                                style = MaterialTheme.typography.bodySmall.pv(12f),
                                color = onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            "4.32 GHz",
                            style = MaterialTheme.typography.titleSmall.pv(14f, FontWeight.SemiBold),
                            color = scheme.primary,
                            maxLines = 1
                        )
                    }
                    CoreBars(color = scheme.primary, track = onSurface.copy(alpha = 0.12f))
                }

                // Memory + Battery
                Row(horizontalArrangement = Arrangement.spacedBy(pv(12f))) {
                    PreviewStatCard(
                        modifier = Modifier.weight(1f).pvCard(glass, pv(24f)),
                        icon = Icons.Rounded.Speed,
                        iconShape = iconShapeStyle,
                        label = "Memory",
                        value = "12.4 GB",
                        progress = 0.68f,
                        onSurface = onSurface,
                        onSurfaceVariant = onSurfaceVariant,
                        accent = scheme.primary
                    )
                    PreviewStatCard(
                        modifier = Modifier.weight(1f).pvCard(glass, pv(24f)),
                        icon = Icons.Rounded.BatteryChargingFull,
                        iconShape = iconShapeStyle,
                        label = "Battery",
                        value = "84 %",
                        progress = 0.84f,
                        onSurface = onSurface,
                        onSurfaceVariant = onSurfaceVariant,
                        accent = scheme.tertiary
                    )
                }

                // Kartu kontrol: memperlihatkan Toggle + Slider asli
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pvCard(glass, pv(26f))
                        .padding(horizontal = pv(16f), vertical = pv(14f)),
                    verticalArrangement = Arrangement.spacedBy(pv(8f))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Performance mode",
                                style = MaterialTheme.typography.titleSmall.pv(15f, FontWeight.Bold),
                                color = onSurface,
                                maxLines = 1
                            )
                            Text(
                                "Boost clocks while gaming",
                                style = MaterialTheme.typography.bodySmall.pv(11f),
                                color = onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        ScaledBox(naturalWidth = 64.dp) {
                            ZkmSwitch(checked = true, onCheckedChange = null)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "GPU limit",
                            style = MaterialTheme.typography.labelMedium.pv(12f),
                            color = onSurfaceVariant,
                            modifier = Modifier.width(pv(70f)),
                            maxLines = 1
                        )
                        ScaledBox(naturalWidth = 230.dp) {
                            ZkmSlider(value = 0.62f, onValueChange = {}, enabled = false)
                        }
                    }
                }

                // Kartu thermal dengan sparkline
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pvCard(glass, pv(26f))
                        .padding(pv(16f)),
                    verticalArrangement = Arrangement.spacedBy(pv(8f))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PreviewIconTile(Icons.Rounded.Thermostat, iconShapeStyle, size = 36f)
                        Spacer(Modifier.width(pv(10f)))
                        Text(
                            "Thermal",
                            style = MaterialTheme.typography.titleSmall.pv(15f, FontWeight.Bold),
                            color = onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "38.4 °C",
                            style = MaterialTheme.typography.titleSmall.pv(14f, FontWeight.SemiBold),
                            color = scheme.secondary
                        )
                    }
                    Sparkline(
                        color = scheme.secondary,
                        modifier = Modifier.fillMaxWidth().height(pv(44f))
                    )
                }
            }

            // LAYER 2 — app bar: progressive blur ATAU banner progressive
            PreviewAppBar(
                modifier = Modifier.align(Alignment.TopCenter),
                hazeState = hazeState,
                isDark = isDark,
                isCustomBg = isCustomBg,
                hasBanner = hasBanner,
                bannerUri = bannerUriString,
                bannerTypeKey = bannerTypeKey,
                bannerDim = bannerDim,
                onSurface = if (hasBanner) Color.White else onSurface,
                onSurfaceVariant = if (hasBanner) Color.White.copy(alpha = 0.78f) else onSurfaceVariant,
                avatar = {
                    AvatarWithDecoration(
                        style = avatarDeco,
                        modifier = Modifier.size(pv(64f))
                    ) {
                        AvatarPreviewContent(
                            media = profileMedia,
                            live = false,
                            placeholderBg = scheme.primaryContainer,
                            placeholderTint = scheme.onPrimaryContainer,
                            iconSize = pv(22f)
                        )
                    }
                }
            )

            // LAYER 3 — capsule nav melayang (mengikuti gaya kaca kartu)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = pv(22f))
                    .width(pv(250f))
                    .height(pv(58f))
                    .pvCard(glass, pv(29f), forceCorner = true)
                    .padding(horizontal = pv(8f)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val navIcons = listOf(Icons.Rounded.Home, Icons.Rounded.Apps, Icons.Rounded.Code, Icons.Rounded.Settings)
                navIcons.forEachIndexed { i, icon ->
                    Box(
                        modifier = Modifier
                            .size(pv(42f))
                            .clip(CircleShape)
                            .background(if (i == 0) scheme.primaryContainer.copy(alpha = 0.9f) else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier.size(pv(22f)),
                            tint = if (i == 0) scheme.onPrimaryContainer else onSurfaceVariant
                        )
                    }
                }
            }

            // LAYER 4 — dialog (hanya saat halaman detail Dialog dibuka)
            if (focus == PreviewFocus.DIALOG) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.38f)),
                    contentAlignment = Alignment.Center
                ) {
                    val dialogShape = RoundedCornerShape(pv(32f))
                    val dialogMod = if (dialogStyle == DialogStyle.LIQUID_GLASS && isLiquidGlassSupported()) {
                        Modifier.zkmLiquidGlassCard(
                            backdrop = backdrop,
                            spec = glass.liquidSpec.copy(cornerRadius = pv(32f)),
                            tint = scheme.surfaceContainer.copy(alpha = 0.22f),
                        )
                    } else {
                        Modifier
                            .clip(dialogShape)
                            .hazeEffect(
                                state = hazeState,
                                style = ZkmHazeStyleCache.of(
                                    blurRadius = pv(24f),
                                    tint = HazeTint(Color.Black.copy(alpha = 0.35f)),
                                    noiseFactor = 0.1f
                                )
                            )
                            .background(scheme.surface.copy(alpha = 0.55f), dialogShape)
                            .border(1.dp, Color.White.copy(alpha = 0.18f), dialogShape)
                    }
                    Column(
                        modifier = Modifier
                            .width(pv(290f))
                            .then(dialogMod)
                            .padding(pv(22f)),
                        verticalArrangement = Arrangement.spacedBy(pv(8f))
                    ) {
                        Text(
                            "Reboot device?",
                            style = MaterialTheme.typography.titleMedium.pv(19f, FontWeight.Bold),
                            color = scheme.onSurface
                        )
                        Text(
                            "Changes will be applied after the next restart.",
                            style = MaterialTheme.typography.bodySmall.pv(13f),
                            color = scheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(pv(6f)))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Cancel",
                                style = MaterialTheme.typography.labelLarge.pv(13f),
                                color = scheme.primary
                            )
                            Spacer(Modifier.width(pv(18f)))
                            Text(
                                "Reboot",
                                style = MaterialTheme.typography.labelLarge.pv(13f, FontWeight.Bold),
                                color = scheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// APP BAR PREVIEW
// -----------------------------------------------------------------------------

@Composable
private fun PreviewAppBar(
    modifier: Modifier,
    hazeState: HazeState,
    isDark: Boolean,
    isCustomBg: Boolean,
    hasBanner: Boolean,
    bannerUri: String?,
    bannerTypeKey: String?,
    bannerDim: Float,
    onSurface: Color,
    onSurfaceVariant: Color,
    avatar: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val overlay = if (isDark) Color.Black else Color.White
    // Sama dengan zkmAppBarHazeStyle, hanya radius blur ikut diskalakan.
    val style = remember(isDark, isCustomBg, scheme.background) {
        if (isCustomBg) {
            HazeStyle(
                backgroundColor = overlay.copy(alpha = 0.3f),
                blurRadius = pv(22f),
                noiseFactor = 0.1f,
                tints = listOf(HazeTint(overlay.copy(alpha = 0.1f))),
            )
        } else {
            HazeStyle(
                backgroundColor = scheme.background.copy(alpha = 0.7f),
                blurRadius = pv(22f),
                noiseFactor = 0.05f,
                tints = listOf(HazeTint(scheme.background.copy(alpha = 0.3f))),
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(pv(150f))
            .hazeEffect(state = hazeState, style = style) {
                progressive = HazeProgressive.verticalGradient(
                    startIntensity = 1f,
                    endIntensity = 0f
                )
            }
    ) {
        if (hasBanner && bannerUri != null) {
            ZkmAppBarBanner(
                uri = Uri.parse(bannerUri),
                type = AppBarBannerType.fromKey(bannerTypeKey),
                modifier = Modifier.matchParentSize(),
                dim = bannerDim
            )
        }

        // status bar mini
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = pv(24f), vertical = pv(12f)),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("09:41", style = MaterialTheme.typography.labelSmall.pv(12f, FontWeight.SemiBold), color = onSurface)
            Box(
                Modifier
                    .width(pv(18f))
                    .height(pv(9f))
                    .clip(RoundedCornerShape(pv(3f)))
                    .background(onSurface.copy(alpha = 0.85f))
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = pv(22f), end = pv(20f), bottom = pv(14f)),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Code,
                        contentDescription = null,
                        modifier = Modifier.size(pv(15f)),
                        tint = onSurfaceVariant
                    )
                    Spacer(Modifier.width(pv(4f)))
                    Text(
                        "ZKM",
                        style = MaterialTheme.typography.labelMedium.pv(12f),
                        color = onSurfaceVariant
                    )
                }
                Text(
                    "Dashboard",
                    style = MaterialTheme.typography.headlineMedium.pv(31f, FontWeight.Bold),
                    color = onSurface,
                    maxLines = 1
                )
            }
            avatar()
        }
    }
}

// -----------------------------------------------------------------------------
// POTONGAN KECIL
// -----------------------------------------------------------------------------

@Composable
private fun PreviewIconTile(icon: ImageVector, shapeStyle: ZkmIconShapeStyle, size: Float) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(pv(size))
            .clip(shapeStyle.resolve(RoundedCornerShape(pv(size * 0.32f))))
            .background(scheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(pv(size * 0.52f)),
            tint = scheme.onPrimaryContainer
        )
    }
}

@Composable
private fun PreviewStatCard(
    modifier: Modifier,
    icon: ImageVector,
    iconShape: ZkmIconShapeStyle,
    label: String,
    value: String,
    progress: Float,
    onSurface: Color,
    onSurfaceVariant: Color,
    accent: Color,
) {
    Column(
        modifier = modifier.padding(pv(14f)),
        verticalArrangement = Arrangement.spacedBy(pv(6f))
    ) {
        PreviewIconTile(icon, iconShape, size = 34f)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.pv(12f),
            color = onSurfaceVariant,
            maxLines = 1
        )
        Text(
            value,
            style = MaterialTheme.typography.titleLarge.pv(21f, FontWeight.Bold),
            color = onSurface,
            maxLines = 1
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(pv(6f))
                .clip(CircleShape)
                .background(onSurface.copy(alpha = 0.12f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(accent)
            )
        }
    }
}

@Composable
private fun CoreBars(color: Color, track: Color) {
    val heights = listOf(0.9f, 0.7f, 0.55f, 0.8f, 0.4f, 0.65f, 0.85f, 0.5f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(pv(34f)),
        horizontalArrangement = Arrangement.spacedBy(pv(6f)),
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(pv(6f)))
                    .background(track),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(h)
                        .clip(RoundedCornerShape(pv(6f)))
                        .background(color)
                )
            }
        }
    }
}

@Composable
private fun Sparkline(color: Color, modifier: Modifier = Modifier) {
    val points = remember { listOf(0.55f, 0.4f, 0.5f, 0.3f, 0.45f, 0.25f, 0.4f, 0.2f, 0.35f, 0.3f) }
    Box(
        modifier = modifier.drawBehind {
            val path = Path()
            val stepX = size.width / (points.size - 1)
            points.forEachIndexed { i, p ->
                val x = stepX * i
                val y = size.height * p
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    )
}
