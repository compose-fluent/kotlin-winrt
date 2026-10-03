package io.github.composefluent.winrt.gallery.layout

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import io.github.composefluent.winrt.runtime.WinRTObservableList
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*
import microsoft.ui.xaml.media.animation.*

@GalleryPage(route = "SplitView", title = "SplitView", group = "Layout", order = 5)
internal class SplitViewPage : Page() {
    val NavLinks: MutableList<NavLink> = WinRTObservableList(listOf(NavLink("People", Symbol.People), NavLink("Globe", Symbol.Globe), NavLink("Message", Symbol.Message), NavLink("Mail", Symbol.Mail)))
    private var ready = false
    override fun initializeComponent() { super.initializeComponent(); ready = true; loaded.add { _, _ -> UpdateNavLinkItemLayout() } }
    private fun NavLinksList_ItemClick(sender: Any?, args: ItemClickEventArgs) { (args.clickedItem as? NavLink)?.let { contentTextBlock.text = "${it.Label} Page" } }
    private fun PanePlacement_Toggled(sender: Any?, args: RoutedEventArgs) {
        if (!ready) return
        splitView.panePlacement = if (checkNotNull(sender).asWinRT<ToggleSwitch>().isOn) SplitViewPanePlacement.Right else SplitViewPanePlacement.Left
        UpdateNavLinkItemLayout()
    }
    private fun UpdateNavLinkItemLayout() { if (ready) VisualStateManager.goToState(this, if (splitView.panePlacement == SplitViewPanePlacement.Right) "RightIconLayout" else "LeftIconLayout", false) }
    private fun togglePaneButton_CheckedChanged(sender: Any?, args: RoutedEventArgs) { UpdateNavLinkItemLayout() }
    private fun displayModeCombobox_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) {
        if (!ready) return
        splitView.displayMode = when (args.addedItems.firstOrNull()?.asWinRT<ComboBoxItem>()?.content?.toString()) { "CompactInline" -> SplitViewDisplayMode.CompactInline; "Overlay" -> SplitViewDisplayMode.Overlay; "CompactOverlay" -> SplitViewDisplayMode.CompactOverlay; else -> SplitViewDisplayMode.Inline }
    }
    private fun paneBackgroundCombobox_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) { if (ready) args.addedItems.firstOrNull()?.asWinRT<ComboBoxItem>()?.content?.toString()?.let { VisualStateManager.goToState(this, it, false) } }
}
