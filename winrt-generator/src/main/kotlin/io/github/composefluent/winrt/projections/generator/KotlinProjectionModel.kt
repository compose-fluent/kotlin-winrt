package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRtMetadataModel
import io.github.composefluent.winrt.metadata.WinRtAbiMarshalerPlanDescriptor
import io.github.composefluent.winrt.metadata.WinRtAbiMarshalerSlotDescriptor
import io.github.composefluent.winrt.metadata.WinRtClassMemberMergeDescriptor
import io.github.composefluent.winrt.metadata.WinRtCustomMappedMemberOutputDescriptor
import io.github.composefluent.winrt.metadata.WinRtEventDefinition
import io.github.composefluent.winrt.metadata.WinRtEventInvokeDescriptor
import io.github.composefluent.winrt.metadata.WinRtFactorySurfaceDescriptor
import io.github.composefluent.winrt.metadata.WinRtFastAbiClassDescriptor
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
import io.github.composefluent.winrt.metadata.WinRtProjectedAttributeDescriptor
import io.github.composefluent.winrt.metadata.WinRtMetadataParameterCategory
import io.github.composefluent.winrt.metadata.WinRtModuleActivationAndAuthoringDescriptor
import io.github.composefluent.winrt.metadata.WinRtMethodVtableDescriptor
import io.github.composefluent.winrt.metadata.WinRtMethodDefinition
import io.github.composefluent.winrt.metadata.WinRtNamespace
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
import io.github.composefluent.winrt.runtime.ComAbiValueKind
import io.github.composefluent.winrt.runtime.ComMethodSignature
import io.github.composefluent.winrt.runtime.ComObjectReference
import io.github.composefluent.winrt.runtime.ComWrappersSupport
import io.github.composefluent.winrt.runtime.ComVtableInvoker
import io.github.composefluent.winrt.runtime.DerivedComposed
import io.github.composefluent.winrt.runtime.EventRegistrationToken
import io.github.composefluent.winrt.runtime.ExceptionHelpers
import io.github.composefluent.winrt.runtime.EventSource
import io.github.composefluent.winrt.runtime.Guid
import io.github.composefluent.winrt.runtime.HResult
import io.github.composefluent.winrt.runtime.HString
import io.github.composefluent.winrt.runtime.IID
import io.github.composefluent.winrt.runtime.IUnknownReference
import io.github.composefluent.winrt.runtime.IWinRTObject
import io.github.composefluent.winrt.runtime.KnownHResults
import io.github.composefluent.winrt.runtime.Marshaler
import io.github.composefluent.winrt.runtime.NativeStringMarshaller
import io.github.composefluent.winrt.runtime.PlatformAbi
import io.github.composefluent.winrt.runtime.ParameterizedInterfaceId
import io.github.composefluent.winrt.runtime.Projections
import io.github.composefluent.winrt.runtime.RawAddress
import io.github.composefluent.winrt.runtime.WinRtAbiArray
import io.github.composefluent.winrt.runtime.NativeAbiLayout
import io.github.composefluent.winrt.runtime.NativeNestedStructFieldSpec
import io.github.composefluent.winrt.runtime.NativeScalarFieldSpec
import io.github.composefluent.winrt.runtime.NativeStructAdapter
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
import io.github.composefluent.winrt.runtime.WinRtAsyncInterfaceIds
import io.github.composefluent.winrt.runtime.WinRtAsyncOperationReference
import io.github.composefluent.winrt.runtime.WinRtAsyncOperationWithProgressReference
import io.github.composefluent.winrt.runtime.WinRtAsyncOperationWithProgressVftblSlots
import io.github.composefluent.winrt.runtime.WinRtAsyncOperationVftblSlots
import io.github.composefluent.winrt.runtime.WinRtAsyncProjectionInterop
import io.github.composefluent.winrt.runtime.WinRtReadOnlyDictionaryProjection
import io.github.composefluent.winrt.runtime.WinRtReadOnlyListProjection
import io.github.composefluent.winrt.runtime.WinRtReferenceArrayProjection
import io.github.composefluent.winrt.runtime.WinRtReferenceProjection
import io.github.composefluent.winrt.runtime.WinRtReferenceProjectionInterop
import io.github.composefluent.winrt.runtime.WinRtReferenceValueAdapter
import io.github.composefluent.winrt.runtime.WinRtReferenceValueAdapters
import io.github.composefluent.winrt.runtime.WinRtPropertyValueProjection
import io.github.composefluent.winrt.runtime.WinRtGenericParameterProjection
import io.github.composefluent.winrt.runtime.WinRtGenericAbiSupportIntrinsic
import io.github.composefluent.winrt.runtime.WinRtGenericTypeInstantiationSupportIntrinsic
import io.github.composefluent.winrt.runtime.WinRtAuthoringSupportIntrinsic
import io.github.composefluent.winrt.runtime.WinRtProjectionIntrinsic
import io.github.composefluent.winrt.runtime.WinRtProjectionSupportIntrinsic
import io.github.composefluent.winrt.runtime.WinRtPlatformApi
import io.github.composefluent.winrt.runtime.WinRtSystemProjectionMarshalers
import io.github.composefluent.winrt.runtime.WinRtTypeSignature
import io.github.composefluent.winrt.runtime.WinRtTypeHandle
import io.github.composefluent.winrt.runtime.WinRtUri
import io.github.composefluent.winrt.runtime.WinRtValueBoxingRegistration
import io.github.composefluent.winrt.runtime.WinRtDelegateBridge
import io.github.composefluent.winrt.runtime.WinRtDelegateDescriptor
import io.github.composefluent.winrt.runtime.WinRtDelegateReference
import io.github.composefluent.winrt.runtime.WinRtDelegateValueKind
import io.github.composefluent.winrt.runtime.WinRtEvent
import io.github.composefluent.winrt.runtime.WinRtObjectMarshaller
import io.github.composefluent.winrt.runtime.WinRtProjectedDelegate
import io.github.composefluent.winrt.runtime.WinRtClosableObject
import io.github.composefluent.winrt.runtime.WinRtComposableObject
import io.github.composefluent.winrt.runtime.WinRtComposableObjectReference
import io.github.composefluent.winrt.runtime.WinRtAttributeUsage
import io.github.composefluent.winrt.runtime.WinRtActivationFactory
import io.github.composefluent.winrt.runtime.WinRtCcwDefinition
import io.github.composefluent.winrt.runtime.WinRtContractVersion
import io.github.composefluent.winrt.runtime.WinRtDefaultOverload
import io.github.composefluent.winrt.runtime.WinRtExperimental
import io.github.composefluent.winrt.runtime.WinRtOverload
import io.github.composefluent.winrt.runtime.WinRtSupportedOSPlatform
import io.github.composefluent.winrt.runtime.WinRtInspectableInterfaceDefinition
import io.github.composefluent.winrt.runtime.WinRtInspectableMethodDefinition
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
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
import kotlin.jvm.JvmField
import kotlin.jvm.JvmInline
import kotlin.reflect.KClass

