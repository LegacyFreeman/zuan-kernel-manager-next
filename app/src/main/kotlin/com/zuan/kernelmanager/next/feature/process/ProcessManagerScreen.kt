/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class,
    ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class
)

package com.zuan.kernelmanager.next.feature.process

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zuan.kernelmanager.next.ui.components.zkmLiteAlpha
import com.zuan.kernelmanager.next.ui.components.zkmAppBarHazeStyle
import com.zuan.kernelmanager.next.ui.components.zkmCardHazeEffect
import com.zuan.kernelmanager.next.ui.components.zkmCardFill
import com.zuan.kernelmanager.next.ui.components.zkmLiquidSurface
import com.zuan.kernelmanager.next.ui.components.ZkmLiquidFlags
import com.zuan.kernelmanager.next.ui.components.zkmCardBorder
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundContentColor
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundSubContentColor
import com.zuan.kernelmanager.next.ui.components.zkmCustomBackgroundCardColor
import com.zuan.kernelmanager.next.ui.components.zkmGlassFallbackTint
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import com.zuan.kernelmanager.next.ui.components.BlurredAlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.ui.navigation.NavigationRoute
import com.zuan.kernelmanager.next.feature.help.ZkmHelpButton
import com.zuan.kernelmanager.next.feature.settings.BgType
import com.zuan.kernelmanager.next.feature.settings.SettingsViewModel
import com.zuan.kernelmanager.next.ui.theme.ThemeMode
import com.zuan.kernelmanager.next.library.device.process.ProcessData
import com.zuan.kernelmanager.next.library.device.process.ProcessUtils
import com.zuan.kernelmanager.next.library.device.process.SortType
import com.zuan.kernelmanager.next.library.device.process.ProcessFilter
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import com.zuan.kernelmanager.next.ui.components.zkmHazeSource
import com.zuan.kernelmanager.next.ui.components.zkmHazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.zuan.kernelmanager.next.ui.components.zkmGlassTint
import com.zuan.kernelmanager.next.ui.components.zkmGlassCard
import com.zuan.kernelmanager.next.ui.components.ZkmHazeStyleCache
import com.zuan.kernelmanager.next.ui.theme.zkmIconShape
import com.zuan.kernelmanager.next.ui.theme.ZkmIconGlyph

// --- VIEWMODEL ---
class ProcessManagerViewModel : ViewModel() {
    var processList by mutableStateOf<List<ProcessData>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var limitOption by mutableStateOf(20)
        private set
    var sortType by mutableStateOf(SortType.CPU)
        private set
    var filterType by mutableStateOf(ProcessFilter.ALL)
        private set
    var searchQuery by mutableStateOf("")
        private set

    var userProcessCount by mutableStateOf(0)
        private set
    var systemProcessCount by mutableStateOf(0)
        private set
    var androidProcessCount by mutableStateOf(0)
        private set
    var totalProcessCount by mutableStateOf(0)
        private set
    var lastUpdatedMs by mutableLongStateOf(0L)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var monitorJob: Job? = null
    private var refreshJob: Job? = null

    fun setLimit(newLimit: Int) {
        limitOption = newLimit
        refreshData()
    }

    fun setSort(newSort: SortType) {
        sortType = newSort
        refreshData()
    }

    fun setFilter(newFilter: ProcessFilter) {
        filterType = newFilter
        refreshData()
    }

    fun setSearch(query: String) {
        searchQuery = query
        refreshData()
    }

