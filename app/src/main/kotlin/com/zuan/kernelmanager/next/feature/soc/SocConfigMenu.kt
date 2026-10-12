/*
 * Copyright (c) 2026 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.zuan.kernelmanager.next.feature.soc
import com.zuan.kernelmanager.next.ui.components.BlurredAlertDialog
import com.zuan.kernelmanager.next.R
import androidx.compose.ui.res.stringResource

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zuan.kernelmanager.next.feature.tuning.CpuGpuUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * State internal untuk alur Import/Export.
 */
private sealed class ConfigDialogState {
    data object Hidden : ConfigDialogState()
    data class PresetHub(val presets: List<CuratedSocPreset>) : ConfigDialogState()
    data class ExportForm(val chipset: String, val gpu: String) : ConfigDialogState()
    data class PreviewImport(val metadata: SocConfigMetadata, val rawJson: String) : ConfigDialogState()
    data class MismatchWarning(val metadata: SocConfigMetadata, val currentChipset: String, val currentGpu: String, val rawJson: String) : ConfigDialogState()
    data class SuccessInfo(val title: String, val message: String, val isExport: Boolean) : ConfigDialogState()
}

@Composable
fun SocConfigMenu(activeColor: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var menuExpanded by remember { mutableStateOf(false) }
    var dialogState by remember { mutableStateOf<ConfigDialogState>(ConfigDialogState.Hidden) }
    
    var profileName by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }

    fun restore(rawJson: String) {
        scope.launch {
            isBusy = true
            SocConfigManager.restoreFromJson(context, rawJson)
                .onSuccess { 
                    dialogState = ConfigDialogState.SuccessInfo(
                        title = "Import Successful! ⚡",
                        message = "The config has been restored and applied directly to the kernel via root.",
                        isExport = false
                    )
                }
                .onFailure { Toast.makeText(context, context.getString(R.string.lp_toast_soc_restore_failed, it.message ?: "Unknown error"), Toast.LENGTH_LONG).show() }
            isBusy = false
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri: Uri = result.data?.data ?: return@rememberLauncherForActivityResult
        scope.launch {
            isBusy = true
            val currentState = dialogState as? ConfigDialogState.ExportForm
            if (currentState != null) {
                // DI SINI PARAMETERNYA UDAH LENGKAP 7 BIJI
                SocConfigManager.exportToUri(context, uri, profileName, author, description, currentState.chipset, currentState.gpu)
                    .onSuccess { 
                        dialogState = ConfigDialogState.SuccessInfo(
                            title = "Export Successful! 📤",
                            message = "Config profile '${it.profileName}' by ${it.author} has been saved successfully.\n\nDescription:\n${it.description}",
                            isExport = true
                        )
                    }
                    .onFailure { Toast.makeText(context, context.getString(R.string.lp_toast_soc_export_failed, it.message ?: "Unknown error"), Toast.LENGTH_LONG).show() }
            }
            isBusy = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri: Uri = result.data?.data ?: return@rememberLauncherForActivityResult
        scope.launch {
            isBusy = true
            when (val check = SocConfigManager.validateFromUri(context, uri)) {
                is SocImportCheck.Compatible -> {
                    dialogState = ConfigDialogState.PreviewImport(check.metadata, check.rawJson)
                }
                is SocImportCheck.ChipsetMismatch -> {
                    dialogState = ConfigDialogState.MismatchWarning(check.metadata, check.currentChipset, check.currentGpu, check.rawJson)
                }
                is SocImportCheck.Invalid -> Toast.makeText(context, context.getString(R.string.lp_toast_soc_invalid_file, check.reason), Toast.LENGTH_LONG).show()
            }
            isBusy = false
        }
    }

    IconButton(onClick = { menuExpanded = true }, enabled = !isBusy
        ) {
        Icon(Icons.Rounded.Bolt, contentDescription = stringResource(R.string.lp_a11y_smart_soc_config), tint = activeColor)
    }

    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.soc_preset_hub_title)) },
            leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null, tint = activeColor) },
            onClick = {
                menuExpanded = false
                scope.launch {
                    val presets = SocConfigManager.getCuratedPresets()
                    dialogState = ConfigDialogState.PresetHub(presets)
                }
            }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.lp_export_config)) },
            leadingIcon = { Icon(Icons.Rounded.FileUpload, null) },
            onClick = {
                menuExpanded = false
                scope.launch {
                    val chipset = CpuGpuUtils.getCpuInfo()
                    val gpuType = CpuGpuUtils.getGpuType()
                    val gpuStr = when (gpuType) {
                        CpuGpuUtils.GpuType.ADRENO -> "Adreno"
                        CpuGpuUtils.GpuType.MEDIATEK_V2, CpuGpuUtils.GpuType.MEDIATEK_LEGACY -> "Mali / MediaTek"
                        CpuGpuUtils.GpuType.GENERIC_DEVFREQ -> "Generic GPU"
                        else -> "Unknown GPU"
                    }
                    dialogState = ConfigDialogState.ExportForm(chipset, gpuStr)
                }
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.lp_import_config)) },
            leadingIcon = { Icon(Icons.Rounded.FileDownload, null) },
            onClick = {
                menuExpanded = false
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = SocConfigManager.FILE_MIME_TYPE }
                importLauncher.launch(intent)
            }
        )
    }

    // 1. DIALOG FORM EXPORT
    if (dialogState is ConfigDialogState.ExportForm) {
        val state = dialogState as ConfigDialogState.ExportForm
        BlurredAlertDialog(
            onDismissRequest = { dialogState = ConfigDialogState.Hidden },
            shape = RoundedCornerShape(28.dp),
            title = { Text(stringResource(R.string.lp_export_soc_config), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.lp_share_your_tweak_recipe_enter_the_config_details_b), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    OutlinedTextField(value = profileName, onValueChange = { profileName = it }, label = { Text(stringResource(R.string.lp_profile_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    OutlinedTextField(value = author, onValueChange = { author = it }, label = { Text(stringResource(R.string.lp_author_your_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text(stringResource(R.string.lp_config_description)) }, modifier = Modifier.fillMaxWidth().height(100.dp), shape = RoundedCornerShape(12.dp), placeholder = { Text(stringResource(R.string.lp_e_g_stable_60fps_everywhere)) })
                    
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Rounded.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text(stringResource(R.string.lp_hardware_compatibility), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${state.gpu} (Recommended)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(state.chipset, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                    val safeName = profileName.ifBlank { "soc_config" }.replace(Regex("[^A-Za-z0-9_-]"), "_")
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = SocConfigManager.FILE_MIME_TYPE; putExtra(Intent.EXTRA_TITLE, "${safeName}_$timestamp.json") }
                    exportLauncher.launch(intent)
                }
        ) { Text(stringResource(R.string.lp_export)) }
            },
            dismissButton = { TextButton(onClick = { dialogState = ConfigDialogState.Hidden }
        ) { Text(stringResource(R.string.lp_cancel_77dfd2)) } }
        )
    }

    // 2. DIALOG PREVIEW IMPORT (Kalo Cocok)
    if (dialogState is ConfigDialogState.PreviewImport) {
        val meta = (dialogState as ConfigDialogState.PreviewImport).metadata
        val rawJson = (dialogState as ConfigDialogState.PreviewImport).rawJson
        
        BlurredAlertDialog(
            onDismissRequest = { dialogState = ConfigDialogState.Hidden },
            shape = RoundedCornerShape(28.dp),
            title = { Text(stringResource(R.string.lp_review_config), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(meta.profileName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text("By: ${meta.author}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Text(meta.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    
                    Surface(color = Color(0xFF4CAF50).copy(alpha = 0.1f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF388E3C))
                            Column {
                                Text(stringResource(R.string.lp_hardware_compatibility), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${meta.gpu} • ${meta.chipset}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFF388E3C))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { restore(rawJson) }
        ) { Text(stringResource(R.string.lp_apply_tweak), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { dialogState = ConfigDialogState.Hidden }
        ) { Text(stringResource(R.string.lp_cancel_77dfd2)) } }
        )
    }

    // 3. DIALOG WARNING MISMATCH (Kalo KTP Beda)
    if (dialogState is ConfigDialogState.MismatchWarning) {
        val state = dialogState as ConfigDialogState.MismatchWarning
        BlurredAlertDialog(
            onDismissRequest = { dialogState = ConfigDialogState.Hidden },
            shape = RoundedCornerShape(28.dp),
            icon = { Icon(Icons.Rounded.WarningAmber, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.lp_chipset_mismatch), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.lp_this_config_was_made_for_different_hardware_applyi), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    
                    Surface(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(state.metadata.profileName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text("By: ${state.metadata.author}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text("Description: ${state.metadata.description}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            HorizontalDivider(color = MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                            Text("Original Target: ${state.metadata.gpu} (${state.metadata.chipset})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            Text("Your Hardware: ${state.currentGpu} (${state.currentChipset})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            },
            confirmButton = {
                Button(colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError), onClick = { restore(state.rawJson) }
        ) { Text(stringResource(R.string.lp_proceed_at_my_own_risk)) }
            },
            dismissButton = { TextButton(onClick = { dialogState = ConfigDialogState.Hidden }
        ) { Text(stringResource(R.string.lp_cancel_77dfd2)) } }
        )
    }

    // PRESET LIBRARY DIALOG
    if (dialogState is ConfigDialogState.PresetHub) {
        val state = dialogState as ConfigDialogState.PresetHub
        val scrollState = rememberScrollState()
        BlurredAlertDialog(
            scrollableText = false,
            onDismissRequest = { dialogState = ConfigDialogState.Hidden },
            shape = RoundedCornerShape(28.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = activeColor)
                    Text(stringResource(R.string.soc_preset_hub_title), fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(R.string.soc_preset_hub_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    state.presets.forEach { preset ->
                        val presetIcon = when (preset.type) {
                            CuratedPresetType.BATTERY_SAVER -> Icons.Rounded.EnergySavingsLeaf
                            CuratedPresetType.BALANCED -> Icons.Rounded.Speed
                            CuratedPresetType.GAMING_ULTRA -> Icons.Rounded.RocketLaunch
                            CuratedPresetType.THERMAL_CHILLER -> Icons.Rounded.AcUnit
                        }
                        val presetColor = when (preset.type) {
                            CuratedPresetType.BATTERY_SAVER -> Color(0xFF4CAF50)
                            CuratedPresetType.BALANCED -> activeColor
                            CuratedPresetType.GAMING_ULTRA -> Color(0xFFFF5722)
                            CuratedPresetType.THERMAL_CHILLER -> Color(0xFF00BCD4)
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(presetIcon, contentDescription = null, tint = presetColor, modifier = Modifier.size(22.dp))
                                        Text(
                                            stringResource(preset.titleRes),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                Text(
                                    stringResource(preset.descRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        preset.author,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = presetColor,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                isBusy = true
                                                SocConfigManager.applyPreset(context, preset)
                                                    .onSuccess {
                                                        dialogState = ConfigDialogState.SuccessInfo(
                                                            title = "Preset Applied! ⚡",
                                                            message = context.getString(
                                                                R.string.soc_preset_applied_success,
                                                                context.getString(preset.titleRes)
                                                            ),
                                                            isExport = false
                                                        )
                                                    }
                                                    .onFailure {
                                                        Toast.makeText(
                                                            context,
                                                            context.getString(R.string.lp_toast_soc_restore_failed, it.message ?: "Unknown error"),
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                                isBusy = false
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = presetColor),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(stringResource(R.string.soc_preset_apply_action), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { dialogState = ConfigDialogState.Hidden }) {
                    Text(stringResource(R.string.lp_cancel_77dfd2))
                }
            }
        )
    }

    // 4. DIALOG SUCCESS INFO
    if (dialogState is ConfigDialogState.SuccessInfo) {
        val state = dialogState as ConfigDialogState.SuccessInfo
        BlurredAlertDialog(
            onDismissRequest = { dialogState = ConfigDialogState.Hidden },
            shape = RoundedCornerShape(28.dp),
            icon = { Icon(if (state.isExport) Icons.Rounded.FileUpload else Icons.Rounded.OfflineBolt, null, tint = activeColor, modifier = Modifier.size(32.dp)) },
            title = { Text(state.title, fontWeight = FontWeight.Bold) },
            text = { Text(state.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = { Button(onClick = { dialogState = ConfigDialogState.Hidden }
        ) { Text(stringResource(R.string.lp_ok), fontWeight = FontWeight.Bold) } }
        )
    }
}
