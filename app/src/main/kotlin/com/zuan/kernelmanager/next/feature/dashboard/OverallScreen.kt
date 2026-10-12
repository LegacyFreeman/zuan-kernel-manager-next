/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalHazeMaterialsApi::class
)

package com.zuan.kernelmanager.next.feature.dashboard

import com.zuan.kernelmanager.next.ui.adaptive.zkmLargeAppBarExpandedHeight
import com.zuan.kernelmanager.next.ui.components.zkmAppBarHazeStyle
import com.zuan.kernelmanager.next.ui.components.zkmCardHazeEffect
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundContentColor
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundSubContentColor
import com.zuan.kernelmanager.next.ui.components.zkmCustomBackgroundCardColor
import com.zuan.kernelmanager.next.ui.components.zkmGlassFallbackTint
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import com.zuan.kernelmanager.next.ui.components.BlurredAlertDialog
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage 
import coil3.request.ImageRequest
import com.composables.core.rememberDialogState

import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView

import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.feature.help.ZkmHelpButton
import com.zuan.kernelmanager.next.ui.components.VideoWallpaperPlayer
import com.zuan.kernelmanager.next.ui.components.AppBarBannerType
import com.zuan.kernelmanager.next.ui.components.ZkmAppBarBanner
import com.zuan.kernelmanager.next.ui.components.WeatherEffectOverlay
import com.zuan.kernelmanager.next.ui.components.battery.BatteryWeatherCard
import com.zuan.kernelmanager.next.feature.settings.BgType
import com.zuan.kernelmanager.next.feature.settings.SettingsViewModel
import com.zuan.kernelmanager.next.ui.theme.ThemeMode
import com.zuan.kernelmanager.next.core.util.Utils
import com.zuan.kernelmanager.next.ui.navigation.NavigationRoute
import com.zuan.kernelmanager.next.ui.navigation.ZkmTabNavigator
import com.zuan.kernelmanager.next.feature.soc.SoCTab
import com.zuan.kernelmanager.next.feature.tuning.CpuGpuUtils
import com.zuan.kernelmanager.next.feature.dashboard.layout.DashboardContent
import com.zuan.kernelmanager.next.feature.dashboard.layout.DashboardFabMenu
import com.zuan.kernelmanager.next.feature.dashboard.layout.DashboardLayoutViewModel
import com.zuan.kernelmanager.next.feature.dashboard.layout.DashboardWidgetType
import com.zuan.kernelmanager.next.feature.dashboard.layout.WidgetGalleryBottomSheet
import com.zuan.kernelmanager.next.feature.dashboard.layout.AddWidgetSheet
import com.zuan.kernelmanager.next.feature.dashboard.layout.QuickToolsDialog
import com.zuan.kernelmanager.next.feature.dashboard.layout.WidgetDragOverlay
import com.zuan.kernelmanager.next.feature.dashboard.layout.WidgetDragState
import com.zuan.kernelmanager.next.feature.dashboard.layout.computeWidgetDropTarget
import com.zuan.kernelmanager.next.feature.dashboard.layout.screenPosition
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.zuan.kernelmanager.next.feature.dashboard.components.AvatarWithDecoration
import com.zuan.kernelmanager.next.feature.dashboard.components.ProfileAvatar
import com.zuan.kernelmanager.next.feature.settings.SettingsPreference
import com.zuan.kernelmanager.next.feature.dashboard.components.ProfileCropDialog
import com.zuan.kernelmanager.next.feature.dashboard.components.ProfileMedia
import com.zuan.kernelmanager.next.feature.dashboard.components.ProfileImportResult
import com.zuan.kernelmanager.next.feature.dashboard.components.ProfileMediaStore
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import java.util.Calendar

import com.zuan.kernelmanager.next.feature.dashboard.components.*
import com.zuan.kernelmanager.next.ui.components.ZkmHazeStyleCache

