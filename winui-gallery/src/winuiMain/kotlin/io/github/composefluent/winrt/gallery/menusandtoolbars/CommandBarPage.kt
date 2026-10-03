package io.github.composefluent.winrt.gallery.menusandtoolbars

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.gallery.samplepages.*
import io.github.composefluent.winrt.runtime.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*
import microsoft.ui.xaml.media.animation.*

@GalleryPage(route = "CommandBar", title = "CommandBar", group = "MenusAndToolbars", order = 3)
internal class CommandBarPage : Page(), microsoft.ui.xaml.data.INotifyPropertyChanged {
    private val propertyChangedHandlers = mutableListOf<microsoft.ui.xaml.data.PropertyChangedEventHandler>()
    var MultipleButtons: Boolean = false
        private set(value) { if (field != value) { field = value; propertyChangedHandlers.toList().forEach { it(this, microsoft.ui.xaml.data.PropertyChangedEventArgs("MultipleButtons")) } } }
    override fun addPropertyChanged(handler: microsoft.ui.xaml.data.PropertyChangedEventHandler) { propertyChangedHandlers.add(handler) }
    override fun removePropertyChanged(handler: microsoft.ui.xaml.data.PropertyChangedEventHandler) { propertyChangedHandlers.remove(handler) }
    override fun initializeComponent() {
        super.initializeComponent()
        accelerator(editButton, windows.system.VirtualKey.E, windows.system.VirtualKeyModifiers.Control)
        accelerator(shareButton, windows.system.VirtualKey.F4)
        accelerator(addButton, windows.system.VirtualKey.A, windows.system.VirtualKeyModifiers.Control)
        unloaded.add { _, _ -> RemoveSecondaryCommands() }
    }
    private fun accelerator(button: AppBarButton, key: windows.system.VirtualKey, modifiers: windows.system.VirtualKeyModifiers = windows.system.VirtualKeyModifiers.None) {
        button.keyboardAccelerators.add(microsoft.ui.xaml.input.KeyboardAccelerator().apply { this.key = key; this.modifiers = modifiers })
    }
    private fun OpenButton_Click(sender: Any?, args: RoutedEventArgs) { PrimaryCommandBar.isOpen = true; PrimaryCommandBar.isSticky = true }
    private fun CloseButton_Click(sender: Any?, args: RoutedEventArgs) { PrimaryCommandBar.isOpen = false; PrimaryCommandBar.isSticky = false }
    private fun OnElementClicked(sender: Any?, args: RoutedEventArgs) { SelectedOptionText.text = "${checkNotNull(sender).asWinRT<AppBarButton>().label} option selected" }
    private fun AddSecondaryCommands_Click(sender: Any?, args: RoutedEventArgs) {
        if (PrimaryCommandBar.secondaryCommands.size != 1) return
        listOf(Triple(Symbol.Add, "Button 1", windows.system.VirtualKey.N), Triple(Symbol.Delete, "Button 2", windows.system.VirtualKey.Delete),
            Triple(Symbol.FontDecrease, "Button 3", windows.system.VirtualKey.Subtract), Triple(Symbol.FontIncrease, "Button 4", windows.system.VirtualKey.Add)).forEachIndexed { index, item ->
            if (index == 2) PrimaryCommandBar.secondaryCommands.add(AppBarSeparator())
            PrimaryCommandBar.secondaryCommands.add(AppBarButton().apply { icon = SymbolIcon(item.first); label = item.second; click.add(::OnElementClicked); accelerator(this, item.third, if (index == 1) windows.system.VirtualKeyModifiers.None else windows.system.VirtualKeyModifiers.Control) })
        }
        MultipleButtons = true
    }
    private fun RemoveSecondaryCommands_Click(sender: Any?, args: RoutedEventArgs) { RemoveSecondaryCommands() }
    private fun RemoveSecondaryCommands() { while (PrimaryCommandBar.secondaryCommands.size > 1) PrimaryCommandBar.secondaryCommands.removeAt(PrimaryCommandBar.secondaryCommands.lastIndex); MultipleButtons = false }
}
