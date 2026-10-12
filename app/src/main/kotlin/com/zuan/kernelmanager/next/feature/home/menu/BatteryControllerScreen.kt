/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalHazeMaterialsApi::class)

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
import com.zuan.kernelmanager.next.ui.components.LocalZkmHazeState
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import androidx.compose.ui.zIndex
import com.zuan.kernelmanager.next.ui.components.BlurredAlertDialog
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.zuan.kernelmanager.next.ui.components.ZkmHazeStyleCache
import com.zuan.kernelmanager.next.ui.theme.zkmIconShape
import com.zuan.kernelmanager.next.ui.theme.ZkmIconGlyph
import com.zuan.kernelmanager.next.ui.components.ZkmSwitch
import com.zuan.kernelmanager.next.ui.components.ZkmSlider

@Composable
fun BatteryControllerScreen(
    navController: NavController,
    viewModel: BatteryControllerViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val localDensity = LocalDensity.current
    
    // Wallpaper Style States
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
    
    // Theme Colors
    val isDynamic by settingsViewModel.isDynamicColor.collectAsStateWithLifecycle()
    val themeColorName by settingsViewModel.currentThemeColor.collectAsStateWithLifecycle()
    val isCustomColor by settingsViewModel.isCustomColor.collectAsStateWithLifecycle()
    val customPrimary by settingsViewModel.customPrimaryColor.collectAsStateWithLifecycle()

    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()

    val effectivePrimary = remember(isDynamic, themeColorName, isCustomColor, customPrimary) {
        when {
            isCustomColor -> Color(customPrimary)
            isDynamic -> Color(0xFF4A6595)
            else -> themeColorName.primary
        }
    }

    val isDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM_DEFAULT -> isSystemInDarkTheme()
    }

    val (tintedBackground, cardContainerColor) = generateThemedBackgroundColor(effectivePrimary, isDarkTheme)
    
    val isCustomBg = bgType != BgType.SYSTEM
    val isGlassActive = isHazeEnabled && isCustomBg
    
    val finalCardColor = when {
        isGlassActive -> zkmGlassFallbackTint(zkmIsDarkTheme())
        isCustomBg -> zkmCustomBackgroundCardColor(zkmIsDarkTheme(), cardDarkness)
        else -> cardContainerColor
    }
    
    val mainBackgroundColor = if (isCustomBg) Color.Transparent else tintedBackground
    
    val textColor = if (isCustomBg) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.onSurface
    val subTextColor = if (isCustomBg) zkmOnCustomBackgroundSubContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.onSurfaceVariant
    
    val batteryInfo by viewModel.batteryInfo.collectAsStateWithLifecycle()
    val chargingStats by viewModel.chargingStats.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    
    val hazeState = rememberHazeState()
    
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeightDp = with(localDensity) { headerHeightPx.toDp() }

    LaunchedEffect(Unit) {
        viewModel.loadBatteryData(context)
    }

    Box(modifier = Modifier.fillMaxSize().background(mainBackgroundColor)) {
        // BACKGROUND LAYER (Custom Wallpaper)
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
                    VideoWallpaperPlayer(uri = uri, modifier = Modifier.fillMaxSize().then(blurModifier))
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
            Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState, zIndex = 0f)) {
                Box(modifier = Modifier.fillMaxSize().background(mainBackgroundColor))
            }
        }
        
        // WEATHER OVERLAY
        WeatherEffectOverlay(effect = weatherEffect, intensity = weatherIntensity, modifier = Modifier.fillMaxSize())

        // CONTENT
        androidx.compose.runtime.CompositionLocalProvider(LocalZkmHazeState provides hazeState) {
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
                        0 -> BatteryDashboardTab(
                            viewModel = viewModel, isGlassActive = isGlassActive, hazeState = hazeState,
                            cardColor = finalCardColor, textColor = textColor, subTextColor = subTextColor,
                            primaryColor = effectivePrimary, topPadding = headerHeightDp
                        )
                        1 -> ChargeControlTab(
                            viewModel = viewModel, isGlassActive = isGlassActive, hazeState = hazeState,
                            cardColor = finalCardColor, textColor = textColor, subTextColor = subTextColor,
                            primaryColor = effectivePrimary, topPadding = headerHeightDp
                        )
                        else -> BatteryRawDataTab(
                            viewModel = viewModel, isGlassActive = isGlassActive, hazeState = hazeState,
                            cardColor = finalCardColor, textColor = textColor, subTextColor = subTextColor,
                            primaryColor = effectivePrimary, topPadding = headerHeightDp
                        )
                    }
                }
                
                // LAYER 2: HEADER WITH PROGRESSIVE BLUR
                val headerHazeStyle = zkmAppBarHazeStyle(tintedBackground, isCustomBg)
                
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
                                Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.btn_back), tint = textColor) 
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.battery_statistics_title), 
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), 
                                color = textColor
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            ZkmHelpButton(NavigationRoute.BatteryController.route, tint = textColor)
                        }
                        
                        // Pill tab selector (modern segmented control)
                        BatteryPillTabs(
                            titles = listOf(
                                stringResource(R.string.battery_tab_overview),
                                stringResource(R.string.battery_tab_charge_control),
                                stringResource(R.string.battery_tab_raw)
                            ),
                            selected = selectedTab,
                            onSelect = { viewModel.onTabSelected(it) },
                            primaryColor = effectivePrimary,
                            textColor = textColor,
                            isCustomBg = isCustomBg,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        
                        // Ruang kosong untuk progressive blur fading out (Bumbu Rahasia 2)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
        } // end CompositionLocalProvider(LocalZkmHazeState)
    }
}

