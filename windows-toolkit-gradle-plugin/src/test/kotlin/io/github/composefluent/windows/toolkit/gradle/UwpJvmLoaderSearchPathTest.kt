package io.github.composefluent.windows.toolkit.gradle

import io.github.composefluent.winrt.runtime.XamlFramework
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory

class UwpJvmLoaderSearchPathTest {
    @Test fun package_loader_paths_preserve_user_order_and_merge_case_insensitive_duplicates() {
        // SDK uap6 LoaderSearchPathOverride replaces the package root and permits at most five
        // entries. This is the package equivalent of the JNI host's desktop DLL search setup.
        val manifest = Files.createTempFile("uwp-loader-paths-", ".xml")
        Files.writeString(manifest, manifestWithPaths("plugins", "Runtime\\Bin", "runtime/bin"))
        repeat(2) { AppxManifestPackageSupport.mergeLoaderSearchPaths(manifest, listOf("", "runtime/bin", "runtime/bin/server")) }
        assertEquals(listOf("plugins", "Runtime\\Bin", "", "runtime/bin/server"), loaderPaths(manifest))
        val document = readManifest(manifest)
        assertEquals(1, document.getElementsByTagNameNS(loaderNamespace, "LoaderSearchPathOverride").length)
        assertEquals(listOf("uap6"), document.documentElement.getAttribute("IgnorableNamespaces").split(' '))
    }

    @Test fun loader_path_limit_and_invalid_relative_paths_fail_without_rewriting_the_manifest() {
        val manifest = Files.createTempFile("uwp-loader-limit-", ".xml")
        val original = manifestWithPaths("plugins", "plugins2", "plugins3")
        Files.writeString(manifest, original)
        val failure = runCatching {
            AppxManifestPackageSupport.mergeLoaderSearchPaths(manifest, listOf("", "runtime/bin", "runtime/bin/server"))
        }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("at most 5"))
        assertEquals(original, Files.readString(manifest))
        for (path in listOf("/absolute", "C:/absolute", "runtime/", "../outside")) {
            Files.writeString(manifest, manifestWithPaths(path))
            assertTrue(runCatching { AppxManifestPackageSupport.mergeLoaderSearchPaths(manifest, listOf("")) }.isFailure)
        }
    }

    @Test fun staging_adds_loader_paths_only_for_the_uwp_jvm_host() {
        for ((model, jvm) in listOf(WindowsApplicationModel.Uwp to true,
            WindowsApplicationModel.Uwp to false, WindowsApplicationModel.Desktop to true)) {
            val root = Files.createTempDirectory("uwp-loader-stage-")
            val assets = root.resolve("assets")
            Files.createDirectories(assets.resolve("Assets"))
            Files.writeString(assets.resolve("AppxManifest.xml"), stageManifest)
            Files.writeString(assets.resolve("Assets/Logo.png"), "logo")
            Files.writeString(assets.resolve("App.exe"), "application")
            val launcher = root.resolve("launcher.exe")
            Files.writeString(launcher, "launcher")
            val jvmLibrary = root.resolve("jvm.dll")
            val javaLibrary = root.resolve("java.dll")
            Files.writeString(jvmLibrary, "jvm")
            Files.writeString(javaLibrary, "java")
            val project = ProjectBuilder.builder().withProjectDir(root.toFile()).build()
            val stage = project.tasks.register("stage", StageWinAppPackageTask::class.java).get()
            stage.runtimeAssetsDirectory.set(assets.toFile())
            stage.outputDirectory.set(root.resolve("output").toFile())
            stage.runtimeIdentifier.set("win-x64")
            stage.minWindowsVersion.set("10.0.19041.0")
            stage.maxVersionTested.set("10.0.26100.0")
            stage.applicationModel.set(model)
            stage.xamlFramework.set(if (model == WindowsApplicationModel.Uwp) XamlFramework.Uwp else XamlFramework.WinUi)
            stage.executableBaseName.set("App")
            stage.generateProjectPri.set(false)
            if (jvm) stage.jvmLauncherExecutable.set(launcher.toFile())
            stage.packagePayloadFiles.from(jvmLibrary, javaLibrary)
            stage.projectPriTargetPaths.set(mapOf(jvmLibrary.toString() to "runtime/bin/server/jvm.dll",
                javaLibrary.toString() to "runtime/bin/java.dll"))
            stage.stage()
            val paths = loaderPaths(root.resolve("output/AppxManifest.xml"))
            if (jvm && model == WindowsApplicationModel.Uwp) {
                assertEquals(listOf("", "runtime/bin", "runtime/bin/server"), paths)
            } else assertTrue(paths.isEmpty())
            assertFalse(Files.readString(assets.resolve("AppxManifest.xml")).contains("LoaderSearchPathOverride"))
        }
    }

    private fun loaderPaths(manifest: Path): List<String> {
        val nodes = readManifest(manifest).getElementsByTagNameNS(loaderNamespace, "LoaderSearchPathEntry")
        return (0 until nodes.length).map { (nodes.item(it) as org.w3c.dom.Element).getAttribute("FolderPath") }
    }

    private fun readManifest(manifest: Path) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(manifest.toFile())

    private fun manifestWithPaths(vararg paths: String) = """
        <Package xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10" xmlns:uap6="$loaderNamespace">
          <Extensions><uap6:Extension Category="windows.loaderSearchPathOverride"><uap6:LoaderSearchPathOverride>
            ${paths.joinToString("\n") { "<uap6:LoaderSearchPathEntry FolderPath=\"$it\" />" }}
          </uap6:LoaderSearchPathOverride></uap6:Extension></Extensions>
        </Package>
    """.trimIndent()

    private val loaderNamespace = "http://schemas.microsoft.com/appx/manifest/uap/windows10/6"
    private val stageManifest = """
        <Package xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10" xmlns:uap="http://schemas.microsoft.com/appx/manifest/uap/windows10">
          <Identity Name="Example.Loader" Publisher="CN=Example" Version="1.0.0.0" ProcessorArchitecture="x64" />
          <Properties><DisplayName>Loader</DisplayName><PublisherDisplayName>Example</PublisherDisplayName><Logo>Assets/Logo.png</Logo></Properties>
          <Applications><Application Id="App" Executable="App.exe" EntryPoint="Example.App">
            <uap:VisualElements DisplayName="Loader" Description="Loader" BackgroundColor="transparent" Square150x150Logo="Assets/Logo.png" Square44x44Logo="Assets/Logo.png" />
          </Application></Applications>
        </Package>
    """.trimIndent()
}
