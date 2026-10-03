package io.github.composefluent.winrt.gallery.system

import io.github.composefluent.winrt.gallery.GalleryPage
import io.github.composefluent.winrt.gallery.GalleryPageTasks
import io.github.composefluent.winrt.gallery.announce
import io.github.composefluent.winrt.runtime.WinRTOut
import io.github.composefluent.winrt.runtime.asWinRT
import io.github.composefluent.winrt.runtime.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import microsoft.ui.text.TextGetOptions
import microsoft.ui.text.TextSetOptions
import microsoft.ui.xaml.RoutedEventArgs
import microsoft.ui.xaml.UIElement
import microsoft.ui.xaml.Visibility
import microsoft.ui.xaml.VisualStateManager
import microsoft.ui.xaml.controls.Page
import microsoft.ui.xaml.media.imaging.BitmapImage
import microsoft.windows.storage.pickers.FileOpenPicker
import windows.applicationmodel.datatransfer.Clipboard
import windows.applicationmodel.datatransfer.ClipboardContentOptions
import windows.applicationmodel.datatransfer.DataPackage
import windows.applicationmodel.datatransfer.DataPackageOperation
import windows.applicationmodel.datatransfer.StandardDataFormats
import windows.foundation.Uri
import windows.storage.IStorageItem
import windows.storage.StorageFile
import windows.storage.streams.RandomAccessStreamReference

@GalleryPage(route = "Clipboard", title = "Clipboard", group = "System", order = 0)
internal class ClipboardPage : Page() {
    private val tasks = GalleryPageTasks(this)
    private var confirmationJob: Job? = null
    private var stopMonitoring: (() -> Unit)? = null

    override fun initializeComponent() {
        super.initializeComponent()
        checkNotNull(richEditBox.document).setText(TextSetOptions.None, "This text will be copied to the clipboard.")
        UpdateHistoryRoamingStatus()
        loaded.add { _, _ -> updateMonitoring() }
        unloaded.add { _, _ ->
            stopMonitoring?.invoke()
            stopMonitoring = null
        }
    }

    private fun CopyText_Click(sender: Any?, args: RoutedEventArgs) {
        val text = WinRTOut<String>()
        checkNotNull(richEditBox.document).getText(TextGetOptions.None, text)
        Clipboard.setContent(DataPackage().apply { setText(text.value) })
        announce(checkNotNull(sender).asWinRT<UIElement>(), "Text copied to clipboard", "TextCopiedSuccessNotificationId")
        VisualStateManager.goToState(this, "ConfirmationClipboardVisible", false)
        confirmationJob?.cancel()
        confirmationJob = tasks.launch {
            delay(2000)
            VisualStateManager.goToState(this@ClipboardPage, "ConfirmationClipboardCollapsed", false)
        }
    }

    private fun PasteText_Click(sender: Any?, args: RoutedEventArgs) {
        tasks.launch {
            val packageView = Clipboard.getContent()
            if (packageView.contains(StandardDataFormats.text)) {
                PasteClipboard2.text = packageView.getTextAsync().await()
                announce(checkNotNull(sender).asWinRT<UIElement>(), "Text pasted from clipboard", "TextPastedSuccessNotificationId")
            }
        }
    }

    private fun CopyImage_Click(sender: Any?, args: RoutedEventArgs) {
        val packageContent = DataPackage().apply {
            setBitmap(RandomAccessStreamReference.createFromUri(Uri("ms-appx:///Assets/SampleMedia/rainier.jpg")))
        }
        val copied = Clipboard.setContentWithOptions(packageContent, ClipboardContentOptions())
        ImageStatusText.text = if (copied) "Image copied to clipboard." else "Error copying image to clipboard."
        ImageStatusText.visibility = Visibility.Visible
        if (copied) announce(checkNotNull(sender).asWinRT<UIElement>(), "Image copied to clipboard", "ImageCopiedSuccessNotificationId")
    }

    private fun PasteImage_Click(sender: Any?, args: RoutedEventArgs) {
        tasks.launch {
            val packageView = Clipboard.getContent()
            if (!packageView.contains(StandardDataFormats.bitmap)) {
                ImageStatusText.text = "Bitmap format is not available in the clipboard."
                ImageStatusText.visibility = Visibility.Visible
                PastedImage.visibility = Visibility.Collapsed
                return@launch
            }
            try {
                val stream = packageView.getBitmapAsync().await().openReadAsync().await()
                try {
                    val bitmap = BitmapImage()
                    bitmap.setSourceAsync(stream).await()
                    PastedImage.source = bitmap
                } finally {
                    stream.close()
                }
                PastedImage.visibility = Visibility.Visible
                ImageStatusText.text = "Image pasted from clipboard."
                announce(checkNotNull(sender).asWinRT<UIElement>(), "Image pasted from clipboard", "ImagePastedSuccessNotificationId")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                ImageStatusText.text = "Error pasting image: " + error.message
            }
            ImageStatusText.visibility = Visibility.Visible
        }
    }

