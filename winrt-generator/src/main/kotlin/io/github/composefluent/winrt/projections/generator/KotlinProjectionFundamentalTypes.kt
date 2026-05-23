package io.github.composefluent.winrt.projections.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asClassName
import io.github.composefluent.winrt.metadata.WinRtFundamentalType

internal fun WinRtFundamentalType.toProjectionAbiValueKind(): KotlinProjectionAbiValueKind =
    when (this) {
        WinRtFundamentalType.Boolean -> KotlinProjectionAbiValueKind.Boolean
        WinRtFundamentalType.Char -> KotlinProjectionAbiValueKind.Char16
        WinRtFundamentalType.Int8 -> KotlinProjectionAbiValueKind.Int8
        WinRtFundamentalType.UInt8 -> KotlinProjectionAbiValueKind.UInt8
        WinRtFundamentalType.Int16 -> KotlinProjectionAbiValueKind.Int16
        WinRtFundamentalType.UInt16 -> KotlinProjectionAbiValueKind.UInt16
        WinRtFundamentalType.Int32 -> KotlinProjectionAbiValueKind.Int32
        WinRtFundamentalType.UInt32 -> KotlinProjectionAbiValueKind.UInt32
        WinRtFundamentalType.Int64 -> KotlinProjectionAbiValueKind.Int64
        WinRtFundamentalType.UInt64 -> KotlinProjectionAbiValueKind.UInt64
        WinRtFundamentalType.Float -> KotlinProjectionAbiValueKind.Float
        WinRtFundamentalType.Double -> KotlinProjectionAbiValueKind.Double
        WinRtFundamentalType.String -> KotlinProjectionAbiValueKind.String
    }

internal fun WinRtFundamentalType.toProjectionTypeName(): TypeName =
    when (this) {
        WinRtFundamentalType.Boolean -> Boolean::class.asClassName()
        WinRtFundamentalType.Char -> Char::class.asClassName()
        WinRtFundamentalType.Int8 -> Byte::class.asClassName()
        WinRtFundamentalType.UInt8 -> KOTLIN_UBYTE_CLASS_NAME
        WinRtFundamentalType.Int16 -> Short::class.asClassName()
        WinRtFundamentalType.UInt16 -> KOTLIN_USHORT_CLASS_NAME
        WinRtFundamentalType.Int32 -> Int::class.asClassName()
        WinRtFundamentalType.UInt32 -> KOTLIN_UINT_CLASS_NAME
        WinRtFundamentalType.Int64 -> Long::class.asClassName()
        WinRtFundamentalType.UInt64 -> KOTLIN_ULONG_CLASS_NAME
        WinRtFundamentalType.Float -> Float::class.asClassName()
        WinRtFundamentalType.Double -> Double::class.asClassName()
        WinRtFundamentalType.String -> String::class.asClassName()
    }

internal fun WinRtFundamentalType.toAttributeParameterTypeName(): TypeName =
    when (this) {
        WinRtFundamentalType.Boolean -> Boolean::class.asClassName()
        WinRtFundamentalType.Char -> Char::class.asClassName()
        WinRtFundamentalType.String -> String::class.asClassName()
        WinRtFundamentalType.Float -> Float::class.asClassName()
        WinRtFundamentalType.Double -> Double::class.asClassName()
        WinRtFundamentalType.Int8,
        WinRtFundamentalType.UInt8,
        WinRtFundamentalType.Int16,
        WinRtFundamentalType.UInt16,
        WinRtFundamentalType.Int32,
        WinRtFundamentalType.UInt32,
        WinRtFundamentalType.Int64,
        WinRtFundamentalType.UInt64 -> Long::class.asClassName()
    }

internal fun WinRtFundamentalType.toAttributeParameterDefaultValue(): CodeBlock =
    when (this) {
        WinRtFundamentalType.Boolean -> CodeBlock.of("false")
        WinRtFundamentalType.Char -> CodeBlock.of("'\\u0000'")
        WinRtFundamentalType.String -> CodeBlock.of("%S", "")
        WinRtFundamentalType.Float -> CodeBlock.of("0.0f")
        WinRtFundamentalType.Double -> CodeBlock.of("0.0")
        WinRtFundamentalType.Int8,
        WinRtFundamentalType.UInt8,
        WinRtFundamentalType.Int16,
        WinRtFundamentalType.UInt16,
        WinRtFundamentalType.Int32,
        WinRtFundamentalType.UInt32,
        WinRtFundamentalType.Int64,
        WinRtFundamentalType.UInt64 -> CodeBlock.of("0L")
    }

