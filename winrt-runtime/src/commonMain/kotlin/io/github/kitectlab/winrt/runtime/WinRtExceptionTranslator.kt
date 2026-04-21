package io.github.kitectlab.winrt.runtime

internal object WinRtExceptionTranslator {
    fun exceptionFor(
        hResult: HResult,
        operation: String = "WinRT call",
    ): WinRtRuntimeException = platformExceptionFor(hResult, operation)

    fun hResultFromWin32(errorCode: Int): HResult =
        if (errorCode <= 0) {
            HResult(errorCode)
        } else {
            HResult((errorCode and 0x0000FFFF) or 0x80070000.toInt())
        }

    fun hResultFromException(error: Throwable): HResult = platformHResultFromThrowable(error)
}
