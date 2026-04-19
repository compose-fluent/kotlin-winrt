package io.github.kitectlab.winrt.runtime

@JvmInline
value class HResult(val value: Int) {
    val isSuccess: Boolean
        get() = value >= 0

    val isFailure: Boolean
        get() = value < 0

    override fun toString(): String = "0x%08X".format(value)

    fun requireSuccess(operation: String = "WinRT call"): HResult {
        if (isFailure) {
            throw WinRtExceptionTranslator.exceptionFor(this, operation)
        }
        return this
    }
}
