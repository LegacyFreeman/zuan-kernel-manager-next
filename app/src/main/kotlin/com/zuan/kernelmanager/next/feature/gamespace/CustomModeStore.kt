/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 *
 * Game Space - Custom Mode.
 *
 * SATU sumber kebenaran untuk Balance / Dynamic / Performance:
 *  - Pemilih mode (tombol di lobby, Control Panel, overlay in-game) hanya memilih MODE MANA yang aktif.
 *  - Isi tiap mode (batas CPU, batas GPU, governor, thermal guard) dibaca dari [CustomModeStore].
 *  - Mode yang belum di-custom memakai perilaku bawaan lama (tidak ada yang berubah untuk pengguna lama).
 *  - Thermal guard SEKARANG per mode dan MATI secara bawaan (sebelumnya aktif sendiri di Game Center).
 *
 * Batas frekuensi disimpan sebagai persen dari frekuensi maksimum tiap cluster, jadi satu profil
 * tetap masuk akal di SoC mana pun, lalu dibulatkan ke frekuensi yang benar-benar tersedia.
 */
package com.zuan.kernelmanager.next.feature.gamespace

import android.content.Context
import com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime
import com.zuan.kernelmanager.next.library.shell.PrivilegedAccess
import com.zuan.kernelmanager.next.core.mode.WorkingMode
import com.zuan.kernelmanager.next.core.mode.WorkingModeManager
import java.io.File

/** Isi satu mode. [enabled] = false -> pakai perilaku bawaan mode tersebut. */
data class ModeProfile(
    val enabled: Boolean,
    val cpuMinPct: Int,
    val cpuMaxPct: Int,
    val gpuMinPct: Int,
    val gpuMaxPct: Int,
    /** Kosong = otomatis (daftar governor bawaan mode). */
    val governor: String,
    val thermalOn: Boolean,
    val thermalLimitC: Int,
    /** Batas per cluster (kunci = nama policy, mis. "policy4") dalam persen frekuensi maks cluster itu. Kosong = ikut batas global di atas. */
    val clusterLimits: Map<String, Pair<Int, Int>> = emptyMap()
) {
    companion object {
        /** Nilai awal yang meniru perilaku bawaan tiap mode. */
        fun defaults(mode: CpuMode) = when (mode) {
            CpuMode.BALANCE -> ModeProfile(false, 0, 100, 0, 100, "", false, 46)
            CpuMode.DYNAMIC -> ModeProfile(false, 40, 100, 0, 100, "", false, 45)
            CpuMode.PERFORMANCE -> ModeProfile(false, 100, 100, 60, 100, "performance", false, 44)
        }
    }
}

object CustomModeStore {
    private const val FILE = "zkm_gamespace_custom_mode"

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private fun k(mode: CpuMode, f: String) = "cm_${mode.name}_$f"

    fun get(c: Context, mode: CpuMode): ModeProfile {
        val d = ModeProfile.defaults(mode)
        val s = sp(c)
        return ModeProfile(
            enabled = s.getBoolean(k(mode, "on"), d.enabled),
            cpuMinPct = s.getInt(k(mode, "cmin"), d.cpuMinPct),
            cpuMaxPct = s.getInt(k(mode, "cmax"), d.cpuMaxPct),
            gpuMinPct = s.getInt(k(mode, "gmin"), d.gpuMinPct),
            gpuMaxPct = s.getInt(k(mode, "gmax"), d.gpuMaxPct),
            governor = s.getString(k(mode, "gov"), d.governor) ?: "",
            thermalOn = s.getBoolean(k(mode, "tOn"), d.thermalOn),
            thermalLimitC = s.getInt(k(mode, "tLim"), d.thermalLimitC),
            clusterLimits = decodeClusters(s.getString(k(mode, "cl"), "") ?: "")
        )
    }

    /** Format simpan: "policy0=0-100,policy4=20-80". */
    private fun encodeClusters(m: Map<String, Pair<Int, Int>>) =
        m.entries.joinToString(",") { "${it.key}=${it.value.first}-${it.value.second}" }

    private fun decodeClusters(raw: String): Map<String, Pair<Int, Int>> =
        raw.split(',').mapNotNull { e ->
            val kv = e.split('=')
            val mm = kv.getOrNull(1)?.split('-')
            val lo = mm?.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val hi = mm.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            if (kv[0].isBlank()) null else kv[0] to (lo to hi)
        }.toMap()

