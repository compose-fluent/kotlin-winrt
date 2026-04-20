package io.github.kitectlab.winrt.runtime

expect class NativePointer()

expect class NativeScope() : AutoCloseable {
    override fun close()
}

expect object NativeInterop

expect object WinRtPlatformApi
