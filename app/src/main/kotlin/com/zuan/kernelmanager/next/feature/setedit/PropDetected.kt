package com.zuan.kernelmanager.next.feature.setedit

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dangerous
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.library.shell.su
import com.zuan.kernelmanager.next.ui.components.zkmOnCustomBackgroundContentColor
import com.zuan.kernelmanager.next.ui.components.zkmReferenceGlass
import com.zuan.kernelmanager.next.ui.components.zkmReferenceGlassFill
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** Hasil perbandingan baseline vs kondisi sekarang. */
enum class PropStatus { SAFE, WARNING, DANGER }

data class PropChange(val key: String, val oldValue: String?, val newValue: String?, val critical: Boolean)

data class PropDetectResult(
    val missing: List<PropChange>,
    val changed: List<PropChange>,
    val added: List<PropChange>,
    val baselineTime: Long,
    val baselineCount: Int
) {
    val status: PropStatus
        get() = when {
            (missing + changed).any { it.critical } -> PropStatus.DANGER
            missing.isNotEmpty() || changed.isNotEmpty() || added.isNotEmpty() -> PropStatus.WARNING
            else -> PropStatus.SAFE
        }
    val total: Int get() = missing.size + changed.size + added.size
}

/**
 * Penyimpanan lokal untuk fitur Prop Detected.
 * Semua data hanya ditulis ke penyimpanan privat aplikasi (filesDir + SharedPreferences)
 * dan tidak pernah dikirim keluar perangkat.
 */
class PropDetectedStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("zkm_prop_detected", Context.MODE_PRIVATE)
    private val baselineFile = File(appContext.filesDir, "prop_detected_baseline.json")

    fun hasConsent(): Boolean = prefs.getBoolean(KEY_CONSENT, false) && baselineFile.exists()

    fun hasConsentFlag(): Boolean = prefs.getBoolean(KEY_CONSENT, false)

    private fun setConsent(value: Boolean) { prefs.edit().putBoolean(KEY_CONSENT, value).apply() }

    /** Baca semua prop saat ini lewat getprop (tanpa nilai volatil). */
    suspend fun readCurrentProps(): Map<String, String>? = withContext(Dispatchers.IO) {
        val result = su("getprop")
        if (!result.isSuccess) return@withContext null
        parseGetprop(result.out)
    }

    /** Simpan snapshot sebagai baseline + catat persetujuan. */
    suspend fun saveBaseline(props: Map<String, String>) = withContext(Dispatchers.IO) {
        val json = JSONObject()
        json.put("time", System.currentTimeMillis())
        json.put("props", JSONObject(props as Map<*, *>))
        baselineFile.writeText(json.toString())
        setConsent(true)
    }

    /** Hapus baseline dan cabut persetujuan. */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        baselineFile.delete()
        setConsent(false)
    }

    suspend fun compare(current: Map<String, String>): PropDetectResult? = withContext(Dispatchers.IO) {
        if (!baselineFile.exists()) return@withContext null
        val root = runCatching { JSONObject(baselineFile.readText()) }.getOrNull() ?: return@withContext null
        val time = root.optLong("time", 0L)
        val obj = root.optJSONObject("props") ?: return@withContext null
        val baseline = HashMap<String, String>(obj.length())
        obj.keys().forEach { k -> baseline[k] = obj.optString(k) }

        val missing = ArrayList<PropChange>()
        val changed = ArrayList<PropChange>()
        val added = ArrayList<PropChange>()
        baseline.forEach { (k, v) ->
            val now = current[k]
            if (now == null) missing += PropChange(k, v, null, isCritical(k))
            else if (now != v) changed += PropChange(k, v, now, isCritical(k))
        }
        current.forEach { (k, v) ->
            if (!baseline.containsKey(k)) added += PropChange(k, null, v, isCritical(k))
        }
        PropDetectResult(
            missing = missing.sortedBy { it.key },
            changed = changed.sortedBy { it.key },
            added = added.sortedBy { it.key },
            baselineTime = time,
            baselineCount = baseline.size
        )
    }

    companion object {
        private const val KEY_CONSENT = "consent"

        // Prop yang berubah terus saat runtime; diabaikan agar tidak muncul false positive.
        private val VOLATILE_PREFIXES = listOf(
            "sys.", "init.svc.", "init.svc_debug_pid.", "dev.", "debug.", "vendor.debug.",
            "net.", "dhcp.", "gsm.", "wifi.", "wlan.", "ril.", "vold.", "vendor.sys.",
            "service.bootanim", "ro.boottime.", "ro.boot.bootreason", "ro.bootreason",
            "ro.runtime.firstboot", "persist.sys.boot", "apex.", "ctl.", "ro.boot.boot_devices",
            "vendor.gsm.", "vendor.ril.", "persist.vendor.debug.", "ro.boot.bootdevice_time"
        )

        // Prop penting: hilang/berubah = Danger.
        private val CRITICAL_PREFIXES = listOf(
            "ro.product.", "ro.build.", "ro.hardware", "ro.board.", "ro.soc.", "ro.boot.",
            "ro.bootimage.", "ro.vendor.build.", "ro.vendor.product.", "ro.system.build.",
            "ro.system_ext.build.", "ro.odm.build.", "ro.secure", "ro.debuggable", "ro.adb.secure",
            "ro.kernel.", "ro.treble.", "ro.crypto.", "ro.apex.updatable", "ro.vndk."
        )

        fun isCritical(key: String): Boolean = CRITICAL_PREFIXES.any { key.startsWith(it) }

        private fun isVolatile(key: String): Boolean = VOLATILE_PREFIXES.any { key.startsWith(it) }

        fun parseGetprop(lines: List<String>): Map<String, String> {
            val map = LinkedHashMap<String, String>()
            lines.forEach { line ->
                val parts = line.split("]: [")
                if (parts.size == 2) {
                    val key = parts[0].removePrefix("[").trim()
                    val value = parts[1].removeSuffix("]").trim()
                    if (key.isNotEmpty() && !isVolatile(key)) map[key] = value
                }
            }
            return map
        }
    }
}

