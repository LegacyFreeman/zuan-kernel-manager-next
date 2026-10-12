/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
@file:Suppress("DEPRECATION")

package com.zuan.kernelmanager.next.feature.home.menu

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.ui.navigation.NavigationRoute
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.zuan.kernelmanager.next.core.util.ZkmSamplerBus
import com.zuan.kernelmanager.next.ui.components.ZkmLite
import java.util.Locale

/** Satu sampel traffic jaringan (byte/detik + total sejak boot). */
private data class TrafficSample(
    val downBps: Long = 0L,
    val upBps: Long = 0L,
    val totalRx: Long = 0L,
    val totalTx: Long = 0L,
    val mobileRx: Long = 0L,
    val mobileTx: Long = 0L,
)

private enum class LinkType { WIFI, MOBILE, OTHER, NONE }

/** Info koneksi aktif: tipe, kekuatan sinyal, dan kecepatan link. */
private data class LinkInfo(
    val type: LinkType = LinkType.NONE,
    val signalDbm: Int? = null,
    val signalLevel: Int? = null,   // 0..4
    val linkSpeedMbps: Int? = null, // Wi-Fi: kecepatan link saat ini
    val frequencyMhz: Int? = null,  // Wi-Fi
    val estDownKbps: Int = 0,       // perkiraan bandwidth dari sistem
    val estUpKbps: Int = 0,
)

private fun formatSpeed(bps: Long): String {
    val b = bps.toDouble()
    return when {
        b >= 1024.0 * 1024.0 -> String.format(Locale.US, "%.2f MB/s", b / (1024.0 * 1024.0))
        b >= 1024.0 -> String.format(Locale.US, "%.1f KB/s", b / 1024.0)
        else -> "${bps} B/s"
    }
}

/** Versi ringkas untuk label sumbu grafik (lebar kolom label sempit). */
private fun formatSpeedShort(bps: Long): String {
    val b = bps.toDouble()
    return when {
        b >= 1024.0 * 1024.0 -> String.format(Locale.US, "%.1fM", b / (1024.0 * 1024.0))
        b >= 1024.0 -> String.format(Locale.US, "%.0fK", b / 1024.0)
        else -> "${bps}B"
    }
}

private fun formatBytes(bytes: Long): String {
    val b = bytes.toDouble()
    return when {
        b >= 1024.0 * 1024.0 * 1024.0 -> String.format(Locale.US, "%.2f GB", b / (1024.0 * 1024.0 * 1024.0))
        b >= 1024.0 * 1024.0 -> String.format(Locale.US, "%.1f MB", b / (1024.0 * 1024.0))
        b >= 1024.0 -> String.format(Locale.US, "%.0f KB", b / 1024.0)
        else -> "$bytes B"
    }
}

private fun readLinkInfo(context: Context): LinkInfo {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return LinkInfo()
    val net = cm.activeNetwork ?: return LinkInfo()
    val caps = cm.getNetworkCapabilities(net) ?: return LinkInfo()
    val type = when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> LinkType.WIFI
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> LinkType.MOBILE
        else -> LinkType.OTHER
    }
    var dbm: Int? = null
    var level: Int? = null
    var linkSpeed: Int? = null
    var freq: Int? = null
    if (type == LinkType.WIFI) {
        // RSSI / link speed / frekuensi tidak butuh izin lokasi (SSID yang butuh, jadi tidak ditampilkan).
        @Suppress("DEPRECATION")
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        @Suppress("DEPRECATION")
        val info = runCatching { wm?.connectionInfo }.getOrNull()
        if (info != null) {
            if (info.rssi in -127..0) {
                dbm = info.rssi
                level = WifiManager.calculateSignalLevel(info.rssi, 5)
            }
            if (info.linkSpeed > 0) linkSpeed = info.linkSpeed
            if (info.frequency > 0) freq = info.frequency
        }
    } else {
        val s = caps.signalStrength
        if (s != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED && s in -140..0) {
            dbm = s
            level = when {
                s >= -85 -> 4
                s >= -95 -> 3
                s >= -105 -> 2
                s >= -115 -> 1
                else -> 0
            }
        }
    }
    return LinkInfo(
        type = type,
        signalDbm = dbm,
        signalLevel = level,
        linkSpeedMbps = linkSpeed,
        frequencyMhz = freq,
        estDownKbps = caps.linkDownstreamBandwidthKbps,
        estUpKbps = caps.linkUpstreamBandwidthKbps,
    )
}

