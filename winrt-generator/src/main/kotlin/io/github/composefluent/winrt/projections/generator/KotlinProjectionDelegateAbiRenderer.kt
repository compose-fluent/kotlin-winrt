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
import io.github.composefluent.winrt.metadata.WinRtEventHandlerKind
import io.github.composefluent.winrt.metadata.requireValidForProjection
import io.github.composefluent.winrt.metadata.semanticHelpers
import io.github.composefluent.winrt.metadata.winRtEventHandlerKindForTypeName
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

internal fun KotlinProjectionRenderer.enumParameterMarshaler(
    parameterBinding: KotlinProjectionAbiParameterBinding,
): KotlinProjectionAbiMarshalerPlan? {
    val integralType = parameterBinding.typeBinding.enumUnderlyingType ?: return null
    return KotlinProjectionAbiMarshalerPlan(
        name = parameterBinding.name,
        typeBinding = parameterBinding.typeBinding,
        isReturn = false,
        abiArgumentExpression = CodeBlock.of("%L.abiValue%L", parameterBinding.name, abiIntegralArgumentConversionSuffix(integralType)),
        abiArgumentKind = abiArgumentKindForIntegralType(integralType),
    )
}

internal fun KotlinProjectionRenderer.delegateParameterMarshaler(
    parameterBinding: KotlinProjectionAbiParameterBinding,
): KotlinProjectionAbiMarshalerPlan? {
    val invokeShape = outboundDelegateInvokeShape(parameterBinding.typeBinding) ?: return null
    if (!invokeShape.isSupportedOutboundDelegateShape()) {
        return null
    }
    val delegateIid = delegateInterfaceIdCode(parameterBinding.typeBinding, invokeShape) ?: return null
    if (parameterBinding.typeBinding.isNullableAbiTypeName) {
        return nullableDelegateParameterMarshaler(parameterBinding, invokeShape, delegateIid)
    }
    val handleName = "__${parameterBinding.name}Handle"
    val abiReferenceName = "__${parameterBinding.name}Abi"
    return KotlinProjectionAbiMarshalerPlan(
        name = parameterBinding.name,
        typeBinding = parameterBinding.typeBinding,
        isReturn = false,
        abiArgumentExpression = CodeBlock.of("%T.fromRawComPtr(%L.pointer)", PLATFORM_ABI_CLASS_NAME, abiReferenceName),
        abiArgumentKind = KotlinProjectionComArgumentKind.Pointer,
        scopeOpeners = listOf(
            CodeBlock.of(
                "%T.createDelegate(iid = %L, parameterKinds = %L, returnKind = %L, parameterStructAdapters = %L, returnStructAdapter = %L) { __args ->\n%L(%L)\n}.use { %L ->",
                WINRT_DELEGATE_BRIDGE_CLASS_NAME,
                delegateIid,
                delegateParameterKindsCode(invokeShape.parameterBindings),
                delegateInvokeReturnKindCode(invokeShape.returnBinding),
                delegateParameterStructAdaptersCode(invokeShape.parameterBindings),
                delegateReturnStructAdapterCode(invokeShape.returnBinding),
                parameterBinding.name,
                delegateCallbackArgumentCodeList(invokeShape.parameterBindings),
                handleName,
            ),
            CodeBlock.of("%L.createReference().use { %L ->", handleName, abiReferenceName),
        ),
    )
}

private fun KotlinProjectionRenderer.nullableDelegateParameterMarshaler(
    parameterBinding: KotlinProjectionAbiParameterBinding,
    invokeShape: KotlinProjectionDelegateInvokeShape,
    delegateIid: CodeBlock,
): KotlinProjectionAbiMarshalerPlan {
    val callbackName = "__${parameterBinding.name}Callback"
    val abiName = "__${parameterBinding.name}Abi"
    return KotlinProjectionAbiMarshalerPlan(
        name = parameterBinding.name,
        typeBinding = parameterBinding.typeBinding,
        isReturn = false,
        abiArgumentExpression = CodeBlock.of("%L.abi", abiName),
        abiArgumentKind = KotlinProjectionComArgumentKind.Pointer,
        scopeOpeners = listOf(
            CodeBlock.of(
                """
                val %L = %L?.let { %L ->
                { __args: %T<%T?> ->
                %L(%L)
                }
                }
                %T.createDelegateArgument(iid = %L, parameterKinds = %L, returnKind = %L, parameterStructAdapters = %L, returnStructAdapter = %L, callback = %L).use { %L ->
                """.trimIndent(),
                callbackName,
                parameterBinding.name,
                parameterBinding.name,
                LIST_CLASS_NAME,
                ANY,
                parameterBinding.name,
                delegateCallbackArgumentCodeList(invokeShape.parameterBindings),
                WINRT_DELEGATE_BRIDGE_CLASS_NAME,
                delegateIid,
                delegateParameterKindsCode(invokeShape.parameterBindings),
                delegateInvokeReturnKindCode(invokeShape.returnBinding),
                delegateParameterStructAdaptersCode(invokeShape.parameterBindings),
                delegateReturnStructAdapterCode(invokeShape.returnBinding),
                callbackName,
                abiName,
            ),
        ),
    )
}

internal fun abiArgumentKindForIntegralType(type: WinRtIntegralType): KotlinProjectionComArgumentKind =
    integralAbiDescriptor(type).comArgumentKind

