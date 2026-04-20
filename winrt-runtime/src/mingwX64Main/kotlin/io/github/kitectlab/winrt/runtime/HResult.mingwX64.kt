package io.github.kitectlab.winrt.runtime

internal actual fun throwHResultFailure(hResult: HResult, operation: String): Nothing {
    throw IllegalStateException("$operation failed with $hResult")
}
