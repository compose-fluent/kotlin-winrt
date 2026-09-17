package io.github.composefluent.winrt.projections.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import io.github.composefluent.winrt.metadata.WinRTIntegralType
import io.github.composefluent.winrt.metadata.WinRTMetadataParameterCategory
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class KotlinSharedCallSiteInputTest {
    private val support = KotlinModulePlatformAbiCallSupport(ClassName(RUNTIME, "GeneratedSharedInputCalls"))
    private val renderer = KotlinProjectionRenderer(modulePlatformAbiCalls = support)
    private val unit = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit")

    private fun objectType(name: String, nullable: Boolean = false) = KotlinProjectionAbiTypeBinding(
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass, "$RUNTIME.$name" + if (nullable) "?" else "",
    )

    private fun call(
        input: KotlinProjectionAbiTypeBinding,
        output: KotlinProjectionAbiTypeBinding = unit,
        category: WinRTMetadataParameterCategory = WinRTMetadataParameterCategory.In,
    ) = renderer.requireAbiCallPlan("shared.input", output,
        listOf(KotlinProjectionAbiParameterBinding("value", input, category = category)))

    @Test
    fun only_plain_runtime_class_inputs_lose_their_projection_identity() {
        // CsWinRT code_writers.h: object-reference marshaling is shared; interface selection
        // and output construction remain typed. Kotlin uses the existing IWinRTObject contract.
        val first = objectType("SharedInputOne")
        val second = objectType("SharedInputTwo")
        val one = renderer.composeTypedProjectionCallSite(call(first)).plan
        val two = renderer.composeTypedProjectionCallSite(call(second)).plan
        assertEquals(one, two)
        assertEquals(IWINRT_OBJECT_CLASS_NAME, one.parameters.single().type)
        assertEquals(WinRTProjectionCallSiteReferenceAccess.PROJECTED_OBJECT, one.descriptor.slots.single().recipe.referenceAccess)
        assertNotEquals(one.functionName,
            renderer.composeTypedProjectionCallSite(call(objectType("SharedInputOne", true))).plan.functionName)
        assertNotEquals(renderer.composeTypedProjectionCallSite(call(first, first)).plan.functionName,
            renderer.composeTypedProjectionCallSite(call(second, second)).plan.functionName)
        val iface = first.copy(kind = KotlinProjectionAbiValueKind.ProjectedInterface)
        assertNotEquals(IWINRT_OBJECT_CLASS_NAME, renderer.composeTypedProjectionCallSite(call(iface)).plan.parameters.single().type)
        val out = renderer.composeTypedProjectionCallSite(call(first, category = WinRTMetadataParameterCategory.Out)).plan
        assertTrue(out.parameters.single().type.toString().contains("SharedInputOne"))

        val original = call(objectType("CustomSharedInput"))
        val specialized = original.copy(parameterSlots = original.parameterSlots.map { slot ->
            slot.copy(recipePlan = slot.recipePlan.copy(inputCodec = KotlinProjectionCallSiteInputCodec(
                RAW_ADDRESS_CLASS_NAME, CodeBlock.of("return customAbi(__value)\n"),
            )))
        })
        assertNotEquals(IWINRT_OBJECT_CLASS_NAME,
            renderer.composeTypedProjectionCallSite(specialized).plan.parameters.single().type)
    }

    @Test
    fun generated_shared_inputs_preserve_null_disposal_and_enum_bits() {
        val wrappers = mutableListOf<String>()
        for (name in listOf("SharedInputOne", "SharedInputTwo")) {
            for (nullable in listOf(false, true)) {
                val input = objectType(name, nullable)
                val invocation = renderer.composeTypedProjectionCallSite(call(input))
                val expression = support.typedInvocation("receiver", CodeBlock.of("slot"), invocation)
                wrappers += "private fun use$name${if (nullable) "Nullable" else ""}(receiver: ComObjectReference, slot: Int, value: $name${if (nullable) "?" else ""}) = $expression"
            }
        }
        val enumPlans = listOf("SharedEnumOne", "SharedEnumTwo").map { name ->
            val input = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Enum, "$RUNTIME.$name",
                enumUnderlyingType = WinRTIntegralType.UInt32)
            val call = call(input)
            val invocation = renderer.composeTypedProjectionCallSite(call)
            // A cached canonical plan must still use this particular enum's conversion.
            val cached = renderer.composeTypedProjectionCallSite(call)
            assertEquals(invocation.arguments, cached.arguments)
            assertTrue(cached.arguments.single().toString().contains("$name.Metadata.toAbi(value)"))
            val expression = support.typedInvocation("receiver", CodeBlock.of("slot"), cached)
            wrappers += "private fun use$name(receiver: ComObjectReference, slot: Int, value: $name) = $expression"
            invocation.plan
        }
        assertEquals(enumPlans[0], enumPlans[1])
        assertEquals(3, support.observedTypedCallSitePlans().size)

        val output = System.getProperty("winrt.callsite.integration.output") ?: return
        support.renderFiles(KotlinProjectionGenerationLayout.SingleSourceSet).forEach { file ->
            File(output, file.relativePath).apply { parentFile.mkdirs(); writeText(file.contents) }
        }
        File(output, "GeneratedSharedInputIntegrationTest.kt").writeText("""
            package $RUNTIME
            import kotlin.test.*
            import kotlin.jvm.JvmInline
            private class SharedInputOne(reference: ComObjectReference) : WinRTObjectBase<ComObjectReference>(reference, null)
            private class SharedInputTwo(reference: ComObjectReference) : WinRTObjectBase<ComObjectReference>(reference, null)
            @JvmInline private value class SharedEnumOne(val raw: UInt) {
                companion object Metadata { fun toAbi(value: SharedEnumOne): UInt = value.raw }
            }
            @JvmInline private value class SharedEnumTwo(val raw: UInt) {
                companion object Metadata { fun toAbi(value: SharedEnumTwo): UInt = value.raw }
            }
            ${wrappers.joinToString("\n")}
            class GeneratedSharedInputIntegrationTest {
                @Test fun shared_inputs_preserve_native_reference_lifetimes_and_enum_bits() {
                    val iid = Guid("515724c1-22d3-43db-89fa-973c9351b717")
                    var received = PlatformAbi.nullPointer
                    var calls = 0
                    var bits = 0
                    var fail = false
                    val methods = listOf(
                        WinRTInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                            calls++; received = args[0] as RawAddress
                            if (fail) KnownHResults.E_FAIL.value else 0
                        },
                        WinRTInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Int32)) { args ->
                            bits = args[0] as Int; 0
                        },
                    )
                    WinRTInspectableComObject(listOf(WinRTInspectableInterfaceDefinition(iid, methods)), defaultInterfaceId = iid).use { host ->
                        host.createPrimaryReference().use { receiver ->
                            host.createPrimaryReference().use { input ->
                                val one = SharedInputOne(input)
                                val two = SharedInputTwo(input)
                                val expected = PlatformAbi.fromRawComPtr(input.pointer)
                                useSharedInputOne(receiver, 6, one); assertEquals(expected, received)
                                useSharedInputTwo(receiver, 6, two); assertEquals(expected, received)
                                useSharedInputOneNullable(receiver, 6, one); assertEquals(expected, received)
                                useSharedInputTwoNullable(receiver, 6, null); assertTrue(PlatformAbi.isNull(received))
                                fail = true
                                assertFailsWith<WinRTRuntimeException> { useSharedInputOne(receiver, 6, one) }
                                fail = false
                                useSharedInputTwo(receiver, 6, two); assertEquals(expected, received)
                                val before = calls
                                input.close()
                                assertFailsWith<WinRTObjectDisposedException> { useSharedInputOne(receiver, 6, one) }
                                assertEquals(before, calls)
                            }
                            useSharedEnumOne(receiver, 7, SharedEnumOne(UInt.MAX_VALUE)); assertEquals(-1, bits)
                            useSharedEnumTwo(receiver, 7, SharedEnumTwo(0x80000000u)); assertEquals(Int.MIN_VALUE, bits)
                        }
                    }
                }
            }
        """.trimIndent())
    }

    companion object { private const val RUNTIME = "io.github.composefluent.winrt.runtime" }
}
