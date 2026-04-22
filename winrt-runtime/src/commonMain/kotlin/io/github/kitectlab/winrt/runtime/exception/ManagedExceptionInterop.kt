package io.github.kitectlab.winrt.runtime.exception

import io.github.kitectlab.winrt.runtime.HResult
import io.github.kitectlab.winrt.runtime.PlatformRuntime
import io.github.kitectlab.winrt.runtime.WinRtPlatformApi
import io.github.kitectlab.winrt.runtime.WinRtRuntimeException

internal object ManagedExceptionInterop {
    fun setErrorInfo(error: Throwable) {
        if (!PlatformRuntime.isWindows) {
            return
        }
        if (LanguageExceptionInterop.trySetLanguageExceptionInfo(error)) {
            return
        }
        setRestrictedErrorInfo(error)
        ManagedErrorInfoComObject(error).detachReference().use { errorInfo ->
            HResult(WinRtPlatformApi.setErrorInfoRaw(errorInfo.pointer)).requireSuccess("SetErrorInfo")
        }
    }

    private fun setRestrictedErrorInfo(error: Throwable) {
        val runtimeException = error as? WinRtRuntimeException ?: return
        val hResult = runtimeException.hResult ?: return
        val restrictedErrorInfo = runtimeException.restrictedErrorInfo ?: return
        ManagedRestrictedErrorInfoComObject(hResult, restrictedErrorInfo).detachReference().use { errorInfo ->
            val result = WinRtPlatformApi.setRestrictedErrorInfoRaw(errorInfo.pointer) ?: return
            HResult(result).requireSuccess("SetRestrictedErrorInfo")
        }
    }
}
