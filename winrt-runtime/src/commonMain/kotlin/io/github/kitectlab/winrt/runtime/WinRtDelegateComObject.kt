package io.github.kitectlab.winrt.runtime

internal class WinRtDelegateComObject(
    private val descriptor: WinRtDelegateDescriptor,
    private val callback: (List<Any?>) -> Any?,
) {
    private val scope = NativeInterop.sharedScope()
    private val objectMemory = NativeInterop.allocatePointerSlot(scope)
    private val vtableMemory = NativeInterop.allocatePointerArray(scope, WinRtDelegateVftblSlots.Invoke + 1)
    private val queryInterfaceCallback = NativeInterop.createCallback(
        NativeFunctionDescriptor.of(
            NativeValueLayout.JAVA_INT,
            NativeValueLayout.ADDRESS,
            NativeValueLayout.ADDRESS,
            NativeValueLayout.ADDRESS,
        ),
    ) { args ->
        queryInterface(
            requestedInterfaceId = NativeInterop.readGuid(args[1] as NativePointer),
            resultPointer = args[2] as NativePointer,
        )
    }
    private val addRefCallback = NativeInterop.createCallback(
        NativeFunctionDescriptor.of(
            NativeValueLayout.JAVA_INT,
            NativeValueLayout.ADDRESS,
        ),
    ) {
        addReference()
    }
    private val releaseCallback = NativeInterop.createCallback(
        NativeFunctionDescriptor.of(
            NativeValueLayout.JAVA_INT,
            NativeValueLayout.ADDRESS,
        ),
    ) {
        releaseReference()
    }
    private val invokeCallback = NativeInterop.createCallback(
        WinRtDelegateAbiMarshaller.functionDescriptor(descriptor),
    ) { args ->
        invoke(args.drop(1))
    }
    private val state = ManagedComHostState(::cleanup)

    init {
        registry[NativeInterop.pointerKey(objectMemory)] = this
        NativeInterop.writePointerAt(vtableMemory, IUnknownVftblSlots.QueryInterface, queryInterfaceCallback.pointer)
        NativeInterop.writePointerAt(vtableMemory, IUnknownVftblSlots.AddRef, addRefCallback.pointer)
        NativeInterop.writePointerAt(vtableMemory, IUnknownVftblSlots.Release, releaseCallback.pointer)
        NativeInterop.writePointerAt(vtableMemory, WinRtDelegateVftblSlots.Invoke, invokeCallback.pointer)
        NativeInterop.writePointer(objectMemory, vtableMemory)
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
        resultPointer: NativePointer,
    ): Int {
        val queryResult = state.queryInterface(requestedInterfaceId) { requested ->
            if (requested == IID.IUnknown || requested == descriptor.interfaceId) {
                Unit
            } else {
                null
            }
        }
        if (queryResult.target != null) {
            NativeInterop.writePointer(resultPointer, objectMemory)
        } else {
            NativeInterop.writePointer(resultPointer, NativeInterop.nullPointer)
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
                    parameterKinds = descriptor.parameterKinds,
                    abiArguments = abiArguments,
                ),
            )
            if (hasReturnValue) {
                val resultOut = rawArguments.last() as? NativePointer
                    ?: error("Non-unit delegate invocation requires a trailing ABI return buffer.")
                WinRtDelegateAbiMarshaller.writeReturnValue(descriptor.returnKind, returnValue, resultOut)
            }
            KnownHResults.S_OK.value
        } catch (error: Throwable) {
            platformSetErrorInfo(error)
            platformHResultFromThrowable(error).value
        }

    private fun cleanup() {
        registry.remove(NativeInterop.pointerKey(objectMemory))
        invokeCallback.close()
        releaseCallback.close()
        addRefCallback.close()
        queryInterfaceCallback.close()
        scope.close()
    }

    companion object {
        private val registry = ConcurrentCacheMap<Long, WinRtDelegateComObject>()

        internal fun releaseLocalReference(pointer: NativePointer): Boolean {
            val delegate = registry[NativeInterop.pointerKey(pointer)] ?: return false
            delegate.releaseManagedReference()
            return true
        }
    }
}

class WinRtDelegateReference internal constructor(
    pointer: NativePointer,
    val descriptor: WinRtDelegateDescriptor,
) : ComObjectReference(pointer, descriptor.interfaceId) {
    companion object {
        fun fromAbi(
            pointer: NativePointer,
            descriptor: WinRtDelegateDescriptor,
        ): WinRtDelegateReference? =
            if (NativeInterop.isNull(pointer)) {
                null
            } else {
                WinRtDelegateReference(pointer, descriptor)
            }
    }

    fun invokeAbi(arguments: List<Any?>): HResult {
        val encodedArguments = WinRtDelegateAbiMarshaller.encodeArguments(descriptor.parameterKinds, arguments)
        return HResult(
            invokeIntMethod(
                slot = WinRtDelegateVftblSlots.Invoke,
                descriptor = WinRtDelegateAbiMarshaller.functionDescriptor(descriptor),
                *encodedArguments.toTypedArray(),
            ),
        )
    }

    fun invoke(arguments: List<Any?>): Any? {
        require(arguments.size == descriptor.parameterKinds.size) {
            "Argument count ${arguments.size} must match delegate parameter count ${descriptor.parameterKinds.size}."
        }
        val encodedArguments = WinRtDelegateAbiMarshaller.encodeArguments(descriptor.parameterKinds, arguments)
        return if (descriptor.returnKind == WinRtDelegateValueKind.UNIT) {
            invokeAbi(arguments).requireSuccess()
            Unit
        } else {
            NativeInterop.confinedScope().use { scope ->
                val resultOut = WinRtDelegateAbiMarshaller.allocateReturnOut(descriptor.returnKind, scope)
                val hr = invokeIntMethod(
                    slot = WinRtDelegateVftblSlots.Invoke,
                    descriptor = WinRtDelegateAbiMarshaller.functionDescriptor(descriptor),
                    *arrayOf(*encodedArguments.toTypedArray(), resultOut),
                )
                WinRtPlatformApi.checkSucceededRaw(hr)
                WinRtDelegateAbiMarshaller.decodeReturnValue(descriptor.returnKind, resultOut)
            }
        }
    }
}
