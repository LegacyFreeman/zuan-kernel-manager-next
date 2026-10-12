/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.ui.components

import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

/**
 * Cache global untuk [HazeStyle].
 *
 * Sebelumnya hampir semua layar membuat `HazeStyle(...)` inline di dalam
 * `hazeEffect(...)` — termasuk di dalam item LazyColumn. Akibatnya setiap
 * recomposition saat scroll mengalokasikan style baru DAN karena instance
 * style berubah terus, node hazeEffect menganggap konfigurasinya berubah,
 * memaksa re-setup pipeline blur (penyebab utama stutter saat scroll).
 *
 * Dengan cache ini, style yang sama dipakai ulang di seluruh aplikasi:
 * nol alokasi di hot path dan modifier hazeEffect tetap setara (equals),
 * sehingga Compose/haze tidak perlu mengonfigurasi ulang apa pun.
 */
object ZkmHazeStyleCache {

    private data class Key(
        val blurDp: Int,
        val noiseBits: Int,
        val backgroundBits: Long,
        val tintBits: Long,
    )

    private val cache = LruCache<Key, HazeStyle>(32)

    private val generalCache = LruCache<List<Any?>, HazeStyle>(64)

    /**
     * Pengganti drop-in untuk konstruktor `HazeStyle(...)` yang di-cache.
     *
     * Mendukung kedua varian konstruktor Haze 1.x: `tints: List<HazeTint>`
     * dan `tint: HazeTint?` tunggal. Semua argumen bernama, sama seperti
     * konstruktor aslinya, jadi seluruh call site bisa dialihkan tanpa ubah
     * perilaku — bedanya instance yang sama selalu dikembalikan untuk
     * kombinasi parameter yang sama.
     */
    fun of(
        backgroundColor: Color = Color.Unspecified,
        tints: List<HazeTint> = emptyList(),
        tint: HazeTint? = null,
        blurRadius: Dp = 20.dp,
        noiseFactor: Float = 0.15f,
    ): HazeStyle {
        val effectiveTints = tint?.let { listOf(it) } ?: tints
        val key = listOf<Any?>(backgroundColor, effectiveTints, blurRadius, noiseFactor)
        generalCache.get(key)?.let { return it }
        val style = HazeStyle(
            backgroundColor = backgroundColor,
            tints = effectiveTints,
            blurRadius = blurRadius,
            noiseFactor = noiseFactor,
        )
        generalCache.put(key, style)
        return style
    }

    /**
     * Style glass kartu standar (background transparan, tint digambar terpisah
     * di atas haze — lihat [zkmGlassCard]).
     */
    fun cardStyle(
        blurRadius: Dp,
        noiseFactor: Float = 0.08f,
    ): HazeStyle {
        val key = Key(
            blurDp = blurRadius.value.toInt(),
            noiseBits = noiseFactor.toBits(),
            backgroundBits = 0L,
            tintBits = 0L,
        )
        cache.get(key)?.let { return it }
        val style = HazeStyle(
            backgroundColor = androidx.compose.ui.graphics.Color.Transparent,
            blurRadius = blurRadius,
            noiseFactor = noiseFactor,
            tints = emptyList(),
        )
        cache.put(key, style)
        return style
    }
}

/** Radius blur default yang selalu dipakai kartu glass — satu sumber kebenaran. */
val ZkmGlassCardBlurRadius: Dp = 20.dp
