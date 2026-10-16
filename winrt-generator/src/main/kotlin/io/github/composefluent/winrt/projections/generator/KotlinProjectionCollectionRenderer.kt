package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTAbiMarshalerPlanDescriptor
import io.github.composefluent.winrt.metadata.WinRTAbiMarshalerSlotDescriptor
import io.github.composefluent.winrt.metadata.WinRTCustomMappedMemberOutputDescriptor
import io.github.composefluent.winrt.metadata.WinRTEventDefinition
import io.github.composefluent.winrt.metadata.WinRTEventInvokeDescriptor
import io.github.composefluent.winrt.metadata.WinRTFactorySurfaceDescriptor
import io.github.composefluent.winrt.metadata.WinRTFieldDefinition
import io.github.composefluent.winrt.metadata.WinRTGuidSignatureDescriptor
import io.github.composefluent.winrt.metadata.WinRTInterfaceImplementationDefinition
import io.github.composefluent.winrt.metadata.WinRTInterfaceMemberSignatureSetDescriptor
import io.github.composefluent.winrt.metadata.WinRTIntegralType
import io.github.composefluent.winrt.metadata.WinRTMetadataProjectionContext
import io.github.composefluent.winrt.metadata.WinRTMetadataProjectionInventory
import io.github.composefluent.winrt.metadata.WinRTMetadataProjectionInventoryBuilder
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
import io.github.composefluent.winrt.runtime.WinRTAsyncOperationReference
import io.github.composefluent.winrt.runtime.WinRTAsyncOperationWithProgressReference
import io.github.composefluent.winrt.runtime.WinRTAsyncOperationWithProgressVftblSlots
import io.github.composefluent.winrt.runtime.WinRTAsyncOperationVftblSlots
import io.github.composefluent.winrt.runtime.WinRTReadOnlyDictionaryProjection
import io.github.composefluent.winrt.runtime.WinRTReadOnlyListProjection
import io.github.composefluent.winrt.runtime.WinRTReferenceArrayProjection
import io.github.composefluent.winrt.runtime.WinRTReferenceProjection
import io.github.composefluent.winrt.runtime.WinRTReferenceValueAdapter
import io.github.composefluent.winrt.runtime.WinRTPlatformApi
import io.github.composefluent.winrt.runtime.WinRTTypeSignature
import io.github.composefluent.winrt.runtime.WinRTTypeHandle
import io.github.composefluent.winrt.runtime.WinRTDelegateBridge
import io.github.composefluent.winrt.runtime.WinRTDelegateDescriptor
import io.github.composefluent.winrt.runtime.WinRTDelegateReference
import io.github.composefluent.winrt.runtime.WinRTDelegateValueKind
import io.github.composefluent.winrt.runtime.WinRTEvent
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
import com.squareup.kotlinpoet.asClassName
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.OffsetDateTime
import kotlin.collections.AbstractList
import kotlin.collections.AbstractMap
import kotlin.io.path.extension

internal fun KotlinProjectionRenderer.readOnlyCollectionProjectedType(
    binding: KotlinProjectionReadOnlyCollectionBinding,
): TypeName = when (binding.kind) {
    KotlinProjectionReadOnlyCollectionKind.Iterable ->
        Iterable::class.asClassName().parameterizedBy(resolveValueTypeName(requireNotNull(binding.elementBinding)))
    KotlinProjectionReadOnlyCollectionKind.VectorView ->
        List::class.asClassName().parameterizedBy(resolveValueTypeName(requireNotNull(binding.elementBinding)))
    KotlinProjectionReadOnlyCollectionKind.MapView ->
        Map::class.asClassName().parameterizedBy(
            resolveValueTypeName(requireNotNull(binding.keyBinding)),
            resolveValueTypeName(requireNotNull(binding.valueBinding)),
        )
}

