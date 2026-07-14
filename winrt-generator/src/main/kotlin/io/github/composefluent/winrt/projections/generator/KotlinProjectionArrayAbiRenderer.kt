package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTAbiMarshalerPlanDescriptor
import io.github.composefluent.winrt.metadata.WinRTAbiMarshalerSlotDescriptor
import io.github.composefluent.winrt.metadata.WinRTCustomMappedMemberOutputDescriptor
import io.github.composefluent.winrt.metadata.WinRTEventDefinition
import io.github.composefluent.winrt.metadata.WinRTEventInvokeDescriptor
import io.github.composefluent.winrt.metadata.WinRTFactorySurfaceDescriptor
import io.github.composefluent.winrt.metadata.WinRTFieldDefinition
import io.github.composefluent.winrt.metadata.WinRTGenericAbiClassInitializationDescriptor
import io.github.composefluent.winrt.metadata.WinRTGenericAbiInventory
import io.github.composefluent.winrt.metadata.WinRTGenericInstantiationWriterDescriptor
import io.github.composefluent.winrt.metadata.WinRTGuidSignatureDescriptor
import io.github.composefluent.winrt.metadata.WinRTInterfaceImplementationDefinition
import io.github.composefluent.winrt.metadata.WinRTInterfaceMemberSignatureSetDescriptor
import io.github.composefluent.winrt.metadata.WinRTIntegralType
import io.github.composefluent.winrt.metadata.WinRTMetadataProjectionContext
import io.github.composefluent.winrt.metadata.WinRTMetadataProjectionInventory
import io.github.composefluent.winrt.metadata.WinRTMetadataProjectionInventoryBuilder
import io.github.composefluent.winrt.metadata.WinRTMetadataParameterCategory
import io.github.composefluent.winrt.metadata.WinRTModuleActivationAndAuthoringDescriptor
import io.github.composefluent.winrt.metadata.WinRTMethodVtableDescriptor
import io.github.composefluent.winrt.metadata.WinRTMethodDefinition
import io.github.composefluent.winrt.metadata.WinRTNamespace
import io.github.composefluent.winrt.metadata.WinRTObjectReferenceSurfaceDescriptor
import io.github.composefluent.winrt.metadata.WinRTPropertyDefinition
import io.github.composefluent.winrt.metadata.WinRTRequiredInterfaceAugmentationDescriptor
import io.github.composefluent.winrt.metadata.WinRTSignatureWriterDescriptor
import io.github.composefluent.winrt.metadata.WinRTTypeDeclarationDescriptor
import io.github.composefluent.winrt.metadata.WinRTTypeDefinition
import io.github.composefluent.winrt.metadata.WinRTTypeRef
import io.github.composefluent.winrt.metadata.WinRTTypeKind
import io.github.composefluent.winrt.metadata.WinRTMetadataValidationOptions
import io.github.composefluent.winrt.metadata.WinRTMetadataSemanticHelpers
import io.github.composefluent.winrt.metadata.requireValidForProjection
import io.github.composefluent.winrt.metadata.semanticHelpers
import io.github.composefluent.winrt.runtime.ActivationFactory
import io.github.composefluent.winrt.runtime.ComObjectReference
import io.github.composefluent.winrt.runtime.ComVtableInvoker
import io.github.composefluent.winrt.runtime.Guid
import io.github.composefluent.winrt.runtime.HResult
import io.github.composefluent.winrt.runtime.HString
import io.github.composefluent.winrt.runtime.IUnknownReference
import io.github.composefluent.winrt.runtime.IWinRTObject
import io.github.composefluent.winrt.runtime.Marshaler
import io.github.composefluent.winrt.runtime.PlatformAbi
import io.github.composefluent.winrt.runtime.ParameterizedInterfaceId
import io.github.composefluent.winrt.runtime.RawAddress
import io.github.composefluent.winrt.runtime.NativeNestedStructFieldSpec
import io.github.composefluent.winrt.runtime.NativeScalarFieldSpec
import io.github.composefluent.winrt.runtime.NativeStructLayout
import io.github.composefluent.winrt.runtime.NativeStructScalarKind
import io.github.composefluent.winrt.runtime.WinRTBindableIterableProjection
import io.github.composefluent.winrt.runtime.WinRTBindableVectorProjection
import io.github.composefluent.winrt.runtime.WinRTBindableVectorViewProjection
import io.github.composefluent.winrt.runtime.WinRTCollectionInterfaceIds
import io.github.composefluent.winrt.runtime.WinRTDictionaryProjection
import io.github.composefluent.winrt.runtime.WinRTIterableProjection
import io.github.composefluent.winrt.runtime.WinRTListProjection
import io.github.composefluent.winrt.runtime.WinRTAsyncActionReference
import io.github.composefluent.winrt.runtime.WinRTAsyncActionWithProgressReference
import io.github.composefluent.winrt.runtime.WinRTAsyncActionWithProgressVftblSlots
import io.github.composefluent.winrt.runtime.WinRTAsyncOperationReference
import io.github.composefluent.winrt.runtime.WinRTAsyncOperationWithProgressReference
import io.github.composefluent.winrt.runtime.WinRTAsyncOperationWithProgressVftblSlots
import io.github.composefluent.winrt.runtime.WinRTAsyncOperationVftblSlots
import io.github.composefluent.winrt.runtime.WinRTReadOnlyDictionaryProjection
import io.github.composefluent.winrt.runtime.WinRTReadOnlyListProjection
import io.github.composefluent.winrt.runtime.WinRTReferenceArrayProjection
import io.github.composefluent.winrt.runtime.WinRTReferenceProjection
import io.github.composefluent.winrt.runtime.WinRTReferenceValueAdapter
import io.github.composefluent.winrt.runtime.WinRTPlatformApi
import io.github.composefluent.winrt.runtime.WinRTTypeSignature
import io.github.composefluent.winrt.runtime.WinRTTypeHandle
import io.github.composefluent.winrt.runtime.WinRTDelegateBridge
import io.github.composefluent.winrt.runtime.WinRTDelegateDescriptor
import io.github.composefluent.winrt.runtime.WinRTDelegateReference
import io.github.composefluent.winrt.runtime.WinRTDelegateValueKind
import io.github.composefluent.winrt.runtime.WinRTEvent
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
    descriptor: WinRTAbiMarshalerSlotDescriptor? = null,
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

