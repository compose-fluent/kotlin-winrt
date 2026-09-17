package io.github.composefluent.winrt.projections.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.UNIT
import io.github.composefluent.winrt.metadata.*
import io.github.composefluent.winrt.runtime.Guid
import org.junit.Assert.*
import org.junit.Test

class KotlinSupportNodeSharingTest {
    @Test
    fun closed_iterator_reuses_its_element_adapter_in_both_directions_and_wrap() {
        val model = WinRTMetadataModel(namespaces = listOf(
            WinRTNamespace("Sample", types = listOf(WinRTTypeDefinition(
                namespace = "Sample", name = "IProvider", kind = WinRTTypeKind.Interface,
                iid = Guid("11111111-1111-1111-1111-111111111111"),
                methods = listOf(WinRTMethodDefinition(name = "getNames",
                    returnTypeName = "Windows.Foundation.Collections.IIterator<Sample.Element>", methodRowId = 10)),
            ), WinRTTypeDefinition(namespace = "Sample", name = "IElement", kind = WinRTTypeKind.Interface,
                iid = Guid("22222222-2222-2222-2222-222222222222")),
                WinRTTypeDefinition(namespace = "Sample", name = "Element", kind = WinRTTypeKind.RuntimeClass,
                    defaultInterfaceName = "Sample.IElement"))),
            WinRTNamespace("Windows.Foundation.Collections", types = listOf(WinRTTypeDefinition(
                namespace = "Windows.Foundation.Collections", name = "IIterator", kind = WinRTTypeKind.Interface,
                iid = Guid("6A79E863-4300-459A-9966-CBB660963EE1"),
                genericParameters = listOf(WinRTGenericParameterDefinition("T0", 0)),
            ))),
        ))
        val files = KotlinProjectionGenerator(emitSupportFiles = true).generate(model)
        val source = files.filter { it.relativePath.contains("/support/") }
            .joinToString("\n") { it.contents }
        assertEquals(1, Regex("Element.Metadata.wrap\\(").findAll(source).count())
        val helper = files.filter { it.relativePath.contains("WinRTClosedGenericProjectionHelper_") }
            .joinToString("\n") { it.contents }
        assertFalse(helper.contains("WinRTReferenceValueAdapters.runtimeClass"))
        val references = Regex("metadata_[0-9a-f]{16}").findAll(helper).map { it.value }.toList()
        assertEquals(3, references.size)
        assertEquals(1, references.distinct().size)
    }

    @Test
    fun identical_disposal_bodies_share_across_shards_without_erasing_type_bindings() {
        // CsWinRT Marshalers.cs: element disposal and buffer freeing define the cleanup contract.
        fun render(reverse: Boolean): String {
            val support = KotlinModulePlatformAbiCallSupport(ClassName("sample", "Support"), abiSupportShardCount = 4)
            val entries = listOf("sample.A", "sample.B", "sample.C")
            (if (reverse) entries.reversed() else entries).forEach { type ->
                support.registerCodec(
                    operation = "disposeAbiArray", role = KotlinProjectionAbiCodecRole.DISPOSE_ABI,
                    abiTypeName = type, signature = type,
                    parameters = listOf("length", "data").map { KotlinProjectionCallSiteCodecParameter(it, RAW_ADDRESS_CLASS_NAME) },
                    returnType = UNIT,
                    body = CodeBlock.of(if (type == "sample.C") "releaseStrings(length, data)\n" else "releaseReferences(length, data)\n"),
                )
            }
            return support.renderFiles(KotlinProjectionGenerationLayout.SingleSourceSet)
                .sortedBy { it.relativePath }.joinToString("\n") { it.contents }
        }
        val source = render(false)
        assertEquals(source, render(true))
        assertEquals(1, Regex("releaseReferences\\(").findAll(source).count())
        assertEquals(1, Regex("releaseStrings\\(").findAll(source).count())
        assertEquals(3, Regex("role = WinRTProjectionAbiCodecRole.DISPOSE_ABI").findAll(source).count())
    }
}
