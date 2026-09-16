package io.github.composefluent.winrt.runtime

// Deliberately in another file: JVM handles and Native thunk storage share the physical
// signature with WinRTAbiFloatingCallbackTest while both call bodies remain direct.
@WinRTAbiCallSite
internal fun invokeSharedFloatingCallback(
    instance: RawComPtr,
    slot: Int,
    first: Float,
    second: Double,
    third: Float,
    fourth: Double,
): Int = TODO("Fixed WinRT ABI call")
