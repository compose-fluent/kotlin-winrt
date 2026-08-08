package io.github.composefluent.winrt.runtime

@WinRTCallerOwnedResult
data class WinRTComposableFactoryResult(
    val inner: RawComPtr,
    val instance: RawComPtr,
)
