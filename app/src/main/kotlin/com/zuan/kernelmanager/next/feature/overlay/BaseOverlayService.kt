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
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.feature.process.MyLifecycleOwner
import com.zuan.kernelmanager.next.ui.MainActivity
import kotlin.math.roundToInt

/**
 * =====================================================================================
 * BASE OVERLAY SERVICE
 *
 * Kerangka bersama untuk jendela melayang (System / Mini / Thread / Temperature Monitor):
 * foreground notification, ComposeView di WindowManager (TYPE_APPLICATION_OVERLAY),
 * lifecycle owner, serta helper drag. Subclass cukup mengisi [buildLayoutParams] dan
 * [OverlayContent], dan mengisi flag `isRunning` miliknya di [onRunningChanged] supaya
 * switch di Quick Tools selalu sinkron.
 * =====================================================================================
 */
abstract class BaseOverlayService : Service() {

    protected lateinit var windowManager: WindowManager
    protected lateinit var params: WindowManager.LayoutParams
    private var overlayView: ComposeView? = null
    private var lifecycleOwner: MyLifecycleOwner? = null

    protected abstract val notificationId: Int
    protected abstract fun onRunningChanged(running: Boolean)
    protected abstract fun buildLayoutParams(): WindowManager.LayoutParams

    @Composable
    protected abstract fun OverlayContent()

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Dipanggil saat pengguna menyentuh area di luar jendela overlay (butuh FLAG_WATCH_OUTSIDE_TOUCH
     * pada [params]). Sentuhan tetap diteruskan ke app di bawahnya.
     */
    protected open fun onOutsideTouch() {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onCreate() {
        super.onCreate()
        startAsForeground()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        params = buildLayoutParams()

        val owner = MyLifecycleOwner()
        val store = ViewModelStore()
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(object : ViewModelStoreOwner {
                override val viewModelStore: ViewModelStore = store
            })
            setOnTouchListener { _, e ->
                if (e.actionMasked == MotionEvent.ACTION_OUTSIDE) { onOutsideTouch(); true } else false
            }
            owner.performRestore(null)
            owner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            owner.handleLifecycleEvent(Lifecycle.Event.ON_START)
            owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            setContent {
                // Font overlay tidak ikut skala font sistem -> ukuran sama dengan monitor referensi.
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 1f)) {
                    MaterialTheme(colorScheme = darkColorScheme()) {
                        // Reset gaya teks bawaan Material3 (lineHeight 24sp) yang bikin Text kecil jadi renggang.
                        ProvideTextStyle(TextStyle.Default) { OverlayContent() }
                    }
                }
            }
        }

        try {
            windowManager.addView(view, params)
        } catch (e: Exception) {
            // Izin overlay dicabut / window gagal dibuat -> jangan jadi service zombie.
            android.util.Log.e("ZKM", "Overlay addView failed", e)
            stopSelf()
            return
        }
        lifecycleOwner = owner
        overlayView = view
        onRunningChanged(true)
    }

    override fun onDestroy() {
        onRunningChanged(false)
        overlayView?.let { view ->
            runCatching { view.disposeComposition() }
            runCatching { windowManager.removeView(view) }
        }
        lifecycleOwner?.apply {
            handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        overlayView = null
        lifecycleOwner = null
        super.onDestroy()
    }

    // ---- helper untuk subclass -----------------------------------------------------------

    /** Geser jendela (dipanggil dari modifier [overlayDrag]). Gravity harus TOP|START. */
    protected fun moveBy(dx: Float, dy: Float) {
        params.x += dx.roundToInt()
        params.y += dy.roundToInt()
        applyLayout()
    }

    protected fun applyLayout() {
        overlayView?.let { runCatching { windowManager.updateViewLayout(it, params) } }
    }

    /**
     * LayoutParams dasar overlay. [touchable] = false membuat jendela tembus sentuhan
     * (dipakai Mini Monitor supaya tidak menghalangi status bar / app di bawahnya).
     */
    protected fun overlayParams(
        width: Int = WindowManager.LayoutParams.WRAP_CONTENT,
        height: Int = WindowManager.LayoutParams.WRAP_CONTENT,
        touchable: Boolean = true
    ): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Boleh menggambar sampai area punch-hole / notch.
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun startAsForeground() {
        val channelId = CHANNEL_ID
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channelId, "Overlay Tools", NotificationManager.IMPORTANCE_LOW)
        )
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.fps_notification_title))
            .setContentText(getString(R.string.fps_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
        startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    private companion object {
        const val CHANNEL_ID = "zkm_overlay_tools"
    }
}

/** Drag jendela overlay: pasang pada area yang boleh dipakai untuk menggeser. */
fun Modifier.overlayDrag(onDrag: (Float, Float) -> Unit): Modifier = pointerInput(Unit) {
    detectDragGestures { change, dragAmount ->
        change.consume()
        onDrag(dragAmount.x, dragAmount.y)
    }
}
