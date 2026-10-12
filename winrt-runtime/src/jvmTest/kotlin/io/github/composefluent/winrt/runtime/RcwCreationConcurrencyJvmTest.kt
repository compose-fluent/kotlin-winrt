package io.github.composefluent.winrt.runtime

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RcwCreationConcurrencyJvmTest {
    // Reference: CsWinRT ComWrappersSupport.netstandard2.0.cs RuntimeWrapperCache.
    // Test real RCW factories: unrelated identities must overlap, one identity must publish once.
    @Test
    fun unrelated_owned_factories_overlap_and_same_identity_reuses_the_wrapper() {
        ComWrappersSupport.clearRegistriesForTests()
        val entered = CountDownLatch(2)
        val calls = AtomicInteger()
        val pool = Executors.newFixedThreadPool(2)
        val hosts = List(2) { WinRTInspectableComObject.inspectableBox(null, "test.ParallelRcw") }
        val wrappers = mutableListOf<IWinRTObject>()
        ComWrappersSupport.registerRuntimeClassFactory("test.ParallelRcw") { reference ->
            calls.incrementAndGet()
            entered.countDown()
            check(entered.await(10, TimeUnit.SECONDS)) { "Unrelated RCW factories were serialized" }
            object : IWinRTObject { override val nativeObject: ComObjectReference = reference }
        }
        try {
            val futures = hosts.map { host -> pool.submit<IWinRTObject> {
                ComWrappersSupport.createRcwForOwnedComObject(host.ownedPointer()) as IWinRTObject
            } }
            futures.forEach { wrappers += it.get(15, TimeUnit.SECONDS) }
            assertEquals(2, calls.get())
            val repeats = List(2) { pool.submit<Any?> {
                ComWrappersSupport.createRcwForOwnedComObject(hosts[0].ownedPointer())
            } }
            repeats.forEach { assertSame(wrappers[0], it.get(10, TimeUnit.SECONDS)) }
            assertEquals(2, calls.get())
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS))
            wrappers.forEach { it.nativeObject.close() }
            hosts.forEach { it.close() }
            ComWrappersSupport.clearRegistriesForTests()
        }
    }

    @Test
    fun concurrent_cold_requests_for_one_identity_publish_once() {
        ComWrappersSupport.clearRegistriesForTests()
        val start = CountDownLatch(1)
        val calls = AtomicInteger()
        val pool = Executors.newFixedThreadPool(8)
        val host = WinRTInspectableComObject.inspectableBox(null, "test.SingleRcw")
        var wrapper: IWinRTObject? = null
        ComWrappersSupport.registerRuntimeClassFactory("test.SingleRcw") { reference ->
            calls.incrementAndGet()
            object : IWinRTObject { override val nativeObject: ComObjectReference = reference }
        }
        try {
            val futures = List(8) { pool.submit<IWinRTObject> {
                check(start.await(10, TimeUnit.SECONDS))
                ComWrappersSupport.createRcwForOwnedComObject(host.ownedPointer()) as IWinRTObject
            } }
            start.countDown()
            wrapper = futures.first().get(15, TimeUnit.SECONDS)
            futures.forEach { assertSame(wrapper, it.get(15, TimeUnit.SECONDS)) }
            assertEquals(1, calls.get())
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS))
            wrapper?.nativeObject?.close()
            host.close()
            ComWrappersSupport.clearRegistriesForTests()
        }
    }

    @Test
    fun identity_lock_is_reentrant_and_released_after_failure() {
        val locks = RcwCreationLocks()
        kotlin.test.assertFailsWith<IllegalStateException> {
            locks.withIdentity(1) { locks.withIdentity(1) { error("factory failed") } }
        }
        val pool = Executors.newSingleThreadExecutor()
        try {
            assertEquals(42, pool.submit<Int> { locks.withIdentity(1) { 42 } }.get(10, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }
    }

    private fun WinRTInspectableComObject.ownedPointer(): RawAddress =
        createReference(IID.IInspectable).use { it.getRefPointer().asRawAddress() }
}
