package io.github.kitectlab.winrt.runtime

import java.io.EOFException
import java.io.FileNotFoundException
import java.nio.file.InvalidPathException
import java.nio.file.NoSuchFileException
import kotlinx.coroutines.CancellationException

internal actual fun platformHResultFromThrowable(error: Throwable): HResult =
    when (error) {
        is WinRtRuntimeException -> error.hResult ?: ExceptionHelpers.E_FAIL
        is CancellationException -> ExceptionHelpers.ERROR_CANCELLED
        is NotImplementedError -> ExceptionHelpers.E_NOTIMPL
        is InvalidPathException -> ExceptionHelpers.ERROR_PATH_NOT_FOUND
        is FileNotFoundException,
        is NoSuchFileException,
        -> ExceptionHelpers.ERROR_FILE_NOT_FOUND
        is EOFException -> ExceptionHelpers.ERROR_HANDLE_EOF
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
