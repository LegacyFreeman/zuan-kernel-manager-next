/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.overlay

import android.os.SystemClock
import android.view.Gravity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * =====================================================================================
 * EMPAT OVERLAY MONITOR BARU (diluncurkan dari Quick Tools)
 *
 *  - SystemMonitorService      : ring CPU/GPU/baterai + tabel RAM, DDR, cluster & core, FPS, daya
 *  - MiniMonitorService        : bar kecil permanen di tengah atas layar (tembus sentuhan)
 *  - ThreadMonitorService      : thread terberat milik aplikasi yang sedang di depan
 *  - TemperatureMonitorService : BAT / CPU / GPU / DDR / CAM
 *
 * Sumber data ada di OverlayStats.kt; kerangka jendela di BaseOverlayService.kt.
 * =====================================================================================
 */

private val OverlayCard = Color(0xCC48484A)
private val OverlayText = Color.White
private val OverlayTextDim = Color.White.copy(alpha = 0.72f)
private val OverlayAccent = Color(0xFF7DB4F5)
private val OverlayHot = Color(0xFFFF8A65)
private val BatteryOrange = Color(0xFFE8903A)
private val BatteryLow = Color(0xFFE8603C)
private val SystemCard = Color(0x9948484A)
private val Mono = FontFamily.Monospace

/** Jalankan [sample] berulang di thread IO dan kembalikan hasil terbarunya sebagai state. */
@Composable
private fun <T> rememberSampled(initial: T, intervalMs: Long, sample: () -> T?): T {
    var value by remember { mutableStateOf(initial) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            while (isActive) {
                runCatching(sample).getOrNull()?.let { value = it }
                delay(intervalMs)
            }
        }
    }
    return value
}

// =======================================================================================
// SYSTEM MONITOR
// =======================================================================================

class SystemMonitorService : BaseOverlayService() {
    companion object {
        @Volatile var isRunning = false
    }

    override val notificationId = 111
    override fun onRunningChanged(running: Boolean) { isRunning = running }
    override fun buildLayoutParams() = overlayParams().apply { x = 40; y = 220 }

    @Composable
    override fun OverlayContent() {
        SystemMonitorContent(onDrag = { dx, dy -> moveBy(dx, dy) }, onClose = { stopSelf() })
    }
}

@Composable
private fun SystemMonitorContent(onDrag: (Float, Float) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val sampler = remember { SystemStatsSampler(context) }
    val stats = rememberSampled(SystemStats(), 1000L) { sampler.sample() }

    // Ketuk = lipat jadi deretan cincin / buka lagi, ketuk 3x cepat = tutup, tekan lama = tutup.
    var collapsed by remember { mutableStateOf(false) }
    val taps = remember { longArrayOf(0L, 0L) } // [waktu ketukan terakhir, jumlah ketukan beruntun]
    val tapModifier = Modifier.pointerInput(Unit) {
        detectTapGestures(
            onLongPress = { onClose() },
            onTap = {
                val now = SystemClock.uptimeMillis()
                taps[1] = if (now - taps[0] <= 450L) taps[1] + 1 else 1L
                taps[0] = now
                if (taps[1] >= 3L) onClose() else collapsed = !collapsed
            }
        )
    }

    @Composable
    fun Rings() {
        RingGauge(
            progress = stats.cpuLoad / 100f,
            center = "CPU",
            bottom = "${stats.cpuFreqMHz}MHz",
            color = if (stats.cpuLoad >= 80) OverlayHot else OverlayAccent
        )
        RingGauge(
            progress = if (stats.gpuLoad >= 0) stats.gpuLoad / 100f else 0f,
            center = if (stats.gpuLoadEstimated) "GPU~" else "GPU",
            bottom = if (stats.gpuFreqMHz > 0) "${stats.gpuFreqMHz}MHz" else "N/A",
            color = if (stats.gpuLoad >= 80) OverlayHot else OverlayAccent
        )
        RingGauge(
            progress = stats.batteryPct / 100f,
            center = "${stats.batteryPct}%" + if (stats.charging) "+" else "",
            bottom = String.format(Locale.US, "%.1f℃", stats.batteryTempC),
            // Cincin baterai oranye seperti referensi (merah-oranye bila <=20%).
            color = if (stats.batteryPct <= 20) BatteryLow else BatteryOrange
        )
    }

    if (collapsed) {
        // Mode lipat: hanya tiga cincin berjajar.
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(SystemCard)
                .overlayDrag(onDrag)
                .then(tapModifier)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Rings()
        }
        return
    }

    // Tinggi kolom cincin dipaksa sama dengan tinggi teks, cincin dibagi rata (atas / tengah / bawah)
    // supaya tampilan rapi seperti monitor referensi.
    Row(
        modifier = Modifier
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(8.dp))
            .background(SystemCard)
            .overlayDrag(onDrag)
            .then(tapModifier)
            .padding(start = 5.5.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Rings()
        }
        val lines = buildSystemText(stats).split('\n')
        Text(
            text = buildAnnotatedString {
                lines.forEachIndexed { i, line ->
                    if (i == 0) {
                        withStyle(SpanStyle(color = OverlayText, fontWeight = FontWeight.Bold)) { append(line) }
                    } else {
                        withStyle(SpanStyle(color = OverlayText.copy(alpha = 0.82f))) { append(line) }
                    }
                    if (i != lines.lastIndex) append('\n')
                }
            },
            fontFamily = Mono,
            style = TextStyle(fontSize = 6.9.sp, lineHeight = 8.1.sp)
        )
    }
}

