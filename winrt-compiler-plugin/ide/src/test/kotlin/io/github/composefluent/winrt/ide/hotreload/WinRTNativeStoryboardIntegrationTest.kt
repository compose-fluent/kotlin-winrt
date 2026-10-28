@file:OptIn(io.github.composefluent.winrt.runtime.InternalWinRTApi::class)

package io.github.composefluent.winrt.ide.hotreload

import io.github.composefluent.winrt.runtime.*
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Uses the generated SDK adapter, just as CsWinRT projects Storyboard and the
 * template's FrameworkElement resources. No substitute animation renderer. */
class WinRTNativeStoryboardIntegrationTest {
    @Test fun repeated_template_clocks_play_only_the_selected_native_instance() {
        val configured = System.getProperty("winrt.ide.nativeRestartProject")
        assumeNotNull(configured)
        val project = Path.of(configured).toAbsolutePath().normalize()
        val executable = project.resolve("app/build/kotlin-winrt/application-layout/mingwX64_main_debugExecutable/package/native-project-preview.exe")
        require(Files.isRegularFile(executable))
        val directory = Files.createTempDirectory(project.resolve("app/build"), "storyboard-acceptance-")
        val process = ProcessBuilder(executable.toString(), "--kotlin-winrt-xaml-preview").directory(executable.parent.toFile()).apply {
            environment()["KOTLIN_WINRT_HOT_RELOAD_DIRECTORY"] = directory.toString()
            environment()["KOTLIN_WINRT_XAML_PREVIEW"] = "1"
            environment()["KOTLIN_WINRT_RUNTIME_ASSETS_ROOT"] = executable.parent.toString()
            redirectErrorStream(true); redirectOutput(directory.resolve("host.log").toFile())
        }.start()
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45)
            var client: WinUIXamlHotReloadClient? = null
            while (client == null) {
                check(process.isAlive && System.nanoTime() < deadline) { "The Native preview did not publish its endpoint." }
                client = WinUIXamlHotReloadClient.discover(directory, executable.toString()).singleOrNull()
                if (client == null) Thread.sleep(50)
            }
            client.use { connection ->
                while (connection.request().roots.none { it.className == WinUIXamlHotReloadProtocol.PREVIEW_CLASS }) {
                    check(System.nanoTime() < deadline); Thread.sleep(50)
                }
                val marker = WinUIXamlHotReloadProtocol.PREVIEW_SOURCE_KEY
                val markup = """<Grid xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" Background="White">
                  <Grid.Resources><DataTemplate x:Key="Row"><Grid x:Name="Tile" Background="Red" Width="120" Height="80">
                    <Grid.Resources><x:String x:Key="$marker">repeated-source</x:String><Storyboard x:Key="Fade"><DoubleAnimation Storyboard.TargetName="Tile" Storyboard.TargetProperty="Opacity" From="1" To="0.25" Duration="0:0:1"/></Storyboard></Grid.Resources>
                    <TextBlock Text="Repeated template"/></Grid></DataTemplate></Grid.Resources>
                  <StackPanel><ContentControl x:Name="First" Content="one" ContentTemplate="{StaticResource Row}"/><ContentControl x:Name="Second" Content="two" ContentTemplate="{StaticResource Row}"/></StackPanel>
                </Grid>"""
                val request = WinUIXamlInspectionRequest(WinUIXamlHotReloadProtocol.PREVIEW_CLASS, "", readProperties = false)
                fun inspect(value: WinUIXamlInspectionRequest): WinUIXamlVisualSnapshot {
                    val reply = connection.inspect(value)
                    assertEquals(reply.message, WinUIXamlHotReloadProtocol.APPLIED, reply.status)
                    return requireNotNull(reply.inspection)
                }
                val tree = inspect(request.copy(previewMarkup = markup))
                val instances = tree.nodes.filter { it.sourceId == "repeated-source" }
                assertEquals(2, instances.size)
                fun opacity(index: Int) = inspect(request.copy(capture = false, readTree = false, readProperties = true,
                    selectedPath = instances[index].path)).properties.single { it.name == "Opacity" }.value.toDouble()
                val command = WinUIXamlPreviewAnimation("", "Fade", 1000, instancePath = instances[1].path,
                    sourceId = "repeated-source", instanceTreeToken = tree.treeToken)
                val frame = inspect(request.copy(readTree = false, animation = command))
                assertNotNull(frame.image)
                assertEquals(1.0, opacity(0), .001)
                assertEquals(.25, opacity(1), .001)
                inspect(request.copy(readTree = false, animation = command.copy(action = "Stop")))
                assertEquals(1.0, opacity(1), .001)
                inspect(request.copy(readTree = false, animation = command.copy(instancePath = instances[0].path)))
                assertEquals(.25, opacity(0), .001)
                assertEquals(1.0, opacity(1), .001)
                inspect(request.copy(previewMarkup = markup))
                val expired = connection.inspect(request.copy(readTree = false, animation = command))
                assertNotEquals(WinUIXamlHotReloadProtocol.APPLIED, expired.status)
                assertEquals(1.0, opacity(0), .001)
                assertEquals(1.0, opacity(1), .001)
                println("Native repeated-template playback: second only, stop, first only, stale command rejected; sibling opacity preserved.")
            }
        } finally {
            process.destroy()
            if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroyForcibly().waitFor(10, TimeUnit.SECONDS)
        }
    }
}
