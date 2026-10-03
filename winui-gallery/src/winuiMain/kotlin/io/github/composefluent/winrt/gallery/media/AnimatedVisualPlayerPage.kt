package io.github.composefluent.winrt.gallery.media

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.gallery.samplepages.*
import io.github.composefluent.winrt.runtime.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*
import microsoft.ui.xaml.media.animation.*

@GalleryPage(route = "AnimatedVisualPlayer", title = "AnimatedVisualPlayer", group = "Media", order = 0)
internal class AnimatedVisualPlayerPage : Page() {
    private val tasks = GalleryPageTasks(this)
    private var ready = false
    override fun initializeComponent() { super.initializeComponent(); ready = true; unloaded.add { _, _ -> Player.stop(); Player.source = null } }
    private fun EnsurePlaying() { if (PauseButton.isChecked == true) PauseButton.isChecked = false else if (!Player.isPlaying) tasks.launch { Player.playAsync(0.0, 1.0, false).await() } }
    private fun PlayButton_Click(sender: Any?, args: RoutedEventArgs) { Player.playbackRate = 1.0; EnsurePlaying() }
    private fun PauseButton_Checked(sender: Any?, args: RoutedEventArgs) { if (ready) Player.pause() }
    private fun PauseButton_Unchecked(sender: Any?, args: RoutedEventArgs) { if (ready) Player.resume() }
    private fun StopButton_Click(sender: Any?, args: RoutedEventArgs) { Player.stop(); PauseButton.isChecked = false }
    private fun ReverseButton_Click(sender: Any?, args: RoutedEventArgs) { Player.playbackRate = -1.0; EnsurePlaying() }
}
