package com.zuan.kernelmanager.next.feature.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.feature.dashboard.DashboardState
import java.util.Locale

@Composable
fun UfsHealthWidget(
    state: DashboardState,
    modifier: Modifier,
    containerColor: Color,
    textColor: Color,
    subTextColor: Color,
    onClick: () -> Unit = {}
) {
    val ufs = state.ufsHealth
    val supported = ufs.healthSupported && ufs.healthPercent != null
    val statusColor = when {
        !supported -> subTextColor
        ufs.status == "HEALTHY" -> MaterialTheme.colorScheme.primary
        ufs.status == "CAUTION" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    val tempText = ufs.temperatureC?.let { "%.1f °C".format(Locale.US, it) } ?: "—"

    OSWidgetContainer(modifier.clickable(onClick = onClick), containerColor) {
        IntelligenceHeader(
            icon = Icons.Rounded.Storage,
            title = if (ufs.storageType == "UFS") stringResource(R.string.lp_overall_ufs_health) else stringResource(R.string.lp_overall_storage_health),
            textColor = textColor,
            iconColor = ColorBrandPrimary,
            trailing = {
                Surface(
                    color = statusColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        if (ufs.storageType == "Unknown") stringResource(R.string.lp_overall_unknown_upper) else ufs.storageType,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
            }
        )

        Spacer(Modifier.height(14.dp))

        if (supported) {
            Text(
                "${ufs.healthPercent}%",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Spacer(Modifier.height(4.dp))
            // JEDEC reports wear in 10% buckets, so the % above is the bucket midpoint (0x04 = 60-70% left).
            val pct = ufs.healthPercent ?: 0
            val bucketRange = if (pct in 5..95) "  (${pct - 5}–${pct + 5}%)" else ""
            Text(
                stringResource(R.string.lp_overall_ufs_estimated_health) + bucketRange,
                style = MaterialTheme.typography.bodyMedium,
                color = subTextColor
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { (ufs.healthPercent ?: 0).coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(5.dp)),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            UfsStatPanel(
                subTextColor = subTextColor,
                items = listOf(
                    stringResource(R.string.lp_overall_lifetime_a) to ufs.lifetimeA,
                    stringResource(R.string.lp_overall_lifetime_b) to ufs.lifetimeB,
                    stringResource(R.string.lp_overall_health_state) to dashboardLocalized(ufs.status)
                ),
                textColor = textColor
            )
            Spacer(Modifier.height(10.dp))
            UfsStatPanel(
                subTextColor = subTextColor,
                items = listOf(
                    stringResource(R.string.lp_overall_storage_type) to ufs.storageType,
                    stringResource(R.string.lp_overall_storage_temperature) to tempText
                ),
                textColor = textColor
            )
        } else {
            Text(
                stringResource(R.string.lp_overall_unknown),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Spacer(Modifier.height(5.dp))
            val detail = when (ufs.storageType) {
                "eMMC" -> stringResource(R.string.lp_overall_ufs_emmc_detected)
                "UFS" -> stringResource(R.string.lp_overall_ufs_kernel_descriptor)
                else -> stringResource(R.string.lp_overall_storage_descriptor_unknown)
            }
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = subTextColor)
            Spacer(Modifier.height(14.dp))
            UfsStatPanel(
                subTextColor = subTextColor,
                textColor = textColor,
                items = listOf(
                    stringResource(R.string.lp_overall_storage_type) to ufs.storageType,
                    stringResource(R.string.lp_overall_health_support) to stringResource(R.string.lp_overall_not_available),
                    stringResource(R.string.lp_overall_storage_temperature) to tempText
                )
            )
        }
    }
}

/** Label-over-value columns inside a soft rounded panel (matches the dashboard stat chips). */
@Composable
private fun UfsStatPanel(
    items: List<Pair<String, String>>,
    textColor: Color,
    subTextColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(subTextColor.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        items.forEach { (label, value) ->
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = subTextColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
