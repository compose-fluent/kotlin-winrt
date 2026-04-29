package io.github.kitectlab.winrt.projections.generator

import io.github.kitectlab.winrt.metadata.WinRtMetadataModel
import io.github.kitectlab.winrt.metadata.WinRtAbiMarshalerPlanDescriptor
import io.github.kitectlab.winrt.metadata.WinRtAbiMarshalerSlotDescriptor
import io.github.kitectlab.winrt.metadata.WinRtCustomMappedMemberOutputDescriptor
import io.github.kitectlab.winrt.metadata.WinRtEventDefinition
import io.github.kitectlab.winrt.metadata.WinRtEventInvokeDescriptor
import io.github.kitectlab.winrt.metadata.WinRtFactorySurfaceDescriptor
import io.github.kitectlab.winrt.metadata.WinRtFieldDefinition
import io.github.kitectlab.winrt.metadata.WinRtGenericAbiClassInitializationDescriptor
import io.github.kitectlab.winrt.metadata.WinRtGenericAbiInventory
import io.github.kitectlab.winrt.metadata.WinRtGenericInstantiationWriterDescriptor
import io.github.kitectlab.winrt.metadata.WinRtGuidSignatureDescriptor
import io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition
import io.github.kitectlab.winrt.metadata.WinRtInterfaceMemberSignatureSetDescriptor
import io.github.kitectlab.winrt.metadata.WinRtIntegralType
import io.github.kitectlab.winrt.metadata.WinRtMetadataProjectionContext
import io.github.kitectlab.winrt.metadata.WinRtMetadataProjectionInventory
import io.github.kitectlab.winrt.metadata.WinRtMetadataProjectionInventoryBuilder
import io.github.kitectlab.winrt.metadata.WinRtMetadataParameterCategory
import io.github.kitectlab.winrt.metadata.WinRtModuleActivationAndAuthoringDescriptor
import io.github.kitectlab.winrt.metadata.WinRtMethodVtableDescriptor
import io.github.kitectlab.winrt.metadata.WinRtMethodDefinition
import io.github.kitectlab.winrt.metadata.WinRtNamespace
import io.github.kitectlab.winrt.metadata.WinRtObjectReferenceSurfaceDescriptor
import io.github.kitectlab.winrt.metadata.WinRtPropertyDefinition
import io.github.kitectlab.winrt.metadata.WinRtRequiredInterfaceAugmentationDescriptor
import io.github.kitectlab.winrt.metadata.WinRtSignatureWriterDescriptor
import io.github.kitectlab.winrt.metadata.WinRtTypeDeclarationDescriptor
import io.github.kitectlab.winrt.metadata.WinRtTypeDefinition
import io.github.kitectlab.winrt.metadata.WinRtTypeRef
import io.github.kitectlab.winrt.metadata.WinRtTypeKind
import io.github.kitectlab.winrt.metadata.WinRtMetadataValidationOptions
import io.github.kitectlab.winrt.metadata.WinRtMetadataSemanticHelpers
import io.github.kitectlab.winrt.metadata.requireValidForProjection
import io.github.kitectlab.winrt.metadata.semanticHelpers
import io.github.kitectlab.winrt.runtime.ActivationFactory
import io.github.kitectlab.winrt.runtime.ComObjectReference
import io.github.kitectlab.winrt.runtime.ComVtableInvoker
import io.github.kitectlab.winrt.runtime.Guid
import io.github.kitectlab.winrt.runtime.HResult
import io.github.kitectlab.winrt.runtime.HString
import io.github.kitectlab.winrt.runtime.IUnknownReference
import io.github.kitectlab.winrt.runtime.IWinRTObject
import io.github.kitectlab.winrt.runtime.Marshaler
import io.github.kitectlab.winrt.runtime.PlatformAbi
import io.github.kitectlab.winrt.runtime.ParameterizedInterfaceId
import io.github.kitectlab.winrt.runtime.RawAddress
import io.github.kitectlab.winrt.runtime.NativeNestedStructFieldSpec
import io.github.kitectlab.winrt.runtime.NativeScalarFieldSpec
import io.github.kitectlab.winrt.runtime.NativeStructLayout
import io.github.kitectlab.winrt.runtime.NativeStructScalarKind
import io.github.kitectlab.winrt.runtime.WinRtBindableIterableProjection
import io.github.kitectlab.winrt.runtime.WinRtBindableVectorProjection
import io.github.kitectlab.winrt.runtime.WinRtBindableVectorViewProjection
import io.github.kitectlab.winrt.runtime.WinRtCollectionInterfaceIds
import io.github.kitectlab.winrt.runtime.WinRtDictionaryProjection
import io.github.kitectlab.winrt.runtime.WinRtIterableProjection
import io.github.kitectlab.winrt.runtime.WinRtListProjection
import io.github.kitectlab.winrt.runtime.WinRtAsyncActionReference
import io.github.kitectlab.winrt.runtime.WinRtAsyncActionWithProgressReference
import io.github.kitectlab.winrt.runtime.WinRtAsyncActionWithProgressVftblSlots
import io.github.kitectlab.winrt.runtime.WinRtAsyncOperationReference
import io.github.kitectlab.winrt.runtime.WinRtAsyncOperationWithProgressReference
import io.github.kitectlab.winrt.runtime.WinRtAsyncOperationWithProgressVftblSlots
import io.github.kitectlab.winrt.runtime.WinRtAsyncOperationVftblSlots
import io.github.kitectlab.winrt.runtime.WinRtReadOnlyDictionaryProjection
import io.github.kitectlab.winrt.runtime.WinRtReadOnlyListProjection
import io.github.kitectlab.winrt.runtime.WinRtReferenceArrayProjection
import io.github.kitectlab.winrt.runtime.WinRtReferenceProjection
import io.github.kitectlab.winrt.runtime.WinRtReferenceValueAdapter
import io.github.kitectlab.winrt.runtime.WinRtPlatformApi
import io.github.kitectlab.winrt.runtime.WinRtTypeSignature
import io.github.kitectlab.winrt.runtime.WinRtTypeHandle
import io.github.kitectlab.winrt.runtime.WinRtUri
import io.github.kitectlab.winrt.runtime.WinRtDelegateBridge
import io.github.kitectlab.winrt.runtime.WinRtDelegateDescriptor
import io.github.kitectlab.winrt.runtime.WinRtDelegateReference
import io.github.kitectlab.winrt.runtime.WinRtDelegateValueKind
import io.github.kitectlab.winrt.runtime.WinRtEvent
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asClassName
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.OffsetDateTime
import kotlin.collections.AbstractList
import kotlin.collections.AbstractMap
import kotlin.LazyThreadSafetyMode
import kotlin.io.path.extension

