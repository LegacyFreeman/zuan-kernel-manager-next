/*
 * Copyright (c) 2026 ZKM Next
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package com.zuan.kernelmanager.next.feature.update

import com.zuan.kernelmanager.next.ui.components.zkmLiteAlpha
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.zuan.kernelmanager.next.BuildConfig
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.feature.about.CardPadding
import com.zuan.kernelmanager.next.feature.about.ThemedExpressiveCard
import com.zuan.kernelmanager.next.feature.about.ThemedIconContainer
import com.zuan.kernelmanager.next.feature.about.ThemedSectionTitle
import com.zuan.kernelmanager.next.feature.about.changelogs.ChangelogsUtils
import com.zuan.kernelmanager.next.feature.settings.BgType
import com.zuan.kernelmanager.next.feature.settings.SettingsSwitchItem
import com.zuan.kernelmanager.next.feature.settings.SettingsViewModel
import com.zuan.kernelmanager.next.ui.components.VideoWallpaperPlayer
import com.zuan.kernelmanager.next.ui.components.WeatherEffectOverlay
import com.zuan.kernelmanager.next.ui.components.ZkmScreenPalette
import com.zuan.kernelmanager.next.ui.components.zkmGlassFallbackTint
import com.zuan.kernelmanager.next.ui.navigation.NavigationRoute
import com.zuan.kernelmanager.next.ui.theme.ThemeMode
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import dev.chrisbanes.haze.HazeState
import com.zuan.kernelmanager.next.ui.components.zkmHazeSource
import kotlinx.coroutines.launch

private sealed interface CheckUiState {
    data object Idle : CheckUiState
    data object Checking : CheckUiState
    data class Done(val result: UpdateCheckResult) : CheckUiState
}

@Composable
fun UpdateSettingsScreen(
    navController: NavController,
    hazeState: HazeState,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // ---- theme (same sources as About, so wallpaper / glass / colours match) ----
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val isSystemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM_DEFAULT -> isSystemDark
    }
    val isDynamic by viewModel.isDynamicColor.collectAsStateWithLifecycle()
    val themeColor by viewModel.currentThemeColor.collectAsStateWithLifecycle()
    val isCustomColor by viewModel.isCustomColor.collectAsStateWithLifecycle()
    val customPrimary by viewModel.customPrimaryColor.collectAsStateWithLifecycle()
    val bgType by viewModel.bgType.collectAsStateWithLifecycle()
    val bgUri by viewModel.backgroundImageUri.collectAsStateWithLifecycle()
    val isVideo by viewModel.isVideoWallpaper.collectAsStateWithLifecycle()
    val isBgBlur by viewModel.isBgBlur.collectAsStateWithLifecycle()
    val blurStrength by viewModel.blurStrength.collectAsStateWithLifecycle()
    val bgSaturation by viewModel.bgSaturation.collectAsStateWithLifecycle()
    val bgContrast by viewModel.bgContrast.collectAsStateWithLifecycle()
    val isHazeEnabled by viewModel.isHazeEnabled.collectAsStateWithLifecycle()
    val cardDarkness by viewModel.cardDarkness.collectAsStateWithLifecycle()
    val weatherEffect by viewModel.weatherEffect.collectAsStateWithLifecycle()
    val weatherIntensity by viewModel.weatherIntensity.collectAsStateWithLifecycle()

    val accent = when {
        isCustomColor -> Color(customPrimary)
        isDynamic -> MaterialTheme.colorScheme.primary
        else -> themeColor.primary
    }
    val (tintedBackground, themeCardColor) = ZkmScreenPalette.colors(dark, accent)
    val isCustomBg = bgType != BgType.SYSTEM
    val isGlass = isHazeEnabled && isCustomBg
    val cardColor = when {
        isGlass -> zkmGlassFallbackTint(zkmIsDarkTheme())
        isCustomBg -> MaterialTheme.colorScheme.surface.zkmLiteAlpha(cardDarkness)
        else -> themeCardColor
    }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val divider = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    // ---- settings state ----
    val checkManager by viewModel.checkManagerUpdates.collectAsStateWithLifecycle()
    val checkBeta by viewModel.checkBetaUpdates.collectAsStateWithLifecycle()
    val showDialog by viewModel.showUpdateDialog.collectAsStateWithLifecycle()
    val lastCheck by viewModel.lastUpdateCheck.collectAsStateWithLifecycle()

    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "?"
    }
    val channel = if (versionName.contains('-')) stringResource(R.string.upd_channel_beta) else stringResource(R.string.upd_channel_stable)
    val buildKind = if (BuildConfig.DEBUG) "Debug" else "Release"

    var checkState by remember { mutableStateOf<CheckUiState>(CheckUiState.Idle) }
    var dialogInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var cacheSize by remember { mutableLongStateOf(UpdateChecker.downloadedUpdatesSize(context)) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = if (isCustomBg) Color.Transparent else tintedBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.upd_settings_title),
                        fontWeight = FontWeight.Bold,
                        color = onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = onSurface)
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // wallpaper layer
            Box(modifier = Modifier.fillMaxSize().zkmHazeSource(state = hazeState, zIndex = 0f)) {
                if (isCustomBg && bgUri != null) {
                    val blurMod = when {
                        isBgBlur && blurStrength > 0f -> Modifier.blur(blurStrength.dp)
                        isBgBlur -> Modifier.blur(20.dp)
                        else -> Modifier
                    }
                    val uri = Uri.parse(bgUri)
                    if (isVideo) {
                        VideoWallpaperPlayer(uri = uri, modifier = Modifier.fillMaxSize().then(blurMod))
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(uri).build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().then(blurMod),
                            contentScale = ContentScale.Crop,
                            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(bgSaturation) })
                        )
                    }
                    if (bgContrast > 0f) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = bgContrast)))
                    }
                }
                WeatherEffectOverlay(effect = weatherEffect, intensity = weatherIntensity, modifier = Modifier.fillMaxSize())
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .zkmHazeSource(state = hazeState, zIndex = 1f)
                    .verticalScroll(scroll)
                    .padding(horizontal = 16.dp)
            ) {
                // ================= THIS BUILD =================
                ThemedSectionTitle(stringResource(R.string.upd_section_build), accent)
                Spacer(Modifier.height(8.dp))
                ThemedExpressiveCard(containerColor = cardColor, isGlassActive = isGlass, hazeState = hazeState) {
                    Column {
                        InfoRow(Icons.Outlined.Info, stringResource(R.string.upd_row_version), versionName, accent, onSurface, onSurfaceVariant)
                        HorizontalDivider(color = divider, thickness = 0.8.dp)
                        InfoRow(Icons.Outlined.AltRoute, stringResource(R.string.upd_row_channel), channel, accent, onSurface, onSurfaceVariant)
                        HorizontalDivider(color = divider, thickness = 0.8.dp)
                        InfoRow(Icons.Outlined.Build, stringResource(R.string.upd_row_build), buildKind, accent, onSurface, onSurfaceVariant)
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ================= CHECK NOW =================
                ThemedSectionTitle(stringResource(R.string.upd_section_status), accent)
                Spacer(Modifier.height(8.dp))
                ThemedExpressiveCard(containerColor = cardColor, isGlassActive = isGlass, hazeState = hazeState) {
                    Column(Modifier.fillMaxWidth().padding(CardPadding)) {
                        val state = checkState
                        val headline = when {
                            state is CheckUiState.Checking -> stringResource(R.string.upd_status_checking)
                            state is CheckUiState.Done && state.result is UpdateCheckResult.Available ->
                                stringResource(R.string.upd_status_available, (state.result as UpdateCheckResult.Available).info.version)
                            state is CheckUiState.Done && state.result is UpdateCheckResult.UpToDate -> stringResource(R.string.upd_status_uptodate)
                            state is CheckUiState.Done && state.result is UpdateCheckResult.Error -> stringResource(R.string.upd_status_error)
                            else -> stringResource(R.string.upd_status_idle)
                        }
                        val detail = when {
                            state is CheckUiState.Done && state.result is UpdateCheckResult.Error -> (state.result as UpdateCheckResult.Error).message
                            lastCheck > 0L -> stringResource(
                                R.string.upd_last_checked,
                                DateUtils.getRelativeTimeSpanString(lastCheck, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
                            )
                            else -> stringResource(R.string.upd_never_checked)
                        }
                        Text(headline, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp), color = onSurface)
                        Spacer(Modifier.height(2.dp))
                        Text(detail, style = MaterialTheme.typography.bodyMedium, color = onSurfaceVariant)
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    checkState = CheckUiState.Checking
                                    scope.launch {
                                        val r = UpdateChecker.check(context, manual = true)
                                        checkState = CheckUiState.Done(r)
                                    }
                                },
                                enabled = checkState !is CheckUiState.Checking,
                                colors = ButtonDefaults.buttonColors(containerColor = accent),
                                shape = RoundedCornerShape(50)
                            ) {
                                if (checkState is CheckUiState.Checking) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(stringResource(R.string.upd_btn_check), color = Color.White)
                            }
                            val done = checkState as? CheckUiState.Done
                            val available = (done?.result as? UpdateCheckResult.Available)?.info
                            if (available != null) {
                                OutlinedButton(onClick = { dialogInfo = available }, shape = RoundedCornerShape(50)) {
                                    Text(stringResource(R.string.upd_btn_view), color = accent)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ================= AUTOMATIC CHECKS =================
                ThemedSectionTitle(stringResource(R.string.upd_section_auto), accent)
                Spacer(Modifier.height(8.dp))
                ThemedExpressiveCard(containerColor = cardColor, isGlassActive = isGlass, hazeState = hazeState) {
                    Column {
                        SettingsSwitchItem(
                            icon = Icons.Outlined.SystemUpdate,
                            title = stringResource(R.string.pref_check_manager_updates),
                            subtitle = stringResource(R.string.pref_check_manager_updates_desc),
                            checked = checkManager,
                            onCheckedChange = { viewModel.setCheckManagerUpdates(it) },
                            accentColor = accent,
                            iconTint = accent
                        )
                        HorizontalDivider(color = divider, thickness = 0.8.dp)
                        SettingsSwitchItem(
                            icon = Icons.Outlined.Science,
                            title = stringResource(R.string.pref_check_beta_updates),
                            subtitle = stringResource(R.string.pref_check_beta_updates_desc),
                            checked = checkBeta,
                            onCheckedChange = { viewModel.setCheckBetaUpdates(it) },
                            accentColor = accent,
                            iconTint = accent
                        )
                        HorizontalDivider(color = divider, thickness = 0.8.dp)
                        SettingsSwitchItem(
                            icon = Icons.Outlined.Campaign,
                            title = stringResource(R.string.upd_pref_dialog),
                            subtitle = stringResource(R.string.upd_pref_dialog_desc),
                            checked = showDialog,
                            onCheckedChange = { viewModel.setShowUpdateDialog(it) },
                            accentColor = accent,
                            iconTint = accent
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ================= STORAGE =================
                ThemedSectionTitle(stringResource(R.string.upd_section_storage), accent)
                Spacer(Modifier.height(8.dp))
                ThemedExpressiveCard(
                    containerColor = cardColor,
                    isGlassActive = isGlass,
                    hazeState = hazeState,
                    onClick = {
                        val n = UpdateChecker.clearDownloadedUpdates(context)
                        cacheSize = UpdateChecker.downloadedUpdatesSize(context)
                        Toast.makeText(context, context.getString(R.string.upd_cleared, n), Toast.LENGTH_SHORT).show()
                    }
                ) {
                    LinkRow(
                        icon = Icons.Outlined.DeleteSweep,
                        title = stringResource(R.string.upd_clear_title),
                        subtitle = stringResource(R.string.upd_clear_desc, Formatter.formatShortFileSize(context, cacheSize)),
                        accent = accent, onSurface = onSurface, onSurfaceVariant = onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(24.dp))

                // ================= LINKS =================
                ThemedSectionTitle(stringResource(R.string.upd_section_links), accent)
                Spacer(Modifier.height(8.dp))
                ThemedExpressiveCard(containerColor = cardColor, isGlassActive = isGlass, hazeState = hazeState) {
                    Column {
                        ClickRow(
                            Icons.Outlined.History, stringResource(R.string.upd_link_changelog), stringResource(R.string.upd_link_changelog_desc),
                            accent, onSurface, onSurfaceVariant
                        ) { navController.navigate(NavigationRoute.Changelogs.route) }
                        HorizontalDivider(color = divider, thickness = 0.8.dp)
                        ClickRow(
                            Icons.Outlined.Code, stringResource(R.string.upd_link_releases), "github.com/zuanvfx02/zuan-kernel-manager-next",
                            accent, onSurface, onSurfaceVariant
                        ) { openUrl(context, ChangelogsUtils.RELEASES_URL) }
                        HorizontalDivider(color = divider, thickness = 0.8.dp)
                        ClickRow(
                            Icons.Outlined.Send, stringResource(R.string.upd_link_channel), "@zuankernelmanagernext",
                            accent, onSurface, onSurfaceVariant
                        ) { openUrl(context, ChangelogsUtils.TELEGRAM_CHANNEL_URL) }
                    }
                }

                Spacer(Modifier.height(120.dp))
            }
        }
    }

    dialogInfo?.let { info ->
        UpdateDialog(
            updateInfo = info,
            hazeState = hazeState,
            onDismiss = { dialogInfo = null },
            onUpdateClick = {
                val id = UpdateChecker.downloadUpdate(context, info)
                UpdateChecker.registerInstallReceiver(context, id, info.sha256)
            }
        )
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String,
    accent: Color, onSurface: Color, onSurfaceVariant: Color
) {
    Row(Modifier.fillMaxWidth().padding(CardPadding), verticalAlignment = Alignment.CenterVertically) {
        ThemedIconContainer(icon = icon, primaryColor = accent)
        Spacer(Modifier.width(16.dp))
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp), color = onSurface)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = onSurfaceVariant)
    }
}

@Composable
private fun LinkRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String,
    accent: Color, onSurface: Color, onSurfaceVariant: Color
) {
    Row(Modifier.fillMaxWidth().padding(CardPadding), verticalAlignment = Alignment.CenterVertically) {
        ThemedIconContainer(icon = icon, primaryColor = accent)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp), color = onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = onSurfaceVariant, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun ClickRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String,
    accent: Color, onSurface: Color, onSurfaceVariant: Color, onClick: () -> Unit
) {
    Box(Modifier.fillMaxWidth().clickableNoRipple(onClick)) {
        LinkRow(icon, title, subtitle, accent, onSurface, onSurfaceVariant)
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))

private fun openUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
