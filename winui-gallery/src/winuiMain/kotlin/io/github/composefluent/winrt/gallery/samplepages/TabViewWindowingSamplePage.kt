// Ported from WinUI Gallery TabViewWindowingSamplePage (MIT).
package io.github.composefluent.winrt.gallery.samplepages

import io.github.composefluent.winrt.gallery.GalleryWindows
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.media.VisualTreeHelper
import microsoft.ui.windowing.OverlappedPresenter

internal class TabViewWindowingSamplePage : Page() {
    private var tabTearOutWindow: Window? = null
    override fun initializeComponent() {
        super.initializeComponent()
        loaded.add { _, _ -> GalleryWindows.forElement(this)?.let { window ->
            window.extendsContentIntoTitleBar = true; window.setTitleBar(CustomDragRegion); CustomDragRegion.minWidth = 188.0
            window.appWindow?.presenter?.asWinRT<OverlappedPresenter>()?.apply { preferredMinimumWidth = 500; preferredMinimumHeight = 300 }
        } }
    }
    fun LoadDemoData() { repeat(3) { Tabs.tabItems.add(CreateNewTVI("Item $it","Page $it")) }; Tabs.selectedIndex = 0 }
    fun AddTabToTabs(tab: TabViewItem) { Tabs.tabItems.add(tab) }
    private fun Tabs_TabTearOutWindowRequested(sender: TabView, args: TabViewTabTearOutWindowRequestedEventArgs) {
        tabTearOutWindow = GalleryWindows.create("TabView",TabViewWindowingSamplePage()).apply { extendsContentIntoTitleBar = true; appWindow?.setIcon("Assets/Tiles/GalleryIcon.ico") }
        args.newWindowId = checkNotNull(checkNotNull(tabTearOutWindow).appWindow).id
    }
    private fun Tabs_TabTearOutRequested(sender: TabView, args: TabViewTabTearOutRequestedEventArgs) {
        val newPage = tabTearOutWindow?.content as? TabViewWindowingSamplePage ?: return
        args.tabs.forEach { value -> val tab = value.asWinRT<TabViewItem>(); GetParentTabView(tab)?.tabItems?.remove(tab); newPage.AddTabToTabs(tab) }
        tabTearOutWindow = null; CloseWindowIfEmpty(sender)
    }
    private fun Tabs_ExternalTornOutTabsDropping(sender: TabView, args: TabViewExternalTornOutTabsDroppingEventArgs) { args.allowDrop = true }
    private fun Tabs_ExternalTornOutTabsDropped(sender: TabView, args: TabViewExternalTornOutTabsDroppedEventArgs) {
        args.tabs.forEachIndexed { position,value ->
            val tab = value.asWinRT<TabViewItem>(); val source = GetParentTabView(tab)
            source?.tabItems?.remove(tab); sender.tabItems.add(args.dropIndex+position,tab)
            source?.let(::CloseWindowIfEmpty)
        }
    }
    private fun GetParentTabView(tab: TabViewItem): TabView? {
        var current: DependencyObject? = tab
        while (current != null) { if (current is TabView) return current; current = VisualTreeHelper.getParent(current) }
        return null
    }
    private fun CloseWindowIfEmpty(tabs: TabView) { if (tabs.tabItems.isEmpty()) GalleryWindows.forElement(tabs)?.close() }
    private fun CreateNewTVI(header: String, context: String): TabViewItem = TabViewItem().apply {
        iconSource = SymbolIconSource().apply { symbol = Symbol.Placeholder }; this.header = header; content = TabContentSampleControl().apply { dataContext = context }
    }
    private fun Tabs_AddTabButtonClick(sender: TabView, args: Any?) { sender.tabItems.add(CreateNewTVI("New Item","New Item")) }
    private fun Tabs_TabCloseRequested(sender: TabView, args: TabViewTabCloseRequestedEventArgs) { sender.tabItems.remove(args.tab); CloseWindowIfEmpty(sender) }
}
