package io.github.composefluent.winrt.ide.preview

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class WinUIXamlPreviewStartupTest {
    @Test fun editorAndCanvasShareSourceOwnershipAndLaunchOrder() {
        val root = java.nio.file.Path.of("preview-project").toAbsolutePath()
        val first = io.github.composefluent.winrt.ide.gradle.WinUIXamlHotReloadLaunchData(
            "first", "first.exe", root.toString(), "first-preview.exe", "native")
        val noPreview = first.copy(taskName = "ordinary", executable = "ordinary.exe", previewExecutable = "")
        val module = io.github.composefluent.winrt.ide.gradle.WinRTModuleData(":app", root.toString(),
            root.resolve("build").toString(), "2.4", "sdk", emptyList(), emptyList(), emptyList(), emptyList(),
            hotReloadLaunches = listOf(noPreview, first, first.copy(taskName = "same-executable")))
        val dependency = module.copy(projectPath = ":controls", projectDirectory = root.resolve("controls").toString(),
            hotReloadLaunches = emptyList())
        assertEquals(dependency, previewSourceModule(root.resolve("controls/Page.xaml").toString(), listOf(module, dependency)))
        assertEquals(null, previewSourceModule(root.resolveSibling("outside/Page.xaml").toString(), listOf(module)))
        assertEquals(listOf(first), previewProjectLaunches(module, null, true))
        assertEquals(listOf(noPreview, first), previewProjectLaunches(module, null, false))
        assertEquals(listOf(first), previewProjectLaunches(dependency, first, true))
    }
    @Test fun nativeBuildNeverStartsCompetingSdkBuild() = runBlocking {
        val result = CompletableDeferred<Boolean>()
        val events = mutableListOf<String>()
        val startup = launch {
            startPreferredXamlPreview(true, false, { events += "native" },
                { result.await() }, { events += "sdk" })
        }
        yield()
        assertEquals(listOf("native"), events)
        result.complete(true)
        startup.join()
        assertEquals(listOf("native"), events)
    }
    @Test fun failedNativeFallsBackOnce() = runBlocking {
        val events = mutableListOf<String>()
        startPreferredXamlPreview(true, false, { events += "native" },
            { false }, { events += "sdk" })
        assertEquals(listOf("native", "sdk"), events)
    }
    @Test fun cancelledNativeNeverStartsFallback() = runBlocking {
        var active = true
        val events = mutableListOf<String>()
        startPreferredXamlPreview(true, false, { events += "native" },
            { active = false; false }, { events += "sdk" }, { active })
        assertEquals(listOf("native"), events)
    }
    @Test fun basicOnlyAndConnectedOrCancelledDoNotLaunchUnneededHosts() = runBlocking {
        val events = mutableListOf<String>()
        for ((wants, connected, active) in listOf(Triple(false, false, true),
                Triple(true, true, true), Triple(true, false, false))) {
            startPreferredXamlPreview(wants, connected, { events += "native" },
                { true }, { events += "sdk" }, { active })
        }
        assertEquals(listOf("sdk"), events)
    }
}
