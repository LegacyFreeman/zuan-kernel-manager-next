/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import kotlinx.coroutines.delay

/**
 * Splash versi Lite: satu layar polos (tanpa animasi 3D / kanvas / blur) yang tampil
 * sebentar lalu langsung masuk. Pengganti [Zuan3DCustomSplashScreen] saat mode Lite aktif.
 */
@Composable
fun ZuanLiteSplashScreen(onSplashFinished: () -> Unit) {
    val isDark = zkmIsDarkTheme()
    val bg = if (isDark) Color(0xFF0B0F14) else Color(0xFFF5F8FA)
    val textPrimary = if (isDark) Color(0xFFECEFF1) else Color(0xFF1E262C)
    val textSecondary = if (isDark) Color(0xFF90A4AE) else Color(0xFF607D8B)
    val finish by rememberUpdatedState(onSplashFinished)

    LaunchedEffect(Unit) {
        delay(600L)
        finish()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(bg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Zuan Manager",
                color = textPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Lite",
                color = textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp
            )
        }
    }
}