internal fun KotlinProjectionRenderer.nativeStructAdapterClassName(
    binding: KotlinProjectionAbiTypeBinding,
): ClassName? {
    val typeName = binding.typeName.substringBefore('<').removeSuffix("?")
    val resolvedTypeName = binding.resolvedTypeName.substringBefore('<').removeSuffix("?")
    val mappedType = mappedTypeByAbiName(typeName) ?: mappedTypeByAbiName(resolvedTypeName)
    if (mappedType != null) {
        return mappedNativeStructAdapterClassName(mappedType)
    }
    return runCatching { resolveTypeName(binding.typeName) as? ClassName }.getOrNull()
        ?: runCatching { resolveTypeName(binding.resolvedTypeName) as? ClassName }.getOrNull()
}

private fun mappedNativeStructAdapterClassName(mappedType: KotlinProjectionMappedType): ClassName? {
    if (mappedType.abiValueKind != KotlinProjectionAbiValueKind.Struct) {
        return null
    }
    val projectedType = mappedType.projectedTypeResolver(emptyList()) as? ClassName ?: return null
    val customStructAbi = mappedType.customStructAbi
    if (customStructAbi != null && customStructAbi.helperTypeName != projectedType.nestedClass("Metadata")) {
        return null
    }
    return projectedType
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
    val projectedClassLiteralType = projectedType.copy(nullable = false)
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
            projectedClassLiteralType,
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

internal fun KotlinProjectionRenderer.nativeArrayIntegralElementSizeExpression(type: WinRTIntegralType): CodeBlock =
    integralAbiSizeExpression(type)

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
    val readback = integralPlatformReadExpression(elementBinding.enumUnderlyingType ?: return null, slice)
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
    return integralPlatformWriteCode(elementBinding.enumUnderlyingType ?: return null, slice, abiValue)
}

