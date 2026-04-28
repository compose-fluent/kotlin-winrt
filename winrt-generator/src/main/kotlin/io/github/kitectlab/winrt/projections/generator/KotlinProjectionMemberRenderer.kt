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
import io.github.kitectlab.winrt.metadata.WinRtCustomAttributeValue
import io.github.kitectlab.winrt.metadata.WinRtProjectedAttributeDescriptor
import io.github.kitectlab.winrt.metadata.projectedAttributes
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
    plan.projectedAttributes.mapNotNull(::renderProjectedAttributeAnnotation).forEach(builder::addAnnotation)
    if (addModifiers) {
        plan.modifiers.forEach { modifier ->
            when (modifier) {
                KotlinProjectionModifier.Sealed -> if (emitKotlinSealed) builder.addModifiers(KModifier.SEALED)
                KotlinProjectionModifier.Static -> Unit
            }
        }
    }
}

internal fun renderProjectedAttributeAnnotation(attribute: WinRtProjectedAttributeDescriptor): AnnotationSpec? =
    when (attribute.projectedTypeName) {
        "System.Runtime.Versioning.SupportedOSPlatform" -> {
            val platform = attribute.arguments.firstOrNull()?.stringValue ?: return null
            AnnotationSpec.builder(WINRT_SUPPORTED_OS_PLATFORM_CLASS_NAME)
                .addMember("%S", platform)
                .build()
        }
        "Windows.Foundation.Metadata.ContractVersion" -> {
            val contract = attribute.arguments.getOrNull(0)?.stringValue ?: return null
            val version = (attribute.arguments.getOrNull(1) as? WinRtCustomAttributeValue.IntegralValue)?.value ?: return null
            AnnotationSpec.builder(WINRT_CONTRACT_VERSION_CLASS_NAME)
                .addMember("contract = %S", contract)
                .addMember("version = %LL", version)
                .build()
        }
        "Windows.Foundation.Metadata.Experimental" ->
            AnnotationSpec.builder(WINRT_EXPERIMENTAL_CLASS_NAME).build()
        "System.AttributeUsage" -> {
            val targets = (attribute.arguments.firstOrNull() as? WinRtCustomAttributeValue.EnumValue)?.value ?: return null
            val allowMultiple = attribute.namedArguments
                .firstOrNull { it.name == "AllowMultiple" }
                ?.let { it.value as? WinRtCustomAttributeValue.BooleanValue }
                ?.value ?: false
            AnnotationSpec.builder(WINRT_ATTRIBUTE_USAGE_CLASS_NAME)
                .addMember("targets = %LL", targets)
                .addMember("allowMultiple = %L", allowMultiple)
                .build()
        }
        "Windows.Foundation.Metadata.DefaultOverload" ->
            AnnotationSpec.builder(WINRT_DEFAULT_OVERLOAD_CLASS_NAME).build()
        "Windows.Foundation.Metadata.Overload" -> {
            val name = attribute.arguments.firstOrNull()?.stringValue ?: return null
            AnnotationSpec.builder(WINRT_OVERLOAD_CLASS_NAME)
                .addMember("%S", name)
                .build()
        }
        else -> renderGeneratedWinRtAttributeAnnotation(attribute)
    }

private fun renderGeneratedWinRtAttributeAnnotation(attribute: WinRtProjectedAttributeDescriptor): AnnotationSpec? {
    val attributeTypeName = attribute.metadataTypeName.takeIf { it.endsWith("Attribute") }
        ?: "${attribute.projectedTypeName}Attribute"
    return AnnotationSpec.builder(projectionClassNameForQualifiedName(attributeTypeName))
        .apply {
            attribute.arguments.forEach { value ->
                val rendered = renderGeneratedAttributeValue(value) ?: return null
                addMember("%L", rendered)
            }
            attribute.namedArguments.forEach { argument ->
                val rendered = renderGeneratedAttributeValue(argument.value) ?: return null
                addMember("%L = %L", argument.name, rendered)
            }
        }
        .build()
}

private fun renderGeneratedAttributeValue(value: WinRtCustomAttributeValue): CodeBlock? =
    when (value) {
        is WinRtCustomAttributeValue.StringValue -> CodeBlock.of("%S", value.value.orEmpty())
        is WinRtCustomAttributeValue.TypeValue -> value.typeName?.let { CodeBlock.of("%T::class", projectionClassNameForQualifiedName(it)) }
        is WinRtCustomAttributeValue.BooleanValue -> CodeBlock.of("%L", value.value)
        is WinRtCustomAttributeValue.IntegralValue -> CodeBlock.of("%LL", value.value)
        is WinRtCustomAttributeValue.FloatingPointValue -> CodeBlock.of("%L", value.value)
        is WinRtCustomAttributeValue.EnumValue -> CodeBlock.of("%LL", value.value)
        is WinRtCustomAttributeValue.ArrayValue -> {
            val values = value.values.map { renderGeneratedAttributeValue(it) ?: return null }
            CodeBlock.builder()
                .add("[")
                .apply {
                    values.forEachIndexed { index, rendered ->
                        if (index > 0) add(", ")
                        add("%L", rendered)
                    }
                }
                .add("]")
                .build()
        }
        WinRtCustomAttributeValue.NullValue -> null
    }

