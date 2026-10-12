/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.home.menu

import android.content.Context
import android.content.pm.ApplicationInfo
import com.zuan.kernelmanager.next.library.shell.su
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppCompileItem(
    val packageName: String,
    val label: String,
    val icon: android.graphics.drawable.Drawable?,
    val isSystem: Boolean
)

object Dex2oatUtils {

    val COMPILE_MODES = listOf(
        "speed-profile" to "Speed Profile (Default)",
        "speed" to "Speed (Max Performance)",
        "everything" to "Everything (Max Size)",
        "quicker" to "Quicker (Balanced)",
        "verify" to "Verify (Fast Install)"
    )

    fun getInstalledApps(context: Context): List<AppCompileItem> {
        val pm = context.packageManager
        val apps = mutableListOf<AppCompileItem>()
        
        try {
            val installed = pm.getInstalledPackages(0)
            installed.forEach { pkg ->
                pkg.applicationInfo?.let { appInfo ->
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val icon = pm.getApplicationIcon(appInfo)
                    apps.add(AppCompileItem(pkg.packageName, label, icon, isSystem))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ZKM", "Unhandled operation failure", e)
        }
        return apps.sortedBy { it.label.lowercase() }
    }

    private fun isSafePackageName(packageName: String): Boolean =
        packageName.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+"))

    // Compiler filter names (speed-profile, everything, ...) are lowercase words and dashes.
    private fun isSafeMode(mode: String): Boolean = mode.matches(Regex("[a-z][a-z0-9-]*"))

    suspend fun compileApp(packageName: String, mode: String): Boolean = withContext(Dispatchers.IO) {
        if (!isSafePackageName(packageName) || !isSafeMode(mode)) return@withContext false
        su("cmd package compile -m $mode -f $packageName").isSuccess
    }

    suspend fun resetApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!isSafePackageName(packageName)) return@withContext false
        su("cmd package compile --reset $packageName").isSuccess
    }
    
    suspend fun compileAll(mode: String): Boolean = withContext(Dispatchers.IO) {
        if (!isSafeMode(mode)) return@withContext false
        su("cmd package compile -m $mode -f -a").isSuccess
    }

    suspend fun resetAll(): Boolean = withContext(Dispatchers.IO) {
        su("cmd package compile --reset -a").isSuccess
    }
}