internal fun KotlinProjectionRenderer.nativeStructParameterMarshaler(
    parameterBinding: KotlinProjectionAbiParameterBinding,
): KotlinProjectionAbiMarshalerPlan? {
    customStructAbi(parameterBinding.typeBinding)?.let { customAbi ->
        val parameterName = parameterBinding.name
        val toAbiFunctionName = customAbi.toAbiFunctionName
        val abiArgumentKind = customAbi.abiArgumentKind
        if (toAbiFunctionName != null && abiArgumentKind != null) {
            return KotlinProjectionAbiMarshalerPlan(
                name = parameterName,
                typeBinding = parameterBinding.typeBinding,
                isReturn = false,
                abiArgumentExpression = CodeBlock.of("%T.%L(%L)", customAbi.helperTypeName, toAbiFunctionName, parameterName),
                abiArgumentKind = abiArgumentKind,
            )
        }
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
    descriptor: WinRTAbiMarshalerSlotDescriptor? = null,
): KotlinProjectionAbiMarshalerPlan? {
    val category = descriptor?.category ?: parameterBinding.category
    val elementBinding = parameterBinding.typeBinding.typeArguments.singleOrNull() ?: return null
    nonBlittableArrayElementMarshalerExpression(elementBinding)?.let { elementMarshaler ->
        return nonBlittableArrayParameterMarshaler(parameterBinding, category, elementMarshaler)
    }
    val elementSize = nativeArrayElementSizeExpression(elementBinding) ?: return null
    val parameterName = parameterBinding.name
    if (category == WinRTMetadataParameterCategory.ReceiveArray) {
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
        postCallStatements = if (category == WinRTMetadataParameterCategory.FillArray && elementRead != null) {
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
    category: WinRTMetadataParameterCategory,
    elementMarshaler: CodeBlock,
): KotlinProjectionAbiMarshalerPlan? {
    if (category == WinRTMetadataParameterCategory.ReceiveArray) {
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
        postCallStatements = if (category == WinRTMetadataParameterCategory.FillArray) {
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
        KotlinProjectionAbiValueKind.InspectableReference -> {
            val elementType = resolveTypeName(elementBinding.resolvedTypeName).copy(nullable = false)
            CodeBlock.of("%T.inspectableArray<%T>()", MARSHALER_CLASS_NAME, elementType)
        }
        KotlinProjectionAbiValueKind.ProjectedInterface -> {
            val interfaceId = elementBinding.interfaceId ?: return null
            val projectedType = resolveTypeName(elementBinding.resolvedTypeName).copy(nullable = false)
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
            val projectedType = resolveTypeName(elementBinding.resolvedTypeName).copy(nullable = false)
            CodeBlock.of("%T.inspectable(%T::class)", MARSHALER_CLASS_NAME, projectedType)
        }
        KotlinProjectionAbiValueKind.GenericParameter ->
            CodeBlock.of("%T.genericParameter<%T>()", MARSHALER_CLASS_NAME, resolveTypeName(elementBinding.typeName))
        KotlinProjectionAbiValueKind.MappedKeyValuePair -> {
            val keyAdapter = collectionReferenceAdapterCode(elementBinding.typeArguments.getOrNull(0) ?: return null) ?: return null
            val valueAdapter = collectionReferenceAdapterCode(elementBinding.typeArguments.getOrNull(1) ?: return null) ?: return null
            CodeBlock.of("%T.referenceValueAdapter(%M(%L, %L))", MARSHALER_CLASS_NAME, WINRT_KEY_VALUE_PAIR_ADAPTER_FUNCTION_NAME, keyAdapter, valueAdapter)
        }
        else -> null
    }

internal fun KotlinProjectionRenderer.arrayElementMarshalerExpression(
    elementBinding: KotlinProjectionAbiTypeBinding,
): CodeBlock? =
    nonBlittableArrayElementMarshalerExpression(elementBinding) ?: when (elementBinding.kind) {
        KotlinProjectionAbiValueKind.Boolean -> CodeBlock.of("%T.boolean()", MARSHALER_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int8 -> CodeBlock.of("%T.int8()", MARSHALER_CLASS_NAME)
        KotlinProjectionAbiValueKind.UInt8 -> CodeBlock.of("%T.uint8()", MARSHALER_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int16 -> CodeBlock.of("%T.int16()", MARSHALER_CLASS_NAME)
        KotlinProjectionAbiValueKind.UInt16 -> CodeBlock.of("%T.uint16()", MARSHALER_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int32 -> CodeBlock.of("%T.int32()", MARSHALER_CLASS_NAME)
        KotlinProjectionAbiValueKind.UInt32 -> CodeBlock.of("%T.uint32()", MARSHALER_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int64 -> CodeBlock.of("%T.int64()", MARSHALER_CLASS_NAME)
        KotlinProjectionAbiValueKind.UInt64 -> CodeBlock.of("%T.uint64()", MARSHALER_CLASS_NAME)
        KotlinProjectionAbiValueKind.Float -> CodeBlock.of("%T.float32()", MARSHALER_CLASS_NAME)
        KotlinProjectionAbiValueKind.Double -> CodeBlock.of("%T.float64()", MARSHALER_CLASS_NAME)
        KotlinProjectionAbiValueKind.Char16 -> CodeBlock.of("%T.char16()", MARSHALER_CLASS_NAME)
        KotlinProjectionAbiValueKind.GuidValue -> CodeBlock.of("%T.guid()", MARSHALER_CLASS_NAME)
        else -> null
    }
