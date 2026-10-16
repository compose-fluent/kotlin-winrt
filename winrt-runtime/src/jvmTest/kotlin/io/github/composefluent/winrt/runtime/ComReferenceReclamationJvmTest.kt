package io.github.composefluent.winrt.runtime

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComReferenceReclamationJvmTest {
    @Test
    fun a_collected_reference_burst_releases_every_owner_once() {
        // CLR ComWrappers owns native reference reclamation. JVM's bounded
        // queue workers must preserve once-only release across a GC burst.
        val count = 4096
        val released = AtomicInteger()
        val done = CountDownLatch(count)
        fun abandonOwners() {
            repeat(count) {
                createComPtrFinalizationRegistration(
                    Any(),
                    RawComObjectReferenceSupport(
                        pointer = RawComPtr(1),
                        interfaceIdLowBits = IID.IUnknown.abiLowBits,
                        interfaceIdHighBits = IID.IUnknown.abiHighBits,
                        knownInterfaceId = IID.IUnknown,
                        preventReleaseOnDispose = true,
                        trackContext = false,
                        afterRelease = { released.incrementAndGet(); done.countDown() },
                    ),
                )
            }
        }
        abandonOwners()
        repeat(20) {
            if (done.count != 0L) {
                System.gc()
                done.await(250, TimeUnit.MILLISECONDS)
            }
        }
        assertTrue(done.await(5, TimeUnit.SECONDS), "Unreleased owners: ${done.count}")
        assertEquals(count, released.get())
    }
}
