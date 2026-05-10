package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRtMetadataModel
import io.github.composefluent.winrt.metadata.WinRtAbiMarshalerPlanDescriptor
import io.github.composefluent.winrt.metadata.WinRtAbiMarshalerSlotDescriptor
import io.github.composefluent.winrt.metadata.WinRtCustomMappedMemberOutputDescriptor
import io.github.composefluent.winrt.metadata.WinRtEventDefinition
import io.github.composefluent.winrt.metadata.WinRtEventInvokeDescriptor
import io.github.composefluent.winrt.metadata.WinRtFactorySurfaceDescriptor
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
import io.github.composefluent.winrt.runtime.WinRtAsyncOperationReference
import io.github.composefluent.winrt.runtime.WinRtAsyncOperationWithProgressReference
import io.github.composefluent.winrt.runtime.WinRtAsyncOperationWithProgressVftblSlots
import io.github.composefluent.winrt.runtime.WinRtAsyncOperationVftblSlots
import io.github.composefluent.winrt.runtime.WinRtReadOnlyDictionaryProjection
import io.github.composefluent.winrt.runtime.WinRtReadOnlyListProjection
import io.github.composefluent.winrt.runtime.WinRtReferenceArrayProjection
import io.github.composefluent.winrt.runtime.WinRtReferenceProjection
import io.github.composefluent.winrt.runtime.WinRtReferenceValueAdapter
import io.github.composefluent.winrt.runtime.WinRtPlatformApi
import io.github.composefluent.winrt.runtime.WinRtTypeSignature
import io.github.composefluent.winrt.runtime.WinRtTypeHandle
import io.github.composefluent.winrt.runtime.WinRtUri
import io.github.composefluent.winrt.runtime.WinRtDelegateBridge
import io.github.composefluent.winrt.runtime.WinRtDelegateDescriptor
import io.github.composefluent.winrt.runtime.WinRtDelegateReference
import io.github.composefluent.winrt.runtime.WinRtDelegateValueKind
import io.github.composefluent.winrt.runtime.WinRtEvent
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
import kotlin.LazyThreadSafetyMode
import kotlin.io.path.extension

internal fun KotlinProjectionRenderer.renderInlineAbiInvocation(
    invokeTargetExpression: String,
    slotExpression: String,
    callPlan: KotlinProjectionAbiCallPlan,
): CodeBlock? =
    renderInlineAbiInvocation(invokeTargetExpression, CodeBlock.of("%L", slotExpression), callPlan)

internal fun KotlinProjectionRenderer.renderInlineAbiInvocation(
    invokeTargetExpression: String,
    slotExpression: CodeBlock,
    callPlan: KotlinProjectionAbiCallPlan,
): CodeBlock? =
    renderInlineAbiInvocation(
        invokeTargetExpression = invokeTargetExpression,
        slotExpression = slotExpression,
        callPlan = callPlan,
        renderInvocation = { target, slot, args -> renderComVtableInvocation(target, slot, args) },
    )

