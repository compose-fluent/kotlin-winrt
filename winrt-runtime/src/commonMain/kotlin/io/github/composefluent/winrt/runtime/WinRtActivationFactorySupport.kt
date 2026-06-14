package io.github.composefluent.winrt.runtime

fun interface WinRtActivationFactory {
    fun activateInstance(): ComObjectReference
}

internal object WinRtActivationFactorySupport {
    fun createCcwDefinition(
        factory: WinRtActivationFactory,
        factoryInterfaces: List<WinRtInspectableInterfaceDefinition> = emptyList(),
    ): WinRtCcwDefinition =
        WinRtCcwDefinition(
            interfaceDefinitions = listOf(
                WinRtInspectableInterfaceDefinition(
                    interfaceId = IID.IActivationFactory,
                    methods = listOf(
                        WinRtInspectableMethodDefinition(ComMethodSignatures.HResult_Ptr) { args ->
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