internal val ROOT_PACKAGE_SEGMENTS = emptyList<String>()
internal val IREFERENCE_GENERIC_INTERFACE_ID = Guid("61C17706-2D65-11E0-9AE8-D48564015472")
internal val IREFERENCE_ARRAY_GENERIC_INTERFACE_ID = Guid("61C17707-2D65-11E0-9AE8-D48564015472")
internal val GUID_CLASS_NAME = Guid::class.asClassName()
internal val ACTIVATION_FACTORY_CLASS_NAME = ActivationFactory::class.asClassName()
internal val COM_ABI_VALUE_KIND_CLASS_NAME = ComAbiValueKind::class.asClassName()
internal val COM_METHOD_SIGNATURE_CLASS_NAME = ComMethodSignature::class.asClassName()
internal val COM_OBJECT_REFERENCE_CLASS_NAME = ComObjectReference::class.asClassName()
internal val COM_WRAPPERS_SUPPORT_CLASS_NAME = ComWrappersSupport::class.asClassName()
internal val COM_VTABLE_INVOKER_CLASS_NAME = ComVtableInvoker::class.asClassName()
internal val DERIVED_COMPOSED_CLASS_NAME = DerivedComposed::class.asClassName()
internal val WINRT_CCW_DEFINITION_CLASS_NAME = WinRtCcwDefinition::class.asClassName()
internal val WINRT_INSPECTABLE_INTERFACE_DEFINITION_CLASS_NAME = WinRtInspectableInterfaceDefinition::class.asClassName()
internal val WINRT_INSPECTABLE_METHOD_DEFINITION_CLASS_NAME = WinRtInspectableMethodDefinition::class.asClassName()
internal val WINRT_GENERIC_TYPE_INSTANTIATIONS_CLASS_NAME =
    ClassName("io.github.composefluent.winrt.projections.support", "WinRTGenericTypeInstantiations")
internal val WINRT_AUTHORING_HOST_EXPORTS_CLASS_NAME =
    ClassName("io.github.composefluent.winrt.projections.support", "WinRTAuthoringHostExports")
internal val WINRT_AUTHORING_SERVER_ACTIVATION_FACTORIES_CLASS_NAME =
    ClassName("io.github.composefluent.winrt.projections.support", "WinRTAuthoringServerActivationFactories")
internal val WINRT_AUTHORING_MODULE_ACTIVATION_FACTORY_PLAN_CLASS_NAME =
    ClassName("io.github.composefluent.winrt.projections.support", "WinRTAuthoringModuleActivationFactoryPlan")
internal val WINRT_NAMESPACE_ADDITIONS_CLASS_NAME =
    ClassName("io.github.composefluent.winrt.projections.support", "WinRTNamespaceAdditions")
internal fun winRtGenericTypeInstantiationsClassName(ownerIdentity: String?): ClassName {
    val suffix = ownerIdentity
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.toKotlinSupportIdentifierSuffix()
        ?.takeIf(String::isNotEmpty)
        ?: return WINRT_GENERIC_TYPE_INSTANTIATIONS_CLASS_NAME
    return ClassName(
        "io.github.composefluent.winrt.projections.support",
        "WinRTGenericTypeInstantiations_$suffix",
    )
}

internal fun winRtAuthoringHostExportsClassName(ownerIdentity: String?): ClassName =
    winRtSupportOwnerClassName(ownerIdentity, WINRT_AUTHORING_HOST_EXPORTS_CLASS_NAME)

internal fun winRtAuthoringServerActivationFactoriesClassName(ownerIdentity: String?): ClassName =
    winRtSupportOwnerClassName(ownerIdentity, WINRT_AUTHORING_SERVER_ACTIVATION_FACTORIES_CLASS_NAME)

internal fun winRtAuthoringModuleActivationFactoryPlanClassName(ownerIdentity: String?): ClassName =
    winRtSupportOwnerClassName(ownerIdentity, WINRT_AUTHORING_MODULE_ACTIVATION_FACTORY_PLAN_CLASS_NAME)

internal fun winRtModulePlatformAbiCallClassName(ownerIdentity: String?): ClassName =
    winRtSupportOwnerClassName(
        ownerIdentity,
        ClassName("io.github.composefluent.winrt.projections.support", "WinRTModulePlatformAbiCall"),
    )

internal fun winRtGenericAbiSupportFileName(ownerIdentity: String?): String {
    val suffix = winRtSupportOwnerIdentifierSuffix(ownerIdentity) ?: return "WinRTGenericAbiSupport"
    return "WinRTGenericAbiSupport_$suffix"
}

internal fun winRtEventProjectionHelperFilePrefix(ownerIdentity: String?): String {
    val suffix = winRtSupportOwnerIdentifierSuffix(ownerIdentity) ?: return "WinRTEventProjectionHelper"
    return "WinRTEventProjectionHelper_$suffix"
}

internal fun winRtProjectionSupportAnchorFileName(ownerIdentity: String?): String {
    val suffix = winRtSupportOwnerIdentifierSuffix(ownerIdentity) ?: return "WinRTProjectionSupportAnchor"
    return "WinRTProjectionSupportAnchor_$suffix"
}

internal fun winRtNamespaceAdditionsClassName(ownerIdentity: String?): ClassName =
    winRtSupportOwnerClassName(ownerIdentity, WINRT_NAMESPACE_ADDITIONS_CLASS_NAME)

private fun winRtSupportOwnerClassName(ownerIdentity: String?, defaultClassName: ClassName): ClassName {
    val suffix = winRtSupportOwnerIdentifierSuffix(ownerIdentity) ?: return defaultClassName
    return ClassName(defaultClassName.packageName, "${defaultClassName.simpleName}_$suffix")
}

internal fun winRtSupportOwnerIdentifierSuffix(ownerIdentity: String?): String? =
    ownerIdentity
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.toKotlinSupportIdentifierSuffix()
        ?.takeIf(String::isNotEmpty)

private fun String.toKotlinSupportIdentifierSuffix(): String =
    buildString {
        this@toKotlinSupportIdentifierSuffix.forEach { char ->
            append(if (char.isLetterOrDigit()) char else '_')
        }
    }.trim('_')
        .replace(Regex("_+"), "_")
        .let { suffix ->
            if (suffix.firstOrNull()?.isDigit() == true) "_$suffix" else suffix
        }
internal val JVM_INLINE_CLASS_NAME = JvmInline::class.asClassName()
internal val JVM_FIELD_CLASS_NAME = JvmField::class.asClassName()
internal val HRESULT_CLASS_NAME = HResult::class.asClassName()
internal val HSTRING_CLASS_NAME = HString::class.asClassName()
internal val IID_CLASS_NAME = IID::class.asClassName()
internal val IUNKNOWN_REFERENCE_CLASS_NAME = IUnknownReference::class.asClassName()
internal val IINSPECTABLE_REFERENCE_CLASS_NAME = ClassName("io.github.composefluent.winrt.runtime", "IInspectableReference")
internal val IWINRT_OBJECT_CLASS_NAME = IWinRTObject::class.asClassName()
internal val KNOWN_HRESULTS_CLASS_NAME = KnownHResults::class.asClassName()
internal val MARSHALER_CLASS_NAME = Marshaler::class.asClassName()
internal val WINRT_ABI_ARRAY_CLASS_NAME = WinRtAbiArray::class.asClassName()
internal val NATIVE_STRING_MARSHALER_CLASS_NAME = NativeStringMarshaller::class.asClassName()
internal val PLATFORM_ABI_CLASS_NAME = PlatformAbi::class.asClassName()
internal val PARAMETERIZED_INTERFACE_ID_CLASS_NAME = ParameterizedInterfaceId::class.asClassName()
internal val PROJECTIONS_CLASS_NAME = Projections::class.asClassName()
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
internal val WINRT_ASYNC_INTERFACE_IDS_CLASS_NAME = WinRtAsyncInterfaceIds::class.asClassName()
internal val WINRT_ASYNC_OPERATION_REFERENCE_CLASS_NAME = WinRtAsyncOperationReference::class.asClassName()
internal val WINRT_ASYNC_OPERATION_WITH_PROGRESS_REFERENCE_CLASS_NAME = WinRtAsyncOperationWithProgressReference::class.asClassName()
internal val WINRT_ASYNC_OPERATION_WITH_PROGRESS_VFTBL_SLOTS_CLASS_NAME = WinRtAsyncOperationWithProgressVftblSlots::class.asClassName()
internal val WINRT_ASYNC_OPERATION_VFTBL_SLOTS_CLASS_NAME = WinRtAsyncOperationVftblSlots::class.asClassName()
internal val WINRT_ASYNC_PROJECTION_INTEROP_CLASS_NAME = WinRtAsyncProjectionInterop::class.asClassName()
internal val WINRT_READ_ONLY_DICTIONARY_PROJECTION_CLASS_NAME = WinRtReadOnlyDictionaryProjection::class.asClassName()
internal val WINRT_READ_ONLY_LIST_PROJECTION_CLASS_NAME = WinRtReadOnlyListProjection::class.asClassName()
internal val WINRT_REFERENCE_ARRAY_PROJECTION_CLASS_NAME = WinRtReferenceArrayProjection::class.asClassName()
internal val WINRT_REFERENCE_PROJECTION_CLASS_NAME = WinRtReferenceProjection::class.asClassName()
internal val WINRT_REFERENCE_PROJECTION_INTEROP_CLASS_NAME = WinRtReferenceProjectionInterop::class.asClassName()
internal val WINRT_REFERENCE_VALUE_ADAPTER_CLASS_NAME = WinRtReferenceValueAdapter::class.asClassName()
internal val WINRT_REFERENCE_VALUE_ADAPTERS_CLASS_NAME = WinRtReferenceValueAdapters::class.asClassName()
internal val WINRT_PROPERTY_VALUE_PROJECTION_CLASS_NAME = WinRtPropertyValueProjection::class.asClassName()
internal val WINRT_GENERIC_PARAMETER_PROJECTION_CLASS_NAME = WinRtGenericParameterProjection::class.asClassName()
internal val WINRT_GENERIC_ABI_SUPPORT_INTRINSIC_CLASS_NAME = WinRtGenericAbiSupportIntrinsic::class.asClassName()
internal val WINRT_GENERIC_TYPE_INSTANTIATION_SUPPORT_INTRINSIC_CLASS_NAME =
    WinRtGenericTypeInstantiationSupportIntrinsic::class.asClassName()