internal fun FunSpec.Builder.addProjectedAttributeAnnotations(
    attributes: List<WinRtProjectedAttributeDescriptor>,
): FunSpec.Builder = apply {
    attributes.mapNotNull(::renderProjectedAttributeAnnotation).forEach(::addAnnotation)
}

internal fun PropertySpec.Builder.addProjectedAttributeAnnotations(
    attributes: List<WinRtProjectedAttributeDescriptor>,
): PropertySpec.Builder = apply {
    attributes.mapNotNull(::renderProjectedAttributeAnnotation).forEach(::addAnnotation)
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
    val objectShape = runtimeObjectMethodShape(method)
    val builder = FunSpec.builder(objectShape?.name ?: method.name)
        .addParameters(objectShape?.parameters ?: method.parameters.map { ParameterSpec.builder(it.name, resolveTypeName(it.typeName)).build() })
        .returns(objectShape?.returnType ?: resolveTypeName(method.returnTypeName))
        .addCode("return %L\n", missingAbiBindingError("method ${method.name}"))
    if (override || objectShape != null) {
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
            .addCode("return %L\n", missingAbiBindingError("property ${property.name} getter"))
            .build(),
    )
    if (!property.isReadOnly) {
        builder.setter(
            FunSpec.setterBuilder()
                .addParameter("value", resolveTypeName(property.typeName))
                .addCode("%L\n", missingAbiBindingError("property ${property.name} setter"))
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
    val objectShape = runtimeObjectMethodShape(method)
    val invocation = if (objectShape?.kind == RuntimeObjectMethodKind.Equals) {
        renderObjectEqualsInvocation(binding)
    } else {
        renderBoundInvocation(binding)
    }
    return FunSpec.builder(objectShape?.name ?: method.name)
        .addProjectedAttributeAnnotations(binding.projectedAttributes)
        .addModifiers(objectShape?.let { listOf(KModifier.OVERRIDE) } ?: runtimeClassMemberModifiers(plan, binding))
        .returns(objectShape?.returnType ?: resolveTypeName(method.returnTypeName))
        .addParameters(objectShape?.parameters ?: method.parameters.map { ParameterSpec.builder(it.name, resolveTypeName(it.typeName)).build() })
        .apply {
            if (objectShape?.kind == RuntimeObjectMethodKind.Equals) {
                addCode("if (other !is %T) return false\n", IWINRT_OBJECT_CLASS_NAME)
            }
        }
        .addCode("%L\n", invocation)
        .build()
}

private enum class RuntimeObjectMethodKind {
    ToString,
    Equals,
    HashCode,
}

private data class RuntimeObjectMethodShape(
    val kind: RuntimeObjectMethodKind,
    val name: String,
    val returnType: TypeName,
    val parameters: List<ParameterSpec>,
)

private fun runtimeObjectMethodShape(method: WinRtMethodDefinition): RuntimeObjectMethodShape? =
    when {
        method.name == "ToString" && method.parameters.isEmpty() && method.returnTypeName == "String" ->
            RuntimeObjectMethodShape(
                kind = RuntimeObjectMethodKind.ToString,
                name = "toString",
                returnType = String::class.asClassName(),
                parameters = emptyList(),
            )
        method.isObjectEquals ->
            RuntimeObjectMethodShape(
                kind = RuntimeObjectMethodKind.Equals,
                name = "equals",
                returnType = Boolean::class.asClassName(),
                parameters = listOf(ParameterSpec.builder("other", ANY.copy(nullable = true)).build()),
            )
        method.isObjectGetHashCode ->
            RuntimeObjectMethodShape(
                kind = RuntimeObjectMethodKind.HashCode,
                name = "hashCode",
                returnType = Int::class.asClassName(),
                parameters = emptyList(),
            )
        else -> null
    }

private fun KotlinProjectionRenderer.renderObjectEqualsInvocation(
    binding: KotlinProjectionInstanceMemberBinding,
): CodeBlock {
    val equalsBinding = binding.copy(
        parameterBindings = listOf(
            KotlinProjectionAbiParameterBinding(
                name = "other",
                typeBinding = KotlinProjectionAbiTypeBinding(
                    kind = KotlinProjectionAbiValueKind.ProjectedRuntimeClass,
                    typeName = "Any?",
                    resolvedTypeName = "System.Object",
                ),
            ),
        ),
    )
    return renderBoundInvocation(equalsBinding)
}

internal fun KotlinProjectionRenderer.renderBoundProperty(
    plan: KotlinTypeProjectionPlan,
    property: WinRtPropertyDefinition,
): PropertySpec? {
    val builder = PropertySpec.builder(
        property.name.replaceFirstChar(Char::lowercase),
        resolveTypeName(property.typeName),
    ).mutable(!property.isReadOnly)
    val getterBinding = plan.instanceMemberBindings.firstOrNull {
        it.bindingName == "${property.name.uppercase()}_GETTER_SLOT"
    } ?: return null
    builder.addModifiers(runtimeClassMemberModifiers(plan, getterBinding))
    val getterInvocation = renderBoundInvocation(binding = getterBinding)
    builder.addProjectedAttributeAnnotations(getterBinding.projectedAttributes)
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
                .addCode(
                    "%L\n",
                    setterBinding?.let(::renderBoundInvocation)
                        ?: missingAbiBindingError("property ${property.name} setter"),
                )
                .build(),
        )
    }
    return builder.build()
}

