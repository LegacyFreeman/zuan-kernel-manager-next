/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.home.menu

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import com.zuan.kernelmanager.next.library.shell.suBlocking
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.roundToInt

data class DisplayDashboardInfo(
    val resolution: String,
    val technology: String, 
    val physicalSize: String, 
    val diagonalSize: String, 
    val density: String,
    val xdpiYdpi: String,
    val gpuRenderer: String,
    val refreshRate: String,
    val orientation: String,
    val openGlVersion: String,
    val hdrCapabilities: String,
    val isWideColorGamut: Boolean
)

object DisplayUtils {

    fun getCurrentResolution(context: Context): DisplayResolution {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay
        val size = Point()
        display.getRealSize(size)
        return DisplayResolution(size.x, size.y)
    }

    fun getCurrentDpi(context: Context): Int {
        return try {
            val result = suBlocking("wm density")
            if (result.isSuccess) {
                val output = result.outputString
                // "wm density" mencetak Physical (bawaan panel) dan Override (nilai aktif jika diubah).
                // Sebelumnya hanya Physical yang dibaca, jadi DPI aktif selalu salah setelah di-override.
                val override = "Override density: (\\d+)".toRegex().find(output)?.groupValues?.get(1)?.toIntOrNull()
                val physical = "Physical density: (\\d+)".toRegex().find(output)?.groupValues?.get(1)?.toIntOrNull()
                override ?: physical ?: context.resources.configuration.densityDpi
            } else {
                context.resources.configuration.densityDpi
            }
        } catch (e: Exception) {
            context.resources.configuration.densityDpi
        }
    }

    fun getPhysicalDpi(context: Context): Int = try {
        val out = suBlocking("wm density").outputString
        "Physical density: (\\d+)".toRegex().find(out)?.groupValues?.get(1)?.toIntOrNull()
            ?: context.resources.displayMetrics.densityDpi
    } catch (_: Exception) { context.resources.displayMetrics.densityDpi }

    fun getPhysicalResolution(context: Context): DisplayResolution = try {
        val out = suBlocking("wm size").outputString
        val m = "Physical size: (\\d+)x(\\d+)".toRegex().find(out)
        if (m != null) DisplayResolution(m.groupValues[1].toInt(), m.groupValues[2].toInt()) else getCurrentResolution(context)
    } catch (_: Exception) { getCurrentResolution(context) }

    fun getCurrentRefreshRate(context: Context): Int = try {
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.refreshRate.roundToInt()
    } catch (_: Exception) { 60 }

    fun setDpi(dpi: Int): Boolean = suBlocking("wm density $dpi").isSuccess
    fun resetDpi(): Boolean = suBlocking("wm density reset").isSuccess
    fun setResolution(width: Int, height: Int): Boolean = suBlocking("wm size ${width}x$height").isSuccess
    fun resetResolution(): Boolean = suBlocking("wm size reset").isSuccess

    fun setSaturation(saturation: Float): Boolean {
        return suBlocking("service call SurfaceFlinger 1022 f $saturation").isSuccess
    }

    fun getDashboardInfo(context: Context): DisplayDashboardInfo {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay
        val metrics = DisplayMetrics()
        display.getRealMetrics(metrics)

        val widthMm = metrics.widthPixels / metrics.xdpi * 25.4
        val heightMm = metrics.heightPixels / metrics.ydpi * 25.4
        val widthIn = metrics.widthPixels / metrics.xdpi
        val heightIn = metrics.heightPixels / metrics.ydpi
        val diagonal = sqrt(widthIn.pow(2) + heightIn.pow(2))

        val gpu = suBlocking("getprop ro.hardware.egl").out.firstOrNull() ?: "Unknown Adreno/Mali"
        val opengl = try {
            val res = suBlocking("dumpsys SurfaceFlinger | grep \"GLES:\"")
            res.out.firstOrNull()?.substringAfter("GLES:")?.trim() ?: "ES 3.2"
        } catch (e: Exception) { "3.2" }

        val hdr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            display.hdrCapabilities?.supportedHdrTypes?.joinToString(", ") { type ->
                when(type) {
                    1 -> "Dolby Vision"; 2 -> "HDR10"; 3 -> "HLG"; 4 -> "HDR10+"; else -> "HDR"
                }
            } ?: "None"
        } else "N/A"

        val orientation = when(context.resources.configuration.orientation) {
            1 -> "Portrait"; 2 -> "Landscape"; else -> "Unknown"
        }

