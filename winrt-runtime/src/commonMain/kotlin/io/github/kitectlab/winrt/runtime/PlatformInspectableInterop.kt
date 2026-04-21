package io.github.kitectlab.winrt.runtime

internal expect fun platformCreateInspectableReference(value: Any): ComObjectReference

internal expect fun platformTryProjectBindableInspectable(pointer: NativePointer): Any?
