package io.github.kitectlab.winrt.runtime

open class WinRtRuntimeException(
    message: String,
    val hResult: HResult? = null,
    cause: Throwable? = null,
    val restrictedErrorInfo: WinRtRestrictedErrorInfo? = null,
) : RuntimeException(message, cause)

data class WinRtRestrictedErrorInfo(
    val description: String?,
    val restrictedDescription: String?,
    val reference: String?,
    val capabilitySid: String?,
)

class WinRtIllegalArgumentException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRtRestrictedErrorInfo? = null,
) : WinRtRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRtNullReferenceException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRtRestrictedErrorInfo? = null,
) : WinRtRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRtIllegalStateException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRtRestrictedErrorInfo? = null,
) : WinRtRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRtObjectDisposedException(
    message: String,
    hResult: HResult? = null,
    restrictedErrorInfo: WinRtRestrictedErrorInfo? = null,
) : WinRtRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRtIndexOutOfBoundsException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRtRestrictedErrorInfo? = null,
) : WinRtRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

open class WinRtUnsupportedOperationException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRtRestrictedErrorInfo? = null,
) : WinRtRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRtNotImplementedException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRtRestrictedErrorInfo? = null,
) : WinRtUnsupportedOperationException(message, hResult, restrictedErrorInfo)

class WinRtInvalidCastException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRtRestrictedErrorInfo? = null,
) : WinRtRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRtAccessDeniedException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRtRestrictedErrorInfo? = null,
) : WinRtRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRtTimeoutException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRtRestrictedErrorInfo? = null,
) : WinRtRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRtCancelledException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRtRestrictedErrorInfo? = null,
) : WinRtRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRtOutOfMemoryException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRtRestrictedErrorInfo? = null,
) : WinRtRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRtArithmeticException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRtRestrictedErrorInfo? = null,
) : WinRtRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRtPathTooLongException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRtRestrictedErrorInfo? = null,
) : WinRtRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRtFileNotFoundException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRtRestrictedErrorInfo? = null,
) : WinRtRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRtDirectoryNotFoundException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRtRestrictedErrorInfo? = null,
) : WinRtRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRtEndOfFileException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRtRestrictedErrorInfo? = null,
) : WinRtRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRtBadImageFormatException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRtRestrictedErrorInfo? = null,
) : WinRtRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRtInvalidWindowHandleException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRtRestrictedErrorInfo? = null,
) : WinRtRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRtXamlParseException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRtRestrictedErrorInfo? = null,
) : WinRtRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRtLayoutCycleException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRtRestrictedErrorInfo? = null,
) : WinRtRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRtElementNotEnabledException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRtRestrictedErrorInfo? = null,
) : WinRtRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRtElementNotAvailableException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRtRestrictedErrorInfo? = null,
) : WinRtRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

internal object WinRtExceptionTranslator {
    fun exceptionFor(
        hResult: HResult,
        operation: String = "WinRT call",
    ): WinRtRuntimeException = ExceptionHelpers.exceptionFor(hResult, operation)

    fun hResultFromWin32(errorCode: Int): HResult = ExceptionHelpers.hResultFromWin32(errorCode)

    fun hResultFromException(error: Throwable): HResult = ExceptionHelpers.hResultFromException(error)
}
