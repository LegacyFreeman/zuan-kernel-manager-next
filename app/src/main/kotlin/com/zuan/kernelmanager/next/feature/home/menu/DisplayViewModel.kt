/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.home.menu

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DisplayViewModel : ViewModel() {

    private val _currentResolution = MutableStateFlow(DisplayResolution(1080, 2400))
    private val _currentDpi = MutableStateFlow(420)
    private val _currentRefreshRate = MutableStateFlow(60)
    private val _screenTimeout = MutableStateFlow(30)
    private val _autoBrightness = MutableStateFlow(false)
    private val _brightness = MutableStateFlow(128)
    private val _nightMode = MutableStateFlow(false)
    private val _fontScale = MutableStateFlow(1.0f)
    private val _animationScale = MutableStateFlow(1.0f)
    private val _displayInversion = MutableStateFlow(false)
    
    // Fitur Baru
    private val _dashboardInfo = MutableStateFlow<DisplayDashboardInfo?>(null)
    private val _saturation = MutableStateFlow(1.0f)

    // Advanced Panel (HBM, DC Dimming, KCAL)
    private val _hbmSupported = MutableStateFlow(false)
    private val _hbmActive = MutableStateFlow(false)
    private val _dcDimmingSupported = MutableStateFlow(false)
    private val _dcDimmingActive = MutableStateFlow(false)
    private val _kcalConfig = MutableStateFlow(KcalConfig())

    private val _isLoading = MutableStateFlow(true)
    private val _selectedTab = MutableStateFlow(0)

    // Disimpan agar reset/refresh tidak perlu hardcode nilai (sebelumnya 420 dpi & 1080x2400).
    private var appContext: Context? = null

    val currentResolution: StateFlow<DisplayResolution> = _currentResolution
    val currentDpi: StateFlow<Int> = _currentDpi
    val currentRefreshRate: StateFlow<Int> = _currentRefreshRate
    val screenTimeout: StateFlow<Int> = _screenTimeout
    val autoBrightness: StateFlow<Boolean> = _autoBrightness
    val brightness: StateFlow<Int> = _brightness
    val nightMode: StateFlow<Boolean> = _nightMode
    val fontScale: StateFlow<Float> = _fontScale
    val animationScale: StateFlow<Float> = _animationScale
    val displayInversion: StateFlow<Boolean> = _displayInversion
    val dashboardInfo: StateFlow<DisplayDashboardInfo?> = _dashboardInfo
    val saturation: StateFlow<Float> = _saturation

    val hbmSupported: StateFlow<Boolean> = _hbmSupported
    val hbmActive: StateFlow<Boolean> = _hbmActive
    val dcDimmingSupported: StateFlow<Boolean> = _dcDimmingSupported
    val dcDimmingActive: StateFlow<Boolean> = _dcDimmingActive
    val kcalConfig: StateFlow<KcalConfig> = _kcalConfig
    
    val isLoading: StateFlow<Boolean> = _isLoading
    val selectedTab: StateFlow<Int> = _selectedTab

    val availableResolutions = DisplayUtils.getAvailableResolutions()
    val availableDpiValues = DisplayUtils.getAvailableDpiValues()
    val availableRefreshRates = DisplayUtils.getAvailableRefreshRates()
    val timeoutOptions = listOf(15, 30, 60, 120, 300, 600, 1800)
    val fontScaleOptions = listOf(0.85f, 1.0f, 1.15f, 1.3f)
    val animationScaleOptions = listOf(0.0f, 0.5f, 1.0f, 1.5f, 2.0f)

    fun loadDisplayData(context: Context) {
        appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            
            val resolution = DisplayUtils.getCurrentResolution(context)
            val dpi = DisplayUtils.getCurrentDpi(context)
            val refreshRate = DisplayUtils.getCurrentRefreshRate(context) // BUG FIX: sebelumnya salah memanggil getCurrentDpi
            val timeout = DisplayUtils.getScreenTimeout(context)
            val autoBright = DisplayUtils.getBrightnessMode(context)
            val bright = DisplayUtils.getBrightness(context)
            val night = DisplayUtils.isNightModeEnabled(context)
            val font = DisplayUtils.getFontScale(context)
            val anim = DisplayUtils.getAnimationScale(context)
            val inversion = DisplayUtils.isDisplayInversionEnabled(context)
            val dashInfo = DisplayUtils.getDashboardInfo(context)
            val hbmSup = DisplayUtils.isHbmSupported()
            val hbmAct = if (hbmSup) DisplayUtils.isHbmActive() else false
            val dcSup = DisplayUtils.isDcDimmingSupported()
            val dcAct = if (dcSup) DisplayUtils.isDcDimmingActive() else false
            val kcal = DisplayUtils.getKcalConfig()
            
            withContext(Dispatchers.Main) {
                _currentResolution.value = resolution
                _currentDpi.value = dpi
                _currentRefreshRate.value = refreshRate
                _screenTimeout.value = timeout
                _autoBrightness.value = autoBright
                _brightness.value = bright
                _nightMode.value = night
                _fontScale.value = font
                _animationScale.value = anim
                _displayInversion.value = inversion
                _dashboardInfo.value = dashInfo
                _hbmSupported.value = hbmSup
                _hbmActive.value = hbmAct
                _dcDimmingSupported.value = dcSup
                _dcDimmingActive.value = dcAct
                _kcalConfig.value = kcal
                _isLoading.value = false
            }
        }
    }

    fun onTabSelected(index: Int) {
        _selectedTab.value = index
    }

    fun setResolution(width: Int, height: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = DisplayUtils.setResolution(width, height)
            if (success) {
                _currentResolution.value = DisplayResolution(width, height)
                refreshDashboard()
            }
        }
    }

    fun resetResolution() {
        viewModelScope.launch(Dispatchers.IO) {
            val success = DisplayUtils.resetResolution()
            if (success) {
                val ctx = appContext
                _currentResolution.value = if (ctx != null) DisplayUtils.getPhysicalResolution(ctx) else _currentResolution.value
                refreshDashboard()
            }
        }
    }

    fun setDpi(dpi: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = DisplayUtils.setDpi(dpi)
            if (success) { _currentDpi.value = dpi; refreshDashboard() }
        }
    }

    fun resetDpi() {
        viewModelScope.launch(Dispatchers.IO) {
            val success = DisplayUtils.resetDpi()
            if (success) {
                val ctx = appContext
                if (ctx != null) _currentDpi.value = DisplayUtils.getPhysicalDpi(ctx)
                refreshDashboard()
            }
        }
    }
    
    private fun refreshDashboard() {
        val ctx = appContext ?: return
        _dashboardInfo.value = DisplayUtils.getDashboardInfo(ctx)
    }

    fun setSaturation(value: Float) {
        _saturation.value = value
        viewModelScope.launch(Dispatchers.IO) {
            DisplayUtils.setSaturation(value)
        }
    }

    fun setRefreshRate(rate: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = DisplayUtils.setRefreshRate(rate)
            if (success) _currentRefreshRate.value = rate
        }
    }

    fun setScreenTimeout(seconds: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = DisplayUtils.setScreenTimeout(seconds)
            if (success) _screenTimeout.value = seconds
        }
    }

    fun setAutoBrightness(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = DisplayUtils.setBrightnessMode(enabled)
            if (success) _autoBrightness.value = enabled
        }
    }

    fun setBrightness(value: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = DisplayUtils.setBrightness(value)
            if (success) _brightness.value = value
        }
    }

    fun setNightMode(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = DisplayUtils.setNightMode(enabled)
            if (success) _nightMode.value = enabled
        }
    }

    fun setFontScale(scale: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = DisplayUtils.setFontScale(scale)
            if (success) _fontScale.value = scale
        }
    }

    fun setAnimationScale(scale: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = DisplayUtils.setAnimationScale(scale)
            if (success) _animationScale.value = scale
        }
    }

    fun setDisplayInversion(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = DisplayUtils.setDisplayInversion(enabled)
            if (success) _displayInversion.value = enabled
        }
    }

    fun toggleHbm(enable: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = DisplayUtils.setHbm(enable)
            if (success) _hbmActive.value = enable
        }
    }

    fun toggleDcDimming(enable: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = DisplayUtils.setDcDimming(enable)
            if (success) _dcDimmingActive.value = enable
        }
    }

    fun setKcalRgb(r: Int, g: Int, b: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = DisplayUtils.setKcalRgb(r, g, b)
            if (success) {
                _kcalConfig.value = _kcalConfig.value.copy(r = r, g = g, b = b, enabled = true)
            }
        }
    }

    fun resetKcal() {
        viewModelScope.launch(Dispatchers.IO) {
            val success = DisplayUtils.resetKcal()
            if (success) {
                _kcalConfig.value = _kcalConfig.value.copy(r = 256, g = 256, b = 256, enabled = true)
            }
        }
    }
}