/**
 * GENERATE TINTED BACKGROUND - CARA KERJA SEPERTI ABOUT SCREEN
 */
@Composable
fun generateThemedBackgroundColor(primaryColor: Color, isDark: Boolean): Pair<Color, Color> = com.zuan.kernelmanager.next.ui.components.ZkmScreenPalette.colors(isDark, primaryColor)

@Composable
fun GlassBatteryCard(
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val glassModifier = if (isGlassActive) {
        Modifier
            .zkmCardHazeEffect(shape = shape, state = hazeState, style = ZkmHazeStyleCache.of(backgroundColor = cardColor.copy(alpha = 0.5f), blurRadius = 24.dp, noiseFactor = 0.1f, tints = emptyList()))
            .zkmCardBorder(width = 1.dp, brush = Brush.linearGradient(colors = listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.05f))), shape = shape)
    } else {
        Modifier.clip(shape)
    }
    Card(modifier = modifier.then(glassModifier), shape = shape, colors = CardDefaults.cardColors(containerColor = if (isGlassActive) zkmCardFill(zkmGlassTint(MaterialTheme.colorScheme.surfaceContainerHigh, zkmIsDarkTheme(), 0.40f)) else cardColor)) {
        content()
    }
}

@Composable
fun MiniInfoCardGlass(label: String, value: String, icon: ImageVector, isGlassActive: Boolean, textColor: Color, subTextColor: Color) {
    val miniShape = RoundedCornerShape(12.dp)
    val miniHaze = LocalZkmHazeState.current
    val miniModifier = if (isGlassActive) Modifier.fillMaxWidth().height(48.dp).zkmGlassCard(true, miniHaze, miniShape, zkmGlassTint(MaterialTheme.colorScheme.surfaceContainerHigh, zkmIsDarkTheme(), 0.45f), 16.dp) else Modifier.fillMaxWidth().height(48.dp)
    Card(modifier = miniModifier, shape = miniShape, colors = CardDefaults.cardColors(containerColor = if (isGlassActive) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = subTextColor, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = subTextColor)
                Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = textColor)
            }
        }
    }
}

@Composable
fun PeakWavyGaugeGlass(title: String, value: String, progress: Float, color: Color, isGlassActive: Boolean, textColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.size(60.dp),
                color = color,
                trackColor = if (isGlassActive) Color.White.copy(0.2f) else MaterialTheme.colorScheme.surfaceContainerHighest
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = textColor)
        Text(title, style = MaterialTheme.typography.labelSmall, color = textColor.copy(0.7f))
    }
}

