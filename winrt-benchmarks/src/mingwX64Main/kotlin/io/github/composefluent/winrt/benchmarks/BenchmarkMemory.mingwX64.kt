@file:OptIn(kotlin.native.runtime.NativeRuntimeApi::class, ExperimentalStdlibApi::class)

package io.github.composefluent.winrt.benchmarks

import kotlin.native.runtime.GC

// GC.lastGCInfo reports heap occupancy, not cumulative allocation. Do not subtract its
// before/after fields to manufacture B/op: intervening collections would lose allocations.
internal actual val benchmarkAllocationScope: String = "unavailable"
internal actual fun benchmarkAllocatedBytes(): Long = -1L

internal actual fun benchmarkCollectedHeapBytes(): Long {
    repeat(2) { GC.collect() }
    return checkNotNull(GC.lastGCInfo).memoryUsageAfter.values.sumOf { it.totalObjectsSizeBytes }
}
