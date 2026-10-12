/*
 * Original code from: Rem01Gaming (origami_kernel_manager)
 * Modified and integrated by: Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.zuan.kernelmanager.next.feature.tuning
import com.zuan.kernelmanager.next.ui.components.zkmGlassFallbackTint
import com.zuan.kernelmanager.next.ui.components.zkmCardHazeEffect
import com.zuan.kernelmanager.next.ui.components.zkmReferenceGlassSurface
import com.zuan.kernelmanager.next.ui.components.zkmCardBorder
import com.zuan.kernelmanager.next.ui.components.BlurredAlertDialog
import com.zuan.kernelmanager.next.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zuan.kernelmanager.next.feature.settings.SettingsViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import com.zuan.kernelmanager.next.ui.components.zkmHazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import com.zuan.kernelmanager.next.ui.components.ZkmHazeStyleCache
import com.zuan.kernelmanager.next.ui.theme.ZkmBareIcon
import com.zuan.kernelmanager.next.ui.components.ZkmSwitch

@Composable
fun SchedulerScreen(
    viewModel: SchedulerViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    hazeState: HazeState,
    cardBg: Color,
    contentColor: Color,
    subContentColor: Color,
    activeColor: Color,
    contentPadding: PaddingValues = PaddingValues(top = 180.dp, bottom = 130.dp)
) {
    val sched by viewModel.sched.collectAsStateWithLifecycle()
    val bore by viewModel.bore.collectAsStateWithLifecycle()
    val uclamp by viewModel.uclamp.collectAsStateWithLifecycle()
    val genericTunables by viewModel.genericTunables.collectAsStateWithLifecycle()
    val applyOnBoot by viewModel.applyOnBoot.collectAsStateWithLifecycle()

    val isCustomBg by settingsViewModel.isCustomBackground.collectAsStateWithLifecycle()
    val isHazeEnabled by settingsViewModel.isHazeEnabled.collectAsStateWithLifecycle()
    val cardDarkness by settingsViewModel.cardDarkness.collectAsStateWithLifecycle()
    val isGlassActive = isCustomBg && isHazeEnabled

    var showDialog by remember { mutableStateOf(false) }
    var selectedItemName by remember { mutableStateOf("") }
    var selectedItemPath by remember { mutableStateOf("") }
    var editValue by remember { mutableStateOf("") }

    fun openEditDialog(name: String, path: String, currentValue: String) {
        selectedItemName = name
        selectedItemPath = path
        editValue = currentValue
        showDialog = true
    }

    fun onSave() {
        if (selectedItemPath.isNotEmpty()) {
            viewModel.updateValue(selectedItemPath, editValue)
        }
        showDialog = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {

        item {
            val shape = RoundedCornerShape(16.dp)
            Box(
                modifier = if (isGlassActive) {
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .zkmReferenceGlassSurface(hazeState, shape)
                } else {
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(shape)
                        .background(cardBg)
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(activeColor.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.RestartAlt, null, tint = activeColor)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Apply on Boot",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                        Text(
                            text = "Restore scheduler settings after reboot",
                            style = MaterialTheme.typography.labelSmall,
                            color = subContentColor
                        )
                    }
                    ZkmSwitch(
                        checked = applyOnBoot,
                        onCheckedChange = { viewModel.setApplyOnBoot(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = activeColor,
                            uncheckedThumbColor = contentColor.copy(alpha = 0.6f),
                            uncheckedTrackColor = Color.Transparent,
                            uncheckedBorderColor = contentColor.copy(alpha = 0.3f)
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            SectionTitle("Feature Toggles", subContentColor)

            if (bore.hasBore) {
                BentoSwitchCard(
                    title = "BORE Scheduler",
                    checked = bore.bore == 1,
                    icon = Icons.Rounded.Speed,
                    activeColor = activeColor, cardBg = cardBg, contentColor = contentColor,
                    isGlassActive = isGlassActive, hazeState = hazeState, cardDarkness = cardDarkness,
                    onCheckedChange = { viewModel.updateValue(SchedulerUtils.BORE, if (it) "1" else "0") }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (sched.hasSchedAutogroup) {
                BentoSwitchCard(
                    title = "Auto Group",
                    checked = sched.schedAutogroup == "1",
                    icon = Icons.Rounded.GroupWork,
                    activeColor = activeColor, cardBg = cardBg, contentColor = contentColor,
                    isGlassActive = isGlassActive, hazeState = hazeState, cardDarkness = cardDarkness,
                    onCheckedChange = { viewModel.updateValue(SchedulerUtils.SCHED_AUTO_GROUP, if (it) "1" else "0") }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (sched.hasChildRunsFirst) {
                BentoSwitchCard(
                    title = "Child Runs First",
                    checked = sched.childRunsFirst == "1",
                    icon = Icons.Rounded.ChildCare,
                    activeColor = activeColor, cardBg = cardBg, contentColor = contentColor,
                    isGlassActive = isGlassActive, hazeState = hazeState, cardDarkness = cardDarkness,
                    onCheckedChange = {
                        viewModel.updateValue(
                            SchedulerUtils.SCHED_CHILD_RUNS_FIRST,
                            if (it) "1" else "0"
                        )
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (sched.hasSchedStats) {
                BentoSwitchCard(
                    title = "Sched Stats",
                    checked = sched.schedStats == "1",
                    icon = Icons.Rounded.QueryStats,
                    activeColor = activeColor, cardBg = cardBg, contentColor = contentColor,
                    isGlassActive = isGlassActive, hazeState = hazeState, cardDarkness = cardDarkness,
                    onCheckedChange = { viewModel.updateValue(SchedulerUtils.SCHED_SCHEDSTATS, if (it) "1" else "0") }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (sched.hasTunableScaling) {
                BentoSwitchCard(
                    title = "Tunable Scaling",
                    checked = sched.tunableScaling == "1",
                    icon = Icons.Rounded.Tune,
                    activeColor = activeColor, cardBg = cardBg, contentColor = contentColor,
                    isGlassActive = isGlassActive, hazeState = hazeState, cardDarkness = cardDarkness,
                    onCheckedChange = {
                        viewModel.updateValue(
                            SchedulerUtils.SCHED_TUNABLE_SCALING,
                            if (it) "1" else "0"
                        )
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (sched.hasCstateAware) {
                BentoSwitchCard(
                    title = "C-State Aware",
                    checked = sched.cstateAware == "1",
                    icon = Icons.Rounded.BatteryStd,
                    activeColor = activeColor, cardBg = cardBg, contentColor = contentColor,
                    isGlassActive = isGlassActive, hazeState = hazeState, cardDarkness = cardDarkness,
                    onCheckedChange = { viewModel.updateValue(SchedulerUtils.SCHED_CSTATE_AWARE, if (it) "1" else "0") }
                )
            }
        }

        if (uclamp.hasUclampMax || uclamp.hasUclampMin) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                SectionTitle("UClamp Configuration", subContentColor)

                if (uclamp.hasUclampMax) {
                    BentoManualInputCard(
                        title = "UClamp Max",
                        value = uclamp.uclampMax,
                        cardBg = cardBg, contentColor = contentColor, subContentColor = subContentColor,
                        isGlassActive = isGlassActive, hazeState = hazeState, cardDarkness = cardDarkness
                    ) { openEditDialog("UClamp Max", SchedulerUtils.SCHED_UTIL_CLAMP_MAX, uclamp.uclampMax) }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (uclamp.hasUclampMin) {
                    BentoManualInputCard(
                        title = "UClamp Min",
                        value = uclamp.uclampMin,
                        cardBg = cardBg, contentColor = contentColor, subContentColor = subContentColor,
                        isGlassActive = isGlassActive, hazeState = hazeState, cardDarkness = cardDarkness
                    ) { openEditDialog("UClamp Min", SchedulerUtils.SCHED_UTIL_CLAMP_MIN, uclamp.uclampMin) }
                }
            }
        }

        if (genericTunables.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                SectionTitle("Advanced Parameters", subContentColor)
            }

            items(genericTunables) { item ->
                BentoManualInputCard(
                    title = item.name,
                    value = item.value,
                    cardBg = cardBg,
                    contentColor = contentColor,
                    subContentColor = subContentColor,
                    isGlassActive = isGlassActive, hazeState = hazeState, cardDarkness = cardDarkness,
                    onClick = { openEditDialog(item.name, item.path, item.value) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (showDialog) {
        val contentColor = MaterialTheme.colorScheme.onSurface
        val subContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        BlurredAlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    text = selectedItemName,
                    color = contentColor,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text("Current value: $editValue", color = subContentColor, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editValue,
                        onValueChange = { editValue = it },
                        label = { Text(stringResource(R.string.lp_new_value)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = contentColor,
                            unfocusedTextColor = contentColor,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedBorderColor = activeColor,
                            unfocusedBorderColor = subContentColor.copy(alpha = 0.5f),
                            cursorColor = activeColor,
                            focusedLabelColor = activeColor,
                            unfocusedLabelColor = subContentColor
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onSave() }) {
                    Text(stringResource(R.string.lp_apply), color = activeColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.lp_cancel_77dfd2), color = subContentColor)
                }
            }
        )
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun BentoSwitchCard(
    title: String,
    checked: Boolean,
    icon: ImageVector,
    activeColor: Color,
    cardBg: Color,
    contentColor: Color,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardDarkness: Float,
    onCheckedChange: (Boolean) -> Unit
) {
    val shape = RoundedCornerShape(16.dp)

    Card(
        modifier = if (isGlassActive) {
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .zkmCardHazeEffect(shape = shape,
                    state = hazeState,
                    style = ZkmHazeStyleCache.of(
                        backgroundColor = cardBg,
                        blurRadius = 30.dp,
                        noiseFactor = 0.08f,
                        tints = emptyList()
                    )
                )
                .zkmCardBorder(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.25f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    ),
                    shape = shape
                )
        } else {
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .clip(shape)
                .background(cardBg)
        },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else cardBg),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                ZkmBareIcon(imageVector = icon, tint = activeColor, size = 24.dp)
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }

            ZkmSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = activeColor,
                    uncheckedThumbColor = contentColor.copy(alpha = 0.6f),
                    uncheckedTrackColor = Color.Transparent,
                    uncheckedBorderColor = contentColor.copy(alpha = 0.3f)
                )
            )
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun BentoManualInputCard(
    title: String,
    value: String,
    cardBg: Color,
    contentColor: Color,
    subContentColor: Color,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardDarkness: Float,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)

    Card(
        modifier = if (isGlassActive) {
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clickable { onClick() }
                .zkmCardHazeEffect(shape = shape,
                    state = hazeState,
                    style = ZkmHazeStyleCache.of(
                        backgroundColor = cardBg,
                        blurRadius = 30.dp,
                        noiseFactor = 0.08f,
                        tints = emptyList()
                    )
                )
                .zkmCardBorder(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.25f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    ),
                    shape = shape
                )
        } else {
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clickable { onClick() }
                .clip(shape)
                .background(cardBg)
        },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else cardBg),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = subContentColor,
                    maxLines = 1
                )
            }
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = stringResource(R.string.lp_a11y_edit),
                tint = subContentColor.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
