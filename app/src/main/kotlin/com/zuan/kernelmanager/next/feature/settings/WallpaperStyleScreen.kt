/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalHazeMaterialsApi::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.zuan.kernelmanager.next.feature.settings

import com.zuan.kernelmanager.next.ui.components.ConfigureSheetWindowBlur
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorMatrixColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import androidx.navigation.NavController
import coil3.compose.rememberAsyncImagePainter
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.ui.components.VideoWallpaperPlayer
import com.zuan.kernelmanager.next.ui.components.WeatherEffectOverlay
import com.zuan.kernelmanager.next.ui.theme.ThemeMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import com.zuan.kernelmanager.next.ui.theme.zkmIconShape
import com.zuan.kernelmanager.next.ui.adaptive.rememberZkmWindowInfo
import androidx.compose.ui.graphics.Shape
import com.zuan.kernelmanager.next.ui.theme.ZkmIconShapeStyle
import com.zuan.kernelmanager.next.ui.theme.resolve
import com.zuan.kernelmanager.next.ui.theme.ZkmIconGlyph
import com.zuan.kernelmanager.next.ui.navigation.NavigationRoute
import com.zuan.kernelmanager.next.ui.components.ZkmSwitch
import com.zuan.kernelmanager.next.ui.components.ZkmSlider
import com.zuan.kernelmanager.next.feature.dashboard.components.AvatarWithDecoration
import com.zuan.kernelmanager.next.feature.dashboard.components.AvatarPreviewContent
import com.zuan.kernelmanager.next.feature.dashboard.components.ProfileMedia
import com.zuan.kernelmanager.next.feature.dashboard.components.rememberProfileMediaSnapshot
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems

enum class ColorType { PRIMARY, SECONDARY, TERTIARY }

