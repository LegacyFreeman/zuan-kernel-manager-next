/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.dashboard.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.zuan.kernelmanager.next.feature.settings.AvatarDecorationStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Bingkai / dekorasi avatar profil ala Discord Nitro.
 * Semua gaya digambar prosedural dengan Canvas (tanpa aset gambar).
 */
@Composable
fun AvatarWithDecoration(
    style: AvatarDecorationStyle,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    if (style == AvatarDecorationStyle.NONE) {
        Box(modifier = modifier, contentAlignment = Alignment.Center, content = content)
        return
    }

    val clock = rememberDecoClock(animated = !style.isStatic)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawDecoration(style, clock.value, DecoLayer.BACK)
        }
        // Leave a larger transparent bleed between the profile media and its ornament.
        // This makes large, illustrative frames read as artwork instead of a thin ring.
        Box(
            modifier = Modifier
                .fillMaxSize((style.contentScale * 0.82f).coerceIn(0.52f, 0.68f))
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
            content = content
        )
        if (style.is3D) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawDecoration(style, clock.value, DecoLayer.FRONT)
            }
        }
    }
}

internal enum class DecoLayer { BACK, FRONT }

@Composable
internal fun rememberDecoClock(animated: Boolean = true): State<Float> {
    val time = remember { mutableStateOf(0f) }
    LaunchedEffect(animated) {
        if (!animated) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { time.value = (it - start) / 1_000_000_000f }
        }
    }
    return time
}

internal fun decoPulse(t: Float): Float {
    val p = (t / 1.6f) % 2f
    val tri = if (p < 1f) p else 2f - p
    return tri * tri * (3f - 2f * tri)
}

