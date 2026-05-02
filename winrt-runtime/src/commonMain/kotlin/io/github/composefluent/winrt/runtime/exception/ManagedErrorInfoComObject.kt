package io.github.composefluent.winrt.runtime.exception

import io.github.composefluent.winrt.runtime.ConcurrentCacheMap
import io.github.composefluent.winrt.runtime.ComAbiInteropBridge
import io.github.composefluent.winrt.runtime.ComMethodSignature
import io.github.composefluent.winrt.runtime.ComMethodSignatures
import io.github.composefluent.winrt.runtime.Guid
import io.github.composefluent.winrt.runtime.HResult
import io.github.composefluent.winrt.runtime.IID
import io.github.composefluent.winrt.runtime.IUnknownReference
import io.github.composefluent.winrt.runtime.IUnknownVftbl
import io.github.composefluent.winrt.runtime.IUnknownVftblSlots
import io.github.composefluent.winrt.runtime.KnownHResults
import io.github.composefluent.winrt.runtime.ManagedComHostState
import io.github.composefluent.winrt.runtime.ManagedReferenceHostSupport
import io.github.composefluent.winrt.runtime.NativeCallbackHandle
import io.github.composefluent.winrt.runtime.PlatformAbi
import io.github.composefluent.winrt.runtime.RawAddress
import io.github.composefluent.winrt.runtime.WinRtPlatformApi
import io.github.composefluent.winrt.runtime.WinRtUnsupportedOperationException
import io.github.composefluent.winrt.runtime.asRawComPtr
import io.github.composefluent.winrt.runtime.guidOf
import io.github.composefluent.winrt.runtime.platformHResultFromThrowable
import io.github.composefluent.winrt.runtime.typeDisplayName

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
    private val scope = PlatformAbi.sharedScope()
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
            registry[PlatformAbi.pointerKey(entry.objectMemory)] = this
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
        return IUnknownReference(interfacePointer(interfaceId).asRawComPtr(), interfaceId)
    }

    private fun releaseManagedReference() {
        releaseReference()
    }

    private fun interfacePointer(interfaceId: Guid): RawAddress =
        interfaceEntries[interfaceId]?.objectMemory
            ?: throw WinRtUnsupportedOperationException(
                "Managed error info object does not implement '$interfaceId'.",
                KnownHResults.E_NOINTERFACE,
            )

    private fun createInterfaceEntry(
        slotCount: Int,
        slotBuilder: (RawAddress, MutableList<NativeCallbackHandle>) -> Unit,
    ): InterfaceEntry {
        val objectMemory = PlatformAbi.allocatePointerSlot(scope)
        val vtableMemory = PlatformAbi.allocatePointerArray(scope, slotCount)
        val callbacks = mutableListOf<NativeCallbackHandle>()
        val queryInterfaceCallback = callbackOf(IUnknownVftbl.QueryInterface) { args ->
            queryInterface(
                requestedInterfaceId = PlatformAbi.readGuid(args[1] as RawAddress),
                resultPointer = args[2] as RawAddress,
            )
        }
        val addRefCallback = callbackOf(IUnknownVftbl.AddRef) { addReference() }
        val releaseCallback = callbackOf(IUnknownVftbl.Release) { releaseReference() }
        callbacks += queryInterfaceCallback
        callbacks += addRefCallback
        callbacks += releaseCallback
        PlatformAbi.writePointerAt(vtableMemory, IUnknownVftblSlots.QueryInterface, queryInterfaceCallback.pointer)
        PlatformAbi.writePointerAt(vtableMemory, IUnknownVftblSlots.AddRef, addRefCallback.pointer)
        PlatformAbi.writePointerAt(vtableMemory, IUnknownVftblSlots.Release, releaseCallback.pointer)
        slotBuilder(vtableMemory, callbacks)
        PlatformAbi.writePointer(objectMemory, vtableMemory)
        return InterfaceEntry(objectMemory = objectMemory, callbacks = callbacks)
    }

    private fun populateErrorInfoSlots(
        vtableMemory: RawAddress,
        callbacks: MutableList<NativeCallbackHandle>,
    ) {
        val getGuidCallback = callbackOf(ComMethodSignatures.HResult_Ptr) { args ->
            getGuid(args[1] as RawAddress)
        }
        val getSourceCallback = callbackOf(ComMethodSignatures.HResult_Ptr) { args ->
            getSource(args[1] as RawAddress)
        }
        val getDescriptionCallback = callbackOf(ComMethodSignatures.HResult_Ptr) { args ->
            getDescription(args[1] as RawAddress)
        }
        val getHelpFileCallback = callbackOf(ComMethodSignatures.HResult_Ptr) { args ->
            getHelpFile(args[1] as RawAddress)
        }
        val getHelpFileContentCallback = callbackOf(ComMethodSignatures.HResult_Ptr) { args ->
            getHelpFileContent(args[1] as RawAddress)
        }
        callbacks += getGuidCallback
        callbacks += getSourceCallback
        callbacks += getDescriptionCallback
        callbacks += getHelpFileCallback
        callbacks += getHelpFileContentCallback
        PlatformAbi.writePointerAt(vtableMemory, 3, getGuidCallback.pointer)
        PlatformAbi.writePointerAt(vtableMemory, 4, getSourceCallback.pointer)
        PlatformAbi.writePointerAt(vtableMemory, 5, getDescriptionCallback.pointer)
        PlatformAbi.writePointerAt(vtableMemory, 6, getHelpFileCallback.pointer)
        PlatformAbi.writePointerAt(vtableMemory, 7, getHelpFileContentCallback.pointer)
    }

    private fun populateSupportErrorInfoSlots(
        vtableMemory: RawAddress,
        callbacks: MutableList<NativeCallbackHandle>,
    ) {
        val supportsErrorInfoCallback = callbackOf(ComMethodSignatures.HResult_Ptr) { args ->
            interfaceSupportsErrorInfo(args[1] as RawAddress)
        }
        callbacks += supportsErrorInfoCallback
        PlatformAbi.writePointerAt(vtableMemory, 3, supportsErrorInfoCallback.pointer)
    }

    private fun queryInterface(
        requestedInterfaceId: Guid,
        resultPointer: RawAddress,
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
        PlatformAbi.writePointer(
            resultPointer,
            queryResult.target ?: PlatformAbi.nullPointer,
        )
        return queryResult.hResult.value
    }

    private fun addReference(): Int = state.addReference()

    private fun releaseReference(): Int = state.releaseReference()

    private fun getGuid(resultPointer: RawAddress): Int {
        PlatformAbi.writeGuid(resultPointer, guidOf("00000000-0000-0000-0000-000000000000"))
        return KnownHResults.S_OK.value
    }

    private fun getSource(resultPointer: RawAddress): Int =
        writeBstr(resultPointer, error::class.typeDisplayName())

    private fun getDescription(resultPointer: RawAddress): Int =
        writeBstr(resultPointer, error.message ?: error::class.typeDisplayName())

    private fun getHelpFile(resultPointer: RawAddress): Int =
        writeBstr(resultPointer, null)

    private fun getHelpFileContent(resultPointer: RawAddress): Int =
        writeBstr(resultPointer, null)

    private fun interfaceSupportsErrorInfo(@Suppress("UNUSED_PARAMETER") interfaceIdPointer: RawAddress): Int =
        KnownHResults.S_OK.value

    private fun writeBstr(
        resultPointer: RawAddress,
        value: String?,
    ): Int {
        PlatformAbi.writePointer(resultPointer, PlatformAbi.nullPointer)
        return runCatching {
            PlatformAbi.writePointer(resultPointer, WinRtPlatformApi.sysAllocStringRaw(value))
            KnownHResults.S_OK.value
        }.getOrElse { failure ->
            platformHResultFromThrowable(failure).value
        }
    }

    private fun cleanup() {
        interfaceEntries.values.forEach { entry ->
            registry.remove(PlatformAbi.pointerKey(entry.objectMemory))
            entry.callbacks.forEach(NativeCallbackHandle::close)
        }
        scope.close()
    }

    private fun callbackOf(
        signature: ComMethodSignature,
        callback: (List<Any?>) -> Int,
    ): NativeCallbackHandle = ComAbiInteropBridge.createComMethodCallback(signature, callback)

    private data class InterfaceEntry(
        val objectMemory: RawAddress,
        val callbacks: List<NativeCallbackHandle>,
    )

    companion object {
        private val registry = ConcurrentCacheMap<Long, ManagedErrorInfoComObject>()
    }
}
