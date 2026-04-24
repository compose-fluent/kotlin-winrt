@file:OptIn(ExperimentalUnsignedTypes::class)

package io.github.kitectlab.winrt.runtime

private const val DISP_E_OVERFLOW: Int = 0x8002000A.toInt()

internal fun unboxInspectablePointer(pointer: RawAddress): Any {
    WinRtInspectableComObject.findManagedValue(pointer)?.let { return it }
    tryProjectBorrowedInspectableValue(pointer)?.let { return it }
    return ComWrappersSupport.createRcwForComObject(pointer)
        ?: WinRtInvalidCastException("Unable to project inspectable value.", HResult(TYPE_E_TYPEMISMATCH))
}

internal fun coerceString(value: Any): String =
    when (value) {
        is String -> value
        is Guid -> value.toString()
        else -> throw WinRtInvalidCastException("Cannot coerce ${value::class.typeDisplayName()} to String.", HResult(TYPE_E_TYPEMISMATCH))
    }

internal fun coerceGuid(value: Any): Guid =
    when (value) {
        is Guid -> value
        is String -> runCatching { Guid(value) }.getOrElse {
            throw WinRtInvalidCastException("Cannot parse Guid from '$value'.", HResult(TYPE_E_TYPEMISMATCH))
        }
        else -> throw WinRtInvalidCastException("Cannot coerce ${value::class.typeDisplayName()} to Guid.", HResult(TYPE_E_TYPEMISMATCH))
    }

internal fun coerceBoolean(value: Any): Boolean =
    when (value) {
        is Boolean -> value
        else -> throw WinRtInvalidCastException("Cannot coerce ${value::class.typeDisplayName()} to Boolean.", HResult(TYPE_E_TYPEMISMATCH))
    }

internal fun coerceChar(value: Any): Char =
    when (value) {
        is Char -> value
        else -> throw WinRtInvalidCastException("Cannot coerce ${value::class.typeDisplayName()} to Char.", HResult(TYPE_E_TYPEMISMATCH))
    }

internal fun coerceUByte(value: Any): UByte = numericCoerce("UByte", value) { (it as Number).toLong().toUByte() }

internal fun coerceShort(value: Any): Short = numericCoerce("Short", value) { (it as Number).toLong().toShort() }

internal fun coerceUShort(value: Any): UShort = numericCoerce("UShort", value) { (it as Number).toLong().toUShort() }

internal fun coerceInt(value: Any): Int = numericCoerce("Int", value) { (it as Number).toLong().toInt() }

internal fun coerceUInt(value: Any): UInt = numericCoerce("UInt", value) { (it as Number).toLong().toUInt() }

internal fun coerceLong(value: Any): Long = numericCoerce("Long", value) { (it as Number).toLong() }

internal fun coerceULong(value: Any): ULong = numericCoerce("ULong", value) { (it as Number).toLong().toULong() }

internal fun coerceFloat(value: Any): Float = numericCoerce("Float", value) { (it as Number).toDouble().toFloat() }

internal fun coerceDouble(value: Any): Double = numericCoerce("Double", value) { (it as Number).toDouble() }

private fun <T> numericCoerce(
    label: String,
    value: Any,
    convert: (Any) -> T,
): T {
    val coercible =
        value is Byte ||
            value is UByte ||
            value is Short ||
            value is UShort ||
            value is Int ||
            value is UInt ||
            value is Long ||
            value is ULong ||
            value is Float ||
            value is Double
    if (!coercible) {
        throw WinRtInvalidCastException("Cannot coerce ${value::class.typeDisplayName()} to $label.", HResult(TYPE_E_TYPEMISMATCH))
    }
    return try {
        convert(value)
    } catch (_: Throwable) {
        throw WinRtInvalidCastException("Numeric coercion overflow for $label.", HResult(DISP_E_OVERFLOW))
    }
}
