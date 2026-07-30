package io.github.composefluent.winrt.benchmarks

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import kotlin.math.ceil
import kotlin.time.TimeSource

internal const val BENCHMARK_SCHEMA_VERSION: Int = 1

internal data class BenchmarkOptions(
    val warmupRounds: Int = 5,
    val measurementRounds: Int = 15,
    val iterations: Int = 10_000,
    val outputPath: String? = null,
    val filter: Set<String> = emptySet(),
)

internal data class BenchmarkScenario(
    val name: String,
    val expectedSingleChecksum: Long,
    val runBatch: (Int) -> Long,
)

internal data class BenchmarkResult(
    val runner: String,
    val runtime: String,
    val scenario: String,
    val warmupRounds: Int,
    val measurementRounds: Int,
    val iterations: Int,
    val minNsPerOp: Double,
    val medianNsPerOp: Double,
    val p95NsPerOp: Double,
    val checksum: Long,
    val samplesNsPerOp: List<Double>,
)

internal expect object BenchmarkPlatform {
    val runner: String
    val runtime: String
}

internal fun parseBenchmarkOptions(args: Array<String>): BenchmarkOptions {
    var warmupRounds = 5
    var measurementRounds = 15
    var iterations = 10_000
    var outputPath: String? = null
    var filter = emptySet<String>()
    var index = 0

    fun nextValue(option: String): String {
        check(index + 1 < args.size) { "Missing value for $option." }
        index += 1
        return args[index]
    }

    while (index < args.size) {
        when (val option = args[index]) {
            "--warmup-rounds" -> warmupRounds = nextValue(option).toNonNegativeInt(option)
            "--measurement-rounds" -> measurementRounds = nextValue(option).toPositiveInt(option)
            "--iterations" -> iterations = nextValue(option).toPositiveInt(option)
            "--output" -> outputPath = nextValue(option).takeIf(String::isNotBlank)
                ?: error("$option must not be blank.")
            "--filter" -> filter = nextValue(option)
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toSet()
                .also { require(it.isNotEmpty()) { "$option must select at least one scenario." } }
            else -> error("Unknown benchmark option '$option'.")
        }
        index += 1
    }

    return BenchmarkOptions(
        warmupRounds = warmupRounds,
        measurementRounds = measurementRounds,
        iterations = iterations,
        outputPath = outputPath,
        filter = filter,
    )
}

private fun String.toNonNegativeInt(option: String): Int =
    toIntOrNull()?.takeIf { it >= 0 }
        ?: error("$option expects a non-negative integer, got '$this'.")

private fun String.toPositiveInt(option: String): Int =
    toIntOrNull()?.takeIf { it > 0 }
        ?: error("$option expects a positive integer, got '$this'.")

internal fun runBenchmarkSuite(
    args: Array<String>,
    scenarios: List<BenchmarkScenario>,
): List<BenchmarkResult> {
    val options = parseBenchmarkOptions(args)
    val selected = scenarios.filter { scenario ->
        options.filter.isEmpty() || scenario.name in options.filter
    }
    val knownNames = scenarios.mapTo(linkedSetOf(), BenchmarkScenario::name)
    val unknownNames = options.filter - knownNames
    require(unknownNames.isEmpty()) {
        "Unknown benchmark scenarios: ${unknownNames.sorted().joinToString()}."
    }
    require(selected.isNotEmpty()) { "No benchmark scenarios selected." }

    val results = selected.map { scenario -> runScenario(scenario, options) }
    val jsonLines = results.joinToString(separator = "\n", postfix = "\n", transform = BenchmarkResult::toJson)
    options.outputPath?.let { outputPath ->
        val path = Path(outputPath)
        path.parent?.let(SystemFileSystem::createDirectories)
        SystemFileSystem.sink(path).buffered().use { sink -> sink.writeString(jsonLines) }
    }
    print(jsonLines)
    return results
}

private fun runScenario(
    scenario: BenchmarkScenario,
    options: BenchmarkOptions,
): BenchmarkResult {
    val validationChecksum = scenario.runBatch(1)
    check(validationChecksum == scenario.expectedSingleChecksum) {
        "Scenario '${scenario.name}' failed correctness validation: expected " +
            "${scenario.expectedSingleChecksum}, got $validationChecksum."
    }

    repeat(options.warmupRounds) {
        check(scenario.runBatch(options.iterations) == scenario.expectedSingleChecksum * options.iterations) {
            "Scenario '${scenario.name}' produced an unstable warmup checksum."
        }
    }

    val expectedChecksum = scenario.expectedSingleChecksum * options.iterations
    val samples = List(options.measurementRounds) {
        val start = TimeSource.Monotonic.markNow()
        val checksum = scenario.runBatch(options.iterations)
        val elapsedNs = start.elapsedNow().inWholeNanoseconds
        check(checksum == expectedChecksum) {
            "Scenario '${scenario.name}' produced checksum $checksum; expected $expectedChecksum."
        }
        elapsedNs.toDouble() / options.iterations
    }
    val sortedSamples = samples.sorted()

    return BenchmarkResult(
        runner = BenchmarkPlatform.runner,
        runtime = BenchmarkPlatform.runtime,
        scenario = scenario.name,
        warmupRounds = options.warmupRounds,
        measurementRounds = options.measurementRounds,
        iterations = options.iterations,
        minNsPerOp = sortedSamples.first(),
        medianNsPerOp = median(sortedSamples),
        p95NsPerOp = nearestRankPercentile(sortedSamples, 0.95),
        checksum = expectedChecksum,
        samplesNsPerOp = samples,
    )
}

internal fun median(sortedValues: List<Double>): Double {
    require(sortedValues.isNotEmpty()) { "Cannot calculate a median from no values." }
    val middle = sortedValues.size / 2
    return if (sortedValues.size % 2 == 0) {
        (sortedValues[middle - 1] + sortedValues[middle]) / 2.0
    } else {
        sortedValues[middle]
    }
}

internal fun nearestRankPercentile(
    sortedValues: List<Double>,
    percentile: Double,
): Double {
    require(sortedValues.isNotEmpty()) { "Cannot calculate a percentile from no values." }
    require(percentile > 0.0 && percentile <= 1.0) { "Percentile must be in (0, 1]." }
    val index = (ceil(percentile * sortedValues.size).toInt() - 1).coerceIn(sortedValues.indices)
    return sortedValues[index]
}

private fun BenchmarkResult.toJson(): String = buildString {
    append("{\"schemaVersion\":")
    append(BENCHMARK_SCHEMA_VERSION)
    append(",\"runner\":\"")
    append(runner.escapeJson())
    append("\",\"runtime\":\"")
    append(runtime.escapeJson())
    append("\",\"scenario\":\"")
    append(scenario.escapeJson())
    append("\",\"warmupRounds\":")
    append(warmupRounds)
    append(",\"measurementRounds\":")
    append(measurementRounds)
    append(",\"iterations\":")
    append(iterations)
    append(",\"minNsPerOp\":")
    append(minNsPerOp)
    append(",\"medianNsPerOp\":")
    append(medianNsPerOp)
    append(",\"p95NsPerOp\":")
    append(p95NsPerOp)
    append(",\"checksum\":")
    append(checksum)
    append(",\"samplesNsPerOp\":[")
    samplesNsPerOp.joinTo(this, separator = ",")
    append("]}")
}

private fun String.escapeJson(): String = buildString(length) {
    for (character in this@escapeJson) {
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (character.code < 0x20) {
                append("\\u")
                append(character.code.toString(16).padStart(4, '0'))
            } else {
                append(character)
            }
        }
    }
}
