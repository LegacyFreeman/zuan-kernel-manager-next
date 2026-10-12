/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.zuan.kernelmanager.next.feature.miuithermal

import com.zuan.kernelmanager.next.ui.components.zkmAppBarHazeStyle
import com.zuan.kernelmanager.next.ui.components.zkmCardHazeEffect
import com.zuan.kernelmanager.next.ui.components.zkmReferenceGlass
import com.zuan.kernelmanager.next.ui.components.zkmReferenceGlassFill
import com.zuan.kernelmanager.next.ui.components.zkmCardBorder
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundContentColor
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundSubContentColor
import com.zuan.kernelmanager.next.ui.components.zkmCustomBackgroundCardColor
import com.zuan.kernelmanager.next.ui.components.zkmGlassFallbackTint
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.composables.core.rememberDialogState
import com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.ui.navigation.NavigationRoute
import com.zuan.kernelmanager.next.feature.help.ZkmHelpButton
import com.zuan.kernelmanager.next.ui.components.DialogTextButton
import com.zuan.kernelmanager.next.ui.components.DialogUnstyled
import com.zuan.kernelmanager.next.ui.components.VideoWallpaperPlayer
import com.zuan.kernelmanager.next.ui.components.WeatherEffectOverlay
import com.zuan.kernelmanager.next.feature.settings.BgType
import com.zuan.kernelmanager.next.feature.settings.SettingsViewModel
import com.zuan.kernelmanager.next.ui.theme.ThemeMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets
import com.zuan.kernelmanager.next.ui.components.ZkmHazeStyleCache
import com.zuan.kernelmanager.next.ui.theme.zkmIconShape
import com.zuan.kernelmanager.next.ui.theme.ZkmIconGlyph

private const val THERMAL_CONFIG_DIR = "/data/vendor/thermal/config"

data class ThermalMode(
    val id: String,
    val label: String,
    val description: String = ""
)

class MiuiThermalViewModel : ViewModel() {
    var miuiVersion by mutableStateOf("")
        private set
    var platform by mutableStateOf("")
        private set
    var supported by mutableStateOf(false)
        private set
    var supportReason by mutableStateOf("")
        private set
    var currentMode by mutableStateOf("default")
        private set
    var modes by mutableStateOf<List<ThermalMode>>(emptyList())
        private set
    var thermalFiles by mutableStateOf<List<String>>(emptyList())
        private set
    var currentFile by mutableStateOf("")
        private set
    var editorContent by mutableStateOf("")
        private set
    var editorOpen by mutableStateOf(false)
        private set
    var encrypted by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var log by mutableStateOf("")
        private set

    fun refresh(context: Context) {
        if (busy) return
        viewModelScope.launch(Dispatchers.IO) {
            val assetsRoot = extractAssets(context)
            val miui = KernelRootRuntime.executeBlocking("getprop ro.miui.ui.version.name").outputString.trim()
            val plat = KernelRootRuntime.executeBlocking("getprop ro.board.platform").outputString.trim()
            val sdk = KernelRootRuntime.executeBlocking("getprop ro.build.version.sdk").outputString.trim().toIntOrNull() ?: 0
            val files = KernelRootRuntime.executeBlocking(
                "for d in /data/vendor/thermal/config /vendor/etc /system/etc; do [ -d \"\$d\" ] || continue; for f in \"\$d\"/thermal*.conf; do [ -f \"\$f\" ] && echo \"\$f\"; done; done"
            ).out
                .map { it.trim() }
                .filter { it.startsWith("/") && it.substringAfterLast('/').startsWith("thermal") && it.endsWith(".conf") }
                .distinct()
                .sorted()
            val stateNew = KernelRootRuntime.executeBlocking(
                "cat ${shellQuote("$THERMAL_CONFIG_DIR/thermal.current.ini")} 2>/dev/null || true"
            ).outputString.trim()
            val stateOld = KernelRootRuntime.executeBlocking(
                "cat ${shellQuote("$THERMAL_CONFIG_DIR/thermal-engine.current.ini")} 2>/dev/null || true"
            ).outputString.trim()
            val rawState = stateNew.ifBlank { stateOld }
            val modeFromState = rawState.trim()

            val supportResult = KernelRootRuntime.executeBlocking(
                "cd ${shellQuote(assetsRoot)} && sh kr-script/miui/thermal_conf2/support.sh 2>/dev/null || true"
            )
            val supportedByScript = supportResult.outputString.trim() == "1"
            val optionsResult = KernelRootRuntime.executeBlocking(
                "cd ${shellQuote(assetsRoot)} && sh kr-script/miui/thermal_conf2/options.sh 2>/dev/null || true"
            )
            val parsedModes = parseModes(optionsResult.outputString, plat, context)
            withContext(Dispatchers.Main) {
                miuiVersion = miui
                platform = plat
                thermalFiles = files
                currentMode = modeFromState
                modes = parsedModes
                supported = supportedByScript || (miui.isNotBlank() && parsedModes.isNotEmpty())
                supportReason = when {
                    sdk <= 28 -> "Scene master requires Android 10+ for this profile set."
                    miui.isBlank() -> "MIUI property was not detected."
                    parsedModes.isEmpty() -> "No bundled profile set for board platform $plat."
                    else -> ""
                }
            }
        }
    }
    fun updateEditorContent(value: String) {
        editorContent = value
    }

