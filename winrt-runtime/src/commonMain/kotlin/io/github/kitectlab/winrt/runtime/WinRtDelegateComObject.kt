package io.github.kitectlab.winrt.runtime

internal class WinRtDelegateComObject(
    private val descriptor: WinRtDelegateDescriptor,
    private val callback: (List<Any?>) -> Any?,
) {
    private val scope = PlatformAbi.sharedScope()
    private val objectMemory = PlatformAbi.allocatePointerSlot(scope)
    private val vtableMemory = PlatformAbi.allocatePointerArray(scope, WinRtDelegateVftblSlots.Invoke + 1)
    private val queryInterfaceCallback = ComAbiInteropBridge.createComMethodCallback(IUnknownVftbl.QueryInterface) { args ->
        queryInterface(
            requestedInterfaceId = PlatformAbi.readGuid(args[1] as RawAddress),
            resultPointer = args[2] as RawAddress,
        )
    }
    private val addRefCallback = ComAbiInteropBridge.createComMethodCallback(IUnknownVftbl.AddRef) {
        addReference()
    }
    private val releaseCallback = ComAbiInteropBridge.createComMethodCallback(IUnknownVftbl.Release) {
        releaseReference()
    }
    private val invokeCallback = ComAbiInteropBridge.createComMethodCallback(WinRtDelegateAbiMarshaller.functionSignature(descriptor)) { args ->
        invoke(args.drop(1))
    }
    private val state = ManagedComHostState(::cleanup)

    init {
        registry[PlatformAbi.pointerKey(objectMemory)] = this
        PlatformAbi.writePointerAt(vtableMemory, IUnknownVftblSlots.QueryInterface, queryInterfaceCallback.pointer)
        PlatformAbi.writePointerAt(vtableMemory, IUnknownVftblSlots.AddRef, addRefCallback.pointer)
        PlatformAbi.writePointerAt(vtableMemory, IUnknownVftblSlots.Release, releaseCallback.pointer)
        PlatformAbi.writePointerAt(vtableMemory, WinRtDelegateVftblSlots.Invoke, invokeCallback.pointer)
        PlatformAbi.writePointer(objectMemory, vtableMemory)
    }

    fun createReference(): WinRtDelegateReference {
        addReference()
        return WinRtDelegateReference(
            pointer = objectMemory,
            descriptor = descriptor,
        )
    }

    fun releaseManagedReference() {
        releaseReference()
    }

    private fun queryInterface(
        requestedInterfaceId: Guid,
        resultPointer: RawAddress,
    ): Int {
        val queryResult = state.queryInterface(requestedInterfaceId) { requested ->
            if (requested == IID.IUnknown || requested == descriptor.interfaceId) {
                Unit
            } else {
                null
            }
        }
        if (queryResult.target != null) {
            PlatformAbi.writePointer(resultPointer, objectMemory)
        } else {
            PlatformAbi.writePointer(resultPointer, PlatformAbi.nullPointer)
        }
        return queryResult.hResult.value
    }

    private fun addReference(): Int = state.addReference()

    private fun releaseReference(): Int = state.releaseReference()

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
            platformSetErrorInfo(error)
            platformHResultFromThrowable(error).value
        }

    private fun cleanup() {
        registry.remove(PlatformAbi.pointerKey(objectMemory))
        invokeCallback.close()
        releaseCallback.close()
        addRefCallback.close()
        queryInterfaceCallback.close()
        scope.close()
    }

    companion object {
        private val registry = ConcurrentCacheMap<Long, WinRtDelegateComObject>()

        internal fun releaseLocalReference(pointer: RawAddress): Boolean {
            val delegate = registry[PlatformAbi.pointerKey(pointer)] ?: return false
            delegate.releaseManagedReference()
            return true
        }
    }
}

class WinRtDelegateReference internal constructor(
    pointer: RawAddress,
    val descriptor: WinRtDelegateDescriptor,
) : ComObjectReference(pointer.asRawComPtr(), descriptor.interfaceId) {
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
