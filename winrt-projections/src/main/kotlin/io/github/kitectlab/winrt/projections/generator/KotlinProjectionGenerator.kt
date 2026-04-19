package io.github.kitectlab.winrt.projections.generator

import io.github.kitectlab.winrt.metadata.WinRtMetadataModel
import io.github.kitectlab.winrt.metadata.WinRtEventDefinition
import io.github.kitectlab.winrt.metadata.WinRtMethodDefinition
import io.github.kitectlab.winrt.metadata.WinRtNamespace
import io.github.kitectlab.winrt.metadata.WinRtPropertyDefinition
import io.github.kitectlab.winrt.metadata.WinRtTypeDefinition
import io.github.kitectlab.winrt.metadata.WinRtTypeKind
import io.github.kitectlab.winrt.runtime.ActivationFactory
import io.github.kitectlab.winrt.runtime.Guid
import io.github.kitectlab.winrt.runtime.IInspectableReference
import io.github.kitectlab.winrt.runtime.IUnknownReference
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
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asClassName
import java.net.URI
import java.time.Duration
import java.time.OffsetDateTime
import java.util.concurrent.CompletableFuture

private val ROOT_PACKAGE_SEGMENTS = listOf("io", "github", "kitectlab", "winrt", "projections")
private val GUID_CLASS_NAME = Guid::class.asClassName()
private val ACTIVATION_FACTORY_CLASS_NAME = ActivationFactory::class.asClassName()
private val IUNKNOWN_REFERENCE_CLASS_NAME = IUnknownReference::class.asClassName()
private val IINSPECTABLE_REFERENCE_CLASS_NAME = IInspectableReference::class.asClassName()
private val ATTRIBUTE_CLASS_NAME = Annotation::class.asClassName()
private val COMPLETABLE_FUTURE_CLASS_NAME = CompletableFuture::class.asClassName()
private val URI_CLASS_NAME = URI::class.asClassName()
private val OFFSET_DATE_TIME_CLASS_NAME = OffsetDateTime::class.asClassName()
private val DURATION_CLASS_NAME = Duration::class.asClassName()
private val AUTO_CLOSEABLE_CLASS_NAME = AutoCloseable::class.asClassName()
private val MUTABLE_LIST_CLASS_NAME = ClassName("kotlin.collections", "MutableList")
private val MUTABLE_MAP_CLASS_NAME = ClassName("kotlin.collections", "MutableMap")

private typealias SpecialTypeResolver = (List<TypeName>) -> TypeName

private val SPECIAL_TYPE_MAPPINGS: Map<String, SpecialTypeResolver> = mapOf(
    "System.Object" to { ANY },
    "WinRT.Interop.HWND" to { Long::class.asClassName() },
    "Windows.Foundation.DateTime" to { OFFSET_DATE_TIME_CLASS_NAME },
    "Windows.Foundation.TimeSpan" to { DURATION_CLASS_NAME },
    "Windows.Foundation.Uri" to { URI_CLASS_NAME },
    "Windows.Foundation.IClosable" to { AUTO_CLOSEABLE_CLASS_NAME },
    "Windows.Foundation.IReference" to { arguments -> arguments.single().copy(nullable = true) },
    "Windows.Foundation.Collections.IIterable" to { arguments -> Iterable::class.asClassName().parameterizedBy(arguments) },
    "Windows.Foundation.Collections.IIterator" to { arguments -> Iterator::class.asClassName().parameterizedBy(arguments) },
    "Windows.Foundation.Collections.IVectorView" to { arguments -> List::class.asClassName().parameterizedBy(arguments) },
    "Windows.Foundation.Collections.IVector" to { arguments -> MUTABLE_LIST_CLASS_NAME.parameterizedBy(arguments) },
    "Windows.Foundation.Collections.IMapView" to { arguments -> Map::class.asClassName().parameterizedBy(arguments) },
    "Windows.Foundation.Collections.IMap" to { arguments -> MUTABLE_MAP_CLASS_NAME.parameterizedBy(arguments) },
    "Windows.Foundation.Collections.IKeyValuePair" to { arguments -> Map.Entry::class.asClassName().parameterizedBy(arguments) },
    "Windows.Foundation.IAsyncAction" to { COMPLETABLE_FUTURE_CLASS_NAME.parameterizedBy(UNIT) },
    "Windows.Foundation.IAsyncActionWithProgress" to { COMPLETABLE_FUTURE_CLASS_NAME.parameterizedBy(UNIT) },
    "Windows.Foundation.IAsyncOperation" to { arguments -> COMPLETABLE_FUTURE_CLASS_NAME.parameterizedBy(arguments.single()) },
    "Windows.Foundation.IAsyncOperationWithProgress" to { arguments -> COMPLETABLE_FUTURE_CLASS_NAME.parameterizedBy(arguments.first()) },
    "Microsoft.UI.Xaml.Interop.IBindableIterable" to { Iterable::class.asClassName().parameterizedBy(ANY.copy(nullable = true)) },
    "Microsoft.UI.Xaml.Interop.IBindableVector" to { MUTABLE_LIST_CLASS_NAME.parameterizedBy(ANY.copy(nullable = true)) },
)

