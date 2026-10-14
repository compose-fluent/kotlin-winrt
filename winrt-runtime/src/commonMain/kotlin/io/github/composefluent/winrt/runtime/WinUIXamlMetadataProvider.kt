package io.github.composefluent.winrt.runtime

open class XamlTypeReference(
    pointer: RawAddress,
    interfaceId: Guid = XamlInterfaceIds.IXamlType,
) : IUnknownReference(pointer.asRawComPtr(), interfaceId)

class WinUIXamlTypeReference(
    pointer: RawAddress,
    interfaceId: Guid = WinUIXamlInterfaceIds.IXamlType,
) : XamlTypeReference(pointer, interfaceId)

internal data class WinUIXamlXmlnsDefinition(
    val xmlNamespace: String,
    val namespace: String,
)

internal data class WinUIXamlXmlnsDefinitionsResult(
    val hResult: Int,
    val definitions: List<WinUIXamlXmlnsDefinition>,
)

open class XamlMetadataProviderReference(
    pointer: RawAddress,
    interfaceId: Guid = XamlInterfaceIds.IXamlMetadataProvider,
    private val xamlTypeInterfaceId: Guid = XamlInterfaceIds.IXamlType,
) : IUnknownReference(pointer.asRawComPtr(), interfaceId) {
    open fun getXamlTypeByFullName(fullName: String): XamlTypeReference? =
        getXamlTypePointer(fullName).takeUnless(PlatformAbi::isNull)?.let { pointer ->
            XamlTypeReference(pointer, xamlTypeInterfaceId)
        }

    protected fun getXamlTypePointer(fullName: String): RawAddress =
        HString.create(fullName).use { hString ->
            PlatformAbi.confinedScope().use { scope ->
                val resultOut = PlatformAbi.allocatePointerSlot(scope)
                val hr =
                    ComVtableInvoker.invokeArgs(
                        pointer,
                        WinUIXamlMetadataProviderSlots.GetXamlTypeByFullName,
                        hString.handle,
                        resultOut,
                    )
                HResult(hr).requireSuccess()
                PlatformAbi.readPointer(resultOut)
            }
        }

    internal fun getXmlnsDefinitions(): WinUIXamlXmlnsDefinitionsResult =
        PlatformAbi.confinedScope().use { scope ->
            val countOut = PlatformAbi.allocateInt32Slot(scope)
            val definitionsOut = PlatformAbi.allocatePointerSlot(scope)
            PlatformAbi.writeInt32(countOut, 0)
            PlatformAbi.writePointer(definitionsOut, PlatformAbi.nullPointer)

            val hResult =
                ComVtableInvoker.invokeArgs(
                    pointer,
                    WinUIXamlMetadataProviderSlots.GetXmlnsDefinitions,
                    countOut,
                    definitionsOut,
                )
            val definitionsPointer = PlatformAbi.readPointer(definitionsOut)
            val definitionCount = PlatformAbi.readInt32(countOut).toUInt().toLong()
            if (PlatformAbi.isNull(definitionsPointer)) {
                check(definitionCount == 0L || !HResult(hResult).isSuccess) {
                    "XAML metadata provider returned a null XmlnsDefinition array for $definitionCount entries."
                }
                return@use WinUIXamlXmlnsDefinitionsResult(hResult, emptyList())
            }

            readAndReleaseXmlnsDefinitions(
                hResult = hResult,
                definitionsPointer = definitionsPointer,
                definitionCount = definitionCount,
            )
        }
}

/** Compatibility surface that always uses WinUI identities, regardless of the host selection. */
class WinUIXamlMetadataProviderReference(
    pointer: RawAddress,
    interfaceId: Guid = WinUIXamlInterfaceIds.IXamlMetadataProvider,
) : XamlMetadataProviderReference(pointer, interfaceId, WinUIXamlInterfaceIds.IXamlType) {
    override fun getXamlTypeByFullName(fullName: String): WinUIXamlTypeReference =
        WinUIXamlTypeReference(getXamlTypePointer(fullName))
}

internal const val winUIXamlXmlnsDefinitionSizeBytes = 16L
internal const val winUIXamlXmlnsDefinitionNamespaceOffsetBytes = 8L