@Composable
fun WallpaperStyleScreen(
    navController: NavController,
    viewModel: WallpaperStyleViewModel = viewModel()
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val isDynamic by viewModel.isDynamicColor.collectAsState()
    val selectedThemeColor by viewModel.currentThemeColor.collectAsState()
    val effectiveColor by viewModel.effectiveThemeColor.collectAsState()

    val isCustomColor by viewModel.isCustomColor.collectAsState()
    val customPrimary by viewModel.customPrimaryColor.collectAsState()
    val customSecondary by viewModel.customSecondaryColor.collectAsState()
    val customTertiary by viewModel.customTertiaryColor.collectAsState()

    val weatherEffect by viewModel.weatherEffect.collectAsState()
    val weatherIntensity by viewModel.weatherIntensity.collectAsState()

    val bgSaturation by viewModel.bgSaturation.collectAsState()
    val blurStrength by viewModel.blurStrength.collectAsState()
    val isBgBlur by viewModel.isBgBlur.collectAsState()
    val bgContrast by viewModel.bgContrast.collectAsState()

    val context = LocalContext.current
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM_DEFAULT -> isSystemInDarkTheme()
    }

    val baseColorScheme = remember(isDynamic, effectiveColor, isDark, isCustomColor) {
        if (isDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (isDark) {
                darkColorScheme(
                    primary = effectiveColor.primary,
                    onPrimary = if (effectiveColor.primary.luminance() > 0.5f) Color.Black else Color.White,
                    primaryContainer = effectiveColor.primary.copy(alpha = 0.3f),
                    onPrimaryContainer = effectiveColor.primary,
                    secondary = effectiveColor.secondary,
                    secondaryContainer = effectiveColor.secondary.copy(alpha = 0.3f),
                    onSecondaryContainer = effectiveColor.secondary,
                    surface = Color(0xFF161B22),
                    surfaceContainer = Color(0xFF1E2430),
                    surfaceContainerHigh = Color(0xFF262E3D),
                    background = Color(0xFF10141B)
                )
            } else {
                lightColorScheme(
                    primary = effectiveColor.primary,
                    onPrimary = if (effectiveColor.primary.luminance() > 0.5f) Color.Black else Color.White,
                    primaryContainer = effectiveColor.primary.copy(alpha = 0.2f),
                    onPrimaryContainer = effectiveColor.primary,
                    secondary = effectiveColor.secondary,
                    surface = Color(0xFFFFFFFF),
                    surfaceContainer = Color(0xFFF3F4F8),
                    surfaceContainerHigh = Color(0xFFE7E9F1),
                    background = Color(0xFFF7F8FA)
                )
            }
        }
    }

    var activeTab by remember { mutableIntStateOf(0) }
    var isEffectsModeOpen by remember { mutableStateOf(false) }
    // Halaman detail (Colors/Fonts/dst.) dibuka di dalam panel, bukan bottom sheet,
    // supaya preview Overall di atas tetap terlihat dan berubah realtime.
    // Detail disimpan PER TAB: tiap tab capsule punya hub + halaman detailnya sendiri.
    val details = remember { mutableStateMapOf<Int, StyleDetail>() }
    val detail: StyleDetail? = details[activeTab]
    var previewExpanded by remember { mutableStateOf(true) }
    val panelHaze = remember { HazeState() }
    var themeTransitionVisible by remember { mutableStateOf(false) }
    var themeTransitionToDark by remember { mutableStateOf(false) }
    var themeToggleCenter by remember { mutableStateOf(Offset.Zero) }
    var themeTransitionGeneration by remember { mutableIntStateOf(0) }
    val themeTransitionAnim = remember { Animatable(0f) }
    val themeTransitionProgress = themeTransitionAnim.value

    var showCustomColorPicker by remember { mutableStateOf(false) }
    var editingColorType by remember { mutableStateOf<ColorType?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.setBackgroundMedia(it) }
    }

    val fontPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importCustomFont(it) }
    }
    val scope = rememberCoroutineScope()

    // Back: tutup halaman detail dulu sebelum keluar layar.
    BackHandler(enabled = detail != null && !isEffectsModeOpen) { details.remove(activeTab) }

    MaterialTheme(
        colorScheme = baseColorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isEffectsModeOpen) {
                EffectsStudioScreen(
                    viewModel = viewModel,
                    weatherEffect = weatherEffect,
                    weatherIntensity = weatherIntensity,
                    bgSaturation = bgSaturation,
                    blurStrength = if (isBgBlur) blurStrength else 0f,
                    onClose = { isEffectsModeOpen = false }
                )
            } else {
                // ==========================================================
                // FISIKA RUANG WAKTU: Efek vortex hisap gravitasi ke singularitas
                // ==========================================================
                val suctionPhase = (themeTransitionProgress / 0.5f).coerceIn(0f, 1f)
                val releasePhase = ((themeTransitionProgress - 0.5f) / 0.5f).coerceIn(0f, 1f)

                // Percepatan kurva eksponensial gravitasi
                val pageScale = if (themeTransitionProgress < 0.5f) {
                    1f - 0.98f * (suctionPhase * suctionPhase * suctionPhase)
                } else {
                    val r = releasePhase
                    val tension = 1.25f
                    val bounce = (r - 1f) * (r - 1f) * ((tension + 1f) * (r - 1f) + tension) + 1f
                    0.02f + 0.98f * bounce.coerceIn(0f, 1.05f)
                }

                // Rotasi distorsi pusaran ruang waktu
                val pageRotation = if (themeTransitionProgress < 0.5f) {
                    -18f * (suctionPhase * suctionPhase)
                } else {
                    18f * (1f - releasePhase) * (1f - releasePhase)
                }

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                    modifier = Modifier.graphicsLayer {
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val suction = if (themeTransitionProgress < 0.5f) {
                            suctionPhase * suctionPhase
                        } else {
                            (1f - releasePhase) * (1f - releasePhase)
                        }
                        val dx = if (themeToggleCenter != Offset.Zero) themeToggleCenter.x - centerX else 0f
                        val dy = if (themeToggleCenter != Offset.Zero) themeToggleCenter.y - centerY else 0f

                        scaleX = pageScale
                        scaleY = pageScale
                        rotationZ = pageRotation
                        translationX = dx * suction
                        translationY = dy * suction
                        alpha = if (themeTransitionProgress < 0.5f) {
                            (1f - suctionPhase * 0.9f).coerceIn(0.1f, 1f)
                        } else {
                            (releasePhase * 1.2f).coerceIn(0.1f, 1f)
                        }
                    }
                ) { _ ->
                    val windowInfo = rememberZkmWindowInfo()
                    val wide = windowInfo.isWide
                    val previewFocus =
                        if (detail == StyleDetail.DIALOG) PreviewFocus.DIALOG else PreviewFocus.NONE
                    val previewBlur = if (isBgBlur) blurStrength else 0f

                    val toggleDarkTheme: () -> Unit = {
                        if (!themeTransitionVisible) {
                            val nextDark = themeMode != ThemeMode.DARK
                            themeTransitionToDark = nextDark
                            themeTransitionVisible = true
                            val generation = ++themeTransitionGeneration
                            scope.launch {
                                themeTransitionAnim.snapTo(0f)
                                // Penggantian tema terjadi tepat saat singularitas mencapai kepadatan puncak
                                launch {
                                    delay(490)
                                    if (generation == themeTransitionGeneration) {
                                        viewModel.setThemeMode(if (nextDark) ThemeMode.DARK else ThemeMode.LIGHT)
                                    }
                                }
                                themeTransitionAnim.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(
                                        durationMillis = 1150,
                                        easing = FastOutSlowInEasing
                                    )
                                )
                                if (generation == themeTransitionGeneration) {
                                    themeTransitionVisible = false
                                    themeTransitionAnim.snapTo(0f)
                                }
                            }
                        }
                    }

                    // PANEL KONTROL: hub kartu per tab; kartu yang diketuk menimpa hub tab itu.
                    val panel: @Composable (Modifier, Boolean, Shape) -> Unit =
                        { panelModifier, panelWide, panelShape ->
                            StudioControlPanel(
                                activeTab = activeTab,
                                detail = detail,
                                onDetailChange = { d ->
                                    if (d == null) {
                                        details.remove(activeTab)
                                    } else {
                                        details[activeTab] = d
                                    }
                                },
                                viewModel = viewModel,
                                panelHaze = panelHaze,
                                onOpenPhotos = {
                                    imagePickerLauncher.launch(
                                        arrayOf("image/*", "video/*", "video/mp4", "video/x-matroska")
                                    )
                                },
                                onOpenEffects = { isEffectsModeOpen = true },
                                onShowCustomColorPicker = { type ->
                                    editingColorType = type
                                    showCustomColorPicker = true
                                },
                                onImportCustomFont = {
                                    fontPickerLauncher.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream"))
                                },
                                themeToggleCenter = { themeToggleCenter = it },
                                onToggleDarkTheme = toggleDarkTheme,
                                themeTransitionRunning = themeTransitionVisible,
                                modifier = panelModifier,
                                wide = panelWide,
                                shape = panelShape
                            )
                        }

                    Box(modifier = Modifier.fillMaxSize()) {
                        if (wide) {
                            val previewPaneWidth = (windowInfo.widthDp * 0.36f).dp.coerceIn(260.dp, 420.dp)
                            Row(modifier = Modifier.fillMaxSize()) {
                                // PREVIEW REALTIME di kiri (ponsel mini diskalakan muat di tinggi layar)
                                PreviewSidePane(
                                    viewModel = viewModel,
                                    bgSaturation = bgSaturation,
                                    blurStrength = previewBlur,
                                    focus = previewFocus,
                                    modifier = Modifier
                                        .width(previewPaneWidth)
                                        .fillMaxHeight()
                                )
                                // Panel kanan FULL-BLEED: lurus dengan sisi preview, tanpa sudut kartu,
                                // menutupi seluruh tinggi layar (inset ditangani di dalam panel).
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    panel(
                                        Modifier.fillMaxSize(),
                                        true,
                                        RectangleShape
                                    )
                                    // CAPSULE NAV MELAYANG: selalu terlihat, ganti tab = ganti hub
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .windowInsetsPadding(
                                                WindowInsets.safeDrawing.only(WindowInsetsSides.End)
                                            )
                                    ) {
                                        StudioDock(
                                            activeTab = activeTab,
                                            onTabSelected = { activeTab = it },
                                            hazeState = panelHaze
                                        )
                                    }
                                }
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // PREVIEW REALTIME layar Overall (selalu terlihat di atas)
                                PreviewStage(
                                    viewModel = viewModel,
                                    bgSaturation = bgSaturation,
                                    blurStrength = previewBlur,
                                    expanded = previewExpanded,
                                    onToggleExpanded = { previewExpanded = !previewExpanded },
                                    focus = previewFocus
                                )
                                panel(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    false,
                                    RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
                                )
                            }
                            // CAPSULE NAV MELAYANG: selalu terlihat, ganti tab = ganti hub
                            StudioDock(
                                activeTab = activeTab,
                                onTabSelected = { activeTab = it },
                                hazeState = panelHaze
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // RENDERER REALISTIS LUBANG HITAM (Singularity, Accretion Disk, & Lens Arc)
            // =========================================================================
            if (themeTransitionProgress > 0f && themeToggleCenter != Offset.Zero) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(999f)
                ) {
                    val p = themeTransitionProgress
                    val center = themeToggleCenter

                    // Fase keruntuhan singularitas: 0 -> 1 -> 0
                    val collapse = if (p < 0.5f) (p / 0.5f) else ((1f - p) / 0.5f)
                    val smoothCollapse = sin(collapse * Math.PI.toFloat() * 0.5f)

                    // Radius horizon peristiwa (Schwarzschild radius)
                    val eventHorizonRadius = 16.dp.toPx() + 28.dp.toPx() * smoothCollapse
                    val diskOuterRadius = eventHorizonRadius * 3.8f

                    // Palet warna plasma energi (Oranye panas relativistik atau Biru/Cyan kosmik)
                    val plasmaCore = if (themeTransitionToDark) Color(0xFFFFB300) else Color(0xFF00E5FF)
                    val plasmaHot = if (themeTransitionToDark) Color(0xFFFF4500) else Color(0xFF2979FF)
                    val plasmaCold = if (themeTransitionToDark) Color(0xFF8A2BE2) else Color(0xFF001F3F)

                    // 1. Gravitational Vignette (Bayangan Medan Gravitasi)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.85f * smoothCollapse),
                                Color.Black.copy(alpha = 0.4f * smoothCollapse),
                                Color.Transparent
                            ),
                            center = center,
                            radius = diskOuterRadius * 2.2f
                        ),
                        radius = diskOuterRadius * 2.2f,
                        center = center
                    )

                    // 2. Gravitational Lensing (Busur Lengkung Lensa Gravitasi Atas & Bawah ala Gargantua)
                    for (i in 0 until 3) {
                        val haloAlpha = (0.25f - i * 0.07f) * smoothCollapse
                        val haloThickness = (12f - i * 3f).dp.toPx()
                        val warpRadius = eventHorizonRadius * (1.35f + i * 0.28f)

                        // Lengkungan atas
                        drawArc(
                            brush = Brush.radialGradient(
                                colors = listOf(plasmaCore.copy(alpha = haloAlpha), Color.Transparent),
                                center = center.copy(y = center.y - warpRadius * 0.3f),
                                radius = warpRadius * 1.2f
                            ),
                            startAngle = 180f,
                            sweepAngle = 180f,
                            useCenter = false,
                            topLeft = Offset(center.x - warpRadius, center.y - warpRadius * 1.1f),
                            size = Size(warpRadius * 2, warpRadius * 2.2f),
                            style = Stroke(width = haloThickness)
                        )

                        // Lengkungan bawah
                        drawArc(
                            brush = Brush.radialGradient(
                                colors = listOf(plasmaHot.copy(alpha = haloAlpha * 0.7f), Color.Transparent),
                                center = center.copy(y = center.y + warpRadius * 0.3f),
                                radius = warpRadius * 1.2f
                            ),
                            startAngle = 0f,
                            sweepAngle = 180f,
                            useCenter = false,
                            topLeft = Offset(center.x - warpRadius, center.y - warpRadius * 1.1f),
                            size = Size(warpRadius * 2, warpRadius * 2.2f),
                            style = Stroke(width = haloThickness)
                        )
                    }

                    // 3. Relativistic Accretion Disk (Piringan Akresi Miring & Efek Doppler)
                    val rotationAngle = p * 1440f
                    val diskTiltY = 0.38f

                    for (ring in 0 until 6) {
                        val ringProgress = ring / 6f
                        val rRadius = eventHorizonRadius * 1.15f + (diskOuterRadius - eventHorizonRadius) * ringProgress
                        val ringAlpha = ((1f - ringProgress) * 0.65f + 0.2f) * smoothCollapse

                        drawArc(
                            brush = Brush.sweepGradient(
                                0.0f to plasmaCore.copy(alpha = ringAlpha * 1.2f),
                                0.25f to plasmaHot.copy(alpha = ringAlpha * 0.9f),
                                0.5f to plasmaCold.copy(alpha = ringAlpha * 0.25f),
                                0.75f to plasmaHot.copy(alpha = ringAlpha * 0.5f),
                                1.0f to plasmaCore.copy(alpha = ringAlpha * 1.2f),
                                center = center
                            ),
                            startAngle = rotationAngle * (1f + ringProgress * 0.5f),
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(center.x - rRadius, center.y - rRadius * diskTiltY),
                            size = Size(rRadius * 2, rRadius * 2 * diskTiltY),
                            style = Stroke(width = (2.5f + (1f - ringProgress) * 3f).dp.toPx())
                        )
                    }

                    // 4. Spaghettified Inflow Streaks (Garis Materi Meregang Masuk ke Singularitas)
                    val particleCount = 72
                    for (i in 0 until particleCount) {
                        val seed = (i * 137.5f) % 360f
                        val baseT = ((i.toFloat() / particleCount) + p * 2.5f) % 1f
                        val r = eventHorizonRadius + (diskOuterRadius * 1.3f - eventHorizonRadius) * (1f - baseT * baseT)
                        val spiralAngle = seed * (Math.PI / 180f).toFloat() + (1f - baseT) * 7.5f

                        val px = center.x + cos(spiralAngle) * r
                        val py = center.y + sin(spiralAngle) * r * diskTiltY

                        val tailAngle = spiralAngle - 0.22f * (1f - baseT)
                        val tailR = r * 1.12f
                        val tx = center.x + cos(tailAngle) * tailR
                        val ty = center.y + sin(tailAngle) * tailR * diskTiltY

                        val particleAlpha = (baseT * (1f - baseT) * 4f) * smoothCollapse
                        drawLine(
                            color = if (i % 2 == 0) plasmaCore.copy(alpha = particleAlpha) else Color.White.copy(alpha = particleAlpha),
                            start = Offset(tx, ty),
                            end = Offset(px, py),
                            strokeWidth = (1.2f + baseT * 1.8f).dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // 5. Event Horizon & Photon Sphere (Inti Hitam Absolut & Cincin Foton)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White,
                                plasmaCore.copy(alpha = 0.9f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = eventHorizonRadius * 1.25f
                        ),
                        radius = eventHorizonRadius * 1.25f,
                        center = center
                    )

                    drawCircle(
                        color = Color.Black,
                        radius = eventHorizonRadius,
                        center = center
                    )

                    drawCircle(
                        color = Color.White.copy(alpha = 0.95f * smoothCollapse),
                        radius = eventHorizonRadius * 1.02f,
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    // 6. Gravitational Wave Burst (Gelombang Kejut saat Tema Berpindah)
                    if (p > 0.45f) {
                        val shockProgress = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f)
                        val shockRadius = eventHorizonRadius + shockProgress * size.maxDimension * 0.85f
                        val shockAlpha = (1f - shockProgress) * 0.7f

                        drawCircle(
                            color = if (themeTransitionToDark) Color.White.copy(alpha = shockAlpha) else plasmaCore.copy(alpha = shockAlpha),
                            radius = shockRadius,
                            center = center,
                            style = Stroke(width = (6.dp.toPx() * (1f - shockProgress)).coerceAtLeast(1f))
                        )
                    }
                }
            }

            if (showCustomColorPicker && editingColorType != null) {
                val initialColor = when (editingColorType) {
                    ColorType.PRIMARY -> Color(customPrimary)
                    ColorType.SECONDARY -> Color(customSecondary)
                    ColorType.TERTIARY -> Color(customTertiary)
                    null -> Color(0xFF4A6595)
                }

                AdvancedColorPickerDialog(
                    initialColor = initialColor,
                    onDismiss = { showCustomColorPicker = false },
                    onColorSelected = { color ->
                        when (editingColorType) {
                            ColorType.PRIMARY -> viewModel.setCustomPrimaryColor(color)
                            ColorType.SECONDARY -> viewModel.setCustomSecondaryColor(color)
                            ColorType.TERTIARY -> viewModel.setCustomTertiaryColor(color)
                            null -> {}
                        }
                        showCustomColorPicker = false
                    }
                )
            }
        }
    }
}

