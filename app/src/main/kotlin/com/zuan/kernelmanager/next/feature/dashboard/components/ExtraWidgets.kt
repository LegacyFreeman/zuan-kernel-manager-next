/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.dashboard.components

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.feature.dashboard.DashboardState
import java.util.Locale

/**
 * =====================================================================================
 * WIDGET TAMBAHAN (menu FAB → Add Widget)
 * Semua widget di file ini memakai data yang sama dengan dashboard (DashboardState) dan
 * gaya yang sama dengan widget "intelligence" lain (OSWidgetContainer + IntelligenceHeader).
 * =====================================================================================
 */

@Composable
private fun StatusChip(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        )
    }
}

/** Sparkline sederhana untuk deret Float. Tidak menggambar apa pun bila data < 2 titik. */
@Composable
private fun MiniSparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    if (values.size < 2) return
    Canvas(modifier = modifier) {
        val min = values.minOrNull() ?: 0f
        val max = values.maxOrNull() ?: 1f
        val range = (max - min).coerceAtLeast(1f)
        val step = size.width / (values.size - 1).coerceAtLeast(1)
        fun y(v: Float) = size.height - ((v - min) / range * size.height)

        val line = Path()
        values.forEachIndexed { i, v ->
            val x = i * step
            if (i == 0) line.moveTo(x, y(v)) else line.lineTo(x, y(v))
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            fill,
            Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent), 0f, size.height)
        )
        drawPath(
            line,
            color.copy(alpha = 0.7f),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawCircle(color, radius = 3.5.dp.toPx(), center = Offset((values.size - 1) * step, y(values.last())))
    }
}

@Composable
private fun PressureRow(label: String, percent: Float, textColor: Color, subTextColor: Color) {
    val value = percent.coerceIn(0f, 100f)
    val barColor = when {
        value >= 25f -> MaterialTheme.colorScheme.error
        value >= 10f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    IntelligenceMetricRow(label, "%.2f%%".format(Locale.US, value), textColor, subTextColor)
    Spacer(Modifier.height(6.dp))
    LinearProgressIndicator(
        progress = { (value / 50f).coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
        color = barColor,
        trackColor = MaterialTheme.colorScheme.surfaceVariant
    )
}

// ------------------------------------------------------------------------------------
// THERMAL MONITOR
// ------------------------------------------------------------------------------------
@Composable
fun ThermalMonitorWidget(state: DashboardState, modifier: Modifier, containerColor: Color, textColor: Color, subTextColor: Color) {
    val t = state.thermal
    val hot = t.throttling
    val accent = if (hot) MaterialTheme.colorScheme.error else ColorBrandPrimary
    OSWidgetContainer(modifier, containerColor) {
        IntelligenceHeader(
            icon = Icons.Rounded.Thermostat,
            title = stringResource(R.string.lp_widget_thermal_monitor),
            textColor = textColor,
            iconColor = accent,
            trailing = {
                StatusChip(
                    if (hot) stringResource(R.string.lp_widget_thermal_hot) else stringResource(R.string.lp_widget_thermal_normal),
                    accent
                )
            }
        )
        Spacer(Modifier.height(14.dp))
        Text(
            if (t.zoneCount > 0) "%.1f °C".format(Locale.US, t.maxTempC) else "—",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.lp_widget_hottest_zone, t.hottestZone),
            style = MaterialTheme.typography.bodyMedium,
            color = subTextColor
        )
        Spacer(Modifier.height(12.dp))
        MiniSparkline(state.thermalHistory, accent, Modifier.fillMaxWidth().height(56.dp))
        Spacer(Modifier.height(14.dp))
        IntelligenceDivider(subTextColor)
        Spacer(Modifier.height(12.dp))
        IntelligenceMetricRow(
            stringResource(R.string.lp_widget_avg_temp),
            if (t.zoneCount > 0) "%.1f °C".format(Locale.US, t.avgTempC) else "—",
            textColor, subTextColor
        )
        Spacer(Modifier.height(8.dp))
        IntelligenceMetricRow(stringResource(R.string.lp_widget_active_zones), t.zoneCount.toString(), textColor, subTextColor)
    }
}

// ------------------------------------------------------------------------------------
// POWER DRAW
// ------------------------------------------------------------------------------------
@Composable
fun PowerDrawWidget(state: DashboardState, modifier: Modifier, containerColor: Color, textColor: Color, subTextColor: Color) {
    val b = state.batteryInfo
    OSWidgetContainer(modifier, containerColor) {
        IntelligenceHeader(
            icon = Icons.Rounded.Bolt,
            title = stringResource(R.string.lp_widget_power_draw),
            textColor = textColor,
            iconColor = ColorBrandPrimary,
            trailing = { StatusChip(b.status, ColorBrandPrimary) }
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "%.2f W".format(Locale.US, b.wattage),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
        Spacer(Modifier.height(4.dp))
        Text(b.level, style = MaterialTheme.typography.bodyMedium, color = subTextColor)
        Spacer(Modifier.height(12.dp))
        LiveCurrentChart(state.batteryHistory, ColorBrandPrimary, Modifier.fillMaxWidth().height(56.dp))
        Spacer(Modifier.height(14.dp))
        IntelligenceDivider(subTextColor)
        Spacer(Modifier.height(12.dp))
        IntelligenceMetricRow(stringResource(R.string.lp_widget_current), "${b.currentNow} mA", textColor, subTextColor)
        Spacer(Modifier.height(8.dp))
        IntelligenceMetricRow(stringResource(R.string.lp_widget_voltage), b.voltage, textColor, subTextColor)
        Spacer(Modifier.height(8.dp))
        IntelligenceMetricRow(stringResource(R.string.lp_widget_temperature), b.temp, textColor, subTextColor)
    }
}

// ------------------------------------------------------------------------------------
// UPTIME & DEEP SLEEP
// ------------------------------------------------------------------------------------
@Composable
fun SystemUptimeWidget(state: DashboardState, modifier: Modifier, containerColor: Color, textColor: Color, subTextColor: Color) {
    // Rasio dihitung ulang tiap kali string uptime berubah (state di-update tiap polling).
    val ratio = remember(state.uptime, state.deepSleepTime) {
        val elapsed = SystemClock.elapsedRealtime()
        if (elapsed > 0) ((elapsed - SystemClock.uptimeMillis()).toFloat() / elapsed).coerceIn(0f, 1f) else 0f
    }
    OSWidgetContainer(modifier, containerColor) {
        IntelligenceHeader(
            icon = Icons.Rounded.Schedule,
            title = stringResource(R.string.lp_widget_system_uptime),
            textColor = textColor,
            iconColor = ColorBrandPrimary
        )
        Spacer(Modifier.height(14.dp))
        Text(state.uptime, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = textColor)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.lp_widget_uptime), style = MaterialTheme.typography.bodyMedium, color = subTextColor)
        Spacer(Modifier.height(12.dp))
        IntelligenceMetricRow(stringResource(R.string.lp_widget_deep_sleep_ratio), "%.0f%%".format(Locale.US, ratio * 100f), textColor, subTextColor)
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(5.dp)),
            color = ColorBrandPrimary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        IntelligenceDivider(subTextColor)
        Spacer(Modifier.height(12.dp))
        IntelligenceMetricRow(stringResource(R.string.lp_widget_awake), state.awakeTime, textColor, subTextColor)
        Spacer(Modifier.height(8.dp))
        IntelligenceMetricRow(stringResource(R.string.lp_widget_deep_sleep), state.deepSleepTime, textColor, subTextColor)
    }
}

