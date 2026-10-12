/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalView
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.zuan.kernelmanager.next.feature.settings.CardGlassStyle
import com.zuan.kernelmanager.next.feature.settings.SettingsPreference

/**
 * Backdrop liquid glass level-app. Di-provide oleh ZuanKernelManagerApp dari
 * layer wallpaper MainActivity (layer wallpaper direkam terpisah supaya tidak
 * ada siklus RenderNode). Komponen yang berada di dalam layer konten (kartu,
 * toggle, slider, dialog) menggambar efek liquid glass dari backdrop ini.
 * Bila null (mis. layar dibuka dari activity lain tanpa provider), semua
 * pemakai WAJIB fallback ke Haze/warna biasa.
 */
val LocalZkmLiquidBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Parameter Android Liquid Glass (Kyant) yang bisa diatur user. */
data class ZkmLiquidGlassSpec(
    val cornerRadius: Dp = 20.dp,
    val blurRadius: Dp = 8.dp,
    val refractionHeight: Dp = 12.dp,
    val refractionAmount: Dp = 12.dp,
    val chromaticAberration: Boolean = false,
)

/** Baca spec liquid glass dari SettingsPreference (reaktif). */
@Composable
fun rememberZkmLiquidGlassSpec(): ZkmLiquidGlassSpec {
    val context = LocalContext.current
    val prefs = remember { SettingsPreference.getInstance(context) }
    val corner by prefs.liquidCornerRadius.collectAsState()
    val blur by prefs.liquidBlurRadius.collectAsState()
    val refHeight by prefs.liquidRefractionHeight.collectAsState()
    val refAmount by prefs.liquidRefractionAmount.collectAsState()
    val chroma by prefs.liquidChromaticAberration.collectAsState()
    return remember(corner, blur, refHeight, refAmount, chroma) {
        ZkmLiquidGlassSpec(
            cornerRadius = corner.dp,
            blurRadius = blur.dp,
            refractionHeight = refHeight.dp,
            refractionAmount = refAmount.dp,
            chromaticAberration = chroma,
        )
    }
}

/** Runtime shader AGSL butuh Android 13+. Di bawah itu caller fallback ke Haze. */
fun isLiquidGlassSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * Modifier kartu Android Liquid Glass (Kyant backdrop): blur + lens refraction
 * (+ chromatic aberration opsional) dengan corner radius dari [spec].
 * [tint] digambar di atas efek supaya warna kartu tetap terbaca.
 */
fun Modifier.zkmLiquidGlassCard(
    backdrop: Backdrop,
    spec: ZkmLiquidGlassSpec,
    tint: Color,
    borderColor: Color = Color.White.copy(alpha = 0.18f),
): Modifier {
    val shape = RoundedCornerShape(spec.cornerRadius)
    return this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                blur(spec.blurRadius.toPx())
                if (spec.refractionHeight > 0.dp && spec.refractionAmount > 0.dp) {
                    lens(
                        refractionHeight = spec.refractionHeight.toPx(),
                        refractionAmount = spec.refractionAmount.toPx(),
                        chromaticAberration = spec.chromaticAberration,
                    )
                }
            },
            onDrawSurface = { drawRect(tint) },
        )
        .border(1.dp, borderColor, shape)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * true bila composable ini digambar di window Activity utama — satu-satunya
 * window yang berbagi layer wallpaper (LayerBackdrop). Compose `Dialog`,
 * `ModalBottomSheet`, dan `Popup` memakai WINDOW TERPISAH: layer backdrop
 * milik window utama tidak bisa digambar di sana (hasilnya kosong/transparan
 * dan koordinatnya salah). Di window seperti itu semua komponen liquid glass
 * WAJIB fallback ke Haze / warna solid.
 */
@Composable
fun isInHostWindow(): Boolean {
    val view = LocalView.current
    val activity = LocalContext.current.findActivity() ?: return false
    return view.rootView === activity.window?.decorView
}

/**
 * Dipakai oleh [zkmGlassCard] dan [zkmCardHazeEffect]: resolve request liquid
 * glass yang aktif. Mengembalikan null bila style bukan LIQUID_GLASS / tidak
 * didukung / backdrop tidak tersedia / berada di window terpisah (dialog,
 * bottom sheet), sehingga caller lanjut ke jalur Haze frosted glass.
 */
