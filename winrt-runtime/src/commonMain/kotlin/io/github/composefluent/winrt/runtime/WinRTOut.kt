package io.github.composefluent.winrt.runtime

/** Mutable projected storage for a WinMD non-array `out` parameter. */
class WinRTOut<T> {
    private var state: Any? = UNINITIALIZED

    constructor()

    constructor(value: T) {
        state = value
    }

    val isInitialized: Boolean
        get() = state !== UNINITIALIZED

    var value: T
        @Suppress("UNCHECKED_CAST")
        get() {
            check(isInitialized) { "WinRT out value has not been initialized." }
            return state as T
        }
        set(value) {
            state = value
        }

    fun clear() {
        state = UNINITIALIZED
    }

    private companion object {
        private val UNINITIALIZED = Any()
    }
}
