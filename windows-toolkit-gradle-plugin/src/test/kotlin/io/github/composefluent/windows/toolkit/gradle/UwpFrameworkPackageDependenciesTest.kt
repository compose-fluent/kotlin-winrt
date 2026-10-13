package io.github.composefluent.windows.toolkit.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

class UwpFrameworkPackageDependenciesTest {
    @Test fun uwp_without_windows_app_sdk_keeps_restored_component_framework_dependencies() {
        // Microsoft.UI.Xaml 2.8.7's AppxPackageRegistration refers to this UWP framework;
        // it is independent of Windows App SDK's AppXReference/deployment property.
        val root = Files.createTempDirectory("uwp-component-framework-")
        val project = ProjectBuilder.builder().withProjectDir(root.toFile()).build()
        project.pluginManager.apply(KotlinWindowsToolkitPlugin::class.java)
        val windows = project.extensions.getByType(WindowsExtension::class.java)
        windows.application { application ->
            application.applicationModel.set(WindowsApplicationModel.Uwp)
            application.packageType.set(WindowsPackageType.Packaged)
            application.windowsAppSdkDeployment.set(WindowsAppSdkDeployment.None)
        }
        project.pluginManager.apply("application")
        val stage = project.tasks.named("stageWinAppPackageJvmMain", StageWinAppPackageTask::class.java).get()
        val install = project.tasks.named("installWinAppPackageJvmMain", InstallWinAppPackageTask::class.java).get()
        val run = project.tasks.named("runWinAppPackageJvmMain", RunWinAppPackageTask::class.java).get()
        assertEquals(WindowsAppSdkDeployment.None, windows.application.windowsAppSdkDeployment.get())
        assertTrue(stage.includeFrameworkPackageDependencies.get())
        assertTrue(install.includeRestoredFrameworkDependencies.get())
        assertEquals(install.dependencyLockFiles.files, run.dependencyLockFiles.files)
        assertEquals(install.dependencyPackageSpecs.get(), run.dependencyPackageSpecs.get())
        assertEquals(install.runtimeIdentifier.get(), run.runtimeIdentifier.get())

        val restoredPackage = root.resolve("nuget/microsoft.ui.xaml/2.8.7")
        val framework = restoredPackage.resolve("tools/AppX/x64/Release/Microsoft.UI.Xaml.2.8.appx")
        Files.createDirectories(framework.parent)
        val publisher = "CN=Microsoft Corporation, O=Microsoft Corporation, L=Redmond, S=Washington, C=US"
        ZipOutputStream(Files.newOutputStream(framework)).use { zip ->
            zip.putNextEntry(ZipEntry("AppxManifest.xml"))
            zip.write("""
                <Package xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10">
                  <Identity Name="Microsoft.UI.Xaml.2.8" Version="8.2501.31001.0" Publisher="$publisher" ProcessorArchitecture="x64" />
                  <Properties><Framework>true</Framework></Properties>
                </Package>
            """.trimIndent().toByteArray())
            zip.closeEntry()
        }
        val payload = root.resolve("payload")
        Files.createDirectories(payload)
        val manifest = payload.resolve("AppxManifest.xml")
        Files.writeString(manifest, """
            <Package xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10">
              <Identity Name="Example.Uwp" Version="1.0.0.0" Publisher="CN=Example" ProcessorArchitecture="x64" />
              <Dependencies><TargetDeviceFamily Name="Windows.Universal" MinVersion="10.0.19041.0" MaxVersionTested="10.0.26100.0" /></Dependencies>
            </Package>
        """.trimIndent())
        AppxManifestPackageSupport.mergeRuntimeDependenciesAndExtensions(
            manifest = manifest,
            packageRoot = payload,
            resolvedPackageManifestFiles = emptyList(),
            runtimeIdentifier = "win-x64",
            restoredPackageRoots = listOf(restoredPackage),
            includeFrameworkDependencies = stage.includeFrameworkPackageDependencies.get(),
        )
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(manifest.toFile())
        val dependencies = document.getElementsByTagNameNS(
            "http://schemas.microsoft.com/appx/manifest/foundation/windows10", "PackageDependency")
        assertEquals(1, dependencies.length)
        val dependency = dependencies.item(0) as org.w3c.dom.Element
        assertEquals("Microsoft.UI.Xaml.2.8", dependency.getAttribute("Name"))
        assertEquals("8.2501.31001.0", dependency.getAttribute("MinVersion"))
        assertEquals(publisher, dependency.getAttribute("Publisher"))
        val candidates = AppxManifestPackageSupport.discoverFrameworkPackageArchives(listOf(restoredPackage), "win-x64")
        val developmentDependencies = AppxManifestPackageSupport.selectFrameworkPackageArchivesForManifest(
            manifest, candidates, "win-x64",
        )
        assertEquals(listOf(framework.toAbsolutePath().normalize()), developmentDependencies)
        val archive = root.resolve("application.msix")
        ZipOutputStream(Files.newOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("AppxManifest.xml"))
            zip.write(Files.readAllBytes(manifest))
            zip.closeEntry()
        }
        assertEquals(developmentDependencies, AppxManifestPackageSupport.selectFrameworkPackageArchives(
            archive, candidates, "win-x64",
        ))
    }
}
