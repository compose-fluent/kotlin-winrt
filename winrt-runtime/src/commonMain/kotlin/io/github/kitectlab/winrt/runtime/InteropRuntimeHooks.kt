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
                    descriptor = getWeakReferenceDescriptor,
                ) { rawArgs ->
                    val resultOut = rawArgs[0] as NativePointer
                    NativeInterop.writePointer(resultOut, createManagedWeakReferencePointer(value))
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
                    descriptor = getUnmarshalClassDescriptor,
                ) { rawArgs ->
                    val requestedInterfaceId = NativeInterop.readGuid(rawArgs[0] as NativePointer)
                    val sourcePointer = rawArgs[1] as NativePointer
                    val destinationContext = rawArgs[2] as Int
                    val destinationContextPointer = rawArgs[3] as NativePointer
                    val flags = rawArgs[4] as Int
                    val resultOut = rawArgs[5] as NativePointer
                    NativeInterop.writeGuid(
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
                    descriptor = getMarshalSizeMaxDescriptor,
                ) { rawArgs ->
                    val requestedInterfaceId = NativeInterop.readGuid(rawArgs[0] as NativePointer)
                    val sourcePointer = rawArgs[1] as NativePointer
                    val destinationContext = rawArgs[2] as Int
                    val destinationContextPointer = rawArgs[3] as NativePointer
                    val flags = rawArgs[4] as Int
                    val resultOut = rawArgs[5] as NativePointer
                    NativeInterop.writeInt32(
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
                    descriptor = marshalInterfaceDescriptor,
                ) { rawArgs ->
                    FreeThreadedMarshalerSupport.proxy().marshalInterface(
                        streamPointer = rawArgs[0] as NativePointer,
                        interfaceId = NativeInterop.readGuid(rawArgs[1] as NativePointer),
                        interfacePointer = rawArgs[2] as NativePointer,
                        destinationContext = rawArgs[3] as Int,
                        destinationContextPointer = rawArgs[4] as NativePointer,
                        flags = rawArgs[5] as Int,
                    )
                    KnownHResults.S_OK.value
                },
                WinRtInspectableMethodDefinition(
                    descriptor = unmarshalInterfaceDescriptor,
                ) { rawArgs ->
                    val resultOut = rawArgs[2] as NativePointer
                    val resolved = FreeThreadedMarshalerSupport.proxy().unmarshalInterface(
                        streamPointer = rawArgs[0] as NativePointer,
                        interfaceId = NativeInterop.readGuid(rawArgs[1] as NativePointer),
                    )
                    NativeInterop.writePointer(resultOut, resolved?.useAndGetRef() ?: NativeInterop.nullPointer)
                    KnownHResults.S_OK.value
                },
                WinRtInspectableMethodDefinition(
                    descriptor = releaseMarshalDataDescriptor,
                ) { rawArgs ->
                    FreeThreadedMarshalerSupport.proxy().releaseMarshalData(rawArgs[0] as NativePointer)
                    KnownHResults.S_OK.value
                },
                WinRtInspectableMethodDefinition(
                    descriptor = disconnectObjectDescriptor,
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

    private fun createManagedWeakReferencePointer(target: Any): NativePointer {
        val state = ManagedWeakReferenceState(target)
        val host = WinRtInspectableComObject(
            interfaceDefinitions = listOf(
                WinRtInspectableInterfaceDefinition(
                    interfaceId = IID.IWeakReference,
                    baseKind = WinRtComInterfaceBaseKind.IUnknown,
                    methods = listOf(
                        WinRtInspectableMethodDefinition(
                            descriptor = resolveWeakReferenceDescriptor,
                        ) { rawArgs ->
                            val requestedInterfaceId = NativeInterop.readGuid(rawArgs[0] as NativePointer)
                            val resultOut = rawArgs[1] as NativePointer
                            NativeInterop.writePointer(
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

        fun resolve(interfaceId: Guid): NativePointer =
            weakReference.get()?.let { target ->
                ComWrappersSupport.createCCWForObject(target, interfaceId).useAndGetRef()
            } ?: NativeInterop.nullPointer
    }
}