@Composable
fun FontSelectionSheetContent(
    viewModel: WallpaperStyleViewModel,
    onImportCustomFont: () -> Unit,
    onDone: () -> Unit
) {
    val selected by viewModel.appFontChoice.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(stringResource(R.string.wall_app_font), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.wall_font_sheet_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))

        val options = listOf(
            AppFontChoice.APP_DEFAULT to (stringResource(R.string.wall_app_default) to stringResource(R.string.wall_app_default_desc)),
            AppFontChoice.DEVICE_SYSTEM to (stringResource(R.string.wall_device_system_font) to stringResource(R.string.wall_device_system_font_desc)),
            AppFontChoice.SERIF to (stringResource(R.string.wall_serif) to stringResource(R.string.wall_serif_desc)),
            AppFontChoice.MONOSPACE to (stringResource(R.string.wall_monospace) to stringResource(R.string.wall_monospace_desc))
        )

        options.forEachIndexed { index, (choice, copy) ->
            Surface(
                onClick = { viewModel.setAppFontChoice(choice) },
                shape = RoundedCornerShape(if (index == 0) 24.dp else 0.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected == choice, onClick = { viewModel.setAppFontChoice(choice) })
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(copy.first, style = MaterialTheme.typography.titleMedium)
                        Text(copy.second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
        }

        Surface(
            onClick = onImportCustomFont,
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected == AppFontChoice.CUSTOM, onClick = onImportCustomFont)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.wall_custom_font), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.wall_custom_font_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onImportCustomFont) { Text(stringResource(R.string.wall_import_font)) }
            }
        }

        Spacer(Modifier.height(18.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.wall_done)) }
        Spacer(Modifier.height(12.dp))
    }
}

// ==========================================
// ICON SHAPES (MaterialShapes / ShapesSample)
// ==========================================

