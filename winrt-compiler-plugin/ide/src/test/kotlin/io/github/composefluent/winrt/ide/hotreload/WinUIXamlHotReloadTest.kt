package io.github.composefluent.winrt.ide.hotreload

import io.github.composefluent.winrt.ide.xaml.WinUIXamlCatalog
import io.github.composefluent.winrt.ide.xaml.WinUIXamlContentMember
import io.github.composefluent.winrt.metadata.WinRTMetadataLoader
import io.github.composefluent.winrt.metadata.WinRTPortableExecutableMetadataWriter
import io.github.composefluent.winrt.metadata.WinUIXamlApplicationTypeDescriptor
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

class WinUIXamlHotReloadTest {
    private val source = """<Window xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" x:Class="probe.Window"><StackPanel><TextBlock x:Name="Greeting" Text="Hello" Width="100"/></StackPanel></Window>"""
    private fun root(text: String = source) = WinUIXamlHotReloadRoot("probe.Window", "Window.xaml", WinUIXamlHotReloadMarkup.parse(text).hash, 0, listOf("Greeting"))

    @Test fun named_event_changes_are_separate_from_property_updates_and_can_disable_and_restore() {
        val before = source.replace("Text=\"Hello\"", "Text=\"Hello\" Tapped=\"First\"")
        fun patch(old: String, next: String) = WinUIXamlHotReloadMarkup.parse(next).patch(
            WinUIXamlHotReloadMarkup.parse(old), root(old), isEvent = { _, _, member -> member == "Tapped" })
        val changed = patch(before, before.replace("First", "Second"))
        assertTrue(changed.changes.isEmpty())
        assertEquals(listOf(WinUIXamlHotReloadEvent("Greeting", "Tapped", "First", "Second")), changed.events)
        val removed = before.replace(" Tapped=\"First\"", "")
        assertEquals("", patch(before, removed).events.single().handler)
        assertEquals("", patch(removed, before).events.single().expectedHandler)
        assertThrows(IllegalArgumentException::class.java) { patch(before, before.replace("First", "{x:Bind First}")) }
        assertThrows(IllegalArgumentException::class.java) { patch(before.replace("First", "{x:Bind First}"), removed) }
    }

    @Test fun removed_literal_properties_emit_resets_but_expressions_remain_guarded() {
        val after = source.replace(" Width=\"100\"", "")
        val patch = WinUIXamlHotReloadMarkup.parse(after).patch(WinUIXamlHotReloadMarkup.parse(source), root())
        assertEquals(WinUIXamlHotReloadChange("Greeting", "Width", "", reset = true), patch.changes.single())
        val bound = source.replace("100", "{Binding Width}")
        assertThrows(IllegalArgumentException::class.java) {
            WinUIXamlHotReloadMarkup.parse(after).patch(WinUIXamlHotReloadMarkup.parse(bound), root(bound))
        }
    }

    @Test fun attached_literals_use_resolved_owner_and_preserve_expression_guards() {
        val before = source.replace("Width=\"100\"", "Grid.Row=\"0\"")
        val after = before.replace("Grid.Row=\"0\"", "Grid.Row=\"2\"")
        val patch = WinUIXamlHotReloadMarkup.parse(after).patch(WinUIXamlHotReloadMarkup.parse(before), root(before),
            attachedProperty = { uri, owner, property ->
                assertEquals(WinUIXamlCatalog.PRESENTATION, uri)
                assertEquals("Grid", owner); assertEquals("Row", property)
                "Microsoft.UI.Xaml.Controls.Grid.Row"
            })
        assertEquals("Microsoft.UI.Xaml.Controls.Grid.Row", patch.changes.single().property)
        assertEquals("2", patch.changes.single().literal)
        assertThrows(IllegalArgumentException::class.java) {
            WinUIXamlHotReloadMarkup.parse(after.replace("Grid.Row=\"2\"", "Grid.Row=\"{Binding Row}\""))
                .patch(WinUIXamlHotReloadMarkup.parse(before), root(before), attachedProperty = { _, _, _ -> "Grid.Row" })
        }
    }

    @Test fun visual_tree_window_roots_follow_the_metadata_hierarchy_for_custom_window_types() {
        val folder = Files.createTempDirectory("winrt-window-hierarchy-")
        val metadata = folder.resolve("Windows.winmd")
        try {
            // Use the normalized metadata owner, including the same hierarchy
            // as Gallery's WindowEx, rather than recognizing class-name suffixes.
            WinRTPortableExecutableMetadataWriter.writeXamlSchemaWinmd("Windows", listOf(
                WinUIXamlApplicationTypeDescriptor("Microsoft.UI.Xaml.Window"),
                WinUIXamlApplicationTypeDescriptor("Sample.WindowEx", "Microsoft.UI.Xaml.Window"),
                WinUIXamlApplicationTypeDescriptor("Sample.GalleryShell", "Sample.WindowEx"),
                WinUIXamlApplicationTypeDescriptor("Sample.WindowNamedControl"),
            ), emptyMap(), metadata, emptyMap())
            val catalog = WinUIXamlCatalog(WinRTMetadataLoader.load(listOf(metadata)))
            fun markup(name: String) = WinUIXamlHotReloadMarkup.parse("""<local:$name xmlns:local="using:Sample"/>""")
            assertTrue(markup("GalleryShell").isWindow(catalog))
            assertTrue(markup("WindowEx").isWindow(catalog))
            assertFalse(markup("WindowNamedControl").isWindow(catalog))
            assertTrue(markup("Unknown").isWindow(catalog, "Sample.WindowEx"))
            assertTrue(WinUIXamlHotReloadMarkup.parse(source).isWindow(null))
        } finally {
            Files.deleteIfExists(metadata)
            Files.delete(folder)
        }
    }