private fun DrawScope.drawDecoration(style: AvatarDecorationStyle, t: Float, layer: DecoLayer) {
    val m = size.minDimension
    val radius = m / 2f - m * 0.09f
    val strokeW = m * 0.055f
    val c = center

    if (style.is3D) {
        when (style) {
            AvatarDecorationStyle.GYRO_CORE -> drawGyroCore(c, radius, strokeW, t, layer)
            AvatarDecorationStyle.SATURN_HALO -> drawSaturnHalo(c, radius, strokeW, t, layer)
            AvatarDecorationStyle.QUANTUM_ATOM -> drawQuantumAtom(c, radius, strokeW, t, layer)
            AvatarDecorationStyle.HYPER_CUBE -> drawHyperCube(c, radius, strokeW, t, layer)
            AvatarDecorationStyle.HELIX_RING -> drawHelixRing(c, radius, strokeW, t, layer)
            AvatarDecorationStyle.PRISM_SHARDS -> drawPrismShards(c, radius, strokeW, t, layer)
            AvatarDecorationStyle.GLASS_GLOBE -> drawGlassGlobe(c, radius, strokeW, t, layer)
            AvatarDecorationStyle.CHROME_TORUS -> drawChromeTorus(c, radius, strokeW, t, layer)
            AvatarDecorationStyle.MINI_SOLAR -> drawMiniSolar(c, radius, strokeW, t, layer)
            AvatarDecorationStyle.HEX_HOLO -> drawHexHolo(c, radius, strokeW, t, layer)
            // --- 3D Epic Premium / Mythic (Kimi K3) ---
            AvatarDecorationStyle.PLASMA_ORB -> drawPlasmaOrb(c, radius, strokeW, t, layer)
            AvatarDecorationStyle.HYPER_PYRAMID -> drawHyperPyramid(c, radius, strokeW, t, layer)
            AvatarDecorationStyle.PORTAL_GATE -> drawPortalGate(c, radius, strokeW, t, layer)
            AvatarDecorationStyle.GRAVITY_WELL -> drawGravityWell(c, radius, strokeW, t, layer)
            AvatarDecorationStyle.WARP_TUNNEL -> drawWarpTunnel(c, radius, strokeW, t, layer)
            AvatarDecorationStyle.ORBITAL_BLADES -> drawOrbitalBlades(c, radius, strokeW, t, layer)
            AvatarDecorationStyle.METEOR_BELT -> drawMeteorBelt(c, radius, strokeW, t, layer)
            AvatarDecorationStyle.NEBULA_SPHERE -> drawNebulaSphere(c, radius, strokeW, t, layer)
            AvatarDecorationStyle.DRONE_SWARM -> drawDroneSwarm(c, radius, strokeW, t, layer)
            AvatarDecorationStyle.CRYSTAL_CROWN -> drawCrystalCrown(c, radius, strokeW, t, layer)
            // --- 3D TAMBAHAN BARU (7 Efek) ---
            AvatarDecorationStyle.CELESTIAL_ASTROLABE -> drawCelestialAstrolabe(c, radius, strokeW, t, layer)
            AvatarDecorationStyle.BLACK_HOLE_ACCRETION -> drawBlackHoleAccretion(c, radius, strokeW, t, layer)
            AvatarDecorationStyle.NEON_TESSERACT -> drawNeonTesseract(c, radius, strokeW, t, layer)
            AvatarDecorationStyle.INFINITY_OUROBOROS -> drawInfinityOuroboros(c, radius, strokeW, t, layer)
            AvatarDecorationStyle.SOLAR_CORONA -> drawSolarCorona(c, radius, strokeW, t, layer)
            AvatarDecorationStyle.VALKYRIE_AEGIS -> drawValkyrieAegis(c, radius, strokeW, t, layer)
            AvatarDecorationStyle.DNA_CYBER_SYNTH -> drawDnaCyberSynth(c, radius, strokeW, t, layer)
            // ZKM NEXT Avatar Studio — Ultimate 3D collection
            AvatarDecorationStyle.PRISMATIC_REACTOR -> drawPrismaticReactor(c, radius, strokeW, t, layer)
            AvatarDecorationStyle.CELESTIAL_ARMILLARY -> drawCelestialArmillary(c, radius, strokeW, t, layer)
            AvatarDecorationStyle.NEBULA_DRAGON -> drawNebulaDragon(c, radius, strokeW, t, layer)
            AvatarDecorationStyle.TITAN_RING -> drawTitanRing(c, radius, strokeW, t, layer)
            AvatarDecorationStyle.AETHER_CUBE -> drawAetherCube(c, radius, strokeW, t, layer)
            AvatarDecorationStyle.ORBITAL_CROWN -> drawOrbitalCrown(c, radius, strokeW, t, layer)
            AvatarDecorationStyle.CRYSTAL_HEART -> drawCrystalHeart(c, radius, strokeW, t, layer)
            AvatarDecorationStyle.ECLIPSE_ENGINE -> drawEclipseEngine(c, radius, strokeW, t, layer)
            AvatarDecorationStyle.PHOENIX_NEBULA -> drawPhoenixNebula(c, radius, strokeW, t, layer)
            AvatarDecorationStyle.QUANTUM_TRIDENT -> drawQuantumTrident(c, radius, strokeW, t, layer)
            else -> {}
        }
        return
    }

    if (style.isStatic) {
        if (layer != DecoLayer.BACK) return
        when (style) {
            AvatarDecorationStyle.OBSIDIAN_EDGE -> drawObsidianEdge(c, radius, strokeW)
            AvatarDecorationStyle.JADE_DYNASTY -> drawJadeDynasty(c, radius, strokeW)
            AvatarDecorationStyle.STEAMPUNK_GEAR -> drawSteampunkGear(c, radius, strokeW)
            AvatarDecorationStyle.PEARL_LACE -> drawPearlLace(c, radius, strokeW)
            AvatarDecorationStyle.CARBON_CORE -> drawCarbonCore(c, radius, strokeW)
            AvatarDecorationStyle.MARBLE_LUXE -> drawMarbleLuxe(c, radius, strokeW)
            AvatarDecorationStyle.TRIBAL_SPIRIT -> drawTribalSpirit(c, radius, strokeW)
            AvatarDecorationStyle.SILVER_WINGS -> drawSilverWings(c, radius, strokeW)
            AvatarDecorationStyle.ROYAL_CREST -> drawRoyalCrest(c, radius, strokeW)
            AvatarDecorationStyle.ONYX_CROWN -> drawOnyxCrown(c, radius, strokeW)
            // --- STATIS TAMBAHAN BARU (4 Efek) ---
            AvatarDecorationStyle.DIVINE_IMPERIAL_CROWN -> drawDivineImperialCrown(c, radius, strokeW)
            AvatarDecorationStyle.VOID_REAPER_SCYTHE -> drawVoidReaperScythe(c, radius, strokeW)
            AvatarDecorationStyle.SOLARIS_EMBLEM -> drawSolarisEmblem(c, radius, strokeW)
            AvatarDecorationStyle.CYBER_EXOSKELETON -> drawCyberExoskeleton(c, radius, strokeW)
            // ZKM NEXT Avatar Studio — Ultimate static collection
            AvatarDecorationStyle.AMETHYST_DIADEM -> drawAmethystDiadem(c, radius, strokeW)
            AvatarDecorationStyle.TITANIUM_WREATH -> drawTitaniumWreath(c, radius, strokeW)
            AvatarDecorationStyle.OBSIDIAN_DRAGON -> drawObsidianDragon(c, radius, strokeW)
            AvatarDecorationStyle.FROSTED_SAPPHIRE -> drawFrostedSapphire(c, radius, strokeW)
            AvatarDecorationStyle.CELESTIAL_RELIC -> drawCelestialRelic(c, radius, strokeW)
            AvatarDecorationStyle.CRIMSON_SIGIL -> drawCrimsonSigil(c, radius, strokeW)
            AvatarDecorationStyle.ECLIPSE_MEDALLION -> drawEclipseMedallion(c, radius, strokeW)
            AvatarDecorationStyle.ROSE_GOLD_FILIGREE -> drawRoseGoldFiligree(c, radius, strokeW)
            AvatarDecorationStyle.MYSTIC_RUNESTONE -> drawMysticRunestone(c, radius, strokeW)
            AvatarDecorationStyle.IMPERIAL_PHOENIX -> drawImperialPhoenix(c, radius, strokeW)
            else -> {}
        }
        return
    }

    if (layer != DecoLayer.BACK) return

    val rotation = (t * 60f) % 360f
    val slowRotation = 360f - (t * (360f / 14f)) % 360f
    val pulse = decoPulse(t)

    when (style) {
        AvatarDecorationStyle.NEON_PULSE -> drawNeonPulse(c, radius, strokeW, rotation, pulse)
        AvatarDecorationStyle.RGB_STORM -> drawRgbStorm(c, radius, strokeW, rotation, pulse)
        AvatarDecorationStyle.SAKURA_BLOOM -> drawSakura(c, radius, strokeW, rotation, pulse)
        AvatarDecorationStyle.INFERNO_RING -> drawInferno(c, radius, strokeW, rotation, pulse)
        AvatarDecorationStyle.FROST_CRYSTAL -> drawFrost(c, radius, strokeW, slowRotation, pulse)
        AvatarDecorationStyle.GALAXY_ORBIT -> drawGalaxy(c, radius, strokeW, rotation, slowRotation, pulse)
        AvatarDecorationStyle.GOLD_ROYALE -> drawGold(c, radius, strokeW, slowRotation, pulse)
        AvatarDecorationStyle.AURORA_VEIL -> drawAurora(c, radius, strokeW, t)
        AvatarDecorationStyle.CYBER_HUD -> drawCyberHud(c, radius, strokeW, t)
        AvatarDecorationStyle.ELECTRIC_STORM -> drawElectricStorm(c, radius, strokeW, t)
        AvatarDecorationStyle.OCEAN_WAVE -> drawOceanWave(c, radius, strokeW, t)
        AvatarDecorationStyle.LOVE_PULSE -> drawLovePulse(c, radius, strokeW, t)
        AvatarDecorationStyle.COMET_TRAIL -> drawCometTrail(c, radius, strokeW, t)
        AvatarDecorationStyle.RUNE_CIRCLE -> drawRuneCircle(c, radius, strokeW, t)
        AvatarDecorationStyle.PIXEL_RETRO -> drawPixelRetro(c, radius, strokeW, t)
        AvatarDecorationStyle.NEKO_EARS -> drawNekoEars(c, radius, strokeW, t)
        AvatarDecorationStyle.ANGEL_HALO -> drawAngelHalo(c, radius, strokeW, t)
        // --- 2D Epic Premium / Mythic (Kimi K3) ---
        AvatarDecorationStyle.VOID_VORTEX -> drawVoidVortex(c, radius, strokeW, t)
        AvatarDecorationStyle.PHOENIX_FLAME -> drawPhoenixFlame(c, radius, strokeW, t)
        AvatarDecorationStyle.TOXIC_SLIME -> drawToxicSlime(c, radius, strokeW, t)
        AvatarDecorationStyle.DIGITAL_MATRIX -> drawDigitalMatrix(c, radius, strokeW, t)
        AvatarDecorationStyle.TECH_CIRCUIT -> drawTechCircuit(c, radius, strokeW, t)
        AvatarDecorationStyle.BUTTERFLY_DREAM -> drawButterflyDream(c, radius, strokeW, t)
        AvatarDecorationStyle.LAVA_FLOW -> drawLavaFlow(c, radius, strokeW, t)
        AvatarDecorationStyle.HOLO_FOIL -> drawHoloFoil(c, radius, strokeW, t)
        AvatarDecorationStyle.BLOOD_MOON -> drawBloodMoon(c, radius, strokeW, t)
        AvatarDecorationStyle.SERPENT_COIL -> drawSerpentCoil(c, radius, strokeW, t)
        // --- 2D TAMBAHAN BARU (7 Efek) ---
        AvatarDecorationStyle.SUPERNOVA_BURST -> drawSupernovaBurst(c, radius, strokeW, t)
        AvatarDecorationStyle.CYBERPUNK_GLITCH -> drawCyberpunkGlitch(c, radius, strokeW, t)
        AvatarDecorationStyle.DRAGON_SOUL -> drawDragonSoul(c, radius, strokeW, t)
        AvatarDecorationStyle.ABYSSAL_KRAKEN -> drawAbyssalKraken(c, radius, strokeW, t)
        AvatarDecorationStyle.ETHEREAL_WINGS -> drawEtherealWings(c, radius, strokeW, t)
        AvatarDecorationStyle.QUANTUM_SINGULARITY -> drawQuantumSingularity(c, radius, strokeW, t)
        AvatarDecorationStyle.SAKURA_NIGHTFALL -> drawSakuraNightfall(c, radius, strokeW, t)
        // ZKM NEXT Avatar Studio — Ultimate 2D collection
        AvatarDecorationStyle.AETHER_WINGS -> drawAetherWings(c, radius, strokeW, t)
        AvatarDecorationStyle.THUNDER_CREST -> drawThunderCrest(c, radius, strokeW, t)
        AvatarDecorationStyle.FROSTBITE_SIGIL -> drawFrostbiteSigil(c, radius, strokeW, t)
        AvatarDecorationStyle.SOLAR_LANCE -> drawSolarLance(c, radius, strokeW, t)
        AvatarDecorationStyle.ROSE_VORTEX -> drawRoseVortex(c, radius, strokeW, t)
        AvatarDecorationStyle.ARCANE_TAROT -> drawArcaneTarot(c, radius, strokeW, t)
        AvatarDecorationStyle.CRIMSON_BLOOM -> drawCrimsonBloom(c, radius, strokeW, t)
        AvatarDecorationStyle.DIGITAL_PHANTOM -> drawDigitalPhantom(c, radius, strokeW, t)
        AvatarDecorationStyle.LUNAR_ECLIPSE -> drawLunarEclipse(c, radius, strokeW, t)
        AvatarDecorationStyle.STARDUST_CROWN -> drawStardustCrown(c, radius, strokeW, t)
        else -> {}
    }
}

