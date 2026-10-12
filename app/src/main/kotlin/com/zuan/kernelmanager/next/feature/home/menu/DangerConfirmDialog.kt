/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.zuan.kernelmanager.next.feature.home.menu
import com.zuan.kernelmanager.next.ui.components.BlurredAlertDialog
import androidx.compose.material3.ButtonDefaults
import com.zuan.kernelmanager.next.R
import androidx.compose.ui.res.stringResource

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

sealed class DangerAction(val title: String, val message: String) {
    data class DisableAllZones(val level: Float) : DangerAction(
        "Disable ALL Thermal Zones?",
        "This disables every thermal zone including battery and skin sensors. Your device can overheat and suffer permanent hardware damage. Only use for short benchmarks."
    )
    data object KillThermalProcess : DangerAction(
        "Stop Thermal Process?",
        "This freezes the system thermal daemon (kill -STOP). The system loses ALL software thermal protection until you re-enable it or reboot."
    )
    data object DisableThermalProp : DangerAction(
        "Stop Thermal Services?",
        "This stops system thermal services via ctl.stop. Thermal throttling will no longer protect your device."
    )
    data object DisableGpuThrottling : DangerAction(
        "Disable GPU Throttling?",
        "The GPU will ignore thermal limits and can overheat under sustained load. Use with caution."
    )
}

@Composable
fun DangerConfirmDialog(
    action: DangerAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    BlurredAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(action.title) },
        text = { Text(action.message) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }
        ) {
                Text(stringResource(R.string.lp_i_understand_proceed), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss
        ) { Text(stringResource(R.string.lp_cancel_77dfd2)) }
        }
    )
}