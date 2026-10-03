package io.github.composefluent.winrt.gallery.media

import io.github.composefluent.winrt.gallery.GalleryPage
import microsoft.ui.xaml.RoutedEventArgs
import microsoft.ui.xaml.controls.MapElementsLayer
import microsoft.ui.xaml.controls.MapIcon
import microsoft.ui.xaml.controls.Page
import microsoft.ui.xaml.input.KeyRoutedEventArgs
import windows.devices.geolocation.BasicGeoposition
import windows.devices.geolocation.Geopoint
import windows.system.VirtualKey

@GalleryPage(route = "MapControl", title = "MapControl", group = "Media", order = 3)
internal class MapControlPage : Page() {
    override fun initializeComponent() {
        super.initializeComponent()
        loaded.add { _, _ ->
            map1.center = Geopoint(BasicGeoposition(0.0, 0.0, 0.0))
            map1.zoomLevel = 1.0
            if (map1.layers.isEmpty()) map1.layers.add(MapElementsLayer().apply {
                mapElements = mutableListOf(MapIcon().apply {
                    location = Geopoint(BasicGeoposition(-30.034647, -51.217659, 0.0))
                })
            })
        }
    }

    private fun Button_Click(sender: Any?, args: RoutedEventArgs) {
        map1.mapServiceToken = MapToken.password
    }

    private fun MapToken_KeyDown(sender: Any?, args: KeyRoutedEventArgs) {
        if (args.key == VirtualKey.Enter) map1.mapServiceToken = MapToken.password
    }
}
