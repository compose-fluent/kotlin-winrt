package io.github.kitectlab.winrt.runtime

enum class ApartmentType {
    SingleThreaded,
    MultiThreaded,
}

data class NativePointerResult(
    val hResultValue: Int,
    val pointer: NativePointer,
) {
    val isSuccess: Boolean
        get() = hResultValue >= 0 && !NativeInterop.isNull(pointer)
}
