package io.github.composefluent.winrt.runtime

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComObjectReferenceLifetimeJvmTest {
    @Test
    fun owned_reference_close_is_idempotent_and_releases_managed_host_once() {
        val cleanupCount = AtomicInteger(0)
        val host = inspectableHost(cleanupCount)
        val reference = IInspectableReference(host.detachReference(IID.IInspectable).asRawComPtr())

        reference.close()
        reference.close()

        assertTrue(reference.isDisposed)
        assertEquals(1, cleanupCount.get())
    }

    @Test
    fun concurrent_owned_reference_close_releases_managed_host_once() {
        val cleanupCount = AtomicInteger(0)
        val host = inspectableHost(cleanupCount)
        val reference = IInspectableReference(host.detachReference(IID.IInspectable).asRawComPtr())
        val child = acquireInterfaceReference(reference, IID.IInspectable)
        val threadCount = 8
        val ready = CountDownLatch(threadCount)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threadCount)
        val failures = mutableListOf<Throwable>()

        val threads = List(threadCount) {
            Thread {
                try {
                    ready.countDown()
                    start.await()
                    reference.close()
                } catch (error: Throwable) {
                    synchronized(failures) {
                        failures += error
                    }
                } finally {
                    done.countDown()
                }
            }
        }
        threads.forEach(Thread::start)
        ready.await()
        start.countDown()
        done.await()
        threads.forEach(Thread::join)

        assertEquals(emptyList(), failures)
        assertTrue(reference.isDisposed)
        assertTrue(child.isDisposed)
        assertEquals(1, cleanupCount.get())
    }

    @Test
    fun interface_registration_racing_parent_close_does_not_leak() {
        repeat(32) {
            val cleanupCount = AtomicInteger(0)
            val host = inspectableHost(cleanupCount)
            val parent = IInspectableReference(host.detachReference(IID.IInspectable).asRawComPtr())
            val child = parent.queryInterface(IID.IInspectable).getOrThrow()
            val start = CountDownLatch(1)
            var failure: Throwable? = null
            val registration = Thread {
                start.await()
                try {
                    parent.ownInterfaceReference(child)
                } catch (_: WinRTObjectDisposedException) {
                    // Registration lost the race and must have closed the child.
                } catch (error: Throwable) {
                    failure = error
                }
            }
            registration.start()
            start.countDown()
            parent.close()
            registration.join()

            assertEquals(null, failure)
            assertTrue(child.isDisposed)
            assertEquals(1, cleanupCount.get())
        }
    }

    private fun inspectableHost(cleanupCount: AtomicInteger): WinRTInspectableComObject =
        WinRTInspectableComObject(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(
                    interfaceId = IID.IInspectable,
                    methods = emptyList(),
                ),
            ),
            defaultInterfaceId = IID.IInspectable,
            runtimeClassName = "test.Lifetime",
            cleanupAction = {
                cleanupCount.incrementAndGet()
            },
        )
}
