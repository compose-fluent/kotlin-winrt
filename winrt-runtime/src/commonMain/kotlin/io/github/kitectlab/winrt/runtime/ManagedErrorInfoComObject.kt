package io.github.kitectlab.winrt.runtime

/**
 * Shared fallback for `.cswinrt/src/WinRT.Runtime/Interop/ExceptionErrorInfo*`.
 *
 * Kotlin/JVM cannot mirror the CLR-specific language-exception CCW path, so the
 * runtime still exposes a narrow `IErrorInfo` / `ISupportErrorInfo` COM host.
 * The host mechanics themselves are target-agnostic and stay in `commonMain`;
 * only the final `SetErrorInfo` bridge remains platform-owned.
 */
internal class ManagedErrorInfoComObject(
    private val error: Throwable,
) : AutoCloseable {
    private val scope = NativeInterop.sharedScope()
    private val state = ManagedComHostState(::cleanup)
    private val interfaceEntries = mapOf(
        IID.IErrorInfo to createInterfaceEntry(
            slotCount = 8,
            slotBuilder = ::populateErrorInfoSlots,
        ),
        IID.ISupportErrorInfo to createInterfaceEntry(
            slotCount = 4,
            slotBuilder = ::populateSupportErrorInfoSlots,
        ),
    )

    init {
        interfaceEntries.values.forEach { entry ->
            registry[NativeInterop.pointerKey(entry.objectMemory)] = this
        }
    }

    override fun close() {
        releaseManagedReference()
    }

    fun detachReference(interfaceId: Guid = IID.IErrorInfo): IUnknownReference =
        ManagedReferenceHostSupport.detachReference(
            createReference = { createReference(interfaceId) },
            releaseManagedReference = ::releaseManagedReference,
        )

    private fun createReference(interfaceId: Guid): IUnknownReference {
        addReference()
        return IUnknownReference(interfacePointer(interfaceId), interfaceId)
    }

    private fun releaseManagedReference() {
        releaseReference()
    }

    private fun interfacePointer(interfaceId: Guid): NativePointer =
        interfaceEntries[interfaceId]?.objectMemory
            ?: throw WinRtUnsupportedOperationException(
                "Managed error info object does not implement '$interfaceId'.",
                KnownHResults.E_NOINTERFACE,
            )

    private fun createInterfaceEntry(
        slotCount: Int,
        slotBuilder: (NativePointer, MutableList<NativeCallbackHandle>) -> Unit,
    ): InterfaceEntry {
        val objectMemory = NativeInterop.allocatePointerSlot(scope)
        val vtableMemory = NativeInterop.allocatePointerArray(scope, slotCount)
        val callbacks = mutableListOf<NativeCallbackHandle>()
        val queryInterfaceCallback = callbackOf(
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
        val addRefCallback = callbackOf(
            NativeFunctionDescriptor.of(
                NativeValueLayout.JAVA_INT,
                NativeValueLayout.ADDRESS,
            ),
        ) { addReference() }
        val releaseCallback = callbackOf(
            NativeFunctionDescriptor.of(
                NativeValueLayout.JAVA_INT,
                NativeValueLayout.ADDRESS,
            ),
        ) { releaseReference() }
        callbacks += queryInterfaceCallback
        callbacks += addRefCallback
        callbacks += releaseCallback
        NativeInterop.writePointerAt(vtableMemory, IUnknownVftblSlots.QueryInterface, queryInterfaceCallback.pointer)
        NativeInterop.writePointerAt(vtableMemory, IUnknownVftblSlots.AddRef, addRefCallback.pointer)
        NativeInterop.writePointerAt(vtableMemory, IUnknownVftblSlots.Release, releaseCallback.pointer)
        slotBuilder(vtableMemory, callbacks)
        NativeInterop.writePointer(objectMemory, vtableMemory)
        return InterfaceEntry(objectMemory = objectMemory, callbacks = callbacks)
    }

    private fun populateErrorInfoSlots(
        vtableMemory: NativePointer,
        callbacks: MutableList<NativeCallbackHandle>,
    ) {
        val getGuidCallback = callbackOf(
            NativeFunctionDescriptor.of(
                NativeValueLayout.JAVA_INT,
                NativeValueLayout.ADDRESS,
                NativeValueLayout.ADDRESS,
            ),
        ) { args ->
            getGuid(args[1] as NativePointer)
        }
        val getSourceCallback = callbackOf(
            NativeFunctionDescriptor.of(
                NativeValueLayout.JAVA_INT,
                NativeValueLayout.ADDRESS,
                NativeValueLayout.ADDRESS,
            ),
        ) { args ->
            getSource(args[1] as NativePointer)
        }
        val getDescriptionCallback = callbackOf(
            NativeFunctionDescriptor.of(
                NativeValueLayout.JAVA_INT,
                NativeValueLayout.ADDRESS,
                NativeValueLayout.ADDRESS,
            ),
        ) { args ->
            getDescription(args[1] as NativePointer)
        }
        val getHelpFileCallback = callbackOf(
            NativeFunctionDescriptor.of(
                NativeValueLayout.JAVA_INT,
                NativeValueLayout.ADDRESS,
                NativeValueLayout.ADDRESS,
            ),
        ) { args ->
            getHelpFile(args[1] as NativePointer)
        }
        val getHelpFileContentCallback = callbackOf(
            NativeFunctionDescriptor.of(
                NativeValueLayout.JAVA_INT,
                NativeValueLayout.ADDRESS,
                NativeValueLayout.ADDRESS,
            ),
        ) { args ->
            getHelpFileContent(args[1] as NativePointer)
        }
        callbacks += getGuidCallback
        callbacks += getSourceCallback
        callbacks += getDescriptionCallback
        callbacks += getHelpFileCallback
        callbacks += getHelpFileContentCallback
        NativeInterop.writePointerAt(vtableMemory, 3, getGuidCallback.pointer)
        NativeInterop.writePointerAt(vtableMemory, 4, getSourceCallback.pointer)
        NativeInterop.writePointerAt(vtableMemory, 5, getDescriptionCallback.pointer)
        NativeInterop.writePointerAt(vtableMemory, 6, getHelpFileCallback.pointer)
        NativeInterop.writePointerAt(vtableMemory, 7, getHelpFileContentCallback.pointer)
    }

    private fun populateSupportErrorInfoSlots(
        vtableMemory: NativePointer,
        callbacks: MutableList<NativeCallbackHandle>,
    ) {
        val supportsErrorInfoCallback = callbackOf(
            NativeFunctionDescriptor.of(
                NativeValueLayout.JAVA_INT,
                NativeValueLayout.ADDRESS,
                NativeValueLayout.ADDRESS,
            ),
        ) { args ->
            interfaceSupportsErrorInfo(args[1] as NativePointer)
        }
        callbacks += supportsErrorInfoCallback
        NativeInterop.writePointerAt(vtableMemory, 3, supportsErrorInfoCallback.pointer)
    }

    private fun queryInterface(
        requestedInterfaceId: Guid,
        resultPointer: NativePointer,
    ): Int {
        val queryResult = state.queryInterface(requestedInterfaceId) { requested ->
            when (requested) {
                IID.IUnknown,
                IID.IErrorInfo,
                IID.ISupportErrorInfo,
                -> interfaceEntries[requested]?.objectMemory ?: interfaceEntries.getValue(IID.IErrorInfo).objectMemory

                else -> null
            }
        }
        NativeInterop.writePointer(
            resultPointer,
            queryResult.target ?: NativeInterop.nullPointer,
        )
        return queryResult.hResult.value
    }

    private fun addReference(): Int = state.addReference()

    private fun releaseReference(): Int = state.releaseReference()

    private fun getGuid(resultPointer: NativePointer): Int {
        NativeInterop.writeGuid(resultPointer, guidOf("00000000-0000-0000-0000-000000000000"))
        return KnownHResults.S_OK.value
    }

    private fun getSource(resultPointer: NativePointer): Int =
        writeBstr(resultPointer, error::class.typeDisplayName())

    private fun getDescription(resultPointer: NativePointer): Int =
        writeBstr(resultPointer, error.message ?: error::class.typeDisplayName())

    private fun getHelpFile(resultPointer: NativePointer): Int =
        writeBstr(resultPointer, null)

    private fun getHelpFileContent(resultPointer: NativePointer): Int =
        writeBstr(resultPointer, null)

    private fun interfaceSupportsErrorInfo(@Suppress("UNUSED_PARAMETER") interfaceIdPointer: NativePointer): Int =
        KnownHResults.S_OK.value

    private fun writeBstr(
        resultPointer: NativePointer,
        value: String?,
    ): Int {
        NativeInterop.writePointer(resultPointer, NativeInterop.nullPointer)
        return runCatching {
            NativeInterop.writePointer(resultPointer, WinRtPlatformApi.sysAllocStringRaw(value))
            KnownHResults.S_OK.value
        }.getOrElse { failure ->
            platformHResultFromThrowable(failure).value
        }
    }

    private fun cleanup() {
        interfaceEntries.values.forEach { entry ->
            registry.remove(NativeInterop.pointerKey(entry.objectMemory))
            entry.callbacks.forEach(NativeCallbackHandle::close)
        }
        scope.close()
    }

    private fun callbackOf(
        descriptor: NativeFunctionDescriptor,
        callback: (List<Any?>) -> Int,
    ): NativeCallbackHandle = NativeInterop.createCallback(descriptor, callback)

    private data class InterfaceEntry(
        val objectMemory: NativePointer,
        val callbacks: List<NativeCallbackHandle>,
    )

    companion object {
        private val registry = ConcurrentCacheMap<Long, ManagedErrorInfoComObject>()
    }
}