private fun DrawScope.drawNeonPulse(
    c: Offset, r: Float, w: Float, rot: Float, pulse: Float
) {
    val cyan = Color(0xFF00E5FF)
    val magenta = Color(0xFFFF2D95)
    drawCircle(cyan.copy(alpha = 0.18f + 0.22f * pulse), r + w, c, style = Stroke(w * 2.2f))
    drawCircle(Color.White.copy(alpha = 0.10f), r, c, style = Stroke(w))
    rotate(rot, c) {
        drawArc(cyan, -40f, 170f, false, Offset(c.x - r, c.y - r), androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Stroke(w))
    }
    rotate(-rot * 1.4f, c) {
        drawArc(magenta, 140f, 170f, false, Offset(c.x - r, c.y - r), androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Stroke(w))
    }
    val a = Math.toRadians((rot - 40.0)).toFloat()
    drawCircle(cyan, w * (0.9f + 0.5f * pulse), Offset(c.x + r * cos(a), c.y + r * sin(a)))
    val b = Math.toRadians((-rot * 1.4f + 140.0)).toFloat()
    drawCircle(magenta, w * (0.9f + 0.5f * (1f - pulse)), Offset(c.x + r * cos(b), c.y + r * sin(b)))
}

private fun DrawScope.drawRgbStorm(
    c: Offset, r: Float, w: Float, rot: Float, pulse: Float
) {
    val rainbow = listOf(
        Color(0xFFFF004C), Color(0xFFFF8A00), Color(0xFFFFEE00),
        Color(0xFF00E676), Color(0xFF00B0FF), Color(0xFF7C4DFF), Color(0xFFFF004C)
    )
    rotate(rot, c) {
        drawCircle(
            Brush.sweepGradient(rainbow.map { it.copy(alpha = 0.25f) }, c),
            r + w * 0.8f, c, style = Stroke(w * (1.4f + 0.5f * pulse))
        )
        drawCircle(Brush.sweepGradient(rainbow, c), r, c, style = Stroke(w))
    }
    val a = Math.toRadians((rot * 2.0)).toFloat()
    drawCircle(Color.White.copy(alpha = 0.7f + 0.3f * pulse), w * 0.55f, Offset(c.x + r * cos(a), c.y + r * sin(a)))
}

