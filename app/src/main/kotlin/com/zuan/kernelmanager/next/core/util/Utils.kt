/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.core.util
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime
import com.zuan.kernelmanager.next.core.kernel.KernelControlEngine

import android.app.ActivityManager
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.text.format.Formatter
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

object Utils {
    // --- DEVICE INFO UTILS ---
    fun getDeviceName(): String = Build.MODEL
    fun getDeviceCodename(): String = Build.DEVICE
    fun getAndroidVersion(): String = Build.VERSION.RELEASE
    fun getSdkVersion(): String = Build.VERSION.SDK_INT.toString()
    fun getManufacturer(): String = Build.MANUFACTURER
    fun getBuildTags(): String = Build.TAGS

    // --- KERNEL VERSION (Tanpa Shell) ---
    fun getKernelVersion(): String {
        return try {
            val reader = BufferedReader(FileReader("/proc/version"))
            val line = reader.readLine()
            reader.close()
            if (line.isNotEmpty()) {
                val temp = line.split(" (")[0] 
                temp.replace("Linux version ", "") 
            } else {
                "Unknown"
            }
        } catch (e: Exception) {
            System.getProperty("os.version") ?: "Unknown"
        }
    }

    // --- SYSTEM PROPERTY ---
    fun getSystemProperty(key: String): String = runCatching {
        val safeKey = key.trim().takeIf { it.matches(Regex("[A-Za-z0-9_.-]+")) } ?: return@runCatching ""
        val res = KernelRootRuntime.executeBlocking("getprop ${safeKey}")
        if (res.isSuccess) res.outputString.lines().firstOrNull()?.trim() ?: "" else ""
    }.getOrNull().orEmpty()

    // --- FILE OPERATIONS ---
    fun getTemp(filePath: String): String {
        val value = readFile(filePath).trim()
        return value.toFloatOrNull()?.div(1000)?.let { "%.1f".format(it) } ?: "N/A"
    }

    fun testFile(filePath: String): Boolean {
        if (File(filePath).exists()) return true
        if (filePath.startsWith("/sys/") || filePath.startsWith("/proc/") || filePath.startsWith("/dev/cpuset/") || filePath.startsWith("/dev/stune/") || filePath.startsWith("/dev/cpuctl/") || filePath.startsWith("/sys/fs/cgroup/")) {
            return KernelRootRuntime.nodeExistsBlocking(filePath)
        }
        val result = KernelRootRuntime.executeBlocking("test -f ${shellQuote(filePath)} && printf '%s' true || printf '%s' false")
        return result.isSuccess && result.outputString.trim() == "true"
    }

    fun setPermissions(permission: Int, filePath: String) {
        KernelRootRuntime.executeBlocking("chmod ${permission.coerceIn(0, 511)} ${shellQuote(filePath)}")
    }

    fun readFile(filePath: String): String {
        if (filePath.startsWith("/sys/") || filePath.startsWith("/proc/") || filePath.startsWith("/dev/cpuset/") || filePath.startsWith("/dev/stune/") || filePath.startsWith("/dev/cpuctl/") || filePath.startsWith("/sys/fs/cgroup/")) {
            return KernelRootRuntime.readNodeBlocking(filePath)
        }
        val res = KernelRootRuntime.executeBlocking("cat ${shellQuote(filePath)}")
        return if (res.isSuccess) res.outputString else ""
    }

    fun writeFile(filePath: String, value: String): Boolean = runCatching {
        if (filePath.startsWith("/sys/") || filePath.startsWith("/proc/") || filePath.startsWith("/dev/cpuset/") || filePath.startsWith("/dev/stune/") || filePath.startsWith("/dev/cpuctl/") || filePath.startsWith("/sys/fs/cgroup/")) {
            return@runCatching KernelControlEngine.setKernelNodeBlocking(filePath, value, verify = true).success
        }
        KernelRootRuntime.executeBlocking("printf '%s' ${shellQuote(value)} > ${shellQuote(filePath)}").isSuccess
    }.getOrElse {
        Log.e("writeFile", "Error writing to $filePath: ${it.message}", it)
        false
    }

