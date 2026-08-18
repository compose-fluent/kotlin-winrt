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

/**
 * Shape cache for the short-lived outbound IReference marshaler host.
 *
 * Unlike a public boxed-value host, this path never exposes IPropertyValue or the runtime
 * compatibility suffix.  Its shape therefore depends only on the closed interface IID; looking
 * up property metadata and constructing a [ValueHostShapeKey] for every setter was both redundant
 * and particularly expensive on Native, where the generic cache is lock based.
 */
private val referenceMarshalerDefinitionCache = ConcurrentCacheMap<Guid, WinRTCcwDefinition>()

@kotlin.concurrent.Volatile
private var lastReferenceMarshalerDefinition: WinRTCcwDefinition? = null

@kotlin.concurrent.Volatile
private var lastReferenceMarshalerInterfaceLowBits: Long = 0L

@kotlin.concurrent.Volatile
private var lastReferenceMarshalerInterfaceHighBits: Long = 0L

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
 * The public boxed-value path still exposes the complete CsWinRT-compatible interface table.
 * A typed setter already carries the closed IReference ABI, however, so constructing the public
 * IPropertyValue/suffix interfaces for a host that lives only until the setter returns is wasted
 * work. The shape remains metadata-composed and cached; only the managed value and COM lifetime
 * stay per instance.
 */
internal fun createReferenceMarshalerHost(
    interfaceId: Guid,
    value: Any,
): WinRTInspectableComObject =
    createValueHost(
        value = value,
        baseDefinition = cachedReferenceMarshalerDefinition(interfaceId),
        augmentRuntimeInterfaces = false,
    )

private fun cachedReferenceMarshalerDefinition(interfaceId: Guid): WinRTCcwDefinition {
    val cached = lastReferenceMarshalerDefinition
    if (
        cached != null &&
        lastReferenceMarshalerInterfaceLowBits == interfaceId.abiLowBits &&
        lastReferenceMarshalerInterfaceHighBits == interfaceId.abiHighBits
    ) {
        return cached
    }

    val definition = referenceMarshalerDefinitionCache.computeIfAbsent(interfaceId) {
        WinRTCcwDefinition(
            interfaceDefinitions = listOf(
                ValueBoxingInterop.createHostReferenceInterfaceDefinition(interfaceId),
            ),
            defaultInterfaceId = interfaceId,
        )
    }
    // Publish the key before the definition.  A racing reader may miss and fall back to the
    // locked cache, but it can never observe a matching key with a partially initialized shape.
    lastReferenceMarshalerInterfaceLowBits = interfaceId.abiLowBits
    lastReferenceMarshalerInterfaceHighBits = interfaceId.abiHighBits
    lastReferenceMarshalerDefinition = definition
    return definition
}

private fun createReferenceHost(
    interfaceId: Guid,
    value: Any,
    includePropertyValueInterface: Boolean,
    augmentRuntimeInterfaces: Boolean,
): WinRTInspectableComObject {
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
        includePropertyValueInterface = false,
        augmentRuntimeInterfaces = false,
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
