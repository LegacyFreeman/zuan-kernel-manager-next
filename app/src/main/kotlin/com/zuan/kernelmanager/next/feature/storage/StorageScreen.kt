/*
 * Copyright (c) 2026 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalHazeMaterialsApi::class,
    ExperimentalFoundationApi::class
)

package com.zuan.kernelmanager.next.feature.storage

import com.zuan.kernelmanager.next.ui.adaptive.zkmLargeAppBarExpandedHeight
import android.content.Context
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.ui.navigation.NavigationRoute
import com.zuan.kernelmanager.next.feature.help.ZkmHelpButton
import com.zuan.kernelmanager.next.feature.dashboard.OverallUtils
import com.zuan.kernelmanager.next.feature.dashboard.UfsHealthInfo
import com.zuan.kernelmanager.next.feature.dashboard.components.dashboardLocalized
import com.zuan.kernelmanager.next.feature.home.generateHomeBackgroundColors
import com.zuan.kernelmanager.next.feature.home.menu.AppIcon
import com.zuan.kernelmanager.next.feature.home.menu.rememberUsageAccess
import com.zuan.kernelmanager.next.feature.settings.SettingsViewModel
import com.zuan.kernelmanager.next.library.device.storage.AppStorage
import com.zuan.kernelmanager.next.library.device.storage.DiskIoPoint
import com.zuan.kernelmanager.next.library.device.storage.FsInfo
import com.zuan.kernelmanager.next.library.device.storage.PartitionUsage
import com.zuan.kernelmanager.next.library.device.storage.StorageBreakdown
import com.zuan.kernelmanager.next.library.device.storage.StorageOverview
import com.zuan.kernelmanager.next.library.device.storage.StorageReader
import com.zuan.kernelmanager.next.library.device.usage.UsageStatsHelper
import com.zuan.kernelmanager.next.ui.components.zkmAppBarHazeStyle
import com.zuan.kernelmanager.next.ui.components.zkmGlassCard
import com.zuan.kernelmanager.next.ui.components.zkmGlassTint
import com.zuan.kernelmanager.next.ui.theme.zkmIconShape
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max

// ---------------------------------------------------------------------------------------------
// Model & tema lokal layar
// ---------------------------------------------------------------------------------------------

private enum class HeroStyle(val key: String, val labelRes: Int, val icon: ImageVector) {
    BAR("bar", R.string.storage_page_style_bar, Icons.Rounded.LinearScale),
    RING("ring", R.string.storage_page_style_ring, Icons.Rounded.DonutLarge),
    TANK("tank", R.string.storage_page_style_tank, Icons.Rounded.ViewColumn)
}

private data class StorageUiState(
    val overview: StorageOverview? = null,
    val breakdown: StorageBreakdown? = null,
    val partitions: List<PartitionUsage> = emptyList(),
    val fs: FsInfo? = null,
    val lifetimeWrites: Long? = null,
    val ufs: UfsHealthInfo? = null,
    val pressure: Float = 0f,
    val topApps: List<AppStorage> = emptyList()
)

private class Segment(val label: String, val bytes: Long, val color: Color, val icon: ImageVector)

/**
 * Semua warna / glass / bentuk kartu diturunkan dari Wallpaper Style (lewat SettingsViewModel),
 * lalu dibagikan ke seluruh komponen lewat CompositionLocal ini.
 */
private class StorageTheme(
    val isGlass: Boolean,
    val hazeState: HazeState,
    val shape: Shape,
    val tint: Color,
    val text: Color,
    val sub: Color,
    val accent: Color,
    val palette: List<Color>
)

private val LocalStorageTheme = staticCompositionLocalOf<StorageTheme> {
    error("StorageTheme not provided")
}

private fun buildPalette(base: Color): List<Color> {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(base.toArgb(), hsv)
    val s = hsv[1].coerceIn(0.5f, 0.85f)
    val v = hsv[2].coerceIn(0.78f, 0.95f)
    return List(6) { i -> Color.hsv((hsv[0] + i * 52f) % 360f, s, v) }
}

private fun fileSize(context: Context, bytes: Long): String =
    Formatter.formatFileSize(context, bytes.coerceAtLeast(0L))

private fun formatRate(bps: Float): String = when {
    bps >= 1_000_000f -> String.format(Locale.US, "%.1f MB/s", bps / 1_000_000f)
    bps >= 1_000f -> String.format(Locale.US, "%.0f KB/s", bps / 1_000f)
    else -> "0 KB/s"
}

