package io.github.composefluent.winrt.runtime

internal class WinRtDelegateComObject(
    private val descriptor: WinRtDelegateDescriptor,
    private val callback: (List<Any?>) -> Any?,
) {
    private val host = createHost()

    fun createReference(): WinRtDelegateReference {
        val reference = host.createReference(descriptor.interfaceId)
        return WinRtDelegateReference(reference.comPtr, descriptor)
    }

    fun releaseManagedReference() {
        host.releaseManagedReference()
    }

    private fun invoke(rawArguments: List<Any?>): Int =
        try {
            val hasReturnValue = descriptor.returnKind != WinRtDelegateValueKind.UNIT
            val abiArguments = if (hasReturnValue) rawArguments.dropLast(1) else rawArguments
            val returnValue = callback(
                WinRtDelegateAbiMarshaller.decodeArguments(
                    descriptor = descriptor,
                    abiArguments = abiArguments,
                ),
            )
            if (hasReturnValue) {
                val resultOut = rawArguments.last() as? RawAddress
                    ?: error("Non-unit delegate invocation requires a trailing ABI return buffer.")
                WinRtDelegateAbiMarshaller.writeReturnValue(descriptor, returnValue, resultOut)
            }
            KnownHResults.S_OK.value
        } catch (error: Throwable) {
            if (FeatureSwitches.traceCcw) {
                println("winrt-delegate: Invoke failed for ${descriptor.runtimeClassName ?: descriptor.interfaceId}: ${error::class.qualifiedName}: ${error.message}")
                error.printStackTrace()
            }
            if (descriptor.isDispatcherQueueHandler()) {
                ExceptionHelpers.reportUnhandledError(error)
                return KnownHResults.S_OK.value
            }
            platformSetErrorInfo(error)
            platformHResultFromThrowable(error).value
        }

    private fun createHost(): WinRtInspectableComObject {
        val delegateReferenceInterfaceId = descriptor.referenceInterfaceId()
        val definition = InteropRuntimeHooks.augmentInspectableDefinition(
            value = callback,
            definition = WinRtCcwDefinition(
                interfaceDefinitions = listOf(
                    WinRtInspectableInterfaceDefinition(
                        interfaceId = descriptor.interfaceId,
                        baseKind = WinRtComInterfaceBaseKind.IUnknown,
                        methods = listOf(
                            WinRtInspectableMethodDefinition(WinRtDelegateAbiMarshaller.functionSignature(descriptor)) { args ->
                                invoke(args)
                            },
                        ),
                    ),
                    createPropertyValueInterfaceDefinition(callback),
                    WinRtInspectableInterfaceDefinition(
                        interfaceId = delegateReferenceInterfaceId,
                        methods = listOf(
                            WinRtInspectableMethodDefinition(
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
                    WinRtInspectableInterfaceDefinition(
                        interfaceId = IID.IInspectable,
                        methods = emptyList(),
                    ),
                ),
                defaultInterfaceId = descriptor.interfaceId,
            ),
        )
        return WinRtInspectableComObject(
            interfaceDefinitions = definition.interfaceDefinitions,
            hiddenInterfaceDefinitions = definition.hiddenInterfaceDefinitions,
            defaultInterfaceId = definition.defaultInterfaceId,
            runtimeClassName = descriptor.runtimeClassName ?: definition.runtimeClassName,
        )
    }
}

private fun WinRtDelegateDescriptor.isDispatcherQueueHandler(): Boolean =
    interfaceId == IID.DispatcherQueueHandler &&
        parameterKinds.isEmpty() &&
        returnKind == WinRtDelegateValueKind.UNIT

class WinRtDelegateReference internal constructor(
    comPtr: ComPtr,
    val descriptor: WinRtDelegateDescriptor,
) : ComObjectReference(comPtr) {
    internal constructor(
        pointer: RawAddress,
        descriptor: WinRtDelegateDescriptor,
    ) : this(
        ComPtr.create(pointer.asRawComPtr(), descriptor.interfaceId),
        descriptor,
    )

    companion object {
        fun fromAbi(
            pointer: RawAddress,
            descriptor: WinRtDelegateDescriptor,
        ): WinRtDelegateReference? =
            if (PlatformAbi.isNull(pointer)) {
                null
            } else {
                WinRtDelegateReference(pointer, descriptor)
            }
    }

    fun invokeAbi(arguments: List<Any?>): HResult {
        WinRtDelegateAbiMarshaller.encodeArgumentsLease(descriptor, arguments).use { encodedArguments ->
            val signature = WinRtDelegateAbiMarshaller.functionSignature(descriptor)
            val words = ComAbiWord.fromDynamicArgs(signature.explicitParameterKinds, encodedArguments.values)
            return HResult(
                comPtr.invokeGeneric(
                    slot = WinRtDelegateVftblSlots.Invoke,
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
        return if (descriptor.returnKind == WinRtDelegateValueKind.UNIT) {
            invokeAbi(arguments).requireSuccess()
            Unit
        } else {
            PlatformAbi.confinedScope().use { scope ->
                WinRtDelegateAbiMarshaller.encodeArgumentsLease(descriptor, arguments).use { encodedArguments ->
                    val resultOut = WinRtDelegateAbiMarshaller.allocateReturnOut(descriptor, scope)
                    val signature = WinRtDelegateAbiMarshaller.functionSignature(descriptor)
                    val abiArguments = encodedArguments.values + resultOut
                    val words = ComAbiWord.fromDynamicArgs(signature.explicitParameterKinds, abiArguments)
                    val hr = comPtr.invokeGeneric(
                        slot = WinRtDelegateVftblSlots.Invoke,
                        signature = signature,
                        args = words,
                    )
                    WinRtPlatformApi.checkSucceededRaw(hr)
                    WinRtDelegateAbiMarshaller.decodeReturnValue(descriptor, resultOut)
                }
            }
        }
    }
}
