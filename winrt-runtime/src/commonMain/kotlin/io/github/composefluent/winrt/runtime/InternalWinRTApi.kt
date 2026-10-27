package io.github.composefluent.winrt.runtime

/**
 * Implementation detail shared by the runtime, generated code and development tools.
 * Not a supported application API; signatures and behavior may change without notice.
 *
 * Mirrors CsWinRT's PrivateImplementationDetail diagnostics on public infrastructure.
 * Kotlin requires public visibility for generated code in other modules; opt-in makes
 * that exception explicit on both JVM and Native. This is not a security boundary.
 */
@RequiresOptIn(
    message = "Internal Kotlin WinRT infrastructure API. Not supported for application use.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.CONSTRUCTOR)
annotation class InternalWinRTApi
