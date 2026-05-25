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
): Boolean {
    val receiveArrayParameterName = authoredCcwReceiveArrayReturnParameter(interfaceType, binding)?.name
    return binding.parameterBindings.all { parameter ->
        parameter.category != WinRtMetadataParameterCategory.ReceiveArray ||
            parameter.name == receiveArrayParameterName
    } && binding.parameterBindings.all { authoredCcwAbiBindingIsSupported(typeRenderer, it.typeBinding) } &&
        authoredCcwAbiBindingIsSupported(typeRenderer, binding.returnBinding)
}

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
): Boolean = when (binding.kind) {
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
    KotlinProjectionAbiValueKind.InspectableReference -> true
    KotlinProjectionAbiValueKind.Array -> binding.typeArguments.singleOrNull()
        ?.let { authoredCcwArrayElementBindingIsSupported(typeRenderer, it) } == true
    KotlinProjectionAbiValueKind.Struct ->
        binding.resolvedTypeName == "Windows.Foundation.EventRegistrationToken" ||
            typeRenderer.nativeStructClassName(binding) != null
    else -> false
}

private fun authoredCcwArrayElementBindingIsSupported(
    typeRenderer: KotlinProjectionRenderer,
    binding: KotlinProjectionAbiTypeBinding,
): Boolean =
    typeRenderer.nativeArrayElementReadCode(binding, CodeBlock.of("__data"), CodeBlock.of("__index")) != null ||
        typeRenderer.nonBlittableArrayElementMarshalerExpression(binding) != null
