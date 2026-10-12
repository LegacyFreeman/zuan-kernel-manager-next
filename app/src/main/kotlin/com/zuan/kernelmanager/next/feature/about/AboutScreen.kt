/*
 * Copyright (c) 2025 ZKM Next
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)

package com.zuan.kernelmanager.next.feature.about

import com.zuan.kernelmanager.next.ui.components.zkmLiteAlpha
import com.zuan.kernelmanager.next.ui.adaptive.zkmLargeAppBarExpandedHeight
import com.zuan.kernelmanager.next.ui.components.zkmAppBarHazeStyle
import com.zuan.kernelmanager.next.ui.components.zkmCardHazeEffect
import com.zuan.kernelmanager.next.ui.components.zkmCardBorder
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundContentColor
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundSubContentColor
import com.zuan.kernelmanager.next.ui.components.zkmCustomBackgroundCardColor
import com.zuan.kernelmanager.next.ui.components.zkmGlassFallbackTint
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.ui.components.VideoWallpaperPlayer
import com.zuan.kernelmanager.next.ui.components.WeatherEffectOverlay
import com.zuan.kernelmanager.next.ui.navigation.NavigationRoute
import com.zuan.kernelmanager.next.feature.help.ZkmHelpButton
import com.zuan.kernelmanager.next.ui.ZKM_SUPPORT_URL
import com.zuan.kernelmanager.next.feature.settings.BgType
import com.zuan.kernelmanager.next.feature.settings.SettingsViewModel
import com.zuan.kernelmanager.next.feature.settings.WeatherEffect
import com.zuan.kernelmanager.next.ui.theme.ThemeMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import com.zuan.kernelmanager.next.ui.components.zkmHazeEffect
import com.zuan.kernelmanager.next.ui.components.zkmHazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import com.zuan.kernelmanager.next.ui.components.ZkmHazeStyleCache
import com.zuan.kernelmanager.next.ui.theme.zkmIconShape
import com.zuan.kernelmanager.next.ui.theme.ZkmIconGlyph
import com.zuan.kernelmanager.next.ui.theme.ZkmPaddedIcon

val ExpressiveCornerRadius = 32.dp
val IconContainerRadius = 16.dp
val CardPadding = 16.dp

@Composable
fun generateThemedBackgroundColor(primaryColor: Color, isDark: Boolean): Pair<Color, Color> = com.zuan.kernelmanager.next.ui.components.ZkmScreenPalette.colors(isDark, primaryColor)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun AboutScreen(
    navController: NavController,
    hazeState: HazeState,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val density = LocalDensity.current
    
    val appVersion = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
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

    val finalPrimary = if (effectivePrimary == Color.Unspecified) {
        MaterialTheme.colorScheme.primary
    } else {
        effectivePrimary
    }

    val (tintedBackground, themeCardColor) = generateThemedBackgroundColor(finalPrimary, useDarkTheme)

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = rememberTopAppBarState(),
        snapAnimationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        flingAnimationSpec = rememberSplineBasedDecay()
    )

    val isCollapsed by remember {
        derivedStateOf { scrollBehavior.state.collapsedFraction > 0.7f }
    }

    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeightDp = with(density) { headerHeightPx.toDp() }

    val accentColor = finalPrimary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    val isGlassActive = isHazeEnabled && isCustomBg
    val mainBackgroundColor = if (isCustomBg) Color.Transparent else tintedBackground
    
    val targetCardColor = when {
        isGlassActive -> zkmGlassFallbackTint(zkmIsDarkTheme())
        isCustomBg -> MaterialTheme.colorScheme.surface.zkmLiteAlpha(cardDarkness)
        else -> themeCardColor
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(mainBackgroundColor)
        ) {
            // BACKGROUND LAYER (zIndex 0)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zkmHazeSource(state = hazeState, zIndex = 0f)
            ) {
                if (isCustomBg && bgUriString != null) {
                    val blurModifier = if (isBgBlur && blurStrength > 0f) {
                        Modifier.blur(blurStrength.dp)
                    } else if (isBgBlur) {
                        Modifier.blur(20.dp)
                    } else {
                        Modifier
                    }
                    
                    val saturationMatrix = ColorMatrix().apply {
                        setToSaturation(bgSaturation)
                    }
                    val colorFilter = ColorFilter.colorMatrix(saturationMatrix)
                    
                    val uri = Uri.parse(bgUriString)

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
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = bgContrast))
                        )
                    }
                }

                WeatherEffectOverlay(
                    effect = weatherEffect,
                    intensity = weatherIntensity,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // CONTENT LAYER (zIndex 1)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .zkmHazeSource(state = hazeState, zIndex = 1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(headerHeightDp + 8.dp))

                // DEVELOPER SECTION
                ThemedSectionTitle(stringResource(R.string.about_section_developer), accentColor)
                Spacer(modifier = Modifier.height(8.dp))

                AboutDeveloperCard(
                    avatarUrl = "https://avatars.githubusercontent.com/u/207377902?v=4",
                    name = "Zuanvfx01",
                    subtitle = stringResource(R.string.about_maintainer_title),
                    onClick = { openUrl(context, "https://github.com/zuanvfx01") },
                    cardColor = targetCardColor,
                    isGlassActive = isGlassActive,
                    hazeState = hazeState,
                    onSurface = onSurfaceColor,
                    onSurfaceVariant = onSurfaceVariantColor
                )

                Spacer(modifier = Modifier.height(8.dp))

                AboutDeveloperCard(
                    avatarUrl = "https://avatars.githubusercontent.com/zuanvfx02",
                    name = "Zuanvfx02",
                    subtitle = stringResource(R.string.about_maintainer_next_title),
                    onClick = { openUrl(context, "https://github.com/zuanvfx02") },
                    cardColor = targetCardColor,
                    isGlassActive = isGlassActive,
                    hazeState = hazeState,
                    onSurface = onSurfaceColor,
                    onSurfaceVariant = onSurfaceVariantColor
                )

                Spacer(modifier = Modifier.height(8.dp))

                AboutLinkCard(
                    icon = Icons.Outlined.Language,
                    title = stringResource(R.string.about_link_website),
                    subtitle = "zuanvfx01.github.io/ZKM",
                    onClick = { openUrl(context, "https://zuanvfx01.github.io/ZKM/") },
                    external = false,
                    cardColor = targetCardColor,
                    isGlassActive = isGlassActive,
                    hazeState = hazeState,
                    accent = accentColor,
                    onSurface = onSurfaceColor,
                    onSurfaceVariant = onSurfaceVariantColor
                )

                Spacer(modifier = Modifier.height(8.dp))

                AboutLinkCard(
                    icon = Icons.Outlined.Language,
                    title = stringResource(R.string.about_link_website),
                    subtitle = "zuanvfx02.github.io/zuan-kernel-manager-next",
                    onClick = { openUrl(context, "https://zuanvfx02.github.io/zuan-kernel-manager-next/#download") },
                    external = false,
                    cardColor = targetCardColor,
                    isGlassActive = isGlassActive,
                    hazeState = hazeState,
                    accent = accentColor,
                    onSurface = onSurfaceColor,
                    onSurfaceVariant = onSurfaceVariantColor
                )

                Spacer(modifier = Modifier.height(24.dp))

                // SUPPORT DEVELOPER SECTION - Buy Me a Coffee
                ThemedSectionTitle(stringResource(R.string.about_section_support_dev), accentColor)
                Spacer(modifier = Modifier.height(8.dp))

                AboutLinkCard(
                    icon = Icons.Outlined.Coffee,
                    title = stringResource(R.string.about_support_coffee_title),
                    subtitle = stringResource(R.string.about_support_coffee_desc),
                    onClick = { openUrl(context, ZKM_SUPPORT_URL) },
                    external = true,
                    cardColor = targetCardColor,
                    isGlassActive = isGlassActive,
                    hazeState = hazeState,
                    accent = accentColor,
                    onSurface = onSurfaceColor,
                    onSurfaceVariant = onSurfaceVariantColor
                )

                Spacer(modifier = Modifier.height(24.dp))

                // APPLICATION SECTION - Help, Changelog & Open Source
                ThemedSectionTitle("Application", accentColor)
                Spacer(modifier = Modifier.height(8.dp))

                ThemedExpressiveCard(
                    containerColor = targetCardColor,
                    isGlassActive = isGlassActive,
                    hazeState = hazeState,
                    onClick = { navController.navigate(NavigationRoute.HelpCenter.route) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(CardPadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ThemedIconContainer(
                            icon = Icons.Outlined.MenuBook,
                            primaryColor = accentColor
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.help_about_entry_title),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 17.sp
                                ),
                                color = onSurfaceColor
                            )
                            Text(
                                text = stringResource(R.string.help_about_entry_sub),
                                style = MaterialTheme.typography.bodyMedium,
                                color = onSurfaceVariantColor
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = onSurfaceVariantColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                ThemedExpressiveCard(
                    containerColor = targetCardColor,
                    isGlassActive = isGlassActive,
                    hazeState = hazeState,
                    onClick = { navController.navigate(NavigationRoute.Changelogs.route) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(CardPadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ThemedIconContainer(
                            icon = Icons.Outlined.History,
                            primaryColor = accentColor
                        )
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Changelog",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 17.sp
                                ),
                                color = onSurfaceColor
                            )
                            Text(
                                text = "Version history and updates",
                                style = MaterialTheme.typography.bodyMedium,
                                color = onSurfaceVariantColor
                            )
                        }
                        
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = onSurfaceVariantColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                ThemedExpressiveCard(
                    containerColor = targetCardColor,
                    isGlassActive = isGlassActive,
                    hazeState = hazeState,
                    onClick = { navController.navigate(NavigationRoute.OpenSource.route) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(CardPadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ThemedIconContainer(
                            icon = Icons.Outlined.Code,
                            primaryColor = accentColor
                        )
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Open Source",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 17.sp
                                ),
                                color = onSurfaceColor
                            )
                            Text(
                                text = "Contributors and licenses",
                                style = MaterialTheme.typography.bodyMedium,
                                color = onSurfaceVariantColor
                            )
                        }
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy((-8).dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            listOf(
                                "https://avatars.githubusercontent.com/Rve27",
                                "https://avatars.githubusercontent.com/libxzr",
                                "https://avatars.githubusercontent.com/Kyant0"
                            ).forEach { avatarUrl ->
                                Surface(
                                    shape = CircleShape,
                                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    AsyncImage(
                                        model = avatarUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                    )
                                }
                            }
                            
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+5",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = onSurfaceVariantColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // APP INFO SECTION
                ThemedSectionTitle(stringResource(R.string.about_section_app_info), accentColor)
                Spacer(modifier = Modifier.height(8.dp))
                
                ThemedExpressiveCard(
                    containerColor = targetCardColor,
                    isGlassActive = isGlassActive,
                    hazeState = hazeState,
                    onClick = { navController.navigate(NavigationRoute.General.route) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(CardPadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ThemedIconContainer(
                            icon = Icons.Outlined.GridView,
                            primaryColor = accentColor
                        )
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.sect_general),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 17.sp
                                ),
                                color = onSurfaceColor
                            )
                            Text(
                                text = "App settings and configuration",
                                style = MaterialTheme.typography.bodyMedium,
                                color = onSurfaceVariantColor
                            )
                        }
                        
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = onSurfaceVariantColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                ThemedExpressiveCard(
                    containerColor = targetCardColor,
                    isGlassActive = isGlassActive,
                    hazeState = hazeState,
                    onClick = { navController.navigate(NavigationRoute.Settings.route) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(CardPadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ThemedIconContainer(
                            icon = Icons.Outlined.Palette,
                            primaryColor = accentColor
                        )
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.sect_appearance),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 17.sp
                                ),
                                color = onSurfaceColor
                            )
                            Text(
                                text = "Theme, colors and personalization",
                                style = MaterialTheme.typography.bodyMedium,
                                color = onSurfaceVariantColor
                            )
                        }
                        
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = onSurfaceVariantColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // COMMUNITY SECTION
                ThemedSectionTitle(stringResource(R.string.about_section_community), accentColor)
                Spacer(modifier = Modifier.height(8.dp))

                AboutLinkCard(
                    icon = Icons.Outlined.Campaign,
                    title = stringResource(R.string.about_link_channel_official),
                    subtitle = "@zuankernelmanagernext",
                    onClick = { openUrl(context, "https://t.me/zuankernelmanagernext") },
                    external = true,
                    cardColor = targetCardColor,
                    isGlassActive = isGlassActive,
                    hazeState = hazeState,
                    accent = accentColor,
                    onSurface = onSurfaceColor,
                    onSurfaceVariant = onSurfaceVariantColor
                )

                Spacer(modifier = Modifier.height(8.dp))
                
                ThemedExpressiveCard(
                    containerColor = targetCardColor,
                    isGlassActive = isGlassActive,
                    hazeState = hazeState,
                    onClick = { openUrl(context, "https://t.me/ZuanvfxProject3") }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(CardPadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ThemedIconContainer(
                            icon = Icons.Outlined.Campaign,
                            primaryColor = accentColor
                        )
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.about_link_channel),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 17.sp
                                ),
                                color = onSurfaceColor
                            )
                            Text(
                                text = "@ZuanvfxProject3",
                                style = MaterialTheme.typography.bodyMedium,
                                color = onSurfaceVariantColor
                            )
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = onSurfaceVariantColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Outlined.OpenInNew,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                ThemedExpressiveCard(
                    containerColor = targetCardColor,
                    isGlassActive = isGlassActive,
                    hazeState = hazeState,
                    onClick = { openUrl(context, "https://t.me/zuanvfx01enterprise/2") }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(CardPadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ThemedIconContainer(
                            icon = Icons.Outlined.Group,
                            primaryColor = accentColor
                        )
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.about_link_discussion),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 17.sp
                                ),
                                color = onSurfaceColor
                            )
                            Text(
                                text = "@zuanvfx01enterprise",
                                style = MaterialTheme.typography.bodyMedium,
                                color = onSurfaceVariantColor
                            )
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = onSurfaceVariantColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Outlined.OpenInNew,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                ThemedExpressiveCard(
                    containerColor = targetCardColor,
                    isGlassActive = isGlassActive,
                    hazeState = hazeState,
                    onClick = { openUrl(context, "https://t.me/Zuanvfx01") }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(CardPadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ThemedIconContainer(
                            icon = Icons.Outlined.Translate,
                            primaryColor = accentColor
                        )
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.about_link_translate),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 17.sp
                                ),
                                color = onSurfaceColor
                            )
                            Text(
                                text = "@Zuanvfx01",
                                style = MaterialTheme.typography.bodyMedium,
                                color = onSurfaceVariantColor
                            )
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = onSurfaceVariantColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Outlined.OpenInNew,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(120.dp))
            }

            // FLOATING HEADER DENGAN PROGRESSIVE BLUR (zIndex 2)
            val headerHazeStyle = zkmAppBarHazeStyle(tintedBackground, isCustomBg)

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .onGloballyPositioned { headerHeightPx = it.size.height }
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
                            AppBarContent(
                                isCollapsed = isCollapsed,
                                appName = stringResource(R.string.app_name),
                                version = appVersion,
                                textColor = onSurfaceColor,
                                subTextColor = onSurfaceVariantColor,
                                accentColor = accentColor
                            )
                        },
                        actions = {
                            ZkmHelpButton(NavigationRoute.About.route, tint = onSurfaceColor)
                        },
                        colors = TopAppBarDefaults.largeTopAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                            actionIconContentColor = onSurfaceColor,
                            navigationIconContentColor = onSurfaceColor
                        ),
                        scrollBehavior = scrollBehavior,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Spacer Fading Buffer
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun AppBarContent(
    isCollapsed: Boolean,
    appName: String,
    version: String,
    textColor: Color,
    subTextColor: Color,
    accentColor: Color
) {
    AnimatedContent(
        targetState = isCollapsed,
        transitionSpec = {
            (fadeIn(animationSpec = tween(300, easing = EaseOutQuart)) +
                    slideInVertically { it / 2 })
                .togetherWith(
                    fadeOut(animationSpec = tween(200)) +
                            slideOutVertically { -it / 2 }
                )
        },
        label = "AppBarTransition"
    ) { collapsed ->
        if (collapsed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = accentColor.copy(alpha = 0.2f),
                    shape = zkmIconShape(RoundedCornerShape(10.dp)),
                    modifier = Modifier.size(36.dp)
                ) {
                    ZkmPaddedIcon(imageVector = Icons.Outlined.Info, tint = accentColor, padding = 8.dp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = textColor
                )
            }
        } else {
            Column {
                Text(
                    text = appName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = subTextColor
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Version $version",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = textColor
                )
            }
        }
    }
}

@Composable
fun ThemedSectionTitle(text: String, accentColor: Color) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp),
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            fontSize = 13.sp
        ),
        color = accentColor.copy(alpha = 0.9f)
    )
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun ThemedExpressiveCard(
    containerColor: Color,
    isGlassActive: Boolean = false,
    hazeState: HazeState? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(ExpressiveCornerRadius)
    
    val modifier = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    } else {
        Modifier.fillMaxWidth()
    }
    
    val glassModifier = if (isGlassActive && hazeState != null) {
        Modifier
            .zkmCardHazeEffect(shape = shape,
                state = hazeState,
                style = ZkmHazeStyleCache.of(
                    backgroundColor = MaterialTheme.colorScheme.surface.zkmLiteAlpha(0.2f),
                    blurRadius = 20.dp,
                    noiseFactor = 0.05f,
                    tints = emptyList()
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
                shape = shape
            )
    } else {
        Modifier
    }

    Surface(
        modifier = modifier.then(glassModifier),
        shape = shape,
        color = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else containerColor,
        tonalElevation = if (onClick != null && !isGlassActive) 1.dp else 0.dp
    ) {
        content()
    }
}

@Composable
fun ThemedIconContainer(
    icon: ImageVector,
    primaryColor: Color
) {
    val softBackground = primaryColor.copy(alpha = 0.12f)
    
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(zkmIconShape(RoundedCornerShape(IconContainerRadius)))
            .background(softBackground),
        contentAlignment = Alignment.Center
    ) {
        ZkmIconGlyph(icon = icon, tint = primaryColor, iconSize = 24.dp)
    }
}

@Composable
private fun AboutDeveloperCard(
    avatarUrl: String,
    name: String,
    subtitle: String,
    onClick: () -> Unit,
    cardColor: Color,
    isGlassActive: Boolean,
    hazeState: HazeState,
    onSurface: Color,
    onSurfaceVariant: Color
) {
    ThemedExpressiveCard(
        containerColor = cardColor,
        isGlassActive = isGlassActive,
        hazeState = hazeState,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    ),
                    color = onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun AboutLinkCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    external: Boolean,
    cardColor: Color,
    isGlassActive: Boolean,
    hazeState: HazeState,
    accent: Color,
    onSurface: Color,
    onSurfaceVariant: Color
) {
    ThemedExpressiveCard(
        containerColor = cardColor,
        isGlassActive = isGlassActive,
        hazeState = hazeState,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ThemedIconContainer(icon = icon, primaryColor = accent)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    ),
                    color = onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                if (external) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Outlined.OpenInNew,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}
