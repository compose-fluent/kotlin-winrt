package io.github.kitectlab.winrt.runtime

/**
 * Narrow no-op adaptation point for `.cswinrt/src/WinRT.Runtime/MonoSupport.cs`.
 *
 * CsWinRT needs explicit Mono foreign-thread attach/detach hooks because it runs managed .NET code
 * inside native hosts that can re-enter through Mono. Kotlin runtime targets here do not embed Mono,
 * so there is no equivalent thread-domain handoff to perform. Keep the explicit owner in shared code
 * so later runtime slices do not reintroduce platform-specific checks for this concern.
 */
internal object MonoSupport {
    class ThreadContext : AutoCloseable {
        override fun close() = Unit
    }
}
