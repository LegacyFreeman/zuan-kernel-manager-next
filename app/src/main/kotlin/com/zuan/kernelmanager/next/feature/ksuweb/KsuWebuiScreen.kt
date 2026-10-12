/*
 * Original code from: 5ec1cff (KsuWebUIStandalone)
 * Modified and integrated by: Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(
    ExperimentalMaterial3Api::class, 
    ExperimentalHazeMaterialsApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)

package com.zuan.kernelmanager.next.feature.ksuweb
import com.zuan.kernelmanager.next.ui.components.zkmLiteAlpha
import com.zuan.kernelmanager.next.ui.components.ConfigureSheetWindowBlur
import com.zuan.kernelmanager.next.ui.components.zkmCardHazeEffect
import com.zuan.kernelmanager.next.ui.components.zkmCardBorder
import com.zuan.kernelmanager.next.ui.components.zkmGlassFallbackTint
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundContentColor
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundSubContentColor
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import com.zuan.kernelmanager.next.ui.components.zkmAppBarHazeStyle
import com.zuan.kernelmanager.next.feature.settings.generateThemedBackgroundColor

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.webkit.WebViewAssetLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.topjohnwu.superuser.ipc.RootService
import com.topjohnwu.superuser.nio.FileSystemManager
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.ui.navigation.NavigationRoute
import com.zuan.kernelmanager.next.feature.help.ZkmHelpButton
import com.zuan.kernelmanager.next.ui.components.VideoWallpaperPlayer
import com.zuan.kernelmanager.next.ui.components.WeatherEffectOverlay
import com.zuan.kernelmanager.next.feature.ksuweb.core.FileSystemService
import com.zuan.kernelmanager.next.feature.ksuweb.core.RemoteFsPathHandler
import com.zuan.kernelmanager.next.feature.ksuweb.core.WebViewInterface
import com.zuan.kernelmanager.next.feature.settings.BgType
import com.zuan.kernelmanager.next.feature.settings.SettingsViewModel
import com.zuan.kernelmanager.next.feature.settings.WeatherEffect
import com.zuan.kernelmanager.next.ui.theme.ThemeMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import com.zuan.kernelmanager.next.ui.components.zkmHazeEffect
import com.zuan.kernelmanager.next.ui.components.zkmHazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.sin
import com.zuan.kernelmanager.next.ui.components.ZkmHazeStyleCache
import com.zuan.kernelmanager.next.ui.components.ZkmSwitch

// --- ENUMS & DATA ---

enum class CardStyle(val icon: ImageVector) {
    Immersive(Icons.Default.Image),
    Minimalist(Icons.Default.ViewStream),
    Media(Icons.Default.ViewAgenda),
    Outlined(Icons.Default.GridView)
}

@Composable
fun getCardStyleLabel(style: CardStyle): String {
    return when (style) {
        CardStyle.Immersive -> stringResource(R.string.card_style_immersive)
        CardStyle.Minimalist -> stringResource(R.string.card_style_minimalist)
        CardStyle.Media -> stringResource(R.string.card_style_media)
        CardStyle.Outlined -> stringResource(R.string.card_style_outlined)
    }
}

data class KsuModule(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val dirPath: String,
    val banner: String,
    val enabled: Boolean,
    val hasActionScript: Boolean
)

private val ExpressiveSpringSpec = spring<Float>(dampingRatio = 0.38f, stiffness = 700f)



@Composable
fun KsuWebuiScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // --- WALLPAPER STYLE SETTINGS COLLECTION ---
    val bgType by settingsViewModel.bgType.collectAsStateWithLifecycle()
    val bgUriString by settingsViewModel.backgroundImageUri.collectAsStateWithLifecycle()
    val isVideo by settingsViewModel.isVideoWallpaper.collectAsStateWithLifecycle()
    val isBgBlur by settingsViewModel.isBgBlur.collectAsStateWithLifecycle()
    val blurStrength by settingsViewModel.blurStrength.collectAsStateWithLifecycle()
    val bgSaturation by settingsViewModel.bgSaturation.collectAsStateWithLifecycle()
    val bgContrast by settingsViewModel.bgContrast.collectAsStateWithLifecycle()
    
    // Weather Effects
    val weatherEffect by settingsViewModel.weatherEffect.collectAsStateWithLifecycle()
    val weatherIntensity by settingsViewModel.weatherIntensity.collectAsStateWithLifecycle()
    
    // Card Style
    val isHazeEnabled by settingsViewModel.isHazeEnabled.collectAsStateWithLifecycle()
    val cardDarkness by settingsViewModel.cardDarkness.collectAsStateWithLifecycle()
    
    // Theme Colors
    val isDynamic by settingsViewModel.isDynamicColor.collectAsStateWithLifecycle()
    val themeColorName by settingsViewModel.currentThemeColor.collectAsStateWithLifecycle()
    val isCustomColor by settingsViewModel.isCustomColor.collectAsStateWithLifecycle()
    val customPrimary by settingsViewModel.customPrimaryColor.collectAsStateWithLifecycle()

    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()

    val effectivePrimary = when {
        isDynamic -> MaterialTheme.colorScheme.primary
        isCustomColor -> Color(customPrimary)
        else -> themeColorName.primary
    }

    val useDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM_DEFAULT -> isSystemInDarkTheme()
    }
    
    val (tintedBackground, cardContainerColor) = generateThemedBackgroundColor(effectivePrimary, useDarkTheme)
    
    val isCustomBg = bgType != BgType.SYSTEM
    val isGlassActive = isHazeEnabled && isCustomBg
    
    val localHazeState = rememberHazeState()
    
    val density = LocalDensity.current
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeightDp = with(density) { headerHeightPx.toDp() }

    // Logic Data
    var fileSystemManager by remember { mutableStateOf<FileSystemManager?>(null) }
    var moduleList by remember { mutableStateOf<List<KsuModule>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedModule by remember { mutableStateOf<KsuModule?>(null) }

    // Prefs
    var currentStyle by remember { mutableStateOf(CardStyle.Immersive) }
    var sortActionFirst by remember { mutableStateOf(false) }
    var sortEnabledFirst by remember { mutableStateOf(false) }

    // Bottom Sheet
    var showBottomSheet by remember { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val sortedList = remember(moduleList, sortActionFirst, sortEnabledFirst) {
        moduleList.sortedWith(
            compareBy(
                { if (sortEnabledFirst) !it.enabled else false },
                { if (sortActionFirst) !it.hasActionScript else false },
                { it.name.lowercase() }
            )
        )
    }

    val connection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                try {
                    if (service != null) {
                        val fs = FileSystemManager.getRemote(service) 
                        fileSystemManager = fs
                        scope.launch(Dispatchers.IO) {
                            val modules = loadModules(fs)
                            moduleList = modules
                            isLoading = false
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            override fun onServiceDisconnected(name: ComponentName?) { fileSystemManager = null }
        }
    }

    LaunchedEffect(Unit) {
        val intent = Intent(context, FileSystemService::class.java)
        RootService.bind(intent, connection)
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                context.unbindService(connection)
            } catch (e: Exception) { }
        }
    }

    BackHandler(enabled = true) {
        if (showBottomSheet) showBottomSheet = false
        else if (selectedModule != null) selectedModule = null 
        else navController.popBackStack()
    }

    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val backgroundLuminance = MaterialTheme.colorScheme.background.luminance()
    
    val headerTextColor = if (isCustomBg) {
        if (backgroundLuminance > 0.5f) Color.Black else Color.White
    } else onSurfaceColor
    
    val headerSubColor = if (isCustomBg) {
        if (backgroundLuminance > 0.5f) Color.Black.copy(0.7f) else Color.White.copy(0.7f)
    } else onSurfaceVariantColor

    val cardTextColor = if (isCustomBg) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else onSurfaceColor
    val cardSubTextColor = if (isCustomBg) zkmOnCustomBackgroundSubContentColor(zkmIsDarkTheme()) else onSurfaceVariantColor

    // --- UI STRUCTURE ---
    Scaffold(
        containerColor = if (isCustomBg) Color.Transparent else tintedBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ -> 
        
        Box(modifier = Modifier.fillMaxSize()) {
            
            // --- BACKGROUND LAYER ---
            if (isCustomBg && bgUriString != null && selectedModule == null) {
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

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zkmHazeSource(state = localHazeState, zIndex = 0f)
                ) {
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
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zkmHazeSource(state = localHazeState, zIndex = 0f)
                )
            }
            
            // --- WEATHER EFFECT OVERLAY ---
            if (selectedModule == null) {
                WeatherEffectOverlay(
                    effect = weatherEffect,
                    intensity = weatherIntensity,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // LAYER 1: CONTENT
            AnimatedContent(
                targetState = isLoading,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "LoadingTransition",
                modifier = Modifier.fillMaxSize()
            ) { loading ->
                if (loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ExpressiveWavyLoader(color = effectivePrimary)
                    }
                } else if (selectedModule != null && fileSystemManager != null) {
                    KsuWebView(selectedModule!!, fileSystemManager!!, effectivePrimary) { selectedModule = null }
                } else {
                    if (sortedList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.ksuweb_no_modules), 
                                color = if (isCustomBg) {
                                    if (backgroundLuminance > 0.5f) Color.Black.copy(0.7f) else Color.White.copy(0.7f)
                                } else onSurfaceVariantColor
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .zkmHazeSource(state = localHazeState, zIndex = 1f),
                            contentPadding = PaddingValues(
                                top = headerHeightDp + 8.dp, 
                                bottom = 100.dp
                            )
                        ) {
                            items(sortedList, key = { it.id }) { module ->
                                UniversalModuleCard(
                                    module = module, 
                                    fs = fileSystemManager,
                                    style = currentStyle,
                                    isGlassActive = isGlassActive,
                                    hazeState = localHazeState,
                                    cardColor = cardContainerColor,
                                    textColor = cardTextColor,
                                    subTextColor = cardSubTextColor,
                                    onClick = { selectedModule = module }
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }

            // LAYER 2: TOP HEADER (PROGRESSIVE BLUR)
            if (selectedModule == null) {
                val headerHazeStyle = zkmAppBarHazeStyle(tintedBackground, isCustomBg)

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .onGloballyPositioned { headerHeightPx = it.size.height }
                        .zkmHazeEffect(
                            state = localHazeState,
                            style = headerHazeStyle
                        ) {
                            progressive = HazeProgressive.verticalGradient(
                                startIntensity = 1f,
                                endIntensity = 0f
                            )
                        }
                        .zIndex(2f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack, 
                                    contentDescription = stringResource(R.string.btn_cancel),
                                    tint = headerTextColor
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            Text(
                                text = stringResource(R.string.ksuweb_title),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = headerTextColor
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            ZkmHelpButton(NavigationRoute.KsuWebUI.route, tint = headerTextColor)

                            IconButton(
                                onClick = { 
                                    isLoading = true
                                    fileSystemManager?.let { fs ->
                                        scope.launch(Dispatchers.IO) {
                                            val modules = loadModules(fs)
                                            moduleList = modules
                                            isLoading = false
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Refresh, 
                                    contentDescription = stringResource(R.string.btn_refresh),
                                    tint = headerTextColor
                                )
                            }
                            IconButton(onClick = { showBottomSheet = true }) {
                                Icon(
                                    Icons.Default.MoreHoriz, 
                                    contentDescription = stringResource(R.string.btn_options),
                                    tint = headerTextColor
                                )
                            }
                        }

                        // Spacer Fading Buffer (Bumbu Rahasia)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // BOTTOM SHEET OPTIONS
            if (showBottomSheet) {
                ModalBottomSheet(
        contentWindowInsets = { com.zuan.kernelmanager.next.ui.adaptive.zkmSheetWindowInsets() },
        
                    onDismissRequest = { showBottomSheet = false },
                    sheetState = bottomSheetState,
                    containerColor = if (isCustomBg) 
                        MaterialTheme.colorScheme.surface.zkmLiteAlpha(0.95f) 
                    else 
                        MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                ) {
ConfigureSheetWindowBlur()
                    Column(
                        modifier = Modifier
                            .verticalScroll(androidx.compose.foundation.rememberScrollState())
                            .padding(24.dp)
                            .padding(bottom = 48.dp)
                    ) {
                        Text(
                            stringResource(R.string.ksuweb_card_style), 
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = onSurfaceColor
                            ), 
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        val styles = CardStyle.values()
                        styles.toList().chunked(2).forEach { rowStyles ->
                            Row(
                                modifier = Modifier.fillMaxWidth(), 
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowStyles.forEach { style ->
                                    StyleOptionCard(
                                        style = style, 
                                        isSelected = currentStyle == style, 
                                        modifier = Modifier.weight(1f), 
                                        primaryColor = effectivePrimary,
                                        isCustomBg = isCustomBg,
                                        onClick = { currentStyle = style }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isCustomBg) 0.2f else 1f)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            stringResource(R.string.ksuweb_sorting), 
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = onSurfaceColor
                            ), 
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        ExpressiveSwitchRow(
                            stringResource(R.string.ksuweb_sort_action), 
                            sortActionFirst,
                            primaryColor = effectivePrimary,
                            isCustomBg = isCustomBg,
                            onCheckedChange = { sortActionFirst = it }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ExpressiveSwitchRow(
                            stringResource(R.string.ksuweb_sort_enabled), 
                            sortEnabledFirst,
                            primaryColor = effectivePrimary,
                            isCustomBg = isCustomBg,
                            onCheckedChange = { sortEnabledFirst = it }
                        )
                    }
                }
            }
        }
    }
}

// --- SUSPEND FUNCTION UNTUK LOAD MODULES ---
suspend fun loadModules(fs: FileSystemManager): List<KsuModule> = withContext(Dispatchers.IO) {
    val mods = mutableListOf<KsuModule>()
    try {
        val modulesDir = fs.getFile("/data/adb/modules")
        val files = modulesDir.listFiles()
        if (files != null) {
            for (f in files) {
                if (!f.isDirectory || !fs.getFile(f, "webroot").isDirectory) continue
                val isEnabled = !fs.getFile(f, "disable").exists()
                val hasActionScript = fs.getFile(f, "action.sh").exists()
                var name = f.name
                var author = "?"
                var version = "?"
                var desc = ""
                var banner = ""
                try {
                    val propFile = fs.getFile(f, "module.prop")
                    if (propFile.exists()) {
                        propFile.newInputStream().bufferedReader().use { reader ->
                            reader.forEachLine { line ->
                                val parts = line.split("=", limit = 2)
                                if (parts.size == 2) {
                                    when (parts[0].trim()) {
                                        "name" -> name = parts[1].trim()
                                        "version" -> version = parts[1].trim()
                                        "author" -> author = parts[1].trim()
                                        "description" -> desc = parts[1].trim()
                                        "banner" -> banner = parts[1].trim()
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
                mods.add(
                    KsuModule(
                        id = f.name,
                        name = name,
                        version = version,
                        author = author,
                        description = desc,
                        dirPath = f.absolutePath,
                        banner = banner,
                        enabled = isEnabled,
                        hasActionScript = hasActionScript
                    )
                )
            }
        }
    } catch (e: Exception) { e.printStackTrace() }
    mods
}

// --- CARD SYSTEM ---

@Composable
fun UniversalModuleCard(
    module: KsuModule, 
    fs: FileSystemManager?, 
    style: CardStyle,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    textColor: Color,
    subTextColor: Color,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f, 
        animationSpec = ExpressiveSpringSpec, 
        label = "Scale"
    )

    val bannerRequest by produceState<Any?>(initialValue = null, key1 = module.banner, key2 = fs) {
        if (module.banner.isEmpty()) { value = null; return@produceState }
        if (module.banner.startsWith("http", ignoreCase = true)) {
            value = module.banner
        } else if (fs != null) {
            value = withContext(Dispatchers.IO) {
                try {
                    val f = fs.getFile(module.dirPath, module.banner)
                    if (f.exists()) f.newInputStream().use { it.readBytes() } else null
                } catch (e: Exception) { null }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() }
                )
            }
    ) {
        when (style) {
            CardStyle.Immersive -> ImmersiveCard(
                module = module, 
                bannerData = bannerRequest
            )
            CardStyle.Minimalist -> MinimalistCard(
                module = module, 
                bannerData = bannerRequest,
                isGlassActive = isGlassActive,
                hazeState = hazeState,
                cardColor = cardColor,
                textColor = textColor,
                subTextColor = subTextColor
            )
            CardStyle.Media -> MediaCard(
                module = module, 
                bannerData = bannerRequest,
                isGlassActive = isGlassActive,
                hazeState = hazeState,
                cardColor = cardColor,
                textColor = textColor,
                subTextColor = subTextColor
            )
            CardStyle.Outlined -> OutlinedCard(
                module = module, 
                bannerData = bannerRequest,
                isGlassActive = isGlassActive,
                hazeState = hazeState,
                cardColor = cardColor,
                textColor = textColor,
                subTextColor = subTextColor
            )
        }
    }
}

@Composable
fun ImmersiveCard(
    module: KsuModule, 
    bannerData: Any?
) {
    val shape = RoundedCornerShape(32.dp)
    
    Box(
        modifier = Modifier
            .height(240.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        BannerImage(data = bannerData, modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)), 
                        startY = 100f
                    )
                )
        )
        
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(), 
                horizontalArrangement = Arrangement.SpaceBetween
            ) { 
                StatusBadge(module, isGlass = true) 
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                module.name, 
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black), 
                color = Color.White, 
                modifier = Modifier.basicMarquee()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassTag(stringResource(R.string.ksuweb_by_author, module.author))
                Spacer(modifier = Modifier.width(8.dp))
                GlassTag(stringResource(R.string.ksuweb_version_prefix, module.version), true)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                module.description, 
                style = MaterialTheme.typography.bodySmall, 
                color = Color.White.copy(0.8f), 
                maxLines = 2, 
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MinimalistCard(
    module: KsuModule, 
    bannerData: Any?,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    textColor: Color,
    subTextColor: Color
) {
    val shape = RoundedCornerShape(24.dp)
    
    val glassModifier = if (isGlassActive) {
        Modifier
            .zkmCardHazeEffect(shape = shape,
                state = hazeState,
                style = ZkmHazeStyleCache.of(
                    backgroundColor = cardColor.copy(alpha = 0.3f),
                    blurRadius = 24.dp,
                    noiseFactor = 0.1f,
                    tints = emptyList()
                )
            )
            .zkmCardBorder(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.2f),
                shape = shape
            )
    } else {
        Modifier.clip(shape)
    }
    
    val containerColor = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else cardColor
    
    Surface(
        shape = shape,
        color = containerColor,
        modifier = Modifier
            .height(140.dp)
            .then(glassModifier)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp), 
                verticalArrangement = Arrangement.Center
            ) {
                StatusBadge(
                    module, 
                    isGlass = isGlassActive,
                    textColor = textColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    module.name, 
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    ), 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(R.string.ksuweb_version_author_format, module.version, module.author), 
                    style = MaterialTheme.typography.labelMedium, 
                    color = if (isGlassActive) textColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    module.description, 
                    style = MaterialTheme.typography.bodySmall, 
                    color = subTextColor,
                    maxLines = 2, 
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .fillMaxHeight()
                    .padding(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp), 
                    modifier = Modifier.fillMaxSize()
                ) { 
                    BannerImage(data = bannerData, modifier = Modifier.fillMaxSize()) 
                }
            }
        }
    }
}

@Composable
fun MediaCard(
    module: KsuModule, 
    bannerData: Any?,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    textColor: Color,
    subTextColor: Color
) {
    val shape = RoundedCornerShape(28.dp)
    
    val glassModifier = if (isGlassActive) {
        Modifier
            .zkmCardHazeEffect(shape = shape,
                state = hazeState,
                style = ZkmHazeStyleCache.of(
                    backgroundColor = cardColor.copy(alpha = 0.3f),
                    blurRadius = 24.dp,
                    noiseFactor = 0.1f,
                    tints = emptyList()
                )
            )
            .zkmCardBorder(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.2f),
                shape = shape
            )
    } else {
        Modifier.clip(shape)
    }
    
    val containerColor = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else cardColor
    
    Card(
        shape = shape, 
        colors = CardDefaults.cardColors(containerColor = containerColor), 
        modifier = Modifier
            .wrapContentHeight()
            .then(glassModifier)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .height(160.dp)
                    .fillMaxWidth()
            ) {
                BannerImage(data = bannerData, modifier = Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) { 
                    StatusBadge(module, isGlass = true) 
                }
            }
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    module.name, 
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.ksuweb_by_author, module.author), 
                    style = MaterialTheme.typography.labelLarge, 
                    color = if (isGlassActive) textColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    module.description, 
                    style = MaterialTheme.typography.bodyMedium, 
                    color = subTextColor,
                    maxLines = 3, 
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun OutlinedCard(
    module: KsuModule, 
    bannerData: Any?,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    textColor: Color,
    subTextColor: Color
) {
    val shape = RoundedCornerShape(24.dp)
    
    val glassModifier = if (isGlassActive) {
        Modifier
            .zkmCardHazeEffect(shape = shape,
                state = hazeState,
                style = ZkmHazeStyleCache.of(
                    backgroundColor = cardColor.copy(alpha = 0.3f),
                    blurRadius = 24.dp,
                    noiseFactor = 0.1f,
                    tints = emptyList()
                )
            )
            .zkmCardBorder(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.2f),
                shape = shape
            )
    } else {
        Modifier
            .clip(shape)
            .border(
                1.dp, 
                MaterialTheme.colorScheme.outlineVariant, 
                shape
            )
    }
    
    val containerColor = if (isGlassActive) zkmGlassFallbackTint(zkmIsDarkTheme()) else cardColor
    
    Surface(
        shape = shape, 
        color = containerColor, 
        modifier = Modifier
            .height(130.dp)
            .then(glassModifier)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(), 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Verified, 
                        null, 
                        modifier = Modifier.size(16.dp), 
                        tint = if (isGlassActive) textColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "WEBUI", 
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isGlassActive) textColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary
                        )
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    module.name, 
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    ), 
                    maxLines = 1
                )
                Text(
                    module.description, 
                    style = MaterialTheme.typography.bodySmall, 
                    color = subTextColor, 
                    maxLines = 2, 
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(90.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp), 
                    modifier = Modifier.fillMaxSize(), 
                    color = if (isGlassActive) Color.Black.copy(alpha = 0.3f) else MaterialTheme.colorScheme.secondaryContainer
                ) { 
                    BannerImage(data = bannerData, modifier = Modifier.fillMaxSize()) 
                }
            }
        }
    }
}

// --- HELPER COMPONENTS ---

@Composable
fun StyleOptionCard(
    style: CardStyle, 
    isSelected: Boolean, 
    modifier: Modifier = Modifier,
    primaryColor: Color,
    isCustomBg: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) primaryColor else {
        if (isCustomBg) Color.White.copy(0.3f) else MaterialTheme.colorScheme.outlineVariant
    }
    val containerColor = if (isSelected) {
        primaryColor.copy(alpha = 0.2f)
    } else {
        if (isCustomBg) Color.White.copy(0.1f) else MaterialTheme.colorScheme.surfaceContainerLow
    }
    val textColor = MaterialTheme.colorScheme.onSurface
    
    Surface(
        modifier = modifier.height(70.dp).clickable { onClick() }, 
        shape = RoundedCornerShape(16.dp), 
        color = containerColor, 
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), 
            verticalAlignment = Alignment.CenterVertically, 
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                style.icon, 
                contentDescription = null, 
                tint = textColor
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    getCardStyleLabel(style), 
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                )
                if (isSelected) Text(
                    stringResource(R.string.ksuweb_status_active), 
                    style = MaterialTheme.typography.labelSmall, 
                    color = primaryColor
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (isSelected) Icon(
                Icons.Default.Check, 
                null, 
                tint = primaryColor, 
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun BannerImage(data: Any?, modifier: Modifier) {
    if (data != null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(data).crossfade(true).build(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            Icon(
                Icons.Default.Image, 
                null, 
                modifier = Modifier.align(Alignment.Center), 
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.5f)
            )
        }
    }
}

@Composable
fun StatusBadge(
    module: KsuModule, 
    isGlass: Boolean = true,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val color = when {
        module.hasActionScript -> Color(0xFFFFD54F)
        !module.enabled -> Color(0xFFEF5350)
        else -> Color(0xFF81D4FA)
    }
    val text = when {
        module.hasActionScript -> stringResource(R.string.ksuweb_badge_action)
        !module.enabled -> stringResource(R.string.ksuweb_badge_disabled)
        else -> stringResource(R.string.ksuweb_badge_webui)
    }
    
    if (isGlass) {
        GlassPill(text, color)
    } else {
        Surface(
            color = color.copy(alpha = 0.2f), 
            shape = CircleShape
        ) { 
            Text(
                text, 
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), 
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), 
                color = textColor
            ) 
        }
    }
}

@Composable
fun GlassPill(text: String, color: Color) {
    Surface(
        color = Color.Black.copy(alpha = 0.4f), 
        shape = CircleShape, 
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically, 
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text, 
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), 
                color = Color.White
            )
        }
    }
}

@Composable
fun GlassTag(text: String, isBold: Boolean = false) {
    Surface(
        color = Color.White.copy(0.15f), 
        shape = RoundedCornerShape(8.dp)
    ) { 
        Text(
            text, 
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
            ), 
            color = Color.White.copy(0.9f), 
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) 
    }
}

@Composable
fun ExpressiveSwitchRow(
    label: String, 
    checked: Boolean,
    primaryColor: Color,
    isCustomBg: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 12.dp), 
        horizontalArrangement = Arrangement.SpaceBetween, 
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label, 
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        )
        ZkmSwitch(
            checked = checked, 
            onCheckedChange = null, 
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary, 
                checkedTrackColor = primaryColor
            )
        )
    }
}

@Composable
fun ExpressiveWavyLoader(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f, 
        targetValue = 360f, 
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing)
        ), 
        label = "angle"
    )
    
    Canvas(modifier = Modifier.size(60.dp)) {
        val radius = size.minDimension / 2
        val path = Path()
        val steps = 100
        for (i in 0..steps) {
            val theta = (i.toFloat() / steps) * 2 * Math.PI
            val r = radius + 8.dp.toPx() * sin(theta * 3 + Math.toRadians(angle.toDouble())).toFloat()
            val x = center.x + r * kotlin.math.cos(theta).toFloat()
            val y = center.y + r * kotlin.math.sin(theta).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(
            path = path, 
            color = color, 
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KsuWebView(
    module: KsuModule, 
    fs: FileSystemManager, 
    primaryColor: Color,
    onBack: () -> Unit
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    
    BackHandler(enabled = true) { 
        if (webViewRef?.canGoBack() == true) webViewRef?.goBack() else onBack() 
    }
    
    AndroidView(
        modifier = Modifier.fillMaxSize().imePadding(), 
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.apply { 
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    displayZoomControls = false
                    builtInZoomControls = false
                    setSupportZoom(false) 
                }
                webChromeClient = object : android.webkit.WebChromeClient() {}
                
                val ksuInterface = WebViewInterface(context, this, module.dirPath)
                addJavascriptInterface(ksuInterface, "ksu")
                
                val webRoot = File("${module.dirPath}/webroot")
                val assetLoader = WebViewAssetLoader.Builder()
                    .setDomain("mui.kernelsu.org")
                    .addPathHandler("/", RemoteFsPathHandler(context, webRoot, fs))
                    .build()
                    
                webViewClient = object : WebViewClient() { 
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean = !isKsuWebOrigin(request?.url)

                    override fun shouldInterceptRequest(
                        view: WebView?, 
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        if (request == null) return null
                        // The `ksu` bridge can run root commands, so remote pages must never
                        // load into this WebView (navigation, iframes or scripts).
                        if (!isKsuWebOrigin(request.url)) {
                            if (KSU_BLOCK_EXTERNAL_REQUESTS || request.isForMainFrame) {
                                return blockedKsuResponse()
                            }
                            return null
                        }
                        return assetLoader.shouldInterceptRequest(request.url)
                    }
                }
                
                loadUrl("https://mui.kernelsu.org/index.html")
                webViewRef = this
            }
        }
    )
}

private const val KSU_WEB_HOST = "mui.kernelsu.org"

/** Set to false to still allow remote sub-resources (CDN fonts/scripts); top-level navigation stays blocked. */
private const val KSU_BLOCK_EXTERNAL_REQUESTS = true

private fun isKsuWebOrigin(uri: Uri?): Boolean =
    uri != null && uri.scheme == "https" && uri.host == KSU_WEB_HOST

private fun blockedKsuResponse(): WebResourceResponse = WebResourceResponse(
    "text/plain",
    "utf-8",
    403,
    "Blocked",
    emptyMap(),
    java.io.ByteArrayInputStream(ByteArray(0))
)
