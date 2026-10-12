/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.zuan.kernelmanager.next.feature.home.menu

import com.zuan.kernelmanager.next.ui.components.zkmAppBarHazeStyle
import com.zuan.kernelmanager.next.ui.components.zkmCardHazeEffect
import com.zuan.kernelmanager.next.ui.components.zkmCardFill
import com.zuan.kernelmanager.next.ui.components.zkmCardBorder
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundContentColor
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundSubContentColor
import com.zuan.kernelmanager.next.ui.components.zkmCustomBackgroundCardColor
import com.zuan.kernelmanager.next.ui.components.zkmGlassFallbackTint
import com.zuan.kernelmanager.next.ui.components.zkmGlassTint
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import androidx.compose.ui.zIndex
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import com.zuan.kernelmanager.next.ui.components.ZkmHazeStyleCache
import com.zuan.kernelmanager.next.ui.components.ZkmSwitch

/**
 * Generate SOFT TINTED background berdasarkan primary color tema
 */
@Composable
fun generateDozeBackgroundColors(primaryColor: Color, isDark: Boolean): Pair<Color, Color> = com.zuan.kernelmanager.next.ui.components.ZkmScreenPalette.colors(isDark, primaryColor)

@Composable
fun DozeModeScreen(
    navController: NavController,
    viewModel: DozeModeViewModel = viewModel(),
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

    val effectivePrimary = remember(isDynamic, themeColorName, isCustomColor, customPrimary) {
        when {
            isCustomColor -> Color(customPrimary)
            isDynamic -> Color.Unspecified
            else -> themeColorName.primary
        }
    }

    val finalPrimary = if (effectivePrimary == Color.Unspecified) {
        MaterialTheme.colorScheme.primary
    } else {
        effectivePrimary
    }

    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM_DEFAULT -> isSystemInDarkTheme()
    }
    
    val (themeBgColor, themeCardColor) = if (bgType == BgType.SYSTEM) {
        generateDozeBackgroundColors(finalPrimary, isDark)
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
    
    val textColor = if (isCustomBg) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.onSurface
    val subTextColor = if (isCustomBg) zkmOnCustomBackgroundSubContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.onSurfaceVariant
    
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val dozeState by viewModel.dozeState.collectAsStateWithLifecycle()
    val dozeStats by viewModel.dozeStats.collectAsStateWithLifecycle()
    
    val hazeState = rememberHazeState()
    
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeightDp = with(localDensity) { headerHeightPx.toDp() }

    LaunchedEffect(Unit) {
        viewModel.loadDozeData(context)
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
                    .hazeSource(state = hazeState, zIndex = 0f)
            ) {
                // Background harus jadi child hazeSource: modifier .background()
                // yang dipasang SEBELUM .hazeSource() tidak ikut terekam ke buffer
                // haze, sehingga progressive blur appbar kosong di mode non-custom.
                Box(modifier = Modifier.fillMaxSize().background(themeBgColor))
            }
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(state = hazeState, zIndex = 1f)
                ) {
                    when (selectedTab) {
                        0 -> DozeAppsTabGlass(
                            viewModel = viewModel, 
                            showOnlyWhitelist = false,
                            isGlassActive = isGlassActive,
                            hazeState = hazeState,
                            cardColor = finalCardColor,
                            textColor = textColor,
                            subTextColor = subTextColor,
                            primaryColor = finalPrimary,
                            topPadding = headerHeightDp,
                            dozeState = dozeState,
                            dozeStats = dozeStats,
                            isCustomBg = isCustomBg
                        )
                        1 -> DozeAppsTabGlass(
                            viewModel = viewModel, 
                            showOnlyWhitelist = true,
                            isGlassActive = isGlassActive,
                            hazeState = hazeState,
                            cardColor = finalCardColor,
                            textColor = textColor,
                            subTextColor = subTextColor,
                            primaryColor = finalPrimary,
                            topPadding = headerHeightDp,
                            dozeState = dozeState,
                            dozeStats = dozeStats,
                            isCustomBg = isCustomBg
                        )
                        2 -> DozeControlsTabGlass(
                            viewModel = viewModel,
                            isGlassActive = isGlassActive,
                            hazeState = hazeState,
                            cardColor = finalCardColor,
                            textColor = textColor,
                            subTextColor = subTextColor,
                            primaryColor = finalPrimary,
                            topPadding = headerHeightDp,
                            dozeState = dozeState,
                            dozeStats = dozeStats,
                            isCustomBg = isCustomBg
                        )
                        3 -> DozeSettingsTabGlass(
                            viewModel = viewModel,
                            isGlassActive = isGlassActive,
                            hazeState = hazeState,
                            cardColor = finalCardColor,
                            textColor = textColor,
                            subTextColor = subTextColor,
                            primaryColor = finalPrimary,
                            topPadding = headerHeightDp,
                            dozeState = dozeState,
                            dozeStats = dozeStats,
                            isCustomBg = isCustomBg
                        )
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
                        // Title & Back Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, start = 8.dp, end = 16.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { navController.popBackStack() }) { 
                                Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.doze_back), tint = textColor) 
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.doze_title), 
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), 
                                color = textColor
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            ZkmHelpButton(NavigationRoute.DozeMode.route, tint = textColor)
                        }
                        
                        // Tab Row Dibungkus Surface (Bumbu Rahasia)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(22.dp),
                            color = if (isCustomBg) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
                        ) {
                            ScrollableTabRow(
                                selectedTabIndex = selectedTab,
                                containerColor = Color.Transparent,
                                contentColor = finalPrimary,
                                edgePadding = 6.dp,
                                divider = {},
                                indicator = { tabPositions ->
                                    if (selectedTab < tabPositions.size) {
                                        TabRowDefaults.SecondaryIndicator(
                                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]).padding(horizontal = 16.dp),
                                            height = 3.dp,
                                            color = finalPrimary
                                        )
                                    }
                                }
                            ) {
                                val tabs = listOf(
                                    stringResource(R.string.doze_tab_apps),
                                    stringResource(R.string.doze_tab_whitelist),
                                    stringResource(R.string.doze_tab_controls),
                                    stringResource(R.string.doze_tab_settings)
                                )
                                tabs.forEachIndexed { index, title ->
                                    DozeTabItemGlass(
                                        selected = selectedTab == index,
                                        onClick = { viewModel.onTabSelected(index) },
                                        text = title,
                                        textColor = textColor,
                                        isGlassActive = isGlassActive,
                                        primaryColor = finalPrimary
                                    )
                                }
                            }
                        }
                        
                        // Ruang kosong untuk progressive blur fading out (Bumbu Rahasia 2)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

