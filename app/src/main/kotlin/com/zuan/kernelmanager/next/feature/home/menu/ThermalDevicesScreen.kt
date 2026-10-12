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
import com.zuan.kernelmanager.next.ui.components.zkmCardFill
import com.zuan.kernelmanager.next.ui.components.zkmCardBorder
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundContentColor
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundSubContentColor
import com.zuan.kernelmanager.next.ui.components.zkmCustomBackgroundCardColor
import com.zuan.kernelmanager.next.ui.components.zkmGlassFallbackTint
import com.zuan.kernelmanager.next.ui.components.zkmGlassTint
import com.zuan.kernelmanager.next.ui.components.zkmGlassCard
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import androidx.compose.ui.zIndex
import com.zuan.kernelmanager.next.ui.components.BlurredAlertDialog
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.scale
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
import kotlinx.coroutines.launch
import com.zuan.kernelmanager.next.ui.components.ZkmHazeStyleCache
import com.zuan.kernelmanager.next.ui.theme.zkmIconShape
import com.zuan.kernelmanager.next.ui.theme.ZkmIconGlyph
import com.zuan.kernelmanager.next.ui.components.ZkmSwitch
import com.zuan.kernelmanager.next.ui.components.ZkmSlider

@Composable
fun generateThermalBackgroundColors(isDark: Boolean, primaryColor: Color): Pair<Color, Color> = com.zuan.kernelmanager.next.ui.components.ZkmScreenPalette.colors(isDark, primaryColor)

