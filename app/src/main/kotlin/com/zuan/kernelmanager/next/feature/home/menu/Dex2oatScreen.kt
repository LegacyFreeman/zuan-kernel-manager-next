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
import com.zuan.kernelmanager.next.ui.components.zkmReferenceGlass
import com.zuan.kernelmanager.next.ui.components.zkmReferenceGlassFill
import com.zuan.kernelmanager.next.ui.components.zkmReferenceGlassSurface
import com.zuan.kernelmanager.next.ui.components.zkmCardBorder
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundContentColor
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundSubContentColor
import com.zuan.kernelmanager.next.ui.components.zkmCustomBackgroundCardColor
import com.zuan.kernelmanager.next.ui.components.zkmGlassFallbackTint
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import androidx.compose.ui.zIndex
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
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
import androidx.compose.foundation.border
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import com.zuan.kernelmanager.next.ui.components.ZkmHazeStyleCache

/**
 * Generate SOFT TINTED background berdasarkan primary color tema
 */
@Composable
fun generateDex2oatBackgroundColors(isDark: Boolean, primaryColor: Color): Pair<Color, Color> = com.zuan.kernelmanager.next.ui.components.ZkmScreenPalette.colors(isDark, primaryColor)

@Composable
fun Dex2oatScreen(
    navController: NavController,
    viewModel: Dex2oatViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
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
    
    val effectivePrimary = if (isDynamic) {
        MaterialTheme.colorScheme.primary
    } else if (isCustomColor) {
        Color(customPrimary)
    } else {
        themeColorName.primary
    }

    val isDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM_DEFAULT -> isSystemInDarkTheme()
    }
    
    val (themeBackground, themeCardColor) = if (bgType == BgType.SYSTEM) {
        generateDex2oatBackgroundColors(isDarkTheme, effectivePrimary)
    } else {
        Pair(Color.Transparent, Color(0xFFFFFFFF))
    }
    
    val isCustomBg = bgType != BgType.SYSTEM
    val isGlassActive = isHazeEnabled && isCustomBg
    
    val mainBackgroundColor = if (bgType != BgType.SYSTEM) {
        Color.Transparent
    } else {
        themeBackground
    }
    
    val finalCardColor = when {
        isGlassActive -> zkmGlassFallbackTint(zkmIsDarkTheme())
        isCustomBg -> zkmCustomBackgroundCardColor(zkmIsDarkTheme(), cardDarkness)
        else -> themeCardColor
    }
    
    val textColor = when {
        isGlassActive || isCustomBg -> zkmOnCustomBackgroundContentColor(zkmIsDarkTheme())
        isDynamic -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface
    }
    
    val subTextColor = when {
        isGlassActive || isCustomBg -> zkmOnCustomBackgroundSubContentColor(zkmIsDarkTheme())
        isDynamic -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    val dividerColor = if (isCustomBg) Color.White.copy(0.2f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
    
    val apps by viewModel.filteredApps.collectAsStateWithLifecycle(initialValue = emptyList())
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val processingStatus by viewModel.processingStatus.collectAsStateWithLifecycle()
    val selectedMode by viewModel.selectedMode.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    val hazeState = rememberHazeState()
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeightDp = with(localDensity) { headerHeightPx.toDp() }

    LaunchedEffect(Unit) {
        viewModel.loadApps(context)
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

            Box(modifier = Modifier.fillMaxSize().zkmHazeSource(state = hazeState, zIndex = 0f)) {
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
            // Background digambar sebagai child di dalam hazeSource agar terekam
            // ke buffer haze (fix progressive blur appbar di mode non-custom).
            Box(modifier = Modifier.fillMaxSize().zkmHazeSource(state = hazeState, zIndex = 0f)) {
                Box(modifier = Modifier.fillMaxSize().background(mainBackgroundColor))
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
            contentWindowInsets = WindowInsets(0,0,0,0),
            floatingActionButton = {
                if (!isLoading && processingStatus == null) {
                    ExtendedFloatingActionButton(
                        onClick = { viewModel.compileAll() },
                        // FAB must stay fully opaque and theme-correct in every mode;
                        // the old glass branch used a semi-transparent primary that
                        // looked "naked" over custom wallpapers.
                        containerColor = if (isCustomBg) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (isCustomBg) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                        icon = { Icon(Icons.Rounded.ElectricBolt, null) },
                        text = { Text(stringResource(R.string.dex2oat_compile_all)) }
                    )
                }
            }
        ) { padding ->
            
            Box(modifier = Modifier.fillMaxSize()) {
                
                // LAYER 1: LIST CONTENT
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = effectivePrimary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().zkmHazeSource(state = hazeState, zIndex = 1f),
                        contentPadding = PaddingValues(
                            top = headerHeightDp,
                            bottom = 100.dp
                        )
                    ) {
                        // Memindahkan Card Info & Search Mode ke dalam list yang scrollable
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            GlassInfoCard(
                                isGlassActive = isGlassActive,
                                hazeState = hazeState,
                                cardColor = finalCardColor,
                                textColor = textColor,
                                subTextColor = subTextColor,
                                primaryColor = effectivePrimary,
                                onClick = { uriHandler.openUri("https://source.android.com/docs/core/runtime/jit-compiler") }
                            )
                        }

                        item {
                            GlassSearchModeCard(
                                viewModel = viewModel,
                                searchQuery = searchQuery,
                                selectedMode = selectedMode,
                                isGlassActive = isGlassActive,
                                hazeState = hazeState,
                                cardColor = finalCardColor,
                                textColor = textColor,
                                subTextColor = subTextColor,
                                primaryColor = effectivePrimary
                            )
                        }
                        
                        item {
                            if (processingStatus != null) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = effectivePrimary
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                        .background(
                                            if (isGlassActive) effectivePrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), 
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = processingStatus ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isGlassActive) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        items(apps, key = { it.packageName }) { app ->
                            AppCompileItemGlass(
                                app = app,
                                isEnabled = processingStatus == null,
                                onCompile = { viewModel.compileApp(app) },
                                onReset = { viewModel.resetApp(app) },
                                isGlassActive = isGlassActive,
                                hazeState = hazeState,
                                textColor = textColor,
                                subTextColor = subTextColor,
                                primaryColor = effectivePrimary
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 72.dp),
                                color = dividerColor.copy(alpha = 0.15f)
                            )
                        }
                    }
                }

                // LAYER 2: TOP HEADER (PROGRESSIVE BLUR)
                val headerHazeStyle = zkmAppBarHazeStyle(themeBackground, isCustomBg)

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            headerHeightPx = coordinates.size.height
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                    ) {
                        GlassDexHeader(
                            navController = navController,
                            viewModel = viewModel,
                            isCustomBg = isCustomBg,
                            isGlassActive = isGlassActive,
                            textColor = textColor,
                            subTextColor = subTextColor,
                            primaryColor = effectivePrimary
                        )
                        // Spacer fading blur
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun GlassInfoCard(
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    textColor: Color,
    subTextColor: Color,
    primaryColor: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    
    val modifier = if (isGlassActive) {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .zkmReferenceGlass(hazeState, shape)
            .clickable(onClick = onClick)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick)
    }

    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isGlassActive) zkmReferenceGlassFill() else primaryColor.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Info, null, tint = if (isGlassActive) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else primaryColor)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.dex2oat_art_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = textColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.dex2oat_art_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = subTextColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.dex2oat_view_source),
                    style = MaterialTheme.typography.labelSmall.copy(textDecoration = TextDecoration.Underline),
                    color = if (isGlassActive) Color.White.copy(alpha = 0.8f) else primaryColor
                )
            }
        }
    }
}

