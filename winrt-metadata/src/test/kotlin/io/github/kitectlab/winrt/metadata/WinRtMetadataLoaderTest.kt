package io.github.kitectlab.winrt.metadata

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class WinRtMetadataLoaderTest {
    @Test
    fun loads_namespace_and_type_kinds_from_real_cli_metadata() {
        val assembly = buildManagedMetadataSample()

        val model = WinRtMetadataLoader.load(assembly).normalized()

        assertEquals(listOf("Sample.Foundation"), model.namespaces.map { it.name })
        assertEquals(
            listOf("Color", "IWidget", "Point", "Widget", "WidgetHandler"),
            model.namespaces.single().types.map { it.name },
        )
        assertEquals(
            listOf(
                WinRtTypeKind.Enum,
                WinRtTypeKind.Interface,
                WinRtTypeKind.Struct,
                WinRtTypeKind.RuntimeClass,
                WinRtTypeKind.Delegate,
            ),
            model.namespaces.single().types.map { it.kind },
        )
    }

    @Test
    fun discovers_metadata_files_from_directory_inputs() {
        val assembly = buildManagedMetadataSample()

        val discovered = WinRtMetadataLoader.discoverMetadataFiles(listOf(assembly.parent))

        assertTrue(discovered.contains(assembly))
    }

    @Test
    fun canonicalizes_duplicate_path_forms_and_keeps_loader_output_stable() {
        val assembly = buildManagedMetadataSample()
        val duplicatePathForm = assembly.parent.resolve(".").resolve(assembly.fileName.toString())

        val discovered = WinRtMetadataLoader.discoverMetadataFiles(
            listOf(assembly, duplicatePathForm, assembly.parent),
        )
        val firstLoad = WinRtMetadataLoader.load(assembly, duplicatePathForm)
        val secondLoad = WinRtMetadataLoader.load(duplicatePathForm, assembly)

        assertEquals(1, discovered.count { it.fileName == assembly.fileName })
        assertEquals(firstLoad, secondLoad)
        assertEquals(listOf("Color", "IWidget", "Point", "Widget", "WidgetHandler"), firstLoad.namespaces.single().types.map { it.name })
    }

    private fun buildManagedMetadataSample(): Path {
        val dotnet = findDotnet()
        assumeTrue("dotnet CLI is required for Metadata 2.1 tests", dotnet != null)
        val tempDir = Files.createTempDirectory("kotlin-winrt-metadata-test")
        val projectDir = tempDir.resolve("SampleMetadata")
        projectDir.createDirectories()

        projectDir.resolve("SampleMetadata.csproj").writeText(
            """
            <Project Sdk="Microsoft.NET.Sdk">
              <PropertyGroup>
                <TargetFramework>net8.0</TargetFramework>
                <ImplicitUsings>disable</ImplicitUsings>
                <Nullable>disable</Nullable>
              </PropertyGroup>
            </Project>
            """.trimIndent(),
        )
        projectDir.resolve("MetadataTypes.cs").writeText(
            """
            namespace Sample.Foundation
            {
                public interface IWidget {}
                public class Widget {}
                public enum Color { Red, Blue }
                public struct Point { public int X; public int Y; }
                public delegate void WidgetHandler();
            }
            """.trimIndent(),
        )

        runProcess(
            workingDirectory = projectDir,
            command = listOf(dotnet!!.toString(), "build", "-nologo", "-clp:ErrorsOnly"),
        )
        return projectDir.resolve("bin/Debug/net8.0/SampleMetadata.dll")
    }

    private fun findDotnet(): Path? =
        runCatching {
            val locatorCommand = if (isWindows()) listOf("where", "dotnet") else listOf("which", "dotnet")
            val process = ProcessBuilder(locatorCommand)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText().trim() }
            val exitCode = process.waitFor()
            if (exitCode == 0 && output.isNotBlank()) {
                Path.of(output.lineSequence().first())
            } else {
                null
            }
        }.getOrNull()

    private fun isWindows(): Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

    private fun runProcess(workingDirectory: Path, command: List<String>) {
        assumeTrue("dotnet CLI is required for Metadata 2.1 tests", command.isNotEmpty())
        val process = ProcessBuilder(command)
            .directory(workingDirectory.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        check(exitCode == 0) {
            "Command failed (${command.joinToString(" ")})\n$output"
        }
    }
}