// ---------------------------------------------------------------------------------------------
// Layar
// ---------------------------------------------------------------------------------------------

@Composable
fun StorageScreen(
    navController: NavController,
    hazeState: HazeState,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // --- Pengaturan dari Wallpaper Style -------------------------------------------------
    val isCustomBg by settingsViewModel.isCustomBackground.collectAsStateWithLifecycle()
    val isCardBlur by settingsViewModel.isHazeEnabled.collectAsStateWithLifecycle()
    val cardDarkness by settingsViewModel.cardDarkness.collectAsStateWithLifecycle()
    val isDynamic by settingsViewModel.isDynamicColor.collectAsStateWithLifecycle()
    val themeColorName by settingsViewModel.currentThemeColor.collectAsStateWithLifecycle()
    val isCustomColor by settingsViewModel.isCustomColor.collectAsStateWithLifecycle()
    val customPrimary by settingsViewModel.customPrimaryColor.collectAsStateWithLifecycle()

    val isDark = zkmIsDarkTheme()
    val effectivePrimary = remember(isDynamic, themeColorName, isCustomColor, customPrimary) {
        when {
            isCustomColor -> Color(customPrimary)
            isDynamic -> Color.Unspecified
            else -> themeColorName.primary
        }
    }
    val finalPrimary =
        if (effectivePrimary == Color.Unspecified) MaterialTheme.colorScheme.primary else effectivePrimary
    val (themeBgColor, themeCardColor) = generateHomeBackgroundColors(isDark, finalPrimary)

    val isGlassActive = isCardBlur && isCustomBg
    val textColor = if (isDark) Color(0xFFEEEEEE) else Color(0xFF111111)
    val subColor = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666666)
    val cardTint = when {
        isGlassActive -> zkmGlassTint(finalPrimary, isDark, 0.07f)
        isCustomBg -> themeCardColor.copy(alpha = cardDarkness)
        else -> themeCardColor
    }
    val palette = remember(finalPrimary) { buildPalette(finalPrimary) }
    val theme = remember(isGlassActive, hazeState, cardTint, textColor, subColor, finalPrimary, palette) {
        StorageTheme(
            isGlass = isGlassActive,
            hazeState = hazeState,
            shape = RoundedCornerShape(28.dp),
            tint = cardTint,
            text = textColor,
            sub = subColor,
            accent = finalPrimary,
            palette = palette
        )
    }

    // --- Preferensi lokal halaman (gaya hero) ----------------------------------------------
    val uiPrefs = remember { context.getSharedPreferences("zkm_storage_ui", Context.MODE_PRIVATE) }
    var heroStyle by remember {
        mutableStateOf(
            HeroStyle.entries.firstOrNull { it.key == uiPrefs.getString("hero_style", null) } ?: HeroStyle.BAR
        )
    }

    // --- Data ----------------------------------------------------------------------------
    val hasAccess = rememberUsageAccess()
    var refreshKey by remember { mutableIntStateOf(0) }
    var ui by remember { mutableStateOf(StorageUiState()) }

    LaunchedEffect(refreshKey, hasAccess) {
        val overview = withContext(Dispatchers.IO) { StorageReader.readOverview(context) }
        val partitions = withContext(Dispatchers.IO) { StorageReader.readPartitions() }
        val fs = withContext(Dispatchers.IO) { StorageReader.readFsInfo() }
        ui = ui.copy(overview = overview, partitions = partitions, fs = fs)

        val breakdownJob = async(Dispatchers.IO) {
            if (hasAccess) StorageReader.readBreakdown(context, overview) else null
        }
        val ufsJob = async(Dispatchers.IO) { runCatching { OverallUtils.getUfsHealth() }.getOrNull() }
        val pressureJob = async(Dispatchers.IO) {
            runCatching { OverallUtils.getStorageIntelligence().inodePercent }.getOrDefault(0f)
        }
        val writesJob = async(Dispatchers.IO) { StorageReader.readLifetimeWrites() }
        ui = ui.copy(
            breakdown = breakdownJob.await(),
            ufs = ufsJob.await(),
            pressure = pressureJob.await(),
            lifetimeWrites = writesJob.await()
        )

        val apps = withContext(Dispatchers.IO) {
            if (hasAccess) StorageReader.readTopApps(context, 8, force = refreshKey > 0) else emptyList()
        }
        ui = ui.copy(topApps = apps)
    }

    val ioHistory = remember { mutableStateListOf<DiskIoPoint>() }
    LaunchedEffect(Unit) {
        val sampler = StorageReader.DiskIoSampler()
        while (true) {
            val point = withContext(Dispatchers.IO) { sampler.sample() }
            if (point != null) {
                ioHistory.add(point)
                if (ioHistory.size > 60) ioHistory.removeAt(0)
            }
            delay(1000)
        }
    }

    // --- Header ----------------------------------------------------------------------------
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val isCollapsed by remember { derivedStateOf { scrollBehavior.state.collapsedFraction > 0.6f } }
    val scrollState = rememberScrollState()
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeightDp = with(density) { headerHeightPx.toDp() }
    val headerHazeStyle = zkmAppBarHazeStyle(themeBgColor, isCustomBg)
    val headerOnSurface = textColor
    val isBuggyAndroid12 =
        Build.VERSION.SDK_INT == Build.VERSION_CODES.S || Build.VERSION.SDK_INT == Build.VERSION_CODES.S_V2

    val segments = buildSegments(ui, palette)

    CompositionLocalProvider(LocalStorageTheme provides theme) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { _ ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Wallpaper dirender MainActivity; tanpa wallpaper pakai warna tema.
                    .background(if (isCustomBg) Color.Transparent else themeBgColor)
            ) {
                // LAYER 1: konten (jadi sumber blur untuk app bar progresif)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(state = hazeState, zIndex = 1f)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Spacer(Modifier.height(headerHeightDp))

                    StorageEntrance(0) {
                        HeroCard(
                            ui = ui,
                            segments = segments,
                            style = heroStyle,
                            onStyle = {
                                heroStyle = it
                                uiPrefs.edit().putString("hero_style", it.key).apply()
                            }
                        )
                    }
                    if (!hasAccess) {
                        StorageEntrance(1) { AccessCard(onGrantRoot = { scope.launch(Dispatchers.IO) { UsageStatsHelper.grantWithRoot(context) } }) }
                    }
                    StorageEntrance(2) { HealthCard(ui) }
                    StorageEntrance(3) { IoCard(ioHistory) }
                    if (ui.partitions.isNotEmpty()) {
                        StorageEntrance(4) { PartitionsCard(ui.partitions) }
                    }
                    if (hasAccess && ui.topApps.isNotEmpty()) {
                        StorageEntrance(5) { TopAppsCard(ui.topApps) }
                    }
                    Spacer(Modifier.height(120.dp))
                }

                // LAYER 2: app bar dengan progressive blur (Appbar Banner Progressive khusus layar Overall)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .onGloballyPositioned { headerHeightPx = it.size.height }
                        .hazeEffect(state = hazeState, style = headerHazeStyle) {
                            progressive = HazeProgressive.verticalGradient(
                                startIntensity = 1f,
                                endIntensity = 0f
                            )
                            if (isBuggyAndroid12) forceInvalidateOnPreDraw = true
                        }
                        .zIndex(2f)
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        LargeTopAppBar(
                            expandedHeight = zkmLargeAppBarExpandedHeight(),
                            title = {
                                Text(
                                    text = stringResource(R.string.ext_storage),
                                    style = if (isCollapsed) {
                                        MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                    } else {
                                        MaterialTheme.typography.headlineLarge.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = (-0.5).sp
                                        )
                                    },
                                    color = headerOnSurface
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = stringResource(R.string.lp_a11y_back),
                                        tint = headerOnSurface
                                    )
                                }
                            },
                            actions = {
                                IconButton(onClick = { refreshKey++ }) {
                                    Icon(
                                        Icons.Rounded.Refresh,
                                        contentDescription = stringResource(R.string.storage_page_refresh),
                                        tint = headerOnSurface
                                    )
                                }
                                ZkmHelpButton(NavigationRoute.Storage.route, tint = headerOnSurface)
                            },
                            colors = TopAppBarDefaults.largeTopAppBarColors(
                                containerColor = Color.Transparent,
                                scrolledContainerColor = Color.Transparent
                            ),
                            scrollBehavior = scrollBehavior,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun buildSegments(ui: StorageUiState, palette: List<Color>): List<Segment> {
    val overview = ui.overview ?: return emptyList()
    val b = ui.breakdown
    if (b == null) {
        return listOf(
            Segment(stringResource(R.string.storage_page_cat_used), overview.usedBytes, palette[0], Icons.Rounded.Storage)
        )
    }
    return listOf(
        Segment(stringResource(R.string.storage_page_cat_apps), b.apps, palette[0], Icons.Rounded.Apps),
        Segment(stringResource(R.string.storage_page_cat_images), b.images, palette[1], Icons.Rounded.Image),
        Segment(stringResource(R.string.storage_page_cat_videos), b.videos, palette[2], Icons.Rounded.VideoLibrary),
        Segment(stringResource(R.string.storage_page_cat_audio), b.audio, palette[3], Icons.Rounded.MusicNote),
        Segment(stringResource(R.string.storage_page_cat_docs), b.documentsOther, palette[4], Icons.Rounded.Description),
        Segment(stringResource(R.string.storage_page_cat_system), b.system, palette[5], Icons.Rounded.Android)
    ).filter { it.bytes > 0L }
}

