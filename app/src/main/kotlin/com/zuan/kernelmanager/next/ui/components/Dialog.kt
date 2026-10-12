/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalHazeMaterialsApi::class
)

package com.zuan.kernelmanager.next.ui.components

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.zuan.kernelmanager.next.ui.adaptive.ZkmDialogSafeArea
import com.zuan.kernelmanager.next.ui.adaptive.zkmDialogMaxHeight
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.core.Dialog
import com.composables.core.DialogPanel
import com.composables.core.DialogProperties
import com.composables.core.DialogState
import com.composables.core.Scrim
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

@Composable
fun DialogUnstyled(
    state: DialogState,
    title: String? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null,
    hazeState: HazeState? = null,
    isCustomBg: Boolean = false,
    // true bila isi 'text' bukan list/scroll sendiri (hindari scroll bersarang).
    scrollableText: Boolean = false
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // [1] Warna Panel Dialog (Isinya)
    // Menggunakan logika DialogNEW agar tetap transparan (glassy)
    // Same glass look for every dialog; isCustomBg/hazeState are kept only for source compatibility.
    val dialogContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = ZkmDialogDefaults.MaxContainerAlpha)
    val mainTextColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    // Properties Default
    val properties = DialogProperties()

    Dialog(state = state, properties = properties) {

        // [2] NATIVE WINDOW BLUR (Android 12+)
        // Hanya aktifkan Native Blur jika HazeState TIDAK ada.
        // Jika HazeState ada, kita prioritaskan Haze (Compose Blur).
        ConfigureDialogWindowBlur()

        // Android 12+ blurs the window itself; older versions fall back to a plain dim scrim.
        val scrimColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Color.Transparent else Color.Black.copy(alpha = 0.5f)

        Scrim(
            modifier = Modifier.fillMaxSize(),
            scrimColor = scrimColor,
            enter = fadeIn(),
            exit = fadeOut()
        )

        val dialogShape = ZkmDialogDefaults.Shape

        // Area aman: bebas taskbar PC mode / status bar / cutout, dan tinggi panel diukur dari
        // ruang yang benar-benar tersisa (bukan dari konfigurasi layar).
        ZkmDialogSafeArea {
            val dialogModifier = Modifier
                .widthIn(min = 280.dp, max = 560.dp)
                .heightIn(max = zkmDialogMaxHeight())
                .clip(dialogShape)

            DialogPanel(
                modifier = dialogModifier,
                backgroundColor = dialogContainerColor,
                contentColor = mainTextColor,
                shape = dialogShape,
                enter = fadeIn(spring(stiffness = Spring.StiffnessHigh)) + scaleIn(initialScale = 0.8f),
                exit = scaleOut(targetScale = 0.6f) + fadeOut(tween(durationMillis = 150)),
            ) {
                Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                    title?.let {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = mainTextColor,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        )
                    }

                    text?.let {
                        androidx.compose.runtime.CompositionLocalProvider(
                            androidx.compose.material3.LocalContentColor provides subTextColor
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .padding(bottom = 24.dp)
                                    .then(if (scrollableText) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                            ) { text() }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        dismissButton?.let { Box(modifier = Modifier.weight(1f, fill = false)) { dismissButton() } }
                        Spacer(modifier = Modifier.width(8.dp))
                        confirmButton?.let { Box(modifier = Modifier.weight(1f, fill = false)) { confirmButton() } }
                    }
                }
            }
        }
    }
}

@Composable
fun DialogTextButton(
    icon: Any? = null,
    text: String,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.primary
) {
    TextButton(
        onClick = onClick,
        shape = ButtonDefaults.shape,
        colors = ButtonDefaults.textButtonColors(contentColor = color)
        ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                when (icon) {
                    is ImageVector -> Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                        tint = color
                    )
                    is Painter -> Icon(
                        painter = icon,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                        tint = color
                    )
                }
            }
            Text(text = text, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}
