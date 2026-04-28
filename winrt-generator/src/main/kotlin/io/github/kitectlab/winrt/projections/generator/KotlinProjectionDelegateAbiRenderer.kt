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
    val handleName = "__${parameterBinding.name}Handle"
    val abiReferenceName = "__${parameterBinding.name}Abi"
    return KotlinProjectionAbiMarshalerPlan(
        name = parameterBinding.name,
        typeBinding = parameterBinding.typeBinding,
        isReturn = false,
        abiArgumentExpression = CodeBlock.of("%L.pointer", abiReferenceName),
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

internal fun abiArgumentKindForIntegralType(type: WinRtIntegralType): KotlinProjectionComArgumentKind =
    when (type) {
        WinRtIntegralType.Int8,
        WinRtIntegralType.UInt8 -> KotlinProjectionComArgumentKind.Int8
        WinRtIntegralType.Int16,
        WinRtIntegralType.UInt16 -> KotlinProjectionComArgumentKind.Int16
        WinRtIntegralType.Int32,
        WinRtIntegralType.UInt32 -> KotlinProjectionComArgumentKind.Int32
        WinRtIntegralType.Int64,
        WinRtIntegralType.UInt64 -> KotlinProjectionComArgumentKind.Int64
    }

internal fun KotlinProjectionRenderer.outboundDelegateInvokeShape(
    typeBinding: KotlinProjectionAbiTypeBinding,
): KotlinProjectionDelegateInvokeShape? {
    val invokeShape = typeBinding.delegateInvokeShape ?: return null
    if (typeBinding.resolvedTypeName == "Windows.Foundation.EventHandler" && typeBinding.typeArguments.size == 1) {
        return invokeShape.copy(
            parameterBindings = listOf(
                KotlinProjectionAbiParameterBinding(
                    "sender",
                    KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Object, "Any", "System.Object"),
                ),
                KotlinProjectionAbiParameterBinding("args", typeBinding.typeArguments[0]),
            ),
        )
    }
    if (typeBinding.resolvedTypeName == "Windows.Foundation.TypedEventHandler" && typeBinding.typeArguments.size == 2) {
        return invokeShape.copy(
            parameterBindings = listOf(
                KotlinProjectionAbiParameterBinding("sender", typeBinding.typeArguments[0]),
                KotlinProjectionAbiParameterBinding("args", typeBinding.typeArguments[1]),
            ),
        )
    }
    return invokeShape
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
        .addStatement("return %T.Metadata.fromAbi(__enumValue)", enumType)
        .build()
}

internal fun KotlinProjectionRenderer.abiResultAllocationForIntegralType(type: WinRtIntegralType): CodeBlock =
    when (type) {
        WinRtIntegralType.Int8,
        WinRtIntegralType.UInt8 -> CodeBlock.of("%T.allocateInt8Slot(__scope)", PLATFORM_ABI_CLASS_NAME)
        WinRtIntegralType.Int16,
        WinRtIntegralType.UInt16 -> CodeBlock.of("%T.allocateBytes(__scope, 2)", PLATFORM_ABI_CLASS_NAME)
        WinRtIntegralType.Int32,
        WinRtIntegralType.UInt32 -> CodeBlock.of("%T.allocateInt32Slot(__scope)", PLATFORM_ABI_CLASS_NAME)
        WinRtIntegralType.Int64,
        WinRtIntegralType.UInt64 -> CodeBlock.of("%T.allocateInt64Slot(__scope)", PLATFORM_ABI_CLASS_NAME)
    }

internal fun KotlinProjectionRenderer.abiIntegralArgumentConversionSuffix(type: WinRtIntegralType): String =
    integralAbiDescriptor(type).argumentConversionSuffix

