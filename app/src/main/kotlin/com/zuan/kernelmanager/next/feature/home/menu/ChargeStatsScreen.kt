/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.home.menu

import android.content.Context
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.library.device.battery.BatteryHistory
import com.zuan.kernelmanager.next.library.device.battery.BatterySample
import com.zuan.kernelmanager.next.library.device.battery.BatterySampler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

private data class ChargeSession(
    val samples: List<BatterySample>,
    val startT: Long,
    val endT: Long,
    val startLevel: Int,
    val endLevel: Int,
    val energyWh: Float,
    val avgW: Float,
    val peakW: Float
) {
    val durationMs: Long get() = (endT - startT).coerceAtLeast(0L)
}

private const val SESSION_GAP_MS = 3 * 60_000L
private const val MAX_SAMPLE_GAP_MS = 60_000L

/** Kelompokkan sampel "sedang mengisi" yang berurutan menjadi sesi pengisian. */
private fun buildSessions(all: List<BatterySample>): List<ChargeSession> {
    val groups = ArrayList<MutableList<BatterySample>>()
    var current: MutableList<BatterySample>? = null
    for (s in all) {
        if (!s.charging) {
            current = null
            continue
        }
        val cur = current
        if (cur == null || s.t - cur.last().t > SESSION_GAP_MS) {
            val fresh = ArrayList<BatterySample>()
            groups.add(fresh)
            current = fresh
            fresh.add(s)
        } else {
            cur.add(s)
        }
    }
    return groups
        .filter { it.size >= 2 }
        .map { g ->
            var energyWs = 0.0 // watt * detik
            var peak = 0f
            g.forEachIndexed { i, s ->
                val next = g.getOrNull(i + 1)?.t ?: s.t
                val dt = (next - s.t).coerceIn(0L, MAX_SAMPLE_GAP_MS)
                energyWs += abs(s.watt) * (dt / 1000.0)
                if (abs(s.watt) > peak) peak = abs(s.watt)
            }
            val durSec = ((g.last().t - g.first().t) / 1000.0).coerceAtLeast(1.0)
            ChargeSession(
                samples = g,
                startT = g.first().t,
                endT = g.last().t,
                startLevel = g.first().level,
                endLevel = g.last().level,
                energyWh = (energyWs / 3600.0).toFloat(),
                avgW = (energyWs / durSec).toFloat(),
                peakW = peak
            )
        }
        .reversed() // terbaru dulu
}

private fun loadSessions(context: Context): List<ChargeSession> =
    buildSessions(BatteryHistory.snapshot(context))

