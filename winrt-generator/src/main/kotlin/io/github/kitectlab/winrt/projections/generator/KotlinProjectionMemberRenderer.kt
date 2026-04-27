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

internal fun KotlinProjectionRenderer.applyCommonTypeShape(
    builder: TypeSpec.Builder,
    plan: KotlinTypeProjectionPlan,
    addModifiers: Boolean = true,
    emitKotlinSealed: Boolean = true,
) {
    builder.addModifiers(renderVisibility(plan.visibility))
    repeat(plan.type.genericParameterCount) { index ->
        builder.addTypeVariable(TypeVariableName("T$index"))
    }
    if (addModifiers) {
        plan.modifiers.forEach { modifier ->
            when (modifier) {
                KotlinProjectionModifier.Sealed -> if (emitKotlinSealed) builder.addModifiers(KModifier.SEALED)
                KotlinProjectionModifier.Static -> Unit
            }
        }
    }
}

internal fun KotlinProjectionRenderer.renderVisibility(visibility: KotlinProjectionVisibility): KModifier = when (visibility) {
    KotlinProjectionVisibility.Public -> KModifier.PUBLIC
    KotlinProjectionVisibility.Internal -> KModifier.INTERNAL
}

internal fun KotlinProjectionRenderer.renderInterfaceMethod(method: WinRtMethodDefinition): FunSpec =
    FunSpec.builder(method.name)
        .addModifiers(KModifier.ABSTRACT)
        .addParameters(method.parameters.map { ParameterSpec.builder(it.name, resolveTypeName(it.typeName)).build() })
        .returns(resolveTypeName(method.returnTypeName))
        .build()

internal fun KotlinProjectionRenderer.renderStubMethod(method: WinRtMethodDefinition, override: Boolean = false): FunSpec {
    val builder = FunSpec.builder(method.name)
        .addParameters(method.parameters.map { ParameterSpec.builder(it.name, resolveTypeName(it.typeName)).build() })
        .returns(resolveTypeName(method.returnTypeName))
        .addCode("return error(%S)\n", "Not yet bound to winrt-runtime")
    if (override) {
        builder.addModifiers(KModifier.OVERRIDE)
    }
    return builder.build()
}

internal fun KotlinProjectionRenderer.renderRuntimeMethod(
    plan: KotlinTypeProjectionPlan,
    method: WinRtMethodDefinition,
): FunSpec =
    renderBoundMethod(plan, method) ?: renderStubMethod(method)

internal fun KotlinProjectionRenderer.renderInterfaceProperty(property: WinRtPropertyDefinition): PropertySpec =
    PropertySpec.builder(property.name.replaceFirstChar(Char::lowercase), resolveTypeName(property.typeName))
        .mutable(!property.isReadOnly)
        .addModifiers(KModifier.ABSTRACT)
        .build()

internal fun KotlinProjectionRenderer.renderStubProperty(property: WinRtPropertyDefinition, override: Boolean = false): PropertySpec {
    val builder = PropertySpec.builder(
        property.name.replaceFirstChar(Char::lowercase),
        resolveTypeName(property.typeName),
    ).mutable(!property.isReadOnly)
    if (override) {
        builder.addModifiers(KModifier.OVERRIDE)
    }
    builder.getter(
        FunSpec.getterBuilder()
            .addCode("return error(%S)\n", "Not yet bound to winrt-runtime")
            .build(),
    )
    if (!property.isReadOnly) {
        builder.setter(
            FunSpec.setterBuilder()
                .addParameter("value", resolveTypeName(property.typeName))
                .addCode("error(%S)\n", "Not yet bound to winrt-runtime")
                .build(),
        )
    }
    return builder.build()
}

internal fun KotlinProjectionRenderer.renderRuntimeProperty(
    plan: KotlinTypeProjectionPlan,
    property: WinRtPropertyDefinition,
): PropertySpec =
    renderBoundProperty(plan, property) ?: renderStubProperty(property)

internal fun KotlinProjectionRenderer.renderBoundMethod(
    plan: KotlinTypeProjectionPlan,
    method: WinRtMethodDefinition,
): FunSpec? {
    val binding = plan.instanceMemberBindings.firstOrNull { it.bindingName == "${method.name.uppercase()}_SLOT" } ?: return null
    val invocation = renderBoundInvocation(binding)
    return FunSpec.builder(method.name)
        .addModifiers(KModifier.OVERRIDE)
        .returns(resolveTypeName(method.returnTypeName))
        .addParameters(method.parameters.map { ParameterSpec.builder(it.name, resolveTypeName(it.typeName)).build() })
        .addCode("%L\n", invocation)
        .build()
}

internal fun KotlinProjectionRenderer.renderBoundProperty(
    plan: KotlinTypeProjectionPlan,
    property: WinRtPropertyDefinition,
): PropertySpec? {
    val builder = PropertySpec.builder(
        property.name.replaceFirstChar(Char::lowercase),
        resolveTypeName(property.typeName),
    ).mutable(!property.isReadOnly)
        .addModifiers(KModifier.OVERRIDE)
    val getterBinding = plan.instanceMemberBindings.firstOrNull {
        it.bindingName == "${property.name.uppercase()}_GETTER_SLOT"
    } ?: return null
    val getterInvocation = renderBoundInvocation(binding = getterBinding)
    builder.getter(
        FunSpec.getterBuilder()
            .addCode("%L\n", getterInvocation)
            .build(),
    )
    if (!property.isReadOnly) {
        val setterBinding = plan.instanceMemberBindings.firstOrNull {
            it.bindingName == "${property.name.uppercase()}_SETTER_SLOT"
        }
        builder.setter(
            FunSpec.setterBuilder()
                .addParameter("value", resolveTypeName(property.typeName))
                .addCode("%L\n", setterBinding?.let(::renderBoundInvocation) ?: CodeBlock.of("error(%S)", "Not yet bound to winrt-runtime"))
                .build(),
        )
    }
    return builder.build()
}

internal fun KotlinProjectionRenderer.renderBoundInvocation(
    binding: KotlinProjectionInstanceMemberBinding,
): CodeBlock {
    val callPlan = requireAbiCallPlan(
        bindingName = binding.bindingName,
        returnBinding = binding.returnBinding,
        parameterBindings = binding.parameterBindings,
        marshalerPlanDescriptor = binding.marshalerPlanDescriptor,
    )
    return renderInlineAbiInvocation(
        invokeTargetExpression = binding.ownerCachePropertyName,
        slotExpression = "Metadata.${binding.bindingName}",
        callPlan = callPlan,
    ) ?: error("Generator ABI marshaler parity failed to emit ${binding.bindingName}")
}

internal fun KotlinProjectionRenderer.renderBoundStaticInvocation(
    binding: KotlinProjectionStaticMemberBinding,
): CodeBlock {
    val callPlan = requireAbiCallPlan(
        bindingName = binding.bindingName,
        returnBinding = binding.returnBinding,
        parameterBindings = binding.parameterBindings,
        marshalerPlanDescriptor = binding.marshalerPlanDescriptor,
    )
    return renderInlineAbiInvocation(
        invokeTargetExpression = "StaticInterfaces.${binding.ownerAccessorName}()",
        slotExpression = binding.bindingName,
        callPlan = callPlan,
    ) ?: error("Generator ABI marshaler parity failed to emit ${binding.bindingName}")
}
