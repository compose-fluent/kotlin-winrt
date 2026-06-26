package io.github.composefluent.winrt.runtime

internal fun createReferenceHost(
    interfaceId: Guid,
    value: Any,
): ManagedReferenceHost =
    createBoxedValueHost(
        value = value,
        defaultInterfaceId = interfaceId,
        interfaceDefinitions = buildList {
            if (WinRTValueBoxing.isPropertyValueCompatible(value)) {
                add(createPropertyValueInterfaceDefinition(value))
            }
            add(
                WinRTValueBoxing.createReferenceInterfaceDefinition(value)
                    ?: throw WinRTInvalidCastException("Unsupported IReference interface id: $interfaceId", HResult(TYPE_E_TYPEMISMATCH)),
            )
        },
    )

internal fun createReferenceArrayHost(
    interfaceId: Guid,
    value: Any,
): ManagedReferenceHost =
    createBoxedValueHost(
        value = value,
        defaultInterfaceId = interfaceId,
        interfaceDefinitions = buildList {
            if (WinRTValueBoxing.isPropertyValueCompatible(value)) {
                add(createPropertyValueInterfaceDefinition(value))
            }
            add(
                WinRTValueBoxing.createReferenceArrayInterfaceDefinition(value)
                    ?: throw WinRTInvalidCastException("Unsupported IReferenceArray interface id: $interfaceId", HResult(TYPE_E_TYPEMISMATCH)),
            )
        },
    )

private fun createBoxedValueHost(
    value: Any,
    defaultInterfaceId: Guid,
    interfaceDefinitions: List<WinRTInspectableInterfaceDefinition>,
): WinRTInspectableComObject {
    val definition = InteropRuntimeHooks.augmentInspectableDefinition(
        value = value,
        definition = WinRTCcwDefinition(
            interfaceDefinitions = interfaceDefinitions,
            defaultInterfaceId = defaultInterfaceId,
            runtimeClassName = WinRTValueBoxing.boxedRuntimeClassNameForType(value::class),
        ),
    )
    return WinRTInspectableComObject(
        interfaceDefinitions = definition.interfaceDefinitions,
        hiddenInterfaceDefinitions = definition.hiddenInterfaceDefinitions,
        defaultInterfaceId = definition.defaultInterfaceId,
        runtimeClassName = definition.runtimeClassName,
        managedValue = value,
    )
}