@Composable
fun LiveRoundedBarChartGlass(data: List<ChartPoint>, isGlassActive: Boolean, primaryColor: Color) {
    if (data.isEmpty()) return
    val textMeasurer = rememberTextMeasurer()
    val labelColor = if (isGlassActive) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.onSurfaceVariant
    val chargeColor = Color(0xFF4CAF50)
    val dischargeColor = primaryColor
    val highlightColor = MaterialTheme.colorScheme.tertiary
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val maxVal = data.maxOfOrNull { it.value }?.coerceAtLeast(100) ?: 100
        val textHeight = 40.dp.toPx()
        val graphHeight = size.height - (textHeight * 2)
        val barCount = 18
        val stepX = size.width / barCount
        val barWidth = stepX * 0.65f
        val cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)

        drawLine(color = labelColor.copy(alpha = 0.2f), start = Offset(0f, textHeight + graphHeight), end = Offset(size.width, textHeight + graphHeight), strokeWidth = 2f)

        data.forEachIndexed { i, point ->
            val barHeight = (point.value.toFloat() / maxVal) * graphHeight
            val xOffset = (i * stepX) + (stepX - barWidth) / 2
            val yOffset = textHeight + graphHeight - barHeight
            val isLatest = i == data.lastIndex
            val barColor = when {
                isLatest -> highlightColor
                point.isCharging -> chargeColor
                else -> dischargeColor
            }

            drawRoundRect(color = barColor, topLeft = Offset(xOffset, yOffset), size = Size(barWidth, barHeight.coerceAtLeast(10f)), cornerRadius = cornerRadius)

            if (isLatest || i % 4 == 0) {
                val valueText = "${point.value}"
                val textLayoutResult = textMeasurer.measure(text = valueText, style = TextStyle(color = labelColor, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                drawText(textLayoutResult = textLayoutResult, topLeft = Offset(x = xOffset + (barWidth / 2) - (textLayoutResult.size.width / 2), y = yOffset - textLayoutResult.size.height - 8f))
            }

            if (i == 0 || i == data.lastIndex || i % 4 == 0) {
                val timeText = timeFormat.format(Date(point.timeStamp))
                val timeLayout = textMeasurer.measure(text = timeText, style = TextStyle(color = labelColor.copy(alpha=0.7f), fontSize = 9.sp))
                drawText(textLayoutResult = timeLayout, topLeft = Offset(x = xOffset + (barWidth / 2) - (timeLayout.size.width / 2), y = textHeight + graphHeight + 12f))
            }
        }
    }
}

@Composable
fun BmsRowGlass(label: String, value: String, textColor: Color, subTextColor: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = subTextColor)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = textColor)
    }
}

@Composable
fun BatterySectionTitleGlass(title: String, textColor: Color) {
    Text(text = title, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = textColor.copy(0.9f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
}

@Composable
fun ModernControlCardGlass(title: String, subtitle: String, icon: ImageVector, checked: Boolean, isGlassActive: Boolean, textColor: Color, subTextColor: Color, primaryColor: Color, onCheckedChange: (Boolean) -> Unit) {
    val ctrlShape = RoundedCornerShape(20.dp)
    val ctrlHaze = LocalZkmHazeState.current
    val ctrlModifier = if (isGlassActive) Modifier.fillMaxWidth().padding(horizontal = 16.dp).zkmGlassCard(true, ctrlHaze, ctrlShape, zkmGlassTint(MaterialTheme.colorScheme.surfaceContainer, zkmIsDarkTheme(), 0.45f), 20.dp).clickable { onCheckedChange(!checked) } else Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { onCheckedChange(!checked) }
    Card(modifier = ctrlModifier, shape = ctrlShape, colors = CardDefaults.cardColors(containerColor = if (isGlassActive) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).clip(zkmIconShape(CircleShape)).background(if (checked) primaryColor else if (isGlassActive) Color.White.copy(0.2f) else MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                ZkmIconGlyph(icon = icon, tint = if (checked) Color.White else subTextColor, iconSize = 20.dp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = textColor)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = subTextColor)
            }
            ZkmSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
fun SmartCutoffCardGlass(enabled: Boolean, limit: Float, onToggle: (Boolean) -> Unit, onLimitChange: (Float) -> Unit, isGlassActive: Boolean, hazeState: HazeState, cardColor: Color, textColor: Color, subTextColor: Color, primaryColor: Color) {
    GlassBatteryCard(isGlassActive = isGlassActive, hazeState = hazeState, cardColor = cardColor, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).clip(zkmIconShape(CircleShape)).background(primaryColor.copy(0.2f)), contentAlignment = Alignment.Center) {
                    ZkmIconGlyph(painter = painterResource(R.drawable.ic_battery_android_frame_shield), tint = primaryColor, iconSize = 24.dp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.battery_smart_cutoff), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = textColor)
                    Text(stringResource(R.string.battery_smart_cutoff_desc), style = MaterialTheme.typography.bodySmall, color = subTextColor)
                }
                ZkmSwitch(checked = enabled, onCheckedChange = onToggle)
            }
            if (enabled) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.battery_limit_label, limit.toInt()), style = MaterialTheme.typography.labelMedium, color = primaryColor, fontWeight = FontWeight.Bold)
                ZkmSlider(value = limit, onValueChange = onLimitChange, valueRange = 50f..100f, steps = 9)
            }
        }
    }
}

