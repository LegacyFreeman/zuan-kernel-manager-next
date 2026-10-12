/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.feature.activitylauncher.FloatingActivityService
import com.zuan.kernelmanager.next.feature.process.FloatingProcessService
import com.zuan.kernelmanager.next.feature.terminal.FloatingTerminalService
import com.zuan.kernelmanager.next.library.service.FpsOverlayController
import com.zuan.kernelmanager.next.service.BatteryMonitorService
import com.zuan.kernelmanager.next.ui.navigation.toggleService
import kotlinx.coroutines.delay

/**
 * =====================================================================================
 * QUICK TOOLS — isi dialog (dipakai bersama oleh FAB Overall & hybrid tabs)
 *
 * Dibuat ringkas: tiap tool jadi satu tile (ikon + nama), 2 kolom, tanpa subtitle, dengan
 * tombol tutup di header. Tinggi total sekitar separuh dialog lama dan tetap bisa di-scroll
 * kalau layar kecil. Tile menyala = tool sedang berjalan; status dibaca ulang tiap 0,5 dtk
 * supaya ikut mati kalau jendela overlay ditutup dengan long-press.
 *
 *  - Floating : jendela melayang (butuh izin overlay)
 *  - Notification : notifikasi realtime (Live Stats, Battery Monitor)
 * =====================================================================================
 */
private enum class ToolId {
    TERMINAL, FPS, PROCESS, ACTIVITY, SYSTEM, MINI, THREAD, TEMP, LIVE_STATS, BATTERY_MONITOR
}

private class ToolSpec(
    val id: ToolId,
    val icon: ImageVector,
    @StringRes val titleRes: Int,
    val running: () -> Boolean
)

private val floatingTools = listOf(
    ToolSpec(ToolId.TERMINAL, Icons.Rounded.Terminal, R.string.tool_terminal_title) { FloatingTerminalService.isRunning },
    ToolSpec(ToolId.FPS, Icons.Rounded.Speed, R.string.tool_fps_title) { FpsOverlayController.isRunning.value },
    ToolSpec(ToolId.PROCESS, Icons.Rounded.Memory, R.string.tool_process_title) { FloatingProcessService.isRunning },
    ToolSpec(ToolId.ACTIVITY, Icons.Rounded.Extension, R.string.tool_activity_title) { FloatingActivityService.isRunning },
    ToolSpec(ToolId.SYSTEM, Icons.Rounded.MonitorHeart, R.string.tool_system_monitor_title) { SystemMonitorService.isRunning },
    ToolSpec(ToolId.MINI, Icons.Rounded.BarChart, R.string.tool_mini_monitor_title) { MiniMonitorService.isRunning },
    ToolSpec(ToolId.THREAD, Icons.Rounded.AccountTree, R.string.tool_thread_monitor_title) { ThreadMonitorService.isRunning },
    ToolSpec(ToolId.TEMP, Icons.Rounded.Thermostat, R.string.tool_temp_monitor_title) { TemperatureMonitorService.isRunning }
)

private val notificationTools = listOf(
    ToolSpec(ToolId.LIVE_STATS, Icons.Rounded.NotificationsActive, R.string.tool_live_stats_title) { LiveStatsNotificationService.isRunning },
    ToolSpec(ToolId.BATTERY_MONITOR, Icons.Rounded.BatteryChargingFull, R.string.tool_battery_monitor_title) { BatteryMonitorService.isRunning }
)

/** Urutan style FPS di UI (Android, Mini, PC) -> nilai STYLE untuk FpsOverlayController. */
private val fpsStyleValues = listOf(0, 2, 1)