private fun DrawScope.drawSakura(
    c: Offset, r: Float, w: Float, rot: Float, pulse: Float
) {
    val pink = Color(0xFFFF8FAB)
    val pinkDeep = Color(0xFFFF5C8A)
    drawCircle(pink.copy(alpha = 0.15f + 0.15f * pulse), r + w, c, style = Stroke(w * 2f))
    drawCircle(Brush.sweepGradient(listOf(pink, pinkDeep, pink), c), r, c, style = Stroke(w * 0.9f))
    for (i in 0 until 5) {
        val ang = Math.toRadians((rot + i * 72.0)).toFloat()
        val orbitR = r + w * 0.2f
        val p = Offset(c.x + orbitR * cos(ang), c.y + orbitR * sin(ang))
        rotate(rot * 2f + i * 72f, p) {
            val path = Path().apply {
                moveTo(p.x, p.y - w * 0.9f)
                quadraticBezierTo(p.x + w * 0.8f, p.y - w * 0.3f, p.x, p.y + w * 0.9f)
                quadraticBezierTo(p.x - w * 0.8f, p.y - w * 0.3f, p.x, p.y - w * 0.9f)
            }
            drawPath(path, if (i % 2 == 0) pink else pinkDeep, alpha = 0.9f)
        }
    }
}

