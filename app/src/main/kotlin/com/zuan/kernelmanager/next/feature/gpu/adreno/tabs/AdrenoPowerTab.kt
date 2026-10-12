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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.zuan.kernelmanager.next.ui.components.ZkmSwitch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdrenoPowerTab(
    state: AdrenoViewModel.PowerState,
    hazeState: HazeState,
    cardColor: Color,
    isGlassActive: Boolean,
    accentColor: Color,
    onToggleThrottling: (Boolean) -> Unit,
    onUpdateBoost: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(top = 160.dp, bottom = 120.dp),
) {
    val bentoShape = RoundedCornerShape(28.dp)
    val glassModifier =
        if (isGlassActive) {
            Modifier
                .zkmCardHazeEffect(shape = bentoShape,
                    state = hazeState,
                    style =
                        ZkmHazeStyleCache.of(
                            backgroundColor = cardColor,
                            blurRadius = 30.dp,
                            noiseFactor = 0.08f,
                            tints = emptyList(),
                        ),
                ).zkmCardBorder(1.dp, Color.White.copy(alpha = 0.15f), bentoShape)
        } else {
            Modifier.clip(bentoShape).background(cardColor)
        }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.hasGpuThrottling) {
            item {
                val isThrottling = state.gpuThrottling == "1"
                Surface(
                    modifier = Modifier.fillMaxWidth().height(100.dp).then(glassModifier),
                    color =
                        if (isGlassActive) {
                            Color.Transparent
                        } else if (isThrottling) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            cardColor
                        },
                    shape = bentoShape,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp).fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(
                                            48.dp,
                                        ).clip(zkmIconShape(CircleShape)).background(
                                            if (isThrottling) MaterialTheme.colorScheme.error else accentColor.copy(0.2f),
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                ZkmIconGlyph(
                                    icon = if (isThrottling) Icons.Default.Warning else Icons.Default.Bolt,
                                    tint = if (isThrottling) MaterialTheme.colorScheme.onError else accentColor,
                                    iconSize = 24.dp,
                                )
                            }
                            Column {
                                Text(
                                    stringResource(R.string.adreno_gpu_throttling),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isThrottling) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    if (isThrottling) "Performance Limited" else "Full Performance",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        ZkmSwitch(
                            checked = isThrottling,
                            onCheckedChange = onToggleThrottling,
                            colors =
                                SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.error,
                                    checkedTrackColor = MaterialTheme.colorScheme.errorContainer,
                                ),
                        )
                    }
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth().then(glassModifier),
                color = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else cardColor,
                shape = bentoShape,
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        stringResource(R.string.lp_adreno_boost),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Level ${state.adrenoBoost} - ${stringResource(R.string.adreno_boost_desc)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(20.dp))

                    val boostLevels = listOf("0" to "Off", "1" to "Low", "2" to "Medium", "3" to "High")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        boostLevels.forEach { (level, label) ->
                            val isSelected = state.adrenoBoost == level
                            Box(
                                modifier =
                                    Modifier
                                        .clip(
                                            CircleShape,
                                        ).background(if (isSelected) accentColor else MaterialTheme.colorScheme.surfaceContainerHighest)
                                        .clickable {
                                            onUpdateBoost(level)
                                        }.padding(horizontal = 20.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
