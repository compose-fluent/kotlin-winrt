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

internal fun KotlinProjectionRenderer.buildAbiCallPlan(
    binding: KotlinProjectionInstanceMemberBinding,
): KotlinProjectionAbiCallPlan? =
    buildAbiCallPlan(binding.returnBinding, binding.parameterBindings, binding.marshalerPlanDescriptor)

internal fun KotlinProjectionRenderer.buildAbiCallPlan(
    returnBinding: KotlinProjectionAbiTypeBinding,
    parameterBindings: List<KotlinProjectionAbiParameterBinding>,
    marshalerPlanDescriptor: WinRtAbiMarshalerPlanDescriptor? = null,
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
    )
}

internal fun KotlinProjectionRenderer.requireAbiCallPlan(
    bindingName: String,
    returnBinding: KotlinProjectionAbiTypeBinding,
    parameterBindings: List<KotlinProjectionAbiParameterBinding>,
    marshalerPlanDescriptor: WinRtAbiMarshalerPlanDescriptor? = null,
): KotlinProjectionAbiCallPlan {
    return requireNotNull(buildAbiCallPlan(returnBinding, parameterBindings, marshalerPlanDescriptor)) {
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
    return when (parameterBinding.typeBinding.kind) {
        KotlinProjectionAbiValueKind.String -> KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%L.handle", abiLocalName),
            scopeOpeners = listOf(
                CodeBlock.of("%T.create(%L).use { %L ->", HSTRING_CLASS_NAME, parameterName, abiLocalName),
            ),
        )
        KotlinProjectionAbiValueKind.Boolean -> KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("if (%L) 1 else 0", parameterName),
        )
        KotlinProjectionAbiValueKind.Int8 -> KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%L", parameterName),
        )
        KotlinProjectionAbiValueKind.UInt8 -> KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%L.toByte()", parameterName),
        )
        KotlinProjectionAbiValueKind.Int16 -> KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%L", parameterName),
        )
        KotlinProjectionAbiValueKind.UInt16 -> KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%L.toShort()", parameterName),
        )
        KotlinProjectionAbiValueKind.Double -> KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%L", parameterName),
        )
        KotlinProjectionAbiValueKind.UInt32 -> KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%L.toInt()", parameterName),
        )
        KotlinProjectionAbiValueKind.Int32 -> KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%L", parameterName),
        )
        KotlinProjectionAbiValueKind.Int64 -> KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%L", parameterName),
        )
        KotlinProjectionAbiValueKind.UInt64 -> KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%L.toLong()", parameterName),
        )
        KotlinProjectionAbiValueKind.Float -> KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%L", parameterName),
        )
        KotlinProjectionAbiValueKind.Char16 -> KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%L", parameterName),
        )
        KotlinProjectionAbiValueKind.GuidValue -> {
            val scopeName = "__${parameterName}GuidScope"
            val abiLocalName = "__${parameterName}Abi"
            KotlinProjectionAbiMarshalerPlan(
                name = parameterName,
                typeBinding = parameterBinding.typeBinding,
                isReturn = false,
                abiArgumentExpression = CodeBlock.of("%L", abiLocalName),
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
        KotlinProjectionAbiValueKind.ProjectedInterface -> KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%T.fromRawComPtr((%L as %T).nativeObject.pointer)", PLATFORM_ABI_CLASS_NAME, parameterName, IWINRT_OBJECT_CLASS_NAME),
        )
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass -> KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%T.fromRawComPtr((%L as %T).nativeObject.pointer)", PLATFORM_ABI_CLASS_NAME, parameterName, IWINRT_OBJECT_CLASS_NAME),
        )
        KotlinProjectionAbiValueKind.Object,
        KotlinProjectionAbiValueKind.UnknownReference,
        KotlinProjectionAbiValueKind.InspectableReference -> KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%T.fromRawComPtr(%L.pointer)", PLATFORM_ABI_CLASS_NAME, parameterName),
        )
        else -> null
    }
}

