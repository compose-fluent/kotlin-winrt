package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteHResultPolicy
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
import io.github.composefluent.winrt.metadata.WinRTMetadataProjectionContext
import io.github.composefluent.winrt.metadata.WinRTMetadataProjectionInventory
import io.github.composefluent.winrt.metadata.WinRTMetadataProjectionInventoryBuilder
import io.github.composefluent.winrt.metadata.WinRTModuleActivationAndAuthoringDescriptor
import io.github.composefluent.winrt.metadata.WinRTMethodVtableDescriptor
import io.github.composefluent.winrt.metadata.WinRTMethodDefinition
import io.github.composefluent.winrt.metadata.WinRTNamespace
import io.github.composefluent.winrt.metadata.WinRTObjectReferenceSurfaceDescriptor
import io.github.composefluent.winrt.metadata.WinRTParameterDefinition
import io.github.composefluent.winrt.metadata.WinRTPropertyDefinition
import io.github.composefluent.winrt.metadata.WinRTProjectedAttributeDescriptor
import io.github.composefluent.winrt.metadata.WinRTRequiredInterfaceAugmentationDescriptor
import io.github.composefluent.winrt.metadata.WinRTSignatureWriterDescriptor
import io.github.composefluent.winrt.metadata.WinRTTypeDeclarationDescriptor
import io.github.composefluent.winrt.metadata.WinRTTypeDefinition
import io.github.composefluent.winrt.metadata.WinRTTypeRef
import io.github.composefluent.winrt.metadata.WinRTTypeKind
import io.github.composefluent.winrt.metadata.WinRTMetadataValidationOptions
import io.github.composefluent.winrt.metadata.WinRTMetadataSemanticHelpers
import io.github.composefluent.winrt.metadata.projectedPropertyTypeName
import io.github.composefluent.winrt.metadata.requireValidForProjection
import io.github.composefluent.winrt.metadata.semanticHelpers
import io.github.composefluent.winrt.metadata.isWinRTObjectTypeName
import io.github.composefluent.winrt.metadata.isWinRTVoidTypeName
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
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.time.OffsetDateTime
import kotlin.collections.AbstractList
import kotlin.collections.AbstractMap
import kotlin.LazyThreadSafetyMode
import kotlin.io.path.extension

internal fun KotlinProjectionRenderer.renderBoundEventFunctions(
    plan: KotlinTypeProjectionPlan,
    event: WinRTEventDefinition,
    override: Boolean = false,
): List<FunSpec>? {
    val addBinding = plan.instanceMemberBindings.firstOrNull {
        it.bindingName == "${event.name.uppercase()}_ADD_SLOT"
    } ?: return null
    plan.instanceMemberBindings.firstOrNull {
        it.bindingName == "${event.name.uppercase()}_REMOVE_SLOT"
    } ?: return null
    return renderEventSourceBackedFunctions(
        event = event,
        eventInvokeDescriptor = plan.eventInvokeDescriptors.firstOrNull { it.eventName == event.name && !it.isStatic },
        modifiers = if (override) runtimeClassMemberModifiers(plan, addBinding) else emptyList(),
        projectedAttributes = addBinding.projectedAttributes,
    )
}

internal fun KotlinProjectionRenderer.renderBoundStaticEventFunctions(
    plan: KotlinTypeProjectionPlan,
    event: WinRTEventDefinition,
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
    event: WinRTEventDefinition,
    eventInvokeDescriptor: WinRTEventInvokeDescriptor?,
): List<FunSpec> {
    return renderEventSourceBackedFunctions(
        event = event,
        eventInvokeDescriptor = eventInvokeDescriptor,
        modifiers = emptyList(),
    )
}

private fun KotlinProjectionRenderer.renderEventSourceBackedFunctions(
    event: WinRTEventDefinition,
    eventInvokeDescriptor: WinRTEventInvokeDescriptor?,
    modifiers: List<KModifier>,
    projectedAttributes: List<WinRTProjectedAttributeDescriptor> = emptyList(),
): List<FunSpec> {
    val typeName = resolveTypeName(eventInvokeDescriptor?.delegateTypeName ?: event.delegateTypeName)
    val propertyName = event.name.replaceFirstChar(Char::lowercase)
    return listOf(
        FunSpec.builder("add${event.name}")
            .addProjectedAttributeAnnotations(projectedAttributes)
            .addModifiers(modifiers)
            .addParameter("handler", typeName)
            .returns(EVENT_REGISTRATION_TOKEN_CLASS_NAME)
            .addCode("return %L.add(handler)\n", propertyName)
            .build(),
        FunSpec.builder("remove${event.name}")
            .addProjectedAttributeAnnotations(projectedAttributes)
            .addModifiers(modifiers)
            .addParameter("token", EVENT_REGISTRATION_TOKEN_CLASS_NAME)
            .addCode("%L.remove(token)\n", propertyName)
            .build(),
    )
}

