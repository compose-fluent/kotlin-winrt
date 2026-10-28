@file:OptIn(io.github.composefluent.winrt.runtime.InternalWinRTApi::class)

package io.github.composefluent.winrt.ide.hotreload

import io.github.composefluent.winrt.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

internal data class WinRTRestartStateCapture(
    val requests: List<WinUIXamlStateRequest>, val skippedComponents: List<String>,
)

/** Loaded/Unloaded and navigation can change component keys between protocol
 * requests. Capture one stable set before stopping the old process. Permanent
 * failures abort replacement; transient unavailable roots retry within a deadline.
 */
internal suspend fun captureWinRTRestartState(
    roots: () -> List<WinUIXamlHotReloadRoot>,
    capture: (WinUIXamlStateRequest) -> WinUIXamlHotReloadReply,
): WinRTRestartStateCapture = withTimeout(15_000) {
    fun plan() = restartStatePlan(roots().filterNot { it.className == WinUIXamlHotReloadProtocol.PREVIEW_CLASS })
    fun same(first: WinRTRestartStatePlan, second: WinRTRestartStatePlan) =
        first.requests.toSet() == second.requests.toSet() && first.skippedComponents.toSet() == second.skippedComponents.toSet()
    var result: WinRTRestartStateCapture? = null
    while (result == null) {
        val before = plan()
        // Let deferred SDK lifecycle events settle without blocking the UI thread.
        delay(100)
        if (!same(before, plan())) continue
        val states = mutableListOf<WinUIXamlStateRequest>()
        var unavailable = false
        for (request in before.requests) {
            val reply = capture(request)
            if (reply.status == WinUIXamlHotReloadProtocol.UNAVAILABLE) { unavailable = true; break }
            check(reply.status == WinUIXamlHotReloadProtocol.APPLIED) { reply.message }
            states += request.copy(restore = reply.savedState.orEmpty())
        }
        if (!unavailable && same(before, plan())) result = WinRTRestartStateCapture(states, before.skippedComponents)
    }
    requireNotNull(result)
}