private fun DrawScope.drawInferno(
    c: Offset, r: Float, w: Float, rot: Float, pulse: Float
) {
    val fire = listOf(Color(0xFFFFF176), Color(0xFFFFB300), Color(0xFFFF5722), Color(0xFFB71C1C), Color(0xFFFFF176))
    rotate(rot * 0.6f, c) {
        drawCircle(Brush.sweepGradient(fire.map { it.copy(alpha = 0.25f) }, c), r + w, c, style = Stroke(w * (1.6f + 0.6f * pulse)))
        drawCircle(Brush.sweepGradient(fire, c), r, c, style = Stroke(w))
    }
    for (i in 0 until 8) {
        val base = i * 45.0 + rot * 1.8
        val ang = Math.toRadians(base).toFloat()
        val wobble = sin((pulse * PI + i).toFloat()) * w * 0.9f
        val p = Offset(c.x + (r + wobble) * cos(ang), c.y + (r + wobble) * sin(ang))
        val emberSize = w * (0.28f + 0.22f * sin((pulse * 2 * PI + i * 1.7).toFloat()).let { if (it < 0) -it else it })
        drawCircle(if (i % 2 == 0) Color(0xFFFFB300) else Color(0xFFFF5722), emberSize, p)
    }
}

private fun DrawScope.drawFrost(
    c: Offset, r: Float, w: Float, rot: Float, pulse: Float
) {
    val ice = Color(0xFF80D8FF)
    val iceDeep = Color(0xFF448AFF)
    drawCircle(ice.copy(alpha = 0.14f + 0.18f * pulse), r + w, c, style = Stroke(w * 2f))
    drawCircle(Brush.linearGradient(listOf(ice, Color.White, iceDeep), c.copy(x = c.x - r), c.copy(x = c.x + r)), r, c, style = Stroke(w * 0.85f))
    rotate(rot, c) {
        for (i in 0 until 6) {
            val ang = Math.toRadians((i * 60.0)).toFloat()
            val p = Offset(c.x + r * cos(ang), c.y + r * sin(ang))
            rotate(i * 60f + 45f, p) {
                val path = Path().apply {
                    moveTo(p.x, p.y - w * 1.1f)
                    lineTo(p.x + w * 0.55f, p.y)
                    lineTo(p.x, p.y + w * 1.1f)
                    lineTo(p.x - w * 0.55f, p.y)
                    close()
                }
                drawPath(path, if (i % 2 == 0) ice else Color.White.copy(alpha = 0.9f))
            }
        }
    }
}

