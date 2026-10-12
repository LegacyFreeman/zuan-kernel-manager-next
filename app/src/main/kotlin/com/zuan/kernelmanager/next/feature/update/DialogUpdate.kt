/*
 * Copyright (c) 2025-2026 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.zuan.kernelmanager.next.feature.update

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.zuan.kernelmanager.next.R
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.zuan.kernelmanager.next.ui.components.ZkmHazeStyleCache
import com.zuan.kernelmanager.next.ui.components.ZkmSwitch

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onUpdateClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isDownloading by remember { mutableStateOf(false) }
    var isAutoDownload by remember { mutableStateOf(true) }

    // --- ANIMASI BLACK HOLE & KOSMIK ---
    val infiniteTransition = rememberInfiniteTransition(label = "cosmic_anim")

    // Rotasi Cakram Akresi (Accretion Disk)
    val diskRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "diskRotation"
    )

    // Denyut Gravitasi (Gravitational Breathing)
    val gravityPulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gravityPulse"
    )

    // Osilasi Melayang Halus di Ruang Hampa
    val floatingOffsetY by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatingOffsetY"
    )

    // Kerlip Bintang Kosmik
    val starTwinkle by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "starTwinkle"
    )

    Dialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        com.zuan.kernelmanager.next.ui.components.ConfigureDialogWindowBlur()
        // decorFitsSystemWindows = false -> inset (cutout / bar sistem / taskbar PC mode) diurus
        // frame aman; tinggi maksimum diukur dari ruang yang tersisa.
        com.zuan.kernelmanager.next.ui.adaptive.ZkmDialogSafeArea(horizontalMargin = 0.dp, verticalMargin = 0.dp) {
            AnimatedVisibility(
                visible = true,
                enter = scaleIn(animationSpec = spring(dampingRatio = 0.65f)),
                exit = scaleOut()
            ) {
                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier.padding(20.dp)
                ) {
                    
                    // ==========================================
                    // 1. BLACK HOLE SINGULARITY HERO
                    // ==========================================
                    Box(
                        modifier = Modifier
                            .zIndex(2f)
                            .offset(y = (-60).dp + floatingOffsetY.dp)
                            .size(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawEpicBlackHole(
                                rotationAngle = diskRotation,
                                pulseScale = gravityPulse
                            )
                        }
                    }

                    // ==========================================
                    // 2. DIALOG CARD
                    // ==========================================
                    Card(
                        modifier = Modifier
                            .widthIn(max = 520.dp)
                            .fillMaxWidth(0.95f)
                            .wrapContentHeight()
                            .padding(top = 20.dp),
                        shape = RoundedCornerShape(32.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        // Landscape: tinggi layar pendek -> isi kartu dibatasi & bisa discroll.
                        val updateCardMaxHeight = (com.zuan.kernelmanager.next.ui.adaptive.zkmDialogMaxHeight(1f) - 48.dp)
                            .coerceAtLeast(200.dp)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = updateCardMaxHeight)
                                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                        ) {
                            
                            // ==========================================
                            // 3. HEADER KOSMIK & HORIZON WAVE
                            // ==========================================
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp)
                            ) {
                                // Latar Belakang Gradien Deep Space Nebula
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(
                                                    Color(0xFF030014), // Deep Space Void
                                                    Color(0xFF0F0728), // Violet Nebula
                                                    Color(0xFF1D0C42)  // Cosmic Indigo
                                                )
                                            )
                                        )
                                )

                                // Partikel Bintang Kosmik
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawCosmicStars(twinkle = starTwinkle)
                                }

                                // Gelombang Lengkungan Horizon Menembus Badan Kartu Putih
                                Canvas(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .height(55.dp)
                                ) {
                                    drawCosmicHorizonWave(color = Color.White)
                                }
                            }

                            // ==========================================
                            // 4. KONTEN & DETAIL UPDATE
                            // ==========================================
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                
                                Text(
                                    text = stringResource(R.string.update_title, updateInfo.version),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 22.sp
                                    ),
                                    color = Color(0xFF1E1B4B)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Kotak Changelog Scrollable
                                val scrollState = rememberScrollState()
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 140.dp)
                                        .background(
                                            color = Color(0xFFF8FAFC),
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .padding(14.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.verticalScroll(scrollState)
                                    ) {
                                        val formattedChangelog = updateInfo.changelog
                                            .replace("# ", "")
                                            .replace("## ", "")
                                            .replace("- ", "• ")
                                            .replace("* ", "• ")
                                        
                                        Text(
                                            text = formattedChangelog,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                lineHeight = 20.sp,
                                                color = Color(0xFF475569)
                                            )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // --- DOWNLOAD MODE SELECTION ---
                                if (!isDownloading) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color(0xFFF5F3FF)
                                        ),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = stringResource(R.string.auto_download),
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.SemiBold
                                                    ),
                                                    color = Color(0xFF5B21B6)
                                                )
                                                Text(
                                                    text = stringResource(R.string.auto_download_desc),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color(0xFF7C3AED)
                                                )
                                            }
                                            
                                            ZkmSwitch(
                                                checked = isAutoDownload,
                                                onCheckedChange = { isAutoDownload = it },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color(0xFF7C3AED),
                                                    checkedTrackColor = Color(0xFF7C3AED).copy(alpha = 0.35f)
                                                )
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // --- ACTION BUTTONS ---
                                if (isDownloading) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(color = Color(0xFF7C3AED))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = stringResource(R.string.downloading_update),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.Gray
                                        )
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Tombol Batal
                                        OutlinedButton(
                                            onClick = onDismiss,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(50.dp),
                                            shape = RoundedCornerShape(50),
                                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                                width = 1.dp,
                                                brush = SolidColor(Color(0xFFE2E8F0))
                                            ),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = Color(0xFF64748B)
                                            )
                                        ) {
                                            Text(
                                                stringResource(R.string.btn_cancel),
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 16.sp
                                            )
                                        }

                                        // Tombol Update / Open Browser
                                        Button(
                                            onClick = {
                                                if (isAutoDownload) {
                                                    isDownloading = true
                                                    scope.launch {
                                                        onUpdateClick()
                                                        delay(500)
                                                    }
                                                } else {
                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                        data = Uri.parse(updateInfo.downloadUrl)
                                                    }
                                                    context.startActivity(intent)
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(50.dp),
                                            shape = RoundedCornerShape(50),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isAutoDownload) 
                                                    Color(0xFF7C3AED) else Color(0xFF0284C7)
                                            ),
                                            elevation = ButtonDefaults.buttonElevation(
                                                defaultElevation = 6.dp,
                                                pressedElevation = 2.dp
                                            )
                                        ) {
                                            if (isAutoDownload) {
                                                Text(
                                                    stringResource(R.string.btn_update),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp,
                                                    color = Color.White
                                                )
                                            } else {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.OpenInBrowser,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Text(
                                                        stringResource(R.string.btn_manual_download),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// CUSTOM CANVAS GRAPHICS
// =========================================================================

/**
 * Menggambar Lubang Hitam (Singularity, Lensing Aura, & Accretion Disk tebal bercahaya)
 */
