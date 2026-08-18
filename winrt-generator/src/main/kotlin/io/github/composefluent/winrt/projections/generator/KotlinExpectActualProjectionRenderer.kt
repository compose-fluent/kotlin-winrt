package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRTMethodDefinition
import io.github.composefluent.winrt.metadata.WinRTEventDefinition
import io.github.composefluent.winrt.metadata.WinRTPropertyDefinition
import io.github.composefluent.winrt.metadata.WinRTTypeKind
import io.github.composefluent.winrt.metadata.isWinRTObjectTypeName
import io.github.composefluent.winrt.metadata.metadataParameterCategoryFor
import io.github.composefluent.winrt.metadata.projectedPropertyTypeName
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

internal class KotlinExpectActualProjectionRenderer(
    private val baseRenderer: KotlinProjectionRenderer,
) : KotlinProjectionFileRenderer {
    override fun render(plan: KotlinTypeProjectionPlan): List<KotlinProjectionFile> {
        return when {
            canRenderExpectActualInterfaceSlice(plan) -> listOf(
                renderCommonInterface(plan),
                renderJvmInterfaceProjectionSupport(plan),
            )
            canRenderExpectActualRuntimeClassSlice(plan) -> listOf(
                renderCommonExpectRuntimeClass(plan),
                renderJvmActualRuntimeClass(plan),
            )
            else -> listOf(prefixFile("commonMain/kotlin", baseRenderer.render(plan)))
        }
    }

    private fun canRenderExpectActualInterfaceSlice(plan: KotlinTypeProjectionPlan): Boolean {
        if (
            plan.declarationKind != KotlinProjectionDeclarationKind.Interface ||
            plan.type.kind != WinRTTypeKind.Interface ||
            plan.type.genericParameterCount != 0 ||
            plan.mutableCollectionBindings.isNotEmpty() ||
            plan.readOnlyCollectionBindings.isNotEmpty()
        ) {
            return false
        }
        val interfaceProxyTypes = baseRenderer.collectInterfaceProxyTypes(plan)
        return interfaceProxyMembersAreConflictFree(interfaceProxyTypes, plan.typesByQualifiedName) &&
            interfaceProxyTypes.all { interfaceType ->
                canRenderExpectActualInterfaceType(plan, interfaceType)
            }
    }

    private fun canRenderExpectActualRuntimeClassSlice(plan: KotlinTypeProjectionPlan): Boolean {
        if (
            plan.declarationKind != KotlinProjectionDeclarationKind.Class ||
            plan.type.kind != WinRTTypeKind.RuntimeClass ||
            plan.type.genericParameterCount != 0 ||
            plan.type.baseTypeName?.let { !isWinRTObjectTypeName(it) } == true ||
            plan.type.methods.any(WinRTMethodDefinition::isStatic) ||
            plan.type.properties.any(WinRTPropertyDefinition::isStatic) ||
            plan.type.properties.any { !it.hasNativeProjectionGetterAccessor() } ||
            plan.type.events.any(WinRTEventDefinition::isStatic) ||
            plan.staticInterfaceNames.isNotEmpty() ||
            plan.activatableFactoryInterfaceName != null ||
            plan.composableFactoryBindings.isNotEmpty() ||
            KotlinProjectionCompanionKind.ActivationFactory in plan.companionKinds ||
            KotlinProjectionCompanionKind.StaticInterfaces in plan.companionKinds ||
            KotlinProjectionCompanionKind.ComposableFactory in plan.companionKinds ||
            KotlinProjectionSpecializationKind.StaticClass in plan.specializationKinds ||
            KotlinProjectionSpecializationKind.AttributeClass in plan.specializationKinds ||
            plan.mutableCollectionBindings.isNotEmpty() ||
            plan.readOnlyCollectionBindings.isNotEmpty()
        ) {
            return false
        }
        val publicInterfaces = publicRuntimeClassInterfaces(plan)
        if (publicInterfaces.isEmpty()) {
            return false
        }
        val interfaceProxyTypes = publicRuntimeClassInterfaceProxyTypes(plan, publicInterfaces)
        return interfaceProxyTypes.all { interfaceType ->
            canRenderExpectActualInterfaceType(plan, interfaceType)
        } &&
            interfaceProxyMembersAreConflictFree(interfaceProxyTypes, plan.typesByQualifiedName) &&
            runtimeClassMembersAreCoveredByPublicInterface(plan, interfaceProxyTypes)
    }

    private fun canRenderExpectActualInterfaceType(
        plan: KotlinTypeProjectionPlan,
        type: io.github.composefluent.winrt.metadata.WinRTTypeDefinition,
    ): Boolean =
            type.kind == WinRTTypeKind.Interface &&
            type.genericParameterCount == 0 &&
            type.methods.all { it.genericParameterCount == 0 } &&
            type.methods.none(WinRTMethodDefinition::isStatic) &&
            type.properties.none(WinRTPropertyDefinition::isStatic) &&
            type.properties.all { it.hasNativeProjectionPropertyAccessor() } &&
            type.events.none(WinRTEventDefinition::isStatic) &&
            type.events.all { event -> event.hasNativeProjectionAccessorPair() } &&
            type.properties
                .filterNot(WinRTPropertyDefinition::isStatic)
                .filter { it.hasNativeProjectionPropertyAccessor() }
                .all { property ->
                    val getterAvailable = if (property.hasNativeProjectionGetterAccessor()) {
                        true
                    } else {
                        findNativeProjectionGetterInterface(type, property, plan.typesByQualifiedName) != null
                    }
                    getterAvailable && (
                        property.isReadOnly ||
                            property.hasNativeProjectionSetterAccessor()
                        )
                }

    private fun publicRuntimeClassInterfaces(plan: KotlinTypeProjectionPlan): List<io.github.composefluent.winrt.metadata.WinRTTypeDefinition> =
        plan.type.implementedInterfaces
            .filter { implemented -> isPublicRuntimeClassInterface(plan, implemented.interfaceName) }
            .mapNotNull { implemented -> plan.typesByQualifiedName[implemented.interfaceName.rawWinRTTypeName()] }
            .distinctBy { it.qualifiedName }

    private fun publicRuntimeClassInterfaceProxyTypes(plan: KotlinTypeProjectionPlan): List<io.github.composefluent.winrt.metadata.WinRTTypeDefinition> =
        publicRuntimeClassInterfaceProxyTypes(plan, publicRuntimeClassInterfaces(plan))

    private fun publicRuntimeClassInterfaceProxyTypes(
        plan: KotlinTypeProjectionPlan,
        publicInterfaces: List<io.github.composefluent.winrt.metadata.WinRTTypeDefinition>,
    ): List<io.github.composefluent.winrt.metadata.WinRTTypeDefinition> =
        publicInterfaces
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
        val rawName = interfaceName.rawWinRTTypeName()
        val descriptor = plan.classMemberMergeDescriptor
            ?.interfaceDescriptors
            ?.firstOrNull { it.interfaceTypeName == rawName }
        return descriptor?.let { !it.isOverridableInterface && !it.isProtectedInterface } ?: true
    }

    private fun String.rawWinRTTypeName(): String =
        substringBefore('<').removeSuffix("?")

    private fun runtimeClassMembersAreCoveredByPublicInterface(
        plan: KotlinTypeProjectionPlan,
        interfaceTypes: List<io.github.composefluent.winrt.metadata.WinRTTypeDefinition>,
    ): Boolean {
        if (interfaceTypes.isEmpty()) {
            return false
        }
        val interfaceMethods = interfaceTypes.flatMap { interfaceType ->
            interfaceType.methods
                .filter(WinRTMethodDefinition::isOrdinaryProjectedMethod)
                .map { method -> projectedMethodSignatureKey(method) to methodCoverage(method) }
        }.toMap()
        val interfaceProperties = interfaceTypes.flatMap { interfaceType ->
            interfaceType.properties
                .filterNot(WinRTPropertyDefinition::isStatic)
                .filter { it.hasNativeProjectionGetterAccessor() }
                .map { property ->
                    property.name.replaceFirstChar(Char::lowercase) to propertyCoverage(interfaceType.qualifiedName, property, plan.typesByQualifiedName)
                }
        }.toMap()
        val interfaceEvents = interfaceTypes.flatMap { interfaceType ->
            interfaceType.events
                .filterNot(WinRTEventDefinition::isStatic)
                .map { event -> event.name.replaceFirstChar(Char::lowercase) to eventCoverage(event) }
        }.toMap()
        val classMethodsCovered = plan.type.methods
            .filter(WinRTMethodDefinition::isOrdinaryProjectedMethod)
            .all { method -> interfaceMethods[projectedMethodSignatureKey(method)] == methodCoverage(method) }
        val classPropertiesCovered = plan.type.properties
            .filterNot(WinRTPropertyDefinition::isStatic)
            .filter { it.hasNativeProjectionGetterAccessor() }
            .all { property ->
                val interfaceCoverage = interfaceProperties[property.name.replaceFirstChar(Char::lowercase)]
                    ?: return@all false
                val classCoverage = propertyCoverage(plan.type.qualifiedName, property, plan.typesByQualifiedName)
                interfaceCoverage.coversRuntimeClassProperty(classCoverage)
            }
        val classEventsCovered = plan.type.events
            .filterNot(WinRTEventDefinition::isStatic)
            .all { event ->
                interfaceEvents[event.name.replaceFirstChar(Char::lowercase)] == eventCoverage(event)
            }
        return classMethodsCovered && classPropertiesCovered && classEventsCovered
    }

    private fun interfaceProxyMembersAreConflictFree(
        interfaceTypes: List<io.github.composefluent.winrt.metadata.WinRTTypeDefinition>,
        typesByQualifiedName: Map<String, io.github.composefluent.winrt.metadata.WinRTTypeDefinition>,
    ): Boolean {
        val methods = mutableMapOf<String, RuntimeClassMethodCoverage>()
        val properties = mutableMapOf<String, RuntimeClassPropertyCoverage>()
        val events = mutableMapOf<String, RuntimeClassEventCoverage>()
        interfaceTypes.forEach { interfaceType ->
            interfaceType.methods
                .filter(WinRTMethodDefinition::isOrdinaryProjectedMethod)
                .forEach { method ->
                    val key = projectedMethodSignatureKey(method)
                    val coverage = methodCoverage(method)
                    val previous = methods.putIfAbsent(key, coverage)
                    if (previous != null && previous != coverage) {
                        return false
                    }
                }
            interfaceType.properties
                .filterNot(WinRTPropertyDefinition::isStatic)
                .filter { it.hasNativeProjectionGetterAccessor() }
                .forEach { property ->
                    val key = property.name.replaceFirstChar(Char::lowercase)
                    val coverage = propertyCoverage(interfaceType.qualifiedName, property, typesByQualifiedName)
                    val previous = properties.putIfAbsent(key, coverage)
                    if (previous != null && previous != coverage) {
                        return false
                    }
                }
            interfaceType.events
                .filterNot(WinRTEventDefinition::isStatic)
                .forEach { event ->
                    val key = event.name.replaceFirstChar(Char::lowercase)
                    val coverage = eventCoverage(event)
                    val previous = events.putIfAbsent(key, coverage)
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
        val isNoException: Boolean,
    )

    private data class RuntimeClassPropertyCoverage(
        val typeName: String,
        val isReadOnly: Boolean,
        val getterMethodName: String?,
        val setterMethodName: String?,
        val isNoException: Boolean,
    )

    private data class RuntimeClassEventCoverage(
        val delegateTypeName: String,
        val addMethodName: String?,
        val removeMethodName: String?,
    )

    private fun methodCoverage(method: WinRTMethodDefinition): RuntimeClassMethodCoverage =
        RuntimeClassMethodCoverage(
            returnTypeName = method.returnTypeName,
            parameters = method.parameters.map { it.name to it.typeName },
            isNoException = method.isNoException,
        )

    private fun propertyCoverage(
        ownerTypeName: String,
        property: WinRTPropertyDefinition,
        typesByQualifiedName: Map<String, io.github.composefluent.winrt.metadata.WinRTTypeDefinition>,
    ): RuntimeClassPropertyCoverage =
        RuntimeClassPropertyCoverage(
            typeName = property.projectedPropertyTypeName(ownerTypeName, typesByQualifiedName),
            isReadOnly = property.isReadOnly,
            getterMethodName = property.getterMethodName,
            setterMethodName = property.setterMethodName,
            isNoException = property.isNoException,
        )

    private fun RuntimeClassPropertyCoverage.coversRuntimeClassProperty(
        runtimeClassProperty: RuntimeClassPropertyCoverage,
    ): Boolean =
        typeName == runtimeClassProperty.typeName &&
            getterMethodName == runtimeClassProperty.getterMethodName &&
            isNoException == runtimeClassProperty.isNoException &&
            (runtimeClassProperty.isReadOnly ||
                (!isReadOnly && setterMethodName == runtimeClassProperty.setterMethodName))

    private fun eventCoverage(event: WinRTEventDefinition): RuntimeClassEventCoverage =
        RuntimeClassEventCoverage(
            delegateTypeName = event.delegateTypeName,
            addMethodName = event.addMethodName,
            removeMethodName = event.removeMethodName,
        )

    private fun projectedMethodSignatureKey(method: WinRTMethodDefinition): String =
        "${method.projectedMethodName()}:${method.parameters.joinToString(",") { it.typeName }}"

    private fun renderCommonInterface(plan: KotlinTypeProjectionPlan): KotlinProjectionFile {
        val builder = TypeSpec.interfaceBuilder(plan.type.name)
        baseRenderer.applyCommonTypeShape(builder, plan)
        plan.type.implementedInterfaces.forEach { implemented ->
            builder.addSuperinterface(baseRenderer.resolveTypeName(implemented.interfaceName))
        }
        builder.addSuperinterface(WINRT_MANAGED_PROJECTION_STATE_ACCESS_CLASS_NAME)
        plan.type.methods
            .filter(WinRTMethodDefinition::isOrdinaryProjectedMethod)
            .forEach { method -> builder.addFunction(baseRenderer.renderInterfaceMethod(method)) }
        plan.type.properties
            .filterNot(WinRTPropertyDefinition::isStatic)
            .filter { it.hasNativeProjectionPropertyAccessor() }
            .forEach { property ->
                val getterResolution = property
                    .takeIf { !it.hasNativeProjectionGetterAccessor() && it.hasNativeProjectionSetterAccessor() }
                    ?.let { findNativeProjectionGetterInterface(plan.type, it, plan.typesByQualifiedName) }
                builder.addProperty(
                    baseRenderer.renderInterfaceProperty(
                        plan.type.qualifiedName,
                        property,
                        plan.typesByQualifiedName,
                        override = getterResolution?.fromBaseInterface == true,
                    ),
                )
            }
        plan.type.events.filterNot(WinRTEventDefinition::isStatic).forEach { event ->
            builder.addProperty(baseRenderer.renderEventProperty(event, eventInvokeDescriptor = null, abstract = true))
            baseRenderer.renderEventFunctions(event, abstract = true).forEach(builder::addFunction)
        }
        builder.addType(renderCommonInterfaceMetadata(plan))
        return renderSourceSetFile("commonMain/kotlin", plan, builder.build())
    }

    private fun renderJvmInterfaceProjectionSupport(plan: KotlinTypeProjectionPlan): KotlinProjectionFile =
        renderSourceSetFile(
            "jvmMain/kotlin",
            plan,
            TypeSpec.objectBuilder(jvmInterfaceProjectionSupportClassName(plan).simpleName)
                .addModifiers(KModifier.INTERNAL)
                .addFunction(
                    FunSpec.builder("wrap")
                        .addParameter("instance", IUNKNOWN_REFERENCE_CLASS_NAME)
                        .returns(baseRenderer.resolveTypeName(plan.type.qualifiedName))
                        .addCode("return NativeProjection(instance)\n")
                        .build(),
                )
                .addType(renderJvmInterfaceNativeProjection(plan))
                .build(),
        )

    private fun renderCommonInterfaceMetadata(plan: KotlinTypeProjectionPlan): TypeSpec =
        TypeSpec.companionObjectBuilder("Metadata")
            .addProperty(
                PropertySpec.builder("TYPE_NAME", String::class)
                    .addModifiers(KModifier.CONST)
                    .initializer("%S", plan.type.qualifiedName)
                    .build(),
            )
            .apply {
                plan.interfaceIid?.let { iid ->
                    addProperty(
                        PropertySpec.builder("IID", GUID_CLASS_NAME)
                            .initializer("%T(%S)", GUID_CLASS_NAME, iid.toString())
                            .build(),
                    )
                    addProperty(
                        PropertySpec.builder("TYPE_HANDLE", WINRT_TYPE_HANDLE_CLASS_NAME)
                            .initializer("%T(%S, IID)", WINRT_TYPE_HANDLE_CLASS_NAME, ClassName(plan.packageName, plan.type.name).canonicalName)
                            .build(),
                    )
                }
                plan.abiSlotBindings.forEach { binding ->
                    addProperty(
                        PropertySpec.builder(binding.constantName, Int::class)
                            .addModifiers(KModifier.INTERNAL, KModifier.CONST)
                            .initializer("%L", binding.slot)
                            .build(),
                    )
                }
            }
            .build()

    private fun renderCommonExpectRuntimeClass(plan: KotlinTypeProjectionPlan): KotlinProjectionFile {
        val builder = TypeSpec.classBuilder(plan.type.name)
            .addModifiers(KModifier.EXPECT)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addModifiers(plan.runtimeClassWrapperConstructorVisibility())
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
        val proxyTypesByName = publicRuntimeClassInterfaceProxyTypes(plan).associateBy { it.qualifiedName }
        publicRuntimeClassInterfaces(plan).forEach { interfaceType ->
            val typeName = baseRenderer.resolveTypeName(interfaceType.qualifiedName)
            if (proxyTypesByName.containsKey(interfaceType.qualifiedName)) {
                builder.addSuperinterface(
                    typeName,
                    CodeBlock.of(
                        "%T.wrap(Metadata.acquireInterface(_inner, %T.Metadata.IID))",
                        jvmInterfaceProjectionSupportClassName(plan, interfaceType),
                        typeName,
                    ),
                )
            } else {
                builder.addSuperinterface(typeName)
            }
        }
        builder.addSuperinterface(IWINRT_OBJECT_CLASS_NAME)
        builder.primaryConstructor(
            FunSpec.constructorBuilder()
                .addModifiers(KModifier.ACTUAL)
                .addModifiers(plan.runtimeClassWrapperConstructorVisibility())
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
        if (plan.type.genericParameterCount == 0 && plan.defaultInterfaceIid != null) {
            builder.addProperty(
                PropertySpec.builder("primaryTypeHandle", WINRT_TYPE_HANDLE_CLASS_NAME.copy(nullable = true))
                    .addModifiers(KModifier.OVERRIDE)
                    .getter(
                        FunSpec.getterBuilder()
                            .addCode("return Metadata.TYPE_HANDLE\n")
                            .build(),
                    )
                    .build(),
            )
        }
        addJvmRuntimeClassInterfaceForwards(builder, plan, delegatedInterfaceNames = proxyTypesByName.keys)
        builder.addType(baseRenderer.buildMetadataCompanionShell(plan, emptyList(), emptyList(), emptyList()))
        baseRenderer.appendCompanionShells(builder, plan, excludeKinds = setOf(KotlinProjectionCompanionKind.Metadata))
        return renderSourceSetFile("jvmMain/kotlin", plan, builder.build())
    }

    private fun addJvmRuntimeClassInterfaceForwards(
        builder: TypeSpec.Builder,
        plan: KotlinTypeProjectionPlan,
        delegatedInterfaceNames: Set<String> = emptySet(),
    ) {
        val emittedMethods = mutableSetOf<String>()
        val emittedProperties = mutableSetOf<String>()
        val emittedEvents = mutableSetOf<String>()
        publicRuntimeClassInterfaceProxyTypes(plan).forEach { interfaceType ->
            if (interfaceType.qualifiedName in delegatedInterfaceNames) {
                return@forEach
            }
            val cacheName = "_${interfaceType.name.replaceFirstChar(Char::lowercase)}"
            builder.addProperty(
                PropertySpec.builder(cacheName, baseRenderer.resolveTypeName(interfaceType.qualifiedName))
                    .addModifiers(KModifier.PRIVATE)
                    .delegate(
                        CodeBlock.of(
                            "lazy(%T.PUBLICATION) { %T.wrap(Metadata.acquireInterface(_inner, %T.Metadata.IID)) }",
                            LAZY_THREAD_SAFETY_MODE_CLASS_NAME,
                            jvmInterfaceProjectionSupportClassName(plan, interfaceType),
                            baseRenderer.resolveTypeName(interfaceType.qualifiedName),
                        ),
                    )
                    .build(),
            )
            interfaceType.methods
                .filter(WinRTMethodDefinition::isOrdinaryProjectedMethod)
                .forEach { method ->
                    val key = "${method.projectedMethodName()}:${method.parameters.joinToString(",") { it.typeName }}"
                    if (emittedMethods.add(key)) {
                        builder.addFunction(renderJvmRuntimeClassForwardMethod(cacheName, method))
                    }
                }
            interfaceType.properties
                .filterNot(WinRTPropertyDefinition::isStatic)
                .filter { it.hasNativeProjectionGetterAccessor() }
                .forEach { property ->
                    val propertyName = property.name.replaceFirstChar(Char::lowercase)
                    if (emittedProperties.add(propertyName)) {
                        builder.addProperty(renderJvmRuntimeClassForwardProperty(cacheName, interfaceType.qualifiedName, property, plan.typesByQualifiedName))
                    }
                }
            interfaceType.events
                .filterNot(WinRTEventDefinition::isStatic)
                .forEach { event ->
                    val eventName = event.name.replaceFirstChar(Char::lowercase)
                    if (emittedEvents.add(eventName)) {
                        builder.addProperty(renderJvmRuntimeClassForwardEventProperty(cacheName, event))
                        renderJvmRuntimeClassForwardEventFunctions(cacheName, event).forEach(builder::addFunction)
                    }
                }
        }
    }

    private fun renderJvmRuntimeClassForwardMethod(
        cacheName: String,
        method: WinRTMethodDefinition,
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
        ownerTypeName: String,
        property: WinRTPropertyDefinition,
        typesByQualifiedName: Map<String, io.github.composefluent.winrt.metadata.WinRTTypeDefinition>,
    ): PropertySpec {
        val propertyName = property.name.replaceFirstChar(Char::lowercase)
        val propertyTypeName = property.projectedPropertyTypeName(ownerTypeName, typesByQualifiedName)
        val builder = PropertySpec.builder(propertyName, baseRenderer.resolveTypeName(propertyTypeName))
            .mutable(!property.isReadOnly)
            .addModifiers(KModifier.OVERRIDE)
            .getter(FunSpec.getterBuilder().addCode("return %L.%N\n", cacheName, propertyName).build())
        if (!property.isReadOnly) {
            builder.setter(
                FunSpec.setterBuilder()
                    .addParameter("value", baseRenderer.resolveTypeName(propertyTypeName))
                    .addCode("%L.%N = value\n", cacheName, propertyName)
                    .build(),
            )
        }
        return builder.build()
    }

    private fun renderJvmRuntimeClassForwardEventProperty(
        cacheName: String,
        event: WinRTEventDefinition,
    ): PropertySpec {
        val propertyName = event.name.replaceFirstChar(Char::lowercase)
        return PropertySpec.builder(
            propertyName,
            WINRT_EVENT_CLASS_NAME.parameterizedBy(baseRenderer.resolveTypeName(event.delegateTypeName)),
        )
            .addModifiers(KModifier.OVERRIDE)
            .getter(FunSpec.getterBuilder().addCode("return %L.%L\n", cacheName, propertyName).build())
            .build()
    }

    private fun renderJvmRuntimeClassForwardEventFunctions(
        cacheName: String,
        event: WinRTEventDefinition,
    ): List<FunSpec> {
        val typeName = baseRenderer.resolveTypeName(event.delegateTypeName)
        return listOf(
            FunSpec.builder("add${event.name}")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("handler", typeName)
                .returns(EVENT_REGISTRATION_TOKEN_CLASS_NAME)
                .addCode("return %L.add%L(handler)\n", cacheName, event.name)
                .build(),
            FunSpec.builder("remove${event.name}")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("token", EVENT_REGISTRATION_TOKEN_CLASS_NAME)
                .addCode("%L.remove%L(token)\n", cacheName, event.name)
                .build(),
        )
    }

    private fun renderJvmInterfaceNativeProjection(plan: KotlinTypeProjectionPlan): TypeSpec {
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
                            .apply {
                                if (plan.interfaceIid == null) {
                                    addCode("return null\n")
                                } else {
                                    addCode("return %T.Metadata.TYPE_HANDLE\n", ClassName(plan.packageName, plan.type.name))
                                }
                            }
                            .build(),
                    )
                    .build(),
            )

        val emittedMethods = mutableSetOf<String>()
        val emittedProperties = mutableSetOf<String>()
        val emittedEvents = mutableSetOf<String>()
        baseRenderer.collectInterfaceProxyTypes(plan).forEach { interfaceType ->
            interfaceType.methods.filter(WinRTMethodDefinition::isOrdinaryProjectedMethod).forEach { method ->
                val key = projectedMethodSignatureKey(method)
                if (emittedMethods.add(key)) {
                    builder.addFunction(renderJvmInterfaceProxyMethod(interfaceType, method, plan.typesByQualifiedName))
                }
            }
            interfaceType.properties.filterNot(WinRTPropertyDefinition::isStatic).filter { it.hasNativeProjectionPropertyAccessor() }.forEach { property ->
                val propertyName = property.name.replaceFirstChar(Char::lowercase)
                if (emittedProperties.add(propertyName)) {
                    builder.addProperty(renderJvmInterfaceProxyProperty(interfaceType, property, plan.typesByQualifiedName))
                }
            }
            interfaceType.events.filterNot(WinRTEventDefinition::isStatic).forEach { event ->
                val eventName = event.name.replaceFirstChar(Char::lowercase)
                if (emittedEvents.add(eventName)) {
                    builder.addProperty(
                        baseRenderer.renderEventProperty(
                            event = event,
                            eventInvokeDescriptor = null,
                            abstract = false,
                            override = true,
                            eventSourceOwnerTypeName = interfaceType.qualifiedName,
                            eventSourceEventTypeName = plan.typesByQualifiedName[interfaceType.qualifiedName]
                                ?.events
                                ?.firstOrNull { rawEvent -> rawEvent.name == event.name }
                                ?.delegateTypeName,
                            eventSourceObjectReference = baseRenderer.interfaceNativeProjectionEventSourceObjectReference(plan, interfaceType),
                            eventSourceAddSlot = baseRenderer.metadataSlotExpression(interfaceType, "${event.name.uppercase()}_ADD_SLOT"),
                            fallbackToAddRemove = false,
                        ),
                    )
                    baseRenderer.renderInterfaceProxyEventFunctions(interfaceType, event).forEach(builder::addFunction)
                }
            }
        }
        return builder.build()
    }

    private fun renderJvmInterfaceProxyMethod(
        slotInterfaceType: io.github.composefluent.winrt.metadata.WinRTTypeDefinition,
        method: WinRTMethodDefinition,
        typesByQualifiedName: Map<String, io.github.composefluent.winrt.metadata.WinRTTypeDefinition>,
    ): FunSpec {
        val returnBinding = baseRenderer.renderAbiTypeBinding(method.returnTypeName, typesByQualifiedName, slotInterfaceType.namespace)
        val parameterBindings = method.parameters.map { parameter ->
            KotlinProjectionAbiParameterBinding(
                name = parameter.name,
                typeBinding = baseRenderer.renderAbiTypeBinding(parameter.typeName, typesByQualifiedName, slotInterfaceType.namespace),
                category = metadataParameterCategoryFor(parameter),
            )
        }
        val callPlan = buildJvmInterfaceAbiCallPlan(
            returnBinding = returnBinding,
            parameterBindings = parameterBindings,
            suppressHResultCheck = method.isNoException,
            typesByQualifiedName = typesByQualifiedName,
        ) ?: error("Generator interface proxy parity failed to plan ${slotInterfaceType.qualifiedName}.${method.name}")
        val invocation = baseRenderer.renderInlineAbiInvocation(
            invokeTargetExpression = "nativeObject",
            slotExpression = baseRenderer.metadataSlotExpression(slotInterfaceType, method.abiSlotConstantName(slotInterfaceType.methods)),
            callPlan = callPlan,
        )
        val objectShape = closableMethodShape(slotInterfaceType, method) ?: runtimeObjectMethodShape(method)
        return FunSpec.builder(objectShape?.name ?: method.projectedMethodName())
            .addModifiers(KModifier.OVERRIDE)
            .addMethodGenericParameters(method, objectShape)
            .addParameters(objectShape?.parameters ?: method.projectedKotlinParameters().map { ParameterSpec.builder(it.name, baseRenderer.resolveTypeName(it.typeName)).build() })
            .returns(objectShape?.returnType ?: baseRenderer.resolveTypeName(method.projectedKotlinReturnTypeName()))
            .addCode("%L\n", invocation)
            .build()
    }

    private fun renderJvmInterfaceProxyProperty(
        slotInterfaceType: io.github.composefluent.winrt.metadata.WinRTTypeDefinition,
        property: WinRTPropertyDefinition,
        typesByQualifiedName: Map<String, io.github.composefluent.winrt.metadata.WinRTTypeDefinition>,
    ): PropertySpec {
        val propertyTypeName = property.projectedPropertyTypeName(slotInterfaceType.qualifiedName, typesByQualifiedName)
        val builder = PropertySpec.builder(
            property.name.replaceFirstChar(Char::lowercase),
            baseRenderer.resolveTypeName(propertyTypeName),
        )
            .mutable(!property.isReadOnly)
            .addModifiers(KModifier.OVERRIDE)
        if (property.hasNativeProjectionGetterAccessor()) {
            val getterCallPlan = buildJvmInterfaceAbiCallPlan(
                returnBinding = baseRenderer.renderAbiTypeBinding(propertyTypeName, typesByQualifiedName, slotInterfaceType.namespace),
                parameterBindings = emptyList(),
                suppressHResultCheck = property.isNoException,
                typesByQualifiedName = typesByQualifiedName,
            ) ?: error("Generator interface proxy parity failed to plan getter ${slotInterfaceType.qualifiedName}.${property.name}")
            builder.getter(
                FunSpec.getterBuilder()
                    .addCode(
                        "%L\n",
                        baseRenderer.renderInlineAbiInvocation(
                            invokeTargetExpression = "nativeObject",
                            slotExpression = CodeBlock.of("%T.Metadata.%L", baseRenderer.resolveTypeName(slotInterfaceType.qualifiedName), "${property.name.uppercase()}_GETTER_SLOT"),
                            callPlan = getterCallPlan,
                        ),
                    )
                    .build(),
            )
        } else {
            val getterInterfaceType = findNativeProjectionGetterInterface(slotInterfaceType, property, typesByQualifiedName)?.interfaceType
                ?: error("Could not find property getter interface for ${slotInterfaceType.qualifiedName}.${property.name}")
            val getterInterfaceClassName = baseRenderer.resolveTypeName(getterInterfaceType.qualifiedName)
            builder.getter(
                FunSpec.getterBuilder()
                    .addCode(
                        "return this.%M<%T>().%L\n",
                        WINRT_AS_FUNCTION_NAME,
                        getterInterfaceClassName,
                        property.name.replaceFirstChar(Char::lowercase),
                    )
                    .build(),
            )
        }
        if (!property.isReadOnly) {
            val setterCallPlan = buildJvmInterfaceAbiCallPlan(
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                parameterBindings = listOf(KotlinProjectionAbiParameterBinding("value", baseRenderer.renderAbiTypeBinding(propertyTypeName, typesByQualifiedName, slotInterfaceType.namespace))),
                suppressHResultCheck = property.isNoException,
                typesByQualifiedName = typesByQualifiedName,
            ) ?: error("Generator interface proxy parity failed to plan setter ${slotInterfaceType.qualifiedName}.${property.name}")
            builder.setter(
                FunSpec.setterBuilder()
                    .addParameter("value", baseRenderer.resolveTypeName(propertyTypeName))
                    .addCode(
                        "%L\n",
                        baseRenderer.renderInlineAbiInvocation(
                            invokeTargetExpression = "nativeObject",
                            slotExpression = CodeBlock.of("%T.Metadata.%L", baseRenderer.resolveTypeName(slotInterfaceType.qualifiedName), "${property.name.uppercase()}_SETTER_SLOT"),
                            callPlan = setterCallPlan,
                        ),
                    )
                    .build(),
            )
        }
        return builder.build()
    }

    private fun renderSourceSetFile(
        sourceSetPrefix: String,
        plan: KotlinTypeProjectionPlan,
        type: TypeSpec,
    ): KotlinProjectionFile {
        val contents = FileSpec.builder(plan.packageName, plan.type.name)
            .addGeneratedProjectionSuppressions()
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

    private fun jvmInterfaceProjectionSupportClassName(
        plan: KotlinTypeProjectionPlan,
        interfaceType: io.github.composefluent.winrt.metadata.WinRTTypeDefinition = plan.type,
    ): ClassName =
        (baseRenderer.resolveTypeName(interfaceType.qualifiedName) as? ClassName)
            ?.let { ClassName(it.packageName, "${interfaceType.name}JvmProjection") }
            ?: ClassName(plan.packageName, "${interfaceType.name}JvmProjection")

    private fun jvmInterfaceProjectionSupportClassName(
        interfaceType: io.github.composefluent.winrt.metadata.WinRTTypeDefinition,
    ): ClassName =
        (baseRenderer.resolveTypeName(interfaceType.qualifiedName) as? ClassName)
            ?.let { ClassName(it.packageName, "${interfaceType.name}JvmProjection") }
            ?: projectionClassNameForQualifiedName(interfaceType.qualifiedName)
                .let { ClassName(it.packageName, "${interfaceType.name}JvmProjection") }

    private fun buildJvmInterfaceAbiCallPlan(
        returnBinding: KotlinProjectionAbiTypeBinding,
        parameterBindings: List<KotlinProjectionAbiParameterBinding>,
        suppressHResultCheck: Boolean,
        typesByQualifiedName: Map<String, io.github.composefluent.winrt.metadata.WinRTTypeDefinition>,
    ): KotlinProjectionAbiCallPlan? {
        val callPlan = baseRenderer.buildAbiCallPlan(
            returnBinding = returnBinding,
            parameterBindings = parameterBindings,
            suppressHResultCheck = suppressHResultCheck,
        ) ?: return null
        return callPlan.withJvmProjectedInterfaceOutputCodec(returnBinding, typesByQualifiedName)
    }

    private fun KotlinProjectionAbiCallPlan.withJvmProjectedInterfaceOutputCodec(
        returnBinding: KotlinProjectionAbiTypeBinding,
        typesByQualifiedName: Map<String, io.github.composefluent.winrt.metadata.WinRTTypeDefinition>,
    ): KotlinProjectionAbiCallPlan? {
        if (returnBinding.kind != KotlinProjectionAbiValueKind.ProjectedInterface) {
            return this
        }
        val returnInterface = projectedInterfaceType(returnBinding, typesByQualifiedName) ?: return null
        val recipePlan = returnRecipePlan ?: return null
        val outputCodec = recipePlan.outputCodecs[recipePlan.recipe]
            ?: baseRenderer.buildProjectionOutputCodec(returnBinding, recipePlan.recipe)
        val codecBody = CodeBlock.builder()
            .apply {
                if (returnBinding.isNullableAbiTypeName) {
                    add("if (%T.isNull(__abi)) return null\n", PLATFORM_ABI_CLASS_NAME)
                } else {
                    add("if (%T.isNull(__abi)) error(%S)\n", PLATFORM_ABI_CLASS_NAME, "WINRT_E_NULL_ABI_RETURN")
                }
            }
            .add("val __resultRef = %T(%T.toRawComPtr(__abi))\n", IUNKNOWN_REFERENCE_CLASS_NAME, PLATFORM_ABI_CLASS_NAME)
            .add("return %T.wrap(__resultRef)\n", jvmInterfaceProjectionSupportClassName(returnInterface))
            .build()
        return copy(
            returnRecipePlan = recipePlan.copy(
                outputCodecs = recipePlan.outputCodecs +
                    (recipePlan.recipe to outputCodec.copy(body = codecBody)),
            ),
        )
    }

    private fun projectedInterfaceType(
        binding: KotlinProjectionAbiTypeBinding,
        typesByQualifiedName: Map<String, io.github.composefluent.winrt.metadata.WinRTTypeDefinition>,
    ): io.github.composefluent.winrt.metadata.WinRTTypeDefinition? {
        if (binding.kind != KotlinProjectionAbiValueKind.ProjectedInterface || binding.typeArguments.isNotEmpty()) {
            return null
        }
        return typesByQualifiedName[binding.resolvedTypeName.rawWinRTTypeName()]
            ?: typesByQualifiedName[binding.typeName.rawWinRTTypeName()]
    }
}