    fun startMonitoring(context: android.content.Context) {
        if (monitorJob?.isActive == true) return
        monitorJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                updateData(context)
                delay(3000L)
            }
        }
    }

    fun refreshData() {
        isLoading = true
    }

    fun refreshNow(context: android.content.Context) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            updateData(context)
        }
    }

    fun loadDetail(context: android.content.Context, pid: String, onResult: (ProcessData?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val detail = ProcessUtils.getProcessDetail(context, pid)
            withContext(Dispatchers.Main) { onResult(detail) }
        }
    }

    fun kill(context: android.content.Context, process: ProcessData, onResult: (String, Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = ProcessUtils.killProcess(context, process)
            withContext(Dispatchers.Main) {
                onResult(result.message, result.success)
                if (result.success) refreshNow(context)
            }
        }
    }

    fun forceStop(context: android.content.Context, process: ProcessData, onResult: (String, Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = ProcessUtils.forceStopApp(context, process)
            withContext(Dispatchers.Main) {
                onResult(result.message, result.success)
                if (result.success) refreshNow(context)
            }
        }
    }

    private suspend fun updateData(context: android.content.Context) {
        runCatching {
            val all = ProcessUtils.snapshot(context)
            val pm = context.packageManager
            var user = 0
            var system = 0
            var android = 0
            all.forEach { process ->
                if (process.isAndroidApp) {
                    android++
                    if (process.isUserApp) user++ else system++
                }
            }

            val filtered = all.asSequence()
                .filter { process ->
                    when (filterType) {
                        ProcessFilter.ALL -> true
                        ProcessFilter.ANDROID_USER -> process.isAndroidApp && process.isUserApp
                        ProcessFilter.ANDROID_SYSTEM -> process.isAndroidApp && !process.isUserApp
                        ProcessFilter.ANDROID -> process.isAndroidApp
                        ProcessFilter.OTHER -> !process.isAndroidApp
                    }
                }
                .filter { process ->
                    val q = searchQuery.trim().lowercase()
                    q.isEmpty() || listOf(process.appName, process.packageName, process.user, process.command, process.cmdline, process.pid)
                        .any { value -> value.lowercase().contains(q) }
                }
                .sortedWith(Comparator { a, b ->
                    when (sortType) {
                        SortType.CPU -> parseCpu(b.cpu).compareTo(parseCpu(a.cpu))
                        SortType.RES -> parseMemory(b.res).compareTo(parseMemory(a.res))
                        SortType.PID -> (a.pid.toLongOrNull() ?: Long.MAX_VALUE).compareTo(b.pid.toLongOrNull() ?: Long.MAX_VALUE)
                    }
                })
                .take(limitOption.coerceIn(1, 200))
                .toList()

            withContext(Dispatchers.Main) {
                totalProcessCount = all.size
                userProcessCount = user
                systemProcessCount = system
                androidProcessCount = android
                processList = filtered
                lastUpdatedMs = System.currentTimeMillis()
                isLoading = false
                errorMessage = null
            }
        }.onFailure { throwable ->
            withContext(Dispatchers.Main) {
                isLoading = false
                errorMessage = throwable.message ?: throwable.javaClass.simpleName
            }
        }
    }

    private fun parseCpu(value: String): Float = value.removeSuffix("%").toFloatOrNull() ?: 0f

    private fun parseMemory(value: String): Long {
        val upper = value.uppercase()
        val num = upper.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
        return when {
            upper.contains("G") -> (num * 1024 * 1024).toLong()
            upper.contains("M") -> (num * 1024).toLong()
            else -> num.toLong()
        }
    }

    override fun onCleared() {
        monitorJob?.cancel()
        refreshJob?.cancel()
        super.onCleared()
    }
}

