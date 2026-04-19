package io.github.kitectlab.winrt.projections.generator

import io.github.kitectlab.winrt.metadata.WinRtMetadataModel
import io.github.kitectlab.winrt.metadata.WinRtEventDefinition
import io.github.kitectlab.winrt.metadata.WinRtIntegralType
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
import io.github.kitectlab.winrt.runtime.WinRtDelegateBridge
import io.github.kitectlab.winrt.runtime.WinRtDelegateDescriptor
import io.github.kitectlab.winrt.runtime.WinRtDelegateReference
import io.github.kitectlab.winrt.runtime.WinRtDelegateValueKind
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
private val WINRT_DELEGATE_BRIDGE_CLASS_NAME = WinRtDelegateBridge::class.asClassName()
private val WINRT_DELEGATE_DESCRIPTOR_CLASS_NAME = WinRtDelegateDescriptor::class.asClassName()
private val WINRT_DELEGATE_REFERENCE_CLASS_NAME = WinRtDelegateReference::class.asClassName()
private val WINRT_DELEGATE_VALUE_KIND_CLASS_NAME = WinRtDelegateValueKind::class.asClassName()
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
private val MEMORY_SEGMENT_CLASS_NAME = ClassName("java.lang.foreign", "MemorySegment")
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
    val delegateInvokeShape: KotlinProjectionDelegateInvokeShape? = null,
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
    Enum,
    ProjectedInterface,
    ProjectedRuntimeClass,
    Struct,
    Delegate,
    Object,
    UnknownReference,
    InspectableReference,
    Unsupported,
}

data class KotlinProjectionAbiTypeBinding(
    val kind: KotlinProjectionAbiValueKind,
    val typeName: String,
    val resolvedTypeName: String = typeName,
    val sourceTypeKind: WinRtTypeKind? = null,
    val enumUnderlyingType: WinRtIntegralType? = null,
    val delegateInvokeShape: KotlinProjectionDelegateInvokeShape? = null,
)

data class KotlinProjectionAbiParameterBinding(
    val name: String,
    val typeBinding: KotlinProjectionAbiTypeBinding,
)

data class KotlinProjectionDelegateInvokeShape(
    val interfaceId: Guid? = null,
    val parameterBindings: List<KotlinProjectionAbiParameterBinding> = emptyList(),
    val returnBinding: KotlinProjectionAbiTypeBinding,
)