enum class KotlinProjectionDeclarationKind {
    Interface,
    Class,
    Enum,
    Struct,
    Delegate,
}

enum class KotlinProjectionCompanionKind {
    Metadata,
    ActivationFactory,
    StaticInterfaces,
    ComposableFactory,
}

enum class KotlinProjectionVisibility {
    Public,
    Internal,
}

enum class KotlinProjectionSpecializationKind {
    None,
    AttributeClass,
    ApiContract,
    ExclusiveInterface,
    ProjectionInternal,
    StaticClass,
}

enum class KotlinProjectionModifier {
    Sealed,
    Static,
}

data class KotlinTypeProjectionPlan(
    val type: WinRtTypeDefinition,
    val packageName: String,
    val relativePath: String,
    val declarationKind: KotlinProjectionDeclarationKind,
    val visibility: KotlinProjectionVisibility = KotlinProjectionVisibility.Public,
    val modifiers: List<KotlinProjectionModifier> = emptyList(),
    val specializationKinds: List<KotlinProjectionSpecializationKind> = emptyList(),
    val interfaceIid: Guid? = null,
    val defaultInterfaceName: String? = null,
    val defaultInterfaceIid: Guid? = null,
    val staticInterfaceNames: List<String> = emptyList(),
    val staticInterfaceBindings: List<KotlinProjectionInterfaceBinding> = emptyList(),
    val activatableFactoryInterfaceName: String? = null,
    val activatableFactoryInterfaceIid: Guid? = null,
    val composableFactoryInterfaceName: String? = null,
    val composableFactoryInterfaceIid: Guid? = null,
    val companionKinds: List<KotlinProjectionCompanionKind> = emptyList(),
)

data class KotlinProjectionInterfaceBinding(
    val qualifiedName: String,
    val iid: Guid? = null,
)

data class KotlinProjectionFile(
    val relativePath: String,
    val packageName: String,
    val contents: String,
)

class KotlinProjectionContractValidator {
    fun validate(model: WinRtMetadataModel): WinRtMetadataModel =
        model.normalized().also { normalized ->
            normalized.namespaces
                .flatMap(WinRtNamespace::types)
                .forEach(::validateType)
        }

    fun validateType(type: WinRtTypeDefinition) {
        when (type.kind) {
            WinRtTypeKind.Interface -> validateInterface(type)
            WinRtTypeKind.RuntimeClass -> validateRuntimeClass(type)
            else -> Unit
        }
    }

    private fun validateInterface(type: WinRtTypeDefinition) {
        val hasSurface = type.methods.isNotEmpty() || type.properties.isNotEmpty() || type.events.isNotEmpty()
        require(!hasSurface || type.iid != null) {
            "Generator 3.1 requires interface ${type.qualifiedName} to carry metadata IID before projection planning."
        }
    }

    private fun validateRuntimeClass(type: WinRtTypeDefinition) {
        val hasStaticSurface = type.methods.any(WinRtMethodDefinition::isStatic) ||
            type.properties.any { it.isStatic } ||
            type.events.any { it.isStatic }
        require(!hasStaticSurface || type.activation.staticInterfaceNames.isNotEmpty()) {
            "Generator 3.1 requires runtime class ${type.qualifiedName} to carry static interface metadata before projection planning."
        }
    }
}

