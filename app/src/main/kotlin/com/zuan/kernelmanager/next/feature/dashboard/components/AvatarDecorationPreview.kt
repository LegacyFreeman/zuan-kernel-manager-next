/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Media profil (foto / GIF / video / Live Photo) yang sedang dipakai di layar Overall,
 * dibaca dari [ProfileMediaStore] — sumber yang sama dengan OverallScreen — supaya
 * preview Avatar Decoration di Wallpaper Style SELALU sinkron dengan profil asli.
 * Dimuat ulang setiap layar kembali RESUME (mis. habis ganti foto profil).
 */
@Composable
fun rememberProfileMediaSnapshot(): ProfileMedia? {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var media by remember { mutableStateOf(ProfileMediaStore.load(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) media = ProfileMediaStore.load(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return media
}

/**
 * Isi avatar untuk preview bingkai.
 *  - [media] ada  -> tampilkan foto/GIF/video profil asli (lengkap dengan crop zoom/geser).
 *  - [media] null -> ikon Person placeholder.
 *  - [live] = true memutar video sungguhan (pakai untuk 1 preview besar);
 *    false menampilkan frame pertama video sebagai gambar diam (pakai untuk grid).
 */
@Composable
fun AvatarPreviewContent(
    media: ProfileMedia?,
    live: Boolean,
    placeholderBg: Color,
    placeholderTint: Color,
    iconSize: Dp = 28.dp
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(placeholderBg),
        contentAlignment = Alignment.Center
    ) {
        if (media != null) {
            ProfileAvatar(
                media = media,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                liveVideo = live
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = placeholderTint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
