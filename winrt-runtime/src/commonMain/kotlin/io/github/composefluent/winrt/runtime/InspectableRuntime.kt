package io.github.composefluent.winrt.runtime

interface ActivationFactoryProvider {
    fun getActivationFactory(runtimeClassId: RuntimeClassId, interfaceId: Guid): ComObjectReference?
}
