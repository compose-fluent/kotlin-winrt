package io.github.composefluent.winrt.gallery.fundamentals

import microsoft.ui.xaml.Visibility
import microsoft.ui.xaml.data.INotifyPropertyChanged
import microsoft.ui.xaml.data.PropertyChangedEventArgs
import microsoft.ui.xaml.data.PropertyChangedEventHandler
import microsoft.ui.xaml.data.IValueConverter
import kotlin.reflect.KClass

internal class ExampleViewModel : INotifyPropertyChanged {
    private val handlers = mutableListOf<PropertyChangedEventHandler>()
    var Title: String = "Welcome to WinUI 3"
        set(value) { if (field != value) { field = value; changed("Title") } }
    var Description: String = "This is an example of binding to a view model."
        set(value) { if (field != value) { field = value; changed("Description") } }
    var NullString: String? = ""
        set(value) { if (field != value) { field = value; changed("NullString") } }
    override fun addPropertyChanged(handler: PropertyChangedEventHandler) { handlers.add(handler) }
    override fun removePropertyChanged(handler: PropertyChangedEventHandler) { handlers.remove(handler) }
    private fun changed(name: String) { handlers.toList().forEach { it(this, PropertyChangedEventArgs(name)) } }
}

internal class ListDetailItem(val Id: Int, val Title: String, val Text: String, val DateCreated: kotlin.time.Instant) {
    // WinRT's formatter keeps this shared sample locale-aware on JVM and Native.
    val DateCreatedFormatted: String
        get() = windows.globalization.datetimeformatting.DateTimeFormatter("month.abbreviated day year hour minute").format(DateCreated)
}

internal fun bindingDate(year: Int, month: Int, day: Int, hour: Int, minute: Int): kotlin.time.Instant =
    windows.globalization.Calendar().apply {
        changeClock("24HourClock")
        this.day = 1
        this.year = year
        this.month = month
        this.day = day
        this.hour = hour
        this.minute = minute
        second = 0
        nanosecond = 0
    }.getDateTime()

internal class EmptyStringToVisibilityConverter : IValueConverter {
    override fun convert(value: Any?, targetType: KClass<*>?, parameter: Any?, language: String): Any? =
        if ((value as? String).isNullOrEmpty()) Visibility.Collapsed else Visibility.Visible
    override fun convertBack(value: Any?, targetType: KClass<*>?, parameter: Any?, language: String): Any? =
        throw UnsupportedOperationException("Visibility conversion is one way")
}
