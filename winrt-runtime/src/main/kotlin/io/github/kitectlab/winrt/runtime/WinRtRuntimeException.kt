package io.github.kitectlab.winrt.runtime

open class WinRtRuntimeException(
    message: String,
    val hResult: HResult? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class WinRtIllegalArgumentException(
    message: String,
    hResult: HResult,
) : WinRtRuntimeException(message, hResult)

class WinRtNullReferenceException(
    message: String,
    hResult: HResult,
) : WinRtRuntimeException(message, hResult)

class WinRtIllegalStateException(
    message: String,
    hResult: HResult,
) : WinRtRuntimeException(message, hResult)

class WinRtObjectDisposedException(
    message: String,
    hResult: HResult? = null,
) : WinRtRuntimeException(message, hResult)

class WinRtIndexOutOfBoundsException(
    message: String,
    hResult: HResult,
) : WinRtRuntimeException(message, hResult)

open class WinRtUnsupportedOperationException(
    message: String,
    hResult: HResult,
) : WinRtRuntimeException(message, hResult)

class WinRtNotImplementedException(
    message: String,
    hResult: HResult,
) : WinRtUnsupportedOperationException(message, hResult)

class WinRtInvalidCastException(
    message: String,
    hResult: HResult,
) : WinRtRuntimeException(message, hResult)

class WinRtAccessDeniedException(
    message: String,
    hResult: HResult,
) : WinRtRuntimeException(message, hResult)

class WinRtTimeoutException(
    message: String,
    hResult: HResult,
) : WinRtRuntimeException(message, hResult)

class WinRtCancelledException(
    message: String,
    hResult: HResult,
) : WinRtRuntimeException(message, hResult)

class WinRtOutOfMemoryException(
    message: String,
    hResult: HResult,
) : WinRtRuntimeException(message, hResult)

internal object WinRtExceptionTranslator {
    fun exceptionFor(
        hResult: HResult,
        operation: String = "WinRT call",
    ): WinRtRuntimeException = ExceptionHelpers.exceptionFor(hResult, operation)

    fun hResultFromWin32(errorCode: Int): HResult = ExceptionHelpers.hResultFromWin32(errorCode)

    fun hResultFromException(error: Throwable): HResult = ExceptionHelpers.hResultFromException(error)
}
