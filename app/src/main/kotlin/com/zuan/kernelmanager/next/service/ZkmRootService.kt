/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.service

import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.util.Log
import com.topjohnwu.superuser.ipc.RootService
import com.zuan.kernelmanager.next.BuildConfig
import com.zuan.kernelmanager.next.IZkmRootService
import java.io.File
import java.io.IOException

/**
 * Root-side privileged transport for ZKM Kernel Engine.
 *
 * The public surface is node-oriented. Diagnostic file operations are debug-only
 * and restricted to an exact temporary directory so tests never need access to
 * arbitrary filesystem paths.
 */
class ZkmRootService : RootService() {
    companion object {
        private const val TAG = "ZkmRootService"
        private const val MAX_PATHS_PER_BATCH = 96
        private const val MAX_NODE_BYTES = 64 * 1024
        private const val DIAG_PREFIX = "/data/local/tmp/zkm-next-diag-"

        private val ALLOWED_PREFIXES = listOf(
            "/sys/",
            "/proc/",
            "/dev/cpuset/",
            "/dev/stune/",
            "/dev/cpuctl/",
            "/sys/fs/cgroup/"
        )

        private fun resolveAllowed(path: String): File? {
            if (path.isBlank()) return null
            return try {
                val canonical = File(path).canonicalFile
                val cp = canonical.path
                if (ALLOWED_PREFIXES.any { prefix -> cp == prefix.dropLast(1) || cp.startsWith(prefix) }) {
                    canonical
                } else {
                    Log.w(TAG, "Rejected privileged path: $path -> $cp")
                    null
                }
            } catch (e: IOException) {
                Log.w(TAG, "Canonicalization failed for $path", e)
                null
            }
        }

        private fun resolveDiagnostic(path: String): File? {
            if (!BuildConfig.DEBUG || !path.startsWith(DIAG_PREFIX)) return null
            return try {
                val canonical = File(path).canonicalFile
                if (canonical.path.startsWith(DIAG_PREFIX)) canonical else null
            } catch (_: Exception) {
                null
            }
        }

        private fun readFile(file: File): String {
            return try {
                if (!file.exists() || !file.isFile) return ""
                file.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    val output = java.io.ByteArrayOutputStream(minOf(MAX_NODE_BYTES, 8192))
                    var total = 0
                    while (total < MAX_NODE_BYTES) {
                        val count = input.read(buffer, 0, minOf(buffer.size, MAX_NODE_BYTES - total))
                        if (count <= 0) break
                        output.write(buffer, 0, count)
                        total += count
                    }
                    output.toString(Charsets.UTF_8.name()).trim()
                }
            } catch (e: Exception) {
                Log.d(TAG, "Read failed: ${file.path}", e)
                ""
            }
        }
    }

    override fun onBind(intent: Intent): IBinder = object : IZkmRootService.Stub() {
        override fun ping(): Boolean = true

        override fun crashForDiagnostics(): Boolean {
            if (!BuildConfig.DEBUG) return false
            Handler(Looper.getMainLooper()).postDelayed({
                Log.w(TAG, "Diagnostic crash requested; terminating root daemon")
                Process.killProcess(Process.myPid())
            }, 150L)
            return true
        }

        override fun readNode(path: String): String = resolveAllowed(path)?.let(::readFile).orEmpty()

        override fun readNodes(paths: Array<String>): Array<String> {
            val count = minOf(paths.size, MAX_PATHS_PER_BATCH)
            return Array(count) { index -> resolveAllowed(paths[index])?.let(::readFile).orEmpty() }
        }

        override fun writeNode(path: String, value: String): Boolean {
            val file = resolveAllowed(path) ?: return false
            if (!com.zuan.kernelmanager.next.core.kernel.KernelNodePolicy.isWriteAllowed(file.path)) return false
            return writeKernelFile(file, value)
        }

        override fun writeNodes(paths: Array<String>, values: Array<String>): BooleanArray {
            val count = minOf(paths.size, values.size, MAX_PATHS_PER_BATCH)
            return BooleanArray(count) { index ->
                val file = resolveAllowed(paths[index])
                file != null && com.zuan.kernelmanager.next.core.kernel.KernelNodePolicy.isWriteAllowed(file.path) && writeKernelFile(file, values[index])
            }
        }

        private fun writeKernelFile(file: File, value: String): Boolean {
            return try {
                if (!file.exists() || !file.isFile) return false
                file.writeText(value)
                true
            } catch (e: Exception) {
                Log.w(TAG, "Write failed: ${file.path}", e)
                false
            }
        }

        override fun nodeExists(path: String): Boolean = resolveAllowed(path)?.exists() == true

        override fun nodeExistsBatch(paths: Array<String>): BooleanArray {
            val count = minOf(paths.size, MAX_PATHS_PER_BATCH)
            return BooleanArray(count) { index -> resolveAllowed(paths[index])?.exists() == true }
        }

        override fun nodeWritable(path: String): Boolean {
            val file = resolveAllowed(path) ?: return false
            return file.exists() && file.canWrite()
        }

        override fun nodeWritableBatch(paths: Array<String>): BooleanArray {
            val count = minOf(paths.size, MAX_PATHS_PER_BATCH)
            return BooleanArray(count) { index ->
                val file = resolveAllowed(paths[index])
                file != null && file.exists() && file.canWrite()
            }
        }

        override fun listDirectories(path: String): MutableList<String> {
            val file = resolveAllowed(path) ?: return mutableListOf()
            return try {
                if (!file.isDirectory) return mutableListOf()
                file.listFiles()?.filter { it.isDirectory }?.map { it.name }?.toMutableList()
                    ?: mutableListOf()
            } catch (e: Exception) {
                Log.d(TAG, "List failed: $path", e)
                mutableListOf()
            }
        }

        override fun diagnosticWriteFile(path: String, value: String): Boolean {
            val file = resolveDiagnostic(path) ?: return false
            return try {
                if (value.toByteArray(Charsets.UTF_8).size > MAX_NODE_BYTES) return false
                file.parentFile?.mkdirs()
                file.writeText(value)
                true
            } catch (e: Exception) {
                Log.d(TAG, "Diagnostic write failed: $path", e)
                false
            }
        }

        override fun diagnosticReadFile(path: String): String = resolveDiagnostic(path)?.let(::readFile).orEmpty()

        override fun diagnosticDeleteFile(path: String): Boolean {
            val file = resolveDiagnostic(path) ?: return false
            return try { !file.exists() || file.delete() } catch (_: Exception) { false }
        }
    }
}
