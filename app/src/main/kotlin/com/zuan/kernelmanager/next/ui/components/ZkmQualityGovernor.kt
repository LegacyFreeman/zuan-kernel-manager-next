/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.ui.components

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.view.Choreographer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tingkat kualitas grafis adaptif untuk ZKM Next.
 */
enum class ZkmQualityTier {
    /** Performa maksimal / grafis penuh: blur penuh (20-22dp), 100% resolusi video, animasi penuh. */
    HIGH,

    /** Seimbang: blur sedikit diperkecil (~12dp), resolusi video 75%, blur dibekukan saat scroll kencang. */
    BALANCED,

    /** Hemat daya / kondisi panas: blur minimal (4-6dp) atau solid scrim, resolusi video 50%. */
    LOW
}

/**
 * Snapshot metrik performa real-time untuk pemantauan dan debug panel HUD.
 */
data class ZkmGovernorMetrics(
    val currentFps: Float = 60f,
    val frameTimeMs: Float = 16.6f,
    val jankFrames: Int = 0,
    val thermalStatus: Int = 0, // PowerManager.THERMAL_STATUS_*
    val thermalHeadroom: Float = 1.0f,
    val tier: ZkmQualityTier = ZkmQualityTier.HIGH,
    val isScrollFrozen: Boolean = false,
)

/**
 * Governor Kualitas Grafis Adaptif (ZKM Perf Tahap 3).
 *
 * Mengukur beban frame (Choreographer) dan suhu perangkat (PowerManager) secara dinamis.
 * Menerapkan hysteresis:
 * - Downgrade cepat saat terjadi frame drop / thermal naik agar tidak patah-patah.
 * - Upgrade lambat (perlu stabil >= 8 detik) agar kualitas tidak naik-turun (flapping).
 */
object ZkmQualityGovernor {

    private val _tier = MutableStateFlow(ZkmQualityTier.HIGH)
    val tier: StateFlow<ZkmQualityTier> = _tier.asStateFlow()

    private val _metrics = MutableStateFlow(ZkmGovernorMetrics())
    val metrics: StateFlow<ZkmGovernorMetrics> = _metrics.asStateFlow()

    var isScrollFrozen: Boolean by mutableStateOf(false)
        private set

    @Volatile private var isRunning = false
    @Volatile private var foregroundActivities = 0
    @Volatile private var lastFrameNanos = 0L
    @Volatile private var frameCount = 0
    @Volatile private var jankCount = 0
    @Volatile private var totalFrameDeltaMs = 0f
    @Volatile private var lastEvalTimeMs = 0L
    @Volatile private var highStabilityCounter = 0 // Hitungan detik kondisi stabil sebelum upgrade