@Composable
fun PropDetectedPage(
    hasConsent: Boolean,
    isLoading: Boolean,
    result: PropDetectResult?,
    query: String,
    topPadding: androidx.compose.ui.unit.Dp,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    primaryColor: Color,
    onRequestConsent: () -> Unit,
    onRescan: () -> Unit,
    onResetBaseline: () -> Unit,
    onDeleteData: () -> Unit
) {
    val onCard = if (isGlassActive || cardColor.alpha < 1f) zkmOnCustomBackgroundContentColor(zkmIsDarkTheme()) else MaterialTheme.colorScheme.onSurface
    val onCardSub = onCard.copy(alpha = 0.7f)
    val shape = RoundedCornerShape(20.dp)
    val glassModifier = if (isGlassActive) Modifier.zkmReferenceGlass(hazeState, shape) else Modifier.clip(shape)
    val fill = if (isGlassActive) zkmReferenceGlassFill() else cardColor

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding + 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!hasConsent) {
            item {
                Card(shape = shape, colors = CardDefaults.cardColors(containerColor = fill), modifier = Modifier.fillMaxWidth().then(glassModifier)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.Shield, null, tint = primaryColor, modifier = Modifier.size(36.dp))
                        Text(stringResource(R.string.propdet_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = onCard)
                        Text(stringResource(R.string.propdet_need_consent), style = MaterialTheme.typography.bodyMedium, color = onCardSub)
                        Button(onClick = onRequestConsent, shape = CircleShape) { Text(stringResource(R.string.propdet_review_consent)) }
                    }
                }
            }
            return@LazyColumn
        }

        if (isLoading && result == null) {
            item { Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = primaryColor) } }
            return@LazyColumn
        }
        if (result == null) {
            item {
                Card(shape = shape, colors = CardDefaults.cardColors(containerColor = fill), modifier = Modifier.fillMaxWidth().then(glassModifier)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.propdet_scan_failed), style = MaterialTheme.typography.bodyMedium, color = onCard)
                        OutlinedButton(onClick = onRescan, shape = CircleShape) { Text(stringResource(R.string.propdet_rescan)) }
                    }
                }
            }
            return@LazyColumn
        }

        val statusColor = when (result.status) {
            PropStatus.SAFE -> Color(0xFF22C55E)
            PropStatus.WARNING -> Color(0xFFF59E0B)
            PropStatus.DANGER -> Color(0xFFEF4444)
        }
        val statusIcon = when (result.status) {
            PropStatus.SAFE -> Icons.Rounded.CheckCircle
            PropStatus.WARNING -> Icons.Rounded.Warning
            PropStatus.DANGER -> Icons.Rounded.Dangerous
        }
        val statusTitle = when (result.status) {
            PropStatus.SAFE -> R.string.propdet_status_safe
            PropStatus.WARNING -> R.string.propdet_status_warning
            PropStatus.DANGER -> R.string.propdet_status_danger
        }
        val statusDesc = when (result.status) {
            PropStatus.SAFE -> R.string.propdet_status_safe_desc
            PropStatus.WARNING -> R.string.propdet_status_warning_desc
            PropStatus.DANGER -> R.string.propdet_status_danger_desc
        }

        item {
            Card(shape = shape, colors = CardDefaults.cardColors(containerColor = fill), modifier = Modifier.fillMaxWidth().then(glassModifier)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = statusColor.copy(alpha = 0.18f), shape = CircleShape, modifier = Modifier.size(52.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(statusIcon, null, tint = statusColor, modifier = Modifier.size(30.dp)) }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(statusTitle), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = statusColor)
                            Text(stringResource(statusDesc), style = MaterialTheme.typography.bodySmall, color = onCardSub)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        CountChip(stringResource(R.string.propdet_missing), result.missing.size, Color(0xFFEF4444), Modifier.weight(1f))
                        CountChip(stringResource(R.string.propdet_changed), result.changed.size, Color(0xFFF59E0B), Modifier.weight(1f))
                        CountChip(stringResource(R.string.propdet_added), result.added.size, Color(0xFF3B82F6), Modifier.weight(1f))
                    }
                    Text(
                        stringResource(R.string.propdet_baseline_info, result.baselineCount, formatTime(result.baselineTime)),
                        style = MaterialTheme.typography.labelSmall, color = onCardSub
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRescan, shape = CircleShape, enabled = !isLoading) {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.propdet_rescan))
                        }
                        OutlinedButton(onClick = onResetBaseline, shape = CircleShape) { Text(stringResource(R.string.propdet_reset_baseline)) }
                    }
                    OutlinedButton(onClick = onDeleteData, shape = CircleShape) { Text(stringResource(R.string.propdet_delete_data)) }
                }
            }
        }

        fun matches(c: PropChange) = query.isBlank() ||
            c.key.contains(query, ignoreCase = true) ||
            (c.oldValue?.contains(query, ignoreCase = true) == true) ||
            (c.newValue?.contains(query, ignoreCase = true) == true)

        val sections = listOf(
            Triple(R.string.propdet_missing, result.missing.filter(::matches), Color(0xFFEF4444)),
            Triple(R.string.propdet_changed, result.changed.filter(::matches), Color(0xFFF59E0B)),
            Triple(R.string.propdet_added, result.added.filter(::matches), Color(0xFF3B82F6))
        )
        sections.forEach { (titleRes, list, color) ->
            if (list.isNotEmpty()) {
                item(key = "hdr_$titleRes") {
                    Text("${stringResource(titleRes)} (${list.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
                }
                items(list, key = { "${titleRes}_${it.key}" }) { change ->
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = fill), modifier = Modifier.fillMaxWidth().then(if (isGlassActive) Modifier.zkmReferenceGlass(hazeState, RoundedCornerShape(16.dp)) else Modifier.clip(RoundedCornerShape(16.dp)))) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(change.key, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = onCard, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                if (change.critical) {
                                    Spacer(Modifier.width(6.dp))
                                    Icon(Icons.Rounded.Warning, null, tint = Color(0xFFFF6B35), modifier = Modifier.size(16.dp))
                                }
                            }
                            change.oldValue?.let { ValueLine("−", it, Color(0xFFEF4444), onCardSub) }
                            change.newValue?.let { ValueLine("+", it, Color(0xFF22C55E), onCardSub) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CountChip(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$count", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.9f), maxLines = 1)
        }
    }
}

@Composable
private fun ValueLine(sign: String, value: String, signColor: Color, textColor: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Text(sign, color = signColor, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Spacer(Modifier.width(6.dp))
        Text(value.ifEmpty { "(empty)" }, color = textColor, fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}

private fun formatTime(ms: Long): String =
    if (ms <= 0L) "-" else java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ms))
