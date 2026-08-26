package io.github.composefluent.winrt.runtime

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.concurrent.atomics.ExperimentalAtomicApi

class ManagedComHostStateJvmTest {
    @Test
    fun multi_interface_host_registers_one_canonical_inbound_binding() {
        val initialBindingCount = managedComInboundBindings.size
        val secondaryInterfaceId = Guid("620fb93f-5b6f-460f-b924-56ee757aee07")
        val primaryInterfaceId = Guid("c2332430-428b-4dc7-92f2-5e970a47fc82")
        val managedValue = Any()

        WinRTInspectableComObject(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(
                    interfaceId = secondaryInterfaceId,
                    methods = emptyList(),
                ),
                WinRTInspectableInterfaceDefinition(
                    interfaceId = primaryInterfaceId,
                    methods = emptyList(),
                ),
            ),
            defaultInterfaceId = primaryInterfaceId,
            managedValue = managedValue,
        ).use { host ->
            val primaryPointer = host.borrowCachedInterfacePointer(primaryInterfaceId)
            val secondaryPointer = host.borrowCachedInterfacePointer(secondaryInterfaceId)

            assertEquals(initialBindingCount + 1, managedComInboundBindings.size)
            assertSame(managedValue, managedComInboundBindings[primaryPointer.value]?.get())
            assertNull(managedComInboundBindings[secondaryPointer.value])
            assertEquals(
                primaryPointer,
                PlatformAbi.readPointerAt(secondaryPointer, managedComInboundBindingSlot),
            )
            assertSame(
                managedValue,
                platformTryWinRTProjectionInboundBinding(primaryPointer.value)?.get(),
            )
            assertSame(
                managedValue,
                platformTryWinRTProjectionInboundBinding(secondaryPointer.value)?.get(),
            )

            PlatformAbi.confinedScope().use { scope ->
                val externalVtable = PlatformAbi.allocatePointerArray(scope, 3)
                val externalObject = PlatformAbi.allocatePointerArray(scope, 4)
                PlatformAbi.writePointer(externalObject, externalVtable)
                PlatformAbi.writePointerAt(
                    externalObject,
                    managedComInboundBindingSlot,
                    primaryPointer,
                )

                assertNull(platformTryWinRTProjectionInboundBinding(externalObject.value))
            }
        }

        assertEquals(initialBindingCount, managedComInboundBindings.size)
    }

    @Test
    @OptIn(ExperimentalAtomicApi::class)
    fun concurrent_publish_cannot_restore_a_closed_hot_binding() {
        val host = WinRTInspectableComObject.inspectableBox(Any())
        val pointer = host.borrowCachedInterfacePointer(IID.IInspectable)
        val binding = checkNotNull(winRTProjectionInboundBinding(pointer.value))
        val start = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val publishers = List(6) {
            thread(start = true) {
                try {
                    assertTrue(start.await(5, TimeUnit.SECONDS))
                    repeat(10_000) {
                        binding.publishHotEntry(pointer.value)
                        Thread.yield()
                    }
                } catch (error: Throwable) {
                    failure.compareAndSet(null, error)
                }
            }
        }
        val closer = thread(start = true) {
            try {
                assertTrue(start.await(5, TimeUnit.SECONDS))
                host.close()
            } catch (error: Throwable) {
                failure.compareAndSet(null, error)
            }
        }

        start.countDown()
        publishers.forEach(Thread::join)
        closer.join()
        failure.get()?.let { throw it }

        assertFalse(binding.publishHotEntry(pointer.value))
        assertTrue(managedComInboundHotEntry.load()?.binding !== binding)
        host.close()
    }

    @Test
    fun concurrent_add_waits_until_root_release_is_fully_published() {
        val root = BlockingRootReference()
        val state = ManagedComHostState(cleanup = {}, rootReference = root)
        assertEquals(2, state.addReference())

        val releaseResult = AtomicInteger()
        val releaseThread = thread(start = true) {
            releaseResult.set(state.releaseReference())
        }
        assertTrue(root.unpinEntered.await(5, TimeUnit.SECONDS))

        val addResult = AtomicInteger()
        val addDone = CountDownLatch(1)
        val addThread = thread(start = true) {
            addResult.set(state.addReference())
            addDone.countDown()
        }

        assertFalse(addDone.await(100, TimeUnit.MILLISECONDS))
        root.allowUnpin.countDown()
        assertTrue(addDone.await(5, TimeUnit.SECONDS))
        releaseThread.join()
        addThread.join()

        assertEquals(1, releaseResult.get())
        assertEquals(2, addResult.get())
        assertTrue(root.pinned.get())
        assertEquals(1, state.releaseReference())
        assertFalse(root.pinned.get())
        assertEquals(0, state.releaseReference())
    }

    @Test
    fun concurrent_borrow_ready_calls_converge_to_the_unpinned_baseline() {
        val root = ConcurrentRecordingRootReference()
        val state = ManagedComHostState(cleanup = {}, rootReference = root, borrowReady = true)
        val managedValue = Any()
        val start = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val workers = List(6) {
            thread(start = true) {
                try {
                    assertTrue(start.await(5, TimeUnit.SECONDS))
                    repeat(2_000) {
                        assertTrue(state.tryBeginBorrowedCall(managedValue))
                        assertTrue(state.addBorrowedReference() > 0)
                        Thread.yield()
                        state.releaseReference()
                        state.endBorrowedCall(managedValue)
                    }
                } catch (error: Throwable) {
                    failure.compareAndSet(null, error)
                }
            }
        }

        start.countDown()
        workers.forEach(Thread::join)
        failure.get()?.let { throw it }

        assertEquals(1, state.currentReferenceCount())
        assertFalse(root.pinned.get())
        assertEquals(root.pinCalls.get(), root.unpinCalls.get())
        assertEquals(0, state.releaseBaselineReference())
    }

    private class BlockingRootReference : ManagedComRootReference {
        val pinned = AtomicBoolean(false)
        val unpinEntered = CountDownLatch(1)
        val allowUnpin = CountDownLatch(1)

        override fun tryPin(knownManagedValue: Any?): Boolean {
            pinned.set(true)
            return true
        }

        override fun unpin() {
            unpinEntered.countDown()
            assertTrue(allowUnpin.await(5, TimeUnit.SECONDS))
            pinned.set(false)
        }
    }

    private class ConcurrentRecordingRootReference : ManagedComRootReference {
        val pinned = AtomicBoolean(false)
        val pinCalls = AtomicInteger()
        val unpinCalls = AtomicInteger()

        override fun tryPin(knownManagedValue: Any?): Boolean {
            assertTrue(pinned.compareAndSet(false, true))
            pinCalls.incrementAndGet()
            return true
        }

        override fun unpin() {
            assertTrue(pinned.compareAndSet(true, false))
            unpinCalls.incrementAndGet()
        }
    }
}
