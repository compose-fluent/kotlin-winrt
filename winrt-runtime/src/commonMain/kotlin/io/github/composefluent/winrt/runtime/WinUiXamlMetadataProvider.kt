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

    fun create(runtimeClassName: String = providerRuntimeClassName): WinUiXamlMetadataProviderReference {
        val factory = ActivationFactory.get(runtimeClassName)
        return factory.use { activationFactory ->
            if (runtimeClassName == providerRuntimeClassName) {
                initialize(activationFactory)
            }
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

    fun tryCreate(runtimeClassName: String = providerRuntimeClassName): WinUiXamlMetadataProviderReference? =
        runCatching { create(runtimeClassName) }.getOrNull()

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
                    WinRTPlatformApi.checkSucceededRaw(hr)
                }
            initialized = true
        }
    }
}

object WinUiXamlMetadataProviderRegistry {
    private val lock = PlatformLock()
    private val runtimeClassNames = mutableListOf<String>()
    private var runtimeAssetProvidersLoaded = false

    fun register(runtimeClassName: String) {
        require(runtimeClassName.isNotBlank()) { "XAML metadata provider runtime class name must not be blank." }
        lock.withLock {
            if (runtimeClassName !in runtimeClassNames) {
                runtimeClassNames += runtimeClassName
            }
        }
    }

    fun registeredRuntimeClassNames(): List<String> =
        lock.withLock {
            loadRuntimeAssetProviders()
            runtimeClassNames.toList()
        }

    internal fun clearForTests() {
        lock.withLock {
            runtimeClassNames.clear()
            runtimeAssetProvidersLoaded = false
        }
    }

    private fun loadRuntimeAssetProviders() {
        if (runtimeAssetProvidersLoaded) {
            return
        }
        WinUiXamlMetadataProviderRuntimeAssets.loadProviderRuntimeClassNames().forEach { runtimeClassName ->
            if (runtimeClassName !in runtimeClassNames) {
                runtimeClassNames += runtimeClassName
            }
        }
        runtimeAssetProvidersLoaded = true
    }
}
