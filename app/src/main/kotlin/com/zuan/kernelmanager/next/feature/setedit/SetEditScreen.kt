/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class, ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.zuan.kernelmanager.next.feature.setedit

import com.zuan.kernelmanager.next.ui.components.zkmLiteAlpha
import com.zuan.kernelmanager.next.ui.components.ConfigureSheetWindowBlur
import com.zuan.kernelmanager.next.ui.components.zkmCardHazeEffect
import com.zuan.kernelmanager.next.ui.components.zkmCardFill
import com.zuan.kernelmanager.next.ui.components.zkmReferenceGlass
import com.zuan.kernelmanager.next.ui.components.zkmReferenceGlassFill
import com.zuan.kernelmanager.next.ui.components.zkmCardBorder
import com.zuan.kernelmanager.next.ui.components.zkmAppBarHazeStyle
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundContentColor
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundSubContentColor
import com.zuan.kernelmanager.next.ui.components.zkmCustomBackgroundCardColor
import com.zuan.kernelmanager.next.ui.components.zkmGlassFallbackTint
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import com.zuan.kernelmanager.next.ui.components.BlurredAlertDialog
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SettingsSystemDaydream
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.zuan.kernelmanager.next.library.shell.su
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.ui.navigation.NavigationRoute
import com.zuan.kernelmanager.next.feature.help.ZkmHelpButton
import com.zuan.kernelmanager.next.ui.components.VideoWallpaperPlayer
import com.zuan.kernelmanager.next.ui.components.WeatherEffectOverlay
import com.zuan.kernelmanager.next.feature.settings.BgType
import com.zuan.kernelmanager.next.feature.settings.SettingsViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import com.zuan.kernelmanager.next.ui.components.zkmHazeEffect
import com.zuan.kernelmanager.next.ui.components.zkmHazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.zuan.kernelmanager.next.ui.components.ZkmHazeStyleCache

private fun shellQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"

enum class PropCategory(val titleRes: Int, val command: String, val icon: ImageVector) {
    All(R.string.setedit_tab_all, "ALL", Icons.Rounded.Dns),
    Global(R.string.setedit_tab_global, "settings list global", Icons.Rounded.Public),
    Secure(R.string.setedit_tab_secure, "settings list secure", Icons.Rounded.Lock),
    System(R.string.setedit_tab_system, "settings list system", Icons.Rounded.SettingsSystemDaydream),
    Android(R.string.setedit_tab_android, "getprop", Icons.Rounded.Android)
}

data class SetEditItem(val key: String, val value: String, val sourceCategory: PropCategory)

