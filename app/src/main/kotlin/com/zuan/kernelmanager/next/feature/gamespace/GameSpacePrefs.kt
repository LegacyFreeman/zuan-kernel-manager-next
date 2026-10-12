/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 *
 * Game Space - original ZKM implementation (no code taken from any other project).
 */
package com.zuan.kernelmanager.next.feature.gamespace

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/** Setelan yang diterapkan saat sebuah game dibuka. Dipakai untuk default global & override per game. */
data class GameSettings(
    val dnd: Boolean = true,
    val blockHeadsUp: Boolean = true,
    val lockBrightnessMode: Boolean = false,
    val performanceMode: Boolean = true,
    val clearRamOnLaunch: Boolean = false,
    /** 0 = tidak diubah. Jika > 0, refresh rate dikunci ke nilai ini (bergantung ROM/OEM). */
    val refreshRateHz: Int = 0,
    val crosshair: Boolean = false,
    val crosshairStyle: Int = 0,
    val crosshairSizeDp: Int = 28,
    val crosshairOpacity: Int = 90,
    val showPanel: Boolean = true
) {
    fun toJson(): String = JSONObject().apply {
        put("dnd", dnd); put("heads", blockHeadsUp); put("bright", lockBrightnessMode)
        put("perf", performanceMode); put("ram", clearRamOnLaunch); put("hz", refreshRateHz)
        put("ch", crosshair); put("chStyle", crosshairStyle); put("chSize", crosshairSizeDp)
        put("chAlpha", crosshairOpacity); put("panel", showPanel)
    }.toString()

    companion object {
        fun fromJson(raw: String?): GameSettings {
            if (raw.isNullOrBlank()) return GameSettings()
            return runCatching {
                val j = JSONObject(raw)
                val d = GameSettings()
                GameSettings(
                    dnd = j.optBoolean("dnd", d.dnd),
                    blockHeadsUp = j.optBoolean("heads", d.blockHeadsUp),
                    lockBrightnessMode = j.optBoolean("bright", d.lockBrightnessMode),
                    performanceMode = j.optBoolean("perf", d.performanceMode),
                    clearRamOnLaunch = j.optBoolean("ram", d.clearRamOnLaunch),
                    refreshRateHz = j.optInt("hz", d.refreshRateHz),
                    crosshair = j.optBoolean("ch", d.crosshair),
                    crosshairStyle = j.optInt("chStyle", d.crosshairStyle),
                    crosshairSizeDp = j.optInt("chSize", d.crosshairSizeDp),
                    crosshairOpacity = j.optInt("chAlpha", d.crosshairOpacity),
                    showPanel = j.optBoolean("panel", d.showPanel)
                )
            }.getOrDefault(GameSettings())
        }
    }
}

enum class PanelEdge { LEFT, RIGHT }

/** Aksen panel (ARGB). Overlay tidak punya akses ke tema Compose aplikasi, jadi aksen disimpan di sini. */
val GameAccentPresets: List<Long> = listOf(
    0xFFE5304A, 0xFF7DB4F5, 0xFF5EEAD4, 0xFFA78BFA, 0xFFF472B6, 0xFFFBBF24, 0xFFF87171, 0xFF86EFAC
)

/** Penyimpanan Game Space (SharedPreferences, satu file) - dibaca dari Activity maupun service overlay. */
object GameSpacePrefs {
    private const val FILE = "zkm_gamespace"
    private const val K_ENABLED = "enabled"
    private const val K_GAMES = "games"
    private const val K_GLOBAL = "global"
    private const val K_EDGE = "edge"
    private const val K_EDGE_Y = "edge_y"
    private const val K_ACCENT = "accent"
    private const val K_AUTOHIDE = "autohide_s"
    private const val K_STYLE = "panel_style"
    private const val K_CPU_MODE = "cpu_mode"
    private const val K_SCREEN_MODE = "screen_mode"
    private const val K_PER_GAME = "pg_"
    private const val K_PLAYTIME = "pt_"
    private const val K_LASTPLAYED = "lp_"

    private fun sp(c: Context): SharedPreferences = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isEnabled(c: Context) = sp(c).getBoolean(K_ENABLED, false)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean(K_ENABLED, v).apply()

