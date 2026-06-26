package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTAbiMarshalerPlanDescriptor
import io.github.composefluent.winrt.metadata.WinRTAbiMarshalerSlotDescriptor
import io.github.composefluent.winrt.metadata.WinRTCustomMappedMemberOutputDescriptor
import io.github.composefluent.winrt.metadata.WinRTEventDefinition
import io.github.composefluent.winrt.metadata.WinRTEventInvokeDescriptor
import io.github.composefluent.winrt.metadata.WinRTFactorySurfaceDescriptor
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
import io.github.composefluent.winrt.runtime.WinRTUri
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
    val parameterAbiArguments = callPlan.parameterMarshalers.flatMap { marshaler ->
        listOf(
            KotlinProjectionComArgument(marshaler.abiArgumentExpression, marshaler.abiArgumentKind),
        ) + marshaler.extraAbiArgumentExpressions.mapIndexed { index, expression ->
            KotlinProjectionComArgument(expression, marshaler.extraAbiArgumentKinds.getOrNull(index))
        }
    }
    val finallyStatements = callPlan.parameterMarshalers.flatMap { it.finallyStatements }
    val inlineResultInvocation =
        if (resultMarshaler != null && !callPlan.suppressHResultCheck) {
            renderInlineDescriptorProjectedObjectIntrinsicInvocation(
                invokeTargetExpression = invokeTargetExpression,
                slotExpression = slotExpression,
                returnBinding = resultMarshaler.typeBinding,
                abiArguments = parameterAbiArguments,
            ) ?: renderInlineDescriptorMappedCollectionIntrinsicInvocation(
                invokeTargetExpression = invokeTargetExpression,
                slotExpression = slotExpression,
                returnBinding = resultMarshaler.typeBinding,
                abiArguments = parameterAbiArguments,
            ) ?: renderInlineDescriptorInspectableReferenceIntrinsicInvocation(
                invokeTargetExpression = invokeTargetExpression,
                slotExpression = slotExpression,
                returnBinding = resultMarshaler.typeBinding,
                abiArguments = parameterAbiArguments,
            ) ?: renderInlineDescriptorBooleanIntrinsicInvocation(
                invokeTargetExpression = invokeTargetExpression,
                slotExpression = slotExpression,
                returnBinding = resultMarshaler.typeBinding,
                abiArguments = parameterAbiArguments,
            ) ?: renderInlineDescriptorScalarIntrinsicInvocation(
                invokeTargetExpression = invokeTargetExpression,
                slotExpression = slotExpression,
                returnBinding = resultMarshaler.typeBinding,
                abiArguments = parameterAbiArguments,
            ) ?: renderInlineDescriptorObjectIntrinsicInvocation(
                invokeTargetExpression = invokeTargetExpression,
                slotExpression = slotExpression,
                returnBinding = resultMarshaler.typeBinding,
                abiArguments = parameterAbiArguments,
            )
        } else {
            null
        }
    if (inlineResultInvocation != null) {
        if (finallyStatements.isNotEmpty()) {
            code.add("try {\n")
            code.indent()
        }
        code.add("%L", inlineResultInvocation)
        callPlan.parameterMarshalers.flatMap { it.postCallStatements }.forEach { postCallStatement ->
            code.add("%L\n", postCallStatement)
        }
        code.add("return __result\n")
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
        repeat(scopedParameterOpeners.size) {
            code.unindent()
            code.add("}\n")
        }
        return code.build()
    }
    if (resultMarshaler != null) {
        code.add("%T.confinedScope().use { __scope ->\n", PLATFORM_ABI_CLASS_NAME)
        code.indent()
        resultMarshaler.resultLocalDeclarations?.let { declarations ->
            code.add("%L", declarations)
        } ?: code.addStatement("val __resultOut = %L", requireNotNull(resultMarshaler.resultAllocation))
    }
    if (finallyStatements.isNotEmpty()) {
        code.add("try {\n")
        code.indent()
    }
    val abiArguments = parameterAbiArguments + if (resultMarshaler != null) {
        listOf(
            KotlinProjectionComArgument(resultMarshaler.abiArgumentExpression, resultMarshaler.abiArgumentKind),
        ) + resultMarshaler.extraAbiArgumentExpressions.mapIndexed { index, expression ->
            KotlinProjectionComArgument(expression, resultMarshaler.extraAbiArgumentKinds.getOrNull(index))
        }
    } else {
        emptyList()
    }
    val intrinsicHResultInvocation =
        if (!callPlan.suppressHResultCheck) {
            renderInlineDescriptorUnitIntrinsicInvocation(
                invokeTargetExpression = invokeTargetExpression,
                slotExpression = slotExpression,
                abiArguments = abiArguments,
            )
        } else {
            null
        }
    if (intrinsicHResultInvocation != null) {
        code.add("%L", intrinsicHResultInvocation)
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

internal fun KotlinProjectionRenderer.renderInlineDescriptorUnitIntrinsicInvocation(
    invokeTargetExpression: String,
    slotExpression: CodeBlock,
    abiArguments: List<KotlinProjectionComArgument>,
): CodeBlock? {
    if (!useProjectionIntrinsics) {
        return null
    }
    val argumentShapes = abiArguments.map { argument ->
        argument.kind?.descriptorAbiToken() ?: return null
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

private fun KotlinProjectionRenderer.renderInlineDescriptorProjectedObjectIntrinsicInvocation(
    invokeTargetExpression: String,
    slotExpression: CodeBlock,
    returnBinding: KotlinProjectionAbiTypeBinding,
    abiArguments: List<KotlinProjectionComArgument>,
): CodeBlock? {
    if (
        !useProjectionIntrinsics ||
        abiArguments.isEmpty() ||
        returnBinding.isNullableAbiReturn ||
        customObjectAbi(returnBinding) != null
    ) {
        return null
    }
    val helperFunction = when (returnBinding.kind) {
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass -> "callProjectedRuntimeClass"
        KotlinProjectionAbiValueKind.ProjectedInterface -> "callProjectedInterface"
        else -> return null
    }
    val returnType = resolvedReturnClassName(returnBinding) ?: return null
    val argumentShapes = abiArguments.map { argument ->
        argument.kind?.descriptorAbiToken() ?: return null
    }
    return CodeBlock.builder()
        .add("val __result = %T.%L(\n", WINRT_PROJECTION_INTRINSIC_CLASS_NAME, helperFunction)
        .indent()
        .add("%L,\n", invokeTargetExpression)
        .add("%L,\n", slotExpression)
        .add("%S,\n", argumentShapes.joinToString(","))
        .add("%T.Metadata::wrap,\n", returnType)
        .apply {
            abiArguments.forEach { argument ->
                add("%L,\n", argument.expression)
            }
        }
        .unindent()
        .add(")\n")
        .build()
}

private fun KotlinProjectionRenderer.renderInlineDescriptorObjectIntrinsicInvocation(
    invokeTargetExpression: String,
    slotExpression: CodeBlock,
    returnBinding: KotlinProjectionAbiTypeBinding,
    abiArguments: List<KotlinProjectionComArgument>,
): CodeBlock? {
    if (
        !useProjectionIntrinsics ||
        abiArguments.isEmpty() ||
        returnBinding.kind != KotlinProjectionAbiValueKind.Object ||
        customObjectAbi(returnBinding) != null ||
        resolvedReturnClassName(returnBinding) == IINSPECTABLE_REFERENCE_CLASS_NAME
    ) {
        return null
    }
    val argumentShapes = abiArguments.map { argument ->
        argument.kind?.descriptorAbiToken() ?: return null
    }
    return CodeBlock.builder()
        .add("val __result = %T.callObject(\n", WINRT_PROJECTION_INTRINSIC_CLASS_NAME)
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

private fun KotlinProjectionRenderer.renderInlineDescriptorBooleanIntrinsicInvocation(
    invokeTargetExpression: String,
    slotExpression: CodeBlock,
    returnBinding: KotlinProjectionAbiTypeBinding,
    abiArguments: List<KotlinProjectionComArgument>,
): CodeBlock? {
    if (
        !useProjectionIntrinsics ||
        abiArguments.isEmpty() ||
        returnBinding.kind != KotlinProjectionAbiValueKind.Boolean
    ) {
        return null
    }
    val argumentShapes = abiArguments.map { argument ->
        argument.kind?.descriptorAbiToken() ?: return null
    }
    return CodeBlock.builder()
        .add("val __result = %T.callBoolean(\n", WINRT_PROJECTION_INTRINSIC_CLASS_NAME)
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

private fun KotlinProjectionRenderer.renderInlineDescriptorScalarIntrinsicInvocation(
    invokeTargetExpression: String,
    slotExpression: CodeBlock,
    returnBinding: KotlinProjectionAbiTypeBinding,
    abiArguments: List<KotlinProjectionComArgument>,
): CodeBlock? {
    if (!useProjectionIntrinsics || abiArguments.isEmpty()) {
        return null
    }
    val returnShape = scalarIntrinsicReturnShape(returnBinding) ?: return null
    val returnType = resolvedReturnClassName(returnBinding) ?: return null
    val argumentShapes = abiArguments.map { argument ->
        argument.kind?.descriptorAbiToken() ?: return null
    }
    return CodeBlock.builder()
        .add("val __result = %T.callScalar<%T>(\n", WINRT_PROJECTION_INTRINSIC_CLASS_NAME, returnType)
        .indent()
        .add("%L,\n", invokeTargetExpression)
        .add("%L,\n", slotExpression)
        .add("%S,\n", returnShape)
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

private fun KotlinProjectionRenderer.renderInlineDescriptorMappedCollectionIntrinsicInvocation(
    invokeTargetExpression: String,
    slotExpression: CodeBlock,
    returnBinding: KotlinProjectionAbiTypeBinding,
    abiArguments: List<KotlinProjectionComArgument>,
): CodeBlock? {
    if (!useProjectionIntrinsics || abiArguments.isEmpty() || !returnBinding.isMappedCollectionBinding()) {
        return null
    }
    val collectionInitializer =
        readOnlyCollectionBindingForReturn(returnBinding)?.let(::renderReadOnlyCollectionDelegateInitializer)
            ?: mutableCollectionBindingForReturn(returnBinding)?.let(::renderMutableCollectionDelegateInitializer)
            ?: return null
    val argumentShapes = abiArguments.map { argument ->
        argument.kind?.descriptorAbiToken() ?: return null
    }
    return CodeBlock.builder()
        .add("val __result = %T.callProjectedInterface(\n", WINRT_PROJECTION_INTRINSIC_CLASS_NAME)
        .indent()
        .add("%L,\n", invokeTargetExpression)
        .add("%L,\n", slotExpression)
        .add("%S,\n", argumentShapes.joinToString(","))
        .add("{ __collectionRef ->\n")
        .indent()
        .add("%L", collectionInitializer)
        .unindent()
        .add("},\n")
        .apply {
            abiArguments.forEach { argument ->
                add("%L,\n", argument.expression)
            }
        }
        .unindent()
        .add(")\n")
        .build()
}

private fun KotlinProjectionRenderer.renderInlineDescriptorInspectableReferenceIntrinsicInvocation(
    invokeTargetExpression: String,
    slotExpression: CodeBlock,
    returnBinding: KotlinProjectionAbiTypeBinding,
    abiArguments: List<KotlinProjectionComArgument>,
): CodeBlock? {
    if (
        !useProjectionIntrinsics ||
        abiArguments.isEmpty() ||
        returnBinding.kind != KotlinProjectionAbiValueKind.InspectableReference ||
        resolvedReturnClassName(returnBinding) != IINSPECTABLE_REFERENCE_CLASS_NAME
    ) {
        return null
    }
    val argumentShapes = abiArguments.map { argument ->
        argument.kind?.descriptorAbiToken() ?: return null
    }
    return CodeBlock.builder()
        .add("val __result = %T.callProjectedInterface(\n", WINRT_PROJECTION_INTRINSIC_CLASS_NAME)
        .indent()
        .add("%L,\n", invokeTargetExpression)
        .add("%L,\n", slotExpression)
        .add("%S,\n", argumentShapes.joinToString(","))
        .add("{ __result -> __result.use { it.asInspectable() } },\n")
        .apply {
            abiArguments.forEach { argument ->
                add("%L,\n", argument.expression)
            }
        }
        .unindent()
        .add(")\n")
        .build()
}

internal fun KotlinProjectionComArgumentKind.descriptorAbiToken(): String? =
    when (this) {
        KotlinProjectionComArgumentKind.Pointer -> "RawAddress"
        KotlinProjectionComArgumentKind.Int8 -> "Byte"
        KotlinProjectionComArgumentKind.Int32 -> "Int32"
        KotlinProjectionComArgumentKind.Int64 -> "Int64"
        KotlinProjectionComArgumentKind.Float -> "Float"
        KotlinProjectionComArgumentKind.Double -> "Double"
        KotlinProjectionComArgumentKind.Int16 -> "Int16"
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
        if (useProjectionIntrinsics) {
            error(
                "No descriptor intrinsic or direct vtable invocation overload for ABI argument shape " +
                    abiArguments.joinToString(prefix = "[", postfix = "]") { argument ->
                        argument.kind?.name ?: "Unknown"
                    },
            )
        }
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
