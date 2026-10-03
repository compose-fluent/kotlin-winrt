// Copyright (c) Microsoft Corporation. Licensed under the MIT License.
package io.github.composefluent.winrt.gallery.controls
import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.gallery.models.ControlInfoDataItem
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.ToggleButton
import windows.foundation.Uri
import windows.applicationmodel.datatransfer.*
internal class PageHeader : UserControl() {
    var Item: ControlInfoDataItem? = null
    private var ToggleThemeAction: (() -> Unit)? = null
    private var SetFavoriteAction: ((Boolean) -> Unit)? = null
    fun ConfigureActions(toggleTheme: () -> Unit, setFavorite: (Boolean) -> Unit) {
        ToggleThemeAction = toggleTheme
        SetFavoriteAction = setFavorite
    }
    var ThemeButtonVisibility: Visibility
        get() = getValue(ThemeButtonVisibilityProperty) as Visibility
        set(value) { setValue(ThemeButtonVisibilityProperty,value) }
    private fun UserControl_Loaded(sender: Any?,args: RoutedEventArgs) {
        val item = Item ?: return
        APIDetailsBtn.visibility = if (item.ApiNamespace.isBlank() && item.BaseClasses.isEmpty()) Visibility.Collapsed else Visibility.Visible
        FavoriteButton.isChecked = item.UniqueId in GalleryPreferences.routes("Favorites")
        ControlSourcePanel.visibility = if (item.SourcePath.isBlank()) Visibility.Collapsed else Visibility.Visible
        ControlSourceSeparator.visibility = ControlSourcePanel.visibility
        if (item.SourcePath.isNotBlank()) ControlSourceLink.navigateUri = Uri("https://github.com/microsoft/microsoft-ui-xaml/tree/main/controls/dev" + item.SourcePath)
        PageCodeGitHubLink.navigateUri = Uri("https://github.com/compose-fluent/kotlin-winrt/blob/xaml-support/" + item.RepositorySourcePath)
        PageMarkupGitHubLink.navigateUri = Uri("https://github.com/compose-fluent/kotlin-winrt/blob/xaml-support/" + item.RepositorySourcePath.removeSuffix(".kt") + ".xaml")
    }
    private fun GetControlSourceInfoText(): String = "Source code of ${Item?.Title.orEmpty()} in the WinUI repository. For some controls only the XAML file is available."
    private fun GetSamplePageSourceInfoText(): String = "XAML and Kotlin source code of ${Item?.Title.orEmpty()} in the Kotlin/WinRT repository."
    private fun GetFavoriteGlyph(value: Boolean?): String = if (value == true) "\uE735" else "\uE734"
    private fun GetFavoriteToolTip(value: Boolean?): String = if (value == true) "Remove from favorites" else "Add to favorites"
    private fun FavoriteButton_Click(sender: Any?,args: RoutedEventArgs) {
        val selected = FavoriteButton.isChecked == true
        SetFavoriteAction?.invoke(selected)
    }
    private fun OnThemeButtonClick(sender: Any?,args: RoutedEventArgs) {
        ToggleThemeAction?.invoke(); announce(ThemeButton,"Theme changed.","ThemeChangedSuccessNotificationId")
    }
    private fun OnCopyLinkButtonClick(sender: Any?,args: RoutedEventArgs) {
        val item = Item ?: return
        Clipboard.setContent(DataPackage().apply { setText("kotlin-winui-gallery://${item.UniqueId}") })
        if (!GalleryPreferences.flag("HideCopyLinkTip")) CopyLinkButtonTeachingTip.isOpen = true
    }
    private fun OnCopyDontShowAgainButtonClick(sender: TeachingTip,args: Any?) {
        GalleryPreferences.putFlag("HideCopyLinkTip",true); CopyLinkButtonTeachingTip.isOpen = false
    }
    companion object {
        val ThemeButtonVisibilityProperty: DependencyProperty = DependencyProperty.register("ThemeButtonVisibility", Visibility::class, PageHeader::class, PropertyMetadata(Visibility.Visible))
    }
}
