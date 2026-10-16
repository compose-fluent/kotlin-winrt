package io.github.composefluent.winrt.runtime

import java.util.concurrent.Executors
import java.lang.management.ManagementFactory
import java.lang.ref.Reference
import kotlin.test.Test
import kotlin.time.TimeSource

/** Includes construction so registration overhead cannot be hidden outside the timed batch. */
class ReferenceLifecycleMemoryBenchmark {
    @Test
    fun allocation_and_retained_memory() {
        if (!referenceContentionBenchmarkEnabled()) return
        repeat(100_000) { reference().close() }
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        bean.isThreadAllocatedMemoryEnabled = true
        val thread = Thread.currentThread().threadId()
        val before = bean.getThreadAllocatedBytes(thread)
        val start = TimeSource.Monotonic.markNow()
        repeat(200_000) { reference().close() }
        val ns = start.elapsedNow().inWholeNanoseconds / 200_000.0
        val bytes = (bean.getThreadAllocatedBytes(thread) - before) / 200_000.0
        val empty = usedHeapAfterGc()
        val owners = Array(50_000) { reference() }
        val live = usedHeapAfterGc()
        println("LIFECYCLE nsPerOp=$ns allocatedBytesPerOp=$bytes retainedBytesPerOwner=${(live - empty) / 50_000.0}")
        println("RECLAIMERS " + Thread.getAllStackTraces().keys.map { it.name }
            .filter { it.contains("Cleaner") || it.contains("Reclaimer") }.sorted())
        owners.forEach { it.close() }
        Reference.reachabilityFence(owners)
    }

    private fun usedHeapAfterGc(): Long {
        repeat(3) { System.gc(); Thread.sleep(50) }
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
    }

    private fun reference() = ComObjectReference(
        ComPtr.create(RawComPtr(1), IID.IUnknown, ComOwnershipMode.Borrowed, trackContext = false),
    )
}

internal actual fun referenceContentionBenchmarkEnabled() = System.getenv("WINRT_CONTENTION_BENCHMARK") == "1"
internal actual fun referenceContentionYield() = Thread.yield()
internal actual fun runReferenceContentionWorkers(count: Int, action: (Int) -> Unit) {
    val pool = Executors.newFixedThreadPool(count)
    try {
        (0 until count).map { index -> pool.submit { action(index) } }.forEach { it.get() }
    } finally {
        pool.shutdown()
    }
}
