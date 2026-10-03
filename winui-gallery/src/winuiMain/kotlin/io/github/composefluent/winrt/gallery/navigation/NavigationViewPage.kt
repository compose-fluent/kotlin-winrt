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

@GalleryPage(route = "NavigationView", title = "NavigationView", group = "Navigation", order = 1)
internal class NavigationViewPage : Page() {
    private var ready = false
    private var CameFromGridChange = false
    val Categories: MutableList<CategoryBase> = WinRTObservableList(listOf(
        Category("Category 1", "This is category 1", Symbol.Home),
        Category("Category 2", "This is category 2", Symbol.Keyboard),
        Category("Category 3", "This is category 3", Symbol.Library),
        Category("Category 4", "This is category 4", Symbol.Mail)))
    override fun initializeComponent() {
        super.initializeComponent(); ready = true
        listOf(nvSample2, nvSample5, nvSample6, nvSample7, nvSample8, nvSample9).forEach { it.selectedItem = it.menuItems.firstOrNull() }
        nvSample4.selectedItem = Categories.first()
        setASBSubstitutionString()
        nvSample2.updateLayout()
    }
    fun ChoosePanePosition(toggleOn: Boolean): NavigationViewPaneDisplayMode = if (toggleOn) NavigationViewPaneDisplayMode.Left else NavigationViewPaneDisplayMode.Top
    private fun navigate(sender: NavigationView, args: NavigationViewSelectionChangedEventArgs, frame: Frame, header: Boolean = false, transition: Boolean = false) {
        if (!ready) return
        if (args.isSettingsSelected) { frame.navigate(SampleSettingsPage::class); return }
        val tag = args.selectedItem?.asWinRT<NavigationViewItem>()?.tag?.toString() ?: return
        val page = when (tag) { "SamplePage1" -> SamplePage1::class; "SamplePage2" -> SamplePage2::class; "SamplePage3" -> SamplePage3::class; else -> return }
        if (header) sender.header = "Sample Page ${tag.last()}"
        if (transition) frame.navigate(page, null, args.recommendedNavigationTransitionInfo) else frame.navigate(page)
    }
    private fun NavigationView_SelectionChanged(sender: NavigationView, args: NavigationViewSelectionChangedEventArgs) { if (ready) navigate(sender,args,contentFrame,true) }
    private fun NavigationView_SelectionChanged2(sender: NavigationView, args: NavigationViewSelectionChangedEventArgs) {
        if (ready && !CameFromGridChange) navigate(sender,args,contentFrame2)
        CameFromGridChange = false
    }
    private fun NavigationView_SelectionChanged4(sender: NavigationView, args: NavigationViewSelectionChangedEventArgs) {
        if (!ready) return
        if (args.isSettingsSelected) contentFrame4.navigate(SampleSettingsPage::class) else {
            val category = args.selectedItem as? Category ?: return
            sender.header = "Sample Page ${category.Name.last()}"; contentFrame4.navigate(SamplePage1::class)
        }
    }
    private fun NavigationView_SelectionChanged5(sender: NavigationView, args: NavigationViewSelectionChangedEventArgs) { if (ready) navigate(sender,args,contentFrame5,true) }
    private fun NavigationView_SelectionChanged6(sender: NavigationView, args: NavigationViewSelectionChangedEventArgs) { if (ready) navigate(sender,args,contentFrame6) }
    private fun NavigationView_SelectionChanged7(sender: NavigationView, args: NavigationViewSelectionChangedEventArgs) { if (ready) navigate(sender,args,contentFrame7,transition=true) }
    private fun NavigationView_SelectionChanged8(sender: NavigationView, args: NavigationViewSelectionChangedEventArgs) { if (ready) navigate(sender,args,contentFrame8,true) }
    private fun NavigationView_SelectionChanged9(sender: NavigationView, args: NavigationViewSelectionChangedEventArgs) { if (ready) navigate(sender,args,contentFrame9,transition=true) }
    private fun checked(sender: Any?): Boolean = checkNotNull(sender).asWinRT<CheckBox>().isChecked == true
    private fun headerCheck_Click(sender: Any?, args: RoutedEventArgs) { if (ready) nvSample.alwaysShowHeader = checked(sender) }
    private fun settingsCheck_Click(sender: Any?, args: RoutedEventArgs) { if (ready) nvSample.isSettingsVisible = checked(sender) }
    private fun visibleCheck_Click(sender: Any?, args: RoutedEventArgs) { if (ready) nvSample.isBackButtonVisible = if (checked(sender)) NavigationViewBackButtonVisible.Visible else NavigationViewBackButtonVisible.Collapsed }
    private fun enableCheck_Click(sender: Any?, args: RoutedEventArgs) { if (ready) nvSample.isBackEnabled = checked(sender) }
    private fun autoSuggestCheck_Click(sender: Any?, args: RoutedEventArgs) {
        if (!ready) return
        if (checked(sender)) {
            nvSample.autoSuggestBox = AutoSuggestBox().apply { queryIcon = SymbolIcon(Symbol.Find); microsoft.ui.xaml.automation.AutomationProperties.setName(this,"Search") }
            setASBSubstitutionString()
        } else { nvSample.autoSuggestBox = null; navViewASB.Value = null }
    }
    private fun setASBSubstitutionString() { navViewASB.Value = "\n    <NavigationView.AutoSuggestBox>\n        <AutoSuggestBox QueryIcon=\"Find\" AutomationProperties.Name=\"Search\" />\n    </NavigationView.AutoSuggestBox>\n" }
    private fun panemc_Check_Click(sender: Any?, args: RoutedEventArgs) { if (ready) PaneHyperlink.visibility = if (checked(sender)) Visibility.Visible else Visibility.Collapsed }
    private fun paneFooterCheck_Click(sender: Any?, args: RoutedEventArgs) { if (ready) FooterStackPanel.visibility = if (checked(sender)) Visibility.Visible else Visibility.Collapsed }
    private fun setPanePosition(sender: Any?, mode: NavigationViewPaneDisplayMode) {
        if (!ready) return
        val radio = checkNotNull(sender).asWinRT<RadioButton>()
        if (radio.isChecked != true) return
        val view = when { radio.name.startsWith("nvSample8") -> nvSample8; radio.name.startsWith("nvSample9") -> nvSample9; else -> nvSample }
        view.paneDisplayMode = mode; view.isPaneOpen = mode == NavigationViewPaneDisplayMode.Left
        if (view == nvSample) FooterStackPanel.orientation = if (mode == NavigationViewPaneDisplayMode.Top) Orientation.Horizontal else Orientation.Vertical
    }
    private fun panePositionLeft_Checked(sender: Any?, args: RoutedEventArgs) = setPanePosition(sender,NavigationViewPaneDisplayMode.Left)
    private fun panePositionTop_Checked(sender: Any?, args: RoutedEventArgs) = setPanePosition(sender,NavigationViewPaneDisplayMode.Top)
    private fun panePositionLeftCompact_Checked(sender: Any?, args: RoutedEventArgs) = setPanePosition(sender,NavigationViewPaneDisplayMode.LeftCompact)
    private fun sffCheck_Click(sender: Any?, args: RoutedEventArgs) { if (ready) nvSample.selectionFollowsFocus = if (checked(sender)) NavigationViewSelectionFollowsFocus.Enabled else NavigationViewSelectionFollowsFocus.Disabled }
    private fun suppressselectionCheck_Checked_Click(sender: Any?, args: RoutedEventArgs) { if (ready) SamplePage2Item.selectsOnInvoked = !checked(sender) }

}
