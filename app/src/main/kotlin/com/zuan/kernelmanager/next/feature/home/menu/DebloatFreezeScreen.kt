/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.zuan.kernelmanager.next.feature.home.menu

import com.zuan.kernelmanager.next.ui.components.zkmLiteAlpha
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
import com.zuan.kernelmanager.next.ui.components.BlurredAlertDialog
import com.zuan.kernelmanager.next.ui.components.ZkmDialogDefaults
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
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
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import com.zuan.kernelmanager.next.ui.components.zkmHazeEffect
import com.zuan.kernelmanager.next.ui.components.zkmHazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.ui.tooling.preview.Preview
import com.zuan.kernelmanager.next.ui.components.ZkmHazeStyleCache

enum class ActionType {
    FREEZE, DEBLOAT
}

@Composable
fun generateDebloatBackgroundColors(isDark: Boolean, primaryColor: Color): Pair<Color, Color> = com.zuan.kernelmanager.next.ui.components.ZkmScreenPalette.colors(isDark, primaryColor)

@Composable
fun DebloatFreezeScreen(
    navController: NavController,
    viewModel: DebloatFreezeViewModel = viewModel(),
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
            isDynamic -> Color(0xFF4A6595) // Fallback for dynamic
            else -> themeColorName.primary
        }
    }

    val isDark = when (themeMode) {
        com.zuan.kernelmanager.next.ui.theme.ThemeMode.LIGHT -> false
        com.zuan.kernelmanager.next.ui.theme.ThemeMode.DARK -> true
        com.zuan.kernelmanager.next.ui.theme.ThemeMode.SYSTEM_DEFAULT -> isSystemInDarkTheme()
    }
    
    val (themeBgColor, themeCardColor) = generateDebloatBackgroundColors(isDark, effectivePrimary)
    
    val isCustomBg = bgType != BgType.SYSTEM
    val isGlassActive = isHazeEnabled && isCustomBg
    
    val finalCardColor = when {
        isGlassActive -> zkmGlassFallbackTint(zkmIsDarkTheme())
        isCustomBg -> zkmCustomBackgroundCardColor(zkmIsDarkTheme(), cardDarkness)
        else -> themeCardColor
    }
    
    val mainBackgroundColor = if (isCustomBg) Color.Transparent else themeBgColor
    
    // Penyesuaian warna teks agar kontras
    val textColor = if (isCustomBg) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.onSurface
    val subTextColor = if (isCustomBg) zkmOnCustomBackgroundSubContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.onSurfaceVariant
    val dividerColor = if (isCustomBg) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()).copy(alpha = 0.15f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    
    val apps by viewModel.filteredApps.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    
    val userApps by viewModel.userAppsCount.collectAsStateWithLifecycle()
    val systemApps by viewModel.systemAppsCount.collectAsStateWithLifecycle()
    val frozenApps by viewModel.frozenAppsCount.collectAsStateWithLifecycle()

    var showDialogForApp by remember { mutableStateOf<AppUiModel?>(null) }
    var pendingActionApp by remember { mutableStateOf<AppUiModel?>(null) }
    var pendingActionType by remember { mutableStateOf<ActionType?>(null) }

    val hazeState = rememberHazeState()
    
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeightDp = with(localDensity) { headerHeightPx.toDp() }

    LaunchedEffect(Unit) {
        if (viewModel.totalAppsCount.value == 0) {
            viewModel.loadApps(context)
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
            // Warna background digambar SEBAGAI CHILD di dalam hazeSource supaya
            // ikut terekam ke buffer haze (blur appbar punya sumber di mode non-custom).
            // Sebelumnya Box ini kosong dan warna tema hanya ada di Box luar (bukan
            // hazeSource), sehingga progressive blur appbar tidak merender apa-apa.
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
            contentWindowInsets = WindowInsets(0,0,0,0)
        ) { _ ->
            
            Box(modifier = Modifier.fillMaxSize()) {
                
                // LAYER 1: LIST CONTENT
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = effectivePrimary)
                    }
                } else {
                    LazyColumn(
                        // HazeSource ada di sini agar terdeteksi oleh header
                        modifier = Modifier.fillMaxSize().zkmHazeSource(state = hazeState, zIndex = 1f),
                        contentPadding = PaddingValues(
                            top = headerHeightDp,
                            bottom = 100.dp
                        )
                    ) {
                        // KOTAK STATS DIPINDAHKAN KE DALAM SCROLL AGAR HEADER RAPI
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ExpressiveStatsChipGlass(
                                    label = stringResource(R.string.dod_user_apps), 
                                    count = userApps.toString(), 
                                    icon = Icons.Rounded.Person,
                                    color = effectivePrimary.copy(alpha = 0.2f),
                                    onColor = effectivePrimary,
                                    isGlassActive = isGlassActive,
                                    cardColor = finalCardColor,
                                    modifier = Modifier.weight(1f),
                                    hazeState = hazeState
                                )
                                ExpressiveStatsChipGlass(
                                    label = stringResource(R.string.dod_system_apps), 
                                    count = systemApps.toString(), 
                                    icon = Icons.Rounded.Android,
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                    onColor = MaterialTheme.colorScheme.secondary,
                                    isGlassActive = isGlassActive,
                                    cardColor = finalCardColor,
                                    modifier = Modifier.weight(1f),
                                    hazeState = hazeState
                                )
                                ExpressiveStatsChipGlass(
                                    label = stringResource(R.string.dod_frozen_apps), 
                                    count = frozenApps.toString(), 
                                    icon = Icons.Rounded.AcUnit,
                                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                                    onColor = MaterialTheme.colorScheme.tertiary,
                                    isGlassActive = isGlassActive,
                                    cardColor = finalCardColor,
                                    modifier = Modifier.weight(1f),
                                    hazeState = hazeState
                                )
                            }
                        }

                        items(apps, key = { it.packageName }) { app ->
                            AppListItemGlass(
                                app = app,
                                onClick = { showDialogForApp = app },
                                isGlassActive = isGlassActive,
                                hazeState = hazeState,
                                cardColor = finalCardColor,
                                textColor = textColor,
                                subTextColor = subTextColor
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 72.dp),
                                color = dividerColor
                            )
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
                        // Title dan Back Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, start = 8.dp, end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    Icons.Rounded.ArrowBack, 
                                    contentDescription = stringResource(R.string.dod_back), 
                                    tint = textColor
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.dod_title),
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = textColor
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            ZkmHelpButton(NavigationRoute.DebloatFreeze.route, tint = textColor)
                        }

                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            placeholder = { Text(stringResource(R.string.dod_search_hint), color = subTextColor) },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = subTextColor) },
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = if (isCustomBg) Color.White.copy(0.15f) else MaterialTheme.colorScheme.surfaceContainerHighest.zkmLiteAlpha(0.6f),
                                focusedContainerColor = if (isCustomBg) Color.White.copy(0.2f) else MaterialTheme.colorScheme.surfaceContainerHighest.zkmLiteAlpha(0.8f),
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = effectivePrimary.copy(alpha = 0.5f),
                                cursorColor = effectivePrimary,
                                focusedTextColor = textColor,
                                unfocusedTextColor = textColor
                            ),
                            singleLine = true
                        )

                        // Tab Row
                        ScrollableTabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = Color.Transparent,
                            contentColor = effectivePrimary,
                            edgePadding = 16.dp,
                            divider = {},
                            indicator = { tabPositions ->
                                if (selectedTab < tabPositions.size) {
                                    TabRowDefaults.SecondaryIndicator(
                                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                        height = 3.dp,
                                        color = effectivePrimary
                                    )
                                }
                            }
                        ) {
                            TabItemGlass(selected = selectedTab == 0, onClick = { viewModel.onTabSelected(0) }, text = stringResource(R.string.dod_tab_all), textColor = textColor)
                            TabItemGlass(selected = selectedTab == 1, onClick = { viewModel.onTabSelected(1) }, text = stringResource(R.string.dod_tab_user), textColor = textColor)
                            TabItemGlass(selected = selectedTab == 2, onClick = { viewModel.onTabSelected(2) }, text = stringResource(R.string.dod_tab_system), textColor = textColor)
                            TabItemGlass(selected = selectedTab == 3, onClick = { viewModel.onTabSelected(3) }, text = stringResource(R.string.dod_tab_frozen), textColor = textColor)
                        }
                        
                        // Ruang kosong ini krusial agar blur progressive punya ruang memudar
                        // di ruang kosong ini, bukan memudar tepat di atas Teks Tab.
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    val dialogApp = showDialogForApp
    if (dialogApp != null) {
        DoDExpressiveDialogGlass(
            app = dialogApp,
            onDismiss = { showDialogForApp = null },
            onDebloat = {
                if (dialogApp.isSystem) {
                    pendingActionApp = showDialogForApp
                    pendingActionType = ActionType.DEBLOAT
                    showDialogForApp = null
                } else {
                    viewModel.debloatApp(context, dialogApp)
                    showDialogForApp = null
                }
            },
            onFreezeToggle = {
                if (dialogApp.isSystem) {
                    pendingActionApp = showDialogForApp
                    pendingActionType = ActionType.FREEZE
                    showDialogForApp = null
                } else {
                    viewModel.toggleFreeze(context, dialogApp)
                    showDialogForApp = null
                }
            },
            onOpenSettings = {
                viewModel.openAppSettings(context, dialogApp.packageName)
            }
        )
    }

    val pendingApp = pendingActionApp
    val pendingType = pendingActionType
    if (pendingApp != null && pendingType != null) {
        SystemWarningDialog(
            app = pendingApp,
            actionType = pendingType,
            onDismiss = {
                pendingActionApp = null
                pendingActionType = null
            },
            onConfirm = {
                if (pendingType == ActionType.FREEZE) {
                    viewModel.toggleFreeze(context, pendingApp)
                } else {
                    viewModel.debloatApp(context, pendingApp)
                }
                pendingActionApp = null
                pendingActionType = null
            }
        )
    }
}

