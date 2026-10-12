/*
 * Copyright (c) 2026 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.soc

import android.content.Context
import android.net.Uri
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.feature.tuning.CpuGpuUtils
import com.zuan.kernelmanager.next.library.device.tuning.CpuTweakStore
import com.zuan.kernelmanager.next.core.kernel.KernelTuningMigration
import com.zuan.kernelmanager.next.library.device.tuning.MemoryTweakStore
import com.zuan.kernelmanager.next.library.device.tuning.SchedulerTweakStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class CuratedPresetType {
    BATTERY_SAVER,
    BALANCED,
    GAMING_ULTRA,
    THERMAL_CHILLER
}

data class CuratedSocPreset(
    val id: String,
    val type: CuratedPresetType,
    val titleRes: Int,
    val descRes: Int,
    val author: String = "ZKM Core Team",
    val cpuSettings: Map<String, Any>,
    val memorySettings: Map<String, Any>,
    val schedulerSettings: Map<String, Any>
)

/**
 * Metadata "KTP" dari sebuah config profile.
 * Chipset dipakai sebagai validasi hardware saat Import.
 */
data class SocConfigMetadata(
    val profileName: String,
    val author: String,
    val description: String, // [NEW] Keterangan
    val chipset: String,
    val gpu: String,         // [NEW] GPU Compatibility
    val exportedAt: Long,
    val version: Int
)

sealed class SocImportCheck {
    data class Compatible(val metadata: SocConfigMetadata, val rawJson: String) : SocImportCheck()
    data class ChipsetMismatch(
        val metadata: SocConfigMetadata,
        val currentChipset: String,
        val currentGpu: String,
        val rawJson: String
    ) : SocImportCheck()
    data class Invalid(val reason: String) : SocImportCheck()
}

object SocConfigManager {

    const val CONFIG_VERSION = 1
    const val FILE_MIME_TYPE = "application/json"

    private const val KEY_METADATA = "metadata"
    private const val KEY_PAYLOAD = "payload"

    private const val KEY_PROFILE_NAME = "profileName"
    private const val KEY_AUTHOR = "author"
    private const val KEY_DESCRIPTION = "description"
    private const val KEY_CHIPSET = "chipset"
    private const val KEY_GPU = "gpu"
    private const val KEY_EXPORTED_AT = "exportedAt"
    private const val KEY_VERSION = "version"

    private const val SECTION_CPU = "cpu"
    private const val SECTION_MEMORY = "memory"
    private const val SECTION_SCHEDULER = "scheduler"

    suspend fun exportToJson(
        context: Context,
        profileName: String,
        author: String,
        description: String,
        chipset: String,
        gpu: String
    ): String = withContext(Dispatchers.IO) {
        val metadata = JSONObject().apply {
            put(KEY_PROFILE_NAME, profileName.ifBlank { "Untitled Profile" })
            put(KEY_AUTHOR, author.ifBlank { "Unknown Author" })
            put(KEY_DESCRIPTION, description.ifBlank { "No description." })
            put(KEY_CHIPSET, chipset)
            put(KEY_GPU, gpu)
            put(KEY_EXPORTED_AT, System.currentTimeMillis())
            put(KEY_VERSION, CONFIG_VERSION)
        }

        val payload = JSONObject().apply {
            put(SECTION_CPU, mapToJson(CpuTweakStore.exportPreferences(context)))
            put(SECTION_MEMORY, mapToJson(MemoryTweakStore.exportPreferences(context)))
            put(SECTION_SCHEDULER, mapToJson(SchedulerTweakStore.exportPreferences(context)))
        }

        JSONObject().apply {
            put(KEY_METADATA, metadata)
            put(KEY_PAYLOAD, payload)
        }.toString(4)
    }

