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

    internal fun renderType(plan: KotlinTypeProjectionPlan): TypeSpec = when (plan.declarationKind) {
        KotlinProjectionDeclarationKind.Interface -> renderInterfaceShell(plan)
        KotlinProjectionDeclarationKind.Class -> renderClassShell(plan)
        KotlinProjectionDeclarationKind.Enum -> renderEnumShell(plan)
        KotlinProjectionDeclarationKind.Struct -> renderStruct(plan)
        KotlinProjectionDeclarationKind.Delegate -> renderDelegate(plan)
    }

    internal fun renderInterfaceShell(plan: KotlinTypeProjectionPlan): TypeSpec {
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
        if (canRenderInterfaceProxy(plan)) {
            builder.addType(renderInterfaceNativeProjection(plan))
        }
        appendCompanionShells(builder, plan)
        return builder.build()
    }

    internal fun renderInterfaceNativeProjection(plan: KotlinTypeProjectionPlan): TypeSpec {
        val builder = TypeSpec.classBuilder("NativeProjection")
            .addModifiers(KModifier.PRIVATE)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("nativeObject", IUNKNOWN_REFERENCE_CLASS_NAME)
                    .build(),
            )
            .addSuperinterface(plan.projectedSelfTypeName())
            .addSuperinterface(IWINRT_OBJECT_CLASS_NAME)
            .addProperty(
                PropertySpec.builder("nativeObject", COM_OBJECT_REFERENCE_CLASS_NAME)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer("nativeObject")
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("primaryTypeHandle", WINRT_TYPE_HANDLE_CLASS_NAME.copy(nullable = true))
                    .addModifiers(KModifier.OVERRIDE)
                    .getter(
                        FunSpec.getterBuilder()
                            .addCode("return Metadata.TYPE_HANDLE\n")
                            .build(),
                    )
                    .build(),
            )
        repeat(plan.type.genericParameterCount) { index ->
            builder.addTypeVariable(TypeVariableName("T$index"))
        }
        collectInterfaceProxyTypes(plan).forEach { interfaceType ->
            interfaceType.methods.filterNot(WinRtMethodDefinition::isStatic).forEach { method ->
                builder.addFunction(renderInterfaceProxyMethod(interfaceType, method))
            }
            interfaceType.properties.filterNot(WinRtPropertyDefinition::isStatic).forEach { property ->
                builder.addProperty(renderInterfaceProxyProperty(interfaceType, property))
            }
            interfaceType.events.filterNot(WinRtEventDefinition::isStatic).forEach { event ->
                builder.addProperty(renderEventProperty(event, eventInvokeDescriptor = null, abstract = false, override = true))
                renderInterfaceProxyEventFunctions(interfaceType, event).forEach(builder::addFunction)
            }
        }
        return builder.build()
    }

    internal fun KotlinTypeProjectionPlan.projectedSelfTypeName(): TypeName {
        val className = ClassName(packageName, type.name)
        return if (type.genericParameterCount == 0) {
            className
        } else {
            className.parameterizedBy((0 until type.genericParameterCount).map { index -> TypeVariableName("T$index") })
        }
    }

    internal fun renderInterfaceProxyMethod(
        slotInterfaceType: WinRtTypeDefinition,
        method: WinRtMethodDefinition,
    ): FunSpec {
        val returnBinding = renderAbiTypeBinding(method.returnTypeName)
        val parameterBindings = method.parameters.map { parameter ->
            KotlinProjectionAbiParameterBinding(
                name = parameter.name,
                typeBinding = renderAbiTypeBinding(parameter.typeName),
            )
        }
        val callPlan = requireAbiCallPlan(
            bindingName = "${slotInterfaceType.qualifiedName}.${method.name}",
            returnBinding = returnBinding,
            parameterBindings = parameterBindings,
        )
        val invocation = renderInlineAbiInvocation(
            invokeTargetExpression = "nativeObject",
            slotExpression = CodeBlock.of("%T.Metadata.%L", resolveTypeName(slotInterfaceType.qualifiedName), method.abiSlotConstantName(slotInterfaceType.methods)),
            callPlan = callPlan,
        ) ?: error("Generator interface proxy parity failed to emit ${method.name}")
        return FunSpec.builder(method.name)
            .addModifiers(KModifier.OVERRIDE)
            .addParameters(method.parameters.map { ParameterSpec.builder(it.name, resolveTypeName(it.typeName)).build() })
            .returns(resolveTypeName(method.returnTypeName))
            .addCode("%L\n", invocation)
            .build()
    }

    internal fun renderInterfaceProxyProperty(
        slotInterfaceType: WinRtTypeDefinition,
        property: WinRtPropertyDefinition,
    ): PropertySpec {
        val builder = PropertySpec.builder(
            property.name.replaceFirstChar(Char::lowercase),
            resolveTypeName(property.typeName),
        )
            .mutable(!property.isReadOnly)
            .addModifiers(KModifier.OVERRIDE)
        val getterCallPlan = requireAbiCallPlan(
            bindingName = "${slotInterfaceType.qualifiedName}.${property.name}.get",
            returnBinding = renderAbiTypeBinding(property.typeName),
            parameterBindings = emptyList(),
        )
        builder.getter(
            FunSpec.getterBuilder()
                .addCode(
                    "%L\n",
                    renderInlineAbiInvocation(
                        invokeTargetExpression = "nativeObject",
                        slotExpression = CodeBlock.of("%T.Metadata.%L", resolveTypeName(slotInterfaceType.qualifiedName), "${property.name.uppercase()}_GETTER_SLOT"),
                        callPlan = getterCallPlan,
                    ) ?: error("Generator interface proxy parity failed to emit getter ${property.name}"),
                )
                .build(),
        )
        if (!property.isReadOnly) {
            val setterCallPlan = requireAbiCallPlan(
                bindingName = "${slotInterfaceType.qualifiedName}.${property.name}.set",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                parameterBindings = listOf(KotlinProjectionAbiParameterBinding("value", renderAbiTypeBinding(property.typeName))),
            )
            builder.setter(
                FunSpec.setterBuilder()
                    .addParameter("value", resolveTypeName(property.typeName))
                    .addCode(
                        "%L\n",
                        renderInlineAbiInvocation(
                            invokeTargetExpression = "nativeObject",
                            slotExpression = CodeBlock.of("%T.Metadata.%L", resolveTypeName(slotInterfaceType.qualifiedName), "${property.name.uppercase()}_SETTER_SLOT"),
                            callPlan = setterCallPlan,
                        ) ?: error("Generator interface proxy parity failed to emit setter ${property.name}"),
                    )
                    .build(),
            )
        }
        return builder.build()
    }

    internal fun renderInterfaceProxyEventFunctions(
        slotInterfaceType: WinRtTypeDefinition,
        event: WinRtEventDefinition,
    ): List<FunSpec> {
        val addCallPlan = requireAbiCallPlan(
            bindingName = "${slotInterfaceType.qualifiedName}.${event.name}.add",
            returnBinding = renderAbiTypeBinding("Windows.Foundation.EventRegistrationToken"),
            parameterBindings = listOf(KotlinProjectionAbiParameterBinding("handler", renderAbiTypeBinding(event.delegateTypeName))),
        )
        val removeCallPlan = requireAbiCallPlan(
            bindingName = "${slotInterfaceType.qualifiedName}.${event.name}.remove",
            returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
            parameterBindings = listOf(KotlinProjectionAbiParameterBinding("token", renderAbiTypeBinding("Windows.Foundation.EventRegistrationToken"))),
        )
        return buildBoundEventFunctions(
            event = event,
            eventInvokeDescriptor = null,
            addInvocation = renderInlineAbiInvocation(
                invokeTargetExpression = "nativeObject",
                slotExpression = CodeBlock.of("%T.Metadata.%L", resolveTypeName(slotInterfaceType.qualifiedName), "${event.name.uppercase()}_ADD_SLOT"),
                callPlan = addCallPlan,
            ) ?: error("Generator interface proxy parity failed to emit add ${event.name}"),
            removeInvocation = renderInlineAbiInvocation(
                invokeTargetExpression = "nativeObject",
                slotExpression = CodeBlock.of("%T.Metadata.%L", resolveTypeName(slotInterfaceType.qualifiedName), "${event.name.uppercase()}_REMOVE_SLOT"),
                callPlan = removeCallPlan,
            ) ?: error("Generator interface proxy parity failed to emit remove ${event.name}"),
            override = true,
        )
    }

    internal fun canRenderInterfaceProxy(plan: KotlinTypeProjectionPlan): Boolean =
        collectInterfaceProxyTypes(plan).all { interfaceType ->
            interfaceType.methods.filterNot(WinRtMethodDefinition::isStatic).all { method ->
                runCatching {
                    buildAbiCallPlan(
                        returnBinding = renderAbiTypeBinding(method.returnTypeName),
                        parameterBindings = method.parameters.map { parameter ->
                            KotlinProjectionAbiParameterBinding(parameter.name, renderAbiTypeBinding(parameter.typeName))
                        },
                    ) != null
                }.getOrDefault(false)
            } &&
                interfaceType.properties.filterNot(WinRtPropertyDefinition::isStatic).all { property ->
                    runCatching {
                        buildAbiCallPlan(
                            returnBinding = renderAbiTypeBinding(property.typeName),
                            parameterBindings = emptyList(),
                        ) != null
                    }.getOrDefault(false) &&
                        (
                            property.isReadOnly ||
                                runCatching {
                                    buildAbiCallPlan(
                                        returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                                        parameterBindings = listOf(KotlinProjectionAbiParameterBinding("value", renderAbiTypeBinding(property.typeName))),
                                    ) != null
                                }.getOrDefault(false)
                            )
                } &&
                interfaceType.events.filterNot(WinRtEventDefinition::isStatic).all { event ->
                    runCatching {
                        buildAbiCallPlan(
                            returnBinding = renderAbiTypeBinding("Windows.Foundation.EventRegistrationToken"),
                            parameterBindings = listOf(KotlinProjectionAbiParameterBinding("handler", renderAbiTypeBinding(event.delegateTypeName))),
                        ) != null
                    }.getOrDefault(false) &&
                        runCatching {
                            buildAbiCallPlan(
                                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                                parameterBindings = listOf(KotlinProjectionAbiParameterBinding("token", renderAbiTypeBinding("Windows.Foundation.EventRegistrationToken"))),
                            ) != null
                        }.getOrDefault(false)
                }
        }

    private fun collectInterfaceProxyTypes(plan: KotlinTypeProjectionPlan): List<WinRtTypeDefinition> =
        collectInterfaceProxyTypes(plan.type, plan, linkedSetOf())

    private fun collectInterfaceProxyTypes(
        interfaceType: WinRtTypeDefinition,
        plan: KotlinTypeProjectionPlan,
        visited: MutableSet<String>,
    ): List<WinRtTypeDefinition> {
        if (!visited.add(interfaceType.qualifiedName)) {
            return emptyList()
        }
        return buildList {
            interfaceType.implementedInterfaces.forEach { implemented ->
                plan.typesByQualifiedName[implemented.interfaceName.substringBefore('<').removeSuffix("?")]?.let { baseType ->
                    addAll(collectInterfaceProxyTypes(baseType, plan, visited))
                }
            }
            add(interfaceType)
        }
    }

    internal fun renderAbiTypeBinding(typeName: String): KotlinProjectionAbiTypeBinding {
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
            "Any",
            "System.Object" -> KotlinProjectionAbiValueKind.Object
            else -> mappedType?.abiValueKind ?: KotlinProjectionAbiValueKind.Unsupported
        }
        return KotlinProjectionAbiTypeBinding(
            kind = kind,
            typeName = trimmed,
            resolvedTypeName = rawTypeName,
            typeArguments = typeArguments,
        )
    }

    internal fun renderClassShell(plan: KotlinTypeProjectionPlan): TypeSpec = when {
        KotlinProjectionSpecializationKind.AttributeClass in plan.specializationKinds -> renderAttributeClassShell(plan)
        KotlinProjectionSpecializationKind.StaticClass in plan.specializationKinds -> renderStaticClassShell(plan)
        else -> renderRuntimeClassShell(plan)
    }

    internal fun renderRuntimeClassShell(plan: KotlinTypeProjectionPlan): TypeSpec {
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
        val objectReferencePlansByInterface = plan.objectReferenceSurfaceDescriptor
            ?.objectReferencePlans
            .orEmpty()
            .associateBy { it.interfaceName.substringBefore('<') }
        val defaultObjectReferencePlan = plan.defaultInterfaceName
            ?.substringBefore('<')
            ?.let(objectReferencePlansByInterface::get)
        if (plan.defaultInterfaceIid != null && defaultObjectReferencePlan?.skippedReason == null) {
            val defaultCacheType = if (defaultObjectReferencePlan?.usesInner == true) {
                COM_OBJECT_REFERENCE_CLASS_NAME
            } else {
                IUNKNOWN_REFERENCE_CLASS_NAME
            }
            builder.addProperty(
                PropertySpec.builder("_defaultInterface", defaultCacheType)
                    .addModifiers(KModifier.PRIVATE)
                    .apply {
                        if (defaultObjectReferencePlan?.usesInner == true) {
                            getter(
                                FunSpec.getterBuilder()
                                    .addCode("return _inner\n")
                                    .build(),
                            )
                        } else {
                            delegate(
                                CodeBlock.of(
                                    "lazy(%T.PUBLICATION) { Metadata.acquireDefaultInterface(_inner) }",
                                    LAZY_THREAD_SAFETY_MODE_CLASS_NAME,
                                ),
                            )
                        }
                    }
                    .build(),
            )
        }
        plan.implementedInterfaceBindings
            .filter { it.iid != null }
            .filter { binding ->
                objectReferencePlansByInterface[binding.qualifiedName.substringBefore('<')]?.skippedReason == null
            }
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
        requiredInterfaceCacheBindings(plan)
            .filter { it.iid != null }
            .forEach { binding ->
                builder.addProperty(
                    PropertySpec.builder(
                        "_${binding.qualifiedName.substringBefore('<').substringAfterLast('.').replaceFirstChar(Char::lowercase)}",
                        IUNKNOWN_REFERENCE_CLASS_NAME,
                    )
                        .addModifiers(KModifier.PRIVATE)
                        .delegate(
                            CodeBlock.of(
                                "lazy(%T.PUBLICATION) { Metadata.acquireInterface(_inner, %T.Metadata.IID) }",
                                LAZY_THREAD_SAFETY_MODE_CLASS_NAME,
                                projectionClassName(binding.qualifiedName.substringBefore('<')),
                            ),
                        )
                        .build(),
                )
            }
        plan.defaultInterfaceName
            ?.takeUnless(::isMappedCollectionInterfaceName)
            ?.let { defaultInterfaceName ->
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
            builder.addSuperinterface(mutableCollectionProjectedType(binding))
            addMutableCollectionForwardMembers(builder, binding)
        }
        plan.readOnlyCollectionBindings.forEach { binding ->
            builder.addProperty(renderReadOnlyCollectionDelegateProperty(binding))
            builder.addSuperinterface(readOnlyCollectionProjectedType(binding))
            when (binding.kind) {
                KotlinProjectionReadOnlyCollectionKind.Iterable -> {
                    val elementType = resolveTypeName(requireNotNull(binding.elementBinding).typeName)
                    builder.addFunction(
                        FunSpec.builder("iterator")
                            .addModifiers(KModifier.OVERRIDE)
                            .returns(Iterator::class.asClassName().parameterizedBy(elementType))
                            .addCode("return %L.iterator()\n", binding.delegatePropertyName)
                            .build(),
                    )
                }
                KotlinProjectionReadOnlyCollectionKind.VectorView -> {
                    val elementType = resolveTypeName(requireNotNull(binding.elementBinding).typeName)
                    builder.addProperty(
                        PropertySpec.builder("size", Int::class)
                            .addModifiers(KModifier.OVERRIDE)
                            .getter(FunSpec.getterBuilder().addCode("return %L.size\n", binding.delegatePropertyName).build())
                            .build(),
                    )
                    builder.addFunction(
                        FunSpec.builder("get")
                            .addModifiers(KModifier.OVERRIDE)
                            .addParameter("index", Int::class)
                            .returns(elementType)
                            .addCode("return %L[index]\n", binding.delegatePropertyName)
                            .build(),
                    )
                }
                KotlinProjectionReadOnlyCollectionKind.MapView -> {
                    val keyType = resolveTypeName(requireNotNull(binding.keyBinding).typeName)
                    val valueType = resolveTypeName(requireNotNull(binding.valueBinding).typeName)
                    val entryType = Map.Entry::class.asClassName().parameterizedBy(keyType, valueType)
                    builder.addProperty(
                        PropertySpec.builder("entries", Set::class.asClassName().parameterizedBy(entryType))
                            .addModifiers(KModifier.OVERRIDE)
                            .getter(FunSpec.getterBuilder().addCode("return %L.entries\n", binding.delegatePropertyName).build())
                            .build(),
                    )
                    builder.addFunction(
                        FunSpec.builder("containsKey")
                            .addModifiers(KModifier.OVERRIDE)
                            .addParameter("key", keyType)
                            .returns(Boolean::class)
                            .addCode("return %L.containsKey(key)\n", binding.delegatePropertyName)
                            .build(),
                    )
                    builder.addFunction(
                        FunSpec.builder("get")
                            .addModifiers(KModifier.OVERRIDE)
                            .addParameter("key", keyType)
                            .returns(valueType.copy(nullable = true))
                            .addCode("return %L[key]\n", binding.delegatePropertyName)
                            .build(),
                    )
                }
            }
        }
        if (plan.usesMappedDataErrorInfoAugmentation && !plan.hasDirectMappedDataErrorInfoSuperinterface) {
            builder.addSuperinterface(WINRT_DATA_ERROR_INFO_CLASS_NAME)
        }
        if (plan.usesMappedDataErrorInfoAugmentation) {
            addMappedDataErrorInfoForwardMembers(builder, plan)
        }
        if (plan.usesMappedDisposableAugmentation && !plan.hasDirectMappedDisposableSuperinterface) {
            builder.addSuperinterface(AUTO_CLOSEABLE_CLASS_NAME)
        }
        requiredIteratorBinding(plan)?.let { iteratorBinding ->
            addRequiredIteratorForwardMembers(builder, iteratorBinding)
        }
        renderRequiredInterfaceForwardMembers(plan, mappedCollectionMemberNames(plan)).forEach { member ->
            when (member) {
                is TypeSpec -> error("Nested required-interface members are not supported.")
                is FunSpec -> builder.addFunction(member)
                is PropertySpec -> builder.addProperty(member)
                else -> Unit
            }
        }
        builder.addSuperinterface(IWINRT_OBJECT_CLASS_NAME)
        if (KotlinProjectionCompanionKind.ActivationFactory in plan.companionKinds) {
            builder.addFunction(
                FunSpec.constructorBuilder()
                    .callThisConstructor(CodeBlock.of("%T.activateInstance(Metadata.TYPE_NAME)", ACTIVATION_FACTORY_CLASS_NAME))
                    .build(),
            )
            renderFactoryConstructors(plan).forEach(builder::addFunction)
        }
        renderComposableConstructors(plan).forEach(builder::addFunction)
        val mappedCollectionMemberNames = mappedCollectionMemberNames(plan)
        plan.type.methods
            .filterNot { it.isStatic }
            .filterNot { it.name in mappedCollectionMemberNames }
            .filterNot { plan.usesMappedDisposableAugmentation && it.name == "Close" }
            .filterNot { plan.usesMappedDataErrorInfoAugmentation && it.name == "GetErrors" }
            .forEach { builder.addFunction(renderRuntimeMethod(plan, it)) }
        plan.type.properties
            .filterNot { it.isStatic }
            .filterNot { it.name in mappedCollectionMemberNames }
            .filterNot { plan.usesMappedDataErrorInfoAugmentation && it.name == "HasErrors" }
            .forEach { builder.addProperty(renderRuntimeProperty(plan, it)) }
        plan.type.events
            .filterNot { it.isStatic }
            .filterNot { plan.usesMappedDataErrorInfoAugmentation && it.name == "ErrorsChanged" }
            .forEach { event ->
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
        if (plan.usesMappedDisposableAugmentation) {
            builder.addFunction(
                FunSpec.builder("close")
                    .addModifiers(KModifier.OVERRIDE)
                    .addCode("%T(_inner).close()\n", WINRT_CLOSABLE_OBJECT_CLASS_NAME)
                    .build(),
            )
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

    private fun addMutableCollectionForwardMembers(
        builder: TypeSpec.Builder,
        binding: KotlinProjectionMutableCollectionBinding,
    ) {
        when (binding.kind) {
            KotlinProjectionMutableCollectionKind.Vector -> addMutableListForwardMembers(builder, binding)
            KotlinProjectionMutableCollectionKind.Map -> addMutableMapForwardMembers(builder, binding)
        }
    }

    private fun addMutableListForwardMembers(
        builder: TypeSpec.Builder,
        binding: KotlinProjectionMutableCollectionBinding,
    ) {
        val elementType = resolveTypeName(requireNotNull(binding.elementBinding).typeName)
        val collectionType = Collection::class.asClassName().parameterizedBy(elementType)
        val listIteratorType = ListIterator::class.asClassName().parameterizedBy(elementType)
        val mutableListType = MutableList::class.asClassName().parameterizedBy(elementType)
        builder.addProperty(
            PropertySpec.builder("size", Int::class)
                .addModifiers(KModifier.OVERRIDE)
                .getter(FunSpec.getterBuilder().addCode("return %L.size\n", binding.delegatePropertyName).build())
                .build(),
        )
        fun forwardBoolean(name: String, parameterName: String, parameterType: TypeName) {
            builder.addFunction(
                FunSpec.builder(name)
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter(parameterName, parameterType)
                    .returns(Boolean::class)
                    .addCode("return %L.%L(%L)\n", binding.delegatePropertyName, name, parameterName)
                    .build(),
            )
        }
        forwardBoolean("contains", "element", elementType)
        forwardBoolean("containsAll", "elements", collectionType)
        forwardBoolean("add", "element", elementType)
        forwardBoolean("addAll", "elements", collectionType)
        builder.addFunction(
            FunSpec.builder("addAll")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("index", Int::class)
                .addParameter("elements", collectionType)
                .returns(Boolean::class)
                .addCode("return %L.addAll(index, elements)\n", binding.delegatePropertyName)
                .build(),
        )
        forwardBoolean("remove", "element", elementType)
        forwardBoolean("removeAll", "elements", collectionType)
        forwardBoolean("retainAll", "elements", collectionType)
        builder.addFunction(FunSpec.builder("isEmpty").addModifiers(KModifier.OVERRIDE).returns(Boolean::class).addCode("return %L.isEmpty()\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("iterator").addModifiers(KModifier.OVERRIDE).returns(MutableIterator::class.asClassName().parameterizedBy(elementType)).addCode("return %L.iterator()\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("listIterator").addModifiers(KModifier.OVERRIDE).returns(MutableListIterator::class.asClassName().parameterizedBy(elementType)).addCode("return %L.listIterator()\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("listIterator").addModifiers(KModifier.OVERRIDE).addParameter("index", Int::class).returns(MutableListIterator::class.asClassName().parameterizedBy(elementType)).addCode("return %L.listIterator(index)\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("subList").addModifiers(KModifier.OVERRIDE).addParameter("fromIndex", Int::class).addParameter("toIndex", Int::class).returns(mutableListType).addCode("return %L.subList(fromIndex, toIndex)\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("get").addModifiers(KModifier.OVERRIDE).addParameter("index", Int::class).returns(elementType).addCode("return %L[index]\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("indexOf").addModifiers(KModifier.OVERRIDE).addParameter("element", elementType).returns(Int::class).addCode("return %L.indexOf(element)\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("lastIndexOf").addModifiers(KModifier.OVERRIDE).addParameter("element", elementType).returns(Int::class).addCode("return %L.lastIndexOf(element)\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("add").addModifiers(KModifier.OVERRIDE).addParameter("index", Int::class).addParameter("element", elementType).addCode("%L.add(index, element)\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("clear").addModifiers(KModifier.OVERRIDE).addCode("%L.clear()\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("removeAt").addModifiers(KModifier.OVERRIDE).addParameter("index", Int::class).returns(elementType).addCode("return %L.removeAt(index)\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("set").addModifiers(KModifier.OVERRIDE).addParameter("index", Int::class).addParameter("element", elementType).returns(elementType).addCode("return %L.set(index, element)\n", binding.delegatePropertyName).build())
    }

    private fun addMutableMapForwardMembers(
        builder: TypeSpec.Builder,
        binding: KotlinProjectionMutableCollectionBinding,
    ) {
        val keyType = resolveTypeName(requireNotNull(binding.keyBinding).typeName)
        val valueType = resolveTypeName(requireNotNull(binding.valueBinding).typeName)
        val entryType = MutableMap.MutableEntry::class.asClassName().parameterizedBy(keyType, valueType)
        builder.addProperty(PropertySpec.builder("size", Int::class).addModifiers(KModifier.OVERRIDE).getter(FunSpec.getterBuilder().addCode("return %L.size\n", binding.delegatePropertyName).build()).build())
        builder.addProperty(PropertySpec.builder("entries", MutableSet::class.asClassName().parameterizedBy(entryType)).addModifiers(KModifier.OVERRIDE).getter(FunSpec.getterBuilder().addCode("return %L.entries\n", binding.delegatePropertyName).build()).build())
        builder.addProperty(PropertySpec.builder("keys", MutableSet::class.asClassName().parameterizedBy(keyType)).addModifiers(KModifier.OVERRIDE).getter(FunSpec.getterBuilder().addCode("return %L.keys\n", binding.delegatePropertyName).build()).build())
        builder.addProperty(PropertySpec.builder("values", MutableCollection::class.asClassName().parameterizedBy(valueType)).addModifiers(KModifier.OVERRIDE).getter(FunSpec.getterBuilder().addCode("return %L.values\n", binding.delegatePropertyName).build()).build())
        builder.addFunction(FunSpec.builder("containsKey").addModifiers(KModifier.OVERRIDE).addParameter("key", keyType).returns(Boolean::class).addCode("return %L.containsKey(key)\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("containsValue").addModifiers(KModifier.OVERRIDE).addParameter("value", valueType).returns(Boolean::class).addCode("return %L.containsValue(value)\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("get").addModifiers(KModifier.OVERRIDE).addParameter("key", keyType).returns(valueType.copy(nullable = true)).addCode("return %L[key]\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("isEmpty").addModifiers(KModifier.OVERRIDE).returns(Boolean::class).addCode("return %L.isEmpty()\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("clear").addModifiers(KModifier.OVERRIDE).addCode("%L.clear()\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("put").addModifiers(KModifier.OVERRIDE).addParameter("key", keyType).addParameter("value", valueType).returns(valueType.copy(nullable = true)).addCode("return %L.put(key, value)\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("putAll").addModifiers(KModifier.OVERRIDE).addParameter("from", Map::class.asClassName().parameterizedBy(keyType, valueType)).addCode("%L.putAll(from)\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("remove").addModifiers(KModifier.OVERRIDE).addParameter("key", keyType).returns(valueType.copy(nullable = true)).addCode("return %L.remove(key)\n", binding.delegatePropertyName).build())
    }

    private val KotlinTypeProjectionPlan.usesMappedDisposableAugmentation: Boolean
        get() = requiredInterfaceAugmentationDescriptor?.mappedAugmentationMembers.orEmpty().contains("IDisposable")

    private val KotlinTypeProjectionPlan.hasDirectMappedDisposableSuperinterface: Boolean
        get() = sequenceOf(defaultInterfaceName)
            .plus(type.implementedInterfaces.map { it.interfaceName })
            .filterNotNull()
            .map { it.substringBefore('<').removeSuffix("?") }
            .mapNotNull(::mappedTypeByAbiName)
            .any { it.descriptionName == "IClosable" }

    private val KotlinTypeProjectionPlan.usesMappedDataErrorInfoAugmentation: Boolean
        get() = requiredInterfaceAugmentationDescriptor?.mappedAugmentationMembers.orEmpty().contains("INotifyDataErrorInfo")

    private val KotlinTypeProjectionPlan.hasDirectMappedDataErrorInfoSuperinterface: Boolean
        get() = sequenceOf(defaultInterfaceName)
            .plus(type.implementedInterfaces.map { it.interfaceName })
            .filterNotNull()
            .map { it.substringBefore('<').removeSuffix("?") }
            .mapNotNull(::mappedTypeByAbiName)
            .any { it.descriptionName == "INotifyDataErrorInfo" }

    private fun addMappedDataErrorInfoForwardMembers(
        builder: TypeSpec.Builder,
        plan: KotlinTypeProjectionPlan,
    ) {
        builder.addProperty(
            PropertySpec.builder("__dataErrorInfo", WINRT_DATA_ERROR_INFO_CLASS_NAME)
                .addModifiers(KModifier.PRIVATE)
                .delegate(
                    CodeBlock.of(
                        "lazy(%T.PUBLICATION) { %T.fromAbi(%L) }",
                        LAZY_THREAD_SAFETY_MODE_CLASS_NAME,
                        WINRT_DATA_ERROR_INFO_PROJECTION_CLASS_NAME,
                        "_inner",
                    ),
                )
                .build(),
        )
        builder.addProperty(
            PropertySpec.builder("hasErrors", Boolean::class)
                .addModifiers(KModifier.OVERRIDE)
                .getter(FunSpec.getterBuilder().addCode("return __dataErrorInfo.hasErrors\n").build())
                .build(),
        )
        builder.addFunction(
            FunSpec.builder("getErrors")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("propertyName", String::class.asClassName().copy(nullable = true))
                .returns(Iterable::class.asClassName().parameterizedBy(ANY.copy(nullable = true)).copy(nullable = true))
                .addCode("return __dataErrorInfo.getErrors(propertyName)\n")
                .build(),
        )
        builder.addFunction(
            FunSpec.builder("addErrorsChanged")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("handler", WINRT_DATA_ERRORS_CHANGED_HANDLER_CLASS_NAME)
                .addCode("__dataErrorInfo.addErrorsChanged(handler)\n")
                .build(),
        )
        builder.addFunction(
            FunSpec.builder("removeErrorsChanged")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("handler", WINRT_DATA_ERRORS_CHANGED_HANDLER_CLASS_NAME)
                .addCode("__dataErrorInfo.removeErrorsChanged(handler)\n")
                .build(),
        )
    }

    private fun mappedCollectionMemberNames(plan: KotlinTypeProjectionPlan): Set<String> =
        if (plan.mutableCollectionBindings.isEmpty() && plan.readOnlyCollectionBindings.isEmpty() && requiredIteratorBinding(plan) == null) {
            emptySet()
        } else {
            setOf(
                "First",
                "Current",
                "HasCurrent",
                "MoveNext",
                "GetAt",
                "Size",
                "IndexOf",
                "SetAt",
                "InsertAt",
                "RemoveAt",
                "Append",
                "RemoveAtEnd",
                "Clear",
                "GetMany",
                "ReplaceAll",
                "GetView",
                "HasKey",
                "Lookup",
                "Insert",
                "Remove",
                "Split",
                "Key",
                "Value",
                "Current",
                "HasCurrent",
                "MoveNext",
            )
        }

    private data class RequiredIteratorBinding(
        val elementBinding: KotlinProjectionAbiTypeBinding,
        val ownerCachePropertyName: String,
    )

    private fun requiredIteratorBinding(plan: KotlinTypeProjectionPlan): RequiredIteratorBinding? {
        if (plan.type.kind != WinRtTypeKind.RuntimeClass) {
            return null
        }
        return plan.type.implementedInterfaces
            .flatMap { implemented -> collectRequiredForwardInterfaceTypes(implemented.interfaceName, plan, mutableSetOf()) }
            .firstOrNull { required ->
                mappedTypeByAbiName(required.interfaceName.substringBefore('<').removeSuffix("?"))?.descriptionName == "Iterator" &&
                    required.genericArguments.size == 1
            }
            ?.let { required ->
                required.genericArguments.single()
                    .normalized()
                    .typeName
                    .let(::renderAbiTypeBinding)
                    .takeIf(KotlinProjectionAbiTypeBinding::isSupportedReadOnlyCollectionElementBinding)
                    ?.let { elementBinding ->
                        RequiredIteratorBinding(
                            elementBinding = elementBinding,
                            ownerCachePropertyName = requiredForwardOwnerCache(required.interfaceName, plan.defaultInterfaceName),
                        )
                    }
            }
    }

    private fun addRequiredIteratorForwardMembers(
        builder: TypeSpec.Builder,
        iteratorBinding: RequiredIteratorBinding,
    ) {
        val elementBinding = iteratorBinding.elementBinding
        val elementType = resolveTypeName(elementBinding.typeName)
        builder.addSuperinterface(Iterator::class.asClassName().parameterizedBy(elementType))
        builder.addProperty(
            PropertySpec.builder("__iteratorInitialized", Boolean::class)
                .addModifiers(KModifier.PRIVATE)
                .mutable(true)
                .initializer("false")
                .build(),
        )
        builder.addProperty(
            PropertySpec.builder("__iteratorHasCurrent", Boolean::class)
                .addModifiers(KModifier.PRIVATE)
                .mutable(true)
                .initializer("false")
                .build(),
        )
        builder.addFunction(
            FunSpec.builder("hasNext")
                .addModifiers(KModifier.OVERRIDE)
                .returns(Boolean::class)
                .addCode("__ensureIteratorInitialized()\nreturn __iteratorHasCurrent\n")
                .build(),
        )
        builder.addFunction(
            FunSpec.builder("next")
                .addModifiers(KModifier.OVERRIDE)
                .returns(elementType)
                .addCode(
                    """
                    __ensureIteratorInitialized()
                    if (!__iteratorHasCurrent) {
                        throw %T()
                    }
                    val __current = __iteratorCurrent()
                    __iteratorHasCurrent = __iteratorMoveNext()
                    return __current
                    """.trimIndent() + "\n",
                    NO_SUCH_ELEMENT_EXCEPTION_CLASS_NAME,
                )
                .build(),
        )
        builder.addFunction(
            FunSpec.builder("__ensureIteratorInitialized")
                .addModifiers(KModifier.PRIVATE)
                .addCode(
                    """
                    if (__iteratorInitialized) {
                        return
                    }
                    __iteratorInitialized = true
                    __iteratorHasCurrent = __readIteratorHasCurrent()
                    """.trimIndent() + "\n",
                )
                .build(),
        )
        builder.addFunction(
            FunSpec.builder("__iteratorCurrent")
                .addModifiers(KModifier.PRIVATE)
                .returns(elementType)
                .addCode(
                    "%L\n",
                    renderCollectionInvocation(
                        invokeTargetExpression = iteratorBinding.ownerCachePropertyName,
                        slotInterfaceQualifiedName = "Windows.Foundation.Collections.IIterator",
                        slotConstantName = "CURRENT_GETTER_SLOT",
                        returnBinding = elementBinding,
                    ),
                )
                .build(),
        )
        builder.addFunction(
            FunSpec.builder("__readIteratorHasCurrent")
                .addModifiers(KModifier.PRIVATE)
                .returns(Boolean::class)
                .addCode(
                    "%L\n",
                    renderCollectionInvocation(
                        invokeTargetExpression = iteratorBinding.ownerCachePropertyName,
                        slotInterfaceQualifiedName = "Windows.Foundation.Collections.IIterator",
                        slotConstantName = "HASCURRENT_GETTER_SLOT",
                        returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Boolean, "Boolean"),
                    ),
                )
                .build(),
        )
        builder.addFunction(
            FunSpec.builder("__iteratorMoveNext")
                .addModifiers(KModifier.PRIVATE)
                .returns(Boolean::class)
                .addCode(
                    "%L\n",
                    renderCollectionInvocation(
                        invokeTargetExpression = iteratorBinding.ownerCachePropertyName,
                        slotInterfaceQualifiedName = "Windows.Foundation.Collections.IIterator",
                        slotConstantName = "MOVENEXT_SLOT",
                        returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Boolean, "Boolean"),
                    ),
                )
                .build(),
        )
    }

    private fun requiredInterfaceCacheBindings(plan: KotlinTypeProjectionPlan): List<KotlinProjectionInterfaceBinding> {
        if (plan.type.kind != WinRtTypeKind.RuntimeClass) {
            return emptyList()
        }
        val existingCacheNames = buildSet {
            plan.defaultInterfaceName?.let(::add)
            plan.implementedInterfaceBindings.mapTo(this) { it.qualifiedName }
        }
        return plan.type.implementedInterfaces
            .flatMap { implemented ->
                collectRequiredForwardInterfaceTypes(implemented.interfaceName, plan, mutableSetOf())
            }
            .filterNot { required ->
                val mappedType = mappedTypeByAbiName(required.interfaceName.substringBefore('<').removeSuffix("?"))
                mappedType?.descriptionName == "INotifyDataErrorInfo" ||
                    mappedType?.descriptionName == "IClosable" ||
                    mappedType?.abiValueKind == KotlinProjectionAbiValueKind.MappedBindableIterable ||
                    mappedType?.abiValueKind == KotlinProjectionAbiValueKind.MappedBindableVectorView ||
                    mappedType?.abiValueKind == KotlinProjectionAbiValueKind.MappedBindableVector
            }
            .filterNot { required -> required.interfaceName in existingCacheNames }
            .distinctBy { required -> required.interfaceName.substringBefore('<') }
            .map { required ->
                KotlinProjectionInterfaceBinding(
                    qualifiedName = required.interfaceName,
                    iid = required.type.iid,
                )
            }
    }

    internal fun renderAttributeClassShell(plan: KotlinTypeProjectionPlan): TypeSpec =
        TypeSpec.annotationBuilder(plan.type.name)
            .apply {
                addModifiers(renderVisibility(plan.visibility))
                addKdoc("attribute WinRT class shell\n")
            }
            .build()

    internal fun renderStaticClassShell(plan: KotlinTypeProjectionPlan): TypeSpec =
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

    internal fun renderEnumShell(plan: KotlinTypeProjectionPlan): TypeSpec =
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
                            enumConstantName(member.name),
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
                                        CodeBlock.builder()
                                            .beginControlFlow("%T.entries.forEach { entry ->", resolveTypeName(plan.type.qualifiedName))
                                            .beginControlFlow("if (entry.abiValue == value)")
                                            .addStatement("return entry")
                                            .endControlFlow()
                                            .endControlFlow()
                                            .addStatement("error(%S)", "Unknown ${plan.type.qualifiedName} ABI value: \$value")
                                            .build(),
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

    internal fun renderStruct(plan: KotlinTypeProjectionPlan): TypeSpec =
        TypeSpec.classBuilder(plan.type.name)
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
                renderStructMetadataCompanion(plan)?.let { companion ->
                    addInitializerBlock(CodeBlock.of("Metadata.register()\n"))
                    addType(companion)
                }
            }
            .build()

    internal fun renderStructMetadataCompanion(plan: KotlinTypeProjectionPlan): TypeSpec? {
        val fields = plan.type.fields.filterNot { it.isStatic || it.isLiteral }
        val fieldSpecs = fields.map { field ->
            nativeStructFieldSpec(field, plan.type.namespace, plan.typesByQualifiedName) ?: return null
        }
        val structTypeName = resolveTypeName(plan.type.qualifiedName)
        return TypeSpec.companionObjectBuilder("Metadata")
            .addSuperinterface(NATIVE_STRUCT_ADAPTER_CLASS_NAME.parameterizedBy(structTypeName))
            .addProperty(
                PropertySpec.builder("layout", NATIVE_STRUCT_LAYOUT_CLASS_NAME)
                    .addModifiers(KModifier.OVERRIDE)
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
                FunSpec.builder("register")
                    .addModifiers(KModifier.INTERNAL)
                    .addCode(
                        CodeBlock.builder()
                            .addStatement(
                                "%T.registerStruct(%T::class, %S, %S, this, emptyArray<%T>()::class)",
                                WINRT_VALUE_BOXING_REGISTRATION_CLASS_NAME,
                                resolveTypeName(plan.type.qualifiedName),
                                plan.type.qualifiedName,
                                nativeStructGuidSignature(plan) ?: error("Struct ${plan.type.qualifiedName} is missing a WinRT GUID signature."),
                                resolveTypeName(plan.type.qualifiedName),
                            )
                            .build(),
                    )
                    .build(),
            )
            .addFunction(
                FunSpec.builder("read")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("source", RAW_ADDRESS_CLASS_NAME)
                    .returns(structTypeName)
                    .addCode(nativeStructReadCode(plan, fields))
                    .build(),
            )
            .addFunction(
                FunSpec.builder("write")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("value", structTypeName)
                    .addParameter("destination", RAW_ADDRESS_CLASS_NAME)
                    .addCode(nativeStructWriteCode(fields))
                    .build(),
            )
            .addFunction(
                FunSpec.builder("fromAbi")
                    .addModifiers(KModifier.INTERNAL)
                    .addParameter("source", RAW_ADDRESS_CLASS_NAME)
                    .returns(structTypeName)
                    .addCode("return read(source)\n")
                    .build(),
            )
            .addFunction(
                FunSpec.builder("copyTo")
                    .addModifiers(KModifier.INTERNAL)
                    .addParameter("value", structTypeName)
                    .addParameter("destination", RAW_ADDRESS_CLASS_NAME)
                    .addCode("write(value, destination)\n")
                    .build(),
            )
            .build()
    }

    private fun nativeStructReadCode(plan: KotlinTypeProjectionPlan, fields: List<WinRtFieldDefinition>): CodeBlock =
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
            .build()

    private fun nativeStructWriteCode(fields: List<WinRtFieldDefinition>): CodeBlock =
        CodeBlock.builder()
            .apply {
                fields.forEach { field ->
                    add("%L\n", nativeStructFieldWriteCode(field, "value", "destination"))
                }
            }
            .build()

    private fun enumConstantName(name: String): String =
        if (name == "Metadata") "MetadataValue" else name

    internal fun nativeStructGuidSignature(plan: KotlinTypeProjectionPlan): String? =
        nativeStructGuidSignature(
            typeName = plan.type.qualifiedName,
            currentNamespace = plan.type.namespace,
            typesByQualifiedName = plan.typesByQualifiedName,
            visiting = emptySet(),
        )

    private fun nativeStructGuidSignature(
        typeName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
        visiting: Set<String>,
    ): String? {
        val qualifiedName = when {
            typesByQualifiedName.containsKey(typeName) -> typeName
            currentNamespace.isNotBlank() && typesByQualifiedName.containsKey("$currentNamespace.$typeName") -> "$currentNamespace.$typeName"
            else -> typesByQualifiedName.keys.firstOrNull { it.endsWith(".$typeName") }
        } ?: return mappedTypeByAbiName(typeName)?.abiQualifiedName?.let { mapped ->
            when (mapped) {
                "Windows.Foundation.EventRegistrationToken" -> "struct(Windows.Foundation.EventRegistrationToken;i8)"
                else -> null
            }
        }
        if (qualifiedName in visiting) {
            return null
        }
        val type = typesByQualifiedName[qualifiedName]?.takeIf { it.kind == WinRtTypeKind.Struct } ?: return null
        val fieldSignatures = type.fields
            .filterNot { it.isStatic || it.isLiteral }
            .map { field ->
                nativeStructFieldGuidSignature(field.typeName, type.namespace, typesByQualifiedName, visiting + qualifiedName) ?: return null
            }
        return "struct($qualifiedName;${fieldSignatures.joinToString(";")})"
    }

    private fun nativeStructFieldGuidSignature(
        typeName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
        visiting: Set<String>,
    ): String? =
        when (typeName) {
            "Byte",
            "Int8",
            "SByte" -> "i1"
            "UByte",
            "UInt8" -> "u1"
            "Short",
            "Int16" -> "i2"
            "UShort",
            "UInt16" -> "u2"
            "Int",
            "Int32" -> "i4"
            "UInt",
            "UInt32" -> "u4"
            "Long",
            "Int64" -> "i8"
            "ULong",
            "UInt64" -> "u8"
            "Float",
            "Single" -> "f4"
            "Double" -> "f8"
            "Char" -> "c2"
            "Guid",
            "System.Guid" -> "g16"
            else -> nativeStructGuidSignature(typeName, currentNamespace, typesByQualifiedName, visiting)
        }

    internal fun nativeStructFieldSpec(
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

    internal fun nativeStructScalarKind(typeName: String): String? = when (typeName) {
            "Byte",
            "SByte",
            "Int8",
            "UByte",
            "UInt8" -> "INT8"
            "Short",
            "Int16",
            "UShort",
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

    internal fun nativeNestedStructFieldTypeName(
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

    internal fun nativeStructFieldReadCode(field: WinRtFieldDefinition, sourceName: String): CodeBlock {
        val fieldName = field.name.replaceFirstChar(Char::lowercase)
        val slice = CodeBlock.of("layout.slice(%L, %S)", sourceName, fieldName)
        return when (field.typeName) {
            "Byte",
            "Int8" -> CodeBlock.of("%T.readInt8(%L)", PLATFORM_ABI_CLASS_NAME, slice)
            "SByte" -> CodeBlock.of("%T.readInt8(%L)", PLATFORM_ABI_CLASS_NAME, slice)
            "UByte",
            "UInt8" -> CodeBlock.of("%T.readInt8(%L).toUByte()", PLATFORM_ABI_CLASS_NAME, slice)
            "Short",
            "Int16" -> CodeBlock.of("%T.readInt16(%L)", PLATFORM_ABI_CLASS_NAME, slice)
            "UShort",
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

    internal fun nativeStructFieldWriteCode(field: WinRtFieldDefinition, valueName: String, destinationName: String): CodeBlock {
        val fieldName = field.name.replaceFirstChar(Char::lowercase)
        val value = CodeBlock.of("%L.%L", valueName, fieldName)
        val slice = CodeBlock.of("layout.slice(%L, %S)", destinationName, fieldName)
        return when (field.typeName) {
            "Byte",
            "Int8",
            "SByte" -> CodeBlock.of("%T.writeInt8(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, value)
            "UByte",
            "UInt8" -> CodeBlock.of("%T.writeInt8(%L, %L.toByte())", PLATFORM_ABI_CLASS_NAME, slice, value)
            "Short",
            "Int16" -> CodeBlock.of("%T.writeInt16(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, value)
            "UShort",
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

    internal fun renderDelegate(plan: KotlinTypeProjectionPlan): TypeSpec {
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
}
