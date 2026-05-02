package io.github.composefluent.winrt.runtime

internal expect object PlatformProcessHooks {
    fun registerShutdownHook(cleanup: () -> Unit): AutoCloseable?
}
