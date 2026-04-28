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
    val binding = plan.instanceMemberBindings.firstOrNull { it.bindingName == method.abiSlotConstantName(plan.type.methods) } ?: return null
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

internal fun KotlinProjectionRenderer.renderRequiredInterfaceForwardMembers(
    plan: KotlinTypeProjectionPlan,
    suppressedMemberNames: Set<String>,
): List<Any> {
    if (plan.type.kind != WinRtTypeKind.RuntimeClass) {
        return emptyList()
    }
    val existingMethodNames = plan.type.methods.filterNot(WinRtMethodDefinition::isStatic).mapTo(mutableSetOf(), WinRtMethodDefinition::name)
    val existingPropertyNames = plan.type.properties.filterNot(WinRtPropertyDefinition::isStatic).mapTo(mutableSetOf()) {
        it.name.replaceFirstChar(Char::lowercase)
    }
    val members = mutableListOf<Any>()
    val emittedMethods = mutableSetOf<String>()
    val propertyForwards = linkedMapOf<String, RequiredForwardProperty>()
    plan.type.implementedInterfaces.forEach { implemented ->
        val ownerInterface = implemented.interfaceName
        collectRequiredForwardInterfaceTypes(ownerInterface, plan, mutableSetOf()).forEach { requiredInterface ->
            val interfaceType = requiredInterface.type
            interfaceType.methods
                .filterNot(WinRtMethodDefinition::isStatic)
                .filterNot { it.name in suppressedMemberNames || it.name in existingMethodNames }
                .forEach { method ->
                    val substitutedMethod = requiredInterface.substitute(method)
                    val key = "${substitutedMethod.name}:${substitutedMethod.parameters.joinToString(",") { it.typeName }}"
                    if (emittedMethods.add(key)) {
                        renderRequiredForwardMethod(plan, requiredInterface.interfaceName, interfaceType, substitutedMethod)?.let(members::add)
                    }
                }
            interfaceType.properties
                .filterNot(WinRtPropertyDefinition::isStatic)
                .filterNot { it.name in suppressedMemberNames || it.name.replaceFirstChar(Char::lowercase) in existingPropertyNames }
                .forEach { property ->
                    val substitutedProperty = requiredInterface.substitute(property)
                    val propertyName = property.name.replaceFirstChar(Char::lowercase)
                    propertyForwards[propertyName] = propertyForwards[propertyName]
                        ?.merge(requiredInterface.interfaceName, interfaceType, substitutedProperty)
                        ?: RequiredForwardProperty(
                            propertyName = propertyName,
                            propertyTypeName = substitutedProperty.typeName,
                            getter = substitutedProperty.takeIf { it.getterMethodName != null }?.let {
                                RequiredForwardPropertyAccessor(requiredInterface.interfaceName, interfaceType, it)
                            },
                            setter = substitutedProperty.takeIf { !it.isReadOnly && it.setterMethodName != null }?.let {
                                RequiredForwardPropertyAccessor(requiredInterface.interfaceName, interfaceType, it)
                            },
                        )
                }
        }
    }
    propertyForwards.values.forEach { property ->
        renderRequiredForwardProperty(plan, property)?.let(members::add)
    }
    return members
}

private data class RequiredForwardPropertyAccessor(
    val ownerInterfaceName: String,
    val slotInterfaceType: WinRtTypeDefinition,
    val property: WinRtPropertyDefinition,
)

internal data class RequiredForwardInterfaceType(
    val interfaceName: String,
    val type: WinRtTypeDefinition,
    val genericArguments: List<WinRtTypeRef>,
) {
    fun substitute(method: WinRtMethodDefinition): WinRtMethodDefinition =
        if (genericArguments.isEmpty()) {
            method
        } else {
            val substitutedReturnType = method.returnType.substituteTypeParameters(genericArguments).normalized()
            method.copy(
                returnTypeName = substitutedReturnType.typeName,
                returnTypeSignature = substitutedReturnType,
                parameters = method.parameters.map { parameter ->
                    val substitutedType = parameter.type.substituteTypeParameters(genericArguments).normalized()
                    parameter.copy(
                        typeName = substitutedType.typeName,
                        typeSignature = substitutedType,
                        typeIsByRef = substitutedType.isByRef,
                    )
                },
            )
        }

    fun substitute(property: WinRtPropertyDefinition): WinRtPropertyDefinition =
        if (genericArguments.isEmpty()) {
            property
        } else {
            val substitutedType = property.type.substituteTypeParameters(genericArguments).normalized()
            property.copy(
                typeName = substitutedType.typeName,
                typeSignature = substitutedType,
            )
        }
}

private data class RequiredForwardProperty(
    val propertyName: String,
    val propertyTypeName: String,
    val getter: RequiredForwardPropertyAccessor?,
    val setter: RequiredForwardPropertyAccessor?,
) {
    fun merge(
        ownerInterfaceName: String,
        slotInterfaceType: WinRtTypeDefinition,
        property: WinRtPropertyDefinition,
    ): RequiredForwardProperty {
        require(property.typeName == propertyTypeName) {
            "Cannot merge required interface property '$propertyName' with incompatible types: $propertyTypeName vs ${property.typeName}"
        }
        return copy(
            getter = getter ?: property.takeIf { it.getterMethodName != null }?.let {
                RequiredForwardPropertyAccessor(ownerInterfaceName, slotInterfaceType, it)
            },
            setter = setter ?: property.takeIf { !it.isReadOnly && it.setterMethodName != null }?.let {
                RequiredForwardPropertyAccessor(ownerInterfaceName, slotInterfaceType, it)
            },
        )
    }
}

