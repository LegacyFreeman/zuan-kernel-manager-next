/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.dashboard.layout

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * =====================================================================================
 * STATE HOLDER LAYOUT DASHBOARD
 *
 * Strategi: "in-memory first, persist async".
 * - Saat drag berlangsung, urutan diubah di memory (StateFlow) → UI 60fps tanpa I/O.
 * - Persist ke DataStore hanya saat drag SELESAI (onDragEnd) atau toggle widget.
 * - Load dari DataStore sekali saat init.
 * =====================================================================================
 */
class DashboardLayoutViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = DashboardLayoutRepository(application)

    // Urutan penuh SEMUA widget (termasuk yang hidden — posisinya tetap diingat)
    private val _order = MutableStateFlow(DashboardWidgetType.DEFAULT_ORDER)
    val fullOrder: StateFlow<List<DashboardWidgetType>> = _order.asStateFlow()

    // Widget yang disembunyikan
    private val _hidden = MutableStateFlow(DashboardWidgetType.DEFAULT_HIDDEN)
    val hiddenWidgets: StateFlow<Set<DashboardWidgetType>> = _hidden.asStateFlow()

    /** List yang benar-benar dirender di dashboard: order dikurangi yang hidden. */
    val visibleWidgets: StateFlow<List<DashboardWidgetType>> =
        combine(_order, _hidden) { order, hidden -> order.filter { it !in hidden } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = DashboardWidgetType.DEFAULT_ORDER
                    .filter { it !in DashboardWidgetType.DEFAULT_HIDDEN }
            )

    init {
        // Restore layout tersimpan milik user
        viewModelScope.launch {
            val saved = repo.layoutFlow.first()
            _order.value = saved.order
            _hidden.value = saved.hidden
        }
    }

    /**
     * Dipanggil setiap kali card yang di-drag melewati card lain.
     * Index yang diterima adalah index pada list VISIBLE (yang dirender),
     * lalu dikonversi ke posisi pada urutan penuh agar widget hidden tidak rusak posisinya.
     */
    fun moveWidget(fromVisibleIndex: Int, toVisibleIndex: Int) {
        val visible = visibleWidgets.value
        if (fromVisibleIndex !in visible.indices || toVisibleIndex !in visible.indices) return
        if (fromVisibleIndex == toVisibleIndex) return

        val moving = visible[fromVisibleIndex]
        val target = visible[toVisibleIndex]

        _order.update { order ->
            val mutable = order.toMutableList()
            val fromIdx = mutable.indexOf(moving)
            val toIdx = mutable.indexOf(target)
            if (fromIdx == -1 || toIdx == -1) return@update order
            mutable.add(toIdx, mutable.removeAt(fromIdx))
            mutable
        }
    }

    /** Dipanggil saat jari dilepas (drag selesai) → simpan urutan baru. */
    fun persistOrder() {
        viewModelScope.launch { repo.saveOrder(_order.value) }
    }

    /** Toggle tampil/sembunyi widget dari Widget Gallery. */
    fun toggleWidget(widget: DashboardWidgetType) {
        if (!widget.removable) return // widget inti tidak bisa disembunyikan
        _hidden.update { hidden ->
            if (widget in hidden) hidden - widget else hidden + widget
        }
        viewModelScope.launch { repo.saveHidden(_hidden.value) }
    }

    /**
     * Pasang widget (dari menu Add Widget) di posisi [atVisibleIndex] pada daftar yang terlihat.
     * - Widget yang tadinya disembunyikan otomatis ditampilkan lagi.
     * - Index di luar jangkauan → ditaruh paling bawah.
     * Order + hidden disimpan sekaligus dalam satu transaksi.
     */
    fun addWidget(widget: DashboardWidgetType, atVisibleIndex: Int) {
        val visibleBefore = visibleWidgets.value.filter { it != widget }
        val anchor = visibleBefore.getOrNull(atVisibleIndex)

        val newOrder = _order.value.toMutableList().apply {
            remove(widget)
            val anchorIdx = if (anchor != null) indexOf(anchor) else -1
            if (anchorIdx >= 0) {
                add(anchorIdx, widget)
            } else {
                // Taruh setelah widget terlihat paling akhir (atau paling bawah bila kosong)
                val lastVisibleIdx = visibleBefore.lastOrNull()?.let { indexOf(it) } ?: -1
                add(if (lastVisibleIdx >= 0) lastVisibleIdx + 1 else size, widget)
            }
        }
        val newHidden = _hidden.value - widget

        _order.value = newOrder
        _hidden.value = newHidden
        viewModelScope.launch { repo.saveLayout(newOrder, newHidden) }
    }

    /** Kembalikan layout ke default pabrik. */
    fun resetLayout() {
        _order.value = DashboardWidgetType.DEFAULT_ORDER
        _hidden.value = DashboardWidgetType.DEFAULT_HIDDEN
        viewModelScope.launch { repo.reset() }
    }
}
