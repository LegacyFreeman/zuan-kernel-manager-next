/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalHazeMaterialsApi::class
)

package com.zuan.kernelmanager.next.feature.dashboard.components

import com.zuan.kernelmanager.next.ui.components.zkmInfiniteFloat
import android.graphics.Matrix
import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.zuan.kernelmanager.next.ui.components.ZkmDialog
import com.zuan.kernelmanager.next.ui.components.ZkmDialogPanel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.ui.components.DialogTextButton
import com.zuan.kernelmanager.next.ui.components.DialogUnstyled
import com.zuan.kernelmanager.next.ui.components.battery.BatteryWeatherCard
import com.zuan.kernelmanager.next.feature.dashboard.CoreDetailInfo
import com.zuan.kernelmanager.next.feature.dashboard.DashboardState
import com.zuan.kernelmanager.next.feature.dashboard.OverallBatteryInfo
import com.zuan.kernelmanager.next.feature.dashboard.GithubRelease // FIX: Import diperbaiki ke package yang benar
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import com.zuan.kernelmanager.next.ui.components.zkmHazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import com.zuan.kernelmanager.next.ui.components.ZkmHazeStyleCache
import com.zuan.kernelmanager.next.ui.theme.zkmIconShape
import com.zuan.kernelmanager.next.ui.theme.ZkmIconGlyph

val ColorBrandPrimary @Composable get() = MaterialTheme.colorScheme.primary
val ColorBrandSecondary @Composable get() = MaterialTheme.colorScheme.secondary
val ColorBrandTertiary @Composable get() = MaterialTheme.colorScheme.tertiary
val ColorBrandError = Color(0xFFEF4444)

object ExpressiveMotion {
    fun <T> spatial(): SpringSpec<T> = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow)
    fun <T> bouncy(): SpringSpec<T> = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
    fun <T> snappy(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)
}

@Composable
fun ExpressiveEntrance(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var visible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!visible) {
            kotlinx.coroutines.delay(60L * index)
            visible = true
        }
    }
    val transition = updateTransition(targetState = visible, label = "entrance")
    val alpha by transition.animateFloat(transitionSpec = { tween(durationMillis = 420, easing = LinearOutSlowInEasing) }, label = "alpha") { if (it) 1f else 0f }
    val translateY by transition.animateDp(transitionSpec = { ExpressiveMotion.spatial() }, label = "translateY") { if (it) 0.dp else 28.dp }
    val scale by transition.animateFloat(transitionSpec = { ExpressiveMotion.spatial() }, label = "scale") { if (it) 1f else 0.94f }

    if (alpha >= 1f && scale >= 1f && translateY == 0.dp) {
        Box(modifier = modifier) { content() }
    } else {
        Box(modifier = modifier.alpha(alpha).offset(y = translateY).scale(scale)) { content() }
    }
}

fun Modifier.expressivePress(interactionSource: MutableInteractionSource, pressedScale: Float = 0.97f): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (pressed) pressedScale else 1f, animationSpec = ExpressiveMotion.bouncy(), label = "pressScale")
    this.scale(scale)
}

@Composable
fun OverallIconContainer(icon: ImageVector, modifier: Modifier = Modifier, containerColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), iconColor: Color = MaterialTheme.colorScheme.primary, size: Dp = 40.dp, iconSize: Dp = 20.dp, cornerRadius: Dp = 14.dp) {
    Box(
        modifier = modifier.size(size).clip(zkmIconShape(RoundedCornerShape(cornerRadius))).background(Brush.linearGradient(colors = listOf(containerColor.copy(alpha = (containerColor.alpha + 0.06f).coerceAtMost(1f)), containerColor))),
        contentAlignment = Alignment.Center
    ) { ZkmIconGlyph(icon = icon, tint = iconColor, iconSize = iconSize) }
}

@Composable
fun generateThemeBackgroundColors(isDark: Boolean, primaryColor: Color): Pair<Color, Color> = com.zuan.kernelmanager.next.ui.components.ZkmScreenPalette.colors(isDark, primaryColor)

fun getAlgorithmColor(algorithm: String): Color {
    return when (algorithm.lowercase()) {
        "lz4" -> Color(0xFF10B981)
        "zstd" -> Color(0xFF3B82F6)
        "lzo" -> Color(0xFFF59E0B)
        "lz4hc" -> Color(0xFF8B5CF6)
        "deflate" -> Color(0xFFEF4444)
        "842" -> Color(0xFFEC4899)
        else -> Color(0xFF6B7280)
    }
}