data class EditHistoryItem(
    val id: String = System.currentTimeMillis().toString(),
    val action: EditAction,
    val item: SetEditItem,
    val oldValue: String? = null,
    val newValue: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class EditAction {
    DELETED, MODIFIED, CREATED
}

val SENSITIVE_KEYWORDS = listOf(
    "adb_enabled", "development_settings_enabled", "install_non_market_apps",
    "enabled_input_methods", "accessibility_enabled", "location_providers_allowed",
    "assisted_gps_enabled", "mock_location", "verifier_verify_adb_installs",
    "package_verifier_enable", "backup_enabled", "auto_time", "auto_time_zone",
    "airplane_mode_on", "cell_on", "data_roaming", "usb_mass_storage_enabled"
)

fun isSensitiveItem(key: String): Boolean {
    return SENSITIVE_KEYWORDS.any { key.contains(it, ignoreCase = true) }
}

@Composable
fun generateSetEditBackgroundColors(isDark: Boolean, primaryColor: Color): Pair<Color, Color> = com.zuan.kernelmanager.next.ui.components.ZkmScreenPalette.colors(isDark, primaryColor)

@Composable
fun SetEditScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel = viewModel()
) {
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
    val colorScheme = MaterialTheme.colorScheme

    val effectivePrimary = remember(isDynamic, themeColorName, isCustomColor, customPrimary) {
        when {
            isCustomColor -> Color(customPrimary)
            isDynamic -> Color.Unspecified
            else -> themeColorName.primary
        }
    }

    val finalPrimary = if (effectivePrimary == Color.Unspecified) colorScheme.primary else effectivePrimary

    val context = LocalContext.current
    val isDark = when (themeMode) {
        com.zuan.kernelmanager.next.ui.theme.ThemeMode.LIGHT -> false
        com.zuan.kernelmanager.next.ui.theme.ThemeMode.DARK -> true
        com.zuan.kernelmanager.next.ui.theme.ThemeMode.SYSTEM_DEFAULT -> isSystemInDarkTheme()
    }
    
    val (themeBgColor, themeCardColor) = generateSetEditBackgroundColors(isDark, finalPrimary)
    
    val isCustomBg = bgType != BgType.SYSTEM
    val isGlassActive = isHazeEnabled && isCustomBg
    
    val finalCardColor = when {
        isGlassActive -> zkmGlassFallbackTint(zkmIsDarkTheme())
        isCustomBg -> zkmCustomBackgroundCardColor(zkmIsDarkTheme(), cardDarkness)
        else -> themeCardColor
    }
    
    val mainBackgroundColor = if (isCustomBg) Color.Transparent else themeBgColor

    val categories = PropCategory.values()
    val detectPageIndex = categories.size
    val pagerState = rememberPagerState(pageCount = { categories.size + 1 })
    val currentTabIndex = pagerState.currentPage
    val scope = rememberCoroutineScope()

    var propList by remember { mutableStateOf<List<SetEditItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<SetEditItem?>(null) }
    var isGridView by remember { mutableStateOf(false) }

    var editHistory by remember { mutableStateOf<List<EditHistoryItem>>(emptyList()) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showSensitiveDialog by remember { mutableStateOf(false) }
    var pendingDeleteItem by remember { mutableStateOf<SetEditItem?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val propFailedMsg = stringResource(R.string.setedit_msg_failed)
    val propStore = remember { PropDetectedStore(context) }
    var detectHasConsent by remember { mutableStateOf(propStore.hasConsent()) }
    var detectResult by remember { mutableStateOf<PropDetectResult?>(null) }
    var detectLoading by remember { mutableStateOf(false) }
    var showConsentDialog by remember { mutableStateOf(false) }
    var detectConfirm by remember { mutableStateOf<Int?>(null) } // 1 = reset baseline, 2 = hapus data

    val hazeState = rememberHazeState()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    
    val localDensity = LocalDensity.current
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeightDp = with(localDensity) { headerHeightPx.toDp() }

    var isFabExpanded by remember { mutableStateOf(false) }
    val fabRotation by animateFloatAsState(targetValue = if (isFabExpanded) 45f else 0f, animationSpec = tween(300))

    val listState = rememberLazyListState()
    var isFabVisible by remember { mutableStateOf(true) }
    var previousScrollOffset by remember { mutableIntStateOf(0) }
    
    LaunchedEffect(listState.firstVisibleItemScrollOffset) {
        val currentOffset = listState.firstVisibleItemScrollOffset
        val isScrollingDown = currentOffset > previousScrollOffset
        if (kotlin.math.abs(currentOffset - previousScrollOffset) > 50) {
            isFabVisible = !isScrollingDown || currentOffset < 100
            previousScrollOffset = currentOffset
        }
    }

    val fabScale by animateFloatAsState(targetValue = if (isFabVisible) 1f else 0f, animationSpec = tween(300), label = "fab_scale")
    val fabAlpha by animateFloatAsState(targetValue = if (isFabVisible) 1f else 0f, animationSpec = tween(300), label = "fab_alpha")

    fun runDetect() {
        scope.launch {
            detectLoading = true
            val current = propStore.readCurrentProps()
            detectResult = if (current == null) null else propStore.compare(current)
            detectLoading = false
        }
    }

    fun refreshData(index: Int) {
        if (index >= categories.size) return
        scope.launch(Dispatchers.IO) {
            isLoading = true
            val currentCategory = categories[index]
            val resultList = mutableListOf<SetEditItem>()

            suspend fun fetchAndParse(cat: PropCategory): List<SetEditItem> {
                val result = su(cat.command)
                if (!result.isSuccess) return emptyList()
                return result.out.mapNotNull { line ->
                    if (cat == PropCategory.Android) {
                        val parts = line.split("]: [")
                        if (parts.size == 2) {
                            SetEditItem(
                                key = parts[0].replace("[", "").trim(),
                                value = parts[1].replace("]", "").trim(),
                                sourceCategory = cat
                            )
                        } else null
                    } else {
                        val parts = line.split("=", limit = 2)
                        if (parts.size == 2) {
                            SetEditItem(
                                key = parts[0].trim(),
                                value = parts[1].trim(),
                                sourceCategory = cat
                            )
                        } else null
                    }
                }
            }

            if (currentCategory == PropCategory.All) {
                categories.filter { it != PropCategory.All }.forEach { cat ->
                    resultList.addAll(fetchAndParse(cat))
                }
                resultList.sortBy { it.key }
            } else {
                resultList.addAll(fetchAndParse(currentCategory))
            }

            withContext(Dispatchers.Main) {
                propList = resultList
                isLoading = false
            }
        }
    }

    LaunchedEffect(currentTabIndex) {
        if (currentTabIndex == detectPageIndex) {
            if (propStore.hasConsent()) { detectHasConsent = true; runDetect() }
            else { detectHasConsent = false; showConsentDialog = true }
        } else refreshData(currentTabIndex)
    }

    val filteredList = remember(propList, searchQuery) {
        if (searchQuery.isEmpty()) propList else propList.filter { it.key.contains(searchQuery, ignoreCase = true) || it.value.contains(searchQuery, ignoreCase = true) }
    }

    val deletedHistory = remember(editHistory) {
        editHistory.filter { it.action == EditAction.DELETED }.take(10)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark && !isCustomBg
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0.dp),
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 32.dp, end = 8.dp).graphicsLayer { val v = if (currentTabIndex == detectPageIndex) 0f else 1f; scaleX = fabScale * v; scaleY = fabScale * v; alpha = fabAlpha * v }
            ) {
                AnimatedVisibility(visible = isFabExpanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        if (deletedHistory.isNotEmpty()) {
                            FloatingActionButton(
                                onClick = { showHistorySheet = true; isFabExpanded = false },
                                containerColor = finalPrimary.copy(alpha = 0.9f),
                                modifier = Modifier.size(56.dp),
                                shape = CircleShape,
                                elevation = FloatingActionButtonDefaults.elevation(6.dp)
                            ) {
                                BadgedBox(badge = { Badge(containerColor = Color.Red, contentColor = Color.White) { Text("${deletedHistory.size}") } }) {
                                    Icon(Icons.Default.History, contentDescription = stringResource(R.string.lp_a11y_history), tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                            }
                        }

                        FloatingActionButton(
                            onClick = { showAddDialog = true; isFabExpanded = false },
                            containerColor = finalPrimary.copy(alpha = 0.9f),
                            modifier = Modifier.size(56.dp),
                            shape = CircleShape,
                            elevation = FloatingActionButtonDefaults.elevation(6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.lp_a11y_add_new), tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                }

                FloatingActionButton(
                    onClick = { isFabExpanded = !isFabExpanded },
                    containerColor = finalPrimary,
                    contentColor = if (finalPrimary.luminance() > 0.5f) Color.Black else Color.White,
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.lp_a11y_menu), modifier = Modifier.rotate(fabRotation).size(28.dp))
                }
            }
        }
    ) { innerPadding ->

        Box(modifier = Modifier.fillMaxSize().background(mainBackgroundColor)) {
            
            if (isCustomBg && bgUriString != null) {
                val blurModifier = if (isBgBlur && blurStrength > 0f) Modifier.blur(blurStrength.dp) else if (isBgBlur) Modifier.blur(20.dp) else Modifier
                val saturationMatrix = ColorMatrix().apply { setToSaturation(bgSaturation) }
                val colorFilter = ColorFilter.colorMatrix(saturationMatrix)
                val uri = Uri.parse(bgUriString)

                Box(modifier = Modifier.fillMaxSize().zkmHazeSource(state = hazeState, zIndex = 0f)) {
                    if (isVideo) {
                        VideoWallpaperPlayer(uri = uri, modifier = Modifier.fillMaxSize().then(blurModifier))
                    } else {
                        AsyncImage(model = ImageRequest.Builder(context).data(uri).build(), contentDescription = null, modifier = Modifier.fillMaxSize().then(blurModifier), contentScale = ContentScale.Crop, colorFilter = colorFilter)
                    }
                    if (bgContrast > 0f) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = bgContrast)))
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize().zkmHazeSource(state = hazeState, zIndex = 0f))
            }
            
            WeatherEffectOverlay(effect = weatherEffect, intensity = weatherIntensity, modifier = Modifier.fillMaxSize())

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding()).zkmHazeSource(state = hazeState, zIndex = 1f)) { pageIndex ->
                if (pageIndex == detectPageIndex) {
                    PropDetectedPage(
                        hasConsent = detectHasConsent,
                        isLoading = detectLoading,
                        result = detectResult,
                        query = searchQuery,
                        topPadding = headerHeightDp,
                        isGlassActive = isGlassActive,
                        hazeState = hazeState,
                        cardColor = finalCardColor,
                        primaryColor = finalPrimary,
                        onRequestConsent = { showConsentDialog = true },
                        onRescan = { runDetect() },
                        onResetBaseline = { detectConfirm = 1 },
                        onDeleteData = { detectConfirm = 2 }
                    )
                } else if (isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = finalPrimary) }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = headerHeightDp + 16.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredList) { item ->
                            SetEditItemCard(item = item, onClick = { selectedItem = item; showBottomSheet = true }, isGlassActive = isGlassActive, hazeState = hazeState, cardColor = finalCardColor, primaryColor = finalPrimary, isSensitive = isSensitiveItem(item.key))
                        }
                        if (filteredList.isEmpty()) {
                            item { Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { Text(stringResource(R.string.setedit_empty), color = if(isCustomBg) zkmOnCustomBackgroundSubContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.onSurfaceVariant) } }
                        }
                    }
                }
            }

            val headerHazeStyle = zkmAppBarHazeStyle(themeBgColor, isCustomBg)
            
            val headerTextColor = if (isCustomBg) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.onSurface
            val headerSubColor = if (isCustomBg) zkmOnCustomBackgroundSubContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.onSurfaceVariant

            Column(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).zIndex(2f).onGloballyPositioned { coordinates -> headerHeightPx = coordinates.size.height }
                    .zkmHazeEffect(state = hazeState, style = headerHazeStyle) { progressive = dev.chrisbanes.haze.HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f) }
                    .zIndex(2f)
            ) {
                Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(stringResource(R.string.setedit_title), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, color = headerTextColor))
                        Text(stringResource(R.string.setedit_subtitle), style = MaterialTheme.typography.titleMedium.copy(color = headerSubColor))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ZkmHelpButton(NavigationRoute.SetEdit.route, tint = headerTextColor)
                        IconButton(onClick = { isGridView = true }, modifier = Modifier.background(if (isGridView) finalPrimary.copy(alpha = 0.3f) else Color.Transparent, CircleShape).size(40.dp)
        ) {
                            Icon(Icons.Outlined.GridView, stringResource(R.string.setedit_view_grid), tint = headerTextColor)
                        }
                        IconButton(onClick = { isGridView = false }, modifier = Modifier.background(if (!isGridView) finalPrimary.copy(alpha = 0.3f) else Color.Transparent, CircleShape).size(40.dp)
        ) {
                            Icon(Icons.Outlined.FormatListBulleted, stringResource(R.string.setedit_view_list), tint = headerTextColor)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                TextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.setedit_search_hint), style = MaterialTheme.typography.bodyLarge.copy(color = headerSubColor)) },
                    leadingIcon = { Icon(Icons.Default.Search, stringResource(R.string.setedit_search_hint), tint = headerSubColor) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(56.dp),
                    shape = RoundedCornerShape(50),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = if(isCustomBg) Color.White.copy(0.15f) else MaterialTheme.colorScheme.surfaceContainerHighest.zkmLiteAlpha(0.6f),
                        unfocusedContainerColor = if(isCustomBg) Color.White.copy(0.1f) else MaterialTheme.colorScheme.surfaceContainerHighest.zkmLiteAlpha(0.6f),
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = finalPrimary,
                        focusedTextColor = headerTextColor, unfocusedTextColor = headerTextColor
                    ),
                    singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                )

                Spacer(modifier = Modifier.height(16.dp))

                SetEditTabRow(tabs = categories.map { it.titleRes to it.icon } + (R.string.propdet_tab to Icons.Rounded.Shield), selectedIndex = pagerState.currentPage, onTabSelected = { index -> scope.launch { pagerState.animateScrollToPage(index) } }, primaryColor = finalPrimary, isCustomBg = isCustomBg)

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showConsentDialog) {
        BlurredAlertDialog(
            onDismissRequest = { showConsentDialog = false },
            icon = { Icon(Icons.Rounded.Shield, null, tint = finalPrimary, modifier = Modifier.size(32.dp)) },
            title = { Text(stringResource(R.string.propdet_consent_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.propdet_consent_body), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(
                    onClick = {
                        showConsentDialog = false
                        scope.launch {
                            detectLoading = true
                            val current = propStore.readCurrentProps()
                            if (current != null) {
                                propStore.saveBaseline(current)
                                detectHasConsent = true
                                detectResult = propStore.compare(current)
                            } else {
                                snackbarHostState.showSnackbar(propFailedMsg)
                            }
                            detectLoading = false
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(R.string.propdet_consent_agree)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConsentDialog = false }, shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.propdet_consent_decline)) }
            }
        )
    }

    detectConfirm?.let { action ->
        BlurredAlertDialog(
            onDismissRequest = { detectConfirm = null },
            icon = { Icon(Icons.Rounded.Warning, null, tint = Color(0xFFFF6B35), modifier = Modifier.size(32.dp)) },
            title = { Text(stringResource(if (action == 1) R.string.propdet_reset_title else R.string.propdet_delete_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(if (action == 1) R.string.propdet_reset_body else R.string.propdet_delete_body), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(
                    onClick = {
                        detectConfirm = null
                        scope.launch {
                            if (action == 1) {
                                val current = propStore.readCurrentProps()
                                if (current != null) { propStore.saveBaseline(current); detectResult = propStore.compare(current) }
                            } else {
                                propStore.clearAll()
                                detectHasConsent = false
                                detectResult = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B35)),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(R.string.propdet_confirm)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { detectConfirm = null }, shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.lp_cancel_77dfd2)) }
            }
        )
    }

    val deleteItem = pendingDeleteItem
    if (showSensitiveDialog && deleteItem != null) {
        BlurredAlertDialog(
            onDismissRequest = { showSensitiveDialog = false; pendingDeleteItem = null },
            icon = { Icon(Icons.Rounded.Warning, null, tint = Color(0xFFFF6B35), modifier = Modifier.size(32.dp)) },
            title = { Text(stringResource(R.string.lp_sensitive_setting), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.lp_you_are_about_to_delete_a_critical_system_setting), style = MaterialTheme.typography.bodyMedium)
                    Surface(color = Color(0xFFFF6B35).copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(deleteItem.key, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = Color(0xFFFF6B35))
                    }
                    Text(stringResource(R.string.lp_this_may_cause_system_instability_are_you_sure), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        performDelete(
                            deleteItem, scope, snackbarHostState, editHistory,
                            onHistoryUpdate = { editHistory = it },
                            onRefresh = { refreshData(currentTabIndex) },
                            onDismiss = { showSensitiveDialog = false; pendingDeleteItem = null; showBottomSheet = false }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B35)),
                    shape = RoundedCornerShape(12.dp)
        ) { Text(stringResource(R.string.lp_delete_anyway)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSensitiveDialog = false; pendingDeleteItem = null }, shape = RoundedCornerShape(12.dp)
        ) { Text(stringResource(R.string.lp_cancel_77dfd2)) }
            },
            containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp)
        )
    }

    if (showHistorySheet) {
        ModalBottomSheet(
        contentWindowInsets = { com.zuan.kernelmanager.next.ui.adaptive.zkmSheetWindowInsets() },
        onDismissRequest = { showHistorySheet = false }, containerColor = MaterialTheme.colorScheme.surface) {
            ConfigureSheetWindowBlur()
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(24.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.lp_recently_deleted), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${deletedHistory.size} items", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (deletedHistory.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.History, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Text(stringResource(R.string.lp_no_deleted_items), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 400.dp)) {
                        items(deletedHistory) { historyItem ->
                            HistoryItemCard(
                                historyItem = historyItem,
                                onRestore = {
                                    scope.launch(Dispatchers.IO) {
                                        val item = historyItem.item
                                        val cat = item.sourceCategory
                                        val cmd = if (cat == PropCategory.Android) "setprop ${shellQuote(item.key)} ${shellQuote(item.value)}" else "settings put ${shellQuote(cat.command.substringAfterLast(' '))} ${shellQuote(item.key)} ${shellQuote(item.value)}"
                                        val result = su(cmd)
                                        withContext(Dispatchers.Main) {
                                            if (result.isSuccess) {
                                                snackbarHostState.showSnackbar("Restored: ${item.key}")
                                                editHistory = editHistory.filter { it.id != historyItem.id }
                                                refreshData(currentTabIndex)
                                            } else { snackbarHostState.showSnackbar("Failed to restore") }
                                        }
                                    }
                                },
                                primaryColor = finalPrimary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { showHistorySheet = false }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
        ) { Text(stringResource(R.string.lp_close)) }
            }
        }
    }

    if (showAddDialog) {
        var newKey by remember { mutableStateOf("") }
        var newValue by remember { mutableStateOf("") }
        var selectedCategory by remember { mutableStateOf(PropCategory.Global) }
        
        BlurredAlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.lp_add_new_property), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.lp_category), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PropCategory.values().filter { it != PropCategory.All }) { cat ->
                            FilterChip(selected = selectedCategory == cat, onClick = { selectedCategory = cat }, label = { Text(stringResource(cat.titleRes)) }, leadingIcon = if (selectedCategory == cat) { { Icon(cat.icon, null, modifier = Modifier.size(18.dp)) } } else null
        )
                        }
                    }
                    OutlinedTextField(value = newKey, onValueChange = { newKey = it }, label = { Text(stringResource(R.string.lp_key_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                    OutlinedTextField(value = newValue, onValueChange = { newValue = it }, label = { Text(stringResource(R.string.lp_value_8dce17)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newKey.isNotBlank()) {
                            scope.launch(Dispatchers.IO) {
                                val cmd = if (selectedCategory == PropCategory.Android) "setprop ${shellQuote(newKey)} ${shellQuote(newValue)}" else "settings put ${shellQuote(selectedCategory.command.substringAfterLast(' '))} ${shellQuote(newKey)} ${shellQuote(newValue)}"
                                val result = su(cmd)
                                withContext(Dispatchers.Main) {
                                    if (result.isSuccess) {
                                        val newItem = SetEditItem(newKey, newValue, selectedCategory)
                                        editHistory = editHistory + EditHistoryItem(action = EditAction.CREATED, item = newItem)
                                        snackbarHostState.showSnackbar("Created: $newKey")
                                        refreshData(currentTabIndex)
                                        showAddDialog = false
                                    } else { snackbarHostState.showSnackbar("Failed to create") }
                                }
                            }
                        }
                    },
                    enabled = newKey.isNotBlank(), shape = RoundedCornerShape(12.dp)
        ) { Text(stringResource(R.string.lp_create)) }
            },
            dismissButton = { OutlinedButton(onClick = { showAddDialog = false }, shape = RoundedCornerShape(12.dp)
        ) { Text(stringResource(R.string.lp_cancel_77dfd2)) } },
            shape = RoundedCornerShape(24.dp)
        )
    }

    val currentItem = selectedItem
    if (showBottomSheet && currentItem != null) {
        val msgSaved = stringResource(R.string.setedit_msg_saved, "")
        val msgFailed = stringResource(R.string.setedit_msg_failed)
        val activeCategory = currentItem.sourceCategory

        EditPropBottomSheet(
            item = currentItem,
            activeCategory = activeCategory, 
            onDismiss = { showBottomSheet = false },
            onSave = { key, newValue ->
                scope.launch(Dispatchers.IO) {
                    val oldValue = currentItem.value
                    val cmd = if (activeCategory == PropCategory.Android) "setprop ${shellQuote(key)} ${shellQuote(newValue)}" else "settings put ${shellQuote(activeCategory.command.substringAfterLast(' '))} ${shellQuote(key)} ${shellQuote(newValue)}"
                    val result = su(cmd)
                    withContext(Dispatchers.Main) {
                        if (result.isSuccess) { 
                            editHistory = editHistory + EditHistoryItem(action = EditAction.MODIFIED, item = currentItem, oldValue = oldValue, newValue = newValue)
                            snackbarHostState.showSnackbar("$msgSaved $key")
                            refreshData(currentTabIndex)
                            showBottomSheet = false
                        }
                        else { snackbarHostState.showSnackbar(msgFailed) }
                    }
                }
            },
            onDelete = { key ->
                if (isSensitiveItem(key)) {
                    pendingDeleteItem = selectedItem
                    showSensitiveDialog = true
                } else {
                    performDelete(
                        currentItem, scope, snackbarHostState, editHistory,
                        onHistoryUpdate = { editHistory = it },
                        onRefresh = { refreshData(currentTabIndex) },
                        onDismiss = { showBottomSheet = false }
                    )
                }
            },
            activeColor = finalPrimary
        )
    }
}

