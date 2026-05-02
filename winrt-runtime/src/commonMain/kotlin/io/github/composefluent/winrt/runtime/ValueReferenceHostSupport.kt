package io.github.composefluent.winrt.runtime

internal fun createReferenceHost(
    interfaceId: Guid,
    value: Any,
): ManagedReferenceHost =
    createBoxedValueHost(
        value = value,
        defaultInterfaceId = interfaceId,
        interfaceDefinitions = buildList {
            if (WinRtValueBoxing.isPropertyValueCompatible(value)) {
                add(createPropertyValueInterfaceDefinition(value))
            }
            add(
                WinRtValueBoxing.createReferenceInterfaceDefinition(value)
                    ?: throw WinRtInvalidCastException("Unsupported IReference interface id: $interfaceId", HResult(TYPE_E_TYPEMISMATCH)),
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
            if (WinRtValueBoxing.isPropertyValueCompatible(value)) {
                add(createPropertyValueInterfaceDefinition(value))
            }
            add(
                WinRtValueBoxing.createReferenceArrayInterfaceDefinition(value)
                    ?: throw WinRtInvalidCastException("Unsupported IReferenceArray interface id: $interfaceId", HResult(TYPE_E_TYPEMISMATCH)),
            )
        },
    )

private fun createBoxedValueHost(
    value: Any,
    defaultInterfaceId: Guid,
    interfaceDefinitions: List<WinRtInspectableInterfaceDefinition>,
): WinRtInspectableComObject {
    val definition = InteropRuntimeHooks.augmentInspectableDefinition(
        value = value,
        definition = WinRtCcwDefinition(
            interfaceDefinitions = interfaceDefinitions,
            defaultInterfaceId = defaultInterfaceId,
            runtimeClassName = WinRtValueBoxing.boxedRuntimeClassNameForType(value::class),
        ),
    )
    return WinRtInspectableComObject(
        interfaceDefinitions = definition.interfaceDefinitions,
        hiddenInterfaceDefinitions = definition.hiddenInterfaceDefinitions,
        defaultInterfaceId = definition.defaultInterfaceId,
        runtimeClassName = definition.runtimeClassName,
        managedValue = value,
    )
}
