package io.github.composefluent.winrt.samples

import microsoft.ui.xaml.controls.WebView2
import microsoft.web.webview2.core.CoreWebView2NavigationCompletedEventArgs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WebView2SampleTest {
    @Test
    fun normalizes_supported_addresses() {
        assertEquals("https://example.com", normalizeWebView2Address(" example.com "))
        assertEquals("http://localhost:8080", normalizeWebView2Address("http://localhost:8080"))
        assertEquals("https://localhost:8080", normalizeWebView2Address("localhost:8080"))
        assertEquals("https://example.com:8443/path", normalizeWebView2Address("example.com:8443/path"))
        assertEquals("https://127.0.0.1:8080", normalizeWebView2Address("127.0.0.1:8080"))
        assertEquals("https://[::1]:8080", normalizeWebView2Address("[::1]:8080"))
        assertEquals("https://openai.com", normalizeWebView2Address("https://openai.com"))
    }

    @Test
    fun rejects_blank_and_unsupported_addresses() {
        assertNull(normalizeWebView2Address("  "))
        assertNull(normalizeWebView2Address("file:///C:/private.html"))
        assertNull(normalizeWebView2Address("file:443"))
        assertNull(normalizeWebView2Address("mailto:user@example.com"))
        assertNull(normalizeWebView2Address("mailto:123"))
        assertNull(normalizeWebView2Address("about:blank"))
        assertNull(normalizeWebView2Address("about:123"))
    }

    @Test
    fun auto_exit_smoke_rethrows_recorded_failure() {
        val cause = IllegalStateException("WebView2 core unavailable")
        val failures = WebView2SampleFailures(autoExit = true)

        assertTrue(failures.recordForSmoke("WebView2 initialization failed", cause))

        val thrown = try {
            failures.throwIfPresent()
            fail("Expected the recorded smoke failure to be thrown.")
            error("unreachable")
        } catch (error: IllegalStateException) {
            error
        }
        assertEquals("WebView2 initialization failed", thrown.message)
        assertSame(cause, thrown.cause)
    }

    @Test
    fun smoke_failure_is_recorded_before_status_failure_and_still_exits() {
        val navigationFailure = IllegalStateException("navigation unavailable")
        val renderingFailure = IllegalArgumentException("status unavailable")
        val failures = WebView2SampleFailures(autoExit = true)
        var exitCount = 0

        reportWebView2Failure(
            failures = failures,
            message = "WebView2 navigation failed",
            cause = navigationFailure,
            renderStatus = { throw renderingFailure },
            exit = { exitCount += 1 },
        )

        assertEquals(1, exitCount)
        val thrown = try {
            failures.throwIfPresent()
            fail("Expected the recorded smoke failure to be thrown.")
            error("unreachable")
        } catch (error: IllegalStateException) {
            error
        }
        assertEquals("WebView2 navigation failed: navigation unavailable", thrown.message)
        assertSame(navigationFailure, thrown.cause)
        assertEquals(1, thrown.suppressed.size)
        val statusFailure = thrown.suppressed.single()
        assertEquals("WebView2 status update failed", statusFailure.message)
        assertSame(renderingFailure, statusFailure.cause)
    }

    @Test
    fun interactive_webview_failure_is_status_only() {
        val failures = WebView2SampleFailures(autoExit = false)
        val rendered = mutableListOf<String>()
        var exitCount = 0

        reportWebView2Failure(
            failures = failures,
            message = "WebView2 navigation failed",
            renderStatus = rendered::add,
            exit = { exitCount += 1 },
        )

        assertEquals(listOf("WebView2 navigation failed"), rendered)
        assertEquals(0, exitCount)
        failures.throwIfPresent()
    }

    @Test
    fun callback_boundary_records_failures_without_rethrowing_through_the_delegate() {
        val callbackFailure = IllegalStateException("callback unavailable")
        val exitFailure = IllegalArgumentException("exit unavailable")
        val failures = WebView2SampleFailures(autoExit = false)
        var exitCount = 0

        executeWebView2Callback(
            failures = failures,
            name = "navigation completed callback",
            exit = {
                exitCount += 1
                throw exitFailure
            },
        ) {
            throw callbackFailure
        }

        assertEquals(1, exitCount)
        val thrown = try {
            failures.throwIfPresent()
            fail("Expected the callback failure to be recorded.")
            error("unreachable")
        } catch (error: IllegalStateException) {
            error
        }
        assertEquals("WebView2 navigation completed callback failed", thrown.message)
        assertSame(callbackFailure, thrown.cause)
        assertEquals(1, thrown.suppressed.size)
        val recordedExitFailure = thrown.suppressed.single()
        assertEquals("WebView2 automatic exit failed", recordedExitFailure.message)
        assertSame(exitFailure, recordedExitFailure.cause)
    }

    @Test
    fun cleanup_attempts_every_action_and_suppresses_later_failures() {
        val attempts = mutableListOf<String>()
        val first = IllegalStateException("first cleanup failure")
        val second = IllegalArgumentException("second cleanup failure")

        val thrown = try {
            executeWebView2Cleanup(
                listOf(
                    { attempts += "first"; throw first },
                    { attempts += "middle" },
                    { attempts += "last"; throw second },
                ),
            )
            fail("Expected cleanup failure to be thrown.")
            error("unreachable")
        } catch (error: IllegalStateException) {
            error
        }

        assertEquals(listOf("first", "middle", "last"), attempts)
        assertSame(first, thrown)
        assertEquals(1, thrown.suppressed.size)
        assertSame(second, thrown.suppressed.single())
    }

    @Test
    fun closes_only_owned_webview_resources_in_order() {
        val attempts = mutableListOf<String>()

        closeWebView2Resources(
            closeInitializationAction = { attempts += "initialization" },
            closeWebView = { attempts += "webview" },
        )

        assertEquals(listOf("initialization", "webview"), attempts)
    }

    @Test
    fun generated_webview2_surface_is_available() {
        assertEquals("Microsoft.UI.Xaml.Controls.WebView2", WebView2.Metadata.TYPE_NAME)
        assertEquals(
            "Microsoft.Web.WebView2.Core.CoreWebView2NavigationCompletedEventArgs",
            CoreWebView2NavigationCompletedEventArgs.Metadata.TYPE_NAME,
        )
    }

    @Test
    fun reads_webview2_sample_selection() {
        val name = "kotlin.winrt.samples.runWebView2Sample"
        val previous = System.getProperty(name)
        try {
            System.setProperty(name, "true")
            assertTrue(shouldRunWebView2Sample())
        } finally {
            if (previous == null) {
                System.clearProperty(name)
            } else {
                System.setProperty(name, previous)
            }
        }
    }
}
