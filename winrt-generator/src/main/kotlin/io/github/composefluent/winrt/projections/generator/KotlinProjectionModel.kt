package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTAbiMarshalerPlanDescriptor
import io.github.composefluent.winrt.metadata.WinRTAbiMarshalerSlotDescriptor
import io.github.composefluent.winrt.metadata.WinRTClassMemberMergeDescriptor
import io.github.composefluent.winrt.metadata.WinRTCustomMappedMemberOutputDescriptor
import io.github.composefluent.winrt.metadata.WinRTEventDefinition
import io.github.composefluent.winrt.metadata.WinRTEventInvokeDescriptor
import io.github.composefluent.winrt.metadata.WinRTFactorySurfaceDescriptor
import io.github.composefluent.winrt.metadata.WinRTFastAbiClassDescriptor
import io.github.composefluent.winrt.metadata.WinRTFieldDefinition
import io.github.composefluent.winrt.metadata.WinRTGenericAbiClassInitializationDescriptor
import io.github.composefluent.winrt.metadata.WinRTGenericAbiInventory
import io.github.composefluent.winrt.metadata.WinRTGenericInstantiationWriterDescriptor
import io.github.composefluent.winrt.metadata.WinRTGuidSignatureDescriptor
import io.github.composefluent.winrt.metadata.WinRTInterfaceImplementationDefinition
import io.github.composefluent.winrt.metadata.WinRTInterfaceMemberSignatureSetDescriptor
import io.github.composefluent.winrt.metadata.WinRTIntegralType
import io.github.composefluent.winrt.metadata.WinRTMetadataProjectionContext
import io.github.composefluent.winrt.metadata.WinRTMetadataProjectionInventory
import io.github.composefluent.winrt.metadata.WinRTMetadataProjectionInventoryBuilder
import io.github.composefluent.winrt.metadata.WinRTProjectedAttributeDescriptor
import io.github.composefluent.winrt.metadata.WinRTMetadataParameterCategory
import io.github.composefluent.winrt.metadata.WinRTModuleActivationAndAuthoringDescriptor
import io.github.composefluent.winrt.metadata.WinRTMethodVtableDescriptor
import io.github.composefluent.winrt.metadata.WinRTMethodDefinition
import io.github.composefluent.winrt.metadata.WinRTNamespace
import io.github.composefluent.winrt.metadata.WinRTObjectReferenceSurfaceDescriptor
import io.github.composefluent.winrt.metadata.WinRTPropertyDefinition
import io.github.composefluent.winrt.metadata.WinRTRequiredInterfaceAugmentationDescriptor
import io.github.composefluent.winrt.metadata.WinRTSignatureWriterDescriptor
import io.github.composefluent.winrt.metadata.WinRTTypeDeclarationDescriptor
import io.github.composefluent.winrt.metadata.WinRTTypeDefinition
import io.github.composefluent.winrt.metadata.WinRTTypeRef
import io.github.composefluent.winrt.metadata.WinRTTypeKind
import io.github.composefluent.winrt.metadata.WinRTMetadataValidationOptions
import io.github.composefluent.winrt.metadata.WinRTMetadataSemanticHelpers
import io.github.composefluent.winrt.metadata.requireValidForProjection
import io.github.composefluent.winrt.metadata.semanticHelpers
import io.github.composefluent.winrt.runtime.ActivationFactory
import io.github.composefluent.winrt.runtime.ComAbiValueKind
import io.github.composefluent.winrt.runtime.ComMethodSignature
import io.github.composefluent.winrt.runtime.ComObjectReference
import io.github.composefluent.winrt.runtime.ComWrappersSupport
import io.github.composefluent.winrt.runtime.ComVtableInvoker
import io.github.composefluent.winrt.runtime.DerivedComposed
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
import io.github.composefluent.winrt.runtime.WinRTAbiArray
import io.github.composefluent.winrt.runtime.NativeAbiLayout
import io.github.composefluent.winrt.runtime.NativeNestedStructFieldSpec
import io.github.composefluent.winrt.runtime.NativeScalarFieldSpec
import io.github.composefluent.winrt.runtime.NativeStructAdapter
import io.github.composefluent.winrt.runtime.NativeStructLayout
import io.github.composefluent.winrt.runtime.NativeStructScalarKind
import io.github.composefluent.winrt.runtime.WinRTBindableIterableProjection
import io.github.composefluent.winrt.runtime.WinRTBindableVectorProjection
import io.github.composefluent.winrt.runtime.WinRTBindableVectorViewProjection
import io.github.composefluent.winrt.runtime.WinRTCollectionInterfaceIds
import io.github.composefluent.winrt.runtime.WinRTDictionaryProjection
import io.github.composefluent.winrt.runtime.WinRTIterableProjection
import io.github.composefluent.winrt.runtime.WinRTListProjection
import io.github.composefluent.winrt.runtime.WinRTAsyncActionReference
import io.github.composefluent.winrt.runtime.WinRTAsyncActionWithProgressReference
import io.github.composefluent.winrt.runtime.WinRTAsyncActionWithProgressVftblSlots
import io.github.composefluent.winrt.runtime.WinRTAsyncInterfaceIds
import io.github.composefluent.winrt.runtime.WinRTAsyncOperationReference
import io.github.composefluent.winrt.runtime.WinRTAsyncOperationWithProgressReference
import io.github.composefluent.winrt.runtime.WinRTAsyncOperationWithProgressVftblSlots
import io.github.composefluent.winrt.runtime.WinRTAsyncOperationVftblSlots
import io.github.composefluent.winrt.runtime.WinRTAsyncProjectionInterop
import io.github.composefluent.winrt.runtime.WinRTReadOnlyDictionaryProjection
import io.github.composefluent.winrt.runtime.WinRTReadOnlyListProjection
import io.github.composefluent.winrt.runtime.WinRTReferenceArrayProjection
import io.github.composefluent.winrt.runtime.WinRTReferenceProjection
import io.github.composefluent.winrt.runtime.WinRTReferenceProjectionInterop
import io.github.composefluent.winrt.runtime.WinRTReferenceValueAdapter
import io.github.composefluent.winrt.runtime.WinRTReferenceValueAdapters
import io.github.composefluent.winrt.runtime.WinRTPropertyValueProjection
import io.github.composefluent.winrt.runtime.WinRTGenericParameterProjection
import io.github.composefluent.winrt.runtime.WinRTGenericAbiSupportIntrinsic
import io.github.composefluent.winrt.runtime.WinRTGenericTypeInstantiationSupportIntrinsic
import io.github.composefluent.winrt.runtime.WinRTAuthoringSupportIntrinsic
import io.github.composefluent.winrt.runtime.WinRTProjectionIntrinsic
import io.github.composefluent.winrt.runtime.WinRTProjectionSupportIntrinsic
import io.github.composefluent.winrt.runtime.WinRTPlatformApi
import io.github.composefluent.winrt.runtime.WinRTSystemProjectionMarshalers
import io.github.composefluent.winrt.runtime.WinRTTypeSignature
import io.github.composefluent.winrt.runtime.WinRTTypeHandle
import io.github.composefluent.winrt.runtime.WinRTValueBoxingRegistration
import io.github.composefluent.winrt.runtime.WinRTDelegateBridge
import io.github.composefluent.winrt.runtime.WinRTDelegateDescriptor
import io.github.composefluent.winrt.runtime.WinRTDelegateReference
import io.github.composefluent.winrt.runtime.WinRTDelegateValueKind
import io.github.composefluent.winrt.runtime.WinRTEvent
import io.github.composefluent.winrt.runtime.WinRTObjectMarshaller
import io.github.composefluent.winrt.runtime.WinRTProjectedDelegate
import io.github.composefluent.winrt.runtime.WinRTComposableObject
import io.github.composefluent.winrt.runtime.WinRTComposableObjectReference
import io.github.composefluent.winrt.runtime.WinRTAttributeUsage
import io.github.composefluent.winrt.runtime.WinRTActivationFactory
import io.github.composefluent.winrt.runtime.WinRTCcwDefinition
import io.github.composefluent.winrt.runtime.WinRTContractVersion
import io.github.composefluent.winrt.runtime.WinRTDefaultOverload
import io.github.composefluent.winrt.runtime.WinRTExperimental
import io.github.composefluent.winrt.runtime.WinRTOverload
import io.github.composefluent.winrt.runtime.WinRTSupportedOSPlatform
import io.github.composefluent.winrt.runtime.WinRTInspectableInterfaceDefinition
import io.github.composefluent.winrt.runtime.WinRTInspectableMethodDefinition
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
internal val WINRT_CCW_DEFINITION_CLASS_NAME = WinRTCcwDefinition::class.asClassName()
internal val WINRT_INSPECTABLE_INTERFACE_DEFINITION_CLASS_NAME = WinRTInspectableInterfaceDefinition::class.asClassName()
internal val WINRT_INSPECTABLE_METHOD_DEFINITION_CLASS_NAME = WinRTInspectableMethodDefinition::class.asClassName()
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
internal fun winRTGenericTypeInstantiationsClassName(ownerIdentity: String?): ClassName {
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

fun winRTAuthoringHostExportsClassName(ownerIdentity: String?): ClassName =
    winRTSupportOwnerClassName(ownerIdentity, WINRT_AUTHORING_HOST_EXPORTS_CLASS_NAME)

internal fun winRTAuthoringServerActivationFactoriesClassName(ownerIdentity: String?): ClassName =
    winRTSupportOwnerClassName(ownerIdentity, WINRT_AUTHORING_SERVER_ACTIVATION_FACTORIES_CLASS_NAME)

internal fun winRTAuthoringModuleActivationFactoryPlanClassName(ownerIdentity: String?): ClassName =
    winRTSupportOwnerClassName(ownerIdentity, WINRT_AUTHORING_MODULE_ACTIVATION_FACTORY_PLAN_CLASS_NAME)

internal fun winRTModulePlatformAbiCallClassName(ownerIdentity: String?): ClassName =
    winRTSupportOwnerClassName(
        ownerIdentity,
        ClassName("io.github.composefluent.winrt.projections.support", "WinRTModulePlatformAbiCall"),
    )

internal fun winRTGenericAbiSupportFileName(ownerIdentity: String?): String {
    val suffix = winRTSupportOwnerIdentifierSuffix(ownerIdentity) ?: return "WinRTGenericAbiSupport"
    return "WinRTGenericAbiSupport_$suffix"
}

internal fun winRTEventProjectionHelperFilePrefix(ownerIdentity: String?): String {
    val suffix = winRTSupportOwnerIdentifierSuffix(ownerIdentity) ?: return "WinRTEventProjectionHelper"
    return "WinRTEventProjectionHelper_$suffix"
}

internal fun winRTProjectionSupportAnchorFileName(ownerIdentity: String?): String {
    val suffix = winRTSupportOwnerIdentifierSuffix(ownerIdentity) ?: return "WinRTProjectionSupportAnchor"
    return "WinRTProjectionSupportAnchor_$suffix"
}

internal fun winRTNamespaceAdditionsClassName(ownerIdentity: String?): ClassName =
    winRTSupportOwnerClassName(ownerIdentity, WINRT_NAMESPACE_ADDITIONS_CLASS_NAME)

private fun winRTSupportOwnerClassName(ownerIdentity: String?, defaultClassName: ClassName): ClassName {
    val suffix = winRTSupportOwnerIdentifierSuffix(ownerIdentity) ?: return defaultClassName
    return ClassName(defaultClassName.packageName, "${defaultClassName.simpleName}_$suffix")
}

internal fun winRTSupportOwnerIdentifierSuffix(ownerIdentity: String?): String? =
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
internal val WINRT_ABI_ARRAY_CLASS_NAME = WinRTAbiArray::class.asClassName()
internal val NATIVE_STRING_MARSHALER_CLASS_NAME = NativeStringMarshaller::class.asClassName()
internal val PLATFORM_ABI_CLASS_NAME = PlatformAbi::class.asClassName()
internal val PARAMETERIZED_INTERFACE_ID_CLASS_NAME = ParameterizedInterfaceId::class.asClassName()
internal val PROJECTIONS_CLASS_NAME = Projections::class.asClassName()
internal val WINRT_BINDABLE_ITERABLE_PROJECTION_CLASS_NAME = WinRTBindableIterableProjection::class.asClassName()
internal val WINRT_BINDABLE_VECTOR_PROJECTION_CLASS_NAME = WinRTBindableVectorProjection::class.asClassName()
internal val WINRT_BINDABLE_VECTOR_VIEW_PROJECTION_CLASS_NAME = WinRTBindableVectorViewProjection::class.asClassName()
internal val WINRT_COLLECTION_INTERFACE_IDS_CLASS_NAME = WinRTCollectionInterfaceIds::class.asClassName()
internal val WINRT_DICTIONARY_PROJECTION_CLASS_NAME = WinRTDictionaryProjection::class.asClassName()
internal val WINRT_ITERABLE_PROJECTION_CLASS_NAME = WinRTIterableProjection::class.asClassName()
internal val WINRT_LIST_PROJECTION_CLASS_NAME = WinRTListProjection::class.asClassName()
internal val WINRT_ASYNC_ACTION_REFERENCE_CLASS_NAME = WinRTAsyncActionReference::class.asClassName()
internal val WINRT_ASYNC_ACTION_WITH_PROGRESS_REFERENCE_CLASS_NAME = WinRTAsyncActionWithProgressReference::class.asClassName()
internal val WINRT_ASYNC_ACTION_WITH_PROGRESS_VFTBL_SLOTS_CLASS_NAME = WinRTAsyncActionWithProgressVftblSlots::class.asClassName()
internal val WINRT_ASYNC_INTERFACE_IDS_CLASS_NAME = WinRTAsyncInterfaceIds::class.asClassName()
internal val WINRT_ASYNC_OPERATION_REFERENCE_CLASS_NAME = WinRTAsyncOperationReference::class.asClassName()
internal val WINRT_ASYNC_OPERATION_WITH_PROGRESS_REFERENCE_CLASS_NAME = WinRTAsyncOperationWithProgressReference::class.asClassName()
internal val WINRT_ASYNC_OPERATION_WITH_PROGRESS_VFTBL_SLOTS_CLASS_NAME = WinRTAsyncOperationWithProgressVftblSlots::class.asClassName()
internal val WINRT_ASYNC_OPERATION_VFTBL_SLOTS_CLASS_NAME = WinRTAsyncOperationVftblSlots::class.asClassName()
internal val WINRT_ASYNC_PROJECTION_INTEROP_CLASS_NAME = WinRTAsyncProjectionInterop::class.asClassName()
internal val WINRT_READ_ONLY_DICTIONARY_PROJECTION_CLASS_NAME = WinRTReadOnlyDictionaryProjection::class.asClassName()
internal val WINRT_READ_ONLY_LIST_PROJECTION_CLASS_NAME = WinRTReadOnlyListProjection::class.asClassName()
internal val WINRT_REFERENCE_ARRAY_PROJECTION_CLASS_NAME = WinRTReferenceArrayProjection::class.asClassName()
internal val WINRT_REFERENCE_PROJECTION_CLASS_NAME = WinRTReferenceProjection::class.asClassName()
internal val WINRT_REFERENCE_PROJECTION_INTEROP_CLASS_NAME = WinRTReferenceProjectionInterop::class.asClassName()
internal val WINRT_REFERENCE_VALUE_ADAPTER_CLASS_NAME = WinRTReferenceValueAdapter::class.asClassName()
internal val WINRT_REFERENCE_VALUE_ADAPTERS_CLASS_NAME = WinRTReferenceValueAdapters::class.asClassName()
internal val WINRT_PROPERTY_VALUE_PROJECTION_CLASS_NAME = WinRTPropertyValueProjection::class.asClassName()
internal val WINRT_GENERIC_PARAMETER_PROJECTION_CLASS_NAME = WinRTGenericParameterProjection::class.asClassName()
internal val WINRT_GENERIC_ABI_SUPPORT_INTRINSIC_CLASS_NAME = WinRTGenericAbiSupportIntrinsic::class.asClassName()
internal val WINRT_GENERIC_TYPE_INSTANTIATION_SUPPORT_INTRINSIC_CLASS_NAME =
    WinRTGenericTypeInstantiationSupportIntrinsic::class.asClassName()
internal val WINRT_AUTHORING_SUPPORT_INTRINSIC_CLASS_NAME = WinRTAuthoringSupportIntrinsic::class.asClassName()
internal val WINRT_PROJECTION_INTRINSIC_CLASS_NAME = WinRTProjectionIntrinsic::class.asClassName()
internal val WINRT_PROJECTION_SUPPORT_INTRINSIC_CLASS_NAME = WinRTProjectionSupportIntrinsic::class.asClassName()
internal val WINRT_KEY_VALUE_PAIR_ADAPTER_FUNCTION_NAME =
    MemberName("io.github.composefluent.winrt.runtime", "winRTKeyValuePairAdapter")
internal val WINRT_OBJECT_MARSHALER_FUNCTION_NAME =
    MemberName("io.github.composefluent.winrt.runtime", "winRTObjectMarshaler")
internal val WINRT_PROJECTION_MARSHALER_FUNCTION_NAME =
    MemberName("io.github.composefluent.winrt.runtime", "winRTProjectionMarshaler")
internal val WINRT_AS_FUNCTION_NAME =
    MemberName("io.github.composefluent.winrt.runtime", "asWinRT")
internal val ACQUIRE_INTERFACE_REFERENCE_FUNCTION_NAME =
    MemberName("io.github.composefluent.winrt.runtime", "acquireInterfaceReference")
internal val WINRT_PROPERTY_CHANGED_EVENT_ARGS_FROM_ABI_FUNCTION_NAME =
    MemberName("io.github.composefluent.winrt.runtime", "winRTPropertyChangedEventArgsFromAbi")
internal val WINRT_PLATFORM_API_CLASS_NAME = WinRTPlatformApi::class.asClassName()
internal val WINRT_SYSTEM_PROJECTION_MARSHALERS_CLASS_NAME = WinRTSystemProjectionMarshalers::class.asClassName()
internal val WINRT_TYPE_SIGNATURE_CLASS_NAME = WinRTTypeSignature::class.asClassName()
internal val WINRT_TYPE_HANDLE_CLASS_NAME = WinRTTypeHandle::class.asClassName()
internal val WINRT_VALUE_BOXING_REGISTRATION_CLASS_NAME = WinRTValueBoxingRegistration::class.asClassName()
internal val WINRT_DELEGATE_BRIDGE_CLASS_NAME = WinRTDelegateBridge::class.asClassName()
internal val WINRT_DELEGATE_DESCRIPTOR_CLASS_NAME = WinRTDelegateDescriptor::class.asClassName()
internal val WINRT_DELEGATE_REFERENCE_CLASS_NAME = WinRTDelegateReference::class.asClassName()
internal val WINRT_DELEGATE_VALUE_KIND_CLASS_NAME = WinRTDelegateValueKind::class.asClassName()
internal val WINRT_EVENT_CLASS_NAME = WinRTEvent::class.asClassName()
internal val WINRT_OBJECT_MARSHALLER_CLASS_NAME = WinRTObjectMarshaller::class.asClassName()
internal val WINRT_PROJECTED_DELEGATE_CLASS_NAME = WinRTProjectedDelegate::class.asClassName()
internal val WINRT_EVENT_SOURCE_CLASS_NAME = EventSource::class.asClassName()
internal val WINRT_EVENT_PROJECTION_HELPERS_CLASS_NAME =
    ClassName("io.github.composefluent.winrt.projections.support", "WinRTEventProjectionHelpers")
internal val WINRT_CLOSABLE_OBJECT_CLASS_NAME = ClassName("windows.foundation", "WinRTClosableObject")
internal val WINRT_COMPOSABLE_OBJECT_CLASS_NAME = WinRTComposableObject::class.asClassName()
internal val WINRT_COMPOSABLE_OBJECT_REFERENCE_CLASS_NAME = WinRTComposableObjectReference::class.asClassName()
internal val WINRT_ACTIVATION_FACTORY_CLASS_NAME = WinRTActivationFactory::class.asClassName()
internal val ATTRIBUTE_CLASS_NAME = Annotation::class.asClassName()
internal val ABSTRACT_LIST_CLASS_NAME = AbstractList::class.asClassName()
internal val ABSTRACT_MAP_CLASS_NAME = AbstractMap::class.asClassName()
internal val ABSTRACT_MUTABLE_LIST_CLASS_NAME = ClassName("kotlin.collections", "AbstractMutableList")
internal val ABSTRACT_MUTABLE_MAP_CLASS_NAME = ClassName("kotlin.collections", "AbstractMutableMap")
internal val ABSTRACT_MUTABLE_SET_CLASS_NAME = ClassName("kotlin.collections", "AbstractMutableSet")
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
internal val EVENT_REGISTRATION_TOKEN_CLASS_NAME = ClassName("windows.foundation", "EventRegistrationToken")
internal val EXCEPTION_HELPERS_CLASS_NAME = ExceptionHelpers::class.asClassName()
internal val EXCEPTION_CLASS_NAME = ClassName("kotlin", "Exception")
internal val WINDOWS_FOUNDATION_EVENT_HANDLER_CLASS_NAME = ClassName("windows.foundation", "EventHandler")
internal val MUX_COMMAND_CLASS_NAME = ClassName("microsoft.ui.xaml.input", "ICommand")
internal val MUX_INTEROP_COMMAND_CLASS_NAME = ClassName("microsoft.ui.xaml.interop", "ICommand")
internal val WUX_COMMAND_CLASS_NAME = ClassName("windows.ui.xaml.input", "ICommand")
internal val WUX_INTEROP_COMMAND_CLASS_NAME = ClassName("windows.ui.xaml.interop", "ICommand")
internal val MUX_PROPERTY_CHANGED_NOTIFIER_CLASS_NAME = ClassName("microsoft.ui.xaml.data", "INotifyPropertyChanged")
internal val WUX_PROPERTY_CHANGED_NOTIFIER_CLASS_NAME = ClassName("windows.ui.xaml.data", "INotifyPropertyChanged")
internal val WINRT_PROPERTY_CHANGED_NOTIFIER_PROJECTION_CLASS_NAME = ClassName("io.github.composefluent.winrt.runtime", "INotifyPropertyChangedProjection")
internal val MUX_PROPERTY_CHANGED_EVENT_ARGS_CLASS_NAME = ClassName("microsoft.ui.xaml.data", "PropertyChangedEventArgs")
internal val WUX_PROPERTY_CHANGED_EVENT_ARGS_CLASS_NAME = ClassName("windows.ui.xaml.data", "PropertyChangedEventArgs")
internal val MUX_PROPERTY_CHANGED_HANDLER_CLASS_NAME = ClassName("microsoft.ui.xaml.data", "PropertyChangedEventHandler")
internal val WUX_PROPERTY_CHANGED_HANDLER_CLASS_NAME = ClassName("windows.ui.xaml.data", "PropertyChangedEventHandler")
internal val MUX_COLLECTION_CHANGED_NOTIFIER_CLASS_NAME = ClassName("microsoft.ui.xaml.interop", "INotifyCollectionChanged")
internal val WUX_COLLECTION_CHANGED_NOTIFIER_CLASS_NAME = ClassName("windows.ui.xaml.interop", "INotifyCollectionChanged")
internal val MUX_NOTIFY_COLLECTION_CHANGED_ACTION_CLASS_NAME = ClassName("microsoft.ui.xaml.interop", "NotifyCollectionChangedAction")
internal val WUX_NOTIFY_COLLECTION_CHANGED_ACTION_CLASS_NAME = ClassName("windows.ui.xaml.interop", "NotifyCollectionChangedAction")
internal val MUX_NOTIFY_COLLECTION_CHANGED_EVENT_ARGS_CLASS_NAME = ClassName("microsoft.ui.xaml.interop", "NotifyCollectionChangedEventArgs")
internal val WUX_NOTIFY_COLLECTION_CHANGED_EVENT_ARGS_CLASS_NAME = ClassName("windows.ui.xaml.interop", "NotifyCollectionChangedEventArgs")
internal val MUX_COLLECTION_CHANGED_HANDLER_CLASS_NAME = ClassName("microsoft.ui.xaml.interop", "NotifyCollectionChangedEventHandler")
internal val WUX_COLLECTION_CHANGED_HANDLER_CLASS_NAME = ClassName("windows.ui.xaml.interop", "NotifyCollectionChangedEventHandler")
internal val MUX_DATA_ERROR_INFO_CLASS_NAME = ClassName("microsoft.ui.xaml.data", "INotifyDataErrorInfo")
internal val WUX_DATA_ERROR_INFO_CLASS_NAME = ClassName("windows.ui.xaml.data", "INotifyDataErrorInfo")
internal val WINRT_DATA_ERROR_INFO_PROJECTION_CLASS_NAME = ClassName("io.github.composefluent.winrt.runtime", "INotifyDataErrorInfoProjection")
internal val MUX_DATA_ERRORS_CHANGED_EVENT_ARGS_CLASS_NAME = ClassName("microsoft.ui.xaml.data", "DataErrorsChangedEventArgs")
internal val WUX_DATA_ERRORS_CHANGED_EVENT_ARGS_CLASS_NAME = ClassName("windows.ui.xaml.data", "DataErrorsChangedEventArgs")
internal val MUX_DATA_ERRORS_CHANGED_HANDLER_CLASS_NAME = ClassName("microsoft.ui.xaml.data", "DataErrorsChangedEventHandler")
internal val WUX_DATA_ERRORS_CHANGED_HANDLER_CLASS_NAME = ClassName("windows.ui.xaml.data", "DataErrorsChangedEventHandler")
internal val MUX_SERVICE_PROVIDER_CLASS_NAME = ClassName("microsoft.ui.xaml", "IXamlServiceProvider")
internal val WUX_SERVICE_PROVIDER_CLASS_NAME = ClassName("windows.ui.xaml", "IXamlServiceProvider")
internal val WINRT_ATTRIBUTE_USAGE_CLASS_NAME = WinRTAttributeUsage::class.asClassName()
internal val WINRT_CONTRACT_VERSION_CLASS_NAME = WinRTContractVersion::class.asClassName()
internal val WINRT_DEFAULT_OVERLOAD_CLASS_NAME = WinRTDefaultOverload::class.asClassName()
internal val WINRT_EXPERIMENTAL_CLASS_NAME = WinRTExperimental::class.asClassName()
internal val WINRT_OVERLOAD_CLASS_NAME = WinRTOverload::class.asClassName()
internal val WINRT_SUPPORTED_OS_PLATFORM_CLASS_NAME = WinRTSupportedOSPlatform::class.asClassName()
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
    val type: WinRTTypeDefinition,
    val typesByQualifiedName: Map<String, WinRTTypeDefinition> = emptyMap(),
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
    val composableFactoryBindings: List<KotlinProjectionComposableFactoryBinding> = emptyList(),
    val abiSlotBindings: List<KotlinProjectionAbiSlotBinding> = emptyList(),
    val instanceMemberBindings: List<KotlinProjectionInstanceMemberBinding> = emptyList(),
    val staticMemberBindings: List<KotlinProjectionStaticMemberBinding> = emptyList(),
    val readOnlyCollectionBindings: List<KotlinProjectionReadOnlyCollectionBinding> = emptyList(),
    val mutableCollectionBindings: List<KotlinProjectionMutableCollectionBinding> = emptyList(),
    val delegateInvokeShape: KotlinProjectionDelegateInvokeShape? = null,
    val eventInvokeDescriptors: List<WinRTEventInvokeDescriptor> = emptyList(),
    val typeDeclarationDescriptor: WinRTTypeDeclarationDescriptor,
    val factorySurfaceDescriptor: WinRTFactorySurfaceDescriptor? = null,
    val objectReferenceSurfaceDescriptor: WinRTObjectReferenceSurfaceDescriptor? = null,
    val guidSignatureDescriptor: WinRTGuidSignatureDescriptor? = null,
    val interfaceMemberSignatureSetDescriptor: WinRTInterfaceMemberSignatureSetDescriptor? = null,
    val customMappedMemberOutputDescriptor: WinRTCustomMappedMemberOutputDescriptor? = null,
    val classMemberMergeDescriptor: WinRTClassMemberMergeDescriptor? = null,
    val genericAbiClassInitializationDescriptor: WinRTGenericAbiClassInitializationDescriptor? = null,
    val requiredInterfaceAugmentationDescriptor: WinRTRequiredInterfaceAugmentationDescriptor? = null,
    val fastAbiClassDescriptor: WinRTFastAbiClassDescriptor? = null,
    val moduleActivationAndAuthoringDescriptor: WinRTModuleActivationAndAuthoringDescriptor? = null,
    val projectedAttributes: List<WinRTProjectedAttributeDescriptor> = emptyList(),
    val companionKinds: List<KotlinProjectionCompanionKind> = emptyList(),
)

