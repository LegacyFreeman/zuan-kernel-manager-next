/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import java.util.Locale

/**
 * Mini Panel Profiler Performa (ZKM Perf Tahap 4).
 *
 * Komponen HUD elegan untuk memvalidasi dan memantau hasil optimasi performa secara nyata di perangkat:
 * - FPS & frame time real-time
 * - Tingkatan tier Quality Governor yang sedang aktif
 * - Jumlah frame drop / jank
 * - Status thermal headroom
 * - Status fast-scroll blur freeze
 * - Saklar cepat Mode Lite untuk pengujian perbandingan instan
 */
@Composable
fun ZkmPerformanceHudCard(
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    val metrics by ZkmQualityGovernor.metrics.collectAsState()
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val isDark = zkmIsDarkTheme()

    val fpsColor by animateColorAsState(
        when {
            metrics.currentFps >= 55f -> Color(0xFF00E676)
            metrics.currentFps >= 40f -> Color(0xFFFFD600)
            else -> Color(0xFFFF5252)
        },
        label = "fpsColor"
    )

    val tierBadgeColor = when (metrics.tier) {
        ZkmQualityTier.HIGH -> Color(0xFF00E676)
        ZkmQualityTier.BALANCED -> Color(0xFF00B0FF)
        ZkmQualityTier.LOW -> Color(0xFFFF9100)
    }

    val cardBg = if (isDark) Color(0xFF141418) else Color(0xFFF4F4F8)
    val cardBorder = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(20.dp))
            .clickable { expanded = !expanded }
            .padding(16.dp)
    ) {
        // Header Ringkas
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(fpsColor)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = String.format(Locale.US, "%.1f FPS", metrics.currentFps),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = String.format(Locale.US, "(%.1f ms)", metrics.frameTimeMs),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Tier Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(tierBadgeColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = metrics.tier.name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = tierBadgeColor
                )
            }
        }

        // Detail saat dibuka
        AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
            Column(modifier = Modifier.padding(top = 14.dp)) {
                HudMetricRow(
                    label = "Jank Frames (1s)",
                    value = "${metrics.jankFrames}",
                    highlight = metrics.jankFrames > 2
                )
                HudMetricRow(
                    label = "Thermal Status",
                    value = when (metrics.thermalStatus) {
                        0 -> "Normal"
                        1 -> "Light"
                        2 -> "Moderate"
                        else -> "Severe"
                    },
                    highlight = metrics.thermalStatus >= 2
                )
                HudMetricRow(
                    label = "Scroll Blur Freeze",
                    value = if (metrics.isScrollFrozen) "ACTIVE" else "Idle",
                    highlight = metrics.isScrollFrozen
                )
                HudMetricRow(
                    label = "Governor Blur Scale",
                    value = when (metrics.tier) {
                        ZkmQualityTier.HIGH -> "100% (Full)"
                        ZkmQualityTier.BALANCED -> "65% (Adaptive)"
                        ZkmQualityTier.LOW -> "30% (Minimal)"
                    }
                )
                HudMetricRow(
                    label = "Video Dec. Scale",
                    value = when (metrics.tier) {
                        ZkmQualityTier.HIGH -> "100%"
                        ZkmQualityTier.BALANCED -> "75% (540p)"
                        ZkmQualityTier.LOW -> "50% (360p)"
                    }
                )

                Spacer(Modifier.height(10.dp))

                // Toggle Cepat Mode Lite
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Mode Lite",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (ZkmLite.active) "Semua blur & video dimatikan" else "Mode Pro aktif",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = ZkmLite.active,
                        onCheckedChange = { ZkmLite.active = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00E676),
                            checkedTrackColor = Color(0xFF00E676).copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun HudMetricRow(
    label: String,
    value: String,
    highlight: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = if (highlight) Color(0xFFFF9100) else MaterialTheme.colorScheme.onSurface
        )
    }
}
