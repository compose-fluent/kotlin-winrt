package io.github.composefluent.winrt.benchmarks

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import kotlin.math.ceil
import kotlin.time.TimeSource

internal const val BENCHMARK_SCHEMA_VERSION: Int = 3
private const val TARGET_BATCH_NANOSECONDS: Long = 5_000_000L
private const val MAX_CALIBRATED_ITERATIONS: Int = 100_000
private const val MAX_CALIBRATION_STEPS: Int = 8
private const val MIN_ADAPTIVE_WARMUP_OPERATIONS: Long = 100_000L
private const val MIN_ADAPTIVE_WARMUP_NANOSECONDS: Long = 500_000_000L
private const val MAX_ADAPTIVE_WARMUP_NANOSECONDS: Long = 1_000_000_000L
private const val WARMUP_SETTLE_ROUNDS: Int = 5

internal data class BenchmarkOptions(
    val warmupRounds: Int = 5,
    val measurementRounds: Int = 15,
    val iterations: Int = 1,
    val outputPath: String? = null,
    val filter: Set<String> = emptySet(),
    val listScenarios: Boolean = false,
)

internal data class BenchmarkScenario(
    val name: String,
    val expectedSingleChecksum: Long?,
    val prepare: () -> PreparedBenchmarkScenario,
)

internal class PreparedBenchmarkScenario(
    val runBatch: (Int) -> Long,
    private val cleanup: () -> Unit = {},
) : AutoCloseable {
    override fun close() = cleanup()
}

internal data class BenchmarkResult(
    val runner: String,
    val runtime: String,
    val scenario: String,
    val warmupRounds: Int,
    val actualWarmupRounds: Int,
    val warmupOperations: Long,
    val measurementRounds: Int,
    val minimumIterations: Int,
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

    fun consumeResult(value: Any?)
}

internal fun parseBenchmarkOptions(args: Array<String>): BenchmarkOptions {
    var warmupRounds = 5
    var measurementRounds = 15
    var iterations = 1
    var outputPath: String? = null
    var filter = emptySet<String>()
    var listScenarios = false
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
            "--list-scenarios" -> listScenarios = true
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
        listScenarios = listScenarios,
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
    if (options.listScenarios) {
        require(options.filter.isEmpty()) { "--list-scenarios cannot be combined with --filter." }
        val catalog = scenarios.map(BenchmarkScenario::name).sorted().joinToString(separator = "\n", postfix = "\n")
        options.outputPath?.let { outputPath ->
            val path = Path(outputPath)
            path.parent?.let(SystemFileSystem::createDirectories)
            SystemFileSystem.sink(path).buffered().use { sink -> sink.writeString(catalog) }
        }
        print(catalog)
        return emptyList()
    }
    val selected = scenarios.filter { scenario ->
        options.filter.isEmpty() || scenario.name in options.filter
    }
    val knownNames = scenarios.mapTo(linkedSetOf(), BenchmarkScenario::name)
    val unknownNames = options.filter - knownNames
    require(unknownNames.isEmpty()) {
        "Unknown benchmark scenarios: ${unknownNames.sorted().joinToString()}."
    }
    require(selected.isNotEmpty()) { "No benchmark scenarios selected." }

    val results = selected.map { scenario ->
        val prepared = scenario.prepare()
        try {
            runScenario(scenario, prepared, options)
        } finally {
            prepared.close()
        }
    }
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
    prepared: PreparedBenchmarkScenario,
    options: BenchmarkOptions,
): BenchmarkResult {
    val validationChecksum = prepared.runBatch(1)
    scenario.expectedSingleChecksum?.let { expectedSingleChecksum ->
        check(validationChecksum == expectedSingleChecksum) {
            "Scenario '${scenario.name}' failed correctness validation: expected " +
                "$expectedSingleChecksum, got $validationChecksum."
        }
    }

    val expectedSingleChecksum = scenario.expectedSingleChecksum ?: validationChecksum

    var iterations = calibrateIterations(scenario.name, prepared, expectedSingleChecksum, options.iterations)
    val warmup = warmUp(
        scenarioName = scenario.name,
        prepared = prepared,
        expectedSingleChecksum = expectedSingleChecksum,
        iterations = iterations,
        minimumRounds = options.warmupRounds,
    )
    iterations = calibrateIterations(scenario.name, prepared, expectedSingleChecksum, iterations)

    val expectedChecksum = expectedSingleChecksum * iterations
    val samples = List(options.measurementRounds) {
        val start = TimeSource.Monotonic.markNow()
        val checksum = prepared.runBatch(iterations)
        val elapsedNs = start.elapsedNow().inWholeNanoseconds
        check(checksum == expectedChecksum) {
            "Scenario '${scenario.name}' produced checksum $checksum; expected $expectedChecksum."
        }
        elapsedNs.toDouble() / iterations
    }
    val sortedSamples = samples.sorted()

    return BenchmarkResult(
        runner = BenchmarkPlatform.runner,
        runtime = BenchmarkPlatform.runtime,
        scenario = scenario.name,
        warmupRounds = options.warmupRounds,
        actualWarmupRounds = warmup.rounds,
        warmupOperations = warmup.operations,
        measurementRounds = options.measurementRounds,
        minimumIterations = options.iterations,
        iterations = iterations,
        minNsPerOp = sortedSamples.first(),
        medianNsPerOp = median(sortedSamples),
        p95NsPerOp = nearestRankPercentile(sortedSamples, 0.95),
        checksum = expectedSingleChecksum,
        samplesNsPerOp = samples,
    )
}

