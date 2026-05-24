package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRtEventDefinition

internal fun WinRtEventDefinition.hasNativeProjectionAccessorPair(): Boolean =
    hasValidAccessors &&
        (addMethodName != null || addMethodRowId != null) &&
        (removeMethodName != null || removeMethodRowId != null)
