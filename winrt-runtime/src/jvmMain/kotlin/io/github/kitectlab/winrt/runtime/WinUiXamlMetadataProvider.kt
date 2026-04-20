package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

object WinUiXamlInterfaceIds {
    val IXamlMetadataProvider: Guid = Guid("A96251F0-2214-5D53-8746-CE99A2593CD7")
    val IXamlType: Guid = Guid("D24219DF-7EC9-57F1-A27B-6AF251D9C5BC")
    val IXamlControlsXamlMetaDataProviderStatics: Guid = Guid("2D7EB3FD-ECDB-5084-B7E0-12F9598381EF")
}

object WinUiXamlMetadataProviderSlots {
    const val GetXamlType = 6
    const val GetXamlTypeByFullName = 7
    const val GetXmlnsDefinitions = 8
}

object WinUiXamlControlsXamlMetadataProviderStaticsSlots {
    const val Initialize = 6
}

class WinUiXamlTypeReference(
    pointer: MemorySegment,
    interfaceId: Guid = WinUiXamlInterfaceIds.IXamlType,
) : IUnknownReference(pointer, interfaceId)

class WinUiXamlMetadataProviderReference(
    pointer: MemorySegment,
    interfaceId: Guid = WinUiXamlInterfaceIds.IXamlMetadataProvider,
) : IUnknownReference(pointer, interfaceId) {
    fun getXamlTypeByFullName(fullName: String): WinUiXamlTypeReference =
        HString.create(fullName).use { hString ->
            Arena.ofConfined().use { arena ->
                val resultOut = arena.allocate(ValueLayout.ADDRESS)
                val hr = invokeAbi(
                    slot = WinUiXamlMetadataProviderSlots.GetXamlTypeByFullName,
                    descriptor = FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                    ),
                    hString.handle,
                    resultOut,
                )
                HResult(hr).requireSuccess()
                IUnknownReference(resultOut.get(ValueLayout.ADDRESS, 0)).use { reference ->
                    WinUiXamlTypeReference(reference.getRef(), WinUiXamlInterfaceIds.IXamlType)
                }
            }
        }
}

object WinUiXamlMetadataProvider {
    const val providerRuntimeClassName: String = "Microsoft.UI.Xaml.XamlTypeInfo.XamlControlsXamlMetaDataProvider"

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
                            WinUiXamlMetadataProviderReference(reference.getRef(), WinUiXamlInterfaceIds.IXamlMetadataProvider)
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
