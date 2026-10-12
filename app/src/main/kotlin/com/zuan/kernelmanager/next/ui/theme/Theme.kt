/*
 * Copyright (c) 2025-2026 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.zuan.kernelmanager.next.ui.theme

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import com.zuan.kernelmanager.next.feature.settings.AppFontChoice
import com.zuan.kernelmanager.next.feature.settings.SettingsPreference
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Default theme colors
private val DefaultPurple80 = Color(0xFFD0BCFF)
private val DefaultPurpleGrey80 = Color(0xFFCCC2DC)
private val DefaultPink80 = Color(0xFFEFB8C8)
private val DefaultPurple40 = Color(0xFF6650a4)
private val DefaultPurpleGrey40 = Color(0xFF625b71)
private val DefaultPink40 = Color(0xFF7D5260)

private val DarkColorScheme = darkColorScheme(
    primary = DefaultPurple80,
    secondary = DefaultPurpleGrey80,
    tertiary = DefaultPink80
)

// Expressive baseline is intentionally used for the non-dynamic light fallback.
private val LightColorScheme = expressiveLightColorScheme()

/**
 * Creates a color scheme while keeping the expressive component system independent
 * from the user's selected accent colors.
 */
fun createCustomColorScheme(
    darkTheme: Boolean,
    customPrimary: Color?,
    customSecondary: Color?,
    customTertiary: Color?
): androidx.compose.material3.ColorScheme {
    return if (darkTheme) {
        darkColorScheme(
            primary = customPrimary ?: DefaultPurple80,
            secondary = customSecondary ?: DefaultPurpleGrey80,
            tertiary = customTertiary ?: DefaultPink80,
            primaryContainer = (customPrimary ?: DefaultPurple80).copy(alpha = 0.3f),
            secondaryContainer = (customSecondary ?: DefaultPurpleGrey80).copy(alpha = 0.3f),
            tertiaryContainer = (customTertiary ?: DefaultPink80).copy(alpha = 0.3f)
        )
    } else {
        lightColorScheme(
            primary = customPrimary ?: DefaultPurple40,
            secondary = customSecondary ?: DefaultPurpleGrey40,
            tertiary = customTertiary ?: DefaultPink40,
            primaryContainer = (customPrimary ?: DefaultPurple40).copy(alpha = 0.2f),
            secondaryContainer = (customSecondary ?: DefaultPurpleGrey40).copy(alpha = 0.2f),
            tertiaryContainer = (customTertiary ?: DefaultPink40).copy(alpha = 0.2f)
        )
    }
}

/**
 * Single source of truth for the *app-resolved* dark mode.
 *
 * Every component must read this instead of calling
 * [androidx.compose.foundation.isSystemInDarkTheme] directly. The app allows
 * forcing LIGHT/DARK regardless of the system setting, so any component that
 * reads the system flag directly will follow the phone theme instead of the
 * in-app theme (capsule nav bars, cards, custom-background text, ...).
 */
val LocalZkmIsDark = staticCompositionLocalOf { false }

/** Seed regenerated once per app process so Random/Mix shapes change on app restart. */
val LocalZkmIconRandomSeed = staticCompositionLocalOf { 0 }

/**
 * Convenience accessor: `true` when the effective app theme is dark.
 * Falls back to the color scheme luminance when the local was not provided
 * (e.g. previews), which is still theme-aware unlike the raw system flag.
 */
@Composable
fun zkmIsDarkTheme(): Boolean {
    val provided = LocalZkmIsDark.current
    return if (provided) true else MaterialTheme.colorScheme.surface.luminance() < 0.5f
}

/**
 * ZKM's single application-wide Material theme.
 *
 * Material 3 Expressive is now the default rendering system. This is deliberately
 * centralized here so existing Material 3 components automatically receive the
 * current expressive theme tokens where the component supports them. Components
 * that have no expressive counterpart remain normal Material 3 components.
 */
@Composable
fun ZuanKernelManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    customPrimary: Color? = null,
    customSecondary: Color? = null,
    customTertiary: Color? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val baseColorScheme = when {
        customPrimary != null -> {
            createCustomColorScheme(
                darkTheme = darkTheme,
                customPrimary = customPrimary,
                customSecondary = customSecondary,
                customTertiary = customTertiary
            )
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Token permukaan-kartu (surfaceContainer*) diseragamkan dengan palet kartu SetEdit/Home
    // (putih di light, gelap bertint tipis di dark) supaya tidak ada kartu berwarna theme.
    val colorScheme = com.zuan.kernelmanager.next.ui.components.ZkmScreenPalette.applyTo(baseColorScheme, darkTheme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    val prefs = remember { SettingsPreference.getInstance(context) }
    val fontChoice = prefs.appFontChoice.collectAsState().value
    val customFontPath = prefs.customFontPath.collectAsState().value
    val iconShapeStyle = prefs.iconShapeStyle.collectAsState().value
    val iconImagePath = prefs.customIconImagePath.collectAsState().value
    val iconRandomSeed = remember { Random.nextInt() }
    val iconImage: ImageBitmap? = produceState<ImageBitmap?>(initialValue = null, key1 = iconImagePath) {
        val path = iconImagePath
        value = if (path == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
            }
        }
    }.value
    val fontFamily = remember(fontChoice, customFontPath) {
        when (fontChoice) {
            AppFontChoice.APP_DEFAULT -> FontFamily.Default
            AppFontChoice.DEVICE_SYSTEM -> FontFamily(Typeface.create("sans-serif", Typeface.NORMAL))
            AppFontChoice.SERIF -> FontFamily(Typeface.create("serif", Typeface.NORMAL))
            AppFontChoice.MONOSPACE -> FontFamily(Typeface.create("monospace", Typeface.NORMAL))
            AppFontChoice.CUSTOM -> runCatching {
                customFontPath?.let { FontFamily(Typeface.createFromFile(it)) } ?: FontFamily.Default
            }.getOrDefault(FontFamily.Default)
        }
    }

    val typography = MaterialTheme.typography.withFontFamily(fontFamily)

    // Keep MaterialTheme as the source of ColorScheme/Typography consumers while
    // letting MaterialExpressiveTheme provide the updated expressive component
    // defaults, shapes, motion and component behavior underneath.
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = typography
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalZkmIsDark provides darkTheme,
            LocalZkmIconShapeStyle provides iconShapeStyle,
            LocalZkmIconImage provides iconImage,
            LocalZkmIconRandomSeed provides iconRandomSeed
        ) {
            content()
        }
    }
}


private fun androidx.compose.material3.Typography.withFontFamily(fontFamily: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = fontFamily),
    displayMedium = displayMedium.copy(fontFamily = fontFamily),
    displaySmall = displaySmall.copy(fontFamily = fontFamily),
    headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
    headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
    headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
    titleLarge = titleLarge.copy(fontFamily = fontFamily),
    titleMedium = titleMedium.copy(fontFamily = fontFamily),
    titleSmall = titleSmall.copy(fontFamily = fontFamily),
    bodyLarge = bodyLarge.copy(fontFamily = fontFamily),
    bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
    bodySmall = bodySmall.copy(fontFamily = fontFamily),
    labelLarge = labelLarge.copy(fontFamily = fontFamily),
    labelMedium = labelMedium.copy(fontFamily = fontFamily),
    labelSmall = labelSmall.copy(fontFamily = fontFamily)
)
