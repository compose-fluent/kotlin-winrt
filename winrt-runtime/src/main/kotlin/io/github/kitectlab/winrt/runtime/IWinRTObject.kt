package io.github.kitectlab.winrt.runtime

/**
 * Minimal Kotlin-side equivalent of `.cswinrt/src/WinRT.Runtime/IWinRTObject`.
 *
 * The full cswinrt surface also carries query-interface caches and dynamic-cast hooks.
 * The current JVM generator slice only needs stable access to the wrapped native object
 * so projected parameters can be marshaled through the same runtime contract instead of
 * reaching into generated `_inner` fields directly.
 */
interface IWinRTObject {
    val nativeObject: ComObjectReference
}
