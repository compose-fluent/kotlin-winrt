package io.github.kitectlab.winrt.runtime

internal actual fun platformCreateInspectableReference(value: Any): ComObjectReference =
    ComWrappersSupport.createCCWForObject(value, IID.IInspectable)

internal actual fun platformTryProjectBindableInspectable(pointer: NativePointer): Any? =
    WinRtValueBoxing.tryProjectBorrowedInspectable(pointer)

internal actual fun platformEnsureInspectableProjectionInteropRegistered() {
    WinRtBuiltInProjectionRuntimeHooks.ensureRegistered()
}

internal actual fun platformTryProjectInspectable(
    inspectable: IInspectableReference,
    runtimeClassName: String?,
): Any? = WinRtValueBoxing.tryProjectInspectable(inspectable, runtimeClassName)

internal actual fun platformTryCreateProjectedReference(
    value: Any,
    interfaceId: Guid?,
): ComObjectReference? = WinRtBuiltInProjectionRuntimeHooks.tryCreateProjectedReference(value, interfaceId)

internal actual fun platformCreateSyntheticCcwDefinition(value: Any): WinRtCcwDefinition? =
    WinRtBuiltInProjectionRuntimeHooks.createSyntheticCcwDefinition(value)

internal actual fun platformRuntimeClassNameFor(value: Any): String? =
    WinRtBuiltInProjectionRuntimeHooks.runtimeClassNameFor(value)
