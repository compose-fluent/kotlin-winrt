package io.github.composefluent.winrt.projections.generator

import com.squareup.kotlinpoet.CodeBlock
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KotlinGuidCallSiteSourceTest {
    @Test
    fun guid_input_and_return_use_generated_marshaling() {
        // cswinrt code_writers.h abi_marshaler / write_abi_method_call_marshalers:
        // a blittable GUID has value semantics and no owned reference cleanup.
        val guid = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.GuidValue, "Guid")
        val scalar = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Int32, "Int")
        val renderer = KotlinProjectionRenderer(modulePlatformAbiCalls = KotlinModulePlatformAbiCallSupport(
            com.squareup.kotlinpoet.ClassName("sample", "GuidAbiSupport"),
        ))
        val plan = renderer.composeTypedProjectionCallSite(renderer.requireAbiCallPlan(
            bindingName = "sample.GuidRoundTrip",
            returnBinding = guid,
            parameterBindings = listOf(
                KotlinProjectionAbiParameterBinding("first", guid),
                KotlinProjectionAbiParameterBinding("tag", scalar),
                KotlinProjectionAbiParameterBinding("second", guid),
            ),
        )).plan
        val body = plan.scalarSourceBody(listOf("receiver", "slot", "first", "tag", "second").map { CodeBlock.of("%L", it) })
        assertNotNull(body)
        val source = body.toString()
        assertTrue(source, source.contains("writeGuid"))
        assertTrue(source, source.indexOf("requireSuccess") < source.indexOf("readGuid"))
        val output = System.getProperty("winrt.callsite.integration.output") ?: return
        File(output, "GeneratedGuidSourceIntegrationTest.kt").apply {
            parentFile.mkdirs()
            writeText("""
                package io.github.composefluent.winrt.runtime
                import kotlin.test.*
                private fun generatedGuid(receiver: ComObjectReference, slot: Int, first: Guid, tag: Int, second: Guid): Guid = $source
                class GeneratedGuidSourceIntegrationTest {
                    @Test fun mixed_guid_inputs_and_return_survive_failure_and_reentry() {
                        val iid = Guid("fc05fd27-44c9-4fdd-92fd-8b8737eb58f4")
                        val first = Guid("01234567-89ab-cdef-0123-456789abcdef")
                        val second = Guid("fedcba98-7654-3210-fedc-ba9876543210")
                        var fail = false
                        var nested = false
                        var reenter: (() -> Unit)? = null
                        val method = WinRTInspectableMethodDefinition(
                            ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Int32, ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                            handler = { args ->
                                assertEquals(first, PlatformAbi.readGuid(args[0] as RawAddress))
                                assertEquals(42, args[1])
                                assertEquals(second, PlatformAbi.readGuid(args[2] as RawAddress))
                                assertEquals(Guid("00000000-0000-0000-0000-000000000000"), PlatformAbi.readGuid(args[3] as RawAddress))
                                if (!nested) {
                                    nested = true
                                    try { reenter?.invoke() } finally { nested = false }
                                    assertEquals(first, PlatformAbi.readGuid(args[0] as RawAddress))
                                }
                                PlatformAbi.writeGuid(args[3] as RawAddress, second)
                                if (fail) KnownHResults.E_FAIL.value else 0
                            },
                        )
                        WinRTInspectableComObject(listOf(WinRTInspectableInterfaceDefinition(iid, listOf(method))), defaultInterfaceId = iid).use { host ->
                            host.createPrimaryReference().use { receiver ->
                                reenter = { assertEquals(second, generatedGuid(receiver, 6, first, 42, second)) }
                                assertEquals(second, generatedGuid(receiver, 6, first, 42, second))
                                reenter = null
                                fail = true
                                assertFailsWith<WinRTRuntimeException> { generatedGuid(receiver, 6, first, 42, second) }
                                fail = false
                                assertEquals(second, generatedGuid(receiver, 6, first, 42, second))
                            }
                        }
                    }
                }
            """.trimIndent())
        }
    }
}
