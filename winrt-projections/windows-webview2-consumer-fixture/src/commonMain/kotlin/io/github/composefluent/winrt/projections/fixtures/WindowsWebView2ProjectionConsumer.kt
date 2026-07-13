package io.github.composefluent.winrt.projections.fixtures

import microsoft.web.webview2.core.CoreWebView2
import windows.foundation.Size

internal data class WindowsWebView2ProjectionConsumer(
    val coreWebView: CoreWebView2,
    val size: Size,
)