internal fun KotlinProjectionRenderer.mutableCollectionProjectedType(
    binding: KotlinProjectionMutableCollectionBinding,
): TypeName = when (binding.kind) {
    KotlinProjectionMutableCollectionKind.Vector ->
        MUTABLE_LIST_CLASS_NAME.parameterizedBy(resolveValueTypeName(requireNotNull(binding.elementBinding)))
    KotlinProjectionMutableCollectionKind.Map ->
        MUTABLE_MAP_CLASS_NAME.parameterizedBy(
            resolveValueTypeName(requireNotNull(binding.keyBinding)),
            resolveValueTypeName(requireNotNull(binding.valueBinding)),
        )
}

internal fun KotlinProjectionRenderer.addMutableCollectionDelegateSupport(
    builder: TypeSpec.Builder,
    binding: KotlinProjectionMutableCollectionBinding,
) {
    val type = mutableCollectionProjectedType(binding)
    val cacheName = "${binding.delegatePropertyName}Cache"
    val makeName = "${binding.delegatePropertyName}Make"
    builder.addProperty(
        PropertySpec.builder(cacheName, type.copy(nullable = true))
            .addModifiers(KModifier.PRIVATE)
            .mutable(true)
            .addAnnotation(KOTLIN_VOLATILE_CLASS_NAME)
            .initializer("null")
            .build(),
    )
    builder.addFunction(
        FunSpec.builder(makeName)
            .addModifiers(KModifier.PRIVATE)
            .returns(type)
            .addCode(
                CodeBlock.builder()
                    .add("val candidate: %T = kotlin.run {\n", type)
                    .indent()
                    .add(renderMutableCollectionDelegateInitializer(binding))
                    .unindent()
                    .add("}\n")
                    .add("return %M(\n", PUBLISH_GENERATED_WINRT_VALUE_FUNCTION_NAME)
                    .indent()
                    .add("candidate,\n")
                    .add("{ %L },\n", cacheName)
                    .add("{ value ->\n")
                    .indent()
                    .add("%L = value\n", cacheName)
                    .unindent()
                    .add("},\n")
                    .unindent()
                    .add(")\n")
                    .build(),
            )
            .build(),
    )
    builder.addProperty(
        PropertySpec.builder(binding.delegatePropertyName, type)
            .addModifiers(KModifier.PRIVATE)
            .getter(
                FunSpec.getterBuilder()
                    .addCode("return %L ?: %L()\n", cacheName, makeName)
                    .build(),
            )
            .build(),
    )
}

internal fun KotlinProjectionRenderer.renderMutableCollectionDelegateInitializer(
    binding: KotlinProjectionMutableCollectionBinding,
): CodeBlock = when (binding.kind) {
    KotlinProjectionMutableCollectionKind.Vector ->
        bindableCollectionDelegateInitializer(binding.slotInterfaceQualifiedName, binding.ownerCachePropertyName)
            ?: runtimeMutableCollectionDelegateInitializer(binding)
            ?: error("Missing runtime collection element ABI for $binding")
    KotlinProjectionMutableCollectionKind.Map ->
        runtimeMutableCollectionDelegateInitializer(binding)
            ?: error("Missing runtime collection element ABI for $binding")
}

private fun KotlinProjectionRenderer.runtimeMutableCollectionDelegateInitializer(
    binding: KotlinProjectionMutableCollectionBinding,
): CodeBlock? =
    when (binding.kind) {
        KotlinProjectionMutableCollectionKind.Vector -> {
            // CsWinRT's collection facade retains its own object reference. Our fromAbi
            // consumes an owned reference, so never transfer the cache's borrowed pointer.
            val elementAdapter = collectionReferenceAdapterCode(requireNotNull(binding.elementBinding)) ?: return null
            CodeBlock.of(
                "%T.fromAbi(%L.acquireOwnedPointer().asRawAddress(), %L) ?: error(%S)\n",
                WINRT_LIST_PROJECTION_CLASS_NAME,
                binding.ownerCachePropertyName,
                elementAdapter,
                "WINRT_E_NULL_ABI_RETURN",
            )
        }
        KotlinProjectionMutableCollectionKind.Map -> {
            val keyAdapter = collectionReferenceAdapterCode(requireNotNull(binding.keyBinding)) ?: return null
            val valueAdapter = collectionReferenceAdapterCode(requireNotNull(binding.valueBinding)) ?: return null
            CodeBlock.of(
                "%T.fromAbi(%L.acquireOwnedPointer().asRawAddress(), %L, %L) ?: error(%S)\n",
                WINRT_DICTIONARY_PROJECTION_CLASS_NAME,
                binding.ownerCachePropertyName,
                keyAdapter,
                valueAdapter,
                "WINRT_E_NULL_ABI_RETURN",
            )
        }
    }

