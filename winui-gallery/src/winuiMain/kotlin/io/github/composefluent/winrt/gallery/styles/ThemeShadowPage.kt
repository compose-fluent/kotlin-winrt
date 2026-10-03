package io.github.composefluent.winrt.gallery.styles

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*

@GalleryPage(route = "ThemeShadow", title = "ThemeShadow", group = "Styles", order = 9)
internal class ThemeShadowPage : Page() {
    private var ready = false
    override fun initializeComponent() { super.initializeComponent(); ready = true }
    private fun TranslationSliderInApp_ValueChanged(sender: Any?, args: RangeBaseValueChangedEventArgs) {
        if (ready) ShadowRect.translation = windows.foundation.numerics.Vector3(0f, 0f, args.newValue.toFloat())
    }
    private fun ShadowRect_Loaded(sender: Any?, args: RoutedEventArgs) { exampleShadow.receivers.add(ShadowCastGrid) }
}
