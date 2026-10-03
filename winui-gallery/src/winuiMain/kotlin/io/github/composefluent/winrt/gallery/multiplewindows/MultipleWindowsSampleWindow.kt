package io.github.composefluent.winrt.gallery.multiplewindows

import microsoft.ui.xaml.Window
import microsoft.ui.xaml.media.MicaBackdrop
import windows.graphics.SizeInt32

internal class MultipleWindowsSampleWindow : Window() {
    override fun initializeComponent() {
        super.initializeComponent()
        extendsContentIntoTitleBar = true; systemBackdrop = MicaBackdrop()
        checkNotNull(appWindow).resizeClient(SizeInt32(500, 500))
    }
}