fun RoundedPolygon.toShape(): Shape {
    return object : Shape {
        override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
            val path = android.graphics.Path(); this@toShape.toPath(path)
            val bounds = RectF(); path.computeBounds(bounds, true)
            val matrix = Matrix(); matrix.setRectToRect(bounds, RectF(0f, 0f, size.width, size.height), Matrix.ScaleToFit.FILL)
            path.transform(matrix); return Outline.Generic(path.asComposePath())
        }
    }
}

/** Core governor detail dialog - same glass style as every other ZKM dialog. */
@Composable
fun CoreDetailDialogOverlay(info: CoreDetailInfo, onDismiss: () -> Unit, onSettings: () -> Unit, hazeState: HazeState) {
    val colors = MaterialTheme.colorScheme
    val accent = colors.primary
    val tunables = remember(info.tunables) { info.tunables.entries.toList() }

    ZkmDialog(onDismissRequest = onDismiss) {
        ZkmDialogPanel {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OverallIconContainer(icon = Icons.Default.Memory, containerColor = accent.copy(alpha = 0.14f), iconColor = accent, size = 56.dp, iconSize = 28.dp, cornerRadius = 18.dp)
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.core_detail_title, info.coreIndex),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = colors.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.core_detail_governor).trim(), style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
                    Text(info.governor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent)
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    stringResource(R.string.core_detail_params),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = 340.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface)
                ) {
                    LazyColumn {
                        if (tunables.isEmpty()) {
                            item {
                                Box(Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.core_detail_no_params), color = colors.onSurfaceVariant)
                                }
                            }
                        } else {
                            itemsIndexed(tunables) { index, entry ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 20.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(entry.key, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                                    Text(entry.value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.onSurface, textAlign = TextAlign.End)
                                }
                                if (index != tunables.lastIndex) {
                                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.25f))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp)) {
                        Text(stringResource(R.string.core_detail_cancel), style = MaterialTheme.typography.labelLarge, color = accent, maxLines = 1)
                    }
                    Button(
                        onClick = onSettings,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = colors.onPrimary),
                        elevation = ButtonDefaults.buttonElevation(0.dp)
                    ) {
                        Icon(Icons.Default.Settings, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.core_detail_settings), fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun GPUWidget(state: DashboardState, modifier: Modifier, containerColor: Color, textColor: Color, subTextColor: Color, onClick: () -> Unit = {}) {
    OSWidgetContainer(modifier.clickable(onClick = onClick), containerColor) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OverallIconContainer(icon = Icons.Rounded.GraphicEq, containerColor = ColorBrandPrimary.copy(alpha = 0.12f), iconColor = ColorBrandPrimary, size = 46.dp, iconSize = 24.dp, cornerRadius = 16.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.gpu_load), style = MaterialTheme.typography.labelMedium, color = subTextColor)
                val animGpu by animateIntAsState(state.gpuLoad, ExpressiveMotion.snappy(), label = "gpuLoad")
                Text("$animGpu%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = textColor)
            }
            Spacer(Modifier.weight(1f))
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(10.dp)) {
                Text(state.gpuFreq, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        val animProgress by animateFloatAsState(state.gpuLoad / 100f, ExpressiveMotion.snappy(), label = "gpuProg")
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(animProgress).clip(RoundedCornerShape(6.dp)).background(Brush.horizontalGradient(listOf(ColorBrandPrimary.copy(alpha = 0.7f), ColorBrandPrimary))))
        }
        Spacer(Modifier.height(16.dp)); HorizontalDivider(color = subTextColor.copy(alpha = 0.2f)); Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.gpu_renderer_info), style = MaterialTheme.typography.labelSmall, color = subTextColor); Spacer(Modifier.height(4.dp))
        Text(state.gpuDetail, style = MaterialTheme.typography.bodySmall, color = textColor, lineHeight = 16.sp)
    }
}

