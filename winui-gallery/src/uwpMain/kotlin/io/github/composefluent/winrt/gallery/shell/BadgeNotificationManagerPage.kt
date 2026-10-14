package io.github.composefluent.winrt.gallery.shell

import io.github.composefluent.winrt.gallery.*
import windows.data.xml.dom.XmlDocument
import windows.ui.notifications.BadgeNotification
import windows.ui.notifications.BadgeUpdateManager
import windows.ui.xaml.RoutedEventArgs
import windows.ui.xaml.controls.Page
import windows.ui.xaml.controls.SelectionChangedEventArgs
import microsoft.ui.xaml.controls.NumberBox
import microsoft.ui.xaml.controls.NumberBoxValueChangedEventArgs

@GalleryPage(route = "BadgeNotificationManager", title = "Badge notifications", group = "Shell", order = 1)
internal class BadgeNotificationManagerPage : Page() {
    private val tasks = GalleryPageTasks(this)
    // These are the values of the Windows badge XML schema, not projection types.
    private val badgeNotificationGlyphs = listOf("none", "activity", "alert", "available", "away", "busy", "newMessage", "paused", "playing", "unavailable", "error", "attention", "alarm")
    private var selectedGlyph = "activity"
    private var isBadgeSet = false
    private fun update(value: String) { tasks.launch {
        val xml = XmlDocument().apply { loadXml("""<badge value="$value"/>""") }
        checkNotNull(BadgeUpdateManager.createBadgeUpdaterForApplication()).update(BadgeNotification(xml))
        isBadgeSet = true
        galleryLog("Badge updated: $value")
    } }
    private fun SetBadgeCountButton_Click(sender: Any?, args: RoutedEventArgs?) = update(BadgeCountBox.value.toUInt().toString())
    private fun SetBadgeGlyphButton_Click(sender: Any?, args: RoutedEventArgs?) = update(selectedGlyph)
    private fun ClearBadgeButton_Click(sender: Any?, args: RoutedEventArgs?) { tasks.launch {
        checkNotNull(BadgeUpdateManager.createBadgeUpdaterForApplication()).clear()
        isBadgeSet = false
        galleryLog("Badge cleared")
    } }
    private fun BadgeCountBox_ValueChanged(sender: NumberBox?, args: NumberBoxValueChangedEventArgs?) {
        if (isBadgeSet && sender != null) update(sender.value.toUInt().toString())
    }
    private fun BadgeNotificationGlyphsCombo_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs?) {
        if (isBadgeSet) update(selectedGlyph)
    }
}