internal fun KotlinProjectionRenderer.abiIntegralReadbackExpression(type: WinRtIntegralType): CodeBlock =
    when (type) {
        WinRtIntegralType.Int8 -> CodeBlock.of("%T.readInt8(__resultOut)", PLATFORM_ABI_CLASS_NAME)
        WinRtIntegralType.UInt8 -> CodeBlock.of("%T.readInt8(__resultOut).toUByte()", PLATFORM_ABI_CLASS_NAME)
        WinRtIntegralType.Int16 -> CodeBlock.of("%T.readInt16(__resultOut)", PLATFORM_ABI_CLASS_NAME)
        WinRtIntegralType.UInt16 -> CodeBlock.of("%T.readInt16(__resultOut).toUShort()", PLATFORM_ABI_CLASS_NAME)
        WinRtIntegralType.Int32 -> CodeBlock.of("%T.readInt32(__resultOut)", PLATFORM_ABI_CLASS_NAME)
        WinRtIntegralType.UInt32 -> CodeBlock.of("%T.readInt32(__resultOut).toUInt()", PLATFORM_ABI_CLASS_NAME)
        WinRtIntegralType.Int64 -> CodeBlock.of("%T.readInt64(__resultOut)", PLATFORM_ABI_CLASS_NAME)
        WinRtIntegralType.UInt64 -> CodeBlock.of("%T.readInt64(__resultOut).toULong()", PLATFORM_ABI_CLASS_NAME)
    }

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
): CodeBlock =
    CodeBlock.of(
        "%T(interfaceId = %T(%S), parameterKinds = %L, returnKind = %L, parameterStructAdapters = %L, returnStructAdapter = %L)",
        WINRT_DELEGATE_DESCRIPTOR_CLASS_NAME,
        GUID_CLASS_NAME,
        invokeShape.interfaceId.toString(),
        delegateInvokeParameterKindsCode(invokeShape.parameterBindings),
        delegateInvokeReturnKindCode(invokeShape.returnBinding),
        delegateParameterStructAdaptersCode(invokeShape.parameterBindings),
        delegateReturnStructAdapterCode(invokeShape.returnBinding),
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
    KotlinProjectionAbiValueKind.Object,
    KotlinProjectionAbiValueKind.ProjectedInterface,
    KotlinProjectionAbiValueKind.ProjectedRuntimeClass,
    KotlinProjectionAbiValueKind.MappedAsyncAction,
    KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress,
    KotlinProjectionAbiValueKind.MappedAsyncOperation,
    KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress,
    KotlinProjectionAbiValueKind.UnknownReference,
    KotlinProjectionAbiValueKind.InspectableReference -> CodeBlock.of("%T.OBJECT", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    else -> error("Unsupported delegate callback ABI kind: ${typeBinding.describeAbiKind()}")
}

internal fun KotlinProjectionRenderer.delegateEnumValueKindCode(typeBinding: KotlinProjectionAbiTypeBinding): CodeBlock =
    when (typeBinding.enumUnderlyingType) {
        WinRtIntegralType.Int8 -> CodeBlock.of("%T.INT8", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        WinRtIntegralType.UInt8 -> CodeBlock.of("%T.UINT8", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        WinRtIntegralType.Int16 -> CodeBlock.of("%T.INT16", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        WinRtIntegralType.UInt16 -> CodeBlock.of("%T.UINT16", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        WinRtIntegralType.Int32 -> CodeBlock.of("%T.INT32", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        WinRtIntegralType.UInt32 -> CodeBlock.of("%T.UINT32", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        WinRtIntegralType.Int64 -> CodeBlock.of("%T.INT64", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        WinRtIntegralType.UInt64 -> CodeBlock.of("%T.UINT64", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        null -> error("Delegate enum ABI kind requires enum underlying type for ${typeBinding.resolvedTypeName}")
    }

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
    KotlinProjectionAbiValueKind.ProjectedInterface -> CodeBlock.of("%T.IUNKNOWN", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.ProjectedRuntimeClass -> CodeBlock.of("%T.IINSPECTABLE", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.MappedAsyncAction,
    KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress,
    KotlinProjectionAbiValueKind.MappedAsyncOperation,
    KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress -> CodeBlock.of("%T.IUNKNOWN", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.UnknownReference -> CodeBlock.of("%T.IUNKNOWN", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    KotlinProjectionAbiValueKind.InspectableReference -> CodeBlock.of("%T.IINSPECTABLE", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
    else -> error("Unsupported projected delegate ABI kind: ${typeBinding.describeAbiKind()}")
}

internal fun KotlinProjectionRenderer.delegateInvokeBodyCode(
    invokeShape: KotlinProjectionDelegateInvokeShape,
): CodeBlock {
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
    return delegateInvokeReturnCode(invokeShape.returnBinding, nativeInvokeExpression)
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
    KotlinProjectionAbiValueKind.UnknownReference,
    KotlinProjectionAbiValueKind.InspectableReference,
    KotlinProjectionAbiValueKind.MappedAsyncAction,
    KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress,
    KotlinProjectionAbiValueKind.MappedAsyncOperation,
    KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress -> CodeBlock.of("%L", parameterBinding.name)
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
        when (returnBinding.enumUnderlyingType) {
            WinRtIntegralType.Int8 -> CodeBlock.of("return %T.Metadata.fromAbi(%L as Byte)\n", enumType, nativeInvokeExpression)
            WinRtIntegralType.UInt8 -> CodeBlock.of("return %T.Metadata.fromAbi(%L as UByte)\n", enumType, nativeInvokeExpression)
            WinRtIntegralType.Int16 -> CodeBlock.of("return %T.Metadata.fromAbi(%L as Short)\n", enumType, nativeInvokeExpression)
            WinRtIntegralType.UInt16 -> CodeBlock.of("return %T.Metadata.fromAbi(%L as UShort)\n", enumType, nativeInvokeExpression)
            WinRtIntegralType.Int32 -> CodeBlock.of("return %T.Metadata.fromAbi(%L as Int)\n", enumType, nativeInvokeExpression)
            WinRtIntegralType.UInt32 -> CodeBlock.of("return %T.Metadata.fromAbi(%L as UInt)\n", enumType, nativeInvokeExpression)
            WinRtIntegralType.Int64 -> CodeBlock.of("return %T.Metadata.fromAbi(%L as Long)\n", enumType, nativeInvokeExpression)
            WinRtIntegralType.UInt64 -> CodeBlock.of("return %T.Metadata.fromAbi(%L as ULong)\n", enumType, nativeInvokeExpression)
            null -> error("Delegate enum return binding requires enum underlying type for ${returnBinding.resolvedTypeName}")
        }
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
    KotlinProjectionAbiValueKind.ProjectedRuntimeClass -> CodeBlock.of(
        "%T.Metadata.wrap((__args[%L] as %T).asInspectable())",
        resolveTypeName(typeBinding.resolvedTypeName),
        index,
        IUNKNOWN_REFERENCE_CLASS_NAME,
    )
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
    KotlinProjectionAbiValueKind.Object,
    KotlinProjectionAbiValueKind.InspectableReference -> CodeBlock.of(
        "(__args[%L] as %T).asInspectable()",
        index,
        IUNKNOWN_REFERENCE_CLASS_NAME,
    )
    else -> error("Unsupported delegate callback ABI kind: ${typeBinding.describeAbiKind()}")
}

internal fun KotlinProjectionRenderer.delegateEnumCallbackArgumentCode(
    index: Int,
    typeBinding: KotlinProjectionAbiTypeBinding,
): CodeBlock {
    val integralType = typeBinding.enumUnderlyingType
        ?: error("Delegate enum callback binding requires enum underlying type for ${typeBinding.resolvedTypeName}")
    val enumType = resolveTypeName(typeBinding.resolvedTypeName)
    return when (integralType) {
        WinRtIntegralType.Int8 -> CodeBlock.of("%T.Metadata.fromAbi(__args[%L] as Byte)", enumType, index)
        WinRtIntegralType.UInt8 -> CodeBlock.of("%T.Metadata.fromAbi(__args[%L] as UByte)", enumType, index)
        WinRtIntegralType.Int16 -> CodeBlock.of("%T.Metadata.fromAbi(__args[%L] as Short)", enumType, index)
        WinRtIntegralType.UInt16 -> CodeBlock.of("%T.Metadata.fromAbi(__args[%L] as UShort)", enumType, index)
        WinRtIntegralType.Int32 -> CodeBlock.of("%T.Metadata.fromAbi(__args[%L] as Int)", enumType, index)
        WinRtIntegralType.UInt32 -> CodeBlock.of("%T.Metadata.fromAbi(__args[%L] as UInt)", enumType, index)
        WinRtIntegralType.Int64 -> CodeBlock.of("%T.Metadata.fromAbi(__args[%L] as Long)", enumType, index)
        WinRtIntegralType.UInt64 -> CodeBlock.of("%T.Metadata.fromAbi(__args[%L] as ULong)", enumType, index)
    }
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
