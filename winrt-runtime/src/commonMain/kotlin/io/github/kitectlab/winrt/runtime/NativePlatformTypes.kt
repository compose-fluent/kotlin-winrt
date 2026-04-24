package io.github.kitectlab.winrt.runtime

import kotlin.jvm.JvmInline

@JvmInline
value class RawAddress(
    val value: Long,
) {
    companion object {
        val Null: RawAddress = RawAddress(0L)
    }
}

@JvmInline
value class RawComPtr(
    val value: Long,
) {
    companion object {
        val Null: RawComPtr = RawComPtr(0L)
    }
}

enum class ApartmentType {
    SingleThreaded,
    MultiThreaded,
}

data class NativePointerResult(
    val hResultValue: Int,
    val pointer: RawAddress,
) {
    val isSuccess: Boolean
        get() = hResultValue >= 0 && !PlatformAbi.isNull(pointer)
}
