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
            support.observe(KotlinTypedProjectionCallSiteInvocation(plan, listOf(CodeBlock.of("first"), CodeBlock.of("second"))))
        }
        val text = support.renderFiles(KotlinProjectionGenerationLayout.SingleSourceSet).joinToString("\n") { it.contents }
        assertEquals(2, support.observedPlatformCallShapes().size)
        assertEquals(text, 0, Regex("fun abiCall_").findAll(text).count())
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
            val inline = support.inlineInvocation(unsigned.plan, listOf("receiver", "slot", "value").map { CodeBlock.of("%L", it) })
            assertFalse(inline.toString(), inline.toString().contains("fun __abi"))
            val files = support.renderFiles(KotlinProjectionGenerationLayout.SingleSourceSet)
            val text = files.joinToString("\n") { it.contents }
            assertEquals(signed.plan.platformShape, unsigned.plan.platformShape)
            assertNotEquals(signed.plan.platformShape, wide.plan.platformShape)
            assertEquals(text, 0, Regex("fun abiCall_").findAll(text).count())
            assertEquals(text, 2, Regex("Lowered while compiling the generated WinRT module").findAll(text).count())
            assertFalse(text, text.contains("fun __abi"))
            assertEquals(files, support.renderFiles(KotlinProjectionGenerationLayout.SingleSourceSet))
            assertTrue(inline.toString(), inline.toString().contains("winRTProjectionCallSiteArguments"))
            assertFalse(text, text.contains("sourceGenerated = true"))
        }
    }
}
