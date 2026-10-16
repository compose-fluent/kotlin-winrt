package io.github.composefluent.winrt.runtime

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import windows.foundation.EventHandler
import windows.foundation.EventRegistrationToken

class EventSourceShutdownApartmentJvmTest {
    // The thread that calls System.exit waits for the shutdown hooks and serves no call into its
    // apartment meanwhile. The publisher here belongs to such a thread: a single-threaded apartment
    // that has stopped pumping.
    @Test
    fun shutdown_cleanup_leaves_the_publisher_of_another_apartment_to_its_thread() {
        if (!PlatformRuntime.isWindows) {
            return
        }
        EventSourceCache.clearForTests()
        EventSourceShutdownRegistry.clearForTests()

        val subscribed = CountDownLatch(1)
        val shutdownRan = CountDownLatch(1)
        val removals = AtomicInteger()
        val callableOnOwnThread = AtomicReference<Boolean?>()
        val publisherReference = AtomicReference<ComObjectReference?>()
        val publisherDelegate = AtomicReference<RawAddress?>()
        val apartmentFailure = AtomicReference<Throwable?>()
        val apartment = Thread {
            try {
                RuntimeScope.initializeSingleThreaded().use {
                    val publisher = createApartmentBoundObject()
                    try {
                        callableOnOwnThread.set(publisher.isCallableInCurrentContext)
                        publisherReference.set(publisher)
                        val source = EventHandlerEventSource<Int>(
                            objectReference = publisher,
                            interfaceId = eventInterfaceId,
                            argsKind = WinRTDelegateValueKind.INT32,
                            // The publisher holds the delegate, which keeps the registration alive.
                            addHandler = { _, delegate ->
                                publisherDelegate.set(delegate.acquireOwnedPointer().asRawAddress())
                                EventRegistrationToken(0x79790000_00000001)
                            },
                            removeHandler = { _, _ ->
                                removals.incrementAndGet()
                                publisherDelegate.getAndSet(null)?.let(WinRTPlatformApi::releaseRaw)
                            },
                            index = 79,
                        )
                        val handler = EventHandler<Int> { _, _ -> }
                        source.subscribe(handler)
                        subscribed.countDown()
                        // No message is pumped while the shutdown cleanup runs.
                        check(shutdownRan.await(60, TimeUnit.SECONDS)) { "The shutdown cleanup did not finish." }
                        source.unsubscribe(handler)
                    } finally {
                        publisherDelegate.getAndSet(null)?.let(WinRTPlatformApi::releaseRaw)
                        publisherReference.set(null)
                        publisher.close()
                    }
                }
            } catch (error: Throwable) {
                apartmentFailure.set(error)
            } finally {
                subscribed.countDown()
            }
        }
        apartment.start()

        val callableOnShutdownThread = AtomicReference<Boolean?>()
        val removalsAfterShutdown = AtomicInteger(-1)
        val shutdownFailure = AtomicReference<Throwable?>()
        val shutdown = Thread {
            try {
                RuntimeScope.initializeMultithreaded().use {
                    callableOnShutdownThread.set(publisherReference.get()?.isCallableInCurrentContext)
                    EventSourceShutdownRegistry.closeAllForTests()
                    removalsAfterShutdown.set(removals.get())
                }
            } catch (error: Throwable) {
                shutdownFailure.set(error)
            }
        }
        try {
            assertTrue(subscribed.await(60, TimeUnit.SECONDS), "The apartment thread did not subscribe.")
            assertNull(apartmentFailure.get())
            shutdown.start()
            shutdown.join(30_000)
            assertFalse(shutdown.isAlive, "The shutdown cleanup waits for an apartment that does not answer.")
        } finally {
            shutdownRan.countDown()
            apartment.join(60_000)
            EventSourceCache.clearForTests()
            EventSourceShutdownRegistry.clearForTests()
        }

        assertNull(shutdownFailure.get())
        assertNull(apartmentFailure.get())
        assertFalse(apartment.isAlive)
        assertEquals(true, callableOnOwnThread.get())
        assertEquals(false, callableOnShutdownThread.get(), "The fixture needs an apartment-bound publisher.")
        assertEquals(0, removalsAfterShutdown.get(), "The shutdown cleanup must not call into another apartment.")
        // The registration stayed whole: its own thread still removes it.
        assertEquals(1, removals.get())
    }

    // An in-process object of an apartment-threaded class: it is neither agile nor free-threaded,
    // so a reference to it is bound to the apartment of the thread that creates it.
    private fun createApartmentBoundObject(): ComObjectReference {
        val result = WinRTPlatformApi.coCreateInstanceRaw(shellLinkClassId, IID.IUnknown)
        HResult(result.hResultValue).requireSuccess("CoCreateInstance(CLSID_ShellLink)")
        return ComObjectReference(result.pointer.asRawComPtr(), IID.IUnknown)
    }

    private companion object {
        val shellLinkClassId = Guid("00021401-0000-0000-C000-000000000046")
        val eventInterfaceId = Guid("79797979-7979-7979-7979-797979797979")
    }
}
