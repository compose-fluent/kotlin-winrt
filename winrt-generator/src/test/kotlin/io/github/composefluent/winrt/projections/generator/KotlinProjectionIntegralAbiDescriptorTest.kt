package io.github.composefluent.winrt.projections.generator

import com.squareup.kotlinpoet.CodeBlock
import io.github.composefluent.winrt.metadata.WinRTIntegralType
import org.junit.Assert.assertEquals
import org.junit.Test

class KotlinProjectionIntegralAbiDescriptorTest {
    @Test
    fun integral_abi_descriptor_owns_width_and_com_carrier_kind() {
        val platformAbiName = PLATFORM_ABI_CLASS_NAME.canonicalName
        val typeSignatureName = WINRT_TYPE_SIGNATURE_CLASS_NAME.canonicalName
        val expected = mapOf(
            WinRTIntegralType.Int8 to ExpectedIntegralAbi(1, KotlinProjectionComArgumentKind.Int8, KotlinProjectionAbiValueKind.Int8, "INT8", "readInt8", "writeInt8", "int8", null, null, null, "rawArgs[0] as kotlin.Byte", "$platformAbiName.readInt8(address)", "$platformAbiName.writeInt8(address, value)"),
            WinRTIntegralType.UInt8 to ExpectedIntegralAbi(1, KotlinProjectionComArgumentKind.Int8, KotlinProjectionAbiValueKind.UInt8, "UINT8", "readInt8", "writeInt8", "uint8", null, null, null, "(rawArgs[0] as kotlin.Byte).toUByte()", "$platformAbiName.readInt8(address).toUByte()", "$platformAbiName.writeInt8(address, value.toByte())"),
            WinRTIntegralType.Int16 to ExpectedIntegralAbi(2, KotlinProjectionComArgumentKind.Int16, KotlinProjectionAbiValueKind.Int16, "INT16", "readInt16", "writeInt16", "int16", null, null, null, "rawArgs[0] as kotlin.Short", "$platformAbiName.readInt16(address)", "$platformAbiName.writeInt16(address, value)"),
            WinRTIntegralType.UInt16 to ExpectedIntegralAbi(2, KotlinProjectionComArgumentKind.Int16, KotlinProjectionAbiValueKind.UInt16, "UINT16", "readInt16", "writeInt16", "uint16", null, null, null, "(rawArgs[0] as kotlin.Short).toUShort()", "$platformAbiName.readInt16(address).toUShort()", "$platformAbiName.writeInt16(address, value.toShort())"),
            WinRTIntegralType.Int32 to ExpectedIntegralAbi(4, KotlinProjectionComArgumentKind.Int32, KotlinProjectionAbiValueKind.Int32, "INT32", "readInt32", "writeInt32", "int32", "Int32", "getInt32", "setInt32", "rawArgs[0] as kotlin.Int", "$platformAbiName.readInt32(address)", "$platformAbiName.writeInt32(address, value)"),
            WinRTIntegralType.UInt32 to ExpectedIntegralAbi(4, KotlinProjectionComArgumentKind.Int32, KotlinProjectionAbiValueKind.UInt32, "UINT32", "readInt32", "writeInt32", "uint32", "UInt32", "getUInt32", "setUInt32", "(rawArgs[0] as kotlin.Int).toUInt()", "$platformAbiName.readInt32(address).toUInt()", "$platformAbiName.writeInt32(address, value.toInt())"),
            WinRTIntegralType.Int64 to ExpectedIntegralAbi(8, KotlinProjectionComArgumentKind.Int64, KotlinProjectionAbiValueKind.Int64, "INT64", "readInt64", "writeInt64", "int64", null, null, null, "rawArgs[0] as kotlin.Long", "$platformAbiName.readInt64(address)", "$platformAbiName.writeInt64(address, value)"),
            WinRTIntegralType.UInt64 to ExpectedIntegralAbi(8, KotlinProjectionComArgumentKind.Int64, KotlinProjectionAbiValueKind.UInt64, "UINT64", "readInt64", "writeInt64", "uint64", null, null, null, "(rawArgs[0] as kotlin.Long).toULong()", "$platformAbiName.readInt64(address).toULong()", "$platformAbiName.writeInt64(address, value.toLong())"),
        )

        expected.forEach { (type, expectedAbi) ->
            val descriptor = integralAbiDescriptor(type)
            assertEquals("ABI width for $type", expectedAbi.sizeBytes, descriptor.abiSizeBytes)
            assertEquals("COM argument kind for $type", expectedAbi.argumentKind, descriptor.comArgumentKind)
            assertEquals("ABI value kind for $type", expectedAbi.abiValueKind, descriptor.abiValueKind)
            assertEquals("delegate value kind for $type", expectedAbi.delegateValueKindName, descriptor.delegateValueKindName)
            assertEquals("platform read function for $type", expectedAbi.platformReadFunctionName, descriptor.platformReadFunctionName)
            assertEquals("platform write function for $type", expectedAbi.platformWriteFunctionName, descriptor.platformWriteFunctionName)
            assertEquals("type signature function for $type", expectedAbi.typeSignatureFunctionName, descriptor.typeSignatureFunctionName)
            assertEquals("intrinsic shape for $type", expectedAbi.projectionIntrinsicShapeName, descriptor.projectionIntrinsicShapeName)
            assertEquals("intrinsic getter for $type", expectedAbi.projectionIntrinsicGetterName, descriptor.projectionIntrinsicGetterName)
            assertEquals("intrinsic setter for $type", expectedAbi.projectionIntrinsicSetterName, descriptor.projectionIntrinsicSetterName)
            assertEquals("array element width for $type", expectedAbi.sizeBytes.toString(), integralAbiSizeExpression(type).toString())
            assertEquals("delegate argument kind for $type", expectedAbi.argumentKind, abiArgumentKindForIntegralType(type))
            assertEquals("raw carrier expression for $type", expectedAbi.rawCarrierExpression, integralAbiCarrierExpression(type, CodeBlock.of("rawArgs[0]")).toString())
            assertEquals("delegate enum cast for $type", "rawArgs[0] as ${descriptor.kotlinTypeName}", integralKotlinCastExpression(type, CodeBlock.of("rawArgs[0]")).toString())
            assertEquals("platform read expression for $type", expectedAbi.platformReadExpression, integralPlatformReadExpression(type, CodeBlock.of("address")).toString())
            assertEquals("platform write expression for $type", expectedAbi.platformWriteExpression, integralPlatformWriteCode(type, CodeBlock.of("address"), CodeBlock.of("value")).toString())
            assertEquals("type signature expression for $type", "$typeSignatureName.${expectedAbi.typeSignatureFunctionName}()", integralTypeSignatureCode(type).toString())
        }
    }

    private data class ExpectedIntegralAbi(
        val sizeBytes: Int,
        val argumentKind: KotlinProjectionComArgumentKind,
        val abiValueKind: KotlinProjectionAbiValueKind,
        val delegateValueKindName: String,
        val platformReadFunctionName: String,
        val platformWriteFunctionName: String,
        val typeSignatureFunctionName: String,
        val projectionIntrinsicShapeName: String?,
        val projectionIntrinsicGetterName: String?,
        val projectionIntrinsicSetterName: String?,
        val rawCarrierExpression: String,
        val platformReadExpression: String,
        val platformWriteExpression: String,
    )
}
