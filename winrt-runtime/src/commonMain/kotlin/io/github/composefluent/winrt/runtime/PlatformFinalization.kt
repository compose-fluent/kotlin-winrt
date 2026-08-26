package io.github.composefluent.winrt.runtime

internal expect object PlatformFinalization {
    fun collect()

    fun drain()
}