/** Network Traffic Monitor: hanya kecepatan traffic + kualitas/kecepatan sinyal Wi-Fi / data. */
@Composable
fun NetworkTrafficScreen(navController: NavController, hazeState: HazeState) {
    val context = LocalContext.current

    // Riwayat kecepatan download untuk grafik (60 titik ~ 1 menit).
    val history by produceState(initialValue = emptyList<TrafficSample>()) {
        var prevRx = TrafficStats.getTotalRxBytes()
        var prevTx = TrafficStats.getTotalTxBytes()
        var prevT = SystemClock.elapsedRealtime()
        val list = ArrayDeque<TrafficSample>()
        while (true) {
            val isFg = ZkmSamplerBus.isForeground.value
            val isLite = ZkmLite.active
            val pollDelay = when {
                !isFg -> 5_000L
                isLite -> 2_500L
                else -> 1_000L
            }
            delay(pollDelay)
            val now = SystemClock.elapsedRealtime()
            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()
            val dt = (now - prevT).coerceAtLeast(1L)
            val sample = if (rx < 0 || tx < 0) {
                TrafficSample()
            } else {
                TrafficSample(
                    downBps = ((rx - prevRx).coerceAtLeast(0) * 1000L) / dt,
                    upBps = ((tx - prevTx).coerceAtLeast(0) * 1000L) / dt,
                    totalRx = rx,
                    totalTx = tx,
                    mobileRx = TrafficStats.getMobileRxBytes().coerceAtLeast(0),
                    mobileTx = TrafficStats.getMobileTxBytes().coerceAtLeast(0),
                )
            }
            prevRx = rx; prevTx = tx; prevT = now
            list.addLast(sample)
            while (list.size > 60) list.removeFirst()
            value = list.toList()
        }
    }

    val link by produceState(initialValue = LinkInfo()) {
        value = withContext(Dispatchers.IO) { runCatching { readLinkInfo(context) }.getOrDefault(LinkInfo()) }
        ZkmSamplerBus.fastTick.collect {
            value = withContext(Dispatchers.IO) { runCatching { readLinkInfo(context) }.getOrDefault(LinkInfo()) }
        }
    }

    val now = history.lastOrNull() ?: TrafficSample()
    val peakDown = history.maxOfOrNull { it.downBps } ?: 0L
    val peakUp = history.maxOfOrNull { it.upBps } ?: 0L

    UsageScreenScaffold(
        hazeState = hazeState,
        title = stringResource(R.string.ext_network_traffic),
        navController = navController,
        helpRoute = NavigationRoute.NetworkTraffic.route,
    ) {
        // ── Kecepatan live ──────────────────────────────────────────────
        UsageCard {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatCell(value = formatSpeed(now.downBps), label = stringResource(R.string.net_traffic_download))
                StatCell(value = formatSpeed(now.upBps), label = stringResource(R.string.net_traffic_upload))
            }
            if (history.size > 2) {
                Spacer(Modifier.height(12.dp))
                val yMax = (peakDown.coerceAtLeast(1024L)).toFloat()
                SimpleLineChart(
                    points = history.mapIndexed { i, s -> i.toFloat() to s.downBps.toFloat() },
                    xMin = 0f,
                    xMax = (history.size - 1).toFloat().coerceAtLeast(1f),
                    yMin = 0f,
                    yMax = yMax,
                    yLabels = listOf(formatSpeedShort(yMax.toLong()), "0"),
                    xLabels = listOf("-${history.size}s", "0s"),
                    lineColor = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatCell(value = formatSpeed(peakDown), label = stringResource(R.string.net_traffic_peak_down))
                StatCell(value = formatSpeed(peakUp), label = stringResource(R.string.net_traffic_peak_up))
            }
        }

        // ── Sinyal & kecepatan link ─────────────────────────────────────
        UsageCard {
            val typeLabel = when (link.type) {
                LinkType.WIFI -> stringResource(R.string.net_traffic_type_wifi)
                LinkType.MOBILE -> stringResource(R.string.net_traffic_type_mobile)
                LinkType.OTHER -> stringResource(R.string.net_traffic_type_other)
                LinkType.NONE -> stringResource(R.string.net_traffic_type_none)
            }
            Text(
                stringResource(R.string.net_traffic_connection),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoLine(stringResource(R.string.net_traffic_type), typeLabel)
                if (link.type != LinkType.NONE) {
                    InfoLine(
                        stringResource(R.string.net_traffic_signal),
                        link.signalDbm?.let { "$it dBm  (${link.signalLevel ?: 0}/4)" }
                            ?: stringResource(R.string.net_traffic_unknown)
                    )
                    if (link.type == LinkType.WIFI) {
                        InfoLine(
                            stringResource(R.string.net_traffic_link_speed),
                            link.linkSpeedMbps?.let { "$it Mbps" } ?: stringResource(R.string.net_traffic_unknown)
                        )
                        link.frequencyMhz?.let {
                            val band = if (it >= 5925) "6 GHz" else if (it >= 4900) "5 GHz" else "2.4 GHz"
                            InfoLine(stringResource(R.string.net_traffic_band), "$band  ($it MHz)")
                        }
                    }
                    if (link.estDownKbps > 0) {
                        InfoLine(
                            stringResource(R.string.net_traffic_est_bandwidth),
                            "↓ ${String.format(Locale.US, "%.1f", link.estDownKbps / 1000f)} / " +
                                "↑ ${String.format(Locale.US, "%.1f", link.estUpKbps / 1000f)} Mbps"
                        )
                    }
                }
            }
        }

        // ── Total sejak boot ────────────────────────────────────────────
        UsageCard {
            Text(
                stringResource(R.string.net_traffic_since_boot),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            val wifiRx = (now.totalRx - now.mobileRx).coerceAtLeast(0)
            val wifiTx = (now.totalTx - now.mobileTx).coerceAtLeast(0)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoLine(
                    stringResource(R.string.net_traffic_type_wifi),
                    "↓ ${formatBytes(wifiRx)}  ↑ ${formatBytes(wifiTx)}"
                )
                InfoLine(
                    stringResource(R.string.net_traffic_type_mobile),
                    "↓ ${formatBytes(now.mobileRx)}  ↑ ${formatBytes(now.mobileTx)}"
                )
                InfoLine(
                    stringResource(R.string.net_traffic_total),
                    "↓ ${formatBytes(now.totalRx)}  ↑ ${formatBytes(now.totalTx)}"
                )
            }
        }
    }
}

@Composable
internal fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = usageSubColor())
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
