package io.github.composefluent.winrt.gallery

import microsoft.ui.xaml.controls.ColorPicker
import microsoft.ui.xaml.controls.Expander
import microsoft.ui.xaml.controls.InfoBar
import microsoft.ui.xaml.controls.InfoBarSeverity
import microsoft.ui.xaml.controls.NumberBox
import microsoft.ui.xaml.controls.NumberBoxSpinButtonPlacementMode
import microsoft.ui.xaml.controls.ProgressRing
import microsoft.ui.xaml.controls.RadioButtons
import microsoft.ui.xaml.controls.RatingControl
import microsoft.ui.xaml.controls.ToggleSplitButton
import microsoft.ui.xaml.controls.DropDownButton
import microsoft.ui.xaml.controls.TeachingTip
import microsoft.ui.xaml.controls.TreeView
import microsoft.ui.xaml.controls.TreeViewNode
import windows.ui.xaml.HorizontalAlignment
import windows.ui.xaml.TextWrapping
import windows.ui.xaml.UIElement
import windows.ui.xaml.controls.*

internal class WinUi2Sample(
    val title: String,
    val description: String,
    val code: String,
    val create: (StackPanel, (String) -> Unit) -> Unit,
)

private fun StackPanel.add(element: UIElement) { checkNotNull(children).add(element) }
private fun text(value: String) = TextBlock().apply { text = value; textWrapping = TextWrapping.Wrap }

