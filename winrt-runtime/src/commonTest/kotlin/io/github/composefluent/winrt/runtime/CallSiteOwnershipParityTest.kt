package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private object OwnedInspectableResultCallSite {
    @WinRTProjectionCallSite
    fun invoke(reference: ComObjectReference, slot: Int): InspectableReference =
        TODO("Lowered in the shared ownership parity fixture")
}

class CallSiteOwnershipParityTest {
    @Test
    fun owned_com_result_matches_reference_abi_transfer_balance() {
        OwnedInspectableGetterHost.create(KnownHResults.S_OK.value).use { host ->
            host.invokeReferenceAbi().use { result ->
                assertTrue(PlatformAbi.samePointer(result.pointer, host.resultPointer.asRawComPtr()))
                assertEquals(1, host.addRefCalls)
                assertEquals(0, host.releaseCalls)
                assertEquals(1, host.referenceCount)
            }
            assertEquals(1, host.releaseCalls)
            assertEquals(0, host.referenceCount)

            OwnedInspectableResultCallSite.invoke(host.receiver, GET_RESULT_SLOT).use { result ->
                assertTrue(PlatformAbi.samePointer(result.pointer, host.resultPointer.asRawComPtr()))
                assertEquals(2, host.addRefCalls)
                assertEquals(1, host.releaseCalls)
                assertEquals(1, host.referenceCount)
            }
            assertEquals(2, host.releaseCalls)
            assertEquals(0, host.referenceCount)
        }
    }

    @Test
    fun partially_initialized_owned_com_result_is_released_after_hresult_failure() {
        OwnedInspectableGetterHost.create(KnownHResults.E_FAIL.value).use { host ->
            val failure = assertFailsWith<WinRTRuntimeException> {
                OwnedInspectableResultCallSite.invoke(host.receiver, GET_RESULT_SLOT)
            }

            assertEquals(KnownHResults.E_FAIL, failure.hResult)
            assertEquals(1, host.addRefCalls)
            assertEquals(1, host.releaseCalls)
            assertEquals(0, host.referenceCount)
        }
    }
}

/** Mirrors the out-parameter FromAbi/DisposeAbi ownership split in .cswinrt code_writers.h. */
private class OwnedInspectableGetterHost private constructor(
    private val scope: NativeScope,
    private val getterCallback: NativeCallbackHandle,
    private val addRefCallback: NativeCallbackHandle,
    private val releaseCallback: NativeCallbackHandle,
    val receiver: ComObjectReference,
    val resultPointer: RawAddress,
) : AutoCloseable {
    var addRefCalls: Int = 0
        private set

    var releaseCalls: Int = 0
        private set

    var referenceCount: Int = 0
        private set

    fun invokeReferenceAbi(): InspectableReference =
        PlatformAbi.confinedScope().use { callScope ->
            val resultOut = PlatformAbi.allocatePointerSlot(callScope)
            val hResult = ComVtableInvoker.invokeArgs(
                instance = receiver.pointer,
                slot = GET_RESULT_SLOT,
                arg0 = resultOut,
            )
            val result = PlatformAbi.readPointer(resultOut)
            try {
                HResult(hResult).requireSuccess()
            } catch (error: Throwable) {
                if (!PlatformAbi.isNull(result)) {
                    WinRTPlatformApi.releaseRaw(result)
                }
                throw error
            }
            InspectableReference(PlatformAbi.toRawComPtr(result))
        }

    override fun close() {
        receiver.close()
        getterCallback.close()
        releaseCallback.close()
        addRefCallback.close()
        scope.close()
    }

    companion object {
        fun create(hResult: Int): OwnedInspectableGetterHost {
            val scope = PlatformAbi.confinedScope()
            lateinit var host: OwnedInspectableGetterHost

            val addRefCallback = ComAbiInteropBridge.createRawInt32Callback(
                listOf(ComAbiValueKind.Pointer),
            ) {
                host.addRefCalls += 1
                host.referenceCount += 1
                host.referenceCount
            }
            val releaseCallback = ComAbiInteropBridge.createRawInt32Callback(
                listOf(ComAbiValueKind.Pointer),
            ) {
                host.releaseCalls += 1
                host.referenceCount -= 1
                check(host.referenceCount >= 0) { "Owned COM output was released more than once." }
                host.referenceCount
            }
            val resultVtable = PlatformAbi.allocatePointerArray(scope, IUnknownVftblSlots.Release + 1)
            PlatformAbi.writePointerAt(resultVtable, IUnknownVftblSlots.AddRef, addRefCallback.pointer)
            PlatformAbi.writePointerAt(resultVtable, IUnknownVftblSlots.Release, releaseCallback.pointer)
            val resultPointer = PlatformAbi.allocatePointerSlot(scope)
            PlatformAbi.writePointer(resultPointer, resultVtable)

            val getterCallback = ComAbiInteropBridge.createRawInt32Callback(
                listOf(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
            ) { args ->
                WinRTPlatformApi.addRefRaw(resultPointer)
                PlatformAbi.writePointer(args[1] as RawAddress, resultPointer)
                hResult
            }
            val receiverVtable = PlatformAbi.allocatePointerArray(scope, GET_RESULT_SLOT + 1)
            PlatformAbi.writePointerAt(receiverVtable, GET_RESULT_SLOT, getterCallback.pointer)
            val receiverPointer = PlatformAbi.allocatePointerSlot(scope)
            PlatformAbi.writePointer(receiverPointer, receiverVtable)
            val receiver = ComObjectReference(
                pointer = receiverPointer.asRawComPtr(),
                interfaceId = IID.IInspectable,
                preventReleaseOnDispose = true,
            )

            host = OwnedInspectableGetterHost(
                scope = scope,
                getterCallback = getterCallback,
                addRefCallback = addRefCallback,
                releaseCallback = releaseCallback,
                receiver = receiver,
                resultPointer = resultPointer,
            )
            return host
        }
    }
}

private fun RawAddress.asRawComPtr(): RawComPtr = PlatformAbi.toRawComPtr(this)

private const val GET_RESULT_SLOT: Int = 6
