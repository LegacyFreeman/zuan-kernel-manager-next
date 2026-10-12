package com.zuan.kernelmanager.next.platform.receiver // Sesuaikan nama package-nya

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zuan.kernelmanager.next.core.kernel.KernelCore
import com.zuan.kernelmanager.next.core.kernel.KernelTuningMigration
import com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime
import com.zuan.kernelmanager.next.core.kernel.KernelRebootValidation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ZkmBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        val pendingResult = goAsync()

        KernelRebootValidation.noteBoot(context.applicationContext)

        // Game Space: hidupkan lagi pengawas game bila saklarnya aktif (hanya setelah unlock, prefs butuh storage terbuka).
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            runCatching {
                if (com.zuan.kernelmanager.next.feature.gamespace.GameSpacePrefs.isEnabled(context)) {
                    com.zuan.kernelmanager.next.feature.gamespace.GameDetectorService.start(context.applicationContext)
                }
            }
        }

        // Bind the daemon from the receiver's main thread; the actual tweak work
        // remains on IO below. This also warms capability discovery early at boot.
        // Central lifecycle owner restores profiles + automation through KernelEngine.
        KernelCore.initialize(context.applicationContext)

        // Legacy tuning migration is compatibility-only and runs after the central
        // engine has been brought up; it never becomes a second kernel-control path.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val root = KernelRootRuntime.execute("id")
                if (root.isSuccess && root.outputString.contains("uid=0")) {
                    KernelTuningMigration.applyPersisted(context.applicationContext)
                }
            } catch (e: Exception) {
                android.util.Log.e("ZKM", "Unhandled operation failure", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}