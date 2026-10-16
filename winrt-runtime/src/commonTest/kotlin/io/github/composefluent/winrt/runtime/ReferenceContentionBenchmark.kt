@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicInt
import kotlin.test.Test
import kotlin.time.TimeSource

/**
 * Opt-in runtime-internal diagnostic; no COM calls, construction, or thread startup
 * in timed ownership batches. The state case includes cache construction.
 *
 * Enable with WINRT_CONTENTION_BENCHMARK=1. Build Native tests with
 * -Pwinrt.callsite.benchmark=true (optimized=true, debuggable=false), then run
 * test.exe --ktest_filter=*ReferenceContentionBenchmark* without a concurrent build.
 * On JVM run this class through JUnitCore with -Xbatch and native access enabled.
 * Compare several independent processes; this isolates runtime bookkeeping and
 * must not be presented as end-to-end COM throughput or a C++/WinRT comparison.
 */
class ReferenceContentionBenchmark {
    @Test
    fun independent_reference_contention() {
        if (!referenceContentionBenchmarkEnabled()) return
        val batch = 4096
        val rounds = 40
        for (mode in listOf("close", "own-close", "state")) {
            for (threads in listOf(1, 2, 4, 8)) {
                val ready = Array(rounds) { AtomicInt(0) }
                val done = Array(rounds) { AtomicInt(0) }
                val elapsed = Array(threads) { DoubleArray(rounds) }
                runReferenceContentionWorkers(threads) { thread ->
                    repeat(rounds) { round ->
                        val owners = if (mode != "state") Array(batch) { reference() } else emptyArray()
                        val children = if (mode == "own-close") Array(batch) { reference() } else emptyArray()
                        val states = if (mode == "state") Array(batch) { WinRTObjectState<ComObjectReference>() } else emptyArray()
                        ready[round].fetchAndAdd(1)
                        while (ready[round].load() != threads) referenceContentionYield()
                        val start = TimeSource.Monotonic.markNow()
                        when (mode) {
                            "close" -> owners.forEach { it.close() }
                            "own-close" -> owners.forEachIndexed { i, owner -> owner.ownInterfaceReference(children[i]); owner.close() }
                            "state" -> states.forEach { check(it.queryInterfaceCache.size == 0) }
                        }
                        elapsed[thread][round] = start.elapsedNow().inWholeNanoseconds.toDouble()
                        done[round].fetchAndAdd(1)
                        while (done[round].load() != threads) referenceContentionYield()
                    }
                }
                // Parallel batch completion is the slowest worker; report aggregate ns/operation.
                val samples = (10 until rounds).map { round -> elapsed.maxOf { it[round] } / (batch * threads) }.sorted()
                println("CONTENTION mode=$mode threads=$threads nsPerOp=${samples[samples.size / 2]} p90=${samples[samples.size * 9 / 10]}")
            }
        }
    }

    private fun reference() = ComObjectReference(
        ComPtr.create(RawComPtr(1), IID.IUnknown, ComOwnershipMode.Borrowed, trackContext = false),
    )
}

internal expect fun referenceContentionBenchmarkEnabled(): Boolean
internal expect fun referenceContentionYield()
internal expect fun runReferenceContentionWorkers(count: Int, action: (Int) -> Unit)
