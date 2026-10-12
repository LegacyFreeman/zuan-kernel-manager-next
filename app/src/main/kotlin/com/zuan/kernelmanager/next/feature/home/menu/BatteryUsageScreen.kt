/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.home.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.library.device.battery.BatteryHistory
import com.zuan.kernelmanager.next.library.device.battery.BatterySample
import com.zuan.kernelmanager.next.library.device.battery.BatterySampler
import com.zuan.kernelmanager.next.library.device.usage.UsageStatsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

private data class UsageScene(
    val pkg: String,
    val label: String,
    val ms: Long,
    val avgW: Float,
    val maxTempC: Float
)

private data class BatteryUsageData(
    val window: List<BatterySample>,
    val durationMs: Long,
    val drainPct: Int,
    val avgDischargeW: Float,
    val screenOnMs: Long,
    val runtimeHours: Float?,
    val scenes: List<UsageScene>
)

private const val MAX_SAMPLE_GAP_MS = 60_000L

private fun loadBatteryUsage(context: android.content.Context): BatteryUsageData {
    val all = BatteryHistory.snapshot(context)
    // Jendela = sejak terakhir kali mengisi / penuh.
    val lastFull = all.indexOfLast { it.charging || it.level >= 100 }
    val window = if (lastFull >= 0) all.subList(lastFull, all.size) else all
    if (window.size < 2) return BatteryUsageData(window, 0L, 0, 0f, 0L, null, emptyList())

    val now = System.currentTimeMillis()
    val durationMs = (window.last().t - window.first().t).coerceAtLeast(0L)
    val drain = (window.first().level - window.last().level).coerceAtLeast(0)

    var dischargeWeight = 0L
    var dischargeEnergy = 0.0
    var screenOn = 0L

    class Acc { var ms = 0L; var energy = 0.0; var maxTemp = 0f }
    val groups = HashMap<String, Acc>()
    val offKey = "\u0000off"

    window.forEachIndexed { i, s ->
        val next = window.getOrNull(i + 1)?.t ?: now
        val dt = (next - s.t).coerceIn(0L, MAX_SAMPLE_GAP_MS)
        if (dt <= 0L) return@forEachIndexed
        if (!s.charging && s.watt < 0f) {
            dischargeWeight += dt
            dischargeEnergy += abs(s.watt) * dt.toDouble()
        }
        if (s.screenOn) screenOn += dt
        val key = if (!s.screenOn) offKey else s.pkg
        val acc = groups.getOrPut(key) { Acc() }
        acc.ms += dt
        if (!s.charging) acc.energy += abs(s.watt) * dt.toDouble()
        if (s.tempC > acc.maxTemp) acc.maxTemp = s.tempC
    }

    val avgW = if (dischargeWeight > 0) (dischargeEnergy / dischargeWeight).toFloat() else 0f
    val durationHours = durationMs / 3_600_000f
    val runtime = if (drain >= 2 && durationHours > 0f) 100f / (drain / durationHours) else null

    val scenes = groups.entries
        .map { (key, acc) ->
            val label = when {
                key == offKey -> ""
                key.isEmpty() -> ""
                else -> UsageStatsHelper.appLabel(context, key)
            }
            UsageScene(
                pkg = key,
                label = label,
                ms = acc.ms,
                avgW = if (acc.ms > 0) (acc.energy / acc.ms).toFloat() else 0f,
                maxTempC = acc.maxTemp
            )
        }
        .filter { it.ms >= 30_000L }
        .sortedByDescending { it.ms }
        .take(20)

    return BatteryUsageData(window, durationMs, drain, avgW, screenOn, runtime, scenes)
}

