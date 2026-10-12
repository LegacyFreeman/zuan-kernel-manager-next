package com.zuan.kernelmanager.next.library.device.tuning

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.zuan.kernelmanager.next.core.kernel.KernelTuningMigration
import com.zuan.kernelmanager.next.core.persistence.ZkmLegacyTuningPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CpuTweakStore {
    val APPLY_ON_BOOT = booleanPreferencesKey("apply_on_boot")
    val OFFLINE_CORES = stringSetPreferencesKey("offline_cores")
    private const val PREFS = "legacy_cpu_tweaks"
    private const val DATASTORE = "cpu_tweaks"
    private fun govKey(path: String) = "gov::$path"
    private fun minKey(path: String) = "min::$path"
    private fun maxKey(path: String) = "max::$path"

    private suspend fun migrate(context: Context) = ZkmLegacyTuningPrefs.ensureMigrated(context, DATASTORE, PREFS)

    suspend fun isApplyOnBoot(context: Context): Boolean = withContext(Dispatchers.IO) {
        migrate(context); ZkmLegacyTuningPrefs.getBoolean(context, PREFS, APPLY_ON_BOOT.name)
    }
    suspend fun setApplyOnBoot(context: Context, enabled: Boolean) = withContext(Dispatchers.IO) {
        migrate(context); ZkmLegacyTuningPrefs.putBoolean(context, PREFS, APPLY_ON_BOOT.name, enabled)
    }
    suspend fun saveGov(context: Context, policyPath: String, gov: String) = withContext(Dispatchers.IO) {
        migrate(context); ZkmLegacyTuningPrefs.putString(context, PREFS, govKey(policyPath), gov)
    }
    suspend fun saveFreq(context: Context, policyPath: String, target: String, freqMhz: String) = withContext(Dispatchers.IO) {
        migrate(context); ZkmLegacyTuningPrefs.putString(context, PREFS, if (target == "min") minKey(policyPath) else maxKey(policyPath), freqMhz)
    }
    suspend fun saveCoreState(context: Context, coreId: Int, online: Boolean) = withContext(Dispatchers.IO) {
        migrate(context)
        val current = ZkmLegacyTuningPrefs.getStringSet(context, PREFS, OFFLINE_CORES.name).toMutableSet()
        if (online) current.remove(coreId.toString()) else current.add(coreId.toString())
        ZkmLegacyTuningPrefs.putStringSet(context, PREFS, OFFLINE_CORES.name, current)
    }
    suspend fun exportPreferences(context: Context): Map<String, Any> = withContext(Dispatchers.IO) {
        migrate(context); ZkmLegacyTuningPrefs.read(context, PREFS)
    }
    suspend fun restorePreferences(context: Context, data: Map<String, Any>) = withContext(Dispatchers.IO) {
        migrate(context); ZkmLegacyTuningPrefs.clearAndRestore(context, PREFS, data)
    }
    suspend fun applyAll(context: Context, force: Boolean = false) { KernelTuningMigration.applyPersisted(context, force) }
}
