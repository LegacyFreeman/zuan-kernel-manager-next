/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.library.device.battery.BatteryHistory
import com.zuan.kernelmanager.next.library.device.battery.BatterySampler
import com.zuan.kernelmanager.next.library.device.usage.UsageStatsHelper
import com.zuan.kernelmanager.next.ui.MainActivity
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * =====================================================================================
 * LIVE STATS NOTIFICATION
 *
 * Notifikasi realtime satu baris: "-2.99W  64%  40.3°C". Judul = aplikasi yang sedang di
 * depan (butuh Usage access; kalau belum ada, judulnya "Live Stats"). Service ini juga
 * mengisi [BatteryHistory] supaya layar Charge Stats & Battery Usage punya data.
 * Dinyalakan dari Quick Tools.
 * =====================================================================================
 */
class LiveStatsNotificationService : Service() {

    companion object {
        @Volatile var isRunning = false

        private const val CHANNEL_ID = "zkm_live_stats"
        private const val NOTIF_ID = 2101
        private const val INTERVAL_SEC = 2L

        fun start(context: Context) {
            val intent = Intent(context, LiveStatsNotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LiveStatsNotificationService::class.java))
        }
    }

    private var executor: ScheduledExecutorService? = null
    private var task: ScheduledFuture<*>? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val first = buildNotification("Live Stats", "…")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, first)
        }
        isRunning = true

        val exec = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "ZKM-LiveStats").apply { isDaemon = true }
        }
        executor = exec
        task = exec.scheduleWithFixedDelay({ tick() }, 0L, INTERVAL_SEC, TimeUnit.SECONDS)
    }

    private fun tick() {
        runCatching {
            BatteryHistory.record(this)
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isInteractive) return // layar mati: tidak perlu memperbarui notifikasi

            val r = BatterySampler.read(this)
            val fg = UsageStatsHelper.foregroundPackage(this)
            val title = if (fg.isNotEmpty()) UsageStatsHelper.appLabel(this, fg) else "Live Stats"
            val text = String.format(Locale.US, "%+.2fW  %d%%  %.1f°C", r.watt, r.level, r.tempC)
            getSystemService(NotificationManager::class.java)
                .notify(NOTIF_ID, buildNotification(title, text))
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_battery_android_frame_full)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Live Stats", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Realtime power, battery and temperature"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        isRunning = false
        task?.cancel(false)
        executor?.shutdownNow()
        BatteryHistory.flush(this)
        super.onDestroy()
    }
}
