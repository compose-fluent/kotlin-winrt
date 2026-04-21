package io.github.kitectlab.winrt.runtime

internal actual fun platformHResultFromThrowable(error: Throwable): HResult =
    ExceptionHelpers.hResultFromException(error)

internal actual fun platformSetErrorInfo(error: Throwable) {
    ExceptionHelpers.setErrorInfo(error)
}

internal actual fun platformExceptionFor(
    hResult: HResult,
    operation: String,
): WinRtRuntimeException = ExceptionHelpers.exceptionFor(hResult, operation)
