/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 *
 * Game Space - Root Tools: optimasi tingkat sistem ala ROG / RedMagic yang hanya bisa jalan dengan Root.
 * Semua fitur NYATA (menulis sysfs / proc / perintah sistem), dicatat nilai aslinya, dan dikembalikan
 * saat sesi game selesai. Laporan hasilnya ([Report]) dipakai splash Energy Cube, jadi teks yang tampil
 * di splash hanya menyebut hal yang benar-benar dikerjakan.
 */
package com.zuan.kernelmanager.next.feature.gamespace

import android.app.ActivityManager
import android.content.Context
import com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime
import com.zuan.kernelmanager.next.core.mode.WorkingMode
import com.zuan.kernelmanager.next.core.mode.WorkingModeManager

object RootBoost {

    const val RAM = "rt_ram"
    const val PRIO = "rt_prio"
    const val SCHED = "rt_sched"
    const val IO = "rt_io"
    const val SWAP = "rt_swap"
    const val SPLASH = "rt_splash"

    /** Fitur yang aktif bila pengguna belum memilih apa pun. */
    private val defaults = mapOf(RAM to true, PRIO to true, SCHED to false, IO to false, SWAP to false, SPLASH to true)

    fun isOn(c: Context, key: String): Boolean = GameHubPrefs.bool(c, key, defaults[key] ?: false)
    fun setOn(c: Context, key: String, v: Boolean) = GameHubPrefs.setBool(c, key, v)

    /** sysfs/proc bisa ditulis: Root, atau Shizuku yang berjalan sebagai root. Shizuku ADB murni -> false (pakai [NonRootBoost]). */
    fun rootAvailable(): Boolean = com.zuan.kernelmanager.next.library.shell.PrivilegedAccess.rootEquivalent()

    /** Hasil satu sesi optimasi. [steps] = kunci pesan yang benar-benar berhasil. */
    data class Report(val steps: List<Step>, val freedMb: Int, val root: Boolean) {
        val applied: Boolean get() = steps.isNotEmpty()
    }

    enum class Step { RAM, PRIORITY, SCHED, IO, SWAP, GAMEMODE, FIXED, BGLIMIT, BATSAVER }

    private data class Saved(val sched: String?, val swap: String?, val readAhead: List<String>)
    @Volatile private var saved: Saved? = null

    private fun sh(c: Context, cmd: String): com.zuan.kernelmanager.next.library.shell.SuResult {
        KernelRootRuntime.initialize(c.applicationContext)
        return KernelRootRuntime.executeBlocking(cmd)
    }

    /** Shell sadar mode: Root / Shizuku-root / Shizuku ADB. Dipakai [DynamicManager] untuk RAM & Storage. */
    internal fun shell(c: Context, cmd: String, timeoutMs: Long = 8000L): com.zuan.kernelmanager.next.library.shell.SuResult =
        NonRootBoost.sh(c, timeoutMs, cmd)

    private fun availMb(c: Context): Long {
        val mi = ActivityManager.MemoryInfo()
        c.getSystemService(ActivityManager::class.java)?.getMemoryInfo(mi)
        return mi.availMem / (1024 * 1024)
    }

