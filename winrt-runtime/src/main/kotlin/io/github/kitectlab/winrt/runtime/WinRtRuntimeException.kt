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

class WinRtUnsupportedOperationException(
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

internal object WinRtExceptionTranslator {
    fun exceptionFor(
        hResult: HResult,
        operation: String = "WinRT call",
    ): WinRtRuntimeException {
        val message = "$operation failed with $hResult"
        return when (hResult) {
            KnownHResults.E_INVALIDARG,
            KnownHResults.E_POINTER,
            -> WinRtIllegalArgumentException(message, hResult)

            KnownHResults.E_BOUNDS,
            -> WinRtIndexOutOfBoundsException(message, hResult)

            KnownHResults.E_CHANGED_STATE,
            KnownHResults.E_ILLEGAL_STATE_CHANGE,
            KnownHResults.E_ILLEGAL_METHOD_CALL,
            KnownHResults.E_ILLEGAL_DELEGATE_ASSIGNMENT,
            KnownHResults.APPMODEL_ERROR_NO_PACKAGE,
            KnownHResults.CO_E_NOTINITIALIZED,
            KnownHResults.REGDB_E_CLASSNOTREG,
            KnownHResults.RPC_E_CHANGED_MODE,
            -> WinRtIllegalStateException(message, hResult)

            KnownHResults.RO_E_CLOSED,
            -> WinRtObjectDisposedException(message, hResult)

            KnownHResults.E_NOTIMPL,
            KnownHResults.E_NOINTERFACE,
            KnownHResults.ERROR_BAD_FORMAT,
            -> WinRtUnsupportedOperationException(message, hResult)

            KnownHResults.E_ACCESSDENIED,
            -> WinRtAccessDeniedException(message, hResult)

            KnownHResults.ERROR_CANCELLED,
            -> WinRtCancelledException(message, hResult)

            KnownHResults.ERROR_TIMEOUT,
            -> WinRtTimeoutException(message, hResult)

            else -> WinRtRuntimeException(message, hResult)
        }
    }

    fun hResultFromWin32(errorCode: Int): HResult =
        if (errorCode <= 0) {
            HResult(errorCode)
        } else {
            HResult((errorCode and 0x0000FFFF) or 0x80070000.toInt())
        }
}
