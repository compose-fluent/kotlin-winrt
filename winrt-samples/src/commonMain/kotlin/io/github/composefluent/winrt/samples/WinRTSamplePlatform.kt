package io.github.composefluent.winrt.samples

internal const val WEBVIEW2_DEFAULT_BACKGROUND_COLOR_VARIABLE = "WEBVIEW2_DEFAULT_BACKGROUND_COLOR"

internal expect fun winRTSampleOption(name: String): Boolean

internal expect fun winRTSampleOptionConfigured(name: String): Boolean

internal expect fun configureWebView2TransparentBackground()

internal expect fun runWinUiSample()
