package com.zuan.kernelmanager.next.feature.dashboard.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zuan.kernelmanager.next.feature.dashboard.DashboardState
import com.zuan.kernelmanager.next.R
import androidx.compose.ui.res.stringResource
import java.util.Locale

@Composable
fun StorageIntelligenceWidget(state: DashboardState, modifier: Modifier, containerColor: Color, textColor: Color, subTextColor: Color, onClick: () -> Unit = {}) {
    val storage = state.storageIntelligence
    OSWidgetContainer(modifier.clickable(onClick = onClick), containerColor) {
        IntelligenceHeader(Icons.Rounded.Storage, stringResource(R.string.lp_overall_storage_health), textColor, ColorBrandPrimary)
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.lp_overall_used_percent, "%.0f%%".format(Locale.US, storage.usagePercent * 100f)), style = MaterialTheme.typography.headlineMedium, color = textColor)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.lp_overall_storage_summary, formatBytes(storage.usedBytes), formatBytes(storage.totalBytes), formatBytes(storage.availableBytes)), style = MaterialTheme.typography.bodyMedium, color = subTextColor)
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(progress = { storage.usagePercent.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(9.dp), color = ColorBrandPrimary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(14.dp))
        IntelligenceDivider(subTextColor)
        Spacer(Modifier.height(12.dp))
        IntelligenceMetricRow(stringResource(R.string.lp_overall_filesystem_block_pressure), "%.0f%%".format(Locale.US, storage.inodePercent * 100f), textColor, subTextColor)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes >= 1073741824L -> "%.1f GB".format(Locale.US, bytes / 1073741824.0)
    else -> "%.0f MB".format(Locale.US, bytes / 1048576.0)
}
