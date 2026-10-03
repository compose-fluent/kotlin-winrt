// Copyright (c) Microsoft Corporation. Licensed under the MIT License.
package io.github.composefluent.winrt.gallery.media

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*
import kotlinx.coroutines.sync.withLock

@GalleryPage(route = "CaptureElementPreview", title = "Capture Element / Camera Preview", group = "Media", order = 1)
internal class CaptureElementPreviewPage : Page(), microsoft.ui.xaml.data.INotifyPropertyChanged {
    private val tasks = GalleryPageTasks(this)
    private val cameraLock = kotlinx.coroutines.sync.Mutex()
    private var mediaCapture: windows.media.capture.MediaCapture? = null
    private var previewSource: windows.media.core.MediaSource? = null
    private var generation = 0
    private var snapshotsExpanded = false
    private val propertyHandlers: MutableList<microsoft.ui.xaml.data.PropertyChangedEventHandler> = mutableListOf()
    var MirrorTextReplacement: String = ""
        private set
    override fun addPropertyChanged(handler: microsoft.ui.xaml.data.PropertyChangedEventHandler) { propertyHandlers.add(handler) }
    override fun removePropertyChanged(handler: microsoft.ui.xaml.data.PropertyChangedEventHandler) { propertyHandlers.remove(handler) }
    override fun initializeComponent() {
        super.initializeComponent(); dataContext = this
        loaded.add { _, _ -> tasks.launch {
            try {
                val groups = windows.media.capture.frames.MediaFrameSourceGroup.findAllAsync().await()
                if (groups.isEmpty()) frameSourceName.text = "No camera devices found."
                else { cameraSourceComboBox.itemsSource = groups; cameraSourceComboBox.selectedIndex = 0 }
                if (!snapshotsExpanded) {
                    snapshotsExpanded = true; val viewer = captureContainer.children.removeAt(0)
                    captureContainer.children.add(ExpandToFillContainer().apply { children.add(viewer) })
                }
                kotlinx.coroutines.awaitCancellation()
            } finally { kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { cameraLock.withLock { generation++; captureElement.source = null; releaseCapture() } } }
        } }
    }
    private suspend fun releaseCapture() {
        val source = previewSource; previewSource = null
        val capture = mediaCapture; mediaCapture = null
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.Default) { source?.close(); capture?.close() }
    }
    private suspend fun StartCapture(sourceGroup: windows.media.capture.frames.MediaFrameSourceGroup, request: Int) {
        cameraLock.withLock {
            if (request != generation) return@withLock
            captureElement.source = null; releaseCapture(); captureButton.isEnabled = false
            frameSourceName.text = "Viewing: ${sourceGroup.displayName}"
            val candidate = windows.media.capture.MediaCapture()
            var attached = false
            try {
                candidate.initializeAsync(windows.media.capture.MediaCaptureInitializationSettings().apply {
                    this.sourceGroup = sourceGroup; sharingMode = windows.media.capture.MediaCaptureSharingMode.SharedReadOnly
                    streamingCaptureMode = windows.media.capture.StreamingCaptureMode.Video; memoryPreference = windows.media.capture.MediaCaptureMemoryPreference.Cpu
                }).await()
                if (request != generation) return@withLock
                val info = sourceGroup.sourceInfos.firstOrNull { it.sourceKind == windows.media.capture.frames.MediaFrameSourceKind.Color } ?: sourceGroup.sourceInfos.first()
                previewSource = windows.media.core.MediaSource.createFromMediaFrameSource(checkNotNull(candidate.frameSources[info.id]))
                captureElement.source = previewSource; mediaCapture = candidate; attached = true; captureButton.isEnabled = true
            } finally { if (!attached) kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.Default) { candidate.close() } }
        }
    }
    private fun CameraSourceComboBox_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) {
        val selected = cameraSourceComboBox.selectedItem?.asWinRT<windows.media.capture.frames.MediaFrameSourceGroup>() ?: return
        val request = ++generation
        tasks.launch {
            try { StartCapture(selected, request) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { frameSourceName.text = "Error: ${error.message}"; throw error }
        }
    }
    private fun MirrorToggleSwitch_Toggled(sender: Any?, args: RoutedEventArgs) {
        captureElement.renderTransform = if (mirrorSwitch.isOn) ScaleTransform().apply { scaleX = -1.0 } else null
        captureElement.renderTransformOrigin = windows.foundation.Point(0.5f, 0.5f)
        MirrorTextReplacement = if (mirrorSwitch.isOn) "\n        captureElement.renderTransform = ScaleTransform().apply { scaleX = -1.0 }\n        captureElement.renderTransformOrigin = Point(0.5f, 0.5f)\n" else ""
        propertyHandlers.toList().forEach { it(this, microsoft.ui.xaml.data.PropertyChangedEventArgs("MirrorTextReplacement")) }
    }
    private fun CapturePhoto_Click(sender: Any?, args: RoutedEventArgs) { tasks.launch { cameraLock.withLock {
        val capture = mediaCapture ?: return@withLock
        windows.storage.streams.InMemoryRandomAccessStream().use { stream ->
            capture.capturePhotoToStreamAsync(windows.media.mediaproperties.ImageEncodingProperties.createJpeg(), stream).await(); stream.seek(0uL)
            val bitmap = microsoft.ui.xaml.media.imaging.BitmapImage(); bitmap.setSourceAsync(stream).await()
            snapshots.children.add(0, Image().apply { source = bitmap }); capturedText.visibility = Visibility.Visible
            announce(captureButton, "Photo successfully captured.", "CameraPreviewSampleCaptureNotificationId")
        }
    } } }
}
