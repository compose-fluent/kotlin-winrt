package io.github.kitectlab.winrt.metadata

import org.junit.Assert.assertEquals
import org.junit.Test

class WinRtMetadataModelTest {
    @Test
    fun preserves_declared_type_order() {
        val namespace = WinRtNamespace(
            name = "Windows.Foundation",
            types = listOf(
                WinRtTypeDefinition(namespace = "Windows.Foundation", name = "IStringable"),
                WinRtTypeDefinition(namespace = "Windows.Foundation", name = "Uri"),
            ),
        )

        assertEquals(listOf("IStringable", "Uri"), namespace.types.map { it.name })
    }

    @Test
    fun normalizes_namespace_and_type_order_deterministically() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Data.Json",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Windows.Data.Json", name = "JsonValue"),
                        WinRtTypeDefinition(namespace = "Windows.Data.Json", name = "JsonArray"),
                    ),
                ),
                WinRtNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Windows.Foundation", name = "Uri"),
                        WinRtTypeDefinition(namespace = "Windows.Foundation", name = "IStringable"),
                    ),
                ),
            ),
        )

        val normalized = model.normalized()

        assertEquals(listOf("Windows.Data.Json", "Windows.Foundation"), normalized.namespaces.map { it.name })
        assertEquals(listOf("JsonArray", "JsonValue"), normalized.namespaces[0].types.map { it.name })
        assertEquals(listOf("IStringable", "Uri"), normalized.namespaces[1].types.map { it.name })
    }
}
