package io.github.composefluent.winrt.runtime

internal actual fun platformCreateInspectableReference(value: Any): ComObjectReference =
    ComWrappersSupport.createCCWForObject(value, IID.IInspectable)

internal actual fun platformTryProjectBindableInspectable(pointer: RawAddress): Any? =
    tryProjectBorrowedInspectableValue(pointer)

internal actual fun platformEnsureInspectableProjectionInteropRegistered() {
    WinRtBuiltInProjectionRuntimeHooks.ensureRegistered()
}

internal actual fun platformTryProjectInspectable(
    inspectable: IInspectableReference,
    runtimeClassName: String?,
): Any? = tryProjectInspectableValue(inspectable, runtimeClassName)

internal actual fun platformTryCreateProjectedReference(
    value: Any,
    interfaceId: Guid?,
): ComObjectReference? = WinRtBuiltInProjectionRuntimeHooks.tryCreateProjectedReference(value, interfaceId)

internal actual fun platformCreateSyntheticCcwDefinition(value: Any): WinRtCcwDefinition? =
    createSyntheticInspectableCcwDefinition(value)

internal actual fun platformRuntimeClassNameFor(value: Any): String? =
    defaultInspectableRuntimeClassNameFor(value)
