/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalHazeMaterialsApi::class,
    ExperimentalLayoutApi::class
)

package com.zuan.kernelmanager.next.feature.home

import com.zuan.kernelmanager.next.ui.adaptive.zkmLargeAppBarExpandedHeight
import com.zuan.kernelmanager.next.ui.components.zkmAppBarHazeStyle
import com.zuan.kernelmanager.next.ui.adaptive.LocalZkmRailActive
import com.zuan.kernelmanager.next.ui.adaptive.LocalZkmWindow
import com.zuan.kernelmanager.next.ui.adaptive.ZkmRailWidth
import com.zuan.kernelmanager.next.ui.adaptive.zkmContentHorizontalPadding
import com.zuan.kernelmanager.next.ui.components.zkmCardHazeEffect
import com.zuan.kernelmanager.next.ui.components.zkmCardBorder
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundContentColor
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundSubContentColor
import com.zuan.kernelmanager.next.ui.components.zkmCustomBackgroundCardColor
import com.zuan.kernelmanager.next.ui.components.zkmGlassFallbackTint
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import com.zuan.kernelmanager.next.ui.components.zkmHazeEffect
import com.zuan.kernelmanager.next.ui.components.zkmHazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import kotlinx.coroutines.delay
import com.zuan.kernelmanager.next.ui.components.ZkmHazeStyleCache
import com.zuan.kernelmanager.next.ui.theme.zkmIconShape
import com.zuan.kernelmanager.next.ui.theme.zkmMenuIconShape
import com.zuan.kernelmanager.next.ui.theme.zkmMenuIconShapeFor
import com.zuan.kernelmanager.next.ui.theme.ZkmIconShapeStyle
import com.zuan.kernelmanager.next.ui.theme.LocalZkmIconRandomSeed
import com.zuan.kernelmanager.next.feature.settings.SettingsPreference
import com.zuan.kernelmanager.next.ui.theme.ZkmIconGlyph
import com.zuan.kernelmanager.next.ui.theme.ZkmPaddedIcon

private object HomeMotion {
    fun <T> spatial(): SpringSpec<T> = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow)
    fun <T> bouncy(): SpringSpec<T> = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
}

@Composable
private fun HomeEntrance(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(50L * index)
        visible = true
    }

    val transition = updateTransition(targetState = visible, label = "HomeEntrance")
    val alpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 400, easing = LinearOutSlowInEasing) },
        label = "alpha"
    ) { if (it) 1f else 0f }
    val translateY by transition.animateDp(
        transitionSpec = { HomeMotion.spatial() },
        label = "translateY"
    ) { if (it) 0.dp else 24.dp }
    val scale by transition.animateFloat(
        transitionSpec = { HomeMotion.spatial() },
        label = "scale"
    ) { if (it) 1f else 0.95f }

    Box(
        modifier = modifier
            .alpha(alpha)
            .offset(y = translateY)
            .scale(scale)
    ) {
        content()
    }
}

private fun Modifier.homePressEffect(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.94f
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = HomeMotion.bouncy(),
        label = "pressScale"
    )
    this.scale(scale)
}

@Composable
private fun HomeIconContainer(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    iconColor: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    cornerRadius: Dp = 14.dp,
    menuId: String? = null
) {
    val context = LocalContext.current
    val preferences = remember(context) { SettingsPreference.getInstance(context) }
    val menuIconPaths = preferences.customIconImagePaths.collectAsStateWithLifecycle().value
    val randomSeed = LocalZkmIconRandomSeed.current
    val menuShapeStyles = preferences.menuIconShapes.collectAsStateWithLifecycle().value
    val configuredStyle = menuId?.let { menuShapeStyles[it] }?.let { ZkmIconShapeStyle.fromName(it) }
    val iconShape = if (configuredStyle != null) {
        zkmMenuIconShapeFor(RoundedCornerShape(cornerRadius), configuredStyle, randomSeed xor (menuId?.hashCode() ?: 0))
    } else {
        zkmMenuIconShape(RoundedCornerShape(cornerRadius), randomSeed xor (menuId?.hashCode() ?: 0))
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(iconShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        containerColor.copy(alpha = (containerColor.alpha + 0.08f).coerceAtMost(1f)),
                        containerColor
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        ZkmIconGlyph(icon = icon, tint = iconColor, iconSize = iconSize, imagePath = menuId?.let { menuIconPaths[it] })
    }
}

@Composable
private fun HomeSectionTitle(text: String, textColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp, top = 24.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                    )
                )
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text, 
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            ),
            color = textColor
        )
    }
}

