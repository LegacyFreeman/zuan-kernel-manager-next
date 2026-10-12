package com.zuan.kernelmanager.next.ui.components.battery

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform

object BatteryArtAssets {

    /**
     * Astronot melayang.
     *
     * CATATAN: rotate()/scale() di DrawTransform secara default berpivot di TENGAH canvas, bukan di
     * titik (x, y) astronot. Itu yang bikin gerakan lama terasa "ngayun aneh/kaku". Sekarang pivot
     * dikunci di Offset.Zero (titik tubuh astronot setelah translate).
     *
     * @param thrust 0..1 — kekuatan nyala jetpack (naik saat charging)
     * @param accent warna visor/jetpack saat aktif
     */
    fun DrawScope.drawAstronaut(
        x: Float,
        y: Float,
        rotation: Float,
        scale: Float = 1.0f,
        thrust: Float = 0f,
        accent: Color = Color(0xFF22D3EE)
    ) {
        withTransform({
            translate(x, y)
            rotate(rotation, Offset.Zero)
            scale(scale, scale, Offset.Zero)
        }) {
            val suitColor = Color.White
            val shade = Color(0xFFD4D4D8)
            val backpackColor = Color(0xFFE4E4E7)

            // Nyala jetpack (di belakang badan)
            if (thrust > 0.02f) {
                val len = 8f + 18f * thrust
                val flame = Path().apply {
                    moveTo(-22f, 25f)
                    lineTo(-13f, 25f)
                    lineTo(-17.5f, 25f + len)
                    close()
                }
                drawPath(
                    path = flame,
                    brush = Brush.verticalGradient(
                        colors = listOf(accent.copy(alpha = 0.95f * thrust), Color.Transparent),
                        startY = 25f,
                        endY = 25f + len
                    )
                )
                drawCircle(Color.White.copy(alpha = 0.75f * thrust), radius = 2.4f, center = Offset(-17.5f, 26.5f))
            }

            // Backpack
            drawRoundRect(
                color = backpackColor,
                topLeft = Offset(-25f, -15f),
                size = Size(15f, 40f),
                cornerRadius = CornerRadius(5f)
            )
            // Lampu kecil di backpack (menyala saat charging)
            drawCircle(accent.copy(alpha = 0.35f + 0.65f * thrust), radius = 2f, center = Offset(-17.5f, -8f))

            // Badan
            drawRoundRect(
                color = suitColor,
                topLeft = Offset(-10f, -10f),
                size = Size(24f, 35f),
                cornerRadius = CornerRadius(8f)
            )
            // Bayangan lembut di sisi kanan badan
            drawRoundRect(
                color = shade.copy(alpha = 0.55f),
                topLeft = Offset(7f, -8f),
                size = Size(7f, 31f),
                cornerRadius = CornerRadius(4f)
            )

            // Kaki kiri & kanan
            drawRoundRect(suitColor, topLeft = Offset(-8f, 20f), size = Size(8f, 15f), cornerRadius = CornerRadius(4f))
            drawRoundRect(suitColor, topLeft = Offset(4f, 20f), size = Size(8f, 15f), cornerRadius = CornerRadius(4f))

            // Tangan
            drawRoundRect(suitColor, topLeft = Offset(10f, -5f), size = Size(15f, 8f), cornerRadius = CornerRadius(4f))
            drawRoundRect(suitColor, topLeft = Offset(-15f, 5f), size = Size(8f, 15f), cornerRadius = CornerRadius(4f))

            // Helm
            drawCircle(color = suitColor, radius = 16f, center = Offset(2f, -20f))
            // Kaca helm (visor) dengan gradien
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.95f), Color(0xFF0E7490)),
                    center = Offset(4f, -24f),
                    radius = 12f
                ),
                radius = 10f,
                center = Offset(6f, -22f)
            )
            // Pantulan visor
            drawCircle(color = Color.White.copy(alpha = 0.6f), radius = 3f, center = Offset(10f, -25f))
        }
    }

    fun DrawScope.drawSatellite(x: Float, y: Float, scale: Float) {
        withTransform({
            translate(x, y)
            scale(scale, scale, Offset.Zero)
            rotate(15f, Offset.Zero)
        }) {
            val panelColor = Color(0xFF38BDF8)
            val bodyColor = Color(0xFFD4D4D8)

            // Panel surya kiri & kanan
            drawRect(panelColor, topLeft = Offset(-40f, -10f), size = Size(30f, 20f))
            drawRect(panelColor, topLeft = Offset(10f, -10f), size = Size(30f, 20f))

            // Garis panel surya
            drawLine(Color.White.copy(alpha = 0.5f), start = Offset(-25f, -10f), end = Offset(-25f, 10f), strokeWidth = 1.5f)
            drawLine(Color.White.copy(alpha = 0.5f), start = Offset(25f, -10f), end = Offset(25f, 10f), strokeWidth = 1.5f)

            // Body utama
            drawRoundRect(bodyColor, topLeft = Offset(-10f, -12f), size = Size(20f, 24f), cornerRadius = CornerRadius(4f))

            // Antena
            drawLine(Color.White, start = Offset(0f, -12f), end = Offset(0f, -25f), strokeWidth = 2.5f)
            drawCircle(Color(0xFFEF4444), radius = 2.6f, center = Offset(0f, -25f))
        }
    }
}
