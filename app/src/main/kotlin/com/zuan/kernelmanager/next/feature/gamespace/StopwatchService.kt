/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 *
 * Plugin "Fast Stopwatch": stopwatch melayang yang bisa digeser, untuk menghitung cooldown / respawn.
 * Dimulai & dihentikan oleh [GameDetectorService] bersama sesi game bila plugin aktif.
 */
package com.zuan.kernelmanager.next.feature.gamespace

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zuan.kernelmanager.next.feature.overlay.BaseOverlayService
import com.zuan.kernelmanager.next.feature.overlay.overlayDrag
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class StopwatchService : BaseOverlayService() {

    companion object {
        @Volatile var isRunning = false
            private set

        fun start(c: Context) {
            runCatching { ContextCompat.startForegroundService(c, Intent(c, StopwatchService::class.java)) }
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, StopwatchService::class.java))
        }
    }

    override val notificationId = 133
    override fun onRunningChanged(running: Boolean) { isRunning = running }

    override fun buildLayoutParams() = overlayParams().apply {
        x = 32
        y = 96
    }

    @Composable
    override fun OverlayContent() {
        val accent = remember { Color(GameSpacePrefs.accent(this)) }
        var running by remember { mutableStateOf(false) }
        var elapsed by remember { mutableLongStateOf(0L) }

        LaunchedEffect(running) {
            if (!running) return@LaunchedEffect
            val t0 = SystemClock.elapsedRealtime() - elapsed
            while (isActive) {
                elapsed = SystemClock.elapsedRealtime() - t0
                delay(50)
            }
        }

        Row(
            Modifier
                .overlayDrag { dx, dy -> moveBy(dx, dy) }
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xCC0A0C14))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val s = elapsed / 1000
            val tenth = (elapsed % 1000) / 100
            Text(
                "%02d:%02d.%d".format(s / 60, s % 60, tenth),
                color = if (running) accent else Color.White,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp
            )
            Spacer(Modifier.width(10.dp))
            Icon(
                if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White,
                modifier = Modifier.size(28.dp).clickable { running = !running }
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Rounded.Refresh, null, tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(28.dp).clickable { running = false; elapsed = 0L }
            )
        }
    }
}