private data class KotlinProjectionAbiMarshalerPlan(
    val name: String,
    val typeBinding: KotlinProjectionAbiTypeBinding,
    val isReturn: Boolean,
    val abiLayout: CodeBlock,
    val invokeDescriptorLayout: CodeBlock,
    val abiArgumentExpression: CodeBlock,
    val scopeOpeners: List<CodeBlock> = emptyList(),
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
            delegateInvokeShape =
                if (type.kind == WinRtTypeKind.Delegate) {
                    classifyAbiTypeBinding(type.qualifiedName, type.namespace, typesByQualifiedName).delegateInvokeShape
                } else {
                    null
                },
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
                    returnBinding = classifyAbiTypeBinding(method.returnTypeName, type.namespace, typesByQualifiedName),
                    parameterBindings = method.parameters.map { parameter ->
                        KotlinProjectionAbiParameterBinding(
                            name = parameter.name,
                            typeBinding = classifyAbiTypeBinding(parameter.typeName, type.namespace, typesByQualifiedName),
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
                        returnBinding = classifyAbiTypeBinding(property.typeName, type.namespace, typesByQualifiedName),
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
                                typeBinding = classifyAbiTypeBinding(property.typeName, type.namespace, typesByQualifiedName),
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
                        returnBinding = classifyAbiTypeBinding("Int", type.namespace, typesByQualifiedName),
                        parameterBindings = listOf(
                            KotlinProjectionAbiParameterBinding(
                                name = "handler",
                                typeBinding = classifyAbiTypeBinding(event.delegateTypeName, type.namespace, typesByQualifiedName),
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
                                typeBinding = classifyAbiTypeBinding("Int", type.namespace, typesByQualifiedName),
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
                    returnBinding = classifyAbiTypeBinding(method.returnTypeName, type.namespace, typesByQualifiedName),
                    parameterBindings = method.parameters.map { parameter ->
                        KotlinProjectionAbiParameterBinding(
                            name = parameter.name,
                            typeBinding = classifyAbiTypeBinding(parameter.typeName, type.namespace, typesByQualifiedName),
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
                        returnBinding = classifyAbiTypeBinding(property.typeName, type.namespace, typesByQualifiedName),
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
                                typeBinding = classifyAbiTypeBinding(property.typeName, type.namespace, typesByQualifiedName),
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

    private fun classifyAbiTypeBinding(
        typeName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
        includeDelegateInvokeShape: Boolean = true,
    ): KotlinProjectionAbiTypeBinding {
        val trimmedTypeName = typeName.trim()
        val rawTypeName = trimmedTypeName.substringBefore('<').removeSuffix("?")
        val resolvedTypeName = qualifyTypeName(rawTypeName, currentNamespace, typesByQualifiedName) ?: rawTypeName
        val resolvedType = typesByQualifiedName[resolvedTypeName]
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
            else -> when {
                rawTypeName == "Any" || rawTypeName == "System.Object" -> KotlinProjectionAbiValueKind.Object
                resolvedType != null -> when (resolvedType.kind) {
                    WinRtTypeKind.Interface -> KotlinProjectionAbiValueKind.ProjectedInterface
                    WinRtTypeKind.RuntimeClass -> KotlinProjectionAbiValueKind.ProjectedRuntimeClass
                    WinRtTypeKind.Enum -> KotlinProjectionAbiValueKind.Enum
                    WinRtTypeKind.Struct -> KotlinProjectionAbiValueKind.Struct
                    WinRtTypeKind.Delegate -> KotlinProjectionAbiValueKind.Delegate
                    WinRtTypeKind.Unknown -> KotlinProjectionAbiValueKind.Unsupported
                }
                rawTypeName in SPECIAL_TYPE_MAPPINGS -> KotlinProjectionAbiValueKind.Unsupported
                else -> KotlinProjectionAbiValueKind.Unsupported
            }
        }
        val delegateInvokeShape = if (
            includeDelegateInvokeShape &&
            kind == KotlinProjectionAbiValueKind.Delegate &&
            resolvedType != null
        ) {
            val invokeMethod = requireDelegateInvokeMethod(resolvedType)
            KotlinProjectionDelegateInvokeShape(
                interfaceId = resolvedType.iid,
                parameterBindings = invokeMethod.parameters.map { parameter ->
                    KotlinProjectionAbiParameterBinding(
                        name = parameter.name,
                        typeBinding = classifyAbiTypeBinding(
                            typeName = parameter.typeName,
                            currentNamespace = resolvedType.namespace,
                            typesByQualifiedName = typesByQualifiedName,
                            includeDelegateInvokeShape = false,
                        ),
                    )
                },
                returnBinding = classifyAbiTypeBinding(
                    typeName = invokeMethod.returnTypeName,
                    currentNamespace = resolvedType.namespace,
                    typesByQualifiedName = typesByQualifiedName,
                    includeDelegateInvokeShape = false,
                ),
            )
        } else {
            null
        }
        return KotlinProjectionAbiTypeBinding(
            kind = kind,
            typeName = trimmedTypeName,
            resolvedTypeName = resolvedTypeName,
            sourceTypeKind = resolvedType?.kind,
            enumUnderlyingType = resolvedType?.enumUnderlyingType,
            delegateInvokeShape = delegateInvokeShape,
        )
    }

    private fun qualifyTypeName(
        rawTypeName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    ): String? {
        if (rawTypeName.isBlank()) {
            return null
        }
        if (rawTypeName in typesByQualifiedName) {
            return rawTypeName
        }
        if ('.' !in rawTypeName) {
            val qualified = "$currentNamespace.$rawTypeName"
            if (qualified in typesByQualifiedName) {
                return qualified
            }
        }
        return null
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

private fun KotlinProjectionAbiTypeBinding.isMarshalableAbiKind(): Boolean = when (kind) {
    KotlinProjectionAbiValueKind.Unit,
    KotlinProjectionAbiValueKind.String,
    KotlinProjectionAbiValueKind.Boolean,
    KotlinProjectionAbiValueKind.Int32,
    KotlinProjectionAbiValueKind.UInt32,
    KotlinProjectionAbiValueKind.Double,
    KotlinProjectionAbiValueKind.ProjectedRuntimeClass,
    KotlinProjectionAbiValueKind.UnknownReference,
    KotlinProjectionAbiValueKind.InspectableReference -> true
    KotlinProjectionAbiValueKind.Enum -> enumUnderlyingType != null
    KotlinProjectionAbiValueKind.Delegate -> delegateInvokeShape?.isSupportedOutboundDelegateShape() == true
    KotlinProjectionAbiValueKind.ProjectedInterface,
    KotlinProjectionAbiValueKind.Struct,
    KotlinProjectionAbiValueKind.Object,
    KotlinProjectionAbiValueKind.Unsupported -> false
}

private fun KotlinProjectionAbiTypeBinding.describeAbiKind(): String = when (kind) {
    KotlinProjectionAbiValueKind.Enum -> "Enum(${resolvedTypeName})"
    KotlinProjectionAbiValueKind.ProjectedInterface -> "Interface(${resolvedTypeName})"
    KotlinProjectionAbiValueKind.ProjectedRuntimeClass -> "RuntimeClass(${resolvedTypeName})"
    KotlinProjectionAbiValueKind.Struct -> "Struct(${resolvedTypeName})"
    KotlinProjectionAbiValueKind.Delegate -> "Delegate(${resolvedTypeName})"
    KotlinProjectionAbiValueKind.Object -> "Object(${resolvedTypeName})"
    else -> kind.name
}

private fun requireDelegateInvokeMethod(type: WinRtTypeDefinition): WinRtMethodDefinition {
    val invokeMethods = type.methods.filter { it.name == "Invoke" }
    require(invokeMethods.size == 1 && type.methods.size == 1) {
        "Delegate(${type.qualifiedName}) must expose exactly one Invoke method in metadata before projection planning."
    }
    return invokeMethods.single()
}

private fun KotlinProjectionDelegateInvokeShape.isSupportedOutboundDelegateShape(): Boolean =
    interfaceId != null &&
        returnBinding.kind == KotlinProjectionAbiValueKind.Unit &&
        parameterBindings.all { it.typeBinding.isSupportedDelegateCallbackBinding() }

private fun KotlinProjectionDelegateInvokeShape.isSupportedProjectedDelegateShape(): Boolean =
    interfaceId != null &&
        parameterBindings.all { it.typeBinding.isSupportedProjectedDelegateBinding() } &&
        returnBinding.isSupportedProjectedDelegateReturnBinding()

private fun KotlinProjectionAbiTypeBinding.isSupportedDelegateCallbackBinding(): Boolean = when (kind) {
    KotlinProjectionAbiValueKind.String,
    KotlinProjectionAbiValueKind.Int32,
    KotlinProjectionAbiValueKind.UInt32,
    KotlinProjectionAbiValueKind.ProjectedRuntimeClass,
    KotlinProjectionAbiValueKind.UnknownReference,
    KotlinProjectionAbiValueKind.InspectableReference -> true
    KotlinProjectionAbiValueKind.Enum -> enumUnderlyingType == WinRtIntegralType.Int32 || enumUnderlyingType == WinRtIntegralType.UInt32
    else -> false
}

private fun KotlinProjectionAbiTypeBinding.isSupportedProjectedDelegateBinding(): Boolean = when (kind) {
    KotlinProjectionAbiValueKind.String,
    KotlinProjectionAbiValueKind.Boolean,
    KotlinProjectionAbiValueKind.Int32,
    KotlinProjectionAbiValueKind.UInt32,
    KotlinProjectionAbiValueKind.Double,
    KotlinProjectionAbiValueKind.ProjectedRuntimeClass,
    KotlinProjectionAbiValueKind.UnknownReference,
    KotlinProjectionAbiValueKind.InspectableReference -> true
    KotlinProjectionAbiValueKind.Enum -> enumUnderlyingType == WinRtIntegralType.Int32 || enumUnderlyingType == WinRtIntegralType.UInt32
    else -> false
}

private fun KotlinProjectionAbiTypeBinding.isSupportedProjectedDelegateReturnBinding(): Boolean =
    isSupportedProjectedDelegateBinding() || kind == KotlinProjectionAbiValueKind.Unit

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
            (renderBoundEventFunctions(plan, event) ?: renderEventFunctions(event, abstract = false))
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
            .primaryConstructor(FunSpec.constructorBuilder().build())
            .apply { applyCommonTypeShape(this, plan, addModifiers = false) }
            .build()

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
                            .addParameter("pointer", MEMORY_SEGMENT_CLASS_NAME)
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
        val invocation = renderBoundInvocation(binding)
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

    private fun requireAbiCallPlan(
        bindingName: String,
        returnBinding: KotlinProjectionAbiTypeBinding,
        parameterBindings: List<KotlinProjectionAbiParameterBinding>,
    ): KotlinProjectionAbiCallPlan {
        return requireNotNull(buildAbiCallPlan(returnBinding, parameterBindings)) {
            val unsupportedKinds = (listOf(returnBinding) + parameterBindings.map(KotlinProjectionAbiParameterBinding::typeBinding))
                .filter { binding -> !binding.isMarshalableAbiKind() }
                .map(KotlinProjectionAbiTypeBinding::describeAbiKind)
                .distinct()
                .joinToString(", ")
            "Generator ABI marshaler parity does not yet support $bindingName for $unsupportedKinds."
        }
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
                scopeOpeners = listOf(
                    CodeBlock.of("%T.create(%L).use { %L ->", HSTRING_CLASS_NAME, parameterName, abiLocalName),
                ),
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
            KotlinProjectionAbiValueKind.Enum -> enumParameterMarshaler(parameterBinding)
            KotlinProjectionAbiValueKind.Delegate -> delegateParameterMarshaler(parameterBinding)
            KotlinProjectionAbiValueKind.ProjectedRuntimeClass -> KotlinProjectionAbiMarshalerPlan(
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
        val returnType = resolveTypeName(returnBinding.resolvedTypeName) as? ClassName
        val resultOutLayout = when (returnBinding.kind) {
            KotlinProjectionAbiValueKind.String,
            KotlinProjectionAbiValueKind.UnknownReference,
            KotlinProjectionAbiValueKind.InspectableReference -> CodeBlock.of("%T.ADDRESS", VALUE_LAYOUT_CLASS_NAME)
            KotlinProjectionAbiValueKind.ProjectedRuntimeClass -> CodeBlock.of("%T.ADDRESS", VALUE_LAYOUT_CLASS_NAME)
            KotlinProjectionAbiValueKind.Enum -> abiLayoutForIntegralType(returnBinding.enumUnderlyingType ?: return null)
            KotlinProjectionAbiValueKind.Boolean -> CodeBlock.of("%T.JAVA_BYTE", VALUE_LAYOUT_CLASS_NAME)
            KotlinProjectionAbiValueKind.Int32,
            KotlinProjectionAbiValueKind.UInt32 -> CodeBlock.of("%T.JAVA_INT", VALUE_LAYOUT_CLASS_NAME)
            KotlinProjectionAbiValueKind.Double -> CodeBlock.of("%T.JAVA_DOUBLE", VALUE_LAYOUT_CLASS_NAME)
            KotlinProjectionAbiValueKind.Unit,
            KotlinProjectionAbiValueKind.ProjectedInterface,
            KotlinProjectionAbiValueKind.Struct,
            KotlinProjectionAbiValueKind.Object,
            KotlinProjectionAbiValueKind.Unsupported -> return null
            KotlinProjectionAbiValueKind.Delegate -> CodeBlock.of("%T.ADDRESS", VALUE_LAYOUT_CLASS_NAME)
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
            KotlinProjectionAbiValueKind.Enum ->
                enumReturnReadback(returnBinding, returnType)
            KotlinProjectionAbiValueKind.ProjectedRuntimeClass ->
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
            KotlinProjectionAbiValueKind.Delegate ->
                if (returnType != null) {
                    CodeBlock.of(
                        "return %T.Metadata.fromAbi(__resultOut.get(%T.ADDRESS, 0)) ?: error(%S)\n",
                        returnType,
                        VALUE_LAYOUT_CLASS_NAME,
                        "Expected non-null delegate instance from ABI return for ${returnBinding.resolvedTypeName}.",
                    )
                } else {
                    return null
                }
            KotlinProjectionAbiValueKind.Unit,
            KotlinProjectionAbiValueKind.ProjectedInterface,
            KotlinProjectionAbiValueKind.Struct,
            KotlinProjectionAbiValueKind.Object,
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

    private fun enumParameterMarshaler(
        parameterBinding: KotlinProjectionAbiParameterBinding,
    ): KotlinProjectionAbiMarshalerPlan? {
        val integralType = parameterBinding.typeBinding.enumUnderlyingType ?: return null
        return KotlinProjectionAbiMarshalerPlan(
            name = parameterBinding.name,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiLayout = abiLayoutForIntegralType(integralType),
            invokeDescriptorLayout = abiLayoutForIntegralType(integralType),
            abiArgumentExpression = CodeBlock.of("%L.abiValue%L", parameterBinding.name, abiIntegralArgumentConversionSuffix(integralType)),
        )
    }

    private fun delegateParameterMarshaler(
        parameterBinding: KotlinProjectionAbiParameterBinding,
    ): KotlinProjectionAbiMarshalerPlan? {
        val invokeShape = parameterBinding.typeBinding.delegateInvokeShape ?: return null
        if (!invokeShape.isSupportedOutboundDelegateShape()) {
            return null
        }
        val delegateIid = invokeShape.interfaceId ?: return null
        val handleName = "__${parameterBinding.name}Handle"
        val abiReferenceName = "__${parameterBinding.name}Abi"
        return KotlinProjectionAbiMarshalerPlan(
            name = parameterBinding.name,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiLayout = CodeBlock.of("%T.ADDRESS", VALUE_LAYOUT_CLASS_NAME),
            invokeDescriptorLayout = CodeBlock.of("%T.ADDRESS", VALUE_LAYOUT_CLASS_NAME),
            abiArgumentExpression = CodeBlock.of("%L.pointer", abiReferenceName),
            scopeOpeners = listOf(
                CodeBlock.of(
                    "%T.createUnitDelegate(iid = %T(%S), parameterKinds = %L) { __args ->\n%L(%L)\n}.use { %L ->",
                    WINRT_DELEGATE_BRIDGE_CLASS_NAME,
                    GUID_CLASS_NAME,
                    delegateIid.toString(),
                    delegateParameterKindsCode(invokeShape.parameterBindings),
                    parameterBinding.name,
                    delegateCallbackArgumentCodeList(invokeShape.parameterBindings),
                    handleName,
                ),
                CodeBlock.of("%L.createReference().use { %L ->", handleName, abiReferenceName),
            ),
        )
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

    private fun abiLayoutForIntegralType(type: WinRtIntegralType): CodeBlock = when (type) {
        WinRtIntegralType.Int8,
        WinRtIntegralType.UInt8 -> CodeBlock.of("%T.JAVA_BYTE", VALUE_LAYOUT_CLASS_NAME)
        WinRtIntegralType.Int16,
        WinRtIntegralType.UInt16 -> CodeBlock.of("%T.JAVA_SHORT", VALUE_LAYOUT_CLASS_NAME)
        WinRtIntegralType.Int32,
        WinRtIntegralType.UInt32 -> CodeBlock.of("%T.JAVA_INT", VALUE_LAYOUT_CLASS_NAME)
        WinRtIntegralType.Int64,
        WinRtIntegralType.UInt64 -> CodeBlock.of("%T.JAVA_LONG", VALUE_LAYOUT_CLASS_NAME)
    }

    private fun abiIntegralArgumentConversionSuffix(type: WinRtIntegralType): String = when (type) {
        WinRtIntegralType.UInt8 -> ".toByte()"
        WinRtIntegralType.UInt16 -> ".toShort()"
        WinRtIntegralType.UInt32 -> ".toInt()"
        WinRtIntegralType.UInt64 -> ".toLong()"
        else -> ""
    }

    private fun abiIntegralReadbackExpression(type: WinRtIntegralType): CodeBlock = when (type) {
        WinRtIntegralType.Int8 -> CodeBlock.of("__resultOut.get(%T.JAVA_BYTE, 0)", VALUE_LAYOUT_CLASS_NAME)
        WinRtIntegralType.UInt8 -> CodeBlock.of("__resultOut.get(%T.JAVA_BYTE, 0).toUByte()", VALUE_LAYOUT_CLASS_NAME)
        WinRtIntegralType.Int16 -> CodeBlock.of("__resultOut.get(%T.JAVA_SHORT, 0)", VALUE_LAYOUT_CLASS_NAME)
        WinRtIntegralType.UInt16 -> CodeBlock.of("__resultOut.get(%T.JAVA_SHORT, 0).toUShort()", VALUE_LAYOUT_CLASS_NAME)
        WinRtIntegralType.Int32 -> CodeBlock.of("__resultOut.get(%T.JAVA_INT, 0)", VALUE_LAYOUT_CLASS_NAME)
        WinRtIntegralType.UInt32 -> CodeBlock.of("__resultOut.get(%T.JAVA_INT, 0).toUInt()", VALUE_LAYOUT_CLASS_NAME)
        WinRtIntegralType.Int64 -> CodeBlock.of("__resultOut.get(%T.JAVA_LONG, 0)", VALUE_LAYOUT_CLASS_NAME)
        WinRtIntegralType.UInt64 -> CodeBlock.of("__resultOut.get(%T.JAVA_LONG, 0).toULong()", VALUE_LAYOUT_CLASS_NAME)
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
        KotlinProjectionAbiValueKind.Int32,
        KotlinProjectionAbiValueKind.UInt32,
        KotlinProjectionAbiValueKind.Enum -> CodeBlock.of("%T.INT32", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass,
        KotlinProjectionAbiValueKind.UnknownReference,
        KotlinProjectionAbiValueKind.InspectableReference -> CodeBlock.of("%T.OBJECT", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        else -> error("Unsupported delegate callback ABI kind: ${typeBinding.describeAbiKind()}")
    }

    private fun delegateInvokeValueKindCode(typeBinding: KotlinProjectionAbiTypeBinding): CodeBlock = when (typeBinding.kind) {
        KotlinProjectionAbiValueKind.Unit -> CodeBlock.of("%T.UNIT", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.String -> CodeBlock.of("%T.HSTRING", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.Boolean -> CodeBlock.of("%T.BOOLEAN", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int32 -> CodeBlock.of("%T.INT32", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.UInt32 -> CodeBlock.of("%T.UINT32", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.Double -> CodeBlock.of("%T.DOUBLE", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.Enum -> when (typeBinding.enumUnderlyingType) {
            WinRtIntegralType.Int32 -> CodeBlock.of("%T.INT32", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
            WinRtIntegralType.UInt32 -> CodeBlock.of("%T.UINT32", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
            else -> error("Unsupported enum delegate ABI kind: ${typeBinding.describeAbiKind()}")
        }
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass -> CodeBlock.of("%T.IINSPECTABLE", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
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
        KotlinProjectionAbiValueKind.Int32,
        KotlinProjectionAbiValueKind.UInt32,
        KotlinProjectionAbiValueKind.Double,
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass,
        KotlinProjectionAbiValueKind.UnknownReference,
        KotlinProjectionAbiValueKind.InspectableReference -> CodeBlock.of("%L", parameterBinding.name)
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
        KotlinProjectionAbiValueKind.Int32 -> CodeBlock.of("return %L as Int\n", nativeInvokeExpression)
        KotlinProjectionAbiValueKind.UInt32 -> CodeBlock.of("return %L as UInt\n", nativeInvokeExpression)
        KotlinProjectionAbiValueKind.Double -> CodeBlock.of("return %L as Double\n", nativeInvokeExpression)
        KotlinProjectionAbiValueKind.Enum -> {
            val enumType = resolveTypeName(returnBinding.resolvedTypeName)
            when (returnBinding.enumUnderlyingType) {
                WinRtIntegralType.Int32 -> CodeBlock.of("return %T.Metadata.fromAbi(%L as Int)\n", enumType, nativeInvokeExpression)
                WinRtIntegralType.UInt32 -> CodeBlock.of("return %T.Metadata.fromAbi(%L as UInt)\n", enumType, nativeInvokeExpression)
                else -> error("Unsupported projected delegate enum return ABI kind: ${returnBinding.describeAbiKind()}")
            }
        }
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass -> {
            val projectedType = resolveTypeName(returnBinding.resolvedTypeName)
            CodeBlock.of("return %T.Metadata.wrap(%L as %T)\n", projectedType, nativeInvokeExpression, IINSPECTABLE_REFERENCE_CLASS_NAME)
        }
        KotlinProjectionAbiValueKind.UnknownReference ->
            CodeBlock.of("return %L as %T\n", nativeInvokeExpression, IUNKNOWN_REFERENCE_CLASS_NAME)
        KotlinProjectionAbiValueKind.InspectableReference ->
            CodeBlock.of("return %L as %T\n", nativeInvokeExpression, IINSPECTABLE_REFERENCE_CLASS_NAME)
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
        KotlinProjectionAbiValueKind.Int32 -> CodeBlock.of("__args[%L] as Int", index)
        KotlinProjectionAbiValueKind.UInt32 -> CodeBlock.of("(__args[%L] as Int).toUInt()", index)
        KotlinProjectionAbiValueKind.Enum -> delegateEnumCallbackArgumentCode(index, typeBinding)
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass -> CodeBlock.of(
            "%T.Metadata.wrap((__args[%L] as %T).asInspectable())",
            resolveTypeName(typeBinding.resolvedTypeName),
            index,
            IUNKNOWN_REFERENCE_CLASS_NAME,
        )
        KotlinProjectionAbiValueKind.UnknownReference -> CodeBlock.of("__args[%L] as %T", index, IUNKNOWN_REFERENCE_CLASS_NAME)
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
            WinRtIntegralType.Int32 -> CodeBlock.of("%T.Metadata.fromAbi(__args[%L] as Int)", enumType, index)
            WinRtIntegralType.UInt32 -> CodeBlock.of("%T.Metadata.fromAbi((__args[%L] as Int).toUInt())", enumType, index)
            else -> error("Delegate callback ABI parity does not yet support $integralType for ${typeBinding.resolvedTypeName}")
        }
    }

    private fun renderInlineAbiInvocation(
        invokeTargetExpression: String,
        slotExpression: String,
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
        repeat(scopedParameterOpeners.size) {
            code.unindent()
            code.add("}\n")
        }
        return code.build()
    }

    private fun renderBoundEventFunctions(
        plan: KotlinTypeProjectionPlan,
        event: WinRtEventDefinition,
    ): List<FunSpec>? {
        val addBinding = plan.instanceMemberBindings.firstOrNull {
            it.bindingName == "${event.name.uppercase()}_ADD_SLOT"
        } ?: return null
        val removeBinding = plan.instanceMemberBindings.firstOrNull {
            it.bindingName == "${event.name.uppercase()}_REMOVE_SLOT"
        } ?: return null
        return buildBoundEventFunctions(
            event = event,
            addInvocation = renderBoundInvocation(addBinding),
            removeInvocation = renderBoundInvocation(removeBinding),
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
            addInvocation = renderBoundStaticInvocation(addBinding),
            removeInvocation = renderBoundStaticInvocation(removeBinding),
        )
    }

    private fun buildBoundEventFunctions(
        event: WinRtEventDefinition,
        addInvocation: CodeBlock,
        removeInvocation: CodeBlock,
    ): List<FunSpec> {
        val typeName = resolveTypeName(event.delegateTypeName)
        return listOf(
            FunSpec.builder("add${event.name}")
                .addParameter("handler", typeName)
                .returns(Int::class.asClassName())
                .addCode("%L\n", addInvocation)
                .build(),
            FunSpec.builder("remove${event.name}")
                .addParameter("token", Int::class.asClassName())
                .addCode("%L\n", removeInvocation)
                .build(),
        )
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
                    (renderBoundStaticEventFunctions(plan, event) ?: renderEventFunctions(event, abstract = false))
                        .forEach(::addFunction)
                }
            }
            .build()

    private fun renderBoundStaticMethod(
        plan: KotlinTypeProjectionPlan,
        method: WinRtMethodDefinition,
    ): FunSpec? {
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

    private fun resolveIntegralTypeName(type: WinRtIntegralType): TypeName = when (type) {
        WinRtIntegralType.Int8 -> Byte::class.asClassName()
        WinRtIntegralType.UInt8 -> UByte::class.asClassName()
        WinRtIntegralType.Int16 -> Short::class.asClassName()
        WinRtIntegralType.UInt16 -> UShort::class.asClassName()
        WinRtIntegralType.Int32 -> Int::class.asClassName()
        WinRtIntegralType.UInt32 -> UInt::class.asClassName()
        WinRtIntegralType.Int64 -> Long::class.asClassName()
        WinRtIntegralType.UInt64 -> ULong::class.asClassName()
    }

    private fun integralLiteral(valueBits: ULong, type: WinRtIntegralType): CodeBlock = when (type) {
        WinRtIntegralType.Int8 -> CodeBlock.of("%L.toByte()", valueBits.toByte())
        WinRtIntegralType.UInt8 -> CodeBlock.of("%L.toUByte()", valueBits.toUByte())
        WinRtIntegralType.Int16 -> CodeBlock.of("%L.toShort()", valueBits.toShort())
        WinRtIntegralType.UInt16 -> CodeBlock.of("%L.toUShort()", valueBits.toUShort())
        WinRtIntegralType.Int32 -> CodeBlock.of("%L", valueBits.toInt())
        WinRtIntegralType.UInt32 -> CodeBlock.of("%L.toUInt()", valueBits.toUInt())
        WinRtIntegralType.Int64 -> CodeBlock.of("%L", "${valueBits.toLong()}L")
        WinRtIntegralType.UInt64 -> CodeBlock.of("%L", "${valueBits}uL")
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
