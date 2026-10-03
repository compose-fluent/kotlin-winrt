package io.github.composefluent.winrt.gallery.dateandtime

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import io.github.composefluent.winrt.runtime.WinRTObservableList
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*
import microsoft.ui.xaml.media.animation.*

@GalleryPage(route = "CalendarView", title = "CalendarView", group = "DateAndTime", order = 1)
internal class CalendarViewPage : Page() {
    val Languages: MutableList<io.github.composefluent.winrt.gallery.helpers.Language> = WinRTObservableList(io.github.composefluent.winrt.gallery.helpers.LanguageList.Languages)
    private var ready = false
    override fun initializeComponent() {
        super.initializeComponent(); ready = true
        calendarIdentifier.itemsSource = listOf(windows.globalization.CalendarIdentifiers.gregorian, windows.globalization.CalendarIdentifiers.hebrew,
            windows.globalization.CalendarIdentifiers.hijri, windows.globalization.CalendarIdentifiers.japanese, windows.globalization.CalendarIdentifiers.julian,
            windows.globalization.CalendarIdentifiers.korean, windows.globalization.CalendarIdentifiers.persian, windows.globalization.CalendarIdentifiers.taiwan,
            windows.globalization.CalendarIdentifiers.thai, windows.globalization.CalendarIdentifiers.umAlQura)
        calendarIdentifier.selectedItem = windows.globalization.CalendarIdentifiers.gregorian
    }
    private fun SelectionMode_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) {
        if (ready) Control1.selectionMode = when (checkNotNull(sender).asWinRT<ComboBox>().selectedItem?.toString()) {
            "Multiple" -> CalendarViewSelectionMode.Multiple; "None" -> CalendarViewSelectionMode.None; else -> CalendarViewSelectionMode.Single
        }
    }
    private fun calendarLanguages_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) {
        if (!ready) return
        val selected = calendarLanguages.selectedItem as? io.github.composefluent.winrt.gallery.helpers.Language ?: return
        if (windows.globalization.Language.isWellFormed(selected.Code)) Control1.language = selected.Code
    }
}
