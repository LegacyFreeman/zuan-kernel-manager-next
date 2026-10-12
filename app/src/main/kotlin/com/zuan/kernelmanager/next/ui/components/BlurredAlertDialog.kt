/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zuan.kernelmanager.next.ui.components

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zuan.kernelmanager.next.ui.components.zkmLiteAlpha
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.zuan.kernelmanager.next.ui.adaptive.LocalZkmDialogMaxHeight
import com.zuan.kernelmanager.next.ui.adaptive.LocalZkmWindow
import com.zuan.kernelmanager.next.ui.adaptive.ZkmWindowDialog
import com.zuan.kernelmanager.next.ui.adaptive.zkmDialogMaxHeight
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/** Single source of truth for the ZKM glass dialog look (same as the "Select Governor" dialog). */
object ZkmDialogDefaults {
    const val BlurRadius = 64
    const val DimAmount = 0.18f
    const val MaxContainerAlpha = 0.88f
    /** Dialog solid penuh di Versi Lite (tanpa blur di belakangnya, jadi tidak boleh tembus pandang). */
    val containerAlpha: Float get() = if (ZkmLite.active) 1f else MaxContainerAlpha
    val Shape = RoundedCornerShape(28.dp)
    val ContentPadding = 24.dp
}

/** Walks up the view tree to the window that hosts [view] when it is a Compose dialog window. */
private fun findDialogWindow(view: View): Window? {
    var cur: View? = view
    while (cur != null) {
        if (cur is DialogWindowProvider) return cur.window
        cur = cur.parent as? View
    }
    return null
}

private fun isCrossWindowBlurAvailable(view: View): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    return runCatching {
        val wm = view.context.getSystemService(WindowManager::class.java)
        wm?.isCrossWindowBlurEnabled ?: true
    }.getOrDefault(true)
}

/**
 * Applies backdrop blur (+ optional dim) to whatever window hosts [view]. Works for Compose
 * dialog windows and, as a fallback, for windows that are added straight to the WindowManager
 * (Material3 ModalBottomSheet), where there is no DialogWindowProvider to find.
 */
@android.annotation.SuppressLint("NewApi")
private fun applyWindowBlur(view: View, blurRadius: Int, dimAmount: Float?) {
    // Versi Lite: tidak ada blur jendela dialog/sheet, cukup dim biasa (lihat ZkmLite).
    val blurOk = isCrossWindowBlurAvailable(view) && !ZkmLite.active
    val dialogWindow = findDialogWindow(view)
    if (dialogWindow != null) {
        dialogWindow.setBackgroundDrawableResource(android.R.color.transparent)
        val attrs = dialogWindow.attributes
        if (dimAmount != null) {
            dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            // When the system has cross-window blur turned off (battery saver etc.) use a stronger dim.
            attrs.dimAmount = if (blurOk) dimAmount else maxOf(dimAmount, 0.45f)
        }
        if (blurOk) {
            attrs.flags = attrs.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            attrs.blurBehindRadius = blurRadius
        }
        dialogWindow.attributes = attrs
        return
    }
    if (!blurOk) return
    val root = view.rootView ?: return
    // Never blur the host activity's own window; only separate windows (sheets, popups).
    if (root === (view.context as? android.app.Activity)?.window?.decorView) return
    val lp = root.layoutParams as? WindowManager.LayoutParams ?: return
    lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
    lp.blurBehindRadius = blurRadius
    runCatching {
        root.context.getSystemService(WindowManager::class.java).updateViewLayout(root, lp)
    }
}

@Composable
private fun RememberWindowBlur(blurRadius: Int, dimAmount: Float?) {
    val view = LocalView.current
    DisposableEffect(view, blurRadius, dimAmount) {
        var listener: View.OnAttachStateChangeListener? = null
        if (view.isAttachedToWindow) {
            applyWindowBlur(view, blurRadius, dimAmount)
        } else {
            listener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) { applyWindowBlur(v, blurRadius, dimAmount) }
                override fun onViewDetachedFromWindow(v: View) {}
            }
            view.addOnAttachStateChangeListener(listener)
        }
        onDispose { listener?.let { view.removeOnAttachStateChangeListener(it) } }
    }
    // Compose may re-create window attributes on recomposition; re-assert them cheaply.
    SideEffect { if (view.isAttachedToWindow) applyWindowBlur(view, blurRadius, dimAmount) }
}

@Composable
fun ConfigureDialogWindowBlur(
    blurRadius: Int = ZkmDialogDefaults.BlurRadius,
    dimAmount: Float = ZkmDialogDefaults.DimAmount
) {
    RememberWindowBlur(blurRadius, dimAmount)
}

/**
 * Glass/iOS-style AlertDialog used throughout ZKM Next.
 *
 * The dialog window gets Android 12+ backdrop blur when supported. On older
 * Android versions the transparent window falls back to the platform dim.
 */
