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

class KotlinProjectionRenderer {
    fun render(plan: KotlinTypeProjectionPlan): KotlinProjectionFile =
        KotlinProjectionFile(
            relativePath = plan.relativePath,
            packageName = plan.packageName,
            contents = FileSpec.builder(plan.packageName, plan.type.name)
                .apply { addType(renderType(plan)) }
                .build()
                .toString(),
        )

    private fun renderType(plan: KotlinTypeProjectionPlan): TypeSpec = when (plan.declarationKind) {
        KotlinProjectionDeclarationKind.Interface -> renderInterfaceShell(plan)
        KotlinProjectionDeclarationKind.Class -> renderClassShell(plan)
        KotlinProjectionDeclarationKind.Enum -> renderEnumShell(plan)
        KotlinProjectionDeclarationKind.Struct -> renderStruct(plan)
        KotlinProjectionDeclarationKind.Delegate -> renderDelegate(plan)
    }

    private fun renderInterfaceShell(plan: KotlinTypeProjectionPlan): TypeSpec {
        val builder = TypeSpec.interfaceBuilder(plan.type.name)
        applyCommonTypeShape(builder, plan)
        plan.type.implementedInterfaces.forEach { implemented ->
            builder.addSuperinterface(resolveTypeName(implemented.interfaceName))
        }
        plan.type.methods.forEach { builder.addFunction(renderInterfaceMethod(it)) }
        plan.type.properties.filterNot { it.isStatic }.forEach { builder.addProperty(renderInterfaceProperty(it)) }
        plan.type.events.filterNot { it.isStatic }.forEach { event ->
            builder.addProperty(renderEventProperty(event, eventInvokeDescriptor = null, abstract = true))
            renderEventFunctions(event, abstract = true).forEach(builder::addFunction)
        }
        appendCompanionShells(builder, plan)
        return builder.build()
    }

    private fun renderInterfaceProxyMethod(method: WinRtMethodDefinition): FunSpec {
        val returnBinding = renderAbiTypeBinding(method.returnTypeName)
        val parameterBindings = method.parameters.map { parameter ->
            KotlinProjectionAbiParameterBinding(
                name = parameter.name,
                typeBinding = renderAbiTypeBinding(parameter.typeName),
            )
        }
        val callPlan = requireAbiCallPlan(
            bindingName = "${method.name.uppercase()}_SLOT",
            returnBinding = returnBinding,
            parameterBindings = parameterBindings,
        )
        val invocation = renderInlineAbiInvocation(
            invokeTargetExpression = "nativeObject",
            slotExpression = "Metadata.${method.name.uppercase()}_SLOT",
            callPlan = callPlan,
        ) ?: error("Generator interface proxy parity failed to emit ${method.name}")
        return FunSpec.builder(method.name)
            .addModifiers(KModifier.OVERRIDE)
            .addParameters(method.parameters.map { ParameterSpec.builder(it.name, resolveTypeName(it.typeName)).build() })
            .returns(resolveTypeName(method.returnTypeName))
            .addCode("%L\n", invocation)
            .build()
    }

    private fun canRenderInterfaceProxy(plan: KotlinTypeProjectionPlan): Boolean =
        plan.type.properties.none { !it.isStatic } &&
            plan.type.events.none { !it.isStatic } &&
            plan.type.methods.all { method ->
                runCatching {
                    buildAbiCallPlan(
                        returnBinding = renderAbiTypeBinding(method.returnTypeName),
                        parameterBindings = method.parameters.map { parameter ->
                            KotlinProjectionAbiParameterBinding(parameter.name, renderAbiTypeBinding(parameter.typeName))
                        },
                    ) != null
                }.getOrDefault(false)
            }

    private fun renderAbiTypeBinding(typeName: String): KotlinProjectionAbiTypeBinding {
        val trimmed = typeName.trim()
        val rawTypeName = trimmed.substringBefore('<').removeSuffix("?")
        val typeArguments = if ('<' in trimmed && trimmed.endsWith('>')) {
            splitGenericArguments(trimmed.substringAfter('<').substringBeforeLast('>')).map(::renderAbiTypeBinding)
        } else {
            emptyList()
        }
        val mappedType = mappedTypeByAbiName(rawTypeName)
        val kind = when (trimmed) {
            "Unit" -> KotlinProjectionAbiValueKind.Unit
            "String" -> KotlinProjectionAbiValueKind.String
            "Boolean" -> KotlinProjectionAbiValueKind.Boolean
            "Byte",
            "SByte",
            "Int8" -> KotlinProjectionAbiValueKind.Int8
            "UByte",
            "UInt8" -> KotlinProjectionAbiValueKind.UInt8
            "Short",
            "Int16" -> KotlinProjectionAbiValueKind.Int16
            "UShort",
            "UInt16" -> KotlinProjectionAbiValueKind.UInt16
            "Int" -> KotlinProjectionAbiValueKind.Int32
            "UInt" -> KotlinProjectionAbiValueKind.UInt32
            "Long",
            "Int64" -> KotlinProjectionAbiValueKind.Int64
            "ULong",
            "UInt64" -> KotlinProjectionAbiValueKind.UInt64
            "Float",
            "Single" -> KotlinProjectionAbiValueKind.Float
            "Double" -> KotlinProjectionAbiValueKind.Double
            IUNKNOWN_REFERENCE_CLASS_NAME.simpleName,
            "io.github.kitectlab.winrt.runtime.IUnknownReference" -> KotlinProjectionAbiValueKind.UnknownReference
            IINSPECTABLE_REFERENCE_CLASS_NAME.simpleName,
            "io.github.kitectlab.winrt.runtime.IInspectableReference" -> KotlinProjectionAbiValueKind.InspectableReference
            else -> mappedType?.abiValueKind ?: KotlinProjectionAbiValueKind.Unsupported
        }
        return KotlinProjectionAbiTypeBinding(
            kind = kind,
            typeName = trimmed,
            resolvedTypeName = rawTypeName,
            typeArguments = typeArguments,
        )
    }

    private fun renderClassShell(plan: KotlinTypeProjectionPlan): TypeSpec = when {
        KotlinProjectionSpecializationKind.AttributeClass in plan.specializationKinds -> renderAttributeClassShell(plan)
        KotlinProjectionSpecializationKind.StaticClass in plan.specializationKinds -> renderStaticClassShell(plan)
        else -> renderRuntimeClassShell(plan)
    }

    private fun renderRuntimeClassShell(plan: KotlinTypeProjectionPlan): TypeSpec {
        val builder = TypeSpec.classBuilder(plan.type.name)
        applyCommonTypeShape(builder, plan, emitKotlinSealed = false)
        if (KotlinProjectionModifier.Sealed in plan.modifiers) {
            builder.addKdoc(
                "WinRT sealed runtime class shell emitted as a regular Kotlin class because Kotlin sealed constructors would block RCW wrapping and activation.\n",
            )
        }
        val constructorBuilder = FunSpec.constructorBuilder()
            .addModifiers(KModifier.INTERNAL)
            .addParameter("_inner", IINSPECTABLE_REFERENCE_CLASS_NAME)
        builder.primaryConstructor(constructorBuilder.build())
        builder.addProperty(
            PropertySpec.builder("_inner", IINSPECTABLE_REFERENCE_CLASS_NAME)
                .addModifiers(KModifier.PRIVATE)
                .initializer("_inner")
                .build(),
        )
        builder.addProperty(
            PropertySpec.builder("nativeObject", COM_OBJECT_REFERENCE_CLASS_NAME)
                .addModifiers(KModifier.OVERRIDE)
                .getter(
                    FunSpec.getterBuilder()
                        .addCode("return _inner\n")
                        .build(),
                )
                .build(),
        )
        if (plan.defaultInterfaceIid != null) {
            builder.addProperty(
                PropertySpec.builder("_defaultInterface", IUNKNOWN_REFERENCE_CLASS_NAME)
                    .addModifiers(KModifier.PRIVATE)
                    .delegate(
                        CodeBlock.of(
                            "lazy(%T.PUBLICATION) { Metadata.acquireDefaultInterface(_inner) }",
                            LAZY_THREAD_SAFETY_MODE_CLASS_NAME,
                        ),
                    )
                    .build(),
            )
        }
        plan.implementedInterfaceBindings
            .filter { it.iid != null }
            .forEach { binding ->
                builder.addProperty(
                    PropertySpec.builder(
                        "_${binding.qualifiedName.substringAfterLast('.').replaceFirstChar(Char::lowercase)}",
                        IUNKNOWN_REFERENCE_CLASS_NAME,
                    )
                        .addModifiers(KModifier.PRIVATE)
                        .delegate(
                            CodeBlock.of(
                                "lazy(%T.PUBLICATION) { Metadata.acquireInterface(_inner, %T.Metadata.IID) }",
                                LAZY_THREAD_SAFETY_MODE_CLASS_NAME,
                                resolveTypeName(binding.qualifiedName),
                            ),
                        )
                        .build(),
                )
        }
        plan.defaultInterfaceName?.let { defaultInterfaceName ->
            builder.addSuperinterface(resolveTypeName(defaultInterfaceName))
        }
        plan.type.implementedInterfaces
            .filterNot { it.isDefault }
            .filterNot { implemented ->
                isMappedCollectionInterfaceName(implemented.interfaceName)
            }
            .forEach { implemented -> builder.addSuperinterface(resolveTypeName(implemented.interfaceName)) }
        plan.mutableCollectionBindings.forEach { binding ->
            builder.addProperty(renderMutableCollectionDelegateProperty(binding))
            builder.addSuperinterface(
                mutableCollectionProjectedType(binding),
                CodeBlock.of("%L", binding.delegatePropertyName),
            )
        }
        plan.readOnlyCollectionBindings.forEach { binding ->
            builder.addProperty(renderReadOnlyCollectionDelegateProperty(binding))
            builder.addSuperinterface(
                readOnlyCollectionProjectedType(binding),
                CodeBlock.of("%L", binding.delegatePropertyName),
            )
        }
        builder.addSuperinterface(IWINRT_OBJECT_CLASS_NAME)
        if (KotlinProjectionCompanionKind.ActivationFactory in plan.companionKinds) {
            builder.addFunction(
                FunSpec.constructorBuilder()
                    .callThisConstructor(CodeBlock.of("%T.activateInstance(Metadata.TYPE_NAME)", ACTIVATION_FACTORY_CLASS_NAME))
                    .build(),
            )
        }
        if (hasDefaultComposableFactoryConstructor(plan)) {
            builder.addFunction(
                FunSpec.constructorBuilder()
                    .callThisConstructor(CodeBlock.of("ComposableFactory.createInstance()"))
                    .build(),
            )
        }
        plan.type.methods.filterNot { it.isStatic }.forEach { builder.addFunction(renderRuntimeMethod(plan, it)) }
        plan.type.properties.filterNot { it.isStatic }.forEach { builder.addProperty(renderRuntimeProperty(plan, it)) }
        plan.type.events.filterNot { it.isStatic }.forEach { event ->
            builder.addProperty(
                renderEventProperty(
                    event = event,
                    eventInvokeDescriptor = plan.eventInvokeDescriptors.firstOrNull { it.eventName == event.name && !it.isStatic },
                    abstract = false,
                    override = true,
                ),
            )
            (renderBoundEventFunctions(plan, event, override = true) ?: renderEventFunctions(event, abstract = false, override = true))
                .forEach(builder::addFunction)
        }
        val staticMethods = plan.type.methods.filter { it.isStatic }
        val staticProperties = plan.type.properties.filter { it.isStatic }
        val staticEvents = plan.type.events.filter { it.isStatic }
        if (staticMethods.isNotEmpty() || staticProperties.isNotEmpty() || staticEvents.isNotEmpty() ||
            KotlinProjectionCompanionKind.Metadata in plan.companionKinds) {
            builder.addType(buildMetadataCompanionShell(plan, staticMethods, staticProperties, staticEvents))
        }
        appendCompanionShells(builder, plan, excludeKinds = setOf(KotlinProjectionCompanionKind.Metadata))
        return builder.build()
    }

