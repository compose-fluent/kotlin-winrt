package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTAbiMarshalerPlanDescriptor
import io.github.composefluent.winrt.metadata.WinRTAbiMarshalerSlotDescriptor
import io.github.composefluent.winrt.metadata.WinRTCustomMappedMemberOutputDescriptor
import io.github.composefluent.winrt.metadata.WinRTEventDefinition
import io.github.composefluent.winrt.metadata.WinRTEventInvokeDescriptor
import io.github.composefluent.winrt.metadata.WinRTFactorySurfaceDescriptor
import io.github.composefluent.winrt.metadata.WinRTFieldDefinition
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
    if (
        returnBinding.containsOpenGenericShape() ||
        parameterBindings.any { parameter -> parameter.typeBinding.containsOpenGenericShape() }
    ) {
        return null
    }
    val parameterSlots = parameterBindings.map { parameterBinding ->
        val slot = marshalerPlanDescriptor?.marshalers?.firstOrNull { !it.isReturn && it.name == parameterBinding.name }
        val effectiveBinding = parameterBinding.copy(category = slot?.category ?: parameterBinding.category)
        KotlinProjectionAbiSlotPlan(
            binding = effectiveBinding,
            recipePlan = buildCallSiteRecipe(effectiveBinding.typeBinding, effectiveBinding.category),
        )
    }
    val returnRecipePlan = when (returnBinding.kind) {
        KotlinProjectionAbiValueKind.Unit -> null
        else -> buildCallSiteRecipe(returnBinding, category = null)
    }
    return KotlinProjectionAbiCallPlan(
        returnBinding = returnBinding,
        parameterSlots = parameterSlots,
        returnRecipePlan = returnRecipePlan,
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
        val unsupportedKinds = (parameterBindings.map { it.typeBinding } + returnBinding)
            .filter(KotlinProjectionAbiTypeBinding::containsOpenGenericShape)
            .map(KotlinProjectionAbiTypeBinding::describeAbiKind)
            .distinct()
            .joinToString(", ")
        "Open generic WinMD member $bindingName is API-only and cannot produce an ABI call plan for $unsupportedKinds."
    }
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

internal fun KotlinProjectionRenderer.delegateFromBorrowedAbiCode(
    binding: KotlinProjectionAbiTypeBinding,
    pointerExpression: CodeBlock,
): CodeBlock {
    staticDelegateFromBorrowedAbiCode(binding, pointerExpression)?.let { return it }
    val delegateType = requireNotNull(projectedClassName(binding)) {
        "Delegate ${binding.describeAbiKind()} has no renderable projected class."
    }
    val invokeShape = requireNotNull(outboundDelegateInvokeShape(binding) ?: binding.delegateInvokeShape) {
        "Delegate ${binding.describeAbiKind()} has no closed invoke shape."
    }
    val interfaceId = requireNotNull(delegateInterfaceIdCode(binding, invokeShape)) {
        "Delegate ${binding.describeAbiKind()} has no renderable closed interface IID."
    }
    val typeArguments = CodeBlock.builder()
    binding.typeArguments.forEachIndexed { index, typeArgument ->
        if (index > 0) {
            typeArguments.add(", ")
        }
        typeArguments.add("%T", resolveTypeName(typeArgument.typeName))
    }
    return CodeBlock.of(
        """
        run {
            val __delegatePointer = %L
            if (%T.isNull(__delegatePointer)) {
                null
            } else {
                val __ownedDelegatePointer = %T(
                    pointer = %T.toRawComPtr(__delegatePointer),
                    interfaceId = %L,
                    preventReleaseOnDispose = true,
                ).use { __borrowed -> %T.fromRawComPtr(__borrowed.getRefPointer()) }
                %L
            }
        }
        """.trimIndent(),
        pointerExpression,
        PLATFORM_ABI_CLASS_NAME,
        IUNKNOWN_REFERENCE_CLASS_NAME,
        PLATFORM_ABI_CLASS_NAME,
        interfaceId,
        PLATFORM_ABI_CLASS_NAME,
        if (binding.typeArguments.isEmpty()) {
            CodeBlock.of("%T.Metadata.fromAbi(__ownedDelegatePointer)", delegateType)
        } else {
            CodeBlock.of(
                "%T.Metadata.fromAbi<%L>(__ownedDelegatePointer, %L)",
                delegateType,
                typeArguments.build(),
                interfaceId,
            )
        },
    )
}

internal fun KotlinProjectionRenderer.delegateFromOwnedAbiCode(
    binding: KotlinProjectionAbiTypeBinding,
    pointerExpression: CodeBlock,
): CodeBlock {
    val delegateType = requireNotNull(projectedClassName(binding)) {
        "Delegate ${binding.describeAbiKind()} has no renderable projected class."
    }
    if (binding.typeArguments.isEmpty()) {
        return CodeBlock.of("%T.Metadata.fromAbi(%L)", delegateType, pointerExpression)
    }
    val invokeShape = requireNotNull(outboundDelegateInvokeShape(binding)) {
        "Delegate ${binding.describeAbiKind()} has no closed invoke shape."
    }
    val interfaceId = requireNotNull(delegateInterfaceIdCode(binding, invokeShape)) {
        "Delegate ${binding.describeAbiKind()} has no renderable closed interface IID."
    }
    val typeArguments = CodeBlock.builder()
    binding.typeArguments.forEachIndexed { index, typeArgument ->
        if (index > 0) {
            typeArguments.add(", ")
        }
        typeArguments.add("%T", resolveTypeName(typeArgument.typeName))
    }
    return CodeBlock.of(
        "%T.Metadata.fromAbi<%L>(%L, %L)",
        delegateType,
        typeArguments.build(),
        pointerExpression,
        interfaceId,
    )
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
