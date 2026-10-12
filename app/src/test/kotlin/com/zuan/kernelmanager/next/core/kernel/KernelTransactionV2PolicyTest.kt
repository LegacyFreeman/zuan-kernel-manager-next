package com.zuan.kernelmanager.next.core.kernel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KernelTransactionV2PolicyTest {
    @Test
    fun policyRejectsDangerousTransactionPaths() {
        assertFalse(KernelNodePolicy.isWriteAllowed("/sys/bus/platform/drivers/example/unbind"))
        assertFalse(KernelNodePolicy.isWriteAllowed("/data/local/tmp/zkm"))
        assertTrue(KernelNodePolicy.isWriteAllowed("/proc/sys/vm/swappiness"))
    }
}
