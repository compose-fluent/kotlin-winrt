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
