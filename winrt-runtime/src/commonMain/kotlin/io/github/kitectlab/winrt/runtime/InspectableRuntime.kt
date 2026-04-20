package io.github.kitectlab.winrt.runtime

interface ActivationFactoryProvider {
    fun getActivationFactory(runtimeClassId: RuntimeClassId, interfaceId: Guid): ComObjectReference?
}
