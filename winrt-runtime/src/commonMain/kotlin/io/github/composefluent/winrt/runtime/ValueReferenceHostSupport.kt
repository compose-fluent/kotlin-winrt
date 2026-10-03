package io.github.composefluent.winrt.runtime

internal enum class ValueHostShapeKind {
    REFERENCE,
    REFERENCE_ARRAY,
    PROPERTY_VALUE,
}

internal data class ValueHostShapeKey(
    val kind: ValueHostShapeKind,
    val defaultInterfaceId: Guid,
    val valueInterfaceId: Guid? = null,
    val propertyType: PropertyType? = null,
    val runtimeClassName: String? = null,
    val includePropertyValueInterface: Boolean = true,
)

private val valueHostShapeCache = ConcurrentCacheMap<ValueHostShapeKey, WinRTCcwDefinition>()

private class CachedReferenceHostDefinition(
    val metadata: WinRTValueTypeMetadata,
    val registeredType: WinRTTypeId<*>?,
    val definition: WinRTCcwDefinition,
)

private val referenceHostDefinitions = ConcurrentCacheMap<Guid, CachedReferenceHostDefinition>()

internal fun cachedValueHostDefinition(
    key: ValueHostShapeKey,
    interfaceDefinitions: () -> List<WinRTInspectableInterfaceDefinition>,
): WinRTCcwDefinition =
    valueHostShapeCache.computeIfAbsent(key) {
        WinRTCcwDefinition(
            interfaceDefinitions = interfaceDefinitions(),
            defaultInterfaceId = key.defaultInterfaceId,
            runtimeClassName = key.runtimeClassName,
        )
    }

internal fun createValueHost(
    value: Any,
    baseDefinition: WinRTCcwDefinition,
    augmentRuntimeInterfaces: Boolean = true,
): WinRTInspectableComObject {
    // Boxed value CCWs mirror CsWinRT's BoxedValueIReferenceImpl<T> for their closed
    // value interfaces, while retaining the standard CCW suffix exposed by
    // ComWrappersSupport (IStringable, IMarshal, tracker, and friends). The definition
    // is shape-cached, so those interfaces and their vtables are built once per closed
    // metadata shape rather than once per boxed value instance.
    val definition = if (augmentRuntimeInterfaces) {
        InteropRuntimeHooks.augmentInspectableDefinition(baseDefinition)
    } else {
        baseDefinition
    }
    return WinRTInspectableComObject(
        interfaceDefinitions = definition.interfaceDefinitions,
        hiddenInterfaceDefinitions = definition.hiddenInterfaceDefinitions,
        defaultInterfaceId = definition.defaultInterfaceId,
        runtimeClassName = definition.runtimeClassName,
        managedValue = value,
        shapeCacheKey = definition,
    )
}

internal fun createReferenceHost(
    interfaceId: Guid,
    value: Any,
): ManagedReferenceHost {
    return createReferenceHost(
        interfaceId = interfaceId,
        value = value,
        includePropertyValueInterface = true,
        augmentRuntimeInterfaces = true,
    )
}

/**
 * Creates the short-lived host used by an outbound IReference marshaler.
 *
 * The host is short lived, but it still exposes the complete CsWinRT-compatible boxed-value
 * interface table because WinUI dependency-property code queries IPropertyValue while validating
 * the value passed to an IReference<T> setter.
 */
internal fun createReferenceMarshalerHost(
    interfaceId: Guid,
    value: Any,
    includePropertyValueInterface: Boolean = false,
): WinRTInspectableComObject {
    // Ordinary IReference<T> calls only need the closed reference interface.  A
    // dependency-property setter opts into the complete boxed-value shape because
    // WinUI validates that argument through IPropertyValue before consuming it.
    return createReferenceHost(
        interfaceId = interfaceId,
        value = value,
        includePropertyValueInterface = includePropertyValueInterface,
        augmentRuntimeInterfaces = includePropertyValueInterface,
    )
}

