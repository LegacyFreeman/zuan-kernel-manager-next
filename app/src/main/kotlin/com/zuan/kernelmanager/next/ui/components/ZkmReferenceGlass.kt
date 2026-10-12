/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.ui.components

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle

/**
 * KARTU GLASS REFERENSI (mode Haze) = kartu SetEdit.
 *
 * Sebelumnya tiap layar menulis parameter haze-nya sendiri (blur 20/24, noise 0.05/0.1,
 * background putih 0.1 / primary 0.2 / transparan / cardColor 0.4-0.5, ada yang tanpa
 * border, ada yang tanpa isi Card, ada yang cuma Surface warna datar tanpa blur), sehingga
 * kartu glass di DoD Manager, App Compiler, App Manager dan MIUI Thermal tampil beda dari
 * SetEdit. Semua parameter itu sekarang HANYA ada di sini; layar cukup memanggil
 * [zkmReferenceGlass] / [zkmReferenceGlassSurface] dan memakai [zkmReferenceGlassFill]
 * sebagai containerColor Card/Surface.
 *
 * Resep (identik dengan SetEdit):
 *  - Haze: blur 24dp, noise 0.10, background scrim (putih/hitam sesuai mode APP) alpha 0.50,
 *    tanpa tint tambahan.
 *  - Isi kartu: scrim yang sama alpha 0.35 (Transparent di mode Android Liquid Glass).
 *  - Border: gradien putih 0.30 -> 0.05 (dilewati di mode liquid; liquid menggambar sendiri).
 * Mode Android Liquid Glass tetap ditangani [zkmCardHazeEffect] seperti sebelumnya.
 */
object ZkmReferenceGlass {
    val BlurRadius: Dp = 24.dp
    const val NoiseFactor: Float = 0.1f
    const val HazeBackgroundAlpha: Float = 0.5f
    const val FillAlpha: Float = 0.35f

    val BorderBrush: Brush = Brush.linearGradient(
        colors = listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.05f))
    )

    /** Warna dasar scrim mengikuti mode terang/gelap APP (bukan HP). */
    fun scrim(isDark: Boolean): Color = if (isDark) Color.Black else Color.White

    fun hazeStyle(isDark: Boolean): HazeStyle = ZkmHazeStyleCache.of(
        backgroundColor = scrim(isDark).copy(alpha = HazeBackgroundAlpha),
        blurRadius = BlurRadius,
        noiseFactor = NoiseFactor,
        tints = emptyList(),
    )
}

/** Isi Card/Surface/Row di atas kaca referensi. Transparent di mode liquid. */
@Composable
fun zkmReferenceGlassFill(): Color {
    val isDark = zkmIsDarkTheme()
    return zkmCardFill(ZkmReferenceGlass.scrim(isDark).copy(alpha = ZkmReferenceGlass.FillAlpha))
}

/**
 * Efek kaca referensi tanpa isi (untuk Card yang sudah punya containerColor =
 * [zkmReferenceGlassFill]): clip + haze/liquid + border gradien.
 */
@Composable
fun Modifier.zkmReferenceGlass(
    hazeState: HazeState,
    shape: Shape,
    borderWidth: Dp = 1.dp,
): Modifier {
    val isDark = zkmIsDarkTheme()
    val style = remember(isDark) { ZkmReferenceGlass.hazeStyle(isDark) }
    return this
        .zkmCardHazeEffect(shape = shape, state = hazeState, style = style)
        .zkmCardBorder(width = borderWidth, brush = ZkmReferenceGlass.BorderBrush, shape = shape)
}

/**
 * Kaca referensi lengkap (efek + isi) untuk elemen yang BUKAN Card/Surface
 * (Row, Box, dst.), sehingga hasilnya sama persis dengan Card SetEdit.
 */
@Composable
fun Modifier.zkmReferenceGlassSurface(
    hazeState: HazeState,
    shape: Shape,
    borderWidth: Dp = 1.dp,
): Modifier = this
    .zkmReferenceGlass(hazeState, shape, borderWidth)
    .background(zkmReferenceGlassFill(), shape)