// KOMPONEN UNTUK KOTAK STATS YANG SEKARANG IKUT DI-SCROLL
@Composable
fun DozeStatCardsRow(
    dozeState: String,
    dozeStats: DozeStats?,
    isCustomBg: Boolean,
    isGlassActive: Boolean,
    hazeState: HazeState,
    textColor: Color,
    primaryColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val stateColor = when (dozeState) {
            "Active" -> if (isCustomBg) Color(0xFFCF6679).copy(alpha = 0.8f) else MaterialTheme.colorScheme.errorContainer
            "Idle" -> if (isCustomBg) Color(0xFF4DB6AC).copy(alpha = 0.8f) else MaterialTheme.colorScheme.tertiaryContainer
            "Maintenance" -> if (isCustomBg) Color(0xFFFFB74D).copy(alpha = 0.8f) else MaterialTheme.colorScheme.secondaryContainer
            else -> primaryColor.copy(alpha = 0.8f)
        }
        val stateOnColor = if (isCustomBg) Color.White else when (dozeState) {
            "Active" -> MaterialTheme.colorScheme.onErrorContainer
            "Idle" -> MaterialTheme.colorScheme.onTertiaryContainer
            "Maintenance" -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        }
        
        GlassStatCard(
            label = stringResource(R.string.doze_system_state),
            value = dozeState,
            icon = Icons.Rounded.Bedtime,
            color = stateColor,
            onColor = stateOnColor,
            modifier = Modifier.weight(1f),
            isGlassActive = isGlassActive,
            hazeState = hazeState
        )
        
        GlassStatCard(
            label = stringResource(R.string.doze_idle_count),
            value = (dozeStats?.idleCount ?: 0).toString(),
            icon = Icons.Rounded.Countertops,
            color = if (isCustomBg) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHigh,
            onColor = textColor,
            modifier = Modifier.weight(1f),
            isGlassActive = isGlassActive,
            hazeState = hazeState
        )
    }
}