@Composable
fun BlurredAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    // shape/containerColor are kept only so old call sites still compile. Every ZKM dialog now
    // uses the same glass look on purpose (see ZkmDialogDefaults), so they are ignored.
    @Suppress("UNUSED_PARAMETER") shape: androidx.compose.ui.graphics.Shape = ZkmDialogDefaults.Shape,
    @Suppress("UNUSED_PARAMETER") containerColor: Color = Color.Unspecified,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: androidx.compose.ui.unit.Dp = 0.dp,
    properties: DialogProperties = DialogProperties(),
    // false untuk dialog yang isinya sudah punya scroll sendiri (LazyColumn / verticalScroll),
    // supaya tidak ada scroll bersarang (crash "infinite max height").
    scrollableText: Boolean = true
) {
    // Dialog style dari Wallpaper Style Screen > Style > Dialog.
    val useOverlay = rememberUseLiquidDialogOverlay()
    val panel: @Composable (Boolean) -> Unit = { liquid ->
        // Tinggi panel = ruang yang BENAR-BENAR tersisa di dalam frame dialog (sudah dikurangi
        // taskbar PC mode / status bar / cutout / keyboard), bukan 0.9 x tinggi layar.
        // Dipanggil di dalam frame, jadi LocalZkmDialogMaxHeight sudah terisi.
        val maxPanelHeight = zkmDialogMaxHeight()
        val panelModifier = if (liquid) {
            Modifier.zkmLiquidGlassCard(
                backdrop = LocalZkmLiquidBackdrop.current!!,
                spec = ZkmLiquidDialogSpec,
                tint = MaterialTheme.colorScheme.surfaceContainerHigh.zkmLiteAlpha(0.50f),
            )
        } else {
            Modifier.clip(ZkmDialogDefaults.Shape)
        }

        Surface(
            modifier = modifier.widthIn(min = 280.dp, max = 560.dp).heightIn(max = maxPanelHeight).then(panelModifier),
            shape = ZkmDialogDefaults.Shape,
            color = if (liquid) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh.zkmLiteAlpha(ZkmDialogDefaults.containerAlpha),
            contentColor = textContentColor,
            tonalElevation = tonalElevation
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (icon != null) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.material3.LocalContentColor provides iconContentColor
                    ) { icon() }
                }
                if (title != null) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.material3.LocalContentColor provides titleContentColor
                    ) { title() }
                }
                if (text != null) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.material3.LocalContentColor provides textContentColor
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .then(if (scrollableText) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                        ) { text() }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (dismissButton != null) dismissButton()
                    confirmButton()
                }
            }
        }
    }

    if (useOverlay) {
        // Liquid glass: digambar di overlay root (satu window dengan layer backdrop).
        ZkmLiquidOverlayDialog(onDismissRequest = onDismissRequest, properties = properties) {
            panel(true)
        }
    } else {
        // Jalur Haze: window Dialog edge-to-edge + blur window + area aman (taskbar PC mode, cutout).
        ZkmWindowDialog(
            onDismissRequest = onDismissRequest,
            properties = properties
        ) {
            panel(false)
        }
    }
}

/**
 * true bila dialog harus memakai overlay Android Liquid Glass: style dipilih,
 * Android 13+, host overlay tersedia, dan pemanggil berada di window utama
 * (bukan di dalam dialog / bottom sheet lain).
 */
@Composable
internal fun rememberUseLiquidDialogOverlay(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { com.zuan.kernelmanager.next.feature.settings.SettingsPreference.getInstance(context) }
    val dialogStyle by prefs.dialogStyle.collectAsStateWithLifecycle()
    if (dialogStyle != com.zuan.kernelmanager.next.feature.settings.DialogStyle.LIQUID_GLASS) return false
    if (!isLiquidGlassSupported()) return false
    if (LocalZkmDialogHost.current == null) return false
    return isInHostWindow()
}


/**
 * Dialog window with the shared ZKM glass treatment (backdrop blur + dim on Android 12+).
 * Pair it with [ZkmDialogPanel] for custom dialog content that looks identical to
 * [BlurredAlertDialog].
 */
@Composable
fun ZkmDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    if (rememberUseLiquidDialogOverlay()) {
        ZkmLiquidOverlayDialog(onDismissRequest = onDismissRequest, properties = properties, content = content)
    } else {
        ZkmWindowDialog(onDismissRequest = onDismissRequest, properties = properties) {
            content()
        }
    }
}

/** The rounded glass card used inside every ZKM dialog. */
@Composable
fun ZkmDialogPanel(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shape: androidx.compose.ui.graphics.Shape = ZkmDialogDefaults.Shape,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable ColumnScope.() -> Unit
) {
    // Liquid glass hanya bila panel ini berada di overlay dialog liquid (satu window dengan backdrop).
    val liquidBackdrop = LocalZkmLiquidBackdrop.current
    val useLiquidGlass = LocalZkmInLiquidDialog.current && liquidBackdrop != null && isLiquidGlassSupported()

    val panelModifier = if (useLiquidGlass) {
        Modifier.zkmLiquidGlassCard(
            backdrop = liquidBackdrop!!,
            spec = ZkmLiquidDialogSpec,
            tint = containerColor.copy(alpha = 0.50f),
        )
    } else {
        Modifier.clip(shape)
    }

    // Batasi tinggi panel ke ruang yang tersisa (taskbar PC mode, landscape pendek, Rotation 16:9).
    val frameMaxHeight = LocalZkmDialogMaxHeight.current
    val heightModifier = if (frameMaxHeight != androidx.compose.ui.unit.Dp.Unspecified) Modifier.heightIn(max = frameMaxHeight) else Modifier

    Surface(
        modifier = modifier.fillMaxWidth().widthIn(max = 560.dp).then(heightModifier).then(panelModifier),
        shape = shape,
        color = if (useLiquidGlass) Color.Transparent else containerColor.copy(alpha = minOf(containerColor.alpha, ZkmDialogDefaults.containerAlpha)),
        contentColor = contentColor,
        tonalElevation = 0.dp
    ) {
        Column(content = content)
    }
}


/**
 * Backdrop blur for bottom sheets so they match dialogs. The sheet already draws its own
 * scrim, so only the window blur is added here (no second dim layer). Android 12+ only.
 */
@Composable
fun ConfigureSheetWindowBlur(blurRadius: Int = ZkmDialogDefaults.BlurRadius) {
    RememberWindowBlur(blurRadius, dimAmount = null)
}
