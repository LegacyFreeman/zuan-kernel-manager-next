package com.zuan.kernelmanager.next.core.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Single owner for the dashboard DataStore only. Legacy tuning no longer uses DataStore. */
object ZkmDashboardDataStore {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile private var store: DataStore<Preferences>? = null

    fun get(context: Context): DataStore<Preferences> = synchronized(this) {
        store ?: PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { context.applicationContext.preferencesDataStoreFile("dashboard_layout") }
        ).also { store = it }
    }
}
