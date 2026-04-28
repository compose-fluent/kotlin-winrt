package io.github.kitectlab.winrt.projections.generator

import io.github.kitectlab.winrt.metadata.WinRtMetadataModel
import io.github.kitectlab.winrt.metadata.WinRtAbiMarshalerPlanDescriptor
import io.github.kitectlab.winrt.metadata.WinRtAbiMarshalerSlotDescriptor
import io.github.kitectlab.winrt.metadata.WinRtCustomMappedMemberOutputDescriptor
import io.github.kitectlab.winrt.metadata.WinRtEventDefinition
import io.github.kitectlab.winrt.metadata.WinRtEventInvokeDescriptor
import io.github.kitectlab.winrt.metadata.WinRtFactorySurfaceDescriptor
import io.github.kitectlab.winrt.metadata.WinRtFastAbiClassDescriptor
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
import io.github.kitectlab.winrt.runtime.EventRegistrationToken
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
import io.github.kitectlab.winrt.runtime.NativeStructAdapter
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
import io.github.kitectlab.winrt.runtime.WinRtSystemProjectionMarshalers
import io.github.kitectlab.winrt.runtime.WinRtTypeSignature
import io.github.kitectlab.winrt.runtime.WinRtTypeHandle
import io.github.kitectlab.winrt.runtime.WinRtUri
import io.github.kitectlab.winrt.runtime.WinRtValueBoxingRegistration
import io.github.kitectlab.winrt.runtime.WinRtDelegateBridge
import io.github.kitectlab.winrt.runtime.WinRtDelegateDescriptor
import io.github.kitectlab.winrt.runtime.WinRtDelegateReference
import io.github.kitectlab.winrt.runtime.WinRtDelegateValueKind
import io.github.kitectlab.winrt.runtime.WinRtEvent
import io.github.kitectlab.winrt.runtime.WinRtClosableObject
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
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asClassName
import java.nio.file.Files
import java.nio.file.Path
import kotlin.collections.AbstractList
import kotlin.collections.AbstractMap
import kotlin.LazyThreadSafetyMode
import kotlin.io.path.extension
import kotlin.reflect.KClass

internal val ROOT_PACKAGE_SEGMENTS = listOf("io", "github", "kitectlab", "winrt", "projections")
internal val IREFERENCE_GENERIC_INTERFACE_ID = Guid("61C17706-2D65-11E0-9AE8-D48564015472")
internal val IREFERENCE_ARRAY_GENERIC_INTERFACE_ID = Guid("61C17707-2D65-11E0-9AE8-D48564015472")
internal val GUID_CLASS_NAME = Guid::class.asClassName()
internal val ACTIVATION_FACTORY_CLASS_NAME = ActivationFactory::class.asClassName()
internal val COM_OBJECT_REFERENCE_CLASS_NAME = ComObjectReference::class.asClassName()
internal val COM_VTABLE_INVOKER_CLASS_NAME = ComVtableInvoker::class.asClassName()
internal val WINRT_GENERIC_TYPE_INSTANTIATIONS_CLASS_NAME =
    ClassName("io.github.kitectlab.winrt.projections.support", "WinRTGenericTypeInstantiations")