@Composable
fun QuickToolsContent(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val maxHeight = com.zuan.kernelmanager.next.ui.adaptive.zkmDialogMaxHeight(0.72f)

    val states = remember { mutableStateMapOf<ToolId, Boolean>() }
    val holdUntil = remember { HashMap<ToolId, Long>() }
    var fpsStyle by remember { mutableIntStateOf(1) } // default: Mini

    LaunchedEffect(Unit) {
        while (true) {
            val now = SystemClock.uptimeMillis()
            (floatingTools + notificationTools).forEach { spec ->
                // Beri waktu service untuk benar-benar hidup setelah ditekan.
                if (now >= (holdUntil[spec.id] ?: 0L)) {
                    states[spec.id] = runCatching { spec.running() }.getOrDefault(false)
                }
            }
            delay(500)
        }
    }

    fun onToggle(id: ToolId, enable: Boolean) {
        holdUntil[id] = SystemClock.uptimeMillis() + 1500L
        states[id] = enable
        applyToggle(context, id, enable, fpsStyleValues[fpsStyle])
    }

    Column(
        modifier = modifier
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.dialog_tools_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_done),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionLabel(stringResource(R.string.quick_tools_section_floating))
        ToolGrid(floatingTools, states) { id, enable -> onToggle(id, enable) }

        AnimatedVisibility(visible = states[ToolId.FPS] == true) {
            FpsStyleRow(selected = fpsStyle) { index ->
                fpsStyle = index
                FpsOverlayController.start(context, mapOf("STYLE" to fpsStyleValues[index]))
            }
        }

        Spacer(modifier = Modifier.size(4.dp))
        SectionLabel(stringResource(R.string.quick_tools_section_notification))
        ToolGrid(notificationTools, states) { id, enable -> onToggle(id, enable) }

        Text(
            text = stringResource(R.string.quick_tools_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )
    }
}

private fun applyToggle(context: Context, id: ToolId, enable: Boolean, fpsStyleValue: Int) {
    when (id) {
        ToolId.TERMINAL -> toggleService(context, FloatingTerminalService::class.java, enable)
        ToolId.PROCESS -> toggleService(context, FloatingProcessService::class.java, enable)
        ToolId.ACTIVITY -> toggleService(context, FloatingActivityService::class.java, enable)
        ToolId.SYSTEM -> toggleService(context, SystemMonitorService::class.java, enable)
        ToolId.MINI -> toggleService(context, MiniMonitorService::class.java, enable)
        ToolId.THREAD -> toggleService(context, ThreadMonitorService::class.java, enable)
        ToolId.TEMP -> toggleService(context, TemperatureMonitorService::class.java, enable)
        ToolId.FPS -> {
            if (!enable) {
                FpsOverlayController.stop(context)
            } else if (!Settings.canDrawOverlays(context)) {
                Toast.makeText(context, context.getString(R.string.overlay_permission_required), Toast.LENGTH_SHORT).show()
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } else {
                FpsOverlayController.start(context, mapOf("STYLE" to fpsStyleValue))
            }
        }
        // Notifikasi tidak butuh izin overlay.
        ToolId.LIVE_STATS ->
            if (enable) LiveStatsNotificationService.start(context) else LiveStatsNotificationService.stop(context)
        ToolId.BATTERY_MONITOR -> BatteryMonitorService.setEnabled(context, enable)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
    )
}

@Composable
private fun ToolGrid(
    tools: List<ToolSpec>,
    states: Map<ToolId, Boolean>,
    onToggle: (ToolId, Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tools.chunked(2).forEach { rowTools ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowTools.forEach { spec ->
                    ToolTile(
                        icon = spec.icon,
                        title = stringResource(spec.titleRes),
                        checked = states[spec.id] == true,
                        onToggle = { onToggle(spec.id, it) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowTools.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ToolTile(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (checked) scheme.primaryContainer else scheme.surfaceContainerHighest,
        label = "ToolTileContainer"
    )
    val content = if (checked) scheme.onPrimaryContainer else scheme.onSurface
    Row(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (checked) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = content,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FpsStyleRow(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf(
        stringResource(R.string.fps_style_android),
        stringResource(R.string.fps_style_mini),
        stringResource(R.string.fps_style_pc)
    )
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selected
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) scheme.onPrimary else scheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) scheme.primary else scheme.surfaceContainerHigh)
                    .toggleable(value = isSelected, role = Role.RadioButton, onValueChange = { onSelect(index) })
                    .padding(vertical = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
