package com.zuan.kernelmanager.next.library.device.tuning

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.zuan.kernelmanager.next.core.kernel.KernelTuningMigration
import com.zuan.kernelmanager.next.core.persistence.ZkmLegacyTuningPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MemoryTweakStore {
    val APPLY_ON_BOOT_VM_IO = booleanPreferencesKey("apply_on_boot_vm_io")
    val APPLY_ON_BOOT_ZRAM = booleanPreferencesKey("apply_on_boot_zram")
    val ZRAM_SIZE = stringPreferencesKey("zram_size")
    val ZRAM_ALGO = stringPreferencesKey("zram_algo")
    private const val PREFS = "legacy_memory_tweaks"
    private const val DATASTORE = "memory_tweaks"
    private fun vmKey(path: String) = "vm::$path"
    private fun ioSchedKey(dev: String) = "iosched::$dev"
    private fun ioTunableKey(dev: String, tunable: String) = "iotun::$dev::$tunable"
    private suspend fun migrate(context: Context) = ZkmLegacyTuningPrefs.ensureMigrated(context, DATASTORE, PREFS)

    suspend fun isApplyOnBootVmIo(context: Context): Boolean = withContext(Dispatchers.IO) { migrate(context); ZkmLegacyTuningPrefs.getBoolean(context, PREFS, APPLY_ON_BOOT_VM_IO.name) }
    suspend fun setApplyOnBootVmIo(context: Context, enabled: Boolean) = withContext(Dispatchers.IO) { migrate(context); ZkmLegacyTuningPrefs.putBoolean(context, PREFS, APPLY_ON_BOOT_VM_IO.name, enabled) }
    suspend fun isApplyOnBootZram(context: Context): Boolean = withContext(Dispatchers.IO) { migrate(context); ZkmLegacyTuningPrefs.getBoolean(context, PREFS, APPLY_ON_BOOT_ZRAM.name) }
    suspend fun setApplyOnBootZram(context: Context, enabled: Boolean) = withContext(Dispatchers.IO) { migrate(context); ZkmLegacyTuningPrefs.putBoolean(context, PREFS, APPLY_ON_BOOT_ZRAM.name, enabled) }
    suspend fun saveZramSettings(context: Context, sizeMb: Int, algo: String) = withContext(Dispatchers.IO) { migrate(context); ZkmLegacyTuningPrefs.putString(context, PREFS, ZRAM_SIZE.name, sizeMb.toString()); ZkmLegacyTuningPrefs.putString(context, PREFS, ZRAM_ALGO.name, algo) }
    suspend fun saveVmSetting(context: Context, path: String, value: String) = withContext(Dispatchers.IO) { migrate(context); ZkmLegacyTuningPrefs.putString(context, PREFS, vmKey(path), value) }
    suspend fun saveIoSched(context: Context, dev: String, sched: String) = withContext(Dispatchers.IO) { migrate(context); ZkmLegacyTuningPrefs.putString(context, PREFS, ioSchedKey(dev), sched) }
    suspend fun saveIoTunable(context: Context, dev: String, tunable: String, value: String) = withContext(Dispatchers.IO) { migrate(context); ZkmLegacyTuningPrefs.putString(context, PREFS, ioTunableKey(dev, tunable), value) }
    suspend fun exportPreferences(context: Context): Map<String, Any> = withContext(Dispatchers.IO) { migrate(context); ZkmLegacyTuningPrefs.read(context, PREFS) }
    suspend fun restorePreferences(context: Context, data: Map<String, Any>) = withContext(Dispatchers.IO) { migrate(context); ZkmLegacyTuningPrefs.clearAndRestore(context, PREFS, data) }
    suspend fun applyAll(context: Context, force: Boolean = false) { KernelTuningMigration.applyPersisted(context, force) }
}
