package io.github.kitectlab.winrt.runtime

object ActivationFactory {
    val iActivationFactoryIid: Guid = IID.IActivationFactory

    fun get(runtimeClassName: String): ActivationFactoryReference {
        return get(runtimeClassName, iActivationFactoryIid) as ActivationFactoryReference
    }

    fun get(runtimeClassName: String, interfaceId: Guid): IUnknownReference {
        val result = CachedActivationFactoryPointers.get(runtimeClassName, interfaceId)
        if (!result.isSuccess) {
            throw WinRtExceptionTranslator.exceptionFor(
                result.hResult,
                "Activation factory lookup for $runtimeClassName",
            )
        }

        return wrapFactory(result.pointer, interfaceId)
    }

    fun tryGet(runtimeClassName: String, interfaceId: Guid = iActivationFactoryIid): ActivationResult =
        RawActivationFactoryLookup.tryGet(runtimeClassName, interfaceId)

    fun activateInstance(runtimeClassName: String): IInspectableReference =
        get(runtimeClassName).use { it.activateInstance() }

    internal fun cachedFactoryCount(): Int = CachedActivationFactoryPointers.cachedFactoryCount()

    internal fun clearCacheForTests() {
        clearRuntimeCache()
    }

    internal fun clearRuntimeCache() {
        CachedActivationFactoryPointers.clearRuntimeCache()
    }

    private fun wrapFactory(pointer: NativePointer, interfaceId: Guid): IUnknownReference =
        if (interfaceId == IID.IActivationFactory) {
            ActivationFactoryReference(pointer.asMemorySegment(), interfaceId)
        } else {
            IUnknownReference(pointer.asMemorySegment(), interfaceId)
        }
}
