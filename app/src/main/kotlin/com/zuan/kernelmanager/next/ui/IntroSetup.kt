/*
 * Copyright (c) 2025-2026 ZKM (Zuan Kernel Manager)
 * INTRO "SETUP" STEPS — optional personalisation shown after "Select working mode".
 *
 *  05 SUPPORT     Buy Me a Coffee link for the developer
 *  06 PROFILE     user name + photo / GIF / video + avatar frame (same storage as the Overall screen)
 *  07 APPEARANCE  light / dark / system, Dynamic Color, theme color (a thin Wallpaper Style)
 *  08 BACKUP      Import / Export Settings (XML), Import / Export Language Pack, help pointer
 *
 * EVERY step can be skipped (defaults stay untouched). Same glass + 3D stagger motion as IntroApp.kt.
 * All user-facing text lives in values/strings.xml so the language pack keeps working.
 */
package com.zuan.kernelmanager.next.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.core.mode.WorkingMode
import com.zuan.kernelmanager.next.core.mode.WorkingModeManager
import com.zuan.kernelmanager.next.core.util.LanguagePackManager
import com.zuan.kernelmanager.next.core.util.SettingsXmlBackup
import com.zuan.kernelmanager.next.feature.dashboard.components.AvatarPreviewContent
import com.zuan.kernelmanager.next.feature.dashboard.components.AvatarWithDecoration
import com.zuan.kernelmanager.next.feature.dashboard.components.ProfileCropDialog
import com.zuan.kernelmanager.next.feature.dashboard.components.ProfileImportResult
import com.zuan.kernelmanager.next.feature.dashboard.components.ProfileMedia
import com.zuan.kernelmanager.next.feature.dashboard.components.ProfileMediaStore
import com.zuan.kernelmanager.next.feature.settings.AvatarDecorationStyle
import com.zuan.kernelmanager.next.feature.settings.SettingsPreference
import com.zuan.kernelmanager.next.feature.settings.availableColors
import com.zuan.kernelmanager.next.ui.theme.ThemeMode
import kotlinx.coroutines.launch

/** Developer support page (Buy Me a Coffee). Also used by the About screen. */
const val ZKM_SUPPORT_URL = "https://sociabuzz.com/zuanvfx01/tribe"

/** First / last stage index handled by [IntroSetupStage] (0 = pages, 1 = working mode). */
internal const val SETUP_FIRST_STAGE = 2
internal const val SETUP_LAST_STAGE = 5

// ═════════════════════════ ENTRY POINT ═════════════════════════
@Composable
internal fun IntroSetupStage(
    stage: Int,
    ctx: HeroCtx,
    colors: IntroColors,
    c1: Color,
    c2: Color,
    time: FloatState,
    totalSteps: Int,
    currentStep: Int,
    selectedMode: WorkingMode?,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onSkipAll: () -> Unit
) {
    val isLast = stage >= SETUP_LAST_STAGE
    when (stage) {
        2 -> SupportStep(ctx, colors, c1, c2, time, totalSteps, currentStep, onContinue, onBack, onSkipAll)
        3 -> ProfileStep(ctx, colors, c1, c2, time, totalSteps, currentStep, onContinue, onBack, onSkipAll)
        4 -> AppearanceStep(ctx, colors, c1, c2, time, totalSteps, currentStep, onContinue, onBack, onSkipAll)
        else -> BackupStep(ctx, colors, c1, c2, time, totalSteps, currentStep, selectedMode, isLast, onContinue, onBack)
    }
}

// ═════════════════════════ SHARED PIECES ═════════════════════════
private fun Modifier.stagger(enter: State<Float>, order: Int): Modifier = graphicsLayer {
    val p = smooth((enter.value - 0.18f - order * 0.12f) * 1.8f)
    cameraDistance = 14f * density
    transformOrigin = TransformOrigin(0.5f, 0f)
    alpha = p
    translationY = (1f - p) * 32.dp.toPx()
    rotationX = (1f - p) * -40f
}

