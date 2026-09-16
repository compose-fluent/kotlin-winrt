package io.github.composefluent.winrt.projections.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import org.junit.Assert.*
import org.junit.Test

class KotlinSharedAbiCallSourceTest {
    @Test
    fun standalone_renderer_does_not_reference_an_unpublished_module_entry() {
        val support = inlineOnlyModulePlatformAbiCallSupport()
        val renderer = KotlinProjectionRenderer(modulePlatformAbiCalls = support)
        val invocation = renderer.composeTypedProjectionCallSite(renderer.requireAbiCallPlan(
            bindingName = "sample.Standalone",
            returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
            parameterBindings = listOf(KotlinProjectionAbiParameterBinding("value", KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Double, "Double"))),
        ))
        val text = support.typedInvocation("receiver", CodeBlock.of("slot"), invocation).toString()
        assertTrue(text, text.contains("winRTProjectionCallSiteArguments"))
        assertFalse(text, text.contains("abiCall_"))
        assertTrue(support.renderFiles(KotlinProjectionGenerationLayout.SingleSourceSet).isEmpty())
    }

    @Test
    fun physical_key_retains_argument_order() {
        val support = KotlinModulePlatformAbiCallSupport(ClassName("sample", "OrderedAbi"))
        val renderer = KotlinProjectionRenderer(modulePlatformAbiCalls = support)
        val int = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Int32, "Int")
        val double = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Double, "Double")
        for (types in listOf(listOf(int, double), listOf(double, int))) {
            val plan = renderer.composeTypedProjectionCallSite(renderer.requireAbiCallPlan(
                bindingName = "sample.Order",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                parameterBindings = types.mapIndexed { index, type -> KotlinProjectionAbiParameterBinding("p$index", type) },
            )).plan
            assertNotNull(support.sourceBody(plan, listOf("receiver", "slot", "first", "second").map { CodeBlock.of("%L", it) }))
        }
        val text = support.renderFiles(KotlinProjectionGenerationLayout.SingleSourceSet).joinToString("\n") { it.contents }
        assertEquals(text, 2, Regex("fun abiCall_").findAll(text).count())
    }

    @Test
    fun typed_and_inline_bodies_share_physical_calls_across_shards() {
        // cswinrt code_writers.h: typed marshaling surrounds a physical ABI invocation.
        for (shards in listOf(1, 32)) {
            val support = KotlinModulePlatformAbiCallSupport(ClassName("sample", "SharedAbi"), abiSupportShardCount = shards)
            val renderer = KotlinProjectionRenderer(modulePlatformAbiCalls = support)
            fun invocation(kind: KotlinProjectionAbiValueKind, type: String) =
                renderer.composeTypedProjectionCallSite(renderer.requireAbiCallPlan(
                    bindingName = "sample.$type",
                    returnBinding = KotlinProjectionAbiTypeBinding(kind, type),
                    parameterBindings = listOf(KotlinProjectionAbiParameterBinding("value", KotlinProjectionAbiTypeBinding(kind, type))),
                ))
            val signed = invocation(KotlinProjectionAbiValueKind.Int32, "Int")
            val unsigned = invocation(KotlinProjectionAbiValueKind.UInt32, "UInt")
            val wide = invocation(KotlinProjectionAbiValueKind.Int64, "Long")
            support.observe(signed)
            support.observe(wide)
            val inline = support.sourceBody(unsigned.plan, listOf("receiver", "slot", "value").map { CodeBlock.of("%L", it) })!!
            assertFalse(inline.toString(), inline.toString().contains("fun __abi"))
            val files = support.renderFiles(KotlinProjectionGenerationLayout.SingleSourceSet)
            val text = files.joinToString("\n") { it.contents }
            assertEquals(text, 2, Regex("fun abiCall_").findAll(text).count())
            assertEquals(text, 2, Regex("Fixed WinRT ABI call").findAll(text).count())
            assertFalse(text, text.contains("fun __abi"))
            assertEquals(files, support.renderFiles(KotlinProjectionGenerationLayout.SingleSourceSet))
            // The one-off unsigned body resolves to the same declaration as the signed body.
            val rawName = Regex("abiCall_[0-9a-f]+").find(inline.toString())!!.value
            assertEquals(text, 2, Regex(rawName).findAll(text).count())
        }
    }
}