@Composable
fun GlassStatCard(
    label: String, 
    value: String, 
    icon: ImageVector, 
    color: Color, 
    onColor: Color, 
    modifier: Modifier = Modifier,
    isGlassActive: Boolean,
    hazeState: HazeState
) {
    val shape = RoundedCornerShape(32.dp)
    
    val cardModifier = if (isGlassActive) {
        Modifier
            .zkmCardHazeEffect(shape = shape,
                state = hazeState,
                style = ZkmHazeStyleCache.of(
                    backgroundColor = color,
                    blurRadius = 20.dp,
                    noiseFactor = 0.1f,
                    tints = emptyList()
                )
            )
            .zkmCardBorder(1.dp, Color.White.copy(alpha = 0.2f), shape)
    } else {
        Modifier.clip(shape)
    }

    Surface(
        modifier = modifier.then(cardModifier),
        shape = shape,
        color = if (isGlassActive) zkmGlassTint(color, zkmIsDarkTheme(), 0.62f) else color
    ) {
        Column(modifier = Modifier.padding(16.dp).height(110.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(
                modifier = Modifier.size(36.dp).background(onColor.copy(alpha=0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = onColor, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(text = value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = onColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = label, style = MaterialTheme.typography.labelSmall, color = onColor.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun DozeTabItemGlass(
    selected: Boolean, 
    onClick: () -> Unit, 
    text: String, 
    textColor: Color,
    isGlassActive: Boolean,
    primaryColor: Color
) {
    val bgColor by animateColorAsState(
        if (selected) {
            if (isGlassActive) primaryColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primaryContainer
        } else Color.Transparent, label = ""
    )
    val txtColor by animateColorAsState(
        if (selected) {
            if (isGlassActive) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.onPrimaryContainer
        } else textColor, label = ""
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(28.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 16.dp), // Padding disesuaikan agar tidak terlalu tinggi
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text, 
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if(selected) FontWeight.Bold else FontWeight.Medium
            ), 
            color = txtColor, 
            maxLines = 1
        )
    }
}

@Composable
fun DozeAppsTabGlass(
    viewModel: DozeModeViewModel, 
    showOnlyWhitelist: Boolean,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    textColor: Color,
    subTextColor: Color,
    primaryColor: Color,
    topPadding: Dp,
    dozeState: String,
    dozeStats: DozeStats?,
    isCustomBg: Boolean
) {
    val apps by if (showOnlyWhitelist) {
        viewModel.whitelistedFilteredApps.collectAsStateWithLifecycle()
    } else {
        viewModel.allFilteredApps.collectAsStateWithLifecycle()
    }
    
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding + 8.dp, bottom = 100.dp)
    ) {
        // Kotak Status di-render di dalam LazyColumn
        item {
            DozeStatCardsRow(dozeState, dozeStats, isCustomBg, isGlassActive, hazeState, textColor, primaryColor)
        }

        // Search Bar di-render di dalam LazyColumn agar ikut tergulung
        item {
            val searchShape = RoundedCornerShape(32.dp)
            val searchModifier = if (isGlassActive) {
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .zkmCardHazeEffect(shape = searchShape,
                        state = hazeState,
                        style = ZkmHazeStyleCache.of(
                            backgroundColor = Color.White.copy(alpha = 0.1f),
                            blurRadius = 20.dp,
                            noiseFactor = 0.05f,
                            tints = emptyList()
                        )
                    )
                    .zkmCardBorder(1.dp, Color.White.copy(alpha = 0.2f), searchShape)
            } else {
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                modifier = searchModifier,
                placeholder = { 
                    Text(
                        if (showOnlyWhitelist) stringResource(R.string.doze_search_whitelist) else stringResource(R.string.doze_search_all), 
                        color = subTextColor
                    ) 
                },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = primaryColor) },
                shape = searchShape,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedContainerColor = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = if (isGlassActive) Color.White.copy(0.5f) else MaterialTheme.colorScheme.primary,
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                    cursorColor = primaryColor
                ),
                singleLine = true
            )
        }

        if (isLoading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { 
                    CircularProgressIndicator(color = primaryColor) 
                }
            }
        } else if (apps.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.TopCenter) {
                    Text(
                        text = if (showOnlyWhitelist) stringResource(R.string.doze_no_whitelisted) else stringResource(R.string.doze_no_apps_found),
                        color = subTextColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            items(apps, key = { it.packageName }) { app ->
                DozeAppItemGlass(
                    app = app, 
                    onToggle = { viewModel.toggleWhitelist(app) },
                    isGlassActive = isGlassActive,
                    hazeState = hazeState,
                    textColor = textColor,
                    subTextColor = subTextColor,
                    primaryColor = primaryColor
                )
            }
        }
    }
}

@Composable
fun DozeAppItemGlass(
    app: DozeApp, 
    onToggle: () -> Unit,
    isGlassActive: Boolean,
    hazeState: HazeState,
    textColor: Color,
    subTextColor: Color,
    primaryColor: Color
) {
    val shape = RoundedCornerShape(16.dp)
    
    val modifier = if (isGlassActive) {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .zkmCardHazeEffect(shape = shape,
                state = hazeState,
                style = ZkmHazeStyleCache.of(
                    backgroundColor = Color.White.copy(alpha = 0.05f),
                    blurRadius = 20.dp,
                    noiseFactor = 0.05f,
                    tints = emptyList()
                )
            )
            .zkmCardBorder(1.dp, Color.White.copy(alpha = 0.1f), shape)
            .clickable(onClick = onToggle)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(shape)
            .clickable(onClick = onToggle)
    }

    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isGlassActive) zkmCardFill(zkmGlassTint(MaterialTheme.colorScheme.surfaceContainerLow, zkmIsDarkTheme(), 0.40f)) else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (app.isWhitelisted) primaryColor.copy(alpha = 0.8f) else if (isGlassActive) Color.White.copy(0.1f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (app.isWhitelisted) Icons.Rounded.Check else Icons.Rounded.Apps,
                        contentDescription = null,
                        tint = if (app.isWhitelisted) Color.White else subTextColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label, 
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis,
                    color = textColor
                )
                Text(
                    text = app.packageName, 
                    style = MaterialTheme.typography.bodySmall, 
                    color = subTextColor, 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis
                )
                if (app.isSystem) {
                    Text(
                        text = stringResource(R.string.doze_system_app), 
                        style = MaterialTheme.typography.labelSmall, 
                        color = if (isGlassActive) Color(0xFF4DB6AC) else MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            ZkmSwitch(
                checked = app.isWhitelisted, 
                onCheckedChange = { onToggle() },
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
fun DozeControlsTabGlass(
    viewModel: DozeModeViewModel,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    textColor: Color,
    subTextColor: Color,
    primaryColor: Color,
    topPadding: Dp,
    dozeState: String,
    dozeStats: DozeStats?,
    isCustomBg: Boolean
) {
    val isAutoRefresh by viewModel.isAutoRefresh.collectAsStateWithLifecycle()
    val gmsDozeMode by viewModel.gmsDozeMode.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding + 8.dp, bottom = 100.dp)
    ) {
        
        item {
            DozeStatCardsRow(dozeState, dozeStats, isCustomBg, isGlassActive, hazeState, textColor, primaryColor)
        }

        item {
            DozeSectionTitleGlass(stringResource(R.string.doze_gms_title), primaryColor, textColor)
            
            GlassDozeCard(isGlassActive, hazeState, cardColor) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(48.dp)
                                .background(if (isGlassActive) Color.White.copy(0.1f) else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha=0.1f), CircleShape), 
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Adb, 
                                contentDescription = null, 
                                tint = if (isGlassActive) Color(0xFF4DB6AC) else MaterialTheme.colorScheme.onSecondaryContainer, 
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.doze_force_gms), 
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), 
                                color = textColor
                            )
                            Text(
                                stringResource(R.string.doze_gms_subtitle), 
                                style = MaterialTheme.typography.bodySmall, 
                                color = subTextColor
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(if (isGlassActive) Color.Black.copy(0.2f) else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha=0.1f), RoundedCornerShape(24.dp)),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val modes = listOf(
                            stringResource(R.string.doze_mode_default),
                            stringResource(R.string.doze_mode_standard),
                            stringResource(R.string.doze_mode_aggressive)
                        )
                        modes.forEach { mode ->
                            val isSelected = mode == gmsDozeMode
                            val bg = if (isSelected) primaryColor else Color.Transparent
                            val tc = if (isSelected) Color.White else textColor
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(bg)
                                    .clickable { viewModel.setGmsDozeMode(mode) }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(mode, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = tc)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val desc = when(gmsDozeMode) {
                        stringResource(R.string.doze_mode_aggressive) -> stringResource(R.string.doze_aggressive_desc)
                        stringResource(R.string.doze_mode_standard) -> stringResource(R.string.doze_standard_desc)
                        else -> stringResource(R.string.doze_default_desc)
                    }
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = subTextColor)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            DozeSectionTitleGlass(stringResource(R.string.doze_manual_controls), primaryColor, textColor)
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), 
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassButton(
                    text = stringResource(R.string.doze_force_idle),
                    icon = Icons.Rounded.Bedtime,
                    onClick = { viewModel.forceIdle() },
                    enabled = dozeState != "Idle",
                    modifier = Modifier.weight(1f),
                    isGlassActive = isGlassActive,
                    primaryColor = primaryColor
                )
                GlassButton(
                    text = stringResource(R.string.doze_wake_up),
                    icon = Icons.Rounded.WbSunny,
                    onClick = { viewModel.unforceIdle() },
                    enabled = dozeState == "Idle",
                    modifier = Modifier.weight(1f),
                    isGlassActive = isGlassActive,
                    primaryColor = primaryColor,
                    isSecondary = true
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), 
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassButton(
                    text = stringResource(R.string.doze_step_state), 
                    icon = Icons.Rounded.SkipNext, 
                    onClick = { viewModel.stepIdleState() }, 
                    modifier = Modifier.weight(1f), 
                    isGlassActive = isGlassActive,
                    primaryColor = primaryColor,
                    isOutlined = true
                )
                GlassButton(
                    text = stringResource(R.string.doze_reset_stats), 
                    icon = Icons.Rounded.RestartAlt, 
                    onClick = { viewModel.resetStats() }, 
                    modifier = Modifier.weight(1f), 
                    isGlassActive = isGlassActive,
                    primaryColor = primaryColor,
                    isOutlined = true
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            DozeSectionTitleGlass(stringResource(R.string.doze_auto_refresh), primaryColor, textColor)
            
            GlassDozeCard(isGlassActive, hazeState, cardColor) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), 
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Sync, 
                        contentDescription = null, 
                        tint = primaryColor
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.doze_auto_refresh_title), 
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = textColor
                        )
                        Text(
                            stringResource(R.string.doze_auto_refresh_desc), 
                            style = MaterialTheme.typography.bodySmall, 
                            color = subTextColor
                        )
                    }
                    ZkmSwitch(
                        checked = isAutoRefresh, 
                        onCheckedChange = { viewModel.toggleAutoRefresh(it) },
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

        item {
            Spacer(modifier = Modifier.height(16.dp))
            DozeSectionTitleGlass(stringResource(R.string.doze_states_info), primaryColor, textColor)
            
            DozeStateInfoCardGlass(
                state = stringResource(R.string.doze_state_active), 
                description = stringResource(R.string.doze_state_active_desc), 
                isGlassActive = isGlassActive, 
                hazeState = hazeState,
                textColor = textColor, 
                subTextColor = subTextColor, 
                primaryColor = primaryColor
            )
            DozeStateInfoCardGlass(
                state = stringResource(R.string.doze_state_idle_pending), 
                description = stringResource(R.string.doze_state_idle_pending_desc), 
                isGlassActive = isGlassActive, 
                hazeState = hazeState,
                textColor = textColor, 
                subTextColor = subTextColor, 
                primaryColor = primaryColor
            )
            DozeStateInfoCardGlass(
                state = stringResource(R.string.doze_state_sensing), 
                description = stringResource(R.string.doze_state_sensing_desc), 
                isGlassActive = isGlassActive, 
                hazeState = hazeState,
                textColor = textColor, 
                subTextColor = subTextColor, 
                primaryColor = primaryColor
            )
            DozeStateInfoCardGlass(
                state = stringResource(R.string.doze_state_idle), 
                description = stringResource(R.string.doze_state_idle_desc), 
                isGlassActive = isGlassActive, 
                hazeState = hazeState,
                textColor = textColor, 
                subTextColor = subTextColor, 
                primaryColor = primaryColor
            )
            DozeStateInfoCardGlass(
                state = stringResource(R.string.doze_state_maintenance), 
                description = stringResource(R.string.doze_state_maintenance_desc), 
                isGlassActive = isGlassActive, 
                hazeState = hazeState,
                textColor = textColor, 
                subTextColor = subTextColor, 
                primaryColor = primaryColor
            )
        }
    }
}

