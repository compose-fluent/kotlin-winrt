package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

internal actual fun platformTryInitializeGeneratedWinRtMetadata(type: KClass<*>) {
    // Native projection support is initialized through compiler-generated support registrars.
}
