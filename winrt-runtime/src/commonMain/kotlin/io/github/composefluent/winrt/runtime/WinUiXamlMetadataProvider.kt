package io.github.composefluent.winrt.runtime

class WinUiXamlTypeReference(
    pointer: RawAddress,
    interfaceId: Guid = WinUiXamlInterfaceIds.IXamlType,
) : IUnknownReference(pointer.asRawComPtr(), interfaceId)

class WinUiXamlMetadataProviderReference(
    pointer: RawAddress,
    interfaceId: Guid = WinUiXamlInterfaceIds.IXamlMetadataProvider,
) : IUnknownReference(pointer.asRawComPtr(), interfaceId) {
    fun getXamlTypeByFullName(fullName: String): WinUiXamlTypeReference =
        HString.create(fullName).use { hString ->
            PlatformAbi.confinedScope().use { scope ->
                val resultOut = PlatformAbi.allocatePointerSlot(scope)
                val hr =
                    ComVtableInvoker.invokeArgs(
                        pointer,
                        WinUiXamlMetadataProviderSlots.GetXamlTypeByFullName,
                        hString.handle,
                        resultOut,
                    )
                HResult(hr).requireSuccess()
                IUnknownReference(PlatformAbi.readPointer(resultOut).asRawComPtr()).use { reference ->
                    WinUiXamlTypeReference(reference.getRefPointer().asRawAddress(), WinUiXamlInterfaceIds.IXamlType)
                }
            }
        }
}

object WinUiXamlMetadataProvider {
    val providerRuntimeClassName: String
        get() = WinUiXamlMetadataProviderInfo.runtimeClassName

    private val initializationLock = PlatformLock()
    private var initialized = false

    fun create(): WinUiXamlMetadataProviderReference {
        val factory = ActivationFactory.get(providerRuntimeClassName)
        return factory.use { activationFactory ->
            initialize(activationFactory)
            activationFactory.activateInstance().use { instance ->
                instance.queryInterface(WinUiXamlInterfaceIds.IXamlMetadataProvider)
                    .getOrThrow()
                    .let { reference ->
                        try {
                            WinUiXamlMetadataProviderReference(reference.getRefPointer().asRawAddress(), WinUiXamlInterfaceIds.IXamlMetadataProvider)
                        } finally {
                            reference.close()
                        }
                    }
            }
        }
    }

    fun tryCreate(): WinUiXamlMetadataProviderReference? =
        runCatching(::create).getOrNull()

    private fun initialize(factory: ActivationFactoryReference) {
        if (initialized) {
            return
        }
        initializationLock.withLock {
            if (initialized) {
                return@withLock
            }
            factory.queryInterface(WinUiXamlInterfaceIds.IXamlControlsXamlMetaDataProviderStatics)
                .getOrThrow()
                .use { statics ->
                    val hr =
                        ComVtableInvoker.invoke(
                            statics.pointer,
                            WinUiXamlControlsXamlMetadataProviderStaticsSlots.Initialize,
                        )
                    WinRtPlatformApi.checkSucceededRaw(hr)
                }
            initialized = true
        }
    }
}
