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
import io.github.composefluent.winrt.metadata.WinRtObjectReferencePlanDescriptor
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
import com.squareup.kotlinpoet.WildcardTypeName
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
    fun render(plan: KotlinTypeProjectionPlan): KotlinProjectionFile {
        val contents = FileSpec.builder(plan.packageName, plan.type.name)
            .addGeneratedProjectionSuppressions()
            .apply { addType(renderType(plan)) }
            .build()
            .toString()
        return KotlinProjectionFile(
            relativePath = plan.relativePath,
            packageName = plan.packageName,
            contents = contents,
        )
    }

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
        plan.type.methods.filter(WinRtMethodDefinition::isOrdinaryProjectedMethod).forEach { builder.addFunction(renderInterfaceMethod(it)) }
        plan.type.properties.filterNot { it.isStatic }.filter { it.getterMethodName != null }.forEach { builder.addProperty(renderInterfaceProperty(it)) }
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
        plan.mutableCollectionBindings.forEach { binding ->
            val nativeBinding = binding.copy(ownerCachePropertyName = "nativeObject")
            builder.addProperty(renderMutableCollectionDelegateProperty(nativeBinding))
            if (nativeBinding.kind == KotlinProjectionMutableCollectionKind.Map) {
                builder.addSuperinterface(mapIterableType(nativeBinding))
            }
            addMutableCollectionForwardMembers(builder, nativeBinding)
        }
        plan.readOnlyCollectionBindings
            .filterNot { readOnlyBinding ->
                plan.mutableCollectionBindings.any { mutableBinding -> mutableBinding.covers(readOnlyBinding) }
            }
            .forEach { binding ->
                val nativeBinding = binding.copy(ownerCachePropertyName = "nativeObject")
                builder.addProperty(renderReadOnlyCollectionDelegateProperty(nativeBinding))
                addReadOnlyCollectionForwardMembers(builder, nativeBinding)
            }
        if (plan.usesMappedDisposableAugmentation || plan.hasDirectMappedDisposableSuperinterface) {
            builder.addFunction(
                FunSpec.builder("close")
                    .addModifiers(KModifier.OVERRIDE)
                    .addCode(
                        "val __hr = %T.invoke(instance = nativeObject.pointer, slot = 6)\n%T(__hr).requireSuccess()\n",
                        COM_VTABLE_INVOKER_CLASS_NAME,
                        HRESULT_CLASS_NAME,
                    )
                    .build(),
            )
        }
        collectInterfaceProxyTypes(plan).forEach { interfaceType ->
            interfaceType.methods.filter(WinRtMethodDefinition::isOrdinaryProjectedMethod).forEach { method ->
                builder.addFunction(renderInterfaceProxyMethod(interfaceType, method, plan.typesByQualifiedName))
            }
            interfaceType.properties.filterNot(WinRtPropertyDefinition::isStatic).filter { it.getterMethodName != null }.forEach { property ->
                builder.addProperty(renderInterfaceProxyProperty(interfaceType, property, plan.typesByQualifiedName))
            }
            interfaceType.events.filterNot(WinRtEventDefinition::isStatic).forEach { event ->
                builder.addProperty(
                    renderEventProperty(
                        event = event,
                        eventInvokeDescriptor = null,
                        abstract = false,
                        override = true,
                        eventSourceOwnerTypeName = interfaceType.qualifiedName,
                        eventSourceObjectReference = CodeBlock.of("nativeObject"),
                        eventSourceAddSlot = metadataSlotExpression(interfaceType, "${event.name.uppercase()}_ADD_SLOT"),
                        fallbackToAddRemove = false,
                    ),
                )
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
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    ): FunSpec {
        val returnBinding = renderAbiTypeBinding(method.returnTypeName, typesByQualifiedName)
        val parameterBindings = method.parameters.map { parameter ->
            KotlinProjectionAbiParameterBinding(
                name = parameter.name,
                typeBinding = renderAbiTypeBinding(parameter.typeName, typesByQualifiedName),
            )
        }
        val callPlan = requireAbiCallPlan(
            bindingName = "${slotInterfaceType.qualifiedName}.${method.name}",
            returnBinding = returnBinding,
            parameterBindings = parameterBindings,
            suppressHResultCheck = method.isNoException,
        )
        val invocation = renderInlineAbiInvocation(
            invokeTargetExpression = "nativeObject",
            slotExpression = metadataSlotExpression(slotInterfaceType, method.abiSlotConstantName(slotInterfaceType.methods)),
            callPlan = callPlan,
        ) ?: error("Generator interface proxy parity failed to emit ${method.name}")
        val objectShape = closableMethodShape(slotInterfaceType, method) ?: runtimeObjectMethodShape(method)
        return FunSpec.builder(objectShape?.name ?: method.projectedMethodName())
            .addModifiers(KModifier.OVERRIDE)
            .addMethodGenericParameters(method, objectShape)
            .addParameters(objectShape?.parameters ?: method.parameters.map { ParameterSpec.builder(it.name, resolveTypeName(it.typeName)).build() })
            .returns(objectShape?.returnType ?: resolveTypeName(method.returnTypeName))
            .addCode("%L\n", invocation)
            .build()
    }

    internal fun renderInterfaceProxyProperty(
        slotInterfaceType: WinRtTypeDefinition,
        property: WinRtPropertyDefinition,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    ): PropertySpec {
        val builder = PropertySpec.builder(
            property.name.replaceFirstChar(Char::lowercase),
            resolveTypeName(property.typeName),
        )
            .mutable(!property.isReadOnly)
            .addModifiers(KModifier.OVERRIDE)
        val getterCallPlan = requireAbiCallPlan(
            bindingName = "${slotInterfaceType.qualifiedName}.${property.name}.get",
            returnBinding = renderAbiTypeBinding(property.typeName, typesByQualifiedName),
            parameterBindings = emptyList(),
            suppressHResultCheck = property.isNoException,
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
                parameterBindings = listOf(KotlinProjectionAbiParameterBinding("value", renderAbiTypeBinding(property.typeName, typesByQualifiedName))),
                suppressHResultCheck = property.isNoException,
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
        val typeName = resolveTypeName(event.delegateTypeName)
        val propertyName = event.name.replaceFirstChar(Char::lowercase)
        return listOf(
            FunSpec.builder("add${event.name}")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("handler", typeName)
                .returns(EVENT_REGISTRATION_TOKEN_CLASS_NAME)
                .addCode("return %L.add(handler)\n", propertyName)
                .build(),
            FunSpec.builder("remove${event.name}")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("token", EVENT_REGISTRATION_TOKEN_CLASS_NAME)
                .addCode("%L.remove(token)\n", propertyName)
                .build(),
        )
    }

    internal fun canRenderInterfaceProxy(plan: KotlinTypeProjectionPlan): Boolean =
        !isRuntimeOwnedMappedTypeName(plan.type.qualifiedName) &&
            mappedTypeByAbiName(plan.type.qualifiedName)?.abiValueKind != KotlinProjectionAbiValueKind.MappedKeyValuePair &&
            (!isMappedCollectionInterfaceName(plan.type.qualifiedName) || plan.readOnlyCollectionBindings.isNotEmpty() || plan.mutableCollectionBindings.isNotEmpty()) &&
            collectInterfaceProxyTypes(plan).all { interfaceType ->
            interfaceType.methods.filter(WinRtMethodDefinition::isOrdinaryProjectedMethod).all { method ->
                runCatching {
                    buildAbiCallPlan(
                        returnBinding = renderAbiTypeBinding(method.returnTypeName, plan.typesByQualifiedName),
                        parameterBindings = method.parameters.map { parameter ->
                            KotlinProjectionAbiParameterBinding(parameter.name, renderAbiTypeBinding(parameter.typeName, plan.typesByQualifiedName))
                        },
                    ) != null
                }.getOrDefault(false)
            } &&
                interfaceType.properties.filterNot(WinRtPropertyDefinition::isStatic).all { property ->
                    runCatching {
                        buildAbiCallPlan(
                                    returnBinding = renderAbiTypeBinding(property.typeName, plan.typesByQualifiedName),
                            parameterBindings = emptyList(),
                        ) != null
                    }.getOrDefault(false) &&
                        (
                            property.isReadOnly ||
                                runCatching {
                                    buildAbiCallPlan(
                                        returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                                        parameterBindings = listOf(KotlinProjectionAbiParameterBinding("value", renderAbiTypeBinding(property.typeName, plan.typesByQualifiedName))),
                                    ) != null
                                }.getOrDefault(false)
                            )
                } &&
                interfaceType.events.filterNot(WinRtEventDefinition::isStatic).all { event ->
                    event.addMethodName != null || event.addMethodRowId != null
                }
        }

    internal fun collectInterfaceProxyTypes(plan: KotlinTypeProjectionPlan): List<WinRtTypeDefinition> =
        collectInterfaceProxyTypes(plan.type, plan, linkedSetOf(), emptyList())

    private fun collectInterfaceProxyTypes(
        interfaceType: WinRtTypeDefinition,
        plan: KotlinTypeProjectionPlan,
        visited: MutableSet<String>,
        genericArguments: List<WinRtTypeRef>,
    ): List<WinRtTypeDefinition> {
        if (!visited.add(interfaceType.qualifiedName)) {
            return emptyList()
        }
        return buildList {
            interfaceType.implementedInterfaces.forEach { implemented ->
                val substitutedInterfaceName = implemented.interfaceType
                    .substituteTypeParameters(genericArguments)
                    .normalized()
                    .typeName
                val implementedRawName = substitutedInterfaceName.substringBefore('<').removeSuffix("?")
                val mappedType = mappedTypeByAbiName(implementedRawName)
                if (
                    isRuntimeOwnedMappedTypeName(implementedRawName) ||
                    isMappedCollectionInterfaceName(implementedRawName) ||
                    mappedType?.descriptionName == "Iterator"
                ) {
                    return@forEach
                }
                plan.typesByQualifiedName[implementedRawName]?.let { baseType ->
                    addAll(collectInterfaceProxyTypes(baseType, plan, visited, genericArgumentTypeRefs(substitutedInterfaceName)))
                }
            }
            add(interfaceType.substituteInterfaceProxyMembers(genericArguments))
        }
    }

    private fun WinRtTypeDefinition.substituteInterfaceProxyMembers(
        genericArguments: List<WinRtTypeRef>,
    ): WinRtTypeDefinition =
        if (genericArguments.isEmpty()) {
            this
        } else {
            copy(
                methods = methods.map { method ->
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
                },
                properties = properties.map { property ->
                    val substitutedType = property.type.substituteTypeParameters(genericArguments).normalized()
                    property.copy(typeName = substitutedType.typeName, typeSignature = substitutedType)
                },
                events = events.map { event ->
                    val substitutedType = event.delegateType.substituteTypeParameters(genericArguments).normalized()
                    event.copy(delegateTypeName = substitutedType.typeName, delegateTypeSignature = substitutedType)
                },
            )
        }

    private fun genericArgumentTypeRefs(typeName: String): List<WinRtTypeRef> {
        val trimmed = typeName.trim()
        if ('<' !in trimmed || !trimmed.endsWith('>')) {
            return emptyList()
        }
        return splitGenericArguments(trimmed.substringAfter('<').substringBeforeLast('>'))
            .map(WinRtTypeRef::fromDisplayName)
    }

    internal fun renderAbiTypeBinding(
        typeName: String,
        typesByQualifiedName: Map<String, WinRtTypeDefinition> = emptyMap(),
    ): KotlinProjectionAbiTypeBinding {
        val trimmed = typeName.trim()
        val rawTypeName = trimmed.substringBefore('<').removeSuffix("?")
        val typeArguments = if ('<' in trimmed && trimmed.endsWith('>')) {
            splitGenericArguments(trimmed.substringAfter('<').substringBeforeLast('>'))
                .map { renderAbiTypeBinding(it, typesByQualifiedName) }
        } else {
            emptyList()
        }
        val mappedType = mappedTypeByAbiName(rawTypeName)
        val resolvedType = typesByQualifiedName[rawTypeName]
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
            "io.github.composefluent.winrt.runtime.IUnknownReference" -> KotlinProjectionAbiValueKind.UnknownReference
            IINSPECTABLE_REFERENCE_CLASS_NAME.simpleName,
            "io.github.composefluent.winrt.runtime.IInspectableReference" -> KotlinProjectionAbiValueKind.InspectableReference
            "Any",
            "System.Object" -> KotlinProjectionAbiValueKind.Object
            else -> when {
                rawTypeName.isMethodGenericParameterName() -> KotlinProjectionAbiValueKind.GenericParameter
                mappedType?.abiValueKind != null -> mappedType.abiValueKind
                else -> when (resolvedType?.kind) {
                    WinRtTypeKind.Interface -> KotlinProjectionAbiValueKind.ProjectedInterface
                    WinRtTypeKind.RuntimeClass -> KotlinProjectionAbiValueKind.ProjectedRuntimeClass
                    WinRtTypeKind.Enum -> KotlinProjectionAbiValueKind.Enum
                    WinRtTypeKind.Struct -> KotlinProjectionAbiValueKind.Struct
                    WinRtTypeKind.Delegate -> KotlinProjectionAbiValueKind.Delegate
                    WinRtTypeKind.Unknown,
                    null -> KotlinProjectionAbiValueKind.Unsupported
                }
            }
        }
        val interfaceId = when (resolvedType?.kind) {
            WinRtTypeKind.RuntimeClass -> resolvedType.defaultInterfaceName
                ?.let { defaultInterfaceName ->
                    typesByQualifiedName[defaultInterfaceName]
                        ?: typesByQualifiedName[defaultInterfaceName.substringBefore('<').removeSuffix("?")]
                }
                ?.iid
            else -> resolvedType?.iid
        } ?: mappedReferenceGenericInterfaceId(kind)
        return KotlinProjectionAbiTypeBinding(
            kind = kind,
            typeName = trimmed,
            resolvedTypeName = rawTypeName,
            sourceTypeKind = resolvedType?.kind,
            interfaceId = interfaceId,
            enumUnderlyingType = resolvedType?.enumUnderlyingType,
            typeArguments = typeArguments,
        )
    }

    private fun String.isMethodGenericParameterName(): Boolean =
        startsWith("M") && drop(1).toIntOrNull() != null

    private fun mappedReferenceGenericInterfaceId(kind: KotlinProjectionAbiValueKind): Guid? =
        when (kind) {
            KotlinProjectionAbiValueKind.Reference -> IREFERENCE_GENERIC_INTERFACE_ID
            KotlinProjectionAbiValueKind.ReferenceArray -> IREFERENCE_ARRAY_GENERIC_INTERFACE_ID
            else -> null
        }

    internal fun renderClassShell(plan: KotlinTypeProjectionPlan): TypeSpec = when {
        KotlinProjectionSpecializationKind.AttributeClass in plan.specializationKinds -> renderAttributeClassShell(plan)
        KotlinProjectionSpecializationKind.StaticClass in plan.specializationKinds -> renderStaticClassShell(plan)
        else -> renderRuntimeClassShell(plan)
    }

    internal fun renderRuntimeClassShell(plan: KotlinTypeProjectionPlan): TypeSpec {
        val builder = TypeSpec.classBuilder(plan.type.name)
        applyCommonTypeShape(builder, plan, emitKotlinSealed = false)
        if (plan.hasRuntimeClassDerivedTypes && KotlinProjectionModifier.Sealed !in plan.modifiers) {
            builder.addModifiers(KModifier.OPEN)
        }
        if (KotlinProjectionModifier.Sealed in plan.modifiers) {
            builder.addKdoc(
                "WinRT sealed runtime class shell emitted as a regular Kotlin class because Kotlin sealed constructors would block RCW wrapping and activation.\n",
            )
        }
        val constructorBuilder = FunSpec.constructorBuilder()
            .addModifiers(KModifier.INTERNAL)
            .addParameter("_inner", IINSPECTABLE_REFERENCE_CLASS_NAME)
            .addParameter("__winrtWrapper", UNIT)
        val supportsDerivedComposableConstruction = plan.supportsDerivedComposableConstruction()
        plan.runtimeClassBaseTypeName?.let { baseTypeName ->
            builder.superclass(resolveTypeName(baseTypeName))
            builder.addSuperclassConstructorParameter("_inner")
            builder.addSuperclassConstructorParameter("kotlin.Unit")
        }
        if (supportsDerivedComposableConstruction) {
            builder.addProperty(
                PropertySpec.builder("_innerStorage", IINSPECTABLE_REFERENCE_CLASS_NAME.copy(nullable = true))
                    .addModifiers(KModifier.PRIVATE)
                    .mutable(true)
                    .initializer("null")
                    .build(),
            )
            builder.addProperty(
                PropertySpec.builder("_composableReference", WINRT_COMPOSABLE_OBJECT_REFERENCE_CLASS_NAME.copy(nullable = true))
                    .addModifiers(KModifier.PRIVATE)
                    .mutable(true)
                    .initializer("null")
                    .build(),
            )
            builder.addProperty(
                PropertySpec.builder("_inner", IINSPECTABLE_REFERENCE_CLASS_NAME)
                    .addModifiers(KModifier.PRIVATE)
                    .getter(
                        FunSpec.getterBuilder()
                            .addCode("return requireNotNull(_innerStorage) { %S }\n", "WinRT runtime class object reference is not initialized.")
                            .build(),
                    )
                    .build(),
            )
            builder.addFunction(
                constructorBuilder
                    .addStatement("this._innerStorage = _inner")
                    .build(),
            )
        } else {
            builder.primaryConstructor(constructorBuilder.build())
            builder.addProperty(
                PropertySpec.builder("_inner", IINSPECTABLE_REFERENCE_CLASS_NAME)
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("_inner")
                    .build(),
            )
        }
        builder.addProperty(
            PropertySpec.builder("nativeObject", COM_OBJECT_REFERENCE_CLASS_NAME)
                .addModifiers(KModifier.OVERRIDE)
                .apply {
                    if (plan.hasRuntimeClassDerivedTypes && KotlinProjectionModifier.Sealed !in plan.modifiers) {
                        addModifiers(KModifier.OPEN)
                    }
                }
                .getter(
                    FunSpec.getterBuilder()
                        .addCode("return _inner\n")
                        .build(),
                )
                .build(),
        )
        addRuntimeClassIdentityMembers(builder, plan)
        val objectReferencePlansByInterface = plan.objectReferenceSurfaceDescriptor
            ?.objectReferencePlans
            .orEmpty()
            .associateBy { it.interfaceName.substringBefore('<') }
        val defaultObjectReferencePlan = plan.defaultInterfaceName
            ?.substringBefore('<')
            ?.let(objectReferencePlansByInterface::get)
        val defaultInterfaceIsRuntimeOwnedMapped = plan.defaultInterfaceName
            ?.let(::isRuntimeOwnedMappedTypeName) == true
        if (!defaultInterfaceIsRuntimeOwnedMapped &&
            (plan.defaultInterfaceIid != null ||
                (defaultObjectReferencePlan != null && defaultObjectReferencePlan.skippedReason == null) ||
                isMappedCollectionInterfaceName(plan.defaultInterfaceName.orEmpty()))
        ) {
            val defaultCacheType = if (
                plan.runtimeClassBaseTypeName != null ||
                plan.hasRuntimeClassDerivedTypes ||
                defaultObjectReferencePlan?.usesInner == true ||
                defaultObjectReferencePlan?.usesDefaultInterfaceObjRef == true
            ) {
                COM_OBJECT_REFERENCE_CLASS_NAME
            } else {
                IUNKNOWN_REFERENCE_CLASS_NAME
            }
            builder.addProperty(
                PropertySpec.builder("_defaultInterface", defaultCacheType)
                    .addModifiers(KModifier.PRIVATE)
                    .apply {
                        if (defaultObjectReferencePlan != null) {
                            delegate(
                                runtimeClassObjectReferenceCacheInitializer(
                                    defaultObjectReferencePlan,
                                    plan.typesByQualifiedName,
                                    "Metadata.acquireInterface(_inner, %T.Metadata.IID)",
                                    projectionClassName(defaultObjectReferencePlan.interfaceName.substringBefore('<')),
                                ),
                            )
                        } else {
                            delegate(
                                runtimeClassObjectReferenceCacheInitializer(defaultObjectReferencePlan, plan.typesByQualifiedName, "Metadata.acquireDefaultInterface(_inner)"),
                            )
                        }
                    }
                    .build(),
            )
        }
        plan.implementedInterfaceBindings
            .filter { it.iid != null }
            .filterNot { isRuntimeOwnedMappedTypeName(it.qualifiedName) }
            .filter { binding ->
                objectReferencePlansByInterface[binding.qualifiedName.substringBefore('<')]?.skippedReason == null
            }
            .forEach { binding ->
                val objectReferencePlan = objectReferencePlansByInterface[binding.qualifiedName.substringBefore('<')]
                builder.addProperty(
                    PropertySpec.builder(
                        "_${binding.qualifiedName.substringBefore('<').substringAfterLast('.').replaceFirstChar(Char::lowercase)}",
                        IUNKNOWN_REFERENCE_CLASS_NAME,
                    )
                        .addModifiers(KModifier.PRIVATE)
                        .delegate(
                            runtimeClassObjectReferenceCacheInitializer(
                                objectReferencePlan,
                                plan.typesByQualifiedName,
                                "Metadata.acquireInterface(_inner, %T.Metadata.IID)",
                                projectionClassName(binding.qualifiedName.substringBefore('<')),
                            ),
                        )
                        .build(),
                )
        }
        requiredInterfaceCacheBindings(plan)
            .filter { it.iid != null }
            .filterNot { isRuntimeOwnedMappedTypeName(it.qualifiedName) }
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
            ?.takeUnless(::isRuntimeOwnedMappedTypeName)
            ?.takeIf { interfaceName -> plan.isPublicRuntimeClassInterface(interfaceName) }
            ?.let { defaultInterfaceName ->
            builder.addSuperinterface(resolveTypeName(defaultInterfaceName))
        }
        plan.type.implementedInterfaces
            .filterNot { it.isDefault }
            .filter { implemented -> plan.isPublicRuntimeClassInterface(implemented.interfaceName) }
            .filterNot { implemented ->
                isMappedCollectionInterfaceName(implemented.interfaceName)
            }
            .filterNot { implemented ->
                isRuntimeOwnedMappedTypeName(implemented.interfaceName)
            }
            .forEach { implemented -> builder.addSuperinterface(resolveTypeName(implemented.interfaceName)) }
        plan.mutableCollectionBindings.forEach { binding ->
            builder.addProperty(renderMutableCollectionDelegateProperty(binding))
            builder.addSuperinterface(mutableCollectionProjectedType(binding))
            if (binding.kind == KotlinProjectionMutableCollectionKind.Map) {
                builder.addSuperinterface(mapIterableType(binding))
            }
            addMutableCollectionForwardMembers(builder, binding)
        }
        val readOnlyCollectionBindings = plan.readOnlyCollectionBindings.filterNot { readOnlyBinding ->
            plan.mutableCollectionBindings.any { mutableBinding -> mutableBinding.covers(readOnlyBinding) }
        }
        readOnlyCollectionBindings.forEach { binding ->
            builder.addProperty(renderReadOnlyCollectionDelegateProperty(binding))
            builder.addSuperinterface(readOnlyCollectionProjectedType(binding))
            addReadOnlyCollectionForwardMembers(builder, binding)
        }
        if (plan.usesMappedDataErrorInfoAugmentation) {
            builder.addSuperinterface(WINRT_DATA_ERROR_INFO_CLASS_NAME)
        }
        if (plan.usesMappedDataErrorInfoAugmentation) {
            addMappedDataErrorInfoForwardMembers(builder, plan)
        }
        if (plan.usesMappedPropertyChangedAugmentation) {
            builder.addSuperinterface(WINRT_PROPERTY_CHANGED_NOTIFIER_CLASS_NAME)
            addMappedPropertyChangedForwardMembers(builder)
        }
        if (plan.usesMappedDisposableAugmentation) {
            builder.addSuperinterface(AUTO_CLOSEABLE_CLASS_NAME)
        }
        requiredIteratorBinding(plan)?.let { iteratorBinding ->
            addRequiredIteratorForwardMembers(builder, iteratorBinding)
        }
        addRuntimeClassInterfaceProjectionCaches(builder, plan)
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
                    .callThisConstructor(CodeBlock.of("%T.activateInstance(Metadata.TYPE_NAME), kotlin.Unit", ACTIVATION_FACTORY_CLASS_NAME))
                    .build(),
            )
            renderFactoryConstructors(plan).forEach(builder::addFunction)
        }
        renderComposableConstructors(plan).forEach(builder::addFunction)
        val mappedCollectionMemberNames = mappedCollectionMemberNames(plan)
        plan.type.methods
            .filter(WinRtMethodDefinition::isOrdinaryProjectedMethod)
            .filterNot { it.isMappedCollectionRuntimeMethod(plan, mappedCollectionMemberNames) }
            .filterNot { plan.usesMappedDisposableAugmentation && it.name == "Close" }
            .filterNot { plan.usesMappedDataErrorInfoAugmentation && it.name == "GetErrors" }
            .forEach { method ->
                builder.addFunction(renderRuntimeClassInterfaceForwardMethod(plan, method) ?: renderRuntimeMethod(plan, method))
            }
        plan.type.properties
            .filterNot { it.isStatic }
            .filter { it.getterMethodName != null }
            .filterNot { it.isMappedCollectionRuntimeProperty(plan, mappedCollectionMemberNames) }
            .filterNot { plan.usesMappedDataErrorInfoAugmentation && it.name == "HasErrors" }
            .forEach { property ->
                builder.addProperty(renderRuntimeClassInterfaceForwardProperty(plan, property) ?: renderRuntimeProperty(plan, property))
            }
        plan.type.events
            .filterNot { it.isStatic }
            .filterNot { plan.usesMappedDataErrorInfoAugmentation && it.name == "ErrorsChanged" }
            .filterNot { plan.usesMappedPropertyChangedAugmentation && it.name == "PropertyChanged" }
            .forEach { event ->
            val forwardEvent = renderRuntimeClassInterfaceForwardEvent(plan, event)
            if (forwardEvent != null) {
                builder.addProperty(forwardEvent.property)
                forwardEvent.functions.forEach(builder::addFunction)
                return@forEach
            }
            val addBinding = plan.instanceMemberBindings.firstOrNull {
                it.bindingName == "${event.name.uppercase()}_ADD_SLOT"
            }
            val eventModifiers = addBinding?.let { runtimeClassMemberModifiers(plan, it) } ?: listOf(KModifier.OVERRIDE)
            builder.addProperty(
                renderEventProperty(
                    event = event,
                    eventInvokeDescriptor = plan.eventInvokeDescriptors.firstOrNull { it.eventName == event.name && !it.isStatic },
                    abstract = false,
                    override = true,
                    modifiers = eventModifiers,
                    eventSourceOwnerTypeName = addBinding?.ownerInterfaceQualifiedName,
                    eventSourceObjectReference = addBinding?.let { CodeBlock.of(it.ownerCachePropertyName) },
                    eventSourceAddSlot = addBinding?.let {
                        CodeBlock.of("%T.Metadata.%L", resolveTypeName(it.slotInterfaceQualifiedName), it.slotConstantName)
                    },
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
        val staticMethods = plan.type.methods.filter(WinRtMethodDefinition::isOrdinaryProjectedStaticMethod)
        val staticProperties = plan.type.properties.filter { it.isStatic }
        val staticEvents = plan.type.events.filter { it.isStatic }
        if (staticMethods.isNotEmpty() || staticProperties.isNotEmpty() || staticEvents.isNotEmpty() ||
            KotlinProjectionCompanionKind.Metadata in plan.companionKinds) {
            builder.addType(buildMetadataCompanionShell(plan, staticMethods, staticProperties, staticEvents))
        }
        appendCompanionShells(builder, plan, excludeKinds = setOf(KotlinProjectionCompanionKind.Metadata))
        return builder.build()
    }

    private fun addRuntimeClassInterfaceProjectionCaches(
        builder: TypeSpec.Builder,
        plan: KotlinTypeProjectionPlan,
    ) {
        runtimeClassInterfaceProjectionForwardTargets(plan).values
            .distinctBy { it.projectionPropertyName }
            .sortedBy { it.projectionPropertyName }
            .forEach { target ->
                builder.addProperty(
                    PropertySpec.builder(target.projectionPropertyName, resolveTypeName(target.interfaceName))
                        .addModifiers(KModifier.PRIVATE)
                        .delegate(
                            CodeBlock.of(
                                "lazy(%T.PUBLICATION) { %T.Metadata.wrap(Metadata.acquireInterface(_inner, %T.Metadata.IID)) }",
                                LAZY_THREAD_SAFETY_MODE_CLASS_NAME,
                                resolveTypeName(target.interfaceName),
                                resolveTypeName(target.interfaceName),
                            ),
                        )
                        .build(),
                )
            }
    }

    private fun renderRuntimeClassInterfaceForwardMethod(
        plan: KotlinTypeProjectionPlan,
        method: WinRtMethodDefinition,
    ): FunSpec? {
        val binding = plan.instanceMemberBindings.firstOrNull { it.bindingName == method.abiSlotConstantName(plan.type.methods) }
            ?: return null
        val target = runtimeClassInterfaceProjectionForwardTargets(plan)[binding.ownerInterfaceQualifiedName.substringBefore('<')]
            ?: return null
        val objectShape = runtimeObjectMethodShape(method)
        val functionName = objectShape?.name ?: method.projectedMethodName()
        val parameterSpecs = objectShape?.parameters ?: method.parameters.map { parameter ->
            ParameterSpec.builder(parameter.name, resolveTypeName(parameter.typeName)).build()
        }
        val arguments = parameterSpecs.joinToString(", ") { parameter -> parameter.name }
        val returns = objectShape?.returnType ?: resolveTypeName(method.returnTypeName)
        return FunSpec.builder(functionName)
            .addProjectedAttributeAnnotations(binding.projectedAttributes)
            .addMethodGenericParameters(method, objectShape)
            .addModifiers(objectShape?.let { listOf(KModifier.OVERRIDE) } ?: runtimeClassMemberModifiers(plan, binding))
            .addParameters(parameterSpecs)
            .returns(returns)
            .apply {
                if (objectShape?.kind == RuntimeObjectMethodKind.Equals) {
                    addCode("if (other !is %T) return false\n", IWINRT_OBJECT_CLASS_NAME)
                }
                if (returns == UNIT) {
                    addCode("%L.%L(%L)\n", target.projectionPropertyName, functionName, arguments)
                } else {
                    addCode("return %L.%L(%L)\n", target.projectionPropertyName, functionName, arguments)
                }
            }
            .build()
    }

    private fun renderRuntimeClassInterfaceForwardProperty(
        plan: KotlinTypeProjectionPlan,
        property: WinRtPropertyDefinition,
    ): PropertySpec? {
        val getterBinding = plan.instanceMemberBindings.firstOrNull {
            it.bindingName == "${property.name.uppercase()}_GETTER_SLOT"
        } ?: return null
        val target = runtimeClassInterfaceProjectionForwardTargets(plan)[getterBinding.ownerInterfaceQualifiedName.substringBefore('<')]
            ?: return null
        val propertyName = property.name.replaceFirstChar(Char::lowercase)
        val builder = PropertySpec.builder(propertyName, resolveTypeName(property.typeName))
            .mutable(!property.isReadOnly)
            .addProjectedAttributeAnnotations(getterBinding.projectedAttributes)
            .addModifiers(runtimeClassMemberModifiers(plan, getterBinding))
            .getter(
                FunSpec.getterBuilder()
                    .addCode("return %L.%L\n", target.projectionPropertyName, propertyName)
                    .build(),
            )
        if (!property.isReadOnly) {
            builder.setter(
                FunSpec.setterBuilder()
                    .addParameter("value", resolveTypeName(property.typeName))
                    .addCode("%L.%L = value\n", target.projectionPropertyName, propertyName)
                    .build(),
            )
        }
        return builder.build()
    }

    private data class RuntimeClassForwardEvent(
        val property: PropertySpec,
        val functions: List<FunSpec>,
    )

    private fun renderRuntimeClassInterfaceForwardEvent(
        plan: KotlinTypeProjectionPlan,
        event: WinRtEventDefinition,
    ): RuntimeClassForwardEvent? {
        val addBinding = plan.instanceMemberBindings.firstOrNull {
            it.bindingName == "${event.name.uppercase()}_ADD_SLOT"
        } ?: return null
        val target = runtimeClassInterfaceProjectionForwardTargets(plan)[addBinding.ownerInterfaceQualifiedName.substringBefore('<')]
            ?: return null
        val eventName = event.name.replaceFirstChar(Char::lowercase)
        val delegateTypeName = resolveTypeName(event.delegateTypeName)
        val property = PropertySpec.builder(eventName, WINRT_EVENT_CLASS_NAME.parameterizedBy(delegateTypeName))
            .addModifiers(runtimeClassMemberModifiers(plan, addBinding))
            .getter(
                FunSpec.getterBuilder()
                    .addCode("return %L.%L\n", target.projectionPropertyName, eventName)
                    .build(),
            )
            .build()
        val functions = listOf(
            FunSpec.builder("add${event.name}")
                .addModifiers(runtimeClassMemberModifiers(plan, addBinding))
                .addParameter("handler", delegateTypeName)
                .returns(EVENT_REGISTRATION_TOKEN_CLASS_NAME)
                .addCode("return %L.add%L(handler)\n", target.projectionPropertyName, event.name)
                .build(),
            FunSpec.builder("remove${event.name}")
                .addModifiers(runtimeClassMemberModifiers(plan, addBinding))
                .addParameter("token", EVENT_REGISTRATION_TOKEN_CLASS_NAME)
                .addCode("%L.remove%L(token)\n", target.projectionPropertyName, event.name)
                .build(),
        )
        return RuntimeClassForwardEvent(property, functions)
    }

    private data class RuntimeClassInterfaceProjectionForwardTarget(
        val interfaceName: String,
        val projectionPropertyName: String,
    )

    private fun runtimeClassInterfaceProjectionForwardTargets(
        plan: KotlinTypeProjectionPlan,
    ): Map<String, RuntimeClassInterfaceProjectionForwardTarget> {
        val ownerInterfaceNames = plan.instanceMemberBindings
            .map { binding -> binding.ownerInterfaceQualifiedName.substringBefore('<').removeSuffix("?") }
            .distinct()
        return ownerInterfaceNames.mapNotNull { interfaceName ->
            if ('<' in interfaceName) {
                return@mapNotNull null
            }
            val interfaceType = plan.typesByQualifiedName[interfaceName] ?: return@mapNotNull null
            if (interfaceType.kind != WinRtTypeKind.Interface) {
                return@mapNotNull null
            }
            val interfacePlan = plan.copy(
                type = interfaceType,
                declarationKind = KotlinProjectionDeclarationKind.Interface,
                defaultInterfaceName = null,
                defaultInterfaceIid = null,
                staticInterfaceNames = emptyList(),
                staticInterfaceBindings = emptyList(),
                implementedInterfaceBindings = emptyList(),
                instanceMemberBindings = emptyList(),
                staticMemberBindings = emptyList(),
                classMemberMergeDescriptor = null,
            )
            if (!canRenderInterfaceProxy(interfacePlan)) {
                return@mapNotNull null
            }
            interfaceName to RuntimeClassInterfaceProjectionForwardTarget(
                interfaceName = interfaceName,
                projectionPropertyName = "_${interfaceType.name.replaceFirstChar(Char::lowercase)}Projection",
            )
        }.toMap()
    }

    private val KotlinTypeProjectionPlan.runtimeClassBaseTypeName: String?
        get() = type.baseTypeName
            ?.takeUnless { it == "System.Object" || it == "Any" }

    private fun KotlinTypeProjectionPlan.isPublicRuntimeClassInterface(interfaceName: String): Boolean {
        val rawName = interfaceName.substringBefore('<').removeSuffix("?")
        val descriptor = classMemberMergeDescriptor
            ?.interfaceDescriptors
            ?.firstOrNull { it.interfaceTypeName == rawName }
        return descriptor?.let { !it.isOverridableInterface && !it.isProtectedInterface } ?: true
    }

    private val KotlinTypeProjectionPlan.hasRuntimeClassDerivedTypes: Boolean
        get() =
            classMemberMergeDescriptor?.interfaceDescriptors?.any { descriptor ->
                descriptor.isOverridableInterface
            } == true ||
                typesByQualifiedName.values.any { candidate ->
                    candidate.kind == WinRtTypeKind.RuntimeClass &&
                        candidate.baseTypeName?.let { baseName ->
                            baseName == type.qualifiedName || baseName == type.name
                        } == true
                }

    private fun addRuntimeClassIdentityMembers(
        builder: TypeSpec.Builder,
        plan: KotlinTypeProjectionPlan,
    ) {
        if (plan.type.methods.none { it.isObjectEquals }) {
            builder.addFunction(
                FunSpec.builder("equals")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("other", ANY.copy(nullable = true))
                    .returns(Boolean::class)
                    .addCode(
                        "if (other !is %T) return false\nreturn nativeObject.pointer == other.nativeObject.pointer\n",
                        projectionClassName(plan.type.qualifiedName),
                    )
                    .build(),
            )
        }
        if (plan.type.methods.none { it.isObjectGetHashCode }) {
            builder.addFunction(
                FunSpec.builder("hashCode")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(Int::class)
                    .addCode("return nativeObject.pointer.hashCode()\n")
                    .build(),
            )
        }
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

    private fun KotlinProjectionMutableCollectionBinding.covers(
        readOnlyBinding: KotlinProjectionReadOnlyCollectionBinding,
    ): Boolean = when (kind) {
        KotlinProjectionMutableCollectionKind.Vector ->
            readOnlyBinding.kind in setOf(KotlinProjectionReadOnlyCollectionKind.Iterable, KotlinProjectionReadOnlyCollectionKind.VectorView)
        KotlinProjectionMutableCollectionKind.Map ->
            readOnlyBinding.kind == KotlinProjectionReadOnlyCollectionKind.MapView
    }

    private fun addReadOnlyCollectionForwardMembers(
        builder: TypeSpec.Builder,
        binding: KotlinProjectionReadOnlyCollectionBinding,
    ) {
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
                builder.addFunction(FunSpec.builder("isEmpty").addModifiers(KModifier.OVERRIDE).returns(Boolean::class).addCode("return %L.isEmpty()\n", binding.delegatePropertyName).build())
                builder.addFunction(FunSpec.builder("contains").addModifiers(KModifier.OVERRIDE).addParameter("element", elementType).returns(Boolean::class).addCode("return %L.contains(element)\n", binding.delegatePropertyName).build())
                builder.addFunction(FunSpec.builder("containsAll").addModifiers(KModifier.OVERRIDE).addParameter("elements", Collection::class.asClassName().parameterizedBy(elementType)).returns(Boolean::class).addCode("return %L.containsAll(elements)\n", binding.delegatePropertyName).build())
                builder.addFunction(FunSpec.builder("iterator").addModifiers(KModifier.OVERRIDE).returns(Iterator::class.asClassName().parameterizedBy(elementType)).addCode("return %L.iterator()\n", binding.delegatePropertyName).build())
                builder.addFunction(FunSpec.builder("listIterator").addModifiers(KModifier.OVERRIDE).returns(ListIterator::class.asClassName().parameterizedBy(elementType)).addCode("return %L.listIterator()\n", binding.delegatePropertyName).build())
                builder.addFunction(FunSpec.builder("listIterator").addModifiers(KModifier.OVERRIDE).addParameter("index", Int::class).returns(ListIterator::class.asClassName().parameterizedBy(elementType)).addCode("return %L.listIterator(index)\n", binding.delegatePropertyName).build())
                builder.addFunction(FunSpec.builder("subList").addModifiers(KModifier.OVERRIDE).addParameter("fromIndex", Int::class).addParameter("toIndex", Int::class).returns(List::class.asClassName().parameterizedBy(elementType)).addCode("return %L.subList(fromIndex, toIndex)\n", binding.delegatePropertyName).build())
                builder.addFunction(FunSpec.builder("get").addModifiers(KModifier.OVERRIDE).addParameter("index", Int::class).returns(elementType).addCode("return %L[index]\n", binding.delegatePropertyName).build())
                builder.addFunction(FunSpec.builder("indexOf").addModifiers(KModifier.OVERRIDE).addParameter("element", elementType).returns(Int::class).addCode("return %L.indexOf(element)\n", binding.delegatePropertyName).build())
                builder.addFunction(FunSpec.builder("lastIndexOf").addModifiers(KModifier.OVERRIDE).addParameter("element", elementType).returns(Int::class).addCode("return %L.lastIndexOf(element)\n", binding.delegatePropertyName).build())
            }
            KotlinProjectionReadOnlyCollectionKind.MapView -> {
                val keyType = resolveTypeName(requireNotNull(binding.keyBinding).typeName)
                val valueType = resolveTypeName(requireNotNull(binding.valueBinding).typeName)
                val entryType = Map.Entry::class.asClassName().parameterizedBy(keyType, valueType)
                builder.addProperty(PropertySpec.builder("size", Int::class).addModifiers(KModifier.OVERRIDE).getter(FunSpec.getterBuilder().addCode("return %L.size\n", binding.delegatePropertyName).build()).build())
                builder.addProperty(PropertySpec.builder("entries", Set::class.asClassName().parameterizedBy(entryType)).addModifiers(KModifier.OVERRIDE).getter(FunSpec.getterBuilder().addCode("return %L.entries\n", binding.delegatePropertyName).build()).build())
                builder.addProperty(PropertySpec.builder("keys", Set::class.asClassName().parameterizedBy(keyType)).addModifiers(KModifier.OVERRIDE).getter(FunSpec.getterBuilder().addCode("return %L.keys\n", binding.delegatePropertyName).build()).build())
                builder.addProperty(PropertySpec.builder("values", Collection::class.asClassName().parameterizedBy(valueType)).addModifiers(KModifier.OVERRIDE).getter(FunSpec.getterBuilder().addCode("return %L.values\n", binding.delegatePropertyName).build()).build())
                builder.addFunction(FunSpec.builder("containsKey").addModifiers(KModifier.OVERRIDE).addParameter("key", keyType).returns(Boolean::class).addCode("return %L.containsKey(key)\n", binding.delegatePropertyName).build())
                builder.addFunction(FunSpec.builder("containsValue").addModifiers(KModifier.OVERRIDE).addParameter("value", valueType).returns(Boolean::class).addCode("return %L.containsValue(value)\n", binding.delegatePropertyName).build())
                builder.addFunction(FunSpec.builder("get").addModifiers(KModifier.OVERRIDE).addParameter("key", keyType).returns(valueType.copy(nullable = true)).addCode("return %L[key]\n", binding.delegatePropertyName).build())
                builder.addFunction(FunSpec.builder("isEmpty").addModifiers(KModifier.OVERRIDE).returns(Boolean::class).addCode("return %L.isEmpty()\n", binding.delegatePropertyName).build())
            }
        }
    }

    private fun addMutableListForwardMembers(
        builder: TypeSpec.Builder,
        binding: KotlinProjectionMutableCollectionBinding,
    ) {
        val elementType = resolveTypeName(requireNotNull(binding.elementBinding).typeName)
        val collectionType = Collection::class.asClassName().parameterizedBy(elementType)
        val mutableListType = MUTABLE_LIST_CLASS_NAME.parameterizedBy(elementType)
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
        builder.addFunction(FunSpec.builder("iterator").addModifiers(KModifier.OVERRIDE).returns(MUTABLE_ITERATOR_CLASS_NAME.parameterizedBy(elementType)).addCode("return %L.iterator()\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("listIterator").addModifiers(KModifier.OVERRIDE).returns(MUTABLE_LIST_ITERATOR_CLASS_NAME.parameterizedBy(elementType)).addCode("return %L.listIterator()\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("listIterator").addModifiers(KModifier.OVERRIDE).addParameter("index", Int::class).returns(MUTABLE_LIST_ITERATOR_CLASS_NAME.parameterizedBy(elementType)).addCode("return %L.listIterator(index)\n", binding.delegatePropertyName).build())
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
        val entryType = MUTABLE_MAP_CLASS_NAME.nestedClass("MutableEntry").parameterizedBy(keyType, valueType)
        builder.addProperty(PropertySpec.builder("size", Int::class).addModifiers(KModifier.OVERRIDE).getter(FunSpec.getterBuilder().addCode("return %L.size\n", binding.delegatePropertyName).build()).build())
        builder.addProperty(PropertySpec.builder("entries", MUTABLE_SET_CLASS_NAME.parameterizedBy(entryType)).addModifiers(KModifier.OVERRIDE).getter(FunSpec.getterBuilder().addCode("return %L.entries\n", binding.delegatePropertyName).build()).build())
        builder.addProperty(PropertySpec.builder("keys", MUTABLE_SET_CLASS_NAME.parameterizedBy(keyType)).addModifiers(KModifier.OVERRIDE).getter(FunSpec.getterBuilder().addCode("return %L.keys\n", binding.delegatePropertyName).build()).build())
        builder.addProperty(PropertySpec.builder("values", MUTABLE_COLLECTION_CLASS_NAME.parameterizedBy(valueType)).addModifiers(KModifier.OVERRIDE).getter(FunSpec.getterBuilder().addCode("return %L.values\n", binding.delegatePropertyName).build()).build())
        builder.addFunction(FunSpec.builder("containsKey").addModifiers(KModifier.OVERRIDE).addParameter("key", keyType).returns(Boolean::class).addCode("return %L.containsKey(key)\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("containsValue").addModifiers(KModifier.OVERRIDE).addParameter("value", valueType).returns(Boolean::class).addCode("return %L.containsValue(value)\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("get").addModifiers(KModifier.OVERRIDE).addParameter("key", keyType).returns(valueType.copy(nullable = true)).addCode("return %L[key]\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("isEmpty").addModifiers(KModifier.OVERRIDE).returns(Boolean::class).addCode("return %L.isEmpty()\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("clear").addModifiers(KModifier.OVERRIDE).addCode("%L.clear()\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("put").addModifiers(KModifier.OVERRIDE).addParameter("key", keyType).addParameter("value", valueType).returns(valueType.copy(nullable = true)).addCode("return %L.put(key, value)\n", binding.delegatePropertyName).build())
        builder.addFunction(
            FunSpec.builder("putAll")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("from", Map::class.asClassName().parameterizedBy(WildcardTypeName.producerOf(keyType), valueType))
                .addCode("%L.putAll(from)\n", binding.delegatePropertyName)
                .build(),
        )
        builder.addFunction(FunSpec.builder("remove").addModifiers(KModifier.OVERRIDE).addParameter("key", keyType).returns(valueType.copy(nullable = true)).addCode("return %L.remove(key)\n", binding.delegatePropertyName).build())
        builder.addFunction(
            FunSpec.builder("iterator")
                .addModifiers(KModifier.OVERRIDE)
                .returns(Iterator::class.asClassName().parameterizedBy(Map.Entry::class.asClassName().parameterizedBy(keyType, valueType)))
                .addCode("return %L.entries.iterator()\n", binding.delegatePropertyName)
                .build(),
        )
    }

    private fun mapIterableType(binding: KotlinProjectionMutableCollectionBinding): TypeName {
        val keyType = resolveTypeName(requireNotNull(binding.keyBinding).typeName)
        val valueType = resolveTypeName(requireNotNull(binding.valueBinding).typeName)
        return Iterable::class.asClassName().parameterizedBy(Map.Entry::class.asClassName().parameterizedBy(keyType, valueType))
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

    private val KotlinTypeProjectionPlan.usesMappedPropertyChangedAugmentation: Boolean
        get() = requiredInterfaceAugmentationDescriptor?.mappedAugmentationMembers.orEmpty().contains("INotifyPropertyChanged")

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

    private fun addMappedPropertyChangedForwardMembers(builder: TypeSpec.Builder) {
        builder.addProperty(
            PropertySpec.builder("__propertyChangedNotifier", WINRT_PROPERTY_CHANGED_NOTIFIER_CLASS_NAME)
                .addModifiers(KModifier.PRIVATE)
                .delegate(
                    CodeBlock.of(
                        "lazy(%T.PUBLICATION) { %T.fromAbi(%L) }",
                        LAZY_THREAD_SAFETY_MODE_CLASS_NAME,
                        WINRT_PROPERTY_CHANGED_NOTIFIER_PROJECTION_CLASS_NAME,
                        "_inner",
                    ),
                )
                .build(),
        )
        builder.addFunction(
            FunSpec.builder("addPropertyChanged")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("handler", WINRT_PROPERTY_CHANGED_HANDLER_CLASS_NAME)
                .addCode("__propertyChangedNotifier.addPropertyChanged(handler)\n")
                .build(),
        )
        builder.addFunction(
            FunSpec.builder("removePropertyChanged")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("handler", WINRT_PROPERTY_CHANGED_HANDLER_CLASS_NAME)
                .addCode("__propertyChangedNotifier.removePropertyChanged(handler)\n")
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

    private fun WinRtMethodDefinition.isMappedCollectionRuntimeMethod(
        plan: KotlinTypeProjectionPlan,
        mappedCollectionMemberNames: Set<String>,
    ): Boolean {
        if (name !in mappedCollectionMemberNames) {
            return false
        }
        val bindingName = abiSlotConstantName(plan.type.methods)
        val binding = plan.instanceMemberBindings.firstOrNull { it.bindingName == bindingName }
        if (binding != null) {
            return binding.isMappedCollectionOrIteratorBinding
        }
        return plan.mutableCollectionBindings.isNotEmpty() ||
            plan.readOnlyCollectionBindings.isNotEmpty() ||
            requiredIteratorBinding(plan) != null
    }

    private fun WinRtPropertyDefinition.isMappedCollectionRuntimeProperty(
        plan: KotlinTypeProjectionPlan,
        mappedCollectionMemberNames: Set<String>,
    ): Boolean {
        if (name !in mappedCollectionMemberNames) {
            return false
        }
        if (plan.mutableCollectionBindings.isNotEmpty() || plan.readOnlyCollectionBindings.isNotEmpty()) {
            val hasGetterBinding = getterMethodName == null ||
                plan.instanceMemberBindings.any { it.bindingName == "${name.uppercase()}_GETTER_SLOT" }
            val hasSetterBinding = setterMethodName == null ||
                plan.instanceMemberBindings.any { it.bindingName == "${name.uppercase()}_SETTER_SLOT" }
            if (!hasGetterBinding || !hasSetterBinding) {
                return true
            }
        }
        val getterIsMapped = getterMethodName == null ||
            plan.instanceMemberBindings
                .firstOrNull { it.bindingName == "${name.uppercase()}_GETTER_SLOT" }
                ?.isMappedCollectionOrIteratorBinding == true
        val setterIsMapped = setterMethodName == null ||
            plan.instanceMemberBindings
                .firstOrNull { it.bindingName == "${name.uppercase()}_SETTER_SLOT" }
                ?.isMappedCollectionOrIteratorBinding == true
        return getterIsMapped && setterIsMapped
    }

    private val KotlinProjectionInstanceMemberBinding.isMappedCollectionOrIteratorBinding: Boolean
        get() = mappedTypeByAbiName(ownerInterfaceQualifiedName.substringBefore('<').removeSuffix("?"))?.let { mappedType ->
            mappedType.readOnlyCollectionKind != null ||
                mappedType.mutableCollectionKind != null ||
                mappedType.descriptionName == "Iterator"
        } == true

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
                    mappedType?.descriptionName == "INotifyPropertyChanged" ||
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
                val constructorParameters = attributeConstructorParameters(plan)
                if (constructorParameters.isNotEmpty()) {
                    primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addParameters(constructorParameters)
                            .build(),
                    )
                    constructorParameters.forEach { parameter ->
                        addProperty(
                            PropertySpec.builder(parameter.name, parameter.type)
                                .initializer(parameter.name)
                                .build(),
                        )
                    }
                }
                addKdoc("attribute WinRT class shell\n")
            }
            .build()

    private fun attributeConstructorParameters(plan: KotlinTypeProjectionPlan): List<ParameterSpec> {
        val primaryCtorParameters = plan.type.methods
            .filter { method -> method.name == ".ctor" && method.isRuntimeSpecialName }
            .maxWithOrNull(compareBy<WinRtMethodDefinition>({ it.parameters.size }, { -(it.methodRowId ?: Int.MAX_VALUE) }))
            ?.parameters
            .orEmpty()
            .mapNotNull { parameter ->
                attributeParameterSpec(parameter.name, parameter.typeName, hasDefault = false)
            }
        val requiredNames = primaryCtorParameters.mapTo(linkedSetOf()) { it.name }
        val optionalFieldParameters = plan.type.fields
            .filterNot { field -> field.isStatic || field.isLiteral || field.name in requiredNames }
            .mapNotNull { field -> attributeParameterSpec(field.name, field.typeName, hasDefault = true) }
        val optionalPropertyParameters = plan.type.properties
            .filterNot { property -> property.isStatic || property.name in requiredNames }
            .mapNotNull { property -> attributeParameterSpec(property.name, property.typeName, hasDefault = true) }
        return primaryCtorParameters + optionalFieldParameters + optionalPropertyParameters
    }

    private fun attributeParameterSpec(
        name: String,
        typeName: String,
        hasDefault: Boolean,
    ): ParameterSpec? {
        val annotationTypeName = attributeParameterTypeName(typeName) ?: return null
        return ParameterSpec.builder(name, annotationTypeName)
            .apply {
                if (hasDefault) {
                    defaultValue(attributeParameterDefaultValue(typeName) ?: return null)
                }
            }
            .build()
    }

    private fun attributeParameterTypeName(typeName: String): TypeName? {
        val trimmed = typeName.trim()
        if (trimmed.startsWith("Array<") && trimmed.endsWith(">")) {
            val elementType = attributeParameterTypeName(trimmed.substringAfter('<').substringBeforeLast('>')) ?: return null
            return Array::class.asClassName().parameterizedBy(elementType)
        }
        return when (trimmed) {
            "Boolean", "System.Boolean" -> Boolean::class.asClassName()
            "Char", "System.Char" -> Char::class.asClassName()
            "String", "System.String" -> String::class.asClassName()
            "Float", "Single", "System.Single" -> Float::class.asClassName()
            "Double", "System.Double" -> Double::class.asClassName()
            "Byte", "SByte", "Int8", "UInt8",
            "Short", "Int16", "UShort", "UInt16",
            "Int", "Int32", "UInt", "UInt32",
            "Long", "Int64", "ULong", "UInt64" -> Long::class.asClassName()
            "System.Type" -> KCLASS_STAR_TYPE_NAME
            else -> Long::class.asClassName()
        }
    }

    private fun attributeParameterDefaultValue(typeName: String): CodeBlock? {
        val trimmed = typeName.trim()
        if (trimmed.startsWith("Array<") && trimmed.endsWith(">")) {
            return CodeBlock.of("[]")
        }
        return when (trimmed) {
            "Boolean", "System.Boolean" -> CodeBlock.of("false")
            "Char", "System.Char" -> CodeBlock.of("'\\u0000'")
            "String", "System.String" -> CodeBlock.of("%S", "")
            "Float", "Single", "System.Single" -> CodeBlock.of("0.0f")
            "Double", "System.Double" -> CodeBlock.of("0.0")
            "System.Type" -> CodeBlock.of("Any::class")
            else -> CodeBlock.of("0L")
        }
    }

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
                    .addCode(nativeStructWriteCode(plan, fields))
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
            .addFunction(
                FunSpec.builder("disposeAbi")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("source", RAW_ADDRESS_CLASS_NAME)
                    .addCode(nativeStructDisposeAbiCode(plan, fields))
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
                    add("%L = %L,\n", field.name.replaceFirstChar(Char::lowercase), nativeStructFieldReadCode(field, "source", plan.type.namespace, plan.typesByQualifiedName))
                }
            }
            .unindent()
            .add(")\n")
            .build()

    private fun nativeStructWriteCode(plan: KotlinTypeProjectionPlan, fields: List<WinRtFieldDefinition>): CodeBlock =
        CodeBlock.builder()
            .apply {
                fields.forEach { field ->
                    add("%L\n", nativeStructFieldWriteCode(field, "value", "destination", plan.type.namespace, plan.typesByQualifiedName))
                }
            }
            .build()

    private fun nativeStructDisposeAbiCode(plan: KotlinTypeProjectionPlan, fields: List<WinRtFieldDefinition>): CodeBlock =
        CodeBlock.builder()
            .apply {
                fields.forEach { field ->
                    nativeStructFieldDisposeAbiCode(field, "source", plan.type.namespace, plan.typesByQualifiedName)?.let { add("%L\n", it) }
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
                "Windows.Foundation.DateTime" -> "struct(Windows.Foundation.DateTime;i8)"
                "Windows.Foundation.EventRegistrationToken" -> "struct(Windows.Foundation.EventRegistrationToken;i8)"
                "Windows.Foundation.HResult" -> "struct(Windows.Foundation.HResult;i4)"
                "Windows.Foundation.TimeSpan" -> "struct(Windows.Foundation.TimeSpan;i8)"
                else -> null
            }
        }
        if (qualifiedName in visiting) {
            return null
        }
        val resolvedType = typesByQualifiedName[qualifiedName] ?: return null
        if (resolvedType.kind == WinRtTypeKind.Enum) {
            return "enum($qualifiedName;i4)"
        }
        val type = resolvedType.takeIf { it.kind == WinRtTypeKind.Struct } ?: return null
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
            "Boolean" -> "b1"
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
            "String" -> "string"
            "Any",
            "System.Object",
            IINSPECTABLE_REFERENCE_CLASS_NAME.simpleName,
            "io.github.composefluent.winrt.runtime.IInspectableReference" -> "cinterface(IInspectable)"
            IUNKNOWN_REFERENCE_CLASS_NAME.simpleName,
            "io.github.composefluent.winrt.runtime.IUnknownReference" -> "cinterface(IUnknown)"
            else -> nativeStructGuidSignature(typeName, currentNamespace, typesByQualifiedName, visiting)
                ?: nativeStructReferenceFieldGuidSignature(typeName, currentNamespace, typesByQualifiedName)
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
        if (nativeStructReferenceFieldKind(field.typeName, currentNamespace, typesByQualifiedName) != null) {
            return CodeBlock.of("%T(%S, %T.ADDRESS)", NATIVE_SCALAR_FIELD_SPEC_CLASS_NAME, field.name.replaceFirstChar(Char::lowercase), NATIVE_STRUCT_SCALAR_KIND_CLASS_NAME)
        }
        val enumQualifiedName = field.typeName.substringBefore('<').removeSuffix("?").let { rawTypeName ->
            when {
                typesByQualifiedName[rawTypeName]?.kind == WinRtTypeKind.Enum -> rawTypeName
                currentNamespace.isNotBlank() && typesByQualifiedName["$currentNamespace.$rawTypeName"]?.kind == WinRtTypeKind.Enum -> "$currentNamespace.$rawTypeName"
                else -> typesByQualifiedName.keys.firstOrNull { it.endsWith(".$rawTypeName") && typesByQualifiedName[it]?.kind == WinRtTypeKind.Enum }
            }
        }
        if (enumQualifiedName != null) {
            return CodeBlock.of("%T(%S, %T.INT32)", NATIVE_SCALAR_FIELD_SPEC_CLASS_NAME, field.name.replaceFirstChar(Char::lowercase), NATIVE_STRUCT_SCALAR_KIND_CLASS_NAME)
        }
        val fieldQualifiedName = nativeNestedStructFieldTypeName(field.typeName, currentNamespace, typesByQualifiedName) ?: return null
        val fieldType = runCatching { resolveTypeName(fieldQualifiedName) as? ClassName }.getOrNull() ?: return null
        return CodeBlock.of("%T(%S, %T.Metadata.layout)", NATIVE_NESTED_STRUCT_FIELD_SPEC_CLASS_NAME, field.name.replaceFirstChar(Char::lowercase), fieldType)
    }

    private fun nativeStructReferenceFieldGuidSignature(
        typeName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    ): String? {
        val rawTypeName = typeName.substringBefore('<').removeSuffix("?")
        val qualifiedName = when {
            typesByQualifiedName.containsKey(rawTypeName) -> rawTypeName
            currentNamespace.isNotBlank() && typesByQualifiedName.containsKey("$currentNamespace.$rawTypeName") -> "$currentNamespace.$rawTypeName"
            else -> typesByQualifiedName.keys.firstOrNull { it.endsWith(".$rawTypeName") }
        } ?: return null
        return when (typesByQualifiedName[qualifiedName]?.kind) {
            WinRtTypeKind.Interface -> "cinterface($qualifiedName)"
            WinRtTypeKind.RuntimeClass -> "rc($qualifiedName;default)"
            else -> null
        }
    }

    private fun nativeStructReferenceFieldKind(
        typeName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    ): NativeStructReferenceFieldKind? {
        val rawTypeName = typeName.substringBefore('<').removeSuffix("?")
        return when (rawTypeName) {
            "String" -> NativeStructReferenceFieldKind.String
            "Any",
            "System.Object",
            IINSPECTABLE_REFERENCE_CLASS_NAME.simpleName,
            "io.github.composefluent.winrt.runtime.IInspectableReference" -> NativeStructReferenceFieldKind.InspectableReference
            IUNKNOWN_REFERENCE_CLASS_NAME.simpleName,
            "io.github.composefluent.winrt.runtime.IUnknownReference" -> NativeStructReferenceFieldKind.UnknownReference
            else -> {
                val qualifiedName = when {
                    typesByQualifiedName.containsKey(rawTypeName) -> rawTypeName
                    currentNamespace.isNotBlank() && typesByQualifiedName.containsKey("$currentNamespace.$rawTypeName") -> "$currentNamespace.$rawTypeName"
                    else -> typesByQualifiedName.keys.firstOrNull { it.endsWith(".$rawTypeName") }
                } ?: return null
                when (typesByQualifiedName[qualifiedName]?.kind) {
                    WinRtTypeKind.Interface -> NativeStructReferenceFieldKind.ProjectedInterface
                    WinRtTypeKind.RuntimeClass -> NativeStructReferenceFieldKind.ProjectedRuntimeClass
                    else -> null
                }
            }
        }
    }

    internal fun nativeStructScalarKind(typeName: String): String? = when (typeName) {
        "Boolean" -> "INT8"
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

    internal fun nativeStructFieldReadCode(
        field: WinRtFieldDefinition,
        sourceName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    ): CodeBlock {
        val fieldName = field.name.replaceFirstChar(Char::lowercase)
        val slice = CodeBlock.of("layout.slice(%L, %S)", sourceName, fieldName)
        return when (field.typeName) {
            "Boolean" -> CodeBlock.of("%T.readInt8(%L).toInt() != 0", PLATFORM_ABI_CLASS_NAME, slice)
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
            else -> nativeStructReferenceFieldReadCode(field, sourceName, currentNamespace, typesByQualifiedName)
                ?: nativeStructEnumFieldReadCode(field, slice, currentNamespace, typesByQualifiedName)
                ?: CodeBlock.of("%T.Metadata.fromAbi(%L)", resolveTypeName(field.typeName), slice)
        }
    }

    internal fun nativeStructFieldWriteCode(
        field: WinRtFieldDefinition,
        valueName: String,
        destinationName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    ): CodeBlock {
        val fieldName = field.name.replaceFirstChar(Char::lowercase)
        val value = CodeBlock.of("%L.%L", valueName, fieldName)
        val slice = CodeBlock.of("layout.slice(%L, %S)", destinationName, fieldName)
        return when (field.typeName) {
            "Boolean" -> CodeBlock.of("%T.writeInt8(%L, if (%L) 1 else 0)", PLATFORM_ABI_CLASS_NAME, slice, value)
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
            else -> nativeStructReferenceFieldWriteCode(field, valueName, destinationName, currentNamespace, typesByQualifiedName)
                ?: nativeStructEnumFieldWriteCode(field, value, slice, currentNamespace, typesByQualifiedName)
                ?: CodeBlock.of("%T.Metadata.copyTo(%L, %L)", resolveTypeName(field.typeName), value, slice)
        }
    }

    private fun nativeStructEnumFieldReadCode(
        field: WinRtFieldDefinition,
        slice: CodeBlock,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    ): CodeBlock? {
        val enumType = nativeStructEnumFieldTypeName(field.typeName, currentNamespace, typesByQualifiedName) ?: return null
        return CodeBlock.of("%T.Metadata.fromAbi(%T.readInt32(%L))", resolveTypeName(enumType), PLATFORM_ABI_CLASS_NAME, slice)
    }

    private fun nativeStructEnumFieldWriteCode(
        field: WinRtFieldDefinition,
        value: CodeBlock,
        slice: CodeBlock,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    ): CodeBlock? {
        val enumType = nativeStructEnumFieldTypeName(field.typeName, currentNamespace, typesByQualifiedName) ?: return null
        return CodeBlock.of("%T.writeInt32(%L, %T.Metadata.toAbi(%L))", PLATFORM_ABI_CLASS_NAME, slice, resolveTypeName(enumType), value)
    }

    private fun nativeStructEnumFieldTypeName(
        typeName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    ): String? {
        val rawTypeName = typeName.substringBefore('<').removeSuffix("?")
        return when {
            typesByQualifiedName[rawTypeName]?.kind == WinRtTypeKind.Enum -> rawTypeName
            currentNamespace.isNotBlank() && typesByQualifiedName["$currentNamespace.$rawTypeName"]?.kind == WinRtTypeKind.Enum -> "$currentNamespace.$rawTypeName"
            else -> typesByQualifiedName.keys.firstOrNull { it.endsWith(".$rawTypeName") && typesByQualifiedName[it]?.kind == WinRtTypeKind.Enum }
        }
    }

    private fun nativeStructReferenceFieldReadCode(
        field: WinRtFieldDefinition,
        sourceName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    ): CodeBlock? {
        val fieldName = field.name.replaceFirstChar(Char::lowercase)
        val slice = CodeBlock.of("layout.slice(%L, %S)", sourceName, fieldName)
        return when (nativeStructReferenceFieldKind(field.typeName, currentNamespace, typesByQualifiedName) ?: return null) {
            NativeStructReferenceFieldKind.String ->
                CodeBlock.of("%T.fromHandle(%T.readPointer(%L), owner = false).use { it.toKString() }", HSTRING_CLASS_NAME, PLATFORM_ABI_CLASS_NAME, slice)
            NativeStructReferenceFieldKind.UnknownReference ->
                CodeBlock.of("%T(%T.toRawComPtr(%T.readPointer(%L)), preventReleaseOnDispose = true).use { %T(it.getRefPointer()) }", IUNKNOWN_REFERENCE_CLASS_NAME, PLATFORM_ABI_CLASS_NAME, PLATFORM_ABI_CLASS_NAME, slice, IUNKNOWN_REFERENCE_CLASS_NAME)
            NativeStructReferenceFieldKind.InspectableReference ->
                CodeBlock.of("%T(%T.toRawComPtr(%T.readPointer(%L)), %T.IInspectable, preventReleaseOnDispose = true).use { %T(it.getRefPointer(), %T.IInspectable) }", IUNKNOWN_REFERENCE_CLASS_NAME, PLATFORM_ABI_CLASS_NAME, PLATFORM_ABI_CLASS_NAME, slice, IID_CLASS_NAME, IINSPECTABLE_REFERENCE_CLASS_NAME, IID_CLASS_NAME)
            NativeStructReferenceFieldKind.ProjectedInterface,
            NativeStructReferenceFieldKind.ProjectedRuntimeClass -> CodeBlock.of(
                "run {\nval __fieldRef = %T(%T.toRawComPtr(%T.readPointer(%L)), preventReleaseOnDispose = true)\n%T.Metadata.wrap(%T(__fieldRef.getRefPointer()))\n}",
                IUNKNOWN_REFERENCE_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
                slice,
                resolveTypeName(field.typeName),
                IUNKNOWN_REFERENCE_CLASS_NAME,
            )
        }
    }

    private fun nativeStructReferenceFieldWriteCode(
        field: WinRtFieldDefinition,
        valueName: String,
        destinationName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    ): CodeBlock? {
        val fieldName = field.name.replaceFirstChar(Char::lowercase)
        val value = CodeBlock.of("%L.%L", valueName, fieldName)
        val slice = CodeBlock.of("layout.slice(%L, %S)", destinationName, fieldName)
        return when (nativeStructReferenceFieldKind(field.typeName, currentNamespace, typesByQualifiedName) ?: return null) {
            NativeStructReferenceFieldKind.String ->
                CodeBlock.of("%T.writePointer(%L, %T.create(%L).handle)", PLATFORM_ABI_CLASS_NAME, slice, HSTRING_CLASS_NAME, value)
            NativeStructReferenceFieldKind.UnknownReference,
            NativeStructReferenceFieldKind.InspectableReference ->
                CodeBlock.of("%T.writePointer(%L, %T.fromRawComPtr(%L.getRefPointer()))", PLATFORM_ABI_CLASS_NAME, slice, PLATFORM_ABI_CLASS_NAME, value)
            NativeStructReferenceFieldKind.ProjectedInterface,
            NativeStructReferenceFieldKind.ProjectedRuntimeClass ->
                CodeBlock.of(
                    "%T.writePointer(%L, %T.fromRawComPtr((%L as %T).nativeObject.getRefPointer()))",
                    PLATFORM_ABI_CLASS_NAME,
                    slice,
                    PLATFORM_ABI_CLASS_NAME,
                    value,
                    IWINRT_OBJECT_CLASS_NAME,
                )
        }
    }

    private fun nativeStructFieldDisposeAbiCode(
        field: WinRtFieldDefinition,
        sourceName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    ): CodeBlock? {
        val fieldName = field.name.replaceFirstChar(Char::lowercase)
        val slice = CodeBlock.of("layout.slice(%L, %S)", sourceName, fieldName)
        val pointer = CodeBlock.of("%T.readPointer(%L)", PLATFORM_ABI_CLASS_NAME, slice)
        return when {
            field.typeName.substringBefore('<').removeSuffix("?") == "String" ->
                CodeBlock.of("%T.fromHandle(%L, owner = true).close()", HSTRING_CLASS_NAME, pointer)
            nativeStructReferenceFieldKind(field.typeName, currentNamespace, typesByQualifiedName) != null ->
                CodeBlock.of("if (%L != %T.nullPointer) %T(%T.toRawComPtr(%L)).close()", pointer, PLATFORM_ABI_CLASS_NAME, IUNKNOWN_REFERENCE_CLASS_NAME, PLATFORM_ABI_CLASS_NAME, pointer)
            else -> null
        }
    }

    private enum class NativeStructReferenceFieldKind {
        String,
        UnknownReference,
        InspectableReference,
        ProjectedInterface,
        ProjectedRuntimeClass,
    }

    private fun runtimeClassObjectReferenceCacheInitializer(
        objectReferencePlan: WinRtObjectReferencePlanDescriptor?,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
        acquireExpression: String,
        vararg acquireArgs: Any,
    ): CodeBlock {
        val body = CodeBlock.builder()
        if (objectReferencePlan?.requiresGenericInstantiation == true) {
            body.addStatement(
                "%T.initializeBySourceType(%S)",
                WINRT_GENERIC_TYPE_INSTANTIATIONS_CLASS_NAME,
                objectReferencePlan.interfaceName,
            )
        }
        if (objectReferencePlan?.usesDefaultInterfaceObjRef == true && objectReferencePlan.defaultInterfaceObjRefVtableSlot != null) {
            body.addStatement("_inner.getDefaultInterfaceObjectReference(%L)", objectReferencePlan.defaultInterfaceObjRefVtableSlot)
        } else if (objectReferencePlan?.requiresGenericInstantiation == true) {
            val signature = abiTypeSignature(renderAbiTypeBinding(objectReferencePlan.interfaceName, typesByQualifiedName))
            if (signature != null) {
                body.addStatement(
                    "Metadata.acquireInterface(_inner, %T.createFromSignature(%L))",
                    PARAMETERIZED_INTERFACE_ID_CLASS_NAME,
                    signature,
                )
            } else {
                body.add(acquireExpression, *acquireArgs)
                body.add("\n")
            }
        } else {
            body.add(acquireExpression, *acquireArgs)
            body.add("\n")
        }
        return CodeBlock.builder()
            .add("lazy(%T.PUBLICATION) {\n", LAZY_THREAD_SAFETY_MODE_CLASS_NAME)
            .indent()
            .add(body.build())
            .unindent()
            .add("}")
            .build()
    }

    internal fun renderDelegate(plan: KotlinTypeProjectionPlan): TypeSpec {
        val invokeMethod = requireDelegateInvokeMethod(plan.type)
        val builder = TypeSpec.funInterfaceBuilder(plan.type.name)
        applyCommonTypeShape(builder, plan)
        val invokeShape = plan.delegateInvokeShape
        if (invokeShape != null && supportsProjectedDelegateObjectMarshaller(plan, invokeShape)) {
            builder.addSuperinterface(WINRT_PROJECTED_DELEGATE_CLASS_NAME)
            builder.addFunction(
                FunSpec.builder("createWinRtDelegateHandle")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(ClassName("io.github.composefluent.winrt.runtime", "WinRtDelegateHandle"))
                    .addCode(
                        CodeBlock.of(
                            """
                            return %T.createDelegate(
                                iid = Metadata.DESCRIPTOR.interfaceId,
                                parameterKinds = Metadata.DESCRIPTOR.parameterKinds,
                                returnKind = Metadata.DESCRIPTOR.returnKind,
                                parameterStructAdapters = Metadata.DESCRIPTOR.parameterStructAdapters,
                                returnStructAdapter = Metadata.DESCRIPTOR.returnStructAdapter,
                            ) { __args ->
                                this(%L)
                            }
                            """.trimIndent() + "\n",
                            WINRT_DELEGATE_BRIDGE_CLASS_NAME,
                            delegateCallbackArgumentCodeList(invokeShape.parameterBindings),
                        ),
                    )
                    .build(),
            )
        }
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
        if (invokeShape != null && invokeShape.isSupportedProjectedDelegateShape()) {
            val projectedType = plan.projectedSelfTypeName()
            builder.addType(
                TypeSpec.companionObjectBuilder("Metadata")
                    .addProperty(
                        PropertySpec.builder("DESCRIPTOR", WINRT_DELEGATE_DESCRIPTOR_CLASS_NAME)
                            .addModifiers(KModifier.INTERNAL)
                            .initializer("%L", delegateDescriptorCode(invokeShape, plan.type.qualifiedName))
                            .build(),
                    )
                    .addFunction(
                        FunSpec.builder("fromAbi")
                            .addModifiers(KModifier.INTERNAL)
                            .apply {
                                repeat(plan.type.genericParameterCount) { index ->
                                    addTypeVariable(TypeVariableName("T$index"))
                                }
                            }
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

internal fun KotlinTypeProjectionPlan.supportsDerivedComposableConstruction(): Boolean =
    classMemberMergeDescriptor?.interfaceDescriptors?.any { descriptor -> descriptor.isOverridableInterface } == true &&
        type.baseTypeName?.takeUnless { it == "System.Object" || it == "Any" } == null &&
        KotlinProjectionCompanionKind.ComposableFactory in companionKinds

private fun KotlinProjectionRenderer.supportsProjectedDelegateObjectMarshaller(
    plan: KotlinTypeProjectionPlan,
    invokeShape: KotlinProjectionDelegateInvokeShape,
): Boolean =
    plan.type.genericParameterCount == 0 &&
        invokeShape.returnBinding.kind == KotlinProjectionAbiValueKind.Unit &&
        invokeShape.isSupportedOutboundDelegateShape() &&
        invokeShape.parameterBindings.all { binding -> supportsProjectedDelegateObjectMarshallerArgument(binding.typeBinding) }

private fun KotlinProjectionRenderer.supportsProjectedDelegateObjectMarshallerArgument(
    typeBinding: KotlinProjectionAbiTypeBinding,
): Boolean =
    when (typeBinding.kind) {
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
        KotlinProjectionAbiValueKind.GuidValue,
        KotlinProjectionAbiValueKind.Struct,
        KotlinProjectionAbiValueKind.Enum,
        KotlinProjectionAbiValueKind.Object,
        KotlinProjectionAbiValueKind.UnknownReference,
        KotlinProjectionAbiValueKind.InspectableReference,
        KotlinProjectionAbiValueKind.MappedAsyncAction,
        KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress,
        KotlinProjectionAbiValueKind.MappedAsyncOperation,
        KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress -> true
        KotlinProjectionAbiValueKind.ProjectedInterface,
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass -> true
        else -> false
    }
