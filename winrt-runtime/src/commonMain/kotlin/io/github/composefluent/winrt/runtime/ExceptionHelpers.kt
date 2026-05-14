package io.github.composefluent.winrt.runtime

import io.github.composefluent.winrt.runtime.exception.ManagedExceptionInterop

object ExceptionHelpers {
    val S_OK = KnownHResults.S_OK
    val S_FALSE = KnownHResults.S_FALSE
    val E_FAIL = KnownHResults.E_FAIL
    val E_BOUNDS = KnownHResults.E_BOUNDS
    val E_CHANGED_STATE = KnownHResults.E_CHANGED_STATE
    val E_ILLEGAL_STATE_CHANGE = KnownHResults.E_ILLEGAL_STATE_CHANGE
    val E_ILLEGAL_METHOD_CALL = KnownHResults.E_ILLEGAL_METHOD_CALL
    val RO_E_CLOSED = KnownHResults.RO_E_CLOSED
    val E_ILLEGAL_DELEGATE_ASSIGNMENT = KnownHResults.E_ILLEGAL_DELEGATE_ASSIGNMENT
    val E_XAMLPARSEFAILED = HResult(0x802B000A.toInt())
    val E_LAYOUTCYCLE = HResult(0x802B0014.toInt())
    val E_ELEMENTNOTENABLED = HResult(0x802B001E.toInt())
    val E_ELEMENTNOTAVAILABLE = HResult(0x802B001F.toInt())
    val ERROR_INVALID_WINDOW_HANDLE = HResult(0x80070578.toInt())
    val E_NOTIMPL = KnownHResults.E_NOTIMPL
    val E_NOINTERFACE = KnownHResults.E_NOINTERFACE
    val E_POINTER = KnownHResults.E_POINTER
    val CO_E_NOTINITIALIZED = KnownHResults.CO_E_NOTINITIALIZED
    val REGDB_E_CLASSNOTREG = KnownHResults.REGDB_E_CLASSNOTREG
    val RPC_E_CHANGED_MODE = KnownHResults.RPC_E_CHANGED_MODE
    val E_ACCESSDENIED = KnownHResults.E_ACCESSDENIED
    val E_OUTOFMEMORY = KnownHResults.E_OUTOFMEMORY
    val E_INVALIDARG = KnownHResults.E_INVALIDARG
    val E_NOTSUPPORTED = KnownHResults.E_NOTSUPPORTED
    val ERROR_FILE_NOT_FOUND = KnownHResults.ERROR_FILE_NOT_FOUND
    val ERROR_PATH_NOT_FOUND = KnownHResults.ERROR_PATH_NOT_FOUND
    val ERROR_BAD_FORMAT = KnownHResults.ERROR_BAD_FORMAT
    val ERROR_ARITHMETIC_OVERFLOW = HResult(0x80070216.toInt())
    val ERROR_FILENAME_EXCED_RANGE = HResult(0x800700CE.toInt())
    val ERROR_HANDLE_EOF = HResult(0x80070026.toInt())
    val ERROR_CANCELLED = KnownHResults.ERROR_CANCELLED
    val ERROR_TIMEOUT = KnownHResults.ERROR_TIMEOUT
    val APPMODEL_ERROR_NO_PACKAGE = KnownHResults.APPMODEL_ERROR_NO_PACKAGE
    val CLIPBRD_E_CANT_OPEN = KnownHResults.CLIPBRD_E_CANT_OPEN
    val WEB_E_JSON_VALUE_NOT_FOUND = KnownHResults.WEB_E_JSON_VALUE_NOT_FOUND

    fun throwExceptionForHR(
        hResult: Int,
        operation: String = "WinRT call",
    ) {
        if (hResult < 0) {
            throw exceptionFor(HResult(hResult), operation)
        }
    }

