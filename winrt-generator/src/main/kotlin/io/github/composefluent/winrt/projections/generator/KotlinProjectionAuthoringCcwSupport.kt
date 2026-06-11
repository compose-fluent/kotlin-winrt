package io.github.composefluent.winrt.projections.generator

import com.squareup.kotlinpoet.CodeBlock
import io.github.composefluent.winrt.metadata.WinRtMetadataParameterCategory
import io.github.composefluent.winrt.metadata.WinRtMethodDefinition
import io.github.composefluent.winrt.metadata.WinRtParameterDefinition
import io.github.composefluent.winrt.metadata.WinRtTypeDefinition

internal fun authoredCcwBindingIsSupported(
    typeRenderer: KotlinProjectionRenderer,
    interfaceType: WinRtTypeDefinition,
    binding: KotlinProjectionInstanceMemberBinding,
): Boolean =
    authoredCcwBindingUnsupportedReason(typeRenderer, interfaceType, binding) == null

internal fun authoredCcwBindingUnsupportedReason(
    typeRenderer: KotlinProjectionRenderer,
    interfaceType: WinRtTypeDefinition,
    binding: KotlinProjectionInstanceMemberBinding,
): String? {
    val receiveArrayParameterName = authoredCcwReceiveArrayReturnParameter(interfaceType, binding)?.name
    binding.parameterBindings
        .firstOrNull { parameter ->
            parameter.category == WinRtMetadataParameterCategory.ReceiveArray &&
                parameter.name != receiveArrayParameterName
        }
        ?.let { parameter ->
            return "parameter ${parameter.name} receive-array ABI shape ${parameter.typeBinding.describeAbiKind()}"
        }
    binding.parameterBindings.forEach { parameter ->
        authoredCcwParameterAbiBindingUnsupportedReason(typeRenderer, parameter.typeBinding)?.let { reason ->
            return "parameter ${parameter.name} $reason"
        }
    }
    authoredCcwReturnAbiBindingUnsupportedReason(typeRenderer, binding.returnBinding)?.let { reason ->
        return "return $reason"
    }
    return null
}

internal fun authoredCcwBindingHasIntentionalFallback(
    interfaceType: WinRtTypeDefinition,
    binding: KotlinProjectionInstanceMemberBinding,
): Boolean =
    binding.parameterBindings.any { parameter -> parameter.category == WinRtMetadataParameterCategory.ReceiveArray } &&
        authoredCcwReceiveArrayReturnParameter(interfaceType, binding) == null

internal fun authoredCcwReceiveArrayReturnParameter(
    interfaceType: WinRtTypeDefinition,
    binding: KotlinProjectionInstanceMemberBinding,
): WinRtParameterDefinition? =
    interfaceType.methods
        .filter(WinRtMethodDefinition::isOrdinaryProjectedMethod)
        .firstOrNull { method -> binding.bindingName == method.abiSlotConstantName(interfaceType.methods) }
        ?.receiveArrayResultParameter()

internal fun authoredCcwAbiBindingIsSupported(
    typeRenderer: KotlinProjectionRenderer,
    binding: KotlinProjectionAbiTypeBinding,
): Boolean = authoredCcwReturnAbiBindingUnsupportedReason(typeRenderer, binding) == null

private fun authoredCcwParameterAbiBindingUnsupportedReason(
    typeRenderer: KotlinProjectionRenderer,
    binding: KotlinProjectionAbiTypeBinding,
): String? =
    authoredCcwAbiBindingUnsupportedReason(typeRenderer, binding, allowAsyncReference = false)

private fun authoredCcwReturnAbiBindingUnsupportedReason(
    typeRenderer: KotlinProjectionRenderer,
    binding: KotlinProjectionAbiTypeBinding,
): String? =
    authoredCcwAbiBindingUnsupportedReason(typeRenderer, binding, allowAsyncReference = true)

