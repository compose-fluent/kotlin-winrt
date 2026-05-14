package io.github.composefluent.winrt.runtime

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WinRtClipboardInteropTest {
    @Test
    fun clipboard_open_failure_maps_to_transient_clipboard_exception() {
        val error = ExceptionHelpers.exceptionFor(KnownHResults.CLIPBRD_E_CANT_OPEN, "Clipboard.SetContent")

        assertTrue(error is WinRtClipboardUnavailableException)
        assertEquals(KnownHResults.CLIPBRD_E_CANT_OPEN, error.hResult)
        assertTrue(WinRtClipboardInterop.isTransientClipboardOpenFailure(error))
    }

    @Test
    fun retry_transient_clipboard_access_retries_clipboard_open_failure() {
        var attempts = 0

        val result = runBlocking {
            WinRtClipboardInterop.retryTransientClipboardAccess(
                attempts = 3,
                initialDelayMillis = 0,
                maxDelayMillis = 0,
            ) {
                attempts += 1
                if (attempts < 3) {
                    throw WinRtClipboardUnavailableException(
                        "Clipboard is locked.",
                        KnownHResults.CLIPBRD_E_CANT_OPEN,
                    )
                }
                "copied"
            }
        }

        assertEquals("copied", result)
        assertEquals(3, attempts)
    }

    @Test
    fun retry_transient_clipboard_access_does_not_retry_other_hresult_failures() {
        var attempts = 0

        try {
            runBlocking {
                WinRtClipboardInterop.retryTransientClipboardAccess(
                    attempts = 3,
                    initialDelayMillis = 0,
                    maxDelayMillis = 0,
                ) {
                    attempts += 1
                    throw WinRtAccessDeniedException("denied", KnownHResults.E_ACCESSDENIED)
                }
            }
        } catch (error: WinRtAccessDeniedException) {
            assertFalse(WinRtClipboardInterop.isTransientClipboardOpenFailure(error))
            assertEquals(1, attempts)
            return
        }

        throw AssertionError("Expected non-transient clipboard failure to be rethrown.")
    }

    @Test
    fun retry_transient_clipboard_access_blocking_uses_same_retry_policy() {
        var attempts = 0

        val result = WinRtClipboardInterop.retryTransientClipboardAccessBlocking(
            attempts = 2,
            initialDelayMillis = 0,
            maxDelayMillis = 0,
        ) {
            attempts += 1
            if (attempts == 1) {
                throw WinRtClipboardUnavailableException(
                    "Clipboard is locked.",
                    KnownHResults.CLIPBRD_E_CANT_OPEN,
                )
            }
            42
        }

        assertEquals(42, result)
        assertEquals(2, attempts)
    }
}
