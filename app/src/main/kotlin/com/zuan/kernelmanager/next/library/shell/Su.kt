/*
 * Copyright (c) 2025 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.library.shell

import android.os.Looper

import com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime

data class SuResult(
    val isSuccess: Boolean,
    val out: List<String>,
    val err: List<String>,
    val code: Int
) {
    val outputString: String get() = out.joinToString("\n").trim()
    val errorString: String get() = err.joinToString("\n").trim()
}

/**
 * Central suspend root command API. All blocking execution is moved to IO.
 */
suspend fun su(vararg cmds: String): SuResult = KernelRootRuntime.execute(*cmds)

/**
 * Legacy synchronous API. Call only from a worker thread.
 */
fun suBlocking(vararg cmds: String): SuResult {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        return SuResult(
            isSuccess = false,
            out = emptyList(),
            err = listOf("suBlocking() must not be called from the main thread"),
            code = -2
        )
    }
    return KernelRootRuntime.executeBlocking(*cmds)
}
