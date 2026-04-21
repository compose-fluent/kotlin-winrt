package io.github.kitectlab.winrt.runtime

internal actual fun platformHResultFromThrowable(error: Throwable): HResult =
    WinRtExceptionTranslator.hResultFromException(error)

internal actual fun platformSetErrorInfo(error: Throwable) {
    ExceptionHelpers.setErrorInfo(error)
}
