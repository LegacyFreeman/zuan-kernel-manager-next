/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package com.zuan.kernelmanager.next.feature.dashboard.layout

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.feature.dashboard.components.OverallIconContainer
import com.zuan.kernelmanager.next.ui.components.ConfigureSheetWindowBlur
import com.zuan.kernelmanager.next.ui.components.ZkmDialogDefaults
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * =====================================================================================
 * ADD WIDGET — katalog widget tambahan + drag & drop ke dashboard
 *
 * Alur:
 * 1. FAB → "Add Widget" membuka sheet ini (daftar widget yang BELUM ada di dashboard).
 * 2. Tekan-tahan sebuah kartu widget lalu geser: sheet memudar, dashboard terlihat penuh,
 *    kartu "hantu" mengikuti jari dan garis penanda menunjukkan posisi sisipnya.
 * 3. Lepas jari → widget dipasang di posisi itu (lihat [DashboardLayoutViewModel.addWidget]).
 * Alternatif tanpa drag: tombol "+" di kartu menambahkan widget ke posisi paling bawah.
 *
 * Sheet ini memakai [ModalBottomSheet] (window tersendiri) — sama dengan Widget Gallery —
 * sehingga tampil DI ATAS navigation bar, gesture swipe-to-dismiss standar, dan list-nya
 * bisa di-scroll. Karena sheet hidup di window lain, semua koordinat drag memakai
 * koordinat LAYAR ([screenPosition]) supaya konsisten antara sheet dan overlay dashboard.
 * =====================================================================================
 */

/**
 * Posisi layar (px) dari koordinat ini. Dipakai bersama oleh sheet (window sheet) dan
 * dashboard (window activity) karena `positionInRoot` berbeda antar-window.
 */
fun LayoutCoordinates.screenPosition(view: View): Offset {
    val loc = IntArray(2)
    view.rootView.getLocationOnScreen(loc)
    return positionInWindow() + Offset(loc[0].toFloat(), loc[1].toFloat())
}

/** State drag lintas-komponen (sheet ↔ overlay ↔ OverallScreen). Koordinat = root komposisi. */
@Stable
class WidgetDragState {
    var dragging by mutableStateOf<DashboardWidgetType?>(null)
        private set
    var pointer by mutableStateOf(Offset.Zero)
        private set

    fun begin(widget: DashboardWidgetType, startInRoot: Offset) {
        pointer = startInRoot
        dragging = widget
    }

    fun move(delta: Offset) {
        pointer += delta
    }

    /** Akhiri drag; mengembalikan widget + posisi lepas, atau null bila tidak sedang drag. */
    fun finish(): Pair<DashboardWidgetType, Offset>? {
        val widget = dragging ?: return null
        val at = pointer
        dragging = null
        return widget to at
    }

    fun cancel() {
        dragging = null
    }
}

/**
 * Posisi sisip hasil drag.
 * @param index  Index pada daftar widget yang TERLIHAT; widget baru disisipkan sebelum item ini
 *               (index == jumlah widget terlihat → paling bawah).
 * @param lineY  Posisi Y garis penanda, relatif terhadap awal list (px).
 */
data class WidgetDropTarget(val index: Int, val lineY: Float)

/**
 * Hitung posisi sisip dari posisi jari ([pointerYInList], relatif awal list).
 * Jari di paruh atas sebuah kartu → sisip sebelum kartu itu; di paruh bawah → sesudahnya.
 */
fun computeWidgetDropTarget(
    layoutInfo: LazyListLayoutInfo,
    widgets: List<DashboardWidgetType>,
    pointerYInList: Float,
    spacingPx: Float
): WidgetDropTarget {
    val items = layoutInfo.visibleItemsInfo.mapNotNull { info ->
        val idx = widgets.indexOfFirst { it.name == info.key }
        if (idx >= 0) idx to info else null
    }
    if (items.isEmpty()) {
        return WidgetDropTarget(widgets.size, layoutInfo.viewportStartOffset.toFloat())
    }
    val hit = items.firstOrNull { (_, info) -> pointerYInList < info.offset + info.size / 2f }
    return if (hit != null) {
        WidgetDropTarget(hit.first, hit.second.offset - spacingPx / 2f)
    } else {
        val (lastIdx, lastInfo) = items.last()
        WidgetDropTarget(lastIdx + 1, lastInfo.offset + lastInfo.size + spacingPx / 2f)
    }
}

@Composable
fun AddWidgetSheet(
    visible: Boolean,
    candidates: List<DashboardWidgetType>,
    dragState: WidgetDragState,
    onDrop: (DashboardWidgetType, Offset) -> Unit,
    onTapAdd: (DashboardWidgetType) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme

    // Jangan sampai state drag tertinggal bila sheet dilepas dari komposisi di tengah drag.
    DisposableEffect(Unit) { onDispose { dragState.cancel() } }

    // Saat drag: sheet + scrim + blur window disembunyikan supaya dashboard terlihat penuh,
    // tapi window-nya tetap hidup agar gesture yang sedang berjalan tidak terputus.
    val isDragging = dragState.dragging != null
    val sheetAlpha by animateFloatAsState(
        targetValue = if (isDragging) 0f else 1f,
        animationSpec = tween(140),
        label = "addWidgetSheetAlpha"
    )
    val scrimColor by animateColorAsState(
        targetValue = if (isDragging) Color.Transparent else BottomSheetDefaults.ScrimColor,
        animationSpec = tween(140),
        label = "addWidgetScrim"
    )

    // Tutup dengan animasi turun dulu, baru jalankan aksinya.
    val closeThen: (() -> Unit) -> Unit = { action ->
        scope.launch {
            sheetState.hide()
            action()
        }
    }

    ModalBottomSheet(
        contentWindowInsets = { com.zuan.kernelmanager.next.ui.adaptive.zkmSheetWindowInsets() },
        
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.graphicsLayer { alpha = sheetAlpha },
        sheetGesturesEnabled = !isDragging,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = colors.surfaceContainerHigh,
        scrimColor = scrimColor
    ) {
        AddWidgetWindowBlur(dragging = isDragging)
        SheetPanelContent(
            candidates = candidates,
            dragState = dragState,
            // Lepas di dashboard: sheet langsung hilang (jangan animasi turun).
            onDrop = onDrop,
            onTapAdd = { widget -> closeThen { onTapAdd(widget) } }
        )
    }
}