internal fun KotlinProjectionRenderer.outboundDelegateInvokeShape(
    typeBinding: KotlinProjectionAbiTypeBinding,
): KotlinProjectionDelegateInvokeShape? {
    val invokeShape = typeBinding.delegateInvokeShape ?: return null
    return when (winRtEventHandlerKindForTypeName(typeBinding.resolvedTypeName)) {
        WinRtEventHandlerKind.EventHandler ->
            if (typeBinding.typeArguments.size == 1) {
                invokeShape.copy(
                    parameterBindings = listOf(
                        KotlinProjectionAbiParameterBinding(
                            "sender",
                            KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Object, "Any", "System.Object"),
                        ),
                        KotlinProjectionAbiParameterBinding("args", typeBinding.typeArguments[0]),
                    ),
                )
            } else {
                invokeShape
            }

        WinRtEventHandlerKind.TypedEventHandler ->
            if (typeBinding.typeArguments.size == 2) {
                invokeShape.copy(
                    parameterBindings = listOf(
                        KotlinProjectionAbiParameterBinding("sender", typeBinding.typeArguments[0]),
                        KotlinProjectionAbiParameterBinding("args", typeBinding.typeArguments[1]),
                    ),
                )
            } else {
                invokeShape
            }

        WinRtEventHandlerKind.VectorChangedEventHandler,
        WinRtEventHandlerKind.MapChangedEventHandler,
        WinRtEventHandlerKind.AsyncActionProgressHandler,
        WinRtEventHandlerKind.AsyncOperationProgressHandler,
        WinRtEventHandlerKind.PropertyChangedEventHandler,
        WinRtEventHandlerKind.NotifyCollectionChangedEventHandler,
        null,
        -> invokeShape
    }
}

internal fun KotlinProjectionRenderer.delegateInterfaceIdCode(
    typeBinding: KotlinProjectionAbiTypeBinding,
    invokeShape: KotlinProjectionDelegateInvokeShape,
): CodeBlock? {
    val delegateIid = invokeShape.interfaceId ?: return null
    if (typeBinding.typeArguments.isEmpty()) {
        return CodeBlock.of("%T(%S)", GUID_CLASS_NAME, delegateIid.toString())
    }
    val argumentSignatures = typeBinding.typeArguments.map { typeArgument ->
        abiTypeSignature(typeArgument) ?: return null
    }
    return CodeBlock.builder()
        .add("%T.createFromParameterizedInterface(%T(%S)", PARAMETERIZED_INTERFACE_ID_CLASS_NAME, GUID_CLASS_NAME, delegateIid.toString())
        .apply {
            argumentSignatures.forEach { signature ->
                add(", %L", signature)
            }
        }
        .add(")")
        .build()
}

internal fun KotlinProjectionRenderer.enumReturnReadback(
    returnBinding: KotlinProjectionAbiTypeBinding,
    returnType: ClassName?,
): CodeBlock? {
    val integralType = returnBinding.enumUnderlyingType ?: return null
    val enumType = returnType ?: return null
    return CodeBlock.builder()
        .addStatement("val __enumValue = %L", abiIntegralReadbackExpression(integralType))
        .addStatement("val __enumResult = %T.Metadata.fromAbi(__enumValue)", enumType)
        .addStatement("return __enumResult")
        .build()
}

internal fun KotlinProjectionRenderer.abiResultAllocationForIntegralType(type: WinRtIntegralType): CodeBlock =
    integralResultSlotAllocation(type, "__scope")

internal fun KotlinProjectionRenderer.abiIntegralArgumentConversionSuffix(type: WinRtIntegralType): String =
    integralAbiDescriptor(type).argumentConversionSuffix

internal fun KotlinProjectionRenderer.abiIntegralReadbackExpression(type: WinRtIntegralType): CodeBlock =
    integralPlatformReadExpression(type, CodeBlock.of("__resultOut"))

internal fun KotlinProjectionRenderer.delegateParameterKindsCode(
    parameterBindings: List<KotlinProjectionAbiParameterBinding>,
): CodeBlock =
    CodeBlock.builder()
        .add("listOf(")
        .apply {
            parameterBindings.forEachIndexed { index, parameterBinding ->
                if (index > 0) {
                    add(", ")
                }
                add("%L", delegateValueKindCode(parameterBinding.typeBinding))
            }
        }
        .add(")")
        .build()

internal fun KotlinProjectionRenderer.delegateDescriptorCode(
    invokeShape: KotlinProjectionDelegateInvokeShape,
    runtimeClassName: String,
): CodeBlock =
    CodeBlock.of(
        "%T(interfaceId = %T(%S), parameterKinds = %L, returnKind = %L, parameterStructAdapters = %L, returnStructAdapter = %L, runtimeClassName = %S)",
        WINRT_DELEGATE_DESCRIPTOR_CLASS_NAME,
        GUID_CLASS_NAME,
        invokeShape.interfaceId.toString(),
        delegateInvokeParameterKindsCode(invokeShape.parameterBindings),
        delegateInvokeReturnKindCode(invokeShape.returnBinding),
        delegateParameterStructAdaptersCode(invokeShape.parameterBindings),
        delegateReturnStructAdapterCode(invokeShape.returnBinding),
        runtimeClassName,
    )

internal fun KotlinProjectionRenderer.delegateInvokeParameterKindsCode(
    parameterBindings: List<KotlinProjectionAbiParameterBinding>,
): CodeBlock =
    CodeBlock.builder()
        .add("listOf(")
        .apply {
            parameterBindings.forEachIndexed { index, parameterBinding ->
                if (index > 0) {
                    add(", ")
                }
                add("%L", delegateInvokeValueKindCode(parameterBinding.typeBinding))
            }
        }
        .add(")")
        .build()

