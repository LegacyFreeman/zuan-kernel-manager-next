package com.zuan.kernelmanager.next.ui.components.battery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Bintang latar. x/y dinormalisasi (0..1); size dalam dp. */
class SpaceStar(
    val x: Float,
    val y: Float,
    val size: Float,
    val twinkleSpeed: Float,
    val phase: Float,
    val depth: Float
)

/** Partikel energi yang mengalir MASUK menuju astronot saat charging (menggantikan "hujan" lama). */
class EnergyMote(
    var angle: Float,
    var radius: Float,
    val speed: Float,
    val size: Float,
    val spin: Float
)

/** Bintang jatuh langka, hanya muncul saat idle. */
class Meteor(val x: Float, val y: Float, val len: Float) {
    var progress: Float = 0f
}

/**
 * Mesin animasi Orbit Power.
 *
 * Beda dengan versi lama:
 *  - Semua gerak berbasis `dt` (detik nyata antar-frame), bukan "+= konstanta per frame" ditambah
 *    delay(24) yang bikin ritme patah-patah.
 *  - Astronot digerakkan lintasan sin/cos yang lalu di-low-pass (inersia), jadi melayang mulus.
 *  - Charging = partikel energi spiral MASUK ke astronot + gelombang denyut + cincin yang
 *    "mengalir" — bukan garis-garis jatuh vertikal.
 */
class BatteryWeatherEngine {

    /** Dibaca di Canvas supaya tiap frame ter-redraw tanpa recomposition. */
    var tick by mutableIntStateOf(0)
        private set

    var time = 0f; private set
    /** Fase satelit di cincin (radian). */
    var orbitPhase = 0f; private set
    /** Offset astronot: relatif lebar / tinggi kartu, rotasi dalam derajat. */
    var astroX = 0f; private set
    var astroY = 0f; private set
    var astroRot = 0f; private set
    /** 0..1, naik/turun halus saat colokan charger dipasang/dicabut. */
    var chargeFade = 0f; private set
    var meteor: Meteor? = null; private set

    val stars: List<SpaceStar> = List(46) { createStar() }
    val motes: List<EnergyMote> = List(24) { createMote(initial = true) }

    private var meteorCountdown = 3f

    fun update(dt: Float, charging: Boolean, intensity: Float) {
        time += dt

        // Fade charging (≈ 0.4 detik untuk menyala/mati)
        val kFade = 1f - exp(-dt * 2.5f)
        chargeFade += ((if (charging) 1f else 0f) - chargeFade) * kFade

        val power = intensity.coerceIn(0f, 1f)

        // Satelit di cincin: pelan, sedikit lebih cepat saat charging
        orbitPhase += dt * (0.30f + 0.55f * chargeFade)

        // Astronot: lintasan target (jumlah dua gelombang) lalu di-smooth supaya punya inersia
        val tx = sin(time * 0.55f) * 0.030f + sin(time * 0.23f + 1.3f) * 0.016f
        val ty = sin(time * 0.80f) * 0.012f + sin(time * 0.37f + 0.6f) * 0.007f
        val tr = sin(time * 0.60f) * 6f + cos(time * 0.31f) * 3f
        val kMove = 1f - exp(-dt * 5f)
        astroX += (tx - astroX) * kMove
        astroY += (ty - astroY) * kMove
        astroRot += (tr - astroRot) * kMove

        // Partikel energi: spiral masuk. Cepat di luar, melambat mendekati inti.
        if (chargeFade > 0.01f) {
            val flow = 0.30f + 0.85f * power
            for (m in motes) {
                m.radius -= dt * m.speed * flow * (0.35f + m.radius)
                m.angle += dt * m.spin * (1.1f + (1f - m.radius) * 2.2f)
                if (m.radius < 0.07f) respawn(m)
            }
        }

        // Meteor langka, hanya saat idle (tidak ada efek jatuh saat charging)
        val current = meteor
        if (current != null) {
            current.progress += dt / 0.9f
            if (current.progress >= 1f) meteor = null
        } else {
            meteorCountdown -= dt
            if (meteorCountdown <= 0f && chargeFade < 0.2f) {
                meteor = Meteor(
                    x = 0.45f + Random.nextFloat() * 0.5f,
                    y = 0.02f + Random.nextFloat() * 0.22f,
                    len = 26f + Random.nextFloat() * 20f
                )
                meteorCountdown = 5f + Random.nextFloat() * 6f
            }
        }

        tick++
    }

    private fun respawn(m: EnergyMote) {
        m.radius = 1f
        m.angle = Random.nextFloat() * TWO_PI
    }

    private fun createStar() = SpaceStar(
        x = Random.nextFloat(),
        y = Random.nextFloat() * 0.8f,
        size = 0.35f + Random.nextFloat() * 0.85f,
        twinkleSpeed = 0.8f + Random.nextFloat() * 2.2f,
        phase = Random.nextFloat() * TWO_PI,
        depth = 0.3f + Random.nextFloat() * 0.7f
    )

    private fun createMote(initial: Boolean) = EnergyMote(
        angle = Random.nextFloat() * TWO_PI,
        // Frame statis (animasi dijeda) tetap menampilkan partikel tersebar, bukan menumpuk di tepi
        radius = if (initial) 0.15f + Random.nextFloat() * 0.85f else 1f,
        speed = 0.35f + Random.nextFloat() * 0.35f,
        size = 1.0f + Random.nextFloat() * 1.2f,
        spin = (if (Random.nextBoolean()) 1f else -1f) * (0.7f + Random.nextFloat() * 0.8f)
    )

    private companion object {
        const val TWO_PI = 6.2831855f
    }
}