@Composable
fun ExpressiveStatsChipGlass(
    label: String, 
    count: String, 
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onColor: Color,
    isGlassActive: Boolean,
    cardColor: Color,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null
) {
    val shape = RoundedCornerShape(24.dp)
    val useGlass = isGlassActive && hazeState != null
    // Sebelumnya mode glass hanya Surface warna datar (color.copy(0.3)) TANPA blur, jadi tampak
    // pink/ungu solid dan beda dari kartu lain. Sekarang kaca referensi (sama dengan SetEdit);
    // warna semantik (primary/secondary/tertiary) tetap lewat ikon + angka.
    Surface(
        modifier = modifier
            .height(110.dp)
            .then(if (useGlass) Modifier.zkmReferenceGlass(hazeState!!, shape) else Modifier),
        shape = shape,
        color = if (useGlass) zkmReferenceGlassFill() else if (isGlassActive) color.copy(alpha = 0.3f) else cardColor,
        shadowElevation = if (isGlassActive) 0.dp else 2.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon, 
                contentDescription = null, 
                tint = onColor, 
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = count, 
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), 
                    color = onColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = label, 
                    style = MaterialTheme.typography.labelMedium, 
                    color = onColor.copy(alpha = 0.8f), 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun TabItemGlass(selected: Boolean, onClick: () -> Unit, text: String, textColor: Color) {
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
fun AppListItemGlass(
    app: AppUiModel, 
    onClick: () -> Unit,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    textColor: Color,
    subTextColor: Color
) {
    val shape = RoundedCornerShape(12.dp)
    
    val modifier = if (isGlassActive) {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .zkmReferenceGlassSurface(hazeState, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            bitmap = app.iconDrawable.toBitmap().asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(52.dp).clip(CircleShape)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (app.isEnabled) textColor else textColor.copy(alpha = 0.5f)
                )
                if (!app.isEnabled) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.dod_status_frozen),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = subTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun DoDExpressiveDialogGlass(
    app: AppUiModel,
    onDismiss: () -> Unit,
    onDebloat: () -> Unit,
    onFreezeToggle: () -> Unit,
    onOpenSettings: () -> Unit
) {
    BlurredAlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(24.dp).fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = ZkmDialogDefaults.Shape,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Image(
                    bitmap = app.iconDrawable.toBitmap().asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp).clip(CircleShape)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onOpenSettings,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.dod_app_info))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                DetailRow(stringResource(R.string.dod_version), app.versionName)
                DetailRow(stringResource(R.string.dod_version_code), app.versionCode.toString())
                DetailRow(stringResource(R.string.dod_package), app.packageName)
                DetailRow(stringResource(R.string.dod_target_sdk), app.targetSdk.toString())
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onFreezeToggle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (app.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                    ),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(if (app.isEnabled) Icons.Rounded.AcUnit else Icons.Rounded.PlayArrow, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (app.isEnabled) stringResource(R.string.dod_freeze_app) else stringResource(R.string.dod_unfreeze_app))
                }

                FilledTonalButton(
                    onClick = onDebloat,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.DeleteForever, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.dod_debloat_uninstall))
                }
            }
        },
        dismissButton = {} 
    )
}

