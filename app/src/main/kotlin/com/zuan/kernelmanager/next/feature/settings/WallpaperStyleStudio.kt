/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalHazeMaterialsApi::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.zuan.kernelmanager.next.feature.settings

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.rounded.EnergySavingsLeaf
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import com.zuan.kernelmanager.next.ui.components.ZkmLite
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Panorama
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.feature.dashboard.components.AvatarPreviewContent
import com.zuan.kernelmanager.next.feature.dashboard.components.AvatarWithDecoration
import com.zuan.kernelmanager.next.feature.dashboard.components.rememberProfileMediaSnapshot
import com.zuan.kernelmanager.next.ui.components.AppBarBannerType
import com.zuan.kernelmanager.next.ui.components.ZkmAppBarBanner
import com.zuan.kernelmanager.next.ui.components.ZkmSlider
import com.zuan.kernelmanager.next.ui.components.ZkmSwitch
import com.zuan.kernelmanager.next.ui.theme.ThemeMode
import com.zuan.kernelmanager.next.ui.theme.ZkmIconShapeStyle
import com.zuan.kernelmanager.next.ui.theme.resolve
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import com.zuan.kernelmanager.next.ui.components.zkmHazeEffect
import com.zuan.kernelmanager.next.ui.components.zkmHazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi

/**
 * Halaman detail yang dibuka di dalam panel (menggantikan bottom sheet lama
 * supaya preview Overall tetap terlihat dan berubah realtime).
 *
 * Tiap tab capsule punya hub kartu sendiri; kartu yang diketuk membuka salah satu
 * detail di bawah ini MENIMPA hub tab itu. Detail disimpan per-tab, jadi pindah tab
 * lalu kembali tidak menghilangkan halaman yang sedang dibuka.
 */
enum class StyleDetail {
    // Tab Wallpaper
    WALL_SOURCE, WALL_ADJUST,

    // Tab Style
    GLASS, COLORS, FONTS, ICON_SHAPES, AVATAR, TOGGLE, SLIDER, DIALOG,

    // Tab Addition
    BANNER
}

/** Ruang yang disisakan di bawah halaman detail supaya capsule melayang tidak menutup konten. */
private val StudioDockReserve: Dp = 92.dp

// =============================================================================
// CAPSULE NAV MELAYANG
// =============================================================================

