package io.github.composefluent.winrt.ide.hotreload

import io.github.composefluent.winrt.ide.gradle.*
import org.junit.Test
import org.junit.Assert.*
import java.nio.file.Files

class WinRTPreviewLaunchCacheTest {
    @Test fun packagedIdentityMatchesActualToolchainArchive() {
        val type = WinRTPreviewLaunchCache::class.java
        val identity = requireNotNull(type.getResourceAsStream("/templates/toolchain-sha256.txt"))
            .bufferedReader().use { it.readText().trim() }
        val hash = java.security.MessageDigest.getInstance("SHA-256")
        requireNotNull(type.getResourceAsStream("/templates/toolchain.zip")).use { stream ->
            val buffer = ByteArray(65536)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                hash.update(buffer, 0, count)
            }
        }
        assertEquals("A changed archive must regenerate its packaged identity",
            java.util.HexFormat.of().formatHex(hash.digest()), identity)
    }

    private fun checked(cache: WinRTPreviewLaunchCache): Boolean = kotlinx.coroutines.runBlocking {
        val result = cache.checkedInputs()
        assertEquals(cache.inputs(), result.first)
        assertEquals("Concurrent validation must retain receipt compatibility", cache.reusable(result.first), result.second)
        result.second
    }
    @Test fun parallelFileHashesPreserveOrderingBoundariesAndLargeFiles() {
        val root = Files.createTempDirectory("preview-hash-compatibility")
        try {
            val files = (0..9).map { index -> Files.write(root.resolve("file$index.bin"),
                ByteArray(if (index == 3) 4 * 1024 * 1024 + 7 else index * 33) { (it % 251).toByte() }) }
            val missing = root.resolve("missing")
            val module = WinRTModuleData(":app", root.toString(), root.resolve("build").toString(), "2.4", "sdk", emptyList(), emptyList(), emptyList(), emptyList())
            val cache = WinRTPreviewLaunchCache(root, listOf(module), module, WinUIXamlHotReloadLaunchData("run", root.resolve("app.exe").toString(), root.toString()))
            val legacy = java.security.MessageDigest.getInstance("SHA-256")
            fun add(value: String) { legacy.update(value.toByteArray()); legacy.update(0.toByte()) }
            add("missing:$missing")
            add("content-fingerprint-v2")
            files.sorted().forEach { add(it.toString()); legacy.update(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(it))); legacy.update(0.toByte()) }
            val expected = legacy.digest().joinToString("") { "%02x".format(it) }
            val fingerprint = WinRTPreviewLaunchCache::class.java.getDeclaredMethod("fingerprint", List::class.java,
                Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, List::class.java)
            fingerprint.isAccessible = true
            assertEquals("Parallel file hashing must match ordered independent file digests", expected,
                fingerprint.invoke(cache, listOf(root, missing), true, false, false, emptyList<java.nio.file.Path>()))
        } finally { Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }
    @Test fun dependencyEditsConfigurationChangesAndDeploymentChangesRequireRebuild() {
        val root = Files.createTempDirectory("preview-launch-cache")
        val external = Files.createTempFile("preview-external", ".klib")
        Files.writeString(external, "library-one")
        try {
            val app = Files.createDirectories(root.resolve("app"))
            val dep = Files.createDirectories(root.resolve("controls/src"))
            val source = Files.writeString(dep.resolve("Greeting.kt"), "class Greeting")
            val build = Files.createDirectories(app.resolve("build"))
            val deployed = Files.createDirectories(build.resolve("deployed"))
            val exe = Files.write(deployed.resolve("preview.exe"), byteArrayOf(1))
            Files.writeString(deployed.resolve("AppxManifest.xml"), "<Application EntryPoint=\"Windows.FullTrustApplication\"/>")
            val config = Files.writeString(root.resolve("settings.gradle.kts"), "include(\":app\", \":controls\")")
            val launch = WinUIXamlHotReloadLaunchData("runWinAppPackageMingwX64MainDebugExecutable", exe.toString(), deployed.toString(), exe.toString(), "native")
            val module = WinRTModuleData(":app", app.toString(), build.toString(), "2.4", "sdk", emptyList(), emptyList(), emptyList(), emptyList())
            val cache = WinRTPreviewLaunchCache(root, listOf(module), module, launch)
            assertFalse("Old deployments without resolved inputs must use Gradle", cache.supported())
            io.github.composefluent.winrt.ide.model.WinRTPreviewBuildInputs.write(
                deployed.parent.resolve(io.github.composefluent.winrt.ide.model.WinRTPreviewBuildInputs.FILE_NAME), listOf(external))
            val initial = cache.inputs()
            assertFalse(cache.hasCandidate())
            assertFalse(cache.reusable(initial))
            cache.remember(initial)
            assertTrue(cache.hasCandidate())
            assertTrue(checked(cache))
            var warmed = 0
            fun warmingCheck() = kotlinx.coroutines.runBlocking { cache.checkedInputs { warmed++ }.second }
            assertTrue(warmingCheck())
            assertEquals("A matching Native deployment can initialize during validation", 1, warmed)
            val deploymentIdentity = cache.connectedDeploymentIdentity()
            assertNotNull(deploymentIdentity)
            assertEquals(deploymentIdentity, cache.validatedDeploymentIdentity)
            val externalTime = Files.getLastModifiedTime(external)
            Files.writeString(external, "library-two")
            Files.setLastModifiedTime(external, externalTime)
            assertEquals("External artifact is not part of the project source scan", initial, cache.inputs())
            assertTrue("Routing hint must never authorize reuse", cache.hasCandidate())
            warmed = 0
            assertFalse("A warm candidate must not authorize changed dependency contents", warmingCheck())
            assertEquals("Routing is independent from final dependency-content validation", 1, warmed)
            assertFalse("Changed resolved artifact invalidates even with preserved size/time", checked(cache))
            assertNull(cache.validatedDeploymentIdentity)
            assertNotEquals(deploymentIdentity, cache.connectedDeploymentIdentity())
            Files.writeString(external, "library-one")
            assertTrue(checked(cache))
            val timestamp = Files.getLastModifiedTime(source)
            Files.writeString(source, "class Changed!")
            Files.setLastModifiedTime(source, timestamp)
            warmed = 0
            assertFalse("A warm candidate must not authorize changed source contents", warmingCheck())
            assertEquals(1, warmed)
            assertFalse("Dependency source edits with preserved timestamps invalidate", checked(cache))
            assertEquals("Theme switches retain the connected compiled baseline", deploymentIdentity, cache.connectedDeploymentIdentity())
            Files.writeString(source, "class Greeting")
            assertTrue(checked(cache))
            Files.writeString(dep.resolve("Added.kt"), "class Added")
            assertFalse("New dependency files invalidate", checked(cache))
            Files.delete(dep.resolve("Added.kt"))
            Files.writeString(config, "include(\":app\")")
            assertFalse("Build configuration invalidates", checked(cache))
            cache.remember(cache.inputs())
            val currentDeployment = cache.connectedDeploymentIdentity()
            Files.write(exe, byteArrayOf(1, 2))
            warmed = 0
            assertFalse(warmingCheck())
            assertEquals("Changed deployed files cannot even initialize speculatively", 0, warmed)
            assertFalse("Changed deployment requires Gradle validation", checked(cache))
            assertNotEquals(currentDeployment, cache.connectedDeploymentIdentity())
            cache.remember(cache.inputs())
            Files.writeString(deployed.resolve("AppxManifest.xml"), "<Application EntryPoint=\"App\"/>")
            assertFalse("UWP must use activation, never CreateProcess", checked(cache))
        } finally {
            Files.deleteIfExists(external)
            Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    @Test fun unpackagedNativeCacheRequiresResolvedInputsAndAnIsolatedDirectLaunch() {
        val root = Files.createTempDirectory("preview-unpackaged-cache")
        try {
            val build = Files.createDirectories(root.resolve("build"))
            val deployed = Files.createDirectories(build.resolve("application-preview/variant/Unpackaged"))
            val exe = Files.write(deployed.resolve("preview.exe"), byteArrayOf(1))
            val dependency = Files.writeString(build.resolve("dependency.klib"), "library")
            val launch = WinUIXamlHotReloadLaunchData("runWinAppNativeMingwX64MainDebugExecutable",
                exe.toString(), deployed.toString(), exe.toString(), "native", isDirectPreview = true)
            val module = WinRTModuleData(":app", root.toString(), build.toString(), "2.4", "sdk", emptyList(), emptyList(), emptyList(), emptyList())
            val cache = WinRTPreviewLaunchCache(root, listOf(module), module, launch)
            assertFalse(cache.supported())
            io.github.composefluent.winrt.ide.model.WinRTPreviewBuildInputs.write(
                deployed.parent.resolve(io.github.composefluent.winrt.ide.model.WinRTPreviewBuildInputs.FILE_NAME), listOf(dependency))
            assertTrue(cache.supported())
            cache.remember(cache.inputs())
            assertTrue(checked(cache))
            Files.writeString(dependency, "changed")
            assertFalse(checked(cache))
            assertFalse(WinRTPreviewLaunchCache(root, listOf(module), module, launch.copy(isDirectPreview = false)).supported())
        } finally { Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }

    @Test fun overlappingRootsKeepExplicitGeneratedSourcesAndMissingInputs() {
        val root = Files.createTempDirectory("preview-overlap")
        try {
            val source = Files.createDirectories(root.resolve("src"))
            Files.writeString(source.resolve("Page.xaml"), "<Grid/>")
            val generated = Files.createDirectories(root.resolve("build/generated"))
            val generatedFile = Files.writeString(generated.resolve("Control.kt"), "class Original")
            val missing = root.resolve("missing.xaml")
            val module = WinRTModuleData(":app", root.toString(), root.resolve("build").toString(), "2.4", "sdk",
                listOf(WinRTSourceSetData("main", listOf(source.toString(), generated.toString()), emptyList(), emptyList())),
                emptyList(), emptyList(), listOf(missing.toString()))
            val launch = WinUIXamlHotReloadLaunchData("run", root.resolve("build/app.exe").toString(), root.toString())
            val cache = WinRTPreviewLaunchCache(root, listOf(module), module, launch)
            val legacy = module.copy(sourceSets = module.sourceSets.map { it.copy(generatedKotlinRoots = null) })
            val legacyCache = WinRTPreviewLaunchCache(root, listOf(legacy), legacy, launch)
            assertEquals("Legacy workspace graphs retain conservative source validation", legacyCache.inputs(), legacyCache.buildInputs())
            val before = cache.inputs()
            Files.writeString(generatedFile, "class Changed")
            assertNotEquals("Explicit source root under build must still be hashed", before, cache.inputs())
            Files.writeString(generatedFile, "class Original")
            assertEquals(before, cache.inputs())
            Files.writeString(missing, "<Page/>")
            assertNotEquals("An explicit missing input becoming present invalidates", before, cache.inputs())
        } finally { Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }

    @Test fun firstBuildRecordsGeneratedChangesButNeverUserEdits() {
        val root = Files.createTempDirectory("preview-first-build")
        try {
            val build = Files.createDirectories(root.resolve("build"))
            val deployed = Files.createDirectories(build.resolve("deployed"))
            val exe = Files.write(deployed.resolve("preview.exe"), byteArrayOf(1))
            Files.writeString(deployed.resolve("AppxManifest.xml"), "<Application EntryPoint=\"Windows.FullTrustApplication\"/>")
            val dependency = Files.writeString(build.resolve("dependency.klib"), "library")
            io.github.composefluent.winrt.ide.model.WinRTPreviewBuildInputs.write(
                deployed.parent.resolve(io.github.composefluent.winrt.ide.model.WinRTPreviewBuildInputs.FILE_NAME), listOf(dependency))
            val generated = build.resolve("generated")
            val custom = Files.createDirectories(build.resolve("custom-source"))
            val source = Files.writeString(root.resolve("User.kt"), "class User")
            val customSource = Files.writeString(custom.resolve("Custom.kt"), "class Custom")
            val module = WinRTModuleData(":app", root.toString(), build.toString(), "2.4", "sdk",
                listOf(WinRTSourceSetData("main", listOf(generated.toString(), custom.toString()), emptyList(), emptyList(), listOf(generated.toString()))),
                emptyList(), emptyList(), emptyList())
            val launch = WinUIXamlHotReloadLaunchData("runWinAppPackageNative", exe.toString(), deployed.toString(), exe.toString(), "native")
            val cache = WinRTPreviewLaunchCache(root, listOf(module), module, launch)
            val before = cache.buildInputs()
            Files.createDirectories(generated)
            val output = Files.writeString(generated.resolve("Generated.kt"), "class Generated")
            assertTrue("First generation must not discard a completed build receipt", cache.rememberAfterBuild(before))
            assertTrue(checked(cache))
            Files.writeString(output, "class Tampered")
            assertFalse("Later edits to generated outputs still invalidate", checked(cache))
            Files.writeString(source, "class Edited")
            assertFalse("User edits during compilation cannot be blessed", cache.rememberAfterBuild(before))
            Files.writeString(source, "class User")
            Files.writeString(customSource, "class EditedCustom")
            assertFalse("Unowned sources under build are ordinary inputs", cache.rememberAfterBuild(before))
        } finally { Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }

    @Test fun sdkHostSurvivesProjectSourceEditsButInvalidatesBuildConfiguration() {
        val root = Files.createTempDirectory("sdk-preview-cache")
        try {
            val build = Files.createDirectories(root.resolve("build/host"))
            val exe = Files.write(build.resolve("preview.exe"), byteArrayOf(1))
            val refs = Files.writeString(build.resolve("references.json"), "{}")
            val config = Files.writeString(root.resolve("build.gradle.kts"), "sdkVersion=1")
            val source = Files.writeString(root.resolve("Page.xaml"), "<Grid/>")
            val sdk = WinRTStaticPreviewData("runWinRTXamlSdkPreview", exe.toString(), build.toString(), refs.toString())
            val module = WinRTModuleData(":app", root.toString(), root.resolve("build").toString(), "2.4", "sdk", emptyList(), emptyList(), emptyList(), emptyList(), staticPreview = sdk)
            val cache = WinRTPreviewLaunchCache(root, listOf(module), module, sdk.launch())
            cache.remember(cache.inputs())
            Files.writeString(source, "<Grid><Button/></Grid>")
            Files.writeString(root.resolve("Component.kt"), "class Component")
            assertTrue("Basic preview does not wait for changed project code", checked(cache))
            val originalInputs = cache.inputs()
            Files.writeString(config, "sdkVersion=2")
            assertFalse(checked(cache))
            assertFalse("A late first frame cannot bless configuration edited during compilation",
                cache.rememberIfUnchanged(originalInputs))
            assertFalse(checked(cache))
            assertTrue(cache.rememberIfUnchanged(cache.inputs()))
            Files.write(exe, byteArrayOf(2, 3))
            assertFalse(checked(cache))
        } finally { Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }
}
