/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalHazeMaterialsApi::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.zuan.kernelmanager.next.feature.home.menu

import com.zuan.kernelmanager.next.ui.components.zkmAppBarHazeStyle
import com.zuan.kernelmanager.next.ui.components.zkmCardHazeEffect
import com.zuan.kernelmanager.next.ui.components.zkmCardBorder
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundContentColor
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundSubContentColor
import com.zuan.kernelmanager.next.ui.components.zkmCustomBackgroundCardColor
import com.zuan.kernelmanager.next.ui.components.zkmGlassFallbackTint
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import androidx.compose.ui.zIndex
import com.zuan.kernelmanager.next.ui.components.BlurredAlertDialog
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import com.zuan.kernelmanager.next.ui.components.ZkmHazeStyleCache
import com.zuan.kernelmanager.next.ui.components.ZkmSwitch
import com.zuan.kernelmanager.next.ui.components.ZkmSlider

/**
 * Generate SOFT TINTED background berdasarkan primary color tema
 */
@Composable
fun generateDisplayBackgroundColors(isDark: Boolean, primaryColor: Color): Pair<Color, Color> = com.zuan.kernelmanager.next.ui.components.ZkmScreenPalette.colors(isDark, primaryColor)

@Composable
fun DisplayScreen(
    navController: NavController,
    viewModel: DisplayViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val localDensity = LocalDensity.current
    
    val bgType by settingsViewModel.bgType.collectAsStateWithLifecycle()
    val bgUriString by settingsViewModel.backgroundImageUri.collectAsStateWithLifecycle()
    val isVideo by settingsViewModel.isVideoWallpaper.collectAsStateWithLifecycle()
    val isBgBlur by settingsViewModel.isBgBlur.collectAsStateWithLifecycle()
    val blurStrength by settingsViewModel.blurStrength.collectAsStateWithLifecycle()
    val bgSaturation by settingsViewModel.bgSaturation.collectAsStateWithLifecycle()
    val bgContrast by settingsViewModel.bgContrast.collectAsStateWithLifecycle()
    val weatherEffect by settingsViewModel.weatherEffect.collectAsStateWithLifecycle()
    val weatherIntensity by settingsViewModel.weatherIntensity.collectAsStateWithLifecycle()
    val isHazeEnabled by settingsViewModel.isHazeEnabled.collectAsStateWithLifecycle()
    val cardDarkness by settingsViewModel.cardDarkness.collectAsStateWithLifecycle()
    
    val isDynamic by settingsViewModel.isDynamicColor.collectAsStateWithLifecycle()
    val themeColorName by settingsViewModel.currentThemeColor.collectAsStateWithLifecycle()
    val isCustomColor by settingsViewModel.isCustomColor.collectAsStateWithLifecycle()
    val customPrimary by settingsViewModel.customPrimaryColor.collectAsStateWithLifecycle()

    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()

    val effectivePrimary = when {
        isCustomColor -> Color(customPrimary)
        isDynamic -> MaterialTheme.colorScheme.primary
        else -> themeColorName.primary
    }

    val isDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM_DEFAULT -> isSystemInDarkTheme()
    }

    val (themeBgColor, themeCardColor) = if (bgType == BgType.SYSTEM) {
        generateDisplayBackgroundColors(isDarkTheme, effectivePrimary)
    } else {
        Pair(Color.Transparent, Color(0xFFFFFFFF))
    }
    
    val isCustomBg = bgType != BgType.SYSTEM
    val isGlassActive = isHazeEnabled && isCustomBg
    
    val finalCardColor = when {
        isGlassActive -> zkmGlassFallbackTint(zkmIsDarkTheme())
        isCustomBg -> zkmCustomBackgroundCardColor(zkmIsDarkTheme(), cardDarkness)
        else -> themeCardColor
    }
    
    val mainBackgroundColor = if (isCustomBg) Color.Transparent else themeBgColor
    
    val textColor = when {
        isGlassActive || isCustomBg -> zkmOnCustomBackgroundContentColor(zkmIsDarkTheme())
        else -> MaterialTheme.colorScheme.onSurface
    }
    
    val subTextColor = when {
        isGlassActive || isCustomBg -> zkmOnCustomBackgroundSubContentColor(zkmIsDarkTheme())
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    val dividerColor = if (isCustomBg) Color.White.copy(0.2f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
    
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    
    val pagerState = rememberPagerState(pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()
    val hazeState = rememberHazeState()

    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeightDp = with(localDensity) { headerHeightPx.toDp() }

    LaunchedEffect(Unit) {
        viewModel.loadDisplayData(context)
    }

    LaunchedEffect(selectedTab) {
        pagerState.animateScrollToPage(selectedTab)
    }
    LaunchedEffect(pagerState.currentPage) {
        if (selectedTab != pagerState.currentPage) {
            viewModel.onTabSelected(pagerState.currentPage)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(mainBackgroundColor)) {
        // --- BACKGROUND LAYER ---
        if (isCustomBg && bgUriString != null) {
            val blurModifier = if (isBgBlur && blurStrength > 0f) {
                Modifier.blur(blurStrength.dp)
            } else if (isBgBlur) {
                Modifier.blur(20.dp)
            } else {
                Modifier
            }
            
            val saturationMatrix = ColorMatrix().apply { setToSaturation(bgSaturation) }
            val colorFilter = ColorFilter.colorMatrix(saturationMatrix)
            val uri = Uri.parse(bgUriString)

            Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState, zIndex = 0f)) {
                if (isVideo) {
                    VideoWallpaperPlayer(
                        uri = uri, 
                        modifier = Modifier.fillMaxSize().then(blurModifier)
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(uri).build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().then(blurModifier),
                        contentScale = ContentScale.Crop,
                        colorFilter = colorFilter
                    )
                }
                if (bgContrast > 0f) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = bgContrast)))
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(themeBgColor)
                    .hazeSource(state = hazeState, zIndex = 0f)
            )
        }
        
        WeatherEffectOverlay(
            effect = weatherEffect, 
            intensity = weatherIntensity, 
            modifier = Modifier.fillMaxSize()
        )

        // --- CONTENT LAYER ---
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0,0,0,0)
        ) { _ ->
            
            Box(modifier = Modifier.fillMaxSize()) {
                
                // LAYER 1: SCROLLABLE CONTENT
                Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState, zIndex = 1f)) {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = effectivePrimary)
                        }
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.Top
                        ) { page ->
                            when (page) {
                                0 -> DashboardTabGlass(viewModel, isGlassActive, hazeState, finalCardColor, textColor, subTextColor, dividerColor, effectivePrimary, headerHeightDp)
                                1 -> ResolutionDpiTabGlass(viewModel, isGlassActive, hazeState, finalCardColor, textColor, subTextColor, dividerColor, effectivePrimary, headerHeightDp)
                                2 -> RefreshColorTabGlass(viewModel, isGlassActive, hazeState, finalCardColor, textColor, subTextColor, effectivePrimary, headerHeightDp)
                                3 -> AppearanceTabGlass(viewModel, isGlassActive, hazeState, finalCardColor, textColor, subTextColor, effectivePrimary, headerHeightDp)
                            }
                        }
                    }
                }
                
                // LAYER 2: TOP HEADER (PROGRESSIVE BLUR)
                val headerHazeStyle = zkmAppBarHazeStyle(themeBgColor, isCustomBg)
                
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            headerHeightPx = coordinates.size.height
                        }
                        .hazeEffect(
                            state = hazeState,
                            style = headerHazeStyle
                        ) {
                            progressive = HazeProgressive.verticalGradient(
                                startIntensity = 1f,
                                endIntensity = 0f
                            )
                        }
                        .zIndex(2f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                    ) {
                        // Title dan Back Button
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 8.dp, end = 16.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    Icons.Rounded.ArrowBack, 
                                    contentDescription = stringResource(R.string.display_back), 
                                    tint = textColor, 
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.display_title),
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = textColor
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            ZkmHelpButton(NavigationRoute.Display.route, tint = textColor)
                        }

                        // Pill tab selector (sama dengan Battery screen)
                        BatteryPillTabs(
                            titles = listOf(
                                stringResource(R.string.display_tab_info),
                                stringResource(R.string.display_tab_res_dpi),
                                stringResource(R.string.display_tab_color),
                                stringResource(R.string.display_tab_extra)
                            ),
                            selected = selectedTab,
                            onSelect = { index ->
                                viewModel.onTabSelected(index)
                                coroutineScope.launch { pagerState.animateScrollToPage(index) }
                            },
                            primaryColor = effectivePrimary,
                            textColor = textColor,
                            isCustomBg = isCustomBg,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        // Ruang kosong untuk progressive blur memudar
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

// NAMA FUNGSI DIUBAH MENJADI LEBIH SPESIFIK UNTUK MENCEGAH CONFLICTING OVERLOADS
@Composable
private fun DisplayTabItemGlass(selected: Boolean, onClick: () -> Unit, text: String, textColor: Color) {
    Tab(
        selected = selected,
        onClick = onClick,
        text = { 
            Text(
                text = text,
                color = textColor,
                style = if(selected) MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold) 
                        else MaterialTheme.typography.bodyMedium
            ) 
        }
    )
}

