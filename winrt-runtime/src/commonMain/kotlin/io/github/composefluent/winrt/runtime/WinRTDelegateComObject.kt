package io.github.composefluent.winrt.runtime

internal class WinRTDelegateComObject(
    private val descriptor: WinRTDelegateDescriptor,
    private val callback: (List<Any?>) -> Any?,
    private val managedTarget: Any = callback,
    private val abiEntryPoint: RawAddress? = null,
    private val rawWordCallback: ComRawWordCallback? = null,
    private val definitionTemplate: WinRTCcwDefinition? = null,
) {
    // A delegate host currently has one lifecycle hook (the event-state release). Keep the
    // common path as a nullable callback instead of allocating a synchronized SnapshotList for
    // every delegate instance. If another owner ever needs a second hook, compose it here without
    // changing the host/lifetime contract.
    private var cleanupAction: (() -> Unit)? = null
    private val host = createHost()

    fun createReference(): WinRTDelegateReference =
        tryCreateReference()
            ?: throw WinRTObjectDisposedException("Delegate COM host is already closed.")

    fun tryCreateReference(): WinRTDelegateReference? =
        host.tryCreateReference(descriptor.interfaceId)?.let { reference ->
            WinRTDelegateReference(reference.comPtr, descriptor)
        }

    fun releaseManagedReference() {
        host.releaseManagedReference()
    }

    fun addCleanupAction(action: () -> Unit) {
        val previous = cleanupAction
        cleanupAction = if (previous == null) {
            action
        } else {
            {
                previous()
                action()
            }
        }
    }

    private fun invoke(rawArguments: List<Any?>): Int =
        WinRTDelegateInvocationSupport.invoke(descriptor, callback, rawArguments)

    private fun createHost(): WinRTInspectableComObject {
        val delegateReferenceInterfaceId = descriptor.referenceInterfaceId
        val definition = definitionTemplate ?: InteropRuntimeHooks.augmentInspectableDefinition(
            definition = WinRTCcwDefinition(
                interfaceDefinitions = listOf(
                    WinRTInspectableInterfaceDefinition(
                        interfaceId = descriptor.interfaceId,
                        baseKind = WinRTComInterfaceBaseKind.IUnknown,
                        methods = listOf(
                            abiEntryPoint?.let { entryPoint ->
                                WinRTInspectableMethodDefinition(
                                    signature = descriptor.functionSignature,
                                    abiEntryPoint = entryPoint,
                                )
                            } ?: rawWordCallback?.let { rawCallback ->
                                WinRTInspectableMethodDefinition(
                                    signature = descriptor.functionSignature,
                                    handler = ::invoke,
                                    rawWordHandler = rawCallback,
                                )
                            } ?: WinRTInspectableMethodDefinition(
                                descriptor.functionSignature,
                                ::invoke,
                            ),
                        ),
                    ),
                    createPropertyValueInterfaceDefinition(callback),
                    WinRTInspectableInterfaceDefinition(
                        interfaceId = delegateReferenceInterfaceId,
                        methods = listOf(
                            WinRTInspectableMethodDefinition(
                                signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                            ) { args ->
                                val valueOut = args.singleOrNull() as? RawAddress
                                    ?: throw IllegalStateException("IReference<TDelegate>.get_Value requires one out-argument.")
                                val delegatePointer = host.tryCreateDetachedReference(descriptor.interfaceId)
                                    ?: throw WinRTObjectDisposedException("Delegate COM host is already closed.")
                                PlatformAbi.writePointer(valueOut, delegatePointer)
                                KnownHResults.S_OK.value
                            },
                        ),
                    ),
                    WinRTInspectableInterfaceDefinition(
                        interfaceId = IID.IInspectable,
                        methods = emptyList(),
                    ),
                ),
                defaultInterfaceId = descriptor.interfaceId,
            ),
        )
        return WinRTInspectableComObject(
            interfaceDefinitions = definition.interfaceDefinitions,
            hiddenInterfaceDefinitions = definition.hiddenInterfaceDefinitions,
            defaultInterfaceId = definition.defaultInterfaceId,
            runtimeClassName = descriptor.runtimeClassName ?: definition.runtimeClassName,
            managedValue = managedTarget,
            cleanupAction = {
                cleanupAction?.invoke()
            },
            shapeCacheKey = definition,
        )
    }
}

