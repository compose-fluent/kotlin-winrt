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
            vtable = errorInfoVtable,
        ),
        IID.ISupportErrorInfo to createInterfaceEntry(
            slotCount = 4,
            vtable = supportErrorInfoVtable,
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
        vtable: RawAddress,
    ): InterfaceEntry {
        val objectMemory = PlatformAbi.allocatePointerSlot(scope)
        require(slotCount > 0)
        PlatformAbi.writePointer(objectMemory, vtable)
        return InterfaceEntry(objectMemory = objectMemory)
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
        }
        scope.close()
    }

    private data class InterfaceEntry(
        val objectMemory: RawAddress,
    )

    companion object {
        private val registry = ConcurrentCacheMap<Long, ManagedErrorInfoComObject>()
        private val sharedScope = PlatformAbi.sharedScope()
        private val queryInterfaceCallback = callbackOf(IUnknownVftbl.QueryInterface) { args ->
            hostFor(args[0] as RawAddress)?.queryInterface(
                requestedInterfaceId = PlatformAbi.readGuid(args[1] as RawAddress),
                resultPointer = args[2] as RawAddress,
            ) ?: KnownHResults.E_POINTER.value
        }
        private val addRefCallback = callbackOf(IUnknownVftbl.AddRef) { args ->
            hostFor(args[0] as RawAddress)?.addReference() ?: 0
        }
        private val releaseCallback = callbackOf(IUnknownVftbl.Release) { args ->
            hostFor(args[0] as RawAddress)?.releaseReference() ?: 0
        }
        private val getGuidCallback = callbackOf(ComMethodSignatures.HResult_Ptr) { args ->
            hostFor(args[0] as RawAddress)?.getGuid(args[1] as RawAddress)
                ?: KnownHResults.E_POINTER.value
        }
        private val getSourceCallback = callbackOf(ComMethodSignatures.HResult_Ptr) { args ->
            hostFor(args[0] as RawAddress)?.getSource(args[1] as RawAddress)
                ?: KnownHResults.E_POINTER.value
        }
        private val getDescriptionCallback = callbackOf(ComMethodSignatures.HResult_Ptr) { args ->
            hostFor(args[0] as RawAddress)?.getDescription(args[1] as RawAddress)
                ?: KnownHResults.E_POINTER.value
        }
        private val getHelpFileCallback = callbackOf(ComMethodSignatures.HResult_Ptr) { args ->
            hostFor(args[0] as RawAddress)?.getHelpFile(args[1] as RawAddress)
                ?: KnownHResults.E_POINTER.value
        }
        private val getHelpFileContentCallback = callbackOf(ComMethodSignatures.HResult_Ptr) { args ->
            hostFor(args[0] as RawAddress)?.getHelpFileContent(args[1] as RawAddress)
                ?: KnownHResults.E_POINTER.value
        }
        private val supportsErrorInfoCallback = callbackOf(ComMethodSignatures.HResult_Ptr) { args ->
            hostFor(args[0] as RawAddress)?.interfaceSupportsErrorInfo(args[1] as RawAddress)
                ?: KnownHResults.E_POINTER.value
        }
        private val errorInfoVtable = createErrorInfoVtable()
        private val supportErrorInfoVtable = createSupportErrorInfoVtable()

        private fun createErrorInfoVtable(): RawAddress {
            val vtable = PlatformAbi.allocatePointerArray(sharedScope, 8)
            populateUnknownSlots(vtable)
            PlatformAbi.writePointerAt(vtable, 3, getGuidCallback.pointer)
            PlatformAbi.writePointerAt(vtable, 4, getSourceCallback.pointer)
            PlatformAbi.writePointerAt(vtable, 5, getDescriptionCallback.pointer)
            PlatformAbi.writePointerAt(vtable, 6, getHelpFileCallback.pointer)
            PlatformAbi.writePointerAt(vtable, 7, getHelpFileContentCallback.pointer)
            return vtable
        }

        private fun createSupportErrorInfoVtable(): RawAddress {
            val vtable = PlatformAbi.allocatePointerArray(sharedScope, 4)
            populateUnknownSlots(vtable)
            PlatformAbi.writePointerAt(vtable, 3, supportsErrorInfoCallback.pointer)
            return vtable
        }

        private fun populateUnknownSlots(vtable: RawAddress) {
            PlatformAbi.writePointerAt(vtable, IUnknownVftblSlots.QueryInterface, queryInterfaceCallback.pointer)
            PlatformAbi.writePointerAt(vtable, IUnknownVftblSlots.AddRef, addRefCallback.pointer)
            PlatformAbi.writePointerAt(vtable, IUnknownVftblSlots.Release, releaseCallback.pointer)
        }

        private fun hostFor(pointer: RawAddress): ManagedErrorInfoComObject? =
            registry[PlatformAbi.pointerKey(pointer)]

        private fun callbackOf(
            signature: ComMethodSignature,
            callback: (List<Any?>) -> Int,
        ): NativeCallbackHandle = ComAbiInteropBridge.createComMethodCallback(signature, callback)
    }
}
