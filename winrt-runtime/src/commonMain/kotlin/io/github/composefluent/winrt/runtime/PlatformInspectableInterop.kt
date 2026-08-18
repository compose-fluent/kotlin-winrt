package io.github.composefluent.winrt.runtime

internal fun platformCreateInspectableReference(value: Any): ComObjectReference =
    ComWrappersSupport.createCCWForObject(value, IID.IInspectable)

internal fun platformTryProjectBindableInspectable(pointer: RawAddress): Any? =
    tryProjectBorrowedInspectableValue(pointer)

internal fun platformEnsureInspectableProjectionInteropRegistered() {
    ensureProjectionMappingsRegistered()
    WinRTBuiltInProjectionRuntimeHooks.ensureRegistered()
}

internal fun platformTryProjectInspectable(
    inspectable: IInspectableReference,
    runtimeClassName: String?,
): Any? = tryProjectInspectableValue(inspectable, runtimeClassName)

internal fun platformTryCreateProjectedReference(
    value: Any,
    interfaceId: Guid?,
): ComObjectReference? = WinRTBuiltInProjectionRuntimeHooks.tryCreateProjectedReference(value, interfaceId)

internal fun platformCreateSyntheticCcwDefinition(value: Any): WinRTCcwDefinition? =
    createSyntheticInspectableCcwDefinition(value)

internal fun platformRuntimeClassNameFor(value: Any): String? =
    defaultInspectableRuntimeClassNameFor(value)
