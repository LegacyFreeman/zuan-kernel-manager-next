/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.home.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.library.device.usage.UsageStatsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private data class DayUsage(val dayStart: Long, val ms: Long)
private data class AppUsage(val pkg: String, val label: String, val ms: Long)
private data class ScreenTimeData(val days: List<DayUsage>, val apps: List<AppUsage>)

private fun loadScreenTime(context: android.content.Context): ScreenTimeData {
    val now = System.currentTimeMillis()
    val days = (6 downTo 0).map { ago ->
        val start = UsageStatsHelper.startOfDay(ago)
        val end = if (ago == 0) now else UsageStatsHelper.startOfDay(ago - 1)
        DayUsage(start, UsageStatsHelper.screenOnMillis(context, start, end))
    }
    val apps = UsageStatsHelper.foregroundByPackage(context, UsageStatsHelper.startOfDay(0), now)
        .filter { it.value >= 30_000L }
        .entries
        .sortedByDescending { it.value }
        .take(15)
        .map { AppUsage(it.key, UsageStatsHelper.appLabel(context, it.key), it.value) }
    return ScreenTimeData(days, apps)
}

/** Screen Time: total layar hari ini, grafik 7 hari, dan aplikasi terlama dipakai. */
@Composable
fun ScreenTimeScreen(navController: NavController, hazeState: dev.chrisbanes.haze.HazeState) {
    val context = LocalContext.current
    val hasAccess = rememberUsageAccess()

    val data by produceState<ScreenTimeData?>(initialValue = null, hasAccess) {
        if (!hasAccess) {
            value = null
        } else {
            while (true) {
                value = withContext(Dispatchers.IO) { runCatching { loadScreenTime(context) }.getOrNull() }
                delay(30_000)
            }
        }
    }

    UsageScreenScaffold(title = stringResource(R.string.ext_screen_time), navController = navController, hazeState = hazeState, helpRoute = com.zuan.kernelmanager.next.ui.navigation.NavigationRoute.ScreenTime.route) {
        if (!hasAccess) {
            UsageAccessCard()
            return@UsageScreenScaffold
        }
        val d = data ?: return@UsageScreenScaffold

        val today = d.days.lastOrNull()?.ms ?: 0L
        val yesterday = d.days.getOrNull(d.days.size - 2)?.ms ?: 0L
        val diff = today - yesterday

        UsageCard {
            Text(
                stringResource(R.string.screen_time_today),
                style = MaterialTheme.typography.titleSmall,
                color = usageSubColor()
            )
            Text(
                formatUsageDuration(today),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            if (abs(diff) >= 60_000L) {
                Text(
                    stringResource(
                        if (diff < 0) R.string.screen_time_less else R.string.screen_time_more,
                        formatUsageDuration(abs(diff))
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = usageSubColor()
                )
            }
            Spacer(Modifier.height(16.dp))
            WeekBars(d.days)
        }

        UsageCard {
            Text(
                stringResource(R.string.screen_time_app_usage),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            if (d.apps.isEmpty()) {
                Text(
                    stringResource(R.string.screen_time_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = usageSubColor()
                )
            } else {
                val maxMs = d.apps.first().ms.coerceAtLeast(1L)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    d.apps.forEach { app -> AppUsageRow(app, maxMs) }
                }
            }
        }
    }
}

@Composable
private fun WeekBars(days: List<DayUsage>) {
    val maxMs = days.maxOfOrNull { it.ms }?.coerceAtLeast(1L) ?: 1L
    val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth().height(140.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        days.forEachIndexed { index, day ->
            val isToday = index == days.lastIndex
            val fraction = (day.ms.toFloat() / maxMs).coerceIn(0.03f, 1f)
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.62f)
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isToday) primary else primary.copy(alpha = 0.5f))
                    )
                }
                Text(
                    text = if (isToday) stringResource(R.string.screen_time_today_short)
                    else dayFormat.format(Date(day.dayStart)),
                    fontSize = 10.sp,
                    maxLines = 1,
                    color = usageSubColor(),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun AppUsageRow(app: AppUsage, maxMs: Long) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppIcon(app.pkg)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    app.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    formatUsageDuration(app.ms),
                    style = MaterialTheme.typography.bodySmall,
                    color = usageSubColor()
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { app.ms.toFloat() / maxMs },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                trackColor = usageTrackColor()
            )
        }
    }
}
