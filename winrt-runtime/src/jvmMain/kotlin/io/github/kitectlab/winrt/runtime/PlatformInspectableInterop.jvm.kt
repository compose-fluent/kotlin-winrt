package io.github.kitectlab.winrt.runtime

internal actual fun platformCreateInspectableReference(value: Any): ComObjectReference =
    ComWrappersSupport.createCCWForObject(value, IID.IInspectable)

internal actual fun platformTryProjectBindableInspectable(pointer: NativePointer): Any? =
    WinRtValueBoxing.tryProjectBorrowedInspectable(pointer)