@Composable
fun ThermalProfileCardGlass(viewModel: BatteryControllerViewModel, isGlassActive: Boolean, hazeState: HazeState, cardColor: Color, textColor: Color, subTextColor: Color, primaryColor: Color) {
    val currentProfile by viewModel.thermalSconfig.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    val profileName = when(currentProfile) { 
        "0" -> stringResource(R.string.battery_thermal_default)
        "13" -> stringResource(R.string.battery_thermal_gaming)
        "10" -> stringResource(R.string.battery_thermal_benchmark)
        else -> "Custom ($currentProfile)" 
    }
    
    GlassBatteryCard(isGlassActive = isGlassActive, hazeState = hazeState, cardColor = cardColor, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { showDialog = true }) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).clip(zkmIconShape(CircleShape)).background(Color(0xFFE67E22).copy(alpha=0.2f)), contentAlignment = Alignment.Center) {
                ZkmIconGlyph(painter = painterResource(R.drawable.ic_battery_profile), tint = Color(0xFFE67E22), iconSize = 20.dp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.battery_thermal_profile), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = textColor)
                Text(profileName, style = MaterialTheme.typography.bodySmall, color = subTextColor)
            }
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = subTextColor)
        }
    }
    
    if (showDialog) {
        BlurredAlertDialog(
            onDismissRequest = { showDialog = false }, 
            title = { Text(stringResource(R.string.battery_select_thermal_profile)) }, 
            text = { 
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { 
                    TextButton(onClick = { viewModel.updateThermalSconfig("0"); showDialog = false }
        ) { 
                        Text(stringResource(R.string.battery_thermal_default), modifier = Modifier.fillMaxWidth()) 
                    } 
                    TextButton(onClick = { viewModel.updateThermalSconfig("13"); showDialog = false }
        ) { 
                        Text(stringResource(R.string.battery_thermal_gaming), modifier = Modifier.fillMaxWidth()) 
                    } 
                    TextButton(onClick = { viewModel.updateThermalSconfig("10"); showDialog = false }
        ) { 
                        Text(stringResource(R.string.battery_thermal_benchmark), modifier = Modifier.fillMaxWidth()) 
                    } 
                } 
            }, 
            confirmButton = { 
                TextButton(onClick = { showDialog = false }
        ) { 
                    Text(stringResource(R.string.btn_cancel)) 
                } 
            }
        )
    }
}

@Composable
fun BatteryTipCardGlass(icon: ImageVector, title: String, description: String, isGlassActive: Boolean, textColor: Color, subTextColor: Color) {
    val tipShape = RoundedCornerShape(12.dp)
    val tipHaze = LocalZkmHazeState.current
    val tipModifier = if (isGlassActive) Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).zkmGlassCard(true, tipHaze, tipShape, zkmGlassTint(MaterialTheme.colorScheme.surfaceContainerLow, zkmIsDarkTheme(), 0.45f), 16.dp) else Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    Card(modifier = tipModifier, shape = tipShape, colors = CardDefaults.cardColors(containerColor = if (isGlassActive) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = textColor.copy(0.8f), modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = textColor)
                Text(description, style = MaterialTheme.typography.bodySmall, color = subTextColor)
            }
        }
    }
}

// =====================================================================================
//  BATTERY SCREEN v2 — pill tabs, hero card, Charge Control, Raw Data
// =====================================================================================

@Composable
fun BatteryPillTabs(
    titles: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    primaryColor: Color,
    textColor: Color,
    isCustomBg: Boolean,
    modifier: Modifier = Modifier
) {
    val trackColor = if (isCustomBg) Color.White.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.75f)
    val onPrimary = if (primaryColor.luminance() > 0.55f) Color.Black else Color.White
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(trackColor)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        titles.forEachIndexed { index, title ->
            val isSelected = index == selected
            val bg by animateColorAsState(if (isSelected) primaryColor else Color.Transparent, label = "pillBg$index")
            val fg by animateColorAsState(if (isSelected) onPrimary else textColor.copy(alpha = 0.85f), label = "pillFg$index")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(bg)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    color = fg,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                )
            }
        }
    }
}

