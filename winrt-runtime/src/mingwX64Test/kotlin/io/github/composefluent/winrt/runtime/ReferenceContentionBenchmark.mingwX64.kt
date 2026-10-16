@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress("DEPRECATION")

package io.github.composefluent.winrt.runtime

import kotlinx.cinterop.toKString
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import platform.posix.getenv
import platform.windows.SwitchToThread

internal actual fun referenceContentionBenchmarkEnabled() = getenv("WINRT_CONTENTION_BENCHMARK")?.toKString() == "1"
internal actual fun referenceContentionBenchmarkMode(): String? = getenv("WINRT_CONTENTION_MODE")?.toKString()
internal actual fun referenceContentionYield() { SwitchToThread() }
internal actual fun runReferenceContentionWorkers(count: Int, action: (Int) -> Unit) {
    val workers = List(count) { Worker.start() }
    try {
        workers.mapIndexed { index, worker ->
            worker.execute(TransferMode.SAFE, { Pair(action, index) }) { (task, index) -> task(index) }
        }.forEach { it.result }
    } finally {
        workers.forEach { it.requestTermination().result }
    }
}