    /** Jalankan semua fitur yang dinyalakan. Aman dipanggil di thread latar. Tanpa Root -> laporan kosong. */
    @Synchronized
    fun apply(c: Context, pkg: String): Report {
        val app = c.applicationContext
        restore(app)
        // Tanpa root (Shizuku ADB murni): jalankan tweak no-root yang NYATA, bukan laporan kosong.
        if (!rootAvailable()) return NonRootBoost.apply(app, pkg, CpuMode.fromOrdinal(GameSpacePrefs.cpuMode(app)))

        val steps = mutableListOf<Step>()
        var freed = 0

        if (isOn(app, RAM)) {
            val before = availMb(app)
            // am kill-all hanya mematikan proses cache di latar; drop_caches membuang page cache.
            val r = sh(app, "am kill-all; sync; echo 3 > /proc/sys/vm/drop_caches 2>/dev/null; echo ZKMOK")
            if (r.out.any { it.contains("ZKMOK") }) {
                freed = (availMb(app) - before).coerceAtLeast(0L).toInt()
                steps += Step.RAM
            }
        }

        if (isOn(app, PRIO) && Regex("[A-Za-z0-9_.]+").matches(pkg)) {
            // Naikkan prioritas CPU + I/O proses game dan jauhkan dari pembunuh memori (oom).
            val r = sh(
                app,
                """
                pid=§(pidof $pkg 2>/dev/null | cut -d' ' -f1)
                if [ -n "§pid" ]; then
                  renice -n -10 -p §pid >/dev/null 2>&1
                  for t in /proc/§pid/task/*; do renice -n -10 -p §(basename §t) >/dev/null 2>&1; done
                  ionice -c 1 -n 0 -p §pid >/dev/null 2>&1
                  echo -500 > /proc/§pid/oom_score_adj 2>/dev/null
                  echo ZKMOK
                fi
                """.trimIndent().replace('§', '$')
            )
            if (r.out.any { it.contains("ZKMOK") }) steps += Step.PRIORITY
        }

        var schedPrev: String? = null
        if (isOn(app, SCHED)) {
            val r = sh(
                app,
                """
                f=/proc/sys/kernel/sched_boost
                if [ -w "§f" ]; then echo "ZKMPREV §(cat §f)"; echo 1 > §f; echo ZKMOK; fi
                """.trimIndent().replace('§', '$')
            )
            if (r.out.any { it.contains("ZKMOK") }) {
                schedPrev = r.out.firstOrNull { it.startsWith("ZKMPREV ") }?.removePrefix("ZKMPREV ")?.trim()
                steps += Step.SCHED
            }
        }

        var swapPrev: String? = null
        if (isOn(app, SWAP)) {
            val r = sh(
                app,
                """
                f=/proc/sys/vm/swappiness
                if [ -w "§f" ]; then echo "ZKMPREV §(cat §f)"; echo 20 > §f; echo ZKMOK; fi
                """.trimIndent().replace('§', '$')
            )
            if (r.out.any { it.contains("ZKMOK") }) {
                swapPrev = r.out.firstOrNull { it.startsWith("ZKMPREV ") }?.removePrefix("ZKMPREV ")?.trim()
                steps += Step.SWAP
            }
        }

        var raPrev = emptyList<String>()
        if (isOn(app, IO)) {
            val r = sh(
                app,
                """
                for q in /sys/block/sd*/queue/read_ahead_kb /sys/block/mmcblk*/queue/read_ahead_kb; do
                  [ -w "§q" ] || continue
                  echo "ZKMRA §q §(cat §q)"
                  echo 1024 > §q 2>/dev/null
                done
                echo ZKMOK
                """.trimIndent().replace('§', '$')
            )
            raPrev = r.out.filter { it.startsWith("ZKMRA ") }
            if (raPrev.isNotEmpty()) steps += Step.IO
        }

        saved = Saved(schedPrev, swapPrev, raPrev)
        return Report(steps, freed, true)
    }

    /** Kembalikan nilai yang diubah [apply]. Aman dipanggil berulang. */
    @Synchronized
    fun restore(c: Context) {
        val st = saved ?: return
        saved = null
        if (!rootAvailable()) return
        val cmds = mutableListOf<String>()
        st.sched?.takeIf { it.all(Char::isDigit) }?.let { cmds += "echo $it > /proc/sys/kernel/sched_boost 2>/dev/null" }
        st.swap?.takeIf { it.all(Char::isDigit) }?.let { cmds += "echo $it > /proc/sys/vm/swappiness 2>/dev/null" }
        st.readAhead.forEach { l ->
            val p = l.removePrefix("ZKMRA ").trim().split(' ')
            if (p.size >= 2) cmds += "echo ${p[1]} > ${p[0]} 2>/dev/null"
        }
        if (cmds.isNotEmpty()) sh(c, cmds.joinToString("; "))
    }
}
