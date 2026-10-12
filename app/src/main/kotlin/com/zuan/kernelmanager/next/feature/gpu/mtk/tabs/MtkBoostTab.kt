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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
fun MtkBoostTab(
    state: MtkViewModel.BoostState,
    hazeState: HazeState,
    cardColor: Color,
    isGlassActive: Boolean,
    accentColor: Color,
    onToggleFeature: (String, Boolean) -> Unit,
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
        item {
            Text(
                stringResource(R.string.mtk_gaming_performance),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
            )
        }

        item {
            BoostCard(
                title = stringResource(R.string.mtk_fpsgo_title),
                subtitle = stringResource(R.string.mtk_fpsgo_subtitle),
                icon = Icons.Default.Games,
                checked = state.isFpsGoEnabled,
                onCheckedChange = { onToggleFeature("fpsgo", it) },
                color = accentColor.copy(alpha = 0.15f),
                contentColor = accentColor,
                cardColor = cardColor,
                isGlassActive = isGlassActive,
                glassModifier = glassModifier,
            )
        }

        item {
            BoostCard(
                title = stringResource(R.string.mtk_ged_game_mode_title),
                subtitle = stringResource(R.string.mtk_ged_game_mode_subtitle),
                icon = Icons.Default.SportsEsports,
                checked = state.isGedGameMode,
                onCheckedChange = { onToggleFeature("ged_game", it) },
                color = accentColor.copy(alpha = 0.15f),
                contentColor = accentColor,
                cardColor = cardColor,
                isGlassActive = isGlassActive,
                glassModifier = glassModifier,
            )
        }

        item {
            BoostCard(
                title = stringResource(R.string.mtk_touch_boost_title),
                subtitle = stringResource(R.string.mtk_touch_boost_subtitle),
                icon = Icons.Default.TouchApp,
                checked = state.isTouchBoostEnabled,
                onCheckedChange = { onToggleFeature("touch_boost", it) },
                color = accentColor.copy(alpha = 0.15f),
                contentColor = accentColor,
                cardColor = cardColor,
                isGlassActive = isGlassActive,
                glassModifier = glassModifier,
            )
        }

        item {
            BoostCard(
                title = stringResource(R.string.mtk_sched_boost_title),
                subtitle = stringResource(R.string.mtk_sched_boost_subtitle),
                icon = Icons.Default.Schedule,
                checked = state.isSchedBoostEnabled,
                onCheckedChange = { onToggleFeature("sched_boost", it) },
                color = accentColor.copy(alpha = 0.15f),
                contentColor = accentColor,
                cardColor = cardColor,
                isGlassActive = isGlassActive,
                glassModifier = glassModifier,
            )
        }

        item {
            Surface(
                shape = bentoShape,
                color = accentColor.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Surface(
                            shape = zkmIconShape(RoundedCornerShape(16.dp)),
                            color = accentColor.copy(alpha = 0.2f),
                            modifier = Modifier.size(52.dp),
                        ) {
                            ZkmPaddedIcon(imageVector = Icons.Default.RocketLaunch, tint = accentColor, padding = 14.dp)
                        }
                        Column {
                            Text(
                                stringResource(R.string.mtk_gaming_mode_preset),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                            )
                            Text(
                                stringResource(R.string.mtk_gaming_mode_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = accentColor.copy(alpha = 0.8f),
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            onToggleFeature("fpsgo", true)
                            onToggleFeature("ged_game", true)
                            onToggleFeature("touch_boost", true)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                    ) {
                        Icon(Icons.Default.Games, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.mtk_activate_gaming_mode),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoostCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    color: Color,
    contentColor: Color,
    cardColor: Color,
    isGlassActive: Boolean,
    glassModifier: Modifier,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Surface(
                    shape = zkmIconShape(RoundedCornerShape(14.dp)),
                    color = color,
                    modifier = Modifier.size(48.dp),
                ) {
                    ZkmPaddedIcon(imageVector = icon, tint = contentColor, padding = 12.dp)
                }
                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ZkmSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors =
                    SwitchDefaults.colors(
                        checkedThumbColor = contentColor,
                        checkedTrackColor = contentColor.copy(alpha = 0.4f),
                    ),
            )
        }
    }
}
