package io.github.composefluent.winrt.ide.debug

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletableFuture
import java.nio.file.Path

internal interface WinRTNativeDebugRestarter {
    fun detachForRestart(): CompletableFuture<Unit>
    fun attachAfterRestart(pid: Long, started: Instant, executable: Path): CompletableFuture<Unit>
    fun restartFailed(error: Throwable)
}

/** Windows permits one debugger per process. Reserve before starting LLDB so
 * concurrent IDE sessions cannot suspend each other's development connection. */
internal object WinRTNativeDebugOwnership {
    private class Lease(val started: Instant, val restarter: WinRTNativeDebugRestarter?)
    private val processes = ConcurrentHashMap<Long, Lease>()

    fun contains(pid: Long): Boolean = processes.containsKey(pid)

    fun restarter(pid: Long, started: Instant): WinRTNativeDebugRestarter? = processes[pid]?.takeIf { it.started == started }?.restarter

    fun acquire(pid: Long, started: Instant, restarter: WinRTNativeDebugRestarter? = null): AutoCloseable {
        require(pid > 0)
        val lease = Lease(started, restarter)
        processes.compute(pid) { _, previous ->
            check(previous == null || previous.started != started) {
                "This Native process already has a Kotlin WinRT debug session. Stop that session before attaching again."
            }
            lease
        }
        return AutoCloseable { processes.remove(pid, lease) }
    }
}
