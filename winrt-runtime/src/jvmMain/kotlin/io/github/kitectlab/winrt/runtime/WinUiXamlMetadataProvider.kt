package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

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
                    hString.handle.asMemorySegment(),
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