private fun performDelete(
    item: SetEditItem,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState,
    currentHistory: List<EditHistoryItem>,
    onHistoryUpdate: (List<EditHistoryItem>) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    scope.launch(Dispatchers.IO) {
        val cat = item.sourceCategory
        val cmd = if (cat == PropCategory.Android) "" else "settings delete ${shellQuote(cat.command.substringAfterLast(' '))} ${shellQuote(item.key)}"
        if (cmd.isNotEmpty()) {
            su(cmd)
            withContext(Dispatchers.Main) { 
                onHistoryUpdate(currentHistory + EditHistoryItem(action = EditAction.DELETED, item = item))
                snackbarHostState.showSnackbar("Deleted: ${item.key}")
                onRefresh()
                onDismiss()
            }
        } else { 
            withContext(Dispatchers.Main) { snackbarHostState.showSnackbar("Cannot delete Android props") } 
        }
    }
}

@Composable
fun HistoryItemCard(historyItem: EditHistoryItem, onRestore: () -> Unit, primaryColor: Color) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeString = remember(historyItem.timestamp) { dateFormat.format(Date(historyItem.timestamp)) }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Delete, null, tint = Color(0xFFFF6B35), modifier = Modifier.size(16.dp))
                    Text(historyItem.item.key, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Value: ${historyItem.item.value}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(timeString, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
            IconButton(onClick = onRestore, modifier = Modifier.background(primaryColor.copy(alpha = 0.1f), CircleShape).size(40.dp)
        ) {
                Icon(Icons.Default.Restore, "Restore", tint = primaryColor)
            }
        }
    }
}

