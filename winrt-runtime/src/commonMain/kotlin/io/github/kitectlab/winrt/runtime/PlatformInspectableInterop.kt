package io.github.kitectlab.winrt.runtime

internal expect fun platformCreateInspectableReference(value: Any): ComObjectReference

internal expect fun platformTryProjectBindableInspectable(pointer: NativePointer): Any?

internal expect fun platformEnsureInspectableProjectionInteropRegistered()

internal expect fun platformTryProjectInspectable(
    inspectable: IInspectableReference,
    runtimeClassName: String?,
): Any?

internal expect fun platformTryCreateProjectedReference(
    value: Any,
    interfaceId: Guid?,
): ComObjectReference?

internal expect fun platformCreateSyntheticCcwDefinition(value: Any): WinRtCcwDefinition?

internal expect fun platformRuntimeClassNameFor(value: Any): String?
