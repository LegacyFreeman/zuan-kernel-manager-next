package com.zuan.kernelmanager.next.core.kernel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KernelPolicyTest {
    @Test
    fun kernelPathsAreReadable() {
        assertTrue(KernelNodePolicy.isAllowed("/sys/devices/system/cpu/cpufreq/policy0/scaling_governor"))
        assertTrue(KernelNodePolicy.isAllowed("/proc/sys/vm/swappiness"))
        assertTrue(KernelNodePolicy.isAllowed("/dev/cpuset"))
        assertTrue(KernelNodePolicy.isAllowed("/sys/fs/cgroup/cgroup.controllers"))
    }

    @Test
    fun applicationDataIsNotKernelPath() {
        assertFalse(KernelNodePolicy.isAllowed("/data/local/tmp/zkm"))
        assertFalse(KernelNodePolicy.isAllowed("/system/bin/sh"))
    }

    @Test
    fun destructiveDriverControlsAreWriteBlocked() {
        assertFalse(KernelNodePolicy.isWriteAllowed("/sys/bus/platform/drivers/example/unbind"))
        assertFalse(KernelNodePolicy.isWriteAllowed("/sys/bus/platform/drivers/example/remove"))
        assertFalse(KernelNodePolicy.isWriteAllowed("/sys/kernel/debug/example"))
        assertFalse(KernelNodePolicy.isWriteAllowed("/proc/sys/kernel/sysrq"))
        assertTrue(KernelNodePolicy.isWriteAllowed("/proc/sys/vm/swappiness"))
    }

    @Test
    fun cgroupV2WritesAreLimitedToProcessPlacement() {
        assertTrue(KernelNodePolicy.isWriteAllowed("/sys/fs/cgroup/game/cgroup.procs"))
        assertFalse(KernelNodePolicy.isWriteAllowed("/sys/fs/cgroup/game/cpu.weight"))
    }

    @Test
    fun dotDotSegmentsCannotEscapeIntoApplicationData() {
        assertFalse(KernelNodePolicy.isAllowed("/sys/devices/../../data/local/tmp/x"))
        assertFalse(KernelNodePolicy.isWriteAllowed("/proc/sys/vm/../../../data/local/tmp/x"))
        assertTrue(KernelNodePolicy.isWriteAllowed("/sys/../proc/sys/vm/swappiness"))
    }

    @Test
    fun relativeAndEmptyPathsAreRejected() {
        assertFalse(KernelNodePolicy.isAllowed("proc/sys/vm/swappiness"))
        assertFalse(KernelNodePolicy.isWriteAllowed(""))
        assertFalse(KernelNodePolicy.isWriteAllowed("swappiness"))
    }

    @Test
    fun legacyGpuClockSpellingIsNormalised() {
        assertEquals(
            "/sys/class/kgsl/kgsl-3d0/min_gpuclk",
            KernelNodePolicy.canonical("/sys/class/kgsl/kgsl-3d0/min_gpu_clk")
        )
    }
}
