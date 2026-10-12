/*
 * Original code from: libxzr (HorizonKernelFlasher) and capntrips (KernelFlasher)
 * Modified and integrated by: Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.compose.ui.unit.ExperimentalUnitApi::class,
    kotlinx.serialization.ExperimentalSerializationApi::class
)

package com.zuan.kernelmanager.next.feature.kernel.flasher

import com.zuan.kernelmanager.next.ui.adaptive.zkmLargeAppBarExpandedHeight
import com.zuan.kernelmanager.next.ui.components.zkmAppBarHazeStyle
import com.zuan.kernelmanager.next.ui.components.zkmCardHazeEffect
import com.zuan.kernelmanager.next.ui.components.zkmCardFill
import com.zuan.kernelmanager.next.ui.components.zkmCardBorder
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundContentColor
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundSubContentColor
import com.zuan.kernelmanager.next.ui.components.zkmCustomBackgroundCardColor
import com.zuan.kernelmanager.next.ui.components.zkmGlassFallbackTint
import com.zuan.kernelmanager.next.ui.components.zkmGlassTint
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import androidx.compose.ui.zIndex
import com.zuan.kernelmanager.next.ui.components.BlurredAlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

// --- IMPORT COIL & SETTINGS ---
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.ui.navigation.NavigationRoute
import com.zuan.kernelmanager.next.feature.help.ZkmHelpButton
import com.zuan.kernelmanager.next.feature.settings.BgType
import com.zuan.kernelmanager.next.feature.settings.SettingsViewModel
import com.zuan.kernelmanager.next.ui.components.VideoWallpaperPlayer
import com.zuan.kernelmanager.next.ui.components.WeatherEffectOverlay

// --- IMPORT ZUAN UTILS ---
import com.zuan.kernelmanager.next.library.device.kernel.KernelInfoUtils
import com.zuan.kernelmanager.next.library.device.kernel.BootInfo
import com.zuan.kernelmanager.next.library.shell.su
import com.zuan.kernelmanager.next.library.shell.suBlocking

// --- IMPORT HAZE ---
import com.zuan.kernelmanager.next.ui.components.zkmHazeEffect
import com.zuan.kernelmanager.next.ui.components.zkmHazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

// --- IMPORT MODULE CAPNTRIPS ---
import com.github.capntrips.kernelflasher.FilesystemService
import com.github.capntrips.kernelflasher.IFilesystemService
import com.github.capntrips.kernelflasher.ui.screens.main.MainContent
import com.github.capntrips.kernelflasher.ui.screens.main.MainViewModel
import com.github.capntrips.kernelflasher.ui.screens.backups.BackupsContent
import com.github.capntrips.kernelflasher.ui.screens.slot.SlotContent
import com.github.capntrips.kernelflasher.ui.screens.slot.SlotFlashContent
import com.github.capntrips.kernelflasher.ui.screens.backups.SlotBackupsContent

// --- IMPORT LIBSU ---
import com.topjohnwu.superuser.ipc.RootService
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File 
import java.io.InputStream
import com.zuan.kernelmanager.next.ui.components.ZkmHazeStyleCache

enum class FlasherMode { HORIZON, CAPNTRIPS }

@Composable
fun generateFlasherBackgroundColors(isDark: Boolean, primaryColor: Color): Pair<Color, Color> = com.zuan.kernelmanager.next.ui.components.ZkmScreenPalette.colors(isDark, primaryColor)

// =========================================================================
// [FIXED] FUNGSI SMART EXTRACT: DIUBAH JADI SUSPEND BUAT MANGGIL SU()
// =========================================================================
private fun shellQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"

private suspend fun setupFlashTools(context: Context): String {
    // Map: Nama target di folder files -> Kemungkinan nama di assets
    val toolsMap = mapOf(
        "httools_static" to listOf("httools_static", "libhttools_static", "libhttools_static.so"),
        "lptools_static" to listOf("lptools_static", "liblptools_static", "liblptools_static.so"),
        "magiskboot"     to listOf("magiskboot", "libmagiskboot", "libmagiskboot.so"),
        "flash_ak3.sh"   to listOf("flash_ak3.sh")
    )
    
    var status = "Ready"
    
    try {
        val filesDir = context.filesDir
        if (!filesDir.exists()) filesDir.mkdirs()

        // These tools are bundled as assets, not JNI libraries. Select the
        // correct executable for the device ABI so 32-bit devices keep working.
        val abi = Build.SUPPORTED_ABIS.firstOrNull { it == "arm64-v8a" || it == "armeabi-v7a" } ?: "arm64-v8a"

        for ((targetName, possibleAssetNames) in toolsMap) {
            val destFile = File(filesDir, targetName)

            // Cek apakah perlu copy (jika belum ada atau size 0)
            if (!destFile.exists() || destFile.length() == 0L) {
                var copied = false

                // ABI-specific assets are preferred. Legacy root-level assets
                // remain as a fallback for older builds/upgrades.
                val abiAssetNames = if (targetName == "flash_ak3.sh") {
                    possibleAssetNames
                } else {
                    possibleAssetNames.flatMap { name ->
                        listOf("$abi/$name", name)
                    }
                }

                // Coba cari aset satu per satu (termasuk nama legacy 'lib...').
                for (assetName in abiAssetNames) {
                    try {
                        context.assets.open(assetName).use { inputStream ->
                            destFile.outputStream().use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        copied = true
                        break // Berhasil copy, lanjut ke file berikutnya
                    } catch (e: Exception) {
                        // Lanjut coba nama aset berikutnya
                    }
                }
                
                if (copied) {
                    // Set Permission Eksekusi menggunakan su() yang baru
                    if (!destFile.setExecutable(true, false)) {
                        su("chmod 755 ${shellQuote(destFile.absolutePath)}")
                    }
                } else {
                    // Gagal menemukan aset dengan nama apapun
                    status = "Missing asset for: $targetName"
                }
            }
        }
        
        // Final touch: Gabung command Multi-Command biar aman dan set PATH + Chmod
        su(
            "export PATH=\$PATH:${shellQuote(filesDir.absolutePath)}",
            "chmod 755 ${shellQuote(filesDir.absolutePath)}/*"
        )
        
    } catch (e: Exception) {
        status = "Error: ${e.message}"
    }
    return status
}

@Composable
fun KernelFlasherScreen(
    rootNavController: NavController,
    settingsViewModel: SettingsViewModel = viewModel()
) { 
    // --- SETTINGS COLLECTION ---
    val bgType by settingsViewModel.bgType.collectAsStateWithLifecycle()
    val bgUriString by settingsViewModel.backgroundImageUri.collectAsStateWithLifecycle()
    val isVideo by settingsViewModel.isVideoWallpaper.collectAsStateWithLifecycle()
    val isBgBlur by settingsViewModel.isBgBlur.collectAsStateWithLifecycle()
    val blurStrength by settingsViewModel.blurStrength.collectAsStateWithLifecycle()
    val bgSaturation by settingsViewModel.bgSaturation.collectAsStateWithLifecycle()
    val bgContrast by settingsViewModel.bgContrast.collectAsStateWithLifecycle()
    val weatherEffect by settingsViewModel.weatherEffect.collectAsStateWithLifecycle()
    val weatherIntensity by settingsViewModel.weatherIntensity.collectAsStateWithLifecycle()
    val isHazeEnabled by settingsViewModel.isHazeEnabled.collectAsStateWithLifecycle()
    val cardDarkness by settingsViewModel.cardDarkness.collectAsStateWithLifecycle()
    val isDynamic by settingsViewModel.isDynamicColor.collectAsStateWithLifecycle()
    val themeColorName by settingsViewModel.currentThemeColor.collectAsStateWithLifecycle()
    val isCustomColor by settingsViewModel.isCustomColor.collectAsStateWithLifecycle()
    val customPrimary by settingsViewModel.customPrimaryColor.collectAsStateWithLifecycle()
    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()

    val context = LocalContext.current
    
    val isDark = when (themeMode) {
        com.zuan.kernelmanager.next.ui.theme.ThemeMode.LIGHT -> false
        com.zuan.kernelmanager.next.ui.theme.ThemeMode.DARK -> true
        com.zuan.kernelmanager.next.ui.theme.ThemeMode.SYSTEM_DEFAULT -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    
    // Urutan sama dengan SetEdit: warna custom menang atas Dynamic Color.
    val effectivePrimary = if (isCustomColor) Color(customPrimary) else if (isDynamic) MaterialTheme.colorScheme.primary else themeColorName.primary
    
    // Warna latar + kartu memakai palet tunggal (sama dengan SetEdit / Home) di SEMUA mode,
    // termasuk Dynamic Color. Dulu Dynamic memakai surfaceContainer Material sehingga kartu
    // flasher berwarna theme dan beda dengan SetEdit.
    val (themeBgColor, themeCardColor) = generateFlasherBackgroundColors(isDark, effectivePrimary)
    val mainBackgroundColor = if (bgType != BgType.SYSTEM) Color.Transparent else themeBgColor
    
    val isCustomBg = bgType != BgType.SYSTEM
    val isGlassActive = isHazeEnabled && isCustomBg
    val finalCardColor = when {
        isGlassActive -> zkmGlassFallbackTint(zkmIsDarkTheme())
        isCustomBg -> zkmCustomBackgroundCardColor(zkmIsDarkTheme(), cardDarkness)
        else -> themeCardColor
    }
    val contentColor = when {
        isCustomBg -> zkmOnCustomBackgroundContentColor(zkmIsDarkTheme())
        isDynamic -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface
    }

    var currentMode by remember { mutableStateOf(FlasherMode.CAPNTRIPS) }
    val localHazeState = rememberHazeState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val density = LocalDensity.current
    var headerHeightPx by remember { mutableIntStateOf(with(density) { 160.dp.roundToPx() }) }
    val headerHeightDp = with(density) { headerHeightPx.toDp() }

    // =========================================================================
    // [FIXED] LOGIC LOADING: CEGAH RACE CONDITION & MIGRASI SHELL
    // =========================================================================
    var fsManager by remember { mutableStateOf<FileSystemManager?>(null) }
    var extractionStatus by remember { mutableStateOf(context.getString(R.string.flasher_init)) }
    var mainViewModel by remember { mutableStateOf<MainViewModel?>(null) } 

    // 1. Bind Service
    DisposableEffect(Unit) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                try {
                    val serviceInterface = IFilesystemService.Stub.asInterface(service)
                    fsManager = FileSystemManager.getRemote(serviceInterface.getFileSystemService())
                } catch (e: Exception) { android.util.Log.e("KernelFlasher", "Operation failed", e) }
            }
            override fun onServiceDisconnected(name: ComponentName?) { fsManager = null }
        }
        val intent = Intent(context, FilesystemService::class.java)
        RootService.bind(intent, connection)
        onDispose {
            try {
                RootService.unbind(connection)
            } catch (e: Exception) {
                android.util.Log.w("KernelFlasher", "Service was already unbound", e)
            }
        }
    }

    // 2. Eksekusi Copy File (Jalan Paralel)
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            extractionStatus = context.getString(R.string.flasher_checking_tools)
            // Jalankan fungsi smart extract
            val result = setupFlashTools(context)
            // Migrasi dari Shell.cmd().exec() ke su()
            su("cd ${shellQuote(context.filesDir.absolutePath)}")
            extractionStatus = result
        }
    }

    // 3. Init ViewModel (WAIT FOR EXTRACTION TO BE READY)
    LaunchedEffect(fsManager, extractionStatus) {
        val currentFsManager = fsManager
        if (currentFsManager != null && extractionStatus == "Ready" && mainViewModel == null) {
            withContext(Dispatchers.IO) {
                val app = context.applicationContext
                val vm = MainViewModel(app, currentFsManager, rootNavController)
                withContext(Dispatchers.Main) {
                    mainViewModel = vm 
                }
            }
        }
    }
    
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize().background(mainBackgroundColor)) {
            
            // BACKGROUND LAYERS
            if (isCustomBg && bgUriString != null) {
                val blurModifier = if (isBgBlur && blurStrength > 0f) Modifier.blur(blurStrength.dp) else if (isBgBlur) Modifier.blur(20.dp) else Modifier
                val saturationMatrix = ColorMatrix().apply { setToSaturation(bgSaturation) }
                val colorFilter = ColorFilter.colorMatrix(saturationMatrix)
                val uri = Uri.parse(bgUriString)
                Box(modifier = Modifier.fillMaxSize().zkmHazeSource(state = localHazeState, zIndex = 0f)) {
                    if (isVideo) {
                        VideoWallpaperPlayer(uri = uri, modifier = Modifier.fillMaxSize().then(blurModifier))
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
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = bgContrast)))
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize().zkmHazeSource(state = localHazeState, zIndex = 0f))
            }

            WeatherEffectOverlay(effect = weatherEffect, intensity = weatherIntensity, modifier = Modifier.fillMaxSize())

            // CONTENT SWITCHER
            AnimatedContent(
                targetState = currentMode,
                label = "FlasherModeSwitch",
                modifier = Modifier.fillMaxSize().zkmHazeSource(state = localHazeState, zIndex = 1f)
            ) { mode ->
                when (mode) {
                    FlasherMode.HORIZON -> {
                        HorizonFlasherContent(
                            navController = rootNavController, 
                            headerPadding = headerHeightDp,
                            isGlassActive = isGlassActive,
                            hazeState = localHazeState,
                            cardColor = finalCardColor,
                            contentColor = contentColor,
                            primaryColor = effectivePrimary
                        ) 
                    }
                    FlasherMode.CAPNTRIPS -> {
                        if (mainViewModel != null) {
                            mainViewModel?.let { vm ->
                                // Mode Android Liquid Glass (Kyant): kirim backdrop + parameter
                                // dari Wallpaper Style Screen ke modul flasher (Capntrips).
                                val flasherLiquid = com.zuan.kernelmanager.next.ui.components
                                    .resolveLiquidGlassCardRequest(isGlassActive)
                                val flasherLiquidTint = MaterialTheme.colorScheme.surfaceContainer
                                    .copy(alpha = com.zuan.kernelmanager.next.ui.components.ZkmLiquidCardTintAlpha)
                                androidx.compose.runtime.CompositionLocalProvider(
                                    com.github.capntrips.kernelflasher.ui.components.LocalFlasherGlass provides
                                        com.github.capntrips.kernelflasher.ui.components.FlasherGlass(
                                            isActive = isGlassActive,
                                            hazeState = localHazeState,
                                            isDark = zkmIsDarkTheme(),
                                            liquidBackdrop = flasherLiquid?.first,
                                            liquidCornerRadius = flasherLiquid?.second?.cornerRadius ?: 20.dp,
                                            liquidBlurRadius = flasherLiquid?.second?.blurRadius ?: 8.dp,
                                            liquidRefractionHeight = flasherLiquid?.second?.refractionHeight ?: 12.dp,
                                            liquidRefractionAmount = flasherLiquid?.second?.refractionAmount ?: 12.dp,
                                            liquidChromaticAberration = flasherLiquid?.second?.chromaticAberration ?: false,
                                            liquidTint = flasherLiquidTint,
                                            cardColor = finalCardColor,
                                            accentColor = effectivePrimary,
                                        )
                                ) {
                                    CapntripsContainer(
                                        rootNavController = rootNavController, 
                                        headerPadding = headerHeightDp,
                                        viewModel = vm
                                    )
                                }
                            }
                        } else {
                            // Loading State
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = effectivePrimary)
                                    Spacer(Modifier.height(16.dp))
                                    
                                    val statusText = when {
                                        fsManager == null -> stringResource(R.string.flasher_connecting_root)
                                        extractionStatus != "Ready" -> "Extracting Tools..." 
                                        else -> stringResource(R.string.flasher_scanning_partitions) 
                                    }
                                    
                                    Text(statusText, color = contentColor, style = MaterialTheme.typography.bodyMedium)
                                    
                                    if (extractionStatus.startsWith("Error")) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(extractionStatus, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                    } else {
                                        Text(stringResource(R.string.please_wait), style = MaterialTheme.typography.bodySmall, color = contentColor.copy(0.6f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            val appBarHazeStyle = zkmAppBarHazeStyle(mainBackgroundColor, isCustomBg)

            LargeTopAppBar(
                expandedHeight = zkmLargeAppBarExpandedHeight(),
                title = {
                    Column {
                        Text(stringResource(R.string.kernel_flasher_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = contentColor)
                        Spacer(Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(end = 16.dp)) {
                            SegmentedButton(
                                selected = currentMode == FlasherMode.HORIZON, 
                                onClick = { currentMode = FlasherMode.HORIZON }, 
                                shape = RoundedCornerShape(12.dp, 0.dp, 0.dp, 12.dp), 
                                icon = { if(currentMode == FlasherMode.HORIZON) Icon(Icons.Default.Check, null) },
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = effectivePrimary,
                                    activeContentColor = if (effectivePrimary.luminance() > 0.5f) Color.Black else Color.White,
                                    inactiveContainerColor = if (isCustomBg) Color.White.copy(0.1f) else Color.Transparent,
                                    inactiveContentColor = contentColor
                                )
                            ) { Text(stringResource(R.string.flasher_mode_horizon)) }
                            SegmentedButton(
                                selected = currentMode == FlasherMode.CAPNTRIPS, 
                                onClick = { currentMode = FlasherMode.CAPNTRIPS }, 
                                shape = RoundedCornerShape(0.dp, 12.dp, 12.dp, 0.dp), 
                                icon = { if(currentMode == FlasherMode.CAPNTRIPS) Icon(Icons.Default.Check, null) },
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = effectivePrimary,
                                    activeContentColor = if (effectivePrimary.luminance() > 0.5f) Color.Black else Color.White,
                                    inactiveContainerColor = if (isCustomBg) Color.White.copy(0.1f) else Color.Transparent,
                                    inactiveContentColor = contentColor
                                )
                            ) { Text(stringResource(R.string.flasher_mode_capntrips)) }
                        }
                    }
                },
                navigationIcon = { 
                    IconButton(onClick = { rootNavController.popBackStack() }
        ) { 
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_back), tint = contentColor) 
                    } 
                },
                actions = {
                    ZkmHelpButton(NavigationRoute.KernelFlasher.route, tint = contentColor)
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Color.Transparent, 
                    scrolledContainerColor = Color.Transparent
                ),
                scrollBehavior = scrollBehavior,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .onGloballyPositioned { coordinates ->
                        if (kotlin.math.abs(headerHeightPx - coordinates.size.height) > 5) {
                            headerHeightPx = coordinates.size.height 
                        }
                    }
                    .zkmHazeEffect(state = localHazeState, style = appBarHazeStyle) {
                        progressive = dev.chrisbanes.haze.HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
                    }
                    .zIndex(2f)
            )
        }
    }
}

@Composable
fun CapntripsContainer(
    rootNavController: NavController, 
    headerPadding: Dp,
    viewModel: MainViewModel
) {
    val internalNavController = rememberNavController()
    val topSpacer = @Composable { Spacer(Modifier.height(headerPadding + 16.dp)) }

    NavHost(
        navController = internalNavController,
        startDestination = "main",
        modifier = Modifier.fillMaxSize()
    ) {
        
        // Error Route
        composable("error/{message}") { backStackEntry ->
            val message = backStackEntry.arguments?.getString("message") ?: stringResource(R.string.unknown_error)
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.error_occurred), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(24.dp))
                Button(onClick = { internalNavController.popBackStack("main", false) }
        ) {
                    Text(stringResource(R.string.go_back))
                }
            }
        }

        // --- MAIN SCREENS ---
        composable("main") {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                topSpacer()
                MainContent(viewModel = viewModel, navController = internalNavController)
                Spacer(Modifier.height(100.dp))
            }
        }

        composable("backups") {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                topSpacer()
                BackupsContent(viewModel = viewModel.backups, navController = internalNavController)
                Spacer(Modifier.height(100.dp))
            }
        }
         composable("backups/{backupId}") { backStackEntry ->
            val backupId = backStackEntry.arguments?.getString("backupId")
            viewModel.backups.currentBackup = backupId
             Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                topSpacer()
                BackupsContent(viewModel = viewModel.backups, navController = internalNavController)
                Spacer(Modifier.height(100.dp))
             }
        }

        // --- SLOT A ---
        composable("slot_a") {
            val slotVM = viewModel.slotA
            if (slotVM != null) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    topSpacer()
                    SlotContent(viewModel = slotVM, slotSuffix = "_a", navController = internalNavController)
                    Spacer(Modifier.height(100.dp))
                }
            } else ErrorScreen(stringResource(R.string.slot_a_not_found), headerPadding)
        }

        val slotARoutes = listOf(
            "slot_a/flash", "slot_a/flash/ak3", "slot_a/flash/image", 
            "slot_a/flash/image/flash", "slot_a/backup", "slot_a/backup/backup"
        )
        slotARoutes.forEach { route ->
            composable(route) { HandleSlotFlash(viewModel.slotA, "_a", internalNavController, headerPadding) }
        }

        val slotABackupsRoutes = listOf(
            "slot_a/backups", "slot_a/backups/{backupId}", "slot_a/backups/{backupId}/restore",
            "slot_a/backups/{backupId}/restore/restore", "slot_a/backups/{backupId}/flash/ak3"
        )
        slotABackupsRoutes.forEach { route ->
            composable(route) { backStackEntry ->
                if(route.contains("{backupId}")) viewModel.backups.currentBackup = backStackEntry.arguments?.getString("backupId")
                HandleSlotBackups(viewModel.slotA, viewModel.backups, "_a", internalNavController, headerPadding)
            }
        }

        // --- SLOT B ---
        composable("slot_b") {
            val slotVM = viewModel.slotB
            if (slotVM != null) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    topSpacer()
                    SlotContent(viewModel = slotVM, slotSuffix = "_b", navController = internalNavController)
                    Spacer(Modifier.height(100.dp))
                }
            } else ErrorScreen(stringResource(R.string.slot_b_not_available), headerPadding)
        }

        val slotBRoutes = listOf(
            "slot_b/flash", "slot_b/flash/ak3", "slot_b/flash/image", 
            "slot_b/flash/image/flash", "slot_b/backup", "slot_b/backup/backup"
        )
        slotBRoutes.forEach { route ->
            composable(route) { HandleSlotFlash(viewModel.slotB, "_b", internalNavController, headerPadding) }
        }
        
        val slotBBackupsRoutes = listOf(
            "slot_b/backups", "slot_b/backups/{backupId}", "slot_b/backups/{backupId}/restore",
            "slot_b/backups/{backupId}/restore/restore", "slot_b/backups/{backupId}/flash/ak3"
        )
        slotBBackupsRoutes.forEach { route ->
            composable(route) { backStackEntry ->
                if (route.contains("{backupId}")) viewModel.backups.currentBackup = backStackEntry.arguments?.getString("backupId")
                HandleSlotBackups(viewModel.slotB, viewModel.backups, "_b", internalNavController, headerPadding) 
            }
        }

        // --- REBOOT ---
        // [FIXED] Migrasi Shell.cmd ke suBlocking agar sinkron dengan utils baru
        composable("reboot") {
            Box(Modifier.fillMaxSize().padding(top=headerPadding), contentAlignment = Alignment.Center) {
                Button(onClick = { suBlocking("reboot") }
        ) { Text(stringResource(R.string.confirm_reboot_system)) }
            }
        }
    }
}

// ==========================================
// 3. HORIZON CONTENT (UPDATED FOR STYLING)
// ==========================================

@Composable
fun HorizonFlasherContent(
    navController: NavController, 
    headerPadding: Dp,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    contentColor: Color,
    primaryColor: Color
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var logs by remember { mutableStateOf("") }
    var isFlashing by remember { mutableStateOf(false) }
    var showRebootDialog by remember { mutableStateOf(false) }

    val activeSlot by produceState(initialValue = stringResource(R.string.loading)) { value = KernelInfoUtils.getActiveSlot() }
    val deviceModel = remember { KernelInfoUtils.getDeviceModel() }
    val kernelVersion = remember { KernelInfoUtils.getKernelVersion() }
    val bootInfoA by produceState(initialValue = BootInfo(stringResource(R.string.scanning), "...", "...")) { value = KernelInfoUtils.getBootInfo("a") }
    val bootInfoB by produceState(initialValue = BootInfo(stringResource(R.string.scanning), "...", "...")) { value = KernelInfoUtils.getBootInfo("b") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isFlashing = true
            // Log Batching (ZKM Perf - Adopsi ACSIDE Compiler Stream):
            // Mencegah Recomposition Storm saat script unpacking/flashing mencetak ratusan log.
            val logBatcher = com.zuan.kernelmanager.next.core.util.ZkmLogBatcher(scope, intervalMs = 150L) { batchedText ->
                logs = batchedText
            }
            logBatcher.append(context.getString(R.string.flasher_init_process) + "\nTarget: $uri")
            scope.launch {
                val worker = com.zuan.kernelmanager.next.feature.kernel.flasher.FlasherWorker(context, uri) { newLog ->
                    logBatcher.append(newLog)
                }
                val success = worker.startFlashing()
                isFlashing = false
                if (success) {
                    logBatcher.append("\n" + context.getString(R.string.flasher_success))
                    logBatcher.flush()
                    showRebootDialog = true
                } else {
                    logBatcher.append("\n" + context.getString(R.string.flasher_failed))
                    logBatcher.flush()
                    Toast.makeText(context, context.getString(R.string.flasher_toast_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (showRebootDialog) {
        BlurredAlertDialog(
            onDismissRequest = { },
            icon = { Icon(Icons.Default.CheckCircle, null, tint = primaryColor) },
            title = { Text(stringResource(R.string.flashing_complete)) },
            text = { Text(stringResource(R.string.flasher_reboot_message)) },
            confirmButton = { Button(onClick = { com.zuan.kernelmanager.next.feature.kernel.flasher.FlasherWorker.rebootDevice() }, colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
        ) { Text(stringResource(R.string.reboot_system)) } },
            dismissButton = { TextButton(onClick = { showRebootDialog = false }, colors = ButtonDefaults.textButtonColors(contentColor = primaryColor)
        ) { Text(stringResource(R.string.later)) } }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = headerPadding + 16.dp, bottom = 100.dp) 
    ) {
        item {
            SectionTitle(stringResource(R.string.horizon_engine_legacy), primaryColor)
            StyledCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                isGlassActive = isGlassActive,
                hazeState = hazeState,
                cardColor = cardColor
            ) {
                Column {
                    PixelListItem(Icons.Default.PhoneAndroid, stringResource(R.string.device_model), deviceModel, contentColor)
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = contentColor.copy(0.1f))
                    PixelListItem(Icons.Default.Memory, stringResource(R.string.kernel_version), kernelVersion, contentColor)
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = contentColor.copy(0.1f))
                    PixelListItem(Icons.Default.SystemUpdate, stringResource(R.string.active_slot), stringResource(R.string.slot_current, activeSlot.uppercase()), primaryColor)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        item {
            SectionTitle(stringResource(R.string.boot_partitions), primaryColor)
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val isSlotA = activeSlot.contains("A", ignoreCase = true)
                val isSlotB = activeSlot.contains("B", ignoreCase = true)
                Box(Modifier.weight(1f)) { SlotStatusCard(stringResource(R.string.slot_a), isSlotA, bootInfoA, cardColor, contentColor, primaryColor, isGlassActive, hazeState) }
                Box(Modifier.weight(1f)) { SlotStatusCard(stringResource(R.string.slot_b), isSlotB, bootInfoB, cardColor, contentColor, primaryColor, isGlassActive, hazeState) }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        item {
            SectionTitle(stringResource(R.string.flasher_console), primaryColor)
            StyledCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 150.dp, max = 300.dp),
                isGlassActive = isGlassActive,
                hazeState = hazeState,
                // Console selalu gelap (terminal look), tapi saat glass aktif tetap
                // butuh scrim supaya tidak jadi card "telanjang" transparan.
                cardColor = if(isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else Color(0xFF1E1E1E).copy(alpha=0.8f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Code, null, tint = contentColor.copy(0.6f), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.terminal_output), color = contentColor.copy(0.6f), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = contentColor.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = if (logs.isEmpty()) stringResource(R.string.horizon_ready) else logs, color = Color(0xFF00E676), fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { if (!isFlashing) filePickerLauncher.launch("*/*") },
                enabled = !isFlashing,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = if (primaryColor.luminance() > 0.5f) Color.Black else Color.White)
        ) {
                if (isFlashing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.flashing_kernel_progress), style = MaterialTheme.typography.titleMedium)
                } else {
                    Icon(Icons.Default.Bolt, null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.flash_horizon_method), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}


