package io.github.composefluent.winrt.runtime

internal actual object PlatformFinalization {
    actual fun collect() {
        System.gc()
    }

    actual fun drain() {
        System.gc()
        System.runFinalization()
        System.gc()
        System.runFinalization()
    }
}