@Composable
fun MemoryWidget(state: DashboardState, modifier: Modifier, containerColor: Color, textColor: Color, subTextColor: Color, onClick: () -> Unit = {}) {
    OSWidgetContainer(modifier.clickable(onClick = onClick), containerColor) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OverallIconContainer(icon = Icons.Rounded.Memory, containerColor = ColorBrandTertiary.copy(alpha = 0.12f), iconColor = ColorBrandTertiary, size = 46.dp, iconSize = 24.dp, cornerRadius = 16.dp)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.ram_physical), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = textColor)
        }
        Spacer(Modifier.height(12.dp))
        ModernProgressBar(percentText = "${(state.ramProgress * 100).toInt()}%", progress = state.ramProgress, color = ColorBrandTertiary, used = state.memUsed, free = state.memFree, total = state.memTotal, textColor = textColor, subTextColor = subTextColor)

        Spacer(Modifier.height(20.dp)); HorizontalDivider(color = subTextColor.copy(alpha = 0.15f)); Spacer(Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OverallIconContainer(icon = Icons.Rounded.SwapHoriz, containerColor = ColorBrandSecondary.copy(alpha = 0.12f), iconColor = ColorBrandSecondary, size = 46.dp, iconSize = 24.dp, cornerRadius = 16.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) { Text(stringResource(R.string.ram_swap_zram), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = textColor) }

            if (state.zramAlgorithm != "Unknown") {
                val algoColor = getAlgorithmColor(state.zramAlgorithm)
                Surface(color = algoColor.copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(algoColor))
                        Text(state.zramAlgorithm.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = algoColor, fontSize = 10.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        ModernProgressBar(percentText = "${(state.swapProgress * 100).toInt()}%", progress = state.swapProgress, color = ColorBrandSecondary, used = state.swapUsed, free = state.swapFree, total = state.swapTotal, textColor = textColor, subTextColor = subTextColor)
    }
}

@Composable
fun ModernProgressBar(percentText: String, progress: Float, color: Color, used: String, free: String, total: String, textColor: Color, subTextColor: Color) {
    Column {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Bottom) { Text(percentText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color) }
        Spacer(Modifier.height(8.dp))
        val animProgress by animateFloatAsState(progress.coerceIn(0f, 1f), ExpressiveMotion.snappy(), label = "memProg")
        LinearProgressIndicator(progress = { animProgress }, modifier = Modifier.fillMaxWidth().height(9.dp), color = color, trackColor = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            MemoryDetailItem(stringResource(R.string.ram_used), used, false, textColor, subTextColor)
            MemoryDetailItem(stringResource(R.string.ram_free), free, false, textColor, subTextColor)
            MemoryDetailItem(stringResource(R.string.ram_total), total, true, textColor, subTextColor)
        }
    }
}

@Composable
fun MemoryDetailItem(label: String, value: String, alignEnd: Boolean = false, textColor: Color, subTextColor: Color) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = subTextColor, fontSize = 9.sp)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = textColor)
    }
}

@Composable
fun VMParametersWidget(state: DashboardState, modifier: Modifier, containerColor: Color, textColor: Color, subTextColor: Color, onClick: () -> Unit = {}) {
    OSWidgetContainer(modifier.clickable(onClick = onClick), containerColor) {
        val vmParams = state.vmParameters
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            VMParamItem(stringResource(R.string.vm_swappiness), vmParams.swappiness, Icons.Rounded.WaterDrop, ColorBrandPrimary, textColor = textColor, subTextColor = subTextColor)
            HorizontalDivider(color = subTextColor.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 12.dp))
            VMParamItem(stringResource(R.string.vm_extra_free), vmParams.extraFreeKbytes, Icons.Rounded.AddCircle, ColorBrandSecondary, textColor = textColor, subTextColor = subTextColor)
            HorizontalDivider(color = subTextColor.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 12.dp))
            VMParamItem(stringResource(R.string.vm_watermark), vmParams.watermarkScaleFactor, Icons.Rounded.SignalCellularAlt, ColorBrandTertiary, textColor = textColor, subTextColor = subTextColor)
            HorizontalDivider(color = subTextColor.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 12.dp))
            VMParamItem(stringResource(R.string.vm_vfs_cache), vmParams.vfsCachePressure, Icons.Rounded.Storage, ColorBrandPrimary, textColor = textColor, subTextColor = subTextColor)
            HorizontalDivider(color = subTextColor.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 12.dp))
            VMParamItem(stringResource(R.string.vm_dirty_ratio), vmParams.dirtyRatio, Icons.Rounded.DirtyLens, ColorBrandSecondary, textColor = textColor, subTextColor = subTextColor)
            HorizontalDivider(color = subTextColor.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 12.dp))
            VMParamItem(stringResource(R.string.vm_dirty_bg), vmParams.dirtyBgRatio, Icons.Rounded.CleaningServices, ColorBrandTertiary, textColor = textColor, subTextColor = subTextColor)
        }
    }
}

