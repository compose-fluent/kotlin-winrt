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

internal fun KotlinProjectionRenderer.buildAbiCallPlan(
    binding: KotlinProjectionInstanceMemberBinding,
): KotlinProjectionAbiCallPlan? =
    buildAbiCallPlan(
        binding.returnBinding,
        binding.parameterBindings,
        binding.marshalerPlanDescriptor,
        suppressHResultCheck = binding.suppressHResultCheck,
    )

internal fun KotlinProjectionRenderer.buildAbiCallPlan(
    returnBinding: KotlinProjectionAbiTypeBinding,
    parameterBindings: List<KotlinProjectionAbiParameterBinding>,
    marshalerPlanDescriptor: WinRtAbiMarshalerPlanDescriptor? = null,
    suppressHResultCheck: Boolean = marshalerPlanDescriptor?.hasNoExceptionAttribute == true,
): KotlinProjectionAbiCallPlan? {
    val parameterMarshalers = parameterBindings.map { parameterBinding ->
        val slot = marshalerPlanDescriptor?.marshalers?.firstOrNull { !it.isReturn && it.name == parameterBinding.name }
        buildAbiParameterMarshaler(parameterBinding, slot) ?: return null
    }
    val returnMarshaler = when (returnBinding.kind) {
        KotlinProjectionAbiValueKind.Unit -> null
        else -> buildAbiReturnMarshaler(
            returnBinding,
            marshalerPlanDescriptor?.marshalers?.firstOrNull { it.isReturn },
        ) ?: return null
    }
    return KotlinProjectionAbiCallPlan(
        parameterMarshalers = parameterMarshalers,
        returnMarshaler = returnMarshaler,
        descriptor = marshalerPlanDescriptor,
        suppressHResultCheck = suppressHResultCheck || marshalerPlanDescriptor?.hasNoExceptionAttribute == true,
    )
}

internal fun KotlinProjectionRenderer.requireAbiCallPlan(
    bindingName: String,
    returnBinding: KotlinProjectionAbiTypeBinding,
    parameterBindings: List<KotlinProjectionAbiParameterBinding>,
    marshalerPlanDescriptor: WinRtAbiMarshalerPlanDescriptor? = null,
    suppressHResultCheck: Boolean = marshalerPlanDescriptor?.hasNoExceptionAttribute == true,
): KotlinProjectionAbiCallPlan {
    return requireNotNull(buildAbiCallPlan(returnBinding, parameterBindings, marshalerPlanDescriptor, suppressHResultCheck)) {
        val unsupportedKinds = buildList {
            if (
                returnBinding.kind != KotlinProjectionAbiValueKind.Unit &&
                buildAbiReturnMarshaler(returnBinding, marshalerPlanDescriptor?.marshalers?.firstOrNull { it.isReturn }) == null
            ) {
                add(returnBinding.describeAbiKind())
            }
            addAll(
                parameterBindings
                    .filter { parameterBinding ->
                        val slot = marshalerPlanDescriptor?.marshalers?.firstOrNull { !it.isReturn && it.name == parameterBinding.name }
                        buildAbiParameterMarshaler(parameterBinding, slot) == null
                    }
                    .map { parameterBinding -> parameterBinding.typeBinding.describeAbiKind() },
            )
        }
            .distinct()
            .joinToString(", ")
        "Generator ABI marshaler parity does not yet support $bindingName for $unsupportedKinds."
    }
}

