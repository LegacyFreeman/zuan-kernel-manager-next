/*
 * Original code from: Rem01Gaming (origami_kernel_manager)
 * Modified and integrated by: Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.tuning
import com.zuan.kernelmanager.next.library.device.tuning.MemoryTweakStore
import com.zuan.kernelmanager.next.library.device.tuning.MemoryUtils

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zuan.kernelmanager.next.core.kernel.KernelControlEngine
import com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MemoryViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext get() = getApplication<Application>()

    data class MemoryState(
        val swappiness: String = "0", val hasSwappiness: Boolean = false,
        val vfsCachePressure: String = "0", val hasVfsCachePressure: Boolean = false,
        val dirtyRatio: String = "0", val hasDirtyRatio: Boolean = false,
        val dirtyBackgroundRatio: String = "0", val hasDirtyBackgroundRatio: Boolean = false,
        val minFreeKbytes: String = "0", val hasMinFreeKbytes: Boolean = false,
        val extraFreeKbytes: String = "0", val hasExtraFreeKbytes: Boolean = false
    )

    data class ZramState(
        val sizeMb: Int = 0,
        val activeAlgo: String = "Unknown",
        val availableAlgos: List<String> = emptyList()
    )

    data class IODeviceState(
        val name: String, val activeScheduler: String, val availableSchedulers: List<String>,
        val nrRequests: String = "", val readAheadKb: String = "", val rqAffinity: String = "",
        val rotational: String = "", val addRandom: String = "", val iostats: String = ""
    )

    private val _mem = MutableStateFlow(MemoryState())
    val mem: StateFlow<MemoryState> = _mem

    private val _zram = MutableStateFlow(ZramState())
    val zram: StateFlow<ZramState> = _zram

    private val _ioDevices = MutableStateFlow<List<IODeviceState>>(emptyList())
    val ioDevices: StateFlow<List<IODeviceState>> = _ioDevices

    // --- APPLY ON BOOT STATES ---
    private val _applyOnBootVmIo = MutableStateFlow(false)
    val applyOnBootVmIo: StateFlow<Boolean> = _applyOnBootVmIo

    private val _applyOnBootZram = MutableStateFlow(false)
    val applyOnBootZram: StateFlow<Boolean> = _applyOnBootZram

    init { 
        refreshAll()
        viewModelScope.launch(Dispatchers.IO) {
            _applyOnBootVmIo.value = MemoryTweakStore.isApplyOnBootVmIo(appContext)
            _applyOnBootZram.value = MemoryTweakStore.isApplyOnBootZram(appContext)
        }
    }

    fun setApplyOnBootVmIo(enabled: Boolean) {
        _applyOnBootVmIo.value = enabled
        viewModelScope.launch(Dispatchers.IO) { MemoryTweakStore.setApplyOnBootVmIo(appContext, enabled) }
    }

    fun setApplyOnBootZram(enabled: Boolean) {
        _applyOnBootZram.value = enabled
        viewModelScope.launch(Dispatchers.IO) { MemoryTweakStore.setApplyOnBootZram(appContext, enabled) }
    }

    fun refreshAll() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshVmStats()
            refreshZramStats()
            refreshIOStats()
        }
    }

    private fun refreshVmStats() {
        _mem.value = MemoryState(
            swappiness = KernelRootRuntime.readNodeBlocking(MemoryUtils.SWAPPINESS).trim(),
            hasSwappiness = KernelRootRuntime.nodeExistsBlocking(MemoryUtils.SWAPPINESS),
            vfsCachePressure = KernelRootRuntime.readNodeBlocking(MemoryUtils.VFS_CACHE_PRESSURE).trim(),
            hasVfsCachePressure = KernelRootRuntime.nodeExistsBlocking(MemoryUtils.VFS_CACHE_PRESSURE),
            dirtyRatio = KernelRootRuntime.readNodeBlocking(MemoryUtils.DIRTY_RATIO).trim(),
            hasDirtyRatio = KernelRootRuntime.nodeExistsBlocking(MemoryUtils.DIRTY_RATIO),
            dirtyBackgroundRatio = KernelRootRuntime.readNodeBlocking(MemoryUtils.DIRTY_BACKGROUND_RATIO).trim(),
            hasDirtyBackgroundRatio = KernelRootRuntime.nodeExistsBlocking(MemoryUtils.DIRTY_BACKGROUND_RATIO),
            minFreeKbytes = KernelRootRuntime.readNodeBlocking(MemoryUtils.MIN_FREE_KBYTES).trim(),
            hasMinFreeKbytes = KernelRootRuntime.nodeExistsBlocking(MemoryUtils.MIN_FREE_KBYTES),
            extraFreeKbytes = KernelRootRuntime.readNodeBlocking(MemoryUtils.EXTRA_FREE_KBYTES).trim(),
            hasExtraFreeKbytes = KernelRootRuntime.nodeExistsBlocking(MemoryUtils.EXTRA_FREE_KBYTES)
        )
    }

    private fun refreshZramStats() {
        val sizeBytes = MemoryUtils.getZramSizeBytes()
        val sizeMb = (sizeBytes / 1048576L).toInt()
        val (active, algos) = MemoryUtils.getZramAlgoInfo()
        _zram.value = ZramState(sizeMb = sizeMb, activeAlgo = active, availableAlgos = algos)
    }

    private fun refreshIOStats() {
        val devices = MemoryUtils.getBlockDevices()
        val newList = devices.map { devName ->
            val (sched, scheds) = MemoryUtils.getIOSchedulerInfo(devName)
            IODeviceState(
                name = devName, activeScheduler = sched, availableSchedulers = scheds,
                nrRequests = MemoryUtils.getIOTunable(devName, "nr_requests"),
                readAheadKb = MemoryUtils.getIOTunable(devName, "read_ahead_kb"),
                rqAffinity = MemoryUtils.getIOTunable(devName, "rq_affinity"),
                rotational = MemoryUtils.getIOTunable(devName, "rotational"),
                addRandom = MemoryUtils.getIOTunable(devName, "add_random"),
                iostats = MemoryUtils.getIOTunable(devName, "iostats")
            )
        }
        _ioDevices.value = newList
    }

    // --- SAVE ACTIONS ---

    fun updateVmValue(path: String, value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = KernelControlEngine.setKernelNode(path, value, verify = true)
            if (result.success) MemoryTweakStore.saveVmSetting(appContext, path, value)
            refreshVmStats()
        }
    }
    
    fun applyZramChanges(targetSizeMb: Int, targetAlgo: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = KernelControlEngine.configureZram(targetSizeMb, targetAlgo)
            if (result.success) {
                MemoryTweakStore.saveZramSettings(appContext, targetSizeMb, targetAlgo)
            }
            refreshZramStats()
        }
    }

    fun setIOScheduler(devName: String, scheduler: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = KernelControlEngine.setIoScheduler(devName, scheduler).success
            if (ok) {
                MemoryTweakStore.saveIoSched(appContext, devName, scheduler)
            }
            refreshIOStats()
        }
    }

    fun setIOTunable(devName: String, key: String, value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val path = "/sys/block/$devName/queue/$key"
            val ok = KernelControlEngine.setKernelNode(path, value, verify = true).success
            if (ok) {
                MemoryTweakStore.saveIoTunable(appContext, devName, key, value)
            }
            refreshIOStats()
        }
    }
}
