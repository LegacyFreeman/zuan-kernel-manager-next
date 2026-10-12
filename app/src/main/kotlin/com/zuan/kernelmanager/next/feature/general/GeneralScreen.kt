/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.zuan.kernelmanager.next.feature.general
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.ui.navigation.NavigationRoute
import com.zuan.kernelmanager.next.feature.help.ZkmHelpButton
import com.zuan.kernelmanager.next.feature.settings.NavLabelState
import com.zuan.kernelmanager.next.feature.settings.NavStyle
import com.zuan.kernelmanager.next.feature.settings.SettingsViewModel
import com.zuan.kernelmanager.next.ui.theme.ThemeMode
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi

/**
 * Generate SOFT TINTED background berdasarkan primary color tema (SAMA PERSIS AboutScreen)
 */
@Composable
fun generateThemedBackgroundColor(primaryColor: Color, isDark: Boolean): Pair<Color, Color> = com.zuan.kernelmanager.next.ui.components.ZkmScreenPalette.colors(isDark, primaryColor)

@Composable
fun GeneralScreen(
    navController: NavController,
    viewModel: SettingsViewModel = viewModel()
) {
    val navStyle by viewModel.navStyle.collectAsState()
    val navLabelState by viewModel.navLabelState.collectAsState()
    
    val themeMode by viewModel.themeMode.collectAsState()
    val isDynamic by viewModel.isDynamicColor.collectAsState()
    val themeColorName by viewModel.currentThemeColor.collectAsState()
    val isCustomColor by viewModel.isCustomColor.collectAsState()
    val customPrimary by viewModel.customPrimaryColor.collectAsState()

    val useDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM_DEFAULT -> isSystemInDarkTheme()
    }
    
    val effectivePrimary = when {
        isCustomColor -> Color(customPrimary)
        isDynamic -> MaterialTheme.colorScheme.primary
        else -> themeColorName.primary
    }

    val (tintedBackground, cardContainerColor) = generateThemedBackgroundColor(effectivePrimary, useDarkTheme)
    
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lp_general), color = onSurfaceColor) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }
        ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.lp_a11y_back), tint = onSurfaceColor)
                    }
                },
                actions = {
                    ZkmHelpButton(NavigationRoute.General.route, tint = onSurfaceColor)
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = onSurfaceColor,
                    navigationIconContentColor = onSurfaceColor
                )
            )
        },
        containerColor = tintedBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            
            Text(
                text = "Navigation Mode",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    fontSize = 13.sp
                ),
                color = effectivePrimary.copy(alpha = 0.9f),
                modifier = Modifier.padding(bottom = 16.dp, start = 4.dp)
            )

            // --- PILIHAN NAVIGASI ---
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                
                // BARIS 1: Capsule iOS 1 & Bottom Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    NavigationPreviewCard(
                        modifier = Modifier.weight(1f),
                        title = "Capsule iOS 1",
                        imageRes = R.drawable.navigation_capsule,
                        isSelected = navStyle == NavStyle.LIQUID_CAPSULE,
                        cardColor = cardContainerColor,
                        primaryColor = effectivePrimary,
                        onClick = { viewModel.setNavStyle(NavStyle.LIQUID_CAPSULE) }
                    )

                    NavigationPreviewCard(
                        modifier = Modifier.weight(1f),
                        title = "Bottom Bar",
                        imageRes = R.drawable.navigation_bottom_bar,
                        isSelected = navStyle == NavStyle.CLASSIC_MATERIAL,
                        cardColor = cardContainerColor,
                        primaryColor = effectivePrimary,
                        onClick = { viewModel.setNavStyle(NavStyle.CLASSIC_MATERIAL) }
                    )
                }

                // BARIS 2: Hybrid Tabs & Capsule iOS 2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    NavigationPreviewCard(
                        modifier = Modifier.weight(1f), 
                        title = "Hybrid Tabs",
                        imageRes = R.drawable.navigation_hybird_tabs,
                        isSelected = navStyle == NavStyle.MODERN_TABS,
                        badge = "BETA",
                        cardColor = cardContainerColor,
                        primaryColor = effectivePrimary,
                        onClick = { viewModel.setNavStyle(NavStyle.MODERN_TABS) }
                    )
                    
                    NavigationPreviewCard(
                        modifier = Modifier.weight(1f), 
                        title = "Capsule iOS 2",
                        imageRes = R.drawable.navigation_capsule, 
                        isSelected = navStyle == NavStyle.LIQUID_CAPSULE_2,
                        badge = "NEW",
                        cardColor = cardContainerColor,
                        primaryColor = effectivePrimary,
                        onClick = { viewModel.setNavStyle(NavStyle.LIQUID_CAPSULE_2) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- LABEL BEHAVIOR SETTINGS (Sekarang Selalu Tampil) ---
            Text(
                text = "Label Behavior",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    fontSize = 13.sp
                ),
                color = effectivePrimary.copy(alpha = 0.9f),
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = cardContainerColor),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column {
                    LabelOptionItem("Always Show", navLabelState == NavLabelState.ALWAYS_SHOW, effectivePrimary) { viewModel.setNavLabelState(NavLabelState.ALWAYS_SHOW) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    LabelOptionItem("Only Selected", navLabelState == NavLabelState.ONLY_SELECTED, effectivePrimary) { viewModel.setNavLabelState(NavLabelState.ONLY_SELECTED) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    LabelOptionItem("Always Hide", navLabelState == NavLabelState.ALWAYS_HIDE, effectivePrimary) { viewModel.setNavLabelState(NavLabelState.ALWAYS_HIDE) }
                }
            }
            
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun NavigationPreviewCard(
    modifier: Modifier = Modifier, title: String, imageRes: Int, isSelected: Boolean, cardColor: Color, primaryColor: Color, badge: String? = null, onClick: () -> Unit
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .aspectRatio(0.6f) 
                .clip(RoundedCornerShape(16.dp))
                .border(if (isSelected) 3.dp else 1.dp, if (isSelected) primaryColor else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .background(cardColor)
                .clickable(onClick = onClick)
        ) {
            Image(painter = painterResource(id = imageRes), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (badge != null) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(topEnd = 16.dp, bottomStart = 8.dp), modifier = Modifier.align(Alignment.TopEnd)) {
                    Text(text = badge, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp), color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            if (isSelected) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp).background(primaryColor, CircleShape).padding(6.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, fontSize = 17.sp), color = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun LabelOptionItem(text: String, isSelected: Boolean, primaryColor: Color, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        if (isSelected) Icon(Icons.Default.Check, null, tint = primaryColor)
    }
}
