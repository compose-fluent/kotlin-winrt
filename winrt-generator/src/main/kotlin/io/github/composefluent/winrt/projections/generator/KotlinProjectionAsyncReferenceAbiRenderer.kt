package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRtMetadataModel
import io.github.composefluent.winrt.metadata.WinRtAbiMarshalerPlanDescriptor
import io.github.composefluent.winrt.metadata.WinRtAbiMarshalerSlotDescriptor
import io.github.composefluent.winrt.metadata.WinRtCustomMappedMemberOutputDescriptor
import io.github.composefluent.winrt.metadata.WinRtEventDefinition
import io.github.composefluent.winrt.metadata.WinRtEventInvokeDescriptor
import io.github.composefluent.winrt.metadata.WinRtFactorySurfaceDescriptor
import io.github.composefluent.winrt.metadata.WinRtFieldDefinition
import io.github.composefluent.winrt.metadata.WinRtGenericAbiClassInitializationDescriptor
import io.github.composefluent.winrt.metadata.WinRtGenericAbiInventory
import io.github.composefluent.winrt.metadata.WinRtGenericInstantiationWriterDescriptor
import io.github.composefluent.winrt.metadata.WinRtGuidSignatureDescriptor
import io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition
import io.github.composefluent.winrt.metadata.WinRtInterfaceMemberSignatureSetDescriptor
import io.github.composefluent.winrt.metadata.WinRtIntegralType
import io.github.composefluent.winrt.metadata.WinRtMetadataProjectionContext
import io.github.composefluent.winrt.metadata.WinRtMetadataProjectionInventory
import io.github.composefluent.winrt.metadata.WinRtMetadataProjectionInventoryBuilder
import io.github.composefluent.winrt.metadata.WinRtMetadataParameterCategory
import io.github.composefluent.winrt.metadata.WinRtModuleActivationAndAuthoringDescriptor
import io.github.composefluent.winrt.metadata.WinRtMethodVtableDescriptor
import io.github.composefluent.winrt.metadata.WinRtMethodDefinition
import io.github.composefluent.winrt.metadata.WinRtNamespace
import io.github.composefluent.winrt.metadata.WinRtObjectReferenceSurfaceDescriptor
import io.github.composefluent.winrt.metadata.WinRtPropertyDefinition
import io.github.composefluent.winrt.metadata.WinRtRequiredInterfaceAugmentationDescriptor
import io.github.composefluent.winrt.metadata.WinRtSignatureWriterDescriptor
import io.github.composefluent.winrt.metadata.WinRtTypeDeclarationDescriptor
import io.github.composefluent.winrt.metadata.WinRtTypeDefinition
import io.github.composefluent.winrt.metadata.WinRtTypeRef
import io.github.composefluent.winrt.metadata.WinRtTypeKind
import io.github.composefluent.winrt.metadata.WinRtMetadataValidationOptions
import io.github.composefluent.winrt.metadata.WinRtMetadataSemanticHelpers
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
import io.github.composefluent.winrt.runtime.WinRtBindableIterableProjection
import io.github.composefluent.winrt.runtime.WinRtBindableVectorProjection
import io.github.composefluent.winrt.runtime.WinRtBindableVectorViewProjection
import io.github.composefluent.winrt.runtime.WinRtCollectionInterfaceIds
import io.github.composefluent.winrt.runtime.WinRtDictionaryProjection
import io.github.composefluent.winrt.runtime.WinRtIterableProjection
import io.github.composefluent.winrt.runtime.WinRtListProjection
import io.github.composefluent.winrt.runtime.WinRtAsyncActionReference
import io.github.composefluent.winrt.runtime.WinRtAsyncActionWithProgressReference
import io.github.composefluent.winrt.runtime.WinRtAsyncActionWithProgressVftblSlots
import io.github.composefluent.winrt.runtime.WinRtAsyncOperationReference
import io.github.composefluent.winrt.runtime.WinRtAsyncOperationWithProgressReference
import io.github.composefluent.winrt.runtime.WinRtAsyncOperationWithProgressVftblSlots
import io.github.composefluent.winrt.runtime.WinRtAsyncOperationVftblSlots
import io.github.composefluent.winrt.runtime.WinRtReadOnlyDictionaryProjection
import io.github.composefluent.winrt.runtime.WinRtReadOnlyListProjection
import io.github.composefluent.winrt.runtime.WinRtReferenceArrayProjection
import io.github.composefluent.winrt.runtime.WinRtReferenceProjection
import io.github.composefluent.winrt.runtime.WinRtReferenceValueAdapter
import io.github.composefluent.winrt.runtime.WinRtPlatformApi
import io.github.composefluent.winrt.runtime.WinRtTypeSignature
import io.github.composefluent.winrt.runtime.WinRtTypeHandle
import io.github.composefluent.winrt.runtime.WinRtUri
import io.github.composefluent.winrt.runtime.WinRtDelegateBridge
import io.github.composefluent.winrt.runtime.WinRtDelegateDescriptor
import io.github.composefluent.winrt.runtime.WinRtDelegateReference
import io.github.composefluent.winrt.runtime.WinRtDelegateValueKind
import io.github.composefluent.winrt.runtime.WinRtEvent
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

internal fun KotlinProjectionRenderer.asyncActionWithProgressReturnReadback(
    returnBinding: KotlinProjectionAbiTypeBinding,
): CodeBlock? {
    val expression = asyncReferenceExpression(
        returnBinding = returnBinding,
        pointerExpression = CodeBlock.of("%T.readPointer(__resultOut)", PLATFORM_ABI_CLASS_NAME),
    ) ?: return null
    return CodeBlock.of("return %L\n", expression)
}

internal fun KotlinProjectionRenderer.asyncOperationReturnReadback(
    returnBinding: KotlinProjectionAbiTypeBinding,
): CodeBlock? {
    val expression = asyncReferenceExpression(
        returnBinding = returnBinding,
        pointerExpression = CodeBlock.of("%T.readPointer(__resultOut)", PLATFORM_ABI_CLASS_NAME),
    ) ?: return null
    return CodeBlock.of("return %L\n", expression)
}

internal fun KotlinProjectionRenderer.asyncOperationWithProgressReturnReadback(
    returnBinding: KotlinProjectionAbiTypeBinding,
): CodeBlock? {
    val expression = asyncReferenceExpression(
        returnBinding = returnBinding,
        pointerExpression = CodeBlock.of("%T.readPointer(__resultOut)", PLATFORM_ABI_CLASS_NAME),
    ) ?: return null
    return CodeBlock.of("return %L\n", expression)
}

internal fun KotlinProjectionRenderer.asyncReferenceExpression(
    returnBinding: KotlinProjectionAbiTypeBinding,
    pointerExpression: CodeBlock,
): CodeBlock? =
    when (returnBinding.kind) {
        KotlinProjectionAbiValueKind.MappedAsyncAction ->
            CodeBlock.of("%T(%L)", WINRT_ASYNC_ACTION_REFERENCE_CLASS_NAME, pointerExpression)
        KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress ->
            asyncActionWithProgressExpression(returnBinding, pointerExpression)
        KotlinProjectionAbiValueKind.MappedAsyncOperation ->
            asyncOperationExpression(returnBinding, pointerExpression)
        KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress ->
            asyncOperationWithProgressExpression(returnBinding, pointerExpression)
        else -> null
    }

