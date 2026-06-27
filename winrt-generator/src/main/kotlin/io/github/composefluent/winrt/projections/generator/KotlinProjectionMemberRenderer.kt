package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTAbiMarshalerPlanDescriptor
import io.github.composefluent.winrt.metadata.WinRTAbiMarshalerSlotDescriptor
import io.github.composefluent.winrt.metadata.WinRTCustomMappedMemberOutputDescriptor
import io.github.composefluent.winrt.metadata.WinRTEventDefinition
import io.github.composefluent.winrt.metadata.WinRTEventInvokeDescriptor
import io.github.composefluent.winrt.metadata.WinRTFactorySurfaceDescriptor
import io.github.composefluent.winrt.metadata.WinRTFieldDefinition
import io.github.composefluent.winrt.metadata.WinRTFundamentalType
import io.github.composefluent.winrt.metadata.WinRTGenericAbiClassInitializationDescriptor
import io.github.composefluent.winrt.metadata.WinRTGenericAbiInventory
import io.github.composefluent.winrt.metadata.WinRTGenericInstantiationWriterDescriptor
import io.github.composefluent.winrt.metadata.WinRTGuidSignatureDescriptor
import io.github.composefluent.winrt.metadata.WinRTInterfaceImplementationDefinition
import io.github.composefluent.winrt.metadata.WinRTInterfaceMemberSignatureSetDescriptor
import io.github.composefluent.winrt.metadata.WinRTMetadataProjectionContext
import io.github.composefluent.winrt.metadata.WinRTMetadataProjectionInventory
import io.github.composefluent.winrt.metadata.WinRTMetadataProjectionInventoryBuilder
import io.github.composefluent.winrt.metadata.WinRTMetadataParameterCategory
import io.github.composefluent.winrt.metadata.WinRTModuleActivationAndAuthoringDescriptor
import io.github.composefluent.winrt.metadata.WinRTMethodVtableDescriptor
import io.github.composefluent.winrt.metadata.WinRTMethodDefinition
import io.github.composefluent.winrt.metadata.WinRTNamespace
import io.github.composefluent.winrt.metadata.WinRTObjectReferenceSurfaceDescriptor
import io.github.composefluent.winrt.metadata.WinRTParameterDefinition
import io.github.composefluent.winrt.metadata.WinRTPropertyDefinition
import io.github.composefluent.winrt.metadata.WinRTRequiredInterfaceAugmentationDescriptor
import io.github.composefluent.winrt.metadata.WinRTSignatureWriterDescriptor
import io.github.composefluent.winrt.metadata.WinRTTypeDeclarationDescriptor
import io.github.composefluent.winrt.metadata.WinRTTypeDefinition
import io.github.composefluent.winrt.metadata.WinRTTypeRef
import io.github.composefluent.winrt.metadata.WinRTTypeRefKind
import io.github.composefluent.winrt.metadata.WinRTTypeKind
import io.github.composefluent.winrt.metadata.isWinRTObjectTypeName
import io.github.composefluent.winrt.metadata.WinRTMetadataValidationOptions
import io.github.composefluent.winrt.metadata.WinRTMetadataSemanticHelpers
import io.github.composefluent.winrt.metadata.WinRTCustomAttributeValue
import io.github.composefluent.winrt.metadata.WinRTProjectedAttributeDescriptor
import io.github.composefluent.winrt.metadata.metadataParameterCategoryFor
import io.github.composefluent.winrt.metadata.projectedAttributes
import io.github.composefluent.winrt.metadata.projectedPropertyTypeName
import io.github.composefluent.winrt.metadata.requireValidForProjection
import io.github.composefluent.winrt.metadata.semanticHelpers
import io.github.composefluent.winrt.metadata.isWinRTVoidTypeName
import io.github.composefluent.winrt.metadata.winRTFundamentalTypeForName
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

