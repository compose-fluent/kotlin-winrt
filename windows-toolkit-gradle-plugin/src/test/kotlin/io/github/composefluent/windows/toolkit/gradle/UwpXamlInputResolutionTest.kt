package io.github.composefluent.windows.toolkit.gradle

import io.github.composefluent.winrt.metadata.WinRTAuthoredRuntimeClassDescriptor
import io.github.composefluent.winrt.metadata.WinRTPortableExecutableMetadataWriter
import io.github.composefluent.winrt.runtime.XamlFramework
import io.github.composefluent.winrt.runtime.WinUiRuntimeAssetManifests
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

class UwpXamlInputResolutionTest {
    @Test
    fun programmatic_application_discovers_providers_from_library_and_compiler_references() {
        // CsWinRT consumes resolved dependency references independently of whether the
        // entry module owns markup. WinUI 2's provider lives in lib/uap10.0, not payload.
        val root = Files.createTempDirectory("uwp-library-providers-")
        val project = ProjectBuilder.builder().withProjectDir(root.toFile()).build()
        project.pluginManager.apply(KotlinWindowsToolkitPlugin::class.java)
        val windows = project.extensions.getByType(WindowsExtension::class.java)
        windows.xaml.framework.set(XamlFramework.Uwp)
        val libraryMetadata = root.resolve("dependencies/Library.winmd")
        val compilerMetadata = root.resolve("dependencies/lib/uap10.0/Microsoft.UI.Xaml.winmd")
        fun writeProvider(path: Path, name: String) {
            Files.createDirectories(path.parent)
            WinRTPortableExecutableMetadataWriter.writeAuthoredWinmd(path.fileName.toString().removeSuffix(".winmd"),
                listOf(WinRTAuthoredRuntimeClassDescriptor(name,
                    interfaceNames = listOf("Windows.UI.Xaml.Markup.IXamlMetadataProvider"))), path)
        }
        writeProvider(libraryMetadata, "Components.LibraryProvider")
        writeProvider(compilerMetadata, "Microsoft.UI.Xaml.XamlTypeInfo.XamlControlsXamlMetaDataProvider")
        val prepare = project.tasks.named("prepareWinRTProjectionMetadata", GenerateWinRTProjectionsTask::class.java).get()
        val manifest = prepare.outputDirectory.file("kotlin-winrt-metadata/resolved-sources.tsv").get().asFile.toPath()
        val references = project.tasks.named("resolveWinRTXamlReferences", ResolveWinUiXamlReferencesTask::class.java).get()
        val referenceManifest = references.outputFile.get().asFile.toPath()

        val stage = project.tasks.create("stageLibraryProviders", StageWindowsPackageRuntimeAssetsTask::class.java)
        stage.xamlFramework.set(XamlFramework.Uwp)
        stage.outputDirectory.set(root.resolve("staged").toFile())
        stage.nugetPackages.set(emptyList())
        stage.runtimeAssets.set(emptyList())
        stage.runtimeIdentifier.set("win-x64")
        stage.generateProjectPri.set(false)
        // Dependency discovery must not read outputs before either producer runs.
        assertFalse(Files.exists(manifest))
        assertFalse(Files.exists(referenceManifest))
        val producers = stage.inputXamlMetadataFiles.buildDependencies.getDependencies(stage)
        assertTrue(prepare in producers)
        assertTrue(references in producers)
        Files.createDirectories(manifest.parent)
        Files.writeString(manifest, "kotlin-winrt-prepared-metadata-v1\n" + metadataRow(libraryMetadata))
        Files.createDirectories(referenceManifest.parent)
        Files.writeString(referenceManifest, "$compilerMetadata\n")
        assertEquals(manifest.toFile(), stage.xamlPreparedMetadataManifest.get().asFile)
        assertEquals(setOf(referenceManifest.toFile()), stage.xamlReferenceManifests.files)
        // ProjectBuilder does not execute producer tasks. After checking the real
        // pipeline edges, supply their fixture outputs to this direct task invocation.
        stage.xamlPreparedMetadataManifest.set(manifest.toFile())
        stage.xamlReferenceManifests.setFrom(referenceManifest.toFile())
        assertEquals(setOf(libraryMetadata.toFile(), compilerMetadata.toFile()), stage.inputXamlMetadataFiles.files)
        stage.stage()
        assertEquals(listOf("Components.LibraryProvider", "Microsoft.UI.Xaml.XamlTypeInfo.XamlControlsXamlMetaDataProvider"),
            Files.readAllLines(root.resolve("staged").resolve(WinUiRuntimeAssetManifests.xamlMetadataProvidersFileName)))
        assertFalse(Files.exists(root.resolve("staged/Microsoft.UI.Xaml.winmd")))
    }