// ---------------------------------------------------------------------------------------------
// Komponen dasar
// ---------------------------------------------------------------------------------------------

@Composable
private fun StorageEntrance(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(60L * index)
        visible = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(380, easing = LinearOutSlowInEasing),
        label = "storageEntranceAlpha"
    )
    val translate by animateDpAsState(
        targetValue = if (visible) 0.dp else 22.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow),
        label = "storageEntranceY"
    )
    Box(
        modifier = Modifier.graphicsLayer {
            this.alpha = alpha
            translationY = translate.toPx()
        }
    ) { content() }
}

@Composable
private fun StorageCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val t = LocalStorageTheme.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .zkmGlassCard(t.isGlass, t.hazeState, t.shape, t.tint)
            .padding(20.dp),
        content = content
    )
}

@Composable
private fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = LocalStorageTheme.current.accent, size: Dp = 40.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(zkmIconShape(RoundedCornerShape(14.dp)))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
private fun CardHeader(icon: ImageVector, title: String, trailing: (@Composable () -> Unit)? = null) {
    val t = LocalStorageTheme.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon)
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = t.text,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        trailing?.invoke()
    }
}

@Composable
private fun ThinBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val t = LocalStorageTheme.current
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "thinBar")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(t.sub.copy(alpha = 0.16f))
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(f)
                .clip(RoundedCornerShape(height / 2))
                .background(color)
        )
    }
}

