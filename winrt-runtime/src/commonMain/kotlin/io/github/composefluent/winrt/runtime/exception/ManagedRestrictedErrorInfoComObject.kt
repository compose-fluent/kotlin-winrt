package io.github.composefluent.winrt.runtime.exception

import io.github.composefluent.winrt.runtime.ConcurrentCacheMap
import io.github.composefluent.winrt.runtime.ComAbiValueKind
import io.github.composefluent.winrt.runtime.ComAbiInteropBridge
import io.github.composefluent.winrt.runtime.ComMethodSignature
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
import io.github.composefluent.winrt.runtime.WinRTPlatformApi
import io.github.composefluent.winrt.runtime.WinRTRestrictedErrorInfo
import io.github.composefluent.winrt.runtime.WinRTUnsupportedOperationException
import io.github.composefluent.winrt.runtime.asRawComPtr
import io.github.composefluent.winrt.runtime.platformHResultFromThrowable

/**
 * Shared owner for the writable `IRestrictedErrorInfo` fallback path from
 * `.cswinrt/src/WinRT.Runtime/ExceptionHelpers.cs`.
 *
 * Kotlin/JVM cannot mirror CLR language-exception propagation, but it can still
 * round-trip restricted error details back into the WinRT global error state.
 */
internal class ManagedRestrictedErrorInfoComObject(
    private val hResult: HResult,
    private val errorInfo: WinRTRestrictedErrorInfo,
) : AutoCloseable {
    private val scope = PlatformAbi.sharedScope()
    private val state = ManagedComHostState(::cleanup)
    private val interfaceEntry = createInterfaceEntry()

    init {
        registry[PlatformAbi.pointerKey(interfaceEntry.objectMemory)] = this
    }

    override fun close() {
        releaseManagedReference()
    }

    fun detachReference(): IUnknownReference =
        ManagedReferenceHostSupport.detachReference(
            createReference = ::createReference,
            releaseManagedReference = ::releaseManagedReference,
        )

    private fun createReference(): IUnknownReference {
        addReference()
        return IUnknownReference(interfaceEntry.objectMemory.asRawComPtr(), IID.IRestrictedErrorInfo)
    }

    private fun releaseManagedReference() {
        releaseReference()
    }

    private fun createInterfaceEntry(): InterfaceEntry {
        val objectMemory = PlatformAbi.allocatePointerSlot(scope)
        PlatformAbi.writePointer(objectMemory, restrictedErrorInfoVtable)
        return InterfaceEntry(objectMemory = objectMemory)
    }

    private fun queryInterface(
        requestedInterfaceId: Guid,
        resultPointer: RawAddress,
    ): Int {
        val queryResult = state.queryInterface(requestedInterfaceId) { requested ->
            when (requested) {
                IID.IUnknown,
                IID.IRestrictedErrorInfo,
                -> interfaceEntry.objectMemory

                else -> null
            }
        }
        PlatformAbi.writePointer(resultPointer, queryResult.target ?: PlatformAbi.nullPointer)
        return queryResult.hResult.value
    }

    private fun addReference(): Int = state.addReference()

    private fun releaseReference(): Int = state.releaseReference()

    private fun getErrorDetails(
        descriptionOut: RawAddress,
        hResultOut: RawAddress,
        restrictedDescriptionOut: RawAddress,
        capabilitySidOut: RawAddress,
    ): Int {
        PlatformAbi.writePointer(descriptionOut, PlatformAbi.nullPointer)
        PlatformAbi.writeInt32(hResultOut, hResult.value)
        PlatformAbi.writePointer(restrictedDescriptionOut, PlatformAbi.nullPointer)
        PlatformAbi.writePointer(capabilitySidOut, PlatformAbi.nullPointer)
        val descriptionResult = writeBstr(descriptionOut, errorInfo.description)
        if (descriptionResult < 0) {
            return descriptionResult
        }
        val restrictedDescriptionResult = writeBstr(restrictedDescriptionOut, errorInfo.restrictedDescription)
        if (restrictedDescriptionResult < 0) {
            return restrictedDescriptionResult
        }
        return writeBstr(capabilitySidOut, errorInfo.capabilitySid)
    }

    private fun getReference(resultPointer: RawAddress): Int {
        PlatformAbi.writePointer(resultPointer, PlatformAbi.nullPointer)
        return writeBstr(resultPointer, errorInfo.reference)
    }

    private fun writeBstr(
        resultPointer: RawAddress,
        value: String?,
    ): Int =
        runCatching {
            PlatformAbi.writePointer(resultPointer, WinRTPlatformApi.sysAllocStringRaw(value))
            KnownHResults.S_OK.value
        }.getOrElse { failure ->
            platformHResultFromThrowable(failure).value
        }

    private fun cleanup() {
        registry.remove(PlatformAbi.pointerKey(interfaceEntry.objectMemory))
        scope.close()
    }

    private data class InterfaceEntry(
        val objectMemory: RawAddress,
    )

    companion object {
        private val registry = ConcurrentCacheMap<Long, ManagedRestrictedErrorInfoComObject>()
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
        private val getErrorDetailsCallback =
            callbackOf(
                ComMethodSignature.of(
                    ComAbiValueKind.Pointer,
                    ComAbiValueKind.Pointer,
                    ComAbiValueKind.Pointer,
                    ComAbiValueKind.Pointer,
                ),
            ) { args ->
                hostFor(args[0] as RawAddress)?.getErrorDetails(
                    descriptionOut = args[1] as RawAddress,
                    hResultOut = args[2] as RawAddress,
                    restrictedDescriptionOut = args[3] as RawAddress,
                    capabilitySidOut = args[4] as RawAddress,
                ) ?: KnownHResults.E_POINTER.value
            }
        private val getReferenceCallback = callbackOf(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
            hostFor(args[0] as RawAddress)?.getReference(args[1] as RawAddress)
                ?: KnownHResults.E_POINTER.value
        }
        private val restrictedErrorInfoVtable = createRestrictedErrorInfoVtable()

        private fun createRestrictedErrorInfoVtable(): RawAddress {
            val vtable = PlatformAbi.allocatePointerArray(sharedScope, 5)
            PlatformAbi.writePointerAt(vtable, IUnknownVftblSlots.QueryInterface, queryInterfaceCallback.pointer)
            PlatformAbi.writePointerAt(vtable, IUnknownVftblSlots.AddRef, addRefCallback.pointer)
            PlatformAbi.writePointerAt(vtable, IUnknownVftblSlots.Release, releaseCallback.pointer)
            PlatformAbi.writePointerAt(vtable, 3, getErrorDetailsCallback.pointer)
            PlatformAbi.writePointerAt(vtable, 4, getReferenceCallback.pointer)
            return vtable
        }

        private fun hostFor(pointer: RawAddress): ManagedRestrictedErrorInfoComObject? =
            registry[PlatformAbi.pointerKey(pointer)]

        private fun callbackOf(
            signature: ComMethodSignature,
            callback: (List<Any?>) -> Int,
        ): NativeCallbackHandle = ComAbiInteropBridge.createComMethodCallback(signature, callback)
    }
}