@Composable
fun SystemWarningDialog(
    app: AppUiModel,
    actionType: ActionType,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var isChecked by remember { mutableStateOf(false) }

    BlurredAlertDialog(
        onDismissRequest = onDismiss,
        iconContentColor = MaterialTheme.colorScheme.error,
        icon = { Icon(Icons.Rounded.Warning, contentDescription = null, modifier = Modifier.size(32.dp)) },
        title = {
            Text(
                text = stringResource(R.string.dod_system_warning_title),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column {
                val actionTypeLabel = if(actionType == ActionType.FREEZE) 
                    stringResource(R.string.dod_action_freeze) 
                else 
                    stringResource(R.string.dod_action_uninstall)
                    
                Text(
                    text = stringResource(R.string.dod_system_warning_desc, actionTypeLabel),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${app.label} (${app.packageName})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp)).padding(4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.dod_system_warning_notice),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isChecked = !isChecked }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { isChecked = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.error,
                            uncheckedColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                            checkmarkColor = MaterialTheme.colorScheme.onError
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.dod_accept_risk),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = isChecked,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                )
            ) {
                val confirmText = if(actionType == ActionType.FREEZE) 
                    stringResource(R.string.dod_confirm_freeze) 
                else 
                    stringResource(R.string.dod_confirm_debloat)
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(0.6f),
            textAlign = TextAlign.End
        )
    }
}