    fun openFile(path: String) {
        val normalized = path.trim()
        if (normalized.isBlank()) {
            message = "Thermal config path is empty"
            return
        }
        if (!normalized.substringAfterLast('/').startsWith("thermal")) {
            message = "Only thermal*.conf files are accepted"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val result = readRootBytes(normalized)
            withContext(Dispatchers.Main) {
                if (result == null) {
                    message = "Unable to read $normalized"
                } else {
                    currentFile = normalized
                    editorContent = result.first
                    encrypted = result.second
                    editorOpen = true
                    message = "Opened ${normalized.substringAfterLast('/')}"
                }
            }
        }
    }

    fun saveCurrent() {
        val path = currentFile
        if (!editorOpen || path.isBlank()) return
        busy = true
        viewModelScope.launch(Dispatchers.IO) {
            val ok = writeRootBytes(path, editorContent.toByteArray(StandardCharsets.UTF_8), encrypted)
            val verify = if (ok) readRootBytes(path)?.first == normalize(editorContent) else false
            withContext(Dispatchers.Main) {
                busy = false
                message = if (ok && verify) "Saved successfully" else "Save failed or verification failed"
            }
        }
    }

    fun applyCurrent(saveSource: Boolean) {
        val source = currentFile
        if (!editorOpen || source.isBlank()) return
        busy = true
        viewModelScope.launch(Dispatchers.IO) {
            val target = "$THERMAL_CONFIG_DIR/${source.substringAfterLast('/')}"
            val temp = File.createTempFile("zkm-thermal-", ".tmp", null)
            try {
                val data = if (encrypted) {
                    MiuiThermalCrypto.encrypt(editorContent.toByteArray(StandardCharsets.UTF_8))
                } else {
                    editorContent.toByteArray(StandardCharsets.UTF_8)
                }
                temp.writeBytes(data)
                val cp = KernelRootRuntime.executeBlocking(
                    "cp ${shellQuote(temp.absolutePath)} ${shellQuote(target)}",
                    "chmod 664 ${shellQuote(target)}"
                )
                val targetRead = readRootBytes(target)?.first
                val verified = cp.isSuccess && targetRead == normalize(editorContent)
                if (saveSource && source != target) {
                    writeRootBytes(source, editorContent.toByteArray(StandardCharsets.UTF_8), encrypted)
                }
                withContext(Dispatchers.Main) {
                    message = when {
                        verified -> "Thermal configuration applied and verified"
                        !cp.isSuccess -> cp.err.joinToString(" ").ifBlank { "Unable to apply thermal config" }
                        else -> "Config was written, but verification did not match"
                    }
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { message = t.message ?: "Thermal apply failed" }
            } finally {
                temp.delete()
                withContext(Dispatchers.Main) { busy = false }
            }
        }
    }

    fun applyProfile(context: Context, mode: ThermalMode) {
        if (busy) return
        busy = true
        viewModelScope.launch(Dispatchers.IO) {
            val root = extractAssets(context)
            val command = "cd ${shellQuote(root)} && state=${shellQuote(mode.id)} sh kr-script/miui/thermal_conf2/config.sh"
            val result = KernelRootRuntime.executeBlocking(command)
            val details = (result.out + result.err).joinToString("\n").trim()
            withContext(Dispatchers.Main) {
                busy = false
                log = details
                message = if (result.isSuccess) {
                    "Applied ${mode.label}"
                } else {
                    details.ifBlank { "Thermal profile apply failed" }
                }
                refresh(context)
            }
        }
    }

    fun closeEditor() {
        editorOpen = false
    }

    fun clearMessage() {
        message = null
    }

    private fun parseModes(raw: String, platform: String, context: Context): List<ThermalMode> {
        val parsed = raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && it.contains('|') }
            .mapNotNull {
                val parts = it.split('|', limit = 2)
                parts.getOrNull(0)?.takeIf(String::isNotBlank)?.let { id ->
                    val label = parts.getOrNull(1)?.trim().orEmpty().ifBlank { id }
                    ThermalMode(id, label, "Board: $platform")
                }
            }
            .toList()
        if (parsed.isNotEmpty()) return parsed

        val dir = "kr-script/miui/thermal_conf2/$platform"
        return runCatching {
            context.assets.list(dir).orEmpty()
                .filter { name -> name !in setOf("thermal_files.sh", "support.sh", "options.sh", "options.txt") }
                .filter { name -> context.assets.list("$dir/$name").orEmpty().isNotEmpty() }
                .map { ThermalMode(it, it, "Bundled profile") }
        }.getOrDefault(emptyList())
    }

