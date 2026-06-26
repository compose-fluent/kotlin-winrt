package io.github.composefluent.winrt.runtime

open class WinRTRuntimeException(
    message: String,
    val hResult: HResult? = null,
    cause: Throwable? = null,
    val restrictedErrorInfo: WinRTRestrictedErrorInfo? = null,
) : RuntimeException(message, cause)

data class WinRTRestrictedErrorInfo(
    val description: String?,
    val restrictedDescription: String?,
    val reference: String?,
    val capabilitySid: String?,
)

class WinRTIllegalArgumentException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRTRestrictedErrorInfo? = null,
) : WinRTRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRTNullReferenceException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRTRestrictedErrorInfo? = null,
) : WinRTRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRTIllegalStateException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRTRestrictedErrorInfo? = null,
) : WinRTRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRTObjectDisposedException(
    message: String,
    hResult: HResult? = null,
    restrictedErrorInfo: WinRTRestrictedErrorInfo? = null,
) : WinRTRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRTIndexOutOfBoundsException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRTRestrictedErrorInfo? = null,
) : WinRTRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

open class WinRTUnsupportedOperationException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRTRestrictedErrorInfo? = null,
) : WinRTRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRTNotImplementedException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRTRestrictedErrorInfo? = null,
) : WinRTUnsupportedOperationException(message, hResult, restrictedErrorInfo)

class WinRTInvalidCastException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRTRestrictedErrorInfo? = null,
) : WinRTRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRTAccessDeniedException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRTRestrictedErrorInfo? = null,
) : WinRTRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRTTimeoutException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRTRestrictedErrorInfo? = null,
) : WinRTRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRTCancelledException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRTRestrictedErrorInfo? = null,
) : WinRTRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRTOutOfMemoryException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRTRestrictedErrorInfo? = null,
) : WinRTRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRTArithmeticException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRTRestrictedErrorInfo? = null,
) : WinRTRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRTPathTooLongException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRTRestrictedErrorInfo? = null,
) : WinRTRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRTFileNotFoundException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRTRestrictedErrorInfo? = null,
) : WinRTRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRTDirectoryNotFoundException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRTRestrictedErrorInfo? = null,
) : WinRTRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRTEndOfFileException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRTRestrictedErrorInfo? = null,
) : WinRTRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRTBadImageFormatException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRTRestrictedErrorInfo? = null,
) : WinRTRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRTInvalidWindowHandleException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRTRestrictedErrorInfo? = null,
) : WinRTRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRTXamlParseException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRTRestrictedErrorInfo? = null,
) : WinRTRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRTLayoutCycleException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRTRestrictedErrorInfo? = null,
) : WinRTRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRTElementNotEnabledException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRTRestrictedErrorInfo? = null,
) : WinRTRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)

class WinRTElementNotAvailableException(
    message: String,
    hResult: HResult,
    restrictedErrorInfo: WinRTRestrictedErrorInfo? = null,
) : WinRTRuntimeException(message, hResult, restrictedErrorInfo = restrictedErrorInfo)
