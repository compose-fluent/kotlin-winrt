@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.composefluent.winrt.runtime

import kotlinx.cinterop.toKString
import platform.posix.getenv

internal actual fun platformReadFeatureSwitch(propertyName: String): Boolean? =
    parseFeatureSwitchValue(getenv(propertyName)?.toKString())