internal fun KotlinProjectionRenderer.arrayReturnMarshaler(
    returnBinding: KotlinProjectionAbiTypeBinding,
    descriptor: WinRtAbiMarshalerSlotDescriptor? = null,
): KotlinProjectionAbiMarshalerPlan? {
    @Suppress("UNUSED_PARAMETER")
    descriptor
    val elementBinding = returnBinding.typeArguments.singleOrNull() ?: return null
    nonBlittableArrayElementMarshalerExpression(elementBinding)?.let { elementMarshaler ->
        return nonBlittableArrayReturnMarshaler(returnBinding, elementMarshaler)
    }
    val elementSize = nativeArrayElementSizeExpression(elementBinding) ?: return null
    val elementRead = nativeArrayElementReadCode(
        elementBinding = elementBinding,
        dataExpression = CodeBlock.of("__arrayData"),
        indexExpression = CodeBlock.of("__index"),
    ) ?: return null
    return KotlinProjectionAbiMarshalerPlan(
        name = "retval",
        typeBinding = returnBinding,
        isReturn = true,
        abiArgumentExpression = CodeBlock.of("__resultLengthOut"),
        abiArgumentKind = KotlinProjectionComArgumentKind.Pointer,
        extraAbiArgumentExpressions = listOf(CodeBlock.of("__resultDataOut")),
        extraAbiArgumentKinds = listOf(KotlinProjectionComArgumentKind.Pointer),
        resultLocalDeclarations = CodeBlock.of(
            "val __resultLengthOut = %T.allocateInt32Slot(__scope)\nval __resultDataOut = %T.allocatePointerSlot(__scope)\n",
            PLATFORM_ABI_CLASS_NAME,
            PLATFORM_ABI_CLASS_NAME,
        ),
        readbackStatement = CodeBlock.of(
            """
            val __arrayLength = %T.readInt32(__resultLengthOut)
            val __arrayData = %T.readPointer(__resultDataOut)
            return Array(__arrayLength) { __index ->
                %L
            }
            """.trimIndent() + "\n",
            PLATFORM_ABI_CLASS_NAME,
            PLATFORM_ABI_CLASS_NAME,
            elementRead,
        ),
    )
}

