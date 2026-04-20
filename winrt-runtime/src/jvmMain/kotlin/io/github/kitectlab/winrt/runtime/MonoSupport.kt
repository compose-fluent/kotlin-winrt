package io.github.kitectlab.winrt.runtime

/**
 * Narrow JVM adaptation point for `.cswinrt/src/WinRT.Runtime/MonoSupport.cs`.
 *
 * CsWinRT needs explicit Mono foreign-thread attach/detach hooks because it runs managed .NET code inside
 * native hosts that can re-enter through Mono. The Kotlin/JVM runtime does not embed Mono or the CLR, so
 * there is no equivalent thread-domain handoff to perform here. Keep the no-op owner explicit so later
 * runtime slices do not invent ad hoc checks for this environment-specific concern.
 */
internal object MonoSupport {
    class ThreadContext : AutoCloseable {
        override fun close() = Unit
    }
}
