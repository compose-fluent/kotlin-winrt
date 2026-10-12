package io.github.composefluent.windows.toolkit.gradle

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deployment contract: .cswinrt/src/Samples/WinUIDesktopSample/WinUIDesktopSample.csproj
 * enables WindowsAppSDKSelfContained; WindowsAppSDK.SelfContained.targets collects component
 * payloads and merges package.appxfragment. These tests exercise the shared JVM/Native stager.
 */
class WindowsAppSdkSelfContainedTest {
    @Test
    fun component_appx_fragments_register_lifecycle_and_notifications_in_both_manifests() {
        val fixture = Fixture()
        val foundation = fixture.packageRoot("Microsoft.WindowsAppSDK.Foundation")
        fixture.write(foundation, "runtimes-framework/win-x64/native/Microsoft.WindowsAppRuntime.dll", "component")
        val fragment = """
            <Fragment xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10">
              <Extensions><Extension Category="windows.activatableClass.inProcessServer">
                <InProcessServer><Path>Microsoft.WindowsAppRuntime.dll</Path>
                  <ActivatableClass ActivatableClassId="Microsoft.Windows.AppLifecycle.AppInstance" ThreadingModel="both" />
                  <ActivatableClass ActivatableClassId="Microsoft.Windows.AppNotifications.AppNotificationManager" ThreadingModel="both" />
                  <ActivatableClass ActivatableClassId="Microsoft.Windows.Shared" ThreadingModel="both" />
                </InProcessServer>
              </Extension></Extensions>
            </Fragment>
        """.trimIndent()
        fixture.write(foundation, "runtimes-framework/package.appxfragment", fragment)
        fixture.write(foundation, "build/native/LiftedWinRTClassRegistrations.xml", """
            <Registrations><InProcessServer><Path>Microsoft.WindowsAppRuntime.dll</Path>
              <ActivatableClass ActivatableClassId="Microsoft.Windows.Shared" ThreadingModel="both" />
            </InProcessServer></Registrations>
        """.trimIndent())
        val task = fixture.stageTask(listOf(foundation))

        for (includeJvm in listOf(true, false)) {
            task.includeJvmAuthoringArtifacts.set(includeJvm)
            task.stage()
            val root = task.outputDirectory.get().asFile.toPath()
            assertEquals(fragment, Files.readString(root.resolve(
                "registrations/Microsoft.WindowsAppSDK.Foundation/runtimes-framework/package.appxfragment",
            )))
            val manifest = Files.readString(root.resolve("app.exe.manifest"))
            assertEquals(1, Regex("name='Microsoft.Windows.AppLifecycle.AppInstance'").findAll(manifest).count())
            assertEquals(1, Regex("name='Microsoft.Windows.AppNotifications.AppNotificationManager'").findAll(manifest).count())
            assertEquals(1, Regex("name='Microsoft.Windows.Shared'").findAll(manifest).count())

            val appxManifest = fixture.write(root, "AppxManifest.xml", """
                <Package xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10" />
            """.trimIndent())
            AppxManifestPackageSupport.mergeRuntimeDependenciesAndExtensions(
                manifest = appxManifest,
                packageRoot = root,
                resolvedPackageManifestFiles = emptyList(),
                runtimeIdentifier = "win-x64",
                includeFrameworkDependencies = false,
            )
            val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                .newDocumentBuilder().parse(appxManifest.toFile())
            assertEquals(3, document.getElementsByTagNameNS("*", "ActivatableClass").length)
        }

        task.includeFrameworkRuntimeAssets.set(false)
        task.stage()
        val root = task.outputDirectory.get().asFile.toPath()
        assertFalse(Files.exists(root.resolve("Microsoft.WindowsAppRuntime.dll")))
        assertFalse(Files.exists(root.resolve(
            "registrations/Microsoft.WindowsAppSDK.Foundation/runtimes-framework/package.appxfragment",
        )))
        assertFalse(Files.readString(root.resolve("app.exe.manifest")).contains("AppInstance"))
    }