private fun authoredCcwAbiBindingUnsupportedReason(
    typeRenderer: KotlinProjectionRenderer,
    binding: KotlinProjectionAbiTypeBinding,
    allowAsyncReference: Boolean,
): String? {
    return when (binding.kind) {
        KotlinProjectionAbiValueKind.Unit,
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
        KotlinProjectionAbiValueKind.Enum,
        KotlinProjectionAbiValueKind.Object,
        KotlinProjectionAbiValueKind.ProjectedInterface,
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass,
        KotlinProjectionAbiValueKind.Delegate,
        KotlinProjectionAbiValueKind.GenericParameter,
        KotlinProjectionAbiValueKind.UnknownReference,
        KotlinProjectionAbiValueKind.InspectableReference -> null
        KotlinProjectionAbiValueKind.MappedAsyncAction,
        KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress,
        KotlinProjectionAbiValueKind.MappedAsyncOperation,
        KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress ->
            if (allowAsyncReference) {
                null
            } else {
                "${binding.describeAbiKind()} uses unsupported authored ABI shape"
            }
        KotlinProjectionAbiValueKind.Array -> {
            val elementBinding = binding.typeArguments.singleOrNull()
                ?: return "array ${binding.describeAbiKind()} is missing an element ABI binding"
            if (authoredCcwArrayElementBindingIsSupported(typeRenderer, elementBinding)) {
                null
            } else {
                "array element ${elementBinding.describeAbiKind()} uses unsupported authored ABI shape"
            }
        }
        KotlinProjectionAbiValueKind.MappedIterable,
        KotlinProjectionAbiValueKind.MappedVector,
        KotlinProjectionAbiValueKind.MappedVectorView,
        KotlinProjectionAbiValueKind.MappedMap,
        KotlinProjectionAbiValueKind.MappedMapView ->
            if (authoredCcwMappedCollectionBindingIsSupported(typeRenderer, binding)) {
                null
            } else {
                "collection ${binding.describeAbiKind()} uses unsupported authored ABI shape"
            }
        KotlinProjectionAbiValueKind.Struct ->
            if (binding.resolvedTypeName == "Windows.Foundation.EventRegistrationToken" ||
                authoredCcwCustomStructAbiIsSupported(binding) ||
                typeRenderer.nativeStructClassName(binding) != null
            ) {
                null
            } else {
                "struct ${binding.describeAbiKind()} uses unsupported authored ABI shape"
            }
        else -> "${binding.describeAbiKind()} uses unsupported authored ABI shape"
    }
}

private fun authoredCcwArrayElementBindingIsSupported(
    typeRenderer: KotlinProjectionRenderer,
    binding: KotlinProjectionAbiTypeBinding,
): Boolean =
    typeRenderer.nativeArrayElementReadCode(binding, CodeBlock.of("__data"), CodeBlock.of("__index")) != null ||
        typeRenderer.nonBlittableArrayElementMarshalerExpression(binding) != null

private fun authoredCcwMappedCollectionBindingIsSupported(
    typeRenderer: KotlinProjectionRenderer,
    binding: KotlinProjectionAbiTypeBinding,
): Boolean =
    when (binding.kind) {
        KotlinProjectionAbiValueKind.MappedIterable,
        KotlinProjectionAbiValueKind.MappedVector,
        KotlinProjectionAbiValueKind.MappedVectorView ->
            binding.typeArguments.singleOrNull()?.let(typeRenderer::collectionReferenceAdapterCode) != null
        KotlinProjectionAbiValueKind.MappedMap,
        KotlinProjectionAbiValueKind.MappedMapView ->
            binding.typeArguments.size == 2 &&
                binding.typeArguments.all { argument -> typeRenderer.collectionReferenceAdapterCode(argument) != null }
        else -> false
    }

private fun authoredCcwCustomStructAbiIsSupported(binding: KotlinProjectionAbiTypeBinding): Boolean =
    customStructAbi(binding)?.let { customAbi ->
        (customAbi.abiArgumentKind != null && customAbi.fromAbiCarrierFunctionName != null) ||
            customAbi.abiLayoutExpression != null
    } == true
