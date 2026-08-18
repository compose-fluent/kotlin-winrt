package io.github.composefluent.winrt.runtime

import windows.foundation.FoundationBuiltInProjectionRuntimeHooks

internal object WinRTBuiltInProjectionRuntimeHooks {
    private val registrationLock = PlatformLock()

    @kotlin.concurrent.Volatile
    private var registered = false

    fun ensureRegistered() {
        if (registered || !FeatureSwitches.enableDefaultCustomTypeMappings) {
            return
        }
        registrationLock.withLock {
            if (registered) {
                return@withLock
            }
            XamlSystemProjectionRuntimeHooks.ensureRegistered()
            FoundationBuiltInProjectionRuntimeHooks.ensureRegistered()
            registered = true
        }
    }

    fun clearForTests() {
        registrationLock.withLock {
            registered = false
        }
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
