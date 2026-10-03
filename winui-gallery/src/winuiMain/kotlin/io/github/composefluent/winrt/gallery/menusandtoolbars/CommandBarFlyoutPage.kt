package io.github.composefluent.winrt.gallery.menusandtoolbars

import io.github.composefluent.winrt.gallery.GalleryPage
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.RoutedEventArgs
import microsoft.ui.xaml.UIElement
import microsoft.ui.xaml.controls.AppBarButton
import microsoft.ui.xaml.controls.Page
import microsoft.ui.xaml.controls.primitives.FlyoutPlacementMode
import microsoft.ui.xaml.controls.primitives.FlyoutShowMode
import microsoft.ui.xaml.controls.primitives.FlyoutShowOptions
import microsoft.ui.xaml.input.ContextRequestedEventArgs

@GalleryPage(route = "CommandBarFlyout", title = "CommandBarFlyout", group = "MenusAndToolbars", order = 4)
internal class CommandBarFlyoutPage : Page() {
    override fun initializeComponent() {
        super.initializeComponent()
    }

    private fun OnElementClicked(sender: Any?, args: RoutedEventArgs) {
        SelectedOptionText.text = "You clicked: " + checkNotNull(sender).asWinRT<AppBarButton>().label
    }

    private fun showMenu(transient: Boolean) {
        CommandBarFlyout1.showAt(Image1, FlyoutShowOptions().apply {
            showMode = if (transient) FlyoutShowMode.Transient else FlyoutShowMode.Standard
            placement = FlyoutPlacementMode.RightEdgeAlignedTop
        })
    }

    private fun MyImageButton_Click(sender: Any?, args: RoutedEventArgs) {
        showMenu(true)
    }

    private fun MyImageButton_ContextRequested(sender: UIElement, args: ContextRequestedEventArgs) {
        showMenu(false)
        args.handled = true
    }
}