// These are application examples, not projection rules. Imports deliberately
// distinguish the UWP controls from their WinUI 2 counterparts.
internal val winUi2Samples = listOf(
    WinUi2Sample("Home", "Kotlin/WinRT • UWP • WinUI 2.8 • $galleryTargetName",
        """
        import windows.ui.xaml.Application
        import windows.ui.xaml.Window
        import microsoft.ui.xaml.controls.NumberBox

        // UWP owns the window. WinUI 2 extends the system XAML framework.
        Window.current!!.content = GalleryShell()
        Window.current!!.activate()
        """) { panel, _ ->
        panel.add(InfoBar().apply {
            title = "Kotlin WinUI 2 Gallery"
            message = "Explore native controls, live Kotlin event handlers, and source examples. Choose a control in the navigation pane or search by name."
            severity = InfoBarSeverity.Informational
            isClosable = false
            isOpen = true
        })
        panel.add(text("WinUI 2 controls use Microsoft.UI.Xaml.Controls; the underlying application, layout, and base controls use Windows.UI.Xaml."))
        panel.add(text("This UWP variant shares the Gallery module and assets. Windows App SDK desktop APIs and WinUI 3-only controls are not part of this catalog."))
    },
    WinUi2Sample("Button", "UWP buttons with Kotlin click handlers.",
        """
        Button().apply {
            content = "Click me"
            click.add { _, _ -> clicks++; output.text = "Clicked: " + clicks }
        }
        """) { panel, report ->
        var clicks = 0
        panel.add(Button().apply { content = "Click me"; click.add { _, _ -> report("Clicked: ${++clicks}") } })
        panel.add(HyperlinkButton().apply { content = "A hyperlink button"; click.add { _, _ -> report("Hyperlink clicked") } })
    },
    WinUi2Sample("CheckBox", "Checked, unchecked, and indeterminate state.",
        """
        CheckBox().apply {
            content = "Three-state option"
            isThreeState = true
            click.add { _, _ -> output.text = "Checked: " + isChecked }
        }
        """) { panel, report ->
        panel.add(CheckBox().apply { content = "Three-state option"; isThreeState = true; click.add { _, _ -> report("Checked: $isChecked") } })
    },
    WinUi2Sample("ToggleSwitch", "Toggle an application setting.",
        """
        ToggleSwitch().apply {
            header = "Enable notifications"
            toggled.add { _, _ -> output.text = "Enabled: " + isOn }
        }
        """) { panel, report ->
        panel.add(ToggleSwitch().apply { header = "Enable notifications"; toggled.add { _, _ -> report("Enabled: $isOn") } })
    },
    WinUi2Sample("Slider", "Change a numeric value by dragging or using the keyboard.",
        """
        Slider().apply {
            header = "Volume"; minimum = 0.0; maximum = 100.0; value = 40.0
            valueChanged.add { _, _ -> output.text = "Volume: " + value.toInt() }
        }
        """) { panel, report ->
        panel.add(Slider().apply { header = "Volume"; minimum = 0.0; maximum = 100.0; value = 40.0; maxWidth = 480.0; horizontalAlignment = HorizontalAlignment.Left; valueChanged.add { _, _ -> report("Volume: ${value.toInt()}") } })
    },
    WinUi2Sample("NumberBox", "WinUI 2 number input with spin buttons and expression evaluation.",
        """
        NumberBox().apply {
            header = "Quantity"; value = 2.0; acceptsExpression = true
            spinButtonPlacementMode = NumberBoxSpinButtonPlacementMode.Inline
            valueChanged.add { _, args -> output.text = "Value: " + args!!.newValue }
        }
        """) { panel, report ->
        panel.add(NumberBox().apply { header = "Quantity"; value = 2.0; acceptsExpression = true; width = 280.0; horizontalAlignment = HorizontalAlignment.Left; spinButtonPlacementMode = NumberBoxSpinButtonPlacementMode.Inline; valueChanged.add { _, args -> report("Value: ${args!!.newValue}") } })
    },
    WinUi2Sample("RadioButtons", "WinUI 2 mutually exclusive choices.",
        """
        RadioButtons().apply {
            header = "Display density"
            listOf("Compact", "Normal", "Comfortable").forEach { items!!.add(it) }
            selectedIndex = 1
        }
        """) { panel, report ->
        panel.add(RadioButtons().apply { header = "Display density"; listOf("Compact", "Normal", "Comfortable").forEach { items!!.add(it) }; selectedIndex = 1; selectionChanged.add { _, _ -> report("Selected: $selectedIndex") } })
    },
    WinUi2Sample("RatingControl", "WinUI 2 star rating.",
        """
        RatingControl().apply {
            caption = "Rate this sample"; maxRating = 5; value = 3.0
            valueChanged.add { _, _ -> output.text = "Rating: " + value }
        }
        """) { panel, report ->
        panel.add(RatingControl().apply { caption = "Rate this sample"; maxRating = 5; value = 3.0; valueChanged.add { _, _ -> report("Rating: $value") } })
    },
    WinUi2Sample("TextBox", "Editable text and a password field.",
        """
        TextBox().apply {
            header = "Name"; placeholderText = "Enter your name"
            textChanged.add { _, _ -> output.text = "Hello, " + text }
        }
        """) { panel, report ->
        panel.add(TextBox().apply { header = "Name"; placeholderText = "Enter your name"; maxWidth = 480.0; horizontalAlignment = HorizontalAlignment.Left; textChanged.add { _, _ -> report("Hello, $text") } })
        panel.add(PasswordBox().apply { header = "Password"; maxWidth = 480.0; horizontalAlignment = HorizontalAlignment.Left })
    },
    WinUi2Sample("ComboBox", "Select one item from a drop-down list.",
        """
        ComboBox().apply {
            header = "Font"
            listOf("Segoe UI", "Consolas", "Calibri").forEach { items!!.add(it) }
            selectedIndex = 0
        }
        """) { panel, report ->
        panel.add(ComboBox().apply { header = "Font"; listOf("Segoe UI", "Consolas", "Calibri").forEach { items!!.add(it) }; selectedIndex = 0; selectionChanged.add { _, _ -> report("Font: $selectedItem") } })
    },
    WinUi2Sample("DropDownButton", "WinUI 2 button with a menu flyout.",
        """
        DropDownButton().apply {
            content = "Actions"
            flyout = MenuFlyout().apply {
                items!!.add(MenuFlyoutItem().apply { text = "Copy" })
            }
        }
        """) { panel, report ->
        panel.add(DropDownButton().apply { content = "Actions"; flyout = MenuFlyout().apply { listOf("Copy", "Paste", "Share").forEach { label -> items!!.add(MenuFlyoutItem().apply { text = label; click.add { _, _ -> report(label) } }) } } })
    },
    WinUi2Sample("ToggleSplitButton", "WinUI 2 split button with a toggle state.",
        """
        ToggleSplitButton().apply {
            content = "Bold"
            isCheckedChanged.add { _, _ -> output.text = "Bold: " + isChecked }
        }
        """) { panel, report ->
        panel.add(ToggleSplitButton().apply { content = "Bold"; isCheckedChanged.add { _, _ -> report("Bold: $isChecked") }; flyout = MenuFlyout().apply { items!!.add(MenuFlyoutItem().apply { text = "More formatting" }) } })
    },
    WinUi2Sample("ColorPicker", "WinUI 2 color spectrum, slider, and numeric inputs.",
        """
        ColorPicker().apply {
            isAlphaEnabled = true
            colorChanged.add { _, args -> output.text = "Color: " + args!!.newColor }
        }
        """) { panel, report ->
        panel.add(ColorPicker().apply { isAlphaEnabled = true; horizontalAlignment = HorizontalAlignment.Left; colorChanged.add { _, args -> val color = args!!.newColor; report("ARGB: ${color.a}, ${color.r}, ${color.g}, ${color.b}") } })
    },
    WinUi2Sample("Expander", "WinUI 2 expandable content.",
        """
        Expander().apply {
            header = "More information"
            content = TextBlock().apply { text = "Kotlin content inside a WinUI 2 Expander." }
        }
        """) { panel, _ ->
        panel.add(Expander().apply { header = "More information"; horizontalAlignment = HorizontalAlignment.Stretch; content = text("Kotlin content inside a WinUI 2 Expander.") })
    },
    WinUi2Sample("InfoBar", "WinUI 2 inline status messages.",
        """
        InfoBar().apply {
            title = "Saved"; message = "Your changes were saved."
            severity = InfoBarSeverity.Success; isOpen = true
        }
        """) { panel, _ ->
        listOf(InfoBarSeverity.Informational, InfoBarSeverity.Success, InfoBarSeverity.Warning, InfoBarSeverity.Error).forEach { level ->
            panel.add(InfoBar().apply { title = "Status message"; message = "A native WinUI 2 InfoBar."; severity = level; isOpen = true })
        }
    },
    WinUi2Sample("ProgressRing", "WinUI 2 determinate and indeterminate progress.",
        """
        ProgressRing().apply { isActive = true; width = 48.0; height = 48.0 }
        ProgressRing().apply { isActive = true; isIndeterminate = false; value = 65.0 }
        """) { panel, _ ->
        panel.add(ProgressRing().apply { isActive = true; width = 48.0; height = 48.0; horizontalAlignment = HorizontalAlignment.Left })
        panel.add(ProgressRing().apply { isActive = true; isIndeterminate = false; value = 65.0; width = 48.0; height = 48.0; horizontalAlignment = HorizontalAlignment.Left })
    },
    WinUi2Sample("ProgressBar", "System XAML determinate and indeterminate progress.",
        """
        ProgressBar().apply { value = 65.0 }
        ProgressBar().apply { isIndeterminate = true }
        """) { panel, _ ->
        panel.add(ProgressBar().apply { value = 65.0; maxWidth = 480.0; horizontalAlignment = HorizontalAlignment.Stretch })
        panel.add(ProgressBar().apply { isIndeterminate = true; maxWidth = 480.0; horizontalAlignment = HorizontalAlignment.Stretch })
    },
    WinUi2Sample("CalendarDatePicker", "Choose a date using the system calendar flyout.",
        """
        CalendarDatePicker().apply {
            header = "Travel date"; placeholderText = "Choose a date"
            dateChanged.add { _, _ -> output.text = "Selected: " + date }
        }
        """) { panel, report ->
        panel.add(CalendarDatePicker().apply { header = "Travel date"; placeholderText = "Choose a date"; dateChanged.add { _, _ -> report("Selected: $date") } })
    },
    WinUi2Sample("DatePicker", "Choose a date using day, month, and year wheels.",
        """DatePicker().apply { header = "Date" }""") { panel, report ->
        panel.add(DatePicker().apply { header = "Date"; dateChanged.add { _, _ -> report("Date: $date") } })
    },
    WinUi2Sample("TimePicker", "Choose a time with the UWP time picker.",
        """TimePicker().apply { header = "Departure time" }""") { panel, report ->
        panel.add(TimePicker().apply { header = "Departure time"; timeChanged.add { _, _ -> report("Time: $time") } })
    },
    WinUi2Sample("ListView", "Native list selection.",
        """
        ListView().apply {
            listOf("Fluent", "Kotlin", "WinRT", "UWP").forEach { items!!.add(it) }
            selectionChanged.add { _, _ -> output.text = "Selected: " + selectedItem }
        }
        """) { panel, report ->
        panel.add(ListView().apply { listOf("Fluent", "Kotlin", "WinRT", "UWP").forEach { items!!.add(it) }; maxHeight = 240.0; selectionChanged.add { _, _ -> report("Selected: $selectedItem") } })
    },
    WinUi2Sample("TreeView", "WinUI 2 hierarchical data.",
        """
        TreeView().apply {
            rootNodes!!.add(TreeViewNode().apply {
                content = "Gallery"; isExpanded = true
                children!!.add(TreeViewNode().apply { content = "Controls" })
            })
        }
        """) { panel, _ ->
        panel.add(TreeView().apply { rootNodes!!.add(TreeViewNode().apply { content = "Gallery"; isExpanded = true; listOf("Controls", "Styles", "Resources").forEach { label -> children!!.add(TreeViewNode().apply { content = label }) } }); maxHeight = 300.0 })
    },
    WinUi2Sample("TeachingTip", "WinUI 2 contextual teaching tip.",
        """
        val tip = TeachingTip().apply {
            title = "A helpful tip"; subtitle = "This teaching tip is a WinUI 2 control."
        }
        Button().apply { content = "Show tip"; click.add { _, _ -> tip.isOpen = true } }
        """) { panel, _ ->
        val tip = TeachingTip().apply { title = "A helpful tip"; subtitle = "This teaching tip is a WinUI 2 control."; closeButtonContent = "Got it" }
        val button = Button().apply { content = "Show tip"; click.add { _, _ -> tip.isOpen = true } }
        tip.target = button
        panel.add(button)
        panel.add(tip)
    },
)
