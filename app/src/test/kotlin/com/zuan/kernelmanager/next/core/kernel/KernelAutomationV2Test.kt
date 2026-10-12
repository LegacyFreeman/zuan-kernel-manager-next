package com.zuan.kernelmanager.next.core.kernel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KernelAutomationV2Test {
    @Test
    fun evaluatesEventPackageAndThresholdsDeterministically() {
        val rules = listOf(
            KernelAutomationEngine.Rule("r1", KernelAutomationEngine.EventType.APP_FOREGROUND, "com.game", profileId = "p1"),
            KernelAutomationEngine.Rule("r2", KernelAutomationEngine.EventType.BATTERY, batteryBelow = 30, profileId = "p2"),
            KernelAutomationEngine.Rule("r3", KernelAutomationEngine.EventType.THERMAL, thermalAboveC = 60f, profileId = "p3", lastFiredAt = 9500L, cooldownMs = 1000L)
        )
        val app = KernelAutomationV2.evaluate(rules, KernelAutomationV2.Input(KernelAutomationEngine.EventType.APP_FOREGROUND, packageName = "com.game", now = 5000L))
        assertTrue(app.first { it.ruleId == "r1" }.matched)
        assertEquals("package_mismatch", app.first { it.ruleId == "r2" }.reason)

        val battery = KernelAutomationV2.evaluate(rules, KernelAutomationV2.Input(KernelAutomationEngine.EventType.BATTERY, battery = 25, now = 5000L))
        assertTrue(battery.first { it.ruleId == "r2" }.matched)

        val thermal = KernelAutomationV2.evaluate(rules, KernelAutomationV2.Input(KernelAutomationEngine.EventType.THERMAL, thermalC = 70f, now = 10200L))
        assertFalse(thermal.first { it.ruleId == "r3" }.matched)
        assertEquals("cooldown", thermal.first { it.ruleId == "r3" }.reason)
    }
}
