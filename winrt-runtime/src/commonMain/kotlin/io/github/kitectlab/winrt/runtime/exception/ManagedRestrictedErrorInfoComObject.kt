package io.github.kitectlab.winrt.runtime.exception

import io.github.kitectlab.winrt.runtime.ConcurrentCacheMap
import io.github.kitectlab.winrt.runtime.Guid
import io.github.kitectlab.winrt.runtime.HResult
import io.github.kitectlab.winrt.runtime.IID
import io.github.kitectlab.winrt.runtime.IUnknownReference
import io.github.kitectlab.winrt.runtime.IUnknownVftblSlots
import io.github.kitectlab.winrt.runtime.KnownHResults
import io.github.kitectlab.winrt.runtime.ManagedComHostState
import io.github.kitectlab.winrt.runtime.ManagedReferenceHostSupport
import io.github.kitectlab.winrt.runtime.NativeCallbackHandle
import io.github.kitectlab.winrt.runtime.NativeFunctionDescriptor
import io.github.kitectlab.winrt.runtime.NativeInterop
import io.github.kitectlab.winrt.runtime.NativePointer
import io.github.kitectlab.winrt.runtime.NativeValueLayout
import io.github.kitectlab.winrt.runtime.WinRtPlatformApi
import io.github.kitectlab.winrt.runtime.WinRtRestrictedErrorInfo
import io.github.kitectlab.winrt.runtime.WinRtUnsupportedOperationException
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
    private val scope = NativeInterop.sharedScope()
    private val state = ManagedComHostState(::cleanup)
    private val interfaceEntry = createInterfaceEntry()

    init {
        registry[NativeInterop.pointerKey(interfaceEntry.objectMemory)] = this
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
        return IUnknownReference(interfaceEntry.objectMemory, IID.IRestrictedErrorInfo)
    }

    private fun releaseManagedReference() {
        releaseReference()
    }

    private fun createInterfaceEntry(): InterfaceEntry {
        val objectMemory = NativeInterop.allocatePointerSlot(scope)
        val vtableMemory = NativeInterop.allocatePointerArray(scope, 5)
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
        val getErrorDetailsCallback = callbackOf(
            NativeFunctionDescriptor.of(
                NativeValueLayout.JAVA_INT,
                NativeValueLayout.ADDRESS,
                NativeValueLayout.ADDRESS,
                NativeValueLayout.ADDRESS,
                NativeValueLayout.ADDRESS,
                NativeValueLayout.ADDRESS,
            ),
        ) { args ->
            getErrorDetails(
                descriptionOut = args[1] as NativePointer,
                hResultOut = args[2] as NativePointer,
                restrictedDescriptionOut = args[3] as NativePointer,
                capabilitySidOut = args[4] as NativePointer,
            )
        }
        val getReferenceCallback = callbackOf(
            NativeFunctionDescriptor.of(
                NativeValueLayout.JAVA_INT,
                NativeValueLayout.ADDRESS,
                NativeValueLayout.ADDRESS,
            ),
        ) { args ->
            getReference(args[1] as NativePointer)
        }
        callbacks += queryInterfaceCallback
        callbacks += addRefCallback
        callbacks += releaseCallback
        callbacks += getErrorDetailsCallback
        callbacks += getReferenceCallback
        NativeInterop.writePointerAt(vtableMemory, IUnknownVftblSlots.QueryInterface, queryInterfaceCallback.pointer)
        NativeInterop.writePointerAt(vtableMemory, IUnknownVftblSlots.AddRef, addRefCallback.pointer)
        NativeInterop.writePointerAt(vtableMemory, IUnknownVftblSlots.Release, releaseCallback.pointer)
        NativeInterop.writePointerAt(vtableMemory, 3, getErrorDetailsCallback.pointer)
        NativeInterop.writePointerAt(vtableMemory, 4, getReferenceCallback.pointer)
        NativeInterop.writePointer(objectMemory, vtableMemory)
        return InterfaceEntry(objectMemory = objectMemory, callbacks = callbacks)
    }

    private fun queryInterface(
        requestedInterfaceId: Guid,
        resultPointer: NativePointer,
    ): Int {
        val queryResult = state.queryInterface(requestedInterfaceId) { requested ->
            when (requested) {
                IID.IUnknown,
                IID.IRestrictedErrorInfo,
                -> interfaceEntry.objectMemory

                else -> null
            }
        }
        NativeInterop.writePointer(resultPointer, queryResult.target ?: NativeInterop.nullPointer)
        return queryResult.hResult.value
    }

    private fun addReference(): Int = state.addReference()

    private fun releaseReference(): Int = state.releaseReference()

    private fun getErrorDetails(
        descriptionOut: NativePointer,
        hResultOut: NativePointer,
        restrictedDescriptionOut: NativePointer,
        capabilitySidOut: NativePointer,
    ): Int {
        NativeInterop.writePointer(descriptionOut, NativeInterop.nullPointer)
        NativeInterop.writeInt32(hResultOut, hResult.value)
        NativeInterop.writePointer(restrictedDescriptionOut, NativeInterop.nullPointer)
        NativeInterop.writePointer(capabilitySidOut, NativeInterop.nullPointer)
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

    private fun getReference(resultPointer: NativePointer): Int {
        NativeInterop.writePointer(resultPointer, NativeInterop.nullPointer)
        return writeBstr(resultPointer, errorInfo.reference)
    }

    private fun writeBstr(
        resultPointer: NativePointer,
        value: String?,
    ): Int =
        runCatching {
            NativeInterop.writePointer(resultPointer, WinRtPlatformApi.sysAllocStringRaw(value))
            KnownHResults.S_OK.value
        }.getOrElse { failure ->
            platformHResultFromThrowable(failure).value
        }

    private fun cleanup() {
        registry.remove(NativeInterop.pointerKey(interfaceEntry.objectMemory))
        interfaceEntry.callbacks.forEach(NativeCallbackHandle::close)
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
        private val registry = ConcurrentCacheMap<Long, ManagedRestrictedErrorInfoComObject>()
    }
}