internal val HRESULT_CLASS_NAME = HResult::class.asClassName()
internal val HSTRING_CLASS_NAME = HString::class.asClassName()
internal val IUNKNOWN_REFERENCE_CLASS_NAME = IUnknownReference::class.asClassName()
internal val IINSPECTABLE_REFERENCE_CLASS_NAME = ClassName("io.github.kitectlab.winrt.runtime", "IInspectableReference")
internal val IWINRT_OBJECT_CLASS_NAME = IWinRTObject::class.asClassName()
internal val MARSHALER_CLASS_NAME = Marshaler::class.asClassName()
internal val PLATFORM_ABI_CLASS_NAME = PlatformAbi::class.asClassName()
internal val PARAMETERIZED_INTERFACE_ID_CLASS_NAME = ParameterizedInterfaceId::class.asClassName()
internal val WINRT_BINDABLE_ITERABLE_PROJECTION_CLASS_NAME = WinRtBindableIterableProjection::class.asClassName()
internal val WINRT_BINDABLE_VECTOR_PROJECTION_CLASS_NAME = WinRtBindableVectorProjection::class.asClassName()
internal val WINRT_BINDABLE_VECTOR_VIEW_PROJECTION_CLASS_NAME = WinRtBindableVectorViewProjection::class.asClassName()
internal val WINRT_COLLECTION_INTERFACE_IDS_CLASS_NAME = WinRtCollectionInterfaceIds::class.asClassName()
internal val WINRT_DICTIONARY_PROJECTION_CLASS_NAME = WinRtDictionaryProjection::class.asClassName()
internal val WINRT_ITERABLE_PROJECTION_CLASS_NAME = WinRtIterableProjection::class.asClassName()
internal val WINRT_LIST_PROJECTION_CLASS_NAME = WinRtListProjection::class.asClassName()
internal val WINRT_ASYNC_ACTION_REFERENCE_CLASS_NAME = WinRtAsyncActionReference::class.asClassName()
internal val WINRT_ASYNC_ACTION_WITH_PROGRESS_REFERENCE_CLASS_NAME = WinRtAsyncActionWithProgressReference::class.asClassName()
internal val WINRT_ASYNC_ACTION_WITH_PROGRESS_VFTBL_SLOTS_CLASS_NAME = WinRtAsyncActionWithProgressVftblSlots::class.asClassName()
internal val WINRT_ASYNC_OPERATION_REFERENCE_CLASS_NAME = WinRtAsyncOperationReference::class.asClassName()
internal val WINRT_ASYNC_OPERATION_WITH_PROGRESS_REFERENCE_CLASS_NAME = WinRtAsyncOperationWithProgressReference::class.asClassName()
internal val WINRT_ASYNC_OPERATION_WITH_PROGRESS_VFTBL_SLOTS_CLASS_NAME = WinRtAsyncOperationWithProgressVftblSlots::class.asClassName()
internal val WINRT_ASYNC_OPERATION_VFTBL_SLOTS_CLASS_NAME = WinRtAsyncOperationVftblSlots::class.asClassName()
internal val WINRT_READ_ONLY_DICTIONARY_PROJECTION_CLASS_NAME = WinRtReadOnlyDictionaryProjection::class.asClassName()
internal val WINRT_READ_ONLY_LIST_PROJECTION_CLASS_NAME = WinRtReadOnlyListProjection::class.asClassName()
internal val WINRT_REFERENCE_ARRAY_PROJECTION_CLASS_NAME = WinRtReferenceArrayProjection::class.asClassName()
internal val WINRT_REFERENCE_PROJECTION_CLASS_NAME = WinRtReferenceProjection::class.asClassName()
internal val WINRT_REFERENCE_VALUE_ADAPTER_CLASS_NAME = WinRtReferenceValueAdapter::class.asClassName()
internal val WINRT_PLATFORM_API_CLASS_NAME = WinRtPlatformApi::class.asClassName()
internal val WINRT_SYSTEM_PROJECTION_MARSHALERS_CLASS_NAME = WinRtSystemProjectionMarshalers::class.asClassName()
internal val WINRT_TYPE_SIGNATURE_CLASS_NAME = WinRtTypeSignature::class.asClassName()
internal val WINRT_TYPE_HANDLE_CLASS_NAME = WinRtTypeHandle::class.asClassName()
internal val WINRT_VALUE_BOXING_REGISTRATION_CLASS_NAME = WinRtValueBoxingRegistration::class.asClassName()
internal val WINRT_DELEGATE_BRIDGE_CLASS_NAME = WinRtDelegateBridge::class.asClassName()
internal val WINRT_DELEGATE_DESCRIPTOR_CLASS_NAME = WinRtDelegateDescriptor::class.asClassName()
internal val WINRT_DELEGATE_REFERENCE_CLASS_NAME = WinRtDelegateReference::class.asClassName()
internal val WINRT_DELEGATE_VALUE_KIND_CLASS_NAME = WinRtDelegateValueKind::class.asClassName()
internal val WINRT_EVENT_CLASS_NAME = WinRtEvent::class.asClassName()
internal val WINRT_CLOSABLE_OBJECT_CLASS_NAME = WinRtClosableObject::class.asClassName()
internal val ATTRIBUTE_CLASS_NAME = Annotation::class.asClassName()
internal val ABSTRACT_LIST_CLASS_NAME = AbstractList::class.asClassName()
internal val ABSTRACT_MAP_CLASS_NAME = AbstractMap::class.asClassName()
internal val ABSTRACT_MUTABLE_LIST_CLASS_NAME = ClassName("kotlin.collections", "AbstractMutableList")
internal val ABSTRACT_MUTABLE_MAP_CLASS_NAME = ClassName("kotlin.collections", "AbstractMutableMap")
internal val ABSTRACT_MUTABLE_SET_CLASS_NAME = ClassName("kotlin.collections", "AbstractMutableSet")
internal val WINRT_URI_CLASS_NAME = WinRtUri::class.asClassName()
internal val KOTLIN_INSTANT_CLASS_NAME = ClassName("kotlin.time", "Instant")
internal val KOTLIN_DURATION_CLASS_NAME = ClassName("kotlin.time", "Duration")
internal val KCLASS_STAR_TYPE_NAME = KClass::class.asClassName().parameterizedBy(STAR)
internal val AUTO_CLOSEABLE_CLASS_NAME = AutoCloseable::class.asClassName()
internal val ILLEGAL_STATE_EXCEPTION_CLASS_NAME = IllegalStateException::class.asClassName()
internal val NO_SUCH_ELEMENT_EXCEPTION_CLASS_NAME = NoSuchElementException::class.asClassName()
internal val LAZY_THREAD_SAFETY_MODE_CLASS_NAME = LazyThreadSafetyMode::class.asClassName()
internal val MUTABLE_LIST_CLASS_NAME = ClassName("kotlin.collections", "MutableList")
internal val MUTABLE_MAP_CLASS_NAME = ClassName("kotlin.collections", "MutableMap")
internal val RAW_ADDRESS_CLASS_NAME = RawAddress::class.asClassName()
internal val NATIVE_NESTED_STRUCT_FIELD_SPEC_CLASS_NAME = NativeNestedStructFieldSpec::class.asClassName()
internal val NATIVE_SCALAR_FIELD_SPEC_CLASS_NAME = NativeScalarFieldSpec::class.asClassName()
internal val NATIVE_STRUCT_ADAPTER_CLASS_NAME = NativeStructAdapter::class.asClassName()
internal val NATIVE_STRUCT_LAYOUT_CLASS_NAME = NativeStructLayout::class.asClassName()
internal val NATIVE_STRUCT_SCALAR_KIND_CLASS_NAME = NativeStructScalarKind::class.asClassName()
internal val EVENT_REGISTRATION_TOKEN_CLASS_NAME = EventRegistrationToken::class.asClassName()
internal val EXCEPTION_CLASS_NAME = Exception::class.asClassName()
internal val EVENT_HANDLER_CALLBACK_CLASS_NAME = ClassName("io.github.kitectlab.winrt.runtime", "EventHandlerCallback")
internal val WINRT_COMMAND_CLASS_NAME = ClassName("io.github.kitectlab.winrt.runtime", "WinRtCommand")
internal val WINRT_PROPERTY_CHANGED_NOTIFIER_CLASS_NAME = ClassName("io.github.kitectlab.winrt.runtime", "WinRtPropertyChangedNotifier")
internal val WINRT_PROPERTY_CHANGED_EVENT_ARGS_CLASS_NAME = ClassName("io.github.kitectlab.winrt.runtime", "WinRtPropertyChangedEventArgs")
internal val WINRT_PROPERTY_CHANGED_HANDLER_CLASS_NAME = ClassName("io.github.kitectlab.winrt.runtime", "WinRtPropertyChangedHandler")
internal val WINRT_COLLECTION_CHANGED_NOTIFIER_CLASS_NAME = ClassName("io.github.kitectlab.winrt.runtime", "WinRtCollectionChangedNotifier")
internal val WINRT_NOTIFY_COLLECTION_CHANGED_ACTION_CLASS_NAME = ClassName("io.github.kitectlab.winrt.runtime", "WinRtNotifyCollectionChangedAction")
internal val WINRT_NOTIFY_COLLECTION_CHANGED_EVENT_ARGS_CLASS_NAME = ClassName("io.github.kitectlab.winrt.runtime", "WinRtNotifyCollectionChangedEventArgs")
internal val WINRT_COLLECTION_CHANGED_HANDLER_CLASS_NAME = ClassName("io.github.kitectlab.winrt.runtime", "WinRtCollectionChangedHandler")
internal val WINRT_DATA_ERROR_INFO_CLASS_NAME = ClassName("io.github.kitectlab.winrt.runtime", "WinRtDataErrorInfo")
internal val WINRT_DATA_ERROR_INFO_PROJECTION_CLASS_NAME = ClassName("io.github.kitectlab.winrt.runtime", "WinRtDataErrorInfoProjection")
internal val WINRT_DATA_ERRORS_CHANGED_EVENT_ARGS_CLASS_NAME = ClassName("io.github.kitectlab.winrt.runtime", "WinRtDataErrorsChangedEventArgs")
internal val WINRT_DATA_ERRORS_CHANGED_HANDLER_CLASS_NAME = ClassName("io.github.kitectlab.winrt.runtime", "WinRtDataErrorsChangedHandler")
internal val WINRT_SERVICE_PROVIDER_CLASS_NAME = ClassName("io.github.kitectlab.winrt.runtime", "WinRtServiceProvider")
internal val KOTLIN_UBYTE_CLASS_NAME = UByte::class.asClassName().withKotlinPackageIfRoot()
internal val KOTLIN_UINT_CLASS_NAME = UInt::class.asClassName().withKotlinPackageIfRoot()
internal val KOTLIN_ULONG_CLASS_NAME = ULong::class.asClassName().withKotlinPackageIfRoot()
internal val KOTLIN_USHORT_CLASS_NAME = UShort::class.asClassName().withKotlinPackageIfRoot()

