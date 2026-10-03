package io.github.composefluent.winrt.gallery.shell

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import io.github.composefluent.winrt.runtime.WinRTObservableList
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*
import microsoft.ui.xaml.media.animation.*

@GalleryPage(route = "BadgeNotificationManager", title = "Badge notifications", group = "Shell", order = 1)
internal class BadgeNotificationManagerPage : Page() {
    private val badgeNotificationGlyphs: List<microsoft.windows.badgenotifications.BadgeNotificationGlyph> = listOf(
        microsoft.windows.badgenotifications.BadgeNotificationGlyph.None, microsoft.windows.badgenotifications.BadgeNotificationGlyph.Activity,
        microsoft.windows.badgenotifications.BadgeNotificationGlyph.Alert, microsoft.windows.badgenotifications.BadgeNotificationGlyph.Available,
        microsoft.windows.badgenotifications.BadgeNotificationGlyph.Away, microsoft.windows.badgenotifications.BadgeNotificationGlyph.Busy,
        microsoft.windows.badgenotifications.BadgeNotificationGlyph.NewMessage, microsoft.windows.badgenotifications.BadgeNotificationGlyph.Paused,
        microsoft.windows.badgenotifications.BadgeNotificationGlyph.Playing, microsoft.windows.badgenotifications.BadgeNotificationGlyph.Unavailable,
        microsoft.windows.badgenotifications.BadgeNotificationGlyph.Error, microsoft.windows.badgenotifications.BadgeNotificationGlyph.Attention,
        microsoft.windows.badgenotifications.BadgeNotificationGlyph.Alarm)
    private var selectedGlyph: microsoft.windows.badgenotifications.BadgeNotificationGlyph = microsoft.windows.badgenotifications.BadgeNotificationGlyph.Activity
    private var isBadgeSetted = false
    private fun SetBadgeCountButton_Click(sender: Any?, args: RoutedEventArgs) { if (GalleryPreferences.packaged) { checkNotNull(microsoft.windows.badgenotifications.BadgeNotificationManager.current).setBadgeAsCount(BadgeCountBox.value.toUInt()); isBadgeSetted = true } }
    private fun ClearBadgeButton_Click(sender: Any?, args: RoutedEventArgs) { if (GalleryPreferences.packaged) checkNotNull(microsoft.windows.badgenotifications.BadgeNotificationManager.current).clearBadge() }
    private fun SetBadgeGlyphButton_Click(sender: Any?, args: RoutedEventArgs) { if (GalleryPreferences.packaged) { checkNotNull(microsoft.windows.badgenotifications.BadgeNotificationManager.current).setBadgeAsGlyph(selectedGlyph); isBadgeSetted = true } }
    private fun BadgeCountBox_ValueChanged(sender: NumberBox, args: NumberBoxValueChangedEventArgs) { if (GalleryPreferences.packaged && isBadgeSetted) checkNotNull(microsoft.windows.badgenotifications.BadgeNotificationManager.current).setBadgeAsCount(sender.value.toUInt()) }
    private fun BadgeNotificationGlyphsCombo_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) { if (GalleryPreferences.packaged && isBadgeSetted) checkNotNull(microsoft.windows.badgenotifications.BadgeNotificationManager.current).setBadgeAsGlyph(selectedGlyph) }
}
