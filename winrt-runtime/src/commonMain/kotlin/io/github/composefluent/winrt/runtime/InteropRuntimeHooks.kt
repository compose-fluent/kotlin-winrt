package io.github.composefluent.winrt.runtime

internal object InteropRuntimeHooks {
    private val augmentedDefinitionCache =
        WeakKeyStateMap<WinRTCcwDefinition, CachedAugmentedDefinition>()
    @kotlin.concurrent.Volatile
    private var lastAugmentedBase: WinRTCcwDefinition? = null
    @kotlin.concurrent.Volatile
    private var lastAugmentedConfiguration: AugmentationConfiguration? = null
    @kotlin.concurrent.Volatile
    private var lastAugmentedDefinition: WinRTCcwDefinition? = null

    fun augmentInspectableDefinition(
        definition: WinRTCcwDefinition,
    ): WinRTCcwDefinition {
        val customPropertyProvider =
            XamlSystemProjectionRuntimeHooks.defaultCustomPropertyProviderInterfaceDefinition(
                existingInterfaceIds = definition.interfaceDefinitions.mapTo(linkedSetOf()) { it.interfaceId },
            )
        val configuration = AugmentationConfiguration.current(customPropertyProvider != null)
        if (definition === lastAugmentedBase && configuration == lastAugmentedConfiguration) {
            lastAugmentedDefinition?.let { return it }
        }
        val cacheable =
            definition.queryInterfaceFallback == null &&
                (definition.interfaceDefinitions + definition.hiddenInterfaceDefinitions)
                    .all(::isHostValueHandler)
        if (cacheable) {
            augmentedDefinitionCache[definition]
                ?.takeIf { it.configuration == configuration }
                ?.let { return rememberAugmented(definition, configuration, it.definition) }
            return augmentInspectableDefinitionUncached(
                definition = definition,
                customPropertyProvider = customPropertyProvider,
            ).also { augmented ->
                augmentedDefinitionCache.remove(definition)
                augmentedDefinitionCache.getOrPut(definition) {
                    CachedAugmentedDefinition(configuration, augmented)
                }
            }.let { rememberAugmented(definition, configuration, it) }
        }
        return rememberAugmented(
            definition,
            configuration,
            augmentInspectableDefinitionUncached(
            definition = definition,
            customPropertyProvider = customPropertyProvider,
            ),
        )
    }

    private fun rememberAugmented(
        base: WinRTCcwDefinition,
        configuration: AugmentationConfiguration,
        definition: WinRTCcwDefinition,
    ): WinRTCcwDefinition {
        lastAugmentedBase = base
        lastAugmentedConfiguration = configuration
        lastAugmentedDefinition = definition
        return definition
    }

    internal fun clearForTests() {
        augmentedDefinitionCache.clear()
        lastAugmentedBase = null
        lastAugmentedConfiguration = null
        lastAugmentedDefinition = null
    }

    private fun augmentInspectableDefinitionUncached(
        definition: WinRTCcwDefinition,
        customPropertyProvider: WinRTInspectableInterfaceDefinition?,
    ): WinRTCcwDefinition {
        val existingInterfaceIds = definition.interfaceDefinitions.mapTo(linkedSetOf()) { it.interfaceId }
        val authoredInterfaces = definition.interfaceDefinitions.filterNot {
            it.interfaceId in referenceAppendedInterfaceIds && it.interfaceId != IID.IMarshal
        }
        val augmentedInterfaces = buildList {
            addAll(authoredInterfaces)
            add(stringableInterfaceDefinition)
            customPropertyProvider?.let(::add)
            add(weakReferenceSourceInterfaceDefinition)
            if (IID.IMarshal !in existingInterfaceIds) {
                add(marshalInterfaceDefinition)
            }
            add(agileObjectInterfaceDefinition)
            add(inspectableInterfaceDefinition)
            add(unknownInterfaceDefinition)
        }
        return definition.copy(
            interfaceDefinitions = augmentedInterfaces,
            hiddenInterfaceDefinitions = definition.hiddenInterfaceDefinitions +
                referenceTrackerTargetInterfaceDefinition +
                referenceTrackerExtensionInterfaceDefinition,
        )
    }

    private fun isHostValueHandler(definition: WinRTInspectableInterfaceDefinition): Boolean =
        definition.methods.all { method ->
            method.readsHostManagedValue || method.abiEntryPoint != null || method.rawWordHandler != null
        }

    private data class CachedAugmentedDefinition(
        val configuration: AugmentationConfiguration,
        val definition: WinRTCcwDefinition,
    )

