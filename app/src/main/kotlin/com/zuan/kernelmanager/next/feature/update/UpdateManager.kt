/*
 * Copyright (c) 2025 ZKM Next
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
    val sha256: String? = null,
    /** True when the release is flagged as a pre-release (beta build). */
    val isPrerelease: Boolean = false
)

sealed interface UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    /** Both "manager" and "beta" checks are switched off in Update Settings. */
    data object Disabled : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

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
 * When the numeric parts are equal a final release is newer than its pre-release
 * (1.1.0 > 1.1.0-beta.2) and pre-releases are ordered by their suffix
 * (1.1.0-beta.2 > 1.1.0-beta.1).
 */
fun isNewerVersion(latestVersion: String, currentVersion: String): Boolean {
    fun clean(value: String): String = value
        .trim()
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore('+')

    fun numeric(value: String): List<Int> = value
        .substringBefore('-')
        .split('.')
        .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    fun pre(value: String): String? =
        if (value.contains('-')) value.substringAfter('-').takeIf { it.isNotBlank() } else null

    fun compareIdentifier(a: String, b: String): Int {
        val ai = a.toIntOrNull()
        val bi = b.toIntOrNull()
        if (ai != null && bi != null) return ai.compareTo(bi)
        // "beta2" vs "beta10": same alphabetic prefix, compare the trailing number
        val ap = a.trimEnd(Char::isDigit)
        val bp = b.trimEnd(Char::isDigit)
        if (ap == bp && ap.isNotEmpty()) {
            val an = a.removePrefix(ap).toIntOrNull() ?: 0
            val bn = b.removePrefix(bp).toIntOrNull() ?: 0
            return an.compareTo(bn)
        }
        return a.compareTo(b, ignoreCase = true)
    }

    fun comparePre(a: String, b: String): Int {
        val ap = a.split('.')
        val bp = b.split('.')
        for (i in 0 until maxOf(ap.size, bp.size)) {
            val x = ap.getOrNull(i) ?: return -1
            val y = bp.getOrNull(i) ?: return 1
            val c = compareIdentifier(x, y)
            if (c != 0) return c
        }
        return 0
    }

    val latest = clean(latestVersion)
    val current = clean(currentVersion)
    val latestNum = numeric(latest)
    val currentNum = numeric(current)
    val size = maxOf(latestNum.size, currentNum.size)

    for (index in 0 until size) {
        val latestPart = latestNum.getOrElse(index) { 0 }
        val currentPart = currentNum.getOrElse(index) { 0 }
        if (latestPart != currentPart) return latestPart > currentPart
    }

    val latestPre = pre(latest)
    val currentPre = pre(current)
    return when {
        latestPre == currentPre -> false
        latestPre == null -> true      // final release beats its own pre-release
        currentPre == null -> false    // pre-release never beats the final release
        else -> comparePre(latestPre, currentPre) > 0
    }
}

object UpdateChecker {
    const val REPO_OWNER = "zuanvfx02"
    const val REPO_NAME = "zuan-kernel-manager-next"
    const val REPO_URL = "https://github.com/$REPO_OWNER/$REPO_NAME"
    const val RELEASES_URL = "$REPO_URL/releases"
    private const val API_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases?per_page=20"

    /**
     * Looks at the published releases of the ZKM Next repository and returns the newest one
     * that is newer than the installed version, or null when there is nothing to offer.
     *
     * Stable releases are considered when "Check for manager updates" is on, pre-releases
     * (beta builds) when "Check for beta updates" is on. [manual] = true (the user pressed a
     * check button) always includes stable releases regardless of the toggles.
     */
    suspend fun checkForUpdate(context: Context, manual: Boolean = false): UpdateInfo? =
        (check(context, manual) as? UpdateCheckResult.Available)?.info

    /** Same as [checkForUpdate] but tells "up to date", "disabled" and "error" apart. */
    suspend fun check(context: Context, manual: Boolean = false): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val prefs = com.zuan.kernelmanager.next.feature.settings.SettingsPreference.getInstance(context)
            val includeStable = manual || prefs.getCheckManagerUpdates()
            val includeBeta = prefs.getCheckBetaUpdates()
            if (!includeStable && !includeBeta) return@withContext UpdateCheckResult.Disabled

