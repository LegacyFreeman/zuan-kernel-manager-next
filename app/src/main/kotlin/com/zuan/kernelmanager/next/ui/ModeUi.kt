/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.core.mode.FeatureAccess
import com.zuan.kernelmanager.next.core.mode.WorkingMode
import com.zuan.kernelmanager.next.core.mode.WorkingModeManager
import com.zuan.kernelmanager.next.core.mode.nameRes
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme

/** Drop-in replacement for `composable(route)` that shows a "locked" screen when the working mode is too low. */
fun NavGraphBuilder.gatedComposable(
    navController: NavController,
    route: String,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) {
    composable(route) { entry ->
        ModeGate(route = route, navController = navController) {
            this@composable.content(entry)
        }
    }
}

@Composable
fun ModeGate(route: String, navController: NavController, content: @Composable () -> Unit) {
    val mode by WorkingModeManager.mode.collectAsStateWithLifecycle()
    if (WorkingModeManager.allows(route, mode)) {
        content()
    } else {
        LockedFeatureScreen(
            required = FeatureAccess.requiredMode(route),
            current = mode,
            onBack = { navController.popBackStack() }
        )
    }
}

@Composable
fun LockedFeatureScreen(required: WorkingMode, current: WorkingMode, onBack: () -> Unit) {
    val accent = modeTheme(required).first
    var showPicker by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f))
                    .border(1.5.dp, accent.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = accent, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.mode_locked_title, stringResource(current.nameRes())),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(required.nameRes()),
                    fontWeight = FontWeight.ExtraBold,
                    color = accent,
                    fontSize = 16.sp
                )
                Spacer(Modifier.width(8.dp))
                ModeBadge(required, zkmIsDarkTheme())
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.mode_locked_desc, stringResource(required.nameRes())),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { showPicker = true },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text(stringResource(R.string.mode_locked_switch), fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text(stringResource(R.string.mode_locked_back), fontWeight = FontWeight.SemiBold) }
        }
    }

    if (showPicker) {
        WorkingModePickerDialog(
            current = current,
            onDismiss = { showPicker = false },
            onApply = {
                showPicker = false
                WorkingModeManager.set(context, it)
            }
        )
    }
}

/** Change the working mode after the intro (Settings + locked screen). MainActivity reacts to the change. */
@Composable
fun WorkingModePickerDialog(
    current: WorkingMode,
    onDismiss: () -> Unit,
    onApply: (WorkingMode) -> Unit
) {
    var picked by remember { mutableStateOf(current) }
    val dark = zkmIsDarkTheme()

    com.zuan.kernelmanager.next.ui.components.BlurredAlertDialog(
        onDismissRequest = onDismiss,
        scrollableText = false, // isi sudah punya verticalScroll sendiri
        title = { Text(stringResource(R.string.mode_dialog_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.mode_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                WorkingMode.entries.forEach { mode ->
                    val accent = modeTheme(mode).first
                    val isPicked = picked == mode
                    val shape = RoundedCornerShape(18.dp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(if (isPicked) accent.copy(alpha = 0.12f) else Color.Transparent)
                            .border(
                                1.dp,
                                if (isPicked) accent.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant,
                                shape
                            )
                            .clickable(role = Role.RadioButton) { picked = mode }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(modeIcon(mode), contentDescription = null, tint = accent, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(mode.nameRes()),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.width(8.dp))
                                ModeBadge(mode, dark)
                            }
                            Text(
                                text = stringResource(modeDescRes(mode)),
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        RadioButton(selected = isPicked, onClick = { picked = mode })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(picked) }) { Text(stringResource(R.string.mode_dialog_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
