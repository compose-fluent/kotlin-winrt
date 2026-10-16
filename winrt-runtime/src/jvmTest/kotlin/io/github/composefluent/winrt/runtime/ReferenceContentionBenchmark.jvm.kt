package io.github.composefluent.winrt.runtime

import java.util.concurrent.Executors

internal actual fun referenceContentionBenchmarkEnabled() = System.getenv("WINRT_CONTENTION_BENCHMARK") == "1"
internal actual fun referenceContentionYield() = Thread.yield()
internal actual fun runReferenceContentionWorkers(count: Int, action: (Int) -> Unit) {
    val pool = Executors.newFixedThreadPool(count)
    try {
        (0 until count).map { index -> pool.submit { action(index) } }.forEach { it.get() }
    } finally {
        pool.shutdown()
    }
}