@Composable
fun IconShapeSelectionSheetContent(
    viewModel: WallpaperStyleViewModel,
    onDone: () -> Unit
) {
    val selected by viewModel.iconShapeStyle.collectAsState()
    val customImagePath by viewModel.customIconImagePath.collectAsState()
    val customImagePaths by viewModel.customIconImagePaths.collectAsState()
    val menuShapeStyles by viewModel.menuIconShapes.collectAsState()
    val autoImageColor by viewModel.autoIconImageColor.collectAsState()
    val iconMenuOptions = remember {
        listOf(
            NavigationRoute.Terminal.route to R.string.ext_terminal,
            NavigationRoute.SetEdit.route to R.string.ext_setedit,
            NavigationRoute.KernelFlasher.route to R.string.ext_flasher,
            NavigationRoute.ActivityLauncher.route to R.string.ext_launcher,
            NavigationRoute.DebloatFreeze.route to R.string.ext_dod,
            NavigationRoute.Dex2oat.route to R.string.ext_compiler,
            NavigationRoute.ThermalDevices.route to R.string.ext_thermal,
            NavigationRoute.Display.route to R.string.ext_display,
            NavigationRoute.BatteryController.route to R.string.ext_battery,
            NavigationRoute.DozeMode.route to R.string.ext_dozemode,
            NavigationRoute.AppManager.route to R.string.ext_app_manager,
            NavigationRoute.MiuiThermal.route to R.string.ext_miui_thermal,
            NavigationRoute.FpsManager.route to R.string.ext_fps,
            NavigationRoute.GameSpace.route to R.string.ext_gamespace,
            NavigationRoute.ProcessManager.route to R.string.ext_process,
            NavigationRoute.KsuWebUI.route to R.string.ext_ksuweb
        )
    }
    var selectedMenuId by remember { mutableStateOf(iconMenuOptions.first().first) }
    val selectedMenuName = iconMenuOptions.firstOrNull { it.first == selectedMenuId }?.second?.let { stringResource(it) } ?: selectedMenuId
    val iconImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.setCustomIconImageForMenu(selectedMenuId, it) }
    }
    val presetExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::exportIconPreset)
    }
    val presetImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importIconPreset)
    }
    val previewIcons = listOf(
        Icons.Rounded.Terminal,
        Icons.Rounded.RocketLaunch,
        Icons.Rounded.BatteryChargingFull,
        Icons.Rounded.Apps,
        Icons.Rounded.Settings
    )

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(stringResource(R.string.wall_icon_shapes), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.wall_icon_shapes_sheet_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))

        // Live preview memakai shape yang sedang dipilih
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.wall_icon_shapes_preview), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    previewIcons.forEach { icon ->
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(selected.resolve(RoundedCornerShape(16.dp)))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            ZkmIconGlyph(icon = icon, tint = MaterialTheme.colorScheme.primary, iconSize = 24.dp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // Grid shape (4 kolom) seperti ShapesSample
        ZkmIconShapeStyle.entries.chunked(4).forEach { rowItems ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                rowItems.forEach { style ->
                    val isSelected = style == selected
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { viewModel.setIconShapeStyle(style) }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(style.resolve(RoundedCornerShape(18.dp)))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = style.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            maxLines = 1,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
                // isi sisa kolom supaya lebar sel tetap sama
                repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        Spacer(Modifier.height(14.dp))
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Per-menu images", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(4.dp))
                Text(
                    "Give each Home menu its own image. The selected image replaces the default icon only for that menu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text("Selected: $selectedMenuName", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(10.dp))
                Text("Shape for this menu", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                val menuShape = menuShapeStyles[selectedMenuId]?.let { ZkmIconShapeStyle.fromName(it) } ?: ZkmIconShapeStyle.DEFAULT
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ZkmIconShapeStyle.entries) { style ->
                        FilterChip(
                            selected = menuShape == style,
                            onClick = { viewModel.setMenuIconShape(selectedMenuId, style) },
                            label = { Text(style.label, maxLines = 1) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto color from image", modifier = Modifier.weight(1f))
                    ZkmSwitch(checked = autoImageColor, onCheckedChange = { viewModel.setAutoIconImageColor(it) })
                }
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(iconMenuOptions) { (route, labelRes) ->
                        val active = route == selectedMenuId
                        FilterChip(
                            selected = active,
                            onClick = { selectedMenuId = route },
                            label = { Text(stringResource(labelRes), maxLines = 1) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                val selectedPath = customImagePaths[selectedMenuId]
                if (selectedPath != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(selectedPath),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(84.dp).clip(RoundedCornerShape(18.dp))
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { iconImagePicker.launch(arrayOf("image/*")) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Choose image") }
                    OutlinedButton(
                        onClick = { viewModel.clearCustomIconImageForMenu(selectedMenuId) },
                        enabled = selectedPath != null,
                        modifier = Modifier.weight(1f)
                    ) { Text("Remove") }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.wall_icon_custom_image), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.wall_icon_custom_image_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { iconImagePicker.launch(arrayOf("image/*")) },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.wall_icon_custom_image_pick)) }
                    OutlinedButton(
                        onClick = { viewModel.clearCustomIconImage() },
                        enabled = customImagePath != null,
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.wall_icon_custom_image_remove)) }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Icon theme presets", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(4.dp))
                Text("Export shapes, per-menu images and theme colors into one portable JSON preset.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { presetExport.launch("zkm-icon-theme.json") }, modifier = Modifier.weight(1f)) { Text("Export") }
                    OutlinedButton(onClick = { presetImport.launch(arrayOf("application/json", "text/*")) }, modifier = Modifier.weight(1f)) { Text("Import") }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        OutlinedButton(
            onClick = { viewModel.setIconShapeStyle(ZkmIconShapeStyle.DEFAULT) },
            enabled = selected != ZkmIconShapeStyle.DEFAULT,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.wall_icon_shapes_reset)) }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.wall_done)) }
        Spacer(Modifier.height(12.dp))
    }
}

// ==========================================
// FULLSCREEN EFFECTS STUDIO
// ==========================================

