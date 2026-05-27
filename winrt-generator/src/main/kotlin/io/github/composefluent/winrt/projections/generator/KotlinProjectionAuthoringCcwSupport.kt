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
        authoredCcwAbiBindingUnsupportedReason(typeRenderer, parameter.typeBinding)?.let { reason ->
            return "parameter ${parameter.name} $reason"
        }
    }
    authoredCcwAbiBindingUnsupportedReason(typeRenderer, binding.returnBinding)?.let { reason ->
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
): Boolean = authoredCcwAbiBindingUnsupportedReason(typeRenderer, binding) == null

private fun authoredCcwAbiBindingUnsupportedReason(
    typeRenderer: KotlinProjectionRenderer,
    binding: KotlinProjectionAbiTypeBinding,
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
        KotlinProjectionAbiValueKind.Array -> {
            val elementBinding = binding.typeArguments.singleOrNull()
                ?: return "array ${binding.describeAbiKind()} is missing an element ABI binding"
            if (authoredCcwArrayElementBindingIsSupported(typeRenderer, elementBinding)) {
                null
            } else {
                "array element ${elementBinding.describeAbiKind()} uses unsupported authored ABI shape"
            }
        }
        KotlinProjectionAbiValueKind.Struct ->
            if (binding.resolvedTypeName == "Windows.Foundation.EventRegistrationToken" ||
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
