package io.github.composefluent.winrt.gallery.media

import io.github.composefluent.winrt.gallery.GalleryPage
import io.github.composefluent.winrt.gallery.GalleryPageTasks
import io.github.composefluent.winrt.runtime.await
import microsoft.ui.xaml.RoutedEventArgs
import microsoft.ui.xaml.controls.Page
import microsoft.windows.storage.pickers.FileOpenPicker
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

    private fun OpenFileButton_Click(sender: Any?, args: RoutedEventArgs) {
        tasks.launch {
            val picker = FileOpenPicker(checkNotNull(checkNotNull(Player1.xamlRoot).contentIslandEnvironment).appWindowId)
            val file = picker.pickSingleFileAsync().await() ?: return@launch
            Player1.mediaPlayer?.pause()
            Player1.source = MediaSource.createFromStorageFile(StorageFile.getFileFromPathAsync(file.path).await())
        }
    }
}
