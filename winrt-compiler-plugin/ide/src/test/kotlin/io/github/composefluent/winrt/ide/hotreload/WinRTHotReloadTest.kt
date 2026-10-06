package io.github.composefluent.winrt.ide.hotreload

import io.github.composefluent.winrt.ide.xaml.WinRTXamlCatalog
import io.github.composefluent.winrt.ide.xaml.WinRTXamlContentMember
import io.github.composefluent.winrt.metadata.WinRTMetadataLoader
import io.github.composefluent.winrt.runtime.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class WinRTHotReloadTest {
    private val source = """<Window xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" x:Class="probe.Window"><StackPanel><TextBlock x:Name="Greeting" Text="Hello" Width="100"/></StackPanel></Window>"""
    private fun root(text: String = source) = WinRTXamlHotReloadRoot("probe.Window", "Window.xaml", WinRTHotReloadMarkup.parse(text).hash, 0, listOf("Greeting"))

    @Test fun literal_changes_decode_xml_and_keep_compiler_fingerprint_and_version() {
        val before = WinRTHotReloadMarkup.parse(source)
        val after = WinRTHotReloadMarkup.parse(source.replace("Hello", "A &amp; B").replace("100", "140"))
        val patch = after.patch(before, root())
        assertEquals(before.hash, patch.expectedHash)
        assertEquals(after.hash, patch.sourceHash)
        assertEquals(1L, patch.version)
        assertEquals(listOf(WinRTXamlHotReloadChange("Greeting", "Text", "A & B"), WinRTXamlHotReloadChange("Greeting", "Width", "140")), patch.changes.sortedBy { it.property })
        val formatted = WinRTHotReloadMarkup.parse(source.replace("<StackPanel>", "\n  <StackPanel>"))
        assertTrue(formatted.patch(before, root()).changes.isEmpty())
        val escaped = WinRTHotReloadMarkup.parse(source.replace("Hello", "{}{literal}"))
        assertEquals("{literal}", escaped.patch(before, root()).changes.single().literal)
    }

    @Test fun lifetime_and_connection_changes_require_rebuilding() {
        val before = WinRTHotReloadMarkup.parse(source)
        val unsupported = listOf(
            source.replace("TextBlock", "TextBox"), source.replace("Greeting", "Renamed"),
            source.replace("Hello", "{Binding Title}"), source.replace(" Width=\"100\"", ""),
            source.replace("x:Class=\"probe.Window\"", "x:Class=\"probe.Other\""),
            source.replace("<StackPanel>", "<StackPanel><TextBlock/>"), source.replace("Text=\"Hello\"", "Grid.Row=\"2\" Text=\"Hello\""),
        )
        unsupported.forEach { text -> assertTrue(text, runCatching { WinRTHotReloadMarkup.parse(text).patch(before, root()) }.isFailure) }
        assertTrue(runCatching { WinRTHotReloadMarkup.parse(source.replace("Hello", "changed")).patch(before, root().copy(sourceHash = "0".repeat(64))) }.isFailure)
        val template = source.replace("<StackPanel>", "<StackPanel><DataTemplate><TextBlock x:Name=\"Greeting\" Text=\"template\"/></DataTemplate>")
        assertTrue(runCatching { WinRTHotReloadMarkup.parse(template.replace("template", "changed"))
            .patch(WinRTHotReloadMarkup.parse(template), root(template)) }.isFailure)
        assertTrue(runCatching { WinRTHotReloadMarkup.parse("<!DOCTYPE Window [<!ENTITY x SYSTEM 'file:///secret'>]>$source") }.isFailure)
    }

    private val resourceSource = """<Window xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" x:Class="probe.Window">
        <StackPanel x:Name="Layout"><StackPanel.Resources><ResourceDictionary>
            <SolidColorBrush x:Key="Accent" Color="#FF0067C0"/>
            <Style x:Key="GreetingStyle" TargetType="TextBlock"><Setter Property="FontSize" Value="28"/><Setter Property="Foreground" Value="{StaticResource Accent}"/></Style>
        </ResourceDictionary></StackPanel.Resources>
        <TextBlock x:Name="Greeting" Text="Hello" Style="{StaticResource GreetingStyle}" Foreground="{StaticResource Accent}"/>
        <TextBlock x:Name="Second" Style="{StaticResource ResourceKey=GreetingStyle}"/>
        </StackPanel></Window>"""

    private fun resourceRoot(text: String = resourceSource) = root(text).copy(elements = listOf("Layout", "Greeting", "Second"))

    @Test fun mutable_resources_use_projected_paths_and_preserve_resource_expressions() {
        val before = WinRTHotReloadMarkup.parse(resourceSource)
        val after = WinRTHotReloadMarkup.parse(resourceSource.replace("#FF0067C0", "#FF00AA44"))
        val patch = after.patch(before, resourceRoot())
        assertTrue(patch.resources.isEmpty())
        assertEquals(WinRTXamlHotReloadChange("Layout", "Color", "#FF00AA44", listOf(
            WinRTXamlHotReloadStep.Property("Resources"), WinRTXamlHotReloadStep.Key("Accent"))), patch.changes.single())
        val themed = resourceSource.replace("Foreground=\"{StaticResource Accent}\"", "Foreground=\"{ThemeResource Accent}\"")
        assertEquals(1, WinRTHotReloadMarkup.parse(themed.replace("#FF0067C0", "#FF00AA44"))
            .patch(WinRTHotReloadMarkup.parse(themed), resourceRoot(themed)).changes.size)
    }

    @Test fun sealed_styles_reload_the_local_dictionary_and_its_explicit_consumers() {
        val before = WinRTHotReloadMarkup.parse(resourceSource)
        val patch = WinRTHotReloadMarkup.parse(resourceSource.replace("Value=\"28\"", "Value=\"36\""))
            .patch(before, resourceRoot())
        assertTrue(patch.changes.isEmpty())
        val replacement = patch.resources.single()
        assertEquals(WinRTXamlHotReloadTarget("Layout", listOf(WinRTXamlHotReloadStep.Property("Resources"))), replacement.target)
        assertEquals(listOf("Accent", "GreetingStyle"), replacement.expectedKeys)
        assertEquals(setOf("Greeting" to "Style", "Greeting" to "Foreground", "Second" to "Style"),
            replacement.references.map { it.target.element to it.property }.toSet())
        assertTrue(replacement.xaml, replacement.xaml.startsWith("<ResourceDictionary"))
        assertTrue(replacement.xaml.contains("xmlns:x=\"http://schemas.microsoft.com/winfx/2006/xaml\""))
        assertTrue(replacement.xaml.contains("Value=\"36\""))
        assertTrue(replacement.xaml.contains("{StaticResource Accent}"))
        // Implicit Resources syntax uses the same runtime dictionary contract.
        val implicit = resourceSource.replace("<ResourceDictionary>", "").replace("</ResourceDictionary>", "")
        assertEquals(1, WinRTHotReloadMarkup.parse(implicit.replace("Value=\"28\"", "Value=\"36\""))
            .patch(WinRTHotReloadMarkup.parse(implicit), resourceRoot(implicit)).resources.size)
    }

    @Test fun style_replacement_rejects_theme_implicit_keys_and_unresolved_connection_lifetimes() {
        val unsupported = listOf(
            resourceSource.replace("{StaticResource GreetingStyle}", "{ThemeResource GreetingStyle}"),
            resourceSource.replace(" x:Key=\"GreetingStyle\"", ""),
            resourceSource.replace(" x:Name=\"Second\"", ""),
            resourceSource.replace("Value=\"{StaticResource Accent}\"", "Value=\"{StaticResource Outside}\""),
            resourceSource.replace("<Style x:Key", "<Style x:Name=\"NamedStyle\" x:Key"),
        )
        unsupported.forEach { text ->
            assertTrue(text, runCatching { WinRTHotReloadMarkup.parse(text.replace("Value=\"28\"", "Value=\"36\""))
                .patch(WinRTHotReloadMarkup.parse(text), resourceRoot(text)) }.isFailure)
        }
        val added = resourceSource.replace("</ResourceDictionary>", "<SolidColorBrush x:Key=\"Extra\"/></ResourceDictionary>")
        assertTrue(runCatching { WinRTHotReloadMarkup.parse(added).patch(WinRTHotReloadMarkup.parse(resourceSource), resourceRoot()) }.isFailure)
    }

    private fun content(uri: String, type: String, property: String?): WinRTXamlContentMember? =
        if (uri != WinRTXamlCatalog.PRESENTATION) null else when (property ?: type) {
            "Window" -> WinRTXamlContentMember("Content", false)
            "StackPanel", "Children" -> WinRTXamlContentMember("Children", true)
            "Text", "FontSize", "Width" -> WinRTXamlContentMember(property!!, false)
            else -> null
        }

    @Test fun child_collections_reuse_connected_objects_and_load_only_unconnected_subtrees() {
        val first = "<TextBlock x:Name=\"Greeting\" Text=\"Hello\" Style=\"{StaticResource GreetingStyle}\" Foreground=\"{StaticResource Accent}\"/>"
        val second = "<TextBlock x:Name=\"Second\" Style=\"{StaticResource ResourceKey=GreetingStyle}\"/>"
        val next = resourceSource.replace(first, "").replace(second, "$second<TextBlock Text=\"New\"/>$first")
        val patch = WinRTHotReloadMarkup.parse(next).patch(WinRTHotReloadMarkup.parse(resourceSource), resourceRoot(), contentMember = ::content)
        val update = patch.children.single()
        assertEquals(WinRTXamlHotReloadTarget("Layout", listOf(WinRTXamlHotReloadStep.Property("Children"))), update.target)
        assertEquals(2, update.expectedSize)
        assertEquals(WinRTXamlHotReloadItem.Existing(1), update.items[0]); assertEquals(WinRTXamlHotReloadItem.Existing(0), update.items[2])
        val fragment = (update.items[1] as WinRTXamlHotReloadItem.Markup).xaml
        assertTrue(fragment, fragment.contains("Text=\"New\"")); assertTrue(fragment.contains("xmlns=\"${WinRTXamlCatalog.PRESENTATION}\""))
        assertTrue(patch.changes.isEmpty()); assertTrue(patch.resources.isEmpty())
        val removed = next.replace("<TextBlock Text=\"New\"/>", "")
        val deletion = WinRTHotReloadMarkup.parse(removed).patch(WinRTHotReloadMarkup.parse(next), resourceRoot(next), contentMember = ::content)
        assertEquals(listOf(WinRTXamlHotReloadItem.Existing(0), WinRTXamlHotReloadItem.Existing(2)), deletion.children.single().items)
        // Unnamed members are addressed through the compiler-owned getter and
        // checked list index, rather than an invented native name scope.
        val unnamed = source.replace(" x:Name=\"Greeting\"", "")
        val edited = WinRTHotReloadMarkup.parse(unnamed.replace("Hello", "changed"))
            .patch(WinRTHotReloadMarkup.parse(unnamed), root(unnamed), contentMember = ::content)
        assertEquals(listOf(WinRTXamlHotReloadStep.Property("Content"), WinRTXamlHotReloadStep.Property("Children"),
            WinRTXamlHotReloadStep.Index(0, 1)), edited.changes.single().path)
    }

    @Test fun child_changes_reject_compiled_connections_and_authored_component_lifetimes() {
        val before = WinRTHotReloadMarkup.parse(source)
        val unsupported = listOf(
            source.replace("<StackPanel>", "<StackPanel><TextBlock x:Name=\"Added\"/>"),
            source.replace("<StackPanel>", "<StackPanel><TextBlock Text=\"{Binding Title}\"/>"),
            source.replace("<StackPanel>", "<StackPanel><TextBlock Text=\"{x:Bind Title}\"/>"),
            source.replace("<StackPanel>", "<StackPanel><Button Click=\"onClick\"/>"),
            source.replace("<StackPanel>", "<StackPanel><DataTemplate><TextBlock/></DataTemplate>"),
            source.replace("<StackPanel>", "<StackPanel><c:Widget xmlns:c=\"using:probe\"/>"),
            source.replace("<TextBlock x:Name=\"Greeting\" Text=\"Hello\" Width=\"100\"/>", ""),
        )
        unsupported.forEach { text -> assertTrue(text, runCatching {
            WinRTHotReloadMarkup.parse(text).patch(before, root(), propertyProblem = { _, _, property ->
                if (property == "Click") "Event connections require rebuilding." else null
            }, contentMember = ::content)
        }.isFailure) }
        val eventSource = source.replace("<StackPanel>", "<StackPanel><Button Click=\"onClick\"/>")
        assertTrue("Missing event metadata must reject removal, not leave a compiled subscription alive", runCatching {
            WinRTHotReloadMarkup.parse(source).patch(WinRTHotReloadMarkup.parse(eventSource), root(eventSource), contentMember = ::content)
        }.isFailure)
    }

    private fun sdkCatalog(): WinRTXamlCatalog {
        val input = Path.of(requireNotNull(System.getProperty("winrt.ide.xamlInput")))
        val paths = Json.parseToJsonElement(Files.readString(input)).jsonObject.getValue("ReferenceAssemblies").jsonArray.map {
            Path.of(it.jsonObject.getValue("FullPath").jsonPrimitive.content)
        }.filter(Files::isRegularFile)
        return WinRTXamlCatalog(WinRTMetadataLoader.load(paths))
    }

    @Test fun real_sdk_content_properties_and_vector_interfaces_drive_collection_updates() {
        assumeTrue(System.getProperty("winrt.ide.xamlInput") != null)
        val catalog = sdkCatalog()
        val stack = requireNotNull(catalog.resolve(WinRTXamlCatalog.PRESENTATION, "StackPanel"))
        assertEquals(WinRTXamlContentMember("Children", true), catalog.contentMember(stack))
        assertEquals(WinRTXamlContentMember("Children", true), catalog.propertyContent(stack, "Children"))
        assertEquals(WinRTXamlContentMember("Content", false), catalog.contentMember(requireNotNull(catalog.resolve(WinRTXamlCatalog.PRESENTATION, "Window"))))
    }

    @Test fun loopback_client_authenticates_and_does_not_expose_credentials() {
        val folder = Files.createTempDirectory("winrt-hot-client-")
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val executor = Executors.newSingleThreadExecutor()
        val token = "t".repeat(43)
        val file = folder.resolve("probe.session")
        try {
            Files.newOutputStream(file).use { Properties().apply {
                setProperty("protocol", WinRTXamlHotReloadProtocol.VERSION.toString()); setProperty("pid", ProcessHandle.current().pid().toString())
                setProperty("port", server.localPort.toString()); setProperty("token", token)
            }.store(it, "probe") }
            val response = executor.submit {
                server.accept().use { socket ->
                    val (supplied, patch) = WinRTXamlHotReloadWire.readRequest(socket.getInputStream())
                    assertEquals(token, supplied); assertNull(patch)
                    WinRTXamlHotReloadWire.writeReply(socket.getOutputStream(), WinRTXamlHotReloadReply(0, "connected", listOf(root())))
                }
            }
            WinRTHotReloadClient.read(file).use { client ->
                assertFalse(client.toString().contains(token))
                assertEquals("connected", client.request().message)
                assertEquals(ProcessHandle.current().pid(), client.process.pid())
            }
            response.get(5, TimeUnit.SECONDS)
            assertTrue(runCatching { WinRTHotReloadClient.read(file, "C:/not-this-process.exe") }.isFailure)
        } finally { server.close(); executor.shutdownNow(); Files.deleteIfExists(file); Files.deleteIfExists(folder) }
    }

    /** A real compiler-generated WinUI host supplies the session; no test-only XAML runtime glue. */
    @Test fun real_winui_host_updates_text_dimensions_and_brush_on_its_ui_thread() {
        val directory = System.getProperty("winrt.ide.hotReloadSession")
        assumeTrue(directory != null)
        val clients = WinRTHotReloadClient.discover(Path.of(directory))
        assertEquals(1, clients.size)
        clients.single().use { client ->
            val initial = client.request()
            assertEquals(initial.message, WinRTXamlHotReloadProtocol.APPLIED, initial.status)
            val loaded = initial.roots.single { it.className.endsWith(".MainWindow") && "Greeting" in it.elements }
            val change = WinRTXamlHotReloadPatch(loaded.className, loaded.resourcePath, loaded.sourceHash, "b".repeat(64), loaded.version + 1,
                listOf(WinRTXamlHotReloadChange("Greeting", "Text", "Reloaded in place"),
                    WinRTXamlHotReloadChange("Greeting", "Width", "240"),
                    WinRTXamlHotReloadChange("Greeting", "FontSize", "24"),
                    WinRTXamlHotReloadChange("Greeting", "Foreground", "#FF0067C0")))
            val reply = client.request(change)
            assertEquals(reply.message, WinRTXamlHotReloadProtocol.APPLIED, reply.status)
            assertEquals("Reloaded in place", reply.values.single { it.property == "Text" }.value)
            assertEquals(240.0, reply.values.single { it.property == "Width" }.value.toDouble(), 0.0)
            assertEquals(24.0, reply.values.single { it.property == "FontSize" }.value.toDouble(), 0.0)
            assertEquals(4, reply.values.size)
            assertTrue("SDK color conversion must retain a native brush", reply.values.single { it.property == "Foreground" }.value.isNotBlank())
            assertTrue(client.alive)
            val updated = client.request().roots.single { it.className == loaded.className }
            assertEquals(change.version, updated.version); assertEquals(change.sourceHash, updated.sourceHash)
            val invalid = change.copy(expectedHash = updated.sourceHash, sourceHash = "c".repeat(64), version = updated.version + 1,
                changes = listOf(WinRTXamlHotReloadChange("Greeting", "Text", "must roll back"), WinRTXamlHotReloadChange("Greeting", "Width", "-1")))
            val rejected = client.request(invalid)
            assertEquals(rejected.message, WinRTXamlHotReloadProtocol.REJECTED, rejected.status)
            assertEquals(updated.sourceHash, client.request().roots.single { it.className == loaded.className }.sourceHash)
        }
    }

    /** Actual XBF, shared native brushes and sealed native Style objects, with
     * the normal compiler-generated SDK getters and WinUI XamlReader loader. */
    @Test fun real_winui_host_reloads_shared_resources_and_sealed_styles() {
        val directory = System.getProperty("winrt.ide.hotReloadResourcesSession")
        val sourcePath = System.getProperty("winrt.ide.hotReloadResourcesSource")
        assumeTrue(directory != null && sourcePath != null)
        val text = Files.readString(Path.of(sourcePath))
        val clients = WinRTHotReloadClient.discover(Path.of(directory))
        assertEquals(1, clients.size)
        clients.single().use { client ->
            fun current() = client.request().roots.single { it.className.endsWith(".MainWindow") && "Second" in it.elements }
            val resourcePath = listOf(WinRTXamlHotReloadStep.Property("Resources"), WinRTXamlHotReloadStep.Key("Accent"))
            val stylePath = listOf(WinRTXamlHotReloadStep.Property("Resources"), WinRTXamlHotReloadStep.Key("GreetingStyle"))
            val reads = listOf(
                WinRTXamlHotReloadRead(WinRTXamlHotReloadTarget("Greeting"), "FontSize"),
                WinRTXamlHotReloadRead(WinRTXamlHotReloadTarget("Second"), "FontSize"),
                WinRTXamlHotReloadRead(WinRTXamlHotReloadTarget("Layout", resourcePath), "Color"),
                WinRTXamlHotReloadRead(WinRTXamlHotReloadTarget("Greeting", listOf(WinRTXamlHotReloadStep.Property("Foreground"))), "Color"),
                WinRTXamlHotReloadRead(WinRTXamlHotReloadTarget("Second", listOf(WinRTXamlHotReloadStep.Property("Foreground"))), "Color"),
                WinRTXamlHotReloadRead(WinRTXamlHotReloadTarget("Layout", stylePath), "IsSealed"),
            )
            fun inspect(): WinRTXamlHotReloadReply {
                val root = current()
                return client.request(WinRTXamlHotReloadPatch(root.className, root.resourcePath, root.sourceHash,
                    root.sourceHash, root.version + 1, emptyList(), reads = reads)).also {
                    assertEquals(it.message, WinRTXamlHotReloadProtocol.APPLIED, it.status)
                }
            }
            fun assertState(reply: WinRTXamlHotReloadReply, font: Double): String {
                listOf("Greeting", "Second").forEach { element ->
                    assertEquals(font, reply.values.single { it.element == element && it.property == "FontSize" }.value.toDouble(), 0.0)
                }
                // Readbacks follow mutation echoes; Color has both for an
                // in-place recolor, using the same resource address.
                val color = reply.values.last { it.element == "Layout" && it.property == "Color" }.value
                assertEquals(listOf(color, color), reply.values.filter { it.element != "Layout" && it.property == "Color" }.map { it.value })
                assertEquals("true", reply.values.single { it.property == "IsSealed" }.value)
                return color
            }
            val originalColor = assertState(inspect(), 28.0)
            val before = WinRTHotReloadMarkup.parse(text)
            val blue = WinRTHotReloadMarkup.parse(text.replace("#FF0067C0", "#FF008844"))
            val recolor = blue.patch(before, current()).copy(reads = reads)
            val recolored = client.request(recolor)
            assertEquals(recolored.message, WinRTXamlHotReloadProtocol.APPLIED, recolored.status)
            val changedColor = assertState(recolored, 28.0)
            assertNotEquals(originalColor, changedColor)
            val restyled = WinRTHotReloadMarkup.parse(blue.text.replace("Value=\"28\"", "Value=\"36\""))
            val style = restyled.patch(blue, current()).copy(reads = reads)
            assertEquals(1, style.resources.size)
            val replaced = client.request(style)
            assertEquals(replaced.message, WinRTXamlHotReloadProtocol.APPLIED, replaced.status)
            assertEquals(changedColor, assertState(replaced, 36.0))
            val latest = current()
            val rejectedStyle = style.copy(expectedHash = latest.sourceHash, sourceHash = "c".repeat(64), version = latest.version + 1,
                changes = listOf(WinRTXamlHotReloadChange("Greeting", "Width", "-1")),
                resources = style.resources.map { it.copy(xaml = it.xaml.replace("Value=\"36\"", "Value=\"48\"")) })
            val rejected = client.request(rejectedStyle)
            assertEquals(rejected.message, WinRTXamlHotReloadProtocol.REJECTED, rejected.status)
            assertEquals(latest.sourceHash, current().sourceHash)
            assertEquals(changedColor, assertState(inspect(), 36.0))
            // A subsequent in-place brush update reaches both reloaded styles,
            // demonstrating that they use the current live dictionary object.
            val finalRoot = current()
            val final = client.request(WinRTXamlHotReloadPatch(finalRoot.className, finalRoot.resourcePath,
                finalRoot.sourceHash, "d".repeat(64), finalRoot.version + 1,
                listOf(WinRTXamlHotReloadChange("Layout", "Color", "#FFCC4400", resourcePath)), reads = reads))
            assertEquals(final.message, WinRTXamlHotReloadProtocol.APPLIED, final.status)
            assertNotEquals(changedColor, assertState(final, 36.0))
            assertTrue(client.alive)
        }
    }

    /** Native UIElementCollection, SDK parser and compiler-generated accessors.
     * The expected text/order and restored style expose detached/replaced-object
     * bugs that an echo-only transport test cannot observe. */
    @Test fun real_winui_host_reorders_adds_removes_children_and_rolls_back_failed_transactions() {
        val directory = System.getProperty("winrt.ide.hotReloadGraphSession")
        val sourcePath = System.getProperty("winrt.ide.hotReloadGraphSource")
        assumeTrue(directory != null && sourcePath != null && System.getProperty("winrt.ide.xamlInput") != null)
        val catalog = sdkCatalog()
        val text = Files.readString(Path.of(sourcePath))
        val first = Regex("<TextBlock[^>]*x:Name=\"Greeting\"[^>]*/>").find(text)!!.value
        val second = Regex("<TextBlock[^>]*x:Name=\"Second\"[^>]*/>").find(text)!!.value
        val clients = WinRTHotReloadClient.discover(Path.of(directory))
        assertEquals(1, clients.size)
        clients.single().use { client ->
            fun current() = client.request().roots.single { it.className.endsWith(".MainWindow") && "Second" in it.elements }
            fun address(index: Int, size: Int) = WinRTXamlHotReloadTarget("Layout", listOf(
                WinRTXamlHotReloadStep.Property("Children"), WinRTXamlHotReloadStep.Index(index, size)))
            fun inspect(size: Int, indices: List<Int>): WinRTXamlHotReloadReply {
                val root = current()
                return client.request(WinRTXamlHotReloadPatch(root.className, root.resourcePath, root.sourceHash, root.sourceHash,
                    root.version + 1, emptyList(), reads = indices.map { WinRTXamlHotReloadRead(address(it, size), "Text") } +
                        WinRTXamlHotReloadRead(WinRTXamlHotReloadTarget("Greeting"), "Width"))).also {
                    assertEquals(it.message, WinRTXamlHotReloadProtocol.APPLIED, it.status)
                }
            }
            fun patch(before: String, after: String) = WinRTHotReloadMarkup.parse(after).patch(WinRTHotReloadMarkup.parse(before), current(),
                propertyProblem = { uri, type, property ->
                    if (catalog.resolve(uri, type)?.let { catalog.members(it).any { m -> m.name == property && m.isEvent } } == true)
                        "Events require rebuilding." else null
                }, contentMember = { uri, type, property -> catalog.resolve(uri, type)?.let {
                    if (property == null) catalog.contentMember(it) else catalog.propertyContent(it, property)
                } })
            fun texts(reply: WinRTXamlHotReloadReply) = reply.values.filter { it.property == "Text" }.map { it.value }
            assertEquals(listOf("Hello from Kotlin WinRT", "Shared style and brush"), texts(inspect(3, listOf(0, 1))))
            // A live value absent from XAML must survive the reorder.
            val root = current()
            val state = client.request(WinRTXamlHotReloadPatch(root.className, root.resourcePath, root.sourceHash, root.sourceHash,
                root.version + 1, listOf(WinRTXamlHotReloadChange("Greeting", "Width", "271"))))
            assertEquals(state.message, WinRTXamlHotReloadProtocol.APPLIED, state.status)
            val added = "<TextBlock Text=\"Added at runtime\" FontSize=\"19\"/>"
            val reordered = text.replace(first, "").replace(second, "$second$added$first")
            val update = patch(text, reordered).copy(reads = listOf(WinRTXamlHotReloadRead(address(1, 4), "Text")))
            assertEquals(1, update.children.size)
            val applied = client.request(update)
            assertEquals(applied.message, WinRTXamlHotReloadProtocol.APPLIED, applied.status)
            val live = inspect(4, listOf(0, 1, 2))
            assertEquals(listOf("Shared style and brush", "Added at runtime", "Hello from Kotlin WinRT"), texts(live))
            assertEquals(271.0, live.values.single { it.property == "Width" }.value.toDouble(), 0.0)
            val invalidText = reordered.replace(added, "<TextBlock Text=\"Must roll back\"/>$added")
            val invalid = patch(reordered, invalidText).copy(changes = listOf(WinRTXamlHotReloadChange("Greeting", "Width", "-1")))
            assertEquals(1, invalid.children.size)
            val fingerprint = current().sourceHash
            val rejected = client.request(invalid)
            assertEquals(rejected.message, WinRTXamlHotReloadProtocol.REJECTED, rejected.status)
            assertEquals(fingerprint, current().sourceHash)
            assertEquals(texts(live), texts(inspect(4, listOf(0, 1, 2))))
            val deleted = reordered.replace(added, "")
            val removal = client.request(patch(reordered, deleted))
            assertEquals(removal.message, WinRTXamlHotReloadProtocol.APPLIED, removal.status)
            val final = inspect(3, listOf(0, 1))
            assertEquals(listOf("Shared style and brush", "Hello from Kotlin WinRT"), texts(final))
            assertEquals(271.0, final.values.single { it.property == "Width" }.value.toDouble(), 0.0)
            assertTrue(client.alive)
        }
    }
}
