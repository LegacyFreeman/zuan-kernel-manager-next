/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package com.zuan.kernelmanager.next.feature.help

import com.zuan.kernelmanager.next.ui.adaptive.zkmLargeAppBarExpandedHeight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.feature.settings.BgType
import com.zuan.kernelmanager.next.feature.settings.SettingsViewModel
import com.zuan.kernelmanager.next.feature.settings.generateThemedBackgroundColor
import com.zuan.kernelmanager.next.ui.components.ZkmAppBarProgressive
import com.zuan.kernelmanager.next.ui.components.zkmAppBarHazeStyle
import com.zuan.kernelmanager.next.ui.components.zkmGlassCard
import com.zuan.kernelmanager.next.ui.components.zkmGlassFallbackTint
import com.zuan.kernelmanager.next.ui.theme.ThemeMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

/**
 * Help & Documentation center: every menu explained, plus general FAQ.
 * Content comes from [HelpContent] (strings.xml, language-pack translatable).
 */
@Composable
fun HelpCenterScreen(
    navController: NavController,
    hazeState: HazeState? = null,
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val isCollapsed by remember { derivedStateOf { scrollBehavior.state.collapsedFraction > 0.5f } }

    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val isSystemDark = isSystemInDarkTheme()
    val useDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM_DEFAULT -> isSystemDark
    }

    val isDynamic by settingsViewModel.isDynamicColor.collectAsStateWithLifecycle()
    val themeColorName by settingsViewModel.currentThemeColor.collectAsStateWithLifecycle()
    val isCustomColor by settingsViewModel.isCustomColor.collectAsStateWithLifecycle()
    val customPrimary by settingsViewModel.customPrimaryColor.collectAsStateWithLifecycle()
    val isHazeEnabled by settingsViewModel.isHazeEnabled.collectAsStateWithLifecycle()
    val bgType by settingsViewModel.bgType.collectAsStateWithLifecycle()
    val isCustomBg = bgType == BgType.GALLERY

    val effectivePrimary = remember(isDynamic, themeColorName, isCustomColor, customPrimary) {
        when {
            isCustomColor -> Color(customPrimary)
            isDynamic -> Color.Unspecified
            else -> themeColorName.primary
        }
    }
    val finalPrimary = if (effectivePrimary == Color.Unspecified) {
        MaterialTheme.colorScheme.primary
    } else {
        effectivePrimary
    }
    val (tintedBackground, _) = generateThemedBackgroundColor(finalPrimary, useDarkTheme)
    val headerHazeStyle = zkmAppBarHazeStyle(tintedBackground, isCustomBg)

    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    var query by remember { mutableStateOf("") }

    // Resolve all strings once so search works on visible text.
    val resolvedTopics = HelpContent.topics.map { topic ->
        ResolvedTopic(
            route = topic.route,
            title = stringResource(topic.titleRes),
            desc = stringResource(topic.descRes),
            bullets = topic.bulletRes.map { stringResource(it) },
            warning = topic.warningRes?.let { stringResource(it) },
        )
    }
    val resolvedFaqs = HelpContent.faqs.map { faq ->
        ResolvedFaq(
            title = stringResource(faq.titleRes),
            body = stringResource(faq.bodyRes),
        )
    }

    val filteredTopics = remember(query, resolvedTopics) {
        if (query.isBlank()) resolvedTopics
        else resolvedTopics.filter {
            it.title.contains(query, true) || it.desc.contains(query, true)
        }
    }
    val filteredFaqs = remember(query, resolvedFaqs) {
        if (query.isBlank()) resolvedFaqs
        else resolvedFaqs.filter {
            it.title.contains(query, true) || it.body.contains(query, true)
        }
    }

    // Haze app bar hanya dipasang bila ada sumber haze; tanpa itu style
    // tetap valid tapi tidak ada yang di-blur, jadi modifier dilewati.
    val appBarModifier = if (hazeState != null) {
        Modifier.hazeEffect(
            state = hazeState,
            style = headerHazeStyle,
        ) { progressive = ZkmAppBarProgressive }
    } else {
        Modifier
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                expandedHeight = zkmLargeAppBarExpandedHeight(),
                title = {
                    Text(
                        text = stringResource(R.string.help_center_title),
                        style = if (isCollapsed) {
                            MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        } else {
                            MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp,
                            )
                        },
                        color = onSurfaceColor,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null,
                            tint = onSurfaceColor,
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    actionIconContentColor = onSurfaceColor,
                    navigationIconContentColor = onSurfaceColor,
                ),
                scrollBehavior = scrollBehavior,
                modifier = appBarModifier,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.help_search_hint)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(18.dp),
                )
            }

            item(key = "menu_header") {
                HelpSectionHeader(
                    icon = { Icon(Icons.Rounded.MenuBook, null, tint = finalPrimary) },
                    text = stringResource(R.string.help_section_menus),
                    color = onSurfaceColor,
                )
            }

            if (filteredTopics.isEmpty() && filteredFaqs.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.help_no_results),
                        color = onSurfaceVariantColor,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }

            items(
                count = filteredTopics.size,
                key = { filteredTopics[it].route },
            ) { index ->
                val topic = filteredTopics[index]
                HelpTopicCard(
                    topic = topic,
                    isGlassActive = isHazeEnabled,
                    hazeState = hazeState,
                    isDark = useDarkTheme,
                    accentColor = finalPrimary,
                    onSurfaceColor = onSurfaceColor,
                    onSurfaceVariantColor = onSurfaceVariantColor,
                )
            }

            item(key = "faq_header") {
                HelpSectionHeader(
                    icon = { Icon(Icons.Rounded.HelpOutline, null, tint = finalPrimary) },
                    text = stringResource(R.string.help_section_faq),
                    color = onSurfaceColor,
                )
            }

            items(
                count = filteredFaqs.size,
                key = { "faq_" + filteredFaqs[it].title },
            ) { index ->
                HelpFaqCard(
                    faq = filteredFaqs[index],
                    isGlassActive = isHazeEnabled,
                    hazeState = hazeState,
                    isDark = useDarkTheme,
                    accentColor = finalPrimary,
                    onSurfaceColor = onSurfaceColor,
                    onSurfaceVariantColor = onSurfaceVariantColor,
                )
            }
        }
    }
}

