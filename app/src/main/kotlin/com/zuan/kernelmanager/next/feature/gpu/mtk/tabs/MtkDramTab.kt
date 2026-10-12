/*
 * Original code from: Rem01Gaming (origami_kernel_manager) and helloklf (vtools)
 * Modified and integrated by: Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.zuan.kernelmanager.next.feature.gpu.mtk.tabs

import com.zuan.kernelmanager.next.ui.components.zkmGlassFallbackTint
import com.zuan.kernelmanager.next.ui.components.zkmCardHazeEffect
import com.zuan.kernelmanager.next.ui.components.zkmCardBorder
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Memory
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
import com.zuan.kernelmanager.next.library.device.gpu.MtkUtils
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import com.zuan.kernelmanager.next.ui.components.ZkmHazeStyleCache
import com.zuan.kernelmanager.next.ui.theme.zkmIconShape
import com.zuan.kernelmanager.next.ui.theme.ZkmPaddedIcon

@Composable
fun MtkDramTab(
    state: MtkViewModel.DramState,
    hazeState: HazeState,
    cardColor: Color,
    isGlassActive: Boolean,
    accentColor: Color,
    onSetDramFreq: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(top = 160.dp, bottom = 400.dp),
) {
    val bentoShape = RoundedCornerShape(24.dp)
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
                ).zkmCardBorder(
                    width = 1.dp,
                    brush =
                        Brush.linearGradient(
                            colors = listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.05f)),
                        ),
                    shape = bentoShape,
                )
        } else {
            Modifier.clip(bentoShape)
        }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!state.isAvailable) {
            item {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Surface(
                            shape = zkmIconShape(RoundedCornerShape(24.dp)),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(80.dp),
                        ) {
                            ZkmPaddedIcon(imageVector = Icons.Default.Warning, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), padding = 20.dp)
                        }
                        Text(
                            stringResource(R.string.mtk_dram_unavailable),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${stringResource(R.string.mtk_dram_type)}: ${state.type}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                        if (state.type == MtkUtils.DramType.MMDVFS) {
                            Text(
                                "Path: /proc/mmdvfs/dump_setting",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }
            return@LazyColumn
        }

        item {
            Surface(
                shape = bentoShape,
                color = accentColor.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = zkmIconShape(RoundedCornerShape(16.dp)),
                        color = accentColor.copy(alpha = 0.2f),
                        modifier = Modifier.size(56.dp),
                    ) {
                        ZkmPaddedIcon(imageVector = Icons.Default.Memory, tint = accentColor, padding = 14.dp)
                    }
                    Column {
                        Text(
                            stringResource(R.string.mtk_dram_controller),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                        )
                        Text(
                            when (state.type) {
                                MtkUtils.DramType.MMDVFS -> stringResource(R.string.mtk_dram_mmdvfs)
                                MtkUtils.DramType.DEVFREQ -> stringResource(R.string.mtk_dram_devfreq)
                                MtkUtils.DramType.MTK_FLIPER -> stringResource(R.string.mtk_dram_fliper)
                                else -> stringResource(R.string.mtk_dram_unknown)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = accentColor.copy(alpha = 0.8f),
                        )
                        if (state.currentStep != "-1") {
                            Text(
                                "${stringResource(R.string.mtk_dram_current_step)}: ${state.currentStep}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = accentColor.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        }

        if (state.availableFreqs.isNotEmpty()) {
            item {
                Surface(
                    shape = bentoShape,
                    color = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else cardColor,
                    tonalElevation = if (isGlassActive) 0.dp else 2.dp,
                    modifier = Modifier.fillMaxWidth().then(if (isGlassActive) glassModifier else Modifier),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                shape = zkmIconShape(RoundedCornerShape(12.dp)),
                                color = accentColor.copy(alpha = 0.15f),
                                modifier = Modifier.size(40.dp),
                            ) {
                                ZkmPaddedIcon(imageVector = Icons.Default.Memory, tint = accentColor, padding = 8.dp)
                            }
                            Column {
                                Text(
                                    stringResource(R.string.mtk_dram_force_frequency),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    stringResource(R.string.mtk_dram_lock_opp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.availableFreqs.forEach { freq ->
                                val isSelected = state.freqMap[freq] == state.currentStep
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSetDramFreq(freq) },
                                    label = {
                                        Text(
                                            freq,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    },
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = accentColor,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                    modifier = Modifier.height(40.dp),
                                )
                            }
                        }

                        if (state.currentStep != "-1") {
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = { onSetDramFreq("-1") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(Icons.Default.AutoFixHigh, null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.mtk_dram_reset_dvfs), fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}