@Composable
fun SetEditTabRow(tabs: List<Pair<Int, ImageVector>>, selectedIndex: Int, onTabSelected: (Int) -> Unit, primaryColor: Color, isCustomBg: Boolean) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex) { listState.animateScrollToItem(selectedIndex) }

    LazyRow(state = listState, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        itemsIndexed(tabs) { index, tabItem ->
            val isSelected = selectedIndex == index
            if (isSelected) {
                Button(onClick = { onTabSelected(index) }, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = if (primaryColor.luminance() > 0.5f) Color.Black else Color.White), modifier = Modifier.height(40.dp)
        ) {
                    Icon(tabItem.second, null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(8.dp)); Text(stringResource(tabItem.first), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(onClick = { onTabSelected(index) }, shape = CircleShape, border = BorderStroke(1.dp, if (isCustomBg) Color.White.copy(0.3f) else MaterialTheme.colorScheme.outlineVariant), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (isCustomBg) Color.White.copy(0.1f) else Color.Transparent, contentColor = if (isCustomBg) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.onSurfaceVariant), modifier = Modifier.height(40.dp)
        ) {
                    Icon(tabItem.second, null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(8.dp)); Text(stringResource(tabItem.first), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun SetEditItemCard(item: SetEditItem, onClick: () -> Unit, isGlassActive: Boolean, hazeState: HazeState, cardColor: Color, primaryColor: Color, isSensitive: Boolean = false) {
    val clipboardManager = LocalClipboardManager.current
    val shape = RoundedCornerShape(16.dp)

    // Kartu referensi: seluruh resep haze/isi/border ada di ZkmReferenceGlass.kt.
    val glassModifier = if (isGlassActive) Modifier.zkmReferenceGlass(hazeState, shape) else Modifier.clip(shape)

    val textColor = if (isGlassActive || cardColor.alpha < 1f) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.onSurface
    val iconTint = if (isGlassActive || cardColor.alpha < 1f) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()).copy(0.8f) else primaryColor.copy(alpha = 0.8f)
    val valueBoxColor = if (isGlassActive || cardColor.alpha < 1f) Color.White.copy(0.15f) else MaterialTheme.colorScheme.surfaceContainerHigh.zkmLiteAlpha(0.5f)
    val valueTextColor = if (isGlassActive || cardColor.alpha < 1f) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()).copy(0.9f) else MaterialTheme.colorScheme.secondary

    Card(onClick = onClick, shape = shape, colors = CardDefaults.cardColors(containerColor = if (isGlassActive) zkmReferenceGlassFill() else cardColor), modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp).then(glassModifier)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(item.key, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (isSensitive) { Spacer(modifier = Modifier.width(6.dp)); Icon(Icons.Rounded.Warning, "Sensitive", tint = Color(0xFFFF6B35), modifier = Modifier.size(16.dp)) }
                }
                IconButton(onClick = { clipboardManager.setText(AnnotatedString(item.value)) }, modifier = Modifier.size(24.dp)
        ) { Icon(Icons.Default.ContentCopy, stringResource(R.string.setedit_copy_value), tint = iconTint, modifier = Modifier.size(16.dp)) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Surface(color = valueBoxColor, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(item.value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 11.sp), color = valueTextColor, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(10.dp))
            }
        }
    }
}

@Composable
fun EditPropBottomSheet(item: SetEditItem, activeCategory: PropCategory, onDismiss: () -> Unit, onSave: (String, String) -> Unit, onDelete: (String) -> Unit, activeColor: Color) {
    val clipboardManager = LocalClipboardManager.current
    var editValue by remember { mutableStateOf(item.value) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()
    val isSensitive = remember(item.key) { isSensitiveItem(item.key) }

    ModalBottomSheet(
        contentWindowInsets = { com.zuan.kernelmanager.next.ui.adaptive.zkmSheetWindowInsets() },
        onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        ConfigureSheetWindowBlur()

        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().verticalScroll(scrollState).padding(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.setedit_sheet_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Surface(color = activeColor.copy(alpha=0.1f), shape = RoundedCornerShape(4.dp)) { Text(stringResource(activeCategory.titleRes), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = activeColor) }
            }
            if (isSensitive) {
                Surface(color = Color(0xFFFF6B35).copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Warning, null, tint = Color(0xFFFF6B35)); Text(stringResource(R.string.lp_this_is_a_sensitive_system_setting_modifying_it_ma), style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF6B35))
                    }
                }
            }
            Text(stringResource(R.string.setedit_label_key), style = MaterialTheme.typography.labelLarge, color = activeColor)
            OutlinedButton(onClick = { clipboardManager.setText(AnnotatedString(item.key)) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, activeColor.copy(0.5f))
        ) {
                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp), tint = activeColor); Spacer(Modifier.width(8.dp)); Text(item.key, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(stringResource(R.string.setedit_label_original), style = MaterialTheme.typography.labelLarge, color = activeColor)
            OutlinedButton(onClick = { clipboardManager.setText(AnnotatedString(item.value)) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, activeColor.copy(0.5f))
        ) {
                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp), tint = activeColor); Spacer(Modifier.width(8.dp)); Text(item.value, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
            }
            OutlinedTextField(value = editValue, onValueChange = { editValue = it }, label = { Text(stringResource(R.string.setedit_label_edit)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = activeColor, focusedLabelColor = activeColor, cursorColor = activeColor))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = { onDelete(item.key) }, colors = if (isSensitive) ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFFF6B35).copy(alpha = 0.2f), contentColor = Color(0xFFFF6B35)) else ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer), modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
        ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.setedit_btn_remove))
                }
                Button(onClick = { onSave(item.key, editValue) }, colors = ButtonDefaults.buttonColors(containerColor = activeColor), modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
        ) {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.setedit_btn_save))
                }
            }
        }
    }
}
