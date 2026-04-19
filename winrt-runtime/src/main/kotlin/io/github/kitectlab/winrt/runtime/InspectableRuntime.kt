package io.github.kitectlab.winrt.runtime

/**
 * Minimal JVM-side seed for the IInspectable/activation surface mirrored from
 * `.cswinrt/src/WinRT.Runtime`. This is intentionally narrow until ABI interop lands.
 */
data class RuntimeClassId(val value: String)

interface ActivationFactoryProvider {
    fun getActivationFactory(runtimeClassId: RuntimeClassId, interfaceId: Guid): ComObjectReference?
}

interface IInspectable {
    val runtimeClassId: RuntimeClassId
}
