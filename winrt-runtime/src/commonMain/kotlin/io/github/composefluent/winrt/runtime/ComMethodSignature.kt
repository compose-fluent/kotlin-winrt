package io.github.composefluent.winrt.runtime

sealed class ComAbiValueKind {
    data object Pointer : ComAbiValueKind()
    data object Int8 : ComAbiValueKind()
    data object Int16 : ComAbiValueKind()
    data object Int32 : ComAbiValueKind()
    data object Int64 : ComAbiValueKind()
    data object Float : ComAbiValueKind()
    data object Double : ComAbiValueKind()
    data class Struct(val layout: NativeAbiLayout) : ComAbiValueKind()
}

data class ComMethodSignature(
    val explicitParameterKinds: List<ComAbiValueKind> = emptyList(),
    val resultKind: ComAbiValueKind = ComAbiValueKind.Int32,
) {
    companion object {
        fun of(
            vararg explicitParameterKinds: ComAbiValueKind,
        ): ComMethodSignature = ComMethodSignature(explicitParameterKinds.toList())
    }
}

internal fun genericComAbiArgumentKind(value: Any): ComAbiValueKind =
    when (value) {
        is RawAddress,
        is RawComPtr,
        -> ComAbiValueKind.Pointer

        is Byte,
        is UByte,
        -> ComAbiValueKind.Int8

        is Short,
        is UShort,
        is Char,
        -> ComAbiValueKind.Int16

        is Int,
        is UInt,
        -> ComAbiValueKind.Int32

        is Long,
        is ULong,
        -> ComAbiValueKind.Int64

        is Float -> ComAbiValueKind.Float
        is Double -> ComAbiValueKind.Double
        else -> error("Unsupported generic COM ABI argument type: ${value::class.simpleName}.")
    }

internal fun genericComAbiArgumentWord(value: Any): Long =
    when (value) {
        is RawAddress -> value.value
        is RawComPtr -> value.value
        is Byte -> value.toLong()
        is UByte -> value.toLong()
        is Short -> value.toLong()
        is UShort -> value.toLong()
        is Int -> value.toLong()
        is UInt -> value.toLong()
        is Char -> value.code.toLong()
        is Long -> value
        is ULong -> value.toLong()
        is Float -> value.toBits().toLong()
        is Double -> value.toBits()
        else -> error("Unsupported generic COM ABI argument type: ${value::class.simpleName}.")
    }