/** Battery Usage: pemakaian sejak terakhir penuh + skenario pemakaian per aplikasi. */
@Composable
fun BatteryUsageScreen(navController: NavController, hazeState: dev.chrisbanes.haze.HazeState) {
    val context = LocalContext.current
    val hasAccess = rememberUsageAccess()
    var refreshKey by remember { mutableIntStateOf(0) }

    // Selagi layar ini terbuka, ikut mengisi riwayat (dibatasi 15 dtk di dalam record()).
    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.IO) { runCatching { BatteryHistory.record(context) } }
            delay(5_000)
        }
    }

    val data by produceState<BatteryUsageData?>(initialValue = null, refreshKey, hasAccess) {
        while (true) {
            value = withContext(Dispatchers.IO) { runCatching { loadBatteryUsage(context) }.getOrNull() }
            delay(15_000)
        }
    }
    val live by produceState(initialValue = BatterySampler.read(context)) {
        while (true) {
            value = withContext(Dispatchers.IO) { BatterySampler.read(context) }
            delay(2_000)
        }
    }

    UsageScreenScaffold(
        hazeState = hazeState,
        title = stringResource(R.string.ext_battery_usage),
        navController = navController,
        helpRoute = com.zuan.kernelmanager.next.ui.navigation.NavigationRoute.BatteryUsage.route,
        actions = {
            IconButton(onClick = {
                BatteryHistory.clear(context)
                refreshKey++
            }) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.battery_usage_clear))
            }
        }
    ) {
        if (!hasAccess) UsageAccessCard()

        val d = data
        UsageCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.battery_usage_since_full),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${live.level}%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(12.dp))

            if (d == null || d.window.size < 2) {
                Text(
                    stringResource(R.string.battery_usage_need_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = usageSubColor()
                )
            } else {
                val first = d.window.first().t
                SimpleLineChart(
                    points = d.window.map { ((it.t - first) / 60_000f) to it.level.toFloat() },
                    xMin = 0f,
                    xMax = (d.durationMs / 60_000f).coerceAtLeast(1f),
                    yMin = 0f,
                    yMax = 100f,
                    yLabels = listOf("100%", "75%", "50%", "25%", "0%"),
                    xLabels = timeAxisLabels(d.durationMs),
                    lineColor = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCell(
                        String.format(Locale.US, "%.2fW", d.avgDischargeW),
                        stringResource(R.string.battery_usage_avg_power),
                        Modifier.weight(1f)
                    )
                    StatCell(
                        formatUsageDuration(d.screenOnMs),
                        stringResource(R.string.battery_usage_screen_on),
                        Modifier.weight(1f)
                    )
                    StatCell(
                        d.runtimeHours?.let { formatUsageDuration((it * 3_600_000f).toLong()) } ?: "--",
                        stringResource(R.string.battery_usage_runtime),
                        Modifier.weight(1f)
                    )
                    StatCell(
                        String.format(Locale.US, "%.1f°C", live.tempC),
                        stringResource(R.string.battery_usage_temp),
                        Modifier.weight(1f)
                    )
                }
            }
        }

        if (d != null && d.scenes.isNotEmpty()) {
            UsageCard {
                Text(
                    stringResource(R.string.battery_usage_scenes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    d.scenes.forEach { SceneRow(it) }
                }
            }
        }
    }
}

@Composable
private fun SceneRow(scene: UsageScene) {
    val isOff = scene.pkg == "\u0000off"
    val isUnknown = !isOff && scene.pkg.isEmpty()
    val name = when {
        isOff -> stringResource(R.string.battery_usage_screen_off)
        isUnknown -> stringResource(R.string.battery_usage_unknown)
        else -> scene.label
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (isOff || isUnknown) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(usageTrackColor())
            )
        } else {
            AppIcon(scene.pkg)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                String.format(Locale.US, "AVG: %.2fW   MAX: %.0f°C", scene.avgW, scene.maxTempC),
                style = MaterialTheme.typography.bodySmall,
                color = usageSubColor()
            )
        }
        Text(
            formatUsageDuration(scene.ms),
            style = MaterialTheme.typography.bodySmall,
            color = usageSubColor()
        )
    }
}
