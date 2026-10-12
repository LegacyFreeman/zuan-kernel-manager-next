/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.ui.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zuan.kernelmanager.next.feature.settings.BgType
import com.zuan.kernelmanager.next.feature.settings.SettingsPreference
import com.zuan.kernelmanager.next.ui.components.zkmAppBarHazeStyle
import com.zuan.kernelmanager.next.ui.theme.zkmIconShape
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

/**
 * Info ukuran jendela saat ini. Dipakai semua layar supaya keputusan "landscape / lebar"
 * konsisten (satu sumber kebenaran), bukan tiap layar menghitung sendiri.
 */
@Immutable
data class ZkmWindowInfo(val widthDp: Int, val heightDp: Int) {
    val isLandscape: Boolean get() = widthDp > heightDp

    /** Landscape ATAU layar lebar (tablet/foldable) -> pakai NavigationRail, bukan bottom bar. */
    val isWide: Boolean get() = isLandscape && widthDp >= 560

    /** Tablet / layar sangat lebar: konten dibatasi lebarnya supaya kartu tidak melar. */
    val isExpanded: Boolean get() = widthDp >= 840

    /** Jumlah kolom kartu menu (Home) untuk lebar konten tertentu. */
    fun cardColumns(contentWidthDp: Int = widthDp): Int = when {
        contentWidthDp >= 1400 -> 6
        contentWidthDp >= 1100 -> 5
        contentWidthDp >= 860 -> 4
        contentWidthDp >= 560 -> 3
        else -> 2
    }
}

val LocalZkmWindow = compositionLocalOf { ZkmWindowInfo(widthDp = 360, heightDp = 800) }

/** true bila NavigationRail sedang tampil (gaya Classic, landscape). Capsule tetap di bawah -> false. */
val LocalZkmRailActive = compositionLocalOf { false }

@Composable
fun rememberZkmWindowInfo(): ZkmWindowInfo {
    val config = LocalConfiguration.current
    return remember(config.screenWidthDp, config.screenHeightDp) {
        ZkmWindowInfo(config.screenWidthDp, config.screenHeightDp)
    }
}

/** Lebar maksimum area konten di layar lebar (tablet). */
val ZkmMaxContentWidth: Dp = 840.dp

/** Lebar rail (di luar inset cutout/status di sisi kiri). */
val ZkmRailWidth: Dp = 88.dp

/**
 * Padding horizontal untuk daftar/konten utama.
 *
 * Sebelumnya konten dipusatkan & dibatasi 840dp. Di PC mode / jendela freeform yang sangat lebar
 * itu membuat kartu terpusat sementara header (Dashboard / Good Morning) tetap rata kiri,
 * sehingga tidak sejajar. Sekarang konten selalu selebar area yang tersedia (sejajar dengan
 * header); yang menyesuaikan lebar adalah JUMLAH KOLOM kartu, bukan padding.
 */
@Composable
fun zkmContentHorizontalPadding(base: Dp = 16.dp, reservedStart: Dp = 0.dp): Dp = base

/** Padding sisi kiri/kanan yang aman dari cutout kamera & bilah navigasi sistem saat landscape. */
@Composable
fun Modifier.zkmHorizontalSafeInsets(
    sides: WindowInsetsSides = WindowInsetsSides.Horizontal
): Modifier = this.windowInsetsPadding(WindowInsets.safeDrawing.only(sides))

/** Inset kiri (start) yang dipakai sistem; dipakai untuk menggeser konten di samping rail. */
@Composable
fun zkmStartInset(): Dp =
    WindowInsets.safeDrawing.asPaddingValues().calculateLeftPadding(LocalLayoutDirection.current)

@Immutable
data class ZkmRailItem(
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val label: String,
)

/**
 * NavigationRail untuk landscape. Mengganti bottom bar melayang yang sebelumnya menutupi konten.
 * Selalu menampilkan label (ruang vertikal landscape sempit, jadi item ringkas & bisa discroll).
 */
@Composable
fun ZkmNavigationRail(
    items: List<ZkmRailItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val context = LocalContext.current
    val prefs = remember { SettingsPreference.getInstance(context) }
    val bgType by prefs.bgType.collectAsState()
    val isCustomBg = bgType != BgType.SYSTEM

    val hazeStyle = zkmAppBarHazeStyle(
        tintedBackground = MaterialTheme.colorScheme.surface,
        isCustomBackground = isCustomBg
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .hazeEffect(state = hazeState, style = hazeStyle)
            // Inset kiri (cutout / bilah nav sistem), atas & bawah ditangani di sini
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Start + WindowInsetsSides.Vertical
                )
            )
    ) {
        NavigationRail(
            containerColor = Color.Transparent,
            modifier = Modifier.width(ZkmRailWidth),
            windowInsets = WindowInsets(0.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(
                    8.dp, Alignment.CenterVertically
                ),
            ) {
                items.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    NavigationRailItem(
                        selected = selected,
                        onClick = { if (!selected) onSelect(index) },
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(zkmIconShape(RoundedCornerShape(14.dp)))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.secondaryContainer
                                        else Color.Transparent
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label,
                                    tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        label = { Text(text = item.label, maxLines = 1) },
                        alwaysShowLabel = true,
                        colors = NavigationRailItemDefaults.colors(
                            indicatorColor = Color.Transparent
                        ),
                    )
                }
                footer?.invoke(this)
            }
        }
    }
}

/** PaddingValues kosong helper (hindari alokasi berulang di call-site). */
val ZkmNoPadding = PaddingValues(0.dp)

/**
 * Tinggi expanded LargeTopAppBar. Landscape punya tinggi layar pendek (~360dp), jadi header
 * 152dp bawaan memakan >40% layar. Di portrait nilainya sama dengan bawaan Material.
 */
@Composable
fun zkmLargeAppBarExpandedHeight(): Dp =
    if (LocalZkmWindow.current.isLandscape) 120.dp else 152.dp