    private fun CopyFiles_Click(sender: Any?, args: RoutedEventArgs) {
        tasks.launch {
            val picker = FileOpenPicker(checkNotNull(checkNotNull(xamlRoot).contentIslandEnvironment).appWindowId)
                .apply { fileTypeFilter.add("*") }
            val files = picker.pickMultipleFilesAsync().await()
            if (files.isEmpty()) return@launch
            val items = mutableListOf<IStorageItem>()
            for (file in files) items.add(StorageFile.getFileFromPathAsync(file.path).await())
            val packageContent = DataPackage().apply {
                setStorageItems(items)
                requestedOperation = DataPackageOperation.Copy
            }
            val copied = Clipboard.setContentWithOptions(packageContent, ClipboardContentOptions())
            FilesStatusText.text = if (copied) "${items.size} file(s) copied to clipboard." else "Error copying files to clipboard."
            if (copied) announce(checkNotNull(sender).asWinRT<UIElement>(), "${items.size} files copied to clipboard", "FilesCopiedSuccessNotificationId")
        }
    }

    private fun PasteFiles_Click(sender: Any?, args: RoutedEventArgs) {
        tasks.launch {
            val packageView = Clipboard.getContent()
            if (!packageView.contains(StandardDataFormats.storageItems)) {
                FilesStatusText.text = "StorageItems format is not available in the clipboard."
                return@launch
            }
            try {
                val items = packageView.getStorageItemsAsync().await()
                FilesStatusText.text = "Requested operation: ${packageView.requestedOperation}\nFile(s) on clipboard (${items.size}):\n" +
                    items.joinToString("\n") { "  • ${it.name}" }
                announce(checkNotNull(sender).asWinRT<UIElement>(), "Files pasted from clipboard", "FilesPastedSuccessNotificationId")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                FilesStatusText.text = "Error pasting files: " + error.message
            }
        }
    }

    private fun CopyWithOptions_Click(sender: Any?, args: RoutedEventArgs) {
        val text = HistoryRoamingTextBox.text
        if (text.isEmpty()) {
            HistoryRoamingStatusText.text = "Please enter text to copy."
            return
        }
        val options = ClipboardContentOptions().apply {
            isAllowedInHistory = AllowHistoryToggle.isOn
            isRoamable = AllowRoamingToggle.isOn
        }
        val copied = Clipboard.setContentWithOptions(DataPackage().apply { setText(text) }, options)
        HistoryRoamingStatusText.text = if (copied)
            "Text copied to clipboard. History: " + (if (options.isAllowedInHistory) "allowed" else "excluded") +
                ". Roaming: " + (if (options.isRoamable) "allowed" else "excluded") + "."
        else "Error copying content to clipboard."
        if (copied) announce(checkNotNull(sender).asWinRT<UIElement>(), "Text copied with options", "OptionsCopiedSuccessNotificationId")
    }

    private fun UpdateHistoryRoamingStatus() {
        runCatching {
            HistoryEnabledText.text = "Clipboard history: " + if (Clipboard.isHistoryEnabled()) "enabled" else "disabled"
            RoamingEnabledText.text = "Clipboard roaming: " + if (Clipboard.isRoamingEnabled()) "enabled" else "disabled"
        }
    }

    private fun ShowFormats_Click(sender: Any?, args: RoutedEventArgs) {
        val formats = Clipboard.getContent().availableFormats
        OtherOperationsStatusText.text = if (formats.isEmpty()) "The clipboard is empty."
        else "Available formats on the clipboard:\n" + formats.joinToString("\n") { "  • $it" }
    }

    private fun ClearClipboard_Click(sender: Any?, args: RoutedEventArgs) {
        try {
            Clipboard.clear()
            OtherOperationsStatusText.text = "Clipboard has been cleared."
        } catch (error: Exception) {
            OtherOperationsStatusText.text = "Error clearing clipboard: " + error.message
        }
    }

    private fun ContentChangedToggle_Toggled(sender: Any?, args: RoutedEventArgs) {
        updateMonitoring()
        OtherOperationsStatusText.text = if (ContentChangedToggle.isOn)
            "Monitoring clipboard changes..." else "Stopped monitoring clipboard changes."
    }

    private fun updateMonitoring() {
        stopMonitoring?.invoke()
        stopMonitoring = null
        if (!ContentChangedToggle.isOn) return
        val subscription = Clipboard.contentChanged.add { _, _ ->
            dispatcherQueue?.tryEnqueue {
                if (isLoaded && ContentChangedToggle.isOn) {
                    val formats = Clipboard.getContent().availableFormats
                    OtherOperationsStatusText.text = if (formats.isEmpty()) "Clipboard is now empty."
                    else "Clipboard content changed!\nNew formats:\n" + formats.joinToString("\n") { "  • $it" }
                }
            }
        }
        stopMonitoring = { Clipboard.contentChanged.remove(subscription) }
    }
}
