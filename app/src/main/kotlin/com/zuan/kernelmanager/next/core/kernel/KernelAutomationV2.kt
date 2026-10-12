package com.zuan.kernelmanager.next.core.kernel

/** Deterministic trigger evaluator. It performs no side effects. */
object KernelAutomationV2 {
    data class Input(
        val event: KernelAutomationEngine.EventType,
        val packageName: String? = null,
        val battery: Int? = null,
        val thermalC: Float? = null,
        val now: Long = System.currentTimeMillis()
    )

    data class Decision(
        val ruleId: String,
        val profileId: String,
        val matched: Boolean,
        val reason: String
    )

    fun evaluate(
        rules: List<KernelAutomationEngine.Rule>,
        input: Input
    ): List<Decision> = rules.map { rule ->
        when {
            rule.event != input.event -> Decision(rule.id, rule.profileId, false, "event_mismatch")
            rule.packageName != null && rule.packageName != input.packageName -> Decision(rule.id, rule.profileId, false, "package_mismatch")
            rule.batteryBelow != null && (input.battery == null || input.battery > rule.batteryBelow) -> Decision(rule.id, rule.profileId, false, "battery_condition_not_met")
            rule.thermalAboveC != null && (input.thermalC == null || input.thermalC < rule.thermalAboveC) -> Decision(rule.id, rule.profileId, false, "thermal_condition_not_met")
            input.now - rule.lastFiredAt < rule.cooldownMs -> Decision(rule.id, rule.profileId, false, "cooldown")
            else -> Decision(rule.id, rule.profileId, true, "matched")
        }
    }
}
