/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 *
 * Game Space - mode CPU untuk hub Energy Cube: Balance / Dynamic / Performance.
 *
 *  - ROOT   : benar-benar menulis governor + batas frekuensi tiap cpufreq policy lewat sysfs,
 *             lalu membaca balik untuk memastikan berhasil.
 *  - Shizuku root (uid 0): sama dengan Root.
 *  - Shizuku (ADB) : sysfs CPU tidak bisa ditulis dari shell uid 2000, jadi mode dipetakan ke tweak no-root
 *             yang nyata lewat [NonRootBoost] (Game Mode resmi, fixed performance, thermal-aware Dynamic).
 *  - Basic : mode hanya disimpan dan dipakai UI (warna + animasi).
 *
 * Pesan lama "Command failed: Root command failed" muncul karena tombol ini dulu memanggil
 * `cmd game mode performance <pkg>` yang tidak didukung semua ROM dan gagal tanpa pesan error.
 */
package com.zuan.kernelmanager.next.feature.gamespace

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime
import com.zuan.kernelmanager.next.library.shell.PrivilegedAccess
import java.io.File

enum class CpuMode(val labelRes: Int, val color: Color) {
    BALANCE(R.string.gs_hub_mode_balance, Color(0xFF34D17A)),
    DYNAMIC(R.string.gs_hub_mode_dynamic, Color(0xFFFFC82E)),
    PERFORMANCE(R.string.gs_hub_mode_performance, Color(0xFFFF2D4A));

    fun next(): CpuMode = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromOrdinal(i: Int): CpuMode = entries.getOrElse(i) { BALANCE }
    }
}

object CpuModeEngine {

    data class Result(val ok: Boolean, val message: String?)

    private const val BASE = "/sys/devices/system/cpu/cpufreq"

    /** Nilai asli (governor/min/max per policy) yang direkam sebelum mode pertama diterapkan. */
    @Volatile private var savedLines: List<String>? = null

    /** Frekuensi maksimum CPU (MHz) dari cpuinfo_max_freq - dibaca langsung, tanpa shell. */
    fun maxMHz(): Float {
        val dirs = File(BASE).listFiles { f -> f.name.startsWith("policy") } ?: return 0f
        return dirs.maxOfOrNull {
            runCatching { File(it, "cpuinfo_max_freq").readText().trim().toFloat() }.getOrDefault(0f)
        }?.let { if (it >= 10_000f) it / 1000f else it } ?: 0f
    }

    fun apply(c: Context, mode: CpuMode): Result =
        if (PrivilegedAccess.rootEquivalent()) applyRoot(c, mode) else applyNonRoot(c, mode)

    /** Shizuku ADB / Basic: sysfs tidak bisa ditulis -> Game Mode resmi + fixed performance (lihat [NonRootBoost]). */
    private fun applyNonRoot(c: Context, mode: CpuMode): Result = NonRootBoost.applyMode(c, mode)

    private fun govs(mode: CpuMode) = when (mode) {
        CpuMode.BALANCE -> "schedutil walt sched_pixel interactive ondemand"
        CpuMode.DYNAMIC -> "schedutil walt sched_pixel interactive ondemand"
        CpuMode.PERFORMANCE -> "performance"
    }

