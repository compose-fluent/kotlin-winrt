package io.github.composefluent.winrt.runtime

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentLinkedQueue
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
    fun racing_factories_publish_one_wrapper_without_disposing_escaped_candidates() {
        ComWrappersSupport.clearRegistriesForTests()
        val entered = CountDownLatch(2)
        val candidates = ConcurrentLinkedQueue<IWinRTObject>()
        val pool = Executors.newFixedThreadPool(2)
        val host = WinRTInspectableComObject.inspectableBox(null, "test.SingleRcw")
        var wrapper: IWinRTObject? = null
        ComWrappersSupport.registerRuntimeClassFactory("test.SingleRcw") { reference ->
            val candidate = object : IWinRTObject { override val nativeObject: ComObjectReference = reference }
            candidates += candidate
            entered.countDown()
            check(entered.await(10, TimeUnit.SECONDS)) { "Factory ran inside the publication lock" }
            ComWrappersSupport.registerRuntimeClassWrapper(candidate, reference)
            candidate
        }
        try {
            val futures = List(2) { pool.submit<IWinRTObject> {
                ComWrappersSupport.createRcwForOwnedComObject(host.ownedPointer()) as IWinRTObject
            } }
            wrapper = futures.first().get(15, TimeUnit.SECONDS)
            futures.forEach { assertSame(wrapper, it.get(15, TimeUnit.SECONDS)) }
            assertEquals(2, candidates.size)
            assertTrue(candidates.all { !it.nativeObject.isDisposed })
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS))
            candidates.forEach { it.nativeObject.close() }
            host.close()
            ComWrappersSupport.clearRegistriesForTests()
        }
    }

    private fun WinRTInspectableComObject.ownedPointer(): RawAddress =
        createReference(IID.IInspectable).use { it.acquireOwnedPointer().asRawAddress() }
}
