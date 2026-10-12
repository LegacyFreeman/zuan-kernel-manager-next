/*
 * Original code from: Rem01Gaming (origami_kernel_manager)
 * Modified and integrated by: Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.tuning
import com.zuan.kernelmanager.next.library.device.tuning.SchedulerTweakStore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zuan.kernelmanager.next.core.kernel.KernelControlEngine
import com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SchedulerViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext get() = getApplication<Application>()

    // --- State Classes ---
    data class SchedState(
        val schedAutogroup: String = "0", val hasSchedAutogroup: Boolean = false,
        val childRunsFirst: String = "0", val hasChildRunsFirst: Boolean = false,
        val cstateAware: String = "0", val hasCstateAware: Boolean = false,
        val schedStats: String = "0", val hasSchedStats: Boolean = false,
        val tunableScaling: String = "0", val hasTunableScaling: Boolean = false
    )

    data class BoreState(val hasBore: Boolean = false, val bore: Int = 0)

    data class UclampState(
        val hasUclampMax: Boolean = false, val uclampMax: String = "N/A",
        val hasUclampMin: Boolean = false, val uclampMin: String = "N/A"
    )

    data class TunableItem(
        val name: String,
        val path: String,
        val value: String
    )

    // --- State Flows ---
    private val _sched = MutableStateFlow(SchedState())
    val sched: StateFlow<SchedState> = _sched

    private val _bore = MutableStateFlow(BoreState())
    val bore: StateFlow<BoreState> = _bore

    private val _uclamp = MutableStateFlow(UclampState())
    val uclamp: StateFlow<UclampState> = _uclamp

    private val _genericTunables = MutableStateFlow<List<TunableItem>>(emptyList())
    val genericTunables: StateFlow<List<TunableItem>> = _genericTunables

    // State untuk Apply on Boot
    private val _applyOnBoot = MutableStateFlow(false)
    val applyOnBoot: StateFlow<Boolean> = _applyOnBoot

    init {
        refreshData()
        // Ambil status Apply on Boot terakhir saat layar dibuka
        viewModelScope.launch(Dispatchers.IO) {
            _applyOnBoot.value = SchedulerTweakStore.isApplyOnBoot(appContext)
        }
    }

    fun setApplyOnBoot(enabled: Boolean) {
        _applyOnBoot.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            SchedulerTweakStore.setApplyOnBoot(appContext, enabled)
        }
    }

    fun refreshData() {
        viewModelScope.launch(Dispatchers.IO) {
            _sched.value = SchedState(
                schedAutogroup = KernelRootRuntime.readNodeBlocking(SchedulerUtils.SCHED_AUTO_GROUP).trim(),
                hasSchedAutogroup = KernelRootRuntime.nodeExistsBlocking(SchedulerUtils.SCHED_AUTO_GROUP),
                childRunsFirst = KernelRootRuntime.readNodeBlocking(SchedulerUtils.SCHED_CHILD_RUNS_FIRST).trim(),
                hasChildRunsFirst = KernelRootRuntime.nodeExistsBlocking(SchedulerUtils.SCHED_CHILD_RUNS_FIRST),
                cstateAware = KernelRootRuntime.readNodeBlocking(SchedulerUtils.SCHED_CSTATE_AWARE).trim(),
                hasCstateAware = KernelRootRuntime.nodeExistsBlocking(SchedulerUtils.SCHED_CSTATE_AWARE),
                schedStats = KernelRootRuntime.readNodeBlocking(SchedulerUtils.SCHED_SCHEDSTATS).trim(),
                hasSchedStats = KernelRootRuntime.nodeExistsBlocking(SchedulerUtils.SCHED_SCHEDSTATS),
                tunableScaling = KernelRootRuntime.readNodeBlocking(SchedulerUtils.SCHED_TUNABLE_SCALING).trim(),
                hasTunableScaling = KernelRootRuntime.nodeExistsBlocking(SchedulerUtils.SCHED_TUNABLE_SCALING)
            )

            _bore.value = BoreState(
                hasBore = KernelRootRuntime.nodeExistsBlocking(SchedulerUtils.BORE),
                bore = KernelRootRuntime.readNodeBlocking(SchedulerUtils.BORE).trim().toIntOrNull() ?: 0
            )

            _uclamp.value = UclampState(
                hasUclampMax = KernelRootRuntime.nodeExistsBlocking(SchedulerUtils.SCHED_UTIL_CLAMP_MAX),
                uclampMax = KernelRootRuntime.readNodeBlocking(SchedulerUtils.SCHED_UTIL_CLAMP_MAX).trim(),
                hasUclampMin = KernelRootRuntime.nodeExistsBlocking(SchedulerUtils.SCHED_UTIL_CLAMP_MIN),
                uclampMin = KernelRootRuntime.readNodeBlocking(SchedulerUtils.SCHED_UTIL_CLAMP_MIN).trim()
            )

            val validTunables = mutableListOf<TunableItem>()
            SchedulerUtils.GENERIC_SCHED_TUNABLES.forEach { (name, path) ->
                if (KernelRootRuntime.nodeExistsBlocking(path)) {
                    val content = KernelRootRuntime.readNodeBlocking(path).trim()
                    if (content.isNotEmpty()) {
                        validTunables.add(TunableItem(name, path, content))
                    }
                }
            }
            _genericTunables.value = validTunables
        }
    }

    fun updateValue(path: String, value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = KernelControlEngine.setKernelNode(path, value, verify = true)
            if (result.success) {
                // Simpan hanya setelah kernel mengonfirmasi nilai yang ditulis.
                SchedulerTweakStore.saveValue(appContext, path, value)
            }
            refreshData()
        }
    }
}