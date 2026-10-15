package io.github.composefluent.winrt.runtime

import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JvmWeakReferenceConcurrencyTest {
    @Test
    fun concurrent_first_use_of_weak_reference_lock_is_mutually_exclusive() {
        // Same platform-weak-storage and atomic owner-retargeting contract as Native;
        // the JVM owner now also defers allocating its lock until first use.
        val reference = WeakReference<Any>()
        val targets = Array(4) { Any() }
        val ready = CyclicBarrier(4)
        val lock = PlatformWeakReferenceLock()
        val active = AtomicInteger()
        val executor = Executors.newFixedThreadPool(4)
        try {
            val jobs = targets.map { target ->
                executor.submit(Callable {
                    ready.await(10, TimeUnit.SECONDS)
                    repeat(1_000) { iteration ->
                        // Check mutual exclusion explicitly; concurrent weak reads alone
                        // would not detect accidentally publishing multiple owner locks.
                        lock.withWeakReferenceLock {
                            val previouslyActive = active.getAndIncrement()
                            try {
                                assertEquals(0, previouslyActive)
                                Thread.yield()
                            } finally {
                                active.decrementAndGet()
                            }
                        }
                        reference.setTarget(if (iteration % 4 == 0) null else target)
                        val resolved = reference.tryGetTarget()
                        assertTrue(resolved == null || targets.any { it === resolved })
                    }
                })
            }
            jobs.forEach { it.get(30, TimeUnit.SECONDS) }
            reference.setTarget(null)
            assertNull(reference.tryGetTarget())
        } finally {
            executor.shutdownNow()
        }
    }
}
