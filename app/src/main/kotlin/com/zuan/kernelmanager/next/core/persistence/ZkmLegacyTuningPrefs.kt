package com.zuan.kernelmanager.next.core.persistence

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Compatibility persistence for the legacy tuning screens.
 *
 * These stores are deliberately backed by SharedPreferences rather than a live
 * DataStore. Legacy tuning is a small key/value compatibility layer and does
 * not need DataStore's streaming model. Most importantly, this removes the
 * possibility of a second active DataStore instance for scheduler_tweaks
 * while a diagnostic/profile operation is also touching the same data.
 *
 * On first access after upgrading, the old DataStore file is imported once,
 * then all normal reads/writes use SharedPreferences only.
 */
object ZkmLegacyTuningPrefs {
    private const val MIGRATION_MARKER = "__zkm_datastore_import_v1"
    private val migrationMutex = Mutex()

    private fun prefs(context: Context, name: String): SharedPreferences =
        context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    suspend fun ensureMigrated(context: Context, oldDataStoreName: String, prefsName: String) {
        val app = context.applicationContext
        val target = prefs(app, prefsName)
        if (target.getBoolean(MIGRATION_MARKER, false)) return

        migrationMutex.withLock {
            if (target.getBoolean(MIGRATION_MARKER, false)) return@withLock

            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            try {
                val dataStore = PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = { app.preferencesDataStoreFile(oldDataStoreName) }
                )
                val values = runCatching { dataStore.data.first().asMap() }.getOrElse { emptyMap() }
                if (values.isNotEmpty()) {
                    val editor = target.edit()
                    values.forEach { (key, value) ->
                        when (value) {
                            is Boolean -> editor.putBoolean(key.name, value)
                            is String -> editor.putString(key.name, value)
                            is Int -> editor.putInt(key.name, value)
                            is Long -> editor.putLong(key.name, value)
                            is Float -> editor.putFloat(key.name, value)
                            is Double -> editor.putString(key.name, value.toString())
                            is Set<*> -> editor.putStringSet(key.name, value.map { it.toString() }.toSet())
                        }
                    }
                    editor.putBoolean(MIGRATION_MARKER, true).commit()
                } else {
                    target.edit().putBoolean(MIGRATION_MARKER, true).commit()
                }
            } finally {
                scope.cancel()
            }
        }
    }

    fun read(context: Context, prefsName: String): Map<String, Any> {
        val all = prefs(context, prefsName).all
        return all.filterKeys { it != MIGRATION_MARKER }
            .mapValues { (_, value) -> value ?: "" }
    }

    fun clearAndRestore(context: Context, prefsName: String, data: Map<String, Any>) {
        val editor = prefs(context, prefsName).edit().clear()
        data.forEach { (name, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(name, value)
                is String -> editor.putString(name, value)
                is Int -> editor.putInt(name, value)
                is Long -> editor.putLong(name, value)
                is Float -> editor.putFloat(name, value)
                is Double -> editor.putString(name, value.toString())
                is Set<*> -> editor.putStringSet(name, value.map { it.toString() }.toSet())
                else -> editor.putString(name, value.toString())
            }
        }
        editor.putBoolean(MIGRATION_MARKER, true).commit()
    }

    fun putBoolean(context: Context, prefsName: String, key: String, value: Boolean) {
        prefs(context, prefsName).edit().putBoolean(key, value).apply()
    }

    fun putString(context: Context, prefsName: String, key: String, value: String) {
        prefs(context, prefsName).edit().putString(key, value).apply()
    }

    fun putStringSet(context: Context, prefsName: String, key: String, value: Set<String>) {
        prefs(context, prefsName).edit().putStringSet(key, value).apply()
    }

    fun putInt(context: Context, prefsName: String, key: String, value: Int) {
        prefs(context, prefsName).edit().putInt(key, value).apply()
    }

    fun getBoolean(context: Context, prefsName: String, key: String, default: Boolean = false): Boolean =
        prefs(context, prefsName).getBoolean(key, default)

    fun getString(context: Context, prefsName: String, key: String, default: String? = null): String? =
        prefs(context, prefsName).getString(key, default)

    fun getStringSet(context: Context, prefsName: String, key: String): Set<String> =
        prefs(context, prefsName).getStringSet(key, emptySet())?.toSet() ?: emptySet()
}