private fun ClassName.withKotlinPackageIfRoot(): ClassName =
    if (packageName.isEmpty()) ClassName("kotlin", simpleNames) else this

internal typealias SpecialTypeResolver = (List<TypeName>) -> TypeName

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
    val fastAbiClassDescriptor: WinRtFastAbiClassDescriptor? = null,
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

internal data class KotlinProjectionMappedType(
    val abiQualifiedName: String,
    val projectedTypeResolver: SpecialTypeResolver,
    val abiValueKind: KotlinProjectionAbiValueKind? = null,
    val customStructAbi: KotlinProjectionCustomStructAbi? = null,
    val customObjectAbi: KotlinProjectionCustomObjectAbi? = null,
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

internal data class KotlinProjectionCustomStructAbi(
    val helperTypeName: ClassName,
    val sizeBytes: Long,
    val fromAbiFunctionName: String,
    val copyToFunctionName: String,
    val disposeAbiFunctionName: String? = null,
)

internal data class KotlinProjectionCustomObjectAbi(
    val interfaceId: Guid,
    val typeHandleName: String,
    val fromAbiFunctionName: String = "objectFromAbi",
    val createReferenceFunctionName: String = "createObjectReference",
)

internal data class KotlinProjectionIntegralAbiDescriptor(
    val kotlinTypeName: TypeName,
    val argumentConversionSuffix: String = "",
    val literalRenderer: (ULong) -> CodeBlock,
)

/**
 * `.cswinrt/src/cswinrt/helpers.h` converges mapped WinRT type decisions through `mapped_type` plus
 * `get_mapped_type(...)`. Keep Kotlin projection planning on the same ownership model instead of
 * repeating the same collection/async/custom-type tables across unrelated helpers.
 */
internal val MAPPED_TYPES: List<KotlinProjectionMappedType> = listOf(
    KotlinProjectionMappedType("System.Object", { IINSPECTABLE_REFERENCE_CLASS_NAME }, descriptionName = "Object"),
    KotlinProjectionMappedType("WinRT.Interop.HWND", { Long::class.asClassName() }, descriptionName = "HWND"),
    KotlinProjectionMappedType(
        "Windows.Foundation.DateTime",
        { KOTLIN_INSTANT_CLASS_NAME },
        abiValueKind = KotlinProjectionAbiValueKind.Struct,
        customStructAbi = KotlinProjectionCustomStructAbi(WINRT_SYSTEM_PROJECTION_MARSHALERS_CLASS_NAME, 8, "dateTimeFromAbi", "copyDateTimeTo"),
        descriptionName = "DateTime",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.TimeSpan",
        { KOTLIN_DURATION_CLASS_NAME },
        abiValueKind = KotlinProjectionAbiValueKind.Struct,
        customStructAbi = KotlinProjectionCustomStructAbi(WINRT_SYSTEM_PROJECTION_MARSHALERS_CLASS_NAME, 8, "timeSpanFromAbi", "copyTimeSpanTo"),
        descriptionName = "TimeSpan",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.Uri",
        { WINRT_URI_CLASS_NAME },
        abiValueKind = KotlinProjectionAbiValueKind.ProjectedRuntimeClass,
        customObjectAbi = KotlinProjectionCustomObjectAbi(
            interfaceId = Guid("9E365E57-48B2-4160-956F-C7385120BBFC"),
            typeHandleName = "io.github.kitectlab.winrt.runtime.WinRtUri",
            fromAbiFunctionName = "uriFromAbi",
        ),
        descriptionName = "Uri",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.EventHandler",
        { arguments -> EVENT_HANDLER_CALLBACK_CLASS_NAME.parameterizedBy(arguments.singleOrNull() ?: ANY.copy(nullable = true)) },
        descriptionName = "EventHandler",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.EventRegistrationToken",
        { EVENT_REGISTRATION_TOKEN_CLASS_NAME },
        abiValueKind = KotlinProjectionAbiValueKind.Struct,
        descriptionName = "EventRegistrationToken",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.HResult",
        { EXCEPTION_CLASS_NAME },
        abiValueKind = KotlinProjectionAbiValueKind.Struct,
        customStructAbi = KotlinProjectionCustomStructAbi(WINRT_SYSTEM_PROJECTION_MARSHALERS_CLASS_NAME, 4, "hResultFromAbi", "copyHResultTo"),
        descriptionName = "HResult",
    ),
    KotlinProjectionMappedType(
        "Windows.UI.Xaml.Interop.TypeName",
        { KCLASS_STAR_TYPE_NAME.copy(nullable = true) },
        abiValueKind = KotlinProjectionAbiValueKind.Struct,
        customStructAbi = KotlinProjectionCustomStructAbi(
            WINRT_SYSTEM_PROJECTION_MARSHALERS_CLASS_NAME,
            12,
            "typeNameFromAbi",
            "copyTypeNameTo",
            "disposeTypeNameAbi",
        ),
        descriptionName = "TypeName",
    ),
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
    KotlinProjectionMappedType("Microsoft.UI.Xaml.IXamlServiceProvider", { WINRT_SERVICE_PROVIDER_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("68B3A2DF-8173-539F-B524-C8A2348F5AFB"), "io.github.kitectlab.winrt.runtime.WinRtServiceProvider"), descriptionName = "IXamlServiceProvider"),
    KotlinProjectionMappedType("Microsoft.UI.Xaml.Data.DataErrorsChangedEventArgs", { WINRT_DATA_ERRORS_CHANGED_EVENT_ARGS_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedRuntimeClass, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("D026DD64-5F26-5F15-A86A-0DEC8A431796"), "io.github.kitectlab.winrt.runtime.WinRtDataErrorsChangedEventArgs"), descriptionName = "DataErrorsChangedEventArgs"),
    KotlinProjectionMappedType("Microsoft.UI.Xaml.Data.INotifyDataErrorInfo", { WINRT_DATA_ERROR_INFO_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("0EE6C2CC-273E-567D-BC0A-1DD87EE51EBA"), "io.github.kitectlab.winrt.runtime.WinRtDataErrorInfo"), descriptionName = "INotifyDataErrorInfo"),
    KotlinProjectionMappedType("Microsoft.UI.Xaml.Data.INotifyPropertyChanged", { WINRT_PROPERTY_CHANGED_NOTIFIER_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("90B17601-B065-586E-83D9-9ADC3A695284"), "io.github.kitectlab.winrt.runtime.WinRtPropertyChangedNotifier"), descriptionName = "INotifyPropertyChanged"),
    KotlinProjectionMappedType("Microsoft.UI.Xaml.Data.PropertyChangedEventArgs", { WINRT_PROPERTY_CHANGED_EVENT_ARGS_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedRuntimeClass, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("63D0C952-396B-54F4-AF8C-BA8724A427BF"), "io.github.kitectlab.winrt.runtime.WinRtPropertyChangedEventArgs"), descriptionName = "PropertyChangedEventArgs"),
    KotlinProjectionMappedType("Microsoft.UI.Xaml.Data.PropertyChangedEventHandler", { WINRT_PROPERTY_CHANGED_HANDLER_CLASS_NAME }, descriptionName = "PropertyChangedEventHandler"),
    KotlinProjectionMappedType("Microsoft.UI.Xaml.Input.ICommand", { WINRT_COMMAND_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("E5AF3542-CA67-4081-995B-709DD13792DF"), "io.github.kitectlab.winrt.runtime.WinRtCommand"), descriptionName = "ICommand"),
    KotlinProjectionMappedType("Microsoft.UI.Xaml.Interop.ICommand", { WINRT_COMMAND_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("E5AF3542-CA67-4081-995B-709DD13792DF"), "io.github.kitectlab.winrt.runtime.WinRtCommand"), descriptionName = "ICommand"),
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
    KotlinProjectionMappedType("Microsoft.UI.Xaml.Interop.INotifyCollectionChanged", { WINRT_COLLECTION_CHANGED_NOTIFIER_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("530155E1-28A5-5693-87CE-30724D95A06D"), "io.github.kitectlab.winrt.runtime.WinRtCollectionChangedNotifier"), descriptionName = "INotifyCollectionChanged"),
    KotlinProjectionMappedType("Microsoft.UI.Xaml.Interop.NotifyCollectionChangedAction", { WINRT_NOTIFY_COLLECTION_CHANGED_ACTION_CLASS_NAME }, descriptionName = "NotifyCollectionChangedAction"),
    KotlinProjectionMappedType("Microsoft.UI.Xaml.Interop.NotifyCollectionChangedEventArgs", { WINRT_NOTIFY_COLLECTION_CHANGED_EVENT_ARGS_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedRuntimeClass, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("DA049FF2-D2E0-5FE8-8C7B-F87F26060B6F"), "io.github.kitectlab.winrt.runtime.WinRtNotifyCollectionChangedEventArgs"), descriptionName = "NotifyCollectionChangedEventArgs"),
    KotlinProjectionMappedType("Microsoft.UI.Xaml.Interop.NotifyCollectionChangedEventHandler", { WINRT_COLLECTION_CHANGED_HANDLER_CLASS_NAME }, descriptionName = "NotifyCollectionChangedEventHandler"),
    KotlinProjectionMappedType("Windows.UI.Xaml.IXamlServiceProvider", { WINRT_SERVICE_PROVIDER_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("68B3A2DF-8173-539F-B524-C8A2348F5AFB"), "io.github.kitectlab.winrt.runtime.WinRtServiceProvider"), descriptionName = "IXamlServiceProvider"),
    KotlinProjectionMappedType("Windows.UI.Xaml.Data.DataErrorsChangedEventArgs", { WINRT_DATA_ERRORS_CHANGED_EVENT_ARGS_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedRuntimeClass, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("D026DD64-5F26-5F15-A86A-0DEC8A431796"), "io.github.kitectlab.winrt.runtime.WinRtDataErrorsChangedEventArgs"), descriptionName = "DataErrorsChangedEventArgs"),
    KotlinProjectionMappedType("Windows.UI.Xaml.Data.INotifyDataErrorInfo", { WINRT_DATA_ERROR_INFO_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("0EE6C2CC-273E-567D-BC0A-1DD87EE51EBA"), "io.github.kitectlab.winrt.runtime.WinRtDataErrorInfo"), descriptionName = "INotifyDataErrorInfo"),
    KotlinProjectionMappedType("Windows.UI.Xaml.Data.INotifyPropertyChanged", { WINRT_PROPERTY_CHANGED_NOTIFIER_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("CF75D69C-F2F4-486B-B302-BB4C09BAEBFA"), "io.github.kitectlab.winrt.runtime.WinRtPropertyChangedNotifier"), descriptionName = "INotifyPropertyChanged"),
    KotlinProjectionMappedType("Windows.UI.Xaml.Data.PropertyChangedEventArgs", { WINRT_PROPERTY_CHANGED_EVENT_ARGS_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedRuntimeClass, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("4F33A9A0-5CF4-47A4-B16F-D7FAAF17457E"), "io.github.kitectlab.winrt.runtime.WinRtPropertyChangedEventArgs"), descriptionName = "PropertyChangedEventArgs"),
    KotlinProjectionMappedType("Windows.UI.Xaml.Data.PropertyChangedEventHandler", { WINRT_PROPERTY_CHANGED_HANDLER_CLASS_NAME }, descriptionName = "PropertyChangedEventHandler"),
    KotlinProjectionMappedType("Windows.UI.Xaml.Input.ICommand", { WINRT_COMMAND_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("E5AF3542-CA67-4081-995B-709DD13792DF"), "io.github.kitectlab.winrt.runtime.WinRtCommand"), descriptionName = "ICommand"),
    KotlinProjectionMappedType("Windows.UI.Xaml.Interop.ICommand", { WINRT_COMMAND_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("E5AF3542-CA67-4081-995B-709DD13792DF"), "io.github.kitectlab.winrt.runtime.WinRtCommand"), descriptionName = "ICommand"),
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
    KotlinProjectionMappedType("Windows.UI.Xaml.Interop.INotifyCollectionChanged", { WINRT_COLLECTION_CHANGED_NOTIFIER_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("28B167D5-1A31-465B-9B25-D5C3AE686C40"), "io.github.kitectlab.winrt.runtime.WinRtCollectionChangedNotifier"), descriptionName = "INotifyCollectionChanged"),
    KotlinProjectionMappedType("Windows.UI.Xaml.Interop.NotifyCollectionChangedAction", { WINRT_NOTIFY_COLLECTION_CHANGED_ACTION_CLASS_NAME }, descriptionName = "NotifyCollectionChangedAction"),
    KotlinProjectionMappedType("Windows.UI.Xaml.Interop.NotifyCollectionChangedEventArgs", { WINRT_NOTIFY_COLLECTION_CHANGED_EVENT_ARGS_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedRuntimeClass, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("4CF68D33-E3F2-4964-B85E-945B4F7E2F21"), "io.github.kitectlab.winrt.runtime.WinRtNotifyCollectionChangedEventArgs"), descriptionName = "NotifyCollectionChangedEventArgs"),
    KotlinProjectionMappedType("Windows.UI.Xaml.Interop.NotifyCollectionChangedEventHandler", { WINRT_COLLECTION_CHANGED_HANDLER_CLASS_NAME }, descriptionName = "NotifyCollectionChangedEventHandler"),
)

internal val MAPPED_TYPES_BY_ABI_NAME: Map<String, KotlinProjectionMappedType> =
    MAPPED_TYPES.associateBy(KotlinProjectionMappedType::abiQualifiedName)

internal val MAPPED_TYPES_BY_SIMPLE_ABI_NAME: Map<String, KotlinProjectionMappedType> =
    MAPPED_TYPES.filter { mappedType ->
        mappedType.abiValueKind in setOf(
            KotlinProjectionAbiValueKind.Struct,
            KotlinProjectionAbiValueKind.Reference,
            KotlinProjectionAbiValueKind.ReferenceArray,
        ) ||
            mappedType.descriptionName in setOf("Object", "HWND", "DateTime", "TimeSpan", "Uri", "EventHandler", "HResult", "IClosable")
    }.groupBy { it.abiQualifiedName.substringAfterLast('.') }
        .filterValues { it.size == 1 }
        .mapValues { (_, mappedTypes) -> mappedTypes.single() }

internal val MAPPED_TYPES_BY_ABI_KIND: Map<KotlinProjectionAbiValueKind, KotlinProjectionMappedType> =
    MAPPED_TYPES.mapNotNull { mappedType ->
        mappedType.abiValueKind?.let { abiValueKind -> abiValueKind to mappedType }
    }.toMap()

internal val INTEGRAL_ABI_DESCRIPTORS: Map<WinRtIntegralType, KotlinProjectionIntegralAbiDescriptor> = mapOf(
    WinRtIntegralType.Int8 to KotlinProjectionIntegralAbiDescriptor(
        kotlinTypeName = Byte::class.asClassName(),
        literalRenderer = { valueBits -> CodeBlock.of("%L.toByte()", valueBits.toByte()) },
    ),
    WinRtIntegralType.UInt8 to KotlinProjectionIntegralAbiDescriptor(
        kotlinTypeName = KOTLIN_UBYTE_CLASS_NAME,
        argumentConversionSuffix = ".toByte()",
        literalRenderer = { valueBits -> CodeBlock.of("%L.toUByte()", valueBits.toUByte()) },
    ),
    WinRtIntegralType.Int16 to KotlinProjectionIntegralAbiDescriptor(
        kotlinTypeName = Short::class.asClassName(),
        literalRenderer = { valueBits -> CodeBlock.of("%L.toShort()", valueBits.toShort()) },
    ),
    WinRtIntegralType.UInt16 to KotlinProjectionIntegralAbiDescriptor(
        kotlinTypeName = KOTLIN_USHORT_CLASS_NAME,
        argumentConversionSuffix = ".toShort()",
        literalRenderer = { valueBits -> CodeBlock.of("%L.toUShort()", valueBits.toUShort()) },
    ),
    WinRtIntegralType.Int32 to KotlinProjectionIntegralAbiDescriptor(
        kotlinTypeName = Int::class.asClassName(),
        literalRenderer = { valueBits -> CodeBlock.of("%L", valueBits.toInt()) },
    ),
    WinRtIntegralType.UInt32 to KotlinProjectionIntegralAbiDescriptor(
        kotlinTypeName = KOTLIN_UINT_CLASS_NAME,
        argumentConversionSuffix = ".toInt()",
        literalRenderer = { valueBits -> CodeBlock.of("%L.toUInt()", valueBits.toUInt()) },
    ),
    WinRtIntegralType.Int64 to KotlinProjectionIntegralAbiDescriptor(
        kotlinTypeName = Long::class.asClassName(),
        literalRenderer = { valueBits -> CodeBlock.of("%L", "${valueBits.toLong()}L") },
    ),
    WinRtIntegralType.UInt64 to KotlinProjectionIntegralAbiDescriptor(
        kotlinTypeName = KOTLIN_ULONG_CLASS_NAME,
        argumentConversionSuffix = ".toLong()",
        literalRenderer = { valueBits -> CodeBlock.of("%L", "${valueBits}uL") },
    ),
)

internal fun mappedTypeByAbiName(abiQualifiedName: String): KotlinProjectionMappedType? =
    MAPPED_TYPES_BY_ABI_NAME[abiQualifiedName]
        ?: abiQualifiedName.takeIf { '.' !in it }?.let(MAPPED_TYPES_BY_SIMPLE_ABI_NAME::get)

internal fun mappedTypeByAbiKind(kind: KotlinProjectionAbiValueKind): KotlinProjectionMappedType? =
    MAPPED_TYPES_BY_ABI_KIND[kind]

internal fun integralAbiDescriptor(type: WinRtIntegralType): KotlinProjectionIntegralAbiDescriptor =
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

internal data class KotlinProjectionAbiMarshalerPlan(
    val name: String,
    val typeBinding: KotlinProjectionAbiTypeBinding,
    val isReturn: Boolean,
    val abiArgumentExpression: CodeBlock,
    val abiArgumentKind: KotlinProjectionComArgumentKind? = null,
    val extraAbiArgumentExpressions: List<CodeBlock> = emptyList(),
    val extraAbiArgumentKinds: List<KotlinProjectionComArgumentKind> = emptyList(),
    val scopeOpeners: List<CodeBlock> = emptyList(),
    val postCallStatements: List<CodeBlock> = emptyList(),
    val finallyStatements: List<CodeBlock> = emptyList(),
    val resultAllocation: CodeBlock? = null,
    val resultLocalDeclarations: CodeBlock? = null,
    val readbackStatement: CodeBlock? = null,
)

internal data class KotlinProjectionAbiCallPlan(
    val parameterMarshalers: List<KotlinProjectionAbiMarshalerPlan>,
    val returnMarshaler: KotlinProjectionAbiMarshalerPlan? = null,
    val descriptor: WinRtAbiMarshalerPlanDescriptor? = null,
)

internal enum class KotlinProjectionComArgumentKind {
    Pointer,
    Int8,
    Int16,
    Int32,
    Int64,
    Float,
    Double,
}

data class KotlinProjectionFile(
    val relativePath: String,
    val packageName: String,
    val contents: String,
) {
    fun writeToIfChanged(outputRoot: Path): Boolean {
        val target = outputRoot.resolve(relativePath)
        Files.createDirectories(target.parent)
        if (Files.isRegularFile(target) && Files.readString(target) == contents) {
            return false
        }
        Files.writeString(target, contents)
        return true
    }
}

data class KotlinProjectionWriteSummary(
    val renderedFiles: Int,
    val writtenFiles: Int,
    val unchangedFiles: Int,
    val deletedStaleFiles: Int,
)

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