internal fun renderProjectedAttributeAnnotation(attribute: WinRTProjectedAttributeDescriptor): AnnotationSpec? =
    when (attribute.projectedTypeName) {
        "System.Runtime.Versioning.SupportedOSPlatform" -> {
            val platform = attribute.arguments.firstOrNull()?.stringValue ?: return null
            AnnotationSpec.builder(WINRT_SUPPORTED_OS_PLATFORM_CLASS_NAME)
                .addMember("%S", platform)
                .build()
        }
        "Windows.Foundation.Metadata.ContractVersion" -> {
            val contract = attribute.arguments.getOrNull(0)?.contractNameValue() ?: return null
            val version = (attribute.arguments.getOrNull(1) as? WinRTCustomAttributeValue.IntegralValue)?.value ?: return null
            AnnotationSpec.builder(WINRT_CONTRACT_VERSION_CLASS_NAME)
                .addMember("contract = %S", contract)
                .addMember("version = %LL", version)
                .build()
        }
        "Windows.Foundation.Metadata.Experimental" ->
            AnnotationSpec.builder(WINRT_EXPERIMENTAL_CLASS_NAME).build()
        "System.AttributeUsage" -> {
            val targets = (attribute.arguments.firstOrNull() as? WinRTCustomAttributeValue.EnumValue)?.value ?: return null
            val allowMultiple = attribute.namedArguments
                .firstOrNull { it.name == "AllowMultiple" }
                ?.let { it.value as? WinRTCustomAttributeValue.BooleanValue }
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
        else -> renderGeneratedWinRTAttributeAnnotation(attribute)
    }

private fun renderGeneratedWinRTAttributeAnnotation(attribute: WinRTProjectedAttributeDescriptor): AnnotationSpec? {
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

private fun WinRTCustomAttributeValue.contractNameValue(): String? =
    when (this) {
        is WinRTCustomAttributeValue.StringValue -> value
        is WinRTCustomAttributeValue.TypeValue -> typeName
        else -> null
    }

private fun renderGeneratedAttributeValue(value: WinRTCustomAttributeValue): CodeBlock? =
    when (value) {
        is WinRTCustomAttributeValue.StringValue -> CodeBlock.of("%S", value.value.orEmpty())
        is WinRTCustomAttributeValue.TypeValue -> value.typeName?.let { CodeBlock.of("%T::class", projectionClassNameForQualifiedName(it)) }
        is WinRTCustomAttributeValue.BooleanValue -> CodeBlock.of("%L", value.value)
        is WinRTCustomAttributeValue.IntegralValue -> CodeBlock.of("%LL", value.value)
        is WinRTCustomAttributeValue.FloatingPointValue -> CodeBlock.of("%L", value.value)
        is WinRTCustomAttributeValue.EnumValue -> CodeBlock.of("%LL", value.value)
        is WinRTCustomAttributeValue.ArrayValue -> {
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
        WinRTCustomAttributeValue.NullValue -> null
    }

internal fun FunSpec.Builder.addProjectedAttributeAnnotations(
    attributes: List<WinRTProjectedAttributeDescriptor>,
): FunSpec.Builder = apply {
    attributes.mapNotNull(::renderProjectedAttributeAnnotation).forEach(::addAnnotation)
}

internal fun PropertySpec.Builder.addProjectedAttributeAnnotations(
    attributes: List<WinRTProjectedAttributeDescriptor>,
): PropertySpec.Builder = apply {
    attributes.mapNotNull(::renderProjectedAttributeAnnotation).forEach(::addAnnotation)
}

internal fun KotlinProjectionRenderer.renderVisibility(visibility: KotlinProjectionVisibility): KModifier = when (visibility) {
    KotlinProjectionVisibility.Public -> KModifier.PUBLIC
    KotlinProjectionVisibility.Internal -> KModifier.INTERNAL
}

internal fun KotlinProjectionRenderer.renderInterfaceMethod(method: WinRTMethodDefinition): FunSpec {
    val objectShape = runtimeObjectMethodShape(method)
    return FunSpec.builder(objectShape?.name ?: method.projectedMethodName())
        .addModifiers(KModifier.ABSTRACT)
        .addMethodGenericParameters(method, objectShape)
        .apply {
            if (objectShape != null) {
                addModifiers(KModifier.OVERRIDE)
            }
        }
        .addParameters(objectShape?.parameters ?: method.projectedKotlinParameters().map { ParameterSpec.builder(it.name, resolveTypeName(it.typeName)).build() })
        .returns(objectShape?.returnType ?: resolveTypeName(method.projectedKotlinReturnTypeName()))
        .build()
}

internal fun KotlinProjectionRenderer.renderStubMethod(method: WinRTMethodDefinition, override: Boolean = false): FunSpec {
    val objectShape = runtimeObjectMethodShape(method)
    val builder = FunSpec.builder(objectShape?.name ?: method.projectedMethodName())
        .addMethodGenericParameters(method, objectShape)
        .addParameters(objectShape?.parameters ?: method.projectedKotlinParameters().map { ParameterSpec.builder(it.name, resolveTypeName(it.typeName)).build() })
        .returns(objectShape?.returnType ?: resolveTypeName(method.projectedKotlinReturnTypeName()))
        .addCode("return %L\n", missingAbiBindingError("method ${method.name}"))
    if (override || objectShape != null) {
        builder.addModifiers(KModifier.OVERRIDE)
    }
    return builder.build()
}

internal fun FunSpec.Builder.addMethodGenericParameters(
    method: WinRTMethodDefinition,
    objectShape: RuntimeObjectMethodShape? = null,
): FunSpec.Builder = apply {
    if (objectShape != null) {
        return@apply
    }
    val parameters = method.genericParameters.ifEmpty {
        (0 until method.genericParameterCount).map { index ->
            io.github.composefluent.winrt.metadata.WinRTGenericParameterDefinition("M$index", index)
        }
    }
    parameters.forEach { parameter ->
        addTypeVariable(TypeVariableName("M${parameter.index}"))
    }
}

internal fun KotlinProjectionRenderer.renderRuntimeMethod(
    plan: KotlinTypeProjectionPlan,
    method: WinRTMethodDefinition,
): FunSpec =
    renderBoundMethod(plan, method) ?: renderStubMethod(method)

internal fun KotlinProjectionRenderer.renderInterfaceProperty(
    ownerTypeName: String,
    property: WinRTPropertyDefinition,
    typesByQualifiedName: Map<String, WinRTTypeDefinition> = emptyMap(),
    override: Boolean = false,
): PropertySpec {
    val builder = PropertySpec.builder(
        property.name.replaceFirstChar(Char::lowercase),
        resolveTypeName(property.projectedPropertyTypeName(ownerTypeName, typesByQualifiedName)),
    )
        .mutable(!property.isReadOnly)
        .addModifiers(KModifier.ABSTRACT)
    if (override) {
        builder.addModifiers(KModifier.OVERRIDE)
    }
    return builder.build()
}

internal fun KotlinProjectionRenderer.renderStubProperty(
    ownerTypeName: String,
    property: WinRTPropertyDefinition,
    override: Boolean = false,
    typesByQualifiedName: Map<String, WinRTTypeDefinition> = emptyMap(),
): PropertySpec {
    val propertyTypeName = property.projectedPropertyTypeName(ownerTypeName, typesByQualifiedName)
    val builder = PropertySpec.builder(
        property.name.replaceFirstChar(Char::lowercase),
        resolveTypeName(propertyTypeName),
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
                .addParameter("value", resolveTypeName(propertyTypeName))
                .addCode("%L\n", missingAbiBindingError("property ${property.name} setter"))
                .build(),
        )
    }
    return builder.build()
}

internal fun KotlinProjectionRenderer.renderRuntimeProperty(
    plan: KotlinTypeProjectionPlan,
    property: WinRTPropertyDefinition,
): PropertySpec =
    renderBoundProperty(plan, property) ?: renderStubProperty(plan.type.qualifiedName, property)

internal fun KotlinProjectionRenderer.renderBoundMethod(
    plan: KotlinTypeProjectionPlan,
    method: WinRTMethodDefinition,
): FunSpec? {
    val binding = matchingMethodBinding(plan, method) ?: return null
    val slotExpression = binding.slotCodeBlock()
    val objectShape = runtimeObjectMethodShape(method)
    val methodReturnBinding = renderAbiTypeBinding(method.projectedKotlinReturnTypeName(), plan.typesByQualifiedName, plan.type.namespace)
    val methodParameterBindings = method.projectedKotlinParameters().map { parameter ->
        KotlinProjectionAbiParameterBinding(
            name = parameter.name,
            typeBinding = renderAbiTypeBinding(parameter.typeName, plan.typesByQualifiedName, plan.type.namespace),
            category = metadataParameterCategoryFor(parameter),
        )
    }
    val effectiveReturnBinding = methodReturnBinding.takeUnless { it.kind == KotlinProjectionAbiValueKind.Unsupported } ?: binding.returnBinding
    val effectiveParameterBindings = methodParameterBindings.mapIndexed { index, parameter ->
        if (parameter.typeBinding.kind == KotlinProjectionAbiValueKind.Unsupported) {
            binding.parameterBindings.getOrNull(index) ?: parameter
        } else {
            parameter
        }
    }
    val effectiveMarshalerPlanDescriptor =
        binding.marshalerPlanDescriptor.takeIf {
            effectiveReturnBinding == binding.returnBinding && effectiveParameterBindings == binding.parameterBindings
        }
    val invocation = if (objectShape?.kind == RuntimeObjectMethodKind.Equals) {
        renderObjectEqualsInvocation(binding)
    } else {
        renderInstanceNoArgIntrinsicInvocation(binding)
            ?: renderInstanceStructResultIntrinsicInvocation(
                referenceExpression = binding.ownerCachePropertyName,
                slotExpression = slotExpression,
                returnBinding = effectiveReturnBinding,
                parameterBindings = effectiveParameterBindings,
                suppressHResultCheck = binding.suppressHResultCheck,
            )
            ?: renderInstanceArrayResultIntrinsicInvocation(
                referenceExpression = binding.ownerCachePropertyName,
                slotExpression = slotExpression,
                returnBinding = effectiveReturnBinding,
                parameterBindings = effectiveParameterBindings,
                suppressHResultCheck = binding.suppressHResultCheck,
            )
            ?: renderInstanceEnumResultIntrinsicInvocation(
                referenceExpression = binding.ownerCachePropertyName,
                slotExpression = slotExpression,
                returnBinding = effectiveReturnBinding,
                parameterBindings = effectiveParameterBindings,
                suppressHResultCheck = binding.suppressHResultCheck,
            )
            ?: renderInstanceOneArgUnitIntrinsicInvocation(binding)
            ?: renderInstanceDescriptorUnitIntrinsicInvocation(
                referenceExpression = binding.ownerCachePropertyName,
                slotExpression = slotExpression,
                returnBinding = effectiveReturnBinding,
                parameterBindings = effectiveParameterBindings,
                suppressHResultCheck = binding.suppressHResultCheck,
            )
            ?: renderInstanceDescriptorBooleanIntrinsicInvocation(
                referenceExpression = binding.ownerCachePropertyName,
                slotExpression = slotExpression,
                returnBinding = effectiveReturnBinding,
                parameterBindings = effectiveParameterBindings,
                suppressHResultCheck = binding.suppressHResultCheck,
            )
            ?: renderInstanceDescriptorScalarIntrinsicInvocation(
                referenceExpression = binding.ownerCachePropertyName,
                slotExpression = slotExpression,
                returnBinding = effectiveReturnBinding,
                parameterBindings = effectiveParameterBindings,
                suppressHResultCheck = binding.suppressHResultCheck,
            )
            ?: renderInstanceDescriptorProjectedObjectIntrinsicInvocation(
                referenceExpression = binding.ownerCachePropertyName,
                slotExpression = slotExpression,
                returnBinding = effectiveReturnBinding,
                parameterBindings = effectiveParameterBindings,
                suppressHResultCheck = binding.suppressHResultCheck,
            )
            ?: renderInstanceDescriptorAsyncIntrinsicInvocation(
                referenceExpression = binding.ownerCachePropertyName,
                slotExpression = slotExpression,
                returnBinding = effectiveReturnBinding,
                parameterBindings = effectiveParameterBindings,
                suppressHResultCheck = binding.suppressHResultCheck,
            )
            ?: renderInstanceStructOneArgUnitIntrinsicInvocation(
                referenceExpression = binding.ownerCachePropertyName,
                slotExpression = slotExpression,
                returnBinding = effectiveReturnBinding,
                parameterBindings = effectiveParameterBindings,
                suppressHResultCheck = binding.suppressHResultCheck,
            )
            ?: renderInstanceEnumOneArgUnitIntrinsicInvocation(
                referenceExpression = binding.ownerCachePropertyName,
                slotExpression = slotExpression,
                returnBinding = effectiveReturnBinding,
                parameterBindings = effectiveParameterBindings,
                suppressHResultCheck = binding.suppressHResultCheck,
            )
            ?: renderBoundInvocation(
                binding = binding,
                returnBinding = effectiveReturnBinding,
                parameterBindings = effectiveParameterBindings,
                marshalerPlanDescriptor = effectiveMarshalerPlanDescriptor,
            )
    }
    val modifiers = objectShape?.let { listOf(KModifier.OVERRIDE) } ?: runtimeClassMemberModifiers(plan, binding)
    val functionName = objectShape?.name ?: method.projectedRuntimeClassMethodName(plan, modifiers)
    return FunSpec.builder(functionName)
        .addProjectedAttributeAnnotations(binding.projectedAttributes)
        .addMethodGenericParameters(method, objectShape)
        .addModifiers(modifiers)
        .returns(objectShape?.returnType ?: resolveTypeName(method.projectedKotlinReturnTypeName()))
        .addParameters(objectShape?.parameters ?: method.projectedKotlinParameters().map { ParameterSpec.builder(it.name, resolveTypeName(it.typeName)).build() })
        .apply {
            if (objectShape?.kind == RuntimeObjectMethodKind.Equals) {
                addCode("if (other !is %T) return false\n", IWINRT_OBJECT_CLASS_NAME)
            }
            addCode("%L\n", invocation)
        }
        .build()
}

private fun KotlinProjectionRenderer.matchingMethodBinding(
    plan: KotlinTypeProjectionPlan,
    method: WinRTMethodDefinition,
): KotlinProjectionInstanceMemberBinding? {
    val bindingName = method.abiSlotConstantName(plan.type.methods)
    val candidates = plan.instanceMemberBindings.filter { it.bindingName == bindingName }
    if (candidates.size <= 1) {
        return candidates.firstOrNull()
    }
    val returnBinding = renderAbiTypeBinding(method.projectedKotlinReturnTypeName(), plan.typesByQualifiedName, plan.type.namespace)
    val parameterBindings = method.projectedKotlinParameters().map { parameter ->
        KotlinProjectionAbiParameterBinding(
            name = parameter.name,
            typeBinding = renderAbiTypeBinding(parameter.typeName, plan.typesByQualifiedName, plan.type.namespace),
            category = metadataParameterCategoryFor(parameter),
        )
    }
    return candidates.firstOrNull { candidate ->
        candidate.returnBinding == returnBinding && candidate.parameterBindings == parameterBindings
    } ?: candidates.firstOrNull()
}

internal fun WinRTMethodDefinition.projectedRuntimeClassMethodName(
    plan: KotlinTypeProjectionPlan,
    modifiers: List<KModifier>,
): String {
    val functionName = projectedMethodName()
    if (KModifier.OVERRIDE in modifiers ||
        KModifier.PROTECTED !in modifiers ||
        !functionName.startsWith("set") ||
        functionName.length <= "set".length ||
        parameters.size != 1 ||
        !isWinRTVoidTypeName(returnTypeName)
    ) {
        return functionName
    }
    if (!plan.hasRuntimeClassPropertySetterJvmSignature(functionName)) {
        return functionName
    }
    return "winrt${functionName.replaceFirstChar(Char::uppercase)}"
}

private fun KotlinTypeProjectionPlan.hasRuntimeClassPropertySetterJvmSignature(setterName: String): Boolean =
    typesByQualifiedName.values
        .filter { candidate ->
            candidate.kind == WinRTTypeKind.RuntimeClass &&
                (candidate.qualifiedName == type.qualifiedName ||
                    candidate.hasRuntimeClassBase(type.qualifiedName, typesByQualifiedName))
        }
        .flatMap { it.properties }
        .any { property -> property.setterJvmName() == setterName }

private fun WinRTTypeDefinition.hasRuntimeClassBase(
    baseQualifiedName: String,
    typesByQualifiedName: Map<String, WinRTTypeDefinition>,
): Boolean {
    var currentBaseName = baseTypeName
    while (currentBaseName != null && !isWinRTObjectTypeName(currentBaseName)) {
        if (currentBaseName == baseQualifiedName) {
            return true
        }
        val currentBase = typesByQualifiedName[currentBaseName]
            ?: typesByQualifiedName.values.firstOrNull { it.name == currentBaseName || it.qualifiedName == currentBaseName }
            ?: return false
        currentBaseName = currentBase.baseTypeName
    }
    return false
}

private fun WinRTPropertyDefinition.setterJvmName(): String {
    val projectedName = name.replaceFirstChar(Char::lowercase)
    val setterStem = if (
        projectedName.startsWith("is") &&
        projectedName.length > 2 &&
        projectedName[2].isUpperCase()
    ) {
        projectedName.drop(2)
    } else {
        projectedName.replaceFirstChar(Char::uppercase)
    }
    return "set$setterStem"
}

internal enum class RuntimeObjectMethodKind {
    ToString,
    Equals,
    HashCode,
    Close,
}

internal data class RuntimeObjectMethodShape(
    val kind: RuntimeObjectMethodKind,
    val name: String,
    val returnType: TypeName,
    val parameters: List<ParameterSpec>,
)

internal fun runtimeObjectMethodShape(method: WinRTMethodDefinition): RuntimeObjectMethodShape? =
    when {
        method.name == "ToString" &&
            method.parameters.isEmpty() &&
            winRTFundamentalTypeForName(method.returnTypeName) == WinRTFundamentalType.String ->
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

internal fun closableMethodShape(
    slotInterfaceType: WinRTTypeDefinition,
    method: WinRTMethodDefinition,
): RuntimeObjectMethodShape? =
    if (
        slotInterfaceType.qualifiedName == "Windows.Foundation.IClosable" &&
        method.name == "Close" &&
        method.parameters.isEmpty() &&
        isWinRTVoidTypeName(method.returnTypeName)
    ) {
        RuntimeObjectMethodShape(
            kind = RuntimeObjectMethodKind.Close,
            name = "close",
            returnType = UNIT,
            parameters = emptyList(),
        )
    } else {
        null
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
    property: WinRTPropertyDefinition,
): PropertySpec? {
    val getterBinding = plan.instanceMemberBindings.firstOrNull {
        it.bindingName == "${property.name.uppercase()}_GETTER_SLOT"
    } ?: return null
    val getterSlotExpression = getterBinding.slotCodeBlock()
    val propertyTypeName = property.projectedPropertyTypeName(getterBinding.ownerInterfaceQualifiedName, plan.typesByQualifiedName)
    val builder = PropertySpec.builder(
        property.name.replaceFirstChar(Char::lowercase),
        resolveTypeName(propertyTypeName),
    ).mutable(!property.isReadOnly)
    builder.addModifiers(runtimeClassMemberModifiers(plan, getterBinding))
    val getterInvocation = renderReferencePropertyGetter(getterBinding)
        ?: renderProjectedObjectPropertyGetter(getterBinding)
        ?: renderScalarPropertyGetter(getterBinding)
        ?: renderInstanceNoArgIntrinsicInvocation(getterBinding)
        ?: renderInstanceStructResultIntrinsicInvocation(getterBinding)
        ?: renderInstanceArrayResultIntrinsicInvocation(
            referenceExpression = getterBinding.ownerCachePropertyName,
            slotExpression = getterSlotExpression,
            returnBinding = getterBinding.returnBinding,
            parameterBindings = getterBinding.parameterBindings,
            suppressHResultCheck = getterBinding.suppressHResultCheck,
        )
        ?: renderInstanceEnumResultIntrinsicInvocation(
            referenceExpression = getterBinding.ownerCachePropertyName,
            slotExpression = getterSlotExpression,
            returnBinding = getterBinding.returnBinding,
            parameterBindings = getterBinding.parameterBindings,
            suppressHResultCheck = getterBinding.suppressHResultCheck,
        )
        ?: renderBoundInvocation(binding = getterBinding)
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
                .addParameter("value", resolveTypeName(propertyTypeName))
                .addCode(
                    "%L\n",
                    setterBinding?.let {
                        renderReferencePropertySetter(it)
                            ?: renderInstanceOneArgUnitIntrinsicInvocation(it, argumentExpression = "value")
                            ?: renderInstanceStructOneArgUnitIntrinsicInvocation(
                                referenceExpression = it.ownerCachePropertyName,
                                slotExpression = it.slotCodeBlock(),
                                returnBinding = it.returnBinding,
                                parameterBindings = it.parameterBindings,
                                suppressHResultCheck = it.suppressHResultCheck,
                                argumentExpression = "value",
                            )
                            ?: renderInstanceEnumOneArgUnitIntrinsicInvocation(
                                referenceExpression = it.ownerCachePropertyName,
                                slotExpression = it.slotCodeBlock(),
                                returnBinding = it.returnBinding,
                                parameterBindings = it.parameterBindings,
                                suppressHResultCheck = it.suppressHResultCheck,
                                argumentExpression = "value",
                            )
                            ?: renderBoundInvocation(it)
                    }
                        ?: missingAbiBindingError("property ${property.name} setter"),
                )
                .build(),
        )
    }
    return builder.build()
}

internal fun WinRTMethodDefinition.receiveArrayResultParameter(): WinRTParameterDefinition? {
    if (returnTypeName != "Unit") {
        return null
    }
    val parameter = parameters.singleOrNull { candidate ->
        candidate.type.normalized().kind == WinRTTypeRefKind.Array &&
            candidate.typeIsByRef &&
            candidate.isOutParameter
    } ?: return null
    return if (parameters.lastOrNull() == parameter) parameter else null
}

internal fun WinRTMethodDefinition.projectedKotlinParameters(): List<WinRTParameterDefinition> =
    receiveArrayResultParameter()?.let { receiveArray -> parameters.filterNot { it == receiveArray } } ?: parameters

internal fun WinRTMethodDefinition.projectedKotlinReturnTypeName(): String =
    receiveArrayResultParameter()?.typeName ?: returnTypeName

private fun KotlinProjectionRenderer.renderInstanceOneArgUnitIntrinsicInvocation(
    binding: KotlinProjectionInstanceMemberBinding,
    argumentExpression: String? = null,
): CodeBlock? {
    if (
        !useProjectionIntrinsics ||
        binding.returnBinding.kind != KotlinProjectionAbiValueKind.Unit ||
        binding.parameterBindings.size != 1 ||
        binding.suppressHResultCheck
    ) {
        return null
    }
    val parameter = binding.parameterBindings.single()
    val helperFunction = when (parameter.typeBinding.kind) {
        KotlinProjectionAbiValueKind.String -> {
            if (parameter.typeBinding.typeName.endsWith("?")) return null
            "setString"
        }
        KotlinProjectionAbiValueKind.Boolean -> "setBoolean"
        KotlinProjectionAbiValueKind.Int32 -> "setInt32"
        KotlinProjectionAbiValueKind.UInt32 -> "setUInt32"
        KotlinProjectionAbiValueKind.Int64 -> "setInt64"
        KotlinProjectionAbiValueKind.UInt64 -> "setUInt64"
        KotlinProjectionAbiValueKind.Float -> "setFloat"
        KotlinProjectionAbiValueKind.Double -> "setDouble"
        else -> return null
    }
    modulePlatformAbiCalls?.scalarSetter(
        referenceExpression = binding.ownerCachePropertyName,
        slotExpression = binding.slotCodeBlock(),
        helperFunction = helperFunction,
        argumentExpression = CodeBlock.of("%L", argumentExpression ?: parameter.name),
    )?.let { return it }
    return CodeBlock.builder()
        .add("return %T.%L(\n", WINRT_PROJECTION_INTRINSIC_CLASS_NAME, helperFunction)
        .indent()
        .add("%L,\n", binding.ownerCachePropertyName)
        .add("%L,\n", binding.slotCodeBlock())
        .add("%L,\n", argumentExpression ?: parameter.name)
        .unindent()
        .add(")\n")
        .build()
}

internal fun KotlinProjectionRenderer.renderInstanceDescriptorUnitIntrinsicInvocation(
    referenceExpression: String,
    slotExpression: CodeBlock,
    returnBinding: KotlinProjectionAbiTypeBinding,
    parameterBindings: List<KotlinProjectionAbiParameterBinding>,
    suppressHResultCheck: Boolean,
    includeReturn: Boolean = true,
): CodeBlock? {
    if (
        !useProjectionIntrinsics ||
        returnBinding.kind != KotlinProjectionAbiValueKind.Unit ||
        parameterBindings.isEmpty() ||
        suppressHResultCheck
    ) {
        return null
    }
    val arguments = parameterBindings.map { parameter ->
        if (parameter.category != WinRTMetadataParameterCategory.In) {
            return null
        }
        descriptorIntrinsicArgument(parameter, includeStruct = true) ?: return null
    }
    if (arguments.count { it.shape == "String" } > 1) {
        return null
    }
    modulePlatformAbiCalls?.descriptorUnit(
        referenceExpression = referenceExpression,
        slotExpression = slotExpression,
        arguments = arguments,
        includeReturn = includeReturn,
    )?.let { return it }
    return CodeBlock.builder()
        .openDescriptorIntrinsicArgumentScopes(arguments)
        .add("%L%T.callUnit(\n", if (includeReturn) "return " else "", WINRT_PROJECTION_INTRINSIC_CLASS_NAME)
        .indent()
        .add("%L,\n", referenceExpression)
        .add("%L,\n", slotExpression)
        .add("%S,\n", arguments.joinToString(",") { it.shape })
        .addDescriptorIntrinsicArgumentExpressions(arguments)
        .unindent()
        .add(")\n")
        .closeDescriptorIntrinsicArgumentScopes(arguments)
        .build()
}

internal fun KotlinProjectionRenderer.renderInstanceDescriptorScalarIntrinsicInvocation(
    referenceExpression: String,
    slotExpression: CodeBlock,
    returnBinding: KotlinProjectionAbiTypeBinding,
    parameterBindings: List<KotlinProjectionAbiParameterBinding>,
    suppressHResultCheck: Boolean,
): CodeBlock? {
    if (
        !useProjectionIntrinsics ||
        suppressHResultCheck ||
        parameterBindings.isEmpty()
    ) {
        return null
    }
    val returnShape = scalarIntrinsicReturnShape(returnBinding) ?: return null
    val arguments = parameterBindings.map { parameter ->
        if (parameter.category != WinRTMetadataParameterCategory.In) {
            return null
        }
        descriptorIntrinsicArgument(parameter, includeStruct = true) ?: return null
    }
    modulePlatformAbiCalls?.descriptorScalar(
        referenceExpression = referenceExpression,
        slotExpression = slotExpression,
        returnShape = returnShape,
        arguments = arguments,
    )?.let { return it }
    return CodeBlock.builder()
        .openDescriptorIntrinsicArgumentScopes(arguments)
        .add("return %T.callScalar(\n", WINRT_PROJECTION_INTRINSIC_CLASS_NAME)
        .indent()
        .add("%L,\n", referenceExpression)
        .add("%L,\n", slotExpression)
        .add("%S,\n", returnShape)
        .add("%S,\n", arguments.joinToString(",") { it.shape })
        .addDescriptorIntrinsicArgumentExpressions(arguments)
        .unindent()
        .add(")\n")
        .closeDescriptorIntrinsicArgumentScopes(arguments)
        .build()
}

internal fun KotlinProjectionRenderer.renderInstanceDescriptorBooleanIntrinsicInvocation(
    referenceExpression: String,
    slotExpression: CodeBlock,
    returnBinding: KotlinProjectionAbiTypeBinding,
    parameterBindings: List<KotlinProjectionAbiParameterBinding>,
    suppressHResultCheck: Boolean,
): CodeBlock? {
    if (
        !useProjectionIntrinsics ||
        suppressHResultCheck ||
        returnBinding.kind != KotlinProjectionAbiValueKind.Boolean ||
        parameterBindings.isEmpty()
    ) {
        return null
    }
    val arguments = parameterBindings.map { parameter ->
        if (parameter.category != WinRTMetadataParameterCategory.In) {
            return null
        }
        descriptorIntrinsicArgument(parameter, includeStruct = true) ?: return null
    }
    modulePlatformAbiCalls?.descriptorBoolean(
        referenceExpression = referenceExpression,
        slotExpression = slotExpression,
        arguments = arguments,
    )?.let { return it }
    return CodeBlock.builder()
        .openDescriptorIntrinsicArgumentScopes(arguments)
        .add("return %T.callBoolean(\n", WINRT_PROJECTION_INTRINSIC_CLASS_NAME)
        .indent()
        .add("%L,\n", referenceExpression)
        .add("%L,\n", slotExpression)
        .add("%S,\n", arguments.joinToString(",") { it.shape })
        .addDescriptorIntrinsicArgumentExpressions(arguments)
        .unindent()
        .add(")\n")
        .closeDescriptorIntrinsicArgumentScopes(arguments)
        .build()
}

internal fun KotlinProjectionRenderer.renderInstanceDescriptorProjectedObjectIntrinsicInvocation(
    referenceExpression: String,
    slotExpression: CodeBlock,
    returnBinding: KotlinProjectionAbiTypeBinding,
    parameterBindings: List<KotlinProjectionAbiParameterBinding>,
    suppressHResultCheck: Boolean,
): CodeBlock? {
    if (
        !useProjectionIntrinsics ||
        suppressHResultCheck ||
        parameterBindings.isEmpty() ||
        customObjectAbi(returnBinding) != null
    ) {
        return null
    }
    val helperFunction = when (returnBinding.kind) {
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass -> "callProjectedRuntimeClass"
        KotlinProjectionAbiValueKind.ProjectedInterface -> "callProjectedInterface"
        else -> return null
    }
    val returnType = resolvedReturnClassName(returnBinding) ?: return null
    val arguments = parameterBindings.map { parameter ->
        if (parameter.category != WinRTMetadataParameterCategory.In) {
            return null
        }
        descriptorIntrinsicArgument(parameter, includeStruct = true) ?: return null
    }
    if (returnBinding.typeArguments.isEmpty()) {
        modulePlatformAbiCalls?.descriptorProjectedObject(
            referenceExpression = referenceExpression,
            slotExpression = slotExpression,
            helperFunction = helperFunction,
            returnType = resolveTypeName(returnBinding.typeName),
            wrapType = returnType,
            arguments = arguments,
        )?.let { return it }
    }
    return CodeBlock.builder()
        .openDescriptorIntrinsicArgumentScopes(arguments)
        .add("return %T.%L(\n", WINRT_PROJECTION_INTRINSIC_CLASS_NAME, helperFunction)
        .indent()
        .add("%L,\n", referenceExpression)
        .add("%L,\n", slotExpression)
        .add("%S,\n", arguments.joinToString(",") { it.shape })
        .add("%T.Metadata::wrap,\n", returnType)
        .addDescriptorIntrinsicArgumentExpressions(arguments)
        .unindent()
        .add(")\n")
        .closeDescriptorIntrinsicArgumentScopes(arguments)
        .build()
}

internal fun KotlinProjectionRenderer.renderInstanceDescriptorAsyncIntrinsicInvocation(
    referenceExpression: String,
    slotExpression: CodeBlock,
    returnBinding: KotlinProjectionAbiTypeBinding,
    parameterBindings: List<KotlinProjectionAbiParameterBinding>,
    suppressHResultCheck: Boolean,
): CodeBlock? {
    if (
        !useProjectionIntrinsics ||
        suppressHResultCheck ||
        parameterBindings.isEmpty()
    ) {
        return null
    }
    val arguments = parameterBindings.map { parameter ->
        if (parameter.category != WinRTMetadataParameterCategory.In) {
            return null
        }
        descriptorIntrinsicArgument(parameter, includeStruct = true) ?: return null
    }
    val asyncExpression = asyncReferenceExpression(
        returnBinding = returnBinding,
        pointerExpression = CodeBlock.of("%T.fromRawComPtr(__asyncReference.pointer)", PLATFORM_ABI_CLASS_NAME),
    ) ?: return null
    return CodeBlock.builder()
        .openDescriptorIntrinsicArgumentScopes(arguments)
        .add("return %T.callProjectedInterface(\n", WINRT_PROJECTION_INTRINSIC_CLASS_NAME)
        .indent()
        .add("%L,\n", referenceExpression)
        .add("%L,\n", slotExpression)
        .add("%S,\n", arguments.joinToString(",") { it.shape })
        .add("{ __asyncReference ->\n")
        .indent()
        .add("%L\n", asyncExpression)
        .unindent()
        .add("},\n")
        .addDescriptorIntrinsicArgumentExpressions(arguments)
        .unindent()
        .add(")\n")
        .closeDescriptorIntrinsicArgumentScopes(arguments)
        .build()
}

internal fun scalarIntrinsicReturnShape(binding: KotlinProjectionAbiTypeBinding): String? =
    when (binding.kind) {
        KotlinProjectionAbiValueKind.Int8 -> "Int8"
        KotlinProjectionAbiValueKind.UInt8 -> "UInt8"
        KotlinProjectionAbiValueKind.Int16 -> "Int16"
        KotlinProjectionAbiValueKind.UInt16 -> "UInt16"
        KotlinProjectionAbiValueKind.Int32 -> "Int32"
        KotlinProjectionAbiValueKind.UInt32 -> "UInt32"
        KotlinProjectionAbiValueKind.Int64 -> "Int64"
        KotlinProjectionAbiValueKind.UInt64 -> "UInt64"
        KotlinProjectionAbiValueKind.Float -> "Float"
        KotlinProjectionAbiValueKind.Double -> "Double"
        else -> null
    }

internal data class DescriptorIntrinsicArgument(
    val shape: String,
    val expressions: List<CodeBlock>,
    val scopeOpeners: List<CodeBlock> = emptyList(),
)

internal fun KotlinProjectionRenderer.descriptorIntrinsicArgument(
    parameter: KotlinProjectionAbiParameterBinding,
    includeStruct: Boolean = false,
): DescriptorIntrinsicArgument? {
    val binding = parameter.typeBinding
    descriptorCollectionIntrinsicArgument(parameter)?.let { return it }
    val shape = if (includeStruct) {
        descriptorStructCapableArgumentShape(binding)
    } else {
        descriptorIntrinsicArgumentShape(binding)
    } ?: return null
    if (shape == "Object" && binding.kind == KotlinProjectionAbiValueKind.ProjectedRuntimeClass) {
        val interfaceId = binding.interfaceId ?: return null
        val marshalerName = generatedLocalIdentifier("__", parameter.name, "ProjectionMarshaler")
        return DescriptorIntrinsicArgument(
            shape = "RawAddress",
            expressions = listOf(CodeBlock.of("%L.abi", marshalerName)),
            scopeOpeners = listOf(
                CodeBlock.of(
                    "%M(%N, %S, %T(%S)).use { %L ->",
                    WINRT_PROJECTION_MARSHALER_FUNCTION_NAME,
                    kotlinPoetNameLiteral(parameter.name),
                    binding.resolvedTypeName,
                    GUID_CLASS_NAME,
                    interfaceId.toString(),
                    marshalerName,
                ),
            ),
        )
    }
    return when {
        shape == "Object" -> DescriptorIntrinsicArgument(
            shape = shape,
            expressions = listOf(CodeBlock.of("%L as %T", parameter.name, IWINRT_OBJECT_CLASS_NAME)),
        )
        shape.startsWith("Struct") -> {
            val structType = nativeStructAdapterClassName(binding) ?: return null
            DescriptorIntrinsicArgument(
                shape = shape,
                expressions = listOf(CodeBlock.of("%L", parameter.name), CodeBlock.of("%T.Metadata", structType)),
            )
        }
        binding.kind == KotlinProjectionAbiValueKind.Enum -> DescriptorIntrinsicArgument(
            shape = shape,
            expressions = listOf(CodeBlock.of("%L.abiValue", parameter.name)),
        )
        else -> DescriptorIntrinsicArgument(
            shape = shape,
            expressions = listOf(CodeBlock.of("%L", parameter.name)),
        )
    }
}

private fun KotlinProjectionRenderer.descriptorCollectionIntrinsicArgument(
    parameter: KotlinProjectionAbiParameterBinding,
): DescriptorIntrinsicArgument? {
    val marshaler = when (parameter.typeBinding.kind) {
        KotlinProjectionAbiValueKind.MappedBindableIterable,
        KotlinProjectionAbiValueKind.MappedBindableVector,
        KotlinProjectionAbiValueKind.MappedBindableVectorView -> bindableCollectionParameterMarshaler(parameter)
        KotlinProjectionAbiValueKind.MappedIterable,
        KotlinProjectionAbiValueKind.MappedVector,
        KotlinProjectionAbiValueKind.MappedMap,
        KotlinProjectionAbiValueKind.MappedVectorView,
        KotlinProjectionAbiValueKind.MappedMapView -> mappedCollectionParameterMarshaler(parameter)
        else -> return null
    } ?: return null
    if (
        marshaler.abiArgumentKind != KotlinProjectionComArgumentKind.Pointer ||
        marshaler.extraAbiArgumentExpressions.isNotEmpty() ||
        marshaler.postCallStatements.isNotEmpty() ||
        marshaler.finallyStatements.isNotEmpty() ||
        marshaler.resultAllocation != null ||
        marshaler.resultLocalDeclarations != null ||
        marshaler.readbackStatement != null
    ) {
        return null
    }
    return DescriptorIntrinsicArgument(
        shape = "RawAddress",
        expressions = listOf(marshaler.abiArgumentExpression),
        scopeOpeners = marshaler.scopeOpeners,
    )
}

internal fun CodeBlock.Builder.openDescriptorIntrinsicArgumentScopes(
    arguments: List<DescriptorIntrinsicArgument>,
): CodeBlock.Builder {
    arguments.flatMap(DescriptorIntrinsicArgument::scopeOpeners).forEach { scopeOpener ->
        add("%L\n", scopeOpener)
        indent()
    }
    return this
}

internal fun CodeBlock.Builder.closeDescriptorIntrinsicArgumentScopes(
    arguments: List<DescriptorIntrinsicArgument>,
): CodeBlock.Builder {
    repeat(arguments.sumOf { it.scopeOpeners.size }) {
        unindent()
        add("}\n")
    }
    return this
}

internal fun CodeBlock.Builder.addDescriptorIntrinsicArgumentExpressions(
    arguments: List<DescriptorIntrinsicArgument>,
): CodeBlock.Builder {
    arguments.forEach { argument ->
        argument.expressions.forEach { expression ->
            add("%L,\n", expression)
        }
    }
    return this
}

internal fun descriptorIntrinsicArgumentShape(binding: KotlinProjectionAbiTypeBinding): String? =
    when (binding.kind) {
        KotlinProjectionAbiValueKind.Int32 ->
            if (binding.typeName.endsWith("?")) null else "Int32"
        KotlinProjectionAbiValueKind.UInt32 ->
            if (binding.typeName.endsWith("?")) null else "UInt32"
        KotlinProjectionAbiValueKind.Int64 ->
            if (binding.typeName.endsWith("?")) null else "Int64"
        KotlinProjectionAbiValueKind.UInt64 ->
            if (binding.typeName.endsWith("?")) null else "UInt64"
        KotlinProjectionAbiValueKind.Float ->
            if (binding.typeName.endsWith("?")) null else "Float"
        KotlinProjectionAbiValueKind.Double ->
            if (binding.typeName.endsWith("?")) null else "Double"
        KotlinProjectionAbiValueKind.Boolean ->
            if (binding.typeName.endsWith("?")) null else "Boolean"
        KotlinProjectionAbiValueKind.Enum ->
            binding.enumUnderlyingType?.let(::integralProjectionIntrinsicShapeName)
        KotlinProjectionAbiValueKind.String ->
            if (binding.typeName.endsWith("?")) null else "String"
        KotlinProjectionAbiValueKind.ProjectedInterface,
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass ->
            if (
                binding.typeName.endsWith("?") ||
                binding.typeArguments.isNotEmpty() ||
                binding.typeName != binding.resolvedTypeName ||
                '.' !in binding.typeName ||
                mappedTypeByAbiName(binding.resolvedTypeName)?.customObjectAbi != null ||
                mappedTypeByAbiName(binding.typeName)?.customObjectAbi != null
            ) {
                null
            } else {
                "Object"
            }
        else -> null
    }

internal fun KotlinProjectionRenderer.descriptorStructCapableArgumentShape(binding: KotlinProjectionAbiTypeBinding): String? =
    when (binding.kind) {
        KotlinProjectionAbiValueKind.Struct ->
            if (binding.typeName.endsWith("?") || nativeStructAdapterClassName(binding) == null) {
                null
            } else {
                descriptorByValueStructArgumentShape(binding)
            }
        else -> descriptorIntrinsicArgumentShape(binding)
    }

private fun descriptorByValueStructArgumentShape(binding: KotlinProjectionAbiTypeBinding): String? =
    binding.abiSize
        ?.takeIf { it > 0 }
        ?.let { size ->
            binding.abiAlignment
                ?.takeIf { it > 0 }
                ?.let { alignment -> "Struct${size}_${alignment}" }
        }

private fun KotlinProjectionRenderer.renderProjectedObjectPropertyGetter(
    binding: KotlinProjectionInstanceMemberBinding,
): CodeBlock? {
    if (binding.parameterBindings.isNotEmpty() || binding.suppressHResultCheck) {
        return null
    }
    return renderInstanceProjectedObjectGetterInvocation(
        referenceExpression = binding.ownerCachePropertyName,
        slotExpression = binding.slotCodeBlock(),
        returnBinding = binding.returnBinding,
    )
}

private fun KotlinProjectionRenderer.renderScalarPropertyGetter(
    binding: KotlinProjectionInstanceMemberBinding,
): CodeBlock? {
    if (binding.parameterBindings.isNotEmpty()) {
        return null
    }
    val helperFunction = when (binding.returnBinding.kind) {
        KotlinProjectionAbiValueKind.Boolean ->
            if (binding.suppressHResultCheck) "getNoExceptionBoolean" else "getBoolean"
        KotlinProjectionAbiValueKind.Int32 -> "getInt32"
        KotlinProjectionAbiValueKind.UInt32 -> "getUInt32"
        KotlinProjectionAbiValueKind.Int64 -> "getInt64"
        KotlinProjectionAbiValueKind.UInt64 -> "getUInt64"
        KotlinProjectionAbiValueKind.Float -> "getFloat"
        KotlinProjectionAbiValueKind.Double -> "getDouble"
        else -> return null
    }
    return renderInstanceScalarGetterInvocation(
        referenceExpression = binding.ownerCachePropertyName,
        slotExpression = binding.slotCodeBlock(),
        helperFunction = helperFunction,
        intrinsic = useProjectionIntrinsics,
        modulePlatformAbiCalls = modulePlatformAbiCalls,
    )
}

internal fun renderInstanceScalarGetterInvocation(
    referenceExpression: String,
    slotExpression: CodeBlock,
    helperFunction: String,
    @Suppress("UNUSED_PARAMETER")
    intrinsic: Boolean = false,
    modulePlatformAbiCalls: KotlinModulePlatformAbiCallSupport? = null,
): CodeBlock {
    modulePlatformAbiCalls?.scalarGetter(
        referenceExpression = referenceExpression,
        slotExpression = slotExpression,
        helperFunction = helperFunction,
    )?.let { return it }
    return CodeBlock.builder()
        .add(
            "return %T.%L(\n",
            WINRT_PROJECTION_INTRINSIC_CLASS_NAME,
            helperFunction,
        )
        .indent()
        .add("%L,\n", referenceExpression)
        .add("%L,\n", slotExpression)
        .unindent()
        .add(")\n")
        .build()
}

internal fun KotlinProjectionRenderer.renderInstanceProjectedObjectGetterInvocation(
    referenceExpression: String,
    slotExpression: CodeBlock,
    returnBinding: KotlinProjectionAbiTypeBinding,
    @Suppress("UNUSED_PARAMETER")
    intrinsic: Boolean = useProjectionIntrinsics,
): CodeBlock? {
    if (customObjectAbi(returnBinding) != null) {
        return null
    }
    val helperFunction = when (returnBinding.kind) {
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass ->
            if (returnBinding.isNullableAbiReturn) "getNullableProjectedRuntimeClass" else "getProjectedRuntimeClass"
        KotlinProjectionAbiValueKind.ProjectedInterface ->
            if (returnBinding.isNullableAbiReturn) "getNullableProjectedInterface" else "getProjectedInterface"
        else -> return null
    }
    val returnType = resolvedReturnClassName(returnBinding) ?: return null
    if (returnBinding.typeArguments.isEmpty()) {
        modulePlatformAbiCalls?.projectedObjectGetter(
            referenceExpression = referenceExpression,
            slotExpression = slotExpression,
            helperFunction = helperFunction,
            returnType = resolveTypeName(returnBinding.typeName),
            wrapType = returnType,
        )?.let { return it }
    }
    return CodeBlock.builder()
        .add(
            "return %T.%L(\n",
            WINRT_PROJECTION_INTRINSIC_CLASS_NAME,
            helperFunction,
        )
        .indent()
        .add("%L,\n", referenceExpression)
        .add("%L,\n", slotExpression)
        .add("%T.Metadata::wrap,\n", returnType)
        .unindent()
        .add(")\n")
        .build()
}

private fun KotlinProjectionRenderer.renderReferencePropertyGetter(
    binding: KotlinProjectionInstanceMemberBinding,
): CodeBlock? {
    if (
        binding.parameterBindings.isNotEmpty() ||
        binding.suppressHResultCheck ||
        binding.returnBinding.kind != KotlinProjectionAbiValueKind.Reference
    ) {
        return null
    }
    val interfaceId = referenceInterfaceIdCode(binding.returnBinding) ?: return null
    return CodeBlock.builder()
        .add("return %T.getReferenceValue(\n", WINRT_REFERENCE_PROJECTION_INTEROP_CLASS_NAME)
        .indent()
        .add("%L,\n", binding.ownerCachePropertyName)
        .add("%L,\n", binding.slotCodeBlock())
        .add("%L,\n", interfaceId)
        .unindent()
        .add(")\n")
        .build()
}

private fun KotlinProjectionRenderer.renderReferencePropertySetter(
    binding: KotlinProjectionInstanceMemberBinding,
): CodeBlock? {
    val valueBinding = binding.parameterBindings.singleOrNull() ?: return null
    if (
        binding.suppressHResultCheck ||
        binding.returnBinding.kind != KotlinProjectionAbiValueKind.Unit ||
        valueBinding.name != "value" ||
        valueBinding.typeBinding.kind != KotlinProjectionAbiValueKind.Reference
    ) {
        return null
    }
    val interfaceId = referenceInterfaceIdCode(valueBinding.typeBinding) ?: return null
    return CodeBlock.builder()
        .add("%T.setReferenceValue(\n", WINRT_REFERENCE_PROJECTION_INTEROP_CLASS_NAME)
        .indent()
        .add("%L,\n", binding.ownerCachePropertyName)
        .add("%L,\n", binding.slotCodeBlock())
        .add("value,\n")
        .add("%L,\n", interfaceId)
        .unindent()
        .add(")\n")
        .build()
}

internal fun missingAbiBindingError(memberName: String): CodeBlock =
    CodeBlock.of(
        "error(%S)",
        "WinRT ABI binding is unavailable for $memberName; projection metadata did not provide a matching interface slot.",
    )

internal fun KotlinProjectionInstanceMemberBinding.slotCodeBlock(): CodeBlock =
    slot?.let { CodeBlock.of("%L", it) } ?: CodeBlock.of("Metadata.%L", bindingName)

internal fun KotlinProjectionInstanceMemberBinding.slotExpressionString(): String =
    slot?.toString() ?: "Metadata.$bindingName"

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

internal fun authoringInvokeBridgeName(method: WinRTMethodDefinition): String =
    "__winrtAuthoringInvoke${method.name}"

internal fun KotlinProjectionRenderer.renderBoundInvocation(
    binding: KotlinProjectionInstanceMemberBinding,
    returnBinding: KotlinProjectionAbiTypeBinding = binding.returnBinding,
    parameterBindings: List<KotlinProjectionAbiParameterBinding> = binding.parameterBindings,
    marshalerPlanDescriptor: WinRTAbiMarshalerPlanDescriptor? = binding.marshalerPlanDescriptor,
): CodeBlock {
    val callPlan = requireAbiCallPlan(
        bindingName = binding.bindingName,
        returnBinding = returnBinding,
        parameterBindings = parameterBindings,
        marshalerPlanDescriptor = marshalerPlanDescriptor,
        suppressHResultCheck = binding.suppressHResultCheck,
    )
    return renderInlineAbiInvocation(
        invokeTargetExpression = binding.ownerCachePropertyName,
        slotExpression = binding.slotExpressionString(),
        callPlan = callPlan,
    ) ?: error("Generator ABI marshaler parity failed to emit ${binding.bindingName}")
}

private fun KotlinProjectionRenderer.renderInstanceNoArgIntrinsicInvocation(
    binding: KotlinProjectionInstanceMemberBinding,
): CodeBlock? {
    if (!useProjectionIntrinsics || binding.parameterBindings.isNotEmpty()) {
        return null
    }
    val helperFunction = when (binding.returnBinding.kind) {
        KotlinProjectionAbiValueKind.Unit -> return null
        KotlinProjectionAbiValueKind.String -> "getString"
        KotlinProjectionAbiValueKind.Boolean ->
            if (binding.suppressHResultCheck) "getNoExceptionBoolean" else "getBoolean"
        KotlinProjectionAbiValueKind.Int32 -> "getInt32"
        KotlinProjectionAbiValueKind.UInt32 -> "getUInt32"
        KotlinProjectionAbiValueKind.Int64 -> "getInt64"
        KotlinProjectionAbiValueKind.UInt64 -> "getUInt64"
        KotlinProjectionAbiValueKind.Float -> "getFloat"
        KotlinProjectionAbiValueKind.Double -> "getDouble"
        else -> return null
    }
    return renderInstanceScalarGetterInvocation(
        referenceExpression = binding.ownerCachePropertyName,
        slotExpression = binding.slotCodeBlock(),
        helperFunction = helperFunction,
        intrinsic = true,
        modulePlatformAbiCalls = modulePlatformAbiCalls,
    )
}

private fun KotlinProjectionRenderer.renderInstanceStructResultIntrinsicInvocation(
    binding: KotlinProjectionInstanceMemberBinding,
): CodeBlock? =
    renderInstanceStructResultIntrinsicInvocation(
        referenceExpression = binding.ownerCachePropertyName,
        slotExpression = binding.slotCodeBlock(),
        returnBinding = binding.returnBinding,
        parameterBindings = binding.parameterBindings,
        suppressHResultCheck = binding.suppressHResultCheck,
    )

private fun KotlinProjectionRenderer.renderInstanceStructResultIntrinsicInvocation(
    referenceExpression: String,
    slotExpression: CodeBlock,
    returnBinding: KotlinProjectionAbiTypeBinding,
    parameterBindings: List<KotlinProjectionAbiParameterBinding>,
    suppressHResultCheck: Boolean,
): CodeBlock? {
    if (
        !useProjectionIntrinsics ||
        returnBinding.kind != KotlinProjectionAbiValueKind.Struct ||
        suppressHResultCheck
    ) {
        return null
    }
    if (customStructAbi(returnBinding) != null) {
        return null
    }
    val structType = nativeStructClassName(returnBinding) ?: return null
    if (parameterBindings.isNotEmpty()) {
        val arguments = parameterBindings.map { parameter ->
            if (parameter.category != WinRTMetadataParameterCategory.In) {
                return null
            }
            descriptorIntrinsicArgument(parameter, includeStruct = true) ?: return null
        }
        modulePlatformAbiCalls?.descriptorStruct(
            referenceExpression = referenceExpression,
            slotExpression = slotExpression,
            adapterExpression = CodeBlock.of("%T.Metadata", structType),
            arguments = arguments,
        )?.let { return it }
        return CodeBlock.builder()
            .openDescriptorIntrinsicArgumentScopes(arguments)
            .add("return %T.callStruct(\n", WINRT_PROJECTION_INTRINSIC_CLASS_NAME)
            .indent()
            .add("%L,\n", referenceExpression)
            .add("%L,\n", slotExpression)
            .add("%S,\n", arguments.joinToString(",") { it.shape })
            .add("%T.Metadata,\n", structType)
            .addDescriptorIntrinsicArgumentExpressions(arguments)
            .unindent()
            .add(")\n")
            .closeDescriptorIntrinsicArgumentScopes(arguments)
            .build()
    }
    modulePlatformAbiCalls?.structGetter(
        referenceExpression = referenceExpression,
        slotExpression = slotExpression,
        adapterExpression = CodeBlock.of("%T.Metadata", structType),
    )?.let { return it }
    return CodeBlock.builder()
        .add("return %T.getStruct(\n", WINRT_PROJECTION_INTRINSIC_CLASS_NAME)
        .indent()
        .add("%L,\n", referenceExpression)
        .add("%L,\n", slotExpression)
        .add("%T.Metadata,\n", structType)
        .unindent()
        .add(")\n")
        .build()
}

internal fun KotlinProjectionRenderer.renderInstanceEnumResultIntrinsicInvocation(
    referenceExpression: String,
    slotExpression: CodeBlock,
    returnBinding: KotlinProjectionAbiTypeBinding,
    parameterBindings: List<KotlinProjectionAbiParameterBinding>,
    suppressHResultCheck: Boolean,
): CodeBlock? {
    if (
        !useProjectionIntrinsics ||
        returnBinding.kind != KotlinProjectionAbiValueKind.Enum ||
        suppressHResultCheck
    ) {
        return null
    }
    val enumType = resolvedReturnClassName(returnBinding) ?: return null
    val integralType = returnBinding.enumUnderlyingType ?: return null
    val helperFunction = integralProjectionIntrinsicGetterName(integralType) ?: return null
    if (parameterBindings.isNotEmpty()) {
        val returnShape = integralProjectionIntrinsicShapeName(integralType) ?: return null
        val arguments = parameterBindings.map { parameter ->
            if (parameter.category != WinRTMetadataParameterCategory.In) {
                return null
            }
            descriptorIntrinsicArgument(parameter) ?: return null
        }
        return CodeBlock.builder()
            .openDescriptorIntrinsicArgumentScopes(arguments)
            .add("return %T.Metadata.fromAbi(\n", enumType)
            .indent()
            .add("%T.callScalar(\n", WINRT_PROJECTION_INTRINSIC_CLASS_NAME)
            .indent()
            .add("%L,\n", referenceExpression)
            .add("%L,\n", slotExpression)
            .add("%S,\n", returnShape)
            .add("%S,\n", arguments.joinToString(",") { it.shape })
            .addDescriptorIntrinsicArgumentExpressions(arguments)
            .unindent()
            .add("),\n")
            .unindent()
            .add(")\n")
            .closeDescriptorIntrinsicArgumentScopes(arguments)
            .build()
    }
    return CodeBlock.builder()
        .add("return %T.Metadata.fromAbi(\n", enumType)
        .indent()
        .add("%T.%L(\n", WINRT_PROJECTION_INTRINSIC_CLASS_NAME, helperFunction)
        .indent()
        .add("%L,\n", referenceExpression)
        .add("%L,\n", slotExpression)
        .unindent()
        .add("),\n")
        .unindent()
        .add(")\n")
        .build()
}

internal fun KotlinProjectionRenderer.renderInstanceArrayResultIntrinsicInvocation(
    referenceExpression: String,
    slotExpression: CodeBlock,
    returnBinding: KotlinProjectionAbiTypeBinding,
    parameterBindings: List<KotlinProjectionAbiParameterBinding>,
    suppressHResultCheck: Boolean,
): CodeBlock? {
    if (
        !useProjectionIntrinsics ||
        returnBinding.kind != KotlinProjectionAbiValueKind.Array ||
        parameterBindings.isNotEmpty() ||
        suppressHResultCheck
    ) {
        return null
    }
    val elementBinding = returnBinding.typeArguments.singleOrNull() ?: return null
    val marshaler = arrayElementMarshalerExpression(elementBinding) ?: return null
    return CodeBlock.builder()
        .add("return %T.getArray(\n", WINRT_PROJECTION_INTRINSIC_CLASS_NAME)
        .indent()
        .add("%L,\n", referenceExpression)
        .add("%L,\n", slotExpression)
        .add("%L,\n", marshaler)
        .unindent()
        .add(").toTypedArray() as %T\n", resolveTypeName(returnBinding.typeName))
        .build()
}

internal fun KotlinProjectionRenderer.renderInstanceEnumOneArgUnitIntrinsicInvocation(
    referenceExpression: String,
    slotExpression: CodeBlock,
    returnBinding: KotlinProjectionAbiTypeBinding,
    parameterBindings: List<KotlinProjectionAbiParameterBinding>,
    suppressHResultCheck: Boolean,
    argumentExpression: String? = null,
): CodeBlock? {
    if (
        !useProjectionIntrinsics ||
        returnBinding.kind != KotlinProjectionAbiValueKind.Unit ||
        parameterBindings.size != 1 ||
        suppressHResultCheck
    ) {
        return null
    }
    val parameter = parameterBindings.single()
    val helperFunction = when (parameter.typeBinding.kind) {
        KotlinProjectionAbiValueKind.Enum ->
            parameter.typeBinding.enumUnderlyingType?.let(::integralProjectionIntrinsicSetterName)
                ?: return null
        else -> return null
    }
    return CodeBlock.builder()
        .add("return %T.%L(\n", WINRT_PROJECTION_INTRINSIC_CLASS_NAME, helperFunction)
        .indent()
        .add("%L,\n", referenceExpression)
        .add("%L,\n", slotExpression)
        .add("%L.abiValue,\n", argumentExpression ?: parameter.name)
        .unindent()
        .add(")\n")
        .build()
}

internal fun KotlinProjectionRenderer.renderInstanceStructOneArgUnitIntrinsicInvocation(
    referenceExpression: String,
    slotExpression: CodeBlock,
    returnBinding: KotlinProjectionAbiTypeBinding,
    parameterBindings: List<KotlinProjectionAbiParameterBinding>,
    suppressHResultCheck: Boolean,
    argumentExpression: String? = null,
): CodeBlock? {
    if (
        !useProjectionIntrinsics ||
        returnBinding.kind != KotlinProjectionAbiValueKind.Unit ||
        parameterBindings.size != 1 ||
        suppressHResultCheck
    ) {
        return null
    }
    val parameter = parameterBindings.single()
    if (parameter.typeBinding.kind != KotlinProjectionAbiValueKind.Struct || customStructAbi(parameter.typeBinding) != null) {
        return null
    }
    val structType = nativeStructClassName(parameter.typeBinding) ?: return null
    modulePlatformAbiCalls?.structSetter(
        referenceExpression = referenceExpression,
        slotExpression = slotExpression,
        valueExpression = CodeBlock.of("%L", argumentExpression ?: parameter.name),
        adapterExpression = CodeBlock.of("%T.Metadata", structType),
    )?.let { return it }
    return CodeBlock.builder()
        .add("return %T.setStruct(\n", WINRT_PROJECTION_INTRINSIC_CLASS_NAME)
        .indent()
        .add("%L,\n", referenceExpression)
        .add("%L,\n", slotExpression)
        .add("%L,\n", argumentExpression ?: parameter.name)
        .add("%T.Metadata,\n", structType)
        .unindent()
        .add(")\n")
        .build()
}

internal fun KotlinProjectionRenderer.renderBoundStaticInvocation(
    binding: KotlinProjectionStaticMemberBinding,
): CodeBlock {
    renderStaticIntrinsicInvocation(binding)?.let { return it }
    val callPlan = requireAbiCallPlan(
        bindingName = binding.bindingName,
        returnBinding = binding.returnBinding,
        parameterBindings = binding.parameterBindings,
        marshalerPlanDescriptor = binding.marshalerPlanDescriptor,
        suppressHResultCheck = binding.suppressHResultCheck,
    )
    return renderInlineAbiInvocation(
        invokeTargetExpression = "StaticInterfaces.${binding.ownerAccessorName}()",
        slotExpression = writeTimeSlotCodeBlock(binding),
        callPlan = callPlan,
    ) ?: error("Generator ABI marshaler parity failed to emit ${binding.bindingName}")
}

internal fun KotlinProjectionRenderer.renderBoundStaticInvocationOrNull(
    binding: KotlinProjectionStaticMemberBinding,
): CodeBlock? {
    renderStaticIntrinsicInvocation(binding)?.let { return it }
    val callPlan = buildAbiCallPlan(
        returnBinding = binding.returnBinding,
        parameterBindings = binding.parameterBindings,
        marshalerPlanDescriptor = binding.marshalerPlanDescriptor,
        suppressHResultCheck = binding.suppressHResultCheck,
    ) ?: return null
    return renderInlineAbiInvocation(
        invokeTargetExpression = "StaticInterfaces.${binding.ownerAccessorName}()",
        slotExpression = writeTimeSlotCodeBlock(binding),
        callPlan = callPlan,
    )
}

private fun KotlinProjectionRenderer.renderStaticIntrinsicInvocation(
    binding: KotlinProjectionStaticMemberBinding,
): CodeBlock? {
    val referenceExpression = "StaticInterfaces.${binding.ownerAccessorName}()"
    val slotExpression = writeTimeSlotCodeBlock(binding)
    return renderStaticNoArgIntrinsicInvocation(
        referenceExpression = referenceExpression,
        slotExpression = slotExpression,
        returnBinding = binding.returnBinding,
        parameterBindings = binding.parameterBindings,
        suppressHResultCheck = binding.suppressHResultCheck,
    )
        ?: renderInstanceStructResultIntrinsicInvocation(
            referenceExpression = referenceExpression,
            slotExpression = slotExpression,
            returnBinding = binding.returnBinding,
            parameterBindings = binding.parameterBindings,
            suppressHResultCheck = binding.suppressHResultCheck,
        )
        ?: renderInstanceDescriptorUnitIntrinsicInvocation(
            referenceExpression = referenceExpression,
            slotExpression = slotExpression,
            returnBinding = binding.returnBinding,
            parameterBindings = binding.parameterBindings,
            suppressHResultCheck = binding.suppressHResultCheck,
        )
        ?: renderInstanceDescriptorBooleanIntrinsicInvocation(
            referenceExpression = referenceExpression,
            slotExpression = slotExpression,
            returnBinding = binding.returnBinding,
            parameterBindings = binding.parameterBindings,
            suppressHResultCheck = binding.suppressHResultCheck,
        )
        ?: renderInstanceDescriptorScalarIntrinsicInvocation(
            referenceExpression = referenceExpression,
            slotExpression = slotExpression,
            returnBinding = binding.returnBinding,
            parameterBindings = binding.parameterBindings,
            suppressHResultCheck = binding.suppressHResultCheck,
        )
        ?: renderInstanceDescriptorProjectedObjectIntrinsicInvocation(
            referenceExpression = referenceExpression,
            slotExpression = slotExpression,
            returnBinding = binding.returnBinding,
            parameterBindings = binding.parameterBindings,
            suppressHResultCheck = binding.suppressHResultCheck,
        )
        ?: renderInstanceStructOneArgUnitIntrinsicInvocation(
            referenceExpression = referenceExpression,
            slotExpression = slotExpression,
            returnBinding = binding.returnBinding,
            parameterBindings = binding.parameterBindings,
            suppressHResultCheck = binding.suppressHResultCheck,
        )
}

private fun KotlinProjectionRenderer.renderStaticNoArgIntrinsicInvocation(
    referenceExpression: String,
    slotExpression: CodeBlock,
    returnBinding: KotlinProjectionAbiTypeBinding,
    parameterBindings: List<KotlinProjectionAbiParameterBinding>,
    suppressHResultCheck: Boolean,
): CodeBlock? {
    if (!useProjectionIntrinsics || parameterBindings.isNotEmpty()) {
        return null
    }
    val helperFunction = when (returnBinding.kind) {
        KotlinProjectionAbiValueKind.String -> "getString"
        KotlinProjectionAbiValueKind.Boolean ->
            if (suppressHResultCheck) "getNoExceptionBoolean" else "getBoolean"
        KotlinProjectionAbiValueKind.Int32 -> "getInt32"
        KotlinProjectionAbiValueKind.UInt32 -> "getUInt32"
        KotlinProjectionAbiValueKind.Int64 -> "getInt64"
        KotlinProjectionAbiValueKind.UInt64 -> "getUInt64"
        KotlinProjectionAbiValueKind.Float -> "getFloat"
        KotlinProjectionAbiValueKind.Double -> "getDouble"
        else -> return renderInstanceProjectedObjectGetterInvocation(
            referenceExpression = referenceExpression,
            slotExpression = slotExpression,
            returnBinding = returnBinding,
        )
    }
    return renderInstanceScalarGetterInvocation(
        referenceExpression = referenceExpression,
        slotExpression = slotExpression,
        helperFunction = helperFunction,
        intrinsic = true,
        modulePlatformAbiCalls = modulePlatformAbiCalls,
    )
}

private fun KotlinProjectionRenderer.writeTimeSlotCodeBlock(
    binding: KotlinProjectionStaticMemberBinding,
): CodeBlock =
    if (suppressProjectedMemberSlotConstants) {
        binding.slotCodeBlock()
    } else {
        CodeBlock.of("%L", binding.bindingName)
    }

internal fun KotlinProjectionRenderer.renderRequiredInterfaceForwardMembers(
    plan: KotlinTypeProjectionPlan,
    suppressedMemberNames: Set<String>,
): List<Any> {
    if (plan.type.kind != WinRTTypeKind.RuntimeClass) {
        return emptyList()
    }
    val existingMethodNames = plan.type.methods.filter(WinRTMethodDefinition::isOrdinaryProjectedMethod).mapTo(mutableSetOf(), WinRTMethodDefinition::name)
    val existingPropertyNames = plan.type.properties.filterNot(WinRTPropertyDefinition::isStatic).mapTo(mutableSetOf()) {
        it.name.replaceFirstChar(Char::lowercase)
    }
    val members = mutableListOf<Any>()
    val emittedMethods = mutableSetOf<String>()
    val propertyForwards = linkedMapOf<String, RequiredForwardProperty>()
    plan.type.implementedInterfaces.forEach { implemented ->
        if (isRuntimeClassDelegatedInterface(plan, implemented.interfaceName)) {
            return@forEach
        }
        val ownerInterface = implemented.interfaceName
        collectRequiredForwardInterfaceTypes(ownerInterface, plan, mutableSetOf()).forEach { requiredInterface ->
            if (isRuntimeOwnedMappedTypeName(requiredInterface.interfaceName)) {
                return@forEach
            }
            val interfaceType = requiredInterface.type
            interfaceType.methods
                .filter(WinRTMethodDefinition::isOrdinaryProjectedMethod)
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
                .filterNot(WinRTPropertyDefinition::isStatic)
                .filterNot {
                    requiredInterface.isMappedCollectionOrIteratorInterface && it.name in suppressedMemberNames ||
                        it.name.replaceFirstChar(Char::lowercase) in existingPropertyNames
                }
                .forEach { property ->
                    val substitutedProperty = requiredInterface.substitute(property)
                    val propertyName = property.name.replaceFirstChar(Char::lowercase)
                    val propertyTypeName = substitutedProperty.projectedPropertyTypeName(requiredInterface.interfaceName, plan.typesByQualifiedName)
                    propertyForwards[propertyName] = propertyForwards[propertyName]
                        ?.merge(requiredInterface.interfaceName, interfaceType, substitutedProperty, plan.typesByQualifiedName)
                        ?: RequiredForwardProperty(
                            propertyName = propertyName,
                            propertyTypeName = propertyTypeName,
                            getter = substitutedProperty.takeIf { it.hasNativeProjectionGetterAccessor() }?.let {
                                RequiredForwardPropertyAccessor(requiredInterface.interfaceName, interfaceType, it)
                            },
                            setter = substitutedProperty.takeIf { !it.isReadOnly && it.hasNativeProjectionSetterAccessor() }?.let {
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
    val slotInterfaceType: WinRTTypeDefinition,
    val property: WinRTPropertyDefinition,
)

internal data class RequiredForwardInterfaceType(
    val interfaceName: String,
    val type: WinRTTypeDefinition,
    val genericArguments: List<WinRTTypeRef>,
) {
    fun substitute(method: WinRTMethodDefinition): WinRTMethodDefinition =
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

    fun substitute(property: WinRTPropertyDefinition): WinRTPropertyDefinition =
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
        slotInterfaceType: WinRTTypeDefinition,
        property: WinRTPropertyDefinition,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
    ): RequiredForwardProperty {
        val projectedTypeName = property.projectedPropertyTypeName(ownerInterfaceName, typesByQualifiedName)
        require(projectedTypeName == propertyTypeName) {
            "Cannot merge required interface property '$propertyName' with incompatible types: $propertyTypeName vs $projectedTypeName"
        }
        return copy(
            getter = getter ?: property.takeIf { it.hasNativeProjectionGetterAccessor() }?.let {
                RequiredForwardPropertyAccessor(ownerInterfaceName, slotInterfaceType, it)
            },
            setter = setter ?: property.takeIf { !it.isReadOnly && it.hasNativeProjectionSetterAccessor() }?.let {
                RequiredForwardPropertyAccessor(ownerInterfaceName, slotInterfaceType, it)
            },
        )
    }
}

internal fun KotlinProjectionRenderer.collectRequiredForwardInterfaceTypes(
    interfaceName: String,
    plan: KotlinTypeProjectionPlan,
    visiting: MutableSet<String>,
    currentNamespace: String = plan.type.namespace,
): List<RequiredForwardInterfaceType> {
    val rawName = interfaceName.substringBefore('<').removeSuffix("?")
    val resolvedRawName = resolveRequiredForwardInterfaceRawName(rawName, currentNamespace, plan.typesByQualifiedName)
    val resolvedInterfaceName = interfaceName.replacePrefix(rawName, resolvedRawName)
    val interfaceType = plan.typesByQualifiedName[resolvedRawName] ?: return emptyList()
    val genericArguments = genericArgumentTypeRefs(resolvedInterfaceName)
    if (!visiting.add(resolvedInterfaceName)) {
        return emptyList()
    }
    return try {
        buildList {
            add(RequiredForwardInterfaceType(resolvedInterfaceName, interfaceType, genericArguments))
            interfaceType.implementedInterfaces.forEach { implemented ->
                val substitutedInterfaceName = implemented.interfaceType
                    .substituteTypeParameters(genericArguments)
                    .normalized()
                    .typeName
                addAll(collectRequiredForwardInterfaceTypes(substitutedInterfaceName, plan, visiting, interfaceType.namespace))
            }
        }
    } finally {
        visiting.remove(resolvedInterfaceName)
    }
}

private fun String.replacePrefix(oldPrefix: String, newPrefix: String): String =
    if (startsWith(oldPrefix)) newPrefix + removePrefix(oldPrefix) else this

private fun resolveRequiredForwardInterfaceRawName(
    rawName: String,
    currentNamespace: String,
    typesByQualifiedName: Map<String, WinRTTypeDefinition>,
): String {
    if (rawName in typesByQualifiedName || '.' in rawName) {
        return rawName
    }
    val qualifiedName = "$currentNamespace.$rawName"
    return if (qualifiedName in typesByQualifiedName) qualifiedName else rawName
}

private fun genericArgumentTypeRefs(typeName: String): List<WinRTTypeRef> {
    val trimmed = typeName.trim()
    if ('<' !in trimmed || !trimmed.endsWith('>')) {
        return emptyList()
    }
    return splitGenericArguments(trimmed.substringAfter('<').substringBeforeLast('>'))
        .map(WinRTTypeRef::fromDisplayName)
}

private fun KotlinProjectionRenderer.renderRequiredForwardMethod(
    plan: KotlinTypeProjectionPlan,
    ownerInterfaceName: String,
    slotInterfaceType: WinRTTypeDefinition,
    method: WinRTMethodDefinition,
): FunSpec? {
    val returnBinding = renderAbiTypeBinding(method.projectedKotlinReturnTypeName(), plan.typesByQualifiedName, slotInterfaceType.namespace)
    val parameterBindings = method.projectedKotlinParameters().map { parameter ->
        KotlinProjectionAbiParameterBinding(
            name = parameter.name,
            typeBinding = renderAbiTypeBinding(parameter.typeName, plan.typesByQualifiedName, slotInterfaceType.namespace),
            category = metadataParameterCategoryFor(parameter),
        )
    }
    val slotConstantName = method.abiSlotConstantName(slotInterfaceType.methods)
    val referenceExpression = requiredForwardOwnerCache(ownerInterfaceName, plan.defaultInterfaceName)
    val slotExpression = metadataSlotExpression(slotInterfaceType, slotConstantName)
    val invocation = renderInstanceDescriptorBooleanIntrinsicInvocation(
        referenceExpression = referenceExpression,
        slotExpression = slotExpression,
        returnBinding = returnBinding,
        parameterBindings = parameterBindings,
        suppressHResultCheck = method.isNoException,
    ) ?: renderInstanceDescriptorScalarIntrinsicInvocation(
        referenceExpression = referenceExpression,
        slotExpression = slotExpression,
        returnBinding = returnBinding,
        parameterBindings = parameterBindings,
        suppressHResultCheck = method.isNoException,
    ) ?: renderInstanceArrayResultIntrinsicInvocation(
        referenceExpression = referenceExpression,
        slotExpression = slotExpression,
        returnBinding = returnBinding,
        parameterBindings = parameterBindings,
        suppressHResultCheck = method.isNoException,
    ) ?: renderInlineAbiInvocation(
        invokeTargetExpression = referenceExpression,
        slotExpression = slotExpression,
        callPlan = buildAbiCallPlan(
            returnBinding = returnBinding,
            parameterBindings = parameterBindings,
            suppressHResultCheck = method.isNoException,
        ) ?: return null,
    ) ?: return null
    val objectShape = closableMethodShape(slotInterfaceType, method)
    val projectedAttributes = slotInterfaceType.projectedAttributes()
        .filter(WinRTProjectedAttributeDescriptor::isPlatformAttribute)
    return FunSpec.builder(objectShape?.name ?: method.projectedMethodName())
        .addProjectedAttributeAnnotations(projectedAttributes)
        .addMethodGenericParameters(method, objectShape)
        .addModifiers(KModifier.OVERRIDE)
        .returns(objectShape?.returnType ?: resolveTypeName(method.projectedKotlinReturnTypeName()))
        .addParameters(objectShape?.parameters ?: method.projectedKotlinParameters().map { ParameterSpec.builder(it.name, resolveTypeName(it.typeName)).build() })
        .addCode("%L\n", invocation)
        .build()
}

internal fun KotlinProjectionRenderer.metadataSlotExpression(
    slotInterfaceType: WinRTTypeDefinition,
    slotConstantName: String,
): CodeBlock = metadataSlotExpression(slotInterfaceType.qualifiedName, slotConstantName)

internal fun KotlinProjectionRenderer.metadataSlotExpression(
    slotInterfaceQualifiedName: String,
    slotConstantName: String,
): CodeBlock {
    val rawInterfaceName = slotInterfaceQualifiedName.substringBefore('<').removeSuffix("?")
    projectedSlotLiterals[KotlinProjectionSlotLiteralKey(rawInterfaceName, slotConstantName)]?.let { slot ->
        return CodeBlock.of("%L", slot)
    }
    return if (slotInterfaceQualifiedName == "Windows.Foundation.IClosable" && slotConstantName == "CLOSE_SLOT") {
        CodeBlock.of("6")
    } else if (mappedTypeByAbiName(rawInterfaceName) != null) {
        CodeBlock.of(
            "%T.Metadata.%L",
            projectionClassName(rawInterfaceName),
            slotConstantName,
        )
    } else {
        CodeBlock.of("%T.Metadata.%L", resolveTypeName(slotInterfaceQualifiedName), slotConstantName)
    }
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
        ?.filter(WinRTProjectedAttributeDescriptor::isPlatformAttribute)
        .orEmpty()
    builder.addProjectedAttributeAnnotations(projectedAttributes)
    property.getter?.let { getter ->
        val getterTypeName = getter.property.projectedPropertyTypeName(getter.ownerInterfaceName, plan.typesByQualifiedName)
        val callPlan = buildAbiCallPlan(
            returnBinding = renderAbiTypeBinding(getterTypeName, plan.typesByQualifiedName, getter.slotInterfaceType.namespace),
            parameterBindings = emptyList(),
            suppressHResultCheck = getter.property.isNoException,
        ) ?: return null
        val invocation = renderInlineAbiInvocation(
            invokeTargetExpression = requiredForwardOwnerCache(getter.ownerInterfaceName, plan.defaultInterfaceName),
            slotExpression = metadataSlotExpression(getter.slotInterfaceType.qualifiedName, "${getter.property.name.uppercase()}_GETTER_SLOT"),
            callPlan = callPlan,
        ) ?: return null
        builder.getter(FunSpec.getterBuilder().addCode("%L\n", invocation).build())
    }
    property.setter?.let { setter ->
        val setterTypeName = setter.property.projectedPropertyTypeName(setter.ownerInterfaceName, plan.typesByQualifiedName)
        val callPlan = buildAbiCallPlan(
            returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
            parameterBindings = listOf(KotlinProjectionAbiParameterBinding("value", renderAbiTypeBinding(setterTypeName, plan.typesByQualifiedName, setter.slotInterfaceType.namespace))),
            suppressHResultCheck = setter.property.isNoException,
        ) ?: return null
        val invocation = renderInlineAbiInvocation(
            invokeTargetExpression = requiredForwardOwnerCache(setter.ownerInterfaceName, plan.defaultInterfaceName),
            slotExpression = metadataSlotExpression(setter.slotInterfaceType.qualifiedName, "${setter.property.name.uppercase()}_SETTER_SLOT"),
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

internal fun WinRTMethodDefinition.projectedMethodName(): String =
    name.replaceFirstChar(Char::lowercase)
