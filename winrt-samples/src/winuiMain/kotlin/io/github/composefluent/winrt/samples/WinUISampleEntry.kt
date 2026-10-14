package io.github.composefluent.winrt.samples

internal actual fun runWinUISample() {
    if (shouldRunWebView2Sample()) {
        WebView2Sample.start()
    } else {
        WinUIControlsSample.start()
    }
}
