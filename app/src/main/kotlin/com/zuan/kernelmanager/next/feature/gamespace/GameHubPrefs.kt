/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 *
 * Game Space Hub - penyimpanan setelan untuk Super Base, Plugin Library, Game Center,
 * Control Panel dan Atmosphere. Setelan yang nyata dibaca [GameHubEngine]; sisanya masih
 * kerangka UI (lihat catatan di halaman masing-masing).
 */
package com.zuan.kernelmanager.next.feature.gamespace

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

object GameHubPrefs {
    private const val FILE = "zkm_gamespace_hub"

    const val STYLE = "look_style"
    const val LED_ON = "led_on"
    const val LED_FX = "led_fx"
    const val LED_COLOR = "led_color"
    const val BG_DIM = "bg_dim"
    /** Indeks gaya latar "Custom image" (gambar dari Galeri). */
    const val STYLE_IMAGE = 10
    const val BG_FILE = "gamespace_bg.jpg"

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun bool(c: Context, key: String, def: Boolean): Boolean = sp(c).getBoolean(key, def)
    fun setBool(c: Context, key: String, v: Boolean) = sp(c).edit().putBoolean(key, v).apply()
    fun int(c: Context, key: String, def: Int): Int = sp(c).getInt(key, def)
    fun setInt(c: Context, key: String, v: Int) = sp(c).edit().putInt(key, v).apply()

    /**
     * Nilai efektif sebuah setelan hub: profil game -> profil global -> kunci polos (Game Center) -> [def].
     * Control Panel menyimpan di `pg_<pkg|global>_<key>`, Game Center di kunci polos.
     */
    private fun effKey(c: Context, pkg: String?, key: String): String? {
        val p = pkg?.takeIf { it.isNotEmpty() }
        val sp = sp(c)
        return listOf(game(p, key), game(null, key), key).firstOrNull { sp.contains(it) }
    }

    fun effBool(c: Context, pkg: String?, key: String, def: Boolean): Boolean =
        effKey(c, pkg, key)?.let { sp(c).getBoolean(it, def) } ?: def

    fun effInt(c: Context, pkg: String?, key: String, def: Int): Int =
        effKey(c, pkg, key)?.let { sp(c).getInt(it, def) } ?: def

    fun pluginOn(c: Context, id: String): Boolean = bool(c, plugin(id), false)

    /** Kunci saklar plugin. */
    fun plugin(id: String) = "plugin_$id"

    /** Kunci setelan per game; [pkg] null = profil global. */
    fun game(pkg: String?, key: String) = "pg_${pkg ?: "global"}_$key"

    /** Hapus semua setelan Control Panel milik satu profil. */
    fun clearGame(c: Context, pkg: String?) {
        val prefix = "pg_${pkg ?: "global"}_"
        val e = sp(c).edit()
        sp(c).all.keys.filter { it.startsWith(prefix) }.forEach { e.remove(it) }
        e.apply()
    }
}

/** Nilai yang tersimpan: tulis lewat [value] dan otomatis disimpan + memicu recompose. */
internal class HubPref<T>(private val st: MutableState<T>, private val save: (T) -> Unit) {
    var value: T
        get() = st.value
        set(v) {
            st.value = v
            save(v)
        }
}

@Composable
internal fun rememberHubBool(key: String, def: Boolean): HubPref<Boolean> {
    val c = LocalContext.current
    return remember(key) {
        HubPref(mutableStateOf(GameHubPrefs.bool(c, key, def))) { GameHubPrefs.setBool(c, key, it) }
    }
}

@Composable
internal fun rememberHubInt(key: String, def: Int): HubPref<Int> {
    val c = LocalContext.current
    return remember(key) {
        HubPref(mutableStateOf(GameHubPrefs.int(c, key, def))) { GameHubPrefs.setInt(c, key, it) }
    }
}

