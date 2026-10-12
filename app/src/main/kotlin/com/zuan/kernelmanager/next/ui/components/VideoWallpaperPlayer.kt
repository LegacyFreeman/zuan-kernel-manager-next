/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.ui.components

import android.net.Uri
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

@OptIn(UnstableApi::class) 
@Composable
fun VideoWallpaperPlayer(
    uri: Uri,
    modifier: Modifier = Modifier,
    volume: Float = 0f
) {
    // Mode Lite (Zkm Perf Tahap 4): Jangan hidupkan decoder video / ExoPlayer sama sekali
    // Menghilangkan 100% beban decoding video, RAM buffer, dan GL context saat Lite aktif.
    if (ZkmLite.active) return

    val context = LocalContext.current

    // 1. Setup ExoPlayer
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE // Loop
            
            // [FIX] Gunakan 'this.volume' untuk merujuk ke property player
            // 'volume' merujuk ke parameter fungsi (val)
            this.volume = volume 
            
            // [PENTING] Scaling Mode untuk TextureView agar Full Screen (Crop)
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            playWhenReady = true
        }
    }

    // 1b. Batasi beban decode (Zkm perf tahap 1): maksimal 720p/30fps bila file punya track
    // alternatif. Wallpaper hanya latar, jadi tidak perlu 4K/60fps (juga di-blur Haze tiap frame).
    LaunchedEffect(exoPlayer) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setMaxVideoSize(1280, 720)
            .setMaxVideoFrameRate(30)
            .build()
    }

    // 1c. Ikuti lifecycle: video dijeda otomatis saat app tidak terlihat (layar mati,
    // pindah app, recents) dan lanjut lagi saat kembali. Tanpa ini decoder + blur tetap
    // jalan di background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> exoPlayer.pause()
                Lifecycle.Event.ON_START -> exoPlayer.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // Bila composable masuk saat lifecycle belum STARTED, jangan langsung memutar.
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            exoPlayer.pause()
        }
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 2. Load Media
    LaunchedEffect(uri) {
        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
    }

    // 3. Cleanup
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // 4. Render menggunakan TextureView (Bukan PlayerView)
    // TextureView wajib digunakan agar library Haze/Blur bisa "melihat" videonya.
    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                // Attach Player ke TextureView ini
                exoPlayer.setVideoTextureView(this)

                // True hardware downscale (Zkm Perf Tahap 2 & 3):
                // Jika governor mendeteksi beban atau tier balanced/low, set defaultBufferSize
                // lebih rendah agar memori GPU dan blur cost turun hingga 60-75%.
                post {
                    val scale = ZkmQualityGovernor.videoDownscaleScale()
                    if (scale < 1.0f && width > 0 && height > 0) {
                        surfaceTexture?.setDefaultBufferSize(
                            (width * scale).toInt().coerceAtLeast(360),
                            (height * scale).toInt().coerceAtLeast(640)
                        )
                    }
                }
            }
        },
        modifier = modifier
    )
}