@Composable
fun ThermalDevicesScreen(
    navController: NavController,
    viewModel: ThermalDevicesViewModel = viewModel(),
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

    val (themeBgColor, themeCardColor) = if (bgType == BgType.SYSTEM) generateThermalBackgroundColors(isDarkTheme, effectivePrimary) else Pair(Color.Transparent, Color(0xFFFFFFFF))
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
    val dividerColor = if (isCustomBg) Color.White.copy(0.2f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
    
    val activeZones by viewModel.filteredZones.collectAsStateWithLifecycle()
    val disabledZones by viewModel.disabledZones.collectAsStateWithLifecycle()
    val coolingDevices by viewModel.coolingDevices.collectAsStateWithLifecycle()
    val stats by viewModel.thermalStats.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val isAutoRefresh by viewModel.isAutoRefresh.collectAsStateWithLifecycle()
    val thermalPolicy by viewModel.thermalPolicy.collectAsStateWithLifecycle()
    val showTripPoints by viewModel.showTripPoints.collectAsStateWithLifecycle()

    var showPolicyDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    
    val pagerState = rememberPagerState(pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()
    val hazeState = rememberHazeState()

    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeightDp = with(localDensity) { headerHeightPx.toDp() }

    Box(modifier = Modifier.fillMaxSize().background(mainBackgroundColor)) {
        
        // --- BACKGROUND LAYER ---
        if (isCustomBg && bgUriString != null) {
            val blurModifier = if (isBgBlur && blurStrength > 0f) Modifier.blur(blurStrength.dp) else if (isBgBlur) Modifier.blur(20.dp) else Modifier
            val colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(bgSaturation) })
            val uri = Uri.parse(bgUriString)

            Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState, zIndex = 0f)) {
                if (isVideo) {
                    VideoWallpaperPlayer(uri = uri, modifier = Modifier.fillMaxSize().then(blurModifier))
                } else {
                    AsyncImage(model = ImageRequest.Builder(context).data(uri).build(), contentDescription = null, modifier = Modifier.fillMaxSize().then(blurModifier), contentScale = ContentScale.Crop, colorFilter = colorFilter)
                }
                if (bgContrast > 0f) Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = bgContrast)))
            }
        } else {
            // Background harus jadi child hazeSource (bukan modifier sebelum
            // hazeSource) supaya ikut terekam ke buffer haze — fix progressive
            // blur appbar di mode non-custom.
            Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState, zIndex = 0f)) {
                Box(modifier = Modifier.fillMaxSize().background(themeBgColor))
            }
        }
        
        WeatherEffectOverlay(effect = weatherEffect, intensity = weatherIntensity, modifier = Modifier.fillMaxSize())

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
                        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.Top) { page ->
                            when (page) {
                                0 -> { 
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(top = headerHeightDp, bottom = 100.dp)
                                    ) {
                                        // Pindah ke sini agar bisa ter-scroll di bawah blur header
                                        item {
                                            GlassFilterChips(viewModel.availableFilters, selectedFilter, { viewModel.onFilterSelected(it) }, isGlassActive, effectivePrimary, textColor)
                                        }
                                        items(activeZones, key = { it.id }) { zone ->
                                            ThermalZoneCardGlass(zone, showTripPoints[zone.id], { viewModel.toggleThermalZone(zone) }, { viewModel.loadTripPoints(zone) }, { viewModel.clearTripPoints(zone.id) }, isGlassActive, hazeState, finalCardColor, textColor, subTextColor, effectivePrimary)
                                            if (!isGlassActive) HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = dividerColor.copy(alpha = 0.5f))
                                        }
                                    }
                                }
                                1 -> { 
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(top = headerHeightDp + 8.dp, bottom = 100.dp)
                                    ) {
                                        items(coolingDevices, key = { it.id }) { device ->
                                            CoolingDeviceCardGlass(device, { viewModel.setCoolingState(device, it) }, isGlassActive, hazeState, finalCardColor, textColor, subTextColor, effectivePrimary)
                                            if (!isGlassActive) HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = dividerColor.copy(alpha = 0.5f))
                                        }
                                    }
                                }
                                2 -> { 
                                    if (disabledZones.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
                                            Text(stringResource(R.string.thermal_no_disabled), color = subTextColor) 
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(top = headerHeightDp + 8.dp, bottom = 100.dp)
                                        ) {
                                            items(disabledZones, key = { "disabled_${it.id}" }) { zone ->
                                                ThermalZoneCardGlass(zone, showTripPoints[zone.id], { viewModel.toggleThermalZone(zone) }, { viewModel.loadTripPoints(zone) }, { viewModel.clearTripPoints(zone.id) }, isGlassActive, hazeState, finalCardColor, textColor, subTextColor, effectivePrimary)
                                                if (!isGlassActive) HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = dividerColor.copy(alpha = 0.5f))
                                            }
                                        }
                                    }
                                }
                                3 -> { 
                                    AdvanceTabContent(viewModel, isGlassActive, hazeState, finalCardColor, textColor, subTextColor, effectivePrimary, headerHeightDp)
                                }
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
                        // Header Top Bar
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 8.dp, end = 8.dp, bottom = 8.dp), 
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { navController.popBackStack() }) { 
                                Icon(Icons.Rounded.ArrowBack, contentDescription = null, tint = textColor) 
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.lp_thermal_manager), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = textColor)
                            
                            Spacer(modifier = Modifier.weight(1f))
                            
                            ZkmHelpButton(NavigationRoute.ThermalDevices.route, tint = textColor)

                            // Menu Dropdown untuk Fitur yang tersembunyi
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Rounded.MoreVert, contentDescription = "Options", tint = textColor)
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                    containerColor = if(isGlassActive) Color(0xFF1D1D1D) else MaterialTheme.colorScheme.surface
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Thermal Policy: ${thermalPolicy.replaceFirstChar { it.uppercase() }}", color = textColor) },
                                        onClick = { showMenu = false; showPolicyDialog = true },
                                        leadingIcon = { Icon(Icons.Rounded.Policy, null, tint = effectivePrimary) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Auto Refresh", color = textColor) },
                                        trailingIcon = { 
                                            ZkmSwitch(
                                                checked = isAutoRefresh, 
                                                onCheckedChange = { viewModel.toggleAutoRefresh(it) },
                                                modifier = Modifier.scale(0.8f)
                                            ) 
                                        },
                                        onClick = { viewModel.toggleAutoRefresh(!isAutoRefresh) }
                                    )
                                }
                            }
                        }
                        
                        // Header Stats Card (Fix typo padding di baris ini)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), 
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            GlassStatCardThermal("Max Temp", "${stats.maxTemp}°C", Icons.Rounded.Whatshot, effectivePrimary.copy(0.8f), Color.White, Modifier.weight(1f), isGlassActive, hazeState)
                            GlassStatCardThermal("Avg Temp", "${stats.avgTemp.toInt()}°C", Icons.Rounded.Speed, effectivePrimary.copy(0.4f), textColor, Modifier.weight(1f), isGlassActive, hazeState)
                            GlassStatCardThermal("Zones", "${stats.activeZones}/${stats.totalZones}", Icons.Rounded.Analytics, effectivePrimary.copy(0.2f), subTextColor, Modifier.weight(1f), isGlassActive, hazeState)
                        }

                        // Tab Row (Dibungkus Surface - Bumbu Rahasia)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(22.dp),
                            color = if (isCustomBg) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
                        ) {
                            ScrollableTabRow(
                                selectedTabIndex = pagerState.currentPage,
                                containerColor = Color.Transparent,
                                contentColor = effectivePrimary,
                                edgePadding = 6.dp,
                                divider = {},
                                indicator = { tabPositions ->
                                    if (pagerState.currentPage < tabPositions.size) {
                                        TabRowDefaults.SecondaryIndicator(
                                            modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]).padding(horizontal = 16.dp),
                                            height = 3.dp,
                                            color = effectivePrimary
                                        )
                                    }
                                }
                            ) {
                                val tabs = listOf(
                                    stringResource(R.string.thermal_tab_zones), 
                                    stringResource(R.string.thermal_tab_cooling), 
                                    stringResource(R.string.thermal_tab_disabled), 
                                    "Advance"
                                )
                                tabs.forEachIndexed { index, title ->
                                    ThermalTabItemGlass(
                                        selected = pagerState.currentPage == index, 
                                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } }, 
                                        text = title, 
                                        textColor = textColor,
                                        subTextColor = subTextColor,
                                        primaryColor = effectivePrimary,
                                        isGlassActive = isGlassActive
                                    )
                                }
                            }
                        }
                        
                        // Ruang kosong untuk progressive blur fading out
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }

        if (showPolicyDialog) {
            ThermalPolicyDialog(thermalPolicy, { showPolicyDialog = false }, { viewModel.setThermalPolicy(it); showPolicyDialog = false }, isGlassActive, textColor, subTextColor, effectivePrimary)
        }
    }
}

