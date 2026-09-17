package io.github.composefluent.winrt.projections.generator

import com.squareup.kotlinpoet.CodeBlock
import java.io.File
import io.github.composefluent.winrt.metadata.WinRTIntegralType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KotlinScalarCallSiteSourceTest {
    @Test
    fun module_scalar_body_keeps_the_run_lambda_attached_after_a_long_signature() {
        val support = KotlinModulePlatformAbiCallSupport(
            com.squareup.kotlinpoet.ClassName("sample", "ModulePlatformAbi"),
        )
        val renderer = KotlinProjectionRenderer(modulePlatformAbiCalls = support)
        val invocation = renderer.composeTypedProjectionCallSite(renderer.requireAbiCallPlan(
            bindingName = "sample.Close",
            returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
            parameterBindings = emptyList(),
        ))
        support.observe(invocation)
        val text = support.renderFiles(KotlinProjectionGenerationLayout.SingleSourceSet)
            .joinToString("\n") { it.contents }
        assertTrue(text, text.contains("kotlin.run {"))
        assertFalse(text, Regex("kotlin\\.run\\s*\\n\\s*\\{").containsMatchIn(text))
    }

    @Test
    fun scalar_conversions_and_storage_are_emitted_as_source() {
        // code_writers.h write_fundamental_marshal_to_abi/from_abi and abi_marshaler.
        val cases = listOf(
            Case(KotlinProjectionAbiValueKind.Boolean, "Boolean", "Int8", "1.toByte()", "true"),
            Case(KotlinProjectionAbiValueKind.Int8, "Byte", "Int8", "(-7).toByte()", "(-7).toByte()"),
            Case(KotlinProjectionAbiValueKind.UInt8, "UByte", "Int8", "(-1).toByte()", "255.toUByte()"),
            Case(KotlinProjectionAbiValueKind.Int16, "Short", "Int16", "(-1234).toShort()", "(-1234).toShort()"),
            Case(KotlinProjectionAbiValueKind.UInt16, "UShort", "Int16", "(-1).toShort()", "65535.toUShort()"),
            Case(KotlinProjectionAbiValueKind.Char16, "Char", "Int16", "0xf123.toShort()", "'\\uf123'"),
            Case(KotlinProjectionAbiValueKind.Int32, "Int", "Int32", "-123456", "-123456"),
            Case(KotlinProjectionAbiValueKind.UInt32, "UInt", "Int32", "-1", "UInt.MAX_VALUE"),
            Case(KotlinProjectionAbiValueKind.Int64, "Long", "Int64", "-1234567890123L", "-1234567890123L"),
            Case(KotlinProjectionAbiValueKind.UInt64, "ULong", "Int64", "-1L", "ULong.MAX_VALUE"),
            Case(KotlinProjectionAbiValueKind.Float, "Float", "Float", "123.25f", "123.25f"),
            Case(KotlinProjectionAbiValueKind.Double, "Double", "Double", "456.125", "456.125"),
            Case(KotlinProjectionAbiValueKind.Enum, "GeneratedSourceEnum", "Int32", "-1", "GeneratedSourceEnum(UInt.MAX_VALUE)"),
        )
        val support = KotlinModulePlatformAbiCallSupport(
            com.squareup.kotlinpoet.ClassName("io.github.composefluent.winrt.runtime", "ScalarAbiSupport"),
        )
        val renderer = KotlinProjectionRenderer(modulePlatformAbiCalls = support)
        val functions = cases.map { case ->
            val binding = KotlinProjectionAbiTypeBinding(
                case.kind, case.type,
                resolvedTypeName = if (case.kind == KotlinProjectionAbiValueKind.Enum)
                    "io.github.composefluent.winrt.runtime.GeneratedSourceEnum" else case.type,
                enumUnderlyingType = if (case.kind == KotlinProjectionAbiValueKind.Enum) WinRTIntegralType.UInt32 else null,
            )
            val invocation = renderer.composeTypedProjectionCallSite(renderer.requireAbiCallPlan(
                bindingName = "sample.read${case.type}",
                returnBinding = binding,
                parameterBindings = listOf(KotlinProjectionAbiParameterBinding("value", binding)),
            ))
            val source = support.sourceBody(invocation.plan,
                listOf(CodeBlock.of("instance"), CodeBlock.of("slot")) + invocation.arguments)
            assertNotNull(case.type, source)
            val text = source.toString()
            assertTrue(text, text.contains("abiCall_"))
            assertTrue(text, text.contains("withWinRTScalarResult"))
            assertTrue(text, text.contains("requireSuccess()"))
            assertFalse(text, text.contains("Lowered while compiling the generated WinRT module"))
            "private fun generated${case.type}(instance: ComObjectReference, slot: Int, value: ${case.type}): ${case.type} = $text"
        }
        // Export the actual generated source for Windows JVM/Native integration validation.
        // This is a build artifact, never a hand-maintained projection fixture.
        val outputDirectory = System.getProperty("winrt.callsite.integration.output") ?: return
        support.renderFiles(KotlinProjectionGenerationLayout.SingleSourceSet).forEach { file ->
            File(outputDirectory, file.relativePath).apply { parentFile.mkdirs(); writeText(file.contents) }
        }
        File(outputDirectory, "GeneratedScalarSourceIntegrationTest.kt").apply {
            parentFile.mkdirs()
            writeText(buildString {
                appendLine("package io.github.composefluent.winrt.runtime")
                appendLine("import kotlin.test.*")
                appendLine("private data class GeneratedSourceEnum(val abiValue: UInt) { companion object Metadata {")
                appendLine("fun toAbi(value: GeneratedSourceEnum): UInt = value.abiValue")
                appendLine("fun fromAbi(value: UInt): GeneratedSourceEnum = GeneratedSourceEnum(value)")
                appendLine("} }")
                functions.forEach(::appendLine)
                appendLine("@WinRTProjectionCallSite private fun recipeInt(instance: ComObjectReference, slot: Int, value: Int): Int = TODO(\"comparison fixture\")")
                appendLine("@WinRTProjectionCallSite private fun recipeDouble(instance: ComObjectReference, slot: Int, value: Double): Double = TODO(\"comparison fixture\")")
                appendLine("class GeneratedScalarSourceIntegrationTest {")
                appendLine("@Test fun generated_scalar_getters_round_trip_through_abi() {")
                appendLine("val iid = Guid(\"fc05fd27-44c9-4fdd-92fd-8b8737eb58f4\")")
                appendLine("var fail = false")
                appendLine("val methods = listOf(")
                cases.forEach { case ->
                    appendLine("WinRTInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.${case.writer}, ComAbiValueKind.Pointer), handler = { args ->")
                    appendLine("assertEquals(${case.abi}, args[0])")
                    appendLine("PlatformAbi.write${case.writer}(args[1] as RawAddress, ${case.abi})")
                    appendLine("if (fail) KnownHResults.E_FAIL.value else 0 }),")
                }
                appendLine(")")
                appendLine("WinRTInspectableComObject(listOf(WinRTInspectableInterfaceDefinition(iid, methods)), defaultInterfaceId = iid).use { host ->")
                appendLine("host.createPrimaryReference().use { receiver ->")
                cases.forEachIndexed { index, case ->
                    appendLine("assertEquals(${case.expected}, generated${case.type}(receiver, ${index + 6}, ${case.expected}))")
                }
                appendLine("fail = true")
                appendLine("assertFailsWith<WinRTRuntimeException> { generatedInt(receiver, 12, -123456) }")
                appendLine("fail = false")
                appendLine("assertEquals(-123456, generatedInt(receiver, 12, -123456))")
                appendLine("assertEquals(-123456, recipeInt(receiver, 12, -123456))")
                appendLine("assertEquals(456.125, recipeDouble(receiver, 17, 456.125))")
                if (System.getProperty("winrt.callsite.benchmark") == "true") {
                    // Opt-in paired measurement; never assert a timing threshold in CI.
                    appendLine("fun sample(source: Boolean, wide: Boolean): Long {")
                    appendLine("var sum = 0.0")
                    appendLine("val elapsed = kotlin.time.measureTime { repeat(20000) {")
                    appendLine("sum += if (wide) { if (source) generatedDouble(receiver, 17, 456.125) else recipeDouble(receiver, 17, 456.125) }")
                    appendLine("else { (if (source) generatedInt(receiver, 12, -123456) else recipeInt(receiver, 12, -123456)).toDouble() }")
                    appendLine("} }")
                    appendLine("assertEquals((if (wide) 456.125 else -123456.0) * 20000, sum)")
                    appendLine("return elapsed.inWholeNanoseconds }")
                    appendLine("for (wide in listOf(false, true)) {")
                    appendLine("repeat(3) { sample(true, wide); sample(false, wide) }")
                    appendLine("repeat(9) { round ->")
                    appendLine("val first = sample(round % 2 == 0, wide); val second = sample(round % 2 != 0, wide)")
                    appendLine("println(\"CALLSITE_PAIR wide=\" + wide + \" sourceNs=\" + (if (round % 2 == 0) first else second) + \" recipeNs=\" + (if (round % 2 == 0) second else first))")
                    appendLine("} }")
                }
                appendLine("} } } }")
            })
        }
    }

    private data class Case(val kind: KotlinProjectionAbiValueKind, val type: String, val writer: String, val abi: String, val expected: String)
}
