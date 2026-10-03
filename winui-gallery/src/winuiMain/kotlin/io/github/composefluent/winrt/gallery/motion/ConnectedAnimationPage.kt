package io.github.composefluent.winrt.gallery.motion

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.gallery.collections.CustomDataObject
import io.github.composefluent.winrt.runtime.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.input.*
import microsoft.ui.xaml.media.*
import microsoft.ui.xaml.media.animation.*
import microsoft.ui.xaml.navigation.*
import io.github.composefluent.winrt.gallery.samplepages.*

@GalleryPage(route = "ConnectedAnimation", title = "Connected Animation", group = "Motion", order = 1)
internal class ConnectedAnimationPage : Page() {
    override fun initializeComponent() {
        super.initializeComponent()
        CollectionContentFrame.navigate(CollectionPage::class); CardFrame.navigate(CardPage::class)
        ContentFrame.navigate(SamplePage1::class); ItemsRepeaterFrame.navigate(ItemsRepeaterCollectionPage::class)
    }
    private fun GetConfiguration(): ConnectedAnimationConfiguration? = when (ConfigurationPanel.selectedItem?.asWinRT<RadioButton>()?.content?.toString()) {
        "Gravity" -> GravityConnectedAnimationConfiguration(); "Direct" -> DirectConnectedAnimationConfiguration(); "Basic" -> BasicConnectedAnimationConfiguration(); else -> null
    }
    private fun NavigateButton_Click(sender: Any?, args: RoutedEventArgs) {
        when (val page = ContentFrame.content) {
            is SamplePage1 -> { page.PrepareConnectedAnimation(GetConfiguration()); ContentFrame.navigate(SamplePage2::class, null, SuppressNavigationTransitionInfo()) }
            is SamplePage2 -> { page.PrepareConnectedAnimation(GetConfiguration()); ContentFrame.navigate(SamplePage1::class, null, SuppressNavigationTransitionInfo()) }
        }
    }
}