internal fun KotlinProjectionRenderer.collectRequiredForwardInterfaceTypes(
    interfaceName: String,
    plan: KotlinTypeProjectionPlan,
    visiting: MutableSet<String>,
): List<RequiredForwardInterfaceType> {
    val rawName = interfaceName.substringBefore('<').removeSuffix("?")
    val interfaceType = plan.typesByQualifiedName[rawName] ?: return emptyList()
    val genericArguments = genericArgumentTypeRefs(interfaceName)
    if (!visiting.add(interfaceName)) {
        return emptyList()
    }
    return try {
        buildList {
            add(RequiredForwardInterfaceType(interfaceName, interfaceType, genericArguments))
            interfaceType.implementedInterfaces.forEach { implemented ->
                val substitutedInterfaceName = implemented.interfaceType
                    .substituteTypeParameters(genericArguments)
                    .normalized()
                    .typeName
                addAll(collectRequiredForwardInterfaceTypes(substitutedInterfaceName, plan, visiting))
            }
        }
    } finally {
        visiting.remove(interfaceName)
    }
}

private fun genericArgumentTypeRefs(typeName: String): List<WinRtTypeRef> {
    val trimmed = typeName.trim()
    if ('<' !in trimmed || !trimmed.endsWith('>')) {
        return emptyList()
    }
    return splitGenericArguments(trimmed.substringAfter('<').substringBeforeLast('>'))
        .map(WinRtTypeRef::fromDisplayName)
}

private fun KotlinProjectionRenderer.renderRequiredForwardMethod(
    plan: KotlinTypeProjectionPlan,
    ownerInterfaceName: String,
    slotInterfaceType: WinRtTypeDefinition,
    method: WinRtMethodDefinition,
): FunSpec? {
    val returnBinding = renderAbiTypeBinding(method.returnTypeName)
    val parameterBindings = method.parameters.map { parameter ->
        KotlinProjectionAbiParameterBinding(parameter.name, renderAbiTypeBinding(parameter.typeName))
    }
    val callPlan = buildAbiCallPlan(
        returnBinding = returnBinding,
        parameterBindings = parameterBindings,
    ) ?: return null
    val slotConstantName = method.abiSlotConstantName(slotInterfaceType.methods)
    val invocation = renderInlineAbiInvocation(
        invokeTargetExpression = requiredForwardOwnerCache(ownerInterfaceName, plan.defaultInterfaceName),
        slotExpression = CodeBlock.of("%T.Metadata.%L", resolveTypeName(slotInterfaceType.qualifiedName), slotConstantName),
        callPlan = callPlan,
    ) ?: return null
    return FunSpec.builder(method.name)
        .addModifiers(KModifier.OVERRIDE)
        .returns(resolveTypeName(method.returnTypeName))
        .addParameters(method.parameters.map { ParameterSpec.builder(it.name, resolveTypeName(it.typeName)).build() })
        .addCode("%L\n", invocation)
        .build()
}

private fun KotlinProjectionRenderer.renderRequiredForwardProperty(
    plan: KotlinTypeProjectionPlan,
    property: RequiredForwardProperty,
): PropertySpec? {
    val propertyType = resolveTypeName(property.propertyTypeName)
    val builder = PropertySpec.builder(property.propertyName, propertyType)
        .addModifiers(KModifier.OVERRIDE)
        .mutable(property.setter != null)
    property.getter?.let { getter ->
        val callPlan = buildAbiCallPlan(
            returnBinding = renderAbiTypeBinding(getter.property.typeName),
            parameterBindings = emptyList(),
        ) ?: return null
        val invocation = renderInlineAbiInvocation(
            invokeTargetExpression = requiredForwardOwnerCache(getter.ownerInterfaceName, plan.defaultInterfaceName),
            slotExpression = CodeBlock.of("%T.Metadata.%L", resolveTypeName(getter.slotInterfaceType.qualifiedName), "${getter.property.name.uppercase()}_GETTER_SLOT"),
            callPlan = callPlan,
        ) ?: return null
        builder.getter(FunSpec.getterBuilder().addCode("%L\n", invocation).build())
    }
    property.setter?.let { setter ->
        val callPlan = buildAbiCallPlan(
            returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
            parameterBindings = listOf(KotlinProjectionAbiParameterBinding("value", renderAbiTypeBinding(setter.property.typeName))),
        ) ?: return null
        val invocation = renderInlineAbiInvocation(
            invokeTargetExpression = requiredForwardOwnerCache(setter.ownerInterfaceName, plan.defaultInterfaceName),
            slotExpression = CodeBlock.of("%T.Metadata.%L", resolveTypeName(setter.slotInterfaceType.qualifiedName), "${setter.property.name.uppercase()}_SETTER_SLOT"),
            callPlan = callPlan,
        ) ?: return null
        builder.setter(FunSpec.setterBuilder().addParameter("value", propertyType).addCode("%L\n", invocation).build())
    }
    return builder.build()
}

internal fun requiredForwardOwnerCache(
    ownerInterfaceName: String,
    defaultInterfaceName: String?,
): String =
    if (ownerInterfaceName == defaultInterfaceName) {
        "_defaultInterface"
    } else {
        "_${ownerInterfaceName.substringBefore('<').substringAfterLast('.').replaceFirstChar(Char::lowercase)}"
    }
