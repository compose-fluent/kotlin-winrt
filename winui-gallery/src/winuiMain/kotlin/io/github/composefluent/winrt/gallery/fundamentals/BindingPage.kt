package io.github.composefluent.winrt.gallery.fundamentals

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import io.github.composefluent.winrt.runtime.await
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.media.*

@GalleryPage(route = "Binding", title = "Binding", group = "FundamentalsItem", order = 2)
internal class BindingPage : Page() {
    var GreetingMessage: String = "Hello, WinUI 3!"
    val ViewModel: ExampleViewModel = ExampleViewModel()
    val Items: List<ListDetailItem> = listOf(
        ListDetailItem(0, "Item 1", "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Integer id facilisis lectus. Cras nec convallis ante, quis pulvinar tellus.", bindingDate(2025, 6, 15, 9, 30)),
        ListDetailItem(1, "Item 2", "Quisque accumsan pretium ligula in faucibus. Mauris sollicitudin augue vitae lorem cursus condimentum quis ac mauris.", bindingDate(2025, 7, 22, 14, 15)),
        ListDetailItem(2, "Item 3", "Ut consequat magna luctus justo egestas vehicula. Integer pharetra risus libero, et posuere justo mattis et.", bindingDate(2025, 8, 3, 11, 0)),
        ListDetailItem(3, "Item 4", "Duis facilisis, quam ut laoreet commodo, elit ex aliquet massa, non varius tellus lectus et nunc.", bindingDate(2025, 9, 10, 16, 45))
    )
    override fun initializeComponent() {
        super.initializeComponent()
        dataContext = ViewModel
    }
    fun FormatDate(date: kotlin.time.Instant?): String = date?.let {
        "Selected date is: ${windows.globalization.datetimeformatting.DateTimeFormatter("dayofweek month day year").format(it)}"
    } ?: "No date selected"
}
