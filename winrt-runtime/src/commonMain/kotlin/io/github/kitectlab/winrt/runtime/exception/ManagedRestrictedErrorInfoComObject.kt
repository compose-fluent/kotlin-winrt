package io.github.kitectlab.winrt.runtime.exception

import io.github.kitectlab.winrt.runtime.ConcurrentCacheMap
import io.github.kitectlab.winrt.runtime.ComAbiValueKind
import io.github.kitectlab.winrt.runtime.ComAbiInteropBridge
import io.github.kitectlab.winrt.runtime.ComMethodSignature
import io.github.kitectlab.winrt.runtime.Guid
import io.github.kitectlab.winrt.runtime.HResult
import io.github.kitectlab.winrt.runtime.IID
import io.github.kitectlab.winrt.runtime.IUnknownReference
import io.github.kitectlab.winrt.runtime.IUnknownVftbl
import io.github.kitectlab.winrt.runtime.IUnknownVftblSlots
import io.github.kitectlab.winrt.runtime.KnownHResults
import io.github.kitectlab.winrt.runtime.ManagedComHostState
import io.github.kitectlab.winrt.runtime.ManagedReferenceHostSupport
import io.github.kitectlab.winrt.runtime.NativeCallbackHandle
import io.github.kitectlab.winrt.runtime.PlatformAbi
import io.github.kitectlab.winrt.runtime.RawAddress
import io.github.kitectlab.winrt.runtime.WinRtPlatformApi
import io.github.kitectlab.winrt.runtime.WinRtRestrictedErrorInfo
import io.github.kitectlab.winrt.runtime.WinRtUnsupportedOperationException
import io.github.kitectlab.winrt.runtime.asRawComPtr
import io.github.kitectlab.winrt.runtime.platformHResultFromThrowable

/**
 * Shared owner for the writable `IRestrictedErrorInfo` fallback path from
 * `.cswinrt/src/WinRT.Runtime/ExceptionHelpers.cs`.
 *
 * Kotlin/JVM cannot mirror CLR language-exception propagation, but it can still
 * round-trip restricted error details back into the WinRT global error state.
 */
internal class ManagedRestrictedErrorInfoComObject(
    private val hResult: HResult,
    private val errorInfo: WinRtRestrictedErrorInfo,
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
        val vtableMemory = PlatformAbi.allocatePointerArray(scope, 5)
        val callbacks = mutableListOf<NativeCallbackHandle>()
        val queryInterfaceCallback = callbackOf(IUnknownVftbl.QueryInterface) { args ->
            queryInterface(
                requestedInterfaceId = PlatformAbi.readGuid(args[1] as RawAddress),
                resultPointer = args[2] as RawAddress,
            )
        }
        val addRefCallback = callbackOf(IUnknownVftbl.AddRef) { addReference() }
        val releaseCallback = callbackOf(IUnknownVftbl.Release) { releaseReference() }
        val getErrorDetailsCallback =
            callbackOf(
                ComMethodSignature.of(
                    ComAbiValueKind.Pointer,
                    ComAbiValueKind.Pointer,
                    ComAbiValueKind.Pointer,
                    ComAbiValueKind.Pointer,
                ),
            ) { args ->
                getErrorDetails(
                    descriptionOut = args[1] as RawAddress,
                    hResultOut = args[2] as RawAddress,
                    restrictedDescriptionOut = args[3] as RawAddress,
                    capabilitySidOut = args[4] as RawAddress,
                )
            }
        val getReferenceCallback = callbackOf(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
            getReference(args[1] as RawAddress)
        }
        callbacks += queryInterfaceCallback
        callbacks += addRefCallback
        callbacks += releaseCallback
        callbacks += getErrorDetailsCallback
        callbacks += getReferenceCallback
        PlatformAbi.writePointerAt(vtableMemory, IUnknownVftblSlots.QueryInterface, queryInterfaceCallback.pointer)
        PlatformAbi.writePointerAt(vtableMemory, IUnknownVftblSlots.AddRef, addRefCallback.pointer)
        PlatformAbi.writePointerAt(vtableMemory, IUnknownVftblSlots.Release, releaseCallback.pointer)
        PlatformAbi.writePointerAt(vtableMemory, 3, getErrorDetailsCallback.pointer)
        PlatformAbi.writePointerAt(vtableMemory, 4, getReferenceCallback.pointer)
        PlatformAbi.writePointer(objectMemory, vtableMemory)
        return InterfaceEntry(objectMemory = objectMemory, callbacks = callbacks)
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
            PlatformAbi.writePointer(resultPointer, WinRtPlatformApi.sysAllocStringRaw(value))
            KnownHResults.S_OK.value
        }.getOrElse { failure ->
            platformHResultFromThrowable(failure).value
        }

    private fun cleanup() {
        registry.remove(PlatformAbi.pointerKey(interfaceEntry.objectMemory))
        interfaceEntry.callbacks.forEach(NativeCallbackHandle::close)
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
        private val registry = ConcurrentCacheMap<Long, ManagedRestrictedErrorInfoComObject>()
    }
}