internal fun KotlinProjectionRenderer.buildAbiParameterMarshaler(
    parameterBinding: KotlinProjectionAbiParameterBinding,
    descriptor: WinRtAbiMarshalerSlotDescriptor? = null,
): KotlinProjectionAbiMarshalerPlan? {
    val parameterName = parameterBinding.name
    val abiLocalName = "__${parameterName}Abi"
    customObjectAbi(parameterBinding.typeBinding)?.let { customAbi ->
        return KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%T.fromRawComPtr(%L.pointer)", PLATFORM_ABI_CLASS_NAME, abiLocalName),
            abiArgumentKind = KotlinProjectionComArgumentKind.Pointer,
            scopeOpeners = listOf(
                CodeBlock.of(
                    "%T.%L(%L, %T(%S)).use { %L ->",
                    WINRT_SYSTEM_PROJECTION_MARSHALERS_CLASS_NAME,
                    if (parameterBinding.typeBinding.isNullableAbiTypeName) "createObjectReferenceOrNull" else customAbi.createReferenceFunctionName,
                    parameterName,
                    GUID_CLASS_NAME,
                    customAbi.interfaceId.toString(),
                    abiLocalName,
                ),
            ),
        )
    }
    if (
        parameterBinding.typeBinding.kind == KotlinProjectionAbiValueKind.Unsupported &&
        parameterBinding.typeBinding.resolvedTypeName.isProjectedWinRtInterfaceReferenceName()
    ) {
        return projectedInterfaceParameterMarshaler(parameterName, parameterBinding)
    }
    return when (parameterBinding.typeBinding.kind) {
        KotlinProjectionAbiValueKind.String -> KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%L.handle", abiLocalName),
            abiArgumentKind = KotlinProjectionComArgumentKind.Pointer,
            scopeOpeners = listOf(
                CodeBlock.of("%T.createReference(%L).use { %L ->", HSTRING_CLASS_NAME, parameterName, abiLocalName),
            ),
        )
        KotlinProjectionAbiValueKind.Boolean -> KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("if (%L) 1.toByte() else 0.toByte()", parameterName),
            abiArgumentKind = KotlinProjectionComArgumentKind.Int8,
        )
        KotlinProjectionAbiValueKind.Int8 -> KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%L", parameterName),
            abiArgumentKind = KotlinProjectionComArgumentKind.Int8,
        )
        KotlinProjectionAbiValueKind.UInt8 -> KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%L.toByte()", parameterName),
            abiArgumentKind = KotlinProjectionComArgumentKind.Int8,
        )
        KotlinProjectionAbiValueKind.Int16 -> KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%L", parameterName),
            abiArgumentKind = KotlinProjectionComArgumentKind.Int16,
        )
        KotlinProjectionAbiValueKind.UInt16 -> KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%L.toShort()", parameterName),
            abiArgumentKind = KotlinProjectionComArgumentKind.Int16,
        )
        KotlinProjectionAbiValueKind.Double -> KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%L", parameterName),
            abiArgumentKind = KotlinProjectionComArgumentKind.Double,
        )
        KotlinProjectionAbiValueKind.UInt32 -> KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%L.toInt()", parameterName),
            abiArgumentKind = KotlinProjectionComArgumentKind.Int32,
        )
        KotlinProjectionAbiValueKind.Int32 -> KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%L", parameterName),
            abiArgumentKind = KotlinProjectionComArgumentKind.Int32,
        )
        KotlinProjectionAbiValueKind.Int64 -> KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%L", parameterName),
            abiArgumentKind = KotlinProjectionComArgumentKind.Int64,
        )
        KotlinProjectionAbiValueKind.UInt64 -> KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%L.toLong()", parameterName),
            abiArgumentKind = KotlinProjectionComArgumentKind.Int64,
        )
        KotlinProjectionAbiValueKind.Float -> KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%L", parameterName),
            abiArgumentKind = KotlinProjectionComArgumentKind.Float,
        )
        KotlinProjectionAbiValueKind.Char16 -> KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%L.code.toShort()", parameterName),
            abiArgumentKind = KotlinProjectionComArgumentKind.Int16,
        )
        KotlinProjectionAbiValueKind.GuidValue -> {
            val scopeName = "__${parameterName}GuidScope"
            val abiLocalName = "__${parameterName}Abi"
            KotlinProjectionAbiMarshalerPlan(
                name = parameterName,
                typeBinding = parameterBinding.typeBinding,
                isReturn = false,
                abiArgumentExpression = CodeBlock.of("%L", abiLocalName),
                abiArgumentKind = KotlinProjectionComArgumentKind.Pointer,
                scopeOpeners = listOf(
                    CodeBlock.of(
                        "%T.confinedScope().use { %L ->\nval %L = %T.allocateBytes(%L, %T.BYTE_SIZE.toLong())\n%L.writeTo(%L)",
                        PLATFORM_ABI_CLASS_NAME,
                        scopeName,
                        abiLocalName,
                        PLATFORM_ABI_CLASS_NAME,
                        scopeName,
                        GUID_CLASS_NAME,
                        parameterName,
                        abiLocalName,
                    ),
                ),
            )
        }
        KotlinProjectionAbiValueKind.Enum -> enumParameterMarshaler(parameterBinding)
        KotlinProjectionAbiValueKind.Struct -> nativeStructParameterMarshaler(parameterBinding)
        KotlinProjectionAbiValueKind.Reference -> referenceParameterMarshaler(parameterBinding, WINRT_REFERENCE_PROJECTION_CLASS_NAME)
        KotlinProjectionAbiValueKind.ReferenceArray -> referenceParameterMarshaler(parameterBinding, WINRT_REFERENCE_ARRAY_PROJECTION_CLASS_NAME)
        KotlinProjectionAbiValueKind.GenericParameter -> genericParameterMarshaler(parameterName, parameterBinding)
        KotlinProjectionAbiValueKind.Array -> arrayParameterMarshaler(parameterBinding, descriptor)
        KotlinProjectionAbiValueKind.Delegate -> delegateParameterMarshaler(parameterBinding)
        KotlinProjectionAbiValueKind.MappedBindableIterable,
        KotlinProjectionAbiValueKind.MappedBindableVector,
        KotlinProjectionAbiValueKind.MappedBindableVectorView -> bindableCollectionParameterMarshaler(parameterBinding)
        KotlinProjectionAbiValueKind.MappedIterable,
        KotlinProjectionAbiValueKind.MappedVector,
        KotlinProjectionAbiValueKind.MappedMap,
        KotlinProjectionAbiValueKind.MappedVectorView,
        KotlinProjectionAbiValueKind.MappedMapView -> mappedCollectionParameterMarshaler(parameterBinding)
        KotlinProjectionAbiValueKind.ProjectedInterface -> projectedInterfaceParameterMarshaler(parameterName, parameterBinding)
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass -> projectedRuntimeClassParameterMarshaler(parameterName, parameterBinding)
        KotlinProjectionAbiValueKind.Object -> {
            val marshalerName = "__${parameterName}Marshaler"
            KotlinProjectionAbiMarshalerPlan(
                name = parameterName,
                typeBinding = parameterBinding.typeBinding,
                isReturn = false,
                abiArgumentExpression = CodeBlock.of("%L.abi", marshalerName),
                abiArgumentKind = KotlinProjectionComArgumentKind.Pointer,
                scopeOpeners = listOf(
                    CodeBlock.of("%M(%L).use { %L ->", WINRT_OBJECT_MARSHALER_FUNCTION_NAME, parameterName, marshalerName),
                ),
            )
        }
        KotlinProjectionAbiValueKind.UnknownReference,
        KotlinProjectionAbiValueKind.InspectableReference -> KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%T.fromRawComPtr(%L.pointer)", PLATFORM_ABI_CLASS_NAME, parameterName),
            abiArgumentKind = KotlinProjectionComArgumentKind.Pointer,
        )
        else -> null
    }
}

