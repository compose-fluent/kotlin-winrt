package io.github.kitectlab.winrt.runtime

internal expect fun platformHResultFromThrowable(error: Throwable): HResult

internal expect fun platformSetErrorInfo(error: Throwable)

internal expect fun platformExceptionFor(
    hResult: HResult,
    operation: String = "WinRT call",
): WinRtRuntimeException
