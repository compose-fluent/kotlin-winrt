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
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.OffsetDateTime
import kotlin.collections.AbstractList
import kotlin.collections.AbstractMap
import kotlin.LazyThreadSafetyMode

private val ROOT_PACKAGE_SEGMENTS = listOf("io", "github", "kitectlab", "winrt", "projections")
private val IREFERENCE_GENERIC_INTERFACE_ID = Guid("61C17706-2D65-11E0-9AE8-D48564015472")
private val IREFERENCE_ARRAY_GENERIC_INTERFACE_ID = Guid("61C17707-2D65-11E0-9AE8-D48564015472")
private val GUID_CLASS_NAME = Guid::class.asClassName()
private val ACTIVATION_FACTORY_CLASS_NAME = ActivationFactory::class.asClassName()
private val COM_OBJECT_REFERENCE_CLASS_NAME = ComObjectReference::class.asClassName()
private val COM_VTABLE_INVOKER_CLASS_NAME = ComVtableInvoker::class.asClassName()
private val HRESULT_CLASS_NAME = HResult::class.asClassName()
private val HSTRING_CLASS_NAME = HString::class.asClassName()
private val IUNKNOWN_REFERENCE_CLASS_NAME = IUnknownReference::class.asClassName()
private val IINSPECTABLE_REFERENCE_CLASS_NAME = ClassName("io.github.kitectlab.winrt.runtime", "IInspectableReference")
private val IWINRT_OBJECT_CLASS_NAME = IWinRTObject::class.asClassName()
private val MARSHALER_CLASS_NAME = Marshaler::class.asClassName()
private val PLATFORM_ABI_CLASS_NAME = PlatformAbi::class.asClassName()
private val PARAMETERIZED_INTERFACE_ID_CLASS_NAME = ParameterizedInterfaceId::class.asClassName()
private val WINRT_BINDABLE_ITERABLE_PROJECTION_CLASS_NAME = WinRtBindableIterableProjection::class.asClassName()
private val WINRT_BINDABLE_VECTOR_PROJECTION_CLASS_NAME = WinRtBindableVectorProjection::class.asClassName()
private val WINRT_BINDABLE_VECTOR_VIEW_PROJECTION_CLASS_NAME = WinRtBindableVectorViewProjection::class.asClassName()
private val WINRT_COLLECTION_INTERFACE_IDS_CLASS_NAME = WinRtCollectionInterfaceIds::class.asClassName()
private val WINRT_DICTIONARY_PROJECTION_CLASS_NAME = WinRtDictionaryProjection::class.asClassName()
private val WINRT_ITERABLE_PROJECTION_CLASS_NAME = WinRtIterableProjection::class.asClassName()
private val WINRT_LIST_PROJECTION_CLASS_NAME = WinRtListProjection::class.asClassName()
private val WINRT_ASYNC_ACTION_REFERENCE_CLASS_NAME = WinRtAsyncActionReference::class.asClassName()
private val WINRT_ASYNC_ACTION_WITH_PROGRESS_REFERENCE_CLASS_NAME = WinRtAsyncActionWithProgressReference::class.asClassName()
private val WINRT_ASYNC_ACTION_WITH_PROGRESS_VFTBL_SLOTS_CLASS_NAME = WinRtAsyncActionWithProgressVftblSlots::class.asClassName()
private val WINRT_ASYNC_OPERATION_REFERENCE_CLASS_NAME = WinRtAsyncOperationReference::class.asClassName()
private val WINRT_ASYNC_OPERATION_WITH_PROGRESS_REFERENCE_CLASS_NAME = WinRtAsyncOperationWithProgressReference::class.asClassName()
private val WINRT_ASYNC_OPERATION_WITH_PROGRESS_VFTBL_SLOTS_CLASS_NAME = WinRtAsyncOperationWithProgressVftblSlots::class.asClassName()
private val WINRT_ASYNC_OPERATION_VFTBL_SLOTS_CLASS_NAME = WinRtAsyncOperationVftblSlots::class.asClassName()
private val WINRT_READ_ONLY_DICTIONARY_PROJECTION_CLASS_NAME = WinRtReadOnlyDictionaryProjection::class.asClassName()
private val WINRT_READ_ONLY_LIST_PROJECTION_CLASS_NAME = WinRtReadOnlyListProjection::class.asClassName()
private val WINRT_REFERENCE_ARRAY_PROJECTION_CLASS_NAME = WinRtReferenceArrayProjection::class.asClassName()
private val WINRT_REFERENCE_PROJECTION_CLASS_NAME = WinRtReferenceProjection::class.asClassName()
private val WINRT_REFERENCE_VALUE_ADAPTER_CLASS_NAME = WinRtReferenceValueAdapter::class.asClassName()
private val WINRT_PLATFORM_API_CLASS_NAME = WinRtPlatformApi::class.asClassName()
private val WINRT_TYPE_SIGNATURE_CLASS_NAME = WinRtTypeSignature::class.asClassName()
private val WINRT_TYPE_HANDLE_CLASS_NAME = WinRtTypeHandle::class.asClassName()
private val WINRT_DELEGATE_BRIDGE_CLASS_NAME = WinRtDelegateBridge::class.asClassName()
private val WINRT_DELEGATE_DESCRIPTOR_CLASS_NAME = WinRtDelegateDescriptor::class.asClassName()
private val WINRT_DELEGATE_REFERENCE_CLASS_NAME = WinRtDelegateReference::class.asClassName()
private val WINRT_DELEGATE_VALUE_KIND_CLASS_NAME = WinRtDelegateValueKind::class.asClassName()
private val ATTRIBUTE_CLASS_NAME = Annotation::class.asClassName()
private val ABSTRACT_LIST_CLASS_NAME = AbstractList::class.asClassName()
private val ABSTRACT_MAP_CLASS_NAME = AbstractMap::class.asClassName()
private val ABSTRACT_MUTABLE_LIST_CLASS_NAME = ClassName("kotlin.collections", "AbstractMutableList")
private val ABSTRACT_MUTABLE_MAP_CLASS_NAME = ClassName("kotlin.collections", "AbstractMutableMap")
private val ABSTRACT_MUTABLE_SET_CLASS_NAME = ClassName("kotlin.collections", "AbstractMutableSet")
private val URI_CLASS_NAME = URI::class.asClassName()
private val OFFSET_DATE_TIME_CLASS_NAME = OffsetDateTime::class.asClassName()
private val DURATION_CLASS_NAME = Duration::class.asClassName()
private val AUTO_CLOSEABLE_CLASS_NAME = AutoCloseable::class.asClassName()
private val ILLEGAL_STATE_EXCEPTION_CLASS_NAME = IllegalStateException::class.asClassName()
private val NO_SUCH_ELEMENT_EXCEPTION_CLASS_NAME = NoSuchElementException::class.asClassName()
private val LAZY_THREAD_SAFETY_MODE_CLASS_NAME = LazyThreadSafetyMode::class.asClassName()
private val MUTABLE_LIST_CLASS_NAME = ClassName("kotlin.collections", "MutableList")
private val MUTABLE_MAP_CLASS_NAME = ClassName("kotlin.collections", "MutableMap")
private val RAW_ADDRESS_CLASS_NAME = RawAddress::class.asClassName()
private val NATIVE_NESTED_STRUCT_FIELD_SPEC_CLASS_NAME = NativeNestedStructFieldSpec::class.asClassName()
private val NATIVE_SCALAR_FIELD_SPEC_CLASS_NAME = NativeScalarFieldSpec::class.asClassName()
private val NATIVE_STRUCT_LAYOUT_CLASS_NAME = NativeStructLayout::class.asClassName()
private val NATIVE_STRUCT_SCALAR_KIND_CLASS_NAME = NativeStructScalarKind::class.asClassName()
private val RUNTIME_POINT_CLASS_NAME = ClassName("io.github.kitectlab.winrt.runtime", "Point")
private val RUNTIME_SIZE_CLASS_NAME = ClassName("io.github.kitectlab.winrt.runtime", "Size")
private val RUNTIME_RECT_CLASS_NAME = ClassName("io.github.kitectlab.winrt.runtime", "Rect")
private val RUNTIME_VECTOR2_CLASS_NAME = ClassName("io.github.kitectlab.winrt.runtime", "Vector2")
private val RUNTIME_VECTOR3_CLASS_NAME = ClassName("io.github.kitectlab.winrt.runtime", "Vector3")
private val RUNTIME_VECTOR4_CLASS_NAME = ClassName("io.github.kitectlab.winrt.runtime", "Vector4")
private val RUNTIME_QUATERNION_CLASS_NAME = ClassName("io.github.kitectlab.winrt.runtime", "Quaternion")
private val RUNTIME_MATRIX3X2_CLASS_NAME = ClassName("io.github.kitectlab.winrt.runtime", "Matrix3x2")
private val RUNTIME_MATRIX4X4_CLASS_NAME = ClassName("io.github.kitectlab.winrt.runtime", "Matrix4x4")
private val RUNTIME_PLANE_CLASS_NAME = ClassName("io.github.kitectlab.winrt.runtime", "Plane")

private typealias SpecialTypeResolver = (List<TypeName>) -> TypeName

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

enum class KotlinProjectionReadOnlyCollectionKind {
    Iterable,
    VectorView,
    MapView,
}

enum class KotlinProjectionMutableCollectionKind {
    Vector,
    Map,
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
    val typesByQualifiedName: Map<String, WinRtTypeDefinition> = emptyMap(),
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
    val readOnlyCollectionBindings: List<KotlinProjectionReadOnlyCollectionBinding> = emptyList(),
    val mutableCollectionBindings: List<KotlinProjectionMutableCollectionBinding> = emptyList(),
    val delegateInvokeShape: KotlinProjectionDelegateInvokeShape? = null,
    val eventInvokeDescriptors: List<WinRtEventInvokeDescriptor> = emptyList(),
    val typeDeclarationDescriptor: WinRtTypeDeclarationDescriptor,
    val factorySurfaceDescriptor: WinRtFactorySurfaceDescriptor? = null,
    val objectReferenceSurfaceDescriptor: WinRtObjectReferenceSurfaceDescriptor? = null,
    val guidSignatureDescriptor: WinRtGuidSignatureDescriptor? = null,
    val interfaceMemberSignatureSetDescriptor: WinRtInterfaceMemberSignatureSetDescriptor? = null,
    val customMappedMemberOutputDescriptor: WinRtCustomMappedMemberOutputDescriptor? = null,
    val genericAbiClassInitializationDescriptor: WinRtGenericAbiClassInitializationDescriptor? = null,
    val requiredInterfaceAugmentationDescriptor: WinRtRequiredInterfaceAugmentationDescriptor? = null,
    val moduleActivationAndAuthoringDescriptor: WinRtModuleActivationAndAuthoringDescriptor? = null,
    val companionKinds: List<KotlinProjectionCompanionKind> = emptyList(),
)

data class KotlinProjectionInterfaceBinding(
    val qualifiedName: String,
    val iid: Guid? = null,
)

data class KotlinProjectionAbiSlotBinding(
    val constantName: String,
    val slot: Int,
    val descriptor: WinRtMethodVtableDescriptor? = null,
)

data class KotlinProjectionInstanceMemberBinding(
    val bindingName: String,
    val ownerInterfaceQualifiedName: String,
    val ownerCachePropertyName: String,
    val slotInterfaceQualifiedName: String,
    val slotConstantName: String,
    val returnBinding: KotlinProjectionAbiTypeBinding,
    val parameterBindings: List<KotlinProjectionAbiParameterBinding> = emptyList(),
    val signatureDescriptor: WinRtSignatureWriterDescriptor? = null,
    val marshalerPlanDescriptor: WinRtAbiMarshalerPlanDescriptor? = null,
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
    val signatureDescriptor: WinRtSignatureWriterDescriptor? = null,
    val marshalerPlanDescriptor: WinRtAbiMarshalerPlanDescriptor? = null,
)

data class KotlinProjectionReadOnlyCollectionBinding(
    val kind: KotlinProjectionReadOnlyCollectionKind,
    val ownerInterfaceQualifiedName: String,
    val ownerCachePropertyName: String,
    val slotInterfaceQualifiedName: String,
    val delegatePropertyName: String,
    val elementBinding: KotlinProjectionAbiTypeBinding? = null,
    val keyBinding: KotlinProjectionAbiTypeBinding? = null,
    val valueBinding: KotlinProjectionAbiTypeBinding? = null,
)

data class KotlinProjectionMutableCollectionBinding(
    val kind: KotlinProjectionMutableCollectionKind,
    val ownerInterfaceQualifiedName: String,
    val ownerCachePropertyName: String,
    val slotInterfaceQualifiedName: String,
    val delegatePropertyName: String,
    val elementBinding: KotlinProjectionAbiTypeBinding? = null,
    val keyBinding: KotlinProjectionAbiTypeBinding? = null,
    val valueBinding: KotlinProjectionAbiTypeBinding? = null,
)

enum class KotlinProjectionAbiValueKind {
    Unit,
    String,
    Boolean,
    Int8,
    UInt8,
    Int16,
    UInt16,
    Int32,
    UInt32,
    Int64,
    UInt64,
    Float,
    Double,
    Char16,
    GuidValue,
    Enum,
    MappedIterable,
    MappedVector,
    MappedMap,
    MappedVectorView,
    MappedMapView,
    MappedKeyValuePair,
    MappedBindableIterable,
    MappedBindableVector,
    MappedBindableVectorView,
    MappedAsyncAction,
    MappedAsyncActionWithProgress,
    MappedAsyncOperation,
    MappedAsyncOperationWithProgress,
    Reference,
    ReferenceArray,
    Array,
    ProjectedInterface,
    ProjectedRuntimeClass,
    Struct,
    Delegate,
    Object,
    UnknownReference,
    InspectableReference,
    Unsupported,
}

private data class KotlinProjectionMappedType(
    val abiQualifiedName: String,
    val projectedTypeResolver: SpecialTypeResolver,
    val abiValueKind: KotlinProjectionAbiValueKind? = null,
    val readOnlyCollectionKind: KotlinProjectionReadOnlyCollectionKind? = null,
    val mutableCollectionKind: KotlinProjectionMutableCollectionKind? = null,
    val descriptionName: String = abiQualifiedName.substringAfterLast('.'),
) {
    init {
        require(readOnlyCollectionKind == null || mutableCollectionKind == null) {
            "Mapped type '$abiQualifiedName' cannot be both read-only and mutable."
        }
    }
}

private data class KotlinProjectionIntegralAbiDescriptor(
    val kotlinTypeName: TypeName,
    val argumentConversionSuffix: String = "",
    val literalRenderer: (ULong) -> CodeBlock,
)

/**
 * `.cswinrt/src/cswinrt/helpers.h` converges mapped WinRT type decisions through `mapped_type` plus
 * `get_mapped_type(...)`. Keep Kotlin projection planning on the same ownership model instead of
 * repeating the same collection/async/custom-type tables across unrelated helpers.
 */
private val MAPPED_TYPES: List<KotlinProjectionMappedType> = listOf(
    KotlinProjectionMappedType("System.Object", { IINSPECTABLE_REFERENCE_CLASS_NAME }, descriptionName = "Object"),
    KotlinProjectionMappedType("WinRT.Interop.HWND", { Long::class.asClassName() }, descriptionName = "HWND"),
    KotlinProjectionMappedType("Windows.Foundation.DateTime", { OFFSET_DATE_TIME_CLASS_NAME }, descriptionName = "DateTime"),
    KotlinProjectionMappedType("Windows.Foundation.TimeSpan", { DURATION_CLASS_NAME }, descriptionName = "TimeSpan"),
    KotlinProjectionMappedType("Windows.Foundation.Uri", { URI_CLASS_NAME }, descriptionName = "Uri"),
    KotlinProjectionMappedType("Windows.Foundation.IClosable", { AUTO_CLOSEABLE_CLASS_NAME }, descriptionName = "IClosable"),
    KotlinProjectionMappedType(
        "Windows.Foundation.IReference",
        { arguments -> arguments.single().copy(nullable = true) },
        abiValueKind = KotlinProjectionAbiValueKind.Reference,
        descriptionName = "IReference",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.IReferenceArray",
        { arguments -> Array::class.asClassName().parameterizedBy(arguments.single().copy(nullable = true)) },
        abiValueKind = KotlinProjectionAbiValueKind.ReferenceArray,
        descriptionName = "IReferenceArray",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.Point",
        { RUNTIME_POINT_CLASS_NAME },
        abiValueKind = KotlinProjectionAbiValueKind.Struct,
        descriptionName = "Point",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.Size",
        { RUNTIME_SIZE_CLASS_NAME },
        abiValueKind = KotlinProjectionAbiValueKind.Struct,
        descriptionName = "Size",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.Rect",
        { RUNTIME_RECT_CLASS_NAME },
        abiValueKind = KotlinProjectionAbiValueKind.Struct,
        descriptionName = "Rect",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.Numerics.Vector2",
        { RUNTIME_VECTOR2_CLASS_NAME },
        abiValueKind = KotlinProjectionAbiValueKind.Struct,
        descriptionName = "Vector2",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.Numerics.Vector3",
        { RUNTIME_VECTOR3_CLASS_NAME },
        abiValueKind = KotlinProjectionAbiValueKind.Struct,
        descriptionName = "Vector3",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.Numerics.Vector4",
        { RUNTIME_VECTOR4_CLASS_NAME },
        abiValueKind = KotlinProjectionAbiValueKind.Struct,
        descriptionName = "Vector4",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.Numerics.Quaternion",
        { RUNTIME_QUATERNION_CLASS_NAME },
        abiValueKind = KotlinProjectionAbiValueKind.Struct,
        descriptionName = "Quaternion",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.Numerics.Matrix3x2",
        { RUNTIME_MATRIX3X2_CLASS_NAME },
        abiValueKind = KotlinProjectionAbiValueKind.Struct,
        descriptionName = "Matrix3x2",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.Numerics.Matrix4x4",
        { RUNTIME_MATRIX4X4_CLASS_NAME },
        abiValueKind = KotlinProjectionAbiValueKind.Struct,
        descriptionName = "Matrix4x4",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.Numerics.Plane",
        { RUNTIME_PLANE_CLASS_NAME },
        abiValueKind = KotlinProjectionAbiValueKind.Struct,
        descriptionName = "Plane",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.Collections.IIterable",
        projectedTypeResolver = { arguments -> Iterable::class.asClassName().parameterizedBy(arguments) },
        abiValueKind = KotlinProjectionAbiValueKind.MappedIterable,
        readOnlyCollectionKind = KotlinProjectionReadOnlyCollectionKind.Iterable,
        descriptionName = "Iterable",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.Collections.IIterator",
        projectedTypeResolver = { arguments -> Iterator::class.asClassName().parameterizedBy(arguments) },
        descriptionName = "Iterator",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.Collections.IVectorView",
        projectedTypeResolver = { arguments -> List::class.asClassName().parameterizedBy(arguments) },
        abiValueKind = KotlinProjectionAbiValueKind.MappedVectorView,
        readOnlyCollectionKind = KotlinProjectionReadOnlyCollectionKind.VectorView,
        descriptionName = "VectorView",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.Collections.IVector",
        projectedTypeResolver = { arguments -> MUTABLE_LIST_CLASS_NAME.parameterizedBy(arguments) },
        abiValueKind = KotlinProjectionAbiValueKind.MappedVector,
        mutableCollectionKind = KotlinProjectionMutableCollectionKind.Vector,
        descriptionName = "Vector",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.Collections.IMapView",
        projectedTypeResolver = { arguments -> Map::class.asClassName().parameterizedBy(arguments) },
        abiValueKind = KotlinProjectionAbiValueKind.MappedMapView,
        readOnlyCollectionKind = KotlinProjectionReadOnlyCollectionKind.MapView,
        descriptionName = "MapView",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.Collections.IMap",
        projectedTypeResolver = { arguments -> MUTABLE_MAP_CLASS_NAME.parameterizedBy(arguments) },
        abiValueKind = KotlinProjectionAbiValueKind.MappedMap,
        mutableCollectionKind = KotlinProjectionMutableCollectionKind.Map,
        descriptionName = "Map",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.Collections.IKeyValuePair",
        projectedTypeResolver = { arguments ->
            if (arguments.size == 2) {
                Map.Entry::class.asClassName().parameterizedBy(arguments)
            } else {
                ClassName("io.github.kitectlab.winrt.projections.windows.foundation.collections", "IKeyValuePair")
            }
        },
        abiValueKind = KotlinProjectionAbiValueKind.MappedKeyValuePair,
        descriptionName = "KeyValuePair",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.IAsyncAction",
        { WINRT_ASYNC_ACTION_REFERENCE_CLASS_NAME },
        abiValueKind = KotlinProjectionAbiValueKind.MappedAsyncAction,
        descriptionName = "IAsyncAction",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.IAsyncActionWithProgress",
        { arguments -> WINRT_ASYNC_ACTION_WITH_PROGRESS_REFERENCE_CLASS_NAME.parameterizedBy(arguments.single()) },
        abiValueKind = KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress,
        descriptionName = "IAsyncActionWithProgress",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.IAsyncOperation",
        { arguments -> WINRT_ASYNC_OPERATION_REFERENCE_CLASS_NAME.parameterizedBy(arguments.single()) },
        abiValueKind = KotlinProjectionAbiValueKind.MappedAsyncOperation,
        descriptionName = "IAsyncOperation",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.IAsyncOperationWithProgress",
        { arguments -> WINRT_ASYNC_OPERATION_WITH_PROGRESS_REFERENCE_CLASS_NAME.parameterizedBy(arguments[0], arguments[1]) },
        abiValueKind = KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress,
        descriptionName = "IAsyncOperationWithProgress",
    ),
    KotlinProjectionMappedType(
        "Microsoft.UI.Xaml.Interop.IBindableIterable",
        { Iterable::class.asClassName().parameterizedBy(ANY.copy(nullable = true)) },
        abiValueKind = KotlinProjectionAbiValueKind.MappedBindableIterable,
        readOnlyCollectionKind = KotlinProjectionReadOnlyCollectionKind.Iterable,
        descriptionName = "IBindableIterable",
    ),
    KotlinProjectionMappedType(
        "Microsoft.UI.Xaml.Interop.IBindableVectorView",
        { List::class.asClassName().parameterizedBy(ANY.copy(nullable = true)) },
        abiValueKind = KotlinProjectionAbiValueKind.MappedBindableVectorView,
        readOnlyCollectionKind = KotlinProjectionReadOnlyCollectionKind.VectorView,
        descriptionName = "IBindableVectorView",
    ),
    KotlinProjectionMappedType(
        "Microsoft.UI.Xaml.Interop.IBindableVector",
        { MUTABLE_LIST_CLASS_NAME.parameterizedBy(ANY.copy(nullable = true)) },
        abiValueKind = KotlinProjectionAbiValueKind.MappedBindableVector,
        mutableCollectionKind = KotlinProjectionMutableCollectionKind.Vector,
        descriptionName = "IBindableVector",
    ),
    KotlinProjectionMappedType(
        "Windows.UI.Xaml.Interop.IBindableIterable",
        { Iterable::class.asClassName().parameterizedBy(ANY.copy(nullable = true)) },
        abiValueKind = KotlinProjectionAbiValueKind.MappedBindableIterable,
        readOnlyCollectionKind = KotlinProjectionReadOnlyCollectionKind.Iterable,
        descriptionName = "IBindableIterable",
    ),
    KotlinProjectionMappedType(
        "Windows.UI.Xaml.Interop.IBindableVectorView",
        { List::class.asClassName().parameterizedBy(ANY.copy(nullable = true)) },
        abiValueKind = KotlinProjectionAbiValueKind.MappedBindableVectorView,
        readOnlyCollectionKind = KotlinProjectionReadOnlyCollectionKind.VectorView,
        descriptionName = "IBindableVectorView",
    ),
    KotlinProjectionMappedType(
        "Windows.UI.Xaml.Interop.IBindableVector",
        { MUTABLE_LIST_CLASS_NAME.parameterizedBy(ANY.copy(nullable = true)) },
        abiValueKind = KotlinProjectionAbiValueKind.MappedBindableVector,
        mutableCollectionKind = KotlinProjectionMutableCollectionKind.Vector,
        descriptionName = "IBindableVector",
    ),
)

private val MAPPED_TYPES_BY_ABI_NAME: Map<String, KotlinProjectionMappedType> =
    MAPPED_TYPES.associateBy(KotlinProjectionMappedType::abiQualifiedName)

private val MAPPED_TYPES_BY_SIMPLE_ABI_NAME: Map<String, KotlinProjectionMappedType> =
    MAPPED_TYPES.filter { mappedType ->
        mappedType.abiValueKind in setOf(
            KotlinProjectionAbiValueKind.Struct,
            KotlinProjectionAbiValueKind.Reference,
            KotlinProjectionAbiValueKind.ReferenceArray,
        ) ||
            mappedType.descriptionName in setOf("Object", "HWND", "DateTime", "TimeSpan", "Uri", "IClosable")
    }.groupBy { it.abiQualifiedName.substringAfterLast('.') }
        .filterValues { it.size == 1 }
        .mapValues { (_, mappedTypes) -> mappedTypes.single() }

private val MAPPED_TYPES_BY_ABI_KIND: Map<KotlinProjectionAbiValueKind, KotlinProjectionMappedType> =
    MAPPED_TYPES.mapNotNull { mappedType ->
        mappedType.abiValueKind?.let { abiValueKind -> abiValueKind to mappedType }
    }.toMap()

private val INTEGRAL_ABI_DESCRIPTORS: Map<WinRtIntegralType, KotlinProjectionIntegralAbiDescriptor> = mapOf(
    WinRtIntegralType.Int8 to KotlinProjectionIntegralAbiDescriptor(
        kotlinTypeName = Byte::class.asClassName(),
        literalRenderer = { valueBits -> CodeBlock.of("%L.toByte()", valueBits.toByte()) },
    ),
    WinRtIntegralType.UInt8 to KotlinProjectionIntegralAbiDescriptor(
        kotlinTypeName = UByte::class.asClassName(),
        argumentConversionSuffix = ".toByte()",
        literalRenderer = { valueBits -> CodeBlock.of("%L.toUByte()", valueBits.toUByte()) },
    ),
    WinRtIntegralType.Int16 to KotlinProjectionIntegralAbiDescriptor(
        kotlinTypeName = Short::class.asClassName(),
        literalRenderer = { valueBits -> CodeBlock.of("%L.toShort()", valueBits.toShort()) },
    ),
    WinRtIntegralType.UInt16 to KotlinProjectionIntegralAbiDescriptor(
        kotlinTypeName = UShort::class.asClassName(),
        argumentConversionSuffix = ".toShort()",
        literalRenderer = { valueBits -> CodeBlock.of("%L.toUShort()", valueBits.toUShort()) },
    ),
    WinRtIntegralType.Int32 to KotlinProjectionIntegralAbiDescriptor(
        kotlinTypeName = Int::class.asClassName(),
        literalRenderer = { valueBits -> CodeBlock.of("%L", valueBits.toInt()) },
    ),
    WinRtIntegralType.UInt32 to KotlinProjectionIntegralAbiDescriptor(
        kotlinTypeName = UInt::class.asClassName(),
        argumentConversionSuffix = ".toInt()",
        literalRenderer = { valueBits -> CodeBlock.of("%L.toUInt()", valueBits.toUInt()) },
    ),
    WinRtIntegralType.Int64 to KotlinProjectionIntegralAbiDescriptor(
        kotlinTypeName = Long::class.asClassName(),
        literalRenderer = { valueBits -> CodeBlock.of("%L", "${valueBits.toLong()}L") },
    ),
    WinRtIntegralType.UInt64 to KotlinProjectionIntegralAbiDescriptor(
        kotlinTypeName = ULong::class.asClassName(),
        argumentConversionSuffix = ".toLong()",
        literalRenderer = { valueBits -> CodeBlock.of("%L", "${valueBits}uL") },
    ),
)

private fun mappedTypeByAbiName(abiQualifiedName: String): KotlinProjectionMappedType? =
    MAPPED_TYPES_BY_ABI_NAME[abiQualifiedName]
        ?: abiQualifiedName.takeIf { '.' !in it }?.let(MAPPED_TYPES_BY_SIMPLE_ABI_NAME::get)

private fun mappedTypeByAbiKind(kind: KotlinProjectionAbiValueKind): KotlinProjectionMappedType? =
    MAPPED_TYPES_BY_ABI_KIND[kind]

private fun integralAbiDescriptor(type: WinRtIntegralType): KotlinProjectionIntegralAbiDescriptor =
    INTEGRAL_ABI_DESCRIPTORS.getValue(type)

data class KotlinProjectionAbiTypeBinding(
    val kind: KotlinProjectionAbiValueKind,
    val typeName: String,
    val resolvedTypeName: String = typeName,
    val sourceTypeKind: WinRtTypeKind? = null,
    val interfaceId: Guid? = null,
    val enumUnderlyingType: WinRtIntegralType? = null,
    val delegateInvokeShape: KotlinProjectionDelegateInvokeShape? = null,
    val typeArguments: List<KotlinProjectionAbiTypeBinding> = emptyList(),
)

data class KotlinProjectionAbiParameterBinding(
    val name: String,
    val typeBinding: KotlinProjectionAbiTypeBinding,
    val category: WinRtMetadataParameterCategory = WinRtMetadataParameterCategory.In,
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
    val abiArgumentExpression: CodeBlock,
    val extraAbiArgumentExpressions: List<CodeBlock> = emptyList(),
    val scopeOpeners: List<CodeBlock> = emptyList(),
    val postCallStatements: List<CodeBlock> = emptyList(),
    val resultAllocation: CodeBlock? = null,
    val resultLocalDeclarations: CodeBlock? = null,
    val readbackStatement: CodeBlock? = null,
)

private data class KotlinProjectionAbiCallPlan(
    val parameterMarshalers: List<KotlinProjectionAbiMarshalerPlan>,
    val returnMarshaler: KotlinProjectionAbiMarshalerPlan? = null,
    val descriptor: WinRtAbiMarshalerPlanDescriptor? = null,
)

data class KotlinProjectionFile(
    val relativePath: String,
    val packageName: String,
    val contents: String,
) {
    fun writeTo(outputRoot: Path) {
        val target = outputRoot.resolve(relativePath)
        Files.createDirectories(target.parent)
        Files.writeString(target, contents)
    }
}

class KotlinProjectionContractValidator {
    fun validate(model: WinRtMetadataModel): WinRtMetadataModel =
        model.requireValidForProjection(GENERATOR_VALIDATION_OPTIONS)