    private fun readRootBytes(path: String): Pair<String, Boolean>? {
        val result = KernelRootRuntime.executeBlocking("base64 ${shellQuote(path)} 2>/dev/null | tr -d '\\n'")
        if (!result.isSuccess || result.outputString.isBlank()) return null
        val bytes = runCatching { Base64.decode(result.outputString.trim(), Base64.DEFAULT) }.getOrNull() ?: return null
        return try {
            String(MiuiThermalCrypto.decrypt(bytes), StandardCharsets.UTF_8).trim() to true
        } catch (_: Throwable) {
            String(bytes, StandardCharsets.UTF_8).trim() to false
        }
    }
    
    private fun writeRootBytes(path: String, plain: ByteArray, encrypted: Boolean): Boolean {
        val data = if (encrypted) MiuiThermalCrypto.encrypt(plain) else plain
        val temp = File.createTempFile("zkm-thermal-write-", ".bin", null)
        return try {
            temp.writeBytes(data)
            val commands = buildList {
                if (path.startsWith("/vendor/") || path.startsWith("/system/")) {
                    add("mount -o rw,remount / 2>/dev/null || true")
                    add("mount -o rw,remount /system 2>/dev/null || true")
                    add("mount -o rw,remount /vendor 2>/dev/null || true")
                    add("busybox mount -o remount,rw /dev/block/bootdevice/by-name/system /system 2>/dev/null || true")
                }
                add("cp ${shellQuote(temp.absolutePath)} ${shellQuote(path)}")
                add("chmod 664 ${shellQuote(path)}")
            }
            KernelRootRuntime.executeBlocking(*commands.toTypedArray()).isSuccess
        } finally {
            temp.delete()
        }
    }

    private fun normalize(value: String): String = value.trim().replace("\r\n", "\n").replace("\r", "\n")

    private fun extractAssets(context: Context): String {
        val root = context.filesDir.resolve("zkm-miui-thermal-master")
        if (root.resolve("kr-script/miui/thermal_conf2/options.sh").exists()) return root.absolutePath
        fun copy(path: String) {
            val target = root.resolve(path)
            val children = context.assets.list(path) ?: emptyArray()
            if (children.isEmpty()) {
                target.parentFile?.mkdirs()
                context.assets.open(path).use { input -> target.outputStream().use { output -> input.copyTo(output) } }
            } else {
                children.forEach { child -> copy("$path/$child") }
            }
        }
        copy("kr-script/miui/thermal_conf2")
        return root.absolutePath
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"
}

/**
 * Generate SOFT TINTED background berdasarkan primary color tema
 */
@Composable
fun generateMiuiThermalBackgroundColors(isDark: Boolean, primaryColor: Color): Pair<Color, Color> = com.zuan.kernelmanager.next.ui.components.ZkmScreenPalette.colors(isDark, primaryColor)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MiuiThermalScreen(
    viewModel: MiuiThermalViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val localDensity = LocalDensity.current
    
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
    val isCustomColor by settingsViewModel.isCustomColor.collectAsStateWithLifecycle()
    val customPrimary by settingsViewModel.customPrimaryColor.collectAsStateWithLifecycle()
    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()

    val effectivePrimary = if (isCustomColor) {
        Color(customPrimary)
    } else {
        MaterialTheme.colorScheme.primary
    }

    val isDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        else -> isSystemInDarkTheme()
    }