@Composable
fun EffectsStudioScreen(
    viewModel: WallpaperStyleViewModel,
    weatherEffect: WeatherEffect,
    weatherIntensity: Float,
    bgSaturation: Float,
    blurStrength: Float,
    onClose: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf("Weather") }
    val bgType by viewModel.bgType.collectAsState()
    val backgroundUri by viewModel.backgroundImageUri.collectAsState()
    val customBackgroundActive = bgType == BgType.GALLERY && backgroundUri != null
    val visibleWeatherEffect = if (customBackgroundActive || weatherEffect == WeatherEffect.NONE || weatherEffect == WeatherEffect.SNOW) {
        weatherEffect
    } else {
        WeatherEffect.NONE
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BackgroundRendererWithEffects(
            viewModel = viewModel,
            bgSaturation = bgSaturation,
            blurStrength = blurStrength
        )
        WeatherEffectOverlay(
            effect = visibleWeatherEffect,
            intensity = weatherIntensity,
            modifier = Modifier.fillMaxSize()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.3f))
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = Color.White)
            }

            Spacer(Modifier.width(8.dp))

            Text(
                text = stringResource(R.string.wall_effects),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )

            Spacer(Modifier.weight(1f))

            com.zuan.kernelmanager.next.feature.help.ZkmHelpButton(
                com.zuan.kernelmanager.next.ui.navigation.NavigationRoute.WallpaperStyle.route,
                tint = Color.White
            )

            Spacer(Modifier.width(8.dp))

            IconButton(
                onClick = { },
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.3f))
            ) {
                Icon(Icons.Outlined.Contrast, contentDescription = stringResource(R.string.common_compare), tint = Color.White)
            }

            Spacer(Modifier.width(8.dp))

            IconButton(
                onClick = onClose,
                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.common_done), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }

        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 18.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EffectIconItem(
                        icon = Icons.Outlined.AutoMode,
                        label = stringResource(R.string.wall_auto),
                        isSelected = weatherEffect == WeatherEffect.NONE,
                        onClick = { viewModel.setWeatherEffect(WeatherEffect.NONE) }
                    )
                    EffectIconItem(
                        icon = Icons.Outlined.WaterDrop,
                        label = stringResource(R.string.wall_rain),
                        isSelected = weatherEffect == WeatherEffect.RAIN,
                        enabled = customBackgroundActive,
                        onClick = { viewModel.setWeatherEffect(WeatherEffect.RAIN) }
                    )
                    EffectIconItem(
                        icon = Icons.Outlined.AcUnit,
                        label = stringResource(R.string.wall_snow),
                        isSelected = weatherEffect == WeatherEffect.SNOW,
                        enabled = true,
                        onClick = { viewModel.setWeatherEffect(WeatherEffect.SNOW) }
                    )
                    EffectIconItem(
                        icon = Icons.Outlined.CloudQueue,
                        label = stringResource(R.string.wall_fog),
                        isSelected = weatherEffect == WeatherEffect.FOG,
                        enabled = customBackgroundActive,
                        onClick = { viewModel.setWeatherEffect(WeatherEffect.FOG) }
                    )
                    EffectIconItem(
                        icon = Icons.Outlined.Cloud,
                        label = stringResource(R.string.wall_clouds),
                        isSelected = weatherEffect == WeatherEffect.CLOUDS,
                        enabled = customBackgroundActive,
                        onClick = { viewModel.setWeatherEffect(WeatherEffect.CLOUDS) }
                    )
                    EffectIconItem(
                        icon = Icons.Outlined.WbSunny,
                        label = stringResource(R.string.wall_sun),
                        isSelected = weatherEffect == WeatherEffect.SUN_RAYS,
                        enabled = customBackgroundActive,
                        onClick = { viewModel.setWeatherEffect(WeatherEffect.SUN_RAYS) }
                    )
                    EffectIconItem(
                        icon = Icons.Outlined.SevereCold,
                        label = stringResource(R.string.wall_ice),
                        isSelected = weatherEffect == WeatherEffect.ICE,
                        enabled = customBackgroundActive,
                        onClick = { viewModel.setWeatherEffect(WeatherEffect.ICE) }
                    )
                }

                Spacer(Modifier.height(16.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.wall_intensity, (weatherIntensity * 100).toInt()),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    ZkmSlider(
                        value = weatherIntensity,
                        onValueChange = { viewModel.setWeatherIntensity(it) },
                        valueRange = 0.1f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "Shape" to stringResource(R.string.wall_effect_shape),
                        "Weather" to stringResource(R.string.wall_effect_weather),
                        "Cinematic" to stringResource(R.string.wall_effect_cinematic),
                        "Atmosphere" to stringResource(R.string.wall_effect_atmosphere)
                    ).forEach { (key, label) ->
                        val isCatSelected = selectedCategory == key
                        Surface(
                            onClick = { selectedCategory = key },
                            shape = CircleShape,
                            color = if (isCatSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCatSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EffectIconItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick).graphicsLayer { alpha = if (enabled) 1f else 0.38f }
    ) {
        Surface(
            shape = CircleShape,
            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(46.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ==========================================
// BOTTOM SHEETS & KOMPONEN WARNA
// ==========================================

@Composable
fun ColorsSelectionSheetContent(
    viewModel: WallpaperStyleViewModel,
    onShowCustomColorPicker: (ColorType) -> Unit
) {
    val isDynamic by viewModel.isDynamicColor.collectAsState()
    val selectedThemeColor by viewModel.currentThemeColor.collectAsState()
    val isCustomColor by viewModel.isCustomColor.collectAsState()
    val customPrimary by viewModel.customPrimaryColor.collectAsState()
    val customSecondary by viewModel.customSecondaryColor.collectAsState()
    val customTertiary by viewModel.customTertiaryColor.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.wall_colors_theme_mode),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(16.dp))

        Spacer(Modifier.height(4.dp))

        ColorPresetGrid(
            selectedColor = selectedThemeColor,
            isDynamic = isDynamic,
            isCustomColor = isCustomColor,
            customColor = Color(customPrimary),
            onColorSelected = { viewModel.setThemeColor(it) },
            onDynamicSelected = { viewModel.setDynamicColor(true) },
            onCustomColorSelected = { viewModel.setCustomColorEnabled(true) }
        )

        if (isCustomColor) {
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.wall_custom_palettes),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CustomColorButton(
                    label = stringResource(R.string.wall_primary),
                    color = Color(customPrimary),
                    onClick = { onShowCustomColorPicker(ColorType.PRIMARY) },
                    modifier = Modifier.weight(1f)
                )
                CustomColorButton(
                    label = stringResource(R.string.wall_secondary),
                    color = Color(customSecondary),
                    onClick = { onShowCustomColorPicker(ColorType.SECONDARY) },
                    modifier = Modifier.weight(1f)
                )
                CustomColorButton(
                    label = stringResource(R.string.wall_tertiary),
                    color = Color(customTertiary),
                    onClick = { onShowCustomColorPicker(ColorType.TERTIARY) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun CustomColorButton(
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color)
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
        )
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

// ==========================================
// RENDERER DASAR
// ==========================================

@Composable
fun BackgroundRendererWithEffects(
    viewModel: WallpaperStyleViewModel,
    bgSaturation: Float,
    blurStrength: Float
) {
    val bgType by viewModel.bgType.collectAsState()
    val solidColor by viewModel.solidColor.collectAsState()
    val expressiveThemeId by viewModel.expressiveThemeId.collectAsState()
    val customImageUri by viewModel.backgroundImageUri.collectAsState()
    val isVideo by viewModel.isVideoWallpaper.collectAsState()
    val bgContrast by viewModel.bgContrast.collectAsState()

    Box(Modifier.fillMaxSize()) {
        when (bgType) {
            BgType.SYSTEM -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            BgType.PRESET_SOLID -> Box(Modifier.fillMaxSize().background(Color(solidColor)))
            BgType.EXPRESSIVE -> ExpressiveWallpaperCanvas(themeId = expressiveThemeId)
            BgType.GALLERY -> {
                if (customImageUri != null) {
                    val uri = Uri.parse(customImageUri)

                    val blurModifier = if (blurStrength > 0f) {
                        Modifier.blur(blurStrength.dp)
                    } else {
                        Modifier
                    }

                    if (isVideo) {
                        VideoWallpaperPlayer(
                            uri = uri,
                            modifier = Modifier.fillMaxSize().then(blurModifier)
                        )
                    } else {
                        val saturationModifier = if (bgSaturation != 1f) {
                            Modifier.graphicsLayer {
                                val matrix = ColorMatrix().apply { setToSaturation(bgSaturation) }
                                this.colorFilter = ColorMatrixColorFilter(matrix)
                            }
                        } else {
                            Modifier
                        }

                        Image(
                            painter = rememberAsyncImagePainter(model = uri),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(blurModifier)
                                .then(saturationModifier)
                        )
                    }

                    if (bgContrast > 0f) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = bgContrast))
                        )
                    }
                } else {
                    Box(Modifier.fillMaxSize().background(Color.DarkGray))
                }
            }
        }
    }
}


// ==========================================
// [BARU] CARD GLASS STYLE OPTION
// ==========================================

@Composable
fun CardGlassStyleOption(
    title: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Kartu menu style di tab Style (Toggle/Slider/Dialog). */
@Composable
private fun StyleMenuCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    preview: @Composable () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            preview()
        }
    }
}

// ==========================================
// [BARU] STYLE SHEETS: TOGGLE / SLIDER / DIALOG
// ==========================================

