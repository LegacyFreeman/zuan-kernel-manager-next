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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SettingsSuggest
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
fun MtkPpmTab(
    state: MtkViewModel.PpmState,
    hazeState: HazeState,
    cardColor: Color,
    isGlassActive: Boolean,
    accentColor: Color,
    onTogglePolicy: (Int, Boolean) -> Unit,
    onTogglePpm: (Boolean) -> Unit,
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
                            ZkmPaddedIcon(imageVector = Icons.Default.Info, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), padding = 20.dp)
                        }
                        Text(
                            stringResource(R.string.mtk_ppm_unavailable),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.mtk_ppm_not_found),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
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
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Surface(
                            shape = zkmIconShape(RoundedCornerShape(16.dp)),
                            color = accentColor.copy(alpha = 0.2f),
                            modifier = Modifier.size(52.dp),
                        ) {
                            ZkmPaddedIcon(imageVector = Icons.Default.SettingsSuggest, tint = accentColor, padding = 14.dp)
                        }
                        Column {
                            Text(
                                stringResource(R.string.mtk_ppm_controller),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                            )
                            Text(
                                if (state.isEnabled) {
                                    stringResource(R.string.mtk_ppm_active)
                                } else {
                                    stringResource(R.string.mtk_ppm_disabled)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = accentColor.copy(alpha = 0.8f),
                            )
                        }
                    }
                    ZkmSwitch(
                        checked = state.isEnabled,
                        onCheckedChange = onTogglePpm,
                        colors =
                            SwitchDefaults.colors(
                                checkedThumbColor = accentColor,
                                checkedTrackColor = accentColor.copy(alpha = 0.4f),
                            ),
                    )
                }
            }
        }

        if (state.isEnabled && state.policies.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.mtk_active_policies),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                )
            }

            items(
                items = state.policies,
                key = { it.idx },
            ) { policy ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else cardColor,
                    tonalElevation = if (isGlassActive) 0.dp else 2.dp,
                    modifier = Modifier.fillMaxWidth().then(if (isGlassActive) glassModifier else Modifier),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                policy.name.replace("_", " "),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                "${stringResource(R.string.mtk_policy_hash)}${policy.idx}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ZkmSwitch(
                            checked = policy.enabled,
                            onCheckedChange = { onTogglePolicy(policy.idx, it) },
                            colors =
                                SwitchDefaults.colors(
                                    checkedThumbColor = accentColor,
                                    checkedTrackColor = accentColor.copy(alpha = 0.4f),
                                ),
                        )
                    }
                }
            }
        } else if (state.isEnabled && state.policies.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.mtk_ppm_no_policies),
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            stringResource(R.string.mtk_about_ppm),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.mtk_ppm_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}
