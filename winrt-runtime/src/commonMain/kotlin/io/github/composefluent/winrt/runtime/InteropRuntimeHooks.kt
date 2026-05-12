package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal object InteropRuntimeHooks {
    fun augmentInspectableDefinition(
        value: Any,
        definition: WinRtCcwDefinition,
    ): WinRtCcwDefinition {
        val existingInterfaceIds = definition.interfaceDefinitions.mapTo(linkedSetOf()) { it.interfaceId }
        val authoredInterfaces = definition.interfaceDefinitions.filterNot { it.interfaceId in cswinrtAppendedInterfaceIds }
        val augmentedInterfaces = buildList {
            addAll(authoredInterfaces)
            add(createStringableInterfaceDefinition(value))
            add(createWeakReferenceSourceInterfaceDefinition(value))
            if (IID.IMarshal !in existingInterfaceIds) {
                add(createMarshalInterfaceDefinition())
            } else {
                definition.interfaceDefinitions.firstOrNull { it.interfaceId == IID.IMarshal }?.let(::add)
            }
            add(createAgileObjectInterfaceDefinition())
            add(createInspectableInterfaceDefinition())
            add(createUnknownInterfaceDefinition())
        }
        return definition.copy(
            interfaceDefinitions = augmentedInterfaces,
            hiddenInterfaceDefinitions = definition.hiddenInterfaceDefinitions + createReferenceTrackerTargetInterfaceDefinition(),
        )
    }

    private val cswinrtAppendedInterfaceIds = setOf(
        IID.IStringable,
        IID.IWeakReferenceSource,
        IID.IReferenceTrackerTarget,
        IID.IMarshal,
        IID.IAgileObject,
        IID.IInspectable,
        IID.IUnknown,
    )

    private fun createReferenceTrackerTargetInterfaceDefinition(): WinRtInspectableInterfaceDefinition {
        val state = ReferenceTrackerTargetState()
        return WinRtInspectableInterfaceDefinition(
            interfaceId = IID.IReferenceTrackerTarget,
            baseKind = WinRtComInterfaceBaseKind.IUnknown,
            methods = listOf(
                WinRtInspectableMethodDefinition(ComMethodSignatures.HResult) { state.addRefFromReferenceTracker() },
                WinRtInspectableMethodDefinition(ComMethodSignatures.HResult) { state.releaseFromReferenceTracker() },
                WinRtInspectableMethodDefinition(ComMethodSignatures.HResult) { KnownHResults.S_OK.value },
                WinRtInspectableMethodDefinition(ComMethodSignatures.HResult) { KnownHResults.S_OK.value },
            ),
        )
    }

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

    private fun createInspectableInterfaceDefinition(): WinRtInspectableInterfaceDefinition =
        WinRtInspectableInterfaceDefinition(
            interfaceId = IID.IInspectable,
            methods = emptyList(),
        )

    private fun createUnknownInterfaceDefinition(): WinRtInspectableInterfaceDefinition =
        WinRtInspectableInterfaceDefinition(
            interfaceId = IID.IUnknown,
            baseKind = WinRtComInterfaceBaseKind.IUnknown,
            methods = emptyList(),
        )

    private fun createStringableInterfaceDefinition(value: Any): WinRtInspectableInterfaceDefinition =
        WinRtInspectableInterfaceDefinition(
            interfaceId = IID.IStringable,
            methods = listOf(
                WinRtInspectableMethodDefinition(
                    signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                ) { rawArgs ->
                    PlatformAbi.writePointer(rawArgs[0] as RawAddress, HString.create(value.toString()).handle)
                    KnownHResults.S_OK.value
                },
            ),
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
