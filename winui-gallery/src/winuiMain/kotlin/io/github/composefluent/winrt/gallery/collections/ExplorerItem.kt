package io.github.composefluent.winrt.gallery.collections

import io.github.composefluent.winrt.runtime.WinRTObservableList

internal class ExplorerItem(val Name: String, val IsFolder: Boolean = false, children: List<ExplorerItem> = emptyList()) {
    val Children: MutableList<ExplorerItem> = WinRTObservableList(children)
}
