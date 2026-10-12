/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.ui.components

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [BETA] Jenis media untuk "Appbar Banner Progressive".
 * Disimpan sebagai [key] di SettingsPreference.
 */
enum class AppBarBannerType(val key: String) {
    IMAGE("image"),
    GIF("gif"),
    VIDEO("video");

    companion object {
        fun fromKey(key: String?): AppBarBannerType =
            entries.firstOrNull { it.key == key } ?: IMAGE

        /**
         * Deteksi jenis media dari MIME type (utama) lalu ekstensi (fallback).
         * GIF dan WebP dirutekan ke jalur animasi (ImageDecoder) supaya
         * kalau file-nya beranimasi, tetap bergerak.
         */
        fun detect(context: Context, uri: Uri): AppBarBannerType {
            val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
            val path = uri.toString().lowercase()
            return when {
                mime.startsWith("video") -> VIDEO
                mime == "image/gif" || mime == "image/webp" -> GIF
                path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".webm") ||
                    path.endsWith(".3gp") -> VIDEO
                path.endsWith(".gif") || path.endsWith(".webp") -> GIF
                else -> IMAGE
            }
        }
    }
}

/**
 * Banner yang menimpa seluruh area app bar. Pasang sebagai anak pertama Box
 * header dengan `Modifier.matchParentSize()` supaya mengikuti tinggi app bar
 * (ikut menyusut saat app bar collapse).
 *
 * "Progressive": bagian bawah banner memudar bertahap (alpha mask), sehingga
 * transisi ke konten di bawahnya halus, tanpa potongan keras.
 *
 * @param dim     scrim hitam di atas banner agar teks app bar tetap terbaca.
 * @param fadeStart posisi (0..1) dari atas di mana banner mulai memudar.
 */
@Composable
fun ZkmAppBarBanner(
    uri: Uri,
    type: AppBarBannerType,
    modifier: Modifier = Modifier,
    dim: Float = 0.25f,
    fadeStart: Float = 0.55f,
) {
    val context = LocalContext.current
    Box(modifier = modifier.progressiveBottomFade(fadeStart)) {
        when (type) {
            AppBarBannerType.VIDEO -> VideoWallpaperPlayer(
                uri = uri,
                modifier = Modifier.fillMaxSize(),
            )
            AppBarBannerType.GIF -> AnimatedImageBanner(
                uri = uri,
                modifier = Modifier.fillMaxSize(),
            )
            AppBarBannerType.IMAGE -> AsyncImage(
                model = ImageRequest.Builder(context).data(uri).build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        if (dim > 0f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim.coerceIn(0f, 0.8f))))
        }
    }
}

/** GIF / animated WebP lewat ImageDecoder (minSdk 29, tanpa dependency tambahan). */
@Composable
private fun AnimatedImageBanner(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val drawable by produceState<Drawable?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                    // Batasi lebar decode supaya GIF besar tidak menghabiskan memori.
                    val maxWidth = 1080
                    if (info.size.width > maxWidth) {
                        val ratio = maxWidth.toFloat() / info.size.width
                        decoder.setTargetSize(
                            maxWidth,
                            (info.size.height * ratio).toInt().coerceAtLeast(1),
                        )
                    }
                }
            }.getOrNull()
        }
    }

    val current = drawable ?: return

    DisposableEffect(current) {
        (current as? AnimatedImageDrawable)?.apply {
            repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
            start()
        }
        onDispose { (current as? AnimatedImageDrawable)?.stop() }
    }

    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        },
        update = { it.setImageDrawable(current) },
        modifier = modifier,
    )
}

/**
 * Mask alpha vertikal: penuh dari atas sampai [solidUntil], lalu memudar
 * halus (kurva ease) sampai transparan di tepi bawah.
 * Offscreen layer wajib agar BlendMode.DstIn hanya memengaruhi banner,
 * bukan konten lain di belakangnya.
 */
private fun Modifier.progressiveBottomFade(solidUntil: Float): Modifier {
    val s = solidUntil.coerceIn(0.1f, 0.95f)
    val span = 1f - s
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Black,
                        s to Color.Black,
                        s + span * 0.25f to Color.Black.copy(alpha = 0.85f),
                        s + span * 0.50f to Color.Black.copy(alpha = 0.55f),
                        s + span * 0.75f to Color.Black.copy(alpha = 0.22f),
                        1f to Color.Transparent,
                    ),
                ),
                blendMode = BlendMode.DstIn,
            )
        }
}
