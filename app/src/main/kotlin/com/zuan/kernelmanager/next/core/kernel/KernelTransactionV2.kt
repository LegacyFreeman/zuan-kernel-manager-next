package com.zuan.kernelmanager.next.core.kernel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Transaction V2: serialized journal with preflight, capture, apply, verify and
 * rollback. All kernel I/O stays suspend/IO-bound; no UI-thread blocking is
 * introduced by the transaction layer.
 */
object KernelTransactionV2 {
    enum class Phase { PREFLIGHT, CAPTURE, APPLY, VERIFY, COMMIT, ROLLBACK, COMPLETE }

    data class Entry(
        val path: String,
        val before: String,
        val requested: String,
        val observed: String = "",
        val ok: Boolean = false
    )

    data class Result(
        val phase: Phase,
        val success: Boolean,
        val committed: Boolean,
        val rollbackAttempted: Boolean,
        val rollbackVerified: Boolean,
        val entries: List<Entry>,
        val error: String? = null
    )

    private const val MAX_ENTRIES = 96
    private const val MAX_VALUE_LENGTH = 512
    private val mutex = Mutex()

    suspend fun execute(writes: List<Pair<String, String>>, verify: Boolean = true): Result =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val normalized = writes
                    .map { KernelNodePolicy.normalizeLegacyPath(it.first) to it.second.replace('\u0000', ' ').trim() }
                    .distinctBy { it.first }
                    .take(MAX_ENTRIES)

                if (writes.size > MAX_ENTRIES) {
                    return@withContext Result(
                        Phase.PREFLIGHT, false, false, false, false, emptyList(),
                        "Transaction exceeds $MAX_ENTRIES entries"
                    )
                }
                if (normalized.isEmpty()) {
                    return@withContext Result(Phase.COMPLETE, true, true, false, true, emptyList())
                }

                val invalid = normalized.firstOrNull { (path, value) ->
                    val canonical = KernelNodePolicy.canonical(path)
                    canonical == null || canonical != path ||
                        !KernelNodePolicy.isWriteAllowed(path) ||
                        value.isBlank() || value.length > MAX_VALUE_LENGTH
                }
                if (invalid != null) {
                    return@withContext Result(
                        Phase.PREFLIGHT, false, false, false, false, emptyList(),
                        "Blocked/invalid node: ${invalid.first}"
                    )
                }

                val paths = normalized.map { it.first }
                val before = KernelRootRuntime.readNodes(paths)
                if (before.size != paths.size) {
                    return@withContext Result(Phase.CAPTURE, false, false, false, false, emptyList(), "Capture incomplete")
                }

                var entries = normalized.map { (path, value) ->
                    Entry(path, before.getValue(path), value)
                }

                val outcome = KernelRootRuntime.writeNodes(paths, normalized.map { it.second })
                val failed = outcome.entries.firstOrNull { it.value != true }?.key
                if (failed != null || outcome.size != paths.size) {
                    val rb = rollback(before)
                    return@withContext Result(
                        Phase.ROLLBACK, false, false, true, rb, entries,
                        "Write failed${failed?.let { ": $it" } ?: ""}"
                    )
                }

                if (verify) {
                    val observed = KernelRootRuntime.readNodes(paths)
                    entries = entries.map { entry ->
                        val value = observed[entry.path].orEmpty()
                        entry.copy(
                            observed = value,
                            ok = normalize(value) == normalize(entry.requested)
                        )
                    }
                    if (entries.any { !it.ok } || observed.size != paths.size) {
                        val rb = rollback(before)
                        return@withContext Result(
                            Phase.ROLLBACK, false, false, true, rb, entries,
                            "Verification failed"
                        )
                    }
                } else {
                    entries = entries.map { it.copy(observed = it.requested, ok = true) }
                }

                Result(Phase.COMPLETE, true, true, false, true, entries)
            }
        }

    private suspend fun rollback(before: Map<String, String>): Boolean = withContext(Dispatchers.IO) {
        if (before.isEmpty()) return@withContext true
        val safe = before.filterKeys { KernelNodePolicy.isWriteAllowed(it) }
        if (safe.size != before.size) return@withContext false
        runCatching {
            val out = KernelRootRuntime.writeNodes(safe.keys.toList(), safe.values.toList())
            if (out.size != safe.size || safe.keys.any { out[it] != true }) return@runCatching false
            val observed = KernelRootRuntime.readNodes(safe.keys.toList())
            observed.size == safe.size && safe.all { (path, expected) ->
                normalize(observed[path]) == normalize(expected)
            }
        }.getOrDefault(false)
    }

    private fun normalize(value: String?): String =
        value.orEmpty().trim().replace("\r", "").replace("\n", " ").replace(Regex("\\s+"), " ")
}
