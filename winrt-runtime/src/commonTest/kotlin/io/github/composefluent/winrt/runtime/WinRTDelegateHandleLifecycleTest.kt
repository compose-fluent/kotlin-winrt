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
