@file:OptIn(io.github.composefluent.winrt.runtime.InternalWinRTApi::class)

package io.github.composefluent.winrt.ide.preview

import io.github.composefluent.winrt.ide.hotreload.WinUIXamlHotReloadClient
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
    @Test fun uwp_native_appcontainer_previews_unsaved_markup_and_reads_effective_properties() {
        verifyUwpPreview("winrt.ide.uwpPreviewSession")
    }
    @Test fun uwp_sdk_designer_previews_unsaved_markup_and_reads_effective_properties() {
        verifyUwpPreview("winrt.ide.uwpSdkPreviewSession")
    }
    private fun verifyUwpPreview(property: String) {
        val folder = System.getProperty(property)
        assumeTrue("Supply a real UWP preview session: $property", !folder.isNullOrEmpty())
        connect(folder!!).use { client ->
            val before = client.request()
            val root = before.roots.single { it.className == WinUIXamlHotReloadProtocol.PREVIEW_CLASS }
            assertEquals("The user's UWP application must not be instantiated", listOf(root), before.roots)
            var previousPixels: ByteArray? = null
            for ((theme, text) in listOf("Light" to "UWP preview", "Dark" to "Unsaved UWP edit")) {
                val document = WinUIXamlDesignDocument.prepare("""<Page xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" x:Class="sample.DesignPage">
                    <StackPanel Background="{ThemeResource ApplicationPageBackgroundThemeBrush}" Padding="24" Spacing="16">
                        <TextBlock x:Name="Greeting" Text="$text" FontSize="28"/>
                        <Button x:Name="Action" Content="AppContainer preview"/>
                    </StackPanel></Page>""")
                val reply = client.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath,
                    previewMarkup = document.markup, width = 640, height = 480, theme = theme))
                assertEquals(reply.message, WinUIXamlHotReloadProtocol.APPLIED, reply.status)
                val view = requireNotNull(reply.inspection)
                assertTrue("Native templates must be rendered", view.nodes.size > 5)
                val greeting = view.nodes.single { it.name == "Greeting" }
                val values = client.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath,
                    selectedPath = greeting.path, capture = false))
                assertEquals(text, values.inspection!!.properties.single { it.name == "Text" }.value)
                val image = requireNotNull(view.image)
                assertTrue("UWP design layout is empty: root=${view.nodes.first().bounds}, greeting=${greeting.bounds}, bitmap=${image.width}x${image.height}",
                    greeting.bounds.width > 0 && greeting.bounds.height > 0)
                assertEquals("UWP viewport: root=${view.nodes.first().bounds}, bitmap=${image.width}x${image.height}",
                    4.0 / 3.0, image.width.toDouble() / image.height, 0.01)
                assertBoundedPixels(image)
                previousPixels?.let { assertFalse(it.contentEquals(image.pixels)) }
                previousPixels = image.pixels
                System.getProperty("winrt.ide.previewOutput")?.let { output ->
                    val path = Path.of(output); Files.createDirectories(path.parent)
                    ImageIO.write(bgraImage(image), "png", path.toFile())
                }
                assertEquals(root.version, reply.roots.single().version)
            }
        }
    }
    @Test fun sdk_designer_renders_without_application_classes_and_reloads_source_resources() {
        val folder = System.getProperty("winrt.ide.sdkPreviewSession")
        assumeTrue("Supply the SDK-only WinUI designer session", !folder.isNullOrEmpty())
        connect(folder!!).use { client ->
            val before = client.request()
            val root = before.roots.single { it.className == WinUIXamlHotReloadProtocol.PREVIEW_CLASS }
            assertEquals(listOf(root), before.roots)
            fun document(color: String, text: String) = WinUIXamlDesignDocument.prepare("""<Window xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" xmlns:d="http://schemas.microsoft.com/expression/blend/2008" xmlns:local="using:broken.project" x:Class="broken.project.Window">
              <StackPanel Background="{ThemeResource ApplicationPageBackgroundThemeBrush}" Spacing="20" Padding="24">
              <TextBlock x:Name="Greeting" Text="{x:Bind MissingTitle}" d:Text="$text" FontSize="28" Foreground="{StaticResource PreviewBrush}"/>
              <Button x:Name="Action" Content="SDK designer" Style="{StaticResource SubtleButtonStyle}" Click="MissingEvent"/>
              <local:Custom x:Name="Custom" Width="160" Height="40"/>
              <ListView><d:ListView.Items><d:TextBlock Text="Design item"/></d:ListView.Items></ListView>
              </StackPanel></Window>""",
                """<Application xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"><Application.Resources><ResourceDictionary Source="Styles/Colors.xaml"/></Application.Resources></Application>""",
                resource = { uri, _ -> if (uri == "Styles/Colors.xaml") WinUIXamlDesignResource("""<ResourceDictionary xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"><SolidColorBrush x:Key="PreviewBrush" Color="$color"/></ResourceDictionary>""", uri) else null },
                sdkType = { _, _ -> false }) { _, type, member -> type == "Button" && member == "Click" }
            val first = client.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath,
                previewMarkup = document("Tomato", "No application build").markup, width = 640, height = 480, theme = "Light"))
            assertEquals(first.message, WinUIXamlHotReloadProtocol.APPLIED, first.status)
            val view = first.inspection!!
            assertTrue(view.nodes.size > 8)
            assertTrue(view.nodes.any { it.name == "Custom" && it.typeName.endsWith("Border") })
            val greeting = view.nodes.single { it.name == "Greeting" }
            val values = client.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath, selectedPath = greeting.path, capture = false))
            assertEquals("No application build", values.inspection!!.properties.single { it.name == "Text" }.value)
            val image = view.image!!
            assertBoundedPixels(image)
            System.getProperty("winrt.ide.previewOutput")?.let { output ->
                val path = Path.of(output); Files.createDirectories(path.parent); ImageIO.write(bgraImage(image), "png", path.toFile())
            }
            val next = client.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath,
                previewMarkup = document("Blue", "Unsaved source edit").markup, width = 320, height = 240, theme = "Dark"))
            assertEquals(next.message, WinUIXamlHotReloadProtocol.APPLIED, next.status)
            val nextGreeting = next.inspection!!.nodes.single { it.name == "Greeting" }
            val nextValues = client.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath, selectedPath = nextGreeting.path, capture = false))
            assertEquals("Unsaved source edit", nextValues.inspection!!.properties.single { it.name == "Text" }.value)
            assertFalse(image.pixels.contentEquals(next.inspection!!.image!!.pixels))
            assertEquals(root.version, next.roots.single().version)
            val invalid = client.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath,
                previewMarkup = "<ThisIsNotAControl xmlns=\"http://schemas.microsoft.com/winfx/2006/xaml/presentation\"/>"))
            assertEquals(WinUIXamlHotReloadProtocol.REJECTED, invalid.status)
            assertEquals("Unsaved source edit", client.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath, selectedPath = nextGreeting.path, capture = false)).inspection!!.properties.single { it.name == "Text" }.value)
        }
    }
    @Test fun real_winui_templates_pixels_and_effective_properties_match_the_static_document() {
        val folder = System.getProperty("winrt.ide.previewSession")
        assumeTrue("Supply a real WinUI preview session", !folder.isNullOrEmpty())
        connect(folder!!).use { client ->
            val before = client.request()
            assertEquals(before.message, WinUIXamlHotReloadProtocol.APPLIED, before.status)
            val root = before.roots.single { it.className == WinUIXamlHotReloadProtocol.PREVIEW_CLASS }
            assertFalse("User main must not instantiate its window", before.roots.any { it.className == "sample.hello.MainWindow" })
            val document = WinUIXamlDesignDocument.prepare("""<Window xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" xmlns:controls="using:sample.controls" x:Class="sample.Window">
              <StackPanel Background="{ThemeResource ApplicationPageBackgroundThemeBrush}" Spacing="20" Padding="24">
              <TextBlock x:Name="Greeting" Text="Real WinUI Preview" FontSize="28"/>
              <Button x:Name="Action" Content="Preview button" Style="{StaticResource SubtleButtonStyle}"/>
              <controls:GreetingControl x:Name="CustomGreeting"/>
              </StackPanel></Window>""")
            val response = client.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath,
                previewMarkup = document.markup, width = 640, height = 480, theme = "Light"))
            assertEquals(response.message, WinUIXamlHotReloadProtocol.APPLIED, response.status)
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
            val selected = client.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath, selectedPath = greeting.path))
            assertEquals(selected.message, WinUIXamlHotReloadProtocol.APPLIED, selected.status)
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
            val edited = WinUIXamlDesignDocument.prepare("""<Page xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" xmlns:d="http://schemas.microsoft.com/expression/blend/2008" x:Class="sample.Page">
              <Grid Background="{ThemeResource ApplicationPageBackgroundThemeBrush}"><TextBlock x:Name="Edited" Text="{x:Bind Title}" d:Text="Edited design value" Foreground="{StaticResource PreviewBrush}"/></Grid></Page>""",
                """<Application xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"><Application.Resources><SolidColorBrush x:Key="PreviewBrush" Color="Tomato"/></Application.Resources></Application>""")
            val refreshed = client.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath,
                previewMarkup = edited.markup, width = 320, height = 240, theme = "Dark"))
            assertEquals(refreshed.message, WinUIXamlHotReloadProtocol.APPLIED, refreshed.status)
            assertEquals(320.0, refreshed.inspection!!.nodes.first().bounds.width, 0.1)
            assertEquals(240.0, refreshed.inspection!!.nodes.first().bounds.height, 0.1)
            assertBoundedPixels(refreshed.inspection!!.image!!)
            assertFalse(refreshed.inspection!!.nodes.any { it.name == "Greeting" })
            val editedNode = refreshed.inspection!!.nodes.single { it.name == "Edited" }
            val readback = client.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath, selectedPath = editedNode.path, capture = false))
            assertEquals("Edited design value", readback.inspection!!.properties.single { it.name == "Text" }.value)
            assertEquals(root.version, readback.roots.single { it.className == root.className }.version)
        }
    }
    @Test fun native_popup_layer_pixels_and_large_visual_tree_pages_are_complete() {
        val folder = System.getProperty("winrt.ide.previewSession")
        assumeTrue("Supply a real Native preview session", !folder.isNullOrEmpty())
        connect(folder!!).use { client ->
            val root = client.request().roots.single { it.className == WinUIXamlHotReloadProtocol.PREVIEW_CLASS }
            val popupDocument = WinUIXamlDesignDocument.prepare("""<Grid xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" Background="White"><Popup IsOpen="True" HorizontalOffset="60" VerticalOffset="70"><Border x:Name="PopupCard" Background="Red" Width="100" Height="80"><TextBlock Text="Popup capture"/></Border></Popup></Grid>""")
            val reply = client.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath,
                previewMarkup = popupDocument.markup, width = 800, height = 600))
            assertEquals(reply.message, WinUIXamlHotReloadProtocol.APPLIED, reply.status)
            val view = requireNotNull(reply.inspection)
            val popup = view.nodes.single { it.name == "PopupCard" }
            assertTrue("Popup nodes need their own layer", popup.layer > 0)
            val image = requireNotNull(view.image)
            val bitmap = bgraImage(image)
            val x = ((popup.bounds.x + popup.bounds.width * 0.8) / 800 * image.width).toInt()
            val y = ((popup.bounds.y + popup.bounds.height * 0.8) / 600 * image.height).toInt()
            System.getProperty("winrt.ide.previewOutput")?.let { output ->
                val file = Path.of(output).resolveSibling("native-popup.png"); Files.createDirectories(file.parent); ImageIO.write(bitmap, "png", file.toFile())
            }
            assertEquals("Popup pixels must be composited at SDK-reported bounds ${popup.bounds}", 0xffff0000.toInt(), bitmap.getRGB(x, y))
            val stacked = WinUIXamlDesignDocument.prepare("""<Grid xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" Background="White"><Popup IsOpen="True" HorizontalOffset="40" VerticalOffset="40"><Border x:Name="LowerPopup" Background="Red" Width="120" Height="100"/></Popup><Popup IsOpen="True" HorizontalOffset="60" VerticalOffset="60"><Border x:Name="UpperPopup" Background="Blue" Width="100" Height="80"/></Popup></Grid>""")
            val layers = client.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath,
                previewMarkup = stacked.markup, width = 800, height = 600)).inspection!!
            val lower = layers.nodes.single { it.name == "LowerPopup" }
            val upper = layers.nodes.single { it.name == "UpperPopup" }
            assertTrue("Last-opened popup must be the front layer", upper.layer > lower.layer)
            val overlap = layers.image!!
            assertEquals("Overlapping popups must compose back to front", 0xff0000ff.toInt(), bgraImage(overlap).getRGB(
                ((upper.bounds.x + 30) / 800 * overlap.width).toInt(), ((upper.bounds.y + 30) / 600 * overlap.height).toInt()))
            assertEquals(upper.path, WinUIXamlVisualInspectionSelection.hit(layers.nodes, upper.bounds.x + 30, upper.bounds.y + 30)!!.path)
            val buttons = (0 until 800).joinToString("") { index -> """<Button Content="B$index" Canvas.Left="${index % 30 * 32}" Canvas.Top="${index / 30 * 32}" Width="32" Height="32"/>""" }
            val large = WinUIXamlDesignDocument.prepare("""<Canvas xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation">$buttons</Canvas>""")
            val tree = client.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath, previewMarkup = large.markup,
                width = 1000, height = 700, capture = false, readProperties = false))
            assertEquals(tree.message, WinUIXamlHotReloadProtocol.APPLIED, tree.status)
            val nodes = requireNotNull(tree.inspection).nodes
            assertTrue("Realized SDK tree must span multiple wire pages: ${nodes.size}", nodes.size > 2048)
            assertEquals(nodes.size, tree.inspection!!.totalNodes)
            assertEquals(nodes.size, nodes.map { it.path }.distinct().size)
            assertTrue("Old popup layers must be closed when replacing the document", nodes.none { it.layer > 0 })
        }
    }
    @Test fun large_artboard_region_keeps_world_coordinates_and_native_pixel_density() {
        val folder = System.getProperty("winrt.ide.previewSession")
        assumeTrue("Supply a real Native preview session", !folder.isNullOrEmpty())
        connect(folder!!).use { client ->
            val root = client.request().roots.single { it.className == WinUIXamlHotReloadProtocol.PREVIEW_CLASS }
            val document = WinUIXamlDesignDocument.prepare("""<Canvas xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" Background="White"><Border x:Name="Blue" Background="Blue" Canvas.Left="4400" Canvas.Top="50" Width="100" Height="100"/><TextBlock Text="Capture region" Canvas.Left="4420" Canvas.Top="80" FontSize="20" Foreground="Black"/></Canvas>""")
            val initial = client.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath,
                previewMarkup = document.markup, width = 5000, height = 600, renderScale = 1.0))
            assertEquals(initial.message, WinUIXamlHotReloadProtocol.APPLIED, initial.status)
            assertEquals(5000.0, initial.inspection!!.nodes.first().bounds.width, 0.1)
            val region = WinUIXamlVisualBounds(4300.0, 0.0, 300.0, 200.0)
            val capture = client.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath,
                captureBounds = region, renderScale = 2.0, readTree = false, readProperties = false))
            assertEquals(capture.message, WinUIXamlHotReloadProtocol.APPLIED, capture.status)
            val image = requireNotNull(capture.inspection!!.image)
            assertEquals(region, image.bounds)
            val bitmap = bgraImage(image)
            System.getProperty("winrt.ide.previewOutput")?.let { output ->
                val file = Path.of(output); Files.createDirectories(file.parent); ImageIO.write(bitmap, "png", file.toFile())
            }
            assertTrue("Viewport pixel width: ${image.width}", kotlin.math.abs(600 - image.width) <= 2)
            assertTrue("Viewport pixel height: ${image.height}", kotlin.math.abs(400 - image.height) <= 2)
            assertEquals("Blue at its world-space coordinate", 0xff0000ff.toInt(), bitmap.getRGB(image.width / 2, image.height * 7 / 10))
            assertEquals("White outside the element", 0xffffffff.toInt(), bitmap.getRGB(image.width / 20, image.height / 20))
            val empty = client.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath,
                captureBounds = region.copy(x = 0.0), renderScale = 2.0, readTree = false, readProperties = false))
            assertEquals(empty.message, WinUIXamlHotReloadProtocol.APPLIED, empty.status)
            val emptyBitmap = bgraImage(requireNotNull(empty.inspection!!.image))
            assertEquals(0xffffffff.toInt(), emptyBitmap.getRGB(emptyBitmap.width / 2, emptyBitmap.height / 2))
        }
    }
    @Test fun a_running_app_can_be_inspected_without_replacing_its_content() {
        val folder = System.getProperty("winrt.ide.hotReloadSession")
        assumeTrue("Supply a real live WinUI session", !folder.isNullOrEmpty())
        connect(folder!!).use { client ->
            val before = client.request()
            val root = before.roots.first { it.className.endsWith("MainWindow") }
            val response = client.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath))
            assertEquals(response.message, WinUIXamlHotReloadProtocol.APPLIED, response.status)
            assertTrue(response.inspection!!.nodes.any { it.name == "Greeting" })
            assertNotNull(response.inspection!!.image)
            assertEquals(root.version, response.roots.single { it.className == root.className }.version)
            val rejected = client.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath, previewMarkup = "<Grid/>"))
            assertEquals(WinUIXamlHotReloadProtocol.REJECTED, rejected.status)
            assertEquals(root.sourceHash, client.request().roots.single { it.className == root.className }.sourceHash)
        }
    }
    private fun assertBoundedPixels(image: WinUIXamlVisualImage) {
        // Windows can apply 200% DPI: the design viewport is DIP, while
        // RenderTargetBitmap returns physical pixels bounded by the protocol.
        assertTrue(image.width in 1..WinUIXamlHotReloadProtocol.MAX_IMAGE_DIMENSION && image.height in 1..WinUIXamlHotReloadProtocol.MAX_IMAGE_DIMENSION)
        assertEquals(4.0 / 3.0, image.width.toDouble() / image.height, 0.01)
    }
    private fun connect(folder: String): WinUIXamlHotReloadClient {
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
                if (command != null) runCatching { WinUIXamlHotReloadClient.read(file, command) }
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
