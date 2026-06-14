@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.composefluent.winrt.samples.kmp.library

import kotlinx.cinterop.toKString
import platform.posix.getenv

internal actual object WinUiKmpSamplePlatform {
    actual fun option(name: String): Boolean =
        getenv(name)?.toKString()?.toBooleanStrictOrNull() ?: false

    actual fun scheduleTimerTimeout(action: () -> Unit) {
        // The timeout is only a guard for JVM sample validation; native timer dispatch is checked by the tick callback.
    }
}
