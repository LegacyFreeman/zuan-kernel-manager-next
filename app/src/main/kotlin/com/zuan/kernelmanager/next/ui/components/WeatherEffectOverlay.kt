/*
 * Copyright (c) 2025 ZKM
 * Realistic Multi-layered Physics Weather Effects
 */
package com.zuan.kernelmanager.next.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.zuan.kernelmanager.next.feature.settings.WeatherEffect
import kotlinx.coroutines.isActive
import kotlin.math.*
import kotlin.random.Random

@Composable
fun WeatherEffectOverlay(
    effect: WeatherEffect,
    intensity: Float,
    modifier: Modifier = Modifier,
) {
    if (effect == WeatherEffect.NONE) return

    val densityIntensity = intensity.coerceIn(0.1f, 1f)
    var animationClock by remember { mutableLongStateOf(0L) }

    LaunchedEffect(effect) {
        var lastNanos = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (lastNanos != 0L) {
                    val delta = (now - lastNanos).coerceAtMost(50_000_000L)
                    animationClock += delta
                }
                lastNanos = now
            }
        }
    }

    val state =
        remember(effect, densityIntensity) {
            WeatherSimulationState(effect, densityIntensity)
        }

    Canvas(modifier = modifier.fillMaxSize()) {
        state.updateAndRender(
            drawScope = this,
            nanos = animationClock,
            intensity = densityIntensity,
        )
    }
}

