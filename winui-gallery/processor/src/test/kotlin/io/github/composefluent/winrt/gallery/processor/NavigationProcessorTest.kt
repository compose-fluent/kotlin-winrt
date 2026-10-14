package io.github.composefluent.winrt.gallery.processor

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.nio.file.Path
import kotlin.test.*

class NavigationProcessorTest {
    private val home = Entry("GalleryPage", mapOf("route" to "Home", "title" to "Home", "group" to "", "order" to "-1", "glyph" to "\uE80F"), "GalleryShell.home", "GalleryShell.kt", "winui-gallery/src/winuiMain/kotlin/GalleryShell.kt")

    @Test
    fun glyph_order_and_factory_come_from_kotlin_annotations() {
            val entries = listOf(
                Entry("GalleryGroupEntry", mapOf("route" to "Input", "title" to "Input", "glyph" to "\uE73A", "order" to "3"), "Input", "navigation.kt"),
                Entry("GalleryPage", mapOf("route" to "Button", "title" to "Push button", "group" to "Input", "order" to "0", "glyph" to "\uE8A7"), "io.github.composefluent.winrt.gallery.basicinput.buttonPage", "ButtonPage.kt", "winui-gallery/src/winuiMain/kotlin/basicinput/ButtonPage.kt"),
                home,
            )
            assertEquals("\uE73A", entries.first().value("glyph"))
            val output = generate(entries, Json.parseToJsonElement("""
                {"Groups":[{"UniqueId":"OldGroup","Title":"Old title","Items":[
                    {"UniqueId":"Button","Title":"Old button title","Description":"A button","IsUpdated":true,
                     "ApiNamespace":"Microsoft.UI.Xaml.Controls","BaseClasses":["DependencyObject","UIElement","Button"],
                     "SourcePath":"/Button","IsExperimental":true}
                ]}]}
            """).jsonObject)
            assertContains(output, "GalleryGroup(\"Input\", \"Input\", \"\uE73A\"")
            assertContains(output, "GalleryPageInfo(\"Button\", \"Push button\"")
            assertContains(output, "\"Input\", false, true, listOf(")
            assertContains(output, "\"Button\" -> io.github.composefluent.winrt.gallery.basicinput.buttonPage()")
            assertContains(output, "actual val home: GalleryPageInfo")
            assertContains(output, "\"Microsoft.UI.Xaml.Controls\", listOf(\"DependencyObject\", \"UIElement\", \"Button\"), \"/Button\", true")
            assertContains(output, "\"winui-gallery/src/winuiMain/kotlin/basicinput/ButtonPage.kt\"")
            assertContains(output, "repositorySourcePath = \"winui-gallery/src/winuiMain/kotlin/GalleryShell.kt\"")
            assertFalse(output.contains("Old title"))
    }

    @Test
    fun route_collisions_fail_instead_of_generating_ambiguous_navigation() {
        val group = Entry("GalleryGroupEntry", mapOf("route" to "Input", "title" to "Input", "glyph" to "x", "order" to "0"), "Input", "groups.kt")
        val catalog = Json.parseToJsonElement("""{"Groups":[]}""").jsonObject
        assertFailsWith<IllegalArgumentException> { generate(listOf(group, group, home), catalog) }
    }

    @Test
    fun gallery_page_must_reference_a_declared_group() {
        val sample = Entry("GalleryPage", mapOf("route" to "Missing", "title" to "Missing", "group" to "MissingGroup", "order" to "0"), "missingPage", "samples.kt", "winui-gallery/src/winuiMain/kotlin/samples.kt")
        val catalog = Json.parseToJsonElement("""{"Groups":[]}""").jsonObject
        assertFailsWith<IllegalArgumentException> { generate(listOf(sample, home), catalog) }
    }

    @Test
    fun navigation_page_uses_the_annotated_function_as_its_factory() {
        val group = Entry("GalleryGroupEntry", mapOf("route" to "Input", "title" to "Input", "glyph" to "x", "order" to "0"), "Input", "groups.kt")
        val page = Entry("GalleryPage", mapOf("route" to "Button", "title" to "Button", "group" to "Input", "order" to "0"), "sample.buttonPage", "ButtonPage.kt", "winui-gallery/src/winuiMain/kotlin/ButtonPage.kt")
        val catalog = Json.parseToJsonElement("""{"Groups":[{"Items":[{"UniqueId":"Button"}]}]}""").jsonObject
        assertContains(generate(listOf(group, page, home), catalog), "\"Button\" -> sample.buttonPage()")
    }

    @Test
    fun source_paths_are_relative_to_repository_root_and_normalized_for_git() {
        val root = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val source = root.resolve("winui-gallery/src/winuiMain/kotlin/basicinput/ButtonPage.kt")
        assertEquals(
            "winui-gallery/src/winuiMain/kotlin/basicinput/ButtonPage.kt",
            repositoryRelativePath(source.toString(), root.toString()),
        )
        assertFailsWith<IllegalArgumentException> {
            repositoryRelativePath(root.resolveSibling("other-project/ButtonPage.kt").toString(), root.toString())
        }
        for (build in listOf("build", "build/winui2")) {
            assertEquals("winui-gallery/src/winuiMain/kotlin/basicinput/ButtonPage.kt",
                galleryRepositorySourcePath(root.resolve("winui-gallery/$build/generated/gallery-sources/basicinput/ButtonPage.kt").toString(), root.toString()))
        }
    }

    @Test fun excluded_groups_do_not_produce_empty_navigation_items() {
        val group = Entry("GalleryGroupEntry", mapOf("route" to "DesktopWindows", "title" to "Desktop windows", "glyph" to "x"), "DesktopWindows", "groups.kt")
        val output = generate(listOf(group, home), Json.parseToJsonElement("""{"Groups":[]}""").jsonObject, "windows.ui.xaml")
        assertFalse(output.contains("GalleryGroup(\"DesktopWindows\""))
        assertContains(output, "fun create(route: String): windows.ui.xaml.UIElement?")
    }

    @Test fun uwp_catalog_overrides_leave_shared_and_desktop_metadata_intact() {
        val group = Entry("GalleryGroupEntry", mapOf("route" to "Shell", "title" to "Shell", "glyph" to "x"), "Shell", "groups.kt")
        val page = Entry("GalleryPage", mapOf("route" to "Toast", "title" to "Toast", "group" to "Shell"), "ToastPage", "ToastPage.kt", "winui-gallery/src/uwpMain/kotlin/ToastPage.kt")
        val catalog = Json.parseToJsonElement("""{"Groups":[{"Items":[{"UniqueId":"Toast","Description":"Shared description","ApiNamespace":"Desktop.Notifications","Uwp":{"ApiNamespace":"Windows.UI.Notifications","Docs":[{"Title":"UWP notifications","Uri":"https://example.test/uwp"}]}}]}]}""").jsonObject
        val entries = listOf(group, page, home)
        val uwp = generate(entries, catalog, "windows.ui.xaml")
        assertContains(uwp, "Shared description")
        assertContains(uwp, "Windows.UI.Notifications")
        assertContains(uwp, "UWP notifications")
        assertFalse(uwp.contains("Desktop.Notifications"))
        val desktop = generate(entries, catalog)
        assertContains(desktop, "Desktop.Notifications")
        assertFalse(desktop.contains("UWP notifications"))
    }
}
