package io.github.composefluent.winrt.projections.fixtures

import microsoft.ui.xaml.controls.WebView2
import microsoft.web.webview2.core.CoreWebView2
import microsoft.ui.windowing.AppWindow
import windows.foundation.Point

internal data class WindowsAppSdkProjectionConsumer(
    val appWindow: AppWindow,
    val webView: WebView2,
    val coreWebView: CoreWebView2,
    val point: Point,
)
