/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.core.kernel

import android.util.Log

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

/** Event-driven policy coordinator with persistent app/timing rules. */
class KernelAutomationEngine(
    private val profileEngine: KernelProfileEngine
) {
    enum class EventType { APP_FOREGROUND, APP_BACKGROUND, BATTERY, THERMAL, TIMED }

    data class Rule(
        val id: String,
        val event: EventType,
        val packageName: String? = null,
        val batteryBelow: Int? = null,
        val thermalAboveC: Float? = null,
        val profileId: String,
        val cooldownMs: Long = 2000L,
        var lastFiredAt: Long = 0L
    )

    data class TimedSchedule(
        val id: String,
        val triggerAtEpochMs: Long,
        val profileId: String,
        val enabled: Boolean = true
    )

    private val appProfiles = LinkedHashMap<String, String>()
    private val appRefreshRates = LinkedHashMap<String, Int>()
    private var defaultRefreshRate: Int? = null
    private val rules = LinkedHashMap<String, Rule>()
    private val timedSchedules = LinkedHashMap<String, TimedSchedule>()
    private var foregroundPackage = ""
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null
    private var persistenceContext: Context? = null
    private val timedJobs = LinkedHashMap<String, Job>()

    @Synchronized fun registerAppProfile(packageName: String, profileId: String) {
        require(packageName.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+"))) { "Invalid package name" }
        require(profileEngine.get(profileId) != null) { "Unknown profile" }
        appProfiles[packageName] = profileId
        persistLocked()
    }

    @Synchronized fun unregisterAppProfile(packageName: String) { appProfiles.remove(packageName); persistLocked() }
    @Synchronized fun profileFor(packageName: String): String? = appProfiles[packageName]
    @Synchronized fun appProfilesSnapshot(): Map<String, String> = appProfiles.toMap()

    @Synchronized fun registerAppRefreshRate(packageName: String, refreshRate: Int) {
        require(packageName.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+"))) { "Invalid package name" }
        require(refreshRate in 30..240) { "Invalid refresh rate" }
        appRefreshRates[packageName] = refreshRate
        persistLocked()
    }

    @Synchronized fun unregisterAppRefreshRate(packageName: String) {
        appRefreshRates.remove(packageName)
        persistLocked()
    }

    @Synchronized fun refreshRateFor(packageName: String): Int? = appRefreshRates[packageName]
    @Synchronized fun appRefreshRatesSnapshot(): Map<String, Int> = appRefreshRates.toMap()

    @Synchronized fun currentForegroundPackage(): String = foregroundPackage
    @Synchronized fun ruleCount(): Int = rules.size
    @Synchronized fun rules(): List<Rule> = rules.values.map { it.copy() }
    @Synchronized fun timedSchedules(): List<TimedSchedule> = timedSchedules.values.map { it.copy() }

    @Synchronized fun attachPersistence(context: Context) { persistenceContext = context.applicationContext }

    fun restorePersisted(context: Context) {
        val snapshot = KernelAutomationStore.load(context)
        synchronized(this) {
            appProfiles.clear()
            snapshot.appProfiles.filterValues { profileEngine.get(it) != null }.forEach { (pkg, profile) -> appProfiles[pkg] = profile }
            appRefreshRates.clear()
            snapshot.appRefreshRates.forEach { (pkg, hz) -> appRefreshRates[pkg] = hz }
            rules.clear()
            snapshot.rules.filter { profileEngine.get(it.profileId) != null }.forEach { rules[it.id] = it }
            timedSchedules.clear()
            snapshot.timedSchedules.filter { profileEngine.get(it.profileId) != null && it.enabled }.forEach { timedSchedules[it.id] = it }
        }
        reschedulePersisted(context.applicationContext)
    }

    @Synchronized fun registerRule(rule: Rule) {
        require(rule.id.isNotBlank()) { "Rule id must not be blank" }
        require(rule.profileId.isNotBlank()) { "Rule profile must not be blank" }
        require(profileEngine.get(rule.profileId) != null) { "Unknown profile" }
        rules[rule.id] = rule.copy(lastFiredAt = 0L, cooldownMs = rule.cooldownMs.coerceAtLeast(250L))
        persistLocked()
    }

    @Synchronized fun unregisterRule(id: String) { rules.remove(id); persistLocked() }

    fun scheduleTimedProfile(context: Context, scheduleId: String, delayMs: Long, profileId: String): Boolean {
        if (scheduleId.isBlank() || delayMs < 0L || profileEngine.get(profileId) == null) return false
        val target = System.currentTimeMillis() + delayMs.coerceAtMost(30L * 24L * 60L * 60L * 1000L)
        synchronized(this) {
            timedJobs[scheduleId]?.cancel()
            timedSchedules[scheduleId] = TimedSchedule(scheduleId, target, profileId, true)
            persistLocked()
        }
        scheduleAlarm(context.applicationContext, timedSchedules[scheduleId] ?: return false)
        return true
    }

    fun cancelTimedProfile(scheduleId: String) {
        synchronized(this) { timedJobs.remove(scheduleId)?.cancel(); timedSchedules.remove(scheduleId); persistLocked() }
        persistenceContext?.let { cancelAlarm(it, scheduleId) }
    }

    suspend fun fireTimedSchedule(context: Context, scheduleId: String): Boolean = withContext(Dispatchers.IO) {
        val schedule = synchronized(this@KernelAutomationEngine) { timedSchedules[scheduleId]?.copy() } ?: return@withContext false
        if (!schedule.enabled || schedule.triggerAtEpochMs > System.currentTimeMillis() + 2000L) return@withContext false
        val result = runCatching { profileEngine.apply(context.applicationContext, schedule.profileId) }.getOrNull()
        synchronized(this@KernelAutomationEngine) {
            timedSchedules.remove(scheduleId)
            persistLocked()
        }
        cancelAlarm(context.applicationContext, scheduleId)
        result?.failed == null
    }

    private fun reschedulePersisted(context: Context) {
        val current = synchronized(this) { timedSchedules.values.map { it.copy() } }
        current.forEach { scheduleAlarm(context, it) }
    }

    private fun scheduleAlarm(context: Context, schedule: TimedSchedule) {
        if (!schedule.enabled) return
        val delayMs = (schedule.triggerAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, com.zuan.kernelmanager.next.platform.receiver.KernelAutomationReceiver::class.java)
            .putExtra(com.zuan.kernelmanager.next.platform.receiver.KernelAutomationReceiver.EXTRA_SCHEDULE_ID, schedule.id)
        val pending = PendingIntent.getBroadcast(
            context,
            schedule.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, android.os.SystemClock.elapsedRealtime() + delayMs, pending)
    }

    private fun cancelAlarm(context: Context, scheduleId: String) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, com.zuan.kernelmanager.next.platform.receiver.KernelAutomationReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, scheduleId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarm.cancel(pending)
        pending.cancel()
    }

    suspend fun onForegroundPackageChanged(context: Context, packageName: String) = withContext(Dispatchers.IO) {
        val normalized = packageName.trim()
        val previous: String
        synchronized(this@KernelAutomationEngine) {
            if (normalized == foregroundPackage) return@withContext
            previous = foregroundPackage
            foregroundPackage = normalized
        }
        if (previous.isNotBlank()) fireEvent(context.applicationContext, EventType.APP_BACKGROUND, previous)
        if (normalized.isNotBlank()) fireEvent(context.applicationContext, EventType.APP_FOREGROUND, normalized)

        val profileId = synchronized(this@KernelAutomationEngine) { appProfiles[normalized] }
        if (profileId != null) {
            val result = profileEngine.apply(context.applicationContext, profileId)
            if (result.failed != null) Log.w("ZKM-Automation", "Profile ${profileId} failed: ${result.failed}")
        } else if (previous.isNotBlank()) profileEngine.restoreActive()

        // Per-App Dynamic Refresh Rate (DFPS)
        val targetHz = synchronized(this@KernelAutomationEngine) { appRefreshRates[normalized] }
        if (targetHz != null) {
            if (defaultRefreshRate == null) {
                defaultRefreshRate = runCatching {
                    com.zuan.kernelmanager.next.feature.home.menu.DisplayUtils.getCurrentRefreshRate(context.applicationContext)
                }.getOrDefault(60)
            }
            runCatching {
                com.zuan.kernelmanager.next.feature.home.menu.DisplayUtils.setRefreshRate(targetHz)
            }
        } else if (previous.isNotBlank() && defaultRefreshRate != null) {
            val restoreHz = defaultRefreshRate!!
            defaultRefreshRate = null
            runCatching {
                com.zuan.kernelmanager.next.feature.home.menu.DisplayUtils.setRefreshRate(restoreHz)
            }
        }
    }

    suspend fun onBattery(context: Context, percent: Int) {
        fireEvent(context.applicationContext, EventType.BATTERY, null, batteryPercent = percent.coerceIn(0, 100))
    }

    suspend fun onThermal(context: Context, tempC: Float) {
        fireEvent(context.applicationContext, EventType.THERMAL, null, thermalC = tempC)
    }

    private suspend fun fireEvent(
        context: Context,
        type: EventType,
        packageName: String?,
        batteryPercent: Int? = null,
        thermalC: Float? = null
    ) {
        val candidates = synchronized(this) { rules.values.map { it.copy() } }
        val now = System.currentTimeMillis()
        val decisions = KernelAutomationV2.evaluate(
            candidates,
            KernelAutomationV2.Input(
                event = type,
                packageName = packageName,
                battery = batteryPercent,
                thermalC = thermalC,
                now = now
            )
        )
        decisions.filter { it.matched }.forEach { decision ->
            val current = synchronized(this) { rules[decision.ruleId] } ?: return@forEach
            synchronized(this) { rules[decision.ruleId]?.lastFiredAt = now }
            if (profileEngine.get(current.profileId) != null) {
                val result = profileEngine.apply(context.applicationContext, current.profileId)
                if (result.failed != null) {
                    Log.w("ZKM-Automation", "Rule ${current.id} failed: ${result.failed}")
                }
            }
        }
    }

    fun startPolling(context: Context, intervalMs: Long = 1500L) {
        if (monitorJob?.isActive == true) return
        val appContext = context.applicationContext
        monitorJob = scope.launch {
            while (isActive) {
                val packageName = runCatching { resolveForegroundPackage() }.getOrDefault("")
                if (packageName.isNotEmpty()) runCatching { onForegroundPackageChanged(appContext, packageName) }
                delay(intervalMs.coerceAtLeast(1000L))
            }
        }
    }

    fun stopPolling() {
        monitorJob?.cancel()
        monitorJob = null
    }

    private suspend fun resolveForegroundPackage(): String = withContext(Dispatchers.IO) {
        val output = KernelRootRuntime.execute("cmd activity get-top-activity 2>/dev/null").outputString
        if (output.isBlank()) return@withContext ""
        val matcher = Pattern.compile("([A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)/").matcher(output)
        while (matcher.find()) {
            val pkg = matcher.group(1).orEmpty()
            if (pkg != "android" && pkg != "com.android.systemui") return@withContext pkg
        }
        ""
    }

    @Synchronized
    private fun persistLocked() {
        persistenceContext?.let { KernelAutomationStore.save(it, this) }
    }
}
