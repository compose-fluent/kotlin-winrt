// Copyright (c) Microsoft Corporation. Licensed under the MIT License.
package io.github.composefluent.winrt.gallery.pages
import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.await
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import windows.applicationmodel.Package
import windows.applicationmodel.datatransfer.*
import windows.foundation.Uri
internal class SettingsPage(private val root: FrameworkElement,private val theme: ElementTheme,
    private val setTheme: (ElementTheme) -> Unit,private val setTopNavigation: (Boolean) -> Unit,
    private val clearRecents: () -> Unit,private val clearFavorites: () -> Unit) : Page() {
    private val tasks = GalleryPageTasks(this)
    private var ready = false
    val AboutTitle: String get() = "Kotlin WinUI Gallery ($galleryTargetName)"
    val Version: String get() = if (GalleryPreferences.packaged) checkNotNull(checkNotNull(Package.current).id).version.let { "${it.major}.${it.minor}.${it.build}.${it.revision}" } else ""
    val WinAppSdkRuntimeDetails: String = "Windows App SDK 2.5.1"
    override fun initializeComponent() {
        super.initializeComponent()
        themeMode.selectedIndex = when (theme) { ElementTheme.Light -> 0; ElementTheme.Dark -> 1; else -> 2 }
        navigationLocation.selectedIndex = if (GalleryPreferences.flag("TopNavigation")) 1 else 0
        soundToggle.isOn = GalleryPreferences.flag("Sound")
        spatialSoundBox.isOn = GalleryPreferences.flag("SpatialAudio")
        SpatialAudioCard.isEnabled = soundToggle.isOn
        CheckRecentAndFavoriteButtonStates()
        ready = true
    }
    private fun CheckRecentAndFavoriteButtonStates() {
        ClearRecentBtn.isEnabled = GalleryPreferences.routes("Recent").isNotEmpty()
        UnfavoriteBtn.isEnabled = GalleryPreferences.routes("Favorites").isNotEmpty()
    }
    private fun themeMode_SelectionChanged(sender: Any?,args: SelectionChangedEventArgs) {
        if (!ready) return
        val value = listOf(ElementTheme.Light,ElementTheme.Dark,ElementTheme.Default).getOrNull(themeMode.selectedIndex) ?: return
        setTheme(value); announce(themeMode,"Theme changed to $value","ThemeChangedNotificationActivityId")
    }
    private fun navigationLocation_SelectionChanged(sender: Any?,args: SelectionChangedEventArgs) { if (ready) setTopNavigation(navigationLocation.selectedIndex == 1) }
    private fun soundToggle_Toggled(sender: Any?,args: RoutedEventArgs) {
        if (!ready) return
        SpatialAudioCard.isEnabled = soundToggle.isOn
        ElementSoundPlayer.state = if (soundToggle.isOn) ElementSoundPlayerState.On else ElementSoundPlayerState.Off
        GalleryPreferences.putFlag("Sound",soundToggle.isOn)
        if (!soundToggle.isOn) spatialSoundBox.isOn = false
    }
    private fun spatialSoundBox_Toggled(sender: Any?,args: RoutedEventArgs) {
        if (!ready) return
        val enabled = soundToggle.isOn && spatialSoundBox.isOn
        ElementSoundPlayer.spatialAudioMode = if (enabled) ElementSpatialAudioMode.On else ElementSpatialAudioMode.Off
        GalleryPreferences.putFlag("SpatialAudio",enabled)
    }
    private fun soundPageHyperlink_Click(sender: Any?,args: RoutedEventArgs) { GalleryNavigationHost.navigate("Sound") }
    private fun toCloneRepoCard_Click(sender: Any?,args: RoutedEventArgs) {
        Clipboard.setContent(DataPackage().apply { setText(gitCloneTextBlock.text) })
        announce(this,"Repository clone command copied","RepositoryCopied")
    }
    private fun bugRequestCard_Click(sender: Any?,args: RoutedEventArgs) { tasks.launch { windows.system.Launcher.launchUriAsync(Uri("https://github.com/compose-fluent/kotlin-winrt/issues/new")).await() } }
    private fun confirm(title: String,description: String,actionText: String,action: () -> Unit) {
        tasks.launch {
            val dialog = ContentDialog().apply { xamlRoot = root.xamlRoot; requestedTheme = root.actualTheme; this.title = title; content = description; primaryButtonText = actionText; closeButtonText = "Cancel"; defaultButton = ContentDialogButton.Primary }
            try { if (dialog.showAsync().await() == ContentDialogResult.Primary) { action(); CheckRecentAndFavoriteButtonStates() } } finally { dialog.hide() }
        }
    }
    private fun ClearRecentBtn_Click(sender: Any?,args: RoutedEventArgs) { confirm("Clear recently visited samples?","This will remove all samples from your recent history.","Clear",clearRecents) }
    private fun UnfavoriteBtn_Click(sender: Any?,args: RoutedEventArgs) { confirm("Remove all favorites?","This will unfavorite all your samples.","Remove",clearFavorites) }
}
