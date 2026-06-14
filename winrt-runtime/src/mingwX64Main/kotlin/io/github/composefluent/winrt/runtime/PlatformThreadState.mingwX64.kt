package io.github.composefluent.winrt.runtime

import kotlin.native.concurrent.ThreadLocal

internal actual class PlatformThreadLocalInt actual constructor(
    private val initialValue: Int,
) {
    actual fun get(): Int =
        PlatformThreadLocalIntValues.get(this, initialValue)

    actual fun set(value: Int) {
        PlatformThreadLocalIntValues.set(this, value)
    }
}

@ThreadLocal
private object PlatformThreadLocalIntValues {
    private val values = mutableMapOf<PlatformThreadLocalInt, Int>()

    fun get(
        key: PlatformThreadLocalInt,
        initialValue: Int,
    ): Int =
        values.getOrPut(key) { initialValue }

    fun set(
        key: PlatformThreadLocalInt,
        value: Int,
    ) {
        values[key] = value
    }
}
