package io.github.composefluent.winrt.runtime.exception

/**
 * Narrow Kotlin owner for `.cswinrt`'s `ILanguageExceptionErrorInfo*` path.
 *
 * The CLR-specific language-exception CCW and propagation-context chain cannot
 * be mirrored directly on Kotlin/JVM, so this owner stays explicit and local to
 * the exception package instead of leaking pseudo-parity branches into the
 * broader runtime.
 */
internal object LanguageExceptionInterop {
    fun trySetLanguageExceptionInfo(@Suppress("UNUSED_PARAMETER") error: Throwable): Boolean = false
}
