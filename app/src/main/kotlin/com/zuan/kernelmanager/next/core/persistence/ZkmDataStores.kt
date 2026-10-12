package com.zuan.kernelmanager.next.core.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-wide DataStore ownership for ZKM persistence.
 *
 * Every file has exactly one DataStore instance for the lifetime of the app
 * process. This deliberately avoids creating DataStore instances from
 * Activity/ViewModel/Context call sites.
 */
object ZkmDataStores {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile private var cpu: DataStore<Preferences>? = null
    @Volatile private var memory: DataStore<Preferences>? = null
    @Volatile private var scheduler: DataStore<Preferences>? = null
    @Volatile private var thermal: DataStore<Preferences>? = null
    @Volatile private var dashboard: DataStore<Preferences>? = null

    fun cpu(context: Context): DataStore<Preferences> = synchronized(this) {
        cpu ?: PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { context.applicationContext.preferencesDataStoreFile("cpu_tweaks") }
        ).also { cpu = it }
    }

    fun memory(context: Context): DataStore<Preferences> = synchronized(this) {
        memory ?: PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { context.applicationContext.preferencesDataStoreFile("memory_tweaks") }
        ).also { memory = it }
    }

    fun scheduler(context: Context): DataStore<Preferences> = synchronized(this) {
        scheduler ?: PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { context.applicationContext.preferencesDataStoreFile("scheduler_tweaks") }
        ).also { scheduler = it }
    }

    fun thermal(context: Context): DataStore<Preferences> = synchronized(this) {
        thermal ?: PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { context.applicationContext.preferencesDataStoreFile("thermal_tweaks") }
        ).also { thermal = it }
    }

    fun dashboard(context: Context): DataStore<Preferences> = synchronized(this) {
        dashboard ?: PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { context.applicationContext.preferencesDataStoreFile("dashboard_layout") }
        ).also { dashboard = it }
    }
}
