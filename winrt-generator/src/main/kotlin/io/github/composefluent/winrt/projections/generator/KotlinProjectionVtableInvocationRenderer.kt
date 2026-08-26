package io.github.composefluent.winrt.projections.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.UNIT

internal fun KotlinProjectionRenderer.renderInlineAbiInvocation(
    invokeTargetExpression: String,
    slotExpression: String,
    callPlan: KotlinProjectionAbiCallPlan,
): CodeBlock =
    renderInlineAbiInvocation(invokeTargetExpression, CodeBlock.of("%L", slotExpression), callPlan)

internal fun KotlinProjectionRenderer.renderInlineAbiInvocation(
    invokeTargetExpression: String,
    slotExpression: CodeBlock,
    callPlan: KotlinProjectionAbiCallPlan,
): CodeBlock {
    val support = modulePlatformAbiCalls ?: inlineOnlyModulePlatformAbiCallSupport()
    val invocation = composeTypedProjectionCallSite(callPlan, support)
    val expression = support.typedInvocation(
        referenceExpression = invokeTargetExpression,
        slotExpression = slotExpression,
        invocation = invocation,
    )
    return CodeBlock.builder()
        .apply {
            if (invocation.plan.returnType == UNIT) {
                add("%L\n", expression)
            } else {
                add("return(%L)\n", expression)
            }
        }
        .build()
}
