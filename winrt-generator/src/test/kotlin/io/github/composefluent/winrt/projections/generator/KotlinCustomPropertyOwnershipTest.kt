package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTMethodDefinition
import io.github.composefluent.winrt.metadata.WinRTNamespace
import io.github.composefluent.winrt.metadata.WinRTTypeDefinition
import io.github.composefluent.winrt.metadata.WinRTTypeKind
import io.github.composefluent.winrt.runtime.Guid
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KotlinCustomPropertyOwnershipTest {
    @Test
    fun custom_property_interfaces_keep_runtime_ownership_and_use_runtime_marshaling() {
        // CsWinRT 3: WindowsRuntimeComWrappersMarshallerAttribute fundamental CCW
        // interfaces and CustomPropertyProviderGenerator.Emit's binding contract.
        // Kotlin already owns the shared interfaces/factories in winrt-runtime.
        for (namespace in listOf("Microsoft.UI.Xaml.Data", "Windows.UI.Xaml.Data")) {
            val model = WinRTMetadataModel(namespaces = listOf(
                WinRTNamespace(namespace, listOf(
                    WinRTTypeDefinition(namespace, "ICustomProperty", WinRTTypeKind.Interface,
                        iid = Guid("30DA92C0-23E8-42A0-AE7C-734A0E5D2782")),
                    WinRTTypeDefinition(namespace, "ICustomPropertyProvider", WinRTTypeKind.Interface,
                        iid = Guid("7C925755-3E48-42B4-8677-76372267033F")),
                )),
                WinRTNamespace("Sample.Binding", listOf(
                    WinRTTypeDefinition("Sample.Binding", "IBindingSource", WinRTTypeKind.Interface,
                        iid = Guid("11111111-2222-3333-4444-555555555563"),
                        methods = listOf(
                            WinRTMethodDefinition(name = "GetProperty", returnTypeName = "$namespace.ICustomProperty", methodRowId = 1),
                            WinRTMethodDefinition(name = "GetProvider", returnTypeName = "$namespace.ICustomPropertyProvider", methodRowId = 2),
                        )),
                )),
            ))
            val files = KotlinProjectionGenerator(emitSupportFiles = true).generate(model)
            val source = files.joinToString("\n") { it.contents }
            assertFalse(namespace, files.any { it.relativePath.substringAfterLast('/') in
                setOf("ICustomProperty.kt", "ICustomPropertyProvider.kt") })
            assertTrue(source, source.contains("WinRTSystemProjectionMarshalers.objectFromAbi"))
            assertFalse(source, source.contains("ICustomProperty.Metadata.wrap"))
            assertFalse(source, source.contains("ICustomPropertyProvider.Metadata.wrap"))
        }
    }
}
