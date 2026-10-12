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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.feature.gpu.mtk.viewmodel.MtkViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import com.zuan.kernelmanager.next.ui.components.ZkmHazeStyleCache
import com.zuan.kernelmanager.next.ui.theme.zkmIconShape
import com.zuan.kernelmanager.next.ui.theme.ZkmPaddedIcon

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MtkCpuTab(
    state: MtkViewModel.CpuMiscState,
    hazeState: HazeState,
    cardColor: Color,
    isGlassActive: Boolean,
    accentColor: Color,
    onSetCci: (String) -> Unit,
    onSetPowerMode: (String) -> Unit,
    onSetEemOffset: (String, String) -> Unit,
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
                Surface(
                    shape = bentoShape,
                    color = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else cardColor,
                    tonalElevation = if (isGlassActive) 0.dp else 2.dp,
                    modifier = Modifier.fillMaxWidth().then(if (isGlassActive) glassModifier else Modifier),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Surface(
                            shape = zkmIconShape(RoundedCornerShape(20.dp)),
                            color = accentColor.copy(alpha = 0.15f),
                            modifier = Modifier.size(64.dp),
                        ) {
                            ZkmPaddedIcon(imageVector = Icons.Default.Memory, tint = accentColor, padding = 16.dp)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Generic CPU Managed",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "This device uses a modern kernel (GKI). MediaTek CPU governor and frequency controls are automatically managed by the standard Linux subsystem.\n\nPlease use the main CPU Dashboard menu to configure the governor.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        } else {
            item {
                Surface(
                    shape = bentoShape,
                    color = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else cardColor,
                    tonalElevation = if (isGlassActive) 0.dp else 2.dp,
                    modifier = Modifier.fillMaxWidth().then(if (isGlassActive) glassModifier else Modifier),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "CPU Power Settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(16.dp))

                        Text(
                            "CCI Mode",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.cciMode == "0",
                                onClick = { onSetCci("0") },
                                label = { Text(stringResource(R.string.lp_normal)) },
                                colors =
                                    FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accentColor,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                            )
                            FilterChip(
                                selected = state.cciMode == "1",
                                onClick = { onSetCci("1") },
                                label = { Text(stringResource(R.string.lp_performance)) },
                                colors =
                                    FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accentColor,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 14.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        )

                        Text(
                            "Power Mode",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf("0" to "Normal", "1" to "Low Power", "2" to "Balance", "3" to "Performance").forEach { (mode, label) ->
                                FilterChip(
                                    selected = state.powerMode == mode,
                                    onClick = { onSetPowerMode(mode) },
                                    label = { Text(label) },
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = accentColor,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                )
                            }
                        }
                    }
                }
            }

            if (state.eemOffsets.isNotEmpty()) {
                item {
                    Text(
                        "EEM Voltage Offsets",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                items(state.eemOffsets) { (detName, currentOffset) ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else cardColor,
                        tonalElevation = if (isGlassActive) 0.dp else 2.dp,
                        modifier = Modifier.fillMaxWidth().then(if (isGlassActive) glassModifier else Modifier),
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ElectricBolt, contentDescription = null, tint = accentColor)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    detName.replace("EEM_DET_", "CPU "),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Spacer(Modifier.height(14.dp))

                            var input by remember(detName) { mutableStateOf(currentOffset) }
                            OutlinedTextField(
                                value = input,
                                onValueChange = { input = it },
                                label = { Text(stringResource(R.string.mtk_offset_value)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                trailingIcon = {
                                    FilledIconButton(
                                        onClick = { onSetEemOffset(detName, input) },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Icon(Icons.Default.CheckCircle, null)
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}
