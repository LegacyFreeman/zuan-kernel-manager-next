/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalHazeMaterialsApi::class
)

package com.zuan.kernelmanager.next.feature.soc

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zuan.kernelmanager.next.ui.adaptive.zkmLargeAppBarExpandedHeight
import com.zuan.kernelmanager.next.ui.components.zkmAppBarHazeStyle
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.ui.navigation.NavigationRoute
import com.zuan.kernelmanager.next.feature.help.ZkmHelpButton
import com.zuan.kernelmanager.next.feature.settings.SettingsViewModel
import com.zuan.kernelmanager.next.feature.tuning.*
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
import com.zuan.kernelmanager.next.ui.navigation.ZkmTabNavigator

@Composable
fun generateSoCBackgroundColors(isDark: Boolean, primaryColor: Color): Pair<Color, Color> = com.zuan.kernelmanager.next.ui.components.ZkmScreenPalette.colors(isDark, primaryColor)

enum class SoCTab(val icon: ImageVector) {
    CpuGpu(Icons.Default.Memory),
    Sched(Icons.Default.Schedule),
    Memory(Icons.Default.SdStorage),
    Network(Icons.Default.Wifi)
}

@Composable
fun SoCTab.getTitle(): String = when(this) {
    SoCTab.CpuGpu -> stringResource(R.string.soc_tab_processor)
    SoCTab.Sched -> stringResource(R.string.soc_tab_scheduler)
    SoCTab.Memory -> stringResource(R.string.soc_tab_memory)
    SoCTab.Network -> stringResource(R.string.soc_tab_network)
}

