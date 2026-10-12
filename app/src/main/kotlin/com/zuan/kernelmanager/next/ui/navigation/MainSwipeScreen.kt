/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.ui.adaptive.LocalZkmWindow
import com.zuan.kernelmanager.next.ui.adaptive.ZkmNavigationRail
import com.zuan.kernelmanager.next.ui.adaptive.ZkmRailItem
import com.zuan.kernelmanager.next.ui.adaptive.ZkmRailWidth
import com.zuan.kernelmanager.next.ui.adaptive.zkmHorizontalSafeInsets
import com.zuan.kernelmanager.next.ui.adaptive.zkmStartInset
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import com.zuan.kernelmanager.next.feature.about.AboutScreen
import com.zuan.kernelmanager.next.feature.home.HomeScreen
import com.zuan.kernelmanager.next.feature.dashboard.OverallScreen
import com.zuan.kernelmanager.next.feature.soc.SoCScreen
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch

@Composable
fun MainSwipeScreen(
    navController: NavController,
    hazeState: HazeState,
    onOpenDialog: (ActiveDialog) -> Unit
) {
    val tabs = listOf(
        NavigationRoute.Overall,
        NavigationRoute.Dashboard,
        NavigationRoute.SoC,
        NavigationRoute.About
    )

    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    // Permintaan pindah tab dari layar lain (mis. kartu Overall -> tab SoC) tanpa meninggalkan pager.
    val pendingRoute by ZkmTabNavigator.pendingMainRoute.collectAsState()
    LaunchedEffect(pendingRoute) {
        val route = pendingRoute ?: return@LaunchedEffect
        val index = tabs.indexOfFirst { it.route == route }
        if (index >= 0 && index != pagerState.currentPage) {
            pagerState.animateScrollToPage(index)
        }
        ZkmTabNavigator.consumeMainRoute()
    }

    val isWide = LocalZkmWindow.current.isWide

    Box(modifier = Modifier.fillMaxSize()) {
      // Landscape: jauhkan dari cutout kamera / bilah nav samping.
      Box(
          modifier = Modifier
              .fillMaxSize()
              .then(
                  if (isWide) Modifier.zkmHorizontalSafeInsets() else Modifier
              )
      ) {
        // LAYER 1: Pager Konten (Bisa scroll meluncur ke balik TopBar)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // Padding atas pas agar tidak memotong layar & tidak ada celah raksasa
            contentPadding = PaddingValues(top = 64.dp),
            beyondViewportPageCount = 1, 
            userScrollEnabled = true
        ) { page ->
            when (tabs[page]) {
                NavigationRoute.Overall -> OverallScreen(
                    navController = navController, 
                    hazeState = hazeState
                )
                NavigationRoute.Dashboard -> HomeScreen(
                    navController = navController, 
                    hazeState = hazeState
                )
                NavigationRoute.SoC -> SoCScreen(
                    navController = navController, 
                    hazeState = hazeState,
                    onParentSignal = { direction ->
                        scope.launch {
                            val targetPage = pagerState.currentPage + direction
                            if (targetPage in 0 until tabs.size) {
                                pagerState.animateScrollToPage(targetPage)
                            }
                        }
                    }
                )
                NavigationRoute.About -> AboutScreen(
                    navController = navController, 
                    hazeState = hazeState
                )
                else -> {}
            }
        }

        // LAYER 2: ModernSwipeableNavBar Melayang di Atas
        ModernSwipeableNavBar(
            tabs = tabs,
            pagerState = pagerState,
            hazeState = hazeState, // Terhubung langsung ke backgroundHazeState
            onTabSelected = { index ->
                scope.launch { pagerState.animateScrollToPage(index) }
            },
            navController = navController,
            onOpenDialog = onOpenDialog
        )
      }

    }
}
