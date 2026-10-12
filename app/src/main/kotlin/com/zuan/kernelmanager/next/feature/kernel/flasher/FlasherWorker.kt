/*
 * Original code from: libxzr (HorizonKernelFlasher)
 * Modified and integrated by: Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.kernel.flasher

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.zuan.kernelmanager.next.core.util.AssetsUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.util.zip.ZipFile

class FlasherWorker(
    private val context: Context,
    private val uri: Uri,
    private val onLog: (String) -> Unit
) {
    // PATCH: semua kerja flasher diisolasi ke subfolder sendiri,
    // sehingga cleanup tidak pernah menyentuh data lain di files/
    private val flasherDir = File(context.filesDir, "flasher").absolutePath
    private var filePath: String = ""
    private var binaryPath: String = ""

    // Status flag
    private val DEBUG = false

    suspend fun startFlashing(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Setup Path
            // PATCH (anti shell-injection): nama file dari DocumentFile.name adalah
            // input tak terpercaya (bisa berisi ";, $, `, dll). Sanitasi ke charset aman.
            val documentFile = DocumentFile.fromSingleUri(context, uri)
            val rawName = documentFile?.name ?: "kernel.zip"
            val fileName = sanitizeFileName(rawName)
            filePath = "$flasherDir/$fileName"
            binaryPath = "$flasherDir/META-INF/com/google/android/update-binary"

            onLog("--- Starting Flash Process ---")

            // 2. Cleanup Old Files (hanya folder flasher, bukan seluruh files/)
            onLog("Cleaning up...")
            cleanup()
            File(flasherDir).mkdirs()

            // 3. Cek Root
            if (!rootAvailable()) {
                onLog("ERROR: No Root Access detected!")
                return@withContext false
            }

            // 4. Copy File dari URI ke Internal Storage
            onLog("Copying file to: $filePath")
            copyFile()
            if (!File(filePath).exists()) {
                onLog("ERROR: Failed to copy file.")
                return@withContext false
            }

            // 5. Extract Binary (update-binary)
            onLog("Extracting update-binary...")
            getBinary()

            // 6. Patch Binary (Inject mkbootfs)
            onLog("Patching binary...")
            patch()

            // 7. FLASHING (The Main Event)
            onLog(">>> EXECUTE FLASHING...")
            flash()

            onLog("\n--- FLASHING COMPLETE ---")
            onLog("You can reboot now.")
            return@withContext true

        } catch (e: Exception) {
            onLog("\nERROR: ${e.message}")
            android.util.Log.e("ZKM", "Unhandled operation failure", e)
            return@withContext false
        }
    }

    // PATCH: hanya izinkan karakter aman pada nama file yang masuk ke path/shell.
    private fun sanitizeFileName(name: String): String {
        val cleaned = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('.', '_')
        return if (cleaned.isBlank()) "kernel.zip" else cleaned
    }

    // --- ORIGINAL LOGIC FROM Worker.java ---

    private fun rootAvailable(): Boolean {
        return try {
            val ret = runWithNewProcessReturn(true, listOf("id"))
            // PATCH: cek uid=0, bukan contains("root") yang bisa false positive
            ret.contains("uid=0")
        } catch (e: IOException) {
            false
        }
    }

    private fun copyFile() {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(File(filePath)).use { output ->
                input.copyTo(output, bufferSize = 8192)
                output.flush()
            }
        }
    }

    private fun getBinary() {
        val expectedSuffix = "META-INF/com/google/android/update-binary"
        val maxBinarySize = 8L * 1024L * 1024L
        val zip = File(filePath)
        ZipFile(zip).use { archive ->
            val entry = archive.entries().asSequence().firstOrNull { candidate ->
                val normalized = candidate.name.replace('\\', '/')
                !candidate.isDirectory && (normalized == expectedSuffix || normalized.endsWith("/$expectedSuffix"))
            } ?: throw IOException("update-binary not found inside zip")

            if (entry.size > maxBinarySize) {
                throw IOException("update-binary is unexpectedly large")
            }

            val destination = File(binaryPath)
            destination.parentFile?.mkdirs()
            archive.getInputStream(entry).use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 8192)
                }
            }
        }
        if (!File(binaryPath).isFile) throw IOException("update-binary extraction failed")
        File(binaryPath).setExecutable(true, false)
    }

    private fun patch() {
        val mkbootfsPath = "$flasherDir/mkbootfs"
        AssetsUtil.exportFiles(context, "mkbootfs", mkbootfsPath)

        // Command SED asli dari Worker.java — path di sini sepenuhnya app-controlled
        // (flasherDir + nama fixed), jadi aman dilewatkan sebagai argv.
        runWithNewProcessReturn(
            false,
            listOf(
                "sed", "-i",
                "/\$BB chmod -R 755 tools bin;/i cp -f $mkbootfsPath \$AKHOME/tools;",
                binaryPath
            )
        )
    }

    private fun flash() {
        val process = ProcessBuilder("su").redirectErrorStream(true).start()
        val writer = OutputStreamWriter(process.outputStream)
        val reader = BufferedReader(InputStreamReader(process.inputStream))

        // Command original logic — filePath sudah tersanitasi sehingga
        // aman berada di dalam kutip ganda.
        writer.write("export POSTINSTALL=${shellQuote(flasherDir)}\n")
        val debugFlag = if (DEBUG) "-x " else ""
        writer.write("sh $debugFlag${shellQuote(binaryPath)} 3 1 ${shellQuote(filePath)} && touch ${shellQuote("$flasherDir/done")}\nexit\n")
        writer.flush()

        var line: String?
        while (reader.readLine().also { line = it } != null) {
            line?.let { rawLog ->
                // Filter log logic from MainActivity.java
                if (DEBUG) {
                    onLog(rawLog)
                } else if (rawLog.startsWith("ui_print")) {
                    onLog(rawLog.replace("ui_print", "").trim())
                }
            }
        }

        reader.close()
        writer.close()
        // PATCH: reap proses dulu (hindari zombie), baru destroy
        try {
            process.waitFor()
        } finally {
            process.destroy()
        }

        if (!File("$flasherDir/done").exists()) {
            throw IOException("Flashing script did not finish successfully (no 'done' file).")
        }
    }

    private fun cleanup() {
        // PATCH: hapus HANYA folder kerja flasher, bukan rm -rf files/* .
        // Dilakukan via API File, tanpa shell sama sekali.
        File(flasherDir).deleteRecursively()
    }

    // --- SHELL HELPER ---

    // PATCH: menerima argv list, bukan string shell mentah.
    private fun runWithNewProcessReturn(su: Boolean, argv: List<String>): String {
        val command = if (su) listOf("su", "-c") + argv.joinToString(" ") { shellQuote(it) }
        else argv
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val reader = BufferedReader(InputStreamReader(process.inputStream))

        val ret = StringBuilder()
        var tmp: String?
        while (reader.readLine().also { tmp = it } != null) {
            ret.append(tmp).append("\n")
        }
        reader.close()
        try {
            process.waitFor()
        } finally {
            process.destroy()
        }
        return ret.toString()
    }

    // Quote argumen dengan single-quote POSIX (satu-satunya escape yang dibutuhkan: ')
    private fun shellQuote(arg: String): String =
        "'" + arg.replace("'", "'\\''") + "'"

    // Helper reboot static access
    companion object {
        fun rebootDevice() {
            try {
                val p = ProcessBuilder("su").start()
                val w = OutputStreamWriter(p.outputStream)
                w.write("svc power reboot\nexit\n")
                w.flush()
                w.close()
                p.waitFor()
            } catch (e: Exception) {
                android.util.Log.e("ZKM", "Unhandled operation failure", e)
            }
        }
    }
}
