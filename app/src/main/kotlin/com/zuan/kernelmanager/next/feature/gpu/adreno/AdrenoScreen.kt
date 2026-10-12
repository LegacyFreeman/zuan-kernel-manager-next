/*
 * Copyright (c) 2026 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalHazeMaterialsApi::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.zuan.kernelmanager.next.feature.gpu.adreno

import com.zuan.kernelmanager.next.ui.components.zkmAppBarHazeStyle
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundContentColor
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundSubContentColor
import com.zuan.kernelmanager.next.ui.components.zkmCustomBackgroundCardColor
import com.zuan.kernelmanager.next.ui.components.zkmGlassFallbackTint
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.compositeOver
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
import com.zuan.kernelmanager.next.feature.gpu.adreno.tabs.*
import com.zuan.kernelmanager.next.feature.gpu.adreno.viewmodel.AdrenoViewModel
import com.zuan.kernelmanager.next.feature.settings.BgType
import com.zuan.kernelmanager.next.feature.settings.SettingsViewModel
import com.zuan.kernelmanager.next.feature.settings.WeatherEffect
import com.zuan.kernelmanager.next.ui.components.VideoWallpaperPlayer
import com.zuan.kernelmanager.next.ui.components.WeatherEffectOverlay
import com.zuan.kernelmanager.next.ui.theme.ThemeMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import com.zuan.kernelmanager.next.ui.components.zkmHazeEffect
import com.zuan.kernelmanager.next.ui.components.zkmHazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import kotlinx.coroutines.launch
import com.zuan.kernelmanager.next.ui.components.ZkmHazeStyleCache

data class AdrenoTabItem(
    val titleRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
fun AdrenoScreen(
    navController: NavController,
    hazeState: HazeState = remember { HazeState() },
    viewModel: AdrenoViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val tabs = remember {
        listOf(
            AdrenoTabItem(R.string.adreno_tab_freq, Icons.Filled.Speed),
            AdrenoTabItem(R.string.adreno_tab_power, Icons.Filled.Bolt),
            AdrenoTabItem(R.string.adreno_tab_bus, Icons.Filled.Memory),
            AdrenoTabItem(R.string.adreno_tab_thermal, Icons.Filled.Thermostat),
            AdrenoTabItem(R.string.adreno_tab_advanced, Icons.Filled.Tune),
        )
    }

    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current

    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeightDp = if (headerHeightPx > 0) with(density) { headerHeightPx.toDp() } else 145.dp

    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val isSystemDark = isSystemInDarkTheme()
    val useDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM_DEFAULT -> isSystemDark
    }

    val isDynamic by settingsViewModel.isDynamicColor.collectAsStateWithLifecycle()
    val themeColorName by settingsViewModel.currentThemeColor.collectAsStateWithLifecycle()
    val isCustomColor by settingsViewModel.isCustomColor.collectAsStateWithLifecycle()
    val customPrimary by settingsViewModel.customPrimaryColor.collectAsStateWithLifecycle()

    val bgType by settingsViewModel.bgType.collectAsStateWithLifecycle()
    val isCustomBg = bgType != BgType.SYSTEM
    val bgUriString by settingsViewModel.backgroundImageUri.collectAsStateWithLifecycle()
    val isVideo by settingsViewModel.isVideoWallpaper.collectAsStateWithLifecycle()
    val isBgBlur by settingsViewModel.isBgBlur.collectAsStateWithLifecycle()
    val blurStrength by settingsViewModel.blurStrength.collectAsStateWithLifecycle()
    val bgSaturation by settingsViewModel.bgSaturation.collectAsStateWithLifecycle()
    val bgContrast by settingsViewModel.bgContrast.collectAsStateWithLifecycle()
    val isHazeEnabled by settingsViewModel.isHazeEnabled.collectAsStateWithLifecycle()
    val cardDarkness by settingsViewModel.cardDarkness.collectAsStateWithLifecycle()
    val weatherEffect by settingsViewModel.weatherEffect.collectAsStateWithLifecycle()
    val weatherIntensity by settingsViewModel.weatherIntensity.collectAsStateWithLifecycle()

    val effectivePrimary = remember(isDynamic, themeColorName, isCustomColor, customPrimary) {
        when {
            isCustomColor -> Color(customPrimary)
            isDynamic -> Color.Unspecified
            else -> themeColorName.primary
        }
    }

    val finalPrimary = if (effectivePrimary == Color.Unspecified) MaterialTheme.colorScheme.primary else effectivePrimary
    val (themeBgColor, themeCardColor) = generateAdrenoBackgroundColors(useDarkTheme, finalPrimary)

    val isGlassActive = isHazeEnabled && isCustomBg
    val mainBackgroundColor = if (isCustomBg) Color.Transparent else themeBgColor

    val targetCardColor = when {
        isGlassActive -> zkmGlassFallbackTint(zkmIsDarkTheme())
        isCustomBg -> themeCardColor.copy(alpha = cardDarkness)
        else -> themeCardColor
    }

    val shouldUseDarkText = !useDarkTheme
    val contentColor = if (shouldUseDarkText) Color(0xFF111111) else Color(0xFFEEEEEE)
    val subContentColor = if (shouldUseDarkText) Color(0xFF666666) else Color(0xFFAAAAAA)

    // Padding atas agar tidak tertutup header saat pertama buka, padding bawah 400.dp agar selalu bisa di-scroll
    val tabContentPadding = PaddingValues(top = headerHeightDp + 16.dp, bottom = 400.dp)

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(mainBackgroundColor),
        ) {
            // LAYER 0: BACKGROUND
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isCustomBg) Color.Transparent else mainBackgroundColor)
                    .zkmHazeSource(state = hazeState, zIndex = 0f),
            ) {
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

                    if (isVideo) {
                        VideoWallpaperPlayer(uri = uri, modifier = Modifier.fillMaxSize().then(blurModifier))
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(uri).build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().then(blurModifier),
                            contentScale = ContentScale.Crop,
                            colorFilter = colorFilter,
                        )
                    }
                    if (bgContrast > 0f) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = bgContrast)))
                    }
                }
                WeatherEffectOverlay(effect = weatherEffect, intensity = weatherIntensity, modifier = Modifier.fillMaxSize())
            }

            // LAYER 1: CONTENT (Tembus pandang dari Y=0 ke balik header)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zkmHazeSource(state = hazeState, zIndex = 1f),
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 2,
                ) { page ->
                    // Padding horizontal 16.dp memberikan celah samping agar kartu tidak menempel di bezel layar
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        when (page) {
                            0 -> AdrenoFreqTab(
                                state = state.freqState,
                                hazeState = hazeState,
                                cardColor = targetCardColor,
                                isGlassActive = isGlassActive,
                                accentColor = finalPrimary,
                                useDarkTheme = useDarkTheme,
                                onUpdateMin = { viewModel.updateFreq("min", it) },
                                onUpdateMax = { viewModel.updateFreq("max", it) },
                                onUpdateGov = { viewModel.updateGov(it) },
                                contentPadding = tabContentPadding
                            )
                            1 -> AdrenoPowerTab(
                                state = state.powerState,
                                hazeState = hazeState,
                                cardColor = targetCardColor,
                                isGlassActive = isGlassActive,
                                accentColor = finalPrimary,
                                onToggleThrottling = { viewModel.updateGPUThrottling(it) },
                                onUpdateBoost = { viewModel.updateAdrenoBoost(it) },
                                contentPadding = tabContentPadding
                            )
                            2 -> AdrenoBusTab(
                                state = state.busState,
                                hazeState = hazeState,
                                cardColor = targetCardColor,
                                isGlassActive = isGlassActive,
                                accentColor = finalPrimary,
                                onUpdateBusFreq = { name, target, freq -> viewModel.updateBusFreq(name, target, freq) },
                                onUpdateDevfreq = { path, param, value -> viewModel.updateDevfreq(path, param, value) },
                                contentPadding = tabContentPadding
                            )
                            3 -> AdrenoThermalTab(
                                state = state.thermalState,
                                hazeState = hazeState,
                                cardColor = targetCardColor,
                                isGlassActive = isGlassActive,
                                accentColor = finalPrimary,
                                contentPadding = tabContentPadding
                            )
                            4 -> AdrenoAdvancedTab(
                                state = state.advancedState,
                                hazeState = hazeState,
                                cardColor = targetCardColor,
                                isGlassActive = isGlassActive,
                                accentColor = finalPrimary,
                                onToggleIdler = { viewModel.toggleIdler(it) },
                                onUpdateIdlerParam = { param, value -> viewModel.updateIdlerParam(param, value) },
                                onToggleSimpleGpu = { viewModel.toggleSimpleGpu(it) },
                                onUpdateSimpleGpuParam = { param, value -> viewModel.updateSimpleGpuParam(param, value) },
                                onToggleKgsl = { param, enable -> viewModel.updateKgslToggle(param, enable) },
                                onUpdateKgslPwr = { param, value -> viewModel.updateKgslPwrlevel(param, value) },
                                contentPadding = tabContentPadding
                            )
                        }
                    }
                }
            }

            // LAYER 2: FLOATING HEADER (PROGRESSIVE BLUR)
            val headerHazeStyle = zkmAppBarHazeStyle(themeBgColor, isCustomBg)

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        if (coordinates.size.height > 0 && headerHeightPx != coordinates.size.height) {
                            headerHeightPx = coordinates.size.height
                        }
                    }.zkmHazeEffect(
                        state = hazeState,
                        style = headerHazeStyle,
                    ) {
                        progressive = HazeProgressive.verticalGradient(
                            startIntensity = 1f,
                            endIntensity = 0f,
                        )
                    }.zIndex(2f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding(),
                ) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    stringResource(R.string.adreno_gpu_title),
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = contentColor,
                                )
                                Text(
                                    stringResource(R.string.adreno_gpu_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = subContentColor,
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.action_back),
                                    tint = contentColor,
                                )
                            }
                        },
                        actions = {
                            ZkmHelpButton(NavigationRoute.Adreno.route, tint = contentColor)
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                            actionIconContentColor = contentColor,
                            navigationIconContentColor = contentColor,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    AdrenoExpressiveTabRow(
                        tabs = tabs,
                        selectedIndex = pagerState.currentPage,
                        onTabSelected = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                        activeColor = finalPrimary,
                        subContentColor = subContentColor,
                        hazeState = hazeState,
                        isGlassActive = isGlassActive,
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }
    }
}

@Composable
private fun AdrenoExpressiveTabRow(
    tabs: List<AdrenoTabItem>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    activeColor: Color,
    subContentColor: Color,
    hazeState: HazeState? = null,
    isGlassActive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex) { listState.animateScrollToItem(selectedIndex, -100) }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        itemsIndexed(tabs) { index, tab ->
            val isSelected = selectedIndex == index

            var tabModifier = Modifier
                .height(38.dp)
                .clip(CircleShape)
                .clickable { onTabSelected(index) }

            val iconColor: Color
            val textColor: Color

            if (isSelected && isGlassActive && hazeState != null) {
                tabModifier = tabModifier
                    .zkmHazeEffect(
                        state = hazeState,
                        style = ZkmHazeStyleCache.of(
                            backgroundColor = activeColor.copy(alpha = 0.3f),
                            blurRadius = 16.dp,
                            noiseFactor = 0.05f,
                            tints = emptyList(),
                        ),
                    ).border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)

                iconColor = activeColor
                textColor = activeColor
            } else {
                val containerColor by animateColorAsState(
                    targetValue = if (isSelected) activeColor.copy(alpha = 0.14f) else Color.Transparent,
                    label = "tabBg",
                )
                tabModifier = tabModifier.background(containerColor)

                val animIconColor by animateColorAsState(if (isSelected) activeColor else subContentColor, label = "icon")
                val animTextColor by animateColorAsState(if (isSelected) activeColor else subContentColor, label = "text")
                iconColor = animIconColor
                textColor = animTextColor
            }

            Row(
                modifier = tabModifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(tab.icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(tab.titleRes),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = textColor,
                )
            }
        }
    }
}

@Composable
fun generateAdrenoBackgroundColors(
    isDark: Boolean,
    primaryColor: Color,
): Pair<Color, Color> = com.zuan.kernelmanager.next.ui.components.ZkmScreenPalette.colors(isDark, primaryColor)
