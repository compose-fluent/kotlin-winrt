// Copyright (c) Microsoft Corporation. Licensed under the MIT License.
package io.github.composefluent.winrt.gallery.controls
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
internal class HorizontalScrollContainer : UserControl() {
    var Source: Any?
        get() = getValue(SourceProperty) as Any?
        set(value) { setValue(SourceProperty, value) }

    private fun Scroller_ViewChanging(sender: Any?, args: ScrollViewerViewChangingEventArgs) {
        val offset = checkNotNull(args.finalView).horizontalOffset
        ScrollBackBtn.visibility = if (offset < 1) Visibility.Collapsed else Visibility.Visible
        ScrollForwardBtn.visibility = if (offset > scroller.scrollableWidth - 1) Visibility.Collapsed else Visibility.Visible
    }
    private fun ScrollBackBtn_Click(sender: Any?, args: RoutedEventArgs) {
        scroller.changeView(scroller.horizontalOffset - scroller.viewportWidth, null, null)
        ScrollForwardBtn.focus(FocusState.Programmatic)
    }
    private fun ScrollForwardBtn_Click(sender: Any?, args: RoutedEventArgs) {
        scroller.changeView(scroller.horizontalOffset + scroller.viewportWidth, null, null)
        ScrollBackBtn.focus(FocusState.Programmatic)
    }
    private fun Scroller_SizeChanged(sender: Any?, args: SizeChangedEventArgs) {
        ScrollForwardBtn.visibility = if (scroller.scrollableWidth > 0) Visibility.Visible else Visibility.Collapsed
    }
    companion object {
        val SourceProperty: DependencyProperty = DependencyProperty.register("Source", Any::class, HorizontalScrollContainer::class, PropertyMetadata(null))
    }
}
