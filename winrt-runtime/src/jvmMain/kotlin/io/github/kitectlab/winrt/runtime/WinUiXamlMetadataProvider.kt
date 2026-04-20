package io.github.kitectlab.winrt.runtime

private val getXamlTypeByFullNameDescriptor = NativeFunctionDescriptor.of(
    NativeValueLayout.JAVA_INT,
    NativeValueLayout.ADDRESS,
    NativeValueLayout.ADDRESS,
    NativeValueLayout.ADDRESS,
)

class WinUiXamlTypeReference(
    pointer: NativePointer,
    interfaceId: Guid = WinUiXamlInterfaceIds.IXamlType,
) : IUnknownReference(pointer, interfaceId)

class WinUiXamlMetadataProviderReference(
    pointer: NativePointer,
    interfaceId: Guid = WinUiXamlInterfaceIds.IXamlMetadataProvider,
) : IUnknownReference(pointer, interfaceId) {
    fun getXamlTypeByFullName(fullName: String): WinUiXamlTypeReference =
        HString.create(fullName).use { hString ->
            NativeInterop.confinedScope().use { scope ->
                val resultOut = NativeInterop.allocatePointerSlot(scope)
                val hr = invokeAbi(
                    slot = WinUiXamlMetadataProviderSlots.GetXamlTypeByFullName,
                    descriptor = getXamlTypeByFullNameDescriptor,
                    hString.handle,
                    resultOut,
                )
                HResult(hr).requireSuccess()
                IUnknownReference(NativeInterop.readPointer(resultOut)).use { reference ->
                    WinUiXamlTypeReference(reference.getRefPointer(), WinUiXamlInterfaceIds.IXamlType)
                }
            }
        }
}

object WinUiXamlMetadataProvider {
    val providerRuntimeClassName: String
        get() = WinUiXamlMetadataProviderInfo.runtimeClassName

    @Volatile
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
                            WinUiXamlMetadataProviderReference(reference.getRefPointer(), WinUiXamlInterfaceIds.IXamlMetadataProvider)
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
        synchronized(this) {
            if (initialized) {
                return
            }
            factory.queryInterface(WinUiXamlInterfaceIds.IXamlControlsXamlMetaDataProviderStatics)
                .getOrThrow()
                .use { statics ->
                    statics.invokeUnitMethod(WinUiXamlControlsXamlMetadataProviderStaticsSlots.Initialize)
                }
            initialized = true
        }
    }
}
