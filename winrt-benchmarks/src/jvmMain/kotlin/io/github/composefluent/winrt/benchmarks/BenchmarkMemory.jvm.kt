package io.github.composefluent.winrt.benchmarks

import java.lang.management.ManagementFactory

private val allocationBean = (ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean)
    ?.takeIf { it.isThreadAllocatedMemorySupported }
    ?.apply { isThreadAllocatedMemoryEnabled = true }

internal actual val benchmarkAllocationScope: String = "current-thread-managed-only"

internal actual fun benchmarkAllocatedBytes(): Long =
    allocationBean?.getThreadAllocatedBytes(Thread.currentThread().threadId()) ?: -1L

internal actual fun benchmarkCollectedHeapBytes(): Long {
    // Two requests allow reference processing from the first collection to become reclaimable.
    repeat(2) { System.gc() }
    return ManagementFactory.getMemoryMXBean().heapMemoryUsage.used
}
