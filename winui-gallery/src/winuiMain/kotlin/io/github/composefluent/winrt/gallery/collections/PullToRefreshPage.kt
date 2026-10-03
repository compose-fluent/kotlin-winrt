package io.github.composefluent.winrt.gallery.collections

import io.github.composefluent.winrt.gallery.GalleryPage
import io.github.composefluent.winrt.gallery.GalleryPageTasks
import io.github.composefluent.winrt.gallery.GalleryTheme
import kotlinx.coroutines.delay
import microsoft.ui.xaml.ElementTheme
import microsoft.ui.xaml.HorizontalAlignment
import microsoft.ui.xaml.Thickness
import microsoft.ui.xaml.controls.Grid
import microsoft.ui.xaml.controls.Image
import microsoft.ui.xaml.controls.ListView
import microsoft.ui.xaml.controls.Page
import microsoft.ui.xaml.controls.RefreshContainer
import microsoft.ui.xaml.controls.RefreshRequestedEventArgs
import microsoft.ui.xaml.controls.RefreshVisualizer
import microsoft.ui.xaml.hosting.ElementCompositionPreview
import microsoft.ui.xaml.media.imaging.BitmapImage
import windows.foundation.Uri
import windows.ui.viewmanagement.AccessibilitySettings

@GalleryPage(route = "PullToRefresh", title = "PullToRefresh", group = "Collections", order = 6)
internal class PullToRefreshPage : Page() {
    private val tasks = GalleryPageTasks(this)
    private var controlCount = 0
    private var friendCount = 0
    private lateinit var customList: ListView

    override fun initializeComponent() {
        super.initializeComponent()
        "AcrylicBrush ColorPicker NavigationView ParallaxView PersonPicture PullToRefreshPage RatingsControl RevealBrush TreeView"
            .split(' ').forEach { lv.items.add(it) }
        initializeCustomRefresh()
    }

    private fun initializeCustomRefresh() {
        customList = ListView().apply {
            width = 200.0; height = 200.0
            borderThickness = Thickness(1.0, 1.0, 1.0, 1.0)
            horizontalAlignment = HorizontalAlignment.Center
            borderBrush = GalleryTheme.brush("TextControlBorderBrush")
            "Mike Ben Barbra Claire Justin Shawn Drew Lili".split(' ').forEach { items.add(it) }
        }
        val sun = Image().apply { width = 35.0; height = 35.0 }
        fun updateSun() {
            val accessibility = AccessibilitySettings()
            val black = (sun.actualTheme == ElementTheme.Light && !accessibility.highContrast) ||
                (accessibility.highContrast && accessibility.highContrastScheme == "High Contrast Black")
            sun.source = BitmapImage(Uri("ms-appx:///Assets/SampleMedia/Sun" + if (black) "Black.png" else "White.png"))
        }
        sun.loaded.add { _, _ -> updateSun() }
        sun.actualThemeChanged.add { _, _ -> updateSun() }
        val visualizer = RefreshVisualizer().apply {
            content = sun
            refreshStateChanged.add { _, _ -> ElementCompositionPreview.getElementVisual(sun).stopAnimation("RotationAngle") }
        }
        val container = RefreshContainer().apply {
            content = customList
            this.visualizer = visualizer
            refreshRequested.add { sender, args -> rc2_RefreshRequested(sender, args) }
        }
        Grid.setRow(container, 1)
        Ex2Grid.children.add(container)
    }

    private fun rc_RefreshRequested(sender: RefreshContainer, args: RefreshRequestedEventArgs) {
        val deferral = args.getDeferral()
        tasks.launch {
            try {
                delay(500)
                lv.items.add(0, "NewControl ${controlCount++}")
            } finally {
                deferral.complete()
                deferral.close()
            }
        }
    }

    private fun rc2_RefreshRequested(sender: RefreshContainer, args: RefreshRequestedEventArgs) {
        val deferral = args.getDeferral()
        tasks.launch {
            try {
                delay(800)
                customList.items.add(0, "New Friend ${friendCount++}")
            } finally {
                deferral.complete()
                deferral.close()
            }
        }
    }
}
