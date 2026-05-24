package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRtEventDefinition
import io.github.composefluent.winrt.metadata.WinRtPropertyDefinition

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