internal fun missingAbiBindingError(memberName: String): CodeBlock =
    CodeBlock.of(
        "error(%S)",
        "WinRT ABI binding is unavailable for $memberName; projection metadata did not provide a matching interface slot.",
    )

internal fun runtimeClassMemberModifiers(
    plan: KotlinTypeProjectionPlan,
    binding: KotlinProjectionInstanceMemberBinding,
): List<KModifier> {
    val ownerInterfaceName = binding.ownerInterfaceQualifiedName.substringBefore('<')
    val descriptor = plan.classMemberMergeDescriptor
        ?.interfaceDescriptors
        ?.firstOrNull { it.interfaceTypeName == ownerInterfaceName }
    return when {
        descriptor?.isOverridableInterface == true && !plan.type.isSealedType -> listOf(KModifier.PROTECTED, KModifier.OPEN)
        descriptor?.isOverridableInterface == true || descriptor?.isProtectedInterface == true -> listOf(KModifier.PROTECTED)
        else -> listOf(KModifier.OVERRIDE)
    }
}

internal fun KotlinProjectionRenderer.renderBoundInvocation(
    binding: KotlinProjectionInstanceMemberBinding,
): CodeBlock {
    val callPlan = requireAbiCallPlan(
        bindingName = binding.bindingName,
        returnBinding = binding.returnBinding,
        parameterBindings = binding.parameterBindings,
        marshalerPlanDescriptor = binding.marshalerPlanDescriptor,
        suppressHResultCheck = binding.suppressHResultCheck,
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
        suppressHResultCheck = binding.suppressHResultCheck,
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
    val existingMethodNames = plan.type.methods.filter(WinRtMethodDefinition::isOrdinaryProjectedMethod).mapTo(mutableSetOf(), WinRtMethodDefinition::name)
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
                .filter(WinRtMethodDefinition::isOrdinaryProjectedMethod)
                .filterNot {
                    requiredInterface.isMappedCollectionOrIteratorInterface && it.name in suppressedMemberNames ||
                        it.name in existingMethodNames
                }
                .forEach { method ->
                    val substitutedMethod = requiredInterface.substitute(method)
                    val key = "${substitutedMethod.name}:${substitutedMethod.parameters.joinToString(",") { it.typeName }}"
                    if (emittedMethods.add(key)) {
                        renderRequiredForwardMethod(plan, requiredInterface.interfaceName, interfaceType, substitutedMethod)?.let(members::add)
                    }
                }
            interfaceType.properties
                .filterNot(WinRtPropertyDefinition::isStatic)
                .filterNot {
                    requiredInterface.isMappedCollectionOrIteratorInterface && it.name in suppressedMemberNames ||
                        it.name.replaceFirstChar(Char::lowercase) in existingPropertyNames
                }
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

private val RequiredForwardInterfaceType.isMappedCollectionOrIteratorInterface: Boolean
    get() = mappedTypeByAbiName(interfaceName.substringBefore('<').removeSuffix("?"))?.let { mappedType ->
        mappedType.readOnlyCollectionKind != null ||
            mappedType.mutableCollectionKind != null ||
            mappedType.descriptionName == "Iterator"
    } == true

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
        suppressHResultCheck = method.isNoException,
    ) ?: return null
    val slotConstantName = method.abiSlotConstantName(slotInterfaceType.methods)
    val invocation = renderInlineAbiInvocation(
        invokeTargetExpression = requiredForwardOwnerCache(ownerInterfaceName, plan.defaultInterfaceName),
        slotExpression = CodeBlock.of("%T.Metadata.%L", resolveTypeName(slotInterfaceType.qualifiedName), slotConstantName),
        callPlan = callPlan,
    ) ?: return null
    val projectedAttributes = slotInterfaceType.projectedAttributes()
        .filter(WinRtProjectedAttributeDescriptor::isPlatformAttribute)
    return FunSpec.builder(method.name)
        .addProjectedAttributeAnnotations(projectedAttributes)
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
    val projectedAttributes = (property.getter?.slotInterfaceType ?: property.setter?.slotInterfaceType)
        ?.projectedAttributes()
        ?.filter(WinRtProjectedAttributeDescriptor::isPlatformAttribute)
        .orEmpty()
    builder.addProjectedAttributeAnnotations(projectedAttributes)
    property.getter?.let { getter ->
        val callPlan = buildAbiCallPlan(
            returnBinding = renderAbiTypeBinding(getter.property.typeName),
            parameterBindings = emptyList(),
            suppressHResultCheck = getter.property.isNoException,
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
            suppressHResultCheck = setter.property.isNoException,
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
