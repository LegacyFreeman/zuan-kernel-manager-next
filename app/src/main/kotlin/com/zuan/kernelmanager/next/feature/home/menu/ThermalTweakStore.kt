package com.zuan.kernelmanager.next.feature.home.menu

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import com.zuan.kernelmanager.next.core.persistence.ZkmLegacyTuningPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ThermalTweakStore {
    val APPLY_ON_BOOT = booleanPreferencesKey("apply_on_boot")
    val PPM_ENABLED = booleanPreferencesKey("ppm_enabled")
    val GED_DCS_DISABLED = booleanPreferencesKey("ged_dcs_disabled")
    val GPU_IGNORE = booleanPreferencesKey("gpu_ignore")
    val DVFS_DISABLED = booleanPreferencesKey("dvfs_disabled")
    val GPU_ALWAYS_ON = booleanPreferencesKey("gpu_always_on")
    val CCI_MODE = booleanPreferencesKey("cci_mode")
    val CPU_POWER_MODE = intPreferencesKey("cpu_power_mode")
    val ADRENO_PWR_MAX = booleanPreferencesKey("adreno_pwr_max")
    val ADRENO_THROTTLE_OFF = booleanPreferencesKey("adreno_throttle_off")
    val ADRENO_BCL_OFF = booleanPreferencesKey("adreno_bcl_off")
    val ADRENO_PREEMPT_OFF = booleanPreferencesKey("adreno_preempt_off")
    val LPM_PRED_OFF = booleanPreferencesKey("lpm_pred_off")
    val LPM_SLEEP_OFF = booleanPreferencesKey("lpm_sleep_off")
    val UFSHC_OFF = booleanPreferencesKey("ufshc_off")
    val THERMAL_LEVEL = intPreferencesKey("thermal_level")
    val SCONFIG_VALUE = intPreferencesKey("sconfig_value")
    private const val PREFS = "legacy_thermal_tweaks"
    private const val DATASTORE = "thermal_tweaks"
    private suspend fun migrate(context: Context) = ZkmLegacyTuningPrefs.ensureMigrated(context, DATASTORE, PREFS)

    suspend fun setBool(context: Context, key: Preferences.Key<Boolean>, value: Boolean) = withContext(Dispatchers.IO) { migrate(context); ZkmLegacyTuningPrefs.putBoolean(context, PREFS, key.name, value) }
    suspend fun setInt(context: Context, key: Preferences.Key<Int>, value: Int) = withContext(Dispatchers.IO) { migrate(context); ZkmLegacyTuningPrefs.putInt(context, PREFS, key.name, value) }
    suspend fun isApplyOnBoot(context: Context): Boolean = withContext(Dispatchers.IO) { migrate(context); ZkmLegacyTuningPrefs.getBoolean(context, PREFS, APPLY_ON_BOOT.name) }
    suspend fun exportPreferences(context: Context): Map<String, Any> = withContext(Dispatchers.IO) { migrate(context); ZkmLegacyTuningPrefs.read(context, PREFS) }

    suspend fun applyAll(context: Context) {
        withContext(Dispatchers.IO) {
            migrate(context)
            if (!ZkmLegacyTuningPrefs.getBoolean(context, PREFS, APPLY_ON_BOOT.name)) return@withContext
            val getB: (String) -> Boolean = { key -> ZkmLegacyTuningPrefs.getBoolean(context, PREFS, key) }
            val getI: (String, Int) -> Int = { key, def -> context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(key, def) }
            if (getB(PPM_ENABLED.name)) ThermalUtils.setPpmEnabled(true)
            if (getB(GED_DCS_DISABLED.name)) ThermalUtils.setGedDcs(true)
            if (getB(GPU_IGNORE.name)) ThermalUtils.setGpuIgnore(true)
            if (getB(DVFS_DISABLED.name)) ThermalUtils.setDvfs(true)
            if (getB(GPU_ALWAYS_ON.name)) ThermalUtils.setGpuAlwaysOn(true)
            if (getB(CCI_MODE.name)) ThermalUtils.setCpufreqCci(true)
            val cpuPower = getI(CPU_POWER_MODE.name, 0); if (cpuPower > 0) ThermalUtils.setCpufreqPowerMode(cpuPower)
            if (getB(ADRENO_PWR_MAX.name)) ThermalUtils.setPowerLevelMax(true)
            if (getB(ADRENO_THROTTLE_OFF.name)) ThermalUtils.setThrottling(true)
            if (getB(ADRENO_BCL_OFF.name)) ThermalUtils.setBcl(true)
            if (getB(ADRENO_PREEMPT_OFF.name)) ThermalUtils.setPreemption(true)
            if (getB(LPM_PRED_OFF.name)) ThermalUtils.setLpmPrediction(true)
            if (getB(LPM_SLEEP_OFF.name)) ThermalUtils.setLpmSleep(true)
            if (getB(UFSHC_OFF.name)) ThermalUtils.setUfshcThrottling(true)
            val level = getI(THERMAL_LEVEL.name, 0); if (level > 0) ThermalUtils.setThermalZoneLevel(level)
            val sconfig = getI(SCONFIG_VALUE.name, -1); if (sconfig >= 0) ThermalUtils.setThermalSconfig(sconfig)
        }
    }
}
