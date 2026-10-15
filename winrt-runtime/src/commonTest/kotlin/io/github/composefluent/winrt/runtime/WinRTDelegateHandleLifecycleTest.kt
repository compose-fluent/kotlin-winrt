package io.github.composefluent.winrt.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@OptIn(ExperimentalAtomicApi::class)
class WinRTDelegateHandleLifecycleTest {
    @Test
    fun delegate_output_references_keep_independent_ownership_after_handle_close() {
        // CsWinRT 3 ObjectReferenceValue.DetachThisPtrUnsafe transfers one native reference.
        // Each ABI recipient must keep the callback alive independently of the managed handle.
        val cleanupCalls = AtomicInt(0)
        val handle = WinRTDelegateBridge.createDelegate(
            iid = Guid("b2602874-f9cb-4074-a3c9-14279f64c776"),
            parameterKinds = emptyList(),
            returnKind = WinRTDelegateValueKind.INT32,
        ) { 37 }.also { it.addCleanupAction { cleanupCalls.fetchAndAdd(1) } }
        val first = requireNotNull(
            WinRTDelegateReference.fromAbi(MarshalDelegate.fromManaged(handle), handle.descriptor),
        )
        try {
            val second = requireNotNull(
                WinRTDelegateReference.fromAbi(MarshalDelegate.fromManaged(handle), handle.descriptor),
            )
            second.use {
                handle.close()
                PlatformFinalization.drain()
                assertEquals(0, cleanupCalls.load())
                assertEquals(37, first.invoke(emptyList()))
                first.close()
                assertEquals(0, cleanupCalls.load())
                assertEquals(37, second.invoke(emptyList()))
            }
            assertEquals(1, cleanupCalls.load())
        } finally {
            first.close()
            handle.close()
        }
        assertEquals(1, cleanupCalls.load())
    }

    @Test
    fun delegate_output_rejects_closed_handles_and_preserves_null() {
        assertEquals(PlatformAbi.nullPointer, MarshalDelegate.fromManaged(null))
        assertEquals(PlatformAbi.nullPointer, MarshalDelegate.fromProjected(null))
        val handle = WinRTDelegateBridge.createUnitDelegate(
            iid = Guid("b2602874-f9cb-4074-a3c9-14279f64c777"),
            parameterKinds = emptyList(),
        ) {}
        handle.close()
        assertFailsWith<WinRTObjectDisposedException> { MarshalDelegate.fromManaged(handle) }
    }

    @Test
    fun delegate_array_releases_each_owned_reference_on_close() {
        // CsWinRT 3 WindowsRuntimeUnknownArrayMarshaller.Free releases every element.
        val cleanupCalls = AtomicInt(0)
        val handle = WinRTDelegateBridge.createUnitDelegate(
            iid = Guid("b2602874-f9cb-4074-a3c9-14279f64c778"),
            parameterKinds = emptyList(),
        ) {}.also { it.addCleanupAction { cleanupCalls.fetchAndAdd(1) } }
        try {
            requireNotNull(MarshalDelegate.fromManagedArray(arrayOf(handle, null, handle))).use { array ->
                assertEquals(3, array.length)
                assertEquals(PlatformAbi.nullPointer, PlatformAbi.readPointerAt(array.data, 1))
                handle.close()
                assertEquals(0, cleanupCalls.load())
            }
            assertEquals(1, cleanupCalls.load())
        } finally {
            handle.close()
        }
    }

    @Test
    fun delegate_array_rolls_back_references_when_a_later_element_is_closed() {
        // CsWinRT 3 WindowsRuntimeReferenceTypeArrayMarshaller.ConvertToUnmanaged releases
        // the completed prefix if a later element cannot be marshaled.
        val cleanupCalls = AtomicInt(0)
        val handle = WinRTDelegateBridge.createUnitDelegate(
            iid = Guid("b2602874-f9cb-4074-a3c9-14279f64c779"),
            parameterKinds = emptyList(),
        ) {}.also { it.addCleanupAction { cleanupCalls.fetchAndAdd(1) } }
        val closedHandle = WinRTDelegateBridge.createUnitDelegate(
            iid = Guid("b2602874-f9cb-4074-a3c9-14279f64c77a"),
            parameterKinds = emptyList(),
        ) {}
        closedHandle.close()
        try {
            assertFailsWith<WinRTObjectDisposedException> {
                MarshalDelegate.fromManagedArray(arrayOf(handle, null, handle, closedHandle))?.close()
            }
            assertEquals(0, cleanupCalls.load())
        } finally {
            handle.close()
        }
        assertEquals(1, cleanupCalls.load())
    }

