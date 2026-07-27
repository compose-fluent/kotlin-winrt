package io.github.composefluent.winrt.samples

import microsoft.ui.xaml.controls.WebView2
import microsoft.web.webview2.core.CoreWebView2NavigationCompletedEventArgs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebView2SampleTest {
    @Test
    fun normalizes_supported_addresses() {
        assertEquals("https://example.com", normalizeWebView2Address(" example.com "))
        assertEquals("http://localhost:8080", normalizeWebView2Address("http://localhost:8080"))
        assertEquals("https://openai.com", normalizeWebView2Address("https://openai.com"))
    }

    @Test
    fun rejects_blank_and_unsupported_addresses() {
        assertNull(normalizeWebView2Address("  "))
        assertNull(normalizeWebView2Address("file:///C:/private.html"))
    }

    @Test
    fun generated_webview2_surface_is_available() {
        assertEquals("Microsoft.UI.Xaml.Controls.WebView2", WebView2.Metadata.TYPE_NAME)
        assertEquals(
            "Microsoft.Web.WebView2.Core.CoreWebView2NavigationCompletedEventArgs",
            CoreWebView2NavigationCompletedEventArgs.Metadata.TYPE_NAME,
        )
    }
}