    // --- APP INFO ---
    fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${packageInfo.versionName} (${packageInfo.longVersionCode})"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    fun getAppVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
        } catch (e: Exception) {
            "0.0.0"
        }
    }

    // --- RAM & MEMORY UTILS ---
    fun getTotalRam(context: Context): String {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        val totalMemVars = memInfo.totalMem.toDouble()
        return String.format("%.1f GB", totalMemVars / (1024 * 1024 * 1024))
    }

    fun getSelinuxStatus(): String {
        val result = KernelRootRuntime.executeBlocking("getenforce 2>/dev/null")
        return if (result.isSuccess) result.outputString else "Unknown"
    }

    fun getBuildUser(): String {
        val cmd = "getprop ro.build.fingerprint 2>/dev/null | tr '/' '\\n' 2>/dev/null | grep user 2>/dev/null | cut -f1 -d: 2>/dev/null"
        val result = KernelRootRuntime.executeBlocking(cmd)
        return if (result.isSuccess && result.outputString.isNotEmpty()) result.outputString.lines().first().trim() else "CPU"
    }

    // --- RAM STATUS ---
    data class RamStatus(
        val totalMem: Long,
        val availMem: Long,
        val usedMem: Long,
        val usagePercent: Float,
        val availString: String,
        val totalString: String
    )

    fun getRamStatus(context: Context): RamStatus {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)

        val total = memInfo.totalMem
        val avail = memInfo.availMem
        val used = total - avail
        val percent = if (total > 0) used.toFloat() / total.toFloat() else 0f

        return RamStatus(
            totalMem = total,
            availMem = avail,
            usedMem = used,
            usagePercent = percent,
            availString = Formatter.formatShortFileSize(context, avail),
            totalString = Formatter.formatShortFileSize(context, total)
        )
    }

    // --- SERVICE CHECKER UTILS ---
    @Suppress("DEPRECATION") 
    fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        for (service in manager?.getRunningServices(Int.MAX_VALUE) ?: emptyList()) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    // --- UPDATE & INSTALLER UTILS ---
    fun downloadAndInstallApk(context: Context, url: String, fileName: String) {
        try {
            require(url.startsWith("https://", ignoreCase = true)) { "Update URL must use https" }
            require(fileName.matches(Regex("[A-Za-z0-9._-]+"))) { "Unsafe update file name" }
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
            if (file.exists()) file.delete()

            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(context.getString(R.string.lp_a11y_update_zkm))
                .setDescription(context.getString(R.string.lp_a11y_downloading_latest_version))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setMimeType("application/vnd.android.package-archive")

            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = manager.enqueue(request)

            Toast.makeText(context, context.getString(R.string.lp_toast_download_start), Toast.LENGTH_SHORT).show()

            val onComplete = object : BroadcastReceiver() {
                override fun onReceive(ctxt: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        installApk(ctxt, fileName)
                        try { ctxt.unregisterReceiver(this) } catch (e: Exception) { android.util.Log.w("ZKM", "Optional operation failed; continuing", e) }
                    }
                }
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            }

        } catch (e: Exception) {
            android.util.Log.e("ZKM", "Unhandled operation failure", e)
            Toast.makeText(context, context.getString(R.string.lp_toast_download_failed, e.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
        }
    }

    private fun installApk(context: Context, fileName: String) {
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
        
        if (file.exists()) {
            try {
                val authority = "${context.packageName}.provider" 
                val uri = FileProvider.getUriForFile(context, authority, file)

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                context.startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.e("ZKM", "Unhandled operation failure", e)
                Toast.makeText(context, context.getString(R.string.lp_toast_installer_failed, e.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, context.getString(R.string.lp_toast_update_not_found), Toast.LENGTH_SHORT).show()
        }
    }
    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"
}
