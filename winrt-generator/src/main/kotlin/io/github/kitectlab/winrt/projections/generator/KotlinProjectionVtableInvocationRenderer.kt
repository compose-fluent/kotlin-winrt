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
import io.github.kitectlab.winrt.runtime.WinRtUri
import io.github.kitectlab.winrt.runtime.WinRtDelegateBridge
import io.github.kitectlab.winrt.runtime.WinRtDelegateDescriptor
import io.github.kitectlab.winrt.runtime.WinRtDelegateReference
import io.github.kitectlab.winrt.runtime.WinRtDelegateValueKind
import io.github.kitectlab.winrt.runtime.WinRtEvent
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
    code.add("val __hr = ")
    code.add(
        renderComVtableInvocation(
            invokeTargetExpression = invokeTargetExpression,
            slotExpression = slotExpression,
            abiArguments = abiArguments,
        ),
    )
    code.add("\n")
    if (!callPlan.suppressHResultCheck) {
        code.addStatement("%T(__hr).requireSuccess()", HRESULT_CLASS_NAME)
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
private val I8 = KotlinProjectionComArgumentKind.Int8
private val I16 = KotlinProjectionComArgumentKind.Int16
private val I32 = KotlinProjectionComArgumentKind.Int32
private val I64 = KotlinProjectionComArgumentKind.Int64
private val F = KotlinProjectionComArgumentKind.Float
private val D = KotlinProjectionComArgumentKind.Double

private val DIRECT_VTABLE_INVOKE_OVERLOADS: Set<List<KotlinProjectionComArgumentKind>> = setOf(
    listOf(P),
    listOf(I8),
    listOf(I16),
    listOf(I32),
    listOf(I64),
    listOf(F),
    listOf(D),
    listOf(P, P),
    listOf(I8, P),
    listOf(I32, P),
    listOf(I32, I32),
    listOf(D, P),
    listOf(D, D, P),
    listOf(I8, I16, F),
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
