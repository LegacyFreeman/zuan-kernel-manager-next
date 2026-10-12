/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next

import android.app.Application
import com.zuan.kernelmanager.next.core.kernel.KernelRuntimeContext
import com.zuan.kernelmanager.next.core.mode.WorkingModeManager
import com.zuan.kernelmanager.next.library.shell.AxeronShell
import com.zuan.kernelmanager.next.library.shell.PrivilegedAccess
import com.zuan.kernelmanager.next.library.shell.ShizukuShell

/**
 * The project had no Application class, so the working mode and the runtime context were only
 * loaded when MainActivity (or a few specific classes) ran. A service revived by START_STICKY
 * in a fresh process (FPS overlay, live stats ...) therefore saw WorkingMode = BASIC and every
 * Shizuku/Axeron command was rejected with "Not available in Basic mode".
 *
 * Doing it here makes every entry point (activity, service, receiver, tile) see the real mode.
 */
class ZkmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        KernelRuntimeContext.context = applicationContext
        WorkingModeManager.load(applicationContext)
        ShizukuShell.registerListeners(applicationContext)
        AxeronShell.registerListeners(applicationContext)
        PrivilegedAccess.bumpEpoch()

        // Inisialisasi engine performa adaptif (Zkm Perf Tahap 2, 3 & 4)
        com.zuan.kernelmanager.next.ui.components.ZkmQualityGovernor.init(applicationContext)
        com.zuan.kernelmanager.next.core.util.ZkmSamplerBus.init(applicationContext)
    }
}