@Composable
fun FloatingCapsuleNav(
    activeTab: Int,
    onTabSelected: (Int) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val labels = listOf(
        stringResource(R.string.wall_wallpaper),
        stringResource(R.string.wall_style),
        stringResource(R.string.wall_addition)
    )
    val unselectedIcons = listOf(Icons.Outlined.Wallpaper, Icons.Outlined.Palette, Icons.Outlined.Bolt)
    val selectedIcons = listOf(Icons.Filled.Wallpaper, Icons.Filled.Palette, Icons.Filled.Bolt)

    val hazeStyle = remember(scheme.surfaceContainerHigh) {
        HazeStyle(
            backgroundColor = scheme.surfaceContainerHigh.copy(alpha = 0.60f),
            blurRadius = 26.dp,
            noiseFactor = 0.04f,
            tints = listOf(HazeTint(scheme.surfaceContainerHighest.copy(alpha = 0.22f)))
        )
    }

    val capsuleBorder = remember(scheme.outline, scheme.outlineVariant) {
        Brush.verticalGradient(
            listOf(
                scheme.outline.copy(alpha = 0.38f),
                scheme.outlineVariant.copy(alpha = 0.18f)
            )
        )
    }

    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 14.dp)
            .shadow(
                elevation = 20.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.22f),
                spotColor = Color.Black.copy(alpha = 0.32f)
            )
            .clip(CircleShape)
            .zkmHazeEffect(state = hazeState, style = hazeStyle)
            .border(1.dp, capsuleBorder, CircleShape)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEachIndexed { i, label ->
            val selected = i == activeTab
            val bg by animateColorAsState(
                if (selected) scheme.primary else Color.Transparent,
                tween(260),
                label = "NavPillBg"
            )
            val fg by animateColorAsState(
                if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
                tween(260),
                label = "NavPillFg"
            )
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(bg)
                    .clickable { onTabSelected(i) }
                    .padding(horizontal = if (selected) 20.dp else 18.dp, vertical = 13.dp)
                    .animateContentSize(tween(260)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (selected) selectedIcons[i] else unselectedIcons[i],
                    contentDescription = label,
                    tint = fg,
                    modifier = Modifier.size(22.dp)
                )
                AnimatedVisibility(visible = selected) {
                    Row {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = fg,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dock capsule di tengah bawah panel (portrait maupun landscape / PC mode).
 * Harus dipanggil di luar panel (bukan di dalam hazeSource) supaya blur-nya mengambil konten panel.
 */
@Composable
fun BoxScope.StudioDock(
    activeTab: Int,
    onTabSelected: (Int) -> Unit,
    hazeState: HazeState
) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        FloatingCapsuleNav(
            activeTab = activeTab,
            onTabSelected = onTabSelected,
            hazeState = hazeState
        )
    }
}

// =============================================================================
// PANEL KONTROL (di bawah preview / di kanan preview saat landscape)
// =============================================================================

@Composable
fun StudioControlPanel(
    activeTab: Int,
    detail: StyleDetail?,
    onDetailChange: (StyleDetail?) -> Unit,
    viewModel: WallpaperStyleViewModel,
    panelHaze: HazeState,
    onOpenPhotos: () -> Unit,
    onOpenEffects: () -> Unit,
    onShowCustomColorPicker: (ColorType) -> Unit,
    onImportCustomFont: () -> Unit,
    themeToggleCenter: (Offset) -> Unit,
    onToggleDarkTheme: () -> Unit,
    themeTransitionRunning: Boolean,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
    shape: Shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
) {
    val liteLockedAction = rememberLiteLockedAction()
    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = if (wide) 0.dp else 10.dp
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .zkmHazeSource(state = panelHaze)
        ) {
            // Layar lebar: hub memakai SELURUH lebar panel kanan dengan kolom kartu yang bertambah.
            val columns = if (wide) ((maxWidth - 48.dp) / 220.dp).toInt().coerceIn(2, 5) else 2
            CompositionLocalProvider(LocalHubColumns provides columns) {
              Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (wide) Modifier.windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End)
                        ) else Modifier
                    )
              ) {
                // Hub tiap tab; kartu yang diketuk MENIMPA hub tab itu dengan halaman detailnya.
                AnimatedContent(
                    targetState = activeTab to detail,
                    transitionSpec = {
                        val (fromTab, fromDetail) = initialState
                        val (toTab, toDetail) = targetState
                        when {
                            fromTab != toTab ->
                                fadeIn(tween(240)) togetherWith fadeOut(tween(140))

                            fromDetail == null && toDetail != null ->
                                (slideInHorizontally(tween(300)) { it / 5 } + fadeIn(tween(260))) togetherWith
                                    (slideOutHorizontally(tween(300)) { -it / 8 } + fadeOut(tween(160)))

                            else ->
                                (slideInHorizontally(tween(300)) { -it / 5 } + fadeIn(tween(260))) togetherWith
                                    (slideOutHorizontally(tween(300)) { it / 8 } + fadeOut(tween(160)))
                        }
                    },
                    label = "StudioPanelContent"
                ) { (tab, d) ->
                    LiteStudioFrame(locked = d != null && d in LiteLockedDetails) {
                        if (d != null) {
                            DetailPage(
                                detail = d,
                                viewModel = viewModel,
                                wide = wide,
                                onBack = { onDetailChange(null) },
                                onShowCustomColorPicker = onShowCustomColorPicker,
                                onImportCustomFont = onImportCustomFont
                            )
                        } else {
                            TabHub(
                                tab = tab,
                                selected = null,
                                viewModel = viewModel,
                                onOpenDetail = { onDetailChange(it) },
                                onOpenPhotos = onOpenPhotos,
                                // Efek wallpaper (cuaca, dll) adalah fitur berat: tidak didukung di Lite.
                                onOpenEffects = if (ZkmLite.active) liteLockedAction else onOpenEffects,
                                themeToggleCenter = themeToggleCenter,
                                onToggleDarkTheme = onToggleDarkTheme,
                                themeTransitionRunning = themeTransitionRunning
                            )
                        }
                    }
                }
              }
            }
        }
    }
}

// =============================================================================
// VERSI LITE: banner + kunci fitur berat
// =============================================================================

/** Halaman detail yang berisi fitur berat (blur / glass / animasi) dan dikunci di Versi Lite. */
private val LiteLockedDetails = setOf(
    StyleDetail.GLASS,
    StyleDetail.AVATAR,
    StyleDetail.DIALOG,
    StyleDetail.BANNER
)

/** Tinggi header halaman detail (tombol kembali) yang TIDAK boleh tertutup lapisan kunci. */
private val LiteLockHeaderHeight = 76.dp

@Composable
private fun rememberLiteLockedAction(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        {
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.lite_locked_toast),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}

/**
 * Bingkai panel Studio saat Versi Lite aktif: menampilkan banner "Mode Lite aktif" di atas
 * dan, untuk halaman fitur berat ([locked]), meredupkan isinya + memblokir sentuhan
 * (header dengan tombol kembali tetap bisa dipakai). Di mode Pro bingkai ini tidak
 * melakukan apa-apa.
 */
@Composable
private fun LiteStudioFrame(locked: Boolean, content: @Composable () -> Unit) {
    if (!ZkmLite.active) {
        content()
        return
    }
    val context = LocalContext.current
    val onLockedTap = rememberLiteLockedAction()
    Column(Modifier.fillMaxSize()) {
        LiteBanner(
            onSwitchToPro = { SettingsPreference.getInstance(context).setLiteMode(false) }
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.fillMaxSize().then(if (locked) Modifier.alpha(0.38f) else Modifier)) {
                content()
            }
            if (locked) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(top = LiteLockHeaderHeight)
                        .pointerInput(Unit) { detectTapGestures(onTap = { onLockedTap() }) }
                )
            }
        }
    }
}

@Composable
private fun LiteBanner(onSwitchToPro: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = scheme.tertiaryContainer,
        contentColor = scheme.onTertiaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.EnergySavingsLeaf,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.lite_banner_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.lite_banner_desc),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onSwitchToPro) {
                Text(stringResource(R.string.lite_banner_switch_pro))
            }
        }
    }
}