    suspend fun exportToUri(
        context: Context,
        uri: Uri,
        profileName: String,
        author: String,
        description: String,
        chipset: String,
        gpu: String
    ): Result<SocConfigMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val json = exportToJson(context, profileName, author, description, chipset, gpu)
            context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.write(json.toByteArray(Charsets.UTF_8))
            } ?: error("Failed to open output stream for the destination file.")
            parseMetadata(JSONObject(json))
        }
    }

    suspend fun validateFromUri(context: Context, uri: Uri): SocImportCheck =
        withContext(Dispatchers.IO) {
            val rawJson = runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.readBytes().toString(Charsets.UTF_8)
                } ?: error("Failed to read the file.")
            }.getOrElse { return@withContext SocImportCheck.Invalid("Failed to read the file: ${it.message}") }

            validateJson(rawJson)
        }

    suspend fun validateJson(rawJson: String): SocImportCheck {
        val root = runCatching { JSONObject(rawJson) }.getOrElse {
            return SocImportCheck.Invalid("The file is not valid JSON.")
        }

        if (!root.has(KEY_METADATA) || !root.has(KEY_PAYLOAD)) {
            return SocImportCheck.Invalid("Unrecognized config structure (missing metadata/payload).")
        }

        val metadata = runCatching { parseMetadata(root) }.getOrElse {
            return SocImportCheck.Invalid("Metadata config rusak: ${it.message}")
        }

        val currentChipset = CpuGpuUtils.getCpuInfo()
        val currentGpuType = CpuGpuUtils.getGpuType()
        val currentGpu = when (currentGpuType) {
            CpuGpuUtils.GpuType.ADRENO -> "Adreno"
            CpuGpuUtils.GpuType.MEDIATEK_V2, CpuGpuUtils.GpuType.MEDIATEK_LEGACY -> "Mali / MediaTek"
            CpuGpuUtils.GpuType.GENERIC_DEVFREQ -> "Generic GPU"
            else -> "Unknown GPU"
        }

        val isSameChipset = metadata.chipset.trim().equals(currentChipset.trim(), ignoreCase = true)

        return if (isSameChipset) {
            SocImportCheck.Compatible(metadata, rawJson)
        } else {
            SocImportCheck.ChipsetMismatch(metadata, currentChipset, currentGpu, rawJson)
        }
    }

    suspend fun restoreFromJson(context: Context, rawJson: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject(rawJson).getJSONObject(KEY_PAYLOAD)

                CpuTweakStore.restorePreferences(context, jsonToMap(payload.optJSONObject(SECTION_CPU)))
                MemoryTweakStore.restorePreferences(context, jsonToMap(payload.optJSONObject(SECTION_MEMORY)))
                SchedulerTweakStore.restorePreferences(context, jsonToMap(payload.optJSONObject(SECTION_SCHEDULER)))

                // Terapkan melalui Kernel Engine transaction/policy layer.
                val report = KernelTuningMigration.applyPersisted(context, force = true)
                check(report.passed) { report.errors.joinToString("; ").ifBlank { "Failed to restore persisted tuning" } }
            }
        }

    suspend fun getCuratedPresets(): List<CuratedSocPreset> = withContext(Dispatchers.IO) {
        val policies = CpuGpuUtils.getCpuPolicies().ifEmpty {
            listOf(
                "/sys/devices/system/cpu/cpufreq/policy0",
                "/sys/devices/system/cpu/cpufreq/policy4",
                "/sys/devices/system/cpu/cpufreq/policy7"
            )
        }

        listOf(
            CuratedSocPreset(
                id = "preset_battery_saver",
                type = CuratedPresetType.BATTERY_SAVER,
                titleRes = R.string.soc_preset_battery_title,
                descRes = R.string.soc_preset_battery_desc,
                author = "ZKM Core Engineering",
                cpuSettings = buildMap {
                    put(CpuTweakStore.APPLY_ON_BOOT.name, true)
                    policies.forEach { path -> put("gov::$path", "powersave") }
                },
                memorySettings = buildMap {
                    put(MemoryTweakStore.APPLY_ON_BOOT_VM_IO.name, true)
                    put("vm::/proc/sys/vm/swappiness", "80")
                    put("vm::/proc/sys/vm/dirty_ratio", "15")
                    put("vm::/proc/sys/vm/dirty_background_ratio", "5")
                    put(MemoryTweakStore.APPLY_ON_BOOT_ZRAM.name, true)
                    put(MemoryTweakStore.ZRAM_SIZE.name, "3072")
                    put(MemoryTweakStore.ZRAM_ALGO.name, "zstd")
                },
                schedulerSettings = buildMap {
                    put(SchedulerTweakStore.APPLY_ON_BOOT.name, true)
                    put("sched::/proc/sys/kernel/sched_energy_aware", "1")
                    put("sched::/proc/sys/kernel/sched_boost", "0")
                    put("sched::/proc/sys/kernel/sched_child_runs_first", "0")
                }
            ),
            CuratedSocPreset(
                id = "preset_balanced",
                type = CuratedPresetType.BALANCED,
                titleRes = R.string.soc_preset_balanced_title,
                descRes = R.string.soc_preset_balanced_desc,
                author = "ZKM Core Engineering",
                cpuSettings = buildMap {
                    put(CpuTweakStore.APPLY_ON_BOOT.name, true)
                    policies.forEach { path -> put("gov::$path", "schedutil") }
                },
                memorySettings = buildMap {
                    put(MemoryTweakStore.APPLY_ON_BOOT_VM_IO.name, true)
                    put("vm::/proc/sys/vm/swappiness", "60")
                    put("vm::/proc/sys/vm/dirty_ratio", "20")
                    put("vm::/proc/sys/vm/dirty_background_ratio", "10")
                    put(MemoryTweakStore.APPLY_ON_BOOT_ZRAM.name, true)
                    put(MemoryTweakStore.ZRAM_SIZE.name, "4096")
                    put(MemoryTweakStore.ZRAM_ALGO.name, "lz4")
                },
                schedulerSettings = buildMap {
                    put(SchedulerTweakStore.APPLY_ON_BOOT.name, true)
                    put("sched::/proc/sys/kernel/sched_energy_aware", "1")
                    put("sched::/proc/sys/kernel/sched_boost", "0")
                }
            ),
            CuratedSocPreset(
                id = "preset_gaming_ultra",
                type = CuratedPresetType.GAMING_ULTRA,
                titleRes = R.string.soc_preset_gaming_title,
                descRes = R.string.soc_preset_gaming_desc,
                author = "ZKM Core Engineering",
                cpuSettings = buildMap {
                    put(CpuTweakStore.APPLY_ON_BOOT.name, true)
                    policies.forEach { path -> put("gov::$path", "performance") }
                },
                memorySettings = buildMap {
                    put(MemoryTweakStore.APPLY_ON_BOOT_VM_IO.name, true)
                    put("vm::/proc/sys/vm/swappiness", "40")
                    put("vm::/proc/sys/vm/dirty_ratio", "30")
                    put("vm::/proc/sys/vm/dirty_background_ratio", "15")
                    put(MemoryTweakStore.APPLY_ON_BOOT_ZRAM.name, true)
                    put(MemoryTweakStore.ZRAM_SIZE.name, "4096")
                    put(MemoryTweakStore.ZRAM_ALGO.name, "lz4")
                },
                schedulerSettings = buildMap {
                    put(SchedulerTweakStore.APPLY_ON_BOOT.name, true)
                    put("sched::/proc/sys/kernel/sched_boost", "1")
                    put("sched::/proc/sys/kernel/sched_child_runs_first", "1")
                }
            ),
            CuratedSocPreset(
                id = "preset_thermal_chiller",
                type = CuratedPresetType.THERMAL_CHILLER,
                titleRes = R.string.soc_preset_thermal_title,
                descRes = R.string.soc_preset_thermal_desc,
                author = "ZKM Core Engineering",
                cpuSettings = buildMap {
                    put(CpuTweakStore.APPLY_ON_BOOT.name, true)
                    policies.forEach { path -> put("gov::$path", "schedutil") }
                },
                memorySettings = buildMap {
                    put(MemoryTweakStore.APPLY_ON_BOOT_VM_IO.name, true)
                    put("vm::/proc/sys/vm/swappiness", "70")
                    put("vm::/proc/sys/vm/dirty_ratio", "10")
                    put("vm::/proc/sys/vm/dirty_background_ratio", "5")
                    put(MemoryTweakStore.APPLY_ON_BOOT_ZRAM.name, true)
                    put(MemoryTweakStore.ZRAM_SIZE.name, "2048")
                    put(MemoryTweakStore.ZRAM_ALGO.name, "zstd")
                },
                schedulerSettings = buildMap {
                    put(SchedulerTweakStore.APPLY_ON_BOOT.name, true)
                    put("sched::/proc/sys/kernel/sched_energy_aware", "1")
                    put("sched::/proc/sys/kernel/sched_boost", "0")
                }
            )
        )
    }

    suspend fun applyPreset(context: Context, preset: CuratedSocPreset): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                CpuTweakStore.restorePreferences(context, preset.cpuSettings)
                MemoryTweakStore.restorePreferences(context, preset.memorySettings)
                SchedulerTweakStore.restorePreferences(context, preset.schedulerSettings)

                val report = KernelTuningMigration.applyPersisted(context, force = true)
                check(report.passed) { report.errors.joinToString("; ").ifBlank { "Failed to apply preset" } }
            }
        }

    private fun parseMetadata(root: JSONObject): SocConfigMetadata {
        val meta = root.getJSONObject(KEY_METADATA)
        return SocConfigMetadata(
            profileName = meta.optString(KEY_PROFILE_NAME, "Untitled Profile"),
            author = meta.optString(KEY_AUTHOR, "Unknown Author"),
            description = meta.optString(KEY_DESCRIPTION, "No description."),
            chipset = meta.optString(KEY_CHIPSET, "Unknown"),
            gpu = meta.optString(KEY_GPU, "Unknown GPU"),
            exportedAt = meta.optLong(KEY_EXPORTED_AT, 0L),
            version = meta.optInt(KEY_VERSION, CONFIG_VERSION)
        )
    }

    private fun mapToJson(map: Map<String, Any>): JSONObject {
        val json = JSONObject()
        map.forEach { (key, value) ->
            when (value) {
                is Boolean -> json.put(key, value)
                is Set<*> -> json.put(key, JSONArray(value.map { it.toString() }))
                else -> json.put(key, value.toString())
            }
        }
        return json
    }

    private fun jsonToMap(json: JSONObject?): Map<String, Any> {
        if (json == null) return emptyMap()
        val map = mutableMapOf<String, Any>()
        json.keys().forEach { key ->
            when (val value = json.get(key)) {
                is Boolean -> map[key] = value
                is JSONArray -> {
                    val set = mutableSetOf<String>()
                    for (i in 0 until value.length()) set.add(value.getString(i))
                    map[key] = set
                }
                else -> map[key] = value.toString()
            }
        }
        return map
    }
}
