package io.github.composefluent.winrt.benchmarks

/** Companion diagnostics to the CsWinRT [MemoryDiagnoser] scenarios, outside timing samples.
 * Heap deltas are retained managed bytes, not allocation rates or native COM memory.
 * A missing allocation counter is null, never zero. Run in a fresh process per scenario.
 */
internal data class BenchmarkMemoryResult(
    val operations: Int,
    val allocatedBytes: Long?,
    val allocationScope: String,
    val heapBeforeBytes: Long,
    val heapAfterBytes: Long,
) {
    fun toJson(): String = "{\"operations\":$operations,\"allocatedBytes\":$allocatedBytes," +
        "\"allocationScope\":\"$allocationScope\",\"heapBeforeBytes\":$heapBeforeBytes," +
        "\"heapAfterBytes\":$heapAfterBytes,\"retainedHeapDeltaBytes\":${heapAfterBytes - heapBeforeBytes}}"
}

internal expect fun benchmarkCollectedHeapBytes(): Long
internal expect fun benchmarkAllocatedBytes(): Long
internal expect val benchmarkAllocationScope: String

internal fun measureBenchmarkMemory(
    prepared: PreparedBenchmarkScenario,
    iterations: Int,
    expectedChecksum: Long,
): BenchmarkMemoryResult {
    BenchmarkPlatform.consumeResult(null)
    val heapBefore = benchmarkCollectedHeapBytes()
    // Initialize the platform counter before the measured interval.
    benchmarkAllocatedBytes()
    val before = benchmarkAllocatedBytes()
    val checksum = prepared.runBatch(iterations)
    val after = benchmarkAllocatedBytes()
    check(checksum == expectedChecksum) { "Memory diagnostic checksum mismatch." }
    BenchmarkPlatform.consumeResult(null)
    val heapAfter = benchmarkCollectedHeapBytes()
    return BenchmarkMemoryResult(
        iterations, if (before >= 0 && after >= before) after - before else null,
        benchmarkAllocationScope, heapBefore, heapAfter,
    )
}