data class KotlinProjectionInterfaceBinding(
    val qualifiedName: String,
    val iid: Guid? = null,
)

data class KotlinProjectionComposableFactoryBinding(
    val qualifiedName: String,
    val iid: Guid? = null,
    val isVisible: Boolean = false,
)

data class KotlinProjectionAbiSlotBinding(
    val constantName: String,
    val slot: Int,
    val descriptor: WinRTMethodVtableDescriptor? = null,
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
    val signatureDescriptor: WinRTSignatureWriterDescriptor? = null,
    val marshalerPlanDescriptor: WinRTAbiMarshalerPlanDescriptor? = null,
    val projectedAttributes: List<WinRTProjectedAttributeDescriptor> = emptyList(),
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
    val signatureDescriptor: WinRTSignatureWriterDescriptor? = null,
    val marshalerPlanDescriptor: WinRTAbiMarshalerPlanDescriptor? = null,
    val projectedAttributes: List<WinRTProjectedAttributeDescriptor> = emptyList(),
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
    val runtimeOwnedPublicDeclaration: Boolean = false,
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
        "Windows.Foundation.EventHandler",
        { arguments -> WINDOWS_FOUNDATION_EVENT_HANDLER_CLASS_NAME.parameterizedBy(arguments.singleOrNull() ?: ANY.copy(nullable = true)) },
        runtimeOwnedProjection = true,
        runtimeOwnedPublicDeclaration = true,
        simpleAbiLookup = true,
        descriptionName = "EventHandler",
    ),
    KotlinProjectionMappedType(
        "Windows.Foundation.TypedEventHandler",
        { arguments ->
            ClassName("windows.foundation", "TypedEventHandler").parameterizedBy(
                arguments.getOrNull(0) ?: ANY.copy(nullable = true),
                arguments.getOrNull(1) ?: ANY.copy(nullable = true),
            )
        },
        runtimeOwnedProjection = true,
        runtimeOwnedPublicDeclaration = true,
        simpleAbiLookup = true,
        descriptionName = "TypedEventHandler",
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
        "Windows.Foundation.IStringable",
        { ClassName("windows.foundation", "IStringable") },
        runtimeOwnedProjection = true,
        runtimeOwnedPublicDeclaration = true,
        simpleAbiLookup = true,
        descriptionName = "IStringable",
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
    KotlinProjectionMappedType("Microsoft.UI.Xaml.IXamlServiceProvider", { MUX_SERVICE_PROVIDER_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("68B3A2DF-8173-539F-B524-C8A2348F5AFB"), "microsoft.ui.xaml.IXamlServiceProvider"), runtimeOwnedPublicDeclaration = true, descriptionName = "IXamlServiceProvider"),
    KotlinProjectionMappedType("Microsoft.UI.Xaml.Data.DataErrorsChangedEventArgs", { MUX_DATA_ERRORS_CHANGED_EVENT_ARGS_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedRuntimeClass, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("D026DD64-5F26-5F15-A86A-0DEC8A431796"), "microsoft.ui.xaml.data.DataErrorsChangedEventArgs"), runtimeOwnedPublicDeclaration = true, descriptionName = "DataErrorsChangedEventArgs"),
    KotlinProjectionMappedType("Microsoft.UI.Xaml.Data.INotifyDataErrorInfo", { MUX_DATA_ERROR_INFO_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("0EE6C2CC-273E-567D-BC0A-1DD87EE51EBA"), "microsoft.ui.xaml.data.INotifyDataErrorInfo"), runtimeOwnedPublicDeclaration = true, descriptionName = "INotifyDataErrorInfo"),
    KotlinProjectionMappedType("Microsoft.UI.Xaml.Data.DataErrorsChangedEventHandler", { MUX_DATA_ERRORS_CHANGED_HANDLER_CLASS_NAME }, runtimeOwnedProjection = true, runtimeOwnedPublicDeclaration = true, descriptionName = "DataErrorsChangedEventHandler"),
    KotlinProjectionMappedType("Microsoft.UI.Xaml.Data.INotifyPropertyChanged", { MUX_PROPERTY_CHANGED_NOTIFIER_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("90B17601-B065-586E-83D9-9ADC3A695284"), "microsoft.ui.xaml.data.INotifyPropertyChanged"), runtimeOwnedPublicDeclaration = true, descriptionName = "INotifyPropertyChanged"),
    KotlinProjectionMappedType("Microsoft.UI.Xaml.Data.PropertyChangedEventArgs", { MUX_PROPERTY_CHANGED_EVENT_ARGS_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedRuntimeClass, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("63D0C952-396B-54F4-AF8C-BA8724A427BF"), "microsoft.ui.xaml.data.PropertyChangedEventArgs"), runtimeOwnedPublicDeclaration = true, descriptionName = "PropertyChangedEventArgs"),
    KotlinProjectionMappedType("Microsoft.UI.Xaml.Data.PropertyChangedEventHandler", { MUX_PROPERTY_CHANGED_HANDLER_CLASS_NAME }, runtimeOwnedProjection = true, runtimeOwnedPublicDeclaration = true, descriptionName = "PropertyChangedEventHandler"),
    KotlinProjectionMappedType("Microsoft.UI.Xaml.Input.ICommand", { MUX_COMMAND_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("E5AF3542-CA67-4081-995B-709DD13792DF"), "microsoft.ui.xaml.input.ICommand"), runtimeOwnedPublicDeclaration = true, descriptionName = "ICommand"),
    KotlinProjectionMappedType("Microsoft.UI.Xaml.Interop.ICommand", { MUX_INTEROP_COMMAND_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("E5AF3542-CA67-4081-995B-709DD13792DF"), "microsoft.ui.xaml.interop.ICommand"), runtimeOwnedPublicDeclaration = true, descriptionName = "ICommand"),
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
    KotlinProjectionMappedType("Microsoft.UI.Xaml.Interop.INotifyCollectionChanged", { MUX_COLLECTION_CHANGED_NOTIFIER_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("530155E1-28A5-5693-87CE-30724D95A06D"), "microsoft.ui.xaml.interop.INotifyCollectionChanged"), runtimeOwnedPublicDeclaration = true, descriptionName = "INotifyCollectionChanged"),
    KotlinProjectionMappedType(
        "Microsoft.UI.Xaml.Interop.NotifyCollectionChangedAction",
        { MUX_NOTIFY_COLLECTION_CHANGED_ACTION_CLASS_NAME },
        runtimeOwnedProjection = true,
        descriptionName = "NotifyCollectionChangedAction",
    ),
    KotlinProjectionMappedType("Microsoft.UI.Xaml.Interop.NotifyCollectionChangedEventArgs", { MUX_NOTIFY_COLLECTION_CHANGED_EVENT_ARGS_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedRuntimeClass, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("DA049FF2-D2E0-5FE8-8C7B-F87F26060B6F"), "microsoft.ui.xaml.interop.NotifyCollectionChangedEventArgs"), runtimeOwnedPublicDeclaration = true, descriptionName = "NotifyCollectionChangedEventArgs"),
    KotlinProjectionMappedType("Microsoft.UI.Xaml.Interop.NotifyCollectionChangedEventHandler", { MUX_COLLECTION_CHANGED_HANDLER_CLASS_NAME }, runtimeOwnedPublicDeclaration = true, descriptionName = "NotifyCollectionChangedEventHandler"),
    KotlinProjectionMappedType("Windows.UI.Xaml.IXamlServiceProvider", { WUX_SERVICE_PROVIDER_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("68B3A2DF-8173-539F-B524-C8A2348F5AFB"), "windows.ui.xaml.IXamlServiceProvider"), runtimeOwnedPublicDeclaration = true, descriptionName = "IXamlServiceProvider"),
    KotlinProjectionMappedType("Windows.UI.Xaml.Data.DataErrorsChangedEventArgs", { WUX_DATA_ERRORS_CHANGED_EVENT_ARGS_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedRuntimeClass, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("D026DD64-5F26-5F15-A86A-0DEC8A431796"), "windows.ui.xaml.data.DataErrorsChangedEventArgs"), runtimeOwnedPublicDeclaration = true, descriptionName = "DataErrorsChangedEventArgs"),
    KotlinProjectionMappedType("Windows.UI.Xaml.Data.INotifyDataErrorInfo", { WUX_DATA_ERROR_INFO_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("0EE6C2CC-273E-567D-BC0A-1DD87EE51EBA"), "windows.ui.xaml.data.INotifyDataErrorInfo"), runtimeOwnedPublicDeclaration = true, descriptionName = "INotifyDataErrorInfo"),
    KotlinProjectionMappedType("Windows.UI.Xaml.Data.DataErrorsChangedEventHandler", { WUX_DATA_ERRORS_CHANGED_HANDLER_CLASS_NAME }, runtimeOwnedProjection = true, runtimeOwnedPublicDeclaration = true, descriptionName = "DataErrorsChangedEventHandler"),
    KotlinProjectionMappedType("Windows.UI.Xaml.Data.INotifyPropertyChanged", { WUX_PROPERTY_CHANGED_NOTIFIER_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("CF75D69C-F2F4-486B-B302-BB4C09BAEBFA"), "windows.ui.xaml.data.INotifyPropertyChanged"), runtimeOwnedPublicDeclaration = true, descriptionName = "INotifyPropertyChanged"),
    KotlinProjectionMappedType("Windows.UI.Xaml.Data.PropertyChangedEventArgs", { WUX_PROPERTY_CHANGED_EVENT_ARGS_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedRuntimeClass, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("4F33A9A0-5CF4-47A4-B16F-D7FAAF17457E"), "windows.ui.xaml.data.PropertyChangedEventArgs"), runtimeOwnedPublicDeclaration = true, descriptionName = "PropertyChangedEventArgs"),
    KotlinProjectionMappedType("Windows.UI.Xaml.Data.PropertyChangedEventHandler", { WUX_PROPERTY_CHANGED_HANDLER_CLASS_NAME }, runtimeOwnedProjection = true, runtimeOwnedPublicDeclaration = true, descriptionName = "PropertyChangedEventHandler"),
    KotlinProjectionMappedType("Windows.UI.Xaml.Input.ICommand", { WUX_COMMAND_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("E5AF3542-CA67-4081-995B-709DD13792DF"), "windows.ui.xaml.input.ICommand"), runtimeOwnedPublicDeclaration = true, descriptionName = "ICommand"),
    KotlinProjectionMappedType("Windows.UI.Xaml.Interop.ICommand", { WUX_INTEROP_COMMAND_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("E5AF3542-CA67-4081-995B-709DD13792DF"), "windows.ui.xaml.interop.ICommand"), runtimeOwnedPublicDeclaration = true, descriptionName = "ICommand"),
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
    KotlinProjectionMappedType("Windows.UI.Xaml.Interop.INotifyCollectionChanged", { WUX_COLLECTION_CHANGED_NOTIFIER_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedInterface, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("28B167D5-1A31-465B-9B25-D5C3AE686C40"), "windows.ui.xaml.interop.INotifyCollectionChanged"), runtimeOwnedPublicDeclaration = true, descriptionName = "INotifyCollectionChanged"),
    KotlinProjectionMappedType(
        "Windows.UI.Xaml.Interop.NotifyCollectionChangedAction",
        { WUX_NOTIFY_COLLECTION_CHANGED_ACTION_CLASS_NAME },
        runtimeOwnedProjection = true,
        descriptionName = "NotifyCollectionChangedAction",
    ),
    KotlinProjectionMappedType("Windows.UI.Xaml.Interop.NotifyCollectionChangedEventArgs", { WUX_NOTIFY_COLLECTION_CHANGED_EVENT_ARGS_CLASS_NAME }, abiValueKind = KotlinProjectionAbiValueKind.ProjectedRuntimeClass, customObjectAbi = KotlinProjectionCustomObjectAbi(Guid("4CF68D33-E3F2-4964-B85E-945B4F7E2F21"), "windows.ui.xaml.interop.NotifyCollectionChangedEventArgs"), runtimeOwnedPublicDeclaration = true, descriptionName = "NotifyCollectionChangedEventArgs"),
    KotlinProjectionMappedType("Windows.UI.Xaml.Interop.NotifyCollectionChangedEventHandler", { WUX_COLLECTION_CHANGED_HANDLER_CLASS_NAME }, runtimeOwnedPublicDeclaration = true, descriptionName = "NotifyCollectionChangedEventHandler"),
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

internal fun isRuntimeOwnedPublicDeclarationTypeName(typeName: String): Boolean {
    val rawTypeName = typeName.substringBefore('<').removeSuffix("?")
    return mappedTypeByAbiName(rawTypeName)?.runtimeOwnedPublicDeclaration == true
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

internal val INTEGRAL_ABI_DESCRIPTORS: Map<WinRTIntegralType, KotlinProjectionIntegralAbiDescriptor> = mapOf(
    WinRTIntegralType.Int8 to KotlinProjectionIntegralAbiDescriptor(
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
    WinRTIntegralType.UInt8 to KotlinProjectionIntegralAbiDescriptor(
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
    WinRTIntegralType.Int16 to KotlinProjectionIntegralAbiDescriptor(
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
    WinRTIntegralType.UInt16 to KotlinProjectionIntegralAbiDescriptor(
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
    WinRTIntegralType.Int32 to KotlinProjectionIntegralAbiDescriptor(
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
    WinRTIntegralType.UInt32 to KotlinProjectionIntegralAbiDescriptor(
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
    WinRTIntegralType.Int64 to KotlinProjectionIntegralAbiDescriptor(
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
    WinRTIntegralType.UInt64 to KotlinProjectionIntegralAbiDescriptor(
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

internal fun integralAbiDescriptor(type: WinRTIntegralType): KotlinProjectionIntegralAbiDescriptor =
    INTEGRAL_ABI_DESCRIPTORS.getValue(type)

internal fun integralComAbiValueKindCode(type: WinRTIntegralType): CodeBlock =
    CodeBlock.of("%T.%L", COM_ABI_VALUE_KIND_CLASS_NAME, integralAbiDescriptor(type).comArgumentKind.name)

internal fun integralDelegateValueKindCode(type: WinRTIntegralType): CodeBlock =
    CodeBlock.of("%T.%L", WINRT_DELEGATE_VALUE_KIND_CLASS_NAME, integralAbiDescriptor(type).delegateValueKindName)

internal fun integralTypeSignatureCode(type: WinRTIntegralType): CodeBlock =
    CodeBlock.of("%T.%L()", WINRT_TYPE_SIGNATURE_CLASS_NAME, integralAbiDescriptor(type).typeSignatureFunctionName)

internal fun integralProjectionIntrinsicShapeName(type: WinRTIntegralType): String? =
    integralAbiDescriptor(type).projectionIntrinsicShapeName

internal fun integralProjectionIntrinsicGetterName(type: WinRTIntegralType): String? =
    integralAbiDescriptor(type).projectionIntrinsicGetterName

internal fun integralProjectionIntrinsicSetterName(type: WinRTIntegralType): String? =
    integralAbiDescriptor(type).projectionIntrinsicSetterName

internal fun integralAbiSizeExpression(type: WinRTIntegralType): CodeBlock =
    CodeBlock.of("%L", integralAbiDescriptor(type).abiSizeBytes)

internal fun integralKotlinCastExpression(type: WinRTIntegralType, expression: CodeBlock): CodeBlock =
    CodeBlock.of("%L as %T", expression, integralAbiDescriptor(type).kotlinTypeName)

internal fun integralPlatformReadExpression(type: WinRTIntegralType, addressExpression: CodeBlock): CodeBlock {
    val descriptor = integralAbiDescriptor(type)
    val readExpression = CodeBlock.of("%T.%L(%L)", PLATFORM_ABI_CLASS_NAME, descriptor.platformReadFunctionName, addressExpression)
    return if (descriptor.carrierToKotlinConversionSuffix.isEmpty()) {
        readExpression
    } else {
        CodeBlock.of("%L%L", readExpression, descriptor.carrierToKotlinConversionSuffix)
    }
}

internal fun integralPlatformWriteCode(
    type: WinRTIntegralType,
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

internal fun integralAbiCarrierExpression(type: WinRTIntegralType, expression: CodeBlock): CodeBlock {
    val descriptor = integralAbiDescriptor(type)
    val carrierExpression = CodeBlock.of("%L as %T", expression, descriptor.abiCarrierTypeName)
    return if (descriptor.carrierToKotlinConversionSuffix.isEmpty()) {
        carrierExpression
    } else {
        CodeBlock.of("(%L)%L", carrierExpression, descriptor.carrierToKotlinConversionSuffix)
    }
}

internal fun integralResultSlotAllocation(type: WinRTIntegralType, scopeName: String): CodeBlock =
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
    val sourceTypeKind: WinRTTypeKind? = null,
    val abiSize: Int? = null,
    val abiAlignment: Int? = null,
    val interfaceId: Guid? = null,
    val enumUnderlyingType: WinRTIntegralType? = null,
    val delegateInvokeShape: KotlinProjectionDelegateInvokeShape? = null,
    val typeArguments: List<KotlinProjectionAbiTypeBinding> = emptyList(),
    val structFieldBindings: List<KotlinProjectionAbiTypeBinding> = emptyList(),
)

data class KotlinProjectionAbiParameterBinding(
    val name: String,
    val typeBinding: KotlinProjectionAbiTypeBinding,
    val category: WinRTMetadataParameterCategory = WinRTMetadataParameterCategory.In,
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
    val descriptor: WinRTAbiMarshalerPlanDescriptor? = null,
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

internal fun String.isProjectedWinRTInterfaceReferenceName(): Boolean {
    val simpleName = substringAfterLast('.')
    return '.' in this &&
        simpleName.startsWith('I') &&
        (startsWith("Windows.") || startsWith("Microsoft."))
}

class KotlinProjectionContractValidator {
    fun validate(model: WinRTMetadataModel): WinRTMetadataModel =
        model.requireValidForProjection(GENERATOR_VALIDATION_OPTIONS)

    fun validateType(type: WinRTTypeDefinition) =
        WinRTMetadataModel(listOf(WinRTNamespace(type.namespace, listOf(type)))).requireValidForProjection(GENERATOR_VALIDATION_OPTIONS)

    companion object {
        private val GENERATOR_VALIDATION_OPTIONS = WinRTMetadataValidationOptions(
            validateTypeReferences = false,
            validateActivationFactoryReferences = false,
            validateRuntimeClassDefaultInterface = false,
        )
    }
}
