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

internal actual fun platformCreateSyntheticCcwDefinition(value: Any): WinRtCcwDefinition? {
    WinRtValueBoxing.createInspectableBoxDefinition(value)?.let { return it }
    if (value is AutoCloseable) {
        return WinRtCcwDefinition(
            interfaceDefinitions = listOf(
                WinRtInspectableInterfaceDefinition(
                    interfaceId = IID.IDisposable,
                    methods = listOf(
                        WinRtInspectableMethodDefinition(
                            descriptor = NativeFunctionDescriptor.of(
                                NativeValueLayout.JAVA_INT,
                                NativeValueLayout.ADDRESS,
                            ),
                        ) { _ ->
                            value.close()
                            KnownHResults.S_OK.value
                        },
                    ),
                ),
            ),
            defaultInterfaceId = IID.IDisposable,
            runtimeClassName = platformRuntimeClassNameFor(value),
        )
    }
    return null
}

internal actual fun platformRuntimeClassNameFor(value: Any): String? {
    WinRtValueBoxing.boxedRuntimeClassNameForType(value::class)?.let { return it }
    val lookupName =
        TypeNameSupport.getNameForType(
            value::class,
            setOf(TypeNameGenerationFlag.ForGetRuntimeClassName),
        )
    return lookupName.takeIf(String::isNotBlank) ?: value::class.qualifiedName ?: value::class.toString()
}