// ==========================================
// 4. HELPER COMPOSABLES
// ==========================================

@Composable
fun StyledCard(
    modifier: Modifier = Modifier,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    
    val glassModifier = if (isGlassActive) {
        Modifier.zkmCardHazeEffect(shape = shape,
                state = hazeState, 
                style = ZkmHazeStyleCache.of(
                    backgroundColor = cardColor.copy(alpha = 0.5f), 
                    blurRadius = 24.dp, 
                    noiseFactor = 0.1f,
                    tints = emptyList()
                )
            )
            .zkmCardBorder(1.dp, Color.White.copy(0.1f), shape)
    } else {
        Modifier.clip(shape)
    }

    Card(
        modifier = modifier.then(glassModifier),
        shape = shape,
        // Saat glass aktif jangan transparan penuh: pakai fallback tint supaya
        // card tetap terbaca walau blur haze meleset / tidak menutup area card.
        colors = CardDefaults.cardColors(containerColor = if(isGlassActive) zkmCardFill(zkmGlassTint(cardColor.copy(alpha = 1f), zkmIsDarkTheme(), 0.35f)) else cardColor)
    ) {
        content()
    }
}

@Composable
fun HandleSlotFlash(slotVM: com.github.capntrips.kernelflasher.ui.screens.slot.SlotViewModel?, suffix: String, navController: NavController, headerPadding: Dp) {
    if (slotVM != null) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(headerPadding + 16.dp)) 
            SlotFlashContent(viewModel = slotVM, slotSuffix = suffix, navController = navController)
            Spacer(Modifier.height(100.dp))
        }
    } else ErrorScreen(stringResource(R.string.slot_vm_not_found), headerPadding)
}

