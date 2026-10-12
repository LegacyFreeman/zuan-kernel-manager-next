/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package com.zuan.kernelmanager.next.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.kyant.backdrop.Backdrop
import com.zuan.kernelmanager.next.ui.adaptive.ZkmDialogSafeArea

/**
 * KENAPA FILE INI ADA
 *
 * Compose `Dialog { }` membuat WINDOW Android terpisah. Layer backdrop
 * (wallpaper + konten) direkam di window Activity utama, jadi `drawBackdrop`
 * di dalam window dialog tidak bisa membaca/menggambar layer tersebut:
 * panel menjadi transparan tanpa efek kaca (inilah kenapa "Dialog Android
 * Liquid Glass" sebelumnya tidak jalan).
 *
 * Solusi: dialog liquid glass TIDAK dibuat sebagai window. Ia di-"teleport"
 * ke [ZkmDialogHost] yang dipasang di root app, di dalam window & composition
 * yang sama dengan layer backdrop, lewat [ZkmLiquidOverlayDialog].
 */
@Stable
class ZkmDialogHostState {
    internal class Entry(val key: Any, val content: @Composable () -> Unit)

    internal val entries = mutableStateListOf<Entry>()
}

/** Host dialog aktif; null di luar ZuanKernelManagerApp (fallback ke Dialog window biasa). */
val LocalZkmDialogHost = staticCompositionLocalOf<ZkmDialogHostState?> { null }

/** true bila composable berada di dalam overlay dialog liquid glass. */
val LocalZkmInLiquidDialog = staticCompositionLocalOf { false }

/** Spec kaca tetap untuk panel dialog (terpisah dari spec kartu). */
val ZkmLiquidDialogSpec = ZkmLiquidGlassSpec(
    cornerRadius = 28.dp,
    blurRadius = 14.dp,
    refractionHeight = 16.dp,
    refractionAmount = 24.dp,
    chromaticAberration = false,
)

/**
 * Pasang SEKALI di root app, sebagai sibling TERAKHIR (di atas Scaffold dan nav
 * bar) tetapi BUKAN di dalam `layerBackdrop(...)` apa pun, supaya tidak ada
 * siklus RenderNode. [backdrop] = gabungan layer wallpaper + layer konten.
 */
@Composable
fun ZkmDialogHost(state: ZkmDialogHostState, backdrop: Backdrop) {
    CompositionLocalProvider(
        LocalZkmLiquidBackdrop provides backdrop,
        LocalZkmInLiquidDialog provides true,
    ) {
        state.entries.forEach { entry ->
            key(entry.key) { entry.content() }
        }
    }
}

/** Mendaftarkan [content] ke [ZkmDialogHost] selama composable ini ada di komposisi. */
@Composable
fun ZkmLiquidOverlayDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    val host = LocalZkmDialogHost.current ?: return
    val id = remember { Any() }
    val latestContent by rememberUpdatedState(content)
    val latestDismiss by rememberUpdatedState(onDismissRequest)
    val latestProps by rememberUpdatedState(properties)

    DisposableEffect(host, id) {
        val entry = ZkmDialogHostState.Entry(id) {
            ZkmOverlayDialogLayer(
                onDismiss = { latestDismiss() },
                properties = latestProps,
            ) { latestContent() }
        }
        host.entries.add(entry)
        onDispose { host.entries.remove(entry) }
    }
}

@Composable
private fun ZkmOverlayDialogLayer(
    onDismiss: () -> Unit,
    properties: DialogProperties,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "ZkmOverlayDialogEnter"
    )

    // Selalu tangkap Back selama overlay tampil (supaya Back tidak menembus ke layar di belakang).
    BackHandler(enabled = true) {
        if (properties.dismissOnBackPress) onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f * progress))
            .pointerInput(properties.dismissOnClickOutside) {
                detectTapGestures {
                    if (properties.dismissOnClickOutside) onDismiss()
                }
            }
    ) {
        // Area aman yang sama dengan dialog window: bebas taskbar PC mode / status bar / cutout / IME,
        // dan memberi tinggi maksimum panel dari ruang yang benar-benar tersisa.
        ZkmDialogSafeArea {
            Box(
                modifier = Modifier
                    // Tap di panel tidak boleh dianggap "tap di luar".
                    .pointerInput(Unit) { detectTapGestures { } }
                    .graphicsLayer {
                        alpha = progress
                        val s = 0.92f + 0.08f * progress
                        scaleX = s
                        scaleY = s
                    }
            ) {
                content()
            }
        }
    }
}