internal fun KotlinProjectionRenderer.nonBlittableArrayReturnMarshaler(
    returnBinding: KotlinProjectionAbiTypeBinding,
    elementMarshaler: CodeBlock,
): KotlinProjectionAbiMarshalerPlan =
    KotlinProjectionAbiMarshalerPlan(
        name = "retval",
        typeBinding = returnBinding,
        isReturn = true,
        abiArgumentExpression = CodeBlock.of("__resultLengthOut"),
        abiArgumentKind = KotlinProjectionComArgumentKind.Pointer,
        extraAbiArgumentExpressions = listOf(CodeBlock.of("__resultDataOut")),
        extraAbiArgumentKinds = listOf(KotlinProjectionComArgumentKind.Pointer),
        resultLocalDeclarations = CodeBlock.of(
            "val __resultLengthOut = %T.allocateInt32Slot(__scope)\nval __resultDataOut = %T.allocatePointerSlot(__scope)\n",
            PLATFORM_ABI_CLASS_NAME,
            PLATFORM_ABI_CLASS_NAME,
        ),
        readbackStatement = CodeBlock.of(
            """
            val __arrayLength = %T.readInt32(__resultLengthOut)
            val __arrayData = %T.readPointer(__resultDataOut)
            val __arrayMarshaler = %L
            val __arrayResult = __arrayMarshaler.fromAbiArray(__arrayLength, __arrayData)?.toTypedArray() ?: emptyArray()
            __arrayMarshaler.disposeAbiArray(__arrayLength, __arrayData)
            return __arrayResult as %T
            """.trimIndent() + "\n",
            PLATFORM_ABI_CLASS_NAME,
            PLATFORM_ABI_CLASS_NAME,
            elementMarshaler,
            resolveTypeName(returnBinding.typeName),
        ),
    )

internal fun KotlinProjectionRenderer.nativeStructClassName(
    binding: KotlinProjectionAbiTypeBinding,
): ClassName? {
    mappedTypeByAbiName(binding.typeName.substringBefore('<').removeSuffix("?"))
        ?.takeIf { it.abiValueKind == KotlinProjectionAbiValueKind.Struct }
        ?.let { mappedType -> return mappedType.projectedTypeResolver(emptyList()) as? ClassName }
    mappedTypeByAbiName(binding.resolvedTypeName.substringBefore('<').removeSuffix("?"))
        ?.takeIf { it.abiValueKind == KotlinProjectionAbiValueKind.Struct }
        ?.let { mappedType -> return mappedType.projectedTypeResolver(emptyList()) as? ClassName }
    return runCatching { resolveTypeName(binding.typeName) as? ClassName }.getOrNull()
        ?: runCatching { resolveTypeName(binding.resolvedTypeName) as? ClassName }.getOrNull()
}

internal fun customStructAbi(
    binding: KotlinProjectionAbiTypeBinding,
): KotlinProjectionCustomStructAbi? =
    mappedTypeByAbiName(binding.typeName.substringBefore('<').removeSuffix("?"))?.customStructAbi
        ?: mappedTypeByAbiName(binding.resolvedTypeName.substringBefore('<').removeSuffix("?"))?.customStructAbi

internal fun customObjectAbi(
    binding: KotlinProjectionAbiTypeBinding,
): KotlinProjectionCustomObjectAbi? =
    mappedTypeByAbiName(binding.typeName.substringBefore('<').removeSuffix("?"))?.customObjectAbi
        ?: mappedTypeByAbiName(binding.resolvedTypeName.substringBefore('<').removeSuffix("?"))?.customObjectAbi

internal fun KotlinProjectionRenderer.customObjectReturnReadback(
    binding: KotlinProjectionAbiTypeBinding,
): CodeBlock? {
    val customAbi = customObjectAbi(binding) ?: return null
    val projectedType = resolveTypeName(binding.resolvedTypeName)
    val nullReadback = abiNullReturnReadback(binding)
    return if (customAbi.fromAbiFunctionName == "objectFromAbi") {
        CodeBlock.of(
            "val __resultPointer = %T.readPointer(__resultOut)\n%Lreturn %T.%L(__resultPointer, %T(%S, %T(%S)), %T::class) ?: error(%S)\n",
            PLATFORM_ABI_CLASS_NAME,
            nullReadback,
            WINRT_SYSTEM_PROJECTION_MARSHALERS_CLASS_NAME,
            customAbi.fromAbiFunctionName,
            WINRT_TYPE_HANDLE_CLASS_NAME,
            customAbi.typeHandleName,
            GUID_CLASS_NAME,
            customAbi.interfaceId.toString(),
            projectedType,
            "WINRT_E_NULL_ABI_PROJECTED_RETURN",
        )
    } else {
        CodeBlock.of(
            "val __resultPointer = %T.readPointer(__resultOut)\n%Lreturn %T.%L(__resultPointer) ?: error(%S)\n",
            PLATFORM_ABI_CLASS_NAME,
            nullReadback,
            WINRT_SYSTEM_PROJECTION_MARSHALERS_CLASS_NAME,
            customAbi.fromAbiFunctionName,
            "WINRT_E_NULL_ABI_PROJECTED_RETURN",
        )
    }
}

