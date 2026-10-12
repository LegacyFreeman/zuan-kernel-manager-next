package com.zuan.kernelmanager.next.feature.dashboard.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.zuan.kernelmanager.next.R

/** Localizes dashboard state values whose internal representation stays stable in English. */
@Composable
fun dashboardLocalized(raw: String): String = when (raw) {
    "HIGH LOAD" -> stringResource(R.string.lp_overall_high_load)
    "ACTIVE" -> stringResource(R.string.lp_overall_active)
    "BALANCED" -> stringResource(R.string.lp_overall_balanced)
    "IDLE" -> stringResource(R.string.lp_overall_idle)
    "HIGH DRAW" -> stringResource(R.string.lp_overall_high_draw)
    "STABLE" -> stringResource(R.string.lp_overall_stable)
    "THROTTLE RISK" -> stringResource(R.string.lp_overall_throttle_risk)
    "READY" -> stringResource(R.string.lp_overall_ready)
    "ATTENTION" -> stringResource(R.string.lp_overall_attention)
    "THERMAL" -> stringResource(R.string.lp_overall_thermal)
    "PERFORMANCE" -> stringResource(R.string.lp_overall_performance)
    "POWER" -> stringResource(R.string.lp_overall_power)
    "NORMAL" -> stringResource(R.string.lp_overall_normal)
    "HEALTHY" -> stringResource(R.string.lp_overall_healthy)
    "CAUTION" -> stringResource(R.string.lp_overall_caution)
    "Unknown", "UNKNOWN" -> stringResource(R.string.lp_overall_unknown)
    "Charging" -> stringResource(R.string.lp_overall_charging)
    "Discharging" -> stringResource(R.string.lp_overall_discharging)
    "Full" -> stringResource(R.string.lp_overall_full)
    "Not charging" -> stringResource(R.string.lp_overall_not_charging)
    else -> raw
}

@Composable
fun dashboardProfileLocalized(raw: String): String = when (raw) {
    "Cool Down" -> stringResource(R.string.lp_overall_cool_down)
    "Performance" -> stringResource(R.string.lp_overall_profile_performance)
    "Active Power" -> stringResource(R.string.lp_overall_active_power)
    "Balanced" -> stringResource(R.string.lp_overall_profile_balanced)
    "Thermal headroom is reduced" -> stringResource(R.string.lp_overall_thermal_headroom_reason)
    "Compute activity is high" -> stringResource(R.string.lp_overall_compute_activity_high)
    "Battery power demand is elevated" -> stringResource(R.string.lp_overall_battery_demand_elevated)
    "Reduce sustained load" -> stringResource(R.string.lp_overall_reduce_sustained_load)
    "Keep performance telemetry visible" -> stringResource(R.string.lp_overall_keep_performance_visible)
    "Monitor power draw" -> stringResource(R.string.lp_overall_monitor_power_draw)
    "No immediate action" -> stringResource(R.string.lp_overall_no_immediate_action)
    else -> raw
}
