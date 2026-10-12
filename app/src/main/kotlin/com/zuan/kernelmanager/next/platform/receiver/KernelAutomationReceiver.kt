package com.zuan.kernelmanager.next.platform.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zuan.kernelmanager.next.core.kernel.KernelCore
import com.zuan.kernelmanager.next.core.kernel.KernelEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Restores the kernel engine and applies a persisted timed profile. */
class KernelAutomationReceiver : BroadcastReceiver() {
    companion object { const val EXTRA_SCHEDULE_ID = "schedule_id" }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_SCHEDULE_ID)?.takeIf { it.isNotBlank() } ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                KernelEngine.initialize(context.applicationContext)
                KernelCore.automation.fireTimedSchedule(context.applicationContext, id)
            } finally {
                pending.finish()
            }
        }
    }
}