@Composable
fun HandleSlotBackups(
    slotVM: com.github.capntrips.kernelflasher.ui.screens.slot.SlotViewModel?, 
    backupsVM: com.github.capntrips.kernelflasher.ui.screens.backups.BackupsViewModel,
    suffix: String, 
    navController: NavController,
    headerPadding: Dp
) {
    if (slotVM != null) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(headerPadding + 16.dp)) 
            SlotBackupsContent(slotViewModel = slotVM, backupsViewModel = backupsVM, slotSuffix = suffix, navController = navController)
            Spacer(Modifier.height(100.dp))
        }
    } else ErrorScreen(stringResource(R.string.slot_vm_not_found), headerPadding)
}

@Composable
fun ErrorScreen(msg: String, topPadding: Dp) {
    Box(Modifier.fillMaxSize().padding(top=topPadding + 32.dp), contentAlignment = Alignment.Center) {
        Text(msg)
    }
}

@Composable
fun SectionTitle(title: String, color: Color) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 32.dp, bottom = 8.dp))
}

@Composable
fun PixelListItem(icon: ImageVector, title: String, value: String, contentColor: Color) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = contentColor) },
        supportingContent = { Text(value, style = MaterialTheme.typography.bodySmall, color = contentColor.copy(0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { Icon(icon, null, tint = contentColor.copy(0.7f)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun SlotStatusCard(
    slotName: String, 
    isActive: Boolean, 
    data: BootInfo, 
    cardColor: Color, 
    contentColor: Color, 
    primaryColor: Color, 
    isGlassActive: Boolean, 
    hazeState: HazeState
) {
    val bgColor = if (isActive) primaryColor else MaterialTheme.colorScheme.surfaceContainerHighest
    
    val textColor = if (isActive) (if(primaryColor.luminance() > 0.5f) Color.Black else Color.White) else contentColor
    
    val shape = RoundedCornerShape(20.dp)
    
    val glassModifier = if (isGlassActive) {
        Modifier.zkmCardHazeEffect(shape = shape,
                state = hazeState, 
                style = ZkmHazeStyleCache.of(
                    backgroundColor = bgColor, 
                    blurRadius = 15.dp,
                    tints = emptyList()
                )
            )
            .zkmCardBorder(1.dp, Color.White.copy(0.1f), shape)
    } else {
        Modifier.clip(shape)
    }

    Card(
        modifier = Modifier.fillMaxWidth().then(glassModifier),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = if(isGlassActive) zkmGlassTint(bgColor, zkmIsDarkTheme(), if (isActive) (if (com.zuan.kernelmanager.next.ui.components.ZkmLiquidFlags.cardsLiquid) 1.0f else 0.70f) else 0.40f) else bgColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Icon(if (isActive) Icons.Default.CheckCircle else Icons.Outlined.SdStorage, null, tint = textColor, modifier = Modifier.size(20.dp))
                if (isActive) Text(stringResource(R.string.active_label).uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = textColor)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(slotName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = textColor)
            Spacer(modifier = Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("SHA1: ${data.sha1.take(8)}...", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = textColor.copy(alpha = 0.7f))
                Text(stringResource(R.string.format_label, data.format), style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.7f))
            }
        }
    }
}
