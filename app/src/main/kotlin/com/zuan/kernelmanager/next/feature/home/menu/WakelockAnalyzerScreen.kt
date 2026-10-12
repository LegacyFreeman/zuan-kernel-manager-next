/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.feature.home.menu

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.library.shell.PrivilegedAccess
import com.zuan.kernelmanager.next.library.shell.su
import com.zuan.kernelmanager.next.ui.navigation.NavigationRoute
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.zuan.kernelmanager.next.core.util.ZkmSamplerBus
import java.util.Locale

/** Satu wakelock hasil parse (kernel / partial / wakeup source / aktif sekarang). */
private data class WakelockEntry(
    val name: String,
    val owner: String,      // nama app / "kernel"
    val durationMs: Long,
    val count: Int,
)

private data class WakelockReport(
    val kernel: List<WakelockEntry> = emptyList(),
    val partial: List<WakelockEntry> = emptyList(),
    val wakeupSources: List<WakelockEntry> = emptyList(),
    val activeNow: List<WakelockEntry> = emptyList(),
    val dozeDeep: String? = null,
    val dozeLight: String? = null,
    val suspendOk: Int? = null,
    val suspendFail: Int? = null,
    val shellOk: Boolean = false,
    val loaded: Boolean = false,
)

private data class SleepInfo(val upMs: Long, val deepMs: Long) {
    val awakeMs get() = (upMs - deepMs).coerceAtLeast(0)
    val ratio get() = if (upMs > 0) deepMs.toFloat() / upMs else 0f
}

private fun readSleep(): SleepInfo {
    val up = SystemClock.elapsedRealtime()
    return SleepInfo(up, (up - SystemClock.uptimeMillis()).coerceAtLeast(0))
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    val d = s / 86400
    val h = (s % 86400) / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return when {
        d > 0 -> "${d}d ${h}h ${m}m"
        h > 0 -> "${h}h ${m}m ${sec}s"
        m > 0 -> "${m}m ${sec}s"
        s > 0 -> "${s}s"
        else -> "${ms}ms"
    }
}

/** "1d 2h 3m 4s 5ms" (format batterystats) -> ms. */
private fun parseDuration(text: String): Long {
    var total = 0L
    Regex("""(\d+)(d|h|ms|m|s)""").findAll(text).forEach {
        val v = it.groupValues[1].toLong()
        total += when (it.groupValues[2]) {
            "d" -> v * 86_400_000L
            "h" -> v * 3_600_000L
            "m" -> v * 60_000L
            "s" -> v * 1_000L
            else -> v
        }
    }
    return total
}

/** u0a123 -> 10123, "1000"/"u0s1000" -> system; resolusi ke label aplikasi. */
private fun resolveOwner(context: Context, raw: String): String {
    val uid = when {
        Regex("""u\d+a(\d+)""").matches(raw) ->
            Regex("""u\d+a(\d+)""").find(raw)!!.groupValues[1].toInt() + 10_000
        raw.toIntOrNull() != null -> raw.toInt()
        else -> return raw
    }
    if (uid == 1000) return "Android System"
    val pm = context.packageManager
    val pkg = runCatching { pm.getPackagesForUid(uid)?.firstOrNull() }.getOrNull() ?: return raw
    return runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
        .getOrDefault(pkg)
}

private fun parseBatteryStats(context: Context, text: String): Pair<List<WakelockEntry>, List<WakelockEntry>> {
    val kernel = ArrayList<WakelockEntry>()
    val partial = LinkedHashMap<String, WakelockEntry>()
    // Kernel wake lock <nama>: 1m 2s 3ms (5 times) realtime
    val kRe = Regex("""Kernel [Ww]ake ?lock (.+?): ([0-9dhms ]+?) \((\d+) times?\)""")
    // Wake lock u0a123 <tag>: 1m 2s (3 times) max=...
    val wRe = Regex("""^\s*Wake lock (\S+) (.+?): ([0-9dhms ]+?) \((\d+) times?\)""")
    text.lineSequence().forEach { line ->
        kRe.find(line)?.let {
            val ms = parseDuration(it.groupValues[2])
            if (ms > 0) kernel += WakelockEntry(it.groupValues[1].trim(), "kernel", ms, it.groupValues[3].toInt())
            return@forEach
        }
        wRe.find(line)?.let {
            val owner = resolveOwner(context, it.groupValues[1])
            val tag = it.groupValues[2].trim()
            val ms = parseDuration(it.groupValues[3])
            val cnt = it.groupValues[4].toInt()
            if (ms > 0) {
                val key = "$owner|$tag"
                val old = partial[key]
                partial[key] = if (old == null) WakelockEntry(tag, owner, ms, cnt)
                else old.copy(durationMs = old.durationMs + ms, count = old.count + cnt)
            }
        }
    }
    return kernel.sortedByDescending { it.durationMs } to partial.values.sortedByDescending { it.durationMs }
}

