package io.github.composefluent.winrt.gallery.dateandtime

import io.github.composefluent.winrt.gallery.GalleryPage
import microsoft.ui.xaml.controls.Page
import windows.globalization.Calendar

@GalleryPage(route = "DatePicker", title = "DatePicker", group = "DateAndTime", order = 2)
internal class DatePickerPage : Page() {
    override fun initializeComponent() {
        super.initializeComponent()
        val calendar = Calendar()
        Control2.minYear = calendar.getDateTime()
        calendar.addMonths(2)
        Control2.date = calendar.getDateTime()
        calendar.setToNow()
        calendar.addYears(5)
        Control2.maxYear = calendar.getDateTime()
    }
}
