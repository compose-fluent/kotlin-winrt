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
import io.github.composefluent.winrt.metadata.WinRtCustomAttributeValue
import io.github.composefluent.winrt.metadata.WinRtProjectedAttributeDescriptor
import io.github.composefluent.winrt.metadata.projectedAttributes
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

internal fun KotlinProjectionRenderer.renderInterfaceMethod(method: WinRtMethodDefinition): FunSpec {
    val objectShape = runtimeObjectMethodShape(method)
    return FunSpec.builder(objectShape?.name ?: method.projectedMethodName())
        .addModifiers(KModifier.ABSTRACT)
        .addMethodGenericParameters(method, objectShape)
        .apply {
            if (objectShape != null) {
                addModifiers(KModifier.OVERRIDE)
            }
        }
        .addParameters(objectShape?.parameters ?: method.parameters.map { ParameterSpec.builder(it.name, resolveTypeName(it.typeName)).build() })
        .returns(objectShape?.returnType ?: resolveTypeName(method.returnTypeName))
        .build()
}

internal fun KotlinProjectionRenderer.renderStubMethod(method: WinRtMethodDefinition, override: Boolean = false): FunSpec {
    val objectShape = runtimeObjectMethodShape(method)
    val builder = FunSpec.builder(objectShape?.name ?: method.projectedMethodName())
        .addMethodGenericParameters(method, objectShape)
        .addParameters(objectShape?.parameters ?: method.parameters.map { ParameterSpec.builder(it.name, resolveTypeName(it.typeName)).build() })
        .returns(objectShape?.returnType ?: resolveTypeName(method.returnTypeName))
        .addCode("return %L\n", missingAbiBindingError("method ${method.name}"))
    if (override || objectShape != null) {
        builder.addModifiers(KModifier.OVERRIDE)
    }
    return builder.build()
}

internal fun FunSpec.Builder.addMethodGenericParameters(
    method: WinRtMethodDefinition,
    objectShape: RuntimeObjectMethodShape? = null,
): FunSpec.Builder = apply {
    if (objectShape != null) {
        return@apply
    }
    val parameters = method.genericParameters.ifEmpty {
        (0 until method.genericParameterCount).map { index ->
            io.github.composefluent.winrt.metadata.WinRtGenericParameterDefinition("M$index", index)
        }
    }
    parameters.forEach { parameter ->
        addTypeVariable(TypeVariableName("M${parameter.index}"))
    }
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
        renderInstanceNoArgIntrinsicInvocation(binding)
            ?: renderInstanceStructResultIntrinsicInvocation(binding)
            ?: renderInstanceArrayResultIntrinsicInvocation(
                referenceExpression = binding.ownerCachePropertyName,
                slotExpression = CodeBlock.of("Metadata.%L", binding.bindingName),
                returnBinding = binding.returnBinding,
                parameterBindings = binding.parameterBindings,
                suppressHResultCheck = binding.suppressHResultCheck,
            )
            ?: renderInstanceEnumResultIntrinsicInvocation(
                referenceExpression = binding.ownerCachePropertyName,
                slotExpression = CodeBlock.of("Metadata.%L", binding.bindingName),
                returnBinding = binding.returnBinding,
                parameterBindings = binding.parameterBindings,
                suppressHResultCheck = binding.suppressHResultCheck,
            )
            ?: renderInstanceOneArgUnitIntrinsicInvocation(binding)
            ?: renderInstanceFloatStringUnitIntrinsicInvocation(
                referenceExpression = binding.ownerCachePropertyName,
                slotExpression = CodeBlock.of("Metadata.%L", binding.bindingName),
                returnBinding = binding.returnBinding,
                parameterBindings = binding.parameterBindings,
                suppressHResultCheck = binding.suppressHResultCheck,
            )
            ?: renderInstanceFloatStringProjectedObjectUnitIntrinsicInvocation(
                referenceExpression = binding.ownerCachePropertyName,
                slotExpression = CodeBlock.of("Metadata.%L", binding.bindingName),
                returnBinding = binding.returnBinding,
                parameterBindings = binding.parameterBindings,
                suppressHResultCheck = binding.suppressHResultCheck,
            )
            ?: renderInstanceStructOneArgUnitIntrinsicInvocation(
                referenceExpression = binding.ownerCachePropertyName,
                slotExpression = CodeBlock.of("Metadata.%L", binding.bindingName),
                returnBinding = binding.returnBinding,
                parameterBindings = binding.parameterBindings,
                suppressHResultCheck = binding.suppressHResultCheck,
            )
            ?: renderInstanceEnumOneArgUnitIntrinsicInvocation(
                referenceExpression = binding.ownerCachePropertyName,
                slotExpression = CodeBlock.of("Metadata.%L", binding.bindingName),
                returnBinding = binding.returnBinding,
                parameterBindings = binding.parameterBindings,
                suppressHResultCheck = binding.suppressHResultCheck,
            )
            ?: renderBoundInvocation(binding)
    }
    return FunSpec.builder(objectShape?.name ?: method.projectedMethodName())
        .addProjectedAttributeAnnotations(binding.projectedAttributes)
        .addMethodGenericParameters(method, objectShape)
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

