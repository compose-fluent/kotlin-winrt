package io.github.kitectlab.winrt.runtime

internal expect fun platformHResultFromThrowable(error: Throwable): HResult

internal expect fun platformSetErrorInfo(error: Throwable)
