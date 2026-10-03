package io.github.composefluent.winrt.gallery.media

import io.github.composefluent.winrt.gallery.GalleryPage
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.RoutedEventArgs
import microsoft.ui.xaml.controls.Button
import microsoft.ui.xaml.ElementSoundKind
import microsoft.ui.xaml.ElementSoundPlayer
import microsoft.ui.xaml.ElementSoundPlayerState
import microsoft.ui.xaml.ElementSpatialAudioMode
import microsoft.ui.xaml.controls.Page

@GalleryPage(route = "Sound", title = "Sound", group = "Media", order = 6)
internal class SoundPage : Page() {
    override fun initializeComponent() {
        super.initializeComponent()
        soundToggle.isOn = ElementSoundPlayer.state == ElementSoundPlayerState.On
        spatialAudioBox.isEnabled = soundToggle.isOn
        spatialAudioBox.isChecked = soundToggle.isOn && ElementSoundPlayer.spatialAudioMode == ElementSpatialAudioMode.On
    }

    private fun Button_Click(sender: Any?, args: RoutedEventArgs) {
        val index = checkNotNull(sender).asWinRT<Button>().tag?.toString()?.toIntOrNull() ?: return
        val kind = listOf(ElementSoundKind.Focus, ElementSoundKind.Invoke, ElementSoundKind.Show,
            ElementSoundKind.Hide, ElementSoundKind.MovePrevious, ElementSoundKind.MoveNext, ElementSoundKind.GoBack)
            .getOrNull(index) ?: return
        ElementSoundPlayer.play(kind)
    }

    private fun spatialAudioBox_Checked(sender: Any?, args: RoutedEventArgs) {
        if (soundToggle.isOn) ElementSoundPlayer.spatialAudioMode = ElementSpatialAudioMode.On
    }

    private fun spatialAudioBox_Unchecked(sender: Any?, args: RoutedEventArgs) {
        if (soundToggle.isOn) ElementSoundPlayer.spatialAudioMode = ElementSpatialAudioMode.Off
    }

    private fun soundToggle_Toggled(sender: Any?, args: RoutedEventArgs) {
        spatialAudioBox.isEnabled = soundToggle.isOn
        if (soundToggle.isOn) {
            ElementSoundPlayer.state = ElementSoundPlayerState.On
        } else {
            spatialAudioBox.isChecked = false
            ElementSoundPlayer.state = ElementSoundPlayerState.Off
            ElementSoundPlayer.spatialAudioMode = ElementSpatialAudioMode.Off
        }
    }
}