/** Charge Stats: kondisi baterai live + riwayat sesi pengisian dengan grafik daya & level. */
@Composable
fun ChargeStatsScreen(navController: NavController, hazeState: dev.chrisbanes.haze.HazeState) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    var selectedStart by remember { mutableLongStateOf(-1L) }

    // Pembacaan live + ikut mengisi riwayat selagi layar terbuka.
    val live by produceState(initialValue = BatterySampler.read(context)) {
        while (true) {
            value = withContext(Dispatchers.IO) {
                runCatching { BatteryHistory.record(context) }
                BatterySampler.read(context)
            }
            delay(2_000)
        }
    }
    val sessions by produceState(initialValue = emptyList<ChargeSession>(), refreshKey) {
        while (true) {
            value = withContext(Dispatchers.IO) { runCatching { loadSessions(context) }.getOrDefault(emptyList()) }
            delay(10_000)
        }
    }

    UsageScreenScaffold(
        hazeState = hazeState,
        title = stringResource(R.string.ext_charge_stats),
        navController = navController,
        helpRoute = com.zuan.kernelmanager.next.ui.navigation.NavigationRoute.ChargeStats.route,
        actions = {
            IconButton(onClick = {
                BatteryHistory.clear(context)
                selectedStart = -1L
                refreshKey++
            }) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.battery_usage_clear))
            }
        }
    ) {
        LiveHeader(live)

        val selected = sessions.firstOrNull { it.startT == selectedStart } ?: sessions.firstOrNull()
        if (selected == null) {
            UsageCard {
                Text(
                    stringResource(R.string.charge_stats_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = usageSubColor()
                )
            }
        } else {
            UsageCard {
                SessionSummary(selected)
            }
            ChartCard(selected)

            if (sessions.size > 1) {
                UsageCard {
                    Text(
                        stringResource(R.string.charge_stats_sessions),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        sessions.take(20).forEach { session ->
                            SessionRow(
                                session = session,
                                isSelected = session.startT == selected.startT,
                                onClick = { selectedStart = session.startT }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveHeader(r: com.zuan.kernelmanager.next.library.device.battery.BatteryReading) {
    val ringColor = MaterialTheme.colorScheme.primary
    val track = usageTrackColor()
    val state = when (r.status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> stringResource(R.string.charge_stats_state_charging)
        BatteryManager.BATTERY_STATUS_FULL -> stringResource(R.string.charge_stats_state_full)
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> stringResource(R.string.charge_stats_state_not_charging)
        else -> stringResource(R.string.charge_stats_state_discharging)
    }
    UsageCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(modifier = Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(110.dp)) {
                    val stroke = 12.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    drawArc(
                        ringColor, -90f, 360f * r.level / 100f, false, Offset(inset, inset), arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
                Text("${r.level}%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(state, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    String.format(Locale.US, "%+.2fW", r.watt),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    String.format(
                        Locale.US, "%+.0f mA · %.2f V · %.1f°C", r.currentMa, r.voltageV, r.tempC
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = usageSubColor()
                )
            }
        }
    }
}

private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd  HH:mm", Locale.getDefault())
private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

@Composable
private fun SessionSummary(s: ChargeSession) {
    Text(
        "${dateTimeFormat.format(Date(s.startT))} ~ ${timeFormat.format(Date(s.endT))}",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(12.dp))
    Row(modifier = Modifier.fillMaxWidth()) {
        StatCell(formatUsageDuration(s.durationMs), stringResource(R.string.charge_stats_duration), Modifier.weight(1f))
        StatCell("${s.startLevel}% → ${s.endLevel}%", "Δ ${s.endLevel - s.startLevel}%", Modifier.weight(1f))
        StatCell(String.format(Locale.US, "%.2fWh", s.energyWh), stringResource(R.string.charge_stats_energy), Modifier.weight(1f))
        StatCell(
            String.format(Locale.US, "%.1fW", s.avgW),
            stringResource(R.string.charge_stats_avg_peak, String.format(Locale.US, "%.1fW", s.peakW)),
            Modifier.weight(1f)
        )
    }
}

@Composable
private fun ChartCard(s: ChargeSession) {
    val first = s.startT
    val durMin = (s.durationMs / 60_000f).coerceAtLeast(1f)
    val xLabels = timeAxisLabels(s.durationMs)

    val maxW = ceil((s.peakW.coerceAtLeast(1f)) / 5f) * 5f
    val mid = maxW / 2f

    UsageCard {
        Text(
            stringResource(R.string.charge_stats_power_chart),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        SimpleLineChart(
            points = s.samples.map { ((it.t - first) / 60_000f) to abs(it.watt) },
            xMin = 0f, xMax = durMin, yMin = 0f, yMax = maxW,
            yLabels = listOf(
                String.format(Locale.US, "%.0fW", maxW),
                String.format(Locale.US, "%.1fW", mid),
                "0W"
            ),
            xLabels = xLabels,
            lineColor = MaterialTheme.colorScheme.primary
        )
    }
    UsageCard {
        Text(
            stringResource(R.string.charge_stats_level_chart),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        SimpleLineChart(
            points = s.samples.map { ((it.t - first) / 60_000f) to it.level.toFloat() },
            xMin = 0f, xMax = durMin, yMin = 0f, yMax = 100f,
            yLabels = listOf("100%", "75%", "50%", "25%", "0%"),
            xLabels = xLabels,
            lineColor = MaterialTheme.colorScheme.tertiary
        )
    }
}

@Composable
private fun SessionRow(session: ChargeSession, isSelected: Boolean, onClick: () -> Unit) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${dateTimeFormat.format(Date(session.startT))} ~ ${timeFormat.format(Date(session.endT))}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                "${formatUsageDuration(session.durationMs)}  ·  " +
                    String.format(Locale.US, "%.2fWh", session.energyWh),
                fontSize = 12.sp,
                color = usageSubColor()
            )
        }
        Text(
            "${session.startLevel}% → ${session.endLevel}%",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
    }
}
