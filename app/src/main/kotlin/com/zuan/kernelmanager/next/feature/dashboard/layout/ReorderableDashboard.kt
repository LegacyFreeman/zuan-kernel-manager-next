/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.dashboard.layout

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import com.zuan.kernelmanager.next.ui.adaptive.LocalZkmRailActive
import com.zuan.kernelmanager.next.ui.adaptive.LocalZkmWindow
import com.zuan.kernelmanager.next.ui.adaptive.ZkmRailWidth
import com.zuan.kernelmanager.next.ui.adaptive.zkmContentHorizontalPadding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.zuan.kernelmanager.next.feature.dashboard.components.ExpressiveEntrance
import com.zuan.kernelmanager.next.feature.dashboard.components.ExpressiveMotion
import dev.chrisbanes.haze.HazeState
import com.zuan.kernelmanager.next.ui.components.zkmHazeSource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun DashboardContent(
    widgets: List<DashboardWidgetType>,
    lazyListState: LazyListState,
    hazeState: HazeState,
    topPadding: Dp,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    headerContent: (@Composable () -> Unit)? = null,
    widgetContent: @Composable (DashboardWidgetType) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val fixedItemCount = 1 + if (headerContent != null) 1 else 0

    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onMove(from.index - fixedItemCount, to.index - fixedItemCount)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    val railActive = LocalZkmRailActive.current
    val sidePadding = zkmContentHorizontalPadding(
        reservedStart = if (railActive) ZkmRailWidth else 0.dp
    )

    LazyColumn(
        state = lazyListState,
        modifier = modifier
            .fillMaxSize()
            .zkmHazeSource(state = hazeState, zIndex = 1f),
        contentPadding = PaddingValues(
            start = sidePadding,
            end = sidePadding,
            bottom = if (railActive) 72.dp else 100.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "header_spacer") {
            Spacer(modifier = Modifier.height(topPadding))
        }

        if (headerContent != null) {
            item(key = "update_card") {
                headerContent()
            }
        }

        itemsIndexed(
            items = widgets,
            key = { _, widget -> widget.name }
        ) { index, widget ->
            ReorderableItem(
                state = reorderableState,
                key = widget.name
            ) { isDragging ->

                val floatScale by animateFloatAsState(
                    targetValue = if (isDragging) 1.04f else 1f,
                    animationSpec = ExpressiveMotion.bouncy(),
                    label = "dragScale"
                )
                val floatElevation by animateDpAsState(
                    targetValue = if (isDragging) 20.dp else 0.dp,
                    animationSpec = ExpressiveMotion.snappy(),
                    label = "dragElevation"
                )

                ExpressiveEntrance(index = index) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDragging) 2f else 0f)
                            .graphicsLayer {
                                scaleX = floatScale
                                scaleY = floatScale
                                shadowElevation = floatElevation.toPx()
                                shape = RoundedCornerShape(30.dp)
                                clip = false
                            }
                            .longPressDraggableHandle(
                                onDragStarted = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragStopped = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDragEnd()
                                }
                            )
                    ) {
                        widgetContent(widget)
                    }
                }
            }
        }
    }
}