internal fun KotlinProjectionRenderer.buildBoundEventFunctions(
    event: WinRTEventDefinition,
    eventInvokeDescriptor: WinRTEventInvokeDescriptor?,
    addInvocation: CodeBlock,
    removeInvocation: CodeBlock,
    modifiers: List<KModifier>,
    projectedAttributes: List<WinRTProjectedAttributeDescriptor> = emptyList(),
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
    event: WinRTEventDefinition,
    eventInvokeDescriptor: WinRTEventInvokeDescriptor?,
    abstract: Boolean,
    override: Boolean = false,
    modifiers: List<KModifier> = if (override) listOf(KModifier.OVERRIDE) else emptyList(),
    eventSourceOwnerTypeName: String? = null,
    eventSourceEventTypeName: String? = null,
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
                        "val __eventSource = %T.%L(%L, %L) as? %T",
                        ClassName(
                            WINRT_EVENT_PROJECTION_HELPERS_CLASS_NAME.packageName,
                            eventSourceOwnerHelperName(
                                ownerType = eventSourceOwnerTypeName,
                                eventType = eventSourceEventTypeName
                                    ?: eventInvokeDescriptor?.delegateTypeName
                                    ?: event.delegateTypeName,
                                supportOwnerIdentity = supportOwnerIdentity,
                            ),
                        ),
                        eventSourceCreateFunctionName(
                            eventType = eventSourceEventTypeName ?: eventInvokeDescriptor?.delegateTypeName ?: event.delegateTypeName,
                            ownerType = eventSourceOwnerTypeName,
                        ),
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
    event: WinRTEventDefinition,
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
    staticMethods: List<WinRTMethodDefinition>,
    staticProperties: List<WinRTPropertyDefinition>,
    staticEvents: List<WinRTEventDefinition>,
): TypeSpec =
    TypeSpec.companionObjectBuilder("Metadata")
        .apply {
            val projectedStaticMethods = mergedStaticMethods(plan, staticMethods)
            val projectedStaticProperties = mergedStaticProperties(plan, staticProperties)
            val projectedStaticEvents = mergedStaticEvents(plan, staticEvents)
            appendMetadataCompanionMembers(this, plan)
            projectedStaticMethods.forEach { addFunction(renderBoundStaticMethod(plan, it) ?: renderStubMethod(it)) }
            projectedStaticProperties.forEach {
                addProperty(renderBoundStaticProperty(plan, it) ?: renderStubProperty(plan.type.qualifiedName, it, typesByQualifiedName = plan.typesByQualifiedName))
            }
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
                        eventSourceAddSlot = addBinding?.slotCodeBlock(),
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

internal fun mergedStaticMethods(
    plan: KotlinTypeProjectionPlan,
    staticMethods: List<WinRTMethodDefinition>,
): List<WinRTMethodDefinition> {
    val merged = linkedMapOf<String, WinRTMethodDefinition>()
    staticMethods.forEach { method ->
        merged.putIfAbsent(method.projectionSignatureIgnoringStaticKey(), method.copy(isStatic = true))
    }
    plan.staticInterfaceNames
        .mapNotNull(plan.typesByQualifiedName::get)
        .flatMap(WinRTTypeDefinition::methods)
        .filter(WinRTMethodDefinition::isProjectedCallableMethod)
        .forEach { method ->
            merged.putIfAbsent(method.projectionSignatureIgnoringStaticKey(), method.copy(isStatic = true))
        }
    return merged.values.toList()
}

internal fun mergedStaticProperties(
    plan: KotlinTypeProjectionPlan,
    staticProperties: List<WinRTPropertyDefinition>,
): List<WinRTPropertyDefinition> {
    val merged = linkedMapOf<String, WinRTPropertyDefinition>()
    fun add(property: WinRTPropertyDefinition) {
        val staticProperty = property.copy(isStatic = true)
        val key = staticProperty.projectionSignatureIgnoringStaticKey()
        merged[key] = merged[key]?.mergeStaticAccessor(staticProperty) ?: staticProperty
    }
    staticProperties.forEach(::add)
    plan.staticInterfaceNames
        .mapNotNull(plan.typesByQualifiedName::get)
        .flatMap(WinRTTypeDefinition::properties)
        .forEach(::add)
    return merged.values.toList()
}

private fun mergedStaticEvents(
    plan: KotlinTypeProjectionPlan,
    staticEvents: List<WinRTEventDefinition>,
): List<WinRTEventDefinition> {
    val merged = linkedMapOf<String, WinRTEventDefinition>()
    staticEvents.forEach { event ->
        merged.putIfAbsent("${event.name}|${event.delegateTypeName}", event.copy(isStatic = true))
    }
    plan.staticInterfaceNames
        .mapNotNull(plan.typesByQualifiedName::get)
        .flatMap(WinRTTypeDefinition::events)
        .forEach { event ->
            merged.putIfAbsent("${event.name}|${event.delegateTypeName}", event.copy(isStatic = true))
        }
    return merged.values.toList()
}

private fun WinRTPropertyDefinition.mergeStaticAccessor(other: WinRTPropertyDefinition): WinRTPropertyDefinition {
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
    method: WinRTMethodDefinition,
): FunSpec? {
    if (isActivationFactoryCreateMethod(plan, method)) {
        return FunSpec.builder(method.projectedMethodName())
            .returns(resolveTypeName(method.returnTypeName))
            .addCode("return Metadata.wrap(ActivationFactory.activate())\n")
            .build()
    }
    val binding = plan.staticMemberBindings.firstOrNull {
        it.bindingName == staticMethodBindingName(plan, method)
    } ?: return null
    val invocation = renderBoundStaticInvocation(binding)
    return FunSpec.builder(method.projectedMethodName())
        .addProjectedAttributeAnnotations(binding.projectedAttributes)
        .addMethodGenericParameters(method)
        .returns(resolveTypeName(method.projectedKotlinReturnTypeName()))
        .addParameters(
            method.projectedKotlinParameters().map { parameter ->
                ParameterSpec.builder(parameter.name, resolveTypeName(parameter.typeName)).build()
            },
        )
        .addCode("%L\n", invocation)
        .build()
}

internal fun staticMethodBindingName(
    plan: KotlinTypeProjectionPlan,
    method: WinRTMethodDefinition,
): String {
    val declaringStaticMethod = plan.staticInterfaceNames
        .mapNotNull(plan.typesByQualifiedName::get)
        .firstNotNullOfOrNull { staticInterface ->
            staticInterface.methods
                .firstOrNull { it.projectionSignatureIgnoringStaticKey() == method.projectionSignatureIgnoringStaticKey() }
                ?.let { staticInterface to it }
        }
    return if (declaringStaticMethod != null) {
        val (staticInterface, staticMethod) = declaringStaticMethod
        val staticInterfaces = plan.staticInterfaceNames.mapNotNull(plan.typesByQualifiedName::get)
        val staticMethodNameCounts = staticInterfaces
            .flatMap { interfaceType -> interfaceType.methods.filter(WinRTMethodDefinition::isProjectedCallableMethod) }
            .groupingBy(WinRTMethodDefinition::name)
            .eachCount()
        "STATIC_${staticMethod.staticBindingSlotConstantName(staticInterface.methods, staticMethodNameCounts)}"
    } else {
        "STATIC_${method.abiSlotConstantName(plan.type.methods)}"
    }
}

internal fun isActivationFactoryCreateMethod(
    plan: KotlinTypeProjectionPlan,
    method: WinRTMethodDefinition,
): Boolean =
    method.name == "create" &&
        method.parameters.isEmpty() &&
        KotlinProjectionCompanionKind.ActivationFactory in plan.companionKinds &&
        method.returnTypeName.let { it == plan.type.qualifiedName || it == plan.type.name }

internal fun KotlinProjectionRenderer.renderBoundStaticProperty(
    plan: KotlinTypeProjectionPlan,
    property: WinRTPropertyDefinition,
): PropertySpec? {
    val getterBinding = plan.staticMemberBindings.firstOrNull {
        it.bindingName == "STATIC_${property.name.uppercase()}_GETTER_SLOT"
    } ?: return null
    val propertyTypeName = property.projectedPropertyTypeName(getterBinding.ownerInterfaceQualifiedName, plan.typesByQualifiedName)
    val builder = PropertySpec.builder(
        property.name.replaceFirstChar(Char::lowercase),
        resolveTypeName(propertyTypeName),
    ).mutable(!property.isReadOnly)
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
                .addParameter("value", resolveTypeName(propertyTypeName))
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
            .addProperty(
                PropertySpec.builder("_activationFactory", ACTIVATION_FACTORY_REFERENCE_CLASS_NAME)
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("%T.get(RUNTIME_CLASS)", ACTIVATION_FACTORY_CLASS_NAME)
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
                    addProperty(
                        PropertySpec.builder("_factoryInterface", IUNKNOWN_REFERENCE_CLASS_NAME)
                            .addModifiers(KModifier.PRIVATE)
                            .initializer(
                                "%T.get(RUNTIME_CLASS, FACTORY_INTERFACE_IID)",
                                ACTIVATION_FACTORY_CLASS_NAME,
                            )
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
                    .addStatement("return _activationFactory.activateInstance(Metadata.DEFAULT_INTERFACE_IID)")
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
                plan.composableFactoryBindings.forEach { factory ->
                    factory.iid?.let { iid ->
                        val iidConstantName = composableFactoryIidConstantName(factory)
                        addProperty(
                            PropertySpec.builder(iidConstantName, GUID_CLASS_NAME)
                                .addModifiers(KModifier.INTERNAL)
                                .initializer("%T(%S)", GUID_CLASS_NAME, iid.toString())
                                .build(),
                        )
                        addProperty(
                            PropertySpec.builder(
                                composableFactoryCachePropertyName(factory),
                                IUNKNOWN_REFERENCE_CLASS_NAME,
                            )
                                .addModifiers(KModifier.PRIVATE)
                                .delegate(
                                    CodeBlock.of(
                                        "lazy(%T.PUBLICATION) { %T.get(Metadata.TYPE_NAME, %L) }",
                                        LAZY_THREAD_SAFETY_MODE_CLASS_NAME,
                                        ACTIVATION_FACTORY_CLASS_NAME,
                                        iidConstantName,
                                    ),
                                )
                                .build(),
                        )
                    }
                }
                renderComposableFactoryCreateFunctions(plan).forEach(::addFunction)
                renderDerivedComposableFactoryCreateFunctions(plan).forEach(::addFunction)
            }
            .build()
}

internal fun KotlinProjectionRenderer.renderFactoryConstructors(plan: KotlinTypeProjectionPlan): List<FunSpec> {
    val factoryType = plan.activatableFactoryInterfaceName?.let(plan.typesByQualifiedName::get) ?: return emptyList()
    return factoryType.methods
        .filter(WinRTMethodDefinition::isProjectedCallableMethod)
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
    return plan.composableFactoryBindings
        .flatMap { factory ->
            val factoryType = plan.typesByQualifiedName[factory.qualifiedName] ?: return@flatMap emptyList()
            factoryType.methods
                .filter(WinRTMethodDefinition::isProjectedCallableMethod)
                .filter { method -> method.returnType.typeName == plan.type.qualifiedName }
                .mapNotNull(::composableUserParameters)
                .map { (method, userParameters) -> factory to method to userParameters }
        }
        .map { (factoryAndMethod, userParameters) ->
            val (factory, method) = factoryAndMethod
            val constructor = FunSpec.constructorBuilder()
                .addParameters(userParameters.map { parameter -> ParameterSpec.builder(parameter.name, resolveTypeName(parameter.typeName)).build() })
                .apply {
                    if (!factory.isVisible) {
                        addModifiers(KModifier.PROTECTED)
                    }
                }
            if (plan.supportsDerivedComposableConstruction()) {
                val projectedClassName = ClassName(plan.packageName, plan.type.name)
                run {
                    val arguments = userParameters.joinToString(", ") { parameter -> parameter.name }
                    constructor.callThisConstructor(CodeBlock.of("%T.Instance", DERIVED_COMPOSED_CLASS_NAME))
                    constructor.addCode(
                        "if (this::class == %T::class) {\n",
                        projectedClassName,
                    )
                    constructor.addStatement("    nativeObject = ComposableFactory.%L(%L)", factoryCreateFunctionName(method), arguments)
                    constructor.addStatement("    %T.registerComposableWrapper(this, nativeObject)", COM_WRAPPERS_SUPPORT_CLASS_NAME)
                    constructor.addCode("} else {\n")
                    constructor.addStatement("    %T.ensureInitialized()", WINRT_AUTHORING_SUPPORT_INTRINSIC_CLASS_NAME)
                    constructor.addStatement(
                        "    _composableReference = ComposableFactory.%LForSubclass(this, %L%L)",
                        factoryCreateFunctionName(method),
                        CodeBlock.of("Metadata.DEFAULT_INTERFACE_IID"),
                        if (arguments.isBlank()) "" else ", $arguments",
                    )
                    constructor.addStatement("    nativeObject = requireNotNull(_composableReference).instance")
                    constructor.addCode("}\n")
                    constructor.build()
                }
            } else {
                constructor
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
}

internal fun KotlinProjectionRenderer.renderActivationFactoryCreateFunctions(plan: KotlinTypeProjectionPlan): List<FunSpec> {
    val factoryType = plan.activatableFactoryInterfaceName?.let(plan.typesByQualifiedName::get) ?: return emptyList()
    return factoryType.methods
        .filter(WinRTMethodDefinition::isProjectedCallableMethod)
        .filter { method -> method.returnType.typeName == plan.type.qualifiedName }
        .map { method ->
            val returnBinding = KotlinProjectionAbiTypeBinding(
                kind = KotlinProjectionAbiValueKind.InspectableReference,
                typeName = IINSPECTABLE_REFERENCE_CLASS_NAME.canonicalName,
            )
            val parameterBindings = method.parameters.map { parameter ->
                KotlinProjectionAbiParameterBinding(
                    parameter.name,
                    KotlinProjectionPlanner(useWinAppSdkTypeRedirects = useWinAppSdkTypeRedirects)
                        .classifyAbiTypeBinding(parameter.typeName, factoryType.namespace, plan.typesByQualifiedName),
                )
            }
            val callPlan = requireAbiCallPlan(
                bindingName = "${factoryType.qualifiedName}.${method.name}",
                returnBinding = returnBinding,
                parameterBindings = parameterBindings,
                suppressHResultCheck = method.isNoException,
            )
            val invocation = renderInlineAbiInvocation(
                invokeTargetExpression =
                    if (plan.activatableFactoryInterfaceIid != null) "_factoryInterface" else "acquire()",
                slotExpression = metadataSlotExpression(factoryType.qualifiedName, method.abiSlotConstantName(factoryType.methods)),
                callPlan = callPlan,
            )
            FunSpec.builder(factoryCreateFunctionName(method))
                .addModifiers(KModifier.INTERNAL)
                .addParameters(method.parameters.map { parameter -> ParameterSpec.builder(parameter.name, resolveTypeName(parameter.typeName)).build() })
                .returns(IINSPECTABLE_REFERENCE_CLASS_NAME)
                .addCode("%L\n", invocation)
                .build()
        }
}

internal fun KotlinProjectionRenderer.renderComposableFactoryCreateFunctions(plan: KotlinTypeProjectionPlan): List<FunSpec> {
    return plan.composableFactoryBindings
        .flatMap { factory ->
            val factoryType = plan.typesByQualifiedName[factory.qualifiedName] ?: return@flatMap emptyList()
            factoryType.methods
                .filter(WinRTMethodDefinition::isProjectedCallableMethod)
                .filter { method -> method.returnType.typeName == plan.type.qualifiedName }
                .mapNotNull(::composableUserParameters)
                .map { (method, userParameters) -> Triple(factory, factoryType, method to userParameters) }
        }
        .map { (factory, factoryType, methodAndUserParameters) ->
            val (method, userParameters) = methodAndUserParameters
            FunSpec.builder(factoryCreateFunctionName(method))
                .addModifiers(KModifier.INTERNAL)
                .addParameters(userParameters.map { parameter -> ParameterSpec.builder(parameter.name, resolveTypeName(parameter.typeName)).build() })
                .returns(IINSPECTABLE_REFERENCE_CLASS_NAME)
                .addCode(
                    "%L\n",
                    renderComposableFactoryInvocation(plan, factory, factoryType, method, userParameters),
                )
                .build()
        }
}

private fun KotlinProjectionRenderer.renderDerivedComposableFactoryCreateFunctions(plan: KotlinTypeProjectionPlan): List<FunSpec> {
    if (!plan.supportsDerivedComposableConstruction()) {
        return emptyList()
    }
    return plan.composableFactoryBindings
        .flatMap { factory ->
            val factoryType = plan.typesByQualifiedName[factory.qualifiedName] ?: return@flatMap emptyList()
            factoryType.methods
                .filter(WinRTMethodDefinition::isProjectedCallableMethod)
                .filter { method -> method.returnType.typeName == plan.type.qualifiedName }
                .mapNotNull(::composableUserParameters)
                .map { (method, userParameters) -> Triple(factory, factoryType, method to userParameters) }
        }
        .map { (factory, factoryType, methodAndUserParameters) ->
            val (method, userParameters) = methodAndUserParameters
            FunSpec.builder("${factoryCreateFunctionName(method)}ForSubclass")
                .addModifiers(KModifier.INTERNAL)
                .addParameter("value", ANY)
                .addParameter("outerInterfaceId", GUID_CLASS_NAME)
                .addParameters(userParameters.map { parameter -> ParameterSpec.builder(parameter.name, resolveTypeName(parameter.typeName)).build() })
                .returns(WINRT_COMPOSABLE_OBJECT_REFERENCE_CLASS_NAME)
                .addCode(
                    "%L\n",
                    renderDerivedComposableFactoryInvocation(plan, factory, factoryType, method, userParameters),
                )
                .build()
        }
}

private fun KotlinProjectionRenderer.renderDerivedComposableFactoryInvocation(
    plan: KotlinTypeProjectionPlan,
    factory: KotlinProjectionComposableFactoryBinding,
    factoryType: WinRTTypeDefinition,
    method: WinRTMethodDefinition,
    userParameters: List<WinRTParameterDefinition>,
): CodeBlock {
    val parameterBindings = userParameters.map { parameter ->
        KotlinProjectionAbiParameterBinding(
            parameter.name,
            KotlinProjectionPlanner(useWinAppSdkTypeRedirects = useWinAppSdkTypeRedirects)
                .classifyAbiTypeBinding(parameter.typeName, factoryType.namespace, plan.typesByQualifiedName),
        )
    }
    val typedCallPlan = requireAbiCallPlan(
        bindingName = "${factoryType.qualifiedName}.${method.name}",
        returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
        parameterBindings = parameterBindings + composableBaseInterfaceBinding(),
    )
    val support = modulePlatformAbiCalls ?: inlineOnlyModulePlatformAbiCallSupport()
    val invocation = composeTypedProjectionCallSite(
        callPlan = typedCallPlan,
        callSiteSupport = support,
        callerOwnedResultType = WINRT_COMPOSABLE_FACTORY_RESULT_CLASS_NAME,
    )
    val call = support.typedInvocation(
        referenceExpression = "__factory",
        slotExpression = metadataSlotExpression(
            factoryType.qualifiedName,
            method.abiSlotConstantName(factoryType.methods),
        ),
        invocation = invocation,
    )
    return CodeBlock.builder()
        .add("val __factory = %L\n", composableFactoryCachePropertyName(factory))
        .add(
            "return %T.createComposableCCWForObject(value, outerInterfaceId, DEFAULT_INTERFACE_IID) { __baseInterface ->\n",
            COM_WRAPPERS_SUPPORT_CLASS_NAME,
        )
        .indent()
        .add("%L\n", call)
        .unindent()
        .add("}\n")
        .build()
}

private fun KotlinProjectionRenderer.renderComposableFactoryInvocation(
    plan: KotlinTypeProjectionPlan,
    factory: KotlinProjectionComposableFactoryBinding,
    factoryType: WinRTTypeDefinition,
    method: WinRTMethodDefinition,
    userParameters: List<WinRTParameterDefinition>,
): CodeBlock {
    val parameterBindings = userParameters.map { parameter ->
        KotlinProjectionAbiParameterBinding(
            parameter.name,
            KotlinProjectionPlanner(useWinAppSdkTypeRedirects = useWinAppSdkTypeRedirects)
                .classifyAbiTypeBinding(parameter.typeName, factoryType.namespace, plan.typesByQualifiedName),
        )
    }
    val typedCallPlan = requireAbiCallPlan(
        bindingName = "${factoryType.qualifiedName}.${method.name}",
        returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
        parameterBindings = parameterBindings + composableBaseInterfaceBinding(),
        suppressHResultCheck = method.isNoException,
    )
    val support = modulePlatformAbiCalls ?: inlineOnlyModulePlatformAbiCallSupport()
    val invocation = composeTypedProjectionCallSite(
        callPlan = typedCallPlan,
        callSiteSupport = support,
        callerOwnedResultType = WINRT_COMPOSABLE_FACTORY_RESULT_CLASS_NAME,
    )
    val call = support.typedInvocation(
        referenceExpression = "__factory",
        slotExpression = metadataSlotExpression(
            factoryType.qualifiedName,
            method.abiSlotConstantName(factoryType.methods),
        ),
        invocation = invocation,
    )
    return CodeBlock.builder()
        .add("val __factory = %L\n", composableFactoryCachePropertyName(factory))
        .add("val __baseInterface = %T.nullPointer\n", PLATFORM_ABI_CLASS_NAME)
        .add("val __factoryResult = %L\n", call)
        .add(
            "return %T.attachComposableFactoryResult(__factoryResult, DEFAULT_INTERFACE_IID)\n",
            COM_WRAPPERS_SUPPORT_CLASS_NAME,
        )
        .build()
}

private fun composableUserParameters(method: WinRTMethodDefinition): Pair<WinRTMethodDefinition, List<WinRTParameterDefinition>>? {
    if (isWinRTVoidTypeName(method.returnType.typeName) || method.parameters.size < 2) {
        return null
    }
    val trailing = method.parameters.takeLast(2)
    if (trailing.any { parameter -> !isWinRTObjectTypeName(parameter.type.typeName) }) {
        return null
    }
    return method to method.parameters.dropLast(2)
}

private fun factoryCreateFunctionName(method: WinRTMethodDefinition): String =
    method.name.replaceFirstChar(Char::lowercase)

private fun composableFactoryIidConstantName(factory: KotlinProjectionComposableFactoryBinding): String =
    generatedLocalIdentifier(
        prefix = "",
        identifier = factory.qualifiedName.substringAfterLast('.').substringBefore('<').uppercase(),
        suffix = "_IID",
    )

private fun composableFactoryCachePropertyName(factory: KotlinProjectionComposableFactoryBinding): String =
    generatedLocalIdentifier(
        prefix = "_",
        identifier =
            factory.qualifiedName
                .substringAfterLast('.')
                .substringBefore('<')
                .replaceFirstChar(Char::lowercase),
        suffix = "",
    )

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
    if (!useInterfaceProjectionArtifacts) {
        appendDescriptorHandoffCompanionMembers(builder, plan)
    }
    plan.interfaceIid?.let { iid ->
        builder.addProperty(
            PropertySpec.builder("IID", GUID_CLASS_NAME)
                .initializer("%T(%S)", GUID_CLASS_NAME, iid.toString())
                .build(),
        )
        if (plan.declarationKind == KotlinProjectionDeclarationKind.Interface && canRenderInterfaceWrapper(plan)) {
            val typeHandleInitializer = if (plan.type.qualifiedName in projectedInterfaceCcwInputTypeNames) {
                CodeBlock.of(
                    "%T(%S, IID).also({ %T.%L() })",
                    WINRT_TYPE_HANDLE_CLASS_NAME,
                    projectedClassName.canonicalName,
                    winRTProjectedInterfaceCcwFactoriesClassName(supportOwnerIdentity),
                    projectedInterfaceCcwRegisterFunctionName(plan.type.qualifiedName),
                )
            } else {
                CodeBlock.of(
                    "%T(%S, IID)",
                    WINRT_TYPE_HANDLE_CLASS_NAME,
                    projectedClassName.canonicalName,
                )
            }
            builder.addProperty(
                PropertySpec.builder("TYPE_HANDLE", WINRT_TYPE_HANDLE_CLASS_NAME)
                    .initializer(typeHandleInitializer)
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
                    "return %M(instance, iid)\n",
                    ACQUIRE_INTERFACE_REFERENCE_FUNCTION_NAME,
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
        KotlinProjectionSpecializationKind.AttributeClass !in plan.specializationKinds &&
        plan.type.genericParameterCount == 0 &&
        plan.defaultInterfaceIid != null
    ) {
        builder.addProperty(
            PropertySpec.builder("TYPE_HANDLE", WINRT_TYPE_HANDLE_CLASS_NAME)
                .initializer("%T(TYPE_NAME, DEFAULT_INTERFACE_IID)", WINRT_TYPE_HANDLE_CLASS_NAME)
                .build(),
        )
    }
    if (plan.declarationKind == KotlinProjectionDeclarationKind.Class &&
        KotlinProjectionSpecializationKind.StaticClass !in plan.specializationKinds &&
        KotlinProjectionSpecializationKind.AttributeClass !in plan.specializationKinds) {
        builder.addInitializerBlock(
            CodeBlock.builder()
                .add(
                    "%T.ensureInitialized()\n",
                    WINRT_PROJECTION_SUPPORT_INTRINSIC_CLASS_NAME,
                )
                .add(
                    "%T.registerRuntimeClassFactory(TYPE_NAME) { instance -> wrap(instance) }\n",
                    COM_WRAPPERS_SUPPORT_CLASS_NAME,
                )
                .add(
                    "%T.registerCustomAbiTypeMapping(%T::class, %T::class, TYPE_NAME, isRuntimeClass = true)\n",
                    PROJECTIONS_CLASS_NAME,
                    projectedClassName,
                    projectedClassName,
                )
                .apply {
                    plan.defaultInterfaceName?.let { defaultInterfaceName ->
                        val defaultInterfaceSignature = plan.typesByQualifiedName[defaultInterfaceName]
                            ?.iid
                            ?.let { iid ->
                                CodeBlock.of(
                                    "%T.guid(%T(%S))",
                                    WINRT_TYPE_SIGNATURE_CLASS_NAME,
                                    GUID_CLASS_NAME,
                                    iid.toString(),
                                )
                            }
                            ?: abiTypeSignature(renderAbiTypeBinding(defaultInterfaceName, plan.typesByQualifiedName, plan.type.namespace))
                        if (defaultInterfaceSignature != null) {
                            add(
                                "%T.registerDefaultInterfaceTypeName(TYPE_NAME, DEFAULT_INTERFACE, %L.render())\n",
                                PROJECTIONS_CLASS_NAME,
                                defaultInterfaceSignature,
                            )
                        } else {
                            add(
                                "%T.registerDefaultInterfaceTypeName(TYPE_NAME, DEFAULT_INTERFACE)\n",
                                PROJECTIONS_CLASS_NAME,
                            )
                        }
                    }
                    plan.defaultInterfaceName
                        ?.takeUnless { defaultInterfaceName -> defaultInterfaceName.contains('<') }
                        ?.let { defaultInterfaceName ->
                            plan.defaultInterfaceIid?.let {
                                add(
                                    "%T.registerDefaultInterfaceType(%T::class, %T::class, DEFAULT_INTERFACE_IID)\n",
                                    PROJECTIONS_CLASS_NAME,
                                    projectedClassName,
                                    resolveTypeName(defaultInterfaceName),
                                )
                            }
                        }
                }
                .build(),
        )
        builder.addFunction(
            FunSpec.builder("wrap")
                .addParameter("instance", IINSPECTABLE_REFERENCE_CLASS_NAME)
                .returns(projectedClassName)
                .addCode(
                    "val __managed = %T.findObject(%T.fromRawComPtr(instance.pointer), %T::class)\n" +
                        "if (__managed != null) {\n" +
                        "  instance.close()\n" +
                        "  return __managed\n" +
                        "}\n" +
                        "return %T(instance, kotlin.Unit)\n",
                    COM_WRAPPERS_SUPPORT_CLASS_NAME,
                    PLATFORM_ABI_CLASS_NAME,
                    projectedClassName,
                    projectedClassName,
                )
                .build(),
        )
        builder.addFunction(
            FunSpec.builder("wrap")
                .addParameter("instance", IUNKNOWN_REFERENCE_CLASS_NAME)
                .returns(projectedClassName)
                .addCode("return wrap(instance.asInspectable())\n")
                .build(),
        )
    }
    if (plan.declarationKind == KotlinProjectionDeclarationKind.Interface && canRenderInterfaceWrapper(plan)) {
        builder.addFunction(
            FunSpec.builder("wrap")
                .apply {
                    repeat(plan.type.genericParameterCount) { index ->
                        addTypeVariable(TypeVariableName("T$index"))
                    }
                }
                .addParameter("instance", IUNKNOWN_REFERENCE_CLASS_NAME)
                .returns(plan.projectedSelfTypeName())
                .apply {
                    addCode(
                        "return NativeProjection%L(instance)\n",
                        if (plan.type.genericParameterCount == 0) {
                            ""
                        } else {
                            "<${(0 until plan.type.genericParameterCount).joinToString(", ") { index -> "T$index" }}>"
                        },
                    )
                }
                .build(),
        )
    }
    plan.abiSlotBindings
        .filterNot { suppressProjectedMemberSlotConstants && mappedTypeByAbiName(plan.type.qualifiedName) == null }
        .forEach { binding ->
        builder.addProperty(
            PropertySpec.builder(binding.constantName, Int::class)
                .addModifiers(KModifier.CONST)
                .initializer("%L", binding.slot)
                .build(),
        )
    }
    val abiSlotBindingNames = plan.abiSlotBindings.mapTo(mutableSetOf()) { it.constantName }
    plan.instanceMemberBindings
        .filterNot { it.bindingName in abiSlotBindingNames }
        .filterNot(KotlinProjectionInstanceMemberBinding::isRuntimeOwnedMappedBinding)
        .filterNot(KotlinProjectionInstanceMemberBinding::isMappedRuntimeHelperBinding)
        .filterNot { suppressProjectedMemberSlotConstants && it.slot != null }
        .filterNot { binding ->
            plan.requiredInterfaceAugmentationDescriptor?.mappedAugmentationMembers.orEmpty().contains("INotifyPropertyChanged") &&
                mappedTypeByAbiName(binding.ownerInterfaceQualifiedName.substringBefore('<').removeSuffix("?"))?.descriptionName == "INotifyPropertyChanged"
        }
        .forEach { binding ->
        builder.addProperty(
            PropertySpec.builder(binding.bindingName, Int::class)
                .apply {
                    if (binding.slot != null) {
                        addModifiers(KModifier.CONST)
                        initializer("%L", binding.slot)
                    } else {
                        initializer("%L", metadataSlotExpression(binding.slotInterfaceQualifiedName, binding.slotConstantName))
                    }
                }
                .build(),
        )
    }
    plan.staticMemberBindings
        .filterNot { suppressProjectedMemberSlotConstants && it.slot != null }
        .forEach { binding ->
        builder.addProperty(
            PropertySpec.builder(binding.bindingName, Int::class)
                .apply {
                    if (binding.slot != null) {
                        addModifiers(KModifier.CONST)
                        initializer("%L", binding.slot)
                    } else {
                        initializer("%L", metadataSlotExpression(binding.slotInterfaceQualifiedName, binding.slotConstantName))
                    }
                }
                .build(),
        )
    }
}

internal fun KotlinProjectionStaticMemberBinding.slotCodeBlock(): CodeBlock =
    slot?.let { CodeBlock.of("%L", it) } ?: CodeBlock.of("%L", bindingName)

internal fun KotlinProjectionStaticMemberBinding.slotExpressionString(): String =
    slot?.toString() ?: bindingName

internal fun KotlinProjectionRenderer.canRenderInterfaceWrapper(plan: KotlinTypeProjectionPlan): Boolean =
    canRenderInterfaceProxy(plan)

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
    plan.guidSignatureDescriptor?.takeIf { it.guidText != null }?.let { descriptor ->
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

internal fun eventSourceCreateFunctionName(eventType: String, ownerType: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$eventType\t$ownerType".toByteArray(StandardCharsets.UTF_8))
    return "createEventSource_${digest.take(8).joinToString("") { byte -> "%02x".format(byte) }}"
}

internal fun sharedEventHandlerInboundCallSiteFunctionName(
    eventType: String,
    supportOwnerIdentity: String? = null,
): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("${supportOwnerIdentity.orEmpty()}\t$eventType".toByteArray(StandardCharsets.UTF_8))
    return "invokeEvent_${digest.take(8).joinToString("") { byte -> "%02x".format(byte) }}"
}

internal fun eventSourceOwnerHelperName(
    ownerType: String,
    eventType: String,
    supportOwnerIdentity: String? = null,
): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$ownerType\t$eventType".toByteArray(StandardCharsets.UTF_8))
    val baseName = "WinRTEventProjectionHelper_${digest.take(8).joinToString("") { byte -> "%02x".format(byte) }}"
    val suffix = winRTSupportOwnerIdentifierSuffix(supportOwnerIdentity) ?: return baseName
    return "${baseName}_$suffix"
}

private fun composableBaseInterfaceBinding(): KotlinProjectionAbiParameterBinding =
    KotlinProjectionAbiParameterBinding(
        name = "__baseInterface",
        typeBinding = KotlinProjectionAbiTypeBinding(
            KotlinProjectionAbiValueKind.RawAddress,
            RAW_ADDRESS_CLASS_NAME.canonicalName,
        ),
    )