/** Baris opsi style pada bottom sheet (radio-like). */
@Composable
private fun StyleOptionRow(
    title: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun ToggleStyleSheetContent(viewModel: WallpaperStyleViewModel) {
    val toggleStyle by viewModel.toggleStyle.collectAsState()
    val onImagePath by viewModel.customToggleOnImagePath.collectAsState()
    val offImagePath by viewModel.customToggleOffImagePath.collectAsState()

    val onImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.setCustomControlImage(ControlImageKind.TOGGLE_ON, it) }
    }
    val offImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.setCustomControlImage(ControlImageKind.TOGGLE_OFF, it) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            stringResource(R.string.wall_toggle_style),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.wall_toggle_style_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        StyleOptionRow(
            title = stringResource(R.string.wall_toggle_m3_expressive),
            desc = stringResource(R.string.wall_toggle_m3_expressive_desc),
            selected = toggleStyle == ToggleStyle.MATERIAL_EXPRESSIVE,
            onClick = { viewModel.setToggleStyle(ToggleStyle.MATERIAL_EXPRESSIVE) }
        )
        Spacer(Modifier.height(10.dp))
        StyleOptionRow(
            title = stringResource(R.string.wall_toggle_liquid_glass),
            desc = stringResource(R.string.wall_toggle_liquid_glass_desc),
            selected = toggleStyle == ToggleStyle.LIQUID_GLASS,
            onClick = { viewModel.setToggleStyle(ToggleStyle.LIQUID_GLASS) }
        )
        Spacer(Modifier.height(10.dp))
        StyleOptionRow(
            title = stringResource(R.string.wall_toggle_classic),
            desc = stringResource(R.string.wall_toggle_classic_desc),
            selected = toggleStyle == ToggleStyle.CLASSIC,
            onClick = { viewModel.setToggleStyle(ToggleStyle.CLASSIC) }
        )
        Spacer(Modifier.height(10.dp))
        StyleOptionRow(
            title = stringResource(R.string.wall_toggle_custom),
            desc = stringResource(R.string.wall_toggle_custom_desc),
            selected = toggleStyle == ToggleStyle.CUSTOM_IMAGE,
            onClick = { viewModel.setToggleStyle(ToggleStyle.CUSTOM_IMAGE) }
        )

        if (toggleStyle == ToggleStyle.CUSTOM_IMAGE) {
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.wall_toggle_custom_images),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CustomThumbImageCard(
                    label = stringResource(R.string.wall_toggle_image_off),
                    imagePath = offImagePath,
                    onPick = { offImagePicker.launch(arrayOf("image/*")) },
                    onClear = { viewModel.clearCustomControlImage(ControlImageKind.TOGGLE_OFF) },
                    modifier = Modifier.weight(1f)
                )
                CustomThumbImageCard(
                    label = stringResource(R.string.wall_toggle_image_on),
                    imagePath = onImagePath,
                    onPick = { onImagePicker.launch(arrayOf("image/*")) },
                    onClear = { viewModel.clearCustomControlImage(ControlImageKind.TOGGLE_ON) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun SliderStyleSheetContent(viewModel: WallpaperStyleViewModel) {
    val sliderStyle by viewModel.sliderStyle.collectAsState()
    val thumbImagePath by viewModel.customSliderThumbImagePath.collectAsState()

    val thumbImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.setCustomControlImage(ControlImageKind.SLIDER_THUMB, it) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            stringResource(R.string.wall_slider_style),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.wall_slider_style_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        StyleOptionRow(
            title = stringResource(R.string.wall_slider_m3_expressive),
            desc = stringResource(R.string.wall_slider_m3_expressive_desc),
            selected = sliderStyle == SliderStyle.MATERIAL_EXPRESSIVE,
            onClick = { viewModel.setSliderStyle(SliderStyle.MATERIAL_EXPRESSIVE) }
        )
        Spacer(Modifier.height(10.dp))
        StyleOptionRow(
            title = stringResource(R.string.wall_slider_liquid_glass),
            desc = stringResource(R.string.wall_slider_liquid_glass_desc),
            selected = sliderStyle == SliderStyle.LIQUID_GLASS,
            onClick = { viewModel.setSliderStyle(SliderStyle.LIQUID_GLASS) }
        )
        Spacer(Modifier.height(10.dp))
        StyleOptionRow(
            title = stringResource(R.string.wall_slider_classic),
            desc = stringResource(R.string.wall_slider_classic_desc),
            selected = sliderStyle == SliderStyle.CLASSIC,
            onClick = { viewModel.setSliderStyle(SliderStyle.CLASSIC) }
        )
        Spacer(Modifier.height(10.dp))
        StyleOptionRow(
            title = stringResource(R.string.wall_slider_custom),
            desc = stringResource(R.string.wall_slider_custom_desc),
            selected = sliderStyle == SliderStyle.CUSTOM_IMAGE,
            onClick = { viewModel.setSliderStyle(SliderStyle.CUSTOM_IMAGE) }
        )

        if (sliderStyle == SliderStyle.CUSTOM_IMAGE) {
            Spacer(Modifier.height(20.dp))
            CustomThumbImageCard(
                label = stringResource(R.string.wall_slider_thumb_image),
                imagePath = thumbImagePath,
                onPick = { thumbImagePicker.launch(arrayOf("image/*")) },
                onClear = { viewModel.clearCustomControlImage(ControlImageKind.SLIDER_THUMB) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun DialogStyleSheetContent(viewModel: WallpaperStyleViewModel) {
    val dialogStyle by viewModel.dialogStyle.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            stringResource(R.string.wall_dialog_style),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.wall_dialog_style_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        StyleOptionRow(
            title = stringResource(R.string.wall_dialog_haze_blur),
            desc = stringResource(R.string.wall_dialog_haze_blur_desc),
            selected = dialogStyle == DialogStyle.HAZE_BLUR,
            onClick = { viewModel.setDialogStyle(DialogStyle.HAZE_BLUR) }
        )
        Spacer(Modifier.height(10.dp))
        StyleOptionRow(
            title = stringResource(R.string.wall_dialog_liquid_glass),
            desc = stringResource(R.string.wall_dialog_liquid_glass_desc),
            selected = dialogStyle == DialogStyle.LIQUID_GLASS,
            onClick = { viewModel.setDialogStyle(DialogStyle.LIQUID_GLASS) }
        )

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Avatar Decoration Studio — fast category tabs and large, expressive previews.
 * Art direction is original to ZKM Next: layered color fields, orbit lines, particles,
 * and larger live-rendered decoration previews instead of tiny avatar thumbnails.
 */
private enum class AvatarDecoCategory(val title: String) {
    ALL("All"), TWO_D("2D Motion"), THREE_D("3D Depth"), STATIC("Static")
}

@Composable
fun AvatarDecorationSheetContent(viewModel: WallpaperStyleViewModel) {
    val selected by viewModel.avatarDecoration.collectAsState()
    val media = rememberProfileMediaSnapshot()
    var category by remember { mutableStateOf(AvatarDecoCategory.ALL) }

    val allStyles = remember {
        AvatarDecorationStyle.entries.filterNot { it == AvatarDecorationStyle.NONE }
    }
    val styles2D = remember {
        allStyles.filter { !it.is3D && !it.isStatic }
    }
    val styles3D = remember { allStyles.filter { it.is3D } }
    val stylesStatic = remember { allStyles.filter { it.isStatic } }
    val totalCount = allStyles.size
    val displayStyles = remember(category, allStyles, styles2D, styles3D, stylesStatic) {
        when (category) {
            AvatarDecoCategory.ALL -> listOf(AvatarDecorationStyle.NONE) + allStyles
            AvatarDecoCategory.TWO_D -> listOf(AvatarDecorationStyle.NONE) + styles2D
            AvatarDecoCategory.THREE_D -> listOf(AvatarDecorationStyle.NONE) + styles3D
            AvatarDecoCategory.STATIC -> listOf(AvatarDecorationStyle.NONE) + stylesStatic
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header stays compact so the gallery and its artwork have more room.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.wall_avatar_decoration),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Make your profile unmistakably yours",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Text(
                    text = "$totalCount DESIGNS",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold)
                )
            }
        }

        // A profile preview banner: larger art stage, artwork visible around the media.
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .height(108.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(112.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                ) {
                    AvatarDecoArtBackdrop(style = selected, modifier = Modifier.fillMaxSize())
                    AvatarWithDecoration(style = selected, modifier = Modifier.size(98.dp).align(Alignment.Center)) {
                        AvatarPreviewContent(
                            media = media,
                            live = true,
                            placeholderBg = MaterialTheme.colorScheme.primaryContainer,
                            placeholderTint = MaterialTheme.colorScheme.onPrimaryContainer,
                            iconSize = 36.dp
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "YOUR PROFILE PREVIEW",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = selected.label,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when {
                                selected == AvatarDecorationStyle.NONE -> "Original profile"
                                selected.isStatic -> "STATIC ARTWORK"
                                selected.is3D -> "3D DEPTH EFFECT"
                                else -> "2D MOTION ART"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (selected.tier != DecoTier.COMMON) {
                            Spacer(Modifier.width(7.dp))
                            AvatarDecoTierBadge(selected.tier)
                        }
                    }
                }
            }
        }

        // Sticky category tabs remain visible while the catalogue scrolls.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarDecoCategory.entries.forEach { item ->
                val count = when (item) {
                    AvatarDecoCategory.ALL -> totalCount
                    AvatarDecoCategory.TWO_D -> styles2D.size
                    AvatarDecoCategory.THREE_D -> styles3D.size
                    AvatarDecoCategory.STATIC -> stylesStatic.size
                }
                FilterChip(
                    selected = category == item,
                    onClick = { category = item },
                    label = {
                        Text(
                            text = "${item.title}  $count",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    },
                    shape = RoundedCornerShape(50)
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            gridItems(displayStyles, key = { "avatar-deco-${it.name}" }) { style ->
                AvatarDecoTile(
                    style = style,
                    isSelected = selected == style,
                    media = media,
                    onClick = { viewModel.setAvatarDecoration(style) }
                )
            }
        }
    }
}

@Composable
private fun AvatarDecoTile(
    style: AvatarDecorationStyle,
    isSelected: Boolean,
    media: ProfileMedia?,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val accent = avatarDecoAccent(style)
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) scheme.primaryContainer.copy(alpha = 0.78f)
            else scheme.surfaceContainerHigh
        ),
        border = if (isSelected) BorderStroke(2.dp, scheme.primary) else BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.48f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(5.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // The artwork stage is deliberately larger than the old 76dp thumbnail.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(164.dp)
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            ) {
                AvatarDecoArtBackdrop(style = style, modifier = Modifier.fillMaxSize())
                AvatarWithDecoration(style = style, modifier = Modifier.size(140.dp).align(Alignment.Center)) {
                    AvatarPreviewContent(
                        media = media,
                        live = false,
                        placeholderBg = scheme.surfaceVariant,
                        placeholderTint = scheme.onSurfaceVariant,
                        iconSize = 42.dp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = scheme.surface.copy(alpha = 0.88f),
                    contentColor = scheme.onSurface,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                ) {
                    Text(
                        text = when {
                            style == AvatarDecorationStyle.NONE -> "ORIGINAL"
                            style.isStatic -> "STATIC"
                            style.is3D -> "3D DEPTH"
                            else -> "2D MOTION"
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold)
                    )
                }
                if (isSelected) {
                    Surface(
                        shape = CircleShape,
                        color = scheme.primary,
                        contentColor = scheme.onPrimary,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(29.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Check, contentDescription = "Selected", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 11.dp, vertical = 10.dp)
            ) {
                Text(
                    text = style.label,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = if (isSelected) scheme.primary else scheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = when {
                            style == AvatarDecorationStyle.NONE -> "No decoration"
                            style.isStatic -> "Still • Battery friendly"
                            style.is3D -> "Layered perspective"
                            else -> "Animated ornament"
                        },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                if (style.tier != DecoTier.COMMON) {
                    Spacer(Modifier.height(7.dp))
                    AvatarDecoTierBadge(style.tier)
                }
            }
        }
    }
}

/** Original ZKM Next color field behind each frame, with fine orbit geometry and particles. */
@Composable
private fun AvatarDecoArtBackdrop(style: AvatarDecorationStyle, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val accent = avatarDecoAccent(style)
    val secondary = avatarDecoSecondary(style)
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    accent.copy(alpha = 0.20f),
                    scheme.surfaceContainerHigh,
                    secondary.copy(alpha = 0.12f),
                    scheme.surfaceVariant.copy(alpha = 0.65f)
                )
            )
        )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width * 0.5f, size.height * 0.48f)
            val maxR = size.maxDimension * 0.64f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.32f), secondary.copy(alpha = 0.12f), Color.Transparent),
                    center = c,
                    radius = maxR
                ),
                radius = maxR,
                center = c
            )
            drawCircle(
                color = accent.copy(alpha = 0.21f),
                radius = size.minDimension * 0.37f,
                center = c,
                style = Stroke(width = size.minDimension * 0.009f)
            )
            drawCircle(
                brush = Brush.sweepGradient(listOf(accent.copy(alpha = 0.34f), Color.White.copy(alpha = 0.12f), secondary.copy(alpha = 0.32f), accent.copy(alpha = 0.34f)), c),
                radius = size.minDimension * 0.46f,
                center = Offset(c.x, c.y + size.height * 0.015f),
                style = Stroke(width = size.minDimension * 0.006f)
            )
            val orbit = Path().apply {
                moveTo(size.width * 0.04f, size.height * 0.70f)
                cubicTo(size.width * 0.24f, size.height * 0.94f, size.width * 0.70f, size.height * 0.92f, size.width * 0.98f, size.height * 0.66f)
            }
            drawPath(orbit, accent.copy(alpha = 0.28f), style = Stroke(width = size.minDimension * 0.008f))
            val orbit2 = Path().apply {
                moveTo(size.width * 0.10f, size.height * 0.30f)
                cubicTo(size.width * 0.31f, size.height * 0.05f, size.width * 0.73f, size.height * 0.08f, size.width * 0.92f, size.height * 0.31f)
            }
            drawPath(orbit2, secondary.copy(alpha = 0.22f), style = Stroke(width = size.minDimension * 0.006f))

            // Give major themes their own scene language instead of repeating a plain halo.
            val key = style.name
            when {
                key.contains("SAKURA") || key.contains("BLOOM") || key.contains("ROSE") || key.contains("PEARL_LACE") -> {
                    val branch = Path().apply {
                        moveTo(-size.width * 0.04f, size.height * 0.19f)
                        cubicTo(size.width * 0.22f, size.height * 0.04f, size.width * 0.31f, size.height * 0.25f, size.width * 0.48f, size.height * 0.11f)
                        cubicTo(size.width * 0.63f, size.height * 0.01f, size.width * 0.76f, size.height * 0.19f, size.width * 1.04f, size.height * 0.07f)
                    }
                    drawPath(branch, secondary.copy(alpha = 0.72f), style = Stroke(width = size.minDimension * 0.026f, cap = StrokeCap.Round))
                    val twigs = listOf(0.15f, 0.27f, 0.43f, 0.62f, 0.79f)
                    twigs.forEachIndexed { index, fraction ->
                        val bx = size.width * fraction
                        val by = size.height * (if (index % 2 == 0) 0.13f else 0.16f)
                        drawLine(
                            color = secondary.copy(alpha = 0.56f),
                            start = Offset(bx, by),
                            end = Offset(bx + size.width * (if (index % 2 == 0) 0.05f else -0.045f), by - size.height * 0.07f),
                            strokeWidth = size.minDimension * 0.010f,
                            cap = StrokeCap.Round
                        )
                    }
                    val flowers = listOf(
                        Offset(size.width * 0.12f, size.height * 0.13f),
                        Offset(size.width * 0.27f, size.height * 0.07f),
                        Offset(size.width * 0.44f, size.height * 0.11f),
                        Offset(size.width * 0.69f, size.height * 0.08f),
                        Offset(size.width * 0.87f, size.height * 0.12f),
                        Offset(size.width * 0.08f, size.height * 0.81f),
                        Offset(size.width * 0.91f, size.height * 0.78f)
                    )
                    flowers.forEachIndexed { index, flower ->
                        for (petal in 0 until 5) {
                            val angle = petal * (Math.PI.toFloat() * 2f / 5f) + index * 0.13f
                            val p = Offset(
                                flower.x + cos(angle) * size.minDimension * 0.021f,
                                flower.y + sin(angle) * size.minDimension * 0.021f
                            )
                            drawCircle(Color(0xFFFFF1FA).copy(alpha = 0.82f), size.minDimension * 0.016f, p)
                        }
                        drawCircle(Color(0xFFFFC4E1), size.minDimension * 0.008f, flower)
                    }
                }
                key.contains("CYBER") || key.contains("DIGITAL") || key.contains("MATRIX") || key.contains("HOLO") || key.contains("GLITCH") -> {
                    for (i in 0 until 9) {
                        val bw = size.width * (0.035f + (i % 3) * 0.012f)
                        val x = size.width * (i / 9f) + size.width * 0.025f
                        val top = size.height * (0.67f + ((i * 7) % 4) * 0.045f)
                        drawRect(
                            color = if (i % 2 == 0) accent.copy(alpha = 0.28f) else secondary.copy(alpha = 0.25f),
                            topLeft = Offset(x, top),
                            size = Size(bw, size.height * 0.27f)
                        )
                        for (line in 0 until 3) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.40f),
                                start = Offset(x + bw * 0.20f, top + size.height * (0.04f + line * 0.035f)),
                                end = Offset(x + bw * 0.80f, top + size.height * (0.04f + line * 0.035f)),
                                strokeWidth = size.minDimension * 0.004f
                            )
                        }
                    }
                    for (i in 0 until 4) {
                        val y = size.height * (0.18f + i * 0.09f)
                        drawLine(accent.copy(alpha = 0.44f), Offset(size.width * 0.02f, y), Offset(size.width * (0.16f + i * 0.08f), y), size.minDimension * 0.008f)
                        drawLine(secondary.copy(alpha = 0.35f), Offset(size.width * (0.82f - i * 0.03f), y + size.height * 0.035f), Offset(size.width * 0.98f, y + size.height * 0.035f), size.minDimension * 0.006f)
                    }
                }
                key.contains("DRAGON") || key.contains("SERPENT") || key.contains("KRAKEN") || key.contains("OUROBOROS") -> {
                    val creatureArc = Path().apply {
                        moveTo(size.width * 0.08f, size.height * 0.77f)
                        cubicTo(size.width * 0.00f, size.height * 0.54f, size.width * 0.19f, size.height * 0.35f, size.width * 0.08f, size.height * 0.16f)
                        cubicTo(size.width * 0.03f, size.height * 0.05f, size.width * 0.19f, size.height * 0.03f, size.width * 0.24f, size.height * 0.16f)
                    }
                    drawPath(creatureArc, accent.copy(alpha = 0.62f), style = Stroke(width = size.minDimension * 0.023f, cap = StrokeCap.Round))
                    val creatureArc2 = Path().apply {
                        moveTo(size.width * 0.92f, size.height * 0.78f)
                        cubicTo(size.width * 1.02f, size.height * 0.56f, size.width * 0.81f, size.height * 0.39f, size.width * 0.91f, size.height * 0.20f)
                        cubicTo(size.width * 0.97f, size.height * 0.08f, size.width * 0.82f, size.height * 0.05f, size.width * 0.77f, size.height * 0.16f)
                    }
                    drawPath(creatureArc2, secondary.copy(alpha = 0.58f), style = Stroke(width = size.minDimension * 0.018f, cap = StrokeCap.Round))
                    for (i in 0 until 6) {
                        val y = size.height * (0.20f + i * 0.09f)
                        drawCircle(accent.copy(alpha = 0.66f), size.minDimension * 0.012f, Offset(size.width * (0.08f + i * 0.012f), y))
                        drawCircle(secondary.copy(alpha = 0.60f), size.minDimension * 0.011f, Offset(size.width * (0.91f - i * 0.012f), y + size.height * 0.02f))
                    }
                }
                key.contains("CROWN") || key.contains("DIADEM") || key.contains("CREST") || key.contains("WINGS") || key.contains("PHOENIX") -> {
                    val crown = Path().apply {
                        moveTo(size.width * 0.28f, size.height * 0.24f)
                        lineTo(size.width * 0.24f, size.height * 0.10f)
                        lineTo(size.width * 0.39f, size.height * 0.19f)
                        lineTo(size.width * 0.50f, size.height * 0.055f)
                        lineTo(size.width * 0.61f, size.height * 0.19f)
                        lineTo(size.width * 0.76f, size.height * 0.10f)
                        lineTo(size.width * 0.72f, size.height * 0.24f)
                    }
                    drawPath(crown, accent.copy(alpha = 0.66f), style = Stroke(width = size.minDimension * 0.024f, cap = StrokeCap.Round))
                    drawLine(secondary.copy(alpha = 0.62f), Offset(size.width * 0.28f, size.height * 0.27f), Offset(size.width * 0.72f, size.height * 0.27f), size.minDimension * 0.020f, StrokeCap.Round)
                    for (i in 0 until 5) {
                        drawCircle(Color.White.copy(alpha = 0.74f), size.minDimension * 0.010f, Offset(size.width * (0.28f + i * 0.11f), size.height * (if (i == 2) 0.055f else 0.19f)))
                    }
                }
            }

            // Deterministic particles avoid random allocations and keep every thumbnail stable.
            for (i in 0 until 20) {
                val seed = (i * 37 + style.ordinal * 19) % 100
                val x = size.width * (0.04f + (seed / 100f) * 0.92f)
                val ySeed = (seed * 7 + i * 13) % 100
                val y = size.height * (0.06f + (ySeed / 100f) * 0.86f)
                val radius = size.minDimension * (0.0045f + (i % 3) * 0.002f)
                drawCircle(
                    color = if (i % 3 == 0) Color.White.copy(alpha = 0.58f) else accent.copy(alpha = 0.40f),
                    radius = radius,
                    center = Offset(x, y)
                )
            }
            drawLine(
                color = accent.copy(alpha = 0.22f),
                start = Offset(size.width * 0.12f, size.height * 0.88f),
                end = Offset(size.width * 0.88f, size.height * 0.88f),
                strokeWidth = size.minDimension * 0.006f
            )
        }
    }
}