private fun projectedInterfaceParameterMarshaler(
    parameterName: String,
    parameterBinding: KotlinProjectionAbiParameterBinding,
): KotlinProjectionAbiMarshalerPlan =
    KotlinProjectionAbiMarshalerPlan(
        name = parameterName,
        typeBinding = parameterBinding.typeBinding,
        isReturn = false,
        abiArgumentExpression = projectedObjectParameterAbiExpression(parameterName, parameterBinding),
        abiArgumentKind = KotlinProjectionComArgumentKind.Pointer,
    )

private fun KotlinProjectionRenderer.projectedRuntimeClassParameterMarshaler(
    parameterName: String,
    parameterBinding: KotlinProjectionAbiParameterBinding,
): KotlinProjectionAbiMarshalerPlan {
    val interfaceId = parameterBinding.typeBinding.interfaceId ?: return KotlinProjectionAbiMarshalerPlan(
        name = parameterName,
        typeBinding = parameterBinding.typeBinding,
        isReturn = false,
        abiArgumentExpression = projectedObjectParameterAbiExpression(parameterName, parameterBinding),
        abiArgumentKind = KotlinProjectionComArgumentKind.Pointer,
    )
    val marshalerName = "__${parameterName}ProjectionMarshaler"
    return KotlinProjectionAbiMarshalerPlan(
        name = parameterName,
        typeBinding = parameterBinding.typeBinding,
        isReturn = false,
        abiArgumentExpression = CodeBlock.of("%L.abi", marshalerName),
        abiArgumentKind = KotlinProjectionComArgumentKind.Pointer,
        scopeOpeners = listOf(
            CodeBlock.of(
                "%M(%L, %S, %T(%S)).use { %L ->",
                WINRT_PROJECTION_MARSHALER_FUNCTION_NAME,
                parameterName,
                parameterBinding.typeBinding.resolvedTypeName,
                GUID_CLASS_NAME,
                interfaceId.toString(),
                marshalerName,
            ),
        ),
    )
}

