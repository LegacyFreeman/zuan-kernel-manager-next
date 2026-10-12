/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.fps

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeStyle
import com.zuan.kernelmanager.next.ui.components.zkmGlassCard
import com.zuan.kernelmanager.next.ui.components.zkmGlassTint
import com.zuan.kernelmanager.next.ui.components.LocalZkmHazeState
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundContentColor

@Composable
fun ModernActionCard(
    title: String, 
    icon: ImageVector, 
    modifier: Modifier = Modifier,
    isGlassActive: Boolean = false,
    hazeState: HazeState? = null
) {
    // [FIX] Glass: container dulu Transparent + fallbackTint Transparent -> card kosong
    // bila blur gagal, dan teks jatuh ke hitam. Sekarang tint solid di atas blur.
    val shape = RoundedCornerShape(12.dp)
    val isDark = zkmIsDarkTheme()
    val cardModifier = if (isGlassActive) {
        modifier.height(100.dp).zkmGlassCard(
            isGlassActive = true,
            hazeState = hazeState ?: LocalZkmHazeState.current,
            shape = shape,
            tint = zkmGlassTint(MaterialTheme.colorScheme.secondaryContainer, isDark, 0.45f),
            blurRadius = 20.dp
        )
    } else {
        modifier.height(100.dp)
    }
    val textColor = if (isGlassActive) zkmOnCustomBackgroundContentColor(isDark) else MaterialTheme.colorScheme.onSecondaryContainer

    Card(
        modifier = cardModifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isGlassActive) Color.Transparent else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = textColor
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(icon, contentDescription = null, tint = textColor)
            Text(title, style = MaterialTheme.typography.labelLarge, color = textColor)
        }
    }
}
