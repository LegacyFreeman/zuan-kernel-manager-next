/*
 * Copyright (c) 2026 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.gpu.adreno.tabs

import com.zuan.kernelmanager.next.ui.components.zkmGlassFallbackTint
import com.zuan.kernelmanager.next.ui.components.zkmCardHazeEffect
import com.zuan.kernelmanager.next.ui.components.zkmCardBorder
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.feature.gpu.adreno.viewmodel.AdrenoViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import com.zuan.kernelmanager.next.ui.components.ZkmHazeStyleCache
import com.zuan.kernelmanager.next.ui.theme.zkmIconShape
import com.zuan.kernelmanager.next.ui.theme.ZkmIconGlyph

@Composable
fun AdrenoThermalTab(
    state: AdrenoViewModel.ThermalState,
    hazeState: HazeState,
    cardColor: Color,
    isGlassActive: Boolean,
    accentColor: Color,
    contentPadding: PaddingValues = PaddingValues(top = 160.dp, bottom = 120.dp)
) {
    val bentoShape = RoundedCornerShape(28.dp)
    val glassModifier = if (isGlassActive) {
        Modifier
            .zkmCardHazeEffect(shape = bentoShape,
                state = hazeState,
                style = ZkmHazeStyleCache.of(
                    backgroundColor = cardColor,
                    blurRadius = 30.dp,
                    noiseFactor = 0.08f,
                    tints = emptyList()
                )
            )
            .zkmCardBorder(1.dp, Color.White.copy(alpha = 0.15f), bentoShape)
    } else Modifier.clip(bentoShape).background(cardColor)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            val tempCelsius = state.gpuTemp / 1000
            val color = when {
                tempCelsius > 80 -> MaterialTheme.colorScheme.error
                tempCelsius > 60 -> MaterialTheme.colorScheme.tertiary
                else -> accentColor
            }
            
            Surface(
                modifier = Modifier.fillMaxWidth().then(glassModifier),
                color = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else color.copy(alpha = 0.1f),
                shape = bentoShape
            ) {
                Row(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(stringResource(R.string.lp_gpu_temp), style = MaterialTheme.typography.titleMedium, color = color.copy(alpha = 0.8f))
                        Text("$tempCelsius°C", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold, color = color)
                    }
                    Box(modifier = Modifier.size(72.dp).clip(zkmIconShape(CircleShape)).background(color.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                        ZkmIconGlyph(icon = Icons.Default.Thermostat, tint = color, iconSize = 36.dp)
                    }
                }
            }
        }
        
        item {
            Text(
                "Thermal Zones & Cooling", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
            )
        }
        
        items(state.thermalZones) { zone ->
            val zoneTempC = zone.temp / 1000
            Surface(
                modifier = Modifier.fillMaxWidth().height(80.dp).then(glassModifier),
                color = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else cardColor,
                shape = bentoShape
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp).fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(zone.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text(zone.type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("$zoneTempC°C", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = accentColor)
                }
            }
        }
        
        items(state.coolingDevices) { dev ->
            Surface(
                modifier = Modifier.fillMaxWidth().height(80.dp).then(glassModifier),
                color = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else cardColor,
                shape = bentoShape
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp).fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(40.dp).clip(zkmIconShape(CircleShape)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                            ZkmIconGlyph(icon = Icons.Default.AcUnit, tint = MaterialTheme.colorScheme.onSecondaryContainer, iconSize = 20.dp)
                        }
                        Text(dev.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${dev.curState}/${dev.maxState}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = accentColor)
                        LinearProgressIndicator(
                            progress = { dev.curState.toFloat() / dev.maxState.coerceAtLeast(1) },
                            modifier = Modifier.width(60.dp).padding(top = 4.dp), color = accentColor, strokeCap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}
