package io.github.composefluent.winrt.runtime

internal actual class PlatformThreadLocalInt actual constructor(initialValue: Int) {
    private var value: Int = initialValue

    actual fun get(): Int = value

    actual fun set(value: Int) {
        this.value = value
    }
}