private data class ResolvedTopic(
    val route: String,
    val title: String,
    val desc: String,
    val bullets: List<String>,
    val warning: String?,
)

private data class ResolvedFaq(
    val title: String,
    val body: String,
)

@Composable
private fun HelpSectionHeader(
    icon: @Composable () -> Unit,
    text: String,
    color: Color,
) {
    Row(
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = color,
        )
    }
}

@Composable
private fun HelpTopicCard(
    topic: ResolvedTopic,
    isGlassActive: Boolean,
    hazeState: HazeState?,
    isDark: Boolean,
    accentColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color,
) {
    var expanded by remember(topic.route) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .zkmGlassCard(
                isGlassActive = isGlassActive,
                hazeState = hazeState,
                shape = RoundedCornerShape(24.dp),
                tint = zkmGlassFallbackTint(isDark),
            )
            .clickable { expanded = !expanded }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = topic.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = onSurfaceColor,
                )
                Text(
                    text = topic.desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurfaceVariantColor,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                )
            }
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = onSurfaceVariantColor,
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Text(
                    text = stringResource(R.string.help_section_howto),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = accentColor,
                )
                Spacer(Modifier.height(6.dp))
                topic.bullets.forEach { bullet ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.padding(bottom = 6.dp),
                    ) {
                        Text(
                            text = "•",
                            modifier = Modifier.padding(end = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = accentColor,
                        )
                        Text(
                            text = bullet,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                            color = onSurfaceColor,
                        )
                    }
                }
                if (topic.warning != null) {
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
                        Text(
                            text = topic.warning,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpFaqCard(
    faq: ResolvedFaq,
    isGlassActive: Boolean,
    hazeState: HazeState?,
    isDark: Boolean,
    accentColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color,
) {
    var expanded by remember(faq.title) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .zkmGlassCard(
                isGlassActive = isGlassActive,
                hazeState = hazeState,
                shape = RoundedCornerShape(24.dp),
                tint = zkmGlassFallbackTint(isDark),
            )
            .clickable { expanded = !expanded }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = faq.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = accentColor,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = onSurfaceVariantColor,
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Text(
                text = faq.body,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = onSurfaceColor,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}