@Composable
fun AdvanceTabContent(
    viewModel: ThermalDevicesViewModel,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    textColor: Color,
    subTextColor: Color,
    primaryColor: Color,
    topPadding: Dp
) {
    val isPpmSupported by viewModel.isPpmSupported.collectAsStateWithLifecycle()
    val isGpuMaliSupported by viewModel.isGpuMaliSupported.collectAsStateWithLifecycle()
    val isGedDcsSupported by viewModel.isGedDcsSupported.collectAsStateWithLifecycle()
    val isGpuIgnoreSupported by viewModel.isGpuIgnoreSupported.collectAsStateWithLifecycle()
    val isDvfsSupported by viewModel.isDvfsSupported.collectAsStateWithLifecycle()
    val isGpuAlwaysOnSupported by viewModel.isGpuAlwaysOnSupported.collectAsStateWithLifecycle()
    val isCciSupported by viewModel.isCciSupported.collectAsStateWithLifecycle()
    val isCpuPowerModeSupported by viewModel.isCpuPowerModeSupported.collectAsStateWithLifecycle()
    val isAdrenoSupported by viewModel.isAdrenoSupported.collectAsStateWithLifecycle()
    val isAdrenoLpmSupported by viewModel.isAdrenoLpmSupported.collectAsStateWithLifecycle()
    val isUfshcSupported by viewModel.isUfshcSupported.collectAsStateWithLifecycle()
    val isSconfigSupported by viewModel.isSconfigSupported.collectAsStateWithLifecycle()

    val ppmEnabled by viewModel.ppmEnabled.collectAsStateWithLifecycle()
    val ppmPolicies by viewModel.ppmPolicies.collectAsStateWithLifecycle()
    val gedDcsDisabled by viewModel.gedDcsDisabled.collectAsStateWithLifecycle()
    val gpuIgnore by viewModel.gpuIgnore.collectAsStateWithLifecycle()
    val dvfsDisabled by viewModel.dvfsDisabled.collectAsStateWithLifecycle()
    val gpuAlwaysOn by viewModel.gpuAlwaysOn.collectAsStateWithLifecycle()
    val cciModeEnabled by viewModel.cciModeEnabled.collectAsStateWithLifecycle()
    val cpuPowerMode by viewModel.cpuPowerMode.collectAsStateWithLifecycle()
    val adrenoPwrMax by viewModel.adrenoPowerLevelMax.collectAsStateWithLifecycle()
    val adrenoThrottle by viewModel.adrenoThrottlingDisabled.collectAsStateWithLifecycle()
    val adrenoBcl by viewModel.adrenoBclDisabled.collectAsStateWithLifecycle()
    val adrenoPreempt by viewModel.adrenoPreemptionDisabled.collectAsStateWithLifecycle()
    val adrenoLpmPred by viewModel.adrenoLpmPredDisabled.collectAsStateWithLifecycle()
    val adrenoLpmSleep by viewModel.adrenoLpmSleepDisabled.collectAsStateWithLifecycle()
    val adrenoUfshc by viewModel.adrenoUfshcThrottlingDisabled.collectAsStateWithLifecycle()
    val globalThermalLevel by viewModel.globalThermalLevel.collectAsStateWithLifecycle()
    val sconfigIndex by viewModel.sconfigProfile.collectAsStateWithLifecycle()
    val propDisabled by viewModel.propDisabled.collectAsStateWithLifecycle()
    val processDisabled by viewModel.processDisabled.collectAsStateWithLifecycle()
    val shellResult by viewModel.shellCheckResult.collectAsStateWithLifecycle()
    val applyOnBoot by viewModel.applyOnBoot.collectAsStateWithLifecycle()

    var pendingDangerAction by remember { mutableStateOf<DangerAction?>(null) }
    pendingDangerAction?.let { action ->
        DangerConfirmDialog(
            action = action,
            onConfirm = {
                when (action) {
                    is DangerAction.DisableAllZones -> viewModel.setGlobalThermalLevel(action.level)
                    is DangerAction.KillThermalProcess -> viewModel.setProcessDisabledState(true)
                    is DangerAction.DisableThermalProp -> viewModel.setPropDisabledState(true)
                    is DangerAction.DisableGpuThrottling -> viewModel.setAdrenoThrottle(true)
                }
            },
            onDismiss = { pendingDangerAction = null }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(), 
        contentPadding = PaddingValues(top = topPadding + 8.dp, bottom = 100.dp)
    ) {
        item {
            AdvanceSectionCard("Apply on Boot", "Automatically re-apply all saved tweaks after device reboot.", isGlassActive, hazeState, cardColor, textColor, subTextColor) {
                AdvanceToggleItem("APPLY_ON_BOOT", applyOnBoot, { viewModel.setApplyOnBoot(it) }, primaryColor, textColor)
            }
        }

        if (isPpmSupported) {
            item {
                AdvanceSectionCard("Mali PPM Control", "Manage Power Policy Modules (PPM) to fine-tune CPU/GPU performance.", isGlassActive, hazeState, cardColor, textColor, subTextColor) {
                    AdvanceToggleItem("ENABLED_PPM", ppmEnabled, { viewModel.setPpmEnabledState(it) }, primaryColor, textColor)
                    val ppmList = listOf("PPM_POLICY_PTPOD", "PPM_POLICY_UT", "PPM_POLICY_FORCE_LIMIT", "PPM_POLICY_PWR_THRO", "PPM_POLICY_THERMAL", "PPM_POLICY_DLPT", "PPM_POLICY_HARD_USER_LIMIT", "PPM_POLICY_USER_LIMIT", "PPM_POLICY_LCM_OFF", "PPM_POLICY_SYS_BOOST")
                    ppmList.forEachIndexed { index, name -> AdvanceToggleItem(name, ppmPolicies[index], { viewModel.setPpmPolicyState(index, it) }, primaryColor, textColor) }
                }
            }
        }

        if (isGpuMaliSupported) {
            item {
                AdvanceSectionCard("GPU Mali Limit Control", "On/off GPU limit for maximum performance.", isGlassActive, hazeState, cardColor, textColor, subTextColor) {
                    if (isGedDcsSupported) AdvanceToggleItem("DISABLED GED DCS", gedDcsDisabled, { viewModel.setGedDcsState(it) }, primaryColor, textColor)
                    if (isGpuIgnoreSupported) AdvanceToggleItem("GPU_LIMITED_IGNORE", gpuIgnore, { viewModel.setGpuIgnoreState(it) }, primaryColor, textColor)
                    if (isDvfsSupported) AdvanceToggleItem("DISABLED DVFS", dvfsDisabled, { viewModel.setDvfsState(it) }, primaryColor, textColor)
                    if (isGpuAlwaysOnSupported) AdvanceToggleItem("GPU_ALWAYS_ON", gpuAlwaysOn, { viewModel.setGpuAlwaysOnState(it) }, primaryColor, textColor)
                    if (isCciSupported) AdvanceToggleItem("CPUFREQ_CCI_MODE_ENABLED", cciModeEnabled, { viewModel.setCciModeState(it) }, primaryColor, textColor)

                    if (isCpuPowerModeSupported) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(stringResource(R.string.lp_cpufreq_power_mode), style = MaterialTheme.typography.labelMedium, color = textColor)
                        Text(stringResource(R.string.lp_adjust_power_mode_cpufreq_mali_0_default_3_max), style = MaterialTheme.typography.labelSmall, color = subTextColor)
                        var localPowerMode by remember(cpuPowerMode) { mutableFloatStateOf(cpuPowerMode) }
                        ZkmSlider(
                            value = localPowerMode,
                            onValueChange = { localPowerMode = it },
                            onValueChangeFinished = { viewModel.setCpuPowerModeState(localPowerMode) },
                            valueRange = 0f..3f, steps = 2,
                            colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor, inactiveTrackColor = subTextColor.copy(alpha = 0.3f))
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.lp_v_0), style = MaterialTheme.typography.labelSmall, color = subTextColor)
                            Text("Current: ${localPowerMode.toInt()}", style = MaterialTheme.typography.labelSmall, color = primaryColor, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.lp_v_3), style = MaterialTheme.typography.labelSmall, color = subTextColor)
                        }
                    }
                }
            }
        }

        if (isAdrenoSupported || isAdrenoLpmSupported || isUfshcSupported) {
            item {
                AdvanceSectionCard("Snapdragon Adreno", "Manage KGSL Adreno thermal limits, and power behavior.", isGlassActive, hazeState, cardColor, textColor, subTextColor) {
                    if (isAdrenoSupported) {
                        AdvanceToggleItem("POWER_LEVEL_MAX", adrenoPwrMax, { viewModel.setAdrenoPwrMax(it) }, primaryColor, textColor)
                        AdvanceToggleItem("DISABLED THROTTLING", adrenoThrottle, { checked ->
                            if (checked) pendingDangerAction = DangerAction.DisableGpuThrottling
                            else viewModel.setAdrenoThrottle(false)
                        }, primaryColor, textColor)
                        AdvanceToggleItem("BCL", adrenoBcl, { viewModel.setAdrenoBcl(it) }, primaryColor, textColor)
                        AdvanceToggleItem("PREEMPTION", adrenoPreempt, { viewModel.setAdrenoPreempt(it) }, primaryColor, textColor)
                    }
                    if (isAdrenoLpmSupported) {
                        AdvanceToggleItem("QCOM_LPM_PREDICTION_OFF", adrenoLpmPred, { viewModel.setAdrenoLpmPred(it) }, primaryColor, textColor)
                        AdvanceToggleItem("QCOM_LPM_SLEEP_DISABLED", adrenoLpmSleep, { viewModel.setAdrenoLpmSleep(it) }, primaryColor, textColor)
                    }
                    if (isUfshcSupported) {
                        AdvanceToggleItem("DISABLED UFSHC THROTTLING", adrenoUfshc, { viewModel.setAdrenoUfshc(it) }, primaryColor, textColor)
                    }
                }
            }
        }

        item {
            AdvanceSectionCard("Thermal Zone Level Disabled", "0 = Default, 1 = Disable CPU/GPU Thermal, 2 = Disable All", isGlassActive, hazeState, cardColor, textColor, subTextColor) {
                var localThermalLevel by remember(globalThermalLevel) { mutableFloatStateOf(globalThermalLevel) }
                ZkmSlider(
                    value = localThermalLevel,
                    onValueChange = { localThermalLevel = it },
                    onValueChangeFinished = {
                        if (localThermalLevel.toInt() == 2) {
                            pendingDangerAction = DangerAction.DisableAllZones(localThermalLevel)
                        } else {
                            viewModel.setGlobalThermalLevel(localThermalLevel)
                        }
                    },
                    valueRange = 0f..2f, steps = 1,
                    colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor, inactiveTrackColor = subTextColor.copy(alpha = 0.3f))
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.lp_level_0), style = MaterialTheme.typography.labelSmall, color = subTextColor)
                    Text("Level ${localThermalLevel.toInt()}", style = MaterialTheme.typography.labelSmall, color = primaryColor, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.lp_level_2), style = MaterialTheme.typography.labelSmall, color = subTextColor)
                }
            }
        }

        if (isSconfigSupported) {
            item {
                AdvanceSectionCard("Thermal Sconfig Profile", "Adjust thermal profile for performance tuning.", isGlassActive, hazeState, cardColor, textColor, subTextColor) {
                    val sconfigData = listOf(Pair("Not set", -1), Pair("Normal", 0), Pair("Extreme", 2), Pair("Incalls", 8), Pair("Dynamic", 10), Pair("PUBG", 13), Pair("Thermal 20", 20), Pair("Game", 40), Pair("Camera", 42), Pair("Game 2", 50), Pair("YouTube", 51))
                    var localSconfig by remember(sconfigIndex) { mutableFloatStateOf(sconfigIndex) }
                    Text("Current Profile: ${sconfigData[localSconfig.toInt()].first}", style = MaterialTheme.typography.labelMedium, color = primaryColor, fontWeight = FontWeight.Bold)
                    ZkmSlider(
                        value = localSconfig,
                        onValueChange = { localSconfig = it },
                        onValueChangeFinished = { viewModel.setSconfigState(localSconfig, sconfigData[localSconfig.toInt()].second) },
                        valueRange = 0f..(sconfigData.size - 1).toFloat(), steps = sconfigData.size - 2,
                        colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor, inactiveTrackColor = subTextColor.copy(alpha = 0.3f))
                    )
                }
            }
        }

        item {
            AdvanceSectionCard("System Thermal Prop & Process", "Check or disable system built-in thermal properties and background processes.", isGlassActive, hazeState, cardColor, textColor, subTextColor) {
                AdvanceToggleItem("Disable Thermal Prop (ctl.stop)", propDisabled, { checked ->
                    if (checked) pendingDangerAction = DangerAction.DisableThermalProp
                    else viewModel.setPropDisabledState(false)
                }, primaryColor, textColor)
                AdvanceToggleItem("Disable Thermal Process (kill -STOP)", processDisabled, { checked ->
                    if (checked) pendingDangerAction = DangerAction.KillThermalProcess
                    else viewModel.setProcessDisabledState(false)
                }, primaryColor, textColor)

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.checkProps() }, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, primaryColor)
        ) { Text(stringResource(R.string.lp_check_props), color = primaryColor) }
                    OutlinedButton(onClick = { viewModel.checkProcesses() }, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, primaryColor)
        ) { Text(stringResource(R.string.lp_check_processes), color = primaryColor) }
                }

                AnimatedVisibility(visible = shellResult.isNotEmpty()) {
                    Surface(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), shape = RoundedCornerShape(8.dp), color = Color.Black.copy(0.4f)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.lp_terminal_output), style = MaterialTheme.typography.labelSmall, color = primaryColor)
                                Text(stringResource(R.string.lp_clear), style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.clickable { viewModel.clearShellResult() })
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(shellResult, style = MaterialTheme.typography.bodySmall, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdvanceSectionCard(title: String, desc: String, isGlassActive: Boolean, hazeState: HazeState, cardColor: Color, textColor: Color, subTextColor: Color, content: @Composable () -> Unit) {
    GlassThermalSettingCard(isGlassActive, hazeState, cardColor) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = textColor)
            Spacer(modifier = Modifier.height(4.dp))
            Text(desc, style = MaterialTheme.typography.bodySmall, color = subTextColor)
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun AdvanceToggleItem(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, primaryColor: Color, textColor: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onCheckedChange(!checked) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = textColor, modifier = Modifier.weight(1f))
        ZkmSwitch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.scale(0.8f), colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = primaryColor))
    }
}

