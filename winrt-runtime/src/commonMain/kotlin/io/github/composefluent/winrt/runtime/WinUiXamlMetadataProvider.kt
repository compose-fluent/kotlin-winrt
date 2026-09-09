package io.github.composefluent.winrt.runtime

class WinUiXamlTypeReference(
    pointer: RawAddress,
    interfaceId: Guid = WinUiXamlInterfaceIds.IXamlType,
) : IUnknownReference(pointer.asRawComPtr(), interfaceId)

internal data class WinUiXamlXmlnsDefinition(
    val xmlNamespace: String,
    val namespace: String,
)

internal data class WinUiXamlXmlnsDefinitionsResult(
    val hResult: Int,
    val definitions: List<WinUiXamlXmlnsDefinition>,
)

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

    internal fun getXmlnsDefinitions(): WinUiXamlXmlnsDefinitionsResult =
        PlatformAbi.confinedScope().use { scope ->
            val countOut = PlatformAbi.allocateInt32Slot(scope)
            val definitionsOut = PlatformAbi.allocatePointerSlot(scope)
            PlatformAbi.writeInt32(countOut, 0)
            PlatformAbi.writePointer(definitionsOut, PlatformAbi.nullPointer)

            val hResult =
                ComVtableInvoker.invokeArgs(
                    pointer,
                    WinUiXamlMetadataProviderSlots.GetXmlnsDefinitions,
                    countOut,
                    definitionsOut,
                )
            val definitionsPointer = PlatformAbi.readPointer(definitionsOut)
            val definitionCount = PlatformAbi.readInt32(countOut).toUInt().toLong()
            if (PlatformAbi.isNull(definitionsPointer)) {
                check(definitionCount == 0L || !HResult(hResult).isSuccess) {
                    "XAML metadata provider returned a null XmlnsDefinition array for $definitionCount entries."
                }
                return@use WinUiXamlXmlnsDefinitionsResult(hResult, emptyList())
            }

            readAndReleaseXmlnsDefinitions(
                hResult = hResult,
                definitionsPointer = definitionsPointer,
                definitionCount = definitionCount,
            )
        }
}

internal const val winUiXamlXmlnsDefinitionSizeBytes = 16L
internal const val winUiXamlXmlnsDefinitionNamespaceOffsetBytes = 8L

private fun readAndReleaseXmlnsDefinitions(
    hResult: Int,
    definitionsPointer: RawAddress,
    definitionCount: Long,
): WinUiXamlXmlnsDefinitionsResult {
    require(definitionCount <= Int.MAX_VALUE) {
        "XAML metadata provider returned too many XmlnsDefinition entries: $definitionCount."
    }

    val handles = ArrayList<Pair<RawAddress, RawAddress>>(definitionCount.toInt())
    try {
        repeat(definitionCount.toInt()) { index ->
            val element = PlatformAbi.slice(
                definitionsPointer,
                index.toLong() * winUiXamlXmlnsDefinitionSizeBytes,
                winUiXamlXmlnsDefinitionSizeBytes,
            )
            handles +=
                PlatformAbi.readPointer(element) to
                    PlatformAbi.readPointer(
                        PlatformAbi.slice(
                            element,
                            winUiXamlXmlnsDefinitionNamespaceOffsetBytes,
                            Long.SIZE_BYTES.toLong(),
                        ),
                    )
        }

        val definitions =
            if (HResult(hResult).isSuccess) {
                handles.map { (xmlNamespace, namespace) ->
                    WinUiXamlXmlnsDefinition(
                        xmlNamespace = PlatformAbi.readHString(xmlNamespace),
                        namespace = PlatformAbi.readHString(namespace),
                    )
                }
            } else {
                emptyList()
            }
        return WinUiXamlXmlnsDefinitionsResult(hResult, definitions)
    } finally {
        handles.forEach { (xmlNamespace, namespace) ->
            runCatching { WinRTPlatformApi.windowsDeleteStringRaw(xmlNamespace) }
            runCatching { WinRTPlatformApi.windowsDeleteStringRaw(namespace) }
        }
        runCatching { WinRTPlatformApi.coTaskMemFreeRaw(definitionsPointer) }
    }
}

internal fun mergeWinUiXamlXmlnsDefinitions(
    providerDefinitions: Iterable<List<WinUiXamlXmlnsDefinition>>,
): List<WinUiXamlXmlnsDefinition> =
    buildList {
        providerDefinitions.forEach(::addAll)
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
