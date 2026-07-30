package io.github.composefluent.winrt.benchmarks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BenchmarkProtocolTest {
    @Test
    fun parsesSharedRunnerOptions() {
        assertEquals(
            BenchmarkOptions(
                warmupRounds = 2,
                measurementRounds = 4,
                iterations = 128,
                outputPath = "result.jsonl",
                filter = setOf("get_value_type", "stringify"),
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
                    "get_value_type,stringify",
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
}