@Composable
fun GlassStatCardThermal(label: String, value: String, icon: ImageVector, color: Color, onColor: Color, modifier: Modifier = Modifier, isGlassActive: Boolean, hazeState: HazeState) {
    val shape = RoundedCornerShape(24.dp)
    val cardModifier = if (isGlassActive) Modifier.zkmCardHazeEffect(shape = shape, state = hazeState, style = ZkmHazeStyleCache.of(backgroundColor = color, blurRadius = 20.dp, noiseFactor = 0.1f, tints = emptyList())).zkmCardBorder(1.dp, Color.White.copy(alpha = 0.2f), shape) else Modifier.clip(shape)
    Surface(modifier = modifier.then(cardModifier), shape = shape, color = if (isGlassActive) zkmGlassTint(color, zkmIsDarkTheme(), 0.62f) else color) {
        Column(modifier = Modifier.padding(14.dp).height(100.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(imageVector = icon, contentDescription = null, tint = onColor.copy(alpha = 0.8f), modifier = Modifier.size(24.dp))
            Column {
                Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = onColor)
                Text(label, style = MaterialTheme.typography.labelSmall, color = onColor.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
fun GlassThermalSettingCard(isGlassActive: Boolean, hazeState: HazeState, cardColor: Color, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val modifier = if (isGlassActive) Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).zkmCardHazeEffect(shape = shape, state = hazeState, style = ZkmHazeStyleCache.of(backgroundColor = cardColor.copy(alpha = 0.5f), blurRadius = 20.dp, noiseFactor = 0.1f, tints = emptyList())).zkmCardBorder(1.dp, Color.White.copy(alpha = 0.2f), shape) else Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    Card(modifier = modifier, shape = shape, colors = CardDefaults.cardColors(containerColor = if (isGlassActive) zkmCardFill(zkmGlassTint(MaterialTheme.colorScheme.surfaceContainerLow, zkmIsDarkTheme(), 0.40f)) else cardColor)) { content() }
}

@Composable
private fun ThermalTabItemGlass(
    selected: Boolean, 
    onClick: () -> Unit, 
    text: String, 
    textColor: Color, 
    subTextColor: Color, 
    primaryColor: Color, 
    isGlassActive: Boolean
) {
    val bgColor by animateColorAsState(if (selected) { if (isGlassActive) primaryColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primaryContainer } else Color.Transparent, label = "")
    val txtColor by animateColorAsState(if (selected) Color.White else if (isGlassActive) textColor else subTextColor, label = "")
    
    Tab(
        selected = selected,
        onClick = onClick,
        modifier = Modifier
            .clip(RoundedCornerShape(28.dp))
            .background(bgColor)
            .padding(vertical = 4.dp),
        text = { 
            Text(
                text = text,
                color = txtColor,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
            ) 
        }
    )
}

@Composable
fun GlassFilterChips(filters: List<String>, selectedFilter: String, onFilterSelected: (String) -> Unit, isGlassActive: Boolean, primaryColor: Color, textColor: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        filters.forEach { filter ->
            val selected = filter == selectedFilter
            FilterChip(selected = selected, onClick = { onFilterSelected(filter) }, label = { Text(filter, color = if (selected) Color.White else textColor) }, leadingIcon = if (selected) { { Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), Color.White) } } else null, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = primaryColor, containerColor = if (isGlassActive) Color.White.copy(0.1f) else MaterialTheme.colorScheme.surfaceVariant)
        )
        }
    }
}

