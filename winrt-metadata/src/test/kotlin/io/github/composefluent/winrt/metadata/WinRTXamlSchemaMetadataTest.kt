package io.github.composefluent.winrt.metadata

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.Comparator

class WinRTXamlSchemaMetadataTest {
    @Test
    fun enum_schema_round_trips_as_an_enum_with_literal_values() {
        // .cswinrt/src/Authoring/WinRT.SourceGenerator/WinRTTypeWriter.cs:
        // AddComponentType(symbol) + VisitEnumDeclaration preserve System.Enum.
        val directory = Files.createTempDirectory("winrt-xaml-enum-")
        try {
            val output = directory.resolve("Schema.winmd")
            WinRTPortableExecutableMetadataWriter.writeXamlSchemaWinmd("Schema", listOf(
                WinRTXamlApplicationTypeDescriptor("Sample.Mode", enumEntries = listOf("First", "Second")),
            ), emptyMap(), output, emptyMap())
            val type = WinRTMetadataLoader.load(listOf(output)).namespaces.single().types.single()
            assertEquals(WinRTTypeKind.Enum, type.kind)
            assertEquals("System.Enum", type.baseTypeName)
            assertEquals(WinRTIntegralType.Int32, type.enumUnderlyingType)
            assertEquals(listOf("First", "Second"), type.enumMembers.map { it.name })
            assertEquals(listOf(0uL, 1uL), type.fields.filter { it.isLiteral }.map { it.constantValueBits })
            assertFalse(type.activation.isActivatable)
        } finally {
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }
    }
}