@Composable
internal fun BatteryStatTile(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    isGlassActive: Boolean,
    textColor: Color,
    subTextColor: Color,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    val haze = LocalZkmHazeState.current
    val base = if (isGlassActive) {
        modifier.zkmGlassCard(true, haze, shape, zkmGlassTint(MaterialTheme.colorScheme.surfaceContainerHigh, zkmIsDarkTheme(), 0.45f), 20.dp)
    } else {
        modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
    }
    Column(modifier = base.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp)) }
        Column {
            Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = textColor, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelMedium, color = subTextColor, maxLines = 1)
        }
    }
}

@Composable
fun BatteryDashboardTab(
    viewModel: BatteryControllerViewModel,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    textColor: Color,
    subTextColor: Color,
    primaryColor: Color,
    topPadding: androidx.compose.ui.unit.Dp
) {
    val info by viewModel.batteryInfo.collectAsStateWithLifecycle()
    val stats by viewModel.chargingStats.collectAsStateWithLifecycle()
    val batteryCapacity by viewModel.batteryCapacity.collectAsStateWithLifecycle()
    val peakTemp by viewModel.peakTemp.collectAsStateWithLifecycle()
    val peakCharge by viewModel.peakChargeCurrent.collectAsStateWithLifecycle()
    val peakDischarge by viewModel.peakDischargeCurrent.collectAsStateWithLifecycle()
    val history by viewModel.currentHistory.collectAsStateWithLifecycle()

    val batteryInfo = info ?: run {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(modifier = Modifier.size(48.dp), color = primaryColor)
        }
        return
    }

    val chargeGreen = Color(0xFF4CAF50)
    val accent = if (batteryInfo.isCharging) chargeGreen else primaryColor
    val watts = stats?.chargingPower ?: 0f
    val signedWatts = if (batteryInfo.isCharging) watts else -watts

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding + 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ---- HERO ----
        item {
            GlassBatteryCard(isGlassActive = isGlassActive, hazeState = hazeState, cardColor = cardColor, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(128.dp)) {
                        CircularWavyProgressIndicator(
                            progress = { batteryInfo.percentage / 100f },
                            modifier = Modifier.fillMaxSize(),
                            color = accent,
                            trackColor = if (isGlassActive) Color.White.copy(0.2f) else MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        Text("${batteryInfo.percentage}%", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold), color = textColor)
                    }
                    Spacer(Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (batteryInfo.isCharging) stringResource(R.string.battery_charging) else stringResource(R.string.battery_discharging),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = textColor
                        )
                        Text(
                            "%+.2f W".format(signedWatts),
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), color = accent
                        )
                        Text(
                            "${batteryInfo.currentNow} mA  •  ${batteryInfo.chargingType}",
                            style = MaterialTheme.typography.bodyMedium, color = subTextColor
                        )
                    }
                }
            }
        }

        // ---- STAT GRID (2 x 3) ----
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BatteryStatTile(stringResource(R.string.battery_temp), "${batteryInfo.temperature}°C", Icons.Rounded.Thermostat, Color(0xFFFF9800), isGlassActive, textColor, subTextColor, Modifier.weight(1f))
                    BatteryStatTile(stringResource(R.string.battery_voltage), "%.2f V".format(batteryInfo.voltage / 1000f), Icons.Rounded.ElectricBolt, Color(0xFFFFC107), isGlassActive, textColor, subTextColor, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BatteryStatTile(stringResource(R.string.battery_stat_cycles), "${batteryInfo.cycleCount}", Icons.Rounded.Loop, Color(0xFF26C6DA), isGlassActive, textColor, subTextColor, Modifier.weight(1f))
                    BatteryStatTile(stringResource(R.string.battery_stat_health), "$batteryCapacity %  •  ${batteryInfo.health}", Icons.Rounded.HealthAndSafety, chargeGreen, isGlassActive, textColor, subTextColor, Modifier.weight(1f))
                }
            }
        }

        // ---- PEAK RECORDS ----
        item {
            Column {
                Row(modifier = Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BatterySectionTitleGlass(stringResource(R.string.battery_peak_records), textColor)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { viewModel.resetPeaks() }) { Text(stringResource(R.string.battery_reset_peaks), color = primaryColor) }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    PeakWavyGaugeGlass(stringResource(R.string.battery_temp_max), "${peakTemp}°C", peakTemp / 50f, Color(0xFFFF9800), isGlassActive, textColor)
                    PeakWavyGaugeGlass(stringResource(R.string.battery_discharge), "-${peakDischarge}mA", peakDischarge / 5000f, Color(0xFF2196F3), isGlassActive, textColor)
                    PeakWavyGaugeGlass(stringResource(R.string.battery_charge_max), "+${peakCharge}mA", peakCharge / 6000f, chargeGreen, isGlassActive, textColor)
                }
            }
        }

        // ---- LIVE CHART ----
        item {
            Column {
                BatterySectionTitleGlass(stringResource(R.string.battery_current_live_chart), textColor)
                GlassBatteryCard(isGlassActive = isGlassActive, hazeState = hazeState, cardColor = cardColor, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(240.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val currentLabel = if (batteryInfo.isCharging) "+${batteryInfo.currentNow} mA" else "-${batteryInfo.currentNow} mA"
                        Text(currentLabel, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = accent)
                        Spacer(modifier = Modifier.height(12.dp))
                        LiveRoundedBarChartGlass(data = history, isGlassActive = isGlassActive, primaryColor = primaryColor)
                    }
                }
            }
        }

        // ---- BMS SUMMARY ----
        item {
            Column {
                BatterySectionTitleGlass(stringResource(R.string.battery_bms_data), textColor)
                GlassBatteryCard(isGlassActive = isGlassActive, hazeState = hazeState, cardColor = cardColor, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val pwr = stats?.let { String.format("%.2f W", it.chargingPower) } ?: "N/A"
                        BmsRowGlass(stringResource(R.string.battery_discharge_charge_speed), "${batteryInfo.currentNow} mA", textColor, subTextColor)
                        BmsRowGlass(stringResource(R.string.battery_charging_type), batteryInfo.chargingType, textColor, subTextColor)
                        BmsRowGlass(stringResource(R.string.battery_current_voltage), "%.3f v".format(batteryInfo.voltage / 1000f), textColor, subTextColor)
                        BmsRowGlass(stringResource(R.string.battery_max_voltage), "%.3f v".format((stats?.maxVoltage ?: 0) / 1000f), textColor, subTextColor)
                        BmsRowGlass(stringResource(R.string.battery_power_watt), pwr, textColor, subTextColor)
                        BmsRowGlass(stringResource(R.string.battery_tech), batteryInfo.technology, textColor, subTextColor)
                        BmsRowGlass(stringResource(R.string.battery_real_capacity), "$batteryCapacity %", textColor, subTextColor)
                    }
                }
            }
        }

        // ---- TIPS ----
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BatterySectionTitleGlass(stringResource(R.string.battery_health_tips), textColor)
                BatteryTipCardGlass(Icons.Rounded.AcUnit, stringResource(R.string.battery_tip_temp_title), stringResource(R.string.battery_tip_temp_desc), isGlassActive, textColor, subTextColor)
                BatteryTipCardGlass(Icons.Rounded.BatteryChargingFull, stringResource(R.string.battery_tip_discharge_title), stringResource(R.string.battery_tip_discharge_desc), isGlassActive, textColor, subTextColor)
            }
        }
    }
}

