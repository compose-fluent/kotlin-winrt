package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRtIntegralType
import org.junit.Assert.assertEquals
import org.junit.Test

class KotlinProjectionIntegralAbiDescriptorTest {
    @Test
    fun integral_abi_descriptor_owns_width_and_com_carrier_kind() {
        val expected = mapOf(
            WinRtIntegralType.Int8 to ExpectedIntegralAbi(1, KotlinProjectionComArgumentKind.Int8),
            WinRtIntegralType.UInt8 to ExpectedIntegralAbi(1, KotlinProjectionComArgumentKind.Int8),
            WinRtIntegralType.Int16 to ExpectedIntegralAbi(2, KotlinProjectionComArgumentKind.Int16),
            WinRtIntegralType.UInt16 to ExpectedIntegralAbi(2, KotlinProjectionComArgumentKind.Int16),
            WinRtIntegralType.Int32 to ExpectedIntegralAbi(4, KotlinProjectionComArgumentKind.Int32),
            WinRtIntegralType.UInt32 to ExpectedIntegralAbi(4, KotlinProjectionComArgumentKind.Int32),
            WinRtIntegralType.Int64 to ExpectedIntegralAbi(8, KotlinProjectionComArgumentKind.Int64),
            WinRtIntegralType.UInt64 to ExpectedIntegralAbi(8, KotlinProjectionComArgumentKind.Int64),
        )

        expected.forEach { (type, expectedAbi) ->
            val descriptor = integralAbiDescriptor(type)
            assertEquals("ABI width for $type", expectedAbi.sizeBytes, descriptor.abiSizeBytes)
            assertEquals("COM argument kind for $type", expectedAbi.argumentKind, descriptor.comArgumentKind)
            assertEquals("array element width for $type", expectedAbi.sizeBytes.toString(), integralAbiSizeExpression(type).toString())
            assertEquals("delegate argument kind for $type", expectedAbi.argumentKind, abiArgumentKindForIntegralType(type))
        }
    }

    private data class ExpectedIntegralAbi(
        val sizeBytes: Int,
        val argumentKind: KotlinProjectionComArgumentKind,
    )
}
