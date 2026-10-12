/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.library.device.usage

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.Settings
import com.zuan.kernelmanager.next.library.shell.suBlocking
import java.util.Calendar

/**
 * Pembungkus UsageStatsManager untuk Screen Time & Battery Usage.
 * Semua fungsi query WAJIB dipanggil dari thread background.
 */
object UsageStatsHelper {

    fun hasAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        )
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    /** Buka halaman "Usage access" (langsung ke app ini bila didukung ROM). */
    fun openAccessSettings(context: Context) {
        val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val ok = runCatching { context.startActivity(direct) }.isSuccess
        if (!ok) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    /** Beri izin lewat root (app ini sudah root-based). Thread background saja. */
    fun grantWithRoot(context: Context): Boolean =
        runCatching {
            suBlocking("appops set ${context.packageName} GET_USAGE_STATS allow").isSuccess
        }.getOrDefault(false) && hasAccess(context)

    fun startOfDay(daysAgo: Int = 0): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return c.timeInMillis
    }

    /** Lama tiap paket di foreground pada [start, end] (ms), dari event ACTIVITY_RESUMED/PAUSED. */
    fun foregroundByPackage(context: Context, start: Long, end: Long): Map<String, Long> {
        if (!hasAccess(context)) return emptyMap()
        return runCatching {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val events = usm.queryEvents(start, end) ?: return emptyMap()
            val e = UsageEvents.Event()
            val open = HashMap<String, Long>()
            val total = HashMap<String, Long>()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                val pkg = e.packageName ?: continue
                when (e.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        if (!open.containsKey(pkg)) open[pkg] = e.timeStamp
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        val began = open.remove(pkg)
                        if (began != null) {
                            total[pkg] = (total[pkg] ?: 0L) + (e.timeStamp - began).coerceAtLeast(0L)
                        }
                    }
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                        open.forEach { (p, began) ->
                            total[p] = (total[p] ?: 0L) + (e.timeStamp - began).coerceAtLeast(0L)
                        }
                        open.clear()
                    }
                }
            }
            open.forEach { (p, began) ->
                total[p] = (total[p] ?: 0L) + (end - began).coerceAtLeast(0L)
            }
            total
        }.getOrDefault(emptyMap())
    }

    /** Total layar menyala pada [start, end] (ms). Fallback: jumlah waktu foreground semua app. */
    fun screenOnMillis(context: Context, start: Long, end: Long): Long {
        if (!hasAccess(context)) return 0L
        val fromScreenEvents = runCatching {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val events = usm.queryEvents(start, end) ?: return@runCatching -1L
            val e = UsageEvents.Event()
            var onSince = -1L
            var total = 0L
            var seen = false
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                when (e.eventType) {
                    UsageEvents.Event.SCREEN_INTERACTIVE -> {
                        seen = true
                        if (onSince < 0) onSince = e.timeStamp
                    }
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                        seen = true
                        if (onSince >= 0) total += (e.timeStamp - onSince).coerceAtLeast(0L)
                        onSince = -1L
                    }
                }
            }
            if (onSince >= 0) total += (end - onSince).coerceAtLeast(0L)
            if (seen) total else -1L
        }.getOrDefault(-1L)
        return if (fromScreenEvents >= 0) fromScreenEvents
        else foregroundByPackage(context, start, end).values.sum()
    }

    /** Paket yang sedang di depan saat ini, atau "" bila layar mati / tidak diketahui. */
    fun foregroundPackage(context: Context): String {
        if (!hasAccess(context)) return ""
        return runCatching {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - 10 * 60_000L, now) ?: return@runCatching ""
            val e = UsageEvents.Event()
            var current = ""
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                when (e.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> current = e.packageName ?: current
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> if (e.packageName == current) current = ""
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> current = ""
                }
            }
            current
        }.getOrDefault("")
    }

    fun appLabel(context: Context, pkg: String): String =
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
}
