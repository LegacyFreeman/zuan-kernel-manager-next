/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.zuan.kernelmanager.next.feature.dashboard.layout
import com.zuan.kernelmanager.next.R
import androidx.compose.ui.res.stringResource

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddBox
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex

/**
 * =====================================================================================
 * FAB MENU — Material 3 Expressive (FloatingActionButtonMenuSample)
 * =====================================================================================
 */
@Composable
fun DashboardFabMenu(
    visible: Boolean,
    onOpenWidgetGallery: () -> Unit,
    onAddWidget: () -> Unit,
    onResetLayout: () -> Unit,
    onOpenQuickTools: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    // [FIX] Tangkap warna dari MaterialTheme di dalam scope @Composable utama
    val fabContainerColor = MaterialTheme.colorScheme.primary
    val fabContentColor = MaterialTheme.colorScheme.onPrimary
    // [FIX] Menu items sebelumnya memakai default transparan (kelihatan "telanjang"
    // di atas wallpaper/custom background) dan ikon mengikuti tint default yang
    // tidak konsisten. Sekarang eksplisit solid + theme-aware.
    val itemContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val itemContentColor = MaterialTheme.colorScheme.onSurface
    val dashboardMenuDescription = stringResource(R.string.lp_a11y_dashboard_menu)

    // Back gesture menutup menu dulu, bukan keluar screen
    BackHandler(enabled = expanded) { expanded = false }

    AnimatedVisibility(
        visible = visible || expanded,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
        modifier = modifier
    ) {
        FloatingActionButtonMenu(
            expanded = expanded,
            button = {
                ToggleFloatingActionButton(
                    modifier = Modifier
                        .semantics {
                            traversalIndex = -1f
                            stateDescription = if (expanded) "Expanded" else "Collapsed"
                            contentDescription = dashboardMenuDescription
                        },
                    checked = expanded,
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        expanded = !expanded
                    },
                    // Lempar variabel warna yang sudah diekstrak di atas ke dalam lambda
                    containerColor = { fabContainerColor }
                ) {
                    val imageVector by remember {
                        derivedStateOf {
                            if (checkedProgress > 0.5f) Icons.Rounded.Close
                            else Icons.Rounded.Widgets
                        }
                    }
                    Icon(
                        painter = rememberVectorPainter(imageVector),
                        contentDescription = null,
                        modifier = Modifier.animateIcon({ checkedProgress }),
                        // Gunakan warna konten yang sudah diekstrak
                        tint = fabContentColor
                    )
                }
            }
        ) {
            FloatingActionButtonMenuItem(
                onClick = {
                    expanded = false
                    onOpenWidgetGallery()
                },
                containerColor = itemContainerColor,
                contentColor = itemContentColor,
                icon = { Icon(Icons.Rounded.DashboardCustomize, contentDescription = null, tint = itemContentColor) },
                text = { Text(stringResource(R.string.lp_widget_gallery), color = itemContentColor) }
            )
            FloatingActionButtonMenuItem(
                onClick = {
                    expanded = false
                    onAddWidget()
                },
                containerColor = itemContainerColor,
                contentColor = itemContentColor,
                icon = { Icon(Icons.Rounded.AddBox, contentDescription = null, tint = itemContentColor) },
                text = { Text(stringResource(R.string.lp_fab_add_widget), color = itemContentColor) }
            )
            FloatingActionButtonMenuItem(
                onClick = {
                    expanded = false
                    onResetLayout()
                },
                containerColor = itemContainerColor,
                contentColor = itemContentColor,
                icon = { Icon(Icons.Rounded.RestartAlt, contentDescription = null, tint = itemContentColor) },
                text = { Text(stringResource(R.string.lp_reset_layout), color = itemContentColor) }
            )
            FloatingActionButtonMenuItem(
                onClick = {
                    expanded = false
                    onOpenQuickTools()
                },
                containerColor = itemContainerColor,
                contentColor = itemContentColor,
                icon = { Icon(Icons.Rounded.Build, contentDescription = null, tint = itemContentColor) },
                text = { Text(stringResource(R.string.lp_fab_quick_tools), color = itemContentColor) }
            )
        }
    }
}
