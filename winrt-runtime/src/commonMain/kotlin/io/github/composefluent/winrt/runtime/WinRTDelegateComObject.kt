package io.github.composefluent.winrt.runtime

internal class WinRTDelegateComObject(
    private val descriptor: WinRTDelegateDescriptor,
    private val callback: (List<Any?>) -> Any?,
) {
    private val cleanupActions = SnapshotList<() -> Unit>()
    private val host = createHost()

    fun createReference(): WinRTDelegateReference {
        val reference = host.createReference(descriptor.interfaceId)
        return WinRTDelegateReference(reference.comPtr, descriptor)
    }

    fun releaseManagedReference() {
        host.releaseManagedReference()
    }

    fun addCleanupAction(action: () -> Unit) {
        cleanupActions.add(action)
    }

    private fun invoke(rawArguments: List<Any?>): Int =
        WinRTDelegateInvocationSupport.invoke(descriptor, callback, rawArguments)

    private fun createHost(): WinRTInspectableComObject {
        val delegateReferenceInterfaceId = descriptor.referenceInterfaceId()
        val definition = InteropRuntimeHooks.augmentInspectableDefinition(
            value = callback,
            definition = WinRTCcwDefinition(
                interfaceDefinitions = listOf(
                    WinRTInspectableInterfaceDefinition(
                        interfaceId = descriptor.interfaceId,
                        baseKind = WinRTComInterfaceBaseKind.IUnknown,
                        methods = listOf(
                            WinRTInspectableMethodDefinition(WinRTDelegateAbiMarshaller.functionSignature(descriptor)) { args ->
                                invoke(args)
                            },
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
                                val delegateReference = host.createReference(descriptor.interfaceId)
                                PlatformAbi.writePointer(valueOut, delegateReference.pointer.asRawAddress())
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
            cleanupAction = {
                cleanupActions.toList().forEach { action -> action() }
            },
        )
    }
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
            val signature = WinRTDelegateAbiMarshaller.functionSignature(descriptor)
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
                    val signature = WinRTDelegateAbiMarshaller.functionSignature(descriptor)
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