    private var powerManager: PowerManager? = null

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning) return

            if (lastFrameNanos != 0L) {
                val deltaMs = (frameTimeNanos - lastFrameNanos) / 1_000_000f
                if (deltaMs in 1f..200f) {
                    frameCount++
                    totalFrameDeltaMs += deltaMs
                    if (deltaMs > 24f) { // Frame lebih lama dari ~41 fps
                        jankCount++
                    }
                }
            }
            lastFrameNanos = frameTimeNanos

            val now = SystemClock.uptimeMillis()
            if (now - lastEvalTimeMs >= 1000L) { // Evaluasi setiap 1 detik
                evaluateGovernor(now)
            }

            if (isRunning) {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    /**
     * Inisialisasi governor saat aplikasi start. Aman dipanggil berulang.
     */
    fun init(context: Context) {
        val app = context.applicationContext as? Application ?: return
        if (powerManager == null) {
            powerManager = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        }

        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                foregroundActivities++
                if (foregroundActivities == 1) {
                    startMonitoring()
                }
            }

            override fun onActivityStopped(activity: Activity) {
                foregroundActivities = (foregroundActivities - 1).coerceAtLeast(0)
                if (foregroundActivities == 0) {
                    stopMonitoring()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        if (foregroundActivities > 0 || isRunning.not()) {
            startMonitoring()
        }
    }

    private fun startMonitoring() {
        if (isRunning) return
        isRunning = true
        lastFrameNanos = 0L
        frameCount = 0
        jankCount = 0
        totalFrameDeltaMs = 0f
        lastEvalTimeMs = SystemClock.uptimeMillis()
        try {
            Choreographer.getInstance().postFrameCallback(frameCallback)
        } catch (_: Exception) {}
    }

    private fun stopMonitoring() {
        isRunning = false
        try {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        } catch (_: Exception) {}
    }

    private fun evaluateGovernor(now: Long) {
        val count = frameCount.coerceAtLeast(1)
        val avgFrameMs = totalFrameDeltaMs / count
        val calculatedFps = if (avgFrameMs > 0f) (1000f / avgFrameMs).coerceIn(10f, 165f) else 60f
        val currentJank = jankCount

        // Reset akumulator frame
        frameCount = 0
        jankCount = 0
        totalFrameDeltaMs = 0f
        lastEvalTimeMs = now

        // Baca thermal
        var thermalStatus = 0
        var thermalHeadroom = 1.0f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager?.let { pm ->
                thermalStatus = pm.currentThermalStatus
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    thermalHeadroom = runCatching { pm.getThermalHeadroom(10) }.getOrDefault(1.0f)
                }
            }
        }

        val isSevereThermal = thermalStatus >= 2 // MODERATE (2) atau SEVERE (3)
        val isCriticalThermal = thermalStatus >= 3
        val current = _tier.value

        var nextTier = current

        // 1. EVALUASI DOWNGRADE (Cepat, agresif mencegah stutter & overheat)
        if (isCriticalThermal || avgFrameMs > 32f || currentJank >= 8) {
            // Beban sangat berat atau suhu kritis -> langsung ke LOW
            nextTier = ZkmQualityTier.LOW
            highStabilityCounter = 0
        } else if (isSevereThermal || avgFrameMs > 22f || currentJank >= 4) {
            // Beban mulai turun di bawah 45fps atau thermal moderate -> turunkan 1 tingkat
            nextTier = when (current) {
                ZkmQualityTier.HIGH -> ZkmQualityTier.BALANCED
                ZkmQualityTier.BALANCED -> ZkmQualityTier.LOW
                ZkmQualityTier.LOW -> ZkmQualityTier.LOW
            }
            highStabilityCounter = 0
        } else {
            // 2. EVALUASI UPGRADE (Konservatif / Hysteresis): Perlu stabil minimal 8 detik
            if (avgFrameMs < 14f && currentJank <= 1 && thermalStatus <= 1) {
                highStabilityCounter++
                if (highStabilityCounter >= 8) { // 8 detik mulus terus menerus
                    nextTier = when (current) {
                        ZkmQualityTier.LOW -> ZkmQualityTier.BALANCED
                        ZkmQualityTier.BALANCED -> ZkmQualityTier.HIGH
                        ZkmQualityTier.HIGH -> ZkmQualityTier.HIGH
                    }
                    highStabilityCounter = 0
                }
            } else {
                highStabilityCounter = (highStabilityCounter - 1).coerceAtLeast(0)
            }
        }

        if (nextTier != current) {
            _tier.value = nextTier
        }

        _metrics.value = ZkmGovernorMetrics(
            currentFps = calculatedFps,
            frameTimeMs = avgFrameMs,
            jankFrames = currentJank,
            thermalStatus = thermalStatus,
            thermalHeadroom = thermalHeadroom,
            tier = nextTier,
            isScrollFrozen = isScrollFrozen,
        )
    }

    /**
     * Beri tahu governor status scroll cepat.
     */
    fun setFastScrolling(active: Boolean) {
        if (isScrollFrozen != active) {
            isScrollFrozen = active
            _metrics.value = _metrics.value.copy(isScrollFrozen = active)
        }
    }

    /**
     * Hitung radius blur efektif berdasarkan tier saat ini dan status scroll.
     */
    fun effectiveBlurRadius(baseRadius: Dp): Dp {
        if (ZkmLite.active) return 0.dp
        if (isScrollFrozen) return 0.dp // Bekukan total saat scroll kencang

        return when (_tier.value) {
            ZkmQualityTier.HIGH -> baseRadius
            ZkmQualityTier.BALANCED -> (baseRadius * 0.65f).coerceAtLeast(8.dp)
            ZkmQualityTier.LOW -> (baseRadius * 0.30f).coerceAtLeast(4.dp)
        }
    }

    /**
     * Skala resolusi decode video wallpaper (TextureView buffer size).
     */
    fun videoDownscaleScale(): Float = when (_tier.value) {
        ZkmQualityTier.HIGH -> 1.0f
        ZkmQualityTier.BALANCED -> 0.75f
        ZkmQualityTier.LOW -> 0.50f
    }
}

/**
 * CompositionLocal untuk mendeteksi apakah list di layar sedang melakukan scroll.
 */
val LocalZkmScrollInProgress = compositionLocalOf { false }
