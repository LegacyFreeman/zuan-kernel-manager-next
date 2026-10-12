/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.settings

import android.app.Application
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.zuan.kernelmanager.next.ui.theme.ThemeMode
import com.zuan.kernelmanager.next.feature.home.menu.DisplayUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.util.Base64
import org.json.JSONObject
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File

class WallpaperStyleViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsPreference = SettingsPreference.getInstance(application)
    private val context = application.applicationContext

    // --- STATES ---
    val themeMode = settingsPreference.themeMode
    val isDynamicColor = settingsPreference.isDynamicColor
    
    val bgType = settingsPreference.bgType
    val solidColor = settingsPreference.solidColor
    val expressiveThemeId = settingsPreference.expressiveThemeId
    
    val isCustomBackground = settingsPreference.isCustomBackground
    val backgroundImageUri = settingsPreference.backgroundImageUri
    val isVideoWallpaper = settingsPreference.isVideoWallpaper
    
    val cardDarkness = settingsPreference.cardDarkness
    val isBgBlur = settingsPreference.isBgBlur
    val bgContrast = settingsPreference.bgContrast
    
    // [BARU] State untuk Card Blur
    val isCardBlur = settingsPreference.isHazeEnabled

    // [BARU] Card Glass Style (Frosted Haze / Android Liquid Glass Kyant)
    val cardGlassStyle = settingsPreference.cardGlassStyle
    val liquidCornerRadius = settingsPreference.liquidCornerRadius
    val liquidBlurRadius = settingsPreference.liquidBlurRadius
    val liquidRefractionHeight = settingsPreference.liquidRefractionHeight
    val liquidRefractionAmount = settingsPreference.liquidRefractionAmount
    val liquidChromaticAberration = settingsPreference.liquidChromaticAberration

    // [BARU] Toggle / Slider / Dialog style
    val toggleStyle = settingsPreference.toggleStyle
    val sliderStyle = settingsPreference.sliderStyle
    val dialogStyle = settingsPreference.dialogStyle
    val customToggleOnImagePath = settingsPreference.customToggleOnImagePath
    val customToggleOffImagePath = settingsPreference.customToggleOffImagePath
    val customSliderThumbImagePath = settingsPreference.customSliderThumbImagePath

    // [BARU] Weather Effect States
    val weatherEffect = settingsPreference.weatherEffect
    val weatherIntensity = settingsPreference.weatherIntensity

    // [BARU] Avatar Decoration State (bingkai profil ala Discord)
    val avatarDecoration = settingsPreference.avatarDecoration

    // [BARU] Background Adjustment States
    val bgSaturation = settingsPreference.bgSaturation
    val blurStrength = settingsPreference.blurStrength

    // [BARU] Custom Color States
    val isCustomColor = settingsPreference.isCustomColor
    val customPrimaryColor = settingsPreference.customPrimaryColor
    val customSecondaryColor = settingsPreference.customSecondaryColor
    val customTertiaryColor = settingsPreference.customTertiaryColor

    // [BETA] Appbar Banner Progressive
    val appBarBannerEnabled = settingsPreference.appBarBannerEnabled
    val appBarBannerUri = settingsPreference.appBarBannerUri
    val appBarBannerType = settingsPreference.appBarBannerType
    val appBarBannerDim = settingsPreference.appBarBannerDim

    val iconShapeStyle = settingsPreference.iconShapeStyle
    val customIconImagePath = settingsPreference.customIconImagePath
    val customIconImagePaths = settingsPreference.customIconImagePaths
    val menuIconShapes = settingsPreference.menuIconShapes
    val autoIconImageColor = settingsPreference.autoIconImageColor
    val appFontChoice = settingsPreference.appFontChoice
    val customFontPath = settingsPreference.customFontPath
    val highRefreshRate = settingsPreference.highRefreshRate

    val currentThemeColor: StateFlow<AppThemeColor> = settingsPreference.themeColorName
        .map { name -> availableColors.find { it.name == name } ?: availableColors[1] }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), availableColors[1])

    val isBlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // [BARU] Get effective theme color (considering custom colors)
    val effectiveThemeColor: StateFlow<AppThemeColor> = combineStates(
        isCustomColor,
        customPrimaryColor,
        customSecondaryColor,
        customTertiaryColor,
        currentThemeColor
    ) { isCustom, primary, secondary, tertiary, preset ->
        if (isCustom) {
            AppThemeColor(
                name = "Custom",
                primary = Color(primary),
                secondary = Color(secondary),
                tertiary = Color(tertiary)
            )
        } else {
            preset
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), availableColors[1])

    // --- ACTIONS ---

    fun setThemeMode(mode: ThemeMode) { viewModelScope.launch { settingsPreference.setThemeMode(mode) } }

    /** Salin gambar pilihan user ke storage app (diperkecil max 256px) lalu pasang sebagai icon menu. */
    fun setCustomIconImage(uri: Uri) = setCustomIconImageForMenu("__global__", uri)

    fun setCustomIconImageForMenu(menuId: String, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val dir = java.io.File(context.filesDir, "icons").apply { mkdirs() }
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 512 && bounds.outHeight / (sample * 2) >= 512) sample *= 2
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                val decoded = context.contentResolver.openInputStream(uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, opts)
                } ?: error("Unable to decode image")
                val maxSide = 256
                val scale = minOf(1f, maxSide.toFloat() / maxOf(decoded.width, decoded.height))
                val bmp = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(
                    decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true
                ) else decoded
                val safeId = menuId.replace(Regex("[^A-Za-z0-9_.-]"), "_")
                val target = java.io.File(dir, "menu_icon_${safeId}_${System.currentTimeMillis()}.png")
                target.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                if (menuId == "__global__") {
                    dir.listFiles()?.filter { it.name.startsWith("custom_icon_") && it != target }?.forEach { it.delete() }
                    settingsPreference.setCustomIconImagePath(target.absolutePath)
                } else {
                    val old = settingsPreference.getCustomIconImagePathForMenu(menuId)
                    settingsPreference.setCustomIconImagePathForMenu(menuId, target.absolutePath)
                    old?.let { java.io.File(it).delete() }
                }
                if (settingsPreference.autoIconImageColor.value) {
                    val px = android.graphics.BitmapFactory.decodeFile(target.absolutePath)
                    if (px != null) {
                        val one = android.graphics.Bitmap.createScaledBitmap(px, 1, 1, true)
                        val hsv = FloatArray(3)
                        android.graphics.Color.colorToHSV(one.getPixel(0, 0), hsv)
                        val h = hsv[0]; val sat = hsv[1].coerceAtLeast(0.25f); val v = hsv[2].coerceIn(0.25f, 0.9f)
                        fun hsvColor(hue: Float, value: Float) = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
                        settingsPreference.setCustomPrimaryColor(hsvColor(h, v))
                        settingsPreference.setCustomSecondaryColor(hsvColor((h + 25f) % 360f, (v * 0.92f).coerceIn(0.25f, 0.9f)))
                        settingsPreference.setCustomTertiaryColor(hsvColor((h + 55f) % 360f, (v * 1.05f).coerceIn(0.25f, 0.95f)))
                        settingsPreference.setIsCustomColor(true)
                        one.recycle(); px.recycle()
                    }
                }
            }.onFailure { android.util.Log.e("ZKM", "Custom menu icon import failed", it) }
        }
    }

    fun clearCustomIconImageForMenu(menuId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val old = settingsPreference.getCustomIconImagePathForMenu(menuId)
            settingsPreference.setCustomIconImagePathForMenu(menuId, null)
            old?.let { java.io.File(it).delete() }
        }
    }

    fun clearCustomIconImage() {
        viewModelScope.launch(Dispatchers.IO) {
            java.io.File(context.filesDir, "icons").listFiles()?.forEach { it.delete() }
            settingsPreference.setCustomIconImagePath(null)
        }
    }

    fun setIconShapeStyle(style: com.zuan.kernelmanager.next.ui.theme.ZkmIconShapeStyle) {
        viewModelScope.launch { settingsPreference.setIconShapeStyle(style) }
    }

    fun setMenuIconShape(menuId: String, style: com.zuan.kernelmanager.next.ui.theme.ZkmIconShapeStyle) {
        viewModelScope.launch { settingsPreference.setMenuIconShape(menuId, style) }
    }
    fun setAutoIconImageColor(enabled: Boolean) {
        viewModelScope.launch { settingsPreference.setAutoIconImageColor(enabled) }
    }

    fun applyColorFromImage(path: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap = path?.let { android.graphics.BitmapFactory.decodeFile(it) } ?: return@launch
            val one = android.graphics.Bitmap.createScaledBitmap(bitmap, 1, 1, true)
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(one.getPixel(0, 0), hsv)
            val h = hsv[0]; val sat = hsv[1].coerceAtLeast(0.25f); val v = hsv[2].coerceIn(0.25f, 0.9f)
            fun hsvColor(hue: Float, value: Float) = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
            settingsPreference.setCustomPrimaryColor(hsvColor(h, v))
            settingsPreference.setCustomSecondaryColor(hsvColor((h + 25f) % 360f, (v * 0.92f).coerceIn(0.25f, 0.9f)))
            settingsPreference.setCustomTertiaryColor(hsvColor((h + 55f) % 360f, (v * 1.05f).coerceIn(0.25f, 0.95f)))
            settingsPreference.setIsCustomColor(true)
            one.recycle(); bitmap.recycle()
        }
    }

    fun exportIconPreset(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val root = JSONObject().apply {
                    put("version", 1)
                    put("globalShape", iconShapeStyle.value.name)
                    put("autoImageColor", autoIconImageColor.value)
                    put("primary", customPrimaryColor.value)
                    put("secondary", customSecondaryColor.value)
                    put("tertiary", customTertiaryColor.value)
                    put("customColorEnabled", isCustomColor.value)
                    val shapes = JSONObject(); menuIconShapes.value.forEach { (k,v) -> shapes.put(k,v) }; put("menuShapes", shapes)
                    val images = JSONObject()
                    customIconImagePaths.value.forEach { (k,path) ->
                        val f=File(path); if (f.exists()) images.put(k, Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
                    }
                    put("menuImages", images)
                }
                context.contentResolver.openOutputStream(uri)?.use { it.write(root.toString(2).toByteArray()) } ?: error("Unable to write preset")
            }.onFailure { android.util.Log.e("ZKM", "Preset export failed", it) }
        }
    }

    fun importIconPreset(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Unable to read preset")
                val root=JSONObject(json)
                root.optString("globalShape").takeIf { it.isNotBlank() }?.let { settingsPreference.setIconShapeStyle(com.zuan.kernelmanager.next.ui.theme.ZkmIconShapeStyle.fromName(it)) }
                settingsPreference.setAutoIconImageColor(root.optBoolean("autoImageColor", false))
                if (root.optBoolean("customColorEnabled", false)) {
                    settingsPreference.setCustomPrimaryColor(root.optInt("primary", customPrimaryColor.value)); settingsPreference.setCustomSecondaryColor(root.optInt("secondary", customSecondaryColor.value)); settingsPreference.setCustomTertiaryColor(root.optInt("tertiary", customTertiaryColor.value)); settingsPreference.setIsCustomColor(true)
                }
                root.optJSONObject("menuShapes")?.let { obj -> obj.keys().forEach { k -> settingsPreference.setMenuIconShape(k, com.zuan.kernelmanager.next.ui.theme.ZkmIconShapeStyle.fromName(obj.optString(k))) } }
                root.optJSONObject("menuImages")?.let { obj ->
                    val dir=File(context.filesDir,"icons").apply{mkdirs()}; obj.keys().forEach { k ->
                        val bytes=Base64.decode(obj.optString(k), Base64.DEFAULT); val f=File(dir,"preset_${k}_${System.currentTimeMillis()}.png"); f.writeBytes(bytes); settingsPreference.setCustomIconImagePathForMenu(k,f.absolutePath)
                    }
                }
            }.onFailure { android.util.Log.e("ZKM", "Preset import failed", it) }
        }
    }

    fun setAppFontChoice(choice: AppFontChoice) {
        viewModelScope.launch { settingsPreference.setAppFontChoice(choice) }
    }

    fun importCustomFont(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val fontsDir = java.io.File(context.filesDir, "fonts").apply { mkdirs() }
                val extension = context.contentResolver.getType(uri)?.let { mime ->
                    when (mime) {
                        "font/ttf" -> "ttf"
                        "font/otf" -> "otf"
                        else -> null
                    }
                } ?: "ttf"
                val target = java.io.File(fontsDir, "custom_font.$extension")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Unable to open font file")
                settingsPreference.setCustomFontPath(target.absolutePath)
                settingsPreference.setAppFontChoice(AppFontChoice.CUSTOM)
            }.onFailure { android.util.Log.e("ZKM", "Operation failed", it) }
        }
    }

    fun setHighRefreshRate(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = if (enabled) {
                val rate = DisplayUtils.getHighestSupportedRefreshRate(context)
                rate > 0 && DisplayUtils.setRefreshRate(rate)
            } else {
                DisplayUtils.resetRefreshRate()
            }
            if (success) settingsPreference.setHighRefreshRate(enabled)
        }
    }
    fun setDynamicColor(enabled: Boolean) { 
        viewModelScope.launch { 
            settingsPreference.setDynamicColorEnabled(enabled)
            if (enabled) {
                settingsPreference.setIsCustomColor(false)
            }
        } 
    }
    fun setThemeColor(color: AppThemeColor) { 
        viewModelScope.launch { 
            settingsPreference.setThemeColor(color.name)
            settingsPreference.setIsCustomColor(false)
            settingsPreference.setDynamicColorEnabled(false)
        } 
    }
    
    // [BARU] Custom Color Actions
    fun setCustomColorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsPreference.setIsCustomColor(enabled)
            if (enabled) {
                settingsPreference.setDynamicColorEnabled(false)
            }
        }
    }
    
    fun setCustomPrimaryColor(color: Color) {
        viewModelScope.launch {
            settingsPreference.setCustomPrimaryColor(color.toArgb())
        }
    }
    
    fun setCustomSecondaryColor(color: Color) {
        viewModelScope.launch {
            settingsPreference.setCustomSecondaryColor(color.toArgb())
        }
    }
    
    fun setCustomTertiaryColor(color: Color) {
        viewModelScope.launch {
            settingsPreference.setCustomTertiaryColor(color.toArgb())
        }
    }
    
    fun setAllCustomColors(primary: Color, secondary: Color, tertiary: Color) {
        viewModelScope.launch {
            settingsPreference.setCustomPrimaryColor(primary.toArgb())
            settingsPreference.setCustomSecondaryColor(secondary.toArgb())
            settingsPreference.setCustomTertiaryColor(tertiary.toArgb())
            settingsPreference.setIsCustomColor(true)
            settingsPreference.setDynamicColorEnabled(false)
        }
    }
    
    fun setBgType(type: BgType) { viewModelScope.launch { settingsPreference.setBgType(type) } }
    fun setSolidColor(color: Int) { viewModelScope.launch { settingsPreference.setSolidColor(color) } }
    fun setExpressiveThemeId(id: Int) { viewModelScope.launch { settingsPreference.setExpressiveThemeId(id) } }
    
    fun setBgBlurEnabled(enabled: Boolean) { viewModelScope.launch { settingsPreference.setBgBlurEnabled(enabled) } }
    fun setBgContrast(value: Float) { viewModelScope.launch { settingsPreference.setBgContrast(value) } }
    fun setCardDarkness(value: Float) { viewModelScope.launch { settingsPreference.setCardDarkness(value) } }

    // [BARU] Toggle Card Blur
    fun setCardBlurEnabled(enabled: Boolean) { viewModelScope.launch { settingsPreference.setHazeEnabled(enabled) } }

    // [BARU] Card Glass Style + Liquid Glass params
    fun setCardGlassStyle(style: CardGlassStyle) { viewModelScope.launch { settingsPreference.setCardGlassStyle(style) } }
    fun setLiquidCornerRadius(value: Float) { viewModelScope.launch { settingsPreference.setLiquidCornerRadius(value) } }
    fun setLiquidBlurRadius(value: Float) { viewModelScope.launch { settingsPreference.setLiquidBlurRadius(value) } }
    fun setLiquidRefractionHeight(value: Float) { viewModelScope.launch { settingsPreference.setLiquidRefractionHeight(value) } }
    fun setLiquidRefractionAmount(value: Float) { viewModelScope.launch { settingsPreference.setLiquidRefractionAmount(value) } }
    fun setLiquidChromaticAberration(enabled: Boolean) { viewModelScope.launch { settingsPreference.setLiquidChromaticAberration(enabled) } }

    // [BARU] Toggle / Slider / Dialog style
    fun setToggleStyle(style: ToggleStyle) { viewModelScope.launch { settingsPreference.setToggleStyle(style) } }
    fun setSliderStyle(style: SliderStyle) { viewModelScope.launch { settingsPreference.setSliderStyle(style) } }
    fun setDialogStyle(style: DialogStyle) { viewModelScope.launch { settingsPreference.setDialogStyle(style) } }

    /** Salin gambar thumb custom (toggle/slider) ke storage app lalu simpan path-nya. */
    fun setCustomControlImage(kind: ControlImageKind, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val dir = java.io.File(context.filesDir, "controls").apply { mkdirs() }
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 256 && bounds.outHeight / (sample * 2) >= 256) sample *= 2
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                val decoded = context.contentResolver.openInputStream(uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, opts)
                } ?: error("Unable to decode image")
                val maxSide = 256
                val scale = minOf(1f, maxSide.toFloat() / maxOf(decoded.width, decoded.height))
                val bmp = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(
                    decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true
                ) else decoded
                val target = java.io.File(dir, "${kind.filePrefix}_${System.currentTimeMillis()}.png")
                target.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                when (kind) {
                    ControlImageKind.TOGGLE_ON -> {
                        settingsPreference.customToggleOnImagePath.value.let { p -> p?.let { java.io.File(it).delete() } }
                        settingsPreference.setCustomToggleOnImagePath(target.absolutePath)
                    }
                    ControlImageKind.TOGGLE_OFF -> {
                        settingsPreference.customToggleOffImagePath.value.let { p -> p?.let { java.io.File(it).delete() } }
                        settingsPreference.setCustomToggleOffImagePath(target.absolutePath)
                    }
                    ControlImageKind.SLIDER_THUMB -> {
                        settingsPreference.customSliderThumbImagePath.value.let { p -> p?.let { java.io.File(it).delete() } }
                        settingsPreference.setCustomSliderThumbImagePath(target.absolutePath)
                    }
                }
            }.onFailure { android.util.Log.e("ZKM", "Custom control image import failed", it) }
        }
    }

    fun clearCustomControlImage(kind: ControlImageKind) {
        viewModelScope.launch(Dispatchers.IO) {
            val old = when (kind) {
                ControlImageKind.TOGGLE_ON -> settingsPreference.customToggleOnImagePath.value.also { settingsPreference.setCustomToggleOnImagePath(null) }
                ControlImageKind.TOGGLE_OFF -> settingsPreference.customToggleOffImagePath.value.also { settingsPreference.setCustomToggleOffImagePath(null) }
                ControlImageKind.SLIDER_THUMB -> settingsPreference.customSliderThumbImagePath.value.also { settingsPreference.setCustomSliderThumbImagePath(null) }
            }
            old?.let { java.io.File(it).delete() }
        }
    }

    // [BARU] Weather Effect Actions
    fun setWeatherEffect(effect: WeatherEffect) {
        viewModelScope.launch { settingsPreference.setWeatherEffect(effect) }
    }

    // [BARU] Avatar Decoration Action
    fun setAvatarDecoration(style: AvatarDecorationStyle) {
        viewModelScope.launch { settingsPreference.setAvatarDecoration(style) }
    }
    
    fun setWeatherIntensity(intensity: Float) {
        viewModelScope.launch { settingsPreference.setWeatherIntensity(intensity) }
    }

    // [BARU] Background Adjustment Actions
    fun setBgSaturation(saturation: Float) {
        viewModelScope.launch { settingsPreference.setBgSaturation(saturation) }
    }
    
    fun setBlurStrength(strength: Float) {
        viewModelScope.launch { settingsPreference.setBlurStrength(strength) }
    }

    // [BETA] Appbar Banner Progressive
    fun setAppBarBannerEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsPreference.setAppBarBannerEnabled(enabled) }
    }

    fun setAppBarBannerDim(dim: Float) {
        viewModelScope.launch { settingsPreference.setAppBarBannerDim(dim) }
    }

    fun setAppBarBannerMedia(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }.onFailure { android.util.Log.w("ZKM", "Banner URI permission not persisted", it) }
            val type = com.zuan.kernelmanager.next.ui.components.AppBarBannerType.detect(context, uri)
            settingsPreference.setAppBarBanner(uri.toString(), type.key)
            settingsPreference.setAppBarBannerEnabled(true)
        }
    }

    fun clearAppBarBanner() {
        viewModelScope.launch {
            settingsPreference.setAppBarBannerEnabled(false)
            settingsPreference.setAppBarBanner(null, "image")
        }
    }

    fun setBackgroundImage(uri: Uri) {
        viewModelScope.launch {
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                settingsPreference.setBackgroundImageUri(uri.toString())
                settingsPreference.setBgType(BgType.GALLERY)
            } catch (e: Exception) {
                android.util.Log.e("ZKM", "Unhandled operation failure", e)
            }
        }
    }
    
    // [BARU] Set background media dengan video detection
    fun setBackgroundMedia(uri: Uri) {
        viewModelScope.launch {
            try {
                // 1. Persist Permission
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)

                // 2. Cek Mime Type
                val mimeType = context.contentResolver.getType(uri)
                val isVideo = mimeType?.startsWith("video") == true

                // 3. Simpan
                settingsPreference.setBackgroundImageUri(uri.toString())
                settingsPreference.setIsVideoWallpaper(isVideo)
                settingsPreference.setBgType(BgType.GALLERY)
            } catch (e: Exception) {
                android.util.Log.e("ZKM", "Unhandled operation failure", e)
                // Fallback
                settingsPreference.setBackgroundImageUri(uri.toString())
                val isVideo = uri.toString().contains("mp4") || uri.toString().contains("mkv") || uri.toString().contains("webm")
                settingsPreference.setIsVideoWallpaper(isVideo)
                settingsPreference.setBgType(BgType.GALLERY)
            }
        }
    }
}

/** Jenis gambar thumb custom untuk kontrol (toggle/slider). */
enum class ControlImageKind(val filePrefix: String) {
    TOGGLE_ON("toggle_on"),
    TOGGLE_OFF("toggle_off"),
    SLIDER_THUMB("slider_thumb"),
}

// Helper function to combine multiple StateFlows
inline fun <T1, T2, T3, T4, T5, R> combineStates(
    flow1: StateFlow<T1>,
    flow2: StateFlow<T2>,
    flow3: StateFlow<T3>,
    flow4: StateFlow<T4>,
    flow5: StateFlow<T5>,
    crossinline transform: (T1, T2, T3, T4, T5) -> R
): kotlinx.coroutines.flow.Flow<R> = kotlinx.coroutines.flow.combine(
    flow1, flow2, flow3, flow4, flow5
) { v1, v2, v3, v4, v5 ->
    @Suppress("UNCHECKED_CAST")
    transform(v1 as T1, v2 as T2, v3 as T3, v4 as T4, v5 as T5)
}
