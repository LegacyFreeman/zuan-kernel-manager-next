@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.zuan.kernelmanager.next.feature.fps

import com.zuan.kernelmanager.next.ui.components.zkmLiteAlpha
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.data.database.FpsDataPoint
import com.zuan.kernelmanager.next.data.database.FpsDatabase
import com.zuan.kernelmanager.next.data.database.FpsSession
import com.zuan.kernelmanager.next.data.export.DataExporter
import com.zuan.kernelmanager.next.library.device.monitor.MonitorReader
import com.zuan.kernelmanager.next.ui.components.LocalZkmHazeState
import com.zuan.kernelmanager.next.ui.components.zkmGlassCard
import com.zuan.kernelmanager.next.ui.components.zkmGlassTint
import com.zuan.kernelmanager.next.ui.theme.ZkmIconGlyph
import com.zuan.kernelmanager.next.ui.theme.zkmIconShape
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
//  Palet seri grafik (dipakai konsisten di semua kartu)
// ---------------------------------------------------------------------------------------------
private val ColFps = Color(0xFF4C8DFF)
private val ColTemp = Color(0xFFFF9F68)
private val ColCpu = Color(0xFFA77BFF)
private val ColGpu = Color(0xFF2BD4C4)
private val ColFrame = Color(0xFFFF6FB1)
private val ColPower = Color(0xFF5BA8FF)
private val ColBattery = Color(0xFF5DD39E)
private val ColRam = Color(0xFFFFC857)
private val ColWarn = Color(0xFFFF6B6B)

/** Satu garis di grafik. Setiap seri dinormalisasi ke skalanya sendiri (FPS & suhu bisa satu kartu). */
private class ChartSeries(
    val label: String,
    val color: Color,
    val values: List<Float>,
    val fixedMax: Float? = null,
    val format: (Float) -> String
)

