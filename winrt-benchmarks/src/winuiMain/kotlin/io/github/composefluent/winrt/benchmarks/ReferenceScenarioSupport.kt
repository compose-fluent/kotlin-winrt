package io.github.composefluent.winrt.benchmarks

internal fun <T, R> referenceValueScenario(
    name: String,
    create: () -> T,
    setup: (T) -> Unit = {},
    invoke: (T) -> R,
    checksum: (R) -> Long,
    cleanup: (T) -> Unit = {},
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

internal fun <T> referenceObjectScenario(
    name: String,
    create: () -> T,
    setup: (T) -> Unit = {},
    invoke: (T) -> Any?,
    cleanup: (T) -> Unit = {},
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

internal fun <T> referenceVoidScenario(
    name: String,
    create: () -> T,
    setup: (T) -> Unit = {},
    invoke: (T) -> Unit,
    cleanup: (T) -> Unit = {},
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
