package io.github.composefluent.winrt.gallery.media

import io.github.composefluent.winrt.gallery.GalleryPage
import io.github.composefluent.winrt.gallery.GalleryPageTasks
import io.github.composefluent.winrt.runtime.await
import microsoft.ui.xaml.RoutedEventArgs
import microsoft.ui.xaml.controls.Page
// #if WINUI3
import microsoft.windows.storage.pickers.FileOpenPicker
// #else
import windows.storage.pickers.FileOpenPicker
// #endif
import windows.media.core.MediaSource
import windows.storage.StorageFile

@GalleryPage(route = "MediaPlayerElement", title = "MediaPlayerElement", group = "Media", order = 4)
internal class MediaPlayerElementPage : Page() {
    private val tasks = GalleryPageTasks(this)

    override fun initializeComponent() {
        super.initializeComponent()
        loaded.add { _, _ -> Player2.mediaPlayer?.play() }
        unloaded.add { _, _ ->
            Player1.mediaPlayer?.pause()
            Player2.mediaPlayer?.pause()
            Player1.source = null
            Player2.source = null
        }
    }

    private fun OpenFileButton_Click(sender: Any?, args: RoutedEventArgs?) {
        tasks.launch {
// #if WINUI3
            val picker = FileOpenPicker(checkNotNull(checkNotNull(Player1.xamlRoot).contentIslandEnvironment).appWindowId)
// #else
            val picker = FileOpenPicker().apply { fileTypeFilter!!.add("*") }
// #endif
            val file = picker.pickSingleFileAsync()!!.await() ?: return@launch
            Player1.mediaPlayer?.pause()
// #if WINUI3
            Player1.source = MediaSource.createFromStorageFile(StorageFile.getFileFromPathAsync(file.path)!!.await())
// #else
            Player1.source = MediaSource.createFromStorageFile(file)
// #endif
        }
    }
}