@Composable
fun GlassDozeCard(
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(32.dp)
    
    val modifier = if (isGlassActive) {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
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
            .padding(horizontal = 16.dp)
            .clip(shape)
    }

    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isGlassActive) zkmGlassTint(MaterialTheme.colorScheme.secondaryContainer, zkmIsDarkTheme(), 0.40f) else MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        content()
    }
}

@Composable
fun GlassButton(
    text: String, 
    icon: ImageVector, 
    onClick: () -> Unit, 
    modifier: Modifier = Modifier, 
    enabled: Boolean = true,
    isGlassActive: Boolean = false,
    primaryColor: Color,
    isSecondary: Boolean = false,
    isOutlined: Boolean = false
) {
    val shape = RoundedCornerShape(24.dp)
    
    if (isOutlined) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(56.dp),
            shape = shape,
            border = if (isGlassActive) BorderStroke(1.dp, Color.White.copy(0.5f)) else null,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (isGlassActive) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text)
        }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(56.dp),
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isGlassActive) {
                    if (isSecondary) Color.White.copy(0.2f) else primaryColor.copy(alpha = 0.8f)
                } else MaterialTheme.colorScheme.primary,
                contentColor = if (isGlassActive) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = if (isGlassActive) Color.White.copy(0.1f) else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text)
        }
    }
}

