package io.github.composefluent.winrt.runtime

import java.lang.ref.Reference
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComPtrReclamationJvmTest {
    @Test
    fun queued_reclamation_and_explicit_close_release_exactly_once() {
        // CsWinRT Lifecycle.DisposeUnsafe is shared by explicit disposal and finalization.
        val target = Any()
        val releases = AtomicInteger()
        val support = support { releases.incrementAndGet() }
        val registration = createComPtrFinalizationRegistration(target, support)
        runReferenceContentionWorkers(8) { worker ->
            if (worker == 0) (registration as Reference<*>).enqueue()
            repeat(100) { closeComPtrFinalizationRegistration(registration, support) }
        }
        assertEquals(1, releases.get())
        Reference.reachabilityFence(target)
    }

    @Test
    fun abandoned_owner_releases_without_further_runtime_activity() {
        val released = CountDownLatch(1)
        val target = abandon(released)
        repeat(40) {
            System.gc()
            if (released.await(100, TimeUnit.MILLISECONDS)) {
                assertNull(target.get())
                return
            }
        }
        assertTrue(false, "Unreachable COM owner was not reclaimed by the shared queue")
    }

    @Test
    fun closed_registration_does_not_retain_its_support() {
        val (registration, state) = closedRegistration()
        repeat(40) {
            System.gc()
            if (state.get() == null) {
                Reference.reachabilityFence(registration)
                return
            }
            Thread.sleep(25)
        }
        assertNull(state.get())
        Reference.reachabilityFence(registration)
    }

    private fun abandon(released: CountDownLatch): WeakReference<Any> {
        val target = Any()
        createComPtrFinalizationRegistration(target, support { released.countDown() })
        return WeakReference(target)
    }

    private fun closedRegistration(): Pair<Any, WeakReference<RawComObjectReferenceSupport>> {
        val support = support {}
        val registration = createComPtrFinalizationRegistration(Any(), support)
        closeComPtrFinalizationRegistration(registration, support)
        return Pair(registration, WeakReference(support))
    }

    private fun support(release: () -> Unit) = RawComObjectReferenceSupport(
        RawComPtr(1), IID.IUnknown.abiLowBits, IID.IUnknown.abiHighBits, IID.IUnknown,
        preventReleaseOnDispose = true, trackContext = false, afterRelease = release,
    )
}