internal fun KotlinProjectionRenderer.renderInlineAbiInvocation(
    invokeTargetExpression: String,
    slotExpression: CodeBlock,
    callPlan: KotlinProjectionAbiCallPlan,
    renderInvocation: (
        invokeTargetExpression: String,
        slotExpression: CodeBlock,
        abiArguments: List<KotlinProjectionComArgument>,
    ) -> CodeBlock,
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
    val finallyStatements = callPlan.parameterMarshalers.flatMap { it.finallyStatements }
    if (finallyStatements.isNotEmpty()) {
        code.add("try {\n")
        code.indent()
    }
    val abiArguments = callPlan.parameterMarshalers.flatMap { marshaler ->
        listOf(
            KotlinProjectionComArgument(marshaler.abiArgumentExpression, marshaler.abiArgumentKind),
        ) + marshaler.extraAbiArgumentExpressions.mapIndexed { index, expression ->
            KotlinProjectionComArgument(expression, marshaler.extraAbiArgumentKinds.getOrNull(index))
        }
    } + if (resultMarshaler != null) {
        listOf(
            KotlinProjectionComArgument(resultMarshaler.abiArgumentExpression, resultMarshaler.abiArgumentKind),
        ) + resultMarshaler.extraAbiArgumentExpressions.mapIndexed { index, expression ->
            KotlinProjectionComArgument(expression, resultMarshaler.extraAbiArgumentKinds.getOrNull(index))
        }
    } else {
        emptyList()
    }
    val intrinsicUnitInvocation =
        if (resultMarshaler == null && !callPlan.suppressHResultCheck) {
            renderInlineDescriptorUnitIntrinsicInvocation(
                invokeTargetExpression = invokeTargetExpression,
                slotExpression = slotExpression,
                abiArguments = abiArguments,
            )
        } else {
            null
        }
    if (intrinsicUnitInvocation != null) {
        code.add("%L", intrinsicUnitInvocation)
    } else {
        code.add("val __hr = ")
        code.add(
            renderInvocation(invokeTargetExpression, slotExpression, abiArguments),
        )
        code.add("\n")
        if (!callPlan.suppressHResultCheck) {
            code.addStatement("%T(__hr).requireSuccess()", HRESULT_CLASS_NAME)
        }
    }
    callPlan.parameterMarshalers.flatMap { it.postCallStatements }.forEach { postCallStatement ->
        code.add("%L\n", postCallStatement)
    }
    resultMarshaler?.readbackStatement?.let(code::add)
    if (finallyStatements.isNotEmpty()) {
        code.unindent()
        code.add("} finally {\n")
        code.indent()
        finallyStatements.forEach { finallyStatement ->
            code.add("%L\n", finallyStatement)
        }
        code.unindent()
        code.add("}\n")
    }
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

private fun KotlinProjectionRenderer.renderInlineDescriptorUnitIntrinsicInvocation(
    invokeTargetExpression: String,
    slotExpression: CodeBlock,
    abiArguments: List<KotlinProjectionComArgument>,
): CodeBlock? {
    if (!useProjectionIntrinsics || abiArguments.isEmpty()) {
        return null
    }
    val argumentShapes = abiArguments.map { argument ->
        argument.kind?.descriptorUnitToken() ?: return null
    }
    return CodeBlock.builder()
        .add("%T.callUnit(\n", WINRT_PROJECTION_INTRINSIC_CLASS_NAME)
        .indent()
        .add("%L,\n", invokeTargetExpression)
        .add("%L,\n", slotExpression)
        .add("%S,\n", argumentShapes.joinToString(","))
        .apply {
            abiArguments.forEach { argument ->
                add("%L,\n", argument.expression)
            }
        }
        .unindent()
        .add(")\n")
        .build()
}

private fun KotlinProjectionComArgumentKind.descriptorUnitToken(): String? =
    when (this) {
        KotlinProjectionComArgumentKind.Pointer -> "RawAddress"
        KotlinProjectionComArgumentKind.Int8 -> "Byte"
        KotlinProjectionComArgumentKind.Int32 -> "Int32"
        KotlinProjectionComArgumentKind.Int64 -> "Int64"
        KotlinProjectionComArgumentKind.Float -> "Float"
        KotlinProjectionComArgumentKind.Double -> "Double"
        KotlinProjectionComArgumentKind.Int16 -> null
    }

internal fun KotlinProjectionRenderer.renderComVtableInvocation(
    invokeTargetExpression: String,
    slotExpression: CodeBlock,
    abiArguments: List<KotlinProjectionComArgument>,
): CodeBlock {
    val builder = CodeBlock.builder()
    if (abiArguments.isEmpty()) {
        builder.add(
            "%T.invoke(instance = %L.pointer, slot = %L)",
            COM_VTABLE_INVOKER_CLASS_NAME,
            invokeTargetExpression,
            slotExpression,
        )
    } else if (hasDirectVtableInvokeOverload(abiArguments.map { it.kind })) {
        builder.add(
            "%T.invokeArgs(instance = %L.pointer, slot = %L",
            COM_VTABLE_INVOKER_CLASS_NAME,
            invokeTargetExpression,
            slotExpression,
        )
        abiArguments.forEachIndexed { index, argument ->
            builder.add(", arg%L = %L", index, argument.expression)
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
            builder.add(", %L", argument.expression)
        }
        builder.add(")")
    }
    return builder.build()
}

internal data class KotlinProjectionComArgument(
    val expression: CodeBlock,
    val kind: KotlinProjectionComArgumentKind?,
)

internal fun hasDirectVtableInvokeOverload(kinds: List<KotlinProjectionComArgumentKind?>): Boolean =
    kinds.all { it != null } && DIRECT_VTABLE_INVOKE_OVERLOADS.contains(kinds)

private val P = KotlinProjectionComArgumentKind.Pointer
private val I32 = KotlinProjectionComArgumentKind.Int32

private val DIRECT_VTABLE_INVOKE_OVERLOADS: Set<List<KotlinProjectionComArgumentKind>> = setOf(
    listOf(P),
    listOf(I32),
    listOf(P, P),
    listOf(I32, P),
    listOf(I32, I32),
    listOf(P, P, P),
    listOf(I32, P, P),
    listOf(P, I32, P),
    listOf(I32, I32, P, P),
    listOf(P, P, I32, P),
    listOf(P, P, P, P),
    listOf(P, P, P, I32, P),
    listOf(P, P, I32, P, I32, P),
    listOf(P, P, P, I32, P, I32),
)
