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
import io.github.kitectlab.winrt.metadata.WinRtParameterDefinition
import io.github.kitectlab.winrt.metadata.WinRtPropertyDefinition
import io.github.kitectlab.winrt.metadata.WinRtProjectedAttributeDescriptor
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

internal fun KotlinProjectionRenderer.renderBoundEventFunctions(
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
        modifiers = if (override) runtimeClassMemberModifiers(plan, addBinding) else emptyList(),
        projectedAttributes = addBinding.projectedAttributes,
    )
}

internal fun KotlinProjectionRenderer.renderBoundStaticEventFunctions(
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
        modifiers = emptyList(),
        projectedAttributes = addBinding.projectedAttributes,
    )
}

internal fun KotlinProjectionRenderer.renderStaticEventSourceFunctions(
    event: WinRtEventDefinition,
    eventInvokeDescriptor: WinRtEventInvokeDescriptor?,
): List<FunSpec> {
    val typeName = resolveTypeName(eventInvokeDescriptor?.delegateTypeName ?: event.delegateTypeName)
    val propertyName = event.name.replaceFirstChar(Char::lowercase)
    return listOf(
        FunSpec.builder("add${event.name}")
            .addParameter("handler", typeName)
            .returns(EVENT_REGISTRATION_TOKEN_CLASS_NAME)
            .addCode("return %L.add(handler)\n", propertyName)
            .build(),
        FunSpec.builder("remove${event.name}")
            .addParameter("token", EVENT_REGISTRATION_TOKEN_CLASS_NAME)
            .addCode("%L.remove(token)\n", propertyName)
            .build(),
    )
}

internal fun KotlinProjectionRenderer.buildBoundEventFunctions(
    event: WinRtEventDefinition,
    eventInvokeDescriptor: WinRtEventInvokeDescriptor?,
    addInvocation: CodeBlock,
    removeInvocation: CodeBlock,
    modifiers: List<KModifier>,
    projectedAttributes: List<WinRtProjectedAttributeDescriptor> = emptyList(),
): List<FunSpec> {
    val typeName = resolveTypeName(eventInvokeDescriptor?.delegateTypeName ?: event.delegateTypeName)
    return listOf(
        FunSpec.builder("add${event.name}")
            .addProjectedAttributeAnnotations(projectedAttributes)
            .addModifiers(modifiers)
            .addParameter("handler", typeName)
            .returns(EVENT_REGISTRATION_TOKEN_CLASS_NAME)
            .addCode("%L\n", addInvocation)
            .build(),
        FunSpec.builder("remove${event.name}")
            .addProjectedAttributeAnnotations(projectedAttributes)
            .addModifiers(modifiers)
            .addParameter("token", EVENT_REGISTRATION_TOKEN_CLASS_NAME)
            .addCode("%L\n", removeInvocation)
            .build(),
    )
}