private fun DrawScope.drawGalaxy(
    c: Offset, r: Float, w: Float, rot: Float, slowRot: Float, pulse: Float
) {
    val purple = Color(0xFF7C4DFF)
    val deep = Color(0xFF311B92)
    val star = Color(0xFFFFD54F)
    drawCircle(Brush.radialGradient(listOf(purple.copy(alpha = 0.30f + 0.15f * pulse), Color.Transparent), c, r + w * 2.2f), r + w * 2.2f, c)
    drawCircle(Brush.sweepGradient(listOf(deep, purple, Color(0xFFB388FF), deep), c), r, c, style = Stroke(w * 0.9f))
    val pa = Math.toRadians((rot * 1.5)).toFloat()
    val planet = Offset(c.x + r * cos(pa), c.y + r * sin(pa))
    drawCircle(Color(0xFF4FC3F7), w * 0.7f, planet)
    drawCircle(Color(0xFFB3E5FC).copy(alpha = 0.6f), w * 0.7f, planet, style = Stroke(w * 0.18f))
    rotate(rot * 1.5f - 25f, planet) {
        drawCircle(Color.White.copy(alpha = 0.55f), w * 1.05f, planet, style = Stroke(w * 0.14f))
    }
    for (i in 0 until 7) {
        val ang = Math.toRadians((slowRot + i * 51.4)).toFloat()
        val p = Offset(c.x + r * cos(ang), c.y + r * sin(ang))
        val tw = 0.4f + 0.6f * (0.5f + 0.5f * sin((pulse * 2 * PI + i * 2.1).toFloat()))
        drawCircle(star.copy(alpha = tw), w * 0.30f, p)
    }
}

private fun DrawScope.drawGold(
    c: Offset, r: Float, w: Float, rot: Float, pulse: Float
) {
    val gold = Color(0xFFFFD700)
    val goldDeep = Color(0xFFB8860B)
    val goldLight = Color(0xFFFFF8DC)
    drawCircle(gold.copy(alpha = 0.16f + 0.16f * pulse), r + w, c, style = Stroke(w * 1.8f))
    rotate(rot, c) {
        drawCircle(Brush.sweepGradient(listOf(goldDeep, gold, goldLight, gold, goldDeep), c), r, c, style = Stroke(w))
    }
    drawCircle(gold.copy(alpha = 0.8f), r + w * 1.15f, c, style = Stroke(w * 0.22f))
    for (i in 0 until 4) {
        val ang = Math.toRadians((rot + i * 90.0)).toFloat()
        val p = Offset(c.x + (r + w * 1.15f) * cos(ang), c.y + (r + w * 1.15f) * sin(ang))
        val s = w * (0.5f + 0.5f * (0.5f + 0.5f * sin((pulse * 2 * PI + i * 1.57).toFloat())))
        val path = Path().apply {
            moveTo(p.x, p.y - s)
            quadraticBezierTo(p.x, p.y, p.x + s, p.y)
            quadraticBezierTo(p.x, p.y, p.x, p.y + s)
            quadraticBezierTo(p.x, p.y, p.x - s, p.y)
            quadraticBezierTo(p.x, p.y, p.x, p.y - s)
        }
        drawPath(path, goldLight)
    }
}
