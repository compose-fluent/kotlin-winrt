package io.github.kitectlab.winrt.runtime

internal actual fun platformHResultFromThrowable(error: Throwable): HResult =
    (error as? WinRtRuntimeException)?.hResult ?: KnownHResults.E_FAIL

internal actual fun platformSetErrorInfo(error: Throwable) {}

internal actual fun platformExceptionFor(
    hResult: HResult,
    operation: String,
): WinRtRuntimeException = WinRtRuntimeException("$operation failed with HRESULT 0x${hResult.value.toUInt().toString(16)}.", hResult)