@Composable
fun DozeSettingsTabGlass(
    viewModel: DozeModeViewModel,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    textColor: Color,
    subTextColor: Color,
    primaryColor: Color,
    topPadding: Dp,
    dozeState: String,
    dozeStats: DozeStats?,
    isCustomBg: Boolean
) {
    val dozeSettings by viewModel.dozeSettings.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding + 8.dp, bottom = 100.dp)
    ) {
        item {
            DozeStatCardsRow(dozeState, dozeStats, isCustomBg, isGlassActive, hazeState, textColor, primaryColor)
        }

        item {
            DozeSectionTitleGlass(stringResource(R.string.doze_global_settings), primaryColor, textColor)
            
            GlassDozeCard(isGlassActive, hazeState, cardColor) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), 
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, tint = primaryColor)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.doze_enable), 
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = textColor
                            )
                            Text(
                                stringResource(R.string.doze_enable_desc), 
                                style = MaterialTheme.typography.bodySmall, 
                                color = subTextColor
                            )
                        }
                        ZkmSwitch(
                            checked = dozeSettings?.isEnabled ?: true, 
                            onCheckedChange = { viewModel.setDozeEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = primaryColor,
                                uncheckedThumbColor = if (isGlassActive) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else Color.Gray,
                                uncheckedTrackColor = if (isGlassActive) Color.White.copy(0.3f) else Color.Gray.copy(0.3f)
                            )
                        )
                    }
                    HorizontalDivider(color = if (isGlassActive) Color.White.copy(0.1f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.2f))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), 
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Speed, contentDescription = null, tint = primaryColor)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.doze_aggressive_mode), 
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = textColor
                            )
                            Text(
                                stringResource(R.string.doze_aggressive_mode_desc), 
                                style = MaterialTheme.typography.bodySmall, 
                                color = subTextColor
                            )
                        }
                        ZkmSwitch(
                            checked = dozeSettings?.isAggressive ?: false, 
                            onCheckedChange = { viewModel.setAggressiveDoze(it) },
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
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            DozeSectionTitleGlass(stringResource(R.string.doze_battery_tips), primaryColor, textColor)
            
            DozeTipCardGlass(
                icon = Icons.Rounded.Adb, 
                title = stringResource(R.string.doze_tip_gms_title), 
                description = stringResource(R.string.doze_tip_gms_desc),
                isGlassActive = isGlassActive,
                hazeState = hazeState,
                textColor = textColor,
                subTextColor = subTextColor,
                primaryColor = primaryColor
            )
            DozeTipCardGlass(
                icon = Icons.Rounded.AppBlocking, 
                title = stringResource(R.string.doze_tip_whitelist_title), 
                description = stringResource(R.string.doze_tip_whitelist_desc),
                isGlassActive = isGlassActive,
                hazeState = hazeState,
                textColor = textColor,
                subTextColor = subTextColor,
                primaryColor = primaryColor
            )
        }
    }
}