@Composable
private fun TileGrid(items: List<Pair<String, String>>) {
    val t = LocalStorageTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowItems.forEach { (label, value) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(t.sub.copy(alpha = 0.09f))
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = t.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            value,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = t.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Hero (3 gaya: Bar / Ring / Tank)
// ---------------------------------------------------------------------------------------------

@Composable
private fun HeroCard(
    ui: StorageUiState,
    segments: List<Segment>,
    style: HeroStyle,
    onStyle: (HeroStyle) -> Unit
) {
    val t = LocalStorageTheme.current
    val context = LocalContext.current
    val overview = ui.overview
    StorageCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Storage)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.ext_storage),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = t.text
                )
                if (overview != null) {
                    Text(
                        stringResource(
                            R.string.storage_page_of_total,
                            fileSize(context, overview.usedBytes),
                            fileSize(context, overview.totalBytes)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = t.sub
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        StyleSelector(style, onStyle)
        Spacer(Modifier.height(18.dp))

        if (overview == null) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = t.accent)
            }
        } else {
            when (style) {
                HeroStyle.BAR -> BarHero(overview, segments)
                HeroStyle.RING -> RingHero(overview, segments)
                HeroStyle.TANK -> TankHero(overview, segments)
            }
            Spacer(Modifier.height(18.dp))
            SegmentLegend(overview, segments)
        }
    }
}