/** Blur backdrop window sheet; dimatikan selama drag supaya dashboard tidak ikut ngeblur. */
@Composable
private fun AddWidgetWindowBlur(dragging: Boolean) {
    ConfigureSheetWindowBlur()
    val view = LocalView.current
    SideEffect {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !view.isAttachedToWindow) return@SideEffect
        val root = view.rootView ?: return@SideEffect
        if (root === (view.context as? Activity)?.window?.decorView) return@SideEffect
        val lp = root.layoutParams as? WindowManager.LayoutParams ?: return@SideEffect
        val target = if (dragging) 0 else ZkmDialogDefaults.BlurRadius
        if (lp.blurBehindRadius != target) {
            lp.blurBehindRadius = target
            runCatching {
                root.context.getSystemService(WindowManager::class.java).updateViewLayout(root, lp)
            }
        }
    }
}

@Composable
private fun SheetPanelContent(
    candidates: List<DashboardWidgetType>,
    dragState: WidgetDragState,
    onDrop: (DashboardWidgetType, Offset) -> Unit,
    onTapAdd: (DashboardWidgetType) -> Unit
) {
    val colors = MaterialTheme.colorScheme

    var query by remember { mutableStateOf("") }
    val titles = candidates.associateWith { stringResource(it.localizedTitleRes()) }
    val filtered = candidates.filter { widget ->
        query.isBlank() ||
            titles[widget].orEmpty().contains(query, ignoreCase = true) ||
            widget.description.contains(query, ignoreCase = true)
    }

    // Header + search tetap diam; hanya daftar widget yang scroll (weight + fill=false →
    // menyusut sesuai isi, tapi dibatasi tinggi maksimum sheet sehingga bisa di-scroll).
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.lp_add_widget_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 4.dp)
        )
        Text(
            text = stringResource(R.string.lp_add_widget_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 28.dp, vertical = 6.dp),
            textAlign = TextAlign.Center
        )

        TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.lp_add_widget_search)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedContainerColor = colors.surfaceContainerLowest.copy(alpha = 0.7f),
                unfocusedContainerColor = colors.surfaceContainerLowest.copy(alpha = 0.7f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (filtered.isEmpty()) {
                Text(
                    text = stringResource(
                        if (candidates.isEmpty()) R.string.lp_add_widget_empty else R.string.lp_add_widget_no_results
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                filtered.forEach { widget ->
                    AddWidgetCard(
                        widget = widget,
                        title = titles[widget].orEmpty(),
                        dragState = dragState,
                        onDrop = onDrop,
                        onTapAdd = onTapAdd
                    )
                }
            }
        }
    }
}

@Composable
private fun AddWidgetCard(
    widget: DashboardWidgetType,
    title: String,
    dragState: WidgetDragState,
    onDrop: (DashboardWidgetType, Offset) -> Unit,
    onTapAdd: (DashboardWidgetType) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    var origin by remember { mutableStateOf(Offset.Zero) }

    val descriptionRes = widget.localizedDescriptionRes()
    val description = if (descriptionRes != 0) stringResource(descriptionRes) else widget.description
    val addDescription = stringResource(R.string.lp_add_widget_add_cd, title)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainerLowest.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { origin = it.screenPosition(view) }
            .pointerInput(widget) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        dragState.begin(widget, origin + offset)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragState.move(dragAmount)
                    },
                    onDragEnd = {
                        dragState.finish()?.let { (dropped, at) ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDrop(dropped, at)
                        }
                    },
                    onDragCancel = { dragState.cancel() }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OverallIconContainer(
                icon = widget.icon,
                containerColor = colors.primary.copy(alpha = 0.14f),
                iconColor = colors.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTapAdd(widget)
                },
                modifier = Modifier.semantics { contentDescription = addDescription }
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.primary)
            }
        }
    }
}

/**
 * Lapisan di atas dashboard saat drag: kartu "hantu" mengikuti jari + garis penanda posisi sisip.
 * Letakkan di Box root layar (ukuran penuh) dengan zIndex di atas konten.
 *
 * @param dropLineRootY Y garis penanda dalam koordinat root, atau null bila tidak ada.
 */
@Composable
fun WidgetDragOverlay(
    dragState: WidgetDragState,
    dropLineRootY: Float?,
    modifier: Modifier = Modifier
) {
    val widget = dragState.dragging
    val view = LocalView.current
    var origin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.screenPosition(view) }
    ) {
        if (widget != null) {
            val colors = MaterialTheme.colorScheme

            if (dropLineRootY != null) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, (dropLineRootY - origin.y).roundToInt() - 2.dp.roundToPx()) }
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(colors.primary)
                )
            }

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (dragState.pointer.x - origin.x).roundToInt() - 96.dp.roundToPx(),
                            (dragState.pointer.y - origin.y).roundToInt() - 84.dp.roundToPx()
                        )
                    }
                    .width(192.dp)
                    .shadow(16.dp, RoundedCornerShape(22.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(widget.icon, contentDescription = null, tint = colors.primary)
                    Text(
                        text = stringResource(widget.localizedTitleRes()),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
