package io.github.composefluent.winrt.projections.generator

import com.squareup.kotlinpoet.CodeBlock
import io.github.composefluent.winrt.metadata.WinRtIntegralType
import org.junit.Assert.assertEquals
import org.junit.Test

class KotlinProjectionIntegralAbiDescriptorTest {
    @Test
    fun integral_abi_descriptor_owns_width_and_com_carrier_kind() {
        val expected = mapOf(
            WinRtIntegralType.Int8 to ExpectedIntegralAbi(1, KotlinProjectionComArgumentKind.Int8, KotlinProjectionAbiValueKind.Int8, "rawArgs[0] as kotlin.Byte"),
            WinRtIntegralType.UInt8 to ExpectedIntegralAbi(1, KotlinProjectionComArgumentKind.Int8, KotlinProjectionAbiValueKind.UInt8, "(rawArgs[0] as kotlin.Byte).toUByte()"),
            WinRtIntegralType.Int16 to ExpectedIntegralAbi(2, KotlinProjectionComArgumentKind.Int16, KotlinProjectionAbiValueKind.Int16, "rawArgs[0] as kotlin.Short"),
            WinRtIntegralType.UInt16 to ExpectedIntegralAbi(2, KotlinProjectionComArgumentKind.Int16, KotlinProjectionAbiValueKind.UInt16, "(rawArgs[0] as kotlin.Short).toUShort()"),
            WinRtIntegralType.Int32 to ExpectedIntegralAbi(4, KotlinProjectionComArgumentKind.Int32, KotlinProjectionAbiValueKind.Int32, "rawArgs[0] as kotlin.Int"),
            WinRtIntegralType.UInt32 to ExpectedIntegralAbi(4, KotlinProjectionComArgumentKind.Int32, KotlinProjectionAbiValueKind.UInt32, "(rawArgs[0] as kotlin.Int).toUInt()"),
            WinRtIntegralType.Int64 to ExpectedIntegralAbi(8, KotlinProjectionComArgumentKind.Int64, KotlinProjectionAbiValueKind.Int64, "rawArgs[0] as kotlin.Long"),
            WinRtIntegralType.UInt64 to ExpectedIntegralAbi(8, KotlinProjectionComArgumentKind.Int64, KotlinProjectionAbiValueKind.UInt64, "(rawArgs[0] as kotlin.Long).toULong()"),
        )

        expected.forEach { (type, expectedAbi) ->
            val descriptor = integralAbiDescriptor(type)
            assertEquals("ABI width for $type", expectedAbi.sizeBytes, descriptor.abiSizeBytes)
            assertEquals("COM argument kind for $type", expectedAbi.argumentKind, descriptor.comArgumentKind)
            assertEquals("ABI value kind for $type", expectedAbi.abiValueKind, descriptor.abiValueKind)
            assertEquals("array element width for $type", expectedAbi.sizeBytes.toString(), integralAbiSizeExpression(type).toString())
            assertEquals("delegate argument kind for $type", expectedAbi.argumentKind, abiArgumentKindForIntegralType(type))
            assertEquals("raw carrier expression for $type", expectedAbi.rawCarrierExpression, integralAbiCarrierExpression(type, CodeBlock.of("rawArgs[0]")).toString())
        }
    }

    private data class ExpectedIntegralAbi(
        val sizeBytes: Int,
        val argumentKind: KotlinProjectionComArgumentKind,
        val abiValueKind: KotlinProjectionAbiValueKind,
        val rawCarrierExpression: String,
    )
}
