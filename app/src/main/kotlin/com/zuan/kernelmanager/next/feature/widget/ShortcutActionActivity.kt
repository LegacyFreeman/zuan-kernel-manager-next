/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 *
 * Invisible trampoline for app shortcuts (long-press the launcher icon) that should run an
 * action without opening the UI. It forwards the action to [WidgetActionReceiver] and exits.
 */
package com.zuan.kernelmanager.next.feature.widget

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class ShortcutActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent?.getStringExtra("zkm_action")
        if (action != null) {
            val i = Intent(this, WidgetActionReceiver::class.java).setAction(action)
            if (intent.hasExtra(ZkmWidgetKit.EXTRA_MODE)) {
                i.putExtra(ZkmWidgetKit.EXTRA_MODE, intent.getIntExtra(ZkmWidgetKit.EXTRA_MODE, 0))
            }
            sendBroadcast(i)
        }
        finish()
    }
}
