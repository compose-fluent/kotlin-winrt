package io.github.composefluent.winrt.gallery.processor

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class GalleryVariantSourcesTest {
    @Test fun selectsNestedBranchesWithoutMovingSourceLines() {
        val source = """
            shared
            // #if WINUI2
            uwp
            // #if WINUI3
            unreachable
            // #else
            extension
            // #endif
            // #else
            desktop
            // #endif
            end
        """.trimIndent()
        val selected = selectGalleryVariant(source, true)
        assertEquals(source.lines().size, selected.lines().size)
        assertEquals(listOf("shared", "uwp", "extension", "end"), selected.lines().filter(String::isNotBlank))
        assertEquals(listOf("shared", "desktop", "end"), selectGalleryVariant(source, false).lines().filter(String::isNotBlank))
        assertFailsWith<IllegalStateException> { selectGalleryVariant("// #else", true) }
        assertFailsWith<IllegalStateException> { selectGalleryVariant("// #if WINUI2", true) }
    }

    private val names = GalleryUWPTypeNames(setOf(
        "Windows.UI.Xaml.Controls.Button", "Windows.UI.Xaml.Controls.NavigationView",
        "Microsoft.UI.Xaml.Controls.NavigationView", "Microsoft.UI.Xaml.Controls.NumberBox",
        "Microsoft.UI.Xaml.Controls.AnimatedIcon", "Windows.UI.Xaml.Data.IValueConverter",
        "Windows.UI.Xaml.FrameworkElement",
    ))

    @Test fun preservesExtensionTypesAndUsesTheUWPBaseType() {
        val selected = names.kotlin("""
            import microsoft.ui.xaml.controls.*
            import microsoft.ui.xaml.FrameworkElement
            import microsoft.ui.xaml.`data`.IValueConverter
            val page: FrameworkElement = NavigationView()
            val button = Button()
            val number = NumberBox()
        """.trimIndent())
        assertContains(selected, "import windows.ui.xaml.controls.*")
        assertContains(selected, "import microsoft.ui.xaml.controls.NavigationView")
        assertContains(selected, "import microsoft.ui.xaml.controls.NumberBox")
        assertContains(selected, "import windows.ui.xaml.FrameworkElement")
        assertContains(selected, "import windows.ui.xaml.`data`.IValueConverter")
        assertEquals("Microsoft.UI.Xaml.Controls.NavigationView", names.runtimeName("Microsoft.UI.Xaml.Controls.NavigationView"))
        assertEquals("Microsoft.UI.Xaml.Controls.Unavailable", names.runtimeName("Microsoft.UI.Xaml.Controls.Unavailable"))
    }

    @Test fun qualifiesXamlExtensionsIncludingPropertyElementsAndStyleTargets() {
        val selected = names.xaml("""
            <Button xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation">
                <NavigationView><NavigationView.Content><NumberBox /></NavigationView.Content></NavigationView>
                <Style TargetType="NavigationView" />
            </Button>
        """.trimIndent())
        assertContains(selected, "using:Microsoft.UI.Xaml.Controls")
        assertContains(selected, "galleryPlatform0:NavigationView.Content")
        assertContains(selected, "TargetType=\"galleryPlatform0:NavigationView\"")
        assertFalse(selected.contains("galleryPlatform0:Button"))
    }

    @Test fun mapsAttachedPropertiesAndTheirStoryboardPaths() {
        val selected = names.xaml("""
            <Button xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" AnimatedIcon.State="Normal">
                <Setter Target="Root.(AnimatedIcon.State)" Value="PointerOver" />
            </Button>
        """.trimIndent())
        assertContains(selected, "galleryPlatform0:AnimatedIcon.State=\"Normal\"")
        assertContains(selected, "Root.(galleryPlatform0:AnimatedIcon.State)")
    }
}