/**
 * Kolom dibuat tetap (monospace):
 *  - nilai selalu mulai di kolom ke-6, rata kiri
 *  - baris core: beban rata kanan di kolom 0-3, frekuensi rata kiri di kolom ke-6
 */
private fun buildSystemText(s: SystemStats): String = buildString {
    appendLine(String.format(Locale.US, "%-5s %s %s", "#RAM", "${s.ramPct}%", "${s.swapPct}%"))
    appendLine(String.format(Locale.US, "%-5s %s", "#DDR", if (s.ddrMHz > 0) s.ddrMHz.toString() else "--"))
    appendLine(String.format(Locale.US, "%-5s %s", "#CPU", s.cpuTempC?.let { String.format(Locale.US, "%.1f℃", it) } ?: "--"))
    s.clusters.forEach { cluster ->
        appendLine(String.format(Locale.US, "%-5s %dMHz", cluster.label, cluster.freqMHz))
        cluster.cores.forEach { core ->
            appendLine(String.format(Locale.US, "%4s  %dM", "${core.loadPct}%", core.freqMHz))
        }
    }
    appendLine(String.format(Locale.US, "%-5s %.1f", "#FPS", s.fps))
    append(String.format(Locale.getDefault(), "%-5s %.2fW", "#PWR", s.watt))
}

@Composable
private fun RingGauge(progress: Float, center: String, bottom: String, color: Color = OverlayAccent) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 3.5.dp.toPx()
                val topLeft = Offset(stroke / 2f, stroke / 2f)
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = Color.White.copy(alpha = 0.18f),
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(stroke)
                )
                drawArc(
                    color = color,
                    startAngle = -90f, sweepAngle = 360f * progress.coerceIn(0f, 1f), useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
            Text(center, color = OverlayText, style = TextStyle(fontSize = 7.sp, lineHeight = 8.sp, fontWeight = FontWeight.Bold))
        }
        Text(bottom, color = OverlayText, style = TextStyle(fontSize = 6.sp, lineHeight = 8.sp, fontWeight = FontWeight.Bold))
    }
}

// =======================================================================================
// MINI MONITOR  (bar permanen di tengah atas, tembus sentuhan)
// =======================================================================================

class MiniMonitorService : BaseOverlayService() {
    companion object {
        @Volatile var isRunning = false
    }

    override val notificationId = 112
    override fun onRunningChanged(running: Boolean) { isRunning = running }
    override fun buildLayoutParams() = overlayParams(touchable = false).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        x = 0
        y = 0
    }

    @Composable
    override fun OverlayContent() {
        MiniMonitorContent()
    }
}

@Composable
private fun MiniMonitorContent() {
    val context = LocalContext.current
    val sampler = remember { SystemStatsSampler(context) }
    val stats = rememberSampled(SystemStats(), 1000L) { sampler.sample() }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
            .background(OverlayCard)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MiniItem(Icons.Rounded.Memory, "${stats.cpuFreqMHz}") { CoreBars(stats.allCores) }
        MiniItem(Icons.Rounded.DeveloperBoard, if (stats.gpuFreqMHz > 0) "${stats.gpuFreqMHz}" else "-")
        MiniItem(Icons.Rounded.Speed, "${stats.fps.toInt()}")
        MiniItem(Icons.Rounded.Bolt, String.format(Locale.getDefault(), "%.2fW", stats.watt))
    }
}

