/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme

/**
 * WARNA CARD STANDAR menu Home (acuan: kartu SetEdit / Kernel Flasher Horizon).
 *
 *  - Light mode : putih polos.
 *  - Dark mode  : primary * 0.15 di atas #141414 (gelap ber-tint tema).
 *
 * Sebelumnya tiap layar memilih sendiri: ada yang putih, ada yang memakai
 * surfaceContainer(Low/High) Material (ber-tint warna tema di light mode, dan beda
 * lagi di dark mode), sehingga antar menu tidak seragam. Rumus ini identik dengan
 * generateHomeBackgroundColors(...).second, tapi bisa dipanggil dari mana saja.
 */
fun zkmStandardCardColor(isDark: Boolean, primary: Color): Color =
    if (isDark) {
        primary.copy(
            red = primary.red * 0.15f,
            green = primary.green * 0.15f,
            blue = primary.blue * 0.15f,
            alpha = 1f
        ).compositeOver(Color(0xFF141414))
    } else {
        Color(0xFFFFFFFF)
    }

@Composable
fun zkmStandardCardColor(): Color {
    val isDark = zkmIsDarkTheme()
    val primary = MaterialTheme.colorScheme.primary
    return remember(isDark, primary) { zkmStandardCardColor(isDark, primary) }
}

/**
 * Membungkus layar menu Home supaya semua card yang memakai token card Material
 * (surfaceContainerLow / surfaceContainer) otomatis memakai [zkmStandardCardColor].
 *
 * Token surfaceContainerHigh/Highest SENGAJA tidak diubah: dipakai layar sebagai warna
 * elemen di DALAM card (chip, field, track), jadi harus tetap berbeda dari warna card.
 * Branch glass / custom background tiap layar tidak memakai token ini, jadi tidak terpengaruh.
 */
@Composable
fun ZkmStandardCardScheme(
    cardColor: Color = zkmStandardCardColor(),
    content: @Composable () -> Unit
) {
    val card = cardColor
    val base = MaterialTheme.colorScheme
    val scheme = remember(base, card) {
        base.copy(surfaceContainerLow = card, surfaceContainer = card)
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content
    )
}