    @Test fun designer_content_checks_vector_element_type_and_scalar_writability() {
        val folder = Files.createTempDirectory("winrt-content-types-")
        val metadata = folder.resolve("Controls.winmd")
        try {
            // CsWinRT code_writers.h collection writers retain their generic element type;
            // the designer must retain that same constraint when inserting source children.
            val ns = "Microsoft.UI.Xaml"
            fun type(name: String, base: String? = null) = WinUIXamlApplicationTypeDescriptor("$ns.$name", base?.let { "$ns.$it" })
            fun member(name: String, type: String, readOnly: Boolean = false) = io.github.composefluent.winrt.metadata.WinUIXamlApplicationTypeMembers(
                properties = listOf(io.github.composefluent.winrt.metadata.WinUIXamlApplicationProperty(name,
                    io.github.composefluent.winrt.metadata.WinRTTypeRef.fromDisplayName(type), isReadOnly = readOnly)), contentProperty = name)
            WinRTPortableExecutableMetadataWriter.writeXamlSchemaWinmd("Controls", listOf(
                type("UIElement"), type("Button", "UIElement"), type("Panel", "UIElement"), type("StackPanel", "Panel"),
                type("Inline"), type("TextBlock", "UIElement"), type("Border", "UIElement"), type("ReadOnlyHost", "UIElement")
            ), mapOf(
                "$ns.Panel" to member("Children", "Windows.Foundation.Collections.IVector<$ns.UIElement>", true),
                "$ns.TextBlock" to member("Inlines", "Windows.Foundation.Collections.IVector<$ns.Inline>", true),
                "$ns.Border" to member("Child", "$ns.UIElement"),
                "$ns.ReadOnlyHost" to member("Child", "$ns.UIElement", true)
            ), metadata, mapOf("Microsoft.UI.Xaml.Markup.ContentPropertyAttribute" to "Microsoft.UI.Xaml",
                "Windows.Foundation.Collections.IVector`1" to "Windows.Foundation.FoundationContract",
                "Windows.Foundation.Collections.IVector" to "Windows.Foundation.FoundationContract"))
            val catalog = WinUIXamlCatalog(WinRTMetadataLoader.load(listOf(metadata)))
            val button = catalog.types.getValue("$ns.Button")
            assertTrue(catalog.acceptsContent(catalog.types.getValue("$ns.StackPanel"), button))
            assertTrue(catalog.acceptsContent(catalog.types.getValue("$ns.Border"), button))
            assertFalse(catalog.acceptsContent(catalog.types.getValue("$ns.TextBlock"), button))
            assertFalse(catalog.acceptsContent(catalog.types.getValue("$ns.ReadOnlyHost"), button))
        } finally { Files.deleteIfExists(metadata); Files.delete(folder) }
    }

    @Test fun literal_changes_decode_xml_and_keep_compiler_fingerprint_and_version() {
        val before = WinUIXamlHotReloadMarkup.parse(source)
        val after = WinUIXamlHotReloadMarkup.parse(source.replace("Hello", "A &amp; B").replace("100", "140"))
        val patch = after.patch(before, root())
        assertEquals(before.hash, patch.expectedHash)
        assertEquals(after.hash, patch.sourceHash)
        assertEquals(1L, patch.version)
        assertEquals(listOf(WinUIXamlHotReloadChange("Greeting", "Text", "A & B"), WinUIXamlHotReloadChange("Greeting", "Width", "140")), patch.changes.sortedBy { it.property })
        val formatted = WinUIXamlHotReloadMarkup.parse(source.replace("<StackPanel>", "\n  <StackPanel>"))
        assertTrue(formatted.patch(before, root()).changes.isEmpty())
        val escaped = WinUIXamlHotReloadMarkup.parse(source.replace("Hello", "{}{literal}"))
        assertEquals("{literal}", escaped.patch(before, root()).changes.single().literal)
    }

    @Test fun lifetime_and_connection_changes_require_rebuilding() {
        val before = WinUIXamlHotReloadMarkup.parse(source)
        val unsupported = listOf(
            source.replace("TextBlock", "TextBox"), source.replace("Greeting", "Renamed"),
            source.replace("Hello", "{Binding Title}"),
            source.replace("x:Class=\"probe.Window\"", "x:Class=\"probe.Other\""),
            source.replace("<StackPanel>", "<StackPanel><TextBlock/>"), source.replace("Text=\"Hello\"", "Grid.Row=\"2\" Text=\"Hello\""),
        )
        unsupported.forEach { text -> assertTrue(text, runCatching { WinUIXamlHotReloadMarkup.parse(text).patch(before, root()) }.isFailure) }
        assertTrue(runCatching { WinUIXamlHotReloadMarkup.parse(source.replace("Hello", "changed")).patch(before, root().copy(sourceHash = "0".repeat(64))) }.isFailure)
        val template = source.replace("<StackPanel>", "<StackPanel><DataTemplate><TextBlock x:Name=\"Greeting\" Text=\"template\"/></DataTemplate>")
        assertTrue(runCatching { WinUIXamlHotReloadMarkup.parse(template.replace("template", "changed"))
            .patch(WinUIXamlHotReloadMarkup.parse(template), root(template)) }.isFailure)
        assertTrue(runCatching { WinUIXamlHotReloadMarkup.parse("<!DOCTYPE Window [<!ENTITY x SYSTEM 'file:///secret'>]>$source") }.isFailure)
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
        val before = WinUIXamlHotReloadMarkup.parse(resourceSource)
        val after = WinUIXamlHotReloadMarkup.parse(resourceSource.replace("#FF0067C0", "#FF00AA44"))
        val patch = after.patch(before, resourceRoot())
        assertTrue(patch.resources.isEmpty())
        assertEquals(WinUIXamlHotReloadChange("Layout", "Color", "#FF00AA44", listOf(
            WinUIXamlHotReloadStep.Property("Resources"), WinUIXamlHotReloadStep.Key("Accent"))), patch.changes.single())
        val themed = resourceSource.replace("Foreground=\"{StaticResource Accent}\"", "Foreground=\"{ThemeResource Accent}\"")
        assertEquals(1, WinUIXamlHotReloadMarkup.parse(themed.replace("#FF0067C0", "#FF00AA44"))
            .patch(WinUIXamlHotReloadMarkup.parse(themed), resourceRoot(themed)).changes.size)
    }

    @Test fun sealed_styles_reload_the_local_dictionary_and_its_explicit_consumers() {
        val before = WinUIXamlHotReloadMarkup.parse(resourceSource)
        val patch = WinUIXamlHotReloadMarkup.parse(resourceSource.replace("Value=\"28\"", "Value=\"36\""))
            .patch(before, resourceRoot())
        assertTrue(patch.changes.isEmpty())
        val replacement = patch.resources.single()
        assertEquals(WinUIXamlHotReloadTarget("Layout", listOf(WinUIXamlHotReloadStep.Property("Resources"))), replacement.target)
        assertEquals(listOf("Accent", "GreetingStyle"), replacement.expectedKeys)
        assertEquals(setOf("Greeting" to "Style", "Greeting" to "Foreground", "Second" to "Style"),
            replacement.references.map { it.target.element to it.property }.toSet())
        assertTrue(replacement.xaml, replacement.xaml.startsWith("<ResourceDictionary"))
        assertTrue(replacement.xaml.contains("xmlns:x=\"http://schemas.microsoft.com/winfx/2006/xaml\""))
        assertTrue(replacement.xaml.contains("Value=\"36\""))
        assertTrue(replacement.xaml.contains("{StaticResource Accent}"))
        // Implicit Resources syntax uses the same runtime dictionary contract.
        val implicit = resourceSource.replace("<ResourceDictionary>", "").replace("</ResourceDictionary>", "")
        assertEquals(1, WinUIXamlHotReloadMarkup.parse(implicit.replace("Value=\"28\"", "Value=\"36\""))
            .patch(WinUIXamlHotReloadMarkup.parse(implicit), resourceRoot(implicit)).resources.size)
    }