private fun DrawScope.drawEpicBlackHole(rotationAngle: Float, pulseScale: Float) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val holeRadius = (size.width * 0.20f) * pulseScale

    // 1. Aura Pendar Luar Kuat agar bola hitam tampak kontras
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFA855F7).copy(alpha = 0.65f), // Neon Purple
                Color(0xFF38BDF8).copy(alpha = 0.35f), // Cyan Edge
                Color.Transparent
            ),
            center = center,
            radius = size.width * 0.48f
        ),
        radius = size.width * 0.48f,
        center = center
    )

    // 2. Gravitational Lensing (Cahaya melengkung ke atas akibat distorsi gravitasi)
    drawArc(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF38BDF8).copy(alpha = 0.1f),
                Color(0xFFF472B6), // Pink neon
                Color(0xFFFBBF24), // Amber hangat
                Color(0xFF38BDF8).copy(alpha = 0.1f)
            )
        ),
        startAngle = 190f,
        sweepAngle = 160f,
        useCenter = false,
        topLeft = Offset(center.x - holeRadius * 1.55f, center.y - holeRadius * 1.7f),
        size = Size(holeRadius * 3.1f, holeRadius * 2.8f),
        style = Stroke(width = 12f, cap = StrokeCap.Round)
    )

    // 3. Cakram Akresi Belakang (Tebal & Bergradasi Partikel)
    rotate(rotationAngle * 0.6f + 25f, pivot = center) {
        drawOval(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color(0xFF06B6D4), // Cyan
                    Color(0xFFEC4899), // Neon Pink
                    Color(0xFFF59E0B), // Warm Gold
                    Color(0xFF06B6D4)
                ),
                center = center
            ),
            topLeft = Offset(center.x - size.width * 0.45f, center.y - 18f),
            size = Size(size.width * 0.90f, 36f),
            style = Stroke(width = 14f)
        )
    }

    // 4. Photon Ring Terluar (Garis batas cahaya putih-cyan menyilaukan)
    drawCircle(
        brush = Brush.sweepGradient(
            colors = listOf(
                Color.White,
                Color(0xFF38BDF8),
                Color(0xFFF472B6),
                Color.White
            ),
            center = center
        ),
        radius = holeRadius + 4f,
        center = center,
        style = Stroke(width = 4.5f)
    )

    // 5. EVENT HORIZON (Inti Lubang Hitam Mutlak - Pitch Black)
    drawCircle(
        color = Color(0xFF05010D),
        radius = holeRadius,
        center = center
    )

    // 6. Cakram Akresi Depan (Melintasi bagian depan lubang hitam)
    rotate(rotationAngle * 0.6f + 25f, pivot = center) {
        drawArc(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFFF59E0B),
                    Color.White, // Titik terpanas di tengah
                    Color(0xFFEC4899),
                    Color.Transparent
                )
            ),
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(center.x - size.width * 0.44f, center.y - 16f),
            size = Size(size.width * 0.88f, 32f),
            style = Stroke(width = 13f, cap = StrokeCap.Round)
        )
    }

    // 7. Partikel Energi (Relativistic Jets)
    drawCircle(color = Color.White, radius = 3.5f, center = Offset(center.x, center.y - holeRadius * 1.85f))
    drawCircle(color = Color(0xFF38BDF8), radius = 2.5f, center = Offset(center.x + 8f, center.y - holeRadius * 2.1f))
    drawCircle(color = Color(0xFFF472B6), radius = 2.5f, center = Offset(center.x - 6f, center.y + holeRadius * 1.8f))
}