@Composable
fun resolveLiquidGlassCardRequest(isGlassActive: Boolean): Pair<Backdrop, ZkmLiquidGlassSpec>? {
    if (!isGlassActive || !isLiquidGlassSupported()) return null
    val context = LocalContext.current
    val prefs = remember { SettingsPreference.getInstance(context) }
    val style by prefs.cardGlassStyle.collectAsState()
    if (style != CardGlassStyle.LIQUID_GLASS) return null
    val backdrop = LocalZkmLiquidBackdrop.current ?: return null
    if (!isInHostWindow()) return null
    val spec = rememberZkmLiquidGlassSpec()
    return backdrop to spec
}

/**
 * Pengganti drop-in untuk pasangan `.clip(shape).hazeEffect(state, style)` pada
 * KARTU. Jalur Haze = persis perilaku lama. Jalur Android Liquid Glass (Kyant)
 * dipakai bila user memilih style itu: corner radius, blur radius, refraction
 * height/amount dan chromatic aberration diambil dari pengaturan Wallpaper
 * Style Screen (bukan dari shape call-site), supaya SEMUA kartu mengikuti.
 * Warna kartu dari `style.backgroundColor` tetap dipakai sebagai tint.
 */
fun Modifier.zkmCardHazeEffect(
    shape: Shape,
    state: HazeState,
    style: HazeStyle,
): Modifier = composed {
    val request = resolveLiquidGlassCardRequest(isGlassActive = true)
    if (request != null) {
        val (backdrop, spec) = request
        // Tint SERAGAM untuk semua kartu (mirip kartu DoD Manager: tipis, theme-aware).
        // Sebelumnya tint diambil dari warna Haze tiap call-site (Transparent / putih 5% /
        // putih 10% / warna kartu 50% ...) sehingga tampilan antar layar beda-beda.
        val tint = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = ZkmLiquidCardTintAlpha)
        Modifier.zkmLiquidGlassCard(
            backdrop = backdrop,
            spec = spec,
            tint = tint,
        )
    } else {
        Modifier.clip(shape).hazeEffect(state = state, style = style)
    }
}

/**
 * Pengganti `.border(width, color, shape)` untuk kartu yang memakai
 * [zkmCardHazeEffect]. Pada mode Android Liquid Glass kartu sudah menggambar
 * border-nya sendiri dengan corner radius pengaturan user, jadi border
 * call-site (dengan shape lama) dilewati supaya sudutnya tidak bentrok.
 */
fun Modifier.zkmCardBorder(width: Dp, color: Color, shape: androidx.compose.ui.graphics.Shape): Modifier = composed {
    if (resolveLiquidGlassCardRequest(isGlassActive = true) != null) Modifier
    else Modifier.border(width, color, shape)
}

fun Modifier.zkmCardBorder(width: Dp, brush: Brush, shape: androidx.compose.ui.graphics.Shape): Modifier = composed {
    if (resolveLiquidGlassCardRequest(isGlassActive = true) != null) Modifier
    else Modifier.border(width, brush, shape)
}

/**
 * Kaca liquid saja (tanpa jalur Haze) untuk elemen kecil seperti search field. Bila style
 * liquid tidak aktif / tidak didukung / di window terpisah -> tidak melakukan apa-apa
 * (caller tetap memakai warna biasa). [cornerRadius] null = ikut pengaturan user.
 */
fun Modifier.zkmLiquidSurface(cornerRadius: Dp? = null): Modifier = composed {
    val request = resolveLiquidGlassCardRequest(isGlassActive = true)
    if (request != null) {
        val (backdrop, spec) = request
        Modifier.zkmLiquidGlassCard(
            backdrop = backdrop,
            spec = if (cornerRadius != null) spec.copy(cornerRadius = cornerRadius) else spec,
            tint = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = ZkmLiquidCardTintAlpha),
        )
    } else {
        Modifier
    }
}

/** Opasitas tint seragam kartu liquid glass. */
const val ZkmLiquidCardTintAlpha = 0.12f

/**
 * true selama style kartu = Android Liquid Glass (di-set dari root app). Dibaca oleh
 * [zkmGlassFallbackTint] supaya Surface/Card di ATAS kaca tidak menggambar lapisan
 * putih/hitam sendiri — itulah yang membuat "1 kartu terlihat seperti 2 kartu"
 * dan membuat kecerahan antar kartu berbeda-beda.
 */
object ZkmLiquidFlags {
    var cardsLiquid by mutableStateOf(false)
}
