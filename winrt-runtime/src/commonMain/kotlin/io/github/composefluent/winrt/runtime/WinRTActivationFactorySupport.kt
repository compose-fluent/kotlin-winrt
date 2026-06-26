package io.github.composefluent.winrt.runtime

fun interface WinRTActivationFactory {
    fun activateInstance(): ComObjectReference
}

internal object WinRTActivationFactorySupport {
    fun createCcwDefinition(
        factory: WinRTActivationFactory,
        factoryInterfaces: List<WinRTInspectableInterfaceDefinition> = emptyList(),
    ): WinRTCcwDefinition =
        WinRTCcwDefinition(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(
                    interfaceId = IID.IActivationFactory,
                    methods = listOf(
                        WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Ptr) { args ->
                            factory.activateInstance().use { instance ->
                                PlatformAbi.writePointer(
                                    args[0] as RawAddress,
                                    PlatformAbi.fromRawComPtr(instance.getRefPointer()),
                                )
                            }
                            KnownHResults.S_OK.value
                        },
                    ),
                ),
            ) + factoryInterfaces,
            defaultInterfaceId = IID.IActivationFactory,
        )
}
