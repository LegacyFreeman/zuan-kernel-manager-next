/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.core.mode

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.StringRes
import com.zuan.kernelmanager.next.R
import java.util.concurrent.atomic.AtomicLong

@StringRes
fun WorkingMode.nameRes(): Int = when (this) {
    WorkingMode.BASIC -> R.string.mode_basic_name
    WorkingMode.ADB -> R.string.mode_adb_name
    WorkingMode.ROOT -> R.string.mode_root_name
}

/** Throttled toast telling the user an action is not available in the current working mode. */
object ModeNotice {
    private val last = AtomicLong(0L)
    private val main = Handler(Looper.getMainLooper())

    fun denied(context: Context?, needed: WorkingMode) {
        val app = context?.applicationContext ?: return
        val now = System.currentTimeMillis()
        if (now - last.get() < 3000L) return
        last.set(now)
        main.post {
            runCatching {
                Toast.makeText(
                    app,
                    app.getString(R.string.mode_action_needs, app.getString(needed.nameRes())),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