private fun readAndReleaseXmlnsDefinitions(
    hResult: Int,
    definitionsPointer: RawAddress,
    definitionCount: Long,
): WinUIXamlXmlnsDefinitionsResult {
    require(definitionCount <= Int.MAX_VALUE) {
        "XAML metadata provider returned too many XmlnsDefinition entries: $definitionCount."
    }

    val handles = ArrayList<Pair<RawAddress, RawAddress>>(definitionCount.toInt())
    try {
        repeat(definitionCount.toInt()) { index ->
            val element = PlatformAbi.slice(
                definitionsPointer,
                index.toLong() * winUIXamlXmlnsDefinitionSizeBytes,
                winUIXamlXmlnsDefinitionSizeBytes,
            )
            handles +=
                PlatformAbi.readPointer(element) to
                    PlatformAbi.readPointer(
                        PlatformAbi.slice(
                            element,
                            winUIXamlXmlnsDefinitionNamespaceOffsetBytes,
                            Long.SIZE_BYTES.toLong(),
                        ),
                    )
        }

        val definitions =
            if (HResult(hResult).isSuccess) {
                handles.map { (xmlNamespace, namespace) ->
                    WinUIXamlXmlnsDefinition(
                        xmlNamespace = PlatformAbi.readHString(xmlNamespace),
                        namespace = PlatformAbi.readHString(namespace),
                    )
                }
            } else {
                emptyList()
            }
        return WinUIXamlXmlnsDefinitionsResult(hResult, definitions)
    } finally {
        handles.forEach { (xmlNamespace, namespace) ->
            runCatching { WinRTPlatformApi.windowsDeleteStringRaw(xmlNamespace) }
            runCatching { WinRTPlatformApi.windowsDeleteStringRaw(namespace) }
        }
        runCatching { WinRTPlatformApi.coTaskMemFreeRaw(definitionsPointer) }
    }
}

internal fun mergeWinUIXamlXmlnsDefinitions(
    providerDefinitions: Iterable<List<WinUIXamlXmlnsDefinition>>,
): List<WinUIXamlXmlnsDefinition> =
    buildList {
        providerDefinitions.forEach(::addAll)
    }

object WinUIXamlMetadataProvider {
    val providerRuntimeClassName: String
        get() = WinUIXamlMetadataProviderInfo.runtimeClassName

    private val initializationLock = PlatformLock()
    private var initialized = false

    fun create(runtimeClassName: String = providerRuntimeClassName): WinUIXamlMetadataProviderReference {
        val factory = ActivationFactory.get(runtimeClassName)
        return factory.use { activationFactory ->
            if (runtimeClassName == providerRuntimeClassName) {
                initialize(activationFactory)
            }
            activationFactory.activateInstance().use { instance ->
                instance.queryInterface(WinUIXamlInterfaceIds.IXamlMetadataProvider)
                    .getOrThrow()
                    .let { reference ->
                        try {
                            WinUIXamlMetadataProviderReference(reference.getRefPointer().asRawAddress(), WinUIXamlInterfaceIds.IXamlMetadataProvider)
                        } finally {
                            reference.close()
                        }
                    }
            }
        }
    }

    fun tryCreate(runtimeClassName: String = providerRuntimeClassName): WinUIXamlMetadataProviderReference? =
        runCatching { create(runtimeClassName) }.getOrNull()

    private fun initialize(factory: ActivationFactoryReference) {
        if (initialized) {
            return
        }
        initializationLock.withLock {
            if (initialized) {
                return@withLock
            }
            factory.queryInterface(WinUIXamlInterfaceIds.IXamlControlsXamlMetaDataProviderStatics)
                .getOrThrow()
                .use { statics ->
                    val hr =
                        ComVtableInvoker.invoke(
                            statics.pointer,
                            WinUIXamlControlsXamlMetadataProviderStaticsSlots.Initialize,
                        )
                    WinRTPlatformApi.checkSucceededRaw(hr)
                }
            initialized = true
        }
    }
}

/** Activates a component metadata provider using the selected XAML ABI. */
object XamlMetadataProvider {
    fun create(runtimeClassName: String): XamlMetadataProviderReference {
        val abi = XamlProjectionConfiguration.abi
        if (!XamlProjectionConfiguration.useWindowsUiXamlProjections) {
            return WinUIXamlMetadataProvider.create(runtimeClassName)
        }
        return ActivationFactory.get(runtimeClassName).use { factory ->
            factory.activateInstance().use { instance ->
                instance.queryInterface(abi.metadataProvider).getOrThrow().use { reference ->
                    XamlMetadataProviderReference(reference.getRefPointer().asRawAddress(), abi.metadataProvider, abi.type)
                }
            }
        }
    }

    fun tryCreate(runtimeClassName: String): XamlMetadataProviderReference? =
        runCatching { create(runtimeClassName) }.getOrNull()
}

/** Shared component-provider registrations; existing WinUI registrations use the same registry. */
object XamlMetadataProviderRegistry {
    fun register(runtimeClassName: String) = WinUIXamlMetadataProviderRegistry.register(runtimeClassName)

    fun registeredRuntimeClassNames(): List<String> = WinUIXamlMetadataProviderRegistry.registeredRuntimeClassNames()

    internal fun providerRuntimeClassNames(): List<String> =
        listOfNotNull(XamlProjectionConfiguration.abi.defaultMetadataProviderRuntimeClassName) +
            registeredRuntimeClassNames()
}

object WinUIXamlMetadataProviderRegistry {
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
        WinUIXamlMetadataProviderRuntimeAssets.loadProviderRuntimeClassNames().forEach { runtimeClassName ->
            if (runtimeClassName !in runtimeClassNames) {
                runtimeClassNames += runtimeClassName
            }
        }
        runtimeAssetProvidersLoaded = true
    }
}
