/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.ui.components.BlurredAlertDialog

/**
 * Self-contained help button for a screen's top bar.
 *
 * Looks up the [HelpTopic] registered for [route] and opens the standard
 * ZKM glass dialog with the explanation. Renders nothing when the route has
 * no topic, so adding it to a screen is always safe.
 */
@Composable
fun ZkmHelpButton(
    route: String,
    tint: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
) {
    val topic = HelpContent.forRoute(route) ?: return
    var showHelp by remember { mutableStateOf(false) }
    val iconTint = if (tint == Color.Unspecified) LocalContentColor.current else tint

    IconButton(onClick = { showHelp = true }, modifier = modifier) {
        Icon(
            imageVector = Icons.Rounded.HelpOutline,
            contentDescription = stringResource(R.string.help_a11y),
            tint = iconTint,
        )
    }

    if (showHelp) {
        ZkmHelpDialog(topic = topic, onDismiss = { showHelp = false })
    }
}

/**
 * Standard ZKM help dialog: what the menu does, how to use it, and an
 * optional safety warning. Uses the shared glass dialog look.
 */
@Composable
fun ZkmHelpDialog(
    topic: HelpTopic,
    onDismiss: () -> Unit,
) {
    BlurredAlertDialog(
        scrollableText = false,
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.help_got_it),
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        icon = {
            Icon(
                imageVector = Icons.Rounded.HelpOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                text = stringResource(topic.titleRes),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            // scrollableText=false: konten scroll di sini. Tinggi dibatasi oleh frame dialog
            // (ruang tersisa setelah taskbar/status bar), jadi tombol "Mengerti" selalu terlihat
            // di Rotation 16:9 maupun PC mode.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.help_section_about),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(topic.descRes),
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                )

                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.help_section_howto),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
                topic.bulletRes.forEach { res ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "•",
                            modifier = Modifier.padding(end = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(res),
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                        )
                    }
                }

                if (topic.warningRes != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(top = 1.dp),
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                text = stringResource(R.string.help_section_warning),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = stringResource(topic.warningRes),
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                            )
                        }
                    }
                }
            }
        },
    )
}
