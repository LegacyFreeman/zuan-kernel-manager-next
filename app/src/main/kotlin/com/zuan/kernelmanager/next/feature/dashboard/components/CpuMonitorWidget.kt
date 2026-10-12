/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.dashboard.components

import com.zuan.kernelmanager.next.ui.components.zkmInfiniteFloat
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.feature.dashboard.DashboardState
import com.zuan.kernelmanager.next.feature.dashboard.OverallUtils
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

private const val GAUGE_START_ANGLE = 150f
private const val GAUGE_SWEEP_ANGLE = 240f

/**
 * Processor card.
 *
 *  1. Total-load gauge (ticks + glowing knob) next to Average / Peak / Cores chips
 *  2. Live load history curve with peak marker
 *  3. Per-core rings (highest cluster first, works for any core count)
 *  4. Top CPU usage list + user/system process donut
 */
@Composable
fun CPUMonitorWidget(
    state: DashboardState,
    modifier: Modifier,
    containerColor: Color,
    textColor: Color,
    subTextColor: Color,
    onOpenProcessManager: () -> Unit,
    onCoreClick: (Int) -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val hot = ColorBrandTertiary

    val totalPercent = state.currentCpuLoad.replace("%", "").toIntOrNull() ?: 0
    val history = state.cpuHistory
    val avgPercent = if (history.isNotEmpty()) (history.average() * 100.0).roundToInt() else totalPercent
    val peakPercent = if (history.isNotEmpty()) ((history.maxOrNull() ?: 0f) * 100f).roundToInt() else totalPercent
    val coreCount = max(state.perCoreLoads.size, state.cpuFreqs.size)

    OSWidgetContainer(modifier, containerColor) {

        // ───────────── 1. Gauge + stat chips ─────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val animLoad by animateFloatAsState(
                targetValue = (totalPercent / 100f).coerceIn(0f, 1f),
                animationSpec = tween(650, easing = FastOutSlowInEasing),
                label = "cpuGaugeLoad"
            )
            val gaugeAccent = lerp(accent, hot, ((animLoad - 0.6f) / 0.3f).coerceIn(0f, 1f))
            val animTotal by animateIntAsState(totalPercent, ExpressiveMotion.snappy(), label = "cpuTotalLoad")

            CpuLoadGauge(
                load = animLoad,
                accent = gaugeAccent,
                trackColor = subTextColor.copy(alpha = 0.14f),
                tickColor = subTextColor,
                modifier = Modifier.weight(0.92f)
            ) {
                Text(
                    "$animTotal%",
                    style = MaterialTheme.typography.displaySmall,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp,
                    color = textColor
                )
                Text(
                    stringResource(R.string.cpu_total_load),
                    style = MaterialTheme.typography.labelMedium,
                    color = subTextColor
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CpuStatChip(stringResource(R.string.cpu_stat_average), "$avgPercent%", accent, textColor, subTextColor)
                CpuStatChip(stringResource(R.string.cpu_stat_peak), "$peakPercent%", if (peakPercent >= 80) hot else accent, textColor, subTextColor)
                CpuStatChip(stringResource(R.string.cpu_stat_cores), if (coreCount > 0) "$coreCount" else "—", accent, textColor, subTextColor)
            }
        }

        Spacer(Modifier.height(24.dp))

        // ───────────── 2. Live load history ─────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.cpu_live_load_history),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = subTextColor,
                modifier = Modifier.weight(1f)
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(accent.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(7.dp))
                Text(
                    stringResource(R.string.cpu_peak_badge, peakPercent),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        CpuHistoryChart(
            history = history,
            accent = accent,
            gridColor = subTextColor,
            modifier = Modifier.fillMaxWidth().height(116.dp)
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = subTextColor.copy(alpha = 0.15f))
        Spacer(Modifier.height(18.dp))

        // ───────────── 3. Core usage rings ─────────────
        Text(
            stringResource(R.string.cpu_core_usage),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = subTextColor
        )
        Spacer(Modifier.height(14.dp))

        val usableCores = minOf(state.cpuFreqs.size, state.perCoreLoads.size)
        if (usableCores > 0) {
            val rows = (usableCores - 1 downTo 0).toList().chunked(4)
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                rows.forEach { rowIndices ->
                    Row(Modifier.fillMaxWidth()) {
                        rowIndices.forEach { index ->
                            CoreRing(
                                index = index,
                                freq = state.cpuFreqs[index],
                                load = state.perCoreLoads[index],
                                isPrime = index == usableCores - 1 && usableCores > 4,
                                accent = accent,
                                hot = hot,
                                textColor = textColor,
                                subTextColor = subTextColor,
                                onClick = onCoreClick,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // keep columns aligned when the last row is not full
                        repeat(4 - rowIndices.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        } else {
            Text(stringResource(R.string.cpu_loading), style = MaterialTheme.typography.bodySmall, color = subTextColor)
        }

        Spacer(Modifier.height(22.dp))
        HorizontalDivider(color = subTextColor.copy(alpha = 0.15f))
        Spacer(Modifier.height(18.dp))

        // ───────────── 4. Top processes + donut ─────────────
        TopProcessesSection(
            state = state,
            accent = accent,
            textColor = textColor,
            subTextColor = subTextColor,
            onOpenProcessManager = onOpenProcessManager
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  Gauge
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun CpuLoadGauge(
    load: Float,
    accent: Color,
    trackColor: Color,
    tickColor: Color,
    modifier: Modifier = Modifier,
    center: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val tickOuter = size.minDimension / 2f - 1.dp.toPx()
            val ringRadius = tickOuter - 21.dp.toPx()
            val ringStroke = 13.dp.toPx()
            val topLeft = Offset(c.x - ringRadius, c.y - ringRadius)
            val arcSize = Size(ringRadius * 2f, ringRadius * 2f)

            // tick marks around the arc, lit up to the current load
            val tickCount = 30
            for (i in 0..tickCount) {
                val f = i / tickCount.toFloat()
                val rad = Math.toRadians((GAUGE_START_ANGLE + GAUGE_SWEEP_ANGLE * f).toDouble())
                val major = i % 5 == 0
                val len = if (major) 9.dp.toPx() else 5.dp.toPx()
                val active = f <= load
                val color = when {
                    active -> accent.copy(alpha = if (major) 0.9f else 0.5f)
                    else -> tickColor.copy(alpha = if (major) 0.45f else 0.25f)
                }
                drawLine(
                    color = color,
                    start = Offset(c.x + (cos(rad) * tickOuter).toFloat(), c.y + (sin(rad) * tickOuter).toFloat()),
                    end = Offset(c.x + (cos(rad) * (tickOuter - len)).toFloat(), c.y + (sin(rad) * (tickOuter - len)).toFloat()),
                    strokeWidth = if (major) 2.dp.toPx() else 1.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // track
            drawArc(
                color = trackColor,
                startAngle = GAUGE_START_ANGLE,
                sweepAngle = GAUGE_SWEEP_ANGLE,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = ringStroke, cap = StrokeCap.Round)
            )

            if (load > 0.002f) {
                val sweep = GAUGE_SWEEP_ANGLE * load
                // soft halo
                drawArc(
                    color = accent.copy(alpha = 0.16f),
                    startAngle = GAUGE_START_ANGLE,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = ringStroke + 9.dp.toPx(), cap = StrokeCap.Round)
                )
                // progress
                drawArc(
                    brush = Brush.linearGradient(
                        colors = listOf(accent.copy(alpha = 0.72f), accent),
                        start = Offset(0f, size.height),
                        end = Offset(size.width, 0f)
                    ),
                    startAngle = GAUGE_START_ANGLE,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = ringStroke, cap = StrokeCap.Round)
                )
                // knob
                val rad = Math.toRadians((GAUGE_START_ANGLE + sweep).toDouble())
                val knob = Offset(c.x + (cos(rad) * ringRadius).toFloat(), c.y + (sin(rad) * ringRadius).toFloat())
                drawCircle(accent.copy(alpha = 0.22f), radius = 13.dp.toPx(), center = knob)
                drawCircle(Color.White, radius = 7.5.dp.toPx(), center = knob)
                drawCircle(accent, radius = 4.5.dp.toPx(), center = knob)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = center)
    }
}

@Composable
private fun CpuStatChip(
    label: String,
    value: String,
    accent: Color,
    textColor: Color,
    subTextColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(subTextColor.copy(alpha = 0.08f))
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.3f))))
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = subTextColor, maxLines = 1)
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  Live history chart
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun CpuHistoryChart(
    history: List<Float>,
    accent: Color,
    gridColor: Color,
    modifier: Modifier = Modifier
) {
    val pulse by zkmInfiniteFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "cpuChartPulseValue"
    )

    Canvas(modifier) {
        val padX = 14.dp.toPx()
        val padTop = 14.dp.toPx()
        val padBottom = 10.dp.toPx()
        val w = size.width - padX * 2f
        val h = size.height - padTop - padBottom
        val bottom = padTop + h

        // dashed guide lines (top / middle / baseline)
        val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 6.dp.toPx()))
        for (i in 0..2) {
            val y = padTop + h * (i / 2f)
            drawLine(
                color = gridColor.copy(alpha = 0.22f),
                start = Offset(padX, y),
                end = Offset(padX + w, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dash
            )
        }

        val n = history.size
        if (n < 2) {
            drawLine(accent.copy(alpha = 0.5f), Offset(padX, bottom), Offset(padX + w, bottom), 3.dp.toPx(), StrokeCap.Round)
            return@Canvas
        }

        // y-scale adapts to the peak so low loads still draw a readable curve
        val peak = history.maxOrNull() ?: 0f
        val top = max(0.5f, ceil(peak * 4f) / 4f).coerceAtMost(1f)
        fun xAt(i: Int) = padX + w * (i / (n - 1).toFloat())
        fun yAt(v: Float) = padTop + h * (1f - (v / top).coerceIn(0f, 1f))

        val line = Path()
        val fill = Path()
        line.moveTo(xAt(0), yAt(history[0]))
        fill.moveTo(xAt(0), bottom)
        fill.lineTo(xAt(0), yAt(history[0]))
        for (i in 1 until n) {
            val x0 = xAt(i - 1); val y0 = yAt(history[i - 1])
            val x1 = xAt(i); val y1 = yAt(history[i])
            val mid = (x1 - x0) / 2f
            line.cubicTo(x0 + mid, y0, x1 - mid, y1, x1, y1)
            fill.cubicTo(x0 + mid, y0, x1 - mid, y1, x1, y1)
        }
        fill.lineTo(xAt(n - 1), bottom)
        fill.close()

        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                colors = listOf(accent.copy(alpha = 0.22f), Color.Transparent),
                startY = padTop,
                endY = bottom
            )
        )
        drawPath(line, accent.copy(alpha = 0.16f), style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(line, accent, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // peak marker
        val peakIndex = history.indexOf(peak)
        if (peak > 0.05f && peakIndex in 1 until n - 1) {
            val p = Offset(xAt(peakIndex), yAt(peak))
            drawCircle(accent.copy(alpha = 0.2f), radius = 10.dp.toPx(), center = p)
            drawCircle(accent, radius = 5.dp.toPx(), center = p)
            drawCircle(Color.White.copy(alpha = 0.9f), radius = 2.dp.toPx(), center = p)
        }

        // live end-dot with a pulsing ring
        val end = Offset(xAt(n - 1), yAt(history.last()))
        drawCircle(
            color = accent.copy(alpha = (1f - pulse) * 0.4f),
            radius = 7.dp.toPx() + 12.dp.toPx() * pulse,
            center = end,
            style = Stroke(width = 1.5.dp.toPx())
        )
        drawCircle(accent.copy(alpha = 0.22f), radius = 10.dp.toPx(), center = end)
        drawCircle(Color.White, radius = 6.dp.toPx(), center = end)
        drawCircle(accent, radius = 3.8.dp.toPx(), center = end)
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  Core ring
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun CoreRing(
    index: Int,
    freq: String,
    load: Int,
    isPrime: Boolean,
    accent: Color,
    hot: Color,
    textColor: Color,
    subTextColor: Color,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val fraction by animateFloatAsState((load / 100f).coerceIn(0f, 1f), ExpressiveMotion.snappy(), label = "coreRing$index")
    val animatedLoad by animateIntAsState(load, ExpressiveMotion.snappy(), label = "coreRingText$index")
    val ringColor = lerp(accent, hot, ((fraction - 0.65f) / 0.3f).coerceIn(0f, 1f))

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .expressivePress(interaction, 0.92f)
            .clickable(interactionSource = interaction, indication = null) { onClick(index) }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(3.dp)) {
                val stroke = 5.5.dp.toPx()
                drawCircle(subTextColor.copy(alpha = 0.16f), style = Stroke(width = stroke))
                if (fraction > 0.003f) {
                    drawArc(
                        color = ringColor.copy(alpha = 0.18f),
                        startAngle = -90f,
                        sweepAngle = 360f * fraction,
                        useCenter = false,
                        style = Stroke(width = stroke + 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * fraction,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
            }
            Text(
                "$animatedLoad%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = textColor
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "C$index",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isPrime) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (isPrime) accent else subTextColor
        )
        Text(
            freq,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            color = subTextColor,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  Top processes + donut
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun TopProcessesSection(
    state: DashboardState,
    accent: Color,
    textColor: Color,
    subTextColor: Color,
    onOpenProcessManager: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(250.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val cardBg = subTextColor.copy(alpha = 0.08f)

        // ── list ──
        Column(
            modifier = Modifier
                .weight(1.25f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(24.dp))
                .background(cardBg)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onOpenProcessManager() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.cpu_top_usage),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = subTextColor, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(10.dp))

            if (state.processList.isEmpty()) {
                // Basic mode cannot list other apps' CPU usage (Android sandbox) - say so instead of "Loading..." forever.
                val basic = !com.zuan.kernelmanager.next.library.shell.PrivilegedAccess.shellWanted()
                Text(
                    stringResource(if (basic) R.string.cpu_top_needs_shell else R.string.lp_loading),
                    style = MaterialTheme.typography.labelSmall, color = subTextColor
                )
            } else {
                val maxUsage = max(state.processList.maxOf { it.cpuUsage }, 1f)
                // LazyColumn: hanya baris yang kelihatan (~6) yang di-compose, bukan semua 20 baris.
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.processList.take(OverallUtils.TOP_PROCESS_LIMIT), key = { it.pid }) { process ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                process.icon.let { icon ->
                                    if (icon != null) {
                                        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)))
                                    } else {
                                        Box(
                                            Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(accent.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(process.name.take(1).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent)
                                        }
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    process.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "${process.cpuUsage}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = subTextColor,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Box(
                                Modifier
                                    .padding(start = 30.dp)
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(subTextColor.copy(alpha = 0.14f))
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth((process.cpuUsage / maxUsage).coerceIn(0.04f, 1f))
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.55f), accent)))
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── donut ──
        Column(
            modifier = Modifier
                .weight(0.95f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(24.dp))
                .background(cardBg)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val total = state.processList.size
            val userProcs = state.processList.count { it.icon != null }
            val sysProcs = total - userProcs
            val userSweep = if (total > 0) userProcs.toFloat() / total.toFloat() * 360f else 0f
            val trackColor = subTextColor.copy(alpha = 0.2f)

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(92.dp)) {
                Canvas(Modifier.fillMaxSize().padding(6.dp)) {
                    drawArc(trackColor, 0f, 360f, false, style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round))
                    if (userSweep > 0f) {
                        drawArc(accent, -90f, userSweep, false, style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$total", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = textColor)
                    Text(stringResource(R.string.cpu_process_total), style = MaterialTheme.typography.labelSmall, color = subTextColor, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DonutLegend(accent, "$userProcs", stringResource(R.string.lp_user), textColor, subTextColor)
                DonutLegend(trackColor, "$sysProcs", stringResource(R.string.lp_sys), textColor, subTextColor)
            }
        }
    }
}

@Composable
private fun DonutLegend(dot: Color, value: String, label: String, textColor: Color, subTextColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor)
        Text(label, style = MaterialTheme.typography.labelSmall, color = subTextColor, fontSize = 10.sp)
    }
}
