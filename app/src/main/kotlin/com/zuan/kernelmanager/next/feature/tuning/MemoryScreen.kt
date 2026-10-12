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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.library.device.tuning.MemoryUtils
import com.zuan.kernelmanager.next.feature.settings.SettingsViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import com.zuan.kernelmanager.next.ui.components.ZkmHazeStyleCache
import com.zuan.kernelmanager.next.ui.theme.ZkmBareIcon
import com.zuan.kernelmanager.next.ui.components.ZkmSwitch
import com.zuan.kernelmanager.next.ui.components.ZkmSlider

@Composable
fun MemoryScreen(
    viewModel: MemoryViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    hazeState: HazeState,
    cardBg: Color,
    contentColor: Color,
    subContentColor: Color,
    activeColor: Color,
    contentPadding: PaddingValues = PaddingValues(top = 180.dp, bottom = 130.dp)
) {
    val mem by viewModel.mem.collectAsStateWithLifecycle()
    val zramState by viewModel.zram.collectAsStateWithLifecycle()
    val ioDevices by viewModel.ioDevices.collectAsStateWithLifecycle()
    
    val applyOnBootVmIo by viewModel.applyOnBootVmIo.collectAsStateWithLifecycle()
    val applyOnBootZram by viewModel.applyOnBootZram.collectAsStateWithLifecycle()

    val isCustomBg by settingsViewModel.isCustomBackground.collectAsStateWithLifecycle()
    val isHazeEnabled by settingsViewModel.isHazeEnabled.collectAsStateWithLifecycle()
    val cardDarkness by settingsViewModel.cardDarkness.collectAsStateWithLifecycle()
    val isGlassActive = isCustomBg && isHazeEnabled

    LazyColumn(
        modifier = Modifier.fillMaxSize(), 
        contentPadding = contentPadding
    ) {
        
        item {
            val shape = RoundedCornerShape(16.dp)
            Box(
                modifier = if (isGlassActive) {
                    Modifier
                        .fillMaxWidth()
                        .zkmReferenceGlassSurface(hazeState, shape)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(cardBg)
                }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp) 
                            .background(activeColor.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.RestartAlt, null, tint = activeColor, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Apply on Boot", 
                            style = MaterialTheme.typography.titleMedium, 
                            fontWeight = FontWeight.Bold, 
                            color = contentColor
                        )
                        Text(
                            text = "Restore memory settings after reboot", 
                            style = MaterialTheme.typography.bodySmall, 
                            color = subContentColor
                        )
                    }
                    ZkmSwitch(
                        checked = applyOnBootVmIo, 
                        onCheckedChange = { viewModel.setApplyOnBootVmIo(it) },
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
            SectionTitle(stringResource(R.string.zram_manager), subContentColor)
            ZramCard(
                state = zramState, 
                viewModel = viewModel, 
                applyOnBootZram = applyOnBootZram, 
                activeColor = activeColor, 
                cardBg = cardBg, 
                contentColor = contentColor, 
                subContentColor = subContentColor,
                isGlassActive = isGlassActive,
                hazeState = hazeState,
                cardDarkness = cardDarkness
            )
            Spacer(modifier = Modifier.height(12.dp))

            SectionTitle(stringResource(R.string.io_scheduler), subContentColor)
        }

        if (ioDevices.isNotEmpty()) {
            items(ioDevices) { device ->
                IODeviceCard(
                    device = device,
                    viewModel = viewModel,
                    activeColor = activeColor, 
                    cardBg = cardBg,
                    contentColor = contentColor,
                    subContentColor = subContentColor,
                    isGlassActive = isGlassActive,
                    hazeState = hazeState,
                    cardDarkness = cardDarkness
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        } else {
            item {
                Text(
                    text = stringResource(R.string.no_storage_devices),
                    style = MaterialTheme.typography.bodyMedium,
                    color = subContentColor,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
            SectionTitle(stringResource(R.string.virtual_memory), subContentColor)
            
            if (mem.hasSwappiness) {
                SmartSlider(stringResource(R.string.swappiness), mem.swappiness, MemoryUtils.SWAPPINESS, viewModel, activeColor, cardBg, contentColor, subContentColor, isGlassActive, hazeState, cardDarkness)
                Spacer(modifier=Modifier.height(8.dp))
            }
            if (mem.hasVfsCachePressure) {
                SmartSlider(stringResource(R.string.vfs_cache_pressure), mem.vfsCachePressure, MemoryUtils.VFS_CACHE_PRESSURE, viewModel, activeColor, cardBg, contentColor, subContentColor, isGlassActive, hazeState, cardDarkness)
                Spacer(modifier=Modifier.height(8.dp))
            }
            if (mem.hasDirtyRatio) {
                SmartSlider(stringResource(R.string.dirty_ratio), mem.dirtyRatio, MemoryUtils.DIRTY_RATIO, viewModel, activeColor, cardBg, contentColor, subContentColor, isGlassActive, hazeState, cardDarkness)
                Spacer(modifier=Modifier.height(8.dp))
            }
            if (mem.hasDirtyBackgroundRatio) {
                SmartSlider(stringResource(R.string.dirty_bg_ratio), mem.dirtyBackgroundRatio, MemoryUtils.DIRTY_BACKGROUND_RATIO, viewModel, activeColor, cardBg, contentColor, subContentColor, isGlassActive, hazeState, cardDarkness)
            }
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun ZramCard(
    state: MemoryViewModel.ZramState,
    viewModel: MemoryViewModel,
    applyOnBootZram: Boolean,
    activeColor: Color, cardBg: Color, contentColor: Color, subContentColor: Color,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardDarkness: Float
) {
    var showDialog by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    
    val modifier = if (isGlassActive) {
        Modifier
            .fillMaxWidth()
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
            .fillMaxWidth()
            .clip(shape)
            .background(cardBg)
    }
    
    val containerColor = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else cardBg
    val maxZram = 20480f
    val currentProgress = if (maxZram > 0) (state.sizeMb.toFloat() / maxZram).coerceIn(0f, 1f) else 0f

    Card(
        colors = CardDefaults.cardColors(containerColor = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else containerColor),
        shape = shape,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { currentProgress },
                    color = activeColor,
                    trackColor = subContentColor.copy(alpha = 0.2f),
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 4.dp
                )
                Text(
                    text = "${(currentProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ZRAM", 
                    style = MaterialTheme.typography.labelMedium, 
                    color = subContentColor
                )
                Text(
                    text = stringResource(R.string.zram_status), 
                    style = MaterialTheme.typography.titleMedium, 
                    color = activeColor, 
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${state.sizeMb}MB  ${state.activeAlgo}", 
                    style = MaterialTheme.typography.bodySmall, 
                    color = subContentColor
                )
            }
            
            Button(
                onClick = { showDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = activeColor.copy(alpha = 0.15f), 
                    contentColor = activeColor
                ),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(stringResource(R.string.lp_adjust), fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showDialog) {
        AdjustZramDialog(
            state = state,
            applyOnBoot = applyOnBootZram, 
            onApplyOnBootChange = { viewModel.setApplyOnBootZram(it) }, 
            activeColor = activeColor,
            contentColor = contentColor,
            subContentColor = subContentColor,
            cardBg = cardBg,
            onDismiss = { showDialog = false },
            onConfirm = { newSize, newAlgo ->
                if (newSize != state.sizeMb || newAlgo != state.activeAlgo) {
                    viewModel.applyZramChanges(newSize, newAlgo)
                }
                showDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdjustZramDialog(
    state: MemoryViewModel.ZramState,
    applyOnBoot: Boolean, 
    onApplyOnBootChange: (Boolean) -> Unit, 
    activeColor: Color,
    contentColor: Color,
    subContentColor: Color,
    cardBg: Color,
    onDismiss: () -> Unit,
    onConfirm: (Int, String) -> Unit
) {
    var sliderVal by remember { mutableFloatStateOf(state.sizeMb.toFloat()) }
    var selectedAlgo by remember { mutableStateOf(state.activeAlgo) }
    var showAlgoMenu by remember { mutableStateOf(false) }
    
    val maxZram = 20480f

    val contentColor = MaterialTheme.colorScheme.onSurface
    val subContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    BlurredAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.lp_adjust_the_zram), fontWeight = FontWeight.Bold, color = contentColor)
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringResource(R.string.lp_now_drag_the_slider_to_resize_zram), style = MaterialTheme.typography.bodySmall, color = subContentColor)
            }
        },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${sliderVal.toInt()}MB", color = subContentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(8.dp))

                ZkmSlider(
                    value = sliderVal,
                    onValueChange = { sliderVal = it },
                    onValueChangeFinished = {
                        sliderVal = ((sliderVal.toInt() / 128) * 128).toFloat() 
                    },
                    valueRange = 0f..maxZram,
                    steps = (maxZram / 128).toInt() - 1,
                    colors = SliderDefaults.colors(
                        thumbColor = activeColor, 
                        activeTrackColor = activeColor,
                        inactiveTrackColor = subContentColor.copy(alpha = 0.2f)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.lp_algorithm), color = subContentColor, style = MaterialTheme.typography.bodyMedium)
                    Box {
                        Text(
                            text = selectedAlgo,
                            color = activeColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showAlgoMenu = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                        DropdownMenu(
                            expanded = showAlgoMenu,
                            onDismissRequest = { showAlgoMenu = false },
                            modifier = Modifier.background(cardBg)
                        ) {
                            state.availableAlgos.forEach { algo ->
                                DropdownMenuItem(
                                    text = { Text(algo, color = contentColor) },
                                    onClick = {
                                        selectedAlgo = algo
                                        showAlgoMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "ZRAM is one way to implement SWAP. Improve multitasking by compressing data in memory.",
                        style = MaterialTheme.typography.bodySmall,
                        color = subContentColor,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(activeColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.RestartAlt, contentDescription = null, tint = activeColor)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.lp_apply_on_boot), style = MaterialTheme.typography.bodyMedium, color = contentColor, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.lp_auto_apply_settings), style = MaterialTheme.typography.labelSmall, color = subContentColor)
                        }
                        ZkmSwitch(
                            checked = applyOnBoot, 
                            onCheckedChange = { onApplyOnBootChange(it) }, 
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = activeColor
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(sliderVal.toInt(), selectedAlgo) },
                colors = ButtonDefaults.buttonColors(containerColor = activeColor)
            ) {
                Text(stringResource(R.string.lp_ok), color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.lp_cancel_1507c4), color = subContentColor)
            }
        }
    )
}

@OptIn(ExperimentalHazeMaterialsApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun IODeviceCard(
    device: MemoryViewModel.IODeviceState,
    viewModel: MemoryViewModel,
    activeColor: Color, cardBg: Color, contentColor: Color, subContentColor: Color,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardDarkness: Float
) {
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)

    val modifier = if (isGlassActive) {
        Modifier
            .fillMaxWidth()
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
            .fillMaxWidth()
            .clip(shape)
            .background(cardBg)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else cardBg),
        shape = shape,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                ZkmBareIcon(imageVector = Icons.Rounded.Storage, tint = activeColor, size = 24.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(device.name.uppercase(), style = MaterialTheme.typography.titleMedium, color = contentColor, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.scheduler_format, device.activeScheduler), style = MaterialTheme.typography.bodySmall, color = subContentColor)
                }
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = subContentColor
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(color = subContentColor.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(stringResource(R.string.select_scheduler), style = MaterialTheme.typography.labelMedium, color = subContentColor)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        device.availableSchedulers.forEach { sched ->
                            FilterChip(
                                selected = sched == device.activeScheduler,
                                onClick = { viewModel.setIOScheduler(device.name, sched) },
                                label = { Text(sched) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = activeColor, selectedLabelColor = Color.White)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(stringResource(R.string.tunables), style = MaterialTheme.typography.labelMedium, color = subContentColor)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    IOTunableInput("nr_requests", device.nrRequests, device.name, viewModel, contentColor, activeColor)
                    IOTunableInput("read_ahead_kb", device.readAheadKb, device.name, viewModel, contentColor, activeColor)
                    IOTunableInput("rq_affinity", device.rqAffinity, device.name, viewModel, contentColor, activeColor)
                    IOTunableInput("rotational", device.rotational, device.name, viewModel, contentColor, activeColor)
                    IOTunableInput("add_random", device.addRandom, device.name, viewModel, contentColor, activeColor)
                    IOTunableInput("iostats", device.iostats, device.name, viewModel, contentColor, activeColor)
                }
            }
        }
    }
}

@Composable
fun IOTunableInput(
    label: String,
    currentValue: String,
    devName: String,
    viewModel: MemoryViewModel,
    textColor: Color,
    activeColor: Color
) {
    var textState by remember(currentValue) { mutableStateOf(currentValue) }

    OutlinedTextField(
        value = textState,
        onValueChange = { textState = it },
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = textColor,
            unfocusedTextColor = textColor,
            focusedBorderColor = activeColor,
            unfocusedBorderColor = textColor.copy(alpha = 0.3f)
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = {
                viewModel.setIOTunable(devName, label, textState)
            }
        ),
        trailingIcon = {
            IconButton(onClick = { viewModel.setIOTunable(devName, label, textState) }) {
                Text(stringResource(R.string.btn_set).uppercase(), fontSize = 10.sp, color = activeColor, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun SmartSlider(
    title: String,
    systemValue: String,
    path: String,
    viewModel: MemoryViewModel,
    activeColor: Color, cardBg: Color, contentColor: Color, subContentColor: Color,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardDarkness: Float
) {
    val sysFloat = systemValue.replace(Regex("[^0-9]"), "").toFloatOrNull() ?: 0f
    var localValue by remember(sysFloat) { mutableFloatStateOf(sysFloat) }
    
    GlassSliderCard(
        title = title,
        value = localValue.toInt().toString(), 
        activeColor = activeColor, 
        cardBg = cardBg, 
        contentColor = contentColor, 
        subContentColor = subContentColor,
        isGlassActive = isGlassActive,
        hazeState = hazeState,
        cardDarkness = cardDarkness,
        onSliderChange = { newValue -> localValue = newValue },
        onInteractionEnd = { finalValue -> viewModel.updateVmValue(path, finalValue.toInt().toString()) }
    )
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun GlassSliderCard(
    title: String,
    value: String,
    activeColor: Color,
    cardBg: Color,
    contentColor: Color,
    subContentColor: Color,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardDarkness: Float,
    onSliderChange: (Float) -> Unit,
    onInteractionEnd: (Float) -> Unit
) {
    val shape = RoundedCornerShape(16.dp)

    val modifier = if (isGlassActive) {
        Modifier
            .fillMaxWidth()
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
            .fillMaxWidth()
            .clip(shape)
            .background(cardBg)
    }

    val currentValueFloat = value.toFloatOrNull() ?: 0f

    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else cardBg),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = activeColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            ZkmSlider(
                value = currentValueFloat,
                onValueChange = onSliderChange,
                onValueChangeFinished = { onInteractionEnd(currentValueFloat) },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = activeColor,
                    activeTrackColor = activeColor,
                    inactiveTrackColor = subContentColor.copy(alpha = 0.2f)
                )
            )
        }
    }
}