internal fun KotlinProjectionRenderer.addReadOnlyCollectionDelegateSupport(
    builder: TypeSpec.Builder,
    binding: KotlinProjectionReadOnlyCollectionBinding,
) {
    val type = readOnlyCollectionProjectedType(binding)
    val cacheName = "${binding.delegatePropertyName}Cache"
    val makeName = "${binding.delegatePropertyName}Make"
    builder.addProperty(
        PropertySpec.builder(cacheName, type.copy(nullable = true))
            .addModifiers(KModifier.PRIVATE)
            .mutable(true)
            .addAnnotation(KOTLIN_VOLATILE_CLASS_NAME)
            .initializer("null")
            .build(),
    )
    builder.addFunction(
        FunSpec.builder(makeName)
            .addModifiers(KModifier.PRIVATE)
            .returns(type)
            .addCode(
                CodeBlock.builder()
                    .add("val candidate: %T = kotlin.run {\n", type)
                    .indent()
                    .add(renderReadOnlyCollectionDelegateInitializer(binding))
                    .unindent()
                    .add("}\n")
                    .add("return %M(\n", PUBLISH_GENERATED_WINRT_VALUE_FUNCTION_NAME)
                    .indent()
                    .add("candidate,\n")
                    .add("{ %L },\n", cacheName)
                    .add("{ value ->\n")
                    .indent()
                    .add("%L = value\n", cacheName)
                    .unindent()
                    .add("},\n")
                    .unindent()
                    .add(")\n")
                    .build(),
            )
            .build(),
    )
    builder.addProperty(
        PropertySpec.builder(binding.delegatePropertyName, type)
            .addModifiers(KModifier.PRIVATE)
            .getter(
                FunSpec.getterBuilder()
                    .addCode("return %L ?: %L()\n", cacheName, makeName)
                    .build(),
            )
            .build(),
    )
}

internal fun KotlinProjectionRenderer.renderReadOnlyCollectionDelegateInitializer(
    binding: KotlinProjectionReadOnlyCollectionBinding,
): CodeBlock = when (binding.kind) {
    KotlinProjectionReadOnlyCollectionKind.Iterable ->
        bindableCollectionDelegateInitializer(binding.slotInterfaceQualifiedName, binding.ownerCachePropertyName)
            ?: runtimeReadOnlyCollectionDelegateInitializer(binding)
            ?: error("Missing runtime collection element ABI for $binding")
    KotlinProjectionReadOnlyCollectionKind.VectorView ->
        bindableCollectionDelegateInitializer(binding.slotInterfaceQualifiedName, binding.ownerCachePropertyName)
            ?: runtimeReadOnlyCollectionDelegateInitializer(binding)
            ?: error("Missing runtime collection element ABI for $binding")
    KotlinProjectionReadOnlyCollectionKind.MapView ->
        runtimeReadOnlyCollectionDelegateInitializer(binding)
            ?: error("Missing runtime collection element ABI for $binding")
}

