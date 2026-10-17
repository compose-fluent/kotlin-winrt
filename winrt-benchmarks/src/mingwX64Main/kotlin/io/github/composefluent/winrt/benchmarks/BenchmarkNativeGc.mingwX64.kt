@file:OptIn(kotlin.native.runtime.NativeRuntimeApi::class, ExperimentalStdlibApi::class)

package io.github.composefluent.winrt.benchmarks

import kotlin.native.runtime.GC

internal actual fun benchmarkNativeGcSnapshot(): BenchmarkNativeGcSnapshot? {
    // Kotlin 2.4 GCInfo: postGcCleanupTimeNs is absent while reclamation is pending.
    // Reading this snapshot does not force a collection or wait for cleanup.
    val info = GC.lastGCInfo ?: return null
    val secondPauseNs = info.secondPauseEndTimeNs?.let { end ->
        info.secondPauseStartTimeNs?.let { start -> end - start }
    } ?: 0L
    return BenchmarkNativeGcSnapshot(
        epoch = info.epoch,
        collectionNs = info.endTimeNs - info.startTimeNs,
        pauseNs = info.firstPauseEndTimeNs - info.firstPauseStartTimeNs + secondPauseNs,
        cleanupNs = info.postGcCleanupTimeNs?.let { it - info.endTimeNs },
        heapBeforeBytes = info.memoryUsageBefore.values.sumOf { it.totalObjectsSizeBytes },
        heapAfterBytes = info.memoryUsageAfter.values.sumOf { it.totalObjectsSizeBytes },
        stableReferences = info.rootSet.stableReferences,
        markedObjects = info.markedCount,
    )
}