    fun exceptionFor(
        hResult: HResult,
        operation: String = "WinRT call",
    ): WinRtRuntimeException {
        val restrictedErrorInfo = tryGetRestrictedErrorInfo(hResult)
        val message = buildMessage(operation, hResult, restrictedErrorInfo)
        return when (hResult) {
            E_INVALIDARG -> WinRtIllegalArgumentException(message, hResult, restrictedErrorInfo)
            E_POINTER -> WinRtNullReferenceException(message, hResult, restrictedErrorInfo)
            E_BOUNDS -> WinRtIndexOutOfBoundsException(message, hResult, restrictedErrorInfo)
            E_CHANGED_STATE,
            E_ILLEGAL_STATE_CHANGE,
            E_ILLEGAL_METHOD_CALL,
            E_ILLEGAL_DELEGATE_ASSIGNMENT,
            APPMODEL_ERROR_NO_PACKAGE,
            CO_E_NOTINITIALIZED,
            REGDB_E_CLASSNOTREG,
            RPC_E_CHANGED_MODE,
            -> WinRtIllegalStateException(message, hResult, restrictedErrorInfo)

            E_XAMLPARSEFAILED -> WinRtXamlParseException(message, hResult, restrictedErrorInfo)
            E_LAYOUTCYCLE -> WinRtLayoutCycleException(message, hResult, restrictedErrorInfo)
            E_ELEMENTNOTAVAILABLE -> WinRtElementNotAvailableException(message, hResult, restrictedErrorInfo)
            E_ELEMENTNOTENABLED -> WinRtElementNotEnabledException(message, hResult, restrictedErrorInfo)
            ERROR_INVALID_WINDOW_HANDLE ->
                WinRtInvalidWindowHandleException(
                    message.ifBlank { invalidWindowHandleMessage() },
                    hResult,
                    restrictedErrorInfo,
                )

            RO_E_CLOSED -> WinRtObjectDisposedException(message, hResult, restrictedErrorInfo)
            E_NOTIMPL -> WinRtNotImplementedException(message, hResult, restrictedErrorInfo)
            E_NOINTERFACE -> WinRtInvalidCastException(message, hResult, restrictedErrorInfo)
            E_OUTOFMEMORY -> WinRtOutOfMemoryException(message, hResult, restrictedErrorInfo)
            E_NOTSUPPORTED -> WinRtUnsupportedOperationException(message, hResult, restrictedErrorInfo)
            E_ACCESSDENIED -> WinRtAccessDeniedException(message, hResult, restrictedErrorInfo)
            ERROR_ARITHMETIC_OVERFLOW -> WinRtArithmeticException(message, hResult, restrictedErrorInfo)
            ERROR_FILENAME_EXCED_RANGE -> WinRtPathTooLongException(message, hResult, restrictedErrorInfo)
            ERROR_FILE_NOT_FOUND -> WinRtFileNotFoundException(message, hResult, restrictedErrorInfo)
            ERROR_HANDLE_EOF -> WinRtEndOfFileException(message, hResult, restrictedErrorInfo)
            ERROR_PATH_NOT_FOUND -> WinRtDirectoryNotFoundException(message, hResult, restrictedErrorInfo)
            ERROR_BAD_FORMAT -> WinRtBadImageFormatException(message, hResult, restrictedErrorInfo)
            ERROR_CANCELLED -> WinRtCancelledException(message, hResult, restrictedErrorInfo)
            ERROR_TIMEOUT -> WinRtTimeoutException(message, hResult, restrictedErrorInfo)
            CLIPBRD_E_CANT_OPEN -> WinRtClipboardUnavailableException(message, hResult, restrictedErrorInfo)
            else -> WinRtRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)
        }
    }

    fun getHRForException(error: Throwable): HResult = hResultFromException(error)

    fun hResultFromWin32(errorCode: Int): HResult =
        if (errorCode <= 0) {
            HResult(errorCode)
        } else {
            HResult((errorCode and 0x0000FFFF) or 0x80070000.toInt())
        }

    fun hResultFromException(error: Throwable): HResult = platformHResultFromThrowable(error)

    fun setErrorInfo(error: Throwable) {
        if (!PlatformRuntime.isWindows) {
            return
        }
        runCatching {
            platformSetErrorInfo(error)
        }
    }

    fun reportUnhandledError(error: Throwable) {
        setErrorInfo(error)
        if (!PlatformRuntime.isWindows) {
            return
        }
        runCatching {
            val restrictedErrorInfo = WinRtPlatformApi.borrowRestrictedErrorInfoRaw() ?: return
            try {
                WinRtPlatformApi.reportUnhandledErrorRaw(restrictedErrorInfo)
                    ?.let(::HResult)
                    ?.requireSuccess("RoReportUnhandledError")
            } finally {
                IUnknownReference(restrictedErrorInfo.asRawComPtr(), IID.IRestrictedErrorInfo).close()
            }
        }
    }

    fun formatMessage(hResult: HResult): String? =
        if (PlatformRuntime.isWindows) {
            WinRtPlatformApi.tryFormatMessageRaw(hResult.value)
                ?.trimEnd('\r', '\n')
                ?.takeIf { it.isNotBlank() }
        } else {
            null
        }

    private fun buildMessage(
        operation: String,
        hResult: HResult,
        restrictedErrorInfo: WinRtRestrictedErrorInfo?,
    ): String {
        val restrictedMessage = buildRestrictedMessage(restrictedErrorInfo)
        val baseMessage = when {
            !restrictedMessage.isNullOrBlank() -> restrictedMessage
            hResult == ERROR_INVALID_WINDOW_HANDLE -> invalidWindowHandleMessage()
            else -> formatMessage(hResult)
        }
        return if (baseMessage.isNullOrBlank()) {
            "$operation failed with $hResult"
        } else {
            "$operation failed: $baseMessage ($hResult)"
        }
    }

    private fun invalidWindowHandleMessage(): String =
        "Invalid window handle. Consider WindowNative and InitializeWithWindow."

    private fun buildRestrictedMessage(restrictedErrorInfo: WinRtRestrictedErrorInfo?): String? {
        if (restrictedErrorInfo == null) {
            return null
        }
        return buildList {
            restrictedErrorInfo.description?.takeIf { it.isNotBlank() }?.let(::add)
            restrictedErrorInfo.restrictedDescription
                ?.takeIf { it.isNotBlank() && it != restrictedErrorInfo.description }
                ?.let(::add)
        }.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    private fun tryGetRestrictedErrorInfo(expectedHResult: HResult): WinRtRestrictedErrorInfo? {
        if (!PlatformRuntime.isWindows) {
            return null
        }
        val platformInfo = runCatching {
            val borrowedErrorInfo = WinRtPlatformApi.borrowRestrictedErrorInfoRaw() ?: return@runCatching null
            IUnknownReference(borrowedErrorInfo.asRawComPtr(), IID.IRestrictedErrorInfo).use { errorInfo ->
                val details = readRestrictedErrorInfo(errorInfo) ?: return@runCatching null
                if (details.hResult != expectedHResult) {
                    return@runCatching null
                }
                details.info
            }
        }.getOrNull()
        return platformInfo ?: ManagedExceptionInterop.retainedRestrictedErrorInfo(expectedHResult)
    }

    private fun readRestrictedErrorInfo(
        errorInfo: IUnknownReference,
    ): RestrictedErrorInfoDetails? =
        PlatformAbi.confinedScope().use { scope ->
            val descriptionOut = PlatformAbi.allocatePointerSlot(scope)
            val errorOut = PlatformAbi.allocateInt32Slot(scope)
            val restrictedDescriptionOut = PlatformAbi.allocatePointerSlot(scope)
            val capabilitySidOut = PlatformAbi.allocatePointerSlot(scope)
            throwExceptionForHR(
                ComVtableInvoker.invokeArgs(
                    errorInfo.pointer,
                    3,
                    descriptionOut,
                    errorOut,
                    restrictedDescriptionOut,
                    capabilitySidOut,
                ),
                operation = "IRestrictedErrorInfo.GetErrorDetails",
            )

            val referenceOut = PlatformAbi.allocatePointerSlot(scope)
            val referenceResult = HResult(
                ComVtableInvoker.invokeArgs(errorInfo.pointer, 4, referenceOut),
            )

            RestrictedErrorInfoDetails(
                hResult = HResult(PlatformAbi.readInt32(errorOut)),
                info = WinRtRestrictedErrorInfo(
                    description = readAndFreeBstr(descriptionOut).ifBlank { null },
                    restrictedDescription = readAndFreeBstr(restrictedDescriptionOut).ifBlank { null },
                    reference =
                        if (referenceResult.isSuccess) {
                            readAndFreeBstr(referenceOut).ifBlank { null }
                        } else {
                            null
                        },
                    capabilitySid = readAndFreeBstr(capabilitySidOut).ifBlank { null },
                ),
            )
        }

    private fun readAndFreeBstr(slot: RawAddress): String =
        WinRtPlatformApi.readAndFreeBstrRaw(PlatformAbi.readPointer(slot))

    private data class RestrictedErrorInfoDetails(
        val hResult: HResult,
        val info: WinRtRestrictedErrorInfo,
    )
}

// ---------------------------------------------------------------------------
// Exception → HResult mapping and error info propagation.
// ---------------------------------------------------------------------------

internal fun platformHResultFromThrowable(error: Throwable): HResult =
    when (error) {
        is WinRtRuntimeException -> error.hResult ?: ExceptionHelpers.E_FAIL
        is kotlin.coroutines.cancellation.CancellationException -> ExceptionHelpers.ERROR_CANCELLED
        is NotImplementedError -> ExceptionHelpers.E_NOTIMPL
        is NullPointerException -> ExceptionHelpers.E_POINTER
        is IllegalArgumentException -> ExceptionHelpers.E_INVALIDARG
        is IndexOutOfBoundsException -> ExceptionHelpers.E_BOUNDS
        is UnsupportedOperationException -> ExceptionHelpers.E_NOTSUPPORTED
        is ArithmeticException -> ExceptionHelpers.ERROR_ARITHMETIC_OVERFLOW
        is Error -> ExceptionHelpers.E_OUTOFMEMORY
        else -> ExceptionHelpers.E_FAIL
    }

internal fun platformSetErrorInfo(error: Throwable) {
    if (!PlatformRuntime.isWindows) return
    runCatching {
        ManagedExceptionInterop.setErrorInfo(error)
    }
}