    @Test
    fun uwp_compiler_uses_the_prepared_sdk_for_genxbf_and_compatibility() {
        // Microsoft.Windows.CsWinRT.targets derives metadata from TargetPlatformVersion.
        // Preserve that single resolved SDK when metadataInputs selects an older SDK.
        val root = Files.createTempDirectory("uwp-resolved-sdk-")
        val project = ProjectBuilder.builder().withProjectDir(root.toFile()).build()
        val task = project.tasks.create("compileUwp", CompileWinUiXamlTask::class.java)
        task.xamlFramework.set(XamlFramework.Uwp)
        task.windowsSdkVersion.set("10.0.28000.0")
        val manifest = root.resolve("resolved.tsv")
        task.preparedMetadataManifest.set(manifest.toFile())
        for (version in listOf("10.0.26100.0", "10.0.28000.0")) {
            val metadata = root.resolve("References/$version/Windows.Foundation.UniversalApiContract/1.0.0.0/Windows.Foundation.UniversalApiContract.winmd")
            val genXbf = root.resolve("bin/$version/XamlCompiler/x64/GenXbf.dll")
            Files.createDirectories(metadata.parent)
            Files.write(metadata, byteArrayOf())
            Files.createDirectories(genXbf.parent)
            Files.write(genXbf, byteArrayOf())
            Files.writeString(manifest, "kotlin-winrt-prepared-metadata-v1\n" + metadataRow(metadata) + "sdk\t$version\t\n")
            assertEquals(version, task.resolvedWindowsSdkVersion())
            assertEquals(genXbf.parent.parent.toFile(), task.inputGenXbfDirectory.get())
            val compilerManifest = Json.parseToJsonElement("""{"compatibility":{"frameworks":["uwp"],"windowsSdkVersions":["$version"],"genXbfHost":"win-x64"}}""").jsonObject
            validateXamlCompilerFramework(compilerManifest, XamlFramework.Uwp, task.inputReferenceFiles.files,
                task.inputGenXbfDirectory.get(), null, task.resolvedWindowsSdkVersion())
        }
    }

    @Test
    fun raw_metadata_inputs_fall_back_to_the_configured_sdk_but_ambiguous_sdk_selections_fail() {
        val root = Files.createTempDirectory("uwp-raw-sdk-")
        val project = ProjectBuilder.builder().withProjectDir(root.toFile()).build()
        val task = project.tasks.create("compileUwp", CompileWinUiXamlTask::class.java)
        task.xamlFramework.set(XamlFramework.Uwp)
        task.windowsSdkVersion.set("10.0.26100.0")
        val manifest = root.resolve("resolved.tsv")
        task.preparedMetadataManifest.set(manifest.toFile())
        Files.writeString(manifest, "kotlin-winrt-prepared-metadata-v1\n")
        assertEquals("10.0.26100.0", task.resolvedWindowsSdkVersion())
        Files.writeString(manifest, "kotlin-winrt-prepared-metadata-v1\nsdk\t10.0.26100.0\t\nsdk\t10.0.28000.0\t\n")
        val failure = assertThrows(IllegalArgumentException::class.java) { task.resolvedWindowsSdkVersion() }
        assertTrue(failure.message.orEmpty().contains("requires one resolved Windows SDK version"))
        task.xamlFramework.set(XamlFramework.WinUi)
        assertEquals("10.0.26100.0", task.resolvedWindowsSdkVersion())
    }

    private fun metadataRow(path: Path): String {
        fun encoded(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
        return "file\tPath\t${encoded("fixture")}\t${encoded(path.toAbsolutePath().toString())}\n"
    }
}
