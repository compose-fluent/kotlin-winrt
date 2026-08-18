package io.github.composefluent.winrt.benchmarks

import benchmarkcomponent.ClassWithAsync
import io.github.composefluent.winrt.runtime.await
import kotlinx.coroutines.runBlocking

private class AsyncPerf {
    private lateinit var instance: ClassWithAsync

    fun setup() {
        instance = ClassWithAsync()
    }

    suspend fun complete() {
        instance.complete().await()
    }

    suspend fun yieldComplete() {
        instance.yieldComplete().await()
    }

    suspend fun returnValue(): Int = instance.`return`(5).await()

    suspend fun yieldReturn(): Int = instance.yieldReturn(5).await()
}

internal fun asyncScenarios(): List<BenchmarkScenario> =
    listOf(
        asyncVoidScenario("Complete") { it.complete() },
        asyncVoidScenario("YieldComplete") { it.yieldComplete() },
        asyncIntScenario("Return") { it.returnValue() },
        asyncIntScenario("YieldReturn") { it.yieldReturn() },
    )

private fun asyncVoidScenario(
    method: String,
    invoke: suspend (AsyncPerf) -> Unit,
): BenchmarkScenario =
    BenchmarkScenario("AsyncPerf.$method", 1) {
        val benchmark = AsyncPerf().also(AsyncPerf::setup)
        PreparedBenchmarkScenario(
            runBatch = { iterations ->
                runBlocking { repeat(iterations) { invoke(benchmark) } }
                iterations.toLong()
            },
        )
    }

private fun asyncIntScenario(
    method: String,
    invoke: suspend (AsyncPerf) -> Int,
): BenchmarkScenario =
    BenchmarkScenario("AsyncPerf.$method", null) {
        val benchmark = AsyncPerf().also(AsyncPerf::setup)
        PreparedBenchmarkScenario(
            runBatch = { iterations ->
                runBlocking {
                    var checksum = 0L
                    repeat(iterations) { checksum += invoke(benchmark) }
                    checksum
                }
            },
        )
    }
