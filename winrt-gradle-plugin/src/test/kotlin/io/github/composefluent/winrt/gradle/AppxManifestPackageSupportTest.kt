package io.github.composefluent.winrt.gradle

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppxManifestPackageSupportTest {
    @Test
    fun merges_restored_framework_dependency_and_present_lifted_registrations() {
        val root = Files.createTempDirectory("kotlin-winrt-appx-manifest-")
        val packageRoot = root.resolve("packages/microsoft.windowsappsdk.runtime/2.2.0")
        val runtimeArchive = packageRoot.resolve("tools/MSIX/win10-x64/Microsoft.WindowsAppRuntime.2.msix")
        Files.createDirectories(runtimeArchive.parent)
        writeZip(
            runtimeArchive,
            "AppxManifest.xml" to """
                <?xml version="1.0" encoding="utf-8"?>
                <Package xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10">
                  <Identity Name="Microsoft.WindowsAppRuntime.2" Version="2.2.0.0" Publisher="CN=Microsoft Corporation, O=Microsoft Corporation, L=Redmond, S=Washington, C=US" ProcessorArchitecture="x64" />
                  <Properties><Framework>true</Framework></Properties>
                </Package>
            """.trimIndent(),
        )

        val appRoot = root.resolve("app")
        Files.createDirectories(appRoot.resolve("registrations"))
        Files.writeString(appRoot.resolve("WinUI3Package.dll"), "dll")
        Files.writeString(
            appRoot.resolve("registrations/LiftedWinRTClassRegistrations.xml"),
            """
                <Registrations xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10">
                  <Extension Category="windows.activatableClass.inProcessServer">
                    <InProcessServer>
                      <Path>WinUI3Package.dll</Path>
                      <ActivatableClass ActivatableClassId="WinUI3Package.Shimmer" ThreadingModel="both" />
                      <ActivatableClass ActivatableClassId="WinUI3Package.Shimmer" ThreadingModel="both" />
                    </InProcessServer>
                  </Extension>
                  <Extension Category="windows.activatableClass.inProcessServer">
                    <InProcessServer>
                      <Path>Missing.dll</Path>
                      <ActivatableClass ActivatableClassId="Sample.Missing" ThreadingModel="both" />
                    </InProcessServer>
                  </Extension>
                </Registrations>
            """.trimIndent(),
        )
        val manifest = appRoot.resolve("AppxManifest.xml")
        Files.writeString(manifest, """
            <Package xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10">
              <Identity Name="Sample" Publisher="CN=Sample" Version="1.0.0.0" />
              <Dependencies><TargetDeviceFamily Name="Windows.Desktop" MinVersion="10.0.19041.0" /></Dependencies>
              <Applications><Application Id="App" Executable="Sample.exe" EntryPoint="Windows.FullTrustApplication" /></Applications>
            </Package>
        """.trimIndent())

        val resolved = root.resolve("resolved.json")
        Files.writeString(
            resolved,
            """{"packageRoots":["${packageRoot.toString().replace("\\", "\\\\")}"]}""",
        )

        AppxManifestPackageSupport.mergeRuntimeDependenciesAndExtensions(
            manifest = manifest,
            packageRoot = appRoot,
            resolvedPackageManifestFiles = listOf(resolved),
            runtimeIdentifier = "win-x64",
        )

        val output = Files.readString(manifest)
        assertEquals(1, Regex("Name=\"Microsoft\\.WindowsAppRuntime\\.2\"").findAll(output).count())
        assertTrue(output.contains("MinVersion=\"2.2.0.0\""))
        assertTrue(output.contains("Publisher=\"CN=Microsoft Corporation, O=Microsoft Corporation, L=Redmond, S=Washington, C=US\""))
        assertEquals(1, Regex("ActivatableClassId=\"WinUI3Package\\.Shimmer\"").findAll(output).count())
        assertTrue(!output.contains("Sample.Missing"))
    }

    @Test
    fun merges_restored_framework_dependency_without_resolved_manifest_json() {
        val root = Files.createTempDirectory("kotlin-winrt-appx-manifest-lockfile-")
        val packageRoot = root.resolve("packages/microsoft.windowsappsdk.runtime/2.2.0")
        val runtimeArchive = packageRoot.resolve("tools/MSIX/win10-x64/Microsoft.WindowsAppRuntime.2.msix")
        Files.createDirectories(runtimeArchive.parent)
        writeZip(
            runtimeArchive,
            "AppxManifest.xml" to """
                <?xml version="1.0" encoding="utf-8"?>
                <Package xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10">
                  <Identity Name="Microsoft.WindowsAppRuntime.2" Version="2.2.0.0" Publisher="CN=Microsoft Corporation, O=Microsoft Corporation, L=Redmond, S=Washington, C=US" ProcessorArchitecture="x64" />
                  <Properties><Framework>true</Framework></Properties>
                </Package>
            """.trimIndent(),
        )

        val manifest = root.resolve("AppxManifest.xml")
        Files.writeString(manifest, """
            <Package xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10">
              <Identity Name="Sample" Publisher="CN=Sample" Version="1.0.0.0" />
              <Dependencies><TargetDeviceFamily Name="Windows.Desktop" MinVersion="10.0.19041.0" /></Dependencies>
            </Package>
        """.trimIndent())

        AppxManifestPackageSupport.mergeRuntimeDependenciesAndExtensions(
            manifest = manifest,
            packageRoot = root,
            resolvedPackageManifestFiles = emptyList(),
            restoredPackageRoots = listOf(packageRoot),
            runtimeIdentifier = "win-x64",
        )

        val output = Files.readString(manifest)
        assertEquals(1, Regex("Name=\"Microsoft\\.WindowsAppRuntime\\.2\"").findAll(output).count())
        assertTrue(output.contains("MinVersion=\"2.2.0.0\""))
    }

    @Test
    fun discovers_framework_archives_by_manifest_identity_not_directory_name() {
        val root = Files.createTempDirectory("kotlin-winrt-framework-discovery-")
        val matching = root.resolve("arbitrary-layout/runtime-package.msix")
        val wrongArchitecture = root.resolve("win10-x64/wrong-package.msix")
        val nonFramework = root.resolve("MSIX/not-framework.appx")
        listOf(matching, wrongArchitecture, nonFramework).forEach { archive ->
            Files.createDirectories(archive.parent)
        }
        writeZip(matching, frameworkManifest(processorArchitecture = "x64"))
        writeZip(wrongArchitecture, frameworkManifest(processorArchitecture = "arm64"))
        writeZip(nonFramework, frameworkManifest(processorArchitecture = "x64", framework = false))

        val discovered = AppxManifestPackageSupport.discoverFrameworkPackageArchives(
            restoredPackageRoots = listOf(root),
            runtimeIdentifier = "win-x64",
        )

        assertEquals(listOf(matching.toAbsolutePath().normalize()), discovered)
    }

    @Test
    fun rejects_explicit_framework_dependency_with_wrong_architecture_or_framework_flag() {
        val root = Files.createTempDirectory("kotlin-winrt-framework-explicit-")
        val wrongArchitecture = root.resolve("wrong.msix")
        val nonFramework = root.resolve("non-framework.msix")
        writeZip(wrongArchitecture, frameworkManifest(processorArchitecture = "arm64"))
        writeZip(nonFramework, frameworkManifest(processorArchitecture = "x64", framework = false))

        val architectureFailure = runCatching {
            AppxManifestPackageSupport.validateFrameworkPackageArchive(wrongArchitecture, "win-x64")
        }.exceptionOrNull()
        val frameworkFailure = runCatching {
            AppxManifestPackageSupport.validateFrameworkPackageArchive(nonFramework, "win-x64")
        }.exceptionOrNull()

        assertTrue(architectureFailure?.message.orEmpty().contains("processor architecture"))
        assertFalse(frameworkFailure == null)
        assertTrue(frameworkFailure?.message.orEmpty().contains("valid framework AppxManifest.xml"))
    }

    @Test
    fun existing_dependency_min_version_must_not_exceed_restored_package_version() {
        val root = Files.createTempDirectory("kotlin-winrt-framework-version-")
        val packageRoot = root.resolve("packages/runtime/2.2.0")
        val archive = packageRoot.resolve("runtime.msix")
        Files.createDirectories(archive.parent)
        writeZip(archive, frameworkManifest(processorArchitecture = "x64", version = "2.2.0.0"))
        val manifest = root.resolve("AppxManifest.xml")
        Files.writeString(manifest, """
            <Package xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10">
              <Identity Name="Sample" Publisher="CN=Sample" Version="1.0.0.0" />
              <Dependencies>
                <PackageDependency Name="Framework" Publisher="CN=Framework" MinVersion="3.0.0.0" />
              </Dependencies>
            </Package>
        """.trimIndent())

        val failure = runCatching {
            AppxManifestPackageSupport.mergeRuntimeDependenciesAndExtensions(
                manifest = manifest,
                packageRoot = root,
                resolvedPackageManifestFiles = emptyList(),
                restoredPackageRoots = listOf(packageRoot),
                runtimeIdentifier = "win-x64",
            )
        }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("declares MinVersion"))
        assertTrue(failure?.message.orEmpty().contains("only provides version"))
    }

    @Test
    fun removes_identical_duplicate_manifest_dependencies() {
        val root = Files.createTempDirectory("kotlin-winrt-framework-duplicate-")
        val manifest = root.resolve("AppxManifest.xml")
        Files.writeString(manifest, """
            <Package xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10">
              <Identity Name="Sample" Publisher="CN=Sample" Version="1.0.0.0" />
              <Dependencies>
                <PackageDependency Name="Framework" Publisher="CN=Framework" MinVersion="2.0.0.0" />
                <PackageDependency Name="framework" Publisher="CN=Framework" MinVersion="2.0.0.0" />
              </Dependencies>
            </Package>
        """.trimIndent())

        AppxManifestPackageSupport.mergeRuntimeDependenciesAndExtensions(
            manifest = manifest,
            packageRoot = root,
            resolvedPackageManifestFiles = emptyList(),
            runtimeIdentifier = "win-x64",
        )

        val output = Files.readString(manifest)
        assertEquals(1, Regex("Name=\"Framework\"", RegexOption.IGNORE_CASE).findAll(output).count())
    }

    private fun frameworkManifest(
        processorArchitecture: String,
        version: String = "2.2.0.0",
        framework: Boolean = true,
    ): Pair<String, String> =
        "AppxManifest.xml" to """
            <?xml version="1.0" encoding="utf-8"?>
            <Package xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10">
              <Identity Name="Framework" Version="$version" Publisher="CN=Framework" ProcessorArchitecture="$processorArchitecture" />
              <Properties><Framework>$framework</Framework></Properties>
            </Package>
        """.trimIndent()

    private fun writeZip(path: Path, vararg entries: Pair<String, String>) {
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }
}
