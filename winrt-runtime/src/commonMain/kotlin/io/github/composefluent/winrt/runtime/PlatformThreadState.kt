package io.github.composefluent.winrt.runtime

internal expect class PlatformThreadLocalInt(initialValue: Int = 0) {
    fun get(): Int
    fun set(value: Int)
}