internal val WINRT_AUTHORING_SUPPORT_INTRINSIC_CLASS_NAME = WinRtAuthoringSupportIntrinsic::class.asClassName()
internal val WINRT_PROJECTION_INTRINSIC_CLASS_NAME = WinRtProjectionIntrinsic::class.asClassName()
internal val WINRT_PROJECTION_SUPPORT_INTRINSIC_CLASS_NAME = WinRtProjectionSupportIntrinsic::class.asClassName()
internal val WINRT_KEY_VALUE_PAIR_ADAPTER_FUNCTION_NAME =
    MemberName("io.github.composefluent.winrt.runtime", "winRtKeyValuePairAdapter")
internal val WINRT_OBJECT_MARSHALER_FUNCTION_NAME =
    MemberName("io.github.composefluent.winrt.runtime", "winRtObjectMarshaler")
internal val WINRT_PROJECTION_MARSHALER_FUNCTION_NAME =
    MemberName("io.github.composefluent.winrt.runtime", "winRtProjectionMarshaler")
internal val WINRT_AS_FUNCTION_NAME =
    MemberName("io.github.composefluent.winrt.runtime", "winrtAs")
internal val ACQUIRE_INTERFACE_REFERENCE_FUNCTION_NAME =
    MemberName("io.github.composefluent.winrt.runtime", "acquireInterfaceReference")
internal val WINRT_PROPERTY_CHANGED_EVENT_ARGS_FROM_ABI_FUNCTION_NAME =
    MemberName("io.github.composefluent.winrt.runtime", "winRtPropertyChangedEventArgsFromAbi")
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
internal val WINRT_OBJECT_MARSHALLER_CLASS_NAME = WinRtObjectMarshaller::class.asClassName()
internal val WINRT_PROJECTED_DELEGATE_CLASS_NAME = WinRtProjectedDelegate::class.asClassName()
internal val WINRT_EVENT_SOURCE_CLASS_NAME = EventSource::class.asClassName()
internal val WINRT_EVENT_PROJECTION_HELPERS_CLASS_NAME =
    ClassName("io.github.composefluent.winrt.projections.support", "WinRTEventProjectionHelpers")
internal val WINRT_CLOSABLE_OBJECT_CLASS_NAME = WinRtClosableObject::class.asClassName()
internal val WINRT_COMPOSABLE_OBJECT_CLASS_NAME = WinRtComposableObject::class.asClassName()
internal val WINRT_COMPOSABLE_OBJECT_REFERENCE_CLASS_NAME = WinRtComposableObjectReference::class.asClassName()
internal val WINRT_ACTIVATION_FACTORY_CLASS_NAME = WinRtActivationFactory::class.asClassName()
internal val ATTRIBUTE_CLASS_NAME = Annotation::class.asClassName()
internal val ABSTRACT_LIST_CLASS_NAME = AbstractList::class.asClassName()
internal val ABSTRACT_MAP_CLASS_NAME = AbstractMap::class.asClassName()
internal val ABSTRACT_MUTABLE_LIST_CLASS_NAME = ClassName("kotlin.collections", "AbstractMutableList")
internal val ABSTRACT_MUTABLE_MAP_CLASS_NAME = ClassName("kotlin.collections", "AbstractMutableMap")
internal val ABSTRACT_MUTABLE_SET_CLASS_NAME = ClassName("kotlin.collections", "AbstractMutableSet")
internal val WINRT_URI_CLASS_NAME = WinRtUri::class.asClassName()
internal val KOTLIN_INSTANT_CLASS_NAME = ClassName("kotlin.time", "Instant")
internal val KOTLIN_DURATION_CLASS_NAME = ClassName("kotlin.time", "Duration")
internal val KOTLIN_DURATION_ALIAS_CLASS_NAME = ClassName("", "TimeDuration")
internal val KCLASS_STAR_TYPE_NAME = KClass::class.asClassName().parameterizedBy(STAR)
internal val AUTO_CLOSEABLE_CLASS_NAME = ClassName("kotlin", "AutoCloseable")
internal val ILLEGAL_STATE_EXCEPTION_CLASS_NAME = IllegalStateException::class.asClassName()
internal val NO_SUCH_ELEMENT_EXCEPTION_CLASS_NAME = ClassName("kotlin", "NoSuchElementException")
internal val LAZY_THREAD_SAFETY_MODE_CLASS_NAME = LazyThreadSafetyMode::class.asClassName()
internal val LIST_CLASS_NAME = ClassName("kotlin.collections", "List")
internal val MUTABLE_LIST_CLASS_NAME = ClassName("kotlin.collections", "MutableList")
internal val MUTABLE_MAP_CLASS_NAME = ClassName("kotlin.collections", "MutableMap")
internal val MUTABLE_COLLECTION_CLASS_NAME = ClassName("kotlin.collections", "MutableCollection")
internal val MUTABLE_ITERATOR_CLASS_NAME = ClassName("kotlin.collections", "MutableIterator")
internal val MUTABLE_LIST_ITERATOR_CLASS_NAME = ClassName("kotlin.collections", "MutableListIterator")
internal val MUTABLE_SET_CLASS_NAME = ClassName("kotlin.collections", "MutableSet")
internal val RAW_ADDRESS_CLASS_NAME = RawAddress::class.asClassName()
internal val NATIVE_ABI_LAYOUT_CLASS_NAME = NativeAbiLayout::class.asClassName()
internal val NATIVE_NESTED_STRUCT_FIELD_SPEC_CLASS_NAME = NativeNestedStructFieldSpec::class.asClassName()
internal val NATIVE_SCALAR_FIELD_SPEC_CLASS_NAME = NativeScalarFieldSpec::class.asClassName()
internal val NATIVE_STRUCT_ADAPTER_CLASS_NAME = NativeStructAdapter::class.asClassName()
internal val NATIVE_STRUCT_LAYOUT_CLASS_NAME = NativeStructLayout::class.asClassName()
internal val NATIVE_STRUCT_SCALAR_KIND_CLASS_NAME = NativeStructScalarKind::class.asClassName()
internal val EVENT_REGISTRATION_TOKEN_CLASS_NAME = EventRegistrationToken::class.asClassName()
internal val EXCEPTION_HELPERS_CLASS_NAME = ExceptionHelpers::class.asClassName()
internal val EXCEPTION_CLASS_NAME = ClassName("kotlin", "Exception")
internal val EVENT_HANDLER_CALLBACK_CLASS_NAME = ClassName("io.github.composefluent.winrt.runtime", "EventHandlerCallback")
internal val WINRT_COMMAND_CLASS_NAME = ClassName("io.github.composefluent.winrt.runtime", "WinRtCommand")
internal val WINRT_PROPERTY_CHANGED_NOTIFIER_CLASS_NAME = ClassName("io.github.composefluent.winrt.runtime", "WinRtPropertyChangedNotifier")
internal val WINRT_PROPERTY_CHANGED_NOTIFIER_PROJECTION_CLASS_NAME = ClassName("io.github.composefluent.winrt.runtime", "WinRtPropertyChangedNotifierProjection")
internal val WINRT_PROPERTY_CHANGED_EVENT_ARGS_CLASS_NAME = ClassName("io.github.composefluent.winrt.runtime", "WinRtPropertyChangedEventArgs")
internal val WINRT_PROPERTY_CHANGED_HANDLER_CLASS_NAME = ClassName("io.github.composefluent.winrt.runtime", "WinRtPropertyChangedHandler")
internal val WINRT_COLLECTION_CHANGED_NOTIFIER_CLASS_NAME = ClassName("io.github.composefluent.winrt.runtime", "WinRtCollectionChangedNotifier")
internal val WINRT_NOTIFY_COLLECTION_CHANGED_ACTION_CLASS_NAME = ClassName("io.github.composefluent.winrt.runtime", "WinRtNotifyCollectionChangedAction")
internal val WINRT_NOTIFY_COLLECTION_CHANGED_EVENT_ARGS_CLASS_NAME = ClassName("io.github.composefluent.winrt.runtime", "WinRtNotifyCollectionChangedEventArgs")
internal val WINRT_COLLECTION_CHANGED_HANDLER_CLASS_NAME = ClassName("io.github.composefluent.winrt.runtime", "WinRtCollectionChangedHandler")
internal val WINRT_DATA_ERROR_INFO_CLASS_NAME = ClassName("io.github.composefluent.winrt.runtime", "WinRtDataErrorInfo")
internal val WINRT_DATA_ERROR_INFO_PROJECTION_CLASS_NAME = ClassName("io.github.composefluent.winrt.runtime", "WinRtDataErrorInfoProjection")
internal val WINRT_DATA_ERRORS_CHANGED_EVENT_ARGS_CLASS_NAME = ClassName("io.github.composefluent.winrt.runtime", "WinRtDataErrorsChangedEventArgs")
internal val WINRT_DATA_ERRORS_CHANGED_HANDLER_CLASS_NAME = ClassName("io.github.composefluent.winrt.runtime", "WinRtDataErrorsChangedHandler")
internal val WINRT_SERVICE_PROVIDER_CLASS_NAME = ClassName("io.github.composefluent.winrt.runtime", "WinRtServiceProvider")
internal val WINRT_ATTRIBUTE_USAGE_CLASS_NAME = WinRtAttributeUsage::class.asClassName()
internal val WINRT_CONTRACT_VERSION_CLASS_NAME = WinRtContractVersion::class.asClassName()
internal val WINRT_DEFAULT_OVERLOAD_CLASS_NAME = WinRtDefaultOverload::class.asClassName()
internal val WINRT_EXPERIMENTAL_CLASS_NAME = WinRtExperimental::class.asClassName()
internal val WINRT_OVERLOAD_CLASS_NAME = WinRtOverload::class.asClassName()
internal val WINRT_SUPPORTED_OS_PLATFORM_CLASS_NAME = WinRtSupportedOSPlatform::class.asClassName()
internal val KOTLIN_UBYTE_CLASS_NAME = ClassName("kotlin", "UByte")
internal val KOTLIN_UINT_CLASS_NAME = ClassName("kotlin", "UInt")
internal val KOTLIN_ULONG_CLASS_NAME = ClassName("kotlin", "ULong")
internal val KOTLIN_USHORT_CLASS_NAME = ClassName("kotlin", "UShort")

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

