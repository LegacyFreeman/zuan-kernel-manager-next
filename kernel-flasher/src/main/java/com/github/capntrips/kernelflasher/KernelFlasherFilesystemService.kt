package com.github.capntrips.kernelflasher

import android.content.Intent
import android.os.IBinder
import com.topjohnwu.superuser.ipc.RootService
import com.topjohnwu.superuser.nio.FileSystemManager

/**
 * Root service bridge for libsu FileSystemManager.
 *
 * Returning the FileSystemManager binder directly avoids an unnecessary
 * generated AIDL wrapper and keeps the service compatible with libsu's
 * RootService IPC contract.
 */
class KernelFlasherFilesystemService : RootService() {
    override fun onBind(intent: Intent): IBinder {
        return FileSystemManager.getService()
    }
}
