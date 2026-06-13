@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.composefluent.winrt.samples

import kotlinx.cinterop.toKString
import platform.posix.getenv

internal actual fun winRtSampleOption(name: String): Boolean =
    getenv(name)?.toKString()?.toBooleanStrictOrNull() ?: false