@Composable
private fun TabHub(
    tab: Int,
    selected: StyleDetail?,
    viewModel: WallpaperStyleViewModel,
    onOpenDetail: (StyleDetail) -> Unit,
    onOpenPhotos: () -> Unit,
    onOpenEffects: () -> Unit,
    themeToggleCenter: (Offset) -> Unit,
    onToggleDarkTheme: () -> Unit,
    themeTransitionRunning: Boolean
) {
    when (tab) {
        0 -> WallpaperHub(
            viewModel = viewModel,
            selected = selected,
            onOpenDetail = onOpenDetail,
            onOpenPhotos = onOpenPhotos,
            onOpenEffects = onOpenEffects
        )

        1 -> StyleHub(
            viewModel = viewModel,
            selected = selected,
            onOpenDetail = onOpenDetail,
            themeToggleCenter = themeToggleCenter,
            onToggleDarkTheme = onToggleDarkTheme,
            themeTransitionRunning = themeTransitionRunning
        )

        else -> AdditionHub(
            viewModel = viewModel,
            selected = selected,
            onOpenDetail = onOpenDetail
        )
    }
}

@Composable
private fun DetailPage(
    detail: StyleDetail,
    viewModel: WallpaperStyleViewModel,
    wide: Boolean,
    onBack: () -> Unit,
    onShowCustomColorPicker: (ColorType) -> Unit,
    onImportCustomFont: () -> Unit
) {
    val title = when (detail) {
        StyleDetail.WALL_SOURCE -> stringResource(R.string.wall_background_source)
        StyleDetail.WALL_ADJUST -> stringResource(R.string.bg_adjustments)
        StyleDetail.GLASS -> stringResource(R.string.wall_card_glass_effect)
        StyleDetail.COLORS -> stringResource(R.string.wall_colors)
        StyleDetail.FONTS -> stringResource(R.string.wall_fonts)
        StyleDetail.ICON_SHAPES -> stringResource(R.string.wall_icon_shapes)
        StyleDetail.AVATAR -> stringResource(R.string.wall_avatar_decoration)
        StyleDetail.TOGGLE -> stringResource(R.string.wall_toggle_style)
        StyleDetail.SLIDER -> stringResource(R.string.wall_slider_style)
        StyleDetail.DIALOG -> stringResource(R.string.wall_dialog_style)
        StyleDetail.BANNER -> stringResource(R.string.wall_appbar_banner)
    }
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
        Modifier
            .fillMaxHeight()
            .widthIn(max = if (wide) 900.dp else Dp.Unspecified)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(onClick = onBack) {
                Icon(
                    Icons.Rounded.ArrowBackIosNew,
                    contentDescription = stringResource(R.string.wall_studio_back),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (detail) {
                StyleDetail.WALL_SOURCE -> WallpaperSourceDetail(viewModel)
                StyleDetail.WALL_ADJUST -> WallpaperAdjustDetail(viewModel)
                StyleDetail.GLASS -> CardGlassDetail(viewModel)
                StyleDetail.COLORS -> ColorsSelectionSheetContent(viewModel, onShowCustomColorPicker)
                StyleDetail.FONTS -> FontSelectionSheetContent(viewModel, onImportCustomFont, onBack)
                StyleDetail.ICON_SHAPES -> IconShapeSelectionSheetContent(viewModel, onBack)
                StyleDetail.AVATAR -> AvatarDecorationSheetContent(viewModel)
                StyleDetail.TOGGLE -> ToggleStyleSheetContent(viewModel)
                StyleDetail.SLIDER -> SliderStyleSheetContent(viewModel)
                StyleDetail.DIALOG -> DialogStyleSheetContent(viewModel)
                StyleDetail.BANNER -> AppBarBannerDetail(viewModel)
            }
        }
    }
    }
}

// =============================================================================
// HUB: WALLPAPER
// =============================================================================

@Composable
private fun WallpaperHub(
    viewModel: WallpaperStyleViewModel,
    selected: StyleDetail?,
    onOpenDetail: (StyleDetail) -> Unit,
    onOpenPhotos: () -> Unit,
    onOpenEffects: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val bgType by viewModel.bgType.collectAsStateWithLifecycle()
    val isGallery = bgType == BgType.GALLERY

    StudioPage(
        title = stringResource(R.string.wall_wallpaper),
        subtitle = stringResource(R.string.wall_studio_wallpaper_sub)
    ) {
        HubGrid {
            HubCard(
                title = stringResource(R.string.wall_photos),
                subtitle = stringResource(R.string.wall_hub_photos_sub),
                onClick = onOpenPhotos,
                trailing = Icons.Rounded.Add,
                modifier = Modifier
            ) {
                IconBubble(Icons.Rounded.AddPhotoAlternate, size = 60.dp)
            }
            HubCard(
                title = stringResource(R.string.wall_effects),
                subtitle = stringResource(R.string.wall_hub_effects_sub),
                onClick = onOpenEffects,
                modifier = Modifier
            ) {
                IconBubble(
                    Icons.Rounded.AutoAwesome,
                    size = 60.dp,
                    container = scheme.tertiaryContainer,
                    tint = scheme.onTertiaryContainer
                )
            }
            HubCard(
                title = stringResource(R.string.wall_background_source),
                subtitle = stringResource(
                    if (isGallery) R.string.wall_custom_media else R.string.wall_system_default
                ),
                onClick = { onOpenDetail(StyleDetail.WALL_SOURCE) },
                selected = selected == StyleDetail.WALL_SOURCE,
                modifier = Modifier
            ) {
                IconBubble(
                    if (isGallery) Icons.Rounded.Image else Icons.Outlined.Android,
                    size = 60.dp,
                    container = scheme.secondaryContainer,
                    tint = scheme.onSecondaryContainer
                )
            }
            HubCard(
                title = stringResource(R.string.bg_adjustments),
                subtitle = stringResource(
                    if (isGallery) R.string.wall_hub_adjust_sub else R.string.wall_studio_needs_custom_media
                ),
                onClick = { onOpenDetail(StyleDetail.WALL_ADJUST) },
                selected = selected == StyleDetail.WALL_ADJUST,
                modifier = Modifier
            ) {
                IconBubble(Icons.Rounded.Tune, size = 60.dp)
            }
        }
    }
}

