/*
 * Original code from: Rem01Gaming (origami_kernel_manager) and helloklf (vtools)
 * Modified and integrated by: Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.gpu.mtk.tabs

import com.zuan.kernelmanager.next.ui.components.zkmGlassFallbackTint
import com.zuan.kernelmanager.next.ui.components.zkmCardHazeEffect
import com.zuan.kernelmanager.next.ui.components.zkmCardBorder
import androidx.compose.animation.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.feature.gpu.mtk.viewmodel.MtkViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import com.zuan.kernelmanager.next.ui.components.zkmHazeEffect
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import com.zuan.kernelmanager.next.ui.components.ZkmHazeStyleCache
import com.zuan.kernelmanager.next.ui.theme.zkmIconShape
import com.zuan.kernelmanager.next.ui.theme.ZkmPaddedIcon
import com.zuan.kernelmanager.next.ui.components.ZkmSwitch

@Composable
fun MtkThermalTab(
    state: MtkViewModel.ThermalState,
    hazeState: HazeState,
    cardColor: Color,
    isGlassActive: Boolean,
    accentColor: Color,
    onToggleEara: (Boolean) -> Unit = {},
    onToggleEaraFake: (Boolean) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(top = 160.dp, bottom = 400.dp)
) {
    val bentoShape = RoundedCornerShape(24.dp)
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
            .zkmCardBorder(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.05f))
                ),
                shape = bentoShape
            )
    } else Modifier.clip(bentoShape)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            val tempCelsius = state.gpuTemp / 1000
            val color = when {
                tempCelsius > 80 -> MaterialTheme.colorScheme.errorContainer
                tempCelsius > 60 -> MaterialTheme.colorScheme.tertiaryContainer
                else -> accentColor.copy(alpha = 0.15f)
            }
            val contentColor = when {
                tempCelsius > 80 -> MaterialTheme.colorScheme.onErrorContainer
                tempCelsius > 60 -> MaterialTheme.colorScheme.onTertiaryContainer
                else -> accentColor
            }
            
            Surface(
                shape = bentoShape,
                color = color,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedVisibility(
                        visible = state.isThrottling,
                        enter = fadeIn() + expandVertically()
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.error,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onError
                                )
                                Text(
                                    stringResource(R.string.mtk_thermal_throttling),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onError
                                )
                            }
                        }
                    }
                    
                    Surface(
                        shape = zkmIconShape(RoundedCornerShape(20.dp)),
                        color = contentColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(64.dp)
                    ) {
                        ZkmPaddedIcon(imageVector = Icons.Default.Thermostat, tint = contentColor, padding = 16.dp)
                    }
                    
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "$tempCelsius°C",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = contentColor
                    )
                    Text(
                        text = if (state.isThrottling) stringResource(R.string.mtk_temp_limit_exceeded) 
                               else stringResource(R.string.mtk_normal_temp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                }
            }
        }
        
        if (state.hasEara) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else cardColor,
                    tonalElevation = if (isGlassActive) 0.dp else 2.dp,
                    modifier = Modifier.fillMaxWidth().then(if (isGlassActive) glassModifier else Modifier)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.mtk_eara_thermal),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                stringResource(R.string.mtk_early_adaptation),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        ZkmSwitch(
                            checked = state.earaEnabled,
                            onCheckedChange = onToggleEara,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = accentColor,
                                checkedTrackColor = accentColor.copy(alpha = 0.4f)
                            )
                        )
                    }
                }
            }
            
            if (state.earaEnabled) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.mtk_eara_fake_throttle),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    stringResource(R.string.mtk_fake_temp_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                )
                            }
                            ZkmSwitch(
                                checked = state.earaFakeThrottle,
                                onCheckedChange = onToggleEaraFake,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onErrorContainer,
                                    checkedTrackColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                }
            }
        }
        
        if (state.isThrottling) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            stringResource(R.string.mtk_throttling_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else cardColor,
                tonalElevation = if (isGlassActive) 0.dp else 2.dp,
                modifier = Modifier.fillMaxWidth().then(if (isGlassActive) glassModifier else Modifier)
            ) {
                Text(
                    stringResource(R.string.mtk_thermal_daemon_info),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp)
                )
            }
        }
    }
}
