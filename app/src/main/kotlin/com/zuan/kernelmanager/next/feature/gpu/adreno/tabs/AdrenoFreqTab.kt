/*
 * Copyright (c) 2026 ZKM
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
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
import com.zuan.kernelmanager.next.ui.components.BlurredAlertDialog
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import com.zuan.kernelmanager.next.ui.components.zkmHazeEffect
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import com.zuan.kernelmanager.next.ui.components.ZkmHazeStyleCache
import com.zuan.kernelmanager.next.ui.theme.zkmIconShape
import com.zuan.kernelmanager.next.ui.theme.ZkmPaddedIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdrenoFreqTab(
    state: AdrenoViewModel.FreqState,
    hazeState: HazeState,
    cardColor: Color,
    isGlassActive: Boolean,
    accentColor: Color,
    useDarkTheme: Boolean,
    onUpdateMin: (String) -> Unit,
    onUpdateMax: (String) -> Unit,
    onUpdateGov: (String) -> Unit,
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
            Modifier.clip(bentoShape)
        }

    val heroColor = if (useDarkTheme) Color(0xFF2C324A) else Color(0xFFEEF0F6)
    val govColor = if (useDarkTheme) Color(0xFF2B2B2B) else Color(0xFFF3F2F8)
    val minColor = if (useDarkTheme) Color(0xFF4A2C46) else Color(0xFFFFF0F5)
    val maxColor = if (useDarkTheme) Color(0xFF4A322C) else Color(0xFFFFF0EC)

    val heroTextColor = if (useDarkTheme) Color(0xFFAAB8E6) else Color(0xFF3B487A)
    val maxTextColor = if (useDarkTheme) Color.White else Color(0xFF111111)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    modifier = Modifier.weight(1.2f).fillMaxHeight().then(glassModifier),
                    color = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else heroColor,
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Surface(shape = zkmIconShape(CircleShape), color = heroTextColor.copy(alpha = 0.15f), modifier = Modifier.size(52.dp)) {
                            ZkmPaddedIcon(imageVector = Icons.Default.Speed, tint = heroTextColor, padding = 14.dp)
                        }
                        Column {
                            Text(
                                text = "${state.currentFreq}",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = heroTextColor,
                            )
                            Text(
                                text = "MHz Current",
                                style = MaterialTheme.typography.bodyMedium,
                                color = heroTextColor.copy(alpha = 0.8f),
                            )
                        }
                    }
                }

                var showGovDialog by remember { mutableStateOf(false) }
                Surface(
                    modifier =
                        Modifier
                            .weight(0.8f)
                            .fillMaxHeight()
                            .then(glassModifier)
                            .clickable { showGovDialog = true },
                    color = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else govColor,
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column {
                            Text(
                                text = state.governor.uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                            Text(
                                text = "Governor",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (showGovDialog) {
                    FreqSelectionDialog("Select Governor", state.governor, state.availableGovs, {
                        onUpdateGov(it)
                        showGovDialog = false
                    }, {
                        showGovDialog =
                            false
                    })
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().height(130.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                var showMinDialog by remember { mutableStateOf(false) }
                Surface(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(glassModifier)
                            .clickable { showMinDialog = true },
                    color = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else minColor,
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                stringResource(R.string.lp_min_freq),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Icon(
                                Icons.Default.ArrowDownward,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            "${state.minFreq} MHz",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = maxTextColor,
                        )
                    }
                }

                if (showMinDialog) {
                    FreqSelectionDialog("Minimum Frequency", state.minFreq, state.availableFreqs, {
                        onUpdateMin(it)
                        showMinDialog = false
                    }, {
                        showMinDialog =
                            false
                    })
                }

                var showMaxDialog by remember { mutableStateOf(false) }
                Surface(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(glassModifier)
                            .clickable { showMaxDialog = true },
                    color = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else maxColor,
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                stringResource(R.string.lp_max_freq),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Icon(Icons.Default.ArrowUpward, null, tint = Color(0xFFD32F2F), modifier = Modifier.size(18.dp))
                        }
                        Text(
                            "${state.maxFreq} MHz",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = maxTextColor,
                        )
                    }
                }

                if (showMaxDialog) {
                    FreqSelectionDialog("Maximum Frequency", state.maxFreq, state.availableFreqs.reversed(), {
                        onUpdateMax(it)
                        showMaxDialog =
                            false
                    }, { showMaxDialog = false })
                }
            }
        }
    }
}

@Composable
private fun FreqSelectionDialog(
    title: String,
    currentValue: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    BlurredAlertDialog(
        scrollableText = false,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(options) { freq ->
                    val isSelected = freq == currentValue.replace(" MHz", "")
                    Surface(
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(freq) },
                    ) {
                        Text(
                            text = if (freq.any { it.isDigit() }) "$freq MHz" else freq,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.adreno_cancel)) } },
    )
}
