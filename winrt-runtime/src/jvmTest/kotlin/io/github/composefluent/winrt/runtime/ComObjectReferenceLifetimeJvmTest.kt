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
        assertEquals(1, cleanupCount.get())
    }

    private fun inspectableHost(cleanupCount: AtomicInteger): WinRtInspectableComObject =
        WinRtInspectableComObject(
            interfaceDefinitions = listOf(
                WinRtInspectableInterfaceDefinition(
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
