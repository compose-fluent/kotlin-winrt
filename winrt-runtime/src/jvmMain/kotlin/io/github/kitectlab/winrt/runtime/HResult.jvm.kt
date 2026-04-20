package io.github.kitectlab.winrt.runtime

internal actual fun throwHResultFailure(hResult: HResult, operation: String): Nothing {
    throw WinRtExceptionTranslator.exceptionFor(hResult, operation)
}
