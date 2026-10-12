/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.zuan.kernelmanager.next.feature.performance
import com.zuan.kernelmanager.next.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.zuan.kernelmanager.next.ui.components.ZkmSlider

@Composable
fun PerformanceScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lp_performance)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }
        ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.lp_a11y_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // CPU Governor Card
            PerformanceCard(title = "CPU Governor", icon = Icons.Default.Speed) {
                Text(stringResource(R.string.lp_little_cluster_schedutil), style = MaterialTheme.typography.bodyMedium)
                ZkmSlider(value = 0.8f, onValueChange = {})
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.lp_big_cluster_schedutil), style = MaterialTheme.typography.bodyMedium)
                ZkmSlider(value = 0.6f, onValueChange = {})
            }

            // GPU Frequency Card
            PerformanceCard(title = "GPU Frequency", icon = Icons.Default.Speed) {
                Text(stringResource(R.string.lp_max_freq_850_mhz), style = MaterialTheme.typography.bodyMedium)
                ZkmSlider(value = 0.9f, onValueChange = {})
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.lp_min_freq_300_mhz), style = MaterialTheme.typography.bodyMedium)
                ZkmSlider(value = 0.2f, onValueChange = {})
            }

            // I/O Scheduler Card
            PerformanceCard(title = "I/O Scheduler", icon = Icons.Default.Speed) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.lp_scheduler), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { }
        ) {
                        Text(stringResource(R.string.lp_cfq))
                    }
                }
            }
        }
    }
}

@Composable
fun PerformanceCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}