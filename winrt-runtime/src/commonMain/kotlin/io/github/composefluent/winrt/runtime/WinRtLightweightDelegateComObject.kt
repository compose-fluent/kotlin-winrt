package io.github.composefluent.winrt.runtime

internal class WinRtLightweightDelegateComObject(
    private val descriptor: WinRtDelegateDescriptor,
    private val callback: (List<Any?>) -> Any?,
) {
    private val scope = PlatformAbi.sharedScope()
    private val state = ManagedComHostState(::cleanup)
    private val objectMemory = PlatformAbi.allocatePointerSlot(scope)

    init {
        PlatformAbi.writePointer(objectMemory, SharedVtables.getOrCreate(descriptor))
        registry[PlatformAbi.pointerKey(objectMemory)] = this
    }

    fun createReference(): WinRtDelegateReference {
        state.addReference()
        val reference = WinRtDelegateReference(objectMemory, descriptor)
        state.releaseReference()
        return reference
    }

    private fun queryInterface(
        requestedInterfaceId: Guid,
        resultPointer: RawAddress,
    ): Int {
        val targetPointer =
            if (requestedInterfaceId == IID.IUnknown ||
                requestedInterfaceId == IID.IAgileObject ||
                requestedInterfaceId == descriptor.interfaceId
            ) {
                objectMemory
            } else {
                null
            }
        val queryResult = state.queryInterface(requestedInterfaceId) { targetPointer }
        PlatformAbi.writePointer(
            resultPointer,
            queryResult.target ?: PlatformAbi.nullPointer,
        )
        return queryResult.hResult.value
    }

    private fun addReference(): Int = state.addReference()

    private fun releaseReference(): Int = state.releaseReference()

    private fun invoke(rawArguments: List<Any?>): Int =
        WinRtDelegateInvocationSupport.invoke(descriptor, callback, rawArguments)

    private fun cleanup() {
        registry.remove(PlatformAbi.pointerKey(objectMemory))
        scope.close()
    }

    companion object {
        private val registry = ConcurrentCacheMap<Long, WinRtLightweightDelegateComObject>()

        private fun findHost(pointer: RawAddress): WinRtLightweightDelegateComObject? =
            registry[PlatformAbi.pointerKey(pointer)]

        fun createReference(
            descriptor: WinRtDelegateDescriptor,
            callback: (List<Any?>) -> Any?,
        ): WinRtDelegateReference =
            WinRtLightweightDelegateComObject(descriptor, callback).createReference()

        private object SharedVtables {
            private val scope = PlatformAbi.sharedScope()
            private val vtables = ConcurrentCacheMap<WinRtDelegateDescriptor, RawAddress>()
            private val invokeCallbacks = ConcurrentCacheMap<WinRtDelegateDescriptor, NativeCallbackHandle>()

            private val queryInterfaceCallback =
                callbackOf(IUnknownVftbl.QueryInterface) { args ->
                    findHost(args[0] as RawAddress)?.queryInterface(
                        requestedInterfaceId = PlatformAbi.readGuid(args[1] as RawAddress),
                        resultPointer = args[2] as RawAddress,
                    ) ?: KnownHResults.E_POINTER.value
                }

            private val addRefCallback =
                callbackOf(IUnknownVftbl.AddRef) { args ->
                    findHost(args[0] as RawAddress)?.addReference() ?: 0
                }

            private val releaseCallback =
                callbackOf(IUnknownVftbl.Release) { args ->
                    findHost(args[0] as RawAddress)?.releaseReference() ?: 0
                }

            fun getOrCreate(descriptor: WinRtDelegateDescriptor): RawAddress =
                vtables.computeIfAbsent(descriptor) {
                    val vtable = PlatformAbi.allocatePointerArray(scope, WinRtDelegateVftblSlots.Invoke + 1)
                    PlatformAbi.writePointerAt(vtable, IUnknownVftblSlots.QueryInterface, queryInterfaceCallback.pointer)
                    PlatformAbi.writePointerAt(vtable, IUnknownVftblSlots.AddRef, addRefCallback.pointer)
                    PlatformAbi.writePointerAt(vtable, IUnknownVftblSlots.Release, releaseCallback.pointer)
                    PlatformAbi.writePointerAt(vtable, WinRtDelegateVftblSlots.Invoke, invokeCallback(descriptor).pointer)
                    vtable
                }

            private fun invokeCallback(descriptor: WinRtDelegateDescriptor): NativeCallbackHandle =
                invokeCallbacks.computeIfAbsent(descriptor) {
                    callbackOf(WinRtDelegateAbiMarshaller.functionSignature(descriptor)) { args ->
                        findHost(args[0] as RawAddress)?.invoke(args.drop(1)) ?: KnownHResults.E_POINTER.value
                    }
                }

            private fun callbackOf(
                signature: ComMethodSignature,
                callback: (List<Any?>) -> Int,
            ): NativeCallbackHandle =
                ComAbiInteropBridge.createComMethodCallback(signature, callback)
        }
    }
}
