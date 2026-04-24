package io.github.kitectlab.winrt.runtime

internal object InteropRuntimeHooks {
    fun augmentInspectableDefinition(
        value: Any,
        definition: WinRtCcwDefinition,
    ): WinRtCcwDefinition {
        val existingInterfaceIds = definition.interfaceDefinitions.mapTo(linkedSetOf()) { it.interfaceId }
        val augmentedInterfaces = buildList {
            addAll(definition.interfaceDefinitions)
            if (IID.IWeakReferenceSource !in existingInterfaceIds) {
                add(createWeakReferenceSourceInterfaceDefinition(value))
            }
            if (IID.IMarshal !in existingInterfaceIds) {
                add(createMarshalInterfaceDefinition())
            }
            if (IID.IAgileObject !in existingInterfaceIds) {
                add(createAgileObjectInterfaceDefinition())
            }
        }
        return definition.copy(interfaceDefinitions = augmentedInterfaces)
    }

    private fun createWeakReferenceSourceInterfaceDefinition(
        value: Any,
    ): WinRtInspectableInterfaceDefinition =
        WinRtInspectableInterfaceDefinition(
            interfaceId = IID.IWeakReferenceSource,
            baseKind = WinRtComInterfaceBaseKind.IUnknown,
            methods = listOf(
                WinRtInspectableMethodDefinition(
                    signature = ComMethodSignatures.HResult_Ptr,
                ) { rawArgs ->
                    val resultOut = rawArgs[0] as RawAddress
                    PlatformAbi.writePointer(resultOut, createManagedWeakReferencePointer(value))
                    KnownHResults.S_OK.value
                },
            ),
        )

    private fun createMarshalInterfaceDefinition(): WinRtInspectableInterfaceDefinition =
        WinRtInspectableInterfaceDefinition(
            interfaceId = IID.IMarshal,
            baseKind = WinRtComInterfaceBaseKind.IUnknown,
            methods = listOf(
                WinRtInspectableMethodDefinition(
                    signature = ComMethodSignatures.HResult_Ptr_Ptr_Int32_Ptr_Int32_Ptr,
                ) { rawArgs ->
                    val requestedInterfaceId = PlatformAbi.readGuid(rawArgs[0] as RawAddress)
                    val sourcePointer = rawArgs[1] as RawAddress
                    val destinationContext = rawArgs[2] as Int
                    val destinationContextPointer = rawArgs[3] as RawAddress
                    val flags = rawArgs[4] as Int
                    val resultOut = rawArgs[5] as RawAddress
                    PlatformAbi.writeGuid(
                        resultOut,
                        FreeThreadedMarshalerSupport.proxy().getUnmarshalClass(
                            interfaceId = requestedInterfaceId,
                            sourcePointer = sourcePointer,
                            destinationContext = destinationContext,
                            destinationContextPointer = destinationContextPointer,
                            flags = flags,
                        ),
                    )
                    KnownHResults.S_OK.value
                },
                WinRtInspectableMethodDefinition(
                    signature = ComMethodSignatures.HResult_Ptr_Ptr_Int32_Ptr_Int32_Ptr,
                ) { rawArgs ->
                    val requestedInterfaceId = PlatformAbi.readGuid(rawArgs[0] as RawAddress)
                    val sourcePointer = rawArgs[1] as RawAddress
                    val destinationContext = rawArgs[2] as Int
                    val destinationContextPointer = rawArgs[3] as RawAddress
                    val flags = rawArgs[4] as Int
                    val resultOut = rawArgs[5] as RawAddress
                    PlatformAbi.writeInt32(
                        resultOut,
                        FreeThreadedMarshalerSupport.proxy().getMarshalSizeMax(
                            interfaceId = requestedInterfaceId,
                            sourcePointer = sourcePointer,
                            destinationContext = destinationContext,
                            destinationContextPointer = destinationContextPointer,
                            flags = flags,
                        ).toInt(),
                    )
                    KnownHResults.S_OK.value
                },
                WinRtInspectableMethodDefinition(
                    signature = ComMethodSignatures.HResult_Ptr_Ptr_Ptr_Int32_Ptr_Int32,
                ) { rawArgs ->
                    FreeThreadedMarshalerSupport.proxy().marshalInterface(
                        streamPointer = rawArgs[0] as RawAddress,
                        interfaceId = PlatformAbi.readGuid(rawArgs[1] as RawAddress),
                        interfacePointer = rawArgs[2] as RawAddress,
                        destinationContext = rawArgs[3] as Int,
                        destinationContextPointer = rawArgs[4] as RawAddress,
                        flags = rawArgs[5] as Int,
                    )
                    KnownHResults.S_OK.value
                },
                WinRtInspectableMethodDefinition(
                    signature = ComMethodSignatures.HResult_Ptr_Ptr_Ptr,
                ) { rawArgs ->
                    val resultOut = rawArgs[2] as RawAddress
                    val resolved = FreeThreadedMarshalerSupport.proxy().unmarshalInterface(
                        streamPointer = rawArgs[0] as RawAddress,
                        interfaceId = PlatformAbi.readGuid(rawArgs[1] as RawAddress),
                    )
                    PlatformAbi.writePointer(resultOut, resolved?.useAndGetRef() ?: PlatformAbi.nullPointer)
                    KnownHResults.S_OK.value
                },
                WinRtInspectableMethodDefinition(
                    signature = ComMethodSignatures.HResult_Ptr,
                ) { rawArgs ->
                    FreeThreadedMarshalerSupport.proxy().releaseMarshalData(rawArgs[0] as RawAddress)
                    KnownHResults.S_OK.value
                },
                WinRtInspectableMethodDefinition(
                    signature = ComMethodSignatures.HResult_Int32,
                ) { rawArgs ->
                    FreeThreadedMarshalerSupport.proxy().disconnectObject(rawArgs[0] as Int)
                    KnownHResults.S_OK.value
                },
            ),
        )

    private fun createAgileObjectInterfaceDefinition(): WinRtInspectableInterfaceDefinition =
        WinRtInspectableInterfaceDefinition(
            interfaceId = IID.IAgileObject,
            baseKind = WinRtComInterfaceBaseKind.IUnknown,
            methods = emptyList(),
        )

    private fun createManagedWeakReferencePointer(target: Any): RawAddress {
        val state = ManagedWeakReferenceState(target)
        val host = WinRtInspectableComObject(
            interfaceDefinitions = listOf(
                WinRtInspectableInterfaceDefinition(
                    interfaceId = IID.IWeakReference,
                    baseKind = WinRtComInterfaceBaseKind.IUnknown,
                    methods = listOf(
                        WinRtInspectableMethodDefinition(
                            signature = ComMethodSignatures.HResult_Ptr_Ptr,
                        ) { rawArgs ->
                            val requestedInterfaceId = PlatformAbi.readGuid(rawArgs[0] as RawAddress)
                            val resultOut = rawArgs[1] as RawAddress
                            PlatformAbi.writePointer(
                                resultOut,
                                state.resolve(requestedInterfaceId),
                            )
                            KnownHResults.S_OK.value
                        },
                    ),
                ),
            ),
            managedValue = state,
        )
        return host.detachReference(IID.IWeakReference)
    }

    private class ManagedWeakReferenceState(
        target: Any,
    ) {
        private val weakReference = PlatformManagedWeakReference(target)

        fun resolve(interfaceId: Guid): RawAddress =
            weakReference.get()?.let { target ->
                ComWrappersSupport.createCCWForObject(target, interfaceId).useAndGetRef()
            } ?: PlatformAbi.nullPointer
    }
}