@Composable
fun generateHomeBackgroundColors(isDark: Boolean, primaryColor: Color): Pair<Color, Color> = com.zuan.kernelmanager.next.ui.components.ZkmScreenPalette.colors(isDark, primaryColor)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    navController: NavController,
    hazeState: HazeState
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val extensions by viewModel.filteredExtensions.collectAsStateWithLifecycle(initialValue = emptyList())
    
    val bgType by settingsViewModel.bgType.collectAsStateWithLifecycle()
    val bgUriString by settingsViewModel.backgroundImageUri.collectAsStateWithLifecycle()
    val isVideo by settingsViewModel.isVideoWallpaper.collectAsStateWithLifecycle()
    val isBgBlur by settingsViewModel.isBgBlur.collectAsStateWithLifecycle()
    val blurStrength by settingsViewModel.blurStrength.collectAsStateWithLifecycle()
    val bgSaturation by settingsViewModel.bgSaturation.collectAsStateWithLifecycle()
    val bgContrast by settingsViewModel.bgContrast.collectAsStateWithLifecycle()
    val weatherEffect by settingsViewModel.weatherEffect.collectAsStateWithLifecycle()
    val weatherIntensity by settingsViewModel.weatherIntensity.collectAsStateWithLifecycle()
    
    val isCardBlur by settingsViewModel.isHazeEnabled.collectAsStateWithLifecycle()
    val cardDarkness by settingsViewModel.cardDarkness.collectAsStateWithLifecycle()
    
    val isDynamic by settingsViewModel.isDynamicColor.collectAsStateWithLifecycle()
    val themeColorName by settingsViewModel.currentThemeColor.collectAsStateWithLifecycle()
    val isCustomColor by settingsViewModel.isCustomColor.collectAsStateWithLifecycle()
    val customPrimary by settingsViewModel.customPrimaryColor.collectAsStateWithLifecycle()

    val isSystemDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val isCustomBg = bgType != BgType.SYSTEM
    
    val effectivePrimary = remember(isDynamic, themeColorName, isCustomColor, customPrimary) {
        when {
            isCustomColor -> Color(customPrimary)
            isDynamic -> Color.Unspecified
            else -> themeColorName.primary
        }
    }

    val finalPrimary = if (effectivePrimary == Color.Unspecified) MaterialTheme.colorScheme.primary else effectivePrimary
    val (themeBgColor, themeCardColor) = generateHomeBackgroundColors(isSystemDark, finalPrimary)

    val targetCardColor = themeCardColor
    val targetBgColor = themeBgColor

    val shouldUseDarkText = !isSystemDark
    val customOnSurface = if (shouldUseDarkText) Color(0xFF111111) else Color(0xFFEEEEEE)
    val customOnSurfaceVariant = if (shouldUseDarkText) Color(0xFF666666) else Color(0xFFAAAAAA)

    val isGlassActive = isCardBlur && isCustomBg
    val mainBackgroundColor = if (isCustomBg) Color.Transparent else targetBgColor

    val cardColor = when {
        isGlassActive -> zkmGlassFallbackTint(zkmIsDarkTheme())
        isCustomBg -> themeCardColor.copy(alpha = cardDarkness) 
        else -> targetCardColor
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = rememberTopAppBarState(),
        snapAnimationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        flingAnimationSpec = rememberSplineBasedDecay()
    )

    val scrollState = rememberScrollState()
    val isCollapsed by remember { derivedStateOf { scrollBehavior.state.collapsedFraction > 0.7f } }

    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeightDp = with(density) { headerHeightPx.toDp() }

    val groupedExtensions = remember(extensions) { extensions.groupBy { it.type } }

    LaunchedEffect(Unit) { viewModel.loadDeviceInfo(context) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->

        Box(modifier = Modifier.fillMaxSize()) {

            // LAYER 0: BACKGROUND & WEATHER EFFECT
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(mainBackgroundColor)
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

            // LAYER 1: KONTEN UTAMA SCROLLABLE
            // Landscape / tablet: kolom kartu menyesuaikan lebar & konten dipusatkan (maks 840dp).
            val zkmWindow = LocalZkmWindow.current
            val railActive = LocalZkmRailActive.current
            val cardColumns = zkmWindow.cardColumns(
                zkmWindow.widthDp - if (railActive) ZkmRailWidth.value.toInt() else 0
            )
            val sidePadding = zkmContentHorizontalPadding(
                reservedStart = if (railActive) ZkmRailWidth else 0.dp
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .zkmHazeSource(state = hazeState, zIndex = 1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = sidePadding)
            ) {
                Spacer(modifier = Modifier.height(headerHeightDp + 8.dp))

                groupedExtensions.entries.forEachIndexed { index, (category, items) ->
                    val categoryDisplayName = when(category) {
                        "System" -> stringResource(R.string.cat_system)
                        "Monitor" -> stringResource(R.string.cat_monitor)
                        "Root" -> stringResource(R.string.cat_root)
                        else -> category
                    }
                    
                    HomeEntrance(index = index + 1) {
                        Column {
                            HomeSectionTitle(text = categoryDisplayName.uppercase(), textColor = customOnSurface)
                            
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                maxItemsInEachRow = cardColumns
                            ) {
                                items.forEach { item ->
                                    CompactFeatureCard(
                                        item = item,
                                        isGlassActive = isGlassActive,
                                        hazeState = hazeState,
                                        onClick = { navController.navigate(item.route) },
                                        modifier = Modifier.weight(1f),
                                        cardColor = cardColor,
                                        textColor = customOnSurface,
                                        subTextColor = customOnSurfaceVariant
                                    )
                                }
                                repeat((cardColumns - items.size % cardColumns) % cardColumns) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                // Saat rail aktif (bukan bottom bar melayang) -> tidak perlu ruang kosong besar.
                Spacer(modifier = Modifier.height(if (railActive) 72.dp else 120.dp))
            }

            // LAYER 2: TOP HEADER (PROGRESSIVE BLUR)
            val headerHazeStyle = zkmAppBarHazeStyle(themeBgColor, isCustomBg)

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
                        title = { AppBarContent(isCollapsed, scrollBehavior.state.collapsedFraction, customOnSurface, customOnSurfaceVariant) },
                        actions = {
                            ZkmHelpButton(NavigationRoute.Dashboard.route, tint = customOnSurface)
                        },
                        colors = TopAppBarDefaults.largeTopAppBarColors(
                            containerColor = Color.Transparent, 
                            scrolledContainerColor = Color.Transparent,
                            actionIconContentColor = customOnSurface, 
                            navigationIconContentColor = customOnSurface
                        ),
                        scrollBehavior = scrollBehavior,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Spacer Fading Buffer (Bumbu Rahasia)
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun DeviceInfoCompact(
    viewModel: HomeViewModel,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    textColor: Color,
    subTextColor: Color
) {
    val deviceInfo by viewModel.deviceInfo.collectAsStateWithLifecycle()

    ExpressiveGlassCard(
        isGlassActive = isGlassActive,
        hazeState = hazeState,
        shape = RoundedCornerShape(24.dp),
        cardColor = cardColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeIconContainer(
                icon = Icons.Rounded.Smartphone,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                size = 56.dp,
                iconSize = 28.dp,
                cornerRadius = 18.dp
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = deviceInfo.deviceName,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${deviceInfo.manufacturer} • Android ${deviceInfo.androidVersion}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = subTextColor
                )
            }
        }
    }
}

@Composable
fun CompactFeatureCard(
    item: ExtensionItem,
    isGlassActive: Boolean,
    hazeState: HazeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardColor: Color,
    textColor: Color,
    subTextColor: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(20.dp)

    val cardModifier = if (isGlassActive) {
        Modifier
            .zkmCardHazeEffect(shape = shape,
                state = hazeState,
                style = ZkmHazeStyleCache.of(
                    backgroundColor = cardColor.copy(alpha = 0.5f),
                    blurRadius = 30.dp,
                    noiseFactor = 0.08f,
                    tints = emptyList()
                )
            )
            .zkmCardBorder(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.25f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                shape = shape
            )
    } else {
        Modifier.clip(shape)
    }

    val containerColor = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else cardColor

    Surface(
        modifier = modifier
            .height(84.dp)
            .then(cardModifier)
            .homePressEffect(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = shape,
        color = containerColor,
        tonalElevation = if (isGlassActive) 0.dp else 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeIconContainer(
                icon = item.icon,
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                iconColor = MaterialTheme.colorScheme.primary,
                size = 42.dp,
                iconSize = 22.dp,
                cornerRadius = 14.dp,
                menuId = item.route
            )

            Spacer(modifier = Modifier.width(14.dp))

            val activeMode by com.zuan.kernelmanager.next.core.mode.WorkingModeManager.mode.collectAsStateWithLifecycle()
            val requiredMode = com.zuan.kernelmanager.next.core.mode.FeatureAccess.requiredMode(item.route)
            val locked = activeMode.rank < requiredMode.rank

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    text = stringResource(item.nameRes),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                    color = if (locked) textColor.copy(alpha = 0.6f) else textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(item.descriptionRes),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    color = subTextColor.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (locked) {
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = stringResource(
                            if (requiredMode.hasRoot) R.string.mode_tag_root else R.string.mode_tag_adb
                        ),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpressiveGlassCard(
    isGlassActive: Boolean,
    hazeState: HazeState,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    cardColor: Color,
    content: @Composable () -> Unit
) {
    val glassModifier = if (isGlassActive) {
        Modifier
            .zkmCardHazeEffect(shape = shape,
                state = hazeState,
                style = ZkmHazeStyleCache.of(
                    backgroundColor = cardColor.copy(alpha = 0.5f),
                    blurRadius = 30.dp,
                    noiseFactor = 0.08f,
                    tints = emptyList()
                )
            )
            .zkmCardBorder(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.25f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                shape = shape
            )
    } else {
        Modifier
    }

    val containerColor = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else cardColor

    Surface(
        modifier = modifier.then(glassModifier),
        shape = shape,
        color = containerColor,
        tonalElevation = if (isGlassActive) 0.dp else 2.dp
    ) {
        content()
    }
}

@Composable
private fun AppBarContent(
    isCollapsed: Boolean,
    scrollFraction: Float,
    textColor: Color,
    subTextColor: Color
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
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = zkmIconShape(RoundedCornerShape(12.dp)),
                    modifier = Modifier.size(38.dp)
                ) {
                    ZkmPaddedIcon(imageVector = Icons.Rounded.GridView, tint = MaterialTheme.colorScheme.onPrimaryContainer, padding = 8.dp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    stringResource(R.string.home_features),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = textColor
                )
            }
        } else {
            Column {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium.copy(color = subTextColor, fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    stringResource(R.string.home_dashboard),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = textColor
                )
            }
        }
    }
}
