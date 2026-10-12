/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.ui.adaptive

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zuan.kernelmanager.next.ui.components.ConfigureDialogWindowBlur

/*
 * KENAPA FILE INI ADA
 *
 * Dialog / bottom sheet itu WINDOW terpisah. Sebelumnya tingginya dihitung dari
 * `Configuration.screenHeightDp` (0.9 x tinggi layar) dan window dialog dibiarkan memakai lebar
 * default platform. Itu benar di Rotation 16:9, tapi SALAH di PC mode / freeform AOSP:
 *   - taskbar (dan status bar) menutupi bagian bawah/atas dialog -> tombol "Mengerti" tertutup,
 *   - tinggi dialog dihitung dari layar penuh, bukan dari area yang benar-benar tersisa.
 *
 * Aturan sekarang (satu sumber kebenaran, dipakai SEMUA dialog & sheet):
 *   1. Window dialog dibuat edge-to-edge (MATCH_PARENT, decorFitsSystemWindows = false).
 *   2. Padding inset = gabungan inset window dialog + inset window utama (taskbar, status bar,
 *      cutout, tappable element). Diambil nilai terbesar per sisi, jadi aman walau window dialog
 *      tidak menerima inset taskbar.
 *   3. Tinggi/lebar maksimum panel diukur dari ruang yang BENAR-BENAR tersisa (BoxWithConstraints),
 *      bukan dari konfigurasi layar -> hasilnya identik di portrait, landscape, Rotation 16:9
 *      maupun PC mode / jendela freeform yang bisa di-resize.
 */

/** Inset window utama (system bars + cutout + tappable/taskbar). Di-provide sekali di root activity. */
val LocalZkmHostInsets = compositionLocalOf<WindowInsets> { WindowInsets(0, 0, 0, 0) }

/** Tinggi maksimum yang tersedia untuk panel dialog (setelah inset & margin). Unspecified di luar frame. */
val LocalZkmDialogMaxHeight = compositionLocalOf { Dp.Unspecified }

/** Lebar maksimum yang tersedia untuk panel dialog (setelah inset & margin). Unspecified di luar frame. */
val LocalZkmDialogMaxWidth = compositionLocalOf { Dp.Unspecified }

/** Hitung inset window utama; panggil di composition window Activity (bukan di dalam dialog). */
@Composable
fun rememberZkmHostInsets(): WindowInsets {
    val systemBars = WindowInsets.systemBars
    val cutout = WindowInsets.displayCutout
    val tappable = WindowInsets.tappableElement
    return remember(systemBars, cutout, tappable) {
        systemBars.union(cutout).union(tappable)
    }
}

/**
 * Inset yang dipakai dialog / sheet: inset window itu sendiri (termasuk IME) digabung dengan inset
 * window utama (taskbar PC mode). Diambil terbesar per sisi.
 */
@Composable
fun zkmDialogWindowInsets(): WindowInsets =
    WindowInsets.safeDrawing.union(LocalZkmHostInsets.current)

/**
 * Area aman untuk panel dialog: terpusat, terbebas dari taskbar / status bar / cutout / keyboard,
 * dan menyediakan [LocalZkmDialogMaxHeight] / [LocalZkmDialogMaxWidth] sesuai ruang yang tersisa.
 */
@Composable
fun ZkmDialogSafeArea(
    modifier: Modifier = Modifier,
    horizontalMargin: Dp = 24.dp,
    verticalMargin: Dp = 16.dp,
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(zkmDialogWindowInsets())
            .padding(horizontal = horizontalMargin, vertical = verticalMargin),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalZkmDialogMaxHeight provides maxHeight,
            LocalZkmDialogMaxWidth provides maxWidth,
        ) {
            content()
        }
    }
}

/** Tinggi maksimum panel: dari frame bila ada, kalau tidak dari tinggi jendela x [fallbackFraction]. */
@Composable
fun zkmDialogMaxHeight(fallbackFraction: Float = 0.9f): Dp {
    val frame = LocalZkmDialogMaxHeight.current
    return if (frame != Dp.Unspecified) frame
    else (LocalZkmWindow.current.heightDp * fallbackFraction).dp
}

/**
 * Pengganti `androidx.compose.ui.window.Dialog` untuk semua dialog ZKM.
 * Window edge-to-edge + [ZkmDialogSafeArea]; tap di luar panel menutup dialog sesuai [properties].
 */
@Composable
fun ZkmWindowDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    val latestDismiss by rememberUpdatedState(onDismissRequest)
    val dismissOutside = properties.dismissOnClickOutside
    val windowProps = remember(properties) {
        DialogProperties(
            dismissOnBackPress = properties.dismissOnBackPress,
            dismissOnClickOutside = properties.dismissOnClickOutside,
            securePolicy = properties.securePolicy,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        )
    }
    Dialog(onDismissRequest = onDismissRequest, properties = windowProps) {
        ConfigureDialogWindowBlur()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(dismissOutside) {
                    detectTapGestures { if (dismissOutside) latestDismiss() }
                }
        ) {
            ZkmDialogSafeArea {
                // Tap di dalam panel bukan "tap di luar".
                Box(modifier = Modifier.pointerInput(Unit) { detectTapGestures { } }) {
                    content()
                }
            }
        }
    }
}

/**
 * `contentWindowInsets` untuk semua ModalBottomSheet: system bars + cutout milik window sheet
 * digabung dengan inset window utama (taskbar PC mode). Tanpa IME - sheet sudah punya imePadding
 * sendiri. Pakai: `ModalBottomSheet(contentWindowInsets = { zkmSheetWindowInsets() }, ...)`.
 */
@Composable
fun zkmSheetWindowInsets(): WindowInsets =
    WindowInsets.systemBars
        .union(WindowInsets.displayCutout)
        .union(LocalZkmHostInsets.current)
