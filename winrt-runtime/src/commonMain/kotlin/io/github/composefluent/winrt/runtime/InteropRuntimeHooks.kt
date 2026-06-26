package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal object InteropRuntimeHooks {
    fun augmentInspectableDefinition(
        value: Any,
        definition: WinRTCcwDefinition,
    ): WinRTCcwDefinition {
        val existingInterfaceIds = definition.interfaceDefinitions.mapTo(linkedSetOf()) { it.interfaceId }
        val authoredInterfaces = definition.interfaceDefinitions.filterNot {
            it.interfaceId in referenceAppendedInterfaceIds && it.interfaceId != IID.IMarshal
        }
        val augmentedInterfaces = buildList {
            addAll(authoredInterfaces)
            add(createStringableInterfaceDefinition(value))
            XamlSystemProjectionRuntimeHooks.defaultCustomPropertyProviderInterfaceDefinition(
                value = value,
                existingInterfaceIds = existingInterfaceIds,
            )?.let(::add)
            add(createWeakReferenceSourceInterfaceDefinition(value))
            if (IID.IMarshal !in existingInterfaceIds) {
                add(createMarshalInterfaceDefinition())
            }
            add(createAgileObjectInterfaceDefinition())
            add(createInspectableInterfaceDefinition())
            add(createUnknownInterfaceDefinition())
        }
        return definition.copy(
            interfaceDefinitions = augmentedInterfaces,
            hiddenInterfaceDefinitions = definition.hiddenInterfaceDefinitions +
                createReferenceTrackerTargetInterfaceDefinition() +
                createReferenceTrackerExtensionInterfaceDefinition(),
        )
    }

    private val referenceAppendedInterfaceIds = setOf(
        IID.IStringable,
        IID.IWeakReferenceSource,
        IID.IReferenceTrackerTarget,
        IID.IReferenceTrackerExtension,
        IID.IMarshal,
        IID.IAgileObject,
        IID.IInspectable,
        IID.IUnknown,
    )

    private fun createReferenceTrackerTargetInterfaceDefinition(): WinRTInspectableInterfaceDefinition {
        val state = ReferenceTrackerTargetState()
        return WinRTInspectableInterfaceDefinition(
            interfaceId = IID.IReferenceTrackerTarget,
            baseKind = WinRTComInterfaceBaseKind.IUnknown,
            methods = listOf(
                WinRTInspectableMethodDefinition(ComMethodSignatures.HResult) { state.addRefFromReferenceTracker() },
                WinRTInspectableMethodDefinition(ComMethodSignatures.HResult) { state.releaseFromReferenceTracker() },
                WinRTInspectableMethodDefinition(ComMethodSignatures.HResult) { KnownHResults.S_OK.value },
                WinRTInspectableMethodDefinition(ComMethodSignatures.HResult) { KnownHResults.S_OK.value },
            ),
        )
    }

    private fun createReferenceTrackerExtensionInterfaceDefinition(): WinRTInspectableInterfaceDefinition =
        WinRTInspectableInterfaceDefinition(
            interfaceId = IID.IReferenceTrackerExtension,
            baseKind = WinRTComInterfaceBaseKind.IUnknown,
            methods = emptyList(),
        )

    @OptIn(ExperimentalAtomicApi::class)
    private class ReferenceTrackerTargetState {
        private val trackerReferences = AtomicInt(0)

        fun addRefFromReferenceTracker(): Int {
            while (true) {
                val current = trackerReferences.load()
                val next = if (current == Int.MAX_VALUE) current else current + 1
                if (trackerReferences.compareAndSet(current, next)) {
                    return next
                }
            }
        }

        fun releaseFromReferenceTracker(): Int {
            while (true) {
                val current = trackerReferences.load()
                val next = if (current <= 0) 0 else current - 1
                if (trackerReferences.compareAndSet(current, next)) {
                    return next
                }
            }
        }
    }

    private fun createWeakReferenceSourceInterfaceDefinition(
        value: Any,
    ): WinRTInspectableInterfaceDefinition =
        WinRTInspectableInterfaceDefinition(
            interfaceId = IID.IWeakReferenceSource,
            baseKind = WinRTComInterfaceBaseKind.IUnknown,
            methods = listOf(
                WinRTInspectableMethodDefinition(
                    signature = ComMethodSignatures.HResult_Ptr,
                ) { rawArgs ->
                    val resultOut = rawArgs[0] as RawAddress
                    PlatformAbi.writePointer(resultOut, createManagedWeakReferencePointer(value))
                    KnownHResults.S_OK.value
                },
            ),
        )

    private fun createMarshalInterfaceDefinition(): WinRTInspectableInterfaceDefinition =
        WinRTInspectableInterfaceDefinition(
            interfaceId = IID.IMarshal,
            baseKind = WinRTComInterfaceBaseKind.IUnknown,
            methods = listOf(
                WinRTInspectableMethodDefinition(
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
                WinRTInspectableMethodDefinition(
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
                WinRTInspectableMethodDefinition(
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
                WinRTInspectableMethodDefinition(
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
                WinRTInspectableMethodDefinition(
                    signature = ComMethodSignatures.HResult_Ptr,
                ) { rawArgs ->
                    FreeThreadedMarshalerSupport.proxy().releaseMarshalData(rawArgs[0] as RawAddress)
                    KnownHResults.S_OK.value
                },
                WinRTInspectableMethodDefinition(
                    signature = ComMethodSignatures.HResult_Int32,
                ) { rawArgs ->
                    FreeThreadedMarshalerSupport.proxy().disconnectObject(rawArgs[0] as Int)
                    KnownHResults.S_OK.value
                },
            ),
        )

    private fun createAgileObjectInterfaceDefinition(): WinRTInspectableInterfaceDefinition =
        WinRTInspectableInterfaceDefinition(
            interfaceId = IID.IAgileObject,
            baseKind = WinRTComInterfaceBaseKind.IUnknown,
            methods = emptyList(),
        )

    private fun createInspectableInterfaceDefinition(): WinRTInspectableInterfaceDefinition =
        WinRTInspectableInterfaceDefinition(
            interfaceId = IID.IInspectable,
            methods = emptyList(),
        )

    private fun createUnknownInterfaceDefinition(): WinRTInspectableInterfaceDefinition =
        WinRTInspectableInterfaceDefinition(
            interfaceId = IID.IUnknown,
            baseKind = WinRTComInterfaceBaseKind.IUnknown,
            methods = emptyList(),
        )

    private fun createStringableInterfaceDefinition(value: Any): WinRTInspectableInterfaceDefinition =
        WinRTInspectableInterfaceDefinition(
            interfaceId = IID.IStringable,
            methods = listOf(
                WinRTInspectableMethodDefinition(
                    signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                ) { rawArgs ->
                    PlatformAbi.writePointer(rawArgs[0] as RawAddress, HString.create(value.toString()).handle)
                    KnownHResults.S_OK.value
                },
            ),
        )

    private fun createManagedWeakReferencePointer(target: Any): RawAddress {
        val state = ManagedWeakReferenceState(target)
        val host = WinRTInspectableComObject(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(
                    interfaceId = IID.IWeakReference,
                    baseKind = WinRTComInterfaceBaseKind.IUnknown,
                    methods = listOf(
                        WinRTInspectableMethodDefinition(
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
