// Ported from WinUI Gallery Samples/TabView (MIT).
package io.github.composefluent.winrt.gallery

import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.input.KeyboardAccelerator
import windows.system.VirtualKey
import windows.system.VirtualKeyModifiers

internal object GalleryWindows {
    private val windows = mutableListOf<Window>()
    private val nativeWindows = mutableListOf<microsoft.ui.windowing.AppWindow>()
    fun create(title: String, content: UIElement): Window = Window().also { window ->
        track(window)
        window.title = title; window.content = content
    }
    fun open(title: String, content: UIElement): Window = create(title, content).also { it.activate() }
    fun track(window: Window) {
        windows.add(window)
        window.closed.add { _, _ -> windows.remove(window) }
    }
    fun track(window: microsoft.ui.windowing.AppWindow) {
        nativeWindows.add(window)
        window.destroying.add { _, _ -> nativeWindows.remove(window) }
    }
    // WinUI Gallery WindowHelper.GetWindowForElement locates the containing XamlRoot.
    fun forElement(element: UIElement): Window? = windows.firstOrNull { window ->
        val content = window.content as? FrameworkElement
        content?.xamlRoot != null && content.xamlRoot == (element as? FrameworkElement)?.xamlRoot
    }
    fun closeAll() {
        nativeWindows.toList().forEach { it.destroy() }
        windows.toList().forEach { it.close() }
    }
}
