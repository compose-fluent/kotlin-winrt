package io.github.kitectlab.winrt.runtime

internal object ComAbiWord {
    fun pointer(value: RawAddress): Long = value.value

    fun comPointer(value: RawComPtr): Long = value.value

    fun int8(value: Byte): Long = value.toLong()

    fun int32(value: Int): Long = value.toLong()

    fun uint32(value: UInt): Long = value.toLong() and 0xFFFF_FFFFL

    fun int64(value: Long): Long = value

    fun double(value: Double): Long = value.toRawBits()

    fun fromDynamic(
        kind: ComAbiValueKind,
        value: Any?,
    ): Long =
        when (kind) {
            ComAbiValueKind.Pointer ->
                when (value) {
                    null -> 0L
                    is RawAddress -> pointer(value)
                    is RawComPtr -> comPointer(value)
                    else -> error("Expected pointer-compatible ABI value, got '${value::class.qualifiedName}'.")
                }

            ComAbiValueKind.Int8 ->
                when (value) {
                    is Byte -> int8(value)
                    is Int -> int8(value.toByte())
                    else -> error("Expected int8-compatible ABI value, got '${value?.let { it::class.qualifiedName } ?: "null"}'.")
                }

            ComAbiValueKind.Int32 ->
                when (value) {
                    is Int -> int32(value)
                    is UInt -> uint32(value)
                    else -> error("Expected int32-compatible ABI value, got '${value?.let { it::class.qualifiedName } ?: "null"}'.")
                }

            ComAbiValueKind.Int64 ->
                when (value) {
                    is Long -> int64(value)
                    is ULong -> int64(value.toLong())
                    is Int -> int64(value.toLong())
                    else -> error("Expected int64-compatible ABI value, got '${value?.let { it::class.qualifiedName } ?: "null"}'.")
                }

            ComAbiValueKind.Double ->
                when (value) {
                    is Double -> double(value)
                    is Float -> double(value.toDouble())
                    else -> error("Expected double-compatible ABI value, got '${value?.let { it::class.qualifiedName } ?: "null"}'.")
                }
        }

    fun fromDynamicArgs(
        kinds: List<ComAbiValueKind>,
        values: List<Any?>,
    ): LongArray {
        require(kinds.size == values.size) {
            "ABI argument count ${values.size} must match COM signature arity ${kinds.size}."
        }
        return LongArray(values.size) { index ->
            fromDynamic(kinds[index], values[index])
        }
    }
}
