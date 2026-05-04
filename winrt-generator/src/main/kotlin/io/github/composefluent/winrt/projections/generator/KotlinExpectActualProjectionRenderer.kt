package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRtMethodDefinition
import io.github.composefluent.winrt.metadata.WinRtPropertyDefinition
import io.github.composefluent.winrt.metadata.WinRtTypeKind
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asClassName

internal class KotlinExpectActualProjectionRenderer(
    private val baseRenderer: KotlinProjectionRenderer,
) : KotlinProjectionFileRenderer {
    override fun render(plan: KotlinTypeProjectionPlan): List<KotlinProjectionFile> {
        return when {
            canRenderExpectActualInterfaceSlice(plan) -> listOf(
                renderCommonExpectInterface(plan),
                renderJvmActualInterface(plan),
            )
            canRenderExpectActualRuntimeClassSlice(plan) -> listOf(
                renderCommonExpectRuntimeClass(plan),
                renderJvmActualRuntimeClass(plan),
            )
            else -> listOf(prefixFile("commonMain/kotlin", baseRenderer.render(plan)))
        }
    }

    private fun canRenderExpectActualInterfaceSlice(plan: KotlinTypeProjectionPlan): Boolean =
            plan.declarationKind == KotlinProjectionDeclarationKind.Interface &&
            plan.type.kind == WinRtTypeKind.Interface &&
            plan.type.genericParameterCount == 0 &&
            baseRenderer.collectInterfaceProxyTypes(plan).all { interfaceType ->
                canRenderExpectActualInterfaceType(plan, interfaceType)
            } &&
            plan.mutableCollectionBindings.isEmpty() &&
            plan.readOnlyCollectionBindings.isEmpty()

    private fun canRenderExpectActualRuntimeClassSlice(plan: KotlinTypeProjectionPlan): Boolean =
            plan.declarationKind == KotlinProjectionDeclarationKind.Class &&
            plan.type.kind == WinRtTypeKind.RuntimeClass &&
            plan.type.genericParameterCount == 0 &&
            plan.type.baseTypeName?.let { it != "System.Object" && it != "Any" } != true &&
            plan.type.methods.none(WinRtMethodDefinition::isStatic) &&
            plan.type.properties.none(WinRtPropertyDefinition::isStatic) &&
            plan.type.properties.all { it.getterMethodName != null } &&
            plan.type.events.isEmpty() &&
            plan.staticInterfaceNames.isEmpty() &&
            plan.activatableFactoryInterfaceName == null &&
            plan.composableFactoryInterfaceName == null &&
            KotlinProjectionCompanionKind.ActivationFactory !in plan.companionKinds &&
            KotlinProjectionCompanionKind.StaticInterfaces !in plan.companionKinds &&
            KotlinProjectionCompanionKind.ComposableFactory !in plan.companionKinds &&
            KotlinProjectionSpecializationKind.StaticClass !in plan.specializationKinds &&
            KotlinProjectionSpecializationKind.AttributeClass !in plan.specializationKinds &&
            plan.mutableCollectionBindings.isEmpty() &&
            plan.readOnlyCollectionBindings.isEmpty() &&
            publicRuntimeClassInterfaces(plan).isNotEmpty() &&
            publicRuntimeClassInterfaceProxyTypes(plan).all { interfaceType ->
                canRenderExpectActualInterfaceType(plan, interfaceType)
            } &&
            publicRuntimeClassInterfaceMembersAreConflictFree(plan) &&
            runtimeClassMembersAreCoveredByPublicInterface(plan)

    private fun canRenderExpectActualInterfaceType(
        plan: KotlinTypeProjectionPlan,
        type: io.github.composefluent.winrt.metadata.WinRtTypeDefinition,
    ): Boolean =
            type.kind == WinRtTypeKind.Interface &&
            type.genericParameterCount == 0 &&
            type.methods.all { it.genericParameterCount == 0 } &&
            type.methods.none(WinRtMethodDefinition::isStatic) &&
            type.properties.none(WinRtPropertyDefinition::isStatic) &&
            type.properties.all { it.getterMethodName != null } &&
            type.events.isEmpty() &&
            type.methods
                .filter(WinRtMethodDefinition::isOrdinaryProjectedMethod)
                .all { method ->
                    canBuildAbiCallPlan(
                        returnTypeName = method.returnTypeName,
                        parameters = method.parameters.map { it.name to it.typeName },
                        typesByQualifiedName = plan.typesByQualifiedName,
                    )
                } &&
            type.properties
                .filterNot(WinRtPropertyDefinition::isStatic)
                .filter { it.getterMethodName != null }
                .all { property ->
                    canBuildAbiCallPlan(
                        returnTypeName = property.typeName,
                        parameters = emptyList(),
                        typesByQualifiedName = plan.typesByQualifiedName,
                    ) && (
                        property.isReadOnly ||
                            canBuildAbiCallPlan(
                                returnTypeName = "Unit",
                                parameters = listOf("value" to property.typeName),
                                typesByQualifiedName = plan.typesByQualifiedName,
                            )
                        )
                }

    private fun canBuildAbiCallPlan(
        returnTypeName: String,
        parameters: List<Pair<String, String>>,
        typesByQualifiedName: Map<String, io.github.composefluent.winrt.metadata.WinRtTypeDefinition>,
    ): Boolean =
        runCatching {
            baseRenderer.buildAbiCallPlan(
                returnBinding = baseRenderer.renderAbiTypeBinding(returnTypeName, typesByQualifiedName),
                parameterBindings = parameters.map { (name, typeName) ->
                    KotlinProjectionAbiParameterBinding(
                        name = name,
                        typeBinding = baseRenderer.renderAbiTypeBinding(typeName, typesByQualifiedName),
                    )
                },
            ) != null
        }.getOrDefault(false)

    private fun publicRuntimeClassInterfaces(plan: KotlinTypeProjectionPlan): List<io.github.composefluent.winrt.metadata.WinRtTypeDefinition> =
        plan.type.implementedInterfaces
            .filter { implemented -> isPublicRuntimeClassInterface(plan, implemented.interfaceName) }
            .mapNotNull { implemented -> plan.typesByQualifiedName[implemented.interfaceName.rawWinRtTypeName()] }
            .distinctBy { it.qualifiedName }

    private fun publicRuntimeClassInterfaceProxyTypes(plan: KotlinTypeProjectionPlan): List<io.github.composefluent.winrt.metadata.WinRtTypeDefinition> =
        publicRuntimeClassInterfaces(plan)
            .flatMap { interfaceType ->
                baseRenderer.collectInterfaceProxyTypes(
                    plan.copy(
                        type = interfaceType,
                        declarationKind = KotlinProjectionDeclarationKind.Interface,
                    ),
                )
            }
            .distinctBy { it.qualifiedName }

    private fun isPublicRuntimeClassInterface(
        plan: KotlinTypeProjectionPlan,
        interfaceName: String,
    ): Boolean {
        val rawName = interfaceName.rawWinRtTypeName()
        val descriptor = plan.classMemberMergeDescriptor
            ?.interfaceDescriptors
            ?.firstOrNull { it.interfaceTypeName == rawName }
        return descriptor?.let { !it.isOverridableInterface && !it.isProtectedInterface } ?: true
    }

    private fun String.rawWinRtTypeName(): String =
        substringBefore('<').removeSuffix("?")

    private fun runtimeClassMembersAreCoveredByPublicInterface(plan: KotlinTypeProjectionPlan): Boolean {
        val interfaceTypes = publicRuntimeClassInterfaceProxyTypes(plan)
        if (interfaceTypes.isEmpty()) {
            return false
        }
        val interfaceMethods = interfaceTypes.flatMap { interfaceType ->
            interfaceType.methods
                .filter(WinRtMethodDefinition::isOrdinaryProjectedMethod)
                .map { method -> projectedMethodSignatureKey(method) to methodCoverage(method) }
        }.toMap()
        val interfaceProperties = interfaceTypes.flatMap { interfaceType ->
            interfaceType.properties
                .filterNot(WinRtPropertyDefinition::isStatic)
                .filter { it.getterMethodName != null }
                .map { property -> property.name.replaceFirstChar(Char::lowercase) to propertyCoverage(property) }
        }.toMap()
        val classMethodsCovered = plan.type.methods
            .filter(WinRtMethodDefinition::isOrdinaryProjectedMethod)
            .all { method -> interfaceMethods[projectedMethodSignatureKey(method)] == methodCoverage(method) }
        val classPropertiesCovered = plan.type.properties
            .filterNot(WinRtPropertyDefinition::isStatic)
            .filter { it.getterMethodName != null }
            .all { property ->
                interfaceProperties[property.name.replaceFirstChar(Char::lowercase)] ==
                    propertyCoverage(property)
            }
        return classMethodsCovered && classPropertiesCovered
    }

    private fun publicRuntimeClassInterfaceMembersAreConflictFree(plan: KotlinTypeProjectionPlan): Boolean {
        val methods = mutableMapOf<String, RuntimeClassMethodCoverage>()
        val properties = mutableMapOf<String, RuntimeClassPropertyCoverage>()
        publicRuntimeClassInterfaceProxyTypes(plan).forEach { interfaceType ->
            interfaceType.methods
                .filter(WinRtMethodDefinition::isOrdinaryProjectedMethod)
                .forEach { method ->
                    val key = projectedMethodSignatureKey(method)
                    val coverage = methodCoverage(method)
                    val previous = methods.putIfAbsent(key, coverage)
                    if (previous != null && previous != coverage) {
                        return false
                    }
                }
            interfaceType.properties
                .filterNot(WinRtPropertyDefinition::isStatic)
                .filter { it.getterMethodName != null }
                .forEach { property ->
                    val key = property.name.replaceFirstChar(Char::lowercase)
                    val coverage = propertyCoverage(property)
                    val previous = properties.putIfAbsent(key, coverage)
                    if (previous != null && previous != coverage) {
                        return false
                    }
                }
        }
        return true
    }

    private data class RuntimeClassMethodCoverage(
        val returnTypeName: String,
        val parameters: List<Pair<String, String>>,
    )

    private data class RuntimeClassPropertyCoverage(
        val typeName: String,
        val isReadOnly: Boolean,
        val getterMethodName: String?,
        val setterMethodName: String?,
    )

    private fun methodCoverage(method: WinRtMethodDefinition): RuntimeClassMethodCoverage =
        RuntimeClassMethodCoverage(
            returnTypeName = method.returnTypeName,
            parameters = method.parameters.map { it.name to it.typeName },
        )

    private fun propertyCoverage(property: WinRtPropertyDefinition): RuntimeClassPropertyCoverage =
        RuntimeClassPropertyCoverage(
            typeName = property.typeName,
            isReadOnly = property.isReadOnly,
            getterMethodName = property.getterMethodName,
            setterMethodName = property.setterMethodName,
        )

    private fun projectedMethodSignatureKey(method: WinRtMethodDefinition): String =
        "${method.projectedMethodName()}:${method.parameters.joinToString(",") { it.typeName }}"

    private fun renderCommonExpectInterface(plan: KotlinTypeProjectionPlan): KotlinProjectionFile {
        val builder = TypeSpec.interfaceBuilder(plan.type.name)
            .addModifiers(KModifier.EXPECT)
        baseRenderer.applyCommonTypeShape(builder, plan)
        plan.type.implementedInterfaces.forEach { implemented ->
            builder.addSuperinterface(baseRenderer.resolveTypeName(implemented.interfaceName))
        }
        plan.type.methods
            .filter(WinRtMethodDefinition::isOrdinaryProjectedMethod)
            .forEach { method -> builder.addFunction(baseRenderer.renderInterfaceMethod(method)) }
        plan.type.properties
            .filterNot(WinRtPropertyDefinition::isStatic)
            .filter { it.getterMethodName != null }
            .forEach { property -> builder.addProperty(baseRenderer.renderInterfaceProperty(property)) }
        return renderSourceSetFile("commonMain/kotlin", plan, builder.build())
    }

    private fun renderJvmActualInterface(plan: KotlinTypeProjectionPlan): KotlinProjectionFile {
        val builder = TypeSpec.interfaceBuilder(plan.type.name)
            .addModifiers(KModifier.ACTUAL)
        baseRenderer.applyCommonTypeShape(builder, plan)
        plan.type.implementedInterfaces.forEach { implemented ->
            builder.addSuperinterface(baseRenderer.resolveTypeName(implemented.interfaceName))
        }
        plan.type.methods
            .filter(WinRtMethodDefinition::isOrdinaryProjectedMethod)
            .forEach { method -> builder.addFunction(baseRenderer.renderInterfaceMethod(method).withModifier(KModifier.ACTUAL)) }
        plan.type.properties
            .filterNot(WinRtPropertyDefinition::isStatic)
            .filter { it.getterMethodName != null }
            .forEach { property -> builder.addProperty(baseRenderer.renderInterfaceProperty(property).withModifier(KModifier.ACTUAL)) }
        builder.addType(renderJvmInterfaceNativeProjection(plan))
        baseRenderer.appendCompanionShells(builder, plan)
        return renderSourceSetFile("jvmMain/kotlin", plan, builder.build())
    }

    private fun renderCommonExpectRuntimeClass(plan: KotlinTypeProjectionPlan): KotlinProjectionFile {
        val builder = TypeSpec.classBuilder(plan.type.name)
            .addModifiers(KModifier.EXPECT)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addModifiers(KModifier.INTERNAL)
                    .addParameter("_inner", IINSPECTABLE_REFERENCE_CLASS_NAME)
                    .addParameter("__winrtWrapper", UNIT)
                    .build(),
            )
        baseRenderer.applyCommonTypeShape(builder, plan, emitKotlinSealed = false)
        publicRuntimeClassInterfaces(plan).forEach { interfaceType ->
            builder.addSuperinterface(baseRenderer.resolveTypeName(interfaceType.qualifiedName))
        }
        builder.addSuperinterface(IWINRT_OBJECT_CLASS_NAME)
        return renderSourceSetFile("commonMain/kotlin", plan, builder.build())
    }

    private fun renderJvmActualRuntimeClass(plan: KotlinTypeProjectionPlan): KotlinProjectionFile {
        val builder = TypeSpec.classBuilder(plan.type.name)
            .addModifiers(KModifier.ACTUAL)
        baseRenderer.applyCommonTypeShape(builder, plan, emitKotlinSealed = false)
        publicRuntimeClassInterfaces(plan).forEach { interfaceType ->
            builder.addSuperinterface(baseRenderer.resolveTypeName(interfaceType.qualifiedName))
        }
        builder.addSuperinterface(IWINRT_OBJECT_CLASS_NAME)
        builder.primaryConstructor(
            FunSpec.constructorBuilder()
                .addModifiers(KModifier.ACTUAL)
                .addModifiers(KModifier.INTERNAL)
                .addParameter("_inner", IINSPECTABLE_REFERENCE_CLASS_NAME)
                .addParameter("__winrtWrapper", UNIT)
                .build(),
        )
        builder.addProperty(
            PropertySpec.builder("_inner", IINSPECTABLE_REFERENCE_CLASS_NAME)
                .addModifiers(KModifier.PRIVATE)
                .initializer("_inner")
                .build(),
        )
        builder.addProperty(
            PropertySpec.builder("nativeObject", COM_OBJECT_REFERENCE_CLASS_NAME)
                .addModifiers(KModifier.OVERRIDE)
                .getter(FunSpec.getterBuilder().addCode("return _inner\n").build())
                .build(),
        )
        addJvmRuntimeClassInterfaceForwards(builder, plan)
        builder.addType(baseRenderer.buildMetadataCompanionShell(plan, emptyList(), emptyList(), emptyList()))
        baseRenderer.appendCompanionShells(builder, plan, excludeKinds = setOf(KotlinProjectionCompanionKind.Metadata))
        return renderSourceSetFile("jvmMain/kotlin", plan, builder.build())
    }

    private fun addJvmRuntimeClassInterfaceForwards(
        builder: TypeSpec.Builder,
        plan: KotlinTypeProjectionPlan,
    ) {
        val emittedMethods = mutableSetOf<String>()
        val emittedProperties = mutableSetOf<String>()
        publicRuntimeClassInterfaceProxyTypes(plan).forEach { interfaceType ->
            val cacheName = "_${interfaceType.name.replaceFirstChar(Char::lowercase)}"
            builder.addProperty(
                PropertySpec.builder(cacheName, baseRenderer.resolveTypeName(interfaceType.qualifiedName))
                    .addModifiers(KModifier.PRIVATE)
                    .delegate(
                        CodeBlock.of(
                            "lazy(%T.PUBLICATION) { %T.Metadata.wrap(Metadata.acquireInterface(_inner, %T.Metadata.IID)) }",
                            LAZY_THREAD_SAFETY_MODE_CLASS_NAME,
                            baseRenderer.resolveTypeName(interfaceType.qualifiedName),
                            baseRenderer.resolveTypeName(interfaceType.qualifiedName),
                        ),
                    )
                    .build(),
            )
            interfaceType.methods
                .filter(WinRtMethodDefinition::isOrdinaryProjectedMethod)
                .forEach { method ->
                    val key = "${method.projectedMethodName()}:${method.parameters.joinToString(",") { it.typeName }}"
                    if (emittedMethods.add(key)) {
                        builder.addFunction(renderJvmRuntimeClassForwardMethod(cacheName, method))
                    }
                }
            interfaceType.properties
                .filterNot(WinRtPropertyDefinition::isStatic)
                .filter { it.getterMethodName != null }
                .forEach { property ->
                    val propertyName = property.name.replaceFirstChar(Char::lowercase)
                    if (emittedProperties.add(propertyName)) {
                        builder.addProperty(renderJvmRuntimeClassForwardProperty(cacheName, property))
                    }
                }
        }
    }

    private fun renderJvmRuntimeClassForwardMethod(
        cacheName: String,
        method: WinRtMethodDefinition,
    ): FunSpec {
        val objectShape = runtimeObjectMethodShape(method)
        return FunSpec.builder(objectShape?.name ?: method.projectedMethodName())
            .addModifiers(KModifier.OVERRIDE)
            .addParameters(objectShape?.parameters ?: method.parameters.map { ParameterSpec.builder(it.name, baseRenderer.resolveTypeName(it.typeName)).build() })
            .returns(objectShape?.returnType ?: baseRenderer.resolveTypeName(method.returnTypeName))
            .addCode(
                if ((objectShape?.returnType ?: baseRenderer.resolveTypeName(method.returnTypeName)) == UNIT) {
                    "%L.%L(%L)\n"
                } else {
                    "return %L.%L(%L)\n"
                },
                cacheName,
                objectShape?.name ?: method.projectedMethodName(),
                (objectShape?.parameters?.map { it.name } ?: method.parameters.map { it.name }).joinToString(", ") { "`$it`" },
            )
            .build()
    }

    private fun renderJvmRuntimeClassForwardProperty(
        cacheName: String,
        property: WinRtPropertyDefinition,
    ): PropertySpec {
        val propertyName = property.name.replaceFirstChar(Char::lowercase)
        val builder = PropertySpec.builder(propertyName, baseRenderer.resolveTypeName(property.typeName))
            .mutable(!property.isReadOnly)
            .addModifiers(KModifier.OVERRIDE)
            .getter(FunSpec.getterBuilder().addCode("return %L.%L\n", cacheName, propertyName).build())
        if (!property.isReadOnly) {
            builder.setter(
                FunSpec.setterBuilder()
                    .addParameter("value", baseRenderer.resolveTypeName(property.typeName))
                    .addCode("%L.%L = value\n", cacheName, propertyName)
                    .build(),
            )
        }
        return builder.build()
    }

    private fun renderJvmInterfaceNativeProjection(plan: KotlinTypeProjectionPlan): TypeSpec {
        val abiShapes = linkedSetOf<List<KotlinProjectionComArgumentKind>>()
        val builder = TypeSpec.classBuilder("NativeProjection")
            .addModifiers(KModifier.PRIVATE)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("nativeObject", IUNKNOWN_REFERENCE_CLASS_NAME)
                    .build(),
            )
            .addSuperinterface(ClassName(plan.packageName, plan.type.name))
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

        val emittedMethods = mutableSetOf<String>()
        val emittedProperties = mutableSetOf<String>()
        baseRenderer.collectInterfaceProxyTypes(plan).forEach { interfaceType ->
            interfaceType.methods.filter(WinRtMethodDefinition::isOrdinaryProjectedMethod).forEach { method ->
                val key = projectedMethodSignatureKey(method)
                if (emittedMethods.add(key)) {
                    builder.addFunction(renderJvmInterfaceProxyMethod(interfaceType, method, plan.typesByQualifiedName, abiShapes))
                }
            }
            interfaceType.properties.filterNot(WinRtPropertyDefinition::isStatic).filter { it.getterMethodName != null }.forEach { property ->
                val propertyName = property.name.replaceFirstChar(Char::lowercase)
                if (emittedProperties.add(propertyName)) {
                    builder.addProperty(renderJvmInterfaceProxyProperty(interfaceType, property, plan.typesByQualifiedName, abiShapes))
                }
            }
        }
        if (abiShapes.isNotEmpty()) {
            builder.addType(renderJvmAbiHelper(abiShapes))
        }
        return builder.build()
    }

    private fun renderJvmInterfaceProxyMethod(
        slotInterfaceType: io.github.composefluent.winrt.metadata.WinRtTypeDefinition,
        method: WinRtMethodDefinition,
        typesByQualifiedName: Map<String, io.github.composefluent.winrt.metadata.WinRtTypeDefinition>,
        abiShapes: MutableSet<List<KotlinProjectionComArgumentKind>>,
    ): FunSpec {
        val returnBinding = baseRenderer.renderAbiTypeBinding(method.returnTypeName, typesByQualifiedName)
        val parameterBindings = method.parameters.map { parameter ->
            KotlinProjectionAbiParameterBinding(
                name = parameter.name,
                typeBinding = baseRenderer.renderAbiTypeBinding(parameter.typeName, typesByQualifiedName),
            )
        }
        val callPlan = baseRenderer.requireAbiCallPlan(
            bindingName = "${slotInterfaceType.qualifiedName}.${method.name}",
            returnBinding = returnBinding,
            parameterBindings = parameterBindings,
            suppressHResultCheck = method.isNoException,
        )
        val invocation = baseRenderer.renderInlineAbiInvocation(
            invokeTargetExpression = "nativeObject",
            slotExpression = baseRenderer.metadataSlotExpression(slotInterfaceType, method.abiSlotConstantName(slotInterfaceType.methods)),
            callPlan = callPlan,
            renderInvocation = { target, slot, arguments -> renderJvmFfmInvocation(target, slot, arguments, abiShapes) },
        ) ?: error("Generator interface proxy parity failed to emit ${method.name}")
        val objectShape = closableMethodShape(slotInterfaceType, method) ?: runtimeObjectMethodShape(method)
        return FunSpec.builder(objectShape?.name ?: method.projectedMethodName())
            .addModifiers(KModifier.OVERRIDE)
            .addMethodGenericParameters(method, objectShape)
            .addParameters(objectShape?.parameters ?: method.parameters.map { ParameterSpec.builder(it.name, baseRenderer.resolveTypeName(it.typeName)).build() })
            .returns(objectShape?.returnType ?: baseRenderer.resolveTypeName(method.returnTypeName))
            .addCode("%L\n", invocation)
            .build()
    }

    private fun renderJvmInterfaceProxyProperty(
        slotInterfaceType: io.github.composefluent.winrt.metadata.WinRtTypeDefinition,
        property: WinRtPropertyDefinition,
        typesByQualifiedName: Map<String, io.github.composefluent.winrt.metadata.WinRtTypeDefinition>,
        abiShapes: MutableSet<List<KotlinProjectionComArgumentKind>>,
    ): PropertySpec {
        val builder = PropertySpec.builder(
            property.name.replaceFirstChar(Char::lowercase),
            baseRenderer.resolveTypeName(property.typeName),
        )
            .mutable(!property.isReadOnly)
            .addModifiers(KModifier.OVERRIDE)
        val getterCallPlan = baseRenderer.requireAbiCallPlan(
            bindingName = "${slotInterfaceType.qualifiedName}.${property.name}.get",
            returnBinding = baseRenderer.renderAbiTypeBinding(property.typeName, typesByQualifiedName),
            parameterBindings = emptyList(),
            suppressHResultCheck = property.isNoException,
        )
        builder.getter(
            FunSpec.getterBuilder()
                .addCode(
                    "%L\n",
                    baseRenderer.renderInlineAbiInvocation(
                        invokeTargetExpression = "nativeObject",
                        slotExpression = CodeBlock.of("%T.Metadata.%L", baseRenderer.resolveTypeName(slotInterfaceType.qualifiedName), "${property.name.uppercase()}_GETTER_SLOT"),
                        callPlan = getterCallPlan,
                        renderInvocation = { target, slot, arguments -> renderJvmFfmInvocation(target, slot, arguments, abiShapes) },
                    ) ?: error("Generator interface proxy parity failed to emit getter ${property.name}"),
                )
                .build(),
        )
        if (!property.isReadOnly) {
            val setterCallPlan = baseRenderer.requireAbiCallPlan(
                bindingName = "${slotInterfaceType.qualifiedName}.${property.name}.set",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                parameterBindings = listOf(KotlinProjectionAbiParameterBinding("value", baseRenderer.renderAbiTypeBinding(property.typeName, typesByQualifiedName))),
                suppressHResultCheck = property.isNoException,
            )
            builder.setter(
                FunSpec.setterBuilder()
                    .addParameter("value", baseRenderer.resolveTypeName(property.typeName))
                    .addCode(
                        "%L\n",
                        baseRenderer.renderInlineAbiInvocation(
                            invokeTargetExpression = "nativeObject",
                            slotExpression = CodeBlock.of("%T.Metadata.%L", baseRenderer.resolveTypeName(slotInterfaceType.qualifiedName), "${property.name.uppercase()}_SETTER_SLOT"),
                            callPlan = setterCallPlan,
                            renderInvocation = { target, slot, arguments -> renderJvmFfmInvocation(target, slot, arguments, abiShapes) },
                        ) ?: error("Generator interface proxy parity failed to emit setter ${property.name}"),
                    )
                    .build(),
            )
        }
        return builder.build()
    }

    private fun renderJvmFfmInvocation(
        invokeTargetExpression: String,
        slotExpression: CodeBlock,
        abiArguments: List<KotlinProjectionComArgument>,
        abiShapes: MutableSet<List<KotlinProjectionComArgumentKind>>,
    ): CodeBlock {
        val kinds = abiArguments.map { it.kind }
        val shape = kinds.filterNotNull()
        if (shape.size != kinds.size || shape.any { it !in supportedJvmFfmKinds }) {
            return baseRenderer.renderComVtableInvocation(invokeTargetExpression, slotExpression, abiArguments)
        }
        abiShapes += shape
        return CodeBlock.builder()
            .add("JvmAbi.%L(instance = %L.pointer, slot = %L", jvmAbiInvokeFunctionName(shape), invokeTargetExpression, slotExpression)
            .apply {
                abiArguments.forEachIndexed { index, argument ->
                    add(", arg%L = %L", index, argument.expression)
                }
            }
            .add(")")
            .build()
    }

    private fun renderJvmAbiHelper(abiShapes: Set<List<KotlinProjectionComArgumentKind>>): TypeSpec =
        TypeSpec.objectBuilder("JvmAbi")
            .addModifiers(KModifier.PRIVATE)
            .addProperty(
                PropertySpec.builder("linker", ClassName_JAVA_LINKER)
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("%T.nativeLinker()", ClassName_JAVA_LINKER)
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("downcallHandles", ClassName_CONCURRENT_HASH_MAP.parameterizedBy(String::class.asClassName(), ClassName_METHOD_HANDLE))
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("%T()", ClassName_CONCURRENT_HASH_MAP)
                    .build(),
            )
            .apply {
                abiShapes.sortedWith(compareBy<List<KotlinProjectionComArgumentKind>> { it.size }.thenBy { it.joinToString("_") }).forEach { shape ->
                    addProperty(jvmDescriptorProperty(shape))
                    addFunction(jvmInvokeFunction(shape))
                }
                addFunction(jvmHandleFunction())
                addFunction(jvmVtableEntryFunction())
                addFunction(jvmSegmentFunction())
            }
            .build()

    private fun jvmDescriptorProperty(shape: List<KotlinProjectionComArgumentKind>): PropertySpec =
        PropertySpec.builder(jvmAbiDescriptorName(shape), ClassName_FUNCTION_DESCRIPTOR)
            .addModifiers(KModifier.PRIVATE)
            .initializer(jvmDescriptorInitializer(shape))
            .build()

    private fun jvmInvokeFunction(shape: List<KotlinProjectionComArgumentKind>): FunSpec =
        FunSpec.builder(jvmAbiInvokeFunctionName(shape))
            .addModifiers(KModifier.PRIVATE)
            .addParameter("instance", RAW_COM_PTR_CLASS_NAME)
            .addParameter("slot", Int::class)
            .apply {
                shape.forEachIndexed { index, kind ->
                    addParameter("arg$index", jvmKotlinParameterType(kind))
                }
            }
            .returns(Int::class)
            .addCode(
                CodeBlock.builder()
                    .addStatement("val instanceSegment = segment(instance.value)")
                    .add("return handle(instanceSegment, slot, %S, %L).invoke(instanceSegment", shape.jvmShapeSuffix(), jvmAbiDescriptorName(shape))
                    .apply {
                        shape.forEachIndexed { index, kind ->
                            add(", %L", jvmArgumentCarrierExpression(kind, "arg$index"))
                        }
                    }
                    .add(") as Int\n")
                    .build(),
            )
            .build()

    private fun jvmHandleFunction(): FunSpec =
        FunSpec.builder("handle")
            .addModifiers(KModifier.PRIVATE)
            .addParameter("instance", ClassName_MEMORY_SEGMENT)
            .addParameter("slot", Int::class)
            .addParameter("signature", String::class)
            .addParameter("descriptor", ClassName_FUNCTION_DESCRIPTOR)
            .returns(ClassName_METHOD_HANDLE)
            .addCode(
                """
                val function = vtableEntry(instance, slot)
                val key = "${'$'}{function.address()}:${'$'}signature"
                return downcallHandles.computeIfAbsent(key) {
                    linker.downcallHandle(%T.ofAddress(function.address()), descriptor)
                }
                """.trimIndent(),
                ClassName_MEMORY_SEGMENT,
            )
            .build()

    private fun jvmVtableEntryFunction(): FunSpec =
        FunSpec.builder("vtableEntry")
            .addModifiers(KModifier.PRIVATE)
            .addParameter("pointer", ClassName_MEMORY_SEGMENT)
            .addParameter("slot", Int::class)
            .returns(ClassName_MEMORY_SEGMENT)
            .addCode(
                """
                val objectMemory = pointer.reinterpret(%T.ADDRESS.byteSize())
                val vtable = objectMemory.get(%T.ADDRESS, 0)
                val requiredBytes = maxOf(24L, (slot + 1L) * %T.ADDRESS.byteSize())
                return vtable.reinterpret(requiredBytes).getAtIndex(%T.ADDRESS, slot.toLong())
                """.trimIndent(),
                ClassName_VALUE_LAYOUT,
                ClassName_VALUE_LAYOUT,
                ClassName_VALUE_LAYOUT,
                ClassName_VALUE_LAYOUT,
            )
            .build()

    private fun jvmSegmentFunction(): FunSpec =
        FunSpec.builder("segment")
            .addModifiers(KModifier.PRIVATE)
            .addParameter("address", Long::class)
            .returns(ClassName_MEMORY_SEGMENT)
            .addCode(
                """
                return if (address == 0L) {
                    %T.NULL
                } else {
                    %T.ofAddress(address).reinterpret(Long.MAX_VALUE)
                }
                """.trimIndent(),
                ClassName_MEMORY_SEGMENT,
                ClassName_MEMORY_SEGMENT,
            )
            .build()

    private fun jvmDescriptorInitializer(shape: List<KotlinProjectionComArgumentKind>): CodeBlock =
        CodeBlock.builder()
            .add("%T.of(%T.JAVA_INT, %T.ADDRESS", ClassName_FUNCTION_DESCRIPTOR, ClassName_VALUE_LAYOUT, ClassName_VALUE_LAYOUT)
            .apply {
                shape.forEach { kind ->
                    add(", %L", jvmValueLayoutCode(kind))
                }
            }
            .add(")")
            .build()

    private fun renderSourceSetFile(
        sourceSetPrefix: String,
        plan: KotlinTypeProjectionPlan,
        type: TypeSpec,
    ): KotlinProjectionFile {
        val contents = FileSpec.builder(plan.packageName, plan.type.name)
            .addType(type)
            .build()
            .toString()
        return KotlinProjectionFile(
            relativePath = "$sourceSetPrefix/${plan.relativePath}",
            packageName = plan.packageName,
            contents = contents,
        )
    }

    private fun prefixFile(prefix: String, file: KotlinProjectionFile): KotlinProjectionFile =
        KotlinProjectionFile(
            relativePath = "$prefix/${file.relativePath}",
            packageName = file.packageName,
            contents = file.contents,
        )

    private fun FunSpec.withModifier(modifier: KModifier): FunSpec =
        toBuilder().addModifiers(modifier).build()

    private fun PropertySpec.withModifier(modifier: KModifier): PropertySpec =
        toBuilder().addModifiers(modifier).build()
}

