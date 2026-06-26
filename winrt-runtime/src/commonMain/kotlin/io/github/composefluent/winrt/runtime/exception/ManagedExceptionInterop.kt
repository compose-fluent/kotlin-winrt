package io.github.composefluent.winrt.runtime.exception

import io.github.composefluent.winrt.runtime.HResult
import io.github.composefluent.winrt.runtime.PlatformRuntime
import io.github.composefluent.winrt.runtime.WinRTPlatformApi
import io.github.composefluent.winrt.runtime.WinRTRestrictedErrorInfo
import io.github.composefluent.winrt.runtime.WinRTRuntimeException
import io.github.composefluent.winrt.runtime.asRawAddress

internal object ManagedExceptionInterop {
    private var retainedRestrictedErrorInfo: AutoCloseable? = null
    private var retainedRestrictedErrorInfoDetails: Pair<HResult, WinRTRestrictedErrorInfo>? = null

    fun setErrorInfo(error: Throwable) {
        if (!PlatformRuntime.isWindows) {
            return
        }
        setRestrictedErrorInfo(error)
        if (LanguageExceptionInterop.trySetLanguageExceptionInfo(error)) {
            return
        }
        ManagedErrorInfoComObject(error).detachReference().use { errorInfo ->
            HResult(WinRTPlatformApi.setErrorInfoRaw(errorInfo.pointer.asRawAddress())).requireSuccess("SetErrorInfo")
        }
    }

    private fun setRestrictedErrorInfo(error: Throwable) {
        val runtimeException = error as? WinRTRuntimeException ?: return
        val hResult = runtimeException.hResult ?: return
        val restrictedErrorInfo = runtimeException.restrictedErrorInfo ?: return
        retainedRestrictedErrorInfoDetails = hResult to restrictedErrorInfo
        val errorInfo = ManagedRestrictedErrorInfoComObject(hResult, restrictedErrorInfo).detachReference()
        val result = WinRTPlatformApi.setRestrictedErrorInfoRaw(errorInfo.pointer.asRawAddress())
        if (result == null) {
            errorInfo.close()
            return
        }
        HResult(result).requireSuccess("SetRestrictedErrorInfo")
        retainedRestrictedErrorInfo?.close()
        retainedRestrictedErrorInfo = errorInfo
    }

    fun retainedRestrictedErrorInfo(expectedHResult: HResult): WinRTRestrictedErrorInfo? {
        val (hResult, errorInfo) = retainedRestrictedErrorInfoDetails ?: return null
        return errorInfo.takeIf { hResult == expectedHResult }
    }
}
