package io.github.composefluent.winrt.projections.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asClassName
import io.github.composefluent.winrt.metadata.WinRTFundamentalType

internal fun WinRTFundamentalType.toProjectionAbiValueKind(): KotlinProjectionAbiValueKind =
    when (this) {
        WinRTFundamentalType.Boolean -> KotlinProjectionAbiValueKind.Boolean
        WinRTFundamentalType.Char -> KotlinProjectionAbiValueKind.Char16
        WinRTFundamentalType.Int8 -> KotlinProjectionAbiValueKind.Int8
        WinRTFundamentalType.UInt8 -> KotlinProjectionAbiValueKind.UInt8
        WinRTFundamentalType.Int16 -> KotlinProjectionAbiValueKind.Int16
        WinRTFundamentalType.UInt16 -> KotlinProjectionAbiValueKind.UInt16
        WinRTFundamentalType.Int32 -> KotlinProjectionAbiValueKind.Int32
        WinRTFundamentalType.UInt32 -> KotlinProjectionAbiValueKind.UInt32
        WinRTFundamentalType.Int64 -> KotlinProjectionAbiValueKind.Int64
        WinRTFundamentalType.UInt64 -> KotlinProjectionAbiValueKind.UInt64
        WinRTFundamentalType.Float -> KotlinProjectionAbiValueKind.Float
        WinRTFundamentalType.Double -> KotlinProjectionAbiValueKind.Double
        WinRTFundamentalType.String -> KotlinProjectionAbiValueKind.String
    }

internal fun WinRTFundamentalType.toProjectionTypeName(): TypeName =
    when (this) {
        WinRTFundamentalType.Boolean -> Boolean::class.asClassName()
        WinRTFundamentalType.Char -> Char::class.asClassName()
        WinRTFundamentalType.Int8 -> Byte::class.asClassName()
        WinRTFundamentalType.UInt8 -> KOTLIN_UBYTE_CLASS_NAME
        WinRTFundamentalType.Int16 -> Short::class.asClassName()
        WinRTFundamentalType.UInt16 -> KOTLIN_USHORT_CLASS_NAME
        WinRTFundamentalType.Int32 -> Int::class.asClassName()
        WinRTFundamentalType.UInt32 -> KOTLIN_UINT_CLASS_NAME
        WinRTFundamentalType.Int64 -> Long::class.asClassName()
        WinRTFundamentalType.UInt64 -> KOTLIN_ULONG_CLASS_NAME
        WinRTFundamentalType.Float -> Float::class.asClassName()
        WinRTFundamentalType.Double -> Double::class.asClassName()
        WinRTFundamentalType.String -> String::class.asClassName()
    }

internal fun WinRTFundamentalType.toAttributeParameterTypeName(): TypeName =
    when (this) {
        WinRTFundamentalType.Boolean -> Boolean::class.asClassName()
        WinRTFundamentalType.Char -> Char::class.asClassName()
        WinRTFundamentalType.String -> String::class.asClassName()
        WinRTFundamentalType.Float -> Float::class.asClassName()
        WinRTFundamentalType.Double -> Double::class.asClassName()
        WinRTFundamentalType.Int8,
        WinRTFundamentalType.UInt8,
        WinRTFundamentalType.Int16,
        WinRTFundamentalType.UInt16,
        WinRTFundamentalType.Int32,
        WinRTFundamentalType.UInt32,
        WinRTFundamentalType.Int64,
        WinRTFundamentalType.UInt64 -> Long::class.asClassName()
    }

internal fun WinRTFundamentalType.toAttributeParameterDefaultValue(): CodeBlock =
    when (this) {
        WinRTFundamentalType.Boolean -> CodeBlock.of("false")
        WinRTFundamentalType.Char -> CodeBlock.of("'\\u0000'")
        WinRTFundamentalType.String -> CodeBlock.of("%S", "")
        WinRTFundamentalType.Float -> CodeBlock.of("0.0f")
        WinRTFundamentalType.Double -> CodeBlock.of("0.0")
        WinRTFundamentalType.Int8,
        WinRTFundamentalType.UInt8,
        WinRTFundamentalType.Int16,
        WinRTFundamentalType.UInt16,
        WinRTFundamentalType.Int32,
        WinRTFundamentalType.UInt32,
        WinRTFundamentalType.Int64,
        WinRTFundamentalType.UInt64 -> CodeBlock.of("0L")
    }