@Composable
fun DashboardTabGlass(
    viewModel: DisplayViewModel,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    textColor: Color,
    subTextColor: Color,
    dividerColor: Color,
    primaryColor: Color,
    topPadding: Dp
) {
    val info by viewModel.dashboardInfo.collectAsStateWithLifecycle()

    val displayInfo = info ?: return

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = topPadding + 8.dp, bottom = 100.dp)
    ) {
        // ---- HERO SUMMARY ----
        item {
            GlassDisplayCard(isGlassActive, hazeState, cardColor) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        displayInfo.resolution,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = textColor
                    )
                    Text(
                        "${displayInfo.refreshRate}  •  ${displayInfo.diagonalSize}",
                        style = MaterialTheme.typography.titleSmall,
                        color = primaryColor
                    )
                    Text(displayInfo.density, style = MaterialTheme.typography.bodyMedium, color = subTextColor)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BatteryStatTile(stringResource(R.string.display_refresh_rate), displayInfo.refreshRate, Icons.Rounded.Speed, Color(0xFF26C6DA), isGlassActive, textColor, subTextColor, Modifier.weight(1f))
                BatteryStatTile(stringResource(R.string.display_hdr_support), displayInfo.hdrCapabilities, Icons.Rounded.HdrOn, Color(0xFFFFC107), isGlassActive, textColor, subTextColor, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        item {
            DisplaySectionTitleGlass(stringResource(R.string.display_device_info), primaryColor)
            GlassDisplayCard(isGlassActive, hazeState, cardColor) {
                Column(modifier = Modifier.padding(16.dp)) {
                    GlassDisplayDetailRow(stringResource(R.string.display_resolution), displayInfo.resolution, textColor, subTextColor)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = dividerColor)
                    GlassDisplayDetailRow(stringResource(R.string.display_density_dpi), displayInfo.density, textColor, subTextColor)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = dividerColor)
                    GlassDisplayDetailRow(stringResource(R.string.display_exact_dpi), displayInfo.xdpiYdpi, textColor, subTextColor)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = dividerColor)
                    GlassDisplayDetailRow(stringResource(R.string.display_diagonal_size), displayInfo.diagonalSize, textColor, subTextColor)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = dividerColor)
                    GlassDisplayDetailRow(stringResource(R.string.display_physical_size), displayInfo.physicalSize, textColor, subTextColor)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = dividerColor)
                    GlassDisplayDetailRow(stringResource(R.string.display_refresh_rate), displayInfo.refreshRate, textColor, subTextColor)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = dividerColor)
                    GlassDisplayDetailRow(stringResource(R.string.display_orientation), displayInfo.orientation, textColor, subTextColor)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            DisplaySectionTitleGlass(stringResource(R.string.display_graphics_hdr), primaryColor)
            GlassDisplayCard(isGlassActive, hazeState, cardColor) {
                Column(modifier = Modifier.padding(16.dp)) {
                    GlassDisplayDetailRow(stringResource(R.string.display_gpu_renderer), displayInfo.gpuRenderer, textColor, subTextColor)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = dividerColor)
                    GlassDisplayDetailRow(stringResource(R.string.display_opengl_version), displayInfo.openGlVersion, textColor, subTextColor)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = dividerColor)
                    GlassDisplayDetailRow(stringResource(R.string.display_hdr_support), displayInfo.hdrCapabilities, textColor, subTextColor)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = dividerColor)
                    GlassDisplayDetailRow(
                        stringResource(R.string.display_wide_color_gamut), 
                        if(displayInfo.isWideColorGamut) stringResource(R.string.display_supported) else stringResource(R.string.display_not_supported), 
                        textColor, 
                        subTextColor
                    )
                }
            }
        }
    }
}