private fun KotlinProjectionRenderer.asyncActionWithProgressExpression(
    returnBinding: KotlinProjectionAbiTypeBinding,
    pointerExpression: CodeBlock,
): CodeBlock? {
    val progressBinding = returnBinding.typeArguments.singleOrNull() ?: return null
    val progressTypeSignature = asyncOperationResultTypeSignature(progressBinding) ?: return null
    return CodeBlock.builder()
        .add("%T.actionWithProgress<%T>(\n", WINRT_ASYNC_PROJECTION_INTEROP_CLASS_NAME, resolveTypeName(progressBinding.typeName))
        .indent()
        .add("pointer = %L,\n", pointerExpression)
        .add("progressSignature = %L,\n", progressTypeSignature)
        .unindent()
        .add(")")
        .build()
}

private fun KotlinProjectionRenderer.asyncOperationExpression(
    returnBinding: KotlinProjectionAbiTypeBinding,
    pointerExpression: CodeBlock,
): CodeBlock? {
    val resultBinding = returnBinding.typeArguments.singleOrNull() ?: return null
    val resultTypeSignature = asyncOperationResultTypeSignature(resultBinding) ?: return null
    val resultOutAllocation = abiResultAllocationForAsyncOperationResult(resultBinding, "__operationScope") ?: return null
    val resultReadbackExpression = asyncOperationResultReadbackExpression(resultBinding) ?: return null
    return CodeBlock.builder()
        .add("%T.operation<%T>(\n", WINRT_ASYNC_PROJECTION_INTEROP_CLASS_NAME, resolveTypeName(resultBinding.typeName))
        .indent()
        .add("pointer = %L,\n", pointerExpression)
        .add("resultSignature = %L,\n", resultTypeSignature)
        .add("resultOut = { __operationScope -> %L },\n", resultOutAllocation)
        .add("resultReader = { __operationResultOut ->\n")
        .indent()
        .add("%L\n", resultReadbackExpression)
        .unindent()
        .add("},\n")
        .unindent()
        .add(")")
        .build()
}

private fun KotlinProjectionRenderer.asyncOperationWithProgressExpression(
    returnBinding: KotlinProjectionAbiTypeBinding,
    pointerExpression: CodeBlock,
): CodeBlock? {
    val resultBinding = returnBinding.typeArguments.getOrNull(0) ?: return null
    val progressBinding = returnBinding.typeArguments.getOrNull(1) ?: return null
    val resultTypeSignature = asyncOperationResultTypeSignature(resultBinding) ?: return null
    val progressTypeSignature = asyncOperationResultTypeSignature(progressBinding) ?: return null
    val resultOutAllocation = abiResultAllocationForAsyncOperationResult(resultBinding, "__operationScope") ?: return null
    val resultReadbackExpression = asyncOperationResultReadbackExpression(resultBinding) ?: return null
    return CodeBlock.builder()
        .add(
            "%T.operationWithProgress<%T, %T>(\n",
            WINRT_ASYNC_PROJECTION_INTEROP_CLASS_NAME,
            resolveTypeName(resultBinding.typeName),
            resolveTypeName(progressBinding.typeName),
        )
        .indent()
        .add("pointer = %L,\n", pointerExpression)
        .add("resultSignature = %L,\n", resultTypeSignature)
        .add("progressSignature = %L,\n", progressTypeSignature)
        .add("resultOut = { __operationScope -> %L },\n", resultOutAllocation)
        .add("resultReader = { __operationResultOut ->\n")
        .indent()
        .add("%L\n", resultReadbackExpression)
        .unindent()
        .add("},\n")
        .unindent()
        .add(")")
        .build()
}

internal fun KotlinProjectionRenderer.asyncOperationResultTypeSignature(
    resultBinding: KotlinProjectionAbiTypeBinding,
): CodeBlock? = abiTypeSignature(resultBinding)

internal fun KotlinProjectionRenderer.referenceParameterMarshaler(
    parameterBinding: KotlinProjectionAbiParameterBinding,
    projectionClass: ClassName,
): KotlinProjectionAbiMarshalerPlan? {
    val interfaceId = referenceInterfaceIdCode(parameterBinding.typeBinding) ?: return null
    val parameterName = parameterBinding.name
    val abiLocalName = "__${parameterName}Abi"
    return KotlinProjectionAbiMarshalerPlan(
        name = parameterName,
        typeBinding = parameterBinding.typeBinding,
        isReturn = false,
        abiArgumentExpression = CodeBlock.of("%L?.abi ?: %T.nullPointer", abiLocalName, PLATFORM_ABI_CLASS_NAME),
        abiArgumentKind = KotlinProjectionComArgumentKind.Pointer,
        scopeOpeners = listOf(
            CodeBlock.of("%T.createMarshaler(%L, %L).use { %L ->", projectionClass, parameterName, interfaceId, abiLocalName),
        ),
    )
}

internal fun KotlinProjectionRenderer.referenceReadbackExpression(
    typeBinding: KotlinProjectionAbiTypeBinding,
    projectionClass: ClassName,
    resultOutName: String,
): CodeBlock? {
    val projectedType = resolveTypeName(typeBinding.typeName)
    val interfaceId = referenceInterfaceIdCode(typeBinding) ?: return null
    return CodeBlock.of(
        "%T.fromAbi(%T.readPointer(%L), %L) as %T",
        projectionClass,
        PLATFORM_ABI_CLASS_NAME,
        resultOutName,
        interfaceId,
        projectedType,
    )
}

internal fun KotlinProjectionRenderer.referenceReturnReadback(
    typeBinding: KotlinProjectionAbiTypeBinding,
    projectionClass: ClassName,
): CodeBlock? =
    referenceReadbackExpression(typeBinding, projectionClass, "__resultOut")
        ?.let { CodeBlock.of("return %L\n", it) }

