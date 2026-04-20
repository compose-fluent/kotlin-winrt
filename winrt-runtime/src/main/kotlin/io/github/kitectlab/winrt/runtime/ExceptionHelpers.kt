package io.github.kitectlab.winrt.runtime

import java.util.concurrent.CancellationException
import java.util.concurrent.TimeoutException

object ExceptionHelpers {
    val S_OK = HResult(0x00000000)
    val S_FALSE = HResult(0x00000001)
    val E_FAIL = HResult(0x80004005.toInt())
    val E_BOUNDS = HResult(0x8000000B.toInt())
    val E_CHANGED_STATE = HResult(0x8000000C.toInt())
    val E_ILLEGAL_STATE_CHANGE = HResult(0x8000000D.toInt())
    val E_ILLEGAL_METHOD_CALL = HResult(0x8000000E.toInt())
    val RO_E_CLOSED = HResult(0x80000013.toInt())
    val E_ILLEGAL_DELEGATE_ASSIGNMENT = HResult(0x80000018.toInt())
    val E_NOTIMPL = HResult(0x80004001.toInt())
    val E_NOINTERFACE = HResult(0x80004002.toInt())
    val E_POINTER = HResult(0x80004003.toInt())
    val CO_E_NOTINITIALIZED = HResult(0x800401F0.toInt())
    val REGDB_E_CLASSNOTREG = HResult(0x80040154.toInt())
    val RPC_E_CHANGED_MODE = HResult(0x80010106.toInt())
    val E_ACCESSDENIED = HResult(0x80070005.toInt())
    val E_OUTOFMEMORY = HResult(0x8007000E.toInt())
    val E_INVALIDARG = HResult(0x80070057.toInt())
    val E_NOTSUPPORTED = HResult(0x80070032.toInt())
    val ERROR_FILE_NOT_FOUND = HResult(0x80070002.toInt())
    val ERROR_PATH_NOT_FOUND = HResult(0x80070003.toInt())
    val ERROR_BAD_FORMAT = HResult(0x8007000B.toInt())
    val ERROR_CANCELLED = HResult(0x800704C7.toInt())
    val ERROR_TIMEOUT = HResult(0x800705B4.toInt())
    val APPMODEL_ERROR_NO_PACKAGE = HResult(0x80073D54.toInt())
    val WEB_E_JSON_VALUE_NOT_FOUND = HResult(0x83750009.toInt())

    fun exceptionFor(
        hResult: HResult,
        operation: String = "WinRT call",
    ): WinRtRuntimeException {
        val message = buildMessage(operation, hResult)
        return when (hResult) {
            E_INVALIDARG -> WinRtIllegalArgumentException(message, hResult)
            E_POINTER -> WinRtNullReferenceException(message, hResult)
            E_BOUNDS -> WinRtIndexOutOfBoundsException(message, hResult)
            E_CHANGED_STATE,
            E_ILLEGAL_STATE_CHANGE,
            E_ILLEGAL_METHOD_CALL,
            E_ILLEGAL_DELEGATE_ASSIGNMENT,
            APPMODEL_ERROR_NO_PACKAGE,
            CO_E_NOTINITIALIZED,
            REGDB_E_CLASSNOTREG,
            RPC_E_CHANGED_MODE,
            -> WinRtIllegalStateException(message, hResult)

            RO_E_CLOSED -> WinRtObjectDisposedException(message, hResult)
            E_NOTIMPL -> WinRtNotImplementedException(message, hResult)
            E_NOINTERFACE -> WinRtInvalidCastException(message, hResult)
            E_OUTOFMEMORY -> WinRtOutOfMemoryException(message, hResult)
            E_NOTSUPPORTED,
            ERROR_BAD_FORMAT,
            -> WinRtUnsupportedOperationException(message, hResult)

            E_ACCESSDENIED -> WinRtAccessDeniedException(message, hResult)
            ERROR_CANCELLED -> WinRtCancelledException(message, hResult)
            ERROR_TIMEOUT -> WinRtTimeoutException(message, hResult)
            else -> WinRtRuntimeException(message, hResult)
        }
    }

    fun hResultFromWin32(errorCode: Int): HResult =
        if (errorCode <= 0) {
            HResult(errorCode)
        } else {
            HResult((errorCode and 0x0000FFFF) or 0x80070000.toInt())
        }

    fun hResultFromException(error: Throwable): HResult =
        when (error) {
            is WinRtRuntimeException -> error.hResult ?: E_FAIL
            is CancellationException -> ERROR_CANCELLED
            is TimeoutException -> ERROR_TIMEOUT
            is IndexOutOfBoundsException -> E_BOUNDS
            is IllegalArgumentException -> E_INVALIDARG
            is UnsupportedOperationException -> E_NOTSUPPORTED
            is OutOfMemoryError -> E_OUTOFMEMORY
            else -> E_FAIL
        }

    fun formatMessage(hResult: HResult): String? =
        if (PlatformRuntime.isWindows) {
            WindowsRuntimePlatform.tryFormatMessage(hResult)
                ?.trimEnd('\r', '\n')
                ?.takeIf { it.isNotBlank() }
        } else {
            null
        }

    private fun buildMessage(operation: String, hResult: HResult): String {
        val systemMessage = formatMessage(hResult)
        return if (systemMessage.isNullOrBlank()) {
            "$operation failed with $hResult"
        } else {
            "$operation failed: $systemMessage ($hResult)"
        }
    }
}