@Composable
fun ThermalZoneCardGlass(zone: ThermalZone, tripPoints: List<TripPoint>?, onToggle: () -> Unit, onShowTrips: () -> Unit, onHideTrips: () -> Unit, isGlassActive: Boolean, hazeState: HazeState, cardColor: Color, textColor: Color, subTextColor: Color, primaryColor: Color) {
    var expanded by remember { mutableStateOf(false) }
    val tempColor = when { !zone.isEnabled -> subTextColor; zone.temperature > 70 -> if (cardColor == Color.Transparent) Color(0xFFFF6B6B) else MaterialTheme.colorScheme.error; zone.temperature > 50 -> if (cardColor == Color.Transparent) Color(0xFFFFB74D) else MaterialTheme.colorScheme.tertiary; else -> primaryColor }
    val shape = RoundedCornerShape(16.dp)
    val isDarkNow = zkmIsDarkTheme()
    // [FIX] Di mode glass baris zona dulu "telanjang" (tanpa card sama sekali).
    val rootModifier = if (isGlassActive) {
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .zkmGlassCard(true, hazeState, RoundedCornerShape(24.dp), zkmGlassTint(MaterialTheme.colorScheme.surfaceContainerLow, isDarkNow, 0.40f), 20.dp)
    } else Modifier
    Column(modifier = rootModifier) {
        Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = zkmIconShape(RoundedCornerShape(12.dp)), color = tempColor.copy(alpha = 0.15f), modifier = Modifier.size(48.dp)) { Box(contentAlignment = Alignment.Center) { ZkmIconGlyph(icon = if (zone.isEnabled) Icons.Rounded.Thermostat else Icons.Rounded.AcUnit, tint = tempColor, iconSize = 24.dp) } }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = zone.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = if(zone.isEnabled) textColor else subTextColor), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = "${zone.type} • Zone ${zone.id}", style = MaterialTheme.typography.bodySmall, color = subTextColor)
            }
            Text(text = "${zone.temperature}°C", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = tempColor))
            Spacer(modifier = Modifier.width(8.dp))
            Icon(imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = subTextColor)
        }
        AnimatedVisibility(visible = expanded) {
            val cardModifier = if (isGlassActive) Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp).clip(shape).border(1.dp, Color.White.copy(alpha = 0.1f), shape) else Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
            Card(modifier = cardModifier, shape = shape, colors = CardDefaults.cardColors(containerColor = if (isGlassActive) (if (isDarkNow) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)) else MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onToggle, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (zone.isEnabled) (if (isGlassActive) Color(0xFFCF6679) else MaterialTheme.colorScheme.errorContainer) else primaryColor.copy(alpha = 0.8f), contentColor = Color.White)
        ) {
                            Icon(imageVector = if (zone.isEnabled) Icons.Rounded.Block else Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (zone.isEnabled) stringResource(R.string.thermal_disable) else stringResource(R.string.thermal_enable))
                        }
                        OutlinedButton(onClick = { if (tripPoints != null) onHideTrips() else onShowTrips() }, modifier = Modifier.weight(1f), border = if (isGlassActive) BorderStroke(1.dp, Color.White.copy(0.5f)) else null, colors = ButtonDefaults.outlinedButtonColors(contentColor = if (isGlassActive) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.primary)
        ) {
                            Icon(imageVector = Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (tripPoints != null) stringResource(R.string.thermal_hide_trips) else stringResource(R.string.thermal_trip_points))
                        }
                    }
                    if (tripPoints != null && tripPoints.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(shape = RoundedCornerShape(16.dp), color = if (isGlassActive) Color.White.copy(0.05f) else MaterialTheme.colorScheme.surfaceContainerLow) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(text = stringResource(R.string.thermal_trip_config), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = primaryColor)
                                Spacer(modifier = Modifier.height(8.dp))
                                tripPoints.forEach { trip ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = trip.type.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, color = textColor)
                                        Text(text = "${trip.temperature}°C", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = textColor)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CoolingDeviceCardGlass(device: ThermalCoolingDevice, onStateChange: (Int) -> Unit, isGlassActive: Boolean, hazeState: HazeState, cardColor: Color, textColor: Color, subTextColor: Color, primaryColor: Color) {
    var showSlider by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(device.curState.toFloat()) }
    val shape = RoundedCornerShape(16.dp)
    val isDarkNow = zkmIsDarkTheme()
    val rootModifier = if (isGlassActive) {
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .zkmGlassCard(true, hazeState, RoundedCornerShape(24.dp), zkmGlassTint(MaterialTheme.colorScheme.surfaceContainerLow, isDarkNow, 0.40f), 20.dp)
    } else Modifier

    Column(modifier = rootModifier) {
        Row(modifier = Modifier.fillMaxWidth().clickable { showSlider = !showSlider }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = zkmIconShape(RoundedCornerShape(12.dp)), color = if (isGlassActive) primaryColor.copy(0.2f) else MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { ZkmIconGlyph(icon = Icons.Rounded.ModeFanOff, tint = if (isGlassActive) primaryColor else MaterialTheme.colorScheme.onSecondaryContainer, iconSize = 24.dp) }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = device.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = textColor), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = "${device.type} • ID: ${device.id}", style = MaterialTheme.typography.bodySmall, color = subTextColor)
            }
            Surface(shape = RoundedCornerShape(8.dp), color = if (isGlassActive) primaryColor.copy(0.3f) else MaterialTheme.colorScheme.primaryContainer) {
                Text(text = "${device.curState}/${device.maxState}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = if (isGlassActive) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        AnimatedVisibility(visible = showSlider) {
            val sliderCardModifier = if (isGlassActive) Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp).clip(shape).background(if (isDarkNow) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f), shape).border(1.dp, Color.White.copy(alpha = 0.1f), shape).padding(16.dp) else Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp).padding(16.dp)
            Column(modifier = sliderCardModifier) {
                ZkmSlider(value = sliderValue, onValueChange = { sliderValue = it }, onValueChangeFinished = { onStateChange(sliderValue.toInt()) }, valueRange = 0f..device.maxState.toFloat(), steps = if (device.maxState > 1) device.maxState - 1 else 0, colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor, inactiveTrackColor = if (isGlassActive) Color.White.copy(0.3f) else MaterialTheme.colorScheme.surfaceContainerHighest))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.lp_v_0), style = MaterialTheme.typography.labelSmall, color = subTextColor)
                    Text("${stringResource(R.string.thermal_current_state)}: ${sliderValue.toInt()}", style = MaterialTheme.typography.labelMedium.copy(color = primaryColor, fontWeight = FontWeight.Bold))
                    Text("${device.maxState}", style = MaterialTheme.typography.labelSmall, color = subTextColor)
                }
            }
        }
    }
}

@Composable
fun ThermalPolicyDialog(currentPolicy: String, onDismiss: () -> Unit, onSelect: (String) -> Unit, isGlassActive: Boolean, textColor: Color, subTextColor: Color, primaryColor: Color) {
    val policies = listOf("default", "performance", "balanced", "powersave", "user_space")
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    BlurredAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.thermal_policy_dialog_title), fontWeight = FontWeight.Bold, color = textColor) },
        text = {
            Column {
                policies.forEach { policy ->
                    ListItem(
                        headlineContent = { Text(policy.replaceFirstChar { it.uppercase() }, fontWeight = if (policy == currentPolicy) FontWeight.Bold else FontWeight.Normal, color = textColor) },
                        leadingContent = { if (policy == currentPolicy) Icon(Icons.Rounded.CheckCircle, null, tint = primaryColor) else Icon(Icons.Rounded.RadioButtonUnchecked, null, tint = subTextColor) },
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onSelect(policy) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss
        ) { Text(stringResource(R.string.btn_cancel), color = primaryColor) } }
    )
}
