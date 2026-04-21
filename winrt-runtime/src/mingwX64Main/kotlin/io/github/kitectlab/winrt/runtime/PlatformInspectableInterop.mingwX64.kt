package io.github.kitectlab.winrt.runtime

internal actual fun platformCreateInspectableReference(value: Any): ComObjectReference =
    throw NotImplementedError("Managed inspectable CCW creation is not implemented for mingwX64 yet.")

internal actual fun platformTryProjectBindableInspectable(pointer: NativePointer): Any? = null