@Composable
private fun StyleSelector(current: HeroStyle, onSelect: (HeroStyle) -> Unit) {
    val t = LocalStorageTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(t.sub.copy(alpha = 0.10f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        HeroStyle.entries.forEach { s ->
            val selected = s == current
            val bg by animateColorAsState(
                if (selected) t.accent.copy(alpha = 0.22f) else Color.Transparent,
                label = "styleSel"
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .clickable { onSelect(s) }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(s.icon, contentDescription = null, tint = if (selected) t.accent else t.sub, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(s.labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) t.accent else t.sub
                )
            }
        }
    }
}

@Composable
private fun rememberAppear(): Animatable<Float, *> {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    return appear
}

@Composable
private fun BarHero(overview: StorageOverview, segments: List<Segment>) {
    val t = LocalStorageTheme.current
    val context = LocalContext.current
    val appear = rememberAppear()
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            String.format(Locale.US, "%.0f", overview.usedFraction * 100f),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold,
            color = t.text
        )
        Text(
            "%",
            style = MaterialTheme.typography.titleLarge,
            color = t.sub,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.storage_page_used),
            style = MaterialTheme.typography.titleMedium,
            color = t.sub,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(R.string.storage_page_free_value, fileSize(context, overview.freeBytes)),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = t.accent,
            modifier = Modifier.padding(bottom = 6.dp)
        )
    }
    Spacer(Modifier.height(14.dp))
    val track = t.sub.copy(alpha = 0.18f)
    val barHeight = 16.dp
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(RoundedCornerShape(barHeight / 2))
    ) {
        drawRect(track)
        val total = overview.totalBytes.coerceAtLeast(1L).toFloat()
        val gap = 2.dp.toPx()
        var x = 0f
        segments.forEach { s ->
            val w = (s.bytes / total) * size.width * appear.value
            if (w > 0f) {
                drawRect(s.color, topLeft = Offset(x, 0f), size = Size((w - gap).coerceAtLeast(1f), size.height))
                x += w
            }
        }
    }
}

@Composable
private fun RingHero(overview: StorageOverview, segments: List<Segment>) {
    val t = LocalStorageTheme.current
    val context = LocalContext.current
    val appear = rememberAppear()
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 22.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val start = 135f
                val sweepTotal = 270f
                drawArc(
                    color = t.sub.copy(alpha = 0.16f),
                    startAngle = start,
                    sweepAngle = sweepTotal,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
                val total = overview.totalBytes.coerceAtLeast(1L).toFloat()
                var angle = start
                segments.forEach { s ->
                    val sweep = (s.bytes / total) * sweepTotal * appear.value
                    if (sweep > 0.5f) {
                        drawArc(
                            color = s.color,
                            startAngle = angle,
                            sweepAngle = (sweep - 1.5f).coerceAtLeast(0.5f),
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Butt)
                        )
                        angle += sweep
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    fileSize(context, overview.freeBytes),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = t.text
                )
                Text(stringResource(R.string.storage_page_free), style = MaterialTheme.typography.bodyMedium, color = t.sub)
                Spacer(Modifier.height(6.dp))
                Text(
                    String.format(Locale.US, "%.0f%% ", overview.usedFraction * 100f) + stringResource(R.string.storage_page_used),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = t.accent
                )
            }
        }
    }
}

@Composable
private fun TankHero(overview: StorageOverview, segments: List<Segment>) {
    val t = LocalStorageTheme.current
    val context = LocalContext.current
    val appear = rememberAppear()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        Canvas(Modifier.width(112.dp).height(190.dp)) {
            val w = size.width
            val capH = 26.dp.toPx()
            val bodyTop = capH / 2f
            val bodyBottom = size.height - capH / 2f
            val bodyH = bodyBottom - bodyTop
            val track = t.sub.copy(alpha = 0.16f)

            // Tabung kosong (sisa = free)
            drawRect(track, topLeft = Offset(0f, bodyTop), size = Size(w, bodyH))
            drawOval(track, topLeft = Offset(0f, bodyBottom - capH / 2f), size = Size(w, capH))

            val stacked = segments.reversed() // system di dasar, apps di puncak
            stacked.firstOrNull()?.let {
                drawOval(it.color, topLeft = Offset(0f, bodyBottom - capH / 2f), size = Size(w, capH))
            }
            val total = overview.totalBytes.coerceAtLeast(1L).toFloat()
            var y = bodyBottom
            stacked.forEach { s ->
                val h = (s.bytes / total) * bodyH * appear.value
                if (h > 0f) {
                    drawRect(s.color, topLeft = Offset(0f, y - h), size = Size(w, h))
                    drawOval(
                        lerp(s.color, Color.White, 0.2f),
                        topLeft = Offset(0f, y - h - capH / 2f),
                        size = Size(w, capH)
                    )
                    y -= h
                }
            }
            // Tutup atas tabung
            drawOval(track.copy(alpha = 0.28f), topLeft = Offset(0f, 0f), size = Size(w, capH))
        }
        Column(Modifier.weight(1f)) {
            Text(
                String.format(Locale.US, "%.0f%%", overview.usedFraction * 100f),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = t.text
            )
            Text(stringResource(R.string.storage_page_used), style = MaterialTheme.typography.titleMedium, color = t.sub)
            Spacer(Modifier.height(10.dp))
            Text(
                fileSize(context, overview.usedBytes),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = t.accent
            )
            Text(
                stringResource(R.string.storage_page_free_value, fileSize(context, overview.freeBytes)),
                style = MaterialTheme.typography.bodyMedium,
                color = t.sub
            )
        }
    }
}

