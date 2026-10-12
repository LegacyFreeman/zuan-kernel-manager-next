/*
 * Copyright (c) 2026 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.library.device.storage

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.os.storage.StorageManager
import com.zuan.kernelmanager.next.library.shell.suBlocking
import java.io.File

/** Total / used / free untuk partisi /data (angka "marketed", sama seperti Settings sistem). */
data class StorageOverview(
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long
) {
    val usedFraction: Float
        get() = if (totalBytes > 0L) (usedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

/** Rincian pemakaian per kategori. Butuh izin Usage access. */
data class StorageBreakdown(
    val apps: Long,
    val images: Long,
    val videos: Long,
    val audio: Long,
    val documentsOther: Long,
    val system: Long,
    val cache: Long
)

data class PartitionUsage(
    val mount: String,
    val totalBytes: Long,
    val usedBytes: Long
) {
    val usedFraction: Float
        get() = if (totalBytes > 0L) (usedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

data class FsInfo(val type: String, val options: List<String>)

data class AppStorage(val packageName: String, val label: String, val bytes: Long)

/** Throughput disk (byte/detik) hasil selisih dua pembacaan /proc/diskstats. */
data class DiskIoPoint(val readBps: Float, val writeBps: Float)

object StorageReader {

    private val PARTITIONS = listOf(
        "/data", "/system", "/vendor", "/product", "/system_ext", "/odm", "/vendor_dlkm", "/cache"
    )

    fun readOverview(context: Context): StorageOverview {
        val ssm = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
        val statFs = runCatching { StatFs(Environment.getDataDirectory().absolutePath) }.getOrNull()
        val total = runCatching { ssm?.getTotalBytes(StorageManager.UUID_DEFAULT) }.getOrNull()
            ?.takeIf { it > 0L } ?: statFs?.totalBytes ?: 0L
        val free = runCatching { ssm?.getFreeBytes(StorageManager.UUID_DEFAULT) }.getOrNull()
            ?.takeIf { it >= 0L } ?: statFs?.availableBytes ?: 0L
        val used = (total - free).coerceAtLeast(0L)
        return StorageOverview(totalBytes = total, usedBytes = used, freeBytes = free)
    }

    /**
     * Rincian kategori lewat StorageStatsManager (mekanisme yang sama dengan Settings sistem).
     * Return null kalau Usage access belum diberikan.
     */
    fun readBreakdown(context: Context, overview: StorageOverview): StorageBreakdown? {
        val ssm = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
            ?: return null
        val user = Process.myUserHandle()
        val uuid = StorageManager.UUID_DEFAULT
        return runCatching {
            val userStats = ssm.queryStatsForUser(uuid, user)
            val ext = ssm.queryExternalStatsForUser(uuid, user)
            // dataBytes sudah termasuk cache.
            val apps = userStats.appBytes + userStats.dataBytes
            val media = ext.imageBytes + ext.videoBytes + ext.audioBytes
            val docsOther = (ext.totalBytes - media - ext.appBytes).coerceAtLeast(0L)
            val system = (overview.usedBytes - apps - media - docsOther).coerceAtLeast(0L)
            StorageBreakdown(
                apps = apps,
                images = ext.imageBytes,
                videos = ext.videoBytes,
                audio = ext.audioBytes,
                documentsOther = docsOther,
                system = system,
                cache = userStats.cacheBytes
            )
        }.getOrNull()
    }

    fun readPartitions(): List<PartitionUsage> = PARTITIONS.mapNotNull { mount ->
        if (!File(mount).exists()) return@mapNotNull null
        val fs = runCatching { StatFs(mount) }.getOrNull() ?: return@mapNotNull null
        val total = fs.totalBytes
        if (total <= 0L) return@mapNotNull null
        val used = (total - fs.freeBytes).coerceAtLeast(0L)
        PartitionUsage(mount, total, used)
    }

    private val interestingOptions = listOf(
        "noatime", "lazytime", "discard", "nodiscard", "inlinecrypt", "compress_algorithm", "atgc", "gc_merge"
    )

    fun readFsInfo(): FsInfo? = runCatching {
        File("/proc/mounts").readLines()
            .map { it.split(' ') }
            .firstOrNull { it.size >= 4 && it[1] == "/data" }
            ?.let { parts ->
                val opts = parts[3].split(',').filter { opt ->
                    interestingOptions.any { opt == it || opt.startsWith("$it=") }
                }
                FsInfo(type = parts[2], options = opts)
            }
    }.getOrNull()

    /** Total byte yang pernah ditulis (f2fs). Butuh root; null kalau tidak tersedia. */
    fun readLifetimeWrites(): Long? = runCatching {
        val out = suBlocking("cat /sys/fs/f2fs/*/lifetime_write_kbytes 2>/dev/null")
        out.out.mapNotNull { it.trim().toLongOrNull() }.maxOrNull()?.times(1024L)
    }.getOrNull()

    @Volatile private var topAppsCache: List<AppStorage>? = null
    @Volatile private var topAppsCacheAt: Long = 0L

    /** Aplikasi terbesar (app + data + cache). Butuh Usage access. Cache 5 menit. */
    @Suppress("DEPRECATION")
    fun readTopApps(context: Context, limit: Int, force: Boolean = false): List<AppStorage> {
        val now = SystemClock.elapsedRealtime()
        topAppsCache?.let { if (!force && now - topAppsCacheAt < 300_000L) return it.take(limit) }
        val ssm = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
            ?: return emptyList()
        val pm = context.packageManager
        val user = Process.myUserHandle()
        val result = ArrayList<AppStorage>()
        val apps: List<ApplicationInfo> = runCatching { pm.getInstalledApplications(0) }.getOrDefault(emptyList())
        for (app in apps) {
            val st = runCatching {
                ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, app.packageName, user)
            }.getOrNull() ?: continue
            val bytes = st.appBytes + st.dataBytes
            if (bytes <= 0L) continue
            val label = runCatching { pm.getApplicationLabel(app).toString() }.getOrDefault(app.packageName)
            result.add(AppStorage(app.packageName, label, bytes))
        }
        val sorted = result.sortedByDescending { it.bytes }
        topAppsCache = sorted
        topAppsCacheAt = now
        return sorted.take(limit)
    }

    /**
     * Sampler throughput disk dari /proc/diskstats (jumlah semua disk fisik: sdX, mmcblkN, nvmeNnM).
     * Panggil sample() berkala dari thread background.
     */
    class DiskIoSampler {
        private var lastRead = -1L
        private var lastWrite = -1L
        private var lastAt = 0L
        private var directOk = true
        private val physical = Regex("^(sd[a-z]+|mmcblk\\d+|nvme\\d+n\\d+)$")

        private fun readText(): String? {
            if (directOk) {
                val direct = runCatching { File("/proc/diskstats").readText() }.getOrNull()
                if (!direct.isNullOrBlank()) return direct
                directOk = false
            }
            return suBlocking("cat /proc/diskstats").outputString.takeIf { it.isNotBlank() }
        }

        fun sample(): DiskIoPoint? {
            val text = readText() ?: return null
            var read = 0L
            var write = 0L
            var matched = false
            text.lineSequence().forEach { line ->
                val p = line.trim().split(Regex("\\s+"))
                if (p.size >= 10 && physical.matches(p[2])) {
                    matched = true
                    read += p[5].toLongOrNull() ?: 0L
                    write += p[9].toLongOrNull() ?: 0L
                }
            }
            if (!matched) return null
            val now = SystemClock.elapsedRealtime()
            val point = if (lastRead >= 0L && now > lastAt) {
                val dt = (now - lastAt) / 1000f
                DiskIoPoint(
                    readBps = ((read - lastRead).coerceAtLeast(0L) * 512f) / dt,
                    writeBps = ((write - lastWrite).coerceAtLeast(0L) * 512f) / dt
                )
            } else null
            lastRead = read
            lastWrite = write
            lastAt = now
            return point
        }
    }
}