// ---------------------------------------------------------------------------------------------
//  Halaman utama tab Stats
// ---------------------------------------------------------------------------------------------
@Composable
fun FpsManagerStatsContent(
    contentColor: Color,
    subContentColor: Color,
    isGlassActive: Boolean = false,
    hazeState: HazeState,
    glassModifier: Modifier = Modifier,
    topPadding: Dp
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessions by FpsDatabase.getDatabase(context).fpsDao().getAllSessions().collectAsState(initial = emptyList())
    var expandedId by remember { mutableStateOf<Long?>(null) }
    var search by remember { mutableStateOf("") }
    var sortByFps by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    val filtered = remember(sessions, search, sortByFps) {
        val q = search.trim()
        sessions.filter { q.isEmpty() || it.packageName.contains(q, true) }
            .let { if (sortByFps) it.sortedByDescending(FpsSession::avgFps) else it }
    }

    LazyColumn(
        contentPadding = PaddingValues(top = topPadding + 8.dp, bottom = 100.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            OverallCard(
                sessions = sessions, isGlass = isGlassActive, content = contentColor, sub = subContentColor,
                onClear = { confirmClear = true }
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    modifier = Modifier.weight(1f), singleLine = true,
                    shape = RoundedCornerShape(50),
                    placeholder = { Text(stringResource(R.string.fps_search_app)) },
                    leadingIcon = { Icon(Icons.Default.Search, null) }
                )
                FilterChip(
                    selected = sortByFps, onClick = { sortByFps = !sortByFps },
                    shape = RoundedCornerShape(50),
                    label = { Text(stringResource(R.string.fps_best_fps)) },
                    leadingIcon = if (sortByFps) ({ Icon(Icons.Default.Analytics, null, Modifier.size(16.dp)) }) else null
                )
            }
        }
        items(filtered, key = { it.id }) { session ->
            SessionCard(
                session = session,
                expanded = expandedId == session.id,
                contentColor = contentColor, subContentColor = subContentColor,
                isGlass = isGlassActive,
                onExpand = { expandedId = if (expandedId == session.id) null else session.id },
                onDelete = { scope.launch(Dispatchers.IO) { FpsDatabase.getDatabase(context).fpsDao().deleteSession(session.id) } }
            )
        }
        if (filtered.isEmpty()) item { EmptyHistoryCard(contentColor, subContentColor, isGlassActive) }
    }

    if (confirmClear) {
        com.zuan.kernelmanager.next.ui.components.BlurredAlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.fps_stats_delete_all)) },
            text = { Text(stringResource(R.string.fps_stats_delete_all_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch(Dispatchers.IO) { FpsDatabase.getDatabase(context).fpsDao().clearAll() }
                }) { Text(stringResource(R.string.fps_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }
}

// ---------------------------------------------------------------------------------------------
//  Kartu ringkasan keseluruhan
// ---------------------------------------------------------------------------------------------
@Composable
private fun OverallCard(sessions: List<FpsSession>, isGlass: Boolean, content: Color, sub: Color, onClear: () -> Unit) {
    val totalDuration = sessions.sumOf { it.durationSeconds }
    val avg = sessions.map { it.avgFps }.filter { it > 0f }.average().takeIf { !it.isNaN() } ?: 0.0
    val best = sessions.maxOfOrNull { it.avgFps } ?: 0f
    StatsCard(isGlass, shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(zkmIconShape(CircleShape)).background(ColFps.copy(alpha = .16f)),
                    contentAlignment = Alignment.Center
                ) { ZkmIconGlyph(icon = Icons.Default.History, tint = ColFps, iconSize = 22.dp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.fps_session_history), color = content, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.fps_stats_overall), color = sub, style = MaterialTheme.typography.bodySmall)
                }
                if (sessions.isNotEmpty()) IconButton(onClick = onClear) {
                    Icon(Icons.Default.Delete, stringResource(R.string.fps_stats_delete_all), tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigStat(stringResource(R.string.fps_stats_sessions_label), "${sessions.size}", ColFps, content, sub, Modifier.weight(1f))
                BigStat(stringResource(R.string.fps_stats_total_time), formatDuration(totalDuration), ColCpu, content, sub, Modifier.weight(1.3f))
                BigStat(stringResource(R.string.fps_stats_avg_label), "%.1f".format(avg), getFpsColor(avg.toFloat()), content, sub, Modifier.weight(1f))
                BigStat(stringResource(R.string.fps_stats_best_label), "%.0f".format(best), ColBattery, content, sub, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BigStat(label: String, value: String, accent: Color, content: Color, sub: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha = .10f)).padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(label, color = sub, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------------------------------------
//  Kartu sesi (ringkas + detail)
// ---------------------------------------------------------------------------------------------
@Composable
private fun SessionCard(
    session: FpsSession, expanded: Boolean, contentColor: Color, subContentColor: Color,
    isGlass: Boolean, onExpand: () -> Unit, onDelete: () -> Unit
) {
    val context = LocalContext.current
    var points by remember(session.id) { mutableStateOf<List<FpsDataPoint>>(emptyList()) }
    var showAllMetrics by remember(session.id) { mutableStateOf(false) }
    val appName = remember(session.packageName) { MonitorReader.getAppName(context, session.packageName) }
    val appIcon = remember(session.packageName) { MonitorReader.getAppIcon(context, session.packageName) }
    LaunchedEffect(expanded, session.id) {
        if (expanded && points.isEmpty()) withContext(Dispatchers.IO) {
            points = FpsDatabase.getDatabase(context).fpsDao().getPointsForSession(session.id)
        }
    }

    StatsCard(isGlass, shape = RoundedCornerShape(26.dp), onClick = onExpand, modifier = Modifier.animateContentSize()) {
        Column(Modifier.padding(16.dp)) {
            // --- header: ikon, nama, waktu, FPS rata-rata ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (appIcon != null) AppIconView(appIcon, Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)))
                else Box(
                    Modifier.size(46.dp).clip(zkmIconShape(CircleShape)).background(contentColor.copy(alpha = .08f)),
                    contentAlignment = Alignment.Center
                ) { ZkmIconGlyph(icon = Icons.Default.Gamepad, tint = subContentColor, iconSize = 24.dp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(appName, color = contentColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        SimpleDateFormat("dd MMM yyyy • HH:mm:ss", Locale.getDefault()).format(Date(session.startTime)) + "  •  " + formatDuration(session.durationSeconds),
                        color = subContentColor, style = MaterialTheme.typography.bodySmall, maxLines = 1
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("%.0f".format(session.avgFps), color = getFpsColor(session.avgFps), fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                    Text("AVG FPS", color = subContentColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
            // --- chip ringkas ---
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoPill("1% ${"%.0f".format(session.onePercentLow)}", getFpsColor(session.onePercentLow), Modifier.weight(1f))
                InfoPill("Jank ${"%.1f".format(session.jankPercent)}%", if (session.jankPercent < 5f) ColBattery else ColTemp, Modifier.weight(1f))
                InfoPill("%.1fW".format(session.avgWatt), ColPower, Modifier.weight(1f))
                InfoPill("%.0f°C".format(session.avgTemp), if (session.maxTemp >= 90f) ColWarn else ColTemp, Modifier.weight(1f))
            }

            AnimatedVisibility(expanded) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    if (session.maxTemp >= 90f) {
                        val hotSeconds = if (points.isEmpty()) 0L
                        else points.count { it.temp >= 90f } * session.sampleIntervalMs.coerceAtLeast(1L) / 1000L
                        TempWarningBanner(session.maxTemp, hotSeconds)
                        Spacer(Modifier.height(12.dp))
                    }
                    HeadlineStats(session, contentColor, subContentColor)
                    Spacer(Modifier.height(14.dp))

                    if (points.size > 1) {
                        val times = remember(points) { points.map { it.timestamp } }
                        buildCharts(points, session).forEach { (title, series, refresh) ->
                            ChartCard(title, series, times, refresh, contentColor, subContentColor)
                            Spacer(Modifier.height(10.dp))
                        }
                    } else {
                        Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    }

                    // --- semua metrik (bisa dilipat) ---
                    TextButton(onClick = { showAllMetrics = !showAllMetrics }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Icon(if (showAllMetrics) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.fps_stats_tab_details))
                    }
                    AnimatedVisibility(showAllMetrics) { DetailMetricGrid(session, contentColor, subContentColor) }

                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { if (points.isNotEmpty()) DataExporter.exportSessionToCsv(context, session, points) }) {
                            Icon(Icons.Default.Share, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.fps_export_full_csv))
                        }
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = onDelete) { Text(stringResource(R.string.fps_delete), color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

/** Susunan kartu grafik seperti halaman detail Pulse: FPS+suhu, frame time, beban, clock, daya, RAM. */
@Composable
private fun buildCharts(points: List<FpsDataPoint>, s: FpsSession): List<Triple<String, List<ChartSeries>, Float?>> {
    val fpsMax = (maxOf(s.refreshRateHz, s.maxFps)).coerceAtLeast(60f) * 1.05f
    val result = mutableListOf<Triple<String, List<ChartSeries>, Float?>>()
    fun List<Float>.hasData() = any { it > 0f }

    result += Triple(
        "${stringResource(R.string.fps_metric_fps)} / ${stringResource(R.string.fps_chart_soc_temperature)}",
        listOf(
            ChartSeries("FPS", ColFps, points.map { it.fps }, fpsMax) { "%.0f".format(it) },
            ChartSeries("°C", ColTemp, points.map { it.temp }, 100f) { "%.1f°C".format(it) }
        ),
        s.refreshRateHz.takeIf { it > 0f }
    )
    result += Triple(
        stringResource(R.string.fps_chart_frame_time),
        listOf(ChartSeries("ms", ColFrame, points.map { it.frameTimeMs }) { "%.1f ms".format(it) }),
        null
    )
    val load = buildList {
        add(ChartSeries("CPU", ColCpu, points.map { it.cpuLoad.toFloat() }, 100f) { "%.0f%%".format(it) })
        val g = points.map { it.gpuLoad.toFloat() }
        if (g.hasData()) add(ChartSeries("GPU", ColGpu, g, 100f) { "%.0f%%".format(it) })
    }
    result += Triple("${stringResource(R.string.fps_chart_cpu_load)} / GPU", load, null)

    val clocks = buildList {
        val c = points.map { it.cpuFreqMHz }
        val g = points.map { it.gpuFreqMHz }
        if (c.hasData()) add(ChartSeries("CPU", ColCpu, c) { "%.0f MHz".format(it) })
        if (g.hasData()) add(ChartSeries("GPU", ColGpu, g) { "%.0f MHz".format(it) })
    }
    if (clocks.isNotEmpty()) result += Triple(stringResource(R.string.fps_cpu_clock) + " / " + stringResource(R.string.fps_gpu_clock), clocks, null)

    val power = buildList {
        add(ChartSeries("W", ColPower, points.map { it.watt }) { "%.2f W".format(it) })
        add(ChartSeries("%", ColBattery, points.map { it.batteryLevel.toFloat() }, 100f) { "%.0f%%".format(it) })
    }
    result += Triple("${stringResource(R.string.fps_chart_power_draw)} / ${stringResource(R.string.fps_metric_battery)}", power, null)

    result += Triple(
        stringResource(R.string.fps_chart_ram_usage),
        listOf(ChartSeries("MB", ColRam, points.map { it.ramUsageMb.toFloat() }) { "%.0f MB".format(it) }),
        null
    )
    return result
}

// ---------------------------------------------------------------------------------------------
//  Angka besar ala Pulse: MAX / MIN / AVG / JITTER  +  1% LOW / AVG SOC / MAX SOC / AVG POWER
// ---------------------------------------------------------------------------------------------
@Composable
private fun HeadlineStats(s: FpsSession, content: Color, sub: Color) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.zkmLiteAlpha(.45f)).padding(vertical = 14.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            HeadlineCell(stringResource(R.string.fps_max_fps), "%.1f".format(s.maxFps), "FPS", ColFps, sub, Modifier.weight(1f))
            HeadlineCell(stringResource(R.string.fps_min_fps), "%.1f".format(s.minFps), "FPS", ColFps, sub, Modifier.weight(1f))
            HeadlineCell(stringResource(R.string.fps_avg_fps), "%.1f".format(s.avgFps), "FPS", getFpsColor(s.avgFps), sub, Modifier.weight(1f))
            HeadlineCell(stringResource(R.string.fps_jitter), stringResource(R.string.fps_value_jitter, s.variance), "FPS", ColCpu, sub, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth()) {
            HeadlineCell(stringResource(R.string.fps_summary_badge_one_percent), "%.0f".format(s.onePercentLow), "FPS", getFpsColor(s.onePercentLow), sub, Modifier.weight(1f))
            HeadlineCell(stringResource(R.string.fps_avg_temp), "%.1f".format(s.avgTemp), "SoC(°C)", ColTemp, sub, Modifier.weight(1f))
            HeadlineCell(stringResource(R.string.fps_max_temp), "%.1f".format(s.maxTemp), "SoC(°C)", if (s.maxTemp >= 90f) ColWarn else ColTemp, sub, Modifier.weight(1f))
            HeadlineCell(stringResource(R.string.fps_avg_power), "%.2f".format(s.avgWatt), "W", ColPower, sub, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeadlineCell(label: String, value: String, unit: String, color: Color, sub: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), color = sub, fontSize = 9.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(unit, color = sub, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun TempWarningBanner(maxTemp: Float, seconds: Long) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ColWarn.copy(alpha = .14f)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Warning, null, tint = ColWarn, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text("SoC temperature too high  MAX:%.1f°C".format(maxTemp), color = ColWarn, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (seconds > 0) Text("≥90°C ${seconds}s", color = ColWarn, fontSize = 11.sp)
    }
}

// ---------------------------------------------------------------------------------------------
//  Kartu grafik multi-seri dengan scrub (geser jari untuk lihat nilai di titik tertentu)
// ---------------------------------------------------------------------------------------------
@Composable
private fun ChartCard(
    title: String, series: List<ChartSeries>, times: List<Long>, refreshLine: Float?,
    content: Color, sub: Color
) {
    val n = times.size
    if (n < 2 || series.isEmpty()) return
    var scrub by remember(times) { mutableStateOf<Int?>(null) }
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f)

    // Downsample supaya grafik tetap ringan walau sesi panjang.
    val idx = remember(n) {
        if (n <= 140) (0 until n).toList() else (0 until 140).map { (it * (n - 1f) / 139f).roundToInt() }
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.zkmLiteAlpha(.45f)).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = content, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            scrub?.let { Text(formatTime(times[idx[it]]), color = sub, fontSize = 11.sp) }
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier.fillMaxWidth().height(130.dp)
                .pointerInput(idx) {
                    // Sentuh / geser horizontal = scrub. Tidak meng-consume, jadi scroll vertikal tetap jalan.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        fun at(x: Float) = (x / size.width * (idx.size - 1)).roundToInt().coerceIn(0, idx.size - 1)
                        scrub = at(down.position.x)
                        do {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull() ?: break
                            if (c.isConsumed && kotlin.math.abs(c.position.y - down.position.y) > 24f) break
                            scrub = at(c.position.x)
                        } while (ev.changes.any { it.pressed })
                        scrub = null
                    }
                }
        ) {
            val w = size.width; val h = size.height
            for (i in 1..3) {
                val y = h * i / 4f
                drawLine(grid, Offset(0f, y), Offset(w, y), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            }
            series.forEachIndexed { si, sr ->
                val vals = idx.map { sr.values[it].coerceAtLeast(0f) }
                val top = (sr.fixedMax ?: ((vals.maxOrNull() ?: 1f) * 1.12f)).coerceAtLeast(1f)
                val step = w / (vals.size - 1).coerceAtLeast(1)
                val pts = vals.mapIndexed { i, v -> Offset(i * step, h - (v / top).coerceIn(0f, 1f) * h) }
                val line = smoothPath(pts)
                if (si == 0) {
                    val fill = Path().apply { addPath(line); lineTo(w, h); lineTo(0f, h); close() }
                    drawPath(fill, Brush.verticalGradient(listOf(sr.color.copy(alpha = .22f), Color.Transparent)))
                }
                drawPath(line, sr.color, style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                if (si == 0 && refreshLine != null && sr.label == "FPS") {
                    val ty = h - (refreshLine / top).coerceIn(0f, 1f) * h
                    drawLine(Color.White.copy(alpha = .3f), Offset(0f, ty), Offset(w, ty), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f)))
                }
            }
            scrub?.let { i ->
                val x = i * (w / (idx.size - 1).coerceAtLeast(1))
                drawLine(Color.White.copy(alpha = .5f), Offset(x, 0f), Offset(x, h), 1.5f)
                series.forEach { sr ->
                    val vals = idx.map { sr.values[it].coerceAtLeast(0f) }
                    val top = (sr.fixedMax ?: ((vals.maxOrNull() ?: 1f) * 1.12f)).coerceAtLeast(1f)
                    val y = h - (vals[i] / top).coerceIn(0f, 1f) * h
                    drawCircle(sr.color, 6f, Offset(x, y)); drawCircle(Color.White, 2.5f, Offset(x, y))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(times.first()), color = sub, fontSize = 9.sp)
            Text(stringResource(R.string.fps_samples, n), color = sub, fontSize = 9.sp)
            Text(formatTime(times.last()), color = sub, fontSize = 9.sp)
        }
        Spacer(Modifier.height(8.dp))
        // legenda: nilai di titik scrub, atau rata-rata bila tidak disentuh
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            series.forEach { sr ->
                val shown = scrub?.let { sr.values[idx[it]] } ?: sr.values.average().toFloat()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(sr.color))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "${sr.label} ${sr.format(shown)}" + if (scrub == null) " avg" else "",
                        color = sr.color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                    )
                }
            }
        }
    }
}

/** Garis halus (cubic) antar titik, lebih enak dilihat daripada patahan lurus. */
private fun smoothPath(pts: List<Offset>): Path {
    val path = Path()
    if (pts.isEmpty()) return path
    path.moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) {
        val p0 = pts[i - 1]; val p1 = pts[i]
        val mx = (p0.x + p1.x) / 2f
        path.cubicTo(mx, p0.y, mx, p1.y, p1.x, p1.y)
    }
    return path
}

// ---------------------------------------------------------------------------------------------
//  Grid semua metrik (detail lengkap)
// ---------------------------------------------------------------------------------------------
@Composable
private fun DetailMetricGrid(s: FpsSession, content: Color, sub: Color) {
    val metrics = listOf(
        stringResource(R.string.fps_p95_frame) to stringResource(R.string.fps_value_ms, s.frameTimeP95Ms),
        stringResource(R.string.fps_summary_badge_zero_one_percent) to "%.0f".format(s.zeroOnePercentLow),
        stringResource(R.string.fps_avg_cpu) to stringResource(R.string.fps_value_percent, s.avgCpu),
        stringResource(R.string.fps_max_cpu) to "${s.maxCpu}%",
        stringResource(R.string.fps_avg_gpu) to stringResource(R.string.fps_value_percent, s.avgGpuLoad),
        stringResource(R.string.fps_max_gpu) to "${s.maxGpuLoad}%",
        stringResource(R.string.fps_max_power) to stringResource(R.string.fps_value_watt, s.maxWatt),
        stringResource(R.string.fps_avg_ram) to stringResource(R.string.fps_value_mb, s.avgRam),
        stringResource(R.string.fps_max_ram) to stringResource(R.string.fps_value_mb, s.maxRam),
        stringResource(R.string.fps_ram_peak) to "${s.maxRamPercent}%",
        stringResource(R.string.fps_cpu_clock) to stringResource(R.string.fps_value_mhz, s.avgCpuFreqMHz),
        stringResource(R.string.fps_gpu_clock) to stringResource(R.string.fps_value_mhz, s.avgGpuFreqMHz),
        stringResource(R.string.fps_battery) to stringResource(R.string.fps_value_battery_range, s.startBattery, s.endBattery),
        stringResource(R.string.fps_voltage) to stringResource(R.string.fps_value_mv, s.avgVoltageMv),
        stringResource(R.string.fps_current) to stringResource(R.string.fps_value_ma, s.avgCurrentMa),
        stringResource(R.string.fps_refresh) to stringResource(R.string.fps_value_hz, s.refreshRateHz),
        stringResource(R.string.fps_renderer) to s.renderer,
        stringResource(R.string.fps_source) to s.fpsSource,
        stringResource(R.string.fps_smoothness) to stringResource(R.string.fps_value_smoothness, s.smoothness),
        stringResource(R.string.fps_summary_badge_jank) to "%.1f%%".format(s.jankPercent)
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        metrics.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value) -> MetricTile(label, value, content, sub, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, content: Color, sub: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest.zkmLiteAlpha(.45f)).padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(label, fontSize = 10.sp, color = sub, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = content, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------------------------------------
//  Komponen kecil
// ---------------------------------------------------------------------------------------------
/** Kartu: kaca (haze) bila mode glass aktif, selain itu kartu tonal biasa. */
@Composable
private fun StatsCard(
    isGlass: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val click = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    if (isGlass) {
        Column(
            modifier.fillMaxWidth()
                .zkmGlassCard(true, LocalZkmHazeState.current, shape, zkmGlassTint(MaterialTheme.colorScheme.surfaceContainer, zkmIsDarkTheme(), 0.45f), 24.dp)
                .then(click),
            content = content
        )
    } else {
        Column(
            modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainer).then(click),
            content = content
        )
    }
}

@Composable
private fun InfoPill(text: String, color: Color, modifier: Modifier) {
    Text(
        text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = .13f)).padding(vertical = 6.dp)
    )
}

@Composable
private fun EmptyHistoryCard(content: Color, sub: Color, isGlass: Boolean) {
    StatsCard(isGlass, shape = RoundedCornerShape(26.dp)) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.History, null, Modifier.size(36.dp), tint = sub)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.fps_no_sessions), color = content, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.fps_no_sessions_desc), color = sub, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

private fun formatTime(ms: Long) = "%02d:%02d".format(ms / 60000, (ms / 1000) % 60)
private fun formatDuration(sec: Long) = "%02d:%02d:%02d".format(sec / 3600, (sec / 60) % 60, sec % 60)

// --- dipakai file lain (FpsHomeTab / FpsGameTab) ---
@Composable
fun AppIconView(drawable: Drawable, modifier: Modifier = Modifier) {
    AndroidView(factory = { ctx -> ImageView(ctx).apply { setImageDrawable(drawable); scaleType = ImageView.ScaleType.FIT_CENTER } }, modifier = modifier)
}

@Composable
fun StatBox(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(70.dp)) {
        Text(label, fontSize = 10.sp, color = Color.Gray); Text(value, fontWeight = FontWeight.Bold, color = color)
    }
}

fun getFpsColor(fps: Float): Color = when {
    fps >= 55f -> Color(0xFF5DD39E)
    fps >= 30f -> Color(0xFFFFC857)
    else -> Color(0xFFFF6B6B)
}
