package io.github.composefluent.winrt.gallery

import microsoft.ui.xaml.DependencyObject

// #if WINUI3
internal val DependencyObject.galleryDispatcher get() = checkNotNull(dispatcherQueue)
// #else
internal val DependencyObject.galleryDispatcher get() = GalleryDispatcher(checkNotNull(dispatcher))
internal class GalleryDispatcher(private val dispatcher: windows.ui.core.CoreDispatcher) {
    fun tryEnqueue(action: () -> Unit): Boolean = dispatcher.tryRunAsync(windows.ui.core.CoreDispatcherPriority.Normal) { action() } != null
}
// #endif
