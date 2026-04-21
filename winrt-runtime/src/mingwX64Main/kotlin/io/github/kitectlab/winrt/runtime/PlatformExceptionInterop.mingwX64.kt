package io.github.kitectlab.winrt.runtime

internal actual fun platformHResultFromThrowable(error: Throwable): HResult =
    (error as? WinRtRuntimeException)?.hResult ?: KnownHResults.E_FAIL

internal actual fun platformSetErrorInfo(error: Throwable) {}
