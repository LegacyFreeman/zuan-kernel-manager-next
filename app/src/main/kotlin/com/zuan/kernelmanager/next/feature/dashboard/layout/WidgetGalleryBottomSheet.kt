/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package com.zuan.kernelmanager.next.feature.dashboard.layout

import com.zuan.kernelmanager.next.ui.components.ConfigureSheetWindowBlur
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import com.zuan.kernelmanager.next.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zuan.kernelmanager.next.feature.dashboard.components.OverallIconContainer
import com.zuan.kernelmanager.next.ui.components.ZkmSwitch

/**
 * =====================================================================================
 * WIDGET GALLERY BOTTOM SHEET
 *
 * Galeri semua widget yang tersedia untuk dashboard:
 * - Switch ON  → widget tampil di dashboard
 * - Switch OFF → widget disembunyikan (posisinya tetap diingat, bisa dibalikin kapan aja)
 * - Widget inti (removable = false) switch-nya di-disable
 *
 * Menambah widget baru ke galeri = cukup tambah entry di [DashboardWidgetType].
 * =====================================================================================
 */
@Composable
fun WidgetGalleryBottomSheet(
    allWidgets: List<DashboardWidgetType>,
    hiddenWidgets: Set<DashboardWidgetType>,
    onToggle: (DashboardWidgetType) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current

    ModalBottomSheet(
        contentWindowInsets = { com.zuan.kernelmanager.next.ui.adaptive.zkmSheetWindowInsets() },
        
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
ConfigureSheetWindowBlur()
        // verticalScroll: daftar widget lebih tinggi dari layar, jadi harus bisa di-scroll.
        // Scroll ditaruh PALING LUAR (sebelum padding) supaya padding ikut bergulir; sheet
        // otomatis menyerahkan drag ke list dulu dan baru menutup saat list ada di paling atas.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // --- HEADER ---
            Text(
                text = stringResource(R.string.lp_overall_gallery_widget),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.lp_overall_gallery_manage),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // --- DAFTAR WIDGET ---
            allWidgets.forEach { widget ->
                val isVisible = widget !in hiddenWidgets

                val rowColor by animateColorAsState(
                    targetValue = if (isVisible)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    else
                        MaterialTheme.colorScheme.surfaceContainerLow,
                    animationSpec = tween(250),
                    label = "galleryRowColor"
                )

                Surface(
                    onClick = {
                        if (widget.removable) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggle(widget)
                        }
                    },
                    enabled = widget.removable,
                    shape = RoundedCornerShape(24.dp),
                    color = rowColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        OverallIconContainer(
                            icon = widget.icon,
                            containerColor = if (isVisible)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                            iconColor = if (isVisible)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(widget.localizedTitleRes()),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val localizedDescriptionId = widget.localizedDescriptionRes()
                            val description = if (localizedDescriptionId != 0) stringResource(localizedDescriptionId) else widget.description
                            Text(
                                text = if (widget.removable) description
                                    else stringResource(R.string.lp_overall_gallery_core, description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        ZkmSwitch(
                            checked = isVisible,
                            onCheckedChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggle(widget)
                            },
                            enabled = widget.removable,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
        }
    }
}