    fun set(c: Context, mode: CpuMode, p: ModeProfile) {
        // min tidak boleh melewati max; dijaga di satu tempat supaya UI & engine selalu konsisten
        val cMax = p.cpuMaxPct.coerceIn(10, 100)
        val cMin = p.cpuMinPct.coerceIn(0, cMax)
        val gMax = p.gpuMaxPct.coerceIn(10, 100)
        val gMin = p.gpuMinPct.coerceIn(0, gMax)
        sp(c).edit()
            .putBoolean(k(mode, "on"), p.enabled)
            .putInt(k(mode, "cmin"), cMin).putInt(k(mode, "cmax"), cMax)
            .putInt(k(mode, "gmin"), gMin).putInt(k(mode, "gmax"), gMax)
            .putString(k(mode, "gov"), p.governor)
            .putBoolean(k(mode, "tOn"), p.thermalOn)
            .putInt(k(mode, "tLim"), p.thermalLimitC.coerceIn(38, 55))
            .putString(k(mode, "cl"), encodeClusters(p.clusterLimits.mapValues { (_, v) ->
                val hi = v.second.coerceIn(10, 100); v.first.coerceIn(0, hi) to hi
            }))
            .apply()
    }

    fun reset(c: Context, mode: CpuMode) {
        val e = sp(c).edit()
        sp(c).all.keys.filter { it.startsWith("cm_${mode.name}_") }.forEach { e.remove(it) }
        e.apply()
    }
}

/** Pembacaan sysfs (tanpa root, umumnya bisa dibaca) + penerapan GPU untuk profil custom. */
object CustomModeEngine {
    private const val CPU = "/sys/devices/system/cpu/cpufreq"

    data class Cluster(val name: String, val maxMHz: Int)

    /** Cluster CPU (policy) beserta frekuensi maksimumnya, dari yang terkecil. Kosong bila sysfs tidak terbaca. */
    fun clusters(): List<Cluster> {
        val dirs = File(CPU).listFiles { f -> f.name.startsWith("policy") } ?: return emptyList()
        return dirs.sortedBy { it.name.removePrefix("policy").toIntOrNull() ?: 0 }.mapNotNull { d ->
            val mx = runCatching { File(d, "cpuinfo_max_freq").readText().trim().toLong() }.getOrNull() ?: return@mapNotNull null
            Cluster(d.name, (if (mx >= 10_000L) mx / 1000 else mx).toInt())
        }
    }

    fun governors(): List<String> {
        val d = File(CPU).listFiles { f -> f.name.startsWith("policy") }?.minByOrNull { it.name } ?: return emptyList()
        return runCatching { File(d, "scaling_available_governors").readText().trim().split(' ').filter { it.isNotBlank() } }
            .getOrDefault(emptyList())
    }

    fun gpuMaxMHz(): Int = runCatching {
        com.zuan.kernelmanager.next.library.device.monitor.GpuReader.maxFreqMHz()
    }.getOrDefault(0)

    // ---- GPU --------------------------------------------------------------------------------

    @Volatile private var gpuSaved: List<String>? = null

    /** Tulis min/max GPU (devfreq) menurut [p]; null = kembalikan nilai asli. Hanya Root. */
    fun applyGpu(c: Context, p: ModeProfile?) {
        if (!PrivilegedAccess.rootEquivalent()) return
        if (p == null) { restoreGpu(c); return }
        KernelRootRuntime.initialize(c.applicationContext)
        if (gpuSaved == null) {
            val snap = KernelRootRuntime.executeBlocking(
                """
                for d in /sys/class/kgsl/kgsl-3d0/devfreq /sys/class/devfreq/*; do
                  [ -f "§d/min_freq" ] || continue
                  case "§d" in *kgsl*|*gpu*|*GPU*|*mali*) ;; *) continue;; esac
                  echo "ZKMCSAVE §d §(cat §d/min_freq 2>/dev/null) §(cat §d/max_freq 2>/dev/null)"
                done
                """.trimIndent().replace('§', '$')
            )
            gpuSaved = snap.out.filter { it.startsWith("ZKMCSAVE ") }.takeIf { it.isNotEmpty() }
        }
        val script = """
            for d in /sys/class/kgsl/kgsl-3d0/devfreq /sys/class/devfreq/*; do
              [ -f "§d/min_freq" ] || continue
              case "§d" in *kgsl*|*gpu*|*GPU*|*mali*) ;; *) continue;; esac
              av=§(cat §d/available_frequencies 2>/dev/null)
              [ -n "§av" ] || continue
              top=0; bot=""
              for x in §av; do
                [ "§x" -gt "§top" ] && top=§x
                if [ -z "§bot" ] || [ "§x" -lt "§bot" ]; then bot=§x; fi
              done
              lo=§((top*${p.gpuMinPct}/100)); hi=§((top*${p.gpuMaxPct}/100))
              fmin=""; fmax=""
              for x in §av; do
                if [ "§x" -ge "§lo" ]; then if [ -z "§fmin" ] || [ "§x" -lt "§fmin" ]; then fmin=§x; fi; fi
                if [ "§x" -le "§hi" ]; then if [ -z "§fmax" ] || [ "§x" -gt "§fmax" ]; then fmax=§x; fi; fi
              done
              [ -n "§fmin" ] || fmin=§top
              [ -n "§fmax" ] || fmax=§bot
              if [ "§fmin" -gt "§fmax" ]; then fmin=§fmax; fi
              chmod 644 §d/min_freq §d/max_freq 2>/dev/null
              echo §top > §d/max_freq 2>/dev/null
              echo §fmin > §d/min_freq 2>/dev/null
              echo §fmax > §d/max_freq 2>/dev/null
            done
        """.trimIndent().replace('§', '$')
        KernelRootRuntime.executeBlocking(script)
    }