private fun warmUp(
    scenarioName: String,
    prepared: PreparedBenchmarkScenario,
    expectedSingleChecksum: Long,
    iterations: Int,
    minimumRounds: Int,
): AdaptiveWarmup {
    val warmup = AdaptiveWarmup(minimumRounds)
    val expectedChecksum = expectedSingleChecksum * iterations
    while (warmup.shouldContinue) {
        val start = TimeSource.Monotonic.markNow()
        val checksum = prepared.runBatch(iterations)
        val elapsedNs = start.elapsedNow().inWholeNanoseconds.coerceAtLeast(1L)
        check(checksum == expectedChecksum) {
            "Scenario '$scenarioName' produced an unstable warmup checksum: " +
                "expected $expectedChecksum, got $checksum."
        }
        warmup.recordBatch(iterations, elapsedNs)
    }
    return warmup
}

internal class AdaptiveWarmup(
    private val minimumRounds: Int,
) {
    init {
        require(minimumRounds >= 0) { "Minimum warmup rounds must be non-negative." }
    }

    var rounds: Int = 0
        private set
    var operations: Long = 0L
        private set
    var elapsedNanoseconds: Long = 0L
        private set

    private var finalRound: Int? = if (minimumRounds == 0) 0 else null

    val shouldContinue: Boolean
        get() = finalRound?.let { rounds < it } ?: true

    fun recordBatch(
        iterations: Int,
        elapsedNs: Long,
    ) {
        require(shouldContinue) { "Adaptive warmup is already complete." }
        require(iterations > 0) { "Warmup iterations must be positive." }
        require(elapsedNs > 0L) { "Warmup elapsed time must be positive." }

        rounds += 1
        operations += iterations
        elapsedNanoseconds += elapsedNs

        if (finalRound == null && adaptiveThresholdReached()) {
            finalRound = maxOf(minimumRounds, rounds + WARMUP_SETTLE_ROUNDS)
        }
    }

    private fun adaptiveThresholdReached(): Boolean =
        elapsedNanoseconds >= MAX_ADAPTIVE_WARMUP_NANOSECONDS ||
            (
                operations >= MIN_ADAPTIVE_WARMUP_OPERATIONS &&
                    elapsedNanoseconds >= MIN_ADAPTIVE_WARMUP_NANOSECONDS
                )
}

private fun calibrateIterations(
    scenarioName: String,
    prepared: PreparedBenchmarkScenario,
    expectedSingleChecksum: Long,
    minimumIterations: Int,
): Int {
    var iterations = minimumIterations.coerceAtMost(MAX_CALIBRATED_ITERATIONS)
    repeat(MAX_CALIBRATION_STEPS) {
        val start = TimeSource.Monotonic.markNow()
        val checksum = prepared.runBatch(iterations)
        val elapsedNs = start.elapsedNow().inWholeNanoseconds.coerceAtLeast(1L)
        val expectedChecksum = expectedSingleChecksum * iterations
        check(checksum == expectedChecksum) {
            "Scenario '$scenarioName' produced checksum $checksum during calibration; expected $expectedChecksum."
        }
        if (elapsedNs >= TARGET_BATCH_NANOSECONDS || iterations == MAX_CALIBRATED_ITERATIONS) {
            return iterations
        }

        val scale = ceil(TARGET_BATCH_NANOSECONDS.toDouble() / elapsedNs)
            .toLong()
            .coerceIn(2L, 1_000L)
        iterations = (iterations.toLong() * scale)
            .coerceAtMost(MAX_CALIBRATED_ITERATIONS.toLong())
            .toInt()
    }
    return iterations
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
    append(",\"actualWarmupRounds\":")
    append(actualWarmupRounds)
    append(",\"warmupOperations\":")
    append(warmupOperations)
    append(",\"measurementRounds\":")
    append(measurementRounds)
    append(",\"minimumIterations\":")
    append(minimumIterations)
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