@Composable
fun GlassDisplayCard(
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    
    val modifier = if (isGlassActive) {
        Modifier
            .fillMaxWidth()
            .zkmCardHazeEffect(shape = shape,
                state = hazeState,
                style = ZkmHazeStyleCache.of(
                    backgroundColor = cardColor.copy(alpha = 0.5f),
                    blurRadius = 24.dp,
                    noiseFactor = 0.1f,
                    tints = emptyList()
                )
            )
            .zkmCardBorder(1.dp, Color.White.copy(alpha = 0.2f), shape)
    } else {
        Modifier
            .fillMaxWidth()
            .clip(shape)
    }

    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        content()
    }
}

@Composable
fun GlassDisplayDetailRow(label: String, value: String, textColor: Color, subTextColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = subTextColor)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = textColor)
    }
}

@Composable
fun DisplaySectionTitleGlass(title: String, primaryColor: Color) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = primaryColor,
        modifier = Modifier.padding(vertical = 12.dp)
    )
}

@Composable
fun ResolutionDpiTabGlass(
    viewModel: DisplayViewModel,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    textColor: Color,
    subTextColor: Color,
    dividerColor: Color,
    primaryColor: Color,
    topPadding: Dp
) {
    val currentResolution by viewModel.currentResolution.collectAsStateWithLifecycle()
    val currentDpi by viewModel.currentDpi.collectAsStateWithLifecycle()
    
    var showManualResDialog by remember { mutableStateOf(false) }
    var showManualDpiDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = topPadding + 8.dp, bottom = 100.dp)
    ) {
        item {
            DisplaySectionTitleGlass(stringResource(R.string.display_resolution_section), primaryColor)
            GlassSettingCard(
                title = stringResource(R.string.display_current_resolution, currentResolution),
                subtitle = stringResource(R.string.display_tap_presets),
                icon = Icons.Rounded.AspectRatio,
                onClick = { },
                isGlassActive = isGlassActive,
                hazeState = hazeState,
                cardColor = cardColor,
                textColor = textColor,
                subTextColor = subTextColor,
                primaryColor = primaryColor
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(
                    onClick = { showManualResDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isGlassActive) primaryColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.display_manual_input))
                }
                OutlinedButton(
                    onClick = { viewModel.resetResolution() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = if (isGlassActive) BorderStroke(1.dp, Color.White.copy(0.5f)) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(18.dp), tint = if (isGlassActive) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.display_reset), color = if (isGlassActive) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.primary)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Text(stringResource(R.string.display_presets), style = MaterialTheme.typography.labelMedium, color = primaryColor)
            
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp), 
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                viewModel.availableResolutions.take(6).forEach { res ->
                    FilterChip(
                        selected = res == currentResolution,
                        onClick = { viewModel.setResolution(res.width, res.height) },
                        label = { Text("${res.width}x${res.height}", color = textColor) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = primaryColor,
                            selectedLabelColor = Color.White,
                            containerColor = if (isGlassActive) Color.White.copy(0.1f) else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            DisplaySectionTitleGlass(stringResource(R.string.display_density_section), primaryColor)
            GlassSettingCard(
                title = stringResource(R.string.display_current_dpi, currentDpi),
                subtitle = stringResource(R.string.display_density_desc),
                icon = Icons.Rounded.ZoomIn,
                onClick = { },
                isGlassActive = isGlassActive,
                hazeState = hazeState,
                cardColor = cardColor,
                textColor = textColor,
                subTextColor = subTextColor,
                primaryColor = primaryColor
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(
                    onClick = { showManualDpiDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isGlassActive) primaryColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.display_manual_input))
                }
                OutlinedButton(
                    onClick = { viewModel.resetDpi() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = if (isGlassActive) BorderStroke(1.dp, Color.White.copy(0.5f)) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(18.dp), tint = if (isGlassActive) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.display_reset), color = if (isGlassActive) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    if (showManualResDialog) {
        ManualResolutionDialog(
            onDismiss = { showManualResDialog = false },
            onApply = { w, h -> viewModel.setResolution(w, h); showManualResDialog = false }
        )
    }
    if (showManualDpiDialog) {
        ManualDpiDialog(
            currentDpi = currentDpi,
            onDismiss = { showManualDpiDialog = false },
            onApply = { dpi -> viewModel.setDpi(dpi); showManualDpiDialog = false }
        )
    }
}

@Composable
fun GlassSettingCard(
    title: String, 
    subtitle: String, 
    icon: ImageVector, 
    onClick: () -> Unit,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    textColor: Color,
    subTextColor: Color,
    primaryColor: Color
) {
    val shape = RoundedCornerShape(20.dp)
    
    val modifier = if (isGlassActive) {
        Modifier
            .fillMaxWidth()
            .zkmCardHazeEffect(shape = shape,
                state = hazeState,
                style = ZkmHazeStyleCache.of(
                    backgroundColor = cardColor.copy(alpha = 0.5f),
                    blurRadius = 24.dp,
                    noiseFactor = 0.1f,
                    tints = emptyList()
                )
            )
            .zkmCardBorder(1.dp, Color.White.copy(alpha = 0.2f), shape)
            .clickable(onClick = onClick)
    } else {
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
    }

    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = primaryColor)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = textColor)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = subTextColor)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = subTextColor)
        }
    }
}