@Composable
private fun SegmentLegend(overview: StorageOverview, segments: List<Segment>) {
    val t = LocalStorageTheme.current
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        segments.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(s.icon, tint = s.color, size = 32.dp)
                Spacer(Modifier.width(12.dp))
                Text(s.label, style = MaterialTheme.typography.bodyLarge, color = t.text, modifier = Modifier.weight(1f))
                Text(
                    fileSize(context, s.bytes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = t.text
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Storage, tint = t.sub, size = 32.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.storage_page_free),
                style = MaterialTheme.typography.bodyLarge,
                color = t.sub,
                modifier = Modifier.weight(1f)
            )
            Text(
                fileSize(context, overview.freeBytes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = t.sub
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Kartu-kartu lain
// ---------------------------------------------------------------------------------------------

@Composable
private fun AccessCard(onGrantRoot: () -> Unit) {
    val t = LocalStorageTheme.current
    val context = LocalContext.current
    StorageCard {
        CardHeader(Icons.Rounded.Lock, stringResource(R.string.usage_access_title))
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.storage_page_access_desc), style = MaterialTheme.typography.bodyMedium, color = t.sub)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { UsageStatsHelper.openAccessSettings(context) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.usage_access_open_settings))
            }
            OutlinedButton(onClick = onGrantRoot, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.usage_access_grant_root))
            }
        }
    }
}

@Composable
private fun HealthCard(ui: StorageUiState) {
    val t = LocalStorageTheme.current
    val cs = MaterialTheme.colorScheme
    val ufs = ui.ufs
    val supported = ufs != null && ufs.healthSupported && ufs.healthPercent != null
    val statusColor = when {
        !supported -> t.sub
        ufs?.status == "HEALTHY" -> cs.primary
        ufs?.status == "CAUTION" -> cs.tertiary
        else -> cs.error
    }
    val title = if (ufs?.storageType == "UFS") {
        stringResource(R.string.lp_overall_ufs_health)
    } else {
        stringResource(R.string.lp_overall_storage_health)
    }
    val dash = "—"
    val typeText = when {
        ufs == null || ufs.storageType == "Unknown" -> stringResource(R.string.lp_overall_unknown_upper)
        else -> ufs.storageType
    }
    val unknown = stringResource(R.string.lp_overall_unknown)
    val tempText = ufs?.temperatureC?.let { String.format(Locale.US, "%.1f °C", it) } ?: dash
    val context = LocalContext.current

    StorageCard {
        CardHeader(
            icon = Icons.Rounded.HealthAndSafety,
            title = title,
            trailing = {
                Text(
                    typeText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(statusColor.copy(alpha = 0.12f))
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                )
            }
        )
        Spacer(Modifier.height(14.dp))

        if (ufs == null) {
            Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = t.accent, modifier = Modifier.size(28.dp))
            }
        } else if (supported) {
            val pct = ufs.healthPercent ?: 0
            val bucket = if (pct in 5..95) "  (${pct - 5}–${pct + 5}%)" else ""
            Text("$pct%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = t.text)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.lp_overall_ufs_estimated_health) + bucket,
                style = MaterialTheme.typography.bodyMedium,
                color = t.sub
            )
            Spacer(Modifier.height(12.dp))
            ThinBar(pct / 100f, statusColor, height = 9.dp)
        } else {
            Text(unknown, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = t.text)
            Spacer(Modifier.height(5.dp))
            val detail = when (ufs.storageType) {
                "eMMC" -> stringResource(R.string.lp_overall_ufs_emmc_detected)
                "UFS" -> stringResource(R.string.lp_overall_ufs_kernel_descriptor)
                else -> stringResource(R.string.lp_overall_storage_descriptor_unknown)
            }
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = t.sub)
        }

        if (ufs != null) {
            Spacer(Modifier.height(14.dp))
            val tiles = ArrayList<Pair<String, String>>()
            if (supported) {
                tiles += stringResource(R.string.lp_overall_lifetime_a) to ufs.lifetimeA
                tiles += stringResource(R.string.lp_overall_lifetime_b) to ufs.lifetimeB
                tiles += stringResource(R.string.lp_overall_health_state) to dashboardLocalized(ufs.status)
            }
            tiles += stringResource(R.string.lp_overall_storage_temperature) to tempText
            ui.fs?.let { fs ->
                val extra = fs.options.take(2).joinToString(", ")
                tiles += stringResource(R.string.storage_page_filesystem) to
                    (fs.type + if (extra.isNotEmpty()) " ($extra)" else "")
            }
            ui.lifetimeWrites?.let {
                tiles += stringResource(R.string.storage_page_lifetime_writes) to fileSize(context, it)
            }
            tiles += stringResource(R.string.lp_overall_filesystem_block_pressure) to
                String.format(Locale.US, "%.0f%%", ui.pressure * 100f)
            ui.breakdown?.let {
                tiles += stringResource(R.string.storage_page_app_cache) to fileSize(context, it.cache)
            }
            TileGrid(tiles)
        }
    }
}

