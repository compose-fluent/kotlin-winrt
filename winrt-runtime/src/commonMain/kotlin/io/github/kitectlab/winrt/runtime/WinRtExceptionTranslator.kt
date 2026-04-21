package io.github.kitectlab.winrt.runtime

internal object WinRtExceptionTranslator {
    fun exceptionFor(
        hResult: HResult,
        operation: String = "WinRT call",
    ): WinRtRuntimeException = ExceptionHelpers.exceptionFor(hResult, operation)

    fun hResultFromWin32(errorCode: Int): HResult = ExceptionHelpers.hResultFromWin32(errorCode)

    fun hResultFromException(error: Throwable): HResult = ExceptionHelpers.hResultFromException(error)
}
