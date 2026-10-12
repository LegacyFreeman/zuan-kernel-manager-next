/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.dashboard.layout

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.zuan.kernelmanager.next.core.persistence.ZkmDashboardDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// DataStore khusus layout dashboard, terpisah dari settings lain.

/**
 * Snapshot layout dashboard milik user.
 *
 * @param order  Urutan SEMUA widget (termasuk yang disembunyikan).
 * @param hidden Widget yang sedang disembunyikan dari dashboard.
 */
data class DashboardLayout(
    val order: List<DashboardWidgetType>,
    val hidden: Set<DashboardWidgetType>
)

/**
 * =====================================================================================
 * PERSISTENCE LAYER
 * Menyimpan urutan & visibilitas widget ke DataStore sebagai string CSV enum name.
 * Contoh tersimpan: "CPU_MONITOR,MEMORY,DEVICE_HERO,..."
 *
 * Aman terhadap update aplikasi:
 * - Widget baru yang belum ada di data tersimpan otomatis di-append di akhir.
 * - Nama enum yang sudah dihapus dari registry otomatis di-skip (mapNotNull).
 * =====================================================================================
 */
class DashboardLayoutRepository(private val context: Context) {

    private val keyOrder = stringPreferencesKey("widget_order")
    private val keyHidden = stringPreferencesKey("widget_hidden")

    // Daftar widget yang sudah "dikenal" saat terakhir menyimpan. Dipakai agar widget BARU
    // (mis. widget tambahan hasil update app) yang defaultVisible=false tetap tersembunyi di
    // user lama, walau user itu sudah pernah menyimpan layout sebelum widget tersebut ada.
    private val keyKnown = stringPreferencesKey("widget_known")

    private fun allKnownCsv(): String = DashboardWidgetType.entries.joinToString(",") { it.name }

    /** Flow layout — emit ulang setiap ada perubahan tersimpan. */
    val layoutFlow: Flow<DashboardLayout> = ZkmDashboardDataStore.get(context).data.map { prefs ->
        val savedOrder = prefs[keyOrder]
            ?.split(",")
            ?.mapNotNull { name -> runCatching { DashboardWidgetType.valueOf(name) }.getOrNull() }
            ?: DashboardWidgetType.DEFAULT_ORDER

        // Widget baru (hasil update app) yang belum pernah tersimpan → append di akhir
        val fullOrder = savedOrder + DashboardWidgetType.entries.filter { it !in savedOrder }

        val savedHidden = prefs[keyHidden]
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.mapNotNull { name -> runCatching { DashboardWidgetType.valueOf(name) }.getOrNull() }
            ?.toSet()

        // Data lama (sebelum ada widget tambahan) = semua widget non-extra yang dikenal.
        val known = prefs[keyKnown]
            ?.split(",")
            ?.mapNotNull { name -> runCatching { DashboardWidgetType.valueOf(name) }.getOrNull() }
            ?.toSet()
            ?: DashboardWidgetType.entries.filter { !it.isExtra }.toSet()
        val unseenHiddenByDefault = DashboardWidgetType.entries
            .filter { it !in known && !it.defaultVisible }

        val hidden = (savedHidden ?: DashboardWidgetType.DEFAULT_HIDDEN) + unseenHiddenByDefault

        DashboardLayout(order = fullOrder, hidden = hidden)
    }

    suspend fun saveOrder(order: List<DashboardWidgetType>) {
        ZkmDashboardDataStore.get(context).edit { prefs ->
            prefs[keyOrder] = order.joinToString(",") { it.name }
            prefs[keyKnown] = allKnownCsv()
        }
    }

    suspend fun saveHidden(hidden: Set<DashboardWidgetType>) {
        ZkmDashboardDataStore.get(context).edit { prefs ->
            prefs[keyHidden] = hidden.joinToString(",") { it.name }
            prefs[keyKnown] = allKnownCsv()
        }
    }

    /** Simpan urutan dan visibilitas sekaligus (satu transaksi) — dipakai saat Add Widget. */
    suspend fun saveLayout(order: List<DashboardWidgetType>, hidden: Set<DashboardWidgetType>) {
        ZkmDashboardDataStore.get(context).edit { prefs ->
            prefs[keyOrder] = order.joinToString(",") { it.name }
            prefs[keyHidden] = hidden.joinToString(",") { it.name }
            prefs[keyKnown] = allKnownCsv()
        }
    }

    /** Reset layout ke default pabrik. */
    suspend fun reset() {
        ZkmDashboardDataStore.get(context).edit { prefs ->
            prefs.remove(keyOrder)
            prefs.remove(keyHidden)
            prefs.remove(keyKnown)
        }
    }
}