    val (themeBgColor, themeCardColor) = if (bgType == BgType.SYSTEM) {
        generateMiuiThermalBackgroundColors(isDarkTheme, effectivePrimary)
    } else {
        Pair(Color.Transparent, Color(0xFFFFFFFF))
    }
    
    val isCustomBg = bgType != BgType.SYSTEM
    val isGlassActive = isHazeEnabled && isCustomBg
    
    val mainBackgroundColor = if (isCustomBg) Color.Transparent else themeBgColor
    
    val textColor = when {
        isGlassActive || isCustomBg -> zkmOnCustomBackgroundContentColor(zkmIsDarkTheme())
        else -> MaterialTheme.colorScheme.onSurface
    }
    
    val subTextColor = when {
        isGlassActive || isCustomBg -> zkmOnCustomBackgroundSubContentColor(zkmIsDarkTheme())
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val finalCardColor = when {
        isGlassActive -> zkmGlassFallbackTint(zkmIsDarkTheme())
        isCustomBg -> zkmCustomBackgroundCardColor(zkmIsDarkTheme(), cardDarkness)
        else -> themeCardColor
    }

    val hazeState = rememberHazeState()
    var customPath by remember { mutableStateOf("") }
    val filesDialog = rememberDialogState(initiallyVisible = false)
    val editorDialog = rememberDialogState(initiallyVisible = false)
    val logDialog = rememberDialogState(initiallyVisible = false)

    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeightDp = with(localDensity) { headerHeightPx.toDp() }

    LaunchedEffect(Unit) { viewModel.refresh(context) }

    Box(modifier = Modifier.fillMaxSize().background(mainBackgroundColor)) {
        
        // --- BACKGROUND LAYER ---
        if (isCustomBg && bgUriString != null) {
            val blurModifier = if (isBgBlur && blurStrength > 0f) Modifier.blur(blurStrength.dp) else if (isBgBlur) Modifier.blur(20.dp) else Modifier
            val colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(bgSaturation) })
            val uri = Uri.parse(bgUriString)

            Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState, zIndex = 0f)) {
                if (isVideo) {
                    VideoWallpaperPlayer(uri = uri, modifier = Modifier.fillMaxSize().then(blurModifier))
                } else {
                    AsyncImage(model = ImageRequest.Builder(context).data(uri).build(), contentDescription = null, modifier = Modifier.fillMaxSize().then(blurModifier), contentScale = ContentScale.Crop, colorFilter = colorFilter)
                }
                if (bgContrast > 0f) Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = bgContrast)))
            }
        } else {
            // Background digambar sebagai child di dalam hazeSource agar terekam
            // ke buffer haze (fix progressive blur appbar di mode non-custom).
            Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState, zIndex = 0f)) {
                Box(modifier = Modifier.fillMaxSize().background(mainBackgroundColor))
            }
        }
        
        WeatherEffectOverlay(effect = weatherEffect, intensity = weatherIntensity, modifier = Modifier.fillMaxSize())

        // --- CONTENT LAYER ---
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0,0,0,0)
        ) { _ ->
            
            Box(modifier = Modifier.fillMaxSize()) {
                
                // LAYER 1: SCROLLABLE CONTENT
                LazyColumn(
                    modifier = Modifier.fillMaxSize().hazeSource(state = hazeState, zIndex = 1f),
                    contentPadding = PaddingValues(top = headerHeightDp, bottom = 100.dp, start = 16.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item {
                        ThermalOverviewCardGlass(
                            supported = viewModel.supported,
                            miui = viewModel.miuiVersion,
                            platform = viewModel.platform,
                            currentMode = viewModel.currentMode,
                            reason = viewModel.supportReason,
                            busy = viewModel.busy,
                            isGlassActive = isGlassActive,
                            hazeState = hazeState,
                            cardColor = finalCardColor,
                            textColor = textColor,
                            subTextColor = subTextColor,
                            primaryColor = effectivePrimary
                        )
                    }
                    item { ThermalSectionHeader(stringResource(R.string.miui_open_configuration), stringResource(R.string.miui_open_configuration_desc), textColor, subTextColor) }
                    item {
                        GlassActionCard(isGlassActive, hazeState, finalCardColor) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = customPath,
                                    onValueChange = { customPath = it },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(17.dp),
                                    leadingIcon = { Icon(Icons.Rounded.Code, null, tint = subTextColor) },
                                    placeholder = { Text("/vendor/etc/thermal-*.conf", color = subTextColor) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedContainerColor = if (isGlassActive) Color.White.copy(0.1f) else MaterialTheme.colorScheme.surface,
                                        focusedContainerColor = if (isGlassActive) Color.White.copy(0.15f) else MaterialTheme.colorScheme.surface,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedBorderColor = effectivePrimary.copy(alpha = 0.5f),
                                        cursorColor = effectivePrimary,
                                        focusedTextColor = textColor,
                                        unfocusedTextColor = textColor
                                    )
                                )
                                Spacer(Modifier.width(8.dp))
                                FilledIconButton(
                                    enabled = customPath.isNotBlank() && !viewModel.busy,
                                    onClick = { viewModel.openFile(customPath) },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = if (isGlassActive) effectivePrimary.copy(0.8f) else MaterialTheme.colorScheme.primary
                                    )
                                ) { Icon(Icons.Rounded.Edit, stringResource(R.string.common_open), tint = Color.White) }
                            }
                        }
                    }

                    item { ThermalSectionHeader(stringResource(R.string.miui_thermal_profiles), stringResource(R.string.miui_thermal_profiles_desc), textColor, subTextColor) }
                    if (viewModel.modes.isEmpty()) {
                        item {
                            ThermalEmptyStateGlass(Icons.Rounded.Thermostat, stringResource(R.string.miui_no_bundled_profiles), stringResource(R.string.miui_no_bundled_profiles_desc), isGlassActive, hazeState, finalCardColor, textColor, subTextColor, effectivePrimary)
                        }
                    } else {
                        items(viewModel.modes, key = { it.id }) { mode ->
                            ThermalModeCardGlass(
                                mode = mode,
                                active = mode.id == viewModel.currentMode,
                                busy = viewModel.busy,
                                onApply = { viewModel.applyProfile(context, mode) },
                                isGlassActive = isGlassActive,
                                hazeState = hazeState,
                                cardColor = finalCardColor,
                                textColor = textColor,
                                subTextColor = subTextColor,
                                primaryColor = effectivePrimary
                            )
                        }
                    }

                    item { ThermalSectionHeader(stringResource(R.string.miui_installed_configs), stringResource(R.string.miui_installed_configs_desc), textColor, subTextColor) }
                    if (viewModel.thermalFiles.isEmpty()) {
                        item { ThermalEmptyStateGlass(Icons.Rounded.Description, stringResource(R.string.miui_no_thermal_configs), stringResource(R.string.miui_no_thermal_configs_desc), isGlassActive, hazeState, finalCardColor, textColor, subTextColor, effectivePrimary) }
                    } else {
                        items(viewModel.thermalFiles, key = { it }) { path ->
                            ThermalFileCardGlass(
                                path = path, 
                                onOpen = { viewModel.openFile(path) },
                                isGlassActive = isGlassActive,
                                hazeState = hazeState,
                                cardColor = finalCardColor,
                                textColor = textColor,
                                subTextColor = subTextColor,
                                primaryColor = effectivePrimary
                            )
                        }
                    }

                    if (viewModel.log.isNotBlank()) {
                        item {
                            OutlinedButton(
                                onClick = { logDialog.visible = true }, 
                                modifier = Modifier.fillMaxWidth(), 
                                shape = RoundedCornerShape(17.dp),
                                border = if (isGlassActive) BorderStroke(1.dp, Color.White.copy(0.5f)) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (isGlassActive) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Rounded.Description, null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.miui_view_last_log))
                            }
                        }
                    }

                    item {
                        Text(
                            "Profiles are deployed through the master thermal_conf2 scripts. The editor supports plaintext and AES/CBC thermal configs, with read-back verification after writes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = subTextColor,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                        )
                    }
                }

                // LAYER 2: TOP HEADER (PROGRESSIVE BLUR)
                val headerHazeStyle = zkmAppBarHazeStyle(themeBgColor, isCustomBg)

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            headerHeightPx = coordinates.size.height
                        }
                        .hazeEffect(
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
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 16.dp, end = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(40.dp), 
                                shape = zkmIconShape(CircleShape), 
                                color = if(isGlassActive) Color.White.copy(0.2f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                            ) {
                                Box(contentAlignment = Alignment.Center) { 
                                    ZkmIconGlyph(icon = Icons.Rounded.Thermostat, tint = if(isGlassActive) Color.White else MaterialTheme.colorScheme.primary, iconSize = 24.dp) 
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.miui_thermal_title), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = textColor)
                                Text(
                                    if (viewModel.supported) "${viewModel.platform.ifBlank { "Unknown board" }} · ${viewModel.modes.size} profiles" else "Configuration editor & profile deployment",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = subTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            ZkmHelpButton(NavigationRoute.MiuiThermal.route, tint = textColor)
                            IconButton(enabled = !viewModel.busy, onClick = { viewModel.refresh(context) }) { 
                                Icon(Icons.Rounded.Refresh, stringResource(R.string.common_refresh), tint = textColor) 
                            }
                            IconButton(onClick = { filesDialog.visible = true }) { 
                                Icon(Icons.Rounded.Description, stringResource(R.string.miui_open_config), tint = textColor) 
                            }
                        }
                        // Spacer fading blur
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    DialogUnstyled(
        state = filesDialog,
        hazeState = hazeState,
        isCustomBg = isCustomBg,
        title = stringResource(R.string.miui_thermal_configurations),
        text = {
            if (viewModel.thermalFiles.isEmpty()) {
                Text(stringResource(R.string.miui_no_files), color = subTextColor)
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(viewModel.thermalFiles, key = { it }) { path ->
                        Card(
                            onClick = { filesDialog.visible = false; viewModel.openFile(path) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(17.dp),
                            colors = CardDefaults.cardColors(containerColor = if(isGlassActive) Color.White.copy(0.1f) else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Description, null, tint = effectivePrimary)
                                Spacer(Modifier.width(10.dp))
                                Text(path, style = MaterialTheme.typography.bodyMedium, color = textColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        dismissButton = { DialogTextButton(text = stringResource(R.string.common_close), onClick = { filesDialog.visible = false }) }
    )

    if (viewModel.editorOpen) {
        LaunchedEffect(viewModel.editorOpen) { editorDialog.visible = viewModel.editorOpen }
        DialogUnstyled(
            state = editorDialog,
            hazeState = hazeState,
            isCustomBg = isCustomBg,
            title = viewModel.currentFile.substringAfterLast('/'),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AssistChip(
                        onClick = {},
                        label = { Text(if (viewModel.encrypted) stringResource(R.string.miui_encrypted_source) else stringResource(R.string.miui_plain_source), color = textColor) },
                        leadingIcon = { Icon(Icons.Rounded.Code, null, Modifier.size(16.dp), tint = textColor) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = if(isGlassActive) Color.White.copy(0.1f) else Color.Transparent)
                    )
                    OutlinedTextField(
                        value = viewModel.editorContent,
                        onValueChange = viewModel::updateEditorContent,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 460.dp),
                        minLines = 14,
                        maxLines = 24,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = if (isGlassActive) Color.White.copy(0.05f) else MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedContainerColor = if (isGlassActive) Color.White.copy(0.1f) else MaterialTheme.colorScheme.surface,
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor
                        )
                    )
                }
            },
            dismissButton = {
                DialogTextButton(text = stringResource(R.string.common_close), onClick = { if (!viewModel.busy) { editorDialog.visible = false; viewModel.closeEditor() } })
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DialogTextButton(icon = Icons.Rounded.Save, text = stringResource(R.string.common_save), onClick = { viewModel.saveCurrent() })
                    FilledTonalButton(
                        enabled = !viewModel.busy, 
                        onClick = { viewModel.applyCurrent(saveSource = true) }, 
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = if(isGlassActive) effectivePrimary.copy(0.8f) else MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Icon(Icons.Rounded.Upload, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.common_apply))
                    }
                }
            }
        )
    }

    LaunchedEffect(viewModel.editorOpen) {
        if (!viewModel.editorOpen) editorDialog.visible = false
    }

    DialogUnstyled(
        state = logDialog,
        hazeState = hazeState,
        isCustomBg = isCustomBg,
        title = stringResource(R.string.miui_operation_log),
        text = {
            SelectionContainer {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = if(isGlassActive) Color.White.copy(0.1f) else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.76f))
                ) {
                    Text(viewModel.log.ifBlank { stringResource(R.string.miui_no_log) }, modifier = Modifier.padding(14.dp), color = textColor, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                }
            }
        },
        dismissButton = { DialogTextButton(text = stringResource(R.string.common_close), onClick = { logDialog.visible = false }) }
    )

    viewModel.message?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2200)
            viewModel.clearMessage()
        }
    }
}

