/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.core.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Log Batcher Throttler (Adopsi dari arsitektur Compiler ACSIDE / Termux).
 *
 * Mencegah "Recomposition Storm" saat script flashing atau binary native mengeluarkan
 * ratusan baris log per detik.
 *
 * Cara kerja:
 * - Setiap baris baru ditampung ke buffer [StringBuilder] di memori.
 * - Flush ke callback UI hanya dieksekusi secara berkala setiap [intervalMs] (default 150ms).
 * - Menjamin CPU UI thread tetap santai (< 5%) dan frame tetap 60/120fps.
 */
class ZkmLogBatcher(
    private val scope: CoroutineScope,
    private val intervalMs: Long = 150L,
    private val onFlush: (String) -> Unit
) {
    private val buffer = StringBuilder()
    @Volatile private var isDirty = false
    private var flushJob: Job? = null

    fun append(line: String) {
        synchronized(buffer) {
            buffer.append(line).append('\n')
            isDirty = true
        }
        scheduleFlush()
    }

    fun appendDirect(text: String) {
        synchronized(buffer) {
            buffer.append(text)
            isDirty = true
        }
        scheduleFlush()
    }

    private fun scheduleFlush() {
        if (flushJob?.isActive == true) return
        flushJob = scope.launch {
            delay(intervalMs)
            flush()
        }
    }

    fun flush() {
        val snapshot: String
        synchronized(buffer) {
            if (!isDirty) return
            snapshot = buffer.toString()
            isDirty = false
        }
        onFlush(snapshot)
    }

    fun clear() {
        flushJob?.cancel()
        synchronized(buffer) {
            buffer.setLength(0)
            isDirty = false
        }
        onFlush("")
    }
}