/** Warna LED yang bisa dipilih. Indeks -1 = ikut aksen Game Space. */
internal val HubLedColors: List<Color> = listOf(
    Color(0xFFFF4D5E), Color(0xFFFF6FB5), Color(0xFFFF9F5A), Color(0xFFFFD84D),
    Color(0xFF5DF08A), Color(0xFF4DE6FF), Color(0xFF4D8DFF), Color(0xFFC56BFF)
)

/** Tampilan latar konsol: gaya backdrop + LED tepi. */
internal data class HubLook(
    val style: Int = 0,
    val ledOn: Boolean = false,
    val ledFx: Int = 0,
    val ledColor: Color = Color.Unspecified,
    /** Gambar latar dari Galeri (dipakai bila [style] == [GameHubPrefs.STYLE_IMAGE]). */
    val bg: ImageBitmap? = null,
    val bgDim: Float = 0.5f
)

/** Pemegang state tampilan; dibuat sekali di layar utama dan dioper ke dialog Atmosphere. */
internal class HubLookState(private val c: Context) {
    var style by mutableIntStateOf(GameHubPrefs.int(c, GameHubPrefs.STYLE, 0))
        private set
    var ledOn by mutableStateOf(GameHubPrefs.bool(c, GameHubPrefs.LED_ON, false))
        private set
    var ledFx by mutableIntStateOf(GameHubPrefs.int(c, GameHubPrefs.LED_FX, 0))
        private set
    var ledColorIdx by mutableIntStateOf(GameHubPrefs.int(c, GameHubPrefs.LED_COLOR, -1))
        private set

    fun updateStyle(v: Int) { style = v; GameHubPrefs.setInt(c, GameHubPrefs.STYLE, v) }
    fun updateLedOn(v: Boolean) { ledOn = v; GameHubPrefs.setBool(c, GameHubPrefs.LED_ON, v) }
    fun updateLedFx(v: Int) { ledFx = v; GameHubPrefs.setInt(c, GameHubPrefs.LED_FX, v) }
    fun updateLedColor(v: Int) { ledColorIdx = v; GameHubPrefs.setInt(c, GameHubPrefs.LED_COLOR, v) }

    // ---- gambar latar dari Galeri ----
    var bgImage by mutableStateOf<ImageBitmap?>(null)
        private set
    var bgDim by mutableIntStateOf(GameHubPrefs.int(c, GameHubPrefs.BG_DIM, 50))
        private set

    fun updateBgDim(v: Int) { bgDim = v.coerceIn(0, 90); GameHubPrefs.setInt(c, GameHubPrefs.BG_DIM, bgDim) }

    private fun bgFile() = File(c.applicationContext.filesDir, GameHubPrefs.BG_FILE)

    /** Muat gambar tersimpan (aman dipanggil berulang). */
    suspend fun loadBg() {
        val img = withContext(Dispatchers.IO) {
            runCatching { bgFile().takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }.getOrNull()
        }
        bgImage = img
    }

    /**
     * Salin gambar pilihan pengguna ke penyimpanan privat app (diperkecil maks 1920 px, JPEG 88) lalu pakai sebagai latar.
     * Mengembalikan false bila gambar tidak bisa dibaca.
     */
    suspend fun importBg(uri: Uri): Boolean {
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                val res = c.applicationContext.contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                res.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
                val bmp = res.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: return@runCatching false
                val scale = 1920f / maxOf(bmp.width, bmp.height)
                val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
                bgFile().outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                true
            }.getOrDefault(false)
        }
        if (ok) { loadBg(); updateStyle(GameHubPrefs.STYLE_IMAGE) }
        return ok
    }

    fun clearBg() {
        runCatching { bgFile().delete() }
        bgImage = null
        if (style == GameHubPrefs.STYLE_IMAGE) updateStyle(0)
    }

    fun toLook() = HubLook(
        style, ledOn, ledFx,
        if (ledColorIdx in HubLedColors.indices) HubLedColors[ledColorIdx] else Color.Unspecified,
        bgImage, bgDim / 100f
    )
}
