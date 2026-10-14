package io.github.composefluent.windows.toolkit.gradle

import io.github.composefluent.winrt.metadata.WinRTAuthoredRuntimeClassDescriptor
import io.github.composefluent.winrt.metadata.WinRTMetadataLoader
import io.github.composefluent.winrt.metadata.WinRTPortableExecutableMetadataWriter
import io.github.composefluent.winrt.runtime.WinUIRuntimeAssetManifests
import io.github.composefluent.winrt.runtime.XamlFramework
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class UWPXamlComponentsTest {
    @Test
    fun authored_pages_add_compiler_sdk_roots_even_without_a_generated_sdk_projection() {
        // XAMLC CSharpPagePass2 emits connector contracts even when no selected
        // control references them. UWP has a separate IComponentConnector2 ABI.
        val root = Files.createTempDirectory("uwp-page-roots-")
        Files.writeString(root.resolve("Resources.xaml"), "<ResourceDictionary />")
        assertTrue(requiredXamlProjectionTypes(listOf(root.toFile()), XamlFramework.UWP, false).isEmpty())
        Files.writeString(root.resolve("MainPage.xaml"), "<Page />")
        Files.writeString(root.resolve("MainPage.kt"), "class MainPage")

        val uwp = requiredXamlProjectionTypes(listOf(root.toFile()), XamlFramework.UWP, false)
        assertTrue(uwp.contains("Windows.UI.Xaml.Markup.IComponentConnector"))
        assertTrue(uwp.contains("Windows.UI.Xaml.Markup.IComponentConnector2"))
        assertTrue(uwp.contains("Windows.UI.Xaml.Markup.IDataTemplateExtension"))
        assertTrue(uwp.contains("Windows.System.DispatcherQueue"))
        assertFalse(uwp.any { it.startsWith("Microsoft.UI.") })

        val winUI = requiredXamlProjectionTypes(listOf(root.toFile()), XamlFramework.WinUI, true)
        assertTrue(winUI.contains("Microsoft.UI.Xaml.Markup.IComponentConnector"))
        assertFalse(winUI.contains("Microsoft.UI.Xaml.Markup.IComponentConnector2"))
        assertTrue(winUI.contains("Microsoft.UI.Xaml.Controls.XamlControlsResources"))
        assertFalse(winUI.any { it.startsWith("Windows.UI.Xaml.") })
    }

    @Test
    fun component_resource_roots_follow_the_selected_sdk_metadata() {
        // CsWinRT discovers component shapes from their declared SDK bases/interfaces.
        val root = Files.createTempDirectory("uwp-component-roots-")
        val winmd = root.resolve("Components.winmd")
        WinRTPortableExecutableMetadataWriter.writeAuthoredWinmd("Components", listOf(
            WinRTAuthoredRuntimeClassDescriptor("Components.UWPResources",
                baseRuntimeClassName = "Windows.UI.Xaml.ResourceDictionary",
                interfaceNames = listOf("Windows.UI.Xaml.IResourceDictionary")),
            WinRTAuthoredRuntimeClassDescriptor("Components.WinUIResources",
                baseRuntimeClassName = "Microsoft.UI.Xaml.ResourceDictionary",
                interfaceNames = listOf("Microsoft.UI.Xaml.IResourceDictionary")),
        ), winmd)
        val model = WinRTMetadataLoader.load(winmd)
        assertEquals(listOf("Components.UWPResources"), automaticXamlComponentResourceDictionaryTypes(
            model, setOf("Windows.UI.Xaml.ResourceDictionary")))
        assertEquals(listOf("Components.WinUIResources"), automaticXamlComponentResourceDictionaryTypes(
            model, setOf("Microsoft.UI.Xaml.ResourceDictionary")))
        assertTrue(automaticXamlComponentResourceDictionaryTypes(model, emptySet()).isEmpty())
    }

    @Test
    fun merged_component_resources_wrap_the_selected_xaml_resource_dictionary() {
        val project = ProjectBuilder.builder().build()
        val root = Files.createTempDirectory("uwp-component-support-")
        val manifest = root.resolve("compiler-support.tsv")
        Files.writeString(manifest, "kind\tclassName\tsourceFile\tentries\towner\n" +
            "xaml-component-resource\tio.github.composefluent.winrt.projections.support.WinUIXamlComponentResources\txaml-component-resources.tsv\t1\tcomponent.jar\n")
        Files.writeString(root.resolve("xaml-component-resources.tsv"), "runtimeClassName\nComponents.Resources\n")
        val task = project.tasks.create("mergeComponents", MergeWinRTCompilerSupportTask::class.java)
        task.localCompilerSupportManifest.set(manifest.toFile())
        task.outputDirectory.set(root.resolve("merged").toFile())
        task.emitXamlComponentResourceSources.set(true)
        for ((framework, namespace) in listOf(XamlFramework.UWP to "windows.ui.xaml", XamlFramework.WinUI to "microsoft.ui.xaml")) {
            task.xamlFramework.set(framework)
            task.merge()
            val source = Files.readString(root.resolve("merged/io/github/composefluent/winrt/projections/support/WinUIXamlComponentResources.kt"))
            assertTrue(source, source.contains("import $namespace.ResourceDictionary"))
            assertTrue(source, source.contains("ActivationFactory.activateInstance(\"Components.Resources\")"))
        }
    }

    @Test
    fun provider_manifest_uses_the_application_framework_and_metadata_inheritance() {
        val project = ProjectBuilder.builder().build()
        val root = Files.createTempDirectory("uwp-provider-assets-")
        val winmd = root.resolve("Components.winmd")
        WinRTPortableExecutableMetadataWriter.writeAuthoredWinmd("Components", listOf(
            WinRTAuthoredRuntimeClassDescriptor("Components.UWPProvider",
                interfaceNames = listOf("Windows.UI.Xaml.Markup.IXamlMetadataProvider")),
            WinRTAuthoredRuntimeClassDescriptor("Components.DerivedUWPProvider",
                baseRuntimeClassName = "Components.UWPProvider",
                interfaceNames = listOf("Components.IProvider")),
            WinRTAuthoredRuntimeClassDescriptor("Components.WinUIProvider",
                interfaceNames = listOf("Microsoft.UI.Xaml.Markup.IXamlMetadataProvider")),
        ), winmd)
        val winUI2Winmd = root.resolve("lib/uap10.0/Microsoft.UI.Xaml.winmd")
        Files.createDirectories(winUI2Winmd.parent)
        WinRTPortableExecutableMetadataWriter.writeAuthoredWinmd("Microsoft.UI.Xaml", listOf(
            WinRTAuthoredRuntimeClassDescriptor("Microsoft.UI.Xaml.XamlTypeInfo.XamlControlsXamlMetaDataProvider",
                interfaceNames = listOf("Windows.UI.Xaml.Markup.IXamlMetadataProvider")),
        ), winUI2Winmd)
        val task = project.tasks.create("stageComponents", StageWindowsPackageRuntimeAssetsTask::class.java)
        task.outputDirectory.set(root.resolve("staged").toFile())
        task.nugetPackages.set(emptyList())
        task.runtimeAssets.set(emptyList())
        task.runtimeAssetFiles.from(winmd)
        val preparedManifest = root.resolve("resolved-sources.tsv")
        val referenceManifest = root.resolve("compiler-references.txt")
        task.xamlPreparedMetadataManifest.set(preparedManifest.toFile())
        task.xamlReferenceManifests.from(referenceManifest)
        // Match the producer boundary: task configuration stores paths before
        // either manifest exists, and metadata contents remain tracked inputs.
        fun encoded(value: String) = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(Charsets.UTF_8))
        Files.writeString(preparedManifest, "kotlin-winrt-prepared-metadata-v1\n" +
            "file\tPath\t${encoded("Components")}\t${encoded(winmd.toString())}\n")
        Files.writeString(referenceManifest, "$winUI2Winmd\n")
        assertEquals(setOf(winmd.toFile(), winUI2Winmd.toFile()), task.inputXamlMetadataFiles.files)
        task.runtimeIdentifier.set("win-x64")
        task.generateProjectPri.set(false)
        task.xamlFramework.set(XamlFramework.UWP)
        task.stage()
        val manifest = root.resolve("staged").resolve(WinUIRuntimeAssetManifests.xamlMetadataProvidersFileName)
        assertEquals(listOf("Components.DerivedUWPProvider", "Components.UWPProvider",
            "Microsoft.UI.Xaml.XamlTypeInfo.XamlControlsXamlMetaDataProvider"), Files.readAllLines(manifest))
        assertFalse(Files.exists(root.resolve("staged/Microsoft.UI.Xaml.winmd")))
        task.xamlFramework.set(XamlFramework.WinUI)
        task.stage()
        assertEquals(listOf("Components.WinUIProvider"), Files.readAllLines(manifest))
    }
}
