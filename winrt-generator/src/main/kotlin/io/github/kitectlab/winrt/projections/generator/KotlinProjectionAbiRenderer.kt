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
    customObjectAbi(parameterBinding.typeBinding)?.let { customAbi ->
        return KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%T.fromRawComPtr(%L.pointer)", PLATFORM_ABI_CLASS_NAME, abiLocalName),
            scopeOpeners = listOf(
                CodeBlock.of(
                    "%T.%L(%L, %T(%S)).use { %L ->",
                    WINRT_SYSTEM_PROJECTION_MARSHALERS_CLASS_NAME,
                    customAbi.createReferenceFunctionName,
                    parameterName,
                    GUID_CLASS_NAME,
                    customAbi.interfaceId.toString(),
                    abiLocalName,
                ),
            ),
        )
    }
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
            customStructAbi(returnBinding)?.let { customAbi ->
                if (customAbi.disposeAbiFunctionName != null) {
                    CodeBlock.of(
                        "try {\n    return %T.%L(__resultOut)\n} finally {\n    %T.%L(__resultOut)\n}\n",
                        customAbi.helperTypeName,
                        customAbi.fromAbiFunctionName,
                        customAbi.helperTypeName,
                        customAbi.disposeAbiFunctionName,
                    )
                } else {
                    CodeBlock.of("return %T.%L(__resultOut)\n", customAbi.helperTypeName, customAbi.fromAbiFunctionName)
                }
            } ?: nativeStructClassName(returnBinding)?.let { returnType ->
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
