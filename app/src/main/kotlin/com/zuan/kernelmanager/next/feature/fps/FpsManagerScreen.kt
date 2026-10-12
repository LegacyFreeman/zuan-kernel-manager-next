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
    ExperimentalMaterial3ExpressiveApi::class
)

package com.zuan.kernelmanager.next.feature.fps

import com.zuan.kernelmanager.next.ui.components.zkmLiteAlpha
import com.zuan.kernelmanager.next.ui.components.zkmAppBarHazeStyle
import com.zuan.kernelmanager.next.ui.components.zkmCardHazeEffect
import com.zuan.kernelmanager.next.ui.components.zkmCardBorder
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundContentColor
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundSubContentColor
import com.zuan.kernelmanager.next.ui.components.zkmCustomBackgroundCardColor
import com.zuan.kernelmanager.next.ui.components.zkmGlassFallbackTint
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import android.net.Uri
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.ui.navigation.NavigationRoute
import com.zuan.kernelmanager.next.feature.help.ZkmHelpButton
import com.zuan.kernelmanager.next.ui.components.VideoWallpaperPlayer
import com.zuan.kernelmanager.next.ui.components.WeatherEffectOverlay
import com.zuan.kernelmanager.next.feature.settings.BgType
import com.zuan.kernelmanager.next.feature.settings.SettingsViewModel
import com.zuan.kernelmanager.next.ui.theme.ThemeMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import com.zuan.kernelmanager.next.ui.components.zkmHazeEffect
import com.zuan.kernelmanager.next.ui.components.zkmHazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import com.zuan.kernelmanager.next.ui.components.zkmGlassCard
import com.zuan.kernelmanager.next.ui.components.zkmGlassTint
import com.zuan.kernelmanager.next.ui.components.LocalZkmHazeState
import com.zuan.kernelmanager.next.ui.components.ZkmHazeStyleCache

enum class FpsManagerTab(
    val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    Home(R.string.fps_tab_home, Icons.Filled.Home, Icons.Outlined.Home),
    Stats(R.string.fps_tab_stats, Icons.Filled.Analytics, Icons.Outlined.Analytics),
    Games(R.string.fps_tab_games, Icons.Filled.Gamepad, Icons.Outlined.Gamepad),
    Overlay(R.string.fps_tab_overlay, Icons.Filled.Layers, Icons.Outlined.Layers)
}

@Composable
fun generateFpsBackgroundColors(primaryColor: Color, isDark: Boolean): Pair<Color, Color> = com.zuan.kernelmanager.next.ui.components.ZkmScreenPalette.colors(isDark, primaryColor)