internal fun KotlinProjectionRenderer.nativeArrayElementSizeExpression(
    elementBinding: KotlinProjectionAbiTypeBinding,
): CodeBlock? =
    when (elementBinding.kind) {
        KotlinProjectionAbiValueKind.Boolean,
        KotlinProjectionAbiValueKind.Int8,
        KotlinProjectionAbiValueKind.UInt8 -> CodeBlock.of("1")
        KotlinProjectionAbiValueKind.Int16,
        KotlinProjectionAbiValueKind.UInt16,
        KotlinProjectionAbiValueKind.Char16 -> CodeBlock.of("2")
        KotlinProjectionAbiValueKind.Int32,
        KotlinProjectionAbiValueKind.UInt32,
        KotlinProjectionAbiValueKind.Float -> CodeBlock.of("4")
        KotlinProjectionAbiValueKind.Int64,
        KotlinProjectionAbiValueKind.UInt64,
        KotlinProjectionAbiValueKind.Double -> CodeBlock.of("8")
        KotlinProjectionAbiValueKind.GuidValue -> CodeBlock.of("%T.BYTE_SIZE.toLong()", GUID_CLASS_NAME)
        KotlinProjectionAbiValueKind.Enum ->
            elementBinding.enumUnderlyingType?.let(::nativeArrayIntegralElementSizeExpression)
        KotlinProjectionAbiValueKind.Struct ->
            customStructAbi(elementBinding)?.let { CodeBlock.of("%LL", it.sizeBytes) }
                ?: nativeStructClassName(elementBinding)?.let { CodeBlock.of("%T.Metadata.layout.sizeBytes", it) }
        else -> null
    }

internal fun KotlinProjectionRenderer.nativeArrayIntegralElementSizeExpression(type: WinRtIntegralType): CodeBlock =
    when (type) {
        WinRtIntegralType.Int8,
        WinRtIntegralType.UInt8 -> CodeBlock.of("1")
        WinRtIntegralType.Int16,
        WinRtIntegralType.UInt16 -> CodeBlock.of("2")
        WinRtIntegralType.Int32,
        WinRtIntegralType.UInt32 -> CodeBlock.of("4")
        WinRtIntegralType.Int64,
        WinRtIntegralType.UInt64 -> CodeBlock.of("8")
    }

internal fun KotlinProjectionRenderer.nativeArrayElementSliceCode(
    elementBinding: KotlinProjectionAbiTypeBinding,
    dataExpression: CodeBlock,
    indexExpression: CodeBlock,
): CodeBlock? {
    val elementSize = nativeArrayElementSizeExpression(elementBinding) ?: return null
    return CodeBlock.of("%T.slice(%L, %L.toLong() * %L, %L)", PLATFORM_ABI_CLASS_NAME, dataExpression, indexExpression, elementSize, elementSize)
}

