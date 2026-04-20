package io.github.kitectlab.winrt.runtime

import kotlin.jvm.JvmInline

@JvmInline
value class HResult(val value: Int) {
    val isSuccess: Boolean
        get() = value >= 0

    val isFailure: Boolean
        get() = value < 0

    override fun toString(): String = "0x${value.toUInt().toString(16).uppercase().padStart(8, '0')}"

    fun requireSuccess(operation: String = "WinRT call"): HResult {
        if (isFailure) {
            throwHResultFailure(this, operation)
        }
        return this
    }
}

internal expect fun throwHResultFailure(hResult: HResult, operation: String): Nothing