internal fun runtimeObjectMethodShape(method: WinRtMethodDefinition): RuntimeObjectMethodShape? =
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

internal fun closableMethodShape(
    slotInterfaceType: WinRtTypeDefinition,
    method: WinRtMethodDefinition,
): RuntimeObjectMethodShape? =
    if (
        slotInterfaceType.qualifiedName == "Windows.Foundation.IClosable" &&
        method.name == "Close" &&
        method.parameters.isEmpty() &&
        method.returnTypeName == "Unit"
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
    val getterInvocation = renderReferencePropertyGetter(getterBinding)
        ?: renderProjectedObjectPropertyGetter(getterBinding)
        ?: renderScalarPropertyGetter(getterBinding)
        ?: renderInstanceNoArgIntrinsicInvocation(getterBinding)
        ?: renderInstanceStructResultIntrinsicInvocation(getterBinding)
        ?: renderInstanceArrayResultIntrinsicInvocation(
            referenceExpression = getterBinding.ownerCachePropertyName,
            slotExpression = CodeBlock.of("Metadata.%L", getterBinding.bindingName),
            returnBinding = getterBinding.returnBinding,
            parameterBindings = getterBinding.parameterBindings,
            suppressHResultCheck = getterBinding.suppressHResultCheck,
        )
        ?: renderInstanceEnumResultIntrinsicInvocation(
            referenceExpression = getterBinding.ownerCachePropertyName,
            slotExpression = CodeBlock.of("Metadata.%L", getterBinding.bindingName),
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
                .addParameter("value", resolveTypeName(property.typeName))
                .addCode(
                    "%L\n",
                    setterBinding?.let {
                        renderReferencePropertySetter(it)
                            ?: renderInstanceOneArgUnitIntrinsicInvocation(it, argumentExpression = "value")
                            ?: renderInstanceStructOneArgUnitIntrinsicInvocation(
                                referenceExpression = it.ownerCachePropertyName,
                                slotExpression = CodeBlock.of("Metadata.%L", it.bindingName),
                                returnBinding = it.returnBinding,
                                parameterBindings = it.parameterBindings,
                                suppressHResultCheck = it.suppressHResultCheck,
                                argumentExpression = "value",
                            )
                            ?: renderInstanceEnumOneArgUnitIntrinsicInvocation(
                                referenceExpression = it.ownerCachePropertyName,
                                slotExpression = CodeBlock.of("Metadata.%L", it.bindingName),
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
    return CodeBlock.builder()
        .add("return %T.%L(\n", WINRT_PROJECTION_INTRINSIC_CLASS_NAME, helperFunction)
        .indent()
        .add("%L,\n", binding.ownerCachePropertyName)
        .add("Metadata.%L,\n", binding.bindingName)
        .add("%L,\n", argumentExpression ?: parameter.name)
        .unindent()
        .add(")\n")
        .build()
}

internal fun KotlinProjectionRenderer.renderInstanceFloatStringUnitIntrinsicInvocation(
    referenceExpression: String,
    slotExpression: CodeBlock,
    returnBinding: KotlinProjectionAbiTypeBinding,
    parameterBindings: List<KotlinProjectionAbiParameterBinding>,
    suppressHResultCheck: Boolean,
): CodeBlock? {
    if (
        !useProjectionIntrinsics ||
        returnBinding.kind != KotlinProjectionAbiValueKind.Unit ||
        parameterBindings.size != 2 ||
        suppressHResultCheck
    ) {
        return null
    }
    val first = parameterBindings[0]
    val second = parameterBindings[1]
    if (
        first.typeBinding.kind != KotlinProjectionAbiValueKind.Float ||
        first.typeBinding.typeName.endsWith("?") ||
        second.typeBinding.kind != KotlinProjectionAbiValueKind.String ||
        second.typeBinding.typeName.endsWith("?")
    ) {
        return null
    }
    return CodeBlock.builder()
        .add("return %T.callUnitWithFloatAndString(\n", WINRT_PROJECTION_INTRINSIC_CLASS_NAME)
        .indent()
        .add("%L,\n", referenceExpression)
        .add("%L,\n", slotExpression)
        .add("%L,\n", first.name)
        .add("%L,\n", second.name)
        .unindent()
        .add(")\n")
        .build()
}

internal fun KotlinProjectionRenderer.renderInstanceFloatStringProjectedObjectUnitIntrinsicInvocation(
    referenceExpression: String,
    slotExpression: CodeBlock,
    returnBinding: KotlinProjectionAbiTypeBinding,
    parameterBindings: List<KotlinProjectionAbiParameterBinding>,
    suppressHResultCheck: Boolean,
): CodeBlock? {
    if (
        !useProjectionIntrinsics ||
        returnBinding.kind != KotlinProjectionAbiValueKind.Unit ||
        parameterBindings.size != 3 ||
        suppressHResultCheck
    ) {
        return null
    }
    val first = parameterBindings[0]
    val second = parameterBindings[1]
    val third = parameterBindings[2]
    if (
        first.typeBinding.kind != KotlinProjectionAbiValueKind.Float ||
        first.typeBinding.typeName.endsWith("?") ||
        second.typeBinding.kind != KotlinProjectionAbiValueKind.String ||
        second.typeBinding.typeName.endsWith("?") ||
        third.typeBinding.kind !in setOf(
            KotlinProjectionAbiValueKind.ProjectedInterface,
            KotlinProjectionAbiValueKind.ProjectedRuntimeClass,
        ) ||
        third.typeBinding.typeName.endsWith("?") ||
        third.typeBinding.typeArguments.isNotEmpty()
    ) {
        return null
    }
    return CodeBlock.builder()
        .add("return %T.callUnitWithFloatStringAndProjectedObject(\n", WINRT_PROJECTION_INTRINSIC_CLASS_NAME)
        .indent()
        .add("%L,\n", referenceExpression)
        .add("%L,\n", slotExpression)
        .add("%L,\n", first.name)
        .add("%L,\n", second.name)
        .add("%L as %T,\n", third.name, IWINRT_OBJECT_CLASS_NAME)
        .unindent()
        .add(")\n")
        .build()
}

private fun KotlinProjectionRenderer.renderProjectedObjectPropertyGetter(
    binding: KotlinProjectionInstanceMemberBinding,
): CodeBlock? {
    if (binding.parameterBindings.isNotEmpty() || binding.suppressHResultCheck) {
        return null
    }
    return renderInstanceProjectedObjectGetterInvocation(
        referenceExpression = binding.ownerCachePropertyName,
        slotExpression = CodeBlock.of("Metadata.%L", binding.bindingName),
        returnBinding = binding.returnBinding,
    )
}

private fun KotlinProjectionRenderer.renderScalarPropertyGetter(
    binding: KotlinProjectionInstanceMemberBinding,
): CodeBlock? {
    if (binding.parameterBindings.isNotEmpty() || binding.suppressHResultCheck) {
        return null
    }
    val helperFunction = when (binding.returnBinding.kind) {
        KotlinProjectionAbiValueKind.Boolean -> "getBoolean"
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
        slotExpression = CodeBlock.of("Metadata.%L", binding.bindingName),
        helperFunction = helperFunction,
        intrinsic = useProjectionIntrinsics,
    )
}

internal fun renderInstanceScalarGetterInvocation(
    referenceExpression: String,
    slotExpression: CodeBlock,
    helperFunction: String,
    intrinsic: Boolean = false,
): CodeBlock {
    return CodeBlock.builder()
        .add(
            "return %T.%L(\n",
            if (intrinsic) WINRT_PROJECTION_INTRINSIC_CLASS_NAME else WINRT_INSTANCE_PROJECTION_INTEROP_CLASS_NAME,
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
    return CodeBlock.builder()
        .add("return %T.%L(\n", WINRT_INSTANCE_PROJECTION_INTEROP_CLASS_NAME, helperFunction)
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
        .add("Metadata.%L,\n", binding.bindingName)
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
        .add("Metadata.%L,\n", binding.bindingName)
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

private fun KotlinProjectionRenderer.renderInstanceNoArgIntrinsicInvocation(
    binding: KotlinProjectionInstanceMemberBinding,
): CodeBlock? {
    if (!useProjectionIntrinsics || binding.parameterBindings.isNotEmpty() || binding.suppressHResultCheck) {
        return null
    }
    val helperFunction = when (binding.returnBinding.kind) {
        KotlinProjectionAbiValueKind.Unit -> return null
        KotlinProjectionAbiValueKind.String -> "getString"
        KotlinProjectionAbiValueKind.Boolean -> "getBoolean"
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
        slotExpression = CodeBlock.of("Metadata.%L", binding.bindingName),
        helperFunction = helperFunction,
        intrinsic = true,
    )
}

private fun KotlinProjectionRenderer.renderInstanceStructResultIntrinsicInvocation(
    binding: KotlinProjectionInstanceMemberBinding,
): CodeBlock? {
    if (
        !useProjectionIntrinsics ||
        binding.returnBinding.kind != KotlinProjectionAbiValueKind.Struct ||
        binding.parameterBindings.isNotEmpty() ||
        binding.suppressHResultCheck
    ) {
        return null
    }
    if (customStructAbi(binding.returnBinding) != null) {
        return null
    }
    val structType = nativeStructClassName(binding.returnBinding) ?: return null
    return CodeBlock.builder()
        .add("return %T.getStruct(\n", WINRT_PROJECTION_INTRINSIC_CLASS_NAME)
        .indent()
        .add("%L,\n", binding.ownerCachePropertyName)
        .add("Metadata.%L,\n", binding.bindingName)
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
        parameterBindings.isNotEmpty() ||
        suppressHResultCheck
    ) {
        return null
    }
    val enumType = resolvedReturnClassName(returnBinding) ?: return null
    val helperFunction = when (returnBinding.enumUnderlyingType) {
        WinRtIntegralType.Int32 -> "getInt32"
        WinRtIntegralType.UInt32 -> "getUInt32"
        else -> return null
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
        KotlinProjectionAbiValueKind.Enum -> when (parameter.typeBinding.enumUnderlyingType) {
            WinRtIntegralType.Int32 -> "setInt32"
            WinRtIntegralType.UInt32 -> "setUInt32"
            else -> return null
        }
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
        if (isRuntimeClassDelegatedInterface(plan, implemented.interfaceName)) {
            return@forEach
        }
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
            slotExpression = metadataSlotExpression(slotInterfaceType, slotConstantName),
            callPlan = callPlan,
        ) ?: return null
    val objectShape = closableMethodShape(slotInterfaceType, method)
    val projectedAttributes = slotInterfaceType.projectedAttributes()
        .filter(WinRtProjectedAttributeDescriptor::isPlatformAttribute)
    return FunSpec.builder(objectShape?.name ?: method.projectedMethodName())
        .addProjectedAttributeAnnotations(projectedAttributes)
        .addMethodGenericParameters(method, objectShape)
        .addModifiers(KModifier.OVERRIDE)
        .returns(objectShape?.returnType ?: resolveTypeName(method.returnTypeName))
        .addParameters(objectShape?.parameters ?: method.parameters.map { ParameterSpec.builder(it.name, resolveTypeName(it.typeName)).build() })
        .addCode("%L\n", invocation)
        .build()
}

internal fun KotlinProjectionRenderer.metadataSlotExpression(
    slotInterfaceType: WinRtTypeDefinition,
    slotConstantName: String,
): CodeBlock = metadataSlotExpression(slotInterfaceType.qualifiedName, slotConstantName)

internal fun KotlinProjectionRenderer.metadataSlotExpression(
    slotInterfaceQualifiedName: String,
    slotConstantName: String,
): CodeBlock =
    if (slotInterfaceQualifiedName == "Windows.Foundation.IClosable" && slotConstantName == "CLOSE_SLOT") {
        CodeBlock.of("6")
    } else if (mappedTypeByAbiName(slotInterfaceQualifiedName.substringBefore('<').removeSuffix("?")) != null) {
        CodeBlock.of(
            "%T.Metadata.%L",
            projectionClassName(slotInterfaceQualifiedName.substringBefore('<').removeSuffix("?")),
            slotConstantName,
        )
    } else {
        CodeBlock.of("%T.Metadata.%L", resolveTypeName(slotInterfaceQualifiedName), slotConstantName)
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

internal fun WinRtMethodDefinition.projectedMethodName(): String =
    name.replaceFirstChar(Char::lowercase)