@Composable
fun GlassActionCard(
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    val modifier = if (isGlassActive) {
        Modifier.fillMaxWidth().zkmReferenceGlass(hazeState, shape)
    } else {
        Modifier.fillMaxWidth().clip(shape)
    }
    Card(modifier = modifier, shape = shape, colors = CardDefaults.cardColors(containerColor = if (isGlassActive) zkmReferenceGlassFill() else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f))) {
        content()
    }
}

@Composable
private fun ThermalOverviewCardGlass(
    supported: Boolean, 
    miui: String, 
    platform: String, 
    currentMode: String, 
    reason: String, 
    busy: Boolean,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    textColor: Color,
    subTextColor: Color,
    primaryColor: Color
) {
    val shape = RoundedCornerShape(26.dp)
    val modifier = if (isGlassActive) {
        Modifier.fillMaxWidth().zkmReferenceGlass(hazeState, shape)
    } else {
        Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), shape)
    }
    
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = if(isGlassActive) zkmReferenceGlassFill() else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(50.dp), shape = zkmIconShape(RoundedCornerShape(16.dp)), color = if(isGlassActive) Color.White.copy(0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) {
                    Box(contentAlignment = Alignment.Center) { ZkmIconGlyph(icon = Icons.Rounded.Thermostat, tint = if(isGlassActive) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.primary, iconSize = 28.dp) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.miui_thermal_control), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = textColor)
                    Text(if (supported) stringResource(R.string.miui_ready_profiles) else stringResource(R.string.miui_compatibility_attention), style = MaterialTheme.typography.bodySmall, color = subTextColor)
                }
                AssistChip(
                    onClick = {}, 
                    label = { Text(if (supported) stringResource(R.string.common_supported) else stringResource(R.string.common_limited), color = textColor) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = if(isGlassActive) Color.White.copy(0.15f) else Color.Transparent)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ThermalStatPillGlass("MIUI", miui.ifBlank { "-" }, Modifier.weight(1f), isGlassActive, textColor, subTextColor)
                ThermalStatPillGlass("Board", platform.ifBlank { "-" }, Modifier.weight(1f), isGlassActive, textColor, subTextColor)
                ThermalStatPillGlass("Mode", currentMode.ifBlank { "default" }, Modifier.weight(1f), isGlassActive, textColor, subTextColor)
            }
            if (reason.isNotBlank()) Text(reason, style = MaterialTheme.typography.bodySmall, color = if(isGlassActive) Color(0xFFFF6B6B) else MaterialTheme.colorScheme.error)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = if(isGlassActive) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else primaryColor)
        }
    }
}

