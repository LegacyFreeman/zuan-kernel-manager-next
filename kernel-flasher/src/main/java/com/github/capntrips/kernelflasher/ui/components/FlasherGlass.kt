package com.github.capntrips.kernelflasher.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect

/**
 * Status glass yang dikirim dari app (KernelFlasherScreen) ke seluruh layar
 * mode Capntrips. Modul ini tidak mengenal pengaturan wallpaper/haze app,
 * jadi status dikirim lewat CompositionLocal. Bila null / tidak aktif, semua
 * card tampil seperti Material3 biasa.
 */
class FlasherGlass(
    val isActive: Boolean,
    val hazeState: HazeState?,
    val isDark: Boolean,
    /**
     * Android Liquid Glass (Kyant). Bila [liquidBackdrop] != null kartu digambar dengan
     * blur + lens refraction (sama persis dengan kartu di layar Home), parameter diambil dari
     * Wallpaper Style Screen. Null = jalur Haze lama.
     */
    val liquidBackdrop: Backdrop? = null,
    val liquidCornerRadius: Dp = 20.dp,
    val liquidBlurRadius: Dp = 8.dp,
    val liquidRefractionHeight: Dp = 12.dp,
    val liquidRefractionAmount: Dp = 12.dp,
    val liquidChromaticAberration: Boolean = false,
    /** Tint tipis seragam di atas kaca liquid (warna theme-aware dari app). */
    val liquidTint: Color = Color.Transparent,
    /**
     * Warna isi kartu SERAGAM dari app (rumus yang sama dengan kartu SetEdit / Home):
     * putih di light mode, gelap bertint primary di dark mode, atau warna custom-background.
     * Unspecified = pakai warna Material bawaan (perilaku lama, hanya untuk preview/test).
     */
    val cardColor: Color = Color.Unspecified,
    /** Warna aksen app (primary hasil pilihan user) untuk highlight kartu aktif. */
    val accentColor: Color = Color.Unspecified,
)

val LocalFlasherGlass = staticCompositionLocalOf<FlasherGlass?> { null }

/**
 * Menyeragamkan warna kartu modul Capntrips dengan kartu layar Home lain (SetEdit dst.).
 * Sebelumnya tiap kartu memakai surfaceContainer/Low/Highest Material => kartu berwarna
 * theme dan dark mode-nya tidak sama dengan SetEdit.
 *
 * - Kartu biasa  -> [FlasherGlass.cardColor].
 * - Kartu "aktif" (primaryContainer) -> aksen app tipis di atas warna kartu seragam,
 *   supaya status aktif tetap terlihat tapi dasarnya sama dengan kartu lain.
 */
@Composable
internal fun resolveFlasherCardColor(requested: Color): Color {
    val glass = LocalFlasherGlass.current ?: return requested
    val unified = glass.cardColor
    if (unified == Color.Unspecified) return requested
    val scheme = MaterialTheme.colorScheme
    if (requested == scheme.primaryContainer) {
        val accent = if (glass.accentColor == Color.Unspecified) scheme.primary else glass.accentColor
        return accent.copy(alpha = 0.18f).compositeOver(unified)
    }
    return unified
}

/** Warna asli card ditaruh di atas scrim supaya tidak pernah jadi transparan. */
internal fun flasherGlassTint(base: Color, isDark: Boolean, strength: Float = 0.45f): Color {
    val scrim = if (isDark) Color.Black.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.30f)
    if (base == Color.Unspecified || base.alpha == 0f) return scrim
    return base.copy(alpha = strength.coerceIn(0f, 1f)).compositeOver(scrim)
}

/**
 * Pengganti `androidx.compose.material3.Card` yang sadar glass.
 * Tidak glass: Card Material3 biasa dengan [containerColor].
 * Glass: clip -> blur haze -> border tipis, isi card = [containerColor]
 * di atas scrim (digambar oleh Card, jadi selalu terlihat walau blur gagal).
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = LocalFlasherGlass.current
    val hazeState = glass?.hazeState
    val useGlass = glass != null && glass.isActive && hazeState != null

    // Jalur Android Liquid Glass (Kyant): kartu sama dengan kartu Home.
    val liquidBackdrop = glass?.liquidBackdrop
    if (glass != null && glass.isActive && liquidBackdrop != null) {
        val liquidShape = RoundedCornerShape(glass.liquidCornerRadius)
        val liquidModifier = modifier
            .drawBackdrop(
                backdrop = liquidBackdrop,
                shape = { liquidShape },
                effects = {
                    blur(glass.liquidBlurRadius.toPx())
                    if (glass.liquidRefractionHeight > 0.dp && glass.liquidRefractionAmount > 0.dp) {
                        lens(
                            refractionHeight = glass.liquidRefractionHeight.toPx(),
                            refractionAmount = glass.liquidRefractionAmount.toPx(),
                            chromaticAberration = glass.liquidChromaticAberration,
                        )
                    }
                },
                onDrawSurface = { drawRect(glass.liquidTint) },
            )
            .border(1.dp, Color.White.copy(alpha = 0.18f), liquidShape)
        // Isi Card transparan: tint sudah digambar oleh kaca, jangan ditumpuk lagi.
        val liquidColors = CardDefaults.cardColors(containerColor = Color.Transparent)
        if (onClick != null) {
            androidx.compose.material3.Card(
                onClick = onClick,
                modifier = liquidModifier,
                shape = liquidShape,
                colors = liquidColors,
                content = content,
            )
        } else {
            androidx.compose.material3.Card(
                modifier = liquidModifier,
                shape = liquidShape,
                colors = liquidColors,
                content = content,
            )
        }
        return
    }

    // Resep kaca REFERENSI (identik dengan kartu SetEdit / ZkmReferenceGlass di app):
    // blur 24dp, noise 0.10, scrim putih/hitam (sesuai mode APP) 0.50, isi kartu scrim 0.35,
    // border gradien putih 0.30 -> 0.05.
    val scrim = if (glass?.isDark == true) Color.Black else Color.White
    val cardModifier = if (useGlass && hazeState != null) {
        modifier
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = scrim.copy(alpha = 0.5f),
                    blurRadius = 24.dp,
                    noiseFactor = 0.1f,
                    tints = emptyList(),
                ),
            )
            .border(
                1.dp,
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.05f))
                ),
                shape,
            )
    } else {
        modifier
    }
    val resolvedColor = if (useGlass && glass != null) {
        scrim.copy(alpha = 0.35f)
    } else {
        resolveFlasherCardColor(containerColor)
    }
    val colors = CardDefaults.cardColors(containerColor = resolvedColor)

    if (onClick != null) {
        androidx.compose.material3.Card(
            onClick = onClick,
            modifier = cardModifier,
            shape = shape,
            colors = colors,
            content = content,
        )
    } else {
        androidx.compose.material3.Card(
            modifier = cardModifier,
            shape = shape,
            colors = colors,
            content = content,
        )
    }
}