@Composable
fun GlassSearchModeCard(
    viewModel: Dex2oatViewModel,
    searchQuery: String,
    selectedMode: String,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    textColor: Color,
    subTextColor: Color,
    primaryColor: Color
) {
    val shape = RoundedCornerShape(24.dp)
    
    val modifier = if (isGlassActive) {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .zkmReferenceGlass(hazeState, shape)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    }

    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isGlassActive) zkmReferenceGlassFill() else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearch(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.dex2oat_search_hint), color = subTextColor) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = subTextColor) },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = if (isGlassActive) Color.White.copy(0.1f) else MaterialTheme.colorScheme.surface,
                    focusedContainerColor = if (isGlassActive) Color.White.copy(0.15f) else MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = primaryColor.copy(alpha = 0.5f),
                    cursorColor = primaryColor,
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor
                ),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.dex2oat_compilation_mode), 
                style = MaterialTheme.typography.labelMedium, 
                color = if (isGlassActive) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else primaryColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Dex2oatUtils.COMPILE_MODES.forEach { (modeKey, modeName) ->
                    val selected = selectedMode == modeKey
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.onModeSelect(modeKey) },
                        label = { Text(modeName.substringBefore(" "), color = if (selected) Color.White else textColor) },
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
fun GlassDexHeader(
    navController: NavController,
    viewModel: Dex2oatViewModel,
    isCustomBg: Boolean,
    isGlassActive: Boolean,
    textColor: Color,
    subTextColor: Color,
    primaryColor: Color
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    Icons.Rounded.ArrowBack, 
                    contentDescription = stringResource(R.string.dex2oat_back), 
                    tint = textColor
                )
            }
            
            ZkmHelpButton(NavigationRoute.Dex2oat.route, tint = textColor)

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Rounded.MoreVert, 
                        contentDescription = stringResource(R.string.dex2oat_options), 
                        tint = textColor
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    containerColor = if (isGlassActive) Color(0xFF1D1D1D) else MaterialTheme.colorScheme.surface
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.dex2oat_compile_system_only), color = if (isGlassActive) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else Color.Unspecified) },
                        onClick = { 
                            viewModel.compileSystemApps()
                            showMenu = false 
                        },
                        leadingIcon = { Icon(Icons.Rounded.SystemSecurityUpdate, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.dex2oat_compile_user_only), color = if (isGlassActive) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else Color.Unspecified) },
                        onClick = { 
                            viewModel.compileUserApps()
                            showMenu = false 
                        },
                        leadingIcon = { Icon(Icons.Rounded.Apps, null) }
                    )
                    HorizontalDivider(color = if (isGlassActive) Color.White.copy(0.2f) else MaterialTheme.colorScheme.outlineVariant)
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.dex2oat_reset_all), color = MaterialTheme.colorScheme.error) },
                        onClick = { 
                            viewModel.resetAllApps()
                            showMenu = false 
                        },
                        leadingIcon = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.dex2oat_title),
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
            color = textColor,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun AppCompileItemGlass(
    app: AppCompileItem,
    isEnabled: Boolean,
    onCompile: () -> Unit,
    onReset: () -> Unit,
    isGlassActive: Boolean,
    hazeState: HazeState,
    textColor: Color,
    subTextColor: Color,
    primaryColor: Color
) {
    val shape = RoundedCornerShape(12.dp)
    
    val modifier = if (isGlassActive) {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .zkmReferenceGlassSurface(hazeState, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (app.icon != null) {
            Image(
                painter = rememberAsyncImagePainter(model = app.icon), 
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier.size(48.dp).background(
                    if (isGlassActive) Color.White.copy(0.2f) else MaterialTheme.colorScheme.surfaceContainerHigh, 
                    CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Android, 
                    null, 
                    tint = if (isGlassActive) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
                    text = stringResource(R.string.dex2oat_system_app),
                    style = MaterialTheme.typography.labelSmall,
                    color = primaryColor
                )
            }
        }

        Row {
            IconButton(onClick = onReset, enabled = isEnabled) {
                Icon(
                    Icons.Rounded.RestartAlt, 
                    null, 
                    tint = if (isEnabled) {
                        if (isGlassActive) zkmOnCustomBackgroundSubContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.secondary 
                    } else Color.Gray
                )
            }
            IconButton(onClick = onCompile, enabled = isEnabled) {
                Icon(
                    Icons.Rounded.PlayArrow, 
                    null, 
                    tint = if (isEnabled) primaryColor else Color.Gray
                )
            }
        }
    }
}
