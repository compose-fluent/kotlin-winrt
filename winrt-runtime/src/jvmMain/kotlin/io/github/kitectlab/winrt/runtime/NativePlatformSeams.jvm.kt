package io.github.kitectlab.winrt.runtime

actual class NativePointer actual constructor()

actual class NativeScope actual constructor() : AutoCloseable {
    actual override fun close() {}
}

actual object NativeInterop
