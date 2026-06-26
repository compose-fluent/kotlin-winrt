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
import io.github.composefluent.winrt.runtime.WinRTPropertyValueProjection
import io.github.composefluent.winrt.runtime.WinRTPlatformApi
import io.github.composefluent.winrt.runtime.WinRTTypeSignature
import io.github.composefluent.winrt.runtime.WinRTTypeHandle
import io.github.composefluent.winrt.runtime.WinRTUri
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
    marshalerPlanDescriptor: WinRTAbiMarshalerPlanDescriptor? = null,
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
    marshalerPlanDescriptor: WinRTAbiMarshalerPlanDescriptor? = null,
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
    descriptor: WinRTAbiMarshalerSlotDescriptor? = null,
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
        parameterBinding.typeBinding.resolvedTypeName.isProjectedWinRTInterfaceReferenceName()
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
                        "%T.confinedScope().use { %L ->\nval %L = %T.allocateBytes(%L, %T.BYTE_SIZE.toLong())\n%T.writeGuid(%L, %L)",
                        PLATFORM_ABI_CLASS_NAME,
                        scopeName,
                        abiLocalName,
                        PLATFORM_ABI_CLASS_NAME,
                        scopeName,
                        GUID_CLASS_NAME,
                        PLATFORM_ABI_CLASS_NAME,
                        abiLocalName,
                        parameterName,
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
        KotlinProjectionAbiValueKind.MappedKeyValuePair -> mappedKeyValuePairParameterMarshaler(parameterBinding)
        KotlinProjectionAbiValueKind.PropertyValue -> propertyValueParameterMarshaler(parameterBinding)
        KotlinProjectionAbiValueKind.MappedAsyncAction,
        KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress,
        KotlinProjectionAbiValueKind.MappedAsyncOperation,
        KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress -> asyncReferenceParameterMarshaler(parameterBinding)
        KotlinProjectionAbiValueKind.ProjectedInterface -> projectedInterfaceParameterMarshaler(parameterName, parameterBinding)
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass -> projectedRuntimeClassParameterMarshaler(parameterName, parameterBinding)
        KotlinProjectionAbiValueKind.Object -> {
            val marshalerName = generatedLocalIdentifier("__", parameterName, "Marshaler")
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
    val marshalerName = generatedLocalIdentifier("__", parameterName, "ProjectionMarshaler")
    return KotlinProjectionAbiMarshalerPlan(
        name = parameterName,
        typeBinding = parameterBinding.typeBinding,
        isReturn = false,
        abiArgumentExpression = CodeBlock.of("%L.abi", marshalerName),
        abiArgumentKind = KotlinProjectionComArgumentKind.Pointer,
        scopeOpeners = listOf(
            CodeBlock.of(
                "%M(%N, %S, %T(%S)).use { %L ->",
                WINRT_PROJECTION_MARSHALER_FUNCTION_NAME,
                kotlinPoetNameLiteral(parameterName),
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

private fun KotlinProjectionRenderer.mappedKeyValuePairParameterMarshaler(
    parameterBinding: KotlinProjectionAbiParameterBinding,
): KotlinProjectionAbiMarshalerPlan? {
    val adapter = collectionReferenceAdapterCode(parameterBinding.typeBinding) ?: return null
    val marshalerName = generatedLocalIdentifier("__", parameterBinding.name, "Marshaler")
    return KotlinProjectionAbiMarshalerPlan(
        name = parameterBinding.name,
        typeBinding = parameterBinding.typeBinding,
        isReturn = false,
        abiArgumentExpression = CodeBlock.of("%L.abi", marshalerName),
        abiArgumentKind = KotlinProjectionComArgumentKind.Pointer,
        scopeOpeners = listOf(
            CodeBlock.of("%L.createInputMarshaler(%L).use { %L ->", adapter, parameterBinding.name, marshalerName),
        ),
    )
}

private fun asyncReferenceParameterMarshaler(
    parameterBinding: KotlinProjectionAbiParameterBinding,
): KotlinProjectionAbiMarshalerPlan =
    KotlinProjectionAbiMarshalerPlan(
        name = parameterBinding.name,
        typeBinding = parameterBinding.typeBinding,
        isReturn = false,
        abiArgumentExpression = CodeBlock.of("%T.fromRawComPtr(%L.pointer)", PLATFORM_ABI_CLASS_NAME, parameterBinding.name),
        abiArgumentKind = KotlinProjectionComArgumentKind.Pointer,
    )

private fun propertyValueParameterMarshaler(
    parameterBinding: KotlinProjectionAbiParameterBinding,
): KotlinProjectionAbiMarshalerPlan =
    KotlinProjectionAbiMarshalerPlan(
        name = parameterBinding.name,
        typeBinding = parameterBinding.typeBinding,
        isReturn = false,
        abiArgumentExpression = CodeBlock.of("%T.fromManaged(%L)", WINRT_PROPERTY_VALUE_PROJECTION_CLASS_NAME, parameterBinding.name),
        abiArgumentKind = KotlinProjectionComArgumentKind.Pointer,
    )

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
    descriptor: WinRTAbiMarshalerSlotDescriptor? = null,
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
        KotlinProjectionAbiValueKind.PropertyValue -> CodeBlock.of("%T.allocatePointerSlot(__scope)", PLATFORM_ABI_CLASS_NAME)
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
                    "val __resultPointer = %T.readPointer(__resultOut)\n%Lval __result = %L ?: error(%S)\nreturn __result\n",
                    PLATFORM_ABI_CLASS_NAME,
                    abiNullReturnReadback(returnBinding),
                    delegateReturnFromAbiCode(returnType, returnBinding),
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
        KotlinProjectionAbiValueKind.PropertyValue ->
            CodeBlock.of(
                "val __resultPointer = %T.readPointer(__resultOut)\nval __result = %T.tryFromBorrowedAbi(__resultPointer)\nreturn __result\n",
                PLATFORM_ABI_CLASS_NAME,
                WINRT_PROPERTY_VALUE_PROJECTION_CLASS_NAME,
            )
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
    runCatching { resolveTypeName(returnBinding.typeName.rawProjectionTypeName()) as? ClassName }.getOrNull()
        ?: runCatching { resolveTypeName(returnBinding.resolvedTypeName.rawProjectionTypeName()) as? ClassName }.getOrNull()

private fun String.rawProjectionTypeName(): String =
    trim().removeSuffix("?").substringBefore('<')

private fun KotlinProjectionRenderer.delegateReturnFromAbiCode(
    returnType: ClassName,
    returnBinding: KotlinProjectionAbiTypeBinding,
): CodeBlock {
    if (returnBinding.typeArguments.isEmpty()) {
        return CodeBlock.of("%T.Metadata.fromAbi(__resultPointer)", returnType)
    }
    val typeArguments = CodeBlock.builder()
    returnBinding.typeArguments.forEachIndexed { index, typeArgument ->
        if (index > 0) {
            typeArguments.add(", ")
        }
        typeArguments.add("%T", resolveTypeName(typeArgument.typeName))
    }
    return CodeBlock.of("%T.Metadata.fromAbi<%L>(__resultPointer)", returnType, typeArguments.build())
}

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
