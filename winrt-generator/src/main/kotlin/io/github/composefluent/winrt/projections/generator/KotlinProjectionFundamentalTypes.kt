package io.github.composefluent.winrt.projections.generator

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
