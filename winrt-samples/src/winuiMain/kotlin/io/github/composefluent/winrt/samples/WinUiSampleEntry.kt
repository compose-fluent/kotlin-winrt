package io.github.composefluent.winrt.samples

internal actual fun runWinUiSample() {
    if (shouldRunWebView2Sample()) {
        WebView2Sample.start()
    } else {
        WinUiControlsSample.start()
    }
}
