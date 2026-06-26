package io.github.composefluent.winrt.runtime

internal object WinRTExceptionTranslator {
    fun exceptionFor(
        hResult: HResult,
        operation: String = "WinRT call",
    ): WinRTRuntimeException = ExceptionHelpers.exceptionFor(hResult, operation)

    fun hResultFromWin32(errorCode: Int): HResult = ExceptionHelpers.hResultFromWin32(errorCode)

    fun hResultFromException(error: Throwable): HResult = ExceptionHelpers.hResultFromException(error)
}