// ------------------------------------------------------------------------------------
// SYSTEM PRESSURE (PSI + load average)
// ------------------------------------------------------------------------------------
@Composable
fun SystemPressureWidget(state: DashboardState, modifier: Modifier, containerColor: Color, textColor: Color, subTextColor: Color) {
    val p = state.pressure
    OSWidgetContainer(modifier, containerColor) {
        IntelligenceHeader(
            icon = Icons.Rounded.Speed,
            title = stringResource(R.string.lp_widget_system_pressure),
            textColor = textColor,
            iconColor = ColorBrandPrimary
        )
        Spacer(Modifier.height(14.dp))
        if (p.supported) {
            PressureRow(stringResource(R.string.lp_widget_psi_cpu), p.cpuSome, textColor, subTextColor)
            Spacer(Modifier.height(12.dp))
            PressureRow(stringResource(R.string.lp_widget_psi_memory), p.memorySome, textColor, subTextColor)
            Spacer(Modifier.height(12.dp))
            PressureRow(stringResource(R.string.lp_widget_psi_io), p.ioSome, textColor, subTextColor)
        } else {
            Text(
                stringResource(R.string.lp_widget_psi_unsupported),
                style = MaterialTheme.typography.bodyMedium,
                color = subTextColor
            )
        }
        Spacer(Modifier.height(14.dp))
        IntelligenceDivider(subTextColor)
        Spacer(Modifier.height(12.dp))
        IntelligenceMetricRow(
            stringResource(R.string.lp_widget_load_average),
            "%.2f / %.2f / %.2f".format(Locale.US, p.load1, p.load5, p.load15),
            textColor, subTextColor
        )
    }
}

// ------------------------------------------------------------------------------------
// ROOT & SECURITY
// ------------------------------------------------------------------------------------
@Composable
fun RootSecurityWidget(state: DashboardState, modifier: Modifier, containerColor: Color, textColor: Color, subTextColor: Color) {
    val s = state.security
    val rootColor = if (s.rootAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    OSWidgetContainer(modifier, containerColor) {
        IntelligenceHeader(
            icon = Icons.Rounded.Security,
            title = stringResource(R.string.lp_widget_root_security),
            textColor = textColor,
            iconColor = ColorBrandPrimary,
            trailing = {
                StatusChip(
                    if (s.rootAvailable) stringResource(R.string.lp_widget_root_granted) else stringResource(R.string.lp_widget_root_unavailable),
                    rootColor
                )
            }
        )
        Spacer(Modifier.height(14.dp))
        IntelligenceMetricRow(stringResource(R.string.lp_widget_selinux), s.selinux, textColor, subTextColor)
        Spacer(Modifier.height(8.dp))
        IntelligenceMetricRow(stringResource(R.string.lp_widget_security_patch), s.securityPatch, textColor, subTextColor)
        Spacer(Modifier.height(8.dp))
        IntelligenceMetricRow(stringResource(R.string.lp_widget_verified_boot), s.verifiedBoot, textColor, subTextColor)
    }
}
