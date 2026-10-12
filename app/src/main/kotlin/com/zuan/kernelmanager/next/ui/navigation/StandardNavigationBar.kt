/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.ui.navigation

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.zuan.kernelmanager.next.ui.theme.zkmIconShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.feature.settings.BgType
import com.zuan.kernelmanager.next.feature.settings.NavLabelState
import com.zuan.kernelmanager.next.feature.settings.SettingsPreference
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun StandardNavigationBar(
    navController: NavController,
    hazeState: HazeState,
    labelState: NavLabelState
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val context = LocalContext.current
    val prefs = remember { SettingsPreference.getInstance(context) }
    val bgType by prefs.bgType.collectAsState()
    val isCustomBg = bgType != BgType.SYSTEM

    val items = listOf(
        NavigationRoute.Overall,
        NavigationRoute.Dashboard,
        NavigationRoute.SoC,
        NavigationRoute.About
    )

    NavigationBar(
        containerColor = Color.Transparent,
        // [FIX] HazeMaterials.thin() statis: tidak mengikuti dark/light app
        // maupun custom background. Pakai style theme-aware yang sama dengan
        // app bar (tanpa progressive, bar kecil).
        modifier = Modifier.hazeEffect(
            state = hazeState,
            style = com.zuan.kernelmanager.next.ui.components.zkmAppBarHazeStyle(
                tintedBackground = MaterialTheme.colorScheme.surface,
                isCustomBackground = isCustomBg
            )
        )
    ) {
        items.forEach { item ->
            val isSelected = currentDestination?.route == item.route
            
            val showLabel = when (labelState) {
                NavLabelState.ALWAYS_SHOW -> true
                NavLabelState.ALWAYS_HIDE -> false
                NavLabelState.ONLY_SELECTED -> isSelected
            }

            val labelText = when(item) {
                NavigationRoute.Overall -> stringResource(R.string.nav_home)
                NavigationRoute.Dashboard -> stringResource(R.string.nav_home)
                NavigationRoute.SoC -> stringResource(R.string.nav_soc)
                NavigationRoute.About -> stringResource(R.string.nav_about)
                else -> ""
            }

            NavigationBarItem(
                icon = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(zkmIconShape(RoundedCornerShape(14.dp)))
                            .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Icon(
                            if (isSelected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = labelText,
                            tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                label = if (showLabel) { { Text(text = labelText) } } else null,
                selected = isSelected,
                onClick = {
                    if (currentDestination?.route != item.route) {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                alwaysShowLabel = showLabel
            )
        }
    }
}