private val ClassName_FUNCTION_DESCRIPTOR = ClassName("java.lang.foreign", "FunctionDescriptor")
private val ClassName_JAVA_LINKER = ClassName("java.lang.foreign", "Linker")
private val ClassName_MEMORY_SEGMENT = ClassName("java.lang.foreign", "MemorySegment")
private val ClassName_VALUE_LAYOUT = ClassName("java.lang.foreign", "ValueLayout")
private val ClassName_METHOD_HANDLE = ClassName("java.lang.invoke", "MethodHandle")
private val ClassName_CONCURRENT_HASH_MAP = ClassName("java.util.concurrent", "ConcurrentHashMap")
private val RAW_COM_PTR_CLASS_NAME = ClassName("io.github.composefluent.winrt.runtime", "RawComPtr")

private val supportedJvmFfmKinds = setOf(
    KotlinProjectionComArgumentKind.Pointer,
    KotlinProjectionComArgumentKind.Int8,
    KotlinProjectionComArgumentKind.Int16,
    KotlinProjectionComArgumentKind.Int32,
    KotlinProjectionComArgumentKind.Int64,
    KotlinProjectionComArgumentKind.Float,
    KotlinProjectionComArgumentKind.Double,
)

private fun jvmAbiDescriptorName(shape: List<KotlinProjectionComArgumentKind>): String =
    "descriptor_${shape.jvmShapeSuffix()}"