@Composable
private fun ThermalStatPillGlass(label: String, value: String, modifier: Modifier = Modifier, isGlassActive: Boolean, textColor: Color, subTextColor: Color) {
    Surface(modifier = modifier, shape = RoundedCornerShape(15.dp), color = if(isGlassActive) Color.White.copy(0.1f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.58f)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = subTextColor)
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ThermalSectionHeader(title: String, subtitle: String, textColor: Color, subTextColor: Color) {
    Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = subTextColor)
    }
}

@Composable
private fun ThermalModeCardGlass(
    mode: ThermalMode, 
    active: Boolean, 
    busy: Boolean, 
    onApply: () -> Unit,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    textColor: Color,
    subTextColor: Color,
    primaryColor: Color
) {
    val shape = RoundedCornerShape(22.dp)
    val modifier = if (isGlassActive) {
        Modifier.fillMaxWidth().zkmReferenceGlass(hazeState, shape).then(if (active) Modifier.zkmCardBorder(1.dp, primaryColor.copy(0.8f), shape) else Modifier).clickable { if (!active && !busy) onApply() }
    } else {
        Modifier.fillMaxWidth().border(1.dp, if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f), shape).clickable { if (!active && !busy) onApply() }
    }
    
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = if (isGlassActive) zkmReferenceGlassFill() else if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.66f) else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f))
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(46.dp), shape = zkmIconShape(RoundedCornerShape(14.dp)), color = if (active) (if(isGlassActive) Color.White.copy(0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) else (if(isGlassActive) Color.White.copy(0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))) {
                Box(contentAlignment = Alignment.Center) { ZkmIconGlyph(icon = if (active) Icons.Rounded.CheckCircle else Icons.Rounded.Upload, tint = if (active) (if(isGlassActive) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.primary) else subTextColor, iconSize = 24.dp) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(mode.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = textColor)
                Text(mode.description.ifBlank { if (mode.id == "default") stringResource(R.string.miui_restore_default) else "Profile: ${mode.id}" }, style = MaterialTheme.typography.bodySmall, color = subTextColor)
            }
            if (active) {
                AssistChip(onClick = {}, label = { Text(stringResource(R.string.common_active), color = textColor) }, leadingIcon = { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = textColor) }, colors = AssistChipDefaults.assistChipColors(containerColor = if(isGlassActive) Color.White.copy(0.15f) else Color.Transparent))
            } else {
                FilledTonalButton(
                    enabled = !busy, 
                    onClick = onApply, 
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = if(isGlassActive) primaryColor.copy(0.8f) else MaterialTheme.colorScheme.secondaryContainer)
                ) { Text(stringResource(R.string.common_apply), color = if(isGlassActive) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.onSecondaryContainer) }
            }
        }
    }
}

