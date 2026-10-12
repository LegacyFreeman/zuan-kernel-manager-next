/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package rikka.shizuku

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import moe.shizuku.api.BinderContainer

/**
 * ContentProvider for receiving Binder from Shizuku server.
 * Uses the Shizuku API bundled within Axeron-API.
 */
class ShizukuProvider : ContentProvider() {

    override fun attachInfo(context: Context, info: ProviderInfo) {
        super.attachInfo(context, info)
        if (info.multiprocess) throw IllegalStateException("android:multiprocess must be false")
        if (!info.exported) throw IllegalStateException("android:exported must be true")
    }

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (extras == null) return null
        extras.classLoader = BinderContainer::class.java.classLoader

        val reply = Bundle()
        when (method) {
            METHOD_SEND_BINDER -> handleSendBinder(extras)
            METHOD_GET_BINDER -> if (!handleGetBinder(reply)) return null
        }
        return reply
    }

    private fun handleSendBinder(extras: Bundle) {
        if (Shizuku.pingBinder()) return
        val container: BinderContainer? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(EXTRA_BINDER, BinderContainer::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(EXTRA_BINDER)
        }
        if (container?.binder != null) {
            Shizuku.onBinderReceived(container.binder, context?.packageName)
        }
    }

    private fun handleGetBinder(reply: Bundle): Boolean {
        val binder: IBinder? = Shizuku.getBinder()
        if (binder == null || !binder.pingBinder()) return false
        reply.putParcelable(EXTRA_BINDER, BinderContainer(binder))
        return true
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0

    companion object {
        const val METHOD_SEND_BINDER = "sendBinder"
        const val METHOD_GET_BINDER = "getBinder"
        private const val EXTRA_BINDER = "moe.shizuku.privileged.api.intent.extra.BINDER"
    }
}
