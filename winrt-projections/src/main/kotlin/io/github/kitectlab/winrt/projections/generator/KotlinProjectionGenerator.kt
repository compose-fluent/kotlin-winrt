package io.github.kitectlab.winrt.projections.generator

import io.github.kitectlab.winrt.metadata.WinRtMetadataModel
import io.github.kitectlab.winrt.metadata.WinRtEventDefinition
import io.github.kitectlab.winrt.metadata.WinRtMethodDefinition
import io.github.kitectlab.winrt.metadata.WinRtNamespace
import io.github.kitectlab.winrt.metadata.WinRtPropertyDefinition
import io.github.kitectlab.winrt.metadata.WinRtTypeDefinition
import io.github.kitectlab.winrt.metadata.WinRtTypeKind
import io.github.kitectlab.winrt.runtime.ActivationFactory
import io.github.kitectlab.winrt.runtime.ComObjectReference
import io.github.kitectlab.winrt.runtime.Guid
import io.github.kitectlab.winrt.runtime.HResult
import io.github.kitectlab.winrt.runtime.HString
import io.github.kitectlab.winrt.runtime.IInspectableReference
import io.github.kitectlab.winrt.runtime.IUnknownReference
import io.github.kitectlab.winrt.runtime.IWinRTObject
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
import kotlin.LazyThreadSafetyMode
import java.util.concurrent.CompletableFuture

private val ROOT_PACKAGE_SEGMENTS = listOf("io", "github", "kitectlab", "winrt", "projections")
private val GUID_CLASS_NAME = Guid::class.asClassName()
private val ACTIVATION_FACTORY_CLASS_NAME = ActivationFactory::class.asClassName()
private val COM_OBJECT_REFERENCE_CLASS_NAME = ComObjectReference::class.asClassName()
private val HRESULT_CLASS_NAME = HResult::class.asClassName()
private val HSTRING_CLASS_NAME = HString::class.asClassName()
private val IUNKNOWN_REFERENCE_CLASS_NAME = IUnknownReference::class.asClassName()
private val IINSPECTABLE_REFERENCE_CLASS_NAME = IInspectableReference::class.asClassName()
private val IWINRT_OBJECT_CLASS_NAME = IWinRTObject::class.asClassName()
private val ATTRIBUTE_CLASS_NAME = Annotation::class.asClassName()
private val COMPLETABLE_FUTURE_CLASS_NAME = CompletableFuture::class.asClassName()
private val URI_CLASS_NAME = URI::class.asClassName()
private val OFFSET_DATE_TIME_CLASS_NAME = OffsetDateTime::class.asClassName()
private val DURATION_CLASS_NAME = Duration::class.asClassName()
private val AUTO_CLOSEABLE_CLASS_NAME = AutoCloseable::class.asClassName()
private val LAZY_THREAD_SAFETY_MODE_CLASS_NAME = LazyThreadSafetyMode::class.asClassName()
private val MUTABLE_LIST_CLASS_NAME = ClassName("kotlin.collections", "MutableList")
private val MUTABLE_MAP_CLASS_NAME = ClassName("kotlin.collections", "MutableMap")
private val ARENA_CLASS_NAME = ClassName("java.lang.foreign", "Arena")
private val FUNCTION_DESCRIPTOR_CLASS_NAME = ClassName("java.lang.foreign", "FunctionDescriptor")
private val VALUE_LAYOUT_CLASS_NAME = ClassName("java.lang.foreign", "ValueLayout")

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
    val implementedInterfaceBindings: List<KotlinProjectionInterfaceBinding> = emptyList(),
    val activatableFactoryInterfaceName: String? = null,
    val activatableFactoryInterfaceIid: Guid? = null,
    val composableFactoryInterfaceName: String? = null,
    val composableFactoryInterfaceIid: Guid? = null,
    val abiSlotBindings: List<KotlinProjectionAbiSlotBinding> = emptyList(),
    val instanceMemberBindings: List<KotlinProjectionInstanceMemberBinding> = emptyList(),
    val staticMemberBindings: List<KotlinProjectionStaticMemberBinding> = emptyList(),
    val companionKinds: List<KotlinProjectionCompanionKind> = emptyList(),
)

data class KotlinProjectionInterfaceBinding(
    val qualifiedName: String,
    val iid: Guid? = null,
)

data class KotlinProjectionAbiSlotBinding(
    val constantName: String,
    val slot: Int,
)

data class KotlinProjectionInstanceMemberBinding(
    val bindingName: String,
    val ownerInterfaceQualifiedName: String,
    val ownerCachePropertyName: String,
    val slotInterfaceQualifiedName: String,
    val slotConstantName: String,
    val returnBinding: KotlinProjectionAbiTypeBinding,
    val parameterBindings: List<KotlinProjectionAbiParameterBinding> = emptyList(),
)

data class KotlinProjectionStaticMemberBinding(
    val bindingName: String,
    val ownerInterfaceQualifiedName: String,
    val ownerAccessorName: String,
    val ownerCachePropertyName: String,
    val slotInterfaceQualifiedName: String,
    val slotConstantName: String,
    val returnBinding: KotlinProjectionAbiTypeBinding,
    val parameterBindings: List<KotlinProjectionAbiParameterBinding> = emptyList(),
)

