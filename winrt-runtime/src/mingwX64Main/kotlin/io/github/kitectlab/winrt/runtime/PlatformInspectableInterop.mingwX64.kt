package io.github.kitectlab.winrt.runtime

internal actual fun platformCreateInspectableReference(value: Any): ComObjectReference =
    throw NotImplementedError("Managed inspectable CCW creation is not implemented for mingwX64 yet.")

internal actual fun platformTryProjectBindableInspectable(pointer: NativePointer): Any? = null

internal actual fun platformEnsureInspectableProjectionInteropRegistered() {}

internal actual fun platformTryProjectInspectable(
    inspectable: IInspectableReference,
    runtimeClassName: String?,
): Any? = null

internal actual fun platformTryCreateProjectedReference(
    value: Any,
    interfaceId: Guid?,
): ComObjectReference? = null

internal actual fun platformCreateSyntheticCcwDefinition(value: Any): WinRtCcwDefinition? = null

internal actual fun platformRuntimeClassNameFor(value: Any): String? =
    TypeNameSupport.getNameForType(
        value::class,
        setOf(TypeNameGenerationFlag.ForGetRuntimeClassName),
    ).takeIf(String::isNotBlank) ?: value::class.qualifiedName
