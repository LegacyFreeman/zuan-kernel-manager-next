/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.library.device.kernel

import com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object KernelInfoUtils {

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    fun getDeviceModel(): String = "${Build.MANUFACTURER.uppercase()} ${Build.MODEL}"
    fun getBuildNumber(): String = Build.DISPLAY
    fun getKernelVersion(): String = System.getProperty("os.version") ?: "Unknown"

    suspend fun getActiveSlot(): String = withContext(Dispatchers.IO) {
        val slotSuffix = getProp("ro.boot.slot_suffix")
        when {
            slotSuffix.contains("_a") -> "A"
            slotSuffix.contains("_b") -> "B"
            else -> "A (Non-AB)"
        }
    }

    // --- LOGIC ROOT UTAMA (Sekarang Aman dari Jank) ---
    suspend fun getBootInfo(slot: String): BootInfo = withContext(Dispatchers.IO) {
        // 1. Cek apakah Root Access tersedia menggunakan su()
        val rootCheck = KernelRootRuntime.execute("id")
        if (!rootCheck.isSuccess) {
            return@withContext BootInfo(
                sha1 = "No Root",
                format = "Unknown",
                vendorFormat = "Unknown"
            )
        }

        val targetSuffix = "_${slot.lowercase()}" // _a atau _b
        val bootPath = "/dev/block/by-name/boot$targetSuffix"
        val vendorBootPath = "/dev/block/by-name/vendor_boot$targetSuffix"

        // 2. Ambil SHA1 Hash
        val sha1Res = KernelRootRuntime.execute("sha1sum ${shellQuote(bootPath)}")
        val sha1Clean = if (sha1Res.isSuccess) {
            sha1Res.outputString.split(Regex("\\s+")).firstOrNull() ?: "Error"
        } else {
            "Error"
        }
        
        // Format SHA1 biar pendek (8 karakter awal)
        val sha1Display = if (sha1Clean.length > 8) sha1Clean.substring(0, 8) else sha1Clean

        // 3. Cek Format Image (Baca Header)
        val bootFormat = checkHeaderFormat(bootPath)
        val vendorFormat = checkHeaderFormat(vendorBootPath)

        BootInfo(sha1Display, bootFormat, vendorFormat)
    }

    // Helper untuk cek format (Raw vs LZ4 vs Gzip) yang sekarang sudah suspend
    private suspend fun checkHeaderFormat(path: String): String {
        if (!KernelRootRuntime.nodeExists(path)) return "Not Found"

        // Baca 8 byte pertama dalam format HEX
        val res = KernelRootRuntime.execute("dd if=${shellQuote(path)} bs=1 count=8 2>/dev/null | xxd -p")
        val hexHeader = if (res.isSuccess) res.outputString.replace("\n", "").trim() else ""

        return when {
            hexHeader.contains("414e44524f494421", ignoreCase = true) -> "raw"
            hexHeader.startsWith("02214c18", ignoreCase = true) -> "lz4_legacy"
            hexHeader.startsWith("1f8b", ignoreCase = true) -> "gzip"
            hexHeader.isEmpty() -> "Unknown"
            else -> "Custom/Unk"
        }
    }

    private suspend fun getProp(key: String): String {
        val res = KernelRootRuntime.execute("getprop ${shellQuote(key)}")
        return if (res.isSuccess) res.outputString else ""
    }
}

// Data Class penampung
data class BootInfo(
    val sha1: String,
    val format: String,
    val vendorFormat: String
)
