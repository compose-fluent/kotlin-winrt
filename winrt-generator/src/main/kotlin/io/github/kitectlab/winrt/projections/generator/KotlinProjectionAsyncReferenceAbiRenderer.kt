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

internal fun KotlinProjectionRenderer.asyncActionWithProgressReturnReadback(
    returnBinding: KotlinProjectionAbiTypeBinding,
): CodeBlock? {
    val progressBinding = returnBinding.typeArguments.singleOrNull() ?: return null
    val progressTypeSignature = asyncOperationResultTypeSignature(progressBinding) ?: return null
    val asyncActionType = WINRT_ASYNC_ACTION_WITH_PROGRESS_REFERENCE_CLASS_NAME.parameterizedBy(resolveTypeName(progressBinding.typeName))
    return CodeBlock.builder()
        .add("return %T(\n", asyncActionType)
        .indent()
        .add("pointer = %T.readPointer(__resultOut),\n", PLATFORM_ABI_CLASS_NAME)
        .add("interfaceId = %T.interfaceId(%L),\n", WINRT_ASYNC_ACTION_WITH_PROGRESS_REFERENCE_CLASS_NAME, progressTypeSignature)
        .add("progressHandlerInterfaceId = %T.progressHandlerInterfaceId(%L),\n", WINRT_ASYNC_ACTION_WITH_PROGRESS_REFERENCE_CLASS_NAME, progressTypeSignature)
        .add("completedHandlerInterfaceId = %T.completedHandlerInterfaceId(%L),\n", WINRT_ASYNC_ACTION_WITH_PROGRESS_REFERENCE_CLASS_NAME, progressTypeSignature)
        .unindent()
        .add(")\n")
        .build()
}

internal fun KotlinProjectionRenderer.asyncOperationReturnReadback(
    returnBinding: KotlinProjectionAbiTypeBinding,
): CodeBlock? {
    val resultBinding = returnBinding.typeArguments.singleOrNull() ?: return null
    val resultTypeSignature = asyncOperationResultTypeSignature(resultBinding) ?: return null
    val resultOutAllocation = abiResultAllocationForAsyncOperationResult(resultBinding, "__operationScope") ?: return null
    val resultReadbackExpression = asyncOperationResultReadbackExpression(resultBinding) ?: return null
    val asyncOperationType = WINRT_ASYNC_OPERATION_REFERENCE_CLASS_NAME.parameterizedBy(resolveTypeName(resultBinding.typeName))
    return CodeBlock.builder()
        .add("return %T(\n", asyncOperationType)
        .indent()
        .add("pointer = %T.readPointer(__resultOut),\n", PLATFORM_ABI_CLASS_NAME)
        .add("interfaceId = %T.interfaceId(%L),\n", WINRT_ASYNC_OPERATION_REFERENCE_CLASS_NAME, resultTypeSignature)
        .add("completedHandlerInterfaceId = %T.completedHandlerInterfaceId(%L),\n", WINRT_ASYNC_OPERATION_REFERENCE_CLASS_NAME, resultTypeSignature)
        .add("resultReader = { __operation ->\n")
        .indent()
        .add("%T.confinedScope().use { __operationScope ->\n", PLATFORM_ABI_CLASS_NAME)
        .indent()
        .add("val __operationResultOut = %L\n", resultOutAllocation)
        .add(
            "val __operationHr = %T.invokeArgs(__operation.pointer, %T.GetResults, __operationResultOut)\n",
            COM_VTABLE_INVOKER_CLASS_NAME,
            WINRT_ASYNC_OPERATION_VFTBL_SLOTS_CLASS_NAME,
        )
        .add("%T.checkSucceededRaw(__operationHr)\n", WINRT_PLATFORM_API_CLASS_NAME)
        .add("%L\n", resultReadbackExpression)
        .unindent()
        .add("}\n")
        .unindent()
        .add("},\n")
        .unindent()
        .add(")\n")
        .build()
}