@Composable
fun SoCScreen(
    navController: NavController,
    hazeState: HazeState,
    settingsViewModel: SettingsViewModel = viewModel(),
    onParentSignal: (Int) -> Unit = {} 
) {
    val schedulerVM: SchedulerViewModel = viewModel()
    val memoryVM: MemoryViewModel = viewModel()
    val networkVM: NetworkViewModel = viewModel()
    val density = LocalDensity.current

    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val isCustomBg by settingsViewModel.isCustomBackground.collectAsStateWithLifecycle()
    val cardDarkness by settingsViewModel.cardDarkness.collectAsStateWithLifecycle()
    val isDynamic by settingsViewModel.isDynamicColor.collectAsStateWithLifecycle()
    val themeColor by settingsViewModel.currentThemeColor.collectAsStateWithLifecycle()
    val isHazeEnabled by settingsViewModel.isHazeEnabled.collectAsStateWithLifecycle()
    
    val isCustomColor by settingsViewModel.isCustomColor.collectAsStateWithLifecycle()
    val customPrimary by settingsViewModel.customPrimaryColor.collectAsStateWithLifecycle()

    val weatherEffect by settingsViewModel.weatherEffect.collectAsStateWithLifecycle()
    val weatherIntensity by settingsViewModel.weatherIntensity.collectAsStateWithLifecycle()

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

    val (themeBgColor, themeCardColor) = generateSoCBackgroundColors(useDarkTheme, finalPrimary)
    val mainBackgroundColor = if (isCustomBg) Color.Transparent else themeBgColor
    
    val cardBg = when {
        isCustomBg -> themeCardColor.copy(alpha = cardDarkness)
        else -> themeCardColor
    }

    val isGlassActive = isCustomBg && isHazeEnabled
    val contentColor = if (useDarkTheme) Color(0xFFEEEEEE) else Color(0xFF111111)
    val subContentColor = if (useDarkTheme) Color(0xFFAAAAAA) else Color(0xFF666666)
    val activeColor = finalPrimary

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val tabs = SoCTab.values()
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    // Permintaan buka sub-tab dari luar (mis. kartu Memory / VM Parameters / Core CPU di Overall)
    val pendingTab by ZkmTabNavigator.pendingSocTab.collectAsStateWithLifecycle()
    LaunchedEffect(pendingTab) {
        val target = pendingTab ?: return@LaunchedEffect
        pagerState.scrollToPage(target.ordinal.coerceIn(0, tabs.size - 1))
        ZkmTabNavigator.consumeSocTab()
    }
    
    val isCollapsed by remember { derivedStateOf { scrollBehavior.state.collapsedFraction > 0.6f } }
    
    var appBarHeightPx by remember { mutableIntStateOf(0) }
    val appBarHeightDp = if (appBarHeightPx > 0) {
        with(density) { appBarHeightPx.toDp() }
    } else {
        180.dp 
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0) 
    ) { _ -> 
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(mainBackgroundColor)
        ) {

            // LAYER 1: PAGER KONTEN (Terdaftar sebagai hazeSource dengan zIndex 1f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zkmHazeSource(state = hazeState, zIndex = 1f)
            ) {
                if (!isCustomBg) {
                    WeatherEffectOverlay(
                        effect = weatherEffect,
                        intensity = weatherIntensity,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    pageSpacing = 0.dp,
                    beyondViewportPageCount = 1,
                    userScrollEnabled = true,
                    contentPadding = PaddingValues(0.dp)
                ) { page ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        when (tabs[page]) {
                            SoCTab.CpuGpu -> CpuGpuScreen(
                                navController = navController, 
                                hazeState = hazeState,
                                contentPadding = PaddingValues(top = appBarHeightDp + 8.dp, bottom = 100.dp)
                            )
                            
                            SoCTab.Sched -> SchedulerScreen(
                                viewModel = schedulerVM, 
                                hazeState = hazeState,
                                cardBg = cardBg, 
                                contentColor = contentColor, 
                                subContentColor = subContentColor, 
                                activeColor = activeColor,
                                contentPadding = PaddingValues(top = appBarHeightDp + 8.dp, bottom = 130.dp)
                            )
                            SoCTab.Memory -> MemoryScreen(
                                viewModel = memoryVM, 
                                hazeState = hazeState,
                                cardBg = cardBg, 
                                contentColor = contentColor, 
                                subContentColor = subContentColor, 
                                activeColor = activeColor,
                                contentPadding = PaddingValues(top = appBarHeightDp + 8.dp, bottom = 130.dp)
                            )
                            SoCTab.Network -> NetworkScreen(
                                viewModel = networkVM, 
                                hazeState = hazeState,
                                cardBg = cardBg, 
                                contentColor = contentColor, 
                                subContentColor = subContentColor, 
                                activeColor = activeColor,
                                contentPadding = PaddingValues(top = appBarHeightDp + 8.dp, bottom = 130.dp)
                            )
                        }
                    }
                }
            }

            // LAYER 2: FLOATING HEADER DENGAN PROGRESSIVE BLUR (zIndex 2f)
            val headerHazeStyle = zkmAppBarHazeStyle(themeBgColor, isCustomBg)

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        if (coordinates.size.height > 0 && appBarHeightPx != coordinates.size.height) {
                            appBarHeightPx = coordinates.size.height
                        }
                    }
                    .zkmHazeEffect(
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
                Column(modifier = Modifier.fillMaxWidth()) {
                    LargeTopAppBar(
                        expandedHeight = zkmLargeAppBarExpandedHeight(),
                        title = {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = if (isCollapsed) Alignment.CenterHorizontally else Alignment.Start
                            ) {
                                AnimatedContent(
                                    targetState = isCollapsed,
                                    transitionSpec = {
                                        (fadeIn(animationSpec = tween(300)) + slideInVertically { it / 2 })
                                            .togetherWith(fadeOut(animationSpec = tween(300)) + slideOutVertically { -it / 2 })
                                    },
                                    label = "AppBarTitleAnim"
                                ) { collapsed ->
                                    if (!collapsed) {
                                        Text(
                                            stringResource(R.string.soc_system_monitor),
                                            style = MaterialTheme.typography.titleLarge, 
                                            color = subContentColor, 
                                            fontWeight = FontWeight.Normal,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.height(0.dp))
                                    }
                                }
                                
                                ExpressiveTabRow(
                                    tabs = tabs,
                                    selectedIndex = pagerState.currentPage,
                                    onTabSelected = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                                    activeColor = activeColor,
                                    contentColor = contentColor,
                                    subContentColor = subContentColor,
                                    hazeState = hazeState,
                                    isGlassActive = isGlassActive,
                                    modifier = if (isCollapsed) Modifier.padding(top = 4.dp) else Modifier
                                )
                            }
                        },
                        navigationIcon = {
                            SocConfigMenu(activeColor = activeColor)
                        },
                        actions = {
                            ZkmHelpButton(NavigationRoute.SoC.route, tint = activeColor)
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent, 
                            actionIconContentColor = activeColor,
                            navigationIconContentColor = activeColor
                        ),
                        scrollBehavior = scrollBehavior,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun ExpressiveTabRow(
    tabs: Array<SoCTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    activeColor: Color,
    contentColor: Color,
    subContentColor: Color,
    hazeState: HazeState? = null,
    isGlassActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex) { listState.animateScrollToItem(selectedIndex, -100) }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        itemsIndexed(tabs) { index, tab ->
            val isSelected = selectedIndex == index
            
            var tabModifier = Modifier
                .height(36.dp)
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
                            tints = emptyList()
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                
                iconColor = activeColor
                textColor = activeColor
            } else {
                val containerColor by animateColorAsState(
                    targetValue = if (isSelected) activeColor.copy(alpha = 0.15f) else Color.Transparent, 
                    label = "tabBg"
                )
                
                tabModifier = tabModifier.background(containerColor)
                
                val animIconColor by animateColorAsState(if (isSelected) activeColor else subContentColor, label = "icon")
                val animTextColor by animateColorAsState(if (isSelected) activeColor else subContentColor, label = "text")
                iconColor = animIconColor
                textColor = animTextColor
            }

            Row(
                modifier = tabModifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(tab.icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    tab.getTitle(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, 
                    color = textColor
                )
            }
        }
    }
}
