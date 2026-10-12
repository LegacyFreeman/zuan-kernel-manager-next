/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.dashboard.layout

import androidx.compose.runtime.Composable
import com.zuan.kernelmanager.next.feature.overlay.QuickToolsContent
import com.zuan.kernelmanager.next.ui.components.ZkmDialog
import com.zuan.kernelmanager.next.ui.components.ZkmDialogPanel

/**
 * Quick Tools (dari FAB Overall). Isinya [QuickToolsContent] — sama persis dengan dialog
 * Quick Tools di hybrid tabs. Bedanya hanya wadahnya: ZkmDialog + ZkmDialogPanel (pola yang
 * sama dengan dialog Core CPU di Overall) sehingga efek blur-nya identik.
 */
@Composable
fun QuickToolsDialog(onDismiss: () -> Unit) {
    ZkmDialog(onDismissRequest = onDismiss) {
        ZkmDialogPanel {
            QuickToolsContent(onDismiss = onDismiss)
        }
    }
}