internal fun KotlinProjectionRenderer.asyncOperationWithProgressReturnReadback(
    returnBinding: KotlinProjectionAbiTypeBinding,
): CodeBlock? {
    val resultBinding = returnBinding.typeArguments.getOrNull(0) ?: return null
    val progressBinding = returnBinding.typeArguments.getOrNull(1) ?: return null
    val resultTypeSignature = asyncOperationResultTypeSignature(resultBinding) ?: return null
    val progressTypeSignature = asyncOperationResultTypeSignature(progressBinding) ?: return null
    val resultOutAllocation = abiResultAllocationForAsyncOperationResult(resultBinding, "__operationScope") ?: return null
    val resultReadbackExpression = asyncOperationResultReadbackExpression(resultBinding) ?: return null
    val asyncOperationType = WINRT_ASYNC_OPERATION_WITH_PROGRESS_REFERENCE_CLASS_NAME.parameterizedBy(
        resolveTypeName(resultBinding.typeName),
        resolveTypeName(progressBinding.typeName),
    )
    return CodeBlock.builder()
        .add("return %T(\n", asyncOperationType)
        .indent()
        .add("pointer = %T.readPointer(__resultOut),\n", PLATFORM_ABI_CLASS_NAME)
        .add("interfaceId = %T.interfaceId(%L, %L),\n", WINRT_ASYNC_OPERATION_WITH_PROGRESS_REFERENCE_CLASS_NAME, resultTypeSignature, progressTypeSignature)
        .add("progressHandlerInterfaceId = %T.progressHandlerInterfaceId(%L, %L),\n", WINRT_ASYNC_OPERATION_WITH_PROGRESS_REFERENCE_CLASS_NAME, resultTypeSignature, progressTypeSignature)
        .add("completedHandlerInterfaceId = %T.completedHandlerInterfaceId(%L, %L),\n", WINRT_ASYNC_OPERATION_WITH_PROGRESS_REFERENCE_CLASS_NAME, resultTypeSignature, progressTypeSignature)
        .add("resultReader = { __operation ->\n")
        .indent()
        .add("%T.confinedScope().use { __operationScope ->\n", PLATFORM_ABI_CLASS_NAME)
        .indent()
        .add("val __operationResultOut = %L\n", resultOutAllocation)
        .add(
            "val __operationHr = %T.invokeArgs(__operation.pointer, %T.GetResults, __operationResultOut)\n",
            COM_VTABLE_INVOKER_CLASS_NAME,
            WINRT_ASYNC_OPERATION_WITH_PROGRESS_VFTBL_SLOTS_CLASS_NAME,
        )
        .add("%T.checkSucceededRaw(__operationHr)\n", WINRT_PLATFORM_API_CLASS_NAME)
        .add("%L\n", resultReadbackExpression)
        .unindent()
        .add("}\n")
        .unindent()
        .add("},\n")
        .unindent()
        .add(")\n")
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
            CodeBlock.of("%T.guid(%T.Metadata.IID)", WINRT_TYPE_SIGNATURE_CLASS_NAME, resultType)
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
): CodeBlock? = when (resultBinding.kind) {
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
): CodeBlock? = when (resultBinding.kind) {
    KotlinProjectionAbiValueKind.String ->
        CodeBlock.of(
            "%T.fromHandle(%T.readPointer(__operationResultOut), owner = true).use { it.toKString() }",
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
            "%T(%T.toRawComPtr(%T.readPointer(__operationResultOut))).use { it.asInspectable() }",
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
        return CodeBlock.of(
            "val __collectionRef = %T(%T.toRawComPtr(%T.readPointer(__resultOut)))\nreturn %L\n",
            IUNKNOWN_REFERENCE_CLASS_NAME,
            PLATFORM_ABI_CLASS_NAME,
            PLATFORM_ABI_CLASS_NAME,
            renderReadOnlyCollectionDelegateInitializer(binding),
        )
    }
    mutableCollectionBindingForReturn(returnBinding)?.let { binding ->
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
    return KotlinProjectionAbiMarshalerPlan(
        name = parameterName,
        typeBinding = parameterBinding.typeBinding,
        isReturn = false,
        abiArgumentExpression = CodeBlock.of("%L.abi", abiLocalName),
        abiArgumentKind = KotlinProjectionComArgumentKind.Pointer,
        scopeOpeners = listOf(
            CodeBlock.of("%T.createMarshaler(%L)!!.use { %L ->", projectionClass, parameterName, abiLocalName),
        ),
    )
}

internal fun KotlinProjectionRenderer.mappedCollectionParameterMarshaler(
    parameterBinding: KotlinProjectionAbiParameterBinding,
): KotlinProjectionAbiMarshalerPlan? {
    val parameterName = parameterBinding.name
    val abiLocalName = "__${parameterName}Abi"
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
                .add("%T.createMarshaler(%L", projectionClass, parameterName)
                .apply { adapterArguments.forEach { add(", %L", it) } }
                .add(")!!.use { %L ->", abiLocalName)
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
    when (typeBinding.kind) {
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass,
        KotlinProjectionAbiValueKind.ProjectedInterface,
        KotlinProjectionAbiValueKind.UnknownReference,
        KotlinProjectionAbiValueKind.InspectableReference -> Unit
        else -> return null
    }
    val projectedType = resolveTypeName(typeBinding.resolvedTypeName)
    val projectedTypeName = typeBinding.resolvedTypeName
    val projector = when (typeBinding.kind) {
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass ->
            CodeBlock.of("%T.Metadata.wrap(it!!.asInspectable())", projectedType)
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
