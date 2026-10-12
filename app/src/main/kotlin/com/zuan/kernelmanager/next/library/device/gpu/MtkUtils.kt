/*
 * Original code from: Rem01Gaming (origami_kernel_manager) and helloklf (vtools)
 * Modified and integrated by: Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.library.device.gpu

import com.zuan.kernelmanager.next.library.shell.RootIpcManager
import com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime
import com.zuan.kernelmanager.next.core.kernel.KernelControlEngine

object MtkUtils {

    // === Helper IPC (daemon dulu, fallback root shell) ===
    // Fallback lama memakai File(path) milik UID aplikasi. Untuk /proc dan /sys itu hampir selalu
    // gagal (bukan root / SELinux) dan mengembalikan "tidak ada"/kosong, jadi saat daemon belum
    // tersambung semua fitur MTK terlihat tidak didukung. Fallback sekarang lewat root shell.
    fun checkExists(path: String): Boolean {
        val remote = runCatching { RootIpcManager.ipc?.nodeExists(path) }.getOrNull()
        return remote ?: KernelRootRuntime.nodeExistsBlocking(path)
    }

    fun readData(path: String): String {
        val remote = runCatching { RootIpcManager.ipc?.readNode(path) }.getOrNull()
        if (remote != null) return remote.trim()
        return KernelRootRuntime.readNodeBlocking(path).trim()
    }

    /**
     * Default verify=false: hampir semua knob MTK adalah node bergaya "command" (procfs write-only,
     * GED hal, ppm policy_status "idx 1") yang read-back-nya kosong atau formatnya berbeda, sehingga
     * verifikasi read-back selalu melaporkan gagal padahal write berhasil. Hasil akhir tetap
     * terlihat dari refresh state di ViewModel setelah setiap aksi.
     */
    fun writeData(path: String, value: String, verify: Boolean = false): Boolean {
        return try {
            KernelControlEngine.setKernelNodeBlocking(path, value, verify = verify).success
        } catch (e: Exception) { false }
    }

    fun listDirs(path: String): List<String> {
        val remote = runCatching { RootIpcManager.ipc?.listDirectories(path) }.getOrNull()
        return remote ?: KernelRootRuntime.listDirectoriesBlocking(path)
    }

    fun readLines(path: String): List<String> = readData(path).lines()

    /** Boolean node: "1", "Y", "yes", "true", "on", "enabled" -> true (param bool kernel mencetak Y/N). */
    fun parseBool(raw: String): Boolean {
        val v = raw.trim().lowercase()
        if (v.isEmpty()) return false
        return v == "1" || v == "y" || v == "yes" || v == "true" || v == "on" ||
            (v.contains("enabled") && !v.contains("disabled"))
    }

    fun readBool(path: String): Boolean = parseBool(readData(path))

    // === GED PATHS ===
    private const val GED_MODULE_PARAMS = "/sys/module/ged/parameters"
    const val GED_DFS_ENABLE = "$GED_MODULE_PARAMS/gpu_dvfs_enable"
    const val GED_BOOST_ENABLE = "$GED_MODULE_PARAMS/ged_boost_enable"
    const val GED_GAME_MODE = "$GED_MODULE_PARAMS/gx_game_mode"
    const val GED_EXTRA_BOOST = "$GED_MODULE_PARAMS/boost_extra"
    const val GED_GPU_BOOST = "$GED_MODULE_PARAMS/boost_gpu_enable"
    
    private const val GED_KERNEL_HAL = "/sys/kernel/ged/hal"
    private const val WRITE_MIN_FREQ = "$GED_KERNEL_HAL/custom_boost_gpu_freq"
    private const val WRITE_MAX_FREQ = "$GED_KERNEL_HAL/custom_upbound_gpu_freq"
    const val READ_MIN_FREQ = "$GED_MODULE_PARAMS/custom_boost_gpu_freq"
    const val READ_MAX_FREQ = "$GED_MODULE_PARAMS/custom_upbound_gpu_freq"
    const val CURRENT_FREQ_PATH = "$GED_KERNEL_HAL/current_freqency"

    // === LEGACY & V2 GPUFREQ ===
    private const val V2_DIR = "/proc/gpufreqv2"
    private const val LEGACY_DIR = "/proc/gpufreq"
    private const val V2_FIXED_INDEX = "/proc/gpufreqv2/fix_target_opp_index"
    private const val LEGACY_FIXED_IDX = "/proc/gpufreq/gpufreq_opp_freq"
    private const val V2_OPP_TABLE = "/proc/gpufreqv2/stack_signed_opp_table"
    private const val V2_OPP_TABLE_ALT = "/proc/gpufreqv2/gpu_working_opp_table"
    private const val LEGACY_OPP_DUMP = "/proc/gpufreq/gpufreq_opp_dump"

    // === DEVFREQ PATH (STANDAR GKI BARU) ===
    const val GPU_DEVFREQ_DIR = "/sys/class/devfreq"

    // === DRAM PATHS (FIX G95) ===
    private val DRAM_DEVFREQ_CANDIDATES = listOf(
        "/sys/class/devfreq/mtk-dvfsrc-devfreq", 
        "/sys/class/devfreq/10012000.dvfsrc", 
        "/sys/class/devfreq/dram",
        "/sys/class/devfreq/13000000.dvfsrc"
    )
    private const val DRAM_MMDVFS = "/sys/kernel/mmdvfs/pmqos"
    private const val DRAM_MMDVFS_PROC = "/proc/mmdvfs_pmqos"
    private const val FLIPER_OPP_TABLE = "/proc/fliper/opp_table"
    private const val FLIPER_REQ_OPP = "/proc/fliper/req_opp"

    // === PPM PATHS ===
    const val PPM_ENABLED = "/proc/ppm/enabled"
    const val PPM_POLICY_STATUS = "/proc/ppm/policy_status"

    // === CPU MISC ===
    const val CPU_CCI_MODE = "/proc/cpufreq/cpufreq_cci_mode"
    const val CPU_POWER_MODE = "/proc/cpufreq/cpufreq_power_mode"
    const val EEM_DIR = "/proc/eem"
    const val SCHED_BOOST = "/sys/devices/system/cpu/sched/sched_boost"
    
    // === EARA THERMAL ===
    const val EARA_ENABLE = "/sys/kernel/eara_thermal/enable"
    const val EARA_FAKE_THROTTLE = "/sys/kernel/eara_thermal/fake_throttle"

    // === FPSGO ===
    const val MTK_FPSGO = "/sys/kernel/fpsgo/common/fpsgo_enable"

    enum class DramType { NONE, DEVFREQ, MMDVFS, MTK_FLIPER }
    enum class GpuArch { UNKNOWN, LEGACY_GED, V2_GED, DEVFREQ } // Tambahan DEVFREQ

    // Fungsi untuk mencari path node Devfreq GPU
    fun getGpuDevfreqNode(): String? {
        val dirs = listDirs(GPU_DEVFREQ_DIR)
        
        // Cari folder yang mengandung nama mali, gpu, atau dfrgx
        val targetName = dirs.find { it.contains("mali", true) || it.contains("gpu", true) || it == "dfrgx" }
        return targetName?.let { "$GPU_DEVFREQ_DIR/$it" }
    }

    fun getGpuArch(): GpuArch {
        return when {
            checkExists(V2_DIR) -> GpuArch.V2_GED
            checkExists(LEGACY_DIR) -> GpuArch.LEGACY_GED
            getGpuDevfreqNode() != null -> GpuArch.DEVFREQ // Fallback ke GKI standar
            else -> GpuArch.UNKNOWN
        }
    }

    fun isMtkV2(): Boolean = checkExists(V2_DIR)
    fun isMtkLegacy(): Boolean = checkExists(LEGACY_DIR)
    fun getFixedIndexPath(): String = if (isMtkV2()) V2_FIXED_INDEX else LEGACY_FIXED_IDX

    fun getGpuLoad(): String {
        return try {
            val idlePath = "$GED_MODULE_PARAMS/gpu_idle"
            if (checkExists(idlePath)) {
                val idleStr = readData(idlePath)
                val idle = idleStr.toIntOrNull() ?: 100
                "${100 - idle}%"
            } else {
                val path = listOf(
                    "$GED_KERNEL_HAL/gpu_utilization", 
                    "/proc/mali/utilization"
                ).find { checkExists(it) }
                path?.let { "${readData(it).split(" ").first()}%" } ?: "N/A"
            }
        } catch (e: Exception) { "N/A" }
    }

    fun getCurrentGpuFreq(): String {
        return try {
            // Cek GED Terlebih Dahulu
            if (checkExists(CURRENT_FREQ_PATH)) {
                // Format GED: "<opp index> <freq kHz>" (mis. "0 1100000"). Token pertama adalah INDEX;
                // kode lama mengambilnya sebagai frekuensi sehingga selalu "N/A"/"0 MHz".
                // Frekuensi selalu token numerik terbesar (kHz >> index).
                val freq = readData(CURRENT_FREQ_PATH).split(Regex("\\s+"))
                    .mapNotNull { it.toLongOrNull() }.maxOrNull() ?: 0L
                when {
                    freq >= 10_000_000L -> "${freq / 1_000_000L} MHz"   // Hz
                    freq > 0L -> "${freq / 1000L} MHz"                   // kHz
                    else -> "N/A"
                }
            } else {
                // Fallback ke Devfreq
                val devfreqNode = getGpuDevfreqNode()
                if (devfreqNode != null && checkExists("$devfreqNode/cur_freq")) {
                    val freqRaw = readData("$devfreqNode/cur_freq")
                    val freq = freqRaw.toLongOrNull() ?: 0L
                    when {
                        freq >= 10_000_000L -> "${freq / 1_000_000L} MHz"   // Hz
                        freq >= 10_000L -> "${freq / 1000L} MHz"             // kHz
                        freq > 0L -> "$freq MHz"                             // MHz
                        else -> "N/A"
                    }
                } else {
                    "N/A"
                }
            }
        } catch (e: Exception) { "N/A" }
    }

    private data class OppEntry(val index: String, val freqKhz: Long)

    private fun readOppTable(): List<OppEntry> {
        val path = when {
            checkExists(V2_OPP_TABLE) -> V2_OPP_TABLE
            checkExists(V2_OPP_TABLE_ALT) -> V2_OPP_TABLE_ALT
            checkExists(LEGACY_OPP_DUMP) -> LEGACY_OPP_DUMP
            else -> return emptyList()
        }
        // Baris contoh: "[00] freq = 900000, volt = ..." (legacy) / "[00*] freq: 1180000, volt: ..." (v2)
        val strict = Regex("""\[\s*(\d+)\s*\*?\s*\].*?freq\w*\s*[:=]?\s*(\d+)""", RegexOption.IGNORE_CASE)
        val loose = Regex("""\[\s*(\d+)\s*\*?\s*\].*?(\d+)""")
        return try {
            readLines(path).mapNotNull { line ->
                val m = strict.find(line) ?: loose.find(line) ?: return@mapNotNull null
                val (idx, f) = m.destructured
                f.toLongOrNull()?.let { OppEntry(idx.trim(), it) }
            }
        } catch (e: Exception) {
            android.util.Log.e("ZKM", "Unhandled operation failure", e)
            emptyList()
        }
    }

    /** kHz (atau Hz) tabel OPP -> MHz untuk tampilan. Tabel MTK berisi kHz, UI menulis "MHz". */
    private fun oppToMhz(freq: Long): Long = when {
        freq >= 10_000_000L -> freq / 1_000_000L
        freq >= 10_000L -> freq / 1000L
        else -> freq
    }

    /** Peta "MHz (label UI)" -> "index OPP". Sebelumnya kunci berisi kHz mentah ("900000 MHz"). */
    fun getMtkFreqMap(): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        readOppTable().forEach { result[oppToMhz(it.freqKhz).toString()] = it.index }
        return result
    }

    /**
     * Index OPP yang sedang di-lock, atau "-1" bila tidak di-lock.
     *  - v2: /proc/gpufreqv2/fix_target_opp_index berisi index (atau -1).
     *  - legacy: /proc/gpufreq/gpufreq_opp_freq berisi TEKS ("gpufreq keep opp frequency disabled")
     *    saat tidak di-lock. Kode lama menganggap teks tidak kosong = "terkunci", jadi UI legacy
     *    selalu tampil terkunci.
     */
    fun getLockedIndex(): String {
        return when (getGpuArch()) {
            GpuArch.V2_GED -> {
                val raw = readData(V2_FIXED_INDEX)
                if (raw.isBlank() || raw.contains("disabled", true)) "-1"
                else Regex("-?\\d+").findAll(raw).lastOrNull()?.value?.toIntOrNull()
                    ?.takeIf { it >= 0 }?.toString() ?: "-1"
            }
            GpuArch.LEGACY_GED -> {
                val raw = readData(LEGACY_FIXED_IDX)
                if (raw.isBlank() || raw.contains("disabled", true)) "-1"
                else {
                    val table = readOppTable()
                    Regex("\\d+").findAll(raw).mapNotNull { m ->
                        val n = m.value.toLongOrNull() ?: return@mapNotNull null
                        table.firstOrNull { it.freqKhz == n }?.index
                    }.firstOrNull() ?: "-1"
                }
            }
            else -> "-1"
        }
    }

    fun lockGpuFreq(index: String) {
        when (getGpuArch()) {
            // v2: fix_target_opp_index menerima INDEX OPP (-1 = lepas)
            GpuArch.V2_GED -> writeData(V2_FIXED_INDEX, index.trim())
            // legacy: gpufreq_opp_freq menerima FREKUENSI kHz, bukan index. Kode lama menulis
            // index ke sini sehingga lock legacy tidak pernah mengunci OPP yang dimaksud.
            GpuArch.LEGACY_GED -> {
                val khz = readOppTable().firstOrNull { it.index == index.trim() }?.freqKhz ?: return
                writeData(LEGACY_FIXED_IDX, khz.toString())
            }
            else -> return
        }
    }

    fun resetGpuLock() {
        when (getGpuArch()) {
            GpuArch.V2_GED -> writeData(V2_FIXED_INDEX, "-1")
            GpuArch.LEGACY_GED -> writeData(LEGACY_FIXED_IDX, "0")   // 0 = matikan "keep opp frequency"
            else -> return
        }
    }

    fun setMtkMinFreq(index: String) {
        val finalIdx = if (index == "-1") "-1" else parseMtkIndex(index)
        writeData(WRITE_MIN_FREQ, finalIdx)
        if (finalIdx == "-1") writeData(WRITE_MIN_FREQ, "0")
    }

    fun setMtkMaxFreq(index: String) {
        val finalIdx = if (index == "-1") "-1" else parseMtkIndex(index)
        writeData(WRITE_MAX_FREQ, finalIdx)
        if (finalIdx == "-1") writeData(WRITE_MAX_FREQ, "0")
    }

    fun getCurrentMinIndex(): String {
        val raw = readData(READ_MIN_FREQ).ifBlank { readData(WRITE_MIN_FREQ) }
        return parseMtkIndex(raw)
    }

    fun getCurrentMaxIndex(): String {
        val raw = readData(READ_MAX_FREQ).ifBlank { readData(WRITE_MAX_FREQ) }
        return parseMtkIndex(raw)
    }

    fun parseMtkIndex(raw: String): String {
        val cl = raw.trim()
        if (cl.isEmpty() || cl == "-1" || cl.contains("disabled", true) || cl.contains("dynamic", true)) return "-1"
        return Regex("-?\\d+").findAll(cl).map { it.value }.toList().lastOrNull() ?: "-1"
    }

    // === DRAM METHODS ===
    fun getDramType(): DramType {
        if (checkExists(DRAM_MMDVFS) || checkExists(DRAM_MMDVFS_PROC)) return DramType.MMDVFS
        if (checkExists("/proc/mmdvfs/dump_setting")) return DramType.MMDVFS
        if (DRAM_DEVFREQ_CANDIDATES.any { checkExists(it) }) return DramType.DEVFREQ
        if (checkExists(FLIPER_OPP_TABLE)) return DramType.MTK_FLIPER
        return DramType.NONE
    }

    fun getDramFreqs(type: DramType): Pair<List<String>, Map<String, String>> {
        val list = mutableListOf<String>()
        val map = mutableMapOf<String, String>()
        
        when (type) {
            DramType.MMDVFS -> {
                val paths = listOf(
                    "$DRAM_MMDVFS/dump_setting",
                    "$DRAM_MMDVFS_PROC/dump_setting",
                    "/proc/mmdvfs/dump_setting",
                    "/sys/kernel/mmdvfs/dump_setting"
                )
                
                paths.find { checkExists(it) }?.let { path ->
                    readLines(path).forEach { line ->
                        Regex("""\[(\d+)\].*?(\d+)\s*MHz""").find(line)?.let { m ->
                            val (idx, freq) = m.destructured
                            list.add("$freq MHz")
                            map["$freq MHz"] = idx
                        }
                    }
                }
            }
            DramType.DEVFREQ -> {
                DRAM_DEVFREQ_CANDIDATES.find { checkExists(it) }?.let { path ->
                    readData("$path/available_frequencies").split(" ")
                        .filter { it.isNotBlank() }.forEach { freq ->
                        val mhz = freq.toLongOrNull() ?: return@forEach
                        val label = if (mhz > 1000) "${mhz/1000} MHz" else "$mhz MHz"
                        list.add(label)
                        map[label] = freq
                    }
                }
            }
            DramType.MTK_FLIPER -> {
                readLines(FLIPER_OPP_TABLE).forEach { l ->
                    Regex("""\[(\d+)\]\s*:?\s*(\d+)""").find(l)?.let { m ->
                        val (i, f) = m.destructured
                        val d = "${f.toLong()/1000} MHz"
                        list.add(d)
                        map[d] = i
                    }
                }
            }
            else -> {}
        }
        return Pair(list, map)
    }

    fun getDramCurrentInfo(type: DramType): Map<String, String> {
        val info = mutableMapOf<String, String>()
        when (type) {
            DramType.MMDVFS -> {
                val forcePaths = listOf("$DRAM_MMDVFS/force_step", "$DRAM_MMDVFS_PROC/force_step", "/proc/mmdvfs/force_step")
                val dumpPaths = listOf("$DRAM_MMDVFS/dump_setting", "$DRAM_MMDVFS_PROC/dump_setting", "/proc/mmdvfs/dump_setting")
                
                val forcePath = forcePaths.find { checkExists(it) }
                val dumpPath = dumpPaths.find { checkExists(it) }
                
                info["force_step"] = forcePath?.let { readData(it) } ?: "-1"
                info["current"] = dumpPath?.let { 
                    readData(it).lines().find { l -> l.contains("*") || l.contains(">") }?.trim()
                } ?: "N/A"
            }
            DramType.DEVFREQ -> {
                val p = DRAM_DEVFREQ_CANDIDATES.find { checkExists(it) }
                p?.let {
                    info["cur"] = readData("$it/cur_freq")
                    info["min"] = readData("$it/min_freq")
                    info["max"] = readData("$it/max_freq")
                    info["gov"] = readData("$it/governor")
                }
            }
            DramType.MTK_FLIPER -> {
                info["current"] = readData(FLIPER_REQ_OPP)
            }
            else -> {}
        }
        return info
    }

    fun setDramFreq(type: DramType, displayFreq: String, value: String) {
        when (type) {
            DramType.MMDVFS -> {
                val paths = listOf("$DRAM_MMDVFS/force_step", "$DRAM_MMDVFS_PROC/force_step", "/proc/mmdvfs/force_step")
                paths.find { checkExists(it) }?.let { writeData(it, value) }
            }
            DramType.DEVFREQ -> {
                val p = DRAM_DEVFREQ_CANDIDATES.find { checkExists(it) }
                p?.let {
                    writeData("$it/min_freq", value)
                    writeData("$it/max_freq", value)
                }
            }
            DramType.MTK_FLIPER -> writeData(FLIPER_REQ_OPP, value)
            else -> {}
        }
    }

    fun getDramGovs(): List<String> {
        val p = DRAM_DEVFREQ_CANDIDATES.find { checkExists(it) }
        return p?.let { readData("$it/available_governors").split(Regex("\\s+")).filter { g -> g.isNotBlank() } } ?: emptyList()
    }

    fun setDramGov(gov: String) {
        val p = DRAM_DEVFREQ_CANDIDATES.find { checkExists(it) }
        p?.let { writeData("$it/governor", gov) }
    }

    // === PPM METHODS ===
    data class PpmPolicy(
        val idx: Int, val name: String, val enabled: Boolean,
        val priority: Int = 0, val isActivated: Boolean = false
    )
    
    fun isPpmAvailable(): Boolean = checkExists(PPM_POLICY_STATUS)
    
    fun isPpmEnabled(): Boolean = readBool(PPM_ENABLED)

    fun getPpmPolicies(): List<PpmPolicy> {
        val policies = mutableListOf<PpmPolicy>()
        if (!isPpmAvailable()) return policies
        
        try {
            val statusLines = readData(PPM_POLICY_STATUS).lines()
            val dumpLines = readData("/proc/ppm/dump_policy_list").lines()
            
            val policyInfoMap = mutableMapOf<Int, Triple<String, Int, Boolean>>()
            var currentIdx = -1
            var currentName = ""
            var currentPriority = 0
            
            dumpLines.forEach { line ->
                val headerMatch = Regex("""\[(\d+)\]\s+(PPM_POLICY_\w+).+priority:\s*(\d+)""").find(line)
                if (headerMatch != null) {
                    val (idx, name, priority) = headerMatch.destructured
                    currentIdx = idx.toInt()
                    currentName = name
                    currentPriority = priority.toIntOrNull() ?: 0
                }
                
                if (currentIdx >= 0 && line.contains("is_activated")) {
                    val activated = line.contains("= 1") || line.contains("=1")
                    policyInfoMap[currentIdx] = Triple(currentName, currentPriority, activated)
                }
            }
            
            statusLines.forEach { line ->
                if (line.isBlank() || line.startsWith("Usage:")) return@forEach
                
                val idxMatch = Regex("""^\s*\[(\d+)\]""").find(line)
                val idx = idxMatch?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach
                
                val nameFromLine = Regex("""PPM_POLICY_\w+""").find(line)?.value 
                    ?: policyInfoMap[idx]?.first ?: "Policy_$idx"
                
                val afterColon = line.substringAfter(":", line)
                val enabled = when {
                    afterColon.contains("enabled", ignoreCase = true) -> true
                    afterColon.contains("disabled", ignoreCase = true) -> false
                    afterColon.trim() == "1" -> true
                    afterColon.trim() == "0" -> false
                    else -> !line.lowercase().contains("disabled")
                }
                
                val info = policyInfoMap[idx]
                policies.add(PpmPolicy(
                    idx = idx, name = nameFromLine, enabled = enabled,
                    priority = info?.second ?: 0, isActivated = info?.third ?: false
                ))
            }
        } catch (e: Exception) { android.util.Log.e("ZKM", "Unhandled operation failure", e) }
        
        return policies.sortedBy { it.idx }
    }

    fun togglePpmPolicy(idx: Int, enable: Boolean) {
        writeData(PPM_POLICY_STATUS, "$idx ${if(enable) 1 else 0}")
    }

    fun setPpmEnabled(enable: Boolean) {
        writeData(PPM_ENABLED, if(enable) "1" else "0")
    }

    // === CPU MISC & THERMAL ===
    fun getCpuCciMode(): String = readData(CPU_CCI_MODE).ifEmpty { "0" }
    fun setCpuCciMode(mode: String) { writeData(CPU_CCI_MODE, mode) }
    
    fun getCpuPowerMode(): String = readData(CPU_POWER_MODE).ifEmpty { "0" }
    fun setCpuPowerMode(mode: String) { writeData(CPU_POWER_MODE, mode) }
    
    fun getEemOffsets(): List<Pair<String, String>> {
        val offsets = mutableListOf<Pair<String, String>>()
        // Menggunakan IPC listDirectories agar aman bypass namespace
        val dirs = listDirs(EEM_DIR)
        
        dirs.filter { it.startsWith("EEM_DET_") && !it.contains("GPU") }.forEach { dirName ->
            val path = "$EEM_DIR/$dirName/eem_offset"
            if (checkExists(path)) {
                offsets.add(dirName to readData(path))
            }
        }
        return offsets
    }
    
    fun setEemOffset(detName: String, offset: String) {
        writeData("$EEM_DIR/$detName/eem_offset", offset)
    }

    fun hasEaraThermal(): Boolean = checkExists(EARA_ENABLE)
    fun isEaraEnabled(): Boolean = readBool(EARA_ENABLE)
    fun setEaraEnabled(enable: Boolean) { writeData(EARA_ENABLE, if(enable) "1" else "0") }
    
    fun isEaraFakeThrottle(): Boolean = readBool(EARA_FAKE_THROTTLE)
    fun setEaraFakeThrottle(enable: Boolean) { writeData(EARA_FAKE_THROTTLE, if(enable) "1" else "0") }

    fun isGedDvfsEnabled(): Boolean = readBool(GED_DFS_ENABLE)
    
    fun setGedDvfsEnabled(enable: Boolean) {
        writeData(GED_DFS_ENABLE, if(enable) "1" else "0")
    }
    
    fun setMtkFeature(path: String, enable: Boolean) {
        writeData(path, if(enable) "1" else "0")
    }
}
