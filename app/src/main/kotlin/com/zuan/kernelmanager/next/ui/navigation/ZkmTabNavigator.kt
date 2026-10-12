/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.ui.navigation

import androidx.navigation.NavController
import com.zuan.kernelmanager.next.feature.soc.SoCTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pusat navigasi "pindah tab" dari layar mana pun (mis. kartu di Overall).
 *
 * Kenapa perlu: sebelumnya kartu Overall memanggil navController.navigate(SoC.route) polos.
 *  - Di mode MODERN_TABS (pager swipe) itu mendorong SoC sebagai layar terpisah TANPA nav bar,
 *    jadi satu-satunya jalan balik adalah tombol back sistem.
 *  - Di mode bottom bar, back stack jadi [Overall, SoC] tanpa saveState/restoreState sehingga
 *    perilakunya beda dari pindah tab biasa.
 *
 * Sekarang semua lewat sini: kalau sedang di MainSwipeContainer -> geser pager, selain itu ->
 * pakai pola pindah-tab standar yang sama persis dengan nav bar bawah.
 */
object ZkmTabNavigator {

    private val _pendingSocTab = MutableStateFlow<SoCTab?>(null)
    /** Sub-tab SoC yang diminta dibuka (CpuGpu / Sched / Memory / Network). Dikonsumsi SoCScreen. */
    val pendingSocTab: StateFlow<SoCTab?> = _pendingSocTab.asStateFlow()

    private val _pendingMainRoute = MutableStateFlow<String?>(null)
    /** Route tab utama yang diminta dibuka di mode pager. Dikonsumsi MainSwipeScreen. */
    val pendingMainRoute: StateFlow<String?> = _pendingMainRoute.asStateFlow()

    fun consumeSocTab() { _pendingSocTab.value = null }
    fun consumeMainRoute() { _pendingMainRoute.value = null }

    /** Pindah ke salah satu dari 4 tab utama (Overall / Dashboard / SoC / About). */
    fun openMainTab(navController: NavController, route: String) {
        val current = navController.currentDestination?.route
        when {
            // Mode swipe: semua tab ada di dalam satu pager, cukup geser halamannya.
            current == NavigationRoute.MainSwipeContainer.route -> _pendingMainRoute.value = route
            current == route -> Unit
            else -> navController.navigate(route) {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    /** Buka tab SoC sekaligus langsung ke sub-tab tertentu. */
    fun openSocTab(navController: NavController, tab: SoCTab) {
        _pendingSocTab.value = tab
        openMainTab(navController, NavigationRoute.SoC.route)
    }
}
