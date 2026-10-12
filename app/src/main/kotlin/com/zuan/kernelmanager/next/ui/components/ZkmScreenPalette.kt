/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * PALET LAYAR + KARTU TUNGGAL untuk semua layar menu Home.
 *
 * Sebelumnya setiap layar punya salinan sendiri (generateSetEditBackgroundColors,
 * generateFlasherBackgroundColors, ...) dan sebagian layar (Screen Time, Battery Usage,
 * Charge Stats, Kernel Flasher mode Capntrips, dst.) malah memakai warna Material
 * (surfaceContainer) sehingga kartunya beda-beda: ada yang putih di light mode, ada yang
 * berwarna theme. Referensi yang dianggap benar = kartu SetEdit:
 *
 *  - Light : latar hampir putih (tint 3% primary), kartu PUTIH.
 *  - Dark  : latar gelap tint 8% primary, kartu gelap tint 15% primary.
 *
 * Semua generator lama sekarang hanya meneruskan ke fungsi ini, jadi rumus warna cuma ada
 * di satu tempat. Mengubah rumus di sini otomatis mengubah semua layar.
 *
 * @return Pair(warna latar layar, warna isi kartu)
 */
object ZkmScreenPalette {
    private val LightCard = Color(0xFFFFFFFF)

    fun colors(isDark: Boolean, primary: Color): Pair<Color, Color> {
        return if (isDark) {
            val bg = primary.copy(
                red = primary.red * 0.08f,
                green = primary.green * 0.08f,
                blue = primary.blue * 0.08f,
                alpha = 1f,
            ).compositeOver(Color(0xFF0A0A0A))
            val card = primary.copy(
                red = primary.red * 0.15f,
                green = primary.green * 0.15f,
                blue = primary.blue * 0.15f,
                alpha = 1f,
            ).compositeOver(Color(0xFF141414))
            Pair(bg, card)
        } else {
            val bg = Color(
                red = 0.97f + (primary.red * 0.03f),
                green = 0.97f + (primary.green * 0.03f),
                blue = 0.97f + (primary.blue * 0.03f),
                alpha = 1f,
            )
            Pair(bg, LightCard)
        }
    }

    /**
     * Menyeragamkan token permukaan-kartu Material (surfaceContainer*) dengan palet kartu ZKM.
     *
     * Banyak layar Home (AppManager, Process Manager, MIUI Thermal, Thermal, Display, Doze,
     * KSU WebUI, ...) menggambar kartu non-glass dengan `MaterialTheme.colorScheme.surfaceContainer*`
     * sehingga kartunya berwarna theme (dynamic color / primary) dan dark mode-nya beda dengan
     * SetEdit. Dengan mengganti token-token itu di sumbernya, SEMUA kartu yang memakainya ikut
     * putih (light) / gelap bertint tipis (dark) persis seperti kartu SetEdit, tanpa mengubah
     * puluhan call-site.
     *
     * Hirarki tetap terjaga: Lowest/Low/Container = warna kartu, High & Highest sedikit lebih
     * kontras untuk elemen yang bersarang di dalam kartu (kotak nilai, chip, field).
     */
    fun applyTo(scheme: androidx.compose.material3.ColorScheme, isDark: Boolean): androidx.compose.material3.ColorScheme {
        val (_, card) = colors(isDark, scheme.primary)
        val high = if (isDark) androidx.compose.ui.graphics.lerp(card, Color.White, 0.06f) else Color(0xFFF3F3F5)
        val highest = if (isDark) androidx.compose.ui.graphics.lerp(card, Color.White, 0.11f) else Color(0xFFEAEAEE)
        return scheme.copy(
            surfaceContainerLowest = card,
            surfaceContainerLow = card,
            surfaceContainer = card,
            surfaceContainerHigh = high,
            surfaceContainerHighest = highest,
        )
    }

    /** Warna teks utama/sekunder di atas kartu non-glass (mengikuti mode APP). */
    fun text(isDark: Boolean): Color = if (isDark) Color(0xFFEEEEEE) else Color(0xFF111111)
    fun subText(isDark: Boolean): Color = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666666)
}
