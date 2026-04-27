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
        override = override,
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
        override = false,
    )
}

internal fun KotlinProjectionRenderer.buildBoundEventFunctions(
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
            .returns(EVENT_REGISTRATION_TOKEN_CLASS_NAME)
            .addCode("%L\n", addInvocation)
            .build(),
        FunSpec.builder("remove${event.name}")
            .apply { if (override) addModifiers(KModifier.OVERRIDE) }
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

internal fun KotlinProjectionRenderer.renderEventFunctions(event: WinRtEventDefinition, abstract: Boolean, override: Boolean = false): List<FunSpec> {
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
            .returns(EVENT_REGISTRATION_TOKEN_CLASS_NAME)
            .build(),
        FunSpec.builder("remove${event.name}")
            .addParameter("token", EVENT_REGISTRATION_TOKEN_CLASS_NAME)
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

internal fun KotlinProjectionRenderer.buildMetadataCompanionShell(
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
    val binding = plan.staticMemberBindings.firstOrNull { it.bindingName == "STATIC_${method.name.uppercase()}_SLOT" } ?: return null
    val invocation = renderBoundStaticInvocation(binding)
    return FunSpec.builder(method.name)
        .returns(resolveTypeName(method.returnTypeName))
        .addParameters(method.parameters.map { ParameterSpec.builder(it.name, resolveTypeName(it.typeName)).build() })
        .addCode("%L\n", invocation)
        .build()
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

internal fun KotlinProjectionRenderer.hasDefaultComposableFactoryConstructor(plan: KotlinTypeProjectionPlan): Boolean {
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

internal fun KotlinProjectionRenderer.renderDefaultComposableFactoryCreateInstance(plan: KotlinTypeProjectionPlan): FunSpec {
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

internal fun KotlinProjectionRenderer.appendDescriptorHandoffCompanionMembers(
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
