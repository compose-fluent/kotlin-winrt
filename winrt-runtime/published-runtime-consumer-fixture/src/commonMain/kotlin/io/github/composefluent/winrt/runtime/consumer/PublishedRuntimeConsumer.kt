package io.github.composefluent.winrt.runtime.consumer

import io.github.composefluent.winrt.runtime.ComObjectReference
import io.github.composefluent.winrt.runtime.IID
import io.github.composefluent.winrt.runtime.WinRTProjectionIntrinsic
import io.github.composefluent.winrt.runtime.WinRTTypeHandle
import io.github.composefluent.winrt.runtime.releaseWinRTManagedProjectionCallLease
import io.github.composefluent.winrt.runtime.tryAcquireWinRTManagedProjectionCallLease

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

fun consumePublishedManagedCallLease(value: Any): Boolean {
    val lease = tryAcquireWinRTManagedProjectionCallLease(
        value,
        WinRTTypeHandle("published-runtime-only-consumer", IID.IUnknown),
    ) ?: return false
    releaseWinRTManagedProjectionCallLease(lease, value)
    return true
}
