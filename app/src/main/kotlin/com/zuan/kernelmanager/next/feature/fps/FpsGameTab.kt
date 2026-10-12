/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.fps

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.zuan.kernelmanager.next.R
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.zuan.kernelmanager.next.ui.components.zkmGlassCard
import com.zuan.kernelmanager.next.ui.components.zkmGlassTint
import com.zuan.kernelmanager.next.ui.components.LocalZkmHazeState
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable
)

/** Downscale ikon aplikasi ke [sizePx] agar hemat memori. Lihat AppManager. */
private fun ApplicationInfo.loadScaledIcon(pm: PackageManager, sizePx: Int): Drawable {
    val icon = loadIcon(pm)
    return runCatching {
        val bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        icon.setBounds(0, 0, sizePx, sizePx)
        icon.draw(canvas)
        @Suppress("DEPRECATION")
        android.graphics.drawable.BitmapDrawable(null, bitmap)
    }.getOrDefault(icon)
}

@Composable
fun FpsManagerGamesContent(
    contentColor: Color, 
    subContentColor: Color,
    isGlassActive: Boolean = false,
    hazeState: HazeState,
    topPadding: Dp // DITAMBAHKAN
) {
    val context = LocalContext.current
    var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm: PackageManager = context.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            
            val filtered = apps.filter { appInfo: ApplicationInfo ->
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val isGame = (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
                
                (isGame || (!isSystem || isUpdatedSystem)) && appInfo.packageName != context.packageName
            }.mapNotNull { appInfo: ApplicationInfo ->
                if (pm.getLaunchIntentForPackage(appInfo.packageName) != null) {
                    AppInfo(
                        label = appInfo.loadLabel(pm).toString(),
                        packageName = appInfo.packageName,
                        // [PERF] downscale ikon ke 144px: ikon launcher modern
                        // bisa 432px+; menyimpan versi mentah untuk puluhan
                        // aplikasi memakan puluhan MB dan membuat grid berat.
                        icon = appInfo.loadScaledIcon(pm, 144)
                    )
                } else null
            }.sortedBy { it.label }
            
            installedApps = filtered
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // Beri jarak di atas menggunakan Spacer karena LazyVerticalGrid ada di bawah text
        Spacer(Modifier.height(topPadding + 16.dp))
        Text(
            stringResource(R.string.fps_game_launcher),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
        Text(
            stringResource(R.string.fps_game_launcher_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = subContentColor
        )
        Spacer(Modifier.height(16.dp))

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(installedApps, key = { it.packageName }) { app ->
                    GameCard(app, contentColor, isGlassActive)
                }
            }
        }
    }
}

@Composable
fun GameCard(app: AppInfo, textColor: Color, isGlassActive: Boolean = false) {
    val context = LocalContext.current
    
    val cardColors = if (isGlassActive) {
        CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
    
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = cardColors,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .then(
                if (isGlassActive) Modifier.zkmGlassCard(
                    isGlassActive = true,
                    hazeState = LocalZkmHazeState.current,
                    shape = RoundedCornerShape(16.dp),
                    tint = zkmGlassTint(MaterialTheme.colorScheme.surfaceContainerHigh, zkmIsDarkTheme(), 0.45f),
                    blurRadius = 20.dp
                ) else Modifier
            ),
        onClick = {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            launchIntent?.let { context.startActivity(it) }
        }
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                bitmap = app.icon.toBitmap().asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
