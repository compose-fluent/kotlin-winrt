package io.github.kitectlab.winrt.runtime.exception

import io.github.kitectlab.winrt.runtime.HResult
import io.github.kitectlab.winrt.runtime.PlatformRuntime
import io.github.kitectlab.winrt.runtime.WinRtPlatformApi
import io.github.kitectlab.winrt.runtime.WinRtRestrictedErrorInfo
import io.github.kitectlab.winrt.runtime.WinRtRuntimeException
import io.github.kitectlab.winrt.runtime.asRawAddress

internal object ManagedExceptionInterop {
    private var retainedRestrictedErrorInfo: AutoCloseable? = null
    private var retainedRestrictedErrorInfoDetails: Pair<HResult, WinRtRestrictedErrorInfo>? = null

    fun setErrorInfo(error: Throwable) {
        if (!PlatformRuntime.isWindows) {
            return
        }
        setRestrictedErrorInfo(error)
        if (LanguageExceptionInterop.trySetLanguageExceptionInfo(error)) {
            return
        }
        ManagedErrorInfoComObject(error).detachReference().use { errorInfo ->
            HResult(WinRtPlatformApi.setErrorInfoRaw(errorInfo.pointer.asRawAddress())).requireSuccess("SetErrorInfo")
        }
    }

    private fun setRestrictedErrorInfo(error: Throwable) {
        val runtimeException = error as? WinRtRuntimeException ?: return
        val hResult = runtimeException.hResult ?: return
        val restrictedErrorInfo = runtimeException.restrictedErrorInfo ?: return
        retainedRestrictedErrorInfoDetails = hResult to restrictedErrorInfo
        val errorInfo = ManagedRestrictedErrorInfoComObject(hResult, restrictedErrorInfo).detachReference()
        val result = WinRtPlatformApi.setRestrictedErrorInfoRaw(errorInfo.pointer.asRawAddress())
        if (result == null) {
            errorInfo.close()
            return
        }
        HResult(result).requireSuccess("SetRestrictedErrorInfo")
        retainedRestrictedErrorInfo?.close()
        retainedRestrictedErrorInfo = errorInfo
    }

    fun retainedRestrictedErrorInfo(expectedHResult: HResult): WinRtRestrictedErrorInfo? {
        val (hResult, errorInfo) = retainedRestrictedErrorInfoDetails ?: return null
        return errorInfo.takeIf { hResult == expectedHResult }
    }
}