internal fun WinRtFundamentalType.toNativeStructScalarKindName(): String? =
    when (this) {
        WinRtFundamentalType.Boolean,
        WinRtFundamentalType.Int8,
        WinRtFundamentalType.UInt8 -> "INT8"
        WinRtFundamentalType.Int16,
        WinRtFundamentalType.UInt16 -> "INT16"
        WinRtFundamentalType.Int32,
        WinRtFundamentalType.UInt32 -> "INT32"
        WinRtFundamentalType.Int64,
        WinRtFundamentalType.UInt64 -> "INT64"
        WinRtFundamentalType.Float -> "FLOAT32"
        WinRtFundamentalType.Double -> "DOUBLE"
        WinRtFundamentalType.Char -> "CHAR16"
        WinRtFundamentalType.String -> null
    }

internal fun WinRtFundamentalType.toNativeStructFieldReadCode(slice: CodeBlock): CodeBlock? =
    when (this) {
        WinRtFundamentalType.Boolean -> CodeBlock.of("%T.readInt8(%L).toInt() != 0", PLATFORM_ABI_CLASS_NAME, slice)
        WinRtFundamentalType.Int8 -> CodeBlock.of("%T.readInt8(%L)", PLATFORM_ABI_CLASS_NAME, slice)
        WinRtFundamentalType.UInt8 -> CodeBlock.of("%T.readInt8(%L).toUByte()", PLATFORM_ABI_CLASS_NAME, slice)
        WinRtFundamentalType.Int16 -> CodeBlock.of("%T.readInt16(%L)", PLATFORM_ABI_CLASS_NAME, slice)
        WinRtFundamentalType.UInt16 -> CodeBlock.of("%T.readInt16(%L).toUShort()", PLATFORM_ABI_CLASS_NAME, slice)
        WinRtFundamentalType.Int32 -> CodeBlock.of("%T.readInt32(%L)", PLATFORM_ABI_CLASS_NAME, slice)
        WinRtFundamentalType.UInt32 -> CodeBlock.of("%T.readInt32(%L).toUInt()", PLATFORM_ABI_CLASS_NAME, slice)
        WinRtFundamentalType.Int64 -> CodeBlock.of("%T.readInt64(%L)", PLATFORM_ABI_CLASS_NAME, slice)
        WinRtFundamentalType.UInt64 -> CodeBlock.of("%T.readInt64(%L).toULong()", PLATFORM_ABI_CLASS_NAME, slice)
        WinRtFundamentalType.Float -> CodeBlock.of("%T.readFloat(%L)", PLATFORM_ABI_CLASS_NAME, slice)
        WinRtFundamentalType.Double -> CodeBlock.of("%T.readDouble(%L)", PLATFORM_ABI_CLASS_NAME, slice)
        WinRtFundamentalType.Char -> CodeBlock.of("%T.readChar16(%L)", PLATFORM_ABI_CLASS_NAME, slice)
        WinRtFundamentalType.String -> null
    }

internal fun WinRtFundamentalType.toNativeStructFieldWriteCode(slice: CodeBlock, value: CodeBlock): CodeBlock? =
    when (this) {
        WinRtFundamentalType.Boolean -> CodeBlock.of("%T.writeInt8(%L, if (%L) 1 else 0)", PLATFORM_ABI_CLASS_NAME, slice, value)
        WinRtFundamentalType.Int8 -> CodeBlock.of("%T.writeInt8(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, value)
        WinRtFundamentalType.UInt8 -> CodeBlock.of("%T.writeInt8(%L, %L.toByte())", PLATFORM_ABI_CLASS_NAME, slice, value)
        WinRtFundamentalType.Int16 -> CodeBlock.of("%T.writeInt16(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, value)
        WinRtFundamentalType.UInt16 -> CodeBlock.of("%T.writeInt16(%L, %L.toShort())", PLATFORM_ABI_CLASS_NAME, slice, value)
        WinRtFundamentalType.Int32 -> CodeBlock.of("%T.writeInt32(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, value)
        WinRtFundamentalType.UInt32 -> CodeBlock.of("%T.writeInt32(%L, %L.toInt())", PLATFORM_ABI_CLASS_NAME, slice, value)
        WinRtFundamentalType.Int64 -> CodeBlock.of("%T.writeInt64(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, value)
        WinRtFundamentalType.UInt64 -> CodeBlock.of("%T.writeInt64(%L, %L.toLong())", PLATFORM_ABI_CLASS_NAME, slice, value)
        WinRtFundamentalType.Float -> CodeBlock.of("%T.writeFloat(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, value)
        WinRtFundamentalType.Double -> CodeBlock.of("%T.writeDouble(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, value)
        WinRtFundamentalType.Char -> CodeBlock.of("%T.writeChar16(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, value)
        WinRtFundamentalType.String -> null
    }
