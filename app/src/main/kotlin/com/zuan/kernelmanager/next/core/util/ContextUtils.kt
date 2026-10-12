package com.zuan.kernelmanager.next.core.util

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources

object ContextUtils {
    fun updateBaseContext(context: Context): Context {
        val sharedPrefs = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        val dpi = sharedPrefs.getInt("app_custom_dpi", 0)

        val configured = if (dpi == 0) {
            context
        } else {
            val configuration = Configuration(context.resources.configuration)
            configuration.densityDpi = dpi
            context.createConfigurationContext(configuration)
        }

        return LanguageAwareContext(configured)
    }
}

private class LanguageAwareContext(base: Context) : ContextWrapper(base) {
    private val localizedResources by lazy(LazyThreadSafetyMode.NONE) {
        LanguageAwareResources(baseContext.resources, this)
    }

    override fun getResources(): Resources = localizedResources
}
