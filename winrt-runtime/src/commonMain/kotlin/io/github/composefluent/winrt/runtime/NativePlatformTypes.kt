package io.github.composefluent.winrt.runtime

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

internal val ApartmentType.coInitializeFlags: Int
    get() = when (this) {
        ApartmentType.SingleThreaded -> 0x2
        ApartmentType.MultiThreaded -> 0x0
    }

internal val ApartmentType.roInitializeType: Int
    get() = when (this) {
        ApartmentType.SingleThreaded -> 0
        ApartmentType.MultiThreaded -> 1
    }

data class NativePointerResult(
    val hResultValue: Int,
    val pointer: RawAddress,
) {
    val isSuccess: Boolean
        get() = hResultValue >= 0 && !PlatformAbi.isNull(pointer)
}