/**
 * Type-level CCW metadata for closed delegates.
 *
 * The cache key contains only the closed WinMD descriptor and optional compiler-generated static
 * inbound entry point. No callback or managed target is retained by the definition, so every
 * delegate instance can safely share the augmented interface shape and vtables.
 */
internal object WinRTDelegateCcwTemplates {
    private val definitions = ConcurrentCacheMap<TemplateKey, WinRTCcwDefinition>()

    fun compatibility(descriptor: WinRTDelegateDescriptor): WinRTCcwDefinition =
        definitions.computeIfAbsent(TemplateKey(descriptor, abiEntryPoint = null)) { key ->
            createDefinition(key.descriptor, key.abiEntryPoint)
        }

    fun static(
        descriptor: WinRTDelegateDescriptor,
        abiEntryPoint: RawAddress,
    ): WinRTCcwDefinition =
        definitions.computeIfAbsent(TemplateKey(descriptor, abiEntryPoint)) { key ->
            createDefinition(key.descriptor, key.abiEntryPoint)
        }

    internal fun clearForTests() {
        definitions.clear()
    }

    private fun createDefinition(
        descriptor: WinRTDelegateDescriptor,
        abiEntryPoint: RawAddress?,
    ): WinRTCcwDefinition {
        val delegateReferenceInterfaceId = descriptor.referenceInterfaceId
        val invokeMethod = abiEntryPoint?.let { entryPoint ->
            WinRTInspectableMethodDefinition(
                signature = descriptor.functionSignature,
                abiEntryPoint = entryPoint,
            )
        } ?: WinRTInspectableMethodDefinition(
            signature = descriptor.functionSignature,
        ) { managedValue, rawArguments ->
            @Suppress("UNCHECKED_CAST")
            val callback = managedValue as? (List<Any?>) -> Any?
                ?: throw IllegalStateException("Delegate CCW host does not contain its callback.")
            WinRTDelegateInvocationSupport.invoke(descriptor, callback, rawArguments)
        }
        val definition = WinRTCcwDefinition(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(
                    interfaceId = descriptor.interfaceId,
                    baseKind = WinRTComInterfaceBaseKind.IUnknown,
                    methods = listOf(invokeMethod),
                ),
                createHostPropertyValueInterfaceDefinition(),
                WinRTInspectableInterfaceDefinition(
                    interfaceId = delegateReferenceInterfaceId,
                    methods = listOf(
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                            hostHandler = { host, _, args ->
                                val valueOut = args.singleOrNull() as? RawAddress
                                    ?: throw IllegalStateException("IReference<TDelegate>.get_Value requires one out-argument.")
                                val delegatePointer = host.tryCreateDetachedReference(descriptor.interfaceId)
                                    ?: throw WinRTObjectDisposedException("Delegate COM host is already closed.")
                                PlatformAbi.writePointer(valueOut, delegatePointer)
                                KnownHResults.S_OK.value
                            },
                        ),
                    ),
                ),
                WinRTInspectableInterfaceDefinition(
                    interfaceId = IID.IInspectable,
                    methods = emptyList(),
                ),
            ),
            defaultInterfaceId = descriptor.interfaceId,
            runtimeClassName = descriptor.runtimeClassName,
        )
        return InteropRuntimeHooks.augmentInspectableDefinition(definition)
    }

    private data class TemplateKey(
        val descriptor: WinRTDelegateDescriptor,
        val abiEntryPoint: RawAddress?,
    )
}

