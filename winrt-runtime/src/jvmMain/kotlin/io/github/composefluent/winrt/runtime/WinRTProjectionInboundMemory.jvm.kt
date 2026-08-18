package io.github.composefluent.winrt.runtime

@PublishedApi
internal inline fun winRTProjectionInboundWriteAddress(
    resultAddress: Long,
    value: RawAddress,
) {
    PlatformAbi.writePointer(RawAddress(resultAddress), value)
}

@PublishedApi
internal inline fun winRTProjectionInboundWriteInt8(
    resultAddress: Long,
    value: Byte,
) {
    PlatformAbi.writeInt8(RawAddress(resultAddress), value)
}

@PublishedApi
internal inline fun winRTProjectionInboundWriteInt16(
    resultAddress: Long,
    value: Short,
) {
    PlatformAbi.writeInt16(RawAddress(resultAddress), value)
}

@PublishedApi
internal inline fun winRTProjectionInboundWriteInt32(
    resultAddress: Long,
    value: Int,
) {
    PlatformAbi.writeInt32(RawAddress(resultAddress), value)
}

@PublishedApi
internal inline fun winRTProjectionInboundWriteInt64(
    resultAddress: Long,
    value: Long,
) {
    PlatformAbi.writeInt64(RawAddress(resultAddress), value)
}

@PublishedApi
internal inline fun winRTProjectionInboundWriteFloat32(
    resultAddress: Long,
    value: Float,
) {
    PlatformAbi.writeFloat(RawAddress(resultAddress), value)
}

@PublishedApi
internal inline fun winRTProjectionInboundWriteFloat64(
    resultAddress: Long,
    value: Double,
) {
    PlatformAbi.writeDouble(RawAddress(resultAddress), value)
}