            val currentVersion = context.packageManager.getPackageInfo(
                context.packageName, 0
            ).versionName ?: return@withContext UpdateCheckResult.Error("Unknown installed version")

            val connection = URL(API_URL).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "ZKMNext")
                connectTimeout = 10000
                readTimeout = 10000
            }
            if (connection.responseCode != 200) {
                return@withContext UpdateCheckResult.Error("GitHub answered HTTP ${connection.responseCode}")
            }
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            prefs.setLastUpdateCheck(System.currentTimeMillis())

            val releases = org.json.JSONArray(response)
            var best: JSONObject? = null
            var bestVersion = ""
            for (i in 0 until releases.length()) {
                val release = releases.optJSONObject(i) ?: continue
                if (release.optBoolean("draft", false)) continue
                val tag = release.optString("tag_name")
                // Nightly builds (tag "nightly-YYYYMMDD") are for manual download only.
                if (tag.startsWith("nightly", ignoreCase = true)) continue
                val isPre = release.optBoolean("prerelease", false)
                if (isPre && !includeBeta) continue
                if (!isPre && !includeStable) continue
                val version = tag.removePrefix("v").removePrefix("V")
                if (version.isBlank()) continue
                if (best == null || isNewerVersion(version, bestVersion)) {
                    best = release
                    bestVersion = version
                }
            }

            val json = best ?: return@withContext UpdateCheckResult.UpToDate
            if (!isNewerVersion(bestVersion, currentVersion)) return@withContext UpdateCheckResult.UpToDate

            val changelog = json.optString("body", "").ifBlank { "No changelogs provided." }
            val apkAsset = pickApkAsset(json.optJSONArray("assets"))
            var apkUrl = ""
            var apkName = "update.apk"
            var apkSha256: String? = null
            if (apkAsset != null) {
                apkUrl = apkAsset.getString("browser_download_url")
                apkName = apkAsset.getString("name")
                apkSha256 = extractSha256(apkAsset, changelog)
            }
            // No APK attached: send the user to the release page instead of auto-downloading.
            if (apkUrl.isEmpty()) apkUrl = json.optString("html_url").ifBlank { RELEASES_URL }

            UpdateCheckResult.Available(
                UpdateInfo(
                    version = bestVersion,
                    changelog = changelog,
                    downloadUrl = apkUrl,
                    fileName = apkName,
                    sha256 = apkSha256,
                    isPrerelease = json.optBoolean("prerelease", false)
                )
            )
        } catch (e: Exception) {
            android.util.Log.e("ZKMNext", "Update check failed", e)
            UpdateCheckResult.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Size in bytes of APKs this app downloaded for updates (app-private folder). */
    fun downloadedUpdatesSize(context: Context): Long = downloadedUpdateFiles(context).sumOf { it.length() }

    /** Deletes downloaded update APKs, returns how many files were removed. */
    fun clearDownloadedUpdates(context: Context): Int =
        downloadedUpdateFiles(context).count { it.delete() }

    private fun downloadedUpdateFiles(context: Context): List<File> =
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?.listFiles { f -> f.isFile && f.name.startsWith("ZKMNext_v") && f.name.endsWith(".apk") }
            ?.toList().orEmpty()

    /**
     * Enqueues the APK download. Returns -1 (nothing enqueued) when the URL is not an
     * https link to an .apk file, e.g. when the release only offers an HTML page.
     */
    fun downloadUpdate(context: Context, updateInfo: UpdateInfo): Long {
        val url = updateInfo.downloadUrl
        if (!url.startsWith("https://", ignoreCase = true) || !url.substringBefore('?').endsWith(".apk", ignoreCase = true)) {
            android.util.Log.w("ZKMNext", "Refusing to auto-download non-APK/non-https update URL")
            return -1L
        }
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val safeVersion = updateInfo.version.replace(Regex("[^A-Za-z0-9._-]"), "_")

        // App-private download dir: not world-writable, so no other app can swap the APK
        // between download and install.
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("Updating ZKM Next to v${updateInfo.version}")
            setDescription("Downloading...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "ZKMNext_v$safeVersion.apk")
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
                android.util.Log.e("ZKMNext", "Update checksum mismatch; refusing to install")
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