@Composable
fun FpsManagerScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val tabs = FpsManagerTab.values()
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    
    val localHazeState = rememberHazeState()

    val density = LocalDensity.current
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeightDp = with(density) { headerHeightPx.toDp() }
    
    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val bgType by settingsViewModel.bgType.collectAsStateWithLifecycle()
    val isCustomBg = bgType != BgType.SYSTEM 
    
    val bgUriString by settingsViewModel.backgroundImageUri.collectAsStateWithLifecycle()
    val isVideo by settingsViewModel.isVideoWallpaper.collectAsStateWithLifecycle()
    val isBgBlur by settingsViewModel.isBgBlur.collectAsStateWithLifecycle()
    val blurStrength by settingsViewModel.blurStrength.collectAsStateWithLifecycle()
    val bgSaturation by settingsViewModel.bgSaturation.collectAsStateWithLifecycle()
    val bgContrast by settingsViewModel.bgContrast.collectAsStateWithLifecycle()
    val weatherEffect by settingsViewModel.weatherEffect.collectAsStateWithLifecycle()
    val weatherIntensity by settingsViewModel.weatherIntensity.collectAsStateWithLifecycle()
    
    val cardDarkness by settingsViewModel.cardDarkness.collectAsStateWithLifecycle()
    val isHazeEnabled by settingsViewModel.isHazeEnabled.collectAsStateWithLifecycle()
    
    val isDynamic by settingsViewModel.isDynamicColor.collectAsStateWithLifecycle()
    val themeColor by settingsViewModel.currentThemeColor.collectAsStateWithLifecycle()
    val isCustomColor by settingsViewModel.isCustomColor.collectAsStateWithLifecycle()
    val customPrimary by settingsViewModel.customPrimaryColor.collectAsStateWithLifecycle()

    val isSystemDark = isSystemInDarkTheme()
    val useDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM_DEFAULT -> isSystemDark
    }

    val effectivePrimary = remember(isDynamic, themeColor, isCustomColor, customPrimary) {
        when {
            isCustomColor -> Color(customPrimary)
            isDynamic -> Color.Unspecified
            else -> themeColor.primary
        }
    }

    val finalPrimary = if (effectivePrimary == Color.Unspecified) {
        MaterialTheme.colorScheme.primary
    } else {
        effectivePrimary
    }

    val (tintedBackground, cardContainerColor) = generateFpsBackgroundColors(finalPrimary, useDarkTheme)
    val isGlassActive = isHazeEnabled && isCustomBg
    
    val actualCardContainerColor = when {
        isGlassActive -> zkmGlassFallbackTint(zkmIsDarkTheme())
        isCustomBg -> zkmCustomBackgroundCardColor(zkmIsDarkTheme(), cardDarkness)
        else -> cardContainerColor
    }

    val cardShape = RoundedCornerShape(24.dp)
    val glassCardModifier = if (isGlassActive) {
        Modifier
            .zkmCardHazeEffect(shape = cardShape,
                state = localHazeState,
                style = ZkmHazeStyleCache.of(
                    backgroundColor = MaterialTheme.colorScheme.surface.zkmLiteAlpha(0.3f),
                    blurRadius = 24.dp,
                    noiseFactor = 0.1f,
                    tints = listOf(HazeTint(Color.White.copy(alpha = 0.1f)))
                )
            )
            .zkmCardBorder(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.3f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                shape = cardShape
            )
    } else Modifier

    val cardElevation by animateDpAsState(
        targetValue = if (isCustomBg) 0.dp else 2.dp,
        label = "elevation_anim"
    )

    val contentColor = if (isCustomBg) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.onSurface
    val subContentColor = if (isCustomBg) zkmOnCustomBackgroundSubContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.onSurfaceVariant
    val screenBackground = if (isCustomBg) Color.Transparent else tintedBackground

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = contentColor,
        contentWindowInsets = WindowInsets(0,0,0,0)
    ) { _ ->
        androidx.compose.runtime.CompositionLocalProvider(LocalZkmHazeState provides localHazeState) {
        Box(modifier = Modifier.fillMaxSize().background(screenBackground)) {
            // LAYER 0: CUSTOM BACKGROUND
            Box(modifier = Modifier.fillMaxSize().zkmHazeSource(state = localHazeState, zIndex = 0f)) {
                if (isCustomBg && bgUriString != null) {
                    val blurModifier = if (isBgBlur && blurStrength > 0f) Modifier.blur(blurStrength.dp) else if (isBgBlur) Modifier.blur(20.dp) else Modifier
                    val colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(bgSaturation) })
                    val uri = Uri.parse(bgUriString)

                    if (isVideo) {
                        VideoWallpaperPlayer(uri = uri, modifier = Modifier.fillMaxSize().then(blurModifier))
                    } else {
                        AsyncImage(model = ImageRequest.Builder(context).data(uri).build(), contentDescription = null, modifier = Modifier.fillMaxSize().then(blurModifier), contentScale = ContentScale.Crop, colorFilter = colorFilter)
                    }

                    if (bgContrast > 0f) Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = bgContrast)))
                }
                WeatherEffectOverlay(effect = weatherEffect, intensity = weatherIntensity, modifier = Modifier.fillMaxSize())
            }

            // LAYER 1: PAGER CONTENT
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zkmHazeSource(state = localHazeState, zIndex = 1f)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(0.dp) 
                ) { page ->
                    when (tabs[page]) {
                        FpsManagerTab.Home -> FpsManagerHomeContent(
                            cardBg = actualCardContainerColor, 
                            contentColor = contentColor, 
                            subContentColor = subContentColor, 
                            activeColor = finalPrimary, 
                            elevation = cardElevation,
                            isGlassActive = isGlassActive,
                            hazeState = localHazeState,
                            glassModifier = glassCardModifier,
                            topPadding = headerHeightDp // PENTING: Meneruskan padding Header ke Content
                        )
                        FpsManagerTab.Stats -> FpsManagerStatsContent(
                            contentColor = contentColor, 
                            subContentColor = subContentColor,
                            isGlassActive = isGlassActive,
                            hazeState = localHazeState,
                            glassModifier = glassCardModifier,
                            topPadding = headerHeightDp // PENTING: Meneruskan padding Header ke Content
                        )
                        FpsManagerTab.Games -> FpsManagerGamesContent(
                            contentColor = contentColor, 
                            subContentColor = subContentColor,
                            isGlassActive = isGlassActive,
                            hazeState = localHazeState,
                            topPadding = headerHeightDp // PENTING: Meneruskan padding Header ke Content
                        )
                        FpsManagerTab.Overlay -> FpsManagerOverlayContent(
                            cardBg = actualCardContainerColor, 
                            contentColor = contentColor, 
                            subContentColor = subContentColor, 
                            activeColor = finalPrimary, 
                            elevation = cardElevation,
                            isGlassActive = isGlassActive,
                            hazeState = localHazeState,
                            glassModifier = glassCardModifier,
                            topPadding = headerHeightDp // PENTING: Meneruskan padding Header ke Content
                        )
                    }
                }
            }

            // LAYER 2: TOP HEADER (PROGRESSIVE BLUR)
            val headerHazeStyle = zkmAppBarHazeStyle(tintedBackground, isCustomBg)

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .onGloballyPositioned { headerHeightPx = it.size.height }
                    .zkmHazeEffect(
                        state = localHazeState,
                        style = headerHazeStyle
                    ) {
                        progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
                    }
                    .zIndex(2f)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                ) {
                    // Header Title & Back
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.offset(x = (-12).dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_cancel), tint = contentColor)
                        }
                        Column {
                            Text(stringResource(R.string.fps_display_header), style = MaterialTheme.typography.titleMedium, color = subContentColor)
                            Text(stringResource(R.string.fps_manager_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = contentColor)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        ZkmHelpButton(NavigationRoute.FpsManager.route, tint = contentColor)
                    }

                    // Tab Row
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(22.dp),
                        color = if (isCustomBg) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHighest.zkmLiteAlpha(0.7f)
                    ) {
                        ScrollableTabRow(
                            selectedTabIndex = pagerState.currentPage,
                            containerColor = Color.Transparent,
                            contentColor = finalPrimary,
                            edgePadding = 6.dp,
                            divider = {},
                            indicator = { tabPositions ->
                                if (pagerState.currentPage < tabPositions.size) {
                                    TabRowDefaults.SecondaryIndicator(
                                        modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]).padding(horizontal = 16.dp),
                                        height = 3.dp, color = finalPrimary
                                    )
                                }
                            }
                        ) {
                            tabs.forEachIndexed { index, tab ->
                                val isSelected = pagerState.currentPage == index
                                Tab(
                                    selected = isSelected,
                                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                    text = { 
                                        Text(stringResource(tab.titleRes), color = if (isSelected) finalPrimary else contentColor, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) 
                                    },
                                    icon = { Icon(if (isSelected) tab.selectedIcon else tab.unselectedIcon, null, tint = if (isSelected) finalPrimary else contentColor) }
                                )
                            }
                        }
                    }
                    
                    // Spacer Pudar
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
        } // end CompositionLocalProvider(LocalZkmHazeState)
    }
}
