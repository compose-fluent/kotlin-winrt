package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRTEventDefinition
import io.github.composefluent.winrt.metadata.WinRTPropertyDefinition
import io.github.composefluent.winrt.metadata.WinRTTypeDefinition
import io.github.composefluent.winrt.metadata.WinRTPropertyGetterInterfaceResolution

internal fun WinRTEventDefinition.hasNativeProjectionAccessorPair(): Boolean =
    hasValidAccessors &&
        (addMethodName != null || addMethodRowId != null) &&
        (removeMethodName != null || removeMethodRowId != null)

internal fun WinRTEventDefinition.hasNativeProjectionAddAccessor(): Boolean =
    hasValidAccessors && (addMethodName != null || addMethodRowId != null)

internal fun WinRTEventDefinition.hasNativeProjectionRemoveAccessor(): Boolean =
    hasValidAccessors && (removeMethodName != null || removeMethodRowId != null)

internal fun WinRTPropertyDefinition.hasNativeProjectionGetterAccessor(): Boolean =
    hasValidAccessors && (getterMethodName != null || getterMethodRowId != null)

internal fun WinRTPropertyDefinition.hasNativeProjectionSetterAccessor(): Boolean =
    hasValidAccessors && (setterMethodName != null || setterMethodRowId != null)

internal fun WinRTPropertyDefinition.hasNativeProjectionPropertyAccessor(): Boolean =
    hasNativeProjectionGetterAccessor() || hasNativeProjectionSetterAccessor()

internal fun findNativeProjectionGetterInterface(
    setterInterfaceType: WinRTTypeDefinition,
    property: WinRTPropertyDefinition,
    typesByQualifiedName: Map<String, WinRTTypeDefinition>,
): WinRTPropertyGetterInterfaceResolution? =
    io.github.composefluent.winrt.metadata.findNativeProjectionGetterInterface(
        setterInterfaceType = setterInterfaceType,
        property = property,
        typesByQualifiedName = typesByQualifiedName,
    )
