package io.github.composefluent.winrt.runtime.consumer

import io.github.composefluent.winrt.runtime.ComObjectReference
import io.github.composefluent.winrt.runtime.WinRTProjectionIntrinsic

data class PublishedRuntimeBoundaryResult(
    val scalar: Int,
    val boolean: Boolean,
    val string: String,
)

fun consumeRuntimeOwnedWrappers(
    reference: ComObjectReference,
    slot: Int,
): PublishedRuntimeBoundaryResult {
    WinRTProjectionIntrinsic.setString(reference, slot, "published-runtime")
    return PublishedRuntimeBoundaryResult(
        scalar = WinRTProjectionIntrinsic.getInt32(reference, slot),
        boolean = WinRTProjectionIntrinsic.getBoolean(reference, slot),
        string = WinRTProjectionIntrinsic.getString(reference, slot),
    )
}
