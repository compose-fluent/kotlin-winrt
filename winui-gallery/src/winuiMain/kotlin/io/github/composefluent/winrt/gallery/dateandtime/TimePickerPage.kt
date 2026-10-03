package io.github.composefluent.winrt.gallery.dateandtime

import io.github.composefluent.winrt.gallery.GalleryPage
import microsoft.ui.xaml.controls.Page
import windows.globalization.Calendar
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@GalleryPage(route = "TimePicker", title = "TimePicker", group = "DateAndTime", order = 3)
internal class TimePickerPage : Page() {
    override fun initializeComponent() {
        super.initializeComponent()
        val now = Calendar().apply { changeClock("24HourClock"); setToNow() }
        val time = now.hour.hours + now.minute.minutes + now.second.seconds
        TwelveHourPicker.selectedTime = time
        TwentyFourHourPicker.selectedTime = time
    }
}