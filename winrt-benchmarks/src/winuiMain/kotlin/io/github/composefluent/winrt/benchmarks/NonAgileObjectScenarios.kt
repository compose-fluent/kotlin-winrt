package io.github.composefluent.winrt.benchmarks

internal interface NonAgileObjectPerf : AutoCloseable {
    fun constructAndQueryNonAgileObject()

    fun constructNonAgileObject()
}

internal expect fun createNonAgileObjectPerf(): NonAgileObjectPerf

internal fun nonAgileObjectScenarios(): List<BenchmarkScenario> =
    listOf(
        nonAgileObjectScenario("ConstructAndQueryNonAgileObject") {
            it.constructAndQueryNonAgileObject()
        },
        nonAgileObjectScenario("ConstructNonAgileObject") {
            it.constructNonAgileObject()
        },
    )

private fun nonAgileObjectScenario(
    method: String,
    invoke: (NonAgileObjectPerf) -> Unit,
): BenchmarkScenario =
    BenchmarkScenario("NonAgileObjectPerf.$method", 1) {
        val benchmark = createNonAgileObjectPerf()
        PreparedBenchmarkScenario(
            runBatch = { iterations ->
                repeat(iterations) { invoke(benchmark) }
                iterations.toLong()
            },
            cleanup = benchmark::close,
        )
    }
