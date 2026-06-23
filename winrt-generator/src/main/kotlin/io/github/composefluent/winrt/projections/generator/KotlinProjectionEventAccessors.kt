package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRtEventDefinition
import io.github.composefluent.winrt.metadata.WinRtPropertyDefinition
import io.github.composefluent.winrt.metadata.WinRtTypeDefinition
import io.github.composefluent.winrt.metadata.WinRtPropertyGetterInterfaceResolution

internal fun WinRtEventDefinition.hasNativeProjectionAccessorPair(): Boolean =
    hasValidAccessors &&
        (addMethodName != null || addMethodRowId != null) &&
        (removeMethodName != null || removeMethodRowId != null)

internal fun WinRtEventDefinition.hasNativeProjectionAddAccessor(): Boolean =
    hasValidAccessors && (addMethodName != null || addMethodRowId != null)

internal fun WinRtEventDefinition.hasNativeProjectionRemoveAccessor(): Boolean =
    hasValidAccessors && (removeMethodName != null || removeMethodRowId != null)

internal fun WinRtPropertyDefinition.hasNativeProjectionGetterAccessor(): Boolean =
    hasValidAccessors && (getterMethodName != null || getterMethodRowId != null)

internal fun WinRtPropertyDefinition.hasNativeProjectionSetterAccessor(): Boolean =
    hasValidAccessors && (setterMethodName != null || setterMethodRowId != null)

internal fun WinRtPropertyDefinition.hasNativeProjectionPropertyAccessor(): Boolean =
    hasNativeProjectionGetterAccessor() || hasNativeProjectionSetterAccessor()

internal fun findNativeProjectionGetterInterface(
    setterInterfaceType: WinRtTypeDefinition,
    property: WinRtPropertyDefinition,
    typesByQualifiedName: Map<String, WinRtTypeDefinition>,
): WinRtPropertyGetterInterfaceResolution? =
    io.github.composefluent.winrt.metadata.findNativeProjectionGetterInterface(
        setterInterfaceType = setterInterfaceType,
        property = property,
        typesByQualifiedName = typesByQualifiedName,
    )
