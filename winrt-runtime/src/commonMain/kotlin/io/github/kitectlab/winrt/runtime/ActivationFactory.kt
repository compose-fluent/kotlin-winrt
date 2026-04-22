package io.github.kitectlab.winrt.runtime

object ActivationFactory {
    val iActivationFactoryIid: Guid = IID.IActivationFactory

    fun get(runtimeClassName: String): ActivationFactoryReference {
        return get(runtimeClassName, iActivationFactoryIid) as ActivationFactoryReference
    }

    fun get(runtimeClassName: String, interfaceId: Guid): IUnknownReference {
        val result = CachedActivationFactoryPointers.get(runtimeClassName, interfaceId)
        if (!result.isSuccess) {
            if (
                result.hResult == KnownHResults.REGDB_E_CLASSNOTREG &&
                !FeatureSwitches.enableManifestFreeActivation &&
                !FeatureSwitches.manifestFreeActivationReportOriginalException
            ) {
                throw WinRtUnsupportedOperationException(
                    message =
                        "Failed to activate type with runtime class name '$runtimeClassName' with 'RoGetActivationFactory' " +
                            "(it returned 0x80040154, ie. 'REGDB_E_CLASSNOTREG'). Make sure to add the activatable class id " +
                            "for the type to the APPX manifest, or enable the manifest free activation fallback path by " +
                            "setting the '${FeatureSwitches.EnableManifestFreeActivationPropertyName}' system property " +
                            "(note: the fallback path incurs a performance hit).",
                    hResult = result.hResult,
                )
            }
            throwHResultFailure(result.hResult, "Activation factory lookup for $runtimeClassName")
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
        ComReferenceWrapperSupport.wrap(
            kind = ComReferenceWrapperSupport.kindForInterfaceId(interfaceId),
            pointer = pointer,
            interfaceId = interfaceId,
            wrapUnknown = { wrappedPointer, wrappedInterfaceId ->
                IUnknownReference(wrappedPointer, wrappedInterfaceId)
            },
            wrapInspectable = { wrappedPointer, wrappedInterfaceId ->
                IUnknownReference(wrappedPointer, wrappedInterfaceId)
            },
            wrapActivationFactory = { wrappedPointer, wrappedInterfaceId ->
                ActivationFactoryReference(wrappedPointer, wrappedInterfaceId)
            },
        )
}
