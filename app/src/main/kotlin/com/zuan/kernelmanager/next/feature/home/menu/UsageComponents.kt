/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package com.zuan.kernelmanager.next.feature.home.menu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavController
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.library.device.usage.UsageStatsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import android.os.Build
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zuan.kernelmanager.next.feature.settings.BgType
import com.zuan.kernelmanager.next.feature.settings.SettingsViewModel
import com.zuan.kernelmanager.next.ui.components.ZkmScreenPalette
import com.zuan.kernelmanager.next.ui.components.zkmAppBarHazeStyle
import com.zuan.kernelmanager.next.ui.components.zkmCustomBackgroundCardColor
import com.zuan.kernelmanager.next.ui.components.zkmGlassFallbackTint
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundContentColor
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundSubContentColor
import com.zuan.kernelmanager.next.ui.components.zkmReferenceGlass
import com.zuan.kernelmanager.next.ui.components.zkmReferenceGlassFill
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import com.zuan.kernelmanager.next.ui.components.zkmHazeEffect
import com.zuan.kernelmanager.next.ui.components.zkmHazeSource

/**
 * Komponen bersama untuk layar Home baru: Screen Time, Battery Usage, Charge Stats.
 */

/**
 * Warna & status glass layar Usage (Screen Time, Battery Usage, Charge Stats). Diturunkan dari
 * Wallpaper Style Screen dengan resep yang SAMA dengan SetEdit / Home, lalu dibagikan ke semua
 * komponen lewat [LocalUsageColors].
 */
@Immutable
class UsageColors(
    val isGlass: Boolean,
    val isCustomBg: Boolean,
    val hazeState: HazeState,
    val cardColor: Color,
    val text: Color,
    val sub: Color,
    val track: Color,
)

val LocalUsageColors = staticCompositionLocalOf<UsageColors?> { null }

@Composable
fun usageSubColor(): Color = LocalUsageColors.current?.sub ?: MaterialTheme.colorScheme.onSurfaceVariant

@Composable
fun usageTrackColor(): Color = LocalUsageColors.current?.track ?: MaterialTheme.colorScheme.surfaceVariant

@Composable
fun UsageScreenScaffold(
    title: String,
    navController: NavController,
    hazeState: HazeState,
    helpRoute: String? = null,
    settingsViewModel: SettingsViewModel = viewModel(),
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalDensity.current

    // --- Pengaturan dari Wallpaper Style (sama dengan SetEdit) ---------------------------
    val bgType by settingsViewModel.bgType.collectAsStateWithLifecycle()
    val isHazeEnabled by settingsViewModel.isHazeEnabled.collectAsStateWithLifecycle()
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
    val (themeBgColor, themeCardColor) = ZkmScreenPalette.colors(isDark, finalPrimary)

    val isCustomBg = bgType != BgType.SYSTEM
    val isGlassActive = isHazeEnabled && isCustomBg
    val cardColor = when {
        isGlassActive -> zkmGlassFallbackTint(isDark)
        isCustomBg -> zkmCustomBackgroundCardColor(isDark, cardDarkness)
        else -> themeCardColor
    }
    val textColor = if (isCustomBg) zkmOnCustomBackgroundContentColor(isDark) else ZkmScreenPalette.text(isDark)
    val subColor = if (isCustomBg) zkmOnCustomBackgroundSubContentColor(isDark) else ZkmScreenPalette.subText(isDark)
    val trackColor = textColor.copy(alpha = 0.14f)

    val colors = remember(isGlassActive, isCustomBg, hazeState, cardColor, textColor, subColor, trackColor) {
        UsageColors(isGlassActive, isCustomBg, hazeState, cardColor, textColor, subColor, trackColor)
    }

    // --- Header: app bar progressive blur (TANPA Appbar Banner; banner khusus Overall) -------
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeightDp = with(density) { headerHeightPx.toDp() }
    val headerHazeStyle = zkmAppBarHazeStyle(themeBgColor, isCustomBg)
    val isBuggyAndroid12 =
        Build.VERSION.SDK_INT == Build.VERSION_CODES.S || Build.VERSION.SDK_INT == Build.VERSION_CODES.S_V2

    CompositionLocalProvider(
        LocalUsageColors provides colors,
        LocalContentColor provides textColor,
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { _ ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Wallpaper dirender MainActivity; tanpa wallpaper pakai warna tema.
                    .background(if (isCustomBg) Color.Transparent else themeBgColor)
            ) {
                // LAYER 1: konten (sekaligus sumber blur untuk app bar progresif)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .zkmHazeSource(state = hazeState, zIndex = 1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Spacer(Modifier.height(headerHeightDp))
                    content()
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // LAYER 2: app bar + progressive blur
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .onGloballyPositioned { headerHeightPx = it.size.height }
                        .zkmHazeEffect(state = hazeState, style = headerHazeStyle) {
                            progressive = HazeProgressive.verticalGradient(
                                startIntensity = 1f,
                                endIntensity = 0f
                            )
                            if (isBuggyAndroid12) forceInvalidateOnPreDraw = true
                        }
                        .zIndex(2f)
                ) {
                    TopAppBar(
                        title = { Text(title, fontWeight = FontWeight.Bold, color = textColor) },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(R.string.lp_a11y_back),
                                    tint = textColor
                                )
                            }
                        },
                        actions = {
                            actions()
                            if (helpRoute != null) {
                                com.zuan.kernelmanager.next.feature.help.ZkmHelpButton(helpRoute, tint = textColor)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                            titleContentColor = textColor,
                            navigationIconContentColor = textColor,
                            actionIconContentColor = textColor,
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun UsageCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val u = LocalUsageColors.current
    val shape = RoundedCornerShape(24.dp)
    if (u == null) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(modifier = Modifier.padding(16.dp), content = content)
        }
        return
    }
    // Kartu referensi SetEdit: resep haze/isi/border ada di ZkmReferenceGlass.kt.
    val glassModifier = if (u.isGlass) Modifier.zkmReferenceGlass(u.hazeState, shape) else Modifier.clip(shape)
    Card(
        modifier = modifier.fillMaxWidth().then(glassModifier),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (u.isGlass) zkmReferenceGlassFill() else u.cardColor,
            contentColor = u.text
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

/** "6h 34m", "41m", "<1m". */
fun formatUsageDuration(ms: Long): String {
    val totalMin = ms / 60_000L
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m"
        else -> "<1m"
    }
}

/** Izin Usage access: dipantau tiap detik supaya UI langsung berubah setelah user kembali dari Settings. */
@Composable
fun rememberUsageAccess(): Boolean {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(UsageStatsHelper.hasAccess(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            granted = UsageStatsHelper.hasAccess(context)
            delay(1000)
        }
    }
    return granted
}

@Composable
fun UsageAccessCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    UsageCard {
        Text(
            stringResource(R.string.usage_access_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.usage_access_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = usageSubColor()
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { UsageStatsHelper.openAccessSettings(context) },
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.usage_access_open_settings)) }
            OutlinedButton(
                onClick = { scope.launch(Dispatchers.IO) { UsageStatsHelper.grantWithRoot(context) } },
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.usage_access_grant_root)) }
        }
    }
}

