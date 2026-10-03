package io.github.composefluent.winrt.gallery.system

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.windows.storage.pickers.PickerLocationId
import microsoft.windows.storage.pickers.PickerViewMode
import windows.storage.fileproperties.ThumbnailMode

@GalleryPage(route = "StoragePickers", title = "Storage pickers", group = "System", order = 2)
internal class StoragePickersPage : Page() {
    private val tasks = GalleryPageTasks(this)
    private val pickerLocationIds: List<PickerLocationId> = listOf(PickerLocationId.DocumentsLibrary, PickerLocationId.ComputerFolder, PickerLocationId.Desktop, PickerLocationId.Downloads, PickerLocationId.MusicLibrary, PickerLocationId.PicturesLibrary, PickerLocationId.VideosLibrary, PickerLocationId.Objects3D, PickerLocationId.Unspecified)
    private val pickerViewModes: List<PickerViewMode> = listOf(PickerViewMode.List, PickerViewMode.Thumbnail)
    private val thumbnailModes: List<ThumbnailMode> = listOf(ThumbnailMode.PicturesView, ThumbnailMode.VideosView, ThumbnailMode.MusicView, ThumbnailMode.DocumentsView, ThumbnailMode.ListView, ThumbnailMode.SingleItem)
    private fun WindowId(): microsoft.ui.WindowId = checkNotNull(checkNotNull(xamlRoot).contentIslandEnvironment).appWindowId
    private fun AddFilters(picker: microsoft.windows.storage.pickers.FileOpenPicker, combo: ComboBox) {
        when (combo.selectedItem?.asWinRT<ComboBoxItem>()?.tag?.toString()) { ".txt" -> picker.fileTypeFilter.add(".txt"); "images" -> { picker.fileTypeFilter.add(".jpg"); picker.fileTypeFilter.add(".png") }; else -> picker.fileTypeFilter.add("*") }
    }
    private fun ComboBoxItemToFileFilter(value: Any?): String = when (value?.asWinRT<ComboBoxItem>()?.tag?.toString()) {
        ".txt" -> "\n        picker.fileTypeFilter.add(\".txt\")"
        "images" -> "\n        picker.fileTypeFilter.add(\".jpg\")\n        picker.fileTypeFilter.add(\".png\")"
        else -> ""
    }
    private fun TxtCheckBoxIsCheckedToCode(value: Boolean?): String = if (value == true) "\n        picker.fileTypeChoices[\"Text Files\"] = listOf(\".txt\")\n" else ""
    private fun JsonCheckBoxIsCheckedToCode(value: Boolean?): String = if (value == true) "\n        picker.fileTypeChoices[\"JSON Files\"] = listOf(\".json\")\n" else ""
    private fun XmlCheckBoxIsCheckedToCode(value: Boolean?): String = if (value == true) "\n        picker.fileTypeChoices[\"XML Files\"] = listOf(\".xml\")\n" else ""
    private fun PickSingleFileButton_Click(sender: Any?, args: RoutedEventArgs) {
        val button = checkNotNull(sender).asWinRT<Button>(); button.isEnabled = false
        tasks.launch { try {
            val picker = microsoft.windows.storage.pickers.FileOpenPicker(WindowId()).apply {
                AddFilters(this, FileTypeComboBox1); commitButtonText = CommitButtonTextTextBox.text
                suggestedStartLocation = PickerLocationComboBox1.selectedItem as microsoft.windows.storage.pickers.PickerLocationId
                viewMode = PickerViewModeComboBox1.selectedItem as microsoft.windows.storage.pickers.PickerViewMode
            }
            PickedSingleFileTextBlock.text = picker.pickSingleFileAsync().await()?.let { "Picked: ${it.path}" } ?: "No file selected."
            announce(button, PickedSingleFileTextBlock.text, "FilePickedNotificationId")
        } finally { button.isEnabled = true } }
    }
    private fun PickMultipleFilesButton_Click(sender: Any?, args: RoutedEventArgs) {
        val button = checkNotNull(sender).asWinRT<Button>(); button.isEnabled = false
        tasks.launch { try {
            val picker = microsoft.windows.storage.pickers.FileOpenPicker(WindowId()).apply {
                AddFilters(this, FileTypeComboBox2); commitButtonText = CommitButtonTextTextBox2.text
                suggestedStartLocation = PickerLocationComboBox2.selectedItem as microsoft.windows.storage.pickers.PickerLocationId
                viewMode = PickerViewModeComboBox2.selectedItem as microsoft.windows.storage.pickers.PickerViewMode
            }
            val files = picker.pickMultipleFilesAsync().await()
            PickedMultipleFilesTextBlock.text = if (files.isEmpty()) "No files selected." else files.joinToString("\n") { "- Picked: ${it.path}" }
            announce(button, PickedMultipleFilesTextBlock.text, "FilesPickedNotificationId")
        } finally { button.isEnabled = true } }
    }
    private fun SaveFileButton_Click(sender: Any?, args: RoutedEventArgs) {
        val button = checkNotNull(sender).asWinRT<Button>(); button.isEnabled = false
        tasks.launch { try {
            val picker = microsoft.windows.storage.pickers.FileSavePicker(WindowId()).apply {
                if (TxtCheckBox.isChecked == true) fileTypeChoices["Text Files"] = mutableListOf(".txt")
                if (JsonCheckBox.isChecked == true) fileTypeChoices["JSON Files"] = mutableListOf(".json")
                if (XmlCheckBox.isChecked == true) fileTypeChoices["XML Files"] = mutableListOf(".xml")
                defaultFileExtension = DefaultExtensionComboBox.selectedItem.toString(); suggestedFileName = SuggestedFileNameTextBox.text
                commitButtonText = CommitButtonTextTextBox3.text; suggestedFolder = SuggestedFolderTextBox.text
                suggestedStartLocation = PickerLocationComboBox3.selectedItem as microsoft.windows.storage.pickers.PickerLocationId
            }
            val result = picker.pickSaveFileAsync().await()
            if (result == null) SavedFileTextBlock.text = "File save canceled." else {
                val directory = windows.storage.StorageFolder.getFolderFromPathAsync(result.path.substringBeforeLast('\\')).await()
                val file = directory.createFileAsync(result.path.substringAfterLast('\\'), windows.storage.CreationCollisionOption.ReplaceExisting).await()
                windows.storage.FileIO.writeTextAsync(file, FileContentTextBox.text).await()
                SavedFileTextBlock.text = "File saved to: ${result.path}"
            }
            announce(button, SavedFileTextBlock.text, "FileSavedNotificationId")
        } finally { button.isEnabled = true } }
    }
    private fun PickFolderButton_Click(sender: Any?, args: RoutedEventArgs) {
        val button = checkNotNull(sender).asWinRT<Button>(); button.isEnabled = false
        tasks.launch { try {
            val picker = microsoft.windows.storage.pickers.FolderPicker(WindowId()).apply {
                commitButtonText = CommitButtonTextTextBox4.text
                suggestedStartLocation = PickerLocationComboBox4.selectedItem as microsoft.windows.storage.pickers.PickerLocationId
                viewMode = PickerViewModeComboBox3.selectedItem as microsoft.windows.storage.pickers.PickerViewMode
            }
            PickedFolderTextBlock.text = picker.pickSingleFolderAsync().await()?.let { "Picked: ${it.path}" } ?: "No folder selected."
            announce(button, PickedFolderTextBlock.text, "FolderPickedNotificationId")
        } finally { button.isEnabled = true } }
    }
    private fun SelectSuggestedFolderButton_Click(sender: Any?, args: RoutedEventArgs) {
        val button = checkNotNull(sender).asWinRT<Button>(); button.isEnabled = false
        tasks.launch { try {
            val folder = microsoft.windows.storage.pickers.FolderPicker(WindowId()).apply { commitButtonText = "Select folder" }.pickSingleFolderAsync().await()
            if (folder != null) SuggestedFolderTextBox.text = folder.path
            announce(button, folder?.let { "Folder selected: ${it.path}" } ?: "No folder selected", "SuggestedFolderNotificationId")
        } finally { button.isEnabled = true } }
    }
    private fun PickFileForThumbnailButton_Click(sender: Any?, args: RoutedEventArgs) {
        val button = checkNotNull(sender).asWinRT<Button>(); button.isEnabled = false
        tasks.launch { try {
            val selected = microsoft.windows.storage.pickers.FileOpenPicker(WindowId()).apply { fileTypeFilter.add("*") }.pickSingleFileAsync().await()
            if (selected == null) { ThumbnailImage.source = null; ThumbnailDetailsTextBlock.text = "No file selected."; return@launch }
            val file = windows.storage.StorageFile.getFileFromPathAsync(selected.path).await()
            val mode = ThumbnailModeComboBox.selectedItem as windows.storage.fileproperties.ThumbnailMode
            val pixels = ThumbnailSizeNumberBox.value.takeIf { !it.isNaN() && it > 0 }?.toUInt() ?: 200u
            val thumbnail = file.getThumbnailAsync(mode, pixels, windows.storage.fileproperties.ThumbnailOptions.UseCurrentScale).await()
            if (thumbnail == null) { ThumbnailImage.source = null; ThumbnailDetailsTextBlock.text = "No thumbnail available for the selected file." } else try {
                val bitmap = microsoft.ui.xaml.media.imaging.BitmapImage(); bitmap.setSourceAsync(thumbnail).await(); ThumbnailImage.source = bitmap
                ThumbnailDetailsTextBlock.text = "File: ${file.name}\nMode: ThumbnailMode.$mode\nRequested size: $pixels\nReturned size: ${thumbnail.originalWidth} x ${thumbnail.originalHeight}"
            } finally { thumbnail.close() }
            announce(button, ThumbnailDetailsTextBlock.text, "ThumbnailPickedNotificationId")
        } catch (error: Exception) { ThumbnailImage.source = null; ThumbnailDetailsTextBlock.text = "Could not retrieve a thumbnail. ${error.message}" }
          finally { button.isEnabled = true } }
    }
}
