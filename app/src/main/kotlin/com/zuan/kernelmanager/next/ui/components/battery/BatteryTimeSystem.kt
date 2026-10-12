package com.zuan.kernelmanager.next.ui.components.battery

import androidx.compose.ui.graphics.Color
import java.util.Calendar

enum class TimePhase {
    DAWN,   // Subuh: Nebula Ungu
    DAY,    // Siang: Biru Kosmos Terang
    DUSK,   // Sore: Jingga Matahari Merah
    NIGHT   // Malam: Void Gelap gulita
}

data class SpaceTheme(
    val spaceTop: Color,
    val spaceBottom: Color,
    val planetColor: Color,
    val planetGlow: Color,
    val starAlpha: Float
)

object BatteryTimeSystem {

    fun getCurrentPhase(): TimePhase {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..6 -> TimePhase.DAWN
            in 7..16 -> TimePhase.DAY
            in 17..18 -> TimePhase.DUSK
            else -> TimePhase.NIGHT
        }
    }

    fun getTheme(phase: TimePhase): SpaceTheme {
        return when (phase) {
            TimePhase.DAWN -> SpaceTheme(
                spaceTop = Color(0xFF2E1065),    // Ungu gelap
                spaceBottom = Color(0xFF9333EA), // Ungu neon
                planetColor = Color(0xFF4C1D95), // Planet ungu tua
                planetGlow = Color(0xFFD8B4FE).copy(alpha = 0.5f),
                starAlpha = 0.6f
            )
            TimePhase.DAY -> SpaceTheme(
                spaceTop = Color(0xFF0F172A),    // Biru dongker
                spaceBottom = Color(0xFF0284C7), // Biru terang kosmos
                planetColor = Color(0xFF0369A1), // Planet biru laut
                planetGlow = Color(0xFF7DD3FC).copy(alpha = 0.5f),
                starAlpha = 0.3f // Bintang samar di "siang" kosmos
            )
            TimePhase.DUSK -> SpaceTheme(
                spaceTop = Color(0xFF450A0A),    // Merah gelap
                spaceBottom = Color(0xFFEA580C), // Oranye Mars
                planetColor = Color(0xFF7C2D12), // Planet merah kecoklatan
                planetGlow = Color(0xFFFDBA74).copy(alpha = 0.5f),
                starAlpha = 0.7f
            )
            TimePhase.NIGHT -> SpaceTheme(
                spaceTop = Color(0xFF000000),    // Void hitam
                spaceBottom = Color(0xFF1E1E24), // Abu-abu gelap
                planetColor = Color(0xFF27272A), // Planet bulan abu-abu
                planetGlow = Color(0xFFE4E4E7).copy(alpha = 0.3f),
                starAlpha = 1.0f // Bintang paling terang
            )
        }
    }
}
