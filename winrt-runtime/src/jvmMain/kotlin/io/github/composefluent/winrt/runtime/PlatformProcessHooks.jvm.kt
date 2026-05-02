package io.github.composefluent.winrt.runtime

internal actual object PlatformProcessHooks {
    actual fun registerShutdownHook(cleanup: () -> Unit): AutoCloseable? {
        val hook = Thread(cleanup)
        Runtime.getRuntime().addShutdownHook(hook)
        return AutoCloseable {
            runCatching {
                Runtime.getRuntime().removeShutdownHook(hook)
            }
        }
    }
}
