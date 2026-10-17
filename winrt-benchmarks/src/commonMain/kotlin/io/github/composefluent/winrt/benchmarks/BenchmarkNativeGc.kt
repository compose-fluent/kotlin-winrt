package io.github.composefluent.winrt.benchmarks

/** Last completed GC, not cumulative statistics; repeated epochs denote the same collection. */
internal data class BenchmarkNativeGcSnapshot(
    val epoch: Long,
    val collectionNs: Long,
    val pauseNs: Long,
    val cleanupNs: Long?,
    val heapBeforeBytes: Long,
    val heapAfterBytes: Long,
    val stableReferences: Long,
    val markedObjects: Long,
) {
    fun toJson(): String =
        "{\"epoch\":$epoch,\"collectionNs\":$collectionNs,\"pauseNs\":$pauseNs," +
            "\"cleanupNs\":$cleanupNs,\"heapBeforeBytes\":$heapBeforeBytes," +
            "\"heapAfterBytes\":$heapAfterBytes,\"stableReferences\":$stableReferences," +
            "\"markedObjects\":$markedObjects}"
}

internal expect fun benchmarkNativeGcSnapshot(): BenchmarkNativeGcSnapshot?