private fun jvmAbiInvokeFunctionName(shape: List<KotlinProjectionComArgumentKind>): String =
    "invoke_${shape.jvmShapeSuffix()}"

private fun List<KotlinProjectionComArgumentKind>.jvmShapeSuffix(): String =
    if (isEmpty()) {
        "none"
    } else {
        joinToString("_") { kind ->
            when (kind) {
                KotlinProjectionComArgumentKind.Pointer -> "p"
                KotlinProjectionComArgumentKind.Int8 -> "i8"
                KotlinProjectionComArgumentKind.Int16 -> "i16"
                KotlinProjectionComArgumentKind.Int32 -> "i32"
                KotlinProjectionComArgumentKind.Int64 -> "i64"
                KotlinProjectionComArgumentKind.Float -> "f32"
                KotlinProjectionComArgumentKind.Double -> "f64"
            }
        }
    }

private fun jvmKotlinParameterType(kind: KotlinProjectionComArgumentKind): TypeName =
    when (kind) {
        KotlinProjectionComArgumentKind.Pointer -> RAW_ADDRESS_CLASS_NAME
        KotlinProjectionComArgumentKind.Int8 -> Byte::class.asClassName()
        KotlinProjectionComArgumentKind.Int16 -> Short::class.asClassName()
        KotlinProjectionComArgumentKind.Int32 -> Int::class.asClassName()
        KotlinProjectionComArgumentKind.Int64 -> Long::class.asClassName()
        KotlinProjectionComArgumentKind.Float -> Float::class.asClassName()
        KotlinProjectionComArgumentKind.Double -> Double::class.asClassName()
    }

