package com.zuan.kernelmanager.next.core.kernel

import android.content.Context
import android.os.Debug
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Read-only benchmark with warm-up, repeated single/batch samples and contention checks. */
object KernelBenchmarkV2 {
    data class Result(
        val samplesMs: List<Long>,
        val p50Ms: Long,
        val p95Ms: Long,
        val minMs: Long,
        val maxMs: Long,
        val batchP50Ms: Long,
        val speedup: Float,
        val concurrentFailures: Int,
        val runtimeFailureDelta: Long,
        val pssDeltaKb: Int,
        val passed: Boolean,
        val batchSamplesMs: List<Long> = emptyList(),
        val batchP95Ms: Long = 0L
    )

    private val paths = listOf(
        "/proc/uptime", "/proc/stat", "/proc/meminfo", "/proc/loadavg",
        "/proc/version", "/proc/sys/kernel/osrelease", "/proc/sys/kernel/pid_max"
    )

    suspend fun run(context: Context, rounds: Int = 10): Result = withContext(Dispatchers.IO) {
        KernelRootRuntime.initialize(context.applicationContext)
        repeat(3) { KernelRootRuntime.readNodes(paths) }

        val before = KernelRootRuntime.snapshotStats()
        val pss0 = Debug.getPss().toInt()
        val singles = mutableListOf<Long>()
        val batches = mutableListOf<Long>()
        val count = rounds.coerceIn(3, 30)

        repeat(count) {
            val singleStart = SystemClock.elapsedRealtimeNanos()
            paths.forEach { KernelRootRuntime.readNode(it) }
            singles += (SystemClock.elapsedRealtimeNanos() - singleStart) / 1_000_000L

            val batchStart = SystemClock.elapsedRealtimeNanos()
            KernelRootRuntime.readNodes(paths)
            batches += (SystemClock.elapsedRealtimeNanos() - batchStart) / 1_000_000L
        }

        val concurrentFailures = coroutineScope {
            (0 until 8).map {
                async(Dispatchers.IO) {
                    var failures = 0
                    repeat(16) {
                        if (KernelRootRuntime.readNode("/proc/uptime").isBlank()) failures++
                    }
                    failures
                }
            }.awaitAll().sum()
        }

        val after = KernelRootRuntime.snapshotStats()
        fun pct(values: List<Long>, p: Double): Long {
            if (values.isEmpty()) return 0L
            val sorted = values.sorted()
            return sorted[((sorted.size - 1) * p).toInt().coerceIn(0, sorted.lastIndex)]
        }

        val p50 = pct(singles, .50)
        val batchP50 = pct(batches, .50)
        val passed = concurrentFailures == 0 &&
            after.failures == before.failures &&
            after.daemonConnected &&
            singles.all { it >= 0L } &&
            batches.all { it >= 0L }

        Result(
            samplesMs = singles,
            p50Ms = p50,
            p95Ms = pct(singles, .95),
            minMs = singles.minOrNull() ?: 0L,
            maxMs = singles.maxOrNull() ?: 0L,
            batchP50Ms = batchP50,
            speedup = if (batchP50 > 0L) p50.toFloat() / batchP50 else 0f,
            concurrentFailures = concurrentFailures,
            runtimeFailureDelta = after.failures - before.failures,
            pssDeltaKb = Debug.getPss().toInt() - pss0,
            passed = passed,
            batchSamplesMs = batches,
            batchP95Ms = pct(batches, .95)
        )
    }
}