internal fun KotlinProjectionRenderer.abiTypeSignature(
    binding: KotlinProjectionAbiTypeBinding,
): CodeBlock? = when (binding.kind) {
    KotlinProjectionAbiValueKind.MappedIterable ->
        binding.typeArguments.singleOrNull()?.let(::abiTypeSignature)
            ?.let { CodeBlock.of("%T.iterableSignature(%L)", WINRT_COLLECTION_INTERFACE_IDS_CLASS_NAME, it) }
    KotlinProjectionAbiValueKind.MappedVectorView ->
        binding.typeArguments.singleOrNull()?.let(::abiTypeSignature)
            ?.let { CodeBlock.of("%T.vectorViewSignature(%L)", WINRT_COLLECTION_INTERFACE_IDS_CLASS_NAME, it) }
    KotlinProjectionAbiValueKind.MappedVector ->
        binding.typeArguments.singleOrNull()?.let(::abiTypeSignature)
            ?.let { CodeBlock.of("%T.vectorSignature(%L)", WINRT_COLLECTION_INTERFACE_IDS_CLASS_NAME, it) }
    KotlinProjectionAbiValueKind.MappedMapView -> {
        val key = binding.typeArguments.getOrNull(0)?.let(::abiTypeSignature)
        val value = binding.typeArguments.getOrNull(1)?.let(::abiTypeSignature)
        if (key != null && value != null) CodeBlock.of("%T.mapViewSignature(%L, %L)", WINRT_COLLECTION_INTERFACE_IDS_CLASS_NAME, key, value) else null
    }
        KotlinProjectionAbiValueKind.MappedMap -> {
            val key = binding.typeArguments.getOrNull(0)?.let(::abiTypeSignature)
            val value = binding.typeArguments.getOrNull(1)?.let(::abiTypeSignature)
            if (key != null && value != null) CodeBlock.of("%T.mapSignature(%L, %L)", WINRT_COLLECTION_INTERFACE_IDS_CLASS_NAME, key, value) else null
        }
    KotlinProjectionAbiValueKind.Reference,
    KotlinProjectionAbiValueKind.ReferenceArray -> referenceTypeSignatureCode(binding)
    KotlinProjectionAbiValueKind.String -> CodeBlock.of("%T.string()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
    KotlinProjectionAbiValueKind.Boolean -> CodeBlock.of("%T.boolean()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
    KotlinProjectionAbiValueKind.Int8 -> CodeBlock.of("%T.int8()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
    KotlinProjectionAbiValueKind.UInt8 -> CodeBlock.of("%T.uint8()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
    KotlinProjectionAbiValueKind.Int16 -> CodeBlock.of("%T.int16()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
    KotlinProjectionAbiValueKind.UInt16 -> CodeBlock.of("%T.uint16()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
    KotlinProjectionAbiValueKind.Int32 -> CodeBlock.of("%T.int32()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
    KotlinProjectionAbiValueKind.UInt32 -> CodeBlock.of("%T.uint32()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
    KotlinProjectionAbiValueKind.Int64 -> CodeBlock.of("%T.int64()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
    KotlinProjectionAbiValueKind.UInt64 -> CodeBlock.of("%T.uint64()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
    KotlinProjectionAbiValueKind.Float -> CodeBlock.of("%T.float32()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
    KotlinProjectionAbiValueKind.Double -> CodeBlock.of("%T.float64()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
    KotlinProjectionAbiValueKind.Char16 -> CodeBlock.of("%T.char16()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
    KotlinProjectionAbiValueKind.GuidValue -> CodeBlock.of("%T.guidValue()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
    KotlinProjectionAbiValueKind.Enum ->
        resolvedReturnClassName(binding)?.let {
            CodeBlock.of("%T.enum(%S, %L)", WINRT_TYPE_SIGNATURE_CLASS_NAME, binding.resolvedTypeName, binding.enumUnderlyingType?.let(::abiTypeSignatureForIntegralType) ?: CodeBlock.of("%T.int32()", WINRT_TYPE_SIGNATURE_CLASS_NAME))
        }
    KotlinProjectionAbiValueKind.Struct ->
        nativeStructClassName(binding)?.let {
            CodeBlock.of("%T.struct(%S)", WINRT_TYPE_SIGNATURE_CLASS_NAME, binding.resolvedTypeName)
        }
    KotlinProjectionAbiValueKind.Object,
    KotlinProjectionAbiValueKind.InspectableReference -> CodeBlock.of("%T.object_()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
    KotlinProjectionAbiValueKind.ProjectedInterface ->
        resolvedReturnClassName(binding)?.let { resultType ->
            val arguments = binding.typeArguments.mapNotNull(::abiTypeSignature)
            if (arguments.size == binding.typeArguments.size && arguments.isNotEmpty()) {
                CodeBlock.builder()
                    .add("%T.parameterizedInterface(%T.Metadata.IID", WINRT_TYPE_SIGNATURE_CLASS_NAME, resultType)
                    .apply {
                        arguments.forEach { argument ->
                            add(", %L", argument)
                        }
                    }
                    .add(")")
                    .build()
            } else {
                CodeBlock.of("%T.guid(%T.Metadata.IID)", WINRT_TYPE_SIGNATURE_CLASS_NAME, resultType)
            }
        }
    KotlinProjectionAbiValueKind.ProjectedRuntimeClass ->
        resolvedReturnClassName(binding)?.let { resultType ->
            CodeBlock.of(
                "%T.runtimeClass(%S, %T.guid(%T.Metadata.DEFAULT_INTERFACE_IID))",
                WINRT_TYPE_SIGNATURE_CLASS_NAME,
                binding.resolvedTypeName,
                WINRT_TYPE_SIGNATURE_CLASS_NAME,
                resultType,
            )
        }
    else -> null
}

internal fun KotlinProjectionRenderer.referenceTypeSignatureCode(
    binding: KotlinProjectionAbiTypeBinding,
): CodeBlock? {
    val genericInterfaceId = binding.interfaceId ?: return null
    val elementSignature = binding.typeArguments.singleOrNull()?.let(::abiTypeSignature) ?: return null
    return CodeBlock.of(
        "%T.parameterizedInterface(%T(%S), %L)",
        WINRT_TYPE_SIGNATURE_CLASS_NAME,
        GUID_CLASS_NAME,
        genericInterfaceId.toString(),
        elementSignature,
    )
}

internal fun KotlinProjectionRenderer.referenceInterfaceIdCode(
    binding: KotlinProjectionAbiTypeBinding,
): CodeBlock? {
    val genericInterfaceId = binding.interfaceId ?: return null
    val elementSignature = binding.typeArguments.singleOrNull()?.let(::abiTypeSignature) ?: return null
    return CodeBlock.of(
        "%T.createFromParameterizedInterface(%T(%S), %L)",
        PARAMETERIZED_INTERFACE_ID_CLASS_NAME,
        GUID_CLASS_NAME,
        genericInterfaceId.toString(),
        elementSignature,
    )
}

internal fun KotlinProjectionRenderer.abiResultAllocationForAsyncOperationResult(
    resultBinding: KotlinProjectionAbiTypeBinding,
    scopeName: String,
): CodeBlock? = when {
    customObjectAbi(resultBinding) != null -> CodeBlock.of("%T.allocatePointerSlot(%L)", PLATFORM_ABI_CLASS_NAME, scopeName)
    else -> when (resultBinding.kind) {
    KotlinProjectionAbiValueKind.String -> CodeBlock.of("%T.allocatePointerSlot(%L)", PLATFORM_ABI_CLASS_NAME, scopeName)
    KotlinProjectionAbiValueKind.Boolean -> CodeBlock.of("%T.allocateInt8Slot(%L)", PLATFORM_ABI_CLASS_NAME, scopeName)
    KotlinProjectionAbiValueKind.Int8,
    KotlinProjectionAbiValueKind.UInt8 -> CodeBlock.of("%T.allocateInt8Slot(%L)", PLATFORM_ABI_CLASS_NAME, scopeName)
    KotlinProjectionAbiValueKind.Int16,
    KotlinProjectionAbiValueKind.UInt16 -> CodeBlock.of("%T.allocateBytes(%L, 2)", PLATFORM_ABI_CLASS_NAME, scopeName)
    KotlinProjectionAbiValueKind.Int32,
    KotlinProjectionAbiValueKind.UInt32 -> CodeBlock.of("%T.allocateInt32Slot(%L)", PLATFORM_ABI_CLASS_NAME, scopeName)
    KotlinProjectionAbiValueKind.Int64,
    KotlinProjectionAbiValueKind.UInt64 -> CodeBlock.of("%T.allocateInt64Slot(%L)", PLATFORM_ABI_CLASS_NAME, scopeName)
    KotlinProjectionAbiValueKind.Float -> CodeBlock.of("%T.allocateBytes(%L, 4)", PLATFORM_ABI_CLASS_NAME, scopeName)
    KotlinProjectionAbiValueKind.Double -> CodeBlock.of("%T.allocateDoubleSlot(%L)", PLATFORM_ABI_CLASS_NAME, scopeName)
    KotlinProjectionAbiValueKind.Char16 -> CodeBlock.of("%T.allocateBytes(%L, 2)", PLATFORM_ABI_CLASS_NAME, scopeName)
    KotlinProjectionAbiValueKind.GuidValue -> CodeBlock.of("%T.allocateBytes(%L, %T.BYTE_SIZE.toLong())", PLATFORM_ABI_CLASS_NAME, scopeName, GUID_CLASS_NAME)
    KotlinProjectionAbiValueKind.Enum -> bindingAllocationForAsyncEnum(resultBinding, scopeName)
    KotlinProjectionAbiValueKind.Struct ->
        nativeStructClassName(resultBinding)?.let { resultType ->
            CodeBlock.of("%T.allocateBytes(%L, %T.Metadata.layout.sizeBytes)", PLATFORM_ABI_CLASS_NAME, scopeName, resultType)
        }
    KotlinProjectionAbiValueKind.MappedIterable,
    KotlinProjectionAbiValueKind.MappedVector,
    KotlinProjectionAbiValueKind.MappedMap,
    KotlinProjectionAbiValueKind.MappedVectorView,
    KotlinProjectionAbiValueKind.MappedMapView -> CodeBlock.of("%T.allocatePointerSlot(%L)", PLATFORM_ABI_CLASS_NAME, scopeName)
    KotlinProjectionAbiValueKind.ProjectedInterface,
    KotlinProjectionAbiValueKind.ProjectedRuntimeClass,
    KotlinProjectionAbiValueKind.Object,
    KotlinProjectionAbiValueKind.UnknownReference,
    KotlinProjectionAbiValueKind.InspectableReference,
    KotlinProjectionAbiValueKind.Reference,
    KotlinProjectionAbiValueKind.ReferenceArray -> CodeBlock.of("%T.allocatePointerSlot(%L)", PLATFORM_ABI_CLASS_NAME, scopeName)
    else -> null
    }
}

internal fun KotlinProjectionRenderer.bindingAllocationForAsyncEnum(
    resultBinding: KotlinProjectionAbiTypeBinding,
    scopeName: String,
): CodeBlock? = when (resultBinding.enumUnderlyingType) {
    WinRtIntegralType.Int8,
    WinRtIntegralType.UInt8 -> CodeBlock.of("%T.allocateInt8Slot(%L)", PLATFORM_ABI_CLASS_NAME, scopeName)
    WinRtIntegralType.Int16,
    WinRtIntegralType.UInt16 -> CodeBlock.of("%T.allocateBytes(%L, 2)", PLATFORM_ABI_CLASS_NAME, scopeName)
    WinRtIntegralType.Int32,
    WinRtIntegralType.UInt32 -> CodeBlock.of("%T.allocateInt32Slot(%L)", PLATFORM_ABI_CLASS_NAME, scopeName)
    WinRtIntegralType.Int64,
    WinRtIntegralType.UInt64 -> CodeBlock.of("%T.allocateInt64Slot(%L)", PLATFORM_ABI_CLASS_NAME, scopeName)
    null -> null
}

internal fun KotlinProjectionRenderer.abiTypeSignatureForIntegralType(type: WinRtIntegralType): CodeBlock = when (type) {
    WinRtIntegralType.Int8 -> CodeBlock.of("%T.int8()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
    WinRtIntegralType.UInt8 -> CodeBlock.of("%T.uint8()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
    WinRtIntegralType.Int16 -> CodeBlock.of("%T.int16()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
    WinRtIntegralType.UInt16 -> CodeBlock.of("%T.uint16()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
    WinRtIntegralType.Int32 -> CodeBlock.of("%T.int32()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
    WinRtIntegralType.UInt32 -> CodeBlock.of("%T.uint32()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
    WinRtIntegralType.Int64 -> CodeBlock.of("%T.int64()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
    WinRtIntegralType.UInt64 -> CodeBlock.of("%T.uint64()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
}

internal fun KotlinProjectionRenderer.asyncOperationResultReadbackExpression(
    resultBinding: KotlinProjectionAbiTypeBinding,
): CodeBlock? = customObjectAsyncOperationResultReadbackExpression(resultBinding) ?: when (resultBinding.kind) {
    KotlinProjectionAbiValueKind.String ->
        CodeBlock.of(
            "run {\nval __operationResultString = %T.fromHandle(%T.readPointer(__operationResultOut), owner = true)\n__operationResultString.use { value -> value.toKString() }\n}",
            HSTRING_CLASS_NAME,
            PLATFORM_ABI_CLASS_NAME,
        )
    KotlinProjectionAbiValueKind.Boolean ->
        CodeBlock.of("%T.readInt8(__operationResultOut).toInt() != 0", PLATFORM_ABI_CLASS_NAME)
    KotlinProjectionAbiValueKind.Int8 ->
        CodeBlock.of("%T.readInt8(__operationResultOut)", PLATFORM_ABI_CLASS_NAME)
    KotlinProjectionAbiValueKind.UInt8 ->
        CodeBlock.of("%T.readInt8(__operationResultOut).toUByte()", PLATFORM_ABI_CLASS_NAME)
    KotlinProjectionAbiValueKind.Int16 ->
        CodeBlock.of("%T.readInt16(__operationResultOut)", PLATFORM_ABI_CLASS_NAME)
    KotlinProjectionAbiValueKind.UInt16 ->
        CodeBlock.of("%T.readInt16(__operationResultOut).toUShort()", PLATFORM_ABI_CLASS_NAME)
    KotlinProjectionAbiValueKind.Int32 ->
        CodeBlock.of("%T.readInt32(__operationResultOut)", PLATFORM_ABI_CLASS_NAME)
    KotlinProjectionAbiValueKind.UInt32 ->
        CodeBlock.of("%T.readInt32(__operationResultOut).toUInt()", PLATFORM_ABI_CLASS_NAME)
    KotlinProjectionAbiValueKind.Int64 ->
        CodeBlock.of("%T.readInt64(__operationResultOut)", PLATFORM_ABI_CLASS_NAME)
    KotlinProjectionAbiValueKind.UInt64 ->
        CodeBlock.of("%T.readInt64(__operationResultOut).toULong()", PLATFORM_ABI_CLASS_NAME)
    KotlinProjectionAbiValueKind.Float ->
        CodeBlock.of("%T.readFloat(__operationResultOut)", PLATFORM_ABI_CLASS_NAME)
    KotlinProjectionAbiValueKind.Double ->
        CodeBlock.of("%T.readDouble(__operationResultOut)", PLATFORM_ABI_CLASS_NAME)
    KotlinProjectionAbiValueKind.Char16 ->
        CodeBlock.of("%T.readChar16(__operationResultOut)", PLATFORM_ABI_CLASS_NAME)
    KotlinProjectionAbiValueKind.GuidValue ->
        CodeBlock.of("%T.readGuid(__operationResultOut)", PLATFORM_ABI_CLASS_NAME)
    KotlinProjectionAbiValueKind.Enum ->
        asyncEnumResultReadbackExpression(resultBinding)
    KotlinProjectionAbiValueKind.Struct ->
        nativeStructClassName(resultBinding)?.let { resultType ->
            CodeBlock.of("%T.Metadata.fromAbi(__operationResultOut)", resultType)
        }
    KotlinProjectionAbiValueKind.MappedIterable,
    KotlinProjectionAbiValueKind.MappedVectorView,
    KotlinProjectionAbiValueKind.MappedMapView,
    KotlinProjectionAbiValueKind.MappedVector,
    KotlinProjectionAbiValueKind.MappedMap ->
        asyncMappedCollectionResultReadbackExpression(resultBinding)
    KotlinProjectionAbiValueKind.Reference ->
        referenceReadbackExpression(resultBinding, WINRT_REFERENCE_PROJECTION_CLASS_NAME, "__operationResultOut")
    KotlinProjectionAbiValueKind.ReferenceArray ->
        referenceReadbackExpression(resultBinding, WINRT_REFERENCE_ARRAY_PROJECTION_CLASS_NAME, "__operationResultOut")
    KotlinProjectionAbiValueKind.Object,
    KotlinProjectionAbiValueKind.InspectableReference ->
        CodeBlock.of(
            "run {\nval __operationResultRef = %T(%T.toRawComPtr(%T.readPointer(__operationResultOut)))\nval __operationInspectable = __operationResultRef.asInspectable()\n__operationResultRef.close()\n__operationInspectable\n}",
            IUNKNOWN_REFERENCE_CLASS_NAME,
            PLATFORM_ABI_CLASS_NAME,
            PLATFORM_ABI_CLASS_NAME,
        )
    KotlinProjectionAbiValueKind.UnknownReference ->
        CodeBlock.of(
            "%T(%T.toRawComPtr(%T.readPointer(__operationResultOut)))",
            IUNKNOWN_REFERENCE_CLASS_NAME,
            PLATFORM_ABI_CLASS_NAME,
            PLATFORM_ABI_CLASS_NAME,
        )
    KotlinProjectionAbiValueKind.ProjectedInterface ->
        resolvedReturnClassName(resultBinding)?.let { resultType ->
            CodeBlock.of(
                "%T.Metadata.wrap(%T(%T.toRawComPtr(%T.readPointer(__operationResultOut))))",
                resultType,
                IUNKNOWN_REFERENCE_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
            )
        }
    KotlinProjectionAbiValueKind.ProjectedRuntimeClass ->
        resolvedReturnClassName(resultBinding)?.let { resultType ->
            CodeBlock.of(
                "%T.Metadata.wrap(%T(%T.toRawComPtr(%T.readPointer(__operationResultOut))).asInspectable())",
                resultType,
                IUNKNOWN_REFERENCE_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
            )
        }
    else -> null
}

internal fun KotlinProjectionRenderer.customObjectAsyncOperationResultReadbackExpression(
    resultBinding: KotlinProjectionAbiTypeBinding,
): CodeBlock? {
    val customAbi = customObjectAbi(resultBinding) ?: return null
    val projectedType = resolveTypeName(resultBinding.resolvedTypeName)
    val nullReadback = if (resultBinding.isNullableAbiReturn) {
        CodeBlock.of("if (%T.isNull(__operationResultPointer)) null else ", PLATFORM_ABI_CLASS_NAME)
    } else {
        CodeBlock.of(
            "if (%T.isNull(__operationResultPointer)) error(%S) else ",
            PLATFORM_ABI_CLASS_NAME,
            "WINRT_E_NULL_ASYNC_ABI_RESULT",
        )
    }
    val readback = if (customAbi.fromAbiFunctionName == "objectFromAbi") {
        CodeBlock.of(
            "%T.%L(__operationResultPointer, %T(%S, %T(%S)), %T::class) ?: error(%S)",
            WINRT_SYSTEM_PROJECTION_MARSHALERS_CLASS_NAME,
            customAbi.fromAbiFunctionName,
            WINRT_TYPE_HANDLE_CLASS_NAME,
            customAbi.typeHandleName,
            GUID_CLASS_NAME,
            customAbi.interfaceId.toString(),
            projectedType,
            "WINRT_E_NULL_ASYNC_PROJECTED_RESULT",
        )
    } else {
        CodeBlock.of(
            "%T.%L(__operationResultPointer) ?: error(%S)",
            WINRT_SYSTEM_PROJECTION_MARSHALERS_CLASS_NAME,
            customAbi.fromAbiFunctionName,
            "WINRT_E_NULL_ASYNC_PROJECTED_RESULT",
        )
    }
    return CodeBlock.of(
        "run {\nval __operationResultPointer = %T.readPointer(__operationResultOut)\n%L%L\n}",
        PLATFORM_ABI_CLASS_NAME,
        nullReadback,
        readback,
    )
}

internal fun KotlinProjectionRenderer.asyncMappedCollectionResultReadbackExpression(
    resultBinding: KotlinProjectionAbiTypeBinding,
): CodeBlock? {
    readOnlyCollectionBindingForReturn(resultBinding)?.let { binding ->
        return CodeBlock.of(
            "run {\nval __collectionRef = %T(%T.toRawComPtr(%T.readPointer(__operationResultOut)))\n%L}\n",
            IUNKNOWN_REFERENCE_CLASS_NAME,
            PLATFORM_ABI_CLASS_NAME,
            PLATFORM_ABI_CLASS_NAME,
            renderReadOnlyCollectionDelegateInitializer(binding),
        )
    }
    mutableCollectionBindingForReturn(resultBinding)?.let { binding ->
        return CodeBlock.of(
            "run {\nval __collectionRef = %T(%T.toRawComPtr(%T.readPointer(__operationResultOut)))\n%L}\n",
            IUNKNOWN_REFERENCE_CLASS_NAME,
            PLATFORM_ABI_CLASS_NAME,
            PLATFORM_ABI_CLASS_NAME,
            renderMutableCollectionDelegateInitializer(binding),
        )
    }
    return null
}

internal fun KotlinProjectionRenderer.asyncEnumResultReadbackExpression(
    resultBinding: KotlinProjectionAbiTypeBinding,
): CodeBlock? {
    val enumType = resolvedReturnClassName(resultBinding) ?: return null
    val readback = when (resultBinding.enumUnderlyingType ?: return null) {
        WinRtIntegralType.Int8 -> CodeBlock.of("%T.readInt8(__operationResultOut)", PLATFORM_ABI_CLASS_NAME)
        WinRtIntegralType.UInt8 -> CodeBlock.of("%T.readInt8(__operationResultOut).toUByte()", PLATFORM_ABI_CLASS_NAME)
        WinRtIntegralType.Int16 -> CodeBlock.of("%T.readInt16(__operationResultOut)", PLATFORM_ABI_CLASS_NAME)
        WinRtIntegralType.UInt16 -> CodeBlock.of("%T.readInt16(__operationResultOut).toUShort()", PLATFORM_ABI_CLASS_NAME)
        WinRtIntegralType.Int32 -> CodeBlock.of("%T.readInt32(__operationResultOut)", PLATFORM_ABI_CLASS_NAME)
        WinRtIntegralType.UInt32 -> CodeBlock.of("%T.readInt32(__operationResultOut).toUInt()", PLATFORM_ABI_CLASS_NAME)
        WinRtIntegralType.Int64 -> CodeBlock.of("%T.readInt64(__operationResultOut)", PLATFORM_ABI_CLASS_NAME)
        WinRtIntegralType.UInt64 -> CodeBlock.of("%T.readInt64(__operationResultOut).toULong()", PLATFORM_ABI_CLASS_NAME)
    }
    return CodeBlock.of("%T.Metadata.fromAbi(%L)", enumType, readback)
}

internal fun KotlinProjectionRenderer.mappedCollectionReturnReadback(
    returnBinding: KotlinProjectionAbiTypeBinding,
): CodeBlock? {
    readOnlyCollectionBindingForReturn(returnBinding)?.let { binding ->
        renderStringReadOnlyListReturnReadback(binding)?.let { return it }
        renderRuntimeReadOnlyCollectionReturnReadback(binding)?.let { return it }
        return CodeBlock.of(
            "val __collectionRef = %T(%T.toRawComPtr(%T.readPointer(__resultOut)))\nreturn %L\n",
            IUNKNOWN_REFERENCE_CLASS_NAME,
            PLATFORM_ABI_CLASS_NAME,
            PLATFORM_ABI_CLASS_NAME,
            renderReadOnlyCollectionDelegateInitializer(binding),
        )
    }
    mutableCollectionBindingForReturn(returnBinding)?.let { binding ->
        renderStringMutableListReturnReadback(binding)?.let { return it }
        renderRuntimeMutableCollectionReturnReadback(binding)?.let { return it }
        return CodeBlock.of(
            "val __collectionRef = %T(%T.toRawComPtr(%T.readPointer(__resultOut)))\nreturn %L\n",
            IUNKNOWN_REFERENCE_CLASS_NAME,
            PLATFORM_ABI_CLASS_NAME,
            PLATFORM_ABI_CLASS_NAME,
            renderMutableCollectionDelegateInitializer(binding),
        )
    }
    return null
}

private fun KotlinProjectionRenderer.renderRuntimeMutableCollectionReturnReadback(
    binding: KotlinProjectionMutableCollectionBinding,
): CodeBlock? =
    when (binding.kind) {
        KotlinProjectionMutableCollectionKind.Vector -> {
            val elementAdapter = collectionReferenceAdapterCode(requireNotNull(binding.elementBinding)) ?: return null
            CodeBlock.of(
                "val __collectionPointer = %T.readPointer(__resultOut)\nval __collection = %T.fromAbi(__collectionPointer, %L)\nreturn __collection ?: error(%S)\n",
                PLATFORM_ABI_CLASS_NAME,
                WINRT_LIST_PROJECTION_CLASS_NAME,
                elementAdapter,
                "WINRT_E_NULL_ABI_RETURN",
            )
        }
        KotlinProjectionMutableCollectionKind.Map -> {
            val keyAdapter = collectionReferenceAdapterCode(requireNotNull(binding.keyBinding)) ?: return null
            val valueAdapter = collectionReferenceAdapterCode(requireNotNull(binding.valueBinding)) ?: return null
            CodeBlock.of(
                "val __collectionPointer = %T.readPointer(__resultOut)\nval __collection = %T.fromAbi(__collectionPointer, %L, %L)\nreturn __collection ?: error(%S)\n",
                PLATFORM_ABI_CLASS_NAME,
                WINRT_DICTIONARY_PROJECTION_CLASS_NAME,
                keyAdapter,
                valueAdapter,
                "WINRT_E_NULL_ABI_RETURN",
            )
        }
    }

private fun KotlinProjectionRenderer.renderRuntimeReadOnlyCollectionReturnReadback(
    binding: KotlinProjectionReadOnlyCollectionBinding,
): CodeBlock? =
    when (binding.kind) {
        KotlinProjectionReadOnlyCollectionKind.Iterable -> {
            val elementAdapter = collectionReferenceAdapterCode(requireNotNull(binding.elementBinding)) ?: return null
            CodeBlock.of(
                "val __collectionPointer = %T.readPointer(__resultOut)\nval __collection = %T.fromAbi(__collectionPointer, %L)\nreturn __collection ?: error(%S)\n",
                PLATFORM_ABI_CLASS_NAME,
                WINRT_ITERABLE_PROJECTION_CLASS_NAME,
                elementAdapter,
                "WINRT_E_NULL_ABI_RETURN",
            )
        }
        KotlinProjectionReadOnlyCollectionKind.VectorView -> {
            val elementAdapter = collectionReferenceAdapterCode(requireNotNull(binding.elementBinding)) ?: return null
            CodeBlock.of(
                "val __collectionPointer = %T.readPointer(__resultOut)\nval __collection = %T.fromAbi(__collectionPointer, %L)\nreturn __collection ?: error(%S)\n",
                PLATFORM_ABI_CLASS_NAME,
                WINRT_READ_ONLY_LIST_PROJECTION_CLASS_NAME,
                elementAdapter,
                "WINRT_E_NULL_ABI_RETURN",
            )
        }
        KotlinProjectionReadOnlyCollectionKind.MapView -> {
            val keyAdapter = collectionReferenceAdapterCode(requireNotNull(binding.keyBinding)) ?: return null
            val valueAdapter = collectionReferenceAdapterCode(requireNotNull(binding.valueBinding)) ?: return null
            CodeBlock.of(
                "val __collectionPointer = %T.readPointer(__resultOut)\nval __collection = %T.fromAbi(__collectionPointer, %L, %L)\nreturn __collection ?: error(%S)\n",
                PLATFORM_ABI_CLASS_NAME,
                WINRT_READ_ONLY_DICTIONARY_PROJECTION_CLASS_NAME,
                keyAdapter,
                valueAdapter,
                "WINRT_E_NULL_ABI_RETURN",
            )
        }
    }

private fun renderStringMutableListReturnReadback(
    binding: KotlinProjectionMutableCollectionBinding,
): CodeBlock? {
    if (
        binding.kind != KotlinProjectionMutableCollectionKind.Vector ||
        binding.elementBinding?.kind != KotlinProjectionAbiValueKind.String
    ) {
        return null
    }
    return CodeBlock.of(
        "val __collectionPointer = %T.readPointer(__resultOut)\nreturn %T.fromAbi(__collectionPointer, %T.string) ?: error(%S)\n",
        PLATFORM_ABI_CLASS_NAME,
        WINRT_LIST_PROJECTION_CLASS_NAME,
        WINRT_REFERENCE_VALUE_ADAPTERS_CLASS_NAME,
        "WINRT_E_NULL_ABI_RETURN",
    )
}

private fun renderStringReadOnlyListReturnReadback(
    binding: KotlinProjectionReadOnlyCollectionBinding,
): CodeBlock? {
    if (
        binding.kind != KotlinProjectionReadOnlyCollectionKind.VectorView ||
        binding.elementBinding?.kind != KotlinProjectionAbiValueKind.String
    ) {
        return null
    }
    return CodeBlock.of(
        "val __collectionPointer = %T.readPointer(__resultOut)\nreturn %T.fromAbi(__collectionPointer, %T.string) ?: error(%S)\n",
        PLATFORM_ABI_CLASS_NAME,
        WINRT_READ_ONLY_LIST_PROJECTION_CLASS_NAME,
        WINRT_REFERENCE_VALUE_ADAPTERS_CLASS_NAME,
        "WINRT_E_NULL_ABI_RETURN",
    )
}

internal fun KotlinProjectionRenderer.observableVectorReturnReadback(
    returnBinding: KotlinProjectionAbiTypeBinding,
): CodeBlock? {
    if (returnBinding.resolvedTypeName != "Windows.Foundation.Collections.IObservableVector") {
        return null
    }
    val elementBinding = returnBinding.typeArguments.singleOrNull() ?: return null
    val binding = createMutableCollectionBindingPlan(
        collectionKind = KotlinProjectionMutableCollectionKind.Vector,
        ownerInterfaceQualifiedName = returnBinding.typeName,
        ownerCachePropertyName = "__collectionRef",
        slotInterfaceQualifiedName = "Windows.Foundation.Collections.IVector",
        delegatePropertyName = "__observableVectorList",
        typeArguments = listOf(elementBinding),
        errorContext = returnBinding.typeName,
        requireSupportedBinding = true,
        bindingLocationLabel = "return",
    ) ?: return null
    val elementType = resolveTypeName(elementBinding.typeName)
    val observableVectorType = resolveTypeName(returnBinding.typeName)
    val vectorChangedHandlerType = projectionClassName("Windows.Foundation.Collections.VectorChangedEventHandler")
        .parameterizedBy(elementType)
    val vectorChangedEventType = WINRT_EVENT_CLASS_NAME.parameterizedBy(vectorChangedHandlerType)
    val eventSourceType = WINRT_EVENT_SOURCE_CLASS_NAME.parameterizedBy(vectorChangedHandlerType)
    return CodeBlock.of(
        """
        val __collectionRef = %T(%T.toRawComPtr(%T.readPointer(__resultOut)))
        val __observableVectorList = %L
        return object : %T, %T by __observableVectorList, %T {
            override val nativeObject: %T
                get() = __collectionRef

            override val vectorChanged: %T by lazy(%T.PUBLICATION) {
                val __eventSource = %T.%L(__collectionRef, %T.Metadata.VECTORCHANGED_ADD_SLOT) as? %T
                __eventSource?.let { %T(it) } ?: error(%S)
            }

            override fun addVectorChanged(handler: %T): %T =
                vectorChanged.add(handler)

            override fun removeVectorChanged(token: %T) {
                vectorChanged.remove(token)
            }
        }
        """.trimIndent() + "\n",
        IUNKNOWN_REFERENCE_CLASS_NAME,
        PLATFORM_ABI_CLASS_NAME,
        PLATFORM_ABI_CLASS_NAME,
        renderMutableCollectionDelegateInitializer(binding),
        observableVectorType,
        MUTABLE_LIST_CLASS_NAME.parameterizedBy(elementType),
        IWINRT_OBJECT_CLASS_NAME,
        COM_OBJECT_REFERENCE_CLASS_NAME,
        vectorChangedEventType,
        LAZY_THREAD_SAFETY_MODE_CLASS_NAME,
        ClassName(WINRT_EVENT_PROJECTION_HELPERS_CLASS_NAME.packageName, eventSourceOwnerHelperName("Windows.Foundation.Collections.IObservableVector")),
        eventSourceCreateFunctionName(
            eventType = "Windows.Foundation.Collections.VectorChangedEventHandler<${elementBinding.resolvedTypeName}>",
            ownerType = "Windows.Foundation.Collections.IObservableVector",
        ),
        projectionClassName("Windows.Foundation.Collections.IObservableVector"),
        eventSourceType,
        WINRT_EVENT_CLASS_NAME,
        "Event source VectorChanged is not registered.",
        vectorChangedHandlerType,
        EVENT_REGISTRATION_TOKEN_CLASS_NAME,
        EVENT_REGISTRATION_TOKEN_CLASS_NAME,
    )
}

internal fun KotlinProjectionRenderer.readOnlyCollectionBindingForReturn(
    returnBinding: KotlinProjectionAbiTypeBinding,
): KotlinProjectionReadOnlyCollectionBinding? {
    val mappedType = mappedTypeByAbiKind(returnBinding.kind) ?: return null
    val collectionKind = mappedType.readOnlyCollectionKind ?: return null
    return createReadOnlyCollectionBindingPlan(
        collectionKind = collectionKind,
        ownerInterfaceQualifiedName = returnBinding.typeName,
        ownerCachePropertyName = "__collectionRef",
        slotInterfaceQualifiedName = returnBinding.resolvedTypeName,
        delegatePropertyName = collectionKind.returnDelegatePropertyName(),
        typeArguments = returnBinding.typeArguments,
        errorContext = returnBinding.typeName,
        requireSupportedBinding = true,
        bindingLocationLabel = "return",
    )
}

internal fun KotlinProjectionRenderer.mutableCollectionBindingForReturn(
    returnBinding: KotlinProjectionAbiTypeBinding,
): KotlinProjectionMutableCollectionBinding? {
    val mappedType = mappedTypeByAbiKind(returnBinding.kind) ?: return null
    val collectionKind = mappedType.mutableCollectionKind ?: return null
    return createMutableCollectionBindingPlan(
        collectionKind = collectionKind,
        ownerInterfaceQualifiedName = returnBinding.typeName,
        ownerCachePropertyName = "__collectionRef",
        slotInterfaceQualifiedName = returnBinding.resolvedTypeName,
        delegatePropertyName = collectionKind.returnDelegatePropertyName(),
        typeArguments = returnBinding.typeArguments,
        errorContext = returnBinding.typeName,
        requireSupportedBinding = true,
        bindingLocationLabel = "return",
    )
}

internal fun KotlinProjectionRenderer.bindableCollectionParameterMarshaler(
    parameterBinding: KotlinProjectionAbiParameterBinding,
): KotlinProjectionAbiMarshalerPlan? {
    val projectionClass = when (parameterBinding.typeBinding.kind) {
        KotlinProjectionAbiValueKind.MappedBindableIterable -> WINRT_BINDABLE_ITERABLE_PROJECTION_CLASS_NAME
        KotlinProjectionAbiValueKind.MappedBindableVector -> WINRT_BINDABLE_VECTOR_PROJECTION_CLASS_NAME
        KotlinProjectionAbiValueKind.MappedBindableVectorView -> WINRT_BINDABLE_VECTOR_VIEW_PROJECTION_CLASS_NAME
        else -> return null
    }
    val parameterName = parameterBinding.name
    val abiLocalName = "__${parameterName}Abi"
    val marshalerLocalName = "__${parameterName}Marshaler"
    return KotlinProjectionAbiMarshalerPlan(
        name = parameterName,
        typeBinding = parameterBinding.typeBinding,
        isReturn = false,
        abiArgumentExpression = CodeBlock.of("%L.abi", abiLocalName),
        abiArgumentKind = KotlinProjectionComArgumentKind.Pointer,
        scopeOpeners = listOf(
            CodeBlock.of(
                "val %L = %T.createMarshaler(%L) ?: error(%S)\n%L.use { %L ->",
                marshalerLocalName,
                projectionClass,
                parameterName,
                "Unable to marshal WinRT bindable collection parameter $parameterName.",
                marshalerLocalName,
                abiLocalName,
            ),
        ),
    )
}

internal fun KotlinProjectionRenderer.mappedCollectionParameterMarshaler(
    parameterBinding: KotlinProjectionAbiParameterBinding,
): KotlinProjectionAbiMarshalerPlan? {
    val parameterName = parameterBinding.name
    val abiLocalName = "__${parameterName}Abi"
    val marshalerLocalName = "__${parameterName}Marshaler"
    val projectionClass = when (parameterBinding.typeBinding.kind) {
        KotlinProjectionAbiValueKind.MappedIterable -> WINRT_ITERABLE_PROJECTION_CLASS_NAME
        KotlinProjectionAbiValueKind.MappedVector -> WINRT_LIST_PROJECTION_CLASS_NAME
        KotlinProjectionAbiValueKind.MappedVectorView -> WINRT_READ_ONLY_LIST_PROJECTION_CLASS_NAME
        KotlinProjectionAbiValueKind.MappedMap -> WINRT_DICTIONARY_PROJECTION_CLASS_NAME
        KotlinProjectionAbiValueKind.MappedMapView -> WINRT_READ_ONLY_DICTIONARY_PROJECTION_CLASS_NAME
        else -> return null
    }
    val typeArguments = parameterBinding.typeBinding.typeArguments
    val adapterArguments = when (parameterBinding.typeBinding.kind) {
        KotlinProjectionAbiValueKind.MappedIterable,
        KotlinProjectionAbiValueKind.MappedVector,
        KotlinProjectionAbiValueKind.MappedVectorView ->
            listOf(collectionReferenceAdapterCode(typeArguments.singleOrNull() ?: return null))
        KotlinProjectionAbiValueKind.MappedMap,
        KotlinProjectionAbiValueKind.MappedMapView ->
            listOf(
                collectionReferenceAdapterCode(typeArguments.getOrNull(0) ?: return null),
                collectionReferenceAdapterCode(typeArguments.getOrNull(1) ?: return null),
            )
        else -> return null
    }
    return KotlinProjectionAbiMarshalerPlan(
        name = parameterName,
        typeBinding = parameterBinding.typeBinding,
        isReturn = false,
        abiArgumentExpression = CodeBlock.of("%L.abi", abiLocalName),
        abiArgumentKind = KotlinProjectionComArgumentKind.Pointer,
        scopeOpeners = listOf(
            CodeBlock.builder()
                .add("val %L = %T.createMarshaler(%L", marshalerLocalName, projectionClass, parameterName)
                .apply { adapterArguments.forEach { add(", %L", it) } }
                .add(") ?: error(%S)\n", "Unable to marshal WinRT collection parameter $parameterName.")
                .add("%L.use { %L ->", marshalerLocalName, abiLocalName)
                .build(),
        ),
    )
}

internal fun KotlinProjectionRenderer.collectionReferenceAdapterCode(
    typeBinding: KotlinProjectionAbiTypeBinding,
): CodeBlock? {
    if (typeBinding.kind == KotlinProjectionAbiValueKind.String) {
        return CodeBlock.of("%T.string", WINRT_REFERENCE_VALUE_ADAPTERS_CLASS_NAME)
    }
    if (typeBinding.kind == KotlinProjectionAbiValueKind.Object) {
        return CodeBlock.of("%T.object_", WINRT_REFERENCE_VALUE_ADAPTERS_CLASS_NAME)
    }
    if (typeBinding.kind == KotlinProjectionAbiValueKind.InspectableReference) {
        return CodeBlock.of("%T.inspectable", WINRT_REFERENCE_VALUE_ADAPTERS_CLASS_NAME)
    }
    if (typeBinding.kind == KotlinProjectionAbiValueKind.MappedKeyValuePair && typeBinding.typeArguments.size == 2) {
        val keyAdapter = collectionReferenceAdapterCode(typeBinding.typeArguments[0]) ?: return null
        val valueAdapter = collectionReferenceAdapterCode(typeBinding.typeArguments[1]) ?: return null
        return CodeBlock.of("%M(%L, %L)", WINRT_KEY_VALUE_PAIR_ADAPTER_FUNCTION_NAME, keyAdapter, valueAdapter)
    }
    if (typeBinding.kind == KotlinProjectionAbiValueKind.Struct) {
        val projectedType = nativeStructClassName(typeBinding) ?: return null
        val typeSignature = abiTypeSignature(typeBinding) ?: return null
        return CodeBlock.of(
            "%T.valueType(%T::class, %S, %L)",
            WINRT_REFERENCE_VALUE_ADAPTERS_CLASS_NAME,
            projectedType,
            typeBinding.resolvedTypeName,
            typeSignature,
        )
    }
    if (typeBinding.kind == KotlinProjectionAbiValueKind.GenericParameter) {
        val projectedType = resolveTypeName(typeBinding.typeName)
        return CodeBlock.of(
            "%T.genericParameter<%T>(%S)",
            WINRT_REFERENCE_VALUE_ADAPTERS_CLASS_NAME,
            projectedType,
            typeBinding.typeName,
        )
    }
    when (typeBinding.kind) {
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass,
        KotlinProjectionAbiValueKind.ProjectedInterface,
        KotlinProjectionAbiValueKind.UnknownReference,
        KotlinProjectionAbiValueKind.InspectableReference -> Unit
        else -> return null
    }
    val projectedType = resolveTypeName(typeBinding.resolvedTypeName)
    val projectedTypeName = typeBinding.resolvedTypeName
    if (typeBinding.kind == KotlinProjectionAbiValueKind.ProjectedRuntimeClass) {
        return CodeBlock.of(
            "%T.runtimeClass(%T::class, %S, %T.Metadata.DEFAULT_INTERFACE_IID) { %T.Metadata.wrap(it) }",
            WINRT_REFERENCE_VALUE_ADAPTERS_CLASS_NAME,
            projectedType,
            projectedTypeName,
            projectedType,
            projectedType,
        )
    }
    val projector = when (typeBinding.kind) {
        KotlinProjectionAbiValueKind.ProjectedInterface ->
            CodeBlock.of("%T.Metadata.wrap(it!!)", projectedType)
        KotlinProjectionAbiValueKind.UnknownReference ->
            CodeBlock.of("it!!")
        KotlinProjectionAbiValueKind.InspectableReference ->
            CodeBlock.of("it!!.asInspectable()")
        else -> return null
    }
    val marshaller = when (typeBinding.kind) {
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass,
        KotlinProjectionAbiValueKind.ProjectedInterface ->
            CodeBlock.of("%T((it as %T).nativeObject.getRefPointer())", IUNKNOWN_REFERENCE_CLASS_NAME, IWINRT_OBJECT_CLASS_NAME)
        KotlinProjectionAbiValueKind.UnknownReference,
        KotlinProjectionAbiValueKind.InspectableReference ->
            CodeBlock.of("%T(it.getRefPointer())", IUNKNOWN_REFERENCE_CLASS_NAME)
        else -> return null
    }
    return CodeBlock.of(
        "%T<%T>(projectedTypeName = %S, typeSignature = %T.object_(), projector = { %L }, marshaller = { %L })",
        WINRT_REFERENCE_VALUE_ADAPTER_CLASS_NAME,
        projectedType,
        projectedTypeName,
        WINRT_TYPE_SIGNATURE_CLASS_NAME,
        projector,
        marshaller,
    )
}

internal fun KotlinProjectionRenderer.bindableCollectionReturnReadback(
    returnBinding: KotlinProjectionAbiTypeBinding,
): CodeBlock? {
    val projectionClass = when (returnBinding.kind) {
        KotlinProjectionAbiValueKind.MappedBindableIterable -> WINRT_BINDABLE_ITERABLE_PROJECTION_CLASS_NAME
        KotlinProjectionAbiValueKind.MappedBindableVector -> WINRT_BINDABLE_VECTOR_PROJECTION_CLASS_NAME
        KotlinProjectionAbiValueKind.MappedBindableVectorView -> WINRT_BINDABLE_VECTOR_VIEW_PROJECTION_CLASS_NAME
        else -> return null
    }
    return CodeBlock.of(
        "return %T.fromAbi(%T.readPointer(__resultOut)) ?: error(%S)\n",
        projectionClass,
        PLATFORM_ABI_CLASS_NAME,
        "Expected non-null bindable collection from ABI return for ${returnBinding.resolvedTypeName}.",
    )
}
