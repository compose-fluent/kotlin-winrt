package io.github.composefluent.winrt.ide.hotreload

import io.github.composefluent.winrt.ide.gradle.*
import org.junit.Test
import org.junit.Assert.*
import java.nio.file.Files

class WinRTPreviewLaunchCacheTest {
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
            assertTrue(cache.reusable(cache.inputs()))
            val externalTime = Files.getLastModifiedTime(external)
            Files.writeString(external, "library-two")
            Files.setLastModifiedTime(external, externalTime)
            assertEquals("External artifact is not part of the project source scan", initial, cache.inputs())
            assertTrue("Routing hint must never authorize reuse", cache.hasCandidate())
            assertFalse("Changed resolved artifact invalidates even with preserved size/time", cache.reusable(cache.inputs()))
            Files.writeString(external, "library-one")
            assertTrue(cache.reusable(cache.inputs()))
            val timestamp = Files.getLastModifiedTime(source)
            Files.writeString(source, "class Changed!")
            Files.setLastModifiedTime(source, timestamp)
            assertFalse("Dependency source edits with preserved timestamps invalidate", cache.reusable(cache.inputs()))
            Files.writeString(source, "class Greeting")
            assertTrue(cache.reusable(cache.inputs()))
            Files.writeString(dep.resolve("Added.kt"), "class Added")
            assertFalse("New dependency files invalidate", cache.reusable(cache.inputs()))
            Files.delete(dep.resolve("Added.kt"))
            Files.writeString(config, "include(\":app\")")
            assertFalse("Build configuration invalidates", cache.reusable(cache.inputs()))
            cache.remember(cache.inputs())
            Files.write(exe, byteArrayOf(1, 2))
            assertFalse("Changed deployment requires Gradle validation", cache.reusable(cache.inputs()))
            cache.remember(cache.inputs())
            Files.writeString(deployed.resolve("AppxManifest.xml"), "<Application EntryPoint=\"App\"/>")
            assertFalse("UWP must use activation, never CreateProcess", cache.reusable(cache.inputs()))
        } finally {
            Files.deleteIfExists(external)
            Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
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
            assertTrue("Basic preview does not wait for changed project code", cache.reusable(cache.inputs()))
            val originalInputs = cache.inputs()
            Files.writeString(config, "sdkVersion=2")
            assertFalse(cache.reusable(cache.inputs()))
            assertFalse("A late first frame cannot bless configuration edited during compilation",
                cache.rememberIfUnchanged(originalInputs))
            assertFalse(cache.reusable(cache.inputs()))
            assertTrue(cache.rememberIfUnchanged(cache.inputs()))
            Files.write(exe, byteArrayOf(2, 3))
            assertFalse(cache.reusable(cache.inputs()))
        } finally { Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }
}
