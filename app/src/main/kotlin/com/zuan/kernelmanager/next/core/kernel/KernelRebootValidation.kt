package com.zuan.kernelmanager.next.core.kernel

import android.content.Context
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject

/** Persists a reboot-validation marker and verifies it after BOOT_COMPLETED. */
object KernelRebootValidation {
    data class Expectation(val profileId: String?, val ruleId: String?, val nodePaths: List<String>, val deviceFingerprint: String)
    data class Report(
        val armed: Boolean,
        val bootRecorded: Boolean,
        val profileRestored: Boolean,
        val ruleRestored: Boolean,
        val sameDevice: Boolean,
        val passed: Boolean
    )

    private const val PREF = "zkm_reboot_validation_v1"
    private const val KEY_ARMED = "armed"
    private const val KEY_EXPECTATION = "expectation"
    private const val KEY_PREPARED_AT = "prepared_at"
    private const val KEY_BOOT_AT = "boot_at"

    fun arm(context: Context, profileId: String?, ruleId: String?, nodePaths: List<String>) {
        val safePaths = nodePaths.map(KernelNodePolicy::normalizeLegacyPath)
            .filter { KernelNodePolicy.isAllowed(it) }
            .distinct()
            .take(32)
        val o = JSONObject()
            .put("profileId", profileId ?: "")
            .put("ruleId", ruleId ?: "")
            .put("paths", JSONArray(safePaths))
            .put("deviceFingerprint", android.os.Build.FINGERPRINT)
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ARMED, true)
            .putString(KEY_EXPECTATION, o.toString())
            .putLong(KEY_PREPARED_AT, System.currentTimeMillis())
            .apply()
    }

    fun noteBoot(context: Context) {
        val bootContext = context.applicationContext.createDeviceProtectedStorageContext()
        bootContext.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putLong(KEY_BOOT_AT, System.currentTimeMillis())
            .apply()
    }

    fun readExpectation(context: Context): Expectation? = runCatching {
        val raw = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_EXPECTATION, null) ?: return null
        val o = JSONObject(raw)
        val a = o.optJSONArray("paths") ?: JSONArray()
        Expectation(
            o.optString("profileId").takeIf { it.isNotBlank() },
            o.optString("ruleId").takeIf { it.isNotBlank() },
            buildList { for (i in 0 until a.length()) add(a.optString(i)) },
            o.optString("deviceFingerprint")
        )
    }.getOrNull()

    suspend fun verify(context: Context): Report {
        val prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val armed = prefs.getBoolean(KEY_ARMED, false)
        val preparedAt = prefs.getLong(KEY_PREPARED_AT, 0L)
        val bootAt = context.applicationContext.createDeviceProtectedStorageContext()
            .getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong(KEY_BOOT_AT, 0L)
        val expectation = readExpectation(context)
        val status = KernelEngine.status(context.applicationContext)
        val profileOk = expectation?.profileId?.let { KernelCore.profiles.get(it) != null } ?: true
        val ruleOk = expectation?.ruleId?.let { KernelCore.automation.rules().any { r -> r.id == it } } ?: true
        val bootRecorded = bootAt > preparedAt && bootAt > 0L
        val sameDevice = expectation?.deviceFingerprint.isNullOrBlank() || expectation?.deviceFingerprint == android.os.Build.FINGERPRINT
        return Report(armed, bootRecorded, profileOk, ruleOk, sameDevice, armed && bootRecorded && profileOk && ruleOk && sameDevice && status.daemonConnected && status.stateHealthy)
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
