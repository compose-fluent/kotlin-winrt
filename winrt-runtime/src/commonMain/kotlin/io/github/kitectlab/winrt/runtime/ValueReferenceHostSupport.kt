package io.github.kitectlab.winrt.runtime

internal fun createReferenceHost(
    interfaceId: Guid,
    value: Any,
): ManagedReferenceHost =
    WinRtInspectableComObject(
        interfaceDefinitions = listOf(
            PlatformValueProjectionInterop.createReferenceInterfaceDefinition(value)
                ?: throw WinRtInvalidCastException("Unsupported IReference interface id: $interfaceId", HResult(TYPE_E_TYPEMISMATCH)),
        ),
        runtimeClassName = PlatformValueProjectionInterop.boxedRuntimeClassNameForType(value::class),
        managedValue = value,
    )

internal fun createReferenceArrayHost(
    interfaceId: Guid,
    value: Any,
): ManagedReferenceHost =
    WinRtInspectableComObject(
        interfaceDefinitions = listOf(
            PlatformValueProjectionInterop.createReferenceArrayInterfaceDefinition(value)
                ?: throw WinRtInvalidCastException("Unsupported IReferenceArray interface id: $interfaceId", HResult(TYPE_E_TYPEMISMATCH)),
        ),
        runtimeClassName = PlatformValueProjectionInterop.boxedRuntimeClassNameForType(value::class),
        managedValue = value,
    )
