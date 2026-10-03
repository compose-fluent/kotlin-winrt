package io.github.composefluent.winrt.runtime

internal expect object PlatformFinalization {
    fun drain()

    /** Collect managed objects without draining apartment-bound COM releases. */
    fun collectForReferenceTracking()
}
