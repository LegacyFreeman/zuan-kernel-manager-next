package com.zuan.kernelmanager.next.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import com.zuan.kernelmanager.next.ui.components.zkmHazeEffect

/**
 * Shared app-bar haze style. The app bar blur is intentionally independent
 * from the card-glass toggle: it must remain available with the normal
 * themed background as well as with a custom wallpaper.
 *
 * Theme-aware: previously the custom-background branch always used a black
 * tint, which made app bars look dark (and dark-on-dark) in light mode with
 * a wallpaper. The tint now follows the *app-resolved* dark mode.
 *
 * Progressive blur: blur fades out towards the bottom edge so content
 * scrolling under the bar is blurred gradually instead of a hard cut.
 */
@Composable
fun zkmAppBarHazeStyle(
    tintedBackground: Color,
    isCustomBackground: Boolean,
): HazeStyle {
    val isDark = zkmIsDarkTheme()
    // Memoized: style app bar dipakai di TopAppBar yang sering recompose;
    // tanpa remember, tiap recomposition membuat HazeStyle baru dan haze
    // menganggap konfigurasi blur berubah (re-setup pipeline = jank).
    return androidx.compose.runtime.remember(tintedBackground, isCustomBackground, isDark) {
        zkmAppBarHazeStyle(tintedBackground, isCustomBackground, isDark)
    }
}

fun zkmAppBarHazeStyle(
    tintedBackground: Color,
    isCustomBackground: Boolean,
    isDark: Boolean,
): HazeStyle {
    val overlay = if (isDark) Color.Black else Color.White
    // Catatan performa: radius 30.dp -> 22.dp. Perbedaan visual nyaris tak
    // terlihat karena area app bar kecil dan sudah ditimpa tint, tapi biaya
    // GPU blur turun signifikan (kawasan kernel blur ~ r^2).
    return if (isCustomBackground) {
        HazeStyle(
            backgroundColor = overlay.copy(alpha = 0.3f),
            blurRadius = 22.dp,
            noiseFactor = 0.1f,
            tints = listOf(HazeTint(overlay.copy(alpha = 0.1f))),
        )
    } else {
        HazeStyle(
            backgroundColor = tintedBackground.copy(alpha = 0.7f),
            blurRadius = 22.dp,
            noiseFactor = 0.05f,
            tints = listOf(HazeTint(tintedBackground.copy(alpha = 0.3f))),
        )
    }
}

/**
 * Progressive blur spec untuk app bar: kuat di atas, memudar ke bawah.
 * Dipasang lewat trailing lambda `hazeEffect(...) { progressive = ... }`
 * (Haze 1.x tidak menerima progressive di konstruktor HazeStyle).
 */
val ZkmAppBarProgressive: HazeProgressive
    get() = HazeProgressive.verticalGradient(
        startIntensity = 1f,
        endIntensity = 0f,
        preferPerformance = true,
    )

/**
 * Theme-aware content/text colors for surfaces drawn over a custom
 * background. Previously these were hardcoded to white, breaking light mode
 * (white text on light wallpaper) and producing dark text in dark mode on
 * some screens that used the opposite hardcode.
 */
fun zkmOnCustomBackgroundContentColor(isDark: Boolean): Color =
    if (isDark) Color.White else Color(0xFF1B1B1F)

fun zkmOnCustomBackgroundSubContentColor(isDark: Boolean): Color =
    if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF1B1B1F).copy(alpha = 0.7f)

/**
 * Theme-aware solid card color used when glass blur is disabled but a custom
 * background is set. Previously always black-based, which mixed dark cards
 * into light themes.
 */
fun zkmCustomBackgroundCardColor(isDark: Boolean, opacity: Float): Color =
    if (ZkmLite.active) zkmLiteSolid(isDark)
    else (if (isDark) Color.Black else Color.White).copy(alpha = opacity.coerceIn(0f, 1f))

/**
 * Fallback tint used *under* frosted-glass cards. Glass cards were fully
 * transparent, so whenever the haze blur missed (fast scroll, uncovered
 * source, glass toggle off) the card became "naked" floating text. A subtle
 * theme-aware scrim keeps the card readable in every failure case without
 * changing the glass look when blur works.
 */
/**
 * Warna isi Card/Surface/ListItem yang berada DI ATAS kartu kaca. Pada mode Android Liquid
 * Glass modifier kaca sudah menggambar tint-nya sendiri, jadi isi Card WAJIB transparan
 * (kalau tidak, kartu terlihat dobel/gelap dan refraksinya tertutup). Mode lain: [color].
 */
fun zkmCardFill(color: Color): Color =
    if (ZkmLiquidFlags.cardsLiquid) Color.Transparent else color

fun zkmGlassFallbackTint(isDark: Boolean): Color =
    if (ZkmLite.active) zkmLiteSolid(isDark)
    else if (ZkmLiquidFlags.cardsLiquid) Color.Transparent else zkmRawGlassScrim(isDark)

private fun zkmRawGlassScrim(isDark: Boolean): Color =
    if (isDark) Color.Black.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.30f)


