/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.zuan.kernelmanager.next.feature.fps

import com.zuan.kernelmanager.next.core.mode.WorkingMode
import com.zuan.kernelmanager.next.core.mode.WorkingModeManager
import com.zuan.kernelmanager.next.library.shell.AxeronShell
import com.zuan.kernelmanager.next.library.shell.PrivilegedAccess
import com.zuan.kernelmanager.next.library.shell.ShizukuShell
import com.zuan.kernelmanager.next.ui.components.zkmGlassFallbackTint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.library.service.FpsOverlayController
import com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime
import com.zuan.kernelmanager.next.library.device.monitor.FpsMode
import com.zuan.kernelmanager.next.library.device.monitor.FpsReader
import com.zuan.kernelmanager.next.library.shell.suBlocking
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import com.zuan.kernelmanager.next.ui.components.zkmGlassCard
import com.zuan.kernelmanager.next.ui.components.zkmGlassTint
import com.zuan.kernelmanager.next.ui.components.LocalZkmHazeState
import com.zuan.kernelmanager.next.ui.theme.zkmIconShape
import com.zuan.kernelmanager.next.ui.theme.ZkmIconGlyph

@Composable
fun FpsManagerHomeContent(
    cardBg: Color,
    contentColor: Color,
    subContentColor: Color,
    activeColor: Color,
    elevation: Dp,
    isGlassActive: Boolean = false,
    hazeState: HazeState,
    glassModifier: Modifier = Modifier,
    topPadding: Dp // DITAMBAHKAN
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isServiceRunning by remember { mutableStateOf(FpsOverlayController.isRunning.value) }
    var hasRootAccess by remember { mutableStateOf(false) }
    var isCheckingRoot by remember { mutableStateOf(true) }
    
    var currentReaderMode by remember { mutableStateOf(FpsReader.currentMode) }

    // Shizuku may come online after this tab opened: re-check without leaving the page.
    DisposableEffect(Unit) {
        val listener: () -> Unit = {
            scope.launch(Dispatchers.IO) {
                hasRootAccess = PrivilegedAccess.shellWanted() && suBlocking("id").isSuccess
                isCheckingRoot = false
            }
        }
        PrivilegedAccess.addEpochListener(listener)
        onDispose { PrivilegedAccess.removeEpochListener(listener) }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            KernelRootRuntime.initialize(context)
            FpsReader.init(context)
            currentReaderMode = FpsReader.currentMode
            // "root" here really means "a privileged shell for the active mode": Shizuku in ADB
            // mode, su in ROOT mode. Basic mode has none.
            hasRootAccess = PrivilegedAccess.shellWanted() && suBlocking("id").isSuccess
            isCheckingRoot = false
        }
        
        // [BUG FIX] Sama seperti FpsOverlayTab: StateFlow di-polling tiap 1s.
        // Diganti collect — instan, tanpa loop, tanpa waker CPU tiap detik.
        FpsOverlayController.isRunning.collect { running ->
            isServiceRunning = running
        }
    }

    LazyColumn(
        // DIPERBARUI: Meneruskan topPadding
        contentPadding = PaddingValues(top = topPadding + 8.dp, bottom = 100.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            ServiceStatusHeroCard(
                isRunning = isServiceRunning,
                hasRoot = hasRootAccess,
                isChecking = isCheckingRoot,
                onToggleService = { 
                    if (!hasRootAccess) {
                        if (WorkingModeManager.current == WorkingMode.ADB) {
                            if (ShizukuShell.isRunning() && !ShizukuShell.isGranted()) ShizukuShell.requestPermission()
                            if (AxeronShell.isRunning() && !AxeronShell.isGranted()) AxeronShell.requestPermission(context)
                            if (!ShizukuShell.isRunning() && !AxeronShell.isRunning()) {
                                ShizukuShell.requestPermission()
                                AxeronShell.requestPermission(context)
                            }
                            Toast.makeText(context, context.getString(R.string.fps_shell_needed), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, context.getString(R.string.fps_root_needed), Toast.LENGTH_SHORT).show()
                        }
                        return@ServiceStatusHeroCard
                    }
                    
                    if (!Settings.canDrawOverlays(context)) {
                        Toast.makeText(context, context.getString(R.string.lp_toast_overlay_required), Toast.LENGTH_LONG).show()
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                        return@ServiceStatusHeroCard
                    }
                    
                    if (isServiceRunning) {
                        FpsOverlayController.stop(context)
                        isServiceRunning = false 
                    } else {
                        FpsOverlayController.start(context)
                        isServiceRunning = true
                    }
                },
                activeColor = activeColor,
                isGlassActive = isGlassActive,
                glassModifier = glassModifier
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            SectionTitle(stringResource(R.string.fps_system_access), activeColor)
            RootStatusCard(hasRootAccess, isCheckingRoot, activeColor, isGlassActive, glassModifier)
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            SectionTitle(stringResource(R.string.fps_reader_mode), activeColor)
            
            GlassCard(
                isGlassActive = isGlassActive,
                glassModifier = glassModifier,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    
                    ReaderModeOption(
                        title = stringResource(R.string.fps_mode_auto),
                        description = stringResource(R.string.fps_mode_auto_desc),
                        isSelected = currentReaderMode == FpsMode.AUTO,
                        activeColor = activeColor,
                        onClick = {
                            FpsReader.setMode(context, FpsMode.AUTO)
                            currentReaderMode = FpsMode.AUTO
                        }
                    )

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                    ReaderModeOption(
                        title = stringResource(R.string.fps_mode_universal_android),
                        description = stringResource(R.string.fps_mode_android_desc),
                        isSelected = currentReaderMode == FpsMode.UNIVERSAL_ANDROID,
                        activeColor = activeColor,
                        onClick = {
                            FpsReader.setMode(context, FpsMode.UNIVERSAL_ANDROID)
                            currentReaderMode = FpsMode.UNIVERSAL_ANDROID
                        }
                    )
                    
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                    ReaderModeOption(
                        title = stringResource(R.string.fps_mode_universal_gpu),
                        description = stringResource(R.string.fps_mode_gpu_desc),
                        isSelected = currentReaderMode == FpsMode.UNIVERSAL_GPU,
                        activeColor = activeColor,
                        onClick = {
                            FpsReader.setMode(context, FpsMode.UNIVERSAL_GPU)
                            currentReaderMode = FpsMode.UNIVERSAL_GPU
                        }
                    )

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                    ReaderModeOption(
                        title = stringResource(R.string.fps_mode_universal_devices),
                        description = stringResource(R.string.fps_mode_devices_desc),
                        isSelected = currentReaderMode == FpsMode.UNIVERSAL_BETA,
                        isBeta = true,
                        activeColor = activeColor,
                        onClick = {
                            FpsReader.setMode(context, FpsMode.UNIVERSAL_BETA)
                            currentReaderMode = FpsMode.UNIVERSAL_BETA
                        }
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
            FpsModeInfoCard(
                highlightNoRoot = currentReaderMode == FpsMode.UNIVERSAL_BETA || currentReaderMode == FpsMode.AUTO,
                activeColor = activeColor,
                isGlassActive = isGlassActive,
                glassModifier = glassModifier
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            SectionTitle(stringResource(R.string.fps_device_env), activeColor)
            DeviceInfoCard(activeColor, isGlassActive, glassModifier) 
        }
    }
}

/** Penjelasan cara kerja & batasan pembacaan FPS tanpa root (Shizuku) - dilipat, terbuka otomatis untuk Beta/Auto. */
@Composable
fun FpsModeInfoCard(
    highlightNoRoot: Boolean,
    activeColor: Color,
    isGlassActive: Boolean,
    glassModifier: Modifier
) {
    var expanded by remember(highlightNoRoot) { mutableStateOf(highlightNoRoot) }
    GlassCard(
        isGlassActive = isGlassActive,
        glassModifier = glassModifier,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Column(Modifier.clickable { expanded = !expanded }.padding(16.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null, tint = activeColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.fps_info_title),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                listOf(
                    R.string.fps_info_p1, R.string.fps_info_p2, R.string.fps_info_p3,
                    R.string.fps_info_p4, R.string.fps_info_p5
                ).forEach { res ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text("•", color = activeColor, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(res),
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReaderModeOption(
    title: String,
    description: String,
    isSelected: Boolean,
    isBeta: Boolean = false,
    activeColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isBeta) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .border(1.dp, activeColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.fps_beta_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = activeColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SectionTitle(title: String, color: Color) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = color,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 32.dp, bottom = 8.dp)
    )
}

@Composable
fun ServiceStatusHeroCard(
    isRunning: Boolean,
    hasRoot: Boolean,
    isChecking: Boolean,
    onToggleService: () -> Unit, 
    activeColor: Color,
    isGlassActive: Boolean = false,
    glassModifier: Modifier = Modifier
) {
    val containerColor = if (isRunning) activeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHighest
    val buttonContainerColor = if (!hasRoot && !isChecking) Color.Gray else if (isRunning) MaterialTheme.colorScheme.surface else activeColor
    val buttonContentColor = if (!hasRoot && !isChecking) Color.White else if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimary
    
    // [FIX] Dulu glassModifier (clip+blur+border) dipasang SEBELUM padding sehingga blur &
    // border melebar penuh sementara card-nya sempit ("card di dalam card"). Sekarang
    // padding dulu, baru glass + tint solid, shape disamakan dengan Card.
    val heroShape = RoundedCornerShape(28.dp)
    val heroHaze = LocalZkmHazeState.current
    val heroTint = zkmGlassTint(if (isRunning) activeColor else MaterialTheme.colorScheme.surfaceContainerHighest, zkmIsDarkTheme(), 0.35f)
    val cardModifier = if (isGlassActive) {
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).zkmGlassCard(true, heroHaze, heroShape, heroTint, 24.dp)
    } else {
        Modifier.fillMaxWidth().padding(horizontal = 24.dp)
    }
    
    Card(
        modifier = cardModifier,
        shape = heroShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isGlassActive) Color.Transparent else containerColor
        )
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(48.dp).clip(zkmIconShape(CircleShape)).background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        ZkmIconGlyph(icon = if (isRunning) Icons.Default.CheckCircle else Icons.Outlined.CheckCircle, tint = if (isRunning) activeColor else MaterialTheme.colorScheme.outline, iconSize = 24.dp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(stringResource(R.string.fps_overlay_service), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (isRunning) stringResource(R.string.fps_service_active) else stringResource(R.string.fps_service_stopped), 
                            style = MaterialTheme.typography.bodyMedium, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onToggleService,
                enabled = hasRoot || isChecking,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonContainerColor, 
                    contentColor = buttonContentColor, 
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant, 
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
        ) {
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onSurface, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.fps_checking_root))
                } else if (!hasRoot) {
                    Icon(Icons.Default.Lock, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(if (WorkingModeManager.current == WorkingMode.ADB) R.string.fps_shell_needed else R.string.fps_root_needed))
                } else {
                    Icon(if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isRunning) stringResource(R.string.fps_stop_service) else stringResource(R.string.fps_start_service), 
                        style = MaterialTheme.typography.titleMedium, 
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun RootStatusCard(
    hasRoot: Boolean, 
    isChecking: Boolean, 
    activeColor: Color,
    isGlassActive: Boolean = false,
    glassModifier: Modifier = Modifier
) {
    GlassCard(
        isGlassActive = isGlassActive,
        glassModifier = glassModifier,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
    ) {
      Column {
        ListItem(
            headlineContent = { 
                val adb = WorkingModeManager.current == WorkingMode.ADB
                Text(
                    if (hasRoot) stringResource(if (adb) R.string.fps_shell_granted else R.string.fps_root_granted)
                    else stringResource(if (adb) R.string.fps_shell_not_found else R.string.fps_root_not_found),
                    fontWeight = FontWeight.Bold
                ) 
            },
            supportingContent = { 
                Text(
                    if(isChecking) stringResource(R.string.fps_checking_root) 
                    else if (WorkingModeManager.current == WorkingMode.ADB) {
                        val base = stringResource(if (hasRoot) R.string.fps_shell_available_desc else R.string.fps_shell_missing_desc)
                        // Diagnostic: show WHY the ADB shell failed (binder, permission, exec error ...).
                        val why = if (hasRoot) null else KernelRootRuntime.lastErrorText()
                        val diag = "Shizuku[run=${ShizukuShell.isRunning()} grant=${ShizukuShell.isGranted()} uid=${ShizukuShell.cachedServerUid()}] Axeron[run=${AxeronShell.isRunning()} grant=${AxeronShell.isGranted()} uid=${AxeronShell.cachedServerUid()}]"
                        if (why.isNullOrBlank()) "$base\n[$diag]" else "$base\n[$why | $diag]"
                    }
                    else if(hasRoot) stringResource(R.string.fps_root_available_desc) 
                    else stringResource(R.string.fps_root_missing_desc)
                ) 
            },
            leadingContent = { 
                Icon(
                    if(hasRoot) Icons.Default.Security else Icons.Default.Warning, 
                    null, 
                    tint = if(hasRoot) activeColor else MaterialTheme.colorScheme.error
                ) 
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        // Live FPS-engine diagnostics: which stage answered / failed (updates while the overlay runs).
        if (hasRoot) {
            val diag by produceState(initialValue = "") {
                while (true) {
                    value = withContext(Dispatchers.IO) { FpsReader.getDiagnostics() }
                    delay(1500L)
                }
            }
            if (diag.isNotBlank()) {
                Text(
                    diag,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                )
            }
        }
      }
    }
}

@Composable
fun DeviceInfoCard(
    activeColor: Color,
    isGlassActive: Boolean = false,
    glassModifier: Modifier = Modifier
) {
    GlassCard(
        isGlassActive = isGlassActive,
        glassModifier = glassModifier,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
    ) {
        Column {
            PixelInfoRow(stringResource(R.string.fps_manufacturer), Build.MANUFACTURER.uppercase(), Icons.Default.PhoneAndroid)
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            PixelInfoRow(stringResource(R.string.fps_model), Build.MODEL, Icons.Default.PhoneAndroid)
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            PixelInfoRow(stringResource(R.string.fps_android_ver), "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", Icons.Default.Memory)
        }
    }
}

@Composable
fun GlassCard(
    isGlassActive: Boolean,
    glassModifier: Modifier,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    content: @Composable () -> Unit
) {
    if (isGlassActive) {
        // [FIX] Hapus glassModifier lama (shape 24dp terkunci + border ganda + tanpa warna dasar).
        Box(
            modifier = modifier.zkmGlassCard(
                isGlassActive = true,
                hazeState = LocalZkmHazeState.current,
                shape = shape,
                tint = zkmGlassTint(MaterialTheme.colorScheme.surfaceContainer, zkmIsDarkTheme(), 0.45f),
                blurRadius = 24.dp
            ),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            content()
        }
    }
}

@Composable
fun PixelInfoRow(label: String, value: String, icon: ImageVector) {
    ListItem(
        headlineContent = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        trailingContent = { Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