@Composable
private fun MiniItem(icon: ImageVector, value: String, extra: @Composable (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(icon, contentDescription = null, tint = OverlayText, modifier = Modifier.size(10.dp))
        extra?.invoke()
        Text(value, color = OverlayText, fontFamily = Mono, fontSize = 8.sp, maxLines = 1)
    }
}

/** Deretan batang tipis: tinggi tiap batang = beban satu core. */
@Composable
private fun CoreBars(cores: List<CoreStat>) {
    val count = cores.size.coerceAtLeast(1)
    Canvas(modifier = Modifier.width((count * 3).dp).height(9.dp)) {
        val step = size.width / count
        val barWidth = step * 0.7f
        cores.forEachIndexed { index, core ->
            val h = (size.height * core.loadPct / 100f).coerceAtLeast(1.5.dp.toPx())
            drawRect(
                color = if (core.loadPct >= 80) OverlayHot else OverlayAccent,
                topLeft = Offset(index * step, size.height - h),
                size = Size(barWidth, h)
            )
        }
    }
}

// =======================================================================================
// THREAD MONITOR
// =======================================================================================

class ThreadMonitorService : BaseOverlayService() {
    companion object {
        @Volatile var isRunning = false
    }

    override val notificationId = 113
    override fun onRunningChanged(running: Boolean) { isRunning = running }
    override fun buildLayoutParams() = overlayParams().apply { x = 30; y = 260 }

    @Composable
    override fun OverlayContent() {
        ThreadMonitorContent(onDrag = { dx, dy -> moveBy(dx, dy) }, onClose = { stopSelf() })
    }
}

@Composable
private fun ThreadMonitorContent(onDrag: (Float, Float) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val sampler = remember { ThreadSampler(context) }
    val snapshot = rememberSampled(ThreadSnapshot(message = "…"), 1500L) { sampler.sample() }

    val header = String.format(Locale.US, "%-4s %-5s %6s  %s", "CPU%", "CPUS", "TID", "COMM")
    val body = snapshot.rows.joinToString("\n") { r ->
        String.format(Locale.US, "%4.1f %-5s %6d  %s", r.cpuPct, r.cpus, r.tid, r.comm)
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(OverlayCard)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.overlayDrag(onDrag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = snapshot.packageName.ifEmpty { "Thread Monitor" },
                color = OverlayText, fontFamily = Mono, fontSize = 9.sp, maxLines = 1,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(
                Icons.Rounded.Close, contentDescription = null, tint = OverlayText,
                modifier = Modifier.size(16.dp).clickable { onClose() }
            )
        }
        Spacer(Modifier.height(1.dp))
        if (snapshot.message != null && snapshot.rows.isEmpty()) {
            Text(snapshot.message, color = OverlayTextDim, fontFamily = Mono, fontSize = 9.sp)
        } else {
            Text(header, color = OverlayText, fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, lineHeight = 13.sp)
            Text(body, color = OverlayText, fontFamily = Mono, fontSize = 9.sp, lineHeight = 13.sp)
        }
    }
}

// =======================================================================================
// TEMPERATURE MONITOR
// =======================================================================================

class TemperatureMonitorService : BaseOverlayService() {
    companion object {
        @Volatile var isRunning = false
    }

    override val notificationId = 114
    override fun onRunningChanged(running: Boolean) { isRunning = running }
    override fun buildLayoutParams() = overlayParams().apply { x = 0; y = 160 }

    @Composable
    override fun OverlayContent() {
        TemperatureMonitorContent(onDrag = { dx, dy -> moveBy(dx, dy) }, onClose = { stopSelf() })
    }
}

@Composable
private fun TemperatureMonitorContent(onDrag: (Float, Float) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val reader = remember { ThermalReader(context) }
    val temps = rememberSampled(TempReadings(), 2000L) { reader.read() }

    val rows = listOf(
        "BAT" to temps.battery,
        "CPU" to temps.cpu,
        "GPU" to temps.gpu,
        "DDR" to temps.ddr,
        "CAM" to temps.camera
    ).filter { it.second != null }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(OverlayCard)
            .overlayDrag(onDrag)
            // Tekan lama = tutup.
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onClose() }) }
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        if (rows.isEmpty()) {
            Text("--", color = OverlayTextDim, fontFamily = Mono, fontSize = 9.sp)
        }
        rows.forEach { (label, value) ->
            Text(
                text = String.format(Locale.US, "%s %.1f℃", label, value),
                color = if ((value ?: 0f) >= 70f) OverlayHot else OverlayText,
                fontFamily = Mono, fontSize = 9.sp, lineHeight = 11.sp
            )
        }
    }
}
