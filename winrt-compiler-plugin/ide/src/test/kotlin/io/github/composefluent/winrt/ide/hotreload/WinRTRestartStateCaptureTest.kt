package io.github.composefluent.winrt.ide.hotreload

import io.github.composefluent.winrt.runtime.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WinRTRestartStateCaptureTest {
    private fun root(name: String) = WinUIXamlHotReloadRoot(name, "$name.xaml", "hash", 0, emptyList())
    private fun saved(text: String) = WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.APPLIED, "", savedState =
        listOf(WinUIXamlSavedElement("Input", "TextBox", listOf(WinUIXamlVisualProperty("Text", text)))))

    @Test fun transient_unloaded_component_is_recaptured_before_replacement() = runBlocking {
        var attempts = 0
        val snapshot = captureWinRTRestartState({ listOf(root("Window")) }) {
            if (++attempts == 1) WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.UNAVAILABLE, "Unloaded") else saved("latest")
        }
        assertEquals(2, attempts)
        assertEquals("latest", snapshot.requests.single().restore!!.single().values.single().value)
    }

    @Test fun navigation_during_capture_discards_the_obsolete_component_set() = runBlocking {
        var current = "OldPage"
        val snapshot = captureWinRTRestartState({ listOf(root(current)) }) { request ->
            current = "NewPage"
            saved(request.className)
        }
        assertEquals(listOf("NewPage"), snapshot.requests.map { it.className })
        assertEquals("NewPage", snapshot.requests.single().restore!!.single().values.single().value)
    }

    @Test fun permanent_capture_failure_aborts_without_retrying() {
        var attempts = 0
        assertThrows(IllegalStateException::class.java) { runBlocking {
            captureWinRTRestartState({ listOf(root("Window")) }) {
                attempts++
                WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.REJECTED, "Participant rejected state")
            }
        } }
        assertEquals(1, attempts)
    }
}
