package io.github.composefluent.winrt.metadata

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class XamlCompilerWinmdIntegrationTest {
    @Test
    fun original_xamlc_loader_resolves_kotlin_authored_page() {
        // CsWinRT Authoring/WinRT.SourceGenerator/WinRTTypeWriter.cs emits a real metadata
        // graph. This gate uses Microsoft's LMR loader, not our own permissive round-trip reader.
        val compiler = System.getenv("WINRT_TEST_XAMLC")
        val referenceDirectories = System.getenv("WINRT_TEST_XAMLC_REFERENCES")
        assumeTrue("Set WINRT_TEST_XAMLC and WINRT_TEST_XAMLC_REFERENCES to run the Windows integration gate.",
            !compiler.isNullOrBlank() && !referenceDirectories.isNullOrBlank())
        val root = Path.of("build", "xaml-winmd-integration").toAbsolutePath()
        Files.createDirectories(root)
        val winmd = root.resolve("probe.winmd")
        val directories = referenceDirectories!!.split(File.pathSeparator).map(Path::of)
        WinRTPortableExecutableMetadataWriter.writeAuthoredWinmd("probe", listOf(
            WinRTAuthoredRuntimeClassDescriptor(
                runtimeClassName = "probe.MainPage", baseRuntimeClassName = "Microsoft.UI.Xaml.Controls.Page",
                interfaceNames = listOf("Microsoft.UI.Xaml.Markup.IComponentConnector"), isActivatable = false,
            ),
        ), winmd, externalTypeAssemblies = WinRTMetadataLoader.loadTypeAssemblyNames(directories))
        val xaml = root.resolve("MainPage.xaml")
        Files.writeString(xaml, """
            <Page xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
                  xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" x:Class="probe.MainPage">
              <Button x:Name="myButton" Content="Click" Click="onClick" />
            </Page>
        """.trimIndent())
        val references = directories.flatMap { directory -> Files.list(directory).use { stream ->
            stream.filter { it.toString().endsWith(".winmd") }.sorted().toList()
        } }
        fun item(path: Path, system: Boolean = false) = buildJsonObject {
            put("ItemSpec", path.toString()); put("FullPath", path.toString()); put("IsSystemReference", system)
        }
        val input = buildJsonObject {
            put("ProjectPath", root.resolve("probe.proj").toString()); put("ProjectName", "probe")
            // Reuse the unmodified final-pass loader before implementing the Kotlin backend.
            put("Language", "CppWinRT"); put("LanguageSourceExtension", ".cpp")
            put("OutputPath", root.resolve("generated").toString()); put("IsPass1", false)
            put("RootNamespace", "probe"); put("OutputType", "WinExe")
            put("TargetPlatformMinVersion", "10.0.19041.0"); put("DisableXbfGeneration", true)
            put("SavedStateFile", root.resolve("state.xml").toString())
            put("ReferenceAssemblies", JsonArray(references.map { item(it, true) }))
            put("ReferenceAssemblyPaths", JsonArray((directories + listOf(Path.of(System.getenv("WINDIR"), "Microsoft.NET", "Framework64", "v4.0.30319"))).map { item(it) }))
            put("XamlPages", JsonArray(listOf(item(xaml)))); put("LocalAssembly", JsonArray(listOf(item(winmd))))
        }
        val inputFile = root.resolve("input.json").also { Files.writeString(it, input.toString()) }
        val outputFile = root.resolve("output.json")
        val console = root.resolve("console.txt")
        val process = ProcessBuilder(compiler!!, inputFile.toString(), outputFile.toString())
            .redirectErrorStream(true).redirectOutput(console.toFile()).start()
        if (!process.waitFor(45, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            fail("XamlCompiler timed out; see $console")
        }
        val output = Json.parseToJsonElement(Files.readString(outputFile)).jsonObject
        val errors = output.getValue("MSBuildLogEntries").jsonArray.filter { it.jsonObject["Type"]?.jsonPrimitive?.intOrNull == 2 }
        assertEquals("XamlCompiler rejected authored WinMD: $errors; output: $outputFile", 0, process.exitValue())
        assertTrue("No final-pass code generated", output.getValue("GeneratedCodeFiles").jsonArray.isNotEmpty())
    }
}
