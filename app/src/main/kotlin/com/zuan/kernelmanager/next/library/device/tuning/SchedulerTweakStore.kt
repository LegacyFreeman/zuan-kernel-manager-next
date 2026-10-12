package com.zuan.kernelmanager.next.library.device.tuning

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.zuan.kernelmanager.next.core.kernel.KernelTuningMigration
import com.zuan.kernelmanager.next.core.persistence.ZkmLegacyTuningPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SchedulerTweakStore {
    val APPLY_ON_BOOT = booleanPreferencesKey("apply_on_boot")
    private const val PREFS = "legacy_scheduler_tweaks"
    private const val DATASTORE = "scheduler_tweaks"
    private fun schedKey(path: String) = "sched::$path"
    private suspend fun migrate(context: Context) = ZkmLegacyTuningPrefs.ensureMigrated(context, DATASTORE, PREFS)

    suspend fun isApplyOnBoot(context: Context): Boolean = withContext(Dispatchers.IO) { migrate(context); ZkmLegacyTuningPrefs.getBoolean(context, PREFS, APPLY_ON_BOOT.name) }
    suspend fun setApplyOnBoot(context: Context, enabled: Boolean) = withContext(Dispatchers.IO) { migrate(context); ZkmLegacyTuningPrefs.putBoolean(context, PREFS, APPLY_ON_BOOT.name, enabled) }
    suspend fun saveValue(context: Context, path: String, value: String) = withContext(Dispatchers.IO) { migrate(context); ZkmLegacyTuningPrefs.putString(context, PREFS, schedKey(path), value) }
    suspend fun exportPreferences(context: Context): Map<String, Any> = withContext(Dispatchers.IO) { migrate(context); ZkmLegacyTuningPrefs.read(context, PREFS) }
    suspend fun restorePreferences(context: Context, data: Map<String, Any>) = withContext(Dispatchers.IO) { migrate(context); ZkmLegacyTuningPrefs.clearAndRestore(context, PREFS, data) }
    suspend fun applyAll(context: Context, force: Boolean = false) { KernelTuningMigration.applyPersisted(context, force) }
}
