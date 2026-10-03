// Ported from WinUI Gallery v2.9.3 (MIT).
package io.github.composefluent.winrt.gallery.navigation

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.gallery.models.*
import io.github.composefluent.winrt.gallery.samplepages.*
import io.github.composefluent.winrt.runtime.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.input.*
import microsoft.ui.xaml.media.*
import kotlin.reflect.KClass
import windows.system.VirtualKey

@GalleryPage(route = "TabView", title = "TabView", group = "Navigation", order = 4)
internal class TabViewPage : Page() {
    val myDatas: MutableList<MyData> = WinRTObservableList()
    private var ready = false
    private val initializedTabViews = mutableSetOf<TabView>()
    override fun initializeComponent() { super.initializeComponent(); repeat(3) { myDatas.add(CreateNewMyData(it)) }; ready = true }
    private fun page(index: Int): KClass<out Page> = when (index % 3) { 0 -> SamplePage1::class; 1 -> SamplePage2::class; else -> SamplePage3::class }
    private fun TabView_Loaded(sender: Any?, args: RoutedEventArgs) {
        val tabs = checkNotNull(sender).asWinRT<TabView>()
        if (initializedTabViews.add(tabs)) repeat(3) { tabs.tabItems.add(CreateNewTab(it)) }
    }
    private fun TabView_BringIntoViewRequested(sender: UIElement, args: BringIntoViewRequestedEventArgs) { args.handled = true }
    private fun TabView_AddButtonClick(sender: TabView, args: Any?) { sender.tabItems.add(CreateNewTab(sender.tabItems.size)) }
    private fun TabView_TabCloseRequested(sender: TabView, args: TabViewTabCloseRequestedEventArgs) { sender.tabItems.remove(args.tab) }
    private fun CreateNewTab(index: Int): TabViewItem = TabViewItem().apply {
        header = "Document $index"; iconSource = SymbolIconSource().apply { symbol = Symbol.Document }; contextFlyout = TabViewContextMenu
        content = Frame().apply { navigate(page(index)) }
    }
    private fun CreateNewMyData(index: Int): MyData = MyData("MyData Doc $index", SymbolIconSource().apply { symbol = Symbol.Placeholder }, Frame().apply { navigate(page(index)) })
    private fun TabViewItemsSourceSample_AddTabButtonClick(sender: TabView, args: Any?) { myDatas.add(CreateNewMyData(myDatas.size)) }
    private fun TabViewItemsSourceSample_TabCloseRequested(sender: TabView, args: TabViewTabCloseRequestedEventArgs) { (args.item as? MyData)?.let(myDatas::remove) }
    private fun NewTabKeyboardAccelerator_Invoked(sender: KeyboardAccelerator, args: KeyboardAcceleratorInvokedEventArgs) {
        args.element?.asWinRT<TabView>()?.let { it.tabItems.add(CreateNewTab(it.tabItems.size)) }; args.handled = true
    }
    private fun CloseSelectedTabKeyboardAccelerator_Invoked(sender: KeyboardAccelerator, args: KeyboardAcceleratorInvokedEventArgs) {
        args.element?.asWinRT<TabView>()?.let { tabs -> tabs.selectedItem?.asWinRT<TabViewItem>()?.takeIf { it.isClosable }?.let { tabs.tabItems.remove(it) } }; args.handled = true
    }
    private fun NavigateToNumberedTabKeyboardAccelerator_Invoked(sender: KeyboardAccelerator, args: KeyboardAcceleratorInvokedEventArgs) {
        val tabs = args.element?.asWinRT<TabView>() ?: return
        val index = if (sender.key == VirtualKey.Number9) tabs.tabItems.lastIndex else (sender.key.abiValue - VirtualKey.Number1.abiValue).toInt()
        if (index in tabs.tabItems.indices) tabs.selectedIndex = index
        args.handled = true
    }
    private fun TabWidthBehaviorComboBox_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) {
        if (ready) TabView3.tabWidthMode = when (args.addedItems.firstOrNull()?.asWinRT<ComboBoxItem>()?.content?.toString()) { "SizeToContent" -> TabViewWidthMode.SizeToContent; "Compact" -> TabViewWidthMode.Compact; else -> TabViewWidthMode.Equal }
    }
    private fun TabCloseButtonOverlayModeComboBox_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) {
        if (ready) TabView4.closeButtonOverlayMode = when (args.addedItems.firstOrNull()?.asWinRT<ComboBoxItem>()?.content?.toString()) { "Always" -> TabViewCloseButtonOverlayMode.Always; "OnHover" -> TabViewCloseButtonOverlayMode.OnPointerOver; else -> TabViewCloseButtonOverlayMode.Auto }
    }
    private fun TabViewWindowingButton_Click(sender: Any?, args: RoutedEventArgs) {
        val page = TabViewWindowingSamplePage()
        GalleryWindows.create("TabView",page).apply {
            extendsContentIntoTitleBar = true; systemBackdrop = MicaBackdrop()
            appWindow?.setIcon("Assets/Tiles/GalleryIcon.ico"); page.LoadDemoData(); activate()
        }
    }
    private fun TabViewContextMenu_Opening(sender: Any?, args: Any?) {
        val flyout = checkNotNull(sender).asWinRT<MenuFlyout>(); flyout.items.clear()
        val tab = flyout.target?.asWinRT<TabViewItem>() ?: return
        var current: DependencyObject? = VisualTreeHelper.getParent(tab)
        var list: ListView? = null; var tabs: TabView? = null
        while (current != null) {
            if (current is ListView) list = current
            if (current is TabView) { tabs = current; break }
            current = VisualTreeHelper.getParent(current)
        }
        val target = tabs ?: return; val index = list?.indexFromContainer(tab) ?: target.tabItems.indexOf(tab)
        val items = (target.tabItemsSource as? MutableList<Any?>) ?: target.tabItems
        fun addMove(text: String, destination: Int) {
            flyout.items.add(MenuFlyoutItem().apply { this.text = text; click.add { _, _ ->
                val item = items.removeAt(index); items.add(destination,item)
            } })
        }
        if (index > 0) addMove("Move tab left",index-1)
        if (index >= 0 && index < items.lastIndex) addMove("Move tab right",index+1)
        if (flyout.items.isEmpty()) flyout.hide()
    }

}