internal fun KotlinProjectionRenderer.renderEventProperty(
    event: WinRtEventDefinition,
    eventInvokeDescriptor: WinRtEventInvokeDescriptor?,
    abstract: Boolean,
    override: Boolean = false,
    modifiers: List<KModifier> = if (override) listOf(KModifier.OVERRIDE) else emptyList(),
    eventSourceOwnerTypeName: String? = null,
    eventSourceObjectReference: CodeBlock? = null,
    eventSourceAddSlot: CodeBlock? = null,
    fallbackToAddRemove: Boolean = true,
): PropertySpec {
    val typeName = resolveTypeName(eventInvokeDescriptor?.delegateTypeName ?: event.delegateTypeName)
    val builder = PropertySpec.builder(
        event.name.replaceFirstChar(Char::lowercase),
        WINRT_EVENT_CLASS_NAME.parameterizedBy(typeName),
    )
    if (abstract) {
        return builder.addModifiers(KModifier.ABSTRACT).build()
    }
    builder.addModifiers(modifiers)
    if (eventSourceOwnerTypeName != null && eventSourceObjectReference != null && eventSourceAddSlot != null) {
        return builder
            .delegate(
                CodeBlock.builder()
                    .add("lazy(%T.PUBLICATION) {\n", LAZY_THREAD_SAFETY_MODE_CLASS_NAME)
                    .indent()
                    .addStatement(
                        "val __eventSource = %T.createEventSource(%S, %S, %L, %L) as? %T",
                        WINRT_EVENT_PROJECTION_HELPERS_CLASS_NAME,
                        eventInvokeDescriptor?.delegateTypeName ?: event.delegateTypeName,
                        eventSourceOwnerTypeName,
                        eventSourceObjectReference,
                        eventSourceAddSlot,
                        WINRT_EVENT_SOURCE_CLASS_NAME.parameterizedBy(typeName),
                    )
                    .apply {
                        if (fallbackToAddRemove) {
                            addStatement(
                                "__eventSource?.let { %T(it) } ?: %T(::add%L, ::remove%L)",
                                WINRT_EVENT_CLASS_NAME,
                                WINRT_EVENT_CLASS_NAME,
                                event.name,
                                event.name,
                            )
                        } else {
                            addStatement(
                                "__eventSource?.let { %T(it) } ?: error(%S)",
                                WINRT_EVENT_CLASS_NAME,
                                "Event source ${event.name} is not registered.",
                            )
                        }
                    }
                    .unindent()
                    .add("}")
                    .build(),
            )
            .build()
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

internal fun KotlinProjectionRenderer.renderEventFunctions(
    event: WinRtEventDefinition,
    abstract: Boolean,
    override: Boolean = false,
    modifiers: List<KModifier> = if (override) listOf(KModifier.OVERRIDE) else emptyList(),
): List<FunSpec> {
    val typeName = resolveTypeName(event.delegateTypeName)
    return listOf(
        FunSpec.builder("add${event.name}")
            .addParameter("handler", typeName)
            .apply {
                if (abstract) {
                    addModifiers(KModifier.ABSTRACT)
                } else {
                    addModifiers(modifiers)
                    addCode("return %L\n", missingAbiBindingError("event ${event.name} add"))
                }
            }
            .returns(EVENT_REGISTRATION_TOKEN_CLASS_NAME)
            .build(),
        FunSpec.builder("remove${event.name}")
            .addParameter("token", EVENT_REGISTRATION_TOKEN_CLASS_NAME)
            .apply {
                if (abstract) {
                    addModifiers(KModifier.ABSTRACT)
                } else {
                    addModifiers(modifiers)
                    addCode("%L\n", missingAbiBindingError("event ${event.name} remove"))
                }
            }
            .build(),
    )
}

internal fun KotlinProjectionRenderer.buildMetadataCompanionShell(
    plan: KotlinTypeProjectionPlan,
    staticMethods: List<WinRtMethodDefinition>,
    staticProperties: List<WinRtPropertyDefinition>,
    staticEvents: List<WinRtEventDefinition>,
): TypeSpec =
    TypeSpec.companionObjectBuilder("Metadata")
        .apply {
            val projectedStaticMethods = mergedStaticMethods(plan, staticMethods)
            val projectedStaticProperties = mergedStaticProperties(plan, staticProperties)
            val projectedStaticEvents = mergedStaticEvents(plan, staticEvents)
            appendMetadataCompanionMembers(this, plan)
            projectedStaticMethods.forEach { addFunction(renderBoundStaticMethod(plan, it) ?: renderStubMethod(it)) }
            projectedStaticProperties.forEach { addProperty(renderBoundStaticProperty(plan, it) ?: renderStubProperty(it)) }
            projectedStaticEvents.forEach { event ->
                val eventInvokeDescriptor = plan.eventInvokeDescriptors.firstOrNull { it.eventName == event.name && it.isStatic }
                val addBinding = plan.staticMemberBindings.firstOrNull {
                    it.bindingName == "STATIC_${event.name.uppercase()}_ADD_SLOT"
                }
                addProperty(
                    renderEventProperty(
                        event = event,
                        eventInvokeDescriptor = eventInvokeDescriptor,
                        abstract = false,
                        eventSourceOwnerTypeName = addBinding?.ownerInterfaceQualifiedName,
                        eventSourceObjectReference = addBinding?.let { CodeBlock.of("StaticInterfaces.%L()", it.ownerAccessorName) },
                        eventSourceAddSlot = addBinding?.let { CodeBlock.of("%L", it.bindingName) },
                        fallbackToAddRemove = addBinding == null,
                    ),
                )
                (
                    addBinding?.let { renderStaticEventSourceFunctions(event, eventInvokeDescriptor) }
                        ?: renderBoundStaticEventFunctions(plan, event)
                        ?: renderEventFunctions(event, abstract = false)
                    )
                    .forEach(::addFunction)
            }
        }
        .build()

private fun mergedStaticMethods(
    plan: KotlinTypeProjectionPlan,
    staticMethods: List<WinRtMethodDefinition>,
): List<WinRtMethodDefinition> {
    val merged = linkedMapOf<String, WinRtMethodDefinition>()
    staticMethods.forEach { method ->
        merged.putIfAbsent(method.projectionSignatureIgnoringStaticKey(), method.copy(isStatic = true))
    }
    plan.staticInterfaceNames
        .mapNotNull(plan.typesByQualifiedName::get)
        .flatMap(WinRtTypeDefinition::methods)
        .filter(WinRtMethodDefinition::isProjectedCallableMethod)
        .forEach { method ->
            merged.putIfAbsent(method.projectionSignatureIgnoringStaticKey(), method.copy(isStatic = true))
        }
    return merged.values.toList()
}

private fun mergedStaticProperties(
    plan: KotlinTypeProjectionPlan,
    staticProperties: List<WinRtPropertyDefinition>,
): List<WinRtPropertyDefinition> {
    val merged = linkedMapOf<String, WinRtPropertyDefinition>()
    fun add(property: WinRtPropertyDefinition) {
        val staticProperty = property.copy(isStatic = true)
        val key = staticProperty.projectionSignatureIgnoringStaticKey()
        merged[key] = merged[key]?.mergeStaticAccessor(staticProperty) ?: staticProperty
    }
    staticProperties.forEach(::add)
    plan.staticInterfaceNames
        .mapNotNull(plan.typesByQualifiedName::get)
        .flatMap(WinRtTypeDefinition::properties)
        .forEach(::add)
    return merged.values.toList()
}

private fun mergedStaticEvents(
    plan: KotlinTypeProjectionPlan,
    staticEvents: List<WinRtEventDefinition>,
): List<WinRtEventDefinition> {
    val merged = linkedMapOf<String, WinRtEventDefinition>()
    staticEvents.forEach { event ->
        merged.putIfAbsent("${event.name}|${event.delegateTypeName}", event.copy(isStatic = true))
    }
    plan.staticInterfaceNames
        .mapNotNull(plan.typesByQualifiedName::get)
        .flatMap(WinRtTypeDefinition::events)
        .forEach { event ->
            merged.putIfAbsent("${event.name}|${event.delegateTypeName}", event.copy(isStatic = true))
        }
    return merged.values.toList()
}

private fun WinRtPropertyDefinition.mergeStaticAccessor(other: WinRtPropertyDefinition): WinRtPropertyDefinition {
    require(name == other.name && typeName == other.typeName) {
        "Can only merge identical static properties: $name:$typeName vs ${other.name}:${other.typeName}"
    }
    return copy(
        isStatic = true,
        getterMethodName = getterMethodName ?: other.getterMethodName,
        setterMethodName = setterMethodName ?: other.setterMethodName,
        getterMethodRowId = listOfNotNull(getterMethodRowId, other.getterMethodRowId).minOrNull(),
        setterMethodRowId = listOfNotNull(setterMethodRowId, other.setterMethodRowId).minOrNull(),
        hasValidAccessors = hasValidAccessors && other.hasValidAccessors,
    )
}

internal fun KotlinProjectionRenderer.renderBoundStaticMethod(
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
    val binding = plan.staticMemberBindings.firstOrNull {
        it.bindingName == staticMethodBindingName(plan, method)
    } ?: return null
    val invocation = renderBoundStaticInvocation(binding)
    return FunSpec.builder(method.name)
        .addProjectedAttributeAnnotations(binding.projectedAttributes)
        .returns(resolveTypeName(method.returnTypeName))
        .addParameters(method.parameters.map { ParameterSpec.builder(it.name, resolveTypeName(it.typeName)).build() })
        .addCode("%L\n", invocation)
        .build()
}

private fun staticMethodBindingName(
    plan: KotlinTypeProjectionPlan,
    method: WinRtMethodDefinition,
): String {
    val declaringStaticInterface = plan.staticInterfaceNames
        .mapNotNull(plan.typesByQualifiedName::get)
        .firstOrNull { staticInterface ->
            staticInterface.methods.any { it.projectionSignatureIgnoringStaticKey() == method.projectionSignatureIgnoringStaticKey() }
        }
    return "STATIC_${method.abiSlotConstantName(declaringStaticInterface?.methods ?: plan.type.methods)}"
}

internal fun KotlinProjectionRenderer.renderBoundStaticProperty(
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
    builder.addProjectedAttributeAnnotations(getterBinding.projectedAttributes)
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
                .addCode(
                    "%L\n",
                    setterBinding?.let(::renderBoundStaticInvocation)
                        ?: missingAbiBindingError("static property ${property.name} setter"),
                )
                .build(),
        )
    }
    return builder.build()
}

