/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.home.menu

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import com.zuan.kernelmanager.next.library.shell.su
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppUiModel(
    val label: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val targetSdk: Int,
    val isSystem: Boolean,
    val isEnabled: Boolean,
    val iconDrawable: android.graphics.drawable.Drawable 
)

object DebloatFreezeUtils {

    private fun isSafePackageName(packageName: String): Boolean =
        packageName.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+"))

    fun getInstalledApps(context: Context): List<AppUiModel> {
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
        
        return packages.mapNotNull { pkg ->
            try {
                val appInfo = pkg.applicationInfo ?: return@mapNotNull null
                
                val label = pm.getApplicationLabel(appInfo).toString()
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isEnabled = appInfo.enabled

                AppUiModel(
                    label = label,
                    packageName = pkg.packageName,
                    versionName = pkg.versionName ?: "Unknown",
                    versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) pkg.longVersionCode else pkg.versionCode.toLong(),
                    targetSdk = appInfo.targetSdkVersion,
                    isSystem = isSystem,
                    isEnabled = isEnabled,
                    iconDrawable = pm.getApplicationIcon(appInfo)
                )
            } catch (e: Exception) {
                null 
            }
        }.sortedBy { it.label.lowercase() }
    }

    suspend fun toggleAppState(packageName: String, enable: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!isSafePackageName(packageName)) return@withContext false
        val command = if (enable) "pm enable $packageName" else "pm disable-user --user 0 $packageName"
        su(command).isSuccess
    }

    suspend fun debloatApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!isSafePackageName(packageName)) return@withContext false
        su("pm uninstall --user 0 $packageName").isSuccess
    }

    fun openAppSystemSettings(context: Context, packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("ZKM", "Unhandled operation failure", e)
        }
    }
}