    @Test
    fun framework_archives_supply_only_missing_dlls_for_the_selected_architecture() {
        val fixture = Fixture()
        val foundation = fixture.packageRoot("Microsoft.WindowsAppSDK.Foundation")
        fixture.write(foundation, "runtimes-framework/win-x64/native/Microsoft.WindowsAppRuntime.dll", "component")
        fixture.write(foundation, "runtimes-framework/win-x64/native/Controls.pri", "component-pri")
        val runtime = fixture.packageRoot("Microsoft.WindowsAppSDK.Runtime")
        fixture.archive(runtime, "framework-x64.msix", "x64", mapOf(
            "Microsoft.WindowsAppRuntime.dll" to "archive",
            "Microsoft.WindowsAppRuntime.Insights.Resource.dll" to "resource",
            "en-US/Runtime.Resource.dll" to "localized",
            "Controls.pri" to "archive-pri",
            "resources.pri" to "archive-resources",
            "AppxBlockMap.xml" to "packaging metadata",
        ))
        fixture.archive(runtime, "framework-x86.msix", "x86", mapOf("WrongArchitecture.dll" to "x86"))
        fixture.archive(runtime, "main.msix", "x64", mapOf("MainPackage.dll" to "main"), framework = false)
        val unrelated = fixture.packageRoot("Other.Package")
        fixture.archive(unrelated, "framework.msix", "x64", mapOf("Unrelated.dll" to "other"))
        val task = fixture.stageTask(listOf(foundation, runtime, unrelated))

        for (includeJvm in listOf(true, false)) {
            task.includeJvmAuthoringArtifacts.set(includeJvm)
            task.stage()
            val root = task.outputDirectory.get().asFile.toPath()
            assertEquals("component", Files.readString(root.resolve("Microsoft.WindowsAppRuntime.dll")))
            assertEquals("resource", Files.readString(root.resolve("Microsoft.WindowsAppRuntime.Insights.Resource.dll")))
            assertEquals("localized", Files.readString(root.resolve("en-US/Runtime.Resource.dll")))
            assertEquals("component-pri", Files.readString(root.resolve("Controls.pri")))
            for (name in listOf("WrongArchitecture.dll", "MainPackage.dll", "Unrelated.dll", "resources.pri", "AppxBlockMap.xml", "AppxManifest.xml")) {
                assertFalse(name, Files.exists(root.resolve(name)))
            }
        }

        task.includeFrameworkRuntimeAssets.set(false)
        task.stage()
        val root = task.outputDirectory.get().asFile.toPath()
        assertFalse(Files.exists(root.resolve("Microsoft.WindowsAppRuntime.Insights.Resource.dll")))
        assertFalse(Files.exists(root.resolve("Microsoft.WindowsAppRuntime.dll")))
    }

    @Test
    fun framework_dll_paths_cannot_escape_the_output_directory() {
        val fixture = Fixture()
        val runtime = fixture.packageRoot("Microsoft.WindowsAppSDK.Runtime")
        fixture.archive(runtime, "framework.msix", "x64", mapOf("../escaped.dll" to "outside"))
        val task = fixture.stageTask(listOf(runtime))

        val error = runCatching { task.stage() }.exceptionOrNull()
        assertTrue(error?.message.orEmpty(), error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("Windows App SDK framework DLL path"))
        assertFalse(Files.exists(task.outputDirectory.get().asFile.toPath().parent.resolve("escaped.dll")))
    }

    private class Fixture {
        private val project = ProjectBuilder.builder().build()

        fun packageRoot(id: String): Path {
            val root = project.layout.buildDirectory.dir("nuget/${id.lowercase()}/2.5.1").get().asFile.toPath()
            write(root, "$id.nuspec", """
                <package><metadata><id>$id</id><version>2.5.1</version></metadata></package>
            """.trimIndent())
            return root
        }

        fun write(root: Path, relative: String, content: String): Path = root.resolve(relative).also { target ->
            Files.createDirectories(target.parent)
            Files.writeString(target, content)
        }

        fun archive(root: Path, name: String, architecture: String, files: Map<String, String>, framework: Boolean = true) {
            val archive = root.resolve("tools/MSIX/$name")
            Files.createDirectories(archive.parent)
            val manifest = """
                <Package xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10">
                  <Identity Name="Microsoft.WindowsAppRuntime.2" Version="2.5.1.0"
                            Publisher="CN=Microsoft" ProcessorArchitecture="$architecture" />
                  <Properties><Framework>$framework</Framework></Properties>
                </Package>
            """.trimIndent()
            ZipOutputStream(Files.newOutputStream(archive)).use { zip ->
                (mapOf("AppxManifest.xml" to manifest) + files).forEach { (path, content) ->
                    zip.putNextEntry(ZipEntry(path))
                    zip.write(content.toByteArray())
                    zip.closeEntry()
                }
            }
        }

        fun stageTask(roots: List<Path>): StageWindowsPackageRuntimeAssetsTask {
            val manifest = project.layout.buildDirectory.file("packages.json").get().asFile.toPath()
            writeResolvedRuntimeNuGetPackages(manifest, roots)
            return project.tasks.register("stageRuntimeAssets", StageWindowsPackageRuntimeAssetsTask::class.java) { task ->
                task.outputDirectory.set(project.layout.buildDirectory.dir("runtime-assets"))
                task.nugetPackages.set(emptyList())
                task.runtimeAssets.set(emptyList())
                task.resolvedNuGetPackageManifestFiles.from(manifest)
                task.nugetPackageContentFiles.from(roots)
                task.runtimeIdentifier.set("win-x64")
                task.generateProjectPri.set(false)
            }.get()
        }
    }
}
