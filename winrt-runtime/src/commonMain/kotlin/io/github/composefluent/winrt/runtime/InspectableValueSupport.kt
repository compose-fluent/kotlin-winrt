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

        WinRTValueBoxing.tryProjectInspectableForRuntimeClassName(inspectable, runtimeClassName)?.let { return it }
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
