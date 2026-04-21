package io.github.kitectlab.winrt.runtime

import kotlinx.coroutines.CancellationException

internal actual fun platformHResultFromThrowable(error: Throwable): HResult =
    when (error) {
        is WinRtRuntimeException -> error.hResult ?: ExceptionHelpers.E_FAIL
        is CancellationException -> ExceptionHelpers.ERROR_CANCELLED
        is NotImplementedError -> ExceptionHelpers.E_NOTIMPL
        is NullPointerException -> ExceptionHelpers.E_POINTER
        is IllegalArgumentException -> ExceptionHelpers.E_INVALIDARG
        is IndexOutOfBoundsException -> ExceptionHelpers.E_BOUNDS
        is UnsupportedOperationException -> ExceptionHelpers.E_NOTSUPPORTED
        is ArithmeticException -> ExceptionHelpers.ERROR_ARITHMETIC_OVERFLOW
        is OutOfMemoryError -> ExceptionHelpers.E_OUTOFMEMORY
        else -> ExceptionHelpers.E_FAIL
    }

internal actual fun platformSetErrorInfo(error: Throwable) {
    if (!PlatformRuntime.isWindows) {
        return
    }
    ManagedErrorInfoComObject(error).detachReference().use { errorInfo ->
        HResult(
            WinRtPlatformApi.setErrorInfoRaw(errorInfo.pointer),
        ).requireSuccess("SetErrorInfo")
    }
}