/** Wakelock yang sedang ditahan sekarang, dari `dumpsys power`. */
private fun parseActiveNow(context: Context, text: String): List<WakelockEntry> {
    // PARTIAL_WAKE_LOCK              'tag' ACQ=-5s123ms (uid=10123 pid=4567 ws=...)
    val re = Regex("""(PARTIAL_WAKE_LOCK|FULL_WAKE_LOCK|SCREEN_\w+_WAKE_LOCK|PROXIMITY\w*)\s+'(.*?)'.*?ACQ=(-?[0-9dhms]+).*?uid=(\d+)""")
    return text.lineSequence().mapNotNull { line ->
        val m = re.find(line) ?: return@mapNotNull null
        val ms = parseDuration(m.groupValues[3].removePrefix("-"))
        WakelockEntry(
            name = m.groupValues[2].ifBlank { m.groupValues[1] },
            owner = resolveOwner(context, m.groupValues[4]),
            durationMs = ms,
            count = 1,
        )
    }.distinctBy { it.owner + it.name }.sortedByDescending { it.durationMs }.toList()
}

/** /sys/kernel/wakeup_sources (butuh akses root; kalau tidak terbaca, dilewati). */
private fun parseWakeupSources(text: String): List<WakelockEntry> =
    text.lineSequence().drop(1).mapNotNull { line ->
        val c = line.trim().split(Regex("""\s+"""))
        if (c.size < 7) return@mapNotNull null
        val name = c[0]
        val activeCount = c[1].toIntOrNull() ?: return@mapNotNull null
        val totalTime = c[6].toLongOrNull() ?: return@mapNotNull null
        if (totalTime <= 0L) return@mapNotNull null
        WakelockEntry(name, "kernel", totalTime, activeCount)
    }.sortedByDescending { it.durationMs }.toList()

private suspend fun loadReport(context: Context): WakelockReport = withContext(Dispatchers.IO) {
    val stats = runCatching { su("dumpsys batterystats") }.getOrNull()
    val power = runCatching { su("dumpsys power") }.getOrNull()
    val wsCmd = "for p in /sys/kernel/wakeup_sources /sys/kernel/debug/wakeup_sources /d/wakeup_sources; do [ -f \"\$p\" ] && cat \"\$p\" && break; done"
    val ws = runCatching { su(wsCmd) }.getOrNull()
    val deep = runCatching { su("cmd deviceidle get deep") }.getOrNull()
    val light = runCatching { su("cmd deviceidle get light") }.getOrNull()
    val sOk = runCatching { su("cat /sys/power/suspend_stats/success 2>/dev/null") }.getOrNull()
    val sFail = runCatching { su("cat /sys/power/suspend_stats/fail 2>/dev/null") }.getOrNull()

    val shellOk = stats?.isSuccess == true && stats.outputString.isNotBlank()
    val (kernel, partial) = if (shellOk && stats != null) parseBatteryStats(context, stats.outputString) else emptyList<WakelockEntry>() to emptyList()
    WakelockReport(
        kernel = kernel.take(25),
        partial = partial.take(25),
        wakeupSources = ws?.takeIf { it.isSuccess && it.outputString.isNotBlank() }?.let { parseWakeupSources(it.outputString).take(25) } ?: emptyList(),
        activeNow = power?.takeIf { it.isSuccess }?.let { parseActiveNow(context, it.outputString).take(25) } ?: emptyList(),
        dozeDeep = deep?.takeIf { it.isSuccess }?.outputString?.takeIf { it.isNotBlank() },
        dozeLight = light?.takeIf { it.isSuccess }?.outputString?.takeIf { it.isNotBlank() },
        suspendOk = sOk?.takeIf { it.isSuccess }?.outputString?.trim()?.toIntOrNull(),
        suspendFail = sFail?.takeIf { it.isSuccess }?.outputString?.trim()?.toIntOrNull(),
        shellOk = shellOk,
        loaded = true,
    )
}

