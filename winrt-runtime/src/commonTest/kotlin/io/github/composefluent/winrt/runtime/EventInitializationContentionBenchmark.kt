@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicInt
import kotlin.test.Test
import kotlin.time.TimeSource
import windows.foundation.EventRegistrationToken

/**
 * Measures independent event tables' first registration, including lazy synchronization and
 * token storage. CsWinRT EventRegistrationTokenTable<T> synchronizes on each table's dictionary.
 * Opt in with WINRT_CONTENTION_BENCHMARK=1 and WINRT_CONTENTION_MODE=event; run only this class.
 * Use optimized Native tests (-Pwinrt.callsite.benchmark=true) or JVM -Xbatch, without a build.
 * This is runtime bookkeeping, not an end-to-end WinRT/C++ event comparison.
 */
class EventInitializationContentionBenchmark {
    @Test
    fun independent_event_first_registrations() {
        if (!referenceContentionBenchmarkEnabled() || referenceContentionBenchmarkMode() != "event") return
        val batch = 256
        val rounds = 120
        for (threads in listOf(1, 2, 4, 8)) {
            val ready = Array(rounds) { AtomicInt(0) }
            val done = Array(rounds) { AtomicInt(0) }
            val elapsed = Array(threads) { DoubleArray(rounds) }
            runReferenceContentionWorkers(threads) { worker ->
                val handler = Any()
                repeat(rounds) { round ->
                    val tables = Array(batch) { EventRegistrationTokenTable.create<Any>() }
                    val tokens = LongArray(batch)
                    ready[round].fetchAndAdd(1)
                    while (ready[round].load() != threads) referenceContentionYield()
                    val start = TimeSource.Monotonic.markNow()
                    for (index in tables.indices) tokens[index] = tables[index].addEventHandler(handler).value
                    elapsed[worker][round] = start.elapsedNow().inWholeNanoseconds.toDouble()
                    for (index in tables.indices) {
                        check(tables[index].removeEventHandler(EventRegistrationToken(tokens[index])) === handler)
                    }
                    done[round].fetchAndAdd(1)
                    while (done[round].load() != threads) referenceContentionYield()
                }
            }
            val samples = (60 until rounds).map { round ->
                elapsed.maxOf { it[round] } / (batch * threads)
            }.sorted()
            println("EVENT_INIT threads=$threads medianNsPerOp=${samples[samples.size / 2]} " +
                "p90=${samples[samples.size * 9 / 10]} meanNsPerOp=${samples.average()}")
        }
    }
}