internal fun WinRTFundamentalType.toNativeStructScalarKindName(): String? =
    when (this) {
        WinRTFundamentalType.Boolean,
        WinRTFundamentalType.Int8,
        WinRTFundamentalType.UInt8 -> "INT8"
        WinRTFundamentalType.Int16,
        WinRTFundamentalType.UInt16 -> "INT16"
        WinRTFundamentalType.Int32,
        WinRTFundamentalType.UInt32 -> "INT32"
        WinRTFundamentalType.Int64,
        WinRTFundamentalType.UInt64 -> "INT64"
        WinRTFundamentalType.Float -> "FLOAT32"
        WinRTFundamentalType.Double -> "DOUBLE"
        WinRTFundamentalType.Char -> "CHAR16"
        WinRTFundamentalType.String -> null
    }

internal fun WinRTFundamentalType.toNativeStructFieldReadCode(slice: CodeBlock): CodeBlock? =
    when (this) {
        WinRTFundamentalType.Boolean -> CodeBlock.of("%T.readInt8(%L).toInt() != 0", PLATFORM_ABI_CLASS_NAME, slice)
        WinRTFundamentalType.Int8 -> CodeBlock.of("%T.readInt8(%L)", PLATFORM_ABI_CLASS_NAME, slice)
        WinRTFundamentalType.UInt8 -> CodeBlock.of("%T.readInt8(%L).toUByte()", PLATFORM_ABI_CLASS_NAME, slice)
        WinRTFundamentalType.Int16 -> CodeBlock.of("%T.readInt16(%L)", PLATFORM_ABI_CLASS_NAME, slice)
        WinRTFundamentalType.UInt16 -> CodeBlock.of("%T.readInt16(%L).toUShort()", PLATFORM_ABI_CLASS_NAME, slice)
        WinRTFundamentalType.Int32 -> CodeBlock.of("%T.readInt32(%L)", PLATFORM_ABI_CLASS_NAME, slice)
        WinRTFundamentalType.UInt32 -> CodeBlock.of("%T.readInt32(%L).toUInt()", PLATFORM_ABI_CLASS_NAME, slice)
        WinRTFundamentalType.Int64 -> CodeBlock.of("%T.readInt64(%L)", PLATFORM_ABI_CLASS_NAME, slice)
        WinRTFundamentalType.UInt64 -> CodeBlock.of("%T.readInt64(%L).toULong()", PLATFORM_ABI_CLASS_NAME, slice)
        WinRTFundamentalType.Float -> CodeBlock.of("%T.readFloat(%L)", PLATFORM_ABI_CLASS_NAME, slice)
        WinRTFundamentalType.Double -> CodeBlock.of("%T.readDouble(%L)", PLATFORM_ABI_CLASS_NAME, slice)
        WinRTFundamentalType.Char -> CodeBlock.of("%T.readChar16(%L)", PLATFORM_ABI_CLASS_NAME, slice)
        WinRTFundamentalType.String -> null
    }

internal fun WinRTFundamentalType.toNativeStructFieldWriteCode(slice: CodeBlock, value: CodeBlock): CodeBlock? =
    when (this) {
        WinRTFundamentalType.Boolean -> CodeBlock.of("%T.writeInt8(%L, if (%L) 1 else 0)", PLATFORM_ABI_CLASS_NAME, slice, value)
        WinRTFundamentalType.Int8 -> CodeBlock.of("%T.writeInt8(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, value)
        WinRTFundamentalType.UInt8 -> CodeBlock.of("%T.writeInt8(%L, %L.toByte())", PLATFORM_ABI_CLASS_NAME, slice, value)
        WinRTFundamentalType.Int16 -> CodeBlock.of("%T.writeInt16(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, value)
        WinRTFundamentalType.UInt16 -> CodeBlock.of("%T.writeInt16(%L, %L.toShort())", PLATFORM_ABI_CLASS_NAME, slice, value)
        WinRTFundamentalType.Int32 -> CodeBlock.of("%T.writeInt32(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, value)
        WinRTFundamentalType.UInt32 -> CodeBlock.of("%T.writeInt32(%L, %L.toInt())", PLATFORM_ABI_CLASS_NAME, slice, value)
        WinRTFundamentalType.Int64 -> CodeBlock.of("%T.writeInt64(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, value)
        WinRTFundamentalType.UInt64 -> CodeBlock.of("%T.writeInt64(%L, %L.toLong())", PLATFORM_ABI_CLASS_NAME, slice, value)
        WinRTFundamentalType.Float -> CodeBlock.of("%T.writeFloat(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, value)
        WinRTFundamentalType.Double -> CodeBlock.of("%T.writeDouble(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, value)
        WinRTFundamentalType.Char -> CodeBlock.of("%T.writeChar16(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, value)
        WinRTFundamentalType.String -> null
    }
