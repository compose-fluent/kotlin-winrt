package io.github.kitectlab.winrt.runtime

internal fun createSyntheticValueCcwDefinition(value: Any): WinRtCcwDefinition? {
    val interfaceDefinitions =
        buildList {
            if (PlatformValueProjectionInterop.isPropertyValueCompatible(value)) {
                add(createPropertyValueInterfaceDefinition(value))
            }
            PlatformValueProjectionInterop.createReferenceArrayInterfaceDefinition(value)?.let(::add)
                ?: PlatformValueProjectionInterop.createReferenceInterfaceDefinition(value)?.let(::add)
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
        runtimeClassName = PlatformValueProjectionInterop.boxedRuntimeClassNameForType(value::class),
    )
}

internal fun tryProjectInspectableValue(
    inspectable: IInspectableReference,
    runtimeClassName: String? = inspectable.tryGetRuntimeClassName(),
): Any? {
    if (!runtimeClassName.isNullOrBlank()) {
        TypeNameSupport.findRcwKClassByNameCached(runtimeClassName)?.let { projectedType ->
            PlatformValueProjectionInterop.tryProjectInspectableAsType(inspectable, projectedType)?.let { return it }
        }
    }

    WinRtPropertyValueProjection.tryFromBorrowedAbi(inspectable.pointer)?.let { return it }
    PlatformValueProjectionInterop.tryProjectInspectableReference(inspectable)?.let { return it }
    PlatformValueProjectionInterop.tryProjectInspectableReferenceArray(inspectable)?.let { return it }
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
