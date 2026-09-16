package io.github.composefluent.winrt.projections.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class KotlinHStringCallSiteSourceTest {
    @Test
    fun hstring_input_and_return_use_generated_marshaling() {
        // cswinrt code_writers.h abi_marshaler and WinRT.Runtime/Marshalers.cs MarshalString:
        // borrowed input references, copied managed results, unconditional owned ABI cleanup.
        val support = KotlinModulePlatformAbiCallSupport(ClassName("io.github.composefluent.winrt.runtime", "StringAbiSupport"))
        val renderer = KotlinProjectionRenderer(modulePlatformAbiCalls = support)
        val string = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.String, "String")
        val nullableString = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.String, "String?")
        val plan = renderer.composeTypedProjectionCallSite(renderer.requireAbiCallPlan(
            bindingName = "sample.StringRoundTrip",
            returnBinding = string,
            parameterBindings = listOf(
                KotlinProjectionAbiParameterBinding("first", nullableString),
                KotlinProjectionAbiParameterBinding("tag", KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Int32, "Int")),
                KotlinProjectionAbiParameterBinding("second", string),
            ),
        )).plan
        val body = support.sourceBody(plan, listOf("receiver", "slot", "first", "tag", "second").map { CodeBlock.of("%L", it) })
        assertNotNull(body)
        val source = body.toString()
        assertEquals(source, 2, Regex("withWinRTHStringReference").findAll(source).count())
        assertTrue(source, source.contains("withWinRTOwnedHStringResult"))
        assertTrue(source, source.indexOf("requireSuccess") < source.indexOf("fromAbi"))
        assertFalse(source, source.contains("TODO("))
        val output = System.getProperty("winrt.callsite.integration.output") ?: return
        support.renderFiles(KotlinProjectionGenerationLayout.SingleSourceSet).forEach { file ->
            File(output, file.relativePath).apply { parentFile.mkdirs(); writeText(file.contents) }
        }
        File(output, "GeneratedHStringSourceIntegrationTest.kt").apply {
            parentFile.mkdirs()
            writeText("""
                package io.github.composefluent.winrt.runtime
                import kotlin.test.*
                private fun generatedStrings(receiver: ComObjectReference, slot: Int, first: String?, tag: Int, second: String): String = $source
                class GeneratedHStringSourceIntegrationTest {
                    @Test fun generated_strings_preserve_inputs_and_release_outputs_on_all_paths() {
                        val iid = Guid("7954c537-adab-4476-b3ef-8c611a30223c")
                        var failureMode = 0
                        var nested = false
                        var reenter: (() -> Unit)? = null
                        val method = WinRTInspectableMethodDefinition(
                            ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Int32, ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                            handler = { args ->
                                val first = NativeStringMarshaller.fromAbi(args[0] as RawAddress)
                                val second = NativeStringMarshaller.fromAbi(args[2] as RawAddress)
                                val output = args[3] as RawAddress
                                assertEquals(42, args[1])
                                assertTrue(PlatformAbi.isNull(PlatformAbi.readPointer(output)))
                                if (!nested) {
                                    nested = true
                                    try { reenter?.invoke() } finally { nested = false }
                                    assertEquals(first, NativeStringMarshaller.fromAbi(args[0] as RawAddress))
                                    assertEquals(second, NativeStringMarshaller.fromAbi(args[2] as RawAddress))
                                }
                                if (failureMode == 1) KnownHResults.E_FAIL.value else {
                                    // Transfer this owned HSTRING to the ABI caller, including the failure path.
                                    val owned = HString.create(first + second)
                                    PlatformAbi.writePointer(output, owned.handle)
                                    if (failureMode == 2) KnownHResults.E_FAIL.value else 0
                                }
                            },
                        )
                        WinRTInspectableComObject(listOf(WinRTInspectableInterfaceDefinition(iid, listOf(method))), defaultInterfaceId = iid).use { host ->
                            host.createPrimaryReference().use { receiver ->
                                reenter = { assertEquals("innercopy", generatedStrings(receiver, 6, "inner", 42, "copy")) }
                                assertEquals("outerstay", generatedStrings(receiver, 6, "outer", 42, "stay"))
                                reenter = null
                                assertEquals("", generatedStrings(receiver, 6, "", 42, ""))
                                assertEquals("tail", generatedStrings(receiver, 6, null, 42, "tail"))
                                assertEquals("中\u0000文🙂", generatedStrings(receiver, 6, "中\u0000", 42, "文🙂"))
                                repeat(20) {
                                    for (mode in 1..2) {
                                        failureMode = mode
                                        assertFailsWith<WinRTRuntimeException> { generatedStrings(receiver, 6, "failure", 42, "owned") }
                                    }
                                    failureMode = 0
                                    assertEquals("recovered", generatedStrings(receiver, 6, "re", 42, "covered"))
                                }
                            }
                        }
                    }
                }
            """.trimIndent())
        }
    }
}