private fun projectedObjectParameterAbiExpression(
    parameterName: String,
    parameterBinding: KotlinProjectionAbiParameterBinding,
): CodeBlock =
    if (parameterBinding.typeBinding.isNullableAbiTypeName) {
        CodeBlock.of(
            "%L?.let { %T.fromRawComPtr((it as %T).nativeObject.pointer) } ?: %T.nullPointer",
            parameterName,
            PLATFORM_ABI_CLASS_NAME,
            IWINRT_OBJECT_CLASS_NAME,
            PLATFORM_ABI_CLASS_NAME,
        )
    } else {
        CodeBlock.of(
            "%T.fromRawComPtr((%L as %T).nativeObject.pointer)",
            PLATFORM_ABI_CLASS_NAME,
            parameterName,
            IWINRT_OBJECT_CLASS_NAME,
        )
    }

private fun genericParameterMarshaler(
    parameterName: String,
    parameterBinding: KotlinProjectionAbiParameterBinding,
): KotlinProjectionAbiMarshalerPlan {
    val abiReferenceName = "__${parameterName}AbiReference"
    return KotlinProjectionAbiMarshalerPlan(
        name = parameterName,
        typeBinding = parameterBinding.typeBinding,
        isReturn = false,
        abiArgumentExpression = CodeBlock.of(
            "%L?.let { %T.fromRawComPtr(it.pointer) } ?: %T.nullPointer",
            abiReferenceName,
            PLATFORM_ABI_CLASS_NAME,
            PLATFORM_ABI_CLASS_NAME,
        ),
        abiArgumentKind = KotlinProjectionComArgumentKind.Pointer,
        scopeOpeners = listOf(
            CodeBlock.of(
                "%T.createReference(%L)\n.use { %L ->",
                WINRT_GENERIC_PARAMETER_PROJECTION_CLASS_NAME,
                parameterName,
                abiReferenceName,
            ),
        ),
    )
}