@Composable
fun AppIcon(pkg: String, size: Dp = 40.dp) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, pkg) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(10.dp)
    val icon = bitmap
    if (icon != null) {
        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(size).clip(shape))
    } else {
        Box(modifier = Modifier.size(size).clip(shape).background(usageTrackColor()))
    }
}

@Composable
fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = usageSubColor(),
            maxLines = 1
        )
    }
}

/**
 * Grafik garis sederhana. [points] = (x, y) dalam satuan data; sumbu dipetakan dari
 * [xMin]..[xMax] dan [yMin]..[yMax]. [yLabels] dari atas ke bawah, [xLabels] dari kiri ke kanan.
 */
@Composable
fun SimpleLineChart(
    points: List<Pair<Float, Float>>,
    xMin: Float,
    xMax: Float,
    yMin: Float,
    yMax: Float,
    yLabels: List<String>,
    xLabels: List<String>,
    lineColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp
) {
    val grid = usageTrackColor()
    val labelColor = usageSubColor()
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().height(height)) {
            Column(
                modifier = Modifier.width(36.dp).fillMaxHeight().padding(end = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                yLabels.forEach { Text(it, fontSize = 10.sp, color = labelColor, maxLines = 1) }
            }
            Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val lines = yLabels.size.coerceAtLeast(2)
                for (i in 0 until lines) {
                    val y = size.height * i / (lines - 1)
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }
                if (points.size >= 2) {
                    val xRange = (xMax - xMin).coerceAtLeast(1e-6f)
                    val yRange = (yMax - yMin).coerceAtLeast(1e-6f)
                    val path = Path()
                    points.forEachIndexed { i, p ->
                        val px = ((p.first - xMin) / xRange).coerceIn(0f, 1f) * size.width
                        val py = size.height - ((p.second - yMin) / yRange).coerceIn(0f, 1f) * size.height
                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 36.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            xLabels.forEach { Text(it, fontSize = 10.sp, color = labelColor, maxLines = 1) }
        }
    }
}

/** Label sumbu waktu: 5 titik dari 0 sampai [durationMs]. */
fun timeAxisLabels(durationMs: Long): List<String> =
    (0..4).map { i ->
        val ms = durationMs * i / 4
        val min = ms / 60_000L
        when {
            i == 0 -> "0"
            min >= 120 -> String.format(Locale.US, "%.1fh", ms / 3_600_000f)
            else -> "${min}m"
        }
    }