@Composable
fun VMParamItem(label: String, value: String, icon: ImageVector, iconColor: Color, modifier: Modifier = Modifier, textColor: Color, subTextColor: Color) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
        OverallIconContainer(icon = icon, containerColor = iconColor.copy(alpha = 0.12f), iconColor = iconColor, size = 38.dp, iconSize = 18.dp, cornerRadius = 12.dp)
        Spacer(Modifier.width(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = textColor, fontWeight = FontWeight.Medium)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = textColor)
        }
    }
}

@Composable
fun DisplayInfoWidget(state: DashboardState, modifier: Modifier, containerColor: Color, textColor: Color, subTextColor: Color, onClick: () -> Unit = {}) {
    OSWidgetContainer(modifier.clickable(onClick = onClick), containerColor) {
        val displayInfo = state.displayInfo
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            DisplayInfoRow(stringResource(R.string.display_resolution), displayInfo.resolution, null, Icons.Rounded.AspectRatio, ColorBrandPrimary, textColor, subTextColor)
            HorizontalDivider(color = subTextColor.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 12.dp))
            DisplayInfoRow(stringResource(R.string.display_density), displayInfo.densityDpi, displayInfo.density, Icons.Rounded.ZoomIn, ColorBrandSecondary, textColor, subTextColor)
            HorizontalDivider(color = subTextColor.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 12.dp))
            DisplayInfoRow(stringResource(R.string.display_technology), displayInfo.technology, null, Icons.Rounded.DisplaySettings, ColorBrandTertiary, textColor, subTextColor)
            HorizontalDivider(color = subTextColor.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 12.dp))
            DisplayInfoRow(stringResource(R.string.display_hdr), displayInfo.hdrSupport, null, Icons.Rounded.HdrOn, if (displayInfo.hdrSupport.contains("Not")) subTextColor else ColorBrandPrimary, textColor, subTextColor)
            HorizontalDivider(color = subTextColor.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 12.dp))
            DisplayInfoRow(stringResource(R.string.display_refresh_rate), displayInfo.refreshRate, null, Icons.Rounded.Speed, ColorBrandSecondary, textColor, subTextColor)
            HorizontalDivider(color = subTextColor.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 12.dp))
            DisplayInfoRow(stringResource(R.string.display_orientation), displayInfo.orientation, null, Icons.Rounded.ScreenRotation, ColorBrandTertiary, textColor, subTextColor)
            HorizontalDivider(color = subTextColor.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 12.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
                OverallIconContainer(icon = Icons.Rounded.Straighten, containerColor = ColorBrandPrimary.copy(alpha = 0.15f), iconColor = ColorBrandPrimary, size = 46.dp, iconSize = 24.dp, cornerRadius = 16.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.display_physical_size), style = MaterialTheme.typography.bodyMedium, color = subTextColor, fontSize = 13.sp)
                    Text(displayInfo.diagonalSize, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor)
                    Text(displayInfo.physicalSize, style = MaterialTheme.typography.labelSmall, color = subTextColor.copy(alpha = 0.7f), fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun DisplayInfoRow(label: String, value: String, subValue: String? = null, icon: ImageVector, iconColor: Color, textColor: Color, subTextColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
        OverallIconContainer(icon = icon, containerColor = iconColor.copy(alpha = 0.15f), iconColor = iconColor, size = 38.dp, iconSize = 18.dp, cornerRadius = 12.dp)
        Spacer(Modifier.width(12.dp))
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = subTextColor, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = textColor, fontSize = 15.sp)
                if (subValue != null) { Text(" ($subValue)", style = MaterialTheme.typography.labelSmall, color = subTextColor, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
fun LiveCurrentChart(history: List<Int>, color: Color, modifier: Modifier = Modifier) {
    if (history.isEmpty()) return
    Canvas(modifier = modifier) {
        val min = history.minOrNull() ?: 0; val max = history.maxOrNull() ?: 100
        val range = (max - min).toFloat().coerceAtLeast(1f)
        val widthPerPoint = size.width / (history.size - 1).coerceAtLeast(1)
        val path = Path()
        history.forEachIndexed { index, value ->
            val x = index * widthPerPoint
            val normalizedY = size.height - ((value - min) / range * size.height)
            if (index == 0) path.moveTo(x, normalizedY) else {
                val prevX = (index - 1) * widthPerPoint
                val prevY = size.height - ((history[index - 1] - min) / range * size.height)
                path.cubicTo(prevX + widthPerPoint / 2, prevY, x - widthPerPoint / 2, normalizedY, x, normalizedY)
            }
        }
        drawPath(path = path, color = color.copy(alpha = 0.5f), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val fillPath = Path(); fillPath.addPath(path); fillPath.lineTo(size.width, size.height); fillPath.lineTo(0f, size.height); fillPath.close()
        drawPath(path = fillPath, brush = Brush.verticalGradient(colors = listOf(color.copy(alpha = 0.2f), Color.Transparent), startY = 0f, endY = size.height))
    }
}

@Composable
fun OngoingInfoCard(modifier: Modifier = Modifier, info: OverallBatteryInfo, containerColor: Color, contentColor: Color, subColor: Color, onClick: (() -> Unit)? = null) {
    val clickModifier = if (onClick != null) Modifier.clip(RoundedCornerShape(30.dp)).clickable(onClick = onClick) else Modifier
    Card(modifier = modifier.then(clickModifier), shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = containerColor), elevation = CardDefaults.cardElevation(0.dp), border = null) {
        Column(modifier = Modifier.padding(24.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.battery_details), style = MaterialTheme.typography.bodyMedium, color = subColor)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OverallIconContainer(icon = Icons.Outlined.Bolt, containerColor = Color(0xFFFFC107).copy(alpha = 0.12f), iconColor = Color(0xFFFFC107), size = 42.dp, iconSize = 20.dp, cornerRadius = 14.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column { Text(stringResource(R.string.battery_voltage), style = MaterialTheme.typography.labelSmall, color = subColor); Text(info.voltage.replace(" mV", " V"), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = contentColor) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OverallIconContainer(icon = Icons.Outlined.Schedule, containerColor = Color(0xFF3B82F6).copy(alpha = 0.12f), iconColor = Color(0xFF3B82F6), size = 42.dp, iconSize = 20.dp, cornerRadius = 14.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column { Text(stringResource(R.string.battery_uptime), style = MaterialTheme.typography.labelSmall, color = subColor); Text(info.uptime, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = contentColor) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OverallIconContainer(icon = Icons.Outlined.Bedtime, containerColor = Color(0xFF8B5CF6).copy(alpha = 0.12f), iconColor = Color(0xFF8B5CF6), size = 42.dp, iconSize = 20.dp, cornerRadius = 14.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column { Text(stringResource(R.string.battery_deep_sleep), style = MaterialTheme.typography.labelSmall, color = subColor); Text(info.deepSleep.substringBefore(" ("), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = contentColor) }
            }
        }
    }
}

@Composable
private fun BatteryStatPill(text: String, tint: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = textColor, maxLines = 1)
    }
}

@Composable
fun BatteryStatusChip(info: OverallBatteryInfo, textColor: Color, subColor: Color) {
    val lower = info.status.lowercase()
    val dot = when {
        lower.contains("discharg") -> Color(0xFFF59E0B)
        lower.contains("not") -> subColor
        lower.contains("charg") || lower.contains("full") -> Color(0xFF22C55E)
        else -> subColor
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(dot.copy(alpha = 0.13f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(7.dp))
        Text(
            "${dashboardLocalized(info.status)} · ${"%.1f".format(java.util.Locale.US, info.wattage)} W",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = textColor,
            maxLines = 1
        )
    }
}

@Composable
fun DetailedBatteryStats(
    state: DashboardState,
    modifier: Modifier,
    containerColor: Color,
    contentColor: Color,
    subColor: Color,
    onOpenBattery: () -> Unit = {},
    onOpenThermal: () -> Unit = {},
    onOpenChargeStats: () -> Unit = {}
) {
    val info = state.batteryInfo
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val statusLower = info.status.lowercase()
    val isCharging = statusLower.contains("charg") && !statusLower.contains("discharg") && !statusLower.contains("not")
    val isDischarging = statusLower.contains("discharg")

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ── Electric current ──────────────────────────────────────────────
        Card(modifier = modifier.fillMaxWidth().height(172.dp).clickable(onClick = onOpenChargeStats), shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = containerColor), elevation = CardDefaults.cardElevation(0.dp), border = null) {
            Box(modifier = Modifier.fillMaxSize()) {
                LiveCurrentChart(history = state.batteryHistory, color = primary, modifier = Modifier.fillMaxWidth().height(70.dp).align(Alignment.BottomCenter))
                Column(modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 18.dp).fillMaxSize()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.battery_electric_current), style = MaterialTheme.typography.bodyMedium, color = subColor)
                        Icon(Icons.Default.ChevronRight, null, tint = subColor.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.Bottom) {
                        val currentNow = kotlin.math.abs(info.currentNow)
                        Text(text = "$currentNow", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Medium, color = contentColor)
                        Text(text = " mA", style = MaterialTheme.typography.bodyLarge, color = subColor, modifier = Modifier.padding(bottom = 7.dp))
                        if (isCharging || isDischarging) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = if (isCharging) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                                contentDescription = null,
                                tint = if (isCharging) Color(0xFF22C55E) else Color(0xFFF59E0B),
                                modifier = Modifier.padding(bottom = 9.dp).size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        BatteryStatPill("%.1f W".format(java.util.Locale.US, info.wattage), primary, contentColor)
                        BatteryStatPill(info.voltage.replace(" ", ""), secondary, contentColor)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
            // ── Health ────────────────────────────────────────────────────
            Card(modifier = modifier.weight(1f).fillMaxHeight().clickable(onClick = onOpenBattery), shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = containerColor), elevation = CardDefaults.cardElevation(0.dp), border = null) {
                Column(modifier = Modifier.padding(20.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.battery_health), style = MaterialTheme.typography.bodyMedium, color = subColor)
                        Icon(Icons.Default.ChevronRight, null, tint = subColor.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                    }
                    val currentMaxVal = info.maximumCapacity.substringBefore(" mAh").trim().toFloatOrNull() ?: 0f
                    val designVal = info.designCapacity.substringBefore(" mAh").trim().toFloatOrNull() ?: 1f
                    val healthPercent = if (currentMaxVal > 0) ((currentMaxVal / designVal) * 100).toInt() else 0
                    val displayHealth = if (healthPercent > 200) 100 else healthPercent.coerceIn(0, 100)
                    val animHealth by animateIntAsState(displayHealth, ExpressiveMotion.snappy(), label = "health")
                    val healthColor = when {
                        displayHealth >= 80 -> primary
                        displayHealth >= 60 -> Color(0xFFF59E0B)
                        else -> ColorBrandError
                    }
                    Column {
                        Text(text = "$animHealth%", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = contentColor)
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(subColor.copy(alpha = 0.16f))) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth((animHealth / 100f).coerceIn(0.03f, 1f))
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Brush.horizontalGradient(listOf(healthColor.copy(alpha = 0.6f), healthColor)))
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OverallIconContainer(icon = Icons.Rounded.Favorite, containerColor = healthColor.copy(alpha = 0.14f), iconColor = healthColor, size = 34.dp, iconSize = 16.dp, cornerRadius = 10.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.battery_real_capacity), style = MaterialTheme.typography.labelSmall, color = subColor, fontSize = 10.sp)
                            Text("${currentMaxVal.toInt()} / ${designVal.toInt()}", style = MaterialTheme.typography.labelMedium, color = contentColor, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            // ── Temperature + Cycles ──────────────────────────────────────
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TemperatureReactorCard(info = info, modifier = modifier.weight(1f).fillMaxWidth(), containerColor = containerColor, textColor = contentColor, onClick = onOpenThermal)
                Card(modifier = modifier.weight(1f).fillMaxWidth().clickable(onClick = onOpenBattery), shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = containerColor), elevation = CardDefaults.cardElevation(0.dp), border = null) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.battery_cycles), style = MaterialTheme.typography.bodySmall, color = subColor)
                            Icon(Icons.Default.ChevronRight, null, tint = subColor.copy(alpha = 0.3f), modifier = Modifier.size(14.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = info.cycleCount, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium, color = contentColor, modifier = Modifier.weight(1f))
                            OverallIconContainer(icon = Icons.Rounded.Autorenew, containerColor = secondary.copy(alpha = 0.14f), iconColor = secondary, size = 32.dp, iconSize = 16.dp, cornerRadius = 10.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, textColor: Color, trailing: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, end = 4.dp)) {
        Box(modifier = Modifier.width(4.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, color = textColor, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun UpdateCard(release: GithubRelease, modifier: Modifier, containerColor: Color, onClick: () -> Unit, textColor: Color) {
    val interaction = remember { MutableInteractionSource() }
    OSWidgetContainer(modifier.expressivePress(interaction, 0.98f).clickable(interactionSource = interaction, indication = null) { onClick() }, containerColor) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val glow by zkmInfiniteFloat(initialValue = 0.85f, targetValue = 1.05f, animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")
            Box(modifier = Modifier.scale(glow)) {
                OverallIconContainer(icon = Icons.Default.SystemUpdate, containerColor = MaterialTheme.colorScheme.primary, iconColor = MaterialTheme.colorScheme.onPrimary, size = 46.dp, iconSize = 24.dp, cornerRadius = 23.dp)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(stringResource(R.string.update_available, release.tag_name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor)
                Text(stringResource(R.string.update_tap_install), style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun OSWidgetContainer(modifier: Modifier = Modifier, containerColor: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(containerColor).padding(20.dp), content = content)
}

@Composable
fun DeviceHeroSection(state: DashboardState, modifier: Modifier, containerColor: Color, textColor: Color, subTextColor: Color) {
    OSWidgetContainer(modifier, containerColor) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val rotation by zkmInfiniteFloat(initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Restart), label = "heroRotation")
            Box(Modifier.size(58.dp).rotate(rotation).clip(RoundedPolygon(9).toShape()).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))), Alignment.Center) {
                Icon(Icons.Rounded.Smartphone, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp).rotate(-rotation))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                val manufacturer = android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
                Text("$manufacturer ${state.deviceModel}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = textColor)
                Text("${state.deviceCodename} • ${state.socName}", style = MaterialTheme.typography.bodyMedium, color = subTextColor)
            }
        }
        Spacer(Modifier.height(20.dp)); HorizontalDivider(color = subTextColor.copy(alpha = 0.2f)); Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OverallIconContainer(icon = Icons.Outlined.Android, containerColor = ColorBrandSecondary.copy(alpha = 0.12f), iconColor = ColorBrandSecondary, size = 42.dp, iconSize = 20.dp, cornerRadius = 14.dp)
            Spacer(Modifier.width(12.dp))
            Column { Text(stringResource(R.string.device_android_version), style = MaterialTheme.typography.labelSmall, color = subTextColor); Text("Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = textColor) }
        }
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.Top) {
            OverallIconContainer(icon = Icons.Outlined.Memory, containerColor = ColorBrandPrimary.copy(alpha = 0.12f), iconColor = ColorBrandPrimary, size = 42.dp, iconSize = 20.dp, cornerRadius = 14.dp)
            Spacer(Modifier.width(12.dp))
            Column { Text(stringResource(R.string.device_kernel_info), style = MaterialTheme.typography.labelSmall, color = subTextColor); Text(state.kernelVersion, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, lineHeight = 20.sp, color = textColor) }
        }
    }
}

@Composable
fun UpdateChangelogDialog(state: com.composables.core.DialogState, release: GithubRelease, hazeState: HazeState, onUpdate: (String) -> Unit) {
    DialogUnstyled(
        state = state, hazeState = hazeState, isCustomBg = false, title = "${stringResource(R.string.home_update_avail)} ${release.tag_name}",
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 250.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.home_update_new), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text(release.body, style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)
            }
        },
        confirmButton = { DialogTextButton(icon = Icons.Default.CloudDownload, text = "Update", onClick = { val url = release.assets.firstOrNull()?.browser_download_url; if (url != null) onUpdate(url) }, color = MaterialTheme.colorScheme.primary) },
        dismissButton = { DialogTextButton(text = stringResource(R.string.btn_cancel), onClick = { state.visible = false }, color = MaterialTheme.colorScheme.primary) }
    )
}
