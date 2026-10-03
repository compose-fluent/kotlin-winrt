// Ported from WinUI Gallery ModalWindow (MIT).
package io.github.composefluent.winrt.gallery.samplepages

import io.github.composefluent.winrt.gallery.GalleryWindows
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.UserControl
import microsoft.ui.xaml.hosting.DesktopWindowXamlSource
import microsoft.ui.xaml.media.MicaBackdrop
import microsoft.ui.windowing.*
import windows.graphics.*

internal class ModalWindow : UserControl() {
    private var hostWindow: AppWindow? = null
    // AppWindow's owner overload provides the same modal relationship without a
    // platform-specific SetWindowLongPtr call in shared Gallery code.
    fun Show(owner: FrameworkElement) {
        val root = checkNotNull(owner.xamlRoot)
        val ownerId = checkNotNull(root.contentIslandEnvironment).appWindowId
        val host = AppWindow.create(OverlappedPresenter.createForDialog().apply { isModal = true },ownerId)
        hostWindow = host; GalleryWindows.track(host)
        val source = DesktopWindowXamlSource(); source.initialize(host.id); source.content = this; source.systemBackdrop = MicaBackdrop()
        requestedTheme = owner.actualTheme
        val scale = root.rasterizationScale
        host.resize(SizeInt32((400*scale).toInt(),(300*scale).toInt())); host.setIcon("Assets/Tiles/GalleryIcon.ico"); host.titleBar?.preferredTheme = TitleBarTheme.UseDefaultAppMode
        fun resize() { val size = host.clientSize; checkNotNull(source.siteBridge).moveAndResize(RectInt32(0,0,size.width,size.height)) }
        host.changed.add { _, args -> if (args.didSizeChange) resize() }
        host.destroying.add { _, _ -> source.close(); hostWindow = null; GalleryWindows.forElement(owner)?.activate() }
        resize(); checkNotNull(source.siteBridge).show(); host.show()
    }
    private fun OKButton_Click(sender: Any?, args: RoutedEventArgs) { hostWindow?.destroy() }
    private fun CancelButton_Click(sender: Any?, args: RoutedEventArgs) { hostWindow?.destroy() }
}
