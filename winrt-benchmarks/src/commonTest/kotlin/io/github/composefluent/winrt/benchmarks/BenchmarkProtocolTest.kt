package io.github.composefluent.winrt.benchmarks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BenchmarkProtocolTest {
    @Test
    fun parsesSharedRunnerOptions() {
        assertEquals(
            BenchmarkOptions(
                warmupRounds = 2,
                measurementRounds = 4,
                iterations = 128,
                outputPath = "result.jsonl",
                filter = setOf(
                    "QueryInterfacePerf.QueryDefaultInterface",
                    "ReflectionPerf.ExecuteMarshalingForString",
                ),
                listScenarios = true,
            ),
            parseBenchmarkOptions(
                arrayOf(
                    "--warmup-rounds",
                    "2",
                    "--measurement-rounds",
                    "4",
                    "--iterations",
                    "128",
                    "--output",
                    "result.jsonl",
                    "--filter",
                    "QueryInterfacePerf.QueryDefaultInterface,ReflectionPerf.ExecuteMarshalingForString",
                    "--list-scenarios",
                ),
            ),
        )
    }

    @Test
    fun rejectsInvalidIterationCount() {
        assertFailsWith<IllegalStateException> {
            parseBenchmarkOptions(arrayOf("--iterations", "0"))
        }
    }

    @Test
    fun calculatesMedianAndNearestRankP95() {
        val odd = listOf(1.0, 2.0, 3.0, 4.0, 100.0)
        val even = listOf(1.0, 2.0, 3.0, 4.0)

        assertEquals(3.0, median(odd))
        assertEquals(2.5, median(even))
        assertEquals(100.0, nearestRankPercentile(odd, 0.95))
    }

    @Test
    fun adaptiveWarmupWaitsForOperationAndTimeThresholdsThenSettles() {
        val warmup = AdaptiveWarmup(minimumRounds = 2)

        repeat(499) {
            assertTrue(warmup.shouldContinue)
            warmup.recordBatch(iterations = 1_000, elapsedNs = 1_000_000L)
        }
        assertTrue(warmup.shouldContinue)
        warmup.recordBatch(iterations = 1_000, elapsedNs = 1_000_000L)
        repeat(4) {
            assertTrue(warmup.shouldContinue)
            warmup.recordBatch(iterations = 1_000, elapsedNs = 1_000_000L)
        }
        assertTrue(warmup.shouldContinue)
        warmup.recordBatch(iterations = 1_000, elapsedNs = 1_000_000L)

        assertFalse(warmup.shouldContinue)
        assertEquals(505, warmup.rounds)
        assertEquals(505_000L, warmup.operations)
    }

    @Test
    fun adaptiveWarmupCapsSlowScenariosByElapsedTime() {
        val warmup = AdaptiveWarmup(minimumRounds = 2)

        repeat(10) {
            warmup.recordBatch(iterations = 1, elapsedNs = 100_000_000L)
        }
        repeat(5) {
            assertTrue(warmup.shouldContinue)
            warmup.recordBatch(iterations = 1, elapsedNs = 100_000_000L)
        }

        assertFalse(warmup.shouldContinue)
        assertEquals(15, warmup.rounds)
        assertEquals(15L, warmup.operations)
    }

    @Test
    fun zeroWarmupRoundsDisableAdaptiveWarmup() {
        val warmup = AdaptiveWarmup(minimumRounds = 0)

        assertFalse(warmup.shouldContinue)
        assertEquals(0, warmup.rounds)
    }
}