private class WeatherSimulationState(
    val effect: WeatherEffect,
    val intensity: Float,
) {
    private var initialized = false
    private var lastTimeNanos = 0L

    private data class RainDrop(
        var x: Float,
        var y: Float,
        val z: Float,
        val length: Float,
        val speed: Float,
        val slant: Float,
    )

    private val rainDrops = mutableListOf<RainDrop>()

    private data class SnowFlake(
        var x: Float,
        var y: Float,
        val z: Float,
        val radius: Float,
        val speed: Float,
        val wobbleSpeed: Float,
        val wobbleRadius: Float,
        var wobblePhase: Float,
    )

    private val snowFlakes = mutableListOf<SnowFlake>()

    private data class CloudPuff(
        var x: Float,
        var y: Float,
        val radius: Float,
        val speedX: Float,
        val alpha: Float,
    )

    private val clouds = mutableListOf<CloudPuff>()

    private data class DustMote(
        var x: Float,
        var y: Float,
        val radius: Float,
        val speedX: Float,
        val speedY: Float,
        var phase: Float,
    )

    private val dustMotes = mutableListOf<DustMote>()

    private fun initParticles(
        w: Float,
        h: Float,
    ) {
        val rand = Random(42)

        when (effect) {
            WeatherEffect.RAIN -> {
                val count = (120 * intensity).toInt().coerceAtLeast(35)
                rainDrops.clear()
                repeat(count) {
                    val z = rand.nextFloat() * 0.8f + 0.2f
                    rainDrops.add(
                        RainDrop(
                            x = rand.nextFloat() * (w + 200f) - 100f,
                            y = rand.nextFloat() * h,
                            z = z,
                            length = (28f + rand.nextFloat() * 32f) * z,
                            speed = (1200f + rand.nextFloat() * 800f) * z,
                            slant = 0.25f,
                        ),
                    )
                }
            }
            WeatherEffect.SNOW -> {
                val count = (90 * intensity).toInt().coerceAtLeast(25)
                snowFlakes.clear()
                repeat(count) {
                    val z = rand.nextFloat() * 0.8f + 0.2f
                    snowFlakes.add(
                        SnowFlake(
                            x = rand.nextFloat() * w,
                            y = rand.nextFloat() * h,
                            z = z,
                            radius = (2.5f + rand.nextFloat() * 3.5f) * z,
                            speed = (90f + rand.nextFloat() * 110f) * z,
                            wobbleSpeed = 1.8f + rand.nextFloat() * 2.2f,
                            wobbleRadius = 25f + rand.nextFloat() * 40f,
                            wobblePhase = rand.nextFloat() * PI.toFloat() * 2f,
                        ),
                    )
                }
            }
            WeatherEffect.ICE -> {
                val count = (140 * intensity).toInt().coerceAtLeast(40)
                snowFlakes.clear()
                repeat(count) {
                    val z = rand.nextFloat() * 0.85f + 0.15f
                    snowFlakes.add(
                        SnowFlake(
                            x = rand.nextFloat() * (w + 150f) - 50f,
                            y = rand.nextFloat() * h,
                            z = z,
                            radius = (2f + rand.nextFloat() * 4f) * z,
                            speed = (260f + rand.nextFloat() * 300f) * z,
                            wobbleSpeed = 3f + rand.nextFloat() * 3f,
                            wobbleRadius = 15f + rand.nextFloat() * 25f,
                            wobblePhase = rand.nextFloat() * PI.toFloat() * 2f,
                        ),
                    )
                }
            }
            WeatherEffect.FOG -> {
                val count = (12 * intensity).toInt().coerceAtLeast(6)
                clouds.clear()
                repeat(count) {
                    clouds.add(
                        CloudPuff(
                            x = rand.nextFloat() * w,
                            y = rand.nextFloat() * h,
                            radius = (w * 0.45f) + rand.nextFloat() * (w * 0.35f),
                            speedX = (15f + rand.nextFloat() * 25f) * (if (rand.nextBoolean()) 1f else -1f),
                            alpha = 0.12f + rand.nextFloat() * 0.14f,
                        ),
                    )
                }
            }
            WeatherEffect.CLOUDS -> {
                val count = (8 * intensity).toInt().coerceAtLeast(4)
                clouds.clear()
                repeat(count) {
                    clouds.add(
                        CloudPuff(
                            x = rand.nextFloat() * (w * 1.5f),
                            y = rand.nextFloat() * (h * 0.6f),
                            radius = (w * 0.35f) + rand.nextFloat() * (w * 0.45f),
                            speedX = 20f + rand.nextFloat() * 35f,
                            alpha = 0.18f + rand.nextFloat() * 0.2f,
                        ),
                    )
                }
            }
            WeatherEffect.SUN_RAYS -> {
                val count = 25
                dustMotes.clear()
                repeat(count) {
                    dustMotes.add(
                        DustMote(
                            x = rand.nextFloat() * w,
                            y = rand.nextFloat() * h,
                            radius = 1.5f + rand.nextFloat() * 2.5f,
                            speedX = (rand.nextFloat() - 0.4f) * 30f,
                            speedY = (rand.nextFloat() - 0.7f) * 30f,
                            phase = rand.nextFloat() * PI.toFloat() * 2f,
                        ),
                    )
                }
            }
            WeatherEffect.NONE -> {}
        }
        initialized = true
    }

    fun updateAndRender(
        drawScope: DrawScope,
        nanos: Long,
        intensity: Float,
    ) {
        val w = drawScope.size.width
        val h = drawScope.size.height
        if (w <= 0 || h <= 0) return

        if (!initialized) {
            initParticles(w, h)
            lastTimeNanos = nanos
            return
        }

        val dt = if (lastTimeNanos == 0L) 0.016f else ((nanos - lastTimeNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastTimeNanos = nanos

        when (effect) {
            WeatherEffect.RAIN -> renderRain(drawScope, w, h, dt, intensity)
            WeatherEffect.SNOW -> renderSnow(drawScope, w, h, dt, intensity)
            WeatherEffect.ICE -> renderIce(drawScope, w, h, dt, intensity)
            WeatherEffect.FOG -> renderFog(drawScope, w, h, dt, intensity)
            WeatherEffect.CLOUDS -> renderClouds(drawScope, w, h, dt, intensity)
            WeatherEffect.SUN_RAYS -> renderSunRays(drawScope, w, h, dt, intensity, nanos)
            WeatherEffect.NONE -> {}
        }
    }

    private fun renderRain(
        drawScope: DrawScope,
        w: Float,
        h: Float,
        dt: Float,
        intensity: Float,
    ) {
        drawScope.drawRect(
            color = Color(0xFF0F1A2A).copy(alpha = 0.08f * intensity),
        )

        for (drop in rainDrops) {
            drop.y += drop.speed * dt
            drop.x += (drop.speed * drop.slant) * dt

            if (drop.y > h + drop.length) {
                drop.y = -drop.length
                drop.x = Random.nextFloat() * (w + 200f) - 100f
            }

            val alpha = (0.35f + 0.45f * drop.z) * intensity
            val endX = drop.x - drop.length * drop.slant
            val endY = drop.y - drop.length

            drawScope.drawLine(
                brush =
                    Brush.linearGradient(
                        colors =
                            listOf(
                                Color.White.copy(alpha = 0.05f),
                                Color(0xFFD6E4FF).copy(alpha = alpha),
                            ),
                        start = Offset(endX, endY),
                        end = Offset(drop.x, drop.y),
                    ),
                start = Offset(endX, endY),
                end = Offset(drop.x, drop.y),
                strokeWidth = (1.2f + 1.2f * drop.z),
                cap = StrokeCap.Round,
            )
        }
    }

    private fun renderSnow(
        drawScope: DrawScope,
        w: Float,
        h: Float,
        dt: Float,
        intensity: Float,
    ) {
        for (flake in snowFlakes) {
            flake.wobblePhase += flake.wobbleSpeed * dt
            flake.y += flake.speed * dt
            val offsetX = sin(flake.wobblePhase) * flake.wobbleRadius * flake.z

            if (flake.y > h + flake.radius) {
                flake.y = -flake.radius
                flake.x = Random.nextFloat() * w
            }

            val currentX = (flake.x + offsetX).mod(w)
            val alpha = (0.4f + 0.5f * flake.z) * intensity

            drawScope.drawCircle(
                color = Color.White.copy(alpha = alpha * 0.35f),
                radius = flake.radius * 2f,
                center = Offset(currentX, flake.y),
            )
            drawScope.drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = flake.radius,
                center = Offset(currentX, flake.y),
            )
        }
    }

    private fun renderIce(
        drawScope: DrawScope,
        w: Float,
        h: Float,
        dt: Float,
        intensity: Float,
    ) {
        val vignetteAlpha = (0.45f * intensity).coerceIn(0.1f, 0.7f)
        val frostBrush =
            Brush.radialGradient(
                colors =
                    listOf(
                        Color.Transparent,
                        Color(0xFF88CCFF).copy(alpha = vignetteAlpha * 0.25f),
                        Color(0xFFE0F7FF).copy(alpha = vignetteAlpha),
                    ),
                center = Offset(w / 2f, h / 2f),
                radius = (max(w, h) * 0.72f),
            )
        drawScope.drawRect(brush = frostBrush)

        drawScope.drawCircle(
            brush =
                Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = vignetteAlpha * 0.5f), Color.Transparent),
                    center = Offset(0f, 0f),
                    radius = w * 0.4f,
                ),
            center = Offset(0f, 0f),
            radius = w * 0.4f,
        )
        drawScope.drawCircle(
            brush =
                Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = vignetteAlpha * 0.5f), Color.Transparent),
                    center = Offset(w, 0f),
                    radius = w * 0.4f,
                ),
            center = Offset(w, 0f),
            radius = w * 0.4f,
        )

        for (flake in snowFlakes) {
            flake.y += flake.speed * dt
            flake.x -= (flake.speed * 0.6f) * dt

            if (flake.y > h + flake.radius || flake.x < -20f) {
                flake.y = -flake.radius
                flake.x = Random.nextFloat() * (w + 100f)
            }

            val alpha = (0.5f + 0.4f * flake.z) * intensity
            drawScope.drawCircle(
                color = Color(0xFFF0F8FF).copy(alpha = alpha),
                radius = flake.radius,
                center = Offset(flake.x, flake.y),
            )
        }
    }

    private fun renderFog(
        drawScope: DrawScope,
        w: Float,
        h: Float,
        dt: Float,
        intensity: Float,
    ) {
        for (cloud in clouds) {
            cloud.x += cloud.speedX * dt
            if (cloud.speedX > 0 && cloud.x - cloud.radius > w) {
                cloud.x = -cloud.radius
            } else if (cloud.speedX < 0 && cloud.x + cloud.radius < 0) {
                cloud.x = w + cloud.radius
            }

            val currentAlpha = (cloud.alpha * intensity).coerceIn(0.04f, 0.4f)
            drawScope.drawCircle(
                brush =
                    Brush.radialGradient(
                        colors =
                            listOf(
                                Color(0xFFD6E4ED).copy(alpha = currentAlpha),
                                Color.Transparent,
                            ),
                        center = Offset(cloud.x, cloud.y),
                        radius = cloud.radius,
                    ),
                radius = cloud.radius,
                center = Offset(cloud.x, cloud.y),
            )
        }
    }

    private fun renderClouds(
        drawScope: DrawScope,
        w: Float,
        h: Float,
        dt: Float,
        intensity: Float,
    ) {
        for (cloud in clouds) {
            cloud.x += cloud.speedX * dt
            if (cloud.speedX > 0 && cloud.x - cloud.radius > w) {
                cloud.x = -cloud.radius
            }

            val currentAlpha = (cloud.alpha * intensity).coerceIn(0.05f, 0.5f)
            drawScope.drawOval(
                brush =
                    Brush.radialGradient(
                        colors =
                            listOf(
                                Color.White.copy(alpha = currentAlpha),
                                Color(0xFFE2E8F0).copy(alpha = currentAlpha * 0.4f),
                                Color.Transparent,
                            ),
                        center = Offset(cloud.x, cloud.y),
                        radius = cloud.radius,
                    ),
                topLeft = Offset(cloud.x - cloud.radius * 1.3f, cloud.y - cloud.radius * 0.7f),
                size = Size(cloud.radius * 2.6f, cloud.radius * 1.4f),
            )
        }
    }

    private fun renderSunRays(
        drawScope: DrawScope,
        w: Float,
        h: Float,
        dt: Float,
        intensity: Float,
        nanos: Long,
    ) {
        val sunCenter = Offset(w * 0.85f, -h * 0.05f)
        val timeSec = nanos / 1_000_000_000f

        drawScope.drawCircle(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            Color(0xFFFFF3D0).copy(alpha = 0.55f * intensity),
                            Color(0xFFFFD180).copy(alpha = 0.25f * intensity),
                            Color.Transparent,
                        ),
                    center = sunCenter,
                    radius = w * 0.8f,
                ),
            center = sunCenter,
            radius = w * 0.8f,
        )

        val numRays = 7
        for (i in 0 until numRays) {
            val baseAngle = (i * (PI / (numRays + 1))).toFloat() + 0.35f
            val pulse = sin(timeSec * 0.8f + i) * 0.04f
            val angle = baseAngle + pulse
            val rayLength = max(w, h) * 1.5f

            val endX = sunCenter.x - cos(angle) * rayLength
            val endY = sunCenter.y + sin(angle) * rayLength

            val rayAlpha = ((0.12f + 0.08f * sin(timeSec * 1.2f + i * 2)) * intensity).coerceIn(0.03f, 0.3f)

            drawScope.drawLine(
                brush =
                    Brush.linearGradient(
                        colors =
                            listOf(
                                Color(0xFFFFE8A3).copy(alpha = rayAlpha),
                                Color.Transparent,
                            ),
                        start = sunCenter,
                        end = Offset(endX, endY),
                    ),
                start = sunCenter,
                end = Offset(endX, endY),
                strokeWidth = (w * 0.16f),
                cap = StrokeCap.Round,
            )
        }

        for (mote in dustMotes) {
            mote.phase += dt * 1.5f
            mote.x += mote.speedX * dt
            mote.y += mote.speedY * dt

            if (mote.y < 0) mote.y = h
            if (mote.y > h) mote.y = 0f
            if (mote.x < 0) mote.x = w
            if (mote.x > w) mote.x = 0f

            val alpha = ((0.3f + 0.4f * sin(mote.phase)) * intensity).coerceIn(0.05f, 0.8f)
            drawScope.drawCircle(
                color = Color(0xFFFFF7C2).copy(alpha = alpha),
                radius = mote.radius,
                center = Offset(mote.x, mote.y),
            )
        }
    }
}

// ==================== HELPER COMPOSABLES ====================
@Composable
fun SaturationModifier(saturation: Float): Modifier =
    if (saturation != 1f) {
        Modifier.graphicsLayer {
            val matrix =
                ColorMatrix().apply {
                    setToSaturation(saturation)
                }
            this.colorFilter = ColorMatrixColorFilter(matrix)
        }
    } else {
        Modifier
    }

@Composable
fun BlurWithStrength(
    blurStrength: Float,
    content: @Composable () -> Unit,
) {
    if (blurStrength > 0f) {
        Box(modifier = Modifier.blur(blurStrength.dp)) {
            content()
        }
    } else {
        content()
    }
}