enum class KotlinProjectionAbiValueKind {
    Unit,
    String,
    Boolean,
    Int32,
    UInt32,
    Double,
    ProjectedObject,
    UnknownReference,
    InspectableReference,
    Unsupported,
}

data class KotlinProjectionAbiTypeBinding(
    val kind: KotlinProjectionAbiValueKind,
    val typeName: String,
)

data class KotlinProjectionAbiParameterBinding(
    val name: String,
    val typeBinding: KotlinProjectionAbiTypeBinding,
)

private data class KotlinProjectionAbiMarshalerPlan(
    val name: String,
    val typeBinding: KotlinProjectionAbiTypeBinding,
    val isReturn: Boolean,
    val abiLayout: CodeBlock,
    val invokeDescriptorLayout: CodeBlock,
    val abiArgumentExpression: CodeBlock,
    val scopeOpener: CodeBlock? = null,
    val readbackStatement: CodeBlock? = null,
)

private data class KotlinProjectionAbiCallPlan(
    val parameterMarshalers: List<KotlinProjectionAbiMarshalerPlan>,
    val returnMarshaler: KotlinProjectionAbiMarshalerPlan? = null,
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
            val typesByQualifiedName = normalized.namespaces
                .flatMap(WinRtNamespace::types)
                .associateBy(WinRtTypeDefinition::qualifiedName)
            val interfaceIidsByName = normalized.namespaces
                .flatMap(WinRtNamespace::types)
                .associate { it.qualifiedName to it.iid }
            normalized.namespaces.flatMap { planNamespace(it, interfaceIidsByName, typesByQualifiedName) }
        }

    fun planNamespace(
        namespace: WinRtNamespace,
        interfaceIidsByName: Map<String, Guid?> = emptyMap(),
        typesByQualifiedName: Map<String, WinRtTypeDefinition> = emptyMap(),
    ): List<KotlinTypeProjectionPlan> =
        namespace.normalized().types.mapNotNull { type ->
            validator.validateType(type)
            planType(type, interfaceIidsByName, typesByQualifiedName)
        }

    private fun planType(
        type: WinRtTypeDefinition,
        interfaceIidsByName: Map<String, Guid?>,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
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
            implementedInterfaceBindings = type.implementedInterfaces
                .filterNot { it.isDefault }
                .map { implemented ->
                    KotlinProjectionInterfaceBinding(
                        qualifiedName = implemented.interfaceName,
                        iid = interfaceIidsByName[implemented.interfaceName],
                    )
                },
            activatableFactoryInterfaceName = type.activation.activatableFactoryInterfaceName,
            activatableFactoryInterfaceIid = type.activation.activatableFactoryInterfaceName?.let(interfaceIidsByName::get),
            composableFactoryInterfaceName = type.activation.composableFactoryInterfaceName,
            composableFactoryInterfaceIid = type.activation.composableFactoryInterfaceName?.let(interfaceIidsByName::get),
            abiSlotBindings = planAbiSlotBindings(type, typesByQualifiedName),
            instanceMemberBindings = planInstanceMemberBindings(type, typesByQualifiedName),
            staticMemberBindings = planStaticMemberBindings(type, typesByQualifiedName),
            companionKinds = planCompanions(type),
        )
    }

    private fun planAbiSlotBindings(
        type: WinRtTypeDefinition,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    ): List<KotlinProjectionAbiSlotBinding> {
        if (type.kind != WinRtTypeKind.Interface) {
            return emptyList()
        }
        val baseSlotCount = type.implementedInterfaces.sumOf { implemented ->
            interfaceAbiMemberCount(implemented.interfaceName, typesByQualifiedName, mutableSetOf())
        }
        val localBindings = type.localAbiMembers()
        return localBindings.mapIndexed { index, constantName ->
            KotlinProjectionAbiSlotBinding(
                constantName = constantName,
                slot = 6 + baseSlotCount + index,
            )
        }
    }

    private fun interfaceAbiMemberCount(
        interfaceName: String,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
        visiting: MutableSet<String>,
    ): Int {
        val type = typesByQualifiedName[interfaceName] ?: return 0
        if (type.kind != WinRtTypeKind.Interface || !visiting.add(interfaceName)) {
            return 0
        }
        return try {
            type.implementedInterfaces.sumOf { implemented ->
                interfaceAbiMemberCount(implemented.interfaceName, typesByQualifiedName, visiting)
            } + type.localAbiMembers().size
        } finally {
            visiting.remove(interfaceName)
        }
    }

    private fun planInstanceMemberBindings(
        type: WinRtTypeDefinition,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    ): List<KotlinProjectionInstanceMemberBinding> {
        if (type.kind != WinRtTypeKind.RuntimeClass) {
            return emptyList()
        }
        val candidateInterfaces = buildList {
            type.defaultInterfaceName?.let(::add)
            type.implementedInterfaces
                .filterNot { it.isDefault }
                .mapTo(this) { it.interfaceName }
        }.distinct()

        return buildList {
            type.methods.filterNot(WinRtMethodDefinition::isStatic).forEach { method ->
                resolveInstanceMemberBinding(
                    candidateInterfaces = candidateInterfaces,
                    typesByQualifiedName = typesByQualifiedName,
                    slotConstantName = "${method.name.uppercase()}_SLOT",
                    returnBinding = classifyAbiTypeBinding(method.returnTypeName),
                    parameterBindings = method.parameters.map { parameter ->
                        KotlinProjectionAbiParameterBinding(
                            name = parameter.name,
                            typeBinding = classifyAbiTypeBinding(parameter.typeName),
                        )
                    },
                    signatureMatcher = { interfaceType ->
                        interfaceType.methods.any { it.projectionSignatureKey() == method.projectionSignatureKey() }
                    },
                )?.let(::add)
            }
            type.properties.filterNot { it.isStatic }.forEach { property ->
                if (property.getterMethodName != null) {
                    resolveInstanceMemberBinding(
                        candidateInterfaces = candidateInterfaces,
                        typesByQualifiedName = typesByQualifiedName,
                        slotConstantName = "${property.name.uppercase()}_GETTER_SLOT",
                        returnBinding = classifyAbiTypeBinding(property.typeName),
                        parameterBindings = emptyList(),
                        signatureMatcher = { interfaceType ->
                            interfaceType.properties.any {
                                it.projectionSignatureKey() == property.projectionSignatureKey() && it.getterMethodName != null
                            }
                        },
                    )?.let(::add)
                }
                if (property.setterMethodName != null) {
                    resolveInstanceMemberBinding(
                        candidateInterfaces = candidateInterfaces,
                        typesByQualifiedName = typesByQualifiedName,
                        slotConstantName = "${property.name.uppercase()}_SETTER_SLOT",
                        returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                        parameterBindings = listOf(
                            KotlinProjectionAbiParameterBinding(
                                name = "value",
                                typeBinding = classifyAbiTypeBinding(property.typeName),
                            ),
                        ),
                        signatureMatcher = { interfaceType ->
                            interfaceType.properties.any {
                                it.projectionSignatureKey() == property.projectionSignatureKey() && it.setterMethodName != null
                            }
                        },
                    )?.let(::add)
                }
            }
            type.events.filterNot { it.isStatic }.forEach { event ->
                if (event.addMethodName != null || event.addMethodRowId != null) {
                    resolveInstanceMemberBinding(
                        candidateInterfaces = candidateInterfaces,
                        typesByQualifiedName = typesByQualifiedName,
                        slotConstantName = "${event.name.uppercase()}_ADD_SLOT",
                        returnBinding = classifyAbiTypeBinding("Int"),
                        parameterBindings = listOf(
                            KotlinProjectionAbiParameterBinding(
                                name = "handler",
                                typeBinding = classifyAbiTypeBinding(event.delegateTypeName),
                            ),
                        ),
                        signatureMatcher = { interfaceType ->
                            interfaceType.events.any {
                                it.projectionSignatureKey() == event.projectionSignatureKey() &&
                                    (it.addMethodName != null || it.addMethodRowId != null)
                            }
                        },
                    )?.let(::add)
                }
                if (event.removeMethodName != null || event.removeMethodRowId != null) {
                    resolveInstanceMemberBinding(
                        candidateInterfaces = candidateInterfaces,
                        typesByQualifiedName = typesByQualifiedName,
                        slotConstantName = "${event.name.uppercase()}_REMOVE_SLOT",
                        returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                        parameterBindings = listOf(
                            KotlinProjectionAbiParameterBinding(
                                name = "token",
                                typeBinding = classifyAbiTypeBinding("Int"),
                            ),
                        ),
                        signatureMatcher = { interfaceType ->
                            interfaceType.events.any {
                                it.projectionSignatureKey() == event.projectionSignatureKey() &&
                                    (it.removeMethodName != null || it.removeMethodRowId != null)
                            }
                        },
                    )?.let(::add)
                }
            }
        }.distinctBy(KotlinProjectionInstanceMemberBinding::bindingName)
    }

    private fun planStaticMemberBindings(
        type: WinRtTypeDefinition,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    ): List<KotlinProjectionStaticMemberBinding> {
        if (type.kind != WinRtTypeKind.RuntimeClass) {
            return emptyList()
        }
        val candidateInterfaces = type.activation.staticInterfaceNames.distinct()
        return buildList {
            type.methods.filter(WinRtMethodDefinition::isStatic).forEach { method ->
                resolveStaticMemberBinding(
                    candidateInterfaces = candidateInterfaces,
                    typesByQualifiedName = typesByQualifiedName,
                    bindingName = "STATIC_${method.name.uppercase()}_SLOT",
                    slotConstantName = "${method.name.uppercase()}_SLOT",
                    returnBinding = classifyAbiTypeBinding(method.returnTypeName),
                    parameterBindings = method.parameters.map { parameter ->
                        KotlinProjectionAbiParameterBinding(
                            name = parameter.name,
                            typeBinding = classifyAbiTypeBinding(parameter.typeName),
                        )
                    },
                    signatureMatcher = { interfaceType ->
                        interfaceType.methods.any { it.projectionSignatureIgnoringStaticKey() == method.projectionSignatureIgnoringStaticKey() }
                    },
                )?.let(::add)
            }
            type.properties.filter { it.isStatic }.forEach { property ->
                if (property.getterMethodName != null) {
                    resolveStaticMemberBinding(
                        candidateInterfaces = candidateInterfaces,
                        typesByQualifiedName = typesByQualifiedName,
                        bindingName = "STATIC_${property.name.uppercase()}_GETTER_SLOT",
                        slotConstantName = "${property.name.uppercase()}_GETTER_SLOT",
                        returnBinding = classifyAbiTypeBinding(property.typeName),
                        parameterBindings = emptyList(),
                        signatureMatcher = { interfaceType ->
                            interfaceType.properties.any {
                                it.projectionSignatureIgnoringStaticKey() == property.projectionSignatureIgnoringStaticKey() &&
                                    it.getterMethodName != null
                            }
                        },
                    )?.let(::add)
                }
                if (property.setterMethodName != null) {
                    resolveStaticMemberBinding(
                        candidateInterfaces = candidateInterfaces,
                        typesByQualifiedName = typesByQualifiedName,
                        bindingName = "STATIC_${property.name.uppercase()}_SETTER_SLOT",
                        slotConstantName = "${property.name.uppercase()}_SETTER_SLOT",
                        returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                        parameterBindings = listOf(
                            KotlinProjectionAbiParameterBinding(
                                name = "value",
                                typeBinding = classifyAbiTypeBinding(property.typeName),
                            ),
                        ),
                        signatureMatcher = { interfaceType ->
                            interfaceType.properties.any {
                                it.projectionSignatureIgnoringStaticKey() == property.projectionSignatureIgnoringStaticKey() &&
                                    it.setterMethodName != null
                            }
                        },
                    )?.let(::add)
                }
            }
        }.distinctBy(KotlinProjectionStaticMemberBinding::bindingName)
    }

    private fun resolveInstanceMemberBinding(
        candidateInterfaces: List<String>,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
        slotConstantName: String,
        returnBinding: KotlinProjectionAbiTypeBinding,
        parameterBindings: List<KotlinProjectionAbiParameterBinding>,
        signatureMatcher: (WinRtTypeDefinition) -> Boolean,
    ): KotlinProjectionInstanceMemberBinding? {
        candidateInterfaces.forEach { candidateInterface ->
            val slotInterfaceQualifiedName = findDeclaringInterface(
                interfaceName = candidateInterface,
                typesByQualifiedName = typesByQualifiedName,
                visiting = mutableSetOf(),
                signatureMatcher = signatureMatcher,
            ) ?: return@forEach
            return KotlinProjectionInstanceMemberBinding(
                bindingName = slotConstantName,
                ownerInterfaceQualifiedName = candidateInterface,
                ownerCachePropertyName = ownerCachePropertyName(candidateInterface, candidateInterfaces.firstOrNull()),
                slotInterfaceQualifiedName = slotInterfaceQualifiedName,
                slotConstantName = slotConstantName,
                returnBinding = returnBinding,
                parameterBindings = parameterBindings,
            )
        }
        return null
    }

    private fun resolveStaticMemberBinding(
        candidateInterfaces: List<String>,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
        bindingName: String,
        slotConstantName: String,
        returnBinding: KotlinProjectionAbiTypeBinding,
        parameterBindings: List<KotlinProjectionAbiParameterBinding>,
        signatureMatcher: (WinRtTypeDefinition) -> Boolean,
    ): KotlinProjectionStaticMemberBinding? {
        candidateInterfaces.forEach { candidateInterface ->
            val slotInterfaceQualifiedName = findDeclaringInterface(
                interfaceName = candidateInterface,
                typesByQualifiedName = typesByQualifiedName,
                visiting = mutableSetOf(),
                signatureMatcher = signatureMatcher,
            ) ?: return@forEach
            return KotlinProjectionStaticMemberBinding(
                bindingName = bindingName,
                ownerInterfaceQualifiedName = candidateInterface,
                ownerAccessorName = staticOwnerAccessorName(candidateInterface),
                ownerCachePropertyName = staticOwnerCachePropertyName(candidateInterface),
                slotInterfaceQualifiedName = slotInterfaceQualifiedName,
                slotConstantName = slotConstantName,
                returnBinding = returnBinding,
                parameterBindings = parameterBindings,
            )
        }
        return null
    }

    private fun classifyAbiTypeBinding(typeName: String): KotlinProjectionAbiTypeBinding {
        val trimmedTypeName = typeName.trim()
        val rawTypeName = trimmedTypeName.substringBefore('<').removeSuffix("?")
        val kind = when (trimmedTypeName) {
            "Unit" -> KotlinProjectionAbiValueKind.Unit
            "String" -> KotlinProjectionAbiValueKind.String
            "Boolean" -> KotlinProjectionAbiValueKind.Boolean
            "Int" -> KotlinProjectionAbiValueKind.Int32
            "UInt" -> KotlinProjectionAbiValueKind.UInt32
            "Double" -> KotlinProjectionAbiValueKind.Double
            IUNKNOWN_REFERENCE_CLASS_NAME.simpleName -> KotlinProjectionAbiValueKind.UnknownReference
            IINSPECTABLE_REFERENCE_CLASS_NAME.simpleName -> KotlinProjectionAbiValueKind.InspectableReference
            "io.github.kitectlab.winrt.runtime.IUnknownReference" -> KotlinProjectionAbiValueKind.UnknownReference
            "io.github.kitectlab.winrt.runtime.IInspectableReference" -> KotlinProjectionAbiValueKind.InspectableReference
            else -> if ('.' in rawTypeName && rawTypeName !in SPECIAL_TYPE_MAPPINGS) {
                KotlinProjectionAbiValueKind.ProjectedObject
            } else {
                KotlinProjectionAbiValueKind.Unsupported
            }
        }
        return KotlinProjectionAbiTypeBinding(kind = kind, typeName = trimmedTypeName)
    }

    private fun findDeclaringInterface(
        interfaceName: String,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
        visiting: MutableSet<String>,
        signatureMatcher: (WinRtTypeDefinition) -> Boolean,
    ): String? {
        val type = typesByQualifiedName[interfaceName] ?: return null
        if (type.kind != WinRtTypeKind.Interface || !visiting.add(interfaceName)) {
            return null
        }
        return try {
            if (signatureMatcher(type)) {
                interfaceName
            } else {
                type.implementedInterfaces.firstNotNullOfOrNull { implemented ->
                    findDeclaringInterface(implemented.interfaceName, typesByQualifiedName, visiting, signatureMatcher)
                }
            }
        } finally {
            visiting.remove(interfaceName)
        }
    }

    private fun ownerCachePropertyName(interfaceName: String, defaultInterfaceName: String?): String =
        if (interfaceName == defaultInterfaceName) {
            "_defaultInterface"
        } else {
            "_${interfaceName.substringAfterLast('.').replaceFirstChar(Char::lowercase)}"
        }

    private fun staticOwnerAccessorName(interfaceName: String): String =
        interfaceName.substringAfterLast('.').replaceFirstChar(Char::lowercase)

    private fun staticOwnerCachePropertyName(interfaceName: String): String =
        "_${staticOwnerAccessorName(interfaceName)}"

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