/**
 * Menggambar Partikel Bintang Kosmik Berkedip
 */
private fun DrawScope.drawCosmicStars(twinkle: Float) {
    val starCoords = listOf(
        Offset(0.12f, 0.25f) to 2.2f,
        Offset(0.25f, 0.65f) to 1.5f,
        Offset(0.82f, 0.30f) to 2.0f,
        Offset(0.70f, 0.70f) to 1.8f,
        Offset(0.88f, 0.55f) to 1.2f,
        Offset(0.18f, 0.80f) to 2.5f,
        Offset(0.40f, 0.20f) to 1.4f,
        Offset(0.60f, 0.15f) to 1.8f
    )

    starCoords.forEachIndexed { index, (relOffset, radius) ->
        val currentAlpha = if (index % 2 == 0) twinkle else (1.3f - twinkle).coerceIn(0.2f, 1f)
        drawCircle(
            color = Color.White.copy(alpha = currentAlpha * 0.85f),
            radius = radius,
            center = Offset(size.width * relOffset.x, size.height * relOffset.y)
        )
    }
}

/**
 * Horizon Melengkung Kosmik yang Menyambung Mulus ke Badan Kartu Putih
 */
private fun DrawScope.drawCosmicHorizonWave(color: Color) {
    val width = size.width
    val height = size.height

    val path = Path().apply {
        moveTo(0f, height)
        lineTo(0f, height * 0.45f)

        cubicTo(
            width * 0.25f, height * 0.85f,
            width * 0.40f, height * 0.20f,
            width * 0.50f, height * 0.20f
        )
        cubicTo(
            width * 0.60f, height * 0.20f,
            width * 0.75f, height * 0.85f,
            width, height * 0.45f
        )

        lineTo(width, height)
        close()
    }

    drawPath(path = path, color = color)
}
