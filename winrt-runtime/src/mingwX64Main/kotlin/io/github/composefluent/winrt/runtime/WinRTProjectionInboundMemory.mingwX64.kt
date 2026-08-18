@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.native.internal.InternalForKotlinNative::class,
)
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.github.composefluent.winrt.runtime

import kotlinx.cinterop.COpaquePointer
import kotlin.native.internal.IntrinsicType
import kotlin.native.internal.TypedIntrinsic

@TypedIntrinsic(IntrinsicType.INTEROP_WRITE_BITS)
@PublishedApi
internal external fun writeInboundResultBits(
    resultAddress: COpaquePointer,
    offset: Long,
    size: Int,
    value: Long,
)

@PublishedApi
internal inline fun winRTProjectionInboundWriteAddress(
    resultAddress: COpaquePointer,
    value: RawAddress,
) {
    writeInboundResultBits(resultAddress, 0L, Long.SIZE_BITS, value.value)
}

@PublishedApi
internal inline fun winRTProjectionInboundWriteInt8(
    resultAddress: COpaquePointer,
    value: Byte,
) {
    writeInboundResultBits(resultAddress, 0L, Byte.SIZE_BITS, value.toLong())
}

@PublishedApi
internal inline fun winRTProjectionInboundWriteInt16(
    resultAddress: COpaquePointer,
    value: Short,
) {
    writeInboundResultBits(resultAddress, 0L, Short.SIZE_BITS, value.toLong())
}

@PublishedApi
internal inline fun winRTProjectionInboundWriteInt32(
    resultAddress: COpaquePointer,
    value: Int,
) {
    writeInboundResultBits(resultAddress, 0L, Int.SIZE_BITS, value.toLong())
}

@PublishedApi
internal inline fun winRTProjectionInboundWriteInt64(
    resultAddress: COpaquePointer,
    value: Long,
) {
    writeInboundResultBits(resultAddress, 0L, Long.SIZE_BITS, value)
}

@PublishedApi
internal inline fun winRTProjectionInboundWriteFloat32(
    resultAddress: COpaquePointer,
    value: Float,
) {
    writeInboundResultBits(resultAddress, 0L, Float.SIZE_BITS, value.toRawBits().toLong())
}

@PublishedApi
internal inline fun winRTProjectionInboundWriteFloat64(
    resultAddress: COpaquePointer,
    value: Double,
) {
    writeInboundResultBits(resultAddress, 0L, Double.SIZE_BITS, value.toRawBits())
}
