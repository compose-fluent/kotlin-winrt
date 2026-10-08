package io.github.composefluent.winrt.ide.preview

import io.github.composefluent.winrt.ide.hotreload.WinRTHotReloadClient
import io.github.composefluent.winrt.runtime.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import javax.imageio.ImageIO

/** Opt-in acceptance against a built, running WinUI development host. No mock
 * controls, screenshots of another application, or substituted layout engine. */
class WinRTPreviewRuntimeTest {
    @Test fun sdk_designer_renders_without_application_classes_and_reloads_source_resources() {
        val folder = System.getProperty("winrt.ide.sdkPreviewSession")
        assumeTrue("Supply the SDK-only WinUI designer session", !folder.isNullOrEmpty())
        connect(folder!!).use { client ->
            val before = client.request()
            val root = before.roots.single { it.className == WinRTXamlHotReloadProtocol.PREVIEW_CLASS }
            assertEquals(listOf(root), before.roots)
            fun document(color: String, text: String) = WinRTXamlDesignDocument.prepare("""<Window xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" xmlns:d="http://schemas.microsoft.com/expression/blend/2008" xmlns:local="using:broken.project" x:Class="broken.project.Window">
              <StackPanel Background="{ThemeResource ApplicationPageBackgroundThemeBrush}" Spacing="20" Padding="24">
              <TextBlock x:Name="Greeting" Text="{x:Bind MissingTitle}" d:Text="$text" FontSize="28" Foreground="{StaticResource PreviewBrush}"/>
              <Button x:Name="Action" Content="SDK designer" Style="{StaticResource SubtleButtonStyle}" Click="MissingEvent"/>
              <local:Custom x:Name="Custom" Width="160" Height="40"/>
              <ListView><d:ListView.Items><d:TextBlock Text="Design item"/></d:ListView.Items></ListView>
              </StackPanel></Window>""",
                """<Application xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"><Application.Resources><ResourceDictionary Source="Styles/Colors.xaml"/></Application.Resources></Application>""",
                resource = { uri, _ -> if (uri == "Styles/Colors.xaml") WinRTXamlDesignResource("""<ResourceDictionary xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"><SolidColorBrush x:Key="PreviewBrush" Color="$color"/></ResourceDictionary>""", uri) else null },
                sdkType = { _, _ -> false }) { _, type, member -> type == "Button" && member == "Click" }
            val first = client.inspect(WinRTXamlInspectionRequest(root.className, root.resourcePath,
                previewMarkup = document("Tomato", "No application build").markup, width = 640, height = 480, theme = "Light"))
            assertEquals(first.message, WinRTXamlHotReloadProtocol.APPLIED, first.status)
            val view = first.inspection!!
            assertTrue(view.nodes.size > 8)
            assertTrue(view.nodes.any { it.name == "Custom" && it.typeName.endsWith("Border") })
            val greeting = view.nodes.single { it.name == "Greeting" }
            val values = client.inspect(WinRTXamlInspectionRequest(root.className, root.resourcePath, selectedPath = greeting.path, capture = false))
            assertEquals("No application build", values.inspection!!.properties.single { it.name == "Text" }.value)
            val image = view.image!!
            assertBoundedPixels(image)
            System.getProperty("winrt.ide.previewOutput")?.let { output ->
                val path = Path.of(output); Files.createDirectories(path.parent); ImageIO.write(bgraImage(image), "png", path.toFile())
            }
            val next = client.inspect(WinRTXamlInspectionRequest(root.className, root.resourcePath,
                previewMarkup = document("Blue", "Unsaved source edit").markup, width = 320, height = 240, theme = "Dark"))
            assertEquals(next.message, WinRTXamlHotReloadProtocol.APPLIED, next.status)
            val nextGreeting = next.inspection!!.nodes.single { it.name == "Greeting" }
            val nextValues = client.inspect(WinRTXamlInspectionRequest(root.className, root.resourcePath, selectedPath = nextGreeting.path, capture = false))
            assertEquals("Unsaved source edit", nextValues.inspection!!.properties.single { it.name == "Text" }.value)
            assertFalse(image.pixels.contentEquals(next.inspection!!.image!!.pixels))
            assertEquals(root.version, next.roots.single().version)
            val invalid = client.inspect(WinRTXamlInspectionRequest(root.className, root.resourcePath,
                previewMarkup = "<ThisIsNotAControl xmlns=\"http://schemas.microsoft.com/winfx/2006/xaml/presentation\"/>"))
            assertEquals(WinRTXamlHotReloadProtocol.REJECTED, invalid.status)
            assertEquals("Unsaved source edit", client.inspect(WinRTXamlInspectionRequest(root.className, root.resourcePath, selectedPath = nextGreeting.path, capture = false)).inspection!!.properties.single { it.name == "Text" }.value)
        }
    }
    @Test fun real_winui_templates_pixels_and_effective_properties_match_the_static_document() {
        val folder = System.getProperty("winrt.ide.previewSession")
        assumeTrue("Supply a real WinUI preview session", !folder.isNullOrEmpty())
        connect(folder!!).use { client ->
            val before = client.request()
            assertEquals(before.message, WinRTXamlHotReloadProtocol.APPLIED, before.status)
            val root = before.roots.single { it.className == WinRTXamlHotReloadProtocol.PREVIEW_CLASS }
            assertFalse("User main must not instantiate its window", before.roots.any { it.className == "sample.hello.MainWindow" })
            val document = WinRTXamlDesignDocument.prepare("""<Window xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" xmlns:controls="using:sample.controls" x:Class="sample.Window">
              <StackPanel Background="{ThemeResource ApplicationPageBackgroundThemeBrush}" Spacing="20" Padding="24">
              <TextBlock x:Name="Greeting" Text="Real WinUI Preview" FontSize="28"/>
              <Button x:Name="Action" Content="Preview button" Style="{StaticResource SubtleButtonStyle}"/>
              <controls:GreetingControl x:Name="CustomGreeting"/>
              </StackPanel></Window>""")
            val response = client.inspect(WinRTXamlInspectionRequest(root.className, root.resourcePath,
                previewMarkup = document.markup, width = 640, height = 480, theme = "Light"))
            assertEquals(response.message, WinRTXamlHotReloadProtocol.APPLIED, response.status)
            val view = response.inspection!!
            assertTrue("Real template children must be present", view.nodes.size > 5)
            // A dependency UserControl has its own namescope and also names its
            // inner TextBlock Greeting. Visual paths must keep them distinct.
            val greeting = view.nodes.filter { it.name == "Greeting" }.minBy { it.path.size }
            val button = view.nodes.single { it.name == "Action" }
            val custom = view.nodes.single { it.name == "CustomGreeting" }
            assertTrue("Dependency XAML registrars must initialize their named content", view.nodes.any {
                it.name == "Greeting" && it.path.size > custom.path.size && it.path.take(custom.path.size) == custom.path
            })
            assertTrue(greeting.bounds.width > 0 && greeting.bounds.height > 0)
            assertTrue(view.nodes.any { it.path.size > button.path.size && it.path.take(button.path.size) == button.path })
            val selected = client.inspect(WinRTXamlInspectionRequest(root.className, root.resourcePath, selectedPath = greeting.path))
            assertEquals(selected.message, WinRTXamlHotReloadProtocol.APPLIED, selected.status)
            assertEquals("Real WinUI Preview", selected.inspection!!.properties.single { it.name == "Text" }.value)
            assertEquals(root.version, selected.roots.single { it.className == root.className }.version)
            val image = selected.inspection!!.image!!
            assertEquals(640.0, view.nodes.first().bounds.width, 0.1)
            assertEquals(480.0, view.nodes.first().bounds.height, 0.1)
            assertBoundedPixels(image)
            val bitmap = bgraImage(image)
            val distinct = (0 until bitmap.width step 8).flatMap { x -> (0 until bitmap.height step 8).map { y -> bitmap.getRGB(x, y) } }.toSet()
            assertTrue("The capture must contain rendered text/controls", distinct.size > 4)
            System.getProperty("winrt.ide.previewOutput")?.let { output ->
                val file = Path.of(output); Files.createDirectories(file.parent); ImageIO.write(bitmap, "png", file.toFile())
            }
            val edited = WinRTXamlDesignDocument.prepare("""<Page xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" xmlns:d="http://schemas.microsoft.com/expression/blend/2008" x:Class="sample.Page">
              <Grid Background="{ThemeResource ApplicationPageBackgroundThemeBrush}"><TextBlock x:Name="Edited" Text="{x:Bind Title}" d:Text="Edited design value" Foreground="{StaticResource PreviewBrush}"/></Grid></Page>""",
                """<Application xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"><Application.Resources><SolidColorBrush x:Key="PreviewBrush" Color="Tomato"/></Application.Resources></Application>""")
            val refreshed = client.inspect(WinRTXamlInspectionRequest(root.className, root.resourcePath,
                previewMarkup = edited.markup, width = 320, height = 240, theme = "Dark"))
            assertEquals(refreshed.message, WinRTXamlHotReloadProtocol.APPLIED, refreshed.status)
            assertEquals(320.0, refreshed.inspection!!.nodes.first().bounds.width, 0.1)
            assertEquals(240.0, refreshed.inspection!!.nodes.first().bounds.height, 0.1)
            assertBoundedPixels(refreshed.inspection!!.image!!)
            assertFalse(refreshed.inspection!!.nodes.any { it.name == "Greeting" })
            val editedNode = refreshed.inspection!!.nodes.single { it.name == "Edited" }
            val readback = client.inspect(WinRTXamlInspectionRequest(root.className, root.resourcePath, selectedPath = editedNode.path, capture = false))
            assertEquals("Edited design value", readback.inspection!!.properties.single { it.name == "Text" }.value)
            assertEquals(root.version, readback.roots.single { it.className == root.className }.version)
        }
    }
    @Test fun a_running_app_can_be_inspected_without_replacing_its_content() {
        val folder = System.getProperty("winrt.ide.hotReloadSession")
        assumeTrue("Supply a real live WinUI session", !folder.isNullOrEmpty())
        connect(folder!!).use { client ->
            val before = client.request()
            val root = before.roots.first { it.className.endsWith("MainWindow") }
            val response = client.inspect(WinRTXamlInspectionRequest(root.className, root.resourcePath))
            assertEquals(response.message, WinRTXamlHotReloadProtocol.APPLIED, response.status)
            assertTrue(response.inspection!!.nodes.any { it.name == "Greeting" })
            assertNotNull(response.inspection!!.image)
            assertEquals(root.version, response.roots.single { it.className == root.className }.version)
            val rejected = client.inspect(WinRTXamlInspectionRequest(root.className, root.resourcePath, previewMarkup = "<Grid/>"))
            assertEquals(WinRTXamlHotReloadProtocol.REJECTED, rejected.status)
            assertEquals(root.sourceHash, client.request().roots.single { it.className == root.className }.sourceHash)
        }
    }
    private fun assertBoundedPixels(image: WinRTXamlVisualImage) {
        // Windows can apply 200% DPI: the design viewport is DIP, while
        // RenderTargetBitmap returns physical pixels bounded by the protocol.
        assertTrue(image.width in 1..768 && image.height in 1..768)
        assertEquals(4.0 / 3.0, image.width.toDouble() / image.height, 0.01)
    }
    private fun connect(folder: String): WinRTHotReloadClient {
        val path = Path.of(folder)
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30)
        var latest = "No session file"
        while (System.nanoTime() < deadline) {
            val file = Files.list(path).use { files -> files.filter { it.fileName.toString().endsWith(".session") }
                .toList().maxByOrNull { Files.getLastModifiedTime(it).toMillis() } }
            if (file != null) {
                val info = Properties().apply { Files.newInputStream(file).use(::load) }
                val process = ProcessHandle.of(info.getProperty("pid").toLong()).orElse(null)
                val command = process?.takeIf { it.isAlive }?.info()?.command()?.orElse(null)
                latest = "PID ${info.getProperty("pid")}, alive=${process?.isAlive}, executable=${command != null}"
                if (command != null) runCatching { WinRTHotReloadClient.read(file, command) }
                    .onFailure { latest = it.message.orEmpty() }.getOrNull()?.let { candidate ->
                    val reply = candidate.request()
                    latest = "${reply.status}: ${reply.message}; loaded roots=${reply.roots.map { it.className }}"
                    if (reply.roots.isNotEmpty()) return candidate
                    candidate.close()
                }
            }
            Thread.sleep(100)
        }
        error("The real WinUI host did not publish a live development session: $latest")
    }
}
