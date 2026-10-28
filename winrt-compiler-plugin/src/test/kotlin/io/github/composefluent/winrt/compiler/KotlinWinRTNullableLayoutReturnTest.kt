package io.github.composefluent.winrt.compiler

import io.github.composefluent.winrt.compiler.authoring.KotlinWinRTAuthoredTypeCandidate
import io.github.composefluent.winrt.compiler.authoring.KotlinWinRTAuthoringTypeDetailsRenderer
import io.github.composefluent.winrt.metadata.*
import io.github.composefluent.winrt.runtime.Guid
import java.nio.file.Files
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class KotlinWinRTNullableLayoutReturnTest {
    @Test
    fun authored_layout_callback_preserves_nullable_transition_provider() {
        // WinUI Layout.h returns nullptr; CsWinRT MarshalInterface<T>.FromAbi
        // preserves it. The authored callback must agree with the base projection.
        val namespace = "Microsoft.UI.Xaml.Controls"
        val model = WinRTMetadataModel(listOf(WinRTNamespace(namespace, listOf(
            WinRTTypeDefinition(namespace, "Layout", WinRTTypeKind.RuntimeClass,
                defaultInterfaceName = "$namespace.ILayout"),
            WinRTTypeDefinition(namespace, "ILayout", WinRTTypeKind.Interface,
                iid = Guid("11111111-1111-1111-1111-111111111111")),
            WinRTTypeDefinition(namespace, "ItemCollectionTransitionProvider", WinRTTypeKind.RuntimeClass,
                defaultInterfaceName = "$namespace.IItemCollectionTransitionProvider"),
            WinRTTypeDefinition(namespace, "IItemCollectionTransitionProvider", WinRTTypeKind.Interface,
                iid = Guid("22222222-2222-2222-2222-222222222222")),
            WinRTTypeDefinition(namespace, "ILayoutOverrides", WinRTTypeKind.Interface,
                iid = Guid("441d00c3-dd50-5348-852d-85608cc7dce1"),
                methods = listOf(WinRTMethodDefinition("CreateDefaultItemTransitionProvider",
                    "$namespace.ItemCollectionTransitionProvider"))),
        )))).normalized()
        val output = Files.createTempDirectory("nullable-layout-return-")
        KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(
            candidates = listOf(KotlinWinRTAuthoredTypeCandidate(
                packageName = "sample", className = "LocalLayout", sourceTypeName = "sample.LocalLayout",
                winRTBaseClassName = "$namespace.Layout",
                winRTInterfaceNames = listOf("$namespace.ILayoutOverrides"),
                overridableInterfaceNames = listOf("$namespace.ILayoutOverrides"), isPublic = false,
            )),
            metadataModel = model, outputDirectory = output,
        )
        val source = output.resolve("sample/WinRT_LocalLayout_TypeDetails.kt").readText()
            .replace(Regex("\\s+"), " ")
        assertTrue(source, source.contains("ItemCollectionTransitionProvider? ="))
        assertTrue(source, source.contains("returnAbiType = \"$namespace.ItemCollectionTransitionProvider\""))
        assertTrue(source, source.contains("__winrtAuthoringInvokeCreateDefaultItemTransitionProvider()"))
    }
}
