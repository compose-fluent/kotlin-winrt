package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

internal fun createSyntheticValueCcwDefinition(
    value: Any,
    declaredReferenceArrayElementType: KClass<*>? = null,
): WinRTCcwDefinition? {
    val propertyType =
        if (WinRTValueBoxing.isPropertyValueCompatible(value, declaredReferenceArrayElementType)) {
            WinRTValueBoxing.propertyTypeOf(value, declaredReferenceArrayElementType)
        } else {
            null
        }
    val referenceArrayInterfaceId =
        ValueBoxingMetadata.referenceArrayInterfaceIdForValue(value, declaredReferenceArrayElementType)
    val referenceInterfaceId =
        if (referenceArrayInterfaceId == null) {
            ValueBoxingMetadata.referenceInterfaceIdForValue(value)
        } else {
            null
        }
    val valueInterfaceId = referenceArrayInterfaceId ?: referenceInterfaceId
    if (propertyType == null && valueInterfaceId == null) {
        return null
    }
    val defaultInterfaceId = if (propertyType != null) IID.IPropertyValue else requireNotNull(valueInterfaceId)
    val shapeKind =
        when {
            referenceArrayInterfaceId != null -> ValueHostShapeKind.REFERENCE_ARRAY
            referenceInterfaceId != null -> ValueHostShapeKind.REFERENCE
            else -> ValueHostShapeKind.PROPERTY_VALUE
        }
    val runtimeClassName =
        WinRTValueBoxing.boxedRuntimeClassNameForValue(value, declaredReferenceArrayElementType)
    return cachedValueHostDefinition(
        key = ValueHostShapeKey(
            kind = shapeKind,
            defaultInterfaceId = defaultInterfaceId,
            valueInterfaceId = valueInterfaceId,
            propertyType = propertyType,
            runtimeClassName = runtimeClassName,
            includePropertyValueInterface = propertyType != null,
        ),
    ) {
        buildList {
            propertyType?.let { add(createHostPropertyValueInterfaceDefinition(it)) }
            referenceArrayInterfaceId?.let {
                add(ValueBoxingInterop.createHostReferenceArrayInterfaceDefinition(it))
            } ?: referenceInterfaceId?.let {
                add(ValueBoxingInterop.createHostReferenceInterfaceDefinition(it))
            }
        }
    }
}

internal fun createSyntheticInspectableCcwDefinition(
    value: Any,
    declaredReferenceArrayElementType: KClass<*>? = null,
): WinRTCcwDefinition? {
    createSyntheticValueCcwDefinition(value, declaredReferenceArrayElementType)?.let { return it }
    return createSyntheticInterfaceCcwDefinition(value)
}

/** CsWinRT GetInterfaceTableEntries includes projected interfaces alongside authored ones.
 * Keep interface adaptation separate from value boxing so notification registrations
 * cannot suppress IEnumerable/IBindableIterable on the same managed object. */
internal fun createSyntheticInterfaceCcwDefinition(value: Any): WinRTCcwDefinition? {
    val interfaces = buildList {
        // CsWinRT Projections/Bindable.net5.cs exposes IEnumerable as IBindableIterable
        // even when the caller's declared ABI type is only IInspectable (ItemsSource).
        if (value is Iterable<*>) add(bindableIterableDefinition())
        if (value is AutoCloseable) add(createClosableInspectableInterfaceDefinition(value))
    }
    if (interfaces.isEmpty()) return null
    return WinRTCcwDefinition(
        interfaceDefinitions = interfaces,
        defaultInterfaceId = interfaces.first().interfaceId,
        runtimeClassName = defaultInspectableRuntimeClassNameFor(value),
    )
}

internal fun defaultInspectableRuntimeClassNameFor(value: Any): String? {
    WinRTValueBoxing.boxedRuntimeClassNameForValue(value)?.let { return it }
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
    runtimeClassProjectionAttempted: Boolean = false,
): Any? {
    if (!runtimeClassName.isNullOrBlank()) {
        // CsWinRT's MarshalInspectable<object> only attempts value unboxing when the
        // runtime class name identifies a boxed WinRT value.  A generic inspectable
        // such as IKeyValuePair<String, Object> must stay an RCW.  The exact closed
        // projection plan is cached by runtime class name; only an unknown boxed name
        // reaches the compatibility QI ladder below.
        if (!WinRTValueBoxing.isBoxedRuntimeClassName(runtimeClassName)) {
            return null
        }

        if (!runtimeClassProjectionAttempted) {
            WinRTValueBoxing.tryProjectInspectableForRuntimeClassName(inspectable, runtimeClassName)?.let { return it }
        }
    }

    WinRTPropertyValueProjection.tryFromBorrowedAbi(inspectable.pointer.asRawAddress())?.let { return it }
    WinRTValueBoxing.tryProjectInspectableReference(inspectable)?.let { return it }
    WinRTValueBoxing.tryProjectInspectableReferenceArray(inspectable)?.let { return it }
    return null
}

/** Closed-value attempt for an ABI-owned pointer that remains live throughout this call.
 * A null result still requires the normal interface fallback; that fallback must be told
 * the closed-value plan was already attempted, so a nullable delegate does not read Value twice.
 */
internal fun tryProjectInspectableValueForRuntimeClassName(
    inspectablePointer: RawAddress,
    runtimeClassName: String?,
): Any? {
    if (runtimeClassName.isNullOrBlank()) {
        return null
    }
    return WinRTValueBoxing.tryProjectInspectableForRuntimeClassName(inspectablePointer, runtimeClassName)
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
