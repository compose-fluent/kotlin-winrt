package io.github.composefluent.winrt.gallery.menusandtoolbars

internal class ListItemData(val Text: String, val Command: microsoft.ui.xaml.input.ICommand?) {
    override fun toString(): String = Text
}
