/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.core.kernel

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persistent declarative automation configuration. */
object KernelAutomationStore {
    private const val PREF = "zkm_kernel_automation_v2"
    private const val APPS = "app_profiles"
    private const val RULES = "rules"
    private const val TIMED = "timed_schedules"
    private const val REFRESH_RATES = "app_refresh_rates"
    private const val MIGRATED = "schema_migrated"

    data class Snapshot(
        val appProfiles: Map<String, String> = emptyMap(),
        val appRefreshRates: Map<String, Int> = emptyMap(),
        val rules: List<KernelAutomationEngine.Rule> = emptyList(),
        val timedSchedules: List<KernelAutomationEngine.TimedSchedule> = emptyList()
    )

    fun save(context: Context, engine: KernelAutomationEngine) {
        val prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val apps = JSONArray()
        engine.appProfilesSnapshot().forEach { (packageName, profileId) ->
            apps.put(JSONObject().put("package", packageName).put("profile", profileId))
        }
        val rates = JSONArray()
        engine.appRefreshRatesSnapshot().forEach { (packageName, rate) ->
            rates.put(JSONObject().put("package", packageName).put("refreshRate", rate))
        }
        val rules = JSONArray()
        engine.rules().forEach { rule ->
            rules.put(
                JSONObject()
                    .put("id", rule.id)
                    .put("event", rule.event.name)
                    .putOpt("package", rule.packageName)
                    .putOpt("batteryBelow", rule.batteryBelow)
                    .putOpt("thermalAboveC", rule.thermalAboveC)
                    .put("profile", rule.profileId)
                    .put("cooldownMs", rule.cooldownMs.coerceAtLeast(250L))
            )
        }
        val timed = JSONArray()
        engine.timedSchedules().forEach { schedule ->
            timed.put(
                JSONObject()
                    .put("id", schedule.id)
                    .put("triggerAtEpochMs", schedule.triggerAtEpochMs)
                    .put("profile", schedule.profileId)
                    .put("enabled", schedule.enabled)
            )
        }
        prefs.edit()
            .putString(APPS, apps.toString())
            .putString(REFRESH_RATES, rates.toString())
            .putString(RULES, rules.toString())
            .putString(TIMED, timed.toString())
            .apply()
    }

    fun load(context: Context): Snapshot {
        val prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(MIGRATED, false)) prefs.edit().putBoolean(MIGRATED, true).apply()
        val appsRaw = prefs.getString(APPS, null)
        val ratesRaw = prefs.getString(REFRESH_RATES, null)
        val rulesRaw = prefs.getString(RULES, null)
        val timedRaw = prefs.getString(TIMED, null)
        val apps = linkedMapOf<String, String>()
        if (!appsRaw.isNullOrBlank()) runCatching {
            val array = JSONArray(appsRaw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val pkg = item.optString("package")
                val profile = item.optString("profile")
                if (pkg.isNotBlank() && profile.isNotBlank()) apps[pkg] = profile
            }
        }
        val appRates = linkedMapOf<String, Int>()
        if (!ratesRaw.isNullOrBlank()) runCatching {
            val array = JSONArray(ratesRaw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val pkg = item.optString("package")
                val rate = item.optInt("refreshRate", 0)
                if (pkg.isNotBlank() && rate in 30..240) appRates[pkg] = rate
            }
        }
        val rules = buildList {
            if (!rulesRaw.isNullOrBlank()) runCatching {
                val array = JSONArray(rulesRaw)
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val id = item.optString("id")
                    val profile = item.optString("profile")
                    val event = runCatching { KernelAutomationEngine.EventType.valueOf(item.optString("event")) }.getOrNull()
                    if (id.isBlank() || profile.isBlank() || event == null) continue
                    add(
                        KernelAutomationEngine.Rule(
                            id = id,
                            event = event,
                            packageName = item.optString("package").takeIf { it.isNotBlank() },
                            batteryBelow = if (item.has("batteryBelow") && !item.isNull("batteryBelow")) item.optInt("batteryBelow").coerceIn(0, 100) else null,
                            thermalAboveC = if (item.has("thermalAboveC") && !item.isNull("thermalAboveC")) item.optDouble("thermalAboveC").toFloat() else null,
                            profileId = profile,
                            cooldownMs = item.optLong("cooldownMs", 2000L).coerceAtLeast(250L)
                        )
                    )
                }
            }
        }
        val timed = buildList {
            if (!timedRaw.isNullOrBlank()) runCatching {
                val array = JSONArray(timedRaw)
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val id = item.optString("id")
                    val profile = item.optString("profile")
                    val triggerAt = item.optLong("triggerAtEpochMs", 0L)
                    if (id.isBlank() || profile.isBlank() || triggerAt <= 0L) continue
                    add(KernelAutomationEngine.TimedSchedule(id, triggerAt, profile, item.optBoolean("enabled", true)))
                }
            }
        }
        return Snapshot(apps, appRates, rules, timed)
    }
}