class KotlinProjectionPlanner(
    private val validator: KotlinProjectionContractValidator = KotlinProjectionContractValidator(),
) {
    fun plan(model: WinRtMetadataModel): List<KotlinTypeProjectionPlan> =
        validator.validate(model).let { normalized ->
            val interfaceIidsByName = normalized.namespaces
                .flatMap(WinRtNamespace::types)
                .associate { it.qualifiedName to it.iid }
            normalized.namespaces.flatMap { planNamespace(it, interfaceIidsByName) }
        }

    fun planNamespace(
        namespace: WinRtNamespace,
        interfaceIidsByName: Map<String, Guid?> = emptyMap(),
    ): List<KotlinTypeProjectionPlan> =
        namespace.normalized().types.mapNotNull { type ->
            validator.validateType(type)
            planType(type, interfaceIidsByName)
        }

    private fun planType(
        type: WinRtTypeDefinition,
        interfaceIidsByName: Map<String, Guid?>,
    ): KotlinTypeProjectionPlan? {
        val declarationKind = when (type.kind) {
            WinRtTypeKind.Interface -> KotlinProjectionDeclarationKind.Interface
            WinRtTypeKind.RuntimeClass -> KotlinProjectionDeclarationKind.Class
            WinRtTypeKind.Enum -> KotlinProjectionDeclarationKind.Enum
            WinRtTypeKind.Struct -> if (type.isApiContract) {
                KotlinProjectionDeclarationKind.Enum
            } else {
                KotlinProjectionDeclarationKind.Struct
            }
            WinRtTypeKind.Delegate -> KotlinProjectionDeclarationKind.Delegate
            WinRtTypeKind.Unknown -> return null
        }
        val packageName = (ROOT_PACKAGE_SEGMENTS + namespaceSegments(type.namespace)).joinToString(".")
        val relativePath = packageName.replace('.', '/') + "/${type.name}.kt"
        return KotlinTypeProjectionPlan(
            type = type,
            packageName = packageName,
            relativePath = relativePath,
            declarationKind = declarationKind,
            visibility = planVisibility(type),
            modifiers = planModifiers(type),
            specializationKinds = planSpecializations(type),
            interfaceIid = type.iid,
            defaultInterfaceName = type.defaultInterfaceName,
            defaultInterfaceIid = type.defaultInterfaceName?.let(interfaceIidsByName::get),
            staticInterfaceNames = type.activation.staticInterfaceNames,
            staticInterfaceBindings = type.activation.staticInterfaceNames.map { interfaceName ->
                KotlinProjectionInterfaceBinding(
                    qualifiedName = interfaceName,
                    iid = interfaceIidsByName[interfaceName],
                )
            },
            activatableFactoryInterfaceName = type.activation.activatableFactoryInterfaceName,
            activatableFactoryInterfaceIid = type.activation.activatableFactoryInterfaceName?.let(interfaceIidsByName::get),
            composableFactoryInterfaceName = type.activation.composableFactoryInterfaceName,
            composableFactoryInterfaceIid = type.activation.composableFactoryInterfaceName?.let(interfaceIidsByName::get),
            companionKinds = planCompanions(type),
        )
    }

    private fun planCompanions(type: WinRtTypeDefinition): List<KotlinProjectionCompanionKind> = buildList {
        if (shouldEmitMetadataCompanion(type)) {
            add(KotlinProjectionCompanionKind.Metadata)
        }
        if (type.kind == WinRtTypeKind.RuntimeClass && type.activation.isActivatable) {
            add(KotlinProjectionCompanionKind.ActivationFactory)
        }
        if (type.kind == WinRtTypeKind.RuntimeClass && type.activation.staticInterfaceNames.isNotEmpty()) {
            add(KotlinProjectionCompanionKind.StaticInterfaces)
        }
        if (type.kind == WinRtTypeKind.RuntimeClass && type.activation.composableFactoryInterfaceName != null) {
            add(KotlinProjectionCompanionKind.ComposableFactory)
        }
    }

    private fun shouldEmitMetadataCompanion(type: WinRtTypeDefinition): Boolean = when (type.kind) {
        WinRtTypeKind.Interface -> !type.isExclusiveTo
        WinRtTypeKind.RuntimeClass -> !type.isStaticType && !type.isAttributeType
        else -> false
    }

    private fun planVisibility(type: WinRtTypeDefinition): KotlinProjectionVisibility =
        if (type.isProjectionInternal || (type.kind == WinRtTypeKind.Interface && type.isExclusiveTo)) {
            KotlinProjectionVisibility.Internal
        } else {
            KotlinProjectionVisibility.Public
        }

    private fun planModifiers(type: WinRtTypeDefinition): List<KotlinProjectionModifier> = buildList {
        if (type.isStaticType) {
            add(KotlinProjectionModifier.Static)
        }
        if (type.isAttributeType || (type.kind == WinRtTypeKind.RuntimeClass && type.isSealedType && !type.isStaticType)) {
            add(KotlinProjectionModifier.Sealed)
        }
    }

    private fun planSpecializations(type: WinRtTypeDefinition): List<KotlinProjectionSpecializationKind> = buildList {
        if (type.isAttributeType) {
            add(KotlinProjectionSpecializationKind.AttributeClass)
        }
        if (type.isApiContract) {
            add(KotlinProjectionSpecializationKind.ApiContract)
        }
        if (type.isExclusiveTo) {
            add(KotlinProjectionSpecializationKind.ExclusiveInterface)
        }
        if (type.isProjectionInternal) {
            add(KotlinProjectionSpecializationKind.ProjectionInternal)
        }
        if (type.isStaticType) {
            add(KotlinProjectionSpecializationKind.StaticClass)
        }
        if (isEmpty()) {
            add(KotlinProjectionSpecializationKind.None)
        }
    }

    private fun namespaceSegments(namespace: String): List<String> =
        namespace.split('.')
            .filter { it.isNotBlank() }
            .map { it.lowercase() }
}

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
            renderEventFunctions(event, abstract = true).forEach(builder::addFunction)
        }
        appendCompanionShells(builder, plan)
        return builder.build()
    }

    private fun renderClassShell(plan: KotlinTypeProjectionPlan): TypeSpec = when {
        KotlinProjectionSpecializationKind.AttributeClass in plan.specializationKinds -> renderAttributeClassShell(plan)
        KotlinProjectionSpecializationKind.StaticClass in plan.specializationKinds -> renderStaticClassShell(plan)
        else -> renderRuntimeClassShell(plan)
    }

    private fun renderRuntimeClassShell(plan: KotlinTypeProjectionPlan): TypeSpec {
        val builder = TypeSpec.classBuilder(plan.type.name)
        applyCommonTypeShape(builder, plan)
        plan.defaultInterfaceName?.let { defaultInterfaceName ->
            builder.addSuperinterface(resolveTypeName(defaultInterfaceName))
        }
        plan.type.implementedInterfaces
            .filterNot { it.isDefault }
            .forEach { implemented -> builder.addSuperinterface(resolveTypeName(implemented.interfaceName)) }
        plan.type.methods.filterNot { it.isStatic }.forEach { builder.addFunction(renderStubMethod(it)) }
        plan.type.properties.filterNot { it.isStatic }.forEach { builder.addProperty(renderStubProperty(it)) }
        plan.type.events.filterNot { it.isStatic }.forEach { event ->
            renderEventFunctions(event, abstract = false).forEach(builder::addFunction)
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
            }
            .build()

    private fun renderStruct(plan: KotlinTypeProjectionPlan): TypeSpec =
        TypeSpec.classBuilder(plan.type.name)
            .addModifiers(KModifier.DATA)
            .primaryConstructor(FunSpec.constructorBuilder().build())
            .apply { applyCommonTypeShape(this, plan, addModifiers = false) }
            .build()

    private fun renderDelegate(plan: KotlinTypeProjectionPlan): TypeSpec {
        val builder = TypeSpec.funInterfaceBuilder(plan.type.name)
        applyCommonTypeShape(builder, plan)
        builder.addFunction(
            FunSpec.builder("invoke")
                .addModifiers(KModifier.ABSTRACT, KModifier.OPERATOR)
                .returns(UNIT)
                .build(),
        )
        return builder.build()
    }

    private fun applyCommonTypeShape(
        builder: TypeSpec.Builder,
        plan: KotlinTypeProjectionPlan,
        addModifiers: Boolean = true,
    ) {
        builder.addModifiers(renderVisibility(plan.visibility))
        if (addModifiers) {
            plan.modifiers.forEach { modifier ->
                when (modifier) {
                    KotlinProjectionModifier.Sealed -> builder.addModifiers(KModifier.SEALED)
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

    private fun renderStubMethod(method: WinRtMethodDefinition): FunSpec =
        FunSpec.builder(method.name)
            .addParameters(method.parameters.map { ParameterSpec.builder(it.name, resolveTypeName(it.typeName)).build() })
            .returns(resolveTypeName(method.returnTypeName))
            .addCode("return error(%S)\n", "Not yet bound to winrt-runtime")
            .build()

    private fun renderInterfaceProperty(property: WinRtPropertyDefinition): PropertySpec =
        PropertySpec.builder(property.name.replaceFirstChar(Char::lowercase), resolveTypeName(property.typeName))
            .mutable(!property.isReadOnly)
            .addModifiers(KModifier.ABSTRACT)
            .build()

    private fun renderStubProperty(property: WinRtPropertyDefinition): PropertySpec {
        val builder = PropertySpec.builder(
            property.name.replaceFirstChar(Char::lowercase),
            resolveTypeName(property.typeName),
        ).mutable(!property.isReadOnly)
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

    private fun renderEventFunctions(event: WinRtEventDefinition, abstract: Boolean): List<FunSpec> {
        val typeName = resolveTypeName(event.delegateTypeName)
        return listOf(
            FunSpec.builder("add${event.name}")
                .addParameter("handler", typeName)
                .apply {
                    if (abstract) {
                        addModifiers(KModifier.ABSTRACT)
                    } else {
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
                staticMethods.forEach { addFunction(renderStubMethod(it)) }
                staticProperties.forEach { addProperty(renderStubProperty(it)) }
                staticEvents.forEach { event ->
                    renderEventFunctions(event, abstract = false).forEach(::addFunction)
                }
            }
            .build()

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
                        addProperty(
                            PropertySpec.builder(binding.qualifiedName.substringAfterLast('.').uppercase(), String::class)
                                .addModifiers(KModifier.CONST)
                                .initializer("%S", binding.qualifiedName)
                                .build(),
                        )
                        binding.iid?.let { iid ->
                            addProperty(
                                PropertySpec.builder("${binding.qualifiedName.substringAfterLast('.').uppercase()}_IID", GUID_CLASS_NAME)
                                    .initializer("%T(%S)", GUID_CLASS_NAME, iid.toString())
                                .build(),
                            )
                        }
                    }
                    plan.staticInterfaceBindings
                        .filter { it.iid != null }
                        .forEach { binding ->
                            addFunction(
                                FunSpec.builder(binding.qualifiedName.substringAfterLast('.').replaceFirstChar(Char::lowercase))
                                    .returns(IUNKNOWN_REFERENCE_CLASS_NAME)
                                    .addCode(
                                        CodeBlock.of(
                                            "return %T.get(Metadata.TYPE_NAME, %L_IID)\n",
                                            ACTIVATION_FACTORY_CLASS_NAME,
                                            binding.qualifiedName.substringAfterLast('.').uppercase(),
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

    private fun appendMetadataCompanionMembers(
        builder: TypeSpec.Builder,
        plan: KotlinTypeProjectionPlan,
    ) {
        builder.addProperty(
            PropertySpec.builder("TYPE_NAME", String::class)
                .addModifiers(KModifier.CONST)
                .initializer("%S", plan.type.qualifiedName)
                .build(),
        )
        plan.interfaceIid?.let { iid ->
            builder.addProperty(
                PropertySpec.builder("IID", GUID_CLASS_NAME)
                    .initializer("%T(%S)", GUID_CLASS_NAME, iid.toString())
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
        }
    }

    private fun resolveTypeName(typeName: String): TypeName {
        val trimmed = typeName.trim()
        val genericStart = trimmed.indexOf('<')
        if (genericStart >= 0 && trimmed.endsWith('>')) {
            val rawType = trimmed.substring(0, genericStart)
            val arguments = splitGenericArguments(trimmed.substring(genericStart + 1, trimmed.length - 1))
                .map(::resolveTypeName)
            SPECIAL_TYPE_MAPPINGS[rawType]?.let { resolver ->
                return resolver(arguments)
            }
            val rawClassName = if ('.' in rawType) projectionClassName(rawType) else ClassName.bestGuess(rawType)
            return rawClassName.parameterizedBy(arguments)
        }

        SPECIAL_TYPE_MAPPINGS[trimmed]?.let { resolver ->
            return resolver(emptyList())
        }

        return when (trimmed) {
            "Unit" -> UNIT
            "String" -> String::class.asClassName()
            "Int" -> Int::class.asClassName()
            "UInt" -> UInt::class.asClassName()
            "Boolean" -> Boolean::class.asClassName()
            "Byte" -> Byte::class.asClassName()
            "Short" -> Short::class.asClassName()
            "UShort" -> UShort::class.asClassName()
            "Long" -> Long::class.asClassName()
            "Float" -> Float::class.asClassName()
            "Double" -> Double::class.asClassName()
            "Char" -> Char::class.asClassName()
            else -> if ('.' in trimmed) projectionClassName(trimmed) else ClassName.bestGuess(trimmed)
        }
    }

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

class KotlinProjectionGenerator(
    private val planner: KotlinProjectionPlanner = KotlinProjectionPlanner(),
    private val renderer: KotlinProjectionRenderer = KotlinProjectionRenderer(),
) {
    fun generate(model: WinRtMetadataModel): List<KotlinProjectionFile> =
        planner.plan(model).map(renderer::render)
}
