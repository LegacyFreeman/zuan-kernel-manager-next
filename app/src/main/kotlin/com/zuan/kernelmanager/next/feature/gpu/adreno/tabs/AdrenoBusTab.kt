/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.zuan.kernelmanager.next.feature.gpu.adreno.tabs

import com.zuan.kernelmanager.next.ui.components.zkmGlassFallbackTint
import com.zuan.kernelmanager.next.ui.components.zkmCardHazeEffect
import com.zuan.kernelmanager.next.ui.components.zkmCardBorder
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.feature.gpu.adreno.viewmodel.AdrenoViewModel
import com.zuan.kernelmanager.next.ui.components.BlurredAlertDialog
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import com.zuan.kernelmanager.next.ui.components.ZkmHazeStyleCache
import com.zuan.kernelmanager.next.ui.theme.zkmIconShape
import com.zuan.kernelmanager.next.ui.theme.ZkmPaddedIcon

@Composable
fun AdrenoBusTab(
    state: AdrenoViewModel.BusDcvsState,
    hazeState: HazeState,
    cardColor: Color,
    isGlassActive: Boolean,
    accentColor: Color,
    onUpdateBusFreq: (String, String, String) -> Unit,
    onUpdateDevfreq: (String, String, String) -> Unit,
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
    } else {
        Modifier.clip(bentoShape)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // UFSHC Card
        if (state.ufshc.isAvailable) {
            item {
                DevfreqExpressiveCard(
                    devfreq = state.ufshc,
                    icon = Icons.Default.Storage,
                    color = accentColor.copy(alpha = 0.15f),
                    contentColor = accentColor,
                    cardColor = cardColor,
                    isGlassActive = isGlassActive,
                    glassModifier = glassModifier,
                    onUpdate = onUpdateDevfreq
                )
            }
        }

        // BUSMON Card
        if (state.busmon.isAvailable) {
            item {
                DevfreqExpressiveCard(
                    devfreq = state.busmon,
                    icon = Icons.Default.Speed,
                    color = accentColor.copy(alpha = 0.15f),
                    contentColor = accentColor,
                    cardColor = cardColor,
                    isGlassActive = isGlassActive,
                    glassModifier = glassModifier,
                    onUpdate = onUpdateDevfreq
                )
            }
        }

        // Bus DCVS Header
        if (state.hasBusDcvs && state.busComponents.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.adreno_bus_components),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp)
                )
            }
            
            items(state.busComponents, key = { it.name }) { bus ->
                BusComponentExpressiveCard(
                    bus = bus,
                    cardColor = cardColor,
                    isGlassActive = isGlassActive,
                    glassModifier = glassModifier,
                    onUpdateMin = { freq -> onUpdateBusFreq(bus.name, "min", freq) },
                    onUpdateMax = { freq -> onUpdateBusFreq(bus.name, "max", freq) }
                )
            }
        }
        
        if (!state.hasBusDcvs && !state.ufshc.isAvailable && !state.busmon.isAvailable) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = zkmIconShape(RoundedCornerShape(24.dp)),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(80.dp)
                        ) {
                            ZkmPaddedIcon(imageVector = Icons.Default.Memory, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), padding = 20.dp)
                        }
                        Text(
                            stringResource(R.string.adreno_no_bus_controls),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            stringResource(R.string.adreno_no_bus_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevfreqExpressiveCard(
    devfreq: AdrenoViewModel.DevfreqState,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    contentColor: Color,
    cardColor: Color,
    isGlassActive: Boolean,
    glassModifier: Modifier,
    onUpdate: (String, String, String) -> Unit
) {
    var showMinDialog by remember { mutableStateOf(false) }
    var showMaxDialog by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else cardColor,
        tonalElevation = if (isGlassActive) 0.dp else 2.dp,
        modifier = Modifier.fillMaxWidth().then(if (isGlassActive) glassModifier else Modifier)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = zkmIconShape(RoundedCornerShape(16.dp)),
                    color = color,
                    modifier = Modifier.size(48.dp)
                ) {
                    ZkmPaddedIcon(imageVector = icon, tint = contentColor, padding = 12.dp)
                }
                Column {
                    Text(
                        devfreq.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        devfreq.path,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            if (devfreq.availableGovs.isNotEmpty()) {
                Text(
                    stringResource(R.string.adreno_governor),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                SingleChoiceSegmentedButtonRow {
                    devfreq.availableGovs.take(3).forEachIndexed { index, gov ->
                        SegmentedButton(
                            selected = devfreq.governor == gov,
                            onClick = { onUpdate(devfreq.path, "governor", gov) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = minOf(3, devfreq.availableGovs.size)
                            )
                        ) { 
                            Text(
                                gov.take(4),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (devfreq.governor == gov) FontWeight.Bold else FontWeight.Normal
                            ) 
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            FreqEditRow(
                label = stringResource(R.string.adreno_min_freq),
                value = devfreq.minFreq,
                onClick = { showMinDialog = true }
            )
            
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
            
            FreqEditRow(
                label = stringResource(R.string.adreno_max_freq),
                value = devfreq.maxFreq,
                onClick = { showMaxDialog = true }
            )
        }
    }
    
    if (showMinDialog) {
        FreqSelectionDialogExpressive(
            title = "${stringResource(R.string.adreno_min)} ${devfreq.name}",
            currentValue = devfreq.minFreq,
            options = devfreq.availableFreqs,
            onSelect = { onUpdate(devfreq.path, "min_freq", it) },
            onDismiss = { showMinDialog = false }
        )
    }
    if (showMaxDialog) {
        FreqSelectionDialogExpressive(
            title = "${stringResource(R.string.adreno_max)} ${devfreq.name}",
            currentValue = devfreq.maxFreq,
            options = devfreq.availableFreqs.reversed(),
            onSelect = { onUpdate(devfreq.path, "max_freq", it) },
            onDismiss = { showMaxDialog = false }
        )
    }
}

@Composable
private fun FreqEditRow(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(16.dp)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        IconButton(
            onClick = onClick,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Icon(
                Icons.Default.Edit,
                contentDescription = stringResource(R.string.adreno_edit),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun BusComponentExpressiveCard(
    bus: AdrenoViewModel.BusState,
    cardColor: Color,
    isGlassActive: Boolean,
    glassModifier: Modifier,
    onUpdateMin: (String) -> Unit,
    onUpdateMax: (String) -> Unit
) {
    var showMinDialog by remember { mutableStateOf(false) }
    var showMaxDialog by remember { mutableStateOf(false) }
    
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else cardColor,
        tonalElevation = if (isGlassActive) 0.dp else 2.dp,
        modifier = Modifier.fillMaxWidth().then(if (isGlassActive) glassModifier else Modifier)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                bus.name.replace("_", " ").uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showMinDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            stringResource(R.string.adreno_min),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${bus.minFreq} MHz",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showMaxDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            stringResource(R.string.adreno_max),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${bus.maxFreq} MHz",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
    
    if (showMinDialog) {
        FreqSelectionDialogExpressive(
            title = "${stringResource(R.string.adreno_min)} ${bus.name}",
            currentValue = bus.minFreq,
            options = bus.availableFreqs,
            onSelect = { onUpdateMin(it); showMinDialog = false },
            onDismiss = { showMinDialog = false }
        )
    }
    if (showMaxDialog) {
        FreqSelectionDialogExpressive(
            title = "${stringResource(R.string.adreno_max)} ${bus.name}",
            currentValue = bus.maxFreq,
            options = bus.availableFreqs.reversed(),
            onSelect = { onUpdateMax(it); showMaxDialog = false },
            onDismiss = { showMaxDialog = false }
        )
    }
}

@Composable
private fun FreqSelectionDialogExpressive(
    title: String,
    currentValue: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    BlurredAlertDialog(
        scrollableText = false,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { 
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(options) { freq ->
                    val isSelected = freq == currentValue.replace(" MHz", "")
                    Surface(
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                               else MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(
                            onClick = { onSelect(freq) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer 
                                              else MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text(
                                "$freq MHz",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) { 
                Text(stringResource(R.string.adreno_cancel), fontWeight = FontWeight.Medium) 
            }
        }
    )
}