@Composable
fun OverallScreen(
    navController: NavController,
    hazeState: HazeState,
    viewModel: OverallViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    layoutViewModel: DashboardLayoutViewModel = viewModel()
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    viewModel.monitoring.collectAsStateWithLifecycle()
    
    val manufacturer = remember { android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() } }

    val lazyListState = rememberLazyListState()

    val visibleWidgets by layoutViewModel.visibleWidgets.collectAsStateWithLifecycle()
    
    LaunchedEffect(visibleWidgets) {
        viewModel.setVisibleWidgets(visibleWidgets.toSet())
    }
    
    val hiddenWidgets by layoutViewModel.hiddenWidgets.collectAsStateWithLifecycle()
    val fullOrder by layoutViewModel.fullOrder.collectAsStateWithLifecycle()
    var showWidgetGallery by remember { mutableStateOf(false) }

    // --- ADD WIDGET (drag & drop dari sheet ke dashboard) + QUICK TOOLS ---
    var showAddWidget by remember { mutableStateOf(false) }
    var showQuickTools by remember { mutableStateOf(false) }
    val widgetDragState = remember { WidgetDragState() }
    // Koordinat LAYAR (bukan root): sheet Add Widget hidup di window sendiri.
    val hostView = LocalView.current
    var listOrigin by remember { mutableStateOf(Offset.Zero) }
    var pendingScrollTo by remember { mutableStateOf<DashboardWidgetType?>(null) }
    // Widget yang belum ada di dashboard (tambahan dulu, lalu widget inti yang dimatikan di Gallery)
    val addCandidates = remember(fullOrder, hiddenWidgets) {
        fullOrder.filter { it in hiddenWidgets }.sortedBy { if (it.isExtra) 0 else 1 }
    }
    val dropSpacingPx = remember(density) { with(density) { 16.dp.toPx() } }

    val fabVisible by remember {
        derivedStateOf {
            !lazyListState.canScrollBackward || lazyListState.lastScrolledBackward
        }
    }

    // --- DATA STATE UNTUK TEMA & BACKGROUND ---
    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val isSystemDark = isSystemInDarkTheme()
    val useDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM_DEFAULT -> isSystemDark
    }

    val bgType by settingsViewModel.bgType.collectAsStateWithLifecycle()
    val isCustomBg = bgType == BgType.GALLERY
    val bgUriString by settingsViewModel.backgroundImageUri.collectAsStateWithLifecycle()
    val isVideo by settingsViewModel.isVideoWallpaper.collectAsStateWithLifecycle()
    val isBgBlur by settingsViewModel.isBgBlur.collectAsStateWithLifecycle()
    val blurStrength by settingsViewModel.blurStrength.collectAsStateWithLifecycle()
    val bgSaturation by settingsViewModel.bgSaturation.collectAsStateWithLifecycle()
    val bgContrast by settingsViewModel.bgContrast.collectAsStateWithLifecycle()
    val isCardBlur by settingsViewModel.isHazeEnabled.collectAsStateWithLifecycle()
    val cardDarkness by settingsViewModel.cardDarkness.collectAsStateWithLifecycle()
    val weatherEffect by settingsViewModel.weatherEffect.collectAsStateWithLifecycle()
    val weatherIntensity by settingsViewModel.weatherIntensity.collectAsStateWithLifecycle()

    val isDynamic by settingsViewModel.isDynamicColor.collectAsStateWithLifecycle()
    val themeColorName by settingsViewModel.currentThemeColor.collectAsStateWithLifecycle()
    val isCustomColor by settingsViewModel.isCustomColor.collectAsStateWithLifecycle()
    val customPrimary by settingsViewModel.customPrimaryColor.collectAsStateWithLifecycle()

    // [BETA] Appbar Banner Progressive (uji coba: hanya layar Overall)
    val bannerEnabled by settingsViewModel.appBarBannerEnabled.collectAsStateWithLifecycle()
    val bannerUriString by settingsViewModel.appBarBannerUri.collectAsStateWithLifecycle()
    val bannerTypeKey by settingsViewModel.appBarBannerType.collectAsStateWithLifecycle()
    val bannerDim by settingsViewModel.appBarBannerDim.collectAsStateWithLifecycle()
    val bannerActive = bannerEnabled && !bannerUriString.isNullOrBlank()
    val bannerUri = remember(bannerUriString) { bannerUriString?.let { Uri.parse(it) } }

    val colorScheme = MaterialTheme.colorScheme

    val effectivePrimary = remember(isDynamic, themeColorName, isCustomColor, customPrimary) {
        when {
            isCustomColor -> Color(customPrimary)
            isDynamic -> Color.Unspecified
            else -> themeColorName.primary
        }
    }

    val finalPrimary = if (effectivePrimary == Color.Unspecified) {
        colorScheme.primary
    } else {
        effectivePrimary
    }

    val (themeBgColor, themeCardColor) = generateThemeBackgroundColors(useDarkTheme, finalPrimary)

    val targetCardColor = themeCardColor
    val targetBgColor = themeBgColor

    val shouldUseDarkText = !useDarkTheme
    val customOnSurface = if (shouldUseDarkText) Color(0xFF111111) else Color(0xFFEEEEEE)
    val customOnSurfaceVariant = if (shouldUseDarkText) Color(0xFF666666) else Color(0xFFAAAAAA)

    val isGlassActive = isCardBlur && isCustomBg

    val mainBackgroundColor = if (isCustomBg) Color.Transparent else targetBgColor

    val cardShape = RoundedCornerShape(30.dp)

    val customWidgetHazeStyle = ZkmHazeStyleCache.of(
        backgroundColor = targetCardColor.copy(alpha = 0.5f),
        blurRadius = 30.dp,
        noiseFactor = 0.0f,
        tints = emptyList()
    )

    val baseWidgetColor = when {
        isGlassActive -> zkmGlassFallbackTint(zkmIsDarkTheme())
        isCustomBg -> themeCardColor.copy(alpha = cardDarkness) 
        else -> targetCardColor
    }

    val baseWidgetModifier = if (isGlassActive) {
        Modifier
            .zkmCardHazeEffect(shape = cardShape, state = hazeState, style = customWidgetHazeStyle)
    } else {
        Modifier
            .clip(cardShape)
    }

    // --- NAVIGASI KARTU OVERALL -> HALAMAN TERKAIT ---
    val navScope = rememberCoroutineScope()
    var gpuNavBusy by remember { mutableStateOf(false) }

    // GPU: deteksi chipset sama persis seperti kartu Graphics di tab SoC (CpuGpuScreen).
    val openGpuScreen: () -> Unit = {
        if (!gpuNavBusy) {
            gpuNavBusy = true
            navScope.launch {
                try {
                    when (CpuGpuUtils.getGpuType()) {
                        CpuGpuUtils.GpuType.ADRENO ->
                            navController.navigate(NavigationRoute.Adreno.route)
                        CpuGpuUtils.GpuType.MEDIATEK_V2,
                        CpuGpuUtils.GpuType.MEDIATEK_LEGACY ->
                            navController.navigate(NavigationRoute.Mtk.route)
                        // GPU generik / tidak dikenal: layar khusus belum ada -> buka tab Processor di SoC
                        else -> ZkmTabNavigator.openSocTab(navController, SoCTab.CpuGpu)
                    }
                } finally {
                    gpuNavBusy = false
                }
            }
        }
    }
    val openWakelockScreen: () -> Unit = { navController.navigate(NavigationRoute.WakelockAnalyzer.route) }
    val openBatteryScreen: () -> Unit = { navController.navigate(NavigationRoute.BatteryController.route) }
    val openChargeStatsScreen: () -> Unit = { navController.navigate(NavigationRoute.ChargeStats.route) }
    val openThermalScreen: () -> Unit = { navController.navigate(NavigationRoute.ThermalDevices.route) }
    val openMemoryTab: () -> Unit = { ZkmTabNavigator.openSocTab(navController, SoCTab.Memory) }
    val openSchedulerTab: () -> Unit = { ZkmTabNavigator.openSocTab(navController, SoCTab.Sched) }
    val openDisplayScreen: () -> Unit = { navController.navigate(NavigationRoute.Display.route) }
    val openStorageScreen: () -> Unit = { navController.navigate(NavigationRoute.Storage.route) }

    val updateDialogState = rememberDialogState(initiallyVisible = false)
    
    val sharedPrefs = remember { context.getSharedPreferences("user_profile_prefs", Context.MODE_PRIVATE) }

    var userName by remember { mutableStateOf(sharedPrefs.getString("user_name", "Your Name") ?: "Your Name") }
    var profileMedia by remember { mutableStateOf(ProfileMediaStore.load(context)) }
    // [BARU] Bingkai avatar (avatar decoration ala Discord), diatur dari Wallpaper Style > Personalize
    val avatarDeco by remember { SettingsPreference.getInstance(context) }.avatarDecoration.collectAsState()
    val profileScope = rememberCoroutineScope()
    var pendingMedia by remember { mutableStateOf<ProfileMedia?>(null) }

    var showNameDialog by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf(userName) }

    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val croppedUri = result.uriContent
            if (croppedUri != null) {
                profileScope.launch {
                    ProfileMediaStore.adoptCropped(context, croppedUri)?.let {
                        ProfileMediaStore.save(context, it)
                        profileMedia = it
                    }
                }
            }
        } else {
            android.util.Log.e("OverallScreen", result.error?.toString() ?: "Unknown update error")
        }
    }

    // Foto, GIF, video, Live (Motion) Photo
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { pickedUri ->
            profileScope.launch {
                when (val r = ProfileMediaStore.import(context, pickedUri)) {
                    is ProfileImportResult.NeedsCrop -> {
                        // Foto statis: crop OVAL 1:1 seperti sebelumnya
                        cropLauncher.launch(
                            CropImageContractOptions(
                                uri = r.source,
                                cropImageOptions = CropImageOptions(
                                    fixAspectRatio = true,
                                    aspectRatioX = 1,
                                    aspectRatioY = 1,
                                    cropShape = CropImageView.CropShape.OVAL,
                                    outputCompressFormat = android.graphics.Bitmap.CompressFormat.PNG,
                                    outputCompressQuality = 100
                                )
                            )
                        )
                    }
                    is ProfileImportResult.Ready -> {
                        // Video / GIF / Live Photo: buka editor crop dulu
                        pendingMedia = r.media
                    }
                    ProfileImportResult.TooLarge ->
                        android.widget.Toast.makeText(context, R.string.profile_media_too_large, android.widget.Toast.LENGTH_LONG).show()
                    ProfileImportResult.Failed ->
                        android.widget.Toast.makeText(context, R.string.profile_media_failed, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    pendingMedia?.let { pm ->
        ProfileCropDialog(
            media = pm,
            onCancel = {
                ProfileMediaStore.discard(context, pm, profileMedia)
                pendingMedia = null
            },
            onConfirm = { cropped ->
                ProfileMediaStore.save(context, cropped)
                profileMedia = cropped
                pendingMedia = null
            }
        )
    }

    LaunchedEffect(Unit) {
        val currentVer = Utils.getAppVersionName(context)
        viewModel.start(currentVer, context)
    }

    val isBuggyAndroid12 = Build.VERSION.SDK_INT == Build.VERSION_CODES.S || Build.VERSION.SDK_INT == Build.VERSION_CODES.S_V2

    val isCollapsed by remember { derivedStateOf { scrollBehavior.state.collapsedFraction > 0.6f } }
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeightDp = with(density) { headerHeightPx.toDp() }

    state.updateRelease?.let { release ->
        UpdateChangelogDialog(
            state = updateDialogState,
            release = release,
            hazeState = hazeState,
            onUpdate = { url ->
                updateDialogState.visible = false
                val fileName = "ZKM_${release.tag_name}.apk"
                Utils.downloadAndInstallApk(context, url, fileName)
            }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {

            // LAYER 0: BACKGROUND & WEATHER EFFECT
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(mainBackgroundColor) 
                    .hazeSource(state = hazeState, zIndex = 0f)
            ) {
                if (isCustomBg && bgUriString != null) {
                    val blurModifier = if (isBgBlur && blurStrength > 0f) {
                        Modifier.blur(blurStrength.dp)
                    } else if (isBgBlur) {
                        Modifier.blur(20.dp)
                    } else {
                        Modifier
                    }

                    val saturationMatrix = ColorMatrix().apply {
                        setToSaturation(bgSaturation)
                    }
                    val colorFilter = ColorFilter.colorMatrix(saturationMatrix)

                    val uri = Uri.parse(bgUriString)

                    if (isVideo) {
                        VideoWallpaperPlayer(
                            uri = uri,
                            modifier = Modifier.fillMaxSize().then(blurModifier)
                        )
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(uri).build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().then(blurModifier),
                            contentScale = ContentScale.Crop,
                            colorFilter = colorFilter
                        )
                    }

                    if (bgContrast > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = bgContrast))
                        )
                    }
                }

                WeatherEffectOverlay(
                    effect = weatherEffect,
                    intensity = weatherIntensity,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // LAYER 1: KONTEN DASHBOARD (SCROLLABLE DENGAN HAZE SOURCE)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState, zIndex = 1f)
            ) {
                DashboardContent(
                    widgets = visibleWidgets,
                    lazyListState = lazyListState,
                    hazeState = hazeState,
                    topPadding = headerHeightDp,
                    onMove = layoutViewModel::moveWidget,
                    onDragEnd = layoutViewModel::persistOrder,
                    modifier = Modifier.onGloballyPositioned { listOrigin = it.screenPosition(hostView) },
                    headerContent = state.updateRelease?.let { release ->
                        {
                            val updateCardColor = if (isGlassActive || isCustomBg)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.primaryContainer
                            UpdateCard(release, baseWidgetModifier, updateCardColor, { updateDialogState.visible = true }, customOnSurface)
                        }
                    }
                ) { widget ->
                    when (widget) {
                        DashboardWidgetType.DEVICE_HERO -> {
                            DeviceHeroSection(state, baseWidgetModifier, baseWidgetColor, customOnSurface, customOnSurfaceVariant)
                        }

                        DashboardWidgetType.BATTERY_DASHBOARD -> {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                SectionTitle(
                                    text = stringResource(R.string.overall_dashboard),
                                    textColor = customOnSurface,
                                    trailing = { BatteryStatusChip(state.batteryInfo, customOnSurface, customOnSurfaceVariant) }
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max).heightIn(min = 240.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    BatteryWeatherCard(
                                        info = state.batteryInfo,
                                        modifier = baseWidgetModifier.weight(1f).fillMaxHeight(),
                                        onClick = { }
                                    )
                                    OngoingInfoCard(
                                        modifier = baseWidgetModifier.weight(1f).fillMaxHeight(),
                                        info = state.batteryInfo,
                                        containerColor = baseWidgetColor,
                                        contentColor = customOnSurface,
                                        subColor = customOnSurfaceVariant,
                                        onClick = openWakelockScreen
                                    )
                                }
                                DetailedBatteryStats(
                                    state, baseWidgetModifier, baseWidgetColor, customOnSurface, customOnSurfaceVariant,
                                    onOpenBattery = openBatteryScreen,
                                    onOpenChargeStats = openChargeStatsScreen,
                                    onOpenThermal = openThermalScreen
                                )
                            }
                        }

                        DashboardWidgetType.CPU_MONITOR -> {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                SectionTitle(stringResource(R.string.overall_processor), customOnSurface)
                                CPUMonitorWidget(
                                    state = state,
                                    modifier = baseWidgetModifier,
                                    containerColor = baseWidgetColor,
                                    textColor = customOnSurface,
                                    subTextColor = customOnSurfaceVariant,
                                    onOpenProcessManager = { navController.navigate(NavigationRoute.ProcessManager.route) },
                                    onCoreClick = { index -> viewModel.loadCoreDetail(index) }
                                )
                            }
                        }

                        DashboardWidgetType.GPU -> {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                SectionTitle(stringResource(R.string.overall_graphics), customOnSurface)
                                GPUWidget(state, baseWidgetModifier, baseWidgetColor, customOnSurface, customOnSurfaceVariant, onClick = openGpuScreen)
                            }
                        }

                        DashboardWidgetType.MEMORY -> {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                SectionTitle(stringResource(R.string.overall_memory), customOnSurface)
                                MemoryWidget(state, baseWidgetModifier, baseWidgetColor, customOnSurface, customOnSurfaceVariant, onClick = openMemoryTab)
                            }
                        }

                        DashboardWidgetType.VM_PARAMS -> {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                SectionTitle(stringResource(R.string.overall_vm_params), customOnSurface)
                                VMParametersWidget(state, baseWidgetModifier, baseWidgetColor, customOnSurface, customOnSurfaceVariant, onClick = openSchedulerTab)
                            }
                        }

                        DashboardWidgetType.DISPLAY_INFO -> {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                SectionTitle(stringResource(R.string.overall_display), customOnSurface)
                                DisplayInfoWidget(state, baseWidgetModifier, baseWidgetColor, customOnSurface, customOnSurfaceVariant, onClick = openDisplayScreen)
                            }
                        }

                        DashboardWidgetType.STORAGE_INTELLIGENCE -> {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                SectionTitle(stringResource(R.string.lp_overall_storage_intelligence), customOnSurface)
                                StorageIntelligenceWidget(state, baseWidgetModifier, baseWidgetColor, customOnSurface, customOnSurfaceVariant, onClick = openStorageScreen)
                            }
                        }
                        DashboardWidgetType.UFS_HEALTH -> {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                SectionTitle(stringResource(R.string.lp_overall_ufs_health), customOnSurface)
                                UfsHealthWidget(state, baseWidgetModifier, baseWidgetColor, customOnSurface, customOnSurfaceVariant, onClick = openStorageScreen)
                            }
                        }

                        // ---- Widget tambahan (Add Widget) ----
                        DashboardWidgetType.THERMAL_MONITOR -> {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                SectionTitle(stringResource(R.string.lp_widget_thermal_monitor), customOnSurface)
                                ThermalMonitorWidget(state, baseWidgetModifier, baseWidgetColor, customOnSurface, customOnSurfaceVariant)
                            }
                        }
                        DashboardWidgetType.POWER_DRAW -> {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                SectionTitle(stringResource(R.string.lp_widget_power_draw), customOnSurface)
                                PowerDrawWidget(state, baseWidgetModifier, baseWidgetColor, customOnSurface, customOnSurfaceVariant)
                            }
                        }
                        DashboardWidgetType.SYSTEM_UPTIME -> {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                SectionTitle(stringResource(R.string.lp_widget_system_uptime), customOnSurface)
                                SystemUptimeWidget(state, baseWidgetModifier, baseWidgetColor, customOnSurface, customOnSurfaceVariant)
                            }
                        }
                        DashboardWidgetType.SYSTEM_PRESSURE -> {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                SectionTitle(stringResource(R.string.lp_widget_system_pressure), customOnSurface)
                                SystemPressureWidget(state, baseWidgetModifier, baseWidgetColor, customOnSurface, customOnSurfaceVariant)
                            }
                        }
                        DashboardWidgetType.ROOT_SECURITY -> {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                SectionTitle(stringResource(R.string.lp_widget_root_security), customOnSurface)
                                RootSecurityWidget(state, baseWidgetModifier, baseWidgetColor, customOnSurface, customOnSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // LAYER 2: TOP HEADER (PROGRESSIVE BLUR)
            val headerHazeStyle = zkmAppBarHazeStyle(themeBgColor, isCustomBg)
            // Di atas banner, teks selalu putih (banner bisa terang/gelap, di-dim oleh scrim).
            val headerOnSurface = if (bannerActive) Color.White else customOnSurface
            val headerOnSurfaceVariant = if (bannerActive) Color.White.copy(alpha = 0.78f) else customOnSurfaceVariant

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .onGloballyPositioned { headerHeightPx = it.size.height }
                    .hazeEffect(
                        state = hazeState,
                        style = headerHazeStyle
                    ) {
                        progressive = HazeProgressive.verticalGradient(
                            startIntensity = 1f,
                            endIntensity = 0f
                        )
                        if (isBuggyAndroid12) forceInvalidateOnPreDraw = true
                    }
                    .zIndex(2f)
            ) {
                // [BETA] Banner menimpa seluruh app bar, memudar progresif ke bawah.
                if (bannerActive && bannerUri != null) {
                    ZkmAppBarBanner(
                        uri = bannerUri,
                        type = AppBarBannerType.fromKey(bannerTypeKey),
                        modifier = Modifier.matchParentSize(),
                        dim = bannerDim
                    )
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    LargeTopAppBar(
                        expandedHeight = zkmLargeAppBarExpandedHeight(),
                        title = {
                            AnimatedContent(
                                targetState = isCollapsed,
                                transitionSpec = { (fadeIn(animationSpec = tween(300)) + slideInVertically { it / 2 }).togetherWith(fadeOut(animationSpec = tween(300)) + slideOutVertically { -it / 2 }) },
                                label = "AppBarTitleAnim"
                            ) { collapsed ->
                                if (collapsed) {
                                    val updateRelease = state.updateRelease
                                    Column {
                                        Text("$manufacturer ${state.deviceModel}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = headerOnSurface)
                                        if (updateRelease != null) {
                                            Text(stringResource(R.string.update_available, updateRelease.tag_name), style = MaterialTheme.typography.labelSmall, color = ColorBrandSecondary, fontWeight = FontWeight.Bold, maxLines = 1)
                                        } else {
                                            Text(state.deviceCodename, style = MaterialTheme.typography.labelSmall, color = headerOnSurfaceVariant, maxLines = 1)
                                        }
                                    }
                                } else {
                                    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
                                    val greeting = remember(currentHour) {
                                        when (currentHour) {
                                            in 5..11 -> "Good Morning"
                                            in 12..17 -> "Good Afternoon"
                                            in 18..22 -> "Good Evening"
                                            else -> "Good Night"
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(end = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = greeting,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = headerOnSurface
                                            )
                                            Text(
                                                text = userName,
                                                style = MaterialTheme.typography.headlineLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = headerOnSurface,
                                                letterSpacing = (-0.5).sp,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        tempName = userName
                                                        showNameDialog = true
                                                    }
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Code,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = headerOnSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = stringResource(R.string.app_name),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = headerOnSurfaceVariant
                                                )
                                            }
                                        }

                                        // Avatar + bingkai dekorasi (ala Discord premium badge)
                                        AvatarWithDecoration(
                                            style = avatarDeco,
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clickable {
                                                    mediaPickerLauncher.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                                    )
                                                }
                                        ) {
                                            Surface(
                                                modifier = Modifier.fillMaxSize(),
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                border = BorderStroke(1.dp, headerOnSurfaceVariant.copy(alpha = 0.2f))
                                            ) {
                                                val media = profileMedia
                                                if (media != null) {
                                                    ProfileAvatar(
                                                        media = media,
                                                        contentDescription = stringResource(R.string.lp_a11y_profile),
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Person,
                                                        contentDescription = stringResource(R.string.lp_a11y_profile),
                                                        modifier = Modifier.padding(12.dp),
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        actions = {
                            ZkmHelpButton(NavigationRoute.Overall.route, tint = headerOnSurface)
                        },
                        colors = TopAppBarDefaults.largeTopAppBarColors(
                            containerColor = Color.Transparent, 
                            scrolledContainerColor = Color.Transparent, 
                            actionIconContentColor = headerOnSurface, 
                            navigationIconContentColor = headerOnSurface
                        ),
                        scrollBehavior = scrollBehavior,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Spacer Fading Buffer (Bumbu Rahasia)
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // LAYER 3: FLOATING ACTIONS & OVERLAYS
            DashboardFabMenu(
                visible = fabVisible && !showAddWidget,
                onOpenWidgetGallery = { showWidgetGallery = true },
                onAddWidget = { showAddWidget = true },
                onResetLayout = { layoutViewModel.resetLayout() },
                onOpenQuickTools = { showQuickTools = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 100.dp)
                    .zIndex(3f)
            )

            if (showWidgetGallery) {
                WidgetGalleryBottomSheet(
                    // Widget tambahan baru muncul di Gallery setelah dipasang lewat Add Widget
                    allWidgets = fullOrder.filter { !it.isExtra || it !in hiddenWidgets },
                    hiddenWidgets = hiddenWidgets,
                    onToggle = layoutViewModel::toggleWidget,
                    onDismiss = { showWidgetGallery = false }
                )
            }

            // --- ADD WIDGET: titik sisip, auto-scroll di tepi, sheet, dan overlay drag ---
            val widgetDragging = widgetDragState.dragging != null
            val dropTarget by remember {
                derivedStateOf {
                    if (widgetDragState.dragging == null) null
                    else computeWidgetDropTarget(
                        layoutInfo = lazyListState.layoutInfo,
                        widgets = visibleWidgets,
                        pointerYInList = widgetDragState.pointer.y - listOrigin.y,
                        spacingPx = dropSpacingPx
                    )
                }
            }

            // Auto-scroll saat jari mendekati tepi atas/bawah list supaya bisa menaruh widget di mana saja
            LaunchedEffect(widgetDragging) {
                if (!widgetDragging) return@LaunchedEffect
                val edge = with(density) { 110.dp.toPx() }
                while (isActive) {
                    val y = widgetDragState.pointer.y - listOrigin.y
                    val topLimit = headerHeightPx + edge * 0.35f
                    val bottomLimit = lazyListState.layoutInfo.viewportEndOffset - edge
                    when {
                        y < topLimit -> lazyListState.scrollBy(-(((topLimit - y) / edge).coerceIn(0f, 1f) * 30f))
                        y > bottomLimit -> lazyListState.scrollBy(((y - bottomLimit) / edge).coerceIn(0f, 1f) * 30f)
                    }
                    delay(16)
                }
            }

            // Setelah "+" di kartu: scroll ke widget yang baru dipasang
            LaunchedEffect(pendingScrollTo, visibleWidgets) {
                val target = pendingScrollTo ?: return@LaunchedEffect
                val idx = visibleWidgets.indexOf(target)
                if (idx >= 0) {
                    val fixedCount = 1 + if (state.updateRelease != null) 1 else 0
                    lazyListState.animateScrollToItem(idx + fixedCount, -headerHeightPx)
                    pendingScrollTo = null
                }
            }

            AddWidgetSheet(
                visible = showAddWidget,
                candidates = addCandidates,
                dragState = widgetDragState,
                onDrop = { widget, at ->
                    val target = computeWidgetDropTarget(
                        layoutInfo = lazyListState.layoutInfo,
                        widgets = visibleWidgets,
                        pointerYInList = at.y - listOrigin.y,
                        spacingPx = dropSpacingPx
                    )
                    layoutViewModel.addWidget(widget, target.index)
                    showAddWidget = false
                },
                onTapAdd = { widget ->
                    layoutViewModel.addWidget(widget, Int.MAX_VALUE)
                    pendingScrollTo = widget
                    showAddWidget = false
                },
                onDismiss = { showAddWidget = false }
            )

            WidgetDragOverlay(
                dragState = widgetDragState,
                dropLineRootY = dropTarget?.let { listOrigin.y + it.lineY },
                modifier = Modifier.zIndex(5f)
            )

            if (showQuickTools) {
                QuickToolsDialog(onDismiss = { showQuickTools = false })
            }

            state.selectedCoreInfo?.let { coreInfo ->
                CoreDetailDialogOverlay(
                    info = coreInfo,
                    onDismiss = { viewModel.dismissCoreDetail() },
                    onSettings = {
                        viewModel.dismissCoreDetail()
                        ZkmTabNavigator.openSocTab(navController, SoCTab.CpuGpu)
                    },
                    hazeState = hazeState
                )
            }
        }
        
        if (showNameDialog) {
            BlurredAlertDialog(
                onDismissRequest = { showNameDialog = false },
                title = { 
                    Text(
                        text = "Edit Profile Name", 
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                text = {
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        label = { Text(stringResource(R.string.lp_your_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            userName = tempName
                            sharedPrefs.edit().putString("user_name", userName).apply()
                            showNameDialog = false
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) { 
                        Text(stringResource(R.string.lp_save), fontWeight = FontWeight.Bold) 
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNameDialog = false }) { 
                        Text(stringResource(R.string.lp_cancel_77dfd2)) 
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}