@Composable
fun DozeSectionTitleGlass(title: String, primaryColor: Color, textColor: Color) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = if (textColor == Color.White) primaryColor else MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
    )
}

@Composable
fun DozeTipCardGlass(
    icon: ImageVector, 
    title: String, 
    description: String,
    isGlassActive: Boolean,
    hazeState: HazeState,
    textColor: Color,
    subTextColor: Color,
    primaryColor: Color
) {
    val shape = RoundedCornerShape(24.dp)
    
    val modifier = if (isGlassActive) {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .zkmCardHazeEffect(shape = shape,
                state = hazeState,
                style = ZkmHazeStyleCache.of(
                    backgroundColor = Color.White.copy(alpha = 0.05f),
                    blurRadius = 20.dp,
                    noiseFactor = 0.05f,
                    tints = emptyList()
                )
            )
            .zkmCardBorder(1.dp, Color.White.copy(alpha = 0.1f), shape)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    }

    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isGlassActive) zkmCardFill(zkmGlassTint(MaterialTheme.colorScheme.surfaceContainerHighest, zkmIsDarkTheme(), 0.40f)) else MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp)
                    .background(if (isGlassActive) primaryColor.copy(0.3f) else MaterialTheme.colorScheme.primaryContainer, CircleShape), 
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon, 
                    contentDescription = null, 
                    tint = if (isGlassActive) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.onPrimaryContainer, 
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title, 
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = textColor
                )
                Text(
                    text = description, 
                    style = MaterialTheme.typography.bodySmall, 
                    color = subTextColor
                )
            }
        }
    }
}

@Composable
fun DozeStateInfoCardGlass(
    state: String, 
    description: String,
    isGlassActive: Boolean,
    hazeState: HazeState,
    textColor: Color,
    subTextColor: Color,
    primaryColor: Color
) {
    val shape = RoundedCornerShape(24.dp)
    
    val modifier = if (isGlassActive) {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .zkmCardHazeEffect(shape = shape,
                state = hazeState,
                style = ZkmHazeStyleCache.of(
                    backgroundColor = Color.White.copy(alpha = 0.03f),
                    blurRadius = 20.dp,
                    noiseFactor = 0.05f,
                    tints = emptyList()
                )
            )
            .zkmCardBorder(1.dp, Color.White.copy(alpha = 0.1f), shape)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    }

    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isGlassActive) zkmCardFill(zkmGlassTint(MaterialTheme.colorScheme.surfaceContainerLow, zkmIsDarkTheme(), 0.40f)) else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isGlassActive) primaryColor.copy(0.3f) else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = state.take(1),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isGlassActive) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = state,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = textColor
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = subTextColor
                )
            }
        }
    }
}
