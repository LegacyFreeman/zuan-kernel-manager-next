/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.ui.components

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.isSpecified
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/**
 * Status global "Versi Lite" (Lite vs Pro).
 *
 * Nilai asli ada di [com.zuan.kernelmanager.next.feature.settings.SettingsPreference.liteMode];
 * objek ini hanya cermin-nya berupa Compose snapshot state supaya bisa dibaca dari
 * modifier / helper non-Composable manapun tanpa perlu mengoper parameter, dan
 * membaca nilainya di dalam komposisi otomatis memicu recomposition saat toggle berubah.
 *
 * Lite = semua blur / glass / efek berat dimatikan, tampilan jadi simpel dan ringan.
 * Pilihan user di Wallpaper Style Screen TIDAK dihapus — hanya di-override selama Lite
 * aktif, jadi begitu kembali ke Pro semuanya normal lagi.
 */
object ZkmLite {
    var active: Boolean by mutableStateOf(false)
        internal set
}

/**
 * Pengganti `hazeSource` yang tidak merekam layer sama sekali di mode Lite
 * (tidak ada konsumen blur, jadi merekam hanya buang GPU/memori).
 */
fun Modifier.zkmHazeSource(
    state: HazeState,
    zIndex: Float = 0f,
    key: Any? = null,
): Modifier =
    if (ZkmLite.active) this else hazeSource(state = state, zIndex = zIndex, key = key)

/**
 * Pengganti `hazeEffect` di seluruh app. Mode Pro = persis perilaku lama.
 * Mode Lite = TANPA blur: diganti warna solid (warna dasar style, minimal 92% pekat)
 * supaya app bar / panel tetap terbaca tanpa efek kaca.
 */
fun Modifier.zkmHazeEffect(
    state: HazeState? = null,
    style: HazeStyle = HazeStyle.Unspecified,
    block: (HazeEffectScope.() -> Unit)? = null,
): Modifier {
    if (ZkmLite.active || ZkmQualityGovernor.isScrollFrozen) {
        // Mode Lite atau Scroll Cepat: hindari eksekusi shader blur Haze
        val base = style.backgroundColor
        val targetAlpha = if (ZkmLite.active) 1f else base.alpha.coerceAtLeast(0.4f)
        return if (base.isSpecified && base.alpha > 0f) {
            this.background(base.copy(alpha = targetAlpha))
        } else {
            this
        }
    }
    return hazeEffect(state = state, style = style, block = block)
}

/**
 * Warna permukaan solid netral untuk Versi Lite (kartu, panel, dll). Dipakai sebagai alas
 * supaya semua tint/alpha kaca berubah jadi warna penuh, bukan transparan.
 */
fun zkmLiteSolid(isDark: Boolean): androidx.compose.ui.graphics.Color =
    if (isDark) androidx.compose.ui.graphics.Color(0xFF1C1C21) else androidx.compose.ui.graphics.Color(0xFFF1F1F5)

/**
 * Pengganti `rememberInfiniteTransition().animateFloat(...)` untuk animasi dekoratif tanpa henti.
 * Mode Pro = animasi normal. Mode Lite = tidak ada transition sama sekali (tidak ada frame
 * loop yang terus membangunkan GPU/CPU): nilainya diam di [initialValue].
 */
@Composable
fun zkmInfiniteFloat(
    initialValue: Float,
    targetValue: Float,
    animationSpec: InfiniteRepeatableSpec<Float>,
    label: String = "zkmInfiniteFloat",
): State<Float> {
    if (ZkmLite.active) {
        return remember(initialValue) { mutableFloatStateOf(initialValue) }
    }
    return rememberInfiniteTransition(label = label).animateFloat(
        initialValue = initialValue,
        targetValue = targetValue,
        animationSpec = animationSpec,
        label = label,
    )
}

/**
 * `copy(alpha = a)` yang sadar Lite: di Versi Lite warna permukaan (kartu, panel, chip) dibuat
 * PEKAT 100% supaya tidak terlihat seperti blur gagal render. Alpha rendah (< 0.3, mis. untuk
 * gradien fade / scrim) dibiarkan apa adanya.
 */
fun androidx.compose.ui.graphics.Color.zkmLiteAlpha(a: Float): androidx.compose.ui.graphics.Color =
    copy(alpha = if (ZkmLite.active && a >= 0.3f) 1f else a)