internal fun KotlinProjectionRenderer.nativeArrayElementReadCode(
    elementBinding: KotlinProjectionAbiTypeBinding,
    dataExpression: CodeBlock,
    indexExpression: CodeBlock,
): CodeBlock? {
    val slice = nativeArrayElementSliceCode(elementBinding, dataExpression, indexExpression) ?: return null
    return when (elementBinding.kind) {
        KotlinProjectionAbiValueKind.Boolean -> CodeBlock.of("%T.readInt8(%L).toInt() != 0", PLATFORM_ABI_CLASS_NAME, slice)
        KotlinProjectionAbiValueKind.Int8 -> CodeBlock.of("%T.readInt8(%L)", PLATFORM_ABI_CLASS_NAME, slice)
        KotlinProjectionAbiValueKind.UInt8 -> CodeBlock.of("%T.readInt8(%L).toUByte()", PLATFORM_ABI_CLASS_NAME, slice)
        KotlinProjectionAbiValueKind.Int16 -> CodeBlock.of("%T.readInt16(%L)", PLATFORM_ABI_CLASS_NAME, slice)
        KotlinProjectionAbiValueKind.UInt16 -> CodeBlock.of("%T.readInt16(%L).toUShort()", PLATFORM_ABI_CLASS_NAME, slice)
        KotlinProjectionAbiValueKind.Int32 -> CodeBlock.of("%T.readInt32(%L)", PLATFORM_ABI_CLASS_NAME, slice)
        KotlinProjectionAbiValueKind.UInt32 -> CodeBlock.of("%T.readInt32(%L).toUInt()", PLATFORM_ABI_CLASS_NAME, slice)
        KotlinProjectionAbiValueKind.Int64 -> CodeBlock.of("%T.readInt64(%L)", PLATFORM_ABI_CLASS_NAME, slice)
        KotlinProjectionAbiValueKind.UInt64 -> CodeBlock.of("%T.readInt64(%L).toULong()", PLATFORM_ABI_CLASS_NAME, slice)
        KotlinProjectionAbiValueKind.Float -> CodeBlock.of("%T.readFloat(%L)", PLATFORM_ABI_CLASS_NAME, slice)
        KotlinProjectionAbiValueKind.Double -> CodeBlock.of("%T.readDouble(%L)", PLATFORM_ABI_CLASS_NAME, slice)
        KotlinProjectionAbiValueKind.Char16 -> CodeBlock.of("%T.readChar16(%L)", PLATFORM_ABI_CLASS_NAME, slice)
        KotlinProjectionAbiValueKind.GuidValue -> CodeBlock.of("%T.readGuid(%L)", PLATFORM_ABI_CLASS_NAME, slice)
        KotlinProjectionAbiValueKind.Enum ->
            nativeArrayEnumElementReadCode(elementBinding, slice)
        KotlinProjectionAbiValueKind.Struct ->
            customStructAbi(elementBinding)?.let { CodeBlock.of("%T.%L(%L)", it.helperTypeName, it.fromAbiFunctionName, slice) }
                ?: nativeStructClassName(elementBinding)?.let { CodeBlock.of("%T.Metadata.fromAbi(%L)", it, slice) }
        else -> null
    }
}

internal fun KotlinProjectionRenderer.nativeArrayEnumElementReadCode(
    elementBinding: KotlinProjectionAbiTypeBinding,
    slice: CodeBlock,
): CodeBlock? {
    val enumType = resolvedReturnClassName(elementBinding) ?: return null
    val readback = when (elementBinding.enumUnderlyingType ?: return null) {
        WinRtIntegralType.Int8 -> CodeBlock.of("%T.readInt8(%L)", PLATFORM_ABI_CLASS_NAME, slice)
        WinRtIntegralType.UInt8 -> CodeBlock.of("%T.readInt8(%L).toUByte()", PLATFORM_ABI_CLASS_NAME, slice)
        WinRtIntegralType.Int16 -> CodeBlock.of("%T.readInt16(%L)", PLATFORM_ABI_CLASS_NAME, slice)
        WinRtIntegralType.UInt16 -> CodeBlock.of("%T.readInt16(%L).toUShort()", PLATFORM_ABI_CLASS_NAME, slice)
        WinRtIntegralType.Int32 -> CodeBlock.of("%T.readInt32(%L)", PLATFORM_ABI_CLASS_NAME, slice)
        WinRtIntegralType.UInt32 -> CodeBlock.of("%T.readInt32(%L).toUInt()", PLATFORM_ABI_CLASS_NAME, slice)
        WinRtIntegralType.Int64 -> CodeBlock.of("%T.readInt64(%L)", PLATFORM_ABI_CLASS_NAME, slice)
        WinRtIntegralType.UInt64 -> CodeBlock.of("%T.readInt64(%L).toULong()", PLATFORM_ABI_CLASS_NAME, slice)
    }
    return CodeBlock.of("%T.Metadata.fromAbi(%L)", enumType, readback)
}

