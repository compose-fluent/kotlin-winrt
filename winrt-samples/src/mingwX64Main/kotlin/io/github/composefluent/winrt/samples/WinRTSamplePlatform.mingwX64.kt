@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.composefluent.winrt.samples

import kotlinx.cinterop.toKString
import platform.posix.getenv
import platform.windows.GetLastError
import platform.windows.SetEnvironmentVariableW

internal actual fun winRTSampleOption(name: String): Boolean =
    getenv(name)?.toKString()?.toBooleanStrictOrNull() ?: false

internal actual fun winRTSampleOptionConfigured(name: String): Boolean =
    getenv(name) != null

internal actual fun configureWebView2TransparentBackground() {
    val result = SetEnvironmentVariableW(WEBVIEW2_DEFAULT_BACKGROUND_COLOR_VARIABLE, "0")
    check(result != 0) {
        "SetEnvironmentVariableW failed with GetLastError=${GetLastError()} for $WEBVIEW2_DEFAULT_BACKGROUND_COLOR_VARIABLE"
    }
}