enum class KotlinProjectionGenerationLayout {
    SingleSourceSet,
    ExpectActualJvm,
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
    val classMemberMergeDescriptor: WinRtClassMemberMergeDescriptor? = null,
    val genericAbiClassInitializationDescriptor: WinRtGenericAbiClassInitializationDescriptor? = null,
    val requiredInterfaceAugmentationDescriptor: WinRtRequiredInterfaceAugmentationDescriptor? = null,
    val fastAbiClassDescriptor: WinRtFastAbiClassDescriptor? = null,
    val moduleActivationAndAuthoringDescriptor: WinRtModuleActivationAndAuthoringDescriptor? = null,
    val projectedAttributes: List<WinRtProjectedAttributeDescriptor> = emptyList(),
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

data class KotlinProjectionSlotLiteralKey(
    val interfaceQualifiedName: String,
    val slotConstantName: String,
)

data class KotlinProjectionInstanceMemberBinding(
    val bindingName: String,
    val ownerInterfaceQualifiedName: String,
    val ownerCachePropertyName: String,
    val slotInterfaceQualifiedName: String,
    val slotConstantName: String,
    val slot: Int? = null,
    val returnBinding: KotlinProjectionAbiTypeBinding,
    val parameterBindings: List<KotlinProjectionAbiParameterBinding> = emptyList(),
    val signatureDescriptor: WinRtSignatureWriterDescriptor? = null,
    val marshalerPlanDescriptor: WinRtAbiMarshalerPlanDescriptor? = null,
    val projectedAttributes: List<WinRtProjectedAttributeDescriptor> = emptyList(),
    val suppressHResultCheck: Boolean = false,
)

data class KotlinProjectionStaticMemberBinding(
    val bindingName: String,
    val ownerInterfaceQualifiedName: String,
    val ownerAccessorName: String,
    val ownerCachePropertyName: String,
    val slotInterfaceQualifiedName: String,
    val slotConstantName: String,
    val slot: Int? = null,
    val returnBinding: KotlinProjectionAbiTypeBinding,
    val parameterBindings: List<KotlinProjectionAbiParameterBinding> = emptyList(),
    val signatureDescriptor: WinRtSignatureWriterDescriptor? = null,
    val marshalerPlanDescriptor: WinRtAbiMarshalerPlanDescriptor? = null,
    val projectedAttributes: List<WinRtProjectedAttributeDescriptor> = emptyList(),
    val suppressHResultCheck: Boolean = false,
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
    PropertyValue,
    Array,
    ProjectedInterface,
    ProjectedRuntimeClass,
    Struct,
    Delegate,
    Object,
    GenericParameter,
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
    val runtimeOwnedProjection: Boolean = false,
    val simpleAbiLookup: Boolean = false,
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
    val toAbiFunctionName: String? = null,
    val fromAbiCarrierFunctionName: String? = null,
    val abiArgumentKind: KotlinProjectionComArgumentKind? = null,
    val abiLayoutExpression: CodeBlock? = null,
)

internal data class KotlinProjectionCustomObjectAbi(
    val interfaceId: Guid,
    val typeHandleName: String,
    val fromAbiFunctionName: String = "objectFromAbi",
    val createReferenceFunctionName: String = "createObjectReference",
)

internal data class KotlinProjectionIntegralAbiDescriptor(
    val kotlinTypeName: TypeName,
    val abiValueKind: KotlinProjectionAbiValueKind,
    val delegateValueKindName: String,
    val abiCarrierTypeName: TypeName,
    val platformReadFunctionName: String,
    val platformWriteFunctionName: String,
    val typeSignatureFunctionName: String,
    val projectionIntrinsicShapeName: String? = null,
    val projectionIntrinsicGetterName: String? = null,
    val projectionIntrinsicSetterName: String? = null,
    val abiSizeBytes: Int,
    val comArgumentKind: KotlinProjectionComArgumentKind,
    val argumentConversionSuffix: String = "",
    val carrierToKotlinConversionSuffix: String = "",
    val literalRenderer: (ULong) -> CodeBlock,
)

/**
 * `.cswinrt/src/cswinrt/helpers.h` converges mapped WinRT type decisions through `mapped_type` plus
 * `get_mapped_type(...)`. Keep Kotlin projection planning on the same ownership model instead of
 * repeating the same collection/async/custom-type tables across unrelated helpers.
 */
internal val MAPPED_TYPES: List<KotlinProjectionMappedType> = listOf(
    KotlinProjectionMappedType(
        "System.Object",
        { ANY.copy(nullable = true) },
        runtimeOwnedProjection = true,
        simpleAbiLookup = true,
        descriptionName = "Object",
    ),
    KotlinProjectionMappedType("WinRT.Interop.HWND", { Long::class.asClassName() }, simpleAbiLookup = true, descriptionName = "HWND"),
    KotlinProjectionMappedType(
        "Windows.Foundation.DateTime",
        { KOTLIN_INSTANT_CLASS_NAME },
        abiValueKind = KotlinProjectionAbiValueKind.Struct,
        customStructAbi = KotlinProjectionCustomStructAbi(
            WINRT_SYSTEM_PROJECTION_MARSHALERS_CLASS_NAME,
            8,
            "dateTimeFromAbi",
            "copyDateTimeTo",
            toAbiFunctionName = "dateTimeToAbi",
            fromAbiCarrierFunctionName = "dateTimeFromAbiValue",
            abiArgumentKind = KotlinProjectionComArgumentKind.Int64,
        ),
        simpleAbiLookup = true,
        descriptionName = "DateTime",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.TimeSpan",
        { KOTLIN_DURATION_CLASS_NAME },
        abiValueKind = KotlinProjectionAbiValueKind.Struct,
        customStructAbi = KotlinProjectionCustomStructAbi(
            WINRT_SYSTEM_PROJECTION_MARSHALERS_CLASS_NAME,
            8,
            "timeSpanFromAbi",
            "copyTimeSpanTo",
            toAbiFunctionName = "timeSpanToAbi",
            fromAbiCarrierFunctionName = "timeSpanFromAbiValue",
            abiArgumentKind = KotlinProjectionComArgumentKind.Int64,
        ),
        simpleAbiLookup = true,
        descriptionName = "TimeSpan",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.Uri",
        { WINRT_URI_CLASS_NAME },
        abiValueKind = KotlinProjectionAbiValueKind.ProjectedRuntimeClass,
        customObjectAbi = KotlinProjectionCustomObjectAbi(
            interfaceId = Guid("9E365E57-48B2-4160-956F-C7385120BBFC"),
            typeHandleName = "io.github.composefluent.winrt.runtime.WinRtUri",
            fromAbiFunctionName = "uriFromAbi",
        ),
        simpleAbiLookup = true,
        descriptionName = "Uri",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.EventHandler",
        { arguments -> EVENT_HANDLER_CALLBACK_CLASS_NAME.parameterizedBy(arguments.singleOrNull() ?: ANY.copy(nullable = true)) },
        simpleAbiLookup = true,
        descriptionName = "EventHandler",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.EventRegistrationToken",
        { EVENT_REGISTRATION_TOKEN_CLASS_NAME },
        abiValueKind = KotlinProjectionAbiValueKind.Struct,
        runtimeOwnedProjection = true,
        simpleAbiLookup = true,
        descriptionName = "EventRegistrationToken",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.HResult",
        { EXCEPTION_CLASS_NAME },
        abiValueKind = KotlinProjectionAbiValueKind.Struct,
        customStructAbi = KotlinProjectionCustomStructAbi(
            WINRT_SYSTEM_PROJECTION_MARSHALERS_CLASS_NAME,
            4,
            "hResultFromAbi",
            "copyHResultTo",
            toAbiFunctionName = "hResultToAbi",
            fromAbiCarrierFunctionName = "hResultFromAbiValue",
            abiArgumentKind = KotlinProjectionComArgumentKind.Int32,
        ),
        runtimeOwnedProjection = true,
        simpleAbiLookup = true,
        descriptionName = "HResult",
    ),
    KotlinProjectionMappedType(
        "Windows.UI.Xaml.Interop.TypeName",
        { KCLASS_STAR_TYPE_NAME.copy(nullable = true) },
        abiValueKind = KotlinProjectionAbiValueKind.Struct,
        customStructAbi = KotlinProjectionCustomStructAbi(
            WINRT_SYSTEM_PROJECTION_MARSHALERS_CLASS_NAME,
            16,
            "typeNameFromAbi",
            "copyTypeNameTo",
            "disposeTypeNameAbi",
            abiLayoutExpression = CodeBlock.of("%T.TYPE_NAME", NATIVE_ABI_LAYOUT_CLASS_NAME),
        ),
        simpleAbiLookup = true,
        descriptionName = "TypeName",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.IClosable",
        { AUTO_CLOSEABLE_CLASS_NAME },
        runtimeOwnedProjection = true,
        simpleAbiLookup = true,
        descriptionName = "IClosable",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.IReference",
        { arguments -> arguments.single().copy(nullable = true) },
        abiValueKind = KotlinProjectionAbiValueKind.Reference,
        simpleAbiLookup = true,
        descriptionName = "IReference",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.IReferenceArray",
        { arguments -> Array::class.asClassName().parameterizedBy(arguments.single().copy(nullable = true)) },
        abiValueKind = KotlinProjectionAbiValueKind.ReferenceArray,
        simpleAbiLookup = true,
        descriptionName = "IReferenceArray",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.IPropertyValue",
        { ANY.copy(nullable = true) },
        abiValueKind = KotlinProjectionAbiValueKind.PropertyValue,
        runtimeOwnedProjection = true,
        simpleAbiLookup = true,
        descriptionName = "IPropertyValue",
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
                ClassName("windows.foundation.collections", "IKeyValuePair")
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
    KotlinProjectionMappedType("Microsoft.UI.Xaml.IXamlServiceProvider", { WINRT_SERVICE_PROVIDER_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("68B3A2DF-8173-539F-B524-C8A2348F5AFB"), "io.github.composefluent.winrt.runtime.WinRtServiceProvider"), descriptionName = "IXamlServiceProvider"),
    KotlinProjectionMappedType("Microsoft.UI.Xaml.Data.DataErrorsChangedEventArgs", { WINRT_DATA_ERRORS_CHANGED_EVENT_ARGS_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedRuntimeClass, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("D026DD64-5F26-5F15-A86A-0DEC8A431796"), "io.github.composefluent.winrt.runtime.WinRtDataErrorsChangedEventArgs"), descriptionName = "DataErrorsChangedEventArgs"),
    KotlinProjectionMappedType("Microsoft.UI.Xaml.Data.INotifyDataErrorInfo", { WINRT_DATA_ERROR_INFO_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("0EE6C2CC-273E-567D-BC0A-1DD87EE51EBA"), "io.github.composefluent.winrt.runtime.WinRtDataErrorInfo"), descriptionName = "INotifyDataErrorInfo"),
    KotlinProjectionMappedType("Microsoft.UI.Xaml.Data.INotifyPropertyChanged", { WINRT_PROPERTY_CHANGED_NOTIFIER_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("90B17601-B065-586E-83D9-9ADC3A695284"), "io.github.composefluent.winrt.runtime.WinRtPropertyChangedNotifier"), descriptionName = "INotifyPropertyChanged"),
    KotlinProjectionMappedType("Microsoft.UI.Xaml.Data.PropertyChangedEventArgs", { WINRT_PROPERTY_CHANGED_EVENT_ARGS_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedRuntimeClass, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("63D0C952-396B-54F4-AF8C-BA8724A427BF"), "io.github.composefluent.winrt.runtime.WinRtPropertyChangedEventArgs"), descriptionName = "PropertyChangedEventArgs"),
    KotlinProjectionMappedType("Microsoft.UI.Xaml.Data.PropertyChangedEventHandler", { WINRT_PROPERTY_CHANGED_HANDLER_CLASS_NAME }, descriptionName = "PropertyChangedEventHandler"),
    KotlinProjectionMappedType("Microsoft.UI.Xaml.Input.ICommand", { WINRT_COMMAND_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("E5AF3542-CA67-4081-995B-709DD13792DF"), "io.github.composefluent.winrt.runtime.WinRtCommand"), descriptionName = "ICommand"),
    KotlinProjectionMappedType("Microsoft.UI.Xaml.Interop.ICommand", { WINRT_COMMAND_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("E5AF3542-CA67-4081-995B-709DD13792DF"), "io.github.composefluent.winrt.runtime.WinRtCommand"), descriptionName = "ICommand"),
    KotlinProjectionMappedType(
        "Microsoft.UI.Xaml.Interop.IBindableIterable",
        { Iterable::class.asClassName().parameterizedBy(ANY.copy(nullable = true)) },
        abiValueKind = KotlinProjectionAbiValueKind.MappedBindableIterable,
        readOnlyCollectionKind = KotlinProjectionReadOnlyCollectionKind.Iterable,
        runtimeOwnedProjection = true,
        descriptionName = "IBindableIterable",
    ),
    KotlinProjectionMappedType(
        "Microsoft.UI.Xaml.Interop.IBindableVectorView",
        { List::class.asClassName().parameterizedBy(ANY.copy(nullable = true)) },
        abiValueKind = KotlinProjectionAbiValueKind.MappedBindableVectorView,
        readOnlyCollectionKind = KotlinProjectionReadOnlyCollectionKind.VectorView,
        runtimeOwnedProjection = true,
        descriptionName = "IBindableVectorView",
    ),
    KotlinProjectionMappedType(
        "Microsoft.UI.Xaml.Interop.IBindableVector",
        { MUTABLE_LIST_CLASS_NAME.parameterizedBy(ANY.copy(nullable = true)) },
        abiValueKind = KotlinProjectionAbiValueKind.MappedBindableVector,
        mutableCollectionKind = KotlinProjectionMutableCollectionKind.Vector,
        runtimeOwnedProjection = true,
        descriptionName = "IBindableVector",
    ),
    KotlinProjectionMappedType("Microsoft.UI.Xaml.Interop.INotifyCollectionChanged", { WINRT_COLLECTION_CHANGED_NOTIFIER_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("530155E1-28A5-5693-87CE-30724D95A06D"), "io.github.composefluent.winrt.runtime.WinRtCollectionChangedNotifier"), descriptionName = "INotifyCollectionChanged"),
    KotlinProjectionMappedType(
        "Microsoft.UI.Xaml.Interop.NotifyCollectionChangedAction",
        { WINRT_NOTIFY_COLLECTION_CHANGED_ACTION_CLASS_NAME },
        runtimeOwnedProjection = true,
        descriptionName = "NotifyCollectionChangedAction",
    ),
    KotlinProjectionMappedType("Microsoft.UI.Xaml.Interop.NotifyCollectionChangedEventArgs", { WINRT_NOTIFY_COLLECTION_CHANGED_EVENT_ARGS_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedRuntimeClass, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("DA049FF2-D2E0-5FE8-8C7B-F87F26060B6F"), "io.github.composefluent.winrt.runtime.WinRtNotifyCollectionChangedEventArgs"), descriptionName = "NotifyCollectionChangedEventArgs"),
    KotlinProjectionMappedType("Microsoft.UI.Xaml.Interop.NotifyCollectionChangedEventHandler", { WINRT_COLLECTION_CHANGED_HANDLER_CLASS_NAME }, descriptionName = "NotifyCollectionChangedEventHandler"),
    KotlinProjectionMappedType("Windows.UI.Xaml.IXamlServiceProvider", { WINRT_SERVICE_PROVIDER_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("68B3A2DF-8173-539F-B524-C8A2348F5AFB"), "io.github.composefluent.winrt.runtime.WinRtServiceProvider"), descriptionName = "IXamlServiceProvider"),
    KotlinProjectionMappedType("Windows.UI.Xaml.Data.DataErrorsChangedEventArgs", { WINRT_DATA_ERRORS_CHANGED_EVENT_ARGS_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedRuntimeClass, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("D026DD64-5F26-5F15-A86A-0DEC8A431796"), "io.github.composefluent.winrt.runtime.WinRtDataErrorsChangedEventArgs"), descriptionName = "DataErrorsChangedEventArgs"),
    KotlinProjectionMappedType("Windows.UI.Xaml.Data.INotifyDataErrorInfo", { WINRT_DATA_ERROR_INFO_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("0EE6C2CC-273E-567D-BC0A-1DD87EE51EBA"), "io.github.composefluent.winrt.runtime.WinRtDataErrorInfo"), descriptionName = "INotifyDataErrorInfo"),
    KotlinProjectionMappedType("Windows.UI.Xaml.Data.INotifyPropertyChanged", { WINRT_PROPERTY_CHANGED_NOTIFIER_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("CF75D69C-F2F4-486B-B302-BB4C09BAEBFA"), "io.github.composefluent.winrt.runtime.WinRtPropertyChangedNotifier"), descriptionName = "INotifyPropertyChanged"),
    KotlinProjectionMappedType("Windows.UI.Xaml.Data.PropertyChangedEventArgs", { WINRT_PROPERTY_CHANGED_EVENT_ARGS_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedRuntimeClass, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("4F33A9A0-5CF4-47A4-B16F-D7FAAF17457E"), "io.github.composefluent.winrt.runtime.WinRtPropertyChangedEventArgs"), descriptionName = "PropertyChangedEventArgs"),
    KotlinProjectionMappedType("Windows.UI.Xaml.Data.PropertyChangedEventHandler", { WINRT_PROPERTY_CHANGED_HANDLER_CLASS_NAME }, descriptionName = "PropertyChangedEventHandler"),
    KotlinProjectionMappedType("Windows.UI.Xaml.Input.ICommand", { WINRT_COMMAND_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("E5AF3542-CA67-4081-995B-709DD13792DF"), "io.github.composefluent.winrt.runtime.WinRtCommand"), descriptionName = "ICommand"),
    KotlinProjectionMappedType("Windows.UI.Xaml.Interop.ICommand", { WINRT_COMMAND_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("E5AF3542-CA67-4081-995B-709DD13792DF"), "io.github.composefluent.winrt.runtime.WinRtCommand"), descriptionName = "ICommand"),
    KotlinProjectionMappedType(
        "Windows.UI.Xaml.Interop.IBindableIterable",
        { Iterable::class.asClassName().parameterizedBy(ANY.copy(nullable = true)) },
        abiValueKind = KotlinProjectionAbiValueKind.MappedBindableIterable,
        readOnlyCollectionKind = KotlinProjectionReadOnlyCollectionKind.Iterable,
        runtimeOwnedProjection = true,
        descriptionName = "IBindableIterable",
    ),
    KotlinProjectionMappedType(
        "Windows.UI.Xaml.Interop.IBindableVectorView",
        { List::class.asClassName().parameterizedBy(ANY.copy(nullable = true)) },
        abiValueKind = KotlinProjectionAbiValueKind.MappedBindableVectorView,
        readOnlyCollectionKind = KotlinProjectionReadOnlyCollectionKind.VectorView,
        runtimeOwnedProjection = true,
        descriptionName = "IBindableVectorView",
    ),
    KotlinProjectionMappedType(
        "Windows.UI.Xaml.Interop.IBindableVector",
        { MUTABLE_LIST_CLASS_NAME.parameterizedBy(ANY.copy(nullable = true)) },
        abiValueKind = KotlinProjectionAbiValueKind.MappedBindableVector,
        mutableCollectionKind = KotlinProjectionMutableCollectionKind.Vector,
        runtimeOwnedProjection = true,
        descriptionName = "IBindableVector",
    ),
    KotlinProjectionMappedType("Windows.UI.Xaml.Interop.INotifyCollectionChanged", { WINRT_COLLECTION_CHANGED_NOTIFIER_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("28B167D5-1A31-465B-9B25-D5C3AE686C40"), "io.github.composefluent.winrt.runtime.WinRtCollectionChangedNotifier"), descriptionName = "INotifyCollectionChanged"),
    KotlinProjectionMappedType(
        "Windows.UI.Xaml.Interop.NotifyCollectionChangedAction",
        { WINRT_NOTIFY_COLLECTION_CHANGED_ACTION_CLASS_NAME },
        runtimeOwnedProjection = true,
        descriptionName = "NotifyCollectionChangedAction",
    ),
    KotlinProjectionMappedType("Windows.UI.Xaml.Interop.NotifyCollectionChangedEventArgs", { WINRT_NOTIFY_COLLECTION_CHANGED_EVENT_ARGS_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedRuntimeClass, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("4CF68D33-E3F2-4964-B85E-945B4F7E2F21"), "io.github.composefluent.winrt.runtime.WinRtNotifyCollectionChangedEventArgs"), descriptionName = "NotifyCollectionChangedEventArgs"),
    KotlinProjectionMappedType("Windows.UI.Xaml.Interop.NotifyCollectionChangedEventHandler", { WINRT_COLLECTION_CHANGED_HANDLER_CLASS_NAME }, descriptionName = "NotifyCollectionChangedEventHandler"),
)

internal val MAPPED_TYPES_BY_ABI_NAME: Map<String, KotlinProjectionMappedType> =
    MAPPED_TYPES.associateBy(KotlinProjectionMappedType::abiQualifiedName)

internal val MAPPED_TYPES_BY_SIMPLE_ABI_NAME: Map<String, KotlinProjectionMappedType> =
    MAPPED_TYPES.filter { mappedType ->
        mappedType.simpleAbiLookup
    }.groupBy { it.abiQualifiedName.substringAfterLast('.') }
        .filterValues { it.size == 1 }
        .mapValues { (_, mappedTypes) -> mappedTypes.single() }

internal val MAPPED_TYPES_BY_ABI_KIND: Map<KotlinProjectionAbiValueKind, KotlinProjectionMappedType> =
    MAPPED_TYPES.mapNotNull { mappedType ->
        mappedType.abiValueKind?.let { abiValueKind -> abiValueKind to mappedType }
    }.toMap()

internal fun KotlinProjectionMappedType.isRuntimeOwnedProjection(): Boolean =
    customStructAbi != null ||
        customObjectAbi != null ||
        runtimeOwnedProjection

internal fun isRuntimeOwnedMappedTypeName(typeName: String): Boolean {
    val rawTypeName = typeName.substringBefore('<').removeSuffix("?")
    return mappedTypeByAbiName(rawTypeName)?.isRuntimeOwnedProjection() == true
}

internal fun kotlinPoetNameLiteral(identifier: String): String =
    identifier.removeSurrounding("`")

internal fun String.escapeAsKotlinIdentifierIfNeeded(): String {
    if (startsWith("`") && endsWith("`")) {
        return this
    }
    return if (this in KOTLIN_CODE_IDENTIFIER_KEYWORDS) "`$this`" else this
}

internal fun generatedLocalIdentifier(prefix: String, identifier: String, suffix: String = ""): String {
    val raw = kotlinPoetNameLiteral(identifier)
    val sanitized = raw.map { char ->
        if (char.isLetterOrDigit() || char == '_') char else '_'
    }.joinToString("")
    return prefix + sanitized.ifEmpty { "value" } + suffix
}

private val KOTLIN_CODE_IDENTIFIER_KEYWORDS = setOf(
    "as",
    "break",
    "class",
    "continue",
    "do",
    "else",
    "false",
    "for",
    "fun",
    "if",
    "in",
    "interface",
    "is",
    "null",
    "object",
    "package",
    "return",
    "super",
    "this",
    "throw",
    "true",
    "try",
    "typealias",
    "typeof",
    "val",
    "var",
    "when",
    "while",
)

internal val INTEGRAL_ABI_DESCRIPTORS: Map<WinRtIntegralType, KotlinProjectionIntegralAbiDescriptor> = mapOf(
    WinRtIntegralType.Int8 to KotlinProjectionIntegralAbiDescriptor(
        kotlinTypeName = Byte::class.asClassName(),
        abiValueKind = KotlinProjectionAbiValueKind.Int8,
        delegateValueKindName = "INT8",
        abiCarrierTypeName = Byte::class.asClassName(),
        platformReadFunctionName = "readInt8",
        platformWriteFunctionName = "writeInt8",
        typeSignatureFunctionName = "int8",
        abiSizeBytes = 1,
        comArgumentKind = KotlinProjectionComArgumentKind.Int8,
        literalRenderer = { valueBits -> CodeBlock.of("%L.toByte()", valueBits.toByte()) },
    ),
    WinRtIntegralType.UInt8 to KotlinProjectionIntegralAbiDescriptor(
        kotlinTypeName = KOTLIN_UBYTE_CLASS_NAME,
        abiValueKind = KotlinProjectionAbiValueKind.UInt8,
        delegateValueKindName = "UINT8",
        abiCarrierTypeName = Byte::class.asClassName(),
        platformReadFunctionName = "readInt8",
        platformWriteFunctionName = "writeInt8",
        typeSignatureFunctionName = "uint8",
        abiSizeBytes = 1,
        comArgumentKind = KotlinProjectionComArgumentKind.Int8,
        argumentConversionSuffix = ".toByte()",
        carrierToKotlinConversionSuffix = ".toUByte()",
        literalRenderer = { valueBits -> CodeBlock.of("%L.toUByte()", valueBits.toUByte()) },
    ),
    WinRtIntegralType.Int16 to KotlinProjectionIntegralAbiDescriptor(
        kotlinTypeName = Short::class.asClassName(),
        abiValueKind = KotlinProjectionAbiValueKind.Int16,
        delegateValueKindName = "INT16",
        abiCarrierTypeName = Short::class.asClassName(),
        platformReadFunctionName = "readInt16",
        platformWriteFunctionName = "writeInt16",
        typeSignatureFunctionName = "int16",
        abiSizeBytes = 2,
        comArgumentKind = KotlinProjectionComArgumentKind.Int16,
        literalRenderer = { valueBits -> CodeBlock.of("%L.toShort()", valueBits.toShort()) },
    ),
    WinRtIntegralType.UInt16 to KotlinProjectionIntegralAbiDescriptor(
        kotlinTypeName = KOTLIN_USHORT_CLASS_NAME,
        abiValueKind = KotlinProjectionAbiValueKind.UInt16,
        delegateValueKindName = "UINT16",
        abiCarrierTypeName = Short::class.asClassName(),
        platformReadFunctionName = "readInt16",
        platformWriteFunctionName = "writeInt16",
        typeSignatureFunctionName = "uint16",
        abiSizeBytes = 2,
        comArgumentKind = KotlinProjectionComArgumentKind.Int16,
        argumentConversionSuffix = ".toShort()",
        carrierToKotlinConversionSuffix = ".toUShort()",
        literalRenderer = { valueBits -> CodeBlock.of("%L.toUShort()", valueBits.toUShort()) },
    ),
    WinRtIntegralType.Int32 to KotlinProjectionIntegralAbiDescriptor(
        kotlinTypeName = Int::class.asClassName(),
        abiValueKind = KotlinProjectionAbiValueKind.Int32,
        delegateValueKindName = "INT32",
        abiCarrierTypeName = Int::class.asClassName(),
        platformReadFunctionName = "readInt32",
        platformWriteFunctionName = "writeInt32",
        typeSignatureFunctionName = "int32",
        projectionIntrinsicShapeName = "Int32",
        projectionIntrinsicGetterName = "getInt32",
        projectionIntrinsicSetterName = "setInt32",
        abiSizeBytes = 4,
        comArgumentKind = KotlinProjectionComArgumentKind.Int32,
        literalRenderer = { valueBits -> CodeBlock.of("%L", valueBits.toInt()) },
    ),
    WinRtIntegralType.UInt32 to KotlinProjectionIntegralAbiDescriptor(
        kotlinTypeName = KOTLIN_UINT_CLASS_NAME,
        abiValueKind = KotlinProjectionAbiValueKind.UInt32,
        delegateValueKindName = "UINT32",
        abiCarrierTypeName = Int::class.asClassName(),
        platformReadFunctionName = "readInt32",
        platformWriteFunctionName = "writeInt32",
        typeSignatureFunctionName = "uint32",
        projectionIntrinsicShapeName = "UInt32",
        projectionIntrinsicGetterName = "getUInt32",
        projectionIntrinsicSetterName = "setUInt32",
        abiSizeBytes = 4,
        comArgumentKind = KotlinProjectionComArgumentKind.Int32,
        argumentConversionSuffix = ".toInt()",
        carrierToKotlinConversionSuffix = ".toUInt()",
        literalRenderer = { valueBits -> CodeBlock.of("%L.toUInt()", valueBits.toUInt()) },
    ),
    WinRtIntegralType.Int64 to KotlinProjectionIntegralAbiDescriptor(
        kotlinTypeName = Long::class.asClassName(),
        abiValueKind = KotlinProjectionAbiValueKind.Int64,
        delegateValueKindName = "INT64",
        abiCarrierTypeName = Long::class.asClassName(),
        platformReadFunctionName = "readInt64",
        platformWriteFunctionName = "writeInt64",
        typeSignatureFunctionName = "int64",
        abiSizeBytes = 8,
        comArgumentKind = KotlinProjectionComArgumentKind.Int64,
        literalRenderer = { valueBits -> CodeBlock.of("%L", "${valueBits.toLong()}L") },
    ),
    WinRtIntegralType.UInt64 to KotlinProjectionIntegralAbiDescriptor(
        kotlinTypeName = KOTLIN_ULONG_CLASS_NAME,
        abiValueKind = KotlinProjectionAbiValueKind.UInt64,
        delegateValueKindName = "UINT64",
        abiCarrierTypeName = Long::class.asClassName(),
        platformReadFunctionName = "readInt64",
        platformWriteFunctionName = "writeInt64",
        typeSignatureFunctionName = "uint64",
        abiSizeBytes = 8,
        comArgumentKind = KotlinProjectionComArgumentKind.Int64,
        argumentConversionSuffix = ".toLong()",
        carrierToKotlinConversionSuffix = ".toULong()",
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

internal fun integralComAbiValueKindCode(type: WinRtIntegralType): CodeBlock =
    CodeBlock.of("%T.%L", COM_ABI_VALUE_KIND_CLASS_NAME, integralAbiDescriptor(type).comArgumentKind.name)

internal fun integralDelegateValueKindCode(type: WinRtIntegralType): CodeBlock =
    CodeBlock.of("%T.%L", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME, integralAbiDescriptor(type).delegateValueKindName)

internal fun integralTypeSignatureCode(type: WinRtIntegralType): CodeBlock =
    CodeBlock.of("%T.%L()", WINRT_TYPE_SIGNATURE_CLASS_NAME, integralAbiDescriptor(type).typeSignatureFunctionName)

internal fun integralProjectionIntrinsicShapeName(type: WinRtIntegralType): String? =
    integralAbiDescriptor(type).projectionIntrinsicShapeName

internal fun integralProjectionIntrinsicGetterName(type: WinRtIntegralType): String? =
    integralAbiDescriptor(type).projectionIntrinsicGetterName

internal fun integralProjectionIntrinsicSetterName(type: WinRtIntegralType): String? =
    integralAbiDescriptor(type).projectionIntrinsicSetterName

internal fun integralAbiSizeExpression(type: WinRtIntegralType): CodeBlock =
    CodeBlock.of("%L", integralAbiDescriptor(type).abiSizeBytes)

internal fun integralKotlinCastExpression(type: WinRtIntegralType, expression: CodeBlock): CodeBlock =
    CodeBlock.of("%L as %T", expression, integralAbiDescriptor(type).kotlinTypeName)

internal fun integralPlatformReadExpression(type: WinRtIntegralType, addressExpression: CodeBlock): CodeBlock {
    val descriptor = integralAbiDescriptor(type)
    val readExpression = CodeBlock.of("%T.%L(%L)", PLATFORM_ABI_CLASS_NAME, descriptor.platformReadFunctionName, addressExpression)
    return if (descriptor.carrierToKotlinConversionSuffix.isEmpty()) {
        readExpression
    } else {
        CodeBlock.of("%L%L", readExpression, descriptor.carrierToKotlinConversionSuffix)
    }
}

internal fun integralPlatformWriteCode(
    type: WinRtIntegralType,
    addressExpression: CodeBlock,
    valueExpression: CodeBlock,
): CodeBlock {
    val descriptor = integralAbiDescriptor(type)
    val abiValueExpression = if (descriptor.argumentConversionSuffix.isEmpty()) {
        valueExpression
    } else {
        CodeBlock.of("%L%L", valueExpression, descriptor.argumentConversionSuffix)
    }
    return CodeBlock.of("%T.%L(%L, %L)", PLATFORM_ABI_CLASS_NAME, descriptor.platformWriteFunctionName, addressExpression, abiValueExpression)
}

internal fun integralAbiCarrierExpression(type: WinRtIntegralType, expression: CodeBlock): CodeBlock {
    val descriptor = integralAbiDescriptor(type)
    val carrierExpression = CodeBlock.of("%L as %T", expression, descriptor.abiCarrierTypeName)
    return if (descriptor.carrierToKotlinConversionSuffix.isEmpty()) {
        carrierExpression
    } else {
        CodeBlock.of("(%L)%L", carrierExpression, descriptor.carrierToKotlinConversionSuffix)
    }
}

internal fun integralResultSlotAllocation(type: WinRtIntegralType, scopeName: String): CodeBlock =
    when (integralAbiDescriptor(type).abiSizeBytes) {
        1 -> CodeBlock.of("%T.allocateInt8Slot(%L)", PLATFORM_ABI_CLASS_NAME, scopeName)
        2 -> CodeBlock.of("%T.allocateBytes(%L, 2)", PLATFORM_ABI_CLASS_NAME, scopeName)
        4 -> CodeBlock.of("%T.allocateInt32Slot(%L)", PLATFORM_ABI_CLASS_NAME, scopeName)
        8 -> CodeBlock.of("%T.allocateInt64Slot(%L)", PLATFORM_ABI_CLASS_NAME, scopeName)
        else -> error("Unsupported WinRT integral ABI width for $type")
    }

data class KotlinProjectionAbiTypeBinding(
    val kind: KotlinProjectionAbiValueKind,
    val typeName: String,
    val resolvedTypeName: String = typeName,
    val sourceTypeKind: WinRtTypeKind? = null,
    val abiSize: Int? = null,
    val abiAlignment: Int? = null,
    val interfaceId: Guid? = null,
    val enumUnderlyingType: WinRtIntegralType? = null,
    val delegateInvokeShape: KotlinProjectionDelegateInvokeShape? = null,
    val typeArguments: List<KotlinProjectionAbiTypeBinding> = emptyList(),
    val structFieldBindings: List<KotlinProjectionAbiTypeBinding> = emptyList(),
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
    val suppressHResultCheck: Boolean = false,
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

internal fun String.isProjectedWinRtInterfaceReferenceName(): Boolean {
    val simpleName = substringAfterLast('.')
    return '.' in this &&
        simpleName.startsWith('I') &&
        (startsWith("Windows.") || startsWith("Microsoft."))
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
