package com.zuan.kernelmanager.next.core.util

import android.content.Context
import android.content.res.Resources
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

/**
 * Runtime language overrides for Zuan Kernel Manager.
 *
 * The pack uses Android string resource entry names as stable keys, so a JSON
 * exported from one build can be edited without knowing resource integer IDs.
 */
object LanguagePackManager {
    private const val PREFS = "language_pack"
    private const val OVERRIDES_KEY = "overrides_json"
    private const val FORMAT = "ZKM_LANGUAGE_PACK"
    private const val VERSION = 1

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun knownStringIds(): Map<String, Int> {
        val result = LinkedHashMap<String, Int>()
        fun addResourceClass(resourceClass: Class<*>) {
            resourceClass.fields
                .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) }
                .forEach { field ->
                    try { result.putIfAbsent(field.name, field.getInt(null)) } catch (e: Exception) { android.util.Log.w("ZKM", "Optional operation failed; continuing", e) }
                }
        }

        // App strings are the primary namespace. Kernel Flasher is an embedded
        // library module, so include its resource keys in the same editable
        // language pack as well. Duplicate names intentionally keep the app
        // resource because Android's merged resource table resolves them to
        // the same user-facing key.
        addResourceClass(com.zuan.kernelmanager.next.R.string::class.java)
        addResourceClass(com.github.capntrips.kernelflasher.R.string::class.java)
        return result
    }

    private fun loadOverrides(context: Context): Map<String, String> {
        val raw = prefs(context).getString(OVERRIDES_KEY, null) ?: return emptyMap()
        return try {
            val json = JSONObject(raw)
            val result = LinkedHashMap<String, String>()
            json.keys().forEach { key ->
                json.optString(key, null)?.let { result[key] = it }
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun get(context: Context, resourceId: Int, fallback: String): String {
        val key = try { context.resources.getResourceEntryName(resourceId) } catch (_: Exception) { return fallback }
        return loadOverrides(context)[key] ?: fallback
    }

    fun getText(context: Context, resourceId: Int, fallback: CharSequence = ""): CharSequence {
        return get(context, resourceId, fallback.toString())
    }

    fun getFormatted(context: Context, resourceId: Int, fallback: String, args: Array<out Any>): String {
        val key = try { context.resources.getResourceEntryName(resourceId) } catch (_: Exception) { return fallback }
        val override = loadOverrides(context)[key] ?: return fallback
        if (args.isEmpty()) return override
        return try { String.format(Locale.getDefault(), override, *args) } catch (_: Exception) { fallback }
    }

    fun export(context: Context, output: OutputStream): Int {
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("locale", Locale.ENGLISH.toLanguageTag())
            .put("note", "Edit only the values. Keep Android format placeholders such as %1\$s unchanged.")

        val strings = JSONObject()
        val knownIds = knownStringIds()
        val baseResources = context.applicationContext.resources
        var count = 0
        for ((key, id) in knownIds.toSortedMap()) {
            try {
                strings.put(key, baseResources.getString(id))
                count++
            } catch (_: Exception) {
                // Ignore invalid entries instead of failing the whole export.
            }
        }

        root.put("strings", strings)
        output.bufferedWriter(Charsets.UTF_8).use { it.write(root.toString(2)) }
        return count
    }

    data class ImportResult(val applied: Int, val ignored: Int)

    fun import(context: Context, input: InputStream): ImportResult {
        val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(text)
        if (root.optString("format") != FORMAT) {
            throw IllegalArgumentException("Not a ZKM Next language pack")
        }
        if (root.optInt("version", -1) != VERSION) {
            throw IllegalArgumentException("Unsupported language pack version")
        }

        val strings = root.optJSONObject("strings")
            ?: throw IllegalArgumentException("Missing strings object")

        val knownIds = knownStringIds()
        val overrides = LinkedHashMap<String, String>()
        var applied = 0
        var ignored = 0

        strings.keys().forEach { key ->
            val value = strings.opt(key)
            if (value !is String) {
                ignored++
                return@forEach
            }
            if (!knownIds.containsKey(key)) {
                ignored++
                return@forEach
            }
            overrides[key] = value
            applied++
        }

        val overrideJson = JSONObject()
        overrides.forEach { (key, value) -> overrideJson.put(key, value) }
        prefs(context).edit()
            .putString(OVERRIDES_KEY, overrideJson.toString())
            .commit()

        return ImportResult(applied, ignored)
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(OVERRIDES_KEY).commit()
    }

    fun overrideCount(context: Context): Int = loadOverrides(context).size
}

/** Resources wrapper used by both app screens and library screens. */
class LanguageAwareResources(
    private val base: Resources,
    private val context: Context
) : Resources(base.assets, base.displayMetrics, base.configuration) {
    override fun getString(id: Int): String {
        return LanguagePackManager.get(context, id, base.getString(id))
    }

    override fun getString(id: Int, vararg formatArgs: Any): String {
        val fallback = base.getString(id, *formatArgs)
        return LanguagePackManager.getFormatted(context, id, fallback, formatArgs)
    }

    override fun getText(id: Int): CharSequence {
        return LanguagePackManager.getText(context, id, base.getText(id))
    }
}