    @Test fun nested_theme_and_merged_brushes_update_in_place_without_replacing_resource_expressions() {
        val text = """<Page xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" x:Class="sample.Page">
          <Page.Resources><ResourceDictionary><ResourceDictionary.ThemeDictionaries>
          <ResourceDictionary x:Key="Dark"><SolidColorBrush x:Key="Accent" Color="Red"/></ResourceDictionary>
          </ResourceDictionary.ThemeDictionaries><ResourceDictionary.MergedDictionaries>
          <ResourceDictionary><SolidColorBrush x:Key="Merged" Color="Blue"/></ResourceDictionary>
          </ResourceDictionary.MergedDictionaries></ResourceDictionary></Page.Resources>
          <TextBlock Foreground="{ThemeResource Accent}"/></Page>"""
        val before = WinUIXamlHotReloadMarkup.parse(text)
        val loaded = WinUIXamlHotReloadRoot("sample.Page", "Page.xaml", before.hash, 0, emptyList())
        val patch = WinUIXamlHotReloadMarkup.parse(text.replace("Color=\"Red\"", "Color=\"Green\"").replace("Color=\"Blue\"", "Color=\"Yellow\""))
            .patch(before, loaded)
        assertTrue(patch.resources.isEmpty())
        assertEquals(listOf("Green", "Yellow"), patch.changes.map { it.literal })
        assertEquals(listOf(WinUIXamlHotReloadStep.Property("Resources"), WinUIXamlHotReloadStep.Property("ThemeDictionaries"),
            WinUIXamlHotReloadStep.Key("Dark"), WinUIXamlHotReloadStep.Key("Accent")), patch.changes[0].path)
        assertEquals(listOf(WinUIXamlHotReloadStep.Property("Resources"), WinUIXamlHotReloadStep.Property("MergedDictionaries"),
            WinUIXamlHotReloadStep.Index(0, 1), WinUIXamlHotReloadStep.Key("Merged")), patch.changes[1].path)
        assertTrue(runCatching { WinUIXamlHotReloadMarkup.parse(text.replace("x:Key=\"Dark\"", "x:Key=\"Light\""))
            .patch(before, loaded) }.isFailure)
        val standalone = """<ResourceDictionary xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" x:Class="sample.Colors"><SolidColorBrush x:Key="Accent" Color="Red"/></ResourceDictionary>"""
        val original = WinUIXamlHotReloadMarkup.parse(standalone)
        val direct = WinUIXamlHotReloadMarkup.parse(standalone.replace("Red", "Blue")).patch(original,
            WinUIXamlHotReloadRoot("sample.Colors", "Colors.xaml", original.hash, 0, emptyList()))
        assertEquals(listOf(WinUIXamlHotReloadStep.Key("Accent")), direct.changes.single().path)
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
            assertTrue(text, runCatching { WinUIXamlHotReloadMarkup.parse(text.replace("Value=\"28\"", "Value=\"36\""))
                .patch(WinUIXamlHotReloadMarkup.parse(text), resourceRoot(text)) }.isFailure)
        }
        val added = resourceSource.replace("</ResourceDictionary>", "<SolidColorBrush x:Key=\"Extra\"/></ResourceDictionary>")
        val patch = WinUIXamlHotReloadMarkup.parse(added).patch(WinUIXamlHotReloadMarkup.parse(resourceSource), resourceRoot())
        assertEquals(listOf("Accent", "GreetingStyle"), patch.resources.single().expectedKeys)
        assertTrue(patch.resources.single().xaml.contains("Extra"))
        val removed = resourceSource.replace(Regex("<SolidColorBrush[^>]+/>"), "")
        assertTrue("A still-referenced key cannot be removed", runCatching {
            WinUIXamlHotReloadMarkup.parse(removed).patch(WinUIXamlHotReloadMarkup.parse(resourceSource), resourceRoot())
        }.isFailure)
    }

    private fun content(uri: String, type: String, property: String?): WinUIXamlContentMember? =
        if (uri != WinUIXamlCatalog.PRESENTATION) null else when (property ?: type) {
            "Window" -> WinUIXamlContentMember("Content", false)
            "StackPanel", "Children" -> WinUIXamlContentMember("Children", true)
            "Text", "FontSize", "Width", "Background", "Color" -> WinUIXamlContentMember(property!!, false)
            else -> null
        }

    @Test fun sealed_styles_refresh_unnamed_consumers_through_compiled_content_paths() {
        for (explicit in listOf(false, true)) {
            var text = resourceSource.replace(" x:Name=\"Second\"", "")
            if (explicit) text = text.replace("<TextBlock x:Name=\"Greeting\"", "<StackPanel.Children><TextBlock x:Name=\"Greeting\"")
                .replace("</StackPanel>", "</StackPanel.Children></StackPanel>")
            val patch = WinUIXamlHotReloadMarkup.parse(text.replace("Value=\"28\"", "Value=\"36\""))
                .patch(WinUIXamlHotReloadMarkup.parse(text), resourceRoot(text), contentMember = ::content)
            val consumer = patch.resources.single().references.single { it.property == "Style" && it.target.element != "Greeting" }
            assertEquals(WinUIXamlHotReloadTarget("Layout", listOf(WinUIXamlHotReloadStep.Property("Children"),
                WinUIXamlHotReloadStep.Index(1, 2))), consumer.target)
        }
    }

    @Test fun child_collections_reuse_connected_objects_and_load_only_unconnected_subtrees() {
        val first = "<TextBlock x:Name=\"Greeting\" Text=\"Hello\" Style=\"{StaticResource GreetingStyle}\" Foreground=\"{StaticResource Accent}\"/>"
        val second = "<TextBlock x:Name=\"Second\" Style=\"{StaticResource ResourceKey=GreetingStyle}\"/>"
        val next = resourceSource.replace(first, "").replace(second, "$second<TextBlock Text=\"New\"/>$first")
        val patch = WinUIXamlHotReloadMarkup.parse(next).patch(WinUIXamlHotReloadMarkup.parse(resourceSource), resourceRoot(), contentMember = ::content)
        val update = patch.children.single()
        assertEquals(WinUIXamlHotReloadTarget("Layout", listOf(WinUIXamlHotReloadStep.Property("Children"))), update.target)
        assertEquals(2, update.expectedSize)
        assertEquals(WinUIXamlHotReloadItem.Existing(1), update.items[0]); assertEquals(WinUIXamlHotReloadItem.Existing(0), update.items[2])
        val fragment = (update.items[1] as WinUIXamlHotReloadItem.Markup).xaml
        assertTrue(fragment, fragment.contains("Text=\"New\"")); assertTrue(fragment.contains("xmlns=\"${WinUIXamlCatalog.PRESENTATION}\""))
        assertTrue(patch.changes.isEmpty()); assertTrue(patch.resources.isEmpty())
        val removed = next.replace("<TextBlock Text=\"New\"/>", "")
        val deletion = WinUIXamlHotReloadMarkup.parse(removed).patch(WinUIXamlHotReloadMarkup.parse(next), resourceRoot(next), contentMember = ::content)
        assertEquals(listOf(WinUIXamlHotReloadItem.Existing(0), WinUIXamlHotReloadItem.Existing(2)), deletion.children.single().items)
        // Unnamed members are addressed through the compiler-owned getter and
        // checked list index, rather than an invented native name scope.
        val unnamed = source.replace(" x:Name=\"Greeting\"", "")
        val edited = WinUIXamlHotReloadMarkup.parse(unnamed.replace("Hello", "changed"))
            .patch(WinUIXamlHotReloadMarkup.parse(unnamed), root(unnamed), contentMember = ::content)
        assertEquals(listOf(WinUIXamlHotReloadStep.Property("Content"), WinUIXamlHotReloadStep.Property("Children"),
            WinUIXamlHotReloadStep.Index(0, 1)), edited.changes.single().path)
    }

    /** CsWinRT ObjectLifetimeTests/CustomGroupedItemPages.cs uses the SDK loader
     * for property elements; no new runtime parser or connection contract is needed. */
    @Test fun new_sdk_subtrees_accept_resolved_property_elements_without_connections() {
        val added = "<Border><Border.Background><SolidColorBrush Color=\"Red\"/></Border.Background></Border>"
        val next = source.replace("<StackPanel>", "<StackPanel>$added")
        val patch = WinUIXamlHotReloadMarkup.parse(next).patch(WinUIXamlHotReloadMarkup.parse(source), root(), contentMember = ::content)
        assertTrue((patch.children.single().items.first() as WinUIXamlHotReloadItem.Markup).xaml.contains("Border.Background"))
        val removal = WinUIXamlHotReloadMarkup.parse(source).patch(WinUIXamlHotReloadMarkup.parse(next), root(next), contentMember = ::content)
        assertEquals(listOf(WinUIXamlHotReloadItem.Existing(1)), removal.children.single().items)
        listOf(
            added.replace("Border.Background", "Border.Unknown"),
            added.replace("Border.Background", "Border.Loaded"),
            added.replace("Border.Background", "Button.Background"),
            added.replace("Color=\"Red\"", "Color=\"{Binding Color}\""),
            added.replace("<SolidColorBrush", "<SolidColorBrush x:Name=\"Brush\""),
            added.replace("<Border.Background>", "<Border.Background x:Uid=\"Brush\">"),
        ).forEach { invalid -> assertTrue(invalid, runCatching {
            WinUIXamlHotReloadMarkup.parse(source.replace("<StackPanel>", "<StackPanel>$invalid"))
                .patch(WinUIXamlHotReloadMarkup.parse(source), root(), contentMember = ::content)
        }.isFailure) }
    }

    @Test fun child_changes_reject_compiled_connections_and_authored_component_lifetimes() {
        val before = WinUIXamlHotReloadMarkup.parse(source)
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
            WinUIXamlHotReloadMarkup.parse(text).patch(before, root(), propertyProblem = { _, _, property ->
                if (property == "Click") "Event connections require rebuilding." else null
            }, contentMember = ::content)
        }.isFailure) }
        val eventSource = source.replace("<StackPanel>", "<StackPanel><Button Click=\"onClick\"/>")
        assertTrue("Missing event metadata must reject removal, not leave a compiled subscription alive", runCatching {
            WinUIXamlHotReloadMarkup.parse(source).patch(WinUIXamlHotReloadMarkup.parse(eventSource), root(eventSource), contentMember = ::content)
        }.isFailure)
    }

    private fun sdkCatalog(): WinUIXamlCatalog {
        val input = Path.of(requireNotNull(System.getProperty("winrt.ide.xamlInput")))
        val paths = Json.parseToJsonElement(Files.readString(input)).jsonObject.getValue("ReferenceAssemblies").jsonArray.map {
            Path.of(it.jsonObject.getValue("FullPath").jsonPrimitive.content)
        }.filter(Files::isRegularFile)
        return WinUIXamlCatalog(WinRTMetadataLoader.load(paths))
    }

    @Test fun real_sdk_content_properties_and_vector_interfaces_drive_collection_updates() {
        assumeTrue(System.getProperty("winrt.ide.xamlInput") != null)
        val catalog = sdkCatalog()
        val stack = requireNotNull(catalog.resolve(WinUIXamlCatalog.PRESENTATION, "StackPanel"))
        assertEquals(WinUIXamlContentMember("Children", true), catalog.contentMember(stack))
        assertEquals(WinUIXamlContentMember("Children", true), catalog.propertyContent(stack, "Children"))
        assertEquals(WinUIXamlContentMember("Content", false), catalog.contentMember(requireNotNull(catalog.resolve(WinUIXamlCatalog.PRESENTATION, "Window"))))
    }

    @Test fun loopback_client_authenticates_and_does_not_expose_credentials() {
        val folder = Files.createTempDirectory("winrt-hot-client-")
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val executor = Executors.newSingleThreadExecutor()
        val token = "t".repeat(43)
        val file = folder.resolve("probe.session")
        try {
            Files.newOutputStream(file).use { Properties().apply {
                setProperty("protocol", WinUIXamlHotReloadProtocol.VERSION.toString()); setProperty("pid", ProcessHandle.current().pid().toString())
                setProperty("port", server.localPort.toString()); setProperty("token", token)
            }.store(it, "probe") }
            val response = executor.submit {
                server.accept().use { socket ->
                    val (supplied, patch) = WinUIXamlHotReloadWire.readRequest(socket.getInputStream())
                    assertEquals(token, supplied); assertNull(patch)
                    WinUIXamlHotReloadWire.writeReply(socket.getOutputStream(), WinUIXamlHotReloadReply(0, "connected", listOf(root())))
                }
            }
            WinUIXamlHotReloadClient.read(file).use { client ->
                assertFalse(client.toString().contains(token))
                assertEquals("connected", client.request().message)
                assertEquals(ProcessHandle.current().pid(), client.process.pid())
            }
            response.get(5, TimeUnit.SECONDS)
            assertTrue(runCatching { WinUIXamlHotReloadClient.read(file, "C:/not-this-process.exe") }.isFailure)
        } finally { server.close(); executor.shutdownNow(); Files.deleteIfExists(file); Files.deleteIfExists(folder) }
    }

    @Test fun real_uwp_native_appcontainer_updates_named_properties_and_rolls_back_invalid_changes() {
        val directory = System.getProperty("winrt.ide.uwpHotReloadSession")
        assumeTrue("Supply a real Native UWP application session", directory != null)
        val clients = WinUIXamlHotReloadClient.discover(Path.of(directory))
        assertEquals(1, clients.size)
        clients.single().use { client ->
            val root = client.request().roots.single { it.className.endsWith(".MainPage") && "resultText" in it.elements }
            val patch = WinUIXamlHotReloadPatch(root.className, root.resourcePath, root.sourceHash, "b".repeat(64), root.version + 1,
                listOf(WinUIXamlHotReloadChange("resultText", "Text", "Reloaded inside AppContainer"),
                    WinUIXamlHotReloadChange("incrementButton", "Content", "Increment after reload")))
            val applied = client.request(patch)
            assertEquals(applied.message, WinUIXamlHotReloadProtocol.APPLIED, applied.status)
            assertEquals("Reloaded inside AppContainer", applied.values.single { it.property == "Text" }.value)
            assertEquals("Increment after reload", applied.values.single { it.property == "Content" }.value)
            val current = applied.roots.single { it.className == root.className }
            val invalid = patch.copy(expectedHash = current.sourceHash, sourceHash = "c".repeat(64), version = current.version + 1,
                changes = listOf(WinUIXamlHotReloadChange("resultText", "Text", "must roll back"),
                    WinUIXamlHotReloadChange("resultText", "Width", "-1")))
            val rejected = client.request(invalid)
            assertEquals(rejected.message, WinUIXamlHotReloadProtocol.REJECTED, rejected.status)
            val inspected = client.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath, capture = false))
            val node = inspected.inspection!!.nodes.single { it.name == "resultText" }
            val selected = client.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath, selectedPath = node.path, capture = false))
            assertEquals("Reloaded inside AppContainer", selected.inspection!!.properties.single { it.name == "Text" }.value)
            assertEquals(current.sourceHash, selected.roots.single { it.className == root.className }.sourceHash)
        }
    }

    @Test fun grid_definition_insertions_use_the_property_vector() {
        val text = """<Window xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" x:Class="probe.Window"><Grid x:Name="Greeting"><Grid.RowDefinitions><RowDefinition Height="*"/></Grid.RowDefinitions></Grid></Window>"""
        val changed = text.replace("</Grid.RowDefinitions>", "<RowDefinition Height=\"Auto\"/></Grid.RowDefinitions>")
        val update = WinUIXamlHotReloadMarkup.parse(changed).patch(WinUIXamlHotReloadMarkup.parse(text), root(text),
            contentMember = { _, type, property -> when {
                type == "Window" -> WinUIXamlContentMember("Content", false)
                type == "Grid" && property == "RowDefinitions" -> WinUIXamlContentMember(property, true)
                type == "RowDefinition" && property == "Height" -> WinUIXamlContentMember(property, false)
                else -> null
            } })
        val vector = update.children.single()
        assertEquals(WinUIXamlHotReloadTarget("Greeting", listOf(WinUIXamlHotReloadStep.Property("RowDefinitions"))), vector.target)
        assertEquals(WinUIXamlHotReloadItem.Existing(0), vector.items[0])
        assertTrue((vector.items[1] as WinUIXamlHotReloadItem.Markup).xaml.contains("RowDefinition"))
    }

    @Test fun keyed_presentation_templates_replace_through_resource_transactions() {
        val text = """<Window xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" x:Class="probe.Window"><Grid><Grid.Resources><DataTemplate x:Key="Card"><TextBlock Text="Before"/></DataTemplate></Grid.Resources><ContentControl x:Name="Greeting" ContentTemplate="{StaticResource Card}"/></Grid></Window>"""
        fun patch(old: String, next: String) = WinUIXamlHotReloadMarkup.parse(next).patch(
            WinUIXamlHotReloadMarkup.parse(old), root(old), contentMember = { _, type, property ->
                when { type == "Window" -> WinUIXamlContentMember("Content", false)
                    type == "Grid" && property == null -> WinUIXamlContentMember("Children", true)
                    else -> null }
            }, propertyProblem = { _, _, member -> if (member == "Tapped") "An event needs a compiled connection" else null })
        val update = patch(text, text.replace("Before", "After"))
        assertTrue(update.changes.isEmpty())
        val replacement = update.resources.single()
        assertTrue(replacement.xaml.contains("After"))
        assertEquals(listOf("Card"), replacement.expectedKeys)
        assertEquals(listOf(WinUIXamlHotReloadResourceReference(WinUIXamlHotReloadTarget("Greeting"), "ContentTemplate", "Card")), replacement.references)
        for (unsafe in listOf("Text=\"{x:Bind Title}\"", "Text=\"{Binding Title}\"", "x:Name=\"Connected\"", "Tapped=\"Handler\"")) {
            assertTrue(unsafe, runCatching { patch(text, text.replace("Text=\"Before\"", unsafe)) }.isFailure)
        }
        val themed = text.replace("StaticResource", "ThemeResource")
        assertTrue(runCatching { patch(themed, themed.replace("Before", "After")) }.isFailure)
    }

    @Test fun real_native_host_restores_control_state_without_overwriting_compiled_binding() {
        val directory = System.getProperty("winrt.ide.hotReloadAttachedSession")
        assumeTrue("Supply the Native restart-state fixture session", directory != null)
        WinUIXamlHotReloadClient.discover(Path.of(directory)).single().use { client ->
            val root = client.request().roots.single { it.className.endsWith(".MainWindow") && "ReloadInput" in it.elements }
            val request = WinUIXamlStateRequest(root.className, root.resourcePath)
            fun capture(): List<WinUIXamlSavedElement> {
                val reply = client.transferState(request)
                assertEquals(reply.message, WinUIXamlHotReloadProtocol.APPLIED, reply.status)
                return requireNotNull(reply.savedState)
            }
            val original = capture()
            assertTrue(original.single { it.name == "BoundInput" }.values.none { it.name == "Text" })
            fun text(name: String): String {
                val view = client.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath, capture = false)).inspection!!
                val node = view.nodes.single { it.name == name }
                return client.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath,
                    selectedPath = node.path, capture = false)).inspection!!.properties.single { it.name == "Text" }.value
            }
            val bound = text("BoundInput")
            val replacements = mapOf("ReloadInput" to ("Text" to "Recovered input"),
                "ReloadToggle" to ("IsOn" to "true"), "ReloadSlider" to ("Value" to "75.0"),
                "ReloadChoice" to ("SelectedIndex" to "1"), "BoundInput" to ("Text" to "Must not overwrite binding"))
            try {
                val changed = original.map { entry -> replacements[entry.name]?.let { (property, value) ->
                    entry.copy(values = entry.values.filterNot { it.name == property } + WinUIXamlVisualProperty(property, value))
                } ?: entry }
                val restored = client.transferState(request.copy(restore = changed))
                assertEquals(restored.message, WinUIXamlHotReloadProtocol.APPLIED, restored.status)
                val actual = capture().associateBy { it.name }
                replacements.filterKeys { it != "BoundInput" }.forEach { (name, property) ->
                    assertEquals(name, property.second, actual.getValue(name).values.single { it.name == property.first }.value)
                }
                assertEquals(bound, text("BoundInput"))
                assertEquals(root.sourceHash, client.request().roots.single { it.className == root.className }.sourceHash)
            } finally {
                val restored = client.transferState(request.copy(restore = original))
                assertEquals(restored.message, WinUIXamlHotReloadProtocol.APPLIED, restored.status)
            }
        }
    }

    @Test fun real_native_host_replaces_and_validates_keyed_presentation_templates() {
        val directory = System.getProperty("winrt.ide.hotReloadAttachedSession")
        assumeTrue("Supply the Native layout application session", directory != null)
        WinUIXamlHotReloadClient.discover(Path.of(directory)).single().use { client ->
            val root = client.request().roots.single { it.className.endsWith(".MainWindow") && "First" in it.elements }
            val address = WinUIXamlHotReloadTarget("First", listOf(WinUIXamlHotReloadStep.Property("Resources")))
            fun dictionary(text: String) = """<ResourceDictionary xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"><DataTemplate x:Key="ReloadCard"><TextBlock Text="$text"/></DataTemplate></ResourceDictionary>"""
            var current = root
            fun apply(markup: String, keys: List<String>, reset: Boolean = false): WinUIXamlHotReloadReply {
                val nextHash = if (reset) root.sourceHash else (if (current.sourceHash == "6".repeat(64)) "7" else "6").repeat(64)
                val update = WinUIXamlHotReloadPatch(root.className, root.resourcePath, current.sourceHash, nextHash,
                    current.version + 1, if (reset) listOf(WinUIXamlHotReloadChange("First", "ContentTemplate", "", reset = true)) else emptyList(),
                    resources = listOf(WinUIXamlHotReloadResources(address, markup, keys, if (reset) emptyList() else listOf(
                        WinUIXamlHotReloadResourceReference(WinUIXamlHotReloadTarget("First"), "ContentTemplate", "ReloadCard")))))
                return client.request(update).also { reply ->
                    if (reply.status == WinUIXamlHotReloadProtocol.APPLIED) current = reply.roots.single { it.className == root.className }
                }
            }
            fun renderedText(): String {
                val view = client.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath)).inspection!!
                val button = view.nodes.single { it.name == "First" }
                return view.nodes.filter { it.path.take(button.path.size) == button.path && it.typeName.endsWith(".TextBlock") }
                    .map { node -> client.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath,
                        selectedPath = node.path, capture = false)).inspection!!.properties.firstOrNull { it.name == "Text" }?.value.orEmpty() }
                    .joinToString()
            }
            val first = apply(dictionary("Template before"), emptyList())
            assertEquals(first.message, WinUIXamlHotReloadProtocol.APPLIED, first.status)
            try {
                assertTrue(renderedText().contains("Template before"))
                val second = apply(dictionary("Template after"), listOf("ReloadCard"))
                assertEquals(second.message, WinUIXamlHotReloadProtocol.APPLIED, second.status)
                assertTrue(renderedText().contains("Template after"))
                val invalid = apply(dictionary("invalid").replace("TextBlock", "MissingControl"), listOf("ReloadCard"))
                assertEquals(invalid.message, WinUIXamlHotReloadProtocol.REJECTED, invalid.status)
                assertTrue(renderedText().contains("Template after"))
            } finally {
                val restored = apply("""<ResourceDictionary xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"/>""", listOf("ReloadCard"), true)
                assertEquals(restored.message, WinUIXamlHotReloadProtocol.APPLIED, restored.status)
            }
        }
    }

    @Test fun real_native_host_updates_non_content_grid_definitions() {
        val directory = System.getProperty("winrt.ide.hotReloadAttachedSession")
        assumeTrue("Supply the Native layout application session", directory != null)
        WinUIXamlHotReloadClient.discover(Path.of(directory)).single().use { client ->
            val root = client.request().roots.single { it.className.endsWith(".MainWindow") && "LayoutRoot" in it.elements }
            val path = listOf(WinUIXamlHotReloadStep.Property("RowDefinitions"), WinUIXamlHotReloadStep.Index(0, 2))
            val edit = WinUIXamlHotReloadPatch(root.className, root.resourcePath, root.sourceHash,
                "9".repeat(64), root.version + 1, listOf(WinUIXamlHotReloadChange("LayoutRoot", "Height", "300", path)))
            val applied = client.request(edit)
            assertEquals(applied.message, WinUIXamlHotReloadProtocol.APPLIED, applied.status)
            try {
                assertTrue(applied.values.toString(), applied.values.single().value.contains("300"))
                val rejected = client.request(edit.copy(expectedHash = edit.sourceHash, sourceHash = "8".repeat(64),
                    version = edit.version + 1, changes = listOf(WinUIXamlHotReloadChange("LayoutRoot", "Height", "-1", path))))
                assertEquals(rejected.message, WinUIXamlHotReloadProtocol.REJECTED, rejected.status)
            } finally {
                val restored = client.request(edit.copy(expectedHash = edit.sourceHash, sourceHash = root.sourceHash,
                    version = edit.version + 1, changes = listOf(WinUIXamlHotReloadChange("LayoutRoot", "Height", "*", path))))
                assertEquals(restored.message, WinUIXamlHotReloadProtocol.APPLIED, restored.status)
            }
        }
    }

    @Test fun real_native_host_reports_dependency_control_type_identity() {
        val directory = System.getProperty("winrt.ide.hotReloadAttachedSession")
        assumeTrue(directory != null)
        WinUIXamlHotReloadClient.discover(Path.of(directory)).single().use { client ->
            val root = client.request().roots.single { it.className.endsWith(".MainWindow") && "First" in it.elements }
            val reply = client.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath, capture = false))
            assertEquals(reply.message, WinUIXamlHotReloadProtocol.APPLIED, reply.status)
            assertTrue(reply.inspection!!.nodes.joinToString { it.typeName },
                reply.inspection!!.nodes.any { it.typeName == "sample.controls.GreetingControl" })
        }
    }

    @Test fun real_native_host_updates_attached_layout_without_replacing_the_control() {
        val directory = System.getProperty("winrt.ide.hotReloadAttachedSession")
        assumeTrue(directory != null)
        WinUIXamlHotReloadClient.discover(Path.of(directory)).single().use { client ->
            val root = client.request().roots.single { it.className.endsWith(".MainWindow") && "First" in it.elements }
            val property = "Microsoft.UI.Xaml.Controls.Canvas.Left"
            val patch = WinUIXamlHotReloadPatch(root.className, root.resourcePath, root.sourceHash,
                "d".repeat(64), root.version + 1, listOf(WinUIXamlHotReloadChange("First", property, "220")),
                reads = listOf(WinUIXamlHotReloadRead(WinUIXamlHotReloadTarget("First"), "Content")))
            val reply = client.request(patch)
            assertEquals(reply.message, WinUIXamlHotReloadProtocol.APPLIED, reply.status)
            assertEquals(220.0, reply.values.single { it.property == property }.value.toDouble(), 0.0)
            assertEquals("First", reply.values.single { it.property == "Content" }.value)
            val rejected = client.request(patch.copy(expectedHash = patch.sourceHash, sourceHash = "e".repeat(64),
                version = patch.version + 1, changes = listOf(
                    WinUIXamlHotReloadChange("First", property, "300"),
                    WinUIXamlHotReloadChange("First", "Width", "-1"))))
            assertEquals(rejected.message, WinUIXamlHotReloadProtocol.REJECTED, rejected.status)
            val readback = client.request(patch.copy(expectedHash = patch.sourceHash, sourceHash = "f".repeat(64),
                version = patch.version + 1, changes = emptyList(),
                reads = listOf(WinUIXamlHotReloadRead(WinUIXamlHotReloadTarget("First"), property))))
            assertEquals(readback.message, WinUIXamlHotReloadProtocol.APPLIED, readback.status)
            assertEquals(220.0, readback.values.single().value.toDouble(), 0.0)
        }
    }

    /** A real compiler-generated WinUI host supplies the session; no test-only XAML runtime glue. */
    @Test fun real_winui_host_updates_text_dimensions_and_brush_on_its_ui_thread() {
        val directory = System.getProperty("winrt.ide.hotReloadSession")
        assumeTrue(directory != null)
        val clients = WinUIXamlHotReloadClient.discover(Path.of(directory))
        assertEquals(1, clients.size)
        clients.single().use { client ->
            val initial = client.request()
            assertEquals(initial.message, WinUIXamlHotReloadProtocol.APPLIED, initial.status)
            val loaded = initial.roots.single { it.className.endsWith(".MainWindow") && "Greeting" in it.elements }
            val change = WinUIXamlHotReloadPatch(loaded.className, loaded.resourcePath, loaded.sourceHash, "b".repeat(64), loaded.version + 1,
                listOf(WinUIXamlHotReloadChange("Greeting", "Text", "Reloaded in place"),
                    WinUIXamlHotReloadChange("Greeting", "Width", "240"),
                    WinUIXamlHotReloadChange("Greeting", "FontSize", "24"),
                    WinUIXamlHotReloadChange("Greeting", "Foreground", "#FF0067C0")))
            val reply = client.request(change)
            assertEquals(reply.message, WinUIXamlHotReloadProtocol.APPLIED, reply.status)
            assertEquals("Reloaded in place", reply.values.single { it.property == "Text" }.value)
            assertEquals(240.0, reply.values.single { it.property == "Width" }.value.toDouble(), 0.0)
            assertEquals(24.0, reply.values.single { it.property == "FontSize" }.value.toDouble(), 0.0)
            assertEquals(4, reply.values.size)
            assertTrue("SDK color conversion must retain a native brush", reply.values.single { it.property == "Foreground" }.value.isNotBlank())
            assertTrue(client.alive)
            val updated = client.request().roots.single { it.className == loaded.className }
            assertEquals(change.version, updated.version); assertEquals(change.sourceHash, updated.sourceHash)
            val invalid = change.copy(expectedHash = updated.sourceHash, sourceHash = "c".repeat(64), version = updated.version + 1,
                changes = listOf(WinUIXamlHotReloadChange("Greeting", "Text", "must roll back"), WinUIXamlHotReloadChange("Greeting", "Width", "-1")))
            val rejected = client.request(invalid)
            assertEquals(rejected.message, WinUIXamlHotReloadProtocol.REJECTED, rejected.status)
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
        val clients = WinUIXamlHotReloadClient.discover(Path.of(directory))
        assertEquals(1, clients.size)
        clients.single().use { client ->
            fun current() = client.request().roots.single { it.className.endsWith(".MainWindow") && "Second" in it.elements }
            val resourcePath = listOf(WinUIXamlHotReloadStep.Property("Resources"), WinUIXamlHotReloadStep.Key("Accent"))
            val stylePath = listOf(WinUIXamlHotReloadStep.Property("Resources"), WinUIXamlHotReloadStep.Key("GreetingStyle"))
            val reads = listOf(
                WinUIXamlHotReloadRead(WinUIXamlHotReloadTarget("Greeting"), "FontSize"),
                WinUIXamlHotReloadRead(WinUIXamlHotReloadTarget("Second"), "FontSize"),
                WinUIXamlHotReloadRead(WinUIXamlHotReloadTarget("Layout", resourcePath), "Color"),
                WinUIXamlHotReloadRead(WinUIXamlHotReloadTarget("Greeting", listOf(WinUIXamlHotReloadStep.Property("Foreground"))), "Color"),
                WinUIXamlHotReloadRead(WinUIXamlHotReloadTarget("Second", listOf(WinUIXamlHotReloadStep.Property("Foreground"))), "Color"),
                WinUIXamlHotReloadRead(WinUIXamlHotReloadTarget("Layout", stylePath), "IsSealed"),
            )
            fun inspect(): WinUIXamlHotReloadReply {
                val root = current()
                return client.request(WinUIXamlHotReloadPatch(root.className, root.resourcePath, root.sourceHash,
                    root.sourceHash, root.version + 1, emptyList(), reads = reads)).also {
                    assertEquals(it.message, WinUIXamlHotReloadProtocol.APPLIED, it.status)
                }
            }
            fun assertState(reply: WinUIXamlHotReloadReply, font: Double): String {
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
            val before = WinUIXamlHotReloadMarkup.parse(text)
            val blue = WinUIXamlHotReloadMarkup.parse(text.replace("#FF0067C0", "#FF008844"))
            val recolor = blue.patch(before, current()).copy(reads = reads)
            val recolored = client.request(recolor)
            assertEquals(recolored.message, WinUIXamlHotReloadProtocol.APPLIED, recolored.status)
            val changedColor = assertState(recolored, 28.0)
            assertNotEquals(originalColor, changedColor)
            val restyled = WinUIXamlHotReloadMarkup.parse(blue.text.replace("Value=\"28\"", "Value=\"36\""))
            val style = restyled.patch(blue, current()).copy(reads = reads)
            assertEquals(1, style.resources.size)
            val replaced = client.request(style)
            assertEquals(replaced.message, WinUIXamlHotReloadProtocol.APPLIED, replaced.status)
            assertEquals(changedColor, assertState(replaced, 36.0))
            val latest = current()
            val rejectedStyle = style.copy(expectedHash = latest.sourceHash, sourceHash = "c".repeat(64), version = latest.version + 1,
                changes = listOf(WinUIXamlHotReloadChange("Greeting", "Width", "-1")),
                resources = style.resources.map { it.copy(xaml = it.xaml.replace("Value=\"36\"", "Value=\"48\"")) })
            val rejected = client.request(rejectedStyle)
            assertEquals(rejected.message, WinUIXamlHotReloadProtocol.REJECTED, rejected.status)
            assertEquals(latest.sourceHash, current().sourceHash)
            assertEquals(changedColor, assertState(inspect(), 36.0))
            // A subsequent in-place brush update reaches both reloaded styles,
            // demonstrating that they use the current live dictionary object.
            val finalRoot = current()
            val final = client.request(WinUIXamlHotReloadPatch(finalRoot.className, finalRoot.resourcePath,
                finalRoot.sourceHash, "d".repeat(64), finalRoot.version + 1,
                listOf(WinUIXamlHotReloadChange("Layout", "Color", "#FFCC4400", resourcePath)), reads = reads))
            assertEquals(final.message, WinUIXamlHotReloadProtocol.APPLIED, final.status)
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
        val clients = WinUIXamlHotReloadClient.discover(Path.of(directory))
        assertEquals(1, clients.size)
        clients.single().use { client ->
            fun current() = client.request().roots.single { it.className.endsWith(".MainWindow") && "Second" in it.elements }
            fun address(index: Int, size: Int) = WinUIXamlHotReloadTarget("Layout", listOf(
                WinUIXamlHotReloadStep.Property("Children"), WinUIXamlHotReloadStep.Index(index, size)))
            fun inspect(size: Int, indices: List<Int>): WinUIXamlHotReloadReply {
                val root = current()
                return client.request(WinUIXamlHotReloadPatch(root.className, root.resourcePath, root.sourceHash, root.sourceHash,
                    root.version + 1, emptyList(), reads = indices.map { WinUIXamlHotReloadRead(address(it, size), "Text") } +
                        WinUIXamlHotReloadRead(WinUIXamlHotReloadTarget("Greeting"), "Width"))).also {
                    assertEquals(it.message, WinUIXamlHotReloadProtocol.APPLIED, it.status)
                }
            }
            fun patch(before: String, after: String) = WinUIXamlHotReloadMarkup.parse(after).patch(WinUIXamlHotReloadMarkup.parse(before), current(),
                propertyProblem = { uri, type, property ->
                    if (catalog.resolve(uri, type)?.let { catalog.members(it).any { m -> m.name == property && m.isEvent } } == true)
                        "Events require rebuilding." else null
                }, contentMember = { uri, type, property -> catalog.resolve(uri, type)?.let {
                    if (property == null) catalog.contentMember(it) else catalog.propertyContent(it, property)
                } })
            fun texts(reply: WinUIXamlHotReloadReply) = reply.values.filter { it.property == "Text" }.map { it.value }
            assertEquals(listOf("Hello from Kotlin WinRT", "Shared style and brush"), texts(inspect(3, listOf(0, 1))))
            // A live value absent from XAML must survive the reorder.
            val root = current()
            val state = client.request(WinUIXamlHotReloadPatch(root.className, root.resourcePath, root.sourceHash, root.sourceHash,
                root.version + 1, listOf(WinUIXamlHotReloadChange("Greeting", "Width", "271"))))
            assertEquals(state.message, WinUIXamlHotReloadProtocol.APPLIED, state.status)
            val added = "<TextBlock Text=\"Added at runtime\" FontSize=\"19\"/>"
            val reordered = text.replace(first, "").replace(second, "$second$added$first")
            val update = patch(text, reordered).copy(reads = listOf(WinUIXamlHotReloadRead(address(1, 4), "Text")))
            assertEquals(1, update.children.size)
            val applied = client.request(update)
            assertEquals(applied.message, WinUIXamlHotReloadProtocol.APPLIED, applied.status)
            val live = inspect(4, listOf(0, 1, 2))
            assertEquals(listOf("Shared style and brush", "Added at runtime", "Hello from Kotlin WinRT"), texts(live))
            assertEquals(271.0, live.values.single { it.property == "Width" }.value.toDouble(), 0.0)
            val invalidText = reordered.replace(added, "<TextBlock Text=\"Must roll back\"/>$added")
            val invalid = patch(reordered, invalidText).copy(changes = listOf(WinUIXamlHotReloadChange("Greeting", "Width", "-1")))
            assertEquals(1, invalid.children.size)
            val fingerprint = current().sourceHash
            val rejected = client.request(invalid)
            assertEquals(rejected.message, WinUIXamlHotReloadProtocol.REJECTED, rejected.status)
            assertEquals(fingerprint, current().sourceHash)
            assertEquals(texts(live), texts(inspect(4, listOf(0, 1, 2))))
            val deleted = reordered.replace(added, "")
            val removal = client.request(patch(reordered, deleted))
            assertEquals(removal.message, WinUIXamlHotReloadProtocol.APPLIED, removal.status)
            val final = inspect(3, listOf(0, 1))
            assertEquals(listOf("Shared style and brush", "Hello from Kotlin WinRT"), texts(final))
            assertEquals(271.0, final.values.single { it.property == "Width" }.value.toDouble(), 0.0)
            assertTrue(client.alive)
        }
    }
}