internal fun KotlinProjectionRenderer.nativeArrayElementWriteCode(
    elementBinding: KotlinProjectionAbiTypeBinding,
    dataExpression: CodeBlock,
    indexExpression: CodeBlock,
    valueExpression: CodeBlock,
): CodeBlock? {
    val slice = nativeArrayElementSliceCode(elementBinding, dataExpression, indexExpression) ?: return null
    return when (elementBinding.kind) {
        KotlinProjectionAbiValueKind.Boolean -> CodeBlock.of("%T.writeInt8(%L, if (%L) 1.toByte() else 0.toByte())", PLATFORM_ABI_CLASS_NAME, slice, valueExpression)
        KotlinProjectionAbiValueKind.Int8 -> CodeBlock.of("%T.writeInt8(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, valueExpression)
        KotlinProjectionAbiValueKind.UInt8 -> CodeBlock.of("%T.writeInt8(%L, %L.toByte())", PLATFORM_ABI_CLASS_NAME, slice, valueExpression)
        KotlinProjectionAbiValueKind.Int16 -> CodeBlock.of("%T.writeInt16(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, valueExpression)
        KotlinProjectionAbiValueKind.UInt16 -> CodeBlock.of("%T.writeInt16(%L, %L.toShort())", PLATFORM_ABI_CLASS_NAME, slice, valueExpression)
        KotlinProjectionAbiValueKind.Int32 -> CodeBlock.of("%T.writeInt32(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, valueExpression)
        KotlinProjectionAbiValueKind.UInt32 -> CodeBlock.of("%T.writeInt32(%L, %L.toInt())", PLATFORM_ABI_CLASS_NAME, slice, valueExpression)
        KotlinProjectionAbiValueKind.Int64 -> CodeBlock.of("%T.writeInt64(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, valueExpression)
        KotlinProjectionAbiValueKind.UInt64 -> CodeBlock.of("%T.writeInt64(%L, %L.toLong())", PLATFORM_ABI_CLASS_NAME, slice, valueExpression)
        KotlinProjectionAbiValueKind.Float -> CodeBlock.of("%T.writeFloat(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, valueExpression)
        KotlinProjectionAbiValueKind.Double -> CodeBlock.of("%T.writeDouble(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, valueExpression)
        KotlinProjectionAbiValueKind.Char16 -> CodeBlock.of("%T.writeChar16(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, valueExpression)
        KotlinProjectionAbiValueKind.GuidValue -> CodeBlock.of("%T.writeGuid(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, valueExpression)
        KotlinProjectionAbiValueKind.Enum ->
            nativeArrayEnumElementWriteCode(elementBinding, slice, valueExpression)
        KotlinProjectionAbiValueKind.Struct ->
            customStructAbi(elementBinding)?.let { CodeBlock.of("%T.%L(%L, %L)", it.helperTypeName, it.copyToFunctionName, valueExpression, slice) }
                ?: nativeStructClassName(elementBinding)?.let { CodeBlock.of("%T.Metadata.copyTo(%L, %L)", it, valueExpression, slice) }
        else -> null
    }
}

internal fun KotlinProjectionRenderer.nativeArrayEnumElementWriteCode(
    elementBinding: KotlinProjectionAbiTypeBinding,
    slice: CodeBlock,
    valueExpression: CodeBlock,
): CodeBlock? {
    val enumType = resolvedReturnClassName(elementBinding) ?: return null
    val abiValue = CodeBlock.of("%T.Metadata.toAbi(%L)", enumType, valueExpression)
    return when (elementBinding.enumUnderlyingType ?: return null) {
        WinRtIntegralType.Int8 -> CodeBlock.of("%T.writeInt8(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, abiValue)
        WinRtIntegralType.UInt8 -> CodeBlock.of("%T.writeInt8(%L, %L.toByte())", PLATFORM_ABI_CLASS_NAME, slice, abiValue)
        WinRtIntegralType.Int16 -> CodeBlock.of("%T.writeInt16(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, abiValue)
        WinRtIntegralType.UInt16 -> CodeBlock.of("%T.writeInt16(%L, %L.toShort())", PLATFORM_ABI_CLASS_NAME, slice, abiValue)
        WinRtIntegralType.Int32 -> CodeBlock.of("%T.writeInt32(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, abiValue)
        WinRtIntegralType.UInt32 -> CodeBlock.of("%T.writeInt32(%L, %L.toInt())", PLATFORM_ABI_CLASS_NAME, slice, abiValue)
        WinRtIntegralType.Int64 -> CodeBlock.of("%T.writeInt64(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, abiValue)
        WinRtIntegralType.UInt64 -> CodeBlock.of("%T.writeInt64(%L, %L.toLong())", PLATFORM_ABI_CLASS_NAME, slice, abiValue)
    }
}

internal fun KotlinProjectionRenderer.nativeStructParameterMarshaler(
    parameterBinding: KotlinProjectionAbiParameterBinding,
): KotlinProjectionAbiMarshalerPlan? {
    customStructAbi(parameterBinding.typeBinding)?.let { customAbi ->
        val parameterName = parameterBinding.name
        val scopeName = "__${parameterName}StructScope"
        val abiLocalName = "__${parameterName}Abi"
        return KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%L", abiLocalName),
            abiArgumentKind = KotlinProjectionComArgumentKind.Pointer,
            scopeOpeners = listOf(
                CodeBlock.of(
                    "%T.confinedScope().use { %L ->\nval %L = %T.allocateBytes(%L, %LL)\n%T.%L(%L, %L)",
                    PLATFORM_ABI_CLASS_NAME,
                    scopeName,
                    abiLocalName,
                    PLATFORM_ABI_CLASS_NAME,
                    scopeName,
                    customAbi.sizeBytes,
                    customAbi.helperTypeName,
                    customAbi.copyToFunctionName,
                    parameterName,
                    abiLocalName,
                ),
            ),
            finallyStatements = customAbi.disposeAbiFunctionName?.let { disposeFunctionName ->
                listOf(CodeBlock.of("%T.%L(%L)", customAbi.helperTypeName, disposeFunctionName, abiLocalName))
            }.orEmpty(),
        )
    }
    val structType = nativeStructClassName(parameterBinding.typeBinding) ?: return null
    val parameterName = parameterBinding.name
    val scopeName = "__${parameterName}StructScope"
    val abiLocalName = "__${parameterName}Abi"
    return KotlinProjectionAbiMarshalerPlan(
        name = parameterName,
        typeBinding = parameterBinding.typeBinding,
        isReturn = false,
        abiArgumentExpression = CodeBlock.of("%L", abiLocalName),
        abiArgumentKind = KotlinProjectionComArgumentKind.Pointer,
        scopeOpeners = listOf(
            CodeBlock.of(
                "%T.confinedScope().use { %L ->\nval %L = %T.allocateBytes(%L, %T.Metadata.layout.sizeBytes)\n%T.Metadata.copyTo(%L, %L)",
                PLATFORM_ABI_CLASS_NAME,
                scopeName,
                abiLocalName,
                PLATFORM_ABI_CLASS_NAME,
                scopeName,
                structType,
                structType,
                parameterName,
                abiLocalName,
            ),
        ),
        finallyStatements = listOf(CodeBlock.of("%T.Metadata.disposeAbi(%L)", structType, abiLocalName)),
    )
}

internal fun KotlinProjectionRenderer.arrayParameterMarshaler(
    parameterBinding: KotlinProjectionAbiParameterBinding,
    descriptor: WinRtAbiMarshalerSlotDescriptor? = null,
): KotlinProjectionAbiMarshalerPlan? {
    val category = descriptor?.category ?: parameterBinding.category
    val elementBinding = parameterBinding.typeBinding.typeArguments.singleOrNull() ?: return null
    nonBlittableArrayElementMarshalerExpression(elementBinding)?.let { elementMarshaler ->
        return nonBlittableArrayParameterMarshaler(parameterBinding, category, elementMarshaler)
    }
    val elementSize = nativeArrayElementSizeExpression(elementBinding) ?: return null
    val parameterName = parameterBinding.name
    if (category == WinRtMetadataParameterCategory.ReceiveArray) {
        val lengthOutName = "__${parameterName}LengthOut"
        val dataOutName = "__${parameterName}DataOut"
        return KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%L", lengthOutName),
            abiArgumentKind = KotlinProjectionComArgumentKind.Pointer,
            extraAbiArgumentExpressions = listOf(CodeBlock.of("%L", dataOutName)),
            extraAbiArgumentKinds = listOf(KotlinProjectionComArgumentKind.Pointer),
            scopeOpeners = listOf(
                CodeBlock.of(
                    "%T.confinedScope().use { __${parameterName}OutScope ->\nval %L = %T.allocateInt32Slot(__${parameterName}OutScope)\nval %L = %T.allocatePointerSlot(__${parameterName}OutScope)",
                    PLATFORM_ABI_CLASS_NAME,
                    lengthOutName,
                    PLATFORM_ABI_CLASS_NAME,
                    dataOutName,
                    PLATFORM_ABI_CLASS_NAME,
                ),
            ),
        )
    }
    val scopeName = "__${parameterName}ArrayScope"
    val dataName = "__${parameterName}ArrayData"
    val elementWrite = nativeArrayElementWriteCode(
        elementBinding = elementBinding,
        dataExpression = CodeBlock.of("%L", dataName),
        indexExpression = CodeBlock.of("__index"),
        valueExpression = CodeBlock.of("__element"),
    ) ?: return null
    val elementRead = nativeArrayElementReadCode(
        elementBinding = elementBinding,
        dataExpression = CodeBlock.of("%L", dataName),
        indexExpression = CodeBlock.of("__index"),
    )
    return KotlinProjectionAbiMarshalerPlan(
        name = parameterName,
        typeBinding = parameterBinding.typeBinding,
        isReturn = false,
        abiArgumentExpression = CodeBlock.of("%L.size", parameterName),
        abiArgumentKind = KotlinProjectionComArgumentKind.Int32,
        extraAbiArgumentExpressions = listOf(CodeBlock.of("%L", dataName)),
        extraAbiArgumentKinds = listOf(KotlinProjectionComArgumentKind.Pointer),
        postCallStatements = if (category == WinRtMetadataParameterCategory.FillArray && elementRead != null) {
            listOf(
                CodeBlock.of(
                    """
                    %L.indices.forEach { __index ->
                        %L[__index] = %L
                    }
                    """.trimIndent(),
                    parameterName,
                    parameterName,
                    elementRead,
                ),
            )
        } else {
            emptyList()
        },
        scopeOpeners = listOf(
            CodeBlock.of(
                """
                %T.confinedScope().use { %L ->
                val %L = %T.allocateBytes(%L, %L.size.toLong() * %L)
                %L.forEachIndexed { __index, __element ->
                    %L
                }
                """.trimIndent(),
                PLATFORM_ABI_CLASS_NAME,
                scopeName,
                dataName,
                PLATFORM_ABI_CLASS_NAME,
                scopeName,
                parameterName,
                elementSize,
                parameterName,
                elementWrite,
            ),
        ),
    )
}

internal fun KotlinProjectionRenderer.nonBlittableArrayParameterMarshaler(
    parameterBinding: KotlinProjectionAbiParameterBinding,
    category: WinRtMetadataParameterCategory,
    elementMarshaler: CodeBlock,
): KotlinProjectionAbiMarshalerPlan? {
    if (category == WinRtMetadataParameterCategory.ReceiveArray) {
        return null
    }
    val parameterName = parameterBinding.name
    val marshalerName = "__${parameterName}ArrayMarshaler"
    val arrayName = "__${parameterName}ArrayAbi"
    return KotlinProjectionAbiMarshalerPlan(
        name = parameterName,
        typeBinding = parameterBinding.typeBinding,
        isReturn = false,
        abiArgumentExpression = CodeBlock.of("%L?.length ?: 0", arrayName),
        abiArgumentKind = KotlinProjectionComArgumentKind.Int32,
        extraAbiArgumentExpressions = listOf(CodeBlock.of("%L?.data ?: %T.nullPointer", arrayName, PLATFORM_ABI_CLASS_NAME)),
        extraAbiArgumentKinds = listOf(KotlinProjectionComArgumentKind.Pointer),
        postCallStatements = if (category == WinRtMetadataParameterCategory.FillArray) {
            listOf(
                CodeBlock.of(
                    """
                    %L.fromAbiArray(%L.size, %L?.data ?: %T.nullPointer)?.forEachIndexed { __index, __element ->
                        (%L as Array<Any?>)[__index] = __element
                    }
                    """.trimIndent(),
                    marshalerName,
                    parameterName,
                    arrayName,
                    PLATFORM_ABI_CLASS_NAME,
                    parameterName,
                ),
            )
        } else {
            emptyList()
        },
        scopeOpeners = listOf(
            CodeBlock.of(
                """
                val %L = %L
                %L.createMarshalerArray(%L).use { %L ->
                """.trimIndent(),
                marshalerName,
                elementMarshaler,
                marshalerName,
                parameterName,
                arrayName,
            ),
        ),
    )
}

internal fun KotlinProjectionRenderer.nonBlittableArrayElementMarshalerExpression(
    elementBinding: KotlinProjectionAbiTypeBinding,
): CodeBlock? =
    when (elementBinding.kind) {
        KotlinProjectionAbiValueKind.String -> CodeBlock.of("%T.string()", MARSHALER_CLASS_NAME)
        KotlinProjectionAbiValueKind.Object,
        KotlinProjectionAbiValueKind.InspectableReference -> CodeBlock.of("%T.inspectableAny()", MARSHALER_CLASS_NAME)
        KotlinProjectionAbiValueKind.ProjectedInterface -> {
            val interfaceId = elementBinding.interfaceId ?: return null
            val projectedType = resolveTypeName(elementBinding.resolvedTypeName)
            CodeBlock.of(
                "%T.interfaceType(%T(%S, %T(%S)), %T::class)",
                MARSHALER_CLASS_NAME,
                WINRT_TYPE_HANDLE_CLASS_NAME,
                elementBinding.resolvedTypeName,
                GUID_CLASS_NAME,
                interfaceId.toString(),
                projectedType,
            )
        }
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass -> {
            val projectedType = resolveTypeName(elementBinding.resolvedTypeName)
            CodeBlock.of("%T.inspectable(%T::class)", MARSHALER_CLASS_NAME, projectedType)
        }
        else -> null
    }