    // '§' dipakai sebagai pengganti '$' supaya skrip shell tidak bentrok dengan templat string Kotlin.
    private fun script(mode: CpuMode): String {
        val minExpr = when (mode) {
            // Balance : batas bawah dikembalikan ke minimum perangkat.
            CpuMode.BALANCE -> """echo §mn > §p/scaling_min_freq 2>/dev/null"""
            // Dynamic : batas bawah dinaikkan ke ~40% frekuensi maks supaya respons lebih sigap.
            CpuMode.DYNAMIC -> """
                t=§((mx*40/100)); f=""
                for x in §(cat §p/scaling_available_frequencies 2>/dev/null); do
                  if [ "§x" -ge "§t" ]; then f=§x; break; fi
                done
                if [ -n "§f" ]; then echo §f > §p/scaling_min_freq 2>/dev/null; else echo §mn > §p/scaling_min_freq 2>/dev/null; fi
            """.trimIndent()
            // Performance : governor performance bila ada; jika tidak, kunci min = max.
            CpuMode.PERFORMANCE -> """
                if [ "§g" != "performance" ]; then echo §mx > §p/scaling_min_freq 2>/dev/null; fi
            """.trimIndent()
        }
        return """
            ok=0; tot=0
            for p in $BASE/policy*; do
              [ -d "§p" ] || continue
              tot=§((tot+1))
              mx=§(cat §p/cpuinfo_max_freq 2>/dev/null); mn=§(cat §p/cpuinfo_min_freq 2>/dev/null)
              av=§(cat §p/scaling_available_governors 2>/dev/null)
              g=""
              for c in ${govs(mode)}; do
                case " §av " in *" §c "*) g=§c; break;; esac
              done
              chmod 644 §p/scaling_governor §p/scaling_min_freq §p/scaling_max_freq 2>/dev/null
              echo §mn > §p/scaling_min_freq 2>/dev/null
              echo §mx > §p/scaling_max_freq 2>/dev/null
              if [ -n "§g" ]; then echo §g > §p/scaling_governor 2>/dev/null; fi
              $minExpr
              cur=§(cat §p/scaling_governor 2>/dev/null)
              if [ -n "§g" ] && [ "§cur" = "§g" ]; then ok=§((ok+1)); fi
            done
            echo "ZKMCPU §ok §tot"
        """.trimIndent().replace('§', '$')
    }

    private fun snapshotScript(): String = """
        for p in $BASE/policy*; do
          [ -d "§p" ] || continue
          echo "ZKMSAVE §p §(cat §p/scaling_governor 2>/dev/null) §(cat §p/scaling_min_freq 2>/dev/null) §(cat §p/scaling_max_freq 2>/dev/null)"
        done
    """.trimIndent().replace('§', '$')

    private fun applyRoot(c: Context, mode: CpuMode): Result {
        KernelRootRuntime.initialize(c.applicationContext)
        if (savedLines == null) {
            val snap = KernelRootRuntime.executeBlocking(snapshotScript())
            savedLines = snap.out.filter { it.startsWith("ZKMSAVE ") }.takeIf { it.isNotEmpty() }
        }
        // Custom Mode: bila mode ini sudah di-custom, isinya menggantikan skrip bawaan (satu sumber kebenaran).
        val prof = CustomModeStore.get(c, mode)
        val sc = if (prof.enabled) CustomModeEngine.cpuScript(prof, BASE, govs(mode)) else script(mode)
        val r = KernelRootRuntime.executeBlocking(sc)
        runCatching { CustomModeEngine.applyGpu(c, if (prof.enabled) prof else null) }
        val line = r.out.lastOrNull { it.startsWith("ZKMCPU ") }
        if (line == null) {
            val why = r.errorString.lineSequence().firstOrNull { it.isNotBlank() }?.take(80)
                ?: KernelRootRuntime.lastErrorText()?.take(80) ?: "?"
            return Result(false, if (why.contains("denied", true)) c.getString(R.string.gs_root_denied)
                                 else c.getString(R.string.gs_mode_failed, why))
        }
        val parts = line.split(' ')
        val ok = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val tot = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return if (ok > 0) Result(true, null)
        else Result(false, c.getString(R.string.gs_mode_failed, if (tot == 0) "cpufreq?" else "governor locked"))
    }

    /** Kembalikan governor & batas frekuensi seperti sebelum mode pertama diterapkan (dipanggil saat sesi game berakhir). */
    fun restore(c: Context) {
        runCatching { CustomModeEngine.restoreGpu(c) }
        val saved = savedLines ?: return
        if (!PrivilegedAccess.rootEquivalent()) return
        savedLines = null
        val cmds = saved.mapNotNull { l ->
            val p = l.removePrefix("ZKMSAVE ").trim().split(' ')
            if (p.size < 4) null
            else "echo ${p[3]} > ${p[0]}/scaling_max_freq; echo ${p[2]} > ${p[0]}/scaling_min_freq; echo ${p[1]} > ${p[0]}/scaling_governor"
        }
        if (cmds.isNotEmpty()) KernelRootRuntime.executeBlocking(cmds.joinToString("; "))
    }
}