    private fun renderAttributeClassShell(plan: KotlinTypeProjectionPlan): TypeSpec =
        TypeSpec.classBuilder(plan.type.name)
            .apply {
                applyCommonTypeShape(this, plan)
                superclass(ATTRIBUTE_CLASS_NAME)
                addKdoc("attribute WinRT class shell\n")
            }
            .build()

    private fun renderStaticClassShell(plan: KotlinTypeProjectionPlan): TypeSpec =
        TypeSpec.classBuilder(plan.type.name)
            .apply {
                applyCommonTypeShape(this, plan)
                addAnnotation(
                    AnnotationSpec.builder(Suppress::class)
                        .addMember("%S", "ClassName")
                        .build(),
                )
                addKdoc("static WinRT class shell\n")
                appendCompanionShells(this, plan)
            }
            .build()

    private fun renderEnumShell(plan: KotlinTypeProjectionPlan): TypeSpec =
        TypeSpec.enumBuilder(plan.type.name)
            .apply {
                applyCommonTypeShape(this, plan)
                if (KotlinProjectionSpecializationKind.ApiContract in plan.specializationKinds) {
                    addKdoc("api contract WinRT declaration shell\n")
                }
                val underlyingType = plan.type.enumUnderlyingType
                if (plan.type.kind == WinRtTypeKind.Enum && underlyingType != null && plan.type.enumMembers.isNotEmpty()) {
                    primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addParameter("abiValue", resolveIntegralTypeName(underlyingType))
                            .build(),
                    )
                    addProperty(
                        PropertySpec.builder("abiValue", resolveIntegralTypeName(underlyingType))
                            .addModifiers(KModifier.INTERNAL)
                            .initializer("abiValue")
                            .build(),
                    )
                    plan.type.enumMembers.forEach { member ->
                        addEnumConstant(
                            member.name,
                            TypeSpec.anonymousClassBuilder()
                                .addSuperclassConstructorParameter("%L", integralLiteral(member.valueBits, underlyingType))
                                .build(),
                        )
                    }
                    addType(
                        TypeSpec.companionObjectBuilder("Metadata")
                            .addFunction(
                                FunSpec.builder("fromAbi")
                                    .addModifiers(KModifier.INTERNAL)
                                    .addParameter("value", resolveIntegralTypeName(underlyingType))
                                    .returns(resolveTypeName(plan.type.qualifiedName))
                                    .addCode(
                                        "return %T.entries.firstOrNull { it.abiValue == value } ?: error(%S)\n",
                                        resolveTypeName(plan.type.qualifiedName),
                                        "Unknown ${plan.type.qualifiedName} ABI value: \$value",
                                    )
                                    .build(),
                            )
                            .addFunction(
                                FunSpec.builder("toAbi")
                                    .addModifiers(KModifier.INTERNAL)
                                    .addParameter("value", resolveTypeName(plan.type.qualifiedName))
                                    .returns(resolveIntegralTypeName(underlyingType))
                                    .addCode("return value.abiValue\n")
                                    .build(),
                            )
                            .build(),
                    )
                }
            }
            .build()

    private fun renderStruct(plan: KotlinTypeProjectionPlan): TypeSpec =
        TypeSpec.classBuilder(plan.type.name)
            .addModifiers(KModifier.DATA)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameters(
                        plan.type.fields
                            .filterNot { it.isStatic || it.isLiteral }
                            .map { field ->
                                ParameterSpec.builder(field.name.replaceFirstChar(Char::lowercase), resolveTypeName(field.typeName)).build()
                            },
                    )
                    .build(),
            )
            .apply { applyCommonTypeShape(this, plan, addModifiers = false) }
            .apply {
                plan.type.fields
                    .filterNot { it.isStatic || it.isLiteral }
                    .forEach { field ->
                        addProperty(
                            PropertySpec.builder(field.name.replaceFirstChar(Char::lowercase), resolveTypeName(field.typeName))
                                .initializer(field.name.replaceFirstChar(Char::lowercase))
                                .build(),
                        )
                    }
                renderStructMetadataCompanion(plan)?.let(::addType)
            }
            .build()

    private fun renderStructMetadataCompanion(plan: KotlinTypeProjectionPlan): TypeSpec? {
        val fields = plan.type.fields.filterNot { it.isStatic || it.isLiteral }
        val fieldSpecs = fields.map { field ->
            nativeStructFieldSpec(field, plan.type.namespace, plan.typesByQualifiedName) ?: return null
        }
        return TypeSpec.companionObjectBuilder("Metadata")
            .addProperty(
                PropertySpec.builder("layout", NATIVE_STRUCT_LAYOUT_CLASS_NAME)
                    .addModifiers(KModifier.INTERNAL)
                    .initializer(
                        CodeBlock.builder()
                            .add("%T.sequential(", NATIVE_STRUCT_LAYOUT_CLASS_NAME)
                            .apply {
                                fieldSpecs.forEachIndexed { index, spec ->
                                    if (index > 0) {
                                        add(", ")
                                    }
                                    add("%L", spec)
                                }
                            }
                            .add(")")
                            .build(),
                    )
                    .build(),
            )
            .addFunction(
                FunSpec.builder("fromAbi")
                    .addModifiers(KModifier.INTERNAL)
                    .addParameter("source", RAW_ADDRESS_CLASS_NAME)
                    .returns(resolveTypeName(plan.type.qualifiedName))
                    .addCode(
                        CodeBlock.builder()
                            .add("return %T(\n", resolveTypeName(plan.type.qualifiedName))
                            .indent()
                            .apply {
                                fields.forEach { field ->
                                    add("%L = %L,\n", field.name.replaceFirstChar(Char::lowercase), nativeStructFieldReadCode(field, "source"))
                                }
                            }
                            .unindent()
                            .add(")\n")
                            .build(),
                    )
                    .build(),
            )
            .addFunction(
                FunSpec.builder("copyTo")
                    .addModifiers(KModifier.INTERNAL)
                    .addParameter("value", resolveTypeName(plan.type.qualifiedName))
                    .addParameter("destination", RAW_ADDRESS_CLASS_NAME)
                    .addCode(
                        CodeBlock.builder()
                            .apply {
                                fields.forEach { field ->
                                    add("%L\n", nativeStructFieldWriteCode(field, "value", "destination"))
                                }
                            }
                            .build(),
                    )
                    .build(),
            )
            .build()
    }

    private fun nativeStructFieldSpec(
        field: WinRtFieldDefinition,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    ): CodeBlock? {
        val scalarKind = nativeStructScalarKind(field.typeName)
        if (scalarKind != null) {
            return CodeBlock.of("%T(%S, %T.%L)", NATIVE_SCALAR_FIELD_SPEC_CLASS_NAME, field.name.replaceFirstChar(Char::lowercase), NATIVE_STRUCT_SCALAR_KIND_CLASS_NAME, scalarKind)
        }
        val fieldQualifiedName = nativeNestedStructFieldTypeName(field.typeName, currentNamespace, typesByQualifiedName) ?: return null
        val fieldType = runCatching { resolveTypeName(fieldQualifiedName) as? ClassName }.getOrNull() ?: return null
        return CodeBlock.of("%T(%S, %T.Metadata.layout)", NATIVE_NESTED_STRUCT_FIELD_SPEC_CLASS_NAME, field.name.replaceFirstChar(Char::lowercase), fieldType)
    }

    private fun nativeStructScalarKind(typeName: String): String? = when (typeName) {
        "Byte",
        "SByte",
        "Int8",
        "UInt8" -> "INT8"
        "Short",
        "Int16",
        "UInt16" -> "INT16"
        "Int",
        "Int32",
        "UInt",
        "UInt32" -> "INT32"
        "Long",
        "Int64",
        "ULong",
        "UInt64" -> "INT64"
        "Float",
        "Single" -> "FLOAT32"
        "Double" -> "DOUBLE"
        "Char" -> "CHAR16"
        "Guid",
        "System.Guid" -> "GUID"
        else -> null
    }

    private fun nativeNestedStructFieldTypeName(
        typeName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    ): String? {
        val rawTypeName = typeName.substringBefore('<').removeSuffix("?")
        val mappedType = mappedTypeByAbiName(rawTypeName)
        if (mappedType != null && mappedType.abiValueKind != KotlinProjectionAbiValueKind.Struct) {
            return null
        }
        val qualifiedName = when {
            typesByQualifiedName.containsKey(rawTypeName) -> rawTypeName
            currentNamespace.isNotBlank() && typesByQualifiedName.containsKey("$currentNamespace.$rawTypeName") -> "$currentNamespace.$rawTypeName"
            else -> typesByQualifiedName.keys.firstOrNull { it.endsWith(".$rawTypeName") }
        } ?: return mappedType?.abiQualifiedName
        return qualifiedName.takeIf { typesByQualifiedName[it]?.kind == WinRtTypeKind.Struct }
    }

    private fun nativeStructFieldReadCode(field: WinRtFieldDefinition, sourceName: String): CodeBlock {
        val fieldName = field.name.replaceFirstChar(Char::lowercase)
        val slice = CodeBlock.of("layout.slice(%L, %S)", sourceName, fieldName)
        return when (field.typeName) {
            "Byte",
            "Int8" -> CodeBlock.of("%T.readInt8(%L)", PLATFORM_ABI_CLASS_NAME, slice)
            "SByte" -> CodeBlock.of("%T.readInt8(%L)", PLATFORM_ABI_CLASS_NAME, slice)
            "UInt8" -> CodeBlock.of("%T.readInt8(%L).toUByte()", PLATFORM_ABI_CLASS_NAME, slice)
            "Short",
            "Int16" -> CodeBlock.of("%T.readInt16(%L)", PLATFORM_ABI_CLASS_NAME, slice)
            "UInt16" -> CodeBlock.of("%T.readInt16(%L).toUShort()", PLATFORM_ABI_CLASS_NAME, slice)
            "Int",
            "Int32" -> CodeBlock.of("%T.readInt32(%L)", PLATFORM_ABI_CLASS_NAME, slice)
            "UInt",
            "UInt32" -> CodeBlock.of("%T.readInt32(%L).toUInt()", PLATFORM_ABI_CLASS_NAME, slice)
            "Long",
            "Int64" -> CodeBlock.of("%T.readInt64(%L)", PLATFORM_ABI_CLASS_NAME, slice)
            "ULong",
            "UInt64" -> CodeBlock.of("%T.readInt64(%L).toULong()", PLATFORM_ABI_CLASS_NAME, slice)
            "Float",
            "Single" -> CodeBlock.of("%T.readFloat(%L)", PLATFORM_ABI_CLASS_NAME, slice)
            "Double" -> CodeBlock.of("%T.readDouble(%L)", PLATFORM_ABI_CLASS_NAME, slice)
            "Char" -> CodeBlock.of("%T.readChar16(%L)", PLATFORM_ABI_CLASS_NAME, slice)
            "Guid",
            "System.Guid" -> CodeBlock.of("%T.readGuid(%L)", PLATFORM_ABI_CLASS_NAME, slice)
            else -> CodeBlock.of("%T.Metadata.fromAbi(%L)", resolveTypeName(field.typeName), slice)
        }
    }

    private fun nativeStructFieldWriteCode(field: WinRtFieldDefinition, valueName: String, destinationName: String): CodeBlock {
        val fieldName = field.name.replaceFirstChar(Char::lowercase)
        val value = CodeBlock.of("%L.%L", valueName, fieldName)
        val slice = CodeBlock.of("layout.slice(%L, %S)", destinationName, fieldName)
        return when (field.typeName) {
            "Byte",
            "Int8",
            "SByte" -> CodeBlock.of("%T.writeInt8(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, value)
            "UInt8" -> CodeBlock.of("%T.writeInt8(%L, %L.toByte())", PLATFORM_ABI_CLASS_NAME, slice, value)
            "Short",
            "Int16" -> CodeBlock.of("%T.writeInt16(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, value)
            "UInt16" -> CodeBlock.of("%T.writeInt16(%L, %L.toShort())", PLATFORM_ABI_CLASS_NAME, slice, value)
            "Int",
            "Int32" -> CodeBlock.of("%T.writeInt32(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, value)
            "UInt",
            "UInt32" -> CodeBlock.of("%T.writeInt32(%L, %L.toInt())", PLATFORM_ABI_CLASS_NAME, slice, value)
            "Long",
            "Int64" -> CodeBlock.of("%T.writeInt64(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, value)
            "ULong",
            "UInt64" -> CodeBlock.of("%T.writeInt64(%L, %L.toLong())", PLATFORM_ABI_CLASS_NAME, slice, value)
            "Float",
            "Single" -> CodeBlock.of("%T.writeFloat(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, value)
            "Double" -> CodeBlock.of("%T.writeDouble(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, value)
            "Char" -> CodeBlock.of("%T.writeChar16(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, value)
            "Guid",
            "System.Guid" -> CodeBlock.of("%T.writeGuid(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, value)
            else -> CodeBlock.of("%T.Metadata.copyTo(%L, %L)", resolveTypeName(field.typeName), value, slice)
        }
    }

    private fun renderDelegate(plan: KotlinTypeProjectionPlan): TypeSpec {
        val invokeMethod = requireDelegateInvokeMethod(plan.type)
        val builder = TypeSpec.funInterfaceBuilder(plan.type.name)
        applyCommonTypeShape(builder, plan)
        builder.addFunction(
            FunSpec.builder("invoke")
                .addModifiers(KModifier.ABSTRACT, KModifier.OPERATOR)
                .addParameters(
                    invokeMethod.parameters.map { parameter ->
                        ParameterSpec.builder(parameter.name, resolveTypeName(parameter.typeName)).build()
                    },
                )
                .returns(resolveTypeName(invokeMethod.returnTypeName))
                .build(),
        )
        val invokeShape = plan.delegateInvokeShape
        if (invokeShape != null && invokeShape.isSupportedProjectedDelegateShape()) {
            val projectedType = resolveTypeName(plan.type.qualifiedName)
            builder.addType(
                TypeSpec.companionObjectBuilder("Metadata")
                    .addProperty(
                        PropertySpec.builder("DESCRIPTOR", WINRT_DELEGATE_DESCRIPTOR_CLASS_NAME)
                            .addModifiers(KModifier.INTERNAL)
                            .initializer("%L", delegateDescriptorCode(invokeShape))
                            .build(),
                    )
                    .addFunction(
                        FunSpec.builder("fromAbi")
                            .addModifiers(KModifier.INTERNAL)
                            .addParameter("pointer", RAW_ADDRESS_CLASS_NAME)
                            .returns(projectedType.copy(nullable = true))
                            .addCode(
                                CodeBlock.of(
                                    """
                                    val __native = %T.fromAbi(pointer, DESCRIPTOR) ?: return null
                                    return object : %T, %T {
                                        override val nativeObject: %T
                                            get() = __native

                                        override fun invoke(%L): %T {
                                            %L
                                        }
                                    }
                                    """.trimIndent() + "\n",
                                    WINRT_DELEGATE_REFERENCE_CLASS_NAME,
                                    projectedType,
                                    IWINRT_OBJECT_CLASS_NAME,
                                    COM_OBJECT_REFERENCE_CLASS_NAME,
                                    invokeMethod.parameters.joinToString(", ") { "${it.name}: ${resolveTypeName(it.typeName)}" },
                                    resolveTypeName(invokeMethod.returnTypeName),
                                    delegateInvokeBodyCode(invokeShape),
                                ),
                            )
                            .build(),
                    )
                    .build(),
            )
        }
        return builder.build()
    }

    private fun readOnlyCollectionProjectedType(
        binding: KotlinProjectionReadOnlyCollectionBinding,
    ): TypeName = when (binding.kind) {
        KotlinProjectionReadOnlyCollectionKind.Iterable ->
            Iterable::class.asClassName().parameterizedBy(resolveTypeName(requireNotNull(binding.elementBinding).typeName))
        KotlinProjectionReadOnlyCollectionKind.VectorView ->
            List::class.asClassName().parameterizedBy(resolveTypeName(requireNotNull(binding.elementBinding).typeName))
        KotlinProjectionReadOnlyCollectionKind.MapView ->
            Map::class.asClassName().parameterizedBy(
                resolveTypeName(requireNotNull(binding.keyBinding).typeName),
                resolveTypeName(requireNotNull(binding.valueBinding).typeName),
            )
    }

    private fun mutableCollectionProjectedType(
        binding: KotlinProjectionMutableCollectionBinding,
    ): TypeName = when (binding.kind) {
        KotlinProjectionMutableCollectionKind.Vector ->
            MUTABLE_LIST_CLASS_NAME.parameterizedBy(resolveTypeName(requireNotNull(binding.elementBinding).typeName))
        KotlinProjectionMutableCollectionKind.Map ->
            MUTABLE_MAP_CLASS_NAME.parameterizedBy(
                resolveTypeName(requireNotNull(binding.keyBinding).typeName),
                resolveTypeName(requireNotNull(binding.valueBinding).typeName),
            )
    }

    private fun renderMutableCollectionDelegateProperty(
        binding: KotlinProjectionMutableCollectionBinding,
    ): PropertySpec =
        PropertySpec.builder(binding.delegatePropertyName, mutableCollectionProjectedType(binding))
            .addModifiers(KModifier.PRIVATE)
            .delegate(
                CodeBlock.of(
                    "lazy(%T.PUBLICATION) {\n%L}\n",
                    LAZY_THREAD_SAFETY_MODE_CLASS_NAME,
                    renderMutableCollectionDelegateInitializer(binding),
                ),
            )
            .build()

    private fun renderMutableCollectionDelegateInitializer(
        binding: KotlinProjectionMutableCollectionBinding,
    ): CodeBlock = when (binding.kind) {
        KotlinProjectionMutableCollectionKind.Vector -> renderVectorCollectionDelegateInitializer(binding)
        KotlinProjectionMutableCollectionKind.Map -> renderMapCollectionDelegateInitializer(binding)
    }

    private fun renderVectorCollectionDelegateInitializer(
        binding: KotlinProjectionMutableCollectionBinding,
    ): CodeBlock {
        val elementBinding = requireNotNull(binding.elementBinding)
        val elementType = resolveTypeName(elementBinding.typeName)
        val projectedType = mutableCollectionProjectedType(binding)
        val abstractMutableListType = ABSTRACT_MUTABLE_LIST_CLASS_NAME.parameterizedBy(elementType)
        return CodeBlock.of(
            """
            object : %T(), %T, %T {
                override val nativeObject: %T
                    get() = %L

                override val size: Int
                    get() = __readSize().toInt()

                override fun get(index: Int): %T {
                    require(index >= 0) { %S }
                    %L
                }

                override fun set(index: Int, element: %T): %T {
                    require(index >= 0) { %S }
                    val __previous = get(index)
                    %L
                    return __previous
                }

                override fun add(index: Int, element: %T) {
                    require(index >= 0) { %S }
                    %L
                }

                override fun add(element: %T): Boolean {
                    %L
                    return true
                }

                override fun removeAt(index: Int): %T {
                    require(index >= 0) { %S }
                    val __previous = get(index)
                    %L
                    return __previous
                }

                override fun clear() {
                    %L
                }

                private fun __readSize(): %T {
                    %L
                }
            }
            """.trimIndent() + "\n",
            abstractMutableListType,
            projectedType,
            IWINRT_OBJECT_CLASS_NAME,
            COM_OBJECT_REFERENCE_CLASS_NAME,
            binding.ownerCachePropertyName,
            elementType,
            "index must be non-negative.",
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "GETAT_SLOT",
                returnBinding = elementBinding,
                parameterBindings = listOf(
                    KotlinProjectionAbiParameterBinding(
                        name = "index",
                        typeBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.UInt32, "UInt"),
                    ),
                ),
            ).toString(),
            elementType,
            elementType,
            "index must be non-negative.",
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "SETAT_SLOT",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                parameterBindings = listOf(
                    KotlinProjectionAbiParameterBinding(
                        name = "index",
                        typeBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.UInt32, "UInt"),
                    ),
                    KotlinProjectionAbiParameterBinding("element", elementBinding),
                ),
            ).toString(),
            elementType,
            "index must be non-negative.",
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "INSERTAT_SLOT",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                parameterBindings = listOf(
                    KotlinProjectionAbiParameterBinding(
                        name = "index",
                        typeBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.UInt32, "UInt"),
                    ),
                    KotlinProjectionAbiParameterBinding("element", elementBinding),
                ),
            ).toString(),
            elementType,
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "APPEND_SLOT",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                parameterBindings = listOf(KotlinProjectionAbiParameterBinding("element", elementBinding)),
            ).toString(),
            elementType,
            "index must be non-negative.",
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "REMOVEAT_SLOT",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                parameterBindings = listOf(
                    KotlinProjectionAbiParameterBinding(
                        name = "index",
                        typeBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.UInt32, "UInt"),
                    ),
                ),
            ).toString(),
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "CLEAR_SLOT",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
            ).toString(),
            UInt::class.asClassName(),
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "SIZE_GETTER_SLOT",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.UInt32, "UInt"),
            ).toString(),
        )
    }

    private fun renderMapCollectionDelegateInitializer(
        binding: KotlinProjectionMutableCollectionBinding,
    ): CodeBlock {
        val keyBinding = requireNotNull(binding.keyBinding)
        val valueBinding = requireNotNull(binding.valueBinding)
        val keyType = resolveTypeName(keyBinding.typeName)
        val valueType = resolveTypeName(valueBinding.typeName)
        val projectedType = mutableCollectionProjectedType(binding)
        val abstractMutableMapType = ABSTRACT_MUTABLE_MAP_CLASS_NAME.parameterizedBy(keyType, valueType)
        val entryType = MUTABLE_MAP_CLASS_NAME.nestedClass("MutableEntry").parameterizedBy(keyType, valueType)
        return CodeBlock.of(
            """
            object : %T(), %T, %T {
                override val nativeObject: %T
                    get() = %L

                override val entries: MutableSet<%T>
                    get() {
                        val __map = this
                        return object : %T<%T>() {
                            override val size: Int
                                get() = __map.size

                            override fun add(element: %T): Boolean {
                                val __replaced = __map.containsKey(element.key)
                                __map.put(element.key, element.value)
                                return !__replaced
                            }

                            override fun iterator(): MutableIterator<%T> {
                                val __iterator = __createEntryIterator()
                                return object : MutableIterator<%T> {
                                    private var __lastReturned: %T? = null

                                    override fun hasNext(): Boolean = __iterator.hasNext()

                                    override fun next(): %T {
                                        val __entry = __iterator.next()
                                        val __mutableEntry = object : %T {
                                            override val key: %T = __entry.key
                                            private var __currentValue: %T = __entry.value
                                            override val value: %T
                                                get() = __currentValue

                                            override fun setValue(newValue: %T): %T {
                                                val __previous = __currentValue
                                                __map.put(key, newValue)
                                                __currentValue = newValue
                                                return __previous
                                            }
                                        }
                                        __lastReturned = __mutableEntry
                                        return __mutableEntry
                                    }

                                    override fun remove() {
                                        val __entry = __lastReturned ?: throw %T(%S)
                                        __map.remove(__entry.key)
                                        __lastReturned = null
                                    }
                                }
                            }
                        }
                    }

                override val size: Int
                    get() = __readSize().toInt()

                override fun containsKey(key: %T): Boolean {
                    %L
                }

                override fun get(key: %T): %T? {
                    return if (containsKey(key)) {
                        %L
                    } else {
                        null
                    }
                }

                override fun put(key: %T, value: %T): %T? {
                    val __previous = get(key)
                    %L
                    return __previous
                }

                override fun remove(key: %T): %T? {
                    val __previous = get(key) ?: return null
                    %L
                    return __previous
                }

                override fun clear() {
                    %L
                }

                private fun __createEntryIterator(): Iterator<Map.Entry<%T, %T>> {
                    %L
                }

                private fun __readSize(): %T {
                    %L
                }
            }
            """.trimIndent() + "\n",
            abstractMutableMapType,
            projectedType,
            IWINRT_OBJECT_CLASS_NAME,
            COM_OBJECT_REFERENCE_CLASS_NAME,
            binding.ownerCachePropertyName,
            entryType,
            ABSTRACT_MUTABLE_SET_CLASS_NAME,
            entryType,
            entryType,
            entryType,
            entryType,
            entryType,
            entryType,
            MUTABLE_MAP_CLASS_NAME.nestedClass("MutableEntry").parameterizedBy(keyType, valueType),
            keyType,
            valueType,
            valueType,
            valueType,
            valueType,
            ILLEGAL_STATE_EXCEPTION_CLASS_NAME,
            "remove() before next() is not allowed for mutable map entry iteration.",
            keyType,
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "HASKEY_SLOT",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Boolean, "Boolean"),
                parameterBindings = listOf(KotlinProjectionAbiParameterBinding("key", keyBinding)),
            ).toString(),
            keyType,
            valueType,
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "LOOKUP_SLOT",
                returnBinding = valueBinding,
                parameterBindings = listOf(KotlinProjectionAbiParameterBinding("key", keyBinding)),
            ).toString(),
            keyType,
            valueType,
            valueType,
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "INSERT_SLOT",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Boolean, "Boolean"),
                parameterBindings = listOf(
                    KotlinProjectionAbiParameterBinding("key", keyBinding),
                    KotlinProjectionAbiParameterBinding("value", valueBinding),
                ),
            ).toString(),
            keyType,
            valueType,
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "REMOVE_SLOT",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                parameterBindings = listOf(KotlinProjectionAbiParameterBinding("key", keyBinding)),
            ).toString(),
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "CLEAR_SLOT",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
            ).toString(),
            keyType,
            valueType,
            renderMappedIteratorCreationCode(
                ownerExpression = binding.ownerCachePropertyName,
                iterableSlotInterfaceQualifiedName = "Windows.Foundation.Collections.IIterable",
                elementBinding = null,
                entryBinding = binding.asReadOnlyEntryBinding(),
            ),
            UInt::class.asClassName(),
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "SIZE_GETTER_SLOT",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.UInt32, "UInt"),
            ).toString(),
        )
    }

    private fun renderReadOnlyCollectionDelegateProperty(
        binding: KotlinProjectionReadOnlyCollectionBinding,
    ): PropertySpec =
        PropertySpec.builder(binding.delegatePropertyName, readOnlyCollectionProjectedType(binding))
            .addModifiers(KModifier.PRIVATE)
            .delegate(
                CodeBlock.of(
                    "lazy(%T.PUBLICATION) {\n%L}\n",
                    LAZY_THREAD_SAFETY_MODE_CLASS_NAME,
                    renderReadOnlyCollectionDelegateInitializer(binding),
                ),
            )
            .build()

    private fun renderReadOnlyCollectionDelegateInitializer(
        binding: KotlinProjectionReadOnlyCollectionBinding,
    ): CodeBlock = when (binding.kind) {
        KotlinProjectionReadOnlyCollectionKind.Iterable -> renderIterableCollectionDelegateInitializer(binding)
        KotlinProjectionReadOnlyCollectionKind.VectorView -> renderVectorViewCollectionDelegateInitializer(binding)
        KotlinProjectionReadOnlyCollectionKind.MapView -> renderMapViewCollectionDelegateInitializer(binding)
    }

    private fun renderIterableCollectionDelegateInitializer(
        binding: KotlinProjectionReadOnlyCollectionBinding,
    ): CodeBlock {
        val elementBinding = requireNotNull(binding.elementBinding)
        val elementType = resolveTypeName(elementBinding.typeName)
        val projectedType = readOnlyCollectionProjectedType(binding)
        val iteratorType = Iterator::class.asClassName().parameterizedBy(elementType)
        return CodeBlock.of(
            """
            object : %T, %T {
                override val nativeObject: %T
                    get() = %L

                override fun iterator(): %T {
                    val __owner = %L
                    %L
                }
            }
            """.trimIndent() + "\n",
            projectedType,
            IWINRT_OBJECT_CLASS_NAME,
            COM_OBJECT_REFERENCE_CLASS_NAME,
            binding.ownerCachePropertyName,
            iteratorType,
            binding.ownerCachePropertyName,
            renderMappedIteratorCreationCode(
                ownerExpression = "__owner",
                iterableSlotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                elementBinding = elementBinding,
                entryBinding = null,
            ),
        )
    }

    private fun renderVectorViewCollectionDelegateInitializer(
        binding: KotlinProjectionReadOnlyCollectionBinding,
    ): CodeBlock {
        val elementBinding = requireNotNull(binding.elementBinding)
        val elementType = resolveTypeName(elementBinding.typeName)
        val projectedType = readOnlyCollectionProjectedType(binding)
        val abstractListType = ABSTRACT_LIST_CLASS_NAME.parameterizedBy(elementType)
        return CodeBlock.of(
            """
            object : %T(), %T, %T {
                override val nativeObject: %T
                    get() = %L

                override val size: Int
                    get() = __readSize().toInt()

                override fun get(index: Int): %T {
                    require(index >= 0) { %S }
                    %L
                }

                private fun __readSize(): %T {
                    %L
                }
            }
            """.trimIndent() + "\n",
            abstractListType,
            projectedType,
            IWINRT_OBJECT_CLASS_NAME,
            COM_OBJECT_REFERENCE_CLASS_NAME,
            binding.ownerCachePropertyName,
            elementType,
            "index must be non-negative.",
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "GETAT_SLOT",
                returnBinding = elementBinding,
                parameterBindings = listOf(
                    KotlinProjectionAbiParameterBinding(
                        name = "index",
                        typeBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.UInt32, "UInt"),
                    ),
                ),
            ).toString(),
            UInt::class.asClassName(),
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "SIZE_GETTER_SLOT",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.UInt32, "UInt"),
            ).toString(),
        )
    }

    private fun renderMapViewCollectionDelegateInitializer(
        binding: KotlinProjectionReadOnlyCollectionBinding,
    ): CodeBlock {
        val keyBinding = requireNotNull(binding.keyBinding)
        val valueBinding = requireNotNull(binding.valueBinding)
        val keyType = resolveTypeName(keyBinding.typeName)
        val valueType = resolveTypeName(valueBinding.typeName)
        val projectedType = readOnlyCollectionProjectedType(binding)
        val abstractMapType = ABSTRACT_MAP_CLASS_NAME.parameterizedBy(keyType, valueType)
        val entryType = Map.Entry::class.asClassName().parameterizedBy(keyType, valueType)
        return CodeBlock.of(
            """
            object : %T(), %T, %T {
                override val nativeObject: %T
                    get() = %L

                override val entries: Set<%T>
                    get() {
                        val __entries = linkedSetOf<%T>()
                        val __iterator = __createEntryIterator()
                        while (__iterator.hasNext()) {
                            __entries += __iterator.next()
                        }
                        return __entries
                    }

                override fun containsKey(key: %T): Boolean {
                    %L
                }

                override fun get(key: %T): %T? {
                    return if (containsKey(key)) {
                        %L
                    } else {
                        null
                    }
                }

                private fun __createEntryIterator(): %T {
                    %L
                }
            }
            """.trimIndent() + "\n",
            abstractMapType,
            projectedType,
            IWINRT_OBJECT_CLASS_NAME,
            COM_OBJECT_REFERENCE_CLASS_NAME,
            binding.ownerCachePropertyName,
            entryType,
            entryType,
            keyType,
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "HASKEY_SLOT",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Boolean, "Boolean"),
                parameterBindings = listOf(KotlinProjectionAbiParameterBinding("key", keyBinding)),
            ).toString(),
            keyType,
            valueType,
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "LOOKUP_SLOT",
                returnBinding = valueBinding,
                parameterBindings = listOf(KotlinProjectionAbiParameterBinding("key", keyBinding)),
            ).toString(),
            Iterator::class.asClassName().parameterizedBy(entryType),
            renderMappedIteratorCreationCode(
                ownerExpression = binding.ownerCachePropertyName,
                iterableSlotInterfaceQualifiedName = "Windows.Foundation.Collections.IIterable",
                elementBinding = null,
                entryBinding = binding,
            ),
        )
    }

    private fun renderMappedIteratorCreationCode(
        ownerExpression: String,
        iterableSlotInterfaceQualifiedName: String,
        elementBinding: KotlinProjectionAbiTypeBinding?,
        entryBinding: KotlinProjectionReadOnlyCollectionBinding?,
    ): CodeBlock {
        val effectiveEntryBinding = entryBinding ?: elementBinding
            ?.takeIf { it.kind == KotlinProjectionAbiValueKind.MappedKeyValuePair && it.typeArguments.size == 2 }
            ?.let {
                KotlinProjectionReadOnlyCollectionBinding(
                    kind = KotlinProjectionReadOnlyCollectionKind.MapView,
                    ownerInterfaceQualifiedName = it.resolvedTypeName,
                    ownerCachePropertyName = "",
                    slotInterfaceQualifiedName = it.resolvedTypeName,
                    delegatePropertyName = "",
                    keyBinding = it.typeArguments[0],
                    valueBinding = it.typeArguments[1],
                )
            }
        val returnType = when {
            effectiveEntryBinding != null -> Map.Entry::class.asClassName().parameterizedBy(
                resolveTypeName(requireNotNull(effectiveEntryBinding.keyBinding).typeName),
                resolveTypeName(requireNotNull(effectiveEntryBinding.valueBinding).typeName),
            )
            else -> resolveTypeName(requireNotNull(elementBinding).typeName)
        }
        return CodeBlock.of(
            """
            fun __createIteratorReference(): %T {
                %L
            }
            val __iterator = __createIteratorReference()
            return object : %T {
                private var __hasNext = __iteratorHasCurrent(__iterator)

                override fun hasNext(): Boolean = __hasNext

                override fun next(): %T {
                    if (!__hasNext) {
                        throw %T()
                    }
                    val __current = __readCurrent(__iterator)
                    __hasNext = __iteratorMoveNext(__iterator)
                    return __current
                }

                private fun __readCurrent(__iteratorRef: %T): %T {
                    %L
                }

                private fun __iteratorHasCurrent(__iteratorRef: %T): Boolean {
                    %L
                }

                private fun __iteratorMoveNext(__iteratorRef: %T): Boolean {
                    %L
                }
            }
            """.trimIndent() + "\n",
            IUNKNOWN_REFERENCE_CLASS_NAME,
            renderCollectionInvocation(
                invokeTargetExpression = ownerExpression,
                slotInterfaceQualifiedName = iterableSlotInterfaceQualifiedName,
                slotConstantName = "FIRST_SLOT",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.UnknownReference, IUNKNOWN_REFERENCE_CLASS_NAME.simpleName),
            ).toString(),
            Iterator::class.asClassName().parameterizedBy(returnType),
            returnType,
            NO_SUCH_ELEMENT_EXCEPTION_CLASS_NAME,
            IUNKNOWN_REFERENCE_CLASS_NAME,
            returnType,
            renderMappedIteratorCurrentCode(elementBinding, effectiveEntryBinding, "__iteratorRef").toString(),
            IUNKNOWN_REFERENCE_CLASS_NAME,
            renderCollectionInvocation(
                invokeTargetExpression = "__iteratorRef",
                slotInterfaceQualifiedName = "Windows.Foundation.Collections.IIterator",
                slotConstantName = "HASCURRENT_GETTER_SLOT",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Boolean, "Boolean"),
            ).toString(),
            IUNKNOWN_REFERENCE_CLASS_NAME,
            renderCollectionInvocation(
                invokeTargetExpression = "__iteratorRef",
                slotInterfaceQualifiedName = "Windows.Foundation.Collections.IIterator",
                slotConstantName = "MOVENEXT_SLOT",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Boolean, "Boolean"),
            ).toString(),
        )
    }

    private fun renderMappedIteratorCurrentCode(
        elementBinding: KotlinProjectionAbiTypeBinding?,
        entryBinding: KotlinProjectionReadOnlyCollectionBinding?,
        iteratorExpression: String,
    ): CodeBlock {
        if (entryBinding != null) {
            val keyBinding = requireNotNull(entryBinding.keyBinding)
            val valueBinding = requireNotNull(entryBinding.valueBinding)
            val keyType = resolveTypeName(keyBinding.typeName)
            val valueType = resolveTypeName(valueBinding.typeName)
            return CodeBlock.of(
                """
                fun __readPairReference(): %T {
                    %L
                }
                fun __readKey(__pairRef: %T): %T {
                    %L
                }
                fun __readValue(__pairRef: %T): %T {
                    %L
                }
                val __pair = __readPairReference()
                val __key = __readKey(__pair)
                val __value = __readValue(__pair)
                return object : %T {
                    override val key: %T = __key
                    override val value: %T = __value
                }
                """.trimIndent(),
                IUNKNOWN_REFERENCE_CLASS_NAME,
                renderCollectionInvocation(
                    invokeTargetExpression = iteratorExpression,
                    slotInterfaceQualifiedName = "Windows.Foundation.Collections.IIterator",
                    slotConstantName = "CURRENT_GETTER_SLOT",
                    returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.UnknownReference, IUNKNOWN_REFERENCE_CLASS_NAME.simpleName),
                ).toString(),
                IUNKNOWN_REFERENCE_CLASS_NAME,
                keyType,
                renderCollectionInvocation(
                    invokeTargetExpression = "__pairRef",
                    slotInterfaceQualifiedName = "Windows.Foundation.Collections.IKeyValuePair",
                    slotConstantName = "KEY_GETTER_SLOT",
                    returnBinding = keyBinding,
                ).toString(),
                IUNKNOWN_REFERENCE_CLASS_NAME,
                valueType,
                renderCollectionInvocation(
                    invokeTargetExpression = "__pairRef",
                    slotInterfaceQualifiedName = "Windows.Foundation.Collections.IKeyValuePair",
                    slotConstantName = "VALUE_GETTER_SLOT",
                    returnBinding = valueBinding,
                ).toString(),
                Map.Entry::class.asClassName().parameterizedBy(keyType, valueType),
                keyType,
                valueType,
            )
        }
        return renderCollectionInvocation(
            invokeTargetExpression = iteratorExpression,
            slotInterfaceQualifiedName = "Windows.Foundation.Collections.IIterator",
            slotConstantName = "CURRENT_GETTER_SLOT",
            returnBinding = requireNotNull(elementBinding),
        ).toString().let { CodeBlock.of("%L", it) }
    }

    private fun renderCollectionInvocation(
        invokeTargetExpression: String,
        slotInterfaceQualifiedName: String,
        slotConstantName: String,
        returnBinding: KotlinProjectionAbiTypeBinding,
        parameterBindings: List<KotlinProjectionAbiParameterBinding> = emptyList(),
    ): CodeBlock {
        val callPlan = requireAbiCallPlan(
            bindingName = "${slotInterfaceQualifiedName.substringAfterLast('.')}_$slotConstantName",
            returnBinding = returnBinding,
            parameterBindings = parameterBindings,
        )
        return renderInlineAbiInvocation(
            invokeTargetExpression = invokeTargetExpression,
            slotExpression = CodeBlock.of("%T.Metadata.%L", projectionClassName(slotInterfaceQualifiedName), slotConstantName),
            callPlan = callPlan,
        ) ?: error("Generator read-only collection parity failed to emit $slotInterfaceQualifiedName.$slotConstantName")
    }

    private fun applyCommonTypeShape(
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

    private fun renderVisibility(visibility: KotlinProjectionVisibility): KModifier = when (visibility) {
        KotlinProjectionVisibility.Public -> KModifier.PUBLIC
        KotlinProjectionVisibility.Internal -> KModifier.INTERNAL
    }

    private fun renderInterfaceMethod(method: WinRtMethodDefinition): FunSpec =
        FunSpec.builder(method.name)
            .addModifiers(KModifier.ABSTRACT)
            .addParameters(method.parameters.map { ParameterSpec.builder(it.name, resolveTypeName(it.typeName)).build() })
            .returns(resolveTypeName(method.returnTypeName))
            .build()

    private fun renderStubMethod(method: WinRtMethodDefinition, override: Boolean = false): FunSpec {
        val builder = FunSpec.builder(method.name)
            .addParameters(method.parameters.map { ParameterSpec.builder(it.name, resolveTypeName(it.typeName)).build() })
            .returns(resolveTypeName(method.returnTypeName))
            .addCode("return error(%S)\n", "Not yet bound to winrt-runtime")
        if (override) {
            builder.addModifiers(KModifier.OVERRIDE)
        }
        return builder.build()
    }

    private fun renderRuntimeMethod(
        plan: KotlinTypeProjectionPlan,
        method: WinRtMethodDefinition,
    ): FunSpec =
        renderBoundMethod(plan, method) ?: renderStubMethod(method)

    private fun renderInterfaceProperty(property: WinRtPropertyDefinition): PropertySpec =
        PropertySpec.builder(property.name.replaceFirstChar(Char::lowercase), resolveTypeName(property.typeName))
            .mutable(!property.isReadOnly)
            .addModifiers(KModifier.ABSTRACT)
            .build()

    private fun renderStubProperty(property: WinRtPropertyDefinition, override: Boolean = false): PropertySpec {
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

    private fun renderRuntimeProperty(
        plan: KotlinTypeProjectionPlan,
        property: WinRtPropertyDefinition,
    ): PropertySpec =
        renderBoundProperty(plan, property) ?: renderStubProperty(property)

    private fun renderBoundMethod(
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

    private fun renderBoundProperty(
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

    private fun renderBoundInvocation(
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

    private fun renderBoundStaticInvocation(
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

    private fun buildAbiCallPlan(
        binding: KotlinProjectionInstanceMemberBinding,
    ): KotlinProjectionAbiCallPlan? =
        buildAbiCallPlan(binding.returnBinding, binding.parameterBindings, binding.marshalerPlanDescriptor)

    private fun buildAbiCallPlan(
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

    private fun requireAbiCallPlan(
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

    private fun buildAbiParameterMarshaler(
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

    private fun buildAbiReturnMarshaler(
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

    private fun resolvedReturnClassName(
        returnBinding: KotlinProjectionAbiTypeBinding,
    ): ClassName? =
        runCatching { resolveTypeName(returnBinding.typeName) as? ClassName }.getOrNull()
            ?: runCatching { resolveTypeName(returnBinding.resolvedTypeName) as? ClassName }.getOrNull()

    private fun mappedKeyValuePairReturnReadback(
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

    private fun arrayReturnMarshaler(
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

    private fun nonBlittableArrayReturnMarshaler(
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

    private fun nativeStructClassName(
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

    private fun nativeArrayElementSizeExpression(
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

    private fun nativeArrayIntegralElementSizeExpression(type: WinRtIntegralType): CodeBlock =
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

    private fun nativeArrayElementSliceCode(
        elementBinding: KotlinProjectionAbiTypeBinding,
        dataExpression: CodeBlock,
        indexExpression: CodeBlock,
    ): CodeBlock? {
        val elementSize = nativeArrayElementSizeExpression(elementBinding) ?: return null
        return CodeBlock.of("%T.slice(%L, %L.toLong() * %L, %L)", PLATFORM_ABI_CLASS_NAME, dataExpression, indexExpression, elementSize, elementSize)
    }

    private fun nativeArrayElementReadCode(
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

    private fun nativeArrayEnumElementReadCode(
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

    private fun nativeArrayElementWriteCode(
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

    private fun nativeArrayEnumElementWriteCode(
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

    private fun nativeStructParameterMarshaler(
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

    private fun arrayParameterMarshaler(
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

    private fun nonBlittableArrayParameterMarshaler(
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

    private fun nonBlittableArrayElementMarshalerExpression(
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

    private fun asyncActionWithProgressReturnReadback(
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

    private fun asyncOperationReturnReadback(
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

    private fun asyncOperationWithProgressReturnReadback(
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

    private fun asyncOperationResultTypeSignature(
        resultBinding: KotlinProjectionAbiTypeBinding,
    ): CodeBlock? = abiTypeSignature(resultBinding)

    private fun referenceParameterMarshaler(
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

    private fun referenceReadbackExpression(
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

    private fun referenceReturnReadback(
        typeBinding: KotlinProjectionAbiTypeBinding,
        projectionClass: ClassName,
    ): CodeBlock? =
        referenceReadbackExpression(typeBinding, projectionClass, "__resultOut")
            ?.let { CodeBlock.of("return %L\n", it) }

    private fun abiTypeSignature(
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

    private fun referenceTypeSignatureCode(
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

    private fun referenceInterfaceIdCode(
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

    private fun abiResultAllocationForAsyncOperationResult(
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

    private fun bindingAllocationForAsyncEnum(
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

    private fun abiTypeSignatureForIntegralType(type: WinRtIntegralType): CodeBlock = when (type) {
        WinRtIntegralType.Int8 -> CodeBlock.of("%T.int8()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
        WinRtIntegralType.UInt8 -> CodeBlock.of("%T.uint8()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
        WinRtIntegralType.Int16 -> CodeBlock.of("%T.int16()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
        WinRtIntegralType.UInt16 -> CodeBlock.of("%T.uint16()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
        WinRtIntegralType.Int32 -> CodeBlock.of("%T.int32()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
        WinRtIntegralType.UInt32 -> CodeBlock.of("%T.uint32()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
        WinRtIntegralType.Int64 -> CodeBlock.of("%T.int64()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
        WinRtIntegralType.UInt64 -> CodeBlock.of("%T.uint64()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
    }

    private fun asyncOperationResultReadbackExpression(
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

    private fun asyncMappedCollectionResultReadbackExpression(
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

    private fun asyncEnumResultReadbackExpression(
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

    private fun mappedCollectionReturnReadback(
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

    private fun readOnlyCollectionBindingForReturn(
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

    private fun mutableCollectionBindingForReturn(
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

    private fun bindableCollectionParameterMarshaler(
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

    private fun mappedCollectionParameterMarshaler(
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

    private fun collectionReferenceAdapterCode(
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

    private fun bindableCollectionReturnReadback(
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

    private fun enumParameterMarshaler(
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

    private fun delegateParameterMarshaler(
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

    private fun outboundDelegateInvokeShape(
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

    private fun delegateInterfaceIdCode(
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

    private fun enumReturnReadback(
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

    private fun abiResultAllocationForIntegralType(type: WinRtIntegralType): CodeBlock =
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

    private fun abiIntegralArgumentConversionSuffix(type: WinRtIntegralType): String =
        integralAbiDescriptor(type).argumentConversionSuffix

    private fun abiIntegralReadbackExpression(type: WinRtIntegralType): CodeBlock =
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

    private fun delegateParameterKindsCode(
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

    private fun delegateDescriptorCode(
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

    private fun delegateInvokeParameterKindsCode(
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

    private fun delegateInvokeReturnKindCode(
        returnBinding: KotlinProjectionAbiTypeBinding,
    ): CodeBlock = delegateInvokeValueKindCode(returnBinding)

    private fun delegateValueKindCode(typeBinding: KotlinProjectionAbiTypeBinding): CodeBlock = when (typeBinding.kind) {
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

    private fun delegateEnumValueKindCode(typeBinding: KotlinProjectionAbiTypeBinding): CodeBlock =
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

    private fun delegateInvokeValueKindCode(typeBinding: KotlinProjectionAbiTypeBinding): CodeBlock = when (typeBinding.kind) {
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

    private fun delegateInvokeBodyCode(
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

    private fun delegateInvokeArgumentCode(
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

    private fun delegateInvokeReturnCode(
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

    private fun delegateCallbackArgumentCodeList(
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

    private fun delegateCallbackArgumentCode(
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

    private fun delegateEnumCallbackArgumentCode(
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

    private fun renderInlineAbiInvocation(
        invokeTargetExpression: String,
        slotExpression: String,
        callPlan: KotlinProjectionAbiCallPlan,
    ): CodeBlock? =
        renderInlineAbiInvocation(invokeTargetExpression, CodeBlock.of("%L", slotExpression), callPlan)

    private fun renderInlineAbiInvocation(
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

    private fun renderComVtableInvocation(
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

    private fun renderBoundEventFunctions(
        plan: KotlinTypeProjectionPlan,
        event: WinRtEventDefinition,
        override: Boolean = false,
    ): List<FunSpec>? {
        val addBinding = plan.instanceMemberBindings.firstOrNull {
            it.bindingName == "${event.name.uppercase()}_ADD_SLOT"
        } ?: return null
        val removeBinding = plan.instanceMemberBindings.firstOrNull {
            it.bindingName == "${event.name.uppercase()}_REMOVE_SLOT"
        } ?: return null
        return buildBoundEventFunctions(
            event = event,
            eventInvokeDescriptor = plan.eventInvokeDescriptors.firstOrNull { it.eventName == event.name && !it.isStatic },
            addInvocation = renderBoundInvocation(addBinding),
            removeInvocation = renderBoundInvocation(removeBinding),
            override = override,
        )
    }

    private fun renderBoundStaticEventFunctions(
        plan: KotlinTypeProjectionPlan,
        event: WinRtEventDefinition,
    ): List<FunSpec>? {
        val addBinding = plan.staticMemberBindings.firstOrNull {
            it.bindingName == "STATIC_${event.name.uppercase()}_ADD_SLOT"
        } ?: return null
        val removeBinding = plan.staticMemberBindings.firstOrNull {
            it.bindingName == "STATIC_${event.name.uppercase()}_REMOVE_SLOT"
        } ?: return null
        return buildBoundEventFunctions(
            event = event,
            eventInvokeDescriptor = plan.eventInvokeDescriptors.firstOrNull { it.eventName == event.name && it.isStatic },
            addInvocation = renderBoundStaticInvocation(addBinding),
            removeInvocation = renderBoundStaticInvocation(removeBinding),
            override = false,
        )
    }

    private fun buildBoundEventFunctions(
        event: WinRtEventDefinition,
        eventInvokeDescriptor: WinRtEventInvokeDescriptor?,
        addInvocation: CodeBlock,
        removeInvocation: CodeBlock,
        override: Boolean,
    ): List<FunSpec> {
        val typeName = resolveTypeName(eventInvokeDescriptor?.delegateTypeName ?: event.delegateTypeName)
        return listOf(
            FunSpec.builder("add${event.name}")
                .apply { if (override) addModifiers(KModifier.OVERRIDE) }
                .addParameter("handler", typeName)
                .returns(Int::class.asClassName())
                .addCode("%L\n", addInvocation)
                .build(),
            FunSpec.builder("remove${event.name}")
                .apply { if (override) addModifiers(KModifier.OVERRIDE) }
                .addParameter("token", Int::class.asClassName())
                .addCode("%L\n", removeInvocation)
                .build(),
        )
    }

    private fun renderEventProperty(
        event: WinRtEventDefinition,
        eventInvokeDescriptor: WinRtEventInvokeDescriptor?,
        abstract: Boolean,
        override: Boolean = false,
    ): PropertySpec {
        val typeName = resolveTypeName(eventInvokeDescriptor?.delegateTypeName ?: event.delegateTypeName)
        val builder = PropertySpec.builder(
            event.name.replaceFirstChar(Char::lowercase),
            WINRT_EVENT_CLASS_NAME.parameterizedBy(typeName),
        )
        if (abstract) {
            return builder.addModifiers(KModifier.ABSTRACT).build()
        }
        if (override) {
            builder.addModifiers(KModifier.OVERRIDE)
        }
        return builder
            .delegate(
                CodeBlock.of(
                    "lazy(%T.PUBLICATION) { %T(::add%L, ::remove%L) }",
                    LAZY_THREAD_SAFETY_MODE_CLASS_NAME,
                    WINRT_EVENT_CLASS_NAME,
                    event.name,
                    event.name,
                ),
            )
            .build()
    }

    private fun renderEventFunctions(event: WinRtEventDefinition, abstract: Boolean, override: Boolean = false): List<FunSpec> {
        val typeName = resolveTypeName(event.delegateTypeName)
        return listOf(
            FunSpec.builder("add${event.name}")
                .addParameter("handler", typeName)
                .apply {
                    if (abstract) {
                        addModifiers(KModifier.ABSTRACT)
                    } else {
                        if (override) {
                            addModifiers(KModifier.OVERRIDE)
                        }
                        addCode("return error(%S)\n", "Not yet bound to winrt-runtime")
                    }
                }
                .returns(Int::class.asClassName())
                .build(),
            FunSpec.builder("remove${event.name}")
                .addParameter("token", Int::class.asClassName())
                .apply {
                    if (abstract) {
                        addModifiers(KModifier.ABSTRACT)
                    } else {
                        if (override) {
                            addModifiers(KModifier.OVERRIDE)
                        }
                        addCode("error(%S)\n", "Not yet bound to winrt-runtime")
                    }
                }
                .build(),
        )
    }

    private fun buildMetadataCompanionShell(
        plan: KotlinTypeProjectionPlan,
        staticMethods: List<WinRtMethodDefinition>,
        staticProperties: List<WinRtPropertyDefinition>,
        staticEvents: List<WinRtEventDefinition>,
    ): TypeSpec =
        TypeSpec.companionObjectBuilder("Metadata")
            .apply {
                appendMetadataCompanionMembers(this, plan)
                staticMethods.forEach { addFunction(renderBoundStaticMethod(plan, it) ?: renderStubMethod(it)) }
                staticProperties.forEach { addProperty(renderBoundStaticProperty(plan, it) ?: renderStubProperty(it)) }
                staticEvents.forEach { event ->
                    addProperty(
                        renderEventProperty(
                            event = event,
                            eventInvokeDescriptor = plan.eventInvokeDescriptors.firstOrNull { it.eventName == event.name && it.isStatic },
                            abstract = false,
                        ),
                    )
                    (renderBoundStaticEventFunctions(plan, event) ?: renderEventFunctions(event, abstract = false))
                        .forEach(::addFunction)
                }
            }
            .build()

    private fun renderBoundStaticMethod(
        plan: KotlinTypeProjectionPlan,
        method: WinRtMethodDefinition,
    ): FunSpec? {
        if (
            method.name == "create" &&
            method.parameters.isEmpty() &&
            KotlinProjectionCompanionKind.ActivationFactory in plan.companionKinds &&
            method.returnTypeName.let { it == plan.type.qualifiedName || it == plan.type.name }
        ) {
            return FunSpec.builder(method.name)
                .returns(resolveTypeName(method.returnTypeName))
                .addCode("return Metadata.wrap(ActivationFactory.activate())\n")
                .build()
        }
        val binding = plan.staticMemberBindings.firstOrNull { it.bindingName == "STATIC_${method.name.uppercase()}_SLOT" } ?: return null
        val invocation = renderBoundStaticInvocation(binding)
        return FunSpec.builder(method.name)
            .returns(resolveTypeName(method.returnTypeName))
            .addParameters(method.parameters.map { ParameterSpec.builder(it.name, resolveTypeName(it.typeName)).build() })
            .addCode("%L\n", invocation)
            .build()
    }

    private fun renderBoundStaticProperty(
        plan: KotlinTypeProjectionPlan,
        property: WinRtPropertyDefinition,
    ): PropertySpec? {
        val builder = PropertySpec.builder(
            property.name.replaceFirstChar(Char::lowercase),
            resolveTypeName(property.typeName),
        ).mutable(!property.isReadOnly)
        val getterBinding = plan.staticMemberBindings.firstOrNull {
            it.bindingName == "STATIC_${property.name.uppercase()}_GETTER_SLOT"
        } ?: return null
        val getterInvocation = renderBoundStaticInvocation(getterBinding)
        builder.getter(
            FunSpec.getterBuilder()
                .addCode("%L\n", getterInvocation)
                .build(),
        )
        if (!property.isReadOnly) {
            val setterBinding = plan.staticMemberBindings.firstOrNull {
                it.bindingName == "STATIC_${property.name.uppercase()}_SETTER_SLOT"
            }
            builder.setter(
                FunSpec.setterBuilder()
                    .addParameter("value", resolveTypeName(property.typeName))
                    .addCode("%L\n", setterBinding?.let(::renderBoundStaticInvocation) ?: CodeBlock.of("error(%S)", "Not yet bound to winrt-runtime"))
                    .build(),
            )
        }
        return builder.build()
    }

    private fun appendCompanionShells(
        builder: TypeSpec.Builder,
        plan: KotlinTypeProjectionPlan,
        excludeKinds: Set<KotlinProjectionCompanionKind> = emptySet(),
    ) {
        plan.companionKinds
            .filterNot(excludeKinds::contains)
            .forEach { kind ->
                builder.addType(buildCompanionShell(kind, plan))
            }
    }

    private fun buildCompanionShell(
        kind: KotlinProjectionCompanionKind,
        plan: KotlinTypeProjectionPlan,
    ): TypeSpec = when (kind) {
        KotlinProjectionCompanionKind.Metadata ->
            TypeSpec.companionObjectBuilder("Metadata")
                .apply { appendMetadataCompanionMembers(this, plan) }
                .build()

        KotlinProjectionCompanionKind.ActivationFactory ->
            TypeSpec.objectBuilder("ActivationFactory")
                .addProperty(
                    PropertySpec.builder("RUNTIME_CLASS", String::class)
                        .addModifiers(KModifier.CONST)
                        .initializer("%S", plan.type.qualifiedName)
                        .build(),
                )
                .apply {
                    plan.activatableFactoryInterfaceName?.let { interfaceName ->
                        addProperty(
                            PropertySpec.builder("FACTORY_INTERFACE", String::class)
                                .addModifiers(KModifier.CONST)
                                .initializer("%S", interfaceName)
                                .build(),
                        )
                    }
                    plan.activatableFactoryInterfaceIid?.let { iid ->
                        addProperty(
                            PropertySpec.builder("FACTORY_INTERFACE_IID", GUID_CLASS_NAME)
                                .initializer("%T(%S)", GUID_CLASS_NAME, iid.toString())
                                .build(),
                        )
                    }
                }
                .addFunction(
                    FunSpec.builder("acquire")
                        .returns(IUNKNOWN_REFERENCE_CLASS_NAME)
                        .addCode(
                            CodeBlock.of(
                                "return %T.get(RUNTIME_CLASS%L)\n",
                                ACTIVATION_FACTORY_CLASS_NAME,
                                if (plan.activatableFactoryInterfaceIid != null) ", FACTORY_INTERFACE_IID" else "",
                            ),
                        )
                        .build(),
                )
                .addFunction(
                    FunSpec.builder("activate")
                        .returns(IINSPECTABLE_REFERENCE_CLASS_NAME)
                        .addCode(
                            CodeBlock.of(
                                "return %T.activateInstance(RUNTIME_CLASS)\n",
                                ACTIVATION_FACTORY_CLASS_NAME,
                            ),
                        )
                        .build(),
                )
                .build()

        KotlinProjectionCompanionKind.StaticInterfaces ->
            TypeSpec.objectBuilder("StaticInterfaces")
                .apply {
                    plan.staticInterfaceBindings.forEach { binding ->
                        val interfaceConstantName = binding.qualifiedName.substringAfterLast('.').uppercase()
                        val ownerAccessorName = binding.qualifiedName.substringAfterLast('.').replaceFirstChar(Char::lowercase)
                        val ownerCachePropertyName = "_$ownerAccessorName"
                        addProperty(
                            PropertySpec.builder(interfaceConstantName, String::class)
                                .addModifiers(KModifier.CONST)
                                .initializer("%S", binding.qualifiedName)
                                .build(),
                        )
                        binding.iid?.let { iid ->
                            addProperty(
                                PropertySpec.builder("${interfaceConstantName}_IID", GUID_CLASS_NAME)
                                    .initializer("%T(%S)", GUID_CLASS_NAME, iid.toString())
                                    .build(),
                            )
                            addProperty(
                                PropertySpec.builder(ownerCachePropertyName, IUNKNOWN_REFERENCE_CLASS_NAME)
                                    .addModifiers(KModifier.PRIVATE)
                                    .delegate(
                                        CodeBlock.of(
                                            "lazy(%T.PUBLICATION) { %T.get(Metadata.TYPE_NAME, %L_IID) }",
                                            LAZY_THREAD_SAFETY_MODE_CLASS_NAME,
                                            ACTIVATION_FACTORY_CLASS_NAME,
                                            interfaceConstantName,
                                        ),
                                    )
                                    .build(),
                            )
                        }
                    }
                    plan.staticInterfaceBindings
                        .filter { it.iid != null }
                        .forEach { binding ->
                            val interfaceConstantName = binding.qualifiedName.substringAfterLast('.').uppercase()
                            val ownerAccessorName = binding.qualifiedName.substringAfterLast('.').replaceFirstChar(Char::lowercase)
                            addFunction(
                                FunSpec.builder(ownerAccessorName)
                                    .returns(IUNKNOWN_REFERENCE_CLASS_NAME)
                                    .addCode(
                                        CodeBlock.of(
                                            "return _%L\n",
                                            ownerAccessorName,
                                        ),
                                    )
                                    .build(),
                            )
                        }
                }
                .build()

        KotlinProjectionCompanionKind.ComposableFactory ->
            TypeSpec.objectBuilder("ComposableFactory")
                .apply {
                    plan.defaultInterfaceName?.let { interfaceName ->
                        addProperty(
                            PropertySpec.builder("DEFAULT_INTERFACE", String::class)
                                .addModifiers(KModifier.CONST)
                                .initializer("%S", interfaceName)
                                .build(),
                        )
                    }
                    plan.defaultInterfaceIid?.let { iid ->
                        addProperty(
                            PropertySpec.builder("DEFAULT_INTERFACE_IID", GUID_CLASS_NAME)
                                .initializer("%T(%S)", GUID_CLASS_NAME, iid.toString())
                                .build(),
                        )
                    }
                    plan.composableFactoryInterfaceName?.let { interfaceName ->
                        addProperty(
                            PropertySpec.builder("FACTORY_INTERFACE", String::class)
                                .addModifiers(KModifier.CONST)
                                .initializer("%S", interfaceName)
                                .build(),
                        )
                    }
                    plan.composableFactoryInterfaceIid?.let { iid ->
                        addProperty(
                            PropertySpec.builder("FACTORY_INTERFACE_IID", GUID_CLASS_NAME)
                                .initializer("%T(%S)", GUID_CLASS_NAME, iid.toString())
                                .build(),
                        )
                    }
                    if (hasDefaultComposableFactoryConstructor(plan)) {
                        addFunction(renderDefaultComposableFactoryCreateInstance(plan))
                    }
                }
                .addFunction(
                    FunSpec.builder("acquire")
                        .returns(IUNKNOWN_REFERENCE_CLASS_NAME)
                        .addCode(
                            CodeBlock.of(
                                "return %T.get(Metadata.TYPE_NAME%L)\n",
                                ACTIVATION_FACTORY_CLASS_NAME,
                                if (plan.composableFactoryInterfaceIid != null) ", FACTORY_INTERFACE_IID" else "",
                            ),
                        )
                        .build(),
                )
                .build()
    }

    private fun hasDefaultComposableFactoryConstructor(plan: KotlinTypeProjectionPlan): Boolean {
        val factoryName = plan.composableFactoryInterfaceName ?: return false
        val factoryType = plan.typesByQualifiedName[factoryName] ?: return false
        return factoryType.methods.any { method ->
            method.name == "CreateInstance" &&
                method.parameters.size == 2 &&
                method.parameters[0].type.typeName == "System.Object" &&
                method.parameters[1].type.typeName == "System.Object" &&
                method.returnType.typeName == plan.type.qualifiedName
        }
    }

    private fun renderDefaultComposableFactoryCreateInstance(plan: KotlinTypeProjectionPlan): FunSpec {
        val factoryType = resolveTypeName(requireNotNull(plan.composableFactoryInterfaceName))
        return FunSpec.builder("createInstance")
            .returns(IINSPECTABLE_REFERENCE_CLASS_NAME)
            .addCode(
                CodeBlock.builder()
                    .add("val __factory = acquire()\n")
                    .add("%T.confinedScope().use { __scope ->\n", PLATFORM_ABI_CLASS_NAME)
                    .indent()
                    .add("val __innerOut = %T.allocatePointerSlot(__scope)\n", PLATFORM_ABI_CLASS_NAME)
                    .add("val __resultOut = %T.allocatePointerSlot(__scope)\n", PLATFORM_ABI_CLASS_NAME)
                    .add(
                        "val __hr = %T.invokeArgs(instance = __factory.pointer, slot = %T.Metadata.CREATEINSTANCE_SLOT, arg0 = %T.nullPointer, arg1 = __innerOut, arg2 = __resultOut)\n",
                        COM_VTABLE_INVOKER_CLASS_NAME,
                        factoryType,
                        PLATFORM_ABI_CLASS_NAME,
                    )
                    .add("%T(__hr).requireSuccess()\n", HRESULT_CLASS_NAME)
                    .add("val __inner = %T.readPointer(__innerOut)\n", PLATFORM_ABI_CLASS_NAME)
                    .add("if (__inner != %T.nullPointer) {\n", PLATFORM_ABI_CLASS_NAME)
                    .indent()
                    .add("%T(%T.toRawComPtr(__inner)).close()\n", IUNKNOWN_REFERENCE_CLASS_NAME, PLATFORM_ABI_CLASS_NAME)
                    .unindent()
                    .add("}\n")
                    .add("return %T(%T.toRawComPtr(%T.readPointer(__resultOut))).use { it.asInspectable() }\n", IUNKNOWN_REFERENCE_CLASS_NAME, PLATFORM_ABI_CLASS_NAME, PLATFORM_ABI_CLASS_NAME)
                    .unindent()
                    .add("}\n")
                    .build(),
            )
            .build()
    }

    private fun appendMetadataCompanionMembers(
        builder: TypeSpec.Builder,
        plan: KotlinTypeProjectionPlan,
    ) {
        val projectedClassName = ClassName(plan.packageName, plan.type.name)
        builder.addProperty(
            PropertySpec.builder("TYPE_NAME", String::class)
                .addModifiers(KModifier.CONST)
                .initializer("%S", plan.type.qualifiedName)
                .build(),
        )
        appendDescriptorHandoffCompanionMembers(builder, plan)
        plan.interfaceIid?.let { iid ->
            builder.addProperty(
                PropertySpec.builder("IID", GUID_CLASS_NAME)
                    .initializer("%T(%S)", GUID_CLASS_NAME, iid.toString())
                    .build(),
            )
        }
        if (plan.declarationKind == KotlinProjectionDeclarationKind.Interface && canRenderInterfaceProxy(plan)) {
            builder.addFunction(
                FunSpec.builder("wrap")
                    .addModifiers(KModifier.INTERNAL)
                    .addParameter("instance", IUNKNOWN_REFERENCE_CLASS_NAME)
                    .returns(projectedClassName)
                    .addCode(
                        CodeBlock.builder()
                            .add("return object : %T, %T {\n", projectedClassName, IWINRT_OBJECT_CLASS_NAME)
                            .indent()
                            .add(
                                "override val nativeObject: %T\n",
                                COM_OBJECT_REFERENCE_CLASS_NAME,
                            )
                            .indent()
                            .add("get() = instance\n")
                            .unindent()
                            .apply {
                                plan.type.methods.forEach { method ->
                                    add("%L\n", renderInterfaceProxyMethod(method).toBuilder().build())
                                }
                            }
                            .unindent()
                            .add("}\n")
                            .build(),
                    )
                    .build(),
            )
        }
        if (plan.declarationKind == KotlinProjectionDeclarationKind.Class &&
            KotlinProjectionSpecializationKind.StaticClass !in plan.specializationKinds &&
            KotlinProjectionSpecializationKind.AttributeClass !in plan.specializationKinds) {
            builder.addFunction(
                FunSpec.builder("acquireInterface")
                    .addModifiers(KModifier.INTERNAL)
                    .addParameter("instance", IINSPECTABLE_REFERENCE_CLASS_NAME)
                    .addParameter("iid", GUID_CLASS_NAME)
                    .returns(IUNKNOWN_REFERENCE_CLASS_NAME)
                    .addCode(
                        "return instance.queryInterface(iid).getOrThrow().use { %T(it.getRefPointer(), iid) }\n",
                        IUNKNOWN_REFERENCE_CLASS_NAME,
                    )
                    .build(),
            )
        }
        plan.defaultInterfaceName?.let { interfaceName ->
            builder.addProperty(
                PropertySpec.builder("DEFAULT_INTERFACE", String::class)
                    .addModifiers(KModifier.CONST)
                    .initializer("%S", interfaceName)
                    .build(),
            )
        }
        plan.defaultInterfaceIid?.let { iid ->
            builder.addProperty(
                PropertySpec.builder("DEFAULT_INTERFACE_IID", GUID_CLASS_NAME)
                    .initializer("%T(%S)", GUID_CLASS_NAME, iid.toString())
                    .build(),
            )
            builder.addFunction(
                FunSpec.builder("acquireDefaultInterface")
                    .addParameter("instance", IINSPECTABLE_REFERENCE_CLASS_NAME)
                    .returns(IUNKNOWN_REFERENCE_CLASS_NAME)
                    .addCode(
                        CodeBlock.of("return acquireInterface(instance, DEFAULT_INTERFACE_IID)\n"),
                    )
                    .build(),
            )
        }
        if (plan.declarationKind == KotlinProjectionDeclarationKind.Class &&
            KotlinProjectionSpecializationKind.StaticClass !in plan.specializationKinds &&
            KotlinProjectionSpecializationKind.AttributeClass !in plan.specializationKinds) {
            builder.addFunction(
                FunSpec.builder("wrap")
                    .addModifiers(KModifier.INTERNAL)
                    .addParameter("instance", IINSPECTABLE_REFERENCE_CLASS_NAME)
                    .returns(projectedClassName)
                    .addCode("return %T(instance)\n", projectedClassName)
                    .build(),
            )
        }
        plan.type.methods.forEach { method ->
            method.methodRowId?.let { rowId ->
                builder.addProperty(
                    PropertySpec.builder(method.methodRowConstantName(plan.type.methods), Int::class)
                        .addModifiers(KModifier.INTERNAL, KModifier.CONST)
                        .initializer("%L", rowId)
                        .build(),
                )
            }
        }
        plan.type.properties.forEach { property ->
            property.getterMethodRowId?.let { rowId ->
                builder.addProperty(
                    PropertySpec.builder("${property.name.uppercase()}_GETTER_METHOD_ROW_ID", Int::class)
                        .addModifiers(KModifier.INTERNAL, KModifier.CONST)
                        .initializer("%L", rowId)
                        .build(),
                )
            }
            property.setterMethodRowId?.let { rowId ->
                builder.addProperty(
                    PropertySpec.builder("${property.name.uppercase()}_SETTER_METHOD_ROW_ID", Int::class)
                        .addModifiers(KModifier.INTERNAL, KModifier.CONST)
                        .initializer("%L", rowId)
                        .build(),
                )
            }
        }
        plan.type.events.forEach { event ->
            event.addMethodRowId?.let { rowId ->
                builder.addProperty(
                    PropertySpec.builder("${event.name.uppercase()}_ADD_METHOD_ROW_ID", Int::class)
                        .addModifiers(KModifier.INTERNAL, KModifier.CONST)
                        .initializer("%L", rowId)
                        .build(),
                )
            }
            event.removeMethodRowId?.let { rowId ->
                builder.addProperty(
                    PropertySpec.builder("${event.name.uppercase()}_REMOVE_METHOD_ROW_ID", Int::class)
                        .addModifiers(KModifier.INTERNAL, KModifier.CONST)
                        .initializer("%L", rowId)
                        .build(),
                )
            }
        }
        plan.abiSlotBindings.forEach { binding ->
            builder.addProperty(
                PropertySpec.builder(binding.constantName, Int::class)
                    .addModifiers(KModifier.INTERNAL, KModifier.CONST)
                    .initializer("%L", binding.slot)
                    .build(),
            )
        }
        plan.instanceMemberBindings.forEach { binding ->
            builder.addProperty(
                PropertySpec.builder("${binding.bindingName}_OWNER_INTERFACE", String::class)
                    .addModifiers(KModifier.INTERNAL, KModifier.CONST)
                    .initializer("%S", binding.ownerInterfaceQualifiedName)
                    .build(),
            )
            builder.addProperty(
                PropertySpec.builder("${binding.bindingName}_OWNER_CACHE", String::class)
                    .addModifiers(KModifier.INTERNAL, KModifier.CONST)
                    .initializer("%S", binding.ownerCachePropertyName)
                    .build(),
            )
            builder.addProperty(
                PropertySpec.builder(binding.bindingName, Int::class)
                    .addModifiers(KModifier.INTERNAL)
                    .initializer("%T.Metadata.%L", resolveTypeName(binding.slotInterfaceQualifiedName), binding.slotConstantName)
                    .build(),
            )
        }
        plan.staticMemberBindings.forEach { binding ->
            builder.addProperty(
                PropertySpec.builder("${binding.bindingName}_OWNER_INTERFACE", String::class)
                    .addModifiers(KModifier.INTERNAL, KModifier.CONST)
                    .initializer("%S", binding.ownerInterfaceQualifiedName)
                    .build(),
            )
            builder.addProperty(
                PropertySpec.builder("${binding.bindingName}_OWNER_ACCESSOR", String::class)
                    .addModifiers(KModifier.INTERNAL, KModifier.CONST)
                    .initializer("%S", binding.ownerAccessorName)
                    .build(),
            )
            builder.addProperty(
                PropertySpec.builder("${binding.bindingName}_OWNER_CACHE", String::class)
                    .addModifiers(KModifier.INTERNAL, KModifier.CONST)
                    .initializer("%S", binding.ownerCachePropertyName)
                    .build(),
            )
            builder.addProperty(
                PropertySpec.builder(binding.bindingName, Int::class)
                    .addModifiers(KModifier.INTERNAL)
                    .initializer("%T.Metadata.%L", resolveTypeName(binding.slotInterfaceQualifiedName), binding.slotConstantName)
                    .build(),
            )
        }
    }

    private fun appendDescriptorHandoffCompanionMembers(
        builder: TypeSpec.Builder,
        plan: KotlinTypeProjectionPlan,
    ) {
        val declaration = plan.typeDeclarationDescriptor
        builder.addProperty(
            PropertySpec.builder("WRITES_ABI_DECLARATION", Boolean::class)
                .addModifiers(KModifier.INTERNAL, KModifier.CONST)
                .initializer("%L", declaration.writesAbiDeclaration)
                .build(),
        )
        builder.addProperty(
            PropertySpec.builder("WRITES_WRAPPER_DECLARATION", Boolean::class)
                .addModifiers(KModifier.INTERNAL, KModifier.CONST)
                .initializer("%L", declaration.writesWrapperDeclaration)
                .build(),
        )
        builder.addProperty(
            PropertySpec.builder("WRITES_HELPER_CLASS", Boolean::class)
                .addModifiers(KModifier.INTERNAL, KModifier.CONST)
                .initializer("%L", declaration.writesHelperClass)
                .build(),
        )
        plan.factorySurfaceDescriptor?.let { descriptor ->
            builder.addProperty(
                PropertySpec.builder("FACTORY_CACHE_NAME", String::class)
                    .addModifiers(KModifier.INTERNAL, KModifier.CONST)
                    .initializer("%S", descriptor.activationFactoryCacheName)
                    .build(),
            )
            builder.addStringListProperty("FACTORY_STATIC_TARGETS", descriptor.staticMemberTargets)
            builder.addStringListProperty("FACTORY_CONSTRUCTOR_TARGETS", descriptor.constructorFactories)
            builder.addStringListProperty("FACTORY_COMPOSABLE_TARGETS", descriptor.composableFactories)
        }
        plan.objectReferenceSurfaceDescriptor?.let { descriptor ->
            builder.addStringListProperty("OBJECT_REFERENCE_NAMES", descriptor.objectReferenceNames)
            builder.addStringListProperty("OBJECT_REFERENCE_METADATA_NAMES", descriptor.exposedTypeMetadataNames)
        }
        plan.guidSignatureDescriptor?.let { descriptor ->
            builder.addProperty(
                PropertySpec.builder("GUID_SIGNATURE_FRAGMENT", String::class)
                    .addModifiers(KModifier.INTERNAL, KModifier.CONST)
                    .initializer("%S", descriptor.signatureFragment)
                    .build(),
            )
        }
        plan.interfaceMemberSignatureSetDescriptor?.let { descriptor ->
            builder.addStringListProperty(
                "INTERFACE_METHOD_SIGNATURES",
                descriptor.methodSignatures.map { signature ->
                    "${signature.methodName}:${signature.returnTypeName}(${signature.parameterTypeNames.joinToString(",")})"
                },
            )
            builder.addStringListProperty("INTERFACE_PROPERTY_NAMES", descriptor.propertyNames)
            builder.addStringListProperty("INTERFACE_EVENT_NAMES", descriptor.eventNames)
        }
        plan.customMappedMemberOutputDescriptor?.let { descriptor ->
            builder.addStringListProperty("CUSTOM_MAPPED_MEMBER_PLANS", descriptor.memberPlans)
        }
        plan.genericAbiClassInitializationDescriptor?.let { descriptor ->
            builder.addStringListProperty("GENERIC_ABI_INVOKE_SLOTS", descriptor.invokeSlotNames)
            builder.addStringListProperty("GENERIC_ABI_TYPE_ARRAYS", descriptor.genericTypeArrayDependencies)
        }
        plan.requiredInterfaceAugmentationDescriptor?.let { descriptor ->
            builder.addStringListProperty("REQUIRED_INTERFACE_NAMES", descriptor.requiredInterfaceNames)
            builder.addStringListProperty("REQUIRED_EXPLICIT_FORWARD_MEMBERS", descriptor.explicitForwardMemberNames)
            builder.addStringListProperty("REQUIRED_MAPPED_AUGMENTATION_MEMBERS", descriptor.mappedAugmentationMembers)
        }
        plan.moduleActivationAndAuthoringDescriptor?.let { module ->
            builder.addStringListProperty("MODULE_FACTORY_MEMBERS", module.factoryMemberNames)
            builder.addStringListProperty("MODULE_ACTIVATION_FACTORY_ENTRIES", module.moduleActivationFactoryEntries)
        }
    }

    private fun resolveTypeName(typeName: String): TypeName {
        val trimmed = typeName.trim()
        val genericStart = trimmed.indexOf('<')
        if (genericStart >= 0 && trimmed.endsWith('>')) {
            val rawType = trimmed.substring(0, genericStart)
            val arguments = splitGenericArguments(trimmed.substring(genericStart + 1, trimmed.length - 1))
                .map(::resolveTypeName)
            if (rawType == "Array") {
                return Array::class.asClassName().parameterizedBy(arguments)
            }
            mappedTypeByAbiName(rawType)?.let { mappedType ->
                return mappedType.projectedTypeResolver(arguments)
            }
            val rawClassName = if ('.' in rawType) projectionClassName(rawType) else ClassName.bestGuess(rawType)
            return rawClassName.parameterizedBy(arguments)
        }

        mappedTypeByAbiName(trimmed)?.let { mappedType ->
            return mappedType.projectedTypeResolver(emptyList())
        }
        if ((trimmed.startsWith("T") || trimmed.startsWith("M")) && trimmed.drop(1).toIntOrNull() != null) {
            return TypeVariableName(trimmed)
        }

        return when (trimmed) {
            "Unit" -> UNIT
            "Any",
            "System.Object" -> IINSPECTABLE_REFERENCE_CLASS_NAME
            "String" -> String::class.asClassName()
            "Int" -> Int::class.asClassName()
            "UInt" -> UInt::class.asClassName()
            "Boolean" -> Boolean::class.asClassName()
            "Byte" -> Byte::class.asClassName()
            "SByte",
            "Int8" -> Byte::class.asClassName()
            "UInt8" -> UByte::class.asClassName()
            "Short" -> Short::class.asClassName()
            "Int16" -> Short::class.asClassName()
            "UShort" -> UShort::class.asClassName()
            "UInt16" -> UShort::class.asClassName()
            "Long" -> Long::class.asClassName()
            "Int64" -> Long::class.asClassName()
            "ULong",
            "UInt64" -> ULong::class.asClassName()
            "Float" -> Float::class.asClassName()
            "Double" -> Double::class.asClassName()
            "Char" -> Char::class.asClassName()
            "Guid",
            "System.Guid" -> GUID_CLASS_NAME
            IUNKNOWN_REFERENCE_CLASS_NAME.simpleName,
            "io.github.kitectlab.winrt.runtime.IUnknownReference" -> IUNKNOWN_REFERENCE_CLASS_NAME
            IINSPECTABLE_REFERENCE_CLASS_NAME.simpleName,
            "io.github.kitectlab.winrt.runtime.IInspectableReference" -> IINSPECTABLE_REFERENCE_CLASS_NAME
            IWINRT_OBJECT_CLASS_NAME.simpleName,
            "io.github.kitectlab.winrt.runtime.IWinRTObject" -> IWINRT_OBJECT_CLASS_NAME
            else -> if ('.' in trimmed) projectionClassName(trimmed) else ClassName.bestGuess(trimmed)
        }
    }

    private fun resolveIntegralTypeName(type: WinRtIntegralType): TypeName =
        integralAbiDescriptor(type).kotlinTypeName

    private fun integralLiteral(valueBits: ULong, type: WinRtIntegralType): CodeBlock =
        integralAbiDescriptor(type).literalRenderer(valueBits)

    private fun splitGenericArguments(arguments: String): List<String> {
        if (arguments.isBlank()) {
            return emptyList()
        }
        val result = mutableListOf<String>()
        var depth = 0
        var start = 0
        arguments.forEachIndexed { index, character ->
            when (character) {
                '<' -> depth += 1
                '>' -> depth -= 1
                ',' -> if (depth == 0) {
                    result += arguments.substring(start, index).trim()
                    start = index + 1
                }
            }
        }
        result += arguments.substring(start).trim()
        return result.filter(String::isNotEmpty)
    }

    private fun projectionClassName(qualifiedName: String?): ClassName {
        require(!qualifiedName.isNullOrBlank()) {
            "Projection class name requires a non-blank qualified name."
        }
        val trimmed = qualifiedName.trim()
        val lastDot = trimmed.lastIndexOf('.')
        if (lastDot < 0) {
            return ClassName("", trimmed)
        }
        val namespace = trimmed.substring(0, lastDot)
        val simpleName = trimmed.substring(lastDot + 1)
        val packageName = (ROOT_PACKAGE_SEGMENTS + namespace.split('.').filter { it.isNotBlank() }.map { it.lowercase() })
            .joinToString(".")
        return ClassName(packageName, simpleName)
    }

}
