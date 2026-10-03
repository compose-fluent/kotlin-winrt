package io.github.composefluent.winrt.gallery.statusandinfo

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*

@GalleryPage(route = "InfoBadge", title = "InfoBadge", group = "StatusAndInfo", order = 0)
internal class InfoBadgePage : Page() {
    private var ready = false
    var InfoBadgeOpacity: Double
        get() = getValue(infoBadgeOpacityProperty) as Double
        set(value) { setValue(infoBadgeOpacityProperty, value) }
    private companion object {
        val infoBadgeOpacityProperty: DependencyProperty = DependencyProperty.register("InfoBadgeOpacity", Double::class, InfoBadgePage::class, PropertyMetadata(0.0))
    }
    override fun initializeComponent() { super.initializeComponent(); ready = true }
    private fun ToggleInfoBadgeOpacity_Toggled(sender: Any?, args: RoutedEventArgs) { InfoBadgeOpacity = if (InfoBadgeOpacity == 0.0) 1.0 else 0.0 }
    private fun NavigationViewDisplayMode_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) {
        if (!ready) return
        val mode = checkNotNull(sender).asWinRT<ComboBox>().selectedItem?.toString()
        nvSample1.paneDisplayMode = when (mode) { "Top" -> NavigationViewPaneDisplayMode.Top; "LeftCompact" -> NavigationViewPaneDisplayMode.LeftCompact; else -> NavigationViewPaneDisplayMode.Left }
        nvSample1.isPaneOpen = mode != "LeftCompact"
    }
    private fun InfoBadgeStyleComboBox_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) {
        if (!ready) return
        val name = InfoBadgeStyleComboBox.selectedItem?.toString() ?: return
        val resources = checkNotNull(Application.current).resources
        infoBadge2.style = checkNotNull(resources["${name}IconInfoBadgeStyle"]).asWinRT<Style>()
        infoBadge3.style = checkNotNull(resources["${name}ValueInfoBadgeStyle"]).asWinRT<Style>()
        infoBadge4.style = checkNotNull(resources["${name}DotInfoBadgeStyle"]).asWinRT<Style>()
    }
    private fun ValueNumberBox_ValueChanged(sender: NumberBox, args: NumberBoxValueChangedEventArgs) {
        if (ready && args.newValue.isFinite() && args.newValue >= -1.0) DynamicInfoBadge.value = args.newValue.toInt()
    }
}