@Composable
private fun ThermalEmptyStateGlass(icon: ImageVector, title: String, message: String, isGlassActive: Boolean, hazeState: HazeState, cardColor: Color, textColor: Color, subTextColor: Color, primaryColor: Color) {
    val shape = RoundedCornerShape(24.dp)
    val modifier = if (isGlassActive) {
        Modifier.fillMaxWidth().padding(top = 4.dp).zkmReferenceGlass(hazeState, shape)
    } else {
        Modifier.fillMaxWidth().padding(top = 4.dp)
    }
    
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = if(isGlassActive) zkmReferenceGlassFill() else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(modifier = Modifier.size(56.dp), shape = zkmIconShape(CircleShape), color = if(isGlassActive) Color.White.copy(0.2f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)) {
                Box(contentAlignment = Alignment.Center) { ZkmIconGlyph(icon = icon, tint = if(isGlassActive) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else primaryColor, iconSize = 24.dp) }
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor)
            Text(message, style = MaterialTheme.typography.bodySmall, color = subTextColor, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ThermalFileCardGlass(path: String, onOpen: () -> Unit, isGlassActive: Boolean, hazeState: HazeState, cardColor: Color, textColor: Color, subTextColor: Color, primaryColor: Color) {
    val shape = RoundedCornerShape(20.dp)
    val modifier = if (isGlassActive) {
        Modifier.fillMaxWidth().zkmReferenceGlass(hazeState, shape).clickable(onClick = onOpen)
    } else {
        Modifier.fillMaxWidth().clickable(onClick = onOpen)
    }
    
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = if(isGlassActive) zkmReferenceGlassFill() else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f))
    ) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(44.dp), shape = zkmIconShape(RoundedCornerShape(13.dp)), color = if(isGlassActive) Color.White.copy(0.15f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)) {
                Box(contentAlignment = Alignment.Center) { ZkmIconGlyph(icon = Icons.Rounded.Description, tint = if(isGlassActive) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.secondary, iconSize = 24.dp) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(path.substringAfterLast('/'), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = textColor)
                Text(path, style = MaterialTheme.typography.bodySmall, color = subTextColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = subTextColor)
        }
    }
}