private fun createReferenceHost(
    interfaceId: Guid,
    value: Any,
    includePropertyValueInterface: Boolean,
    augmentRuntimeInterfaces: Boolean,
): WinRTInspectableComObject {
    // Cache only the complete closed-value base shape. The common CCW augmentation owner still
    // selects standard interfaces for the current configuration, and each value owns a new host.
    val metadata = if (includePropertyValueInterface) {
        ValueBoxingMetadata.invariantReferenceHostMetadata(interfaceId, value)
    } else {
        null
    }
    val registeredType = metadata?.projectedClass?.registeredWinRTType()
    if (metadata != null) {
        referenceHostDefinitions[interfaceId]?.let { cached ->
            if (cached.metadata === metadata && cached.registeredType === registeredType) {
                return createValueHost(value, cached.definition, augmentRuntimeInterfaces)
            }
        }
    }
    val propertyType =
        WinRTValueBoxing.propertyTypeForReferenceInterface(interfaceId)
            ?: if (WinRTValueBoxing.isPropertyValueCompatible(value)) {
                WinRTValueBoxing.propertyTypeOf(value)
            } else {
                null
            }
    val runtimeClassName =
        WinRTValueBoxing.boxedRuntimeClassNameForReferenceInterface(interfaceId)
            ?: WinRTValueBoxing.boxedRuntimeClassNameForValue(value)
    val definition = cachedValueHostDefinition(
        key = ValueHostShapeKey(
            kind = ValueHostShapeKind.REFERENCE,
            defaultInterfaceId = interfaceId,
            valueInterfaceId = interfaceId,
            propertyType = propertyType,
            runtimeClassName = runtimeClassName,
            includePropertyValueInterface = includePropertyValueInterface,
        ),
    ) {
        buildList {
            if (includePropertyValueInterface) {
                propertyType?.let { add(createHostPropertyValueInterfaceDefinition(it)) }
            }
            add(ValueBoxingInterop.createHostReferenceInterfaceDefinition(interfaceId))
        }
    }
    // Registration replaces these immutable records. Recheck the observed records before
    // publishing; a later replacement makes the next lookup miss the old entry.
    if (metadata != null &&
        metadata === ValueBoxingMetadata.invariantReferenceHostMetadata(interfaceId, value) &&
        registeredType === metadata.projectedClass.registeredWinRTType()
    ) {
        referenceHostDefinitions[interfaceId] = CachedReferenceHostDefinition(metadata, registeredType, definition)
    }
    return createValueHost(value, definition, augmentRuntimeInterfaces = augmentRuntimeInterfaces)
}

internal fun createReferenceArrayHost(
    interfaceId: Guid,
    value: Any,
): ManagedReferenceHost {
    return createReferenceArrayHost(
        interfaceId = interfaceId,
        value = value,
        includePropertyValueInterface = true,
        augmentRuntimeInterfaces = true,
    )
}

internal fun createReferenceArrayMarshalerHost(
    interfaceId: Guid,
    value: Any,
): WinRTInspectableComObject =
    createReferenceArrayHost(
        interfaceId = interfaceId,
        value = value,
        // CsWinRT adds IPropertyValue to IReferenceArray<T> CCWs as well.  Keep the outbound
        // marshaler shape identical so WinUI collection/dependency-property validation can QI
        // the boxed value before consuming the pointer.
        includePropertyValueInterface = true,
        augmentRuntimeInterfaces = true,
    )

private fun createReferenceArrayHost(
    interfaceId: Guid,
    value: Any,
    includePropertyValueInterface: Boolean,
    augmentRuntimeInterfaces: Boolean,
): WinRTInspectableComObject {
    val propertyType = WinRTValueBoxing.propertyTypeForReferenceArrayInterface(interfaceId)
    val definition = cachedValueHostDefinition(
        key = ValueHostShapeKey(
            kind = ValueHostShapeKind.REFERENCE_ARRAY,
            defaultInterfaceId = interfaceId,
            valueInterfaceId = interfaceId,
            propertyType = propertyType,
            runtimeClassName = WinRTValueBoxing.boxedRuntimeClassNameForReferenceArrayInterface(interfaceId),
            includePropertyValueInterface = includePropertyValueInterface,
        ),
    ) {
        buildList {
            if (includePropertyValueInterface) {
                propertyType?.let { add(createHostPropertyValueInterfaceDefinition(it)) }
            }
            add(ValueBoxingInterop.createHostReferenceArrayInterfaceDefinition(interfaceId))
        }
    }
    return createValueHost(value, definition, augmentRuntimeInterfaces = augmentRuntimeInterfaces)
}