private fun jvmValueLayoutCode(kind: KotlinProjectionComArgumentKind): CodeBlock =
    when (kind) {
        KotlinProjectionComArgumentKind.Pointer -> CodeBlock.of("%T.ADDRESS", ClassName_VALUE_LAYOUT)
        KotlinProjectionComArgumentKind.Int8 -> CodeBlock.of("%T.JAVA_BYTE", ClassName_VALUE_LAYOUT)
        KotlinProjectionComArgumentKind.Int16 -> CodeBlock.of("%T.JAVA_SHORT", ClassName_VALUE_LAYOUT)
        KotlinProjectionComArgumentKind.Int32 -> CodeBlock.of("%T.JAVA_INT", ClassName_VALUE_LAYOUT)
        KotlinProjectionComArgumentKind.Int64 -> CodeBlock.of("%T.JAVA_LONG", ClassName_VALUE_LAYOUT)
        KotlinProjectionComArgumentKind.Float -> CodeBlock.of("%T.JAVA_FLOAT", ClassName_VALUE_LAYOUT)
        KotlinProjectionComArgumentKind.Double -> CodeBlock.of("%T.JAVA_DOUBLE", ClassName_VALUE_LAYOUT)
    }

private fun jvmArgumentCarrierExpression(
    kind: KotlinProjectionComArgumentKind,
    argumentName: String,
): CodeBlock =
    when (kind) {
        KotlinProjectionComArgumentKind.Pointer -> CodeBlock.of("segment(%L.value)", argumentName)
        KotlinProjectionComArgumentKind.Int8,
        KotlinProjectionComArgumentKind.Int16,
        KotlinProjectionComArgumentKind.Int32,
        KotlinProjectionComArgumentKind.Int64,
        KotlinProjectionComArgumentKind.Float,
        KotlinProjectionComArgumentKind.Double -> CodeBlock.of("%L", argumentName)
    }