@Composable
private fun CcCardHeader(
    title: String,
    icon: ImageVector,
    accent: Color,
    textColor: Color,
    trailing: @Composable () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(40.dp).clip(zkmIconShape(CircleShape)).background(accent.copy(0.2f)), contentAlignment = Alignment.Center) {
            ZkmIconGlyph(icon = icon, tint = accent, iconSize = 20.dp)
        }
        Spacer(Modifier.width(14.dp))
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = textColor)
        trailing()
    }
}

@Composable
fun ChargeControlTab(
    viewModel: BatteryControllerViewModel,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    textColor: Color,
    subTextColor: Color,
    primaryColor: Color,
    topPadding: androidx.compose.ui.unit.Dp
) {
    val context = LocalContext.current
    val isChargingEnabled by viewModel.isChargingEnabled.collectAsStateWithLifecycle()
    val isFastChargeEnabled by viewModel.isFastChargeEnabled.collectAsStateWithLifecycle()
    val isFastChargeSupported by viewModel.isFastChargeSupported.collectAsStateWithLifecycle()
    val isBatterySaverEnabled by viewModel.isBatterySaverEnabled.collectAsStateWithLifecycle()
    val isBypassEnabled by viewModel.isBypassEnabled.collectAsStateWithLifecycle()
    val isBypassSupported by viewModel.isBypassSupported.collectAsStateWithLifecycle()
    val smartCutoffEnabled by viewModel.smartCutoffEnabled.collectAsStateWithLifecycle()
    val stopAt by viewModel.smartCutoffLimit.collectAsStateWithLifecycle()
    val resumeBelow by viewModel.resumeBelow.collectAsStateWithLifecycle()
    val isSmartChargeSupported by viewModel.isSmartChargeSupported.collectAsStateWithLifecycle()
    val monitorEnabled by viewModel.monitorEnabled.collectAsStateWithLifecycle()
    val hasThermalSconfig by viewModel.hasThermalSconfig.collectAsStateWithLifecycle()
    val speedEnabled by viewModel.speedEnabled.collectAsStateWithLifecycle()
    val speedTarget by viewModel.speedTargetMa.collectAsStateWithLifecycle()
    val actualUpper by viewModel.actualUpperMa.collectAsStateWithLifecycle()
    val percentLimitSupported by viewModel.percentLimitSupported.collectAsStateWithLifecycle()
    val chargingLimit by viewModel.chargingLimit.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding + 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ---- CHARGING SPEED ----
        item {
            GlassBatteryCard(isGlassActive, hazeState, cardColor, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CcCardHeader(stringResource(R.string.cc_speed_title), Icons.Rounded.Speed, primaryColor, textColor) {
                        ZkmSwitch(checked = speedEnabled, onCheckedChange = { viewModel.toggleSpeedOverride(context, it) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ZkmSlider(
                            value = speedTarget.toFloat(),
                            onValueChange = { viewModel.previewSpeed(((it / 50f).toInt() * 50).coerceIn(500, 6000)) },
                            onValueChangeFinished = { viewModel.commitSpeed(context) },
                            valueRange = 500f..6000f,
                            enabled = speedEnabled,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("$speedTarget mA", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = if (speedEnabled) primaryColor else subTextColor)
                    }
                    Text(
                        if (actualUpper > 0) stringResource(R.string.cc_speed_actual, actualUpper) else stringResource(R.string.cc_speed_actual_unknown),
                        style = MaterialTheme.typography.bodyMedium, color = subTextColor
                    )
                    Text(stringResource(R.string.cc_speed_desc), style = MaterialTheme.typography.bodySmall, color = subTextColor.copy(alpha = 0.8f))
                }
            }
        }

        // ---- CHARGING PROTECTION + MANUAL CONTROL ----
        item {
            GlassBatteryCard(isGlassActive, hazeState, cardColor, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CcCardHeader(stringResource(R.string.cc_protection_title), Icons.Rounded.Shield, Color(0xFF4CAF50), textColor) {
                        ZkmSwitch(
                            checked = smartCutoffEnabled,
                            onCheckedChange = { viewModel.toggleSmartCutoff(context, it) },
                            enabled = isSmartChargeSupported
                        )
                    }
                    if (!isSmartChargeSupported) {
                        Text(stringResource(R.string.cc_not_supported), style = MaterialTheme.typography.bodyMedium, color = subTextColor)
                    } else {
                        Text(stringResource(R.string.cc_protection_stop, stopAt.toInt()), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = textColor)
                        ZkmSlider(value = stopAt, onValueChange = { viewModel.setSmartCutoffLimit(context, it.toInt().toFloat()) }, valueRange = 50f..100f, steps = 49)
                        Text(stringResource(R.string.cc_protection_resume, resumeBelow.toInt()), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = textColor)
                        ZkmSlider(value = resumeBelow, onValueChange = { viewModel.setResumeBelow(context, it.toInt().toFloat()) }, valueRange = 20f..(stopAt - 2f).coerceAtLeast(21f), steps = 0)
                        Text(
                            stringResource(R.string.cc_protection_summary, stopAt.toInt(), resumeBelow.toInt()),
                            style = MaterialTheme.typography.bodyMedium, color = subTextColor
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = subTextColor.copy(alpha = 0.2f))

                    Text(stringResource(R.string.cc_manual_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = textColor)
                    Text(
                        if (isChargingEnabled) stringResource(R.string.cc_manual_state_active) else stringResource(R.string.cc_manual_state_stopped),
                        style = MaterialTheme.typography.bodySmall, color = subTextColor
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(
                            onClick = { viewModel.toggleCharging(false) },
                            enabled = isChargingEnabled,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.cc_manual_stop), maxLines = 1)
                        }
                        FilledTonalButton(
                            onClick = { viewModel.toggleCharging(true) },
                            enabled = !isChargingEnabled,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.cc_manual_resume), maxLines = 1)
                        }
                    }
                }
            }
        }

        // ---- MORE CONTROLS ----
        item { BatterySectionTitleGlass(stringResource(R.string.cc_more_controls), textColor) }
        item { ModernControlCardGlass(stringResource(R.string.battery_monitor_service), stringResource(R.string.battery_monitor_service_desc), Icons.Rounded.Visibility, monitorEnabled, isGlassActive, textColor, subTextColor, primaryColor) { viewModel.toggleMonitor(context, it) } }
        if (hasThermalSconfig) {
            item { ThermalProfileCardGlass(viewModel, isGlassActive, hazeState, cardColor, textColor, subTextColor, primaryColor) }
        }
        if (isFastChargeSupported) {
            item { ModernControlCardGlass(stringResource(R.string.battery_fast_charging), stringResource(R.string.battery_fast_charging_desc), Icons.Rounded.Speed, isFastChargeEnabled, isGlassActive, textColor, subTextColor, primaryColor) { viewModel.toggleFastCharge(it) } }
        }
        if (isBypassSupported) {
            item { ModernControlCardGlass(stringResource(R.string.battery_bypass_charging), stringResource(R.string.battery_bypass_charging_desc), Icons.Rounded.PowerOff, isBypassEnabled, isGlassActive, textColor, subTextColor, primaryColor) { viewModel.toggleBypass(it) } }
        }
        item { ModernControlCardGlass(stringResource(R.string.battery_saver_mode), stringResource(R.string.battery_saver_mode_desc), Icons.Rounded.EnergySavingsLeaf, isBatterySaverEnabled, isGlassActive, textColor, subTextColor, primaryColor) { viewModel.toggleBatterySaver(it) } }

        // ---- KERNEL % LIMIT (hanya jika node benar-benar persen) ----
        if (percentLimitSupported) {
            item {
                Column {
                    BatterySectionTitleGlass(stringResource(R.string.cc_kernel_limit_title), textColor)
                    Text(
                        stringResource(R.string.battery_system_charging_limit_desc),
                        style = MaterialTheme.typography.bodyMedium, color = subTextColor,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        viewModel.chargingLimitOptions.forEach { limit ->
                            FilterChip(selected = limit == chargingLimit, onClick = { viewModel.setChargingLimit(limit) }, label = { Text("$limit%", color = textColor) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UeventCard(
    title: String,
    raw: String,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    textColor: Color,
    subTextColor: Color,
    primaryColor: Color
) {
    val context = LocalContext.current
    val copiedMsg = stringResource(R.string.raw_copied)
    // Kernel memberi prefix POWER_SUPPLY_ pada tiap key; dibuang supaya mudah dibaca.
    val pretty = remember(raw) { raw.lineSequence().map { it.removePrefix("POWER_SUPPLY_") }.filter { it.isNotBlank() }.joinToString("\n") }
    GlassBatteryCard(isGlassActive, hazeState, cardColor, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$ $title", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = textColor)
                IconButton(
                    onClick = {
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText(title, pretty))
                        android.widget.Toast.makeText(context, copiedMsg, android.widget.Toast.LENGTH_SHORT).show()
                    },
                    enabled = pretty.isNotEmpty()
                ) { Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.raw_copy), tint = primaryColor) }
            }
            if (pretty.isEmpty()) {
                Text(stringResource(R.string.raw_empty), style = MaterialTheme.typography.bodyMedium, color = subTextColor)
            } else {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        pretty,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, lineHeight = 20.sp),
                        color = textColor.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

@Composable
fun BatteryRawDataTab(
    viewModel: BatteryControllerViewModel,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    textColor: Color,
    subTextColor: Color,
    primaryColor: Color,
    topPadding: androidx.compose.ui.unit.Dp
) {
    val bms by viewModel.bmsUevent.collectAsStateWithLifecycle()
    val usb by viewModel.usbUevent.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding + 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { UeventCard(stringResource(R.string.raw_bms_title), bms, isGlassActive, hazeState, cardColor, textColor, subTextColor, primaryColor) }
        item { UeventCard(stringResource(R.string.raw_usb_title), usb, isGlassActive, hazeState, cardColor, textColor, subTextColor, primaryColor) }
        item {
            Text(
                stringResource(R.string.raw_disclaimer),
                style = MaterialTheme.typography.labelMedium, color = subTextColor,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}