    fun restoreGpu(c: Context) {
        val saved = gpuSaved ?: return
        if (!PrivilegedAccess.rootEquivalent()) return
        gpuSaved = null
        val cmds = saved.mapNotNull { l ->
            val p = l.removePrefix("ZKMCSAVE ").trim().split(' ')
            if (p.size < 3) null
            else "echo ${p[2]} > ${p[0]}/max_freq 2>/dev/null; echo ${p[1]} > ${p[0]}/min_freq 2>/dev/null"
        }
        if (cmds.isNotEmpty()) KernelRootRuntime.executeBlocking(cmds.joinToString("; "))
    }

    /** Potongan skrip CPU untuk profil custom: dipakai [CpuModeEngine] sebagai pengganti skrip bawaan. */
    fun cpuScript(p: ModeProfile, baseDir: String, defaultGovs: String): String {
        val gov = p.governor.takeIf { it.isNotBlank() && it.all { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '-' } }
        val govs = if (gov != null) "$gov $defaultGovs" else defaultGovs
        // Batas per cluster: policy yang punya entri sendiri memakai itu, sisanya ikut batas global.
        val perCase = p.clusterLimits.entries
            .filter { e -> e.key.all { ch -> ch.isLetterOrDigit() } }
            .joinToString("\n") { e -> "  ${e.key}) pmin=${e.value.first}; pmax=${e.value.second};;" }
            .let { if (it.isBlank()) "  __none__) :;;" else it }
        return """
            ok=0; tot=0
            for p in $baseDir/policy*; do
              [ -d "§p" ] || continue
              tot=§((tot+1))
              mx=§(cat §p/cpuinfo_max_freq 2>/dev/null); mn=§(cat §p/cpuinfo_min_freq 2>/dev/null)
              av=§(cat §p/scaling_available_frequencies 2>/dev/null)
              avg=§(cat §p/scaling_available_governors 2>/dev/null)
              pmin=${p.cpuMinPct}; pmax=${p.cpuMaxPct}
              case "§{p##*/}" in
$perCase
              esac
              lo=§((mx*pmin/100)); hi=§((mx*pmax/100))
              fmin=""; fmax=""
              for x in §av; do
                if [ "§x" -ge "§lo" ]; then if [ -z "§fmin" ] || [ "§x" -lt "§fmin" ]; then fmin=§x; fi; fi
                if [ "§x" -le "§hi" ]; then if [ -z "§fmax" ] || [ "§x" -gt "§fmax" ]; then fmax=§x; fi; fi
              done
              if [ -z "§av" ]; then fmin=§lo; fmax=§hi; fi
              [ -n "§fmin" ] || fmin=§mx
              [ -n "§fmax" ] || fmax=§mn
              if [ "§fmin" -gt "§fmax" ]; then fmin=§fmax; fi
              g=""
              for c in $govs; do
                case " §avg " in *" §c "*) g=§c; break;; esac
              done
              chmod 644 §p/scaling_governor §p/scaling_min_freq §p/scaling_max_freq 2>/dev/null
              echo §mn > §p/scaling_min_freq 2>/dev/null
              echo §mx > §p/scaling_max_freq 2>/dev/null
              if [ -n "§g" ]; then echo §g > §p/scaling_governor 2>/dev/null; fi
              echo §fmax > §p/scaling_max_freq 2>/dev/null
              echo §fmin > §p/scaling_min_freq 2>/dev/null
              cur=§(cat §p/scaling_governor 2>/dev/null)
              if [ -n "§g" ] && [ "§cur" = "§g" ]; then ok=§((ok+1)); fi
            done
            echo "ZKMCPU §ok §tot"
        """.trimIndent().replace('§', '$')
    }
}