@Composable
private fun SetupFrame(
    stepLabel: String,
    title: String,
    desc: String,
    heroKind: Int,
    ctx: HeroCtx,
    colors: IntroColors,
    c1: Color,
    c2: Color,
    time: FloatState,
    currentStep: Int,
    totalSteps: Int,
    primaryText: String,
    showArrow: Boolean,
    onPrimary: () -> Unit,
    onBack: () -> Unit,
    onSkipAll: (() -> Unit)?,
    content: @Composable ColumnScope.(State<Float>) -> Unit
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val enter = animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "setupEnter"
    )

    Column(
        modifier = Modifier.fillMaxSize().imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val heroSize = (maxHeight * 0.20f).coerceIn(96.dp, 150.dp)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Hero3D(
                    kind = heroKind,
                    ctx = ctx,
                    offset = { 0f },
                    c1 = c1,
                    c2 = c2,
                    dark = colors.dark,
                    modifier = Modifier
                        .size(heroSize)
                        .pointerInput(Unit) { detectTapGestures(onTap = { ctx.onKick() }) }
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.graphicsLayer { alpha = smooth(enter.value * 1.6f) }
                ) {
                    Box(Modifier.size(6.dp).background(c1, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stepLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = c1,
                        letterSpacing = 1.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = colors.text,
                    textAlign = TextAlign.Center,
                    lineHeight = 36.sp,
                    modifier = Modifier.graphicsLayer {
                        val p = smooth(enter.value * 1.4f)
                        alpha = p
                        translationY = (1f - p) * 26.dp.toPx()
                    }
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .graphicsLayer {
                            val p = smooth((enter.value - 0.08f) * 1.4f)
                            alpha = p
                            translationY = (1f - p) * 26.dp.toPx()
                        }
                )

                Spacer(Modifier.height(16.dp))

                content(enter)

                Spacer(Modifier.height(4.dp))
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().animateContentSize()
        ) {
            PagerPills({ currentStep.toFloat() }, totalSteps, c1, colors.muted)
            Spacer(Modifier.height(12.dp))

            GlowButton(
                text = primaryText,
                showArrow = showArrow,
                c1 = c1,
                c2 = c2,
                time = time,
                onClick = onPrimary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Text(
                        text = stringResource(R.string.intro_mode_back),
                        color = colors.muted,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (onSkipAll != null) {
                    TextButton(onClick = onSkipAll) {
                        Text(
                            text = stringResource(R.string.intro_setup_skip_all),
                            color = colors.muted,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** Glass row: icon, title, subtitle, optional trailing icon. [onClick] == null makes it a plain info card. */
@Composable
private fun SetupCard(
    enter: State<Float>,
    order: Int,
    colors: IntroColors,
    c1: Color,
    icon: ImageVector,
    title: String,
    subtitle: String?,
    trailing: ImageVector? = null,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(22.dp)
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "setupPress"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .stagger(enter, order)
            .graphicsLayer {
                val sc = 1f - 0.03f * press
                scaleX = sc
                scaleY = sc
            }
            .glass(colors, shape)
            .then(
                if (onClick != null)
                    Modifier.clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(c1.copy(alpha = 0.18f))
        ) {
            Icon(icon, contentDescription = null, tint = c1, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = colors.muted
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            Icon(trailing, contentDescription = null, tint = c1, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String, colors: IntroColors, enter: State<Float>, order: Int) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = colors.muted,
        letterSpacing = 1.2.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, bottom = 8.dp)
            .stagger(enter, order)
    )
}

private fun openLink(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

// ═════════════════════════ 05 · SUPPORT ═════════════════════════
@Composable
private fun SupportStep(
    ctx: HeroCtx, colors: IntroColors, c1: Color, c2: Color, time: FloatState,
    totalSteps: Int, currentStep: Int,
    onContinue: () -> Unit, onBack: () -> Unit, onSkipAll: () -> Unit
) {
    val context = LocalContext.current
    SetupFrame(
        stepLabel = stringResource(R.string.intro_setup_support_step),
        title = stringResource(R.string.intro_setup_support_title),
        desc = stringResource(R.string.intro_setup_support_desc),
        heroKind = 1,
        ctx = ctx, colors = colors, c1 = c1, c2 = c2, time = time,
        currentStep = currentStep, totalSteps = totalSteps,
        primaryText = stringResource(R.string.intro_btn_continue),
        showArrow = true,
        onPrimary = onContinue,
        onBack = onBack,
        onSkipAll = onSkipAll
    ) { enter ->
        SetupCard(
            enter = enter, order = 0, colors = colors, c1 = c1,
            icon = Icons.Rounded.Coffee,
            title = stringResource(R.string.intro_setup_support_btn),
            subtitle = "sociabuzz.com/zuanvfx01/tribe",
            trailing = Icons.Outlined.OpenInNew,
            onClick = { openLink(context, ZKM_SUPPORT_URL) }
        )
        Spacer(Modifier.height(12.dp))
        SetupCard(
            enter = enter, order = 1, colors = colors, c1 = c1,
            icon = Icons.Rounded.Favorite,
            title = stringResource(R.string.intro_setup_optional_title),
            subtitle = stringResource(R.string.intro_setup_support_note)
        )
    }
}

// ═════════════════════════ 06 · PROFILE ═════════════════════════
@Composable
private fun ProfileStep(
    ctx: HeroCtx, colors: IntroColors, c1: Color, c2: Color, time: FloatState,
    totalSteps: Int, currentStep: Int,
    onContinue: () -> Unit, onBack: () -> Unit, onSkipAll: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // same store the Overall screen reads
    val profilePrefs = remember { context.getSharedPreferences("user_profile_prefs", Context.MODE_PRIVATE) }
    var name by rememberSaveable {
        mutableStateOf(profilePrefs.getString("user_name", null)?.takeIf { it != "Your Name" } ?: "")
    }
    var media by remember { mutableStateOf(ProfileMediaStore.load(context)) }
    var pending by remember { mutableStateOf<ProfileMedia?>(null) }
    // avatar frame (decoration): same pref the Overall screen + Wallpaper Style use
    val settingsPrefs = remember { SettingsPreference.getInstance(context) }
    val decoStyle by settingsPrefs.avatarDecoration.collectAsStateWithLifecycle()

    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        val cropped = result.uriContent
        if (result.isSuccessful && cropped != null) {
            scope.launch {
                ProfileMediaStore.adoptCropped(context, cropped)?.let {
                    ProfileMediaStore.save(context, it)
                    media = it
                }
            }
        }
    }

    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                when (val r = ProfileMediaStore.import(context, uri)) {
                    is ProfileImportResult.NeedsCrop -> cropLauncher.launch(
                        CropImageContractOptions(
                            uri = r.source,
                            cropImageOptions = CropImageOptions(
                                fixAspectRatio = true,
                                aspectRatioX = 1,
                                aspectRatioY = 1,
                                cropShape = CropImageView.CropShape.OVAL,
                                outputCompressFormat = android.graphics.Bitmap.CompressFormat.PNG,
                                outputCompressQuality = 100
                            )
                        )
                    )
                    is ProfileImportResult.Ready -> pending = r.media
                    ProfileImportResult.TooLarge ->
                        Toast.makeText(context, R.string.profile_media_too_large, Toast.LENGTH_LONG).show()
                    ProfileImportResult.Failed ->
                        Toast.makeText(context, R.string.profile_media_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    pending?.let { pm ->
        ProfileCropDialog(
            media = pm,
            onCancel = {
                ProfileMediaStore.discard(context, pm, media)
                pending = null
            },
            onConfirm = { cropped ->
                ProfileMediaStore.save(context, cropped)
                media = cropped
                pending = null
            }
        )
    }

    val untouched = name.isBlank() && media == null && decoStyle == AvatarDecorationStyle.NONE

    SetupFrame(
        stepLabel = stringResource(R.string.intro_setup_profile_step),
        title = stringResource(R.string.intro_setup_profile_title),
        desc = stringResource(R.string.intro_setup_profile_desc),
        heroKind = 2,
        ctx = ctx, colors = colors, c1 = c1, c2 = c2, time = time,
        currentStep = currentStep, totalSteps = totalSteps,
        primaryText = stringResource(if (untouched) R.string.intro_setup_skip else R.string.intro_btn_continue),
        showArrow = true,
        onPrimary = {
            if (name.isNotBlank()) profilePrefs.edit().putString("user_name", name.trim()).apply()
            onContinue()
        },
        onBack = onBack,
        onSkipAll = onSkipAll
    ) { enter ->
        // Live preview: photo + chosen frame. Tap to change the photo.
        Box(modifier = Modifier.stagger(enter, 0).size(150.dp)) {
            AvatarWithDecoration(style = decoStyle, modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (decoStyle == AvatarDecorationStyle.NONE)
                                Modifier.border(2.dp, Brush.linearGradient(listOf(c1, c2)), CircleShape)
                            else Modifier
                        )
                        .clickable(role = Role.Button) {
                            pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        }
                ) {
                    AvatarPreviewContent(
                        media = media,
                        live = true,
                        placeholderBg = c1.copy(alpha = 0.14f),
                        placeholderTint = c1,
                        iconSize = 54.dp
                    )
                }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(14.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(c1)
                    .clickable(role = Role.Button) {
                        pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    }
            ) {
                Icon(Icons.Rounded.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.intro_setup_profile_photo_hint),
            style = MaterialTheme.typography.labelSmall,
            color = colors.muted,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.stagger(enter, 1)
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 32) name = it },
            singleLine = true,
            label = { Text(stringResource(R.string.intro_setup_profile_name)) },
            placeholder = { Text("Your Name") },
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.text,
                unfocusedTextColor = colors.text,
                cursorColor = c1,
                focusedBorderColor = c1,
                unfocusedBorderColor = colors.muted.copy(alpha = 0.45f),
                focusedLabelColor = c1,
                unfocusedLabelColor = colors.muted,
                focusedPlaceholderColor = colors.muted.copy(alpha = 0.7f),
                unfocusedPlaceholderColor = colors.muted.copy(alpha = 0.7f)
            ),
            modifier = Modifier.fillMaxWidth().stagger(enter, 2)
        )

        Spacer(Modifier.height(18.dp))

        // ── Avatar frame picker (all frames from Wallpaper Style > Avatar Decoration)
        SectionLabel(stringResource(R.string.intro_setup_frame_title), colors, enter, 3)
        LazyRow(
            modifier = Modifier.fillMaxWidth().stagger(enter, 4),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(AvatarDecorationStyle.entries, key = { it.name }) { style ->
                val selected = decoStyle == style
                val sel by animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    label = "frameSel"
                )
                val tileShape = RoundedCornerShape(22.dp)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(86.dp)
                        .graphicsLayer {
                            val sc = 1f + 0.06f * sel
                            scaleX = sc
                            scaleY = sc
                        }
                        .clip(tileShape)
                        .background(Brush.linearGradient(listOf(colors.glassA, colors.glassB)))
                        .background(c1.copy(alpha = 0.12f * sel))
                        .border(
                            width = (1f + 1.2f * sel).dp,
                            brush = Brush.linearGradient(
                                listOf(lerp(colors.strokeA, c1, sel), lerp(colors.strokeB, c1.copy(alpha = 0.4f), sel))
                            ),
                            shape = tileShape
                        )
                        .clickable(role = Role.RadioButton) { settingsPrefs.setAvatarDecoration(style) }
                ) {
                    AvatarWithDecoration(style = style, modifier = Modifier.size(82.dp)) {
                        AvatarPreviewContent(
                            media = media,
                            live = false,
                            placeholderBg = c1.copy(alpha = 0.18f),
                            placeholderTint = c1,
                            iconSize = 20.dp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = if (decoStyle == AvatarDecorationStyle.NONE) stringResource(R.string.intro_setup_frame_none) else decoStyle.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = colors.muted,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.stagger(enter, 5)
        )
    }
}

// ═════════════════════════ 07 · APPEARANCE ═════════════════════════
@Composable
private fun AppearanceStep(
    ctx: HeroCtx, colors: IntroColors, c1: Color, c2: Color, time: FloatState,
    totalSteps: Int, currentStep: Int,
    onContinue: () -> Unit, onBack: () -> Unit, onSkipAll: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { SettingsPreference.getInstance(context) }
    val themeMode by prefs.themeMode.collectAsStateWithLifecycle()
    val dynamic by prefs.isDynamicColor.collectAsStateWithLifecycle()
    val custom by prefs.isCustomColor.collectAsStateWithLifecycle()
    val colorName by prefs.themeColorName.collectAsStateWithLifecycle()
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    SetupFrame(
        stepLabel = stringResource(R.string.intro_setup_appearance_step),
        title = stringResource(R.string.intro_setup_appearance_title),
        desc = stringResource(R.string.intro_setup_appearance_desc),
        heroKind = 0,
        ctx = ctx, colors = colors, c1 = c1, c2 = c2, time = time,
        currentStep = currentStep, totalSteps = totalSteps,
        primaryText = stringResource(R.string.intro_btn_continue),
        showArrow = true,
        onPrimary = onContinue,
        onBack = onBack,
        onSkipAll = onSkipAll
    ) { enter ->
        // ── Light / Dark / System
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ModeChip(
                modifier = Modifier.weight(1f), enter = enter, order = 0, colors = colors, accent = c1,
                icon = Icons.Rounded.LightMode, label = stringResource(R.string.intro_setup_theme_light),
                selected = themeMode == ThemeMode.LIGHT,
                onClick = { prefs.setThemeMode(ThemeMode.LIGHT) }
            )
            ModeChip(
                modifier = Modifier.weight(1f), enter = enter, order = 1, colors = colors, accent = c1,
                icon = Icons.Rounded.DarkMode, label = stringResource(R.string.intro_setup_theme_dark),
                selected = themeMode == ThemeMode.DARK,
                onClick = { prefs.setThemeMode(ThemeMode.DARK) }
            )
            ModeChip(
                modifier = Modifier.weight(1f), enter = enter, order = 2, colors = colors, accent = c1,
                icon = Icons.Rounded.BrightnessAuto, label = stringResource(R.string.intro_setup_theme_system),
                selected = themeMode == ThemeMode.SYSTEM_DEFAULT,
                onClick = { prefs.setThemeMode(ThemeMode.SYSTEM_DEFAULT) }
            )
        }

        Spacer(Modifier.height(14.dp))

        // ── Dynamic Color
        val glassShape = RoundedCornerShape(22.dp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .stagger(enter, 3)
                .glass(colors, glassShape)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.intro_setup_dynamic_title),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.text
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        if (dynamicSupported) R.string.intro_setup_dynamic_desc else R.string.intro_setup_dynamic_unsupported
                    ),
                    fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted
                )
            }
            Switch(
                checked = dynamic && dynamicSupported,
                enabled = dynamicSupported,
                onCheckedChange = { on ->
                    prefs.setDynamicColorEnabled(on)
                    if (on) prefs.setIsCustomColor(false)
                },
                colors = SwitchDefaults.colors(checkedTrackColor = c1, checkedThumbColor = Color.White)
            )
        }

        Spacer(Modifier.height(18.dp))

        // ── Theme color palette
        SectionLabel(stringResource(R.string.intro_setup_palette_title), colors, enter, 4)
        LazyRow(
            modifier = Modifier.fillMaxWidth().stagger(enter, 5),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(availableColors, key = { it.name }) { swatch ->
                val selected = !dynamic && !custom && colorName == swatch.name
                val sel by animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    label = "swatchSel"
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .graphicsLayer {
                            val sc = 1f + 0.12f * sel
                            scaleX = sc
                            scaleY = sc
                        }
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(swatch.primary, swatch.tertiary)))
                        .border((1f + 2f * sel).dp, lerp(colors.strokeA, colors.text, sel), CircleShape)
                        .clickable(role = Role.RadioButton) {
                            prefs.setThemeColor(swatch.name)
                            prefs.setIsCustomColor(false)
                            prefs.setDynamicColorEnabled(false)
                        }
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = swatch.name,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp).graphicsLayer { alpha = sel }
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.intro_setup_appearance_hint),
            style = MaterialTheme.typography.labelSmall,
            color = colors.muted,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.stagger(enter, 6)
        )
    }
}

@Composable
private fun ModeChip(
    modifier: Modifier,
    enter: State<Float>,
    order: Int,
    colors: IntroColors,
    accent: Color,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val sel by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "chipSel"
    )
    val tint by animateColorAsState(
        targetValue = if (selected) accent else colors.muted,
        animationSpec = tween(300),
        label = "chipTint"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .stagger(enter, order)
            .clip(shape)
            .background(Brush.linearGradient(listOf(colors.glassA, colors.glassB)))
            .background(accent.copy(alpha = 0.14f * sel))
            .border(
                width = (1f + 0.8f * sel).dp,
                brush = Brush.linearGradient(
                    listOf(lerp(colors.strokeA, accent, sel), lerp(colors.strokeB, accent.copy(alpha = 0.35f), sel))
                ),
                shape = shape
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(6.dp))
        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (selected) colors.text else colors.muted)
    }
}