    private data class AugmentationConfiguration(
        val defaultCustomTypeMappings: Boolean,
        val customPropertyProviderSupport: Boolean,
        val customPropertyProviderIncluded: Boolean,
    ) {
        companion object {
            fun current(customPropertyProviderIncluded: Boolean): AugmentationConfiguration =
                AugmentationConfiguration(
                defaultCustomTypeMappings = FeatureSwitches.enableDefaultCustomTypeMappings,
                customPropertyProviderSupport = FeatureSwitches.enableICustomPropertyProviderSupport,
                customPropertyProviderIncluded = customPropertyProviderIncluded,
            )
        }
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

    // The actual tracker counter is owned by ManagedComHostState and dispatched by
    // WinRTInspectableComObject.invokeMethod. This definition is immutable shape metadata.
    private val referenceTrackerTargetInterfaceDefinition =
        WinRTInspectableInterfaceDefinition(
            interfaceId = IID.IReferenceTrackerTarget,
            baseKind = WinRTComInterfaceBaseKind.IUnknown,
            methods = listOf(
                WinRTInspectableMethodDefinition(ComMethodSignatures.HResult) { _, _ -> KnownHResults.S_OK.value },
                WinRTInspectableMethodDefinition(ComMethodSignatures.HResult) { _, _ -> KnownHResults.S_OK.value },
                WinRTInspectableMethodDefinition(ComMethodSignatures.HResult) { _, _ -> KnownHResults.S_OK.value },
                WinRTInspectableMethodDefinition(ComMethodSignatures.HResult) { _, _ -> KnownHResults.S_OK.value },
            ),
        )

    private val referenceTrackerExtensionInterfaceDefinition =
        WinRTInspectableInterfaceDefinition(
            interfaceId = IID.IReferenceTrackerExtension,
            baseKind = WinRTComInterfaceBaseKind.IUnknown,
            methods = emptyList(),
        )

    private val weakReferenceSourceInterfaceDefinition =
        WinRTInspectableInterfaceDefinition(
            interfaceId = IID.IWeakReferenceSource,
            baseKind = WinRTComInterfaceBaseKind.IUnknown,
            methods = listOf(
                WinRTInspectableMethodDefinition(
                    signature = ComMethodSignatures.HResult_Ptr,
                ) { managedValue, rawArgs ->
                    val resultOut = rawArgs[0] as RawAddress
                    PlatformAbi.writePointer(
                        resultOut,
                        createManagedWeakReferencePointer(requireNotNull(managedValue)),
                    )
                    KnownHResults.S_OK.value
                },
            ),
        )

    private val managedWeakReferenceDefinition =
        WinRTCcwDefinition(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(
                    interfaceId = IID.IWeakReference,
                    baseKind = WinRTComInterfaceBaseKind.IUnknown,
                    methods = listOf(
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignatures.HResult_Ptr_Ptr,
                        ) { managedValue, rawArgs ->
                            val state = requireNotNull(managedValue) as ManagedWeakReferenceState
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
            defaultInterfaceId = IID.IWeakReference,
        )

    private val marshalInterfaceDefinition =
        WinRTInspectableInterfaceDefinition(
            interfaceId = IID.IMarshal,
            baseKind = WinRTComInterfaceBaseKind.IUnknown,
            methods = listOf(
                WinRTInspectableMethodDefinition(
                    signature = ComMethodSignatures.HResult_Ptr_Ptr_Int32_Ptr_Int32_Ptr,
                ) { _, rawArgs ->
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
                ) { _, rawArgs ->
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
                ) { _, rawArgs ->
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
                ) { _, rawArgs ->
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
                ) { _, rawArgs ->
                    FreeThreadedMarshalerSupport.proxy().releaseMarshalData(rawArgs[0] as RawAddress)
                    KnownHResults.S_OK.value
                },
                WinRTInspectableMethodDefinition(
                    signature = ComMethodSignatures.HResult_Int32,
                ) { _, rawArgs ->
                    FreeThreadedMarshalerSupport.proxy().disconnectObject(rawArgs[0] as Int)
                    KnownHResults.S_OK.value
                },
            ),
        )

    private val agileObjectInterfaceDefinition =
        WinRTInspectableInterfaceDefinition(
            interfaceId = IID.IAgileObject,
            baseKind = WinRTComInterfaceBaseKind.IUnknown,
            methods = emptyList(),
        )

    private val inspectableInterfaceDefinition =
        WinRTInspectableInterfaceDefinition(
            interfaceId = IID.IInspectable,
            methods = emptyList(),
        )

    private val unknownInterfaceDefinition =
        WinRTInspectableInterfaceDefinition(
            interfaceId = IID.IUnknown,
            baseKind = WinRTComInterfaceBaseKind.IUnknown,
            methods = emptyList(),
        )

    private val stringableInterfaceDefinition =
        WinRTInspectableInterfaceDefinition(
            interfaceId = IID.IStringable,
            methods = listOf(
                WinRTInspectableMethodDefinition(
                    signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                ) { managedValue, rawArgs ->
                    PlatformAbi.writePointer(
                        rawArgs[0] as RawAddress,
                        HString.create(requireNotNull(managedValue).toString()).handle,
                    )
                    KnownHResults.S_OK.value
                },
            ),
        )

    private fun createManagedWeakReferencePointer(target: Any): RawAddress {
        val state = ManagedWeakReferenceState(target)
        val host = WinRTInspectableComObject(
            interfaceDefinitions = managedWeakReferenceDefinition.interfaceDefinitions,
            defaultInterfaceId = managedWeakReferenceDefinition.defaultInterfaceId,
            managedValue = state,
            shapeCacheKey = managedWeakReferenceDefinition,
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