    fun games(c: Context): List<String> =
        sp(c).getString(K_GAMES, "").orEmpty().split(',').filter { it.isNotBlank() }

    fun addGame(c: Context, pkg: String) {
        val cur = games(c)
        if (pkg !in cur) sp(c).edit().putString(K_GAMES, (cur + pkg).joinToString(",")).apply()
    }

    fun removeGame(c: Context, pkg: String) {
        sp(c).edit()
            .putString(K_GAMES, games(c).filter { it != pkg }.joinToString(","))
            .remove(K_PER_GAME + pkg).apply()
    }

    fun isGame(c: Context, pkg: String) = pkg in games(c)

    fun globalSettings(c: Context) = GameSettings.fromJson(sp(c).getString(K_GLOBAL, null))
    fun setGlobalSettings(c: Context, s: GameSettings) = sp(c).edit().putString(K_GLOBAL, s.toJson()).apply()

    fun hasOverride(c: Context, pkg: String) = sp(c).contains(K_PER_GAME + pkg)
    fun setOverride(c: Context, pkg: String, s: GameSettings?) {
        val e = sp(c).edit()
        if (s == null) e.remove(K_PER_GAME + pkg) else e.putString(K_PER_GAME + pkg, s.toJson())
        e.apply()
    }

    /** Setelan efektif untuk [pkg]: override per game bila ada, jika tidak default global. */
    fun settingsFor(c: Context, pkg: String): GameSettings =
        sp(c).getString(K_PER_GAME + pkg, null)?.let { GameSettings.fromJson(it) } ?: globalSettings(c)

    fun edge(c: Context) = if (sp(c).getString(K_EDGE, "RIGHT") == "LEFT") PanelEdge.LEFT else PanelEdge.RIGHT
    fun setEdge(c: Context, e: PanelEdge) = sp(c).edit().putString(K_EDGE, e.name).apply()

    fun edgeY(c: Context) = sp(c).getInt(K_EDGE_Y, 360)
    fun setEdgeY(c: Context, y: Int) = sp(c).edit().putInt(K_EDGE_Y, y).apply()

    fun accent(c: Context): Long = sp(c).getLong(K_ACCENT, GameAccentPresets.first())
    fun setAccent(c: Context, argb: Long) = sp(c).edit().putLong(K_ACCENT, argb).apply()

    /** Detik sebelum panel menutup sendiri; 0 = tidak pernah. */
    fun autoHideSeconds(c: Context) = sp(c).getInt(K_AUTOHIDE, 0)
    fun setAutoHideSeconds(c: Context, s: Int) = sp(c).edit().putInt(K_AUTOHIDE, s).apply()

    /** 0 = Energy Cube (layar penuh, landscape), 1 = panel samping ringkas. */
    fun panelStyle(c: Context) = sp(c).getInt(K_STYLE, 0)
    fun setPanelStyle(c: Context, v: Int) = sp(c).edit().putInt(K_STYLE, v).apply()

    /** Mode CPU hub Energy Cube: 0 = Balance, 1 = Dynamic, 2 = Performance. */
    fun cpuMode(c: Context) = sp(c).getInt(K_CPU_MODE, 0)
    fun setCpuMode(c: Context, v: Int) = sp(c).edit().putInt(K_CPU_MODE, v).apply()

    /** Mode layar Game Space: 0 = Explore (rotasi bebas), 1 = Handheld (16:9 landscape + konsol layar penuh). */
    const val SCREEN_EXPLORE = 0
    const val SCREEN_HANDHELD = 1
    fun screenMode(c: Context) = sp(c).getInt(K_SCREEN_MODE, SCREEN_EXPLORE)
    fun setScreenMode(c: Context, v: Int) = sp(c).edit().putInt(K_SCREEN_MODE, v).apply()

    fun addPlaytime(c: Context, pkg: String, ms: Long) {
        if (ms <= 0) return
        sp(c).edit()
            .putLong(K_PLAYTIME + pkg, playtimeMs(c, pkg) + ms)
            .putLong(K_LASTPLAYED + pkg, System.currentTimeMillis()).apply()
    }

    fun playtimeMs(c: Context, pkg: String) = sp(c).getLong(K_PLAYTIME + pkg, 0L)
    fun lastPlayed(c: Context, pkg: String) = sp(c).getLong(K_LASTPLAYED + pkg, 0L)
}