// ═════════════════════════ 08 · BACKUP & LANGUAGE ═════════════════════════
@Composable
private fun BackupStep(
    ctx: HeroCtx, colors: IntroColors, c1: Color, c2: Color, time: FloatState,
    totalSteps: Int, currentStep: Int, selectedMode: WorkingMode?, isLast: Boolean,
    onFinish: () -> Unit, onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    val exportXml = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/xml")) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { SettingsXmlBackup.export(context, it) }
                    ?: throw IllegalStateException("Unable to open file")
                Toast.makeText(context, context.getString(R.string.lp_toast_settings_exported), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.lp_toast_settings_export_failed, e.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
            }
        }
    }
    val importXml = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val count = context.contentResolver.openInputStream(uri)?.use { SettingsXmlBackup.import(context, it) }
                    ?: throw IllegalStateException("Unable to open file")
                Toast.makeText(context, context.getString(R.string.lp_toast_settings_imported, count), Toast.LENGTH_LONG).show()
                restoreAndRestart(activity ?: context, selectedMode)
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.lp_toast_settings_import_failed, e.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
            }
        }
    }
    val exportLang = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                val count = context.contentResolver.openOutputStream(uri)?.use { LanguagePackManager.export(context, it) }
                    ?: throw IllegalStateException("Unable to open file")
                Toast.makeText(context, context.getString(R.string.pref_language_export_success, count), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.pref_language_export_failed, e.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
            }
        }
    }
    val importLang = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val result = context.contentResolver.openInputStream(uri)?.use { LanguagePackManager.import(context, it) }
                    ?: throw IllegalStateException("Unable to open file")
                Toast.makeText(context, context.getString(R.string.pref_language_import_success, result.applied), Toast.LENGTH_LONG).show()
                // stage + choices are saved (rememberSaveable), so the intro comes back on this same step
                activity?.window?.decorView?.postDelayed({ activity.recreate() }, 450)
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.pref_language_import_failed, e.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
            }
        }
    }

    SetupFrame(
        stepLabel = stringResource(R.string.intro_setup_backup_step),
        title = stringResource(R.string.intro_setup_backup_title),
        desc = stringResource(R.string.intro_setup_backup_desc),
        heroKind = 2,
        ctx = ctx, colors = colors, c1 = c1, c2 = c2, time = time,
        currentStep = currentStep, totalSteps = totalSteps,
        primaryText = stringResource(R.string.intro_mode_btn_start),
        showArrow = !isLast,
        onPrimary = onFinish,
        onBack = onBack,
        onSkipAll = null
    ) { enter ->
        SetupCard(
            enter = enter, order = 0, colors = colors, c1 = c1,
            icon = Icons.Outlined.FileDownload,
            title = stringResource(R.string.pref_import_xml),
            subtitle = stringResource(R.string.pref_import_xml_desc),
            onClick = { importXml.launch(arrayOf("application/xml", "text/xml", "*/*")) }
        )
        Spacer(Modifier.height(10.dp))
        SetupCard(
            enter = enter, order = 1, colors = colors, c1 = c1,
            icon = Icons.Outlined.FileUpload,
            title = stringResource(R.string.pref_export_xml),
            subtitle = stringResource(R.string.pref_export_xml_desc),
            onClick = { exportXml.launch("ZKMNext_Settings_Backup.xml") }
        )
        Spacer(Modifier.height(10.dp))
        SetupCard(
            enter = enter, order = 2, colors = colors, c1 = c1,
            icon = Icons.Outlined.Translate,
            title = stringResource(R.string.pref_language_export),
            subtitle = stringResource(R.string.pref_language_export_desc),
            onClick = { exportLang.launch(context.getString(R.string.pref_language_pack_filename)) }
        )
        Spacer(Modifier.height(10.dp))
        SetupCard(
            enter = enter, order = 3, colors = colors, c1 = c1,
            icon = Icons.Outlined.Language,
            title = stringResource(R.string.pref_language_import),
            subtitle = stringResource(R.string.pref_language_import_desc),
            onClick = { importLang.launch(arrayOf("application/json", "text/json", "*/*")) }
        )
        Spacer(Modifier.height(14.dp))
        SetupCard(
            enter = enter, order = 4, colors = colors, c1 = c1,
            icon = Icons.Outlined.MenuBook,
            title = stringResource(R.string.intro_setup_help_title),
            subtitle = stringResource(R.string.intro_setup_help_desc)
        )
    }
}

/**
 * An XML import replaces every preference file, so the running process still holds the OLD values
 * in memory. Make sure the restored install does not fall back into the intro, then restart cleanly.
 */
private fun restoreAndRestart(context: Context, fallbackMode: WorkingMode?) {
    val settings = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
    // Same keys as SettingsPreference (accepted_terms / has_completed_intro); written directly so the
    // live intro does not flip to the main screen while the toast is still showing.
    settings.edit()
        .putBoolean("accepted_terms", true)
        .putBoolean("has_completed_intro", true)
        .commit()
    if (WorkingMode.fromId(settings.getString("working_mode", null)) == null) {
        WorkingModeManager.set(context, fallbackMode ?: WorkingMode.BASIC)
    }
    Toast.makeText(context, context.getString(R.string.intro_setup_restore_restarting), Toast.LENGTH_SHORT).show()
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    Handler(Looper.getMainLooper()).postDelayed({
        context.startActivity(launch)
        Runtime.getRuntime().exit(0)
    }, 900)
}