internal fun KotlinProjectionRenderer.buildAbiReturnMarshaler(
    returnBinding: KotlinProjectionAbiTypeBinding,
    descriptor: WinRtAbiMarshalerSlotDescriptor? = null,
): KotlinProjectionAbiMarshalerPlan? {
    if (returnBinding.kind == KotlinProjectionAbiValueKind.Array) {
        return arrayReturnMarshaler(returnBinding, descriptor)
    }
    val resultOutLayout = when {
        customObjectAbi(returnBinding) != null -> CodeBlock.of("%T.allocatePointerSlot(__scope)", PLATFORM_ABI_CLASS_NAME)
        returnBinding.isMappedCollectionBinding() -> CodeBlock.of("%T.allocatePointerSlot(__scope)", PLATFORM_ABI_CLASS_NAME)
        returnBinding.isMappedBindableCollectionBinding() -> CodeBlock.of("%T.allocatePointerSlot(__scope)", PLATFORM_ABI_CLASS_NAME)
        else -> when (returnBinding.kind) {
        KotlinProjectionAbiValueKind.String,
        KotlinProjectionAbiValueKind.MappedAsyncAction,
        KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress,
        KotlinProjectionAbiValueKind.MappedAsyncOperation,
        KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress,
        KotlinProjectionAbiValueKind.Object,
        KotlinProjectionAbiValueKind.UnknownReference,
        KotlinProjectionAbiValueKind.InspectableReference,
        KotlinProjectionAbiValueKind.Reference,
        KotlinProjectionAbiValueKind.ReferenceArray -> CodeBlock.of("%T.allocatePointerSlot(__scope)", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.GenericParameter -> CodeBlock.of("%T.allocatePointerSlot(__scope)", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.ProjectedInterface,
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass -> CodeBlock.of("%T.allocatePointerSlot(__scope)", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Enum -> abiResultAllocationForIntegralType(returnBinding.enumUnderlyingType ?: return null)
        KotlinProjectionAbiValueKind.Boolean -> CodeBlock.of("%T.allocateInt8Slot(__scope)", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int8,
        KotlinProjectionAbiValueKind.UInt8 -> CodeBlock.of("%T.allocateInt8Slot(__scope)", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int16,
        KotlinProjectionAbiValueKind.UInt16 -> CodeBlock.of("%T.allocateBytes(__scope, 2)", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int32,
        KotlinProjectionAbiValueKind.UInt32 -> CodeBlock.of("%T.allocateInt32Slot(__scope)", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int64,
        KotlinProjectionAbiValueKind.UInt64 -> CodeBlock.of("%T.allocateInt64Slot(__scope)", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Float -> CodeBlock.of("%T.allocateBytes(__scope, 4)", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Double -> CodeBlock.of("%T.allocateDoubleSlot(__scope)", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Char16 -> CodeBlock.of("%T.allocateBytes(__scope, 2)", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.GuidValue -> CodeBlock.of("%T.allocateBytes(__scope, %T.BYTE_SIZE.toLong())", PLATFORM_ABI_CLASS_NAME, GUID_CLASS_NAME)
        KotlinProjectionAbiValueKind.Unit,
        KotlinProjectionAbiValueKind.MappedIterable,
        KotlinProjectionAbiValueKind.MappedVector,
        KotlinProjectionAbiValueKind.MappedMap,
        KotlinProjectionAbiValueKind.MappedVectorView,
        KotlinProjectionAbiValueKind.MappedMapView,
        KotlinProjectionAbiValueKind.MappedBindableIterable,
        KotlinProjectionAbiValueKind.MappedBindableVector,
        KotlinProjectionAbiValueKind.MappedBindableVectorView,
        KotlinProjectionAbiValueKind.Array,
        KotlinProjectionAbiValueKind.Unsupported -> return null
        KotlinProjectionAbiValueKind.MappedKeyValuePair -> CodeBlock.of("%T.allocatePointerSlot(__scope)", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Delegate -> CodeBlock.of("%T.allocatePointerSlot(__scope)", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Struct ->
            customStructAbi(returnBinding)?.let { customAbi ->
                CodeBlock.of("%T.allocateBytes(__scope, %LL)", PLATFORM_ABI_CLASS_NAME, customAbi.sizeBytes)
            } ?: nativeStructClassName(returnBinding)?.let { returnType ->
                CodeBlock.of("%T.allocateBytes(__scope, %T.Metadata.layout.sizeBytes)", PLATFORM_ABI_CLASS_NAME, returnType)
            } ?: return null
        }
    }
    val readbackStatement = when {
        customObjectAbi(returnBinding) != null -> customObjectReturnReadback(returnBinding)
        returnBinding.isMappedCollectionBinding() -> mappedCollectionReturnReadback(returnBinding)
        returnBinding.isMappedBindableCollectionBinding() -> bindableCollectionReturnReadback(returnBinding)
        else -> when (returnBinding.kind) {
        KotlinProjectionAbiValueKind.String ->
            CodeBlock.of(
                "val __resultHandle = %T.readPointer(__resultOut)\nval __resultString = %T.fromHandle(__resultHandle, owner = true)\nreturn __resultString.use { value -> value.toKString() }\n",
                PLATFORM_ABI_CLASS_NAME,
                HSTRING_CLASS_NAME,
            )
        KotlinProjectionAbiValueKind.MappedAsyncAction ->
            asyncReferenceExpression(
                returnBinding = returnBinding,
                pointerExpression = CodeBlock.of("%T.readPointer(__resultOut)", PLATFORM_ABI_CLASS_NAME),
            )?.let { CodeBlock.of("return %L\n", it) } ?: return null
        KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress ->
            asyncActionWithProgressReturnReadback(returnBinding) ?: return null
        KotlinProjectionAbiValueKind.MappedAsyncOperation ->
            asyncOperationReturnReadback(returnBinding) ?: return null
        KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress ->
            asyncOperationWithProgressReturnReadback(returnBinding) ?: return null
        KotlinProjectionAbiValueKind.Boolean ->
            CodeBlock.of("val __result = %T.readInt8(__resultOut)\nreturn __result.toInt() != 0\n", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int8 ->
            CodeBlock.of("val __result = %T.readInt8(__resultOut)\nreturn __result\n", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.UInt8 ->
            CodeBlock.of("val __result = %T.readInt8(__resultOut).toUByte()\nreturn __result\n", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int16 ->
            CodeBlock.of("val __result = %T.readInt16(__resultOut)\nreturn __result\n", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.UInt16 ->
            CodeBlock.of("val __result = %T.readInt16(__resultOut).toUShort()\nreturn __result\n", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int32 ->
            CodeBlock.of("val __result = %T.readInt32(__resultOut)\nreturn __result\n", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.UInt32 ->
            CodeBlock.of("val __result = %T.readInt32(__resultOut).toUInt()\nreturn __result\n", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int64 ->
            CodeBlock.of("val __result = %T.readInt64(__resultOut)\nreturn __result\n", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.UInt64 ->
            CodeBlock.of("val __result = %T.readInt64(__resultOut).toULong()\nreturn __result\n", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Float ->
            CodeBlock.of("val __result = %T.readFloat(__resultOut)\nreturn __result\n", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Double ->
            CodeBlock.of("val __result = %T.readDouble(__resultOut)\nreturn __result\n", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Char16 ->
            CodeBlock.of("val __result = %T.readChar16(__resultOut)\nreturn __result\n", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.GuidValue ->
            CodeBlock.of("val __result = %T.readGuid(__resultOut)\nreturn __result\n", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Reference ->
            referenceReturnReadback(returnBinding, WINRT_REFERENCE_PROJECTION_CLASS_NAME) ?: return null
        KotlinProjectionAbiValueKind.ReferenceArray ->
            referenceReturnReadback(returnBinding, WINRT_REFERENCE_ARRAY_PROJECTION_CLASS_NAME) ?: return null
        KotlinProjectionAbiValueKind.GenericParameter ->
            CodeBlock.of(
                "val __resultPointer = %T.readPointer(__resultOut)\nval __result = %T.fromAbi<%T>(__resultPointer)\nreturn __result\n",
                PLATFORM_ABI_CLASS_NAME,
                WINRT_GENERIC_PARAMETER_PROJECTION_CLASS_NAME,
                resolveTypeName(returnBinding.typeName),
            )
        KotlinProjectionAbiValueKind.Enum ->
            enumReturnReadback(returnBinding, resolvedReturnClassName(returnBinding))
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass ->
            resolvedReturnClassName(returnBinding)?.let { returnType ->
                CodeBlock.of(
                    "val __resultPointer = %T.readPointer(__resultOut)\n%Lval __resultRef = %T(%T.toRawComPtr(__resultPointer))\nval __resultInspectable = try {\n__resultRef.asInspectable()\n} finally {\n__resultRef.close()\n}\nval __result = %T.Metadata.wrap(__resultInspectable)\nreturn __result\n",
                    PLATFORM_ABI_CLASS_NAME,
                    abiNullReturnReadback(returnBinding),
                    IUNKNOWN_REFERENCE_CLASS_NAME,
                    PLATFORM_ABI_CLASS_NAME,
                    returnType,
                )
            }
        KotlinProjectionAbiValueKind.ProjectedInterface ->
            observableVectorReturnReadback(returnBinding) ?: resolvedReturnClassName(returnBinding)?.let { returnType ->
                CodeBlock.of(
                    "val __resultPointer = %T.readPointer(__resultOut)\n%Lval __resultRef = %T(%T.toRawComPtr(__resultPointer))\nval __result = %T.Metadata.wrap(__resultRef)\nreturn __result\n",
                    PLATFORM_ABI_CLASS_NAME,
                    abiNullReturnReadback(returnBinding),
                    IUNKNOWN_REFERENCE_CLASS_NAME,
                    PLATFORM_ABI_CLASS_NAME,
                    returnType,
                )
            }
        KotlinProjectionAbiValueKind.InspectableReference ->
            if (resolvedReturnClassName(returnBinding) == IINSPECTABLE_REFERENCE_CLASS_NAME) {
                CodeBlock.of(
                    "val __resultPointer = %T.readPointer(__resultOut)\n%Lval __resultRef = %T(%T.toRawComPtr(__resultPointer))\nval __result = __resultRef.use { it.asInspectable() }\nreturn __result\n",
                    PLATFORM_ABI_CLASS_NAME,
                    abiNullReturnReadback(returnBinding),
                    IUNKNOWN_REFERENCE_CLASS_NAME,
                    PLATFORM_ABI_CLASS_NAME,
                )
            } else {
                return null
            }
        KotlinProjectionAbiValueKind.Object ->
            if (resolvedReturnClassName(returnBinding) == IINSPECTABLE_REFERENCE_CLASS_NAME) {
                CodeBlock.of(
                    "val __resultPointer = %T.readPointer(__resultOut)\n%Lval __resultRef = %T(%T.toRawComPtr(__resultPointer))\nval __result = __resultRef.use { it.asInspectable() }\nreturn __result\n",
                    PLATFORM_ABI_CLASS_NAME,
                    abiNullReturnReadback(returnBinding),
                    IUNKNOWN_REFERENCE_CLASS_NAME,
                    PLATFORM_ABI_CLASS_NAME,
                )
            } else {
                CodeBlock.of(
                    "val __resultPointer = %T.readPointer(__resultOut)\n%Lval __result = %T.fromAbi(__resultPointer)\nreturn __result\n",
                    PLATFORM_ABI_CLASS_NAME,
                    abiNullReturnReadback(returnBinding),
                    WINRT_OBJECT_MARSHALLER_CLASS_NAME,
                )
            }
        KotlinProjectionAbiValueKind.UnknownReference ->
            if (resolvedReturnClassName(returnBinding) == IUNKNOWN_REFERENCE_CLASS_NAME) {
                CodeBlock.of(
                    "val __resultPointer = %T.readPointer(__resultOut)\n%Lval __result = %T(%T.toRawComPtr(__resultPointer))\nreturn __result\n",
                    PLATFORM_ABI_CLASS_NAME,
                    abiNullReturnReadback(returnBinding),
                    IUNKNOWN_REFERENCE_CLASS_NAME,
                    PLATFORM_ABI_CLASS_NAME,
                )
            } else {
                return null
            }
        KotlinProjectionAbiValueKind.Delegate ->
            resolvedReturnClassName(returnBinding)?.let { returnType ->
                CodeBlock.of(
                    "val __resultPointer = %T.readPointer(__resultOut)\n%Lval __result = %T.Metadata.fromAbi(__resultPointer) ?: error(%S)\nreturn __result\n",
                    PLATFORM_ABI_CLASS_NAME,
                    abiNullReturnReadback(returnBinding),
                    returnType,
                    "Expected non-null delegate instance from ABI return for ${returnBinding.resolvedTypeName}.",
                )
            }
        KotlinProjectionAbiValueKind.Struct ->
            customStructAbi(returnBinding)?.let { customAbi ->
                if (customAbi.disposeAbiFunctionName != null) {
                    CodeBlock.of(
                        "try {\n    val __result = %T.%L(__resultOut)\n    return __result\n} finally {\n    %T.%L(__resultOut)\n}\n",
                        customAbi.helperTypeName,
                        customAbi.fromAbiFunctionName,
                        customAbi.helperTypeName,
                        customAbi.disposeAbiFunctionName,
                    )
                } else {
                    CodeBlock.of("val __result = %T.%L(__resultOut)\nreturn __result\n", customAbi.helperTypeName, customAbi.fromAbiFunctionName)
                }
            } ?: nativeStructClassName(returnBinding)?.let { returnType ->
                CodeBlock.of(
                    "try {\n    val __result = %T.Metadata.fromAbi(__resultOut)\n    return __result\n} finally {\n    %T.Metadata.disposeAbi(__resultOut)\n}\n",
                    returnType,
                    returnType,
                )
            }
        KotlinProjectionAbiValueKind.MappedKeyValuePair ->
            mappedKeyValuePairReturnReadback(returnBinding)
        KotlinProjectionAbiValueKind.Unit,
        KotlinProjectionAbiValueKind.MappedIterable,
        KotlinProjectionAbiValueKind.MappedVector,
        KotlinProjectionAbiValueKind.MappedMap,
        KotlinProjectionAbiValueKind.MappedVectorView,
        KotlinProjectionAbiValueKind.MappedMapView,
        KotlinProjectionAbiValueKind.MappedBindableIterable,
        KotlinProjectionAbiValueKind.MappedBindableVector,
        KotlinProjectionAbiValueKind.MappedBindableVectorView,
        KotlinProjectionAbiValueKind.Array,
        KotlinProjectionAbiValueKind.Unsupported -> return null
        }
    }
    return KotlinProjectionAbiMarshalerPlan(
        name = "retval",
        typeBinding = returnBinding,
        isReturn = true,
        abiArgumentExpression = CodeBlock.of("__resultOut"),
        abiArgumentKind = KotlinProjectionComArgumentKind.Pointer,
        resultAllocation = resultOutLayout,
        readbackStatement = readbackStatement,
    )
}

internal fun abiNullReturnReadback(binding: KotlinProjectionAbiTypeBinding): CodeBlock =
    if (binding.isNullableAbiReturn) {
        CodeBlock.of("if (%T.isNull(__resultPointer)) return null\n", PLATFORM_ABI_CLASS_NAME)
    } else {
        CodeBlock.of(
            "if (%T.isNull(__resultPointer)) error(%S)\n",
            PLATFORM_ABI_CLASS_NAME,
            "WINRT_E_NULL_ABI_RETURN",
        )
    }

internal val KotlinProjectionAbiTypeBinding.isNullableAbiReturn: Boolean
    get() = isNullableAbiTypeName

internal val KotlinProjectionAbiTypeBinding.isNullableAbiTypeName: Boolean
    get() = typeName.trim().endsWith("?") || resolvedTypeName.trim().endsWith("?")

internal fun KotlinProjectionRenderer.resolvedReturnClassName(
    returnBinding: KotlinProjectionAbiTypeBinding,
): ClassName? =
    runCatching { resolveTypeName(returnBinding.typeName.trim().removeSuffix("?")) as? ClassName }.getOrNull()
        ?: runCatching { resolveTypeName(returnBinding.resolvedTypeName.trim().removeSuffix("?")) as? ClassName }.getOrNull()

internal fun KotlinProjectionRenderer.mappedKeyValuePairReturnReadback(
    returnBinding: KotlinProjectionAbiTypeBinding,
): CodeBlock? {
    val keyBinding = returnBinding.typeArguments.getOrNull(0) ?: return null
    val valueBinding = returnBinding.typeArguments.getOrNull(1) ?: return null
    val keyType = resolveTypeName(keyBinding.typeName)
    val valueType = resolveTypeName(valueBinding.typeName)
    return CodeBlock.of(
        """
        val __pairRef = %T(%T.toRawComPtr(%T.readPointer(__resultOut)))
        fun __readKey(__pair: %T): %T {
            %L
        }
        fun __readValue(__pair: %T): %T {
            %L
        }
        val __key = __readKey(__pairRef)
        val __value = __readValue(__pairRef)
        return object : %T {
            override val key: %T = __key
            override val value: %T = __value
        }
        """.trimIndent() + "\n",
        IUNKNOWN_REFERENCE_CLASS_NAME,
        PLATFORM_ABI_CLASS_NAME,
        PLATFORM_ABI_CLASS_NAME,
        IUNKNOWN_REFERENCE_CLASS_NAME,
        keyType,
        renderCollectionInvocation(
            invokeTargetExpression = "__pair",
            slotInterfaceQualifiedName = "Windows.Foundation.Collections.IKeyValuePair",
            slotConstantName = "KEY_GETTER_SLOT",
            returnBinding = keyBinding,
        ).toString(),
        IUNKNOWN_REFERENCE_CLASS_NAME,
        valueType,
        renderCollectionInvocation(
            invokeTargetExpression = "__pair",
            slotInterfaceQualifiedName = "Windows.Foundation.Collections.IKeyValuePair",
            slotConstantName = "VALUE_GETTER_SLOT",
            returnBinding = valueBinding,
        ).toString(),
        Map.Entry::class.asClassName().parameterizedBy(keyType, valueType),
        keyType,
        valueType,
    )
}
