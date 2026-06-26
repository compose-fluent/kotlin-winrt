@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.composefluent.winrt.samples

import kotlinx.cinterop.toKString
import platform.posix.getenv

internal actual fun winRTSampleOption(name: String): Boolean =
    getenv(name)?.toKString()?.toBooleanStrictOrNull() ?: false

internal actual fun winRTSampleOptionConfigured(name: String): Boolean =
    getenv(name) != null