/** Wakelock & Deep Sleep Analyzer: ringkasan deep sleep + daftar wakelock lengkap (butuh Shizuku/Root). */
@Composable
fun WakelockAnalyzerScreen(navController: NavController, hazeState: HazeState) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }

    // Penyatuan loop liar (Zkm Perf Tahap 4):
    val sleep by produceState(initialValue = readSleep()) {
        ZkmSamplerBus.fastTick.collect { value = readSleep() }
    }
    val report by produceState(initialValue = WakelockReport(), refreshKey) {
        value = withContext(Dispatchers.IO) { runCatching { loadReport(context) }.getOrDefault(WakelockReport(loaded = true)) }
        ZkmSamplerBus.slowTick.collect {
            value = withContext(Dispatchers.IO) { runCatching { loadReport(context) }.getOrDefault(value) }
        }
    }

    UsageScreenScaffold(
        hazeState = hazeState,
        title = stringResource(R.string.ext_wakelock),
        navController = navController,
        helpRoute = NavigationRoute.WakelockAnalyzer.route,
        actions = {
            IconButton(onClick = { refreshKey++ }) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.wakelock_refresh))
            }
        }
    ) {
        // ── Deep sleep sejak boot ───────────────────────────────────────
        UsageCard {
            Text(
                String.format(Locale.US, "%.0f%%", sleep.ratio * 100f),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(stringResource(R.string.wakelock_deep_ratio), style = MaterialTheme.typography.bodySmall, color = usageSubColor())
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatCell(fmt(sleep.upMs), stringResource(R.string.wakelock_uptime))
                StatCell(fmt(sleep.awakeMs), stringResource(R.string.wakelock_awake))
                StatCell(fmt(sleep.deepMs), stringResource(R.string.wakelock_deep_sleep))
            }
        }

        // ── Status doze & suspend ───────────────────────────────────────
        if (report.dozeDeep != null || report.dozeLight != null || report.suspendOk != null) {
            UsageCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    report.dozeDeep?.let { InfoLine(stringResource(R.string.wakelock_doze_deep), it) }
                    report.dozeLight?.let { InfoLine(stringResource(R.string.wakelock_doze_light), it) }
                    report.suspendOk?.let {
                        InfoLine(stringResource(R.string.wakelock_suspend), "$it ok / ${report.suspendFail ?: 0} fail")
                    }
                }
            }
        }

        if (!report.loaded) {
            UsageCard { Text(stringResource(R.string.wakelock_loading), color = usageSubColor()) }
        } else if (!report.shellOk) {
            UsageCard {
                Text(
                    stringResource(R.string.wakelock_need_shell, PrivilegedAccess.label()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = usageSubColor()
                )
            }
        } else {
            WakelockSection(R.string.wakelock_active_now, report.activeNow)
            WakelockSection(R.string.wakelock_partial, report.partial)
            WakelockSection(R.string.wakelock_kernel, report.kernel)
            WakelockSection(R.string.wakelock_sources, report.wakeupSources)
        }
    }
}

@Composable
private fun WakelockSection(titleRes: Int, items: List<WakelockEntry>) {
    if (items.isEmpty()) return
    UsageCard {
        Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { e ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(e.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(e.owner, style = MaterialTheme.typography.labelSmall, color = usageSubColor(),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                        Text(fmt(e.durationMs), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text("${e.count}×", style = MaterialTheme.typography.labelSmall, color = usageSubColor())
                    }
                }
            }
        }
    }
}
