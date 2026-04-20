package io.github.kitectlab.winrt.runtime

internal expect object PlatformProcessHooks {
    fun registerShutdownHook(cleanup: () -> Unit): AutoCloseable?
}