internal object WinRTDelegateInvocationSupport {
    fun invoke(
        descriptor: WinRTDelegateDescriptor,
        callback: (List<Any?>) -> Any?,
        rawArguments: List<Any?>,
    ): Int =
        try {
            val hasReturnValue = descriptor.returnKind != WinRTDelegateValueKind.UNIT
            val abiArguments = if (hasReturnValue) rawArguments.dropLast(1) else rawArguments
            val returnValue = callback(
                WinRTDelegateAbiMarshaller.decodeArguments(
                    descriptor = descriptor,
                    abiArguments = abiArguments,
                ),
            )
            if (hasReturnValue) {
                val resultOut = rawArguments.last() as? RawAddress
                    ?: error("Non-unit delegate invocation requires a trailing ABI return buffer.")
                WinRTDelegateAbiMarshaller.writeReturnValue(descriptor, returnValue, resultOut)
            }
            KnownHResults.S_OK.value
        } catch (error: Throwable) {
            if (FeatureSwitches.traceCcw) {
                println("winrt-delegate: Invoke failed for ${descriptor.runtimeClassName ?: descriptor.interfaceId}: ${error::class.qualifiedName}: ${error.message}")
                error.printStackTrace()
            }
            platformSetErrorInfo(error)
            platformHResultFromThrowable(error).value
        }
}

class WinRTDelegateReference internal constructor(
    comPtr: ComPtr,
    val descriptor: WinRTDelegateDescriptor,
) : ComObjectReference(comPtr) {
    internal constructor(
        pointer: RawAddress,
        descriptor: WinRTDelegateDescriptor,
    ) : this(
        ComPtr.create(pointer.asRawComPtr(), descriptor.interfaceId),
        descriptor,
    )

    companion object {
        fun fromOwnedReference(
            reference: IUnknownReference,
            descriptor: WinRTDelegateDescriptor,
        ): WinRTDelegateReference = WinRTDelegateReference(reference.comPtr, descriptor)

        fun fromAbi(
            pointer: RawAddress,
            descriptor: WinRTDelegateDescriptor,
        ): WinRTDelegateReference? =
            if (PlatformAbi.isNull(pointer)) {
                null
            } else {
                WinRTDelegateReference(pointer, descriptor)
            }
    }

    fun invokeAbi(arguments: List<Any?>): HResult {
        WinRTDelegateAbiMarshaller.encodeArgumentsLease(descriptor, arguments).use { encodedArguments ->
            val signature = descriptor.functionSignature
            val words = ComAbiWord.fromDynamicArgs(signature.explicitParameterKinds, encodedArguments.values)
            return HResult(
                comPtr.invokeGeneric(
                    slot = WinRTDelegateVftblSlots.Invoke,
                    signature = signature,
                    args = words,
                ),
            )
        }
    }

    fun invoke(arguments: List<Any?>): Any? {
        require(arguments.size == descriptor.parameterKinds.size) {
            "Argument count ${arguments.size} must match delegate parameter count ${descriptor.parameterKinds.size}."
        }
        return if (descriptor.returnKind == WinRTDelegateValueKind.UNIT) {
            invokeAbi(arguments).requireSuccess()
            Unit
        } else {
            PlatformAbi.confinedScope().use { scope ->
                WinRTDelegateAbiMarshaller.encodeArgumentsLease(descriptor, arguments).use { encodedArguments ->
                    val resultOut = WinRTDelegateAbiMarshaller.allocateReturnOut(descriptor, scope)
                    val signature = descriptor.functionSignature
                    val abiArguments = encodedArguments.values + resultOut
                    val words = ComAbiWord.fromDynamicArgs(signature.explicitParameterKinds, abiArguments)
                    val hr = comPtr.invokeGeneric(
                        slot = WinRTDelegateVftblSlots.Invoke,
                        signature = signature,
                        args = words,
                    )
                    WinRTPlatformApi.checkSucceededRaw(hr)
                    WinRTDelegateAbiMarshaller.decodeReturnValue(descriptor, resultOut)
                }
            }
        }
    }
}