private fun avatarDecoAccent(style: AvatarDecorationStyle): Color {
    val palette = when {
        style == AvatarDecorationStyle.NONE -> listOf(Color(0xFF7C8DFF), Color(0xFF4DD0E1))
        style.isStatic -> listOf(Color(0xFFFFD180), Color(0xFFC0CAFF), Color(0xFF80CBC4), Color(0xFFF48FB1), Color(0xFFB39DDB))
        style.is3D -> listOf(Color(0xFF80DEEA), Color(0xFFB388FF), Color(0xFF64B5F6), Color(0xFFF48FB1), Color(0xFFFFD180))
        else -> listOf(Color(0xFFFF80AB), Color(0xFF7C4DFF), Color(0xFF40C4FF), Color(0xFFFFAB40), Color(0xFF69F0AE))
    }
    return palette[style.ordinal.mod(palette.size)]
}

private fun avatarDecoSecondary(style: AvatarDecorationStyle): Color {
    val palette = when {
        style.isStatic -> listOf(Color(0xFF80CBC4), Color(0xFFFF8A80), Color(0xFF90CAF9), Color(0xFFE1BEE7))
        style.is3D -> listOf(Color(0xFF7C4DFF), Color(0xFFFF80AB), Color(0xFF80CBC4), Color(0xFFFFD54F))
        else -> listOf(Color(0xFF40C4FF), Color(0xFFFFD180), Color(0xFFB388FF), Color(0xFF80CBC4))
    }
    return palette[(style.ordinal / 3).mod(palette.size)]
}

