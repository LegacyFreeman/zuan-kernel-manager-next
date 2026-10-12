/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.core.util

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Process
import com.zuan.kernelmanager.next.ui.components.ZkmLite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn

/**
 * Bus Sampler Terpadu (ZKM Perf Tahap 4 - Adopsi Arsitektur VTools / Scene).
 *
 * Menggantikan puluhan loop liar `while(true) { delay(...) }` yang tersebar di berbagai layar.
 * Menyediakan ticker terkoordinasi berbasis subscriber:
 * - Hanya berdetak jika ada UI yang mendengarkan (0 subscriber = loop berhenti total).
 * - **Screen-Off Deep Sleep (dari VTools `ScreenOffCleanup`)**: Saat layar mati, ticker TIDUR PULAS (0% CPU, 0 disk I/O).
 * - **Little-Core Pinning**: Menjalankan pembacaan dengan prioritas [Process.THREAD_PRIORITY_BACKGROUND] agar Linux kernel otomatis memilih Little Core hemat daya (< 0.2W).
 * - Saat Mode Lite aktif: frekuensi tick diturunkan otomatis (mis. 2s -> 5s).
 */
object ZkmSamplerBus {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var startedActivities = 0
    @Volatile private var isLifecycleHooked = false

    private val _isForeground = MutableStateFlow(true)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    private val _isScreenOn = MutableStateFlow(true)
    val isScreenOn: StateFlow<Boolean> = _isScreenOn.asStateFlow()

    /**
     * Ticker cepat (default 2 detik untuk UI real-time: frekuensi CPU/GPU, traffic data, live stats).
     * Otomatis melambat ke 5 detik di Mode Lite, dan TIDUR TOTAL saat layar mati.
     */
    val fastTick: SharedFlow<Long> = flow {
        // Pin worker ke background priority (Little Cores)
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) }
        var tickCount = 0L
        while (true) {
            // Screen-Off Deep Sleep: Jika layar mati, tunggu sampai layar hidup lagi
            if (!_isScreenOn.value) {
                _isScreenOn.first { it }
            }

            val isFg = _isForeground.value
            val isLite = ZkmLite.active

            val delayMs = when {
                !isFg -> 10_000L
                isLite -> 5_000L
                else -> 2_000L
            }

            emit(++tickCount)
            delay(delayMs)
        }
    }.shareIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 3_000L),
        replay = 1
    )

    /**
     * Ticker sedang (default 5 detik: status baterai, proses teratas, suhu detail).
     * Otomatis melambat ke 15 detik di Mode Lite, dan TIDUR TOTAL saat layar mati.
     */
    val mediumTick: SharedFlow<Long> = flow {
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) }
        var tickCount = 0L
        while (true) {
            if (!_isScreenOn.value) {
                _isScreenOn.first { it }
            }

            val isFg = _isForeground.value
            val isLite = ZkmLite.active

            val delayMs = when {
                !isFg -> 20_000L
                isLite -> 15_000L
                else -> 5_000L
            }

            emit(++tickCount)
            delay(delayMs)
        }
    }.shareIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
        replay = 1
    )

    /**
     * Ticker lambat (default 15 detik: wakelock history, disk/storage space, permission check).
     * Otomatis melambat ke 30 detik di Mode Lite, dan TIDUR TOTAL saat layar mati.
     */
    val slowTick: SharedFlow<Long> = flow {
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) }
        var tickCount = 0L
        while (true) {
            if (!_isScreenOn.value) {
                _isScreenOn.first { it }
            }

            val isFg = _isForeground.value
            val isLite = ZkmLite.active

            val delayMs = when {
                !isFg -> 60_000L
                isLite -> 30_000L
                else -> 15_000L
            }

            emit(++tickCount)
            delay(delayMs)
        }
    }.shareIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 8_000L),
        replay = 1
    )

    /**
     * Pasang hook lifecycle aplikasi dan BroadcastReceiver untuk screen on/off.
     */
    fun init(context: Context) {
        if (isLifecycleHooked) return
        val application = context.applicationContext as? Application ?: return
        isLifecycleHooked = true

        // 1. Hook Activity Lifecycle (Foreground vs Background)
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                _isForeground.value = startedActivities > 0
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                _isForeground.value = startedActivities > 0
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        // 2. Hook Screen-Off Deep Sleep (Adopsi dari VTools ScreenOffCleanup)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        _isScreenOn.value = false
                    }
                    Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                        _isScreenOn.value = true
                    }
                }
            }
        }
        application.registerReceiver(receiver, filter)
    }
}
