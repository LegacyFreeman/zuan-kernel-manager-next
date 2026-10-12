/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.home.menu

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ThermalStats(
    val maxTemp: Int = 0,
    val avgTemp: Double = 0.0,
    val activeZones: Int = 0,
    val totalZones: Int = 0
)

class ThermalDevicesViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext get() = getApplication<Application>()

    private val _allZones = MutableStateFlow<List<ThermalZone>>(emptyList())
    private val _coolingDevices = MutableStateFlow<List<ThermalCoolingDevice>>(emptyList())
    private val _selectedFilter = MutableStateFlow("All")
    private val _isLoading = MutableStateFlow(true)
    private val _isAutoRefresh = MutableStateFlow(true)
    private val _thermalPolicy = MutableStateFlow("default")
    private val _showTripPoints = MutableStateFlow<Map<Int, List<TripPoint>>>(emptyMap())

    // --- ADVANCE TAB COMPATIBILITY STATES (SUPPORTED OR NOT) ---
    val isPpmSupported = MutableStateFlow(false)
    val isGpuMaliSupported = MutableStateFlow(false)
    val isGedDcsSupported = MutableStateFlow(false)
    val isGpuIgnoreSupported = MutableStateFlow(false)
    val isDvfsSupported = MutableStateFlow(false)
    val isGpuAlwaysOnSupported = MutableStateFlow(false)
    val isCciSupported = MutableStateFlow(false)
    val isCpuPowerModeSupported = MutableStateFlow(false)

    val isAdrenoSupported = MutableStateFlow(false)
    val isAdrenoLpmSupported = MutableStateFlow(false)
    val isUfshcSupported = MutableStateFlow(false)

    val isSconfigSupported = MutableStateFlow(false)

    // --- ADVANCE TAB VALUE STATES ---
    val ppmEnabled = MutableStateFlow(false)
    val ppmPolicies = MutableStateFlow(List(10) { false })

    val gedDcsDisabled = MutableStateFlow(false)
    val gpuIgnore = MutableStateFlow(false)
    val dvfsDisabled = MutableStateFlow(false)
    val gpuAlwaysOn = MutableStateFlow(false)
    val cciModeEnabled = MutableStateFlow(false)
    val cpuPowerMode = MutableStateFlow(0f)

    val adrenoPowerLevelMax = MutableStateFlow(false)
    val adrenoThrottlingDisabled = MutableStateFlow(false)
    val adrenoBclDisabled = MutableStateFlow(false)
    val adrenoPreemptionDisabled = MutableStateFlow(false)
    val adrenoLpmPredDisabled = MutableStateFlow(false)
    val adrenoLpmSleepDisabled = MutableStateFlow(false)
    val adrenoUfshcThrottlingDisabled = MutableStateFlow(false)

    val globalThermalLevel = MutableStateFlow(0f)
    val sconfigProfile = MutableStateFlow(0f)

    val propDisabled = MutableStateFlow(false)
    val processDisabled = MutableStateFlow(false)
    val shellCheckResult = MutableStateFlow("")

    // --- APPLY ON BOOT ---
    val applyOnBoot = MutableStateFlow(false)

    val isLoading: StateFlow<Boolean> = _isLoading
    val isAutoRefresh: StateFlow<Boolean> = _isAutoRefresh
    val thermalPolicy: StateFlow<String> = _thermalPolicy
    val selectedFilter: StateFlow<String> = _selectedFilter
    val coolingDevices: StateFlow<List<ThermalCoolingDevice>> = _coolingDevices
    val showTripPoints: StateFlow<Map<Int, List<TripPoint>>> = _showTripPoints
    val availableFilters = listOf("All", "CPU", "GPU", "Battery", "Skin", "Charger", "System")

    val filteredZones: StateFlow<List<ThermalZone>> = combine(_allZones, _selectedFilter) { zones, filter ->
        val activeZones = zones.filter { it.isEnabled }
        if (filter == "All") activeZones else activeZones.filter { it.type == filter }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val disabledZones: StateFlow<List<ThermalZone>> = _allZones.map { zones ->
        zones.filter { !it.isEnabled }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val thermalStats: StateFlow<ThermalStats> = combine(_allZones) { zonesArray ->
        val zoneList = zonesArray[0]
        if (zoneList.isEmpty()) ThermalStats()
        else {
            val validTemps = zoneList.filter { it.isEnabled && it.temperature > 0 }.map { it.temperature }
            ThermalStats(
                maxTemp = validTemps.maxOrNull() ?: 0,
                avgTemp = if (validTemps.isNotEmpty()) validTemps.average() else 0.0,
                activeZones = zoneList.count { it.isEnabled },
                totalZones = zoneList.size
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThermalStats())

    init {
        loadThermalData()
        startAutoRefresh()
        viewModelScope.launch(Dispatchers.IO) {
            applyOnBoot.value = ThermalTweakStore.isApplyOnBoot(appContext)
        }
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (isActive) {
                if (_isAutoRefresh.value) refreshData()
                delay(2000)
            }
        }
    }

    fun loadThermalData() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true

            // Cek Kompatibilitas saat pertama load
            isPpmSupported.value = ThermalUtils.isPpmSupported()
            isGedDcsSupported.value = ThermalUtils.isGedDcsSupported()
            isGpuIgnoreSupported.value = ThermalUtils.isGpuIgnoreSupported()
            isDvfsSupported.value = ThermalUtils.isDvfsSupported()
            isGpuAlwaysOnSupported.value = ThermalUtils.isGpuAlwaysOnSupported()
            isCciSupported.value = ThermalUtils.isCciSupported()
            isCpuPowerModeSupported.value = ThermalUtils.isCpuPowerModeSupported()
            isGpuMaliSupported.value = isGedDcsSupported.value || isGpuIgnoreSupported.value || isDvfsSupported.value || isGpuAlwaysOnSupported.value || isCciSupported.value

            isAdrenoSupported.value = ThermalUtils.isAdrenoSupported()
            isAdrenoLpmSupported.value = ThermalUtils.isAdrenoLpmSupported()
            isUfshcSupported.value = ThermalUtils.isUfshcSupported()
            isSconfigSupported.value = ThermalUtils.isSconfigSupported()

            val zones = ThermalUtils.getAllThermalZones()
            val cooling = ThermalUtils.getCoolingDevices()
            val policy = ThermalUtils.getThermalPolicy()

            withContext(Dispatchers.Main) {
                _allZones.value = zones
                _coolingDevices.value = cooling
                _thermalPolicy.value = policy
                _isLoading.value = false
            }
            refreshData()
        }
    }

    fun refreshData() {
        viewModelScope.launch(Dispatchers.IO) {
            val zones = ThermalUtils.getAllThermalZones()
            val cooling = ThermalUtils.getCoolingDevices()

            // Baca semua state Advance dalam SATU shell call (hemat baterai)
            val states = ThermalUtils.readAdvanceStatesBatch()

            if (isPpmSupported.value) ppmEnabled.value = states.ppmEnabled
            if (isGedDcsSupported.value) gedDcsDisabled.value = states.gedDcsDisabled
            if (isDvfsSupported.value) dvfsDisabled.value = states.dvfsDisabled
            if (isGpuAlwaysOnSupported.value) gpuAlwaysOn.value = states.gpuAlwaysOn
            if (isCciSupported.value) cciModeEnabled.value = states.cciModeEnabled
            if (isCpuPowerModeSupported.value) cpuPowerMode.value = states.cpuPowerMode

            if (isAdrenoSupported.value) {
                adrenoThrottlingDisabled.value = states.adrenoThrottleDisabled
                adrenoBclDisabled.value = states.adrenoBclDisabled
                adrenoPreemptionDisabled.value = states.adrenoPreemptDisabled
            }
            if (isAdrenoLpmSupported.value) {
                adrenoLpmPredDisabled.value = states.lpmPredDisabled
                adrenoLpmSleepDisabled.value = states.lpmSleepDisabled
            }
            if (isUfshcSupported.value) adrenoUfshcThrottlingDisabled.value = states.ufshcThrottlingDisabled

            if (isSconfigSupported.value) {
                val sconfigData = listOf(-1, 0, 2, 8, 10, 13, 20, 40, 42, 50, 51)
                val mappedIndex = sconfigData.indexOf(states.sconfigValue)
                if (mappedIndex != -1) sconfigProfile.value = mappedIndex.toFloat()
            }

            withContext(Dispatchers.Main) {
                _allZones.value = zones
                _coolingDevices.value = cooling
            }
        }
    }

    // Tab 1, 2, 3 Actions
    fun onFilterSelected(filter: String) { _selectedFilter.value = filter }
    fun toggleAutoRefresh(enabled: Boolean) { _isAutoRefresh.value = enabled }
    fun toggleThermalZone(zone: ThermalZone) { viewModelScope.launch(Dispatchers.IO) { if (ThermalUtils.setThermalZoneState(zone.id, !zone.isEnabled)) refreshData() } }
    fun setCoolingState(device: ThermalCoolingDevice, state: Int) { viewModelScope.launch(Dispatchers.IO) { if (ThermalUtils.setCoolingDeviceState(device.id, state)) refreshData() } }
    fun setThermalPolicy(policy: String) { viewModelScope.launch(Dispatchers.IO) { if (ThermalUtils.setThermalPolicy(policy)) _thermalPolicy.value = policy } }
    fun loadTripPoints(zone: ThermalZone) { viewModelScope.launch(Dispatchers.IO) { val trips = ThermalUtils.getTripPoints(zone.path); withContext(Dispatchers.Main) { _showTripPoints.value = _showTripPoints.value.toMutableMap().apply { put(zone.id, trips) } } } }
    fun clearTripPoints(zoneId: Int) { _showTripPoints.value = _showTripPoints.value.toMutableMap().apply { remove(zoneId) } }

    // --- APPLY ON BOOT ---
    fun setApplyOnBoot(enabled: Boolean) {
        applyOnBoot.value = enabled
        viewModelScope.launch(Dispatchers.IO) { ThermalTweakStore.setBool(appContext, ThermalTweakStore.APPLY_ON_BOOT, enabled) }
    }

    // --- ADVANCE TAB ACTIONS (eksekusi ke kernel + persist ke DataStore) ---
    fun setPpmEnabledState(enabled: Boolean) { ppmEnabled.value = enabled; viewModelScope.launch(Dispatchers.IO) { ThermalUtils.setPpmEnabled(enabled); ThermalTweakStore.setBool(appContext, ThermalTweakStore.PPM_ENABLED, enabled) } }
    fun setPpmPolicyState(index: Int, enabled: Boolean) { val list = ppmPolicies.value.toMutableList(); list[index] = enabled; ppmPolicies.value = list; viewModelScope.launch(Dispatchers.IO) { ThermalUtils.setPpmPolicy(index, enabled) } }

    fun setGedDcsState(disabled: Boolean) { gedDcsDisabled.value = disabled; viewModelScope.launch(Dispatchers.IO) { ThermalUtils.setGedDcs(disabled); ThermalTweakStore.setBool(appContext, ThermalTweakStore.GED_DCS_DISABLED, disabled) } }
    fun setGpuIgnoreState(enabled: Boolean) { gpuIgnore.value = enabled; viewModelScope.launch(Dispatchers.IO) { ThermalUtils.setGpuIgnore(enabled); ThermalTweakStore.setBool(appContext, ThermalTweakStore.GPU_IGNORE, enabled) } }
    fun setDvfsState(disabled: Boolean) { dvfsDisabled.value = disabled; viewModelScope.launch(Dispatchers.IO) { ThermalUtils.setDvfs(disabled); ThermalTweakStore.setBool(appContext, ThermalTweakStore.DVFS_DISABLED, disabled) } }
    fun setGpuAlwaysOnState(enabled: Boolean) { gpuAlwaysOn.value = enabled; viewModelScope.launch(Dispatchers.IO) { ThermalUtils.setGpuAlwaysOn(enabled); ThermalTweakStore.setBool(appContext, ThermalTweakStore.GPU_ALWAYS_ON, enabled) } }
    fun setCciModeState(enabled: Boolean) { cciModeEnabled.value = enabled; viewModelScope.launch(Dispatchers.IO) { ThermalUtils.setCpufreqCci(enabled); ThermalTweakStore.setBool(appContext, ThermalTweakStore.CCI_MODE, enabled) } }
    fun setCpuPowerModeState(mode: Float) { cpuPowerMode.value = mode; viewModelScope.launch(Dispatchers.IO) { ThermalUtils.setCpufreqPowerMode(mode.toInt()); ThermalTweakStore.setInt(appContext, ThermalTweakStore.CPU_POWER_MODE, mode.toInt()) } }

    fun setAdrenoPwrMax(enabled: Boolean) { adrenoPowerLevelMax.value = enabled; viewModelScope.launch(Dispatchers.IO) { ThermalUtils.setPowerLevelMax(enabled); ThermalTweakStore.setBool(appContext, ThermalTweakStore.ADRENO_PWR_MAX, enabled) } }
    fun setAdrenoThrottle(disabled: Boolean) { adrenoThrottlingDisabled.value = disabled; viewModelScope.launch(Dispatchers.IO) { ThermalUtils.setThrottling(disabled); ThermalTweakStore.setBool(appContext, ThermalTweakStore.ADRENO_THROTTLE_OFF, disabled) } }
    fun setAdrenoBcl(disabled: Boolean) { adrenoBclDisabled.value = disabled; viewModelScope.launch(Dispatchers.IO) { ThermalUtils.setBcl(disabled); ThermalTweakStore.setBool(appContext, ThermalTweakStore.ADRENO_BCL_OFF, disabled) } }
    fun setAdrenoPreempt(disabled: Boolean) { adrenoPreemptionDisabled.value = disabled; viewModelScope.launch(Dispatchers.IO) { ThermalUtils.setPreemption(disabled); ThermalTweakStore.setBool(appContext, ThermalTweakStore.ADRENO_PREEMPT_OFF, disabled) } }
    fun setAdrenoLpmPred(disabled: Boolean) { adrenoLpmPredDisabled.value = disabled; viewModelScope.launch(Dispatchers.IO) { ThermalUtils.setLpmPrediction(disabled); ThermalTweakStore.setBool(appContext, ThermalTweakStore.LPM_PRED_OFF, disabled) } }
    fun setAdrenoLpmSleep(disabled: Boolean) { adrenoLpmSleepDisabled.value = disabled; viewModelScope.launch(Dispatchers.IO) { ThermalUtils.setLpmSleep(disabled); ThermalTweakStore.setBool(appContext, ThermalTweakStore.LPM_SLEEP_OFF, disabled) } }
    fun setAdrenoUfshc(disabled: Boolean) { adrenoUfshcThrottlingDisabled.value = disabled; viewModelScope.launch(Dispatchers.IO) { ThermalUtils.setUfshcThrottling(disabled); ThermalTweakStore.setBool(appContext, ThermalTweakStore.UFSHC_OFF, disabled) } }

    fun setGlobalThermalLevel(level: Float) { globalThermalLevel.value = level; viewModelScope.launch(Dispatchers.IO) { ThermalUtils.setThermalZoneLevel(level.toInt()); ThermalTweakStore.setInt(appContext, ThermalTweakStore.THERMAL_LEVEL, level.toInt()) } }
    fun setSconfigState(index: Float, realValue: Int) { sconfigProfile.value = index; viewModelScope.launch(Dispatchers.IO) { ThermalUtils.setThermalSconfig(realValue); ThermalTweakStore.setInt(appContext, ThermalTweakStore.SCONFIG_VALUE, realValue) } }

    fun setPropDisabledState(disabled: Boolean) { propDisabled.value = disabled; viewModelScope.launch(Dispatchers.IO) { ThermalUtils.setThermalProp(disabled) } }
    fun setProcessDisabledState(disabled: Boolean) { processDisabled.value = disabled; viewModelScope.launch(Dispatchers.IO) { ThermalUtils.setThermalProcess(disabled) } }

    fun checkProps() { viewModelScope.launch(Dispatchers.IO) { val res = ThermalUtils.checkThermalProp(); withContext(Dispatchers.Main) { shellCheckResult.value = res } } }
    fun checkProcesses() { viewModelScope.launch(Dispatchers.IO) { val res = ThermalUtils.checkThermalProcess(); withContext(Dispatchers.Main) { shellCheckResult.value = res } } }
    fun clearShellResult() { shellCheckResult.value = "" }
}