// =============================================================================
// HUB: STYLE
// =============================================================================

@Composable
private fun StyleHub(
    viewModel: WallpaperStyleViewModel,
    selected: StyleDetail?,
    onOpenDetail: (StyleDetail) -> Unit,
    themeToggleCenter: (Offset) -> Unit,
    onToggleDarkTheme: () -> Unit,
    themeTransitionRunning: Boolean
) {
    val scheme = MaterialTheme.colorScheme
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val isCardBlur by viewModel.isCardBlur.collectAsStateWithLifecycle()
    val cardDarkness by viewModel.cardDarkness.collectAsStateWithLifecycle()
    val cardGlassStyle by viewModel.cardGlassStyle.collectAsStateWithLifecycle()
    val appFontChoice by viewModel.appFontChoice.collectAsStateWithLifecycle()
    val iconShapeStyle by viewModel.iconShapeStyle.collectAsStateWithLifecycle()
    val avatarDecoration by viewModel.avatarDecoration.collectAsStateWithLifecycle()
    val toggleStyle by viewModel.toggleStyle.collectAsStateWithLifecycle()
    val sliderStyle by viewModel.sliderStyle.collectAsStateWithLifecycle()
    val dialogStyle by viewModel.dialogStyle.collectAsStateWithLifecycle()
    val tileMedia = rememberProfileMediaSnapshot()

    StudioPage(
        title = stringResource(R.string.wall_personalize),
        subtitle = stringResource(R.string.wall_studio_style_sub)
    ) {
        // ---------------- THEME ----------------
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StudioSectionLabel(stringResource(R.string.wall_studio_section_theme))
            HubGrid {
                HubCard(
                    title = stringResource(R.string.wall_colors),
                    subtitle = stringResource(R.string.wall_wallpaper_colors_themes),
                    onClick = { onOpenDetail(StyleDetail.COLORS) },
                    selected = selected == StyleDetail.COLORS,
                    modifier = Modifier
                ) {
                    Box(Modifier.size(width = 84.dp, height = 62.dp)) {
                        Box(
                            Modifier
                                .align(Alignment.TopStart)
                                .size(42.dp)
                                .background(scheme.primary.copy(alpha = 0.85f), CircleShape)
                        )
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(42.dp)
                                .background(scheme.secondary.copy(alpha = 0.85f), CircleShape)
                        )
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .size(42.dp)
                                .background(scheme.tertiary.copy(alpha = 0.85f), CircleShape)
                        )
                    }
                }
                // Kartu Dark Theme: posisi switch dilaporkan untuk animasi lubang hitam.
                HubCard(
                    title = stringResource(R.string.wall_dark_theme),
                    subtitle = stringResource(R.string.wall_dark_theme_desc),
                    onClick = { if (!themeTransitionRunning) onToggleDarkTheme() },
                    trailing = null,
                    modifier = Modifier
                ) {
                    Box(
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            val position = coordinates.positionInRoot()
                            themeToggleCenter(
                                Offset(
                                    position.x + coordinates.size.width / 2f,
                                    position.y + coordinates.size.height / 2f
                                )
                            )
                        }
                    ) {
                        ZkmSwitch(
                            checked = themeMode == ThemeMode.DARK,
                            enabled = !themeTransitionRunning,
                            onCheckedChange = { onToggleDarkTheme() }
                        )
                    }
                }
            }
        }

        // ---------------- GLASS ----------------
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StudioSectionLabel(stringResource(R.string.wall_studio_section_glass))
            HubGrid {
                HubCard(
                    title = stringResource(R.string.wall_card_glass_effect),
                    subtitle = when {
                        !isCardBlur -> stringResource(R.string.wall_card_opacity, (cardDarkness * 100).toInt())
                        cardGlassStyle == CardGlassStyle.LIQUID_GLASS -> stringResource(R.string.wall_card_style_liquid)
                        else -> stringResource(R.string.wall_card_style_frosted)
                    },
                    onClick = { onOpenDetail(StyleDetail.GLASS) },
                    selected = selected == StyleDetail.GLASS,
                    visualHeight = 96.dp,
                    modifier = Modifier
                ) {
                    // Miniatur kaca: gradien "wallpaper" + dua kartu yang ikut berubah sesuai mode.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary))),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            repeat(2) {
                                val cardShape = RoundedCornerShape(14.dp)
                                Box(
                                    Modifier
                                        .size(width = 64.dp, height = 44.dp)
                                        .clip(cardShape)
                                        .background(
                                            if (isCardBlur) Color.White.copy(alpha = 0.28f)
                                            else scheme.surfaceContainer
                                        )
                                        .border(
                                            1.dp,
                                            Color.White.copy(alpha = if (isCardBlur) 0.45f else 0f),
                                            cardShape
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---------------- COMPONENTS ----------------
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StudioSectionLabel(stringResource(R.string.wall_studio_section_components))

            HubGrid {
                HubCard(
                    title = stringResource(R.string.wall_fonts),
                    subtitle = when (appFontChoice) {
                        AppFontChoice.APP_DEFAULT -> stringResource(R.string.wall_app_default)
                        AppFontChoice.DEVICE_SYSTEM -> stringResource(R.string.wall_device_system_font)
                        AppFontChoice.SERIF -> stringResource(R.string.wall_serif)
                        AppFontChoice.MONOSPACE -> stringResource(R.string.wall_monospace)
                        AppFontChoice.CUSTOM -> stringResource(R.string.wall_custom_font)
                    },
                    onClick = { onOpenDetail(StyleDetail.FONTS) },
                    selected = selected == StyleDetail.FONTS,
                    modifier = Modifier
                ) {
                    Text(
                        stringResource(R.string.wall_aa_preview),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Medium,
                        color = scheme.primary
                    )
                }
                HubCard(
                    title = stringResource(R.string.wall_icon_shapes),
                    subtitle = if (iconShapeStyle == ZkmIconShapeStyle.DEFAULT) stringResource(R.string.wall_app_default)
                    else iconShapeStyle.label,
                    onClick = { onOpenDetail(StyleDetail.ICON_SHAPES) },
                    selected = selected == StyleDetail.ICON_SHAPES,
                    modifier = Modifier
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(iconShapeStyle.resolve(RoundedCornerShape(16.dp)))
                            .background(scheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Category,
                            contentDescription = null,
                            tint = scheme.onPrimaryContainer,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                HubCard(
                    title = stringResource(R.string.wall_avatar_decoration),
                    subtitle = if (avatarDecoration == AvatarDecorationStyle.NONE)
                        stringResource(R.string.wall_avatar_decoration_desc)
                    else avatarDecoration.label,
                    onClick = { onOpenDetail(StyleDetail.AVATAR) },
                    selected = selected == StyleDetail.AVATAR,
                    modifier = Modifier
                ) {
                    AvatarWithDecoration(
                        style = avatarDecoration,
                        modifier = Modifier.size(58.dp)
                    ) {
                        AvatarPreviewContent(
                            media = tileMedia,
                            live = false,
                            placeholderBg = scheme.primaryContainer,
                            placeholderTint = scheme.onPrimaryContainer,
                            iconSize = 20.dp
                        )
                    }
                }
                HubCard(
                    title = stringResource(R.string.wall_dialog_style),
                    subtitle = when (dialogStyle) {
                        DialogStyle.HAZE_BLUR -> stringResource(R.string.wall_dialog_haze_blur)
                        DialogStyle.LIQUID_GLASS -> stringResource(R.string.wall_dialog_liquid_glass)
                    },
                    onClick = { onOpenDetail(StyleDetail.DIALOG) },
                    selected = selected == StyleDetail.DIALOG,
                    modifier = Modifier
                ) {
                    IconBubble(Icons.Rounded.Forum, size = 56.dp)
                }
                HubCard(
                    title = stringResource(R.string.wall_toggle_style),
                    subtitle = when (toggleStyle) {
                        ToggleStyle.MATERIAL_EXPRESSIVE -> stringResource(R.string.wall_toggle_m3_expressive)
                        ToggleStyle.LIQUID_GLASS -> stringResource(R.string.wall_toggle_liquid_glass)
                        ToggleStyle.CLASSIC -> stringResource(R.string.wall_toggle_classic)
                        ToggleStyle.CUSTOM_IMAGE -> stringResource(R.string.wall_toggle_custom)
                    },
                    onClick = { onOpenDetail(StyleDetail.TOGGLE) },
                    selected = selected == StyleDetail.TOGGLE,
                    modifier = Modifier
                ) {
                    ZkmSwitch(checked = true, onCheckedChange = null)
                }
                HubCard(
                    title = stringResource(R.string.wall_slider_style),
                    subtitle = when (sliderStyle) {
                        SliderStyle.MATERIAL_EXPRESSIVE -> stringResource(R.string.wall_slider_m3_expressive)
                        SliderStyle.LIQUID_GLASS -> stringResource(R.string.wall_slider_liquid_glass)
                        SliderStyle.CLASSIC -> stringResource(R.string.wall_slider_classic)
                        SliderStyle.CUSTOM_IMAGE -> stringResource(R.string.wall_slider_custom)
                    },
                    onClick = { onOpenDetail(StyleDetail.SLIDER) },
                    selected = selected == StyleDetail.SLIDER,
                    modifier = Modifier
                ) {
                    ZkmSlider(
                        value = 0.6f,
                        onValueChange = {},
                        enabled = false,
                        modifier = Modifier.width(96.dp)
                    )
                }
            }
        }

        // ---------------- TIP ----------------
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = scheme.primaryContainer.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(scheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        stringResource(R.string.wall_pro_tip),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.wall_pro_tip_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

// =============================================================================
// HUB: ADDITION
// =============================================================================

@Composable
private fun AdditionHub(
    viewModel: WallpaperStyleViewModel,
    selected: StyleDetail?,
    onOpenDetail: (StyleDetail) -> Unit
) {
    val highRefreshRate by viewModel.highRefreshRate.collectAsStateWithLifecycle()
    val bannerEnabled by viewModel.appBarBannerEnabled.collectAsStateWithLifecycle()
    val bannerUriString by viewModel.appBarBannerUri.collectAsStateWithLifecycle()
    val bannerActive = bannerEnabled && bannerUriString != null

    StudioPage(
        title = stringResource(R.string.wall_addition),
        subtitle = stringResource(R.string.wall_addition_desc),
        badge = stringResource(R.string.wall_beta)
    ) {
        HubGrid {
            HubCard(
                title = stringResource(R.string.wall_high_refresh_rate),
                subtitle = stringResource(R.string.wall_high_refresh_rate_desc),
                onClick = { viewModel.setHighRefreshRate(!highRefreshRate) },
                trailing = null,
                modifier = Modifier
            ) {
                ZkmSwitch(
                    checked = highRefreshRate,
                    onCheckedChange = { viewModel.setHighRefreshRate(it) }
                )
            }
            HubCard(
                title = stringResource(R.string.wall_appbar_banner),
                subtitle = stringResource(
                    if (bannerActive) R.string.wall_hub_banner_on else R.string.wall_appbar_banner_empty
                ),
                onClick = { onOpenDetail(StyleDetail.BANNER) },
                selected = selected == StyleDetail.BANNER,
                modifier = Modifier
            ) {
                IconBubble(Icons.Rounded.Panorama, size = 60.dp)
            }
        }
    }
}

// =============================================================================
// DETAIL: WALLPAPER, GLASS, BANNER
// =============================================================================

@Composable
private fun WallpaperSourceDetail(viewModel: WallpaperStyleViewModel) {
    val bgType by viewModel.bgType.collectAsStateWithLifecycle()
    val backgroundUri by viewModel.backgroundImageUri.collectAsStateWithLifecycle()
    val isGallery = bgType == BgType.GALLERY

    StudioDetailColumn {
        StudioCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StudioOptionCard(
                    title = stringResource(R.string.wall_system_default),
                    icon = Icons.Outlined.Android,
                    selected = bgType != BgType.GALLERY,
                    onClick = { viewModel.setBgType(BgType.SYSTEM) },
                    modifier = Modifier.weight(1f)
                )
                StudioOptionCard(
                    title = stringResource(R.string.wall_custom_media),
                    icon = Icons.Outlined.Image,
                    selected = bgType == BgType.GALLERY,
                    onClick = { viewModel.setBgType(BgType.GALLERY) },
                    modifier = Modifier.weight(1f)
                )
            }
            if (isGallery && backgroundUri == null) {
                Text(
                    text = stringResource(R.string.wall_studio_no_media),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun WallpaperAdjustDetail(viewModel: WallpaperStyleViewModel) {
    val bgType by viewModel.bgType.collectAsStateWithLifecycle()
    val bgSaturation by viewModel.bgSaturation.collectAsStateWithLifecycle()
    val blurStrength by viewModel.blurStrength.collectAsStateWithLifecycle()
    val isBgBlur by viewModel.isBgBlur.collectAsStateWithLifecycle()
    val bgContrast by viewModel.bgContrast.collectAsStateWithLifecycle()
    val isGallery = bgType == BgType.GALLERY

    StudioDetailColumn {
        StudioCard {
            if (!isGallery) {
                Text(
                    text = stringResource(R.string.wall_studio_needs_custom_media),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)
                )
            }
            StudioSliderRow(
                label = stringResource(R.string.wall_saturation, (bgSaturation * 100).toInt()),
                value = bgSaturation,
                onValueChange = { viewModel.setBgSaturation(it) },
                valueRange = 0f..2f,
                enabled = isGallery
            )
            StudioDivider()
            StudioSwitchRow(
                title = stringResource(R.string.wall_blur),
                subtitle = null,
                checked = isBgBlur,
                enabled = isGallery,
                onCheckedChange = { viewModel.setBgBlurEnabled(it) }
            )
            AnimatedVisibility(
                visible = isBgBlur && isGallery,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                StudioSliderRow(
                    label = null,
                    value = blurStrength,
                    onValueChange = { viewModel.setBlurStrength(it) },
                    valueRange = 5f..50f
                )
            }
            StudioDivider()
            StudioSliderRow(
                label = stringResource(R.string.wall_dark_overlay, (bgContrast * 100).toInt()),
                value = bgContrast,
                onValueChange = { viewModel.setBgContrast(it) },
                valueRange = 0f..0.8f,
                enabled = isGallery
            )
        }
    }
}

@Composable
private fun CardGlassDetail(viewModel: WallpaperStyleViewModel) {
    val scheme = MaterialTheme.colorScheme
    val isCardBlur by viewModel.isCardBlur.collectAsStateWithLifecycle()
    val cardDarkness by viewModel.cardDarkness.collectAsStateWithLifecycle()
    val cardGlassStyle by viewModel.cardGlassStyle.collectAsStateWithLifecycle()
    val liquidCorner by viewModel.liquidCornerRadius.collectAsStateWithLifecycle()
    val liquidBlur by viewModel.liquidBlurRadius.collectAsStateWithLifecycle()
    val liquidRefHeight by viewModel.liquidRefractionHeight.collectAsStateWithLifecycle()
    val liquidRefAmount by viewModel.liquidRefractionAmount.collectAsStateWithLifecycle()
    val liquidChroma by viewModel.liquidChromaticAberration.collectAsStateWithLifecycle()

    StudioDetailColumn {
        StudioCard {
            StudioSwitchRow(
                title = stringResource(R.string.wall_card_glass_effect),
                subtitle = stringResource(R.string.wall_studio_glass_master_desc),
                checked = isCardBlur,
                onCheckedChange = { viewModel.setCardBlurEnabled(it) }
            )

            AnimatedVisibility(
                visible = isCardBlur,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StudioOptionCard(
                            title = stringResource(R.string.wall_card_style_frosted),
                            subtitle = stringResource(R.string.wall_card_style_frosted_desc),
                            icon = Icons.Rounded.BlurOn,
                            selected = cardGlassStyle == CardGlassStyle.FROSTED_GLASS,
                            onClick = { viewModel.setCardGlassStyle(CardGlassStyle.FROSTED_GLASS) },
                            modifier = Modifier.weight(1f)
                        )
                        StudioOptionCard(
                            title = stringResource(R.string.wall_card_style_liquid),
                            subtitle = stringResource(R.string.wall_card_style_liquid_desc),
                            icon = Icons.Rounded.WaterDrop,
                            selected = cardGlassStyle == CardGlassStyle.LIQUID_GLASS,
                            onClick = { viewModel.setCardGlassStyle(CardGlassStyle.LIQUID_GLASS) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    AnimatedVisibility(
                        visible = cardGlassStyle == CardGlassStyle.LIQUID_GLASS,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            StudioDivider()
                            Text(
                                text = stringResource(R.string.wall_liquid_glass_settings),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = scheme.primary,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp)
                            )
                            StudioSliderRow(
                                label = stringResource(R.string.wall_liquid_corner_radius, liquidCorner.toInt()),
                                value = liquidCorner,
                                onValueChange = { viewModel.setLiquidCornerRadius(it) },
                                valueRange = 0f..40f
                            )
                            StudioSliderRow(
                                label = stringResource(R.string.wall_liquid_blur_radius, liquidBlur.toInt()),
                                value = liquidBlur,
                                onValueChange = { viewModel.setLiquidBlurRadius(it) },
                                valueRange = 0f..30f
                            )
                            StudioSliderRow(
                                label = stringResource(R.string.wall_liquid_refraction_height, liquidRefHeight.toInt()),
                                value = liquidRefHeight,
                                onValueChange = { viewModel.setLiquidRefractionHeight(it) },
                                valueRange = 0f..48f
                            )
                            StudioSliderRow(
                                label = stringResource(R.string.wall_liquid_refraction_amount, liquidRefAmount.toInt()),
                                value = liquidRefAmount,
                                onValueChange = { viewModel.setLiquidRefractionAmount(it) },
                                valueRange = 0f..48f
                            )
                            StudioDivider()
                            StudioSwitchRow(
                                title = stringResource(R.string.wall_liquid_chromatic),
                                subtitle = stringResource(R.string.wall_liquid_chromatic_desc),
                                checked = liquidChroma,
                                onCheckedChange = { viewModel.setLiquidChromaticAberration(it) }
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = !isCardBlur,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    StudioDivider()
                    StudioSliderRow(
                        label = stringResource(R.string.wall_card_opacity, (cardDarkness * 100).toInt()),
                        value = cardDarkness,
                        onValueChange = { viewModel.setCardDarkness(it) },
                        valueRange = 0f..1f
                    )
                }
            }
        }
    }
}

@Composable
private fun AppBarBannerDetail(viewModel: WallpaperStyleViewModel) {
    val scheme = MaterialTheme.colorScheme
    val bannerEnabled by viewModel.appBarBannerEnabled.collectAsStateWithLifecycle()
    val bannerUriString by viewModel.appBarBannerUri.collectAsStateWithLifecycle()
    val bannerTypeKey by viewModel.appBarBannerType.collectAsStateWithLifecycle()
    val bannerDim by viewModel.appBarBannerDim.collectAsStateWithLifecycle()
    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.setAppBarBannerMedia(it) }
    }

    StudioDetailColumn {
        StudioCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(scheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.BlurOn,
                        contentDescription = null,
                        tint = scheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        stringResource(R.string.wall_studio_appbar_blur_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        stringResource(R.string.wall_studio_appbar_blur_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
            StudioDivider()
            StudioSwitchRow(
                title = stringResource(R.string.wall_appbar_banner),
                subtitle = stringResource(R.string.wall_appbar_banner_desc),
                checked = bannerEnabled && bannerUriString != null,
                enabled = bannerUriString != null,
                onCheckedChange = { viewModel.setAppBarBannerEnabled(it) }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(132.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(scheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                val previewUri = bannerUriString
                if (previewUri != null) {
                    ZkmAppBarBanner(
                        uri = android.net.Uri.parse(previewUri),
                        type = AppBarBannerType.fromKey(bannerTypeKey),
                        modifier = Modifier.fillMaxSize(),
                        dim = bannerDim
                    )
                } else {
                    Text(
                        stringResource(R.string.wall_appbar_banner_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            if (bannerUriString != null) {
                StudioSliderRow(
                    label = stringResource(R.string.wall_appbar_banner_dim, (bannerDim * 100).toInt()),
                    value = bannerDim,
                    onValueChange = { viewModel.setAppBarBannerDim(it) },
                    valueRange = 0f..0.7f
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { bannerPicker.launch(arrayOf("image/*", "video/*")) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(
                        stringResource(
                            if (bannerUriString == null) R.string.wall_appbar_banner_pick
                            else R.string.wall_appbar_banner_change
                        ),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (bannerUriString != null) {
                    OutlinedButton(
                        onClick = { viewModel.clearAppBarBanner() },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(stringResource(R.string.wall_appbar_banner_remove))
                    }
                }
            }
        }
    }
}

// =============================================================================
// KOMPONEN UI BERSAMA
// =============================================================================

/** Halaman hub: judul + isi yang bisa discroll. Ruang bawah disisakan untuk capsule melayang. */
@Composable
private fun StudioPage(
    title: String,
    subtitle: String,
    badge: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = bottomInset + 110.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                )
                if (badge != null) {
                    Spacer(Modifier.width(10.dp))
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            badge,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        content()
    }
}

/** Isi halaman detail yang discroll (dipakai detail buatan studio ini). */
@Composable
private fun StudioDetailColumn(content: @Composable ColumnScope.() -> Unit) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = bottomInset + 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content
    )
}

@Composable
private fun StudioCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp), content = content)
    }
}

@Composable
private fun StudioSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp)
    )
}

@Composable
private fun StudioDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    )
}

@Composable
private fun StudioSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        ZkmSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun StudioSliderRow(
    label: String?,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
        }
        ZkmSlider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled
        )
    }
}

@Composable
private fun StudioOptionCard(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh,
        tween(220),
        label = "OptionCardColor"
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = container,
        border = if (selected) BorderStroke(2.dp, scheme.primary) else BorderStroke(2.dp, Color.Transparent),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Text(
                title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = if (selected) scheme.onPrimaryContainer else scheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = (if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant)
                        .copy(alpha = 0.8f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// KARTU HUB (gaya layar Personalisasi: visual besar di atas, judul + chevron di bawah)
// -----------------------------------------------------------------------------

/** Jumlah kolom kartu hub. 2 di ponsel; bertambah mengikuti lebar panel di PC mode / landscape. */
private val LocalHubColumns = compositionLocalOf { 2 }

/**
 * Grid kartu hub: [LocalHubColumns] kolom, lebar sel sama rata, tinggi tiap baris seragam.
 * Setiap child harus berupa satu kartu (HubCard).
 */
@Composable
private fun HubGrid(content: @Composable () -> Unit) {
    val columns = LocalHubColumns.current
    Layout(content = content, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val gap = 12.dp.roundToPx()
        val cols = columns.coerceAtLeast(1)
        val totalW = constraints.maxWidth
        val cellW = ((totalW - gap * (cols - 1)) / cols).coerceAtLeast(0)
        val rows = measurables.chunked(cols)
        val rowHeights = rows.map { row -> row.maxOf { it.minIntrinsicHeight(cellW) } }
        val placed = rows.mapIndexed { i, row ->
            row.map { it.measure(Constraints.fixed(cellW, rowHeights[i])) }
        }
        val totalH = rowHeights.sum() + gap * (rows.size - 1).coerceAtLeast(0)
        layout(totalW, totalH) {
            var y = 0
            placed.forEachIndexed { i, row ->
                row.forEachIndexed { c, p -> p.placeRelative(c * (cellW + gap), y) }
                y += rowHeights[i] + gap
            }
        }
    }
}

@Composable
private fun HubCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    trailing: ImageVector? = Icons.Rounded.ChevronRight,
    visualHeight: Dp = 84.dp,
    visual: @Composable BoxScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (selected) scheme.primaryContainer else scheme.surfaceContainer,
        tween(220),
        label = "HubCardColor"
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = container,
        border = BorderStroke(2.dp, if (selected) scheme.primary else Color.Transparent),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(visualHeight),
                contentAlignment = Alignment.Center,
                content = visual
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) scheme.onPrimaryContainer.copy(alpha = 0.8f)
                        else scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (trailing != null) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        trailing,
                        contentDescription = null,
                        tint = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun IconBubble(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    tint: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.48f)
        )
    }
}
