package io.github.composefluent.winrt.ide.debug

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** Windows permits one debugger per process. Reserve before starting LLDB so
 * concurrent IDE sessions cannot suspend each other's development connection. */
internal object WinRTNativeDebugOwnership {
    private class Lease(val started: Instant)
    private val processes = ConcurrentHashMap<Long, Lease>()

    fun contains(pid: Long): Boolean = processes.containsKey(pid)

    fun acquire(pid: Long, started: Instant): AutoCloseable {
        require(pid > 0)
        val lease = Lease(started)
        processes.compute(pid) { _, previous ->
            check(previous == null || previous.started != started) {
                "This Native process already has a Kotlin WinRT debug session. Stop that session before attaching again."
            }
            lease
        }
        return AutoCloseable { processes.remove(pid, lease) }
    }
}
