package io.github.composefluent.winrt.gradle

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
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
