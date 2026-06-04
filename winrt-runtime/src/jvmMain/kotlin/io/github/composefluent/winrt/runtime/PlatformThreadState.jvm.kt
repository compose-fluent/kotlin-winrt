package io.github.composefluent.winrt.runtime

internal actual class PlatformThreadLocalInt actual constructor(initialValue: Int) {
    private val local = ThreadLocal.withInitial { initialValue }

    actual fun get(): Int = local.get()

    actual fun set(value: Int) {
        local.set(value)
    }
}