        return DisplayDashboardInfo(
            resolution = "${metrics.widthPixels} x ${metrics.heightPixels}",
            technology = "Unknown",
            physicalSize = "${widthMm.toInt()} x ${heightMm.toInt()} mm",
            diagonalSize = "%.2f inches".format(diagonal),
            density = "${metrics.densityDpi} dpi (${getDensityName(metrics.densityDpi)})",
            xdpiYdpi = "%.1f / %.1f dpi".format(metrics.xdpi, metrics.ydpi),
            gpuRenderer = gpu,
            refreshRate = "${display.refreshRate} Hz",
            orientation = orientation,
            openGlVersion = opengl,
            hdrCapabilities = hdr,
            isWideColorGamut = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) display.isWideColorGamut else false
        )
    }

    private fun getDensityName(dpi: Int): String {
        return when {
            dpi <= 120 -> "ldpi"; dpi <= 160 -> "mdpi"; dpi <= 240 -> "hdpi"
            dpi <= 320 -> "xhdpi"; dpi <= 480 -> "xxhdpi"; dpi <= 640 -> "xxxhdpi"
            else -> "xxxxhdpi"
        }
    }

    fun getAvailableResolutions(): List<DisplayResolution> = listOf(
        DisplayResolution(1080, 2400), DisplayResolution(1080, 2340),
        DisplayResolution(1080, 1920), DisplayResolution(720, 1600),
        DisplayResolution(720, 1520), DisplayResolution(1440, 3200),
        DisplayResolution(1440, 3088), DisplayResolution(1220, 2712)
    )

    fun getAvailableDpiValues(): List<Int> = listOf(320, 360, 392, 400, 420, 440, 480, 560, 600)
    fun getAvailableRefreshRates(): List<Int> = listOf(30, 48, 60, 90, 120, 144, 165)

    fun setRefreshRate(rate: Int): Boolean {
        val min = suBlocking("settings put system min_refresh_rate $rate")
        val peak = suBlocking("settings put system peak_refresh_rate $rate")
        return min.isSuccess && peak.isSuccess
    }

    fun resetRefreshRate(): Boolean {
        return suBlocking("settings delete system min_refresh_rate; settings delete system peak_refresh_rate").isSuccess
    }

    fun getHighestSupportedRefreshRate(context: Context): Int {
        return try {
            val display = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
            display.supportedModes.maxOfOrNull { it.refreshRate }?.roundToInt() ?: display.refreshRate.roundToInt()
        } catch (_: Exception) {
            0
        }
    }

    fun setScreenTimeout(seconds: Int): Boolean = suBlocking("settings put system screen_off_timeout ${seconds * 1000}").isSuccess

    fun getScreenTimeout(context: Context): Int {
        return try { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT) / 1000 } catch (e: Exception) { 30 }
    }

    fun setBrightnessMode(auto: Boolean): Boolean {
        val mode = if (auto) 1 else 0
        return suBlocking("settings put system screen_brightness_mode $mode").isSuccess
    }

    fun getBrightnessMode(context: Context): Boolean {
        return try { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) == 1 } catch (e: Exception) { false }
    }

    fun setBrightness(value: Int): Boolean {
        val brightness = value.coerceIn(0, 255)
        return suBlocking("settings put system screen_brightness $brightness").isSuccess
    }

    fun getBrightness(context: Context): Int {
        return try { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) } catch (e: Exception) { 128 }
    }

    fun setNightMode(enabled: Boolean): Boolean {
        val value = if (enabled) 1 else 0
        return suBlocking("settings put secure night_display_activated $value").isSuccess
    }

    fun isNightModeEnabled(context: Context): Boolean {
        return try { Settings.Secure.getInt(context.contentResolver, "night_display_activated") == 1 } catch (e: Exception) { false }
    }

    fun setFontScale(scale: Float): Boolean = suBlocking("settings put system font_scale $scale").isSuccess

    fun getFontScale(context: Context): Float {
        return try { Settings.System.getFloat(context.contentResolver, Settings.System.FONT_SCALE) } catch (e: Exception) { 1.0f }
    }

    fun setAnimationScale(scale: Float): Boolean {
        val a = suBlocking("settings put global window_animation_scale $scale")
        val b = suBlocking("settings put global transition_animation_scale $scale")
        val c = suBlocking("settings put global animator_duration_scale $scale")
        return a.isSuccess && b.isSuccess && c.isSuccess
    }

    fun getAnimationScale(context: Context): Float {
        return try { Settings.Global.getFloat(context.contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE) } catch (e: Exception) { 1.0f }
    }

    fun setDisplayInversion(enabled: Boolean): Boolean {
        val value = if (enabled) 1 else 0
        return suBlocking("settings put secure accessibility_display_inversion_enabled $value").isSuccess
    }

    fun isDisplayInversionEnabled(context: Context): Boolean {
        return try { Settings.Secure.getInt(context.contentResolver, "accessibility_display_inversion_enabled") == 1 } catch (e: Exception) { false }
    }

    // ==========================================
    // ADVANCED PANEL TUNING (HBM, DC-DIMMING, KCAL)
    // ==========================================

    private val HBM_CANDIDATE_PATHS = listOf(
        "/sys/class/drm/card0-DSI-1/hbm",
        "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/hbm",
        "/sys/devices/virtual/graphics/fb0/hbm",
        "/sys/class/graphics/fb0/hbm",
        "/sys/kernel/hbm/hbm_state",
        "/sys/devices/platform/soc/soc:qcom,dsi-display/hbm"
    )

    private val DC_DIMMING_CANDIDATES = listOf(
        "/sys/class/drm/card0-DSI-1/dimlayer_bl_en",
        "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/dimlayer_bl_en",
        "/sys/kernel/debug/dri/0/DSI-1/dimlayer_bl_en",
        "/sys/class/graphics/fb0/dimlayer_bl_en",
        "/sys/devices/virtual/graphics/fb0/dimlayer_bl_en",
        "/sys/devices/platform/soc/soc:qcom,dsi-display/dimlayer_bl_en"
    )

    const val KCAL_CTRL_DIR = "/sys/devices/platform/kcal_ctrl.0"
    const val KCAL_RGB_NODE = "$KCAL_CTRL_DIR/kcal"
    const val KCAL_ENABLE_NODE = "$KCAL_CTRL_DIR/kcal_enable"

    fun getHbmNode(): String? = HBM_CANDIDATE_PATHS.firstOrNull {
        com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime.nodeExistsBlocking(it)
    }

    fun isHbmSupported(): Boolean = getHbmNode() != null

    fun isHbmActive(): Boolean {
        val node = getHbmNode() ?: return false
        val value = com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime.readNodeBlocking(node).trim()
        return value == "1" || value == "2" || value.equals("on", ignoreCase = true)
    }

    fun setHbm(active: Boolean): Boolean {
        val node = getHbmNode() ?: return false
        val target = if (active) "1" else "0"
        return com.zuan.kernelmanager.next.core.kernel.KernelControlEngine.setKernelNodeBlocking(node, target, verify = false).success
    }

    fun getDcDimmingNode(): String? = DC_DIMMING_CANDIDATES.firstOrNull {
        com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime.nodeExistsBlocking(it)
    }

    fun isDcDimmingSupported(): Boolean = getDcDimmingNode() != null

    fun isDcDimmingActive(): Boolean {
        val node = getDcDimmingNode() ?: return false
        val value = com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime.readNodeBlocking(node).trim()
        return value == "1" || value.equals("on", ignoreCase = true)
    }

    fun setDcDimming(active: Boolean): Boolean {
        val node = getDcDimmingNode() ?: return false
        val target = if (active) "1" else "0"
        return com.zuan.kernelmanager.next.core.kernel.KernelControlEngine.setKernelNodeBlocking(node, target, verify = false).success
    }

    fun isKcalSupported(): Boolean =
        com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime.nodeExistsBlocking(KCAL_RGB_NODE)

    fun getKcalConfig(): KcalConfig {
        if (!isKcalSupported()) return KcalConfig(256, 256, 256, enabled = false, supported = false)
        val raw = com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime.readNodeBlocking(KCAL_RGB_NODE).trim()
        val parts = raw.split(Regex("""\s+""")).mapNotNull { it.toIntOrNull() }
        val r = parts.getOrNull(0) ?: 256
        val g = parts.getOrNull(1) ?: 256
        val b = parts.getOrNull(2) ?: 256
        val enabledRaw = com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime.readNodeBlocking(KCAL_ENABLE_NODE).trim()
        val enabled = enabledRaw == "1"
        return KcalConfig(r, g, b, enabled = enabled, supported = true)
    }

    fun setKcalRgb(r: Int, g: Int, b: Int): Boolean {
        if (!isKcalSupported()) return false
        val clampedR = r.coerceIn(0, 256)
        val clampedG = g.coerceIn(0, 256)
        val clampedB = b.coerceIn(0, 256)
        val rgbStr = "$clampedR $clampedG $clampedB"
        val writeRgb = com.zuan.kernelmanager.next.core.kernel.KernelControlEngine.setKernelNodeBlocking(KCAL_RGB_NODE, rgbStr, verify = false).success
        val writeEn = com.zuan.kernelmanager.next.core.kernel.KernelControlEngine.setKernelNodeBlocking(KCAL_ENABLE_NODE, "1", verify = false).success
        return writeRgb && writeEn
    }

    fun resetKcal(): Boolean = setKcalRgb(256, 256, 256)
}

data class KcalConfig(
    val r: Int = 256,
    val g: Int = 256,
    val b: Int = 256,
    val enabled: Boolean = false,
    val supported: Boolean = false
)

data class AdvancedDisplayFeatures(
    val hasHbm: Boolean = false,
    val isHbmActive: Boolean = false,
    val hasDcDimming: Boolean = false,
    val isDcDimmingActive: Boolean = false,
    val kcal: KcalConfig = KcalConfig()
)

data class DisplayResolution(val width: Int, val height: Int) {
    override fun toString(): String = "${width}x$height"
}