    @Test
    fun object_delegate_marshaler_preserves_escaped_ownership_after_close() {
        // CsWinRT 3 WindowsRuntimeObjectMarshaller requests IInspectable from the managed CCW.
        // Disposing that input owner must leave another interface's native reference callable.
        val descriptor = WinRTDelegateDescriptor(
            interfaceId = Guid("b2602874-f9cb-4074-a3c9-14279f64c774"),
            parameterKinds = emptyList(),
            returnKind = WinRTDelegateValueKind.INT32,
        )
        val cleanupCalls = AtomicInt(0)
        val marshaler = WinRTObjectMarshaller.createMarshaler(projectedDelegate(descriptor, cleanupCalls))
        try {
            ComObjectReference(marshaler.abi.asRawComPtr(), IID.IInspectable, preventReleaseOnDispose = true).use { borrowed ->
                borrowed.queryInterface(IID.IInspectable).getOrThrow().use { inspectable ->
                    assertEquals(marshaler.abi, inspectable.pointer.asRawAddress())
                }
                borrowed.queryInterface(descriptor.interfaceId).getOrThrow().use { retained ->
                    marshaler.close()
                    marshaler.close()
                    PlatformFinalization.drain()
                    assertEquals(0, cleanupCalls.load())
                    WinRTDelegateReference(retained.comPtr, descriptor).use { callable ->
                        assertEquals(37, callable.invoke(emptyList()))
                    }
                }
            }
            assertEquals(1, cleanupCalls.load())
        } finally {
            marshaler.close()
        }
        assertEquals(1, cleanupCalls.load())
    }

    @Test
    fun abandoned_object_delegate_marshaler_releases_its_owned_reference() {
        // Kotlin's public AutoCloseable marshaler preserves its existing Cleaner fallback
        // in addition to CsWinRT ObjectReferenceValue's deterministic Dispose contract.
        val cleanupCalls = AtomicInt(0)
        val abandoned = abandonObjectDelegateMarshaler(cleanupCalls)
        for (attempt in 0 until 20) {
            PlatformFinalization.drain()
            if (abandoned.get() == null && cleanupCalls.load() == 1) break
        }
        assertNull(abandoned.get())
        assertEquals(1, cleanupCalls.load())
    }

    private fun abandonObjectDelegateMarshaler(
        cleanupCalls: AtomicInt,
    ): PlatformManagedWeakReference<WinRTObjectMarshaler> {
        val descriptor = WinRTDelegateDescriptor(
            interfaceId = Guid("b2602874-f9cb-4074-a3c9-14279f64c775"),
            parameterKinds = emptyList(),
            returnKind = WinRTDelegateValueKind.INT32,
        )
        val marshaler = WinRTObjectMarshaller.createMarshaler(projectedDelegate(descriptor, cleanupCalls))
        return PlatformManagedWeakReference(marshaler)
    }

    private fun projectedDelegate(
        descriptor: WinRTDelegateDescriptor,
        cleanupCalls: AtomicInt,
    ): WinRTProjectedDelegate = object : WinRTProjectedDelegate {
        override fun createWinRTDelegateHandle(): WinRTDelegateHandle =
            WinRTDelegateBridge.createDelegate(
                iid = descriptor.interfaceId,
                parameterKinds = descriptor.parameterKinds,
                returnKind = descriptor.returnKind,
            ) { 37 }.also { handle ->
                handle.addCleanupAction { cleanupCalls.fetchAndAdd(1) }
            }
    }

    @Test
    fun concurrent_close_and_native_handoff_release_the_managed_ownership_once() = runBlocking {
        // CsWinRT 3 WindowsRuntimeObjectReference.Lifecycle uses independent bits
        // in one atomic word. Disposal must preserve a native caller's COM owner.
        val cleanupCalls = AtomicInt(0)
        val secondCleanupCalls = AtomicInt(0)
        val handle = WinRTDelegateBridge.createDelegate(
            iid = Guid("b2602874-f9cb-4074-a3c9-14279f64c773"),
            parameterKinds = emptyList(),
            returnKind = WinRTDelegateValueKind.INT32,
        ) { 37 }
        handle.addCleanupAction {
            cleanupCalls.fetchAndAdd(1)
            handle.close() // Cleanup may reenter an already claimed disposal.
        }
        handle.addCleanupAction { secondCleanupCalls.fetchAndAdd(1) }
        try {
            handle.createReference().use { reference ->
                val start = CompletableDeferred<Unit>()
                val workers = List(4) { index ->
                    async(Dispatchers.Default) {
                        start.await()
                        repeat(64) {
                            if (index % 2 == 0) handle.close()
                            else handle.releaseManagedReferenceForNativeOwnership()
                        }
                    }
                }
                start.complete(Unit)
                workers.awaitAll()
                assertFailsWith<WinRTObjectDisposedException> { handle.createReference() }
                assertNull(handle.tryAcquireMarshalingReference())
                assertEquals(37, reference.invoke(emptyList()))
                assertEquals(0, cleanupCalls.load())
                assertEquals(0, secondCleanupCalls.load())
            }
            assertEquals(1, cleanupCalls.load())
            assertEquals(1, secondCleanupCalls.load())
        } finally {
            handle.close()
        }
        assertEquals(1, cleanupCalls.load())
        assertEquals(1, secondCleanupCalls.load())
    }
}