internal fun KotlinProjectionRenderer.buildAbiReturnMarshaler(
    returnBinding: KotlinProjectionAbiTypeBinding,
    descriptor: WinRtAbiMarshalerSlotDescriptor? = null,
): KotlinProjectionAbiMarshalerPlan? {
    if (returnBinding.kind == KotlinProjectionAbiValueKind.Array) {
        return arrayReturnMarshaler(returnBinding, descriptor)
    }
    val resultOutLayout = when {
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
            nativeStructClassName(returnBinding)?.let { returnType ->
                CodeBlock.of("%T.allocateBytes(__scope, %T.Metadata.layout.sizeBytes)", PLATFORM_ABI_CLASS_NAME, returnType)
            } ?: return null
        }
    }
    val readbackStatement = when {
        returnBinding.isMappedCollectionBinding() -> mappedCollectionReturnReadback(returnBinding)
        returnBinding.isMappedBindableCollectionBinding() -> bindableCollectionReturnReadback(returnBinding)
        else -> when (returnBinding.kind) {
        KotlinProjectionAbiValueKind.String ->
            CodeBlock.of(
                "return %T.fromHandle(%T.readPointer(__resultOut), owner = true).use { it.toKString() }\n",
                HSTRING_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
            )
        KotlinProjectionAbiValueKind.MappedAsyncAction ->
            CodeBlock.of(
                "return %T(%T.readPointer(__resultOut))\n",
                WINRT_ASYNC_ACTION_REFERENCE_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
            )
        KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress ->
            asyncActionWithProgressReturnReadback(returnBinding) ?: return null
        KotlinProjectionAbiValueKind.MappedAsyncOperation ->
            asyncOperationReturnReadback(returnBinding) ?: return null
        KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress ->
            asyncOperationWithProgressReturnReadback(returnBinding) ?: return null
        KotlinProjectionAbiValueKind.Boolean ->
            CodeBlock.of("return %T.readInt8(__resultOut).toInt() != 0\n", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int8 ->
            CodeBlock.of("return %T.readInt8(__resultOut)\n", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.UInt8 ->
            CodeBlock.of("return %T.readInt8(__resultOut).toUByte()\n", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int16 ->
            CodeBlock.of("return %T.readInt16(__resultOut)\n", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.UInt16 ->
            CodeBlock.of("return %T.readInt16(__resultOut).toUShort()\n", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int32 ->
            CodeBlock.of("return %T.readInt32(__resultOut)\n", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.UInt32 ->
            CodeBlock.of("return %T.readInt32(__resultOut).toUInt()\n", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int64 ->
            CodeBlock.of("return %T.readInt64(__resultOut)\n", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.UInt64 ->
            CodeBlock.of("return %T.readInt64(__resultOut).toULong()\n", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Float ->
            CodeBlock.of("return %T.readFloat(__resultOut)\n", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Double ->
            CodeBlock.of("return %T.readDouble(__resultOut)\n", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Char16 ->
            CodeBlock.of("return %T.readChar16(__resultOut)\n", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.GuidValue ->
            CodeBlock.of("return %T.readGuid(__resultOut)\n", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Reference ->
            referenceReturnReadback(returnBinding, WINRT_REFERENCE_PROJECTION_CLASS_NAME) ?: return null
        KotlinProjectionAbiValueKind.ReferenceArray ->
            referenceReturnReadback(returnBinding, WINRT_REFERENCE_ARRAY_PROJECTION_CLASS_NAME) ?: return null
        KotlinProjectionAbiValueKind.Enum ->
            enumReturnReadback(returnBinding, resolvedReturnClassName(returnBinding))
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass ->
            resolvedReturnClassName(returnBinding)?.let { returnType ->
                CodeBlock.of(
                    "val __resultRef = %T(%T.toRawComPtr(%T.readPointer(__resultOut)))\nreturn %T.Metadata.wrap(__resultRef.asInspectable())\n",
                    IUNKNOWN_REFERENCE_CLASS_NAME,
                    PLATFORM_ABI_CLASS_NAME,
                    PLATFORM_ABI_CLASS_NAME,
                    returnType,
                )
            }
        KotlinProjectionAbiValueKind.ProjectedInterface ->
            resolvedReturnClassName(returnBinding)?.let { returnType ->
                CodeBlock.of(
                    "return %T.Metadata.wrap(%T(%T.toRawComPtr(%T.readPointer(__resultOut))))\n",
                    returnType,
                    IUNKNOWN_REFERENCE_CLASS_NAME,
                    PLATFORM_ABI_CLASS_NAME,
                    PLATFORM_ABI_CLASS_NAME,
                )
            }
        KotlinProjectionAbiValueKind.InspectableReference ->
            if (resolvedReturnClassName(returnBinding) == IINSPECTABLE_REFERENCE_CLASS_NAME) {
                CodeBlock.of(
                "return (%T(%T.toRawComPtr(%T.readPointer(__resultOut))).use({ it.asInspectable() }))\n",
                    IUNKNOWN_REFERENCE_CLASS_NAME,
                    PLATFORM_ABI_CLASS_NAME,
                    PLATFORM_ABI_CLASS_NAME,
                )
            } else {
                return null
            }
        KotlinProjectionAbiValueKind.Object ->
            CodeBlock.of(
                "return (%T(%T.toRawComPtr(%T.readPointer(__resultOut))).use { it.asInspectable() })\n",
                IUNKNOWN_REFERENCE_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
            )
        KotlinProjectionAbiValueKind.UnknownReference ->
            if (resolvedReturnClassName(returnBinding) == IUNKNOWN_REFERENCE_CLASS_NAME) {
                CodeBlock.of(
                    "return %T(%T.toRawComPtr(%T.readPointer(__resultOut)))\n",
                    IUNKNOWN_REFERENCE_CLASS_NAME,
                    PLATFORM_ABI_CLASS_NAME,
                    PLATFORM_ABI_CLASS_NAME,
                )
            } else {
                return null
            }
        KotlinProjectionAbiValueKind.Delegate ->
            resolvedReturnClassName(returnBinding)?.let { returnType ->
                CodeBlock.of(
                    "return %T.Metadata.fromAbi(%T.readPointer(__resultOut)) ?: error(%S)\n",
                    returnType,
                    PLATFORM_ABI_CLASS_NAME,
                    "Expected non-null delegate instance from ABI return for ${returnBinding.resolvedTypeName}.",
                )
            }
        KotlinProjectionAbiValueKind.Struct ->
            nativeStructClassName(returnBinding)?.let { returnType ->
                CodeBlock.of("return %T.Metadata.fromAbi(__resultOut)\n", returnType)
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
        resultAllocation = resultOutLayout,
        readbackStatement = readbackStatement,
    )
}

internal fun KotlinProjectionRenderer.resolvedReturnClassName(
    returnBinding: KotlinProjectionAbiTypeBinding,
): ClassName? =
    runCatching { resolveTypeName(returnBinding.typeName) as? ClassName }.getOrNull()
        ?: runCatching { resolveTypeName(returnBinding.resolvedTypeName) as? ClassName }.getOrNull()

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
        extraAbiArgumentExpressions = listOf(CodeBlock.of("__resultDataOut")),
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
        extraAbiArgumentExpressions = listOf(CodeBlock.of("__resultDataOut")),
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
            nativeStructClassName(elementBinding)?.let { CodeBlock.of("%T.Metadata.layout.sizeBytes", it) }
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
            nativeStructClassName(elementBinding)?.let { CodeBlock.of("%T.Metadata.fromAbi(%L)", it, slice) }
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
            nativeStructClassName(elementBinding)?.let { CodeBlock.of("%T.Metadata.copyTo(%L, %L)", it, valueExpression, slice) }
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
    val structType = nativeStructClassName(parameterBinding.typeBinding) ?: return null
    val parameterName = parameterBinding.name
    val scopeName = "__${parameterName}StructScope"
    val abiLocalName = "__${parameterName}Abi"
    return KotlinProjectionAbiMarshalerPlan(
        name = parameterName,
        typeBinding = parameterBinding.typeBinding,
        isReturn = false,
        abiArgumentExpression = CodeBlock.of("%L", abiLocalName),
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
            extraAbiArgumentExpressions = listOf(CodeBlock.of("%L", dataOutName)),
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
        extraAbiArgumentExpressions = listOf(CodeBlock.of("%L", dataName)),
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
        extraAbiArgumentExpressions = listOf(CodeBlock.of("%L?.data ?: %T.nullPointer", arrayName, PLATFORM_ABI_CLASS_NAME)),
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

internal fun KotlinProjectionRenderer.enumParameterMarshaler(
    parameterBinding: KotlinProjectionAbiParameterBinding,
): KotlinProjectionAbiMarshalerPlan? {
    val integralType = parameterBinding.typeBinding.enumUnderlyingType ?: return null
    return KotlinProjectionAbiMarshalerPlan(
        name = parameterBinding.name,
        typeBinding = parameterBinding.typeBinding,
        isReturn = false,
        abiArgumentExpression = CodeBlock.of("%L.abiValue%L", parameterBinding.name, abiIntegralArgumentConversionSuffix(integralType)),
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
        scopeOpeners = listOf(
            CodeBlock.of(
                "%T.createDelegate(iid = %L, parameterKinds = %L, returnKind = %L) { __args ->\n%L(%L)\n}.use { %L ->",
                WINRT_DELEGATE_BRIDGE_CLASS_NAME,
                delegateIid,
                delegateParameterKindsCode(invokeShape.parameterBindings),
                delegateInvokeReturnKindCode(invokeShape.returnBinding),
                parameterBinding.name,
                delegateCallbackArgumentCodeList(invokeShape.parameterBindings),
                handleName,
            ),
            CodeBlock.of("%L.createReference().use { %L ->", handleName, abiReferenceName),
        ),
    )
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
    return CodeBlock.of(
        "return %T.Metadata.fromAbi(%L)\n",
        enumType,
        abiIntegralReadbackExpression(integralType),
    )
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
        "%T(interfaceId = %T(%S), parameterKinds = %L, returnKind = %L)",
        WINRT_DELEGATE_DESCRIPTOR_CLASS_NAME,
        GUID_CLASS_NAME,
        invokeShape.interfaceId.toString(),
        delegateInvokeParameterKindsCode(invokeShape.parameterBindings),
        delegateInvokeReturnKindCode(invokeShape.returnBinding),
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

internal fun KotlinProjectionRenderer.renderInlineAbiInvocation(
    invokeTargetExpression: String,
    slotExpression: String,
    callPlan: KotlinProjectionAbiCallPlan,
): CodeBlock? =
    renderInlineAbiInvocation(invokeTargetExpression, CodeBlock.of("%L", slotExpression), callPlan)

internal fun KotlinProjectionRenderer.renderInlineAbiInvocation(
    invokeTargetExpression: String,
    slotExpression: CodeBlock,
    callPlan: KotlinProjectionAbiCallPlan,
): CodeBlock? {
    val resultMarshaler = callPlan.returnMarshaler
    val code = CodeBlock.builder()
    val scopedParameterOpeners = callPlan.parameterMarshalers.flatMap { it.scopeOpeners }
    scopedParameterOpeners.forEach { opener ->
        code.add("%L\n", opener)
        code.indent()
    }
    if (resultMarshaler != null) {
        code.add("%T.confinedScope().use { __scope ->\n", PLATFORM_ABI_CLASS_NAME)
        code.indent()
        resultMarshaler.resultLocalDeclarations?.let { declarations ->
            code.add("%L", declarations)
        } ?: code.addStatement("val __resultOut = %L", requireNotNull(resultMarshaler.resultAllocation))
    }
    val abiArguments = callPlan.parameterMarshalers.flatMap { marshaler ->
        listOf(marshaler.abiArgumentExpression) + marshaler.extraAbiArgumentExpressions
    } + if (resultMarshaler != null) {
        listOf(resultMarshaler.abiArgumentExpression) + resultMarshaler.extraAbiArgumentExpressions
    } else {
        emptyList()
    }
    code.add("val __hr = ")
    code.add(
        renderComVtableInvocation(
            invokeTargetExpression = invokeTargetExpression,
            slotExpression = slotExpression,
            abiArguments = abiArguments,
        ),
    )
    code.add("\n")
    code.addStatement("%T(__hr).requireSuccess()", HRESULT_CLASS_NAME)
    callPlan.parameterMarshalers.flatMap { it.postCallStatements }.forEach { postCallStatement ->
        code.add("%L\n", postCallStatement)
    }
    resultMarshaler?.readbackStatement?.let(code::add)
    if (resultMarshaler != null) {
        code.unindent()
        code.add("}\n")
    }
    repeat(scopedParameterOpeners.size) {
        code.unindent()
        code.add("}\n")
    }
    return code.build()
}

internal fun KotlinProjectionRenderer.renderComVtableInvocation(
    invokeTargetExpression: String,
    slotExpression: CodeBlock,
    abiArguments: List<CodeBlock>,
): CodeBlock {
    val builder = CodeBlock.builder()
    if (abiArguments.isEmpty()) {
        builder.add(
            "%T.invoke(instance = %L.pointer, slot = %L)",
            COM_VTABLE_INVOKER_CLASS_NAME,
            invokeTargetExpression,
            slotExpression,
        )
    } else if (abiArguments.size <= 6) {
        builder.add(
            "%T.invokeArgs(instance = %L.pointer, slot = %L",
            COM_VTABLE_INVOKER_CLASS_NAME,
            invokeTargetExpression,
            slotExpression,
        )
        abiArguments.forEachIndexed { index, argument ->
            builder.add(", arg%L = %L", index, argument)
        }
        builder.add(")")
    } else {
        builder.add(
            "%T.invokeGenericArgs(instance = %L.pointer, slot = %L",
            COM_VTABLE_INVOKER_CLASS_NAME,
            invokeTargetExpression,
            slotExpression,
        )
        abiArguments.forEach { argument ->
            builder.add(", %L", argument)
        }
        builder.add(")")
    }
    return builder.build()
}