private fun KotlinProjectionRenderer.runtimeReadOnlyCollectionDelegateInitializer(
    binding: KotlinProjectionReadOnlyCollectionBinding,
): CodeBlock? =
    when (binding.kind) {
        KotlinProjectionReadOnlyCollectionKind.Iterable -> {
            val elementAdapter = collectionReferenceAdapterCode(requireNotNull(binding.elementBinding)) ?: return null
            CodeBlock.of(
                "%T.fromAbi(%L.acquireOwnedPointer().asRawAddress(), %L) ?: error(%S)\n",
                WINRT_ITERABLE_PROJECTION_CLASS_NAME,
                binding.ownerCachePropertyName,
                elementAdapter,
                "WINRT_E_NULL_ABI_RETURN",
            )
        }
        KotlinProjectionReadOnlyCollectionKind.VectorView -> {
            val elementAdapter = collectionReferenceAdapterCode(requireNotNull(binding.elementBinding)) ?: return null
            CodeBlock.of(
                "%T.fromAbi(%L.acquireOwnedPointer().asRawAddress(), %L) ?: error(%S)\n",
                WINRT_READ_ONLY_LIST_PROJECTION_CLASS_NAME,
                binding.ownerCachePropertyName,
                elementAdapter,
                "WINRT_E_NULL_ABI_RETURN",
            )
        }
        KotlinProjectionReadOnlyCollectionKind.MapView -> {
            val keyAdapter = collectionReferenceAdapterCode(requireNotNull(binding.keyBinding)) ?: return null
            val valueAdapter = collectionReferenceAdapterCode(requireNotNull(binding.valueBinding)) ?: return null
            CodeBlock.of(
                "%T.fromAbi(%L.acquireOwnedPointer().asRawAddress(), %L, %L) ?: error(%S)\n",
                WINRT_READ_ONLY_DICTIONARY_PROJECTION_CLASS_NAME,
                binding.ownerCachePropertyName,
                keyAdapter,
                valueAdapter,
                "WINRT_E_NULL_ABI_RETURN",
            )
        }
    }

private fun bindableCollectionDelegateInitializer(
    slotInterfaceQualifiedName: String,
    ownerCachePropertyName: String,
): CodeBlock? =
    when (mappedTypeByAbiName(slotInterfaceQualifiedName)?.abiValueKind) {
        KotlinProjectionAbiValueKind.MappedBindableIterable ->
            CodeBlock.of("%T.fromAbi(%L)\n", WINRT_BINDABLE_ITERABLE_PROJECTION_CLASS_NAME, ownerCachePropertyName)
        KotlinProjectionAbiValueKind.MappedBindableVectorView ->
            CodeBlock.of("%T.fromAbi(%L)\n", WINRT_BINDABLE_VECTOR_VIEW_PROJECTION_CLASS_NAME, ownerCachePropertyName)
        KotlinProjectionAbiValueKind.MappedBindableVector ->
            CodeBlock.of("%T.fromAbi(%L)\n", WINRT_BINDABLE_VECTOR_PROJECTION_CLASS_NAME, ownerCachePropertyName)
        else -> null
    }

internal fun KotlinProjectionRenderer.renderCollectionInvocation(
    invokeTargetExpression: String,
    slotInterfaceQualifiedName: String,
    slotConstantName: String,
    returnBinding: KotlinProjectionAbiTypeBinding,
    parameterBindings: List<KotlinProjectionAbiParameterBinding> = emptyList(),
): CodeBlock {
    val slotExpression = CodeBlock.of("%T.Metadata.%L", projectionClassName(slotInterfaceQualifiedName), slotConstantName)
    val callPlan = requireAbiCallPlan(
        bindingName = "${slotInterfaceQualifiedName.substringAfterLast('.')}_$slotConstantName",
        returnBinding = returnBinding,
        parameterBindings = parameterBindings,
    )
    return renderInlineAbiInvocation(
        invokeTargetExpression = invokeTargetExpression,
        slotExpression = slotExpression,
        callPlan = callPlan,
    ) ?: error("Generator read-only collection parity failed to emit $slotInterfaceQualifiedName.$slotConstantName")
}
