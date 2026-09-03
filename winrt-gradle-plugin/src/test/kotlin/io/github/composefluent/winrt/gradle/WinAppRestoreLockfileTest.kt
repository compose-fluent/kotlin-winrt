package io.github.composefluent.winrt.gradle

import org.gradle.api.GradleException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class WinAppRestoreLockfileTest {
    @Test
    fun schema_three_lockfile_exposes_resolved_packages_and_winmds() {
        val root = Files.createTempDirectory("kotlin-winrt-winapp-lock-")
        val lockfile = root.resolve("winmds.lock.json")
        Files.writeString(
            lockfile,
            """
            {
              "schema": 3,
              "generated_at": "2026-09-03T00:00:00Z",
              "nuget_cache_dir": "C:/nuget/packages",
              "packages": [
                {
                  "name": "Microsoft.WindowsAppSDK",
                  "version": "2.2.0",
                  "winmds": ["C:/nuget/packages/microsoft.windowsappsdk/2.2.0/lib/App.winmd"]
                },
                {
                  "name": "Dependency",
                  "version": "1.0.0",
                  "winmds": []
                }
              ]
            }
            """.trimIndent(),
        )

        val parsed = WinAppRestoreLockfileReader.read(lockfile)

        assertEquals(3, parsed.schema)
        assertEquals("C:\\nuget\\packages", parsed.nugetCacheDirectory.toString())
        assertEquals(listOf("Microsoft.WindowsAppSDK", "Dependency"), parsed.packages.map { it.name })
        assertEquals(1, parsed.winmdFiles.size)
    }

    @Test
    fun unknown_lockfile_schema_is_rejected() {
        val root = Files.createTempDirectory("kotlin-winrt-winapp-lock-schema-")
        val lockfile = root.resolve("winmds.lock.json")
        Files.writeString(lockfile, """{"schema":4,"packages":[]}""")

        val failure = runCatching { WinAppRestoreLockfileReader.read(lockfile) }.exceptionOrNull()

        assertTrue(failure is GradleException)
        assertTrue(failure?.message.orEmpty().contains("schema 4"))
    }

    @Test
    fun projection_inputs_exclude_tooling_and_explicit_runtime_only_packages() {
        val root = Files.createTempDirectory("kotlin-winrt-winapp-projection-lock-")
        val cacheRoot = root.resolve("packages").toAbsolutePath()
        val projected = createPackage(cacheRoot, "Projected", dependencies = listOf("Shared"))
        val shared = createPackage(cacheRoot, "Shared")
        val runtimeOnly = createPackage(cacheRoot, "Runtime.Only", dependencies = listOf("Runtime.Transitive"))
        val runtimeTransitive = createPackage(cacheRoot, "Runtime.Transitive")
        val sdk = createPackage(cacheRoot, "Microsoft.Windows.SDK.CPP")
        val lockfile = root.resolve("winmds.lock.json")
        Files.writeString(
            lockfile,
            """
            {
              "schema": 3,
              "nuget_cache_dir": ${jsonString(cacheRoot.toString())},
              "packages": [
                {"name":"Microsoft.Windows.SDK.CPP","version":"10.0.26100.1742","winmds":[${jsonString(sdk.toString())}]},
                {"name":"Projected","version":"1.0.0","winmds":[${jsonString(projected.toString())}]},
                {"name":"Runtime.Only","version":"1.0.0","winmds":[${jsonString(runtimeOnly.toString())}]},
                {"name":"Runtime.Transitive","version":"1.0.0","winmds":[${jsonString(runtimeTransitive.toString())}]},
                {"name":"Shared","version":"1.0.0","winmds":[${jsonString(shared.toString())}]}
              ]
            }
            """.trimIndent(),
        )

        val inputs = readWinAppProjectionWinmdFiles(
            lockFiles = listOf(lockfile.toFile()),
            rootPackageSpecs = listOf("Projected@1.0.0"),
        )

        assertEquals(listOf(projected, shared), inputs)
    }

    @Test
    fun restored_package_roots_come_from_the_lockfile_cache_and_exclude_tooling() {
        val root = Files.createTempDirectory("kotlin-winrt-winapp-package-roots-")
        val cacheRoot = root.resolve("packages").toAbsolutePath()
        val packageRoot = cacheRoot.resolve("sample.package/1.2.3")
        Files.createDirectories(packageRoot)
        val lockfile = root.resolve("winmds.lock.json")
        Files.writeString(
            lockfile,
            """
            {
              "schema": 3,
              "nuget_cache_dir": ${jsonString(cacheRoot.toString())},
              "packages": [
                {"name":"Microsoft.Windows.SDK.BuildTools","version":"10.0.26100.4654","winmds":[]},
                {"name":"Microsoft.Windows.SDK.BuildTools.MSIX","version":"1.7.251221100","winmds":[]},
                {"name":"Sample.Package","version":"1.2.3","winmds":[]}
              ]
            }
            """.trimIndent(),
        )

        val roots = readWinAppRestoredPackageRoots(
            lockFiles = listOf(lockfile.toFile()),
            rootPackageSpecs = listOf(
                "Microsoft.Windows.SDK.BuildTools@10.0.26100.1742",
                "Sample.Package@1.2.3",
            ),
        )

        assertEquals(listOf(packageRoot), roots)
    }

    @Test
    fun runtime_root_version_must_match_the_declared_exact_version() {
        val root = Files.createTempDirectory("kotlin-winrt-winapp-runtime-version-")
        val cacheRoot = root.resolve("packages").toAbsolutePath()
        Files.createDirectories(cacheRoot.resolve("sample.package/2.0.0"))
        val lockfile = root.resolve("winmds.lock.json")
        Files.writeString(
            lockfile,
            """
            {
              "schema": 3,
              "nuget_cache_dir": ${jsonString(cacheRoot.toString())},
              "packages": [
                {"name":"Sample.Package","version":"2.0.0","winmds":[]}
              ]
            }
            """.trimIndent(),
        )

        val failure = runCatching {
            readWinAppRestoredPackageRoots(
                lockFiles = listOf(lockfile.toFile()),
                rootPackageSpecs = listOf("Sample.Package@1.0.0"),
            )
        }.exceptionOrNull()

        assertTrue(failure is GradleException)
        assertTrue(failure?.message.orEmpty().contains("runtime asset staging requires Sample.Package@1.0.0"))
    }

    @Test
    fun missing_declared_runtime_root_is_rejected() {
        val root = Files.createTempDirectory("kotlin-winrt-winapp-runtime-missing-")
        val lockfile = root.resolve("winmds.lock.json")
        Files.writeString(lockfile, """{"schema":3,"packages":[]}""")

        val failure = runCatching {
            readWinAppRestoredPackageRoots(
                lockFiles = listOf(lockfile.toFile()),
                rootPackageSpecs = listOf("Sample.Package@1.0.0"),
            )
        }.exceptionOrNull()

        assertTrue(failure is GradleException)
        assertTrue(failure?.message.orEmpty().contains("Sample.Package@1.0.0"))
    }

    private fun jsonString(value: String): String =
        "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

    private fun createPackage(
        cacheRoot: java.nio.file.Path,
        packageId: String,
        dependencies: List<String> = emptyList(),
        version: String = "1.0.0",
    ): java.nio.file.Path {
        val packageRoot = cacheRoot.resolve(packageId.lowercase()).resolve(version)
        val metadata = packageRoot.resolve("metadata")
        Files.createDirectories(metadata)
        val winmd = metadata.resolve("$packageId.winmd")
        Files.writeString(winmd, "metadata")
        Files.writeString(
            packageRoot.resolve("$packageId.nuspec"),
            """
            <package>
              <metadata>
                <id>$packageId</id>
                <version>$version</version>
                <dependencies>
            ${dependencies.joinToString(separator = "\n") { dependency ->
                "      <dependency id=\"$dependency\" version=\"[1.0.0]\" />"
            }}
                </dependencies>
              </metadata>
            </package>
            """.trimIndent(),
        )
        return winmd
    }
}
