package io.github.kitectlab.winrt.runtime

internal actual object PlatformProcessHooks {
    actual fun registerShutdownHook(cleanup: () -> Unit): AutoCloseable? = null
}
