@file:OptIn(kotlin.native.runtime.NativeRuntimeApi::class)

package io.github.composefluent.winrt.runtime

import kotlin.native.runtime.GC

internal actual object PlatformFinalization {
    actual fun drain() {
        GC.collect()
    }
}