/** Compact rarity label used in the profile gallery and selection preview. */
@Composable
private fun AvatarDecoTierBadge(tier: DecoTier) {
    val (bg, fg) = when (tier) {
        DecoTier.EPIC_PREMIUM -> Color(0xFF4527A0) to Color(0xFFD1C4E9)
        DecoTier.MYTHIC -> Color(0xFF8E0000) to Color(0xFFFFCDD2)
        DecoTier.LEGENDARY -> Color(0xFF7F4F00) to Color(0xFFFFD54F)
        DecoTier.DIVINE -> Color(0xFF006064) to Color(0xFF84FFFF)
        DecoTier.COMMON -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(50), color = bg, contentColor = fg) {
        Text(
            text = tier.label.uppercase(),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold)
        )
    }
}

/** Kartu picker gambar thumb custom (toggle/slider) dengan preview. */
@Composable
private fun CustomThumbImageCard(
    label: String,
    imagePath: String?,
    onPick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val file = imagePath?.let { java.io.File(it) }?.takeIf { it.exists() }
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (file != null) {
                    Image(
                        painter = rememberAsyncImagePainter(model = file),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Outlined.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPick) {
                    Text(stringResource(if (file == null) R.string.wall_pick_image else R.string.wall_change_image))
                }
                if (file != null) {
                    TextButton(onClick = onClear) {
                        Text(stringResource(R.string.wall_remove))
                    }
                }
            }
        }
    }
}