@Composable
private fun IoCard(history: List<DiskIoPoint>) {
    val t = LocalStorageTheme.current
    val cs = MaterialTheme.colorScheme
    val readColor = cs.primary
    val writeColor = cs.tertiary
    StorageCard {
        CardHeader(Icons.Rounded.Speed, stringResource(R.string.storage_page_io_title))
        Spacer(Modifier.height(14.dp))
        if (history.isEmpty()) {
            Text(stringResource(R.string.storage_page_io_unavailable), style = MaterialTheme.typography.bodyMedium, color = t.sub)
        } else {
            val last = history.last()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RateBlock(stringResource(R.string.storage_page_io_read), formatRate(last.readBps), readColor, Modifier.weight(1f))
                RateBlock(stringResource(R.string.storage_page_io_write), formatRate(last.writeBps), writeColor, Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            val peak = max(1_000_000f, history.maxOf { max(it.readBps, it.writeBps) })
            Text(
                "max " + formatRate(peak),
                style = MaterialTheme.typography.labelSmall,
                color = t.sub
            )
            Spacer(Modifier.height(4.dp))
            Canvas(Modifier.fillMaxWidth().height(90.dp)) {
                val grid = t.sub.copy(alpha = 0.15f)
                for (i in 0..2) {
                    val y = size.height * i / 2f
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }
                fun line(selector: (DiskIoPoint) -> Float): Path {
                    val p = Path()
                    history.forEachIndexed { i, pt ->
                        val x = if (history.size > 1) size.width * i / (history.size - 1).toFloat() else 0f
                        val y = size.height - (selector(pt) / peak).coerceIn(0f, 1f) * size.height
                        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                    }
                    return p
                }
                val readLine = line { it.readBps }
                val fill = Path().apply {
                    addPath(readLine)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(
                    fill,
                    Brush.verticalGradient(listOf(readColor.copy(alpha = 0.28f), Color.Transparent))
                )
                drawPath(readLine, readColor, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                drawPath(line { it.writeBps }, writeColor, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
private fun RateBlock(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val t = LocalStorageTheme.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = t.sub)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
    }
}

@Composable
private fun PartitionsCard(partitions: List<PartitionUsage>) {
    val t = LocalStorageTheme.current
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    StorageCard {
        CardHeader(Icons.Rounded.Dns, stringResource(R.string.storage_page_partitions))
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            partitions.forEach { p ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            p.mount,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = t.text,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            fileSize(context, p.usedBytes) + " / " + fileSize(context, p.totalBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = t.sub
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    ThinBar(p.usedFraction, if (p.usedFraction > 0.9f) cs.error else t.accent)
                }
            }
        }
    }
}

@Composable
private fun TopAppsCard(apps: List<AppStorage>) {
    val t = LocalStorageTheme.current
    val context = LocalContext.current
    val maxBytes = apps.maxOfOrNull { it.bytes }?.coerceAtLeast(1L) ?: 1L
    StorageCard {
        CardHeader(Icons.Rounded.Apps, stringResource(R.string.storage_page_top_apps))
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            apps.forEach { app ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app.packageName, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            app.label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = t.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(5.dp))
                        ThinBar(app.bytes.toFloat() / maxBytes, t.accent, height = 6.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        fileSize(context, app.bytes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = t.text
                    )
                }
            }
        }
    }
}
