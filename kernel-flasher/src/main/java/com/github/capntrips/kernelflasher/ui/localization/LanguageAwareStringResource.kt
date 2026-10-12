package com.github.capntrips.kernelflasher.ui.localization

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import org.json.JSONObject
import java.util.Locale

/**
 * Reads the ZKM host language-pack overrides for Kernel Flasher resources.
 *
 * Kernel Flasher is an embedded library module and therefore cannot depend on
 * the app's LanguagePackManager without creating a module dependency cycle.
 * Both sides intentionally share the same small JSON contract instead.
 */
private const val PREFS = "language_pack"
private const val OVERRIDES_KEY = "overrides_json"

private fun overrides(context: Context): Map<String, String> {
    val raw = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(OVERRIDES_KEY, null) ?: return emptyMap()

    return runCatching {
        val json = JSONObject(raw)
        buildMap {
            json.keys().forEach { key ->
                json.optString(key, null)?.let { put(key, it) }
            }
        }
    }.getOrDefault(emptyMap())
}

private fun resolve(context: Context, resourceId: Int, fallback: String, args: Array<out Any>): String {
    val key = runCatching { context.resources.getResourceEntryName(resourceId) }.getOrNull()
        ?: return fallback
    val value = overrides(context)[key] ?: return fallback
    if (args.isEmpty()) return value
    return runCatching { String.format(Locale.getDefault(), value, *args) }.getOrDefault(fallback)
}

@Composable
fun stringResource(resourceId: Int): String {
    val context = LocalContext.current
    return resolve(context, resourceId, context.resources.getString(resourceId), emptyArray())
}

@Composable
fun stringResource(resourceId: Int, vararg formatArgs: Any): String {
    val context = LocalContext.current
    val fallback = context.resources.getString(resourceId, *formatArgs)
    return resolve(context, resourceId, fallback, formatArgs)
}