/**
 * Warna isi card glass yang SELALU kelihatan.
 *
 * Bug sebelumnya: card glass memakai [zkmGlassFallbackTint] (hitam/putih alpha
 * rendah) sebagai satu-satunya warna, sehingga warna semantik card (primary,
 * container, status, dst.) hilang dan card tampak transparan. Di tempat lain
 * alpha ditumpuk dua kali (0.6 x 0.35) sehingga warna hampir tak terlihat.
 *
 * Helper ini menaruh [base] di atas scrim fallback dengan alpha [strength],
 * jadi warna card tetap terbaca walau blur haze gagal/terlambat render.
 */
fun zkmGlassTint(base: Color, isDark: Boolean, strength: Float = 0.5f): Color {
    // Versi Lite: warna PEKAT (semantic color dicampur ke alas solid), tidak ada transparansi.
    if (ZkmLite.active) {
        val solid = zkmLiteSolid(isDark)
        if (base == Color.Unspecified || base.alpha == 0f) return solid
        return base.copy(alpha = strength.coerceIn(0.12f, 0.5f)).compositeOver(solid)
    }
    // Mode Android Liquid Glass (Kyant): kartu sudah punya tint tipis sendiri dan refraksi
    // harus terlihat. Tint tebal (base di atas scrim hitam/putih -> alpha efektif ~0.6) membuat
    // kartu tampak buram/gelap dan beda dari kartu Home. Di mode liquid cukup warna tipis.
    if (ZkmLiquidFlags.cardsLiquid) {
        if (base == Color.Unspecified || base.alpha == 0f) return Color.Transparent
        return base.copy(alpha = (strength * 0.3f).coerceIn(0.08f, 0.30f))
    }
    val scrim = zkmRawGlassScrim(isDark)
    if (base == Color.Unspecified || base.alpha == 0f) return scrim
    return base.copy(alpha = strength.coerceIn(0f, 1f)).compositeOver(scrim)
}

/**
 * Modifier card glass terpadu: clip -> blur haze -> tint solid (digambar
 * SETELAH haze supaya warna tidak bergantung pada hasil blur) -> border tipis.
 * Bila glass tidak aktif atau [hazeState] null, hanya clip + tint solid.
 *
 * [BARU] Bila user memilih style "Card Android Liquid Glass (Kyant)" dan
 * backdrop wallpaper tersedia (Android 13+), kartu digambar dengan efek
 * liquid glass: blur + refraction lens (+ chromatic aberration) dari
 * [ZkmLiquidGlassSpec] yang bisa diatur di Wallpaper Style Screen.
 * Semua kartu yang memakai modifier ini otomatis ikut style baru tanpa
 * perlu diubah satu per satu.
 */
@Composable
fun Modifier.zkmGlassCard(
    isGlassActive: Boolean,
    hazeState: HazeState?,
    shape: Shape,
    tint: Color,
    blurRadius: Dp = ZkmGlassCardBlurRadius,
    borderColor: Color = Color.White.copy(alpha = 0.18f),
): Modifier {
    if (ZkmLite.active) {
        // Versi Lite: kartu solid penuh tanpa blur / tanpa tembus pandang.
        val solid = tint.compositeOver(zkmLiteSolid(zkmIsDarkTheme()))
        return this.clip(shape).background(solid, shape)
    }
    val liquidRequest = resolveLiquidGlassCardRequest(isGlassActive)
    if (liquidRequest != null) {
        val (backdrop, spec) = liquidRequest
        // Tint dari call-site dibatasi supaya semua kartu liquid setipis kartu Home.
        return this.zkmLiquidGlassCard(
            backdrop = backdrop,
            spec = spec,
            tint = tint.copy(alpha = tint.alpha.coerceAtMost(0.30f)),
            borderColor = borderColor,
        )
    }
    val clipped = this.clip(shape)
    if (!isGlassActive || hazeState == null) return clipped.background(tint, shape)

    // Fast-scroll freeze (Zkm Perf Tahap 2): Saat user scroll kencang,
    // bekukan pipeline blur berat dan tampilkan scrim solid transparan sementara.
    // Menghilangkan 100% frame drops/jank selama interaksi scroll.
    val scrollFrozen = LocalZkmScrollInProgress.current || ZkmQualityGovernor.isScrollFrozen
    if (scrollFrozen) {
        val isDark = zkmIsDarkTheme()
        val scrim = tint.compositeOver(if (isDark) Color.Black.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.45f))
        return clipped.background(scrim, shape).border(1.dp, borderColor.copy(alpha = 0.12f), shape)
    }

    return clipped
        .zkmHazeEffect(
            state = hazeState,
            // Style di-cache global (ZkmHazeStyleCache): instance yang sama
            // dipakai ulang di semua kartu, jadi recomposition saat scroll
            // tidak lagi memaksa haze mengonfigurasi ulang pipeline blur.
            style = ZkmHazeStyleCache.cardStyle(blurRadius),
        )
        .background(tint, shape)
        .border(1.dp, borderColor, shape)
}


/**
 * HazeState layar aktif, dipasang oleh layar yang punya hazeSource sendiri.
 * Dipakai komponen kecil yang tidak menerima hazeState lewat parameter
 * supaya tetap bisa jadi card glass (bukan putih 10% yang nyaris invisible).
 */
val LocalZkmHazeState = staticCompositionLocalOf<HazeState?> { null }