internal fun KotlinProjectionRenderer.appendCompanionShells(
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

internal fun KotlinProjectionRenderer.buildCompanionShell(
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
                renderActivationFactoryCreateFunctions(plan).forEach(::addFunction)
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
                renderComposableFactoryCreateFunctions(plan).forEach(::addFunction)
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

internal fun KotlinProjectionRenderer.renderFactoryConstructors(plan: KotlinTypeProjectionPlan): List<FunSpec> {
    val factoryType = plan.activatableFactoryInterfaceName?.let(plan.typesByQualifiedName::get) ?: return emptyList()
    return factoryType.methods
        .filter(WinRtMethodDefinition::isProjectedCallableMethod)
        .filter { method -> method.returnType.typeName == plan.type.qualifiedName }
        .map { method ->
            FunSpec.constructorBuilder()
                .addParameters(method.parameters.map { parameter -> ParameterSpec.builder(parameter.name, resolveTypeName(parameter.typeName)).build() })
                .callThisConstructor(
                    CodeBlock.of(
                        "ActivationFactory.%L(%L), kotlin.Unit",
                        factoryCreateFunctionName(method),
                        method.parameters.joinToString(", ") { parameter -> parameter.name },
                    ),
                )
                .build()
        }
}

internal fun KotlinProjectionRenderer.renderComposableConstructors(plan: KotlinTypeProjectionPlan): List<FunSpec> {
    val factoryType = plan.composableFactoryInterfaceName?.let(plan.typesByQualifiedName::get) ?: return emptyList()
    return factoryType.methods
        .filter(WinRtMethodDefinition::isProjectedCallableMethod)
        .mapNotNull(::composableUserParameters)
        .map { (method, userParameters) ->
            FunSpec.constructorBuilder()
                .addParameters(userParameters.map { parameter -> ParameterSpec.builder(parameter.name, resolveTypeName(parameter.typeName)).build() })
                .callThisConstructor(
                    CodeBlock.of(
                        "ComposableFactory.%L(%L), kotlin.Unit",
                        factoryCreateFunctionName(method),
                        userParameters.joinToString(", ") { parameter -> parameter.name },
                    ),
                )
                .build()
        }
}

internal fun KotlinProjectionRenderer.renderActivationFactoryCreateFunctions(plan: KotlinTypeProjectionPlan): List<FunSpec> {
    val factoryType = plan.activatableFactoryInterfaceName?.let(plan.typesByQualifiedName::get) ?: return emptyList()
    val factoryClassName = resolveTypeName(factoryType.qualifiedName)
    return factoryType.methods
        .filter(WinRtMethodDefinition::isProjectedCallableMethod)
        .filter { method -> method.returnType.typeName == plan.type.qualifiedName }
        .map { method ->
            val returnBinding = KotlinProjectionAbiTypeBinding(
                kind = KotlinProjectionAbiValueKind.InspectableReference,
                typeName = IINSPECTABLE_REFERENCE_CLASS_NAME.canonicalName,
            )
            val parameterBindings = method.parameters.map { parameter ->
                KotlinProjectionAbiParameterBinding(
                    parameter.name,
                    KotlinProjectionPlanner().classifyAbiTypeBinding(parameter.typeName, factoryType.namespace, plan.typesByQualifiedName),
                )
            }
            val callPlan = requireAbiCallPlan(
                bindingName = "${factoryType.qualifiedName}.${method.name}",
                returnBinding = returnBinding,
                parameterBindings = parameterBindings,
                suppressHResultCheck = method.isNoException,
            )
            FunSpec.builder(factoryCreateFunctionName(method))
                .addModifiers(KModifier.INTERNAL)
                .addParameters(method.parameters.map { parameter -> ParameterSpec.builder(parameter.name, resolveTypeName(parameter.typeName)).build() })
                .returns(IINSPECTABLE_REFERENCE_CLASS_NAME)
                .addCode(
                    "%L\n",
                    renderInlineAbiInvocation(
                        invokeTargetExpression = "acquire()",
                        slotExpression = CodeBlock.of("%T.Metadata.%L", factoryClassName, method.abiSlotConstantName(factoryType.methods)),
                        callPlan = callPlan,
                    ) ?: error("Generator ABI marshaler parity failed to emit factory ${factoryType.qualifiedName}.${method.name}"),
                )
                .build()
        }
}

internal fun KotlinProjectionRenderer.renderComposableFactoryCreateFunctions(plan: KotlinTypeProjectionPlan): List<FunSpec> {
    val factoryType = plan.composableFactoryInterfaceName?.let(plan.typesByQualifiedName::get) ?: return emptyList()
    val factoryClassName = resolveTypeName(factoryType.qualifiedName)
    return factoryType.methods
        .filter(WinRtMethodDefinition::isProjectedCallableMethod)
        .mapNotNull(::composableUserParameters)
        .map { (method, userParameters) ->
            FunSpec.builder(factoryCreateFunctionName(method))
                .addModifiers(KModifier.INTERNAL)
                .addParameters(userParameters.map { parameter -> ParameterSpec.builder(parameter.name, resolveTypeName(parameter.typeName)).build() })
                .returns(IINSPECTABLE_REFERENCE_CLASS_NAME)
                .addCode(
                    "%L\n",
                    renderComposableFactoryInvocation(plan, factoryType, factoryClassName, method, userParameters),
                )
                .build()
        }
}

private fun KotlinProjectionRenderer.renderComposableFactoryInvocation(
    plan: KotlinTypeProjectionPlan,
    factoryType: WinRtTypeDefinition,
    factoryClassName: TypeName,
    method: WinRtMethodDefinition,
    userParameters: List<WinRtParameterDefinition>,
): CodeBlock {
    val parameterBindings = userParameters.map { parameter ->
        KotlinProjectionAbiParameterBinding(
            parameter.name,
            KotlinProjectionPlanner().classifyAbiTypeBinding(parameter.typeName, factoryType.namespace, plan.typesByQualifiedName),
        )
    }
    val callPlan = requireAbiCallPlan(
        bindingName = "${factoryType.qualifiedName}.${method.name}",
        returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
        parameterBindings = parameterBindings,
        suppressHResultCheck = method.isNoException,
    )
    val code = CodeBlock.builder()
    val scopedParameterOpeners = callPlan.parameterMarshalers.flatMap { it.scopeOpeners }
    scopedParameterOpeners.forEach { opener ->
        code.add("%L\n", opener)
        code.indent()
    }
    code.add("val __factory = acquire()\n")
    code.add("%T.confinedScope().use { __scope ->\n", PLATFORM_ABI_CLASS_NAME)
    code.indent()
    code.add("val __innerOut = %T.allocatePointerSlot(__scope)\n", PLATFORM_ABI_CLASS_NAME)
    code.add("val __resultOut = %T.allocatePointerSlot(__scope)\n", PLATFORM_ABI_CLASS_NAME)
    val abiArguments = callPlan.parameterMarshalers.flatMap { marshaler ->
        listOf(KotlinProjectionComArgument(marshaler.abiArgumentExpression, marshaler.abiArgumentKind)) +
            marshaler.extraAbiArgumentExpressions.mapIndexed { index, expression ->
                KotlinProjectionComArgument(expression, marshaler.extraAbiArgumentKinds.getOrNull(index))
            }
    } + listOf(
        KotlinProjectionComArgument(CodeBlock.of("%T.nullPointer", PLATFORM_ABI_CLASS_NAME), KotlinProjectionComArgumentKind.Pointer),
        KotlinProjectionComArgument(CodeBlock.of("__innerOut"), KotlinProjectionComArgumentKind.Pointer),
        KotlinProjectionComArgument(CodeBlock.of("__resultOut"), KotlinProjectionComArgumentKind.Pointer),
    )
    val finallyStatements = callPlan.parameterMarshalers.flatMap { it.finallyStatements }
    if (finallyStatements.isNotEmpty()) {
        code.add("try {\n")
        code.indent()
    }
    code.add("val __hr = ")
    code.add(
        renderComVtableInvocation(
            invokeTargetExpression = "__factory",
            slotExpression = CodeBlock.of("%T.Metadata.%L", factoryClassName, method.abiSlotConstantName(factoryType.methods)),
            abiArguments = abiArguments,
        ),
    )
    code.add("\n")
    if (!callPlan.suppressHResultCheck) {
        code.add("%T(__hr).requireSuccess()\n", HRESULT_CLASS_NAME)
    }
    callPlan.parameterMarshalers.flatMap { it.postCallStatements }.forEach { postCallStatement ->
        code.add("%L\n", postCallStatement)
    }
    code.add("val __inner = %T.readPointer(__innerOut)\n", PLATFORM_ABI_CLASS_NAME)
    code.add("if (__inner != %T.nullPointer) {\n", PLATFORM_ABI_CLASS_NAME)
    code.indent()
    code.add("%T(%T.toRawComPtr(__inner)).close()\n", IUNKNOWN_REFERENCE_CLASS_NAME, PLATFORM_ABI_CLASS_NAME)
    code.unindent()
    code.add("}\n")
    code.add("val __resultRef = %T(%T.toRawComPtr(%T.readPointer(__resultOut)))\n", IUNKNOWN_REFERENCE_CLASS_NAME, PLATFORM_ABI_CLASS_NAME, PLATFORM_ABI_CLASS_NAME)
    code.add("return __resultRef.use { it.asInspectable() }\n")
    if (finallyStatements.isNotEmpty()) {
        code.unindent()
        code.add("} finally {\n")
        code.indent()
        finallyStatements.forEach { finallyStatement -> code.add("%L\n", finallyStatement) }
        code.unindent()
        code.add("}\n")
    }
    code.unindent()
    code.add("}\n")
    repeat(scopedParameterOpeners.size) {
        code.unindent()
        code.add("}\n")
    }
    return code.build()
}

private fun composableUserParameters(method: WinRtMethodDefinition): Pair<WinRtMethodDefinition, List<WinRtParameterDefinition>>? {
    if (method.returnType.typeName == "Void" || method.parameters.size < 2) {
        return null
    }
    val trailing = method.parameters.takeLast(2)
    if (trailing.any { parameter -> parameter.type.typeName != "System.Object" }) {
        return null
    }
    return method to method.parameters.dropLast(2)
}

private fun factoryCreateFunctionName(method: WinRtMethodDefinition): String =
    method.name.replaceFirstChar(Char::lowercase)

internal fun KotlinProjectionRenderer.appendMetadataCompanionMembers(
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
        if (plan.declarationKind == KotlinProjectionDeclarationKind.Interface && canRenderInterfaceProxy(plan)) {
            builder.addProperty(
                PropertySpec.builder("TYPE_HANDLE", WINRT_TYPE_HANDLE_CLASS_NAME)
                    .initializer("%T(%S, IID)", WINRT_TYPE_HANDLE_CLASS_NAME, projectedClassName.canonicalName)
                    .build(),
            )
        }
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
                .addCode("return %T(instance, kotlin.Unit)\n", projectedClassName)
                .build(),
        )
        builder.addFunction(
            FunSpec.builder("wrap")
                .addModifiers(KModifier.INTERNAL)
                .addParameter("instance", IUNKNOWN_REFERENCE_CLASS_NAME)
                .returns(projectedClassName)
                .addCode("return wrap(instance.asInspectable())\n")
                .build(),
        )
    }
    if (plan.declarationKind == KotlinProjectionDeclarationKind.Interface && canRenderInterfaceProxy(plan)) {
        builder.addFunction(
            FunSpec.builder("wrap")
                .addModifiers(KModifier.INTERNAL)
                .apply {
                    repeat(plan.type.genericParameterCount) { index ->
                        addTypeVariable(TypeVariableName("T$index"))
                    }
                }
                .addParameter("instance", IUNKNOWN_REFERENCE_CLASS_NAME)
                .returns(plan.projectedSelfTypeName())
                .addCode(
                    "return NativeProjection%L(instance)\n",
                    if (plan.type.genericParameterCount == 0) {
                        ""
                    } else {
                        "<${(0 until plan.type.genericParameterCount).joinToString(", ") { index -> "T$index" }}>"
                    },
                )
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
    val abiSlotBindingNames = plan.abiSlotBindings.mapTo(mutableSetOf()) { it.constantName }
    plan.instanceMemberBindings
        .filterNot { it.bindingName in abiSlotBindingNames }
        .filterNot { binding ->
            plan.requiredInterfaceAugmentationDescriptor?.mappedAugmentationMembers.orEmpty().contains("INotifyPropertyChanged") &&
                mappedTypeByAbiName(binding.ownerInterfaceQualifiedName.substringBefore('<').removeSuffix("?"))?.descriptionName == "INotifyPropertyChanged"
        }
        .forEach { binding ->
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
                .initializer("%L", metadataSlotExpression(binding.slotInterfaceQualifiedName, binding.slotConstantName))
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
                .initializer("%L", metadataSlotExpression(binding.slotInterfaceQualifiedName, binding.slotConstantName))
                .build(),
        )
    }
}

internal fun KotlinProjectionRenderer.appendDescriptorHandoffCompanionMembers(
    builder: TypeSpec.Builder,
    plan: KotlinTypeProjectionPlan,
) {
    if (plan.projectedAttributes.isNotEmpty()) {
        builder.addStringListProperty(
            "PROJECTED_ATTRIBUTES",
            plan.projectedAttributes.map { attribute ->
                listOf(
                    attribute.projectedTypeName,
                    attribute.metadataTypeName,
                    "platform=${attribute.isPlatformAttribute}",
                    "args=${attribute.renderedArguments.joinToString(",")}",
                ).joinToString("|")
            },
        )
    }
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
        builder.addStringListProperty(
            "OBJECT_REFERENCE_PLANS",
            descriptor.objectReferencePlans.map { plan ->
                listOf(
                    plan.interfaceName,
                    "cache=${plan.cacheName}",
                    "default=${plan.isDefaultInterface}",
                    "skip=${plan.skippedReason.orEmpty()}",
                    "inner=${plan.usesInner}",
                    "defaultObjRef=${plan.usesDefaultInterfaceObjRef}",
                    "hierarchy=${plan.defaultInterfaceHierarchyIndex?.toString().orEmpty()}",
                    "defaultObjRefSlot=${plan.defaultInterfaceObjRefVtableSlot?.toString().orEmpty()}",
                    "generic=${plan.requiresGenericInstantiation}",
                ).joinToString("|")
            },
        )
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
        builder.addProperty(
            PropertySpec.builder("CUSTOM_MAPPED_MEMBER_CALL_MODE", String::class)
                .addModifiers(KModifier.INTERNAL, KModifier.CONST)
                .initializer("%S", descriptor.callMode)
                .build(),
        )
        builder.addProperty(
            PropertySpec.builder("CUSTOM_MAPPED_MEMBER_EXPLICIT", Boolean::class)
                .addModifiers(KModifier.INTERNAL, KModifier.CONST)
                .initializer("%L", descriptor.emitsExplicitMembers)
                .build(),
        )
        builder.addProperty(
            PropertySpec.builder("CUSTOM_MAPPED_MEMBER_PRIVATE", Boolean::class)
                .addModifiers(KModifier.INTERNAL, KModifier.CONST)
                .initializer("%L", descriptor.emitsPrivateMembers)
                .build(),
        )
    }
    plan.genericAbiClassInitializationDescriptor?.let { descriptor ->
        builder.addStringListProperty("GENERIC_ABI_INVOKE_SLOTS", descriptor.invokeSlotNames)
        builder.addStringListProperty("GENERIC_ABI_TYPE_ARRAYS", descriptor.genericTypeArrayDependencies)
    }
    plan.requiredInterfaceAugmentationDescriptor?.let { descriptor ->
        builder.addStringListProperty("REQUIRED_INTERFACE_NAMES", descriptor.requiredInterfaceNames)
        builder.addStringListProperty("REQUIRED_EXPLICIT_FORWARD_MEMBERS", descriptor.explicitForwardMemberNames)
        builder.addStringListProperty("REQUIRED_MAPPED_AUGMENTATION_MEMBERS", descriptor.mappedAugmentationMembers)
        builder.addStringListProperty(
            "REQUIRED_MAPPED_HELPER_PLANS",
            descriptor.mappedHelperPlans.map { plan ->
                listOf(
                    plan.interfaceName,
                    plan.memberFamily,
                    plan.callMode,
                    "helper=${plan.helperWrapperName.orEmpty()}",
                    "adapter=${plan.adapterFieldName.orEmpty()}",
                    "private=${plan.emitsPrivateMembers}",
                    "mappedHelpers=${plan.emitsMappedTypeHelpers}",
                    "removeEnumerable=${plan.removesNonGenericEnumerable}",
                    "removeGeneric=${plan.removesGenericEnumerableName.orEmpty()}",
                ).joinToString("|")
            },
        )
    }
    plan.fastAbiClassDescriptor?.let { descriptor ->
        builder.addStringListProperty(
            "FAST_ABI_INTERFACE_SLOTS",
            descriptor.interfaceSlots.map { slot ->
                listOf(
                    slot.interfaceName,
                    "default=${slot.isDefault}",
                    "start=${slot.vtableStartIndex}",
                    "count=${slot.methodCount}",
                    "hierarchyOffset=${slot.hierarchyOffsetAfterDefault}",
                    "next=${slot.nextVtableStartIndex}",
                ).joinToString("|")
            },
        )
        builder.addStringListProperty(
            "FAST_ABI_PROPERTY_SLOTS",
            descriptor.propertySlots.map { slot ->
                listOf(
                    slot.propertyName,
                    "start=${slot.vtableStartIndex}",
                    "get=${slot.getterVtableIndex ?: ""}",
                    "set=${slot.setterVtableIndex ?: ""}",
                ).joinToString("|")
            },
        )
    }
    plan.moduleActivationAndAuthoringDescriptor?.let { module ->
        builder.addStringListProperty("DEFERRED_AUTHORING_FACTORY_MEMBERS", module.factoryMemberNames)
        builder.addStringListProperty("DEFERRED_MODULE_ACTIVATION_FACTORY_ENTRIES", module.moduleActivationFactoryEntries)
    }
}
