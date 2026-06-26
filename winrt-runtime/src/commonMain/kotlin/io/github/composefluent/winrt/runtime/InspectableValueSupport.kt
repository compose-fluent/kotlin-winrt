package io.github.composefluent.winrt.runtime

internal fun createSyntheticValueCcwDefinition(value: Any): WinRTCcwDefinition? {
    val interfaceDefinitions =
        buildList {
            if (WinRTValueBoxing.isPropertyValueCompatible(value)) {
                add(createPropertyValueInterfaceDefinition(value))
            }
            WinRTValueBoxing.createReferenceArrayInterfaceDefinition(value)?.let(::add)
                ?: WinRTValueBoxing.createReferenceInterfaceDefinition(value)?.let(::add)
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
    return WinRTCcwDefinition(
        interfaceDefinitions = interfaceDefinitions,
        defaultInterfaceId = defaultInterfaceId,
        runtimeClassName = WinRTValueBoxing.boxedRuntimeClassNameForType(value::class),
    )
}

internal fun createSyntheticInspectableCcwDefinition(value: Any): WinRTCcwDefinition? {
    createSyntheticValueCcwDefinition(value)?.let { return it }
    if (value is AutoCloseable) {
        return WinRTCcwDefinition(
            interfaceDefinitions = listOf(createClosableInspectableInterfaceDefinition(value)),
            defaultInterfaceId = IID.IDisposable,
            runtimeClassName = defaultInspectableRuntimeClassNameFor(value),
        )
    }
    return null
}

internal fun defaultInspectableRuntimeClassNameFor(value: Any): String? {
    WinRTValueBoxing.boxedRuntimeClassNameForType(value::class)?.let { return it }
    if (value is AutoCloseable) {
        return TypeNameSupport.getNameForType(AutoCloseable::class).takeIf(String::isNotBlank)
    }
    val lookupName =
        TypeNameSupport.getNameForType(
            value::class,
            setOf(TypeNameGenerationFlag.ForGetRuntimeClassName),
        )
    return lookupName.takeIf(String::isNotBlank)
}

private fun createClosableInspectableInterfaceDefinition(value: AutoCloseable): WinRTInspectableInterfaceDefinition =
    WinRTInspectableInterfaceDefinition(
        interfaceId = IID.IDisposable,
        methods = listOf(
            WinRTInspectableMethodDefinition(
                signature = ComMethodSignature.of(),
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
            WinRTValueBoxing.tryProjectInspectableAsType(inspectable, projectedType)?.let { return it }
        }
    }

    WinRTPropertyValueProjection.tryFromBorrowedAbi(inspectable.pointer.asRawAddress())?.let { return it }
    WinRTValueBoxing.tryProjectInspectableReference(inspectable)?.let { return it }
    WinRTValueBoxing.tryProjectInspectableReferenceArray(inspectable)?.let { return it }
    return null
}

internal fun tryProjectBorrowedInspectableValue(pointer: RawAddress): Any? {
    if (PlatformAbi.isNull(pointer)) {
        return null
    }
    val borrowed = IUnknownReference(pointer.asRawComPtr(), IID.IInspectable, preventReleaseOnDispose = true)
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
        try {
            inspectable.close()
        } finally {
            borrowed.close()
        }
    }
}
