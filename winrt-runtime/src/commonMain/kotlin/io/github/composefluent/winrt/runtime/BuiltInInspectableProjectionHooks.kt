package io.github.composefluent.winrt.runtime

import windows.foundation.FoundationBuiltInProjectionRuntimeHooks

internal object WinRTBuiltInProjectionRuntimeHooks {
    fun ensureRegistered() {
        if (!FeatureSwitches.enableDefaultCustomTypeMappings) {
            return
        }
        XamlSystemProjectionRuntimeHooks.ensureRegistered()
        FoundationBuiltInProjectionRuntimeHooks.ensureRegistered()
    }

    fun tryCreateProjectedReference(
        value: Any,
        interfaceId: Guid?,
    ): ComObjectReference? =
        FoundationBuiltInProjectionRuntimeHooks.tryCreateProjectedReference(value, interfaceId)

    fun createSyntheticCcwDefinition(value: Any): WinRTCcwDefinition? =
        platformCreateSyntheticCcwDefinition(value)

    fun runtimeClassNameFor(value: Any): String? =
        platformRuntimeClassNameFor(value)
}