@Composable
fun RefreshColorTabGlass(
    viewModel: DisplayViewModel,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    textColor: Color,
    subTextColor: Color,
    primaryColor: Color,
    topPadding: Dp
) {
    val currentRefreshRate by viewModel.currentRefreshRate.collectAsStateWithLifecycle()
    val screenTimeout by viewModel.screenTimeout.collectAsStateWithLifecycle()
    val saturation by viewModel.saturation.collectAsStateWithLifecycle()

    val hbmSupported by viewModel.hbmSupported.collectAsStateWithLifecycle()
    val hbmActive by viewModel.hbmActive.collectAsStateWithLifecycle()
    val dcDimmingSupported by viewModel.dcDimmingSupported.collectAsStateWithLifecycle()
    val dcDimmingActive by viewModel.dcDimmingActive.collectAsStateWithLifecycle()
    val kcalConfig by viewModel.kcalConfig.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = topPadding + 8.dp, bottom = 100.dp)
    ) {
        item {
            DisplaySectionTitleGlass(stringResource(R.string.display_saturation_title), primaryColor)
            GlassDisplayCard(isGlassActive, hazeState, cardColor) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Palette, null, tint = primaryColor)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.display_saturation_level), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = textColor))
                        Spacer(modifier = Modifier.weight(1f))
                        Text(String.format("%.1f", saturation), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = primaryColor))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    ZkmSlider(
                        value = saturation,
                        onValueChange = { viewModel.setSaturation(it) },
                        valueRange = 0.0f..2.0f,
                        steps = 19,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = primaryColor,
                            activeTrackColor = primaryColor,
                            inactiveTrackColor = if (isGlassActive) Color.White.copy(0.3f) else MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    )
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.display_bw), style = MaterialTheme.typography.labelSmall, color = subTextColor)
                        Text(stringResource(R.string.display_default), style = MaterialTheme.typography.labelSmall, color = subTextColor)
                        Text(stringResource(R.string.display_vivid), style = MaterialTheme.typography.labelSmall, color = subTextColor)
                    }
                }
            }
        }

        // --- ADVANCED PANEL: HBM ---
        item {
            Spacer(modifier = Modifier.height(16.dp))
            DisplaySectionTitleGlass(stringResource(R.string.display_hbm_title), primaryColor)
            GlassDisplayCard(isGlassActive, hazeState, cardColor) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.WbSunny, null, tint = if (hbmActive) primaryColor else subTextColor)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.display_hbm_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = textColor)
                        )
                        Text(
                            text = if (hbmSupported) stringResource(R.string.display_hbm_desc) else stringResource(R.string.display_hbm_unsupported),
                            style = MaterialTheme.typography.bodySmall,
                            color = subTextColor
                        )
                    }
                    if (hbmSupported) {
                        ZkmSwitch(
                            checked = hbmActive,
                            onCheckedChange = { viewModel.toggleHbm(it) }
                        )
                    }
                }
            }
        }

        // --- ADVANCED PANEL: DC DIMMING ---
        item {
            Spacer(modifier = Modifier.height(16.dp))
            DisplaySectionTitleGlass(stringResource(R.string.display_dc_dimming_title), primaryColor)
            GlassDisplayCard(isGlassActive, hazeState, cardColor) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Visibility, null, tint = if (dcDimmingActive) primaryColor else subTextColor)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.display_dc_dimming_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = textColor)
                        )
                        Text(
                            text = if (dcDimmingSupported) stringResource(R.string.display_dc_dimming_desc) else stringResource(R.string.display_dc_dimming_unsupported),
                            style = MaterialTheme.typography.bodySmall,
                            color = subTextColor
                        )
                    }
                    if (dcDimmingSupported) {
                        ZkmSwitch(
                            checked = dcDimmingActive,
                            onCheckedChange = { viewModel.toggleDcDimming(it) }
                        )
                    }
                }
            }
        }

        // --- ADVANCED PANEL: KCAL COLOR CALIBRATION ---
        item {
            Spacer(modifier = Modifier.height(16.dp))
            DisplaySectionTitleGlass(stringResource(R.string.display_kcal_title), primaryColor)
            GlassDisplayCard(isGlassActive, hazeState, cardColor) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ColorLens, null, tint = primaryColor)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.display_kcal_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = textColor)
                            )
                            Text(
                                text = if (kcalConfig.supported) stringResource(R.string.display_kcal_desc) else stringResource(R.string.display_kcal_unsupported),
                                style = MaterialTheme.typography.bodySmall,
                                color = subTextColor
                            )
                        }
                    }
                    if (kcalConfig.supported) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("R: ${kcalConfig.r}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFFEF5350), modifier = Modifier.width(52.dp))
                            ZkmSlider(
                                value = kcalConfig.r.toFloat(),
                                onValueChange = { viewModel.setKcalRgb(it.toInt(), kcalConfig.g, kcalConfig.b) },
                                valueRange = 1f..256f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = Color(0xFFEF5350), activeTrackColor = Color(0xFFEF5350))
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("G: ${kcalConfig.g}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF66BB6A), modifier = Modifier.width(52.dp))
                            ZkmSlider(
                                value = kcalConfig.g.toFloat(),
                                onValueChange = { viewModel.setKcalRgb(kcalConfig.r, it.toInt(), kcalConfig.b) },
                                valueRange = 1f..256f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = Color(0xFF66BB6A), activeTrackColor = Color(0xFF66BB6A))
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("B: ${kcalConfig.b}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF42A5F5), modifier = Modifier.width(52.dp))
                            ZkmSlider(
                                value = kcalConfig.b.toFloat(),
                                onValueChange = { viewModel.setKcalRgb(kcalConfig.r, kcalConfig.g, it.toInt()) },
                                valueRange = 1f..256f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = Color(0xFF42A5F5), activeTrackColor = Color(0xFF42A5F5))
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.resetKcal() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.display_kcal_reset), color = textColor)
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            DisplaySectionTitleGlass(stringResource(R.string.display_refresh_rate), primaryColor)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                viewModel.availableRefreshRates.forEach { rate ->
                    FilterChip(
                        selected = rate == currentRefreshRate,
                        onClick = { viewModel.setRefreshRate(rate) },
                        label = { Text("${rate}Hz", color = if (rate == currentRefreshRate) Color.White else textColor) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = primaryColor,
                            selectedLabelColor = Color.White,
                            containerColor = if (isGlassActive) Color.White.copy(0.1f) else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            DisplaySectionTitleGlass(stringResource(R.string.display_screen_timeout), primaryColor)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                viewModel.timeoutOptions.forEach { timeout ->
                    val label = when {
                        timeout < 60 -> "${timeout}s"
                        else -> "${timeout/60}m"
                    }
                    FilterChip(
                        selected = timeout == screenTimeout,
                        onClick = { viewModel.setScreenTimeout(timeout) },
                        label = { Text(label, color = if (timeout == screenTimeout) Color.White else textColor) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = primaryColor,
                            selectedLabelColor = Color.White,
                            containerColor = if (isGlassActive) Color.White.copy(0.1f) else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun AppearanceTabGlass(
    viewModel: DisplayViewModel,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    textColor: Color,
    subTextColor: Color,
    primaryColor: Color,
    topPadding: Dp
) {
    val fontScale by viewModel.fontScale.collectAsStateWithLifecycle()
    val animationScale by viewModel.animationScale.collectAsStateWithLifecycle()
    val nightMode by viewModel.nightMode.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = topPadding + 8.dp, bottom = 100.dp)
    ) {
        item {
            DisplaySectionTitleGlass(stringResource(R.string.display_font_scale), primaryColor)
            GlassDisplayCard(isGlassActive, hazeState, cardColor) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ZkmSlider(
                        value = fontScale,
                        onValueChange = { viewModel.setFontScale(it) },
                        valueRange = 0.8f..1.3f,
                        steps = 4,
                        colors = SliderDefaults.colors(
                            thumbColor = primaryColor,
                            activeTrackColor = primaryColor,
                            inactiveTrackColor = if (isGlassActive) Color.White.copy(0.3f) else MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    )
                    Text(stringResource(R.string.display_current_scale, (fontScale * 100).toInt()), style = MaterialTheme.typography.bodySmall, color = subTextColor)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            DisplaySectionTitleGlass(stringResource(R.string.display_animation_speed), primaryColor)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                viewModel.animationScaleOptions.forEach { scale ->
                    FilterChip(
                        selected = scale == animationScale,
                        onClick = { viewModel.setAnimationScale(scale) },
                        label = { Text(if(scale == 0f) stringResource(R.string.display_off) else "${scale}x", color = if (scale == animationScale) Color.White else textColor) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = primaryColor,
                            selectedLabelColor = Color.White,
                            containerColor = if (isGlassActive) Color.White.copy(0.1f) else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(16.dp))
            DisplaySectionTitleGlass(stringResource(R.string.display_night_light), primaryColor)
            GlassSwitchControl(
                title = stringResource(R.string.display_night_mode), 
                checked = nightMode, 
                onCheckedChange = { viewModel.setNightMode(it) }, 
                icon = Icons.Rounded.NightsStay,
                isGlassActive = isGlassActive,
                hazeState = hazeState,
                cardColor = cardColor,
                textColor = textColor,
                primaryColor = primaryColor
            )
        }
    }
}

@Composable
fun GlassSwitchControl(
    title: String, 
    checked: Boolean, 
    onCheckedChange: (Boolean) -> Unit, 
    icon: ImageVector,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    textColor: Color,
    primaryColor: Color
) {
    val shape = RoundedCornerShape(20.dp)
    
    val modifier = if (isGlassActive) {
        Modifier
            .fillMaxWidth()
            .zkmCardHazeEffect(shape = shape,
                state = hazeState,
                style = ZkmHazeStyleCache.of(
                    backgroundColor = cardColor.copy(alpha = 0.5f),
                    blurRadius = 24.dp,
                    noiseFactor = 0.1f,
                    tints = emptyList()
                )
            )
            .zkmCardBorder(1.dp, Color.White.copy(alpha = 0.2f), shape)
            .clickable { onCheckedChange(!checked) }
    } else {
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable { onCheckedChange(!checked) }
    }

    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = primaryColor)
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = textColor)
            ZkmSwitch(
                checked = checked, 
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = primaryColor,
                    uncheckedThumbColor = if (isGlassActive) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else Color.Gray,
                    uncheckedTrackColor = if (isGlassActive) Color.White.copy(0.3f) else Color.Gray.copy(0.3f)
                )
            )
        }
    }
}

@Composable
private fun ManualResolutionDialog(onDismiss: () -> Unit, onApply: (Int, Int) -> Unit) {
    var width by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }

    BlurredAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.display_custom_resolution)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = width, onValueChange = { if(it.all { c -> c.isDigit() }) width = it },
                    label = { Text(stringResource(R.string.display_width_px)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = height, onValueChange = { if(it.all { c -> c.isDigit() }) height = it },
                    label = { Text(stringResource(R.string.display_height_px)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    if(width.isNotEmpty() && height.isNotEmpty()) 
                        onApply(width.toInt(), height.toInt()) 
                }
            ) { Text(stringResource(R.string.btn_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@Composable
private fun ManualDpiDialog(currentDpi: Int, onDismiss: () -> Unit, onApply: (Int) -> Unit) {
    var dpi by remember { mutableStateOf(currentDpi.toString()) }

    BlurredAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.display_custom_dpi)) },
        text = {
            OutlinedTextField(
                value = dpi, onValueChange = { if(it.all { c -> c.isDigit() }) dpi = it },
                label = { Text(stringResource(R.string.display_density_dpi_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { if(dpi.isNotEmpty()) onApply(dpi.toInt()) }) { Text(stringResource(R.string.btn_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}
