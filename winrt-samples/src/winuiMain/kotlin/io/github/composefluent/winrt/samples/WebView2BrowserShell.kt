package io.github.composefluent.winrt.samples

import microsoft.ui.xaml.Visibility

internal const val WEBVIEW2_HOME_MIN_WIDTH = 720.0

internal fun webView2HomeVisibility(windowWidth: Double): Visibility =
    if (windowWidth >= WEBVIEW2_HOME_MIN_WIDTH) {
        Visibility.Visible
    } else {
        Visibility.Collapsed
    }
