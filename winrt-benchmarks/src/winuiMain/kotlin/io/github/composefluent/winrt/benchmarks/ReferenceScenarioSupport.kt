package io.github.composefluent.winrt.benchmarks

// Match the C++ reference runner's templated operation loop: specialize operation and
// checksum at the scenario call site instead of timing two generic Function1 dispatches.
internal inline fun <T, R> referenceValueScenario(
    name: String,
    crossinline create: () -> T,
    crossinline setup: (T) -> Unit = {},
    crossinline invoke: (T) -> R,
    crossinline checksum: (R) -> Long,
    crossinline cleanup: (T) -> Unit = {},
): BenchmarkScenario =
    BenchmarkScenario(name, null) {
        val benchmark = create()
        setup(benchmark)
        PreparedBenchmarkScenario(
            runBatch = { iterations ->
                var result = 0L
                repeat(iterations) {
                    result += checksum(invoke(benchmark))
                }
                result
            },
            cleanup = { cleanup(benchmark) },
        )
    }

internal inline fun <T> referenceObjectScenario(
    name: String,
    crossinline create: () -> T,
    crossinline setup: (T) -> Unit = {},
    crossinline invoke: (T) -> Any?,
    crossinline cleanup: (T) -> Unit = {},
): BenchmarkScenario =
    referenceValueScenario(
        name = name,
        create = create,
        setup = setup,
        invoke = invoke,
        checksum = { value ->
            BenchmarkPlatform.consumeResult(value)
            if (value == null) 0L else 1L
        },
        cleanup = cleanup,
    )

internal inline fun <T> referenceVoidScenario(
    name: String,
    crossinline create: () -> T,
    crossinline setup: (T) -> Unit = {},
    crossinline invoke: (T) -> Unit,
    crossinline cleanup: (T) -> Unit = {},
): BenchmarkScenario =
    BenchmarkScenario(name, 1) {
        val benchmark = create()
        setup(benchmark)
        PreparedBenchmarkScenario(
            runBatch = { iterations ->
                repeat(iterations) { invoke(benchmark) }
                iterations.toLong()
            },
            cleanup = { cleanup(benchmark) },
        )
    }