    fun validateType(type: WinRtTypeDefinition) =
        WinRtMetadataModel(listOf(WinRtNamespace(type.namespace, listOf(type)))).requireValidForProjection(GENERATOR_VALIDATION_OPTIONS)

    companion object {
        private val GENERATOR_VALIDATION_OPTIONS = WinRtMetadataValidationOptions(
            validateTypeReferences = false,
            validateActivationFactoryReferences = false,
            validateRuntimeClassDefaultInterface = false,
        )
    }
}

class KotlinProjectionPlanner(
    private val validator: KotlinProjectionContractValidator = KotlinProjectionContractValidator(),
) {
    fun plan(model: WinRtMetadataModel): List<KotlinTypeProjectionPlan> =
        validator.validate(model).let { normalized ->
            val semanticHelpers = normalized.semanticHelpers()
            val typesByQualifiedName = normalized.namespaces
                .flatMap(WinRtNamespace::types)
                .associateBy(WinRtTypeDefinition::qualifiedName)
            val interfaceIidsByName = normalized.namespaces
                .flatMap(WinRtNamespace::types)
                .associate { it.qualifiedName to it.iid }
            normalized.namespaces.flatMap { planNamespace(it, interfaceIidsByName, typesByQualifiedName, semanticHelpers) }
        }

    fun planNamespace(
        namespace: WinRtNamespace,
        interfaceIidsByName: Map<String, Guid?> = emptyMap(),
        typesByQualifiedName: Map<String, WinRtTypeDefinition> = emptyMap(),
        semanticHelpers: WinRtMetadataSemanticHelpers? = null,
    ): List<KotlinTypeProjectionPlan> =
        namespace.normalized().let { normalizedNamespace ->
            val helpers = semanticHelpers ?: WinRtMetadataModel(listOf(normalizedNamespace)).semanticHelpers()
            normalizedNamespace.types.mapNotNull { type ->
                planType(type, interfaceIidsByName, typesByQualifiedName, helpers)
            }
        }

    private fun planType(
        type: WinRtTypeDefinition,
        interfaceIidsByName: Map<String, Guid?>,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): KotlinTypeProjectionPlan? {
        val typeDeclarationDescriptor = semanticHelpers.typeDeclarationDescriptor(type)
        if (!typeDeclarationDescriptor.writesProjectedDeclaration) {
            return null
        }
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
            typesByQualifiedName = typesByQualifiedName,
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
            abiSlotBindings = planAbiSlotBindings(type, typesByQualifiedName, semanticHelpers),
            instanceMemberBindings = planInstanceMemberBindings(type, typesByQualifiedName, semanticHelpers),
            staticMemberBindings = planStaticMemberBindings(type, typesByQualifiedName, semanticHelpers),
            readOnlyCollectionBindings = planReadOnlyCollectionBindings(type, typesByQualifiedName),
            mutableCollectionBindings = planMutableCollectionBindings(type, typesByQualifiedName),
            delegateInvokeShape =
                if (type.kind == WinRtTypeKind.Delegate) {
                    classifyAbiTypeBinding(type.qualifiedName, type.namespace, typesByQualifiedName).delegateInvokeShape
                } else {
                    null
                },
            eventInvokeDescriptors = type.events.map { event -> semanticHelpers.eventInvokeDescriptor(type, event) },
            typeDeclarationDescriptor = typeDeclarationDescriptor,
            factorySurfaceDescriptor = if (type.kind == WinRtTypeKind.RuntimeClass) semanticHelpers.factorySurfaceDescriptor(type) else null,
            objectReferenceSurfaceDescriptor = if (type.kind in setOf(WinRtTypeKind.RuntimeClass, WinRtTypeKind.Delegate)) {
                semanticHelpers.objectReferenceSurfaceDescriptor(type)
            } else {
                null
            },
            guidSignatureDescriptor = type.iid?.let { semanticHelpers.guidSignatureDescriptor(type) },
            interfaceMemberSignatureSetDescriptor = if (type.kind == WinRtTypeKind.Interface) {
                semanticHelpers.interfaceMemberSignatureSetDescriptor(type)
            } else {
                null
            },
            customMappedMemberOutputDescriptor = if (type.kind == WinRtTypeKind.Interface) {
                semanticHelpers.customMappedMemberOutputDescriptor(type)
            } else {
                null
            },
            genericAbiClassInitializationDescriptor = if (type.kind in setOf(WinRtTypeKind.Interface, WinRtTypeKind.Delegate)) {
                semanticHelpers.genericAbiClassInitializationDescriptor(type)
            } else {
                null
            },
            requiredInterfaceAugmentationDescriptor = if (type.kind in setOf(WinRtTypeKind.Interface, WinRtTypeKind.RuntimeClass)) {
                semanticHelpers.requiredInterfaceAugmentationDescriptor(type)
            } else {
                null
            },
            moduleActivationAndAuthoringDescriptor = if (type.kind == WinRtTypeKind.RuntimeClass) {
                semanticHelpers.moduleActivationAndAuthoringDescriptor(type)
            } else {
                null
            },
            companionKinds = planCompanions(type),
        )
    }

    private fun planAbiSlotBindings(
        type: WinRtTypeDefinition,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): List<KotlinProjectionAbiSlotBinding> {
        if (type.kind != WinRtTypeKind.Interface) {
            return emptyList()
        }
        val methodDescriptorsByConstant = semanticHelpers.vtableWriterDescriptor(type).methods
            .associateBy { descriptor -> descriptor.methodName.methodSlotConstantName() }
        val baseSlotCount = type.implementedInterfaces.sumOf { implemented ->
            interfaceAbiMemberCount(implemented.interfaceName, typesByQualifiedName, mutableSetOf())
        }
        return type.localAbiMembers().mapIndexed { index, constantName ->
            val descriptor = methodDescriptorsByConstant[constantName]
            KotlinProjectionAbiSlotBinding(
                constantName = constantName,
                slot = descriptor?.slotIndex ?: 6 + baseSlotCount + index,
                descriptor = descriptor,
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
        semanticHelpers: WinRtMetadataSemanticHelpers,
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
            .filterNot { interfaceName ->
                isMappedCollectionInterfaceName(interfaceName, type.namespace, typesByQualifiedName)
            }

        return buildList {
            type.methods.filterNot(WinRtMethodDefinition::isStatic).forEach { method ->
                val signatureDescriptor = semanticHelpers.signatureWriterDescriptor(method)
                resolveInstanceMemberBinding(
                    candidateInterfaces = candidateInterfaces,
                    typesByQualifiedName = typesByQualifiedName,
                    slotConstantName = "${method.name.uppercase()}_SLOT",
                    returnBinding = classifyAbiTypeBinding(signatureDescriptor.projectionReturnTypeName, type.namespace, typesByQualifiedName),
                    parameterBindings = signatureDescriptor.parameters.map { parameter ->
                        KotlinProjectionAbiParameterBinding(
                            name = parameter.escapedName,
                            typeBinding = classifyAbiTypeBinding(parameter.projectionTypeName, type.namespace, typesByQualifiedName),
                            category = parameter.category,
                        )
                    },
                    signatureDescriptor = signatureDescriptor,
                    marshalerPlanDescriptor = semanticHelpers.abiMarshalerPlanDescriptor(method),
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
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): List<KotlinProjectionStaticMemberBinding> {
        if (type.kind != WinRtTypeKind.RuntimeClass) {
            return emptyList()
        }
        val candidateInterfaces = type.activation.staticInterfaceNames.distinct()
        return buildList {
            type.methods.filter(WinRtMethodDefinition::isStatic).forEach { method ->
                val signatureDescriptor = semanticHelpers.signatureWriterDescriptor(method)
                resolveStaticMemberBinding(
                    candidateInterfaces = candidateInterfaces,
                    typesByQualifiedName = typesByQualifiedName,
                    bindingName = "STATIC_${method.name.uppercase()}_SLOT",
                    slotConstantName = "${method.name.uppercase()}_SLOT",
                    returnBinding = classifyAbiTypeBinding(signatureDescriptor.projectionReturnTypeName, type.namespace, typesByQualifiedName),
                    parameterBindings = signatureDescriptor.parameters.map { parameter ->
                        KotlinProjectionAbiParameterBinding(
                            name = parameter.escapedName,
                            typeBinding = classifyAbiTypeBinding(parameter.projectionTypeName, type.namespace, typesByQualifiedName),
                            category = parameter.category,
                        )
                    },
                    signatureDescriptor = signatureDescriptor,
                    marshalerPlanDescriptor = semanticHelpers.abiMarshalerPlanDescriptor(method),
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

    private fun planReadOnlyCollectionBindings(
        type: WinRtTypeDefinition,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    ): List<KotlinProjectionReadOnlyCollectionBinding> {
        if (type.kind != WinRtTypeKind.RuntimeClass) {
            return emptyList()
        }
        val mutableBindings = planMutableCollectionBindings(type, typesByQualifiedName)
        val candidateInterfaces = buildList {
            type.defaultInterfaceName?.let(::add)
            type.implementedInterfaces
                .filterNot { it.isDefault }
                .mapTo(this) { it.interfaceName }
        }.distinct()
            .filterNot { interfaceName ->
                isMappedCollectionInterfaceName(interfaceName, type.namespace, typesByQualifiedName)
            }

        val bindings = candidateInterfaces.flatMap { ownerInterface ->
            collectReadOnlyCollectionBindings(
                ownerInterface = ownerInterface,
                defaultInterfaceName = type.defaultInterfaceName,
                currentInterfaceName = ownerInterface,
                currentNamespace = ownerInterface.substringBeforeLast('.', type.namespace),
                typesByQualifiedName = typesByQualifiedName,
                visiting = mutableSetOf(),
            )
        }.distinctBy { binding ->
            buildString {
                append(binding.kind)
                append('|')
                append(binding.elementBinding?.resolvedTypeName)
                append('|')
                append(binding.keyBinding?.resolvedTypeName)
                append('|')
                append(binding.valueBinding?.resolvedTypeName)
            }
        }

        val vectorElements = bindings
            .filter { it.kind == KotlinProjectionReadOnlyCollectionKind.VectorView }
            .mapNotNull { it.elementBinding?.resolvedTypeName }
            .toSet()
        val mapEntryPairs = bindings
            .filter { it.kind == KotlinProjectionReadOnlyCollectionKind.MapView }
            .map { it.keyBinding?.resolvedTypeName to it.valueBinding?.resolvedTypeName }
            .toSet()

        return bindings.filterNot { binding ->
            when (binding.kind) {
                KotlinProjectionReadOnlyCollectionKind.Iterable -> {
                    val elementBinding = binding.elementBinding ?: return@filterNot false
                    elementBinding.resolvedTypeName in vectorElements ||
                        (
                            elementBinding.kind == KotlinProjectionAbiValueKind.MappedKeyValuePair &&
                                (elementBinding.typeArguments.firstOrNull()?.resolvedTypeName to
                                    elementBinding.typeArguments.getOrNull(1)?.resolvedTypeName) in mapEntryPairs
                            )
                }
                else -> false
            }
        }.filterNot { binding -> binding.isRedundantReadOnlyCollectionBinding(mutableBindings) }
    }

    private fun planMutableCollectionBindings(
        type: WinRtTypeDefinition,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    ): List<KotlinProjectionMutableCollectionBinding> {
        if (type.kind != WinRtTypeKind.RuntimeClass) {
            return emptyList()
        }
        val candidateInterfaces = buildList {
            type.defaultInterfaceName?.let(::add)
            type.implementedInterfaces
                .filterNot { it.isDefault }
                .mapTo(this) { it.interfaceName }
        }.distinct()
            .filterNot { interfaceName ->
                isMappedCollectionInterfaceName(interfaceName, type.namespace, typesByQualifiedName)
            }

        return candidateInterfaces.flatMap { ownerInterface ->
            collectMutableCollectionBindings(
                ownerInterface = ownerInterface,
                defaultInterfaceName = type.defaultInterfaceName,
                currentInterfaceName = ownerInterface,
                currentNamespace = ownerInterface.substringBeforeLast('.', type.namespace),
                typesByQualifiedName = typesByQualifiedName,
                visiting = mutableSetOf(),
            )
        }.distinctBy { binding ->
            buildString {
                append(binding.kind)
                append('|')
                append(binding.elementBinding?.resolvedTypeName)
                append('|')
                append(binding.keyBinding?.resolvedTypeName)
                append('|')
                append(binding.valueBinding?.resolvedTypeName)
            }
        }
    }

    private fun collectMutableCollectionBindings(
        ownerInterface: String,
        defaultInterfaceName: String?,
        currentInterfaceName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
        visiting: MutableSet<String>,
    ): List<KotlinProjectionMutableCollectionBinding> {
        val rawInterfaceName = currentInterfaceName.substringBefore('<').removeSuffix("?")
        val resolvedInterfaceName = qualifyTypeName(rawInterfaceName, currentNamespace, typesByQualifiedName) ?: rawInterfaceName
        if (!visiting.add("$ownerInterface->$currentInterfaceName")) {
            return emptyList()
        }
        return try {
            buildList {
                mutableCollectionBindingFor(
                    ownerInterface = ownerInterface,
                    defaultInterfaceName = defaultInterfaceName,
                    interfaceName = currentInterfaceName,
                    currentNamespace = currentNamespace,
                    typesByQualifiedName = typesByQualifiedName,
                )?.let(::add)
                val interfaceType = typesByQualifiedName[resolvedInterfaceName]
                interfaceType?.implementedInterfaces?.forEach { implemented ->
                    val implementedInterfaceName = substitutedImplementedInterfaceName(currentInterfaceName, implemented)
                    addAll(
                        collectMutableCollectionBindings(
                            ownerInterface = ownerInterface,
                            defaultInterfaceName = defaultInterfaceName,
                            currentInterfaceName = implementedInterfaceName,
                            currentNamespace = interfaceType.namespace,
                            typesByQualifiedName = typesByQualifiedName,
                            visiting = visiting,
                        ),
                    )
                }
            }
        } finally {
            visiting.remove("$ownerInterface->$currentInterfaceName")
        }
    }

    private fun mutableCollectionBindingFor(
        ownerInterface: String,
        defaultInterfaceName: String?,
        interfaceName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    ): KotlinProjectionMutableCollectionBinding? {
        val rawInterfaceName = interfaceName.substringBefore('<').removeSuffix("?")
        val resolvedInterfaceName = qualifyTypeName(rawInterfaceName, currentNamespace, typesByQualifiedName) ?: rawInterfaceName
        val genericArguments = if ('<' in interfaceName && interfaceName.endsWith('>')) {
            splitGenericArguments(interfaceName.substringAfter('<').substringBeforeLast('>'))
                .map { argument -> classifyAbiTypeBinding(argument, currentNamespace, typesByQualifiedName) }
        } else {
            emptyList()
        }
        val mappedType = mappedTypeByAbiName(resolvedInterfaceName) ?: return null
        val collectionKind = mappedType.mutableCollectionKind ?: return null
        return buildMutableCollectionBinding(
            collectionKind = collectionKind,
            ownerInterfaceQualifiedName = ownerInterface,
            ownerCachePropertyName = ownerCachePropertyName(ownerInterface, defaultInterfaceName),
            slotInterfaceQualifiedName = resolvedInterfaceName,
            delegatePropertyName = collectionKind.ownerDelegatePropertyName(ownerInterface),
            typeArguments = genericArguments,
            errorContext = ownerInterface,
            requireSupportedBinding = false,
        )
    }

    private fun collectReadOnlyCollectionBindings(
        ownerInterface: String,
        defaultInterfaceName: String?,
        currentInterfaceName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
        visiting: MutableSet<String>,
    ): List<KotlinProjectionReadOnlyCollectionBinding> {
        val rawInterfaceName = currentInterfaceName.substringBefore('<').removeSuffix("?")
        val resolvedInterfaceName = qualifyTypeName(rawInterfaceName, currentNamespace, typesByQualifiedName) ?: rawInterfaceName
        if (!visiting.add("$ownerInterface->$currentInterfaceName")) {
            return emptyList()
        }
        return try {
            val bindings = buildList {
                readOnlyCollectionBindingFor(
                    ownerInterface = ownerInterface,
                    defaultInterfaceName = defaultInterfaceName,
                    interfaceName = currentInterfaceName,
                    currentNamespace = currentNamespace,
                    typesByQualifiedName = typesByQualifiedName,
                )?.let(::add)
                val interfaceType = typesByQualifiedName[resolvedInterfaceName]
                interfaceType?.implementedInterfaces?.forEach { implemented ->
                    val implementedInterfaceName = substitutedImplementedInterfaceName(currentInterfaceName, implemented)
                    addAll(
                        collectReadOnlyCollectionBindings(
                            ownerInterface = ownerInterface,
                            defaultInterfaceName = defaultInterfaceName,
                            currentInterfaceName = implementedInterfaceName,
                            currentNamespace = interfaceType.namespace,
                            typesByQualifiedName = typesByQualifiedName,
                            visiting = visiting,
                        ),
                    )
                }
            }
            bindings
        } finally {
            visiting.remove("$ownerInterface->$currentInterfaceName")
        }
    }

    private fun readOnlyCollectionBindingFor(
        ownerInterface: String,
        defaultInterfaceName: String?,
        interfaceName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    ): KotlinProjectionReadOnlyCollectionBinding? {
        val rawInterfaceName = interfaceName.substringBefore('<').removeSuffix("?")
        val resolvedInterfaceName = qualifyTypeName(rawInterfaceName, currentNamespace, typesByQualifiedName) ?: rawInterfaceName
        val genericArguments = if ('<' in interfaceName && interfaceName.endsWith('>')) {
            splitGenericArguments(interfaceName.substringAfter('<').substringBeforeLast('>'))
                .map { argument -> classifyAbiTypeBinding(argument, currentNamespace, typesByQualifiedName) }
        } else {
            emptyList()
        }
        val mappedType = mappedTypeByAbiName(resolvedInterfaceName) ?: return null
        val collectionKind = mappedType.readOnlyCollectionKind ?: return null
        return buildReadOnlyCollectionBinding(
            collectionKind = collectionKind,
            ownerInterfaceQualifiedName = ownerInterface,
            ownerCachePropertyName = ownerCachePropertyName(ownerInterface, defaultInterfaceName),
            slotInterfaceQualifiedName = resolvedInterfaceName,
            delegatePropertyName = collectionKind.ownerDelegatePropertyName(ownerInterface),
            typeArguments = genericArguments,
            errorContext = ownerInterface,
            requireSupportedBinding = false,
        )
    }

    private fun buildReadOnlyCollectionBinding(
        collectionKind: KotlinProjectionReadOnlyCollectionKind,
        ownerInterfaceQualifiedName: String,
        ownerCachePropertyName: String,
        slotInterfaceQualifiedName: String,
        delegatePropertyName: String,
        typeArguments: List<KotlinProjectionAbiTypeBinding>,
        errorContext: String,
        requireSupportedBinding: Boolean,
        bindingLocationLabel: String = "",
    ): KotlinProjectionReadOnlyCollectionBinding? =
        createReadOnlyCollectionBindingPlan(
            collectionKind = collectionKind,
            ownerInterfaceQualifiedName = ownerInterfaceQualifiedName,
            ownerCachePropertyName = ownerCachePropertyName,
            slotInterfaceQualifiedName = slotInterfaceQualifiedName,
            delegatePropertyName = delegatePropertyName,
            typeArguments = typeArguments,
            errorContext = errorContext,
            requireSupportedBinding = requireSupportedBinding,
            bindingLocationLabel = bindingLocationLabel,
        )

    private fun buildMutableCollectionBinding(
        collectionKind: KotlinProjectionMutableCollectionKind,
        ownerInterfaceQualifiedName: String,
        ownerCachePropertyName: String,
        slotInterfaceQualifiedName: String,
        delegatePropertyName: String,
        typeArguments: List<KotlinProjectionAbiTypeBinding>,
        errorContext: String,
        requireSupportedBinding: Boolean,
        bindingLocationLabel: String = "",
    ): KotlinProjectionMutableCollectionBinding? =
        createMutableCollectionBindingPlan(
            collectionKind = collectionKind,
            ownerInterfaceQualifiedName = ownerInterfaceQualifiedName,
            ownerCachePropertyName = ownerCachePropertyName,
            slotInterfaceQualifiedName = slotInterfaceQualifiedName,
            delegatePropertyName = delegatePropertyName,
            typeArguments = typeArguments,
            errorContext = errorContext,
            requireSupportedBinding = requireSupportedBinding,
            bindingLocationLabel = bindingLocationLabel,
        )

    private fun substitutedImplementedInterfaceName(
        currentInterfaceName: String,
        implemented: WinRtInterfaceImplementationDefinition,
    ): String {
        val currentGenericArguments = genericArgumentTypeRefs(currentInterfaceName)
        if (currentGenericArguments.isEmpty()) {
            return implemented.interfaceName
        }
        return implemented.interfaceType
            .substituteTypeParameters(currentGenericArguments)
            .typeName
    }

    private fun genericArgumentTypeRefs(typeName: String): List<WinRtTypeRef> {
        val trimmed = typeName.trim()
        if ('<' !in trimmed || !trimmed.endsWith('>')) {
            return emptyList()
        }
        return splitGenericArguments(trimmed.substringAfter('<').substringBeforeLast('>'))
            .map(WinRtTypeRef::fromDisplayName)
    }

    private fun resolveInstanceMemberBinding(
        candidateInterfaces: List<String>,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
        slotConstantName: String,
        returnBinding: KotlinProjectionAbiTypeBinding,
        parameterBindings: List<KotlinProjectionAbiParameterBinding>,
        signatureDescriptor: WinRtSignatureWriterDescriptor? = null,
        marshalerPlanDescriptor: WinRtAbiMarshalerPlanDescriptor? = null,
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
                signatureDescriptor = signatureDescriptor,
                marshalerPlanDescriptor = marshalerPlanDescriptor,
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
        signatureDescriptor: WinRtSignatureWriterDescriptor? = null,
        marshalerPlanDescriptor: WinRtAbiMarshalerPlanDescriptor? = null,
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
                signatureDescriptor = signatureDescriptor,
                marshalerPlanDescriptor = marshalerPlanDescriptor,
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
        val typeArguments = if ('<' in trimmedTypeName && trimmedTypeName.endsWith('>')) {
            splitGenericArguments(trimmedTypeName.substringAfter('<').substringBeforeLast('>'))
                .map { argument ->
                    classifyAbiTypeBinding(
                        typeName = argument,
                        currentNamespace = currentNamespace,
                        typesByQualifiedName = typesByQualifiedName,
                        includeDelegateInvokeShape = false,
                    )
                }
        } else {
            emptyList()
        }
        val resolvedTypeName = qualifyTypeName(rawTypeName, currentNamespace, typesByQualifiedName) ?: rawTypeName
        val resolvedType = typesByQualifiedName[resolvedTypeName]
        val mappedType = mappedTypeByAbiName(rawTypeName)
        val kind = when (trimmedTypeName) {
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
            "Char",
            "Char16" -> KotlinProjectionAbiValueKind.Char16
            "Guid",
            "System.Guid" -> KotlinProjectionAbiValueKind.GuidValue
            IUNKNOWN_REFERENCE_CLASS_NAME.simpleName -> KotlinProjectionAbiValueKind.UnknownReference
            IINSPECTABLE_REFERENCE_CLASS_NAME.simpleName -> KotlinProjectionAbiValueKind.InspectableReference
            "io.github.kitectlab.winrt.runtime.IUnknownReference" -> KotlinProjectionAbiValueKind.UnknownReference
            "io.github.kitectlab.winrt.runtime.IInspectableReference" -> KotlinProjectionAbiValueKind.InspectableReference
            else -> when {
                rawTypeName == "Array" -> KotlinProjectionAbiValueKind.Array
                rawTypeName == "Any" || rawTypeName == "System.Object" -> KotlinProjectionAbiValueKind.Object
                mappedType?.abiValueKind != null -> mappedType.abiValueKind
                resolvedType != null -> when (resolvedType.kind) {
                    WinRtTypeKind.Interface -> KotlinProjectionAbiValueKind.ProjectedInterface
                    WinRtTypeKind.RuntimeClass -> KotlinProjectionAbiValueKind.ProjectedRuntimeClass
                    WinRtTypeKind.Enum -> KotlinProjectionAbiValueKind.Enum
                    WinRtTypeKind.Struct -> KotlinProjectionAbiValueKind.Struct
                    WinRtTypeKind.Delegate -> KotlinProjectionAbiValueKind.Delegate
                    WinRtTypeKind.Unknown -> KotlinProjectionAbiValueKind.Unsupported
                }
                mappedType != null -> KotlinProjectionAbiValueKind.Unsupported
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
            interfaceId = resolvedType?.iid ?: mappedReferenceGenericInterfaceId(kind),
            enumUnderlyingType = resolvedType?.enumUnderlyingType,
            delegateInvokeShape = delegateInvokeShape,
            typeArguments = typeArguments,
        )
    }

    private fun mappedReferenceGenericInterfaceId(kind: KotlinProjectionAbiValueKind): Guid? =
        when (kind) {
            KotlinProjectionAbiValueKind.Reference -> IREFERENCE_GENERIC_INTERFACE_ID
            KotlinProjectionAbiValueKind.ReferenceArray -> IREFERENCE_ARRAY_GENERIC_INTERFACE_ID
            else -> null
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

    private fun isMappedCollectionInterfaceName(
        interfaceName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    ): Boolean {
        val rawInterfaceName = interfaceName.substringBefore('<').removeSuffix("?")
        val resolvedInterfaceName = qualifyTypeName(rawInterfaceName, currentNamespace, typesByQualifiedName) ?: rawInterfaceName
        val mappedType = mappedTypeByAbiName(resolvedInterfaceName) ?: return false
        return mappedType.readOnlyCollectionKind != null || mappedType.mutableCollectionKind != null
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
            "_${interfaceName.substringBefore('<').substringAfterLast('.').replaceFirstChar(Char::lowercase)}"
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
        WinRtTypeKind.Interface -> !type.isExclusiveTo ||
            type.methods.isNotEmpty() ||
            type.properties.isNotEmpty() ||
            type.events.isNotEmpty()
        WinRtTypeKind.RuntimeClass -> (!type.isStaticType && !type.isAttributeType) ||
            (type.isStaticType && type.activation.staticInterfaceNames.isNotEmpty())
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

private fun KotlinProjectionAbiTypeBinding.describeAbiKind(): String {
    mappedTypeByAbiKind(kind)?.let { mappedType ->
        return "${mappedType.descriptionName}(${typeArguments.joinToString(",") { it.resolvedTypeName }})"
    }
    return when (kind) {
        KotlinProjectionAbiValueKind.Enum -> "Enum(${resolvedTypeName})"
        KotlinProjectionAbiValueKind.ProjectedInterface -> "Interface(${resolvedTypeName})"
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass -> "RuntimeClass(${resolvedTypeName})"
        KotlinProjectionAbiValueKind.Struct -> "Struct(${resolvedTypeName})"
        KotlinProjectionAbiValueKind.Delegate -> "Delegate(${resolvedTypeName})"
        KotlinProjectionAbiValueKind.Array -> "Array(${typeArguments.joinToString(",") { it.describeAbiKind() }})"
        KotlinProjectionAbiValueKind.Object -> "Object(${resolvedTypeName})"
        KotlinProjectionAbiValueKind.Unsupported -> "Unsupported(${resolvedTypeName})"
        else -> kind.name
    }
}

private fun KotlinProjectionReadOnlyCollectionBinding.isRedundantReadOnlyCollectionBinding(
    mutableBindings: List<KotlinProjectionMutableCollectionBinding>,
): Boolean = when (kind) {
    KotlinProjectionReadOnlyCollectionKind.Iterable ->
        mutableBindings.any { binding ->
            when (binding.kind) {
                KotlinProjectionMutableCollectionKind.Vector ->
                    binding.elementBinding?.resolvedTypeName == elementBinding?.resolvedTypeName
                KotlinProjectionMutableCollectionKind.Map ->
                    elementBinding?.kind == KotlinProjectionAbiValueKind.MappedKeyValuePair &&
                        (
                            elementBinding.typeArguments.firstOrNull()?.resolvedTypeName to
                                elementBinding.typeArguments.getOrNull(1)?.resolvedTypeName
                            ) == (binding.keyBinding?.resolvedTypeName to binding.valueBinding?.resolvedTypeName)
            }
        }
    else -> false
}

private fun isMappedCollectionInterfaceName(interfaceName: String): Boolean {
    val rawInterfaceName = interfaceName.substringBefore('<').removeSuffix("?")
    val mappedType = mappedTypeByAbiName(rawInterfaceName) ?: return false
    return mappedType.readOnlyCollectionKind != null || mappedType.mutableCollectionKind != null
}

private fun KotlinProjectionMutableCollectionBinding.asReadOnlyEntryBinding(): KotlinProjectionReadOnlyCollectionBinding {
    require(kind == KotlinProjectionMutableCollectionKind.Map) {
        "Entry iterator projection requires a mutable map binding."
    }
    return KotlinProjectionReadOnlyCollectionBinding(
        kind = KotlinProjectionReadOnlyCollectionKind.MapView,
        ownerInterfaceQualifiedName = ownerInterfaceQualifiedName,
        ownerCachePropertyName = ownerCachePropertyName,
        slotInterfaceQualifiedName = slotInterfaceQualifiedName,
        delegatePropertyName = delegatePropertyName,
        keyBinding = keyBinding,
        valueBinding = valueBinding,
    )
}

private fun KotlinProjectionReadOnlyCollectionKind.abiName(): String = when (this) {
    KotlinProjectionReadOnlyCollectionKind.Iterable -> "IIterable"
    KotlinProjectionReadOnlyCollectionKind.VectorView -> "IVectorView"
    KotlinProjectionReadOnlyCollectionKind.MapView -> "IMapView"
}

private fun KotlinProjectionReadOnlyCollectionKind.ownerDelegatePropertyName(ownerInterface: String): String {
    val ownerSuffix = ownerInterface.collectionOwnerSuffix()
    return when (this) {
        KotlinProjectionReadOnlyCollectionKind.Iterable -> "__${ownerSuffix}IterableCollection"
        KotlinProjectionReadOnlyCollectionKind.VectorView -> "__${ownerSuffix}VectorViewCollection"
        KotlinProjectionReadOnlyCollectionKind.MapView -> "__${ownerSuffix}MapViewCollection"
    }
}

private fun KotlinProjectionReadOnlyCollectionKind.returnDelegatePropertyName(): String = when (this) {
    KotlinProjectionReadOnlyCollectionKind.Iterable -> "__mappedIterableReturn"
    KotlinProjectionReadOnlyCollectionKind.VectorView -> "__mappedVectorViewReturn"
    KotlinProjectionReadOnlyCollectionKind.MapView -> "__mappedMapViewReturn"
}

private fun KotlinProjectionMutableCollectionKind.abiName(): String = when (this) {
    KotlinProjectionMutableCollectionKind.Vector -> "IVector"
    KotlinProjectionMutableCollectionKind.Map -> "IMap"
}

private fun KotlinProjectionMutableCollectionKind.ownerDelegatePropertyName(ownerInterface: String): String {
    val ownerSuffix = ownerInterface.collectionOwnerSuffix()
    return when (this) {
        KotlinProjectionMutableCollectionKind.Vector -> "__${ownerSuffix}VectorCollection"
        KotlinProjectionMutableCollectionKind.Map -> "__${ownerSuffix}MapCollection"
    }
}

private fun String.collectionOwnerSuffix(): String =
    substringAfterLast('.')
        .filter(Char::isLetterOrDigit)
        .replaceFirstChar(Char::lowercase)

private fun KotlinProjectionMutableCollectionKind.returnDelegatePropertyName(): String = when (this) {
    KotlinProjectionMutableCollectionKind.Vector -> "__mappedVectorReturn"
    KotlinProjectionMutableCollectionKind.Map -> "__mappedMapReturn"
}

private fun createReadOnlyCollectionBindingPlan(
    collectionKind: KotlinProjectionReadOnlyCollectionKind,
    ownerInterfaceQualifiedName: String,
    ownerCachePropertyName: String,
    slotInterfaceQualifiedName: String,
    delegatePropertyName: String,
    typeArguments: List<KotlinProjectionAbiTypeBinding>,
    errorContext: String,
    requireSupportedBinding: Boolean,
    bindingLocationLabel: String = "",
): KotlinProjectionReadOnlyCollectionBinding? =
    when (collectionKind) {
        KotlinProjectionReadOnlyCollectionKind.Iterable,
        KotlinProjectionReadOnlyCollectionKind.VectorView -> {
            val elementBinding = typeArguments.singleOrNull() ?: return null
            val bindingTargetPrefix = if (bindingLocationLabel.isBlank()) "" else "$bindingLocationLabel "
            if (requireSupportedBinding) {
                require(elementBinding.isSupportedReadOnlyCollectionElementBinding()) {
                    "Generator read-only collection parity does not yet support ${collectionKind.abiName()} ${bindingTargetPrefix}element ${elementBinding.describeAbiKind()} on $errorContext."
                }
            } else if (!elementBinding.isSupportedReadOnlyCollectionElementBinding()) {
                return null
            }
            KotlinProjectionReadOnlyCollectionBinding(
                kind = collectionKind,
                ownerInterfaceQualifiedName = ownerInterfaceQualifiedName,
                ownerCachePropertyName = ownerCachePropertyName,
                slotInterfaceQualifiedName = slotInterfaceQualifiedName,
                delegatePropertyName = delegatePropertyName,
                elementBinding = elementBinding,
            )
        }

        KotlinProjectionReadOnlyCollectionKind.MapView -> {
            val keyBinding = typeArguments.getOrNull(0) ?: return null
            val valueBinding = typeArguments.getOrNull(1) ?: return null
            val bindingTargetPrefix = if (bindingLocationLabel.isBlank()) "" else "$bindingLocationLabel "
            if (requireSupportedBinding) {
                require(keyBinding.isSupportedReadOnlyCollectionKeyBinding()) {
                    "Generator read-only collection parity does not yet support ${collectionKind.abiName()} ${bindingTargetPrefix}key ${keyBinding.describeAbiKind()} on $errorContext."
                }
                require(valueBinding.isSupportedReadOnlyCollectionElementBinding()) {
                    "Generator read-only collection parity does not yet support ${collectionKind.abiName()} ${bindingTargetPrefix}value ${valueBinding.describeAbiKind()} on $errorContext."
                }
            } else if (!keyBinding.isSupportedReadOnlyCollectionKeyBinding() || !valueBinding.isSupportedReadOnlyCollectionElementBinding()) {
                return null
            }
            KotlinProjectionReadOnlyCollectionBinding(
                kind = collectionKind,
                ownerInterfaceQualifiedName = ownerInterfaceQualifiedName,
                ownerCachePropertyName = ownerCachePropertyName,
                slotInterfaceQualifiedName = slotInterfaceQualifiedName,
                delegatePropertyName = delegatePropertyName,
                keyBinding = keyBinding,
                valueBinding = valueBinding,
            )
        }
    }

private fun createMutableCollectionBindingPlan(
    collectionKind: KotlinProjectionMutableCollectionKind,
    ownerInterfaceQualifiedName: String,
    ownerCachePropertyName: String,
    slotInterfaceQualifiedName: String,
    delegatePropertyName: String,
    typeArguments: List<KotlinProjectionAbiTypeBinding>,
    errorContext: String,
    requireSupportedBinding: Boolean,
    bindingLocationLabel: String = "",
): KotlinProjectionMutableCollectionBinding? =
    when (collectionKind) {
        KotlinProjectionMutableCollectionKind.Vector -> {
            val elementBinding = typeArguments.singleOrNull() ?: return null
            val bindingTargetPrefix = if (bindingLocationLabel.isBlank()) "" else "$bindingLocationLabel "
            if (requireSupportedBinding) {
                require(elementBinding.isSupportedReadOnlyCollectionElementBinding()) {
                    "Generator mutable collection parity does not yet support ${collectionKind.abiName()} ${bindingTargetPrefix}element ${elementBinding.describeAbiKind()} on $errorContext."
                }
            } else if (!elementBinding.isSupportedReadOnlyCollectionElementBinding()) {
                return null
            }
            KotlinProjectionMutableCollectionBinding(
                kind = collectionKind,
                ownerInterfaceQualifiedName = ownerInterfaceQualifiedName,
                ownerCachePropertyName = ownerCachePropertyName,
                slotInterfaceQualifiedName = slotInterfaceQualifiedName,
                delegatePropertyName = delegatePropertyName,
                elementBinding = elementBinding,
            )
        }

        KotlinProjectionMutableCollectionKind.Map -> {
            val keyBinding = typeArguments.getOrNull(0) ?: return null
            val valueBinding = typeArguments.getOrNull(1) ?: return null
            val bindingTargetPrefix = if (bindingLocationLabel.isBlank()) "" else "$bindingLocationLabel "
            if (requireSupportedBinding) {
                require(keyBinding.isSupportedReadOnlyCollectionKeyBinding()) {
                    "Generator mutable collection parity does not yet support ${collectionKind.abiName()} ${bindingTargetPrefix}key ${keyBinding.describeAbiKind()} on $errorContext."
                }
                require(valueBinding.isSupportedReadOnlyCollectionElementBinding()) {
                    "Generator mutable collection parity does not yet support ${collectionKind.abiName()} ${bindingTargetPrefix}value ${valueBinding.describeAbiKind()} on $errorContext."
                }
            } else if (!keyBinding.isSupportedReadOnlyCollectionKeyBinding() || !valueBinding.isSupportedReadOnlyCollectionElementBinding()) {
                return null
            }
            KotlinProjectionMutableCollectionBinding(
                kind = collectionKind,
                ownerInterfaceQualifiedName = ownerInterfaceQualifiedName,
                ownerCachePropertyName = ownerCachePropertyName,
                slotInterfaceQualifiedName = slotInterfaceQualifiedName,
                delegatePropertyName = delegatePropertyName,
                keyBinding = keyBinding,
                valueBinding = valueBinding,
            )
        }
    }

private fun WinRtIntegralType.isSupportedProjectedEnumAbi(): Boolean =
    true

private fun KotlinProjectionAbiTypeBinding.isSupportedReadOnlyCollectionElementBinding(): Boolean = when (kind) {
    KotlinProjectionAbiValueKind.String,
    KotlinProjectionAbiValueKind.Boolean,
    KotlinProjectionAbiValueKind.Int8,
    KotlinProjectionAbiValueKind.UInt8,
    KotlinProjectionAbiValueKind.Int16,
    KotlinProjectionAbiValueKind.UInt16,
    KotlinProjectionAbiValueKind.Int32,
    KotlinProjectionAbiValueKind.UInt32,
    KotlinProjectionAbiValueKind.Int64,
    KotlinProjectionAbiValueKind.UInt64,
    KotlinProjectionAbiValueKind.Float,
    KotlinProjectionAbiValueKind.Double,
    KotlinProjectionAbiValueKind.Char16,
    KotlinProjectionAbiValueKind.GuidValue,
    KotlinProjectionAbiValueKind.ProjectedInterface,
    KotlinProjectionAbiValueKind.ProjectedRuntimeClass,
    KotlinProjectionAbiValueKind.Object,
    KotlinProjectionAbiValueKind.UnknownReference,
    KotlinProjectionAbiValueKind.InspectableReference,
    KotlinProjectionAbiValueKind.Reference,
    KotlinProjectionAbiValueKind.ReferenceArray,
    KotlinProjectionAbiValueKind.Struct,
    KotlinProjectionAbiValueKind.MappedIterable,
    KotlinProjectionAbiValueKind.MappedVector,
    KotlinProjectionAbiValueKind.MappedVectorView,
    KotlinProjectionAbiValueKind.MappedMap,
    KotlinProjectionAbiValueKind.MappedMapView -> true
    KotlinProjectionAbiValueKind.MappedKeyValuePair -> typeArguments.size == 2
    KotlinProjectionAbiValueKind.Enum -> enumUnderlyingType?.isSupportedProjectedEnumAbi() == true
    else -> false
}

private fun KotlinProjectionAbiTypeBinding.isSupportedReadOnlyCollectionKeyBinding(): Boolean =
    isSupportedReadOnlyCollectionElementBinding()

private fun requireDelegateInvokeMethod(type: WinRtTypeDefinition): WinRtMethodDefinition {
    val invokeMethods = type.methods.filter { it.name == "Invoke" }
    require(invokeMethods.size == 1 && type.methods.size == 1) {
        "Delegate(${type.qualifiedName}) must expose exactly one Invoke method in metadata before projection planning."
    }
    return invokeMethods.single()
}

private fun KotlinProjectionDelegateInvokeShape.isSupportedOutboundDelegateShape(): Boolean =
    interfaceId != null &&
        parameterBindings.all { it.typeBinding.isSupportedDelegateCallbackBinding() } &&
        returnBinding.isSupportedProjectedDelegateReturnBinding()

private fun KotlinProjectionDelegateInvokeShape.isSupportedProjectedDelegateShape(): Boolean =
    interfaceId != null &&
        parameterBindings.all { it.typeBinding.isSupportedProjectedDelegateBinding() } &&
        returnBinding.isSupportedProjectedDelegateReturnBinding()

private fun KotlinProjectionAbiTypeBinding.isSupportedDelegateCallbackBinding(): Boolean = when (kind) {
    KotlinProjectionAbiValueKind.String,
    KotlinProjectionAbiValueKind.Boolean,
    KotlinProjectionAbiValueKind.Int8,
    KotlinProjectionAbiValueKind.UInt8,
    KotlinProjectionAbiValueKind.Int16,
    KotlinProjectionAbiValueKind.UInt16,
    KotlinProjectionAbiValueKind.Int32,
    KotlinProjectionAbiValueKind.UInt32,
    KotlinProjectionAbiValueKind.Int64,
    KotlinProjectionAbiValueKind.UInt64,
    KotlinProjectionAbiValueKind.Float,
    KotlinProjectionAbiValueKind.Double,
    KotlinProjectionAbiValueKind.Char16,
    KotlinProjectionAbiValueKind.Object,
    KotlinProjectionAbiValueKind.ProjectedInterface,
    KotlinProjectionAbiValueKind.ProjectedRuntimeClass,
    KotlinProjectionAbiValueKind.MappedAsyncAction,
    KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress,
    KotlinProjectionAbiValueKind.MappedAsyncOperation,
    KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress,
    KotlinProjectionAbiValueKind.UnknownReference,
    KotlinProjectionAbiValueKind.InspectableReference -> true
    KotlinProjectionAbiValueKind.Enum -> enumUnderlyingType?.isSupportedProjectedEnumAbi() == true
    else -> false
}

private fun KotlinProjectionAbiTypeBinding.isSupportedProjectedDelegateBinding(): Boolean = when (kind) {
    KotlinProjectionAbiValueKind.String,
    KotlinProjectionAbiValueKind.Boolean,
    KotlinProjectionAbiValueKind.Int8,
    KotlinProjectionAbiValueKind.UInt8,
    KotlinProjectionAbiValueKind.Int16,
    KotlinProjectionAbiValueKind.UInt16,
    KotlinProjectionAbiValueKind.Int32,
    KotlinProjectionAbiValueKind.UInt32,
    KotlinProjectionAbiValueKind.Int64,
    KotlinProjectionAbiValueKind.UInt64,
    KotlinProjectionAbiValueKind.Float,
    KotlinProjectionAbiValueKind.Double,
    KotlinProjectionAbiValueKind.Char16,
    KotlinProjectionAbiValueKind.ProjectedInterface,
    KotlinProjectionAbiValueKind.ProjectedRuntimeClass,
    KotlinProjectionAbiValueKind.MappedAsyncAction,
    KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress,
    KotlinProjectionAbiValueKind.MappedAsyncOperation,
    KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress,
    KotlinProjectionAbiValueKind.UnknownReference,
    KotlinProjectionAbiValueKind.InspectableReference -> true
    KotlinProjectionAbiValueKind.Enum -> enumUnderlyingType?.isSupportedProjectedEnumAbi() == true
    else -> false
}

private fun KotlinProjectionAbiTypeBinding.isSupportedProjectedDelegateReturnBinding(): Boolean =
    isSupportedProjectedDelegateBinding() || kind == KotlinProjectionAbiValueKind.Unit

private fun KotlinProjectionAbiTypeBinding.isMappedCollectionBinding(): Boolean =
    mappedTypeByAbiKind(kind)?.let { mappedType ->
        (mappedType.readOnlyCollectionKind != null || mappedType.mutableCollectionKind != null) &&
            kind != KotlinProjectionAbiValueKind.MappedBindableIterable &&
            kind != KotlinProjectionAbiValueKind.MappedBindableVector &&
            kind != KotlinProjectionAbiValueKind.MappedBindableVectorView
    } == true

private fun KotlinProjectionAbiTypeBinding.isMappedBindableCollectionBinding(): Boolean =
    kind == KotlinProjectionAbiValueKind.MappedBindableIterable ||
        kind == KotlinProjectionAbiValueKind.MappedBindableVector ||
        kind == KotlinProjectionAbiValueKind.MappedBindableVectorView

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

private fun WinRtMethodDefinition.methodRowConstantName(methods: List<WinRtMethodDefinition>): String {
    val baseName = "${name.uppercase()}_METHOD_ROW_ID"
    if (methods.count { it.name == name } == 1) {
        return baseName
    }
    val rowId = methodRowId ?: return "${name.uppercase()}_${parameters.size}_METHOD_ROW_ID"
    return "${name.uppercase()}_${rowId}_METHOD_ROW_ID"
}

private fun String.methodSlotConstantName(): String =
    "${uppercase()}_SLOT"

private fun TypeSpec.Builder.addStringListProperty(
    name: String,
    values: List<String>,
) {
    addProperty(
        PropertySpec.builder(name, List::class.asClassName().parameterizedBy(String::class.asClassName()))
            .addModifiers(KModifier.INTERNAL)
            .initializer("%L", stringListCode(values))
            .build(),
    )
}

private fun stringListCode(values: List<String>): CodeBlock =
    CodeBlock.builder()
        .add("listOf(")
        .apply {
            values.forEachIndexed { index, value ->
                if (index > 0) {
                    add(", ")
                }
                add("%S", value)
            }
        }
        .add(")")
        .build()

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

    private fun renderInterfaceProxyMethod(method: WinRtMethodDefinition): FunSpec {
        val returnBinding = renderAbiTypeBinding(method.returnTypeName)
        val parameterBindings = method.parameters.map { parameter ->
            KotlinProjectionAbiParameterBinding(
                name = parameter.name,
                typeBinding = renderAbiTypeBinding(parameter.typeName),
            )
        }
        val callPlan = requireAbiCallPlan(
            bindingName = "${method.name.uppercase()}_SLOT",
            returnBinding = returnBinding,
            parameterBindings = parameterBindings,
        )
        val invocation = renderInlineAbiInvocation(
            invokeTargetExpression = "nativeObject",
            slotExpression = "Metadata.${method.name.uppercase()}_SLOT",
            callPlan = callPlan,
        ) ?: error("Generator interface proxy parity failed to emit ${method.name}")
        return FunSpec.builder(method.name)
            .addModifiers(KModifier.OVERRIDE)
            .addParameters(method.parameters.map { ParameterSpec.builder(it.name, resolveTypeName(it.typeName)).build() })
            .returns(resolveTypeName(method.returnTypeName))
            .addCode("%L\n", invocation)
            .build()
    }

    private fun canRenderInterfaceProxy(plan: KotlinTypeProjectionPlan): Boolean =
        plan.type.properties.none { !it.isStatic } &&
            plan.type.events.none { !it.isStatic } &&
            plan.type.methods.all { method ->
                runCatching {
                    buildAbiCallPlan(
                        returnBinding = renderAbiTypeBinding(method.returnTypeName),
                        parameterBindings = method.parameters.map { parameter ->
                            KotlinProjectionAbiParameterBinding(parameter.name, renderAbiTypeBinding(parameter.typeName))
                        },
                    ) != null
                }.getOrDefault(false)
            }

    private fun renderAbiTypeBinding(typeName: String): KotlinProjectionAbiTypeBinding {
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
            else -> mappedType?.abiValueKind ?: KotlinProjectionAbiValueKind.Unsupported
        }
        return KotlinProjectionAbiTypeBinding(
            kind = kind,
            typeName = trimmed,
            resolvedTypeName = rawTypeName,
            typeArguments = typeArguments,
        )
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
            .filterNot { implemented ->
                isMappedCollectionInterfaceName(implemented.interfaceName)
            }
            .forEach { implemented -> builder.addSuperinterface(resolveTypeName(implemented.interfaceName)) }
        plan.mutableCollectionBindings.forEach { binding ->
            builder.addProperty(renderMutableCollectionDelegateProperty(binding))
            builder.addSuperinterface(
                mutableCollectionProjectedType(binding),
                CodeBlock.of("%L", binding.delegatePropertyName),
            )
        }
        plan.readOnlyCollectionBindings.forEach { binding ->
            builder.addProperty(renderReadOnlyCollectionDelegateProperty(binding))
            builder.addSuperinterface(
                readOnlyCollectionProjectedType(binding),
                CodeBlock.of("%L", binding.delegatePropertyName),
            )
        }
        builder.addSuperinterface(IWINRT_OBJECT_CLASS_NAME)
        if (KotlinProjectionCompanionKind.ActivationFactory in plan.companionKinds) {
            builder.addFunction(
                FunSpec.constructorBuilder()
                    .callThisConstructor(CodeBlock.of("%T.activateInstance(Metadata.TYPE_NAME)", ACTIVATION_FACTORY_CLASS_NAME))
                    .build(),
            )
        }
        if (hasDefaultComposableFactoryConstructor(plan)) {
            builder.addFunction(
                FunSpec.constructorBuilder()
                    .callThisConstructor(CodeBlock.of("ComposableFactory.createInstance()"))
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
                renderStructMetadataCompanion(plan)?.let(::addType)
            }
            .build()

    private fun renderStructMetadataCompanion(plan: KotlinTypeProjectionPlan): TypeSpec? {
        val fields = plan.type.fields.filterNot { it.isStatic || it.isLiteral }
        val fieldSpecs = fields.map { field ->
            nativeStructFieldSpec(field, plan.type.namespace, plan.typesByQualifiedName) ?: return null
        }
        return TypeSpec.companionObjectBuilder("Metadata")
            .addProperty(
                PropertySpec.builder("layout", NATIVE_STRUCT_LAYOUT_CLASS_NAME)
                    .addModifiers(KModifier.INTERNAL)
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
                FunSpec.builder("fromAbi")
                    .addModifiers(KModifier.INTERNAL)
                    .addParameter("source", RAW_ADDRESS_CLASS_NAME)
                    .returns(resolveTypeName(plan.type.qualifiedName))
                    .addCode(
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
                            .build(),
                    )
                    .build(),
            )
            .addFunction(
                FunSpec.builder("copyTo")
                    .addModifiers(KModifier.INTERNAL)
                    .addParameter("value", resolveTypeName(plan.type.qualifiedName))
                    .addParameter("destination", RAW_ADDRESS_CLASS_NAME)
                    .addCode(
                        CodeBlock.builder()
                            .apply {
                                fields.forEach { field ->
                                    add("%L\n", nativeStructFieldWriteCode(field, "value", "destination"))
                                }
                            }
                            .build(),
                    )
                    .build(),
            )
            .build()
    }

    private fun nativeStructFieldSpec(
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

    private fun nativeStructScalarKind(typeName: String): String? = when (typeName) {
        "Byte",
        "SByte",
        "Int8",
        "UInt8" -> "INT8"
        "Short",
        "Int16",
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

    private fun nativeNestedStructFieldTypeName(
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

    private fun nativeStructFieldReadCode(field: WinRtFieldDefinition, sourceName: String): CodeBlock {
        val fieldName = field.name.replaceFirstChar(Char::lowercase)
        val slice = CodeBlock.of("layout.slice(%L, %S)", sourceName, fieldName)
        return when (field.typeName) {
            "Byte",
            "Int8" -> CodeBlock.of("%T.readInt8(%L)", PLATFORM_ABI_CLASS_NAME, slice)
            "SByte" -> CodeBlock.of("%T.readInt8(%L)", PLATFORM_ABI_CLASS_NAME, slice)
            "UInt8" -> CodeBlock.of("%T.readInt8(%L).toUByte()", PLATFORM_ABI_CLASS_NAME, slice)
            "Short",
            "Int16" -> CodeBlock.of("%T.readInt16(%L)", PLATFORM_ABI_CLASS_NAME, slice)
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

    private fun nativeStructFieldWriteCode(field: WinRtFieldDefinition, valueName: String, destinationName: String): CodeBlock {
        val fieldName = field.name.replaceFirstChar(Char::lowercase)
        val value = CodeBlock.of("%L.%L", valueName, fieldName)
        val slice = CodeBlock.of("layout.slice(%L, %S)", destinationName, fieldName)
        return when (field.typeName) {
            "Byte",
            "Int8",
            "SByte" -> CodeBlock.of("%T.writeInt8(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, value)
            "UInt8" -> CodeBlock.of("%T.writeInt8(%L, %L.toByte())", PLATFORM_ABI_CLASS_NAME, slice, value)
            "Short",
            "Int16" -> CodeBlock.of("%T.writeInt16(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, value)
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

    private fun readOnlyCollectionProjectedType(
        binding: KotlinProjectionReadOnlyCollectionBinding,
    ): TypeName = when (binding.kind) {
        KotlinProjectionReadOnlyCollectionKind.Iterable ->
            Iterable::class.asClassName().parameterizedBy(resolveTypeName(requireNotNull(binding.elementBinding).typeName))
        KotlinProjectionReadOnlyCollectionKind.VectorView ->
            List::class.asClassName().parameterizedBy(resolveTypeName(requireNotNull(binding.elementBinding).typeName))
        KotlinProjectionReadOnlyCollectionKind.MapView ->
            Map::class.asClassName().parameterizedBy(
                resolveTypeName(requireNotNull(binding.keyBinding).typeName),
                resolveTypeName(requireNotNull(binding.valueBinding).typeName),
            )
    }

    private fun mutableCollectionProjectedType(
        binding: KotlinProjectionMutableCollectionBinding,
    ): TypeName = when (binding.kind) {
        KotlinProjectionMutableCollectionKind.Vector ->
            MUTABLE_LIST_CLASS_NAME.parameterizedBy(resolveTypeName(requireNotNull(binding.elementBinding).typeName))
        KotlinProjectionMutableCollectionKind.Map ->
            MUTABLE_MAP_CLASS_NAME.parameterizedBy(
                resolveTypeName(requireNotNull(binding.keyBinding).typeName),
                resolveTypeName(requireNotNull(binding.valueBinding).typeName),
            )
    }

    private fun renderMutableCollectionDelegateProperty(
        binding: KotlinProjectionMutableCollectionBinding,
    ): PropertySpec =
        PropertySpec.builder(binding.delegatePropertyName, mutableCollectionProjectedType(binding))
            .addModifiers(KModifier.PRIVATE)
            .delegate(
                CodeBlock.of(
                    "lazy(%T.PUBLICATION) {\n%L}\n",
                    LAZY_THREAD_SAFETY_MODE_CLASS_NAME,
                    renderMutableCollectionDelegateInitializer(binding),
                ),
            )
            .build()

    private fun renderMutableCollectionDelegateInitializer(
        binding: KotlinProjectionMutableCollectionBinding,
    ): CodeBlock = when (binding.kind) {
        KotlinProjectionMutableCollectionKind.Vector -> renderVectorCollectionDelegateInitializer(binding)
        KotlinProjectionMutableCollectionKind.Map -> renderMapCollectionDelegateInitializer(binding)
    }

    private fun renderVectorCollectionDelegateInitializer(
        binding: KotlinProjectionMutableCollectionBinding,
    ): CodeBlock {
        val elementBinding = requireNotNull(binding.elementBinding)
        val elementType = resolveTypeName(elementBinding.typeName)
        val projectedType = mutableCollectionProjectedType(binding)
        val abstractMutableListType = ABSTRACT_MUTABLE_LIST_CLASS_NAME.parameterizedBy(elementType)
        return CodeBlock.of(
            """
            object : %T(), %T, %T {
                override val nativeObject: %T
                    get() = %L

                override val size: Int
                    get() = __readSize().toInt()

                override fun get(index: Int): %T {
                    require(index >= 0) { %S }
                    %L
                }

                override fun set(index: Int, element: %T): %T {
                    require(index >= 0) { %S }
                    val __previous = get(index)
                    %L
                    return __previous
                }

                override fun add(index: Int, element: %T) {
                    require(index >= 0) { %S }
                    %L
                }

                override fun add(element: %T): Boolean {
                    %L
                    return true
                }

                override fun removeAt(index: Int): %T {
                    require(index >= 0) { %S }
                    val __previous = get(index)
                    %L
                    return __previous
                }

                override fun clear() {
                    %L
                }

                private fun __readSize(): %T {
                    %L
                }
            }
            """.trimIndent() + "\n",
            abstractMutableListType,
            projectedType,
            IWINRT_OBJECT_CLASS_NAME,
            COM_OBJECT_REFERENCE_CLASS_NAME,
            binding.ownerCachePropertyName,
            elementType,
            "index must be non-negative.",
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "GETAT_SLOT",
                returnBinding = elementBinding,
                parameterBindings = listOf(
                    KotlinProjectionAbiParameterBinding(
                        name = "index",
                        typeBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.UInt32, "UInt"),
                    ),
                ),
            ).toString(),
            elementType,
            elementType,
            "index must be non-negative.",
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "SETAT_SLOT",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                parameterBindings = listOf(
                    KotlinProjectionAbiParameterBinding(
                        name = "index",
                        typeBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.UInt32, "UInt"),
                    ),
                    KotlinProjectionAbiParameterBinding("element", elementBinding),
                ),
            ).toString(),
            elementType,
            "index must be non-negative.",
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "INSERTAT_SLOT",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                parameterBindings = listOf(
                    KotlinProjectionAbiParameterBinding(
                        name = "index",
                        typeBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.UInt32, "UInt"),
                    ),
                    KotlinProjectionAbiParameterBinding("element", elementBinding),
                ),
            ).toString(),
            elementType,
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "APPEND_SLOT",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                parameterBindings = listOf(KotlinProjectionAbiParameterBinding("element", elementBinding)),
            ).toString(),
            elementType,
            "index must be non-negative.",
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "REMOVEAT_SLOT",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                parameterBindings = listOf(
                    KotlinProjectionAbiParameterBinding(
                        name = "index",
                        typeBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.UInt32, "UInt"),
                    ),
                ),
            ).toString(),
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "CLEAR_SLOT",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
            ).toString(),
            UInt::class.asClassName(),
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "SIZE_GETTER_SLOT",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.UInt32, "UInt"),
            ).toString(),
        )
    }

    private fun renderMapCollectionDelegateInitializer(
        binding: KotlinProjectionMutableCollectionBinding,
    ): CodeBlock {
        val keyBinding = requireNotNull(binding.keyBinding)
        val valueBinding = requireNotNull(binding.valueBinding)
        val keyType = resolveTypeName(keyBinding.typeName)
        val valueType = resolveTypeName(valueBinding.typeName)
        val projectedType = mutableCollectionProjectedType(binding)
        val abstractMutableMapType = ABSTRACT_MUTABLE_MAP_CLASS_NAME.parameterizedBy(keyType, valueType)
        val entryType = MUTABLE_MAP_CLASS_NAME.nestedClass("MutableEntry").parameterizedBy(keyType, valueType)
        return CodeBlock.of(
            """
            object : %T(), %T, %T {
                override val nativeObject: %T
                    get() = %L

                override val entries: MutableSet<%T>
                    get() {
                        val __map = this
                        return object : %T<%T>() {
                            override val size: Int
                                get() = __map.size

                            override fun add(element: %T): Boolean {
                                val __replaced = __map.containsKey(element.key)
                                __map.put(element.key, element.value)
                                return !__replaced
                            }

                            override fun iterator(): MutableIterator<%T> {
                                val __iterator = __createEntryIterator()
                                return object : MutableIterator<%T> {
                                    private var __lastReturned: %T? = null

                                    override fun hasNext(): Boolean = __iterator.hasNext()

                                    override fun next(): %T {
                                        val __entry = __iterator.next()
                                        val __mutableEntry = object : %T {
                                            override val key: %T = __entry.key
                                            private var __currentValue: %T = __entry.value
                                            override val value: %T
                                                get() = __currentValue

                                            override fun setValue(newValue: %T): %T {
                                                val __previous = __currentValue
                                                __map.put(key, newValue)
                                                __currentValue = newValue
                                                return __previous
                                            }
                                        }
                                        __lastReturned = __mutableEntry
                                        return __mutableEntry
                                    }

                                    override fun remove() {
                                        val __entry = __lastReturned ?: throw %T(%S)
                                        __map.remove(__entry.key)
                                        __lastReturned = null
                                    }
                                }
                            }
                        }
                    }

                override val size: Int
                    get() = __readSize().toInt()

                override fun containsKey(key: %T): Boolean {
                    %L
                }

                override fun get(key: %T): %T? {
                    return if (containsKey(key)) {
                        %L
                    } else {
                        null
                    }
                }

                override fun put(key: %T, value: %T): %T? {
                    val __previous = get(key)
                    %L
                    return __previous
                }

                override fun remove(key: %T): %T? {
                    val __previous = get(key) ?: return null
                    %L
                    return __previous
                }

                override fun clear() {
                    %L
                }

                private fun __createEntryIterator(): Iterator<Map.Entry<%T, %T>> {
                    %L
                }

                private fun __readSize(): %T {
                    %L
                }
            }
            """.trimIndent() + "\n",
            abstractMutableMapType,
            projectedType,
            IWINRT_OBJECT_CLASS_NAME,
            COM_OBJECT_REFERENCE_CLASS_NAME,
            binding.ownerCachePropertyName,
            entryType,
            ABSTRACT_MUTABLE_SET_CLASS_NAME,
            entryType,
            entryType,
            entryType,
            entryType,
            entryType,
            entryType,
            MUTABLE_MAP_CLASS_NAME.nestedClass("MutableEntry").parameterizedBy(keyType, valueType),
            keyType,
            valueType,
            valueType,
            valueType,
            valueType,
            ILLEGAL_STATE_EXCEPTION_CLASS_NAME,
            "remove() before next() is not allowed for mutable map entry iteration.",
            keyType,
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "HASKEY_SLOT",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Boolean, "Boolean"),
                parameterBindings = listOf(KotlinProjectionAbiParameterBinding("key", keyBinding)),
            ).toString(),
            keyType,
            valueType,
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "LOOKUP_SLOT",
                returnBinding = valueBinding,
                parameterBindings = listOf(KotlinProjectionAbiParameterBinding("key", keyBinding)),
            ).toString(),
            keyType,
            valueType,
            valueType,
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "INSERT_SLOT",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Boolean, "Boolean"),
                parameterBindings = listOf(
                    KotlinProjectionAbiParameterBinding("key", keyBinding),
                    KotlinProjectionAbiParameterBinding("value", valueBinding),
                ),
            ).toString(),
            keyType,
            valueType,
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "REMOVE_SLOT",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                parameterBindings = listOf(KotlinProjectionAbiParameterBinding("key", keyBinding)),
            ).toString(),
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "CLEAR_SLOT",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
            ).toString(),
            keyType,
            valueType,
            renderMappedIteratorCreationCode(
                ownerExpression = binding.ownerCachePropertyName,
                iterableSlotInterfaceQualifiedName = "Windows.Foundation.Collections.IIterable",
                elementBinding = null,
                entryBinding = binding.asReadOnlyEntryBinding(),
            ),
            UInt::class.asClassName(),
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "SIZE_GETTER_SLOT",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.UInt32, "UInt"),
            ).toString(),
        )
    }

    private fun renderReadOnlyCollectionDelegateProperty(
        binding: KotlinProjectionReadOnlyCollectionBinding,
    ): PropertySpec =
        PropertySpec.builder(binding.delegatePropertyName, readOnlyCollectionProjectedType(binding))
            .addModifiers(KModifier.PRIVATE)
            .delegate(
                CodeBlock.of(
                    "lazy(%T.PUBLICATION) {\n%L}\n",
                    LAZY_THREAD_SAFETY_MODE_CLASS_NAME,
                    renderReadOnlyCollectionDelegateInitializer(binding),
                ),
            )
            .build()

    private fun renderReadOnlyCollectionDelegateInitializer(
        binding: KotlinProjectionReadOnlyCollectionBinding,
    ): CodeBlock = when (binding.kind) {
        KotlinProjectionReadOnlyCollectionKind.Iterable -> renderIterableCollectionDelegateInitializer(binding)
        KotlinProjectionReadOnlyCollectionKind.VectorView -> renderVectorViewCollectionDelegateInitializer(binding)
        KotlinProjectionReadOnlyCollectionKind.MapView -> renderMapViewCollectionDelegateInitializer(binding)
    }

    private fun renderIterableCollectionDelegateInitializer(
        binding: KotlinProjectionReadOnlyCollectionBinding,
    ): CodeBlock {
        val elementBinding = requireNotNull(binding.elementBinding)
        val elementType = resolveTypeName(elementBinding.typeName)
        val projectedType = readOnlyCollectionProjectedType(binding)
        val iteratorType = Iterator::class.asClassName().parameterizedBy(elementType)
        return CodeBlock.of(
            """
            object : %T, %T {
                override val nativeObject: %T
                    get() = %L

                override fun iterator(): %T {
                    val __owner = %L
                    %L
                }
            }
            """.trimIndent() + "\n",
            projectedType,
            IWINRT_OBJECT_CLASS_NAME,
            COM_OBJECT_REFERENCE_CLASS_NAME,
            binding.ownerCachePropertyName,
            iteratorType,
            binding.ownerCachePropertyName,
            renderMappedIteratorCreationCode(
                ownerExpression = "__owner",
                iterableSlotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                elementBinding = elementBinding,
                entryBinding = null,
            ),
        )
    }

    private fun renderVectorViewCollectionDelegateInitializer(
        binding: KotlinProjectionReadOnlyCollectionBinding,
    ): CodeBlock {
        val elementBinding = requireNotNull(binding.elementBinding)
        val elementType = resolveTypeName(elementBinding.typeName)
        val projectedType = readOnlyCollectionProjectedType(binding)
        val abstractListType = ABSTRACT_LIST_CLASS_NAME.parameterizedBy(elementType)
        return CodeBlock.of(
            """
            object : %T(), %T, %T {
                override val nativeObject: %T
                    get() = %L

                override val size: Int
                    get() = __readSize().toInt()

                override fun get(index: Int): %T {
                    require(index >= 0) { %S }
                    %L
                }

                private fun __readSize(): %T {
                    %L
                }
            }
            """.trimIndent() + "\n",
            abstractListType,
            projectedType,
            IWINRT_OBJECT_CLASS_NAME,
            COM_OBJECT_REFERENCE_CLASS_NAME,
            binding.ownerCachePropertyName,
            elementType,
            "index must be non-negative.",
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "GETAT_SLOT",
                returnBinding = elementBinding,
                parameterBindings = listOf(
                    KotlinProjectionAbiParameterBinding(
                        name = "index",
                        typeBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.UInt32, "UInt"),
                    ),
                ),
            ).toString(),
            UInt::class.asClassName(),
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "SIZE_GETTER_SLOT",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.UInt32, "UInt"),
            ).toString(),
        )
    }

    private fun renderMapViewCollectionDelegateInitializer(
        binding: KotlinProjectionReadOnlyCollectionBinding,
    ): CodeBlock {
        val keyBinding = requireNotNull(binding.keyBinding)
        val valueBinding = requireNotNull(binding.valueBinding)
        val keyType = resolveTypeName(keyBinding.typeName)
        val valueType = resolveTypeName(valueBinding.typeName)
        val projectedType = readOnlyCollectionProjectedType(binding)
        val abstractMapType = ABSTRACT_MAP_CLASS_NAME.parameterizedBy(keyType, valueType)
        val entryType = Map.Entry::class.asClassName().parameterizedBy(keyType, valueType)
        return CodeBlock.of(
            """
            object : %T(), %T, %T {
                override val nativeObject: %T
                    get() = %L

                override val entries: Set<%T>
                    get() {
                        val __entries = linkedSetOf<%T>()
                        val __iterator = __createEntryIterator()
                        while (__iterator.hasNext()) {
                            __entries += __iterator.next()
                        }
                        return __entries
                    }

                override fun containsKey(key: %T): Boolean {
                    %L
                }

                override fun get(key: %T): %T? {
                    return if (containsKey(key)) {
                        %L
                    } else {
                        null
                    }
                }

                private fun __createEntryIterator(): %T {
                    %L
                }
            }
            """.trimIndent() + "\n",
            abstractMapType,
            projectedType,
            IWINRT_OBJECT_CLASS_NAME,
            COM_OBJECT_REFERENCE_CLASS_NAME,
            binding.ownerCachePropertyName,
            entryType,
            entryType,
            keyType,
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "HASKEY_SLOT",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Boolean, "Boolean"),
                parameterBindings = listOf(KotlinProjectionAbiParameterBinding("key", keyBinding)),
            ).toString(),
            keyType,
            valueType,
            renderCollectionInvocation(
                invokeTargetExpression = binding.ownerCachePropertyName,
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                slotConstantName = "LOOKUP_SLOT",
                returnBinding = valueBinding,
                parameterBindings = listOf(KotlinProjectionAbiParameterBinding("key", keyBinding)),
            ).toString(),
            Iterator::class.asClassName().parameterizedBy(entryType),
            renderMappedIteratorCreationCode(
                ownerExpression = binding.ownerCachePropertyName,
                iterableSlotInterfaceQualifiedName = "Windows.Foundation.Collections.IIterable",
                elementBinding = null,
                entryBinding = binding,
            ),
        )
    }

    private fun renderMappedIteratorCreationCode(
        ownerExpression: String,
        iterableSlotInterfaceQualifiedName: String,
        elementBinding: KotlinProjectionAbiTypeBinding?,
        entryBinding: KotlinProjectionReadOnlyCollectionBinding?,
    ): CodeBlock {
        val effectiveEntryBinding = entryBinding ?: elementBinding
            ?.takeIf { it.kind == KotlinProjectionAbiValueKind.MappedKeyValuePair && it.typeArguments.size == 2 }
            ?.let {
                KotlinProjectionReadOnlyCollectionBinding(
                    kind = KotlinProjectionReadOnlyCollectionKind.MapView,
                    ownerInterfaceQualifiedName = it.resolvedTypeName,
                    ownerCachePropertyName = "",
                    slotInterfaceQualifiedName = it.resolvedTypeName,
                    delegatePropertyName = "",
                    keyBinding = it.typeArguments[0],
                    valueBinding = it.typeArguments[1],
                )
            }
        val returnType = when {
            effectiveEntryBinding != null -> Map.Entry::class.asClassName().parameterizedBy(
                resolveTypeName(requireNotNull(effectiveEntryBinding.keyBinding).typeName),
                resolveTypeName(requireNotNull(effectiveEntryBinding.valueBinding).typeName),
            )
            else -> resolveTypeName(requireNotNull(elementBinding).typeName)
        }
        return CodeBlock.of(
            """
            fun __createIteratorReference(): %T {
                %L
            }
            val __iterator = __createIteratorReference()
            return object : %T {
                private var __hasNext = __iteratorHasCurrent(__iterator)

                override fun hasNext(): Boolean = __hasNext

                override fun next(): %T {
                    if (!__hasNext) {
                        throw %T()
                    }
                    val __current = __readCurrent(__iterator)
                    __hasNext = __iteratorMoveNext(__iterator)
                    return __current
                }

                private fun __readCurrent(__iteratorRef: %T): %T {
                    %L
                }

                private fun __iteratorHasCurrent(__iteratorRef: %T): Boolean {
                    %L
                }

                private fun __iteratorMoveNext(__iteratorRef: %T): Boolean {
                    %L
                }
            }
            """.trimIndent() + "\n",
            IUNKNOWN_REFERENCE_CLASS_NAME,
            renderCollectionInvocation(
                invokeTargetExpression = ownerExpression,
                slotInterfaceQualifiedName = iterableSlotInterfaceQualifiedName,
                slotConstantName = "FIRST_SLOT",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.UnknownReference, IUNKNOWN_REFERENCE_CLASS_NAME.simpleName),
            ).toString(),
            Iterator::class.asClassName().parameterizedBy(returnType),
            returnType,
            NO_SUCH_ELEMENT_EXCEPTION_CLASS_NAME,
            IUNKNOWN_REFERENCE_CLASS_NAME,
            returnType,
            renderMappedIteratorCurrentCode(elementBinding, effectiveEntryBinding, "__iteratorRef").toString(),
            IUNKNOWN_REFERENCE_CLASS_NAME,
            renderCollectionInvocation(
                invokeTargetExpression = "__iteratorRef",
                slotInterfaceQualifiedName = "Windows.Foundation.Collections.IIterator",
                slotConstantName = "HASCURRENT_GETTER_SLOT",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Boolean, "Boolean"),
            ).toString(),
            IUNKNOWN_REFERENCE_CLASS_NAME,
            renderCollectionInvocation(
                invokeTargetExpression = "__iteratorRef",
                slotInterfaceQualifiedName = "Windows.Foundation.Collections.IIterator",
                slotConstantName = "MOVENEXT_SLOT",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Boolean, "Boolean"),
            ).toString(),
        )
    }

    private fun renderMappedIteratorCurrentCode(
        elementBinding: KotlinProjectionAbiTypeBinding?,
        entryBinding: KotlinProjectionReadOnlyCollectionBinding?,
        iteratorExpression: String,
    ): CodeBlock {
        if (entryBinding != null) {
            val keyBinding = requireNotNull(entryBinding.keyBinding)
            val valueBinding = requireNotNull(entryBinding.valueBinding)
            val keyType = resolveTypeName(keyBinding.typeName)
            val valueType = resolveTypeName(valueBinding.typeName)
            return CodeBlock.of(
                """
                fun __readPairReference(): %T {
                    %L
                }
                fun __readKey(__pairRef: %T): %T {
                    %L
                }
                fun __readValue(__pairRef: %T): %T {
                    %L
                }
                val __pair = __readPairReference()
                val __key = __readKey(__pair)
                val __value = __readValue(__pair)
                return object : %T {
                    override val key: %T = __key
                    override val value: %T = __value
                }
                """.trimIndent(),
                IUNKNOWN_REFERENCE_CLASS_NAME,
                renderCollectionInvocation(
                    invokeTargetExpression = iteratorExpression,
                    slotInterfaceQualifiedName = "Windows.Foundation.Collections.IIterator",
                    slotConstantName = "CURRENT_GETTER_SLOT",
                    returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.UnknownReference, IUNKNOWN_REFERENCE_CLASS_NAME.simpleName),
                ).toString(),
                IUNKNOWN_REFERENCE_CLASS_NAME,
                keyType,
                renderCollectionInvocation(
                    invokeTargetExpression = "__pairRef",
                    slotInterfaceQualifiedName = "Windows.Foundation.Collections.IKeyValuePair",
                    slotConstantName = "KEY_GETTER_SLOT",
                    returnBinding = keyBinding,
                ).toString(),
                IUNKNOWN_REFERENCE_CLASS_NAME,
                valueType,
                renderCollectionInvocation(
                    invokeTargetExpression = "__pairRef",
                    slotInterfaceQualifiedName = "Windows.Foundation.Collections.IKeyValuePair",
                    slotConstantName = "VALUE_GETTER_SLOT",
                    returnBinding = valueBinding,
                ).toString(),
                Map.Entry::class.asClassName().parameterizedBy(keyType, valueType),
                keyType,
                valueType,
            )
        }
        return renderCollectionInvocation(
            invokeTargetExpression = iteratorExpression,
            slotInterfaceQualifiedName = "Windows.Foundation.Collections.IIterator",
            slotConstantName = "CURRENT_GETTER_SLOT",
            returnBinding = requireNotNull(elementBinding),
        ).toString().let { CodeBlock.of("%L", it) }
    }

    private fun renderCollectionInvocation(
        invokeTargetExpression: String,
        slotInterfaceQualifiedName: String,
        slotConstantName: String,
        returnBinding: KotlinProjectionAbiTypeBinding,
        parameterBindings: List<KotlinProjectionAbiParameterBinding> = emptyList(),
    ): CodeBlock {
        val callPlan = requireAbiCallPlan(
            bindingName = "${slotInterfaceQualifiedName.substringAfterLast('.')}_$slotConstantName",
            returnBinding = returnBinding,
            parameterBindings = parameterBindings,
        )
        return renderInlineAbiInvocation(
            invokeTargetExpression = invokeTargetExpression,
            slotExpression = CodeBlock.of("%T.Metadata.%L", projectionClassName(slotInterfaceQualifiedName), slotConstantName),
            callPlan = callPlan,
        ) ?: error("Generator read-only collection parity failed to emit $slotInterfaceQualifiedName.$slotConstantName")
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

    private fun renderStubMethod(method: WinRtMethodDefinition, override: Boolean = false): FunSpec {
        val builder = FunSpec.builder(method.name)
            .addParameters(method.parameters.map { ParameterSpec.builder(it.name, resolveTypeName(it.typeName)).build() })
            .returns(resolveTypeName(method.returnTypeName))
            .addCode("return error(%S)\n", "Not yet bound to winrt-runtime")
        if (override) {
            builder.addModifiers(KModifier.OVERRIDE)
        }
        return builder.build()
    }

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

    private fun renderStubProperty(property: WinRtPropertyDefinition, override: Boolean = false): PropertySpec {
        val builder = PropertySpec.builder(
            property.name.replaceFirstChar(Char::lowercase),
            resolveTypeName(property.typeName),
        ).mutable(!property.isReadOnly)
        if (override) {
            builder.addModifiers(KModifier.OVERRIDE)
        }
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
            .addModifiers(KModifier.OVERRIDE)
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
            .addModifiers(KModifier.OVERRIDE)
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
            marshalerPlanDescriptor = binding.marshalerPlanDescriptor,
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
            marshalerPlanDescriptor = binding.marshalerPlanDescriptor,
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
        buildAbiCallPlan(binding.returnBinding, binding.parameterBindings, binding.marshalerPlanDescriptor)

    private fun buildAbiCallPlan(
        returnBinding: KotlinProjectionAbiTypeBinding,
        parameterBindings: List<KotlinProjectionAbiParameterBinding>,
        marshalerPlanDescriptor: WinRtAbiMarshalerPlanDescriptor? = null,
    ): KotlinProjectionAbiCallPlan? {
        val parameterMarshalers = parameterBindings.map { parameterBinding ->
            val slot = marshalerPlanDescriptor?.marshalers?.firstOrNull { !it.isReturn && it.name == parameterBinding.name }
            buildAbiParameterMarshaler(parameterBinding, slot) ?: return null
        }
        val returnMarshaler = when (returnBinding.kind) {
            KotlinProjectionAbiValueKind.Unit -> null
            else -> buildAbiReturnMarshaler(
                returnBinding,
                marshalerPlanDescriptor?.marshalers?.firstOrNull { it.isReturn },
            ) ?: return null
        }
        return KotlinProjectionAbiCallPlan(
            parameterMarshalers = parameterMarshalers,
            returnMarshaler = returnMarshaler,
            descriptor = marshalerPlanDescriptor,
        )
    }

    private fun requireAbiCallPlan(
        bindingName: String,
        returnBinding: KotlinProjectionAbiTypeBinding,
        parameterBindings: List<KotlinProjectionAbiParameterBinding>,
        marshalerPlanDescriptor: WinRtAbiMarshalerPlanDescriptor? = null,
    ): KotlinProjectionAbiCallPlan {
        return requireNotNull(buildAbiCallPlan(returnBinding, parameterBindings, marshalerPlanDescriptor)) {
            val unsupportedKinds = buildList {
                if (
                    returnBinding.kind != KotlinProjectionAbiValueKind.Unit &&
                    buildAbiReturnMarshaler(returnBinding, marshalerPlanDescriptor?.marshalers?.firstOrNull { it.isReturn }) == null
                ) {
                    add(returnBinding.describeAbiKind())
                }
                addAll(
                    parameterBindings
                        .filter { parameterBinding ->
                            val slot = marshalerPlanDescriptor?.marshalers?.firstOrNull { !it.isReturn && it.name == parameterBinding.name }
                            buildAbiParameterMarshaler(parameterBinding, slot) == null
                        }
                        .map { parameterBinding -> parameterBinding.typeBinding.describeAbiKind() },
                )
            }
                .distinct()
                .joinToString(", ")
            "Generator ABI marshaler parity does not yet support $bindingName for $unsupportedKinds."
        }
    }

    private fun buildAbiParameterMarshaler(
        parameterBinding: KotlinProjectionAbiParameterBinding,
        descriptor: WinRtAbiMarshalerSlotDescriptor? = null,
    ): KotlinProjectionAbiMarshalerPlan? {
        val parameterName = parameterBinding.name
        val abiLocalName = "__${parameterName}Abi"
        return when (parameterBinding.typeBinding.kind) {
            KotlinProjectionAbiValueKind.String -> KotlinProjectionAbiMarshalerPlan(
                name = parameterName,
                typeBinding = parameterBinding.typeBinding,
                isReturn = false,
                abiArgumentExpression = CodeBlock.of("%L.handle", abiLocalName),
                scopeOpeners = listOf(
                    CodeBlock.of("%T.create(%L).use { %L ->", HSTRING_CLASS_NAME, parameterName, abiLocalName),
                ),
            )
            KotlinProjectionAbiValueKind.Boolean -> KotlinProjectionAbiMarshalerPlan(
                name = parameterName,
                typeBinding = parameterBinding.typeBinding,
                isReturn = false,
                abiArgumentExpression = CodeBlock.of("if (%L) 1 else 0", parameterName),
            )
            KotlinProjectionAbiValueKind.Int8 -> KotlinProjectionAbiMarshalerPlan(
                name = parameterName,
                typeBinding = parameterBinding.typeBinding,
                isReturn = false,
                abiArgumentExpression = CodeBlock.of("%L", parameterName),
            )
            KotlinProjectionAbiValueKind.UInt8 -> KotlinProjectionAbiMarshalerPlan(
                name = parameterName,
                typeBinding = parameterBinding.typeBinding,
                isReturn = false,
                abiArgumentExpression = CodeBlock.of("%L.toByte()", parameterName),
            )
            KotlinProjectionAbiValueKind.Int16 -> KotlinProjectionAbiMarshalerPlan(
                name = parameterName,
                typeBinding = parameterBinding.typeBinding,
                isReturn = false,
                abiArgumentExpression = CodeBlock.of("%L", parameterName),
            )
            KotlinProjectionAbiValueKind.UInt16 -> KotlinProjectionAbiMarshalerPlan(
                name = parameterName,
                typeBinding = parameterBinding.typeBinding,
                isReturn = false,
                abiArgumentExpression = CodeBlock.of("%L.toShort()", parameterName),
            )
            KotlinProjectionAbiValueKind.Double -> KotlinProjectionAbiMarshalerPlan(
                name = parameterName,
                typeBinding = parameterBinding.typeBinding,
                isReturn = false,
                abiArgumentExpression = CodeBlock.of("%L", parameterName),
            )
            KotlinProjectionAbiValueKind.UInt32 -> KotlinProjectionAbiMarshalerPlan(
                name = parameterName,
                typeBinding = parameterBinding.typeBinding,
                isReturn = false,
                abiArgumentExpression = CodeBlock.of("%L.toInt()", parameterName),
            )
            KotlinProjectionAbiValueKind.Int32 -> KotlinProjectionAbiMarshalerPlan(
                name = parameterName,
                typeBinding = parameterBinding.typeBinding,
                isReturn = false,
                abiArgumentExpression = CodeBlock.of("%L", parameterName),
            )
            KotlinProjectionAbiValueKind.Int64 -> KotlinProjectionAbiMarshalerPlan(
                name = parameterName,
                typeBinding = parameterBinding.typeBinding,
                isReturn = false,
                abiArgumentExpression = CodeBlock.of("%L", parameterName),
            )
            KotlinProjectionAbiValueKind.UInt64 -> KotlinProjectionAbiMarshalerPlan(
                name = parameterName,
                typeBinding = parameterBinding.typeBinding,
                isReturn = false,
                abiArgumentExpression = CodeBlock.of("%L.toLong()", parameterName),
            )
            KotlinProjectionAbiValueKind.Float -> KotlinProjectionAbiMarshalerPlan(
                name = parameterName,
                typeBinding = parameterBinding.typeBinding,
                isReturn = false,
                abiArgumentExpression = CodeBlock.of("%L", parameterName),
            )
            KotlinProjectionAbiValueKind.Char16 -> KotlinProjectionAbiMarshalerPlan(
                name = parameterName,
                typeBinding = parameterBinding.typeBinding,
                isReturn = false,
                abiArgumentExpression = CodeBlock.of("%L", parameterName),
            )
            KotlinProjectionAbiValueKind.GuidValue -> {
                val scopeName = "__${parameterName}GuidScope"
                val abiLocalName = "__${parameterName}Abi"
                KotlinProjectionAbiMarshalerPlan(
                    name = parameterName,
                    typeBinding = parameterBinding.typeBinding,
                    isReturn = false,
                    abiArgumentExpression = CodeBlock.of("%L", abiLocalName),
                    scopeOpeners = listOf(
                        CodeBlock.of(
                            "%T.confinedScope().use { %L ->\nval %L = %T.allocateBytes(%L, %T.BYTE_SIZE.toLong())\n%L.writeTo(%L)",
                            PLATFORM_ABI_CLASS_NAME,
                            scopeName,
                            abiLocalName,
                            PLATFORM_ABI_CLASS_NAME,
                            scopeName,
                            GUID_CLASS_NAME,
                            parameterName,
                            abiLocalName,
                        ),
                    ),
                )
            }
            KotlinProjectionAbiValueKind.Enum -> enumParameterMarshaler(parameterBinding)
            KotlinProjectionAbiValueKind.Struct -> nativeStructParameterMarshaler(parameterBinding)
            KotlinProjectionAbiValueKind.Reference -> referenceParameterMarshaler(parameterBinding, WINRT_REFERENCE_PROJECTION_CLASS_NAME)
            KotlinProjectionAbiValueKind.ReferenceArray -> referenceParameterMarshaler(parameterBinding, WINRT_REFERENCE_ARRAY_PROJECTION_CLASS_NAME)
            KotlinProjectionAbiValueKind.Array -> arrayParameterMarshaler(parameterBinding, descriptor)
            KotlinProjectionAbiValueKind.Delegate -> delegateParameterMarshaler(parameterBinding)
            KotlinProjectionAbiValueKind.MappedBindableIterable,
            KotlinProjectionAbiValueKind.MappedBindableVector,
            KotlinProjectionAbiValueKind.MappedBindableVectorView -> bindableCollectionParameterMarshaler(parameterBinding)
            KotlinProjectionAbiValueKind.MappedIterable,
            KotlinProjectionAbiValueKind.MappedVector,
            KotlinProjectionAbiValueKind.MappedMap,
            KotlinProjectionAbiValueKind.MappedVectorView,
            KotlinProjectionAbiValueKind.MappedMapView -> mappedCollectionParameterMarshaler(parameterBinding)
            KotlinProjectionAbiValueKind.ProjectedInterface -> KotlinProjectionAbiMarshalerPlan(
                name = parameterName,
                typeBinding = parameterBinding.typeBinding,
                isReturn = false,
                abiArgumentExpression = CodeBlock.of("%T.fromRawComPtr((%L as %T).nativeObject.pointer)", PLATFORM_ABI_CLASS_NAME, parameterName, IWINRT_OBJECT_CLASS_NAME),
            )
            KotlinProjectionAbiValueKind.ProjectedRuntimeClass -> KotlinProjectionAbiMarshalerPlan(
                name = parameterName,
                typeBinding = parameterBinding.typeBinding,
                isReturn = false,
                abiArgumentExpression = CodeBlock.of("%T.fromRawComPtr((%L as %T).nativeObject.pointer)", PLATFORM_ABI_CLASS_NAME, parameterName, IWINRT_OBJECT_CLASS_NAME),
            )
            KotlinProjectionAbiValueKind.Object,
            KotlinProjectionAbiValueKind.UnknownReference,
            KotlinProjectionAbiValueKind.InspectableReference -> KotlinProjectionAbiMarshalerPlan(
                name = parameterName,
                typeBinding = parameterBinding.typeBinding,
                isReturn = false,
                abiArgumentExpression = CodeBlock.of("%T.fromRawComPtr(%L.pointer)", PLATFORM_ABI_CLASS_NAME, parameterName),
            )
            else -> null
        }
    }

    private fun buildAbiReturnMarshaler(
        returnBinding: KotlinProjectionAbiTypeBinding,
        descriptor: WinRtAbiMarshalerSlotDescriptor? = null,
    ): KotlinProjectionAbiMarshalerPlan? {
        if (returnBinding.kind == KotlinProjectionAbiValueKind.Array) {
            return arrayReturnMarshaler(returnBinding, descriptor)
        }
        val resultOutLayout = when {
            returnBinding.isMappedCollectionBinding() -> CodeBlock.of("%T.allocatePointerSlot(__scope)", PLATFORM_ABI_CLASS_NAME)
            returnBinding.isMappedBindableCollectionBinding() -> CodeBlock.of("%T.allocatePointerSlot(__scope)", PLATFORM_ABI_CLASS_NAME)
            else -> when (returnBinding.kind) {
            KotlinProjectionAbiValueKind.String,
            KotlinProjectionAbiValueKind.MappedAsyncAction,
            KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress,
            KotlinProjectionAbiValueKind.MappedAsyncOperation,
            KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress,
            KotlinProjectionAbiValueKind.Object,
            KotlinProjectionAbiValueKind.UnknownReference,
            KotlinProjectionAbiValueKind.InspectableReference,
            KotlinProjectionAbiValueKind.Reference,
            KotlinProjectionAbiValueKind.ReferenceArray -> CodeBlock.of("%T.allocatePointerSlot(__scope)", PLATFORM_ABI_CLASS_NAME)
            KotlinProjectionAbiValueKind.ProjectedInterface,
            KotlinProjectionAbiValueKind.ProjectedRuntimeClass -> CodeBlock.of("%T.allocatePointerSlot(__scope)", PLATFORM_ABI_CLASS_NAME)
            KotlinProjectionAbiValueKind.Enum -> abiResultAllocationForIntegralType(returnBinding.enumUnderlyingType ?: return null)
            KotlinProjectionAbiValueKind.Boolean -> CodeBlock.of("%T.allocateInt8Slot(__scope)", PLATFORM_ABI_CLASS_NAME)
            KotlinProjectionAbiValueKind.Int8,
            KotlinProjectionAbiValueKind.UInt8 -> CodeBlock.of("%T.allocateInt8Slot(__scope)", PLATFORM_ABI_CLASS_NAME)
            KotlinProjectionAbiValueKind.Int16,
            KotlinProjectionAbiValueKind.UInt16 -> CodeBlock.of("%T.allocateBytes(__scope, 2)", PLATFORM_ABI_CLASS_NAME)
            KotlinProjectionAbiValueKind.Int32,
            KotlinProjectionAbiValueKind.UInt32 -> CodeBlock.of("%T.allocateInt32Slot(__scope)", PLATFORM_ABI_CLASS_NAME)
            KotlinProjectionAbiValueKind.Int64,
            KotlinProjectionAbiValueKind.UInt64 -> CodeBlock.of("%T.allocateInt64Slot(__scope)", PLATFORM_ABI_CLASS_NAME)
            KotlinProjectionAbiValueKind.Float -> CodeBlock.of("%T.allocateBytes(__scope, 4)", PLATFORM_ABI_CLASS_NAME)
            KotlinProjectionAbiValueKind.Double -> CodeBlock.of("%T.allocateDoubleSlot(__scope)", PLATFORM_ABI_CLASS_NAME)
            KotlinProjectionAbiValueKind.Char16 -> CodeBlock.of("%T.allocateBytes(__scope, 2)", PLATFORM_ABI_CLASS_NAME)
            KotlinProjectionAbiValueKind.GuidValue -> CodeBlock.of("%T.allocateBytes(__scope, %T.BYTE_SIZE.toLong())", PLATFORM_ABI_CLASS_NAME, GUID_CLASS_NAME)
            KotlinProjectionAbiValueKind.Unit,
            KotlinProjectionAbiValueKind.MappedIterable,
            KotlinProjectionAbiValueKind.MappedVector,
            KotlinProjectionAbiValueKind.MappedMap,
            KotlinProjectionAbiValueKind.MappedVectorView,
            KotlinProjectionAbiValueKind.MappedMapView,
            KotlinProjectionAbiValueKind.MappedBindableIterable,
            KotlinProjectionAbiValueKind.MappedBindableVector,
            KotlinProjectionAbiValueKind.MappedBindableVectorView,
            KotlinProjectionAbiValueKind.Array,
            KotlinProjectionAbiValueKind.Unsupported -> return null
            KotlinProjectionAbiValueKind.MappedKeyValuePair -> CodeBlock.of("%T.allocatePointerSlot(__scope)", PLATFORM_ABI_CLASS_NAME)
            KotlinProjectionAbiValueKind.Delegate -> CodeBlock.of("%T.allocatePointerSlot(__scope)", PLATFORM_ABI_CLASS_NAME)
            KotlinProjectionAbiValueKind.Struct ->
                nativeStructClassName(returnBinding)?.let { returnType ->
                    CodeBlock.of("%T.allocateBytes(__scope, %T.Metadata.layout.sizeBytes)", PLATFORM_ABI_CLASS_NAME, returnType)
                } ?: return null
            }
        }
        val readbackStatement = when {
            returnBinding.isMappedCollectionBinding() -> mappedCollectionReturnReadback(returnBinding)
            returnBinding.isMappedBindableCollectionBinding() -> bindableCollectionReturnReadback(returnBinding)
            else -> when (returnBinding.kind) {
            KotlinProjectionAbiValueKind.String ->
                CodeBlock.of(
                    "return %T.fromHandle(%T.readPointer(__resultOut), owner = true).use { it.toKString() }\n",
                    HSTRING_CLASS_NAME,
                    PLATFORM_ABI_CLASS_NAME,
                )
            KotlinProjectionAbiValueKind.MappedAsyncAction ->
                CodeBlock.of(
                    "return %T(%T.readPointer(__resultOut))\n",
                    WINRT_ASYNC_ACTION_REFERENCE_CLASS_NAME,
                    PLATFORM_ABI_CLASS_NAME,
                )
            KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress ->
                asyncActionWithProgressReturnReadback(returnBinding) ?: return null
            KotlinProjectionAbiValueKind.MappedAsyncOperation ->
                asyncOperationReturnReadback(returnBinding) ?: return null
            KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress ->
                asyncOperationWithProgressReturnReadback(returnBinding) ?: return null
            KotlinProjectionAbiValueKind.Boolean ->
                CodeBlock.of("return %T.readInt8(__resultOut).toInt() != 0\n", PLATFORM_ABI_CLASS_NAME)
            KotlinProjectionAbiValueKind.Int8 ->
                CodeBlock.of("return %T.readInt8(__resultOut)\n", PLATFORM_ABI_CLASS_NAME)
            KotlinProjectionAbiValueKind.UInt8 ->
                CodeBlock.of("return %T.readInt8(__resultOut).toUByte()\n", PLATFORM_ABI_CLASS_NAME)
            KotlinProjectionAbiValueKind.Int16 ->
                CodeBlock.of("return %T.readInt16(__resultOut)\n", PLATFORM_ABI_CLASS_NAME)
            KotlinProjectionAbiValueKind.UInt16 ->
                CodeBlock.of("return %T.readInt16(__resultOut).toUShort()\n", PLATFORM_ABI_CLASS_NAME)
            KotlinProjectionAbiValueKind.Int32 ->
                CodeBlock.of("return %T.readInt32(__resultOut)\n", PLATFORM_ABI_CLASS_NAME)
            KotlinProjectionAbiValueKind.UInt32 ->
                CodeBlock.of("return %T.readInt32(__resultOut).toUInt()\n", PLATFORM_ABI_CLASS_NAME)
            KotlinProjectionAbiValueKind.Int64 ->
                CodeBlock.of("return %T.readInt64(__resultOut)\n", PLATFORM_ABI_CLASS_NAME)
            KotlinProjectionAbiValueKind.UInt64 ->
                CodeBlock.of("return %T.readInt64(__resultOut).toULong()\n", PLATFORM_ABI_CLASS_NAME)
            KotlinProjectionAbiValueKind.Float ->
                CodeBlock.of("return %T.readFloat(__resultOut)\n", PLATFORM_ABI_CLASS_NAME)
            KotlinProjectionAbiValueKind.Double ->
                CodeBlock.of("return %T.readDouble(__resultOut)\n", PLATFORM_ABI_CLASS_NAME)
            KotlinProjectionAbiValueKind.Char16 ->
                CodeBlock.of("return %T.readChar16(__resultOut)\n", PLATFORM_ABI_CLASS_NAME)
            KotlinProjectionAbiValueKind.GuidValue ->
                CodeBlock.of("return %T.readGuid(__resultOut)\n", PLATFORM_ABI_CLASS_NAME)
            KotlinProjectionAbiValueKind.Reference ->
                referenceReturnReadback(returnBinding, WINRT_REFERENCE_PROJECTION_CLASS_NAME) ?: return null
            KotlinProjectionAbiValueKind.ReferenceArray ->
                referenceReturnReadback(returnBinding, WINRT_REFERENCE_ARRAY_PROJECTION_CLASS_NAME) ?: return null
            KotlinProjectionAbiValueKind.Enum ->
                enumReturnReadback(returnBinding, resolvedReturnClassName(returnBinding))
            KotlinProjectionAbiValueKind.ProjectedRuntimeClass ->
                resolvedReturnClassName(returnBinding)?.let { returnType ->
                    CodeBlock.of(
                        "val __resultRef = %T(%T.toRawComPtr(%T.readPointer(__resultOut)))\nreturn %T.Metadata.wrap(__resultRef.asInspectable())\n",
                        IUNKNOWN_REFERENCE_CLASS_NAME,
                        PLATFORM_ABI_CLASS_NAME,
                        PLATFORM_ABI_CLASS_NAME,
                        returnType,
                    )
                }
            KotlinProjectionAbiValueKind.ProjectedInterface ->
                resolvedReturnClassName(returnBinding)?.let { returnType ->
                    CodeBlock.of(
                        "return %T.Metadata.wrap(%T(%T.toRawComPtr(%T.readPointer(__resultOut))))\n",
                        returnType,
                        IUNKNOWN_REFERENCE_CLASS_NAME,
                        PLATFORM_ABI_CLASS_NAME,
                        PLATFORM_ABI_CLASS_NAME,
                    )
                }
            KotlinProjectionAbiValueKind.InspectableReference ->
                if (resolvedReturnClassName(returnBinding) == IINSPECTABLE_REFERENCE_CLASS_NAME) {
                    CodeBlock.of(
                    "return (%T(%T.toRawComPtr(%T.readPointer(__resultOut))).use({ it.asInspectable() }))\n",
                        IUNKNOWN_REFERENCE_CLASS_NAME,
                        PLATFORM_ABI_CLASS_NAME,
                        PLATFORM_ABI_CLASS_NAME,
                    )
                } else {
                    return null
                }
            KotlinProjectionAbiValueKind.Object ->
                CodeBlock.of(
                    "return (%T(%T.toRawComPtr(%T.readPointer(__resultOut))).use { it.asInspectable() })\n",
                    IUNKNOWN_REFERENCE_CLASS_NAME,
                    PLATFORM_ABI_CLASS_NAME,
                    PLATFORM_ABI_CLASS_NAME,
                )
            KotlinProjectionAbiValueKind.UnknownReference ->
                if (resolvedReturnClassName(returnBinding) == IUNKNOWN_REFERENCE_CLASS_NAME) {
                    CodeBlock.of(
                        "return %T(%T.toRawComPtr(%T.readPointer(__resultOut)))\n",
                        IUNKNOWN_REFERENCE_CLASS_NAME,
                        PLATFORM_ABI_CLASS_NAME,
                        PLATFORM_ABI_CLASS_NAME,
                    )
                } else {
                    return null
                }
            KotlinProjectionAbiValueKind.Delegate ->
                resolvedReturnClassName(returnBinding)?.let { returnType ->
                    CodeBlock.of(
                        "return %T.Metadata.fromAbi(%T.readPointer(__resultOut)) ?: error(%S)\n",
                        returnType,
                        PLATFORM_ABI_CLASS_NAME,
                        "Expected non-null delegate instance from ABI return for ${returnBinding.resolvedTypeName}.",
                    )
                }
            KotlinProjectionAbiValueKind.Struct ->
                nativeStructClassName(returnBinding)?.let { returnType ->
                    CodeBlock.of("return %T.Metadata.fromAbi(__resultOut)\n", returnType)
                }
            KotlinProjectionAbiValueKind.MappedKeyValuePair ->
                mappedKeyValuePairReturnReadback(returnBinding)
            KotlinProjectionAbiValueKind.Unit,
            KotlinProjectionAbiValueKind.MappedIterable,
            KotlinProjectionAbiValueKind.MappedVector,
            KotlinProjectionAbiValueKind.MappedMap,
            KotlinProjectionAbiValueKind.MappedVectorView,
            KotlinProjectionAbiValueKind.MappedMapView,
            KotlinProjectionAbiValueKind.MappedBindableIterable,
            KotlinProjectionAbiValueKind.MappedBindableVector,
            KotlinProjectionAbiValueKind.MappedBindableVectorView,
            KotlinProjectionAbiValueKind.Array,
            KotlinProjectionAbiValueKind.Unsupported -> return null
            }
        }
        return KotlinProjectionAbiMarshalerPlan(
            name = "retval",
            typeBinding = returnBinding,
            isReturn = true,
            abiArgumentExpression = CodeBlock.of("__resultOut"),
            resultAllocation = resultOutLayout,
            readbackStatement = readbackStatement,
        )
    }

    private fun resolvedReturnClassName(
        returnBinding: KotlinProjectionAbiTypeBinding,
    ): ClassName? =
        runCatching { resolveTypeName(returnBinding.typeName) as? ClassName }.getOrNull()
            ?: runCatching { resolveTypeName(returnBinding.resolvedTypeName) as? ClassName }.getOrNull()

    private fun mappedKeyValuePairReturnReadback(
        returnBinding: KotlinProjectionAbiTypeBinding,
    ): CodeBlock? {
        val keyBinding = returnBinding.typeArguments.getOrNull(0) ?: return null
        val valueBinding = returnBinding.typeArguments.getOrNull(1) ?: return null
        val keyType = resolveTypeName(keyBinding.typeName)
        val valueType = resolveTypeName(valueBinding.typeName)
        return CodeBlock.of(
            """
            val __pairRef = %T(%T.toRawComPtr(%T.readPointer(__resultOut)))
            fun __readKey(__pair: %T): %T {
                %L
            }
            fun __readValue(__pair: %T): %T {
                %L
            }
            val __key = __readKey(__pairRef)
            val __value = __readValue(__pairRef)
            return object : %T {
                override val key: %T = __key
                override val value: %T = __value
            }
            """.trimIndent() + "\n",
            IUNKNOWN_REFERENCE_CLASS_NAME,
            PLATFORM_ABI_CLASS_NAME,
            PLATFORM_ABI_CLASS_NAME,
            IUNKNOWN_REFERENCE_CLASS_NAME,
            keyType,
            renderCollectionInvocation(
                invokeTargetExpression = "__pair",
                slotInterfaceQualifiedName = "Windows.Foundation.Collections.IKeyValuePair",
                slotConstantName = "KEY_GETTER_SLOT",
                returnBinding = keyBinding,
            ).toString(),
            IUNKNOWN_REFERENCE_CLASS_NAME,
            valueType,
            renderCollectionInvocation(
                invokeTargetExpression = "__pair",
                slotInterfaceQualifiedName = "Windows.Foundation.Collections.IKeyValuePair",
                slotConstantName = "VALUE_GETTER_SLOT",
                returnBinding = valueBinding,
            ).toString(),
            Map.Entry::class.asClassName().parameterizedBy(keyType, valueType),
            keyType,
            valueType,
        )
    }

    private fun arrayReturnMarshaler(
        returnBinding: KotlinProjectionAbiTypeBinding,
        descriptor: WinRtAbiMarshalerSlotDescriptor? = null,
    ): KotlinProjectionAbiMarshalerPlan? {
        @Suppress("UNUSED_PARAMETER")
        descriptor
        val elementBinding = returnBinding.typeArguments.singleOrNull() ?: return null
        nonBlittableArrayElementMarshalerExpression(elementBinding)?.let { elementMarshaler ->
            return nonBlittableArrayReturnMarshaler(returnBinding, elementMarshaler)
        }
        val elementSize = nativeArrayElementSizeExpression(elementBinding) ?: return null
        val elementRead = nativeArrayElementReadCode(
            elementBinding = elementBinding,
            dataExpression = CodeBlock.of("__arrayData"),
            indexExpression = CodeBlock.of("__index"),
        ) ?: return null
        return KotlinProjectionAbiMarshalerPlan(
            name = "retval",
            typeBinding = returnBinding,
            isReturn = true,
            abiArgumentExpression = CodeBlock.of("__resultLengthOut"),
            extraAbiArgumentExpressions = listOf(CodeBlock.of("__resultDataOut")),
            resultLocalDeclarations = CodeBlock.of(
                "val __resultLengthOut = %T.allocateInt32Slot(__scope)\nval __resultDataOut = %T.allocatePointerSlot(__scope)\n",
                PLATFORM_ABI_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
            ),
            readbackStatement = CodeBlock.of(
                """
                val __arrayLength = %T.readInt32(__resultLengthOut)
                val __arrayData = %T.readPointer(__resultDataOut)
                return Array(__arrayLength) { __index ->
                    %L
                }
                """.trimIndent() + "\n",
                PLATFORM_ABI_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
                elementRead,
            ),
        )
    }

    private fun nonBlittableArrayReturnMarshaler(
        returnBinding: KotlinProjectionAbiTypeBinding,
        elementMarshaler: CodeBlock,
    ): KotlinProjectionAbiMarshalerPlan =
        KotlinProjectionAbiMarshalerPlan(
            name = "retval",
            typeBinding = returnBinding,
            isReturn = true,
            abiArgumentExpression = CodeBlock.of("__resultLengthOut"),
            extraAbiArgumentExpressions = listOf(CodeBlock.of("__resultDataOut")),
            resultLocalDeclarations = CodeBlock.of(
                "val __resultLengthOut = %T.allocateInt32Slot(__scope)\nval __resultDataOut = %T.allocatePointerSlot(__scope)\n",
                PLATFORM_ABI_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
            ),
            readbackStatement = CodeBlock.of(
                """
                val __arrayLength = %T.readInt32(__resultLengthOut)
                val __arrayData = %T.readPointer(__resultDataOut)
                val __arrayMarshaler = %L
                val __arrayResult = __arrayMarshaler.fromAbiArray(__arrayLength, __arrayData)?.toTypedArray() ?: emptyArray()
                __arrayMarshaler.disposeAbiArray(__arrayLength, __arrayData)
                return __arrayResult as %T
                """.trimIndent() + "\n",
                PLATFORM_ABI_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
                elementMarshaler,
                resolveTypeName(returnBinding.typeName),
            ),
        )

    private fun nativeStructClassName(
        binding: KotlinProjectionAbiTypeBinding,
    ): ClassName? {
        mappedTypeByAbiName(binding.typeName.substringBefore('<').removeSuffix("?"))
            ?.takeIf { it.abiValueKind == KotlinProjectionAbiValueKind.Struct }
            ?.let { mappedType -> return mappedType.projectedTypeResolver(emptyList()) as? ClassName }
        mappedTypeByAbiName(binding.resolvedTypeName.substringBefore('<').removeSuffix("?"))
            ?.takeIf { it.abiValueKind == KotlinProjectionAbiValueKind.Struct }
            ?.let { mappedType -> return mappedType.projectedTypeResolver(emptyList()) as? ClassName }
        return runCatching { resolveTypeName(binding.typeName) as? ClassName }.getOrNull()
            ?: runCatching { resolveTypeName(binding.resolvedTypeName) as? ClassName }.getOrNull()
    }

    private fun nativeArrayElementSizeExpression(
        elementBinding: KotlinProjectionAbiTypeBinding,
    ): CodeBlock? =
        when (elementBinding.kind) {
            KotlinProjectionAbiValueKind.Boolean,
            KotlinProjectionAbiValueKind.Int8,
            KotlinProjectionAbiValueKind.UInt8 -> CodeBlock.of("1")
            KotlinProjectionAbiValueKind.Int16,
            KotlinProjectionAbiValueKind.UInt16,
            KotlinProjectionAbiValueKind.Char16 -> CodeBlock.of("2")
            KotlinProjectionAbiValueKind.Int32,
            KotlinProjectionAbiValueKind.UInt32,
            KotlinProjectionAbiValueKind.Float -> CodeBlock.of("4")
            KotlinProjectionAbiValueKind.Int64,
            KotlinProjectionAbiValueKind.UInt64,
            KotlinProjectionAbiValueKind.Double -> CodeBlock.of("8")
            KotlinProjectionAbiValueKind.GuidValue -> CodeBlock.of("%T.BYTE_SIZE.toLong()", GUID_CLASS_NAME)
            KotlinProjectionAbiValueKind.Enum ->
                elementBinding.enumUnderlyingType?.let(::nativeArrayIntegralElementSizeExpression)
            KotlinProjectionAbiValueKind.Struct ->
                nativeStructClassName(elementBinding)?.let { CodeBlock.of("%T.Metadata.layout.sizeBytes", it) }
            else -> null
        }

    private fun nativeArrayIntegralElementSizeExpression(type: WinRtIntegralType): CodeBlock =
        when (type) {
            WinRtIntegralType.Int8,
            WinRtIntegralType.UInt8 -> CodeBlock.of("1")
            WinRtIntegralType.Int16,
            WinRtIntegralType.UInt16 -> CodeBlock.of("2")
            WinRtIntegralType.Int32,
            WinRtIntegralType.UInt32 -> CodeBlock.of("4")
            WinRtIntegralType.Int64,
            WinRtIntegralType.UInt64 -> CodeBlock.of("8")
        }

    private fun nativeArrayElementSliceCode(
        elementBinding: KotlinProjectionAbiTypeBinding,
        dataExpression: CodeBlock,
        indexExpression: CodeBlock,
    ): CodeBlock? {
        val elementSize = nativeArrayElementSizeExpression(elementBinding) ?: return null
        return CodeBlock.of("%T.slice(%L, %L.toLong() * %L, %L)", PLATFORM_ABI_CLASS_NAME, dataExpression, indexExpression, elementSize, elementSize)
    }

    private fun nativeArrayElementReadCode(
        elementBinding: KotlinProjectionAbiTypeBinding,
        dataExpression: CodeBlock,
        indexExpression: CodeBlock,
    ): CodeBlock? {
        val slice = nativeArrayElementSliceCode(elementBinding, dataExpression, indexExpression) ?: return null
        return when (elementBinding.kind) {
            KotlinProjectionAbiValueKind.Boolean -> CodeBlock.of("%T.readInt8(%L).toInt() != 0", PLATFORM_ABI_CLASS_NAME, slice)
            KotlinProjectionAbiValueKind.Int8 -> CodeBlock.of("%T.readInt8(%L)", PLATFORM_ABI_CLASS_NAME, slice)
            KotlinProjectionAbiValueKind.UInt8 -> CodeBlock.of("%T.readInt8(%L).toUByte()", PLATFORM_ABI_CLASS_NAME, slice)
            KotlinProjectionAbiValueKind.Int16 -> CodeBlock.of("%T.readInt16(%L)", PLATFORM_ABI_CLASS_NAME, slice)
            KotlinProjectionAbiValueKind.UInt16 -> CodeBlock.of("%T.readInt16(%L).toUShort()", PLATFORM_ABI_CLASS_NAME, slice)
            KotlinProjectionAbiValueKind.Int32 -> CodeBlock.of("%T.readInt32(%L)", PLATFORM_ABI_CLASS_NAME, slice)
            KotlinProjectionAbiValueKind.UInt32 -> CodeBlock.of("%T.readInt32(%L).toUInt()", PLATFORM_ABI_CLASS_NAME, slice)
            KotlinProjectionAbiValueKind.Int64 -> CodeBlock.of("%T.readInt64(%L)", PLATFORM_ABI_CLASS_NAME, slice)
            KotlinProjectionAbiValueKind.UInt64 -> CodeBlock.of("%T.readInt64(%L).toULong()", PLATFORM_ABI_CLASS_NAME, slice)
            KotlinProjectionAbiValueKind.Float -> CodeBlock.of("%T.readFloat(%L)", PLATFORM_ABI_CLASS_NAME, slice)
            KotlinProjectionAbiValueKind.Double -> CodeBlock.of("%T.readDouble(%L)", PLATFORM_ABI_CLASS_NAME, slice)
            KotlinProjectionAbiValueKind.Char16 -> CodeBlock.of("%T.readChar16(%L)", PLATFORM_ABI_CLASS_NAME, slice)
            KotlinProjectionAbiValueKind.GuidValue -> CodeBlock.of("%T.readGuid(%L)", PLATFORM_ABI_CLASS_NAME, slice)
            KotlinProjectionAbiValueKind.Enum ->
                nativeArrayEnumElementReadCode(elementBinding, slice)
            KotlinProjectionAbiValueKind.Struct ->
                nativeStructClassName(elementBinding)?.let { CodeBlock.of("%T.Metadata.fromAbi(%L)", it, slice) }
            else -> null
        }
    }

    private fun nativeArrayEnumElementReadCode(
        elementBinding: KotlinProjectionAbiTypeBinding,
        slice: CodeBlock,
    ): CodeBlock? {
        val enumType = resolvedReturnClassName(elementBinding) ?: return null
        val readback = when (elementBinding.enumUnderlyingType ?: return null) {
            WinRtIntegralType.Int8 -> CodeBlock.of("%T.readInt8(%L)", PLATFORM_ABI_CLASS_NAME, slice)
            WinRtIntegralType.UInt8 -> CodeBlock.of("%T.readInt8(%L).toUByte()", PLATFORM_ABI_CLASS_NAME, slice)
            WinRtIntegralType.Int16 -> CodeBlock.of("%T.readInt16(%L)", PLATFORM_ABI_CLASS_NAME, slice)
            WinRtIntegralType.UInt16 -> CodeBlock.of("%T.readInt16(%L).toUShort()", PLATFORM_ABI_CLASS_NAME, slice)
            WinRtIntegralType.Int32 -> CodeBlock.of("%T.readInt32(%L)", PLATFORM_ABI_CLASS_NAME, slice)
            WinRtIntegralType.UInt32 -> CodeBlock.of("%T.readInt32(%L).toUInt()", PLATFORM_ABI_CLASS_NAME, slice)
            WinRtIntegralType.Int64 -> CodeBlock.of("%T.readInt64(%L)", PLATFORM_ABI_CLASS_NAME, slice)
            WinRtIntegralType.UInt64 -> CodeBlock.of("%T.readInt64(%L).toULong()", PLATFORM_ABI_CLASS_NAME, slice)
        }
        return CodeBlock.of("%T.Metadata.fromAbi(%L)", enumType, readback)
    }

    private fun nativeArrayElementWriteCode(
        elementBinding: KotlinProjectionAbiTypeBinding,
        dataExpression: CodeBlock,
        indexExpression: CodeBlock,
        valueExpression: CodeBlock,
    ): CodeBlock? {
        val slice = nativeArrayElementSliceCode(elementBinding, dataExpression, indexExpression) ?: return null
        return when (elementBinding.kind) {
            KotlinProjectionAbiValueKind.Boolean -> CodeBlock.of("%T.writeInt8(%L, if (%L) 1.toByte() else 0.toByte())", PLATFORM_ABI_CLASS_NAME, slice, valueExpression)
            KotlinProjectionAbiValueKind.Int8 -> CodeBlock.of("%T.writeInt8(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, valueExpression)
            KotlinProjectionAbiValueKind.UInt8 -> CodeBlock.of("%T.writeInt8(%L, %L.toByte())", PLATFORM_ABI_CLASS_NAME, slice, valueExpression)
            KotlinProjectionAbiValueKind.Int16 -> CodeBlock.of("%T.writeInt16(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, valueExpression)
            KotlinProjectionAbiValueKind.UInt16 -> CodeBlock.of("%T.writeInt16(%L, %L.toShort())", PLATFORM_ABI_CLASS_NAME, slice, valueExpression)
            KotlinProjectionAbiValueKind.Int32 -> CodeBlock.of("%T.writeInt32(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, valueExpression)
            KotlinProjectionAbiValueKind.UInt32 -> CodeBlock.of("%T.writeInt32(%L, %L.toInt())", PLATFORM_ABI_CLASS_NAME, slice, valueExpression)
            KotlinProjectionAbiValueKind.Int64 -> CodeBlock.of("%T.writeInt64(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, valueExpression)
            KotlinProjectionAbiValueKind.UInt64 -> CodeBlock.of("%T.writeInt64(%L, %L.toLong())", PLATFORM_ABI_CLASS_NAME, slice, valueExpression)
            KotlinProjectionAbiValueKind.Float -> CodeBlock.of("%T.writeFloat(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, valueExpression)
            KotlinProjectionAbiValueKind.Double -> CodeBlock.of("%T.writeDouble(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, valueExpression)
            KotlinProjectionAbiValueKind.Char16 -> CodeBlock.of("%T.writeChar16(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, valueExpression)
            KotlinProjectionAbiValueKind.GuidValue -> CodeBlock.of("%T.writeGuid(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, valueExpression)
            KotlinProjectionAbiValueKind.Enum ->
                nativeArrayEnumElementWriteCode(elementBinding, slice, valueExpression)
            KotlinProjectionAbiValueKind.Struct ->
                nativeStructClassName(elementBinding)?.let { CodeBlock.of("%T.Metadata.copyTo(%L, %L)", it, valueExpression, slice) }
            else -> null
        }
    }

    private fun nativeArrayEnumElementWriteCode(
        elementBinding: KotlinProjectionAbiTypeBinding,
        slice: CodeBlock,
        valueExpression: CodeBlock,
    ): CodeBlock? {
        val enumType = resolvedReturnClassName(elementBinding) ?: return null
        val abiValue = CodeBlock.of("%T.Metadata.toAbi(%L)", enumType, valueExpression)
        return when (elementBinding.enumUnderlyingType ?: return null) {
            WinRtIntegralType.Int8 -> CodeBlock.of("%T.writeInt8(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, abiValue)
            WinRtIntegralType.UInt8 -> CodeBlock.of("%T.writeInt8(%L, %L.toByte())", PLATFORM_ABI_CLASS_NAME, slice, abiValue)
            WinRtIntegralType.Int16 -> CodeBlock.of("%T.writeInt16(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, abiValue)
            WinRtIntegralType.UInt16 -> CodeBlock.of("%T.writeInt16(%L, %L.toShort())", PLATFORM_ABI_CLASS_NAME, slice, abiValue)
            WinRtIntegralType.Int32 -> CodeBlock.of("%T.writeInt32(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, abiValue)
            WinRtIntegralType.UInt32 -> CodeBlock.of("%T.writeInt32(%L, %L.toInt())", PLATFORM_ABI_CLASS_NAME, slice, abiValue)
            WinRtIntegralType.Int64 -> CodeBlock.of("%T.writeInt64(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, abiValue)
            WinRtIntegralType.UInt64 -> CodeBlock.of("%T.writeInt64(%L, %L.toLong())", PLATFORM_ABI_CLASS_NAME, slice, abiValue)
        }
    }

    private fun nativeStructParameterMarshaler(
        parameterBinding: KotlinProjectionAbiParameterBinding,
    ): KotlinProjectionAbiMarshalerPlan? {
        val structType = nativeStructClassName(parameterBinding.typeBinding) ?: return null
        val parameterName = parameterBinding.name
        val scopeName = "__${parameterName}StructScope"
        val abiLocalName = "__${parameterName}Abi"
        return KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%L", abiLocalName),
            scopeOpeners = listOf(
                CodeBlock.of(
                    "%T.confinedScope().use { %L ->\nval %L = %T.allocateBytes(%L, %T.Metadata.layout.sizeBytes)\n%T.Metadata.copyTo(%L, %L)",
                    PLATFORM_ABI_CLASS_NAME,
                    scopeName,
                    abiLocalName,
                    PLATFORM_ABI_CLASS_NAME,
                    scopeName,
                    structType,
                    structType,
                    parameterName,
                    abiLocalName,
                ),
            ),
        )
    }

    private fun arrayParameterMarshaler(
        parameterBinding: KotlinProjectionAbiParameterBinding,
        descriptor: WinRtAbiMarshalerSlotDescriptor? = null,
    ): KotlinProjectionAbiMarshalerPlan? {
        val category = descriptor?.category ?: parameterBinding.category
        val elementBinding = parameterBinding.typeBinding.typeArguments.singleOrNull() ?: return null
        nonBlittableArrayElementMarshalerExpression(elementBinding)?.let { elementMarshaler ->
            return nonBlittableArrayParameterMarshaler(parameterBinding, category, elementMarshaler)
        }
        val elementSize = nativeArrayElementSizeExpression(elementBinding) ?: return null
        val parameterName = parameterBinding.name
        if (category == WinRtMetadataParameterCategory.ReceiveArray) {
            val lengthOutName = "__${parameterName}LengthOut"
            val dataOutName = "__${parameterName}DataOut"
            return KotlinProjectionAbiMarshalerPlan(
                name = parameterName,
                typeBinding = parameterBinding.typeBinding,
                isReturn = false,
                abiArgumentExpression = CodeBlock.of("%L", lengthOutName),
                extraAbiArgumentExpressions = listOf(CodeBlock.of("%L", dataOutName)),
                scopeOpeners = listOf(
                    CodeBlock.of(
                        "%T.confinedScope().use { __${parameterName}OutScope ->\nval %L = %T.allocateInt32Slot(__${parameterName}OutScope)\nval %L = %T.allocatePointerSlot(__${parameterName}OutScope)",
                        PLATFORM_ABI_CLASS_NAME,
                        lengthOutName,
                        PLATFORM_ABI_CLASS_NAME,
                        dataOutName,
                        PLATFORM_ABI_CLASS_NAME,
                    ),
                ),
            )
        }
        val scopeName = "__${parameterName}ArrayScope"
        val dataName = "__${parameterName}ArrayData"
        val elementWrite = nativeArrayElementWriteCode(
            elementBinding = elementBinding,
            dataExpression = CodeBlock.of("%L", dataName),
            indexExpression = CodeBlock.of("__index"),
            valueExpression = CodeBlock.of("__element"),
        ) ?: return null
        val elementRead = nativeArrayElementReadCode(
            elementBinding = elementBinding,
            dataExpression = CodeBlock.of("%L", dataName),
            indexExpression = CodeBlock.of("__index"),
        )
        return KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%L.size", parameterName),
            extraAbiArgumentExpressions = listOf(CodeBlock.of("%L", dataName)),
            postCallStatements = if (category == WinRtMetadataParameterCategory.FillArray && elementRead != null) {
                listOf(
                    CodeBlock.of(
                        """
                        %L.indices.forEach { __index ->
                            %L[__index] = %L
                        }
                        """.trimIndent(),
                        parameterName,
                        parameterName,
                        elementRead,
                    ),
                )
            } else {
                emptyList()
            },
            scopeOpeners = listOf(
                CodeBlock.of(
                    """
                    %T.confinedScope().use { %L ->
                    val %L = %T.allocateBytes(%L, %L.size.toLong() * %L)
                    %L.forEachIndexed { __index, __element ->
                        %L
                    }
                    """.trimIndent(),
                    PLATFORM_ABI_CLASS_NAME,
                    scopeName,
                    dataName,
                    PLATFORM_ABI_CLASS_NAME,
                    scopeName,
                    parameterName,
                    elementSize,
                    parameterName,
                    elementWrite,
                ),
            ),
        )
    }

    private fun nonBlittableArrayParameterMarshaler(
        parameterBinding: KotlinProjectionAbiParameterBinding,
        category: WinRtMetadataParameterCategory,
        elementMarshaler: CodeBlock,
    ): KotlinProjectionAbiMarshalerPlan? {
        if (category == WinRtMetadataParameterCategory.ReceiveArray) {
            return null
        }
        val parameterName = parameterBinding.name
        val marshalerName = "__${parameterName}ArrayMarshaler"
        val arrayName = "__${parameterName}ArrayAbi"
        return KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%L?.length ?: 0", arrayName),
            extraAbiArgumentExpressions = listOf(CodeBlock.of("%L?.data ?: %T.nullPointer", arrayName, PLATFORM_ABI_CLASS_NAME)),
            postCallStatements = if (category == WinRtMetadataParameterCategory.FillArray) {
                listOf(
                    CodeBlock.of(
                        """
                        %L.fromAbiArray(%L.size, %L?.data ?: %T.nullPointer)?.forEachIndexed { __index, __element ->
                            (%L as Array<Any?>)[__index] = __element
                        }
                        """.trimIndent(),
                        marshalerName,
                        parameterName,
                        arrayName,
                        PLATFORM_ABI_CLASS_NAME,
                        parameterName,
                    ),
                )
            } else {
                emptyList()
            },
            scopeOpeners = listOf(
                CodeBlock.of(
                    """
                    val %L = %L
                    %L.createMarshalerArray(%L).use { %L ->
                    """.trimIndent(),
                    marshalerName,
                    elementMarshaler,
                    marshalerName,
                    parameterName,
                    arrayName,
                ),
            ),
        )
    }

    private fun nonBlittableArrayElementMarshalerExpression(
        elementBinding: KotlinProjectionAbiTypeBinding,
    ): CodeBlock? =
        when (elementBinding.kind) {
            KotlinProjectionAbiValueKind.String -> CodeBlock.of("%T.string()", MARSHALER_CLASS_NAME)
            KotlinProjectionAbiValueKind.Object,
            KotlinProjectionAbiValueKind.InspectableReference -> CodeBlock.of("%T.inspectableAny()", MARSHALER_CLASS_NAME)
            KotlinProjectionAbiValueKind.ProjectedInterface -> {
                val interfaceId = elementBinding.interfaceId ?: return null
                val projectedType = resolveTypeName(elementBinding.resolvedTypeName)
                CodeBlock.of(
                    "%T.interfaceType(%T(%S, %T(%S)), %T::class)",
                    MARSHALER_CLASS_NAME,
                    WINRT_TYPE_HANDLE_CLASS_NAME,
                    elementBinding.resolvedTypeName,
                    GUID_CLASS_NAME,
                    interfaceId.toString(),
                    projectedType,
                )
            }
            KotlinProjectionAbiValueKind.ProjectedRuntimeClass -> {
                val projectedType = resolveTypeName(elementBinding.resolvedTypeName)
                CodeBlock.of("%T.inspectable(%T::class)", MARSHALER_CLASS_NAME, projectedType)
            }
            else -> null
        }

    private fun asyncActionWithProgressReturnReadback(
        returnBinding: KotlinProjectionAbiTypeBinding,
    ): CodeBlock? {
        val progressBinding = returnBinding.typeArguments.singleOrNull() ?: return null
        val progressTypeSignature = asyncOperationResultTypeSignature(progressBinding) ?: return null
        val asyncActionType = WINRT_ASYNC_ACTION_WITH_PROGRESS_REFERENCE_CLASS_NAME.parameterizedBy(resolveTypeName(progressBinding.typeName))
        return CodeBlock.builder()
            .add("return %T(\n", asyncActionType)
            .indent()
            .add("pointer = %T.readPointer(__resultOut),\n", PLATFORM_ABI_CLASS_NAME)
            .add("interfaceId = %T.interfaceId(%L),\n", WINRT_ASYNC_ACTION_WITH_PROGRESS_REFERENCE_CLASS_NAME, progressTypeSignature)
            .add("progressHandlerInterfaceId = %T.progressHandlerInterfaceId(%L),\n", WINRT_ASYNC_ACTION_WITH_PROGRESS_REFERENCE_CLASS_NAME, progressTypeSignature)
            .add("completedHandlerInterfaceId = %T.completedHandlerInterfaceId(%L),\n", WINRT_ASYNC_ACTION_WITH_PROGRESS_REFERENCE_CLASS_NAME, progressTypeSignature)
            .unindent()
            .add(")\n")
            .build()
    }

    private fun asyncOperationReturnReadback(
        returnBinding: KotlinProjectionAbiTypeBinding,
    ): CodeBlock? {
        val resultBinding = returnBinding.typeArguments.singleOrNull() ?: return null
        val resultTypeSignature = asyncOperationResultTypeSignature(resultBinding) ?: return null
        val resultOutAllocation = abiResultAllocationForAsyncOperationResult(resultBinding, "__operationScope") ?: return null
        val resultReadbackExpression = asyncOperationResultReadbackExpression(resultBinding) ?: return null
        val asyncOperationType = WINRT_ASYNC_OPERATION_REFERENCE_CLASS_NAME.parameterizedBy(resolveTypeName(resultBinding.typeName))
        return CodeBlock.builder()
            .add("return %T(\n", asyncOperationType)
            .indent()
            .add("pointer = %T.readPointer(__resultOut),\n", PLATFORM_ABI_CLASS_NAME)
            .add("interfaceId = %T.interfaceId(%L),\n", WINRT_ASYNC_OPERATION_REFERENCE_CLASS_NAME, resultTypeSignature)
            .add("completedHandlerInterfaceId = %T.completedHandlerInterfaceId(%L),\n", WINRT_ASYNC_OPERATION_REFERENCE_CLASS_NAME, resultTypeSignature)
            .add("resultReader = { __operation ->\n")
            .indent()
            .add("%T.confinedScope().use { __operationScope ->\n", PLATFORM_ABI_CLASS_NAME)
            .indent()
            .add("val __operationResultOut = %L\n", resultOutAllocation)
            .add(
                "val __operationHr = %T.invokeArgs(__operation.pointer, %T.GetResults, __operationResultOut)\n",
                COM_VTABLE_INVOKER_CLASS_NAME,
                WINRT_ASYNC_OPERATION_VFTBL_SLOTS_CLASS_NAME,
            )
            .add("%T.checkSucceededRaw(__operationHr)\n", WINRT_PLATFORM_API_CLASS_NAME)
            .add("%L\n", resultReadbackExpression)
            .unindent()
            .add("}\n")
            .unindent()
            .add("},\n")
            .unindent()
            .add(")\n")
            .build()
    }

    private fun asyncOperationWithProgressReturnReadback(
        returnBinding: KotlinProjectionAbiTypeBinding,
    ): CodeBlock? {
        val resultBinding = returnBinding.typeArguments.getOrNull(0) ?: return null
        val progressBinding = returnBinding.typeArguments.getOrNull(1) ?: return null
        val resultTypeSignature = asyncOperationResultTypeSignature(resultBinding) ?: return null
        val progressTypeSignature = asyncOperationResultTypeSignature(progressBinding) ?: return null
        val resultOutAllocation = abiResultAllocationForAsyncOperationResult(resultBinding, "__operationScope") ?: return null
        val resultReadbackExpression = asyncOperationResultReadbackExpression(resultBinding) ?: return null
        val asyncOperationType = WINRT_ASYNC_OPERATION_WITH_PROGRESS_REFERENCE_CLASS_NAME.parameterizedBy(
            resolveTypeName(resultBinding.typeName),
            resolveTypeName(progressBinding.typeName),
        )
        return CodeBlock.builder()
            .add("return %T(\n", asyncOperationType)
            .indent()
            .add("pointer = %T.readPointer(__resultOut),\n", PLATFORM_ABI_CLASS_NAME)
            .add("interfaceId = %T.interfaceId(%L, %L),\n", WINRT_ASYNC_OPERATION_WITH_PROGRESS_REFERENCE_CLASS_NAME, resultTypeSignature, progressTypeSignature)
            .add("progressHandlerInterfaceId = %T.progressHandlerInterfaceId(%L, %L),\n", WINRT_ASYNC_OPERATION_WITH_PROGRESS_REFERENCE_CLASS_NAME, resultTypeSignature, progressTypeSignature)
            .add("completedHandlerInterfaceId = %T.completedHandlerInterfaceId(%L, %L),\n", WINRT_ASYNC_OPERATION_WITH_PROGRESS_REFERENCE_CLASS_NAME, resultTypeSignature, progressTypeSignature)
            .add("resultReader = { __operation ->\n")
            .indent()
            .add("%T.confinedScope().use { __operationScope ->\n", PLATFORM_ABI_CLASS_NAME)
            .indent()
            .add("val __operationResultOut = %L\n", resultOutAllocation)
            .add(
                "val __operationHr = %T.invokeArgs(__operation.pointer, %T.GetResults, __operationResultOut)\n",
                COM_VTABLE_INVOKER_CLASS_NAME,
                WINRT_ASYNC_OPERATION_WITH_PROGRESS_VFTBL_SLOTS_CLASS_NAME,
            )
            .add("%T.checkSucceededRaw(__operationHr)\n", WINRT_PLATFORM_API_CLASS_NAME)
            .add("%L\n", resultReadbackExpression)
            .unindent()
            .add("}\n")
            .unindent()
            .add("},\n")
            .unindent()
            .add(")\n")
            .build()
    }

    private fun asyncOperationResultTypeSignature(
        resultBinding: KotlinProjectionAbiTypeBinding,
    ): CodeBlock? = abiTypeSignature(resultBinding)

    private fun referenceParameterMarshaler(
        parameterBinding: KotlinProjectionAbiParameterBinding,
        projectionClass: ClassName,
    ): KotlinProjectionAbiMarshalerPlan? {
        val interfaceId = referenceInterfaceIdCode(parameterBinding.typeBinding) ?: return null
        val parameterName = parameterBinding.name
        val abiLocalName = "__${parameterName}Abi"
        return KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%L?.abi ?: %T.nullPointer", abiLocalName, PLATFORM_ABI_CLASS_NAME),
            scopeOpeners = listOf(
                CodeBlock.of("%T.createMarshaler(%L, %L).use { %L ->", projectionClass, parameterName, interfaceId, abiLocalName),
            ),
        )
    }

    private fun referenceReadbackExpression(
        typeBinding: KotlinProjectionAbiTypeBinding,
        projectionClass: ClassName,
        resultOutName: String,
    ): CodeBlock? {
        val projectedType = resolveTypeName(typeBinding.typeName)
        val interfaceId = referenceInterfaceIdCode(typeBinding) ?: return null
        return CodeBlock.of(
            "%T.fromAbi(%T.readPointer(%L), %L) as %T",
            projectionClass,
            PLATFORM_ABI_CLASS_NAME,
            resultOutName,
            interfaceId,
            projectedType,
        )
    }

    private fun referenceReturnReadback(
        typeBinding: KotlinProjectionAbiTypeBinding,
        projectionClass: ClassName,
    ): CodeBlock? =
        referenceReadbackExpression(typeBinding, projectionClass, "__resultOut")
            ?.let { CodeBlock.of("return %L\n", it) }

    private fun abiTypeSignature(
        binding: KotlinProjectionAbiTypeBinding,
    ): CodeBlock? = when (binding.kind) {
        KotlinProjectionAbiValueKind.MappedIterable ->
            binding.typeArguments.singleOrNull()?.let(::abiTypeSignature)
                ?.let { CodeBlock.of("%T.iterableSignature(%L)", WINRT_COLLECTION_INTERFACE_IDS_CLASS_NAME, it) }
        KotlinProjectionAbiValueKind.MappedVectorView ->
            binding.typeArguments.singleOrNull()?.let(::abiTypeSignature)
                ?.let { CodeBlock.of("%T.vectorViewSignature(%L)", WINRT_COLLECTION_INTERFACE_IDS_CLASS_NAME, it) }
        KotlinProjectionAbiValueKind.MappedVector ->
            binding.typeArguments.singleOrNull()?.let(::abiTypeSignature)
                ?.let { CodeBlock.of("%T.vectorSignature(%L)", WINRT_COLLECTION_INTERFACE_IDS_CLASS_NAME, it) }
        KotlinProjectionAbiValueKind.MappedMapView -> {
            val key = binding.typeArguments.getOrNull(0)?.let(::abiTypeSignature)
            val value = binding.typeArguments.getOrNull(1)?.let(::abiTypeSignature)
            if (key != null && value != null) CodeBlock.of("%T.mapViewSignature(%L, %L)", WINRT_COLLECTION_INTERFACE_IDS_CLASS_NAME, key, value) else null
        }
            KotlinProjectionAbiValueKind.MappedMap -> {
                val key = binding.typeArguments.getOrNull(0)?.let(::abiTypeSignature)
                val value = binding.typeArguments.getOrNull(1)?.let(::abiTypeSignature)
                if (key != null && value != null) CodeBlock.of("%T.mapSignature(%L, %L)", WINRT_COLLECTION_INTERFACE_IDS_CLASS_NAME, key, value) else null
            }
        KotlinProjectionAbiValueKind.Reference,
        KotlinProjectionAbiValueKind.ReferenceArray -> referenceTypeSignatureCode(binding)
        KotlinProjectionAbiValueKind.String -> CodeBlock.of("%T.string()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
        KotlinProjectionAbiValueKind.Boolean -> CodeBlock.of("%T.boolean()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int8 -> CodeBlock.of("%T.int8()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
        KotlinProjectionAbiValueKind.UInt8 -> CodeBlock.of("%T.uint8()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int16 -> CodeBlock.of("%T.int16()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
        KotlinProjectionAbiValueKind.UInt16 -> CodeBlock.of("%T.uint16()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int32 -> CodeBlock.of("%T.int32()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
        KotlinProjectionAbiValueKind.UInt32 -> CodeBlock.of("%T.uint32()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int64 -> CodeBlock.of("%T.int64()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
        KotlinProjectionAbiValueKind.UInt64 -> CodeBlock.of("%T.uint64()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
        KotlinProjectionAbiValueKind.Float -> CodeBlock.of("%T.float32()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
        KotlinProjectionAbiValueKind.Double -> CodeBlock.of("%T.float64()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
        KotlinProjectionAbiValueKind.Char16 -> CodeBlock.of("%T.char16()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
        KotlinProjectionAbiValueKind.GuidValue -> CodeBlock.of("%T.guidValue()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
        KotlinProjectionAbiValueKind.Enum ->
            resolvedReturnClassName(binding)?.let {
                CodeBlock.of("%T.enum(%S, %L)", WINRT_TYPE_SIGNATURE_CLASS_NAME, binding.resolvedTypeName, binding.enumUnderlyingType?.let(::abiTypeSignatureForIntegralType) ?: CodeBlock.of("%T.int32()", WINRT_TYPE_SIGNATURE_CLASS_NAME))
            }
        KotlinProjectionAbiValueKind.Struct ->
            nativeStructClassName(binding)?.let {
                CodeBlock.of("%T.struct(%S)", WINRT_TYPE_SIGNATURE_CLASS_NAME, binding.resolvedTypeName)
            }
        KotlinProjectionAbiValueKind.Object,
        KotlinProjectionAbiValueKind.InspectableReference -> CodeBlock.of("%T.object_()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
        KotlinProjectionAbiValueKind.ProjectedInterface ->
            resolvedReturnClassName(binding)?.let { resultType ->
                CodeBlock.of("%T.guid(%T.Metadata.IID)", WINRT_TYPE_SIGNATURE_CLASS_NAME, resultType)
            }
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass ->
            resolvedReturnClassName(binding)?.let { resultType ->
                CodeBlock.of(
                    "%T.runtimeClass(%S, %T.guid(%T.Metadata.DEFAULT_INTERFACE_IID))",
                    WINRT_TYPE_SIGNATURE_CLASS_NAME,
                    binding.resolvedTypeName,
                    WINRT_TYPE_SIGNATURE_CLASS_NAME,
                    resultType,
                )
            }
        else -> null
    }

    private fun referenceTypeSignatureCode(
        binding: KotlinProjectionAbiTypeBinding,
    ): CodeBlock? {
        val genericInterfaceId = binding.interfaceId ?: return null
        val elementSignature = binding.typeArguments.singleOrNull()?.let(::abiTypeSignature) ?: return null
        return CodeBlock.of(
            "%T.parameterizedInterface(%T(%S), %L)",
            WINRT_TYPE_SIGNATURE_CLASS_NAME,
            GUID_CLASS_NAME,
            genericInterfaceId.toString(),
            elementSignature,
        )
    }

    private fun referenceInterfaceIdCode(
        binding: KotlinProjectionAbiTypeBinding,
    ): CodeBlock? {
        val genericInterfaceId = binding.interfaceId ?: return null
        val elementSignature = binding.typeArguments.singleOrNull()?.let(::abiTypeSignature) ?: return null
        return CodeBlock.of(
            "%T.createFromParameterizedInterface(%T(%S), %L)",
            PARAMETERIZED_INTERFACE_ID_CLASS_NAME,
            GUID_CLASS_NAME,
            genericInterfaceId.toString(),
            elementSignature,
        )
    }

    private fun abiResultAllocationForAsyncOperationResult(
        resultBinding: KotlinProjectionAbiTypeBinding,
        scopeName: String,
    ): CodeBlock? = when (resultBinding.kind) {
        KotlinProjectionAbiValueKind.String -> CodeBlock.of("%T.allocatePointerSlot(%L)", PLATFORM_ABI_CLASS_NAME, scopeName)
        KotlinProjectionAbiValueKind.Boolean -> CodeBlock.of("%T.allocateInt8Slot(%L)", PLATFORM_ABI_CLASS_NAME, scopeName)
        KotlinProjectionAbiValueKind.Int8,
        KotlinProjectionAbiValueKind.UInt8 -> CodeBlock.of("%T.allocateInt8Slot(%L)", PLATFORM_ABI_CLASS_NAME, scopeName)
        KotlinProjectionAbiValueKind.Int16,
        KotlinProjectionAbiValueKind.UInt16 -> CodeBlock.of("%T.allocateBytes(%L, 2)", PLATFORM_ABI_CLASS_NAME, scopeName)
        KotlinProjectionAbiValueKind.Int32,
        KotlinProjectionAbiValueKind.UInt32 -> CodeBlock.of("%T.allocateInt32Slot(%L)", PLATFORM_ABI_CLASS_NAME, scopeName)
        KotlinProjectionAbiValueKind.Int64,
        KotlinProjectionAbiValueKind.UInt64 -> CodeBlock.of("%T.allocateInt64Slot(%L)", PLATFORM_ABI_CLASS_NAME, scopeName)
        KotlinProjectionAbiValueKind.Float -> CodeBlock.of("%T.allocateBytes(%L, 4)", PLATFORM_ABI_CLASS_NAME, scopeName)
        KotlinProjectionAbiValueKind.Double -> CodeBlock.of("%T.allocateDoubleSlot(%L)", PLATFORM_ABI_CLASS_NAME, scopeName)
        KotlinProjectionAbiValueKind.Char16 -> CodeBlock.of("%T.allocateBytes(%L, 2)", PLATFORM_ABI_CLASS_NAME, scopeName)
        KotlinProjectionAbiValueKind.Enum -> bindingAllocationForAsyncEnum(resultBinding, scopeName)
        KotlinProjectionAbiValueKind.Struct ->
            nativeStructClassName(resultBinding)?.let { resultType ->
                CodeBlock.of("%T.allocateBytes(%L, %T.Metadata.layout.sizeBytes)", PLATFORM_ABI_CLASS_NAME, scopeName, resultType)
            }
        KotlinProjectionAbiValueKind.MappedIterable,
        KotlinProjectionAbiValueKind.MappedVector,
        KotlinProjectionAbiValueKind.MappedMap,
        KotlinProjectionAbiValueKind.MappedVectorView,
        KotlinProjectionAbiValueKind.MappedMapView -> CodeBlock.of("%T.allocatePointerSlot(%L)", PLATFORM_ABI_CLASS_NAME, scopeName)
        KotlinProjectionAbiValueKind.ProjectedInterface,
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass,
        KotlinProjectionAbiValueKind.Object,
        KotlinProjectionAbiValueKind.UnknownReference,
        KotlinProjectionAbiValueKind.InspectableReference,
        KotlinProjectionAbiValueKind.Reference,
        KotlinProjectionAbiValueKind.ReferenceArray -> CodeBlock.of("%T.allocatePointerSlot(%L)", PLATFORM_ABI_CLASS_NAME, scopeName)
        else -> null
    }

    private fun bindingAllocationForAsyncEnum(
        resultBinding: KotlinProjectionAbiTypeBinding,
        scopeName: String,
    ): CodeBlock? = when (resultBinding.enumUnderlyingType) {
        WinRtIntegralType.Int8,
        WinRtIntegralType.UInt8 -> CodeBlock.of("%T.allocateInt8Slot(%L)", PLATFORM_ABI_CLASS_NAME, scopeName)
        WinRtIntegralType.Int16,
        WinRtIntegralType.UInt16 -> CodeBlock.of("%T.allocateBytes(%L, 2)", PLATFORM_ABI_CLASS_NAME, scopeName)
        WinRtIntegralType.Int32,
        WinRtIntegralType.UInt32 -> CodeBlock.of("%T.allocateInt32Slot(%L)", PLATFORM_ABI_CLASS_NAME, scopeName)
        WinRtIntegralType.Int64,
        WinRtIntegralType.UInt64 -> CodeBlock.of("%T.allocateInt64Slot(%L)", PLATFORM_ABI_CLASS_NAME, scopeName)
        null -> null
    }

    private fun abiTypeSignatureForIntegralType(type: WinRtIntegralType): CodeBlock = when (type) {
        WinRtIntegralType.Int8 -> CodeBlock.of("%T.int8()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
        WinRtIntegralType.UInt8 -> CodeBlock.of("%T.uint8()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
        WinRtIntegralType.Int16 -> CodeBlock.of("%T.int16()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
        WinRtIntegralType.UInt16 -> CodeBlock.of("%T.uint16()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
        WinRtIntegralType.Int32 -> CodeBlock.of("%T.int32()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
        WinRtIntegralType.UInt32 -> CodeBlock.of("%T.uint32()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
        WinRtIntegralType.Int64 -> CodeBlock.of("%T.int64()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
        WinRtIntegralType.UInt64 -> CodeBlock.of("%T.uint64()", WINRT_TYPE_SIGNATURE_CLASS_NAME)
    }

    private fun asyncOperationResultReadbackExpression(
        resultBinding: KotlinProjectionAbiTypeBinding,
    ): CodeBlock? = when (resultBinding.kind) {
        KotlinProjectionAbiValueKind.String ->
            CodeBlock.of(
                "%T.fromHandle(%T.readPointer(__operationResultOut), owner = true).use { it.toKString() }",
                HSTRING_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
            )
        KotlinProjectionAbiValueKind.Boolean ->
            CodeBlock.of("%T.readInt8(__operationResultOut).toInt() != 0", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int8 ->
            CodeBlock.of("%T.readInt8(__operationResultOut)", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.UInt8 ->
            CodeBlock.of("%T.readInt8(__operationResultOut).toUByte()", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int16 ->
            CodeBlock.of("%T.readInt16(__operationResultOut)", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.UInt16 ->
            CodeBlock.of("%T.readInt16(__operationResultOut).toUShort()", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int32 ->
            CodeBlock.of("%T.readInt32(__operationResultOut)", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.UInt32 ->
            CodeBlock.of("%T.readInt32(__operationResultOut).toUInt()", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int64 ->
            CodeBlock.of("%T.readInt64(__operationResultOut)", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.UInt64 ->
            CodeBlock.of("%T.readInt64(__operationResultOut).toULong()", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Float ->
            CodeBlock.of("%T.readFloat(__operationResultOut)", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Double ->
            CodeBlock.of("%T.readDouble(__operationResultOut)", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Char16 ->
            CodeBlock.of("%T.readChar16(__operationResultOut)", PLATFORM_ABI_CLASS_NAME)
        KotlinProjectionAbiValueKind.Enum ->
            asyncEnumResultReadbackExpression(resultBinding)
        KotlinProjectionAbiValueKind.Struct ->
            nativeStructClassName(resultBinding)?.let { resultType ->
                CodeBlock.of("%T.Metadata.fromAbi(__operationResultOut)", resultType)
            }
        KotlinProjectionAbiValueKind.MappedIterable,
        KotlinProjectionAbiValueKind.MappedVectorView,
        KotlinProjectionAbiValueKind.MappedMapView,
        KotlinProjectionAbiValueKind.MappedVector,
        KotlinProjectionAbiValueKind.MappedMap ->
            asyncMappedCollectionResultReadbackExpression(resultBinding)
        KotlinProjectionAbiValueKind.Reference ->
            referenceReadbackExpression(resultBinding, WINRT_REFERENCE_PROJECTION_CLASS_NAME, "__operationResultOut")
        KotlinProjectionAbiValueKind.ReferenceArray ->
            referenceReadbackExpression(resultBinding, WINRT_REFERENCE_ARRAY_PROJECTION_CLASS_NAME, "__operationResultOut")
        KotlinProjectionAbiValueKind.Object,
        KotlinProjectionAbiValueKind.InspectableReference ->
            CodeBlock.of(
                "%T(%T.toRawComPtr(%T.readPointer(__operationResultOut))).use { it.asInspectable() }",
                IUNKNOWN_REFERENCE_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
            )
        KotlinProjectionAbiValueKind.UnknownReference ->
            CodeBlock.of(
                "%T(%T.toRawComPtr(%T.readPointer(__operationResultOut)))",
                IUNKNOWN_REFERENCE_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
            )
        KotlinProjectionAbiValueKind.ProjectedInterface ->
            resolvedReturnClassName(resultBinding)?.let { resultType ->
                CodeBlock.of(
                    "%T.Metadata.wrap(%T(%T.toRawComPtr(%T.readPointer(__operationResultOut))))",
                    resultType,
                    IUNKNOWN_REFERENCE_CLASS_NAME,
                    PLATFORM_ABI_CLASS_NAME,
                    PLATFORM_ABI_CLASS_NAME,
                )
            }
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass ->
            resolvedReturnClassName(resultBinding)?.let { resultType ->
                CodeBlock.of(
                    "%T.Metadata.wrap(%T(%T.toRawComPtr(%T.readPointer(__operationResultOut))).asInspectable())",
                    resultType,
                    IUNKNOWN_REFERENCE_CLASS_NAME,
                    PLATFORM_ABI_CLASS_NAME,
                    PLATFORM_ABI_CLASS_NAME,
                )
            }
        else -> null
    }

    private fun asyncMappedCollectionResultReadbackExpression(
        resultBinding: KotlinProjectionAbiTypeBinding,
    ): CodeBlock? {
        readOnlyCollectionBindingForReturn(resultBinding)?.let { binding ->
            return CodeBlock.of(
                "run {\nval __collectionRef = %T(%T.toRawComPtr(%T.readPointer(__operationResultOut)))\n%L}\n",
                IUNKNOWN_REFERENCE_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
                renderReadOnlyCollectionDelegateInitializer(binding),
            )
        }
        mutableCollectionBindingForReturn(resultBinding)?.let { binding ->
            return CodeBlock.of(
                "run {\nval __collectionRef = %T(%T.toRawComPtr(%T.readPointer(__operationResultOut)))\n%L}\n",
                IUNKNOWN_REFERENCE_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
                renderMutableCollectionDelegateInitializer(binding),
            )
        }
        return null
    }

    private fun asyncEnumResultReadbackExpression(
        resultBinding: KotlinProjectionAbiTypeBinding,
    ): CodeBlock? {
        val enumType = resolvedReturnClassName(resultBinding) ?: return null
        val readback = when (resultBinding.enumUnderlyingType ?: return null) {
            WinRtIntegralType.Int8 -> CodeBlock.of("%T.readInt8(__operationResultOut)", PLATFORM_ABI_CLASS_NAME)
            WinRtIntegralType.UInt8 -> CodeBlock.of("%T.readInt8(__operationResultOut).toUByte()", PLATFORM_ABI_CLASS_NAME)
            WinRtIntegralType.Int16 -> CodeBlock.of("%T.readInt16(__operationResultOut)", PLATFORM_ABI_CLASS_NAME)
            WinRtIntegralType.UInt16 -> CodeBlock.of("%T.readInt16(__operationResultOut).toUShort()", PLATFORM_ABI_CLASS_NAME)
            WinRtIntegralType.Int32 -> CodeBlock.of("%T.readInt32(__operationResultOut)", PLATFORM_ABI_CLASS_NAME)
            WinRtIntegralType.UInt32 -> CodeBlock.of("%T.readInt32(__operationResultOut).toUInt()", PLATFORM_ABI_CLASS_NAME)
            WinRtIntegralType.Int64 -> CodeBlock.of("%T.readInt64(__operationResultOut)", PLATFORM_ABI_CLASS_NAME)
            WinRtIntegralType.UInt64 -> CodeBlock.of("%T.readInt64(__operationResultOut).toULong()", PLATFORM_ABI_CLASS_NAME)
        }
        return CodeBlock.of("%T.Metadata.fromAbi(%L)", enumType, readback)
    }

    private fun mappedCollectionReturnReadback(
        returnBinding: KotlinProjectionAbiTypeBinding,
    ): CodeBlock? {
        readOnlyCollectionBindingForReturn(returnBinding)?.let { binding ->
            return CodeBlock.of(
                "val __collectionRef = %T(%T.toRawComPtr(%T.readPointer(__resultOut)))\nreturn %L\n",
                IUNKNOWN_REFERENCE_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
                renderReadOnlyCollectionDelegateInitializer(binding),
            )
        }
        mutableCollectionBindingForReturn(returnBinding)?.let { binding ->
            return CodeBlock.of(
                "val __collectionRef = %T(%T.toRawComPtr(%T.readPointer(__resultOut)))\nreturn %L\n",
                IUNKNOWN_REFERENCE_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
                renderMutableCollectionDelegateInitializer(binding),
            )
        }
        return null
    }

    private fun readOnlyCollectionBindingForReturn(
        returnBinding: KotlinProjectionAbiTypeBinding,
    ): KotlinProjectionReadOnlyCollectionBinding? {
        val mappedType = mappedTypeByAbiKind(returnBinding.kind) ?: return null
        val collectionKind = mappedType.readOnlyCollectionKind ?: return null
        return createReadOnlyCollectionBindingPlan(
            collectionKind = collectionKind,
            ownerInterfaceQualifiedName = returnBinding.typeName,
            ownerCachePropertyName = "__collectionRef",
            slotInterfaceQualifiedName = returnBinding.resolvedTypeName,
            delegatePropertyName = collectionKind.returnDelegatePropertyName(),
            typeArguments = returnBinding.typeArguments,
            errorContext = returnBinding.typeName,
            requireSupportedBinding = true,
            bindingLocationLabel = "return",
        )
    }

    private fun mutableCollectionBindingForReturn(
        returnBinding: KotlinProjectionAbiTypeBinding,
    ): KotlinProjectionMutableCollectionBinding? {
        val mappedType = mappedTypeByAbiKind(returnBinding.kind) ?: return null
        val collectionKind = mappedType.mutableCollectionKind ?: return null
        return createMutableCollectionBindingPlan(
            collectionKind = collectionKind,
            ownerInterfaceQualifiedName = returnBinding.typeName,
            ownerCachePropertyName = "__collectionRef",
            slotInterfaceQualifiedName = returnBinding.resolvedTypeName,
            delegatePropertyName = collectionKind.returnDelegatePropertyName(),
            typeArguments = returnBinding.typeArguments,
            errorContext = returnBinding.typeName,
            requireSupportedBinding = true,
            bindingLocationLabel = "return",
        )
    }

    private fun bindableCollectionParameterMarshaler(
        parameterBinding: KotlinProjectionAbiParameterBinding,
    ): KotlinProjectionAbiMarshalerPlan? {
        val projectionClass = when (parameterBinding.typeBinding.kind) {
            KotlinProjectionAbiValueKind.MappedBindableIterable -> WINRT_BINDABLE_ITERABLE_PROJECTION_CLASS_NAME
            KotlinProjectionAbiValueKind.MappedBindableVector -> WINRT_BINDABLE_VECTOR_PROJECTION_CLASS_NAME
            KotlinProjectionAbiValueKind.MappedBindableVectorView -> WINRT_BINDABLE_VECTOR_VIEW_PROJECTION_CLASS_NAME
            else -> return null
        }
        val parameterName = parameterBinding.name
        val abiLocalName = "__${parameterName}Abi"
        return KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%L.abi", abiLocalName),
            scopeOpeners = listOf(
                CodeBlock.of("%T.createMarshaler(%L)!!.use { %L ->", projectionClass, parameterName, abiLocalName),
            ),
        )
    }

    private fun mappedCollectionParameterMarshaler(
        parameterBinding: KotlinProjectionAbiParameterBinding,
    ): KotlinProjectionAbiMarshalerPlan? {
        val parameterName = parameterBinding.name
        val abiLocalName = "__${parameterName}Abi"
        val projectionClass = when (parameterBinding.typeBinding.kind) {
            KotlinProjectionAbiValueKind.MappedIterable -> WINRT_ITERABLE_PROJECTION_CLASS_NAME
            KotlinProjectionAbiValueKind.MappedVector -> WINRT_LIST_PROJECTION_CLASS_NAME
            KotlinProjectionAbiValueKind.MappedVectorView -> WINRT_READ_ONLY_LIST_PROJECTION_CLASS_NAME
            KotlinProjectionAbiValueKind.MappedMap -> WINRT_DICTIONARY_PROJECTION_CLASS_NAME
            KotlinProjectionAbiValueKind.MappedMapView -> WINRT_READ_ONLY_DICTIONARY_PROJECTION_CLASS_NAME
            else -> return null
        }
        val typeArguments = parameterBinding.typeBinding.typeArguments
        val adapterArguments = when (parameterBinding.typeBinding.kind) {
            KotlinProjectionAbiValueKind.MappedIterable,
            KotlinProjectionAbiValueKind.MappedVector,
            KotlinProjectionAbiValueKind.MappedVectorView ->
                listOf(collectionReferenceAdapterCode(typeArguments.singleOrNull() ?: return null))
            KotlinProjectionAbiValueKind.MappedMap,
            KotlinProjectionAbiValueKind.MappedMapView ->
                listOf(
                    collectionReferenceAdapterCode(typeArguments.getOrNull(0) ?: return null),
                    collectionReferenceAdapterCode(typeArguments.getOrNull(1) ?: return null),
                )
            else -> return null
        }
        return KotlinProjectionAbiMarshalerPlan(
            name = parameterName,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%L.abi", abiLocalName),
            scopeOpeners = listOf(
                CodeBlock.builder()
                    .add("%T.createMarshaler(%L", projectionClass, parameterName)
                    .apply { adapterArguments.forEach { add(", %L", it) } }
                    .add(")!!.use { %L ->", abiLocalName)
                    .build(),
            ),
        )
    }

    private fun collectionReferenceAdapterCode(
        typeBinding: KotlinProjectionAbiTypeBinding,
    ): CodeBlock? {
        when (typeBinding.kind) {
            KotlinProjectionAbiValueKind.ProjectedRuntimeClass,
            KotlinProjectionAbiValueKind.ProjectedInterface,
            KotlinProjectionAbiValueKind.UnknownReference,
            KotlinProjectionAbiValueKind.InspectableReference -> Unit
            else -> return null
        }
        val projectedType = resolveTypeName(typeBinding.resolvedTypeName)
        val projectedTypeName = typeBinding.resolvedTypeName
        val projector = when (typeBinding.kind) {
            KotlinProjectionAbiValueKind.ProjectedRuntimeClass ->
                CodeBlock.of("%T.Metadata.wrap(it!!.asInspectable())", projectedType)
            KotlinProjectionAbiValueKind.ProjectedInterface ->
                CodeBlock.of("%T.Metadata.wrap(it!!)", projectedType)
            KotlinProjectionAbiValueKind.UnknownReference ->
                CodeBlock.of("it!!")
            KotlinProjectionAbiValueKind.InspectableReference ->
                CodeBlock.of("it!!.asInspectable()")
            else -> return null
        }
        val marshaller = when (typeBinding.kind) {
            KotlinProjectionAbiValueKind.ProjectedRuntimeClass,
            KotlinProjectionAbiValueKind.ProjectedInterface ->
                CodeBlock.of("%T((it as %T).nativeObject.getRefPointer())", IUNKNOWN_REFERENCE_CLASS_NAME, IWINRT_OBJECT_CLASS_NAME)
            KotlinProjectionAbiValueKind.UnknownReference,
            KotlinProjectionAbiValueKind.InspectableReference ->
                CodeBlock.of("%T(it.getRefPointer())", IUNKNOWN_REFERENCE_CLASS_NAME)
            else -> return null
        }
        return CodeBlock.of(
            "%T<%T>(projectedTypeName = %S, typeSignature = %T.object_(), projector = { %L }, marshaller = { %L })",
            WINRT_REFERENCE_VALUE_ADAPTER_CLASS_NAME,
            projectedType,
            projectedTypeName,
            WINRT_TYPE_SIGNATURE_CLASS_NAME,
            projector,
            marshaller,
        )
    }

    private fun bindableCollectionReturnReadback(
        returnBinding: KotlinProjectionAbiTypeBinding,
    ): CodeBlock? {
        val projectionClass = when (returnBinding.kind) {
            KotlinProjectionAbiValueKind.MappedBindableIterable -> WINRT_BINDABLE_ITERABLE_PROJECTION_CLASS_NAME
            KotlinProjectionAbiValueKind.MappedBindableVector -> WINRT_BINDABLE_VECTOR_PROJECTION_CLASS_NAME
            KotlinProjectionAbiValueKind.MappedBindableVectorView -> WINRT_BINDABLE_VECTOR_VIEW_PROJECTION_CLASS_NAME
            else -> return null
        }
        return CodeBlock.of(
            "return %T.fromAbi(%T.readPointer(__resultOut)) ?: error(%S)\n",
            projectionClass,
            PLATFORM_ABI_CLASS_NAME,
            "Expected non-null bindable collection from ABI return for ${returnBinding.resolvedTypeName}.",
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
            abiArgumentExpression = CodeBlock.of("%L.abiValue%L", parameterBinding.name, abiIntegralArgumentConversionSuffix(integralType)),
        )
    }

    private fun delegateParameterMarshaler(
        parameterBinding: KotlinProjectionAbiParameterBinding,
    ): KotlinProjectionAbiMarshalerPlan? {
        val invokeShape = outboundDelegateInvokeShape(parameterBinding.typeBinding) ?: return null
        if (!invokeShape.isSupportedOutboundDelegateShape()) {
            return null
        }
        val delegateIid = delegateInterfaceIdCode(parameterBinding.typeBinding, invokeShape) ?: return null
        val handleName = "__${parameterBinding.name}Handle"
        val abiReferenceName = "__${parameterBinding.name}Abi"
        return KotlinProjectionAbiMarshalerPlan(
            name = parameterBinding.name,
            typeBinding = parameterBinding.typeBinding,
            isReturn = false,
            abiArgumentExpression = CodeBlock.of("%L.pointer", abiReferenceName),
            scopeOpeners = listOf(
                CodeBlock.of(
                    "%T.createDelegate(iid = %L, parameterKinds = %L, returnKind = %L) { __args ->\n%L(%L)\n}.use { %L ->",
                    WINRT_DELEGATE_BRIDGE_CLASS_NAME,
                    delegateIid,
                    delegateParameterKindsCode(invokeShape.parameterBindings),
                    delegateInvokeReturnKindCode(invokeShape.returnBinding),
                    parameterBinding.name,
                    delegateCallbackArgumentCodeList(invokeShape.parameterBindings),
                    handleName,
                ),
                CodeBlock.of("%L.createReference().use { %L ->", handleName, abiReferenceName),
            ),
        )
    }

    private fun outboundDelegateInvokeShape(
        typeBinding: KotlinProjectionAbiTypeBinding,
    ): KotlinProjectionDelegateInvokeShape? {
        val invokeShape = typeBinding.delegateInvokeShape ?: return null
        if (typeBinding.resolvedTypeName == "Windows.Foundation.EventHandler" && typeBinding.typeArguments.size == 1) {
            return invokeShape.copy(
                parameterBindings = listOf(
                    KotlinProjectionAbiParameterBinding(
                        "sender",
                        KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Object, "Any", "System.Object"),
                    ),
                    KotlinProjectionAbiParameterBinding("args", typeBinding.typeArguments[0]),
                ),
            )
        }
        if (typeBinding.resolvedTypeName == "Windows.Foundation.TypedEventHandler" && typeBinding.typeArguments.size == 2) {
            return invokeShape.copy(
                parameterBindings = listOf(
                    KotlinProjectionAbiParameterBinding("sender", typeBinding.typeArguments[0]),
                    KotlinProjectionAbiParameterBinding("args", typeBinding.typeArguments[1]),
                ),
            )
        }
        return invokeShape
    }

    private fun delegateInterfaceIdCode(
        typeBinding: KotlinProjectionAbiTypeBinding,
        invokeShape: KotlinProjectionDelegateInvokeShape,
    ): CodeBlock? {
        val delegateIid = invokeShape.interfaceId ?: return null
        if (typeBinding.typeArguments.isEmpty()) {
            return CodeBlock.of("%T(%S)", GUID_CLASS_NAME, delegateIid.toString())
        }
        val argumentSignatures = typeBinding.typeArguments.map { typeArgument ->
            abiTypeSignature(typeArgument) ?: return null
        }
        return CodeBlock.builder()
            .add("%T.createFromParameterizedInterface(%T(%S)", PARAMETERIZED_INTERFACE_ID_CLASS_NAME, GUID_CLASS_NAME, delegateIid.toString())
            .apply {
                argumentSignatures.forEach { signature ->
                    add(", %L", signature)
                }
            }
            .add(")")
            .build()
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

    private fun abiResultAllocationForIntegralType(type: WinRtIntegralType): CodeBlock =
        when (type) {
            WinRtIntegralType.Int8,
            WinRtIntegralType.UInt8 -> CodeBlock.of("%T.allocateInt8Slot(__scope)", PLATFORM_ABI_CLASS_NAME)
            WinRtIntegralType.Int16,
            WinRtIntegralType.UInt16 -> CodeBlock.of("%T.allocateBytes(__scope, 2)", PLATFORM_ABI_CLASS_NAME)
            WinRtIntegralType.Int32,
            WinRtIntegralType.UInt32 -> CodeBlock.of("%T.allocateInt32Slot(__scope)", PLATFORM_ABI_CLASS_NAME)
            WinRtIntegralType.Int64,
            WinRtIntegralType.UInt64 -> CodeBlock.of("%T.allocateInt64Slot(__scope)", PLATFORM_ABI_CLASS_NAME)
        }

    private fun abiIntegralArgumentConversionSuffix(type: WinRtIntegralType): String =
        integralAbiDescriptor(type).argumentConversionSuffix

    private fun abiIntegralReadbackExpression(type: WinRtIntegralType): CodeBlock =
        when (type) {
            WinRtIntegralType.Int8 -> CodeBlock.of("%T.readInt8(__resultOut)", PLATFORM_ABI_CLASS_NAME)
            WinRtIntegralType.UInt8 -> CodeBlock.of("%T.readInt8(__resultOut).toUByte()", PLATFORM_ABI_CLASS_NAME)
            WinRtIntegralType.Int16 -> CodeBlock.of("%T.readInt16(__resultOut)", PLATFORM_ABI_CLASS_NAME)
            WinRtIntegralType.UInt16 -> CodeBlock.of("%T.readInt16(__resultOut).toUShort()", PLATFORM_ABI_CLASS_NAME)
            WinRtIntegralType.Int32 -> CodeBlock.of("%T.readInt32(__resultOut)", PLATFORM_ABI_CLASS_NAME)
            WinRtIntegralType.UInt32 -> CodeBlock.of("%T.readInt32(__resultOut).toUInt()", PLATFORM_ABI_CLASS_NAME)
            WinRtIntegralType.Int64 -> CodeBlock.of("%T.readInt64(__resultOut)", PLATFORM_ABI_CLASS_NAME)
            WinRtIntegralType.UInt64 -> CodeBlock.of("%T.readInt64(__resultOut).toULong()", PLATFORM_ABI_CLASS_NAME)
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
        KotlinProjectionAbiValueKind.Boolean -> CodeBlock.of("%T.BOOLEAN", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int8 -> CodeBlock.of("%T.INT8", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.UInt8 -> CodeBlock.of("%T.UINT8", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int16 -> CodeBlock.of("%T.INT16", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.UInt16 -> CodeBlock.of("%T.UINT16", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int32 -> CodeBlock.of("%T.INT32", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.UInt32 -> CodeBlock.of("%T.UINT32", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int64 -> CodeBlock.of("%T.INT64", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.UInt64 -> CodeBlock.of("%T.UINT64", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.Float -> CodeBlock.of("%T.FLOAT", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.Double -> CodeBlock.of("%T.DOUBLE", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.Char16 -> CodeBlock.of("%T.CHAR16", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.Enum -> delegateEnumValueKindCode(typeBinding)
        KotlinProjectionAbiValueKind.Object,
        KotlinProjectionAbiValueKind.ProjectedInterface,
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass,
        KotlinProjectionAbiValueKind.MappedAsyncAction,
        KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress,
        KotlinProjectionAbiValueKind.MappedAsyncOperation,
        KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress,
        KotlinProjectionAbiValueKind.UnknownReference,
        KotlinProjectionAbiValueKind.InspectableReference -> CodeBlock.of("%T.OBJECT", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        else -> error("Unsupported delegate callback ABI kind: ${typeBinding.describeAbiKind()}")
    }

    private fun delegateEnumValueKindCode(typeBinding: KotlinProjectionAbiTypeBinding): CodeBlock =
        when (typeBinding.enumUnderlyingType) {
            WinRtIntegralType.Int8 -> CodeBlock.of("%T.INT8", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
            WinRtIntegralType.UInt8 -> CodeBlock.of("%T.UINT8", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
            WinRtIntegralType.Int16 -> CodeBlock.of("%T.INT16", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
            WinRtIntegralType.UInt16 -> CodeBlock.of("%T.UINT16", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
            WinRtIntegralType.Int32 -> CodeBlock.of("%T.INT32", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
            WinRtIntegralType.UInt32 -> CodeBlock.of("%T.UINT32", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
            WinRtIntegralType.Int64 -> CodeBlock.of("%T.INT64", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
            WinRtIntegralType.UInt64 -> CodeBlock.of("%T.UINT64", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
            null -> error("Delegate enum ABI kind requires enum underlying type for ${typeBinding.resolvedTypeName}")
        }

    private fun delegateInvokeValueKindCode(typeBinding: KotlinProjectionAbiTypeBinding): CodeBlock = when (typeBinding.kind) {
        KotlinProjectionAbiValueKind.Unit -> CodeBlock.of("%T.UNIT", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.String -> CodeBlock.of("%T.HSTRING", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.Boolean -> CodeBlock.of("%T.BOOLEAN", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int8 -> CodeBlock.of("%T.INT8", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.UInt8 -> CodeBlock.of("%T.UINT8", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int16 -> CodeBlock.of("%T.INT16", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.UInt16 -> CodeBlock.of("%T.UINT16", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.Char16 -> CodeBlock.of("%T.CHAR16", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int32 -> CodeBlock.of("%T.INT32", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.UInt32 -> CodeBlock.of("%T.UINT32", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int64 -> CodeBlock.of("%T.INT64", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.UInt64 -> CodeBlock.of("%T.UINT64", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.Float -> CodeBlock.of("%T.FLOAT", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.Double -> CodeBlock.of("%T.DOUBLE", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.Enum -> delegateEnumValueKindCode(typeBinding)
        KotlinProjectionAbiValueKind.Object -> CodeBlock.of("%T.OBJECT", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.ProjectedInterface -> CodeBlock.of("%T.IUNKNOWN", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass -> CodeBlock.of("%T.IINSPECTABLE", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.MappedAsyncAction,
        KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress,
        KotlinProjectionAbiValueKind.MappedAsyncOperation,
        KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress -> CodeBlock.of("%T.IUNKNOWN", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME)
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
        KotlinProjectionAbiValueKind.UnknownReference,
        KotlinProjectionAbiValueKind.InspectableReference,
        KotlinProjectionAbiValueKind.MappedAsyncAction,
        KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress,
        KotlinProjectionAbiValueKind.MappedAsyncOperation,
        KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress -> CodeBlock.of("%L", parameterBinding.name)
        KotlinProjectionAbiValueKind.ProjectedInterface,
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass -> CodeBlock.of("%L", parameterBinding.name)
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
        KotlinProjectionAbiValueKind.Int8 -> CodeBlock.of("return %L as Byte\n", nativeInvokeExpression)
        KotlinProjectionAbiValueKind.UInt8 -> CodeBlock.of("return %L as UByte\n", nativeInvokeExpression)
        KotlinProjectionAbiValueKind.Int16 -> CodeBlock.of("return %L as Short\n", nativeInvokeExpression)
        KotlinProjectionAbiValueKind.UInt16 -> CodeBlock.of("return %L as UShort\n", nativeInvokeExpression)
        KotlinProjectionAbiValueKind.Char16 -> CodeBlock.of("return %L as Char\n", nativeInvokeExpression)
        KotlinProjectionAbiValueKind.Int32 -> CodeBlock.of("return %L as Int\n", nativeInvokeExpression)
        KotlinProjectionAbiValueKind.UInt32 -> CodeBlock.of("return %L as UInt\n", nativeInvokeExpression)
        KotlinProjectionAbiValueKind.Int64 -> CodeBlock.of("return %L as Long\n", nativeInvokeExpression)
        KotlinProjectionAbiValueKind.UInt64 -> CodeBlock.of("return %L as ULong\n", nativeInvokeExpression)
        KotlinProjectionAbiValueKind.Float -> CodeBlock.of("return %L as Float\n", nativeInvokeExpression)
        KotlinProjectionAbiValueKind.Double -> CodeBlock.of("return %L as Double\n", nativeInvokeExpression)
        KotlinProjectionAbiValueKind.Enum -> {
            val enumType = resolveTypeName(returnBinding.resolvedTypeName)
            when (returnBinding.enumUnderlyingType) {
                WinRtIntegralType.Int8 -> CodeBlock.of("return %T.Metadata.fromAbi(%L as Byte)\n", enumType, nativeInvokeExpression)
                WinRtIntegralType.UInt8 -> CodeBlock.of("return %T.Metadata.fromAbi(%L as UByte)\n", enumType, nativeInvokeExpression)
                WinRtIntegralType.Int16 -> CodeBlock.of("return %T.Metadata.fromAbi(%L as Short)\n", enumType, nativeInvokeExpression)
                WinRtIntegralType.UInt16 -> CodeBlock.of("return %T.Metadata.fromAbi(%L as UShort)\n", enumType, nativeInvokeExpression)
                WinRtIntegralType.Int32 -> CodeBlock.of("return %T.Metadata.fromAbi(%L as Int)\n", enumType, nativeInvokeExpression)
                WinRtIntegralType.UInt32 -> CodeBlock.of("return %T.Metadata.fromAbi(%L as UInt)\n", enumType, nativeInvokeExpression)
                WinRtIntegralType.Int64 -> CodeBlock.of("return %T.Metadata.fromAbi(%L as Long)\n", enumType, nativeInvokeExpression)
                WinRtIntegralType.UInt64 -> CodeBlock.of("return %T.Metadata.fromAbi(%L as ULong)\n", enumType, nativeInvokeExpression)
                null -> error("Delegate enum return binding requires enum underlying type for ${returnBinding.resolvedTypeName}")
            }
        }
        KotlinProjectionAbiValueKind.ProjectedInterface -> {
            val projectedType = resolveTypeName(returnBinding.resolvedTypeName)
            CodeBlock.of("return %T.Metadata.wrap(%L as %T)\n", projectedType, nativeInvokeExpression, IUNKNOWN_REFERENCE_CLASS_NAME)
        }
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass -> {
            val projectedType = resolveTypeName(returnBinding.resolvedTypeName)
            CodeBlock.of("return %T.Metadata.wrap(%L as %T)\n", projectedType, nativeInvokeExpression, IINSPECTABLE_REFERENCE_CLASS_NAME)
        }
        KotlinProjectionAbiValueKind.UnknownReference ->
            CodeBlock.of("return %L as %T\n", nativeInvokeExpression, IUNKNOWN_REFERENCE_CLASS_NAME)
        KotlinProjectionAbiValueKind.InspectableReference ->
            CodeBlock.of("return %L as %T\n", nativeInvokeExpression, IINSPECTABLE_REFERENCE_CLASS_NAME)
        KotlinProjectionAbiValueKind.MappedAsyncAction ->
            CodeBlock.of("return %L as %T\n", nativeInvokeExpression, WINRT_ASYNC_ACTION_REFERENCE_CLASS_NAME)
        KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress -> {
            val progressType = returnBinding.typeArguments.singleOrNull()?.let { resolveTypeName(it.typeName) } ?: ANY.copy(nullable = true)
            CodeBlock.of("return %L as %T\n", nativeInvokeExpression, WINRT_ASYNC_ACTION_WITH_PROGRESS_REFERENCE_CLASS_NAME.parameterizedBy(progressType))
        }
        KotlinProjectionAbiValueKind.MappedAsyncOperation -> {
            val resultType = returnBinding.typeArguments.singleOrNull()?.let { resolveTypeName(it.typeName) } ?: ANY.copy(nullable = true)
            CodeBlock.of("return %L as %T\n", nativeInvokeExpression, WINRT_ASYNC_OPERATION_REFERENCE_CLASS_NAME.parameterizedBy(resultType))
        }
        KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress -> {
            val resultType = returnBinding.typeArguments.getOrNull(0)?.let { resolveTypeName(it.typeName) } ?: ANY.copy(nullable = true)
            val progressType = returnBinding.typeArguments.getOrNull(1)?.let { resolveTypeName(it.typeName) } ?: ANY.copy(nullable = true)
            CodeBlock.of("return %L as %T\n", nativeInvokeExpression, WINRT_ASYNC_OPERATION_WITH_PROGRESS_REFERENCE_CLASS_NAME.parameterizedBy(resultType, progressType))
        }
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
        KotlinProjectionAbiValueKind.Boolean -> CodeBlock.of("__args[%L] as Boolean", index)
        KotlinProjectionAbiValueKind.Int8 -> CodeBlock.of("__args[%L] as Byte", index)
        KotlinProjectionAbiValueKind.UInt8 -> CodeBlock.of("__args[%L] as UByte", index)
        KotlinProjectionAbiValueKind.Int16 -> CodeBlock.of("__args[%L] as Short", index)
        KotlinProjectionAbiValueKind.UInt16 -> CodeBlock.of("__args[%L] as UShort", index)
        KotlinProjectionAbiValueKind.Char16 -> CodeBlock.of("__args[%L] as Char", index)
        KotlinProjectionAbiValueKind.Int32 -> CodeBlock.of("__args[%L] as Int", index)
        KotlinProjectionAbiValueKind.UInt32 -> CodeBlock.of("__args[%L] as UInt", index)
        KotlinProjectionAbiValueKind.Int64 -> CodeBlock.of("__args[%L] as Long", index)
        KotlinProjectionAbiValueKind.UInt64 -> CodeBlock.of("__args[%L] as ULong", index)
        KotlinProjectionAbiValueKind.Float -> CodeBlock.of("__args[%L] as Float", index)
        KotlinProjectionAbiValueKind.Double -> CodeBlock.of("__args[%L] as Double", index)
        KotlinProjectionAbiValueKind.Enum -> delegateEnumCallbackArgumentCode(index, typeBinding)
        KotlinProjectionAbiValueKind.ProjectedInterface -> CodeBlock.of(
            "%T.Metadata.wrap(__args[%L] as %T)",
            resolveTypeName(typeBinding.resolvedTypeName),
            index,
            IUNKNOWN_REFERENCE_CLASS_NAME,
        )
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass -> CodeBlock.of(
            "%T.Metadata.wrap((__args[%L] as %T).asInspectable())",
            resolveTypeName(typeBinding.resolvedTypeName),
            index,
            IUNKNOWN_REFERENCE_CLASS_NAME,
        )
        KotlinProjectionAbiValueKind.UnknownReference -> CodeBlock.of("__args[%L] as %T", index, IUNKNOWN_REFERENCE_CLASS_NAME)
        KotlinProjectionAbiValueKind.MappedAsyncAction -> CodeBlock.of("__args[%L] as %T", index, WINRT_ASYNC_ACTION_REFERENCE_CLASS_NAME)
        KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress -> {
            val progressType = typeBinding.typeArguments.singleOrNull()?.let { resolveTypeName(it.typeName) } ?: ANY.copy(nullable = true)
            CodeBlock.of("__args[%L] as %T", index, WINRT_ASYNC_ACTION_WITH_PROGRESS_REFERENCE_CLASS_NAME.parameterizedBy(progressType))
        }
        KotlinProjectionAbiValueKind.MappedAsyncOperation -> {
            val resultType = typeBinding.typeArguments.singleOrNull()?.let { resolveTypeName(it.typeName) } ?: ANY.copy(nullable = true)
            CodeBlock.of("__args[%L] as %T", index, WINRT_ASYNC_OPERATION_REFERENCE_CLASS_NAME.parameterizedBy(resultType))
        }
        KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress -> {
            val resultType = typeBinding.typeArguments.getOrNull(0)?.let { resolveTypeName(it.typeName) } ?: ANY.copy(nullable = true)
            val progressType = typeBinding.typeArguments.getOrNull(1)?.let { resolveTypeName(it.typeName) } ?: ANY.copy(nullable = true)
            CodeBlock.of("__args[%L] as %T", index, WINRT_ASYNC_OPERATION_WITH_PROGRESS_REFERENCE_CLASS_NAME.parameterizedBy(resultType, progressType))
        }
        KotlinProjectionAbiValueKind.Object,
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
            WinRtIntegralType.Int8 -> CodeBlock.of("%T.Metadata.fromAbi(__args[%L] as Byte)", enumType, index)
            WinRtIntegralType.UInt8 -> CodeBlock.of("%T.Metadata.fromAbi(__args[%L] as UByte)", enumType, index)
            WinRtIntegralType.Int16 -> CodeBlock.of("%T.Metadata.fromAbi(__args[%L] as Short)", enumType, index)
            WinRtIntegralType.UInt16 -> CodeBlock.of("%T.Metadata.fromAbi(__args[%L] as UShort)", enumType, index)
            WinRtIntegralType.Int32 -> CodeBlock.of("%T.Metadata.fromAbi(__args[%L] as Int)", enumType, index)
            WinRtIntegralType.UInt32 -> CodeBlock.of("%T.Metadata.fromAbi(__args[%L] as UInt)", enumType, index)
            WinRtIntegralType.Int64 -> CodeBlock.of("%T.Metadata.fromAbi(__args[%L] as Long)", enumType, index)
            WinRtIntegralType.UInt64 -> CodeBlock.of("%T.Metadata.fromAbi(__args[%L] as ULong)", enumType, index)
        }
    }

    private fun renderInlineAbiInvocation(
        invokeTargetExpression: String,
        slotExpression: String,
        callPlan: KotlinProjectionAbiCallPlan,
    ): CodeBlock? =
        renderInlineAbiInvocation(invokeTargetExpression, CodeBlock.of("%L", slotExpression), callPlan)

    private fun renderInlineAbiInvocation(
        invokeTargetExpression: String,
        slotExpression: CodeBlock,
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
            code.add("%T.confinedScope().use { __scope ->\n", PLATFORM_ABI_CLASS_NAME)
            code.indent()
            resultMarshaler.resultLocalDeclarations?.let { declarations ->
                code.add("%L", declarations)
            } ?: code.addStatement("val __resultOut = %L", requireNotNull(resultMarshaler.resultAllocation))
        }
        val abiArguments = callPlan.parameterMarshalers.flatMap { marshaler ->
            listOf(marshaler.abiArgumentExpression) + marshaler.extraAbiArgumentExpressions
        } + if (resultMarshaler != null) {
            listOf(resultMarshaler.abiArgumentExpression) + resultMarshaler.extraAbiArgumentExpressions
        } else {
            emptyList()
        }
        code.add("val __hr = ")
        code.add(
            renderComVtableInvocation(
                invokeTargetExpression = invokeTargetExpression,
                slotExpression = slotExpression,
                abiArguments = abiArguments,
            ),
        )
        code.add("\n")
        code.addStatement("%T(__hr).requireSuccess()", HRESULT_CLASS_NAME)
        callPlan.parameterMarshalers.flatMap { it.postCallStatements }.forEach { postCallStatement ->
            code.add("%L\n", postCallStatement)
        }
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

    private fun renderComVtableInvocation(
        invokeTargetExpression: String,
        slotExpression: CodeBlock,
        abiArguments: List<CodeBlock>,
    ): CodeBlock {
        val builder = CodeBlock.builder()
        if (abiArguments.isEmpty()) {
            builder.add(
                "%T.invoke(instance = %L.pointer, slot = %L)",
                COM_VTABLE_INVOKER_CLASS_NAME,
                invokeTargetExpression,
                slotExpression,
            )
        } else if (abiArguments.size <= 6) {
            builder.add(
                "%T.invokeArgs(instance = %L.pointer, slot = %L",
                COM_VTABLE_INVOKER_CLASS_NAME,
                invokeTargetExpression,
                slotExpression,
            )
            abiArguments.forEachIndexed { index, argument ->
                builder.add(", arg%L = %L", index, argument)
            }
            builder.add(")")
        } else {
            builder.add(
                "%T.invokeGenericArgs(instance = %L.pointer, slot = %L",
                COM_VTABLE_INVOKER_CLASS_NAME,
                invokeTargetExpression,
                slotExpression,
            )
            abiArguments.forEach { argument ->
                builder.add(", %L", argument)
            }
            builder.add(")")
        }
        return builder.build()
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
            eventInvokeDescriptor = plan.eventInvokeDescriptors.firstOrNull { it.eventName == event.name && !it.isStatic },
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
            eventInvokeDescriptor = plan.eventInvokeDescriptors.firstOrNull { it.eventName == event.name && it.isStatic },
            addInvocation = renderBoundStaticInvocation(addBinding),
            removeInvocation = renderBoundStaticInvocation(removeBinding),
        )
    }

    private fun buildBoundEventFunctions(
        event: WinRtEventDefinition,
        eventInvokeDescriptor: WinRtEventInvokeDescriptor?,
        addInvocation: CodeBlock,
        removeInvocation: CodeBlock,
    ): List<FunSpec> {
        val typeName = resolveTypeName(eventInvokeDescriptor?.delegateTypeName ?: event.delegateTypeName)
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

    private fun hasDefaultComposableFactoryConstructor(plan: KotlinTypeProjectionPlan): Boolean {
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

    private fun renderDefaultComposableFactoryCreateInstance(plan: KotlinTypeProjectionPlan): FunSpec {
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

    private fun appendDescriptorHandoffCompanionMembers(
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

    private fun resolveTypeName(typeName: String): TypeName {
        val trimmed = typeName.trim()
        val genericStart = trimmed.indexOf('<')
        if (genericStart >= 0 && trimmed.endsWith('>')) {
            val rawType = trimmed.substring(0, genericStart)
            val arguments = splitGenericArguments(trimmed.substring(genericStart + 1, trimmed.length - 1))
                .map(::resolveTypeName)
            if (rawType == "Array") {
                return Array::class.asClassName().parameterizedBy(arguments)
            }
            mappedTypeByAbiName(rawType)?.let { mappedType ->
                return mappedType.projectedTypeResolver(arguments)
            }
            val rawClassName = if ('.' in rawType) projectionClassName(rawType) else ClassName.bestGuess(rawType)
            return rawClassName.parameterizedBy(arguments)
        }

        mappedTypeByAbiName(trimmed)?.let { mappedType ->
            return mappedType.projectedTypeResolver(emptyList())
        }

        return when (trimmed) {
            "Unit" -> UNIT
            "Any",
            "System.Object" -> IINSPECTABLE_REFERENCE_CLASS_NAME
            "String" -> String::class.asClassName()
            "Int" -> Int::class.asClassName()
            "UInt" -> UInt::class.asClassName()
            "Boolean" -> Boolean::class.asClassName()
            "Byte" -> Byte::class.asClassName()
            "SByte",
            "Int8" -> Byte::class.asClassName()
            "UInt8" -> UByte::class.asClassName()
            "Short" -> Short::class.asClassName()
            "Int16" -> Short::class.asClassName()
            "UShort" -> UShort::class.asClassName()
            "UInt16" -> UShort::class.asClassName()
            "Long" -> Long::class.asClassName()
            "Int64" -> Long::class.asClassName()
            "ULong",
            "UInt64" -> ULong::class.asClassName()
            "Float" -> Float::class.asClassName()
            "Double" -> Double::class.asClassName()
            "Char" -> Char::class.asClassName()
            "Guid",
            "System.Guid" -> GUID_CLASS_NAME
            IUNKNOWN_REFERENCE_CLASS_NAME.simpleName,
            "io.github.kitectlab.winrt.runtime.IUnknownReference" -> IUNKNOWN_REFERENCE_CLASS_NAME
            IINSPECTABLE_REFERENCE_CLASS_NAME.simpleName,
            "io.github.kitectlab.winrt.runtime.IInspectableReference" -> IINSPECTABLE_REFERENCE_CLASS_NAME
            IWINRT_OBJECT_CLASS_NAME.simpleName,
            "io.github.kitectlab.winrt.runtime.IWinRTObject" -> IWINRT_OBJECT_CLASS_NAME
            else -> if ('.' in trimmed) projectionClassName(trimmed) else ClassName.bestGuess(trimmed)
        }
    }

    private fun resolveIntegralTypeName(type: WinRtIntegralType): TypeName =
        integralAbiDescriptor(type).kotlinTypeName

    private fun integralLiteral(valueBits: ULong, type: WinRtIntegralType): CodeBlock =
        integralAbiDescriptor(type).literalRenderer(valueBits)

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
    private val supportRenderer: KotlinProjectionSupportRenderer = KotlinProjectionSupportRenderer(),
    private val emitSupportFiles: Boolean = false,
    private val projectionContext: WinRtMetadataProjectionContext = WinRtMetadataProjectionContext(sources = emptyList()),
) {
    fun generate(model: WinRtMetadataModel): List<KotlinProjectionFile> {
        val normalizedModel = model.normalized()
        val plans = planner.plan(normalizedModel)
        val projectionFiles = plans.map(renderer::render)
        if (!emitSupportFiles) {
            return projectionFiles
        }
        return projectionFiles + supportRenderer.render(normalizedModel, plans, projectionContext)
    }

    fun generateTo(model: WinRtMetadataModel, outputRoot: Path): Int {
        val normalizedModel = model.normalized()
        val plans = planner.plan(normalizedModel)
        var written = 0
        plans.forEach { plan ->
            renderer.render(plan).writeTo(outputRoot)
            written += 1
        }
        if (emitSupportFiles) {
            supportRenderer.render(normalizedModel, plans, projectionContext).forEach { file ->
                file.writeTo(outputRoot)
                written += 1
            }
        }
        return written
    }
}

class KotlinProjectionSupportRenderer {
    fun render(
        model: WinRtMetadataModel,
        plans: List<KotlinTypeProjectionPlan>,
        context: WinRtMetadataProjectionContext = WinRtMetadataProjectionContext(sources = emptyList()),
    ): List<KotlinProjectionFile> {
        val inventory = WinRtMetadataProjectionInventoryBuilder.create(model, context).build()
        val semanticHelpers = model.semanticHelpers()
        val genericInstantiationWriters = semanticHelpers.genericInstantiationWriterDescriptors(context)
        return listOfNotNull(
            renderGenericAbiRegistry(inventory.genericAbiInventory),
            renderGenericTypeInstantiations(genericInstantiationWriters),
            renderEventProjectionHelpers(model, inventory),
            renderAbiImplementationPlan(plans),
            renderTypeShapeWriterPlan(inventory, plans),
            renderNamespaceAdditions(inventory),
        )
    }

    private fun renderGenericAbiRegistry(inventory: WinRtGenericAbiInventory): KotlinProjectionFile? {
        if (inventory.genericAbiDelegates.isEmpty() && inventory.derivedGenericInterfaces.isEmpty()) {
            return null
        }
        val contents = buildString {
            appendHeader("WinRTGenericAbiRegistry")
            appendLine("internal data class GenericAbiDelegateEntry(")
            appendLine("    val name: String,")
            appendLine("    val sourceGenericType: String,")
            appendLine("    val operation: String,")
            appendLine("    val declaration: String,")
            appendLine("    val abiParameterTypes: List<String>,")
            appendLine("    val typeArrayShape: List<String>,")
            appendLine(")")
            appendLine()
            appendLine("internal object WinRTGenericAbiRegistry {")
            appendStringList("DERIVED_GENERIC_INTERFACES", inventory.derivedGenericInterfaces)
            appendLine("    val GENERIC_ABI_DELEGATES: List<GenericAbiDelegateEntry> = listOf(")
            inventory.genericAbiDelegates.forEach { delegate ->
                appendLine("        GenericAbiDelegateEntry(")
                appendLine("            name = ${delegate.abiDelegateName.kotlinString()},")
                appendLine("            sourceGenericType = ${delegate.sourceGenericType.typeName.kotlinString()},")
                appendLine("            operation = ${delegate.operationName.kotlinString()},")
                appendLine("            declaration = ${delegate.declaration.kotlinString()},")
                appendLine("            abiParameterTypes = ${delegate.abiParameterTypeNames.kotlinListLiteral()},")
                appendLine("            typeArrayShape = ${delegate.typeArrayShape.kotlinListLiteral()},")
                appendLine("        ),")
            }
            appendLine("    )")
            appendLine("    val DELEGATES_BY_NAME: Map<String, GenericAbiDelegateEntry> = GENERIC_ABI_DELEGATES.associateBy { it.name }")
            appendLine("    val DELEGATES_BY_SOURCE_TYPE: Map<String, List<GenericAbiDelegateEntry>> = GENERIC_ABI_DELEGATES.groupBy { it.sourceGenericType }")
            appendLine("    val DERIVED_GENERIC_INTERFACE_SET: Set<String> = DERIVED_GENERIC_INTERFACES.toSet()")
            appendLine()
            appendLine("    fun delegateNamed(name: String): GenericAbiDelegateEntry? = DELEGATES_BY_NAME[name]")
            appendLine()
            appendLine("    fun delegatesForSourceType(sourceGenericType: String): List<GenericAbiDelegateEntry> =")
            appendLine("        DELEGATES_BY_SOURCE_TYPE[sourceGenericType].orEmpty()")
            appendLine()
            appendLine("    fun isDerivedGenericInterface(typeName: String): Boolean =")
            appendLine("        typeName in DERIVED_GENERIC_INTERFACE_SET")
            appendLine()
            appendLine("    fun registerAbiDelegates(register: (typeArrayShape: List<String>, delegateName: String) -> Unit) {")
            appendLine("        GENERIC_ABI_DELEGATES.forEach { entry ->")
            appendLine("            register(entry.typeArrayShape, entry.name)")
            appendLine("        }")
            appendLine("    }")
            appendLine("}")
        }
        return supportFile("WinRTGenericAbiRegistry.kt", contents)
    }

    private fun renderGenericTypeInstantiations(
        descriptors: List<WinRtGenericInstantiationWriterDescriptor>,
    ): KotlinProjectionFile? {
        if (descriptors.isEmpty()) {
            return null
        }
        val contents = buildString {
            appendHeader("WinRTGenericTypeInstantiations")
            appendLine("internal data class GenericTypeInstantiationEntry(")
            appendLine("    val className: String,")
            appendLine("    val sourceType: String,")
            appendLine("    val isDelegate: Boolean,")
            appendLine("    val rcwFunctions: List<String>,")
            appendLine("    val vtableFunctions: List<String>,")
            appendLine("    val propertyAccessors: List<String>,")
            appendLine("    val dependencies: List<String>,")
            appendLine(")")
            appendLine()
            appendLine("internal object WinRTGenericTypeInstantiations {")
            appendLine("    val ENTRIES: List<GenericTypeInstantiationEntry> = listOf(")
            descriptors.forEach { descriptor ->
                appendLine("        GenericTypeInstantiationEntry(")
                appendLine("            className = ${descriptor.instantiationClassName.kotlinString()},")
                appendLine("            sourceType = ${descriptor.sourceTypeName.kotlinString()},")
                appendLine("            isDelegate = ${descriptor.isDelegateInstantiation},")
                appendLine("            rcwFunctions = ${descriptor.rcwFunctionNames.kotlinListLiteral()},")
                appendLine("            vtableFunctions = ${descriptor.vtableFunctionNames.kotlinListLiteral()},")
                appendLine("            propertyAccessors = ${descriptor.propertyAccessorFunctionNames.kotlinListLiteral()},")
                appendLine("            dependencies = ${descriptor.initializationDependencies.kotlinListLiteral()},")
                appendLine("        ),")
            }
            appendLine("    )")
            appendLine("    val ENTRIES_BY_CLASS_NAME: Map<String, GenericTypeInstantiationEntry> = ENTRIES.associateBy { it.className }")
            appendLine("    val ENTRIES_BY_SOURCE_TYPE: Map<String, GenericTypeInstantiationEntry> = ENTRIES.associateBy { it.sourceType }")
            appendLine()
            appendLine("    fun entryForClassName(className: String): GenericTypeInstantiationEntry? =")
            appendLine("        ENTRIES_BY_CLASS_NAME[className]")
            appendLine()
            appendLine("    fun entryForSourceType(sourceType: String): GenericTypeInstantiationEntry? =")
            appendLine("        ENTRIES_BY_SOURCE_TYPE[sourceType]")
            appendLine()
            appendLine("    fun initializeAll(initialize: (GenericTypeInstantiationEntry) -> Unit) {")
            appendLine("        val visited = linkedSetOf<String>()")
            appendLine("        ENTRIES.forEach { initializeWithDependencies(it, visited, initialize) }")
            appendLine("    }")
            appendLine()
            appendLine("    fun initializeDependencies(")
            appendLine("        entry: GenericTypeInstantiationEntry,")
            appendLine("        initialize: (GenericTypeInstantiationEntry) -> Unit,")
            appendLine("    ) {")
            appendLine("        val visited = linkedSetOf(entry.className)")
            appendLine("        entry.dependencies.mapNotNull(ENTRIES_BY_SOURCE_TYPE::get)")
            appendLine("            .forEach { initializeWithDependencies(it, visited, initialize) }")
            appendLine("    }")
            appendLine()
            appendLine("    private fun initializeWithDependencies(")
            appendLine("        entry: GenericTypeInstantiationEntry,")
            appendLine("        visited: MutableSet<String>,")
            appendLine("        initialize: (GenericTypeInstantiationEntry) -> Unit,")
            appendLine("    ) {")
            appendLine("        if (!visited.add(entry.className)) return")
            appendLine("        entry.dependencies.mapNotNull(ENTRIES_BY_SOURCE_TYPE::get)")
            appendLine("            .forEach { initializeWithDependencies(it, visited, initialize) }")
            appendLine("        initialize(entry)")
            appendLine("    }")
            appendLine("}")
        }
        return supportFile("WinRTGenericTypeInstantiations.kt", contents)
    }

    private fun renderEventProjectionHelpers(
        model: WinRtMetadataModel,
        inventory: WinRtMetadataProjectionInventory,
    ): KotlinProjectionFile? {
        val helpers = model.semanticHelpers()
        val subclassDescriptors = model.namespaces
            .flatMap(WinRtNamespace::types)
            .flatMap(helpers::eventHelperSubclassDescriptors)
            .distinctBy { it.eventTypeName to it.ownerTypeName }
            .sortedWith(compareBy({ it.eventTypeName }, { it.ownerTypeName }))
        if (inventory.eventSourceMappings.isEmpty() && subclassDescriptors.isEmpty()) {
            return null
        }
        val contents = buildString {
            appendHeader("WinRTEventProjectionHelpers")
            appendLine("internal data class EventSourceEntry(")
            appendLine("    val eventType: String,")
            appendLine("    val ownerType: String,")
            appendLine("    val sourceClass: String,")
            appendLine("    val abiEventType: String,")
            appendLine("    val genericArguments: List<String>,")
            appendLine(")")
            appendLine()
            appendLine("internal object WinRTEventProjectionHelpers {")
            appendLine("    val EVENT_SOURCES: List<EventSourceEntry> = listOf(")
            subclassDescriptors.forEach { descriptor ->
                appendLine("        EventSourceEntry(")
                appendLine("            eventType = ${descriptor.eventTypeName.kotlinString()},")
                appendLine("            ownerType = ${descriptor.ownerTypeName.kotlinString()},")
                appendLine("            sourceClass = ${descriptor.sourceClassName.kotlinString()},")
                appendLine("            abiEventType = ${descriptor.abiEventTypeName.kotlinString()},")
                appendLine("            genericArguments = ${descriptor.genericArgumentTypeNames.kotlinListLiteral()},")
                appendLine("        ),")
            }
            appendLine("    )")
            appendStringList("EVENT_SOURCE_MAPPING_KEYS", inventory.eventSourceMappings.map { "${it.eventTypeName}->${it.sourceClassName}" })
            appendLine("    val EVENT_SOURCES_BY_EVENT_TYPE: Map<String, List<EventSourceEntry>> = EVENT_SOURCES.groupBy { it.eventType }")
            appendLine("    val EVENT_SOURCES_BY_OWNER_TYPE: Map<String, List<EventSourceEntry>> = EVENT_SOURCES.groupBy { it.ownerType }")
            appendLine()
            appendLine("    fun sourcesForEventType(eventType: String): List<EventSourceEntry> =")
            appendLine("        EVENT_SOURCES_BY_EVENT_TYPE[eventType].orEmpty()")
            appendLine()
            appendLine("    fun sourcesForOwnerType(ownerType: String): List<EventSourceEntry> =")
            appendLine("        EVENT_SOURCES_BY_OWNER_TYPE[ownerType].orEmpty()")
            appendLine()
            appendLine("    fun installEventSources(install: (EventSourceEntry) -> Unit) {")
            appendLine("        EVENT_SOURCES.forEach(install)")
            appendLine("    }")
            appendLine("}")
        }
        return supportFile("WinRTEventProjectionHelpers.kt", contents)
    }

    private fun renderAbiImplementationPlan(plans: List<KotlinTypeProjectionPlan>): KotlinProjectionFile? {
        val abiPlans = plans.filter { plan ->
            plan.typeDeclarationDescriptor.writesAbiDeclaration ||
                plan.typeDeclarationDescriptor.writesImplementationClass ||
                plan.genericAbiClassInitializationDescriptor != null ||
                plan.requiredInterfaceAugmentationDescriptor != null
        }
        if (abiPlans.isEmpty()) {
            return null
        }
        val contents = buildString {
            appendHeader("WinRTAbiImplementationPlan")
            appendLine("internal data class AbiImplementationEntry(")
            appendLine("    val typeName: String,")
            appendLine("    val writesAbi: Boolean,")
            appendLine("    val writesImplementationClass: Boolean,")
            appendLine("    val vtableSlots: List<String>,")
            appendLine("    val genericInvokeSlots: List<String>,")
            appendLine("    val requiredInterfaces: List<String>,")
            appendLine("    val explicitForwards: List<String>,")
            appendLine(")")
            appendLine()
            appendLine("internal object WinRTAbiImplementationPlan {")
            appendLine("    val ENTRIES: List<AbiImplementationEntry> = listOf(")
            abiPlans.sortedBy { it.type.qualifiedName }.forEach { plan ->
                appendLine("        AbiImplementationEntry(")
                appendLine("            typeName = ${plan.type.qualifiedName.kotlinString()},")
                appendLine("            writesAbi = ${plan.typeDeclarationDescriptor.writesAbiDeclaration},")
                appendLine("            writesImplementationClass = ${plan.typeDeclarationDescriptor.writesImplementationClass},")
                appendLine("            vtableSlots = ${plan.abiSlotBindings.map { it.constantName }.kotlinListLiteral()},")
                appendLine("            genericInvokeSlots = ${plan.genericAbiClassInitializationDescriptor?.invokeSlotNames.orEmpty().kotlinListLiteral()},")
                appendLine("            requiredInterfaces = ${plan.requiredInterfaceAugmentationDescriptor?.requiredInterfaceNames.orEmpty().kotlinListLiteral()},")
                appendLine("            explicitForwards = ${plan.requiredInterfaceAugmentationDescriptor?.explicitForwardMemberNames.orEmpty().kotlinListLiteral()},")
                appendLine("        ),")
            }
            appendLine("    )")
            appendLine("    val ENTRIES_BY_TYPE_NAME: Map<String, AbiImplementationEntry> = ENTRIES.associateBy { it.typeName }")
            appendLine()
            appendLine("    fun entryForType(typeName: String): AbiImplementationEntry? =")
            appendLine("        ENTRIES_BY_TYPE_NAME[typeName]")
            appendLine()
            appendLine("    fun requiresAbi(typeName: String): Boolean =")
            appendLine("        entryForType(typeName)?.writesAbi == true")
            appendLine()
            appendLine("    fun installAbiImplementations(install: (AbiImplementationEntry) -> Unit) {")
            appendLine("        ENTRIES.forEach(install)")
            appendLine("    }")
            appendLine()
            appendLine("    fun requiredInterfaceNames(): Set<String> =")
            appendLine("        ENTRIES.flatMap { it.requiredInterfaces }.toSet()")
            appendLine("}")
        }
        return supportFile("WinRTAbiImplementationPlan.kt", contents)
    }

    private fun renderTypeShapeWriterPlan(
        inventory: WinRtMetadataProjectionInventory,
        plans: List<KotlinTypeProjectionPlan>,
    ): KotlinProjectionFile? {
        if (plans.isEmpty()) {
            return null
        }
        val contents = buildString {
            appendHeader("WinRTTypeShapeWriterPlan")
            appendLine("internal data class TypeShapeEntry(")
            appendLine("    val typeName: String,")
            appendLine("    val kind: String,")
            appendLine("    val mappedMembers: List<String>,")
            appendLine("    val factoryMembers: List<String>,")
            appendLine("    val moduleActivationEntries: List<String>,")
            appendLine(")")
            appendLine()
            appendLine("internal object WinRTTypeShapeWriterPlan {")
            appendStringList("HELPER_OUTPUTS", inventory.helperOutputs.requiredHelperFileNames)
            appendStringList("BASE_TYPE_MAPPINGS", inventory.baseTypeMappings.map { "${it.typeName}->${it.baseTypeName}" })
            appendStringList("AUTHORING_METADATA_MAPPINGS", inventory.authoredMetadataTypeMappings.map { "${it.projectedTypeName}->${it.metadataTypeName}" })
            appendLine("    val TYPES: List<TypeShapeEntry> = listOf(")
            plans.sortedBy { it.type.qualifiedName }.forEach { plan ->
                appendLine("        TypeShapeEntry(")
                appendLine("            typeName = ${plan.type.qualifiedName.kotlinString()},")
                appendLine("            kind = ${plan.type.kind.name.kotlinString()},")
                appendLine("            mappedMembers = ${plan.customMappedMemberOutputDescriptor?.memberPlans.orEmpty().kotlinListLiteral()},")
                appendLine("            factoryMembers = ${plan.moduleActivationAndAuthoringDescriptor?.factoryMemberNames.orEmpty().kotlinListLiteral()},")
                appendLine("            moduleActivationEntries = ${plan.moduleActivationAndAuthoringDescriptor?.moduleActivationFactoryEntries.orEmpty().kotlinListLiteral()},")
                appendLine("        ),")
            }
            appendLine("    )")
            appendLine("    val TYPES_BY_NAME: Map<String, TypeShapeEntry> = TYPES.associateBy { it.typeName }")
            appendLine("    val BASE_TYPE_MAPPING_TABLE: Map<String, String> = BASE_TYPE_MAPPINGS.toArrowMap()")
            appendLine("    val AUTHORING_METADATA_MAPPING_TABLE: Map<String, String> = AUTHORING_METADATA_MAPPINGS.toArrowMap()")
            appendLine()
            appendLine("    fun typeShape(typeName: String): TypeShapeEntry? = TYPES_BY_NAME[typeName]")
            appendLine()
            appendLine("    fun registerBaseTypeMappings(register: (Map<String, String>) -> Unit) {")
            appendLine("        if (BASE_TYPE_MAPPING_TABLE.isNotEmpty()) {")
            appendLine("            register(BASE_TYPE_MAPPING_TABLE)")
            appendLine("        }")
            appendLine("    }")
            appendLine()
            appendLine("    fun registerAuthoringMetadataMappings(register: (Map<String, String>) -> Unit) {")
            appendLine("        if (AUTHORING_METADATA_MAPPING_TABLE.isNotEmpty()) {")
            appendLine("            register(AUTHORING_METADATA_MAPPING_TABLE)")
            appendLine("        }")
            appendLine("    }")
            appendLine()
            appendLine("    fun installModuleActivationFactories(install: (typeName: String, factoryMember: String) -> Unit) {")
            appendLine("        TYPES.forEach { type ->")
            appendLine("            type.moduleActivationEntries.forEach { factoryMember ->")
            appendLine("                install(type.typeName, factoryMember)")
            appendLine("            }")
            appendLine("        }")
            appendLine("    }")
            appendLine()
            appendLine("    private fun List<String>.toArrowMap(): Map<String, String> =")
            appendLine("        mapNotNull { entry ->")
            appendLine("            val separator = entry.indexOf(\"->\")")
            appendLine("            if (separator < 0) null else entry.substring(0, separator) to entry.substring(separator + 2)")
            appendLine("        }.toMap()")
            appendLine("}")
        }
        return supportFile("WinRTTypeShapeWriterPlan.kt", contents)
    }

    private fun renderNamespaceAdditions(inventory: WinRtMetadataProjectionInventory): KotlinProjectionFile? {
        if (inventory.namespaceAdditions.isEmpty()) {
            return null
        }
        val contents = buildString {
            appendHeader("WinRTNamespaceAdditions")
            appendLine("internal data class NamespaceAdditionEntry(")
            appendLine("    val namespace: String,")
            appendLine(")")
            appendLine()
            appendLine("internal object WinRTNamespaceAdditions {")
            appendLine("    val ENTRIES: List<NamespaceAdditionEntry> = listOf(")
            inventory.namespaceAdditions.forEach { addition ->
                appendLine("        NamespaceAdditionEntry(")
                appendLine("            namespace = ${addition.namespace.kotlinString()},")
                appendLine("        ),")
            }
            appendLine("    )")
            appendLine("    val ENTRIES_BY_NAMESPACE: Map<String, NamespaceAdditionEntry> = ENTRIES.associateBy { it.namespace }")
            appendLine()
            appendLine("    fun entryForNamespace(namespace: String): NamespaceAdditionEntry? =")
            appendLine("        ENTRIES_BY_NAMESPACE[namespace]")
            appendLine()
            appendLine("    fun installNamespaceAdditions(install: (NamespaceAdditionEntry) -> Unit) {")
            appendLine("        ENTRIES.forEach(install)")
            appendLine("    }")
            appendLine("}")
        }
        return supportFile("WinRTNamespaceAdditions.kt", contents)
    }

    private fun supportFile(fileName: String, contents: String): KotlinProjectionFile =
        KotlinProjectionFile(
            relativePath = "io/github/kitectlab/winrt/projections/support/$fileName",
            packageName = SUPPORT_PACKAGE,
            contents = contents,
        )

    private fun StringBuilder.appendHeader(fileName: String) {
        appendLine("package $SUPPORT_PACKAGE")
        appendLine()
        appendLine("// Deterministic generator handoff for .cswinrt $fileName writer parity.")
        appendLine()
    }

    private fun StringBuilder.appendStringList(name: String, values: List<String>) {
        appendLine("    val $name: List<String> = ${values.kotlinListLiteral()}")
    }

    private fun String.kotlinString(): String = buildString {
        append('"')
        this@kotlinString.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
        append('"')
    }

    private fun List<String>.kotlinListLiteral(): String =
        if (isEmpty()) {
            "emptyList()"
        } else {
            joinToString(prefix = "listOf(", postfix = ")") { it.kotlinString() }
        }

    private companion object {
        const val SUPPORT_PACKAGE = "io.github.kitectlab.winrt.projections.support"
    }
}