@Composable
fun ProcessManagerScreen(
    navController: NavController,
    hazeState: HazeState,
    viewModel: ProcessManagerViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeightDp = with(density) { headerHeightPx.toDp() }

    var selectedProcess by remember { mutableStateOf<ProcessData?>(null) }
    var showManageDialog by remember { mutableStateOf(false) }
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }
    var showKillConfirm by remember { mutableStateOf(false) }
    var showForceStopConfirm by remember { mutableStateOf(false) }

    // --- THEME STATES ---
    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val bgType by settingsViewModel.bgType.collectAsStateWithLifecycle()
    val isHazeEnabled by settingsViewModel.isHazeEnabled.collectAsStateWithLifecycle()
    val isCustomBg = bgType != BgType.SYSTEM
    val isGlassActive = isHazeEnabled && isCustomBg
    
    val cardDarkness by settingsViewModel.cardDarkness.collectAsStateWithLifecycle()
    val isDynamic by settingsViewModel.isDynamicColor.collectAsStateWithLifecycle()
    val themeColorName by settingsViewModel.currentThemeColor.collectAsStateWithLifecycle()
    val isCustomColor by settingsViewModel.isCustomColor.collectAsStateWithLifecycle()
    val customPrimary by settingsViewModel.customPrimaryColor.collectAsStateWithLifecycle()

    val isSystemDark = isSystemInDarkTheme()
    val useDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM_DEFAULT -> isSystemDark
    }

    val effectivePrimary = remember(isDynamic, themeColorName, isCustomColor, customPrimary) {
        when {
            isCustomColor -> Color(customPrimary)
            isDynamic -> Color.Unspecified
            else -> themeColorName.primary
        }
    }

    val finalPrimary = if (effectivePrimary == Color.Unspecified) {
        MaterialTheme.colorScheme.primary
    } else {
        effectivePrimary
    }

    val (tintedBackground, cardContainerColor) = generateThemedBackgroundColor(finalPrimary, useDarkTheme)

    val actualCardContainerColor = when {
        isGlassActive -> zkmGlassFallbackTint(zkmIsDarkTheme())
        isCustomBg -> MaterialTheme.colorScheme.surface.zkmLiteAlpha(cardDarkness)
        else -> cardContainerColor
    }

    val contentColor = if (isCustomBg) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.onSurface
    val subContentColor = if (isCustomBg) zkmOnCustomBackgroundSubContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.onSurfaceVariant

    val ExpressiveCornerRadius = 32.dp
    
    val baseGlassModifier = if (isGlassActive) {
        Modifier
            .zkmCardHazeEffect(shape = RoundedCornerShape(ExpressiveCornerRadius),
                state = hazeState,
                style = ZkmHazeStyleCache.of(
                    backgroundColor = MaterialTheme.colorScheme.surface.zkmLiteAlpha(0.2f),
                    blurRadius = 20.dp,
                    noiseFactor = 0.05f,
                    tints = emptyList()
                )
            )
            .zkmCardBorder(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.3f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                shape = RoundedCornerShape(ExpressiveCornerRadius)
            )
    } else {
        Modifier
    }

    fun toggleFloatingMode() {
        if (Settings.canDrawOverlays(context)) {
            val intent = Intent(context, FloatingProcessService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            showOverlayPermissionDialog = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.startMonitoring(context)
        viewModel.refreshNow(context)
    }

    Scaffold(
        containerColor = Color.Transparent,
        // [FIX] contentColor eksplisit: tanpa ini semua Text tanpa warna di dalam layar
        // (angka health, label chart, "shown / total", dll.) jatuh ke hitam di dark mode.
        contentColor = contentColor,
        contentWindowInsets = WindowInsets(0,0,0,0)
    ) { _ -> 
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isCustomBg) Color.Transparent else tintedBackground)
        ) {
            
            // LAYER 0: Custom Wallpaper Background (jika ada)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zkmHazeSource(state = hazeState, zIndex = 0f)
            )
            
            // LAYER 1: SCROLLABLE PROCESS LIST
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .zkmHazeSource(state = hazeState, zIndex = 1f),
                contentPadding = PaddingValues(top = headerHeightDp, bottom = 100.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                item {
                    SystemHealthCard(
                        processList = viewModel.processList, 
                        cardBg = actualCardContainerColor,
                        isGlassActive = isGlassActive,
                        glassModifier = baseGlassModifier
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.weight(1.4f)) {
                            ResourceUsageChart(
                                processList = viewModel.processList,
                                sortType = viewModel.sortType,
                                cardBg = actualCardContainerColor,
                                isGlassActive = isGlassActive,
                                glassModifier = baseGlassModifier
                            )
                        }
                        
                        Box(modifier = Modifier.weight(1f)) {
                            ProcessDistributionChart(
                                userCount = viewModel.userProcessCount,
                                systemCount = viewModel.systemProcessCount,
                                cardBg = actualCardContainerColor,
                                isGlassActive = isGlassActive,
                                glassModifier = baseGlassModifier
                            )
                        }
                    }
                }

                item {
                    ProcessSearchRow(
                        query = viewModel.searchQuery,
                        onQueryChange = { viewModel.setSearch(it) },
                        total = viewModel.totalProcessCount,
                        shown = viewModel.processList.size,
                        isGlassActive = isGlassActive,
                        contentColor = contentColor,
                        subContentColor = subContentColor
                    )
                }

                item {
                    SortAndFilterRow(
                        currentSort = viewModel.sortType,
                        currentLimit = viewModel.limitOption,
                        currentFilter = viewModel.filterType,
                        onSortChange = { viewModel.setSort(it) },
                        onLimitChange = { viewModel.setLimit(it) },
                        onFilterChange = { viewModel.setFilter(it) },
                        isGlassActive = isGlassActive,
                        contentColor = contentColor
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.running_processes),
                        style = MaterialTheme.typography.titleSmall,
                        color = finalPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }

                if (viewModel.processList.isEmpty() && viewModel.isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(200.dp), 
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = finalPrimary)
                        }
                    }
                } else {
                    items(viewModel.processList, key = { it.pid }) { process ->
                        ExpressiveProcessItem(
                            process = process,
                            sortType = viewModel.sortType,
                            onClick = { selectedProcess = process },
                            isGlassActive = isGlassActive,
                            hazeState = hazeState,
                            contentColor = contentColor,
                            subContentColor = subContentColor
                        )
                        if (!isGlassActive) HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp, end = 24.dp),
                            color = if (isCustomBg) Color.White.copy(0.2f) else MaterialTheme.colorScheme.surfaceVariant.zkmLiteAlpha(0.5f)
                        )
                    }
                }
            }

            // LAYER 2: TOP HEADER (PROGRESSIVE BLUR)
            val headerHazeStyle = zkmAppBarHazeStyle(tintedBackground, isCustomBg)

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        headerHeightPx = coordinates.size.height
                    }
                    .zkmHazeEffect(
                        state = hazeState,
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
                    // Header Bar (Back + Title + Action buttons)
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
                                tint = contentColor
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.system), 
                                style = MaterialTheme.typography.labelMedium, 
                                color = subContentColor
                            )
                            Text(
                                text = stringResource(R.string.process_manager), 
                                style = MaterialTheme.typography.titleLarge, 
                                fontWeight = FontWeight.ExtraBold,
                                color = contentColor
                            )
                        }

                        ZkmHelpButton(NavigationRoute.ProcessManager.route, tint = contentColor)

                        IconButton(onClick = { viewModel.refreshNow(context) }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = contentColor
                            )
                        }

                        IconButton(onClick = { toggleFloatingMode() }) {
                            Icon(
                                imageVector = Icons.Outlined.PictureInPictureAlt,
                                contentDescription = stringResource(R.string.floating_mode),
                                tint = contentColor
                            )
                        }
                    }

                    // Spacer Fading Buffer (Bumbu Rahasia)
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    val currentProcess = selectedProcess
    if (currentProcess != null) {
        ModernProcessDetailDialog(
            process = currentProcess,
            viewModel = viewModel,
            onDismiss = { selectedProcess = null },
            onManageClick = { showManageDialog = true },
            onKillClick = { showKillConfirm = true },
            onStopClick = { showForceStopConfirm = true }
        )
    }

    if (showManageDialog && currentProcess != null) {
        AppManageDialog(
            process = currentProcess, 
            onDismiss = { showManageDialog = false }
        )
    }

    if (showKillConfirm && currentProcess != null) {
        BlurredAlertDialog(
            onDismissRequest = { showKillConfirm = false },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.kill)) },
            text = {
                Text("Terminate ${currentProcess.appName} (PID ${currentProcess.pid})? This sends SIGKILL and cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showKillConfirm = false
                    viewModel.kill(context, currentProcess) { message, success ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        if (success) selectedProcess = null
                    }
                }) { Text(stringResource(R.string.kill)) }
            },
            dismissButton = { TextButton(onClick = { showKillConfirm = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    if (showForceStopConfirm && currentProcess != null) {
        BlurredAlertDialog(
            onDismissRequest = { showForceStopConfirm = false },
            icon = { Icon(Icons.Outlined.Stop, contentDescription = null) },
            title = { Text(stringResource(R.string.force_stop)) },
            text = { Text("Force-stop ${currentProcess.packageName}? Android will stop the application and its processes.") },
            confirmButton = {
                TextButton(onClick = {
                    showForceStopConfirm = false
                    viewModel.forceStop(context, currentProcess) { message, success ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        if (success) selectedProcess = null
                    }
                }) { Text(stringResource(R.string.force_stop)) }
            },
            dismissButton = { TextButton(onClick = { showForceStopConfirm = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    if (showOverlayPermissionDialog) {
        BlurredAlertDialog(
            onDismissRequest = { showOverlayPermissionDialog = false },
            icon = { Icon(Icons.Outlined.PictureInPictureAlt, contentDescription = null) },
            title = { Text(stringResource(R.string.permission_required)) },
            text = { Text(stringResource(R.string.overlay_permission_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showOverlayPermissionDialog = false
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION, 
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }) { 
                    Text(stringResource(R.string.open_settings)) 
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayPermissionDialog = false }) { 
                    Text(stringResource(R.string.btn_cancel)) 
                }
            }
        )
    }
}

private fun generateThemedBackgroundColor(primaryColor: Color, isDark: Boolean): Pair<Color, Color> = com.zuan.kernelmanager.next.ui.components.ZkmScreenPalette.colors(isDark, primaryColor)

// ==========================================
// CHARTS COMPONENTS
// ==========================================

@Composable
fun ResourceUsageChart(
    processList: List<ProcessData>, 
    sortType: SortType, 
    cardBg: Color,
    isGlassActive: Boolean = false,
    glassModifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(32.dp)
    val baseModifier = Modifier.fillMaxWidth().height(180.dp)
    
    Surface(
        modifier = if (isGlassActive) baseModifier.then(glassModifier) else baseModifier,
        shape = cardShape,
        color = if (isGlassActive) zkmCardFill(zkmGlassTint(MaterialTheme.colorScheme.surfaceContainer, zkmIsDarkTheme(), 0.45f)) else cardBg,
        tonalElevation = if (isGlassActive) 0.dp else 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (sortType == SortType.CPU) stringResource(R.string.top_cpu_usage) else stringResource(R.string.top_ram_usage),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            val topProcesses = processList.take(5)
            val maxValue = topProcesses.maxOfOrNull { 
                if (sortType == SortType.CPU) it.cpu.replace("%", "").toFloatOrNull() ?: 0f
                else it.res.replace("M", "").replace("K", "").toFloatOrNull() ?: 0f
            } ?: 1f

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                topProcesses.forEach { process ->
                    val rawVal = if (sortType == SortType.CPU) {
                        process.cpu.replace("%", "").toFloatOrNull() ?: 0f
                    } else {
                        process.res.replace("M", "").replace("K", "").toFloatOrNull() ?: 0f
                    }
                    val progress = (rawVal / maxValue).coerceIn(0f, 1f)
                    
                    val animatedProgress by animateFloatAsState(
                        targetValue = progress,
                        animationSpec = spring(dampingRatio = 0.7f),
                        label = "bar"
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = process.appName,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(50.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedProgress)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (sortType == SortType.CPU) {
                                            MaterialTheme.colorScheme.primary 
                                        } else {
                                            MaterialTheme.colorScheme.tertiary
                                        }
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProcessDistributionChart(
    userCount: Int, 
    systemCount: Int, 
    cardBg: Color,
    isGlassActive: Boolean = false,
    glassModifier: Modifier = Modifier
) {
    val total = userCount + systemCount
    val userRatio = if (total > 0) userCount.toFloat() / total else 0f
    
    val animatedUserRatio by animateFloatAsState(
        targetValue = userRatio,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "donut"
    )

    val cardShape = RoundedCornerShape(32.dp)
    val baseModifier = Modifier.fillMaxWidth().height(180.dp)
    
    Surface(
        modifier = if (isGlassActive) baseModifier.then(glassModifier) else baseModifier,
        shape = cardShape,
        color = if (isGlassActive) zkmCardFill(zkmGlassTint(MaterialTheme.colorScheme.surfaceContainer, zkmIsDarkTheme(), 0.45f)) else cardBg,
        tonalElevation = if (isGlassActive) 0.dp else 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                val userColor = MaterialTheme.colorScheme.secondary
                val systemColor = MaterialTheme.colorScheme.surfaceVariant
                
                Canvas(modifier = Modifier.size(80.dp)) {
                    val strokeWidth = 12.dp.toPx()
                    val radius = (size.minDimension - strokeWidth) / 2
                    
                    drawCircle(
                        color = systemColor,
                        radius = radius,
                        style = Stroke(width = strokeWidth)
                    )
                    
                    drawArc(
                        color = userColor,
                        startAngle = -90f,
                        sweepAngle = animatedUserRatio * 360f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        topLeft = Offset((size.width - radius * 2) / 2, (size.height - radius * 2) / 2),
                        size = Size(radius * 2, radius * 2)
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = total.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(stringResource(R.string.total), fontSize = 8.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendDot(stringResource(R.string.user), userCount, MaterialTheme.colorScheme.secondary)
                LegendDot(stringResource(R.string.sys), systemCount, MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}

@Composable
fun LegendDot(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = count.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(text = label, fontSize = 8.sp, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun SystemHealthCard(
    processList: List<ProcessData>, 
    cardBg: Color,
    isGlassActive: Boolean = false,
    glassModifier: Modifier = Modifier
) {
    val topCpu = processList.maxByOrNull { 
        it.cpu.replace("%","").toFloatOrNull() ?: 0f 
    }
    val totalProcs = processList.size

    val cardShape = RoundedCornerShape(32.dp)
    
    val finalModifier = if (isGlassActive) {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .then(glassModifier)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    }

    Surface(
        modifier = finalModifier,
        shape = cardShape,
        color = if (isGlassActive) zkmCardFill(zkmGlassTint(MaterialTheme.colorScheme.surfaceContainer, zkmIsDarkTheme(), 0.45f)) else cardBg,
        tonalElevation = if (isGlassActive) 0.dp else 1.dp
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(zkmIconShape(CircleShape))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                ZkmIconGlyph(icon = Icons.Default.Speed, tint = MaterialTheme.colorScheme.onPrimaryContainer, iconSize = 24.dp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    stringResource(R.string.system_health), 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.monitoring_threads, totalProcs), 
                    style = MaterialTheme.typography.bodySmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (topCpu != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.top_load, topCpu.appName, topCpu.cpu), 
                        style = MaterialTheme.typography.labelSmall, 
                        color = MaterialTheme.colorScheme.primary, 
                        maxLines = 1, 
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun ProcessSearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    total: Int,
    shown: Int,
    isGlassActive: Boolean = false,
    contentColor: Color = Color.Unspecified,
    subContentColor: Color = Color.Unspecified
) {
    val textColor = if (contentColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else contentColor
    val hintColor = if (subContentColor == Color.Unspecified) MaterialTheme.colorScheme.outline else subContentColor
    val fieldTint = zkmGlassTint(MaterialTheme.colorScheme.surfaceContainerHigh, zkmIsDarkTheme(), 0.50f)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Search process, package or PID") },
            modifier = Modifier
                .weight(1f)
                .then(if (isGlassActive) Modifier.zkmLiquidSurface(18.dp) else Modifier),
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = textColor,
                unfocusedTextColor = textColor,
                focusedPlaceholderColor = hintColor,
                unfocusedPlaceholderColor = hintColor,
                focusedLeadingIconColor = textColor,
                unfocusedLeadingIconColor = hintColor,
                focusedContainerColor = if (isGlassActive) zkmCardFill(fieldTint) else Color.Transparent,
                unfocusedContainerColor = if (isGlassActive) zkmCardFill(fieldTint) else Color.Transparent,
                // Mode liquid: kaca sudah menggambar border-nya sendiri -> hilangkan outline ganda.
                unfocusedBorderColor = if (isGlassActive && ZkmLiquidFlags.cardsLiquid) Color.Transparent else Color.Unspecified
            )
        )
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("$shown / $total", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = textColor)
            Text("visible", style = MaterialTheme.typography.labelSmall, color = hintColor)
        }
    }
}

@Composable
fun SortAndFilterRow(
    currentSort: SortType,
    currentLimit: Int,
    currentFilter: ProcessFilter,
    onSortChange: (SortType) -> Unit,
    onLimitChange: (Int) -> Unit,
    onFilterChange: (ProcessFilter) -> Unit,
    isGlassActive: Boolean = false,
    contentColor: Color = Color.Unspecified
) {
    val chipLabelColor = if (contentColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else contentColor
    val chipColors = if (isGlassActive) {
        FilterChipDefaults.filterChipColors(
            containerColor = zkmGlassTint(MaterialTheme.colorScheme.surfaceContainerHigh, zkmIsDarkTheme(), 0.50f),
            labelColor = chipLabelColor,
            iconColor = chipLabelColor,
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.60f),
            selectedLabelColor = chipLabelColor,
            selectedLeadingIconColor = chipLabelColor
        )
    } else {
        FilterChipDefaults.filterChipColors()
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = currentSort == SortType.CPU,
                onClick = { onSortChange(SortType.CPU) },
                label = { Text(stringResource(R.string.cpu_usage)) },
                leadingIcon = { if (currentSort == SortType.CPU) Icon(Icons.Default.Speed, null, Modifier.size(16.dp)) },
                shape = RoundedCornerShape(50),
                colors = chipColors
            )
            FilterChip(
                selected = currentSort == SortType.RES,
                onClick = { onSortChange(SortType.RES) },
                label = { Text(stringResource(R.string.memory)) },
                leadingIcon = { if (currentSort == SortType.RES) Icon(Icons.Default.Memory, null, Modifier.size(16.dp)) },
                shape = RoundedCornerShape(50),
                colors = chipColors
            )
            FilterChip(
                selected = currentSort == SortType.PID,
                onClick = { onSortChange(SortType.PID) },
                label = { Text("PID") },
                leadingIcon = { if (currentSort == SortType.PID) Icon(Icons.Default.FilterList, null, Modifier.size(16.dp)) },
                shape = RoundedCornerShape(50),
                colors = chipColors
            )
            FilterChip(
                selected = false,
                onClick = {
                    val nextLimit = when (currentLimit) {
                        10 -> 20
                        20 -> 50
                        50 -> 100
                        else -> 10
                    }
                    onLimitChange(nextLimit)
                },
                label = { Text(stringResource(R.string.limit_format, currentLimit)) },
                leadingIcon = { Icon(Icons.Default.FilterList, null, Modifier.size(16.dp)) },
                shape = RoundedCornerShape(50),
                colors = if (isGlassActive) chipColors else FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProcessFilterChip("All", currentFilter == ProcessFilter.ALL, colors = chipColors) { onFilterChange(ProcessFilter.ALL) }
            ProcessFilterChip("User apps", currentFilter == ProcessFilter.ANDROID_USER, colors = chipColors) { onFilterChange(ProcessFilter.ANDROID_USER) }
            ProcessFilterChip("System apps", currentFilter == ProcessFilter.ANDROID_SYSTEM, colors = chipColors) { onFilterChange(ProcessFilter.ANDROID_SYSTEM) }
            ProcessFilterChip("Android", currentFilter == ProcessFilter.ANDROID, colors = chipColors) { onFilterChange(ProcessFilter.ANDROID) }
            ProcessFilterChip("Other", currentFilter == ProcessFilter.OTHER, colors = chipColors) { onFilterChange(ProcessFilter.OTHER) }
        }
    }
}

@Composable
private fun ProcessFilterChip(label: String, selected: Boolean, colors: SelectableChipColors = FilterChipDefaults.filterChipColors(), onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(50),
        colors = colors
    )
}

@Composable
fun ExpressiveProcessItem(
    process: ProcessData, 
    sortType: SortType, 
    onClick: () -> Unit,
    isGlassActive: Boolean = false,
    hazeState: HazeState? = null,
    contentColor: Color = Color.Unspecified,
    subContentColor: Color = Color.Unspecified
) {
    val headlineColor = if (contentColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else contentColor
    val supportColor = if (subContentColor == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else subContentColor
    // [FIX] Baris proses dulu "telanjang" saat glass; sekarang tiap baris card glass.
    val rowModifier = if (isGlassActive) {
        Modifier
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .zkmGlassCard(true, hazeState, RoundedCornerShape(20.dp), zkmGlassTint(MaterialTheme.colorScheme.surfaceContainer, zkmIsDarkTheme(), 0.45f), 20.dp)
            .clickable { onClick() }
    } else {
        Modifier.clickable { onClick() }.padding(horizontal = 8.dp)
    }
    ListItem(
        modifier = rowModifier,
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = headlineColor,
            supportingColor = supportColor
        ),
        headlineContent = { 
            Text(
                process.appName, 
                style = MaterialTheme.typography.bodyLarge, 
                fontWeight = FontWeight.SemiBold, 
                maxLines = 1, 
                overflow = TextOverflow.Ellipsis
            ) 
        },
        supportingContent = { 
            Text(
                process.packageName, 
                style = MaterialTheme.typography.bodySmall, 
                color = supportColor, 
                maxLines = 1, 
                overflow = TextOverflow.Ellipsis
            ) 
        },
        leadingContent = {
            Surface(
                shape = RoundedCornerShape(16.dp), 
                color = MaterialTheme.colorScheme.surfaceContainerHighest, 
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) { 
                    AppIcon(drawable = process.icon, modifier = Modifier.size(32.dp)) 
                }
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                val primaryValue = if (sortType == SortType.CPU) process.cpu else process.res
                val primaryLabel = if (sortType == SortType.CPU) stringResource(R.string.cpu) else stringResource(R.string.ram)
                Text(
                    text = primaryValue, 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold, 
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "$primaryLabel • PID ${process.pid}", 
                    style = MaterialTheme.typography.labelSmall, 
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    )
}

@Composable
fun ModernProcessDetailDialog(
    process: ProcessData,
    viewModel: ProcessManagerViewModel,
    onDismiss: () -> Unit,
    onManageClick: () -> Unit,
    onKillClick: () -> Unit,
    onStopClick: () -> Unit
) {
    val context = LocalContext.current
    var detail by remember(process.pid) { mutableStateOf(process) }
    var loading by remember(process.pid) { mutableStateOf(true) }

    LaunchedEffect(process.pid) {
        loading = true
        viewModel.loadDetail(context, process.pid) {
            if (it != null) detail = it
            loading = false
        }
    }

    com.zuan.kernelmanager.next.ui.components.ZkmDialog(onDismissRequest = onDismiss) {
        com.zuan.kernelmanager.next.ui.components.ZkmDialogPanel {
            Column(
                modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) { AppIcon(drawable = detail.icon, modifier = Modifier.size(48.dp)) }
                Spacer(modifier = Modifier.height(16.dp))
                Text(detail.appName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(detail.packageName.ifBlank { detail.command }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    InfoBadge(stringResource(R.string.cpu), detail.cpu)
                    InfoBadge(stringResource(R.string.ram), detail.res)
                    InfoBadge(stringResource(R.string.pid), detail.pid)
                }

                Spacer(modifier = Modifier.height(16.dp))
                if (loading) CircularProgressIndicator(modifier = Modifier.size(22.dp))

                Spacer(modifier = Modifier.height(12.dp))
                DetailRow("State", detail.state.ifBlank { "Unknown" })
                DetailRow("User", detail.user)
                DetailRow("Swap", detail.swap)
                DetailRow("cpuset", detail.cpuSet)
                DetailRow("cgroup", detail.cGroup)
                DetailRow("oom_adj", detail.oomAdj)
                DetailRow("oom_score", detail.oomScore)
                DetailRow("oom_score_adj", detail.oomScoreAdj)
                DetailRow("Command", detail.command.ifBlank { "—" })
                DetailRow("Cmdline", detail.cmdline.ifBlank { "—" })

                Spacer(modifier = Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    if (detail.isAndroidApp) {
                        FilledTonalButton(
                            onClick = onManageClick,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Outlined.Settings, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.manage))
                        }
                    }
                    Button(
                        onClick = onKillClick,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.kill))
                    }
                }
                if (detail.isAndroidApp) {
                    TextButton(onClick = onStopClick, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Stop, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.force_stop), color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
fun InfoBadge(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AppIcon(drawable: android.graphics.drawable.Drawable?, modifier: Modifier = Modifier) {
    if (drawable != null) {
        val bitmap = remember(drawable) { drawable.toBitmap().asImageBitmap() }
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier)
    } else {
        Icon(
            Icons.Default.FilterList, 
            null, 
            modifier = modifier, 
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AppManageDialog(process: ProcessData, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var verName by remember { mutableStateOf("") }
    var verCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(process.packageName) {
        try {
            val pInfo = context.packageManager.getPackageInfo(process.packageName, 0)
            verName = pInfo.versionName ?: context.getString(R.string.unknown)
            verCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toString()
            } else {
                pInfo.versionCode.toString()
            }
            isLoading = false
        } catch (e: Exception) { 
            verName = context.getString(R.string.not_found)
            verCode = "-"
            isLoading = false
        }
    }

    com.zuan.kernelmanager.next.ui.components.ZkmDialog(onDismissRequest = onDismiss) {
        com.zuan.kernelmanager.next.ui.components.ZkmDialogPanel {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    stringResource(R.string.app_details), 
                    style = MaterialTheme.typography.titleLarge, 
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailRow(stringResource(R.string.version), verName)
                        DetailRow(stringResource(R.string.build), verCode)
                        DetailRow(stringResource(R.string.package_name), process.packageName)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onDismiss, 
                    modifier = Modifier.fillMaxWidth(), 
                    shape = RoundedCornerShape(12.dp)
                ) { 
                    Text(stringResource(R.string.btn_close)) 
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(), 
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label, 
            style = MaterialTheme.typography.bodyMedium, 
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value, 
            style = MaterialTheme.typography.bodyMedium, 
            fontWeight = FontWeight.Medium, 
            maxLines = 1, 
            overflow = TextOverflow.Ellipsis, 
            modifier = Modifier.widthIn(max = 200.dp)
        )
    }
}