internal fun KotlinProjectionRenderer.delegateInvokeReturnKindCode(
    returnBinding: KotlinProjectionAbiTypeBinding,
): CodeBlock = delegateInvokeValueKindCode(returnBinding)

internal fun KotlinProjectionRenderer.delegateValueKindCode(typeBinding: KotlinProjectionAbiTypeBinding): CodeBlock = when (typeBinding.kind) {
    KotlinProjectionAbiValueKind.String -> CodeBlock.of("%T.HSTRING", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.Boolean -> CodeBlock.of("%T.BOOLEAN", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.Int8 -> CodeBlock.of("%T.INT8", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.UInt8 -> CodeBlock.of("%T.UINT8", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.Int16 -> CodeBlock.of("%T.INT16", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.UInt16 -> CodeBlock.of("%T.UINT16", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.Int32 -> CodeBlock.of("%T.INT32", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.UInt32 -> CodeBlock.of("%T.UINT32", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.Int64 -> CodeBlock.of("%T.INT64", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.UInt64 -> CodeBlock.of("%T.UINT64", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.Float -> CodeBlock.of("%T.FLOAT", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.Double -> CodeBlock.of("%T.DOUBLE", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.Char16 -> CodeBlock.of("%T.CHAR16", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.GuidValue -> CodeBlock.of("%T.GUID", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.Struct -> CodeBlock.of("%T.STRUCT", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.Enum -> delegateEnumValueKindCode(typeBinding)
    KotlinProjectionAbiValueKind.Object -> CodeBlock.of("%T.OBJECT", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.GenericParameter -> CodeBlock.of("%T.OBJECT", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.ProjectedInterface,
    KotlinProjectionAbiValueKind.MappedAsyncAction,
    KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress,
    KotlinProjectionAbiValueKind.MappedAsyncOperation,
    KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress,
    KotlinProjectionAbiValueKind.MappedIterable,
    KotlinProjectionAbiValueKind.MappedVector,
    KotlinProjectionAbiValueKind.MappedMap,
    KotlinProjectionAbiValueKind.MappedVectorView,
    KotlinProjectionAbiValueKind.MappedMapView,
    KotlinProjectionAbiValueKind.UnknownReference -> CodeBlock.of("%T.IUNKNOWN", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.Array -> delegateArrayValueKindCode(typeBinding)
    KotlinProjectionAbiValueKind.ProjectedRuntimeClass,
    KotlinProjectionAbiValueKind.InspectableReference -> CodeBlock.of("%T.IINSPECTABLE", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    else -> error("Unsupported delegate callback ABI kind: ${typeBinding.describeAbiKind()}")
}

internal fun KotlinProjectionRenderer.delegateEnumValueKindCode(typeBinding: KotlinProjectionAbiTypeBinding): CodeBlock =
    typeBinding.enumUnderlyingType?.let(::integralDelegateValueKindCode)
        ?: error("Delegate enum ABI kind requires enum underlying type for ${typeBinding.resolvedTypeName}")

internal fun KotlinProjectionRenderer.delegateInvokeValueKindCode(typeBinding: KotlinProjectionAbiTypeBinding): CodeBlock = when (typeBinding.kind) {
    KotlinProjectionAbiValueKind.Unit -> CodeBlock.of("%T.UNIT", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.String -> CodeBlock.of("%T.HSTRING", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.Boolean -> CodeBlock.of("%T.BOOLEAN", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.Int8 -> CodeBlock.of("%T.INT8", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.UInt8 -> CodeBlock.of("%T.UINT8", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.Int16 -> CodeBlock.of("%T.INT16", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.UInt16 -> CodeBlock.of("%T.UINT16", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.Char16 -> CodeBlock.of("%T.CHAR16", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.Int32 -> CodeBlock.of("%T.INT32", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.UInt32 -> CodeBlock.of("%T.UINT32", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.Int64 -> CodeBlock.of("%T.INT64", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.UInt64 -> CodeBlock.of("%T.UINT64", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.Float -> CodeBlock.of("%T.FLOAT", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.Double -> CodeBlock.of("%T.DOUBLE", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.GuidValue -> CodeBlock.of("%T.GUID", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.Struct -> CodeBlock.of("%T.STRUCT", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.Enum -> delegateEnumValueKindCode(typeBinding)
    KotlinProjectionAbiValueKind.Object -> CodeBlock.of("%T.OBJECT", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.GenericParameter -> CodeBlock.of("%T.OBJECT", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.ProjectedInterface -> CodeBlock.of("%T.IUNKNOWN", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.ProjectedRuntimeClass -> CodeBlock.of("%T.IINSPECTABLE", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.MappedAsyncAction,
    KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress,
    KotlinProjectionAbiValueKind.MappedAsyncOperation,
    KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress,
    KotlinProjectionAbiValueKind.MappedIterable,
    KotlinProjectionAbiValueKind.MappedVector,
    KotlinProjectionAbiValueKind.MappedMap,
    KotlinProjectionAbiValueKind.MappedVectorView,
    KotlinProjectionAbiValueKind.MappedMapView -> CodeBlock.of("%T.IUNKNOWN", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.Array -> delegateArrayValueKindCode(typeBinding)
    KotlinProjectionAbiValueKind.UnknownReference -> CodeBlock.of("%T.IUNKNOWN", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.InspectableReference -> CodeBlock.of("%T.IINSPECTABLE", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    else -> error("Unsupported projected delegate ABI kind: ${typeBinding.describeAbiKind()}")
}

internal fun KotlinProjectionRenderer.delegateInvokeBodyCode(
    invokeShape: KotlinProjectionDelegateInvokeShape,
): CodeBlock {
    val collectionMarshalerBindings = invokeShape.parameterBindings.mapIndexedNotNull { index, parameterBinding ->
        delegateInvokeCollectionMarshalerCode(index, parameterBinding)
    }
    val argumentList = CodeBlock.builder()
        .add("listOf(")
        .apply {
            invokeShape.parameterBindings.forEachIndexed { index, parameterBinding ->
                if (index > 0) {
                    add(", ")
                }
                add("%L", delegateInvokeArgumentCode(parameterBinding))
            }
        }
        .add(")")
        .build()
    val nativeInvokeExpression = CodeBlock.of("__native.invoke(%L)", argumentList)
    val invokeCode = delegateInvokeReturnCode(invokeShape.returnBinding, nativeInvokeExpression)
    if (collectionMarshalerBindings.isEmpty()) {
        return invokeCode
    }
    return CodeBlock.builder()
        .apply {
            collectionMarshalerBindings.forEach { (name, initializer) ->
                addStatement("val %L = %L", name, initializer)
            }
        }
        .add("try {\n")
        .indent()
        .add("%L", invokeCode)
        .unindent()
        .add("} finally {\n")
        .indent()
        .apply {
            collectionMarshalerBindings.asReversed().forEach { (name, _) ->
                addStatement("%L?.close()", name)
            }
        }
        .unindent()
        .add("}\n")
        .build()
}

private data class DelegateCollectionMarshalerBinding(
    val name: String,
    val initializer: CodeBlock,
)

private fun KotlinProjectionRenderer.delegateInvokeCollectionMarshalerCode(
    index: Int,
    parameterBinding: KotlinProjectionAbiParameterBinding,
): DelegateCollectionMarshalerBinding? {
    val initializer = collectionMarshalerCode(parameterBinding.typeBinding, CodeBlock.of("%L", parameterBinding.name)) ?: return null
    return DelegateCollectionMarshalerBinding("__${parameterBinding.name}Marshaler", initializer)
}

internal fun KotlinProjectionRenderer.delegateInvokeArgumentCode(
    parameterBinding: KotlinProjectionAbiParameterBinding,
): CodeBlock = when (parameterBinding.typeBinding.kind) {
    KotlinProjectionAbiValueKind.String,
    KotlinProjectionAbiValueKind.Boolean,
    KotlinProjectionAbiValueKind.Int8,
    KotlinProjectionAbiValueKind.UInt8,
    KotlinProjectionAbiValueKind.Int16,
    KotlinProjectionAbiValueKind.UInt16,
    KotlinProjectionAbiValueKind.Char16,
    KotlinProjectionAbiValueKind.Int32,
    KotlinProjectionAbiValueKind.UInt32,
    KotlinProjectionAbiValueKind.Int64,
    KotlinProjectionAbiValueKind.UInt64,
    KotlinProjectionAbiValueKind.Float,
    KotlinProjectionAbiValueKind.Double,
    KotlinProjectionAbiValueKind.GuidValue,
    KotlinProjectionAbiValueKind.Struct,
    KotlinProjectionAbiValueKind.Object,
    KotlinProjectionAbiValueKind.GenericParameter,
    KotlinProjectionAbiValueKind.UnknownReference,
    KotlinProjectionAbiValueKind.InspectableReference,
    KotlinProjectionAbiValueKind.MappedAsyncAction,
    KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress,
    KotlinProjectionAbiValueKind.MappedAsyncOperation,
    KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress -> CodeBlock.of("%L", parameterBinding.name)
    KotlinProjectionAbiValueKind.MappedIterable,
    KotlinProjectionAbiValueKind.MappedVector,
    KotlinProjectionAbiValueKind.MappedMap,
    KotlinProjectionAbiValueKind.MappedVectorView,
    KotlinProjectionAbiValueKind.MappedMapView -> CodeBlock.of(
        "__%LMarshaler?.abi ?: %T.nullPointer",
        parameterBinding.name,
        PLATFORM_ABI_CLASS_NAME,
    )
    KotlinProjectionAbiValueKind.Array -> CodeBlock.of("%L", parameterBinding.name)
    KotlinProjectionAbiValueKind.ProjectedInterface,
    KotlinProjectionAbiValueKind.ProjectedRuntimeClass -> CodeBlock.of("%L", parameterBinding.name)
    KotlinProjectionAbiValueKind.Enum -> {
        val enumType = resolveTypeName(parameterBinding.typeBinding.resolvedTypeName)
        CodeBlock.of("%T.Metadata.toAbi(%L)", enumType, parameterBinding.name)
    }
    else -> error("Unsupported projected delegate parameter ABI kind: ${parameterBinding.typeBinding.describeAbiKind()}")
}

internal fun KotlinProjectionRenderer.delegateInvokeReturnCode(
    returnBinding: KotlinProjectionAbiTypeBinding,
    nativeInvokeExpression: CodeBlock,
): CodeBlock = when (returnBinding.kind) {
    KotlinProjectionAbiValueKind.Unit -> CodeBlock.of("%L\nreturn\n", nativeInvokeExpression)
    KotlinProjectionAbiValueKind.String -> CodeBlock.of("return %L as String\n", nativeInvokeExpression)
    KotlinProjectionAbiValueKind.Boolean -> CodeBlock.of("return %L as Boolean\n", nativeInvokeExpression)
    KotlinProjectionAbiValueKind.Int8 -> CodeBlock.of("return %L as Byte\n", nativeInvokeExpression)
    KotlinProjectionAbiValueKind.UInt8 -> CodeBlock.of("return %L as UByte\n", nativeInvokeExpression)
    KotlinProjectionAbiValueKind.Int16 -> CodeBlock.of("return %L as Short\n", nativeInvokeExpression)
    KotlinProjectionAbiValueKind.UInt16 -> CodeBlock.of("return %L as UShort\n", nativeInvokeExpression)
    KotlinProjectionAbiValueKind.Char16 -> CodeBlock.of("return %L as Char\n", nativeInvokeExpression)
    KotlinProjectionAbiValueKind.Int32 -> CodeBlock.of("return %L as Int\n", nativeInvokeExpression)
    KotlinProjectionAbiValueKind.UInt32 -> CodeBlock.of("return %L as UInt\n", nativeInvokeExpression)
    KotlinProjectionAbiValueKind.Int64 -> CodeBlock.of("return %L as Long\n", nativeInvokeExpression)
    KotlinProjectionAbiValueKind.UInt64 -> CodeBlock.of("return %L as ULong\n", nativeInvokeExpression)
    KotlinProjectionAbiValueKind.Float -> CodeBlock.of("return %L as Float\n", nativeInvokeExpression)
    KotlinProjectionAbiValueKind.Double -> CodeBlock.of("return %L as Double\n", nativeInvokeExpression)
    KotlinProjectionAbiValueKind.GuidValue -> CodeBlock.of("return %L as %T\n", nativeInvokeExpression, GUID_CLASS_NAME)
    KotlinProjectionAbiValueKind.Struct -> {
        val structType = nativeStructClassName(returnBinding) ?: error("Delegate struct return requires generated struct type for ${returnBinding.resolvedTypeName}")
        CodeBlock.of("return %L as %T\n", nativeInvokeExpression, structType)
    }
    KotlinProjectionAbiValueKind.Enum -> {
        val enumType = resolveTypeName(returnBinding.resolvedTypeName)
        val integralType = returnBinding.enumUnderlyingType
            ?: error("Delegate enum return binding requires enum underlying type for ${returnBinding.resolvedTypeName}")
        CodeBlock.of("return %T.Metadata.fromAbi(%L)\n", enumType, integralKotlinCastExpression(integralType, nativeInvokeExpression))
    }
    KotlinProjectionAbiValueKind.ProjectedInterface -> {
        val projectedType = resolveTypeName(returnBinding.resolvedTypeName)
        CodeBlock.of("return %T.Metadata.wrap(%L as %T)\n", projectedType, nativeInvokeExpression, IUNKNOWN_REFERENCE_CLASS_NAME)
    }
    KotlinProjectionAbiValueKind.ProjectedRuntimeClass -> {
        val projectedType = resolveTypeName(returnBinding.resolvedTypeName)
        CodeBlock.of("return %T.Metadata.wrap(%L as %T)\n", projectedType, nativeInvokeExpression, IINSPECTABLE_REFERENCE_CLASS_NAME)
    }
    KotlinProjectionAbiValueKind.UnknownReference ->
        CodeBlock.of("return %L as %T\n", nativeInvokeExpression, IUNKNOWN_REFERENCE_CLASS_NAME)
    KotlinProjectionAbiValueKind.Object ->
        CodeBlock.of("return %L as %T\n", nativeInvokeExpression, IINSPECTABLE_REFERENCE_CLASS_NAME)
    KotlinProjectionAbiValueKind.GenericParameter ->
        CodeBlock.of("return %L as %T\n", nativeInvokeExpression, resolveTypeName(returnBinding.typeName))
    KotlinProjectionAbiValueKind.InspectableReference ->
        CodeBlock.of("return %L as %T\n", nativeInvokeExpression, IINSPECTABLE_REFERENCE_CLASS_NAME)
    KotlinProjectionAbiValueKind.MappedAsyncAction ->
        CodeBlock.of("return %L as %T\n", nativeInvokeExpression, WINRT_ASYNC_ACTION_REFERENCE_CLASS_NAME)
    KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress -> {
        val progressType = returnBinding.typeArguments.singleOrNull()?.let { resolveTypeName(it.typeName) } ?: ANY.copy(nullable = true)
        CodeBlock.of("return %L as %T\n", nativeInvokeExpression, WINRT_ASYNC_ACTION_WITH_PROGRESS_REFERENCE_CLASS_NAME.parameterizedBy(progressType))
    }
    KotlinProjectionAbiValueKind.MappedAsyncOperation -> {
        val resultType = returnBinding.typeArguments.singleOrNull()?.let { resolveTypeName(it.typeName) } ?: ANY.copy(nullable = true)
        CodeBlock.of("return %L as %T\n", nativeInvokeExpression, WINRT_ASYNC_OPERATION_REFERENCE_CLASS_NAME.parameterizedBy(resultType))
    }
    KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress -> {
        val resultType = returnBinding.typeArguments.getOrNull(0)?.let { resolveTypeName(it.typeName) } ?: ANY.copy(nullable = true)
        val progressType = returnBinding.typeArguments.getOrNull(1)?.let { resolveTypeName(it.typeName) } ?: ANY.copy(nullable = true)
        CodeBlock.of("return %L as %T\n", nativeInvokeExpression, WINRT_ASYNC_OPERATION_WITH_PROGRESS_REFERENCE_CLASS_NAME.parameterizedBy(resultType, progressType))
    }
    KotlinProjectionAbiValueKind.MappedIterable,
    KotlinProjectionAbiValueKind.MappedVector,
    KotlinProjectionAbiValueKind.MappedMap,
    KotlinProjectionAbiValueKind.MappedVectorView,
    KotlinProjectionAbiValueKind.MappedMapView -> delegateCollectionReturnCode(returnBinding, nativeInvokeExpression)
    else -> error("Unsupported projected delegate return ABI kind: ${returnBinding.describeAbiKind()}")
}

internal fun KotlinProjectionRenderer.delegateCallbackArgumentCodeList(
    parameterBindings: List<KotlinProjectionAbiParameterBinding>,
): CodeBlock =
    CodeBlock.builder()
        .apply {
            parameterBindings.forEachIndexed { index, parameterBinding ->
                if (index > 0) {
                    add(", ")
                }
                add("%L", delegateCallbackArgumentCode(index, parameterBinding.typeBinding))
            }
        }
        .build()

internal fun KotlinProjectionRenderer.delegateCallbackArgumentCode(
    index: Int,
    typeBinding: KotlinProjectionAbiTypeBinding,
): CodeBlock = when (typeBinding.kind) {
    KotlinProjectionAbiValueKind.String -> CodeBlock.of("__args[%L] as String", index)
    KotlinProjectionAbiValueKind.Boolean -> CodeBlock.of("__args[%L] as Boolean", index)
    KotlinProjectionAbiValueKind.Int8 -> CodeBlock.of("__args[%L] as Byte", index)
    KotlinProjectionAbiValueKind.UInt8 -> CodeBlock.of("__args[%L] as UByte", index)
    KotlinProjectionAbiValueKind.Int16 -> CodeBlock.of("__args[%L] as Short", index)
    KotlinProjectionAbiValueKind.UInt16 -> CodeBlock.of("__args[%L] as UShort", index)
    KotlinProjectionAbiValueKind.Char16 -> CodeBlock.of("__args[%L] as Char", index)
    KotlinProjectionAbiValueKind.Int32 -> CodeBlock.of("__args[%L] as Int", index)
    KotlinProjectionAbiValueKind.UInt32 -> CodeBlock.of("__args[%L] as UInt", index)
    KotlinProjectionAbiValueKind.Int64 -> CodeBlock.of("__args[%L] as Long", index)
    KotlinProjectionAbiValueKind.UInt64 -> CodeBlock.of("__args[%L] as ULong", index)
    KotlinProjectionAbiValueKind.Float -> CodeBlock.of("__args[%L] as Float", index)
    KotlinProjectionAbiValueKind.Double -> CodeBlock.of("__args[%L] as Double", index)
    KotlinProjectionAbiValueKind.GuidValue -> CodeBlock.of("__args[%L] as %T", index, GUID_CLASS_NAME)
    KotlinProjectionAbiValueKind.Struct -> {
        val structType = nativeStructClassName(typeBinding) ?: error("Delegate struct callback binding requires generated struct type for ${typeBinding.resolvedTypeName}")
        CodeBlock.of("__args[%L] as %T", index, structType)
    }
    KotlinProjectionAbiValueKind.Enum -> delegateEnumCallbackArgumentCode(index, typeBinding)
    KotlinProjectionAbiValueKind.ProjectedInterface -> CodeBlock.of(
        "%T.Metadata.wrap(__args[%L] as %T)",
        resolveTypeName(typeBinding.resolvedTypeName),
        index,
        IUNKNOWN_REFERENCE_CLASS_NAME,
    )
    KotlinProjectionAbiValueKind.ProjectedRuntimeClass ->
        customObjectAbi(typeBinding)?.let { customAbi ->
            val projectedType = resolveTypeName(typeBinding.resolvedTypeName).copy(nullable = false)
            if (customAbi.fromAbiFunctionName == "objectFromAbi") {
                CodeBlock.of(
                    "%T.%L(%T.fromRawComPtr((__args[%L] as %T).getRefPointer()), %T(%S, %T(%S)), %T::class) ?: error(%S)",
                    WINRT_SYSTEM_PROJECTION_MARSHALERS_CLASS_NAME,
                    customAbi.fromAbiFunctionName,
                    PLATFORM_ABI_CLASS_NAME,
                    index,
                    IINSPECTABLE_REFERENCE_CLASS_NAME,
                    WINRT_TYPE_HANDLE_CLASS_NAME,
                    customAbi.typeHandleName,
                    GUID_CLASS_NAME,
                    customAbi.interfaceId.toString(),
                    projectedType,
                    "WINRT_E_NULL_ABI_DELEGATE_ARGUMENT",
                )
            } else {
                CodeBlock.of(
                    "%T.%L(%T.fromRawComPtr((__args[%L] as %T).getRefPointer())) ?: error(%S)",
                    WINRT_SYSTEM_PROJECTION_MARSHALERS_CLASS_NAME,
                    customAbi.fromAbiFunctionName,
                    PLATFORM_ABI_CLASS_NAME,
                    index,
                    IINSPECTABLE_REFERENCE_CLASS_NAME,
                    "WINRT_E_NULL_ABI_DELEGATE_ARGUMENT",
                )
            }
        } ?: if (mappedTypeByAbiName(typeBinding.resolvedTypeName)?.descriptionName == "PropertyChangedEventArgs") {
            CodeBlock.of(
                "%M(__args[%L])",
                WINRT_PROPERTY_CHANGED_EVENT_ARGS_FROM_ABI_FUNCTION_NAME,
                index,
            )
        } else {
            CodeBlock.of(
                "%T.Metadata.wrap(__args[%L] as %T)",
                resolveTypeName(typeBinding.resolvedTypeName),
                index,
                IINSPECTABLE_REFERENCE_CLASS_NAME,
            )
        }
    KotlinProjectionAbiValueKind.UnknownReference -> CodeBlock.of("__args[%L] as %T", index, IUNKNOWN_REFERENCE_CLASS_NAME)
    KotlinProjectionAbiValueKind.MappedAsyncAction -> CodeBlock.of("__args[%L] as %T", index, WINRT_ASYNC_ACTION_REFERENCE_CLASS_NAME)
    KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress -> {
        val progressType = typeBinding.typeArguments.singleOrNull()?.let { resolveTypeName(it.typeName) } ?: ANY.copy(nullable = true)
        CodeBlock.of("__args[%L] as %T", index, WINRT_ASYNC_ACTION_WITH_PROGRESS_REFERENCE_CLASS_NAME.parameterizedBy(progressType))
    }
    KotlinProjectionAbiValueKind.MappedAsyncOperation -> {
        val resultType = typeBinding.typeArguments.singleOrNull()?.let { resolveTypeName(it.typeName) } ?: ANY.copy(nullable = true)
        CodeBlock.of("__args[%L] as %T", index, WINRT_ASYNC_OPERATION_REFERENCE_CLASS_NAME.parameterizedBy(resultType))
    }
    KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress -> {
        val resultType = typeBinding.typeArguments.getOrNull(0)?.let { resolveTypeName(it.typeName) } ?: ANY.copy(nullable = true)
        val progressType = typeBinding.typeArguments.getOrNull(1)?.let { resolveTypeName(it.typeName) } ?: ANY.copy(nullable = true)
        CodeBlock.of("__args[%L] as %T", index, WINRT_ASYNC_OPERATION_WITH_PROGRESS_REFERENCE_CLASS_NAME.parameterizedBy(resultType, progressType))
    }
    KotlinProjectionAbiValueKind.MappedIterable,
    KotlinProjectionAbiValueKind.MappedVector,
    KotlinProjectionAbiValueKind.MappedMap,
    KotlinProjectionAbiValueKind.MappedVectorView,
    KotlinProjectionAbiValueKind.MappedMapView -> delegateCollectionCallbackArgumentCode(index, typeBinding)
    KotlinProjectionAbiValueKind.Array -> CodeBlock.of("__args[%L] as %T", index, resolveTypeName(typeBinding.typeName))
    KotlinProjectionAbiValueKind.GenericParameter -> CodeBlock.of("__args[%L] as %T", index, resolveTypeName(typeBinding.typeName))
    KotlinProjectionAbiValueKind.Object -> CodeBlock.of("__args[%L]", index)
    KotlinProjectionAbiValueKind.InspectableReference -> CodeBlock.of("__args[%L] as %T", index, IINSPECTABLE_REFERENCE_CLASS_NAME)
    else -> error("Unsupported delegate callback ABI kind: ${typeBinding.describeAbiKind()}")
}

internal fun KotlinProjectionRenderer.delegateArrayValueKindCode(typeBinding: KotlinProjectionAbiTypeBinding): CodeBlock {
    val elementKind = typeBinding.typeArguments.singleOrNull()?.kind
    if (elementKind == KotlinProjectionAbiValueKind.UInt8) {
        return CodeBlock.of("%T.UINT8_ARRAY", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    }
    error("Unsupported delegate array ABI kind: ${typeBinding.describeAbiKind()}")
}

internal fun KotlinProjectionRenderer.delegateCollectionCallbackArgumentCode(
    index: Int,
    typeBinding: KotlinProjectionAbiTypeBinding,
): CodeBlock {
    val initializer =
        readOnlyCollectionBindingForReturn(typeBinding)?.let(::renderReadOnlyCollectionDelegateInitializer)
            ?: mutableCollectionBindingForReturn(typeBinding)?.let(::renderMutableCollectionDelegateInitializer)
            ?: error("Delegate collection callback binding requires runtime collection projection for ${typeBinding.describeAbiKind()}")
    return CodeBlock.of(
        "run {\nval __collectionRef = __args[%L] as %T\n%L}",
        index,
        IUNKNOWN_REFERENCE_CLASS_NAME,
        initializer,
    )
}

internal fun KotlinProjectionRenderer.delegateCollectionReturnCode(
    returnBinding: KotlinProjectionAbiTypeBinding,
    nativeInvokeExpression: CodeBlock,
): CodeBlock {
    val initializer =
        readOnlyCollectionBindingForReturn(returnBinding)?.let(::renderReadOnlyCollectionDelegateInitializer)
            ?: mutableCollectionBindingForReturn(returnBinding)?.let(::renderMutableCollectionDelegateInitializer)
            ?: error("Delegate collection return binding requires runtime collection projection for ${returnBinding.describeAbiKind()}")
    return CodeBlock.of(
        "val __collectionRef = %L as %T\nreturn %L",
        nativeInvokeExpression,
        IUNKNOWN_REFERENCE_CLASS_NAME,
        initializer,
    )
}

internal fun KotlinProjectionRenderer.collectionMarshalerCode(
    typeBinding: KotlinProjectionAbiTypeBinding,
    valueExpression: CodeBlock,
): CodeBlock? {
    readOnlyCollectionBindingForReturn(typeBinding)?.let { binding ->
        return readOnlyCollectionMarshalerCode(binding, valueExpression)
    }
    mutableCollectionBindingForReturn(typeBinding)?.let { binding ->
        return mutableCollectionMarshalerCode(binding, valueExpression)
    }
    return null
}

private fun KotlinProjectionRenderer.readOnlyCollectionMarshalerCode(
    binding: KotlinProjectionReadOnlyCollectionBinding,
    valueExpression: CodeBlock,
): CodeBlock? =
    when (binding.kind) {
        KotlinProjectionReadOnlyCollectionKind.Iterable -> {
            val elementAdapter = collectionReferenceAdapterCode(requireNotNull(binding.elementBinding)) ?: return null
            CodeBlock.of("%T.createMarshaler(%L, %L)", WINRT_ITERABLE_PROJECTION_CLASS_NAME, valueExpression, elementAdapter)
        }
        KotlinProjectionReadOnlyCollectionKind.VectorView -> {
            val elementAdapter = collectionReferenceAdapterCode(requireNotNull(binding.elementBinding)) ?: return null
            CodeBlock.of("%T.createMarshaler(%L, %L)", WINRT_READ_ONLY_LIST_PROJECTION_CLASS_NAME, valueExpression, elementAdapter)
        }
        KotlinProjectionReadOnlyCollectionKind.MapView -> {
            val keyAdapter = collectionReferenceAdapterCode(requireNotNull(binding.keyBinding)) ?: return null
            val valueAdapter = collectionReferenceAdapterCode(requireNotNull(binding.valueBinding)) ?: return null
            CodeBlock.of("%T.createMarshaler(%L, %L, %L)", WINRT_READ_ONLY_DICTIONARY_PROJECTION_CLASS_NAME, valueExpression, keyAdapter, valueAdapter)
        }
    }

private fun KotlinProjectionRenderer.mutableCollectionMarshalerCode(
    binding: KotlinProjectionMutableCollectionBinding,
    valueExpression: CodeBlock,
): CodeBlock? =
    when (binding.kind) {
        KotlinProjectionMutableCollectionKind.Vector -> {
            val elementAdapter = collectionReferenceAdapterCode(requireNotNull(binding.elementBinding)) ?: return null
            CodeBlock.of("%T.createMarshaler(%L, %L)", WINRT_LIST_PROJECTION_CLASS_NAME, valueExpression, elementAdapter)
        }
        KotlinProjectionMutableCollectionKind.Map -> {
            val keyAdapter = collectionReferenceAdapterCode(requireNotNull(binding.keyBinding)) ?: return null
            val valueAdapter = collectionReferenceAdapterCode(requireNotNull(binding.valueBinding)) ?: return null
            CodeBlock.of("%T.createMarshaler(%L, %L, %L)", WINRT_DICTIONARY_PROJECTION_CLASS_NAME, valueExpression, keyAdapter, valueAdapter)
        }
    }

internal fun KotlinProjectionRenderer.delegateEnumCallbackArgumentCode(
    index: Int,
    typeBinding: KotlinProjectionAbiTypeBinding,
): CodeBlock {
    val integralType = typeBinding.enumUnderlyingType
        ?: error("Delegate enum callback binding requires enum underlying type for ${typeBinding.resolvedTypeName}")
    val enumType = resolveTypeName(typeBinding.resolvedTypeName)
    return CodeBlock.of("%T.Metadata.fromAbi(%L)", enumType, integralKotlinCastExpression(integralType, CodeBlock.of("__args[%L]", index)))
}

internal fun KotlinProjectionRenderer.delegateParameterStructAdaptersCode(
    parameterBindings: List<KotlinProjectionAbiParameterBinding>,
): CodeBlock {
    val adapters = parameterBindings.map { binding ->
        if (binding.typeBinding.kind == KotlinProjectionAbiValueKind.Struct) {
            nativeStructClassName(binding.typeBinding)?.let { CodeBlock.of("%T.Metadata", it) }
                ?: error("Delegate struct parameter requires generated struct type for ${binding.typeBinding.resolvedTypeName}")
        } else {
            CodeBlock.of("null")
        }
    }
    if (adapters.all { it.toString() == "null" }) {
        return CodeBlock.of("emptyList()")
    }
    return CodeBlock.builder()
        .add("listOf(")
        .apply {
            adapters.forEachIndexed { index, adapter ->
                if (index > 0) {
                    add(", ")
                }
                add("%L", adapter)
            }
        }
        .add(")")
        .build()
}

internal fun KotlinProjectionRenderer.delegateReturnStructAdapterCode(
    returnBinding: KotlinProjectionAbiTypeBinding,
): CodeBlock =
    if (returnBinding.kind == KotlinProjectionAbiValueKind.Struct) {
        nativeStructClassName(returnBinding)?.let { CodeBlock.of("%T.Metadata", it) }
            ?: error("Delegate struct return requires generated struct type for ${returnBinding.resolvedTypeName}")
    } else {
        CodeBlock.of("null")
    }