private data class AbiMemberOrder(
    val rowId: Int,
    val constantName: String,
)

private fun WinRtMethodDefinition.projectionSignatureKey(): String = buildString {
    append(if (isStatic) 'S' else 'I')
    append('|')
    append(name)
    append('|')
    append(returnTypeName)
    append('|')
    append(parameters.joinToString(",") { "${it.name}:${it.typeName}:${it.direction}" })
}

private fun WinRtMethodDefinition.projectionSignatureIgnoringStaticKey(): String = buildString {
    append(name)
    append('|')
    append(returnTypeName)
    append('|')
    append(parameters.joinToString(",") { "${it.name}:${it.typeName}:${it.direction}" })
}

private fun WinRtPropertyDefinition.projectionSignatureKey(): String = buildString {
    append(if (isStatic) 'S' else 'I')
    append('|')
    append(name)
    append('|')
    append(typeName)
}

private fun WinRtPropertyDefinition.projectionSignatureIgnoringStaticKey(): String = buildString {
    append(name)
    append('|')
    append(typeName)
}

private fun WinRtEventDefinition.projectionSignatureKey(): String = buildString {
    append(if (isStatic) 'S' else 'I')
    append('|')
    append(name)
    append('|')
    append(delegateTypeName)
}

private fun WinRtTypeDefinition.localAbiMembers(): List<String> =
    buildList<AbiMemberOrder> {
        methods.forEach { method ->
            method.methodRowId?.let { rowId ->
                add(AbiMemberOrder(rowId, "${method.name.uppercase()}_SLOT"))
            }
        }
        properties.forEach { property ->
            property.getterMethodRowId?.let { rowId ->
                add(AbiMemberOrder(rowId, "${property.name.uppercase()}_GETTER_SLOT"))
            }
            property.setterMethodRowId?.let { rowId ->
                add(AbiMemberOrder(rowId, "${property.name.uppercase()}_SETTER_SLOT"))
            }
        }
        events.forEach { event ->
            event.addMethodRowId?.let { rowId ->
                add(AbiMemberOrder(rowId, "${event.name.uppercase()}_ADD_SLOT"))
            }
            event.removeMethodRowId?.let { rowId ->
                add(AbiMemberOrder(rowId, "${event.name.uppercase()}_REMOVE_SLOT"))
            }
        }
    }
        .sortedBy(AbiMemberOrder::rowId)
        .map(AbiMemberOrder::constantName)

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
        applyCommonTypeShape(builder, plan, emitKotlinSealed = false)
        if (KotlinProjectionModifier.Sealed in plan.modifiers) {
            builder.addKdoc(
                "WinRT sealed runtime class shell emitted as a regular Kotlin class because Kotlin sealed constructors would block RCW wrapping and activation.\n",
            )
        }
        builder.primaryConstructor(
            FunSpec.constructorBuilder()
                .addModifiers(KModifier.INTERNAL)
                .addParameter("_inner", IINSPECTABLE_REFERENCE_CLASS_NAME)
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
            .forEach { implemented -> builder.addSuperinterface(resolveTypeName(implemented.interfaceName)) }
        builder.addSuperinterface(IWINRT_OBJECT_CLASS_NAME)
        if (KotlinProjectionCompanionKind.ActivationFactory in plan.companionKinds) {
            builder.addFunction(
                FunSpec.constructorBuilder()
                    .callThisConstructor("ActivationFactory.activate()")
                    .build(),
            )
        }
        plan.type.methods.filterNot { it.isStatic }.forEach { builder.addFunction(renderRuntimeMethod(plan, it)) }
        plan.type.properties.filterNot { it.isStatic }.forEach { builder.addProperty(renderRuntimeProperty(plan, it)) }
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
        emitKotlinSealed: Boolean = true,
    ) {
        builder.addModifiers(renderVisibility(plan.visibility))
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

    private fun renderStubMethod(method: WinRtMethodDefinition): FunSpec =
        FunSpec.builder(method.name)
            .addParameters(method.parameters.map { ParameterSpec.builder(it.name, resolveTypeName(it.typeName)).build() })
            .returns(resolveTypeName(method.returnTypeName))
            .addCode("return error(%S)\n", "Not yet bound to winrt-runtime")
            .build()

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
        val invocation = renderBoundInvocation(
            binding = binding,
        ) ?: return null
        return FunSpec.builder(method.name)
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
        val getterBinding = plan.instanceMemberBindings.firstOrNull {
            it.bindingName == "${property.name.uppercase()}_GETTER_SLOT"
        } ?: return null
        val getterInvocation = renderBoundInvocation(binding = getterBinding) ?: return null
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
    ): CodeBlock? {
        val callPlan = buildAbiCallPlan(binding) ?: return null
        return renderInlineAbiInvocation(
            invokeTargetExpression = binding.ownerCachePropertyName,
            slotExpression = "Metadata.${binding.bindingName}",
            callPlan = callPlan,
        )
    }

    private fun renderBoundStaticInvocation(
        binding: KotlinProjectionStaticMemberBinding,
    ): CodeBlock? {
        val callPlan = buildAbiCallPlan(binding.returnBinding, binding.parameterBindings) ?: return null
        return renderInlineAbiInvocation(
            invokeTargetExpression = "StaticInterfaces.${binding.ownerAccessorName}()",
            slotExpression = binding.bindingName,
            callPlan = callPlan,
        )
    }

    private fun buildAbiCallPlan(
        binding: KotlinProjectionInstanceMemberBinding,
    ): KotlinProjectionAbiCallPlan? =
        buildAbiCallPlan(binding.returnBinding, binding.parameterBindings)

    private fun buildAbiCallPlan(
        returnBinding: KotlinProjectionAbiTypeBinding,
        parameterBindings: List<KotlinProjectionAbiParameterBinding>,
    ): KotlinProjectionAbiCallPlan? {
        val parameterMarshalers = parameterBindings.map { parameterBinding ->
            buildAbiParameterMarshaler(parameterBinding) ?: return null
        }
        val returnMarshaler = when (returnBinding.kind) {
            KotlinProjectionAbiValueKind.Unit -> null
            else -> buildAbiReturnMarshaler(returnBinding) ?: return null
        }
        return KotlinProjectionAbiCallPlan(parameterMarshalers = parameterMarshalers, returnMarshaler = returnMarshaler)
    }

    private fun buildAbiParameterMarshaler(
        parameterBinding: KotlinProjectionAbiParameterBinding,
    ): KotlinProjectionAbiMarshalerPlan? {
        val parameterName = parameterBinding.name
        val abiLocalName = "__${parameterName}Abi"
        return when (parameterBinding.typeBinding.kind) {
            KotlinProjectionAbiValueKind.String -> KotlinProjectionAbiMarshalerPlan(
                name = parameterName,
                typeBinding = parameterBinding.typeBinding,
                isReturn = false,
                abiLayout = CodeBlock.of("%T.ADDRESS", VALUE_LAYOUT_CLASS_NAME),
                invokeDescriptorLayout = CodeBlock.of("%T.ADDRESS", VALUE_LAYOUT_CLASS_NAME),
                abiArgumentExpression = CodeBlock.of("%L.handle", abiLocalName),
                scopeOpener = CodeBlock.of("%T.create(%L).use { %L ->", HSTRING_CLASS_NAME, parameterName, abiLocalName),
            )
            KotlinProjectionAbiValueKind.Boolean -> KotlinProjectionAbiMarshalerPlan(
                name = parameterName,
                typeBinding = parameterBinding.typeBinding,
                isReturn = false,
                abiLayout = CodeBlock.of("%T.JAVA_BYTE", VALUE_LAYOUT_CLASS_NAME),
                invokeDescriptorLayout = CodeBlock.of("%T.JAVA_BYTE", VALUE_LAYOUT_CLASS_NAME),
                abiArgumentExpression = CodeBlock.of("if (%L) 1.toByte() else 0.toByte()", parameterName),
            )
            KotlinProjectionAbiValueKind.Double -> KotlinProjectionAbiMarshalerPlan(
                name = parameterName,
                typeBinding = parameterBinding.typeBinding,
                isReturn = false,
                abiLayout = CodeBlock.of("%T.JAVA_DOUBLE", VALUE_LAYOUT_CLASS_NAME),
                invokeDescriptorLayout = CodeBlock.of("%T.JAVA_DOUBLE", VALUE_LAYOUT_CLASS_NAME),
                abiArgumentExpression = CodeBlock.of("%L", parameterName),
            )
            KotlinProjectionAbiValueKind.UInt32 -> KotlinProjectionAbiMarshalerPlan(
                name = parameterName,
                typeBinding = parameterBinding.typeBinding,
                isReturn = false,
                abiLayout = CodeBlock.of("%T.JAVA_INT", VALUE_LAYOUT_CLASS_NAME),
                invokeDescriptorLayout = CodeBlock.of("%T.JAVA_INT", VALUE_LAYOUT_CLASS_NAME),
                abiArgumentExpression = CodeBlock.of("%L.toInt()", parameterName),
            )
            KotlinProjectionAbiValueKind.Int32 -> KotlinProjectionAbiMarshalerPlan(
                name = parameterName,
                typeBinding = parameterBinding.typeBinding,
                isReturn = false,
                abiLayout = CodeBlock.of("%T.JAVA_INT", VALUE_LAYOUT_CLASS_NAME),
                invokeDescriptorLayout = CodeBlock.of("%T.JAVA_INT", VALUE_LAYOUT_CLASS_NAME),
                abiArgumentExpression = CodeBlock.of("%L", parameterName),
            )
            KotlinProjectionAbiValueKind.ProjectedObject -> KotlinProjectionAbiMarshalerPlan(
                name = parameterName,
                typeBinding = parameterBinding.typeBinding,
                isReturn = false,
                abiLayout = CodeBlock.of("%T.ADDRESS", VALUE_LAYOUT_CLASS_NAME),
                invokeDescriptorLayout = CodeBlock.of("%T.ADDRESS", VALUE_LAYOUT_CLASS_NAME),
                abiArgumentExpression = CodeBlock.of("(%L as %T).nativeObject.pointer", parameterName, IWINRT_OBJECT_CLASS_NAME),
            )
            KotlinProjectionAbiValueKind.UnknownReference,
            KotlinProjectionAbiValueKind.InspectableReference -> KotlinProjectionAbiMarshalerPlan(
                name = parameterName,
                typeBinding = parameterBinding.typeBinding,
                isReturn = false,
                abiLayout = CodeBlock.of("%T.ADDRESS", VALUE_LAYOUT_CLASS_NAME),
                invokeDescriptorLayout = CodeBlock.of("%T.ADDRESS", VALUE_LAYOUT_CLASS_NAME),
                abiArgumentExpression = CodeBlock.of("%L.pointer", parameterName),
            )
            else -> null
        }
    }

    private fun buildAbiReturnMarshaler(
        returnBinding: KotlinProjectionAbiTypeBinding,
    ): KotlinProjectionAbiMarshalerPlan? {
        val returnType = resolveTypeName(returnBinding.typeName) as? ClassName
        val resultOutLayout = when (returnBinding.kind) {
            KotlinProjectionAbiValueKind.String,
            KotlinProjectionAbiValueKind.ProjectedObject,
            KotlinProjectionAbiValueKind.UnknownReference,
            KotlinProjectionAbiValueKind.InspectableReference -> CodeBlock.of("%T.ADDRESS", VALUE_LAYOUT_CLASS_NAME)
            KotlinProjectionAbiValueKind.Boolean -> CodeBlock.of("%T.JAVA_BYTE", VALUE_LAYOUT_CLASS_NAME)
            KotlinProjectionAbiValueKind.Int32,
            KotlinProjectionAbiValueKind.UInt32 -> CodeBlock.of("%T.JAVA_INT", VALUE_LAYOUT_CLASS_NAME)
            KotlinProjectionAbiValueKind.Double -> CodeBlock.of("%T.JAVA_DOUBLE", VALUE_LAYOUT_CLASS_NAME)
            KotlinProjectionAbiValueKind.Unit,
            KotlinProjectionAbiValueKind.Unsupported -> return null
        }
        val readbackStatement = when (returnBinding.kind) {
            KotlinProjectionAbiValueKind.String ->
                CodeBlock.of(
                    "return %T.fromHandle(__resultOut.get(%T.ADDRESS, 0), owner = true).use { it.toKString() }\n",
                    HSTRING_CLASS_NAME,
                    VALUE_LAYOUT_CLASS_NAME,
                )
            KotlinProjectionAbiValueKind.Boolean ->
                CodeBlock.of("return __resultOut.get(%T.JAVA_BYTE, 0).toInt() != 0\n", VALUE_LAYOUT_CLASS_NAME)
            KotlinProjectionAbiValueKind.Int32 ->
                CodeBlock.of("return __resultOut.get(%T.JAVA_INT, 0)\n", VALUE_LAYOUT_CLASS_NAME)
            KotlinProjectionAbiValueKind.UInt32 ->
                CodeBlock.of("return __resultOut.get(%T.JAVA_INT, 0).toUInt()\n", VALUE_LAYOUT_CLASS_NAME)
            KotlinProjectionAbiValueKind.Double ->
                CodeBlock.of("return __resultOut.get(%T.JAVA_DOUBLE, 0)\n", VALUE_LAYOUT_CLASS_NAME)
            KotlinProjectionAbiValueKind.ProjectedObject ->
                if (returnType != null) {
                    CodeBlock.of(
                        "return %T(__resultOut.get(%T.ADDRESS, 0)).use { %T.Metadata.wrap(it.asInspectable()) }\n",
                        IUNKNOWN_REFERENCE_CLASS_NAME,
                        VALUE_LAYOUT_CLASS_NAME,
                        returnType,
                    )
                } else {
                    return null
                }
            KotlinProjectionAbiValueKind.InspectableReference ->
                if (returnType == IINSPECTABLE_REFERENCE_CLASS_NAME) {
                    CodeBlock.of(
                        "return %T(__resultOut.get(%T.ADDRESS, 0)).use { it.asInspectable() }\n",
                        IUNKNOWN_REFERENCE_CLASS_NAME,
                        VALUE_LAYOUT_CLASS_NAME,
                    )
                } else {
                    return null
                }
            KotlinProjectionAbiValueKind.UnknownReference ->
                if (returnType == IUNKNOWN_REFERENCE_CLASS_NAME) {
                    CodeBlock.of(
                        "return %T(__resultOut.get(%T.ADDRESS, 0))\n",
                        IUNKNOWN_REFERENCE_CLASS_NAME,
                        VALUE_LAYOUT_CLASS_NAME,
                    )
                } else {
                    return null
                }
            KotlinProjectionAbiValueKind.Unit,
            KotlinProjectionAbiValueKind.Unsupported -> return null
        }
        return KotlinProjectionAbiMarshalerPlan(
            name = "retval",
            typeBinding = returnBinding,
            isReturn = true,
            abiLayout = resultOutLayout,
            invokeDescriptorLayout = CodeBlock.of("%T.ADDRESS", VALUE_LAYOUT_CLASS_NAME),
            abiArgumentExpression = CodeBlock.of("__resultOut"),
            readbackStatement = readbackStatement,
        )
    }

    private fun renderInlineAbiInvocation(
        invokeTargetExpression: String,
        slotExpression: String,
        callPlan: KotlinProjectionAbiCallPlan,
    ): CodeBlock? {
        val resultMarshaler = callPlan.returnMarshaler
        val code = CodeBlock.builder()
        val scopedParameterMarshalers = callPlan.parameterMarshalers.filter { it.scopeOpener != null }
        scopedParameterMarshalers.forEach { marshaler ->
            code.add("%L\n", marshaler.scopeOpener!!)
            code.indent()
        }
        if (resultMarshaler != null) {
            code.add("return %T.ofConfined().use { __arena ->\n", ARENA_CLASS_NAME)
            code.indent()
            code.addStatement("val __resultOut = __arena.allocate(%L)", resultMarshaler.abiLayout)
        }
        code.add("val __hr = %L.invokeAbi(\n", invokeTargetExpression)
        code.indent()
        code.add("slot = %L,\n", slotExpression)
        code.add("descriptor = %T.of(\n", FUNCTION_DESCRIPTOR_CLASS_NAME)
        code.indent()
        code.add("%T.JAVA_INT,\n", VALUE_LAYOUT_CLASS_NAME)
        code.add("%T.ADDRESS", VALUE_LAYOUT_CLASS_NAME)
        callPlan.parameterMarshalers.forEach { marshaler ->
            code.add(",\n%L", marshaler.invokeDescriptorLayout)
        }
        resultMarshaler?.let { marshaler ->
            code.add(",\n%L", marshaler.invokeDescriptorLayout)
        }
        code.add("\n")
        code.unindent()
        code.add(")")
        callPlan.parameterMarshalers.forEach { marshaler ->
            code.add(",\n%L", marshaler.abiArgumentExpression)
        }
        resultMarshaler?.let { marshaler ->
            code.add(",\n%L", marshaler.abiArgumentExpression)
        }
        code.add("\n")
        code.unindent()
        code.add(")\n")
        code.addStatement("%T(__hr).requireSuccess()", HRESULT_CLASS_NAME)
        resultMarshaler?.readbackStatement?.let(code::add)
        if (resultMarshaler != null) {
            code.unindent()
            code.add("}\n")
        }
        repeat(scopedParameterMarshalers.size) {
            code.unindent()
            code.add("}\n")
        }
        return code.build()
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
                staticMethods.forEach { addFunction(renderBoundStaticMethod(plan, it) ?: renderStubMethod(it)) }
                staticProperties.forEach { addProperty(renderBoundStaticProperty(plan, it) ?: renderStubProperty(it)) }
                staticEvents.forEach { event ->
                    renderEventFunctions(event, abstract = false).forEach(::addFunction)
                }
            }
            .build()

    private fun renderBoundStaticMethod(
        plan: KotlinTypeProjectionPlan,
        method: WinRtMethodDefinition,
    ): FunSpec? {
        val binding = plan.staticMemberBindings.firstOrNull { it.bindingName == "STATIC_${method.name.uppercase()}_SLOT" } ?: return null
        val invocation = renderBoundStaticInvocation(binding) ?: return null
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
        val getterInvocation = renderBoundStaticInvocation(getterBinding) ?: return null
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
        val projectedClassName = ClassName(plan.packageName, plan.type.name)
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
                        "return instance.queryInterface(iid).getOrThrow().use { %T(it.getRef(), iid) }\n",
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
                    PropertySpec.builder("${method.name.uppercase()}_METHOD_ROW_ID", Int::class)
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
