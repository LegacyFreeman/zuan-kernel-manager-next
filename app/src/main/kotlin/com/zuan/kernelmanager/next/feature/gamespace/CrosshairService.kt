/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.feature.gamespace

import android.content.Context
import android.content.Intent
import android.view.Gravity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.zuan.kernelmanager.next.feature.overlay.BaseOverlayService

/** Crosshair di tengah layar. Jendelanya tembus sentuhan, jadi tidak mengganggu permainan. */
class CrosshairService : BaseOverlayService() {

    companion object {
        @Volatile var isRunning = false
            private set

        const val STYLE_COUNT = 8

        fun start(c: Context) {
            runCatching { ContextCompat.startForegroundService(c, Intent(c, CrosshairService::class.java)) }
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, CrosshairService::class.java))
        }
    }

    override val notificationId = 132
    override fun onRunningChanged(running: Boolean) { isRunning = running }

    override fun buildLayoutParams() = overlayParams(touchable = false).apply {
        gravity = Gravity.CENTER
        x = 0
        y = 0
    }

    @Composable
    override fun OverlayContent() {
        val settings = remember { GameSpacePrefs.settingsFor(this, GameDetectorService.currentGame.orEmpty()) }
        val accent = remember { Color(GameSpacePrefs.accent(this)) }
        CrosshairView(
            style = settings.crosshairStyle,
            sizeDp = settings.crosshairSizeDp,
            color = accent.copy(alpha = settings.crosshairOpacity.coerceIn(10, 100) / 100f)
        )
    }
}

/** Dipakai juga oleh pratinjau di layar pengaturan. */
@Composable
fun CrosshairView(style: Int, sizeDp: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(sizeDp.coerceIn(12, 96).dp)) {
        drawCrosshair(style, color)
    }
}

private fun DrawScope.drawCrosshair(style: Int, color: Color) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = size.minDimension / 2f
    val w = (size.minDimension / 14f).coerceAtLeast(2f)
    val line = Stroke(width = w, cap = StrokeCap.Round)
    // Garis tipis gelap di bawah warna utama agar tetap terbaca di latar terang.
    val shadow = Color.Black.copy(alpha = 0.45f)
    fun both(draw: (Color, Float) -> Unit) { draw(shadow, w + 2f); draw(color, w) }

    when (style % CrosshairService.STYLE_COUNT) {
        0 -> both { col, sw -> drawCircle(col, radius = sw * 0.9f, center = c) }                       // titik
        1 -> both { col, sw -> // plus utuh
            drawLine(col, Offset(c.x - r, c.y), Offset(c.x + r, c.y), sw, StrokeCap.Round)
            drawLine(col, Offset(c.x, c.y - r), Offset(c.x, c.y + r), sw, StrokeCap.Round)
        }
        2 -> both { col, sw -> // plus berongga
            val gap = r * 0.28f
            drawLine(col, Offset(c.x - r, c.y), Offset(c.x - gap, c.y), sw, StrokeCap.Round)
            drawLine(col, Offset(c.x + gap, c.y), Offset(c.x + r, c.y), sw, StrokeCap.Round)
            drawLine(col, Offset(c.x, c.y - r), Offset(c.x, c.y - gap), sw, StrokeCap.Round)
            drawLine(col, Offset(c.x, c.y + gap), Offset(c.x, c.y + r), sw, StrokeCap.Round)
        }
        3 -> both { col, sw -> drawCircle(col, radius = r * 0.78f, center = c, style = Stroke(sw)) }  // cincin
        4 -> both { col, sw -> // cincin + titik
            drawCircle(col, radius = r * 0.78f, center = c, style = Stroke(sw))
            drawCircle(col, radius = sw * 0.9f, center = c)
        }
        5 -> both { col, sw -> // bentuk T
            drawLine(col, Offset(c.x - r, c.y), Offset(c.x + r, c.y), sw, StrokeCap.Round)
            drawLine(col, Offset(c.x, c.y), Offset(c.x, c.y + r), sw, StrokeCap.Round)
        }
        6 -> both { col, sw -> // X
            val d = r * 0.72f
            drawLine(col, Offset(c.x - d, c.y - d), Offset(c.x + d, c.y + d), sw, StrokeCap.Round)
            drawLine(col, Offset(c.x - d, c.y + d), Offset(c.x + d, c.y - d), sw, StrokeCap.Round)
        }
        else -> both { col, sw -> // sudut kotak
            val d = r * 0.8f
            val l = r * 0.38f
            for (sx in listOf(-1f, 1f)) for (sy in listOf(-1f, 1f)) {
                val corner = Offset(c.x + sx * d, c.y + sy * d)
                drawLine(col, corner, Offset(corner.x - sx * l, corner.y), sw, StrokeCap.Round)
                drawLine(col, corner, Offset(corner.x, corner.y - sy * l), sw, StrokeCap.Round)
            }
        }
    }
}
