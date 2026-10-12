/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val version: String,
    val changelog: String,
    val downloadUrl: String,
    val fileName: String,
    /** Lowercase hex SHA-256 of the APK when the release publishes one; null if unknown. */
    val sha256: String? = null
)

private val SHA256_HEX = Regex("[A-Fa-f0-9]{64}")

/** Returns the first release asset that is really an APK (never blindly assets[0]). */
internal fun pickApkAsset(assets: org.json.JSONArray?): JSONObject? {
    if (assets == null) return null
    for (i in 0 until assets.length()) {
        val asset = assets.optJSONObject(i) ?: continue
        if (asset.optString("name").endsWith(".apk", ignoreCase = true)) return asset
    }
    return null
}

/**
 * Finds the expected SHA-256 for [asset]: GitHub's own `digest` field ("sha256:<hex>")
 * first, then a sha256sum-style line ("<hex>  <file name>") in the release notes.
 */
internal fun extractSha256(asset: JSONObject?, releaseBody: String): String? {
    if (asset == null) return null
    val digest = asset.optString("digest")
    if (digest.startsWith("sha256:", ignoreCase = true)) {
        val hex = digest.substringAfter(':').trim()
        if (SHA256_HEX.matches(hex)) return hex.lowercase()
    }
    val name = asset.optString("name")
    if (name.isNotEmpty()) {
        val line = Regex("([A-Fa-f0-9]{64})\\s+\\*?" + Regex.escape(name)).find(releaseBody)
        if (line != null) return line.groupValues[1].lowercase()
    }
    return null
}

internal fun sha256Hex(input: java.io.InputStream): String {
    val md = java.security.MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        md.update(buffer, 0, read)
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

/**
 * Compares dotted numeric version components, accepting GitHub tags such as v1.2.3.
 * Non-release suffixes (for example -beta) are ignored for numeric ordering.
 */
fun isNewerVersion(latestVersion: String, currentVersion: String): Boolean {
    fun parse(value: String): List<Int> = value
        .trim()
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore('-')
        .substringBefore('+')
        .split('.')
        .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    val latest = parse(latestVersion)
    val current = parse(currentVersion)
    val size = maxOf(latest.size, current.size)

    for (index in 0 until size) {
        val latestPart = latest.getOrElse(index) { 0 }
        val currentPart = current.getOrElse(index) { 0 }
        if (latestPart != currentPart) return latestPart > currentPart
    }

    return false
}

object UpdateChecker {
    private const val REPO_OWNER = "ZUANVFX01"
    private const val REPO_NAME = "ZKM"
    private const val API_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                connectTimeout = 10000
                readTimeout = 10000
            }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                
                val tagName = json.getString("tag_name") // e.g., "v1.0.0"
                val latestVersion = tagName.removePrefix("v").removePrefix("V")
                
                val currentVersion = context.packageManager.getPackageInfo(
                    context.packageName, 0
                ).versionName ?: return@withContext null

                // Cek jika versi berbeda (GitHub lebih diutamakan untuk mencegah mod)
                if (isNewerVersion(latestVersion, currentVersion)) {
                    val changelog = json.optString("body", "No changelogs provided.")
                    val assets = json.optJSONArray("assets")
                    
                    var apkUrl = ""
                    var apkName = "update.apk"
                    var apkSha256: String? = null

                    val apkAsset = pickApkAsset(assets)
                    if (apkAsset != null) {
                        apkUrl = apkAsset.getString("browser_download_url")
                        apkName = apkAsset.getString("name")
                        apkSha256 = extractSha256(apkAsset, changelog)
                    }
                    
                    // Fallback ke html_url jika tidak ada asset langsung
                    if (apkUrl.isEmpty()) {
                        apkUrl = json.getString("html_url")
                    }

                    UpdateInfo(
                        version = latestVersion,
                        changelog = changelog,
                        downloadUrl = apkUrl,
                        fileName = apkName,
                        sha256 = apkSha256
                    )
                } else {
                    null // Versi sama, tidak perlu update
                }
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("ZKM", "Unhandled operation failure", e)
            null
        }
    }

    /**
     * Enqueues the APK download. Returns -1 (nothing enqueued) when the URL is not an
     * https link to an .apk file, e.g. when the release only offers an HTML page.
     */
    fun downloadUpdate(context: Context, updateInfo: UpdateInfo): Long {
        val url = updateInfo.downloadUrl
        if (!url.startsWith("https://", ignoreCase = true) || !url.substringBefore('?').endsWith(".apk", ignoreCase = true)) {
            android.util.Log.w("ZKM", "Refusing to auto-download non-APK/non-https update URL")
            return -1L
        }
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val safeVersion = updateInfo.version.replace(Regex("[^A-Za-z0-9._-]"), "_")

        // App-private download dir: not world-writable, so no other app can swap the APK
        // between download and install.
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("Updating ZKM to v${updateInfo.version}")
            setDescription("Downloading...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "ZKM_v$safeVersion.apk")
            setMimeType("application/vnd.android.package-archive")
        }
        
        return downloadManager.enqueue(request)
    }

    fun registerInstallReceiver(context: Context, downloadId: Long, expectedSha256: String? = null) {
        if (downloadId < 0) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: return
                if (id == downloadId) {
                    context?.let { installApk(it, downloadId, expectedSha256) }
                    context?.unregisterReceiver(this)
                }
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    private fun installApk(context: Context, downloadId: Long, expectedSha256: String?) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = downloadManager.getUriForDownloadedFile(downloadId) ?: return

        // When the release publishes a checksum, refuse to install anything that differs.
        // (Android still enforces the signing-key match as a second line of defence.)
        if (expectedSha256 != null) {
            val actual = try {
                context.contentResolver.openInputStream(uri)?.use { sha256Hex(it) }
            } catch (e: Exception) {
                null
            }
            if (actual == null || !actual.equals(expectedSha256, ignoreCase = true)) {
                android.util.Log.e("ZKM", "Update checksum mismatch; refusing to install")
                downloadManager.remove(downloadId)
                android.widget.Toast.makeText(
                    context,
                    context.getString(com.zuan.kernelmanager.next.R.string.update_checksum_rejected),
                    android.widget.Toast.LENGTH_LONG
                ).show()
                return
            }
        }
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }
}
