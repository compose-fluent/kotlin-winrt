package io.github.kitectlab.winrt.runtime

internal fun createSyntheticValueCcwDefinition(value: Any): WinRtCcwDefinition? {
    val interfaceDefinitions =
        buildList {
            if (WinRtValueBoxing.isPropertyValueCompatible(value)) {
                add(createPropertyValueInterfaceDefinition(value))
            }
            WinRtValueBoxing.createReferenceArrayInterfaceDefinition(value)?.let(::add)
                ?: WinRtValueBoxing.createReferenceInterfaceDefinition(value)?.let(::add)
        }
    if (interfaceDefinitions.isEmpty()) {
        return null
    }
    val defaultInterfaceId =
        if (interfaceDefinitions.any { it.interfaceId == IID.IPropertyValue }) {
            IID.IPropertyValue
        } else {
            interfaceDefinitions.first().interfaceId
        }
    return WinRtCcwDefinition(
        interfaceDefinitions = interfaceDefinitions,
        defaultInterfaceId = defaultInterfaceId,
        runtimeClassName = WinRtValueBoxing.boxedRuntimeClassNameForType(value::class),
    )
}

internal fun createSyntheticInspectableCcwDefinition(value: Any): WinRtCcwDefinition? {
    createSyntheticValueCcwDefinition(value)?.let { return it }
    if (value is AutoCloseable) {
        return WinRtCcwDefinition(
            interfaceDefinitions = listOf(createClosableInspectableInterfaceDefinition(value)),
            defaultInterfaceId = IID.IDisposable,
            runtimeClassName = defaultInspectableRuntimeClassNameFor(value),
        )
    }
    return null
}

internal fun defaultInspectableRuntimeClassNameFor(value: Any): String? {
    WinRtValueBoxing.boxedRuntimeClassNameForType(value::class)?.let { return it }
    val lookupName =
        TypeNameSupport.getNameForType(
            value::class,
            setOf(TypeNameGenerationFlag.ForGetRuntimeClassName),
        )
    return lookupName.takeIf(String::isNotBlank) ?: value::class.qualifiedName ?: value::class.toString()
}

private fun createClosableInspectableInterfaceDefinition(value: AutoCloseable): WinRtInspectableInterfaceDefinition =
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
    )

internal fun tryProjectInspectableValue(
    inspectable: IInspectableReference,
    runtimeClassName: String? = inspectable.tryGetRuntimeClassName(),
): Any? {
    if (!runtimeClassName.isNullOrBlank()) {
        TypeNameSupport.findRcwKClassByNameCached(runtimeClassName)?.let { projectedType ->
            WinRtValueBoxing.tryProjectInspectableAsType(inspectable, projectedType)?.let { return it }
        }
    }

    WinRtPropertyValueProjection.tryFromBorrowedAbi(inspectable.pointer)?.let { return it }
    WinRtValueBoxing.tryProjectInspectableReference(inspectable)?.let { return it }
    WinRtValueBoxing.tryProjectInspectableReferenceArray(inspectable)?.let { return it }
    return null
}

internal fun tryProjectBorrowedInspectableValue(pointer: NativePointer): Any? {
    if (NativeInterop.isNull(pointer)) {
        return null
    }
    val borrowed = IUnknownReference(pointer, IID.IInspectable, preventReleaseOnDispose = true)
    val inspectable =
        try {
            borrowed.asInspectable()
        } catch (_: Throwable) {
            borrowed.close()
            return null
        }
    return try {
        tryProjectInspectableValue(inspectable)
    } finally {
        inspectable.close()
    }
}
